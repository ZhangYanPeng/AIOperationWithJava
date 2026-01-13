package com.company.diagnosis.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 文件校验器
 * <p>
 * 职责：
 * 1. 校验上传的知识库文件是否符合规范
 * 2. 检查文件大小、类型等约束
 * 3. 预检查内容格式（如是否为合法的Markdown/文本）
 */
@Component
public class FileValidator {

    private static final Logger logger = LoggerFactory.getLogger(FileValidator.class);

    // 允许的文件扩展名
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "md", "txt", "json", "yaml", "yml", "xml", "html", "csv"
    ));

    // 允许的MIME类型
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
            "text/plain",
            "text/markdown",
            "text/html",
            "text/xml",
            "text/csv",
            "application/json",
            "application/x-yaml",
            "application/xml"
    ));

    // 最大文件大小 (10MB)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // 最小文件大小 (1 byte)
    private static final long MIN_FILE_SIZE = 1;

    /**
     * 校验文件元数据
     *
     * @param fileMeta 文件元数据（文件名、大小、类型等）
     * @return 是否通过校验
     */
    public Boolean validateMeta(Map<String, Object> fileMeta) {
        if (fileMeta == null || fileMeta.isEmpty()) {
            logger.warn("文件元数据为空");
            return false;
        }

        // 校验文件名
        String fileName = getStringValue(fileMeta, "fileName", "filename", "name");
        if (!validateFileName(fileName)) {
            return false;
        }

        // 校验文件大小
        Long fileSize = getLongValue(fileMeta, "fileSize", "size", "contentLength");
        if (!validateFileSize(fileSize)) {
            return false;
        }

        // 校验文件类型
        String contentType = getStringValue(fileMeta, "contentType", "mimeType", "type");
        if (contentType != null && !validateContentType(contentType)) {
            return false;
        }

        logger.info("文件元数据校验通过: {}", fileName);
        return true;
    }

    /**
     * 校验文件名
     *
     * @param fileName 文件名
     * @return 是否合法
     */
    public Boolean validateFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            logger.warn("文件名为空");
            return false;
        }

        // 检查文件名长度
        if (fileName.length() > 255) {
            logger.warn("文件名过长: {}", fileName.length());
            return false;
        }

        // 检查非法字符
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            logger.warn("文件名包含非法字符: {}", fileName);
            return false;
        }

        // 检查扩展名
        String extension = getFileExtension(fileName);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            logger.warn("不支持的文件扩展名: {}", extension);
            return false;
        }

        return true;
    }

    /**
     * 校验文件大小
     *
     * @param fileSize 文件大小(字节)
     * @return 是否合法
     */
    public Boolean validateFileSize(Long fileSize) {
        if (fileSize == null) {
            logger.warn("文件大小未知");
            return false;
        }

        if (fileSize < MIN_FILE_SIZE) {
            logger.warn("文件为空: size={}", fileSize);
            return false;
        }

        if (fileSize > MAX_FILE_SIZE) {
            logger.warn("文件过大: size={}, max={}", fileSize, MAX_FILE_SIZE);
            return false;
        }

        return true;
    }

    /**
     * 校验内容类型
     *
     * @param contentType MIME类型
     * @return 是否合法
     */
    public Boolean validateContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return true; // 允许未知类型
        }

        // 处理带参数的MIME类型 (如 text/plain; charset=utf-8)
        String mimeType = contentType.split(";")[0].trim().toLowerCase();

        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            logger.warn("不支持的内容类型: {}", contentType);
            return false;
        }

        return true;
    }

    /**
     * 校验文件内容
     *
     * @param content 文件内容
     * @param fileName 文件名
     * @return 是否合法
     */
    public Boolean validateContent(String content, String fileName) {
        if (content == null || content.trim().isEmpty()) {
            logger.warn("文件内容为空");
            return false;
        }

        String extension = getFileExtension(fileName);
        if (extension == null) {
            return true;
        }

        switch (extension.toLowerCase()) {
            case "json":
                return validateJsonContent(content);
            case "yaml":
            case "yml":
                return validateYamlContent(content);
            case "xml":
            case "html":
                return validateXmlContent(content);
            default:
                return true;
        }
    }

    private Boolean validateJsonContent(String content) {
        try {
            com.company.diagnosis.util.JsonUtil.parseJson(content);
            return true;
        } catch (Exception e) {
            logger.warn("JSON内容格式错误: {}", e.getMessage());
            return false;
        }
    }

    private Boolean validateYamlContent(String content) {
        // 基本的YAML格式检查
        // 真实环境中应使用SnakeYAML等库进行验证
        return content != null && !content.trim().isEmpty();
    }

    private Boolean validateXmlContent(String content) {
        // 基本的XML格式检查
        return content != null && 
               content.trim().startsWith("<") && 
               content.trim().endsWith(">");
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(lastDot + 1);
    }

    private String getStringValue(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private Long getLongValue(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                try {
                    return Long.parseLong((String) value);
                } catch (NumberFormatException e) {
                    // 继续尝试下一个key
                }
            }
        }
        return null;
    }
}