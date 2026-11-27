/**
 * ERP系统真实页面截图脚本
 * 使用 Playwright headless 模式，自动登录并逐页截图
 */
const { chromium } = require('playwright')
const path = require('path')
const fs = require('fs')

const BASE_URL = 'http://localhost:5173'
const SCREENSHOT_DIR = path.join(__dirname, 'images', 'screenshots')

// 确保截图目录存在
if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true })
}

// 所有需要截图的页面
const pages = [
  // 基础资料
  { url: '/basedata/customer', file: 'customer-list.png' },
  { url: '/basedata/supplier', file: 'supplier-list.png' },
  { url: '/basedata/material', file: 'material-list.png' },
  { url: '/basedata/material-category', file: 'material-category-list.png' },
  { url: '/basedata/warehouse', file: 'warehouse-list.png' },
  { url: '/basedata/location', file: 'location-list.png' },
  { url: '/basedata/department', file: 'department-list.png' },
  { url: '/basedata/employee', file: 'employee-list.png' },
  { url: '/basedata/unit', file: 'unit-list.png' },
  { url: '/basedata/settle', file: 'settle-list.png' },
  { url: '/basedata/payment-term', file: 'payment-term-list.png' },
  { url: '/basedata/currency', file: 'currency-list.png' },
  { url: '/basedata/account', file: 'account-list.png' },
  { url: '/basedata/price-level', file: 'price-level-list.png' },
  { url: '/basedata/material-code-rule', file: 'material-code-rule-list.png' },
  { url: '/masterdata/bom', file: 'bom-list.png' },

  // 销售管理
  { url: '/sale/quotes', file: 'sale-quote-list.png' },
  { url: '/sale/quotes/new', file: 'sale-quote-edit.png' },
  { url: '/sale/orders', file: 'sale-order-list.png' },
  { url: '/sale/orders/new', file: 'sale-order-edit.png' },
  { url: '/sale/deliveries', file: 'sale-delivery-list.png' },
  { url: '/sale/deliveries/new', file: 'sale-delivery-edit.png' },
  { url: '/sale/returns', file: 'sale-return-list.png' },
  { url: '/sale/returns/new', file: 'sale-return-edit.png' },
  { url: '/sale/contracts', file: 'sale-contract-list.png' },
  { url: '/sale/contracts/new', file: 'sale-contract-edit.png' },
  { url: '/sale/invoices', file: 'sale-invoice-list.png' },
  { url: '/sale/invoices/new', file: 'sale-invoice-edit.png' },

  // 采购管理
  { url: '/purchase/requests', file: 'purchase-request-list.png' },
  { url: '/purchase/requests/new', file: 'purchase-request-edit.png' },
  { url: '/purchase/orders', file: 'purchase-order-list.png' },
  { url: '/purchase/orders/new', file: 'purchase-order-edit.png' },
  { url: '/purchase/inbounds', file: 'purchase-inbound-list.png' },
  { url: '/purchase/inbounds/new', file: 'purchase-inbound-edit.png' },
  { url: '/purchase/returns', file: 'purchase-return-list.png' },
  { url: '/purchase/returns/new', file: 'purchase-return-edit.png' },
  { url: '/purchase/contracts', file: 'purchase-contract-list.png' },
  { url: '/purchase/contracts/new', file: 'purchase-contract-edit.png' },
  { url: '/purchase/invoices', file: 'purchase-invoice-list.png' },
  { url: '/purchase/invoices/new', file: 'purchase-invoice-edit.png' },

  // 库存管理
  { url: '/inventory/current', file: 'inventory-list.png' },
  { url: '/inventory/batches', file: 'batch-list.png' },
  { url: '/inventory/stock-transfers', file: 'stock-transfer-list.png' },
  { url: '/inventory/stock-transfers/edit', file: 'stock-transfer-edit.png' },
  { url: '/inventory/stock-checks', file: 'stock-check-list.png' },
  { url: '/inventory/stock-checks/edit', file: 'stock-check-edit.png' },
  { url: '/inventory/other-issues', file: 'other-issue-list.png' },
  { url: '/inventory/other-issues/edit', file: 'other-issue-edit.png' },
  { url: '/inventory/other-receipts', file: 'other-receipt-list.png' },
  { url: '/inventory/other-receipts/edit', file: 'other-receipt-edit.png' },
  { url: '/inventory/material-requisitions', file: 'material-requisition-list.png' },
  { url: '/inventory/material-requisitions/edit', file: 'material-requisition-edit.png' },
  { url: '/inventory/ledger', file: 'stock-flow-list.png' },

  // 生产管理
  { url: '/production/workorders', file: 'work-order-list.png' },
  { url: '/production/workorders/new', file: 'work-order-edit.png' },
  { url: '/production/receipts', file: 'production-receipt-list.png' },
  { url: '/production/receipts/edit', file: 'production-receipt-edit.png' },
  { url: '/production/returns', file: 'production-return-list.png' },
  { url: '/production/returns/edit', file: 'production-return-edit.png' },
  { url: '/production/supplements', file: 'production-supplement-list.png' },
  { url: '/production/supplements/edit', file: 'production-supplement-edit.png' },
  { url: '/production/operation-report', file: 'operation-report-list.png' },
  { url: '/production/operation-reports/edit', file: 'operation-report-edit.png' },
  { url: '/production/process-transfer', file: 'process-transfer-list.png' },
  { url: '/production/process-transfers/edit', file: 'process-transfer-edit.png' },
  { url: '/production/assemble', file: 'produce-assemble-list.png' },
  { url: '/production/assemble/new', file: 'produce-assemble-edit.png' },
  { url: '/production/disassemble', file: 'produce-disassemble-list.png' },
  { url: '/production/disassemble/new', file: 'produce-disassemble-edit.png' },
  { url: '/production/mrp', file: 'mrp-list.png' },
  { url: '/production/mrp-plan', file: 'mrp-plan-list.png' },
  { url: '/production/routings', file: 'routing-list.png' },
  { url: '/production/work-center', file: 'work-center-list.png' },
  { url: '/production/workstation', file: 'workstation-list.png' },
  { url: '/production/schedule-board', file: 'production-schedule-board.png' },

  // 财务管理
  { url: '/finance/receipt', file: 'payment-receipt-list.png' },
  { url: '/finance/receipt/new', file: 'payment-receipt-edit.png' },
  { url: '/finance/payment', file: 'payment-voucher-list.png' },
  { url: '/finance/payment/new', file: 'payment-voucher-edit.png' },
  { url: '/finance/purchase-pre-payments', file: 'purchase-pre-payment-list.png' },
  { url: '/finance/purchase-pre-payments/new', file: 'purchase-pre-payment-edit.png' },
  { url: '/finance/sale-pre-receipts', file: 'sale-pre-receipt-list.png' },
  { url: '/finance/sale-pre-receipts/new', file: 'sale-pre-receipt-edit.png' },
  { url: '/finance/operation-expense', file: 'operation-expense-list.png' },
  { url: '/finance/operation-expense/new', file: 'operation-expense-edit.png' },
  { url: '/finance/cost-calculation', file: 'cost-calculation-list.png' },
  { url: '/finance/cost-config', file: 'cost-config-list.png' },
  { url: '/finance/balance', file: 'account-balance-list.png' },
  { url: '/finance/vouchers', file: 'voucher-list.png' },

  // 委外管理
  { url: '/subcontract/orders', file: 'subcontract-order-list.png' },
  { url: '/subcontract/orders/new', file: 'subcontract-order-edit.png' },
  { url: '/subcontract/picks', file: 'subcontract-pick-list.png' },
  { url: '/subcontract/picks/new', file: 'subcontract-pick-edit.png' },
  { url: '/subcontract/inbounds', file: 'subcontract-inbound-list.png' },
  { url: '/subcontract/inbounds/new', file: 'subcontract-inbound-edit.png' },
  { url: '/subcontract/returns', file: 'subcontract-return-list.png' },
  { url: '/subcontract/returns/new', file: 'subcontract-return-edit.png' },
  { url: '/subcontract/supplements', file: 'subcontract-supplement-list.png' },
  { url: '/subcontract/supplements/new', file: 'subcontract-supplement-edit.png' },

  // 系统管理
  { url: '/system/settings', file: 'system-settings.png' },
  { url: '/system/ai-data-policy', file: 'ai-data-policy.png' },

  // CRM
  { url: '/crm/dashboard', file: 'crm-dashboard.png' },
  { url: '/crm/customer', file: 'crm-customer-list.png' },
  { url: '/crm/pool', file: 'crm-pool-list.png' },
  { url: '/crm/lead', file: 'crm-lead-list.png' },
  { url: '/crm/opportunity', file: 'crm-opportunity-list.png' },
  { url: '/crm/contact', file: 'crm-contact-list.png' },
  { url: '/crm/follow-up', file: 'crm-follow-up-list.png' },
  { url: '/crm/warning', file: 'crm-warning-list.png' },
  { url: '/crm/complaint', file: 'crm-complaint-list.png' },
  { url: '/crm/survey', file: 'crm-survey-list.png' },
  { url: '/crm/sales-target', file: 'crm-sales-target-list.png' },
  { url: '/crm/funnel', file: 'crm-funnel.png' },

  // 价格管理
  { url: '/sale/price-strategy', file: 'sale-price-strategy-list.png' },
  { url: '/sale/price-strategy/new', file: 'sale-price-strategy-edit.png' },
  { url: '/purchase/price-strategy', file: 'purchase-price-strategy-list.png' },
  { url: '/purchase/price-strategy/new', file: 'purchase-price-strategy-edit.png' },

  // 样品管理
  { url: '/sample/list', file: 'sample-list.png' },
  { url: '/sample/edit', file: 'sample-edit.png' },
  { url: '/freesample/list', file: 'freesample-list.png' },
  { url: '/freesample/edit', file: 'freesample-edit.png' },
]

