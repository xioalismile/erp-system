import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'

const BASE_URL = '/api/export'

/**
 * 统一解析后端 JSON 响应，把三类异常都转成可读提示（避免抛浏览器原始报错）：
 * ① 空响应体 —— 后端鉴权失败曾只 setStatus 不写响应体，直接 response.json() 会抛
 *    "Failed to execute 'json' on 'Response': Unexpected end of JSON input"，用户无法理解也无从处置；
 * ② 非 JSON 内容（网关错误页等）；③ 业务码非 200。
 */
async function parseJsonResponse(response, fallbackMessage) {
  const label = fallbackMessage || tt('操作失败')
  let body = ''
  try {
    body = ((await response.text()) || '').trim()
  } catch (e) {
    body = ''
  }

  let json = null
  if (body) {
    try {
      json = JSON.parse(body)
    } catch (e) {
      json = null
    }
  }

  if (json) {
    if (json.code === 200 || json.code === 0) return json.data
    if (json.code === 401) throw buildAuthExpiredError()
    if (json.code === 403) throw new Error(json.message || tt('无权限执行该操作'))
    throw new Error(json.message || label)
  }

  // 空响应体 或 非 JSON 内容
  if (response.status === 401) throw buildAuthExpiredError()
  if (response.status === 403) throw new Error(tt('无权限执行该操作'))
  if (!response.ok) throw new Error(label + ' (' + tt('服务返回异常') + ', HTTP ' + response.status + ')')
  throw new Error(label + ' (' + tt('服务未返回内容，请稍后重试') + ')')
}

/** 仅取错误提示文本（下载类接口用：非 2xx 时总要抛错） */
async function extractErrorMessage(response, fallbackMessage) {
  try {
    await parseJsonResponse(response, fallbackMessage)
  } catch (e) {
    return (e && e.message) ? e.message : (fallbackMessage || tt('操作失败'))
  }
  return fallbackMessage || tt('操作失败')
}

/**
 * 登录已过期时的统一处理：与 utils/request.js 的 401 分支保持一致
 * （清理本地登录信息并跳回登录页），避免原生 fetch 调用只弹一句报错、页面却停在原地。
 * @returns {Error} 供调用方抛出，保证业务代码仍能拿到可读错误
 */
function buildAuthExpiredError() {
  const msg = tt('登录已过期，请重新登录')
  try {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('permissions')
    if (window.location.pathname !== '/login') {
      ElMessage.warning(msg)
      setTimeout(() => { window.location.href = '/login' }, 800)
    }
  } catch (e) {
    // 环境异常（如 localStorage 不可用）时忽略，仍抛出可读错误
  }
  return new Error(msg)
}

/**
 * 通用文件下载 - 使用 fetch API 直接下载，绕过 axios 拦截器
 * 确保文件不被 JSON 解包或 MIME type 丢失
 */
