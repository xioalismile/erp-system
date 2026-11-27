/**
 * 获取需要隐藏的模块列表（从环境变量读取）
 * 环境变量 VITE_HIDDEN_MODULES=acquisition,crm 表示隐藏获客管理和客户管理
 */
import { isBillHidden } from './hiddenBills'

function getHiddenModules() {
  const hidden = import.meta.env.VITE_HIDDEN_MODULES || ''
  return hidden.split(',').map(s => s.trim()).filter(Boolean)
}

export const HIDDEN_MODULES = getHiddenModules()

/**
 * 左侧菜单配置 - 统一定义所有菜单项
 * 用于 MainLayout 渲染菜单 + SysConfig 自定义菜单名称
 * configKey 规则: menu_name_<group>_<itemKey>，作为 sys_config 的键
 *
 * roles 字段：定义可看到该菜单组的角色编码列表
 * null 或未设置 = 所有角色可见
 * 角色编码对应 sys_role.role_code
 *
 * 隐藏机制：HIDDEN_MODULES（VITE_HIDDEN_MODULES，构建期）= 整组从产物菜单里剔除
 * （获客管理/客户管理/电商平台 客户包隐藏等场景）；数据库菜单与权限不受影响，恢复只需改环境变量重新构建。
 */
export const MENU_GROUPS = [
  {
    key: 'dashboard',
    label: '工作台',
    icon: 'DataLine',
    isSingle: true,
    roles: null, // 所有角色可见
    items: [
      { path: '/dashboard', label: '工作台', itemKey: 'dashboard' }
    ]
  },
  {
    key: 'databoard',
    label: '数据看板',
    icon: 'DataAnalysis',
    // 财务看板含净利润等敏感指标，限定管理层+财务+管理员（SUPER_ADMIN 走 '*' 通配）
    roles: ['FINANCE', 'FINANCE_MANAGER', 'SALES_MANAGER', 'WAREHOUSE_MANAGER', 'PURCHASE_MANAGER', 'PRODUCTION_MANAGER', 'PRODUCTION', 'ADMIN'],
    items: [
      { path: '/databoard/finance', label: '财务看板', itemKey: 'finance' },
      { path: '/databoard/sale', label: '销售看板', itemKey: 'sale' },
      { path: '/databoard/inventory', label: '仓储看板', itemKey: 'inventory' },
      { path: '/databoard/operation', label: '经营看板', itemKey: 'operation' },
      // 2026-11-05 新增：SPC 过程控制看板
      // 2026-11-06：卡片式自定义能力并入上面四张现有看板（页面右上角「看板设置」），不再单设独立看板菜单
      { path: '/databoard/spc', label: 'SPC质量看板', itemKey: 'spc' }
    ]
  },
  {
    key: 'basedata',
    label: '基础资料',
    icon: 'Files',
    roles: null, // 所有角色可见
    items: [
      { path: '/basedata/unit', label: '计量单位', itemKey: 'unit' },
      { path: '/basedata/currency', label: '币种', itemKey: 'currency' },
      { path: '/basedata/settle', label: '结算方式', itemKey: 'settle' },
      { path: '/basedata/bank-account', label: '银行账户', itemKey: 'bank-account' },
      { path: '/basedata/price-level', label: '价格等级', itemKey: 'price-level' },
      { path: '/basedata/department', label: '部门档案', itemKey: 'department' },
      { path: '/basedata/employee', label: '员工档案', itemKey: 'employee' },
      { path: '/basedata/warehouse', label: '仓库档案', itemKey: 'warehouse' },
      { path: '/basedata/location', label: '货位档案', itemKey: 'location' },
      { path: '/basedata/material-category', label: '物料分类', itemKey: 'material-category' },
      { path: '/basedata/material', label: '物料档案', itemKey: 'material' },
      { path: '/basedata/customer', label: '客户档案', itemKey: 'customer' },
      { path: '/basedata/supplier', label: '供应商档案', itemKey: 'supplier' },
      { path: '/masterdata/bom', label: 'BOM', itemKey: 'bom' },
      { path: '/masterdata/qc-template', label: '质检模板', itemKey: 'qc-template' },
      { path: '/basedata/help', label: '操作手册', itemKey: 'basedata-help' }
    ]
  },
  {
    key: 'crm',
    label: '客户管理',
    icon: 'User',
    roles: ['SALES', 'SALES_MANAGER', 'ADMIN'],
    items: [
      { path: '/crm/dashboard', label: '我的驾驶舱', itemKey: 'dashboard' },
      // 二级菜单「自动化管控」（2026-10-09）：把销售过程动作量化一族的页面收进来，避免与常规 CRM 混排
      {
        itemKey: 'automation',
        label: '自动化管控',
        children: [
          { path: '/crm/sales-target', label: '销售目标', itemKey: 'sales-target' },
          { path: '/crm/sales-activity', label: '销售过程', itemKey: 'sales-activity' },
          { path: '/crm/warning', label: '销售预警', itemKey: 'warning' }
        ]
      },
      { path: '/crm/customer', label: '我的私海', itemKey: 'customer' },
      { path: '/crm/pool', label: '公海池', itemKey: 'pool' },
      { path: '/crm/lead', label: '销售线索', itemKey: 'lead' },
      { path: '/crm/opportunity', label: '商机管理', itemKey: 'opportunity' },
      { path: '/crm/funnel', label: '销售漏斗', itemKey: 'funnel' },
      { path: '/crm/contact', label: '联系人管理', itemKey: 'contact' },
      { path: '/crm/follow-up', label: '跟进记录', itemKey: 'follow-up' },
      { path: '/crm/complaint', label: '客户投诉', itemKey: 'complaint' },
      { path: '/crm/survey', label: '满意度调查', itemKey: 'survey' },
      { path: '/crm/report', label: '报表中心', itemKey: 'report' },
      { path: '/crm/help', label: '操作手册', itemKey: 'help' }
    ]
  },
  {
    key: 'acquisition',
    label: '获客管理',
    icon: 'Promotion',
    roles: ['SALES', 'SALES_MANAGER', 'ADMIN'],
    items: [
      { path: '/acquisition/task', label: '获客任务', itemKey: 'task' },
      { path: '/acquisition/result', label: '采集结果审核', itemKey: 'result' },
      { path: '/acquisition/login', label: '企查查登录', itemKey: 'login' },
      { path: '/acquisition/help', label: '操作手册', itemKey: 'help' }
    ]
  },
  // 平台管理菜单组：构建期开关（VITE_PLATFORM_ADMIN），客户包连菜单文案都不进产物
  ...(import.meta.env.VITE_PLATFORM_ADMIN === 'true' ? [{
    key: 'platform',
    label: '平台管理',
    icon: 'Monitor',
    roles: ['SUPER_ADMIN'],
    items: [
      { path: '/admin/tenant/manage', label: '客户管理', itemKey: 'tenant-manage' },
      // 客户升级部署：仅自营环境（VITE_DEPLOY_ADMIN=true）；客户包不设置 → 构建期排除，防覆盖/删除用户数据
      ...(import.meta.env.VITE_DEPLOY_ADMIN === 'true' ? [
        { path: '/admin/deploy/manage', label: '客户升级部署', itemKey: 'deploy-manage' }
      ] : [])
    ]
  }] : []),
  {
    key: 'sale',
    label: '销售管理',
    icon: 'ShoppingCart',
    roles: ['SALES', 'SALES_MANAGER', 'ADMIN'],
    items: [
      { path: '/sale/orders', label: '销售订单', itemKey: 'orders' },
      { path: '/sale/contracts', label: '销售合同', itemKey: 'contracts' },
      { path: '/sale/quotes', label: '销售报价单', itemKey: 'quotes' },
      { path: '/sale/deliveries', label: '销售出库单', itemKey: 'deliveries' },
      { path: '/logistics/dashboard', label: '物流监控看板', itemKey: 'logistics-dashboard' },
      { path: '/sale/returns', label: '销售退货', itemKey: 'returns' },
      // 2026-11-18：销售换货单（替换货）与销售退货同级；后端 sys_menu 由 SchemaUpgradeEnsurer 自动初始化，
      // 侧边栏由本配置渲染，缺这一项则菜单永远不显示。
      // 2026-11-23：客户包可通过 VITE_HIDDEN_BILLS=SALE_EXCHANGE 整项剔除（121 不上该单据）。
      { path: '/sale/exchanges', label: '销售换货单', itemKey: 'exchanges', billCode: 'SALE_EXCHANGE' },
      // 2026-11-18：售后维修单（菜单权限码 sale:repairs，与后端 sys_menu 初始化一致）
      { path: '/sale/repairs', label: '售后维修单', itemKey: 'repairs', billCode: 'SALE_REPAIR' },
      { path: '/sale/invoices', label: '销售发票', itemKey: 'invoices' },
      {
        label: '报表',
        itemKey: 'report',
        children: [
          { path: '/sale/report/analysis', label: '分析报表', itemKey: 'report-analysis' },
          { path: '/sale/report/sale-order-detail', label: '销售订单明细报表', itemKey: 'report-order-detail' },
          { path: '/sale/report/sale-delivery-detail', label: '销售出库明细报表', itemKey: 'report-delivery-detail' },
          { path: '/sale/report/sale-invoice-detail', label: '销售发票明细报表', itemKey: 'report-invoice-detail' },
          { path: '/sale/report/customer-statement', label: '客户对账单', itemKey: 'report-customer-statement' }
        ]
      },
      { path: '/sale/help', label: '操作手册', itemKey: 'help' }
    ]
  },
  {
    key: 'purchase',
    label: '采购管理',
    icon: 'Goods',
    roles: ['PURCHASE', 'PURCHASE_MANAGER', 'ADMIN'],
    items: [
      { path: '/purchase/requests', label: '采购申请单', itemKey: 'requests' },
      { path: '/purchase/contracts', label: '采购合同', itemKey: 'contracts' },
      { path: '/purchase/orders', label: '采购订单', itemKey: 'orders' },
      { path: '/purchase/inbounds', label: '采购入库单', itemKey: 'inbounds' },
      { path: '/purchase/returns', label: '采购退货单', itemKey: 'returns' },
      { path: '/purchase/invoices', label: '采购发票', itemKey: 'invoices' },
      {
        label: '报表',
        itemKey: 'report',
        children: [
          { path: '/purchase/report/analysis', label: '分析报表', itemKey: 'report-analysis' },
          { path: '/purchase/report/purchase-request-detail', label: '采购申请单明细报表', itemKey: 'report-request-detail' },
          { path: '/purchase/report/purchase-contract-detail', label: '采购合同明细报表', itemKey: 'report-contract-detail' },
          { path: '/purchase/report/purchase-order-detail', label: '采购订单明细报表', itemKey: 'report-order-detail' },
          { path: '/purchase/report/purchase-inbound-detail', label: '采购入库单明细报表', itemKey: 'report-inbound-detail' },
          { path: '/purchase/report/purchase-return-detail', label: '采购退货单明细报表', itemKey: 'report-return-detail' },
          { path: '/purchase/report/purchase-invoice-detail', label: '采购发票明细报表', itemKey: 'report-invoice-detail' },
          { path: '/purchase/report/supplier-statement', label: '供应商对账单', itemKey: 'report-supplier-statement' }
        ]
      },
      { path: '/purchase/help', label: '操作手册', itemKey: 'help' }
    ]
  },
  {
    key: 'inventory',
    label: '库存管理',
    icon: 'Box',
    roles: ['WAREHOUSE', 'WAREHOUSE_MANAGER', 'PURCHASE', 'PURCHASE_MANAGER', 'SALES', 'SALES_MANAGER', 'PRODUCTION', 'PRODUCTION_MANAGER', 'FINANCE', 'FINANCE_MANAGER', 'ADMIN'],
    items: [
      { path: '/inventory/current', label: '即时库存', itemKey: 'current' },
      { path: '/inventory/batches', label: '批次库存', itemKey: 'batches' },
      { path: '/inventory/ledger', label: '库存流水', itemKey: 'ledger' },
      { path: '/inventory/other-receipts', label: '其他入库单', itemKey: 'other-receipts' },
      { path: '/inventory/other-issues', label: '其他出库单', itemKey: 'other-issues' },
      { path: '/inventory/stock-transfers', label: '库存调拨单', itemKey: 'stock-transfers' },
      { path: '/inventory/stock-checks', label: '库存盘点单', itemKey: 'stock-checks' },
      { path: '/inventory/material-requisitions', label: '生产领料单', itemKey: 'material-requisitions' },
      {
        label: '报表',
        itemKey: 'report',
        children: [
          { path: '/inventory/report/analysis', label: '分析报表', itemKey: 'report-analysis' },
          { path: '/inventory/report/other-receipt-detail', label: '其他入库单明细报表', itemKey: 'report-other-receipt-detail' },
          { path: '/inventory/report/other-issue-detail', label: '其他出库单明细报表', itemKey: 'report-other-issue-detail' },
          { path: '/inventory/report/stock-transfer-detail', label: '库存调拨单明细报表', itemKey: 'report-stock-transfer-detail' },
          { path: '/inventory/report/stock-check-detail', label: '库存盘点单明细报表', itemKey: 'report-stock-check-detail' },
          { path: '/inventory/report/material-requisition-detail', label: '生产领料单明细报表', itemKey: 'report-material-requisition-detail' }
        ]
      },
      { path: '/inventory/help', label: '操作手册', itemKey: 'help' }
    ]
  },
  {
    key: 'production',
    label: '生产管理',
    icon: 'Tools',
    roles: ['PRODUCTION', 'PRODUCTION_MANAGER', 'ADMIN'],
    items: [
      { path: '/production/routings', label: '工艺路线', itemKey: 'routings' },
      { path: '/production/mrp', label: 'MRP分析', itemKey: 'mrp' },
      { path: '/production/workorders', label: '生产工单', itemKey: 'workorders' },
      { path: '/production/schedule-board', label: '车间排程看板', itemKey: 'schedule-board' },
      { path: '/production/progress-tracking', label: '车间进度跟踪', itemKey: 'progress-tracking' },
      { path: '/production/assemble', label: '生产组装单', itemKey: 'assemble' },
      { path: '/production/disassemble', label: '生产拆卸单', itemKey: 'disassemble' },
      { path: '/production/returns', label: '生产退料单', itemKey: 'returns' },
      { path: '/production/supplements', label: '生产补料单', itemKey: 'supplements' },
      { path: '/production/receipts', label: '生产入库单', itemKey: 'receipts' },
      // 化妆品批记录完整闭环（2026-11-06）：批记录中心（总查询门面）
      { path: '/production/batch-record-center', label: '批记录中心', itemKey: 'batch-record-center' },
      { path: '/production/help', label: '操作手册', itemKey: 'production-help' },
      {
        label: '报表',
        itemKey: 'report',
        children: [
          { path: '/production/report/analysis', label: '分析报表', itemKey: 'report-analysis' },
          { path: '/production/qc-record', label: '工序质检报表', itemKey: 'report-qc-record' }
        ]
      }
    ]
  },
  {
    key: 'device',
    label: '设备管理',
    icon: 'Cpu',
    // 设备数据是全公司共用基础能力，菜单对全部角色可见；具体页面按钮由「角色权限设置」控制
    roles: null,
    items: [
      { path: '/device/ledger', label: '设备台账', itemKey: 'ledger' },
      { path: '/device/access', label: '接入管理', itemKey: 'access' },
      { path: '/device/debug', label: '接入调试台', itemKey: 'debug' },
      { path: '/device/monitor', label: '实时监控', itemKey: 'monitor' },
      { path: '/device/task', label: '设备任务', itemKey: 'task' },
      { path: '/device/report-confirm', label: '报工待确认', itemKey: 'report-confirm' },
      { path: '/device/unmatched', label: '未关联数据', itemKey: 'unmatched' },
      { path: '/device/alarm', label: '报警中心', itemKey: 'alarm' },
      { path: '/device/inspection', label: '点检管理', itemKey: 'inspection' },
      { path: '/device/maintenance', label: '保养管理', itemKey: 'maintenance' },
      { path: '/device/repair', label: '维修工单', itemKey: 'repair' },
      { path: '/device/spare', label: '备件管理', itemKey: 'spare' },
      { path: '/device/report', label: '设备报表', itemKey: 'report' },
      { path: '/device/help', label: '操作手册', itemKey: 'help' }
    ]
  },
  {
    key: 'subcontract',
    label: '委外管理',
    icon: 'Connection',
    roles: ['PRODUCTION', 'PRODUCTION_MANAGER', 'ADMIN'],
    items: [
      { path: '/subcontract/orders', label: '委外加工单', itemKey: 'orders' },
      { path: '/subcontract/picks', label: '委外发料单', itemKey: 'picks' },
      { path: '/subcontract/inbounds', label: '委外入库单', itemKey: 'inbounds' },
      { path: '/subcontract/supplements', label: '委外补料单', itemKey: 'supplements' },
      { path: '/subcontract/returns', label: '委外退料单', itemKey: 'returns' },
      { path: '/subcontract/help', label: '操作手册', itemKey: 'subcontract-help' }
    ]
  },
  {
    key: 'sample',
    label: '打样管理',
    icon: 'Tools',
    roles: ['SALES', 'SALES_MANAGER', 'PRODUCTION', 'PRODUCTION_MANAGER', 'ADMIN'],
    items: [
      { path: '/sample/list', label: '打样单', itemKey: 'list' },
      { path: '/freesample/list', label: '免费样品订单', itemKey: 'freesample-list' }
    ]
  },
  {
    key: 'finance',
    label: '财务管理',
    icon: 'TrendCharts',
    roles: ['FINANCE', 'FINANCE_MANAGER', 'ADMIN'],
    items: [
      { path: '/finance/sale-pre-receipts', label: '销售预收款单', itemKey: 'sale-pre-receipts' },
      { path: '/finance/purchase-pre-payments', label: '采购预付款单', itemKey: 'purchase-pre-payments' },
      { path: '/finance/receipt', label: '收款单', itemKey: 'receipt' },
      { path: '/finance/payment', label: '付款单', itemKey: 'payment' },
      { path: '/finance/operation-expense', label: '费用分摊', itemKey: 'operation-expense' },
      { path: '/finance/cost-calculation', label: '成本核算', itemKey: 'cost-calculation' },
      { path: '/finance/cost-config', label: '成本核算配置', itemKey: 'cost-config' },
      { path: '/finance/wage-settlement', label: '工序工资核算', itemKey: 'wage-settlement' },
      { path: '/finance/help', label: '操作手册', itemKey: 'help' },
      {
        label: '报表',
        itemKey: 'report',
        children: [
          { path: '/finance/report/analysis', label: '分析报表', itemKey: 'report-analysis' },
          { path: '/finance/report/ar-summary', label: '应收账款汇总表', itemKey: 'report-ar-summary' },
          { path: '/finance/report/ap-summary', label: '应付账款汇总表', itemKey: 'report-ap-summary' }
        ]
      }
    ]
  },
  {
    key: 'report',
    label: '报表中心',
    icon: 'PieChart',
    roles: null, // 所有角色可见（数据级权限由后端 SQL 层强制过滤）
    items: [
      { path: '/report/custom', label: '自定义报表', itemKey: 'custom' }
    ]
  },
  // 电商平台（亚马逊北美站 + 拼多多国内站）：店铺授权 / 平台订单 / 商品映射
  {
    key: 'ecommerce',
    label: '电商平台',
    icon: 'Cpu',
    roles: ['ADMIN'],
    items: [
      { path: '/ecommerce/shop', label: '店铺授权', itemKey: 'shop' },
      { path: '/ecommerce/orders', label: '平台订单', itemKey: 'orders' },
      { path: '/ecommerce/mapping', label: '商品映射', itemKey: 'mapping' }
    ]
  },
  {
    key: 'system',
    label: '系统管理',
    icon: 'Setting',
    roles: ['ADMIN', 'SUPER_ADMIN'],
    items: [
      { path: '/system/settings', label: '设置中心', itemKey: 'settings' },
      {
        label: '系统',
        itemKey: 'sub-system',
        children: [
          { path: '/system/settings?item=basic-info', label: '基础信息设置', itemKey: 'basic-info' },
          { path: '/system/settings?item=sys-param', label: '系统参数设置', itemKey: 'sys-param' },
          { path: '/system/settings?item=role-perm', label: '角色权限设置', itemKey: 'role-perm' },
          { path: '/system/settings?item=employee-perm', label: '员工权限补充', itemKey: 'employee-perm' },
          { path: '/system/settings?item=data-maintain', label: '数据维护', itemKey: 'data-maintain' },
          // 语言管理：与设置中心「系统」分组 item key（i18n-manager）一致，缺失会导致小助手「打开语言管理」匹配不到页面
          { path: '/system/settings?item=i18n-manager', label: '语言管理', itemKey: 'i18n-manager' }
        ]
      },
      {
        label: '业务',
        itemKey: 'sub-business',
        children: [
          { path: '/system/settings?item=inventory-warning', label: '库存预警设置', itemKey: 'inventory-warning' },
          { path: '/system/settings?item=crm-warning', label: 'CRM预警设置', itemKey: 'crm-warning' },
          { path: '/system/settings?item=sale-price', label: '销售价格策略', itemKey: 'sale-price' },
          { path: '/system/settings?item=purchase-price', label: '采购价格策略', itemKey: 'purchase-price' },
          { path: '/system/settings?item=commission-rate', label: '提成率配置', itemKey: 'commission-rate' },
          { path: '/system/settings?item=logistics-config', label: '物流配置', itemKey: 'logistics-config' },
          { path: '/system/settings?item=auto-push', label: '自动下推设置', itemKey: 'auto-push' }
        ]
      },
      {
        label: '自定义',
        itemKey: 'sub-custom',
        children: [
          { path: '/system/settings?item=menu-name', label: '菜单名称设置', itemKey: 'menu-name' },
          { path: '/system/settings?item=theme-setting', label: '主题设置', itemKey: 'theme-setting' },
          // 界面尺寸设置：功能状态=未完成 → 构建期开关控制，发布包（.env.production/.env.customer）不设置该变量即整项过滤
          ...(import.meta.env.VITE_UI_SIZE_SETTINGS === 'true'
            ? [{ path: '/system/settings?item=ui-size', label: '界面尺寸设置', itemKey: 'ui-size' }]
            : []),
          { path: '/system/settings?item=notification-setting', label: '消息通知设置', itemKey: 'notification-setting' },
          { path: '/system/settings?item=serial-no', label: '自动编号设置', itemKey: 'serial-no' },
          { path: '/system/settings?item=approval', label: '审批设置', itemKey: 'approval' },
          { path: '/system/settings?item=batch-no', label: '批次编号设置', itemKey: 'batch-no' },
          { path: '/system/settings?item=print-setting', label: '打印设置', itemKey: 'print-setting' },
          { path: '/system/settings?item=custom-field', label: '自定义字段设置', itemKey: 'custom-field' }
        ]
      },
      {
        label: '日志',
        itemKey: 'sub-log',
        children: [
          { path: '/system/settings?item=operate-log', label: '操作日志', itemKey: 'operate-log' }
        ]
      },
      {
        // AI 分组（2026-09-19）：与设置中心第一列顺序一致；原「系统」组内 3 项迁入，item key 不变 → 权限不受影响
        label: 'AI',
        itemKey: 'sub-ai',
        children: [
          { path: '/system/settings?item=ai-overview', label: 'AI 设置总览', itemKey: 'ai-overview' },
          { path: '/system/settings?item=ai-assistant-settings', label: 'AI 助手设置', itemKey: 'ai-assistant-settings' },
          { path: '/system/settings?item=ai-disclaimer', label: '协议与免责声明', itemKey: 'ai-disclaimer' },
          { path: '/system/settings?item=ai-data-policy', label: 'AI 数据使用政策', itemKey: 'ai-data-policy' },
          { path: '/system/settings?item=ai-tool-permission', label: 'AI 工具权限', itemKey: 'ai-tool-permission' },
          { path: '/system/settings?item=ai-token-usage', label: 'AI 用量管理', itemKey: 'ai-token-usage' },
          // @feature FEAT-AIKB-001
          { path: '/system/settings?item=ai-knowledge', label: 'AI 知识库管理', itemKey: 'ai-knowledge' }
        ]
      }
    ]
  }
].filter(group => !HIDDEN_MODULES.includes(group.key))
  // 客户包隐藏单据（VITE_HIDDEN_BILLS）：按 billCode 剔除菜单项，全量包不配置 = 原样
  .map(group => ({
    ...group,
    items: (group.items || []).filter(item => !(item.billCode && isBillHidden(item.billCode)))
  }))

