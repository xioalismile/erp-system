import request from '@/utils/request'

// 打印模板管理 API
export const printTemplateApi = {
  /** 分页查询模板列表 */
  list(params) {
    return request.get('/print-template', { params })
  },
  /** 获取模板详情(含字段) */
  getDetail(id) {
    return request.get(`/print-template/${id}`)
  },
  /** 获取指定单据类型的默认模板 */
  getDefault(docType) {
    return request.get(`/print-template/default/${docType}`)
  },
  /** 新增模板 */
  save(data) {
    return request.post('/print-template', data)
  },
  /** 修改模板 */
  update(data) {
    return request.put('/print-template', data)
  },
  /** 删除模板 */
  delete(id) {
    return request.delete(`/print-template/${id}`)
  },
  /** 设为默认 */
  setDefault(id) {
    return request.put(`/print-template/${id}/default`)
  },
  /** 复制模板 */
  copy(id, newName) {
    return request.post(`/print-template/${id}/copy`, null, { params: { newName } })
  },
  /** 获取指定单据类型的所有可用模板 */
  listByDocType(docType) {
    return request.get(`/print-template/list/${docType}`)
  },
  /**
   * 获取打印模板「可用字段」全量（唯一来源在后端；2026-10-10 起前端不再自存一份）
   * @returns { commonItemFields: [], docTypeFieldsMap: { docType: { header:[], items:[], footer:[] } } }
   */
  getFieldMap() {
    return request.get('/print-template/field-map')
  },
  /**
   * 设计态预览：用样例数据渲染当前正在编辑、尚未保存的模板
   * 与真实打印同一套后端渲染逻辑，保证预览与打印一致
   */
  previewHtml(data) {
    return request.post('/print-template/preview', data)
  }
}

// 打印 API
export const printApi = {
  /**
   * 按单据打印: 自动查找默认模板 → 获取单据数据 → 渲染HTML
   * @param billType 单据类型 (如 SALE_ORDER)
   * @param billId   单据ID
   * @returns 渲染后的HTML字符串
   */
  printByBill(billType, billId) {
    return request.get(`/print-template/print/${billType}/${billId}`)
  },

  /**
   * 按指定模板打印单据
   * @param billType   单据类型
   * @param billId     单据ID
   * @param templateId 模板ID
   */
  printByTemplate(billType, billId, templateId) {
    return request.get(`/print-template/print/${billType}/${billId}/${templateId}`)
  },

  /**
   * 批量标签打印（商品标签 / 工序卡）：多选 + 份数 + 连排 + 条码与二维码双打
   * @param {Object} params { t:'MATERIAL'|'WORK_OPERATION', ids:'1,2,3', copies, paper, barcode, fields }
   * @returns 渲染后的整页 HTML 字符串
   */
  printLabels(params) {
    return request.get('/qrcode/labels', { params })
  }
}

export default printApi
