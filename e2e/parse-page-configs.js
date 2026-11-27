/**
 * 云端ERP pageConfig 解析器
 * 从每个页面的pageConfig中提取：
 *  - 字段列表（label, prop, type, required）
 *  - 按钮列表（text, visible, disabled条件）
 *  - 计算公式脚本（含 amount/price/tax/discount 关键字的脚本）
 *  - 字段联动逻辑（onChange/onSelect脚本）
 *  - 状态判断（status相关条件）
 *  - 下推关系（turn/convert/source_type）
 * 输出：每个页面一个简化JSON + 一个汇总markdown
 */
const fs = require('fs');
const path = require('path');

const PAGES_DIR = path.resolve(__dirname, 'cloud-erp-exploration', 'pages');
const OUT_DIR = path.resolve(__dirname, 'cloud-erp-exploration', 'parsed');
if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });

// 递归遍历对象，收集所有字符串
function collectStrings(obj, prefix = '', out = []) {
  if (typeof obj === 'string') {
    out.push({ path: prefix, value: obj });
  } else if (Array.isArray(obj)) {
    obj.forEach((v, i) => collectStrings(v, `${prefix}[${i}]`, out));
  } else if (obj && typeof obj === 'object') {
    for (const k of Object.keys(obj)) {
      collectStrings(obj[k], prefix ? `${prefix}.${k}` : k, out);
    }
  }
  return out;
}