/**
 * 生成 sys_config 的 configKey：menu_name_<groupKey>_<itemKey>
 */
export function getMenuConfigKey(groupKey, itemKey) {
  return `menu_name_${groupKey}_${itemKey}`
}

/**
 * 扁平化所有菜单项（用于 SysConfig 批量展示）
 */
export function flattenMenuItems() {
  const result = []
  MENU_GROUPS.forEach(group => {
    // 分组标题也可自定义
    result.push({
      configKey: `menu_group_${group.key}`,
      defaultLabel: group.label,
      groupKey: group.key,
      isGroup: true
    })
    group.items.forEach(item => {
      result.push({
        configKey: getMenuConfigKey(group.key, item.itemKey),
        defaultLabel: item.label,
        groupKey: group.key,
        path: item.path,
        isGroup: false
      })
    })
  })
  return result
}

/**
 * 根据用户角色过滤菜单组（兜底逻辑：仅当 menuIds 为空时使用）
 * @param {string[]} roles - 用户角色编码列表
 * @returns {Array} 过滤后的菜单组
 */
export function getMenuGroupsByRoles(roles = []) {
  // SUPER_ADMIN 返回全部菜单（含平台管理）
  if (roles.includes('SUPER_ADMIN')) {
    return MENU_GROUPS
  }
  // 管理员类角色（ADMIN/MANAGER）返回全部菜单，但排除平台管理
  if (roles.includes('admin') || roles.includes('ADMIN') || roles.includes('MANAGER')) {
    return MENU_GROUPS.filter(g => g.key !== 'platform')
  }
  // 无角色信息时返回空数组（无权限），不做默认放行
  if (!roles || roles.length === 0) {
    return []
  }
  return MENU_GROUPS.filter(group => {
    // roles 为 null 表示所有角色可见
    if (!group.roles) return true
    // 用户拥有该组要求的任一角色即可见
    return group.roles.some(r => roles.includes(r))
  })
}

