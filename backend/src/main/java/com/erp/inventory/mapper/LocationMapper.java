package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.entity.Location;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LocationMapper extends BaseMapper<Location> {
    @Select("SELECT l.* FROM location l JOIN warehouse w ON l.stock_id = w.stock_id WHERE w.stock_code = #{warehouseCode} ORDER BY l.bin_code")
    List<Location> selectByWarehouse(@Param("warehouseCode") String warehouseCode);
}