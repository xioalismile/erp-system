# ERP 系统完整知识库

> 最后更新：2026-08-21
> 本文档记录完整的系统模块结构、API路由、设置中心配置、已发现的Bug及解决方案。

***

## 一、系统模块全景图

### 后端模块结构

| 模块   | 包路径           | 说明               | API前缀                |
| ---- | ------------- | ---------------- | -------------------- |
| 基础数据 | `basedata`    | 客户/供应商/物料/仓库/员工等 | `/api/basedata/*`    |
| 主数据  | `masterdata`  | 物料/BOM/分类        | `/api/masterdata/*`  |
| 销售管理 | `sale`        | 销售订单/报价/合同/出库/退货 | `/api/sale/*`        |
| 采购管理 | `purchase`    | 采购申请/订单/入库/发票/退货 | `/api/purchase/*`    |
| 库存管理 | `inventory`   | 即时库存/调拨/盘点/其他出入库 | `/api/inventory/*`   |
| 生产管理 | `production`  | 工单/工艺路线/MRP/报工   | `/api/production/*`  |
| 委外管理 | `subcontract` | 委外订单/入库/发料/退货    | `/api/subcontract/*` |
| 财务管理 | `finance`     | 凭证/收付款/费用/信用/成本  | `/api/finance/*`     |
| CRM  | `crm`         | 线索/商机/联系人/跟进/投诉  | `/api/crm/*`         |
| 审批流  | `approval`    | 审批流程/任务/日志       | `/api/approval/*`    |
| 报表中心 | `report`      | 标准报表/自定义报表       | `/api/report/*`      |
| AI助手 | `ai`          | AI对话/工具调用/语音     | `/api/ai/*`          |
| 系统管理 | `system`      | 用户/角色/菜单/权限/日志   | `/api/system/*`      |
| 获客管理 | `acquisition` | 获客任务/结果          | `/api/acquisition/*` |
| 仪表盘  | `dashboard`   | 首页概览/待办/审批       | `/api/dashboard/*`   |
| 导出导入 | `export`      | Excel导入导出服务      | `/api/export/*`      |
| 许可证  | `license`     | License生成/验证/心跳  | `/api/license/*`     |

***

## 二、设置中心完整功能清单

### 2.1 设置中心4大分组

| 分组  | 说明                    | 子功能数量 |
| --- | --------------------- | ----- |
| 系统  | 基础信息、系统参数、角色权限        | 5项    |
| 业务  | 库存预警、CRM预警、价格策略、编码规则  | 7项    |
| 自定义 | 菜单名称、按钮显隐、主题、打印、审批、编号 | 9项    |
| 日志  | 操作日志                  | 1项    |

### 2.2 系统分组功能

| 功能       | 组件                 | 说明          |
| -------- | ------------------ | ----------- |
| 基础信息设置   | SystemBasicInfo    | 企业名称、地址、电话等 |
| 系统参数设置   | KgwSysParam        | 约80+系统参数配置  |
| 角色权限设置   | RoleList           | 角色管理+权限分配   |
| 员工权限补充   | EmployeePermission | 员工额外权限      |
| AI数据使用政策 | AiDataPolicy       | AI数据使用范围    |

### 2.3 业务分组功能

| 功能      | 组件                        | 说明          |
| ------- | ------------------------- | ----------- |
| 库存预警设置  | KgwInventoryWarning       | 安全库存、预警阈值   |
| CRM预警设置 | KgwCrmWarning             | 客户流失、商机跟进预警 |
| 销售价格策略  | SalePriceStrategyList     | 销售定价规则      |
| 采购价格策略  | PurchasePriceStrategyList | 采购取价规则      |
| 提成率配置   | KgwCommissionRate         | 销售提成计算      |
| 物料编码规则  | MaterialCodeRuleList      | 物料编号规则      |
| 物流配置    | KgwLogisticsConfig        | 快递100等物流接口  |

### 2.4 自定义分组功能

| 功能      | 组件                | 说明        |
| ------- | ----------------- | --------- |
| 菜单名称设置  | KgwMenuName       | 自定义菜单显示名  |
| 按钮显隐设置  | KgwButtonConfig   | 控制各页面按钮显示 |
| 主题设置    | ThemeSetting      | 界面主题颜色    |
| 消息通知设置  | KgwNotification   | 系统通知配置    |
| 自动编号设置  | SerialNoConfig    | 单据自动编号规则  |
| 审批设置    | KgwApproval       | 审批流程+单据开关 |
| 批次编号设置  | KgwBatchNo        | 批次号生成规则   |
| 打印设置    | PrintTemplateList | 打印模板管理    |
| 自定义字段设置 | CustomFieldConfig | 扩展字段配置    |

***

## 三、系统参数完整清单

### 3.1 基础设置（系统参数）

| Config Key                     | 说明            | 类型          | 默认值       |
| ------------------------------ | ------------- | ----------- | --------- |
| `sys.ip.limit.enabled`         | 启用IP限制        | switch      | false     |
| `sys.ip.limit.segment`         | IP段           | input       | -         |
| `sys.tax.enabled`              | 启用含税业务        | switch+sub  | true      |
| `sys.tax.rate`                 | 税率            | number      | 13        |
| `sys.tax.method`               | 默认计税方式        | select      | 不含税价      |
| `sys.foreign.currency.enabled` | 启用外币业务        | switch      | false     |
| `sys.base.currency`            | 本位币           | select      | 人民币       |
| `sys.print.when.effective`     | 单据在生效状态下打印    | switch      | false     |
| `sys.customer.duplicate.check` | 客户名称查重        | switch      | **true**  |
| `sys.material.duplicate.check` | 货品名称查重        | switch      | **true**  |
| `sys.supplier.duplicate.check` | 供应商名称查重       | switch      | **true**  |
| `sys.auto.customer.no`         | ~~自动生成客户编号~~（2026-09-23 已下线：设置中心不再显示、代码不再消费，改由「自动编号设置 → 客户档案」规则管理） | —           | —         |
| `sys.auto.supplier.no`         | ~~自动生成供应商编号~~（2026-09-23 已下线：设置中心不再显示、代码不再消费，改由「自动编号设置 → 供应商档案」规则管理） | —           | —         |
| `sys.float.unit.enabled`       | 启用浮动单位        | switch      | false     |
| `sys.lock.bill.date`           | 不允许修改单据日期     | switch      | false     |
| `sys.discount.calc.method`     | 折扣额推算方式       | select      | 以折扣额推算折扣率 |
| `sys.loss.calc.method`         | 损耗量推算方式       | select      | 以损耗率推算损耗量 |
| `sys.unified.column.enabled`   | 统一列设置         | switch      | false     |
| `sys.unified.column.employee`  | 指定员工统一列设置     | multiSelect | \[]       |
| `sys.show.spec.in.dropdown`    | 货品选择下拉列表中显示规格 | switch      | true      |
| `sys.spec.duplicate.check`     | 货品规格查重        | switch      | false     |
| `sys.bill.no.duplicate.check`  | 单据号码查重        | switch      | true      |
| `sys.phone.duplicate.check`    | 客商手机号查重       | switch      | false     |

### 3.2 小数位数设置

| Config Key            | 说明         | 类型     | 默认值   |
| --------------------- | ---------- | ------ | ----- |
| `precision.quantity`  | 数量类型小数点后位数 | number | 2     |
| `precision.amount`    | 金额类型小数点后位数 | number | 2     |
| `precision.trim.zero` | 省略小数点后多余的0 | switch | false |

### 3.3 采购参数

| Config Key                            | 说明                                                                | 默认值       |
| ------------------------------------- | ----------------------------------------------------------------- | --------- |
| `purchase.price.over.max`             | 进价高于最高进价处理                                                        | 警告提示      |
| `kgw_purchase_price_strategy`         | 采购业务取价方式                                                          | 最新采购入库价   |
| `purchase.price.manual.modify`        | 手工修改采购价                                                           | 允许        |
| `purchase.invoice.default`            | 默认需收票                                                             | 否         |
| `purchase.request.price.strategy`     | 采购申请单取价方式                                                         | 取货品资料中采购价 |
| `purchase.order.require.source`       | 采购订单有来源单号才能保存                                                     | false     |
| `purchase.order.allow.add.material`   | 允许增加来源单货品                                                         | true      |
| `purchase.order.skip.request.price`   | 不取采购申请单价                                                          | true      |
| `purchase.order.over.qty`             | 超过来源单数量下单                                                         | 警告提示      |
| `purchase.order.allow.zero.price`     | 允许单价为0                                                            | true      |
| `purchase.order.over.max.stock`       | 超过最大库存                                                            | 不检测       |
| `purchase.inbound.require.source`     | 入库需来源单号                                                           | false     |
| `purchase.inbound.allow.add.material` | 允许增加来源单货品                                                         | true      |
| `purchase.inbound.over.qty`           | 超过来源单数量入库                                                         | 警告提示      |
| `purchase.inbound.allow.zero.price`   | 允许单价为0                                                            | true      |
| `purchase.inbound.over.max.stock`     | 超过最大库存                                                            | 不允许       |
| `purchase.return.require.source`      | 退货需来源单号                                                           | false     |
| `purchase.return.over.qty`            | 超过来源单数量退货                                                         | 不检测       |
| `purchase.contract.require.source`    | 采购合同明细必须有来源单号才能保存（2026-09-15 新增；判定取明细行 `source_bill_no`，任一行非空即满足） | false     |

### 3.4 销售参数

| Config Key                         | 说明         | 默认值   |
| ---------------------------------- | ---------- | ----- |
| `sale.price.lowest.check`          | 售价低于最低售价检测 | true  |
| `kgw_sale_price_strategy`          | 销售业务取价方式   | 不启用   |
| `sale.price.manual.modify`         | 手工修改销售价    | 允许    |
| `sale.invoice.default`             | 默认需开票      | 是     |
| `sale.quote.price.strategy`        | 销售报价单取价方式  | 不启用   |
| `sale.order.require.source`        | 订单需来源单号    | false |
| `sale.order.allow.add.material`    | 允许增加来源单货品  | false |
| `sale.order.over.qty`              | 超过来源单数量下单  | 预警提示（可选：不控制/预警提示/不允许）  |
| `sale.order.allow.zero.price`      | 允许单价为0     | false |
| `sale.order.below.min.stock`       | 低于最小库存     | 警告提示  |
| `sale.outbound.require.source`     | 出库需来源单号    | false |
| `sale.outbound.allow.add.material` | 允许增加来源单货品  | false |
| `sale.outbound.over.qty`           | 超过来源单数量出库  | 预警提示（可选：不控制/预警提示/不允许）  |
| `sale.outbound.allow.zero.price`   | 允许单价为0     | false |
| `sale.outbound.below.min.stock`    | 低于最小库存     | 警告提示  |
| `sale.return.require.source`       | 退货需来源单号    | false |
| `sale.return.over.qty`             | 超过来源单数量退货  | 预警提示（可选：不控制/预警提示/不允许）  |

> 2026-11-02 三态管控口径：三个「超过来源单数量」参数统一为 不控制 / 预警提示 / 不允许。
> 不控制=完全不校验；预警提示=允许保存/提交并在页面弹出预警；不允许=超量阻断保存/提交。
> 销售订单按来源报价单/销售合同/打样单的剩余数量校验；销售出库按来源销售订单最高发货数量校验；
> 销售退货按来源出库单/销售订单/销售发票的剩余可退数量校验。
> 销售出库的「预警提示」在提交/审核前弹确认框，按钮为「取消 / 继续提交（审核）」；文案只显示
> 「销售出库单第 N 行物料[编码 名称] 超出最高发货数量：最高 XXX」，最高数量已含超量发货比例。

### 3.5 库存参数

| Config Key                         | 说明              | 默认值          |
| ---------------------------------- | --------------- | ------------ |
| `inv.sale.order.check.stock`       | 销售订单检测库存        | false        |
| `inventory.negative.allowed`       | 允许负库存出库         | false        |
| `inv.check.stock.when.approving`   | 审批中单据检测库存       | false        |
| `inv.check.stock.when.draft`       | 草稿单据检测库存        | false        |
| `inv.location.enabled`             | 启用储位管理          | false        |
| `inv.allow.expired.batch.outbound` | 允许过期批次货品出库      | false        |
| `inv.allow.batch.repeat.inbound`   | 允许批次货品重复入库      | false        |
| `inv.sync.default.bom.to.order`    | 同步默认BOM到无BOM的订单 | false        |
| `inv.sync.material.price.to.bom`   | 同步货品的价格到BOM     | false        |
| `inv.sync.material.no.to.bom`      | 同步货品编号到BOM单号    | false        |
| `inv.warning.reference`            | 库存预警参考值         | actual(实际库存) |
| `inv.stock.check.scope`            | 发货库存校验口径        | actual(实际库存) |

### 3.6 委外参数

| Config Key                               | 说明        | 默认值     |
| ---------------------------------------- | --------- | ------- |
| `subcontract.price.strategy`             | 委外业务取价方式  | 最新委外入库价 |
| `subcontract.price.manual.modify`        | 手工修改加工价   | 允许      |
| `subcontract.invoice.default`            | 默认需收票     | 否       |
| `subcontract.order.require.source`       | 委外订单需来源单号 | false   |
| `subcontract.order.allow.add.material`   | 允许增加来源单货品 | true    |
| `subcontract.order.over.qty`             | 超过来源单数量下单 | 警告提示    |
| `subcontract.order.allow.zero.price`     | 允许单价为0    | true    |
| `subcontract.issue.require.source`       | 发料需来源单号   | false   |
| `subcontract.issue.allow.add.material`   | 允许增加来源单货品 | true    |
| `subcontract.issue.over.qty`             | 超过来源单数量发料 | 警告提示    |
| `subcontract.inbound.require.source`     | 入库需来源单号   | false   |
| `subcontract.inbound.allow.add.material` | 允许增加来源单货品 | true    |
| `subcontract.inbound.over.qty`           | 超过来源单数量入库 | 警告提示    |
| `subcontract.inbound.allow.zero.price`   | 允许单价为0    | true    |
| `subcontract.return.require.source`      | 退料需来源单号   | false   |
| `subcontract.return.over.qty`            | 超过来源单数量退料 | 不检测     |

