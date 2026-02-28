package com.company.diagnosis.llm;

import com.company.diagnosis.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAI兼容API客户端
 * <p>
 * 职责：
 * - 调用OpenAI兼容的 /v1/chat/completions 接口
 * - 支持流式和非流式两种模式
 * - 提供JSON容错解析能力
 * - 支持思维链（reasoning_content）提取
 * <p>
 * 兼容的服务：
 * - OpenAI API
 * - DashScope（通义千问）
 * - Ollama
 * - 其他OpenAI兼容服务
 *
 * @author Diagnosis System
 * @since 2026-01-13
 */
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    /**
     * API路径
     */
    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    /**
     * 匹配```json代码块的正则表达式
     */
    private static final Pattern JSON_CODE_BLOCK_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)```");

    /**
     * 匹配<think>标签的正则表达式
     */
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>([\\s\\S]*?)</think>");

    /**
     * WebClient实例
     */
    private final WebClient webClient;

    /**
     * 默认模型名称
     */
    private final String defaultModel;

    /**
     * 客户端名称
     */
    private final String clientName;

    /**
     * 最大重试次数
     */
    private final int maxRetries;

    /**
     * 请求超时时间（秒）
     */
    private final int timeoutSeconds;

    /**
     * 是否启用深度思考（思维链）
     */
    private final boolean enableDeepThinking;

    /**
     * 构造函数
     *
     * @param baseUrl           API基础URL
     * @param defaultModel      默认模型名称
     * @param clientName        客户端名称
     * @param maxRetries        最大重试次数
     * @param timeoutSeconds    超时秒数
     * @param enableDeepThinking 是否启用深度思考
     */
    public OpenAiCompatibleClient(String baseUrl, String defaultModel, String clientName,
                                  int maxRetries, int timeoutSeconds, boolean enableDeepThinking) {
        this.defaultModel = defaultModel;
        this.clientName = clientName;
        this.maxRetries = maxRetries;
        this.timeoutSeconds = timeoutSeconds;
        this.enableDeepThinking = enableDeepThinking;

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        logger.info("OpenAI兼容客户端初始化: name={}, baseUrl={}, model={}, timeout={}s",
                clientName, baseUrl, defaultModel, timeoutSeconds);
    }

    /**
     * 构造函数（使用默认配置）
     *
     * @param baseUrl      API基础URL
     * @param defaultModel 默认模型名称
     */
    public OpenAiCompatibleClient(String baseUrl, String defaultModel) {
        this(baseUrl, defaultModel, "OpenAI-Compatible", 3, 300, false);
    }

    /**
     * 构造函数（简化版本）
     *
     * @param provider        提供商名称
     * @param baseUrl         API基础URL
     * @param apiKey          API密钥（可为null）
     * @param timeout         超时时间（秒）
     * @param defaultModel    默认模型
     * @param webClientBuilder WebClient构建器
     */
    public OpenAiCompatibleClient(String provider, String baseUrl, String apiKey,
                                  int timeout, String defaultModel, WebClient.Builder webClientBuilder) {
        this.defaultModel = defaultModel;
        this.clientName = provider;
        this.maxRetries = 3;
        this.timeoutSeconds = timeout;
        this.enableDeepThinking = false;

        WebClient.Builder builder = webClientBuilder
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));

        // 如果提供API密钥，添加Authorization头
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        this.webClient = builder.build();

        logger.info("OpenAI兼容客户端初始化: provider={}, baseUrl={}, model={}, timeout={}s",
                provider, baseUrl, defaultModel, timeout);
    }

    @Override
    public Mono<LlmResponse> generateJson(String prompt, String systemPrompt, double temperature) {
        long startTime = System.currentTimeMillis();

        return callChatCompletions(prompt, systemPrompt, temperature, false)
                .map(rawResponse -> parseJsonResponse(rawResponse, startTime))
                .onErrorResume(e -> {
                    logger.error("LLM调用失败: {}", e.getMessage());
                    return Mono.just(LlmResponse.error(e.getMessage(), getErrorType(e)));
                })
                .retryWhen(Retry.backoff(maxRetries, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(10))
                        .filter(this::isRetryableError)
                        .doBeforeRetry(signal -> logger.warn("LLM调用重试 {}/{}: {}",
                                signal.totalRetries() + 1, maxRetries, signal.failure().getMessage())));
    }

    @Override
    public Mono<String> generateText(String prompt, String systemPrompt, double temperature) {
        return callChatCompletions(prompt, systemPrompt, temperature, false)
                .map(this::extractContentFromResponse)
                .onErrorResume(e -> {
                    logger.error("LLM文本生成失败: {}", e.getMessage());
                    return Mono.just("生成失败: " + e.getMessage());
                });
    }

    @Override
    public Flux<String> streamText(String prompt, String systemPrompt, double temperature) {
        Map<String, Object> requestBody = buildRequestBody(prompt, systemPrompt, temperature, true);

        logger.info("开始流式调用LLM: model={}", defaultModel);

        return webClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .flatMap(this::parseStreamChunk)
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnError(e -> logger.error("流式调用失败: {}", e.getMessage()))
                .onErrorResume(e -> {
                    logger.warn("流式调用失败，降级到非流式模式");
                    return generateText(prompt, systemPrompt, temperature)
                            .flatMapMany(text -> Flux.just(text));
                });
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return webClient.get()
                .uri("/v1/models")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .map(response -> true)
                .onErrorReturn(false);
    }

    @Override
    public String getName() {
        return clientName;
    }

    @Override
    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * 调用ChatCompletions API
     */
    private Mono<String> callChatCompletions(String prompt, String systemPrompt,
                                             double temperature, boolean stream) {
        Map<String, Object> requestBody = buildRequestBody(prompt, systemPrompt, temperature, stream);

        logger.debug("调用LLM: model={}, stream={}", defaultModel, stream);

        return webClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnSuccess(response -> logger.debug("LLM响应成功，长度: {} 字符",
                        response != null ? response.length() : 0));
    }

    /**
     * 构建请求体
     */
    private Map<String, Object> buildRequestBody(String prompt, String systemPrompt,
                                                 double temperature, boolean stream) {
        List<Map<String, String>> messages = new ArrayList<>();

        // 添加系统提示词
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }

        // 添加用户提示词
        String userContent = prompt;
        if (!enableDeepThinking) {
            // 禁用深度思考时添加指令
            userContent = prompt + "\n/no_think";
        }
        messages.add(Map.of("role", "user", "content", userContent));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", defaultModel);
        requestBody.put("messages", messages);
        requestBody.put("stream", stream);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", 4000);

        // 某些模型支持的参数
        if (!enableDeepThinking) {
            requestBody.put("enable_thinking", false);
        }

        return requestBody;
    }

    /**
     * 解析JSON响应
     */
    private LlmResponse parseJsonResponse(String rawResponse, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        try {
            // 1. 提取content字段
            String content = extractContentFromResponse(rawResponse);
            if (content == null || content.isEmpty()) {
                return LlmResponse.error("响应内容为空", "empty_response", rawResponse);
            }

            // 2. 提取思维链内容
            String thinkingContent = extractThinkingContent(content);

            // 3. 移除思维链标签
            String cleanedContent = removeThinkingTags(content);

            // 4. 尝试解析JSON
            Map<String, Object> data = parseJsonContent(cleanedContent);
            if (data.isEmpty()) {
                // 容错：尝试从原始内容提取JSON
                data = JsonUtil.extractAndParseJson(cleanedContent);
                if (!data.isEmpty()) {
                    LlmResponse response = LlmResponse.successWithFallback(data, rawResponse);
                    response.setThinkingContent(thinkingContent);
                    response.setModel(defaultModel);
                    response.setDurationMs(duration);
                    return response;
                }
                return LlmResponse.jsonParseError(rawResponse);
            }

            LlmResponse response = LlmResponse.success(data, rawResponse, thinkingContent);
            response.setModel(defaultModel);
            response.setDurationMs(duration);
            return response;

        } catch (Exception e) {
            logger.error("解析LLM响应失败: {}", e.getMessage());
            return LlmResponse.error("解析响应失败: " + e.getMessage(), "parse_error", rawResponse);
        }
    }

    /**
     * 从响应中提取content字段
     */
    private String extractContentFromResponse(String rawResponse) {
        try {
            JsonNode root = JsonUtil.parseJson(rawResponse);
            if (root == null) {
                return rawResponse;
            }

            // OpenAI格式: choices[0].message.content
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        return content.asText();
                    }
                }
            }

            return rawResponse;
        } catch (Exception e) {
            logger.warn("提取content失败: {}", e.getMessage());
            return rawResponse;
        }
    }

    /**
     * 提取思维链内容
     */
    private String extractThinkingContent(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = THINK_TAG_PATTERN.matcher(content);
        StringBuilder thinking = new StringBuilder();
        while (matcher.find()) {
            if (thinking.length() > 0) {
                thinking.append("\n");
            }
            thinking.append(matcher.group(1).trim());
        }
        return thinking.length() > 0 ? thinking.toString() : null;
    }

    /**
     * 移除思维链标签
     */
    private String removeThinkingTags(String content) {
        if (content == null) {
            return null;
        }
        String result = THINK_TAG_PATTERN.matcher(content).replaceAll("");
        return result.replace("</think>", "").trim();
    }

    /**
     * 解析JSON内容
     */
    private Map<String, Object> parseJsonContent(String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. 尝试直接解析
        Map<String, Object> result = JsonUtil.jsonToMap(content);
        if (!result.isEmpty()) {
            return result;
        }

        // 2. 尝试从代码块提取
        Matcher matcher = JSON_CODE_BLOCK_PATTERN.matcher(content);
        if (matcher.find()) {
            String jsonStr = matcher.group(1).trim();
            result = JsonUtil.jsonToMap(jsonStr);
            if (!result.isEmpty()) {
                return result;
            }
        }

        // 3. 使用容错解析
        return JsonUtil.extractAndParseJson(content);
    }

    /**
     * 解析流式响应块
     */
    private Flux<String> parseStreamChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return Flux.empty();
        }

        // 处理SSE格式
        String data = chunk;
        if (chunk.startsWith("data: ")) {
            data = chunk.substring(6);
        }
        data = data.trim();

        if (data.isEmpty() || "[DONE]".equals(data)) {
            return Flux.empty();
        }

        try {
            JsonNode root = JsonUtil.parseJson(data);
            if (root == null) {
                return Flux.empty();
            }

            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return Flux.empty();
            }

            JsonNode delta = choices.get(0).get("delta");
            if (delta == null) {
                return Flux.empty();
            }

            List<String> results = new ArrayList<>();

            // 提取reasoning_content（思维链）
            JsonNode reasoning = delta.get("reasoning_content");
            if (reasoning != null && !reasoning.isNull()) {
                String reasoningText = reasoning.asText();
                if (!reasoningText.isEmpty()) {
                    // 包装在<think>标签中
                    results.add("<think>" + reasoningText + "</think>");
                }
            }

            // 提取content
            JsonNode content = delta.get("content");
            if (content != null && !content.isNull()) {
                String contentText = content.asText();
                if (!contentText.isEmpty()) {
                    results.add(contentText);
                }
            }

            return Flux.fromIterable(results);

        } catch (Exception e) {
            logger.debug("解析流式块失败: {}", e.getMessage());
            return Flux.empty();
        }
    }

    /**
     * 判断是否为可重试的错误
     */
    private boolean isRetryableError(Throwable e) {
        if (e instanceof WebClientResponseException) {
            int statusCode = ((WebClientResponseException) e).getStatusCode().value();
            // 5xx错误和429（限流）可重试
            return statusCode >= 500 || statusCode == 429;
        }
        // 超时错误可重试
        return e instanceof java.util.concurrent.TimeoutException;
    }

    /**
     * 获取错误类型
     */
    private String getErrorType(Throwable e) {
        if (e instanceof WebClientResponseException) {
            int statusCode = ((WebClientResponseException) e).getStatusCode().value();
            if (statusCode == 429) {
                return "rate_limit";
            } else if (statusCode >= 500) {
                return "server_error";
            } else {
                return "http_error_" + statusCode;
            }
        }
        if (e instanceof java.util.concurrent.TimeoutException) {
            return "timeout";
        }
        return "unknown";
    }

    /**
     * Builder类，用于构建OpenAiCompatibleClient实例
     */
    public static class Builder {
        private String baseUrl;
        private String defaultModel = "gpt-3.5-turbo";
        private String clientName = "OpenAI-Compatible";
        private int maxRetries = 3;
        private int timeoutSeconds = 300;
        private boolean enableDeepThinking = false;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        public Builder clientName(String clientName) {
            this.clientName = clientName;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder enableDeepThinking(boolean enableDeepThinking) {
            this.enableDeepThinking = enableDeepThinking;
            return this;
        }

        public OpenAiCompatibleClient build() {
            if (baseUrl == null || baseUrl.isEmpty()) {
                throw new IllegalArgumentException("baseUrl不能为空");
            }
            return new OpenAiCompatibleClient(baseUrl, defaultModel, clientName,
                    maxRetries, timeoutSeconds, enableDeepThinking);
        }
    }

    /**
     * 创建Builder
     */
    public static Builder builder() {
        return new Builder();
    }
}
