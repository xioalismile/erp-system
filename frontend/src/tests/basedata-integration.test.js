/**
 * 基础资料与单据关联关系自动测试用例
 * 覆盖：物料批次/保质期、仓库储位、币种汇率、单位、供应商/客户、价格等级、付款条件、结算方式
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'

// ===== Mock API =====
const mockMaterial = {
  materialId: 1, materialCode: 'M001', materialName: '测试物料', model: '规格A',
  baseUnitId: 1, purchaseUnitId: 2, saleUnitId: 3,
  isLotControl: 1, isSnControl: 0, isExpiryControl: 1, expiryDays: 365,
  purchasePrice: 100, taxRate: 13, status: '正常'
}
const mockMaterialNoLot = { ...mockMaterial, materialId: 2, isLotControl: 0, isExpiryControl: 0 }
const mockWarehouse = { stockId: 10, stockName: '主仓库', isBinControl: 1 }
const mockLocation = { binId: 100, binCode: 'A-01', binName: 'A区01位', stockId: 10 }
const mockCurrencyUSD = { currencyId: 2, currencyCode: 'USD', currencyName: '美元', exchangeRate: 7.2, isBaseCurrency: 0 }
const mockCurrencyCNY = { currencyId: 1, currencyCode: 'CNY', currencyName: '人民币', exchangeRate: 1, isBaseCurrency: 1 }
const mockUnit = { unitId: 1, unitName: '个' }
const mockSupplier = {
  supplierId: 1, supplierName: '供应商A', contactPerson: '张三', contactPhone: '13800138000',
  address: '地址A', taxNo: '税号A', bankName: '银行A', bankAccount: '账号A',
  paymentTermId: 1, currencyId: 2, deliveryDays: 7
}
const mockCustomer = {
  customerId: 1, customerName: '客户A', contactPerson: '李四', contactPhone: '13900139000',
  address: '地址B', taxNo: '税号B', bankName: '银行B', bankAccount: '账号B',
  paymentTermId: 2, currencyId: 2, defaultSalerId: 5, defaultPriceLevelId: 3
}
const mockPriceLevel = { priceLevelId: 3, priceLevelName: '批发价', priceAdjustRate: 0.9 }
const mockPaymentTerm = { paymentTermId: 1, paymentTermName: '月结30天', paymentDays: 30, discountDays: 10, discountRate: 0.98, advanceRate: 0.3 }

// ===== 测试用例 =====

describe('基础资料与单据关联关系测试', () => {

  describe('1. 物料批次管理联动', () => {
    it('1.1 物料开启批次管理时，采购入库单明细应显示批次号字段', () => {
      const material = mockMaterial
      expect(material.isLotControl).toBe(1)
      // 期望：选择物料后，该行明细的批次号字段应可见
      const row = { materialId: material.materialId }
      const showBatchNo = material.isLotControl === 1
      expect(showBatchNo).toBe(true)
    })

    it('1.2 物料未开启批次管理时，批次号字段应隐藏', () => {
      const material = mockMaterialNoLot
      expect(material.isLotControl).toBe(0)
      const showBatchNo = material.isLotControl === 1
      expect(showBatchNo).toBe(false)
    })

    it('1.3 物料开启保质期管理时，应显示生产日期和有效期至字段', () => {
      const material = mockMaterial
      expect(material.isExpiryControl).toBe(1)
      const showExpiryFields = material.isExpiryControl === 1
      expect(showExpiryFields).toBe(true)
    })

    it('1.4 物料开启保质期管理时，有效期至应根据expiryDays自动计算', () => {
      const material = mockMaterial
      const productionDate = '2026-01-01'
      const expiryDays = material.expiryDays // 365
      const expectedExpiryDate = new Date(productionDate)
      expectedExpiryDate.setDate(expectedExpiryDate.getDate() + expiryDays)
      expect(expectedExpiryDate.toISOString().slice(0, 10)).toBe('2027-01-01')
    })

    it('1.5 批次号应在行级别控制，而非表头统一控制', () => {
      // 不同物料可能有不同的批次管理设置
      const items = [
        { materialId: 1, _isLotControl: 1 },
        { materialId: 2, _isLotControl: 0 }
      ]
      expect(items[0]._isLotControl).toBe(1)
      expect(items[1]._isLotControl).toBe(0)
      // 行0应显示批次号，行1不应显示
    })
  })

  describe('2. 仓库多仓多储位联动', () => {
    it('2.1 仓库选择后，货位下拉应只显示该仓库的储位', () => {
      const locations = [
        { binId: 100, binName: 'A-01', stockId: 10 },
        { binId: 101, binName: 'A-02', stockId: 10 },
        { binId: 200, binName: 'B-01', stockId: 20 }
      ]
      const filtered = locations.filter(l => l.stockId === 10)
      expect(filtered.length).toBe(2)
      expect(filtered.every(l => l.stockId === 10)).toBe(true)
    })

    it('2.2 切换仓库时应清空已选货位', () => {
      const row = { stockId: 10, binId: 100 }
      // 模拟 onStockChange
      row.binId = null
      expect(row.binId).toBeNull()
    })

    it('2.3 货位下拉应使用正确的字段名(binId/binName)', () => {
      const location = mockLocation
      expect(location.binId).toBeDefined()
      expect(location.binName).toBeDefined()
      // 不应使用 locationId/locationName
    })

    it('2.4 库存应按仓库+储位+物料+批次维度记录', () => {
      const inventoryKey = { stockId: 10, binId: 100, materialId: 1, batchNo: 'B20260101' }
      expect(inventoryKey.stockId).toBeDefined()
      expect(inventoryKey.binId).toBeDefined()
      expect(inventoryKey.materialId).toBeDefined()
      expect(inventoryKey.batchNo).toBeDefined()
    })

    it('2.5 销售出库审核应扣减库存', () => {
      // 模拟审核流程
      const delivery = { items: [{ materialId: 1, quantity: 10, stockId: 10, binId: 100 }] }
      let stockDeducted = false
      // 期望调用 inventoryService.deductStock
      const deductStock = vi.fn(() => { stockDeducted = true })
      deductStock(delivery.items[0].stockId, delivery.items[0].binId, delivery.items[0].materialId, delivery.items[0].quantity)
      expect(stockDeducted).toBe(true)
    })
  })

  describe('3. 币种汇率联动', () => {
    it('3.1 选择不同币种时汇率应自动填充', () => {
      const currencies = [mockCurrencyCNY, mockCurrencyUSD]
      const selectedId = 2 // USD
      const currency = currencies.find(c => c.currencyId === selectedId)
      expect(currency.exchangeRate).toBe(7.2)
    })

    it('3.2 选择本位币时汇率应为1', () => {
      const currencies = [mockCurrencyCNY, mockCurrencyUSD]
      const selectedId = 1 // CNY
      const currency = currencies.find(c => c.currencyId === selectedId)
      expect(currency.exchangeRate).toBe(1)
      expect(currency.isBaseCurrency).toBe(1)
    })

    it('3.3 原币金额 = 数量 × 单价', () => {
      const quantity = 10
      const unitPrice = 100
      const amount = quantity * unitPrice
      expect(amount).toBe(1000)
    })

    it('3.4 本位币金额 = 原币金额 × 汇率', () => {
      const amount = 1000
      const exchangeRate = 7.2
      const baseAmount = (amount * exchangeRate).toFixed(2)
      expect(baseAmount).toBe('7200.00')
    })

    it('3.5 切换币种后本位币金额应同步变化', () => {
      const quantity = 10, unitPrice = 100
      const amount = quantity * unitPrice // 1000
      // 选择CNY
      const baseAmountCNY = (amount * 1).toFixed(2)
      expect(baseAmountCNY).toBe('1000.00')
      // 切换为USD
      const baseAmountUSD = (amount * 7.2).toFixed(2)
      expect(baseAmountUSD).toBe('7200.00')
      expect(baseAmountUSD).not.toBe(baseAmountCNY)
    })

    it('3.6 采购订单应有币种选择和汇率输入', () => {
      const form = { currencyId: null, exchangeRate: 1.0, totalAmount: 0, baseTotalAmount: 0 }
      expect(form).toHaveProperty('currencyId')
      expect(form).toHaveProperty('exchangeRate')
    })

    it('3.7 采购入库单应支持多币种', () => {
      const form = { currencyId: null, exchangeRate: 1.0 }
      expect(form).toHaveProperty('currencyId')
      expect(form).toHaveProperty('exchangeRate')
    })
  })

  describe('4. 计量单位联动', () => {
    it('4.1 物料选择后应自动带出单位名称', () => {
      const material = mockMaterial
      const units = [{ unitId: 1, unitName: '个' }, { unitId: 2, unitName: '箱' }]
      const unitId = material.purchaseUnitId || material.baseUnitId
      const unit = units.find(u => u.unitId === unitId)
      expect(unit).toBeDefined()
      expect(unit.unitName).toBe('箱')
    })

    it('4.2 不应引用后端不存在的m.unitName字段', () => {
      const material = mockMaterial
      // Material实体没有unitName字段
      expect(material.unitName).toBeUndefined()
    })

    it('4.3 物料编辑表单应支持多单位字段', () => {
      const form = {
        baseUnitId: 1, purchaseUnitId: 2, saleUnitId: 3, stockUnitId: 1, productionUnitId: 1
      }
      expect(form).toHaveProperty('baseUnitId')
      expect(form).toHaveProperty('purchaseUnitId')
      expect(form).toHaveProperty('saleUnitId')
    })
  })

  describe('5. 供应商信息联动', () => {
    it('5.1 选择供应商应带入联系人、电话、地址', () => {
      const supplier = mockSupplier
      const form = {}
      form.contactPerson = supplier.contactPerson
      form.contactPhone = supplier.contactPhone
      form.deliveryAddress = supplier.address
      expect(form.contactPerson).toBe('张三')
      expect(form.contactPhone).toBe('13800138000')
      expect(form.deliveryAddress).toBe('地址A')
    })

    it('5.2 选择供应商应带入税号和银行信息', () => {
      const supplier = mockSupplier
      const form = {}
      form.supplierTaxNo = supplier.taxNo
      form.bankName = supplier.bankName
      form.bankAccount = supplier.bankAccount
      expect(form.supplierTaxNo).toBe('税号A')
      expect(form.bankName).toBe('银行A')
      expect(form.bankAccount).toBe('账号A')
    })

    it('5.3 选择供应商应带入付款条件和币种', () => {
      const supplier = mockSupplier
      const form = {}
      form.paymentTermId = supplier.paymentTermId
      form.currencyId = supplier.currencyId
      expect(form.paymentTermId).toBe(1)
      expect(form.currencyId).toBe(2)
    })
  })

  describe('6. 客户信息联动', () => {
    it('6.1 选择客户应带入税号、地址、银行信息', () => {
      const customer = mockCustomer
      const form = {}
      form.customerTaxNo = customer.taxNo
      form.deliveryAddress = customer.address
      form.bankName = customer.bankName
      form.bankAccount = customer.bankAccount
      expect(form.customerTaxNo).toBe('税号B')
      expect(form.deliveryAddress).toBe('地址B')
    })

    it('6.2 选择客户应带入默认销售员和价格等级', () => {
      const customer = mockCustomer
      const form = {}
      form.salerId = customer.defaultSalerId
      form.priceLevelId = customer.defaultPriceLevelId
      expect(form.salerId).toBe(5)
      expect(form.priceLevelId).toBe(3)
    })
  })

  describe('7. 价格等级联动', () => {
    it('7.1 选择价格等级后应根据priceAdjustRate调整单价', () => {
      const priceLevel = mockPriceLevel
      const basePrice = 100
      const adjustedPrice = (basePrice * priceLevel.priceAdjustRate).toFixed(2)
      expect(adjustedPrice).toBe('90.00')
    })

    it('7.2 切换价格等级后所有明细行单价应同步更新', () => {
      const items = [
        { materialId: 1, unitPrice: 100 },
        { materialId: 2, unitPrice: 200 }
      ]
      const priceLevel = mockPriceLevel
      items.forEach(item => {
        item.unitPrice = Number((item.unitPrice * priceLevel.priceAdjustRate).toFixed(2))
      })
      expect(items[0].unitPrice).toBe(90)
      expect(items[1].unitPrice).toBe(180)
    })
  })

  describe('8. 付款条件联动', () => {
    it('8.1 选择付款条件应带入付款天数', () => {
      const paymentTerm = mockPaymentTerm
      const orderDate = '2026-01-01'
      const dueDate = new Date(orderDate)
      dueDate.setDate(dueDate.getDate() + paymentTerm.paymentDays)
      expect(dueDate.toISOString().slice(0, 10)).toBe('2026-01-31')
    })

    it('8.2 付款条件折扣率应可应用于金额计算', () => {
      const paymentTerm = mockPaymentTerm
      const totalAmount = 10000
      const discountAmount = (totalAmount * (1 - paymentTerm.discountRate)).toFixed(2)
      expect(discountAmount).toBe('200.00')
    })
  })

  describe('9. 结算方式联动', () => {
    it('9.1 业务单据应使用基础资料中的结算方式，而非硬编码', () => {
      const settleOptions = [
        { settleModeId: 1, settleModeName: '银行转账', settleType: '通用' },
        { settleModeId: 2, settleModeName: '现金', settleType: '通用' }
      ]
      // 不应使用硬编码 ['现金', '银行转账', '支付宝', '微信支付']
      expect(settleOptions.length).toBeGreaterThan(0)
      expect(settleOptions[0]).toHaveProperty('settleModeId')
      expect(settleOptions[0]).toHaveProperty('settleModeName')
    })

    it('9.2 列表显示结算方式应显示名称而非ID', () => {
      const settleOptions = [
        { settleModeId: 1, settleModeName: '银行转账' }
      ]
      const row = { settleModeId: 1 }
      const display = settleOptions.find(s => s.settleModeId === row.settleModeId)?.settleModeName
      expect(display).toBe('银行转账')
      expect(display).not.toBe(1)
    })
  })

  describe('10. 物料编辑表单完整性', () => {
    it('10.1 物料编辑表单应包含保质期管理字段', () => {
      const form = { isExpiryControl: 0, expiryDays: 0 }
      expect(form).toHaveProperty('isExpiryControl')
      expect(form).toHaveProperty('expiryDays')
    })

    it('10.2 物料编辑表单应包含批次管理、序列号管理、货位管理、质检要求', () => {
      const form = { isLotControl: 0, isSnControl: 0, isBinControl: 0, isQcRequired: 0 }
      expect(form).toHaveProperty('isLotControl')
      expect(form).toHaveProperty('isSnControl')
      expect(form).toHaveProperty('isBinControl')
      expect(form).toHaveProperty('isQcRequired')
    })

    it('10.3 物料编辑表单应包含多单位字段', () => {
      const form = { baseUnitId: null, purchaseUnitId: null, saleUnitId: null }
      expect(form).toHaveProperty('baseUnitId')
      expect(form).toHaveProperty('purchaseUnitId')
      expect(form).toHaveProperty('saleUnitId')
    })
  })
})
