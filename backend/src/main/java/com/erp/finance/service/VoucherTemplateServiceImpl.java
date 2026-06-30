package com.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.finance.entity.VoucherTemplate;
import com.erp.finance.mapper.VoucherTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 凭证模板服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherTemplateServiceImpl extends ServiceImpl<VoucherTemplateMapper, VoucherTemplate>
        implements VoucherTemplateService {

    private final VoucherTemplateMapper templateMapper;

    @Override
    public List<VoucherTemplate> listAll() {
        QueryWrapper<VoucherTemplate> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort_order").orderByDesc("created_at");
        return templateMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public VoucherTemplate saveTemplate(VoucherTemplate template) {
        if (template.getVoucherType() == null) template.setVoucherType("记账凭证");
        if (template.getIsAutoCreate() == null) template.setIsAutoCreate((short) 0);
        if (template.getSortOrder() == null) template.setSortOrder(0);
        if (template.getStatus() == null) template.setStatus("启用");
        templateMapper.insert(template);
        log.info("凭证模板创建: {}", template.getTemplateCode());
        return template;
    }

    @Override
    @Transactional
    public VoucherTemplate updateTemplate(VoucherTemplate template) {
        templateMapper.updateById(template);
        log.info("凭证模板更新: {}", template.getTemplateCode());
        return template;
    }

    @Override
    @Transactional
    public void deleteTemplate(Long templateId) {
        templateMapper.deleteById(templateId);
        log.info("凭证模板删除: {}", templateId);
    }
}
