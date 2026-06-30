package com.erp.finance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.finance.entity.VoucherTemplate;

import java.util.List;

/**
 * 凭证模板服务
 */
public interface VoucherTemplateService extends IService<VoucherTemplate> {

    /**
     * 全量列表
     */
    List<VoucherTemplate> listAll();

    /**
     * 新增模板
     */
    VoucherTemplate saveTemplate(VoucherTemplate template);

    /**
     * 更新模板
     */
    VoucherTemplate updateTemplate(VoucherTemplate template);

    /**
     * 删除模板
     */
    void deleteTemplate(Long templateId);
}