async function run() {
  console.log(`共 ${pages.length} 个页面需要截图\n`)

  const browser = await chromium.launch({
    headless: true,
    executablePath: 'D:\\erp\\playwright-browsers\\chromium_headless_shell-1228\\chrome-headless-shell-win64\\chrome-headless-shell.exe'
  })
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    locale: 'zh-CN',
  })
  const page = await context.newPage()

  // 1. 登录
  console.log('正在登录系统...')
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' })
  await page.fill('input[placeholder*="用户名"], input[type="text"]', 'admin')
  await page.fill('input[type="password"]', 'admin123')
  await page.click('button:has-text("登录")')
  await page.waitForURL('**/dashboard', { timeout: 10000 }).catch(() => {})
  await page.waitForTimeout(2000)

  // 检查是否登录成功
  const currentUrl = page.url()
  if (currentUrl.includes('/login')) {
    console.log('登录失败！尝试直接设置token...')
    // 开发环境可能跳过登录验证
  }
  console.log(`登录后当前页面: ${currentUrl}`)

  // 2. 逐页截图
  let success = 0
  let fail = 0

  for (const item of pages) {
    const url = `${BASE_URL}${item.url}`
    const filePath = path.join(SCREENSHOT_DIR, item.file)

    try {
      await page.goto(url, { waitUntil: 'networkidle', timeout: 15000 })
      await page.waitForTimeout(2500) // 等待页面完全渲染

      await page.screenshot({
        path: filePath,
        fullPage: false,
      })

      const sizeKB = (fs.statSync(filePath).size / 1024).toFixed(0)
      console.log(`  [OK] ${item.file} (${sizeKB}KB)  <- ${item.url}`)
      success++
    } catch (e) {
      console.log(`  [FAIL] ${item.file} <- ${item.url}: ${e.message.slice(0, 80)}`)
      fail++
    }
  }

  await browser.close()
  console.log(`\n截图完成: 成功 ${success}, 失败 ${fail}, 共 ${pages.length}`)
  console.log(`保存目录: ${SCREENSHOT_DIR}`)
}

run().catch(console.error)