***

## 四、审批流支持的单据类型

### 4.1 全局审批开关

- **Config Key**: `approval.enabled`

- **说明**: 关闭后所有单据直接生效，不进入审批流程

### 4.2 支持审批的单据清单

| 模块        | 单据类型    | BillType               | 说明     |
| --------- | ------- | ---------------------- | ------ |
| **销售管理**  | 销售报价单   | SALE\_QUOTE            | <br /> |
| <br />    | 销售订单    | SALE\_ORDER            | <br /> |
| <br />    | 销售合同    | SALE\_CONTRACT         | <br /> |
| <br />    | 销售出库单   | SALE\_DELIVERY         | <br /> |
| <br />    | 销售退货单   | SALE\_RETURN           | <br /> |
| <br />    | 销售发票    | SALE\_INVOICE          | <br /> |
| **采购管理**  | 采购申请单   | PURCHASE\_REQUEST      | <br /> |
| <br />    | 采购订单    | PURCHASE\_ORDER        | <br /> |
| <br />    | 采购合同    | PURCHASE\_CONTRACT     | <br /> |
| <br />    | 采购入库单   | PURCHASE\_INBOUND      | <br /> |
| <br />    | 采购退货单   | PURCHASE\_RETURN       | <br /> |
| <br />    | 采购发票    | PURCHASE\_INVOICE      | <br /> |
| **生产管理**  | 生产订单    | PRODUCTION\_ORDER      | <br /> |
| <br />    | 生产入库单   | PRODUCTION\_RECEIPT    | <br /> |
| <br />    | 生产领料单   | MATERIAL\_REQUISITION  | <br /> |
| **委外管理**  | 委外订单    | SUBCONTRACT\_ORDER     | <br /> |
| <br />    | 委外发料单   | SUBCONTRACT\_DELIVERY  | <br /> |
| <br />    | 委外入库单   | SUBCONTRACT\_RECEIPT   | <br /> |
| **打样/样品** | 打样单     | SAMPLE\_ORDER          | <br /> |
| <br />    | 免费样品单   | FREE\_SAMPLE           | <br /> |
| **库存管理**  | 库存调拨单   | STOCK\_TRANSFER        | <br /> |
| <br />    | 库存盘点单   | STOCK\_CHECK           | <br /> |
| <br />    | 其他入库单   | OTHER\_RECEIPT         | <br /> |
| <br />    | 其他出库单   | OTHER\_ISSUE           | <br /> |
| **财务管理**  | 采购费用单   | PURCHASE\_EXPENSE      | <br /> |
| <br />    | 预付款单    | PURCHASE\_PRE\_PAYMENT | <br /> |
| **其他**    | BOM物料清单 | BOM                    | <br /> |

### 4.3 审批节点模式

| 模式       | 说明     |
| -------- | ------ |
| single   | 单人审批   |
| any      | 任选一人审批 |
| all      | 全部人审批  |
| sequence | 依次审批   |

### 4.4 AI助手权限配置

| Config Key                    | 说明       | 默认值       |
| ----------------------------- | -------- | --------- |
| `ai.tool.write.enabled`       | AI写操作总开关 | **false** |
| `ai.tool.image_order.enabled` | 图片下单     | false     |
| `ai.tool.bom_import.enabled`  | BOM文件导入  | false     |
| `ai.tool.image_bom.enabled`   | 图片识别BOM  | false     |
| `ai.tool.crud.enabled`        | 通用增删改查   | false     |

***

## 五、按钮配置体系

### 5.1 标准按钮定义

| Key    | 标签 | 图标       | 类型      | 权限字段       |
| ------ | -- | -------- | ------- | ---------- |
| query  | 查询 | Search   | primary | permQuery  |
| add    | 新增 | Plus     | primary | permAdd    |
| edit   | 修改 | Edit     | warning | permEdit   |
| delete | 删除 | Delete   | danger  | permDelete |
| import | 导入 | Upload   | success | permImport |
| export | 导出 | Download | info    | permExport |
| print  | 打印 | Printer  | info    | permPrint  |

### 5.2 按钮配置存储

- **存储位置**: localStorage (`buttonConfig`)

- **配置格式**: `{ [pageKey]: { query: true, add: false, ... } }`

- **管理入口**: 设置中心 → 自定义 → 按钮显隐设置

***

## 六、完整API路由表

### 6.1 基础数据模块 (`/api/basedata/*`)

| API                               | Controller                 | 实体               | 说明     |
| --------------------------------- | -------------------------- | ---------------- | ------ |
| `/api/basedata/customer`          | CustomerController         | Customer         | 客户管理   |
| `/api/basedata/supplier`          | SupplierController         | Supplier         | 供应商管理  |
| `/api/basedata/material`          | MaterialController         | Material         | 物料管理   |
| `/api/basedata/material-category` | MaterialCategoryController | MaterialCategory | 物料分类管理 |
| `/api/basedata/warehouse`         | WarehouseController        | Warehouse        | 仓库管理   |
| `/api/basedata/location`          | LocationController         | Location         | 货位管理   |
| `/api/basedata/employee`          | EmployeeController         | Employee         | 员工管理   |
| `/api/basedata/department`        | DepartmentController       | Department       | 部门管理   |
| `/api/basedata/unit`              | UnitOfMeasureController    | UnitOfMeasure    | 计量单位管理 |
| `/api/basedata/currency`          | CurrencyController         | Currency         | 币种管理   |
| `/api/basedata/account`           | AccountController          | Account          | 会计科目管理 |
| `/api/basedata/payment-term`      | PaymentTermController      | PaymentTerm      | 付款条件管理 |
| `/api/basedata/price-level`       | PriceLevelController       | PriceLevel       | 价格等级管理 |
| `/api/basedata/settle`            | SettlementModeController   | SettlementMode   | 结算方式管理 |

### 6.2 销售管理模块 (`/api/sale/*`)

| API                    | Controller             | 实体           | 说明    |
| ---------------------- | ---------------------- | ------------ | ----- |
| `/api/sale/orders`     | SaleOrderController    | SaleOrder    | 销售订单  |
| `/api/sale/quotes`     | SaleQuoteController    | SaleQuote    | 销售报价单 |
| `/api/sale/contracts`  | SaleContractController | SaleContract | 销售合同  |
| `/api/sale/deliveries` | SaleDeliveryController | SaleDelivery | 销售出库单 |
| `/api/sale/invoices`   | SaleInvoiceController  | SaleInvoice  | 销售发票  |
| `/api/sale/returns`    | SaleReturnController   | SaleReturn   | 销售退货单 |

### 6.3 采购管理模块 (`/api/purchase/*`)

| API                      | Controller                 | 实体               | 说明    |
| ------------------------ | -------------------------- | ---------------- | ----- |
| `/api/purchase/requests` | PurchaseRequestController  | PurchaseRequest  | 采购申请  |
| `/api/purchase/orders`   | PurchaseOrderController    | PurchaseOrder    | 采购订单  |
| `/api/purchase/contract` | PurchaseContractController | PurchaseContract | 采购合同  |
| `/api/purchase/inbounds` | PurchaseInboundController  | PurchaseInbound  | 采购入库单 |
| `/api/purchase/invoices` | PurchaseInvoiceController  | PurchaseInvoice  | 采购发票  |
| `/api/purchase/returns`  | PurchaseReturnController   | PurchaseReturn   | 采购退货单 |

### 6.4 库存管理模块 (`/api/inventory/*`)

| API                              | Controller              | 实体            | 说明     |
| -------------------------------- | ----------------------- | ------------- | ------ |
| `/api/inventory/list`            | InventoryController     | Inventory     | 即时库存查询 |
| `/api/inventory/other-issues`    | OtherIssueController    | OtherIssue    | 其他出库单  |
| `/api/inventory/other-receipts`  | OtherReceiptController  | OtherReceipt  | 其他入库单  |
| `/api/inventory/stock-transfers` | StockTransferController | StockTransfer | 库存调拨单  |
| `/api/inventory/stock-checks`    | StockCheckController    | StockCheck    | 库存盘点单  |
| `/api/inventory/batches`         | BatchController         | BatchInfo     | 批次管理   |

### 6.5 生产管理模块 (`/api/production/*`)

| API                                | Controller                | 实体              | 说明   |
| ---------------------------------- | ------------------------- | --------------- | ---- |
| `/api/production/work-orders`      | ProductionController      | WorkOrder       | 生产工单 |
| `/api/production/routings`         | RoutingController         | Routing         | 工艺路线 |
| `/api/production/operation-report` | OperationReportController | OperationReport | 生产报工 |
| `/api/production/process-transfer` | ProcessTransferController | ProcessTransfer | 工序流转 |

### 6.6 其他模块

| API                  | Controller             | 说明         |
| -------------------- | ---------------------- | ---------- |
| `/api/approval`      | ApprovalController     | 审批流程管理     |
| `/api/dashboard`     | DashboardController    | 仪表盘数据      |
| `/api/sys-config`    | SysConfigController    | 系统配置       |
| `/api/export`        | ExportController       | Excel导入导出  |
| `/api/auth`          | AuthController         | 登录认证       |
| `/api/ai`            | AgentController        | AI Agent对话 |
| `/api/custom-field`  | CustomFieldController  | 自定义字段      |
| `/api/operation-log` | OperationLogController | 操作日志       |

***

## 七、已发现的 Bug 及解决方案

### 1. 设置中心查重功能不生效

**根本原因**：`sys_config` 表的 `config_value` 未被正确更新。

**解决方案**：

```sql
UPDATE sys_config 
SET config_value = 'true', updated_at = NOW()
WHERE config_key IN (
    'sys.customer.duplicate.check',
    'sys.supplier.duplicate.check', 
    'sys.material.duplicate.check'
);
POST /api/sys-config/refresh
```

### 2. 物料导入图片功能

**解决方案**：

- ✅ 支持 Excel 内嵌图片（POI XSSFPicture 解析）

- ✅ 支持网络 URL（http\://、https\://、/uploads/ 开头）

- ❌ 不支持本地文件路径

### 3. 物料导入模板分类字段

**解决方案**：填写**分类编码**（如 `01`、`001`），系统通过编码自动查找分类 ID。

### 4. Windows 批处理文件编码问题

**解决方案**：改用 PowerShell `.ps1` 脚本。

### 5. License 校验导致启动失败

**解决方案**：开发环境关闭 License 校验。

```yaml
erp:
  license:
    enabled: ${ERP_LICENSE_ENABLED:false}
```

### 6. 采购合同列表「按关键词搜索」绕过状态/数据权限/自定义字段过滤（2026-09-16 修复）

**现象**：采购合同列表输入关键词后，① 状态页签形同虚设 ② 「仅本人/本部门」角色越权看到他人合同 ③ 自定义字段搜索条件不生效。
**根因**：`PurchaseContractServiceImpl.pageQuery` 里关键词写成平铺的 `like("contract_no",kw).or().like("supplier_short_name",kw)`，
MyBatis-Plus **不会给 OR 分组补括号**（反编译 `NormalSegmentList#childrenSqlSegment` 核实：只给整段 WHERE 加一层括号），
而 SQL 中 `AND` 优先级高于 `OR` → 实际语义变成 `合同号 LIKE ? OR (供应商简称 LIKE ? AND 状态 AND 数据权限 AND EXISTS)`，
凡是"命中合同号"的行全部放行。
**修复**：关键词两支必须成组 —— `wrapper.and(w -> w.like("contract_no",kw).or().like("supplier_short_name",kw))`。
**守门用例**：`backend/src/test/java/com/erp/purchase/service/impl/PurchaseContractKeywordGroupTest.java`（4 例，退回旧写法即变红）。
**同类写法排查**：全库还有多处 `like(a).or().like(b)` 平铺写法（销售订单、多个报表 Controller、系统用户/角色/字典等）；
**只有"OR 链之后还追加了条件"的才受损**，OR 链是最后一个条件时无害 —— 尚未逐个核实，待单独立项。

### 6.1 列表页查询统一约定（2026-09-16 起强制）

> 背景：同一类「列表页查询」缺陷在 19 个页面重复出现（参数名不一致、控件点了没反应、排序未接、OR 漏括号），逐个修必然复发 → 改为**通用件 + 强制约定**。

