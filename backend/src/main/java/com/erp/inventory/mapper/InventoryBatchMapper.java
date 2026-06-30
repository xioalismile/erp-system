package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.entity.InventoryBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryBatchMapper extends BaseMapper<InventoryBatch> {
    @Select("SELECT * FROM inventory_batch WHERE material_code = #{materialCode} AND warehouse_code = #{warehouseCode} AND deleted = 0 ORDER BY production_date ASC")
    List<InventoryBatch> selectByMaterialAndWarehouse(@Param("materialCode") String materialCode, @Param("warehouseCode") String warehouseCode);

    @Select("SELECT * FROM inventory_batch WHERE batch_no = #{batchNo} AND deleted = 0")
    InventoryBatch selectByBatchNo(@Param("batchNo") String batchNo);

    @Select("SELECT * FROM inventory_batch WHERE warehouse_code = #{warehouseCode} AND deleted = 0 ORDER BY material_code")
    List<InventoryBatch> selectByWarehouse(@Param("warehouseCode") String warehouseCode);

    @Select("SELECT * FROM inventory_batch WHERE expiry_date IS NOT NULL AND expiry_date < CURRENT_DATE AND deleted = 0 ORDER BY expiry_date")
    List<InventoryBatch> selectExpiredBatches();

    @Select("SELECT * FROM inventory_batch WHERE expiry_date IS NOT NULL AND expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + #{days} AND deleted = 0 ORDER BY expiry_date")
    List<InventoryBatch> selectExpiringBatches(@Param("days") Integer days);
}