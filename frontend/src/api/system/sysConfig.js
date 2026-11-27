import request from '@/utils/request'
import axios from 'axios'

const BASE = '/sys-config'

// 静默版 - 不带 Authorization header 也不拦截 401，用于公开接口/登录页初始化
function silentPublicGet(url) {
  return axios.get(url, { timeout: 8000 })
    .then(r => {
      const d = r.data
      if (d && d.code === 200) return d.data
      return null
    })
    .catch(() => null)
}

// 静默版 - 带当前登录 token（设置页面加载默认值用）
function silentGet(url) {
  return axios.get(url, {
    headers: { Authorization: 'Bearer ' + (localStorage.getItem('token') || '') },
    timeout: 8000
  }).then(r => {
    const d = r.data
    if (d && d.code === 200) return d.data
    return null
  }).catch(() => null)
}

export function getSysConfigList() {
  return request.get(BASE)
}

export function getSysConfigByGroup(group) {
  return request.get(`${BASE}/group/${group}`)
}

export function getSysConfigByKey(key) {
  // 注意：BASE 自带前导斜杠(/sys-config)，这里不能写成 `/api/${BASE}/...`——
  // 会拼出 /api//sys-config/... 双斜杠，Spring 匹配不到路由返回 No static resource(HTTP 200+code 500)，
  // silentGet 静默吞成 null，员工权限补充页勾选将永远无法回显(2026-09-02 实测踩坑)
  return silentGet(`/api${BASE}/${key}`)
}

/**
 * 公开只读基础配置（匿名可访问，登录页/全局初始化用）
 * 返回 { 'system.name':'...', 'system.logo':'...', 'system.subtitle':'...',
 *         'system.copyright':'...', 'system.customer_service_phone':'...' }
 */
export function getPublicBasicConfig() {
  return silentPublicGet('/api/sys-config/public/basic')
}

export function updateSysConfig(key, value) {
  return request.put(`${BASE}/${key}`, { configValue: value })
}

/**
 * 批量更新配置
 * @param {Array} list [{ configKey, configValue }, ...]
 */
export function batchUpdateSysConfig(list) {
  return request.put(`${BASE}/batch`, list)
}

export function refreshSysConfig() {
  return request.post(`${BASE}/refresh`)
}

/**
 * 全局开关「启用浮动单位」是否开启（2026-11-10 多单位换算）
 * 设置中心 → 系统参数维护该键；未配置/读取异常后端一律返回 false（单据明细浮动两列整列隐藏 = 旧行为）
 */
export function getFloatUnitEnabled() {
  return request.get(`${BASE}/float-unit/enabled`)
}

/**
 * 保存系统配置（内部走 batchUpdateSysConfig）
 * @param {Array} items [{ configKey, configValue }, ...]
 */
export function saveSysConfig(items) {
  return batchUpdateSysConfig(items)
}

/**
 * 上传 logo 图片
 * @param {File} file 图片文件对象
 * @returns Promise<string> 访问路径（如 /uploads/logo/20260816/abc.png）
 */
export function uploadLogo(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/files/upload', fd, {
    params: { type: 'logo' },
    headers: { 'Content-Type': 'multipart/form-data' }
  }).then(r => r?.data || r)
}
