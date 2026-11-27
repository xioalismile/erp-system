// 获客采集模块 API
import request from '@/utils/request'

const TASK_BASE = '/acquisition/task'
const RESULT_BASE = '/acquisition/result'

// ===== 任务管理 =====
export function listTask(params) {
  return request.get(`${TASK_BASE}/page`, { params })
}

export function getTask(id) {
  return request.get(`${TASK_BASE}/${id}`)
}

export function saveTask(data) {
  return request.post(TASK_BASE, data)
}

export function updateTask(id, data) {
  return request.put(`${TASK_BASE}/${id}`, data)
}

export function deleteTask(id) {
  return request.delete(`${TASK_BASE}/${id}`)
}

export function startTask(id) {
  return request.post(`${TASK_BASE}/${id}/start`)
}

export function pauseTask(id) {
  return request.post(`${TASK_BASE}/${id}/pause`)
}

export function saveLoginCookie(id, cookie) {
  return request.post(`${TASK_BASE}/${id}/login-cookie`, { cookie })
}

export function taskDashboard() {
  return request.get(`${TASK_BASE}/dashboard`)
}

// ===== 结果管理 =====
export function listResult(params) {
  return request.get(`${RESULT_BASE}/page`, { params })
}

export function getResult(id) {
  return request.get(`${RESULT_BASE}/${id}`)
}

export function batchSaveResult(data) {
  return request.post(`${RESULT_BASE}/batch`, data)
}

export function reviewResult(id, data) {
  return request.put(`${RESULT_BASE}/${id}/review`, data)
}

export function batchImportToLeads(data) {
  return request.post(`${RESULT_BASE}/batch-import`, data)
}

// 删除单个采集结果
export function deleteResult(id) {
  return request.delete(`${RESULT_BASE}/${id}`)
}

// 批量删除采集结果
export function batchDeleteResult(resultIds) {
  return request.post(`${RESULT_BASE}/batch-delete`, { resultIds })
}

// 按任务清空采集结果（重新采集前清空旧数据）
export function clearResultByTask(taskId) {
  return request.delete(`${RESULT_BASE}/task/${taskId}/clear`)
}

export function pendingCount() {
  return request.get(`${RESULT_BASE}/pending-count`)
}

// ===== 采集服务（acquisition-service，端口3005，经 nginx/vite 代理 /api/capture）=====
// 说明：采集服务返回的是原生 JSON（非后端 Result 包装），故单独用 fetch 封装；
// URL 统一在此声明，禁止在 .vue 内直接硬写（守则2.8-2）
const CAPTURE_BASE = '/api/capture'

async function captureRequest(path, options) {
  const res = await fetch(`${CAPTURE_BASE}${path}`, options)
  const text = await res.text()
  if (!text) {
    throw new Error(`采集服务无响应（HTTP ${res.status}），请确认采集服务已启动（端口3005）`)
  }
  let data
  try {
    data = JSON.parse(text)
  } catch (e) {
    throw new Error(`采集服务返回非JSON响应（HTTP ${res.status}），可能服务异常或被代理拦截`, { cause: e })
  }
  return { httpStatus: res.status, ...data }
}

