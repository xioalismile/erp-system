/**
 * 新手教程步骤配置
 *
 * 所有业务单据编辑页面共用同一套布局结构，因此教程步骤高度通用。
 * 每个模块只需在通用步骤基础上微调字段名称和交互选项即可。
 *
 * 步骤对象格式：
 * {
 *   selector: string,        // CSS选择器或'text:按钮文字'，用于定位高亮元素
 *   title: string,           // 步骤标题
 *   description: string,     // 步骤说明（支持HTML）
 *   position: 'bottom'|'top'|'left'|'right'|'center',  // 气泡位置
 *   interaction: {           // 可选：模拟交互
 *     type: 'select'|'input'|'button',
 *     placeholder: string,
 *     options: [{label, value}],  // select类型用
 *     label: string,               // button类型用
 *     successText: string
 *   }
 * }
 */

// ========== 通用步骤模板（适用于所有业务单据） ==========
// Selector / 文案 / 按钮说明 必须与实际DOM结构保持一致：
//   第 3、5、6、10、11、12、15 步改用[data-onboarding=xxx]属性定位，替代nth-of-type，避免兄弟节点顺序变化后高亮框消失
//   .page-header [data-onboarding=bill-actions] → 真实渲染为 BillActions 组件：
//     新建/草稿态：显示「保存」「提交」（其余被 isNew + BillStatus 规则隐藏）
//     已生效态：还会出现「审核/反审/结案/作废/删除/打印/复制」等按钮
//   [data-onboarding=detail-header-actions] → 明细头部：
//     三个按钮：SourcePicker（选单）、从物料库选择、+ 添加明细
//   [data-onboarding=detail-subtable-header] → 明细内标题 + ⚙圆形列设置按钮
// 基本信息卡片不直接高亮整卡（太高），拆为 4 个 .info-section 分别讲解
function buildCommonSteps(moduleName, partyLabel, partyOptions) {
  return [
    // 1
    {
      selector: '.page-header',
      title: '欢迎来到' + moduleName,
      description: '这是<strong>' + moduleName + '</strong>的编辑页面。我会一步步带你了解各个功能区域，准备好了就点击「下一步」吧！',
      position: 'bottom'
    },
    // 2
    {
      selector: '.back-btn',
      title: '返回列表',
      description: '点击这里可以<strong>返回列表页面</strong>。如果你不想继续编辑当前单据，随时可以通过这里返回。',
      position: 'bottom'
    },
    // 3
    {
      selector: '[data-onboarding="page-title"]',
      title: '页面标题与单号',
      description: '这里显示当前单据的<strong>类型名称和单据编号</strong>（例如 SO-20260816-0021）。新建时编号可能为空，保存后系统会自动生成。右侧会显示当前单据的状态标签（草稿/待审核/已生效/已结案）。',
      position: 'bottom'
    },
    // 4
    {
      selector: '.status-tag',
      title: '单据状态标签',
      description: '这个小标签就是当前单据的<strong>审批状态</strong>：\n<ul style="margin-top:6px;padding-left:18px;">\n<li><strong>草稿</strong>（灰）：正在编辑，可随意修改</li>\n<li><strong>待审核</strong>（黄）：已提交审核，等待审批</li>\n<li><strong>已生效</strong>（绿）：已通过，不可随便修改</li>\n<li><strong>已取消/已结案</strong>（红/灰）：单据完成或终止</li>\n</ul>',
      position: 'bottom'
    },
    // 5 顶部操作按钮区（BillActions 组件）
    {
      selector: '[data-onboarding="bill-actions"]',
      title: '顶部操作按钮区',
      description: '右上角是表头主要操作区，由 <code>BillActions</code> 组件根据单据状态动态显示：\n<ul style="margin-top:6px;padding-left:18px;">\n<li><strong>保存</strong>（蓝）：把当前单据保存为<strong>草稿</strong>，不发起审批</li>\n<li><strong>提交</strong>（蓝描边）：填写完整后点击<strong>发起审批流</strong>，状态变为「待审核」</li>\n<li style="color:#606266;font-size:12px;">（单据已有编号 & 已生效后，还会出现：审核/驳回、重置草稿、结案/反结案、作废/反作废、删除、打印、复制）</li>\n</ul>',
      position: 'bottom'
    },
    // 6 单据信息
    {
      selector: '[data-onboarding="section-doc"]',
      title: '① 单据信息',
      description: '基本信息按分组排列，先看第一个：<strong>单据信息</strong>。\n<ul style="margin-top:6px;padding-left:18px;">\n<li><strong>订单编号</strong>：保存后自动生成</li>\n<li><strong>*订单日期</strong>：必填，业务发生日期</li>\n<li><strong>交货日期</strong>：承诺交付时间</li>\n</ul>',
      position: 'bottom',
      interaction: {
        type: 'button',
        label: '演示：自动填订单日期',
        successText: '✅ 演示完成！实际操作中点击「订单日期」会弹出日历，选择后自动写入 YYYY-MM-DD 格式。'
      }
    },
    // 7 客户/供应商信息（带交互）
    {
      selector: '.base-form .info-section:nth-of-type(2)',
      title: '② ' + partyLabel + '信息（自动带出）',
      description: '第二个分组是<strong>' + partyLabel + '信息</strong>，这是最关键的分组：\n<ul style="margin-top:6px;padding-left:18px;">\n<li><strong>*' + partyLabel + '</strong>（必填下拉）：选择后，系统会自动从基础资料中<strong>带出</strong>下方的联系人、联系电话、收货地址、开户银行等信息，不用手动输入</li>\n<li><strong>商机 / 编号 / 简称</strong>：辅助识别信息</li>\n</ul>',
      position: 'bottom',
      interaction: {
        type: 'select',
        placeholder: '请选择' + partyLabel,
        options: partyOptions,
        successText: '✅ 选择成功！实际使用时该' + partyLabel + '的联系人、电话、地址等信息会自动填充到后面的输入框中。'
      }
    },
    // 8 交易信息
    {
      selector: '.base-form .info-section:nth-of-type(3)',
      title: '③ 交易信息',
      description: '第三个分组是<strong>交易条件类</strong>字段：\n<ul style="margin-top:6px;padding-left:18px;">\n<li><strong>*销售员</strong>：负责本单的业务员</li>\n<li><strong>*币种</strong> / 汇率：默认 CNY = 1</li>\n<li><strong>*仓库</strong>：出入库默认仓库</li>\n<li><strong>价格等级 / 付款条件 / 运输方式</strong>：决定价格、结算方式和运费条款</li>\n<li><strong>整单折扣</strong>：对整张订单按比例打折（0.00 = 不打折）</li>\n</ul>',
      position: 'bottom'
    },
    // 9 其他/备注
    {
      selector: '.base-form .info-section:nth-of-type(4)',
      title: '④ 其他（备注）',
      description: '最后一个分组是<strong>备注</strong>，可填写付款说明、交货要求、提醒事项等任意文本。带 <strong style="color:#f56c6c;">*</strong> 的字段为必填项，保存时系统会自动校验。',
      position: 'bottom'
    },
    // 10 明细卡片头部：选单/物料库选择/添加明细 三个按钮
    {
      selector: '[data-onboarding="detail-header-actions"]',
      title: '明细操作：选单 / 从物料库选择 / + 添加明细',
      description: '这是商品明细卡片顶部的三个快捷按钮：\n<ul style="margin-top:6px;padding-left:18px;">\n<li><strong>选单</strong>（绿色）：从上游单据<strong>批量带入</strong>（例如：在销售出库单里选择销售订单，自动复制客户和明细）</li>\n<li><strong>从物料库选择</strong>：打开物料档案弹窗，可<strong>多选多个物料批量加入</strong>到明细</li>\n<li><strong>+ 添加明细</strong>（蓝色）：直接在表格最后<strong>新增一行空白明细</strong>，在表格里手动选物料和填数量</li>\n</ul>',
      position: 'bottom'
    },
    // 11 明细齿轮列设置
    {
      selector: '[data-onboarding="detail-subtable-header"]',
      title: '明细标题 + 列设置（⚙齿轮按钮）',
      description: '明细表格上方有「物料明细」标题和一颗<strong>⚙齿轮按钮（列设置）</strong>：\n<ul style="margin-top:6px;padding-left:18px;">\n<li>点击可以<strong>勾选显示/隐藏明细列</strong>（图片、规格、单位、储位、数量、单价、税率%、折扣、金额…）</li>\n<li>隐藏不需要的列后，表格不会特别宽、横向滚动条也变短</li>\n<li>这些偏好按账号+单据类型维度<strong>保存在本机浏览器</strong>，下次打开自动恢复</li>\n</ul>',
      position: 'bottom'
    },
    // 12 明细表格（演示：注入 2 行真实物料数据，让用户看到选择物料后的自动填充）
    {
      selector: '[data-onboarding="detail-table"]',
      title: '明细数据表格（自动计算金额）',
      description: '在明细表格里编辑每行：\n<ul style="margin-top:6px;padding-left:18px;">\n<li><strong># 列</strong>：行序号，右侧 🗑 按钮用于删除本行</li>\n<li><strong>物料列</strong>：点击下拉选择具体物料，自动带出单位、规格、默认仓库和单价</li>\n<li><strong>数量 × 单价</strong>：手动填数量即可，<strong>金额 = 数量 × 单价</strong>，系统自动计算</li>\n<li><strong>税率% / 折扣</strong>：整单或单行打折、算税，下方自动汇总</li>\n</ul>\n下面点击按钮，模拟"用户选择物料"后自动填充两行真实数据：',
      position: 'top',
      interaction: {
        type: 'button',
        label: '模拟选择物料并填充数据',
        inject: 'demo-items',
        successText: '✅ 演示完成！可以看到上方明细表格中已出现两行真实物料数据，单位、单价、金额、价税合计都已自动计算。'
      }
    },
    // 13 金额汇总区
    {
      selector: '.summary-area',
      title: '金额汇总区',
      description: '表格下方是<strong>自动汇总信息</strong>：\n<ul style="margin-top:6px;padding-left:18px;">\n<li><strong>合计数量 / 原价金额</strong>：按明细累加</li>\n<li><strong>折扣金额</strong> = 原价金额 × 整单折扣</li>\n<li><strong>折后金额</strong> = 原价金额 − 折扣金额</li>\n<li><strong>税额 / 价税合计</strong>：按每行税率自动计算</li>\n<li><strong>定金信息</strong>：本单已收 / 还需收的定金金额</li>\n</ul>',
      position: 'top'
    },
    // 14 提交审核主按钮（再次强调 BillActions 提交）
    {
      selector: '[data-onboarding="bill-actions"]',
      title: '提交审核主操作',
      description: '所有字段填写完成后，回到顶部点击<strong>「提交」按钮</strong>（BillActions 组件）发起审批。\n<ul style="margin-top:6px;padding-left:18px;">\n<li>提交后状态变为「待审核」，表头自动变为只读，等待管理员在审批流中操作</li>\n<li>未保存时，点击提交会自动先执行保存，再发起审批</li>\n<li>后续发现错误，已生效单据也支持<strong>重置草稿</strong>（需要对应权限）</li>\n</ul>',
      position: 'bottom',
      interaction: {
        type: 'button',
        label: '模拟提交审核',
        successText: '✅ 演示完成！实际操作中会弹出确认对话框，确认后单据进入审批流程。'
      }
    },
    // 15 审核流转图
    {
      selector: '[data-onboarding="audit-flow"]',
      title: '审核流转流程图',
      description: '页面底部是可视化的<strong>审核流转流程</strong>：\n<ul style="margin-top:6px;padding-left:18px;">\n<li><strong>创建单据</strong>：草稿态 ✅</li>\n<li><strong>提交审核</strong>：待审批 ✅</li>\n<li><strong>已生效</strong>：审批通过 ✅</li>\n</ul>\n已生效通过的单据会点亮整条蓝线，表示单据闭环。',
      position: 'top'
    },
    // 16 完成
    {
      selector: null,
      title: '教程完成！',
      description: '恭喜你完成了<strong>' + moduleName + '</strong>的新手教程！✅\n\n现在你可以：\n<ul style="margin-top:6px;padding-left:16px;">\n<li>点击「完成教程」开始实际操作</li>\n<li>随时点击页面右上角的「新手教程」按钮重新查看</li>\n</ul>\n\n<strong>提示：</strong>教程中的所有操作都是演示，不会保存到数据库。',
      position: 'center'
    }
  ]
}

