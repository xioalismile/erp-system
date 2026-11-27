package com.erp.inventory.batch.controller;

import com.erp.common.Result;
import com.erp.inventory.batch.service.BatchReleaseGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批次放行守卫 Controller（化妆品批记录）。
 * 路由: /api/inventory/batch-release
 *
 * <p>前端在销售出库「提交」前调用 /check 拿到预警清单，按三档参数决定是否需要二次确认；
 * 真正的阻断在后端出库校验链路（select 允许/预警提示/不允许）。</p>
 */
@RestController
@RequestMapping("/api/inventory/batch-release")
@RequiredArgsConstructor
public class BatchReleaseGuardController {

    private final BatchReleaseGuardService guardService;

    /** 当前控制档位（设置中心参数） */
    @GetMapping("/mode")
    public Result<String> mode() {
        return Result.success(guardService.currentMode());
    }

    /**
     * 出库前预检：body = { items: [ { materialId, batchNo, lineLabel } ] }
     * 返回 { mode, warnings: [] }；warnings 非空时前端按档位提示或拦截。
     */
    @PostMapping("/check")
    public Result<Map<String, Object>> check(@RequestBody Map<String, Object> body) {
        String mode = guardService.currentMode();
        List<String> warnings = new ArrayList<>();
        Object raw = body == null ? null : body.get("items");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Long materialId = toLong(m.get("materialId"));
                String batchNo = m.get("batchNo") == null ? null : m.get("batchNo").toString();
                String lineLabel = m.get("lineLabel") == null ? null : m.get("lineLabel").toString();
                String w = guardService.warningOf(materialId, batchNo, lineLabel);
                if (w != null) warnings.add(w);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", mode);
        out.put("warnings", warnings);
        return Result.success(out);
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        try {
            return Long.valueOf(o.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