function downloadFile(url, method, fileName) {
  const token = localStorage.getItem('token')
  const headers = {}
  if (token) headers['Authorization'] = `Bearer ${token}`

  return fetch(url, { method, headers })
    .then(response => {
      if (!response.ok) {
        // 非 2xx：统一取后端可读提示（含空响应体 / 非 JSON 兜底）
        return extractErrorMessage(response, '下载失败').then(msg => { throw new Error(msg) })
      }
      return response.blob()
    })
    .then(blob => {
      // 确保Blob有正确的MIME type
      if (!blob.type || blob.type === '' || blob.type === 'application/octet-stream') {
        blob = new Blob([blob], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      }
      saveAs(blob, fileName)
    })
}

/**
 * 保存Blob为文件 - 兼容各浏览器
 */
function saveAs(blob, fileName) {
  // IE/Edge legacy
  if (window.navigator && window.navigator.msSaveOrOpenBlob) {
    window.navigator.msSaveOrOpenBlob(blob, fileName)
    return
  }

  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.style.display = 'none'
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()

  // 延迟清理，确保下载已启动
  setTimeout(() => {
    if (link.parentNode) document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  }, 500)
}

export function downloadTemplate(entityName, fields, billType) {
  const fileName = getTemplateFileName(entityName)
  let url = `${BASE_URL}/template/${entityName}`
  const query = []
  // 可选：按用户勾选的字段过滤模板列（如销售订单模板字段设置）
  if (fields && fields.length) query.push(`fields=${encodeURIComponent(fields.join(','))}`)
  // 可选：单据类型（生产领料/退料/补料共用一张表，退料/补料的模板列名与必填列不同）
  if (billType) query.push(`billType=${encodeURIComponent(billType)}`)
  if (query.length) url += `?${query.join('&')}`
  return downloadFile(url, 'GET', fileName)
    .catch(err => {
      ElMessage.error(tt('模板下载失败：') + (err.message || tt('未知错误')))
      throw err
    })
}

export function exportData(entityName, poolType) {
  const fileName = getExportFileName(entityName)
  let url = `${BASE_URL}/data/${entityName}`
  if (poolType !== undefined && poolType !== null && poolType !== '') {
    url += `?poolType=${poolType}`
  }
  return downloadFile(url, 'POST', fileName)
    .catch(err => {
      ElMessage.error(tt('导出失败：') + (err.message || tt('未知错误')))
      throw err
    })
}

/**
 * 带请求体的通用导出（2026-09-22 新增）。
 *
 * 用途：查询类页面（如即时库存）要求"页面上看到什么，导出就带什么"——把**当前筛选条件**与
 * **当前可见列（含自定义字段列）**一起发给后端，由后端按同一套条件取数、按同一组列写出。
 * 历史问题：即时库存导出走的是另一条"固定 12 列 + 全量"链路 → ①页面上的物料自定义字段列一个都不带；
 * ②页面过滤掉的行会被导出（出现空行）。
 *
 * 仍用原生 fetch 绕过 axios 拦截器，保证二进制文件不被 JSON 解包。
 *
 * @param {string} url      后端导出接口（如 /api/inventory/export）
 * @param {object} payload  请求体（筛选条件 + fields 列定义）
 * @param {string} fileName 下载文件名
 */
export function exportDataWithPayload(url, payload, fileName) {
  const token = localStorage.getItem('token')
  return fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload || {})
  })
    .then(response => {
      if (!response.ok) {
        return extractErrorMessage(response, tt('导出失败')).then(msg => { throw new Error(msg) })
      }
      return response.blob()
    })
    .then(blob => {
      if (!blob.type || blob.type === '' || blob.type === 'application/octet-stream') {
        blob = new Blob([blob], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      }
      saveAs(blob, fileName)
    })
    .catch(err => {
      ElMessage.error(tt('导出失败：') + (err.message || tt('未知错误')))
      throw err
    })
}

/**
 * ★ 全站列表页导出的**统一入口**：「页面上看到什么，导出就带什么」（2026-09-24 用户口径）。
 *
 * 与 `exportData(entity)` 的区别（后者是"后端固定列 + 全量行"，正是用户反馈的病根）：
 *  - **列**：按页面当前可见列（顺序 / 列名 / 含自定义字段列）逐列导出 —— 请求里没有的列一律不导出；
 *  - **行**：由传入的主键集合决定（= 页面当前筛选条件下的结果），后端再按主键套数据权限取数；
 *  - 表头文字直接用页面列名（i18n 后），与页面逐字一致。
 *
 * @param {string} entityName 实体名（如 saleOrder / customer）
 * @param {Array<{key:string,label:string,custom?:boolean}>} fields 当前可见列（按页面顺序）
 * @param {Array<number|string>} [ids] 行主键；不传/空 = 全量（兼容旧行为）
 * @param {string} [fileName] 下载文件名
 */
export function exportAsSeen(entityName, fields, rowsOrIds, fileName) {
  const name = fileName || `${getExportFileName(entityName)}_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.xlsx`
  const isRows = Array.isArray(rowsOrIds) && rowsOrIds.some(x => x && typeof x === 'object')
  const payload = { fields: fields || [] }
  if (isRows) payload.rows = rowsOrIds
  else payload.ids = rowsOrIds || []
  return exportDataWithPayload(`/api/export/data/${entityName}/visible`, payload, name)
}

