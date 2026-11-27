package com.erp.permission.service;

import com.erp.customfield.entity.CustomField;
import com.erp.customfield.service.CustomFieldService;
import com.erp.permission.config.BillPermissionPathRegistry;
import com.erp.permission.dto.FieldMetaDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字段权限的「非 JSON 出口」统一过滤器（2026-11-27 系统性改造）。
 *
 * <p>为什么需要它：字段权限原先只在 JSON 响应出口过滤，导出/打印各写各的，必然漏。
 * 现在把"按页面键剔列/剔字段"收敛成一份实现，所有非 JSON 出口都调它：</p>
 * <ul>
 *   <li>Excel 导出：所有数据导出最终经过 {@code writePositionalSheet}，在那里统一剔列；</li>
 *   <li>单据明细报表导出：报表控制器导出前统一剔 key；</li>
 *   <li>打印取数：打印数据 Map 统一剔字段。</li>
 * </ul>
 *
 * <p>页面键来源不靠路径表：实体类→@TableName（{@link BillPermissionPathRegistry#fromEntityClass}）、
 * 单据类型→主表（{@link BillPermissionPathRegistry#fromBillType}）、报表→配置主表。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FieldPermissionOutputFilter {

    /** 导出请求的页面键放在请求属性里，由导出控制器统一标记，写盘时读取 */
    public static final String EXPORT_PAGE_KEY_ATTR = "ERP_FIELD_PERMISSION_EXPORT_PAGE_KEY";

    private final FieldVisiblePermissionService visibleService;
    private final com.erp.permission.service.FieldMetaService fieldMetaService;
    private final CustomFieldService customFieldService;
    private final BillPermissionPathRegistry pathRegistry;

    /** 导出控制器统一调用：按实体类自动标记本次导出的页面键（无匹配则不标记） */
    public void markExportPageKey(Class<?> entityClass) {
        String pageKey = pathRegistry.fromEntityClass(entityClass);
        HttpServletRequest request = currentRequest();
        if (request != null && pageKey != null) {
            request.setAttribute(EXPORT_PAGE_KEY_ATTR, pageKey);
        }
    }

    /** 本次导出请求的页面键（未标记返回 null） */
    public String currentExportPageKey() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        Object v = request.getAttribute(EXPORT_PAGE_KEY_ATTR);
        return v == null ? null : String.valueOf(v);
    }

    /** 该页面不可见的字段 key（camelCase + snake_case） */
    public Set<String> hiddenKeys(String pageKey) {
        return visibleService.currentUserHiddenKeys(pageKey);
    }

    /** 指定用户在该页面不可见的字段 key（不依赖请求线程：AI 工具/异步链路用） */
    public Set<String> hiddenKeysForUser(Long userId, boolean admin, String pageKey) {
        return visibleService.hiddenKeysFor(userId, admin, pageKey);
    }

    /** 两组不可见 key 合并（多处来源的表取并集时用） */
    public static Set<String> union(Set<String> a, Set<String> b) {
        if (a == null || a.isEmpty()) return b == null ? Collections.emptySet() : b;
        if (b == null || b.isEmpty()) return a;
        Set<String> out = new HashSet<>(a);
        out.addAll(b);
        return out;
    }

    /** 不可见字段的「中文列名」集合（静态字段 + 自定义字段），供按列名剔列的导出/打印使用 */
    public Set<String> hiddenLabels(String pageKey) {
        Set<String> hiddenKeys = hiddenKeys(pageKey);
        if (hiddenKeys.isEmpty()) return Collections.emptySet();
        Set<String> labels = new HashSet<>();
        try {
            for (FieldMetaDTO m : fieldMetaService.getFieldsByPage(pageKey)) {
                if (m.getFieldKey() != null && hiddenKeys.contains(m.getFieldKey())) {
                    labels.add(m.getFieldName() != null ? m.getFieldName() : m.getFieldKey());
                }
            }
        } catch (Exception e) {
            log.warn("[字段权限] 读取页面字段标签失败（按字段 key 继续）: pageKey={}, err={}", pageKey, e.getMessage());
        }
        try {
            for (CustomField cf : customFieldService.listEnabledByEntity(pageKey)) {
                if (cf.getFieldKey() != null && hiddenKeys.contains(cf.getFieldKey())) {
                    labels.add(cf.getFieldLabel() != null ? cf.getFieldLabel() : cf.getFieldKey());
                }
            }
        } catch (Exception e) {
            log.debug("[字段权限] 读取自定义字段标签失败: pageKey={}, err={}", pageKey, e.getMessage());
        }
        return labels;
    }

    /** 导出列过滤结果 */
    public record Layout(List<String> labels, List<List<Object>> rows) {}

    /**
     * 按不可见列名剔除导出列（labels 与每行 cells 同步删列）。
     * 页面键为空 / 不可见集合为空 → 原样返回。
     */
    public Layout filterColumns(String pageKey, List<String> labels, List<List<Object>> rows) {
        if (pageKey == null) return new Layout(labels, rows);
        Set<String> hiddenLabels = hiddenLabels(pageKey);
        if (hiddenLabels.isEmpty()) return new Layout(labels, rows);
        boolean[] keep = new boolean[labels.size()];
        boolean anyRemoved = false;
        for (int i = 0; i < labels.size(); i++) {
            String raw = labels.get(i);
            String plain = raw == null ? "" : raw.replaceAll("\\*$", "").trim();
            keep[i] = !hiddenLabels.contains(raw) && !hiddenLabels.contains(plain);
            if (!keep[i]) anyRemoved = true;
        }
        if (!anyRemoved) return new Layout(labels, rows);

        List<String> newLabels = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) if (keep[i]) newLabels.add(labels.get(i));
        List<List<Object>> newRows = new ArrayList<>(rows.size());
        for (List<Object> row : rows) {
            List<Object> cells = new ArrayList<>(newLabels.size());
            for (int i = 0; i < row.size() && i < keep.length; i++) {
                if (keep[i]) cells.add(row.get(i));
            }
            newRows.add(cells);
        }
        return new Layout(newLabels, newRows);
    }

    /** 剔除一条数据 Map 里的不可见字段（导出/打印通用；保留其它键） */
    public Map<String, Object> filterRecord(String pageKey, Map<String, Object> record) {
        if (pageKey == null || record == null || record.isEmpty()) return record;
        Set<String> hiddenKeys = hiddenKeys(pageKey);
        if (hiddenKeys.isEmpty()) return record;
        record.keySet().removeIf(k -> hiddenKeys.contains(k));
        return record;
    }

    /** 剔除一批数据 Map 里的不可见字段 */
    public List<Map<String, Object>> filterRecords(String pageKey, List<Map<String, Object>> records) {
        if (records == null) return null;
        for (Map<String, Object> r : records) filterRecord(pageKey, r);
        return records;
    }

    /** 按已解析好的不可见集合剔除一条数据 Map（AI 工具出口用，避免重复查库） */
    public Map<String, Object> filterRecord(Set<String> hiddenKeys, Map<String, Object> record) {
        if (hiddenKeys == null || hiddenKeys.isEmpty() || record == null || record.isEmpty()) return record;
        record.keySet().removeIf(hiddenKeys::contains);
        return record;
    }

    /** 按已解析好的不可见集合剔除一批数据 Map */
    public List<Map<String, Object>> filterRecords(Set<String> hiddenKeys, List<Map<String, Object>> records) {
        if (records == null) return null;
        if (hiddenKeys == null || hiddenKeys.isEmpty()) return records;
        for (Map<String, Object> r : records) filterRecord(hiddenKeys, r);
        return records;
    }

    /** 剔除字段中文名映射里的不可见字段（AI 返回的 labels 会告诉模型有哪些列） */
    public Map<String, String> filterLabels(Set<String> hiddenKeys, Map<String, String> labels) {
        if (labels == null || labels.isEmpty() || hiddenKeys == null || hiddenKeys.isEmpty()) return labels;
        Map<String, String> out = new java.util.LinkedHashMap<>(labels);
        out.keySet().removeIf(hiddenKeys::contains);
        return out;
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) return sra.getRequest();
        return null;
    }
}