| 约定     | 内容                                                                                                                                                                                                                                                                                                                               |
| ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 前端搜索区  | 通用壳 [BillListPage.vue](../frontend/src/components/bill/BillListPage.vue) + 通用搜索栏 [BillSearchBar.vue](../frontend/src/components/bill/BillSearchBar.vue)。4 个标准维度：`status` 单据状态（下拉，默认「全部」；**页面声明 `status` 时壳自动隐藏状态页签**，避免两处控同一条件）/ `date` 日期区间 / `customer` 客户（**精准**：按客户 ID 过滤，placeholder 写明"精准搜索"）/ `supplier` 供应商 / `employee` 员工（销售员、采购员…）；**只渲染页面** **`filters`** **里声明的维度** → 未接入的维度不渲染，机制上杜绝"渲染了却点了没反应"                           |
| 前端参数名  | 统一 `current` / `size` / `keyword` / `status` / `startDate` / `endDate` / `customerId` / `supplierId` / `employeeId` / `sortBy` / `sortOrder` / `customSearch`。**禁止页面自行发明参数名**                                                                                                                                                    |
| 搜索区布局（2026-09-16 用户口径） | 通用壳内为**两栏固定**：左栏 `.search-filters`（`flex:1` + `flex-wrap`，以后新增搜索框都在这栏里长，可多行）＋ 右栏 `.search-actions`（`flex:0 0 auto; align-self:flex-start`）。**搜索/重置/⚙ 位置永不漂移，⚙ 贴右边框**；筛选换行成多行时右栏仍固定在**第一行高度**（`.search-bar{align-items:flex-start}`），**不允许垂直居中**。禁止把按钮写进会随筛选换行的容器里 |
| 顶部统计卡片 | 必须接后端真实数据，**禁止硬编码 0**（销售订单曾经 4 张卡片恒为 0）。各页仪表盘接口按同数据权限口径返回；取数失败静默降级、不弹诊断卡。销售订单：`GET /api/sale/dashboard`（注意该接口挂在 `/api/sale` 下，不是 `/api/sale/orders` 下） |
| 统计卡片可点击（2026-09-16 用户口径） | 卡片不只是展示：**带 `filter` 声明的卡片可点击 → 直接筛选下方列表**（再点同一张卡=取消）。通用壳 `stats` 项结构：`{ key, label, value, type, icon, filter }`，`filter` 是可直接并入 `search` 的条件对象（如 `{status:'草稿'}` / 当月 `{startDate,endDate}`）；壳 emit `stat-click`，高亮由页面传 `active-stat-key` 控制。**选中态必须把整卡背景色改掉**（现为 `#eef2ff` + 边框 + 数值/标题变色）——只改边框用户看不出来（用户 2026-09-16 明确要求"要改背景色啊这样才明显"） |
| 筛选控件必须带标签（2026-09-16 用户口径） | 光靠 placeholder 用户看不出每个框搜什么 → 每个筛选维度渲染「标签 + 控件」：`单据编号 / 单据状态 / 单据日期 / 客户 / 供应商 / <员工维度文案>`。壳的 `keywordLabel` prop 控制关键词框标签，其余标签在 `BillSearchBar` 的 `LABEL_MAP`（⚠ 目前是中文字面量，英文界面下不翻译，接入 i18n 待办） |
| 筛选区栅格对齐（2026-09-16 用户口径） | 筛选条件换行成两行/三行时**必须逐列对齐**（用户报"第一行和第二行没对齐"）。规则：`.filter-item` 统一宽度 `calc(var(--erp-list-search-width, 280px) + 68px)`（标签列 60 + 间距 8）+ `.filter-label{flex:0 0 60px; text-align:right}` + 控件 `flex:1 1 auto; width:auto`（覆盖模板内联宽度）→ 每列「标签右边缘 + 8px = 本列控件左边缘」。⚠ 禁止让控件宽度随内容走（否则两行必错位）。用 `--erp-list-search-width` 是为了让系统「界面尺寸设置」仍然生效 |
| 通用壳可选 props（2026-09-16 起） | `keywordLabel`（关键词框标签，默认「单据编号」）/ **`dateLabel`**（日期维度标签，默认「单据日期」；台账类页面如库存流水传「交易日期」，否则文案与真实列名不符）/ **`pageSizes`**（每页条数选项，默认 `[20,50,100]`；**页面原本有 200/页 能力的必须按原样传入**，否则属功能收敛） |
| 列表页接入进度（2026-09-16） | **已改造**：销售订单；**P1 已完成**：采购 6（订单/合同/入库/退货/发票/申请）+ 库存 6（调拨/其他入库/其他出库/生产领料/盘点/流水）；**待改造**：销售其余 5 页（已用壳未改造）+ 生产 15 + 委外 4 + 财务 5 + 其它单据 7 + 商机管理 1。**不接入**：基础资料/报表/系统（用户决定）、CRM 除商机外 9 页、成本核算与成本核算配置（核算/配置类，**不属单据**） |
| ⚠ dashboard 字段命名不统一（待统一） | `sale`：`monthOrders/draftOrders/pendingOrders/approvedOrders`；`purchase`：`monthOrders/monthContracts/monthInbounds/…`；`inventory`：`monthCount/draftCount/pendingCount/approvedCount`（流水页为 `totalCount/inCount/outCount/monthInAmount`）。各页自消费不影响功能，但后续接入新页时**以其页面已消费的字段为准**，别照抄别模块命名 |
| ⚠ 卡片与列表必须同口径（2026-09-16 排查发现并修复） | 两处口径必须逐字对齐，否则用户会看到"卡片数字与列表条数对不上"：① **时间窗口**——"本月"卡后端必须按**当月 1 号~当月最后一天**统计（原来按"到今天"，而前端点卡片后按整月筛，流水页实测差 7 条）；② **数据权限列**——列表在"仅本人"范围下按「创建人 **或** 业务角色列」过滤，卡片统计也必须传**同一列名**（销售订单→`saler_id`、采购 4 单→`buyer_id`、采购申请→`applicant_id`），原来卡片只认创建人。**验证口径**：`卡片值 == 用整月区间查列表的 total` |
| ⚠ 状态下拉选项必须来自状态机真实取值 | 只为"能筛出数据"的状态建选项，且不能漏真实状态。案例：销售订单/采购合同下拉曾有"已关闭"（后端**从不写**该值 → 点了永远 0 条，实测 0 条）；同时缺"已取消"（后端确实会写）。排查方法：在对应 `*ServiceImpl` 里全文检索状态写入语句，把**真实取值集合**与下拉选项**逐一比对**（采购盘点单是 7 项完全对齐的样板）。另注：采购合同真实还会写"已作废"，目前不在下拉（待产品定） |
| 关键词可按"客户名称"搜（2026-09-16 补齐，通用件能力） | 销售类单据表**只存客户主键、没有客户名称列**，因此关键词要能按客户名称命中，必须由**通用件**提供能力：`ListQuerySupport.applyKeywordGroupWithIds(w, keyword, 文本列清单, 关联主键列, 按名称查出的主键集合)` → 生成 `AND ( 单号 LIKE ? OR … OR customer_id IN (…) )`（整体成组）；主键解析用 `CustomerService.findIdsByNameLike(keyword, limit)`（名称+简称模糊、上限 500、**不套客户私海/公海权限**——否则会出现"单据明明有这个客户却搜不到"）。6 个销售页已全部接入（订单/报价/合同/出库/退货/发票）。⚠ 遇到"关键词要匹配别处字段"的需求，**一律补通用件能力**，禁止把搜索框文案改成"只能搜单号"来掩盖 |
| 后端查询件  | [ListQuerySupport.java](../backend/src/main/java/com/erp/common/query/ListQuerySupport.java)：`resolvePage`（`current` 优先、旧名 `page` 兜底）/ `applySort`（列名白名单）/ **`applyKeywordGroup`（多列关键词强制成组）** / `applyDateRange` / `applyId` / `applyEmployee`（统一 `employeeId` → 本页列名映射，如 `saler_id`/`buyer_id`/`handler_id`/`applicant_id`，不改表） |
| ⚠ 命名陷阱 | 部分接口 **URL 叫** **`/page`，但分页入参名是** **`current`**（StockTransfer/OtherReceipt/OtherIssue/MaterialRequisition/StockCheck/ProductionReceipt/FreeSampleOrder/OperationExpense + CRM 5 个）→ 前端按 URL 猜参名会静默失效（CRM 5 页/经营费用就是这么踩的）                                                                                                        |
| ⚠ 反例   | `like(a).or().like(b)` 平铺写法**一律禁止**（漏括号会让后续状态/数据权限/EXISTS 失效，2026-09-16 采购合同事故根因）→ 只能用 `applyKeywordGroup`                                                                                                                                                                                                                       |

***

## 八、编码避坑指南

### 8.1 数据库配置项更新

```java
// ❌ 错误：只更新了名称和备注
sql.append("ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name");

// ✅ 正确：必须包含 config_value
sql.append("ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value");
```

### 8.2 图片上传路径

```java
String uploadPath = "d:/erp/uploads";
// 物料图片: uploadPath + "/material/yyyy/MM/dd/uuid.ext"
// 返回URL: /uploads/material/yyyy/MM/dd/uuid.ext
```

### 8.3 单据状态流转

```
草稿 → 待审核 → 已生效 → 已关闭
  ↓        ↓         ↓
  └──废弃   └──重置草稿  └──重置草稿→待审核
```

***

## 九、问题排查流程

### 当发现功能异常时

1. **检查配置**

   ```sql
   SELECT * FROM sys_config WHERE config_key = 'xxx';
   ```

2. **刷新缓存**

   ```
   POST /api/sys-config/refresh
   ```

3. **检查日志**

   - 后端：WARN/ERROR 日志

   - 前端：浏览器控制台

***

## 十、版本历史

| 日期         | 更新内容                                                                                                                                                                              |
| ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-08-19 | 初始版本：模块结构、API路由                                                                                                                                                                   |
| 2026-08-19 | 完善：添加设置中心完整功能清单                                                                                                                                                                   |
| 2026-08-19 | 完善：添加80+系统参数详细清单                                                                                                                                                                  |
| 2026-08-19 | 完善：添加审批流支持的所有单据类型                                                                                                                                                                 |
| 2026-08-19 | 完善：添加按钮配置体系说明                                                                                                                                                                     |
| 2026-08-19 | 新增：ERP业务参考知识库（金蝶/用友主流做法摘录，仅参考非标准）；同日按用户要求明确其定位为纯参考资料                                                                                                                              |
| 2026-08-21 | 新增：第十二章「8开头服务器部署实录与排障知识库」——环境清单、12个故障根因与修复（含jar路径不一致、sys\_menu空表、psql GBK编码三大坑）、验证命令集、排障方法论                                                                                       |
| 2026-08-31 | 新增：第十四章「部署链路可靠性体系」（MD5+原子切换+回滚闭环+SQL Kit四道防线）                                                                                                                                     |
| 2026-08-31 | 新增：第十五章「标准台账下线与报表中心模块现状」——标准台账子系统整体删除，台账需求由自定义报表承接                                                                                                                                |
| 2026-09-02 | 新增：销售/采购发票报表后端9接口 + 前端两页切换真实数据（口径见15.4）；同日完成括号配平与mock残留静态验证                                                                                                                       |
| 2026-09-16 | 修复：采购合同列表关键词 OR 分组缺括号（状态/数据权限/自定义字段过滤被绕过）＋ 列表页查询参数口径对齐通用组件（keyword + customSearch + 排序）；新增 `deploy/local-run/Restart-LocalBackend.ps1`（含「进程 CWD 决定 JWT 密钥文件路径」的坑）                 |
| 2026-09-16 | 新增：列表页查询**通用件** —— 前端 `BillSearchBar.vue`（4 维声明式筛选：编号/日期/客户供应商/员工）+ 后端 `ListQuerySupport.java`（分页兼容/排序白名单/关键词强制成组/日期/ID/员工维度）；**销售订单**为试点（关键词改按文案支持客户名、新增日期区间+客户+销售员筛选），见 6.1 节约定 |

***

## 十一、ERP业务参考知识库（金蝶/用友等主流做法摘录）

> 本章来源：金蝶云星空、金蝶精斗云/星辰、用友T+、用友好业财等主流ERP系统的业务逻辑摘录。
> 定位（2026-08-19 用户明确）：**仅供理解 ERP 通用业务逻辑的参考资料，不是本系统的开发标准或验收标准**。本系统有自己的产品设计，每个ERP都不一样；仅当用户提出某功能需求时，可从这里查主流做法作参考，禁止拿本章内容反向要求现有系统"补齐差距"。
> **配套文档**：字段级参考手册见 [erp-field-manual.md](./erp-field-manual.md)（销售/采购订单、销货单、调拨/盘点单、存货/往来单位档案的逐字段解释、取值联动、录单规则，同样仅作参考）。

### 11.1 单据状态与生命周期规范

**标准状态机**（金蝶/用友通用）：

```
草稿(录入中) → 保存(待审核) → 审核中(多人审批流场景) → 已生效(生效)
     ↑                                                  ↓
     └──────────── 重置草稿(弃审) ←────────────────────────┘
                                                        ↓
                                              已关闭(手工关闭/执行完成)
```

**关键规则**：

| 规则      | 说明                                                |
| ------- | ------------------------------------------------- |
| 保存≠生效   | 单据保存后仅暂存，不产生库存/财务影响；审核后才生效                        |
| 重置草稿条件   | 已生效单据可重置草稿回草稿；但**下游已有关联单据（如订单已开出入库单）或已生成凭证时禁止重置草稿** |
| 关闭语义    | 关闭≠作废：关闭是业务决定不再执行（如订单不再发货），已执行部分保留                |
| 作废/删除条件 | 仅草稿或无下游单据的可删除；已生效单据只能作废冲销，不能物理删除                  |
| 打印控制    | 可配置"仅生效单据允许打印"（本系统参数 sys.print.when.effective）    |
| 单据日期锁定  | 可配置"审核后不允许修改单据日期"（本系统参数 sys.lock.bill.date）       |
| 数量容差    | "部分交付/交付完成"状态应由行累计数量+容差自动计算，不允许用户手工指定             |

### 11.2 进销存核心业务流程（订单→出入库→发票→结算闭环）

**销售闭环**（金蝶标准流程）：

```
销售报价单 → 销售订单 → 销售出库单(发货) → 销售发票(立账) → 收款单 → 核销
                ↓            ↓                                  ↑
             信用控制      占用/释放虚拟库存            预收款冲抵
                ↓
          销售退货单 ←── 红字入库，冲减已发货数量
```

**采购闭环**（用友标准流程）：

```
采购申请(PR) → 询价单(RFQ)/比价 → 采购订单(PO) → 到货/质检 → 采购入库单
                                    ↓                          ↓
                                 采购变更单(差异审批)      采购发票(立账) → 付款单 → 核销
                                                               ↑
                                                        采购退货单(红字出库)
```

**关键规则**：

| 规则    | 说明                                           |
| ----- | -------------------------------------------- |
| 单据溯源  | 下游单据必须记录来源单据号（source\_order\_id），支持向前追溯、向后联查 |
| 数量回写  | 出库审核回写订单"已发货数量"；退货冲减；关闭订单时未发货部分不再占用          |
| 一单多流  | 一个订单可分多次出库（累计数量≤订单数量+超发容差）；一次出库可含多批次         |
| 立账口径  | 发票立账模式下：出库≠应收，开票后才形成应收；销货单立账模式下：出库即立账        |
| 三单匹配  | 采购结算时"订单价×入库量 vs 发票额"三单匹配，差异超容差需审批           |
| 红字规则  | 退货单必须关联原蓝字单据取成本；无来源单的红字出库按"退货成本选项"取价         |
| 零成本出库 | 出库时账面为零/负库存，按系统选项处理（参考成本/上次出库价/提示报错）         |

### 11.3 库存管理规范（参照金蝶云星空）

**库存维度**：库存记录按「物料+仓库(+货位)+批次(+序列号)+库存状态(合格/不合格/待检)」多维存储。

- 即时库存多维度汇总查询，支持按货主/保管者/库存状态区分

