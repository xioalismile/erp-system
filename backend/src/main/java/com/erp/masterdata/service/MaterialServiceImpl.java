package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 物料保存服务。
 * 分类编码规则取号已前置到调用方（界面控制器 / AI 创建入口）在进入事务前完成；
 * save 事务内对"编码为空"只做守卫：不再调用任何嵌套事务方法，避免规则缺失的异常
 * 被吞掉后外层事务被标记 rollback-only（提交时抛 UnexpectedRollbackException）。
 */
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl extends ServiceImpl<MaterialMapper, Material> implements MaterialService {

    private final SysSerialNoConfigService serialNoService;
    /** 辅计量换算明细：随物料保存一起整表删插（2026-11-10 多单位换算重构第一阶段） */
    private final com.erp.basedata.service.MaterialUnitConversionService materialUnitConversionService;

    @Override
    public Material getByCode(String materialCode) {
        return getOne(new QueryWrapper<Material>().eq("material_code", materialCode));
    }

    @Override
    public java.util.List<String> listCodesByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return java.util.Collections.emptyList();
        }
        return baseMapper.selectCodesByPrefix(prefix);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public boolean save(Material entity) {
        if (entity.getMaterialCode() == null || entity.getMaterialCode().isBlank()) {
            // 设置中心已关闭自动生成时，要求手动输入编码
            if (!serialNoService.isAutoGenerate("MD_MATERIAL")) {
                throw new BusinessException("物料已关闭自动编号，请手动输入物料编码");
            }
            // 三参重载：以业务表实况补最小空洞，删除物料后其编码会被自动复用（不再出现流水断层）
            entity.setMaterialCode(serialNoService.generateSerialNo("MD_MATERIAL",
                    this::nextMaterialCode, this::listCodesByPrefix));
        }
        long count = count(new QueryWrapper<Material>().eq("material_code", entity.getMaterialCode()));
        if (count > 0) {
            throw new BusinessException("物料编码已存在: " + entity.getMaterialCode());
        }
        boolean saved = super.save(entity);
        // 辅计量换算明细随物料一起保存（事务内整表删插）；未带明细的调用方（导入等）不受影响
        if (saved && entity.getUnitConversions() != null) {
            materialUnitConversionService.replaceForMaterial(entity.getMaterialId(), entity.getUnitConversions());
        }
        return saved;
    }

    /**
     * 更新物料：同步保存辅计量换算明细（同一事务内整表删插）。
     * 请求未带换算明细（unitConversions == null）时不动既有明细——
     * 导入、自动带价等内部更新链路不会误清配置。
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public boolean updateById(Material entity) {
        boolean updated = super.updateById(entity);
        if (updated && entity.getUnitConversions() != null) {
            materialUnitConversionService.replaceForMaterial(entity.getMaterialId(), entity.getUnitConversions());
        }
        return updated;
    }

    /**
     * 防重续号探测：查询物料表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextMaterialCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        Material last = getOne(new QueryWrapper<Material>().likeRight("material_code", prefix)
                .orderByDesc("material_code").last("LIMIT 1"));
        if (last == null || last.getMaterialCode() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getMaterialCode().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }
}
