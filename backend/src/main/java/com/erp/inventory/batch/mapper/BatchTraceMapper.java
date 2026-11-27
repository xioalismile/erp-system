package com.erp.inventory.batch.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 批次追溯专用查询（化妆品批记录）。
 * 全部为只读查询，追溯树由现有单据实时推导，不另建追溯关系表。
 */
@Mapper
public interface BatchTraceMapper {

    /** 生产该批次的工单（生产入库明细 / 批生产记录头两条来源） */
    @Select("SELECT DISTINCT pr.production_order_id AS \"orderId\", pr.production_order_no AS \"orderNo\","
            + "       pr.receipt_no AS \"receiptNo\", pr.receipt_date AS \"receiptDate\""
            + "  FROM production_receipt_item pri"
            + "  JOIN production_receipt pr ON pr.receipt_id = pri.receipt_id"
            + " WHERE pri.batch_no = #{batchNo}"
            + "   AND (pr.status IS NULL OR pr.status != '已作废')")
    List<Map<String, Object>> findWorkOrdersByBatchNo(@Param("batchNo") String batchNo);

    /** 某工单的投料明细（带原料批号），追溯上游 */
    @Select("SELECT mr.requisition_no AS \"requisitionNo\", mr.requisition_date AS \"requisitionDate\","
            + "       mri.material_id AS \"materialId\", mri.material_code AS \"materialCode\","
            + "       mri.material_name AS \"materialName\", mri.specification AS \"specification\","
            + "       mri.quantity AS \"quantity\", mri.batch_no AS \"batchNo\""
            + "  FROM material_requisition mr"
            + "  JOIN material_requisition_item mri ON mri.requisition_id = mr.requisition_id"
            + " WHERE mr.work_order_no = #{orderNo}"
            + "   AND (mr.status IS NULL OR mr.status != '已作废')")
    List<Map<String, Object>> findFeedingByWorkOrderNo(@Param("orderNo") String orderNo);

    /** 用原料批号补供应商批号与供应商名 */
    @Select("SELECT batch_no AS \"batchNo\", supplier_batch_no AS \"supplierBatchNo\","
            + "       supplier_name AS \"supplierName\", material_code AS \"materialCode\","
            + "       material_name AS \"materialName\", qc_status AS \"qcStatus\""
            + "  FROM batch_info WHERE batch_no = #{batchNo} LIMIT 1")
    Map<String, Object> findBatchByNo(@Param("batchNo") String batchNo);

    /** 下游：该批次卖给了哪些客户（销售出库明细 → 出库单 → 客户） */
    @Select("SELECT sd.delivery_id AS \"deliveryId\", sd.delivery_no AS \"deliveryNo\","
            + "       sd.delivery_date AS \"deliveryDate\", c.customer_name AS \"customerName\","
            + "       sdi.quantity AS \"quantity\", sd.status AS \"status\""
            + "  FROM sale_delivery_item sdi"
            + "  JOIN sale_delivery sd ON sd.delivery_id = sdi.delivery_id"
            + "  LEFT JOIN customer c ON c.customer_id = sd.customer_id"
            + " WHERE sdi.batch_no = #{batchNo}"
            + "   AND (sd.status IS NULL OR sd.status != '已作废')"
            + " ORDER BY sd.delivery_date")
    List<Map<String, Object>> findDeliveriesByBatchNo(@Param("batchNo") String batchNo);

    /** 该批次的上游原料是否还被别的成品批使用（反查召回用）：给定原料批号，查所有产出成品批 */
    @Select("SELECT DISTINCT pri.batch_no AS \"batchNo\", pr.production_order_no AS \"orderNo\","
            + "       pr.receipt_date AS \"receiptDate\""
            + "  FROM material_requisition mr"
            + "  JOIN material_requisition_item mri ON mri.requisition_id = mr.requisition_id"
            + "  JOIN production_receipt pr ON pr.production_order_no = mr.work_order_no"
            + "  JOIN production_receipt_item pri ON pri.receipt_id = pr.receipt_id"
            + " WHERE mri.batch_no = #{batchNo}"
            + "   AND (mr.status IS NULL OR mr.status != '已作废')"
            + "   AND (pr.status IS NULL OR pr.status != '已作废')")
    List<Map<String, Object>> findDownstreamProductsByMaterialBatch(@Param("batchNo") String batchNo);

    /** 批次档案原始行 */
    @Select("SELECT id, batch_no AS \"batchNo\", material_id AS \"materialId\","
            + "       material_code AS \"materialCode\", material_name AS \"materialName\","
            + "       specification, unit, production_date AS \"productionDate\","
            + "       expiry_date AS \"expiryDate\", supplier_name AS \"supplierName\","
            + "       supplier_batch_no AS \"supplierBatchNo\", quantity, available_qty AS \"availableQty\","
            + "       qc_status AS \"qcStatus\", qc_result AS \"qcResult\","
            + "       released_by AS \"releasedBy\", released_at AS \"releasedAt\""
            + "  FROM batch_info WHERE id = #{batchId}")
    Map<String, Object> findBatchById(@Param("batchId") Long batchId);
}
