// 发票报表模拟数据
export const mockSaleInvoiceOverview = {
  code: 200,
  message: 'success',
  data: {
    totalAmount: 2856000,
    totalTax: 456000,
    totalInvoices: 1250,
    avgAmount: 2284.8,
    monthData: [
      { month: '1月', amount: 180000, tax: 28000, count: 85 },
      { month: '2月', amount: 220000, tax: 35000, count: 95 },
      { month: '3月', amount: 280000, tax: 45000, count: 120 },
      { month: '4月', amount: 240000, tax: 38000, count: 105 },
      { month: '5月', amount: 320000, tax: 51000, count: 140 },
      { month: '6月', amount: 290000, tax: 46000, count: 125 },
      { month: '7月', amount: 350000, tax: 56000, count: 155 },
      { month: '8月', amount: 380000, tax: 61000, count: 165 },
      { month: '9月', amount: 330000, tax: 53000, count: 145 },
      { month: '10月', amount: 300000, tax: 48000, count: 130 },
      { month: '11月', amount: 270000, tax: 43000, count: 115 },
      { month: '12月', amount: 250000, tax: 40000, count: 110 }
    ]
  }
}

export const mockSaleInvoiceTrend = {
  code: 200,
  message: 'success',
  data: {
    weeklyTrend: [
      { week: '第1周', amount: 42000, tax: 6700, count: 18 },
      { week: '第2周', amount: 58000, tax: 9300, count: 25 },
      { week: '第3周', amount: 76000, tax: 12200, count: 33 },
      { week: '第4周', amount: 84000, tax: 13400, count: 38 },
      { week: '第5周', amount: 92000, tax: 14700, count: 42 },
      { week: '第6周', amount: 88000, tax: 14100, count: 40 },
      { week: '第7周', amount: 95000, tax: 15200, count: 45 },
      { week: '第8周', amount: 102000, tax: 16300, count: 48 }
    ],
    customerTrend: [
      { name: '客户A', amount: 450000, trend: 12.5 },
      { name: '客户B', amount: 380000, trend: 8.3 },
      { name: '客户C', amount: 320000, trend: -2.1 },
      { name: '客户D', amount: 280000, trend: 15.7 },
      { name: '客户E', amount: 220000, trend: 6.9 }
    ]
  }
}

export const mockSaleInvoiceCustomerAnalysis = {
  code: 200,
  message: 'success',
  data: {
    customerDistribution: [
      { name: '客户A', value: 450000, percentage: 15.8 },
      { name: '客户B', value: 380000, percentage: 13.3 },
      { name: '客户C', value: 320000, percentage: 11.2 },
      { name: '客户D', value: 280000, percentage: 9.8 },
      { name: '客户E', value: 220000, percentage: 7.7 },
      { name: '其他客户', value: 1206000, percentage: 42.2 }
    ],
    customerRanking: [
      { rank: 1, name: '客户A', amount: 450000, invoices: 45, avgAmount: 10000 },
      { rank: 2, name: '客户B', amount: 380000, invoices: 38, avgAmount: 10000 },
      { rank: 3, name: '客户C', amount: 320000, invoices: 32, avgAmount: 10000 },
      { rank: 4, name: '客户D', amount: 280000, invoices: 28, avgAmount: 10000 },
      { rank: 5, name: '客户E', amount: 220000, invoices: 22, avgAmount: 10000 }
    ]
  }
}

