package com.erp.basedata.service;

import com.erp.basedata.entity.MaterialUnitConversion;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.common.bill.BillItemUnitFields;
import com.erp.config.service.SysConfigService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 多单位换算服务（2026-11-10 重构：来源改为物料换算关系表，一个物料可配多个换算单位）。
 *
 * <p>口径：
 * <ul>
 *   <li>物料档案「辅计量管理」开启后，可维护多行换算明细（辅单位 + 换算方式[固定/浮动] + 换算率）；</li>
 *   <li>换算率语义：1 个基本单位 = X 个该辅单位（如 1盒=10件 → 换算率=10），
 *       因此「辅单位 → 基本单位」的折算率 = 1 / 档案换算率；</li>
 *   <li>单据上的换算率一律按档案自动带出（<b>不信任前端传入值</b>）；</li>
 *   <li>浮动行：全局开关「启用浮动单位」（sys.float.unit.enabled）开启时，单据行允许手填浮动数量，
 *       浮动数量按<b>浮动单位自己的档案折算率</b>折算成基本单位数量；开关关闭时全站按固定处理；</li>
 *   <li>换算关系表查不到行 / 未启用辅计量：rate = 1（旧行为）；</li>
 *   <li>库存与成本只认 baseQuantity（落库前已含浮动折算），
 *       库存记账统一读 {@link BillItemUnitFields#effectiveBaseQuantity()}。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnitConversionService {

    private static final int RATE_SCALE = 8;
    private static final int QTY_SCALE = 4;

    /** 全局开关：「启用浮动单位」（已有配置键，设置中心维护） */
    public static final String FLOAT_UNIT_ENABLED_KEY = "sys.float.unit.enabled";

    private final MaterialService materialService;
    private final MaterialUnitConversionService materialUnitConversionService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final SysConfigService sysConfigService;

    /** 全局开关是否开启「启用浮动单位」；读取异常按关闭处理（全站按固定） */
    public boolean floatUnitEnabled() {
        try {
            return sysConfigService.getBooleanValue(FLOAT_UNIT_ENABLED_KEY, false);
        } catch (Exception e) {
            log.warn("[多计量单位] 读取浮动单位开关失败，按关闭处理: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 计算「所选单位 → 基本单位」的换算率；换算关系表查不到行 / 未启用辅计量时回落 1，保证旧行为不变。
     *
     * <p>换算率语义：1 个基本单位 = X 个该辅单位 → 返回值 = 1 / X（辅→基）。
     * 浮动行在没有客户端快照时同样按档案率处理（如盘点行无快照的场景）。</p>
     */
    public BigDecimal resolveRate(Material material, Long unitId) {
        if (material == null || unitId == null) return BigDecimal.ONE;
        Long baseUnitId = material.getBaseUnitId();
        if (baseUnitId == null || unitId.equals(baseUnitId)) return BigDecimal.ONE;
        if (!Boolean.TRUE.equals(material.getMultiUnitEnabled())) return BigDecimal.ONE;

        MaterialUnitConversion row = conversionRowOf(material, unitId);
        if (row == null) return BigDecimal.ONE;
        return toBaseRate(row.getConversionRate());
    }

    /** 物料 + 单位 → 档案换算行（未启用辅计量 / 无该单位行返回 null） */
    private MaterialUnitConversion conversionRowOf(Material material, Long unitId) {
        if (material == null || unitId == null || material.getMaterialId() == null) return null;
        if (!Boolean.TRUE.equals(material.getMultiUnitEnabled())) return null;
        try {
            return materialUnitConversionService.getOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MaterialUnitConversion>()
                            .eq("material_id", material.getMaterialId())
                            .eq("unit_id", unitId)
                            .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("[多计量单位] 读取物料 {} 的单位 {} 换算行失败: {}", material.getMaterialId(), unitId, e.getMessage());
            return null;
        }
    }

    /**
     * 单位名称兜底解析（部分单据明细只带单位名称、不带单位主键）：
     * 名称等于基本单位名 → 基本单位；否则按计量单位档案的名称精确匹配。
     */
    private Long resolveUnitId(Material material, String unitName) {
        if (material == null || unitName == null || unitName.isBlank()) return null;
        String name = unitName.trim();
        if (name.equals(material.getBaseUnitName())) return material.getBaseUnitId();
        try {
            UnitOfMeasure unit = unitOfMeasureService.getOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UnitOfMeasure>()
                            .eq("unit_name", name)
                            .last("LIMIT 1"));
            return unit != null ? unit.getUnitId() : null;
        } catch (Exception e) {
            log.warn("[多计量单位] 按名称解析单位失败({}): {}", name, e.getMessage());
            return null;
        }
    }

    /** 档案换算率（1基本=X辅）→ 辅→基折算率（1/X）；无效值回落 1 */
    private BigDecimal toBaseRate(BigDecimal archiveRate) {
        if (archiveRate == null || archiveRate.signum() <= 0) return BigDecimal.ONE;
        return BigDecimal.ONE.divide(archiveRate, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 规整一条单据明细：写「换算率快照 + 基本单位数量」两个隐藏字段、以及浮动换算率快照。
     *
     * <p>口径（2026-11-11 用户口径修订：浮动率由系统算，不让填单人自己算）：
     * <ol>
     *   <li>行单位是基本单位：换算率 = 1；行单位是辅单位：换算率 = 1 / 档案换算率；</li>
     *   <li>基本单位数量 = 数量 × 行单位换算率（数量由填单人填，固定/浮动同一口径）；</li>
     *   <li>同时填了浮动单位 + 浮动数量且全局开关开启：浮动换算率 = 基本单位数量 ÷ 浮动数量
     *       （1 浮动单位 = X 基本单位），落库为快照；两个数量缺一个就不算，绝不按档案瞎预填。</li>
     * </ol>
     * 未启用辅计量 / 换算行查不到：换算率 = 1，与旧版完全一致。</p>
     *
     * @param item       明细行（实体继承 {@link BillItemUnitFields}）
     * @param materialId 物料ID
     * @param quantity   本行数量（按所选单位）
     * @param unitId     本行所选单位（可为空，由 unitName 兜底解析）
     * @param unitName   本行单位名称（unitId 为空时使用）
     */
    public void normalize(BillItemUnitFields item, Long materialId, BigDecimal quantity, Long unitId, String unitName) {
        if (item == null || materialId == null) return;
        Material material = materialService.getById(materialId);
        if (material == null) return;

        Long resolvedUnitId = unitId != null ? unitId : resolveUnitId(material, unitName);
        BigDecimal rate = resolveRate(material, resolvedUnitId);
        item.setConversionRate(rate);

        if (quantity == null) return;
        // 基本单位数量 = 数量 × 行单位换算率（数量由填单人填；固定/浮动同一口径）
        BigDecimal base = quantity.multiply(rate).setScale(QTY_SCALE, RoundingMode.HALF_UP);
        item.setBaseQuantity(base);

        // 浮动换算率 = 基本单位数量 ÷ 浮动数量：系统算，不信任前端传值（2026-11-11 用户口径）
        if (item.getFloatUnitId() != null && item.getFloatQuantity() != null
                && item.getFloatQuantity().signum() > 0 && floatUnitEnabled()) {
            item.setFloatConversionRate(base.divide(item.getFloatQuantity(), RATE_SCALE, RoundingMode.HALF_UP));
        }
    }

    /** 兼容旧调用（无单位名称兜底） */
    public void normalize(BillItemUnitFields item, Long materialId, BigDecimal quantity, Long unitId) {
        normalize(item, materialId, quantity, unitId, null);
    }
}
