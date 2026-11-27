import request from '@/utils/request'

/**
 * 数据看板（财务/销售/仓储三大板块）
 * period: month=本月(默认) | quarter=本季 | year=本年
 */
export function getFinanceBoard(period = 'month') {
  return request({
    url: '/data-board/finance',
    method: 'get',
    params: { period }
  })
}

export function getSaleBoard(period = 'month') {
  return request({
    url: '/data-board/sale',
    method: 'get',
    params: { period }
  })
}

export function getInventoryBoard(period = 'month') {
  return request({
    url: '/data-board/inventory',
    method: 'get',
    params: { period }
  })
}
