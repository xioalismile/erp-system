package com.erp.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.production.entity.WorkOrderProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkOrderProductMapper extends BaseMapper<WorkOrderProduct> {

    @Select("SELECT * FROM work_order_product WHERE order_id = #{orderId} ORDER BY line_no")
    List<WorkOrderProduct> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 【2026-11-07】工单各行已下推的生产入库数量（含草稿/待审核，已作废不计）。
     * 口径：下游入库单明细按 work_order_line_no 汇总，与「已入库 completed_quantity（仅生效后累加）」分开。
     */
    @Select("SELECT i.work_order_line_no AS \"lineNo\", COALESCE(SUM(i.quantity), 0) AS \"qty\""
            + " FROM production_receipt_item i"
            + " JOIN production_receipt r ON r.receipt_id = i.receipt_id"
            + " WHERE r.production_order_id = #{orderId}"
            + "   AND i.work_order_line_no IS NOT NULL"
            + "   AND (r.status IS NULL OR r.status != '已作废')"
            + " GROUP BY i.work_order_line_no")
    List<Map<String, Object>> sumPushedReceiptByLine(@Param("orderId") Long orderId);

    /**
     * 【2026-11-07】某销售订单明细行已被生产工单占用的数量（未作废工单的成品行合计）。
     * 用于按标准口径重算「已下推生产数量」：新建/编辑/作废/删除工单后都调用，避免只加不减。
     */
    @Select("SELECT COALESCE(SUM(p.quantity), 0) FROM work_order_product p"
            + " JOIN production_order o ON o.order_id = p.order_id"
            + " WHERE p.source_item_id = #{sourceItemId}"
            + "   AND p.source_type = '销售订单'"
            + "   AND (o.status IS NULL OR o.status != '已作废')")
    BigDecimal sumProduceQtyBySourceItem(@Param("sourceItemId") Long sourceItemId);
}
