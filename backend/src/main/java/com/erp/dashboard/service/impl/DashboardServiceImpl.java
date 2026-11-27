package com.erp.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.scope.DataScopeResolver;
import com.erp.dashboard.dto.*;
import com.erp.dashboard.service.DashboardService;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.mapper.InventoryMapper;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import com.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final InventoryMapper inventoryMapper;
    private final MaterialMapper materialMapper;
    private final DataScopeResolver dataScopeResolver;

    // 与系统单据审批状态保持一致：待审核 / 已生效 / 草稿
    private static final String PENDING = "待审核";
    private static final String APPROVED = "已生效";

    private static BigDecimal safe(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    @Autowired
    private MyDashboardHelper myDashboardHelper;

    // ========== 统一数据权限辅助（与对应业务列表页同一 DataScopeResolver 口径）==========

    /** 当前请求的数据范围上下文；非 Web 上下文（定时任务等）返回 null = 不过滤，保持旧行为 */
    private DataScopeResolver.DataScopeContext currentScope() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : dataScopeResolver.resolve(attrs.getRequest());
        } catch (Exception e) {
            return null;
        }
    }

    /** 单据归属过滤：ownerField 为业务归属列，创建人列沿用统一件的默认 created_by */
    private void applyScope(QueryWrapper<?> wrapper, String ownerField, DataScopeResolver.DataScopeContext scope) {
        if (scope != null) scope.applyTo(wrapper, ownerField);
    }

    /** 可见仓库ID集合：null=不过滤；空列表=无任何可见仓库（相关数字应计 0） */
    private List<Long> visibleStockIds(DataScopeResolver.DataScopeContext scope) {
        return scope == null ? null : dataScopeResolver.visibleWarehouseIds(scope);
    }

    @Override
    public OverviewDTO getOverview() {
        try {
            OverviewDTO dto = new OverviewDTO();
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);
            LocalDate monthStart = today.withDayOfMonth(1);

            // 数据权限（统一口径）：销售订单按 saler_id、采购订单按 buyer_id、库存按可见仓库
            DataScopeResolver.DataScopeContext scope = currentScope();

            BigDecimal todaySale = getSaleAmountByDate(today, scope);
            BigDecimal yesterdaySale = getSaleAmountByDate(yesterday, scope);
            dto.setTodaySaleAmount(safe(todaySale));
            dto.setTodaySaleCompare(calcCompare(todaySale, yesterdaySale));

            BigDecimal todayPurchase = getPurchaseAmountByDate(today, scope);
            BigDecimal yesterdayPurchase = getPurchaseAmountByDate(yesterday, scope);
            dto.setTodayPurchaseAmount(safe(todayPurchase));
            dto.setTodayPurchaseCompare(calcCompare(todayPurchase, yesterdayPurchase));

            List<Long> stockIds = visibleStockIds(scope);
            BigDecimal totalInventoryValue = BigDecimal.ZERO;
            // 无任何可见仓库 → 库存金额计 0（与即时库存列表"返回空页"口径一致）
            if (stockIds == null || !stockIds.isEmpty()) {
                try {
                    QueryWrapper<Inventory> invWrapper = new QueryWrapper<>();
                    if (stockIds != null) invWrapper.in("stock_id", stockIds);
                    totalInventoryValue = inventoryMapper.selectList(invWrapper).stream()
                            .map(i -> safe(i.getTotalCost()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                } catch (Exception ignored) { totalInventoryValue = BigDecimal.ZERO; }
            }
            dto.setTotalInventoryValue(totalInventoryValue);

            long pendingSale = 0L, pendingPur = 0L;
            try {
                QueryWrapper<SaleOrder> pendingSaleW = new QueryWrapper<SaleOrder>().eq("approval_status", PENDING);
                applyScope(pendingSaleW, "saler_id", scope);
                pendingSale = saleOrderMapper.selectCount(pendingSaleW);
            } catch (Exception ignored) {}
            try {
                QueryWrapper<PurchaseOrder> pendingPurW = new QueryWrapper<PurchaseOrder>().eq("approval_status", PENDING);
                applyScope(pendingPurW, "buyer_id", scope);
                pendingPur = purchaseOrderMapper.selectCount(pendingPurW);
            } catch (Exception ignored) {}
            dto.setPendingApprovalCount((int) Math.min(pendingSale + pendingPur, Integer.MAX_VALUE));

            long saleCnt = 0L, purCnt = 0L;
            try {
                QueryWrapper<SaleOrder> saleCntW = new QueryWrapper<SaleOrder>().ge("order_date", monthStart);
                applyScope(saleCntW, "saler_id", scope);
                saleCnt = saleOrderMapper.selectCount(saleCntW);
            } catch (Exception ignored) {}
            try {
                QueryWrapper<PurchaseOrder> purCntW = new QueryWrapper<PurchaseOrder>().ge("order_date", monthStart);
                applyScope(purCntW, "buyer_id", scope);
                purCnt = purchaseOrderMapper.selectCount(purCntW);
            } catch (Exception ignored) {}
            dto.setSaleOrderCount((int) Math.min(saleCnt, Integer.MAX_VALUE));
            dto.setPurchaseOrderCount((int) Math.min(purCnt, Integer.MAX_VALUE));

            return dto;
        } catch (Throwable t) {
            // 任何异常都返回兜底，避免工作台首页崩溃导致整个后端进程退出
            OverviewDTO fallback = new OverviewDTO();
            fallback.setTodaySaleAmount(BigDecimal.ZERO);
            fallback.setTodayPurchaseAmount(BigDecimal.ZERO);
            fallback.setTotalInventoryValue(BigDecimal.ZERO);
            fallback.setPendingApprovalCount(0);
            fallback.setSaleOrderCount(0);
            fallback.setPurchaseOrderCount(0);
            return fallback;
        }
    }

    @Override
    public TrendDTO getSaleTrend(Integer days) {
        try {
            TrendDTO dto = new TrendDTO();
            List<String> dates = new ArrayList<>();
            List<BigDecimal> amounts = new ArrayList<>();
            LocalDate today = LocalDate.now();
            int n = (days == null || days < 1) ? 7 : Math.min(days, 365);
            DataScopeResolver.DataScopeContext scope = currentScope();

            for (int i = n - 1; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                dates.add(date.format(DateTimeFormatter.ofPattern("MM-dd")));
                amounts.add(getSaleAmountByDate(date, scope));
            }

            dto.setDates(dates);
            dto.setAmounts(amounts);
            return dto;
        } catch (Throwable t) {
            TrendDTO f = new TrendDTO();
            f.setDates(new ArrayList<>()); f.setAmounts(new ArrayList<>());
            return f;
        }
    }

    @Override
    public TrendDTO getPurchaseTrend(Integer days) {
        try {
            TrendDTO dto = new TrendDTO();
            List<String> dates = new ArrayList<>();
            List<BigDecimal> amounts = new ArrayList<>();
            LocalDate today = LocalDate.now();
            int n = (days == null || days < 1) ? 7 : Math.min(days, 365);

            DataScopeResolver.DataScopeContext scope = currentScope();

            for (int i = n - 1; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                dates.add(date.format(DateTimeFormatter.ofPattern("MM-dd")));
                amounts.add(getPurchaseAmountByDate(date, scope));
            }

            dto.setDates(dates);
            dto.setAmounts(amounts);
            return dto;
        } catch (Throwable t) {
            TrendDTO f = new TrendDTO();
            f.setDates(new ArrayList<>()); f.setAmounts(new ArrayList<>());
            return f;
        }
    }

    @Override
    public List<TopMaterialDTO> getTopSaleMaterials(Integer limit) {
        try {
            int lim = (limit == null || limit < 1) ? 10 : Math.min(limit, 100);
            LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
            QueryWrapper<SaleOrder> orderWrapper = new QueryWrapper<SaleOrder>().ge("order_date", monthStart);
            applyScope(orderWrapper, "saler_id", currentScope());
            List<SaleOrder> orders = saleOrderMapper.selectList(orderWrapper);
            List<Long> orderIds = orders.stream().map(SaleOrder::getOrderId).collect(Collectors.toList());
            if (orderIds.isEmpty()) return new ArrayList<>();

            List<SaleOrderItem> items = saleOrderItemMapper.selectList(
                    new QueryWrapper<SaleOrderItem>()
                            .in("order_id", orderIds)
            );

            Map<Long, BigDecimal> amtMap = new java.util.HashMap<>();
            Map<Long, BigDecimal> qtyMap = new java.util.HashMap<>();
            for (SaleOrderItem item : items) {
                if (item == null || item.getMaterialId() == null) continue;
                Long mid = item.getMaterialId();
                amtMap.merge(mid, safe(item.getAmount()), BigDecimal::add);
                qtyMap.merge(mid, safe(item.getQuantity()), BigDecimal::add);
            }
            return amtMap.entrySet().stream()
                    .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                    .limit(lim)
                    .map(entry -> {
                        TopMaterialDTO dto = new TopMaterialDTO();
                        Long mid = entry.getKey();
                        dto.setMaterialId(mid);
                        try {
                            Material m = materialMapper.selectById(mid);
                            if (m != null) {
                                dto.setMaterialCode(m.getMaterialCode());
                                dto.setMaterialName(m.getMaterialName());
                            }
                        } catch (Exception ignored) { }
                        dto.setAmount(entry.getValue());
                        dto.setQuantity(qtyMap.getOrDefault(mid, BigDecimal.ZERO));
                        return dto;
                    })
                    .collect(Collectors.toList());
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    @Override
    public List<TopMaterialDTO> getTopPurchaseMaterials(Integer limit) {
        try {
            int lim = (limit == null || limit < 1) ? 10 : Math.min(limit, 100);
            LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
            QueryWrapper<PurchaseOrder> orderWrapper = new QueryWrapper<PurchaseOrder>().ge("order_date", monthStart);
            applyScope(orderWrapper, "buyer_id", currentScope());
            List<PurchaseOrder> orders = purchaseOrderMapper.selectList(orderWrapper);
            List<Long> orderIds = orders.stream().map(PurchaseOrder::getOrderId).collect(Collectors.toList());
            if (orderIds.isEmpty()) return new ArrayList<>();

            var items = purchaseOrderItemMapper.selectList(
                    new QueryWrapper<PurchaseOrderItem>()
                            .in("order_id", orderIds)
            );

            Map<Long, BigDecimal> amtMap = new java.util.HashMap<>();
            Map<Long, BigDecimal> qtyMap = new java.util.HashMap<>();
            for (var item : items) {
                if (item == null || item.getMaterialId() == null) continue;
                Long mid = item.getMaterialId();
                amtMap.merge(mid, safe(item.getAmount()), BigDecimal::add);
                qtyMap.merge(mid, safe(item.getQuantity()), BigDecimal::add);
            }
            return amtMap.entrySet().stream()
                    .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                    .limit(lim)
                    .map(entry -> {
                        TopMaterialDTO dto = new TopMaterialDTO();
                        Long mid = entry.getKey();
                        dto.setMaterialId(mid);
                        try {
                            Material m = materialMapper.selectById(mid);
                            if (m != null) {
                                dto.setMaterialCode(m.getMaterialCode());
                                dto.setMaterialName(m.getMaterialName());
                            }
                        } catch (Exception ignored) { }
                        dto.setAmount(entry.getValue());
                        dto.setQuantity(qtyMap.getOrDefault(mid, BigDecimal.ZERO));
                        return dto;
                    })
                    .collect(Collectors.toList());
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    @Override
    public WarningCountDTO getInventoryWarningCount() {
        try {
            WarningCountDTO dto = new WarningCountDTO();
            // 数据权限（与即时库存/库存预警列表同口径）：按可见仓库过滤现存量
            List<Long> stockIds = visibleStockIds(currentScope());
            if (stockIds != null && stockIds.isEmpty()) {
                // 无任何可见仓库 → 预警数全部计 0
                dto.setBelowMinCount(0); dto.setAboveMaxCount(0); dto.setExpiredCount(0); dto.setTotalCount(0);
                return dto;
            }
            List<Material> materials;
            try { materials = materialMapper.selectList(null); }
            catch (Exception e) {
                dto.setBelowMinCount(0); dto.setAboveMaxCount(0); dto.setExpiredCount(0); dto.setTotalCount(0);
                return dto;
            }
            List<Inventory> inventories;
            try {
                inventories = inventoryMapper.selectList(
                        stockIds == null ? null : new QueryWrapper<Inventory>().in("stock_id", stockIds));
            }
            catch (Exception e) { inventories = new ArrayList<>(); }

            int belowMin = 0;
            int aboveMax = 0;

            for (Material material : materials) {
                if (material == null) continue;
                BigDecimal totalQty = inventories.stream()
                        .filter(inv -> inv != null && inv.getMaterialId() != null
                                && inv.getMaterialId().equals(material.getMaterialId()))
                        .map(inv -> safe(inv.getQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (material.getMinStock() != null && material.getMinStock().compareTo(BigDecimal.ZERO) > 0
                        && totalQty.compareTo(material.getMinStock()) < 0) {
                    belowMin++;
                }
                if (material.getMaxStock() != null && material.getMaxStock().compareTo(BigDecimal.ZERO) > 0
                        && totalQty.compareTo(material.getMaxStock()) > 0) {
                    aboveMax++;
                }
            }

            dto.setBelowMinCount(belowMin);
            dto.setAboveMaxCount(aboveMax);
            dto.setExpiredCount(0);
            dto.setTotalCount(belowMin + aboveMax);
            return dto;
        } catch (Throwable t) {
            WarningCountDTO f = new WarningCountDTO();
            f.setBelowMinCount(0); f.setAboveMaxCount(0); f.setExpiredCount(0); f.setTotalCount(0);
            return f;
        }
    }

    @Override
    public List<TodoItemDTO> getTodoList() {
        try {
            List<TodoItemDTO> list = new ArrayList<>();

            DataScopeResolver.DataScopeContext scope = currentScope();

            try {
                QueryWrapper<SaleOrder> saleWrapper = new QueryWrapper<SaleOrder>()
                        .eq("approval_status", PENDING)
                        .orderByDesc("created_at")
                        .last("LIMIT 5");
                applyScope(saleWrapper, "saler_id", scope);
                List<SaleOrder> saleOrders = saleOrderMapper.selectList(saleWrapper);
                for (SaleOrder order : saleOrders) {
                    if (order == null) continue;
                    TodoItemDTO item = new TodoItemDTO();
                    item.setId(order.getOrderId());
                    item.setType("SALE_ORDER");
                    item.setTypeName("销售订单");
                    item.setBillNo(order.getOrderNo());
                    String party = order.getCustomerShortName() != null ? order.getCustomerShortName() : "";
                    item.setTitle(party + " - 销售订单审批");
                    item.setStatus(order.getApprovalStatus());
                    item.setCreateTime(order.getCreatedAt());
                    item.setCreator(order.getCreatedBy());
                    list.add(item);
                }
            } catch (Exception ignored) { }

            try {
                QueryWrapper<PurchaseOrder> purchaseWrapper = new QueryWrapper<PurchaseOrder>()
                        .eq("approval_status", PENDING)
                        .orderByDesc("created_at")
                        .last("LIMIT 5");
                applyScope(purchaseWrapper, "buyer_id", scope);
                List<PurchaseOrder> purchaseOrders = purchaseOrderMapper.selectList(purchaseWrapper);
                for (PurchaseOrder order : purchaseOrders) {
                    if (order == null) continue;
                    TodoItemDTO item = new TodoItemDTO();
                    item.setId(order.getOrderId());
                    item.setType("PURCHASE_ORDER");
                    item.setTypeName("采购订单");
                    item.setBillNo(order.getOrderNo());
                    String party = order.getSupplierName() != null ? order.getSupplierName() : "";
                    item.setTitle(party + " - 采购订单审批");
                    item.setStatus(order.getApprovalStatus());
                    item.setCreateTime(order.getCreatedAt());
                    item.setCreator(order.getCreatedBy());
                    list.add(item);
                }
            } catch (Exception ignored) { }

            return list.stream()
                    .sorted(Comparator.comparing(TodoItemDTO::getCreateTime,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(10)
                    .collect(Collectors.toList());
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    @Override
    public List<EmployeeRankingDTO> getEmployeeRanking(String month) {
        YearMonth yearMonth = month != null ? YearMonth.parse(month) : YearMonth.now();
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        QueryWrapper<SaleOrder> orderWrapper = new QueryWrapper<SaleOrder>()
                .between("order_date", monthStart, monthEnd);
        applyScope(orderWrapper, "saler_id", currentScope());
        List<SaleOrder> orders = saleOrderMapper.selectList(orderWrapper);

        return orders.stream()
                .filter(o -> o.getSalerId() != null)
                .collect(Collectors.groupingBy(
                        SaleOrder::getSalerId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                orderList -> {
                                    EmployeeRankingDTO dto = new EmployeeRankingDTO();
                                    dto.setEmployeeId(orderList.get(0).getSalerId());
                                    dto.setEmployeeName(orderList.get(0).getSalerName());
                                    dto.setDepartmentName("");
                                    dto.setOrderCount(orderList.size());
                                    BigDecimal totalAmount = orderList.stream()
                                            .map(SaleOrder::getTotalAmount)
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                                    dto.setAmount(totalAmount);
                                    return dto;
                                }
                        )
                ))
                .values().stream()
                .sorted(Comparator.comparing(EmployeeRankingDTO::getAmount).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    private BigDecimal getSaleAmountByDate(LocalDate date, DataScopeResolver.DataScopeContext scope) {
        try {
            QueryWrapper<SaleOrder> wrapper = new QueryWrapper<SaleOrder>().eq("order_date", date);
            applyScope(wrapper, "saler_id", scope);
            List<SaleOrder> orders = saleOrderMapper.selectList(wrapper);
            return orders.stream()
                    .map(o -> safe(o.getTotalAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal getPurchaseAmountByDate(LocalDate date, DataScopeResolver.DataScopeContext scope) {
        try {
            QueryWrapper<PurchaseOrder> wrapper = new QueryWrapper<PurchaseOrder>().eq("order_date", date);
            applyScope(wrapper, "buyer_id", scope);
            List<PurchaseOrder> orders = purchaseOrderMapper.selectList(wrapper);
            return orders.stream()
                    .map(o -> safe(o.getTotalAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calcCompare(BigDecimal current, BigDecimal previous) {
        BigDecimal c = safe(current);
        BigDecimal p = safe(previous);
        if (p.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        try {
            return c.subtract(p)
                    .divide(p, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // ========== 个人工作台实现：委托给 MyDashboardHelper，全面兜底 ==========

    @Override
    public MyOverviewDTO getMyOverview(String username, Long employeeId, List<String> roles) {
        try {
            return myDashboardHelper.getMyOverview(username, employeeId, roles);
        } catch (Throwable t) {
            MyOverviewDTO f = new MyOverviewDTO();
            f.setPendingApprovalCount(0); f.setOverdueCount(0); f.setMyDraftCount(0); f.setArApFollowUpCount(0);
            return f;
        }
    }

    @Override
    public List<MyTodoDTO> getMyPendingApprovals(String username, Long employeeId, List<String> roles) {
        try { return myDashboardHelper.getMyPendingApprovals(username, employeeId, roles); }
        catch (Throwable t) { return new ArrayList<>(); }
    }

    @Override
    public List<MyTodoDTO> getMyEnginePendingApprovals(String username) {
        try { return myDashboardHelper.getEnginePendingApprovals(username); }
        catch (Throwable t) { return new ArrayList<>(); }
    }

    @Override
    public List<MyTodoDTO> getMyOverdueTasks(String username, Long employeeId, List<String> roles) {
        try { return myDashboardHelper.getMyOverdueTasks(username, employeeId, roles); }
        catch (Throwable t) { return new ArrayList<>(); }
    }

    @Override
    public List<MyTodoDTO> getMyDrafts(String username, Long employeeId, List<String> roles) {
        try { return myDashboardHelper.getMyDrafts(username, employeeId, roles); }
        catch (Throwable t) { return new ArrayList<>(); }
    }

    @Override
    public List<MyTodoDTO> getMyArApFollowUp(String username, Long employeeId, List<String> roles) {
        try { return myDashboardHelper.getMyArApFollowUp(username, employeeId, roles); }
        catch (Throwable t) { return new ArrayList<>(); }
    }

    @Override
    public List<MyTodoDTO> getMyOverdueTasksSelfOnly(String username, Long employeeId, List<String> roles) {
        try { return myDashboardHelper.getMyOverdueTasksSelfOnly(username, employeeId, roles); }
        catch (Throwable t) { return new ArrayList<>(); }
    }

    @Override
    public List<MyTodoDTO> getMyArApFollowUpSelfOnly(String username, Long employeeId, List<String> roles) {
        try { return myDashboardHelper.getMyArApFollowUpSelfOnly(username, employeeId, roles); }
        catch (Throwable t) { return new ArrayList<>(); }
    }
}