/**
 * 页面列定义 → 导出列描述。
 * key 用列的 prop（= 实体属性名；自定义字段列的 prop 就是 fieldKey），label 用页面列名。
 * 自定义字段列在列定义里 type === 'custom'（见 composables/useCustomField.js 的 customFieldsToTableColumns）。
 */
export function columnsToExportFields(columns) {
  return (columns || [])
    .filter(c => c && c.prop)
    .map(c => ({
      // exportProp：页面列用于展示的 prop 与导出行数据字段不一致时用它（如工艺路线「物料」列展示多物料文本）
      key: c.exportProp || c.prop,
      label: c.label,
      // 自定义字段列的标记口径：单据/查询页用 type==='custom'，通用表格壳（CrudTable）用 customField===true
      custom: c.type === 'custom' || c.customField === true || c.isCustom === true
    }))
}

/**
 * 收集「当前筛选条件」下的**全量行数据**（按页面自己的列表接口分页拉取）。
 *
 * 为什么在前端拉、且直接传"行数据"而不是主键：
 *  ① 各实体筛选参数千差万别，只有页面自己的接口 + 自己的参数才能保证"导出的行 = 页面上筛选后的行"；
 *  ② 页面显示的列里常有"展示字段/别名列"（如销售订单的"客户/销售员"在实体上是非持久字段、
 *     委外订单的"委外单号"是前端别名、组装拆卸单是蛇形列）——拿主键回查后端这些列会是空白，
 *     直接把页面的行数据传给后端写盘，才真正做到"页面显示什么，导出就带什么"。
 *
 * @param {(params:object)=>Promise<any>} fetchPage 列表接口（返回 {records,total} 或数组）
 * @param {object} baseParams 页面当前筛选参数（不含 current/size）
 * @param {number} [pageSize=500]
 * @param {number} [maxRows=20000] 安全上限，超出即停（余下不导，避免浏览器/网络被打爆）
 */
export async function collectFilteredRows(fetchPage, baseParams, pageSize = 500, maxRows = 20000) {
  const rows = []
  let current = 1
  for (;;) {
    const res = await fetchPage({ ...(baseParams || {}), current, size: pageSize })
    const records = Array.isArray(res) ? res : (res?.records || res?.data?.records || [])
    const total = Array.isArray(res) ? records.length : (res?.total ?? res?.data?.total ?? records.length)
    rows.push(...records)
    if (!records.length || records.length < pageSize || rows.length >= total || rows.length >= maxRows) break
    current += 1
  }
  return rows
}

/**
 * 列表页导出的一步到位封装（**全站列表页统一用这个**）：
 * 列 = 页面当前可见列（含自定义字段列）；行 = 当前筛选条件下的全部页面行；
 * 统一走后端 `/api/export/data/{entityName}/visible`，按页面列顺序/列名逐列输出。
 *
 * @param {object} o
 * @param {string} o.entityName 实体名
 * @param {Array}  o.columns    页面当前可见列（含自定义字段列）
 * @param {Function} o.fetchPage 列表接口
 * @param {object} o.params     页面当前筛选参数（不含 current/size）
 */
export async function exportListAsSeen({ entityName, columns, fetchPage, params }) {
  const fields = columnsToExportFields(columns)
  const rows = fetchPage ? await collectFilteredRows(fetchPage, params) : []
  return exportAsSeen(entityName, fields, rows)
}

/**
 * 自定义列导出：用户选择要导出的字段后调用
 * @param {string} entityName 实体名
 * @param {string[]} selectedFields 用户选择的字段名列表（Java 字段名）
 */
export function exportDataWithSelectedFields(entityName, selectedFields) {
  const fileName = getExportFileName(entityName)
  const token = localStorage.getItem('token')
  return fetch(`${BASE_URL}/data/${entityName}/custom`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(selectedFields || [])
  })
    .then(response => {
      if (!response.ok) {
        // 非 2xx：统一取后端可读提示（含空响应体 / 非 JSON 兜底）
        return extractErrorMessage(response, tt('导出失败')).then(msg => { throw new Error(msg) })
      }
      return response.blob()
    })
    .then(blob => {
      if (!blob.type || blob.type === '' || blob.type === 'application/octet-stream') {
        blob = new Blob([blob], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      }
      saveAs(blob, fileName)
    })
    .catch(err => {
      ElMessage.error(tt('导出失败：') + (err.message || tt('未知错误')))
      throw err
    })
}

