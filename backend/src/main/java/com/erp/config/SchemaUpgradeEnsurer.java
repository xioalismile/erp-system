package com.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动时兜底：把最近 schema 升级里新增的列全部 "确保存在"。
 * <p>
 * 过去踩坑（8开头服务器）：
 * 1. old erp_db.sys_config 缺 config_section/config_type/config_options → Kuaidi100Service.init() 抛错把 ApplicationContext 拉垮，502。
 * 2. old erp_db.sys_menu   缺 version/features → 切租户后 fetchUserinfo 查菜单挂，前端「正在进入」一直 loading。
 * 3. old erp_platform.sys_tenant 缺 version/features/db_config → 平台客户管理后台创建/查询租户挂。
 * <p>
 * 这段代码在 {@link com.erp.config.service.SysConfigServiceImpl} 的列兜底之外，
 * 再额外覆盖 sys_menu 和平台库 sys_tenant。所有操作均用 information_schema 检测，幂等。
 * 以后再有新增列：只往下面两个方法里加一行，部署到老库不会再炸启动 / 炸登录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaUpgradeEnsurer {

    @Qualifier("jdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    /** 平台库连接（sys_tenant 登记表）：租户库巡检时用它取库名与连接串。 */
    @Qualifier("platformJdbcTemplate")
    private final JdbcTemplate platformJdbcTemplate;

    /** 平台库账号同步（2026-10-12）：租户库缺平台库记录的账号由启动自愈补齐，否则登录"查无此人"。 */
    private final com.erp.system.service.PlatformUserSyncService platformUserSyncService;

    /**
     * 客户包收敛 · 待隐藏菜单（2026-10-12）：逗号分隔的菜单编码。
     * 独立部署客户（如 121）不需要「电商平台」「字段名称设置」等新功能入口，
     * 在服务器外置 application.yml 配置该清单 → 每次启动强制 is_visible=0（主库 + 全部租户库）。
     * 空值 = 全量包行为不变。配置示例：
     * erp.customer-package.hide-menu-codes: ecommerce,ecommerce_shop,ecommerce_orders,ecommerce_mapping,system_field_name_settings
     */
    @Value("${erp.customer-package.hide-menu-codes:}")
    private String customerHideMenuCodes;

    /**
     * 客户包收敛 · 关闭开账引导（2026-10-12）：true 时把
     * 「小楷按行业初始化」开关置 false、「开账引导状态」置 done（主库 + 全部租户库，幂等强收敛）。
     * 独立部署客户不做新租户系统自动配置。
     */
    @Value("${erp.customer-package.disable-onboarding:false}")
    private boolean customerDisableOnboarding;

    /** 业务库补列唯一清单（主库与每个租户独立库共用同一份，杜绝两处清单漂移）。 */
    private static final java.util.List<String[]> BUSINESS_COLUMN_SPECS = java.util.List.of(
        // 销售订单收款情况（2026-10-12）：收款单两条路径回写订单 + 明细/汇总收款。
        // ① 订单明细行级已收金额；② 应收单来源订单号（发货收款回写链路）；③ 核销明细来源明细行。
        // 等价脚本：deploy/sql/151_receipt_writeback_to_order.sql（含存量应收单来源订单号回填）。
        new String[]{"sale_order_item", "received_amount", "NUMERIC(18,2) NOT NULL DEFAULT 0.00"},
        // 销售订单完结口径可配置（2026-10-14）：明细行「已开票金额」，发票生效 +net_amount、重置草稿 -net_amount 双向维护；
        // 「销售发票 / 三者全完成」完结模式按它判定。等价脚本：deploy/sql/159_sale_order_close_mode_setting.sql（含存量回填）。
        new String[]{"sale_order_item", "turned_invoice_amount", "NUMERIC(18,2) NOT NULL DEFAULT 0.00"},
        new String[]{"account_receivable", "source_order_no", "VARCHAR(50)"},
        new String[]{"bill_writeoff_detail", "source_item_id", "BIGINT"},
        new String[]{"bill_writeoff_detail", "source_item_name", "VARCHAR(200)"},
        // 收款单销售提成（2026-10-16）：表头提成四列 + 核销明细销售员/行提成四列。
        // 等价脚本：deploy/sql/162_receipt_commission.sql；参数兜底见 ensureReceiptCommissionSettings。
        new String[]{"cash_receipt", "commission_rate", "NUMERIC(9,4)"},
        new String[]{"cash_receipt", "special_service_fee", "NUMERIC(18,2) DEFAULT 0"},
        new String[]{"cash_receipt", "tax_excluded_received_amount", "NUMERIC(18,2)"},
        // 替换货（2026-11-17）：出库明细替换标记 + 回写前成本快照。等价脚本：deploy/sql/192_sale_exchange.sql
        new String[]{"sale_delivery_item", "replaced_from_material_id", "BIGINT"},
        new String[]{"sale_delivery_item", "replaced_from_material_code", "VARCHAR(64)"},
        new String[]{"sale_delivery_item", "replaced_from_material_name", "VARCHAR(255)"},
        new String[]{"sale_delivery_item", "order_cost_before", "NUMERIC(18,4)"},
        // 替换发货指示（2026-11-27）：出库行关联销售预指定指示；仓库直接替代为空。
        // 等价脚本：deploy/sql/201_sale_substitute_plan.sql
        new String[]{"sale_delivery_item", "replace_plan_id", "BIGINT"},
        // 售后维修（2026-11-18）：货品档案保修期（月），空按 12 个月。等价脚本：deploy/sql/193_sale_repair.sql
        new String[]{"material", "warranty_months", "INT"},
        new String[]{"sale_repair", "transferred_bill_id", "BIGINT"},
        new String[]{"cash_receipt", "sales_commission", "NUMERIC(18,2)"},
        // 收款单来源（设计图 v3 第二批）：系统自动生成 / 手工新增。带默认值 → 存量单据自动回填为手工新增
        new String[]{"cash_receipt", "source_type", "VARCHAR(20) DEFAULT '手工新增'"},
        // 发货管控下放单据（2026-10-19）：发货收款管控在源头销售订单上选定，出库单随单带出只读展示；
        // 订单另带超期账款管控开关。等价脚本：deploy/sql/168_delivery_control_on_bill.sql。
        new String[]{"sale_order", "payment_control", "VARCHAR(20)"},
        new String[]{"sale_order", "overdue_block", "VARCHAR(10)"},
        new String[]{"sale_delivery", "payment_control", "VARCHAR(20)"},
        new String[]{"bill_writeoff_detail", "saler_id", "BIGINT"},
        new String[]{"bill_writeoff_detail", "saler_name", "VARCHAR(100)"},
        new String[]{"bill_writeoff_detail", "tax_excluded_amount", "NUMERIC(18,2)"},
        new String[]{"bill_writeoff_detail", "commission_rate", "NUMERIC(9,4)"},
        new String[]{"bill_writeoff_detail", "special_service_fee", "NUMERIC(18,2) DEFAULT 0"},
        new String[]{"bill_writeoff_detail", "commission", "NUMERIC(18,2)"},
        // 销售/采购价格策略改造（2026-10-10）：适用范围互斥单选（target_type）+ 明细对象化（target_type/target_id）。
        // 电商平台接入拼多多（2026-10-11）：店铺档案加访问令牌两列、平台订单加平台列。
        // 等价脚本：deploy/sql/73_ecommerce_pdd.sql。
        new String[]{"ecom_shop", "access_token", "VARCHAR(1000)"},
        new String[]{"ecom_shop", "token_expire_at", "TIMESTAMP"},
        new String[]{"ecom_platform_order", "platform", "VARCHAR(20)"},
        // 等价脚本：db/146_price_strategy_target.sql、deploy/sql/146_price_strategy_target.sql。
        // 除补列外，主表/明细表的「放开非空 + 存量回填 + 优先级重排 + 唯一索引」见 ensurePriceStrategyTargetsOn。
        new String[]{"sale_price_strategy", "target_type", "VARCHAR(20) DEFAULT 'material'"},
        new String[]{"purchase_price_strategy", "target_type", "VARCHAR(20) DEFAULT 'material'"},
        new String[]{"sale_price_strategy_item", "target_type", "VARCHAR(20) DEFAULT 'material'"},
        new String[]{"sale_price_strategy_item", "target_id", "BIGINT"},
        new String[]{"purchase_price_strategy_item", "target_type", "VARCHAR(20) DEFAULT 'material'"},
        new String[]{"purchase_price_strategy_item", "target_id", "BIGINT"},
        new String[]{"purchase_price_strategy_item", "price_type", "VARCHAR(20) DEFAULT 'fixed'"},
        new String[]{"purchase_price_strategy_item", "cost_price", "NUMERIC(18,4) DEFAULT 0"},
        new String[]{"purchase_price_strategy_item", "gross_profit_rate", "NUMERIC(18,4) DEFAULT 0"},
        // 数量区间从主表下沉到明细行（2026-10-10 追加）：一行一个对象、每个对象自己的数量区间
        new String[]{"sale_price_strategy_item", "min_quantity", "NUMERIC(18,4)"},
        new String[]{"sale_price_strategy_item", "max_quantity", "NUMERIC(18,4)"},
        new String[]{"purchase_price_strategy_item", "min_quantity", "NUMERIC(18,4)"},
        new String[]{"purchase_price_strategy_item", "max_quantity", "NUMERIC(18,4)"},
        // 生产入库单附件列（2026-09-20 实锤：主库/模板库/租户库都不存在，此前注释误判为"已存在"）
        new String[]{"production_receipt", "attachment_urls", "VARCHAR(1000)"},
        // 第 11 期（2026-09-28）：生产入库单记录「来源工序行」，入库类型工序一键发起入库单时写入（按工序控制剩余可入库量）
        new String[]{"production_receipt", "source_operation_id", "BIGINT"},
        // 2026-11-06：入库单明细行级记录来源工序（一张入库单可对应多道入库工序；已入库量按行汇总）
        new String[]{"production_receipt_item", "source_operation_id", "BIGINT"},
        // 付款条件改造（2026-09-28）：客户/供应商档案的账期天数 —— 应收/应付到期日按"付款条件账期 → 档案账期 → 沿用原行为"取值
        new String[]{"customer", "credit_days", "INTEGER"},
        new String[]{"supplier", "credit_days", "INTEGER"},
        // 基础资料联动补列（2026-11-08）：客户/供应商默认税率、供应商默认付款方式、报价单客户编号。
        // 等价脚本：deploy/sql/188_master_linkage_fields.sql（同脚本含存量简称与编号回填）。
        new String[]{"customer", "tax_rate", "NUMERIC(5,2)"},
        new String[]{"supplier", "tax_rate", "NUMERIC(5,2)"},
        new String[]{"supplier", "payment_method", "VARCHAR(50)"},
        new String[]{"sale_quote", "customer_no", "VARCHAR(64)"},
        // 车间工序可视化 第1期（2026-09-23）：工序类型 + 计件单价 + 工单工序扩展字段
        new String[]{"routing_operation", "operation_type", "VARCHAR(20)"},
        new String[]{"routing_operation", "piece_rate", "NUMERIC(18,4)"},
        new String[]{"work_order_operation", "operation_type", "VARCHAR(20)"},
        new String[]{"work_order_operation", "assignee_id", "BIGINT"},
        new String[]{"work_order_operation", "assignee_name", "VARCHAR(50)"},
        new String[]{"work_order_operation", "input_quantity", "NUMERIC(18,4)"},
        new String[]{"work_order_operation", "output_quantity", "NUMERIC(18,4)"},
        new String[]{"work_order_operation", "piece_rate", "NUMERIC(18,4)"},
        // 车间工序可视化 第4期（2026-09-27）：回退确认 / 判废确认 / 一键下推补料
        new String[]{"work_order_operation", "rework_quantity", "NUMERIC(18,4)"},
        new String[]{"work_order_operation", "scrap_quantity", "NUMERIC(18,4)"},
        new String[]{"work_order_operation", "scrap_pushed_quantity", "NUMERIC(18,4)"},
        new String[]{"work_order_operation", "supplement_bill_no", "VARCHAR(64)"},
        // 第10期（2026-09-27）：工序按「成品明细行」绑定（一张工单多个商品，每个成品各自一套工序）
        new String[]{"work_order_product", "routing_id", "BIGINT"},
        new String[]{"work_order_product", "routing_version", "VARCHAR(50)"},
        new String[]{"work_order_operation", "order_product_id", "BIGINT"},
        // 2026-11-10：原料明细「归属成品行」改为独立非主键列（此前借主键承载归属，两表主键值域重合会撞主键）。
        // 等价脚本：deploy/sql/190_production_order_item_product_id.sql。
        new String[]{"production_order_item", "order_product_id", "BIGINT"},
        // 第13期（2026-09-28）：工艺路线「自动通知下道工序负责人」+「按顺序执行」两个开关，默认 0=关
        new String[]{"routing", "auto_notify_next", "SMALLINT NOT NULL DEFAULT 0"},
        new String[]{"routing", "enforce_sequence", "SMALLINT NOT NULL DEFAULT 0"},
        // 销售过程动作量化（2026-10-07）：跟进记录加 3 列 —— 回访与深入交流两条日任务的判据
        new String[]{"crm_follow_up", "call_duration_seconds", "INTEGER"},
        new String[]{"crm_follow_up", "chat_message_count", "INTEGER"},
        new String[]{"crm_follow_up", "is_deep_communication", "SMALLINT NOT NULL DEFAULT 0"},
        // 销售过程动作量化（2026-10-07）：客户加 1 列 —— wechat_set_at「微信首次由空变非空的时间」。
        // 等价脚本：db/134_customer_wechat_intent_fields.sql、deploy/sql/134_customer_wechat_intent_fields.sql。
        new String[]{"customer", "wechat_set_at", "TIMESTAMP"},
        // 单据「计税方式」与含税/不含税价（2026-10-08 · 销售订单试点）：
        // 明细「含税价 / 不含税价」两列，按表头「计税方式」由「单价」换算落库。
        // 等价脚本：db/138_price_tax_type.sql、deploy/sql/138_price_tax_type.sql。
        new String[]{"sale_order_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"sale_order_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"sys_config", "config_section", "VARCHAR(100)"},
        new String[]{"sys_config", "config_type", "VARCHAR(20)"},
        new String[]{"sys_config", "config_options", "TEXT"},
        new String[]{"sys_menu", "version", "VARCHAR(50)"},
        new String[]{"sys_menu", "features", "JSONB"},
        new String[]{"sys_menu", "route_path", "VARCHAR(200)"},
        new String[]{"sys_user", "tenant_id", "BIGINT"},
        new String[]{"sys_user", "tenant_name", "VARCHAR(200)"},
        new String[]{"sys_user", "tenant_version", "VARCHAR(50)"},
        // 微信小程序（2026-11-19）：订阅消息剩余可推次数 + 微信身份（wx_openid 补齐 171 遗漏进自愈清单）。
        // 等价脚本：db/194_wx_subscribe.sql、deploy/sql/194_wx_subscribe.sql。
        new String[]{"sys_user", "wx_openid", "VARCHAR(64)"},
        new String[]{"sys_user", "wx_subscribe_quota", "INTEGER"},
        // 微信订阅消息按模板分额度（2026-11-20）：工序通知专用授权额度（审批额度仍用 wx_subscribe_quota）
        new String[]{"sys_user", "wx_operation_quota", "INTEGER"},
        new String[]{"approval_flow", "notify_user_ids", "TEXT"},
        new String[]{"inventory_ledger", "material_code", "VARCHAR(50)"},
        new String[]{"inventory_ledger", "material_name", "VARCHAR(200)"},
        new String[]{"inventory_ledger", "specification", "VARCHAR(200)"},
        new String[]{"inventory_current", "material_code", "VARCHAR(50)"},
        new String[]{"inventory_current", "material_name", "VARCHAR(200)"},
        new String[]{"inventory_current", "spec", "VARCHAR(200)"},
        new String[]{"sale_pre_receipt", "cashier_id", "BIGINT"},
        new String[]{"sale_pre_receipt", "cashier_name", "VARCHAR(100)"},
        new String[]{"purchase_pre_payment", "cashier_id", "BIGINT"},
        new String[]{"purchase_pre_payment", "cashier_name", "VARCHAR(100)"},
        new String[]{"sale_quote", "settle_mode_id", "BIGINT"},
        new String[]{"sale_return", "settle_mode_id", "BIGINT"},
        new String[]{"sale_invoice", "settle_mode_id", "BIGINT"},
        new String[]{"sale_delivery", "settle_mode_id", "BIGINT"},
        new String[]{"purchase_request", "settle_mode_id", "BIGINT"},
        new String[]{"purchase_order", "settle_mode_id", "BIGINT"},
        new String[]{"customer", "default_delivery_warehouse_id", "BIGINT"},
        new String[]{"sale_pre_receipt", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"purchase_pre_payment", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"operation_expense", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"cost_calculation", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"operation_report", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"mrp_plan", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"subcontract_order", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"subcontract_return", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"subcontract_inbound", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"subcontract_supplement", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"subcontract_pick", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"production_order", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"sample_order", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"free_sample_order", "attachment_urls", "VARCHAR(1000)"},
        new String[]{"purchase_receipt", "delivery_address", "VARCHAR(255)"},
        new String[]{"purchase_return", "delivery_address", "VARCHAR(255)"},
        new String[]{"purchase_invoice", "delivery_address", "VARCHAR(255)"},
        new String[]{"sale_quote", "delivery_address", "VARCHAR(255)"},
        new String[]{"sale_return", "delivery_address", "VARCHAR(255)"},
        new String[]{"purchase_receipt", "expected_delivery_date", "DATE"},
        new String[]{"sale_quote", "prepaid_rate", "NUMERIC(18,4)"},
        new String[]{"sale_quote", "prepaid_amount", "NUMERIC(18,2)"},
        new String[]{"sale_return", "prepaid_rate", "NUMERIC(18,4)"},
        new String[]{"sale_return", "prepaid_amount", "NUMERIC(18,2)"},
        new String[]{"sale_invoice", "prepaid_rate", "NUMERIC(18,4)"},
        new String[]{"sale_invoice", "prepaid_amount", "NUMERIC(18,2)"},
        new String[]{"sale_delivery", "prepaid_rate", "NUMERIC(18,4)"},
        new String[]{"sale_delivery", "prepaid_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_order", "prepaid_rate", "NUMERIC(18,4)"},
        new String[]{"stock_transfer", "discount_amount", "NUMERIC(18,2)"},
        new String[]{"stock_transfer", "tax_amount", "NUMERIC(18,2)"},
        new String[]{"stock_transfer", "net_amount", "NUMERIC(18,2)"},
        new String[]{"other_receipt", "discount_amount", "NUMERIC(18,2)"},
        new String[]{"other_receipt", "tax_amount", "NUMERIC(18,2)"},
        new String[]{"other_receipt", "net_amount", "NUMERIC(18,2)"},
        new String[]{"other_issue", "discount_amount", "NUMERIC(18,2)"},
        new String[]{"other_issue", "tax_amount", "NUMERIC(18,2)"},
        new String[]{"other_issue", "net_amount", "NUMERIC(18,2)"},
        new String[]{"stock_transfer_item", "tax_rate", "NUMERIC(18,4)"},
        new String[]{"stock_transfer_item", "discount_rate", "NUMERIC(18,4)"},
        new String[]{"stock_transfer_item", "discount_amount", "NUMERIC(18,4)"},
        new String[]{"stock_transfer_item", "tax_amount", "NUMERIC(18,4)"},
        new String[]{"stock_transfer_item", "net_amount", "NUMERIC(18,4)"},
        new String[]{"other_receipt_item", "tax_rate", "NUMERIC(18,4)"},
        new String[]{"other_receipt_item", "discount_rate", "NUMERIC(18,4)"},
        new String[]{"other_receipt_item", "discount_amount", "NUMERIC(18,4)"},
        new String[]{"other_receipt_item", "tax_amount", "NUMERIC(18,4)"},
        new String[]{"other_receipt_item", "net_amount", "NUMERIC(18,4)"},
        new String[]{"other_issue_item", "tax_rate", "NUMERIC(18,4)"},
        new String[]{"other_issue_item", "discount_rate", "NUMERIC(18,4)"},
        new String[]{"other_issue_item", "discount_amount", "NUMERIC(18,4)"},
        new String[]{"other_issue_item", "tax_amount", "NUMERIC(18,4)"},
        new String[]{"other_issue_item", "net_amount", "NUMERIC(18,4)"},
        new String[]{"sale_pre_receipt_item", "return_amount", "NUMERIC(18,2)"},
        new String[]{"sale_pre_receipt_item", "base_amount", "NUMERIC(18,2)"},
        new String[]{"sale_pre_receipt_item", "used_amount", "NUMERIC(18,2)"},
        new String[]{"sale_pre_receipt_item", "used_base_amount", "NUMERIC(18,2)"},
        new String[]{"sale_pre_receipt_item", "return_base_amount", "NUMERIC(18,2)"},
        new String[]{"sale_pre_receipt_item", "remark", "VARCHAR(500)"},
        new String[]{"purchase_pre_payment_item", "return_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_pre_payment_item", "base_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_pre_payment_item", "used_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_pre_payment_item", "used_base_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_pre_payment_item", "return_base_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_pre_payment_item", "remark", "VARCHAR(500)"},
        new String[]{"mrp_plan", "plan_horizon", "INTEGER"},
        new String[]{"mrp_plan", "time_fence_days", "INTEGER"},
        new String[]{"mrp_plan", "include_so", "INTEGER"},
        new String[]{"mrp_plan", "include_forecast", "INTEGER"},
        new String[]{"mrp_plan", "include_safety_stock", "INTEGER"},
        new String[]{"mrp_plan", "include_open_po", "INTEGER"},
        new String[]{"mrp_plan", "include_open_mo", "INTEGER"},
        new String[]{"mrp_plan", "include_onhand_stock", "INTEGER"},
        new String[]{"mrp_plan", "lot_size_type", "VARCHAR(50)"},
        new String[]{"mrp_plan", "lot_size", "NUMERIC(18,4)"},
        new String[]{"mrp_plan", "min_order_qty", "NUMERIC(18,4)"},
        new String[]{"material", "default_warehouse_id", "BIGINT"},
        new String[]{"operation_report", "equipment_id", "VARCHAR(100)"},
        new String[]{"operation_report", "work_center_name", "VARCHAR(100)"},
        new String[]{"purchase_invoice_item", "batch_no", "VARCHAR(50)"},
        new String[]{"purchase_invoice_item", "production_date", "DATE"},
        new String[]{"purchase_invoice_item", "expiry_date", "DATE"},
        new String[]{"purchase_order_item", "batch_no", "VARCHAR(50)"},
        new String[]{"purchase_order_item", "production_date", "DATE"},
        new String[]{"purchase_order_item", "expiry_date", "DATE"},
        new String[]{"purchase_request_item", "batch_no", "VARCHAR(50)"},
        new String[]{"purchase_request_item", "production_date", "DATE"},
        new String[]{"purchase_request_item", "expiry_date", "DATE"},
        new String[]{"purchase_return_item", "production_date", "DATE"},
        new String[]{"purchase_return_item", "expiry_date", "DATE"},
        new String[]{"purchase_contract_item", "batch_no", "VARCHAR(50)"},
        new String[]{"subcontract_order", "material_id", "BIGINT"},
        new String[]{"subcontract_order", "material_code", "VARCHAR(50)"},
        new String[]{"subcontract_order", "material_name", "VARCHAR(200)"},
        new String[]{"subcontract_order", "unit_id", "BIGINT"},
        new String[]{"subcontract_order", "unit_name", "VARCHAR(50)"},
        new String[]{"subcontract_order_item", "unit_id", "BIGINT"},
        new String[]{"subcontract_order_item", "supply_type", "VARCHAR(50)"},
        new String[]{"subcontract_return", "pick_id", "BIGINT"},
        new String[]{"subcontract_return", "pick_no", "VARCHAR(50)"},
        new String[]{"subcontract_return", "source_bill_type", "VARCHAR(50)"},
        new String[]{"subcontract_return", "source_bill_no", "VARCHAR(50)"},
        new String[]{"subcontract_inbound", "source_bill_type", "VARCHAR(50)"},
        new String[]{"subcontract_inbound", "source_bill_no", "VARCHAR(50)"},
        new String[]{"subcontract_supplement", "pick_id", "BIGINT"},
        new String[]{"subcontract_supplement", "pick_no", "VARCHAR(50)"},
        new String[]{"subcontract_supplement", "source_bill_type", "VARCHAR(50)"},
        new String[]{"subcontract_supplement", "source_bill_no", "VARCHAR(50)"},
        // 业务执行态列（2026-09-20）：与 status(审批态)、close_status(完结标志) 三字段解耦；
        // 迁移期双写（存量 NULL，前端仍取 status），详见 com.erp.common.bill.BusStatusEnum
        new String[]{"stock_transfer", "bus_status", "VARCHAR(20)"},
        new String[]{"stock_check", "bus_status", "VARCHAR(20)"},
        new String[]{"other_receipt", "bus_status", "VARCHAR(20)"},
        new String[]{"other_issue", "bus_status", "VARCHAR(20)"},
        new String[]{"material_requisition", "bus_status", "VARCHAR(20)"},
        new String[]{"production_receipt", "bus_status", "VARCHAR(20)"},
        new String[]{"sale_quote", "bus_status", "VARCHAR(20)"},
        new String[]{"sale_contract", "bus_status", "VARCHAR(20)"},
        new String[]{"purchase_request", "bus_status", "VARCHAR(20)"},
        new String[]{"purchase_order", "bus_status", "VARCHAR(20)"},
        // 既有缺陷修复（阶段6 全量验证发现）：采购申请单关闭接口写 close_date，本环境缺该列 → 接口一直 500
        new String[]{"purchase_request", "close_date", "TIMESTAMP"},
        // 版本隔离冒烟修复（2026-10-10）：老租户模板库缺这些列 → 设置中心自定义字段/销售与财务分析报表/费用分摊统计报错。
        // 登记于此，主库 + 所有租户库启动时自动补齐（防"老模板库克隆出的租户缺对象"再复发）。
        new String[]{"sys_custom_field", "is_drop_show", "SMALLINT NOT NULL DEFAULT 0"},
        new String[]{"sale_order", "account_id", "BIGINT"},
        new String[]{"operation_expense", "approval_status", "VARCHAR(20)"},
        // 第 12 期（2026-09-28）：质检模块 —— 工单工序列补「质检模板绑定 / 质检合格数（下道可开工数）/ 本道可开工数」
        new String[]{"work_order_operation", "qc_template_id", "BIGINT"},
        new String[]{"work_order_operation", "approve_qty", "NUMERIC(18,4)"},
        new String[]{"work_order_operation", "can_start_qty", "NUMERIC(18,4)"},
        // 第 19 期（2026-10-09）：质检逐项留证 —— 检验记录明细加「留证图片」列（逗号分隔 URL，可空）
        // 等价脚本：db/141_qc_item_images.sql、deploy/sql/141_qc_item_images.sql。
        new String[]{"qc_record_item", "images", "VARCHAR(1000)"},
        // 工序级「谁可以改负责人」（2026-10-09）：档位从工艺路线主表下沉到「工序明细」每一行
        // 等价脚本：db/142_operation_assign_scope.sql、deploy/sql/142_operation_assign_scope.sql。
        new String[]{"routing_operation", "assign_scope", "VARCHAR(20)"},
        new String[]{"routing_operation", "assign_role_ids", "TEXT"},
        // 工序扫码认领开关（2026-10-25）：每道工序是否允许扫码「我来做」；空按「是」处理
        new String[]{"routing_operation", "allow_scan_claim", "VARCHAR(8) DEFAULT '是'"},
        // 第 13 期（2026-09-28）：父子工序 —— 父工序行号（路线主档 + 工单快照）/ 工单侧解析出的父工序行ID
        new String[]{"routing_operation", "parent_line_no", "INT"},
        new String[]{"work_order_operation", "parent_line_no", "INT"},
        new String[]{"work_order_operation", "parent_row_id", "BIGINT"},
        // 第 13 期（2026-09-28）：金额类型工序 —— 计划金额（路线主档 + 工单快照）/ 已确认金额（登记金额累加）
        new String[]{"routing_operation", "plan_amount", "NUMERIC(18,4)"},
        new String[]{"work_order_operation", "plan_amount", "NUMERIC(18,4)"},
        new String[]{"work_order_operation", "confirmed_amount", "NUMERIC(18,4)"},
        // 第 2 版工序面板（2026-10-07）：① 工序附件列；② 工艺路线「投入自动带出上道产出」开关（默认 1=开）
        // 等价脚本：db/132_wo_op_panel.sql、deploy/sql/132_wo_op_panel.sql。
        new String[]{"work_order_operation", "attachments", "TEXT"},
        new String[]{"routing", "auto_input_from_prev", "SMALLINT NOT NULL DEFAULT 1"},
        // 第 4 版工序面板（2026-10-08）：工艺路线「谁可以改负责人」两列（默认「全部人」= 升级前行为）
        // 等价脚本：db/139_routing_assign_scope.sql、deploy/sql/139_routing_assign_scope.sql。
        new String[]{"routing", "assign_scope", "VARCHAR(20) NOT NULL DEFAULT '全部人'"},
        new String[]{"routing", "assign_role_ids", "TEXT"},
        // 工时体系（2026-10-07）：① 工序实际工时（自动累计）② 本段计时起点
        //                      ③ 物料单件标准加工工时 ④ 工艺路线单件加工工时
        // 等价脚本：db/137_work_hours.sql、deploy/sql/137_work_hours.sql。
        new String[]{"work_order_operation", "actual_hours", "NUMERIC(18,4)"},
        new String[]{"work_order_operation", "timing_since", "TIMESTAMP"},
        new String[]{"material", "standard_hours", "NUMERIC(18,4)"},
        // 2026-11-07：物料日产能（件/天），参与 APS 排程占用天数计算；0/空 = 不限制（升级前排程结果不变）。
        // 等价脚本：deploy/sql/185_material_daily_capacity.sql。
        new String[]{"material", "daily_capacity", "NUMERIC(18,4) DEFAULT 0"},
        // 2026-11-25：物料采购批量政策（金蝶/用友同口径）——采购批量=包装量、最小订货量=订货下限。
        // 0/空 = 不启用，升级前后行为一致。等价脚本：db/198_material_purchase_batch.sql、deploy/sql/198_material_purchase_batch.sql。
        new String[]{"material", "purchase_batch_qty", "NUMERIC(18,4) DEFAULT 0"},
        new String[]{"material", "min_order_qty", "NUMERIC(18,4) DEFAULT 0"},
        new String[]{"routing_operation", "unit_hours", "NUMERIC(18,4)"},
        // 2026-11-26：工艺路线多物料/多分类 + 工序自定义字段 —— 工序稳定标识（路线保存全删全插，字段不能绑自增ID）
        // 等价脚本：db/199_routing_material_and_operation_fields.sql、deploy/sql/199_routing_material_and_operation_fields.sql
        new String[]{"routing_operation", "operation_key", "VARCHAR(64)"},
        new String[]{"work_order_operation", "routing_operation_key", "VARCHAR(64)"},
        // 2026-11-26 成本链路：委外发料/补料/退料记录实际成本，入库记录成品单位成本与材料成本。
        // 等价脚本：db/200_subcontract_cost_trace.sql、deploy/sql/200_subcontract_cost_trace.sql。
        new String[]{"subcontract_pick_item", "avg_cost", "NUMERIC(18,6)"},
        new String[]{"subcontract_supplement_item", "avg_cost", "NUMERIC(18,6)"},
        new String[]{"subcontract_return_item", "avg_cost", "NUMERIC(18,6)"},
        new String[]{"subcontract_inbound_item", "unit_cost", "NUMERIC(18,6)"},
        new String[]{"subcontract_inbound_item", "material_cost", "NUMERIC(18,2)"},
        new String[]{"subcontract_inbound_item", "batch_no", "VARCHAR(64)"},
        new String[]{"subcontract_pick_item", "batch_no", "VARCHAR(64)"},
        new String[]{"subcontract_supplement_item", "batch_no", "VARCHAR(64)"},
        new String[]{"subcontract_return_item", "batch_no", "VARCHAR(64)"},
        new String[]{"purchase_return_item", "avg_cost", "NUMERIC(18,6)"},
        // 财务管理 → 工序工资核算（2026-09-27，等价脚本 db/118_wage_settlement.sql）：员工「工时费率」列。
        // 【2026-10-09 补漏】该列此前只由一次性脚本 118 写入"执行时那一个库"（主库），租户库是从模板库
        // 克隆出来的快照、又没有登记进本清单 → 租户库永久缺列。员工实体按映射列全量 SELECT，
        // 首条语句即报「字段 hourly_rate 不存在」，PG 事务随即被中止，后续语句统一报
        // 「当前事务被终止」（本机租户库 neibu001 实测复现）。补进清单后逐租户库启动自愈。
        new String[]{"employee", "hourly_rate", "NUMERIC(18,4)"},
        // 第 13 期（2026-09-28）：生产工单接入自定义字段 —— 补 ext_data JSONB（缺列会让自定义字段保存事务被中止）
        new String[]{"production_order", "ext_data", "JSONB"},
        // 字段落库补齐 第 3 批（2026-09-28）：23 个「前端能填能选、后端实体与数据库都不存在」的字段，
        // 仅用于记录、不参与业务判断。等价脚本：db/129_field_persistence_batch3.sql、deploy/sql/129_field_persistence_batch3.sql。
        new String[]{"sale_contract", "is_consignment", "VARCHAR(255)"},
        new String[]{"sale_contract", "picker_id", "BIGINT"},
        new String[]{"sale_quote", "customer_short_name", "VARCHAR(255)"},
        new String[]{"sale_delivery", "payment_term_id", "BIGINT"},
        new String[]{"sale_delivery", "price_level_id", "BIGINT"},
        new String[]{"sale_delivery", "freight", "NUMERIC(18,4)"},
        new String[]{"sale_return", "payment_term_id", "BIGINT"},
        new String[]{"sale_return", "price_level_id", "BIGINT"},
        new String[]{"sale_invoice", "payment_term_id", "BIGINT"},
        new String[]{"sale_invoice", "price_level_id", "BIGINT"},
        // 销售发票「税票号码」（2026-10-13）：税控开票软件的真实发票号码，登记字段。
        // 等价脚本：db/155_sale_invoice_tax_invoice_no.sql、deploy/sql/153_sale_invoice_tax_invoice_no.sql。
        new String[]{"sale_invoice", "tax_invoice_no", "VARCHAR(255)"},
        new String[]{"purchase_request", "payment_term_id", "BIGINT"},
        new String[]{"purchase_receipt", "contact_person", "VARCHAR(255)"},
        new String[]{"purchase_receipt", "contact_phone", "VARCHAR(255)"},
        new String[]{"purchase_receipt", "payment_method", "VARCHAR(255)"},
        new String[]{"purchase_receipt", "currency_id", "BIGINT"},
        new String[]{"purchase_return", "contact_person", "VARCHAR(255)"},
        new String[]{"purchase_return", "contact_phone", "VARCHAR(255)"},
        new String[]{"purchase_return", "currency_id", "BIGINT"},
        new String[]{"purchase_return", "discount_rate", "NUMERIC(18,2)"},
        new String[]{"purchase_invoice", "contact_person", "VARCHAR(255)"},
        new String[]{"purchase_invoice", "contact_phone", "VARCHAR(255)"},
        new String[]{"purchase_invoice", "currency_id", "BIGINT"},
        new String[]{"purchase_invoice", "discount_rate", "NUMERIC(18,2)"},
        // 单据明细补「行备注」列（2026-10-08）：下推随行带入的备注此前无列可落，补 remark
        // 等价脚本：deploy/sql/138_item_remark_columns.sql。
        new String[]{"purchase_invoice_item", "remark", "VARCHAR(500)"},
        new String[]{"purchase_price_strategy_item", "remark", "VARCHAR(500)"},
        new String[]{"qc_template_item", "remark", "VARCHAR(500)"},
        new String[]{"work_order_operation", "remark", "VARCHAR(500)"},
        new String[]{"bill_writeoff_detail", "remark", "VARCHAR(500)"},
        // 单据「计税方式」与含税/不含税价 —— 明细列全站铺开（2026-10-09）：
        // 明细「含税价 / 不含税价」两列，按表头「计税方式」由「单价」换算落库；共 40 张明细表，
        // 其中 sale_order_item 已在上面（销售订单试点）登记，此处只补其余 39 张。
        // 等价脚本：db/139_item_price_tax_columns.sql、deploy/sql/139_item_price_tax_columns.sql。
        new String[]{"sale_quote_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"sale_quote_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"sale_contract_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"sale_contract_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"sale_delivery_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"sale_delivery_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"sale_invoice_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"sale_invoice_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"sale_return_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"sale_return_item", "price_without_tax", "NUMERIC(18,6)"},
        // 销售退货回库成本冻结（2026-11-28）：审核时记录原出库成本，反审/作废按原价冲回
        // 等价脚本：db/201_sale_return_stock_effect.sql、deploy/sql/201_sale_return_stock_effect.sql。
        new String[]{"sale_return_item", "avg_cost", "NUMERIC(18,6)"},
        // 免费样品发货成本冻结（2026-11-28）：作废/删除按冻结成本退回库存
        new String[]{"free_sample_order_item", "avg_cost", "NUMERIC(18,6)"},
        new String[]{"purchase_order_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_order_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_contract_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_contract_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_receipt_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_receipt_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_return_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_return_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_invoice_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_invoice_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"stock_transfer_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"stock_transfer_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"stock_check_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"stock_check_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"other_receipt_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"other_receipt_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"other_issue_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"other_issue_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"material_requisition_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"material_requisition_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"production_order_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"production_order_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"production_receipt_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"production_receipt_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"work_order_operation", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"work_order_operation", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"routing_operation", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"routing_operation", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"produce_assemble_detail", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"produce_assemble_detail", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"produce_assemble_bom", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"produce_assemble_bom", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"produce_disassemble_detail", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"produce_disassemble_detail", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"produce_disassemble_bom", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"produce_disassemble_bom", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"subcontract_order_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"subcontract_order_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"subcontract_inbound_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"subcontract_inbound_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"sample_order_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"sample_order_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"cash_receipt_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"cash_receipt_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"cash_payment_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"cash_payment_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"sale_pre_receipt_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"sale_pre_receipt_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_pre_payment_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_pre_payment_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"fund_transfer_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"fund_transfer_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"cost_calculation_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"cost_calculation_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"sale_expense_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"sale_expense_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_expense_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_expense_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"bill_writeoff_detail", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"bill_writeoff_detail", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"operation_expense_allocation", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"operation_expense_allocation", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"sale_statement_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"sale_statement_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_statement_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"purchase_statement_item", "price_without_tax", "NUMERIC(18,6)"},
        new String[]{"wage_settlement_item", "price_with_tax", "NUMERIC(18,6)"},
        new String[]{"wage_settlement_item", "price_without_tax", "NUMERIC(18,6)"},
        // 单据表头「计税方式」列全站落库（2026-10-09）：此前表头 tax_calc_method 仅
        // sale_order / sale_contract / purchase_contract 三张表有列，其余表头实体用
        // @TableField(exist=false) 的传输字段接住 → 保存成功但重开单据表头计税方式丢失。
        // 此处为下列表头补列，使其真正落库、详情接口可回读。
        // 等价脚本：db/140_header_tax_calc_method.sql、deploy/sql/140_header_tax_calc_method.sql。
        new String[]{"sale_quote", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"sale_return", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"sale_invoice", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"sale_delivery", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"purchase_order", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"purchase_receipt", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"purchase_return", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"purchase_invoice", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"other_receipt", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"other_issue", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"stock_transfer", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"stock_check", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"material_requisition", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"production_order", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"production_receipt", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"routing", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"produce_assemble", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"produce_disassemble", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"sample_order", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"cost_calculation", "tax_calc_method", "VARCHAR(20)"},
        new String[]{"subcontract_order", "tax_calc_method", "VARCHAR(20)"},
        // 采购三单（入库单/退货单/发票）表头与明细的税额、净额、折扣列（2026-10-09 补齐）。
        // 这三张单据的实体已按下列列映射并参与保存，但租户库是模板库的历史快照、这些列
        // 之前没有登记进本清单 → 租户库永久缺列：保存时 PG 先报「字段不存在」，事务随即被中止，
        // 界面只能看到「当前事务被终止」（8cloud 测试租户实测：采购入库单/采购退货单/采购发票全部无法保存）。
        // 等价脚本：db/143_purchase_tax_columns.sql、deploy/sql/143_purchase_tax_columns.sql。
        new String[]{"purchase_receipt", "tax_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_receipt", "net_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_receipt_item", "tax_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_receipt_item", "tax_rate", "NUMERIC(18,2)"},
        new String[]{"purchase_receipt_item", "net_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_return", "tax_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_return", "discount_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_return", "net_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_return_item", "tax_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_return_item", "tax_rate", "NUMERIC(18,2)"},
        new String[]{"purchase_return_item", "discount_rate", "NUMERIC(18,2)"},
        new String[]{"purchase_return_item", "discount_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_return_item", "net_amount", "NUMERIC(18,2)"},
        new String[]{"purchase_invoice_item", "net_amount", "NUMERIC(18,2)"},
        // 单据明细与表头「价税合计」补齐（2026-11-23）：前端显示 3 张（采购入库/打样/成本计算），
        // 其余 6 张仅后端落库；采购入库单原有 net_amount 不重复登记。
        // 等价脚本：db/196_item_net_amount_columns.sql、deploy/sql/196_item_net_amount_columns.sql。
        new String[]{"sample_order", "net_amount", "NUMERIC(18,2)"},
        new String[]{"sample_order_item", "net_amount", "NUMERIC(18,2)"},
        new String[]{"cost_calculation", "net_amount", "NUMERIC(18,2)"},
        new String[]{"cost_calculation_item", "net_amount", "NUMERIC(18,2)"},
        new String[]{"production_receipt", "net_amount", "NUMERIC(18,2)"},
        new String[]{"production_receipt_item", "net_amount", "NUMERIC(18,2)"},
        new String[]{"material_requisition", "net_amount", "NUMERIC(18,2)"},
        new String[]{"material_requisition_item", "net_amount", "NUMERIC(18,2)"},
        new String[]{"stock_check", "net_amount", "NUMERIC(18,2)"},
        new String[]{"stock_check_item", "net_amount", "NUMERIC(18,2)"},
        new String[]{"production_order_item", "net_amount", "NUMERIC(18,2)"},
        new String[]{"produce_assemble", "net_amount", "NUMERIC(18,2)"},
        new String[]{"produce_assemble_detail", "net_amount", "NUMERIC(18,2)"},
        new String[]{"produce_assemble_bom", "net_amount", "NUMERIC(18,2)"},
        new String[]{"produce_disassemble", "net_amount", "NUMERIC(18,2)"},
        new String[]{"produce_disassemble_detail", "net_amount", "NUMERIC(18,2)"},
        new String[]{"produce_disassemble_bom", "net_amount", "NUMERIC(18,2)"},
        // AI用量月表「缓存命中」列（2026-10-10 小楷能力整合）：模型侧命中上下文缓存的输入tokens。
        // 等价脚本：db/148_ai_token_cache_stats.sql、deploy/sql/148_ai_token_cache_stats.sql；
        // AiTokenUsageService.ensureTable 自身也带 ADD COLUMN IF NOT EXISTS（三重兜底）。
        new String[]{"ai_token_usage", "prompt_cache_hit_tokens", "BIGINT NOT NULL DEFAULT 0"}
    );

    // ---- 对业务库（erp_db / 每个租户独立库）启动时保证列齐全 ----
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBusinessDbColumns() {
        try {



            // 审批流程抄送：抄送人ID列表(JSON数组)，审批通过/驳回后给这些人 + 发起人 自动推铃铛通知

            // 库存台账物料冗余列（2026-09-19 用户报障）：台账原先只存 material_id，物料编码/名称/规格
            // 全靠列表接口实时 join material 表 → 物料主档被删除或整批换 id 后，历史流水永久空白
            // （本机库实测 231/231 行命中）。补列后写入时一并落库，与其它单据明细表同口径。

            // 即时库存冗余列兜底：Inventory 实体已按真实列映射这三列（物料被删时用它兜底显示），
            // 老库/租户库若缺列会导致列表查询直接报错，故在此逐库补齐（41 号补丁之外的第二道保险）。

            // 按钮权限动作扩展（2026-09-19）：动作从旧 7 个扩到 33 个，为"原先整组勾选过"的角色补齐新动作码，
            // 否则老库里这些角色会因 v-permission 严格模式看不到「提交/重置草稿」等新按钮
            ensureButtonPermissionNewActions();

            // 补列全部走 BUSINESS_COLUMN_SPECS（唯一清单，见类顶部字段：主库 + 每个租户库共用同一份）。

            for (String[] spec : BUSINESS_COLUMN_SPECS) ensureColumnIfMissing(spec[0], spec[1], spec[2]);

            // 多计量单位（2026-11-09）：物料辅计量字段 + 换算关系表 + 单据明细折算列 + 库存双数量
            ensureMultiUnitSupportOn(jdbcTemplate);
            // 生产参数收敛（2026-11-09）：只保留「超过来源单数量」
            ensureProduceParamConvergence(jdbcTemplate);
            // 物料采购批量政策（2026-11-25）：采购订单/领料单两个校验开关，默认关，缺则补、不动已改值
            ensurePurchaseBatchPolicyConfig(jdbcTemplate);

            // 价格策略改造（2026-10-10）：放开非空 + 存量回填 + 优先级重排 + 唯一索引（主库）
            ensurePriceStrategyTargetsOn(jdbcTemplate);

            // sys_config.config_value 放宽为 TEXT（2026-10-10）：AI服务商整体JSON存单值列，255必超长
            ensureSysConfigValueText(jdbcTemplate);

            // 销售订单明细单价列放宽为 numeric(18,6)（2026-10-13）：单价2位截断反算价 → 打开重算漂移1分
            ensureSaleOrderItemPricePrecision(jdbcTemplate);

            // 数据版本记录快照列放宽为 TEXT（2026-10-12）：整行JSON快照255必超长 → 事务污染静默回滚
            ensureDataVersionSnapshotText(jdbcTemplate);

            // 设置中心权限码补回（2026-09-18）：缺这条码 → 非管理员账号的小助手看不到设置中心类工具
            ensureSettingsCenterPermissionCode();

            // 单据终态显示名统一（2026-09-19 用户拍板）：「已生效」→「已生效」
            ensureStatusLabelRenamed();

            // 生产工单状态机对齐全站通用口径（2026-09-20）：退役「已下达/执行中/已完工」业务执行态
            ensureWorkOrderStatusUnified();

            // 工艺路线从"单据"降为"主档"（2026-09-23 用户口径）：状态只保留 启用/停用
            ensureRoutingMasterUnified();

            // 车间工序可视化默认配置（2026-09-23）：工序编号规则 + 默认人工/制造费用率
            ensureWorkOperationDefaults();

            // 编号规则唯一清单收口（2026-10-12，121 生产实锤）：价格等级/结算方式/银行账户等规则
            // 此前只靠一次性 SQL 在本地主库补过 → 服务器主库/模板库/租户库全缺，
            // 新建价格等级报「单据类型 [BD_PRICE_LEVEL] 未配置编号规则」。
            // 主库 + 逐租户库 + 租户模板库三覆盖（租户库/模板库见 ensureColumnsOnTenantDatabases 内）。
            try {
                int snAdded = com.erp.serialno.support.SerialNoDefaults.ensureOn(jdbcTemplate);
                if (snAdded > 0) {
                    log.info("[SchemaUpgradeEnsurer] 主库编号规则补齐: 新增 {} 条（清单共 {} 条）",
                            snAdded, com.erp.serialno.support.SerialNoDefaults.size());
                }
            } catch (Exception e) {
                log.warn("[SchemaUpgradeEnsurer] 主库编号规则补齐跳过: {}", e.getMessage());
            }

            // AI用量管理菜单路径修正（2026-10-12）：该行 route_path 误为 /system/settings（与设置中心
            // 主行重复），导致按主库对齐菜单的每个租户库都多出一条重复行——修正为设置中心子项路径
            // （与 144 版本划分脚本里 AI 用量的口径一致）。主库 + 逐租户库 + 模板库三覆盖，幂等。
            ensureAiTokenUsageRouteFix(jdbcTemplate);

            // 工序操作日志表 + 工序状态 8 值口径统一（2026-09-23 第 2 期）
            ensureWorkOperationLog();

            // 质检模块（2026-09-28 第 12 期）：质检模板/检验记录 4 张表（多租户物理隔离多库的兜底）
            ensureQcTables();

            // 工艺路线多物料/多分类 + 工序自定义字段（2026-11-26）
            ensureRoutingBindingTablesOn(jdbcTemplate);

            // 化妆品批记录完整闭环（2026-11-06）：放行单/留样/批生产记录/记录更正/电子签名 5 表
            // + 批次质量状态列 + 质检环节列 + 参数与默认模板种子（主库兜底，租户库见 ensureColumnsOnTenantDatabases）
            ensureBatchRecordTablesOn(jdbcTemplate);

            // 批次号规则生效 + 批次溯源（2026-11-06）：索引 + 批次唯一口径 + 两个参数（主库兜底）
            ensureBatchTraceSupportOn(jdbcTemplate);

            // 入库单质检闭环（2026-11-07）：质检模板/记录扩展 + 三张入库单质检与仓库列 + 默认报废仓
            ensureInboundQcSupportOn(jdbcTemplate);

            // 基础资料联动（2026-11-08）：存量单据简称/客户编号/供应商编号回填（只填空值）
            ensureMasterLinkageBackfillOn(jdbcTemplate);

            // 行级 BOM（2026-11-06）：生产工单成品明细 / 销售 6 单据明细 / MRP 计划明细 各加 BOM 三列
            ensureLineLevelBomColumnsOn(jdbcTemplate);

            // 工序扫码认领与按人报工（2026-10-25）：共同负责人表 + 报工流水表（主库兜底）
            ensureOperationClaimTables(jdbcTemplate);

            // 工单进度对外分享（2026-11-25）：分享令牌表（主库兜底）
            ensureWorkOrderShareTable(jdbcTemplate);

            // 小楷助手积分制（2026-10-24）：积分账户/积分流水表 + 月用量积分列（主库）
            com.erp.ai.credit.AiCreditSchema.ensure(jdbcTemplate);

            // 开账引导基础设施（2026-10-11）：字段显示名表 + 引导状态/初始化开关种子（主库）
            ensureOnboardingInfra(jdbcTemplate, true);

            // 数值精度设置真正生效（2026-10-10）：默认位数对齐 + 归并到「小数位数设置」分区
            // （此前设置中心的小数位数无任何代码消费=摆设；接入后默认值必须=旧行为）
            ensureNumericPrecisionSettings();

            // 列表默认视图种子（2026-10-14）：双视图二期的全局默认值开关（缺则补，不动已改值）
            ensureListDefaultViewSetting(jdbcTemplate);

            // 收款单销售提成种子（2026-10-16）：启用开关 + 默认比例（缺则补，不动已改值）
            ensureReceiptCommissionSettings(jdbcTemplate);

            // 应收单自动下推选项补全（2026-10-18）：只补「不自动生成」选项与显示名，不改当前取值
            ensureArSourceSetting(jdbcTemplate);

            // 销售订单状态设置种子 + 明细「已开票金额」存量回填（2026-10-14）：完结口径三模式
            ensureSaleOrderCloseModeSetting(jdbcTemplate);
            backfillTurnedInvoiceAmount(jdbcTemplate, "主库");
            // 汇总收款行级 FIFO 回填（2026-10-14）：切换明细收款方式后订单明细行"已收金额"数据差根治
            ensureReceiptSummaryFifoAllocated(jdbcTemplate, "主库");
            // 收付款核销明细孤儿清理（2026-10-19）：删除收/付款单时级联此前漏了核销明细表 →
            // 未冲销孤儿行让订单「收款详情」出现幽灵收款记录、列表"已收"却为 0（121 生产实锤）
            ensureOrphanWriteoffCleaned(jdbcTemplate, "主库");
            // 审核人系统占位清理（2026-10-28 用户口径）：免审批单据没有人工审核人，
            // 历史写入的「系统(免审批)」全部留空，避免列表/明细报表/单据信息里出现假审核人
            clearSystemPlaceholderApprover(jdbcTemplate, "主库");
            // 销售订单超量发货比例列（2026-10-31）：主库补齐，存量订单默认 0
            ensureSaleOrderOvershipRatio(jdbcTemplate, "主库");
            // 销售模块超过来源单数量三态策略（2026-11-02）：主库下拉选项/历史值归一化
            ensureSaleOverQtyPolicySettings(jdbcTemplate, "主库");
            // 自动下推单据开关（2026-11-08）：主库补齐两个默认关闭的设置项
            ensureSaleAutoPushSettings(jdbcTemplate, "主库");
            // 单据统一状态机接入（2026-11-09）：主库补齐 10 张单据的状态机必需列
            ensureBillStatusColumns(jdbcTemplate, "主库");
            // SPC质量看板 + 自定义看板（2026-11-05）：建表 + 菜单 + 授权，主库与逐租户库同口径
            ensureSpcCustomBoardOn(jdbcTemplate, "主库");

            // 2026-09-20 新增：新增列此前只在主库生效 → 早于补列创建的租户独立库会永久缺列，
            // 表现为"界面一切正常、一保存就报当前事务被终止"（8cloud 测试租户实测）。此处逐租户库巡检补齐。
            ensureColumnsOnTenantDatabases();

            // 2026-10-12 新增：租户库里"缺平台库记录"的账号自愈补齐（员工档案建的账号此前只写租户库，
            // 登录只在业务主库+平台库查找 → 租户员工一律"用户名或密码错误"，121 生产实锤）。
            healTenantUsersToPlatform();

            // 2026-10-13 新增：平台库 sys_user.password 放开 NOT NULL（8cloud 测试租户实锤——
            // 存量平台库该列带 NOT NULL，而账号同步口径是"平台库不存密码"（写 NULL），
            // INSERT 违反约束被静默吞掉 → 平台库查无此人 → 新建账号永远登录不上）。
            ensurePlatformPasswordNullable();

            // 菜单初始化（2026-09-22 接上）：ensureMenusExist() 此前**从未被调用**（死代码）——
            // 结果是设置中心/AI用量/各模块单据明细报表菜单全靠人工跑 deploy/sql 才出现，
            // 违反守则 04 的 2.7.1-7「菜单/权限默认值必须自动初始化」，新环境部署会看不到入口。
            // 该方法内部全部走 ensureMenu / ensureMenuRoleGrants 的 NOT EXISTS 幂等判断，重复启动 0 副作用。
            ensureMenusExist();

            // 客户包收敛（2026-10-12）：独立部署客户按外置配置隐藏菜单/关闭开账引导（主库 + 全部租户库）
            applyCustomerPackageConvergence();

            log.info("[SchemaUpgradeEnsurer] business DB schema upgrade finished.");
        } catch (Exception e) {
            // 绝不因为 schema 检查异常把启动拉挂
            log.warn("[SchemaUpgradeEnsurer] business DB schema upgrade skipped: {}", e.getMessage());
        }
    }

    /**
     * 【客户包收敛·幂等】按服务器外置配置隐藏菜单 / 关闭开账引导（2026-10-12）。
     *
     * <p>背景：121 等独立部署客户不需要「电商平台」「字段名称设置」等新功能入口，
     * 也不做新租户系统自动配置（开账引导）。此前仅靠人工在数据库里改 is_visible，
     * 发布重启后一旦菜单初始化补建就会回显；本方法每次启动按外置 application.yml
     * 的配置强收敛一遍，配置了才生效、不配置行为与全量包完全一致。</p>
     *
     * <p>覆盖范围：主库 + sys_tenant 登记的全部活跃租户独立库（各库都有自己的 sys_menu / sys_config）。
     * 全程幂等 UPDATE，重复启动 0 副作用；异常只告警不拉挂启动。</p>
     */
    private void applyCustomerPackageConvergence() {
        java.util.List<String> hideCodes = new java.util.ArrayList<>();
        if (customerHideMenuCodes != null && !customerHideMenuCodes.isBlank()) {
            for (String code : customerHideMenuCodes.split(",")) {
                if (code != null && !code.isBlank()) hideCodes.add(code.trim());
            }
        }
        if (hideCodes.isEmpty() && !customerDisableOnboarding) {
            return; // 全量包：不配置即无收敛动作
        }

        // 主库
        int menuMain = 0, cfgMain = 0;
        try {
            if (!hideCodes.isEmpty()) {
                menuMain += hideMenusOn(jdbcTemplate, hideCodes);
            }
            if (customerDisableOnboarding) {
                cfgMain += disableOnboardingOn(jdbcTemplate);
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 客户包收敛主库失败: {}", e.getMessage());
        }

        // 全部租户库（与 ensureColumnsOnTenantDatabases 同一连接模式）
        int menuTenant = 0, cfgTenant = 0, dbs = 0;
        try {
            java.util.List<java.util.Map<String, Object>> tenants = platformJdbcTemplate.queryForList(
                    "SELECT DISTINCT db_name, db_host, db_port, db_username, db_password FROM sys_tenant "
                  + " WHERE status = 'active' AND db_name IS NOT NULL AND db_name <> ''");
            String mainDb = null;
            try {
                mainDb = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
            } catch (Exception ignore) { }
            for (java.util.Map<String, Object> row : tenants) {
                Object dbNameObj = row.get("db_name");
                if (dbNameObj == null) continue;
                String dbName = String.valueOf(dbNameObj).trim();
                if (dbName.isEmpty() || dbName.equals(mainDb)) continue;
                String host = row.get("db_host") == null ? "localhost" : String.valueOf(row.get("db_host"));
                int port = 5432;
                try {
                    if (row.get("db_port") != null) port = Integer.parseInt(String.valueOf(row.get("db_port")));
                } catch (Exception ignore) { }
                String user = row.get("db_username") == null ? "" : String.valueOf(row.get("db_username"));
                String pwd = row.get("db_password") == null ? "" : String.valueOf(row.get("db_password"));
                java.sql.Connection conn = null;
                try {
                    conn = java.sql.DriverManager.getConnection(
                            "jdbc:postgresql://" + host + ":" + port + "/" + dbName, user, pwd);
                    JdbcTemplate tenant = new JdbcTemplate(
                            new org.springframework.jdbc.datasource.SingleConnectionDataSource(conn, true));
                    if (!hideCodes.isEmpty()) {
                        menuTenant += hideMenusOn(tenant, hideCodes);
                    }
                    if (customerDisableOnboarding) {
                        cfgTenant += disableOnboardingOn(tenant);
                    }
                    dbs++;
                } catch (Exception e) {
                    log.warn("[SchemaUpgradeEnsurer] 客户包收敛租户库失败 {}: {}", dbName, e.getMessage());
                } finally {
                    if (conn != null) {
                        try { conn.close(); } catch (Exception ignore) { }
                    }
                }
            }
        } catch (Exception e) {
            // 无平台库（单机客户环境）时跳过租户库收敛，主库已覆盖
            log.debug("[SchemaUpgradeEnsurer] 客户包收敛租户库巡检跳过: {}", e.getMessage());
        }
        log.info("[SchemaUpgradeEnsurer] 客户包收敛完成: 隐藏菜单 {}（主库 {} 行 / 租户库 {} 行·{} 库），开账引导关闭 {}（主库 {} 行 / 租户库 {} 行）",
                hideCodes.isEmpty() ? "未配置" : String.join(",", hideCodes),
                menuMain, menuTenant, dbs,
                customerDisableOnboarding ? "已启用" : "未配置", cfgMain, cfgTenant);
    }

    /**
     * 客户包是否声明"不上该菜单/单据"（2026-11-23）。
     *
     * <p>判定口径 = 外置配置 {@code erp.customer-package.hide-menu-codes} 是否包含该菜单编码。
     * 命中时：相关菜单/按钮权限不初始化、售后维修默认仓库与参数不落库 —— 真正做到"不上"，
     * 而不是只把 is_visible 置 0（前端侧边栏由静态菜单配置渲染，is_visible 并不生效）。
     * 全量包无该配置 = 一律 false，行为与升级前完全一致。
     */
    private boolean isMenuHiddenByCustomerPackage(String code) {
        if (code == null || code.isBlank() || customerHideMenuCodes == null || customerHideMenuCodes.isBlank()) {
            return false;
        }
        for (String c : customerHideMenuCodes.split(",")) {
            if (code.equalsIgnoreCase(c.trim())) return true;
        }
        return false;
    }

    /** 在单个库隐藏指定菜单编码（幂等 UPDATE；返回受影响行数） */
    private int hideMenusOn(JdbcTemplate jdbc, java.util.List<String> menuCodes) {
        String placeholders = String.join(",", java.util.Collections.nCopies(menuCodes.size(), "?"));
        Object[] params = menuCodes.toArray();
        return jdbc.update(
                "UPDATE sys_menu SET is_visible = 0, updated_at = NOW() WHERE menu_code IN (" + placeholders + ")",
                params);
    }

    /** 在单个库关闭开账引导（小楷按行业初始化开关置 false + 引导状态置 done；幂等 UPDATE） */
    private int disableOnboardingOn(JdbcTemplate jdbc) {
        int n = 0;
        n += jdbc.update(
                "UPDATE sys_config SET config_value = 'false', updated_at = NOW() WHERE config_key = 'ai.tool.initialize.enabled'");
        n += jdbc.update(
                "UPDATE sys_config SET config_value = 'done', updated_at = NOW() WHERE config_key = 'tenant.onboarding.status'");
        return n;
    }

    /**
     * 【数据修复·幂等】把设置中心主菜单的权限码补回 {@code system:settings:view}。
     *
     * <p>背景（2026-09-18 用户报障实锤）：设置中心菜单由 {@code deploy/sql/56_ai_token_menu.sql}
     * 初始化，而该 INSERT 当时**没有写 permission_code** → 库里这条码一直是 NULL。
     * AI 小助手判权限读的正是这个码（{@code ToolRegistry.SETTING_PERM_VIEW}），
     * 于是**所有非管理员账号**的 41 个设置中心类工具（系统参数 / 自定义字段 / 打印模板 / 审批设置…）
     * 被静默隐藏，用户看到的是"小助手没有这些工具"（李工 lyc123 实测：AI 可用工具 258 → 215）。
     *
     * <p>只补空值、不动已有码，重复执行 0 副作用（守则 2.7.1-2 幂等要求）。
     */
    private void ensureSettingsCenterPermissionCode() {
        try {
            int n = jdbcTemplate.update(
                    "UPDATE sys_menu SET permission_code = ?, updated_at = NOW() " +
                    " WHERE route_path = '/system/settings' " +
                    "   AND (permission_code IS NULL OR permission_code = '')",
                    "system:settings:view");
            if (n > 0) {
                log.info("[SchemaUpgradeEnsurer] 设置中心权限码已补回 system:settings:view（{} 行）。"
                        + "缺失会导致非管理员账号的小助手看不到设置中心类工具", n);
            }
        } catch (Exception e) {
            // 当前库无 sys_menu 表等情况忽略，绝不拉挂启动
            log.warn("[SchemaUpgradeEnsurer] 设置中心权限码补回跳过: {}", e.getMessage());
        }
    }

    /**
     * 按钮权限「动作扩展」数据迁移（2026-09-19）。
     *
     * <p><b>背景</b>：设置中心 → 角色权限 → 按钮权限的动作清单从旧 7 个
     * （add/edit/delete/export/import/print/audit）扩展到 33 个（新增 submit / reverseApprove / reject /
     * close / reopen / void / unvoid / confirm / release / start / complete / …）；
     * 前端页面按钮（编辑页动作按钮、列表页页头按钮、批量操作按钮）已全部接入 {@code v-permission} 判定。</p>
     *
     * <p><b>为什么必须迁移</b>：{@code v-permission} 的规则②是"该「模块:对象」下存在显式按钮码 → 严格判定"。
     * 老库中某角色若对某对象**整组勾选过**（≥7 个动作），就会进入严格模式；此时新增动作
     * （如 submit / reverseApprove）在库里没有对应行 → **新按钮被静默隐藏**，
     * 与用户要求的"默认能看见菜单就有全部按钮"口径冲突（本机库实测角色 4 销售员等即为此形态）。</p>
     *
     * <p><b>迁移口径</b>：只为"原先整组勾选过（≥7 个动作）"的角色-对象补新增动作码；
     * **只勾了部分动作的角色不动**（尊重精细配置）。幂等：重复启动不会重复插入。</p>
     *
     * <p><b>2026-10-10 修正（用户实报"物料档案关掉导入/导出没效果"）</b>：本迁移原先**每次启动都无条件重放**，
     * 于是用户在「角色权限设置 → 按钮权限」里取消勾选的按钮（如 物料档案 的 导入/导出），
     * 只要该角色在该对象下仍留有 ≥7 条码，**下次启动 / 发布重启就会被静默补回**，
     * 实测（本机库）：删除后重启，物料档案按钮码 78 → 83，导入/导出 原样回来。
     * 现改为**一次性迁移**（标记表 {@code sys_startup_migration}，标记名 {@code button_action_extend_v1}）：
     * 跑过即打标记、以后永不重放，用户的按钮勾选由此成为最终权威。</p>
     */
    private void ensureButtonPermissionNewActions() {
        final String buttonType = "按钮";
        // 2026-09-20：全站按钮组件化后，动作词表从 33 个扩到 70 个
        // （新增查询/重置/刷新/查看/列设置/保存类/上传下载/选择/明细行/弹窗 确定取消关闭/业务扩展等）。
        // 词表唯一事实来源 = 前端 src/utils/pagePerm.js 的 BUTTON_ACTION_GROUPS，两边必须同步。
        final String[] newActions = {
                // 基础操作
                "query", "reset", "refresh", "view", "add", "edit", "delete", "export", "import", "print",
                "columnSetting",
                // 编辑与保存
                "save", "saveDraft", "saveAndBack", "copy", "upload", "download", "downloadTemplate",
                "pick", "addRow", "deleteRow", "expand", "collapse", "selectAll", "invertSelect", "fill",
                // 弹窗按钮
                "dlgConfirm", "dlgCancel", "dlgClose",
                // 单据流转
                "submit", "audit", "reject", "reverseApprove", "close", "reopen", "void", "unvoid",
                "confirm", "release", "start", "complete", "startCheck", "finishCheck", "issue", "ship",
                "closeOrder", "allocateCost", "writeOff", "reverseWriteOff", "enable", "disable",
                "setDefault", "startSample", "completeSample", "confirmSample", "pushTo",
                // 业务扩展
                "assign", "claim", "recycle", "convert", "setPrimary", "generatePurchase",
                "generateWorkOrder", "generateSubcontract", "markRead", "preview", "apply",
                "resetDefault", "testConnection", "refreshCache", "enableAll", "disableAll", "clear",
                "deploy", "rollback", "pack"
        };
        try {
            // 一次性迁移（2026-10-10 修正）：本迁移原先每次启动无条件重放，会把用户后来
            // 在「角色权限设置 → 按钮权限」里**取消勾选**的按钮（如物料档案的导入/导出）重新补回，
            // 表现为"关掉了过一会儿又出来"。现用标记表只跑一次，跑过即跳过。
            final String migrationMark = "button_action_extend_v1";
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_startup_migration "
                    + "(migration_name VARCHAR(200) PRIMARY KEY, applied_at TIMESTAMP NOT NULL DEFAULT now())");
            Integer migrationApplied = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_startup_migration WHERE migration_name = ?",
                    Integer.class, migrationMark);
            if (migrationApplied != null && migrationApplied > 0) {
                return;
            }
            List<Map<String, Object>> groups = jdbcTemplate.queryForList(
                    "SELECT role_id, split_part(menu_id, ':', 1) || ':' || split_part(menu_id, ':', 2) AS object_code "
                            + "FROM sys_role_permission WHERE permission_type = ? "
                            + "GROUP BY 1, 2 HAVING COUNT(*) >= 7", buttonType);
            int inserted = 0;
            for (Map<String, Object> g : groups) {
                Object roleId = g.get("role_id");
                String objectCode = (String) g.get("object_code");
                if (roleId == null || objectCode == null) {
                    continue;
                }
                for (String act : newActions) {
                    String code = objectCode + ":" + act;
                    Integer exists = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM sys_role_permission WHERE role_id = ? AND permission_type = ? AND menu_id = ?",
                            Integer.class, roleId, buttonType, code);
                    if (exists == null || exists == 0) {
                        jdbcTemplate.update(
                                "INSERT INTO sys_role_permission (role_id, menu_id, permission_type, created_at) "
                                        + "VALUES (?, ?, ?, now())",
                                roleId, code, buttonType);
                        inserted++;
                    }
                }
            }
            // 打上一次性迁移标记：此后不再重放，用户在按钮权限里的取消勾选即为最终结果
            jdbcTemplate.update("INSERT INTO sys_startup_migration (migration_name) VALUES (?) "
                    + "ON CONFLICT (migration_name) DO NOTHING", migrationMark);
            if (inserted > 0) {
                log.info("[启动自检] 按钮权限动作扩展：为整组勾选过的角色补齐 {} 条新增动作码（submit/reverseApprove 等）", inserted);
            }
        } catch (Exception e) {
            log.warn("[启动自检] 按钮权限动作扩展迁移跳过（表可能不存在）: {}", e.getMessage());
        }
    }

    /**
     * 【数据修复·幂等】单据终态显示名统一：{@code 已审核 → 已生效}（2026-09-19 用户拍板）。
     *
     * <p>背景：全系统终态显示名由「已审核」统一改为「已生效」，代码侧 {@link com.erp.common.bill.BillStatusEnum}
     * 与 {@link com.erp.common.bill.BillStatusUtils} 已改，但**存量数据**（部分单据表的主状态列、
     * 全系统的 approval_status 列、企业语言包键名）仍是旧名，会造成"列表按状态筛选筛不出来"。
     *
     * <p>为什么放在这里而不是只写一个 SQL 脚本：多租户是**物理隔离多库**，一次性 SQL 只覆盖执行时那个库，
     * 每个租户库不会自动跑。放启动兜底 → 每个租户库启动时各自幂等迁移一次。
     *
     * <p>实现：用 information_schema 动态列出所有**字符型** status / approval_status / bill_status 列，
     * 逐列 UPDATE，避免手写表清单漏表（新增单据自动覆盖）。数字型状态列（0/1/2/7）不动。
     * 表名/列名来源是 information_schema（非用户输入），无注入面。重复执行命中 0 行。
     */
    private void ensureStatusLabelRenamed() {
        try {
            List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                    "SELECT table_name, column_name FROM information_schema.columns " +
                    " WHERE table_schema = current_schema() " +
                    "   AND column_name IN ('status', 'approval_status', 'bill_status') " +
                    "   AND data_type IN ('character varying', 'character', 'text')");
            int rows = 0;
            for (Map<String, Object> c : cols) {
                String table = String.valueOf(c.get("table_name"));
                String col = String.valueOf(c.get("column_name"));
                rows += jdbcTemplate.update(
                        "UPDATE " + table + " SET " + col + " = ? WHERE " + col + " = ?",
                        "已生效", "已审核");
            }
            if (rows > 0) {
                log.info("[SchemaUpgradeEnsurer] 单据状态显示名迁移 已审核→已生效 完成（{} 行 / {} 列）", rows, cols.size());
            }

            // BOM 表 status 列默认值同步改为「已生效」（新建 BOM 不再落旧名）
            ensureColumnDefault("bom", "status", "已生效");

            // 企业语言包键名同步：旧键（含「已审核」/「反审批」）→ 新名。
            // 先删除"改完会撞唯一键"的旧行，再整体改名；英文行文本不含中文，不受影响。
            renameI18nKey("已审核", "已生效");
            renameI18nKey("反审批", "重置草稿");
        } catch (Exception e) {
            // 当前库无相应表等情况忽略，绝不拉挂启动
            log.warn("[SchemaUpgradeEnsurer] 单据状态显示名迁移跳过: {}", e.getMessage());
        }
    }

    /** 【幂等】把列默认值改为指定文本（列不存在/无权限时静默跳过） */
    private void ensureColumnDefault(String table, String column, String defaultText) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " SET DEFAULT '" + defaultText + "'");
        } catch (Exception e) {
            log.debug("[SchemaUpgradeEnsurer] {}.{} 默认值调整跳过: {}", table, column, e.getMessage());
        }
    }

    /** 【幂等】语言包键名与中文文本里的旧名替换为新名（替换后撞唯一键的旧行先删） */
    private void renameI18nKey(String from, String to) {
        try {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM sys_i18n a WHERE a.i18n_key LIKE ? " +
                    "  AND EXISTS (SELECT 1 FROM sys_i18n b WHERE b.i18n_key = REPLACE(a.i18n_key, ?, ?) AND b.lang = a.lang)",
                    "%" + from + "%", from, to);
            int updated = jdbcTemplate.update(
                    "UPDATE sys_i18n SET i18n_key = REPLACE(i18n_key, ?, ?), text = REPLACE(text, ?, ?) WHERE i18n_key LIKE ?",
                    from, to, from, to, "%" + from + "%");
            if (deleted > 0 || updated > 0) {
                log.info("[SchemaUpgradeEnsurer] 语言包键名迁移 {}→{}：改名 {} 行 / 去重删除 {} 行", from, to, updated, deleted);
            }
        } catch (Exception e) {
            log.debug("[SchemaUpgradeEnsurer] 语言包键名迁移 {}-{} 跳过: {}", from, to, e.getMessage());
        }
    }

    /**
     * 【数据修复·幂等】生产工单状态机对齐全站通用口径（2026-09-20）。
     *
     * <p><b>背景</b>：生产工单原把业务执行态（已下达 / 执行中 / 已完工）写进 status，偏离全站通用模型
     * （status 只有 草稿/待审核/已生效/已作废；闭环用独立字段 close_status）。代码侧已收敛，
     * 存量数据必须同步：否则老库重启后「列表按 已生效 筛选筛不到工单」、下推守卫按新口径判定会全部被拦。
     *
     * <p><b>为什么放启动兜底而不是只写一次性 SQL</b>：多租户是<b>物理隔离多库</b>，
     * 一次性脚本只覆盖执行时那一个库，其它租户库不会自动跑。放这里 → 每个租户库启动各自幂等迁移一次。
     *
     * <p>实现：三条固定旧值 UPDATE（已下达/执行中/已完工 → 已生效；已关闭 → 已生效 + close_status=1；close_status NULL → 0），
     * 重复启动命中 0 行。表不存在（如平台库）时异常被吞，绝不拉挂启动。
     * 等价脚本：{@code deploy/sql/64_workorder_status_unify.sql} / {@code db/106_workorder_status_unify.sql}。
     */
    private void ensureWorkOrderStatusUnified() {
        try {
            int a = jdbcTemplate.update(
                    "UPDATE production_order SET status = ? WHERE status IN (?, ?, ?)",
                    "已生效", "已下达", "执行中", "已完工");
            int b = jdbcTemplate.update(
                    "UPDATE production_order SET status = ?, close_status = 1, is_closed = 1 WHERE status = ?",
                    "已生效", "已关闭");
            int c = jdbcTemplate.update(
                    "UPDATE production_order SET close_status = 0 WHERE close_status IS NULL");
            if (a + b + c > 0) {
                log.info("[SchemaUpgradeEnsurer] 生产工单状态机对齐：执行态→已生效 {} 行 / 已关闭→已结案 {} 行 / close_status 兜底 {} 行",
                        a, b, c);
            }
        } catch (Exception e) {
            // 当前库无 production_order 表等情况忽略，绝不拉挂启动
            log.warn("[SchemaUpgradeEnsurer] 生产工单状态机对齐跳过: {}", e.getMessage());
        }
    }

    /**
     * 【数据修复·幂等】工艺路线从"单据"降为"主档"（2026-09-23 用户口径）。
     *
     * <p><b>背景</b>：工艺路线原走单据式状态机（草稿 → 已生效 → 已停用 + 审核人/审核时间/审批状态），
     * 用户明确要求"剔出单据类型：只有启用和停用两种，不走审批与状态机制"。
     * 代码侧已收敛（保存默认启用、去掉审核动作），存量数据必须同步：
     * 否则老库里草稿/已生效的路线与新的 2 值筛选口径对不上（按"启用"筛不到、按"停用"筛出未审核的）。
     *
     * <p>为什么放启动兜底：多租户是物理隔离多库，一次性脚本只覆盖执行时那一个库。
     * 等价脚本：{@code deploy/sql/115_work_operation_visual.sql} / {@code db/115_work_operation_visual.sql}。
     */
    private void ensureRoutingMasterUnified() {
        try {
            int a = jdbcTemplate.update(
                    "UPDATE routing SET status = ? WHERE status IS NULL OR status IN (?, ?, ?, ?, ?, ?)",
                    "停用", "草稿", "已驳回", "DRAFT", "REJECTED", "待审核", "PENDING");
            int b = jdbcTemplate.update(
                    "UPDATE routing SET status = ? WHERE status IN (?, ?)",
                    "启用", "已生效", "APPROVED");
            int c = jdbcTemplate.update(
                    "UPDATE routing SET is_enabled = CASE WHEN status = ? THEN 1 ELSE 0 END"
                            + " WHERE is_enabled IS NULL OR is_enabled <> (CASE WHEN status = ? THEN 1 ELSE 0 END)",
                    "启用", "启用");
            if (a + b + c > 0) {
                log.info("[SchemaUpgradeEnsurer] 工艺路线主档化对齐：未审核→停用 {} 行 / 已生效→启用 {} 行 / 启停标志同步 {} 行",
                        a, b, c);
            }
        } catch (Exception e) {
            // 当前库无 routing 表等情况忽略，绝不拉挂启动
            log.warn("[SchemaUpgradeEnsurer] 工艺路线主档化对齐跳过: {}", e.getMessage());
        }
    }

    /**
     * AI用量管理菜单路径修正（2026-10-12，幂等）：menu_code=system_ai_token_usage 的行
     * route_path 误为 /system/settings（与设置中心主行重复）→ 修正为
     * /system/settings?item=ai-token-usage（144 版本划分脚本同口径）。
     * 只在"错误值仍存在"时 UPDATE，重复启动 0 副作用；表不存在时异常由调用方吞。
     */
    private void ensureAiTokenUsageRouteFix(JdbcTemplate target) {
        int n = target.update(
                "UPDATE sys_menu SET route_path = '/system/settings?item=ai-token-usage' "
              + "WHERE menu_code = 'system_ai_token_usage' AND route_path = '/system/settings'");
        if (n > 0) {
            log.info("[SchemaUpgradeEnsurer] AI用量管理菜单路径已修正: {} 行", n);
        }
    }

    /**
     * 【初始化·幂等】车间工序可视化默认配置（2026-09-23）。
     *
     * <p>自动编号设置新增「工序(ROUTING_OP)」—— 工序编码一律自动生成（用户口径"编号的地方默认都是自动生成"）。
     *
     * <p>写法：先 COUNT 再 INSERT（不用 INSERT...SELECT WHERE NOT EXISTS —— 后者在 PG 里
     * 会对无 FROM 的 SELECT 参数做类型推断，JDBC 参数易报 "could not determine data type"）。
     * 重复启动命中 0 行。表不存在（平台库）时异常被吞。
     */
    private void ensureWorkOperationDefaults() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_serial_no_config WHERE doc_type = ?", Integer.class, "ROUTING_OP");
            int s = 0;
            if (cnt == null || cnt == 0) {
                s = jdbcTemplate.update(
                        "INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length,"
                                + " reset_type, separator, current_serial, \"current_date\", sort_order, enabled,"
                                + " auto_generate, remark) VALUES (?, ?, ?, ?, ?, ?, ?, 0, NULL, 83, 1, 1, ?)",
                        "ROUTING_OP", "工序", "OP", "yyyyMMdd", 4, "DAILY", "-",
                        "工序编号：OP-日期-流水（工艺路线内每道工序自动生成）");
            }
            if (s > 0) {
                log.info("[SchemaUpgradeEnsurer] 工序可视化默认配置：工序编号规则 {} 行", s);
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 工序可视化默认配置跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】工序操作日志表 + 工序状态 8 值口径统一（2026-09-23 第 2 期）。
     *
     * <p>① 新建 {@code work_operation_log}（只增不改不删，用于追溯与绩效）；
     * <br>② 工序状态统一为 8 值：待派工/已派工/已开工/进行中/暂停/完工待检/已完工/已取消。
     * 历史代码写入的是 {@code PENDING / 待开始 / PROCESSING / 进行中 / COMPLETED / 已完成}，
     * 不迁移会导致"新面板按 8 值判定 → 老数据全是未知状态 → 按钮全不出现"（守则 04 存量兼容）。</p>
     *
     * <p>等价脚本：{@code deploy/sql/116_work_operation_log.sql} / {@code db/116_work_operation_log.sql}。</p>
     */
    private void ensureWorkOperationLog() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS work_operation_log ("
                    + " log_id BIGSERIAL PRIMARY KEY,"
                    + " order_id BIGINT, order_no VARCHAR(50),"
                    + " operation_row_id BIGINT, operation_code VARCHAR(50), operation_name VARCHAR(200),"
                    + " action_type VARCHAR(30), from_status VARCHAR(20), to_status VARCHAR(20),"
                    + " quantity NUMERIC(18,4), rejected_quantity NUMERIC(18,4),"
                    + " operator_id BIGINT, operator_name VARCHAR(50),"
                    + " operate_time TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,"
                    + " remark VARCHAR(500))");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_work_operation_log_order ON work_operation_log(order_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_work_operation_log_row ON work_operation_log(operation_row_id)");

            int a = jdbcTemplate.update("UPDATE work_order_operation SET operation_status = ?"
                    + " WHERE operation_status IS NULL OR operation_status IN (?, ?)", "待派工", "PENDING", "待开始");
            int b = jdbcTemplate.update("UPDATE work_order_operation SET operation_status = ?"
                    + " WHERE operation_status = ?", "进行中", "PROCESSING");
            int c = jdbcTemplate.update("UPDATE work_order_operation SET operation_status = ?"
                    + " WHERE operation_status IN (?, ?)", "已完工", "COMPLETED", "已完成");
            try {
                jdbcTemplate.execute("ALTER TABLE work_order_operation ALTER COLUMN operation_status SET DEFAULT '待派工'");
            } catch (Exception ignored) {
                // 老库列可能不存在默认值约束，忽略
            }
            if (a + b + c > 0) {
                log.info("[SchemaUpgradeEnsurer] 工序状态 8 值迁移：未开始→待派工 {} 行 / 进行中 {} 行 / 已完成→已完工 {} 行", a, b, c);
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 工序操作日志表/状态迁移跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】质检模块建表（2026-09-28 第 12 期）。
     *
     * <p>① {@code qc_template} / {@code qc_template_item}：质检模板头 + 检验项（按物料或工序绑定，默认不绑定）；
     * <br>② {@code qc_record} / {@code qc_record_item}：工序检验记录（首检/抽检/全检三种判定）+ 实测明细。
     *
     * <p>等价脚本：{@code deploy/sql/123_qc.sql} / {@code db/123_qc.sql}。</p>
     */
    private void ensureQcTables() {
        ensureQcTablesOn(jdbcTemplate);
    }

    /**
     * 【初始化·幂等】化妆品批记录完整闭环（2026-11-06）。
     *
     * <p>建 5 张表：放行单 / 留样登记 / 批生产记录头 / 记录更正 / 电子签名记录；
     * 补 batch_info 质量状态 5 列与 qc_record 检验环节 5 列；种 2 个参数与化妆品成品默认检验模板。
     * 全部 IF NOT EXISTS + NOT EXISTS，重复启动 0 副作用。等价脚本：{@code deploy/sql/177/178}。</p>
     */
    private void ensureBatchRecordTablesOn(JdbcTemplate target) {
        try {
            target.execute("CREATE TABLE IF NOT EXISTS release_order ("
                    + " release_id BIGSERIAL PRIMARY KEY, release_no VARCHAR(64) UNIQUE,"
                    + " batch_id BIGINT NOT NULL, batch_no VARCHAR(64),"
                    + " material_id BIGINT, material_code VARCHAR(64), material_name VARCHAR(128),"
                    + " work_order_id BIGINT, work_order_no VARCHAR(64), qc_record_id BIGINT,"
                    + " release_result VARCHAR(20) DEFAULT '放行',"
                    + " released_by VARCHAR(64), released_at TIMESTAMP,"
                    + " status VARCHAR(20) DEFAULT '草稿', approval_status VARCHAR(20) DEFAULT '无',"
                    + " bill_before_status INTEGER, close_status INTEGER DEFAULT 0,"
                    + " approved_by VARCHAR(64), approved_at TIMESTAMP,"
                    + " closed_by VARCHAR(64), closed_at TIMESTAMP, closed_reason VARCHAR(255),"
                    + " voided_by VARCHAR(64), voided_at TIMESTAMP, void_reason VARCHAR(255),"
                    + " remark VARCHAR(255), created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW(),"
                    + " created_by VARCHAR(64), updated_by VARCHAR(64))");

            target.execute("CREATE TABLE IF NOT EXISTS sample_retention ("
                    + " retention_id BIGSERIAL PRIMARY KEY, retention_no VARCHAR(64) UNIQUE,"
                    + " batch_id BIGINT NOT NULL, batch_no VARCHAR(64),"
                    + " material_id BIGINT, material_code VARCHAR(64), material_name VARCHAR(128),"
                    + " retain_qty NUMERIC(18,4) DEFAULT 0, store_location VARCHAR(128),"
                    + " retain_by VARCHAR(64), retain_date DATE, keep_until DATE,"
                    + " remind_status VARCHAR(20) DEFAULT '未到期', status VARCHAR(20) DEFAULT '草稿',"
                    + " remark VARCHAR(255), created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW(),"
                    + " created_by VARCHAR(64), updated_by VARCHAR(64))");

            target.execute("CREATE TABLE IF NOT EXISTS batch_production_record ("
                    + " record_id BIGSERIAL PRIMARY KEY, work_order_id BIGINT UNIQUE NOT NULL,"
                    + " work_order_no VARCHAR(64), product_id BIGINT, product_code VARCHAR(64),"
                    + " product_name VARCHAR(128), batch_no VARCHAR(64), bom_snapshot JSONB,"
                    + " routing_snapshot JSONB, env_temperature NUMERIC(8,2), env_humidity NUMERIC(8,2),"
                    + " env_recorder VARCHAR(64), env_record_time TIMESTAMP,"
                    + " status VARCHAR(20) DEFAULT '生成中',"
                    + " created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW())");

            target.execute("CREATE TABLE IF NOT EXISTS record_correction ("
                    + " correction_id BIGSERIAL PRIMARY KEY, target_table VARCHAR(64) NOT NULL,"
                    + " target_id BIGINT NOT NULL, target_field VARCHAR(64),"
                    + " old_value TEXT, new_value TEXT, reason VARCHAR(255) NOT NULL,"
                    + " corrected_by VARCHAR(64), corrected_at TIMESTAMP DEFAULT NOW(),"
                    + " signed SMALLINT DEFAULT 0)");

            target.execute("CREATE TABLE IF NOT EXISTS signature_record ("
                    + " signature_id BIGSERIAL PRIMARY KEY, action_type VARCHAR(32) NOT NULL,"
                    + " target_table VARCHAR(64), target_id BIGINT, target_desc VARCHAR(255),"
                    + " signer_name VARCHAR(64) NOT NULL, sign_time TIMESTAMP DEFAULT NOW(),"
                    + " verify_result VARCHAR(20) DEFAULT '通过',"
                    + " sign_image TEXT, sign_method VARCHAR(20) DEFAULT '手写')");
            // 电子签名改为手写签名（2026-11-06）：签名图 + 签名方式（存量记录按「密码」口径）
            target.execute("ALTER TABLE signature_record ADD COLUMN IF NOT EXISTS sign_image TEXT");
            target.execute("ALTER TABLE signature_record ADD COLUMN IF NOT EXISTS sign_method VARCHAR(20)");
            target.execute("UPDATE signature_record SET sign_method = '密码' WHERE sign_method IS NULL OR sign_method = ''");
            target.execute("ALTER TABLE signature_record ALTER COLUMN sign_method SET DEFAULT '手写'");

            // 批次档案质量列（独立于库存语义的 status 列）
            target.execute("ALTER TABLE batch_info ADD COLUMN IF NOT EXISTS supplier_batch_no VARCHAR(64)");
            target.execute("ALTER TABLE batch_info ADD COLUMN IF NOT EXISTS qc_status VARCHAR(20) DEFAULT '合格'");
            target.execute("ALTER TABLE batch_info ADD COLUMN IF NOT EXISTS qc_result VARCHAR(20)");
            target.execute("ALTER TABLE batch_info ADD COLUMN IF NOT EXISTS released_by VARCHAR(64)");
            target.execute("ALTER TABLE batch_info ADD COLUMN IF NOT EXISTS released_at TIMESTAMP");
            // 存量批次按历史口径视为合格（不拦出库）
            target.execute("UPDATE batch_info SET qc_status = '合格' WHERE qc_status IS NULL");

            // 质检记录扩展列
            target.execute("ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS qc_stage VARCHAR(20) DEFAULT '过程检'");
            target.execute("ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS batch_id BIGINT");
            target.execute("ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS batch_no VARCHAR(64)");
            target.execute("ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS reviewer_id BIGINT");
            target.execute("ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS reviewer_name VARCHAR(64)");

            // 参数种子（缺则补，不动已改值）
            target.execute("INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,"
                    + " config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)"
                    + " SELECT 'batch.release.outbound.control', '预警提示', '未放行批次出库控制', 'inventory', '库存参数',"
                    + " 'select',"
                    + " '[{\"label\":\"允许\",\"value\":\"允许\"},{\"label\":\"预警提示\",\"value\":\"预警提示\"},"
                    + "{\"label\":\"不允许\",\"value\":\"不允许\"}]', 65,"
                    + " '控制销售出库时未完成检验放行的成品批次：允许=不拦截；预警提示=弹窗提示后可继续；不允许=直接拦截。仅对启用批次管理的物料生效',"
                    + " NOW(), NOW(), 'system', 'system'"
                    + " WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'batch.release.outbound.control')");
            target.execute("INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,"
                    + " config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)"
                    + " SELECT 'batch.sample.keep.months', '24', '留样保存期限', 'inventory', '库存参数',"
                    + " 'number', 66, '成品放行自动生成留样任务时的默认保存月数', NOW(), NOW(), 'system', 'system'"
                    + " WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'batch.sample.keep.months')");

            log.info("[SchemaUpgradeEnsurer] 批记录模块自检：5 张表就绪，批次质量列就绪，"
                    + "未放行出库控制={}，留样保存月数={}",
                    queryOneString(target, "SELECT config_value FROM sys_config WHERE config_key = 'batch.release.outbound.control'"),
                    queryOneString(target, "SELECT config_value FROM sys_config WHERE config_key = 'batch.sample.keep.months'"));
        } catch (Exception e) {
            // 绝不因为 schema 检查异常把启动拉挂
            log.warn("[SchemaUpgradeEnsurer] 批记录模块建表/补列跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】批次号规则生效 + 批次溯源支撑（2026-11-06）。
     *
     * <p>① 溯源查询索引（库存流水按批次号+物料、各单据明细按批号）；
     * <br>② 批次唯一口径由「批次号全局唯一」改为「物料+批次号唯一」（先删旧唯一约束，再建联合唯一索引）；
     * <br>③ 补齐「启用批次管理」「允许批次货品重复入库」两个参数，已有值不覆盖。
     * <br>等价脚本：{@code deploy/sql/183_batch_trace_and_rules.sql}。</p>
     */
    private void ensureBatchTraceSupportOn(JdbcTemplate target) {
        try {
            target.execute("CREATE INDEX IF NOT EXISTS idx_ledger_batch_material ON inventory_ledger (batch_no, material_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_production_receipt_item_batch ON production_receipt_item (batch_no)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_material_requisition_item_batch ON material_requisition_item (batch_no)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_sale_delivery_item_batch ON sale_delivery_item (batch_no)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_purchase_receipt_item_batch ON purchase_receipt_item (batch_no)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_other_receipt_item_batch ON other_receipt_item (batch_no)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_batch_material ON batch (material_id)");

            target.execute("DO $$ "
                    + "DECLARE c record; "
                    + "BEGIN "
                    + "  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = current_schema()"
                    + "                  AND tablename = 'batch' AND indexname = 'uk_batch_material_batch_no') THEN "
                    + "    FOR c IN SELECT conname FROM pg_constraint"
                    + "              WHERE conrelid = 'batch'::regclass AND contype = 'u'"
                    + "                AND conkey = ARRAY[(SELECT attnum FROM pg_attribute"
                    + "                                     WHERE attrelid = 'batch'::regclass AND attname = 'batch_no')]::smallint[]"
                    + "    LOOP "
                    + "      EXECUTE format('ALTER TABLE batch DROP CONSTRAINT %I', c.conname); "
                    + "    END LOOP; "
                    + "    EXECUTE 'CREATE UNIQUE INDEX uk_batch_material_batch_no ON batch (material_id, batch_no)'; "
                    + "  END IF; "
                    + "END $$");

            target.execute("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                    + " config_section, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)"
                    + " SELECT 'inv.allow.batch.repeat.inbound', 'false', '允许批次货品重复入库', 'inventory', '库存管理参数',"
                    + " 'boolean', 77, '关闭时同一物料同一批次号只能入库一次；开启时同批次入库数量累加', NOW(), NOW(), 'system', 'system'"
                    + " WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'inv.allow.batch.repeat.inbound')");
            log.info("[SchemaUpgradeEnsurer] 批次规则/溯源自检就绪：索引就绪，批次唯一口径=物料+批次号");
        } catch (Exception e) {
            // 绝不因为 schema 检查异常把启动拉挂
            log.warn("[SchemaUpgradeEnsurer] 批次规则/溯源自检跳过: {}", e.getMessage());
        }
    }

    /** 单值查询小工具（查不到或异常返回 null，不抛） */
    /**
     * 【初始化·幂等】基础资料联动存量回填（2026-11-08）。
     *
     * <p>销售订单/报价/合同的历史客户简称、报价客户编号、采购合同的供应商编号与简称，
     * 按客户/供应商档案补齐；只填空值，绝不覆盖用户已手工填写的内容。
     * 等价脚本：{@code deploy/sql/188_master_linkage_fields.sql}。</p>
     */
    private void ensureMasterLinkageBackfillOn(JdbcTemplate target) {
        try {
            target.execute("UPDATE sale_order o SET customer_short_name = c.short_name"
                    + " FROM customer c WHERE o.customer_id = c.customer_id"
                    + " AND COALESCE(o.customer_short_name,'') = '' AND COALESCE(c.short_name,'') <> ''");
            target.execute("UPDATE sale_quote q SET customer_short_name = c.short_name"
                    + " FROM customer c WHERE q.customer_id = c.customer_id"
                    + " AND COALESCE(q.customer_short_name,'') = '' AND COALESCE(c.short_name,'') <> ''");
            target.execute("UPDATE sale_contract t SET customer_short_name = c.short_name"
                    + " FROM customer c WHERE t.customer_id = c.customer_id"
                    + " AND COALESCE(t.customer_short_name,'') = '' AND COALESCE(c.short_name,'') <> ''");
            target.execute("UPDATE sale_quote q SET customer_no = c.customer_code"
                    + " FROM customer c WHERE q.customer_id = c.customer_id"
                    + " AND COALESCE(q.customer_no,'') = '' AND COALESCE(c.customer_code,'') <> ''");
            target.execute("UPDATE purchase_contract p SET supplier_no = s.supplier_code"
                    + " FROM supplier s WHERE p.supplier_id = s.supplier_id"
                    + " AND COALESCE(p.supplier_no,'') = '' AND COALESCE(s.supplier_code,'') <> ''");
            target.execute("UPDATE purchase_contract p SET supplier_short_name = s.short_name"
                    + " FROM supplier s WHERE p.supplier_id = s.supplier_id"
                    + " AND COALESCE(p.supplier_short_name,'') = '' AND COALESCE(s.short_name,'') <> ''");
            log.info("[SchemaUpgradeEnsurer] 基础资料联动回填完成：简称与编号只填空值");
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 基础资料联动回填跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】入库单质检闭环 + 仓库统一选择器（2026-11-07）。
     *
     * <p>复用既有质检模块表（qc_template/qc_template_item/qc_record/qc_record_item）：
     * 模板扩模板类型/单据绑定；质检记录扩来源入库单、按物料行的合格与不合格数量、处理方式、报废仓。
     * 三张入库单补质检开关/合格数量；采购入库明细补仓库。全部 ADD COLUMN IF NOT EXISTS，
     * 重复启动 0 副作用。等价脚本：{@code deploy/sql/186_inbound_qc_and_warehouse.sql}。</p>
     */
    private void ensureInboundQcSupportOn(JdbcTemplate target) {
        try {
            // 一、质检模板扩展
            target.execute("ALTER TABLE qc_template ADD COLUMN IF NOT EXISTS template_type VARCHAR(20) DEFAULT 'MATERIAL'");
            target.execute("ALTER TABLE qc_template ADD COLUMN IF NOT EXISTS bill_types VARCHAR(300)");
            target.execute("ALTER TABLE qc_template ADD COLUMN IF NOT EXISTS is_default SMALLINT DEFAULT 0");
            target.execute("UPDATE qc_template SET template_type = 'MATERIAL' WHERE template_type IS NULL");
            target.execute("UPDATE qc_template SET is_default = 0 WHERE is_default IS NULL");
            target.execute("CREATE INDEX IF NOT EXISTS idx_qc_template_type ON qc_template (template_type, is_enabled)");

            // 二、质检记录/明细扩展
            target.execute("ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50)");
            target.execute("ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS source_bill_id BIGINT");
            target.execute("ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS scrap_warehouse_id BIGINT");
            target.execute("ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS scrap_warehouse_name VARCHAR(100)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_qc_record_source ON qc_record (source_bill_type, source_bill_id)");

            target.execute("ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS material_id BIGINT");
            target.execute("ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS material_code VARCHAR(50)");
            target.execute("ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS material_name VARCHAR(200)");
            target.execute("ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS handling_type VARCHAR(20) DEFAULT 'RELEASE'");
            target.execute("ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS scrap_warehouse_id BIGINT");
            target.execute("ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS scrap_warehouse_name VARCHAR(100)");
            target.execute("ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS ext_data TEXT");
            target.execute("CREATE INDEX IF NOT EXISTS idx_qc_record_item_material ON qc_record_item (record_id, material_id)");

            // 三、采购入库单
            target.execute("ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS qc_enabled SMALLINT NOT NULL DEFAULT 0");
            target.execute("ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS qc_template_id BIGINT");
            target.execute("ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS qc_status VARCHAR(20) DEFAULT '未质检'");
            target.execute("ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS rejected_amount NUMERIC(18,4) DEFAULT 0");
            target.execute("ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS payable_amount NUMERIC(18,4)");
            target.execute("ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS stock_id BIGINT");
            target.execute("ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS stock_name VARCHAR(100)");
            target.execute("ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS handling_type VARCHAR(20) DEFAULT 'RELEASE'");
            target.execute("ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS scrap_warehouse_id BIGINT");
            target.execute("ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS scrap_warehouse_name VARCHAR(100)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_purchase_receipt_item_stock ON purchase_receipt_item (stock_id)");

            // 四、生产入库单
            target.execute("ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS qc_enabled SMALLINT NOT NULL DEFAULT 0");
            target.execute("ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS qc_template_id BIGINT");
            target.execute("ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS qc_status VARCHAR(20) DEFAULT '未质检'");
            target.execute("ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE production_receipt_item ADD COLUMN IF NOT EXISTS handling_type VARCHAR(20) DEFAULT 'RELEASE'");
            target.execute("ALTER TABLE production_receipt_item ADD COLUMN IF NOT EXISTS scrap_warehouse_id BIGINT");
            target.execute("ALTER TABLE production_receipt_item ADD COLUMN IF NOT EXISTS scrap_warehouse_name VARCHAR(100)");

            // 五、委外入库单
            target.execute("ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS qc_enabled SMALLINT NOT NULL DEFAULT 0");
            target.execute("ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS qc_template_id BIGINT");
            target.execute("ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS qc_status VARCHAR(20) DEFAULT '未质检'");
            target.execute("ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS rejected_amount NUMERIC(18,4) DEFAULT 0");
            target.execute("ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS payable_amount NUMERIC(18,4)");
            target.execute("ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4)");
            target.execute("ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS handling_type VARCHAR(20) DEFAULT 'RELEASE'");
            target.execute("ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS scrap_warehouse_id BIGINT");
            target.execute("ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS scrap_warehouse_name VARCHAR(100)");

            // 六、成本核算报废损失 + 质检自动退货单库存影响标志
            target.execute("ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS scrap_loss NUMERIC(18,4) DEFAULT 0");
            target.execute("ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS stock_effect SMALLINT DEFAULT 1");
            target.execute("ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS stock_effect SMALLINT DEFAULT 1");
            target.execute("UPDATE purchase_return SET stock_effect = 1 WHERE stock_effect IS NULL");
            target.execute("UPDATE subcontract_return SET stock_effect = 1 WHERE stock_effect IS NULL");
            // 销售退货：1=回库增加库存；0=客户不寄回，仅登记与财务冲减（2026-11-28）
            target.execute("ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS stock_effect SMALLINT DEFAULT 1");
            target.execute("UPDATE sale_return SET stock_effect = 1 WHERE stock_effect IS NULL");
            // 免费样品单：发货是否已扣库存（2026-11-28），作废/删除据此决定是否冲回
            target.execute("ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS stock_deducted SMALLINT DEFAULT 0");
            target.execute("UPDATE free_sample_order SET stock_deducted = 0 WHERE stock_deducted IS NULL");

            // 七、默认报废仓 + 参数（缺则补，不动已改值）
            target.execute("INSERT INTO warehouse (stock_code, stock_name, stock_type, warehouse_type, status,"
                    + " is_enabled, is_mrp_available, sort_order, created_at, updated_at, created_by, updated_by, remark)"
                    + " SELECT 'SCRAP', '报废仓', '报废仓', '报废仓', '启用', 1, 0, 999, NOW(), NOW(), 'system', 'system',"
                    + " '质检不合格默认入此仓'"
                    + " WHERE NOT EXISTS (SELECT 1 FROM warehouse WHERE stock_code = 'SCRAP')");
            target.execute("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                    + " config_section, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)"
                    + " SELECT 'inv.scrap.warehouse.id',"
                    + " (SELECT CAST(stock_id AS VARCHAR) FROM warehouse WHERE stock_code = 'SCRAP' LIMIT 1),"
                    + " '默认报废仓', 'inventory', '库存管理参数', 'number', 78,"
                    + " '质检不合格选择进报废仓时默认入此仓，质检行可另选', NOW(), NOW(), 'system', 'system'"
                    + " WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'inv.scrap.warehouse.id')");
            log.info("[SchemaUpgradeEnsurer] 入库单质检/仓库自检就绪：模板与质检记录扩展列、三张入库单质检列、默认报废仓");
        } catch (Exception e) {
            // 绝不因为 schema 检查异常把启动拉挂
            log.warn("[SchemaUpgradeEnsurer] 入库单质检/仓库自检跳过: {}", e.getMessage());
        }
    }

    /** 单值查询小工具（查不到或异常返回 null，不抛） */
    private String queryOneString(JdbcTemplate target, String sql) {
        try {
            String v = target.queryForObject(sql, String.class);
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 【初始化·幂等】行级 BOM 列（2026-11-06）。
     *
     * <p>生产工单成品明细（bom_id/bom_code/bom_version）、销售 6 单据明细（同三列）、
     * MRP 计划明细（bom_code/bom_version）。存量行留空，不回填编造。
     * 全部 ADD COLUMN IF NOT EXISTS，重复启动 0 副作用。等价脚本：{@code deploy/sql/180_line_level_bom.sql}。</p>
     */
    /**
     * 【初始化·幂等】多单位换算（2026-11-09：基础资料维护换算率，单据只读带出）。
     *
     * <p>① 物料档案加辅计量开关、辅计量单位、换算方式、辅换算率；
     * <br>② 各单据明细只补两个隐藏折算列（conversion_rate / base_quantity），<b>界面不新增列</b>。
     * <br>全部 {@code IF NOT EXISTS}；历史数据 base_quantity 留空，业务侧读时回落 quantity（等价于换算率 1）。</p>
     *
     * <p>等价脚本：{@code deploy/sql/189_multi_unit.sql}。</p>
     */
    private void ensureMultiUnitSupportOn(JdbcTemplate target) {
        try {
            // ① 物料档案：开关 + 辅计量单位 + 换算方式 + 辅换算率
            target.execute("ALTER TABLE material ADD COLUMN IF NOT EXISTS multi_unit_enabled BOOLEAN DEFAULT FALSE");
            target.execute("ALTER TABLE material ADD COLUMN IF NOT EXISTS aux_unit_id BIGINT");
            target.execute("ALTER TABLE material ADD COLUMN IF NOT EXISTS aux_rate_mode VARCHAR(10) DEFAULT '固定'");
            target.execute("ALTER TABLE material ADD COLUMN IF NOT EXISTS aux_conversion_rate NUMERIC(18,8)");

            // ② 单据明细：只加两个隐藏折算列
            String[] itemTables = {
                    "sale_quote_item", "sale_order_item", "sale_contract_item",
                    "sale_delivery_item", "sale_return_item", "sale_invoice_item",
                    "purchase_contract_item", "purchase_order_item", "purchase_receipt_item",
                    "purchase_return_item", "purchase_invoice_item", "purchase_request_item",
                    "other_receipt_item", "other_issue_item", "stock_transfer_item",
                    "stock_check_item", "material_requisition_item",
                    "production_receipt_item", "work_order_product", "production_order_item",
                    // 2026-11-10 补充接入：打样单 / 免费样品订单（此前未继承统一明细基类）
                    "sample_order_item", "free_sample_order_item",
                    // 2026-11-10：生产组装单/拆卸单（成品明细 + BOM原料明细）接入多单位换算
                    "produce_assemble_detail", "produce_assemble_bom",
                    "produce_disassemble_detail", "produce_disassemble_bom",
                    "subcontract_order_item", "subcontract_pick_item",
                    "subcontract_inbound_item", "subcontract_return_item", "subcontract_supplement_item"
            };
            for (String t : itemTables) {
                target.execute("ALTER TABLE " + t + " ADD COLUMN IF NOT EXISTS conversion_rate NUMERIC(18,8)");
                target.execute("ALTER TABLE " + t + " ADD COLUMN IF NOT EXISTS base_quantity NUMERIC(18,4)");
                // 委外 4 张明细此前只有单位名称：补单位主键列（其余表已存在，IF NOT EXISTS 无副作用）
                target.execute("ALTER TABLE " + t + " ADD COLUMN IF NOT EXISTS unit_id BIGINT");
            }

            // ③ 换算关系表（2026-11-10 重构第一阶段：一个物料可配多个换算单位，1 对多）
            target.execute("CREATE TABLE IF NOT EXISTS material_unit_conversion ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "material_id BIGINT NOT NULL,"
                    + "unit_id BIGINT NOT NULL,"
                    + "rate_mode VARCHAR(10) DEFAULT '固定',"
                    + "conversion_rate NUMERIC(18,8),"
                    + "sort_order INT DEFAULT 0,"
                    + "created_at TIMESTAMP,"
                    + "updated_at TIMESTAMP,"
                    + "created_by VARCHAR(255),"
                    + "updated_by VARCHAR(255))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_muc_material ON material_unit_conversion(material_id)");
            // 行单位去重（与保存校验双保险）：同一物料同一单位只允许一行
            target.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_muc_material_unit "
                    + "ON material_unit_conversion(material_id, unit_id)");

            // ③' 替换货（2026-11-17）：货品替代清单 + 销售换货单两表
            //     等价脚本：deploy/sql/192_sale_exchange.sql（新库/老库均幂等）
            target.execute("CREATE TABLE IF NOT EXISTS material_substitute ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "material_id BIGINT NOT NULL,"
                    + "substitute_material_id BIGINT NOT NULL,"
                    + "sort_order INT DEFAULT 0,"
                    + "created_at TIMESTAMP, updated_at TIMESTAMP,"
                    + "created_by VARCHAR(255), updated_by VARCHAR(255))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_msub_material ON material_substitute(material_id)");
            target.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_msub_material_sub "
                    + "ON material_substitute(material_id, substitute_material_id)");

            target.execute("CREATE TABLE IF NOT EXISTS sale_exchange ("
                    + "exchange_id BIGSERIAL PRIMARY KEY,"
                    + "exchange_no VARCHAR(64) NOT NULL,"
                    + "exchange_date DATE NOT NULL,"
                    + "customer_id BIGINT NOT NULL,"
                    + "customer_name VARCHAR(255),"
                    + "contact_person VARCHAR(100), contact_phone VARCHAR(50),"
                    + "saler_id BIGINT, saler_name VARCHAR(100), dept_id BIGINT,"
                    + "currency_id BIGINT, currency_code VARCHAR(20),"
                    + "exchange_rate NUMERIC(18,6) DEFAULT 1,"
                    + "tax_calc_method VARCHAR(20),"
                    + "source_bill_type VARCHAR(64), source_bill_no VARCHAR(64),"
                    + "source_id BIGINT, source_doc_id BIGINT,"
                    + "delivery_address VARCHAR(500), remark VARCHAR(500), attachment_urls TEXT,"
                    + "total_quantity NUMERIC(18,4) DEFAULT 0,"
                    + "total_amount NUMERIC(18,2) DEFAULT 0,"
                    + "tax_amount NUMERIC(18,2) DEFAULT 0,"
                    + "discount_amount NUMERIC(18,2) DEFAULT 0,"
                    + "net_amount NUMERIC(18,2) DEFAULT 0,"
                    + "generated_delivery_id BIGINT, generated_delivery_no VARCHAR(64),"
                    + "status VARCHAR(20) DEFAULT '草稿', approval_status VARCHAR(20) DEFAULT '草稿',"
                    + "approved_by VARCHAR(255), approved_at TIMESTAMP,"
                    + "close_status INT DEFAULT 0, closed_by VARCHAR(255), closed_at TIMESTAMP, closed_reason VARCHAR(500),"
                    + "bill_before_status INT, voided_by VARCHAR(255), voided_at TIMESTAMP, void_reason VARCHAR(500),"
                    + "org_code VARCHAR(64),"
                    + "created_at TIMESTAMP, updated_at TIMESTAMP, created_by VARCHAR(255), updated_by VARCHAR(255))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_sexchange_no ON sale_exchange(exchange_no)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_sexchange_source ON sale_exchange(source_id)");

            target.execute("CREATE TABLE IF NOT EXISTS sale_exchange_item ("
                    + "exchange_item_id BIGSERIAL PRIMARY KEY,"
                    + "exchange_id BIGINT NOT NULL, line_no INT,"
                    + "origin_material_id BIGINT, origin_material_code VARCHAR(64), origin_material_name VARCHAR(255),"
                    + "origin_specification VARCHAR(255), origin_unit_id BIGINT, origin_unit VARCHAR(50),"
                    + "origin_unit_price NUMERIC(18,4),"
                    + "material_id BIGINT, material_code VARCHAR(64), material_name VARCHAR(255),"
                    + "specification VARCHAR(255), unit_id BIGINT, unit VARCHAR(50), material_main_image VARCHAR(500),"
                    + "quantity NUMERIC(18,4) DEFAULT 0, unit_price NUMERIC(18,4) DEFAULT 0,"
                    + "tax_rate NUMERIC(8,4) DEFAULT 13, tax_type INT,"
                    + "discount_rate NUMERIC(8,4) DEFAULT 0, discount_amount NUMERIC(18,2) DEFAULT 0,"
                    + "amount NUMERIC(18,2) DEFAULT 0, tax_amount NUMERIC(18,2) DEFAULT 0, net_amount NUMERIC(18,2) DEFAULT 0,"
                    + "price_with_tax NUMERIC(18,4), price_without_tax NUMERIC(18,4),"
                    + "source_bill_type VARCHAR(64), source_bill_no VARCHAR(64), source_line_no INT,"
                    + "source_item_id BIGINT, source_id BIGINT, source_doc_id BIGINT,"
                    + "source_delivery_no VARCHAR(64), source_delivery_id BIGINT, source_delivery_item_id BIGINT,"
                    + "route_type VARCHAR(2), linked_return_id BIGINT, linked_return_no VARCHAR(64), linked_return_item_id BIGINT,"
                    + "returned_quantity NUMERIC(18,4) DEFAULT 0,"
                    + "substitute_returned_quantity NUMERIC(18,4) DEFAULT 0,"
                    + "pending_return_quantity NUMERIC(18,4) DEFAULT 0,"
                    + "waived_quantity NUMERIC(18,4) DEFAULT 0, waived_by VARCHAR(255), waived_at TIMESTAMP, waived_reason VARCHAR(500),"
                    + "turned_delivery_quantity NUMERIC(18,4) DEFAULT 0, remark VARCHAR(500),"
                    + "created_at TIMESTAMP, updated_at TIMESTAMP, created_by VARCHAR(255), updated_by VARCHAR(255))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_sexchange_item_fk ON sale_exchange_item(exchange_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_sexchange_item_source ON sale_exchange_item(source_id, source_item_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_sexchange_item_route ON sale_exchange_item(source_item_id, route_type)");

            // ③''' 替换发货指示（2026-11-27）：销售在订单行上预先指定"发多少用哪个替代品"。
            //      等价脚本：deploy/sql/201_sale_substitute_plan.sql（新库/老库均幂等）
            target.execute("CREATE TABLE IF NOT EXISTS sale_order_substitute_plan ("
                    + "plan_id BIGSERIAL PRIMARY KEY,"
                    + "order_id BIGINT NOT NULL, order_item_id BIGINT NOT NULL,"
                    + "substitute_material_id BIGINT NOT NULL,"
                    + "substitute_material_code VARCHAR(64), substitute_material_name VARCHAR(255),"
                    + "specification VARCHAR(255), base_unit_id BIGINT,"
                    + "unit_price NUMERIC(18,4), quantity NUMERIC(18,4) NOT NULL,"
                    + "turned_delivery_quantity NUMERIC(18,4) DEFAULT 0,"
                    + "status VARCHAR(20) DEFAULT '有效', remark VARCHAR(500),"
                    + "org_code VARCHAR(64),"
                    + "created_at TIMESTAMP, updated_at TIMESTAMP, created_by VARCHAR(255), updated_by VARCHAR(255))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_sosp_order ON sale_order_substitute_plan(order_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_sosp_item ON sale_order_substitute_plan(order_item_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_sosp_item_status ON sale_order_substitute_plan(order_item_id, status)");

            // ③'' 售后维修（2026-11-18）：维修单表头/维修明细/配件领用三表
            //     等价脚本：deploy/sql/193_sale_repair.sql（新库/老库均幂等）
            target.execute("CREATE TABLE IF NOT EXISTS sale_repair ("
                    + "repair_id BIGSERIAL PRIMARY KEY,"
                    + "repair_no VARCHAR(64) NOT NULL,"
                    + "repair_date DATE NOT NULL,"
                    + "customer_id BIGINT NOT NULL, customer_name VARCHAR(255),"
                    + "contact_person VARCHAR(100), contact_phone VARCHAR(50),"
                    + "source_bill_type VARCHAR(64), source_bill_no VARCHAR(64), source_id BIGINT,"
                    + "service_type VARCHAR(20) DEFAULT '寄修',"
                    + "warranty_status VARCHAR(20) DEFAULT '待判定', warranty_basis VARCHAR(500), warranty_months INT,"
                    + "warehouse_id BIGINT, parts_warehouse_id BIGINT,"
                    + "fault_description TEXT,"
                    + "receive_date DATE, return_date DATE,"
                    + "received_quantity NUMERIC(18,4) DEFAULT 0, returned_quantity NUMERIC(18,4) DEFAULT 0,"
                    + "repair_fee NUMERIC(18,2) DEFAULT 0, parts_fee NUMERIC(18,2) DEFAULT 0, total_fee NUMERIC(18,2) DEFAULT 0,"
                    + "repair_result VARCHAR(20),"
                    + "transferred_bill_type VARCHAR(64), transferred_bill_no VARCHAR(64), transferred_bill_id BIGINT,"
                    + "received_inbound_id BIGINT, received_inbound_no VARCHAR(64),"
                    + "returned_issue_id BIGINT, returned_issue_no VARCHAR(64),"
                    + "receipt_id BIGINT, receipt_no VARCHAR(64),"
                    + "total_quantity NUMERIC(18,4) DEFAULT 0,"
                    + "remark VARCHAR(500), attachment_urls TEXT,"
                    + "status VARCHAR(20) DEFAULT '草稿', approval_status VARCHAR(20) DEFAULT '草稿',"
                    + "approved_by VARCHAR(255), approved_at TIMESTAMP,"
                    + "close_status INT DEFAULT 0, closed_by VARCHAR(255), closed_at TIMESTAMP, closed_reason VARCHAR(500),"
                    + "bill_before_status INT, voided_by VARCHAR(255), voided_at TIMESTAMP, void_reason VARCHAR(500),"
                    + "org_code VARCHAR(64),"
                    + "created_at TIMESTAMP, updated_at TIMESTAMP, created_by VARCHAR(255), updated_by VARCHAR(255))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_srepair_no ON sale_repair(repair_no)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_srepair_source ON sale_repair(source_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_srepair_customer ON sale_repair(customer_id)");

            target.execute("CREATE TABLE IF NOT EXISTS sale_repair_item ("
                    + "repair_item_id BIGSERIAL PRIMARY KEY,"
                    + "repair_id BIGINT NOT NULL, line_no INT,"
                    + "material_id BIGINT, material_code VARCHAR(64), material_name VARCHAR(255),"
                    + "specification VARCHAR(255), unit_id BIGINT, unit VARCHAR(50),"
                    + "serial_no VARCHAR(100), batch_no VARCHAR(100),"
                    + "quantity NUMERIC(18,4) DEFAULT 0,"
                    + "fault_description VARCHAR(500), repair_plan VARCHAR(500),"
                    + "warranty_status VARCHAR(20), labor_hours NUMERIC(10,2),"
                    + "received_quantity NUMERIC(18,4) DEFAULT 0, returned_quantity NUMERIC(18,4) DEFAULT 0,"
                    + "remark VARCHAR(500),"
                    + "created_at TIMESTAMP, updated_at TIMESTAMP, created_by VARCHAR(255), updated_by VARCHAR(255))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_srepair_item_fk ON sale_repair_item(repair_id)");

            target.execute("CREATE TABLE IF NOT EXISTS sale_repair_part ("
                    + "part_id BIGSERIAL PRIMARY KEY,"
                    + "repair_id BIGINT NOT NULL, repair_item_id BIGINT, line_no INT,"
                    + "material_id BIGINT, material_code VARCHAR(64), material_name VARCHAR(255),"
                    + "specification VARCHAR(255), unit_id BIGINT, unit VARCHAR(50),"
                    + "quantity NUMERIC(18,4) DEFAULT 0, warehouse_id BIGINT, batch_no VARCHAR(100),"
                    + "avg_cost NUMERIC(18,4), amount NUMERIC(18,2),"
                    + "issued_quantity NUMERIC(18,4) DEFAULT 0, issue_id BIGINT, issue_no VARCHAR(64),"
                    + "remark VARCHAR(500),"
                    + "created_at TIMESTAMP, updated_at TIMESTAMP, created_by VARCHAR(255), updated_by VARCHAR(255))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_srepair_part_fk ON sale_repair_part(repair_id)");

            // 售后维修默认仓库（2026-11-18）：维修仓不参与 MRP/可用量；备件仓参与库存与成本
            // 客户包声明不上售后维修单（hide-menu-codes 含 sale_repairs）时：不建仓、不写默认仓参数
            if (!isMenuHiddenByCustomerPackage("sale_repairs")) {
            target.execute("INSERT INTO warehouse (stock_code, stock_name, stock_type, warehouse_type, status,"
                    + " is_enabled, is_mrp_available, sort_order, created_at, updated_at, created_by, updated_by, remark)"
                    + " SELECT 'REPAIR', '维修仓', '维修仓', '维修仓', '启用', 1, 0, 997, NOW(), NOW(), 'system', 'system',"
                    + " '售后维修收件与返还专用，不参与可用量'"
                    + " WHERE NOT EXISTS (SELECT 1 FROM warehouse WHERE stock_code = 'REPAIR')");
            target.execute("INSERT INTO warehouse (stock_code, stock_name, stock_type, warehouse_type, status,"
                    + " is_enabled, is_mrp_available, sort_order, created_at, updated_at, created_by, updated_by, remark)"
                    + " SELECT 'SPARE', '备件仓', '备件仓', '备件仓', '启用', 1, 1, 998, NOW(), NOW(), 'system', 'system',"
                    + " '售后维修配件领用专用'"
                    + " WHERE NOT EXISTS (SELECT 1 FROM warehouse WHERE stock_code = 'SPARE')");
            target.execute("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                    + " config_section, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)"
                    + " SELECT 'sale.repair.warehouse.id',"
                    + " (SELECT CAST(stock_id AS VARCHAR) FROM warehouse WHERE stock_code = 'REPAIR' LIMIT 1),"
                    + " '售后维修默认维修仓', 'sale', '销售管理参数', 'number', 88,"
                    + " '售后维修单收件入库与返还出库默认使用此仓库', NOW(), NOW(), 'system', 'system'"
                    + " WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.repair.warehouse.id')");
            target.execute("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                    + " config_section, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)"
                    + " SELECT 'sale.repair.parts.warehouse.id',"
                    + " (SELECT CAST(stock_id AS VARCHAR) FROM warehouse WHERE stock_code = 'SPARE' LIMIT 1),"
                    + " '售后维修默认备件仓', 'sale', '销售管理参数', 'number', 89,"
                    + " '售后维修配件领用出库默认使用此仓库', NOW(), NOW(), 'system', 'system'"
                    + " WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.repair.parts.warehouse.id')");
            log.info("[SchemaUpgradeEnsurer] 售后维修自检就绪：三张表 + 货品保修期列 + 维修仓/备件仓默认参数");
            }

            // ④ 单据明细浮动两列（2026-11-10）：凡已有 base_quantity 的明细表统一补
            //    float_unit_id（浮动单位）/ float_quantity（浮动数量，按该浮动单位计）
            java.util.List<String> floatTables = target.queryForList(
                    "SELECT DISTINCT table_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND column_name = 'base_quantity'", String.class);
            for (String t : floatTables) {
                target.execute("ALTER TABLE " + t + " ADD COLUMN IF NOT EXISTS float_unit_id BIGINT");
                target.execute("ALTER TABLE " + t + " ADD COLUMN IF NOT EXISTS float_quantity NUMERIC(18,8)");
                // 副单位可手选（2026-11-10）：空值 = 按档案第一个固定换算行显示
                target.execute("ALTER TABLE " + t + " ADD COLUMN IF NOT EXISTS sub_unit_id BIGINT");
                // 浮动换算率（2026-11-10 标准浮动换算）：行上可改，未填回落档案率
                target.execute("ALTER TABLE " + t + " ADD COLUMN IF NOT EXISTS float_conversion_rate NUMERIC(18,8)");
            }

            log.info("[SchemaUpgradeEnsurer] 多单位换算自检完成：物料辅计量字段 + 换算关系表 + {} 张单据明细折算列（含浮动两列 + 副单位）",
                    Math.max(itemTables.length, floatTables.size()));
        } catch (Exception e) {
            // 绝不因为 schema 检查异常把启动拉挂
            log.warn("[SchemaUpgradeEnsurer] 多单位换算列补齐跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】生产参数收敛（2026-11-09）：生产设置只保留「超过来源单数量」。
     * 删除历史参数（含默认人工费率/制造费用率）并补一条来源单数量控制，主库与逐租户库同口径。
     */
    private void ensureProduceParamConvergence(JdbcTemplate target) {
        try {
            target.update("DELETE FROM sys_config WHERE config_key IN ("
                    + "'produce.order.prefix','produce.mrp.enabled','produce.lead.time.buffer',"
                    + "'produce.report.real.time','produce.scrap.max',"
                    + "'produce.labor.rate.default','produce.overhead.rate.default')");
            Integer exists = target.queryForObject(
                    "SELECT COUNT(*) FROM sys_config WHERE config_key = 'produce.over.qty'", Integer.class);
            if (exists == null || exists == 0) {
                target.update("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                                + " config_type, config_options, sort_order, remark, created_at, updated_at,"
                                + " created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,NOW(),NOW(),?,?)",
                        "produce.over.qty", "不控制", "超过来源单数量", "produce", "select",
                        "[{\"label\":\"不控制\",\"value\":\"不控制\"},{\"label\":\"预警提示\",\"value\":\"预警提示\"},"
                                + "{\"label\":\"不允许\",\"value\":\"不允许\"}]",
                        1, "生产单据转入来源单时，本次数量超过可转数量的处理策略", "system", "system");
            } else {
                target.update("UPDATE sys_config SET config_group = 'produce', config_name = '超过来源单数量',"
                        + " sort_order = 1 WHERE config_key = 'produce.over.qty'");
            }
            log.info("[SchemaUpgradeEnsurer] 生产参数已收敛：仅保留「超过来源单数量」");
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 生产参数收敛跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】物料采购批量政策开关（2026-11-25）：采购订单、领料单两个整数倍校验开关。
     * 默认 false（关 = 旧行为），缺则补、存在则只校正名称与分组，不动用户已改的值。
     */
    private void ensurePurchaseBatchPolicyConfig(JdbcTemplate target) {
        try {
            Integer poExists = target.queryForObject(
                    "SELECT COUNT(*) FROM sys_config WHERE config_key = 'purchase.order.batch.check.enabled'", Integer.class);
            if (poExists == null || poExists == 0) {
                target.update("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                                + " config_section, config_type, sort_order, remark, created_at, updated_at,"
                                + " created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,NOW(),NOW(),?,?)",
                        "purchase.order.batch.check.enabled", "false", "采购订单按采购批量校验", "purchase",
                        "采购订单", "boolean", 90,
                        "开启后采购订单数量必须是物料采购批量的整数倍", "system", "system");
            }
            Integer reqExists = target.queryForObject(
                    "SELECT COUNT(*) FROM sys_config WHERE config_key = 'inventory.requisition.batch.check.enabled'", Integer.class);
            if (reqExists == null || reqExists == 0) {
                target.update("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                                + " config_section, config_type, sort_order, remark, created_at, updated_at,"
                                + " created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,NOW(),NOW(),?,?)",
                        "inventory.requisition.batch.check.enabled", "false", "领料单按采购批量校验", "inventory",
                        "库存管理参数", "boolean", 90,
                        "开启后领料数量必须是物料采购批量的整数倍", "system", "system");
            }
            log.info("[SchemaUpgradeEnsurer] 采购批量政策开关：采购订单校验={}，领料单校验={}",
                    queryOneString(target, "SELECT config_value FROM sys_config WHERE config_key = 'purchase.order.batch.check.enabled'"),
                    queryOneString(target, "SELECT config_value FROM sys_config WHERE config_key = 'inventory.requisition.batch.check.enabled'"));
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 采购批量政策开关补齐跳过: {}", e.getMessage());
        }
    }

    private void ensureLineLevelBomColumnsOn(JdbcTemplate target) {
        try {
            // 生产工单成品明细（行级 BOM 选择）
            target.execute("ALTER TABLE work_order_product ADD COLUMN IF NOT EXISTS bom_id BIGINT");
            target.execute("ALTER TABLE work_order_product ADD COLUMN IF NOT EXISTS bom_code VARCHAR(64)");
            target.execute("ALTER TABLE work_order_product ADD COLUMN IF NOT EXISTS bom_version VARCHAR(64)");

            // 销售 6 单据明细（只读按物料主 BOM 带出）
            String[] saleItems = {"sale_quote_item", "sale_order_item", "sale_contract_item",
                    "sale_delivery_item", "sale_return_item", "sale_invoice_item"};
            for (String t : saleItems) {
                target.execute("ALTER TABLE " + t + " ADD COLUMN IF NOT EXISTS bom_id BIGINT");
                target.execute("ALTER TABLE " + t + " ADD COLUMN IF NOT EXISTS bom_code VARCHAR(64)");
                target.execute("ALTER TABLE " + t + " ADD COLUMN IF NOT EXISTS bom_version VARCHAR(64)");
            }

            // MRP 计划明细（运算时使用的 BOM 快照）
            target.execute("ALTER TABLE mrp_plan_item ADD COLUMN IF NOT EXISTS bom_code VARCHAR(64)");
            target.execute("ALTER TABLE mrp_plan_item ADD COLUMN IF NOT EXISTS bom_version VARCHAR(64)");

            log.info("[SchemaUpgradeEnsurer] 行级 BOM 列自检完成：生产工单成品明细 + 销售 6 单据明细 + MRP 计划明细");
        } catch (Exception e) {
            // 绝不因为 schema 检查异常把启动拉挂
            log.warn("[SchemaUpgradeEnsurer] 行级 BOM 列补齐跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】工序扫码认领与按人报工（2026-10-25）。
     *
     * <p>① {@code work_order_operation_worker}：工序共同负责人（在岗/已退出/已结束 + 认领数量 + 个人已报）；
     * <br>② {@code operation_report_flow}：工序报工流水（按人记账，工序已报 = 流水汇总）。
     *
     * <p>等价脚本：{@code deploy/sql/173_operation_scan_claim.sql} / {@code db/173_operation_scan_claim.sql}。</p>
     */
    private void ensureOperationClaimTables(JdbcTemplate target) {
        try {
            target.execute("CREATE TABLE IF NOT EXISTS work_order_operation_worker ("
                    + " id BIGSERIAL PRIMARY KEY,"
                    + " operation_row_id BIGINT NOT NULL,"
                    + " employee_id BIGINT NOT NULL,"
                    + " employee_name VARCHAR(100),"
                    + " claim_quantity NUMERIC(18,4),"
                    + " reported_quantity NUMERIC(18,4) NOT NULL DEFAULT 0,"
                    + " status VARCHAR(20) NOT NULL DEFAULT '在岗',"
                    + " claim_time TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,"
                    + " exit_time TIMESTAMP WITHOUT TIME ZONE,"
                    + " claimed_by BIGINT,"
                    + " claimed_by_name VARCHAR(100),"
                    + " remark VARCHAR(255))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_wop_worker_op ON work_order_operation_worker(operation_row_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_wop_worker_emp ON work_order_operation_worker(employee_id)");
            target.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_wop_worker_active"
                    + " ON work_order_operation_worker(operation_row_id, employee_id) WHERE status = '在岗'");

            target.execute("CREATE TABLE IF NOT EXISTS operation_report_flow ("
                    + " id BIGSERIAL PRIMARY KEY,"
                    + " operation_row_id BIGINT NOT NULL,"
                    + " order_id BIGINT,"
                    + " order_no VARCHAR(64),"
                    + " employee_id BIGINT,"
                    + " employee_name VARCHAR(100),"
                    + " quantity NUMERIC(18,4) NOT NULL DEFAULT 0,"
                    + " rejected_quantity NUMERIC(18,4) NOT NULL DEFAULT 0,"
                    + " source VARCHAR(20) NOT NULL DEFAULT '扫码报工',"
                    + " source_bill_id BIGINT,"
                    + " source_bill_no VARCHAR(64),"
                    + " operator_id BIGINT,"
                    + " operator_name VARCHAR(100),"
                    + " is_proxy INTEGER NOT NULL DEFAULT 0,"
                    + " flow_status VARCHAR(20) NOT NULL DEFAULT '有效',"
                    + " idem_key VARCHAR(80),"
                    + " report_time TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,"
                    + " remark VARCHAR(255))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_orf_op ON operation_report_flow(operation_row_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_orf_emp ON operation_report_flow(employee_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_orf_order ON operation_report_flow(order_id)");
            target.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_orf_idem"
                    + " ON operation_report_flow(idem_key) WHERE idem_key IS NOT NULL");
            // 存量工序：未配置的按「是」（默认允许扫码认领；不影响既有派工与报工口径）
            try {
                target.update("UPDATE routing_operation SET allow_scan_claim = ?"
                        + " WHERE allow_scan_claim IS NULL OR allow_scan_claim = ''", "是");
            } catch (Exception ignore) {
                // routing_operation 尚未建表（极早期库）→ 忽略，补列环节会再兜底
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 工序扫码认领/报工流水建表跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】生产工单进度对外分享（2026-11-25）。
     *
     * <p>{@code work_order_share}：手机端「工序进度」生成带过期时间的分享链接 / 小程序卡片，
     * 记录令牌、有效期、展示项开关（负责人 / 计划日期 / 物料编码）与访问计数。
     * 公开页只读，过期或停止后立即失效；不存客户、价格、成本等敏感字段。</p>
     *
     * <p>等价脚本：{@code deploy/sql/197_work_order_share.sql} / {@code db/197_work_order_share.sql}。</p>
     */
    private void ensureWorkOrderShareTable(JdbcTemplate target) {
        try {
            target.execute("CREATE TABLE IF NOT EXISTS work_order_share ("
                    + " id BIGSERIAL PRIMARY KEY,"
                    + " token VARCHAR(96) NOT NULL,"
                    + " order_id BIGINT NOT NULL,"
                    + " order_no VARCHAR(64),"
                    + " title VARCHAR(120) NOT NULL,"
                    + " remark VARCHAR(500),"
                    + " expire_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,"
                    + " status INTEGER NOT NULL DEFAULT 1,"
                    + " show_assignee INTEGER NOT NULL DEFAULT 1,"
                    + " show_plan_date INTEGER NOT NULL DEFAULT 1,"
                    + " show_material_code INTEGER NOT NULL DEFAULT 1,"
                    + " view_count INTEGER NOT NULL DEFAULT 0,"
                    + " last_view_at TIMESTAMP WITHOUT TIME ZONE,"
                    + " created_by BIGINT,"
                    + " created_by_name VARCHAR(100),"
                    + " created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,"
                    + " updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP)");
            target.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_work_order_share_token ON work_order_share(token)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_work_order_share_order ON work_order_share(order_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_work_order_share_expire ON work_order_share(expire_at)");
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 工单进度分享表建表跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】指定目标库建质检模块表（主库 + 每个租户库共用）。
     *
     * <p>含 2026-10-07 绑定模型改造新增的两张关联表（模板↔适用物料 / 模板↔适用分类），
     * 以及旧单值物料向关联表的存量迁移（INSERT ... WHERE NOT EXISTS）。</p>
     */
    /**
     * 【初始化·幂等】工艺路线多物料/多分类绑定 + 工序自定义字段定义（2026-11-26）。
     * 主库与每个租户库都要建；同时给存量工序补 operation_key、给存量单物料补关联行。
     */
    private void ensureRoutingBindingTablesOn(JdbcTemplate target) {
        try {
            target.execute("CREATE TABLE IF NOT EXISTS routing_material ("
                    + " id BIGSERIAL PRIMARY KEY, routing_id BIGINT NOT NULL, material_id BIGINT NOT NULL,"
                    + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            target.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_routing_material ON routing_material (routing_id, material_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_routing_material_mid ON routing_material (material_id)");
            target.execute("CREATE TABLE IF NOT EXISTS routing_material_category ("
                    + " id BIGSERIAL PRIMARY KEY, routing_id BIGINT NOT NULL, category_id BIGINT NOT NULL,"
                    + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            target.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_routing_material_category"
                    + " ON routing_material_category (routing_id, category_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_routing_material_category_cid"
                    + " ON routing_material_category (category_id)");
            target.execute("CREATE TABLE IF NOT EXISTS routing_operation_field ("
                    + " id BIGSERIAL PRIMARY KEY, routing_id BIGINT NOT NULL, operation_key VARCHAR(64) NOT NULL,"
                    + " field_label VARCHAR(100) NOT NULL, field_type VARCHAR(20) NOT NULL DEFAULT 'text',"
                    + " field_options TEXT, is_required SMALLINT NOT NULL DEFAULT 0, sort_no INT NOT NULL DEFAULT 0,"
                    + " custom_field_id BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_routing_operation_field"
                    + " ON routing_operation_field (routing_id, operation_key, sort_no)");
            // 存量工序补稳定标识（幂等）
            target.execute("UPDATE routing_operation SET operation_key = 'ro_' || operation_id"
                    + " WHERE operation_key IS NULL OR operation_key = ''");
            // 存量单物料迁入多物料关联表（只补缺失行）
            target.execute("INSERT INTO routing_material (routing_id, material_id, created_at) "
                    + "SELECT r.routing_id, r.material_id, NOW() FROM routing r "
                    + " WHERE r.material_id IS NOT NULL AND NOT EXISTS ("
                    + "   SELECT 1 FROM routing_material m"
                    + "    WHERE m.routing_id = r.routing_id AND m.material_id = r.material_id)");
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 工艺路线绑定表建表/迁移跳过: {}", e.getMessage());
        }
    }

    private void ensureQcTablesOn(JdbcTemplate target) {
        try {
            target.execute("CREATE TABLE IF NOT EXISTS qc_template ("
                    + " template_id BIGSERIAL PRIMARY KEY, template_code VARCHAR(50), template_name VARCHAR(200) NOT NULL,"
                    + " material_id BIGINT, operation_name VARCHAR(200), qc_type VARCHAR(20) DEFAULT '首检',"
                    + " sample_ratio NUMERIC(18,4) DEFAULT 0, allowed_defect_ratio NUMERIC(18,4) DEFAULT 0,"
                    + " is_enabled INT DEFAULT 1, remark VARCHAR(500),"
                    + " created_at TIMESTAMP, updated_at TIMESTAMP, created_by VARCHAR(50), updated_by VARCHAR(50))");
            target.execute("CREATE TABLE IF NOT EXISTS qc_template_item ("
                    + " item_id BIGSERIAL PRIMARY KEY, template_id BIGINT NOT NULL, line_no INT,"
                    + " item_name VARCHAR(200) NOT NULL, inspect_method VARCHAR(200), standard_value VARCHAR(200),"
                    + " upper_limit NUMERIC(18,4), lower_limit NUMERIC(18,4), unit VARCHAR(50), is_required INT DEFAULT 1,"
                    + " created_at TIMESTAMP, updated_at TIMESTAMP, created_by VARCHAR(50), updated_by VARCHAR(50))");
            target.execute("CREATE TABLE IF NOT EXISTS qc_record ("
                    + " record_id BIGSERIAL PRIMARY KEY, record_no VARCHAR(50), order_id BIGINT, order_no VARCHAR(50),"
                    + " operation_row_id BIGINT, operation_name VARCHAR(200), template_id BIGINT, qc_type VARCHAR(20),"
                    + " inspect_qty NUMERIC(18,4) DEFAULT 0, qualified_qty NUMERIC(18,4) DEFAULT 0,"
                    + " defect_qty NUMERIC(18,4) DEFAULT 0, result VARCHAR(20), handle_type VARCHAR(20),"
                    + " handle_operation_name VARCHAR(200), handle_remark VARCHAR(500), inspector_id BIGINT,"
                    + " inspector_name VARCHAR(50), inspect_time TIMESTAMP, remark VARCHAR(500),"
                    + " created_at TIMESTAMP, updated_at TIMESTAMP, created_by VARCHAR(50), updated_by VARCHAR(50))");
            target.execute("CREATE TABLE IF NOT EXISTS qc_record_item ("
                    + " item_id BIGSERIAL PRIMARY KEY, record_id BIGINT NOT NULL, line_no INT,"
                    + " item_name VARCHAR(200), standard_value VARCHAR(200), measured_value VARCHAR(200),"
                    + " upper_limit NUMERIC(18,4), lower_limit NUMERIC(18,4), result VARCHAR(20), remark VARCHAR(500),"
                    + " images VARCHAR(1000), created_at TIMESTAMP)");
            // 绑定模型改造（2026-10-07）：模板可绑多个物料 / 多个分类
            target.execute("CREATE TABLE IF NOT EXISTS qc_template_material ("
                    + " id BIGSERIAL PRIMARY KEY, template_id BIGINT NOT NULL, material_id BIGINT NOT NULL,"
                    + " created_at TIMESTAMP)");
            target.execute("CREATE TABLE IF NOT EXISTS qc_template_category ("
                    + " id BIGSERIAL PRIMARY KEY, template_id BIGINT NOT NULL, category_id BIGINT NOT NULL,"
                    + " created_at TIMESTAMP)");

            target.execute("CREATE INDEX IF NOT EXISTS idx_qc_template_material ON qc_template (material_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_qc_template_operation ON qc_template (operation_name)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_qc_template_item_tid ON qc_template_item (template_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_qc_record_oprow ON qc_record (operation_row_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_qc_record_order ON qc_record (order_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_qc_record_item_rid ON qc_record_item (record_id)");
            target.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_qc_tpl_material ON qc_template_material (template_id, material_id)");
            target.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_qc_tpl_category ON qc_template_category (template_id, category_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_qc_tpl_material_mid ON qc_template_material (material_id)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_qc_tpl_category_cid ON qc_template_category (category_id)");

            // 存量迁移：旧「适用物料」单值 → 多选关联表（只补缺失行，幂等）
            target.execute("INSERT INTO qc_template_material (template_id, material_id, created_at) "
                    + "SELECT t.template_id, t.material_id, NOW() FROM qc_template t "
                    + " WHERE t.material_id IS NOT NULL AND NOT EXISTS ("
                    + "   SELECT 1 FROM qc_template_material m"
                    + "    WHERE m.template_id = t.template_id AND m.material_id = t.material_id)");
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 质检模块建表跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】质检模板菜单（2026-09-28 第 12 期）：挂「基础资料」下 + 基础资料相关角色自动授权。
     *
     * <p>等价脚本：{@code deploy/sql/123_qc.sql} 第 7/8 段。</p>
     */
    private void ensureQcTemplateMenu() {
        try {
            Long basedataId = jdbcTemplate.queryForObject(
                    "SELECT menu_id FROM sys_menu WHERE menu_code = 'basedata'", Long.class);
            ensureMenu("qc_template", "质检模板", basedataId, "MENU",
                    "/masterdata/qc-template", "basedata:qc-template", "masterdata/QcTemplateList",
                    16, "Checked", true, "ULTIMATE");
            ensureMenuRoleGrants("qc_template", "/masterdata/qc-template", new String[]{
                    "ADMIN", "SUPER_ADMIN", "MANAGER",
                    "SALES_MANAGER", "SALES", "PURCHASE_MANAGER", "PURCHASE",
                    "WAREHOUSE_MANAGER", "WAREHOUSE", "PRODUCTION_MANAGER", "PRODUCTION",
                    "FINANCE_MANAGER", "FINANCE"});
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 质检模板菜单初始化跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】工序质检报表菜单（2026-10-07）：挂「生产管理」下、紧随「分析报表」之后。
     *
     * <p>背景：工序检验记录此前无查询接口、无页面 → 用户看不到质检记录，「经营看板 → 质量」块也没有目标路由无法穿透。</p>
     *
     * <p>规则（与 {@link #ensureProductionProgressTrackingMenu()} 同款）：只在菜单不存在时插入；已存在则什么都不做
     * （重复启动 0 副作用）。sort_order = 101，紧跟同组「分析报表」（实库 100），无需顺延既有菜单。
     * 角色口径：生产角色本职使用 + 经营看板授权角色（FINANCE_MANAGER）要能穿透进来 + 同组「分析报表」的 SALES。</p>
     *
     * <p>等价脚本：{@code deploy/sql/136_qc_record_report_menu.sql} = {@code db/136_qc_record_report_menu.sql}。</p>
     */
    private void ensureQcRecordReportMenu() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_menu WHERE menu_code = 'production_qc_record_report'", Integer.class);
            if (exists != null && exists > 0) {
                return;   // 已存在 → 不再插菜单、不再授权，保证重复启动 0 副作用
            }
            Long productionId = jdbcTemplate.queryForObject(
                    "SELECT menu_id FROM sys_menu WHERE menu_code = 'production'", Long.class);
            ensureMenu("production_qc_record_report", "工序质检报表", productionId, "MENU",
                    "/production/qc-record", "production:report-qc-record", null,
                    101, "DataLine", true, "ULTIMATE");
            ensureMenuRoleGrants("production_qc_record_report", "/production/qc-record", new String[]{
                    "ADMIN", "SUPER_ADMIN", "PRODUCTION_MANAGER", "PRODUCTION", "FINANCE_MANAGER", "SALES"});
            log.info("[SchemaUpgradeEnsurer] 工序质检报表菜单已初始化。");
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 工序质检报表菜单初始化跳过: {}", e.getMessage());
        }
    }

    /**
     * 【初始化·幂等】车间进度跟踪菜单（2026-09-28 第 16 期）：挂「生产管理」下、紧随「车间排程看板」之后。
     *
     * <p>规则（用户口径）：
     * <br>① 只在「该菜单尚不存在」时才插菜单，并把原「生产管理」下 sort_order &gt;= 5 的子菜单整体 +1 顺延，
     * 给新菜单腾出第 5 位；已存在则**什么都不做**（重复启动 0 副作用）；
     * <br>② 角色口径与「车间排程看板」一致；
     * <br>③ 失败只记日志，绝不阻断启动。
     *
     * <p>等价脚本：{@code database/schema/99_init_menus.sql}（全新库种子，menu_id=813）。</p>
     */
    private void ensureProductionProgressTrackingMenu() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_menu WHERE menu_code = 'production_progress_tracking'", Integer.class);
            if (exists != null && exists > 0) {
                return;   // 已存在 → 不再顺延排序、不再插菜单，保证重复启动 0 副作用
            }
            Long productionId = jdbcTemplate.queryForObject(
                    "SELECT menu_id FROM sys_menu WHERE menu_code = 'production'", Long.class);
            // 顺延：把生产管理下 sort_order >= 5 的子菜单整体 +1（保持现有顺序不乱、无重号）
            int shifted = jdbcTemplate.update(
                    "UPDATE sys_menu SET sort_order = sort_order + 1 WHERE parent_menu_id = ? AND sort_order >= 5",
                    productionId);
            ensureMenu("production_progress_tracking", "车间进度跟踪", productionId, "MENU",
                    "/production/progress-tracking", "production:progress-tracking",
                    "production/ProgressTracking", 5, "DataLine", true, "ULTIMATE");
            ensureMenuRoleGrants("production_progress_tracking", "/production/progress-tracking", new String[]{
                    "ADMIN", "SUPER_ADMIN", "PRODUCTION_MANAGER", "PRODUCTION", "SALES"});
            log.info("[SchemaUpgradeEnsurer] 车间进度跟踪菜单已初始化（顺延 {} 条子菜单）。", shifted);
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 车间进度跟踪菜单初始化跳过: {}", e.getMessage());
        }
    }

    /** 【幂等】sys_config 缺该 key 才插入（存在则不动用户已改过的值） */
    private int insertSysConfigIfMissing(String key, String value, String name, int sortOrder, String remark) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_config WHERE config_key = ?", Integer.class, key);
        if (cnt != null && cnt > 0) return 0;
        return jdbcTemplate.update(
                "INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type,"
                        + " sort_order, remark, created_at, updated_at, created_by, updated_by)"
                        + " VALUES (?, ?, ?, 'sys_param', 'number', ?, ?, NOW(), NOW(), 'system', 'system')",
                key, value, name, sortOrder, remark);
    }

    // ---- 平台库 sys_tenant 列补齐单独走：平台库用 platformJdbcTemplate。
    // 平台侧 TenantService 在自己的 @PostConstruct 里已经覆盖，这里不重复执行。----

    /**
     * 【2026-10-13】平台库 sys_user.password 放开 NOT NULL（幂等）。
     * 口径：密码只存租户库，平台库账号记录不存密码（写 NULL/空串）。存量环境
     * （8cloud/121 平台库建表早）该列带 NOT NULL → 账号同步 INSERT 违反约束被静默吞掉
     * → 平台库查无此人 → 新建账号登录报「用户名或密码错误」/「用户不存在」（8cloud 实测）。
     * 无平台库（单机客户环境）时跳过，绝不拉挂启动。
     */
    private void ensurePlatformPasswordNullable() {
        if (platformJdbcTemplate == null) return;
        try {
            Integer nullable = platformJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                  + " WHERE table_name = 'sys_user' AND column_name = 'password' AND is_nullable = 'NO'",
                    Integer.class);
            if (nullable != null && nullable > 0) {
                platformJdbcTemplate.execute("ALTER TABLE sys_user ALTER COLUMN password DROP NOT NULL");
                log.info("[SchemaUpgradeEnsurer] 平台库 sys_user.password 已放开 NOT NULL（账号同步口径：平台库不存密码）");
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 平台库 sys_user.password 约束检查/放开失败（不影响启动）: {}", e.getMessage());
        }
    }
    // ---- 菜单兜底：按 menu_code 去重插入，保证设置中心与AI用量管理入口可见 ----
    private void ensureMenusExist() {
        try {
            // 1. 设置中心（顶级MENU，route_path=/system/settings 是 MainLayout 齿轮可见性判定条件）
            ensureMenu("system_settings_center", "设置中心", null, "MENU",
                    "/system/settings", null, null, 6, "Setting", true);

            // 2. AI用量管理（挂在设置中心下，SettingsCenter 左栏通过 ?group=system&item=ai-token-usage 定位）
            Long settingsId = jdbcTemplate.queryForObject(
                    "SELECT menu_id FROM sys_menu WHERE menu_code = 'system_settings_center'", Long.class);
            ensureMenu("system_ai_token_usage", "AI用量管理", settingsId, "MENU",
                    "/system/settings", "system:ai-token-usage", null, 99, "Key", true);

            // 3. AI两级额度管理权限码承载菜单（隐藏，仅用于后端接口权限校验）
            Long aiMenuId = jdbcTemplate.queryForObject(
                    "SELECT menu_id FROM sys_menu WHERE menu_code = 'system_ai_token_usage'", Long.class);
            ensureMenu("system_ai_tenant_key", "AI租户自有Key", aiMenuId, "C",
                    "/system/ai/tenant-key", "ai:key:tenant", null, 1, "Key", false);
            ensureMenu("system_ai_tenant_quota", "AI租户额度", aiMenuId, "C",
                    "/system/ai/tenant-quota", "ai:quota:tenant", null, 2, "Coin", false);
            ensureMenu("system_ai_user_quota", "AI用户额度", aiMenuId, "C",
                    "/system/ai/user-quota", "ai:quota:user", null, 3, "User", false);

            // 4. 设置中心「AI」分组子项（2026-09-19）：4 个新入口，route_path 带 ?item= 参数定位
            //    （另 3 项 ai-data-policy / ai-token-usage / ai-knowledge 菜单已存在，item key 不变，无需重复插入）
            ensureMenu("system_ai_overview", "AI 设置总览", settingsId, "MENU",
                    "/system/settings?item=ai-overview", null, null, 30, "MagicStick", true);
            ensureMenu("system_ai_assistant_settings", "AI 助手设置", settingsId, "MENU",
                    "/system/settings?item=ai-assistant-settings", null, null, 31, "Setting", true);
            ensureMenu("system_ai_disclaimer", "协议与免责声明", settingsId, "MENU",
                    "/system/settings?item=ai-disclaimer", null, null, 32, "Document", true);
            ensureMenu("system_ai_tool_permission", "AI 工具权限", settingsId, "MENU",
                    "/system/settings?item=ai-tool-permission", null, null, 33, "Key", true);

            // 4.6 销售换货单（2026-11-17 替换货）：与销售退货单同级，自动授权超管/销售主管/销售
            try {
                java.util.List<Long> saleParents = jdbcTemplate.queryForList(
                        "SELECT parent_menu_id FROM sys_menu WHERE route_path = ? AND parent_menu_id IS NOT NULL",
                        Long.class, "/sale/returns");
                if (!saleParents.isEmpty() && !isMenuHiddenByCustomerPackage("sale_exchanges")) {
                    ensureMenu("sale_exchanges", "销售换货单", saleParents.get(0), "MENU",
                            "/sale/exchanges", "sale:exchanges", "views/sale/SaleExchangeList",
                            7, "Document", true, "STANDARD");
                    ensureMenuRoleGrants("sale_exchanges", "/sale/exchanges",
                            new String[]{"ADMIN", "SALES_MANAGER", "SALES"});
                }
            } catch (Exception e) {
                log.warn("[SchemaUpgradeEnsurer] 销售换货单菜单初始化跳过: {}", e.getMessage());
            }

            // 4.7 售后维修单（2026-11-18）：与销售退货单同级，自动授权超管/销售主管/销售
            try {
                java.util.List<Long> repairParents = jdbcTemplate.queryForList(
                        "SELECT parent_menu_id FROM sys_menu WHERE route_path = ? AND parent_menu_id IS NOT NULL",
                        Long.class, "/sale/returns");
                if (!repairParents.isEmpty() && !isMenuHiddenByCustomerPackage("sale_repairs")) {
                    ensureMenu("sale_repairs", "售后维修单", repairParents.get(0), "MENU",
                            "/sale/repairs", "sale:repairs", "views/sale/SaleRepairList",
                            8, "Tools", true, "STANDARD");
                    ensureMenuRoleGrants("sale_repairs", "/sale/repairs",
                            new String[]{"ADMIN", "SALES_MANAGER", "SALES"});
                }
            } catch (Exception e) {
                log.warn("[SchemaUpgradeEnsurer] 售后维修单菜单初始化跳过: {}", e.getMessage());
            }

            // 4.7.1 售后维修单 7 个过程按钮权限码（2026-11-19）：
            // C 类型隐藏菜单承载权限码（is_visible=false），授权面与 sale_repairs 菜单同口径。
            // 编码 = sale:repairs:<动作>，与前端按钮权限码格式 模块:对象:动作 一致。
            try {
                java.util.List<Long> repairMenuIds = jdbcTemplate.queryForList(
                        "SELECT menu_id FROM sys_menu WHERE menu_code = 'sale_repairs'", Long.class);
                if (!repairMenuIds.isEmpty() && !isMenuHiddenByCustomerPackage("sale_repairs")) {
                    Long repairMenuId = repairMenuIds.get(0);
                    String[][] repairButtons = {
                            {"sale_repairs_receive", "收件入库", "sale:repairs:receive", "1"},
                            {"sale_repairs_start", "开始维修", "sale:repairs:start", "2"},
                            {"sale_repairs_issue_parts", "配件领用", "sale:repairs:issueParts", "3"},
                            {"sale_repairs_complete", "完成维修", "sale:repairs:complete", "4"},
                            {"sale_repairs_return", "返还客户", "sale:repairs:return", "5"},
                            {"sale_repairs_transfer_exchange", "转换新", "sale:repairs:transferExchange", "6"},
                            {"sale_repairs_transfer_return", "转退货", "sale:repairs:transferReturn", "7"},
                            // 2026-11-19：本对象一旦有显式按钮配置，v-permission 进入严格模式，
                            // 其余标准按钮必须一并登记并授权，否则销售角色看不到"新增/导入/导出/提交/审核"等按钮
                            {"sale_repairs_query", "查询", "sale:repairs:query", "8"},
                            {"sale_repairs_reset", "重置", "sale:repairs:reset", "9"},
                            {"sale_repairs_view", "查看", "sale:repairs:view", "10"},
                            {"sale_repairs_add", "新增", "sale:repairs:add", "11"},
                            {"sale_repairs_edit", "编辑", "sale:repairs:edit", "12"},
                            {"sale_repairs_delete", "删除", "sale:repairs:delete", "13"},
                            {"sale_repairs_export", "导出", "sale:repairs:export", "14"},
                            {"sale_repairs_import", "导入", "sale:repairs:import", "15"},
                            {"sale_repairs_print", "打印", "sale:repairs:print", "16"},
                            {"sale_repairs_column_setting", "列设置", "sale:repairs:columnSetting", "17"},
                            {"sale_repairs_filter_setting", "筛选设置", "sale:repairs:filterSetting", "18"},
                            {"sale_repairs_submit", "提交", "sale:repairs:submit", "19"},
                            {"sale_repairs_audit", "审核", "sale:repairs:audit", "20"},
                            {"sale_repairs_approve", "审批", "sale:repairs:approve", "21"},
                            {"sale_repairs_reject", "驳回", "sale:repairs:reject", "22"},
                            {"sale_repairs_reverse_approve", "重置草稿", "sale:repairs:reverseApprove", "23"},
                            {"sale_repairs_void", "作废", "sale:repairs:void", "24"},
                            {"sale_repairs_add_row", "添加行", "sale:repairs:addRow", "25"},
                            {"sale_repairs_pick", "选择", "sale:repairs:pick", "26"},
                            {"sale_repairs_dlg_confirm", "确定", "sale:repairs:dlgConfirm", "27"},
                            {"sale_repairs_dlg_cancel", "取消", "sale:repairs:dlgCancel", "28"}
                    };
                    for (String[] b : repairButtons) {
                        ensureMenu(b[0], b[1], repairMenuId, "C", b[2], b[2], null,
                                Integer.parseInt(b[3]), "Pointer", false, "STANDARD");
                        ensureMenuRoleGrants(b[0], b[2],
                                new String[]{"ADMIN", "SALES_MANAGER", "SALES"});
                    }
                }
            } catch (Exception e) {
                log.warn("[SchemaUpgradeEnsurer] 售后维修单按钮权限初始化跳过: {}", e.getMessage());
            }

            // 5. 单据明细报表菜单（2026-09-22）：销售 3 个 + 采购 6 个。
            //    父级统一取同组「分析报表」的 parent_menu_id —— 实库已核对：库中并无独立「报表」菜单行
            //    （如 /sale/report/analysis 的 parent_menu_id = 销售管理），「报表」只是前端 menuConfig 的分组壳。
            //    permission_code 用「模块:对象」形式，对象名 = 前端 menuConfig 的 itemKey（与设置中心按钮权限对象一致）。
            //    等价脚本：deploy/sql/69_sale_detail_reports_menu.sql、70_purchase_detail_reports_menu.sql。
            //    新增模块只需再调一次 ensureDetailReportMenus。
            ensureDetailReportMenus("/sale/report/analysis", new String[][]{
                    {"sale_report_order_detail", "销售订单明细报表", "/sale/report/sale-order-detail", "sale:report-order-detail", "9", "STANDARD"},
                    {"sale_report_delivery_detail", "销售出库明细报表", "/sale/report/sale-delivery-detail", "sale:report-delivery-detail", "10", "STANDARD"},
                    {"sale_report_invoice_detail", "销售发票明细报表", "/sale/report/sale-invoice-detail", "sale:report-invoice-detail", "11", "STANDARD"}
            }, new String[]{"ADMIN", "SALES_MANAGER", "SALES"});
            ensureDetailReportMenus("/purchase/report/analysis", new String[][]{
                    {"purchase_report_request_detail", "采购申请单明细报表", "/purchase/report/purchase-request-detail", "purchase:report-request-detail", "8", "STANDARD"},
                    {"purchase_report_contract_detail", "采购合同明细报表", "/purchase/report/purchase-contract-detail", "purchase:report-contract-detail", "9", "STANDARD"},
                    {"purchase_report_order_detail", "采购订单明细报表", "/purchase/report/purchase-order-detail", "purchase:report-order-detail", "10", "STANDARD"},
                    {"purchase_report_inbound_detail", "采购入库单明细报表", "/purchase/report/purchase-inbound-detail", "purchase:report-inbound-detail", "11", "STANDARD"},
                    {"purchase_report_return_detail", "采购退货单明细报表", "/purchase/report/purchase-return-detail", "purchase:report-return-detail", "12", "STANDARD"},
                    {"purchase_report_invoice_detail", "采购发票明细报表", "/purchase/report/purchase-invoice-detail", "purchase:report-invoice-detail", "13", "STANDARD"}
            }, new String[]{"ADMIN", "PURCHASE_MANAGER", "PURCHASE", "SALES"});
            ensureDetailReportMenus("/inventory/report/analysis", new String[][]{
                    {"inventory_report_other_receipt_detail", "其他入库单明细报表", "/inventory/report/other-receipt-detail", "inventory:report-other-receipt-detail", "10", "STANDARD"},
                    {"inventory_report_other_issue_detail", "其他出库单明细报表", "/inventory/report/other-issue-detail", "inventory:report-other-issue-detail", "11", "STANDARD"},
                    {"inventory_report_stock_transfer_detail", "库存调拨单明细报表", "/inventory/report/stock-transfer-detail", "inventory:report-stock-transfer-detail", "12", "STANDARD"},
                    {"inventory_report_stock_check_detail", "库存盘点单明细报表", "/inventory/report/stock-check-detail", "inventory:report-stock-check-detail", "13", "STANDARD"},
                    {"inventory_report_material_requisition_detail", "生产领料单明细报表", "/inventory/report/material-requisition-detail", "inventory:report-material-requisition-detail", "14", "ULTIMATE"}
            // 授权面刻意比同组「分析报表」（实库仅 ADMIN+SALES）宽：库存单据明细是仓管员日常报表，
            // 只授 ADMIN+SALES 会让真正用它的人看不到；数据行仍由 DataScopeResolver 按角色过滤。
            }, new String[]{"ADMIN", "SUPER_ADMIN", "WAREHOUSE_MANAGER", "WAREHOUSE",
                    "PURCHASE_MANAGER", "PURCHASE", "SALES_MANAGER", "SALES",
                    "PRODUCTION_MANAGER", "PRODUCTION", "FINANCE_MANAGER", "FINANCE"});

            // 6. 质检模板（2026-09-28 第 12 期）：挂「基础资料」下 + 基础资料相关角色自动授权
            ensureQcTemplateMenu();

            // 7. 车间进度跟踪（2026-09-28 第 16 期）：挂「生产管理」下、紧随「车间排程看板」之后
            ensureProductionProgressTrackingMenu();

            // 7.1 工序质检报表（2026-10-07）：挂「生产管理」下、紧随「分析报表」之后
            ensureQcRecordReportMenu();

            // 8. 销售过程动作量化（2026-10-07）：客户管理下新增「销售过程」、数据看板下新增「经营看板」，
            //    并把「跟进超期预警」改名为「销售预警」、「CRM仪表盘」改名为「我的驾驶舱」
            //    （改名只改显示名，route_path 与 permission_code 一律不动，避免收藏夹失效）
            //    等价脚本：deploy/sql/133_sales_process.sql = db/133_sales_process.sql
            ensureSalesProcessMenus();

            // 9. 客户管理「自动化管控」分组（2026-10-09）：把销售目标 / 销售过程 / 销售预警
            //    收进二级菜单「自动化管控」；「我的驾驶舱」保持客户管理直下
            //    等价脚本：deploy/sql/139_crm_automation_group.sql
            ensureCrmAutomationGroupMenu();

            // 10. 开账引导（2026-10-11）：设置中心 → 自定义 → 字段名称设置
            //     等价脚本：deploy/sql/149_onboarding_industry_init.sql
            ensureMenu("system_field_name_settings", "字段名称设置", settingsId, "MENU",
                    "/system/settings?item=field-name", null, null, 21, "Edit", true);
            ensureMenuRoleGrants("system_field_name_settings", "/system/settings?item=field-name",
                    new String[]{"ADMIN", "SUPER_ADMIN"});

            // 11. 自动下推设置（2026-11-08）：设置中心 → 业务 → 自动下推设置
            //     等价脚本：deploy/sql/189_sale_auto_push.sql
            ensureMenu("system_auto_push_settings", "自动下推设置", settingsId, "MENU",
                    "/system/settings?item=auto-push", null, null, 24, "Connection", true);
            ensureMenuRoleGrants("system_auto_push_settings", "/system/settings?item=auto-push",
                    new String[]{"ADMIN", "SUPER_ADMIN"});

            log.info("[SchemaUpgradeEnsurer] menus ensure finished.");
        } catch (Exception e) {
            // 当前库无 sys_menu 表等异常，忽略即可（绝不拉挂启动）
            log.warn("[SchemaUpgradeEnsurer] menus ensure skipped: {}", e.getMessage());
        }
    }

    private void ensureMenu(String menuCode, String menuName, Long parentId, String menuType,
                            String routePath, String permissionCode, String componentPath,
                            int sortOrder, String icon, boolean visible) {
        // 未声明版本 = 三档通用（设置中心等与版本无关的入口走这里）
        ensureMenu(menuCode, menuName, parentId, menuType, routePath, permissionCode,
                componentPath, sortOrder, icon, visible, null);
    }

    /**
     * 带「版本」的菜单兜底插入（2026-10-10 版本划分重构）。
     *
     * <p>version = {@code STANDARD} / {@code CRM} / {@code ULTIMATE}；{@code null} = 三档通用。
     * 规则（守则 2.7.2-4 默认值对齐 + 版本隔离）：新增菜单**必须显式声明版本**，
     * 否则会再次出现"不属于某版本的功能出现在该版本"（历史遗留的越界根因）。</p>
     */
    private void ensureMenu(String menuCode, String menuName, Long parentId, String menuType,
                            String routePath, String permissionCode, String componentPath,
                            int sortOrder, String icon, boolean visible, String version) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE menu_code = ?", Integer.class, menuCode);
        if (c != null && c > 0) return;   // 已存在直接跳过，保证幂等
        jdbcTemplate.update(
                "INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, route_path, " +
                "permission_code, component_path, sort_order, icon, is_visible, is_enabled, " +
                "is_cache, keepalive, status, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, 1, '启用', ?)",
                menuCode, menuName, parentId, menuType, routePath, permissionCode,
                componentPath, sortOrder, icon, visible ? 1 : 0, version);
        log.info("[SchemaUpgradeEnsurer] menu '{}' created (code={}, visible={}, version={}).",
                menuName, menuCode, visible, version);
    }

    /**
     * 【幂等】销售过程动作量化（2026-10-07）菜单与授权。
     *
     * <ul>
     *   <li>客户管理下新增「销售过程」→ /crm/sales-activity（紧随「联系人管理」之后）</li>
     *   <li>数据看板下新增「经营看板」→ /databoard/operation</li>
     *   <li>两处改名：跟进超期预警→销售预警、CRM仪表盘→我的驾驶舱（route_path 与权限码一律不动）</li>
     * </ul>
     *
     * <p>等价脚本：{@code deploy/sql/133_sales_process.sql} = {@code db/133_sales_process.sql}。</p>
     */
    private void ensureSalesProcessMenus() {
        try {
            java.util.List<Long> crmParents = jdbcTemplate.queryForList(
                    "SELECT menu_id FROM sys_menu WHERE menu_code = 'crm'", Long.class);
            if (!crmParents.isEmpty()) {
                ensureMenu("crm_sales_activity", "销售过程", crmParents.get(0), "MENU",
                        "/crm/sales-activity", "crm:sales-activity", null, 7, "DataLine", true, "CRM");
                ensureMenuRoleGrants("crm_sales_activity", "/crm/sales-activity",
                        new String[]{"ADMIN", "SUPER_ADMIN", "SALES_MANAGER", "SALES"});
            } else {
                log.warn("[SchemaUpgradeEnsurer] 未找到客户管理父级菜单，跳过「销售过程」初始化");
            }

            java.util.List<Long> boardParents = jdbcTemplate.queryForList(
                    "SELECT parent_menu_id FROM sys_menu WHERE route_path = '/databoard/sale' AND parent_menu_id IS NOT NULL",
                    Long.class);
            if (!boardParents.isEmpty()) {
                ensureMenu("databoard_operation", "经营看板", boardParents.get(0), "MENU",
                        "/databoard/operation", "databoard:operation", null, 4, "TrendCharts", true, "ULTIMATE");
                ensureMenuRoleGrants("databoard_operation", "/databoard/operation",
                        new String[]{"ADMIN", "SUPER_ADMIN", "SALES_MANAGER", "SALES",
                                "PURCHASE_MANAGER", "WAREHOUSE_MANAGER", "PRODUCTION_MANAGER",
                                "FINANCE_MANAGER", "FINANCE"});
            } else {
                log.warn("[SchemaUpgradeEnsurer] 未找到数据看板父级菜单，跳过「经营看板」初始化");
            }

            // 改名（只改显示名，不动 route_path 与 permission_code）
            jdbcTemplate.update("UPDATE sys_menu SET menu_name = '销售预警' "
                    + "WHERE menu_code = 'crm_warning' AND menu_name <> '销售预警'");
            jdbcTemplate.update("UPDATE sys_menu SET menu_name = '我的驾驶舱' "
                    + "WHERE menu_code = 'crm_dashboard' AND menu_name <> '我的驾驶舱'");
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 销售过程菜单初始化跳过: {}", e.getMessage());
        }
    }

    /**
     * 【幂等】客户管理「自动化管控」分组（2026-10-09）。
     *
     * <p>把 2026-10-07 销售过程动作量化一族里的「销售目标 / 销售过程 / 销售预警」收进同一分组，
     * 左侧导航以二级菜单「自动化管控」折叠展开；「我的驾驶舱」保持客户管理直下（用户 2026-10-09 口径）。</p>
     *
     * <p>分组节点是纯分组（CATALOG、无 route_path），不参与权限判定 —— 左侧导航与权限
     * 均按子项 route_path 计算，故本节点不需要单独授权，也不会影响任何已有角色的权限。</p>
     *
     * <p>等价脚本：{@code deploy/sql/139_crm_automation_group.sql}。</p>
     */
    private void ensureCrmAutomationGroupMenu() {
        try {
            java.util.List<Long> crmParents = jdbcTemplate.queryForList(
                    "SELECT menu_id FROM sys_menu WHERE menu_code = 'crm'", Long.class);
            if (crmParents.isEmpty()) {
                log.warn("[SchemaUpgradeEnsurer] 未找到客户管理父级菜单，跳过「自动化管控」分组初始化");
                return;
            }
            Long crmId = crmParents.get(0);
            ensureMenu("crm_automation", "自动化管控", crmId, "CATALOG", null,
                    "crm:automation", null, 2, "MagicStick", true, "CRM");
            Long groupId = jdbcTemplate.queryForObject(
                    "SELECT menu_id FROM sys_menu WHERE menu_code = 'crm_automation'", Long.class);
            if (groupId == null) return;
            // 三项移入分组（幂等：父级已是本分组则不改）
            moveMenuToParent("crm_sales-target", "销售目标", groupId, 10);
            moveMenuToParent("crm_warning", "销售预警", groupId, 20);
            moveMenuToParent("crm_sales_activity", "销售过程", groupId, 30);
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 自动化管控分组初始化跳过: {}", e.getMessage());
        }
    }

    /** 幂等：把菜单挂到指定父级并设置排序（父级已一致则不改，避免每次启动写库） */
    private void moveMenuToParent(String menuCode, String menuName, Long parentId, int sortOrder) {
        int n = jdbcTemplate.update(
                "UPDATE sys_menu SET parent_menu_id = ?, sort_order = ?, updated_at = now() "
                        + "WHERE menu_code = ? AND (parent_menu_id IS DISTINCT FROM ?)",
                parentId, sortOrder, menuCode, parentId);
        if (n > 0) {
            log.info("[SchemaUpgradeEnsurer] menu '{}' moved under parent {} (sort={}).", menuName, parentId, sortOrder);
        }
    }

    /**
     * 【幂等】批量初始化「单据明细报表」菜单。
     *
     * <p>同组报表共用一个父级 = 该模块「分析报表」菜单的 parent_menu_id
     * （实库核对：库中并无独立「报表」菜单行，「报表」只是前端 menuConfig 的分组壳）。
     * 新增模块（库存/生产/委外/财务）只需再调一次本方法，不必重复写循环。
     *
     * @param siblingRoute 同组「分析报表」的 route_path（如 /sale/report/analysis）
     * @param menus        {菜单编码, 菜单名, 路由, 权限码, 排序}
     * @param roleCodes    需要授权的角色码
     */
    private void ensureDetailReportMenus(String siblingRoute, String[][] menus, String[] roleCodes) {
        java.util.List<Long> parents = jdbcTemplate.queryForList(
                "SELECT parent_menu_id FROM sys_menu WHERE route_path = ? AND parent_menu_id IS NOT NULL",
                Long.class, siblingRoute);
        if (parents.isEmpty()) {
            log.warn("[SchemaUpgradeEnsurer] 未找到 {} 的父级，跳过其单据明细报表菜单初始化", siblingRoute);
            return;
        }
        Long parentId = parents.get(0);
        for (String[] m : menus) {
            // m = {菜单编码, 菜单名, 路由, 权限码, 排序, 版本(可选；不传=通用)}
            ensureMenu(m[0], m[1], parentId, "MENU", m[2], m[3], null,
                    Integer.parseInt(m[4]), "DataLine", true, m.length > 5 ? m[5] : null);
            ensureMenuRoleGrants(m[0], m[2], roleCodes);
        }
    }

    /**
     * 【幂等】把某个菜单按 route_path 授权给指定角色（守则 2.7.2-3：角色权限升级必须自动授权，
     * 禁止要求部署后管理员手工点一遍）。
     *
     * <p>口径注意：{@code sys_role_permission.menu_id} 字段实际存的是 {@code sys_menu.route_path}
     * 字符串，不是数字 menu_id（与 deploy/sql/46、58 等脚本一致，已对实库核对）。
     * 已授权的角色命中 NOT EXISTS 判断直接跳过，重复启动 0 副作用。
     */
    private void ensureMenuRoleGrants(String menuCode, String routePath, String[] roleCodes) {
        try {
            String in = String.join(",", java.util.Collections.nCopies(roleCodes.length, "?"));
            java.util.List<Long> roleIds = jdbcTemplate.queryForList(
                    "SELECT role_id FROM sys_role WHERE role_code IN (" + in + ")", Long.class,
                    (Object[]) roleCodes);
            int inserted = 0;
            for (Long roleId : roleIds) {
                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_role_permission WHERE role_id = ? AND permission_type = '菜单' AND menu_id = ?",
                        Integer.class, roleId, routePath);
                if (exists == null || exists == 0) {
                    jdbcTemplate.update(
                            "INSERT INTO sys_role_permission (role_id, menu_id, permission_type, created_at) "
                                    + "VALUES (?, ?, '菜单', now())",
                            roleId, routePath);
                    inserted++;
                }
            }
            if (inserted > 0) {
                log.info("[SchemaUpgradeEnsurer] menu '{}' granted to {} role(s).", menuCode, inserted);
            }
        } catch (Exception e) {
            // 平台库无 sys_role / 无该菜单等情况忽略，绝不拉挂启动
            log.warn("[SchemaUpgradeEnsurer] menu grant skipped for {}: {}", menuCode, e.getMessage());
        }
    }

    private void ensureColumnIfMissing(String table, String column, String type) {
        ensureColumnIfMissing(jdbcTemplate, table, column, type);
    }

    /**
     * 逐租户独立库补齐列（2026-09-20 新增）。
     *
     * <p>踩过的坑：新增列的 SQL 只在「执行时那一个库」（主库）生效，启动自愈也只跑了主库那一个连接；
     * 而租户库是从模板库克隆出来的**快照**，早于补列创建的租户库会永久缺列 ——
     * 界面一切正常，一保存就报「当前事务被终止」（PG 事务已被中止，后续查询全部被忽略）。
     *
     * <p>这里按平台库 sys_tenant 的登记逐个 active 租户库跑同一份 {@link #BUSINESS_COLUMN_SPECS}，
     * 全部幂等（先查 information_schema）；单个库失败只记日志，绝不阻断启动。
     */

    /** 数据版本记录快照列放宽为 TEXT（2026-10-12）：整行JSON快照255必超长 → PG事务污染静默回滚（员工删除实测） */
    private void ensureDataVersionSnapshotText(JdbcTemplate target) {
        try {
            String type = target.queryForObject(
                    "SELECT data_type FROM information_schema.columns WHERE table_name = 'data_version_record' AND column_name = 'before_snapshot'",
                    String.class);
            if (type == null) return; // 表不存在（无版本记录功能的库）直接跳过
            if (!"text".equalsIgnoreCase(type)) {
                target.execute("ALTER TABLE data_version_record ALTER COLUMN before_snapshot TYPE TEXT");
                target.execute("ALTER TABLE data_version_record ALTER COLUMN after_snapshot TYPE TEXT");
                log.info("[SchemaUpgradeEnsurer] data_version_record 快照列已放宽为 TEXT（修复事务污染静默回滚）");
            }
        } catch (Exception e) {
            // 绝不因为 schema 检查异常把启动拉挂
            log.warn("[SchemaUpgradeEnsurer] data_version_record 快照列放宽跳过: {}", e.getMessage());
        }
    }

    /**
     * 销售订单明细「单价 / 本位币单价」列放宽为 numeric(18,6)（2026-10-13，121 单据 9000→9000.01 根治的精度侧）。
     *
     * <p>病根：unit_price 列是 numeric(18,2)，而含税价/不含税价两列是 (18,6)、前端单价输入框默认 4 位、
     * 设置中心有「单价小数位数」配置——单价列 2 位截断了反算价（含税 3000÷1.13=2654.8672…→2654.87），
     * 造成"单价×数量 ≠ 行金额"差 1 分，编辑页任何重算都会把价税合计漂移成 9000.01。
     * numeric(18,2)→(18,6) 向上放宽，存量数据无损；重复执行无副作用（幂等）。
     *
     * <p>覆盖：主库 + 每个租户独立库（同「三库齐」口径）。其余单据明细表单价列统一放宽列为待办。
     */
    private void ensureSaleOrderItemPricePrecision(JdbcTemplate target) {
        try {
            String type = target.queryForObject(
                    "SELECT data_type FROM information_schema.columns WHERE table_name = 'sale_order_item' AND column_name = 'unit_price'",
                    String.class);
            if (type == null) return; // 表不存在（无销售模块的库）直接跳过
            if ("numeric".equalsIgnoreCase(type) || "decimal".equalsIgnoreCase(type)) {
                Integer scale = target.queryForObject(
                        "SELECT numeric_scale FROM information_schema.columns WHERE table_name = 'sale_order_item' AND column_name = 'unit_price'",
                        Integer.class);
                if (scale != null && scale < 6) {
                    target.execute("ALTER TABLE sale_order_item ALTER COLUMN unit_price TYPE numeric(18,6)");
                    target.execute("ALTER TABLE sale_order_item ALTER COLUMN base_unit_price TYPE numeric(18,6)");
                    log.info("[SchemaUpgradeEnsurer] sale_order_item 单价列已放宽为 numeric(18,6)（根治反算舍入差 1 分）");
                }
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] sale_order_item 单价列放宽跳过: {}", e.getMessage());
        }
    }

    private void ensureColumnsOnTenantDatabases() {
        java.util.List<java.util.Map<String, Object>> tenants;
        try {
            tenants = platformJdbcTemplate.queryForList(
                    "SELECT DISTINCT db_name, db_host, db_port, db_username, db_password FROM sys_tenant "
                  + " WHERE status = 'active' AND db_name IS NOT NULL AND db_name <> ''");
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 租户库补列巡检跳过（平台库不可用）: {}", e.getMessage());
            return;
        }
        if (tenants == null || tenants.isEmpty()) return;

        String mainDb = null;
        try {
            mainDb = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        } catch (Exception ignore) {
            // 取不到主库名不影响巡检（下面按库名逐个处理）
        }

        int ok = 0, fail = 0, addedTotal = 0;
        for (java.util.Map<String, Object> row : tenants) {
            Object dbNameObj = row.get("db_name");
            if (dbNameObj == null) continue;
            String dbName = String.valueOf(dbNameObj).trim();
            if (dbName.isEmpty() || dbName.equals(mainDb)) continue;

            String host = row.get("db_host") == null ? "localhost" : String.valueOf(row.get("db_host"));
            int port = 5432;
            try {
                if (row.get("db_port") != null) port = Integer.parseInt(String.valueOf(row.get("db_port")));
            } catch (Exception ignore) {
                // 端口非法 → 用默认端口
            }
            String user = row.get("db_username") == null ? "" : String.valueOf(row.get("db_username"));
            String pwd = row.get("db_password") == null ? "" : String.valueOf(row.get("db_password"));

            java.sql.Connection conn = null;
            try {
                conn = java.sql.DriverManager.getConnection(
                        "jdbc:postgresql://" + host + ":" + port + "/" + dbName, user, pwd);
                JdbcTemplate tenant = new JdbcTemplate(
                        new org.springframework.jdbc.datasource.SingleConnectionDataSource(conn, true));
                for (String[] spec : BUSINESS_COLUMN_SPECS) {
                    if (ensureColumnIfMissing(tenant, spec[0], spec[1], spec[2])) addedTotal++;
                }
                // 建表类（非补列）也要覆盖租户库：质检模块表在 2026-10-07 新增两张关联表，
                // 早于本次创建/克隆的租户库不会有 → 这里逐库幂等补齐（含旧物料单值迁移）
                ensureQcTablesOn(tenant);
                // 工艺路线多物料/多分类 + 工序自定义字段（2026-11-26）：逐租户库幂等补齐
                ensureRoutingBindingTablesOn(tenant);
                // 化妆品批记录完整闭环（2026-11-06）：逐租户库幂等补齐 5 张表 + 批次质量列 + 参数种子
                ensureBatchRecordTablesOn(tenant);
                // 批次号规则生效 + 批次溯源（2026-11-06）：逐租户库幂等补齐索引 + 唯一口径 + 参数
                ensureBatchTraceSupportOn(tenant);
                // 入库单质检闭环（2026-11-07）：逐租户库幂等补齐质检模板/记录列 + 入库单质检列 + 默认报废仓
                ensureInboundQcSupportOn(tenant);
                // 基础资料联动（2026-11-08）：逐租户库存量单据简称/客户编号/供应商编号回填（只填空值）
                ensureMasterLinkageBackfillOn(tenant);
                // 行级 BOM（2026-11-06）：逐租户库幂等补齐明细行 BOM 列
                ensureLineLevelBomColumnsOn(tenant);
                // 多计量单位（2026-11-09）：逐租户库幂等补齐物料辅计量字段 + 换算关系表 + 单据明细折算列 + 库存双数量
                ensureMultiUnitSupportOn(tenant);
                // 生产参数收敛（2026-11-09）：逐租户库只保留「超过来源单数量」
                ensureProduceParamConvergence(tenant);
                // 工序扫码认领与按人报工（2026-10-25）：逐租户库幂等补齐
                ensureOperationClaimTables(tenant);
                // 工单进度对外分享（2026-11-25）：逐租户库幂等补齐
                ensureWorkOrderShareTable(tenant);
                // 小楷助手积分制（2026-10-24）：积分账户/流水表 + 月用量积分列，逐租户库幂等补齐
                com.erp.ai.credit.AiCreditSchema.ensure(tenant);
                // 价格策略改造（2026-10-10）：与主库同口径，逐租户库补齐
                ensurePriceStrategyTargetsOn(tenant);
                // 数值精度设置默认位数对齐（2026-10-10）：与主库同口径，逐租户库补齐
                ensureNumericPrecisionOn(tenant);
                // 列表默认视图种子（2026-10-14）：与主库同口径，逐租户库补齐
                ensureListDefaultViewSetting(tenant);
                // 收款单销售提成种子（2026-10-16）：与主库同口径，逐租户库补齐
                ensureReceiptCommissionSettings(tenant);
                // 应收单自动下推选项补全（2026-10-18）：与主库同口径，逐租户库补齐（只补选项，不改取值）
                ensureArSourceSetting(tenant);
                // 销售订单状态设置种子 + 明细「已开票金额」存量回填（2026-10-14）：与主库同口径，逐租户库补齐
                ensureSaleOrderCloseModeSetting(tenant);
                backfillTurnedInvoiceAmount(tenant, dbName);
                // 汇总收款行级 FIFO 回填（2026-10-14）：与主库同口径，逐租户库补齐
                ensureReceiptSummaryFifoAllocated(tenant, dbName);
                // 收付款核销明细孤儿清理（2026-10-19）：与主库同口径，逐租户库补齐
                ensureOrphanWriteoffCleaned(tenant, dbName);
                // 审核人系统占位清理（2026-10-28）：与主库同口径，逐租户库补齐
                clearSystemPlaceholderApprover(tenant, dbName);
                // 销售订单超量发货比例列（2026-10-31）：与主库同口径，逐租户库补齐
                ensureSaleOrderOvershipRatio(tenant, dbName);
                // 销售模块超过来源单数量三态策略（2026-11-02）：与主库同口径，逐租户库补齐
                ensureSaleOverQtyPolicySettings(tenant, dbName);
                // 自动下推单据开关（2026-11-08）：与主库同口径，逐租户库补齐
                ensureSaleAutoPushSettings(tenant, dbName);
                // 单据统一状态机接入（2026-11-09）：与主库同口径，逐租户库补齐
                ensureBillStatusColumns(tenant, dbName);
                // SPC质量看板 + 自定义看板（2026-11-05）：建表 + 菜单 + 授权，逐租户库同口径
                ensureSpcCustomBoardOn(tenant, dbName);
                // sys_config.config_value 放宽为 TEXT（2026-10-10）：与主库同口径，逐租户库补齐
                ensureSysConfigValueText(tenant);
                // 销售订单明细单价列放宽 numeric(18,6)（2026-10-13）：与主库同口径，逐租户库补齐
                ensureSaleOrderItemPricePrecision(tenant);
                // 数据版本记录快照列放宽（2026-10-12）：与主库同口径，逐租户库补齐
                ensureDataVersionSnapshotText(tenant);
                // 开账引导基础设施（2026-10-11）：字段显示名表 + 引导状态种子，逐租户库补齐
                // （存量租户：无引导状态记录 → 种子置 done，绝不对老客户弹向导；
                //   新建租户：建租户链路已显式写 pending → NOT EXISTS 跳过，保持待引导）
                ensureOnboardingInfra(tenant, false);
                // 编号规则唯一清单补齐（2026-10-12）：存量租户库同样按"缺则补"对齐（与主库同清单）
                try {
                    com.erp.serialno.support.SerialNoDefaults.ensureOn(tenant);
                } catch (Exception e) {
                    log.warn("[SchemaUpgradeEnsurer] 租户库编号规则补齐失败 {}: {}", dbName, e.getMessage());
                }
                // 基础币种补齐（2026-10-12）：存量租户库币种表实测全空——银行账户必填币种被卡
                try {
                    com.erp.tenant.support.BaseDataDefaults.ensureCurrenciesOn(tenant);
                } catch (Exception e) {
                    log.warn("[SchemaUpgradeEnsurer] 租户库基础币种补齐失败 {}: {}", dbName, e.getMessage());
                }
                // AI用量管理菜单路径修正（2026-10-12）：与主库同口径，逐租户库幂等修正
                ensureAiTokenUsageRouteFix(tenant);
                ok++;
            } catch (Exception e) {
                fail++;
                log.warn("[SchemaUpgradeEnsurer] 租户库补列失败 {}: {}", dbName, e.getMessage());
            } finally {
                if (conn != null) {
                    try { conn.close(); } catch (Exception ignore) { }
                }
            }
        }
        log.info("[SchemaUpgradeEnsurer] 租户库补列巡检: 登记 {} 个库（成功 {} / 失败 {} / 新增列 {}）",
                tenants.size(), ok, fail, addedTotal);
        // 租户模板库快照列放宽（2026-10-12）：新租户库由模板库克隆，模板不修则新库永远带旧列型
        ensureTenantTemplateSnapshotText(tenants);
        // 租户模板库编号规则补齐（2026-10-12）：模板是克隆源，模板缺规则则新建租户库必缺
        // （121 价格等级实锤）——按同一唯一清单"缺则补"，建租户链路还有第二道兜底。
        ensureTenantTemplateSerialNo(tenants);
        // 租户模板库基础币种补齐（2026-10-12）：模板币种表实测 0 行 → 新租户银行账户/单据默认币种被卡
        ensureTenantTemplateCurrencies(tenants);
    }

    /** 租户模板库基础币种补齐（幂等；模板库不存在或不可连时跳过） */
    private void ensureTenantTemplateCurrencies(java.util.List<Map<String, Object>> tenants) {
        final String templateDb = "erp_tenant_template";
        try {
            Integer exists = platformJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_database WHERE datname = ?", Integer.class, templateDb);
            if (exists == null || exists == 0) return;
            if (tenants == null || tenants.isEmpty()) return;
            Map<String, Object> first = tenants.get(0);
            String host = first.get("db_host") == null ? "localhost" : String.valueOf(first.get("db_host"));
            int port = 5432;
            try { if (first.get("db_port") != null) port = Integer.parseInt(String.valueOf(first.get("db_port"))); } catch (Exception ignore) { }
            String user = first.get("db_username") == null ? "" : String.valueOf(first.get("db_username"));
            String pwd = first.get("db_password") == null ? "" : String.valueOf(first.get("db_password"));
            java.sql.Connection conn = null;
            try {
                conn = java.sql.DriverManager.getConnection(
                        "jdbc:postgresql://" + host + ":" + port + "/" + templateDb, user, pwd);
                JdbcTemplate tpl = new JdbcTemplate(
                        new org.springframework.jdbc.datasource.SingleConnectionDataSource(conn, true));
                int added = com.erp.tenant.support.BaseDataDefaults.ensureCurrenciesOn(tpl);
                if (added > 0) {
                    log.info("[SchemaUpgradeEnsurer] 租户模板库基础币种补齐: 新增 {} 条", added);
                }
                // 顺带：模板库 AI 用量管理菜单路径修正（与主库/租户库同口径，幂等）
                ensureAiTokenUsageRouteFix(tpl);
                // 小楷助手积分制（2026-10-24）：模板库是新建租户的克隆源，必须同步具备积分表
                com.erp.ai.credit.AiCreditSchema.ensure(tpl);
                // 工序扫码认领与按人报工（2026-10-25）：模板库同样必须具备，否则新租户克隆后缺表
                ensureOperationClaimTables(tpl);
                // 工单进度对外分享（2026-11-25）：模板库同样必须具备，否则新租户克隆后缺表
                ensureWorkOrderShareTable(tpl);
                // 单据统一状态机接入（2026-11-09）：模板库是新建租户的克隆源，缺列会让新租户永久缺列
                ensureBillStatusColumns(tpl, templateDb);
            } finally {
                if (conn != null) { try { conn.close(); } catch (Exception ignore) { } }
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 租户模板库基础币种补齐跳过: {}", e.getMessage());
        }
    }

    /** 租户模板库编号规则补齐（幂等；模板库不存在或不可连时跳过） */
    private void ensureTenantTemplateSerialNo(java.util.List<Map<String, Object>> tenants) {
        final String templateDb = "erp_tenant_template";
        try {
            Integer exists = platformJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_database WHERE datname = ?", Integer.class, templateDb);
            if (exists == null || exists == 0) return;
            if (tenants == null || tenants.isEmpty()) return;
            Map<String, Object> first = tenants.get(0);
            String host = first.get("db_host") == null ? "localhost" : String.valueOf(first.get("db_host"));
            int port = 5432;
            try { if (first.get("db_port") != null) port = Integer.parseInt(String.valueOf(first.get("db_port"))); } catch (Exception ignore) { }
            String user = first.get("db_username") == null ? "" : String.valueOf(first.get("db_username"));
            String pwd = first.get("db_password") == null ? "" : String.valueOf(first.get("db_password"));
            java.sql.Connection conn = null;
            try {
                conn = java.sql.DriverManager.getConnection(
                        "jdbc:postgresql://" + host + ":" + port + "/" + templateDb, user, pwd);
                JdbcTemplate tpl = new JdbcTemplate(
                        new org.springframework.jdbc.datasource.SingleConnectionDataSource(conn, true));
                int added = com.erp.serialno.support.SerialNoDefaults.ensureOn(tpl);
                if (added > 0) {
                    log.info("[SchemaUpgradeEnsurer] 租户模板库编号规则补齐: 新增 {} 条（清单共 {} 条）",
                            added, com.erp.serialno.support.SerialNoDefaults.size());
                }
                // 模板库是新建租户的克隆源（2026-10-25）：物料分类历史上 auto_generate=0，
                // 克隆后每个新租户新增分类都要手填编码 → 在克隆源上对齐默认值（默认自动生成）。
                // 只对模板库执行，存量租户库不动（管理员主动关闭的开关不能被自愈翻回）。
                int autoFixed = com.erp.serialno.support.SerialNoDefaults.alignDefaultAutoGenerateOn(
                        tpl, "BD_MATERIAL_CATEGORY");
                if (autoFixed > 0) {
                    log.info("[SchemaUpgradeEnsurer] 租户模板库物料分类编码默认自动生成对齐: 修正 {} 条", autoFixed);
                }
            } finally {
                if (conn != null) { try { conn.close(); } catch (Exception ignore) { } }
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 租户模板库编号规则补齐跳过: {}", e.getMessage());
        }
    }

    /** 租户模板库 data_version_record 快照列放宽为 TEXT（幂等；模板库不存在或不可连时跳过） */
    private void ensureTenantTemplateSnapshotText(java.util.List<Map<String, Object>> tenants) {
        final String templateDb = "erp_tenant_template";
        try {
            Integer exists = platformJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_database WHERE datname = ?", Integer.class, templateDb);
            if (exists == null || exists == 0) return;
            if (tenants == null || tenants.isEmpty()) return;
            Map<String, Object> first = tenants.get(0);
            String host = first.get("db_host") == null ? "localhost" : String.valueOf(first.get("db_host"));
            int port = 5432;
            try { if (first.get("db_port") != null) port = Integer.parseInt(String.valueOf(first.get("db_port"))); } catch (Exception ignore) { }
            String user = first.get("db_username") == null ? "" : String.valueOf(first.get("db_username"));
            String pwd = first.get("db_password") == null ? "" : String.valueOf(first.get("db_password"));
            java.sql.Connection conn = null;
            try {
                conn = java.sql.DriverManager.getConnection(
                        "jdbc:postgresql://" + host + ":" + port + "/" + templateDb, user, pwd);
                JdbcTemplate tpl = new JdbcTemplate(
                        new org.springframework.jdbc.datasource.SingleConnectionDataSource(conn, true));
                ensureDataVersionSnapshotText(tpl);
            } finally {
                if (conn != null) { try { conn.close(); } catch (Exception ignore) { } }
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 租户模板库快照列放宽跳过: {}", e.getMessage());
        }
    }

    /**
     * 租户库账号 → 平台库 自愈（2026-10-12 新增，121 生产实锤）。
     *
     * <p><b>背景</b>：「基础资料 → 员工档案」建的登录账号此前只写租户独立库，而登录只在
     * 业务主库 + 平台库两处查找 → 租户员工一律"用户名或密码错误"（查无此人与密码错同一句话）。
     * 写入路径已由员工档案链路补同步；本方法兜底补齐<b>存量</b>缺口。</p>
     *
     * <p><b>口径（只加不改）</b>：
     * <ul>
     *   <li>主库本身跳过（主库账号本就在登录查找范围内）；</li>
     *   <li>平台库已有同一账号（同 id 同用户名）→ 跳过；</li>
     *   <li>主键或用户名冲突 → 跳过并告警（撞名账号需人工改用户名，用户名全局唯一是既有设计）；</li>
     *   <li>模板库带入的陈旧用户（创建时间早于租户创建 1 分钟以上）→ 跳过，防止老账号被错误挂进租户；</li>
     *   <li>只有"平台库按 id 和用户名都查不到"的账号才补写记录 + 建立租户关联。</li>
     * </ul></p>
     */
    private void healTenantUsersToPlatform() {
        List<Map<String, Object>> tenants;
        try {
            tenants = platformJdbcTemplate.queryForList(
                    "SELECT tenant_id, tenant_code, db_name, db_host, db_port, db_username, db_password, created_at " +
                  "FROM sys_tenant WHERE status = 'active' AND db_name IS NOT NULL AND db_name <> ''");
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 租户账号自愈跳过（平台库不可用）: {}", e.getMessage());
            return;
        }
        if (tenants == null || tenants.isEmpty()) return;

        String mainDb = null;
        try {
            mainDb = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        } catch (Exception ignore) {
            // 取不到主库名时按库名逐个处理
        }
        String platformDb = platformUserSyncService.platformDbName();

        int dbs = 0, inserted = 0, exists = 0, conflict = 0, failed = 0, staleSkipped = 0;
        // 2026-11-12：平台库如果就是主业务库（121 单机客户形态，两个地址都指向 erp_db），
        // 这个库本身也是默认公司的业务库，不能跳过 —— 跳过会让默认公司的账号永远补不上公司归属
        // （实证：张天养在默认公司有账号，却只挂了另一家公司，登录只返回 1 家公司、不弹选择）。
        // 只有"平台库独立于主库"时，才按平台库跳过（避免把平台专用账号挂进某家公司）。
        boolean platformIsSeparate = platformDb != null && !platformDb.isBlank()
                && (mainDb == null || !platformDb.equalsIgnoreCase(mainDb));
        for (Map<String, Object> row : tenants) {
            Object dbNameObj = row.get("db_name");
            if (dbNameObj == null) continue;
            String dbName = String.valueOf(dbNameObj).trim();
            if (dbName.isEmpty() || (platformIsSeparate && dbName.equalsIgnoreCase(platformDb))) continue;
            Long tenantId = row.get("tenant_id") instanceof Number
                    ? ((Number) row.get("tenant_id")).longValue() : null;
            java.sql.Timestamp tenantCreatedAt = row.get("created_at") instanceof java.sql.Timestamp
                    ? (java.sql.Timestamp) row.get("created_at") : null;

            String host = row.get("db_host") == null ? "localhost" : String.valueOf(row.get("db_host"));
            int port = 5432;
            try {
                if (row.get("db_port") != null) port = Integer.parseInt(String.valueOf(row.get("db_port")));
            } catch (Exception ignore) { }
            String user = row.get("db_username") == null ? "" : String.valueOf(row.get("db_username"));
            String pwd = row.get("db_password") == null ? "" : String.valueOf(row.get("db_password"));

            java.sql.Connection conn = null;
            try {
                conn = java.sql.DriverManager.getConnection(
                        "jdbc:postgresql://" + host + ":" + port + "/" + dbName, user, pwd);
                JdbcTemplate tenant = new JdbcTemplate(
                        new org.springframework.jdbc.datasource.SingleConnectionDataSource(conn, true));
                List<Map<String, Object>> users = tenant.queryForList(
                        "SELECT user_id, user_code, username, password, real_name, is_admin, is_enabled, " +
                      "user_type, auth_type, status, created_at, updated_at FROM sys_user");
                dbs++;
                for (Map<String, Object> ur : users) {
                    String username = ur.get("username") == null ? null : String.valueOf(ur.get("username"));
                    String passwordHash = ur.get("password") == null ? null : String.valueOf(ur.get("password"));
                    if (username == null || username.isBlank() || passwordHash == null || passwordHash.isBlank()) continue;
                    // 陈旧模板用户：创建时间早于租户创建 1 分钟以上 → 不是本租户建的人，不挂进租户
                    if (tenantCreatedAt != null && ur.get("created_at") instanceof java.sql.Timestamp uCreated
                            && uCreated.before(new java.sql.Timestamp(tenantCreatedAt.getTime() - 60_000L))) {
                        staleSkipped++;
                        continue;
                    }
                    com.erp.system.service.PlatformUserSyncService.TenantUserRow u =
                            new com.erp.system.service.PlatformUserSyncService.TenantUserRow();
                    u.userId = ur.get("user_id") instanceof Number ? ((Number) ur.get("user_id")).longValue() : null;
                    if (u.userId == null) continue;
                    u.userCode = ur.get("user_code") == null ? null : String.valueOf(ur.get("user_code"));
                    u.username = username;
                    u.password = passwordHash;
                    u.realName = ur.get("real_name") == null ? null : String.valueOf(ur.get("real_name"));
                    u.isAdmin = ur.get("is_admin") instanceof Number ? ((Number) ur.get("is_admin")).shortValue() : (short) 0;
                    u.isEnabled = ur.get("is_enabled") instanceof Number ? ((Number) ur.get("is_enabled")).shortValue() : (short) 1;
                    u.userType = ur.get("user_type") == null ? null : String.valueOf(ur.get("user_type"));
                    u.authType = ur.get("auth_type") == null ? null : String.valueOf(ur.get("auth_type"));
                    u.status = ur.get("status") == null ? null : String.valueOf(ur.get("status"));
                    u.createdAt = ur.get("created_at") instanceof java.sql.Timestamp ? ((java.sql.Timestamp) ur.get("created_at")).toLocalDateTime() : null;
                    u.updatedAt = ur.get("updated_at") instanceof java.sql.Timestamp ? ((java.sql.Timestamp) ur.get("updated_at")).toLocalDateTime() : null;
                    String r = platformUserSyncService.healInsertUser(u, tenantId);
                    switch (r) {
                        case com.erp.system.service.PlatformUserSyncService.HEAL_INSERTED -> {
                            inserted++;
                            log.info("[SchemaUpgradeEnsurer] 租户账号已补写平台库: db={}, username={}, userId={}", dbName, username, u.userId);
                        }
                        case com.erp.system.service.PlatformUserSyncService.HEAL_EXISTS -> exists++;
                        case com.erp.system.service.PlatformUserSyncService.HEAL_CONFLICT -> {
                            conflict++;
                            log.warn("[SchemaUpgradeEnsurer] 租户账号无法自动同步（主键或用户名冲突，需人工改用户名后由员工档案重新保存）: db={}, username={}, userId={}", dbName, username, u.userId);
                        }
                        default -> failed++;
                    }
                }
            } catch (Exception e) {
                failed++;
                log.warn("[SchemaUpgradeEnsurer] 租户账号自愈失败 {}: {}", dbName, e.getMessage());
            } finally {
                if (conn != null) {
                    try { conn.close(); } catch (Exception ignore) { }
                }
            }
        }
        log.info("[SchemaUpgradeEnsurer] 租户账号平台库自愈: 巡检{}库, 补写{}, 已有{}, 冲突{}, 失败{}, 陈旧跳过{}",
                dbs, inserted, exists, conflict, failed, staleSkipped);
    }

    /**
     * 开账引导基础设施（2026-10-11）：字段显示名表 + 引导状态种子 + 小楷初始化开关。
     *
     * <p>幂等（守则 2.7.1-2）：建表 IF NOT EXISTS；两条种子 INSERT 都带 NOT EXISTS。
     * 引导状态种子的关键口径：无记录 = 存量库 → 置 done（老客户绝不被弹向导）；
     * 新租户库在建租户时已被显式写 pending → 本方法跳过，保持待引导。</p>
     */
    private void ensureOnboardingInfra(JdbcTemplate target, boolean mainDb) {
        try {
            // ① 字段显示名表（每租户一份：按"原字段名"存全站显示名覆盖，前端翻译出口最高优先级消费）
            target.execute("CREATE TABLE IF NOT EXISTS sys_field_label ("
                    + " id BIGSERIAL PRIMARY KEY,"
                    + " field_key VARCHAR(100) NOT NULL UNIQUE,"
                    + " display_name VARCHAR(100) NOT NULL,"
                    + " updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + " updated_by VARCHAR(50))");
            // ② 小楷"按行业初始化"开关（默认开：只加数据不删改、强制确认卡、仅管理员可执行）
            target.update(
                    "INSERT INTO sys_config (config_key, config_value, config_name, config_group) "
                    + "SELECT 'ai.tool.initialize.enabled', 'true', 'AI开账初始化', 'ai' "
                    + "WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.tool.initialize.enabled')");
            // ③ 引导状态种子（见方法注释的存量/新建口径）
            target.update(
                    "INSERT INTO sys_config (config_key, config_value, config_name, config_group) "
                    + "SELECT 'tenant.onboarding.status', 'done', '开账引导状态', 'onboarding' "
                    + "WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'tenant.onboarding.status')");
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 开账引导基础设施跳过({}): {}", mainDb ? "主库" : "租户库", e.getMessage());
        }
    }

    /**
     * 数值精度设置（2026-10-10）：让设置中心的「小数位数设置」真正生效。
     *
     * <p><b>背景</b>：设置中心的数量/金额/单价/汇率/百分比小数位数此前**无任何业务代码消费**（纯摆设）。
     * 本次把前端与后端计算统一改为读设置后，「默认值必须=旧行为」——故把默认位数对齐到各系统页
     * 当前写死的主流位数（数量4 / 单价4 / 金额2 / 汇率6 / 百分比2），并把 precision.* 归并到「小数位数设置」分区。</p>
     *
     * <p><b>幂等</b>：只在"仍等于旧初始化默认值（数量2 / 汇率4）"时才改写，绝不覆盖管理员已改过的值；
     * 归组/归分区/补名为纯覆盖写，重复执行结果一致。等价脚本：{@code deploy/sql/148_numeric_precision_settings.sql}。</p>
     */
    private void ensureNumericPrecisionSettings() {
        try {
            ensureNumericPrecisionOn(jdbcTemplate);
            log.info("[SchemaUpgradeEnsurer] 数值精度设置已对齐：数量4/单价4/金额2/汇率6/百分比2（设置中心 → 系统参数 → 小数位数设置）");
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 数值精度设置对齐跳过: {}", e.getMessage());
        }
    }

    /** 在指定库执行数值精度设置的对齐（主库与各租户库共用；单条失败只记日志、不阻断）。 */
    private void ensureNumericPrecisionOn(JdbcTemplate target) {
        try {
            // ① 旧默认 → 新默认（仅当仍等于旧初始化默认值时才改写，保护管理员已改过的值）
            target.update("UPDATE sys_config SET config_value = '4', updated_at = NOW() "
                    + " WHERE config_key = 'precision.quantity' AND config_value = '2'");
            target.update("UPDATE sys_config SET config_value = '6', updated_at = NOW() "
                    + " WHERE config_key = 'precision.rate' AND config_value = '4'");
            // ② 归组 + 归分区：precision.* 全部收进「系统参数 → 小数位数设置」
            target.update("UPDATE sys_config SET config_group = 'sys_param' WHERE config_key LIKE 'precision.%'");
            target.update("UPDATE sys_config SET config_section = '小数位数设置' "
                    + " WHERE config_key IN ('precision.quantity','precision.amount','precision.price',"
                    + "'precision.rate','precision.percent','precision.rounding.mode','precision.trim.zero')");
            // ③ 补名称（空名才补）
            target.update("UPDATE sys_config SET config_name = '数量小数位数' WHERE config_key = 'precision.quantity' AND (config_name IS NULL OR config_name = '')");
            target.update("UPDATE sys_config SET config_name = '金额小数位数' WHERE config_key = 'precision.amount' AND (config_name IS NULL OR config_name = '')");
            target.update("UPDATE sys_config SET config_name = '单价小数位数' WHERE config_key = 'precision.price' AND (config_name IS NULL OR config_name = '')");
            target.update("UPDATE sys_config SET config_name = '汇率小数位数' WHERE config_key = 'precision.rate' AND (config_name IS NULL OR config_name = '')");
            target.update("UPDATE sys_config SET config_name = '百分比小数位数' WHERE config_key = 'precision.percent' AND (config_name IS NULL OR config_name = '')");
        } catch (Exception e) {
            log.debug("[SchemaUpgradeEnsurer] 数值精度设置对齐跳过（当前库可能无 sys_config）: {}", e.getMessage());
        }
    }

    /**
     * 【2026-10-14】列表默认视图种子（列表页「汇总/明细」双视图二期）。
     * 缺 key 才插入（存在则不动管理员已改过的值）；主库 + 逐租户库同口径。
     * 等价脚本：db/157_list_default_view_setting.sql = deploy/sql/155_list_default_view_setting.sql。
     */
    private void ensureListDefaultViewSetting(JdbcTemplate target) {
        try {
            Integer cnt = target.queryForObject(
                    "SELECT COUNT(*) FROM sys_config WHERE config_key = 'sys.list.default.view'", Integer.class);
            if (cnt == null || cnt == 0) {
                target.update("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                                + " config_section, config_type, config_options, sort_order, remark,"
                                + " created_at, updated_at, created_by, updated_by)"
                                + " VALUES ('sys.list.default.view', '汇总', '列表默认视图', 'sys_param', '基础设置',"
                                + " 'select', '[{\"label\":\"汇总\",\"value\":\"汇总\"},{\"label\":\"明细\",\"value\":\"明细\"}]',"
                                + " 27, '列表页默认按汇总显示一张单一行；切换为明细后，未单独设置过的列表页默认按明细行显示',"
                                + " NOW(), NOW(), 'system', 'system')");
                log.info("[SchemaUpgradeEnsurer] 列表默认视图设置项已补种（默认=汇总，设置中心 → 系统参数 → 基础设置）");
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 列表默认视图种子跳过: {}", e.getMessage());
        }
    }

    /**
     * 【2026-10-14】销售订单状态设置种子（完结口径三模式：销售发货单/销售发票/销售发货+销售发票+收款单）。
     * 缺 key 才插入（存在则不动管理员已改过的值）；主库 + 逐租户库同口径。
     * 等价脚本：db/159 = deploy/sql/159_sale_order_close_mode_setting.sql。
     */
    private void ensureSaleOrderCloseModeSetting(JdbcTemplate target) {
        try {
            Integer cnt = target.queryForObject(
                    "SELECT COUNT(*) FROM sys_config WHERE config_key = 'sale.order.auto.close.mode'", Integer.class);
            if (cnt == null || cnt == 0) {
                target.update("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                                + " config_section, config_type, config_options, sort_order, remark,"
                                + " created_at, updated_at, created_by, updated_by)"
                                + " VALUES ('sale.order.auto.close.mode', '销售发货单', '销售订单状态设置', 'sale', '销售订单',"
                                + " 'select', '[{\"label\":\"销售发货单\",\"value\":\"销售发货单\"},{\"label\":\"销售发票\",\"value\":\"销售发票\"},{\"label\":\"销售发货+销售发票+收款单\",\"value\":\"销售发货+销售发票+收款单\"}]',"
                                + " 60, '销售发货单：当前订单发完货之后才会进入自动结案状态；销售发票：当前销售订单下推收完发票的金额之后才会进入自动结案状态；销售发货+销售发票+收款单：当前销售订单的下游单据这三种单必须全部完成才算是自动结案',"
                                + " NOW(), NOW(), 'system', 'system')");
                log.info("[SchemaUpgradeEnsurer] 销售订单状态设置已补种（默认=销售发货单，设置中心 → 系统参数 → 销售参数）");
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 销售订单状态设置种子跳过: {}", e.getMessage());
        }
    }

    /**
     * 【2026-10-16】收款单销售提成种子：启用开关（默认开）+ 默认比例（默认 3）。
     * 缺 key 才插入（存在则不动管理员已改过的值）；主库 + 逐租户库同口径。
     * 等价脚本：db/162 = deploy/sql/162_receipt_commission.sql。
     */
    private void ensureReceiptCommissionSettings(JdbcTemplate target) {
        try {
            Integer cnt = target.queryForObject(
                    "SELECT COUNT(*) FROM sys_config WHERE config_key = 'finance.commission.enabled'", Integer.class);
            if (cnt == null || cnt == 0) {
                target.update("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                                + " config_section, config_type, config_options, sort_order, remark,"
                                + " created_at, updated_at, created_by, updated_by)"
                                + " VALUES ('finance.commission.enabled', 'true', '启用销售提成', 'finance', '收款发货管控',"
                                + " 'boolean', NULL, 16,"
                                + " '开启后收款单显示提成字段（提成比例/特殊服务费/不含税收款金额/销售提成）与核销明细销售员列；关闭即完全旧行为',"
                                + " NOW(), NOW(), 'system', 'system')");
                log.info("[SchemaUpgradeEnsurer] 启用销售提成开关已补种（默认=开，设置中心 → 财务参数 → 收款发货管控）");
            }
            Integer cnt2 = target.queryForObject(
                    "SELECT COUNT(*) FROM sys_config WHERE config_key = 'finance.commission.default.rate'", Integer.class);
            if (cnt2 == null || cnt2 == 0) {
                target.update("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                                + " config_section, config_type, config_options, sort_order, remark,"
                                + " created_at, updated_at, created_by, updated_by)"
                                + " VALUES ('finance.commission.default.rate', '3', '销售提成默认比例', 'finance', '收款发货管控',"
                                + " 'number', NULL, 17,"
                                + " '新开收款单「提成比例%」的默认值（单内可改）；销售提成=(不含税收款金额−特殊服务费)×比例%',"
                                + " NOW(), NOW(), 'system', 'system')");
                log.info("[SchemaUpgradeEnsurer] 销售提成默认比例已补种（默认=3，设置中心 → 财务参数 → 收款发货管控）");
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 收款单提成参数种子跳过: {}", e.getMessage());
        }
    }

    /**
     * 【2026-10-18】「收款单生成方式」设置项选项补全（设计图 v3）。
     *
     * <p>口径：业务单据审核后按配置直接生成**收款单草稿**（不是应收单）；本项只控制"什么时候自动生成"。
     * 缺 key 时插入默认值 none（不生成）；有 key 时只补显示名/说明/选项，
     * **不覆盖管理员后续在界面上选的值**（重启不得改变业务行为）。
     * 主库 + 逐租户库同口径；等价脚本：db/163_receipt_gen_and_detail_meta.sql。
     */
    private void ensureArSourceSetting(JdbcTemplate target) {
        try {
            Integer cnt = target.queryForObject(
                    "SELECT COUNT(*) FROM sys_config WHERE config_key = 'finance.ar.source_bill_type'", Integer.class);
            String options = "[{\"label\":\"不生成\",\"value\":\"none\"},"
                    + "{\"label\":\"销售订单审核\",\"value\":\"sale_order\"},"
                    + "{\"label\":\"销售出库单审核\",\"value\":\"sale_delivery\"},"
                    + "{\"label\":\"销售发票审核\",\"value\":\"sale_invoice\"}]";
            String remark = "什么时候自动生成收款单草稿：销售订单审核 / 销售出库单审核 / 销售发票审核 / 不生成（全手工开）；生成的是草稿，财务核对后提交才生效";
            if (cnt == null || cnt == 0) {
                target.update("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                                + " config_type, config_options, sort_order, remark,"
                                + " created_at, updated_at, created_by, updated_by)"
                                + " VALUES ('finance.ar.source_bill_type', 'none', '收款单生成方式', 'finance',"
                                + " 'select', ?, 15, ?, NOW(), NOW(), 'system', 'system')",
                        options, remark);
                log.info("[SchemaUpgradeEnsurer] 收款单生成方式设置项已补种（默认=不生成）");
            } else {
                int n = target.update("UPDATE sys_config SET config_name = '收款单生成方式',"
                                + " remark = ?, config_options = ?, updated_at = NOW()"
                                + " WHERE config_key = 'finance.ar.source_bill_type'"
                                + "   AND (COALESCE(config_options, '') <> ? OR COALESCE(config_name, '') <> '收款单生成方式')",
                        remark, options, options);
                if (n > 0) {
                    log.info("[SchemaUpgradeEnsurer] 收款单生成方式选项已补全（当前取值未改动）");
                }
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 收款单生成方式选项补全跳过: {}", e.getMessage());
        }
    }

    /**
     * 【2026-10-14】明细行「已开票金额」存量回填（幂等：只回填列值为 0 的行）。
     * 口径与运行时回写镜像：已生效发票、来源为「订单直开」或「经出库明细」两条链路；
     * 「销售退货单」来源不回写订单。聚合为 0 的行不动（真实无票）。
     */
    private void backfillTurnedInvoiceAmount(JdbcTemplate target, String dbName) {
        try {
            int n = target.update(
                    "UPDATE sale_order_item oi SET turned_invoice_amount = agg.amt FROM ("
                    + "  SELECT src_item_id AS oid, SUM(amt) AS amt FROM ("
                    + "    SELECT i.source_item_id AS src_item_id, COALESCE(i.net_amount,0) AS amt"
                    + "    FROM sale_invoice_item i JOIN sale_invoice h ON h.invoice_id = i.invoice_id"
                    + "    WHERE h.status = '已生效' AND i.source_bill_type = '销售订单' AND i.source_item_id IS NOT NULL"
                    + "    UNION ALL"
                    + "    SELECT di.source_item_id AS src_item_id, COALESCE(i.net_amount,0) AS amt"
                    + "    FROM sale_invoice_item i JOIN sale_invoice h ON h.invoice_id = i.invoice_id"
                    + "    JOIN sale_delivery_item di ON di.delivery_item_id = i.source_item_id"
                    + "    WHERE h.status = '已生效' AND i.source_bill_type IN ('销售出库单','销售出库') AND di.source_item_id IS NOT NULL"
                    + "  ) t GROUP BY src_item_id"
                    + ") agg WHERE oi.order_item_id = agg.oid AND COALESCE(oi.turned_invoice_amount,0) = 0 AND agg.amt <> 0");
            if (n > 0) {
                log.info("[SchemaUpgradeEnsurer] 明细「已开票金额」存量回填 {}: {} 行", dbName, n);
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 已开票金额回填跳过 {}: {}", dbName, e.getMessage());
        }
    }

    /**
     * 【2026-10-19】收付款核销明细孤儿清理（删除收/付款单时级联登记此前漏了核销明细表）。
     *
     * <p>现象（121 生产实锤）：草稿收款单删除后，bill_writeoff_detail 里留下指向已删除收款单的
     * 未冲销核销行 → 订单「收款详情」弹窗出现一条无收款单号的幽灵收款记录，而列表"已收"为 0，
     * 用户误以为"收过款没回写"。</p>
     *
     * <p>口径：只清理 <b>未冲销</b> 且父单（收款单/付款单）已不存在的行——未冲销行未产生任何财务影响
     * （确认核销时才回写订单/应收，见 FinanceServiceImpl.rollbackReceiptWriteback 注释）；
     * <b>已冲销</b>的孤儿行意味着曾有已确认单据被删（正常链路不可能出现），只告警不清理，留给人工核对。
     * 幂等：无孤儿即 0 行，重复执行结果一致。</p>
     */
    private void ensureOrphanWriteoffCleaned(JdbcTemplate target, String dbName) {
        try {
            int receipts = target.update(
                "DELETE FROM bill_writeoff_detail w WHERE w.bill_type = '收款单' AND w.settled_qty = '未冲销'"
                + " AND NOT EXISTS (SELECT 1 FROM cash_receipt r WHERE r.receipt_id = w.bill_id)");
            int payments = target.update(
                "DELETE FROM bill_writeoff_detail w WHERE w.bill_type = '付款单' AND w.settled_qty = '未冲销'"
                + " AND NOT EXISTS (SELECT 1 FROM cash_payment p WHERE p.payment_id = w.bill_id)");
            if (receipts + payments > 0) {
                log.info("[SchemaUpgradeEnsurer] 收付款核销孤儿明细清理 {}: 收款侧 {} 行 / 付款侧 {} 行", dbName, receipts, payments);
            }
            try {
                Long confirmedOrphan = target.queryForObject(
                    "SELECT COUNT(*) FROM bill_writeoff_detail w WHERE w.settled_qty = '已冲销'"
                    + " AND ( (w.bill_type = '收款单' AND NOT EXISTS (SELECT 1 FROM cash_receipt r WHERE r.receipt_id = w.bill_id))"
                    + "    OR (w.bill_type = '付款单' AND NOT EXISTS (SELECT 1 FROM cash_payment p WHERE p.payment_id = w.bill_id)) )",
                    Long.class);
                if (confirmedOrphan != null && confirmedOrphan > 0) {
                    log.warn("[SchemaUpgradeEnsurer] {} 存在已冲销的孤儿核销明细 {} 行（曾有已确认收/付款单被删，正常链路不应出现），请人工核对",
                        dbName, confirmedOrphan);
                }
            } catch (Exception ignore) {
                // 体检查询失败不阻断（表缺失等），清理主体已执行
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 核销孤儿清理跳过 {}: {}", dbName, e.getMessage());
        }
    }

    /**
     * 【2026-10-28】审核人系统占位清理（用户口径：免审批单据没有人工审核人，审核人留空）。
     *
     * <p>背景：免审批提交时历史代码把「系统(免审批)」写进 approved_by，导致收款单明细报表「审核人」、
     * 采购入库/发票/退货单据信息等位置显示一个假审批人。改为写入时归一化后，存量数据也必须清掉，
     * 否则老单据仍显示系统占位。</p>
     *
     * <p>范围：当前库所有带 approved_by 列的表；只清两种免审批占位值，真实审批人一律不动。幂等。</p>
     */
    private void clearSystemPlaceholderApprover(JdbcTemplate target, String dbName) {
        try {
            List<Map<String, Object>> tables = target.queryForList(
                    "SELECT table_name FROM information_schema.columns"
                  + " WHERE table_schema = current_schema() AND column_name = 'approved_by'");
            int total = 0;
            for (Map<String, Object> row : tables) {
                String table = String.valueOf(row.get("table_name"));
                if (!table.matches("[A-Za-z0-9_]+")) continue;
                total += target.update("UPDATE " + table + " SET approved_by = NULL"
                        + " WHERE approved_by IN ('系统(免审批)', '系统(免审批自动通过)')");
            }
            if (total > 0) {
                log.info("[SchemaUpgradeEnsurer] 审核人系统占位清理 {}: {} 行（免审批单据审核人留空）", dbName, total);
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 审核人系统占位清理跳过 {}: {}", dbName, e.getMessage());
        }
    }

    /**
     * 【2026-10-31】销售订单超量发货比例列（百分比）：10=10%；0=不允许超量。
     * 主库 + 每个租户库幂等补列；存量订单默认 0。
     */
    private void ensureSaleOrderOvershipRatio(JdbcTemplate target, String dbName) {
        try {
            target.execute("ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS overship_ratio NUMERIC(10,4) NOT NULL DEFAULT 0");
            log.info("[SchemaUpgradeEnsurer] 销售订单超量发货比例列已就绪 {}", dbName);
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 销售订单超量发货比例列补齐跳过 {}: {}", dbName, e.getMessage());
        }
    }

    /**
     * 【2026-11-02】销售模块「超过来源单数量」三态策略：
     * 下拉固定 不控制 / 预警提示 / 不允许；历史值 警告提示→预警提示、不检测→不控制。
     * 主库 + 每个租户库幂等修正，管理员已选的合法值不覆盖。
     * 等价脚本：db/176_sale_over_qty_policy.sql = deploy/sql/176_sale_over_qty_policy.sql。
     */
    private void ensureSaleOverQtyPolicySettings(JdbcTemplate target, String dbName) {
        final String options = "[{\"label\":\"不控制\",\"value\":\"不控制\"},"
                + "{\"label\":\"预警提示\",\"value\":\"预警提示\"},"
                + "{\"label\":\"不允许\",\"value\":\"不允许\"}]";
        final String remark = "不控制=不校验；预警提示=超量允许保存并提示；不允许=超量阻断保存";
        final String[] keys = {"sale.order.over.qty", "sale.outbound.over.qty", "sale.return.over.qty"};
        final String[] names = {"超过来源单数量下单", "超过来源单数量出库", "超过来源单数量退货"};
        final String[] sections = {"销售订单", "销售出库", "销售退货"};
        final int[] sortOrders = {140, 200, 280};
        try {
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                Integer cnt = target.queryForObject(
                        "SELECT COUNT(*) FROM sys_config WHERE config_key = ?", Integer.class, key);
                if (cnt == null || cnt == 0) {
                    target.update("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                                    + " config_section, config_type, config_options, sort_order, remark,"
                                    + " created_at, updated_at, created_by, updated_by)"
                                    + " VALUES (?, '预警提示', ?, 'sale', ?, 'select', ?, ?, ?, NOW(), NOW(), 'system', 'system')",
                            key, names[i], sections[i], options, sortOrders[i], remark);
                    continue;
                }
                target.update("UPDATE sys_config SET config_type = 'select', config_options = ?, remark = ?,"
                                + " updated_at = NOW(), updated_by = 'system' WHERE config_key = ?",
                        options, remark, key);
            }
            target.update("UPDATE sys_config SET config_value = '预警提示', updated_at = NOW(), updated_by = 'system'"
                    + " WHERE config_key IN ('sale.order.over.qty','sale.outbound.over.qty','sale.return.over.qty')"
                    + " AND config_value = '警告提示'");
            target.update("UPDATE sys_config SET config_value = '不控制', updated_at = NOW(), updated_by = 'system'"
                    + " WHERE config_key IN ('sale.order.over.qty','sale.outbound.over.qty','sale.return.over.qty')"
                    + " AND config_value = '不检测'");
            log.info("[SchemaUpgradeEnsurer] 销售超过来源单数量三态策略已对齐 {}", dbName);
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 销售超过来源单数量三态策略对齐跳过 {}: {}", dbName, e.getMessage());
        }
    }

    /**
     * 【2026-11-08 新增 · 幂等】设置中心「自动下推设置」两个开关的种子与自愈。
     *
     * <p>等价脚本：{@code deploy/sql/189_sale_auto_push.sql} = {@code db/189_sale_auto_push.sql}。
     * 主库与逐租户库同口径：缺则补 false，已有值一律不动（默认关闭，升级前后行为一致）。</p>
     */
    private void ensureSaleAutoPushSettings(JdbcTemplate target, String dbName) {
        final String[][] specs = {
                {"sale.auto.push.contract.to.order", "销售合同自动生成销售订单", "70",
                        "开启后，销售合同审核生效时自动生成一份状态为已生效的销售订单，并记录来源单号；自动生成的单据不会再次触发反向生成。"},
                {"sale.auto.push.order.to.contract", "销售订单自动生成销售合同", "71",
                        "开启后，销售订单审核生效时自动生成一份状态为已生效的销售合同，并记录来源单号；自动生成的单据不会再次触发反向生成。"}
        };
        try {
            for (String[] spec : specs) {
                Integer cnt = target.queryForObject(
                        "SELECT COUNT(*) FROM sys_config WHERE config_key = ?", Integer.class, spec[0]);
                if (cnt == null || cnt == 0) {
                    target.update("INSERT INTO sys_config (config_key, config_value, config_name, config_group,"
                                    + " config_section, config_type, config_options, sort_order, remark,"
                                    + " created_at, updated_at, created_by, updated_by)"
                                    + " VALUES (?, 'false', ?, 'sale', '自动下推单据', 'boolean', NULL, ?, ?,"
                                    + " NOW(), NOW(), 'system', 'system')",
                            spec[0], spec[1], Integer.parseInt(spec[2]), spec[3]);
                }
            }
            log.info("[SchemaUpgradeEnsurer] 自动下推单据开关已对齐 {}", dbName);
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 自动下推单据开关对齐跳过 {}: {}", dbName, e.getMessage());
        }
        ensureAutoPushSettingsMenu(target, dbName);
    }

    /**
     * 【2026-11-09 新增 · 幂等】单据统一状态机接入：为 10 张单据补齐基类必需列。
     *
     * <p>等价脚本：{@code deploy/sql/190_bill_status_unify.sql} = {@code db/190_bill_status_unify.sql}。
     * 全部 ADD COLUMN IF NOT EXISTS：只加列、不删列、不改类型、不动存量数据；
     * 主库 + 逐租户库 + 租户模板库同口径，重复启动 0 副作用。</p>
     */
    private void ensureBillStatusColumns(JdbcTemplate target, String dbName) {
        // 表名 + 需要补齐的列定义（列名 / 类型）
        final Object[][] specs = {
                {"operation_expense", new String[][]{{"bill_before_status", "INTEGER"}}},
                {"purchase_expense", new String[][]{
                        {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"sale_expense", new String[][]{
                        {"approval_status", "VARCHAR(20)"}, {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"purchase_pre_payment", new String[][]{
                        {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"sale_pre_receipt", new String[][]{
                        {"approval_status", "VARCHAR(20)"}, {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"cost_calculation", new String[][]{
                        {"approval_status", "VARCHAR(20)"}, {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"wage_settlement", new String[][]{
                        {"bill_before_status", "INTEGER"}, {"close_status", "INTEGER DEFAULT 0"},
                        {"closed_by", "VARCHAR(64)"}, {"closed_at", "TIMESTAMP"}, {"closed_reason", "VARCHAR(255)"}}},
                {"bom", new String[][]{
                        {"close_status", "INTEGER DEFAULT 0"}, {"closed_by", "VARCHAR(64)"},
                        {"closed_at", "TIMESTAMP"}, {"closed_reason", "VARCHAR(255)"},
                        {"bill_before_status", "INTEGER"}, {"voided_by", "VARCHAR(64)"},
                        {"voided_at", "TIMESTAMP"}, {"void_reason", "VARCHAR(255)"}}},
                {"sale_price_strategy", new String[][]{
                        {"approval_status", "VARCHAR(20)"}, {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"},
                        {"close_status", "INTEGER DEFAULT 0"}, {"closed_by", "VARCHAR(64)"},
                        {"closed_at", "TIMESTAMP"}, {"closed_reason", "VARCHAR(255)"},
                        {"bill_before_status", "INTEGER"}}},
                {"purchase_price_strategy", new String[][]{
                        {"approval_status", "VARCHAR(20)"}, {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"},
                        {"close_status", "INTEGER DEFAULT 0"}, {"closed_by", "VARCHAR(64)"},
                        {"closed_at", "TIMESTAMP"}, {"closed_reason", "VARCHAR(255)"},
                        {"bill_before_status", "INTEGER"}}},
                // 2026-11-09 补充：打样、组装拆卸、委外 5 张缺「审核人 / 审核时间」
                {"sample_order", new String[][]{
                        {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"free_sample_order", new String[][]{
                        {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"produce_assemble", new String[][]{
                        {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"produce_disassemble", new String[][]{
                        {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"subcontract_order", new String[][]{
                        {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"subcontract_pick", new String[][]{
                        {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"subcontract_inbound", new String[][]{
                        {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"subcontract_return", new String[][]{
                        {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}},
                {"subcontract_supplement", new String[][]{
                        {"approved_by", "VARCHAR(64)"}, {"approved_at", "TIMESTAMP"}}}
        };
        int added = 0;
        for (Object[] spec : specs) {
            String table = (String) spec[0];
            String[][] columns = (String[][]) spec[1];
            try {
                for (String[] col : columns) {
                    target.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + col[0] + " " + col[1]);
                }
                added++;
            } catch (Exception e) {
                // 表不存在等情况只告警，绝不拉挂启动
                log.warn("[SchemaUpgradeEnsurer] 单据状态机补列跳过 {}.{}: {}", dbName, table, e.getMessage());
            }
        }
        log.info("[SchemaUpgradeEnsurer] 单据统一状态机补列已对齐 {}（{} 张单据表）", dbName, added);
    }

    /** 设置中心「自动下推设置」菜单 + 管理员授权（幂等；主库与逐租户库同口径） */
    private void ensureAutoPushSettingsMenu(JdbcTemplate target, String dbName) {
        final String routePath = "/system/settings?item=auto-push";
        try {
            java.util.List<Long> parents = target.queryForList(
                    "SELECT menu_id FROM sys_menu WHERE menu_code = 'system_settings_center'"
                            + " OR route_path = '/system/settings' ORDER BY menu_id LIMIT 1", Long.class);
            if (parents.isEmpty()) {
                log.warn("[SchemaUpgradeEnsurer] 未找到设置中心父级菜单，跳过自动下推设置菜单初始化 {}", dbName);
                return;
            }
            Integer cnt = target.queryForObject(
                    "SELECT COUNT(*) FROM sys_menu WHERE menu_code = 'system_auto_push_settings'", Integer.class);
            if (cnt == null || cnt == 0) {
                target.update("INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, route_path,"
                                + " permission_code, component_path, sort_order, icon, is_visible, is_enabled,"
                                + " is_cache, keepalive, status)"
                                + " VALUES ('system_auto_push_settings', '自动下推设置', ?, 'MENU', ?, NULL, NULL,"
                                + " 24, 'Connection', 1, 1, 0, 1, '启用')",
                        parents.get(0), routePath);
                log.info("[SchemaUpgradeEnsurer] 菜单「自动下推设置」已创建 {}", dbName);
            }
            for (String roleCode : new String[]{"ADMIN", "SUPER_ADMIN"}) {
                java.util.List<Long> roleIds = target.queryForList(
                        "SELECT role_id FROM sys_role WHERE role_code = ?", Long.class, roleCode);
                for (Long roleId : roleIds) {
                    Integer exists = target.queryForObject(
                            "SELECT COUNT(*) FROM sys_role_permission WHERE role_id = ? AND permission_type = '菜单'"
                                    + " AND menu_id = ?", Integer.class, roleId, routePath);
                    if (exists == null || exists == 0) {
                        target.update("INSERT INTO sys_role_permission (role_id, menu_id, permission_type, created_at)"
                                + " VALUES (?, ?, '菜单', now())", roleId, routePath);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 自动下推设置菜单初始化跳过 {}: {}", dbName, e.getMessage());
        }
    }

    /**
     * 【2026-11-05 新增 · 幂等】SPC质量看板 + 卡片式自定义看板：建表 + 菜单 + 角色授权。
     *
     * <p>等价脚本：{@code deploy/sql/180_spc_custom_board.sql} = {@code db/180_spc_custom_board.sql}。
     * 主库与逐租户库同口径（各自库内都有自己的 sys_menu / sys_role_permission）。</p>
     *
     * <p>规则：建表 IF NOT EXISTS；菜单按 menu_code NOT EXISTS；授权按 (role_id, route_path) NOT EXISTS。
     * 重复启动 0 副作用；任何一步失败只告警，绝不阻断启动。</p>
     */
    private void ensureSpcCustomBoardOn(JdbcTemplate target, String dbName) {
        try {
            // 1) 新表（自定义看板 / 工作台自定义卡）
            target.execute("CREATE TABLE IF NOT EXISTS custom_board ("
                    + " board_id BIGSERIAL PRIMARY KEY,"
                    + " board_type VARCHAR(20) NOT NULL DEFAULT 'board',"
                    + " board_code VARCHAR(40),"
                    + " board_name VARCHAR(120),"
                    + " layout_json TEXT,"
                    + " status VARCHAR(20) NOT NULL DEFAULT '启用',"
                    + " share_scope VARCHAR(20) NOT NULL DEFAULT '私有',"
                    + " share_roles VARCHAR(500),"
                    + " created_by VARCHAR(80),"
                    + " created_at TIMESTAMP DEFAULT NOW(),"
                    + " updated_at TIMESTAMP DEFAULT NOW(),"
                    + " remark VARCHAR(500))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_custom_board_type ON custom_board (board_type)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_custom_board_owner ON custom_board (created_by)");
            // 2026-11-06：现有看板页（财务/销售/仓储/经营）的卡片配置也存这张表，需要 page_key
            try {
                target.execute("ALTER TABLE custom_board ADD COLUMN IF NOT EXISTS page_key VARCHAR(40)");
            } catch (Exception ignore) {
                // 列已存在或旧库不支持 IF NOT EXISTS：忽略，后续查询按空处理
            }

            // 2) 菜单（父级 = 数据看板；现有四张看板 sort_order 1~4，新页接 5/6）
            //    先对齐序列：部分租户库历史上显式 ID 插过菜单，序列落后 → 下面 INSERT 会撞 sys_menu_pkey
            //    （2026-11-06 本地实测：erp_tenant_neibu002 / neibu003 各报一次唯一约束冲突）
            alignMenuSequenceOn(target);
            Long boardParent = null;
            try {
                boardParent = target.queryForObject(
                        "SELECT parent_menu_id FROM sys_menu WHERE route_path = '/databoard/sale'"
                                + " AND parent_menu_id IS NOT NULL LIMIT 1", Long.class);
            } catch (Exception ignore) {
                // 取不到父级时下面 ensureMenu 会写入 null，由后半段兜底 UPDATE 修复
            }
            ensureMenuOn(target, "databoard_spc", "SPC质量看板", boardParent, "/databoard/spc",
                    "databoard:spc", 5, "DataAnalysis", "ULTIMATE");
            // 2026-11-06 口径修正（用户原话：不要新增看板，改现有看板）：自定义能力并入现有看板，
            // 「自定义看板」独立菜单下线；幂等删除其授权与菜单行，不影响其它菜单。
            try {
                target.update("DELETE FROM sys_role_permission WHERE menu_id = '/databoard/custom'");
                target.update("DELETE FROM sys_menu WHERE menu_code = 'databoard_custom'");
            } catch (Exception ignore) {
                // 旧库结构差异时不阻断启动
            }
            target.update("UPDATE sys_menu SET parent_menu_id = ("
                    + " SELECT parent_menu_id FROM sys_menu WHERE route_path = '/databoard/sale'"
                    + " AND parent_menu_id IS NOT NULL LIMIT 1)"
                    + " WHERE menu_code IN ('databoard_spc','databoard_custom') AND parent_menu_id IS NULL");

            // 3) 角色授权
            grantMenuOn(target, "/databoard/spc",
                    new String[]{"ADMIN", "SUPER_ADMIN", "PRODUCTION_MANAGER", "PRODUCTION"});

            log.info("[SchemaUpgradeEnsurer] SPC质量看板 / 自定义看板 表与菜单就绪（{}）", dbName);
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] SPC质量看板 / 自定义看板初始化跳过 {}: {}", dbName, e.getMessage());
        }
    }

    /** 单库对齐 sys_menu 主键序列（与 deploy/sql/180 的 setval 同口径；失败不阻断） */
    private void alignMenuSequenceOn(JdbcTemplate target) {
        try {
            target.queryForObject(
                    "SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1))",
                    Long.class);
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] sys_menu 序列对齐跳过: {}", e.getMessage());
        }
    }

    /** 单库幂等插菜单（不接受 null 主键场景：父级为空时也允许插入，随后由兜底 UPDATE 修正） */
    private void ensureMenuOn(JdbcTemplate target, String menuCode, String menuName, Long parentId,
                              String routePath, String permissionCode, int sortOrder,
                              String icon, String version) {
        Integer c = target.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE menu_code = ?", Integer.class, menuCode);
        if (c != null && c > 0) return;
        target.update("INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, route_path,"
                        + " permission_code, component_path, sort_order, icon, is_visible, is_enabled,"
                        + " is_cache, keepalive, status, version)"
                        + " VALUES (?, ?, ?, 'MENU', ?, ?, NULL, ?, ?, 1, 1, 0, 1, '启用', ?)",
                menuCode, menuName, parentId, routePath, permissionCode, sortOrder, icon, version);
    }

    /** 单库幂等授权（sys_role_permission.menu_id 存 route_path） */
    private void grantMenuOn(JdbcTemplate target, String routePath, String[] roleCodes) {
        if (roleCodes == null || roleCodes.length == 0) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(roleCodes.length, "?"));
        String sql = "INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited)"
                + " SELECT DISTINCT r.role_id, ?, '菜单', 1, 0 FROM sys_role r"
                + " WHERE r.role_code IN (" + placeholders + ")"
                + " AND NOT EXISTS (SELECT 1 FROM sys_role_permission p"
                + "  WHERE p.role_id = r.role_id AND p.menu_id = ? AND p.permission_type = '菜单')";
        Object[] args = new Object[roleCodes.length + 2];
        args[0] = routePath;
        System.arraycopy(roleCodes, 0, args, 1, roleCodes.length);
        args[args.length - 1] = routePath;
        try {
            target.update(sql, args);
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 菜单授权跳过 {}: {}", routePath, e.getMessage());
        }
    }

    /**
     * 【2026-10-14】汇总收款行级 FIFO 回填（收款方式切换数据差根治）。
     *
     * <p>背景：收款方式（汇总/明细）是全局设置，10-14 之前确认的汇总收款只累加订单表头已收，
     * 明细行"已收金额"为 0——切换到明细方式后行级不自动长出来（金蝶/用友口径：核销记录落库后不重算历史）。
     *
     * <p>口径（与 FinanceServiceImpl.allocateOrderItemReceived 兜底分支同一把尺子）：
     * 纯汇总已确认收款（核销明细不指向行、无应收核销回写）按「行号顺序 FIFO」分摊回填行级已收，
     * 每行封顶本行价税合计；混合场景（既有按行收又有汇总收）不自动覆盖，留给人工核对。
     * 回填后按当前「销售订单状态设置」重算完结（与 SaleOrderServiceImpl.recalcOrderCloseStatus 同口径，两处必须同步）。
     * 幂等：行级合计 == 应摊总额的订单跳过。
     */
    private void ensureReceiptSummaryFifoAllocated(JdbcTemplate target, String dbName) {
        try {
            // 1) 找"纯汇总"待回填订单：有已确认汇总核销（不指向行）、无按行核销、无应收核销回写（发货收款回写已由运行时摊过行级）
            java.util.List<java.util.Map<String, Object>> orders = target.queryForList(
                "SELECT w.source_id AS oid, SUM(w.writeoff_amount) AS total"
                + " FROM bill_writeoff_detail w"
                + " JOIN cash_receipt r ON r.receipt_id = w.bill_id"
                + " WHERE w.bill_type = '收款单' AND w.source_type = '销售订单'"
                + "   AND w.source_item_id IS NULL AND w.settled_qty = '已冲销'"
                + "   AND r.status = '已确认'"
                + "   AND NOT EXISTS (SELECT 1 FROM bill_writeoff_detail d2"
                + "       WHERE d2.bill_type='收款单' AND d2.source_type='销售订单' AND d2.source_id = w.source_id"
                + "         AND d2.source_item_id IS NOT NULL AND d2.settled_qty='已冲销')"
                + "   AND NOT EXISTS (SELECT 1 FROM account_receivable ar"
                + "       JOIN sale_order so ON so.order_id = w.source_id"
                + "       WHERE ar.source_order_no = so.order_no AND COALESCE(ar.paid_amount,0) > 0)"
                + " GROUP BY w.source_id");
            if (orders.isEmpty()) return;
            String mode = "销售发货单";
            try {
                String v = target.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key = 'sale.order.auto.close.mode'", String.class);
                if (v != null && !v.isBlank()) mode = v.trim();
            } catch (Exception ignore) { /* 缺键兜底=销售发货单 */ }
            boolean checkInvoice = "销售发票".equals(mode) || "销售发货+销售发票+收款单".equals(mode);
            boolean checkShipment = !"销售发票".equals(mode);
            boolean checkReceipt = "销售发货+销售发票+收款单".equals(mode);
            int rowsFixed = 0, ordersTouched = 0;
            for (java.util.Map<String, Object> o : orders) {
                Long oid = ((Number) o.get("oid")).longValue();
                java.math.BigDecimal total = toDec(o.get("total"));
                java.util.List<java.util.Map<String, Object>> items = target.queryForList(
                    "SELECT order_item_id, COALESCE(net_amount,0) AS net, COALESCE(received_amount,0) AS rec,"
                    + " COALESCE(quantity,0) AS qty, COALESCE(turned_output_quantity,0) AS outq,"
                    + " COALESCE(turned_return_quantity,0) AS retq, COALESCE(turned_invoice_amount,0) AS invamt,"
                    + " COALESCE(detail_close_status,0) AS dcs"
                    + " FROM sale_order_item WHERE order_id = ? ORDER BY line_no", oid);
                java.math.BigDecimal recSum = items.stream()
                    .map(it -> toDec(it.get("rec"))).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                if (recSum.compareTo(total) == 0) continue; // 已回填过（幂等）
                // FIFO 分摊：行号顺序先收满、封顶本行净额
                java.util.Map<Long, java.math.BigDecimal> alloc = new java.util.LinkedHashMap<>();
                java.math.BigDecimal cursor = total;
                for (java.util.Map<String, Object> it : items) {
                    if (cursor.compareTo(java.math.BigDecimal.ZERO) <= 0) break;
                    java.math.BigDecimal net = toDec(it.get("net"));
                    java.math.BigDecimal room = net.max(java.math.BigDecimal.ZERO);
                    if (room.compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
                    java.math.BigDecimal take = cursor.min(room);
                    alloc.put(((Number) it.get("order_item_id")).longValue(), take);
                    cursor = cursor.subtract(take);
                }
                if (cursor.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    log.warn("[SchemaUpgradeEnsurer] 汇总收款回填 {} 订单 {} 行净额合计不足以容纳核销总额 {}，按可容纳部分回填",
                        dbName, oid, cursor);
                }
                // 2) 回填行级 + 按模式重算明细结案标志
                boolean allClosed = !items.isEmpty();
                for (java.util.Map<String, Object> it : items) {
                    Long itemId = ((Number) it.get("order_item_id")).longValue();
                    java.math.BigDecimal net = toDec(it.get("net"));
                    java.math.BigDecimal newRec = alloc.getOrDefault(itemId, java.math.BigDecimal.ZERO);
                    boolean done = true;
                    if (checkShipment) {
                        java.math.BigDecimal netShipped = toDec(it.get("outq")).subtract(toDec(it.get("retq")));
                        done = done && toDec(it.get("qty")).signum() > 0 && netShipped.compareTo(toDec(it.get("qty"))) >= 0;
                    }
                    if (checkInvoice) done = done && net.signum() > 0 && toDec(it.get("invamt")).compareTo(net) >= 0;
                    if (checkReceipt) done = done && net.signum() > 0 && newRec.compareTo(net) >= 0;
                    int newDcs = done ? 1 : 0;
                    if (!done) allClosed = false;
                    if (toDec(it.get("rec")).compareTo(newRec) != 0 || toDec(it.get("dcs")).intValue() != newDcs) {
                        target.update("UPDATE sale_order_item SET received_amount = ?, detail_close_status = ? WHERE order_item_id = ?",
                            newRec, newDcs, itemId);
                        rowsFixed++;
                    }
                }
                // 3) 订单级完结重算（手动结案不覆盖）
                Integer manual = target.queryForObject(
                    "SELECT CASE WHEN COALESCE(closed_reason,'') = '手动结案' THEN 1 ELSE 0 END FROM sale_order WHERE order_id = ?",
                    Integer.class, oid);
                if (manual != null && manual == 1) continue;
                int newClose = allClosed ? 1 : 0;
                target.update("UPDATE sale_order SET close_status = ? WHERE order_id = ?", newClose, oid);
                ordersTouched++;
            }
            if (rowsFixed > 0 || ordersTouched > 0) {
                log.info("[SchemaUpgradeEnsurer] 汇总收款行级 FIFO 回填 {}: {} 行 / {} 单（模式={}）",
                    dbName, rowsFixed, ordersTouched, mode);
            }
        } catch (Exception e) {
            log.warn("[SchemaUpgradeEnsurer] 汇总收款行级回填跳过 {}: {}", dbName, e.getMessage());
        }
    }

    private static java.math.BigDecimal toDec(Object v) {
        if (v == null) return java.math.BigDecimal.ZERO;
        if (v instanceof java.math.BigDecimal) return (java.math.BigDecimal) v;
        return new java.math.BigDecimal(String.valueOf(v));
    }

    /**
     * sys_config.config_value 放宽为 TEXT（2026-10-10 小楷能力整合验证中暴露）。
     *
     * <p>病根：AI 自定义服务商整体以 JSON 数组存配置表单值列（密钥为加密串，比原文更长），
     * 列宽 255 时保存服务商必然「值太长了」——本地库从未存过服务商所以一直没暴露。
     * varchar(255)→TEXT 向上兼容，存量数据无损；重复执行无副作用（幂等）。
     *
     * <p>覆盖：主库 + 每个租户独立库（同 2026-09-20「三库齐」口径）。
     */
    private void ensureSysConfigValueText(JdbcTemplate target) {
        try {
            target.execute("ALTER TABLE sys_config ALTER COLUMN config_value TYPE TEXT");
        } catch (Exception e) {
            log.debug("[SchemaUpgradeEnsurer] sys_config.config_value 放宽跳过（当前库可能无 sys_config）: {}", e.getMessage());
        }
    }

    /** 指定目标库补列；返回 true 表示本次真的新增了列。 */
    private boolean ensureColumnIfMissing(JdbcTemplate target, String table, String column, String type) {
        try {
            Integer c = target.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                " WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class, table, column
            );
            if (c != null && c == 0) {
                target.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                log.info("[SchemaUpgradeEnsurer] {}.{} added ({}).", table, column, type);
                return true;
            }
        } catch (Exception e) {
            // 典型：当前 DB 根本没有这张表（例如 erp_platform 没有 sys_menu），忽略即可
            log.debug("[SchemaUpgradeEnsurer] {}.{} skip: {}", table, column, e.getMessage());
        }
        return false;
    }

    /**
     * 价格策略改造（2026-10-10）：放开明细 material_id / 主表 strategy_name 非空、存量回填 target_type/target_id、
     * 优先级按创建时间重排为 1..N（仅当存在重复时）、建优先级唯一索引与明细对象索引。
     *
     * <p>全部幂等：重复执行结果一致；唯一索引已存在时必然无重复 → 不会覆盖用户手工设的优先级。
     * 单条失败只记日志、绝不阻断启动；主库与每个租户库共用同一份。
     */
    private void ensurePriceStrategyTargetsOn(JdbcTemplate target) {
        String[] stmts = new String[]{
                "ALTER TABLE sale_price_strategy_item ALTER COLUMN material_id DROP NOT NULL",
                "ALTER TABLE purchase_price_strategy_item ALTER COLUMN material_id DROP NOT NULL",
                "ALTER TABLE sale_price_strategy ALTER COLUMN strategy_name DROP NOT NULL",
                "ALTER TABLE purchase_price_strategy ALTER COLUMN strategy_name DROP NOT NULL",
                "UPDATE sale_price_strategy_item SET target_type = 'material', target_id = material_id WHERE target_type IS NULL AND material_id IS NOT NULL",
                "UPDATE purchase_price_strategy_item SET target_type = 'material', target_id = material_id WHERE target_type IS NULL AND material_id IS NOT NULL",
                "UPDATE sale_price_strategy SET target_type = 'material' WHERE target_type IS NULL",
                "UPDATE purchase_price_strategy SET target_type = 'material' WHERE target_type IS NULL"
        };
        for (String sql : stmts) {
            try {
                target.execute(sql);
            } catch (Exception e) {
                log.debug("[SchemaUpgradeEnsurer] 价格策略改造语句跳过: {}", e.getMessage());
            }
        }

        rerankStrategyPriority(target, "sale_price_strategy");
        rerankStrategyPriority(target, "purchase_price_strategy");

        String[] idx = new String[]{
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_sale_strategy_priority ON sale_price_strategy(priority)",
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_purchase_strategy_priority ON purchase_price_strategy(priority)",
                "CREATE INDEX IF NOT EXISTS idx_sale_item_target ON sale_price_strategy_item(strategy_id, target_type, target_id)",
                "CREATE INDEX IF NOT EXISTS idx_purchase_item_target ON purchase_price_strategy_item(strategy_id, target_type, target_id)"
        };
        for (String sql : idx) {
            try {
                target.execute(sql);
            } catch (Exception e) {
                log.warn("[SchemaUpgradeEnsurer] 价格策略索引创建跳过: {}", e.getMessage());
            }
        }
    }

    /** 优先级重排为 1..N（按创建时间）；仅当同一优先级出现重复时才执行，避免每次启动覆盖用户手工值。 */
    private void rerankStrategyPriority(JdbcTemplate target, String table) {
        try {
            Integer dup = target.queryForObject(
                    "SELECT COUNT(*) FROM (SELECT priority FROM " + table + " GROUP BY priority HAVING COUNT(*) > 1) t",
                    Integer.class);
            if (dup != null && dup > 0) {
                target.execute("WITH ranked AS (SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn FROM " + table
                        + ") UPDATE " + table + " s SET priority = r.rn FROM ranked r WHERE s.id = r.id");
                log.info("[SchemaUpgradeEnsurer] {} 优先级已按创建时间重排为 1..N（原有重复 {} 组）", table, dup);
            }
        } catch (Exception e) {
            log.debug("[SchemaUpgradeEnsurer] {} 优先级重排跳过: {}", table, e.getMessage());
        }
    }
}