// 提取脚本中的关键信息
function extractScriptInfo(script) {
  if (typeof script !== 'string' || script.length < 5) return null;
  const info = {
    length: script.length,
    // 计算公式相关
    formulas: [],
    // 字段联动
    fieldLinks: [],
    // 状态判断
    statusChecks: [],
    // 按钮可见性
    buttonVisibles: [],
    // 下推关系
    turnOperations: [],
    // API调用
    apiCalls: []
  };

  // 提取计算公式：包含 =、+、-、*、/ 等运算的赋值语句
  const formulaPatterns = [
    /(\w+\.amount[_\w]*)\s*=\s*([^;\n]{5,150})/g,
    /(\w+\.price[_\w]*)\s*=\s*([^;\n]{5,150})/g,
    /(\w+\.tax[_\w]*)\s*=\s*([^;\n]{5,150})/g,
    /(\w+\.discount[_\w]*)\s*=\s*([^;\n]{5,150})/g,
    /(\w+\.quantity[_\w]*)\s*=\s*([^;\n]{5,150})/g,
    /(\w+\.net_amount[_\w]*)\s*=\s*([^;\n]{5,150})/g,
    /(\w+\.total[_\w]*)\s*=\s*([^;\n]{5,150})/g,
    /(\w+\.base_amount[_\w]*)\s*=\s*([^;\n]{5,150})/g,
    /state\.(\w+)\s*=\s*([^;\n]{5,150}\*[^;\n]{0,100})/g,
    /item\.(\w+)\s*=\s*([^;\n]*?\*[^;\n]*?)[;\n]/g
  ];
  for (const p of formulaPatterns) {
    let m;
    while ((m = p.exec(script)) !== null) {
      const f = `${m[1]} = ${m[2].trim()}`;
      if (!info.formulas.includes(f) && f.length < 200) info.formulas.push(f);
      if (info.formulas.length > 30) break;
    }
  }

  // 提取字段联动：包含"state.xxx = data"或"item.xxx = row.xxx"模式
  const linkPatterns = [
    /state\.(\w+)\s*=\s*(?:data|row|item|record|res|response)\.(\w+)/g,
    /item\.(\w+)\s*=\s*(?:data|row|record|res)\.(\w+)/g
  ];
  for (const p of linkPatterns) {
    let m;
    while ((m = p.exec(script)) !== null) {
      const l = `${m[1]} <- ${m[2]}`;
      if (!info.fieldLinks.includes(l)) info.fieldLinks.push(l);
      if (info.fieldLinks.length > 30) break;
    }
  }

  // 提取状态判断
  const statusPatterns = [
    /(?:status|flow_status|approval_status|approve_status)\s*[=!]==?\s*['"]([^'"]+)['"]/g,
    /['"](草稿|已提交|待审核|已审核|已审批|已确认|已完成|已关闭|已取消|已结案|draft|pending|approved|rejected|closed|cancelled|finished)['"\]]/g
  ];
  for (const p of statusPatterns) {
    let m;
    while ((m = p.exec(script)) !== null) {
      if (!info.statusChecks.includes(m[1])) info.statusChecks.push(m[1]);
      if (info.statusChecks.length > 20) break;
    }
  }

  // 提取按钮可见性条件
  const visiblePatterns = [
    /(\w+)\.visible\s*=\s*([^;\n]{5,150})/g,
    /v-if="([^"]{5,150})"/g,
    /visible[:\s]+([^;\n,]{5,100})/g
  ];

  // 提取下推/转入关系
  const turnPatterns = [
    /turn_to[_\w]*\(['"]([^'"]+)['"]\)/g,
    /source_type[:\s]+['"]([^'"]+)['"]/g,
    /turn_config\.(\w+)/g,
    /source_(\w+)_id/g
  ];
  for (const p of turnPatterns) {
    let m;
    while ((m = p.exec(script)) !== null) {
      if (!info.turnOperations.includes(m[1])) info.turnOperations.push(m[1]);
    }
  }

  // 提取API调用
  const apiPatterns = [
    /(?:fetch|axios|request|http)\.(\w+)\(['"]([^'"]+)['"]/g,
    /\$app\.(\w+)\(['"]([^'"]+)['"]/g,
    /url[:\s]+['"]([^'"]+)['"]/g
  ];
  for (const p of apiPatterns) {
    let m;
    while ((m = p.exec(script)) !== null) {
      const url = m[2] || m[1];
      if (url.includes('/') || url.includes('api')) {
        if (!info.apiCalls.includes(url)) info.apiCalls.push(url);
      }
      if (info.apiCalls.length > 20) break;
    }
  }

  // 只返回有内容的字段
  const hasContent = info.formulas.length > 0 || info.fieldLinks.length > 0 ||
                     info.statusChecks.length > 0 || info.turnOperations.length > 0 ||
                     info.apiCalls.length > 0;
  return hasContent ? info : null;
}

// 提取页面字段配置
function extractFields(config) {
  const fields = [];
  const strings = collectStrings(config);

  // 找 "label": "xxx" 后面跟着 "prop"/"field"/"name"
  for (let i = 0; i < strings.length; i++) {
    const s = strings[i];
    if (s.path.endsWith('.label') && typeof s.value === 'string' && s.value.length < 30) {
      // 提取上下文
      const ctx = s.path.match(/^(.*)\.label$/);
      if (ctx) {
        const basePath = ctx[1];
        // 查找同 basePath 下的 prop/field
        const propMatch = strings.find(x => x.path === `${basePath}.prop` || x.path === `${basePath}.field` || x.path === `${basePath}.name`);
        if (propMatch) {
          const required = strings.find(x => x.path === `${basePath}.required`);
          const type = strings.find(x => x.path === `${basePath}.type`);
          fields.push({
            label: s.value,
            prop: propMatch.value,
            required: required?.value === true,
            type: type?.value
          });
        }
      }
    }
  }
  return fields;
}

// 提取按钮配置
function extractButtons(config) {
  const buttons = [];
  // 找 "text": "xxx" 或 "name": "xxx" 附近有 click 事件的
  const strings = collectStrings(config);
  for (let i = 0; i < strings.length; i++) {
    const s = strings[i];
    if ((s.path.endsWith('.text') || s.path.endsWith('.name')) &&
        typeof s.value === 'string' && s.value.length >= 2 && s.value.length < 15) {
      const ctx = s.path.match(/^(.*)\.(text|name)$/);
      if (ctx) {
        const basePath = ctx[1];
        // 查找 click 事件
        const hasClick = strings.find(x => x.path.startsWith(`${basePath}.events`) ||
                                            x.path === `${basePath}.onClick`);
        if (hasClick) {
          buttons.push({ text: s.value });
        }
      }
    }
  }
  return buttons;
}

// 解析单个页面
function parsePage(page) {
  const pageId = page.pageId;
  const pageName = page.name;
  const config = page.pageConfig;
  if (!Array.isArray(config)) return null;

  const result = {
    pageId,
    pageName,
    path: page.path,
    pageTag: page.pageTag,
    fields: [],
    buttons: [],
    scripts: [],
    totalConfigSize: JSON.stringify(config).length
  };

  // 提取所有脚本字符串
  const allStrings = collectStrings(config);
  const scripts = allStrings.filter(s => typeof s.value === 'string' && s.value.length > 50);

  // 提取字段
  result.fields = extractFields(config);

  // 提取按钮
  result.buttons = extractButtons(config);

  // 提取脚本中的关键信息
  for (const s of scripts) {
    const info = extractScriptInfo(s.value);
    if (info) {
      result.scripts.push({
        path: s.path,
        scriptLength: s.value.length,
        scriptPreview: s.value.slice(0, 300),
        ...info
      });
    }
  }

  // 合并所有脚本的关键信息
  const allFormulas = new Set();
  const allFieldLinks = new Set();
  const allStatusChecks = new Set();
  const allTurnOps = new Set();
  const allApiCalls = new Set();

  for (const s of result.scripts) {
    s.formulas?.forEach(f => allFormulas.add(f));
    s.fieldLinks?.forEach(f => allFieldLinks.add(f));
    s.statusChecks?.forEach(f => allStatusChecks.add(f));
    s.turnOperations?.forEach(f => allTurnOps.add(f));
    s.apiCalls?.forEach(f => allApiCalls.add(f));
  }

  result.summary = {
    formulas: Array.from(allFormulas).slice(0, 50),
    fieldLinks: Array.from(allFieldLinks).slice(0, 50),
    statusChecks: Array.from(allStatusChecks),
    turnOperations: Array.from(allTurnOps),
    apiCalls: Array.from(allApiCalls).slice(0, 30)
  };

  return result;
}

// 主函数
function main() {
  const files = fs.readdirSync(PAGES_DIR).filter(f => f.endsWith('.json'));
  console.log(`解析 ${files.length} 个页面文件`);

  const allResults = [];
  for (const file of files) {
    try {
      const page = JSON.parse(fs.readFileSync(path.join(PAGES_DIR, file), 'utf8'));
      const result = parsePage(page);
      if (result) {
        allResults.push(result);
        // 保存每个页面的解析结果
        fs.writeFileSync(
          path.join(OUT_DIR, file),
          JSON.stringify(result, null, 2)
        );
        console.log(`✓ ${result.pageId}: ${result.fields.length}字段, ${result.buttons.length}按钮, ${result.scripts.length}脚本, ${result.summary.formulas.length}公式`);
      }
    } catch (e) {
      console.log(`✗ ${file}: ${e.message}`);
    }
  }

  // 保存汇总
  const summary = allResults.map(r => ({
    pageId: r.pageId,
    pageName: r.pageName,
    path: r.path,
    pageTag: r.pageTag,
    fieldCount: r.fields.length,
    buttonCount: r.buttons.length,
    scriptCount: r.scripts.length,
    formulaCount: r.summary.formulas.length,
    statusChecks: r.summary.statusChecks,
    turnOperations: r.summary.turnOperations,
    formulas: r.summary.formulas.slice(0, 20),
    fieldLinks: r.summary.fieldLinks.slice(0, 20),
    apiCalls: r.summary.apiCalls
  }));
  fs.writeFileSync(
    path.resolve(__dirname, 'cloud-erp-exploration', '14_all_pages_summary.json'),
    JSON.stringify(summary, null, 2)
  );

  // 输出按模块分组的概览
  console.log('\n=== 模块概览 ===');
  const modules = {};
  for (const r of allResults) {
    const mod = r.pageId.split('_')[0];
    if (!modules[mod]) modules[mod] = [];
    modules[mod].push(r.pageId);
  }
  for (const [mod, pages] of Object.entries(modules)) {
    console.log(`${mod}: ${pages.length}页`);
  }

  console.log(`\n=== 完成！已解析 ${allResults.length} 个页面 ===`);
  console.log(`输出目录: ${OUT_DIR}`);
}

main();
