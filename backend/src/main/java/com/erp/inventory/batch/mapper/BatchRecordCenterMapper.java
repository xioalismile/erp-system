package com.erp.inventory.batch.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 批记录中心查询（化妆品批记录）。
 * 全部只读，一屏汇总全部批次与状态；筛选条件全部可选。
 */
@Mapper
public interface BatchRecordCenterMapper {

    /**
     * 批次总览分页查询。
     * 生产进度取自批生产记录状态（生成中/已完成），放行状态取已生效放行单。
     */
    @Select("<script>"
            + "SELECT b.id AS \"batchId\", b.batch_no AS \"batchNo\", b.material_id AS \"materialId\","
            + "       b.material_code AS \"materialCode\", b.material_name AS \"materialName\","
            + "       b.specification, b.unit, b.production_date AS \"productionDate\","
            + "       b.expiry_date AS \"expiryDate\", b.quantity, b.available_qty AS \"availableQty\","
            + "       b.supplier_name AS \"supplierName\", b.supplier_batch_no AS \"supplierBatchNo\","
            + "       b.qc_status AS \"qcStatus\", b.qc_result AS \"qcResult\","
            + "       b.released_by AS \"releasedBy\", b.released_at AS \"releasedAt\","
            + "       (SELECT COUNT(*) FROM release_order r"
            + "         WHERE r.batch_id = b.id AND r.status = '已生效') AS \"releaseCount\""
            + "  FROM batch_info b"
            + " WHERE 1 = 1"
            + " <if test='keyword != null and keyword != \"\"'>"
            + "   AND (b.batch_no LIKE CONCAT('%', #{keyword}, '%')"
            + "        OR b.material_code LIKE CONCAT('%', #{keyword}, '%')"
            + "        OR b.material_name LIKE CONCAT('%', #{keyword}, '%'))"
            + " </if>"
            + " <if test='qcStatus != null and qcStatus != \"\"'> AND b.qc_status = #{qcStatus} </if>"
            + " <if test='materialId != null'> AND b.material_id = #{materialId} </if>"
            + " <if test='dateFrom != null and dateFrom != \"\"'> AND b.production_date &gt;= CAST(#{dateFrom} AS DATE) </if>"
            + " <if test='dateTo != null and dateTo != \"\"'> AND b.production_date &lt;= CAST(#{dateTo} AS DATE) </if>"
            + " ORDER BY b.id DESC"
            + " LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<Map<String, Object>> pageList(@Param("keyword") String keyword,
                                       @Param("qcStatus") String qcStatus,
                                       @Param("materialId") Long materialId,
                                       @Param("dateFrom") String dateFrom,
                                       @Param("dateTo") String dateTo,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /** 与 pageList 同条件的总数 */
    @Select("<script>"
            + "SELECT COUNT(*) FROM batch_info b WHERE 1 = 1"
            + " <if test='keyword != null and keyword != \"\"'>"
            + "   AND (b.batch_no LIKE CONCAT('%', #{keyword}, '%')"
            + "        OR b.material_code LIKE CONCAT('%', #{keyword}, '%')"
            + "        OR b.material_name LIKE CONCAT('%', #{keyword}, '%'))"
            + " </if>"
            + " <if test='qcStatus != null and qcStatus != \"\"'> AND b.qc_status = #{qcStatus} </if>"
            + " <if test='materialId != null'> AND b.material_id = #{materialId} </if>"
            + " <if test='dateFrom != null and dateFrom != \"\"'> AND b.production_date &gt;= CAST(#{dateFrom} AS DATE) </if>"
            + " <if test='dateTo != null and dateTo != \"\"'> AND b.production_date &lt;= CAST(#{dateTo} AS DATE) </if>"
            + "</script>")
    long pageCount(@Param("keyword") String keyword,
                   @Param("qcStatus") String qcStatus,
                   @Param("materialId") Long materialId,
                   @Param("dateFrom") String dateFrom,
                   @Param("dateTo") String dateTo);

    /** 留样台账 */
    @Select("<script>"
            + "SELECT s.* FROM sample_retention s WHERE 1 = 1"
            + " <if test='keyword != null and keyword != \"\"'>"
            + "   AND (s.batch_no LIKE CONCAT('%', #{keyword}, '%')"
            + "        OR s.material_name LIKE CONCAT('%', #{keyword}, '%'))"
            + " </if>"
            + " <if test='remindStatus != null and remindStatus != \"\"'> AND s.remind_status = #{remindStatus} </if>"
            + " ORDER BY s.keep_until DESC NULLS LAST"
            + " LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<Map<String, Object>> sampleList(@Param("keyword") String keyword,
                                         @Param("remindStatus") String remindStatus,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM sample_retention s WHERE 1 = 1"
            + " <if test='keyword != null and keyword != \"\"'>"
            + "   AND (s.batch_no LIKE CONCAT('%', #{keyword}, '%')"
            + "        OR s.material_name LIKE CONCAT('%', #{keyword}, '%'))"
            + " </if>"
            + " <if test='remindStatus != null and remindStatus != \"\"'> AND s.remind_status = #{remindStatus} </if>"
            + "</script>")
    long sampleCount(@Param("keyword") String keyword,
                     @Param("remindStatus") String remindStatus);
}
