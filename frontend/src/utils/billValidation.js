import { nextTick } from 'vue'
import { getCurrentUserFieldPermission, getFieldMetaList } from '@/api/system/fieldPermission'
import { resolvePageFieldLabels } from '@/utils/fieldRequiredMark'
import { tt } from '@/i18n'

/** 字段权限字段标签缓存（pageKey -> { fieldKey: 中文标签 }）；元数据稳定，进程内缓存即可 */
const fieldLabelCache = new Map()

/**
 * 采集「字段权限-角色必填」缺失项（与 BaseEdit 同口径：静态必填 OR 角色必填）。
 * 仅校验前端表单模型里存在的字段；管理员后端短路返回空 Map，故不会叠加。
 *
 * @param {Object} [pageLabels] 字段名 → 页面叫法（客户 / 付款条件…），优先于元数据叫法（客户ID / 付款条件ID）
 * @param {Function} [alreadyReported] 该字段是否已在清单里（表单校验已报过就跳过，避免同一字段报两遍）
 */
async function collectFieldPermissionMissing(pageKey, model, push, pageLabels, alreadyReported) {
  try {
    const permMap = await getCurrentUserFieldPermission(pageKey)
    if (!permMap || typeof permMap !== 'object') return
    const requiredKeys = Object.values(permMap)
      .filter(p => p && p.required === 1 && p.visible !== 0)
      .map(p => p.fieldKey)
      .filter(Boolean)
    if (requiredKeys.length === 0) return

    let labels = fieldLabelCache.get(pageKey)
    if (!labels) {
      const metas = await getFieldMetaList(pageKey)
      labels = {}
      ;(Array.isArray(metas) ? metas : []).forEach(m => {
        if (m && m.fieldKey) labels[m.fieldKey] = m.fieldName || m.fieldKey
      })
      fieldLabelCache.set(pageKey, labels)
    }
    const toCamel = (k) => String(k).replace(/_([a-z])/g, (_, c) => c.toUpperCase())
    const hasOwn = (o, k) => !!o && Object.prototype.hasOwnProperty.call(o, k)
    requiredKeys.forEach(key => {
      // 权限表字段名是**蛇形**（order_date / customer_id），页面表单模型是**驼峰**（orderDate / customerId）。
      // 不映射就会按 model['order_date'] 取到 undefined → 明明填了客户和日期也报"未填写"，
      // 且提示里出现英文键名（2026-11-11 实锤：销售订单复制后提交被误拦）。
      const camelKey = toCamel(key)
      const modelKey = hasOwn(model, key) ? key : (hasOwn(model, camelKey) ? camelKey : null)
      // 模型里根本没有这个字段 = 本页没有这个输入项（如展示用的 customer_name），不参与必填拦截
      if (!modelKey) return
      const v = model[modelKey]
      if (v === '' || v === null || v === undefined) {
        // 页面叫法优先，元数据叫法兜底（修掉「客户ID / 付款条件ID」这类配置页内部叫法）
        const label = (pageLabels && (pageLabels[modelKey] || pageLabels[key])) || labels[modelKey] || labels[key] || modelKey
        if (alreadyReported && alreadyReported(modelKey, label)) return
        push(modelKey, `${label} ${tt('不能为空')}`, label)
      }
    })
  } catch (e) {
    // 字段权限读取失败不阻断保存（与后端"取不到就不叠加"一致）
  }
}

/**
 * 单据必填字段统一校验（提交前调用）
 *
 * 收集头部表单（headerFormRef）与明细/其他表单（formRef）的校验错误，
 * 并结合 DOM 中已渲染的错误提示解析出字段中文名，返回缺失字段列表。
 *
 * @param {Object} opts
 * @param {Ref} [opts.headerFormRef] 头部 el-form 实例 ref（可省略）
 * @param {Ref} [opts.formRef] 其他 el-form 实例 ref（可省略）
 * @param {Object} [opts.rules] 校验规则（预留，当前从表单实例内部规则校验）
 * @param {Document|Element} [opts.$el] 根元素，默认 document
 * @param {string} [opts.pageKey] 字段权限 pageKey（传入则额外叠加"角色必填"校验）
 * @param {Object} [opts.model] 表头表单数据对象（叠加角色必填时需要，用于取值判空）
 * @returns {Promise<Array<{prop:string,label:string,message:string}>>}
 *   缺失字段列表（为空数组表示校验通过）
 */
