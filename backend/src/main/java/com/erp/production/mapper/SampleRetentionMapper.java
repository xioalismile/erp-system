package com.erp.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.production.entity.SampleRetention;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SampleRetentionMapper extends BaseMapper<SampleRetention> {

    /** 到期留样（keep_until ≤ 今天 且未提醒）——工作台待办用 */
    @Select("SELECT * FROM sample_retention"
            + " WHERE keep_until <= CURRENT_DATE AND remind_status = '未到期'"
            + " AND (status IS NULL OR status != '已作废')"
            + " ORDER BY keep_until")
    List<SampleRetention> listDue();
}
