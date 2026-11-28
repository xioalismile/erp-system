package com.erp.sale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.customfield.service.CustomFieldService;
import com.erp.basedata.entity.Currency;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.entity.MaterialSubstitute;
import com.erp.masterdata.mapper.MaterialSubstituteMapper;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.*;
import com.erp.sale.mapper.*;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 销售换货单实现。
 *
 * <p>核心口径（详见 PRD 第十三章）：
 * <pre>
 * D  = 已生效【普通】出库数量（排除换货生成的出库单）
 * E  = 已生效换货数量（A + B）
 * R_A= 已退回原货 = 订单行累计退货 − R_C
 * R_C= 已退回替换货（退货来源是换货生成的出库单）
 * H  = 客户持有 = (D − R_A) + (E − R_C)
 * P  = 待退回   = max(0, min(E, D) − R_A)
 * 可替换 = 订购数量 − (H − P)
 * </pre>
 * 换货链不读设置中心「超过来源单数量」三态开关，硬约束始终生效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleExchangeServiceImpl extends AbstractBillService<SaleExchangeMapper, SaleExchange>
        implements SaleExchangeService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SaleExchangeItemMapper itemMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final MaterialService materialService;
    private final MaterialSubstituteMapper materialSubstituteMapper;
    // 2026-11-18：列表/详情显示字段（客户名/销售员/币种）——单据列表每列必须有值
    private final CustomerService customerService;
    private final EmployeeService employeeService;
    private final CurrencyService currencyService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SysParamValidator sysParamValidator;
    private final JdbcTemplate jdbcTemplate;
    // 自定义字段值（表头 sale_exchange / 明细 sale_exchange_item）与数据版本记录
    private final CustomFieldService customFieldService;
    private final SaleBillVersionRecorder saleBillVersionRecorder;

    @Lazy
    private final SaleDeliveryService saleDeliveryService;

    /** 删除草稿出库单时级联清理明细（避免孤儿明细） */
    @org.springframework.beans.factory.annotation.Autowired
    private SaleDeliveryItemMapper saleDeliveryItemMapper;

    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "exchange_no", "exchange_date", "customer_id", "saler_id", "total_quantity",
            "total_amount", "tax_amount", "net_amount", "status", "approval_status",
            "approved_at", "created_at", "created_by");

    // ==================== AbstractBillService 基础实现 ====================

    @Override protected String getBillTypeLabel() { return "销售换货单"; }
    @Override protected String getApprovalBillType() { return "SALE_EXCHANGE"; }
    @Override protected String getBillNo(SaleExchange b) { return b.getExchangeNo(); }
    @Override protected Object getId(SaleExchange b) { return b.getExchangeId(); }
    @Override protected String getStatus(SaleExchange b) { return b.getStatus(); }
    @Override protected void setStatus(SaleExchange b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(SaleExchange b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SaleExchange b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SaleExchange b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SaleExchange b, LocalDateTime t) { b.setApprovedAt(t); }
    @Override protected void setApprovalStatus(SaleExchange b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SaleExchange b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SaleExchange b, LocalDateTime t) { b.setClosedAt(t); }
    @Override protected void setClosedReason(SaleExchange b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SaleExchange b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SaleExchange b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SaleExchange b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SaleExchange b, LocalDateTime t) { b.setVoidedAt(t); }
    @Override protected void setVoidReason(SaleExchange b, String v) { b.setVoidReason(v); }

    // ==================== 查询 ====================

    @Override
    public PageResult<SaleExchange> pageQuery(int current, int size, String keyword, String status,
                                              String startDate, String endDate, String customerId, String employeeId,
                                              com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                              String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<SaleExchange> wrapper = new QueryWrapper<>();
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) {
            SortHelper.orderByNewest(wrapper, SaleExchange.class);
        }
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "exchange_no", "source_bill_no", "customer_name");
        ListQuerySupport.applyDateRange(wrapper, "exchange_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "saler_id");
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "saler_id");
        CustomFieldSearchHelper.apply(wrapper, "sale_exchange", customSearch, "exchange_id");
        Page<SaleExchange> page = page(new Page<>(current, size), wrapper);
        // 2026-11-18：列表每列必须有值（客户/销售员/币种）——实体无对应列，逐行回填显示字段
        for (SaleExchange r : page.getRecords()) fillDisplayFields(r);
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    @Override
    public SaleExchange getDetail(Long id) {
        SaleExchange bill = getById(id);
        if (bill == null) throw new BusinessException("销售换货单不存在");
        fillDisplayFields(bill);
        bill.setItems(itemMapper.selectList(new QueryWrapper<SaleExchangeItem>()
                .eq("exchange_id", id).orderByAsc("line_no")));
        fillCustomFieldValues(bill);
        return bill;
    }

    /** 详情回填自定义字段值：表头 + 每行明细（旧库/未配置字段时返回空 Map，不报错） */
    private void fillCustomFieldValues(SaleExchange bill) {
        if (bill == null || bill.getExchangeId() == null) return;
        try {
            bill.setCustomFieldValues(customFieldService.getFieldValues("sale_exchange", bill.getExchangeId()));
            if (bill.getItems() != null) {
                for (SaleExchangeItem it : bill.getItems()) {
                    if (it != null && it.getExchangeItemId() != null) {
                        it.setCustomFieldValues(customFieldService.getFieldValues(
                                "sale_exchange_item", it.getExchangeItemId()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("回填销售换货单自定义字段失败 id={}: {}", bill.getExchangeId(), e.getMessage());
        }
    }

    /** 回填显示字段：客户名称 / 销售员姓名 / 币种代码（非数据库列） */
    private void fillDisplayFields(SaleExchange bill) {
        if (bill.getCustomerId() != null) {
            Customer c = customerService.getById(bill.getCustomerId());
            if (c != null) bill.setCustomerName(c.getCustomerName());
        }
        if (bill.getSalerId() != null) {
            Employee e = employeeService.getById(bill.getSalerId());
            if (e != null) bill.setSalerName(e.getEmployeeName());
        }
        if (bill.getCurrencyId() != null) {
            Currency cur = currencyService.getById(bill.getCurrencyId());
            if (cur != null) bill.setCurrencyCode(cur.getCurrencyCode());
        }
    }

    // ==================== 保存 ====================

    @Override
    @Transactional
    public SaleExchange saveExchangeWithItems(SaleExchange bill, List<SaleExchangeItem> items) {
        boolean isNew = bill.getExchangeId() == null;
        SaleExchange oldSnapshot = null;
        if (!isNew) {
            oldSnapshot = getById(bill.getExchangeId());
            if (oldSnapshot != null) {
                BillStatusUtils.assertDirectModifiable("销售换货单", oldSnapshot.getExchangeNo(),
                        oldSnapshot.getStatus(), oldSnapshot.getCloseStatus());
            }
        }
        if (bill.getExchangeNo() == null || bill.getExchangeNo().isBlank()) {
            bill.setExchangeNo(generateExchangeNo());
        }
        QueryWrapper<SaleExchange> dupQw = new QueryWrapper<SaleExchange>().eq("exchange_no", bill.getExchangeNo());
        if (!isNew) dupQw.ne("exchange_id", bill.getExchangeId());
        sysParamValidator.validateBillNoDuplicate(bill.getExchangeNo(), "销售换货单", count(dupQw) > 0);

        if (bill.getStatus() == null || bill.getStatus().isBlank()) bill.setStatus("草稿");
        if (bill.getApprovalStatus() == null || bill.getApprovalStatus().isBlank()
                || ("草稿".equals(bill.getStatus()) && "待审核".equals(bill.getApprovalStatus()))) {
            bill.setApprovalStatus("草稿");
        }
        if (bill.getExchangeDate() == null) bill.setExchangeDate(LocalDate.now());
        if (bill.getExchangeRate() == null) bill.setExchangeRate(BigDecimal.ONE);
        if (bill.getSourceBillType() == null) bill.setSourceBillType("销售订单");

        List<SaleExchangeItem> valid = new ArrayList<>();
        if (items != null) {
            for (SaleExchangeItem it : items) {
                if (it != null && it.getMaterialId() != null && it.getSourceItemId() != null) valid.add(it);
            }
        }
        recalc(bill, valid);

        if (isNew) save(bill); else updateById(bill);
        // 自定义字段：表头先落库（主表已有 id），明细行插入拿到新 id 后再逐行保存
        saveHeaderCustomFieldValues(bill);

        if (items != null) {
            // 明细先删后插：旧行 id 的自定义字段值一并清理，避免孤儿值
            clearItemCustomFieldValues(bill.getExchangeId());
            itemMapper.delete(new QueryWrapper<SaleExchangeItem>().eq("exchange_id", bill.getExchangeId()));
            int lineNo = 1;
            for (SaleExchangeItem it : valid) {
                it.setExchangeItemId(null);
                it.setExchangeId(bill.getExchangeId());
                it.setLineNo(lineNo++);
                calcItem(it, bill.getTaxCalcMethod());
                if (it.getRouteType() == null) fillRoute(it, bill);
                itemMapper.insert(it);
                saveItemCustomFieldValues(it);
            }
            bill.setItems(valid);
        }
        // 数据版本记录（独立事务，失败只告警）
        recordVersion(isNew ? "INSERT" : "UPDATE", bill, isNew ? null : oldSnapshot, bill);
        return bill;
    }

    /** 保存表头自定义字段值（值为空 Map 时不处理，避免误清空；显式传 null 值表示清空该字段） */
    private void saveHeaderCustomFieldValues(SaleExchange bill) {
        if (bill == null || bill.getExchangeId() == null) return;
        Map<String, Object> values = bill.getCustomFieldValues();
        if (values == null || values.isEmpty()) return;
        try {
            customFieldService.saveFieldValues("sale_exchange", bill.getExchangeId(), values);
        } catch (Exception e) {
            log.warn("保存销售换货单表头自定义字段失败 id={}: {}", bill.getExchangeId(), e.getMessage());
        }
    }

    /** 保存明细行自定义字段值（明细必须先 insert 拿到主键） */
    private void saveItemCustomFieldValues(SaleExchangeItem it) {
        if (it == null || it.getExchangeItemId() == null) return;
        Map<String, Object> values = it.getCustomFieldValues();
        if (values == null || values.isEmpty()) return;
        try {
            customFieldService.saveFieldValues("sale_exchange_item", it.getExchangeItemId(), values);
        } catch (Exception e) {
            log.warn("保存销售换货单明细自定义字段失败 id={}: {}", it.getExchangeItemId(), e.getMessage());
        }
    }

    /** 清理本单所有旧明细行的自定义字段值（先删后插前调用） */
    private void clearItemCustomFieldValues(Long exchangeId) {
        if (exchangeId == null) return;
        try {
            List<SaleExchangeItem> olds = itemMapper.selectList(new QueryWrapper<SaleExchangeItem>()
                    .select("exchange_item_id").eq("exchange_id", exchangeId));
            for (SaleExchangeItem old : olds) {
                if (old != null && old.getExchangeItemId() != null) {
                    customFieldService.deleteFieldValues("sale_exchange_item", old.getExchangeItemId());
                }
            }
        } catch (Exception e) {
            log.warn("清理销售换货单明细自定义字段失败 exchangeId={}: {}", exchangeId, e.getMessage());
        }
    }

    /** 数据版本记录：独立事务，失败只告警、不影响单据保存 */
    private void recordVersion(String op, SaleExchange bill, Object before, Object after) {
        if (bill == null || bill.getExchangeId() == null) return;
        try {
            String operator = bill.getUpdatedBy() != null && !bill.getUpdatedBy().isBlank()
                    ? bill.getUpdatedBy() : bill.getCreatedBy();
            String remark = "INSERT".equals(op) ? "新增" : ("UPDATE".equals(op) ? "修改" : "删除");
            saleBillVersionRecorder.record("sale_exchange", bill.getExchangeId(), bill.getExchangeNo(),
                    "sale", op, before, after, operator, remark);
        } catch (Exception e) {
            log.warn("销售换货单数据版本记录失败 id={} op={}: {}", bill.getExchangeId(), op, e.getMessage());
        }
    }

    /** 汇总表头金额 */
    private void recalc(SaleExchange bill, List<SaleExchangeItem> items) {
        BigDecimal qty = BigDecimal.ZERO, amount = BigDecimal.ZERO, tax = BigDecimal.ZERO,
                discount = BigDecimal.ZERO, net = BigDecimal.ZERO;
        for (SaleExchangeItem it : items) {
            calcItem(it, bill.getTaxCalcMethod());
            qty = qty.add(nvl(it.getQuantity()));
            amount = amount.add(nvl(it.getAmount()));
            tax = tax.add(nvl(it.getTaxAmount()));
            discount = discount.add(nvl(it.getDiscountAmount()));
            net = net.add(nvl(it.getNetAmount()));
        }
        bill.setTotalQuantity(qty);
        bill.setTotalAmount(amount);
        bill.setTaxAmount(tax);
        bill.setDiscountAmount(discount);
        bill.setNetAmount(net);
    }

    /** 行金额计算（含税价 / 不含税价 / 不计税） */
    private void calcItem(SaleExchangeItem it, String taxCalcMethod) {
        BigDecimal qty = nvl(it.getQuantity());
        BigDecimal price = nvl(it.getUnitPrice());
        BigDecimal rate = nvl(it.getDiscountRate());
        BigDecimal taxRate = nvl(it.getTaxRate());
        if (qty.signum() <= 0) throw new BusinessException("替换数量必须大于 0");
        if (price.signum() < 0) throw new BusinessException("单价不能为负数");
        if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(HUNDRED) > 0) {
            throw new BusinessException("税率必须在 0~100 之间");
        }
        BigDecimal gross = qty.multiply(price);
        BigDecimal discountAmount = gross.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal amount = gross.subtract(discountAmount);
        BigDecimal tax;
        BigDecimal net;
        String method = taxCalcMethod == null ? "含税价" : taxCalcMethod;
        if ("不计税".equals(method)) {
            tax = BigDecimal.ZERO;
            net = amount;
        } else if ("不含税价".equals(method)) {
            tax = amount.multiply(taxRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            net = amount.add(tax);
        } else {
            BigDecimal factor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));
            tax = amount.subtract(amount.divide(factor, 2, RoundingMode.HALF_UP));
            net = amount;
        }
        it.setDiscountAmount(discountAmount);
        it.setAmount(amount);
        it.setTaxAmount(tax);
        it.setNetAmount(net);
        it.setPriceWithTax(price);
        it.setPriceWithoutTax(price);
    }

    // ==================== 台账（H / P） ====================

    private static class Ledger {
        BigDecimal d = BigDecimal.ZERO;      // 已发货（普通出库）
        BigDecimal e = BigDecimal.ZERO;      // 已换货
        BigDecimal rTotal = BigDecimal.ZERO; // 订单行累计退货
        BigDecimal rc = BigDecimal.ZERO;     // 已退回替换货
        BigDecimal ra = BigDecimal.ZERO;     // 已退回原货
        BigDecimal h = BigDecimal.ZERO;      // 客户持有
        BigDecimal p = BigDecimal.ZERO;      // 待退回
        BigDecimal orderQty = BigDecimal.ZERO;
        BigDecimal replaceable = BigDecimal.ZERO;
        BigDecimal bUsed = BigDecimal.ZERO;  // B 路线已占用退货额度
    }

    private Ledger ledgerOf(Long orderId, Long orderItemId) {
        Ledger L = new Ledger();
        SaleOrderItem oi = saleOrderItemMapper.selectById(orderItemId);
        if (oi == null) throw new BusinessException("订单明细不存在");
        L.orderQty = nvl(oi.getQuantity());
        L.rTotal = nvl(oi.getTurnedReturnQuantity());

        L.d = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(i.quantity),0) FROM sale_delivery_item i "
                        + "JOIN sale_delivery h ON h.delivery_id = i.delivery_id "
                        + "WHERE i.source_item_id = ? AND h.status = '已生效' "
                        + "AND (h.source_bill_type IS NULL OR h.source_bill_type NOT LIKE '%销售换货单%')",
                BigDecimal.class, orderItemId);

        L.e = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(ei.quantity),0) FROM sale_exchange_item ei "
                        + "JOIN sale_exchange eh ON eh.exchange_id = ei.exchange_id "
                        + "WHERE ei.source_item_id = ? AND eh.status = '已生效'",
                BigDecimal.class, orderItemId);

        L.bUsed = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(ei.quantity),0) FROM sale_exchange_item ei "
                        + "JOIN sale_exchange eh ON eh.exchange_id = ei.exchange_id "
                        + "WHERE ei.source_item_id = ? AND eh.status = '已生效' AND ei.route_type = 'B'",
                BigDecimal.class, orderItemId);

        L.rc = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(ri.quantity),0) FROM sale_return_item ri "
                        + "JOIN sale_return rh ON rh.return_id = ri.return_id "
                        + "JOIN sale_delivery dh ON dh.delivery_no = ri.source_bill_no "
                        + "WHERE ri.source_item_id IN (SELECT di.delivery_item_id FROM sale_delivery_item di WHERE di.source_item_id = ?) "
                        + "AND rh.status = '已生效' AND dh.source_bill_type LIKE '%销售换货单%'",
                BigDecimal.class, orderItemId);

        L.d = nz(L.d); L.e = nz(L.e); L.rc = nz(L.rc);
        L.ra = L.rTotal.subtract(L.rc).max(BigDecimal.ZERO);
        L.h = L.d.subtract(L.ra).add(L.e.subtract(L.rc));
        L.p = L.e.min(L.d).subtract(L.ra).max(BigDecimal.ZERO);
        // 可替换额度 = min(订购数量, 已发货 + 已退回原货 + 已退回替换货 − 已换货)，且 >= 0
        // 口径说明：退回的货（原货或替换货）都释放了"再发一次"的额度；换出的替换货占用额度；
        // 上限锁订购数量，避免脏数据（退货多于发货）算出超过订购量的可替换值。
        BigDecimal quota = L.d.add(L.ra).add(L.rc).subtract(L.e);
        L.replaceable = L.orderQty.min(quota).max(BigDecimal.ZERO);
        return L;
    }

    // ==================== 弹窗数据 ====================

    @Override
    public List<Map<String, Object>> getReplaceableLines(Long orderId) {
        SaleOrder order = saleOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("销售订单不存在");
        List<SaleOrderItem> items = saleOrderItemMapper.selectList(
                new QueryWrapper<SaleOrderItem>().eq("order_id", orderId).orderByAsc("line_no"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SaleOrderItem oi : items) {
            Ledger L = ledgerOf(orderId, oi.getOrderItemId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderItemId", oi.getOrderItemId());
            m.put("lineNo", oi.getLineNo());
            m.put("materialId", oi.getMaterialId());
            m.put("materialCode", oi.getMaterialCode());
            m.put("materialName", oi.getMaterialName());
            m.put("specification", oi.getSpecification());
            m.put("unit", oi.getUnit());
            m.put("unitPrice", oi.getUnitPrice());
            m.put("taxRate", oi.getTaxRate());
            m.put("taxType", oi.getTaxType());
            m.put("discountRate", oi.getDiscountRate());
            m.put("orderQuantity", L.orderQty);
            m.put("deliveredQuantity", L.d);
            m.put("returnedQuantity", L.ra);
            m.put("substituteReturnedQuantity", L.rc);
            m.put("exchangedQuantity", L.e);
            m.put("customerHoldQuantity", L.h);
            m.put("pendingReturnQuantity", L.p);
            m.put("replaceableQuantity", L.replaceable);
            m.put("availableReturnQuota", L.ra.subtract(L.bUsed).max(BigDecimal.ZERO));
            // 默认替换货候选（替代清单）
            List<Map<String, Object>> subs = new ArrayList<>();
            if (oi.getMaterialId() != null) {
                List<MaterialSubstitute> list = materialSubstituteMapper.selectList(
                        new QueryWrapper<MaterialSubstitute>().eq("material_id", oi.getMaterialId())
                                .orderByAsc("sort_order").orderByAsc("id"));
                for (MaterialSubstitute s : list) {
                    Material sm = materialService.getById(s.getSubstituteMaterialId());
                    if (sm == null) continue;
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("materialId", sm.getMaterialId());
                    c.put("materialCode", sm.getMaterialCode());
                    c.put("materialName", sm.getMaterialName());
                    c.put("specification", sm.getModel());
                    c.put("baseUnitId", sm.getBaseUnitId());
                    c.put("mainImage", sm.getMainImage());
                    subs.add(c);
                }
            }
            m.put("substituteOptions", subs);
            rows.add(m);
        }
        return rows;
    }

    // ==================== 由订单生成换货单 ====================

    @Override
    @Transactional
    public SaleExchange createFromOrder(Long orderId, List<Map<String, Object>> lines, String operator) {
        SaleOrder order = saleOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("销售订单不存在");
        if (!"已生效".equals(order.getStatus())) throw new BusinessException("只有已生效的销售订单才能发起替代出库");
        String closedReason = order.getClosedReason();
        if ("手动结案".equals(closedReason)) {
            throw new BusinessException("订单已手动结案，如需换货请先反结案");
        }
        if (!hasEffectiveNormalDelivery(orderId)) {
            throw new BusinessException("该订单没有已生效的销售出库单，无法发起替代出库");
        }
        if (lines == null || lines.isEmpty()) throw new BusinessException("请至少选择一行需要替换的货品");

        SaleExchange bill = new SaleExchange();
        bill.setExchangeDate(LocalDate.now());
        bill.setCustomerId(order.getCustomerId());
        bill.setCustomerName(order.getCustomerName());
        bill.setContactPerson(order.getContactPerson());
        bill.setContactPhone(order.getContactPhone());
        bill.setSalerId(order.getSalerId());
        bill.setSalerName(order.getSalerName());
        bill.setCurrencyId(order.getCurrencyId());
        bill.setCurrencyCode(order.getCurrencyCode());
        bill.setExchangeRate(order.getExchangeRate());
        bill.setTaxCalcMethod(order.getTaxCalcMethod());
        bill.setSourceBillType("销售订单");
        bill.setSourceBillNo(order.getOrderNo());
        bill.setSourceId(order.getOrderId());
        bill.setSourceDocId(order.getOrderId());
        bill.setDeliveryAddress(order.getReceiveAddress());
        bill.setRemark("由销售订单 " + order.getOrderNo() + " 销售替代出库生成");
        bill.setStatus("草稿");
        bill.setApprovalStatus("草稿");
        bill.setCloseStatus(0);
        bill.setCreatedBy(operator);
        bill.setUpdatedBy(operator);

        List<SaleExchangeItem> items = new ArrayList<>();
        int lineNo = 1;
        for (Map<String, Object> ln : lines) {
            Long orderItemId = toLong(ln.get("orderItemId"));
            if (orderItemId == null) continue;
            BigDecimal qty = toDecimal(ln.get("quantity"));
            Long subMaterialId = toLong(ln.get("substituteMaterialId"));
            BigDecimal unitPrice = toDecimal(ln.get("unitPrice"));
            if (qty == null || qty.signum() <= 0) throw new BusinessException("替换数量必须大于 0");
            if (subMaterialId == null) throw new BusinessException("请选择替换货品");

            SaleOrderItem oi = saleOrderItemMapper.selectById(orderItemId);
            if (oi == null || !orderId.equals(oi.getOrderId())) throw new BusinessException("订单明细不存在");

            Ledger L = ledgerOf(orderId, orderItemId);
            if (qty.compareTo(L.replaceable) > 0) {
                throw new BusinessException("第" + oi.getLineNo() + "行替换数量 " + qty.stripTrailingZeros().toPlainString()
                        + " 超过可替换数量 " + L.replaceable.stripTrailingZeros().toPlainString());
            }

            Material sub = materialService.getById(subMaterialId);
            if (sub == null) throw new BusinessException("替换货品不存在");
            // 单位硬校验：基本单位必须一致（无法换算反写）
            Long originBaseUnitId = oi.getUnitId();
            Material originMat = oi.getMaterialId() == null ? null : materialService.getById(oi.getMaterialId());
            if (originMat != null && originMat.getBaseUnitId() != null && sub.getBaseUnitId() != null
                    && !originMat.getBaseUnitId().equals(sub.getBaseUnitId())) {
                throw new BusinessException("第" + oi.getLineNo() + "行替换货品与原货品基本单位不一致，无法回写原订单行");
            }
            if (oi.getMaterialId() != null && oi.getMaterialId().equals(subMaterialId)) {
                throw new BusinessException("第" + oi.getLineNo() + "行替换货品与原货品相同，无需换货");
            }
            // 若替换货不在替代清单内，允许（手动选择），仅记录日志
            boolean inList = materialSubstituteMapper.selectCount(new QueryWrapper<MaterialSubstitute>()
                    .eq("material_id", oi.getMaterialId()).eq("substitute_material_id", subMaterialId)) > 0;
            if (!inList) log.info("换货使用清单外货品: 原货品={}, 替换货品={}", oi.getMaterialCode(), sub.getMaterialCode());

            SaleExchangeItem it = new SaleExchangeItem();
            it.setOriginMaterialId(oi.getMaterialId());
            it.setOriginMaterialCode(oi.getMaterialCode());
            it.setOriginMaterialName(oi.getMaterialName());
            it.setOriginSpecification(oi.getSpecification());
            it.setOriginUnitId(originBaseUnitId);
            it.setOriginUnit(oi.getUnit());
            it.setOriginUnitPrice(oi.getUnitPrice());
            it.setMaterialId(sub.getMaterialId());
            it.setMaterialCode(sub.getMaterialCode());
            it.setMaterialName(sub.getMaterialName());
            it.setSpecification(sub.getModel());
            it.setUnitId(sub.getBaseUnitId());
            it.setUnit(oi.getUnit());
            it.setMaterialMainImage(sub.getMainImage());
            it.setQuantity(qty);
            it.setUnitPrice(unitPrice != null ? unitPrice : oi.getUnitPrice());
            it.setTaxRate(oi.getTaxRate());
            it.setTaxType(oi.getTaxType());
            it.setDiscountRate(oi.getDiscountRate());
            it.setSourceBillType("销售订单");
            it.setSourceBillNo(order.getOrderNo());
            it.setSourceLineNo(oi.getLineNo());
            it.setSourceItemId(oi.getOrderItemId());
            it.setSourceId(order.getOrderId());
            it.setSourceDocId(order.getOrderId());
            it.setSourceDeliveryNo(ln.get("sourceDeliveryNo") == null ? null : String.valueOf(ln.get("sourceDeliveryNo")));
            it.setSourceDeliveryId(toLong(ln.get("sourceDeliveryId")));
            it.setSourceDeliveryItemId(toLong(ln.get("sourceDeliveryItemId")));
            it.setReturnedQuantity(BigDecimal.ZERO);
            it.setSubstituteReturnedQuantity(BigDecimal.ZERO);
            it.setWaivedQuantity(BigDecimal.ZERO);
            it.setTurnedDeliveryQuantity(BigDecimal.ZERO);

            // 路线判定：有已退未补额度 → B；否则 A
            BigDecimal availableB = L.ra.subtract(L.bUsed).max(BigDecimal.ZERO);
            if (availableB.signum() > 0) {
                it.setRouteType("B");
                Long linkedReturnItemId = toLong(ln.get("linkedReturnItemId"));
                it.setLinkedReturnItemId(linkedReturnItemId);
                it.setLinkedReturnNo(ln.get("linkedReturnNo") == null ? null : String.valueOf(ln.get("linkedReturnNo")));
                it.setPendingReturnQuantity(BigDecimal.ZERO);
            } else {
                it.setRouteType("A");
                it.setPendingReturnQuantity(qty);
            }
            items.add(it);
            lineNo++;
        }
        if (items.isEmpty()) throw new BusinessException("请至少选择一行需要替换的货品");
        return saveExchangeWithItems(bill, items);
    }

    /** 保存时若明细未带路线（手工新增行），按当前账实补 A */
    private void fillRoute(SaleExchangeItem it, SaleExchange bill) {
        if (it.getSourceItemId() == null) return;
        Ledger L = ledgerOf(bill.getSourceId(), it.getSourceItemId());
        BigDecimal availableB = L.ra.subtract(L.bUsed).max(BigDecimal.ZERO);
        if (availableB.signum() > 0) {
            it.setRouteType("B");
            it.setPendingReturnQuantity(BigDecimal.ZERO);
        } else {
            it.setRouteType("A");
            it.setPendingReturnQuantity(nvl(it.getQuantity()));
        }
    }

    // ==================== 提交/审核 ====================

    @Override
    protected void validateStrictSubmit(SaleExchange bill) {
        List<SaleExchangeItem> items = itemMapper.selectList(
                new QueryWrapper<SaleExchangeItem>().eq("exchange_id", bill.getExchangeId()));
        if (items.isEmpty()) throw new BusinessException("销售换货单没有明细，请至少添加一行后再提交");

        // 按订单行汇总本次数量，再与可替换数量比对（同一行多行明细不重复放行）
        Map<Long, BigDecimal> sumByItem = new LinkedHashMap<>();
        for (SaleExchangeItem it : items) {
            if (it.getSourceItemId() == null) throw new BusinessException("换货明细缺少来源订单行，无法提交");
            if (it.getMaterialId() == null) throw new BusinessException("换货明细缺少替换货品");
            sumByItem.merge(it.getSourceItemId(), nvl(it.getQuantity()), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : sumByItem.entrySet()) {
            Ledger L = ledgerOf(bill.getSourceId(), e.getKey());
            // 本次这张单尚未生效，E 不含本单；可替换即为上限
            if (e.getValue().compareTo(L.replaceable) > 0) {
                throw new BusinessException("订单行（明细ID " + e.getKey() + "）本次替换数量 "
                        + e.getValue().stripTrailingZeros().toPlainString()
                        + " 超过可替换数量 " + L.replaceable.stripTrailingZeros().toPlainString()
                        + "，可替换数量已变化，请重新打开弹窗确认");
            }
        }
    }

    @Override
    @Transactional
    public void submitForApproval(Long id) {
        SaleExchange bill = getById(id);
        if (bill == null) throw new BusinessException("销售换货单不存在");
        validateStrictSubmit(bill);
        super.submitForApprovalById(id);
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        SaleExchange bill = getById(id);
        if (bill == null) throw new BusinessException("销售换货单不存在");
        validateStrictSubmit(bill);
        super.approve(id, approver);
    }

    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    /** 生效：自动生成销售出库单草稿（幂等，H2 硬卡） */
    @Override
    @Transactional
    public void onApproved(SaleExchange bill) {
        Long id = bill.getExchangeId();
        List<SaleExchangeItem> items = itemMapper.selectList(
                new QueryWrapper<SaleExchangeItem>().eq("exchange_id", id).orderByAsc("line_no"));
        if (items.isEmpty()) return;

        // 幂等：已生成且未作废则复用
        if (bill.getGeneratedDeliveryId() != null) {
            SaleDelivery exist = saleDeliveryService.getById(bill.getGeneratedDeliveryId());
            if (exist != null && !"已作废".equals(exist.getStatus())) {
                log.info("换货单 {} 已生成出库单 {}，跳过重复生成", bill.getExchangeNo(), exist.getDeliveryNo());
                return;
            }
        }

        SaleDelivery delivery = new SaleDelivery();
        delivery.setDeliveryNo(saleDeliveryService.generateDeliveryNo());
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setCustomerId(bill.getCustomerId());
        delivery.setContactPerson(bill.getContactPerson());
        delivery.setContactPhone(bill.getContactPhone());
        delivery.setSalerId(bill.getSalerId());
        delivery.setCurrencyId(bill.getCurrencyId());
        delivery.setExchangeRate(bill.getExchangeRate());
        delivery.setSourceBillType("销售换货单");
        delivery.setSourceBillNo(bill.getExchangeNo());
        delivery.setRemark("由销售换货单 " + bill.getExchangeNo() + " 生成，替换货品发货");
        delivery.setStatus("草稿");
        delivery.setApprovalStatus("草稿");
        delivery.setCloseStatus(0);
        delivery.setCreatedBy(bill.getApprovedBy());
        delivery.setUpdatedBy(bill.getApprovedBy());

        List<SaleDeliveryItem> dItems = new ArrayList<>();
        for (SaleExchangeItem it : items) {
            SaleDeliveryItem di = new SaleDeliveryItem();
            di.setMaterialId(it.getMaterialId());
            di.setMaterialCode(it.getMaterialCode());
            di.setMaterialName(it.getMaterialName());
            di.setSpecification(it.getSpecification());
            di.setUnitId(it.getUnitId());
            di.setUnit(it.getUnit());
            di.setQuantity(it.getQuantity());
            di.setUnitPrice(it.getUnitPrice());
            di.setTaxRate(it.getTaxRate());
            di.setTaxType(it.getTaxType());
            di.setDiscountRate(it.getDiscountRate());
            di.setDiscountAmount(it.getDiscountAmount());
            di.setAmount(it.getAmount());
            di.setTaxAmount(it.getTaxAmount());
            di.setNetAmount(it.getNetAmount());
            di.setPriceWithTax(it.getPriceWithTax());
            di.setPriceWithoutTax(it.getPriceWithoutTax());
            // 来源链：回写订单原货行的唯一锚点
            di.setSourceBillType("销售订单");
            di.setSourceBillNo(it.getSourceBillNo());
            di.setSourceLineNo(it.getSourceLineNo());
            di.setSourceItemId(it.getSourceItemId());
            di.setSourceId(it.getSourceId());
            di.setSourceDocId(it.getSourceDocId());
            di.setSourceType("sell");
            // 替换标记
            di.setReplacedFromMaterialId(it.getOriginMaterialId());
            di.setReplacedFromMaterialCode(it.getOriginMaterialCode());
            di.setReplacedFromMaterialName(it.getOriginMaterialName());
            dItems.add(di);
        }
        saleDeliveryService.saveDeliveryWithItems(delivery, dItems);

        bill.setGeneratedDeliveryId(delivery.getDeliveryId());
        bill.setGeneratedDeliveryNo(delivery.getDeliveryNo());
        updateById(bill);

        for (SaleExchangeItem it : items) {
            it.setTurnedDeliveryQuantity(nvl(it.getQuantity()));
            itemMapper.updateById(it);
        }
        log.info("销售换货单 {} 生效，已生成出库单 {}", bill.getExchangeNo(), delivery.getDeliveryNo());
    }

    /** 回退：生成的出库单必须未审核；未审核则删除草稿，释放额度 */
    @Override
    @Transactional
    public void onReverseApproved(SaleExchange bill) {
        releaseGeneratedDelivery(bill);
    }

    @Override
    @Transactional
    public void onVoided(SaleExchange bill) {
        releaseGeneratedDelivery(bill);
    }

    /** 反作废（恢复为已生效）：与审核严格镜像，重新生成替换货出库单并回写已转出库数量 */
    @Override
    @Transactional
    public void onUnvoided(SaleExchange bill) {
        onApproved(bill);
    }

    private void releaseGeneratedDelivery(SaleExchange bill) {
        if (bill.getGeneratedDeliveryId() == null) return;
        SaleDelivery delivery = saleDeliveryService.getById(bill.getGeneratedDeliveryId());
        if (delivery == null) return;
        if ("已生效".equals(delivery.getStatus())) {
            throw new BusinessException("该换货单生成的出库单 " + delivery.getDeliveryNo()
                    + " 已生效，请先重置草稿或作废该出库单后再操作");
        }
        // 草稿直接删除（先删明细再删表头，避免孤儿明细）
        saleDeliveryItemMapper.delete(new QueryWrapper<SaleDeliveryItem>()
                .eq("delivery_id", delivery.getDeliveryId()));
        saleDeliveryService.removeById(delivery.getDeliveryId());
        // MyBatis-Plus 默认忽略 null 更新：置空“已生成出库单”必须显式 set
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SaleExchange> uw =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        uw.eq("exchange_id", bill.getExchangeId())
                .set("generated_delivery_id", null)
                .set("generated_delivery_no", null)
                .set("updated_at", LocalDateTime.now());
        update(uw);
        bill.setGeneratedDeliveryId(null);
        bill.setGeneratedDeliveryNo(null);
        List<SaleExchangeItem> items = itemMapper.selectList(
                new QueryWrapper<SaleExchangeItem>().eq("exchange_id", bill.getExchangeId()));
        for (SaleExchangeItem it : items) {
            it.setTurnedDeliveryQuantity(BigDecimal.ZERO);
            itemMapper.updateById(it);
        }
    }

    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        SaleExchange bill = getById(id);
        if (bill == null) throw new BusinessException("单据不存在");
        checkGeneratedDeliveryBeforeReverse(bill);
        super.reverseApprove(id, operator);
    }

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        SaleExchange bill = getById(id);
        if (bill == null) throw new BusinessException("单据不存在");
        checkGeneratedDeliveryBeforeReverse(bill);
        super.voidBill(id, operator, reason);
    }

    private void checkGeneratedDeliveryBeforeReverse(SaleExchange bill) {
        if (bill.getGeneratedDeliveryId() == null) return;
        SaleDelivery delivery = saleDeliveryService.getById(bill.getGeneratedDeliveryId());
        if (delivery != null && "已生效".equals(delivery.getStatus())) {
            throw new BusinessException("该换货单生成的出库单 " + delivery.getDeliveryNo()
                    + " 已生效，请先重置草稿或作废该出库单");
        }
    }

    @Override
    @Transactional
    public void deleteBill(Long id) {
        SaleExchange bill = getById(id);
        if (bill == null) return;
        checkGeneratedDeliveryBeforeReverse(bill);
        // 删除前取整单快照（含明细与自定义字段值），供数据版本记录
        SaleExchange snapshot = getDetail(id);
        clearItemCustomFieldValues(id);
        try {
            customFieldService.deleteFieldValues("sale_exchange", id);
        } catch (Exception e) {
            log.warn("清理销售换货单表头自定义字段失败 id={}: {}", id, e.getMessage());
        }
        itemMapper.delete(new QueryWrapper<SaleExchangeItem>().eq("exchange_id", id));
        super.deleteBill(id);
        recordVersion("DELETE", snapshot, snapshot, null);
    }

    @Override
    public String generateExchangeNo() {
        return sysSerialNoConfigService.generateOrDefault("SALE_EXCHANGE", () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SEXC-" + dateStr + "-";
            QueryWrapper<SaleExchange> qw = new QueryWrapper<>();
            qw.likeRight("exchange_no", prefix).orderByDesc("exchange_no").last("LIMIT 1");
            SaleExchange last = getOne(qw);
            int seq = 1;
            if (last != null && last.getExchangeNo() != null) {
                try { seq = Integer.parseInt(last.getExchangeNo().substring(prefix.length())) + 1; }
                catch (Exception ignored) { }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    // ==================== 退货冲抵 ====================

    @Override
    @Transactional
    public void offsetReturn(Long orderId, List<SaleReturnItem> items, String operator) {
        if (items == null || items.isEmpty()) return;
        for (SaleReturnItem ri : items) {
            // 定位订单明细：退货可能来自普通出库（source_item_id=出库明细）或订单行
            Long orderItemId = resolveOrderItemId(ri);
            if (orderItemId == null) continue;
            boolean fromExchangeDelivery = isFromExchangeDelivery(ri);

            List<SaleExchangeItem> candidates = itemMapper.selectList(
                    new QueryWrapper<SaleExchangeItem>()
                            .eq("source_item_id", orderItemId)
                            .orderByAsc("created_at").orderByAsc("exchange_item_id"));
            BigDecimal remain = nvl(ri.getQuantity());
            if (remain.signum() <= 0) continue;

            if (fromExchangeDelivery) {
                // 退替换货：精确冲抵对应换货明细（linked 出库明细 → 换货明细）
                for (SaleExchangeItem ei : candidates) {
                    if (remain.signum() <= 0) break;
                    if (!"A".equals(ei.getRouteType()) && !"B".equals(ei.getRouteType())) continue;
                    BigDecimal capacity = nvl(ei.getQuantity()).subtract(nvl(ei.getSubstituteReturnedQuantity()));
                    if (capacity.signum() <= 0) continue;
                    BigDecimal apply = remain.min(capacity);
                    ei.setSubstituteReturnedQuantity(nvl(ei.getSubstituteReturnedQuantity()).add(apply));
                    itemMapper.updateById(ei);
                    remain = remain.subtract(apply);
                }
            } else {
                // 退原货：优先冲抵 A 路线待退回（先创建先冲）
                for (SaleExchangeItem ei : candidates) {
                    if (remain.signum() <= 0) break;
                    if (!"A".equals(ei.getRouteType())) continue;
                    BigDecimal pending = nvl(ei.getPendingReturnQuantity());
                    if (pending.signum() <= 0) continue;
                    BigDecimal apply = remain.min(pending);
                    ei.setReturnedQuantity(nvl(ei.getReturnedQuantity()).add(apply));
                    ei.setPendingReturnQuantity(pending.subtract(apply));
                    if (ei.getLinkedReturnItemId() == null) {
                        ei.setLinkedReturnItemId(ri.getReturnItemId());
                        ei.setLinkedReturnId(ri.getReturnId());
                        ei.setLinkedReturnNo(ri.getSourceBillNo());
                    }
                    itemMapper.updateById(ei);
                    remain = remain.subtract(apply);
                }
            }
        }
    }

    private Long resolveOrderItemId(SaleReturnItem ri) {
        String type = ri.getSourceBillType();
        if (type != null && (type.contains("销售出库") || type.contains("SaleDelivery"))) {
            if (ri.getSourceItemId() == null) return null;
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT source_item_id FROM sale_delivery_item WHERE delivery_item_id = ?",
                    Long.class, ri.getSourceItemId());
            return ids.isEmpty() ? null : ids.get(0);
        }
        return ri.getSourceItemId();
    }

    private boolean isFromExchangeDelivery(SaleReturnItem ri) {
        if (ri.getSourceBillNo() == null) return false;
        List<String> types = jdbcTemplate.queryForList(
                "SELECT source_bill_type FROM sale_delivery WHERE delivery_no = ?",
                String.class, ri.getSourceBillNo());
        return !types.isEmpty() && types.get(0) != null && types.get(0).contains("销售换货单");
    }

    // ==================== 结案放弃待退回 ====================

    @Override
    @Transactional
    public void waivePendingReturns(Long orderId, String operator, String reason) {
        List<SaleExchangeItem> items = itemMapper.selectList(
                new QueryWrapper<SaleExchangeItem>().eq("source_id", orderId).gt("pending_return_quantity", 0));
        for (SaleExchangeItem it : items) {
            BigDecimal pending = nvl(it.getPendingReturnQuantity());
            if (pending.signum() <= 0) continue;
            it.setWaivedQuantity(nvl(it.getWaivedQuantity()).add(pending));
            it.setPendingReturnQuantity(BigDecimal.ZERO);
            it.setWaivedBy(operator);
            it.setWaivedAt(LocalDateTime.now());
            it.setWaivedReason(reason);
            itemMapper.updateById(it);
        }
        if (!items.isEmpty()) {
            log.info("销售订单 {} 人工结案，放弃待退回 {} 行，操作人={}，原因={}",
                    orderId, items.size(), operator, reason);
        }
    }

    @Override
    public boolean hasEffectiveNormalDelivery(Long orderId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sale_delivery WHERE source_bill_no IN "
                        + "(SELECT order_no FROM sale_order WHERE order_id = ?) "
                        + "AND status = '已生效' "
                        + "AND (source_bill_type IS NULL OR source_bill_type NOT LIKE '%销售换货单%')",
                Integer.class, orderId);
        return c != null && c > 0;
    }

    @Override
    public boolean hasPendingReturn(Long orderId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sale_exchange_item WHERE source_id = ? AND pending_return_quantity > 0",
                Integer.class, orderId);
        return c != null && c > 0;
    }

    @Override
    public boolean hasPendingReturnForItem(Long orderItemId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sale_exchange_item WHERE source_item_id = ? AND pending_return_quantity > 0",
                Integer.class, orderItemId);
        return c != null && c > 0;
    }

    /**
     * 剩余可替换数量合计（订单页入口显隐用）。
     * 与弹窗「可替换」列同一口径（{@link #ledgerOf}），=0 即该订单已没有可替换额度，
     * 前端不再展示「销售替代出库」入口，避免点开后无可操作行造成"点击静默"。
     */
    @Override
    public BigDecimal sumReplaceableQuantity(Long orderId) {
        List<SaleOrderItem> items = saleOrderItemMapper.selectList(
                new QueryWrapper<SaleOrderItem>().eq("order_id", orderId));
        BigDecimal total = BigDecimal.ZERO;
        for (SaleOrderItem oi : items) {
            total = total.add(ledgerOf(orderId, oi.getOrderItemId()).replaceable);
        }
        return total;
    }

    @Override
    public List<Map<String, Object>> listExchangeInfoByOrder(Long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ei.source_item_id AS \"orderItemId\", ei.material_name AS \"materialName\","
                        + " ei.quantity AS \"quantity\", eh.exchange_no AS \"exchangeNo\","
                        + " ei.pending_return_quantity AS \"pendingReturn\""
                        + " FROM sale_exchange_item ei JOIN sale_exchange eh ON eh.exchange_id = ei.exchange_id"
                        + " WHERE ei.source_id = ? AND eh.status = '已生效'"
                        + " ORDER BY ei.source_item_id, eh.exchange_date, ei.exchange_item_id", orderId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            BigDecimal pend = nz(toDecimal(r.get("pendingReturn")));
            m.put("pendingReturn", pend);
            out.add(m);
        }
        return out;
    }

    // ==================== 工具 ====================

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return new BigDecimal(v.toString());
        try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}
