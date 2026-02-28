package com.company.diagnosis.loader;

import com.company.diagnosis.model.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import jakarta.annotation.PostConstruct;
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
 * @since 2026-01-13
 */
@Component
public class AgentConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigLoader.class);

    /**
     * 配置文件路径
     */
    private static final String CONFIG_FILE = "agent-config.yml";

    /**
     * 配置缓存 - 按名称索引
     */
    private final Map<String, AgentConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 配置缓存 - 按层级索引
     */
    private final Map<Integer, List<AgentConfig>> layerConfigCache = new ConcurrentHashMap<>();

    /**
     * 原始配置数据
     */
    private Map<String, Object> rawConfig;

    /**
     * 是否已加载
     */
    private volatile boolean loaded = false;

    /**
     * 初始化时加载配置
     */
    @PostConstruct
    public void init() {
        try {
            reloadConfigs();
            logger.info("智能体配置加载完成，共加载 {} 个配置", configCache.size());
        } catch (Exception e) {
            logger.error("初始化加载智能体配置失败", e);
        }
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
        if (!loaded) {
            reloadConfigs();
        }
        return new ArrayList<>(configCache.values());
    }

    /**
     * 根据名称加载配置
     * <p>
     * 功能说明：
     * 加载指定名称的智能体配置
     *
     * @param agentName 智能体名称
     * @return 智能体配置，不存在时返回null
     */
    public AgentConfig loadConfigByName(String agentName) {
        if (agentName == null || agentName.isEmpty()) {
            return null;
        }
        if (!loaded) {
            reloadConfigs();
        }
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
        if (!loaded) {
            reloadConfigs();
        }
        return new ArrayList<>(layerConfigCache.getOrDefault(layer, Collections.emptyList()));
    }

    /**
     * 获取启用的层级列表
     *
     * @return 启用的层级编号列表（按层级从高到低排序）
     */
    public List<Integer> getEnabledLayers() {
        if (!loaded) {
            reloadConfigs();
        }
        return configCache.values().stream()
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .map(AgentConfig::getLayer)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /**
     * 获取顶层智能体名称
     *
     * @return 最高层级且启用的智能体名称
     */
    public String getTopLayerAgent() {
        if (!loaded) {
            reloadConfigs();
        }

        // 首先检查编排器配置是否指定了顶层智能体
        if (rawConfig != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> orchestrator = (Map<String, Object>) rawConfig.get("orchestrator");
            if (orchestrator != null) {
                Object topAgent = orchestrator.get("topLayerAgent");
                if (topAgent != null && !topAgent.toString().equals("null")) {
                    return topAgent.toString();
                }
            }
        }

        // 自动检测：查找最高层级且启用的智能体
        return configCache.values().stream()
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .max(Comparator.comparing(AgentConfig::getLayer))
                .map(AgentConfig::getName)
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
        try {
            // 清空缓存
            configCache.clear();
            layerConfigCache.clear();

            // 加载YAML文件
            Yaml yaml = new Yaml();
            ClassPathResource resource = new ClassPathResource(CONFIG_FILE);

            if (!resource.exists()) {
                logger.warn("配置文件不存在: {}", CONFIG_FILE);
                return false;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                rawConfig = yaml.load(inputStream);
            }

            if (rawConfig == null) {
                logger.warn("配置文件内容为空");
                return false;
            }

            // 解析配置
            parseAgentConfigs();

            loaded = true;
            logger.info("配置重新加载成功，共加载 {} 个智能体配置", configCache.size());
            return true;
        } catch (Exception e) {
            logger.error("加载配置文件失败: {}", e.getMessage(), e);
            return false;
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
            result.put("valid", false);
            result.put("errors", List.of("配置对象为空"));
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
                errors.add("非第1层智能体必须指定下层智能体(lowerLayerAgent)");
            } else {
                // 检查下层智能体是否存在
                AgentConfig lowerConfig = configCache.get(config.getLowerLayerAgent());
                if (lowerConfig == null) {
                    errors.add("下层智能体不存在: " + config.getLowerLayerAgent());
                } else if (lowerConfig.getLayer() != null && lowerConfig.getLayer() >= config.getLayer()) {
                    errors.add("下层智能体的层级必须小于当前智能体");
                }
            }
        }

        // 验证模型配置
        if (config.getModel() == null) {
            errors.add("模型配置不能为空");
        } else {
            if (config.getModel().getProvider() == null || config.getModel().getProvider().isEmpty()) {
                errors.add("模型提供商(provider)不能为空");
            }
            if (config.getModel().getModelName() == null || config.getModel().getModelName().isEmpty()) {
                errors.add("模型名称(modelName)不能为空");
            }
        }

        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        return result;
    }

    /**
     * 获取编排器配置
     *
     * @return 编排器配置Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrchestratorConfig() {
        if (rawConfig == null) {
            reloadConfigs();
        }
        return rawConfig != null ? (Map<String, Object>) rawConfig.get("orchestrator") : Collections.emptyMap();
    }

    /**
     * 获取知识库配置
     *
     * @return 知识库配置Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getKnowledgeConfig() {
        if (rawConfig == null) {
            reloadConfigs();
        }
        return rawConfig != null ? (Map<String, Object>) rawConfig.get("knowledge") : Collections.emptyMap();
    }

    /**
     * 解析智能体配置
     */
    @SuppressWarnings("unchecked")
    private void parseAgentConfigs() {
        Map<String, Object> agents = (Map<String, Object>) rawConfig.get("agents");
        if (agents == null) {
            logger.warn("配置文件中没有agents节点");
            return;
        }

        // 解析第1层配置 (interface-invocation)
        Map<String, Object> interfaceConfig = (Map<String, Object>) agents.get("interface-invocation");
        if (interfaceConfig != null) {
            AgentConfig config = parseAgentConfig(interfaceConfig);
            if (config != null) {
                addToCache(config);
            }
        }

        // 解析执行层配置 (execution-layers)
        List<Map<String, Object>> executionLayers = (List<Map<String, Object>>) agents.get("execution-layers");
        if (executionLayers != null) {
            for (Map<String, Object> layerConfig : executionLayers) {
                AgentConfig config = parseAgentConfig(layerConfig);
                if (config != null) {
                    addToCache(config);
                }
            }
        }
    }

    /**
     * 解析单个智能体配置
     */
    @SuppressWarnings("unchecked")
    private AgentConfig parseAgentConfig(Map<String, Object> configMap) {
        try {
            AgentConfig config = new AgentConfig();

            config.setName((String) configMap.get("name"));
            config.setDescription((String) configMap.get("description"));
            config.setLayer((Integer) configMap.get("layer"));
            config.setEnabled(configMap.get("enabled") != null ? (Boolean) configMap.get("enabled") : true);
            config.setLowerLayerAgent((String) configMap.get("lowerLayerAgent"));
            config.setTriggerCondition((String) configMap.get("triggerCondition"));

            // 知识库类型
            config.setKnowledgeTypes((List<String>) configMap.get("knowledgeTypes"));

            // 模型配置
            Map<String, Object> modelMap = (Map<String, Object>) configMap.get("model");
            if (modelMap != null) {
                AgentConfig.ModelConfig modelConfig = new AgentConfig.ModelConfig();
                modelConfig.setProvider((String) modelMap.get("provider"));
                modelConfig.setModelName((String) modelMap.get("modelName"));
                modelConfig.setTemperature(modelMap.get("temperature") != null ?
                        ((Number) modelMap.get("temperature")).doubleValue() : null);
                modelConfig.setMaxTokens((Integer) modelMap.get("maxTokens"));
                modelConfig.setTopP(modelMap.get("topP") != null ?
                        ((Number) modelMap.get("topP")).doubleValue() : null);
                modelConfig.setEnableThinking((Boolean) modelMap.get("enableThinking"));
                modelConfig.setStreaming((Boolean) modelMap.get("streaming"));
                config.setModel(modelConfig);
            }

            // 提示词模板
            config.setPromptTemplates((Map<String, String>) configMap.get("promptTemplates"));

            // 工具函数
            config.setTools((List<String>) configMap.get("tools"));

            // 重试配置
            config.setMaxRetries((Integer) configMap.get("maxRetries"));
            config.setRetryBackoff(configMap.get("retryBackoff") != null ?
                    ((Number) configMap.get("retryBackoff")).longValue() : null);

            // 超时配置
            config.setTimeout(configMap.get("timeout") != null ?
                    ((Number) configMap.get("timeout")).longValue() : null);

            // 并发配置
            config.setConcurrentSteps((Boolean) configMap.get("concurrentSteps"));
            config.setMaxConcurrency((Integer) configMap.get("maxConcurrency"));

            // 校验配置
            Map<String, Object> validationMap = (Map<String, Object>) configMap.get("validation");
            if (validationMap != null) {
                AgentConfig.ValidationConfig validationConfig = new AgentConfig.ValidationConfig();
                validationConfig.setEnabled((Boolean) validationMap.get("enabled"));
                validationConfig.setSchemaPath((String) validationMap.get("schemaPath"));
                config.setValidation(validationConfig);
            }

            // 步骤配置
            config.setMaxSteps((Integer) configMap.get("maxSteps"));
            config.setStepTimeout(configMap.get("stepTimeout") != null ?
                    ((Number) configMap.get("stepTimeout")).longValue() : null);

            return config;
        } catch (Exception e) {
            logger.error("解析智能体配置失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 添加配置到缓存
     */
    private void addToCache(AgentConfig config) {
        if (config == null || config.getName() == null) {
            return;
        }

        // 按名称缓存
        configCache.put(config.getName(), config);

        // 按层级缓存
        if (config.getLayer() != null) {
            layerConfigCache.computeIfAbsent(config.getLayer(), k -> new ArrayList<>()).add(config);
        }

        logger.debug("加载智能体配置: {} (层级: {})", config.getName(), config.getLayer());
    }

    /**
     * 检查配置是否已加载
     *
     * @return 是否已加载
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * 获取配置数量
     *
     * @return 配置数量
     */
    public int getConfigCount() {
        return configCache.size();
    }

    /**
     * 加载配置（兼容旧方法名）
     *
     * @return 所有智能体配置列表
     */
    public List<AgentConfig> loadConfig() {
        return loadAllConfigs();
    }
}
