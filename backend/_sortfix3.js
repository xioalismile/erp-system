// 排序批量脚本遗留编译错误综合修复 (Node 版)
const fs = require('fs');
const path = require('path');

const ROOT = 'D:/erp/prodsrc/智能ERP-项目/backend';
const ERRFILE = path.join(ROOT, '_sorterr5.txt');

const raw = fs.readFileSync(ERRFILE);
let text;
try { text = raw.toString('utf-8'); } catch (e) { text = raw.toString('latin1'); }
// maven 输出为 GBK，文件路径的中文会乱，但我们只按结构解析，随后用目录扫描纠正
const pat = /\[ERROR\]\s+(\S+?\.java):\[(\d+),(\d+)\]\s*(.*)/g;
const relErrs = {}; // 相对路径片段 -> errors
let m;
while ((m = pat.exec(text)) !== null) {
    const p = m[1].replace(/^\/+/, '').replace(/\//g, '\\');
    relErrs[p] = relErrs[p] || [];
    relErrs[p].push({ line: +m[2], col: +m[3], msg: m[4] });
}

// 错误输出里的中文路径乱码（GBK），改按 模块/文件名 重新映射真实文件
function collectJavaFiles(dir, acc) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, e.name);
        if (e.isDirectory()) collectJavaFiles(full, acc);
        else if (e.name.endsWith('.java')) acc.push(full);
    }
    return acc;
}
const allFiles = collectJavaFiles(path.join(ROOT, 'src/main/java'), []);

// 建立匹配：错误路径以 "backend/src/main/java/com/erp/..." 的 ASCII 部分匹配
// GBK 乱码只影响 "智能ERP-项目" 目录名，com/erp 之后的路径是 ASCII
const errTargets = [];
for (const rel of Object.keys(relErrs)) {
    const idx = rel.indexOf('com\\erp\\');
    if (idx < 0) { console.log('SKIP(unmapped)', rel); continue; }
    const tail = rel.slice(idx).replace(/\\/g, '/');
    const real = allFiles.find(f => f.replace(/\\/g, '/').endsWith(tail));
    if (!real) { console.log('SKIP(nofile)', tail); continue; }
    errTargets.push({ real, errs: relErrs[rel] });
}

const fixed = [], skipped = [];
const COLPAT = /"([a-z][a-z0-9]*(?:_[a-z][a-z0-9]*)+)"/g;

for (const { real, errs } of errTargets) {
    let lines;
    try { lines = fs.readFileSync(real, 'utf-8').split('\n'); }
    catch (e) { skipped.push([real, 'read fail']); continue; }
    let changed = false;

    // D类: 无 sortBy 参数方法里的错插 SortHelper 行 → 删除
    for (let i = 0; i < lines.length; i++) {
        if (lines[i].includes('SortHelper.applyToWrapper')) {
            let j = i - 1, sigHasSort = false, depth = 0;
            while (j >= 0 && depth < 40) {
                const s = lines[j];
                if (/\)\s*\{/.test(s) && /(public|private|protected)\s/.test(s)) {
                    for (let k = j; k <= i; k++) {
                        if (lines[k].includes('sortBy') && !lines[k].includes('SortHelper')) { sigHasSort = true; break; }
                    }
                    break;
                }
                j--; depth++;
            }
            if (!sigHasSort) { lines.splice(i, 1); changed = true; i--; continue; }
        }
    }

    // A类: 引用 SORTABLE_COLUMNS 未定义 → 类声明后插入常量
    const uses = lines.some(l => l.includes('SORTABLE_COLUMNS'));
    const defined = lines.some(l => /SORTABLE_COLUMNS\s*=/.test(l));
    if (uses && !defined) {
        let clsIdx = -1;
        for (let i = 0; i < lines.length; i++) {
            if (/^public\s+(final\s+)?class\s+\w+/.test(lines[i])) { clsIdx = i; break; }
        }
        if (clsIdx >= 0) {
            const cols = new Set(['created_at']);
            for (const ln of lines) {
                let c; COLPAT.lastIndex = 0;
                while ((c = COLPAT.exec(ln)) !== null) if (c[1].length <= 40) cols.add(c[1]);
            }
            const collist = [...cols].sort().map(c => `"${c}"`).join(', ');
            let at = clsIdx;
            if (!lines[at].trimEnd().endsWith('{')) at = clsIdx + 1;
            lines.splice(at + 1, 0,
                '',
                '    /** 列头排序白名单（数据库列名，防注入） */',
                `    private static final Set<String> SORTABLE_COLUMNS = Set.of(${collist});`);
            // 补 import java.util.Set
            if (!lines.slice(0, 150).some(l => /^import\s+java\.util\.Set\s*;/.test(l))) {
                let lastImp = 0;
                for (let i = 0; i < Math.min(150, lines.length); i++) if (lines[i].startsWith('import ')) lastImp = i;
                lines.splice(lastImp + 1, 0, 'import java.util.Set;');
            }
            changed = true;
        }
    }

    // B类: Controller 方法签名缺 sortBy/sortOrder → 签名最后参数后追加
    for (const e of errs) {
        const li = e.line - 1;
        if (li >= 0 && li < lines.length && lines[li].includes('pageQuery(') && lines[li].includes('sortBy')) {
            for (let j = li - 1; j >= 0 && j > li - 30; j--) {
                const s = lines[j];
                if (/\)\s*\{\s*$/.test(s)) {
                    let sigStart = j;
                    while (sigStart >= 0 && !/(public|private|protected)\s+[\w<>,\s\[\]]+\(/.test(lines[sigStart])) sigStart--;
                    const block = lines.slice(sigStart, j + 1).join('\n');
                    if (!block.includes('sortBy')) {
                        lines[j] = s.replace(/\)\s*\{\s*$/, ',') +
                            '\n            @RequestParam(required = false) String sortBy,' +
                            '\n            @RequestParam(required = false) String sortOrder) {';
                        changed = true;
                    }
                    break;
                }
            }
        }
    }

    if (changed) { fs.writeFileSync(real, lines.join('\n')); fixed.push(real); }
    else skipped.push([real, errs.map(e => e.msg).join(' | ').slice(0, 80)]);
}

console.log(`=== FIXED ${fixed.length} files ===`);
fixed.forEach(p => console.log(' OK', p.replace(ROOT, '')));
console.log(`=== SKIPPED ${skipped.length} (interface fix or manual) ===`);
skipped.forEach(([p, w]) => console.log(' ??', p.replace(ROOT, ''), '::', w));
