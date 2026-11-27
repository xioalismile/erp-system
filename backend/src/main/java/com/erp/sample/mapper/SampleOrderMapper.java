package com.erp.sample.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.sample.entity.SampleOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SampleOrderMapper extends BaseMapper<SampleOrder> {
}
