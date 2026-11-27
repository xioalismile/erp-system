package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：界面导航（带路）。
 *
 * <p>用途：用户说「打开设置中心」「打开销售订单」「打开 SO20260918001」时，AI 通过本工具把
 * 「要打开哪个页面（+ 哪张单据）」交给前端，由前端用 {@code menuConfig.js} 解析路由并做菜单权限校验后跳转。
 *
 * <p><b>设计约束（勿改）</b>：后端**不维护"页面名 → 路由"映射**——页面清单的单一事实来源是前端
 * {@code frontend/src/config/menuConfig.js}。若在后端也写一份，菜单改名/新增页面时必然出现两套口径。
 * 因此本工具只做「参数校验 + 契约化输出」，查库、跳转、权限判定都在前端 + 路由守卫完成。
 *
 * <p>只读：不写库、不改配置、不消耗写开关（同 {@code image_search} / {@code bill_action_list} 口径），
 * 不走写操作确认卡片。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenPageTool implements AiTool {

    @Override
    public String getName() {
        return "open_page";
    }

    @Override
    public String getDescription() {
        return "【只读】打开系统里的某个页面（带路），不修改任何数据。"
                + "当用户说「打开/进入/跳到/切换到 XX 页面」时调用，例如：打开设置中心、打开自定义字段设置、"
                + "打开销售订单、打开即时库存、打开审批设置、打开库存预警设置。"
                + "page 必须填**具体页面**的菜单名；菜单分组名（基础资料、销售管理、系统管理）不是页面，"
                + "用户这么说时不要调用本工具，先问他要打开里面哪一项。"
                + "当用户说「打开某张单据」时，除 page（单据列表页名，如：销售订单）外，还要带上 recordId 与 entityType："
                + "recordId 必须是**单据主键的数字 ID**（如销售订单的 orderId=258）——**严禁传单据编号/单号**"
                + "（单号不是主键，用它打开详情会白屏）；用户只给了单号时，先用 query_ 查询工具按单号查到主键，再调用本工具。"
                + "entityType 是实体英文名（如 SaleOrder），取值与查询工具返回结果里的 entityName 一致。"
                + "严禁传网址、路由路径或自己编造的页面名。"
                + "本工具只负责带路、不代替用户操作：用户要改数据或走流程时，请改用对应的查询/写操作工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("page", Map.of("type", "string",
                "description", "要打开的具体页面名称（系统菜单名），如：设置中心、自定义字段设置、销售订单、即时库存；"
                        + "菜单分组名（基础资料/销售管理/系统管理）不是页面，不要传"));
        props.put("recordId", Map.of("type", "string",
                "description", "可选。要打开的具体单据的**主键数字 ID**（如 orderId=258）；"
                        + "严禁传单号/单据编号（如 SO202609140005），单号不是主键、用它打开会白屏；"
                        + "只有单号时先用查询工具按单号查到主键再传"));
        props.put("entityType", Map.of("type", "string",
                "description", "可选。单据所属实体英文名（如 SaleOrder、PurchaseOrder），与查询结果里的 entityName 一致；"
                        + "给了 recordId 时必须同时给出"));
        props.put("filters", Map.of("type", "object",
                "description", "可选。打开页面时顺带套用的筛选条件（键值对）。"
                        + "可用键：status（状态，如 草稿/待审核/已生效）、startDate/endDate（单据日期区间，格式 2026-07-01）、"
                        + "customerId（客户ID）、keyword（关键词）。"
                        + "用户说「打开销售看板」「把未审核的订单列表打开」「看本月的销售订单」这类带条件的页面时传本参数"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("page"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            String page = SettingSupport.str(params == null ? null : params.get("page"));
            if (page == null) {
                return fail("请告诉我要打开哪个页面，例如「设置中心」「销售订单」「自定义字段设置」。");
            }
            // 安全：拒绝网址与路径 —— 防止小助手被当成"任意跳转的开放代理"（沿用既有安全口径）
            String lower = page.toLowerCase();
            if (lower.startsWith("http://") || lower.startsWith("https://")
                    || page.startsWith("/") || page.contains("://")) {
                return fail("只支持打开系统内的页面名称，不接受网址或路径。请改用页面名，例如「设置中心」。");
            }
            String recordId = SettingSupport.str(params.get("recordId"));
            String entityType = SettingSupport.str(params.get("entityType"));

            // recordId 必须是主键数字 ID：单号（如 SO202609140005）不是主键，
            // 用它打开详情页会查不到数据、整页空白（2026-09-18 用户实测踩到）
            if (recordId != null && !recordId.matches("\\d+")) {
                return fail("recordId 必须是单据主键的**数字 ID**，收到的「" + recordId + "」不是数字主键（可能是单号）。"
                        + "请先用 query_ 查询工具按单号查到主键（如销售订单的 orderId），再带 recordId + entityType 重新调用。");
            }

            Map<String, Object> navigate = new LinkedHashMap<>();
            navigate.put("page", page);
            // 值为空时不写该键：Map.of 不允许 null，统一用 LinkedHashMap 组装
            if (recordId != null) {
                navigate.put("recordId", recordId);
            }
            if (entityType != null) {
                navigate.put("entityType", entityType);
            }
            // 打开页面时顺带套用的筛选条件（前端会按各列表页支持的键做白名单过滤后再拼到地址栏）
            Object filters = params == null ? null : params.get("filters");
            if (filters instanceof Map<?, ?> f && !f.isEmpty()) {
                Map<String, Object> query = new LinkedHashMap<>();
                f.forEach((k, v) -> {
                    if (k != null && v != null && !String.valueOf(v).isBlank()) {
                        query.put(String.valueOf(k), v);
                    }
                });
                if (!query.isEmpty()) {
                    navigate.put("query", query);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已识别到你要打开【" + page + "】，正在为你打开");
            result.put("navigate", navigate);
            log.info("[AI界面导航] 用户={} 目标页面={} recordId={} entityType={}",
                    scope != null ? scope.getUsername() : "-", page, recordId, entityType);
            return result;
        } catch (Exception e) {
            log.error("[AI界面导航] 执行异常", e);
            return fail("打开页面失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private Map<String, Object> fail(String message) {
        return Map.of("success", false, "message", message);
    }
}