/**
 * 导出「勾选的 N 条」（列表页批量操作的"批量导出"专用，2026-09-19 新增）。
 *
 * 与 exportData 的区别：exportData 导当前筛选条件下的全量；本方法只导传入的这几条。
 * 后端对应 POST /api/export/data/{entityName}/selected，取数仍套数据权限（没权限的勾了也导不出）。
 *
 * @param {string} entityName 实体名（如 saleOrder）
 * @param {Array<number|string>} ids 勾选记录的主键数组
 */
export function exportSelectedRows(entityName, ids) {
  const fileName = getExportFileName(entityName)
  const token = localStorage.getItem('token')
  return fetch(`${BASE_URL}/data/${entityName}/selected`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(ids || [])
  })
    .then(response => {
      if (!response.ok) {
        return extractErrorMessage(response, tt('导出失败')).then(msg => { throw new Error(msg) })
      }
      return response.blob()
    })
    .then(blob => {
      if (!blob.type || blob.type === '' || blob.type === 'application/octet-stream') {
        blob = new Blob([blob], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      }
      saveAs(blob, fileName)
    })
}

export function importData(entityName, file, poolType, billType) {
  const token = localStorage.getItem('token')
  const formData = new FormData()
  formData.append('file', file)
  let url = `${BASE_URL}/import/${entityName}`
  const query = []
  if (poolType !== undefined && poolType !== null && poolType !== '') query.push(`poolType=${poolType}`)
  // 生产退料/补料与领料共用 material_requisition：billType 区分单据类型
  if (billType) query.push(`billType=${encodeURIComponent(billType)}`)
  if (query.length) url += `?${query.join('&')}`

  return fetch(url, {
    method: 'POST',
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: formData
  })
    .then(response => parseJsonResponse(response, tt('导入失败')))
}

export function getExportConfig(entityName) {
  const token = localStorage.getItem('token')
  return fetch(`${BASE_URL}/config/${entityName}`, {
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) }
  })
    .then(response => parseJsonResponse(response, tt('获取配置失败')))
}

/**
 * 下载【精简18列统一模板】（推荐）：左半 9 列为 BOM 主属性（同一母件只需在第1行填写，后续行自动继承），右半 9 列为子件明细，同时兼容一级 BOM 和多级 BOM 导入。
 * 支持可选字段自定义：传入 fields = 列标签数组（后端 getBomTemplateFields 返回的 label），
 * 若传 null/空数组 = 导出默认 18 列；系统锁定必填列（母件编号、子件编码、用量三列，模板头带星号标识）即使没勾选后端也会自动追加。
 */
export function downloadBomUnifiedTemplate(selectedFields) {
  let url = `${BASE_URL}/bom/template`
  if (selectedFields && Array.isArray(selectedFields) && selectedFields.length > 0) {
    const qs = selectedFields.map(f => `fields=${encodeURIComponent(f)}`).join('&')
    url += '?' + qs
  }
  const suffix = (selectedFields && selectedFields.length > 0) ? `_自定义${selectedFields.length}列` : '_默认18列'
  return downloadFile(url, 'GET', `BOM导入模板${suffix}.xlsx`)
}

/** 下载旧版兼容模板（11列纯树形多级格式，不支持字段自定义勾选，保留历史存量文件兼容） */
export function downloadBomLegacyTemplate() {
  return downloadFile(`${BASE_URL}/bom/legacy-template`, 'GET', 'BOM导入模板_旧版兼容11列.xlsx')
}

/** 获取BOM导入模板可勾选字段清单（供自定义面板渲染使用） */
export function getBomTemplateFields() {
  const token = localStorage.getItem('token')
  return fetch(`${BASE_URL}/bom/template/fields`, {
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) }
  })
    .then(response => parseJsonResponse(response, tt('获取BOM模板字段配置失败')))
}

/** 【废弃】旧函数名（downloadBomTemplate），实际走旧版兼容模板（避免老代码引用出错） */
export function downloadBomTemplate() {
  return downloadBomLegacyTemplate()
}

