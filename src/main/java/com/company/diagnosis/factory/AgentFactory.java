package com.company.diagnosis.factory;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import com.company.diagnosis.agent.executionLayer.*;
import com.company.diagnosis.agent.interfaceLayer.*;
import com.company.diagnosis.model.config.AgentConfig;
import com.company.diagnosis.loader.AgentConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
 * - 使用Spring Context获取Bean实例
 * - 确保智能体的单例管理
 * - 提供智能体的预热机制
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Component
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    /**
     * 智能体名称到类的映射
     */
    private static final Map<String, Class<? extends BaseIntelligentAgent>> AGENT_CLASS_MAP = new HashMap<>();

    static {
        // 执行层智能体
        AGENT_CLASS_MAP.put("RequirementUnderstandingAgent", RequirementUnderstandingAgent.class);
        AGENT_CLASS_MAP.put("StepPlanningAgent", StepPlanningAgent.class);
        AGENT_CLASS_MAP.put("StepDecisionAgent", StepDecisionAgent.class);
        AGENT_CLASS_MAP.put("ParameterGenerationAgent", ParameterGenerationAgent.class);
        AGENT_CLASS_MAP.put("ResultGenerationAgent", ResultGenerationAgent.class);
        
        // 接口层智能体
        AGENT_CLASS_MAP.put("ParameterMappingAgent", ParameterMappingAgent.class);
        AGENT_CLASS_MAP.put("ResultParsingAgent", ResultParsingAgent.class);
    }

    /**
     * 智能体实例缓存
     */
    private final ConcurrentHashMap<String, BaseIntelligentAgent> agentCache = new ConcurrentHashMap<>();

    /**
     * 智能体配置缓存
     */
    private final ConcurrentHashMap<String, AgentConfig> configCache = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private AgentConfigLoader agentConfigLoader;

    /**
     * 初始化：预加载所有智能体
     */
    @PostConstruct
    public void init() {
        log.info("初始化智能体工厂...");
        
        // 从Spring Context预加载所有智能体Bean
        for (String agentName : AGENT_CLASS_MAP.keySet()) {
            try {
                Class<? extends BaseIntelligentAgent> agentClass = AGENT_CLASS_MAP.get(agentName);
                BaseIntelligentAgent agent = applicationContext.getBean(agentClass);
                agentCache.put(agentName, agent);
                log.debug("预加载智能体: {}", agentName);
            } catch (Exception e) {
                log.warn("预加载智能体失败: {}, error: {}", agentName, e.getMessage());
            }
        }
        
        log.info("智能体工厂初始化完成: 已加载{}个智能体", agentCache.size());
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
            throw new IllegalArgumentException("智能体配置不能为空");
        }

        String agentName = config.getName();
        log.info("创建智能体: {}", agentName);

        // 1. 检查缓存
        if (agentCache.containsKey(agentName)) {
            log.debug("从缓存获取智能体: {}", agentName);
            return agentCache.get(agentName);
        }

        // 2. 根据名称查找对应的类
        Class<? extends BaseIntelligentAgent> agentClass = AGENT_CLASS_MAP.get(agentName);
        if (agentClass == null) {
            throw new IllegalArgumentException("未知的智能体类型: " + agentName);
        }

        // 3. 从Spring Context获取Bean(支持依赖注入)
        try {
            BaseIntelligentAgent agent = applicationContext.getBean(agentClass);
            
            // 4. 缓存配置
            configCache.put(agentName, config);
            
            // 5. 缓存实例
            agentCache.put(agentName, agent);
            
            log.info("智能体创建成功: {}", agentName);
            return agent;
        } catch (Exception e) {
            log.error("创建智能体失败: {}", agentName, e);
            throw new RuntimeException("创建智能体失败: " + agentName, e);
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
        if (configs == null || configs.isEmpty()) {
            return Collections.emptyList();
        }

        List<BaseIntelligentAgent> agents = new ArrayList<>();
        for (AgentConfig config : configs) {
            try {
                BaseIntelligentAgent agent = createAgent(config);
                agents.add(agent);
            } catch (Exception e) {
                log.error("批量创建智能体失败: {}", config.getName(), e);
            }
        }

        log.info("批量创建智能体完成: 成功{}/总共{}", agents.size(), configs.size());
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
            throw new IllegalArgumentException("智能体名称不能为空");
        }

        // 检查缓存
        if (agentCache.containsKey(agentName)) {
            return agentCache.get(agentName);
        }

        // 加载配置
        AgentConfig config = loadConfig(agentName);
        if (config == null) {
            // 使用默认配置
            config = new AgentConfig();
            config.setName(agentName);
            config.setEnabled(true);
        }

        return createAgent(config);
    }

    /**
     * 获取智能体实例（从缓存）
     *
     * @param agentName 智能体名称
     * @return 智能体实例，如不存在返回null
     */
    public BaseIntelligentAgent getAgent(String agentName) {
        return agentCache.get(agentName);
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
        log.info("销毁智能体: {}", agentName);

        // 从缓存移除
        agentCache.remove(agentName);
        configCache.remove(agentName);

        log.info("智能体已销毁: {}", agentName);
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
        log.info("重新加载智能体: {}", agentName);

        // 销毁旧实例
        BaseIntelligentAgent oldAgent = agentCache.get(agentName);
        if (oldAgent != null) {
            destroyAgent(oldAgent);
        }

        // 重新创建
        return createAgentByName(agentName);
    }

    /**
     * 获取所有已注册的智能体名称
     *
     * @return 智能体名称集合
     */
    public Set<String> getRegisteredAgentNames() {
        return Collections.unmodifiableSet(agentCache.keySet());
    }

    /**
     * 获取智能体配置
     *
     * @param agentName 智能体名称
     * @return 智能体配置
     */
    public AgentConfig getAgentConfig(String agentName) {
        return configCache.get(agentName);
    }

    /**
     * 加载智能体配置
     */
    private AgentConfig loadConfig(String agentName) {
        if (agentConfigLoader == null) {
            return null;
        }

        try {
            List<AgentConfig> configs = agentConfigLoader.loadConfig();
            // 解析配置（简化实现）
            for (AgentConfig config : configs) {
                if (config.getName().equals(agentName)) {
                    return config;
                }
            }
            // 如果没有找到，创建默认配置
            AgentConfig config = new AgentConfig();
            config.setName(agentName);
            config.setEnabled(true);
            return config;
        } catch (Exception e) {
            log.warn("加载智能体配置失败: {}", agentName, e);
            return null;
        }
    }
}
