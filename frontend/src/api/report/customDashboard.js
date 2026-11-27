import request from '@/utils/request'

/**
 * 自定义仪表盘 API
 * 后端：CustomDashboardController @RequestMapping("/api/report/custom/dashboard")
 * 统一返回结构由 axios 拦截器解包（直接拿到 data 字段）
 */

const BASE = '/report/custom/dashboard'

// 仪表盘列表（我的 + 共享给我的）
export function listDashboards(params) {
  return request.get(`${BASE}/list`, { params })
}

// 仪表盘详情（含卡片解析：报表名/模式/图表类型/是否可访问）
export function getDashboard(id) {
  return request.get(`${BASE}/${id}`)
}

// 查看数据（逐卡片执行报表，异常卡片单独返回 error）
export function getDashboardData(id) {
  return request.get(`${BASE}/${id}/data`)
}

// 新建仪表盘（dashCode 后端自动生成）
export function createDashboard(data) {
  return request.post(BASE, data)
}

// 更新仪表盘（仅创建人或管理员）
export function updateDashboard(id, data) {
  return request.put(`${BASE}/${id}`, data)
}

// 删除仪表盘（仅创建人或管理员）
export function deleteDashboard(id) {
  return request.delete(`${BASE}/${id}`)
}
