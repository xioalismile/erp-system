// 优化的页面配置解析器（v2）
// 针对Wuji无极低代码平台的真实结构提取字段/按钮/状态机/公式/权限规则
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, 'cloud-erp-exploration');
const PAGES_DIR = path.join(ROOT, 'pages');
const OUT_DIR = path.join(ROOT, 'parsed-v2');
if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });

// 字段组件类型 → 友好类型名
const FIELD_TYPE_MAP = {
  'public-w-input-2v': 'input',
  'public-w-input': 'input',
  'public-w-input-number': 'number',
  'public-w-number': 'number',
  'public-w-date': 'date',
  'public-w-date-range': 'date-range',
  'public-w-select:antd': 'select',
  'public-w-select': 'select',
  'public-w-radio': 'radio',
  'public-w-checkbox': 'checkbox',
  'public-w-switch': 'switch',
  'public-w-textarea': 'textarea',
  'public-w-cascader': 'cascader',
  'public-w-upload': 'upload',
  'public-w-table': 'table',
  'public-w-table-editable': 'editable-table',
  'public-w-auto-complete': 'autocomplete',
  'public-w-tree-select': 'tree-select',
  'public-w-time': 'time',
  'public-w-rate': 'rate',
  'public-w-editor': 'rich-text',
};

// 按钮：public-w-button-new / public-w-button 等
const BUTTON_TYPES = new Set([
  'public-w-button-new', 'public-w-button', 'w-button', 'public-w-btn',
]);

/**
 * 解析 displayCondition 提取权限/状态判断
 * 例：w.hasPermission('create.page','sell') → 权限:create.page:sell
 * 例：state.isEdit==true → 状态:isEdit==true
 */
