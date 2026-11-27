// 员工 API
import request from '@/utils/request'

const BASE = '/basedata/employee'

export function listEmployee(params) { return request.get(`${BASE}/page`, { params }) }
export function listAllEmployee(params) { return request.get(`${BASE}/list`, { params }) }
// 员工下拉（通用搜索栏「员工」维度：销售员/采购员等）：支持关键词模糊搜索，默认只返回在职
export function employeeSelectOptions(keyword) {
  return request.get(`${BASE}/select-options`, { params: { keyword: keyword || undefined, activeOnly: true } })
}
export function getEmployee(id) { return request.get(`${BASE}/${id}`) }
export function saveEmployee(data) { return request.post(BASE, data) }
export function updateEmployee(data) { return request.put(BASE, data) }
export function deleteEmployee(id) { return request.delete(`${BASE}/${id}`) }
export function batchDeleteEmployee(ids) { return request.post(`${BASE}/batch-delete`, ids) }
// 重置员工关联用户的密码：重置为默认密码 123456789；若指定 newPassword 则使用自定义密码
export function resetEmployeePassword(id, newPassword) {
  return request.post(`${BASE}/${id}/reset-password`, newPassword ? { newPassword } : {})
}
// 修改员工关联用户的密码（自定义新密码）— 需校验旧密码
export function changeEmployeePassword(id, oldPassword, newPassword) {
  return request.post(`${BASE}/${id}/change-password`, { oldPassword, newPassword })
}
// 校验用户名是否可用（排除自身 employeeId 的情况用于编辑查重）
export function checkEmployeeUsername(username, excludeEmployeeId) {
  return request.get(`${BASE}/check-username`, {
    params: {
      username,
      excludeEmployeeId: excludeEmployeeId || null
    }
  })
}
