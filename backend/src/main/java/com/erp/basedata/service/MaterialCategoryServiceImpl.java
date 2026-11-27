package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.MaterialCategory;
import com.erp.basedata.mapper.MaterialCategoryMapper;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MaterialCategoryServiceImpl extends ServiceImpl<MaterialCategoryMapper, MaterialCategory> implements MaterialCategoryService {

    private final SysSerialNoConfigService serialNoService;

    @Override
    public MaterialCategory getByCode(String categoryCode) {
        return getOne(new QueryWrapper<MaterialCategory>().eq("category_code", categoryCode));
    }

    @Override
    public boolean existsByCode(String categoryCode) {
        return baseMapper.exists(new QueryWrapper<MaterialCategory>().eq("category_code", categoryCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String categoryCode, Long categoryId) {
        return baseMapper.exists(new QueryWrapper<MaterialCategory>()
                .eq("category_code", categoryCode).ne("category_id", categoryId));
    }

    @Override
    public boolean existsUnderParent(String categoryCode, Long parentId, Long excludeId) {
        QueryWrapper<MaterialCategory> qw = new QueryWrapper<MaterialCategory>()
                .eq("category_code", categoryCode);
        if (parentId == null || parentId <= 0) {
            qw.and(w -> w.isNull("parent_id").or().eq("parent_id", 0));
        } else {
            qw.eq("parent_id", parentId);
        }
        if (excludeId != null) {
            qw.ne("category_id", excludeId);
        }
        return baseMapper.exists(qw);
    }

    @Override
    public boolean save(MaterialCategory entity) {
        if (entity.getCategoryCode() == null || entity.getCategoryCode().isBlank()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("BD_MATERIAL_CATEGORY")) {
                throw new BusinessException("物料分类已关闭自动编号，请手动输入编码");
            }
            entity.setCategoryCode(serialNoService.generateSerialNo("BD_MATERIAL_CATEGORY", this::nextCategoryCode));
        }
        if (existsUnderParent(entity.getCategoryCode(), entity.getParentId(), null)) {
            throw new BusinessException("同级下已存在分类编码: " + entity.getCategoryCode());
        }
        return super.save(entity);
    }

    /**
     * 防重续号探测：查询物料分类表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextCategoryCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        MaterialCategory last = getOne(new QueryWrapper<MaterialCategory>().likeRight("category_code", prefix)
                .orderByDesc("category_code").last("LIMIT 1"));
        if (last == null || last.getCategoryCode() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getCategoryCode().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }
}