- 不良品支持锁库（库存状态隔离，不可被销售占用）

- 多计量单位：基本单位/库存单位/辅助单位，按换算率自动折算

**批次与保质期管理**：

| 规则     | 说明                           |
| ------ | ---------------------------- |
| 批号自动生成 | 按编码规则自动获取批号（本系统：设置中心→批次编号设置） |
| 保质期预警  | 生产日期+保质期→到期日，临期自动预警          |
| FIFO拣货 | 按批号/保质期设置拣货规则，出库自动指定先到期批次    |
| 批次调整   | 支持批号调整单（录错批号时修正，保留痕迹）        |

**盘点规范**：

```
创建盘点任务(盘点方案：全盘/抽盘，按仓库/物料/批次/货位维度)
  → 锁定/备份库存快照（盘点期间出入库需冻结或并行记录）
  → 实盘录入（盘點数量）
  → 生成差异 → 盘盈单(其他入库)/盘亏单(其他出库) → 审核调整账面
```

- 定期盘点（月结/年终）+ 周期盘点（循环盘点）两种模式

- 盘盈盘亏单必须走审批流后才能调整库存

**调拨规范**：

| 类型    | 说明                                     |
| ----- | -------------------------------------- |
| 直接调拨  | 一步完成：调出仓-quantity → 调入仓+quantity，审核即移动 |
| 分步式调拨 | 两步：调拨出库单（在途）→ 调拨入库单；支持在途库存跟踪           |
| 跨组织调拨 | 视同内部购销，产生组织间结算价                        |

**其他规则**：

- 形态转换：不合格品→合格品（返工），形态转换单记录

- 库存调整：仅调整批号/状态，不改变总量

- 关账：可指定日期关账，关账后该日期前的单据禁止重置草稿

- 预警体系：安全库存/最高库存/最低库存/保质期到期预警

### 11.4 存货成本核算方法规范（金蝶/用友标准）

| 方法         | 原理                                   | 优点              | 缺点               | 适用场景            |
| ---------- | ------------------------------------ | --------------- | ---------------- | --------------- |
| 先进先出(FIFO) | 先入库先出库，按批次顺序结转成本                     | 符合实物流转，期末存货接近市价 | 物价波动大时利润失真；需逐批记录 | 食品、医药等有保质期行业    |
| 移动加权平均     | 每次入库立即重算平均成本：(结存成本+入库成本)÷(结存数量+入库数量) | 成本实时，业务财务同步     | 成本调整后需出库调整单，单量大  | 采购频繁、需实时利润的商贸企业 |
| 月末一次加权平均   | 月末统一计算：(月初+本月入库成本)÷(月初+本月入库数量)       | 计算简单，同月出库成本一致   | 月中无法知道真实成本       | 存货品种少、收发不频繁     |
| 个别计价       | 出库时指定对应入库单号，按该单单价结转                  | 成本最精确，实物成本一致    | 需逐单指定，工作量大       | 高价值单件商品（设备、珠宝）  |
| 标准成本       | 预设标准单价，差异单独核算                        | 便于成本控制和差异分析     | 标准制定工作量大         | 管理成熟的制造业        |

**系统实现规则**（参照用友T+）：

1. 计价方法在存货/物料级设置，可按物料混合使用不同方法
2. 红字出库单优先从来源单取成本，取不到按"退货成本选项"处理
3. 入库成本变化（费用分摊/采购核算）后自动调整对应出库成本：同月未生成凭证→直接改单；跨月或已生成凭证→生成出库调整单
4. 出现零成本/负成本出库时按「零成本出库选项」：参考成本/上次出库成本/最大入库成本/提示手工输入
5. 移动平均法：每次入库后即时更新平均成本，出库按当前最新平均单价计价

### 11.5 价格管理规范（金蝶标准）

**价格取数优先级**（销货类单据自动取价，按优先级逐级向下匹配）：

| 优先级 | 取价来源     | 说明                                 |
| --- | -------- | ---------------------------------- |
| 1   | 最近售价     | 按客户+商品匹配最近一次销售单价（未审核单据也更新最近价）      |
| 2   | 商品价格策略   | 按客户等级匹配商品预设价格（零售价/批发价/VIP价/价格1-10） |
| 3   | 最近报价     | 按客户+商品匹配最近报价单单价                    |
| 4   | 物料档案销售报价 | 商品资料维护的基础报价                        |

- 优先级数值越小越优先；某规则关闭则跳过取下一个

- 无任何匹配时单价默认为0，需手工填写；本次保存后即成为"最近售价"

- 采购取价：最近采购价（按供应商+商品）→ 预计采购价

- 含税口径：价格策略维护的价格含税与否由系统参数"商品价格是否含税"统一控制，对应带入单据"含税单价"或"不含税单价"列

- 多计量单位：录入基本单位价格，副单位价格按换算率自动折算，支持手工修正

- 毛利率测算参考成本：预计参考单位成本（物料档案预计采购价）或实时参考单位成本（库存余额表实时成本）

**限价策略**：

| 类型   | 触发条件                    | 控制方式          |
| ---- | ----------------------- | ------------- |
| 销售限价 | 实际售价 < 最低销售价（或历史最低售价）   | 预警提示 / 强制取消交易 |
| 采购限价 | 实际采购价 > 最高采购价（或历史最高采购价） | 预警提示 / 强制取消交易 |

### 11.6 信用控制规范（金蝶星辰/用友好业财标准）

**信用控制公式**：

```
信用额度占用额 = 已订货未出货/未销货 + 已出货未立账 + 销售应收款
               + 零售应收款 + 其他应收款 - 预收款 - 费用单(冲应收)

通过条件：占用额 - 信用额度 ≤ 0
信用期限：当前单据日期 - 最早未收款立账单据的收款到期日 ≤ 0
```

**控制配置项**：

| 配置   | 选项                                          |
| ---- | ------------------------------------------- |
| 控制对象 | 结算客户 / 业务员（业务员占用=源头单据业务员相同的各量求和）            |
| 控制范围 | 只控制额度 / 只控制期限 / 额度+期限                       |
| 控制时点 | 保存时（含未审核单据）/ 审核时（仅已生效单据）                    |
| 控制强度 | ①提示，允许继续 ②提示+输入密码继续 ③提示，不允许（禁止保存），支持白名单客户豁免 |

**关键规则**：

1. 信用额度在客户档案按本位币录入；**不录入或为0则不控制**该客户
2. 信用额度按客户单独核算，不支持多客户共用一套额度
3. 特权审批（金蝶云星空）：超额度时可走"信用总额特权审批"流程，由有特批额度的人员审批放行，收款时同步返还普通额度+特批额度
4. 预收款默认占用信用额度，可配置未审核预收款单不计入
5. 红、蓝字单据都影响信用占用；红字立账单据不参与信用期限计算
6. 未启用审核流程的单据即使选择"审核时点"也按"保存时点"控制

### 11.7 审批流规范（金蝶/用友/云之家标准）

**审批节点类型**：

| 模式         | 规则                             | 本系统对应    |
| ---------- | ------------------------------ | -------- |
| 会签(AND/全员) | 所有审批人**全部**通过才流转下一节点，任一驳回即整体驳回 | all      |
| 或签(OR/任一)  | **任一**审批人通过即流转下一节点             | any      |
| 依次审批       | 按次序逐人审批，全部通过才流转                | sequence |
| 单人审批       | 指定一人审批                         | single   |

**审批人来源**：指定用户 / 指定角色 / 部门主管（第n级主管）/ 提交人自选 / 发起人本人。

- 部门主管有多个时：会签=全部通过，或签=任一通过

- 跨组织用户取最早加入的直属组织作为审批链条

**条件分支**（用友T+标准）：

- 按表单字段设置流转条件：金额区间、业务类型、部门、物料品类等

- 多个条件从左到右按优先级1→N依次判断，命中即进入对应分支

- 所有分支必须有完整闭环（审批→驳回→结束），杜绝流程悬空

**典型审批矩阵**（金额分级）：

| 单据类型 | 金额          | 审批人1 | 审批人2 | 审批人3 |
| ---- | ----------- | ---- | ---- | ---- |
| 采购订单 | <1000元      | 采购经理 | -    | -    |
| 采购订单 | 1000-10000元 | 采购经理 | 部门总监 | -    |
| 采购订单 | >10000元     | 采购经理 | 部门总监 | 总经理  |

**其他规范**：

| 功能   | 规则                                        |
| ---- | ----------------------------------------- |
| 驳回   | 支持驳回制单人（修改后重新走流程）和驳回上级（上级修改后重新审）；驳回必须填写原因 |
| 撤回   | 仅终审前、下一节点未处理时可由发起人撤回，修改后重新提交              |
| 加签   | 前置加签（新增人审完才流转）/ 后置加签（当前节点完成后辅助复核）         |
| 转办   | 审批人转交他人处理，权责同步转移，全程留痕                     |
| 可终审  | 中间节点可标记"可终审"：该节点通过即整单生效，后续节点不再审批          |
| 规则版本 | 新审批规则仅对之后提交的单据生效，审批中的单据按旧规则走完             |
| 消息通知 | 待审提醒：站内消息/邮件/短信/APP推送                     |
| 移动审批 | 支持手机端一键同意/驳回                              |

### 11.8 采购到料与质检规范（制造业标准）

**收货质检三模式**：

| 模式   | 流程                  | 适用场景        |
| ---- | ------------------- | ----------- |
| 免检   | 收货直接入库              | 低风险物料、合格供应商 |
| 先收待检 | 到货登记→待检区→IQC质检→合格入库 | 常见制造业来料     |
| 紧急放行 | 到货→临时放行使用→后补检验      | 需严格授权和追溯    |

**质检处置方式**：合格入库 / 让步接收（挑选使用）/ 返工 / 换货 / 退货 / 索赔——不止"退货"一条路。

**数量口径字典**（PO行必须明确）：

```
订单数量 / 已发货数量 / 已收货数量 / 待检数量 / 合格数量
不合格数量 / 已退货数量 / 已开票数量 / 已结算数量
```

"已收货"是物理签收还是质检合格入库，必须在字段定义中明确口径。

### 11.9 主流做法与本系统现状对照（纯信息罗列，不代表本系统需要改）

| 业务概念    | 主流做法（金蝶/用友）             | 本系统现状              | 说明              |
| ------- | ----------------------- | ------------------ | --------------- |
| 单据状态机   | 草稿→待审→已审→关闭             | 已实现（保存=草稿，提交走审批开关） | 两边思路一致，仅记录      |
| 重置草稿控制   | 有下游单据禁止反审               | 已实现重置草稿             | 按本系统规则运行即可      |
| 审批流4模式  | single/any/all/sequence | 已实现4种节点模式          | 两边一致，仅记录        |
| 信用控制    | 占用公式+3档强度               | finance模块有信用功能     | 需要时再参考11.6，不主动改 |
| 价格取数优先级 | 最近售价→价格策略→最近报价          | 设置中心有销售/采购价格策略     | 需要时再参考11.5，不主动改 |
| 限价策略    | 低于最低售价预警/禁止             | 未做                 | 仅当用户要求时再实现      |
| 成本核算    | FIFO/移动平均/月加权/个别计价      | 未做                 | 仅当用户要求时再实现      |
| 批次管理    | 批号规则+保质期+FIFO拣货         | 有批次编号设置            | 仅当用户要求时再扩展      |
| 盘点      | 快照→实盘→盘盈亏单→审批调整         | 有盘点模块              | 按本系统现有逻辑运行      |
| 三单匹配    | 订单-入库-发票匹配              | 有采购发票              | 仅当用户要求时再实现      |

***

## 十二、8开头服务器部署实录与排障知识库（2026-08-21）

> 本章完整记录8开头个人服务器（公网IP 8.148.229.2，内网 172.31.56.160）部署SmartERP全功能版的全过程：环境清单、每个故障的根因与修复、验证命令集、防复犯铁律。
> **配套记忆**：`c:\Users\z\.trae-cn\memory\projects\-d-erp--p2-5b38c3286afbbef253b1\project_memory.md`（含121服务器部署铁三角、bat防闪退、zip打包铁律）。

### 12.1 服务器环境清单（8开头服务器）

| 组件         | 版本/路径                                                       | 备注                                                                         |
| ---------- | ----------------------------------------------------------- | -------------------------------------------------------------------------- |
| 操作系统       | Windows Server 2025 Datacenter，4核8G                         | 阿里云华南3（广州）                                                                 |
| JDK        | 17.0.19，`C:\Program Files\Amazon Corretto\jdk17.0.19_10`    | 自启脚本用 `bin\javaw.exe`                                                      |
| PostgreSQL | 16，`C:\Program Files\PostgreSQL\16`，服务名 `postgresql-x64-16` | 业务库 `erp_db`，业务账号 `erp_user`/`Erp@2026`；postgres超级用户密码未知（scram认证失败）        |
| Redis      | 服务名 `Redis`                                                 | <br />                                                                     |
| Nginx      | 1.30.4，`C:\nginx`                                           | 主配置 `conf\nginx.conf`(252B)只做include，**真实站点配置在** **`conf\erp.conf`(579B)** |
| 部署目录       | `C:\erp\smart-erp-deploy`                                   | 后端jar、前端、脚本、SQL都在这                                                         |
| 日志         | `C:\erp\smart-erp-deploy\logs\erp.log`、`autostart.log`      | <br />                                                                     |
| 启动方式       | `start_all_silent.ps1` + 计划任务 `install_autostart.bat` 开机自启  | PG/Redis是Windows服务                                                         |

**Nginx关键配置**（`C:\nginx\conf\erp.conf`）：

```nginx
server {
    listen 80 default_server;
    server_name _;
    root C:/erp/smart-erp-deploy/frontend;
    index index.html;
    location /api/ { proxy_pass http://127.0.0.1:8080; ... }
    location /uploads/ { proxy_pass http://127.0.0.1:8080; }
    location / { try_files $uri $uri/ /index.html; }
}
```

排查root路径时注意：`findstr "root" C:\nginx\conf\nginx.conf` 会**返回空**（root在erp.conf里），必须查 `erp.conf`。

### 12.2 【最大陷阱】开机自启脚本读的jar路径 ≠ 更新包放的位置

