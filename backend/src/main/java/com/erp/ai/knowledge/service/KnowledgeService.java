// @feature FEAT-AIKB-001
package com.erp.ai.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.ai.knowledge.entity.KnowledgeChunk;
import com.erp.ai.knowledge.entity.KnowledgeDoc;
import com.erp.ai.knowledge.mapper.KnowledgeChunkMapper;
import com.erp.ai.knowledge.mapper.KnowledgeDocMapper;
import com.erp.tenant.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final DocumentParserService parserService;
    private final TextChunkerService chunkerService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String STORAGE_DIR = "D:/erp/deploy/knowledge-files";
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public List<KnowledgeDoc> list(String module) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        if (module != null && !module.isBlank()) wrapper.eq(KnowledgeDoc::getModule, module);
        wrapper.orderByDesc(KnowledgeDoc::getCreatedAt);
        return docMapper.selectList(wrapper);
    }

    public KnowledgeDoc upload(MultipartFile file, String title, String module, String createdBy) throws Exception {
        String originalName = file.getOriginalFilename();
        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!List.of("pdf", "docx", "xlsx", "txt", "md").contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型: " + ext + "（仅支持 pdf/docx/xlsx/txt/md）");
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过20MB");
        }

        File dir = new File(STORAGE_DIR);
        if (!dir.exists()) dir.mkdirs();
        String storedName = UUID.randomUUID() + "." + ext;
        File dest = new File(dir, storedName);
        file.transferTo(dest);

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setTitle(title != null && !title.isBlank() ? title : originalName);
        doc.setModule(module != null && !module.isBlank() ? module : "general");
        doc.setFilePath(dest.getAbsolutePath());
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setStatus("parsing");
        doc.setCreatedBy(createdBy);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.insert(doc);

        // 异步解析+切片+向量化
        submitWithTenant(() -> processDoc(doc));
        return doc;
    }

    /**
     * 线程池任务透传租户上下文：ThreadLocal 不会继承到线程池线程，
     * 不透传的话 processDoc 写库会落到 default 数据源（= 租户1的库）。
     */
    private void submitWithTenant(Runnable task) {
        final Long tenantId = TenantContextHolder.getTenantId();
        final String tenantDbName = TenantContextHolder.getTenantDbName();
        executor.submit(() -> {
            if (tenantId != null) {
                TenantContextHolder.setTenantId(tenantId);
            }
            if (tenantDbName != null) {
                TenantContextHolder.setTenantDbName(tenantDbName);
            }
            try {
                task.run();
            } finally {
                // 线程池线程会被复用，必须清理上下文，否则残留会污染下一个任务
                TenantContextHolder.clear();
            }
        });
    }

    private void processDoc(KnowledgeDoc doc) {
        try {
            File file = new File(doc.getFilePath());
            String text = parserService.parse(file, doc.getFileType());
            List<String> chunks = chunkerService.chunk(text);

            if (chunks.isEmpty()) {
                doc.setStatus("failed");
                doc.setErrorMsg("文档解析后无有效文本");
                doc.setUpdatedAt(LocalDateTime.now());
                docMapper.updateById(doc);
                return;
            }

            List<double[]> vectors = embeddingService.embed(chunks);
            int count = 0;
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocId(doc.getId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunk.setKeywords(chunkerService.extractKeywords(chunks.get(i)));
                chunk.setEmbedding(objectMapper.writeValueAsString(vectors.get(i)));
                chunk.setCreatedAt(LocalDateTime.now());
                chunkMapper.insert(chunk);
                count++;
            }

            doc.setStatus("ready");
            doc.setChunkCount(count);
            doc.setUpdatedAt(LocalDateTime.now());
            docMapper.updateById(doc);
            log.info("[AI知识库] 文档处理完成: id={}, chunks={}", doc.getId(), count);
        } catch (Exception e) {
            log.error("[AI知识库] 文档处理失败: id=" + doc.getId(), e);
            doc.setStatus("failed");
            doc.setErrorMsg(e.getMessage());
            doc.setUpdatedAt(LocalDateTime.now());
            docMapper.updateById(doc);
        }
    }

    public void delete(Long id) {
        KnowledgeDoc doc = docMapper.selectById(id);
        if (doc != null) {
            File f = new File(doc.getFilePath());
            if (f.exists()) f.delete();
            chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocId, id));
            docMapper.deleteById(id);
        }
    }

    /**
     * 重新解析+向量化（用于 failed 状态的文档，配好 API Key 后重试）
     */
    public void retry(Long id) {
        KnowledgeDoc doc = docMapper.selectById(id);
        if (doc == null) throw new IllegalArgumentException("文档不存在: " + id);
        if (!"failed".equals(doc.getStatus()) && !"ready".equals(doc.getStatus())) {
            throw new IllegalStateException("当前状态不允许重试: " + doc.getStatus());
        }
        // 清理旧切片
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocId, id));
        doc.setStatus("parsing");
        doc.setErrorMsg(null);
        doc.setChunkCount(null);
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);
        submitWithTenant(() -> processDoc(doc));
    }
}
