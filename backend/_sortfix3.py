# -*- coding: utf-8 -*-
"""排序批量脚本遗留编译错误综合修复"""
import re, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

ROOT = r'D:\erp\prodsrc\智能ERP-项目\backend'
ERRFILE = ROOT + r'\_sorterr.txt'

with open(ERRFILE, 'rb') as f:
    raw = f.read()
try:
    text = raw.decode('utf-8')
except UnicodeDecodeError:
    text = raw.decode('gbk', errors='replace')

pat = re.compile(r'\[ERROR\]\s+(/D:/[^\s:]+?\.java):\[(\d+),(\d+)\]\s*(.*)')
errs = {}
for m in pat.finditer(text):
    p = m.group(1).replace('/D:', 'D:').replace('/', '\\')
    line, col = int(m.group(2)), int(m.group(3))
    msg = m.group(4)
    errs.setdefault(p, []).append((line, col, msg))

fixed, skipped = [], []

def read(p):
    with open(p, 'r', encoding='utf-8') as f:
        return f.read().split('\n')

def write(p, lines):
    with open(p, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))

COLPAT = re.compile(r'"([a-z][a-z0-9]*(?:_[a-z][a-z0-9]*)+)"')

def extract_columns(lines):
    cols = set()
    for ln in lines:
        for c in COLPAT.findall(ln):
            if len(c) <= 40:
                cols.add(c)
    return cols

for path, elist in errs.items():
    try:
        lines = read(path)
    except Exception as e:
        skipped.append((path, 'read fail: %s' % e)); continue
    changed = False
    msgs = ' | '.join(e[2] for e in elist)

    # ---- D类: 无 sortBy 参数方法里的错插 SortHelper 行 → 删除该行 ----
    # 找出每个 SortHelper 行所在方法是否声明了 sortBy（向上找方法签名）
    i = 0
    while i < len(lines):
        ln = lines[i]
        if 'SortHelper.applyToWrapper' in ln:
            # 向上找方法签名 (public/private/protected ... ( ... ) {)
            j = i - 1
            sig_has_sort = False
            depth_lines = 0
            while j >= 0 and depth_lines < 40:
                s = lines[j]
                if re.search(r'\)\s*\{', s) and re.search(r'(public|private|protected)\s', s):
                    # 方法签名行，检查本方法所有签名行是否含 sortBy（可能多行签名）
                    k = j
                    while k <= i:
                        if 'sortBy' in lines[k] and 'SortHelper' not in lines[k]:
                            sig_has_sort = True
                            break
                        k += 1
                    break
                j -= 1
                depth_lines += 1
            if not sig_has_sort:
                # 该方法没有 sortBy 参数，删除错插行
                del lines[i]
                changed = True
                continue
        i += 1

    # ---- A类: 引用 SORTABLE_COLUMNS 但未定义 → 在类声明后插入常量 ----
    uses = any('SORTABLE_COLUMNS' in ln for ln in lines)
    defined = any(re.search(r'SORTABLE_COLUMNS\s*=', ln) for ln in lines)
    if uses and not defined:
        cls_idx = None
        for idx, ln in enumerate(lines):
            if re.match(r'public\s+(final\s+)?class\s+\w+', ln):
                cls_idx = idx; break
        if cls_idx is not None:
            cols = extract_columns(lines)
            cols.add('created_at')
            collist = ', '.join('"%s"' % c for c in sorted(cols))
            insert = [
                '',
                '    /** 列头排序白名单（数据库列名，防注入） */',
                '    private static final Set<String> SORTABLE_COLUMNS = Set.of(%s);' % collist,
            ]
            # 类声明行以 { 结尾；否则找下一行 {
            at = cls_idx
            if not lines[at].rstrip().endswith('{'):
                at = cls_idx + 1
            lines[at+1:at+1] = insert
            # 补 import java.util.Set
            if not any(re.match(r'import\s+java\.util\.Set\s*;', ln) for ln in lines[:120]):
                last_imp = 0
                for idx, ln in enumerate(lines[:150]):
                    if ln.startswith('import '):
                        last_imp = idx
                lines.insert(last_imp + 1, 'import java.util.Set;')
            changed = True

    # ---- B类: Controller 方法签名缺 sortBy/sortOrder → 在签名最后参数后追加 ----
    for (eline, ecol, emsg) in list(elist):
        if 0 < eline-1 < len(lines) and 'pageQuery(' in lines[eline-1] and 'sortBy' in lines[eline-1]:
            # 向上找方法签名结尾
            j = eline - 2
            while j >= 0:
                s = lines[j]
                if re.search(r'\)\s*\{\s*$', s):
                    # 检查签名块是否已有 sortBy
                    k = j
                    sig_start = j
                    while sig_start >= 0 and not re.search(r'(public|private|protected)\s+[\w<>,\s\[\]]+\(', lines[sig_start]):
                        sig_start -= 1
                    block = '\n'.join(lines[sig_start:j+1])
                    if 'sortBy' not in block:
                        lines[j] = s.replace(') {', ',').rstrip()
                        lines[j] += '\n            @RequestParam(required = false) String sortBy,\n            @RequestParam(required = false) String sortOrder) {'
                        changed = True
                    break
                j -= 1

    if changed:
        write(path, lines)
        fixed.append(path)
    else:
        skipped.append((path, msgs[:80]))

print('=== FIXED %d files ===' % len(fixed))
for p in fixed:
    print(' OK', p.replace(ROOT, ''))
print('=== SKIPPED %d (need interface fix or manual) ===' % len(skipped))
for p, why in skipped:
    print(' ??', p.replace(ROOT, ''), '::', why)
