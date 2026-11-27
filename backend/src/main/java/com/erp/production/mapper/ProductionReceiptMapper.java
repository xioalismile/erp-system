package com.erp.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.production.entity.ProductionReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductionReceiptMapper extends BaseMapper<ProductionReceipt> {

    /**
     * 已作废单据不计入任何"已入库量"口径（作废的入库单不应继续占用工序的入库额度）。
     *
     * <p><b>注意</b>：这里刻意用 {@code !=} 而不是 {@code <>} —— MyBatis-Plus 会把注解 SQL
     * 当作 XML 解析，裸的 {@code <} 会让启动直接报 "元素内容必须由格式正确的字符数据或标记组成"。</p>
     */
    String NOT_VOIDED = " (status IS NULL OR status != '已作废') ";

    /**
     * 【第 11 期 / 2026-11-06 行级化】某道工单工序已入库的数量合计。
     *
     * <p>口径：新单据按<b>明细行</b>的 source_operation_id 汇总（一张入库单可含多道入库工序的行）；
     * 存量单据（明细行没有来源工序）按<b>表头</b> source_operation_id 取 total_quantity，二者不重复计入。
     * 已作废的入库单不计。</p>
     */
    @Select("SELECT COALESCE(SUM(t.qty), 0) FROM ("
            + "  SELECT i.quantity AS qty FROM production_receipt_item i"
            + "    JOIN production_receipt r ON r.receipt_id = i.receipt_id"
            + "   WHERE i.source_operation_id = #{operationRowId} AND" + " (r.status IS NULL OR r.status != '已作废')"
            + "  UNION ALL"
            + "  SELECT r.total_quantity AS qty FROM production_receipt r"
            + "   WHERE r.source_operation_id = #{operationRowId} AND" + " (r.status IS NULL OR r.status != '已作废')"
            + "     AND NOT EXISTS (SELECT 1 FROM production_receipt_item i2"
            + "                      WHERE i2.receipt_id = r.receipt_id AND i2.source_operation_id IS NOT NULL)"
            + ") t")
    BigDecimal sumPushedByOperation(@Param("operationRowId") Long operationRowId);

    /**
     * 【第 13 期 / 2026-11-06 行级化】批量取多道工序的已入库量（看板一次查完，避免逐行查询）。
     * 返回 [{operationId, qty}]；口径同上（行级优先、存量按表头、已作废不计）。
     */
    @Select("<script>"
            + "SELECT t.op_id AS \"operationId\", COALESCE(SUM(t.qty), 0) AS qty FROM ("
            + "  SELECT i.source_operation_id AS op_id, i.quantity AS qty FROM production_receipt_item i"
            + "    JOIN production_receipt r ON r.receipt_id = i.receipt_id"
            + "   WHERE i.source_operation_id IN"
            + "   <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "     AND (r.status IS NULL OR r.status != '已作废')"
            + "  UNION ALL"
            + "  SELECT r.source_operation_id AS op_id, r.total_quantity AS qty FROM production_receipt r"
            + "   WHERE r.source_operation_id IN"
            + "   <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "     AND (r.status IS NULL OR r.status != '已作废')"
            + "     AND NOT EXISTS (SELECT 1 FROM production_receipt_item i2"
            + "                      WHERE i2.receipt_id = r.receipt_id AND i2.source_operation_id IS NOT NULL)"
            + ") t GROUP BY t.op_id"
            + "</script>")
    List<Map<String, Object>> sumReceivedByOperations(@Param("ids") Collection<Long> ids);
}
