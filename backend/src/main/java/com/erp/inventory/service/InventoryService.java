package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.inventory.entity.Inventory;

import java.math.BigDecimal;
import java.util.List;

public interface InventoryService extends IService<Inventory> {

    /**
     * 增加库存
     */
    void addStock(String materialCode, String warehouseCode, BigDecimal quantity, String documentNo, String documentType, Long sourceId, String operator);

    /**
     * 扣减库存
     */
    void deductStock(String materialCode, String warehouseCode, BigDecimal quantity, String documentNo, String documentType, Long sourceId, String operator);

    /**
     * 查询物料库存列表
     */
    List<Inventory> getByMaterial(String materialCode);

    /**
     * 库存预警列表
     */
    List<Inventory> getWarningList();
}
