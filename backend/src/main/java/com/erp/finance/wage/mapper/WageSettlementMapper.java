package com.erp.finance.wage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.wage.dto.EmployeeRateDTO;
import com.erp.finance.wage.entity.WageSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工序工资核算单 Mapper
 */
@Mapper
public interface WageSettlementMapper extends BaseMapper<WageSettlement> {

    /**
     * 员工工时费率（计时工资取数来源）。
     * <p>显式列投影到 {@link EmployeeRateDTO}（map-underscore-to-camel-case 生效），
     * 不依赖 basedata 的 Employee 实体；employee.hourly_rate 由 118_wage_settlement.sql 补列。</p>
     */
    @Select("SELECT employee_id, employee_name, dept_id, hourly_rate FROM employee")
    List<EmployeeRateDTO> selectEmployeeRates();
}
