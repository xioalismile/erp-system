package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.finance.entity.CostAccountingConfig;
import com.erp.finance.service.CostAccountingConfigService;
import com.erp.inventory.dto.MaterialStockSummaryDTO;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.entity.InventoryBatch;
import com.erp.inventory.entity.InventoryTransaction;
import com.erp.inventory.mapper.InventoryMapper;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory> implements InventoryService {

    /** 计价方法常量 */
    private static final String METHOD_MOVING_AVERAGE = "MOVING_AVERAGE";
    private static final String METHOD_FIFO = "FIFO";
    private static final String METHOD_LIFO = "LIFO";
    private static final String METHOD_MONTHLY_AVERAGE = "MONTHLY_AVERAGE";
    private static final String METHOD_SPECIFIC = "SPECIFIC";

    private final MaterialService materialService;
    private final InventoryTransactionService transactionService;
    private final CostAccountingConfigService costAccountingConfigService;
    private final InventoryBatchService inventoryBatchService;

    @Autowired
    private SysConfigService sysConfigService;

    @Override
    @Transactional
    public void addStock(Long stockId, Long binId, Long materialId, String batchNo,
                         BigDecimal quantity, BigDecimal unitCost,
                         String transNo, String transType,
                         String sourceBillType, String sourceBillNo, String operator) {
        // 【2026-09-14 修复】行锁：读-改-写必须串行化，否则并发入库会互相覆盖（静默丢库存）
        Inventory inv = getOne(buildLocateWrapper(stockId, binId, materialId, batchNo).last("LIMIT 1 FOR UPDATE"));

        BigDecimal safeUnitCost = unitCost != null ? unitCost : BigDecimal.ZERO;
        BigDecimal inAmount = quantity.multiply(safeUnitCost);
        String costingMethod = getCostingMethod();

        if (inv == null) {
            inv = new Inventory();
            inv.setStockId(stockId);
            inv.setBinId(binId);
            inv.setMaterialId(materialId);
            inv.setBatchNo(batchNo);
            inv.setQuantity(quantity);
            inv.setAvailableQuantity(quantity);
            inv.setFrozenQuantity(BigDecimal.ZERO);
            // 新建库存记录: 各计价方法均以本次入库单价作为初始单位成本
            BigDecimal newUnitCost = quantity.compareTo(BigDecimal.ZERO) != 0
                    ? inAmount.divide(quantity, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            inv.setUnitCost(newUnitCost);
            inv.setTotalCost(inAmount);
            inv.setLastInDate(LocalDate.now());
            save(inv);
        } else {
            BigDecimal oldTotalCost = inv.getTotalCost() != null ? inv.getTotalCost() : BigDecimal.ZERO;
            BigDecimal newQuantity = inv.getQuantity().add(quantity);
            BigDecimal newUnitCost = calculateAddUnitCost(costingMethod, inv, oldTotalCost, newQuantity, quantity, safeUnitCost);
            BigDecimal newTotalCost = newQuantity.multiply(newUnitCost);
            inv.setQuantity(newQuantity);
            inv.setAvailableQuantity(inv.getAvailableQuantity().add(quantity));
            inv.setUnitCost(newUnitCost);
            inv.setTotalCost(newTotalCost);
            inv.setLastInDate(LocalDate.now());
            updateById(inv);
        }

        // 批次库存联动写入: 仅当 batchNo 非空时（物料开启批次管理或入库单填写了批次号）
        // 由入库单据审核自动生成批次库存记录，前端"批次库存"页面不再手动新增
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            try {
                // 批次补充字段（供应商/生产日期/有效期/制造编号）由入库明细提供时，
                // 可通过扩展 addStock 签名传入；当前默认为空，由后续入库单据补全
                inventoryBatchService.upsertByBatchNo(
                        batchNo, materialId, quantity, safeUnitCost,
                        null, null, null, null);
            } catch (Exception e) {
                log.warn("批次库存写入失败 batchNo={} 物料={}: {}", batchNo, materialId, e.getMessage());
            }
        }

        // 记录库存台账(入库: in_quantity=quantity, in_amount=quantity*unitCost)
        transactionService.recordTransaction(
                transNo, transType,
                stockId, binId, materialId, batchNo,
                quantity, inAmount,
                null, null,
                safeUnitCost,
                sourceBillType, sourceBillNo,
                operator, null);

        log.info("库存增加: 物料[{}] 仓库[{}] 数量{} 单据{}", materialId, stockId, quantity, transNo);
    }

    @Override
    @Transactional
    public void deductStock(Long stockId, Long binId, Long materialId, String batchNo,
                            BigDecimal quantity,
                            String transNo, String transType,
                            String sourceBillType, String sourceBillNo, String operator) {
        // 【2026-09-14 修复】行锁：原实现"读→校验→整行覆盖写"无锁无版本号，
        //   并发审核两张出库/领料单时两边都会读到同一结存并各自通过校验 → 库存被超发（可致负库存）。
        //   加 FOR UPDATE 后第二个事务等第一个提交后再读，读到新结存即正确报"库存不足"。
        Inventory inv = getOne(buildLocateWrapper(stockId, binId, materialId, batchNo).last("LIMIT 1 FOR UPDATE"));

        // 读取设置中心配置：是否允许负库存
        boolean negativeAllowed = sysConfigService.getBooleanValue("inventory.negative.allowed", false);

        if (inv == null) {
            if (!negativeAllowed) {
                throw new BusinessException("物料[" + materialId + "]在仓库[" + stockId + "]库存不足");
            }
            // 允许负库存时，创建一条新的库存记录（数量为负）
            // 【2026-09-11 材料成本归集修复】原实现把成本写死为 0，导致"无库存记录 + 允许负库存"
            //   场景下出库成本恒为 0 → 材料成本/入库成本/毛利全部失真。改为取物料采购价兜底。
            BigDecimal fallbackCost = resolveFallbackUnitCost(materialId);
            inv = new Inventory();
            inv.setStockId(stockId);
            inv.setBinId(binId);
            inv.setMaterialId(materialId);
            inv.setBatchNo(batchNo);
            inv.setQuantity(BigDecimal.ZERO.subtract(quantity));
            inv.setAvailableQuantity(BigDecimal.ZERO.subtract(quantity));
            inv.setFrozenQuantity(BigDecimal.ZERO);
            inv.setUnitCost(fallbackCost);
            inv.setTotalCost(BigDecimal.ZERO.subtract(quantity.multiply(fallbackCost)));
            inv.setLastOutDate(LocalDate.now());
            save(inv);
            // 记录台账后直接返回（不需要继续执行后面的扣减逻辑）
            transactionService.recordTransaction(
                    transNo, transType,
                    stockId, binId, materialId, batchNo,
                    null, null,
                    quantity, quantity.multiply(fallbackCost),
                    fallbackCost,
                    sourceBillType, sourceBillNo,
                    operator, null);
            log.info("库存扣减(允许负库存): 物料[{}] 仓库[{}] 数量{} 单价{} 单据{}", materialId, stockId, quantity, fallbackCost, transNo);
            return;
        }

        if (inv.getQuantity() == null || inv.getQuantity().compareTo(quantity) < 0) {
            if (!negativeAllowed) {
                throw new BusinessException("物料[" + materialId + "]在仓库[" + stockId + "]库存不足，当前库存: "
                    + (inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO)
                    + "，需要数量: " + quantity);
            }
            // 允许负库存时，记录日志但不阻止
            log.warn("物料[{}]在仓库[{}]库存不足，已允许负库存出库。当前库存: {}，出库数量: {}",
                materialId, stockId, inv.getQuantity(), quantity);
        }

        String costingMethod = getCostingMethod();
        BigDecimal unitCost = calculateOutUnitCost(costingMethod, inv, stockId, materialId, batchNo);
        BigDecimal outAmount = quantity.multiply(unitCost);
        BigDecimal oldQty = inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO;
        BigDecimal oldTotal = inv.getTotalCost() != null ? inv.getTotalCost() : oldQty.multiply(unitCost);

        inv.setQuantity(inv.getQuantity().subtract(quantity));
        inv.setAvailableQuantity(inv.getAvailableQuantity().subtract(quantity));
        // 成本链路（2026-11-26）：出库必须同步冲减库存总成本，否则移动加权平均会被抬高。
        BigDecimal newTotal = oldTotal.subtract(outAmount).max(BigDecimal.ZERO);
        inv.setTotalCost(newTotal);
        if (inv.getQuantity().signum() <= 0) {
            inv.setUnitCost(BigDecimal.ZERO);
        }
        inv.setLastOutDate(LocalDate.now());
        updateById(inv);

        // 批次库存联动扣减: 仅当 batchNo 非空时
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            try {
                InventoryBatch batch = inventoryBatchService.getByBatchNoAndMaterial(materialId, batchNo.trim());
                if (batch != null) {
                    BigDecimal newQty = (batch.getQuantity() != null ? batch.getQuantity() : BigDecimal.ZERO).subtract(quantity);
                    BigDecimal newAvail = (batch.getAvailableQuantity() != null ? batch.getAvailableQuantity() : BigDecimal.ZERO).subtract(quantity);
                    batch.setQuantity(newQty.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newQty);
                    batch.setAvailableQuantity(newAvail.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newAvail);
                    // 数量为 0 时标记为已用完
                    if (batch.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                        batch.setStatus("已用完");
                    }
                    inventoryBatchService.updateById(batch);
                }
            } catch (Exception e) {
                log.warn("批次库存扣减失败 batchNo={} 物料={}: {}", batchNo, materialId, e.getMessage());
            }
        }

        // 记录库存台账(出库: out_quantity=quantity, out_amount=quantity*unitCost)
        transactionService.recordTransaction(
                transNo, transType,
                stockId, binId, materialId, batchNo,
                null, null,
                quantity, outAmount,
                unitCost,
                sourceBillType, sourceBillNo,
                operator, null);

        log.info("库存扣减: 物料[{}] 仓库[{}] 数量{} 单据{}", materialId, stockId, quantity, transNo);
    }

    @Override
    @Transactional
    public void deductStockAtCost(Long stockId, Long binId, Long materialId, String batchNo,
                                  BigDecimal quantity, BigDecimal unitCost,
                                  String transNo, String transType,
                                  String sourceBillType, String sourceBillNo, String operator) {
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ZERO;
        BigDecimal cost = unitCost != null ? unitCost : BigDecimal.ZERO;
        Inventory inv = getOne(buildLocateWrapper(stockId, binId, materialId, batchNo).last("LIMIT 1 FOR UPDATE"));
        boolean negativeAllowed = sysConfigService.getBooleanValue("inventory.negative.allowed", false);

        if (inv == null) {
            if (!negativeAllowed) {
                throw new BusinessException("物料[" + materialId + "]在仓库[" + stockId + "]库存不足");
            }
            inv = new Inventory();
            inv.setStockId(stockId);
            inv.setBinId(binId);
            inv.setMaterialId(materialId);
            inv.setBatchNo(batchNo);
            inv.setQuantity(qty.negate());
            inv.setAvailableQuantity(qty.negate());
            inv.setFrozenQuantity(BigDecimal.ZERO);
            inv.setUnitCost(cost);
            inv.setTotalCost(qty.multiply(cost).negate());
            inv.setLastOutDate(LocalDate.now());
            save(inv);
            transactionService.recordTransaction(transNo, transType, stockId, binId, materialId, batchNo,
                    null, null, qty, qty.multiply(cost), cost,
                    sourceBillType, sourceBillNo, operator, null);
            return;
        }

        BigDecimal oldQty = inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO;
        if (oldQty.compareTo(qty) < 0 && !negativeAllowed) {
            throw new BusinessException("物料[" + materialId + "]在仓库[" + stockId + "]库存不足，当前库存: "
                    + oldQty + "，需要冲回数量: " + qty);
        }
        BigDecimal oldTotal = inv.getTotalCost() != null
                ? inv.getTotalCost()
                : oldQty.multiply(inv.getUnitCost() != null ? inv.getUnitCost() : cost);
        BigDecimal newQty = oldQty.subtract(qty);
        BigDecimal newTotal = oldTotal.subtract(qty.multiply(cost));
        if (newTotal.signum() < 0) {
            log.warn("[库存反冲] 冲回后总成本为负，已归零: 物料={} 仓库={} 原总成本={} 冲回金额={}",
                    materialId, stockId, oldTotal, qty.multiply(cost));
            newTotal = BigDecimal.ZERO;
        }
        inv.setQuantity(newQty);
        inv.setAvailableQuantity((inv.getAvailableQuantity() != null ? inv.getAvailableQuantity() : BigDecimal.ZERO).subtract(qty));
        inv.setTotalCost(newTotal);
        inv.setUnitCost(newQty.signum() > 0
                ? newTotal.divide(newQty, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        inv.setLastOutDate(LocalDate.now());
        updateById(inv);

        if (batchNo != null && !batchNo.trim().isEmpty()) {
            try {
                InventoryBatch batch = inventoryBatchService.getByBatchNoAndMaterial(materialId, batchNo.trim());
                if (batch != null) {
                    BigDecimal newQtyB = (batch.getQuantity() != null ? batch.getQuantity() : BigDecimal.ZERO).subtract(qty);
                    BigDecimal newAvailB = (batch.getAvailableQuantity() != null ? batch.getAvailableQuantity() : BigDecimal.ZERO).subtract(qty);
                    batch.setQuantity(newQtyB.max(BigDecimal.ZERO));
                    batch.setAvailableQuantity(newAvailB.max(BigDecimal.ZERO));
                    if (batch.getQuantity().signum() == 0) {
                        batch.setStatus("已用完");
                    }
                    inventoryBatchService.updateById(batch);
                }
            } catch (Exception e) {
                log.warn("批次库存冲回失败 batchNo={} 物料={}: {}", batchNo, materialId, e.getMessage());
            }
        }

        transactionService.recordTransaction(transNo, transType, stockId, binId, materialId, batchNo,
                null, null, qty, qty.multiply(cost), cost,
                sourceBillType, sourceBillNo, operator, null);
        log.info("库存按原成本冲回: 物料[{}] 仓库[{}] 数量{} 单价{} 单据{}", materialId, stockId, qty, cost, transNo);
    }

    @Override
    public List<Inventory> getByMaterialAndStock(Long materialId, Long stockId) {
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.eq("material_id", materialId);
        wrapper.eq("stock_id", stockId);
        return list(wrapper);
    }

    /**
     * 【2026-09-11 材料成本归集】预取本次出库的单位成本（与 deductStock 内部口径完全一致）。
     * 无库存记录时按物料采购价兜底（与 deductStock 负库存分支保持一致）。
     */
    @Override
    public BigDecimal getOutUnitCost(Long stockId, Long binId, Long materialId, String batchNo) {
        if (stockId == null || materialId == null) {
            return BigDecimal.ZERO;
        }
        Inventory inv = getOne(buildLocateWrapper(stockId, binId, materialId, batchNo).last("LIMIT 1"));
        if (inv == null) {
            return resolveFallbackUnitCost(materialId);
        }
        return calculateOutUnitCost(getCostingMethod(), inv, stockId, materialId, batchNo);
    }

    /**
     * 【2026-09-11 材料成本归集】无库存记录时的成本兜底：取物料采购价；取不到返回 0。
     */
    private BigDecimal resolveFallbackUnitCost(Long materialId) {
        try {
            com.erp.masterdata.entity.Material m = materialService.getById(materialId);
            if (m != null && m.getPurchasePrice() != null) {
                return m.getPurchasePrice();
            }
        } catch (Exception e) {
            log.warn("取物料采购价失败 materialId={}: {}", materialId, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getTotalAvailableQuantity(Long materialId) {
        if (materialId == null) {
            return BigDecimal.ZERO;
        }
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(COALESCE(available_quantity,0)),0) AS total_available")
                .eq("material_id", materialId);
        List<Map<String, Object>> rows = listMaps(wrapper);
        if (rows == null || rows.isEmpty() || rows.get(0).get("total_available") == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(rows.get(0).get("total_available").toString());
    }

    @Override
    public Map<Long, MaterialStockSummaryDTO> batchGetStockSummary(Collection<Long> materialIds,
                                                                  List<Long> visibleStockIds) {
        Map<Long, MaterialStockSummaryDTO> result = new HashMap<>();
        if (materialIds == null || materialIds.isEmpty()) {
            return result;
        }
        // 无任何可见仓库：直接返回空（与 /inventory/list 同口径，不越权）
        if (visibleStockIds != null && visibleStockIds.isEmpty()) {
            return result;
        }
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.select("material_id",
                        "COALESCE(SUM(COALESCE(quantity,0)),0) AS total_quantity",
                        "COALESCE(SUM(COALESCE(available_quantity,0)),0) AS total_available")
                .in("material_id", materialIds)
                .groupBy("material_id");
        if (visibleStockIds != null) {
            wrapper.in("stock_id", visibleStockIds);
        }
        for (Map<String, Object> row : listMaps(wrapper)) {
            Object mid = row.get("material_id");
            if (mid == null) {
                continue;
            }
            MaterialStockSummaryDTO dto = new MaterialStockSummaryDTO();
            dto.setMaterialId(Long.valueOf(mid.toString()));
            dto.setQuantity(toBigDecimalOrZero(row.get("total_quantity")));
            dto.setAvailableQuantity(toBigDecimalOrZero(row.get("total_available")));
            result.put(dto.getMaterialId(), dto);
        }
        return result;
    }

    private BigDecimal toBigDecimalOrZero(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }

    @Override
    public List<Inventory> getWarningList() {
        // 读取设置中心开关
        boolean lowStockWarn = sysConfigService.getBooleanValue("inventory.low.stock.warn", true);
        boolean overstockWarn = sysConfigService.getBooleanValue("inventory.overstock.warn", true);

        if (!lowStockWarn && !overstockWarn) {
            log.info("【设置中心】库存预警和积压预警均已关闭，跳过预警检查");
            return new ArrayList<>();
        }

        List<Inventory> all = list();
        List<Inventory> warningList = new ArrayList<>();
        for (Inventory inv : all) {
            if (inv.getMaterialId() == null) {
                continue;
            }
            Material material = materialService.getById(inv.getMaterialId());
            if (material == null) {
                continue;
            }
            BigDecimal qty = inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO;

            // 库存不足预警
            if (lowStockWarn && material.getMinStock() != null && qty.compareTo(material.getMinStock()) < 0) {
                warningList.add(inv);
                continue;
            }

            // 库存积压预警
            if (overstockWarn && material.getMaxStock() != null && qty.compareTo(material.getMaxStock()) > 0) {
                warningList.add(inv);
            }
        }
        return warningList;
    }

    /**
     * 按 (stock_id, bin_id, material_id, batch_no) 唯一索引构建定位查询
     * 注意: bin_id / batch_no 为 null 时使用 IS NULL, 否则 NULL 值无法匹配唯一索引
     */
    private QueryWrapper<Inventory> buildLocateWrapper(Long stockId, Long binId, Long materialId, String batchNo) {
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.eq("stock_id", stockId);
        if (binId != null) {
            wrapper.eq("bin_id", binId);
        } else {
            wrapper.isNull("bin_id");
        }
        wrapper.eq("material_id", materialId);
        // batchNo 为 null 或空字符串时同时匹配 NULL 和空字符串, 避免入库写空字符串与出库传 NULL 不匹配
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            wrapper.eq("batch_no", batchNo);
        } else {
            wrapper.and(w -> w.isNull("batch_no").or().eq("batch_no", ""));
        }
        return wrapper;
    }

    /**
     * 读取当前启用的成本计价方法。
     * 从 cost_accounting_config 表的 default_method 字段读取并归一化为标准编码:
     * - moving_avg   -> MOVING_AVERAGE (移动加权平均)
     * - weighted_avg -> MONTHLY_AVERAGE (月末/全月一次加权平均)
     * - fixed        -> SPECIFIC (个别计价/固定成本法)
     * - FIFO / LIFO  -> 原样返回
     * 任何异常或空值时默认返回 MOVING_AVERAGE, 保持原系统行为。
     */
    private String getCostingMethod() {
        try {
            CostAccountingConfig config = costAccountingConfigService.getActiveConfig();
            if (config == null || config.getDefaultMethod() == null || config.getDefaultMethod().isBlank()) {
                return METHOD_MOVING_AVERAGE;
            }
            String raw = config.getDefaultMethod().trim();
            switch (raw) {
                case "moving_avg":
                case "MOVING_AVERAGE":
                    return METHOD_MOVING_AVERAGE;
                case "weighted_avg":
                case "MONTHLY_AVERAGE":
                    return METHOD_MONTHLY_AVERAGE;
                case "fixed":
                case "SPECIFIC":
                    return METHOD_SPECIFIC;
                case "FIFO":
                    return METHOD_FIFO;
                case "LIFO":
                    return METHOD_LIFO;
                default:
                    return METHOD_MOVING_AVERAGE;
            }
        } catch (Exception e) {
            log.warn("读取成本计价方法配置失败，使用默认移动加权平均: {}", e.getMessage());
            return METHOD_MOVING_AVERAGE;
        }
    }

    /**
     * 根据计价方法计算入库后的新单位成本。
     * - MOVING_AVERAGE / MONTHLY_AVERAGE: (原总成本 + 本次入库金额) / 新数量
     * - FIFO / LIFO / SPECIFIC: 各批次独立保留入库单价，不做加权平均
     */
    private BigDecimal calculateAddUnitCost(String method, Inventory inv, BigDecimal oldTotalCost,
                                            BigDecimal newQuantity, BigDecimal inQuantity, BigDecimal inUnitCost) {
        switch (method) {
            case METHOD_FIFO:
            case METHOD_LIFO:
            case METHOD_SPECIFIC:
                // 批次计价: 保留当前批次单价，未设置时使用本次入库单价
                return inv.getUnitCost() != null ? inv.getUnitCost() : inUnitCost;
            case METHOD_MOVING_AVERAGE:
            case METHOD_MONTHLY_AVERAGE:
            default:
                // 月末加权平均此处动态近似为移动加权平均(月末统一差异调整在成本核算模块处理)
                return newQuantity.compareTo(BigDecimal.ZERO) != 0
                        ? oldTotalCost.add(inQuantity.multiply(inUnitCost)).divide(newQuantity, 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
        }
    }

    /**
     * 根据计价方法计算出库单位成本。
     * - MOVING_AVERAGE / SPECIFIC: 直接使用当前库存记录单价
     * - FIFO: 取最早入库批次单价
     * - LIFO: 取最晚入库批次单价
     * - MONTHLY_AVERAGE: 按当月入库流水汇总计算平均单价
     */
    private BigDecimal calculateOutUnitCost(String method, Inventory inv, Long stockId,
                                            Long materialId, String batchNo) {
        BigDecimal invUnitCost = inv.getUnitCost() != null ? inv.getUnitCost() : BigDecimal.ZERO;
        switch (method) {
            case METHOD_FIFO:
                return findBatchUnitCost(stockId, materialId, true);
            case METHOD_LIFO:
                return findBatchUnitCost(stockId, materialId, false);
            case METHOD_MONTHLY_AVERAGE:
                return calculateMonthlyAverageCost(stockId, materialId, invUnitCost);
            case METHOD_MOVING_AVERAGE:
            case METHOD_SPECIFIC:
            default:
                return invUnitCost;
        }
    }

    /**
     * 查询物料在某仓库下最早/最晚入库批次的单价 (FIFO/LIFO 基本实现)。
     * @param earliest true=最早批次(FIFO, last_in_date ASC), false=最晚批次(LIFO, last_in_date DESC)
     */
    private BigDecimal findBatchUnitCost(Long stockId, Long materialId, boolean earliest) {
        try {
            QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
            wrapper.eq("stock_id", stockId)
                    .eq("material_id", materialId)
                    .gt("quantity", BigDecimal.ZERO)
                    .isNotNull("last_in_date")
                    .orderBy(true, earliest, "last_in_date")
                    .last("LIMIT 1");
            Inventory batch = getOne(wrapper);
            if (batch != null && batch.getUnitCost() != null) {
                return batch.getUnitCost();
            }
        } catch (Exception e) {
            log.warn("查询批次单价失败 method={} material={} stock={}: {}",
                    earliest ? METHOD_FIFO : METHOD_LIFO, materialId, stockId, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    /**
     * 按当月汇总计算平均单价 (MONTHLY_AVERAGE 基本实现)。
     * 取本月入库流水的 in_amount 合计 / in_quantity 合计; 无数据时回退到当前库存单价。
     */
    private BigDecimal calculateMonthlyAverageCost(Long stockId, Long materialId, BigDecimal fallback) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);
            LocalDate nextMonthStart = YearMonth.from(today).plusMonths(1).atDay(1);

            QueryWrapper<InventoryTransaction> wrapper = new QueryWrapper<>();
            wrapper.eq("stock_id", stockId)
                    .eq("material_id", materialId)
                    .ge("trans_date", monthStart)
                    .lt("trans_date", nextMonthStart)
                    .gt("in_quantity", BigDecimal.ZERO);
            List<InventoryTransaction> txList = transactionService.list(wrapper);
            BigDecimal qtySum = BigDecimal.ZERO;
            BigDecimal amtSum = BigDecimal.ZERO;
            for (InventoryTransaction tx : txList) {
                if (tx.getInQuantity() != null) {
                    qtySum = qtySum.add(tx.getInQuantity());
                }
                if (tx.getInAmount() != null) {
                    amtSum = amtSum.add(tx.getInAmount());
                }
            }
            if (qtySum.compareTo(BigDecimal.ZERO) != 0) {
                return amtSum.divide(qtySum, 4, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.warn("计算月末加权平均单价失败 material={} stock={}: {}", materialId, stockId, e.getMessage());
        }
        return fallback != null ? fallback : BigDecimal.ZERO;
    }
}
