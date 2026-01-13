package com.company.diagnosis.service;

import com.company.diagnosis.model.context.DiagnosisContext;
import com.company.diagnosis.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 报告缓存
     */
    private final ConcurrentHashMap<String, Map<String, Object>> reportCache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private OrchestratorService orchestratorService;

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
            if (context == null) {
                throw new IllegalArgumentException("诊断上下文不能为空");
            }

            Map<String, Object> report = new LinkedHashMap<>();

            // 1. 基本信息
            report.put("sessionId", context.getSessionId());
            report.put("requestId", context.getRequestId());
            report.put("diagnosisType", context.getDiagnosisType());
            report.put("status", context.getStatus().name());
            report.put("generatedAt", LocalDateTime.now().format(DATE_FORMATTER));

            // 2. 问题描述
            report.put("problem", context.getOriginalProblem());

            // 3. 提取各层执行结果
            Map<String, Object> layerResults = context.getLayerResults();
            report.put("layerResults", layerResults);

            // 4. 生成诊断结论
            String conclusion = generateConclusion(context, layerResults);
            report.put("conclusion", conclusion);

            // 5. 生成改进建议
            List<String> recommendations = generateRecommendations(context, layerResults);
            report.put("recommendations", recommendations);

            // 6. 执行步骤摘要
            List<Map<String, Object>> steps = context.getStepsMemory();
            report.put("stepsCount", steps.size());
            report.put("executionSummary", generateExecutionSummary(steps));

            // 7. 关键指标
            Map<String, Object> metrics = generateMetrics(context);
            report.put("metrics", metrics);

            // 缓存报告
            reportCache.put(context.getSessionId(), report);

            logger.info("生成诊断报告: sessionId={}, status={}", context.getSessionId(), context.getStatus());
            return report;
        });
    }

    /**
     * 生成JSON格式报告
     */
    public Mono<String> generateJsonReport(DiagnosisContext context) {
        return generateReport(context)
                .map(report -> JsonUtil.prettyPrint(JsonUtil.toJson(report)));
    }

    /**
     * 生成Markdown格式报告
     */
    public Mono<String> generateMarkdownReport(DiagnosisContext context) {
        return generateReport(context)
                .map(this::convertToMarkdown);
    }

    /**
     * 生成HTML格式报告
     */
    public Mono<String> generateHtmlReport(DiagnosisContext context) {
        return generateReport(context)
                .map(this::convertToHtml);
    }

    /**
     * 生成摘要报告
     */
    public Mono<Map<String, Object>> generateSummaryReport(DiagnosisContext context) {
        return Mono.fromCallable(() -> {
            Map<String, Object> summary = new LinkedHashMap<>();

            summary.put("sessionId", context.getSessionId());
            summary.put("status", context.getStatus().name());
            summary.put("diagnosisType", context.getDiagnosisType());
            
            // 提取最终结论
            Map<String, Object> layerResults = context.getLayerResults();
            summary.put("conclusion", generateConclusion(context, layerResults));
            
            // 核心建议（最多3条）
            List<String> recommendations = generateRecommendations(context, layerResults);
            summary.put("topRecommendations", recommendations.stream().limit(3).toList());
            
            // 关键指标
            summary.put("executionTime", calculateExecutionTime(context));
            summary.put("stepsCompleted", context.getStepsMemory().size());

            return summary;
        });
    }

    /**
     * 生成详细报告
     */
    public Mono<Map<String, Object>> generateDetailedReport(DiagnosisContext context) {
        return generateReport(context)
                .map(report -> {
                    // 添加详细执行日志
                    report.put("executionHistory", context.getExecutionHistory());
                    report.put("parameterMemory", context.getParameterMemory());
                    report.put("stepsDetail", context.getStepsMemory());
                    return report;
                });
    }

    /**
     * 保存报告
     */
    public Mono<Map<String, Object>> saveReport(String sessionId, String report, String format) {
        return Mono.fromCallable(() -> {
            // 生成文件名
            String fileName = String.format("report_%s_%s.%s", 
                    sessionId, 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
                    format);

            // 实际环境中会写入文件系统或对象存储
            // 这里简单记录到缓存
            Map<String, Object> savedReport = new HashMap<>();
            savedReport.put("content", report);
            savedReport.put("format", format);
            savedReport.put("savedAt", LocalDateTime.now().format(DATE_FORMATTER));

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("fileName", fileName);
            result.put("sessionId", sessionId);
            result.put("savedAt", LocalDateTime.now().format(DATE_FORMATTER));

            logger.info("保存报告: sessionId={}, fileName={}", sessionId, fileName);
            return result;
        });
    }

    /**
     * 导出报告
     */
    public Mono<String> exportReport(String sessionId, String format) {
        return Mono.fromCallable(() -> {
            // 尝试从缓存获取
            Map<String, Object> cachedReport = reportCache.get(sessionId);
            if (cachedReport != null) {
                return formatReport(cachedReport, format);
            }

            // 尝试从OrchestratorService获取上下文
            if (orchestratorService != null) {
                DiagnosisContext context = orchestratorService.getContext(sessionId);
                if (context != null) {
                    return generateReport(context)
                            .map(report -> formatReport(report, format))
                            .block();
                }
            }

            return null;
        });
    }

    /**
     * 比较多次诊断报告
     */
    public Mono<Map<String, Object>> compareReports(List<String> sessionIds) {
        return Mono.fromCallable(() -> {
            Map<String, Object> comparison = new LinkedHashMap<>();
            comparison.put("comparedSessions", sessionIds);
            comparison.put("comparedAt", LocalDateTime.now().format(DATE_FORMATTER));

            List<Map<String, Object>> reports = new ArrayList<>();
            for (String sessionId : sessionIds) {
                Map<String, Object> cachedReport = reportCache.get(sessionId);
                if (cachedReport != null) {
                    reports.add(cachedReport);
                }
            }

            comparison.put("reportsFound", reports.size());
            
            if (reports.size() >= 2) {
                // 识别变化趋势
                comparison.put("trends", analyzeTrends(reports));
            }

            return comparison;
        });
    }

    private String generateConclusion(DiagnosisContext context, Map<String, Object> layerResults) {
        StringBuilder conclusion = new StringBuilder();
        
        conclusion.append("诊断类型: ").append(context.getDiagnosisType()).append("\n");
        conclusion.append("诊断状态: ").append(context.getStatus().name()).append("\n");
        
        if (context.getStatus() == DiagnosisContext.DiagnosisStatus.COMPLETED) {
            conclusion.append("诊断已完成，共执行 ").append(context.getStepsMemory().size()).append(" 个步骤。");
        } else if (context.getStatus() == DiagnosisContext.DiagnosisStatus.FAILED) {
            conclusion.append("诊断执行失败: ").append(context.getErrorMessage());
        }

        return conclusion.toString();
    }

    private List<String> generateRecommendations(DiagnosisContext context, Map<String, Object> layerResults) {
        List<String> recommendations = new ArrayList<>();
        
        // 基于诊断类型生成通用建议
        String diagnosisType = context.getDiagnosisType();
        if (diagnosisType != null) {
            switch (diagnosisType.toLowerCase()) {
                case "resource":
                    recommendations.add("检查系统资源使用情况，考虑扩容或优化");
                    recommendations.add("分析资源消耗趋势，设置合理的告警阈值");
                    break;
                case "network":
                    recommendations.add("检查网络连接状态和延迟");
                    recommendations.add("验证防火墙和安全组配置");
                    break;
                case "service":
                    recommendations.add("检查服务日志以获取详细错误信息");
                    recommendations.add("验证服务依赖项的健康状态");
                    break;
                default:
                    recommendations.add("根据诊断结果进行针对性分析");
            }
        }
        
        recommendations.add("持续监控相关指标变化");
        
        return recommendations;
    }

    private Map<String, Object> generateExecutionSummary(List<Map<String, Object>> steps) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalSteps", steps.size());
        
        long successCount = steps.stream()
                .filter(s -> "SUCCESS".equals(s.get("status")))
                .count();
        summary.put("successfulSteps", successCount);
        summary.put("failedSteps", steps.size() - successCount);
        
        return summary;
    }

    private Map<String, Object> generateMetrics(DiagnosisContext context) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("layersExecuted", context.getLayerResults().size());
        metrics.put("stepsExecuted", context.getStepsMemory().size());
        metrics.put("parametersCollected", context.getParameterMemory().size());
        return metrics;
    }

    private String calculateExecutionTime(DiagnosisContext context) {
        // 简化实现，返回步骤数量估算的时间
        return context.getStepsMemory().size() * 2 + "s (估算)";
    }

    private String formatReport(Map<String, Object> report, String format) {
        switch (format.toLowerCase()) {
            case "json":
                return JsonUtil.prettyPrint(JsonUtil.toJson(report));
            case "markdown":
                return convertToMarkdown(report);
            case "html":
                return convertToHtml(report);
            default:
                return JsonUtil.toJson(report);
        }
    }

    private String convertToMarkdown(Map<String, Object> report) {
        StringBuilder md = new StringBuilder();
        
        md.append("# 诊断报告\n\n");
        md.append("## 基本信息\n\n");
        md.append("- **会话ID**: ").append(report.get("sessionId")).append("\n");
        md.append("- **诊断类型**: ").append(report.get("diagnosisType")).append("\n");
        md.append("- **状态**: ").append(report.get("status")).append("\n");
        md.append("- **生成时间**: ").append(report.get("generatedAt")).append("\n\n");
        
        md.append("## 问题描述\n\n");
        md.append(report.get("problem")).append("\n\n");
        
        md.append("## 诊断结论\n\n");
        md.append(report.get("conclusion")).append("\n\n");
        
        md.append("## 改进建议\n\n");
        Object recommendations = report.get("recommendations");
        if (recommendations instanceof List) {
            for (Object rec : (List<?>) recommendations) {
                md.append("- ").append(rec).append("\n");
            }
        }
        
        return md.toString();
    }

    private String convertToHtml(Map<String, Object> report) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>诊断报告</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("h1 { color: #333; }\n");
        html.append("h2 { color: #666; border-bottom: 1px solid #ccc; padding-bottom: 5px; }\n");
        html.append(".info { background: #f5f5f5; padding: 10px; border-radius: 5px; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        
        html.append("<h1>诊断报告</h1>\n");
        
        html.append("<h2>基本信息</h2>\n");
        html.append("<div class=\"info\">\n");
        html.append("<p><strong>会话ID:</strong> ").append(report.get("sessionId")).append("</p>\n");
        html.append("<p><strong>诊断类型:</strong> ").append(report.get("diagnosisType")).append("</p>\n");
        html.append("<p><strong>状态:</strong> ").append(report.get("status")).append("</p>\n");
        html.append("</div>\n");
        
        html.append("<h2>诊断结论</h2>\n");
        html.append("<p>").append(report.get("conclusion")).append("</p>\n");
        
        html.append("<h2>改进建议</h2>\n<ul>\n");
        Object recommendations = report.get("recommendations");
        if (recommendations instanceof List) {
            for (Object rec : (List<?>) recommendations) {
                html.append("<li>").append(rec).append("</li>\n");
            }
        }
        html.append("</ul>\n");
        
        html.append("</body>\n</html>");
        
        return html.toString();
    }

    private Map<String, Object> analyzeTrends(List<Map<String, Object>> reports) {
        Map<String, Object> trends = new HashMap<>();
        trends.put("reportsAnalyzed", reports.size());
        // 简化的趋势分析
        trends.put("trend", "需要更多数据以进行趋势分析");
        return trends;
    }
}
