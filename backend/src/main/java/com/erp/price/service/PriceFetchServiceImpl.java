package com.erp.price.service;

import com.erp.basedata.entity.PriceLevel;
import com.erp.basedata.service.PriceLevelService;
import com.erp.config.service.SysConfigService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.price.purchase.service.PurchasePriceStrategyService;
import com.erp.price.sale.service.SalePriceStrategyService;
import com.erp.price.vo.PriceFetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 单据取价服务实现（2026-09-20 价格策略修复）。
 * <p>
 * 修复前状态：价格策略算价能力早已具备，但「取价优先级」配置零引用、价格引擎零调用、
 * 单据单价一律取物料档案价 → 价格策略"录了不生效"。本类把三者接上：
 * 读取设置中心的取价优先级 → 按顺序逐档尝试 → 命中即返回，并把命中的档位一并告知前端。
 * </p>
 * <p>
 * 设计约束：
 * 1) 不抛异常：任何一档取不到都只是"跳过"，最终回落到物料档案价；物料不存在返回 none，不阻断录入；
 * 2) 保留旧行为兜底：优先级字符串里若没有 material 档，仍回落物料档案价，避免单价被清成 0；
 * 3) 采购侧跳过 level 档（采购无价格等级档案）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceFetchServiceImpl implements PriceFetchService {

    private static final String SOURCE_STRATEGY = "strategy";
    private static final String SOURCE_LEVEL = "level";
    private static final String SOURCE_MATERIAL = "material";
    private static final String SOURCE_NONE = "none";

    /** 价格计算保留 4 位（与单据单价小数位口径一致） */
    private static final int PRICE_SCALE = 4;

    private final SysConfigService sysConfigService;
    private final SalePriceStrategyService salePriceStrategyService;
    private final PurchasePriceStrategyService purchasePriceStrategyService;
    private final MaterialService materialService;
    private final CustomerService customerService;
    private final PriceLevelService priceLevelService;

    @Override
    public PriceFetchResult fetch(String side, Long partnerId, Long materialId, BigDecimal quantity, String taxType) {
        PriceFetchResult result = new PriceFetchResult();
        result.setMaterialId(materialId);
        result.setQuantity(quantity);

        boolean sale = !"purchase".equalsIgnoreCase(side == null ? "" : side.trim());
        if (materialId == null) {
            return none(result);
        }
        Material material = materialService.getById(materialId);
        if (material == null) {
            return none(result);
        }

        String priorityKey = sale ? "sale.price.fetch.priority" : "purchase.price.fetch.priority";
        String defaultPriority = sale ? "strategy,level,material" : "strategy,material";
        String priority = sysConfigService.getValue(priorityKey, defaultPriority);
        // 开关读错 Key 的历史缺陷已在本轮修复（详见 KgwBusinessRules）；此处与设置中心界面同一开关
        String enabledKey = sale ? "sale.price.strategy.enabled" : "purchase.price.strategy.enabled";
        boolean strategyEnabled = sysConfigService.getBooleanValue(enabledKey, true);

        String[] tiers = (priority == null || priority.isBlank()) ? defaultPriority.split(",") : priority.split(",");
        for (String raw : tiers) {
            String tier = raw == null ? "" : raw.trim().toLowerCase();
            if (tier.isEmpty()) {
                continue;
            }
            if (SOURCE_STRATEGY.equals(tier)) {
                if (!strategyEnabled) {
                    continue;
                }
                BigDecimal strategyPrice = sale
                        ? salePriceStrategyService.tryMatchPrice(partnerId, materialId, quantity, taxType)
                        : purchasePriceStrategyService.tryMatchPrice(partnerId, materialId, quantity, taxType);
                if (strategyPrice != null) {
                    return hit(result, strategyPrice, SOURCE_STRATEGY);
                }
            } else if (SOURCE_LEVEL.equals(tier)) {
                if (!sale) {
                    // 采购侧无价格等级档案，该档不适用
                    continue;
                }
                BigDecimal levelPrice = levelPrice(partnerId, material);
                if (levelPrice != null) {
                    return hit(result, levelPrice, SOURCE_LEVEL);
                }
            } else if (SOURCE_MATERIAL.equals(tier)) {
                BigDecimal basePrice = sale ? material.getSalePrice() : material.getPurchasePrice();
                if (basePrice != null) {
                    return hit(result, basePrice, SOURCE_MATERIAL);
                }
            }
        }

        // 三档都未命中：仍回落物料档案价（保持"永远有默认价"的旧行为，绝不把单价清成 0）
        BigDecimal fallback = sale ? material.getSalePrice() : material.getPurchasePrice();
        if (fallback == null) {
            return none(result);
        }
        return hit(result, fallback, SOURCE_MATERIAL);
    }

    /**
     * 价格等级档：客户档案挂的「价格等级」→ 单价 = 物料默认售价 ×（1 + 调整率/100）。
     * 与前端既有"价格等级切换"口径一致（前端是本地算，本次为服务端同口径）。
     */
    private BigDecimal levelPrice(Long customerId, Material material) {
        if (customerId == null || material.getSalePrice() == null) {
            return null;
        }
        Customer customer = customerService.getById(customerId);
        if (customer == null || customer.getDefaultPriceLevelId() == null) {
            return null;
        }
        PriceLevel level = priceLevelService.getById(customer.getDefaultPriceLevelId());
        if (level == null || level.getPriceAdjustRate() == null) {
            return null;
        }
        BigDecimal rate = level.getPriceAdjustRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return material.getSalePrice().multiply(BigDecimal.ONE.add(rate));
    }

    private PriceFetchResult hit(PriceFetchResult result, BigDecimal price, String source) {
        result.setPrice(price.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        result.setSource(source);
        result.setSourceLabel(sourceLabel(source));
        return result;
    }

    private PriceFetchResult none(PriceFetchResult result) {
        result.setPrice(null);
        result.setSource(SOURCE_NONE);
        result.setSourceLabel(sourceLabel(SOURCE_NONE));
        return result;
    }

    private String sourceLabel(String source) {
        return switch (source) {
            case SOURCE_STRATEGY -> "价格策略";
            case SOURCE_LEVEL -> "价格等级";
            case SOURCE_MATERIAL -> "物料默认价";
            default -> "未取到";
        };
    }
}
