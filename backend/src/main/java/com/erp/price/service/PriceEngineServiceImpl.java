package com.erp.price.service;

import com.erp.price.purchase.service.PurchasePriceStrategyService;
import com.erp.price.sale.service.SalePriceStrategyService;
import com.erp.tenant.TenantContextHolder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PriceEngineServiceImpl implements PriceEngineService {

    private final SalePriceStrategyService salePriceStrategyService;
    private final PurchasePriceStrategyService purchasePriceStrategyService;

    private final Cache<String, BigDecimal> salePriceCache;
    private final Cache<String, BigDecimal> purchasePriceCache;

    public PriceEngineServiceImpl(SalePriceStrategyService salePriceStrategyService,
                                  PurchasePriceStrategyService purchasePriceStrategyService) {
        this.salePriceStrategyService = salePriceStrategyService;
        this.purchasePriceStrategyService = purchasePriceStrategyService;

        this.salePriceCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        this.purchasePriceCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }

    @Override
    public BigDecimal getSalePrice(Long customerId, Long materialId, BigDecimal quantity, String taxType) {
        String key = buildSaleKey(customerId, materialId, quantity, taxType);
        return salePriceCache.get(key, k -> {
            log.debug("销售价格缓存未命中, 重新计算: customerId={}, materialId={}", customerId, materialId);
            return salePriceStrategyService.calculatePrice(customerId, materialId, quantity, taxType);
        });
    }

    @Override
    public BigDecimal getPurchasePrice(Long supplierId, Long materialId, BigDecimal quantity, String taxType) {
        String key = buildPurchaseKey(supplierId, materialId, quantity, taxType);
        return purchasePriceCache.get(key, k -> {
            log.debug("采购价格缓存未命中, 重新计算: supplierId={}, materialId={}", supplierId, materialId);
            return purchasePriceStrategyService.calculatePrice(supplierId, materialId, quantity, taxType);
        });
    }

    @Override
    public void evictSalePriceCache(Long customerId, Long materialId) {
        String prefix = TenantContextHolder.cacheKeyPrefix();
        log.info("清除销售价格缓存: tenant={}, customerId={}, materialId={}",
                TenantContextHolder.cachePrefix(), customerId, materialId);
        salePriceCache.asMap().keySet().removeIf(key ->
                key.startsWith(prefix)
                        && (key.contains("sale:" + (customerId != null ? customerId : "null") + ":")
                        || key.contains(":" + (materialId != null ? materialId : "null") + ":")));
    }

    @Override
    public void evictPurchasePriceCache(Long supplierId, Long materialId) {
        String prefix = TenantContextHolder.cacheKeyPrefix();
        log.info("清除采购价格缓存: tenant={}, supplierId={}, materialId={}",
                TenantContextHolder.cachePrefix(), supplierId, materialId);
        purchasePriceCache.asMap().keySet().removeIf(key ->
                key.startsWith(prefix)
                        && (key.contains("purchase:" + (supplierId != null ? supplierId : "null") + ":")
                        || key.contains(":" + (materialId != null ? materialId : "null") + ":")));
    }

    @Override
    public void evictAllCache() {
        String prefix = TenantContextHolder.cacheKeyPrefix();
        log.info("清除所有价格缓存: tenant={}", TenantContextHolder.cachePrefix());
        salePriceCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        purchasePriceCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSaleKey(Long customerId, Long materialId, BigDecimal quantity, String taxType) {
        // 租户前缀：各租户的客户/物料 id 都从 1 开始自增，不加前缀会取到别家租户的价
        return TenantContextHolder.cacheKeyPrefix()
                + "sale:" + (customerId != null ? customerId : "null") + ":"
                + (materialId != null ? materialId : "null") + ":"
                + (quantity != null ? quantity.toPlainString() : "null") + ":"
                + (taxType != null ? taxType : "null");
    }

    private String buildPurchaseKey(Long supplierId, Long materialId, BigDecimal quantity, String taxType) {
        return TenantContextHolder.cacheKeyPrefix()
                + "purchase:" + (supplierId != null ? supplierId : "null") + ":"
                + (materialId != null ? materialId : "null") + ":"
                + (quantity != null ? quantity.toPlainString() : "null") + ":"
                + (taxType != null ? taxType : "null");
    }
}
