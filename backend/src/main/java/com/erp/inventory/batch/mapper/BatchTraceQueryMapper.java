package com.erp.inventory.batch.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 批次溯源查询（2026-11-06）。
 *
 * <p>全部只读：以「库存批次 + 库存流水 + 各单据明细批号」实时推导，
 * 不新建追溯关系表、不复制数据；历史单据无批号的节点由服务层标注「历史数据未记录批次」。</p>
 */
@Mapper
public interface BatchTraceQueryMapper {

    /** 批次主档 + 物料档案 + 供应商 + 批次档案质量状态 */
    @Select("SELECT b.batch_id AS \"batchId\", b.batch_no AS \"batchNo\", b.material_id AS \"materialId\","
            + "       m.material_code AS \"materialCode\", m.material_name AS \"materialName\","
            + "       m.model AS \"specification\", u.unit_name AS \"unitName\","
            + "       b.quantity, b.available_quantity AS \"availableQuantity\", b.unit_cost AS \"unitCost\","
            + "       b.production_date AS \"productionDate\", b.expiry_date AS \"expiryDate\","
            + "       b.incoming_date AS \"incomingDate\", b.status,"
            + "       s.supplier_name AS \"supplierName\","
            + "       bi.supplier_batch_no AS \"supplierBatchNo\", bi.qc_status AS \"qcStatus\","
            + "       bi.qc_result AS \"qcResult\", bi.released_by AS \"releasedBy\", bi.released_at AS \"releasedAt\""
            + "  FROM batch b"
            + "  LEFT JOIN material m ON m.material_id = b.material_id"
            + "  LEFT JOIN unit_of_measure u ON u.unit_id = m.base_unit_id"
            + "  LEFT JOIN supplier s ON s.supplier_id = b.supplier_id"
            + "  LEFT JOIN batch_info bi ON bi.batch_no = b.batch_no AND bi.material_id = b.material_id"
            + " WHERE b.batch_no = #{batchNo} AND b.material_id = #{materialId}"
            + " LIMIT 1")
    Map<String, Object> findBatch(@Param("batchNo") String batchNo, @Param("materialId") Long materialId);

    /** 批次档案（库存批次不存在时的兜底展示：质量状态/供应商批号） */
    @Select("SELECT batch_no AS \"batchNo\", material_id AS \"materialId\", material_code AS \"materialCode\","
            + "       material_name AS \"materialName\", specification, unit,"
            + "       production_date AS \"productionDate\", expiry_date AS \"expiryDate\","
            + "       supplier_name AS \"supplierName\", supplier_batch_no AS \"supplierBatchNo\","
            + "       qc_status AS \"qcStatus\", qc_result AS \"qcResult\","
            + "       released_by AS \"releasedBy\", released_at AS \"releasedAt\""
            + "  FROM batch_info WHERE batch_no = #{batchNo} AND material_id = #{materialId} LIMIT 1")
    Map<String, Object> findBatchArchive(@Param("batchNo") String batchNo, @Param("materialId") Long materialId);

    /** 该批次的全部出入库流水（带仓库/货位） */
    @Select("SELECT l.ledger_id AS \"ledgerId\", l.trans_date AS \"transDate\", l.trans_type AS \"transType\","
            + "       l.trans_no AS \"transNo\", l.batch_no AS \"batchNo\","
            + "       l.in_quantity AS \"inQuantity\", l.out_quantity AS \"outQuantity\","
            + "       l.unit_cost AS \"unitCost\", l.source_bill_type AS \"sourceBillType\","
            + "       l.source_bill_no AS \"sourceBillNo\", l.stock_id AS \"stockId\", l.bin_id AS \"binId\","
            + "       w.stock_name AS \"warehouseName\", loc.bin_name AS \"binName\""
            + "  FROM inventory_ledger l"
            + "  LEFT JOIN warehouse w ON w.stock_id = l.stock_id"
            + "  LEFT JOIN location loc ON loc.bin_id = l.bin_id"
            + " WHERE l.batch_no = #{batchNo} AND l.material_id = #{materialId}"
            + " ORDER BY l.trans_date, l.ledger_id")
    List<Map<String, Object>> findLedger(@Param("batchNo") String batchNo, @Param("materialId") Long materialId);

