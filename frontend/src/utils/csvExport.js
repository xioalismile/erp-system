/**
 * 通用 CSV 导出工具（纯前端，不依赖后端接口）
 *
 * 用法：exportCsv(columns, data, filename)
 * - columns: 列定义数组 [{ label: '列名', prop: '字段名' }, ...]
 * - data:    行数据数组（对象数组，字段取值支持嵌套路径 a.b.c）
 * - filename: 导出文件名（不含扩展名），自动追加日期后缀
 *
 * 规则：
 * - 值含逗号/引号/换行时用双引号包裹并把内部双引号转义为两个双引号（RFC 4180）
 * - 文件头加 BOM(\uFEFF) 防止 Excel 打开中文乱码
 */

function getNestedValue(obj, path) {
  if (!path) return ''
  const parts = String(path).split('.')
  let cur = obj
  for (const p of parts) {
    if (cur == null) return ''
    cur = cur[p]
  }
  return cur
}

function escapeCsvValue(val) {
  if (val === null || val === undefined) return ''
  let s = String(val)
  // 日期对象统一转字符串
  if (val instanceof Date) s = val.toISOString().slice(0, 10)
  if (/[",\r\n]/.test(s)) {
    s = '"' + s.replace(/"/g, '""') + '"'
  }
  return s
}

export function exportCsv(columns, data, filename) {
  if (!Array.isArray(columns) || columns.length === 0) {
    throw new Error('导出列定义不能为空')
  }
  const rows = Array.isArray(data) ? data : []

  const lines = []
  lines.push(columns.map(c => escapeCsvValue(c.label)).join(','))
  for (const row of rows) {
    lines.push(columns.map(c => escapeCsvValue(getNestedValue(row, c.prop))).join(','))
  }

  const csv = '\uFEFF' + lines.join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  const date = new Date()
  const pad = n => String(n).padStart(2, '0')
  const dateStr = `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
  link.href = url
  link.download = `${filename || '导出数据'}_${dateStr}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 延后回收 objectURL（防御性写法）：click 后立即 revoke 会与浏览器读取 blob 竞争，
  // 表现为"点了导出没有任何文件/偶发下载失败"。与 Blob 导出链路（exportUtil / 报表页）同口径。
  setTimeout(() => URL.revokeObjectURL(url), 4000)
}
