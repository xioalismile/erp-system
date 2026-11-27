package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.common.scope.DataScopeResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板类 AI 工具公共能力（2026-11-07）：数据权限、菜单校验、公式解析、参数规整。
 * 口径与看板页面一致：小楷能看到的 = 当前登录人在看板能看到的。
 */
@Component
@RequiredArgsConstructor
public class BoardAiSupport {

    private static final List<String> BOARD_PAGES = List.of(
            "/databoard/finance", "/databoard/sale", "/databoard/inventory", "/databoard/operation");
    private static final Map<String, String> BOARD_NAMES = Map.of(
            "finance", "财务看板", "sale", "销售看板",
            "inventory", "仓储看板", "operation", "经营看板");

    private final DataScopeResolver dataScopeResolver;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataScopeResolver.DataScopeContext scope(AiDataScope s) {
        if (s == null) return null;
        return dataScopeResolver.resolveForUser(s.getUsername(), s.getUserId(), s.getEmployeeId(),
                s.getDeptId(), s.getRoles());
    }

    public boolean isAdmin(AiDataScope s) {
        return s != null && s.isAdmin();
    }

    /** 任一 / 无看板菜单权限时给出中文拒绝原因；管理员直接放行 */
    public String denyBoard(AiDataScope s) {
        if (isAdmin(s)) return null;
        for (String p : BOARD_PAGES) {
            if (s != null && s.hasMenuPath(p)) return null;
        }
        return "当前账号没有看板菜单权限，无法查询或设置看板";
    }

    /** 指定看板页的菜单权限 */
    public String denyPage(AiDataScope s, String pageKey) {
        if (isAdmin(s)) return null;
        String path = "/databoard/" + pageKey;
        if (!BOARD_PAGES.contains(path)) return "不支持的看板：" + pageKey;
        if (s != null && s.hasMenuPath(path)) return null;
        return "当前账号没有" + boardName(pageKey) + "的菜单权限";
    }

    public boolean validPage(String pageKey) {
        return pageKey != null && BOARD_PAGES.contains("/databoard/" + pageKey);
    }

    public String boardName(String pageKey) {
        return BOARD_NAMES.getOrDefault(pageKey, pageKey);
    }

    /** 把 "毛利 - 销售费用 - 采购费用 - 税金" 拆成 token；变量名不应含运算符与括号 */
    public List<String> formulaTokens(String formula) {
        List<String> out = new ArrayList<>();
        if (formula == null) return out;
        String s = formula.trim();
        if (s.isEmpty()) return out;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if ("+-*/×÷−()".indexOf(c) >= 0) {
                out.add(String.valueOf(c));
                i++;
                continue;
            }
            int j = i;
            while (j < s.length()) {
                char d = s.charAt(j);
                if (Character.isWhitespace(d) || "+-*/×÷−()".indexOf(d) >= 0) break;
                j++;
            }
            out.add(s.substring(i, j));
            i = j;
        }
        return out;
    }

    /** 取数项参数：兼容 JSON 数组 / JSON 字符串 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> itemsOf(Object raw) {
        Object v = raw;
        if (raw instanceof String str) {
            if (str.isBlank()) return new ArrayList<>();
            try {
                v = mapper.readValue(str, List.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("取数项格式不正确，应为数组");
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) item.put(String.valueOf(e.getKey()), e.getValue());
                out.add(item);
            }
        }
        return out;
    }

    public Map<String, Object> fail(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", false);
        out.put("message", message);
        return out;
    }
}
