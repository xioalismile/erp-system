package com.erp.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.masterdata.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}
