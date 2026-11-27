package com.erp.produce.assemble.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.produce.assemble.entity.ProduceDisassembleBom;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生产拆卸单BOM明细表Mapper
 */
@Mapper
public interface ProduceDisassembleBomMapper extends BaseMapper<ProduceDisassembleBom> {
}