export const mockSaleInvoiceTaxAnalysis = {
  code: 200,
  message: 'success',
  data: {
    taxDistribution: [
      { rate: '13%', amount: 1200000, tax: 156000, count: 520 },
      { rate: '9%', amount: 800000, tax: 72000, count: 350 },
      { rate: '6%', amount: 500000, tax: 30000, count: 200 },
      { rate: '3%', amount: 356000, tax: 10680, count: 180 }
    ],
    monthlyTax: [
      { month: '1月', tax: 28000, rates: { '13%': 15000, '9%': 8000, '6%': 3000, '3%': 2000 } },
      { month: '2月', tax: 35000, rates: { '13%': 19000, '9%': 11000, '6%': 3500, '3%': 1500 } },
      { month: '3月', tax: 45000, rates: { '13%': 24000, '9%': 14000, '6%': 5000, '3%': 2000 } },
      { month: '4月', tax: 38000, rates: { '13%': 21000, '9%': 12000, '6%': 3500, '3%': 1500 } },
      { month: '5月', tax: 51000, rates: { '13%': 28000, '9%': 16000, '6%': 5000, '3%': 2000 } },
      { month: '6月', tax: 46000, rates: { '13%': 25000, '9%': 14000, '6%': 5000, '3%': 2000 } },
      { month: '7月', tax: 56000, rates: { '13%': 30000, '9%': 18000, '6%': 6000, '3%': 2000 } },
      { month: '8月', tax: 61000, rates: { '13%': 33000, '9%': 20000, '6%': 6000, '3%': 2000 } },
      { month: '9月', tax: 53000, rates: { '13%': 29000, '9%': 17000, '6%': 5000, '3%': 2000 } },
      { month: '10月', tax: 48000, rates: { '13%': 26000, '9%': 15000, '6%': 5000, '3%': 2000 } },
      { month: '11月', tax: 43000, rates: { '13%': 23000, '9%': 13000, '6%': 5000, '3%': 2000 } },
      { month: '12月', tax: 40000, rates: { '13%': 22000, '9%': 12000, '6%': 4000, '3%': 2000 } }
    ]
  }
}

export const mockSaleInvoiceDetails = {
  code: 200,
  message: 'success',
  data: {
    list: [
      { id: 'INV001', invoiceNo: 'XS2024001', customerName: '客户A', amount: 15000, tax: 1950, total: 16950, date: '2024-01-15', status: '已开票', salesman: '张三' },
      { id: 'INV002', invoiceNo: 'XS2024002', customerName: '客户B', amount: 12000, tax: 1080, total: 13080, date: '2024-01-16', status: '已开票', salesman: '李四' },
      { id: 'INV003', invoiceNo: 'XS2024003', customerName: '客户C', amount: 18000, tax: 1620, total: 19620, date: '2024-01-17', status: '已开票', salesman: '王五' },
      { id: 'INV004', invoiceNo: 'XS2024004', customerName: '客户D', amount: 10000, tax: 900, total: 10900, date: '2024-01-18', status: '已开票', salesman: '赵六' },
      { id: 'INV005', invoiceNo: 'XS2024005', customerName: '客户E', amount: 20000, tax: 1800, total: 21800, date: '2024-01-19', status: '已开票', salesman: '钱七' }
    ],
    pagination: {
      total: 1250,
      page: 1,
      pageSize: 10,
      totalPages: 125
    }
  }
}

export const mockPurchaseSupplierInvoiceAnalysis = {
  code: 200,
  message: 'success',
  data: {
    supplierDistribution: [
      { name: '供应商A', value: 680000, percentage: 23.8 },
      { name: '供应商B', value: 520000, percentage: 18.2 },
      { name: '供应商C', value: 450000, percentage: 15.8 },
      { name: '供应商D', value: 380000, percentage: 13.3 },
      { name: '供应商E', value: 280000, percentage: 9.8 },
      { name: '其他供应商', value: 546000, percentage: 19.1 }
    ],
    supplierRanking: [
      { rank: 1, name: '供应商A', amount: 680000, invoices: 68, avgAmount: 10000 },
      { rank: 2, name: '供应商B', amount: 520000, invoices: 52, avgAmount: 10000 },
      { rank: 3, name: '供应商C', amount: 450000, invoices: 45, avgAmount: 10000 },
      { rank: 4, name: '供应商D', amount: 380000, invoices: 38, avgAmount: 10000 },
      { rank: 5, name: '供应商E', amount: 280000, invoices: 28, avgAmount: 10000 }
    ]
  }
}

