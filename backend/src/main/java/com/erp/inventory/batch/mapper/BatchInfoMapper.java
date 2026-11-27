package com.erp.inventory.batch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.batch.entity.BatchInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BatchInfoMapper extends BaseMapper<BatchInfo> {

    @Select("SELECT * FROM batch_info WHERE batch_no = #{batchNo}")
    BatchInfo selectByBatchNo(@Param("batchNo") String batchNo);

    @Select("SELECT * FROM batch_info WHERE material_id = #{materialId} AND status IN (0, 1) AND available_qty > 0 ORDER BY production_date ASC")
    List<BatchInfo> selectAvailableBatches(@Param("materialId") Long materialId);

    @Select("SELECT * FROM batch_info WHERE expiry_date IS NOT NULL AND expiry_date < CURDATE() AND status != 3")
    List<BatchInfo> selectExpiredBatches();

    @Update("UPDATE batch_info SET status = 3 WHERE id = #{id}")
    int updateStatusToExpired(@Param("id") Long id);
}
