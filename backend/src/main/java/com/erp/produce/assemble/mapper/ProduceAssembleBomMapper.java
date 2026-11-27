package com.erp.produce.assemble.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.produce.assemble.entity.ProduceAssembleBom;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生产组装单BOM明细表Mapper
 */
@Mapper
public interface ProduceAssembleBomMapper extends BaseMapper<ProduceAssembleBom> {
}