function downloadFileWithToken(url, method, fileName, token) {
  return fetch(url, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) }
  })
    .then(response => {
      if (!response.ok) throw new Error(tt('下载失败'))
      return response.blob()
    })
    .then(blob => {
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = fileName
      link.click()
      URL.revokeObjectURL(link.href)
    })
}

/**
 * BOM导入预检查 - 检测缺失物料
 */
export function checkBomImport(file) {
  const token = localStorage.getItem('token')
  const formData = new FormData()
  formData.append('file', file)

  return fetch(`${BASE_URL}/bom/import/check`, {
    method: 'POST',
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: formData
  })
    .then(response => parseJsonResponse(response, tt('预检查失败')))
}

/**
 * BOM确认导入 - 可选择自动创建缺失物料 + 指定冲突母件处理策略
 * @param {File} file           Excel文件
 * @param {boolean} autoCreateMaterials  是否自动创建缺失物料
 * @param {'COVER'|'NEW_VERSION'|'SKIP'} conflictPolicy  已有母件BOM时的处理策略：
 *   COVER：覆盖现有BOM（保留原编号/默认状态，删除旧明细后写入新明细）；
 *   NEW_VERSION：新增一条该母件的新版BOM（is_default=0，避免破坏唯一性）；
 *   SKIP：跳过所有冲突母件的行。
 */
export function confirmBomImport(file, autoCreateMaterials = false, conflictPolicy = 'NEW_VERSION') {
  const token = localStorage.getItem('token')
  const formData = new FormData()
  formData.append('file', file)
  formData.append('autoCreateMaterials', autoCreateMaterials)
  formData.append('onExistingConflict', conflictPolicy || 'NEW_VERSION')

  return fetch(`${BASE_URL}/bom/import/confirm`, {
    method: 'POST',
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: formData
  })
    .then(response => parseJsonResponse(response, tt('导入失败')))
}

/**
 * BOM一键同步缺失物料 - 将Excel中不存在于物料库的物料批量创建到物料库
 * @param {File} file  Excel文件（与预检查同一个文件）
 */
export function syncBomMissingMaterials(file) {
  const token = localStorage.getItem('token')
  const formData = new FormData()
  formData.append('file', file)

  return fetch(`${BASE_URL}/bom/import/sync-materials`, {
    method: 'POST',
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: formData
  })
    .then(response => parseJsonResponse(response, tt('同步物料失败')))
}

/**
 * 物料导入预检查 - 与物料库比对存在性（先编码、后名称），返回冲突清单供用户选择"覆盖/跳过"
 * @param {File} file Excel文件
 */
export function checkMaterialImport(file) {
  const token = localStorage.getItem('token')
  const formData = new FormData()
  formData.append('file', file)

  return fetch(`${BASE_URL}/material/import/check`, {
    method: 'POST',
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: formData
  })
    .then(response => parseJsonResponse(response, tt('预检查失败')))
}

/**
 * 物料导入确认 - 按策略处理已存在物料
 * @param {File} file Excel文件
 * @param {'SKIP'|'OVERWRITE'} onExistingConflict SKIP=跳过已存在（默认）/ OVERWRITE=覆盖已存在（物料编码保持原值）
 */
export function confirmMaterialImport(file, onExistingConflict = 'SKIP') {
  const token = localStorage.getItem('token')
  const formData = new FormData()
  formData.append('file', file)
  formData.append('onExistingConflict', onExistingConflict || 'SKIP')

  return fetch(`${BASE_URL}/material/import/confirm`, {
    method: 'POST',
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: formData
  })
    .then(response => parseJsonResponse(response, tt('导入失败')))
}

