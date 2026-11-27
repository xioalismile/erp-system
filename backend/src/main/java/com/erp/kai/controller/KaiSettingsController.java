package com.erp.kai.controller;

import com.erp.common.Result;
import com.erp.kai.service.KaiAutoTaskService;
import com.erp.kai.service.KaiCreditService;
import com.erp.kai.service.KaiModelService;
import com.erp.kai.service.KaiPluginService;
import com.erp.kai.service.KaiUsageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 小楷独立版 · 设置中心（FEAT-KAI-STANDALONE-003/004/005/006）。
 * 统一前缀 /api/kai-standalone/settings/**：
 * 模型服务商（用户级） / 用量与配额 / 自动化任务 / 插件。
 */
@Slf4j
@RestController
@RequestMapping("/api/kai-standalone/settings")
@RequiredArgsConstructor
public class KaiSettingsController {

    private final KaiModelService modelService;
    private final KaiUsageService usageService;
    private final KaiCreditService creditService;
    private final KaiAutoTaskService autoTaskService;
    private final KaiPluginService pluginService;

    // ---------------- 模型服务商 ----------------

    /** 内置模型启用清单（2026-10-20）：对话页模型下拉用，仅登录客户可读，不含密钥。 */
    @GetMapping("/builtin-models")
    public Result<?> builtinModels(HttpServletRequest request) {
        return Result.success(modelService.listEnabledBuiltin());
    }

    @GetMapping("/model-providers")
    public Result<?> modelProviders(HttpServletRequest request) {
        return Result.success(modelService.list(kaiUserId(request)));
    }

    @PostMapping("/model-providers")
    public Result<?> saveModelProvider(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            return Result.success(Map.of("id", modelService.save(kaiUserId(request), body)));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/model-providers/{id}")
    public Result<?> deleteModelProvider(HttpServletRequest request, @PathVariable Long id) {
        modelService.delete(kaiUserId(request), id);
        return Result.success();
    }

    @PostMapping("/model-providers/{id}/toggle")
    public Result<?> toggleModelProvider(HttpServletRequest request, @PathVariable Long id,
                                         @RequestParam boolean enabled) {
        modelService.toggle(kaiUserId(request), id, enabled);
        return Result.success();
    }

    /** 测试模型服务商连接：草稿（未保存）或已存配置均可；已存配置密钥留空 = 用原密钥测试。 */
    @PostMapping("/model-providers/test")
    public Result<?> testModelProvider(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            return Result.success(modelService.test(kaiUserId(request), body));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "测试失败：" + e.getMessage());
        }
    }

    // ---------------- 用量与配额 ----------------

    @GetMapping("/usage")
    public Result<?> usage(HttpServletRequest request) {
        Long uid = kaiUserId(request);
        Map<String, Object> out = usageService.overview(uid);
        // 积分钱包（余额/累计/流水）+ 月 token 统计一起返回
        out.putAll(creditService.overview(uid));
        return Result.success(out);
    }

    @PostMapping("/quota")
    public Result<?> setQuota(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long quota = body.get("quota") == null ? 0 : Long.parseLong(body.get("quota").toString());
        usageService.setQuota(kaiUserId(request), quota);
        return Result.success();
    }

    // ---------------- 自动化任务 ----------------

    @GetMapping("/auto-tasks")
    public Result<?> autoTasks(HttpServletRequest request) {
        return Result.success(autoTaskService.list(kaiUserId(request)));
    }

    @PostMapping("/auto-tasks")
    public Result<?> saveAutoTask(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            return Result.success(Map.of("id", autoTaskService.save(kaiUserId(request), body)));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "任务保存失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/auto-tasks/{id}")
    public Result<?> deleteAutoTask(HttpServletRequest request, @PathVariable Long id) {
        autoTaskService.delete(kaiUserId(request), id);
        return Result.success();
    }

    @PostMapping("/auto-tasks/{id}/toggle")
    public Result<?> toggleAutoTask(HttpServletRequest request, @PathVariable Long id,
                                    @RequestParam boolean enabled) {
        try {
            autoTaskService.toggle(kaiUserId(request), id, enabled);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, "操作失败：" + e.getMessage());
        }
    }

    @GetMapping("/auto-tasks/{id}/logs")
    public Result<?> autoTaskLogs(HttpServletRequest request, @PathVariable Long id) {
        try {
            return Result.success(autoTaskService.logs(kaiUserId(request), id));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    // ---------------- 插件 ----------------

    @GetMapping("/plugins")
    public Result<?> plugins(HttpServletRequest request) {
        return Result.success(pluginService.list(kaiUserId(request)));
    }

    @PostMapping("/plugins")
    public Result<?> savePlugin(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            return Result.success(Map.of("id", pluginService.save(kaiUserId(request), body)));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/plugins/{id}")
    public Result<?> deletePlugin(HttpServletRequest request, @PathVariable Long id) {
        pluginService.delete(kaiUserId(request), id);
        return Result.success();
    }

    @PostMapping("/plugins/{id}/toggle")
    public Result<?> togglePlugin(HttpServletRequest request, @PathVariable Long id,
                                  @RequestParam boolean enabled) {
        pluginService.toggle(kaiUserId(request), id, enabled);
        return Result.success();
    }

    /** 拉取该插件的工具清单（启用前预览能力）。 */
    @PostMapping("/plugins/{id}/tools")
    public Result<?> pluginTools(HttpServletRequest request, @PathVariable Long id) {
        try {
            Map<String, Object> p = pluginService.list(kaiUserId(request)).stream()
                    .filter(m -> id.equals(((Number) m.get("id")).longValue()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("插件不存在"));
            return Result.success(pluginService.enabledTools(kaiUserId(request)).stream()
                    .filter(t -> id.equals(((Number) t.get("pluginId")).longValue()))
                    .toList());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    private Long kaiUserId(HttpServletRequest request) {
        Object v = request.getAttribute("kaiUserId");
        if (v == null) {
            throw new IllegalArgumentException("请先登录小楷");
        }
        return (Long) v;
    }
}