/**
 * 根据后台返回的菜单路径权限(menuRoutePaths)动态过滤菜单组
 * menuRoutePaths 是用户有权限访问的菜单路径列表
 * 
 * @param {string[]} menuRoutePaths - 后台返回的菜单路径列表（如 ['/sale/order', '/base/product']）
 * @param {string[]} roles - 用户角色编码列表（用于兜底）
 * @returns {Array} 过滤后的菜单组
 */
export function getMenuGroupsByRoutePaths(menuRoutePaths = [], roles = []) {
  // 规则1：menuRoutePaths 为空 → 按静态roles兜底
  if (!Array.isArray(menuRoutePaths) || menuRoutePaths.length === 0) {
    return getMenuGroupsByRoles(roles)
  }

  const pathSet = new Set(menuRoutePaths)

  // 构建一级路径前缀集合（如 /sale/order → /sale）
  const prefixSet = new Set()
  for (const p of menuRoutePaths) {
    const parts = p.split('/').filter(Boolean)
    if (parts.length >= 1) {
      prefixSet.add('/' + parts[0])
    }
  }

  return MENU_GROUPS
    .map(group => {
      const groupPrefix = '/' + group.key
      // 检查该分组是否有任何路径匹配
      // 匹配规则1：路径首段 === group.key（如 /sale/order → 分组sale）
      // 匹配规则2：分组内具体 item.path 直接命中 menuRoutePaths（如 platform 分组的 /admin/deploy/manage）
      const groupHasAnyMatch = menuRoutePaths.some(p => {
        const parts = p.split('/').filter(Boolean)
        if (parts.length >= 1 && parts[0] === group.key) return true
        return false
      }) || (group.items || []).some(item => pathSet.has(item.path))

      // 如果分组有匹配的路径，则过滤其中的菜单项
      if (!groupHasAnyMatch) {
        return null
      }

      const filteredItems = (group.items || [])
        .map(item => {
          // 设置中心及其子功能仅通过齿轮图标入口访问，不在侧边栏菜单渲染
          if (item.path && item.path.startsWith('/system/settings')) return null
          // 有三级children的情况（如 报表→分析报表/销售订单明细表）
          if (item.children && item.children.length) {
            const filteredChildren = item.children.filter(child =>
              pathSet.has(child.path) && !(child.path && child.path.startsWith('/system/settings'))
            )
            const itemHasPermission = pathSet.has(item.path)
            if (itemHasPermission || filteredChildren.length > 0) {
              return {
                ...item,
                children: itemHasPermission ? item.children : filteredChildren
              }
            }
            return null
          }
          // 普通菜单项：检查路径是否在权限列表中
          if (pathSet.has(item.path)) {
            return item
          }
          return null
        })
        .filter(Boolean)

      // 只有有子菜单被保留时，才保留该分组
      if (filteredItems.length > 0) {
        return {
          ...group,
          items: filteredItems
        }
      }
      return null
    })
    .filter(Boolean)
}

