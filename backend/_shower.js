// 解析 maven GBK 错误日志，输出 utf-8 详情
const fs = require('fs');
const raw = fs.readFileSync('_sorterr7.txt');
// maven 输出实际是 GBK：先按 binary 读，再按 gbk 解码
let s;
try {
    s = new TextDecoder('gbk').decode(raw);
} catch (e) {
    s = raw.toString('utf-8');
}
const ls = s.split(/\r?\n/);
const out = [];
let cnt = 0;
for (let i = 0; i < ls.length && cnt < 25; i++) {
    if (ls[i].includes('[ERROR]') && ls[i].includes('.java')) {
        out.push(ls[i].substring(ls[i].indexOf('] ') + 2));
        cnt++;
        for (let k = 1; k <= 3; k++) {
            const nx = ls[i + k] || '';
            if (nx.includes('[ERROR]') || !nx.trim()) break;
            out.push('    ' + nx.trim());
        }
    }
}
console.log(out.join('\n'));