// ========== 各模块教程配置 ==========
export const onboardingSteps = {
  // 销售模块
  'sale-order': () => buildCommonSteps('销售订单', '客户', [
    { label: '广州科技有限公司', value: '1' },
    { label: '深圳贸易有限公司', value: '2' },
    { label: '北京制造集团', value: '3' }
  ]),
  'sale-quote': () => buildCommonSteps('销售报价单', '客户', [
    { label: '广州科技有限公司', value: '1' },
    { label: '深圳贸易有限公司', value: '2' }
  ]),
  'sale-contract': () => buildCommonSteps('销售合同', '客户', [
    { label: '广州科技有限公司', value: '1' },
    { label: '深圳贸易有限公司', value: '2' }
  ]),
  'sale-delivery': () => buildCommonSteps('销售出库单', '客户', [
    { label: '广州科技有限公司', value: '1' },
    { label: '深圳贸易有限公司', value: '2' }
  ]),
  'sale-return': () => buildCommonSteps('销售退货单', '客户', [
    { label: '广州科技有限公司', value: '1' }
  ]),
  'sale-invoice': () => buildCommonSteps('销售发票', '客户', [
    { label: '广州科技有限公司', value: '1' }
  ]),
  'sale-price-strategy': () => buildCommonSteps('销售价格策略', '客户', [
    { label: '广州科技有限公司', value: '1' }
  ]),

  // 采购模块
  'purchase-order': () => buildCommonSteps('采购订单', '供应商', [
    { label: '原材料供应商A', value: '1' },
    { label: '包装材料供应商B', value: '2' },
    { label: '设备配件供应商C', value: '3' }
  ]),
  'purchase-request': () => buildCommonSteps('采购申请单', '供应商', [
    { label: '原材料供应商A', value: '1' }
  ]),
  'purchase-inbound': () => buildCommonSteps('采购入库单', '供应商', [
    { label: '原材料供应商A', value: '1' }
  ]),
  'purchase-return': () => buildCommonSteps('采购退货单', '供应商', [
    { label: '原材料供应商A', value: '1' }
  ]),
  'purchase-invoice': () => buildCommonSteps('采购发票', '供应商', [
    { label: '原材料供应商A', value: '1' }
  ]),
  'purchase-contract': () => buildCommonSteps('采购合同', '供应商', [
    { label: '原材料供应商A', value: '1' }
  ]),
  'purchase-price-strategy': () => buildCommonSteps('采购价格策略', '供应商', [
    { label: '原材料供应商A', value: '1' }
  ]),

  // 库存模块
  'stock-transfer': () => buildCommonSteps('库存调拨单', '调出仓库', [
    { label: '主仓库', value: '1' },
    { label: '辅料仓', value: '2' },
    { label: '成品仓', value: '3' }
  ]),
  'stock-check': () => buildCommonSteps('库存盘点单', '盘点仓库', [
    { label: '主仓库', value: '1' },
    { label: '辅料仓', value: '2' }
  ]),
  'other-issue': () => buildCommonSteps('其他出库单', '出库仓库', [
    { label: '主仓库', value: '1' }
  ]),
  'other-receipt': () => buildCommonSteps('其他入库单', '入库仓库', [
    { label: '主仓库', value: '1' }
  ]),
  'material-requisition': () => buildCommonSteps('生产领料单', '领料仓库', [
    { label: '主仓库', value: '1' },
    { label: '辅料仓', value: '2' }
  ]),

  // 生产模块
  'work-order': () => buildCommonSteps('生产工单', '生产车间', [
    { label: '一号车间', value: '1' },
    { label: '二号车间', value: '2' }
  ]),
  'operation-report': () => buildCommonSteps('工序汇报单', '汇报工序', [
    { label: '注塑工序', value: '1' },
    { label: '组装工序', value: '2' }
  ]),
  'production-receipt': () => buildCommonSteps('生产入库单', '入库仓库', [
    { label: '成品仓', value: '1' }
  ]),
  'production-return': () => buildCommonSteps('生产退料单', '退料仓库', [
    { label: '主仓库', value: '1' }
  ]),
  'production-supplement': () => buildCommonSteps('生产补料单', '补料仓库', [
    { label: '主仓库', value: '1' }
  ]),
  'mrp-plan': () => buildCommonSteps('MRP计划单', '计划类型', [
    { label: '生产计划', value: '1' },
    { label: '采购计划', value: '2' }
  ]),
  'production-assemble': () => buildCommonSteps('生产组装单', '入库仓库', [
    { label: '成品仓', value: '1' }
  ]),
  'production-disassemble': () => buildCommonSteps('生产拆卸单', '出库仓库', [
    { label: '成品仓', value: '1' }
  ]),

  // 委外模块
  'subcontract-order': () => buildCommonSteps('委外加工单', '委外供应商', [
    { label: '外协加工厂A', value: '1' },
    { label: '外协加工厂B', value: '2' }
  ]),
  'subcontract-pick': () => buildCommonSteps('委外发料单', '发料仓库', [
    { label: '主仓库', value: '1' }
  ]),
  'subcontract-inbound': () => buildCommonSteps('委外入库单', '入库仓库', [
    { label: '成品仓', value: '1' }
  ]),
  'subcontract-supplement': () => buildCommonSteps('委外补料单', '发料仓库', [
    { label: '主仓库', value: '1' }
  ]),
  'subcontract-return': () => buildCommonSteps('委外退料单', '退料仓库', [
    { label: '主仓库', value: '1' }
  ]),

  // 财务模块
  'payment-voucher': () => buildCommonSteps('付款单', '收款单位', [
    { label: '原材料供应商A', value: '1' }
  ]),
  'payment-receipt': () => buildCommonSteps('收款单', '付款单位', [
    { label: '广州科技有限公司', value: '1' }
  ]),
  'sale-pre-receipt': () => buildCommonSteps('销售预收款单', '客户', [
    { label: '广州科技有限公司', value: '1' }
  ]),
  'purchase-pre-payment': () => buildCommonSteps('采购预付款单', '供应商', [
    { label: '原材料供应商A', value: '1' }
  ]),
  'operation-expense': () => buildCommonSteps('费用分摊', '费用科目', [
    { label: '直接人工', value: '1' },
    { label: '制造费用', value: '2' }
  ]),

  // 基础数据模块
  'material': () => buildCommonSteps('物料档案', '物料分类', [
    { label: '原材料', value: '1' },
    { label: '半成品', value: '2' },
    { label: '成品', value: '3' }
  ]),
  'customer': () => buildCommonSteps('客户档案', '客户分类', [
    { label: 'VIP客户', value: '1' },
    { label: '普通客户', value: '2' }
  ]),
  'supplier': () => buildCommonSteps('供应商档案', '供应商分类', [
    { label: '原材料供应商', value: '1' },
    { label: '服务供应商', value: '2' }
  ]),
  'bom': () => buildCommonSteps('BOM清单', '物料', [
    { label: '产品A', value: '1' },
    { label: '产品B', value: '2' }
  ]),
}

