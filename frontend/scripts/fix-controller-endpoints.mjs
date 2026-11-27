// fix-controller-endpoints.mjs — 批量为 Controller 补齐缺失的状态流转接口
// reject / reverseApprove / void / unvoid / delete
import fs from 'fs'
import path from 'path'

const BACKEND_ROOT = path.resolve('../backend/src/main/java/com/erp')

// Controller 信息列表（来自分析结果）
const controllers = [
  { file: 'sale/controller/SaleQuoteController.java', svc: 'saleQuoteService', prefix: '/quotes', has: { reject: false, reverseApprove: false, void: false, unvoid: false, delete: false } },
  { file: 'sale/controller/SaleContractController.java', svc: 'saleContractService', prefix: '/contracts', has: { reject: false, reverseApprove: false, void: false, unvoid: false, delete: true } },
  { file: 'sale/controller/SaleReturnController.java', svc: 'saleReturnService', prefix: '/returns', has: { reject: false, reverseApprove: false, void: false, unvoid: false, delete: false } },
  { file: 'sale/controller/SaleInvoiceController.java', svc: 'saleInvoiceService', prefix: '/invoices', has: { reject: false, reverseApprove: false, void: false, unvoid: false, delete: false } },
  { file: 'sale/controller/SaleDeliveryController.java', svc: 'saleDeliveryService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: false } },
  { file: 'purchase/controller/PurchaseRequestController.java', svc: 'purchaseRequestService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: false } },
  { file: 'purchase/controller/PurchaseContractController.java', svc: 'purchaseContractService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'purchase/controller/PurchaseInboundController.java', svc: 'purchaseInboundService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: false } },
  { file: 'purchase/controller/PurchaseReturnController.java', svc: 'purchaseReturnService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: false } },
  { file: 'purchase/controller/PurchaseInvoiceController.java', svc: 'purchaseInvoiceService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: false } },
  { file: 'inventory/controller/OtherIssueController.java', svc: 'otherIssueService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'inventory/controller/OtherReceiptController.java', svc: 'otherReceiptService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'inventory/controller/StockTransferController.java', svc: 'stockTransferService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'inventory/controller/StockCheckController.java', svc: 'stockCheckService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'inventory/controller/MaterialRequisitionController.java', svc: 'materialRequisitionService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'produce/assemble/controller/ProduceAssembleController.java', svc: 'produceAssembleService', prefix: '', has: { reject: true, reverseApprove: false, void: false, unvoid: false, delete: true } },
  { file: 'produce/assemble/controller/ProduceDisassembleController.java', svc: 'produceDisassembleService', prefix: '', has: { reject: true, reverseApprove: false, void: false, unvoid: false, delete: true } },
  { file: 'production/controller/ProductionReceiptController.java', svc: 'productionReceiptService', prefix: '', has: { reject: false, reverseApprove: false, void: false, unvoid: false, delete: true } },
  { file: 'production/controller/OperationReportController.java', svc: 'operationReportService', prefix: '', has: { reject: false, reverseApprove: false, void: false, unvoid: false, delete: true } },
  { file: 'production/controller/ProcessTransferController.java', svc: 'processTransferService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'subcontract/order/controller/SubcontractOrderController.java', svc: 'subcontractOrderService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'subcontract/pick/controller/SubcontractPickController.java', svc: 'subcontractPickService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'subcontract/returnn/controller/SubcontractReturnController.java', svc: 'subcontractReturnService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'subcontract/inbound/controller/SubcontractInboundController.java', svc: 'subcontractInboundService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'subcontract/supplement/controller/SubcontractSupplementController.java', svc: 'subcontractSupplementService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'finance/prepayment/purchase/controller/PurchasePrePaymentController.java', svc: 'purchasePrePaymentService', prefix: '', has: { reject: false, reverseApprove: false, void: true, unvoid: false, delete: true } },
  { file: 'finance/prepayment/sale/controller/SalePreReceiptController.java', svc: 'salePreReceiptService', prefix: '', has: { reject: false, reverseApprove: false, void: true, unvoid: false, delete: true } },
  { file: 'finance/controller/OperationExpenseController.java', svc: 'operationExpenseService', prefix: '', has: { reject: false, reverseApprove: false, void: false, unvoid: false, delete: true } },
  { file: 'finance/cost/calculation/controller/CostCalculationController.java', svc: 'costCalculationService', prefix: '', has: { reject: false, reverseApprove: false, void: false, unvoid: false, delete: true } },
  { file: 'sample/controller/SampleOrderController.java', svc: 'sampleOrderService', prefix: '', has: { reject: false, reverseApprove: true, void: false, unvoid: false, delete: true } },
  { file: 'freesample/controller/FreeSampleOrderController.java', svc: 'freeSampleOrderService', prefix: '', has: { reject: false, reverseApprove: false, void: false, unvoid: false, delete: true } },
  { file: 'masterdata/controller/BomController.java', svc: 'bomService', prefix: '', has: { reject: true, reverseApprove: false, void: false, unvoid: false, delete: true } },
  { file: 'price/purchase/controller/PurchasePriceStrategyController.java', svc: 'purchasePriceStrategyService', prefix: '', has: { reject: false, reverseApprove: false, void: false, unvoid: false, delete: true } },
  { file: 'price/sale/controller/SalePriceStrategyController.java', svc: 'salePriceStrategyService', prefix: '', has: { reject: false, reverseApprove: false, void: false, unvoid: false, delete: true } },
]

// 生成方法代码
function generateMethod(methodType, svc, prefix) {
  const pathPrefix = prefix || ''
  const base = pathPrefix ? `${pathPrefix}/{id}` : '/{id}'

  switch (methodType) {
    case 'reject':
      return `    /** 驳回: 待审核 -> 草稿 */
    @PostMapping("${base}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        ${svc}.reject(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }`

    case 'reverseApprove':
      return `    /** 反审批: 已审核 -> 草稿 */
    @PostMapping("${base}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        ${svc}.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }`

    case 'void':
      return `    /** 作废 */
    @PostMapping("${base}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        ${svc}.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }`

    case 'unvoid':
      return `    /** 反作废 */
    @PostMapping("${base}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        ${svc}.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }`

    case 'delete':
      return `    /** 删除（仅草稿态允许） */
    @DeleteMapping("${base}")
    public Result<Void> delete(@PathVariable Long id) {
        ${svc}.deleteBill(id);
        return Result.success();
    }`
  }
}

let fixed = 0

for (const ctrl of controllers) {
  const filePath = path.join(BACKEND_ROOT, ctrl.file)
  if (!fs.existsSync(filePath)) {
    console.log(`[SKIP] 文件不存在: ${ctrl.file}`)
    continue
  }

  let content = fs.readFileSync(filePath, 'utf8')

  // 检查缺失的方法
  const methodsToAdd = []
  const methodTypes = ['reject', 'reverseApprove', 'void', 'unvoid', 'delete']

  for (const mt of methodTypes) {
    if (!ctrl.has[mt]) {
      // 再次确认文件中确实没有这个方法（避免分析数据过时）
      const methodNames = {
        reject: 'reject',
        reverseApprove: 'reverseApprove',
        void: 'voidBill',
        unvoid: 'unvoidBill',
        delete: 'delete',
      }
      // 检查是否已有方法定义（排除 import 行和注释）
      const methodRegex = new RegExp(`public\\s+Result\\s+${methodNames[mt]}\\s*\\(`)
      if (!methodRegex.test(content)) {
        methodsToAdd.push(mt)
      }
    }
  }

  if (methodsToAdd.length === 0) continue

  // 检查是否已导入 HttpServletRequest
  const needHttpServletRequest = methodsToAdd.some(mt => mt !== 'delete')
  if (needHttpServletRequest && !content.includes('import jakarta.servlet.http.HttpServletRequest')) {
    // 在最后一个 import 后添加
    content = content.replace(
      /(import [^;]+;\s*\n)(?!import)/,
      `$1import jakarta.servlet.http.HttpServletRequest;\n`
    )
  }

  // 检查是否已导入 Result
  if (!content.includes('import com.erp.common.Result')) {
    content = content.replace(
      /(import [^;]+;\s*\n)(?!import)/,
      `$1import com.erp.common.Result;\n`
    )
  }

  // 生成方法代码
  const methodsCode = methodsToAdd
    .map(mt => generateMethod(mt, ctrl.svc, ctrl.prefix))
    .join('\n\n')

  // 在类的最后一个 } 之前插入
  // 找到文件中最后一个 } 的位置
  const lastBrace = content.lastIndexOf('}')
  if (lastBrace === -1) {
    console.log(`[ERROR] 无法找到类结束括号: ${ctrl.file}`)
    continue
  }

  // 在最后一个 } 之前插入方法
  content = content.slice(0, lastBrace) + '\n' + methodsCode + '\n\n' + content.slice(lastBrace)

  fs.writeFileSync(filePath, content, 'utf8')
  console.log(`[FIX] ${ctrl.file}: 添加 ${methodsToAdd.join(', ')} 接口`)
  fixed++
}

console.log(`\n共修复 ${fixed} 个 Controller 文件`)
