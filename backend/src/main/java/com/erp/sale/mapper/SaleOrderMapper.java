package com.erp.sale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.sale.entity.SaleOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SaleOrderMapper extends BaseMapper<SaleOrder> {

    /**
     * 生成订单编号: SO+yyyyMMdd+4位流水
     */
    @Update("UPDATE seq_sale_order SET seq = seq + 1 WHERE prefix = #{prefix} RETURNING seq")
    Long nextSeq(@Param("prefix") String prefix);
}