/**
 * 判断当前路由是否为编辑页面
 * 编辑页面的路由特征：
 * - 以 /new 结尾（如 /sale/orders/new）
 * - 以 /数字 结尾（如 /sale/orders/123）
 * - 包含 /edit（如 /inventory/stock-transfers/edit 或 /inventory/stock-transfers/edit/123）
 * - 以 /:id 结尾的动态路由（已被上面的数字匹配覆盖）
 * @param {string} path - 当前路由路径
 * @returns {boolean}
 */
export function isEditPageRoute(path) {
  if (!path) return false
  const segments = path.split('/').filter(Boolean)
  if (segments.length === 0) return false
  const lastSeg = segments[segments.length - 1]
  // /new 结尾
  if (lastSeg === 'new') return true
  // /数字 结尾（如 /123）
  if (/^\d+$/.test(lastSeg)) return true
  // /edit 结尾（库存/生产模块的新增页面）
  if (lastSeg === 'edit') return true
  // 倒数第二段是 edit，最后是数字（如 /edit/123）
  if (segments.length >= 2 && segments[segments.length - 2] === 'edit' && /^\d+$/.test(lastSeg)) return true
  return false
}

/**
 * 根据路由路径获取对应的教程步骤
 * 精确匹配编辑页面路由（基于实际路由表 router/index.js）
 * @param {string} path - 当前路由路径，如 /sale/orders/new
 * @returns {Array|null} 教程步骤数组
 */
