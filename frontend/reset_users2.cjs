// 用 admin token 列出所有用户 ID，然后逐个 reset password
const http = require('http');
async function api(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const opts = { method, host: 'localhost', port: 8080, path, headers: { 'Content-Type': 'application/json' } };
    if (data) opts.headers['Content-Length'] = Buffer.byteLength(data);
    if (token) opts.headers['Authorization'] = 'Bearer ' + token;
    const req = http.request(opts, r => { let s = ''; r.on('data', d => s += d); r.on('end', () => resolve({ status: r.status, body: s })); });
    req.on('error', reject); if (data) req.write(data); req.end();
  });
}

(async () => {
  const login = await api('POST', '/api/auth/login', { username: 'admin', password: 'admin123' });
  const token = JSON.parse(login.body).data.token;
  console.log('admin token:', token.substring(0, 30));

  // 列出所有用户
  const page = await api('GET', '/api/system/user/page?size=100', null, token);
  const users = JSON.parse(page.body).data.records;
  console.log('用户数:', users.length);
  for (const u of users) {
    console.log(`  ${u.userId}: ${u.username} enabled=${u.isEnabled ?? '?'} status=${u.status ?? '?'}`);
  }

  // 重置 manager 等的密码
  const targets = ['manager', 'sale01', 'purchase01', 'warehouse01', 'production01', 'finance01'];
  for (const t of targets) {
    const u = users.find(x => x.username === t);
    if (!u) { console.log(`✗ ${t} not found`); continue; }
    const r = await api('POST', `/api/system/user/${u.userId}/reset-password`, null, token);
    console.log(`reset ${t} (id=${u.userId}): status=${r.status} body=${r.body.substring(0, 100)}`);
  }
})();