/**
 * @deprecated 使用 getMenuGroupsByRoutePaths 替代
 * 根据后台分配的菜单权限(menuIds)动态过滤菜单组
 */
export function getMenuGroupsByMenuIds(menuIds = [], roles = []) {
  // 如果传入的是路径格式，直接使用新函数
  if (Array.isArray(menuIds) && menuIds.length > 0 && menuIds.some(id => id.startsWith('/'))) {
    return getMenuGroupsByRoutePaths(menuIds, roles)
  }
  
  // 规则1: menuIds 含 '*' → 全部菜单可见（管理员通配符；平台管理仅 SUPER_ADMIN）
  if (Array.isArray(menuIds) && menuIds.includes('*')) {
    if (roles.includes('SUPER_ADMIN')) {
      return MENU_GROUPS
    }
    return MENU_GROUPS.filter(g => g.key !== 'platform')
  }

  // 规则2：后台未分配任何权限（空数组）→ 按静态roles兜底
  if (!Array.isArray(menuIds) || menuIds.length === 0) {
    return getMenuGroupsByRoles(roles)
  }

  // 规则3：严格按 menuIds 过滤
  const idSet = new Set(menuIds)

  return MENU_GROUPS
    .map(group => {
      const groupId = `group_` + group.key
      // 该分组是否在menuIds中被整体勾选
      const groupChecked = idSet.has(groupId)

      // 过滤分组内的菜单项：要么分组整体被勾选，要么该子项的path被勾选
      const filteredItems = (group.items || [])
        .map(item => {
          // 有三级children的情况（如 报表→分析报表/销售订单明细表）
          if (item.children && item.children.length) {
            const filteredChildren = item.children.filter(child =>
              groupChecked || idSet.has(child.path)
            )
            // 父级itemKey本身是否被勾选 或 有任意子children被保留
            const itemHasPermission = groupChecked || idSet.has(item.path)
            if (itemHasPermission || filteredChildren.length > 0) {
              return {
                ...item,
                children: itemHasPermission ? item.children : filteredChildren
              }
            }
            return null
          }
          // 普通菜单项：分组整体勾选 或 该菜单path被勾选
          if (groupChecked || idSet.has(item.path)) {
            return item
          }
          return null
        })
        .filter(Boolean)

      // 只有分组整体被勾选，或者有子菜单被勾选时，才保留该分组
      if (groupChecked || filteredItems.length > 0) {
        return {
          ...group,
          items: filteredItems
        }
      }
      return null
    })
    .filter(Boolean)
}
