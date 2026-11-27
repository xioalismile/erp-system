import request from '@/utils/request'

// 后端控制器：SysRoleFieldPermissionController（/api/role-field-permission）
const BASE = '/role-field-permission'

export function getRoleFieldPermission(roleId, pageKey) {
  return request.get(`${BASE}/role/${roleId}/page/${pageKey}`)
}

export function saveRoleFieldPermission(roleId, pageKey, list) {
  return request.put(`${BASE}/role/${roleId}/page/${pageKey}`, list)
}

export function getCurrentUserFieldPermission(pageKey) {
  return request.get(`${BASE}/current/page/${pageKey}`)
}

export function copyFieldPermission(sourceRoleId, targetRoleId) {
  return request.post(`${BASE}/copy`, null, { params: { sourceRoleId, targetRoleId } })
}

export function getFieldMetaList(pageKey) {
  return request.get(`${BASE}/fields/${pageKey}`)
}

/** 可配置字段权限的页面列表（key/name/module，来自报表元数据注册表） */
export function getAvailablePages() {
  return request.get(`${BASE}/pages`)
}

/** 该角色已配置过字段权限的页面 key 集合（字段权限左栏据此只默认展开有配置的模块） */
export function getRoleConfiguredPages(roleId) {
  return request.get(`${BASE}/role/${roleId}/configured-pages`)
}