**现象**：更新包把新jar放 `C:\erp\smart-erp-deploy\backend\smart-erp.jar`，RUN-UPDATE-SAFE.bat也成功换了这个文件（`.bak`备份都在），但接口行为永远是旧的——`menuRoutePaths数量=0` 反复出现。

**根因**：`start_all_silent.ps1` 第79行：

```powershell
$JAR = Join-Path $APP_HOME "smart-erp-1.0.0.jar"
```

开机自启的后端实际从 **部署包根目录** `C:\erp\smart-erp-deploy\smart-erp-1.0.0.jar` 启动，而更新包把jar放在 `backend\` 子目录——**位置对不上，更新从未作用到运行中的进程**。

**修复**：

```powershell
Copy-Item "C:\erp\smart-erp-deploy\backend\smart-erp.jar" "C:\erp\smart-erp-deploy\smart-erp-1.0.0.jar" -Force
powershell -ExecutionPolicy Bypass -File "C:\erp\smart-erp-deploy\start_all_silent.ps1"
```

**铁律**：更新部署前，先确认"正在运行的进程实际加载的是哪个文件"（查启动脚本的jar路径/进程命令行），**不能假设更新包路径=运行路径**。这是本次反复折腾的主因。

### 12.3 故障清单（按发现顺序，全部已解决）

| #  | 故障                                          | 根因                                                                                                                                                                                                                                  | 修复                                                                                                                                                                              |        |                         |
| -- | ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ----------------------- |
| 1  | 登录502 Bad Gateway                           | 后端启动参数错误（`-Dspring.config.location`指向不存在文件、引号嵌套）致进程退出，8080未监听                                                                                                                                                                       | 修正 `start_all_silent.ps1` 启动参数，加端口监听等待验证                                                                                                                                        |        |                         |
| 2  | 浏览器显示旧登录页                                   | ①Nginx配置遗留冲突server块 ②浏览器缓存                                                                                                                                                                                                          | `cleanup_nginx.ps1` 清理（listen 80 default\_server + server\_name \_）+ Ctrl+Shift+R强刷/无痕窗口                                                                                        |        |                         |
| 3  | Nginx "conflicting server name"             | nginx.conf里有旧80端口server块与erp.conf冲突                                                                                                                                                                                                 | 删除nginx.conf里的server块，只留include erp.conf                                                                                                                                        |        |                         |
| 4  | Nginx "OpenEvent Access denied"             | `nginx -s reload` 权限不足                                                                                                                                                                                                              | `taskkill /F /IM nginx.exe` 后重新start                                                                                                                                            |        |                         |
| 5  | 后端启动 exit=1（25秒退出）                          | `erp_db.sys_config`缺 `config_section/config_type/config_options` 三列 → `Kuaidi100Service.init()` 查询异常 → ApplicationContext启动失败                                                                                                       | ①SQL `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 补列 ②`SysConfigServiceImpl.ensureColumnsExist()` 启动幂等补列 ③`Kuaidi100Service` 三方法加try-catch降级 ④新增 `SchemaUpgradeEnsurer` 启动自动补业务库列 |        |                         |
| 6  | `/api/sys-config/public/basic` 返回401        | ①后端根本没这个端点 ②AuthInterceptor白名单未放行                                                                                                                                                                                                   | ①SysConfigController新增 `/public/basic` 匿名GET（返回system.name/logo/subtitle/copyright/customer\_service\_phone五项）②白名单加 `path.startsWith("/api/sys-config/public")`                 |        |                         |
| 7  | 登录后「选择公司→正在进入」按钮永久卡住                        | `/api/auth/userinfo` 返回仅216B：`sys_user_role`表无该用户记录 → roleIds=\[] → allowedMenuKeys=\[]，isAdmin=true未传入下游，`if (!allowedMenuKeys.isEmpty())` 整块跳过 → menuRoutePaths/menuIds/permissions 全空 → 前端syncUserLanding后路由守卫空菜单跳转异常，loading不解除 | AuthController两处：①isAdmin时兜底 `hasWildcardPermission=true` ②进入权限分支条件改为 \`!allowedMenuKeys.isEmpty()                                                                              | <br /> | hasWildcardPermission\` |
| 8  | 修复jar部署后接口依旧旧版                              | 见12.2，jar路径不一致                                                                                                                                                                                                                      | jar复制到自启脚本读取的真实路径                                                                                                                                                               |        |                         |
| 9  | jar正确后 menuRoutePaths 仍=0                   | **`sys_menu`** **表是空的（0行）**——部署初始化数据库时漏导菜单SQL（权限表102条正常、唯独菜单0）                                                                                                                                                                      | 从本地验证库导出117条菜单 → `fix_menus_20260821.sql` 导入（脚本幂等：DELETE再INSERT）                                                                                                                |        |                         |
| 10 | psql执行菜单SQL报 `编码"GBK"的字符...在编码"UTF8"没有相对应值` | 中文Windows PowerShell默认GBK编码，psql把UTF-8的SQL文件按GBK读                                                                                                                                                                                   | 见12.4 psql编码铁律（121服务器2026-08-17踩过同款坑，记忆里有但未先搜索——被用户批评"错误记忆不先搜索吗"）                                                                                                               |        |                         |
| 11 | 另一台电脑ping通但页面空白                             | Nginx root曾指向空目录/浏览器缓存；后确认为客户端侧问题                                                                                                                                                                                                   | root确认指向 `C:/erp/smart-erp-deploy/frontend`；无痕窗口排除缓存                                                                                                                            |        |                         |
| 12 | 另一台电脑完全访问不了                                 | 用户输入了**全角中文冒号** `http：//8.148.229.2`——浏览器当搜索词处理                                                                                                                                                                                     | 地址栏用英文半角冒号 `http://8.148.229.2`；无法访问时先在服务器跑 `Invoke-WebRequest http://localhost` 区分服务端/客户端问题                                                                                    |        |                         |

**修复后的正确基线**：`/api/auth/userinfo`（admin登录）应返回 menuRoutePaths=117、menuIds=117、permissions=105、roles=\["ADMIN"]、tenantVersion="ULTIMATE"。返回216B/空数组即说明命中故障7或9。

### 12.4 psql执行UTF-8 SQL铁律（中文Windows服务器）

**必须先设编码再执行**，否则GBK/UTF8转换错误：

```powershell
chcp 65001
$env:PGPASSWORD='Erp@2026'; $env:PGCLIENTENCODING='UTF8'
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U erp_user -h localhost -d erp_db -f "C:\erp\smart-erp-deploy\xxx.sql"
```

- postgres超级用户密码未知，一律用业务账号 `erp_user`/`Erp@2026`

- 幂等SQL（DELETE+INSERT / ADD COLUMN IF NOT EXISTS / ON CONFLICT DO NOTHING）可安全重跑

### 12.5 常用验证命令集（8开头服务器）

**① 接口链路全验证（登录→菜单数）**：

```powershell
$body = @{ username='admin'; password='admin123' } | ConvertTo-Json; $login = Invoke-RestMethod -Uri 'http://localhost/api/auth/login' -Method Post -Body $body -ContentType 'application/json'; $token = $login.data.token; $headers = @{ Authorization = 'Bearer ' + $token }; $info = Invoke-RestMethod -Uri 'http://localhost/api/auth/userinfo' -Method Get -Headers $headers; Write-Host ('menuRoutePaths数量 = ' + $info.data.menuRoutePaths.Count)
```

预期 `117`。要看完整结构把最后一句换成 `$info.data | ConvertTo-Json -Depth 4`。

**② 数据库直查**：

```powershell
$env:PGPASSWORD='Erp@2026'; & "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U erp_user -h localhost -d erp_db -c "SELECT COUNT(*) FROM sys_menu;"
```

**③ 端口/进程**：`netstat -ano | findstr ":80 "` / `findstr ":8080"`（0.0.0.0:80 LISTENING=正常；只127.0.0.1=外部访问不了）

**④ Nginx静态页**：`Invoke-WebRequest -Uri 'http://localhost' -UseBasicParsing | Select-Object StatusCode`（200=正常）

**⑤ 后端日志检索**：

```powershell
Select-String -Path C:\erp\smart-erp-deploy\logs\erp.log -Pattern "获取版本菜单失败|获取角色菜单权限失败|does not exist" | Select-Object -Last 15
```

**⑥ 全量重启**：`powershell -ExecutionPolicy Bypass -File "C:\erp\smart-erp-deploy\start_all_silent.ps1"`（PG/Redis是服务自检→杀旧java→启动jar→等8080→Nginx检查）

### 12.6 排障方法论沉淀

1. **先定位层再动手**：服务器本机 `Invoke-WebRequest http://localhost` 通 → 问题在客户端/网络；不通 → 问题在服务端。netstat里出现外部IP连接记录（如 `172.31.56.160:80 ↔ 113.104.96.6`）证明网络链路是通的。
2. **更新不生效先查运行路径**：进程命令行/启动脚本里的jar路径优先于更新包的放置路径（12.2血的教训）。
3. **接口数据量是快速判据**：userinfo正常几KB，216B=结构残缺；对照"正确基线"字段数量可秒判是权限断层还是数据缺失。
4. **数据问题与代码问题分层**：代码修复后仍异常 → 直查库（`COUNT(*)`）区分"代码bug"和"数据没初始化"（故障9：sys\_menu空表）。
5. **执行SQL前必查编码坑**：中文Windows服务器上执行UTF-8 SQL文件，先 `chcp 65001` + `PGCLIENTENCODING=UTF8`。**遇到疑似坑先搜记忆库**（用户明确要求）。
6. **全角字符陷阱**：用户手输URL易用中文冒号`：`，表象是"网站打不开"，实际浏览器当搜索词。让用户复制粘贴标准URL。
7. **Windows防火墙+安全组双关卡**：外部访问不了80端口，本机放行 `New-NetFirewallRule -DisplayName 'SmartERP-HTTP-80' -Direction Inbound -Protocol TCP -LocalPort 80 -Action Allow` 之外，阿里云还要查安全组入方向规则。

### 12.7 补充：8/22后续修复记录

- **121服务器"新增按钮消失"非复发**：权限修复后用户仅刷新页面未重新登录，localStorage缓存旧权限。铁律：权限相关更新后必须重新登录（清localStorage）再验证。

- **8/22 16:13 权限根因补充**：admin的permissions数组缺通配符`*`，且sys\_menu表只存菜单查看码（如basedata:customer）无操作码。修复：admin角色权限=角色权限∩版本权限，含`*`兜底。

***

## 十三、系统开发全程实录（2026-07-03 \~ 2026-08-22，按时间线）

> 本章按时间线浓缩全部开发史，供追溯"某功能是什么时候做的/为什么这样做"。数据源：记忆库三处（`-d-erp-backup-20260815.zip` 覆盖7/03~~8/15、`-d-erp--p2`目录覆盖8/15~~今、`-d-BOM`为独立BOM项目线）。
> **记忆机制说明**：新会话只自动加载最近一天topics + project\_memory.md，更早内容需按日期目录检索（`Grep pattern="关键词" path=...memory\projects\<项目目录>`）。

### 阶段1：环境搭建与模块奠基（7/03 \~ 7/05）

- **7/03 起点**：PostgreSQL 15.18 + JDK17 + Maven + Spring Boot环境搭建；财务模块选型（自有系统 vs 通用总账）；修复登录按钮无响应；基础资料14子模块可用性检查。

- **7/04**：修复数据库字段差异（08\_fix SQL脚本）；销售模块字段对齐（P0问题）；H2→错误修复（CUSTOMER\_ID列缺失）；293个测试用例跑通；**CRM模块7.2-7.9补全**（销售线索/商机/客户跟进/投诉/满意度5张表，11\_crm\_modules.sql诞生）；基础资料UI改为简约科幻风（紫色竖条+三列布局，方案B独立编辑页）；采购订单字段对齐；CRM 120个自动化测试；**销售额与销售目标管理**（公司年度目标自动增长30%逻辑雏形）；销售5单据E2E；库存5单据编号格式修正（如MOPICK-yyyyMMdd-0001）+状态流转（增加'待审核'中间态）。

- **7/05**：采购模块5单据完整业务规则（PR/PO/PIN/PRT/PINV，60项测试）；**打样管理模块**（参考金蝶用友，打样完成推BOM）；**H2切换PostgreSQL+Redis**（重大架构决策）；版本号记录与回滚功能（AOP快照+反向UPDATE）；CRM表缺失修复；密码体系确认（admin123 vs Erp\@2026 BCrypt）。

### 阶段2：功能大扩展（7/06 \~ 7/10）

- **7/06**：按钮通用组件（buttonConfig.js + Pinia + localStorage持久化）；**下推功能**（ERP标准按钮命名）；关联字段下拉化；BOM模块主从表结构重写（BomList.vue）；图片主图Bug修复；**参考云端ERP**（chaoyuxing.com，ziqian账号）功能对比分析（650页面配置/59关键业务页/16系统设置页/55系统参数）；阶段一完善：状态机与行结案机制（close\_status字段、closeOrder()/reopenOrder()）、数量勾稽体系。

- **7/07**：销售订单E2E（Playwright真实浏览器，34项）；**H2模式彻底清除**（15个H2 SQL文件删除）；生产退料单/补料单；**全局导入导出**（EasyExcel + @ExportField注解 + ExcelService，42个实体）；MRP分析页5步流程改造；BOM导入预检查（缺失物料自动创建选项）；公海池私海功能（19项测试）；五金行业全流程E2E。

- **7/08**：CRM菜单中文化（"CRM管理"→"客户管理"、"CRM客户档案"→"我的客户"）；公海回收规则配置；私海置顶（金色五角星呼吸动画）；**AI小助手全模块接入**（四层架构：前端交互/AI网关/业务代理/基础服务，意图识别{动作}\_{模块}）；sys\_config表首次创建；以图识图功能（AI视觉+四维匹配）；编码单号点击开详情（审批中只读）。

- **7/09**：76页面深度测试（修复销售/采购价格策略API路径）；导入模板Excel格式Bug（EasyExcel head()参数）；**生产工单进度管理**（progress接口+工序汇报）；对比云端ERP重构生产菜单（BOM移基础资料、隐藏工作中心、合并MRP）；千问API Key校验（sk-开头）；采购自动化测试脚本（test-utils.js）；报工校验修复；财务费用分摊；成本核算公式修正（CostCalculationServiceImpl 683-686行）。

