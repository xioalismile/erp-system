package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.inventory.entity.InventoryBatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface InventoryBatchService extends IService<InventoryBatch> {
    List<InventoryBatch> getByMaterial(Long materialId);
    InventoryBatch getByBatchNo(String batchNo);
    /** 按「物料+批次号」取批次（批次唯一口径为物料+批次号） */
    InventoryBatch getByBatchNoAndMaterial(Long materialId, String batchNo);
    List<InventoryBatch> getExpiredBatches();
    List<InventoryBatch> getExpiringBatches(Integer days);
    String generateBatchNo(Long materialId);
    /** FIFO 分配批次 */
    Map<String, Object> allocateByFifo(Long materialId, BigDecimal quantity);
    /** 扣减批次库存 */
    void deductBatch(Long batchId, BigDecimal quantity);
    /** 新增批次 */
    void addBatch(InventoryBatch batch);

    /**
     * 入库时按 batchNo upsert 批次库存记录：
     * - 若 batchNo 已存在则累加数量/可用数量，并按移动加权平均更新单位成本
     * - 若 batchNo 不存在则新建批次记录
     * 仅当 batchNo 非空且物料启用批次管理时才写入；其余情况直接返回。
     *
     * @param batchNo        批次号
     * @param materialId     物料ID
     * @param quantity       本次入库数量
     * @param unitCost       本次入库单位成本
     * @param supplierId     供应商ID（可空）
     * @param productionDate 生产日期（可空）
     * @param expiryDate     有效期至（可空）
     * @param manufacturingNo 制造编号（可空）
     */
    void upsertByBatchNo(String batchNo, Long materialId, BigDecimal quantity, BigDecimal unitCost,
                         Long supplierId, LocalDate productionDate, LocalDate expiryDate, String manufacturingNo);
}
