// 重置所有用户密码并查看状态
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

  const page = await api('GET', '/api/system/user/page?size=100', null, token);
  const users = JSON.parse(page.body).data.records;
  console.log('所有用户：');
  for (const u of users) {
    console.log(`  ${u.userId}: ${u.username} enabled=${u.isEnabled} status=${u.status}`);
    // 重置密码
    const r = await api('POST', `/api/system/user/${u.userId}/reset-password`, null, token);
    console.log(`    reset: ${r.status}`);
  }

  // 验证 5 个用户都能登录
  for (const username of ['admin', 'manager01', 'sales01', 'finance01', 'e2euser']) {
    const r = await api('POST', '/api/auth/login', { username, password: 'admin123' });
    const j = JSON.parse(r.body);
    console.log(`${username} login: ${j.code === 200 ? 'OK' : 'FAIL: ' + j.message}`);
  }
})();
