package com.company.diagnosis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 提示词工具类
 * <p>
 * 职责：
 * 1. 加载和管理提示词模板
 * 2. 提供模板变量替换功能
 * 3. 支持多语言提示词
 * 4. 提供提示词的缓存机制
 * <p>
 * 设计考虑：
 * - 支持从文件加载提示词模板
 * - 使用占位符进行变量替换
 * - 提供提示词的验证功能
 * - 实现提示词的缓存和预加载
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
public class PromptUtil {

    private static final Logger logger = LoggerFactory.getLogger(PromptUtil.class);
    
    /**
     * 模板缓存
     */
    private static final Map<String, String> templateCache = new ConcurrentHashMap<>();
    
    /**
     * 占位符模式: ${variableName}
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 加载提示词模板
     * <p>
     * 功能说明：
     * 从资源文件加载指定的提示词模板
     *
     * @param templateName 模板名称（如"interface-invocation/system-prompt"）
     * @return 提示词模板内容
     */
    public static String loadPromptTemplate(String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            return null;
        }
        
        // 先检查缓存
        String cached = templateCache.get(templateName);
        if (cached != null) {
            return cached;
        }
        
        // 构建资源路径
        String resourcePath = "prompts/" + templateName;
        if (!resourcePath.endsWith(".txt")) {
            resourcePath += ".txt";
        }
        
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String content = reader.lines().collect(Collectors.joining("\n"));
                templateCache.put(templateName, content);
                return content;
            }
        } catch (IOException e) {
            logger.warn("加载提示词模板失败: {}, 错误: {}", templateName, e.getMessage());
            return null;
        }
    }

    /**
     * 替换模板变量
     * <p>
     * 功能说明：
     * 将模板中的占位符替换为实际值
     *
     * @param template 提示词模板
     * @param variables 变量Map，key为占位符名称，value为替换值
     * @return 替换后的提示词
     */
    public static String replaceVariables(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = variables.get(variableName);
            String replacement = value != null ? String.valueOf(value) : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * 构建系统提示词
     * <p>
     * 功能说明：
     * 根据智能体类型和上下文构建系统提示词
     *
     * @param agentType 智能体类型
     * @param context 上下文变量
     * @return 系统提示词
     */
    public static String buildSystemPrompt(String agentType, Map<String, Object> context) {
        String templatePath = agentType + "/system-prompt";
        String template = loadPromptTemplate(templatePath);
        
        if (template == null) {
            // 尝试加载通用系统提示词
            template = loadPromptTemplate("common/system-prompt");
        }
        
        if (template == null) {
            logger.warn("未找到系统提示词模板: {}", agentType);
            return "";
        }
        
        return replaceVariables(template, context);
    }

    /**
     * 构建用户提示词
     * <p>
     * 功能说明：
     * 根据任务需求构建用户提示词
     *
     * @param taskType 任务类型
     * @param parameters 任务参数
     * @return 用户提示词
     */
    public static String buildUserPrompt(String taskType, Map<String, Object> parameters) {
        String template = loadPromptTemplate(taskType);
        
        if (template == null) {
            logger.warn("未找到用户提示词模板: {}", taskType);
            return "";
        }
        
        return replaceVariables(template, parameters);
    }

    /**
     * 验证提示词模板
     * <p>
     * 功能说明：
     * 验证提示词模板的完整性和正确性
     *
     * @param template 提示词模板
     * @return 验证结果
     */
    public static Boolean validateTemplate(String template) {
        if (template == null || template.trim().isEmpty()) {
            return false;
        }
        
        // 检查占位符格式
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (variableName == null || variableName.trim().isEmpty()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 清除模板缓存
     */
    public static void clearCache() {
        templateCache.clear();
    }
    
    /**
     * 从缓存中移除指定模板
     *
     * @param templateName 模板名称
     */
    public static void removeFromCache(String templateName) {
        templateCache.remove(templateName);
    }
}