export async function validateBillRequiredFields({ headerFormRef, formRef, rules, $el, pageKey, model, labels } = {}) {
  const missing = []
  const seen = new Set()

  // 1. 触发表单校验（callback 形式不会抛异常，错误统一收集）
  const validateForm = (formInst) => new Promise((resolve) => {
    if (!formInst || typeof formInst.validate !== 'function') return resolve({})
    try {
      const maybePromise = formInst.validate((isValid, invalidFields) => {
        resolve(isValid ? {} : (invalidFields || {}))
      })
      // 兜底：若返回 Promise（未走 callback 分支），捕获避免未处理异常
      if (maybePromise && typeof maybePromise.catch === 'function') {
        maybePromise.catch(() => resolve({}))
      }
    } catch (e) {
      resolve({})
    }
  })
  const results = await Promise.all([
    validateForm(headerFormRef && headerFormRef.value),
    validateForm(formRef && formRef.value)
  ])

  // 2. 等待错误提示渲染到 DOM 后，从 DOM 解析错误消息 -> 字段中文名（label）
  await nextTick()
  const root = $el || (typeof document !== 'undefined' ? document : null)
  const labelMap = new Map()
  if (root && typeof root.querySelectorAll === 'function') {
    try {
      root.querySelectorAll('.el-form-item.is-error').forEach(item => {
        const labelEl = item.querySelector('.el-form-item__label')
        const errEl = item.querySelector('.el-form-item__error')
        const label = (labelEl ? labelEl.textContent : '').replace(/[*\s:：]/g, '')
        const err = errEl ? errEl.textContent.trim() : ''
        if (label && err && !labelMap.has(err)) labelMap.set(err, label)
      })
    } catch (e) { /* DOM 采集失败不影响校验结果 */ }
  }

  // 2.1 页面叫法：字段名 → 页面上显示的名字（客户 / 付款条件），优先于字段元数据叫法
  //     （客户ID / 付款条件ID）—— 缺项清单必须说用户认得的名字（与星号标注同一套匹配）
  const pageLabels = pageKey ? await resolvePageFieldLabels(pageKey, root) : {}

  // 3. 合并两个表单的校验错误（按 prop+message 去重）
  const push = (prop, message, explicitLabel) => {
    const msg = message || tt('该字段不能为空')
    const key = (prop || '') + '|' + msg
    if (seen.has(key)) return
    seen.add(key)
    // 2026-11-12：页面叫法/元数据叫法都取不到时，从中文校验语里反推字段名
    // （"请选择采购员" → 采购员），避免提示里露出 buyerName 这类英文键名（用户看不懂）。
    const deriveLabel = (m) => {
      const t = String(m || '').replace(/^(请选择|请填写|请输入|请上传|请添加)/, '').replace(/(不能为空|不可为空|必填)$/, '').trim()
      return t && t !== String(m || '').trim() ? t : ''
    }
    missing.push({
      prop: prop || '',
      // 2026-11-11：调用页可传 labels（字段名→页面叫法）作为最高优先。
      // 部分页面的字段（如客户/供应商）不在字段权限元数据里，取不到页面叫法时会退化成英文 key
      // （用户看到“customerId 不能为空”，等于看不懂的提示），由调用页显式兜底。
      label: (labels && labels[prop]) || explicitLabel || pageLabels[prop] || labelMap.get(msg) || deriveLabel(msg) || '',
      message: msg
    })
  }
  results.forEach(invalid => {
    if (!invalid) return
    Object.keys(invalid).forEach(prop => {
      const errs = invalid[prop]
      if (Array.isArray(errs)) {
        errs.forEach(e => push(prop, e && e.message ? e.message : tt('该字段不能为空')))
      } else if (errs && errs.message) {
        push(prop, errs.message)
      } else {
        push(prop, tt('该字段不能为空'))
      }
    })
  })

  // 4. 兜底：表单实例校验未覆盖、但 DOM 已渲染出的错误（如原生 required）
  if (missing.length === 0 && root && typeof root.querySelectorAll === 'function') {
    try {
      root.querySelectorAll('.el-form-item.is-error .el-form-item__error').forEach(errEl => {
        const msg = (errEl.textContent || '').trim()
        if (!msg || seen.has('|' + msg)) return
        seen.add('|' + msg)
        const item = errEl.closest ? errEl.closest('.el-form-item') : null
        const labelEl = item ? item.querySelector('.el-form-item__label') : null
        const label = (labelEl ? labelEl.textContent : '').replace(/[*\s:：]/g, '')
        missing.push({ prop: '', label, message: msg })
      })
    } catch (e) { /* ignore */ }
  }

  // 5. 字段权限（角色必填）叠加：与 BaseEdit、导入校验同口径，保证"界面拦 = 模板星号 = 导入拦"
  if (pageKey && model) {
    // 同一字段若已由表单校验报过（例如"客户"已在清单里），不再重复列一遍
    await collectFieldPermissionMissing(pageKey, model, push, pageLabels, (key, label) =>
      missing.some(m => m.prop === key || (label && m.label === label)))
  }

  return missing
}

export default validateBillRequiredFields
