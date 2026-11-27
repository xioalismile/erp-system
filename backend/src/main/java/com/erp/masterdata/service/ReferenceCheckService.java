package com.erp.masterdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 主档引用校验服务：删除客户/供应商前检查是否被业务数据引用
 * 防止删除后单据出现悬空引用（对账报表名称/编码空白的根因）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceCheckService {

    private final JdbcTemplate jdbcTemplate;

    /** 客户引用表清单：{表名, 中文名} */
    private static final List<String[]> CUSTOMER_REF_TABLES = List.of(
            new String[]{"sale_quote", "销售报价单"},
            new String[]{"sale_contract", "销售合同"},
            new String[]{"sale_order", "销售订单"},
            new String[]{"sale_delivery", "销售出库单"},
            new String[]{"sale_return", "销售退货单"},
            new String[]{"sale_invoice", "销售发票"},
            new String[]{"account_receivable", "应收单"},
            new String[]{"cash_receipt", "收款单"},
            new String[]{"sale_expense", "销售费用单"},
            new String[]{"sale_pre_receipt", "销售预收单"},
            new String[]{"sale_statement", "销售对账单"},
            new String[]{"crm_opportunity", "商机"},
            new String[]{"crm_contact", "联系人"},
            new String[]{"crm_survey", "客户调查"},
            new String[]{"crm_complaint", "客户投诉"},
            new String[]{"sample_order", "打样单"},
            new String[]{"free_sample_order", "免费样品订单"},
            new String[]{"credit_limit", "信用额度"}
    );

    /** 供应商引用表清单：{表名, 中文名} */
    private static final List<String[]> SUPPLIER_REF_TABLES = List.of(
            new String[]{"purchase_contract", "采购合同"},
            new String[]{"purchase_order", "采购订单"},
            new String[]{"purchase_receipt", "采购入库单"},
            new String[]{"purchase_return", "采购退货单"},
            new String[]{"purchase_invoice", "采购发票"},
            new String[]{"account_payable", "应付单"},
            new String[]{"cash_payment", "付款单"},
            new String[]{"purchase_expense", "采购费用单"},
            new String[]{"purchase_pre_payment", "采购预付单"},
            new String[]{"purchase_statement", "采购对账单"},
            new String[]{"subcontract_order", "委外订单"},
            new String[]{"subcontract_inbound", "委外入库单"},
            new String[]{"subcontract_supplement", "委外补料单"},
            new String[]{"subcontract_return", "委外退料单"},
            new String[]{"subcontract_pick", "委外领料单"},
            new String[]{"fixed_asset", "固定资产"},
            new String[]{"work_center", "工作中心"},
            new String[]{"batch", "库存批次"},
            new String[]{"batch_info", "批次台账"},
            new String[]{"serial_number", "序列号"}
    );

    /**
     * 物料引用表清单：{表名, 中文名}
     * <p>
     * 口径：物料一旦被任何业务单据 / 库存 / 成本 / BOM / 价格策略引用，就禁止删除（如需停用请走【停用】）。
     * <p>
     * 2026-09-19 用户报障根因之一：物料主档被删除 + 整批重导换 id 后，库存流水 / 即时库存的
     * 物料编码永久空白（台账只存 material_id，实时 join 补不回）→ 从源头拦住"还有库存/单据引用的物料"。
     * <p>
     * 不列入本清单：{@code inventory_warning_log}（预警日志）、{@code doc_trace}（单据追溯，派生数据）——
     * 纯日志/派生表不构成阻塞删除的业务理由。
     */
    private static final List<String[]> MATERIAL_REF_TABLES = List.of(
            // 库存（本次问题的直接来源：有现存量 / 有流水的物料绝不允许删）
            new String[]{"inventory_current", "即时库存"},
            new String[]{"inventory_ledger", "库存流水"},
            new String[]{"batch", "库存批次"},
            new String[]{"batch_info", "批次台账"},
            new String[]{"batch_inventory", "批次库存"},
            new String[]{"serial_number", "序列号"},
            new String[]{"inventory_warning_rule", "库存预警规则"},
            // 基础资料
            new String[]{"bom", "BOM清单"},
            new String[]{"bom_item", "BOM明细"},
            new String[]{"routing", "工艺路线"},
            new String[]{"sale_price_strategy_item", "销售价格策略"},
            new String[]{"purchase_price_strategy_item", "采购价格策略"},
            // 销售
            new String[]{"sale_quote_item", "报价单明细"},
            new String[]{"sale_contract_item", "销售合同明细"},
            new String[]{"sale_order_item", "销售订单明细"},
            new String[]{"sale_delivery_item", "销售出库单明细"},
            new String[]{"sale_return_item", "销售退货单明细"},
            new String[]{"sale_invoice_item", "销售发票明细"},
            new String[]{"sale_expense_item", "销售费用单明细"},
            // 采购
            new String[]{"purchase_request_item", "采购申请明细"},
            new String[]{"purchase_order_item", "采购订单明细"},
            new String[]{"purchase_contract_item", "采购合同明细"},
            new String[]{"purchase_receipt_item", "采购入库单明细"},
            new String[]{"purchase_return_item", "采购退货单明细"},
            new String[]{"purchase_invoice_item", "采购发票明细"},
            new String[]{"purchase_expense_item", "采购费用单明细"},
            // 库存单据
            new String[]{"other_receipt_item", "其他入库单明细"},
            new String[]{"other_issue_item", "其他出库单明细"},
            new String[]{"stock_transfer_item", "库存调拨单明细"},
            new String[]{"stock_check_item", "库存盘点单明细"},
            new String[]{"material_requisition_item", "生产领料单明细"},
            // 生产 / 委外
            new String[]{"production_order", "生产工单"},
            new String[]{"production_order_item", "生产工单原料明细"},
            new String[]{"work_order_product", "生产工单成品明细"},
            new String[]{"production_receipt_item", "生产入库单明细"},
            new String[]{"subcontract_order", "委外订单"},
            new String[]{"subcontract_order_item", "委外订单明细"},
            new String[]{"subcontract_pick_item", "委外发料单明细"},
            new String[]{"subcontract_inbound_item", "委外入库单明细"},
            new String[]{"subcontract_return_item", "委外退货单明细"},
            new String[]{"subcontract_supplement_item", "委外补料单明细"},
            // 打样 / MRP / 成本
            new String[]{"sample_order", "打样单"},
            new String[]{"mrp_plan_item", "MRP方案明细"},
            new String[]{"cost_calculation", "成本核算"},
            new String[]{"cost_calculation_item", "成本核算明细"}
    );

    /** 物料"子件"引用表：打样单的子件用 child_material_id 列（不是 material_id） */
    private static final List<String[]> MATERIAL_CHILD_REF_TABLES = List.<String[]>of(
            new String[]{"sample_order_item", "打样单子件"}
    );

    /**
     * 检查客户是否被引用
     * @return null=无引用可删；否则返回拦截提示信息
     */
    public String checkCustomer(Long customerId) {
        List<String> hits = countReferences(CUSTOMER_REF_TABLES, "customer_id", customerId);
        if (hits.isEmpty()) return null;
        return "客户已被业务数据引用，无法删除：" + String.join("、", hits) + "；如不再使用可改为【停用】";
    }

    /**
     * 检查供应商是否被引用
     * @return null=无引用可删；否则返回拦截提示信息
     */
    public String checkSupplier(Long supplierId) {
        List<String> hits = countReferences(SUPPLIER_REF_TABLES, "supplier_id", supplierId);
        if (hits.isEmpty()) return null;
        return "供应商已被业务数据引用，无法删除：" + String.join("、", hits) + "；如不再使用可改为【停用】";
    }

    /**
     * 检查物料是否被引用（2026-09-19 新增）
     * <p>
     * 背景：物料主档被删除 + 整批重导换 id 后，库存流水/即时库存的物料编码会永久空白
     * （台账只存 material_id，实时 join 补不回）。故与客户/供应商同口径：被引用即禁止删除，
     * 需要下架请改用【停用】。
     *
     * @return null=无引用可删；否则返回拦截提示信息（业务语言，含引用表与条数）
     */
    public String checkMaterial(Long materialId) {
        List<String> hits = countReferences(MATERIAL_REF_TABLES, "material_id", materialId);
        hits.addAll(countReferences(MATERIAL_CHILD_REF_TABLES, "child_material_id", materialId));
        if (hits.isEmpty()) return null;
        return "物料已被业务数据引用，无法删除：" + String.join("、", hits) + "；如不再使用可改为【停用】";
    }

    /** 逐表 COUNT 引用数，返回有引用的表描述（如 "销售订单(3)"）；单表查询失败跳过不阻塞 */
    private List<String> countReferences(List<String[]> tables, String column, Long id) {
        List<String> hits = new ArrayList<>();
        for (String[] t : tables) {
            try {
                Long cnt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + t[0] + " WHERE " + column + " = ?", Long.class, id);
                if (cnt != null && cnt > 0) {
                    hits.add(t[1] + "(" + cnt + "条)");
                }
            } catch (Exception e) {
                // 表在某些环境可能不存在（如裁剪版库），跳过
                log.warn("引用校验跳过表 {}：{}", t[0], e.getMessage());
            }
        }
        return hits;
    }
}
