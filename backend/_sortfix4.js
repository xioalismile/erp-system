// 修复：SORTABLE_COLUMNS 常量插到多行类声明中间 → 移到类体 { 之后
const fs = require('fs');
const path = require('path');
const ROOT = 'D:/erp/prodsrc/智能ERP-项目/backend/src/main/java';

function collect(dir, acc) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, e.name);
        if (e.isDirectory()) collect(full, acc);
        else if (e.name.endsWith('.java')) acc.push(full);
    }
    return acc;
}
const files = collect(ROOT, []);
let fixedCount = 0;

for (const f of files) {
    let lines = fs.readFileSync(f, 'utf-8').split('\n');
    let changed = false;
    for (let i = 0; i < lines.length; i++) {
        if (/private static final Set<String> SORTABLE_COLUMNS\s*=/.test(lines[i])) {
            let clsStart = -1;
            for (let j = i; j >= Math.max(0, i - 15); j--) {
                if (/^public\s+(final\s+)?class\s+\w+/.test(lines[j])) { clsStart = j; break; }
            }
            if (clsStart < 0) continue;
            let braceLine = -1;
            for (let j = clsStart; j <= i; j++) {
                if (/\{\s*$/.test(lines[j])) { braceLine = j; break; }
            }
            if (braceLine < 0) {
                // 错位：类声明 { 在常量行之后 → 移动常量块
                let start = i;
                if (i - 1 >= 0 && /列头排序白名单/.test(lines[i - 1])) start = i - 1;
                if (start - 1 >= 0 && lines[start - 1].trim() === '') start = start - 1;
                const block = lines.slice(start, i + 1);
                lines.splice(start, block.length);
                let brace = -1;
                for (let j = clsStart; j < clsStart + 10 && j < lines.length; j++) {
                    if (/\{\s*$/.test(lines[j])) { brace = j; break; }
                }
                if (brace >= 0) {
                    lines.splice(brace + 1, 0, ...block);
                    changed = true;
                } else {
                    lines.splice(start, 0, ...block);
                }
            }
        }
    }
    if (changed) {
        fs.writeFileSync(f, lines.join('\n'));
        fixedCount++;
        console.log('FIXED', f.replace(ROOT, ''));
    }
}
console.log('total fixed:', fixedCount);
