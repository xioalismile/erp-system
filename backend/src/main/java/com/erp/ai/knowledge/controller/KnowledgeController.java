// @feature FEAT-AIKB-001
package com.erp.ai.knowledge.controller;

import com.erp.ai.knowledge.entity.KnowledgeDoc;
import com.erp.ai.knowledge.service.KnowledgeService;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping("/list")
    public Result<List<KnowledgeDoc>> list(@RequestParam(required = false) String module) {
        return Result.success(knowledgeService.list(module));
    }

    @PostMapping("/upload")
    public Result<KnowledgeDoc> upload(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String module) {
        try {
            String username = SecurityUtils.getCurrentUsername(request);
            KnowledgeDoc doc = knowledgeService.upload(file, title, module, username);
            return Result.success(doc);
        } catch (Exception e) {
            log.error("知识库文档上传失败", e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/retry")
    public Result<Void> retry(@PathVariable Long id) {
        try {
            knowledgeService.retry(id);
            return Result.success();
        } catch (Exception e) {
            log.error("知识库文档重试失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }
}
