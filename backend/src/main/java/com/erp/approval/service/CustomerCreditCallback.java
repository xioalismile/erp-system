package com.erp.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.erp.sale.entity.SaleContract;
import com.erp.sale.service.SaleContractService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户授信一次性审批回调（billType = CUSTOMER_CREDIT，billId = customerId）。
 * <p>
 * 需求背景（2026-09-07）：
 *  - 月结客户首次提交销售合同时，系统自动发起「客户授信审批」替代逐单审批；
 *  - 审批通过 → 客户授信状态打标"已通过"，此后该客户所有销售合同免逐单审批（提交即生效）；
 *  - 审批驳回 → 客户授信状态"已驳回"，首单合同退回草稿，修改后重新提交可再次发起授信申请；
 *  - 审批历史永久留存于 approval_log / approval_task（与单据审批同机制）。
 *  - 误审批应急：客户付款分类 现结↔月结 切换一次 → 授信状态自动失效（CustomerServiceImpl.updateById 钩子），重新首单即重新发起授信。
 */
@Slf4j
@Service
public class CustomerCreditCallback implements BillApprovalCallback {

    /** 授信审批单据类型（billType），billId 存 customer_id，billNo 存 customer_code */
    public static final String BILL_TYPE = "CUSTOMER_CREDIT";

    private final CustomerService customerService;
    private final SaleContractService saleContractService;

    /** @Lazy 注入避免循环依赖：引擎构造注入 List<BillApprovalCallback> → 本 Bean → SaleContractService → ApprovalRuntimeService */
    public CustomerCreditCallback(CustomerService customerService,
                                  @Lazy SaleContractService saleContractService) {
        this.customerService = customerService;
        this.saleContractService = saleContractService;
    }

    @Override
    public String getBillType() {
        return BILL_TYPE;
    }

    /**
     * 授信审批通过：客户打标"已通过"，并将该客户因授信而挂起（待审核）的销售合同自动审核生效。
     */
    @Override
    @Transactional
    public void onApprovalApproved(Long customerId, String approver, String comment) {
        Customer customer = customerService.getById(customerId);
        if (customer == null) {
            log.warn("[客户授信] 审批通过但客户不存在，customerId={}", customerId);
            return;
        }
        customer.setCreditApprovalStatus(Customer.CREDIT_STATUS_APPROVED);
        customer.setCreditApprovedAt(LocalDateTime.now());
        customer.setCreditApprovedBy(approver);
        customerService.updateById(customer);

        // 首单合同（授信审批期间挂起的"待审核"销售合同）随授信通过一并生效
        List<SaleContract> pendings = saleContractService.list(new QueryWrapper<SaleContract>()
                .eq("customer_id", customerId)
                .in("status", "待审核")
                .in("approval_status", "待审核")
                .select("contract_id", "contract_no"));
        for (SaleContract c : pendings) {
            saleContractService.approve(c.getContractId(), approver);
            log.info("[客户授信] 授信通过，首单销售合同自动生效: {} 客户: {}", c.getContractNo(), customer.getCustomerName());
        }
        log.info("[客户授信] 审批通过: 客户 {} ({}), 审批人 {}", customer.getCustomerName(), customer.getCustomerCode(), approver);
    }

    /**
     * 授信审批驳回：客户授信状态置"已驳回"，挂起的首单销售合同退回草稿（可修改后重新提交再次发起授信）。
     */
    @Override
    @Transactional
    public void onApprovalRejected(Long customerId, String rejector, String comment) {
        Customer customer = customerService.getById(customerId);
        if (customer == null) {
            log.warn("[客户授信] 审批驳回但客户不存在，customerId={}", customerId);
            return;
        }
        UpdateWrapper<Customer> uw = new UpdateWrapper<>();
        uw.eq("customer_id", customerId).set("credit_approval_status", Customer.CREDIT_STATUS_REJECTED);
        customerService.update(uw);

        UpdateWrapper<SaleContract> contractUw = new UpdateWrapper<>();
        contractUw.eq("customer_id", customerId)
                .in("status", "待审核")
                .in("approval_status", "待审核")
                .set("status", "草稿")
                .set("approval_status", "驳回");
        saleContractService.update(contractUw);
        log.info("[客户授信] 审批驳回: 客户 {} ({}), 驳回人 {}", customer.getCustomerName(), customer.getCustomerCode(), rejector);
    }
}
