package com.erp.inventory.batch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.batch.entity.BatchInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BatchInventoryMapper extends BaseMapper<BatchInventory> {

    @Select("SELECT * FROM batch_inventory WHERE batch_id = #{batchId}")
    List<BatchInventory> selectByBatchId(@Param("batchId") Long batchId);

    @Select("SELECT * FROM batch_inventory WHERE batch_id = #{batchId} AND warehouse_id = #{warehouseId}")
    BatchInventory selectByBatchIdAndWarehouseId(@Param("batchId") Long batchId, @Param("warehouseId") Long warehouseId);

    @Select("SELECT * FROM batch_inventory WHERE material_id = #{materialId} AND warehouse_id = #{warehouseId} AND available_qty > 0 ORDER BY id ASC")
    List<BatchInventory> selectAvailableByMaterialAndWarehouse(@Param("materialId") Long materialId, @Param("warehouseId") Long warehouseId);
}
