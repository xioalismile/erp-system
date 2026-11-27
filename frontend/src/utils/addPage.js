/**
 * 新增页路径判定（路由守卫、主布局脏标记、请求成功清标记共用一套口径）
 *
 * 新增页有两种地址写法：
 *   /xxx/new           基础资料等（如 /basedata/material/new）
 *   /xxx/edit          库存、生产等单据（无单号的 /edit 即新增，带单号的 /edit/:id 才是编辑）
 * 只按末尾片段判断，编辑页 /xxx/edit/:id 不受影响。
 */
export function isAddPagePath(path) {
  if (typeof path !== 'string' || !path) return false
  // 入参可能带查询串（如 /xxx/edit?copy=3），判定前先去掉
  const clean = path.split('?')[0].split('#')[0].replace(/\/+$/, '')
  return clean.endsWith('/new') || clean.endsWith('/edit')
}