/** 启动扫码登录（服务端浏览器加载登录页并截图二维码） */
export function startCaptureLogin(platform) {
  return captureRequest(`/login/login_${platform}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ platform })
  })
}

/** 查询登录任务状态（二维码路径/登录结果） */
export function getCaptureLoginStatus(platform) {
  return captureRequest(`/login/0/status?platform=${encodeURIComponent(platform)}`)
}

/**
 * 查询平台「已保存的登录态」（读采集服务磁盘上的Cookie文件）
 * 与 getCaptureLoginStatus 的区别：后者查的是内存里的登录任务，服务重启/任务清理后就查不到了；
 * 本接口查磁盘落盘的登录态，重新进入页面、刷新、服务重启后都能拿到真实状态。
 */
export function getCaptureLoginState(platform) {
  return captureRequest(`/login-state?platform=${encodeURIComponent(platform)}`)
}

/** 手动确认登录（备用：自动检测失败时抓取当前浏览器Cookie保存） */
export function confirmCaptureLogin(platform) {
  return captureRequest(`/login/login_${platform}/confirm`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ platform })
  })
}

/** 清除平台登录态（Cookie文件+浏览器持久化目录），用于强制重新扫码登录 */
export function resetCaptureLogin(platform) {
  return captureRequest(`/login/login_${platform}/reset`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ platform })
  })
}

/** 登录二维码/登录页截图地址（用于 <img :src>，非接口调用） */
export function captureQrcodeUrl(platform) {
  return `${CAPTURE_BASE}/qrcode/login_${platform}`
}

// ===== 远程操作浏览器（服务器上无人能进桌面时，在登录页直接操作浏览器）=====

/**
 * 取登录浏览器当前页面的实时画面 + 视口尺寸
 * viewport 用于把页面上的点击换算成浏览器坐标（否则点不准）
 */
export function getCaptureLive(platform) {
  return captureRequest(`/live/${encodeURIComponent(platform)}`)
}

/** 实时画面图片地址（用于 <img :src>，非接口调用） */
export function captureLiveShotUrl(platform) {
  return `${CAPTURE_BASE}/live-shot/${encodeURIComponent(platform)}`
}

/**
 * 实时投屏地址（SSE，交给 new EventSource 使用，非 fetch 调用）
 * 服务端推：{ type:'frame', data(base64 jpeg), w, h } 画面帧
 *          { type:'meta', pageTitle, pageUrl, viewport:{width,height} } 页面信息/视口尺寸
 *          { type:'error', message } 无会话或不可用（随后服务端主动断开）
 * 说明：无需安装依赖（SSE 是原生浏览器能力），也不占用后端接口封装层。
 */
export function captureScreencastUrl(platform) {
  return `${CAPTURE_BASE}/screencast/${encodeURIComponent(platform)}`
}

/**
 * 在登录浏览器里执行一步操作
 * @param {string} platform qcc/fnb/tyc（爱企查 aqc 已下线）
 * @param {Object} payload { action: 'mouse'|'click'|'type'|'key'|'scroll'|'reload'|'back'|'dismiss', x, y, text, key, deltaY, type, button }
 *   action='mouse' 时：{ type:'down'|'move'|'up', x, y, button }，用于实时投屏的原生鼠标操作（点击/拖滑块）
 *   坐标一律是浏览器视口坐标（CSS 像素），由实时投屏的 viewport 换算得到
 */
export function interactCapture(platform, payload) {
  return captureRequest(`/interact/${encodeURIComponent(platform)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload || {})
  })
}

/** 手动关闭服务器的登录浏览器（登录结束后浏览器会保活一段时间，这里可提前关闭） */
export function closeCaptureBrowser(platform) {
  return captureRequest(`/browser/${encodeURIComponent(platform)}/close`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ platform })
  })
}

// ===== 采集任务撞风控时的人工处理（把采集浏览器接到前端画面上自己过验证）=====

/**
 * 查该平台"当前可操作的浏览器"是谁
 * @returns {Promise<Object>} { success, hasSession, source: 'login'|'capture', captureId, taskId,
 *   status: 'running'|'blocked', message, pageUrl }
 *   说明：source='capture' 且 status='blocked' = 采集任务正停在验证/风控页面等待人工处理
 */
export function getCaptureSession(platform) {
  return captureRequest(`/session?platform=${encodeURIComponent(platform)}`)
}

/** 人工验证完成后，通知采集任务继续（只有 status='blocked' 时才需要） */
export function resumeCapture(captureId) {
  return captureRequest(`/capture/${encodeURIComponent(captureId)}/resume`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({})
  })
}
