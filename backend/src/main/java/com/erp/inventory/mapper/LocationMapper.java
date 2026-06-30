package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.entity.Location;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LocationMapper extends BaseMapper<Location> {
    @Select("SELECT * FROM location WHERE warehouse_code = #{warehouseCode} AND deleted = 0 ORDER BY location_code")
    List<Location> selectByWarehouse(@Param("warehouseCode") String warehouseCode);
}