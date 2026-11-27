package com.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.sale.entity.SaleContract;
import com.erp.sale.entity.SaleContractItem;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.service.SaleAutoPushService;
import com.erp.sale.service.SaleContractService;
import com.erp.sale.service.SaleOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动下推单据实现。
 *
 * <p>关键口径：
 * ① 自动生成的单据直接为「已生效」：先按草稿落库（含明细），再走统一状态机的审核动作，
 * 保证明细、金额、生效业务影响与人工建单完全一致；
 * ② 防死循环：自动生成的单据来源类型就是对方单据，再次生效时直接跳过；
 * ③ 防重复：目标单已存在同一来源单号时跳过；
 * ④ 失败不静默：生成或审核失败时抛业务异常，由源单生效事务整体回滚并给出中文原因。</p>
 */
@Slf4j
@Service
public class SaleAutoPushServiceImpl implements SaleAutoPushService {

    /** 设置中心开关：销售合同生效后自动生成销售订单 */
    public static final String CFG_CONTRACT_TO_ORDER = "sale.auto.push.contract.to.order";
    /** 设置中心开关：销售订单生效后自动生成销售合同 */
    public static final String CFG_ORDER_TO_CONTRACT = "sale.auto.push.order.to.contract";

    private static final String AUTO_APPROVER = "系统(自动下推)";
    private static final String SOURCE_CONTRACT = "销售合同";
    private static final String SOURCE_ORDER = "销售订单";

    @Autowired
    private SysConfigService sysConfigService;

    @Lazy
    @Autowired
    private SaleOrderService saleOrderService;

    @Lazy
    @Autowired
    private SaleContractService saleContractService;

    @Override
    @Transactional
    public void autoPushContractToOrder(SaleContract contract, String operator) {
        if (contract == null || contract.getContractId() == null) return;
        if (!sysConfigService.getBooleanValue(CFG_CONTRACT_TO_ORDER, false)) return;
        if (!isEffective(contract.getStatus())) return;
        String contractNo = contract.getContractNo();
        if (contractNo == null || contractNo.isBlank()) return;
        // 防死循环：自动生成的合同来源就是销售订单，不再反向生成订单
        if (cameFrom(contract.getSourceBillType(), SOURCE_ORDER)) {
            log.info("自动下推跳过：销售合同 {} 来源为销售订单，不反向生成销售订单", contractNo);
            return;
        }
        // 防重复：同一合同已生成过订单（任意状态）不再重复生成
        long exists = saleOrderService.count(new QueryWrapper<SaleOrder>().eq("source_bill_no", contractNo));
        if (exists > 0) {
            log.info("自动下推跳过：销售合同 {} 已存在来源销售订单，不重复生成", contractNo);
            return;
        }

        SaleContract detail = contract;
        if (detail.getItems() == null || detail.getItems().isEmpty()) {
            detail = saleContractService.getDetail(contract.getContractId());
        }
        List<SaleContractItem> contractItems = detail.getItems();
        if (contractItems == null || contractItems.isEmpty()) {
            throw new BusinessException("销售合同[" + contractNo + "]没有明细，无法自动生成销售订单");
        }

        SaleOrder order = buildOrder(detail);
        // 设置中心即使把销售订单设为手动编号，系统自动建单仍按同一编号规则取号，保证自动下推可用
        order.setOrderNo(saleOrderService.generateOrderNoForSystem());
        List<SaleOrderItem> orderItems = buildOrderItems(detail, contractItems);
        saleOrderService.saveOrderWithItems(order, orderItems);
        saleOrderService.approve(order.getOrderId(),
                operator != null && !operator.isBlank() ? operator : AUTO_APPROVER);
        log.info("自动下推成功：销售合同 {} 已生成销售订单 {}，状态已生效", contractNo, order.getOrderNo());
    }

    @Override
    @Transactional
    public void autoPushOrderToContract(SaleOrder order, String operator) {
        if (order == null || order.getOrderId() == null) return;
        if (!sysConfigService.getBooleanValue(CFG_ORDER_TO_CONTRACT, false)) return;
        if (!isEffective(order.getStatus())) return;
        String orderNo = order.getOrderNo();
        if (orderNo == null || orderNo.isBlank()) return;
        // 防死循环：自动生成的订单来源就是销售合同，不再反向生成合同
        if (cameFrom(order.getSourceBillType(), SOURCE_CONTRACT)) {
            log.info("自动下推跳过：销售订单 {} 来源为销售合同，不反向生成销售合同", orderNo);
            return;
        }
        // 防重复：同一订单已生成过合同（任意状态）不再重复生成
        long exists = saleContractService.count(new QueryWrapper<SaleContract>().eq("source_bill_no", orderNo));
        if (exists > 0) {
            log.info("自动下推跳过：销售订单 {} 已存在来源销售合同，不重复生成", orderNo);
            return;
        }

        SaleOrder detail = order;
        if (detail.getItems() == null || detail.getItems().isEmpty()) {
            detail = saleOrderService.getDetail(order.getOrderId());
        }
        List<SaleOrderItem> orderItems = detail.getItems();
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException("销售订单[" + orderNo + "]没有明细，无法自动生成销售合同");
        }

