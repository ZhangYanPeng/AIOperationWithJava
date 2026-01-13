package com.company.diagnosis.loader;

import com.company.diagnosis.model.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 智能体配置加载器
 * <p>
 * 职责：
 * 1. 从agent-config.yml加载智能体配置
 * 2. 解析和验证配置内容
 * 3. 支持配置的热加载
 * 4. 提供配置的缓存机制
 * <p>
 * 设计考虑：
 * - 支持YAML格式的配置文件
 * - 提供配置验证和错误处理
 * - 支持配置的分环境加载
 * - 实现配置的缓存和刷新
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Component
public class AgentConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigLoader.class);

    @Value("${agent.config.path:agent-config.yml}")
    private String configPath;

    /**
     * 配置缓存
     */
    private final Map<String, AgentConfig> configCache = new ConcurrentHashMap<>();
    
    /**
     * 所有配置列表缓存
     */
    private volatile List<AgentConfig> allConfigsCache;
    
    /**
     * 原始配置数据
     */
    private volatile Map<String, Object> rawConfig;

    /**
     * 初始化时加载配置
     */
    @PostConstruct
    public void init() {
        reloadConfigs();
    }

    /**
     * 加载所有智能体配置
     * <p>
     * 功能说明：
     * 从配置文件加载所有智能体的配置
     *
     * @return 智能体配置列表
     */
    public List<AgentConfig> loadAllConfigs() {
        if (allConfigsCache != null) {
            return allConfigsCache;
        }
        
        synchronized (this) {
            if (allConfigsCache != null) {
                return allConfigsCache;
            }
            
            List<AgentConfig> configs = new ArrayList<>();
            
            try {
                Map<String, Object> yamlConfig = loadYamlConfig();
                if (yamlConfig == null) {
                    return configs;
                }
                
                this.rawConfig = yamlConfig;
                
                // 解析agents配置
                @SuppressWarnings("unchecked")
                Map<String, Object> agents = (Map<String, Object>) yamlConfig.get("agents");
                if (agents == null) {
                    return configs;
                }
                
                // 解析接口调用层配置
                @SuppressWarnings("unchecked")
                Map<String, Object> interfaceInvocation = (Map<String, Object>) agents.get("interface-invocation");
                if (interfaceInvocation != null) {
                    AgentConfig config = parseAgentConfig(interfaceInvocation);
                    if (config != null) {
                        configs.add(config);
                        configCache.put(config.getName(), config);
                    }
                }
                
                // 解析执行层配置
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> executionLayers = (List<Map<String, Object>>) agents.get("execution-layers");
                if (executionLayers != null) {
                    for (Map<String, Object> layerConfig : executionLayers) {
                        AgentConfig config = parseAgentConfig(layerConfig);
                        if (config != null) {
                            configs.add(config);
                            configCache.put(config.getName(), config);
                        }
                    }
                }
                
                // 按层级排序
                configs.sort(Comparator.comparing(AgentConfig::getLayer));
                allConfigsCache = configs;
                
                logger.info("成功加载{}个智能体配置", configs.size());
                
            } catch (Exception e) {
                logger.error("加载智能体配置失败: {}", e.getMessage(), e);
            }
            
            return configs;
        }
    }

    /**
     * 根据名称加载配置
     * <p>
     * 功能说明：
     * 加载指定名称的智能体配置
     *
     * @param agentName 智能体名称
     * @return 智能体配置
     */
    public AgentConfig loadConfigByName(String agentName) {
        if (agentName == null || agentName.isEmpty()) {
            return null;
        }
        
        // 先从缓存获取
        AgentConfig cached = configCache.get(agentName);
        if (cached != null) {
            return cached;
        }
        
        // 确保配置已加载
        loadAllConfigs();
        
        return configCache.get(agentName);
    }

    /**
     * 根据层级加载配置
     * <p>
     * 功能说明：
     * 加载指定层级的所有智能体配置
     *
     * @param layer 层级编号
     * @return 该层级的智能体配置列表
     */
    public List<AgentConfig> loadConfigsByLayer(Integer layer) {
        if (layer == null) {
            return Collections.emptyList();
        }
        
        return loadAllConfigs().stream()
                .filter(config -> layer.equals(config.getLayer()))
                .collect(Collectors.toList());
    }
    
    /**
     * 加载启用的配置
     *
     * @return 启用的智能体配置列表
     */
    public List<AgentConfig> loadEnabledConfigs() {
        return loadAllConfigs().stream()
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .collect(Collectors.toList());
    }
    
    /**
     * 获取最高层级
     *
     * @return 最高层级编号
     */
    public Integer getTopLayer() {
        return loadEnabledConfigs().stream()
                .map(AgentConfig::getLayer)
                .max(Integer::compareTo)
                .orElse(1);
    }
    
    /**
     * 获取顶层智能体配置
     *
     * @return 顶层智能体配置
     */
    public AgentConfig getTopLayerConfig() {
        Integer topLayer = getTopLayer();
        return loadConfigsByLayer(topLayer).stream()
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 重新加载配置
     * <p>
     * 功能说明：
     * 重新加载配置文件，用于热加载
     *
     * @return 是否加载成功
     */
    public Boolean reloadConfigs() {
        synchronized (this) {
            configCache.clear();
            allConfigsCache = null;
            rawConfig = null;
            
            try {
                loadAllConfigs();
                return true;
            } catch (Exception e) {
                logger.error("重新加载配置失败: {}", e.getMessage(), e);
                return false;
            }
        }
    }

    /**
     * 验证配置
     * <p>
     * 功能说明：
     * 验证智能体配置的合法性
     *
     * @param config 智能体配置
     * @return 验证结果和错误信息
     */
    public Map<String, Object> validateConfig(AgentConfig config) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        
        if (config == null) {
            errors.add("配置对象不能为空");
            result.put("valid", false);
            result.put("errors", errors);
            return result;
        }
        
        // 检查必填字段
        if (config.getName() == null || config.getName().isEmpty()) {
            errors.add("智能体名称不能为空");
        }
        
        if (config.getLayer() == null || config.getLayer() < 1) {
            errors.add("层级必须大于等于1");
        }
        
        // 验证层级关系
        if (config.getLayer() != null && config.getLayer() > 1) {
            if (config.getLowerLayerAgent() == null || config.getLowerLayerAgent().isEmpty()) {
                errors.add("第" + config.getLayer() + "层必须指定下层智能体");
            } else {
                // 验证下层智能体是否存在
                AgentConfig lowerConfig = loadConfigByName(config.getLowerLayerAgent());
                if (lowerConfig == null) {
                    errors.add("下层智能体不存在: " + config.getLowerLayerAgent());
                } else if (lowerConfig.getLayer() != config.getLayer() - 1) {
                    errors.add("下层智能体层级必须为" + (config.getLayer() - 1));
                }
            }
        }
        
        // 验证模型配置
        if (config.getModel() == null) {
            errors.add("模型配置不能为空");
        } else {
            if (config.getModel().getModelName() == null) {
                errors.add("模型名称不能为空");
            }
        }
        
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        return result;
    }
    
    /**
     * 获取编排器配置
     *
     * @return 编排器配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrchestratorConfig() {
        if (rawConfig == null) {
            loadAllConfigs();
        }
        return rawConfig != null ? (Map<String, Object>) rawConfig.get("orchestrator") : null;
    }
    
    /**
     * 获取知识库配置
     *
     * @return 知识库配置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getKnowledgeConfig() {
        if (rawConfig == null) {
            loadAllConfigs();
        }
        return rawConfig != null ? (Map<String, Object>) rawConfig.get("knowledge") : null;
    }

    /**
     * 加载YAML配置文件
     */
    private Map<String, Object> loadYamlConfig() {
        try {
            ClassPathResource resource = new ClassPathResource(configPath);
            try (InputStream inputStream = resource.getInputStream()) {
                Yaml yaml = new Yaml();
                return yaml.load(inputStream);
            }
        } catch (IOException e) {
            logger.error("读取配置文件失败: {}", configPath, e);
            return null;
        }
    }

    /**
     * 解析单个智能体配置
     */
    @SuppressWarnings("unchecked")
    private AgentConfig parseAgentConfig(Map<String, Object> configMap) {
        if (configMap == null) {
            return null;
        }
        
        AgentConfig config = new AgentConfig();
        
        config.setName((String) configMap.get("name"));
        config.setDescription((String) configMap.get("description"));
        config.setLayer((Integer) configMap.get("layer"));
        config.setEnabled((Boolean) configMap.getOrDefault("enabled", true));
        config.setLowerLayerAgent((String) configMap.get("lowerLayerAgent"));
        config.setTriggerCondition((String) configMap.get("triggerCondition"));
        config.setKnowledgeTypes((List<String>) configMap.get("knowledgeTypes"));
        config.setTools((List<String>) configMap.get("tools"));
        config.setMaxRetries((Integer) configMap.get("maxRetries"));
        config.setMaxSteps((Integer) configMap.get("maxSteps"));
        
        // 解析超时配置
        Object timeout = configMap.get("timeout");
        if (timeout instanceof Number) {
            config.setTimeout(((Number) timeout).longValue());
        }
        
        Object retryBackoff = configMap.get("retryBackoff");
        if (retryBackoff instanceof Number) {
            config.setRetryBackoff(((Number) retryBackoff).longValue());
        }
        
        Object stepTimeout = configMap.get("stepTimeout");
        if (stepTimeout instanceof Number) {
            config.setStepTimeout(((Number) stepTimeout).longValue());
        }
        
        config.setConcurrentSteps((Boolean) configMap.get("concurrentSteps"));
        config.setMaxConcurrency((Integer) configMap.get("maxConcurrency"));
        
        // 解析模型配置
        Map<String, Object> modelMap = (Map<String, Object>) configMap.get("model");
        if (modelMap != null) {
            AgentConfig.ModelConfig modelConfig = new AgentConfig.ModelConfig();
            modelConfig.setProvider((String) modelMap.get("provider"));
            modelConfig.setModelName((String) modelMap.get("modelName"));
            modelConfig.setTemperature((Double) modelMap.get("temperature"));
            modelConfig.setMaxTokens((Integer) modelMap.get("maxTokens"));
            modelConfig.setTopP((Double) modelMap.get("topP"));
            modelConfig.setEnableThinking((Boolean) modelMap.get("enableThinking"));
            modelConfig.setStreaming((Boolean) modelMap.get("streaming"));
            config.setModel(modelConfig);
        }
        
        // 解析提示词模板
        config.setPromptTemplates((Map<String, String>) configMap.get("promptTemplates"));
        
        // 解析校验配置
        Map<String, Object> validationMap = (Map<String, Object>) configMap.get("validation");
        if (validationMap != null) {
            AgentConfig.ValidationConfig validationConfig = new AgentConfig.ValidationConfig();
            validationConfig.setEnabled((Boolean) validationMap.get("enabled"));
            validationConfig.setSchemaPath((String) validationMap.get("schemaPath"));
            config.setValidation(validationConfig);
        }
        
        return config;
    }
}
