package com.erp.dashboard.service;

import com.erp.approval.entity.ApprovalTask;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.config.BusinessException;
import com.erp.dashboard.dto.ApproveResultDTO;
import com.erp.inventory.service.StockCheckService;
import com.erp.inventory.service.StockTransferService;
import com.erp.production.service.OperationReportService;
import com.erp.production.service.ProductionReceiptService;
import com.erp.purchase.service.*;
import com.erp.sale.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一审批服务 - 供工作台调用
 * 按单据类型(type)路由到对应模块的审批接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillApprovalService {

    private final ApprovalRuntimeService approvalRuntimeService;
    private final SaleOrderService saleOrderService;
    private final SaleDeliveryService saleDeliveryService;
    private final SaleReturnService saleReturnService;
    private final SaleInvoiceService saleInvoiceService;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseInboundService purchaseInboundService;
    private final PurchaseReturnService purchaseReturnService;
    private final PurchaseInvoiceService purchaseInvoiceService;
    private final StockTransferService stockTransferService;
    private final StockCheckService stockCheckService;
    private final OperationReportService operationReportService;
    private final ProductionReceiptService productionReceiptService;

    /**
     * 审批单条单据
     * @param type 单据类型(对应 MyTodoDTO.type)
     * @param id    单据ID
     * @param approver 审批人用户名
     * @return 审批结果
     */
    public ApproveResultDTO approve(String type, Long id, String approver) {
        if (type == null || id == null) {
            throw new BusinessException("类型或ID不能为空");
        }
        try {
            // 单据已进入审批流程（存在运行中任务）→ 必须走审批引擎，
            // 由引擎完成身份校验、多节点推进、流程终结后回调回写单据状态，
            // 避免直审绕过引擎导致任务永久挂起
            ApprovalTask runningTask = approvalRuntimeService.getRunningTask(type, id);
            if (runningTask != null) {
                approvalRuntimeService.approve(runningTask.getTaskId(), approver, "工作台审批通过");
                return new ApproveResultDTO(true, id, type, null);
            }
            switch (type) {
                case "SALE_ORDER": saleOrderService.approve(id, approver); break;
                case "SALE_DELIVERY": saleDeliveryService.approve(id, approver); break;
                case "SALE_RETURN": saleReturnService.approve(id, approver); break;
                case "SALE_INVOICE": saleInvoiceService.approve(id, approver); break;
                case "PURCHASE_ORDER": purchaseOrderService.approve(id, approver); break;
                case "PURCHASE_INBOUND": purchaseInboundService.approve(id, approver); break;
                case "PURCHASE_RETURN": purchaseReturnService.approve(id, approver); break;
                case "PURCHASE_INVOICE": purchaseInvoiceService.approve(id, approver); break;
                case "STOCK_TRANSFER": stockTransferService.approve(id, approver); break;
                case "STOCK_CHECK": stockCheckService.approve(id, approver); break;
                case "OPERATION_REPORT": operationReportService.approve(id, approver); break;
                case "PRODUCTION_RECEIPT": productionReceiptService.approve(id, approver); break;
                default: throw new BusinessException("不支持的单据类型: " + type);
            }
            return new ApproveResultDTO(true, id, type, null);
        } catch (Exception e) {
            log.error("审批失败 type={} id={} approver={}", type, id, approver, e);
            return new ApproveResultDTO(false, id, type, e.getMessage());
        }
    }

    /**
     * 审批驳回（2026-11-19 新增：与 approve 对齐，供工作台卡片/居中弹窗直接驳回）
     * 有运行中审批任务 → 走审批引擎驳回（身份校验、任务完结、回调退回草稿）；
     * 无任务 → 走对应单据服务的直驳（仅待审核状态可驳）。
     * @param type 单据类型(对应 MyTodoDTO.type)
     * @param id   单据ID
     * @param approver 驳回人用户名
     * @param comment 驳回原因
     */
    public ApproveResultDTO reject(String type, Long id, String approver, String comment) {
        if (type == null || id == null) {
            throw new BusinessException("类型或ID不能为空");
        }
        String reason = (comment == null || comment.isBlank()) ? "工作台驳回" : comment;
        try {
            ApprovalTask runningTask = approvalRuntimeService.getRunningTask(type, id);
            if (runningTask != null) {
                approvalRuntimeService.reject(runningTask.getTaskId(), approver, reason);
                return new ApproveResultDTO(true, id, type, null);
            }
            switch (type) {
                case "SALE_ORDER": saleOrderService.reject(id, approver, reason); break;
                case "SALE_DELIVERY": saleDeliveryService.reject(id, approver, reason); break;
                case "SALE_RETURN": saleReturnService.reject(id, approver, reason); break;
                case "SALE_INVOICE": saleInvoiceService.reject(id, approver, reason); break;
                case "PURCHASE_ORDER": purchaseOrderService.reject(id, approver, reason); break;
                case "PURCHASE_INBOUND": purchaseInboundService.reject(id, approver, reason); break;
                case "PURCHASE_RETURN": purchaseReturnService.reject(id, approver, reason); break;
                case "PURCHASE_INVOICE": purchaseInvoiceService.reject(id, approver, reason); break;
                case "STOCK_TRANSFER": stockTransferService.reject(id, approver, reason); break;
                case "STOCK_CHECK": stockCheckService.reject(id, approver, reason); break;
                case "OPERATION_REPORT": operationReportService.reject(id, approver, reason); break;
                case "PRODUCTION_RECEIPT": productionReceiptService.reject(id, approver, reason); break;
                default: throw new BusinessException("不支持的单据类型: " + type);
            }
            return new ApproveResultDTO(true, id, type, null);
        } catch (Exception e) {
            log.error("审批驳回失败 type={} id={} approver={}", type, id, approver, e);
            return new ApproveResultDTO(false, id, type, e.getMessage());
        }
    }

    /**
     * 当前用户是否为该单据的审批人（2026-11-19 弹窗/编辑页按钮可见性依据）。
     * 无运行中审批任务 = 未接入审批流或直审场景 → 返回 true（审核/驳回按钮正常显示）；
     * 有任务 → 按当前节点指定审批人/any候选人判断（与 approve/reject 的引擎校验同口径）。
     */
    public boolean isBillApprover(String type, Long id, String username) {
        if (type == null || id == null) return true;
        try {
            ApprovalTask runningTask = approvalRuntimeService.getRunningTask(type, id);
            if (runningTask == null) return true;
            return approvalRuntimeService.isTaskApprover(runningTask, username);
        } catch (Exception e) {
            log.warn("判断审批人失败 type={} id={} approver={}", type, id, username, e);
            return true; // 查询失败走保守显示，引擎仍会兜底校验
        }
    }

    /**
     * 批量审批（一键审批）
     * @param items 待审批列表 [{type, id}, ...]
     * @param approver 审批人
     * @return 每条审批结果
     */
    public List<ApproveResultDTO> batchApprove(List<ApproveRequest> items, String approver) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        List<ApproveResultDTO> results = new ArrayList<>();
        for (ApproveRequest it : items) {
            results.add(approve(it.getType(), it.getId(), approver));
        }
        return results;
    }

    /** 单条审批请求体 */
    public static class ApproveRequest {
        private String type;
        private Long id;
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}