export const mockPurchaseInvoicePaymentStatus = {
  code: 200,
  message: 'success',
  data: {
    kpiCards: [
      { title: '应付总额', value: '2,856,000', change: '+12.5%', trend: 'up' },
      { title: '已付款', value: '2,200,000', change: '+15.3%', trend: 'up' },
      { title: '未付款', value: '656,000', change: '-8.2%', trend: 'down' },
      { title: '逾期金额', value: '120,000', change: '+5.7%', trend: 'up' }
    ],
    paymentStatus: [
      { status: '已付款', count: 880, amount: 2200000, percentage: 77.0 },
      { status: '部分付款', count: 156, amount: 420000, percentage: 14.7 },
      { status: '未付款', count: 214, amount: 236000, percentage: 8.3 }
    ],
    overdueAnalysis: [
      { days: '0-30天', count: 45, amount: 180000, percentage: 27.3 },
      { days: '31-60天', count: 78, amount: 280000, percentage: 42.7 },
      { days: '61-90天', count: 56, amount: 120000, percentage: 18.3 },
      { days: '90天以上', count: 35, amount: 76000, percentage: 11.7 }
    ]
  }
}

export const mockPurchaseInvoiceTaxAnalysis = {
  code: 200,
  message: 'success',
  data: {
    taxDistribution: [
      { rate: '13%', amount: 1200000, tax: 156000, count: 480 },
      { rate: '9%', amount: 800000, tax: 72000, count: 320 },
      { rate: '6%', amount: 500000, tax: 30000, count: 200 },
      { rate: '3%', amount: 356000, tax: 10680, count: 160 }
    ],
    monthlyTax: [
      { month: '1月', tax: 28000, rates: { '13%': 15000, '9%': 8000, '6%': 3000, '3%': 2000 } },
      { month: '2月', tax: 35000, rates: { '13%': 19000, '9%': 11000, '6%': 3500, '3%': 1500 } },
      { month: '3月', tax: 45000, rates: { '13%': 24000, '9%': 14000, '6%': 5000, '3%': 2000 } },
      { month: '4月', tax: 38000, rates: { '13%': 21000, '9%': 12000, '6%': 3500, '3%': 1500 } },
      { month: '5月', tax: 51000, rates: { '13%': 28000, '9%': 16000, '6%': 5000, '3%': 2000 } },
      { month: '6月', tax: 46000, rates: { '13%': 25000, '9%': 14000, '6%': 5000, '3%': 2000 } },
      { month: '7月', tax: 56000, rates: { '13%': 30000, '9%': 18000, '6%': 6000, '3%': 2000 } },
      { month: '8月', tax: 61000, rates: { '13%': 33000, '9%': 20000, '6%': 6000, '3%': 2000 } },
      { month: '9月', tax: 53000, rates: { '13%': 29000, '9%': 17000, '6%': 5000, '3%': 2000 } },
      { month: '10月', tax: 48000, rates: { '13%': 26000, '9%': 15000, '6%': 5000, '3%': 2000 } },
      { month: '11月', tax: 43000, rates: { '13%': 23000, '9%': 13000, '6%': 5000, '3%': 2000 } },
      { month: '12月', tax: 40000, rates: { '13%': 22000, '9%': 12000, '6%': 4000, '3%': 2000 } }
    ]
  }
}

export const mockPurchaseInvoiceDetails = {
  code: 200,
  message: 'success',
  data: {
    list: [
      { id: 'PINV001', invoiceNo: 'CG2024001', supplierName: '供应商A', amount: 15000, tax: 1950, total: 16950, date: '2024-01-15', status: '已付款', purchaser: '张三' },
      { id: 'PINV002', invoiceNo: 'CG2024002', supplierName: '供应商B', amount: 12000, tax: 1080, total: 13080, date: '2024-01-16', status: '部分付款', purchaser: '李四' },
      { id: 'PINV003', invoiceNo: 'CG2024003', supplierName: '供应商C', amount: 18000, tax: 1620, total: 19620, date: '2024-01-17', status: '未付款', purchaser: '王五' },
      { id: 'PINV004', invoiceNo: 'CG2024004', supplierName: '供应商D', amount: 10000, tax: 900, total: 10900, date: '2024-01-18', status: '已付款', purchaser: '赵六' },
      { id: 'PINV005', invoiceNo: 'CG2024005', supplierName: '供应商E', amount: 20000, tax: 1800, total: 21800, date: '2024-01-19', status: '已付款', purchaser: '钱七' }
    ],
    pagination: {
      total: 1056,
      page: 1,
      pageSize: 10,
      totalPages: 106
    }
  }
}