export function getOnboardingStepsByRoute(path) {
  if (!path) return null

  // 精确的编辑页面路由前缀映射（基于实际路由表）
  // 这些前缀会匹配对应的 /new、/:id、/edit、/edit/:id 等编辑页面
  const routeMap = {
    // 销售
    '/sale/orders': 'sale-order',
    '/sale/quotes': 'sale-quote',
    '/sale/contracts': 'sale-contract',
    '/sale/deliveries': 'sale-delivery',
    '/sale/returns': 'sale-return',
    '/sale/invoices': 'sale-invoice',
    '/sale/price-strategy': 'sale-price-strategy',
    // 采购
    '/purchase/orders': 'purchase-order',
    '/purchase/requests': 'purchase-request',
    '/purchase/inbounds': 'purchase-inbound',
    '/purchase/returns': 'purchase-return',
    '/purchase/invoices': 'purchase-invoice',
    '/purchase/contracts': 'purchase-contract',
    '/purchase/price-strategy': 'purchase-price-strategy',
    // 库存（注意：编辑页面路径是 /inventory/stock-transfers/edit，不是 /inventory/transfers）
    '/inventory/stock-transfers': 'stock-transfer',
    '/inventory/stock-checks': 'stock-check',
    '/inventory/other-issues': 'other-issue',
    '/inventory/other-receipts': 'other-receipt',
    '/inventory/material-requisitions': 'material-requisition',
    // 生产
    '/production/workorders': 'work-order',

    '/production/operation-reports': 'operation-report',
    '/production/receipts': 'production-receipt',
    '/production/returns': 'production-return',
    '/production/supplements': 'production-supplement',
    '/production/mrp-plans': 'mrp-plan',
    '/production/assemble': 'production-assemble',
    '/production/disassemble': 'production-disassemble',
    // 委外
    '/subcontract/orders': 'subcontract-order',
    '/subcontract/picks': 'subcontract-pick',
    '/subcontract/inbounds': 'subcontract-inbound',
    '/subcontract/supplements': 'subcontract-supplement',
    '/subcontract/returns': 'subcontract-return',
    // 财务
    '/finance/payment': 'payment-voucher',
    '/finance/receipt': 'payment-receipt',
    '/finance/sale-pre-receipts': 'sale-pre-receipt',
    '/finance/purchase-pre-payments': 'purchase-pre-payment',
    '/finance/operation-expense': 'operation-expense',
    // 基础数据
    '/basedata/material': 'material',
    '/basedata/customer': 'customer',
    '/basedata/supplier': 'supplier',
    // 主数据
    '/masterdata/bom': 'bom',
  }

  for (const [prefix, key] of Object.entries(routeMap)) {
    // 路径以前缀开头，且前缀后面是 /new、/:id、/edit、/edit/:id 或为空
    if (path === prefix || path.startsWith(prefix + '/')) {
      const remainder = path.slice(prefix.length)
      // remainder 为空（列表页）、/new、/123、/edit、/edit/123 等
      // 教程只在编辑页面显示，但为了容错，只要有匹配就返回步骤
      // isEditPage 的判断由 MainLayout 中的 hasTutorial computed 控制
      const builder = onboardingSteps[key]
      if (builder) return builder()
    }
  }
  return null
}
