// ====================================================================
// 通用API请求封装 - 所有模块共用
// 统一baseURL、Token注入、错误处理、Result<T>解包
// ====================================================================
import axios from 'axios'
import { ElMessage, ElNotification } from 'element-plus'
import { tt } from '@/i18n'
import { useTagsViewStore } from '@/stores/tagsView'
import { isAddPagePath } from '@/utils/addPage'

/**
 * 写请求成功 → 新增页内容已落库，清除其"未保存内容"标记
 * （标记由 MainLayout 监听用户输入时写入，供路由守卫在"二次点新增"时决定是否弹确认框）
 */
function clearAddPageDirty(res) {
  try {
    const method = (res.config && res.config.method ? res.config.method : '').toLowerCase()
    if (method !== 'post' && method !== 'put' && method !== 'patch' && method !== 'delete') return
    const path = window.location.pathname + window.location.search
    if (!isAddPagePath(path)) return
    useTagsViewStore().clearDirty(path)
  } catch (_) { /* 非关键路径：清标记失败不影响请求结果 */ }
}

/**
 * 后端「预警提示」统一展示：设置中心把超出来源单数量的预警放在响应头 X-Erp-Warning，
 * 前端在这里转为可见提示（不阻断已成功的保存/提交结果）。
 */
function showResponseWarnings(res) {
  try {
    // 页面已在提交前弹过确认框时，业务方传 showWarnings:false，避免再弹一次重复通知
    if (res && res.config && res.config.showWarnings === false) return
    const raw = res && res.headers ? res.headers['x-erp-warning'] : ''
    if (!raw) return
    const message = decodeURIComponent(String(raw))
    if (!message) return
    ElNotification({
      title: tt('预警提示'),
      message,
      type: 'warning',
      duration: 8000,
      showClose: true
    })
  } catch (_) { /* 预警展示失败不影响请求结果 */ }
}

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截：自动加Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 清理后端错误消息，去掉技术细节（PreparedStatementCallback / Java异常 / SQL源码 等）
function cleanErrorMessage(msg) {
  if (!msg) return tt('操作失败')
  let cleaned = String(msg)
  // 去掉 Spring 模板前缀，如 "PreparedStatementCallback; bad SQL grammar [SELECT ...]"
  cleaned = cleaned.replace(/^[\w\.]*Callback;\s*/i, '')
  cleaned = cleaned.replace(/^bad\s+SQL\s+grammar\s*\[\s*/i, tt('SQL语法错误：'))
  cleaned = cleaned.replace(/^\s*SQL\s*Syntax\s*Error\s*\(?\s*/i, tt('SQL语法错误：'))
  cleaned = cleaned.replace(/^Data\s*Integrity\s*Violation\s*[:;]?\s*/i, tt('数据完整性错误：'))
  // 去掉 ] 或 ) 结尾闭合
  cleaned = cleaned.replace(/\][^\]]*$/, '').replace(/\)[^)]*$/, '')
  // 去掉Java异常类名前缀（如 "java.sql.SQLException: ..."）
  cleaned = cleaned.replace(/^java\.\w+\.\w+Exception:\s*/i, '')
  cleaned = cleaned.replace(/^org\.(springframework|hibernate|mybatis)[\w\.]*Exception:\s*/i, '')
  // 去掉 "Could not commit JPA transaction" 等框架消息
  cleaned = cleaned.replace(/^Could not \w+.*?:\s*/i, '')
  // 如果消息中包含堆栈行（at xxx.xxx），截取堆栈之前的部分
  const stackIdx = cleaned.search(/\n\s*at\s/)
  if (stackIdx > 0) cleaned = cleaned.substring(0, stackIdx).trim()
  // 去掉 "### Cause: xxx" 前缀
  cleaned = cleaned.replace(/^###\s*Cause\s*[:：]\s*/i, '')
  // 如果是 SELECT/INSERT/UPDATE 开头的SQL，用更友好的话提示
  if (/^(SELECT|INSERT|UPDATE|DELETE)\b/i.test(cleaned.trim())) {
    cleaned = tt('数据操作失败，请联系管理员（SQL错误）')
  }
  // 限制消息长度
  if (cleaned.length > 120) cleaned = cleaned.substring(0, 120) + '...'
  return cleaned || tt('操作失败')
}

