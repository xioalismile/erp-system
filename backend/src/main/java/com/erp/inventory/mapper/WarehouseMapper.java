package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.basedata.entity.Warehouse;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

@Component("inventoryWarehouseMapper")
@Mapper
public interface WarehouseMapper extends BaseMapper<Warehouse> {
}