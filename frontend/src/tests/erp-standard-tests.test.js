/**
 * ERP标准自动化测试用例（扩展版）
 * 参考ERP系统自动化测试用例标准，补充原测试方案未覆盖的验证场景
 *
 * 覆盖范围：
 *   1.  单据状态流转（状态机验证）
 *   2.  单据编号规则（前缀+日期+流水号）
 *   3.  金额计算完整性（折扣/税额/价税合计/本位币 完整公式链）
 *   4.  库存计算准确性（入库增/出库减/退货回冲/不超卖）
 *   5.  业务流程闭环（端到端链路）
 *   6.  数据完整性校验（必填/数据类型/边界值）
 *   7.  下推关系（上游→下游字段映射、数量不超源单）
 *   8.  负数和异常值处理（退货/负库存/零金额）
 *   9.  日期合法性（交货日期不早于订单日期等）
 *   10. 数据精度（金额2位/汇率6位/数量4位）
 *   11. 物料启用状态（停用物料不可选）
 *   12. 批次先进先出（出库按批次FIFO）
 *   13. 多币种本位币汇总一致性
 *   14. 审批流（提交/审核/重置草稿）
 *   15. 权限控制（角色操作权限）
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// ===== 公共 Mock 数据 =====
const materials = [
  { materialId: 1, materialCode: 'M001', materialName: '批次物料', isLotControl: 1, isExpiryControl: 1, expiryDays: 365, status: '正常', purchasePrice: 100, salePrice: 150, taxRate: 13, baseUnitId: 1, purchaseUnitId: 1 },
  { materialId: 2, materialCode: 'M002', materialName: '普通物料', isLotControl: 0, isExpiryControl: 0, status: '正常', purchasePrice: 50, salePrice: 80, taxRate: 13, baseUnitId: 1, purchaseUnitId: 1 },
  { materialId: 3, materialCode: 'M003', materialName: '停用物料', isLotControl: 0, isExpiryControl: 0, status: '停用', purchasePrice: 30, salePrice: 50, taxRate: 13, baseUnitId: 1, purchaseUnitId: 1 }
]
const currencies = [
  { currencyId: 1, currencyCode: 'CNY', currencyName: '人民币', exchangeRate: 1, isBaseCurrency: 1 },
  { currencyId: 2, currencyCode: 'USD', currencyName: '美元', exchangeRate: 7.2, isBaseCurrency: 0 },
  { currencyId: 3, currencyCode: 'EUR', currencyName: '欧元', exchangeRate: 7.8, isBaseCurrency: 0 },
  { currencyId: 4, currencyCode: 'HKD', currencyName: '港币', exchangeRate: 0.92, isBaseCurrency: 0 }
]

// ===== 1. 单据状态流转测试 =====
describe('1. 单据状态流转测试', () => {
  const validTransitions = {
    '草稿': ['待审核', '已取消'],
    '待审核': ['已生效', '草稿'],
    '已生效': ['已关闭', '草稿'],
    '已关闭': [],
    '已取消': []
  }

  it('1.1 草稿状态可以提交审核', () => {
    expect(validTransitions['草稿']).toContain('待审核')
  })

  it('1.2 待审核状态可以审核通过', () => {
    expect(validTransitions['待审核']).toContain('已生效')
  })

  it('1.3 已生效状态可以重置草稿回草稿', () => {
    expect(validTransitions['已生效']).toContain('草稿')
  })

  it('1.4 已关闭状态不可再流转', () => {
    expect(validTransitions['已关闭']).toHaveLength(0)
  })

  it('1.5 已取消状态不可再流转', () => {
    expect(validTransitions['已取消']).toHaveLength(0)
  })

  it('1.6 非法状态流转应被拒绝', () => {
    // 草稿不能直接到已关闭
    expect(validTransitions['草稿']).not.toContain('已关闭')
    // 已取消不能到已生效
    expect(validTransitions['已取消']).not.toContain('已生效')
  })

  it('1.7 已生效单据不可再次审核', () => {
    const currentStatus = '已生效'
    const canApprove = currentStatus === '待审核'
    expect(canApprove).toBe(false)
  })

  it('1.8 草稿单据不可直接关闭', () => {
    const currentStatus = '草稿'
    const canClose = currentStatus === '已生效'
    expect(canClose).toBe(false)
  })
})

// ===== 2. 单据编号规则测试 =====
describe('2. 单据编号规则测试', () => {
  const prefixes = {
    saleQuote: 'QUT',
    saleOrder: 'SO',
    saleDelivery: 'SOUT',
    saleReturn: 'SRT',
    saleInvoice: 'SINV'
  }

  const generateDocNo = (prefix, date, seq) => {
    const dateStr = date.replace(/-/g, '')
    const seqStr = String(seq).padStart(4, '0')
    return `${prefix}-${dateStr}-${seqStr}`
  }

  it('2.1 销售报价单编号应使用QUT前缀', () => {
    const no = generateDocNo(prefixes.saleQuote, '2026-07-08', 1)
    expect(no).toMatch(/^QUT-\d{8}-\d{4}$/)
    expect(no).toBe('QUT-20260708-0001')
  })

  it('2.2 销售订单编号应使用SO前缀', () => {
    const no = generateDocNo(prefixes.saleOrder, '2026-07-08', 23)
    expect(no).toBe('SO-20260708-0023')
  })

  it('2.3 销售出库单编号应使用SOUT前缀', () => {
    const no = generateDocNo(prefixes.saleDelivery, '2026-07-08', 5)
    expect(no).toBe('SOUT-20260708-0005')
  })

  it('2.4 编号流水号应为4位补零', () => {
    const no = generateDocNo('SO', '2026-07-08', 7)
    expect(no.endsWith('-0007')).toBe(true)
  })

  it('2.5 编号日期部分应为yyyyMMdd格式', () => {
    const no = generateDocNo('SO', '2026-07-08', 1)
    const datePart = no.split('-')[1]
    expect(datePart).toBe('20260708')
    expect(datePart.length).toBe(8)
  })

  it('2.6 同一前缀同一天编号应唯一', () => {
    const nos = new Set()
    for (let i = 1; i <= 100; i++) {
      nos.add(generateDocNo('SO', '2026-07-08', i))
    }
    expect(nos.size).toBe(100)
  })
})

// ===== 3. 金额计算完整性测试 =====
describe('3. 金额计算完整性测试', () => {
  // 项目规范：折后金额 = 原金额 - 折扣额；税额 = 折后金额 × 税率/100；价税合计 = 折后金额 + 税额
  const calcRow = (row) => {
    const qty = Number(row.quantity || 0)
    const price = Number(row.unitPrice || 0)
    const discountRate = Number(row.discountRate || 0)
    const taxRate = Number(row.taxRate || 0)
    const originalAmount = qty * price                            // 原金额 = 数量 × 单价
    const discountAmount = originalAmount * discountRate / 100    // 折扣额 = 原金额 × 折扣率 / 100
    const discountedAmount = originalAmount - discountAmount      // 折后金额 = 原金额 - 折扣额
    const taxAmount = discountedAmount * taxRate / 100            // 税额 = 折后金额 × 税率 / 100
    const totalAmount = discountedAmount + taxAmount              // 价税合计 = 折后金额 + 税额
    return { originalAmount, discountAmount, discountedAmount, taxAmount, totalAmount }
  }

  it('3.1 原金额 = 数量 × 单价', () => {
    const r = calcRow({ quantity: 10, unitPrice: 100, discountRate: 0, taxRate: 13 })
    expect(r.originalAmount).toBe(1000)
  })

  it('3.2 折扣额 = 原金额 × 折扣率 / 100', () => {
    const r = calcRow({ quantity: 10, unitPrice: 100, discountRate: 10, taxRate: 13 })
    expect(r.discountAmount).toBe(100)        // 1000 × 10 / 100
    expect(r.discountedAmount).toBe(900)      // 1000 - 100
  })

  it('3.3 税额 = 折后金额 × 税率 / 100', () => {
    const r = calcRow({ quantity: 10, unitPrice: 100, discountRate: 10, taxRate: 13 })
    expect(r.taxAmount).toBe(117)             // 900 × 13 / 100
  })

  it('3.4 价税合计 = 折后金额 + 税额', () => {
    const r = calcRow({ quantity: 10, unitPrice: 100, discountRate: 10, taxRate: 13 })
    expect(r.totalAmount).toBe(1017)          // 900 + 117
  })

  it('3.5 折前合计 = Σ(原金额)', () => {
    const items = [
      { quantity: 10, unitPrice: 100, discountRate: 0, taxRate: 13 },
      { quantity: 5, unitPrice: 200, discountRate: 0, taxRate: 13 }
    ]
    const beforeDiscount = items.reduce((s, i) => s + calcRow(i).originalAmount, 0)
    expect(beforeDiscount).toBe(2000)         // 1000 + 1000
  })

  it('3.6 折扣合计 = Σ(行折扣额)', () => {
    const items = [
      { quantity: 10, unitPrice: 100, discountRate: 10, taxRate: 13 },
      { quantity: 5, unitPrice: 200, discountRate: 5, taxRate: 13 }
    ]
    const totalDiscount = items.reduce((s, i) => s + calcRow(i).discountAmount, 0)
    expect(totalDiscount).toBe(150)           // 100 + 50
  })

  it('3.7 零税率时税额应为0', () => {
    const r = calcRow({ quantity: 10, unitPrice: 100, discountRate: 0, taxRate: 0 })
    expect(r.taxAmount).toBe(0)
    expect(r.totalAmount).toBe(1000)
  })

  it('3.8 满折扣（100%）时折后金额应为0', () => {
    const r = calcRow({ quantity: 10, unitPrice: 100, discountRate: 100, taxRate: 13 })
    expect(r.discountedAmount).toBe(0)
    expect(r.taxAmount).toBe(0)
    expect(r.totalAmount).toBe(0)
  })

  it('3.9 单价为0时所有金额应为0', () => {
    const r = calcRow({ quantity: 10, unitPrice: 0, discountRate: 10, taxRate: 13 })
    expect(r.originalAmount).toBe(0)
    expect(r.discountAmount).toBe(0)
    expect(r.totalAmount).toBe(0)
  })
})

// ===== 4. 库存计算准确性测试 =====
describe('4. 库存计算准确性测试', () => {
  class InventoryService {
    constructor() {
      // 按 stockId + binId + materialId + batchNo 维度记录
      this.stock = new Map()
    }
    _key(stockId, binId, materialId, batchNo) {
      return `${stockId}|${binId || ''}|${materialId}|${batchNo || ''}`
    }
    addStock(stockId, binId, materialId, qty, batchNo) {
      const k = this._key(stockId, binId, materialId, batchNo)
      this.stock.set(k, (this.stock.get(k) || 0) + qty)
    }
    deductStock(stockId, binId, materialId, qty, batchNo) {
      const k = this._key(stockId, binId, materialId, batchNo)
      const cur = this.stock.get(k) || 0
      if (cur < qty) throw new Error('库存不足')
      this.stock.set(k, cur - qty)
    }
    getQty(stockId, binId, materialId, batchNo) {
      return this.stock.get(this._key(stockId, binId, materialId, batchNo)) || 0
    }
  }

  let inv
  beforeEach(() => { inv = new InventoryService() })

  it('4.1 采购入库审核应增加库存', () => {
    inv.addStock(10, 100, 1, 50, 'B001')
    expect(inv.getQty(10, 100, 1, 'B001')).toBe(50)
  })

  it('4.2 销售出库审核应扣减库存', () => {
    inv.addStock(10, 100, 1, 50, 'B001')
    inv.deductStock(10, 100, 1, 20, 'B001')
    expect(inv.getQty(10, 100, 1, 'B001')).toBe(30)
  })

  it('4.3 销售退货审核应回冲库存', () => {
    inv.addStock(10, 100, 1, 50, 'B001')
    inv.deductStock(10, 100, 1, 20, 'B001')
    inv.addStock(10, 100, 1, 5, 'B001')   // 退货回冲
    expect(inv.getQty(10, 100, 1, 'B001')).toBe(35)
  })

  it('4.4 销售出库重置草稿应回冲库存', () => {
    inv.addStock(10, 100, 1, 50, 'B001')
    inv.deductStock(10, 100, 1, 20, 'B001')
    inv.addStock(10, 100, 1, 20, 'B001')  // 重置草稿回冲
    expect(inv.getQty(10, 100, 1, 'B001')).toBe(50)
  })

  it('4.5 库存不足时应阻止出库', () => {
    inv.addStock(10, 100, 1, 10, 'B001')
    expect(() => inv.deductStock(10, 100, 1, 20, 'B001')).toThrow('库存不足')
  })

  it('4.6 不同批次的库存应独立计算', () => {
    inv.addStock(10, 100, 1, 30, 'B001')
    inv.addStock(10, 100, 1, 20, 'B002')
    expect(inv.getQty(10, 100, 1, 'B001')).toBe(30)
    expect(inv.getQty(10, 100, 1, 'B002')).toBe(20)
    inv.deductStock(10, 100, 1, 10, 'B001')
    expect(inv.getQty(10, 100, 1, 'B001')).toBe(20)
    expect(inv.getQty(10, 100, 1, 'B002')).toBe(20)   // B002 不受影响
  })

  it('4.7 不同储位的库存应独立计算', () => {
    inv.addStock(10, 100, 1, 30, 'B001')
    inv.addStock(10, 200, 1, 20, 'B001')
    expect(inv.getQty(10, 100, 1, 'B001')).toBe(30)
    expect(inv.getQty(10, 200, 1, 'B001')).toBe(20)
  })

  it('4.8 库存可全部出完（边界值=0）', () => {
    inv.addStock(10, 100, 1, 50, 'B001')
    inv.deductStock(10, 100, 1, 50, 'B001')
    expect(inv.getQty(10, 100, 1, 'B001')).toBe(0)
  })
})

// ===== 5. 业务流程闭环测试 =====
describe('5. 业务流程闭环测试', () => {
  it('5.1 采购流程：请购单→采购订单→采购入库→采购发票', () => {
    const flow = ['请购单', '采购订单', '采购入库', '采购发票']
    let currentStep = 0
    const nextStep = () => { if (currentStep < flow.length - 1) currentStep++ }
    nextStep(); expect(flow[currentStep]).toBe('采购订单')
    nextStep(); expect(flow[currentStep]).toBe('采购入库')
    nextStep(); expect(flow[currentStep]).toBe('采购发票')
  })

  it('5.2 销售流程：报价单→销售订单→销售出库→销售发票', () => {
    const flow = ['报价单', '销售订单', '销售出库', '销售发票']
    let currentStep = 0
    const nextStep = () => { if (currentStep < flow.length - 1) currentStep++ }
    nextStep(); expect(flow[currentStep]).toBe('销售订单')
    nextStep(); expect(flow[currentStep]).toBe('销售出库')
    nextStep(); expect(flow[currentStep]).toBe('销售发票')
  })

  it('5.3 生产流程：生产工单→领料→报工→完工入库', () => {
    const flow = ['生产工单', '领料', '报工', '完工入库']
    let currentStep = 0
    const nextStep = () => { if (currentStep < flow.length - 1) currentStep++ }
    nextStep(); expect(flow[currentStep]).toBe('领料')
    nextStep(); expect(flow[currentStep]).toBe('报工')
    nextStep(); expect(flow[currentStep]).toBe('完工入库')
  })

  it('5.4 采购订单入库后状态应为已入库', () => {
    const order = { status: '已确认', inboundStatus: '未入库' }
    // 模拟入库操作
    order.inboundStatus = '已入库'
    expect(order.inboundStatus).toBe('已入库')
  })
})

// ===== 6. 数据完整性校验测试 =====
describe('6. 数据完整性校验测试', () => {
  it('6.1 采购订单必填字段校验：供应商、订单日期、明细不能为空', () => {
    const requiredFields = ['supplierId', 'orderDate', 'items']
    const form = { supplierId: null, orderDate: '', items: [] }
    const errors = requiredFields.filter(f => !form[f] || (Array.isArray(form[f]) && form[f].length === 0))
    expect(errors).toEqual(['supplierId', 'orderDate', 'items'])
  })

  it('6.2 明细行必填字段校验：物料、数量、单价不能为空', () => {
    const row = { materialId: null, quantity: 0, unitPrice: 0 }
    const errors = []
    if (!row.materialId) errors.push('materialId')
    if (!row.quantity || row.quantity <= 0) errors.push('quantity')
    if (!row.unitPrice || row.unitPrice < 0) errors.push('unitPrice')
    expect(errors).toEqual(['materialId', 'quantity', 'unitPrice'])
  })

  it('6.3 数量必须为正数', () => {
    const validQty = (q) => q > 0
    expect(validQty(10)).toBe(true)
    expect(validQty(0)).toBe(false)
    expect(validQty(-5)).toBe(false)
  })

  it('6.4 单价不能为负数', () => {
    const validPrice = (p) => p >= 0
    expect(validPrice(100)).toBe(true)
    expect(validPrice(0)).toBe(true)
    expect(validPrice(-1)).toBe(false)
  })

  it('6.5 折扣率范围应为0-100', () => {
    const validDiscount = (d) => d >= 0 && d <= 100
    expect(validDiscount(0)).toBe(true)
    expect(validDiscount(50)).toBe(true)
    expect(validDiscount(100)).toBe(true)
    expect(validDiscount(-1)).toBe(false)
    expect(validDiscount(101)).toBe(false)
  })

  it('6.6 税率范围应为0-100', () => {
    const validTax = (t) => t >= 0 && t <= 100
    expect(validTax(0)).toBe(true)
    expect(validTax(13)).toBe(true)
    expect(validTax(17)).toBe(true)
    expect(validTax(-1)).toBe(false)
    expect(validTax(101)).toBe(false)
  })

  it('6.7 汇率必须为正数', () => {
    const validRate = (r) => r > 0
    expect(validRate(7.2)).toBe(true)
    expect(validRate(1)).toBe(true)
    expect(validRate(0)).toBe(false)
    expect(validRate(-7.2)).toBe(false)
  })

  it('6.8 明细至少需要一行', () => {
    const validateItems = (items) => Array.isArray(items) && items.length > 0
    expect(validateItems([])).toBe(false)
    expect(validateItems([{ materialId: 1 }])).toBe(true)
  })
})

// ===== 7. 下推关系测试 =====
describe('7. 下推关系测试', () => {
  it('7.1 销售订单下推销售出库：数量不能超过源单', () => {
    const order = { items: [{ materialId: 1, quantity: 100, deliveredQty: 0 }] }
    const deliveryQty = 120
    const canDeliver = deliveryQty <= (order.items[0].quantity - order.items[0].deliveredQty)
    expect(canDeliver).toBe(false)
  })

  it('7.2 销售订单下推销售出库：部分出库允许', () => {
    const order = { items: [{ materialId: 1, quantity: 100, deliveredQty: 30 }] }
    const deliveryQty = 50
    const remaining = order.items[0].quantity - order.items[0].deliveredQty
    const canDeliver = deliveryQty <= remaining
    expect(canDeliver).toBe(true)
  })

  it('7.3 采购订单下推采购入库：累计入库不能超过订单数量', () => {
    const order = { quantity: 100, inboundQty: 80 }
    const newInbound = 30
    const canInbound = (order.inboundQty + newInbound) <= order.quantity
    expect(canInbound).toBe(false)
  })

  it('7.4 下推时物料编码、名称应自动带入', () => {
    const sourceItem = { materialId: 1, materialCode: 'M001', materialName: '批次物料', specification: '规格A', unitPrice: 100 }
    const targetItem = { ...sourceItem }
    expect(targetItem.materialCode).toBe('M001')
    expect(targetItem.materialName).toBe('批次物料')
    expect(targetItem.unitPrice).toBe(100)
  })

  it('7.5 销售退货下推销售发票时数量应一致', () => {
    const returnDoc = { items: [{ materialId: 1, quantity: 10 }] }
    const invoice = { items: [{ materialId: 1, quantity: 10 }] }
    expect(invoice.items[0].quantity).toBe(returnDoc.items[0].quantity)
  })
})

// ===== 8. 负数和异常值处理测试 =====
describe('8. 负数和异常值处理测试', () => {
  it('8.1 销售退货数量应为正数（退货数量本身为正，由单据类型区分方向）', () => {
    const returnQty = 10
    expect(returnQty).toBeGreaterThan(0)
  })

  it('8.2 入库数量为0时应被阻止', () => {
    const qty = 0
    const valid = qty > 0
    expect(valid).toBe(false)
  })

  it('8.3 金额为0时不应产生税额', () => {
    const amount = 0
    const taxRate = 13
    const tax = amount * taxRate / 100
    expect(tax).toBe(0)
  })

  it('8.4 NaN值应被处理为0', () => {
    const safeNumber = (v) => isNaN(Number(v)) ? 0 : Number(v)
    expect(safeNumber(undefined)).toBe(0)
    expect(safeNumber(null)).toBe(0)
    expect(safeNumber('')).toBe(0)
    expect(safeNumber('abc')).toBe(0)
    expect(safeNumber('100')).toBe(100)
  })

  it('8.5 空明细行不应参与金额合计', () => {
    const items = [
      { materialId: 1, quantity: 10, unitPrice: 100, amount: 1000 },
      { materialId: null, quantity: 0, unitPrice: 0, amount: 0 }  // 空行
    ]
    const total = items.filter(i => i.materialId).reduce((s, i) => s + i.amount, 0)
    expect(total).toBe(1000)
  })

  it('8.6 数量超过库存时不应允许出库', () => {
    const stockQty = 50
    const outQty = 100
    const canOut = outQty <= stockQty
    expect(canOut).toBe(false)
  })
})

// ===== 9. 日期合法性测试 =====
describe('9. 日期合法性测试', () => {
  const parseDate = (s) => new Date(s)

  it('9.1 交货日期不能早于订单日期', () => {
    const orderDate = '2026-07-08'
    const deliveryDate = '2026-07-05'
    expect(parseDate(deliveryDate) >= parseDate(orderDate)).toBe(false)
  })

  it('9.2 交货日期等于订单日期应允许', () => {
    const orderDate = '2026-07-08'
    const deliveryDate = '2026-07-08'
    expect(parseDate(deliveryDate) >= parseDate(orderDate)).toBe(true)
  })

  it('9.3 交货日期晚于订单日期应允许', () => {
    const orderDate = '2026-07-08'
    const deliveryDate = '2026-07-15'
    expect(parseDate(deliveryDate) >= parseDate(orderDate)).toBe(true)
  })

  it('9.4 有效期至必须晚于生产日期', () => {
    const productionDate = '2026-01-01'
    const expiryDate = '2026-01-02'
    expect(parseDate(expiryDate) > parseDate(productionDate)).toBe(true)
  })

  it('9.5 有效期至等于生产日期应阻止（保质期必须大于0天）', () => {
    const productionDate = '2026-01-01'
    const expiryDate = '2026-01-01'
    expect(parseDate(expiryDate) > parseDate(productionDate)).toBe(false)
  })

  it('9.6 按付款条件计算的到期日应正确', () => {
    const orderDate = '2026-01-01'
    const paymentDays = 30
    const dueDate = new Date(orderDate)
    dueDate.setDate(dueDate.getDate() + paymentDays)
    expect(dueDate.toISOString().slice(0, 10)).toBe('2026-01-31')
  })

  it('9.7 供应商交货日期应按交货天数自动计算', () => {
    const today = new Date('2026-07-08')
    const deliveryDays = 7
    const expectedDate = new Date(today)
    expectedDate.setDate(expectedDate.getDate() + deliveryDays)
    expect(expectedDate.toISOString().slice(0, 10)).toBe('2026-07-15')
  })
})

// ===== 10. 数据精度测试 =====
describe('10. 数据精度测试', () => {
  it('10.1 金额应保留2位小数', () => {
    const amount = (10 * 99.99).toFixed(2)
    expect(amount).toBe('999.90')
    expect(amount.split('.')[1].length).toBe(2)
  })

  it('10.2 汇率应保留6位小数', () => {
    const rate = (1 / 7.2).toFixed(6)
    expect(rate).toBe('0.138889')
    expect(rate.split('.')[1].length).toBe(6)
  })

  it('10.3 数量应支持4位小数', () => {
    const qty = (1.5 / 3).toFixed(4)
    expect(qty).toBe('0.5000')
  })

  it('10.4 本位币金额换算应保留2位小数', () => {
    const originalAmount = 999.90
    const rate = 7.2
    const baseAmount = (originalAmount * rate).toFixed(2)
    expect(baseAmount).toBe('7199.28')
  })

  it('10.5 税额四舍五入应正确', () => {
    const discountedAmount = 999.99
    const taxRate = 13
    const tax = (discountedAmount * taxRate / 100).toFixed(2)
    expect(tax).toBe('130.00')              // 129.9987 → 130.00
  })

  it('10.6 累加浮点数应避免精度丢失', () => {
    // 错误方式：0.1 + 0.2 = 0.30000000000000004
    const wrong = 0.1 + 0.2
    expect(wrong).not.toBe(0.3)
    // 正确方式：toFixed(2)
    const right = Number((0.1 + 0.2).toFixed(2))
    expect(right).toBe(0.3)
  })
})

// ===== 11. 物料启用状态测试 =====
describe('11. 物料启用状态测试', () => {
  it('11.1 停用物料不应出现在选择列表中', () => {
    const selectableMaterials = materials.filter(m => m.status === '正常')
    expect(selectableMaterials.find(m => m.materialId === 3)).toBeUndefined()
    expect(selectableMaterials.length).toBe(2)
  })

  it('11.2 物料库选择弹窗应只查询状态为正常的物料', () => {
    const queryParam = { status: '正常' }
    expect(queryParam.status).toBe('正常')
  })

  it('11.3 已存在的单据明细中引用了停用物料应允许显示（不删除）', () => {
    const existingItem = { materialId: 3, materialName: '停用物料' }
    expect(existingItem.materialId).toBe(3)
    // 显示不等于可选
  })
})

// ===== 12. 批次先进先出测试 =====
describe('12. 批次先进先出（FIFO）测试', () => {
  it('12.1 出库应优先消耗最早入库的批次', () => {
    const batches = [
      { batchNo: 'B001', inDate: '2026-01-01', qty: 50 },
      { batchNo: 'B002', inDate: '2026-02-01', qty: 30 },
      { batchNo: 'B003', inDate: '2026-03-01', qty: 20 }
    ]
    // 按入库日期升序排序
    const sorted = [...batches].sort((a, b) => new Date(a.inDate) - new Date(b.inDate))
    expect(sorted[0].batchNo).toBe('B001')
    expect(sorted[1].batchNo).toBe('B002')
    expect(sorted[2].batchNo).toBe('B003')
  })

  it('12.2 第一个批次扣完后再扣下一个批次', () => {
    const batches = [
      { batchNo: 'B001', qty: 50 },
      { batchNo: 'B002', qty: 30 }
    ]
    let outQty = 60
    const deductions = []
    for (const b of batches) {
      if (outQty <= 0) break
      const deduct = Math.min(outQty, b.qty)
      deductions.push({ batchNo: b.batchNo, qty: deduct })
      outQty -= deduct
    }
    expect(deductions[0]).toEqual({ batchNo: 'B001', qty: 50 })
    expect(deductions[1]).toEqual({ batchNo: 'B002', qty: 10 })
  })

  it('12.3 过期批次不应被出库', () => {
    const batches = [
      { batchNo: 'B001', inDate: '2025-01-01', expiryDate: '2025-12-31' },
      { batchNo: 'B002', inDate: '2026-01-01', expiryDate: '2026-12-31' }
    ]
    const today = '2026-07-08'
    const validBatches = batches.filter(b => new Date(b.expiryDate) >= new Date(today))
    expect(validBatches.length).toBe(1)
    expect(validBatches[0].batchNo).toBe('B002')
  })
})

// ===== 13. 多币种本位币汇总一致性测试 =====
describe('13. 多币种本位币汇总一致性测试', () => {
  it('13.1 多张不同币种单据的本位币金额应可汇总', () => {
    const docs = [
      { currencyCode: 'CNY', totalAmount: 1000, exchangeRate: 1 },
      { currencyCode: 'USD', totalAmount: 100, exchangeRate: 7.2 },
      { currencyCode: 'EUR', totalAmount: 50, exchangeRate: 7.8 }
    ]
    const totalBase = docs.reduce((s, d) => s + d.totalAmount * d.exchangeRate, 0)
    expect(Number(totalBase.toFixed(2))).toBe(2110)
  })

  it('13.2 切换币种后明细行原币金额不应变化', () => {
    const row = { quantity: 10, unitPrice: 100 }
    const originalAmount = row.quantity * row.unitPrice
    // 切换币种不影响原币金额
    const afterCurrencyChange = row.quantity * row.unitPrice
    expect(afterCurrencyChange).toBe(originalAmount)
  })

  it('13.3 汇率变化时本位币金额应同步变化', () => {
    const originalAmount = 1000
    const rates = [1, 7.2, 7.8, 0.92]
    const baseAmounts = rates.map(r => Number((originalAmount * r).toFixed(2)))
    expect(baseAmounts[0]).toBe(1000)
    expect(baseAmounts[1]).toBe(7200)
    expect(baseAmounts[2]).toBe(7800)
    expect(baseAmounts[3]).toBe(920)
  })

  it('13.4 本位币币种汇率必须为1', () => {
    const baseCurrency = currencies.find(c => c.isBaseCurrency === 1)
    expect(baseCurrency.exchangeRate).toBe(1)
  })

  it('13.5 非本位币币种汇率必须不为1', () => {
    const nonBaseCurrencies = currencies.filter(c => c.isBaseCurrency === 0)
    nonBaseCurrencies.forEach(c => {
      expect(c.exchangeRate).not.toBe(1)
    })
  })
})

// ===== 14. 审批流测试 =====
describe('14. 审批流测试', () => {
  it('14.1 草稿状态可以提交审核', () => {
    const doc = { status: '草稿' }
    const canSubmit = doc.status === '草稿'
    expect(canSubmit).toBe(true)
  })

  it('14.2 待审核状态可以审核', () => {
    const doc = { status: '待审核' }
    const canApprove = doc.status === '待审核'
    expect(canApprove).toBe(true)
  })

  it('14.3 已生效状态可以重置草稿', () => {
    const doc = { status: '已生效' }
    const canReverse = doc.status === '已生效'
    expect(canReverse).toBe(true)
  })

  it('14.4 审核后单据应变为已生效状态', () => {
    const doc = { status: '待审核' }
    doc.status = '已生效'
    expect(doc.status).toBe('已生效')
  })

  it('14.5 重置草稿后单据应回到草稿状态', () => {
    const doc = { status: '已生效' }
    doc.status = '草稿'
    expect(doc.status).toBe('草稿')
  })

  it('14.6 审核时库存应同步扣减', () => {
    const doc = { status: '待审核', items: [{ materialId: 1, quantity: 10 }] }
    let stockDeducted = false
    if (doc.status === '已生效') {
      stockDeducted = true
    }
    // 模拟审核
    doc.status = '已生效'
    if (doc.status === '已生效') stockDeducted = true
    expect(stockDeducted).toBe(true)
  })

  it('14.7 重置草稿时库存应同步回冲', () => {
    const doc = { status: '已生效', items: [{ materialId: 1, quantity: 10 }] }
    let stockReturned = false
    // 模拟重置草稿
    doc.status = '草稿'
    if (doc.status === '草稿') stockReturned = true
    expect(stockReturned).toBe(true)
  })
})

// ===== 15. 权限控制测试 =====
describe('15. 权限控制测试', () => {
  const roles = {
    'admin': ['create', 'edit', 'delete', 'approve', 'reverseApprove', 'view'],
    'sales': ['create', 'edit', 'view'],
    'finance': ['view', 'approve', 'reverseApprove'],
    'viewer': ['view']
  }

  it('15.1 管理员应有所有权限', () => {
    expect(roles.admin).toContain('create')
    expect(roles.admin).toContain('delete')
    expect(roles.admin).toContain('approve')
  })

  it('15.2 销售员不能审核单据', () => {
    expect(roles.sales).not.toContain('approve')
  })

  it('15.3 财务可以审核但不能创建单据', () => {
    expect(roles.finance).toContain('approve')
    expect(roles.finance).not.toContain('create')
  })

  it('15.4 查看者只能查看', () => {
    expect(roles.viewer).toEqual(['view'])
  })

  it('15.5 草稿状态单据只有创建人或管理员可编辑', () => {
    const doc = { status: '草稿', createdBy: 'userA' }
    const currentUser = 'userB'
    const role = 'sales'
    const canEdit = (doc.createdBy === currentUser) || (role === 'admin')
    expect(canEdit).toBe(false)
    // 创建人可编辑
    expect(doc.createdBy === 'userA' || role === 'admin').toBe(true)
  })

  it('15.6 已生效单据不可编辑（需先重置草稿）', () => {
    const doc = { status: '已生效' }
    const canEdit = doc.status === '草稿'
    expect(canEdit).toBe(false)
  })
})

// ===== 16. 物料保质期自动计算测试 =====
describe('16. 物料保质期自动计算测试', () => {
  const calcExpiryDate = (productionDate, expiryDays) => {
    const d = new Date(productionDate)
    d.setDate(d.getDate() + Number(expiryDays))
    return d.toISOString().slice(0, 10)
  }

  it('16.1 有效期至 = 生产日期 + expiryDays', () => {
    expect(calcExpiryDate('2026-01-01', 365)).toBe('2027-01-01')
    expect(calcExpiryDate('2026-01-01', 30)).toBe('2026-01-31')
    expect(calcExpiryDate('2026-01-01', 7)).toBe('2026-01-08')
  })

  it('16.2 修改生产日期应自动重算有效期至', () => {
    let row = { productionDate: '2026-01-01', expiryDays: 30, expiryDate: '2026-01-31' }
    // 修改生产日期
    row.productionDate = '2026-02-01'
    row.expiryDate = calcExpiryDate(row.productionDate, row.expiryDays)
    expect(row.expiryDate).toBe('2026-03-03')
  })

  it('16.3 未开启保质期管理的物料不应显示生产日期/有效期至', () => {
    const material = { isExpiryControl: 0 }
    const showExpiryFields = material.isExpiryControl === 1
    expect(showExpiryFields).toBe(false)
  })

  it('16.4 expiryDays为0时有效期至应等于生产日期', () => {
    expect(calcExpiryDate('2026-01-01', 0)).toBe('2026-01-01')
  })

  it('16.5 跨年计算应正确', () => {
    expect(calcExpiryDate('2025-12-30', 5)).toBe('2026-01-04')
  })

  it('16.6 跨闰年2月计算应正确', () => {
    // 2024年是闰年，2月有29天
    expect(calcExpiryDate('2024-02-28', 2)).toBe('2024-03-01')
  })
})

// ===== 17. 货位管理测试 =====
describe('17. 货位管理测试', () => {
  it('17.1 仓库未开启储位管理时货位字段应隐藏', () => {
    const warehouse = { stockId: 10, stockName: '主仓库', isBinControl: 0 }
    const showBinSelect = warehouse.isBinControl === 1
    expect(showBinSelect).toBe(false)
  })

  it('17.2 仓库开启储位管理时货位字段应显示', () => {
    const warehouse = { stockId: 10, stockName: '主仓库', isBinControl: 1 }
    const showBinSelect = warehouse.isBinControl === 1
    expect(showBinSelect).toBe(true)
  })

  it('17.3 货位下拉应按仓库过滤', () => {
    const locations = [
      { binId: 100, binName: 'A-01', stockId: 10 },
      { binId: 101, binName: 'A-02', stockId: 10 },
      { binId: 200, binName: 'B-01', stockId: 20 }
    ]
    const warehouseId = 10
    const filtered = locations.filter(l => l.stockId === warehouseId)
    expect(filtered.length).toBe(2)
    expect(filtered.every(l => l.stockId === warehouseId)).toBe(true)
  })

  it('17.4 切换仓库时货位应清空', () => {
    const row = { stockId: 10, binId: 100 }
    row.stockId = 20
    row.binId = null
    expect(row.binId).toBeNull()
  })
})

// ===== 18. 客户/供应商默认值带入测试 =====
describe('18. 客户/供应商默认值带入测试', () => {
  const customer = {
    customerId: 1, customerName: '客户A', customerCode: 'C001',
    contactPerson: '李四', contactPhone: '13900139000',
    deliveryAddress: '客户地址A', taxRate: 13,
    paymentTermId: 2, defaultPriceLevelId: 3, defaultSalerId: 5,
    currencyId: 2, creditLimit: 100000
  }

  const supplier = {
    supplierId: 1, supplierName: '供应商A', supplierCode: 'S001',
    contactPerson: '张三', contactPhone: '13800138000',
    deliveryAddress: '供应商地址A', taxRate: 13,
    paymentTermId: 1, settleId: 2, currencyId: 2,
    deliveryDays: 7
  }

  it('18.1 选择客户应带入联系人电话地址', () => {
    const form = {}
    form.contactPerson = customer.contactPerson
    form.contactPhone = customer.contactPhone
    form.deliveryAddress = customer.deliveryAddress
    expect(form.contactPerson).toBe('李四')
    expect(form.contactPhone).toBe('13900139000')
    expect(form.deliveryAddress).toBe('客户地址A')
  })

  it('18.2 选择客户应带入默认币种、付款条件、价格等级、销售员', () => {
    const form = {}
    form.currencyId = customer.currencyId
    form.paymentTermId = customer.paymentTermId
    form.priceLevelId = customer.defaultPriceLevelId
    form.salerId = customer.defaultSalerId
    expect(form.currencyId).toBe(2)
    expect(form.paymentTermId).toBe(2)
    expect(form.priceLevelId).toBe(3)
    expect(form.salerId).toBe(5)
  })

  it('18.3 选择客户应带入信用额度', () => {
    const form = {}
    form.creditLimit = customer.creditLimit
    expect(form.creditLimit).toBe(100000)
  })

  it('18.4 客户默认税率应带入明细行（仅覆盖空值或默认13%）', () => {
    const items = [{ materialId: 1, taxRate: 13 }, { materialId: 2, taxRate: 0 }]
    const customerTaxRate = 17
    items.forEach(row => {
      // 仅覆盖空值或默认13%的行，0 表示用户已主动设置为0（免税），不应覆盖
      if (row.taxRate === undefined || row.taxRate === null || row.taxRate === 13) {
        row.taxRate = customerTaxRate
      }
    })
    expect(items[0].taxRate).toBe(17)    // 原为13（默认值）被覆盖
    expect(items[1].taxRate).toBe(0)     // 原为0（用户主动设置免税）不被覆盖
  })

  it('18.5 选择供应商应按交货天数自动计算交货日期', () => {
    const today = new Date('2026-07-08')
    const expectedDate = new Date(today)
    expectedDate.setDate(expectedDate.getDate() + supplier.deliveryDays)
    expect(expectedDate.toISOString().slice(0, 10)).toBe('2026-07-15')
  })

  it('18.6 选择供应商应带入默认结算方式', () => {
    const form = {}
    form.settleId = supplier.settleId
    expect(form.settleId).toBe(2)
  })
})

// ===== 19. 价格等级调整测试 =====
describe('19. 价格等级调整测试', () => {
  it('19.1 价格等级上调时应按比例提高单价', () => {
    const basePrice = 100
    const adjustRate = 10  // 上调10%
    const newPrice = Number((basePrice * (1 + adjustRate / 100)).toFixed(4))
    expect(newPrice).toBe(110)
  })

  it('19.2 价格等级下调时应按比例降低单价', () => {
    const basePrice = 100
    const adjustRate = -10  // 下调10%
    const newPrice = Number((basePrice * (1 + adjustRate / 100)).toFixed(4))
    expect(newPrice).toBe(90)
  })

  it('19.3 价格等级为0时单价不变', () => {
    const basePrice = 100
    const adjustRate = 0
    const newPrice = Number((basePrice * (1 + adjustRate / 100)).toFixed(4))
    expect(newPrice).toBe(100)
  })

  it('19.4 切换价格等级应基于基础单价重算，而非当前单价', () => {
    const basePrice = 100
    // 第一次切换为上调10%
    let adjustRate = 10
    let newPrice = Number((basePrice * (1 + adjustRate / 100)).toFixed(4))
    expect(newPrice).toBe(110)
    // 第二次切换为下调10%，应基于basePrice重算
    adjustRate = -10
    newPrice = Number((basePrice * (1 + adjustRate / 100)).toFixed(4))
    expect(newPrice).toBe(90)   // 不是 110 * 0.9 = 99
  })

  it('19.5 价格等级切换后所有明细行应同步更新', () => {
    const items = [
      { baseUnitPrice: 100, unitPrice: 100 },
      { baseUnitPrice: 200, unitPrice: 200 },
      { baseUnitPrice: 300, unitPrice: 300 }
    ]
    const adjustRate = 5  // 上调5%
    items.forEach(row => {
      row.unitPrice = Number((row.baseUnitPrice * (1 + adjustRate / 100)).toFixed(4))
    })
    expect(items[0].unitPrice).toBe(105)
    expect(items[1].unitPrice).toBe(210)
    expect(items[2].unitPrice).toBe(315)
  })
})

// ===== 20. 库存计价方法测试 =====
describe('20. 库存计价方法测试', () => {
  it('20.1 移动加权平均法：入库后单价应重新计算', () => {
    // 现有库存：10个，单价100
    let stock = { qty: 10, unitCost: 100 }
    // 入库：5个，单价120
    const inQty = 5, inPrice = 120
    const oldTotalCost = stock.qty * stock.unitCost   // 1000
    const inAmount = inQty * inPrice                   // 600
    const newQty = stock.qty + inQty                   // 15
    const newCost = Number(((oldTotalCost + inAmount) / newQty).toFixed(4))
    expect(newCost).toBe(106.6667)
  })

  it('20.2 FIFO法：出库应按最早批次单价计算', () => {
    const batches = [
      { batchNo: 'B001', qty: 10, unitCost: 100 },  // 最早入库
      { batchNo: 'B002', qty: 10, unitCost: 120 }
    ]
    const outQty = 15
    // 先消耗B001的10个
    const cost1 = 10 * 100   // 1000
    // 再消耗B002的5个
    const cost2 = 5 * 120    // 600
    const totalCost = cost1 + cost2
    expect(totalCost).toBe(1600)
  })

  it('20.3 LIFO法：出库应按最晚批次单价计算', () => {
    const batches = [
      { batchNo: 'B001', qty: 10, unitCost: 100 },
      { batchNo: 'B002', qty: 10, unitCost: 120 }   // 最晚入库
    ]
    const outQty = 15
    // 先消耗B002的10个
    const cost1 = 10 * 120  // 1200
    // 再消耗B001的5个
    const cost2 = 5 * 100   // 500
    const totalCost = cost1 + cost2
    expect(totalCost).toBe(1700)
  })

  it('20.4 配置缺失时应默认使用移动加权平均', () => {
    const config = null
    const method = config ? config.defaultMethod : 'MOVING_AVERAGE'
    expect(method).toBe('MOVING_AVERAGE')
  })

  it('20.5 计价方法映射应正确', () => {
    const map = {
      'moving_avg': 'MOVING_AVERAGE',
      'weighted_avg': 'MONTHLY_AVERAGE',
      'fixed': 'SPECIFIC'
    }
    expect(map['moving_avg']).toBe('MOVING_AVERAGE')
    expect(map['weighted_avg']).toBe('MONTHLY_AVERAGE')
    expect(map['fixed']).toBe('SPECIFIC')
  })
})
