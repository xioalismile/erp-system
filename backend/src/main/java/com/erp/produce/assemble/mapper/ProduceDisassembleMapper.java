package com.erp.produce.assemble.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.produce.assemble.entity.ProduceDisassemble;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生产拆卸单主表Mapper
 */
@Mapper
public interface ProduceDisassembleMapper extends BaseMapper<ProduceDisassemble> {
}