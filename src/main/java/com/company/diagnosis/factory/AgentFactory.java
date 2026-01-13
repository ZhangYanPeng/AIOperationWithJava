package com.company.diagnosis.factory;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import com.company.diagnosis.agent.executionLayer.*;
import com.company.diagnosis.agent.interfaceLayer.*;
import com.company.diagnosis.loader.AgentConfigLoader;
import com.company.diagnosis.model.config.AgentConfig;
import com.company.diagnosis.registry.AgentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能体工厂
 * <p>
 * 职责：
 * 1. 根据配置动态创建智能体实例
 * 2. 初始化智能体的配置参数
 * 3. 注入智能体的依赖（如下层智能体）
 * 4. 管理智能体的生命周期
 * <p>
 * 设计考虑：
 * - 支持多种智能体类型的创建
 * - 使用反射或配置映射创建实例
 * - 确保智能体的单例或多例管理
 * - 提供智能体的预热机制
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Component
public class AgentFactory {

    private static final Logger logger = LoggerFactory.getLogger(AgentFactory.class);

    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private AgentConfigLoader configLoader;
    
    @Autowired
    private AgentRegistry agentRegistry;
    
    /**
     * 初始化时自动创建并注册所有启用的智能体
     */
    @PostConstruct
    public void initializeAgents() {
        try {
            List<AgentConfig> enabledConfigs = configLoader.loadEnabledConfigs();
            logger.info("开始初始化智能体，共{}个配置", enabledConfigs.size());
            
            for (AgentConfig config : enabledConfigs) {
                try {
                    BaseIntelligentAgent agent = createAgent(config);
                    if (agent != null) {
                        agentRegistry.registerAgent(config.getName(), agent, config);
                        logger.info("智能体创建并注册成功: name={}, layer={}", 
                                config.getName(), config.getLayer());
                    }
                } catch (Exception e) {
                    logger.error("创建智能体失败: name={}, error={}", config.getName(), e.getMessage(), e);
                }
            }
            
            logger.info("智能体初始化完成，共注册{}个智能体", agentRegistry.getAgentCount());
        } catch (Exception e) {
            logger.error("智能体初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 创建智能体实例
     * <p>
     * 功能说明：
     * 根据智能体配置创建对应的智能体实例
     *
     * @param config 智能体配置对象
     * @return 智能体实例
     */
    public BaseIntelligentAgent createAgent(AgentConfig config) {
        if (config == null || config.getName() == null) {
            logger.warn("无效的智能体配置");
            return null;
        }
        
        String agentName = config.getName();
        Integer layer = config.getLayer();
        
        try {
            BaseIntelligentAgent agent = createAgentInstance(agentName, layer);
            if (agent != null) {
                logger.debug("智能体实例创建成功: name={}", agentName);
            }
            return agent;
        } catch (Exception e) {
            logger.error("创建智能体实例失败: name={}, error={}", agentName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 批量创建智能体
     * <p>
     * 功能说明：
     * 根据配置列表批量创建智能体实例
     *
     * @param configs 智能体配置列表
     * @return 智能体实例列表
     */
    public List<BaseIntelligentAgent> createAgents(List<AgentConfig> configs) {
        List<BaseIntelligentAgent> agents = new ArrayList<>();
        
        if (configs == null || configs.isEmpty()) {
            return agents;
        }
        
        for (AgentConfig config : configs) {
            BaseIntelligentAgent agent = createAgent(config);
            if (agent != null) {
                agents.add(agent);
            }
        }
        
        return agents;
    }

    /**
     * 根据名称创建智能体
     * <p>
     * 功能说明：
     * 根据智能体名称创建实例（从配置文件加载配置）
     *
     * @param agentName 智能体名称
     * @return 智能体实例
     */
    public BaseIntelligentAgent createAgentByName(String agentName) {
        if (agentName == null || agentName.isEmpty()) {
            return null;
        }
        
        // 先从注册表查找
        BaseIntelligentAgent existing = agentRegistry.getAgent(agentName);
        if (existing != null) {
            return existing;
        }
        
        // 从配置加载并创建
        AgentConfig config = configLoader.loadConfigByName(agentName);
        if (config != null) {
            return createAgent(config);
        }
        
        logger.warn("未找到智能体配置: {}", agentName);
        return null;
    }

    /**
     * 销毁智能体实例
     * <p>
     * 功能说明：
     * 销毁智能体实例并清理资源
     *
     * @param agent 智能体实例
     */
    public void destroyAgent(BaseIntelligentAgent agent) {
        if (agent == null) {
            return;
        }
        
        String agentName = agent.getAgentName();
        agentRegistry.unregisterAgent(agentName);
        logger.info("智能体已销毁: {}", agentName);
    }

    /**
     * 重新加载智能体配置
     * <p>
     * 功能说明：
     * 重新加载智能体配置并更新实例
     *
     * @param agentName 智能体名称
     * @return 更新后的智能体实例
     */
    public BaseIntelligentAgent reloadAgent(String agentName) {
        // 先注销旧实例
        agentRegistry.unregisterAgent(agentName);
        
        // 重新加载配置
        configLoader.reloadConfigs();
        
        // 创建新实例
        return createAgentByName(agentName);
    }
    
    /**
     * 重新加载所有智能体
     */
    public void reloadAllAgents() {
        // 清空注册表
        agentRegistry.clear();
        
        // 重新加载配置
        configLoader.reloadConfigs();
        
        // 重新初始化
        initializeAgents();
    }
    
    /**
     * 根据名称和层级创建智能体实例
     */
    private BaseIntelligentAgent createAgentInstance(String agentName, Integer layer) {
        // 尝试从Spring容器获取
        try {
            // 根据名称匹配对应的智能体类
            return switch (agentName) {
                // 接口调用层智能体
                case "InterfaceInvocationAgent" -> getOrCreateBean(ParameterMappingAgent.class);
                case "ParameterMappingAgent" -> getOrCreateBean(ParameterMappingAgent.class);
                case "ResultParsingAgent" -> getOrCreateBean(ResultParsingAgent.class);
                
                // 执行层智能体
                case "PrimaryDiagnosisAgent" -> getOrCreateBean(StepPlanningAgent.class);
                case "StepPlanningAgent" -> getOrCreateBean(StepPlanningAgent.class);
                case "StepDecisionAgent" -> getOrCreateBean(StepDecisionAgent.class);
                case "ParameterGenerationAgent" -> getOrCreateBean(ParameterGenerationAgent.class);
                case "RequirementUnderstandingAgent" -> getOrCreateBean(RequirementUnderstandingAgent.class);
                case "ResultGenerationAgent" -> getOrCreateBean(ResultGenerationAgent.class);
                
                // 高级执行层智能体
                case "DeepAnalysisAgent" -> getOrCreateBean(StepPlanningAgent.class);
                case "ExpertConsultationAgent" -> getOrCreateBean(StepPlanningAgent.class);
                
                default -> {
                    logger.warn("未知的智能体类型: {}, 尝试按层级创建", agentName);
                    yield createAgentByLayer(layer);
                }
            };
        } catch (Exception e) {
            logger.error("创建智能体实例失败: name={}, error={}", agentName, e.getMessage());
            return null;
        }
    }
    
    /**
     * 按层级创建默认智能体
     */
    private BaseIntelligentAgent createAgentByLayer(Integer layer) {
        if (layer == null) {
            return null;
        }
        
        return switch (layer) {
            case 1 -> getOrCreateBean(ParameterMappingAgent.class);
            case 2 -> getOrCreateBean(StepPlanningAgent.class);
            default -> getOrCreateBean(StepPlanningAgent.class);
        };
    }
    
    /**
     * 从Spring容器获取或创建Bean
     */
    private <T extends BaseIntelligentAgent> T getOrCreateBean(Class<T> clazz) {
        try {
            return applicationContext.getBean(clazz);
        } catch (Exception e) {
            logger.debug("从Spring容器获取Bean失败，尝试创建新实例: {}", clazz.getSimpleName());
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                logger.error("创建Bean实例失败: {}", ex.getMessage());
                return null;
            }
        }
    }
}