function parseDisplayCondition(cond) {
  if (!cond || typeof cond !== 'string') return [];
  const result = [];
  // 权限判断
  const permMatches = [...cond.matchAll(/w\.hasPermission\(\s*['"]([^'"]+)['"]\s*(?:,\s*['"]([^'"]+)['"])?/g)];
  permMatches.forEach(m => {
    result.push({ kind: 'permission', code: m[1], target: m[2] || '' });
  });
  // checkPermissionColumn
  const colMatches = [...cond.matchAll(/\$app\.checkPermissionColumn\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]\s*\)/g)];
  colMatches.forEach(m => {
    result.push({ kind: 'columnPermission', module: m[1], page: m[2], column: m[3] });
  });
  // 状态判断 state.xxx
  const stateMatches = [...cond.matchAll(/state\.(\w+)\s*(==|!=|>=|<=|>|<)\s*['"]?(\w+)['"]?/g)];
  stateMatches.forEach(m => {
    result.push({ kind: 'state', var: m[1], op: m[2], value: m[3] });
  });
  // formData.status
  const statusMatches = [...cond.matchAll(/data\.\w+\.formData\.status\s*(==|!=|>=|<=|>|<|\bincludes\b|\bin\b)\s*['"]?(\w+)['"]?/g)];
  statusMatches.forEach(m => {
    result.push({ kind: 'docStatus', op: m[1], value: m[2] });
  });
  return result;
}

/**
 * 提取脚本中的关键逻辑：
 * - 公式（含 = 的赋值）
 * - API 调用
 * - 工作流调用
 * - 状态切换
 * - 下推/选单操作
 */
function extractScriptLogic(script) {
  if (!script || typeof script !== 'string') return null;
  const formulas = [];
  const apiCalls = [];
  const workflowCalls = [];
  const statusChanges = [];
  const turnOperations = [];
  const fieldLinks = [];

  // 公式：item.xxx = ... 或 formData.xxx = ...
  const formulaRe = /(?:item|data\.\w+\.formData|formData)\.(\w+)\s*=\s*([^;\n]{3,200}?)(?=[;\n])/g;
  let m;
  while ((m = formulaRe.exec(script)) !== null) {
    const expr = m[2].trim();
    if (/^(true|false|null|undefined|\d+)$/.test(expr)) continue;
    formulas.push({ target: m[1], expr });
  }
  // 金额/税额/数量类公式（更严格）
  const amountRe = /\b(\w+)\.(\w+)\s*=\s*Number\(\(([^)]+?)\)\.toFixed\((\d+)\)\)/g;
  while ((m = amountRe.exec(script)) !== null) {
    formulas.push({ target: `${m[1]}.${m[2]}`, expr: `Number((${m[3]}).toFixed(${m[4]}))`, kind: 'rounded' });
  }
  // 通用乘除
  const calcRe = /\b(\w+)\.(\w+)\s*=\s*(\bitem\.\w+\s*[\*\/\+\-]\s*[\w\.]+)/g;
  while ((m = calcRe.exec(script)) !== null) {
    formulas.push({ target: `${m[1]}.${m[2]}`, expr: m[3], kind: 'calc' });
  }

  // API 调用：$app.* / w.request / fetch 等
  const apiRe = /\$(app|flow)\.(\w+)\s*\(/g;
  while ((m = apiRe.exec(script)) !== null) {
    apiCalls.push({ ns: m[1], method: m[2] });
  }
  const httpRe = /(?:await\s+)?(?:\$app\.request|w\.request|axios)\s*\(\s*\{\s*url:\s*['"]([^'"]+)['"]/g;
  while ((m = httpRe.exec(script)) !== null) {
    apiCalls.push({ url: m[1] });
  }

  // 工作流
  const wfRe = /\$app\.workflow\(\s*['"](\w+)['"]/g;
  while ((m = wfRe.exec(script)) !== null) {
    workflowCalls.push(m[1]);
  }

  // 状态切换：updateData.status = X
  const statusRe = /(?:updateData|formData|data\.\w+\.formData)\.status\s*=\s*(\d+)/g;
  while ((m = statusRe.exec(script)) !== null) {
    statusChanges.push({ field: 'status', value: Number(m[1]) });
  }
  const flowStatusRe = /(?:updateData|formData)\.flow_status\s*=\s*['"](\w+)['"]/g;
  while ((m = flowStatusRe.exec(script)) !== null) {
    statusChanges.push({ field: 'flow_status', value: m[1] });
  }

  // 下推/选单：turn_config / source_type / push down
  if (/turn_config|turned_/.test(script)) {
    const turnRe = /source_type['"]\s*[:,]\s*['"](\w+)['"]/g;
    while ((m = turnRe.exec(script)) !== null) {
      turnOperations.push(m[1]);
    }
    if (/source_?id|sourceId/.test(script)) turnOperations.push('pull-in');
  }

  // 字段联动（被赋值的字段）
  const linkRe = /data\.\w+\.formData\.(\w+)\s*=([^;\n]+)/g;
  while ((m = linkRe.exec(script)) !== null) {
    fieldLinks.push({ field: m[1], expr: m[2].trim().slice(0, 100) });
  }

  return { formulas, apiCalls, workflowCalls, statusChanges, turnOperations, fieldLinks };
}

/**
 * 递归遍历 layout 收集：
 * - fields (form fields)
 * - buttons (with click handlers and display conditions)
 * - scripts (runScript blocks)
 * - statusArrays (state machines)
 */
function walkLayout(node, ctx, depth = 0) {
  if (!node || typeof node !== 'object') return;
  const type = node.type || '';

  // 按钮
  if (BUTTON_TYPES.has(type)) {
    const props = node.props || {};
    const btnText = props.btnText || props.text || props.label || '';
    const displayCondition = node.displayCondition || '';
    const hasClick = !!(node.events && node.events[':handleClick']);
    ctx.buttons.push({
      text: btnText,
      type,
      hasClick,
      displayCondition,
      permissions: parseDisplayCondition(displayCondition),
    });
    // 提取按钮点击脚本
    if (hasClick && node.events[':handleClick'].steps) {
      collectScriptsFromSteps(node.events[':handleClick'].steps, ctx);
    }
  }

  // 表格（明细行）
  if (type === 'public-w-table' || type === 'public-w-table-editable' || type === 'w-table') {
    const cols = (node.props && node.props.columns) || [];
    if (Array.isArray(cols) && cols.length) {
      cols.forEach(c => {
        if (c && c.dataIndex) {
          ctx.detailColumns.push({
            field: c.dataIndex,
            title: c.title || '',
            required: !!c.required,
            editor: c.editor || c.editable || '',
          });
        }
      });
    }
  }

  // 表单字段
  if (type === 'w-form-item' && Array.isArray(node.children)) {
    const labelExpr = node.props && node.props.label;
    let label = '';
    if (typeof labelExpr === 'string') {
      if (labelExpr.startsWith('$expr:')) {
        const m = labelExpr.match(/\?\?"([^"]+)"/);
        label = m ? m[1] : labelExpr;
      } else {
        label = labelExpr;
      }
    } else if (labelExpr && labelExpr.$expr) {
      const m = String(labelExpr.$expr).match(/\?\?"([^"]+)"/);
      label = m ? m[1] : '';
    }

    node.children.forEach(child => {
      const ctype = child.type || '';
      const cp = child.props || {};
      // 字段类型识别
      let fieldType = FIELD_TYPE_MAP[ctype] || '';
      if (!fieldType && ctype.startsWith('public-w-')) fieldType = ctype.replace('public-w-', '');
      if (fieldType) {
        const fieldInfo = {
          label,
          field: cp.id || cp.field || cp.name || '',
          fieldType,
          required: !!cp.required,
          readonly: !!(cp.readonly && cp.readonly !== false),
          disabled: typeof cp.disabled === 'object' ? (cp.disabled.$expr || '') : (cp.disabled ? 'true' : ''),
          displayCondition: node.displayCondition || child.displayCondition || '',
        };
        // 解析权限/状态条件
        fieldInfo.conditions = parseDisplayCondition(fieldInfo.displayCondition);
        // 提取 v-model 绑定
        if (child.bindings && child.bindings['v-model']) {
          fieldInfo.vModel = child.bindings['v-model'];
        }
        // 选项（select/radio）
        if (cp.options || cp.dictCode) {
          fieldInfo.options = cp.options || [];
          fieldInfo.dictCode = cp.dictCode || '';
        }
        ctx.fields.push(fieldInfo);
      }
    });
  }

  // 状态机：props.statusArray
  if (node.props && node.props.statusArray) {
    const sa = node.props.statusArray;
    if (sa.$expr) {
      // 提取状态数组表达式
      try {
        const text = sa.$expr;
        const parsed = eval(`(${text})`);
        if (Array.isArray(parsed)) {
          ctx.statusArrays.push(parsed.map(s => ({ value: s.value, text: s.text, color: s.color })));
        }
      } catch (e) {
        ctx.statusArrays.push({ raw: sa.$expr.slice(0, 500) });
      }
    } else if (Array.isArray(sa)) {
      ctx.statusArrays.push(sa);
    }
  }

  // 递归子节点
  if (Array.isArray(node.children)) {
    node.children.forEach(c => walkLayout(c, ctx, depth + 1));
  }
  if (node.props && Array.isArray(node.props.columns)) {
    // table columns may have nested editors
  }
  if (node.events) {
    Object.keys(node.events).forEach(evName => {
      const ev = node.events[evName];
      if (ev && ev.steps) collectScriptsFromSteps(ev.steps, ctx);
    });
  }
}

function collectScriptsFromSteps(steps, ctx) {
  if (!Array.isArray(steps)) return;
  steps.forEach(step => {
    if (!step) return;
    const params = step.params || {};
    if (step.type === 'uicore:runScript' && params.script) {
      const logic = extractScriptLogic(params.script);
      ctx.scripts.push({
        script: params.script,
        note: params.note || '',
        length: params.script.length,
        ...logic,
      });
    }
    // 递归 branches / elseStep / steps
    if (params.branches) {
      params.branches.forEach(b => {
        if (b.steps) collectScriptsFromSteps(b.steps, ctx);
      });
    }
    if (params.elseStep && params.elseStep.steps) {
      collectScriptsFromSteps(params.elseStep.steps, ctx);
    }
    if (params.steps) collectScriptsFromSteps(params.steps, ctx);
  });
}

function parsePageFile(filePath) {
  const raw = fs.readFileSync(filePath, 'utf-8');
  let page;
  try {
    page = JSON.parse(raw);
  } catch (e) {
    return { error: `JSON parse failed: ${e.message}` };
  }

  const pageId = page.pageId || '';
  const pageName = page.name || '';
  const result = {
    pageId,
    pageName,
    path: page.path || '',
    pageTag: page.pageTag || '',
    fields: [],
    buttons: [],
    detailColumns: [],
    statusArrays: [],
    scripts: [],
    summary: {},
  };

  const ctx = result;
  const configs = page.pageConfig;
  if (Array.isArray(configs)) {
    configs.forEach(cfg => {
      if (cfg.layout) walkLayout(cfg.layout, ctx);
    });
  } else if (configs && configs.layout) {
    walkLayout(configs.layout, ctx);
  }

  // 汇总
  result.summary = {
    fieldCount: result.fields.length,
    buttonCount: result.buttons.length,
    detailColumnCount: result.detailColumns.length,
    statusArrayCount: result.statusArrays.length,
    scriptCount: result.scripts.length,
    formulaCount: result.scripts.reduce((s, x) => s + x.formulas.length, 0),
    workflowCallCount: result.scripts.reduce((s, x) => s + x.workflowCalls.length, 0),
    apiCallCount: result.scripts.reduce((s, x) => s + x.apiCalls.length, 0),
    turnOperationCount: result.scripts.reduce((s, x) => s + x.turnOperations.length, 0),
  };

  return result;
}

// 处理所有页面
const files = fs.readdirSync(PAGES_DIR).filter(f => f.endsWith('.json'));
console.log(`共 ${files.length} 个页面待解析`);

const allSummary = [];
let okCount = 0, failCount = 0;
files.forEach(file => {
  try {
    const filePath = path.join(PAGES_DIR, file);
    const parsed = parsePageFile(filePath);
    fs.writeFileSync(path.join(OUT_DIR, file), JSON.stringify(parsed, null, 2), 'utf-8');
    allSummary.push({
      pageId: parsed.pageId,
      pageName: parsed.pageName,
      ...parsed.summary,
    });
    okCount++;
    if (parsed.summary.fieldCount > 0 || parsed.summary.buttonCount > 0) {
      console.log(`✓ ${file}: ${parsed.summary.fieldCount} 字段, ${parsed.summary.buttonCount} 按钮, ${parsed.summary.formulaCount} 公式`);
    }
  } catch (e) {
    failCount++;
    console.error(`✗ ${file}: ${e.message}`);
  }
});

console.log(`\n完成：成功 ${okCount}，失败 ${failCount}`);
fs.writeFileSync(path.join(OUT_DIR, '_summary.json'), JSON.stringify(allSummary, null, 2), 'utf-8');
console.log(`汇总已保存到 ${path.join(OUT_DIR, '_summary.json')}`);
