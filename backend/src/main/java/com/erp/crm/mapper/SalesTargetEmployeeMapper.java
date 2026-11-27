package com.erp.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.crm.entity.SalesTargetEmployee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface SalesTargetEmployeeMapper extends BaseMapper<SalesTargetEmployee> {

    /** 汇总某年度已分配的销售目标总额 */
    @Select("SELECT COALESCE(SUM(annual_target_amount),0) FROM sales_target_employee WHERE target_year = #{year}")
    BigDecimal sumAllocatedAmount(@Param("year") Integer year);

    /** 统计某年度已分配的销售人数 */
    @Select("SELECT COUNT(*) FROM sales_target_employee WHERE target_year = #{year}")
    Integer countAllocatedEmployees(@Param("year") Integer year);
}
