// 给非 admin 角色批量分配所有菜单权限
const http = require('http');
function api(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const opts = { method, host: 'localhost', port: 8080, path, headers: { 'Content-Type': 'application/json' } };
    if (data) opts.headers['Content-Length'] = Buffer.byteLength(data);
    if (token) opts.headers['Authorization'] = 'Bearer ' + token;
    const req = http.request(opts, r => { let s = ''; r.on('data', d => s += d); r.on('end', () => resolve(s)); });
    req.on('error', reject); if (data) req.write(data); req.end();
  });
}

(async () => {
  const login = await api('POST', '/api/auth/login', { username: 'admin', password: 'admin123' });
  const token = JSON.parse(login).data.token;

  // 拿所有角色
  const roles = JSON.parse(await api('GET', '/api/system/role/list', null, token)).data;
  console.log('角色数:', roles.length);
  for (const r of roles) console.log(`  ${r.roleId}: ${r.roleName || r.roleCode} (admin_flag=${r.isAdmin || '?'})`);

  // 拿所有菜单
  const menus = JSON.parse(await api('GET', '/api/system/menu/list', null, token)).data;
  const menuIds = menus.map(m => m.menuId);
  console.log('菜单数:', menuIds.length);

  // 给每个非 admin 角色分配全部菜单
  for (const r of roles) {
    if (r.isAdmin === 1 || r.isAdmin === true) continue;
    const r2 = await api('POST', `/api/system/role/${r.roleId}/permissions`, menuIds, token);
    console.log(`  角色 ${r.roleName || r.roleId} 分配 ${menuIds.length} 菜单: ${r2.substring(0, 80)}`);
  }
})();
