package com.erp.finance.wage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.wage.entity.WageSettlementItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工序工资核算单明细 Mapper
 */
@Mapper
public interface WageSettlementItemMapper extends BaseMapper<WageSettlementItem> {
}
