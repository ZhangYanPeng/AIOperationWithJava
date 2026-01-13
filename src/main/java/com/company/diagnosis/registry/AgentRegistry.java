package com.company.diagnosis.registry;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import com.company.diagnosis.model.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 智能体注册表
 * <p>
 * 职责：
 * 1. 管理所有智能体实例的注册和查找
 * 2. 提供智能体的生命周期管理
 * 3. 维护智能体之间的依赖关系
 * 4. 提供智能体的元数据查询
 * <p>
 * 设计考虑：
 * - 使用ConcurrentHashMap保证线程安全
 * - 支持按名称、层级、类型查询
 * - 提供智能体的健康检查
 * - 支持智能体的动态注册和注销
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Component
public class AgentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistry.class);

    /**
     * 存储所有已注册的智能体
     * key: 智能体名称, value: 智能体实例
     */
    private final ConcurrentHashMap<String, BaseIntelligentAgent> agents = new ConcurrentHashMap<>();
    
    /**
     * 存储智能体配置
     * key: 智能体名称, value: 智能体配置
     */
    private final ConcurrentHashMap<String, AgentConfig> agentConfigs = new ConcurrentHashMap<>();
    
    /**
     * 存储层级到智能体的映射
     * key: 层级编号, value: 智能体名称列表
     */
    private final ConcurrentHashMap<Integer, List<String>> layerAgentMapping = new ConcurrentHashMap<>();

    /**
     * 注册智能体
     * <p>
     * 功能说明：
     * 将智能体实例注册到注册表
     *
     * @param name 智能体名称
     * @param agent 智能体实例
     */
    public void registerAgent(String name, BaseIntelligentAgent agent) {
        if (name == null || name.isEmpty()) {
            logger.warn("注册智能体失败：名称为空");
            return;
        }
        if (agent == null) {
            logger.warn("注册智能体失败：实例为空，名称={}", name);
            return;
        }
        
        if (agents.containsKey(name)) {
            logger.warn("智能体已存在，将被覆盖：{}", name);
        }
        
        agents.put(name, agent);
        logger.info("智能体注册成功：{}", name);
    }
    
    /**
     * 注册智能体及其配置
     *
     * @param name 智能体名称
     * @param agent 智能体实例
     * @param config 智能体配置
     */
    public void registerAgent(String name, BaseIntelligentAgent agent, AgentConfig config) {
        registerAgent(name, agent);
        
        if (config != null) {
            agentConfigs.put(name, config);
            
            // 更新层级映射
            if (config.getLayer() != null) {
                layerAgentMapping
                    .computeIfAbsent(config.getLayer(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(name);
            }
        }
    }

    /**
     * 注销智能体
     * <p>
     * 功能说明：
     * 从注册表移除智能体
     *
     * @param name 智能体名称
     * @return 是否注销成功
     */
    public Boolean unregisterAgent(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        BaseIntelligentAgent removed = agents.remove(name);
        AgentConfig config = agentConfigs.remove(name);
        
        // 从层级映射中移除
        if (config != null && config.getLayer() != null) {
            List<String> layerAgents = layerAgentMapping.get(config.getLayer());
            if (layerAgents != null) {
                layerAgents.remove(name);
            }
        }
        
        if (removed != null) {
            logger.info("智能体注销成功：{}", name);
            return true;
        }
        
        return false;
    }

    /**
     * 获取智能体实例
     * <p>
     * 功能说明：
     * 根据名称获取智能体实例
     *
     * @param name 智能体名称
     * @return 智能体实例，如果不存在返回null
     */
    public BaseIntelligentAgent getAgent(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return agents.get(name);
    }
    
    /**
     * 获取智能体配置
     *
     * @param name 智能体名称
     * @return 智能体配置
     */
    public AgentConfig getAgentConfig(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return agentConfigs.get(name);
    }

    /**
     * 检查智能体是否已注册
     * <p>
     * 功能说明：
     * 检查指定名称的智能体是否已注册
     *
     * @param name 智能体名称
     * @return 是否已注册
     */
    public Boolean isRegistered(String name) {
        return name != null && agents.containsKey(name);
    }

    /**
     * 获取所有已注册的智能体名称
     * <p>
     * 功能说明：
     * 获取所有已注册智能体的名称列表
     *
     * @return 智能体名称列表
     */
    public List<String> getAllAgentNames() {
        return new ArrayList<>(agents.keySet());
    }

    /**
     * 根据层级获取智能体
     * <p>
     * 功能说明：
     * 获取指定层级的所有智能体
     *
     * @param layer 层级编号
     * @return 该层级的智能体列表
     */
    public List<BaseIntelligentAgent> getAgentsByLayer(Integer layer) {
        if (layer == null) {
            return Collections.emptyList();
        }
        
        List<String> agentNames = layerAgentMapping.get(layer);
        if (agentNames == null || agentNames.isEmpty()) {
            // 从配置中查找
            return agents.values().stream()
                    .filter(agent -> {
                        AgentConfig config = agentConfigs.get(getAgentName(agent));
                        return config != null && layer.equals(config.getLayer());
                    })
                    .collect(Collectors.toList());
        }
        
        return agentNames.stream()
                .map(agents::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取指定层级的智能体名称列表
     *
     * @param layer 层级编号
     * @return 智能体名称列表
     */
    public List<String> getAgentNamesByLayer(Integer layer) {
        if (layer == null) {
            return Collections.emptyList();
        }
        
        List<String> names = layerAgentMapping.get(layer);
        if (names != null) {
            return new ArrayList<>(names);
        }
        
        return agentConfigs.entrySet().stream()
                .filter(entry -> layer.equals(entry.getValue().getLayer()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 清空注册表
     * <p>
     * 功能说明：
     * 清空所有已注册的智能体
     */
    public void clear() {
        agents.clear();
        agentConfigs.clear();
        layerAgentMapping.clear();
        logger.info("智能体注册表已清空");
    }

    /**
     * 获取注册统计信息
     * <p>
     * 功能说明：
     * 获取智能体注册的统计信息
     *
     * @return 统计信息Map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 总数量
        stats.put("totalAgents", agents.size());
        
        // 各层级数量
        Map<Integer, Integer> layerCounts = new HashMap<>();
        for (Map.Entry<Integer, List<String>> entry : layerAgentMapping.entrySet()) {
            layerCounts.put(entry.getKey(), entry.getValue().size());
        }
        stats.put("agentsByLayer", layerCounts);
        
        // 智能体名称列表
        stats.put("agentNames", getAllAgentNames());
        
        return stats;
    }
    
    /**
     * 获取所有智能体实例
     *
     * @return 智能体实例集合
     */
    public Collection<BaseIntelligentAgent> getAllAgents() {
        return agents.values();
    }
    
    /**
     * 获取智能体数量
     *
     * @return 智能体数量
     */
    public int getAgentCount() {
        return agents.size();
    }
    
    /**
     * 根据智能体实例获取名称
     */
    private String getAgentName(BaseIntelligentAgent agent) {
        for (Map.Entry<String, BaseIntelligentAgent> entry : agents.entrySet()) {
            if (entry.getValue() == agent) {
                return entry.getKey();
            }
        }
        return null;
    }
}