- **7/10**：物料图片字段全模块打通（21个明细实体）；组装单/拆卸单确认（当时未开发，8月标准版才补）；即时库存显示修复；委外加工全流程；360行E2E；**AI Agent模式**（思考-行动-观察循环，8个工具注册）；查询LIMIT 20→100修复；RBAC权限拦截测试暴露缺口。

### 阶段3：CRM深化与设置中心（7/14 \~ 7/16）

- **7/14**：8模块自动化测试体系（JUnit5，字段全校验/逻辑关联/设置中心生效/权限硬拦截）；标准ERP验收E2E脚本（清库→纯前端录入→获客到财务收款全流程）；**CRM对标钉钉天元**新增4类模块（数据仪表盘/联系人/销售漏斗/合同+报价+回款，Contact/CrmQuote/CrmContract/CrmPayment实体）；全部功能注册为AI工具（动态注册去重）；AI图像识别E2E。

- **7/15**：委外8张表补建；图片下单+BOM文件转换+图片转BOM三功能（方案A）；打印模板系统（print\_template三张表+PrintDataFetcher）；CRM正式客户自动转档案（upgradeCustomerToFormal()）；基础编码自动编号修复；16种单据默认打印模板；打印模板可视化配置（左右布局迭代）；千问增删改查后台权限控制；25个一级业务模块功能梳理；打样进度字段；**销售合同+采购合同单据**（下推+选单）；36项全链路E2E验收；商品明细成本字段（MaterialCostService，已审批锁定成本）；销售订单UI科技风改造（紫蓝渐变#6366f1→#8b5cf6）；选单"已转数量"回写（turned\_order\_quantity）；价格策略毛利率；设置中心UI简化（13项→5大类）。

- **7/16**：**学习库管王ERP**（v3.kuguanwang.com，18674725773账号）设置中心1:1复刻；送货单打印模板（A4标准格式）；设置中心16页面UI复刻+业务联动验证；自定义字段功能（sys\_custom\_field EAV + ext\_data JSONB混合存储，25项测试）；设置中心入口整合（侧边栏菜单替换为头部齿轮+用户下拉）。

### 阶段4：体系化重构（7/17 \~ 7/21）

- **7/17**：**CRM去重定位**（删除CRM的报价/合同/回款，改用销售模块单据打通，双向opportunityId关联）；23张报表+15项缺失功能清单；6大报表中心；报表权限按角色过滤+明细独立；提成率历史版本留痕（CommissionRate实体）；阿里云三档部署方案文档；AI保密物料隔离（isConfidential字段）；21张标准台账报表（对标云端ERP+库管王）；报表三级子菜单结构；销售订单下推采购订单（push-to-purchase端点）。

- **7/18**：打印模板用户自主设置（字段key匹配修复spec→specification）；保密物料前端开关；设置中心问号→感叹号图标；**全部单据UI按销售订单1:1复刻**（12个Edit页48/48+156/156验证）；审批功能改造（模块可展开/单据开关/无限级审批/审批人行内选择/条件模板/通知表）。

- **7/19**：生产模块列表页科技风补全；委外5单据选单补全；**列设置功能**（列顺序/显隐/固定+localStorage）；发票选单互斥（单一路径+退货红字发票）；获客模块风鸟平台对接（企查查API收费0.05\~0.5元/次放弃，改风鸟）；采集服务JWT自动登录；列设置全量推广39个列表页。

- **7/20**：获客二维码加载修复（3005端口）；浏览器单例冲突（userDataDir分离）；发票互斥选单提前提示；地区过滤bug（String.includes误伤）；**工作台个人任务视角**（待我审批/即将超期/我的草稿/应收应付）；cookie自动检测；6问题批量修复（销售合同点击/批次库存/库存流水）；**系统主题色自定义**（theme.js 7套预设+11颜色字段+CSS变量）；**Cron可视化构建器**（秒分时日月周下拉组合）；商机管理按单据风格重写；设置中心与菜单重复功能清理（4处去重）。

- **7/21**：工作台直接审批+批量审批；BOM明细底部保存按钮；库存/生产模块UI复刻补漏；物料明细子表列设置（复合pageKey）；日志字段变更明细接口；`[object Object]`显示修复（formatDateTime参数错误）；通知铃铛+header-right布局（flex横向12px间距）；22个Edit页列设置；**License模块部署**（MAC绑定65天）；客户阿里云账号混淆排查（控制台vs SSH两套体系）。

### 阶段5：首次云部署（7/21 \~ 7/25，Ubuntu→Windows转型）

- **7/22**：Docker镜像拉取失败（DNS解析）→放弃Docker直装；**Ubuntu 22.04部署**（erp\_user/Erp\@2026账号诞生）；JWT配置缺失修复；Nginx 403（前端复制路径错）；"No static resource auth/login"（proxy\_pass斜杠剥离/api前缀——**此坑8/21在8服务器重踩**）；sys\_user缺password\_salt列；171张表全量核查（Java实体 vs SQL文件）；解压部署包deploy.sh修复；License 65天生成；init\_data.sql导入；服务器字段缺失连环修；**服务器Ubuntu→Windows转换**（客户只有C盘，路径全改）；GBK编码坑首现（05\_sys\_serial\_no\_config\_init.sql）；License签名Base64'+'变'-'坑；ClassFinal加密方案（AES加密class字节码）。

- **7/23**：License文件覆盖/签名验证连环修复；admin用户不存在（INSERT全失败根因：SQL字段名与表结构不匹配）；`current_date`关键字语法错误；UTF-8 BOM；jar名smart-erp-1.0.0.jar确认；**8.148.229.2首次出现**（客户阿里云）；空系统GET请求不弹错（MainLayout的getSysConfigByGroup+NotificationBell）；一键更新deploy.bat；SSH密码认证/OpenSSH修复；**最终放弃SSH改RDP复制**；开机自启（任务计划"SmartERP AutoStart"）；8080端口占用；健康检查401；数据库初始化与实体不同步根因确认（init.sql 93表 vs 本地171表）。

- **7/24**：complete\_database\_schema.sql（173张表）；自定义字段全模块适配；代码健康度72/100；三行业E2E验证（化妆品/制造/五金）；v-html XSS加固（DOMPurify）+console.log清理+ESLint；**.env.customer隐藏获客+财务**（VITE\_HIDDEN\_MODULES诞生）；新手教程组件（OnboardingTutorial遮罩+高亮+气泡）；license.lic替换流程。

- **7/25**：bat闪退排查（PowerShell执行保持窗口）；新手教程手动触发；**121.40.168.247客户服务器登场**（MAC 00-16-3E-66-B9-C3，license至2026-12-31，50用户）；erp\_db库名确认；**域名rhtags.cn绑定**（A记录→121.40.168.247）；license改2个月（至2026-09-25）；企查查采集jQuery伪选择器bug；微信小程序版ERP（uni-app）。

### 阶段6：打磨与1.0.x版本（7/30 \~ 8/03）

- **7/30**：**域名备案**（rhtags.cn未备案被阻断）；设置中心开关后端逻辑补全（inventory.negative.allowed/sale.price.lowest.check等全量）；小程序10项验收；操作日志中文化（BillOperateLogAspect时序修复——先更新后取旧数据Bug）；新手教程tooltipPosition未定义；生产加工单多产品明细（WorkOrderProduct实体）；设置中心174项E2E全通过；**快递100物流查询对接**（Kuaidi100Service）；来源单号独立注入（SourcePicker每行明细）；选单按钮17页面统一。

- **7/31**：**编码自动生成**（sys\_serial\_no\_config.auto\_generate，默认自动客户可改手动）；权限动态变化机制（menuIds动态菜单+路由守卫+SecurityUtils数据过滤）；12默认角色确认；docker-compose的full\_schema.sql致命问题发现。

- **8/02**：设置中心44用例E2E全通过；版本号功能确认。

- **8/03**：发布风险评估（3项高风险变动：后端权限校验/basedata合并/公共组件抽取）；LogisticsTracker模板嵌套错误修复；**获客模块剔除验证**（菜单过滤+路由守卫双重）；bat编码UTF-8+LF坑（转GBK+CRLF）；**角色权限中文化开端**（05\_fix\_roles\_and\_permissions.sql错误改role\_code教训：ADMIN→SUPER\_ADMIN是灾难）；Windows环境记忆重核（清理旧docker记忆）；05文件误删数据恢复；1.0.3部署包。

### 阶段7：通用状态机与组件化（8/14 \~ 8/16，P2项目目录开始）

- **8/14**：生产服务器后端重启；"新建审批流程"按钮无响应（ApprovalFlowList双实例合并v-show）；编码自动生成规则确认；后端API全流程测试；**销售订单下推失灵根因**（占位代码`case 'push': break`遗忘补全）；物流看板配置入口丢失（SysConfig.vue重定向SettingsCenter.vue）；**演示数据生成**（朋友圈截图用）；快递100授权方式确认。

- **8/15（两目录衔接日）**：演示数据完成；销售发货单"关联数据不存在"；多租户数据隔离方案讨论（开端）；单据号编辑状态控制；登录页记住密码+默认账号移除；A2仓库智能默认+B全局必填标红+C提交前置校验；**通用状态机体系**（金蝶用友标准，9个Phase：通用状态枚举/抽象基类/接口体系/DDL/前端通用组件）；快递100真实单号修复（.do后缀/key签名/手机后四位/HTML拦截）；purchaseContract.js重复声明；列设置固定定位→自适应；本地启动环境变量（ERP\_LICENSE\_ENABLED=false/ERP\_JWT\_SECRET）。

- **8/15晚\~8/16（P2目录）**：**角色中文化**（12角色role\_name改中文+admin real\_name）；**销售角色数据隔离**（销售只能看销售数据）；23项状态机API验证（草稿→待审核→已生效→已结案/作废+下游引用校验AR阻止重置草稿）；列设置MutationObserver修复；销售订单仓库必填去除；**BillActions通用按钮组件**（表头统一+状态控制显隐）；表格列设置图标重叠修复；组件化架构诊断（重复视图/实体重复/魔法字符串/下推分散）；销售模块4类缺陷（watch时序ReferenceError）；操作日志aroundWrite时序修复；**42个编辑页按钮统一改造**；按钮truthy Bug（canReverseApprove等11按钮常显）；模板演示案例（exportTemplate生成示例数据）；登录空白排查；员工档案admin污染修复+修改密码；用户名查重450ms防抖；176实体类0缺表0缺列验证；**Pinia store不同步Bug**（user.js只写localStorage不写state→菜单消失）；**系统名称logo自定义**（/api/sys-config/public/basic诞生——注意：8/21在8服务器发现该端点401，本地有但部署包jar缺）；**密码明文安全问题**（persistRemembered改账号）；多单据页签开端；客户部署清单+25项冒烟；**121服务器1.0.4部署**（apply-update闪退/编码错误/License RSA公钥）；12默认角色跨模块权限；1.0.5重构（物流看板+多页签+权限bug）；7个SQL迁移失败修复；Nginx停止致登录失败；权限英文残留；**自启动守护包**（smart-erp-autostart-setup.zip）；**域名SSL证书**（rhtags.cn A记录+安全组80/443）；权限字段标准中文值；角色"未设置"修复；物流单号500；CRM销售目标挂钩确认（员工✓订单✗）；无权访问租户；CRM销售目标多指标（33\_sales\_target\_multi\_metrics.sql）。

### 阶段8：审批体系、多租户与双服务器攻坚（8/17 \~ 8/22）

- **8/17**：审批人列表空+**行内下拉搜索**（禁止弹窗，KgwApproval.vue非ApprovalFlowList）；HTTP域名登录"No static resource"（proxy\_pass斜杠二次踩坑）；角色名称英文残留；**26个单据Service审批改造**（4子代理并行）；提交按钮无反应（9模块5子代理）；1.0.6按钮状态+bill\_before\_status缺列；本地License RSA公钥.env；审批开关未接后端+数据库开关实际开启根因；**客户服务器前端真实根目录**（C:\smart-erp\frontend，全盘搜旧JS文件法）；审批全局总开关UI补全；**重置草稿不受审批开关控制**（业务死循环：已生效→无法重置草稿→无法删除）；无权访问租户升级包；销售角色全菜单（localStorage缓存+getMenuGroupsByRoles）；**1.0.8闪退复发**（LF换行+UAC VBS+8.3短路径新根因）；多页签丢失（发布树prodsrc与开发树real分叉）；Caffeine缓存旧approval.enabled+旧JAR。

- **8/18**：1.0.9（设置中心刷新死循环/导入模板演示数据/编码自动编号/图片不显示）；**START-ALL一键启动**；37项需求验收（DictSelect手动输入/备注全模块）；**物料分类4级编码**（E系列/01型号码/001芯片/0001批次，各级独立存储路径拼接）；bat中文注释GBK闪退重写；物料分类导入模板；下载模板500；物料图片列；**单据提交语义定版**（保存=草稿，提交=直接生效或走审批）；多页签+keep-alive三层刷新机制（89列表页自动刷新）；**开发模式定版**（mvn spring-boot:run源码直跑，禁package）；私海导入；93 List+58 Edit按钮三重扫描；Token迁移+记住账号（密码不进localStorage）；多级基础资料分组页（左分类树右详情）；自动编码模板列去必填星号；销售订单排序实测；**登录页科技感改造**（动态HTML背景+手机验证弹窗+slogan"去掉一切不创造价值的环节"）；**19页面销售员/采购员默认当前账号**。

