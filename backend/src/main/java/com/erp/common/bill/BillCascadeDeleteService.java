package com.erp.common.bill;

import com.erp.config.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单据「整单删除」级联服务（2026-09-19 新增）。
 *
 * <p><b>背景（用户硬要求）</b>：删除单据必须**一整单删干净**（主表 + 明细 + 属于本单的从表），
 * 不允许出现"删了主表、明细留一堆"的情况。改造前 `AbstractBillService.deleteBill` 只做
 * {@code removeById} 主表，各覆写版本（报价/合同/退货/发票/调拨/盘点/领用/收付款…）同样是裸删主表，
 * 结果库里会残留孤儿明细（用户实测删除后遗留明细行）。</p>
 *
 * <p><b>口径</b></p>
 * <ul>
 *   <li>只删**属于这张单的从表**（明细/BOM行/分摊行等），**不删**下游业务单据（下游是"禁止删除"的判断依据）；
 *       审批记录/操作日志属于审计留痕，也不删。</li>
 *   <li>删除**过账单据**（regNo 登记的单据编号列）时，按编号同步清理其全部库存流水行（含红冲行）——
 *       2026-11-16 用户硬要求「已删除的单据不在库存流水表记录」；非过账单据不涉及。</li>
 *   <li>与主表删除同事务（调用方已 {@code @Transactional}），任一步异常都会整体回滚，不留半截数据。</li>
 *   <li>登记以**数据库真实表名**为准（本次逐表核对了外键列），不依赖实体类名。</li>
 *   <li>未登记的主表会打 WARN 日志（不阻断），防止将来新增单据漏登记却悄悄产生孤儿明细。</li>
 * </ul>
 *
 * <p><b>维护约定</b>：新增任何带明细的单据时，必须在本类 {@code REGISTRY} 补一行登记；
 * **会写库存流水的单据必须用 {@link #regNo} 登记单据编号列**（漏登记 = 删除后残留流水孤儿行）；
 * 否则可用 {@link #orphanReport()} 体检出来（设置中心/排障脚本可用）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillCascadeDeleteService {

    private final JdbcTemplate jdbcTemplate;

    /** 从表：表名 + 指向主表的列 + 中文名（日志用）+ 可选判别列（同表被多种单据共用时限定归属，如核销明细的 bill_type） */
    private record Child(String table, String fkColumn, String label, String discColumn, String discValue) {
    }

    /** 主表：表名 + 主键列（**仅作注释参考**，实际主键列由数据库元数据动态解析）+ 单据编号列（过账单据必登记）+ 从表清单 */
    private record Main(String table, String pkColumn, String noColumn, List<Child> children) {
    }

    /** 登记表：主表名 → 主表信息（主键 + 从表清单） */
    private static final Map<String, Main> REGISTRY = new LinkedHashMap<>();

    private static Child c(String table, String fkColumn, String label) {
        return new Child(table, fkColumn, label, null, null);
    }

    /** 带判别列的从表登记：同一张从表被多种单据共用（如 bill_writeoff_detail 的 bill_type 区分收/付款单），
     *  删除主表时只清理属于本单据类型的行，避免误删兄弟单据的行。 */
    private static Child cDisc(String table, String fkColumn, String label, String discColumn, String discValue) {
        return new Child(table, fkColumn, label, discColumn, discValue);
    }

    private static void reg(String table, String pkColumn, Child... children) {
        REGISTRY.put(table, new Main(table, pkColumn, null, List.of(children)));
    }

    /**
     * 过账单据登记（会写库存流水的单据必须用本方法登记单据编号列）。
     * 2026-11-16 用户硬要求：已删除的单据不在库存流水表记录 —— 删除单据时按编号清理其全部流水行（含红冲行）。
     * 编号列名以实体字段驼峰转下划线为准（逐一核对过实体），登记错列名会导致清理静默失效。
     */
    private static void regNo(String table, String pkColumn, String noColumn, Child... children) {
        REGISTRY.put(table, new Main(table, pkColumn, noColumn, List.of(children)));
    }

    static {
        // ===== 销售 =====
        reg("sale_order", "order_id", c("sale_order_item", "order_id", "订单明细"));
        reg("sale_quote", "quote_id", c("sale_quote_item", "quote_id", "报价明细"));
        reg("sale_contract", "contract_id", c("sale_contract_item", "contract_id", "合同明细"));
        regNo("sale_delivery", "delivery_id", "delivery_no", c("sale_delivery_item", "delivery_id", "出库明细"));
        regNo("sale_return", "return_id", "return_no", c("sale_return_item", "return_id", "退货明细"));
        reg("sale_invoice", "invoice_id", c("sale_invoice_item", "invoice_id", "发票明细"));
        // 替换货 / 售后维修（2026-11-19 补登记）：主表删除时一并清理明细与配件领用行
        reg("sale_exchange", "exchange_id", c("sale_exchange_item", "exchange_id", "换货明细"));
        reg("sale_repair", "repair_id",
                c("sale_repair_item", "repair_id", "维修明细"),
                c("sale_repair_part", "repair_id", "维修配件领用"));
        reg("sale_pre_receipt", "pre_receipt_id", c("sale_pre_receipt_item", "pre_receipt_id", "预收款明细"));
        reg("sale_expense", "expense_id", c("sale_expense_item", "expense_id", "销售费用明细"));
        reg("sale_price_strategy", "strategy_id", c("sale_price_strategy_item", "strategy_id", "价格策略明细"));

        // ===== 采购 =====
        reg("purchase_request", "request_id", c("purchase_request_item", "request_id", "申请明细"));
        reg("purchase_order", "order_id", c("purchase_order_item", "order_id", "订单明细"));
        reg("purchase_contract", "contract_id", c("purchase_contract_item", "contract_id", "合同明细"));
        // 注意：采购入库单主表是 purchase_receipt（实体 PurchaseInbound），明细表 purchase_receipt_item
        regNo("purchase_receipt", "receipt_id", "receipt_no", c("purchase_receipt_item", "receipt_id", "入库明细"));
        regNo("purchase_return", "return_id", "return_no", c("purchase_return_item", "return_id", "退货明细"));
        reg("purchase_invoice", "invoice_id", c("purchase_invoice_item", "invoice_id", "发票明细"));
        reg("purchase_pre_payment", "pre_payment_id", c("purchase_pre_payment_item", "pre_payment_id", "预付款明细"));
        reg("purchase_expense", "expense_id", c("purchase_expense_item", "expense_id", "采购费用明细"));
        reg("purchase_price_strategy", "strategy_id", c("purchase_price_strategy_item", "strategy_id", "价格策略明细"));

        // ===== 库存（5 张全部过账）=====
        regNo("other_issue", "issue_id", "issue_no", c("other_issue_item", "issue_id", "出库明细"));
        regNo("other_receipt", "receipt_id", "receipt_no", c("other_receipt_item", "receipt_id", "入库明细"));
        regNo("material_requisition", "requisition_id", "requisition_no", c("material_requisition_item", "requisition_id", "领料明细"));
        regNo("stock_transfer", "transfer_id", "transfer_no", c("stock_transfer_item", "transfer_id", "调拨明细"));
        regNo("stock_check", "check_id", "check_no", c("stock_check_item", "check_id", "盘点明细"));

        // ===== 生产（工单主表 production_order，另有产品/工序两张从表）=====
        reg("production_order", "order_id",
                c("production_order_item", "order_id", "用料明细"),
                c("work_order_product", "order_id", "工单产品"),
                c("work_order_operation", "order_id", "工单工序"));
        // 作业汇报：报表本体一行一单，没有从表；登记后删除不再告警"疑似漏登记"
        reg("operation_report", "report_id");
        regNo("production_receipt", "receipt_id", "receipt_no", c("production_receipt_item", "receipt_id", "入库明细"));
        regNo("produce_assemble", "assemble_id", "serial_no",
                c("produce_assemble_detail", "assemble_id", "组装明细"),
                c("produce_assemble_bom", "assemble_id", "组装BOM行"));
        regNo("produce_disassemble", "disassemble_id", "serial_no",
                c("produce_disassemble_detail", "disassemble_id", "拆卸明细"),
                c("produce_disassemble_bom", "disassemble_id", "拆卸BOM行"));

        // ===== 委外（加工单本体不过账；发料/入库/退料/补料 4 张过账）=====
        reg("subcontract_order", "order_id", c("subcontract_order_item", "order_id", "加工明细"));
        regNo("subcontract_pick", "pick_id", "pick_no", c("subcontract_pick_item", "pick_id", "领料明细"));
        regNo("subcontract_inbound", "inbound_id", "inbound_no", c("subcontract_inbound_item", "inbound_id", "入库明细"));
        regNo("subcontract_return", "return_id", "return_no", c("subcontract_return_item", "return_id", "退货明细"));
        regNo("subcontract_supplement", "supplement_id", "supplement_no", c("subcontract_supplement_item", "supplement_id", "补料明细"));

        // ===== 基础资料 =====
        reg("bom", "bom_id", c("bom_item", "bom_id", "BOM明细"));

        // ===== 财务 =====
        // 收/付款单的「核销明细」必须级联（2026-10-19 补登记）：bill_writeoff_detail 由收/付款单共用
        // （bill_type 区分），此前漏登记 → 删除草稿收/付款单后残留未冲销核销行，
        // 订单「收款详情」出现指向已删除收款单的幽灵记录、列表"已收"却为 0（121 生产实锤）。
        reg("cash_receipt", "receipt_id",
                c("cash_receipt_item", "receipt_id", "收款明细"),
                cDisc("bill_writeoff_detail", "bill_id", "收付款核销明细", "bill_type", "收款单"));
        reg("cash_payment", "payment_id",
                c("cash_payment_item", "payment_id", "付款明细"),
                cDisc("bill_writeoff_detail", "bill_id", "收付款核销明细", "bill_type", "付款单"));
        reg("operation_expense", "expense_id", c("operation_expense_allocation", "expense_id", "费用分摊明细"));
        reg("cost_calculation", "calc_id", c("cost_calculation_item", "calc_id", "成本核算明细"));
        reg("fund_transfer", "transfer_id", c("fund_transfer_item", "transfer_id", "资金调拨明细"));
        reg("budget", "budget_id", c("budget_item", "budget_id", "预算明细"));
        reg("sale_statement", "statement_id", c("sale_statement_item", "statement_id", "销售对账单明细"));
        reg("purchase_statement", "statement_id", c("purchase_statement_item", "statement_id", "采购对账单明细"));

        // ===== 打样 / 样品 =====
        reg("sample_order", "sample_id", c("sample_order_item", "sample_id", "打样明细"));
        reg("free_sample_order", "order_id", c("free_sample_order_item", "order_id", "免费样品明细"));

        // ===== 计划 =====
        reg("mrp_plan", "plan_id", c("mrp_plan_item", "plan_id", "MRP明细"));
    }

    /**
     * 按主实体类删从表（读取实体的 {@code @TableName}）。
     * 单据 Service 删除主表前调用一次即可。
     *
     * @return 删除的明细行数合计
     */
    @Transactional
    public int deleteChildrenByEntity(Class<?> entityClass, Object billId) {
        String table = resolveTableName(entityClass);
        if (table == null) {
            log.warn("[整单删除] 实体 {} 无 @TableName，跳过级联删除（id={}）", entityClass.getSimpleName(), billId);
            return 0;
        }
        return deleteChildren(table, billId);
    }

    /**
     * 按主表名删从表。
     *
     * @param mainTable 主表名（如 sale_order）
     * @param billId    主键值
     * @return 删除的明细行数合计
     */
    @Transactional
    public int deleteChildren(String mainTable, Object billId) {
        if (mainTable == null || billId == null) {
            return 0;
        }
        Main main = REGISTRY.get(mainTable);
        if (main == null) {
            log.warn("[整单删除] 主表 {} 未登记从表，本次不会级联清理明细（疑似漏登记，请在 BillCascadeDeleteService.REGISTRY 补一行）：id={}",
                    mainTable, billId);
            return 0;
        }
        // 删除单据时同步清理该单据的库存流水（2026-11-16 用户硬要求：已删除的单据不在库存流水表记录）。
        // 过账单据（regNo 登记）按"单据编号列"读出本单编号，再按编号清流水（正向过账行与红冲行同编号）；
        // 必须在读到编号后、主表删除前执行（本方法返回后调用方才删主表）。清理失败必须如实抛出，与主表删除同事务整体回滚，禁止吞掉留孤儿流水。
        if (main.noColumn() != null) {
            String billNo = null;
            try {
                billNo = jdbcTemplate.queryForObject(
                        "SELECT " + main.noColumn() + " FROM " + main.table() + " WHERE " + resolvePkColumn(main.table()) + " = ?",
                        String.class, billId);
                if (billNo != null && !billNo.isBlank()) {
                    int flowRows = jdbcTemplate.update("DELETE FROM inventory_ledger WHERE trans_no = ?", billNo);
                    if (flowRows > 0) {
                        log.info("[整单删除] {} id={} → 已清理库存流水 {} 行（单据编号：{}）", mainTable, billId, flowRows, billNo);
                    }
                }
            } catch (Exception e) {
                log.warn("[整单删除] 清理库存流水失败 {}（id={}）: {}", mainTable, billId, e.getMessage());
                throw new BusinessException("删除失败：清理库存流水失败（单号 "
                        + (billNo != null && !billNo.isBlank() ? billNo : String.valueOf(billId)) + "）：" + e.getMessage());
            }
        }
        int total = 0;
        for (Child ch : main.children()) {
            try {
                int rows = (ch.discColumn() == null)
                        ? jdbcTemplate.update("DELETE FROM " + ch.table() + " WHERE " + ch.fkColumn() + " = ?", billId)
                        : jdbcTemplate.update("DELETE FROM " + ch.table() + " WHERE " + ch.fkColumn() + " = ? AND "
                                + ch.discColumn() + " = ?", billId, ch.discValue());
                if (rows > 0) {
                    total += rows;
                    log.info("[整单删除] {} id={} → 已清理 {}（{}）{} 行", mainTable, billId, ch.label(), ch.table(), rows);
                }
            } catch (Exception e) {
                // 从表清理失败必须如实抛出，与主表删除同事务整体回滚，禁止吞掉留孤儿明细（留痕后抛出便于排障）
                log.warn("[整单删除] 清理从表失败 {}（{}）: {}", ch.table(), ch.label(), e.getMessage());
                throw new BusinessException("删除失败：清理" + ch.label() + "失败：" + e.getMessage());
            }
        }
        return total;
    }

    /** 该主表是否已登记从表 */
    public boolean isRegistered(String mainTable) {
        return REGISTRY.containsKey(mainTable);
    }

    /** 已登记的主表清单（排障/体检用） */
    public Set<String> registeredMainTables() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /**
     * 孤儿明细体检：返回"主表 → 明细表"的孤儿行数文案（仅统计已登记的）。
     * 用于验证"整单删除"是否彻底，以及排查历史遗留脏数据。
     *
     * <p>主键列名**从数据库元数据动态解析**（不依赖登记里的手写值）：
     * 2026-09-19 实测发现多处主表主键并非语义化的 xxx_id（如 sale_pre_receipt / cost_calculation /
     * subcontract_order 等主键就叫 {@code id}），手写登记值不可靠，故改为查 information_schema。</p>
     */
    public List<String> orphanReport() {
        List<String> rows = new ArrayList<>();
        REGISTRY.forEach((mainTable, main) -> {
            String pkColumn = resolvePkColumn(mainTable);
            for (Child ch : main.children()) {
                try {
                    String sql = "SELECT COUNT(*) FROM " + ch.table() + " i LEFT JOIN " + main.table() + " m ON i."
                            + ch.fkColumn() + " = m." + pkColumn
                            + " WHERE i." + ch.fkColumn() + " IS NOT NULL AND m." + pkColumn + " IS NULL"
                            + (ch.discColumn() == null ? "" : " AND i." + ch.discColumn() + " = ?");
                    Long n = (ch.discColumn() == null)
                            ? jdbcTemplate.queryForObject(sql, Long.class)
                            : jdbcTemplate.queryForObject(sql, Long.class, ch.discValue());
                    if (n != null && n > 0) {
                        rows.add(mainTable + " → " + ch.table() + (ch.discColumn() != null ? "(" + ch.discColumn() + "=" + ch.discValue() + ")" : "") + "：" + n + " 行");
                    }
                } catch (Exception e) {
                    log.debug("[整单删除体检] 跳过 {}: {}", ch.table(), e.getMessage());
                }
            }
        });
        return rows;
    }

    /** 主键列名缓存（表名 → 主键列），避免每轮体检重复查元数据 */
    private final Map<String, String> pkColumnCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** 从数据库元数据解析主表主键列名（解析失败兜底 "id"） */
    private String resolvePkColumn(String table) {
        return pkColumnCache.computeIfAbsent(table, t -> {
            try {
                String pk = jdbcTemplate.queryForObject(
                        "SELECT kcu.column_name FROM information_schema.table_constraints tc "
                                + "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name "
                                + "WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_schema = 'public' "
                                + "AND tc.table_name = ? LIMIT 1",
                        String.class, t);
                return (pk != null && !pk.isBlank()) ? pk : "id";
            } catch (Exception e) {
                log.warn("[整单删除体检] 解析主表 {} 主键失败，兜底 id: {}", t, e.getMessage());
                return "id";
            }
        });
    }

    /** 读实体 @TableName */
    private String resolveTableName(Class<?> entityClass) {
        try {
            com.baomidou.mybatisplus.annotation.TableName ann =
                    entityClass.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class);
            if (ann != null && !ann.value().isBlank()) {
                return ann.value();
            }
        } catch (Exception e) {
            log.debug("[整单删除] 解析 @TableName 失败: {}", e.getMessage());
        }
        return null;
    }
}
