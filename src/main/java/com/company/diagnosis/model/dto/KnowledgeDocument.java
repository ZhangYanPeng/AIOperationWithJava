package com.company.diagnosis.model.dto;

import lombok.Data;
import java.util.Map;

/**
 * 知识文档DTO
 * <p>
 * 职责：
 * 表示知识库中的一个文档
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Data
public class KnowledgeDocument {
    private String docId;
    private String title;
    private String content;
    private String type;
    private Map<String, Object> metadata;
    private float[] embedding;
}