    /** 产出该批次的入库明细（生产入库 → 生产工单） */
    @Select("SELECT pr.receipt_id AS \"receiptId\", pr.receipt_no AS \"receiptNo\","
            + "       pr.receipt_date AS \"receiptDate\", pr.status, pr.source_bill_no AS \"workOrderNo\","
            + "       pri.receipt_item_id AS \"receiptItemId\", pri.material_id AS \"materialId\","
            + "       pri.material_code AS \"materialCode\", pri.material_name AS \"materialName\","
            + "       pri.specification, pri.quantity, pri.batch_no AS \"batchNo\","
            + "       pri.production_date AS \"productionDate\", pri.expiry_date AS \"expiryDate\""
            + "  FROM production_receipt_item pri"
            + "  JOIN production_receipt pr ON pr.receipt_id = pri.receipt_id"
            + " WHERE pri.batch_no = #{batchNo} AND pri.material_id = #{materialId}"
            + "   AND (pr.status IS NULL OR pr.status <> '已作废')"
            + " ORDER BY pr.receipt_date, pr.receipt_id")
    List<Map<String, Object>> findProductionReceipts(@Param("batchNo") String batchNo,
                                                     @Param("materialId") Long materialId);

    /** 指定生产工单的领料明细（上游组成物料 + 各自批号） */
    @Select("SELECT mr.requisition_id AS \"requisitionId\", mr.requisition_no AS \"requisitionNo\","
            + "       mr.requisition_date AS \"requisitionDate\", mr.bill_type AS \"billType\", mr.status,"
            + "       mri.material_id AS \"materialId\", mri.material_code AS \"materialCode\","
            + "       mri.material_name AS \"materialName\", mri.specification,"
            + "       mri.quantity, mri.batch_no AS \"batchNo\""
            + "  FROM material_requisition mr"
            + "  JOIN material_requisition_item mri ON mri.requisition_id = mr.requisition_id"
            + " WHERE mr.work_order_no = #{workOrderNo}"
            + "   AND (mr.status IS NULL OR mr.status <> '已作废')"
            + "   AND (mr.bill_type IS NULL OR mr.bill_type = 'PICK')"
            + " ORDER BY mr.requisition_date, mr.requisition_id, mri.line_no")
    List<Map<String, Object>> findFeedingByWorkOrder(@Param("workOrderNo") String workOrderNo);

    /** 本批被哪些生产领料单领用（下游：成品批来源） */
    @Select("SELECT mr.requisition_id AS \"requisitionId\", mr.requisition_no AS \"requisitionNo\","
            + "       mr.requisition_date AS \"requisitionDate\", mr.work_order_no AS \"workOrderNo\","
            + "       mri.quantity"
            + "  FROM material_requisition_item mri"
            + "  JOIN material_requisition mr ON mr.requisition_id = mri.requisition_id"
            + " WHERE mri.batch_no = #{batchNo} AND mri.material_id = #{materialId}"
            + "   AND (mr.status IS NULL OR mr.status <> '已作废')"
            + "   AND (mr.bill_type IS NULL OR mr.bill_type = 'PICK')"
            + " ORDER BY mr.requisition_date, mr.requisition_id")
    List<Map<String, Object>> findConsumingRequisitions(@Param("batchNo") String batchNo,
                                                        @Param("materialId") Long materialId);

    /** 指定生产工单产出的成品批（下游成品批） */
    @Select("SELECT pr.receipt_id AS \"receiptId\", pr.receipt_no AS \"receiptNo\","
            + "       pr.receipt_date AS \"receiptDate\", pr.status,"
            + "       pri.material_id AS \"materialId\", pri.material_code AS \"materialCode\","
            + "       pri.material_name AS \"materialName\", pri.specification,"
            + "       pri.quantity, pri.batch_no AS \"batchNo\""
            + "  FROM production_receipt pr"
            + "  JOIN production_receipt_item pri ON pri.receipt_id = pr.receipt_id"
            + " WHERE pr.source_bill_no = #{workOrderNo}"
            + "   AND pri.batch_no IS NOT NULL AND pri.batch_no <> ''"
            + "   AND (pr.status IS NULL OR pr.status <> '已作废')"
            + " ORDER BY pr.receipt_date, pr.receipt_id")
    List<Map<String, Object>> findProductsByWorkOrder(@Param("workOrderNo") String workOrderNo);
}
