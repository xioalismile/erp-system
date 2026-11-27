// 库存预警规则 API（设置中心 → 库存预警设置）
import request from '@/utils/request'

const BASE = '/inventory-warning'

/** 分页查询预警规则 */
export function listWarningRules(params) {
  return request.get(`${BASE}/rule`, { params })
}

/** 规则详情 */
export function getWarningRule(id) {
  return request.get(`${BASE}/rule/${id}`)
}

/** 批量新增：一次提交一批货品，每个货品生成一条规则 */
export function saveWarningRuleBatch(data) {
  return request.post(`${BASE}/rule/batch`, data)
}

/** 修改单条规则 */
export function updateWarningRule(data) {
  return request.put(`${BASE}/rule`, data)
}

/** 删除单条规则 */
export function deleteWarningRule(id) {
  return request.delete(`${BASE}/rule/${id}`)
}

/** 启用/停用切换 */
export function toggleWarningRule(id) {
  return request.put(`${BASE}/rule/${id}/toggle`)
}

/** 立即检测预警 */
export function checkWarningNow() {
  return request.post(`${BASE}/check`)
}

/** 下载导入模板（二进制文件，需带 token，绕过统一解包） */
export function downloadWarningRuleTemplate() {
  const token = localStorage.getItem('token')
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  // 注意：fetch 不会自动补 axios 的 baseURL('/api')，这里必须写全 /api 前缀，否则 404
  return fetch(`/api${BASE}/rule/template`, { headers }).then(async (resp) => {
    if (!resp.ok) throw new Error('模板下载失败')
    const blob = await resp.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.style.display = 'none'
    a.href = url
    a.download = '库存预警规则导入模板.xlsx'
    document.body.appendChild(a)
    a.click()
    setTimeout(() => {
      if (a.parentNode) document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
    }, 500)
  })
}

/** 导入预检查（只读，返回可新增/已存在冲突/校验失败） */
export function checkWarningRuleImport(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`${BASE}/rule/import/check`, fd)
}

/** 导入确认：onExistingConflict = SKIP 跳过已存在 / OVERWRITE 覆盖已存在 */
export function confirmWarningRuleImport(file, onExistingConflict = 'SKIP') {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('onExistingConflict', onExistingConflict)
  return request.post(`${BASE}/rule/import/confirm`, fd)
}
