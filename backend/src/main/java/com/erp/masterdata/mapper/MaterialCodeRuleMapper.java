package com.erp.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.masterdata.entity.MaterialCodeRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 物料编码规则 Mapper
 */
@Mapper
public interface MaterialCodeRuleMapper extends BaseMapper<MaterialCodeRule> {

    /**
     * 带行锁读取编码规则（PostgreSQL 悲观锁）。
     * 用途：物料编码采用"删除即释放 + 补最小空洞"策略后，并发请求会争抢同一个最小空洞号，
     * 必须对规则行加锁使取号串行化，否则会出现重号（由 material_code 唯一约束兜底报错）。
     *
     * @param ruleId 规则ID
     * @return 规则（不存在返回 null）
     */
    @Select("SELECT * FROM material_code_rule WHERE rule_id = #{ruleId} FOR UPDATE")
    MaterialCodeRule selectByRuleIdForUpdate(@Param("ruleId") Long ruleId);
}