- **8/19**：登录页Logo位置+记住账号✔图标（Element Plus el-checkbox抛弃改原生+U+2714）；**物料选择器快照过期**（useBaseDataRefresh切页签重拉）；登录页"optim()"幽灵文字；物料状态乱码（æ­£å¸¸编码问题）；**财务总账注释**（/finance/gl/vouchers路由+AiAssistant映射+voucher.js全注释，恢复取消注释即可）；排序功能70列表页+CrudTable排序触发；**5GB磁盘清理**（9.5GB→4.53GB）；图片导入+查重失效；verify.bat编码；**知识库第十一章**（金蝶用友规范，定位=纯参考）；BOM展开按钮+虚拟库存（订单占用+发货校验口径）；**CrudTable全局CSS污染**（.erp-page裸选择器→:has(.erp-card)收窄，"首次正常切回异常"布局bug通杀）；Caffeine缓存查重旧值（绕过缓存直查）；1.0.8打包铁律（.NET ZipArchive禁Compress-Archive/robocopy禁Copy-Item/bat纯ASCII+CRLF）；41\_entity\_columns\_safety\_net.sql（13模块30+列）；系统名称覆盖修复（ON CONFLICT DO NOTHING）；**1.1.0部署包**（70.2MB 433文件）；获客服务三处端口一致（3005）；员工表恢复失败（PLAIN格式erp\_db.sql）；**收款发货控制**（V1作废不回减/V2账期超期阻断——收款单核心业务规则）；获客start-acquisition闪退（src/app.js+依赖+浏览器）。

- **8/20**：V1/V2修复实施（reverseReceipt）；**获客4平台**（企查查/风鸟/天眼查/爱企查）；4平台隔离（login\_qcc/fnb/tyc/aqc）；扫码误报修复（URL变化→实际验证）；基础资料3bug；单价校验el-input-number失焦；BOM展开selectOne 6结果（material\_code不唯一）；采集登录10年开发审查；系统名称标签页同步（Key不匹配appConfig\_cache\_）；**多租户正式实施**（独立数据库模式+组装拆卸标准版+客户管理后台）；客户管理代码审查（axios→@/utils/request）；获客5大优化（Cookie 7天过期）；**创建租户失败连环修**（sys\_user不存在→SQL外键顺序→psql逐句执行）；升级回滚脚本审查；本地数据完整性审查（175表零差异+24 API 100%）；**平台管理不发布121**（customer profile排除com.erp.platform+VITE\_PLATFORM\_ADMIN）；TenantService 7个严重问题（executeViaJdbc只执行第一条）；平台管理≠获客管理（用户纠正理解错误）。

- **8/21**：平台管理打包机制（-P customer物理排除）；**1.1.0部署121**（SQL 52菜单对齐+admin/MANAGER权限'*'）；多租户权限验证（tenant\_admin\_s 81菜单/tenant\_admin\_c 100/ULTIMATE 117）；**独立部署开关**（erp.tenant.version-control-enabled=false默认，121不受租户模式影响）；8开头服务器复活（100元充值3-4天按量/1个月包月）；121旗舰版全模块（VITE\_HIDDEN\_MODULES只留platform）；admin员工档案关联（employee\_id=16）；**menuRoutePaths不落盘修复**（Login.vue+TenantSelect.vue的syncUserLanding补保存）；收款单UI重构（应收导向+核销闭环）；**收款核销配置驱动**（sale.delivery.payment.control：full/advance锁订单预收，none锁发货核销，bypass特殊审批）；**收款付款接入状态机**（10个端点+PAYMENT\_RECEIPT/PAYMENT\_VOUCHER注册+53号SQL）；8服务器部署（Nginx conf清理+start\_all\_silent+自启）；Nginx 502；后端exit=1（sys\_config缺3列）；SchemaUpgradeEnsurer诞生；**AI脱敏**（AbstractOpenAiCompatibleAdapter出口SensitiveDataMasker，AI回答显示138*\*\*\*0000）；**8服务器部署大坑**（详见第十二章：jar路径不一致/sys\_menu空表/GBK编码/全角冒号）。

- **8/22**：121权限缓存（重新登录铁律）；8服务器menuRoutePaths=117验证通过；**本知识库第十二/十三章沉淀**。

- **8/31**：**部署链路可靠性四件套上线**（详见第十四章）：MD5传输校验+预暂存原子切换+回滚闭环健康验证+agent单一事实源收尾。

### 关键架构决策时间点（速查）

| 决策                  | 时间            | 内容                                             |
| ------------------- | ------------- | ---------------------------------------------- |
| H2→PostgreSQL+Redis | 7/05          | 彻底清除H2（7/07完成清理）                               |
| CRM定位收敛             | 7/17          | 删CRM报价/合同/回款，用销售模块打通，opportunityId双向关联         |
| 设置中心1:1复刻库管王        | 7/16          | KgwXxx.vue系列，16页面                              |
| 单据UI统一复刻销售订单        | 7/18\~7/21    | 12→22→42个Edit页，BillActions/BillRowActions      |
| 通用状态机体系             | 8/15          | 金蝶用友标准，AbstractBillService基类，前端通用组件            |
| .env.customer隐藏模块   | 7/24          | VITE\_HIDDEN\_MODULES（8/21改为只留platform）        |
| License体系           | 7/21\~7/25    | MAC绑定+ClassFinal加密+RSA公钥                       |
| 开发模式定版              | 8/18          | mvn spring-boot:run源码直跑，仅部署时package            |
| 多租户                 | 8/15提出→8/20实施 | 独立数据库模式，TenantRoutingDataSource                |
| 版本控制开关              | 8/21          | erp.tenant.version-control-enabled，默认false独立部署 |
| 收款核销锁定              | 8/21          | 配置驱动Tab锁定，单据单核销类型                              |
| 平台管理不发布             | 8/20\~8/21    | -P customer物理排除+VITE\_PLATFORM\_ADMIN          |

***

## 十四、部署链路可靠性体系（2026-08-31，游戏级更新四件套）

> 目标：把"停服→逐文件拷贝→祈祷"升级为游戏更新式"校验→预暂存→原子切换→失败自动回滚并验证"。改动集中在 agent（update-agent.ps1，单一事实源=backend resources）+ DeployService/DeployVersionService + Publish.ps1，无数据库 schema 变更、无前端改动。

### 14.1 已完成能力（agent-version 20260831.01）

| # | 能力                   | 机制                                                                                                                    | 失败语义                                                           |
| - | -------------------- | --------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| 1 | **MD5 传输完整性校验**      | 打包侧算 zip MD5（Publish.ps1 / DeployService 上传前计算；版本库已有 zip\_md5），经 `-ZipMd5` 参数传给 agent；agent 第一步比对 incoming.zip 实际 MD5 | 不匹配 → exit 4，**零触碰**（服务器上一个字节不动）                               |
| 2 | **本地包完整性校验**         | 版本库部署前，实际 MD5 与 deploy\_version.zip\_md5 比对                                                                           | 不一致（本地包损坏/被改）→ 拒绝部署                                            |
| 3 | **zip 解压 CRC 防护**    | .NET ExtractToDirectory 包 try/catch；解压后校验 smart-erp.jar / frontend 目录存在                                               | 失败 → exit 4（仍处于零触碰阶段）                                          |
| 4 | **预暂存（停服前准备）**       | jar 先拷成同目录 `smart-erp.jar.new`；每个前端目标在旁边构建 `<t>.__incoming`（= 现目录全量拷贝 + 新文件覆盖，nginx html 里的非ERP文件得以保留）                | 停服窗口从"拷贝几分钟"缩到"改名毫秒级"                                          |
| 5 | **原子切换**             | jar 同目录 `Move-Item` 改名；前端两次目录改名（`t → t.__prev`，`t.__incoming → t`）                                                    | rename 失败（文件被锁）→ **自动降级为原逐文件拷贝**，不硬失败                          |
| 6 | **回滚闭环验证**           | 健康检查失败自动回滚后，**再跑一轮健康检查**（60s）                                                                                         | 回滚后健康 → exit 2；仍不健康 → exit 3（日志大写报警 MANUAL ATTENTION REQUIRED） |
| 7 | **手动 Rollback 也验健康** | Rollback 模式重启后同样跑健康检查                                                                                                 | exit 0=回滚且健康；exit 3=回滚后不健康                                     |
| 8 | **成功后清理**            | 删除 `__prev`/残留 `__incoming`；备份目录只保留最近 5 份                                                                             | 防磁盘塞满                                                          |
| 9 | **agent 版本标识**       | agent 日志开头打印 `agent 20260831.01`                                                                                      | 一眼识别服务器上跑的 agent 是否过期                                          |

### 14.2 退出码口径（DeployLog 状态映射）

| exit | 含义                                | 部署日志状态       |
| ---- | --------------------------------- | ------------ |
| 0    | 部署成功且健康                           | SUCCESS      |
| 2    | 部署失败已自动回滚且回滚后健康                   | ROLLED\_BACK |
| 3    | 已回滚但服务仍不健康（需人工介入）                 | FAILED       |
| 4    | 输入/完整性问题（MD5不符、zip损坏、缺参数），**零触碰** | FAILED       |

### 14.3 关键设计边界

- `-ZipMd5` 为可选参数：旧调用方（手动跑 agent）不传 = 跳过校验并记日志 `md5 verify SKIPPED`，**向后兼容**。

- `__incoming/__prev` 是前端目标的**同级兄弟目录**（同卷 rename 才原子，不跨盘）。

- 回滚兜底三层：① rename 回滚（秒级）→ ② 目标目录整个缺失时从时间戳备份全量重建 → ③ 无 `__prev`（file-copy 降级路径）时从备份逐文件覆盖。时间戳备份目录**永远不删**（成功路径只 prune 到最近5份）。

- agent 脚本**纯 ASCII**（GBK 误读坑），已通过 PowerShell 语法解析校验（parseErrors=0）。

### 14.4 文件清单

| 文件                                                     | 角色                                                                         |
| ------------------------------------------------------ | -------------------------------------------------------------------------- |
| `backend/src/main/resources/deploy/update-agent.ps1`   | **单一事实源**（DeployService 每次部署从 classpath 推送；Build-Package/Publish 打包时从源码复制） |
| `deploy/server/update-agent.ps1`                       | 副本，已与事实源字节同步（2026-08-31 起消除漂移）                                             |
| `backend/.../deploy/service/DeployService.java`        | 上传前算 MD5 + 传 `-ZipMd5` + 日志打印 md5                                          |
| `backend/.../deploy/service/DeployVersionService.java` | 版本库部署前本地 MD5 完整性校验                                                         |
| `deploy/Publish.ps1`                                   | 打包后算 MD5，runner 调用带 `-ZipMd5`                                              |

### 14.5 验收要点（下次部署看日志）

agent 日志应依次出现：`agent 20260831.01` → `md5 verified ok` → `zip extracted + validated` → `pre-stage: jar` → `pre-stage: frontend incoming dir ready` → `jar swapped (rename)` → `frontend swapped (rename)` → `schema sync ok` → `healthy after Ns` → `=== UPDATE SUCCESS ===`。

### 14.6 8cloud 实测纪实（2026-08-31 00:29\~00:45，三次投递终成功）

第一次：schema guard 拦截（8cloud 缺 `bank_account`/`custom_dashboard` 两表）→ 远程幂等 SQL 补表后过。第二次：agent 20260831.02 SQL 门禁误报失败（exit 5）→ 实为 `Invoke-SqlFile` 函数返回值被 `L()` 的 Write-Output 污染（数组 `-ne 0` 是过滤比较，恒真）→ 修复为 `$script:SqlRc` 传递（agent 20260831.03）。第三次：全绿成功——`md5 verified ok` → SQL 门禁 14 个脚本全过 → `pre-stage` → `jar swapped (rename)` + 两处 `frontend swapped (rename)` 同秒完成 → healthy → 清 `__prev` + 备份 prune 保留5份 → 公网 login/userinfo 200 菜单130项通配在。

**新坑沉淀（永不重犯）**：

1. **PowerShell 函数返回值污染**：函数内调用含 `Write-Output` 的日志函数（如 L），日志行会混入函数返回值；调用方 `(func x) -ne 0` 对数组做的是**过滤比较**（非空数组=$true）→ 成功也判失败。退出码必须用 `$script:变量` 或先捕获再输出。
2. **8cloud 部署后 502 复发（servers.json 漏改）**：8-28 根治只改了 deploy\_server 表的 start\_command=schtasks，**本地 servers.json（Publish.ps1 用）还是旧的 start-backend-8cloud.ps1（SSH 会话链）→ 会话断进程死**。两处配置口径必须同步改。已改 servers.json 8cloud startCommand → `schtasks /run /tn SmartERP-Backend`。
3. **agent 的 exit 5 阶段"零触碰"只对文件成立**：SQL 门禁在文件改动前执行但 SQL 本身已落库（菜单同步非事务）——SQL 门禁失败重跑是安全的（全幂等），但要知道 DB 已被写过。
4. 远程诊断技巧复用：本地写 ASCII 名 ps1 → scp → ssh -File 执行（SSH 内联引号必坏）；agent 日志 `C:\smart-erp-update\update-agent.log`，stage 目录在 exit 5 中止后保留可直接复现。

***

## 十五、标准台账下线与报表中心模块现状（2026-08-31）

### 15.1 下线决策与范围

用户判定「标准台账没啥用」（与自定义报表功能重叠、维护成本高），整体删除：

| 层      | 删除内容                                                                                                                                                                                                           |
| ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 后端     | 5 个 Controller：Sale/Purchase/Inventory/Production/Finance StandardReportController                                                                                                                             |
| 后端（保留） | 客户/供应商对账单端点搬入新 Controller（CustomerStatementController / SupplierStatementController），**URL 不变**（`/api/report/standard/sale/customer-statement/*`、`/api/report/standard/purchase/supplier-statement/*`），前端零改动兼容 |
| 前端页面   | StandardReportCenter.vue、StandardReportPage.vue、ModuleReportHub.vue、standardReports/ 目录                                                                                                                        |
| 前端路由   | 21 条（/report/standard 及各模块 /xxx/report/\*-detail 等）                                                                                                                                                            |
| 前端菜单   | menuConfig.js 20 项（销售/采购/库存/生产/财务下的明细表菜单 + 报表中心「标准台账」）                                                                                                                                                         |
| 前端 API | standardReport.js 瘦身为仅 6 个对账单函数                                                                                                                                                                                |
| 数据库    | `48_drop_standard_report_menus.sql`（幂等，进 manifest）：删 sys\_role\_permission 残留 + sys\_menu 20 条；`46_custom_report_menus.sql` 去掉标准台账菜单再插入逻辑                                                                      |
| 手册     | 14-报表中心模块操作手册.md 整体重写（旧版大量描述未实现的虚构功能：对账单PDF/状态机/差异调整等，新版全部按真实代码编写）                                                                                                                                             |