// 响应拦截：统一处理Result<T>和错误码
// 策略：GET请求不弹窗（空系统无数据是正常场景），POST/PUT/DELETE正常弹窗
request.interceptors.response.use(
  res => {
    const data = res.data
    // Blob响应：检查是否为后端返回的JSON错误
    if (data instanceof Blob && data.type.includes('application/json')) {
      return data.text().then(text => {
        try {
          const json = JSON.parse(text)
          const method = res.config?.method?.toLowerCase()
          if (method === 'get') {
            return Promise.reject(new Error(json.message || tt('操作失败')))
          }
          ElMessage.error(cleanErrorMessage(json.message))
          return Promise.reject(new Error(json.message || tt('操作失败')))
        } catch {
          return data
        }
      })
    }
    // 后端统一返回 { code, message, data }
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 200) {
        clearAddPageDirty(res)
        showResponseWarnings(res)
        // 2026-11-19 弹窗即时提醒：提交/审批类接口成功后立即广播铃铛刷新，
        // 弹窗 1~2 秒内出现（不再等 30 秒轮询），也杜绝"提交不弹、通过后才弹"的时序错乱
        const m = res.config?.method?.toLowerCase()
        if ((m === 'post' || m === 'put') && /\/(submit|approve|reject|reverse-approve|batch-approve|cancel)(\?|$)/.test(res.config?.url || '')) {
          try { window.dispatchEvent(new CustomEvent('erp:notification-refresh')) } catch (e) { /* 忽略 */ }
        }
        return data.data
      }
      const method = res.config?.method?.toLowerCase()
      // GET 请求：静默 reject 不弹窗（空系统下无数据是正常场景）
      if (method === 'get') {
        return Promise.reject(new Error(data.message || tt('操作失败')))
      }
      ElMessage.error(cleanErrorMessage(data.message))
      return Promise.reject(new Error(data.message || tt('操作失败')))
    }
    return data
  },
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('permissions')
      window.location.href = '/login'
    } else if (err.response?.status === 404) {
      const method = err.config?.method?.toLowerCase()
      // GET 请求 404 静默（空系统下无数据是正常场景）
      if (method === 'get') {
        return Promise.reject(err)
      }
      // 非GET请求 404 弹窗（保存/更新时资源不存在是异常）
      const msg = err.response?.data?.message || tt('请求的数据不存在')
      ElMessage.error(cleanErrorMessage(msg))
      return Promise.reject(err)
    } else if (err.response?.status === 403) {
      // 403 优先展示后端中文引导（如「无权限执行该操作：请在【设置中心→角色权限设置→按钮权限】…」），
      // 拿不到后端文案才退化为通用提示
      const m = err.response?.data?.message
      // 客户到期（2026-11-09）：清登录态并退回登录页，避免停留在旧界面继续点。
      // 页面通常并发多个请求，用一次性标记防止重复提示、重复跳转。
      if (m && String(m).indexOf('该客户已到期') >= 0) {
        if (!window.__erpTenantExpiredHandled) {
          window.__erpTenantExpiredHandled = true
          ElMessage.error(cleanErrorMessage(m))
          ;['token', 'username', 'permissions', 'menuIds', 'menuRoutePaths', 'userInfo',
            'tenantId', 'tenantName', 'tenantCode', 'pendingTenants'].forEach((k) => {
            try { localStorage.removeItem(k) } catch (_) {}
          })
          setTimeout(() => { window.location.href = '/login' }, 800)
        }
        return Promise.reject(err)
      }
      ElMessage.error(m ? cleanErrorMessage(m) : tt('没有权限执行此操作'))
    } else {
      const method = err.config?.method?.toLowerCase()
      // GET 请求不弹窗（空系统下初始化接口异常/无数据是正常场景，不打扰用户）
      if (method === 'get') {
        // 仅控制台输出便于开发调试，前端静默处理
        if (import.meta.env.DEV) {
          const url = err.config?.url || ''
          const status = err.response?.status || 'ERR'
          console.warn(`[GET ${status}] ${url} - ${err.message}`)
        }
        return Promise.reject(err)
      }
      // 非 GET（保存/提交/删除等写操作）：失败必须明确弹错，禁止静默（2026-10-14）
      if (!err.response) {
        // 无响应 = 请求未被服务器受理：跨域来源被拒（后端会回 403 "Invalid CORS request" 但浏览器因缺 CORS 头读不到）
        // 或网络中断。此前只弹英文 "Network Error"，用户以为已保存 → 保留旧界面 = 「关了还生效」。
        ElMessage.error(tt('保存失败：请求未被服务器受理（来源未授权或网络异常）。请刷新后重试，若反复失败请联系管理员'))
      } else {
        const rawMsg = err.response?.data?.message || err.response?.data?.error || err.message
        ElMessage.error(cleanErrorMessage(rawMsg))
      }
    }
    return Promise.reject(err)
  }
)

export default request
