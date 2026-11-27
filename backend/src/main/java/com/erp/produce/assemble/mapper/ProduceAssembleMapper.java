package com.erp.produce.assemble.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.produce.assemble.entity.ProduceAssemble;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生产组装单主表Mapper
 */
@Mapper
public interface ProduceAssembleMapper extends BaseMapper<ProduceAssemble> {
}