package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.OtherIssue;
import com.erp.inventory.entity.OtherIssueItem;
import com.erp.inventory.mapper.OtherIssueItemMapper;
import com.erp.inventory.mapper.OtherIssueMapper;
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
public class OtherIssueServiceImpl extends ServiceImpl<OtherIssueMapper, OtherIssue> implements OtherIssueService {

    private final OtherIssueItemMapper itemMapper;
    private final InventoryService inventoryService;

    private static final int SCALE = 2;

    @Override
    @Transactional
    public void saveIssueWithItems(OtherIssue issue, List<OtherIssueItem> items) {
        boolean isNew = issue.getIssueId() == null;

        if (issue.getIssueNo() == null || issue.getIssueNo().isBlank()) {
            issue.setIssueNo(generateIssueNo());
        }

        if (issue.getStatus() == null) issue.setStatus("草稿");
        if (issue.getApprovalStatus() == null) issue.setApprovalStatus("待审核");
        if (issue.getExchangeRate() == null) issue.setExchangeRate(BigDecimal.ONE);
        if (issue.getTotalAmount() == null) issue.setTotalAmount(BigDecimal.ZERO);
        if (issue.getIssueDate() == null) issue.setIssueDate(LocalDate.now());

        recalculateIssue(issue, items);

        if (isNew) {
            save(issue);
        } else {
            itemMapper.delete(new QueryWrapper<OtherIssueItem>().eq("issue_id", issue.getIssueId()));
            updateById(issue);
        }

        if (items != null) {
            int lineNo = 10;
            for (OtherIssueItem item : items) {
                item.setIssueItemId(null);
                item.setIssueId(issue.getIssueId());
                item.setLineNo(lineNo);
                calculateItemAmount(item);
                itemMapper.insert(item);
                lineNo += 10;
            }
        }

        log.info("其他出库单保存成功: {} 状态: {} 合计: {}", issue.getIssueNo(), issue.getStatus(), issue.getTotalAmount());
    }

    private void calculateItemAmount(OtherIssueItem item) {
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        BigDecimal amount = unitPrice.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
    }

    private void recalculateIssue(OtherIssue issue, List<OtherIssueItem> items) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (OtherIssueItem item : items) {
                calculateItemAmount(item);
                totalAmount = totalAmount.add(item.getAmount());
            }
        }
        issue.setTotalAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<OtherIssue> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<OtherIssue> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("issue_no", keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        Page<OtherIssue> page = new Page<>(current, size);
        Page<OtherIssue> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public OtherIssue getDetail(Long id) {
        OtherIssue issue = getById(id);
        if (issue == null) throw new BusinessException("其他出库单不存在");
        List<OtherIssueItem> items = itemMapper.selectList(
                new QueryWrapper<OtherIssueItem>().eq("issue_id", id).orderByAsc("line_no"));
        issue.setItems(items);
        return issue;
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        OtherIssue issue = getById(id);
        if (issue == null) throw new BusinessException("其他出库单不存在");
        if (!"草稿".equals(issue.getStatus())) {
            throw new BusinessException("只有草稿状态可审核，当前状态: " + issue.getStatus());
        }
        issue.setStatus("已审核");
        issue.setApprovalStatus("已审核");
        issue.setApprovedBy(approver);
        issue.setApprovedAt(LocalDateTime.now());
        updateById(issue);

        // 审核通过后扣减库存 (按新签名: stockId/binId/materialId/batchNo 定位, 库存不足抛 BusinessException)
        List<OtherIssueItem> items = itemMapper.selectList(
                new QueryWrapper<OtherIssueItem>().eq("issue_id", id).orderByAsc("line_no"));
        if (items != null) {
            for (OtherIssueItem item : items) {
                Long stockId = item.getStockId() != null ? item.getStockId() : issue.getWarehouseId();
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
                        issue.getIssueNo(),
                        "OTHER_OUT",
                        "OtherIssue",
                        issue.getIssueNo(),
                        approver);
            }
        }

        log.info("其他出库单审核: {} 审核人: {}", issue.getIssueNo(), approver);
    }

    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        OtherIssue issue = getById(id);
        if (issue == null) throw new BusinessException("其他出库单不存在");
        if (!"已审核".equals(issue.getStatus())) {
            throw new BusinessException("只有已审核状态可关闭，当前状态: " + issue.getStatus());
        }
        issue.setStatus("已关闭");
        if (reason != null && !reason.isBlank()) {
            issue.setRemark((issue.getRemark() != null ? issue.getRemark() + " | " : "") + "关闭原因: " + reason);
        }
        updateById(issue);
        log.info("其他出库单关闭: {} 操作人: {} 原因: {}", issue.getIssueNo(), closer, reason);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        OtherIssue issue = getById(id);
        if (issue == null) throw new BusinessException("其他出库单不存在");
        if (!"草稿".equals(issue.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + issue.getStatus());
        }
        issue.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            issue.setRemark((issue.getRemark() != null ? issue.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(issue);
        log.info("其他出库单取消: {} 原因: {}", issue.getIssueNo(), reason);
    }

    @Override
    public String generateIssueNo() {
        String prefix = "OI" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<OtherIssue> wrapper = new QueryWrapper<>();
        wrapper.likeRight("issue_no", prefix).orderByDesc("issue_no").last("LIMIT 1");
        OtherIssue last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getIssueNo() != null) {
            try {
                seq = Integer.parseInt(last.getIssueNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
