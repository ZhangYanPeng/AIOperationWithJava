package com.company.diagnosis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词工具类
 * <p>
 * 职责：
 * 1. 加载和管理提示词模板
 * 2. 提供模板变量替换功能
 * 3. 支持多种占位符格式
 * 4. 提供提示词的缓存机制
 * <p>
 * 设计考虑：
 * - 支持从文件加载提示词模板
 * - 使用占位符进行变量替换（支持${var}和{{var}}格式）
 * - 提供提示词的验证功能
 * - 实现提示词的缓存和预加载
 *
 * @author Diagnosis System
 * @since 2026-01-13
 */
public class PromptUtil {

    private static final Logger logger = LoggerFactory.getLogger(PromptUtil.class);

    /**
     * 模板缓存，避免重复加载文件
     */
    private static final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * 提示词模板基础路径
     */
    private static final String PROMPTS_BASE_PATH = "prompts/";

    /**
     * ${variable} 格式占位符的正则表达式
     */
    private static final Pattern DOLLAR_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * {{variable}} 格式占位符的正则表达式
     */
    private static final Pattern MUSTACHE_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * 加载提示词模板
     * <p>
     * 功能说明：
     * 从资源文件加载指定的提示词模板，支持缓存
     *
     * @param templateName 模板名称（如"interface-invocation/system-prompt"）
     * @return 提示词模板内容，加载失败时返回null
     */
    public static String loadPromptTemplate(String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            logger.warn("模板名称为空");
            return null;
        }

        // 检查缓存
        String cachedTemplate = templateCache.get(templateName);
        if (cachedTemplate != null) {
            return cachedTemplate;
        }

        // 构建文件路径
        String path = PROMPTS_BASE_PATH + templateName;
        if (!path.endsWith(".txt") && !path.endsWith(".md")) {
            path = path + ".txt";
        }

        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                logger.warn("模板文件不存在: {}", path);
                return null;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                String template = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
                // 缓存模板
                templateCache.put(templateName, template);
                logger.debug("成功加载提示词模板: {}", templateName);
                return template;
            }
        } catch (IOException e) {
            logger.error("加载提示词模板失败: {}, 错误: {}", templateName, e.getMessage());
            return null;
        }
    }

    /**
     * 替换模板变量
     * <p>
     * 功能说明：
     * 将模板中的占位符替换为实际值，支持${var}和{{var}}两种格式
     *
     * @param template  提示词模板
     * @param variables 变量Map，key为占位符名称，value为替换值
     * @return 替换后的提示词，如果模板为空则返回空字符串
     */
    public static String replaceVariables(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }

        String result = template;

        // 替换 ${variable} 格式
        Matcher dollarMatcher = DOLLAR_PLACEHOLDER_PATTERN.matcher(result);
        StringBuffer sb1 = new StringBuffer();
        while (dollarMatcher.find()) {
            String varName = dollarMatcher.group(1).trim();
            Object value = variables.get(varName);
            String replacement = value != null ? Matcher.quoteReplacement(String.valueOf(value)) : "";
            dollarMatcher.appendReplacement(sb1, replacement);
        }
        dollarMatcher.appendTail(sb1);
        result = sb1.toString();

        // 替换 {{variable}} 格式
        Matcher mustacheMatcher = MUSTACHE_PLACEHOLDER_PATTERN.matcher(result);
        StringBuffer sb2 = new StringBuffer();
        while (mustacheMatcher.find()) {
            String varName = mustacheMatcher.group(1).trim();
            Object value = variables.get(varName);
            String replacement = value != null ? Matcher.quoteReplacement(String.valueOf(value)) : "";
            mustacheMatcher.appendReplacement(sb2, replacement);
        }
        mustacheMatcher.appendTail(sb2);
        result = sb2.toString();

        return result;
    }

    /**
     * 构建系统提示词
     * <p>
     * 功能说明：
     * 根据智能体类型和上下文构建系统提示词
     *
     * @param agentType 智能体类型（如"requirement-understanding"、"step-planning"）
     * @param context   上下文变量
     * @return 系统提示词，构建失败时返回默认提示词
     */
    public static String buildSystemPrompt(String agentType, Map<String, Object> context) {
        // 尝试加载对应类型的系统提示词模板
        String templateName = agentType + "/system-prompt";
        String template = loadPromptTemplate(templateName);

        if (template == null) {
            // 尝试加载通用系统提示词
            template = loadPromptTemplate("common/system-prompt");
        }

        if (template == null) {
            // 返回默认提示词
            logger.warn("无法加载系统提示词模板，使用默认提示词, agentType: {}", agentType);
            return buildDefaultSystemPrompt(agentType);
        }

        return replaceVariables(template, context);
    }

    /**
     * 构建用户提示词
     * <p>
     * 功能说明：
     * 根据任务需求构建用户提示词
     *
     * @param taskType   任务类型（如"planning"、"reasoning"、"decision"）
     * @param parameters 任务参数
     * @return 用户提示词
     */
    public static String buildUserPrompt(String taskType, Map<String, Object> parameters) {
        // 尝试加载对应类型的用户提示词模板
        String templateName = taskType + "/user-prompt";
        String template = loadPromptTemplate(templateName);

        if (template == null) {
            // 尝试从execution-layer加载
            template = loadPromptTemplate("execution-layer/" + taskType);
        }

        if (template == null) {
            // 直接使用参数构建提示词
            logger.debug("无法加载用户提示词模板，直接使用参数构建, taskType: {}", taskType);
            return buildPromptFromParameters(parameters);
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
     * @return 验证结果，true表示有效
     */
    public static Boolean validateTemplate(String template) {
        if (template == null || template.trim().isEmpty()) {
            return false;
        }

        // 检查是否包含未闭合的占位符
        int dollarOpenCount = countOccurrences(template, "${");
        int dollarCloseCount = countOccurrences(template, "}");

        // 检查{{}}格式的占位符
        int mustacheOpenCount = countOccurrences(template, "{{");
        int mustacheCloseCount = countOccurrences(template, "}}");

        // 简单验证：确保有内容且占位符基本匹配
        return template.length() >= 10 && 
               mustacheOpenCount == mustacheCloseCount;
    }

    /**
     * 清除模板缓存
     */
    public static void clearCache() {
        templateCache.clear();
        logger.info("提示词模板缓存已清除");
    }

    /**
     * 清除指定模板的缓存
     *
     * @param templateName 模板名称
     */
    public static void clearCache(String templateName) {
        templateCache.remove(templateName);
        logger.debug("已清除模板缓存: {}", templateName);
    }

    /**
     * 加载并替换模板变量（便捷方法）
     *
     * @param templateName 模板名称
     * @param variables    变量Map
     * @return 替换后的提示词
     */
    public static String loadAndReplace(String templateName, Map<String, Object> variables) {
        String template = loadPromptTemplate(templateName);
        if (template == null) {
            return null;
        }
        return replaceVariables(template, variables);
    }

    /**
     * 构建默认系统提示词
     */
    private static String buildDefaultSystemPrompt(String agentType) {
        return String.format("""
                你是一个专业的智能诊断助手，当前角色是%s。
                请根据用户提供的信息进行分析，并以JSON格式返回结果。
                确保返回的JSON格式正确且包含所有必需字段。
                """, agentType);
    }

    /**
     * 从参数构建提示词
     */
    private static String buildPromptFromParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下信息进行处理：\n\n");

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value != null) {
                sb.append("【").append(key).append("】\n");
                sb.append(value.toString()).append("\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * 统计字符串出现次数
     */
    private static int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
