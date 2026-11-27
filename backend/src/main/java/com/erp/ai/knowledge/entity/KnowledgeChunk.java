// @feature FEAT-AIKB-001
package com.erp.ai.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_ai_knowledge_chunk")
public class KnowledgeChunk {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("doc_id")
    private Long docId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    @TableField("content")
    private String content;

    @TableField("keywords")
    private String keywords;

    @TableField("embedding")
    private String embedding;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
