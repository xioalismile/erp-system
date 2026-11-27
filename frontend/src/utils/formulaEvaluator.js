import { tt } from '@/i18n'
/**
 * 公式字段求值器
 * 支持 {{fieldKey}} 变量引用 + - * / () 四则运算
 * 安全：仅允许数字、运算符、括号、变量引用，禁止函数调用和关键字
 *
 * 用法：
 *   evaluateFormula('{{qty}} * {{price}} * (1 - {{rate}}/100)', { qty: 10, price: 100, rate: 10 })
 *   => 900
 *
 *   evaluateFormula('{{qty}} + 5', { qty: 10 })
 *   => 15
 */

/**
 * 求值公式表达式
 * @param {string} expr 公式表达式，如 "{{qty}} * {{price}}"
 * @param {Object} data 变量值字典 { fieldKey: value }
 * @returns {number|null} 计算结果，失败返回 null
 */
export function evaluateFormula(expr, data) {
  if (!expr || typeof expr !== 'string') return null
  try {
    // 1. 替换 {{fieldKey}} 为实际值
    let jsExpr = expr.replace(/\{\{(\w+)\}\}/g, (match, key) => {
      const v = data ? data[key] : undefined
      if (v === undefined || v === null || v === '') return '0'
      const n = Number(v)
      return isNaN(n) ? '0' : String(n)
    })
    // 2. 安全校验：仅允许数字、运算符、括号、小数点、空格
    if (!/^[\d+\-*/().\s]+$/.test(jsExpr)) {
      console.warn('[formula] 表达式包含非法字符:', jsExpr)
      return null
    }
    // 3. 求值（Function 构造，避免 eval 的作用域污染）
    // eslint-disable-next-line no-new-func
    const result = new Function('return (' + jsExpr + ')')()
    if (typeof result !== 'number' || isNaN(result) || !isFinite(result)) return null
    return result
  } catch (e) {
    console.warn('[formula] 求值失败:', expr, e.message)
    return null
  }
}

/**
 * 校验公式表达式语法（不计算，只检查格式）
 * @param {string} expr
 * @param {string[]} deps 依赖字段列表
 * @returns {{ valid: boolean, message: string }}
 */
export function validateFormula(expr, deps = []) {
  if (!expr || !expr.trim()) {
    return { valid: false, message: tt('公式表达式不能为空') }
  }
  // 检查变量引用格式
  const varMatches = expr.match(/\{\{(\w+)\}\}/g) || []
  for (const m of varMatches) {
    const key = m.replace(/[{}]/g, '')
    if (!deps.includes(key)) {
      return { valid: false, message: `${tt("变量 {{")}${key}${tt("}} 未在依赖字段中声明")}` }
    }
  }
  // 替换变量为 1 后做语法校验
  const testExpr = expr.replace(/\{\{(\w+)\}\}/g, '1')
  if (!/^[\d+\-*/().\s]+$/.test(testExpr)) {
    return { valid: false, message: tt('表达式包含非法字符（仅支持数字、+ - * / ()）') }
  }
  try {
    // eslint-disable-next-line no-new-func
    const r = new Function('return (' + testExpr + ')')()
    if (typeof r !== 'number') return { valid: false, message: tt('表达式结果不是数字') }
    return { valid: true, message: tt('公式语法正确') }
  } catch (e) {
    return { valid: false, message: tt('表达式语法错误：') + e.message }
  }
}

/**
 * 从公式表达式中提取所有 {{fieldKey}} 变量
 * @param {string} expr
 * @returns {string[]}
 */
export function extractFormulaDeps(expr) {
  if (!expr) return []
  const matches = expr.match(/\{\{(\w+)\}\}/g) || []
  return [...new Set(matches.map(m => m.replace(/[{}]/g, '')))]
}
