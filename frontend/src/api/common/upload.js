// ====================================================================
// 通用文件上传 API
// 后端接口: POST /api/files/upload (单文件) / POST /api/files/uploads (多文件)
// 返回文件访问URL, 如 /uploads/material/2026/07/05/xxx.jpg
// ====================================================================
import request from '@/utils/request'

/**
 * 单文件上传
 * @param {File} file 文件对象
 * @param {String} type 业务类型(如 material/sale-order/delivery), 用于分子目录
 * @returns {Promise<String>} 文件URL
 */
export function uploadFile(file, type = 'common') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', type)
  return request.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 多文件上传
 * @param {File[]} files 文件数组
 * @param {String} type 业务类型
 * @returns {Promise<String[]>} 文件URL数组
 */
export function uploadFiles(files, type = 'common') {
  const formData = new FormData()
  files.forEach(f => formData.append('files', f))
  formData.append('type', type)
  return request.post('/files/uploads', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 将逗号分隔的URL字符串转为数组
 */
export function parseUrls(str) {
  if (!str) return []
  return String(str).split(',').filter(Boolean)
}

/**
 * 将URL数组转为逗号分隔字符串
 */
export function joinUrls(arr) {
  return (arr || []).filter(Boolean).join(',')
}
