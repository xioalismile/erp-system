package com.erp.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.entity.QcRecordItem;
import com.erp.qc.mapper.QcRecordItemMapper;
import com.erp.qc.mapper.QcRecordMapper;
import com.erp.qc.service.QcRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工序检验记录查询服务实现（只读）。
 *
 * <p>与生产模块其它查询接口口径一致：本服务不接数据权限（生产模块列表接口均未接），保持同模块行为一致。</p>
 */
@Service
@RequiredArgsConstructor
public class QcRecordServiceImpl implements QcRecordService {

    private final QcRecordMapper qcRecordMapper;
    /** 【第 19 期】检验记录实测明细（含留证图片） */
    private final QcRecordItemMapper qcRecordItemMapper;
    /** 数据权限解析器（2026-10-09）：/page 与 /summary 按角色数据范围收敛 */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @Override
    public PageResult<QcRecord> pageQuery(int current, int size,
                                          String dateFrom, String dateTo,
                                          String qcType, String result,
                                          String orderNo, String operationName,
                                          String inspectorName, String keyword) {
        QueryWrapper<QcRecord> qw = buildWrapper(dateFrom, dateTo, qcType, result,
                orderNo, operationName, inspectorName, keyword);
        // 排序：按检验时间倒序，其次记录ID倒序（新增在前）
        qw.orderByDesc("inspect_time").orderByDesc("record_id");
        Page<QcRecord> page = qcRecordMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    @Override
    public Map<String, Object> summary(String dateFrom, String dateTo,
                                       String qcType, String result,
                                       String orderNo, String operationName,
                                       String inspectorName, String keyword) {
        QueryWrapper<QcRecord> qw = buildWrapper(dateFrom, dateTo, qcType, result,
                orderNo, operationName, inspectorName, keyword);
        qw.select("COUNT(*) AS batch_count",
                "SUM(CASE WHEN result = '不合格' THEN 1 ELSE 0 END) AS defect_batch",
                "COALESCE(SUM(inspect_qty), 0) AS inspect_qty_total",
                "COALESCE(SUM(qualified_qty), 0) AS qualified_qty_total",
                "COALESCE(SUM(defect_qty), 0) AS defect_qty_total");
        List<Map<String, Object>> rows = qcRecordMapper.selectMaps(qw);
        Map<String, Object> row = (rows == null || rows.isEmpty()) ? null : rows.get(0);

        long batch = toLong(row == null ? null : row.get("batch_count"));
        long defect = toLong(row == null ? null : row.get("defect_batch"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batchCount", batch);
        out.put("qualifiedBatchCount", batch - defect);
        out.put("defectBatchCount", defect);
        // 合格率：分母为 0 时返回空值（与经营看板 quality 块 rate() 同口径，不用 0 冒充）
        out.put("passRate", batch <= 0 ? null : Math.round((batch - defect) * 100.0 / batch));
        out.put("inspectQtyTotal", toDecimal(row == null ? null : row.get("inspect_qty_total")));
        out.put("qualifiedQtyTotal", toDecimal(row == null ? null : row.get("qualified_qty_total")));
        out.put("defectQtyTotal", toDecimal(row == null ? null : row.get("defect_qty_total")));
        return out;
    }

    @Override
    public List<QcRecordItem> listItems(Long recordId) {
        if (recordId == null) return List.of();
        QueryWrapper<QcRecordItem> qw = new QueryWrapper<>();
        qw.eq("record_id", recordId).orderByAsc("line_no").orderByAsc("item_id");
        return qcRecordItemMapper.selectList(qw);
    }

    // ---------------- 内部工具 ----------------

    /**
     * 组装筛选条件（分页与汇总共用，保证两处口径完全一致）。
     *
     * <p>日期：优先按检验时间过滤；检验时间为空的历史记录回落到创建时间，避免漏数。</p>
     * <p>关键字成组：record_no / order_no 的模糊匹配必须用 and(w -&gt; ...) 包住，
     * 否则平铺 or() 会吃掉后续过滤条件（本项目已记录过的坑）。</p>
     */
    private QueryWrapper<QcRecord> buildWrapper(String dateFrom, String dateTo,
                                                String qcType, String result,
                                                String orderNo, String operationName,
                                                String inspectorName, String keyword) {
        QueryWrapper<QcRecord> qw = new QueryWrapper<>();
        if (dateFrom != null && !dateFrom.isBlank()) {
            // 形参为 String（JDBC 以 varchar 绑定），PG 下 date 与 varchar 不能直接比较 → 参数侧显式 ::date 转换
            qw.apply("COALESCE(inspect_time, created_at)::date >= {0}::date", dateFrom.trim());
        }
        if (dateTo != null && !dateTo.isBlank()) {
            qw.apply("COALESCE(inspect_time, created_at)::date <= {0}::date", dateTo.trim());
        }
        if (qcType != null && !qcType.isBlank()) qw.eq("qc_type", qcType.trim());
        if (result != null && !result.isBlank()) qw.eq("result", result.trim());
        if (orderNo != null && !orderNo.isBlank()) qw.like("order_no", orderNo.trim());
        if (operationName != null && !operationName.isBlank()) qw.like("operation_name", operationName.trim());
        if (inspectorName != null && !inspectorName.isBlank()) qw.like("inspector_name", inspectorName.trim());
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            // 成组：避免平铺 or() 让命中第一列的行绕过其它筛选条件
            qw.and(w -> w.like("record_no", kw).or().like("order_no", kw));
        }
        // 数据权限（2026-10-09）：按角色数据范围收敛可见范围（创建人列 created_by）。
        // 与生产模块其它查询接口同口径（WorkOrderServiceImpl / ProductionController 均
        // applyIfLimited(wrapper, null, "created_by")）。
        // 说明：检验人列 inspector_id 存的是「用户ID」（WorkOrderOperationController 传 getCurrentUserId），
        // 与本机制要求的「员工ID」维度不一致，故不作为业务归属列。
        dataScopeResolver.applyIfLimited(qw, null, "created_by");
        return qw;
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return new BigDecimal(String.valueOf(v)).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    private BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return new BigDecimal(v.toString());
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
