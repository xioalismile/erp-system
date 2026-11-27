package com.erp.kai.controller;

import com.erp.common.Result;
import com.erp.kai.service.KaiKnowledgeService;
import com.erp.kai.service.KaiMcpService;
import com.erp.kai.service.KaiSkillService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 小楷独立版 · 能力扩展第一批（FEAT-KAI-STANDALONE-011）：技能库 / 知识库 / MCP 工具。
 * 统一挂在 /api/kai-standalone/**（独立鉴权拦截器自动生效，登录后才可访问）。
 */
@Slf4j
@RestController
@RequestMapping("/api/kai-standalone")
@RequiredArgsConstructor
public class KaiCapabilityController {

    private final KaiSkillService skillService;
    private final KaiKnowledgeService knowledgeService;
    private final KaiMcpService mcpService;

    // ---------------- 技能库 ----------------

    @GetMapping("/skills")
    public Result<?> skills(HttpServletRequest request) {
        return Result.success(skillService.list(kaiUserId(request)));
    }

    @PostMapping("/skills")
    public Result<?> saveSkill(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            return Result.success(Map.of("id", skillService.save(kaiUserId(request), body)));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/skills/{id}")
    public Result<?> deleteSkill(HttpServletRequest request, @PathVariable Long id) {
        skillService.delete(kaiUserId(request), id);
        return Result.success();
    }

    @PostMapping("/skills/{id}/toggle")
    public Result<?> toggleSkill(HttpServletRequest request, @PathVariable Long id, @RequestParam boolean enabled) {
        skillService.toggle(kaiUserId(request), id, enabled);
        return Result.success();
    }

    // ---------------- 知识库 ----------------

    @GetMapping("/kb/bases")
    public Result<?> kbBases(HttpServletRequest request) {
        return Result.success(knowledgeService.listBases(kaiUserId(request)));
    }

    @PostMapping("/kb/bases")
    public Result<?> saveKbBase(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            return Result.success(Map.of("id", knowledgeService.saveBase(kaiUserId(request), body)));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/kb/bases/{id}")
    public Result<?> deleteKbBase(HttpServletRequest request, @PathVariable Long id) {
        knowledgeService.deleteBase(kaiUserId(request), id);
        return Result.success();
    }

    @PostMapping("/kb/bases/{id}/toggle")
    public Result<?> toggleKbBase(HttpServletRequest request, @PathVariable Long id, @RequestParam boolean enabled) {
        knowledgeService.toggleBase(kaiUserId(request), id, enabled);
        return Result.success();
    }

    @GetMapping("/kb/bases/{id}/docs")
    public Result<?> kbDocs(HttpServletRequest request, @PathVariable Long id) {
        try {
            return Result.success(knowledgeService.listDocs(kaiUserId(request), id));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/kb/bases/{id}/docs")
    public Result<?> uploadKbDoc(HttpServletRequest request, @PathVariable Long id,
                                 @RequestParam("file") MultipartFile file) {
        try {
            return Result.success(knowledgeService.uploadDoc(kaiUserId(request), id, file));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("[小楷独立版] 知识库上传失败: {}", e.getMessage());
            return Result.error(400, "上传失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/kb/docs/{id}")
    public Result<?> deleteKbDoc(HttpServletRequest request, @PathVariable Long id) {
        knowledgeService.deleteDoc(kaiUserId(request), id);
        return Result.success();
    }

    @PostMapping("/kb/search")
    public Result<?> kbSearch(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            // 前端发 question / 后端口径 query，双向兼容；baseId 可空=全部启用中的库
            String query = body.get("query") != null ? body.get("query").toString() :
                    (body.get("question") == null ? "" : body.get("question").toString());
            Integer topK = body.get("topK") == null ? null : Integer.valueOf(body.get("topK").toString());
            Long baseId = body.get("baseId") == null || body.get("baseId").toString().isBlank()
                    ? null : Long.valueOf(body.get("baseId").toString());
            return Result.success(knowledgeService.search(kaiUserId(request), query, topK, baseId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    // ---------------- MCP 工具 ----------------

    @GetMapping("/mcp/servers")
    public Result<?> mcpServers(HttpServletRequest request) {
        return Result.success(mcpService.list(kaiUserId(request)));
    }

    @PostMapping("/mcp/servers")
    public Result<?> saveMcpServer(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            return Result.success(Map.of("id", mcpService.save(kaiUserId(request), body)));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/mcp/servers/{id}")
    public Result<?> deleteMcpServer(HttpServletRequest request, @PathVariable Long id) {
        mcpService.delete(kaiUserId(request), id);
        return Result.success();
    }

    @PostMapping("/mcp/servers/{id}/toggle")
    public Result<?> toggleMcpServer(HttpServletRequest request, @PathVariable Long id, @RequestParam boolean enabled) {
        mcpService.toggle(kaiUserId(request), id, enabled);
        return Result.success();
    }

    /** 草稿测试连接：真实握手并列出工具。 */
    @PostMapping("/mcp/servers/test")
    public Result<?> testMcpServer(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            return Result.success(mcpService.test(kaiUserId(request), body));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "连接失败：" + e.getMessage());
        }
    }

    /** 已存服务器：刷新工具清单缓存（真实握手）。 */
    @PostMapping("/mcp/servers/{id}/tools")
    public Result<?> refreshMcpTools(HttpServletRequest request, @PathVariable Long id) {
        try {
            return Result.success(mcpService.refreshTools(kaiUserId(request), id));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "连接失败：" + e.getMessage());
        }
    }

    private Long kaiUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("kaiUserId");
    }
}
