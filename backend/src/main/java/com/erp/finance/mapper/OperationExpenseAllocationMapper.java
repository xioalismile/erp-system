package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.entity.OperationExpenseAllocation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运营费用分摊明细 Mapper
 */
@Mapper
public interface OperationExpenseAllocationMapper extends BaseMapper<OperationExpenseAllocation> {
}
