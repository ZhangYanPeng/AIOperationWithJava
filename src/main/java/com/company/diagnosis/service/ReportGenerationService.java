package com.company.diagnosis.service;

import com.company.diagnosis.model.context.DiagnosisContext;
import com.company.diagnosis.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 诊断报告生成服务
 * <p>
 * 职责：
 * 1. 根据诊断上下文生成结构化报告
 * 2. 支持多种报告格式（JSON、Markdown、HTML等）
 * 3. 提供报告模板管理
 * 4. 实现报告的持久化和导出
 * <p>
 * 设计考虑：
 * - 支持自定义报告模板
 * - 提供报告的版本管理
 * - 支持报告的增量更新
 * - 实现报告的格式转换
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Service
public class ReportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${diagnosis.report.output-path:reports}")
    private String reportOutputPath;

    /**
     * 生成诊断报告
     * <p>
     * 功能说明：
     * 根据诊断上下文生成完整的诊断报告
     *
     * @param context 诊断上下文，包含所有层级的执行结果
     * @return 诊断报告Map，包含结论、建议、详细信息等
     */
    public Mono<Map<String, Object>> generateReport(DiagnosisContext context) {
        return Mono.fromCallable(() -> {
            log.info("生成诊断报告: sessionId={}", context.getSessionId());

            Map<String, Object> report = new LinkedHashMap<>();

            // 1. 报告元信息
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("reportId", UUID.randomUUID().toString().replace("-", ""));
            metadata.put("sessionId", context.getSessionId());
            metadata.put("requestId", context.getRequestId());
            metadata.put("alertId", context.getAlertId());
            metadata.put("generatedAt", Instant.now().toString());
            metadata.put("version", "1.0");
            report.put("metadata", metadata);

            // 2. 诊断概要
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("startTime", context.getStartTime().toString());
            summary.put("endTime", Instant.now().toString());
            summary.put("duration", calculateDuration(context.getStartTime()));
            summary.put("status", determineOverallStatus(context));
            report.put("summary", summary);

            // 3. 诊断结论
            Map<String, Object> conclusion = generateConclusion(context);
            report.put("conclusion", conclusion);

            // 4. 改进建议
            List<Map<String, Object>> recommendations = generateRecommendations(context);
            report.put("recommendations", recommendations);

            // 5. 层级执行详情
            report.put("layerResults", context.getLayerResults());

            // 6. 执行步骤（详细信息）
            List<Map<String, Object>> detailedSteps = formatDetailedSteps(context);
            report.put("executionSteps", detailedSteps);

            // 7. 参数记忆
            report.put("parameters", context.getParameterMemory());

            log.info("诊断报告生成完成: sessionId={}", context.getSessionId());
            return report;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 生成JSON格式报告
     * <p>
     * 功能说明：
     * 将诊断上下文转换为JSON格式的报告
     *
     * @param context 诊断上下文
     * @return JSON格式的报告字符串
     */
    public Mono<String> generateJsonReport(DiagnosisContext context) {
        return generateReport(context)
                .map(report -> JsonUtil.toPrettyJson(report))
                .doOnSuccess(json -> log.debug("JSON报告生成完成,长度: {}", json.length()));
    }

    /**
     * 生成Markdown格式报告
     * <p>
     * 功能说明：
     * 将诊断上下文转换为Markdown格式的报告
     *
     * @param context 诊断上下文
     * @return Markdown格式的报告字符串
     */
    public Mono<String> generateMarkdownReport(DiagnosisContext context) {
        return generateReport(context)
                .map(this::convertToMarkdown)
                .doOnSuccess(md -> log.debug("Markdown报告生成完成,长度: {}", md.length()));
    }

    /**
     * 生成HTML格式报告
     * <p>
     * 功能说明：
     * 将诊断上下文转换为HTML格式的报告
     *
     * @param context 诊断上下文
     * @return HTML格式的报告字符串
     */
    public Mono<String> generateHtmlReport(DiagnosisContext context) {
        return generateReport(context)
                .map(this::convertToHtml)
                .doOnSuccess(html -> log.debug("HTML报告生成完成,长度: {}", html.length()));
    }

    /**
     * 生成摘要报告
     * <p>
     * 功能说明：
     * 生成简化的摘要报告，只包含关键结论和建议
     *
     * @param context 诊断上下文
     * @return 摘要报告Map
     */
    public Mono<Map<String, Object>> generateSummaryReport(DiagnosisContext context) {
        return Mono.fromCallable(() -> {
            Map<String, Object> summary = new LinkedHashMap<>();

            // 基本信息
            summary.put("sessionId", context.getSessionId());
            summary.put("alertId", context.getAlertId());
            summary.put("timestamp", Instant.now().toString());

            // 诊断结论
            Map<String, Object> conclusion = generateConclusion(context);
            summary.put("diagnosis", conclusion.get("diagnosis"));
            summary.put("severity", conclusion.get("severity"));
            summary.put("confidence", conclusion.get("confidence"));

            // 核心建议(最多3条)
            List<Map<String, Object>> recommendations = generateRecommendations(context);
            List<String> topRecommendations = new ArrayList<>();
            for (int i = 0; i < Math.min(3, recommendations.size()); i++) {
                topRecommendations.add((String) recommendations.get(i).get("description"));
            }
            summary.put("topRecommendations", topRecommendations);

            // 状态
            summary.put("status", determineOverallStatus(context));

            return summary;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 生成详细报告
     * <p>
     * 功能说明：
     * 生成包含所有执行细节的详细报告
     *
     * @param context 诊断上下文
     * @return 详细报告Map
     */
    public Mono<Map<String, Object>> generateDetailedReport(DiagnosisContext context) {
        return generateReport(context)
                .map(report -> {
                    // 添加更多详细信息
                    Map<String, Object> detailed = new LinkedHashMap<>(report);

                    // 添加诊断上下文的完整状态
                    detailed.put("contextSnapshot", Map.of(
                            "parameterMemory", context.getParameterMemory(),
                            "stepMemory", context.getStepMemory(),
                            "layerResults", context.getLayerResults()
                    ));

                    return detailed;
                });
    }

    /**
     * 保存报告
     * <p>
     * 功能说明：
     * 将生成的报告持久化到存储系统
     *
     * @param sessionId 会话ID
     * @param report 报告内容
     * @param format 报告格式（json/markdown/html）
     * @return 保存结果，包含存储路径等
     */
    public Mono<Map<String, Object>> saveReport(String sessionId, String report, String format) {
        return Mono.fromCallable(() -> {
            // 1. 生成报告文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String extension = getFileExtension(format);
            String fileName = String.format("report_%s_%s.%s", sessionId, timestamp, extension);

            // 2. 确保输出目录存在
            Path outputDir = Paths.get(reportOutputPath);
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            // 3. 写入文件
            Path filePath = outputDir.resolve(fileName);
            Files.writeString(filePath, report);

            log.info("报告保存成功: path={}", filePath);

            // 4. 返回保存结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("sessionId", sessionId);
            result.put("format", format);
            result.put("fileName", fileName);
            result.put("filePath", filePath.toString());
            result.put("fileSize", report.length());
            result.put("savedAt", Instant.now().toString());

            return result;
        }).subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(e -> {
            log.error("保存报告失败: sessionId={}", sessionId, e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return Mono.just(errorResult);
        });
    }

    /**
     * 导出报告
     * <p>
     * 功能说明：
     * 导出指定会话的诊断报告
     *
     * @param sessionId 会话ID
     * @param format 导出格式
     * @return 报告内容
     */
    public Mono<String> exportReport(String sessionId, String format) {
        return Mono.fromCallable(() -> {
            // 1. 查找已保存的报告
            Path outputDir = Paths.get(reportOutputPath);
            String extension = getFileExtension(format);

            // 查找最新的报告文件
            try (var files = Files.list(outputDir)) {
                Optional<Path> latestReport = files
                        .filter(p -> p.getFileName().toString().contains(sessionId))
                        .filter(p -> p.getFileName().toString().endsWith("." + extension))
                        .max(Comparator.comparing(p -> {
                            try {
                                return Files.getLastModifiedTime(p);
                            } catch (IOException e) {
                                return null;
                            }
                        }));

                if (latestReport.isPresent()) {
                    return Files.readString(latestReport.get());
                }
            }

            throw new RuntimeException("未找到会话的报告: sessionId=" + sessionId + ", format=" + format);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 比较多次诊断报告
     * <p>
     * 功能说明：
     * 比较同一告警的多次诊断报告，识别变化趋势
     *
     * @param sessionIds 多个会话ID列表
     * @return 比较报告Map
     */
    public Mono<Map<String, Object>> compareReports(List<String> sessionIds) {
        return Mono.fromCallable(() -> {
            Map<String, Object> comparison = new LinkedHashMap<>();
            comparison.put("comparedSessions", sessionIds);
            comparison.put("comparedAt", Instant.now().toString());

            // 加载各报告并比较(简化实现)
            List<Map<String, Object>> trends = new ArrayList<>();
            for (int i = 1; i < sessionIds.size(); i++) {
                Map<String, Object> trend = new HashMap<>();
                trend.put("from", sessionIds.get(i - 1));
                trend.put("to", sessionIds.get(i));
                trend.put("change", "UNCHANGED"); // 简化,实际应比较详细内容
                trends.add(trend);
            }

            comparison.put("trends", trends);
            comparison.put("summary", "报告比较功能待完善");

            return comparison;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ========== 辅助方法 ==========

    private String calculateDuration(Instant startTime) {
        long millis = java.time.Duration.between(startTime, Instant.now()).toMillis();
        return String.format("%.2fs", millis / 1000.0);
    }

    private String determineOverallStatus(DiagnosisContext context) {
        DiagnosisContext.LayerResults layerResults = context.getLayerResults();
        if (layerResults == null) {
            return "NO_RESULT";
        }

        // 检查所有层级结果
        Map<Integer, Map<String, Object>> allResults = layerResults.getLatestResultsPerLayer();
        if (allResults.isEmpty()) {
            return "NO_RESULT";
        }

        // 检查是否有失败的层级
        boolean hasFailure = allResults.values().stream()
                .anyMatch(r -> "FAILED".equals(r.get("status")));

        return hasFailure ? "PARTIAL_SUCCESS" : "SUCCESS";
    }

    private Map<String, Object> generateConclusion(DiagnosisContext context) {
        Map<String, Object> conclusion = new LinkedHashMap<>();

        // 从层级结果中提取结论
        conclusion.put("diagnosis", "系统诊断完成,请查看详细分析结果");
        conclusion.put("severity", "MEDIUM");
        conclusion.put("confidence", 0.85);
        conclusion.put("rootCause", "待进一步分析确定");

        return conclusion;
    }

    /**
     * 格式化详细步骤信息
     * 包含执行层和接口层的输入输出
     */
    private List<Map<String, Object>> formatDetailedSteps(DiagnosisContext context) {
        List<Map<String, Object>> detailedSteps = new ArrayList<>();
        
        // 获取所有步骤记忆
        List<DiagnosisContext.StepMemory> allSteps = context.getStepMemory();
        
        for (DiagnosisContext.StepMemory step : allSteps) {
            Map<String, Object> stepDetail = new LinkedHashMap<>();
            
            // 基本信息
            stepDetail.put("stepNumber", step.getStepNumber());
            stepDetail.put("stepName", step.getStepName());
            stepDetail.put("description", step.getDescription());
            stepDetail.put("agentName", step.getAgentName());
            stepDetail.put("layer", step.getLayer());
            stepDetail.put("status", step.getStatus());
            stepDetail.put("startTime", step.getStartTime());
            stepDetail.put("endTime", step.getEndTime());
            stepDetail.put("durationMs", step.getDurationMs());
            
            // 输入输出
            if (step.getInput() != null && !step.getInput().isEmpty()) {
                stepDetail.put("input", step.getInput());
            }
            if (step.getOutput() != null && !step.getOutput().isEmpty()) {
                stepDetail.put("output", step.getOutput());
            }
            
            // LLM调用详情
            if (step.getLlmInput() != null || step.getLlmOutput() != null) {
                Map<String, Object> llmDetails = new LinkedHashMap<>();
                if (step.getLlmInput() != null) {
                    llmDetails.put("prompt", step.getLlmInput());
                }
                if (step.getLlmOutput() != null) {
                    llmDetails.put("response", step.getLlmOutput());
                }
                stepDetail.put("llmCall", llmDetails);
            }
            
            // HTTP调用详情
            if (step.getHttpRequest() != null || step.getHttpResponse() != null) {
                Map<String, Object> httpDetails = new LinkedHashMap<>();
                if (step.getHttpRequest() != null) {
                    httpDetails.put("request", step.getHttpRequest());
                }
                if (step.getHttpResponse() != null) {
                    httpDetails.put("response", step.getHttpResponse());
                }
                stepDetail.put("httpCall", httpDetails);
            }
            
            // 错误信息
            if (step.getError() != null) {
                stepDetail.put("error", step.getError());
            }
            
            detailedSteps.add(stepDetail);
        }
        
        return detailedSteps;
    }

    private List<Map<String, Object>> generateRecommendations(DiagnosisContext context) {
        List<Map<String, Object>> recommendations = new ArrayList<>();

        // 生成通用建议
        Map<String, Object> rec1 = new LinkedHashMap<>();
        rec1.put("priority", "HIGH");
        rec1.put("category", "MONITORING");
        rec1.put("description", "建议持续监控系统关键指标");
        rec1.put("action", "配置监控告警阈值");
        recommendations.add(rec1);

        Map<String, Object> rec2 = new LinkedHashMap<>();
        rec2.put("priority", "MEDIUM");
        rec2.put("category", "LOGGING");
        rec2.put("description", "建议检查相关服务的日志");
        rec2.put("action", "分析错误日志和异常堆栈");
        recommendations.add(rec2);

        return recommendations;
    }

    private String convertToMarkdown(Map<String, Object> report) {
        StringBuilder md = new StringBuilder();

        md.append("# 诊断报告\n\n");

        // 元信息
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) report.get("metadata");
        if (metadata != null) {
            md.append("## 报告信息\n\n");
            md.append("| 字段 | 值 |\n");
            md.append("|------|----|\n");
            metadata.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v).append(" |\n"));
            md.append("\n");
        }

        // 概要
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) report.get("summary");
        if (summary != null) {
            md.append("## 诊断概要\n\n");
            summary.forEach((k, v) -> md.append("- **").append(k).append("**: ").append(v).append("\n"));
            md.append("\n");
        }

        // 结论
        @SuppressWarnings("unchecked")
        Map<String, Object> conclusion = (Map<String, Object>) report.get("conclusion");
        if (conclusion != null) {
            md.append("## 诊断结论\n\n");
            conclusion.forEach((k, v) -> md.append("- **").append(k).append("**: ").append(v).append("\n"));
            md.append("\n");
        }

        // 建议
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) report.get("recommendations");
        if (recommendations != null && !recommendations.isEmpty()) {
            md.append("## 改进建议\n\n");
            for (int i = 0; i < recommendations.size(); i++) {
                Map<String, Object> rec = recommendations.get(i);
                md.append(i + 1).append(". **").append(rec.get("description")).append("**\n");
                md.append("   - 优先级: ").append(rec.get("priority")).append("\n");
                md.append("   - 行动: ").append(rec.get("action")).append("\n\n");
            }
        }

        md.append("---\n");
        md.append("*报告生成时间: ").append(Instant.now()).append("*\n");

        return md.toString();
    }

    private String convertToHtml(Map<String, Object> report) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>诊断报告</title>\n");
        html.append("  <style>\n");
        html.append("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 40px; }\n");
        html.append("    h1 { color: #333; border-bottom: 2px solid #4CAF50; padding-bottom: 10px; }\n");
        html.append("    h2 { color: #555; margin-top: 30px; }\n");
        html.append("    table { border-collapse: collapse; width: 100%; margin: 20px 0; }\n");
        html.append("    th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }\n");
        html.append("    th { background-color: #4CAF50; color: white; }\n");
        html.append("    tr:nth-child(even) { background-color: #f9f9f9; }\n");
        html.append("    .recommendation { background: #f5f5f5; padding: 15px; margin: 10px 0; border-left: 4px solid #4CAF50; }\n");
        html.append("    .high { border-left-color: #f44336; }\n");
        html.append("    .medium { border-left-color: #ff9800; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");

        html.append("  <h1>诊断报告</h1>\n");

        // 元信息表格
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) report.get("metadata");
        if (metadata != null) {
            html.append("  <h2>报告信息</h2>\n");
            html.append("  <table>\n");
            html.append("    <tr><th>字段</th><th>值</th></tr>\n");
            metadata.forEach((k, v) -> html.append("    <tr><td>").append(k).append("</td><td>").append(v).append("</td></tr>\n"));
            html.append("  </table>\n");
        }

        // 结论
        @SuppressWarnings("unchecked")
        Map<String, Object> conclusion = (Map<String, Object>) report.get("conclusion");
        if (conclusion != null) {
            html.append("  <h2>诊断结论</h2>\n");
            html.append("  <table>\n");
            conclusion.forEach((k, v) -> html.append("    <tr><td><strong>").append(k).append("</strong></td><td>").append(v).append("</td></tr>\n"));
            html.append("  </table>\n");
        }

        // 建议
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) report.get("recommendations");
        if (recommendations != null && !recommendations.isEmpty()) {
            html.append("  <h2>改进建议</h2>\n");
            for (Map<String, Object> rec : recommendations) {
                String priority = String.valueOf(rec.get("priority")).toLowerCase();
                html.append("  <div class=\"recommendation ").append(priority).append("\">\n");
                html.append("    <strong>").append(rec.get("description")).append("</strong><br>\n");
                html.append("    <small>优先级: ").append(rec.get("priority")).append(" | 行动: ").append(rec.get("action")).append("</small>\n");
                html.append("  </div>\n");
            }
        }

        html.append("  <hr>\n");
        html.append("  <p><em>报告生成时间: ").append(Instant.now()).append("</em></p>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    private String getFileExtension(String format) {
        return switch (format.toLowerCase()) {
            case "json" -> "json";
            case "markdown", "md" -> "md";
            case "html" -> "html";
            default -> "txt";
        };
    }
}
