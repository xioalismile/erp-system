package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderMaterial;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderMaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper, WorkOrder> implements WorkOrderService {

    private final WorkOrderMaterialMapper materialMapper;

    @Override
    @Transactional
    public void saveOrderWithItems(WorkOrder order, List<WorkOrderMaterial> materials) {
        boolean isNew = order.getOrderId() == null;

        if (order.getOrderNo() == null || order.getOrderNo().isBlank()) {
            order.setOrderNo(generateOrderNo());
        }
        if (order.getStatus() == null) order.setStatus("草稿");
        if (order.getApprovalStatus() == null) order.setApprovalStatus("待审核");
        if (order.getIsUrgent() == null) order.setIsUrgent((short) 0);
        if (order.getCompletedQuantity() == null) order.setCompletedQuantity(BigDecimal.ZERO);
        if (order.getRejectedQuantity() == null) order.setRejectedQuantity(BigDecimal.ZERO);
        if (order.getOrderDate() == null) order.setOrderDate(LocalDate.now());

        if (isNew) {
            save(order);
        } else {
            // 编辑: 先按 order_id 删除旧明细, 再更新主单
            materialMapper.delete(new QueryWrapper<WorkOrderMaterial>().eq("order_id", order.getOrderId()));
            updateById(order);
        }

        if (materials != null && !materials.isEmpty()) {
            int lineNo = 10;
            for (WorkOrderMaterial material : materials) {
                material.setOrderItemId(null);
                material.setOrderId(order.getOrderId());
                if (material.getLineNo() == null) {
                    material.setLineNo(lineNo);
                    lineNo += 10;
                }
                materialMapper.insert(material);
            }
        }

        log.info("工单{}: {} 物料明细{}项",
                isNew ? "新增" : "编辑", order.getOrderNo(),
                materials != null ? materials.size() : 0);
    }

    @Override
    public PageResult<WorkOrder> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<WorkOrder> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("order_no", keyword)
                    .or().like("material_code", keyword)
                    .or().like("material_name", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        Page<WorkOrder> page = new Page<>(current, size);
        Page<WorkOrder> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public WorkOrder getDetail(Long id) {
        WorkOrder order = getById(id);
        if (order == null) throw new BusinessException("工单不存在");
        List<WorkOrderMaterial> materials = materialMapper.selectList(
                new QueryWrapper<WorkOrderMaterial>().eq("order_id", id).orderByAsc("line_no"));
        order.setMaterials(materials);
        return order;
    }

    @Override
    @Transactional
    public void release(Long id, String operator) {
        WorkOrder order = getById(id);
        if (order == null) throw new BusinessException("工单不存在");
        if (!"草稿".equals(order.getStatus())) {
            throw new BusinessException("只有草稿状态可下达, 当前: " + order.getStatus());
        }
        order.setStatus("已下达");
        order.setUpdatedBy(operator);
        updateById(order);
        log.info("工单下达: {} 操作人: {}", order.getOrderNo(), operator);
    }

    @Override
    @Transactional
    public void complete(Long id, String operator) {
        WorkOrder order = getById(id);
        if (order == null) throw new BusinessException("工单不存在");
        if (!"已下达".equals(order.getStatus())) {
            throw new BusinessException("只有已下达状态可完工, 当前: " + order.getStatus());
        }
        order.setStatus("已完工");
        order.setActualEndDate(LocalDate.now());
        order.setUpdatedBy(operator);
        updateById(order);
        log.info("工单完工: {} 操作人: {}", order.getOrderNo(), operator);
    }

    @Override
    @Transactional
    public void close(Long id, String operator, String reason) {
        WorkOrder order = getById(id);
        if (order == null) throw new BusinessException("工单不存在");
        if (!"已完工".equals(order.getStatus())) {
            throw new BusinessException("只有已完工状态可关闭, 当前: " + order.getStatus());
        }
        order.setStatus("已关闭");
        order.setUpdatedBy(operator);
        if (reason != null && !reason.isBlank()) {
            String existing = order.getRemark();
            order.setRemark((existing != null && !existing.isBlank() ? existing + " | " : "") + "关闭: " + reason);
        }
        updateById(order);
        log.info("工单关闭: {} 操作人: {} 原因: {}", order.getOrderNo(), operator, reason);
    }

    @Override
    public String generateOrderNo() {
        String prefix = "WO" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<WorkOrder> wrapper = new QueryWrapper<>();
        wrapper.likeRight("order_no", prefix).orderByDesc("order_no").last("LIMIT 1");
        WorkOrder last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getOrderNo() != null) {
            try {
                seq = Integer.parseInt(last.getOrderNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
