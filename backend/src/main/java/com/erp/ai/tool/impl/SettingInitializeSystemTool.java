package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.system.onboarding.IndustryCatalog;
import com.erp.system.onboarding.IndustryInitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷工具：按行业初始化/预设系统（2026-10-11 开账引导）。
 *
 * <p>用户对小楷说"帮我按食品饮料行业预设，12个人"时，小楷把自然语言映射成
 * industryCode + employeeCount 调用本工具；previewOnly=true 只回预览（确认卡数据），
 * false/缺省执行初始化（幂等，重复说只补缺不删改）。</p>
 *
 * <p>权限与开关：独立开关 ai.tool.initialize.enabled（默认 true —— 初始化只加数据不删改、
 * 走强制确认卡、且仅管理员可执行，风险可控）；仅 ADMIN/MANAGER/SUPER_ADMIN 角色可用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingInitializeSystemTool implements AiTool {

    public static final String NAME = "setting_initialize_system";

    private final IndustryInitService industryInitService;
    private final IndustryCatalog industryCatalog;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "【按行业初始化系统】新客户开账引导：按行业一次性生成 部门/计量单位/业务角色(带菜单权限)/仓库/" +
                "演示基础档案(物料·客户·供应商)/系统字段叫法。幂等：重复执行只补缺，绝不删改已有数据。" +
                "参数：industryCode 必填，取值必须是以下之一（把用户口语映射过去）：" + codeList() +
                "；employeeCount 可选（人数，默认5，决定演示资料条数：≤5人各5条/6~20人各10条/>20人各20条）；" +
                "previewOnly 可选。重要：用户开口要初始化/预设时**直接执行**（previewOnly 不传或 false）——" +
                "系统会自动弹出确认卡片，用户在卡片上确认后才会真正写库，你不需要也不会先做一次 preview；" +
                "仅当用户明确说\"先给我看看计划/先别动手\"时才传 previewOnly=true。" +
                "适用场景：\"帮我把系统设置成做XX行业的\"、\"按XX行业初始化\"、\"新公司开账预设\"。";
    }

    private String codeList() {
        StringBuilder sb = new StringBuilder();
        for (IndustryCatalog.Industry ind : industryCatalog.all()) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(ind.code).append("=").append(ind.name);
        }
        return sb.toString();
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        StringBuilder enumStr = new StringBuilder();
        for (IndustryCatalog.Industry ind : industryCatalog.all()) {
            if (enumStr.length() > 0) enumStr.append(",");
            enumStr.append(ind.code);
        }
        props.put("industryCode", Map.of(
                "type", "string",
                "description", "行业编码，必填。枚举：" + enumStr + "（把用户的行业口语映射到最接近的一项，实在映射不到用 GENERAL）"));
        props.put("employeeCount", Map.of(
                "type", "integer",
                "description", "可选，公司大概人数（1~9999），默认5；决定演示资料条数分档"));
        props.put("previewOnly", Map.of(
                "type", "boolean",
                "description", "可选，true=只返回生成计划预览不写库；缺省=false 直接执行"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("industryCode"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Map<String, Object> p = params == null ? Map.of() : params;
            String industryCode = p.get("industryCode") == null ? "GENERAL" : String.valueOf(p.get("industryCode")).trim();
            Integer employeeCount = toInt(p.get("employeeCount"));
            boolean previewOnly = Boolean.parseBoolean(String.valueOf(p.get("previewOnly")));

            IndustryCatalog.Industry ind = industryCatalog.ofOrDefault(industryCode);

            if (previewOnly) {
                return previewResult(ind, employeeCount);
            }

            Map<String, Object> result = industryInitService.initialize(ind.code, employeeCount, null);
            Map<?, ?> renames = result.get("fieldRenames") instanceof Map<?, ?> m ? m : Map.of();
            String renameText = renames.isEmpty()
                    ? "字段叫法无调整"
                    : "字段叫法已调整为 " + joinPairs(renames);
            result.put("success", true);
            result.put("message", "按「" + ind.name + "」行业初始化完成。"
                    + "部门 " + result.get("departments") + " 个、角色 " + result.get("roles") + " 个、"
                    + "计量单位 " + result.get("units") + " 个、仓库 " + result.get("warehouses") + " 个、"
                    + "演示物料 " + result.get("materials") + " / 客户 " + result.get("customers")
                    + " / 供应商 " + result.get("suppliers") + " 条；" + renameText
                    + "。重复执行只会补缺，不会动您已建的数据。");
            return result;
        } catch (Exception e) {
            log.error("[AI工具] 行业初始化失败", e);
            return Map.of("success", false,
                    "message", "初始化失败（已整体回滚，可重试）: " + e.getMessage());
        }
    }

    private Map<String, Object> previewResult(IndustryCatalog.Industry ind, Integer employeeCount) {
        Map<String, Object> plan = industryInitService.preview(ind.code, employeeCount);
        plan.put("success", true);
        plan.put("message", "已生成「" + ind.name + "」行业的预设计划（未写库）。请把以下内容逐项念给用户确认："
                + "部门 " + join(plan.get("departments"))
                + "；角色 " + join(plan.get("roles"))
                + "；计量单位 " + join(plan.get("units"))
                + "；仓库 " + join(plan.get("warehouses"))
                + "；演示资料按 " + plan.get("demoPerTable") + " 条/表 生成（物料/客户/供应商）。"
                + "确认后请再调用本工具（previewOnly=false）执行。");
        return plan;
    }

    private static String join(Object arr) {
        if (arr instanceof Object[] a) {
            StringBuilder sb = new StringBuilder();
            for (Object o : a) {
                if (sb.length() > 0) sb.append("、");
                sb.append(o);
            }
            return sb.toString();
        }
        return String.valueOf(arr);
    }

    private static String joinPairs(Map<?, ?> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (sb.length() > 0) sb.append("、");
            sb.append(e.getKey()).append("→").append(e.getValue());
        }
        return sb.toString();
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
