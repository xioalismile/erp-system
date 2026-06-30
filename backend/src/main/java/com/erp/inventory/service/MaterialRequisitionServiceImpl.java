package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.inventory.entity.MaterialRequisitionItem;
import com.erp.inventory.mapper.MaterialRequisitionItemMapper;
import com.erp.inventory.mapper.MaterialRequisitionMapper;
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
public class MaterialRequisitionServiceImpl extends ServiceImpl<MaterialRequisitionMapper, MaterialRequisition> implements MaterialRequisitionService {

    private final MaterialRequisitionItemMapper itemMapper;
    private final InventoryService inventoryService;

    private static final int SCALE = 2;

    @Override
    @Transactional
    public void saveRequisitionWithItems(MaterialRequisition requisition, List<MaterialRequisitionItem> items) {
        boolean isNew = requisition.getRequisitionId() == null;

        if (requisition.getRequisitionNo() == null || requisition.getRequisitionNo().isBlank()) {
            requisition.setRequisitionNo(generateRequisitionNo());
        }

        if (requisition.getStatus() == null) requisition.setStatus("草稿");
        if (requisition.getApprovalStatus() == null) requisition.setApprovalStatus("待审核");
        if (requisition.getExchangeRate() == null) requisition.setExchangeRate(BigDecimal.ONE);
        if (requisition.getTotalAmount() == null) requisition.setTotalAmount(BigDecimal.ZERO);
        if (requisition.getRequisitionDate() == null) requisition.setRequisitionDate(LocalDate.now());

        recalculateRequisition(requisition, items);

        if (isNew) {
            save(requisition);
        } else {
            itemMapper.delete(new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", requisition.getRequisitionId()));
            updateById(requisition);
        }

        if (items != null) {
            int lineNo = 10;
            for (MaterialRequisitionItem item : items) {
                item.setRequisitionItemId(null);
                item.setRequisitionId(requisition.getRequisitionId());
                item.setLineNo(lineNo);
                calculateItemAmount(item);
                itemMapper.insert(item);
                lineNo += 10;
            }
        }

        log.info("生产领料单保存成功: {} 状态: {} 合计: {}", requisition.getRequisitionNo(), requisition.getStatus(), requisition.getTotalAmount());
    }

    private void calculateItemAmount(MaterialRequisitionItem item) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        BigDecimal amount = unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
    }

    private void recalculateRequisition(MaterialRequisition requisition, List<MaterialRequisitionItem> items) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (MaterialRequisitionItem item : items) {
                calculateItemAmount(item);
                totalAmount = totalAmount.add(item.getAmount());
            }
        }
        requisition.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<MaterialRequisition> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<MaterialRequisition> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("requisition_no", keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        Page<MaterialRequisition> page = new Page<>(current, size);
        Page<MaterialRequisition> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public MaterialRequisition getDetail(Long id) {
        MaterialRequisition requisition = getById(id);
        if (requisition == null) throw new BusinessException("生产领料单不存在");
        List<MaterialRequisitionItem> items = itemMapper.selectList(
                new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", id).orderByAsc("line_no"));
        requisition.setItems(items);
        return requisition;
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        MaterialRequisition requisition = getById(id);
        if (requisition == null) throw new BusinessException("生产领料单不存在");
        if (!"草稿".equals(requisition.getStatus())) {
            throw new BusinessException("只有草稿状态可审核，当前状态: " + requisition.getStatus());
        }
        requisition.setStatus("已审核");
        requisition.setApprovalStatus("已审核");
        requisition.setApprovedBy(approver);
        requisition.setApprovedAt(LocalDateTime.now());
        updateById(requisition);

        // 审核通过后扣减库存 (领料 = 出库, 库存不足抛 BusinessException 触发回滚)
        List<MaterialRequisitionItem> items = itemMapper.selectList(
                new QueryWrapper<MaterialRequisitionItem>().eq("requisition_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (MaterialRequisitionItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : requisition.getWarehouseId();
                Long materialId = item.getMaterialId();
                BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                if (stockId == null || materialId == null) {
                    continue;
                }
                inventoryService.deductStock(
                        stockId,
                        item.getBinId(),
                        materialId,
                        item.getBatchNo(),
                        quantity,
                        requisition.getRequisitionNo(),
                        "MATERIAL_REQUISITION",
                        "MaterialRequisition",
                        requisition.getRequisitionNo(),
                        approver);
            }
        }

        log.info("生产领料单审核: {} 审核人: {}", requisition.getRequisitionNo(), approver);
    }

    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        MaterialRequisition requisition = getById(id);
        if (requisition == null) throw new BusinessException("生产领料单不存在");
        if (!"已审核".equals(requisition.getStatus())) {
            throw new BusinessException("只有已审核状态可关闭，当前状态: " + requisition.getStatus());
        }
        requisition.setStatus("已关闭");
        if (reason != null && !reason.isBlank()) {
            requisition.setRemark((requisition.getRemark() != null ? requisition.getRemark() + " | " : "") + "关闭原因: " + reason);
        }
        updateById(requisition);
        log.info("生产领料单关闭: {} 操作人: {} 原因: {}", requisition.getRequisitionNo(), closer, reason);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        MaterialRequisition requisition = getById(id);
        if (requisition == null) throw new BusinessException("生产领料单不存在");
        if (!"草稿".equals(requisition.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + requisition.getStatus());
        }
        requisition.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            requisition.setRemark((requisition.getRemark() != null ? requisition.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(requisition);
        log.info("生产领料单取消: {} 原因: {}", requisition.getRequisitionNo(), reason);
    }

    @Override
    public String generateRequisitionNo() {
        String prefix = "MR" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<MaterialRequisition> wrapper = new QueryWrapper<>();
        wrapper.likeRight("requisition_no", prefix).orderByDesc("requisition_no").last("LIMIT 1");
        MaterialRequisition last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getRequisitionNo() != null) {
            try {
                seq = Integer.parseInt(last.getRequisitionNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
