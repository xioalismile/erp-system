// 诊断 + 重置所有用户密码
const { chromium } = require('playwright');
const http = require('http');

async function apiCall(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const opts = {
      method, host: 'localhost', port: 8080, path,
      headers: { 'Content-Type': 'application/json' }
    };
    if (data) opts.headers['Content-Length'] = Buffer.byteLength(data);
    if (token) opts.headers['Authorization'] = 'Bearer ' + token;
    const req = http.request(opts, r => {
      let s = '';
      r.on('data', d => s += d);
      r.on('end', () => resolve({ status: r.statusCode, body: s }));
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

(async () => {
  // 1. 登录拿 admin token
  const login = await apiCall('POST', '/api/auth/login', { username: 'admin', password: 'admin123' });
  const lr = JSON.parse(login.body);
  const token = lr.data?.token;
  console.log('admin token:', token?.substring(0, 30) + '...');

  // 2. 用 admin token 调 resetPwd
  const users = ['manager', 'sale01', 'purchase01', 'warehouse01', 'production01', 'finance01'];
  for (const u of users) {
    const r = await apiCall('POST', `/api/auth/resetPwd?username=${u}&password=admin123`, null, token);
    console.log(`${u}: status=${r.status} body=${r.body.substring(0, 150)}`);
  }

  // 3. 验证 manager 登录
  const mgr = await apiCall('POST', '/api/auth/login', { username: 'manager', password: 'admin123' });
  console.log('\nmanager 登录:', mgr.body.substring(0, 200));

  // 4. 拿 manager 的 userinfo
  const mgrLogin = JSON.parse(mgr.body);
  if (mgrLogin.data?.token) {
    const info = await apiCall('GET', '/api/auth/userinfo', null, mgrLogin.data.token);
    console.log('manager userinfo:', info.body.substring(0, 500));
  }
})();
