package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.entity.InventoryBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryBatchMapper extends BaseMapper<InventoryBatch> {

    @Select("SELECT * FROM batch WHERE material_id = #{materialId} ORDER BY production_date ASC")
    List<InventoryBatch> selectByMaterial(@Param("materialId") Long materialId);

    @Select("SELECT * FROM batch WHERE batch_no = #{batchNo}")
    InventoryBatch selectByBatchNo(@Param("batchNo") String batchNo);

    /** 按「物料+批次号」定位（2026-11-06：批次唯一口径改为物料+批次号，避免不同物料同号串料） */
    @Select("SELECT * FROM batch WHERE material_id = #{materialId} AND batch_no = #{batchNo} LIMIT 1")
    InventoryBatch selectByBatchNoAndMaterial(@Param("materialId") Long materialId, @Param("batchNo") String batchNo);

    @Select("SELECT * FROM batch WHERE expiry_date IS NOT NULL AND expiry_date < CURRENT_DATE ORDER BY expiry_date")
    List<InventoryBatch> selectExpiredBatches();

    @Select("SELECT * FROM batch WHERE expiry_date IS NOT NULL AND expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + #{days} ORDER BY expiry_date")
    List<InventoryBatch> selectExpiringBatches(@Param("days") Integer days);
}
