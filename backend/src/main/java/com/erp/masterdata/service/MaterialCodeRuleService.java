package com.erp.masterdata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.masterdata.entity.MaterialCodeRule;

import java.util.List;

/**
 * 物料编码规则服务
 * 来源: 2026-07-01 客户录音访谈需求
 *
 * 编码生成规则:
 * 1. 根据 categoryId 查找启用的规则；若没有则使用通用规则（category_id 为 NULL）
 * 2. 编码格式: 前缀 + yyyyMMdd + 6位流水号
 * 3. 流水号 = last_seq + 1，并回写 last_seq
 */
public interface MaterialCodeRuleService extends IService<MaterialCodeRule> {

    /** 查询全部规则 */
    List<MaterialCodeRule> listAll();

    /** 保存规则（新增或编辑） */
    MaterialCodeRule saveEntity(MaterialCodeRule rule);

    /** 删除规则 */
    boolean delete(Long ruleId);

    /**
     * 根据物料分类生成物料编码
     * 优先使用分类专属规则，没有则使用通用规则（category_id 为 NULL）
     *
     * @param categoryId 物料分类ID
     * @return 完整物料编码字符串
     */
    String generateCode(Long categoryId);
}
