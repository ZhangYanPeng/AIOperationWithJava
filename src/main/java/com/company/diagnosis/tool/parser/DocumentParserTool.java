package com.company.diagnosis.tool.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档解析工具
 * <p>
 * 职责：
 * 1. 解析Markdown/文本格式的接口文档
 * 2. 提取接口URL、方法、参数、返回体等结构化信息
 * 3. 为接口调用层智能体提供标准化文档结构
 */
@Component
public class DocumentParserTool {

    private static final Logger logger = LoggerFactory.getLogger(DocumentParserTool.class);

    // 常用正则模式
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(GET|POST|PUT|DELETE|PATCH)\\s+(/[\\w\\-/{}?&=]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTTP_METHOD_PATTERN = Pattern.compile(
            "\\b(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{(\\w+)\\}");
    private static final Pattern MARKDOWN_CODE_BLOCK = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```");
    private static final Pattern HEADER_PATTERN = Pattern.compile("^#+\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("\\|([^|]+)\\|([^|]+)\\|([^|]*)\\|?");

    /**
     * 解析接口文档
     *
     * @param docContent 文档内容（Markdown或纯文本）
     * @return 结构化的接口规范信息
     */
    public Map<String, Object> parseInterfaceDoc(String docContent) {
        if (docContent == null || docContent.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> result = new HashMap<>();

        // 提取接口基本信息
        extractEndpointInfo(docContent, result);

        // 提取参数信息
        extractParameters(docContent, result);

        // 提取响应信息
        extractResponse(docContent, result);

        // 提取示例
        extractExamples(docContent, result);

        // 提取描述
        extractDescription(docContent, result);

        logger.debug("文档解析完成: {}", result.get("url"));
        return result;
    }

    /**
     * 解析Markdown表格
     *
     * @param markdown Markdown内容
     * @return 表格数据列表
     */
    public List<Map<String, String>> parseMarkdownTable(String markdown) {
        List<Map<String, String>> result = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return result;
        }

        String[] lines = markdown.split("\n");
        List<String> headers = null;
        boolean inTable = false;

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("|") && line.endsWith("|")) {
                if (line.contains("---")) {
                    // 分隔行，跳过
                    continue;
                }

                String[] cells = line.substring(1, line.length() - 1).split("\\|");
                if (!inTable) {
                    // 首行是表头
                    headers = new ArrayList<>();
                    for (String cell : cells) {
                        headers.add(cell.trim());
                    }
                    inTable = true;
                } else if (headers != null) {
                    // 数据行
                    Map<String, String> row = new HashMap<>();
                    for (int i = 0; i < Math.min(headers.size(), cells.length); i++) {
                        row.put(headers.get(i), cells[i].trim());
                    }
                    result.add(row);
                }
            } else {
                inTable = false;
                headers = null;
            }
        }

        return result;
    }

    /**
     * 提取代码块
     *
     * @param markdown Markdown内容
     * @return 代码块列表 (language -> code)
     */
    public List<Map<String, String>> extractCodeBlocks(String markdown) {
        List<Map<String, String>> result = new ArrayList<>();
        if (markdown == null) {
            return result;
        }

        Matcher matcher = MARKDOWN_CODE_BLOCK.matcher(markdown);
        while (matcher.find()) {
            Map<String, String> block = new HashMap<>();
            block.put("language", matcher.group(1));
            block.put("code", matcher.group(2).trim());
            result.add(block);
        }

        return result;
    }

    /**
     * 提取Markdown标题结构
     *
     * @param markdown Markdown内容
     * @return 标题层级结构
     */
    public List<Map<String, Object>> extractHeadings(String markdown) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (markdown == null) {
            return result;
        }

        String[] lines = markdown.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                String text = line.substring(level).trim();

                Map<String, Object> heading = new HashMap<>();
                heading.put("level", level);
                heading.put("text", text);
                result.add(heading);
            }
        }

        return result;
    }

    /**
     * 解析OpenAPI/Swagger格式的参数定义
     *
     * @param paramDef 参数定义内容
     * @return 参数列表
     */
    public List<Map<String, Object>> parseParameterDefinition(String paramDef) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (paramDef == null) {
            return result;
        }

        // 尝试解析表格格式
        List<Map<String, String>> tableRows = parseMarkdownTable(paramDef);
        for (Map<String, String> row : tableRows) {
            Map<String, Object> param = new HashMap<>();
            param.put("name", row.getOrDefault("参数名", row.get("name")));
            param.put("type", row.getOrDefault("类型", row.get("type")));
            param.put("required", parseRequired(row.getOrDefault("必填", row.get("required"))));
            param.put("description", row.getOrDefault("描述", row.get("description")));
            param.put("default", row.getOrDefault("默认值", row.get("default")));
            result.add(param);
        }

        return result;
    }

    private void extractEndpointInfo(String doc, Map<String, Object> result) {
        // 尝试匹配 "GET /api/v1/xxx" 格式
        Matcher urlMatcher = URL_PATTERN.matcher(doc);
        if (urlMatcher.find()) {
            result.put("method", urlMatcher.group(1).toUpperCase());
            result.put("url", urlMatcher.group(2));
        } else {
            // 尝试分别匹配方法和URL
            Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(doc);
            if (methodMatcher.find()) {
                result.put("method", methodMatcher.group(1).toUpperCase());
            }

            // 查找URL模式
            Pattern simpleUrlPattern = Pattern.compile("(/[\\w\\-/{}]+)");
            Matcher simpleUrlMatcher = simpleUrlPattern.matcher(doc);
            if (simpleUrlMatcher.find()) {
                result.put("url", simpleUrlMatcher.group(1));
            }
        }

        // 提取路径参数
        String url = (String) result.get("url");
        if (url != null) {
            List<String> pathParams = new ArrayList<>();
            Matcher paramMatcher = PATH_PARAM_PATTERN.matcher(url);
            while (paramMatcher.find()) {
                pathParams.add(paramMatcher.group(1));
            }
            if (!pathParams.isEmpty()) {
                result.put("pathParams", pathParams);
            }
        }
    }

    private void extractParameters(String doc, Map<String, Object> result) {
        // 查找参数表格
        int paramIndex = indexOfAny(doc.toLowerCase(), 
                "请求参数", "参数说明", "parameters", "request body", "query parameters");
        
        if (paramIndex >= 0) {
            String paramSection = doc.substring(paramIndex);
            int nextSectionIndex = findNextSection(paramSection);
            if (nextSectionIndex > 0) {
                paramSection = paramSection.substring(0, nextSectionIndex);
            }

            // 解析参数表格
            List<Map<String, String>> params = parseMarkdownTable(paramSection);
            if (!params.isEmpty()) {
                // 分类参数
                List<Map<String, Object>> queryParams = new ArrayList<>();
                List<Map<String, Object>> bodyParams = new ArrayList<>();

                for (Map<String, String> param : params) {
                    Map<String, Object> paramInfo = new HashMap<>(param);
                    String position = param.getOrDefault("位置", param.get("in"));
                    if ("query".equalsIgnoreCase(position)) {
                        queryParams.add(paramInfo);
                    } else {
                        bodyParams.add(paramInfo);
                    }
                }

                if (!queryParams.isEmpty()) {
                    result.put("queryParams", queryParams);
                }
                if (!bodyParams.isEmpty()) {
                    result.put("bodySchema", bodyParams);
                }
            }
        }
    }

    private void extractResponse(String doc, Map<String, Object> result) {
        // 查找响应部分
        int respIndex = indexOfAny(doc.toLowerCase(),
                "响应", "返回", "response", "返回值", "返回结果");

        if (respIndex >= 0) {
            String respSection = doc.substring(respIndex);
            int nextSectionIndex = findNextSection(respSection);
            if (nextSectionIndex > 0) {
                respSection = respSection.substring(0, nextSectionIndex);
            }

            // 提取响应代码块
            List<Map<String, String>> codeBlocks = extractCodeBlocks(respSection);
            for (Map<String, String> block : codeBlocks) {
                String lang = block.get("language");
                if ("json".equalsIgnoreCase(lang) || lang == null || lang.isEmpty()) {
                    result.put("responseExample", block.get("code"));
                    break;
                }
            }

            // 提取响应字段说明
            List<Map<String, String>> respFields = parseMarkdownTable(respSection);
            if (!respFields.isEmpty()) {
                result.put("responseFields", respFields);
            }
        }
    }

    private void extractExamples(String doc, Map<String, Object> result) {
        // 提取请求示例
        int reqExampleIndex = indexOfAny(doc.toLowerCase(),
                "请求示例", "request example", "curl");

        if (reqExampleIndex >= 0) {
            List<Map<String, String>> codeBlocks = extractCodeBlocks(doc.substring(reqExampleIndex));
            if (!codeBlocks.isEmpty()) {
                result.put("requestExample", codeBlocks.get(0).get("code"));
            }
        }
    }

    private void extractDescription(String doc, Map<String, Object> result) {
        // 提取第一段作为描述
        String[] paragraphs = doc.split("\n\n");
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (!trimmed.isEmpty() && 
                !trimmed.startsWith("#") && 
                !trimmed.startsWith("|") &&
                !trimmed.startsWith("```")) {
                result.put("description", trimmed);
                break;
            }
        }
    }

    private int indexOfAny(String text, String... keywords) {
        int minIndex = -1;
        for (String keyword : keywords) {
            int index = text.indexOf(keyword);
            if (index >= 0 && (minIndex < 0 || index < minIndex)) {
                minIndex = index;
            }
        }
        return minIndex;
    }

    private int findNextSection(String text) {
        // 查找下一个Markdown标题
        Matcher matcher = HEADER_PATTERN.matcher(text);
        if (matcher.find() && matcher.start() > 0) {
            return matcher.start();
        }
        return -1;
    }

    private boolean parseRequired(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase().trim();
        return "是".equals(lower) || "true".equals(lower) || "yes".equals(lower) || "必填".equals(lower);
    }
}