### 15.2 报表中心模块现状（已完成功能清单）

| 功能                | 状态                | 说明                                                                                                                                                                         |
| ----------------- | ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 自定义报表（列表/设计器/运行）  | ✅ 已完成             | 明细+汇总双模式、字段搜索、聚合指标（SUM/COUNT/AVG/MAX/MIN+环比%）、计算列（+−×÷+常量）、跨单关联（平铺+聚合挂载）、两跳追溯 via、筛选预设、共享范围、图形/数据双页签、导出Excel                                                               |
| 自定义仪表盘（列表/设计器/查看） | ✅ 已完成             | 多报表卡片组合、卡片级错误隔离、工作台自动嵌入默认仪表盘（仅汇总+图表卡片≤4张）                                                                                                                                  |
| 客户对账单             | ✅ 已完成             | 汇总/明细双页签、点击行下钻、合计行、导出Excel；入口=销售→报表                                                                                                                                        |
| 供应商对账单            | ✅ 已完成             | 与客户对账单对称；入口=采购→报表                                                                                                                                                          |
| 销售发票报表            | ✅ 已完成（2026-09-02） | 5个接口 `/api/report/sale/invoice/{overview,trend,customer-analysis,tax-analysis,details}`（SaleReportController），前端 SaleInvoiceReport.vue 已从 mock 切换真实接口；口径见 15.4             |
| 采购发票报表            | ✅ 已完成（2026-09-02） | 4个接口 `/api/report/purchase/invoice/{supplier-analysis,payment-status,tax-analysis,details}`（PurchaseReportController），前端 PurchaseInvoiceReport.vue 已从 mock 切换真实接口；口径见 15.4 |
| 标准台账（6域×20+报表）    | ❌ 已下线             | 需求由自定义报表承接；旧菜单已删，老服务器经正常部署（SQL Kit 自动跑 48 脚本）完成清理                                                                                                                          |
| AI 建报表（自然语言→报表配置） | ✅ 已完成             | 意图识别全 DeepSeek，含三件套/两跳模板铁律（详见 project\_memory 2026-08-29/30 条目）                                                                                                            |

### 15.4 发票报表数据口径（2026-09-02 实装，改动前必读）

- **通用**：区间解析 startDate/endDate 优先，否则按 year（默认当年）；金额统计排除 status='已作废'（状态为空保留）；明细列表含作废发票；空数据一律返回 0；全部内存聚合（selectList 后 stream）。

- **销售发票**（SaleInvoice：totalAmount=价税合计/netAmount=净额/taxAmount=税额/taxRateAvg=百分数值如13）：回款率 = account\_receivable 按 invoice\_no 关联（排除已作废AR）Σpaid\_amount/Σtotal\_amount；税务分析按 taxRateAvg 分组（13→'13%'，null→'未税率'），分布金额取净额（保证 税额/金额=税率）；SALES 角色按 salerId 数据权限过滤。

- **采购发票**（PurchaseInvoice 字段映射特殊：Java `amount`→列 total\_amount=不含税金额、`taxAmount`→tax\_amount、`taxTotalAmount`→列 net\_amount=价税合计，实体无税率字段）：税率按 税额/金额\*100 四舍五入推算分组；付款状态用 account\_payable 按 invoice\_no 关联（无AP=未付款、Σ余额≤0=已付款、有已付=部分付款）；应付总额=关联AP的Σtotal\_amount+未关联发票价税合计；逾期=AP余额>0且due\_date<今天，按0-30/31-60/61-90/90+分桶；KPI环比=与上一等长周期真实对比（无上期数据记+0.0%）。

- **前端**：SaleInvoiceReport.vue / PurchaseInvoiceReport.vue 已删除全部 invoiceReportMock import；两页此前 mock 接线本身有字段错位（图表实际渲染空），现按真实响应结构适配（周趋势映射 periods/invoiceCounts/amounts，税率标签直接用后端 '13%' 字符串，采购KPI用后端 kpiCards 千分位字符串）。

### 15.5 下线类改动的标准动作（可复用清单）

1. 端点有存量消费者时，先搬家再删类（新 Controller 沿用原 @RequestMapping，前端无感）。
2. 菜单删除必须连权限残留一起清（sys\_role\_permission.menu\_id 存 route\_path）。
3. 清理脚本进 `sql/once` manifest，**必须幂等**（本例 DELETE ... WHERE menu\_code IN (...)，连跑两遍第二遍 0 行验证过）。
4. 菜单同步脚本（46）同步去掉再插入逻辑，否则部署后菜单复活。
5. 操作手册重写而非小修：旧手册里虚构功能必须整体清除，按现存代码逐页核实。

***

## 十六、数据看板模块（2026-09-01，源自D盘《广州日晖智能科技有限公司ERP数据看板.docx》需求）

### 16.1 功能定位与结构

新增独立模块"数据看板"（左侧菜单，位于工作台之后），含三个子页面，全部指标取自真实单据表，零编造：

| 页面   | 路由                   | 后端接口                                                    | 核心指标                                                                                              |
| ---- | -------------------- | ------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| 财务看板 | /databoard/finance   | GET /api/data-board/finance?period=month\|quarter\|year | 营收(含税/不含税)/成本/毛利/净利润/三费(销售/采购/管理)/税金/应收余额/逾期/账龄分布/应收周转天数/现金流入流出/目标达成率(预警<85%)/近12月趋势/产品线毛利        |
| 销售看板 | /databoard/sale      | GET /api/data-board/sale                                | 今日/本月/期间销售额/订单数/客单价/目标达成率/回款率/逾期TOP客户/商机储备(未关闭)/产品线分布/客户等级分布/新老客户/热销滞销TOP10/销售费用率                 |
| 仓储看板 | /databoard/inventory | GET /api/data-board/inventory                           | 库存总金额/总数量/期间出库/周转率(年化)/周转天数/库龄>90天金额/呆滞物料(库龄>180天且从未出库)/缺货SKU(低于安全库存)/平均可销天数/物料类型与产品线结构/近6月出库vs销售 |

### 16.2 数据口径（关键，改动前必读）

- **营收/毛利**：`sale_order` status='已生效'，order\_date 落区间；不含税营收**以明细 sale\_order\_item.net\_amount 为准**（订单头 net\_amount=明细合计，二者只取其一，历史上踩过头+明细双计虚高一倍的坑）；成本=明细 avg\_cost（空则取 inventory\_current 移动平均成本兜底再取最新核算成本）。

- **费用三单**：sale\_expense / purchase\_expense 的 **status 是 integer 数字枚举**（0草稿/1待审/2已生效/3已分摊/4作废），有效口径 `IN (2,3)`；operation\_expense（费用分摊）status 是 varchar '已生效'（与 FinanceReportController 现有口径一致）。

- **收付款**：cash\_receipt / cash\_payment（实体 PaymentReceipt/PaymentVoucher 的 @TableName），status varchar，有效口径 IN ('已确认','已生效')。

- **应收**：account\_receivable（@TableName 单数），未结清=status NOT IN ('已结清','已作废')。

- **库存**：inventory\_current（实体 Inventory）；库存台账=inventory\_ledger（实体 InventoryTransaction）。

- **销售目标**：sales\_target\_company.target\_year/annual\_amount，期间目标=年目标折算（月/12、季/4）。

- **period 白名单**：month/quarter/year，其余一律按 month。

### 16.3 权限与接入清单

- 菜单授权角色：ADMIN/FINANCE/FINANCE\_MANAGER/SALES\_MANAGER/WAREHOUSE\_MANAGER/PURCHASE\_MANAGER/PRODUCTION\_MANAGER（财务看板含净利润敏感指标故不放开全员；SUPER\_ADMIN 走 '\*' 通配）。前端 menuConfig.js roles 与 DB 授权保持一致。

- 接入文件清单：后端 DataBoardController/DataBoardService/DataBoardServiceImpl（com.erp.dashboard）；前端 api/dashboard/dataBoard.js、views/databoard/{FinanceBoard,SaleBoard,InventoryBoard}.vue、router/index.js（3路由+降级map '/databoard'）、menuConfig.js（databoard分组）、MainLayout.vue（DataAnalysis 图标）；DB 脚本 49\_data\_board\_menus.sql（幂等，已进 deploy-kit-manifest）。

- 新增菜单四件套已完成：sys\_menu 插入（CATALOG /databoard + 3 MENU，sort\_order=75）+ sys\_role\_permission 授权（menu\_id 存 route\_path）+ manifest 登记 + 本地库执行两遍幂等验证。

### 16.4 本次踩坑记录（防复发）

1. **费用单状态列类型陷阱**：sale\_expense.status 是 integer 不是 varchar，`.eq("status","已生效")` 会报 `integer = character varying` 操作符不存在（即使表为空也报）。写 QueryWrapper 前先查 information\_schema 确认列类型；跨表查询前先确认实际表名（PaymentReceipt→cash\_receipt，InventoryTransaction→inventory\_ledger，AccountsReceivable→account\_receivable）。
2. **订单头/明细金额重复累计**：sale\_order.net\_amount 与明细合计同值，聚合时只取一头。
3. **明细行物料名称可能为空串**（历史数据）：展示物料名必须从 material 档案兜底解析，不能只信明细行冗余字段。
4. PS 控制台输出中文 JSON 会乱码显示（显示层问题非数据问题），验证物料名等中文字段时以 psql -f UTF8文件 为准。

### 16.5 单元测试（2026-09-01 补充）

- 测试文件：`backend/src/test/java/com/erp/dashboard/service/impl/DataBoardServiceImplTest.java`，JUnit5+Mockito 纯单元测试（Mock 全部 16 个 Mapper 依赖，不连数据库），15 个用例分 7 个 @Nested 组，全部通过。

- 覆盖回归点：头/明细重复累计（防翻倍复发）、物料名称四级兜底、费用单数字枚举 IN(2,3)（ArgumentCaptor 校验 QueryWrapper）、账龄 5 档分桶边界、目标折算（年/12、年/4）、period 白名单、全空数据除零保护。

- 运行命令：`mvn test -Dtest=DataBoardServiceImplTest -Derp.test.skip=false`（必须带 -Derp.test.skip=false，见下）。

### 16.6 单元测试基建踩坑（防复发，影响所有模块的测试）

1. **pom 里 surefire 写死** **`<skip>true</skip>`** **无法用命令行覆盖**（XML 显式配置优先于 -D 属性）。已改为 `<skip>${erp.test.skip}</skip>` + properties 默认 `true`：日常打包行为不变（仍跳过测试），显式跑测试加 `-Derp.test.skip=false`。
2. **Amazon Corretto 17 禁止 ByteBuddy 自附加**：Mockito 内联模拟器初始化报 `Could not self-attach to current VM`，全部用例爆 MockitoInitializationException。已在 surefire 配置 `<argLine>-Djdk.attach.allowAttachSelf=true</argLine>` 修复（此前 65 个存量测试类在本机从未真正跑过）。
3. **MyBatis-Plus QueryWrapper 参数惰性求值**：`in()/eq()` 只是注册 lambda，`formatParam` 把值写入 `paramNameValuePairs` 发生在 `getSqlSegment()` 被调用时。用 ArgumentCaptor 校验 wrapper 参数前必须先调 `w.getSqlSegment()`，否则 map 恒为空（测试类里有 wrapperHasParams 辅助方法）。
4. 主代码偶发"找不到符号 entityMetadataRegistry"编译错误：并发编辑半写入状态所致（文件 21:46 被外部修改），重跑即好，非代码缺陷。

***

## 十七、写操作存在性校验修复（2026-09-02，运行时探针安全审计）

> 背景：用不存在的 ID 调写接口，97 个返回"操作成功"（MyBatis-Plus updateById/removeById 影响 0 行不报错），2 个接口凭空插入新数据。本次只修高危两类：基类静默成功 + 单据接口凭空插入，其余 97 个影响提示问题暂不逐个处理。

### 17.1 修复内容（3 个文件）

| 文件                                                 | 修复                                                                                                                                                                                                              |
| -------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `basedata/controller/BaseCrudController.java`      | update：id 为空或 updateById 返回 false → `Result.error(404, "记录不存在或已被删除")`（复用 getIdField() 反射取 id，反射异常降级为仅依赖返回值）；delete：removeById 返回 false → `Result.error(404, "记录不存在")`；batchDelete 保持原样（批量幂等可接受）。覆盖全部 15 个基础资料子类 |
| `inventory/controller/OtherReceiptController.java` | update 进 service 前先 getById 判空，抛 `BusinessException("其他入库单不存在")`。根因：saveReceiptWithItems 里 existing==null 仅跳过状态校验，主表 updateById 0 行不报错但**明细 itemMapper.insert 照常插入**（挂在不存在 receipt\_id 下的孤儿明细行）                 |
| `finance/controller/VoucherController.java`        | updateTemplate 先 getById 判空，抛 `BusinessException("凭证模板不存在")`（service 层 updateTemplate 原直接 templateMapper.updateById 无校验）                                                                                        |

### 17.2 设计边界与口径

- 错误风格：基类用 `Result.error(404,...)`（与该类现有 409/403 一致）；单据接口用 BusinessException（与所在模块 service 风格一致，GlobalExceptionHandler 统一转 Result）。

- 语义收紧：更新不带 id / 重复删除 → 原来返回"成功"（实际零影响），现在 404，属预期行为变化。

- 正常流程零影响：OtherReceipt 存在的 id 照常走 saveReceiptWithItems 状态机校验；body 里 id 一律以路径 id 为准。

- 15 个子类 getIdField() 已逐一验证返回非 null 常量，反射取 id 无误伤。

***

> 知识库维护说明：本文件由项目记忆沉淀生成，遇到新问题请按章节追加。第十二章为服务器排障实录（先搜此章再动手），第十三章为开发时间线（查"某功能何时做的"），第十四章为部署链路可靠性体系（部署前先读），第十五章为报表中心现状与下线记录，第十六章为数据看板模块（口径与坑），第十七章为写操作存在性校验（安全审计修复）。

<br />
