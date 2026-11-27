package com.erp.price.service;

import java.math.BigDecimal;

public interface PriceEngineService {

    BigDecimal getSalePrice(Long customerId, Long materialId, BigDecimal quantity, String taxType);

    BigDecimal getPurchasePrice(Long supplierId, Long materialId, BigDecimal quantity, String taxType);

    void evictSalePriceCache(Long customerId, Long materialId);

    void evictPurchasePriceCache(Long supplierId, Long materialId);

    void evictAllCache();
}
