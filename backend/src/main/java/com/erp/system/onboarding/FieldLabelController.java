package com.erp.system.onboarding;

import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段显示名自定义（2026-10-11 开账引导配套）。
 *
 * <p>全站字段名统一走前端一个翻译出口；本表按"原字段名"存覆盖显示名（每租户一份），
 * 只影响显示文字，不影响校验、落库、导入导出、打印取数。</p>
 *
 * <p>权限：map 下发所有登录人（人人都要按本租户叫法显示）；
 * list / save / reset 仅管理员。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/system/field-label")
@RequiredArgsConstructor
public class FieldLabelController {

    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    /** 全量覆盖映射：{原字段名: 显示名}（前端翻译出口最高优先级消费） */
    @GetMapping("/map")
    public Result<Map<String, String>> map() {
        IndustryInitService.ensureFieldLabelTableOn(jdbcTemplate);
        Map<String, String> out = new LinkedHashMap<>();
        try {
            for (Map<String, Object> row : jdbcTemplate.queryForList(
                    "SELECT field_key, display_name FROM sys_field_label ORDER BY field_key")) {
                Object k = row.get("field_key");
                Object v = row.get("display_name");
                if (k != null && v != null) out.put(String.valueOf(k), String.valueOf(v));
            }
        } catch (Exception e) {
            log.warn("[字段显示名] 读取覆盖映射失败（按无覆盖处理）: {}", e.getMessage());
        }
        return Result.success(out);
    }

    /** 覆盖清单（管理页；2026-10-13 起可配置：管理员 或 勾选「字段名称设置:查看」） */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(HttpServletRequest request) {
        String deny = denyIfNoPerm(request, "system:field-name:view", "查看字段名称设置");
        if (deny != null) return Result.error(deny);
        IndustryInitService.ensureFieldLabelTableOn(jdbcTemplate);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT field_key, display_name, updated_at, updated_by FROM sys_field_label ORDER BY field_key");
        return Result.success(rows);
    }

    /**
     * 批量保存：displayName 为空 = 恢复默认（删该行）；否则 upsert。
     * body: { items: [ { fieldKey, displayName } ] }
     */
    @PostMapping("/save")
    public Result<Map<String, Object>> save(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String deny = denyIfNoPerm(request, "system:field-name:save", "保存字段名称设置");
        if (deny != null) return Result.error(deny);
        IndustryInitService.ensureFieldLabelTableOn(jdbcTemplate);
        Object itemsObj = body == null ? null : body.get("items");
        if (!(itemsObj instanceof List<?> items) || items.isEmpty()) {
            return Result.error("没有要保存的内容");
        }
        String user = SecurityUtils.getCurrentUsername(request);
        int saved = 0, reset = 0;
        List<String> errors = new ArrayList<>();
        for (Object o : items) {
            if (!(o instanceof Map<?, ?> item)) continue;
            String fieldKey = item.get("fieldKey") == null ? "" : String.valueOf(item.get("fieldKey")).trim();
            String displayName = item.get("displayName") == null ? "" : String.valueOf(item.get("displayName")).trim();
            if (fieldKey.isEmpty()) continue;
            try {
                if (displayName.isEmpty() || displayName.equals(fieldKey)) {
                    jdbcTemplate.update("DELETE FROM sys_field_label WHERE field_key = ?", fieldKey);
                    reset++;
                } else {
                    if (displayName.length() > 20) {
                        errors.add("「" + fieldKey + "」显示名过长（最多20字）");
                        continue;
                    }
                    int c = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM sys_field_label WHERE field_key = ?", Integer.class, fieldKey);
                    if (c > 0) {
                        jdbcTemplate.update(
                                "UPDATE sys_field_label SET display_name = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ? " +
                                        "WHERE field_key = ?", displayName, user, fieldKey);
                    } else {
                        jdbcTemplate.update(
                                "INSERT INTO sys_field_label (field_key, display_name, updated_by) VALUES (?, ?, ?)",
                                fieldKey, displayName, user);
                    }
                    saved++;
                }
            } catch (Exception e) {
                errors.add("「" + fieldKey + "」保存失败: " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", saved);
        result.put("reset", reset);
        result.put("errors", errors);
        if (!errors.isEmpty()) {
            return Result.error(String.join("；", errors));
        }
        log.info("[字段显示名] 保存完成: 新改{}项, 恢复默认{}项, 操作人={}", saved, reset, user);
        return Result.success(result);
    }

    /** 全部恢复默认（清空覆盖） */
    @PostMapping("/reset")
    public Result<Void> reset(HttpServletRequest request) {
        String deny = denyIfNoPerm(request, "system:field-name:resetDefault", "恢复字段名称默认");
        if (deny != null) return Result.error(deny);
        IndustryInitService.ensureFieldLabelTableOn(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM sys_field_label");
        log.info("[字段显示名] 已全部恢复默认，操作人={}", SecurityUtils.getCurrentUsername(request));
        return Result.success();
    }

    /**
     * 统一口径（2026-10-13 与按钮权限体系对齐）：管理员角色/通配权限，或显式勾选对应按钮码。
     * 用户口径「核心是可以所有人都支持配置，关键是我有没有给权限」——勾了就能操作。
     */
    private static String denyIfNoPerm(HttpServletRequest request, String buttonCode, String actionLabel) {
        if (SecurityUtils.isAdmin(request)) return null;
        if (SecurityUtils.hasPermission(request, buttonCode)) return null;
        return "无权限" + actionLabel + "：请在【设置中心 → 角色权限设置 → 按钮权限】为该角色勾选「字段名称设置」的「"
                + actionLabel + "」权限";
    }
}
