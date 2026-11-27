package com.erp.sale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.entity.SaleOrderSubstitutePlan;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import com.erp.sale.mapper.SaleOrderSubstitutePlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 替换发货指示实现。
 *
 * <p>口径：
 * <ul>
 *   <li>指示只挂在已生效、未结案的销售订单行上；不改变订单行货品/数量/金额。</li>
 *   <li>同一订单行待执行指示合计 ≤ 待发净额 U = 订购 - (已发 - 已退)。</li>
 *   <li>该订单行存在草稿/待审核的仓库替代行时，不允许再加销售指示（防重复指定）。</li>
 *   <li>已执行 &gt; 0 的指示只能取消剩余，不能改替代品/数量/单价。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOrderSubstitutePlanServiceImpl
        extends ServiceImpl<SaleOrderSubstitutePlanMapper, SaleOrderSubstitutePlan>
        implements SaleOrderSubstitutePlanService {

    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final MaterialService materialService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<SaleOrderSubstitutePlan> listByOrder(Long orderId) {
        if (orderId == null) return Collections.emptyList();
        SaleOrder order = saleOrderMapper.selectById(orderId);
        List<SaleOrderSubstitutePlan> plans = list(new QueryWrapper<SaleOrderSubstitutePlan>()
                .eq("order_id", orderId)
                .orderByAsc("order_item_id")
                .orderByAsc("plan_id"));
        if (plans.isEmpty()) return plans;
        Set<Long> itemIds = new HashSet<>();
        for (SaleOrderSubstitutePlan p : plans) itemIds.add(p.getOrderItemId());
        Map<Long, SaleOrderItem> itemMap = new HashMap<>();
        if (!itemIds.isEmpty()) {
            List<SaleOrderItem> items = saleOrderItemMapper.selectBatchIds(itemIds);
            if (items != null) for (SaleOrderItem it : items) itemMap.put(it.getOrderItemId(), it);
        }
        for (SaleOrderSubstitutePlan p : plans) {
            p.setOrderNo(order != null ? order.getOrderNo() : null);
            SaleOrderItem it = itemMap.get(p.getOrderItemId());
            if (it != null) {
                p.setLineNo(it.getLineNo());
                p.setOriginMaterialId(it.getMaterialId());
                p.setOriginMaterialCode(it.getMaterialCode());
                p.setOriginMaterialName(it.getMaterialName());
            }
            p.setPendingQuantity(pendingOf(p));
        }
        return plans;
    }

    @Override
    @Transactional
    public SaleOrderSubstitutePlan savePlan(SaleOrderSubstitutePlan plan, String operator) {
        if (plan == null || plan.getOrderId() == null || plan.getOrderItemId() == null) {
            throw new BusinessException("缺少订单或订单明细");
        }
        validate(plan, null);
        fillSnapshots(plan);
        plan.setPlanId(null);
        plan.setTurnedDeliveryQuantity(BigDecimal.ZERO);
        plan.setStatus("有效");
        if (plan.getRemark() == null) plan.setRemark("");
        plan.setCreatedBy(operator);
        plan.setUpdatedBy(operator);
        save(plan);
        plan.setPendingQuantity(pendingOf(plan));
        return plan;
    }

    @Override
    @Transactional
    public SaleOrderSubstitutePlan updatePlan(Long id, SaleOrderSubstitutePlan req, String operator) {
        SaleOrderSubstitutePlan plan = getById(id);
        if (plan == null) throw new BusinessException("替换发货指示不存在");
        if (!"有效".equals(plan.getStatus())) throw new BusinessException("该指示已取消，不能修改");
        if (nvl(plan.getTurnedDeliveryQuantity()).signum() > 0) {
            throw new BusinessException("该指示已执行，不能修改；只能取消剩余");
        }
        if (req == null) throw new BusinessException("缺少修改内容");
        // 归属不允许改
        req.setPlanId(id);
        req.setOrderId(plan.getOrderId());
        req.setOrderItemId(plan.getOrderItemId());
        validate(req, id);
        fillSnapshots(req);
        req.setTurnedDeliveryQuantity(BigDecimal.ZERO);
        req.setStatus("有效");
        if (req.getRemark() == null) req.setRemark("");
        req.setCreatedBy(plan.getCreatedBy());
        req.setCreatedAt(plan.getCreatedAt());
        req.setUpdatedBy(operator);
        updateById(req);
        req.setPendingQuantity(pendingOf(req));
        return req;
    }

    @Override
    @Transactional
    public void cancelPlan(Long id, String operator) {
        SaleOrderSubstitutePlan plan = getById(id);
        if (plan == null) throw new BusinessException("替换发货指示不存在");
        if ("已取消".equals(plan.getStatus())) return;
        plan.setStatus("已取消");
        plan.setUpdatedBy(operator);
        updateById(plan);
    }

    @Override
    @Transactional
    public int cancelByOrder(Long orderId, String reason, String operator) {
        if (orderId == null) return 0;
        List<SaleOrderSubstitutePlan> active = list(new QueryWrapper<SaleOrderSubstitutePlan>()
                .eq("order_id", orderId).eq("status", "有效"));
        int n = 0;
        for (SaleOrderSubstitutePlan p : active) {
            p.setStatus("已取消");
            p.setRemark(appendReason(p.getRemark(), reason));
            p.setUpdatedBy(operator);
            updateById(p);
            n++;
        }
        return n;
    }

    @Override
    @Transactional
    public void deleteByOrder(Long orderId) {
        if (orderId == null) return;
        remove(new QueryWrapper<SaleOrderSubstitutePlan>().eq("order_id", orderId));
    }

    @Override
    public boolean hasActivePendingPlan(Long orderItemId) {
        if (orderItemId == null) return false;
        List<SaleOrderSubstitutePlan> rows = list(new QueryWrapper<SaleOrderSubstitutePlan>()
                .eq("order_item_id", orderItemId).eq("status", "有效"));
        for (SaleOrderSubstitutePlan p : rows) {
            if (nvl(p.getQuantity()).subtract(nvl(p.getTurnedDeliveryQuantity())).signum() > 0) return true;
        }
        return false;
    }

    @Override
    public Set<Long> activePendingItemIds(Collection<Long> orderItemIds) {
        Set<Long> out = new HashSet<>();
        if (orderItemIds == null || orderItemIds.isEmpty()) return out;
        List<SaleOrderSubstitutePlan> rows = list(new QueryWrapper<SaleOrderSubstitutePlan>()
                .in("order_item_id", orderItemIds).eq("status", "有效"));
        for (SaleOrderSubstitutePlan p : rows) {
            if (nvl(p.getQuantity()).subtract(nvl(p.getTurnedDeliveryQuantity())).signum() > 0) {
                out.add(p.getOrderItemId());
            }
        }
        return out;
    }

    @Override
    @Transactional
    public void addTurned(Long planId, BigDecimal delta) {
        if (planId == null || delta == null || delta.signum() == 0) return;
        SaleOrderSubstitutePlan plan = getById(planId);
        if (plan == null) {
            log.warn("替换发货指示不存在，跳过已执行量回写 planId={}", planId);
            return;
        }
        BigDecimal turned = nvl(plan.getTurnedDeliveryQuantity()).add(delta);
        if (turned.signum() < 0) turned = BigDecimal.ZERO;
        if (delta.signum() > 0 && turned.compareTo(nvl(plan.getQuantity())) > 0) {
            throw new BusinessException("替换发货指示可执行数量不足（指示 "
                    + fmt(nvl(plan.getQuantity())) + "，已执行 "
                    + fmt(nvl(plan.getTurnedDeliveryQuantity())) + "，本次 " + fmt(delta) + "）");
        }
        plan.setTurnedDeliveryQuantity(turned);
        updateById(plan);
    }

    @Override
    public BigDecimal sumPendingByOrderItem(Long orderItemId, Long excludePlanId) {
        if (orderItemId == null) return BigDecimal.ZERO;
        List<SaleOrderSubstitutePlan> rows = list(new QueryWrapper<SaleOrderSubstitutePlan>()
                .eq("order_item_id", orderItemId).eq("status", "有效"));
        BigDecimal sum = BigDecimal.ZERO;
        for (SaleOrderSubstitutePlan p : rows) {
            if (excludePlanId != null && excludePlanId.equals(p.getPlanId())) continue;
            sum = sum.add(nvl(p.getQuantity()).subtract(nvl(p.getTurnedDeliveryQuantity())));
        }
        return sum.max(BigDecimal.ZERO);
    }

    // ==================== 校验与工具 ====================

    private void validate(SaleOrderSubstitutePlan plan, Long excludeId) {
        SaleOrder order = saleOrderMapper.selectById(plan.getOrderId());
        if (order == null) throw new BusinessException("销售订单不存在");
        if (!"已生效".equals(order.getStatus())) {
            throw new BusinessException("只有已生效订单可以指定替换发货；草稿订单请直接改订单行");
        }
        if (order.getCloseStatus() != null && order.getCloseStatus() == 1) {
            throw new BusinessException("订单已结案，不能再指定替换发货");
        }
        SaleOrderItem item = saleOrderItemMapper.selectById(plan.getOrderItemId());
        if (item == null || !plan.getOrderId().equals(item.getOrderId())) {
            throw new BusinessException("订单明细不存在或不属于该订单");
        }
        if (plan.getSubstituteMaterialId() == null) throw new BusinessException("请选择替代货品");
        if (plan.getSubstituteMaterialId().equals(item.getMaterialId())) {
            throw new BusinessException("替代货品不能与原货品相同");
        }
        Material origin = item.getMaterialId() == null ? null : materialService.getById(item.getMaterialId());
        Material sub = materialService.getById(plan.getSubstituteMaterialId());
        if (sub == null) throw new BusinessException("替代货品不存在");
        if (origin != null && origin.getBaseUnitId() != null && sub.getBaseUnitId() != null
                && !origin.getBaseUnitId().equals(sub.getBaseUnitId())) {
            throw new BusinessException("替代货品与原货品基本单位不一致，无法替换发货");
        }
        BigDecimal qty = nvl(plan.getQuantity());
        if (qty.signum() <= 0) throw new BusinessException("替换数量必须大于 0");

        BigDecimal undelivered = nvl(item.getQuantity())
                .subtract(nvl(item.getTurnedOutputQuantity()))
                .add(nvl(item.getTurnedReturnQuantity()));
        if (undelivered.signum() < 0) undelivered = BigDecimal.ZERO;
        BigDecimal otherPending = sumPendingByOrderItem(item.getOrderItemId(), excludeId);
        if (otherPending.add(qty).compareTo(undelivered) > 0) {
            throw new BusinessException("待执行指示合计不能超过待发数量 "
                    + fmt(undelivered) + "，当前其它待执行 " + fmt(otherPending)
                    + "，本单最多可指定 " + fmt(undelivered.subtract(otherPending).max(BigDecimal.ZERO)));
        }
        // 同一订单行同一替代品只保留一条有效指示
        QueryWrapper<SaleOrderSubstitutePlan> dup = new QueryWrapper<SaleOrderSubstitutePlan>()
                .eq("order_item_id", item.getOrderItemId())
                .eq("substitute_material_id", plan.getSubstituteMaterialId())
                .eq("status", "有效");
        if (excludeId != null) dup.ne("plan_id", excludeId);
        if (count(dup) > 0) {
            throw new BusinessException("该订单行已有同一替代品的有效指示，请直接编辑原指示");
        }
        // 该订单行有仓库替代的草稿/待审核出库行：先处理草稿，避免重复指定
        if (hasWarehouseDraftReplacement(item.getOrderItemId())) {
            throw new BusinessException("该订单行已有仓库替代的草稿/待审核出库单，请先处理该出库单后再加销售指示");
        }
    }

    private void fillSnapshots(SaleOrderSubstitutePlan plan) {
        Material sub = materialService.getById(plan.getSubstituteMaterialId());
        if (sub == null) throw new BusinessException("替代货品不存在");
        SaleOrderItem item = saleOrderItemMapper.selectById(plan.getOrderItemId());
        plan.setSubstituteMaterialCode(sub.getMaterialCode());
        plan.setSubstituteMaterialName(sub.getMaterialName());
        plan.setSpecification(sub.getModel());
        plan.setBaseUnitId(sub.getBaseUnitId());
        if (plan.getUnitPrice() == null) {
            plan.setUnitPrice(item != null ? nvl(item.getUnitPrice()) : BigDecimal.ZERO);
        }
        SaleOrder order = saleOrderMapper.selectById(plan.getOrderId());
        if (order != null) plan.setOrgCode(order.getOrgCode());
    }

    /** 未作废/未取消的仓库替代草稿或待审核行（已生效行已计入待发净额，不拦） */
    private boolean hasWarehouseDraftReplacement(Long orderItemId) {
        if (orderItemId == null) return false;
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sale_delivery_item di "
                        + "JOIN sale_delivery dh ON dh.delivery_id = di.delivery_id "
                        + "WHERE di.source_item_id = ? "
                        + "AND di.replaced_from_material_id IS NOT NULL "
                        + "AND di.replace_plan_id IS NULL "
                        + "AND dh.status IN ('草稿','待审核')",
                Integer.class, orderItemId);
        return c != null && c > 0;
    }

    private static BigDecimal pendingOf(SaleOrderSubstitutePlan p) {
        return nvl(p.getQuantity()).subtract(nvl(p.getTurnedDeliveryQuantity())).max(BigDecimal.ZERO);
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String fmt(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }

    private static String appendReason(String old, String reason) {
        if (reason == null || reason.isBlank()) return old;
        String base = old == null ? "" : old;
        return base.isBlank() ? reason : base + " | " + reason;
    }
}
