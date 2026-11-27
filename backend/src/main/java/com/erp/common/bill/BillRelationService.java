package com.erp.common.bill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.mapper.AccountsPayableMapper;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.inventory.entity.OtherIssue;
import com.erp.inventory.entity.OtherReceipt;
import com.erp.inventory.entity.StockTransfer;
import com.erp.inventory.mapper.MaterialRequisitionMapper;
import com.erp.inventory.mapper.OtherIssueMapper;
import com.erp.inventory.mapper.OtherReceiptMapper;
import com.erp.inventory.mapper.StockTransferMapper;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.WorkOrder;
import com.erp.production.mapper.OperationReportMapper;
import com.erp.production.mapper.ProductionReceiptMapper;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.mapper.PurchaseInboundMapper;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import com.erp.sale.entity.SaleContract;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleReturn;
import com.erp.sale.mapper.SaleContractMapper;
import com.erp.sale.mapper.SaleDeliveryMapper;
import com.erp.sale.mapper.SaleInvoiceMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import com.erp.sale.mapper.SaleReturnMapper;
import com.erp.subcontract.inbound.entity.SubcontractInbound;
import com.erp.subcontract.inbound.mapper.SubcontractInboundMapper;
import com.erp.subcontract.order.entity.SubcontractOrder;
import com.erp.subcontract.order.mapper.SubcontractOrderMapper;
import com.erp.subcontract.pick.entity.SubcontractPick;
import com.erp.subcontract.pick.mapper.SubcontractPickMapper;
import com.erp.subcontract.returnn.entity.SubcontractReturn;
import com.erp.subcontract.returnn.mapper.SubcontractReturnMapper;
import com.erp.subcontract.supplement.entity.SubcontractSupplement;
import com.erp.subcontract.supplement.mapper.SubcontractSupplementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 单据关联查询服务 — 供各ServiceImpl的 DownstreamBillValidator 使用。
 *
 * <p>核心能力：给定"来源单号"，反向扫描全部下游表，返回已生效（已生效类且未作废）的下游单据描述列表。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillRelationService {

    private final SaleOrderMapper saleOrderMapper;
    private final SaleContractMapper saleContractMapper;
    private final SaleDeliveryMapper saleDeliveryMapper;
    private final SaleInvoiceMapper saleInvoiceMapper;
    private final SaleReturnMapper saleReturnMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final OtherIssueMapper otherIssueMapper;
    private final OtherReceiptMapper otherReceiptMapper;
    private final StockTransferMapper stockTransferMapper;
    private final MaterialRequisitionMapper materialRequisitionMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ProductionReceiptMapper productionReceiptMapper;
    private final OperationReportMapper operationReportMapper;
    private final SubcontractOrderMapper subcontractOrderMapper;
    private final SubcontractInboundMapper subcontractInboundMapper;
    private final SubcontractPickMapper subcontractPickMapper;
    private final SubcontractSupplementMapper subcontractSupplementMapper;
    private final SubcontractReturnMapper subcontractReturnMapper;
    private final AccountsReceivableMapper arMapper;
    private final AccountsPayableMapper apMapper;

    /**
     * 查询一张来源单据的"已生效下游单据"列表（用于作废/重置草稿/删除前校验）。
     *
     * @param sourceBillType 来源单据类型（仅用于日志，内部实际按 sourceBillNo 匹配即可）
     * @param sourceBillNo   来源单号
     * @return 已生效下游单据的人类可读描述，如 ["销售出库单:SC20260801001","销售发票:INV-00001"]
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<String> listApprovedDownstream(String sourceBillType, String sourceBillNo) {
        List<String> result = new ArrayList<>();
        if (sourceBillNo == null || sourceBillNo.isBlank()) return result;

        try {
            // ---- 销售域下游 ----
            addIfApproved(result, "销售订单",
                    () -> saleOrderMapper.selectList(new QueryWrapper<SaleOrder>().eq("source_bill_no", sourceBillNo)),
                    SaleOrder::getOrderNo, SaleOrder::getStatus);

            // 销售合同（2026-11-09 补）：销售订单生效后可自动生成销售合同，生成后合同即订单的下游；
            // 漏登记会导致订单被"重置草稿"后合同仍生效，形成悬空关联。
            addIfApproved(result, "销售合同",
                    () -> saleContractMapper.selectList(new QueryWrapper<SaleContract>().eq("source_bill_no", sourceBillNo)),
                    SaleContract::getContractNo, SaleContract::getStatus);

            addIfApproved(result, "销售出库单",
                    () -> saleDeliveryMapper.selectList(new QueryWrapper<SaleDelivery>().eq("source_bill_no", sourceBillNo)),
                    SaleDelivery::getDeliveryNo, SaleDelivery::getStatus);

            addIfApproved(result, "销售发票",
                    () -> saleInvoiceMapper.selectList(new QueryWrapper<SaleInvoice>().eq("source_bill_no", sourceBillNo)),
                    SaleInvoice::getInvoiceNo, SaleInvoice::getStatus);

            addIfApproved(result, "销售退货单",
                    () -> saleReturnMapper.selectList(new QueryWrapper<SaleReturn>().eq("source_bill_no", sourceBillNo)),
                    SaleReturn::getReturnNo, SaleReturn::getStatus);

            // ---- 采购域下游 ----
            addIfApproved(result, "采购订单",
                    () -> purchaseOrderMapper.selectList(new QueryWrapper<PurchaseOrder>().eq("source_bill_no", sourceBillNo)),
                    PurchaseOrder::getOrderNo, PurchaseOrder::getStatus);

            addIfApproved(result, "采购入库单",
                    () -> purchaseInboundMapper.selectList(new QueryWrapper<PurchaseInbound>().eq("source_bill_no", sourceBillNo)),
                    PurchaseInbound::getReceiptNo, PurchaseInbound::getStatus);

            // ---- 库存域下游 ----
            addIfApproved(result, "其他出库单",
                    () -> otherIssueMapper.selectList(new QueryWrapper<OtherIssue>().eq("source_bill_no", sourceBillNo)),
                    OtherIssue::getIssueNo, OtherIssue::getStatus);

            addIfApproved(result, "其他入库单",
                    () -> otherReceiptMapper.selectList(new QueryWrapper<OtherReceipt>().eq("source_bill_no", sourceBillNo)),
                    OtherReceipt::getReceiptNo, OtherReceipt::getStatus);

            // 调拨单表头无来源列，来源记在明细 stock_transfer_item.source_bill_no（2026-09-11 更正）
            addIfApproved(result, "调拨单",
                    () -> stockTransferMapper.selectList(new QueryWrapper<StockTransfer>()
                            .apply("transfer_id in (select transfer_id from stock_transfer_item where source_bill_no = {0})", sourceBillNo)),
                    StockTransfer::getTransferNo, StockTransfer::getStatus);

            // ---- 生产/委外域下游 ----
            // 生产工单：下推时只写 source_no（+source_type），source_bill_no 从未写入，故必须按 source_no 匹配
            addIfApproved(result, "生产工单",
                    () -> workOrderMapper.selectList(new QueryWrapper<WorkOrder>().eq("source_no", sourceBillNo)),
                    WorkOrder::getOrderNo, WorkOrder::getStatus);

            addIfApproved(result, "生产入库单",
                    () -> productionReceiptMapper.selectList(new QueryWrapper<ProductionReceipt>().eq("source_bill_no", sourceBillNo)),
                    ProductionReceipt::getReceiptNo, ProductionReceipt::getStatus);

            // 生产工单的其余下游：生产领料单 / 工序汇报单
            // （两张表均在表头写 source_bill_no，与工单下推实现一致，已核实）
            addIfApproved(result, "生产领料单",
                    () -> materialRequisitionMapper.selectList(new QueryWrapper<MaterialRequisition>().eq("source_bill_no", sourceBillNo)),
                    MaterialRequisition::getRequisitionNo, MaterialRequisition::getStatus);

            addIfApproved(result, "工序汇报单",
                    () -> operationReportMapper.selectList(new QueryWrapper<OperationReport>().eq("source_bill_no", sourceBillNo)),
                    OperationReport::getReportNo, OperationReport::getStatus);

            addIfApproved(result, "委外订单",
                    () -> subcontractOrderMapper.selectList(new QueryWrapper<SubcontractOrder>().eq("source_bill_no", sourceBillNo)),
                    SubcontractOrder::getOrderNo, SubcontractOrder::getStatus);

            // 委外入库单：两种来源写法都要覆盖（2026-09-20 补齐第二条件）
            // ① 手工建的入库单选过委外单 → 来源记在明细 subcontract_inbound_item.source_bill_no（2026-09-11 更正）
            // ② 由委外加工单「下推生成」的入库单 → 只写表头 order_id/order_no，明细无 source_bill_no（pushInbound 实证）
            //    只判 ① 会漏掉 ②，导致下推出来的入库单拦不住上游作废/删除，故用 OR 叠加两个条件。
            addIfApproved(result, "委外入库单",
                    () -> subcontractInboundMapper.selectList(new QueryWrapper<SubcontractInbound>()
                            .apply("(id in (select inbound_id from subcontract_inbound_item where source_bill_no = {0})"
                                    + " or order_id in (select id from subcontract_order where order_no = {0}))",
                                    sourceBillNo, sourceBillNo)),
                    SubcontractInbound::getInboundNo, SubcontractInbound::getStatus);

            // 委外加工单的其余下游：委外发料单 / 委外补料单 / 委外退料单
            // （三张表表头均按 order_id 关联委外加工单，与 BillLinkRegistry 委外链路注册口径一致，已核实；
            //   状态列是 Integer：0草稿/1待审核/2已生效/3已出库(入库)/7已作废，其中 3 经 BillStatusUtils 归入"已生效类"）
            addIfApproved(result, "委外发料单",
                    () -> subcontractPickMapper.selectList(new QueryWrapper<SubcontractPick>()
                            .apply("order_id in (select id from subcontract_order where order_no = {0})", sourceBillNo)),
                    SubcontractPick::getPickNo, SubcontractPick::getStatus);

            addIfApproved(result, "委外补料单",
                    () -> subcontractSupplementMapper.selectList(new QueryWrapper<SubcontractSupplement>()
                            .apply("order_id in (select id from subcontract_order where order_no = {0})", sourceBillNo)),
                    SubcontractSupplement::getSupplementNo, SubcontractSupplement::getStatus);

            addIfApproved(result, "委外退料单",
                    () -> subcontractReturnMapper.selectList(new QueryWrapper<SubcontractReturn>()
                            .apply("order_id in (select id from subcontract_order where order_no = {0})", sourceBillNo)),
                    SubcontractReturn::getReturnNo, SubcontractReturn::getStatus);

            // ---- 财务应收/应付下游 ----
            addIfApproved(result, "应收账款单",
                    () -> arMapper.selectList(new QueryWrapper<AccountsReceivable>().eq("source_bill_no", sourceBillNo)),
                    AccountsReceivable::getArNo, AccountsReceivable::getStatus);

            addIfApproved(result, "应付账款单",
                    () -> apMapper.selectList(new QueryWrapper<AccountsPayable>().eq("source_bill_no", sourceBillNo)),
                    AccountsPayable::getApNo, AccountsPayable::getStatus);

        } catch (Exception e) {
            log.warn("扫描下游引用失败 sourceBillType={} sourceBillNo={}", sourceBillType, sourceBillNo, e);
        }
        return result;
    }

    private <T> void addIfApproved(List<String> result, String label,
                                   Supplier<List<T>> fetcher,
                                   Function<T, String> noGetter,
                                   Function<T, Object> statusGetter) {
        List<T> list;
        try { list = fetcher.get(); } catch (Exception e) {
            log.trace("{} mapper未就绪或字段缺失，跳过下游检查", label);
            return;
        }
        if (list == null) return;
        for (T b : list) {
            Object status = statusGetter.apply(b);
            if (BillStatusUtils.isApprovedClass(status) && !BillStatusUtils.isVoided(status)) {
                result.add(label + ":" + noGetter.apply(b));
            }
        }
    }
}
