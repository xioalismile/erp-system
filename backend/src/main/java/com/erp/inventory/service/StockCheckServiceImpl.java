package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.StockCheck;
import com.erp.inventory.entity.StockCheckItem;
import com.erp.inventory.mapper.StockCheckItemMapper;
import com.erp.inventory.mapper.StockCheckMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCheckServiceImpl extends ServiceImpl<StockCheckMapper, StockCheck> implements StockCheckService {

    private final StockCheckItemMapper itemMapper;
    private final InventoryService inventoryService;

    private static final int SCALE = 2;

    @Override
    @Transactional
    public void saveCheckWithItems(StockCheck check, List<StockCheckItem> items) {
        boolean isNew = check.getCheckId() == null;

        if (check.getCheckNo() == null || check.getCheckNo().isBlank()) {
            check.setCheckNo(generateCheckNo());
        }

        if (check.getStatus() == null) check.setStatus("草稿");
        if (check.getApprovalStatus() == null) check.setApprovalStatus("待审核");
        if (check.getCheckDate() == null) check.setCheckDate(LocalDate.now());
        if (check.getStockType() == null) check.setStockType("动态盘点");
        if (check.getExchangeRate() == null) check.setExchangeRate(BigDecimal.ONE);
        if (check.getBookAmount() == null) check.setBookAmount(BigDecimal.ZERO);
        if (check.getCheckAmount() == null) check.setCheckAmount(BigDecimal.ZERO);
        if (check.getProfitLossAmount() == null) check.setProfitLossAmount(BigDecimal.ZERO);

        recalculateCheck(check, items);

        if (isNew) {
            save(check);
        } else {
            itemMapper.delete(new QueryWrapper<StockCheckItem>().eq("check_id", check.getCheckId()));
            updateById(check);
        }

        if (items != null) {
            int lineNo = 10;
            for (StockCheckItem item : items) {
                item.setCheckItemId(null);
                item.setCheckId(check.getCheckId());
                item.setLineNo(lineNo);
                calculateItemAmount(item);
                itemMapper.insert(item);
                lineNo += 10;
            }
        }

        log.info("库存盘点单保存成功: {} 状态: {} 盘点金额: {}", check.getCheckNo(), check.getStatus(), check.getCheckAmount());
    }

    /**
     * 计算明细盈亏:
     * diff = checkQuantity - bookQuantity
     * diff > 0 盘盈; diff < 0 盘亏; diff == 0 盘平
     */
    private void calculateItemAmount(StockCheckItem item) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal bookQuantity = item.getBookQuantity() != null ? item.getBookQuantity() : BigDecimal.ZERO;
        BigDecimal checkQuantity = item.getCheckQuantity() != null ? item.getCheckQuantity() : BigDecimal.ZERO;

        item.setUnitPrice(unitPrice);
        item.setBookQuantity(bookQuantity);
        item.setCheckQuantity(checkQuantity);

        BigDecimal bookAmount = bookQuantity.multiply(unitPrice).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal checkAmount = checkQuantity.multiply(unitPrice).setScale(SCALE, RoundingMode.HALF_UP);
        item.setBookAmount(bookAmount);
        item.setCheckAmount(checkAmount);

        BigDecimal diff = checkQuantity.subtract(bookQuantity);
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            item.setProfitQuantity(diff);
            item.setProfitAmount(diff.multiply(unitPrice).setScale(SCALE, RoundingMode.HALF_UP));
            item.setLossQuantity(BigDecimal.ZERO);
            item.setLossAmount(BigDecimal.ZERO);
            item.setCheckResult("盘盈");
        } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal lossQty = diff.negate();
            item.setLossQuantity(lossQty);
            item.setLossAmount(lossQty.multiply(unitPrice).setScale(SCALE, RoundingMode.HALF_UP));
            item.setProfitQuantity(BigDecimal.ZERO);
            item.setProfitAmount(BigDecimal.ZERO);
            item.setCheckResult("盘亏");
        } else {
            item.setProfitQuantity(BigDecimal.ZERO);
            item.setProfitAmount(BigDecimal.ZERO);
            item.setLossQuantity(BigDecimal.ZERO);
            item.setLossAmount(BigDecimal.ZERO);
            item.setCheckResult("盘平");
        }
    }

    private void recalculateCheck(StockCheck check, List<StockCheckItem> items) {
        BigDecimal totalBook = BigDecimal.ZERO;
        BigDecimal totalCheck = BigDecimal.ZERO;

        if (items != null) {
            for (StockCheckItem item : items) {
                calculateItemAmount(item);
                totalBook = totalBook.add(item.getBookAmount());
                totalCheck = totalCheck.add(item.getCheckAmount());
            }
        }

        check.setBookAmount(totalBook.setScale(SCALE, RoundingMode.HALF_UP));
        check.setCheckAmount(totalCheck.setScale(SCALE, RoundingMode.HALF_UP));
        check.setProfitLossAmount(
                check.getCheckAmount().subtract(check.getBookAmount()).setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<StockCheck> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<StockCheck> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("check_no", keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        Page<StockCheck> page = new Page<>(current, size);
        Page<StockCheck> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public StockCheck getDetail(Long id) {
        StockCheck check = getById(id);
        if (check == null) throw new BusinessException("库存盘点单不存在");
        List<StockCheckItem> items = itemMapper.selectList(
                new QueryWrapper<StockCheckItem>().eq("check_id", id).orderByAsc("line_no"));
        check.setItems(items);
        return check;
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        StockCheck check = getById(id);
        if (check == null) throw new BusinessException("库存盘点单不存在");
        if (!"草稿".equals(check.getStatus())) {
            throw new BusinessException("只有草稿状态可审核，当前状态: " + check.getStatus());
        }
        check.setStatus("已审核");
        check.setApprovalStatus("已审核");
        check.setApprovedBy(approver);
        check.setApprovedAt(LocalDateTime.now());
        updateById(check);

        // 盘点审核 = 盘盈入库 + 盘亏出库 (按新签名: stockId/binId/materialId/batchNo 定位)
        List<StockCheckItem> items = itemMapper.selectList(
                new QueryWrapper<StockCheckItem>().eq("check_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (StockCheckItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : check.getWarehouseId();
                Long materialId = item.getMaterialId();
                if (stockId == null || materialId == null) {
                    continue;
                }
                String result = item.getCheckResult();
                BigDecimal unitCost = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                if ("盘盈".equals(result)) {
                    BigDecimal profitQty = item.getProfitQuantity() != null ? item.getProfitQuantity() : BigDecimal.ZERO;
                    inventoryService.addStock(
                            stockId, item.getBinId(), materialId, item.getBatchNo(),
                            profitQty, unitCost,
                            check.getCheckNo(), "STOCK_CHECK_PROFIT",
                            "StockCheck", check.getCheckNo(), approver);
                } else if ("盘亏".equals(result)) {
                    BigDecimal lossQty = item.getLossQuantity() != null ? item.getLossQuantity() : BigDecimal.ZERO;
                    inventoryService.deductStock(
                            stockId, item.getBinId(), materialId, item.getBatchNo(),
                            lossQty,
                            check.getCheckNo(), "STOCK_CHECK_LOSS",
                            "StockCheck", check.getCheckNo(), approver);
                }
                // 盘平: 跳过
            }
        }

        log.info("库存盘点单审核: {} 审核人: {}", check.getCheckNo(), approver);
    }

    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        StockCheck check = getById(id);
        if (check == null) throw new BusinessException("库存盘点单不存在");
        if (!"已审核".equals(check.getStatus())) {
            throw new BusinessException("只有已审核状态可关闭，当前状态: " + check.getStatus());
        }
        check.setStatus("已关闭");
        check.setUpdatedBy(closer);
        if (reason != null && !reason.isBlank()) {
            check.setRemark((check.getRemark() != null ? check.getRemark() + " | " : "") + "关闭原因: " + reason);
        }
        updateById(check);
        log.info("库存盘点单关闭: {} 关闭人: {} 原因: {}", check.getCheckNo(), closer, reason);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        StockCheck check = getById(id);
        if (check == null) throw new BusinessException("库存盘点单不存在");
        if (!"草稿".equals(check.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + check.getStatus());
        }
        check.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            check.setRemark((check.getRemark() != null ? check.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(check);
        log.info("库存盘点单取消: {} 原因: {}", check.getCheckNo(), reason);
    }

    @Override
    public String generateCheckNo() {
        String prefix = "SC" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<StockCheck> wrapper = new QueryWrapper<>();
        wrapper.likeRight("check_no", prefix).orderByDesc("check_no").last("LIMIT 1");
        StockCheck last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getCheckNo() != null) {
            try {
                seq = Integer.parseInt(last.getCheckNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
