package com.company.diagnosis.llm;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * LLM客户端接口
 * <p>
 * 职责：
 * - 定义LLM调用的统一接口
 * - 支持同步/异步调用
 * - 支持流式输出
 * <p>
 * 设计考虑：
 * - 响应式编程，返回 Mono/Flux
 * - 支持多种LLM提供商（OpenAI、DashScope、Ollama等）
 * - 提供JSON和文本两种输出模式
 *
 * @author Diagnosis System
 * @since 2026-01-13
 */
public interface LlmClient {

    /**
     * 生成JSON格式响应
     * <p>
     * 调用LLM生成JSON格式的响应，自动处理JSON提取和解析
     *
     * @param prompt       用户提示词
     * @param systemPrompt 系统提示词
     * @param temperature  温度参数（0-1，越低越确定性）
     * @return Mono包装的LlmResponse对象
     */
    Mono<LlmResponse> generateJson(String prompt, String systemPrompt, double temperature);

    /**
     * 生成JSON格式响应（使用默认温度）
     *
     * @param prompt       用户提示词
     * @param systemPrompt 系统提示词
     * @return Mono包装的LlmResponse对象
     */
    default Mono<LlmResponse> generateJson(String prompt, String systemPrompt) {
        return generateJson(prompt, systemPrompt, 0.3);
    }

    /**
     * 生成文本响应
     * <p>
     * 调用LLM生成纯文本响应
     *
     * @param prompt       用户提示词
     * @param systemPrompt 系统提示词
     * @param temperature  温度参数
     * @return Mono包装的响应文本
     */
    Mono<String> generateText(String prompt, String systemPrompt, double temperature);

    /**
     * 流式生成文本
     * <p>
     * 以流式方式调用LLM，逐token返回结果
     *
     * @param prompt       用户提示词
     * @param systemPrompt 系统提示词
     * @param temperature  温度参数
     * @return Flux流式输出，每个元素为一个token片段
     */
    Flux<String> streamText(String prompt, String systemPrompt, double temperature);

    /**
     * 流式生成文本（使用默认温度）
     *
     * @param prompt       用户提示词
     * @param systemPrompt 系统提示词
     * @return Flux流式输出
     */
    default Flux<String> streamText(String prompt, String systemPrompt) {
        return streamText(prompt, systemPrompt, 0.3);
    }

    /**
     * 检查客户端是否可用
     *
     * @return Mono包装的可用性状态
     */
    Mono<Boolean> isAvailable();

    /**
     * 获取客户端名称
     *
     * @return 客户端名称
     */
    String getName();

    /**
     * 获取默认模型名称
     *
     * @return 模型名称
     */
    String getDefaultModel();
}
