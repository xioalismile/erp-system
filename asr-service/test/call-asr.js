/**
 * /asr 接口调用客户端（用于本机验证）
 * 用法： node test/call-asr.js [wav路径] [服务地址]
 *   默认： test/zh.wav  ->  http://127.0.0.1:6006/asr
 */
'use strict';

const fs = require('fs');
const path = require('path');
const http = require('http');

const wavPath = process.argv[2] || path.join(__dirname, 'zh.wav');
const asrUrl = process.argv[3] || 'http://127.0.0.1:6006/asr';

const u = new URL(asrUrl);
const body = fs.readFileSync(wavPath);

const req = http.request({
  hostname: u.hostname,
  port: u.port || 80,
  path: u.pathname,
  method: 'POST',
  headers: {'Content-Type': 'audio/wav', 'Content-Length': body.length},
}, (res) => {
  const chunks = [];
  res.on('data', (c) => chunks.push(c));
  res.on('end', () => {
    const text = Buffer.concat(chunks).toString('utf8');
    console.log(`HTTP ${res.statusCode}`);
    console.log(text);
  });
});

req.on('error', (e) => {
  console.log(`请求失败：${e.message}`);
  process.exit(1);
});

console.log(`上传：${wavPath}（${body.length} 字节） -> ${asrUrl}`);
req.end(body);