        SaleContract contract = buildContract(detail);
        // 设置中心即使把销售合同设为手动编号，系统自动建单仍按同一编号规则取号，保证自动下推可用
        contract.setContractNo(saleContractService.generateContractNoForSystem());
        List<SaleContractItem> contractItems = buildContractItems(detail, orderItems);
        saleContractService.saveContractWithItems(contract, contractItems);
        saleContractService.approve(contract.getContractId(),
                operator != null && !operator.isBlank() ? operator : AUTO_APPROVER);
        log.info("自动下推成功：销售订单 {} 已生成销售合同 {}，状态已生效", orderNo, contract.getContractNo());
    }

    private boolean isEffective(String status) {
        if (status == null) return false;
        String s = status.trim();
        return "已生效".equals(s) || "已审核".equals(s) || "APPROVED".equalsIgnoreCase(s);
    }

    private boolean cameFrom(String sourceBillType, String label) {
        return sourceBillType != null && sourceBillType.contains(label);
    }

    private SaleOrder buildOrder(SaleContract c) {
        SaleOrder o = new SaleOrder();
        o.setOrderDate(LocalDate.now());
        o.setCustomerId(c.getCustomerId());
        o.setCustomerNo(c.getCustomerNo());
        o.setCustomerShortName(c.getCustomerShortName());
        o.setContactPerson(c.getContactPerson());
        o.setContactPhone(c.getContactPhone());
        o.setSalerId(c.getSalerId());
        o.setDepartmentId(c.getDepartmentId());
        o.setPriceLevelId(c.getPriceLevelId());
        o.setCurrencyId(c.getCurrencyId());
        o.setExchangeRate(c.getExchangeRate());
        o.setDiscountRate(c.getDiscountRate());
        o.setDeliveryDate(c.getDeliveryDate());
        o.setPaymentTermId(c.getPaymentTermId());
        o.setPaymentMethod(c.getPaymentMethod());
        o.setDepositAmount(c.getDepositAmount());
        o.setWarehouseId(c.getWarehouseId());
        o.setReceiveAddress(c.getReceiveAddress());
        o.setTransportMethod(c.getTransportMethod());
        o.setIsTax(c.getIsTax());
        o.setTaxCalcMethod(c.getTaxCalcMethod());
        o.setIsInvoiced(c.getIsInvoiced());
        o.setIsConsignment(c.getIsConsignment());
        o.setPickerId(c.getPickerId());
        o.setProjectNo(c.getProjectNo());
        o.setCustomerOrderNo(c.getCustomerOrderNo());
        o.setOpportunityId(c.getOpportunityId());
        o.setOpportunityNo(c.getOpportunityNo());
        o.setRemark(autoRemark(SOURCE_CONTRACT, c.getContractNo(), c.getRemark()));
        o.setAttachmentUrls(c.getAttachmentUrls());
        o.setSourceBillType(SOURCE_CONTRACT);
        o.setSourceBillNo(c.getContractNo());
        return o;
    }

    private List<SaleOrderItem> buildOrderItems(SaleContract c, List<SaleContractItem> contractItems) {
        List<SaleOrderItem> list = new ArrayList<>();
        for (SaleContractItem ci : contractItems) {
            if (ci == null || ci.getMaterialId() == null) continue;
            SaleOrderItem oi = new SaleOrderItem();
            oi.setMaterialId(ci.getMaterialId());
            oi.setMaterialCode(ci.getMaterialCode());
            oi.setMaterialName(ci.getMaterialName());
            oi.setSpecification(ci.getSpecification());
            oi.setUnitId(ci.getUnitId());
            oi.setUnit(ci.getUnit());
            oi.setQuantity(ci.getQuantity());
            oi.setUnitPrice(ci.getUnitPrice());
            oi.setTaxRate(ci.getTaxRate());
            oi.setDiscountRate(ci.getDiscountRate());
            oi.setDeliveryDate(parseDate(ci.getDeliveryDate()));
            oi.setWarehouseId(ci.getWarehouseId() != null ? ci.getWarehouseId() : c.getWarehouseId());
            oi.setBomId(ci.getBomId());
            oi.setBomCode(ci.getBomCode());
            oi.setBomVersion(ci.getBomVersion());
            oi.setRemark(ci.getRemark());
            oi.setSourceBillType(SOURCE_CONTRACT);
            oi.setSourceBillNo(c.getContractNo());
            oi.setSourceLineNo(ci.getSortNo());
            oi.setSourceId(c.getContractId());
            oi.setSourceItemId(ci.getItemId());
            oi.setSourceDocId(c.getContractId());
            oi.setSourceType("sell");
            list.add(oi);
        }
        return list;
    }

    private SaleContract buildContract(SaleOrder o) {
        SaleContract c = new SaleContract();
        c.setContractDate(o.getOrderDate() != null ? o.getOrderDate() : LocalDate.now());
        c.setContractType(SOURCE_CONTRACT);
        c.setCustomerId(o.getCustomerId());
        c.setCustomerNo(o.getCustomerNo());
        c.setCustomerShortName(o.getCustomerShortName());
        c.setContactPerson(o.getContactPerson());
        c.setContactPhone(o.getContactPhone());
        c.setDepartmentId(o.getDepartmentId());
        c.setSalerId(o.getSalerId());
        c.setPriceLevelId(o.getPriceLevelId());
        c.setCurrencyId(o.getCurrencyId());
        c.setExchangeRate(o.getExchangeRate());
        c.setDiscountRate(o.getDiscountRate());
        c.setDeliveryDate(o.getDeliveryDate());
        c.setPaymentTermId(o.getPaymentTermId());
        c.setPaymentMethod(o.getPaymentMethod());
        c.setDepositAmount(o.getDepositAmount());
        c.setWarehouseId(o.getWarehouseId());
        c.setReceiveAddress(o.getReceiveAddress());
        c.setTransportMethod(o.getTransportMethod());
        c.setIsTax(o.getIsTax());
        c.setTaxCalcMethod(o.getTaxCalcMethod());
        c.setIsInvoiced(o.getIsInvoiced());
        c.setIsConsignment(o.getIsConsignment());
        c.setPickerId(o.getPickerId());
        c.setProjectNo(o.getProjectNo());
        c.setCustomerOrderNo(o.getCustomerOrderNo());
        c.setOpportunityId(o.getOpportunityId());
        c.setOpportunityNo(o.getOpportunityNo());
        c.setRemark(autoRemark(SOURCE_ORDER, o.getOrderNo(), o.getRemark()));
        c.setAttachmentUrls(o.getAttachmentUrls());
        c.setSourceBillType(SOURCE_ORDER);
        c.setSourceBillNo(o.getOrderNo());
        return c;
    }

    private List<SaleContractItem> buildContractItems(SaleOrder o, List<SaleOrderItem> orderItems) {
        List<SaleContractItem> list = new ArrayList<>();
        for (SaleOrderItem oi : orderItems) {
            if (oi == null || oi.getMaterialId() == null) continue;
            SaleContractItem ci = new SaleContractItem();
            ci.setMaterialId(oi.getMaterialId());
            ci.setMaterialCode(oi.getMaterialCode());
            ci.setMaterialName(oi.getMaterialName());
            ci.setSpecification(oi.getSpecification());
            ci.setUnitId(oi.getUnitId());
            ci.setUnit(oi.getUnit());
            ci.setQuantity(oi.getQuantity());
            ci.setUnitPrice(oi.getUnitPrice());
            ci.setTaxRate(oi.getTaxRate());
            ci.setDiscountRate(oi.getDiscountRate());
            ci.setDeliveryDate(oi.getDeliveryDate() != null ? oi.getDeliveryDate().toString() : null);
            ci.setWarehouseId(oi.getWarehouseId() != null ? oi.getWarehouseId() : o.getWarehouseId());
            ci.setBomId(oi.getBomId());
            ci.setBomCode(oi.getBomCode());
            ci.setBomVersion(oi.getBomVersion());
            ci.setRemark(oi.getRemark());
            ci.setSourceBillType(SOURCE_ORDER);
            ci.setSourceBillNo(o.getOrderNo());
            ci.setSourceDocId(o.getOrderId());
            list.add(ci);
        }
        return list;
    }

    private String autoRemark(String sourceLabel, String sourceNo, String originRemark) {
        String base = "由" + sourceLabel + " " + sourceNo + " 自动下推生成";
        if (originRemark != null && !originRemark.isBlank()) {
            base = base + " | " + originRemark.trim();
        }
        return base;
    }

    private LocalDate parseDate(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }
}
