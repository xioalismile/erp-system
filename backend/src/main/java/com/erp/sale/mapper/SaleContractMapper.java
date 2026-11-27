package com.erp.sale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.sale.entity.SaleContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SaleContractMapper extends BaseMapper<SaleContract> {

    /**
     * 生成合同编号: SC+yyyyMMdd+4位流水
     */
    @Update("UPDATE seq_sale_contract SET seq = seq + 1 WHERE prefix = #{prefix} RETURNING seq")
    Long nextSeq(@Param("prefix") String prefix);
}
