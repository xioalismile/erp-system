package com.erp.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.production.entity.WorkOrderMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkOrderMaterialMapper extends BaseMapper<WorkOrderMaterial> {

    /**
     * 【2026-11-07】工单各用料行已下推的生产领料数量（含草稿/待审核，已作废不计）。
     * 只统计生产领料单（PICK）；补料/退料不影响领料剩余量。
     * 与「已领料 issued_quantity（仅生效后累加）」分开，用于选单弹窗「已下推/剩余」。
     */
    @Select("SELECT i.work_order_line_no AS \"lineNo\", COALESCE(SUM(i.quantity), 0) AS \"qty\""
            + " FROM material_requisition_item i"
            + " JOIN material_requisition r ON r.requisition_id = i.requisition_id"
            + " WHERE r.work_order_no = #{workOrderNo}"
            + "   AND r.bill_type = 'PICK'"
            + "   AND i.work_order_line_no IS NOT NULL"
            + "   AND (r.status IS NULL OR r.status != '已作废')"
            + " GROUP BY i.work_order_line_no")
    List<Map<String, Object>> sumPushedPickByLine(@Param("workOrderNo") String workOrderNo);
}
