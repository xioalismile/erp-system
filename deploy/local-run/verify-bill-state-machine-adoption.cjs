#!/usr/bin/env node
/**
 * 单据统一状态机接入排查（2026-11-09）
 *
 * 排查口径（比"有没有写 super"更准确）：
 *   1. 必须继承统一状态机基类（AbstractBillService）；
 *   2. 不得在业务方法里自写状态迁移（setStatus("已生效") / .set("status","待审核") 等），
 *      状态只能由基类模板方法推进；唯一例外是基类没有的动作（如"取消"→"已取消"）
 *      与单据特有业务分支，需人工确认后在下方白名单登记。
 *   3. 基类抽象方法（严格校验、单据标识、状态访问器）由编译强制保证，不重复检测。
 *
 * 用法：node deploy/local-run/verify-bill-state-machine-adoption.cjs
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '../../backend/src/main/java/com/erp')

// 已知豁免：不是业务单据，或已另有说明
const EXEMPT = {
  'production/service/RoutingServiceImpl.java': '工艺路线已降为主档，审核动作直接提示不再审核',
  'finance/service/FinanceServiceImpl.java': '收款单/付款单共用一个服务，需先拆分才能接入（未迁移）'
}

// 允许的自写状态迁移（基类没有该动作，或属于单据初始化/业务执行态）
// 分类依据（2026-11-09 逐条核实 67 处）：
//   ① 初始化：新建单据、下推生成下游单据 → 草稿
//   ② 基类未提供的动作：取消/关闭 → 已取消/已关闭
//   ③ 业务执行态（与审批状态同存 status 列的历史设计）：盘点中/已盘点、未开始/进行中/已完成、
//      打样中/已确认/已转BOM、已发货、已核算、已停用、待付款
//   ④ 单据特有业务分支（代码内已注释）：销售合同月结授信提交时先挂"待审核"，生效仍由基类审核入口完成
const ALLOWED_SELF_TRANSITION = {
  '*': ['草稿', '已取消', 'CANCELED', '关闭', '已关闭',
    '盘点中', '已盘点', '未开始', '进行中', '已完成',
    '打样中', '已确认', '已转BOM', '已发货', '已核算', '已停用', '待付款'],
  'sale/service/impl/SaleContractServiceImpl.java': ['待审核'],
  // BOM 是主档，status 同时承载"审批态"与"启用态"（历史设计）：enable() 把已停用改回已生效。
  // 本轮不做语义拆分（会影响 BOM 选用、MRP 取 BOM、列表筛选），登记为已知例外。
  'masterdata/service/BomServiceImpl.java': ['已生效'],
  // 放行单：此处是给"样品留样记录"设置状态，不是放行单本身，属其它实体。
  'production/service/impl/ReleaseOrderServiceImpl.java': ['已生效']
}

const STATE_WRITE_PATTERNS = [
  // entity.setStatus("xxx")
  { re: /\.setStatus\s*\(\s*"([^"]+)"\s*\)/g, kind: 'setStatus' },
  // UpdateWrapper .set("status", "xxx")
  { re: /\.set\s*\(\s*"status"\s*,\s*"([^"]+)"\s*\)/g, kind: 'updateSet' }
]

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name)
    const st = fs.statSync(full)
    if (st.isDirectory()) walk(full, out)
    else if (name.endsWith('ServiceImpl.java')) out.push(full)
  }
  return out
}

const files = walk(ROOT)
const adopted = []
const suspicious = []
const exempt = []
let unadopted = []

for (const f of files) {
  const rel = path.relative(ROOT, f).replace(/\\/g, '/')
  let text = ''
  try { text = fs.readFileSync(f, 'utf8') } catch (_) { continue }
  const extendsBase = /extends\s+(?:[A-Za-z_$][\w$]*\.)*AbstractBillService/.test(text)
  const hasOwnStateMachine = /public\s+void\s+(submit|approve)\s*\(/.test(text)

  if (!extendsBase) {
    if (EXEMPT[rel]) { exempt.push({ rel, reason: EXEMPT[rel] }); continue }
    if (hasOwnStateMachine) unadopted.push(rel)
    continue
  }
  if (EXEMPT[rel]) { exempt.push({ rel, reason: EXEMPT[rel] }); continue }

  adopted.push(rel)

  // 扫自写状态迁移
  const lines = text.split('\n')
  const allowed = [...(ALLOWED_SELF_TRANSITION['*'] || []), ...(ALLOWED_SELF_TRANSITION[rel] || [])]
  for (const p of STATE_WRITE_PATTERNS) {
    p.re.lastIndex = 0
    let m
    while ((m = p.re.exec(text)) !== null) {
      const value = m[1]
      if (allowed.includes(value)) continue
      const line = text.slice(0, m.index).split('\n').length
      // 标注所在方法名（向上找最近的方法声明）
      let method = ''
      for (let i = line - 1; i >= 0; i--) {
        const mm = lines[i].match(/^\s{0,8}(?:public|protected|private)\s+[\w<>\[\], .]+\s+(\w+)\s*\(/)
        if (mm) { method = mm[1]; break }
      }
      suspicious.push({ rel, line, value, kind: p.kind, method })
    }
  }
}

console.log('==== 单据统一状态机接入排查 ====')
console.log('扫描 Service 实现: ' + files.length + ' 个')
console.log('已继承统一状态机: ' + adopted.length + ' 张（含豁免 ' + exempt.length + ' 张）')
console.log('仍未接入且自写提交/审核: ' + unadopted.length + ' 张')
console.log('')
if (exempt.length) {
  console.log('---- 豁免 ----')
  for (const e of exempt) console.log('  · ' + e.rel + ' — ' + e.reason)
  console.log('')
}
if (unadopted.length) {
  console.log('---- 未接入 ----')
  for (const r of unadopted) console.log('  · ' + r)
  console.log('')
}
console.log('---- 自写状态迁移（需逐条核实是否基类未提供的动作） ----')
if (!suspicious.length) {
  console.log('  无：全部状态迁移均走统一基类')
} else {
  for (const s of suspicious) {
    console.log('  ' + s.rel + ':' + s.line + '  ' + s.kind + '("' + s.value + '")  方法: ' + (s.method || '?'))
  }
}
console.log('')
console.log('结论：已接入 ' + adopted.length + ' 张 / 未接入 ' + unadopted.length + ' 张 / 自写状态迁移 ' + suspicious.length + ' 处')
process.exit(unadopted.length ? 1 : 0)