function getTemplateFileName(entityName) {
  const nameMap = {
    customer: '客户导入模板.xlsx',
    unit: '计量单位导入模板.xlsx',
    department: '部门导入模板.xlsx',
    employee: '员工导入模板.xlsx',
    materialCategory: '物料分类导入模板.xlsx',
    material: '物料导入模板.xlsx',
    supplier: '供应商导入模板.xlsx',
    warehouse: '仓库导入模板.xlsx',
    account: '科目导入模板.xlsx',
    currency: '货币导入模板.xlsx',
    paymentTerm: '付款条件导入模板.xlsx',
    priceLevel: '价格等级导入模板.xlsx',
    settlementMode: '结算方式导入模板.xlsx',
    saleOrder: '销售订单导入模板.xlsx',
    saleQuote: '销售报价单导入模板.xlsx',
    saleContract: '销售合同导入模板.xlsx',
    saleDelivery: '销售出库单导入模板.xlsx',
    saleReturn: '销售退货单导入模板.xlsx',
    saleInvoice: '销售发票导入模板.xlsx',
    purchaseOrder: '采购订单导入模板.xlsx',
    purchaseRequest: '采购申请单导入模板.xlsx',
    purchaseContract: '采购合同导入模板.xlsx',
    purchaseInbound: '采购入库单导入模板.xlsx',
    purchaseReturn: '采购退货单导入模板.xlsx',
    purchaseInvoice: '采购发票导入模板.xlsx',
    otherReceipt: '其他入库单导入模板.xlsx',
    otherIssue: '其他出库单导入模板.xlsx',
    stockTransfer: '库存调拨单导入模板.xlsx',
    stockCheck: '库存盘点单导入模板.xlsx',
    materialRequisition: '生产领料单导入模板.xlsx',
    subcontract: '委外订单导入模板.xlsx',
    subcontractPick: '委外发料单导入模板.xlsx',
    subcontractInbound: '委外入库单导入模板.xlsx',
    subcontractReturn: '委外退料单导入模板.xlsx',
    subcontractSupplement: '委外补料单导入模板.xlsx',
    paymentVoucher: '付款单导入模板.xlsx',
    paymentReceipt: '收款单导入模板.xlsx',
    purchasePrePayment: '采购预付款单导入模板.xlsx',
    salePreReceipt: '销售预收款单导入模板.xlsx',
    operationExpense: '费用支出单导入模板.xlsx',
    bom: 'BOM导入模板.xlsx',
    workOrder: '生产工单导入模板.xlsx',
    salePriceStrategy: '销售价格策略导入模板.xlsx',
    purchasePriceStrategy: '采购价格策略导入模板.xlsx'
  }
  return nameMap[entityName] || '导入模板.xlsx'
}

function getExportFileName(entityName) {
  const nameMap = {
    customer: '客户数据',
    unit: '计量单位数据',
    department: '部门数据',
    employee: '员工数据',
    materialCategory: '物料分类数据',
    material: '物料数据',
    supplier: '供应商数据',
    warehouse: '仓库数据',
    account: '科目数据',
    currency: '货币数据',
    paymentTerm: '付款条件数据',
    priceLevel: '价格等级数据',
    settlementMode: '结算方式数据',
    saleOrder: '销售订单数据',
    saleQuote: '销售报价单数据',
    saleContract: '销售合同数据',
    saleDelivery: '销售出库单数据',
    saleReturn: '销售退货单数据',
    saleInvoice: '销售发票数据',
    purchaseOrder: '采购订单数据',
    purchaseRequest: '采购申请单数据',
    purchaseContract: '采购合同数据',
    purchaseInbound: '采购入库单数据',
    purchaseReturn: '采购退货单数据',
    purchaseInvoice: '采购发票数据',
    otherReceipt: '其他入库单数据',
    otherIssue: '其他出库单数据',
    stockTransfer: '库存调拨单数据',
    stockCheck: '库存盘点单数据',
    materialRequisition: '生产领料单数据',
    subcontract: '委外订单数据',
    subcontractPick: '委外发料单数据',
    subcontractInbound: '委外入库单数据',
    subcontractReturn: '委外退料单数据',
    subcontractSupplement: '委外补料单数据',
    paymentVoucher: '付款单数据',
    paymentReceipt: '收款单数据',
    purchasePrePayment: '采购预付款单数据',
    salePreReceipt: '销售预收款单数据',
    operationExpense: '费用支出单数据',
    bom: 'BOM数据',
    workOrder: '生产工单数据',
    salePriceStrategy: '销售价格策略数据',
    purchasePriceStrategy: '采购价格策略数据'
  }
  const baseName = nameMap[entityName] || '数据'
  return `${baseName}_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.xlsx`
}