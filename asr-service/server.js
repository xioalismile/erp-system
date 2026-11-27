/**
 * 本机常驻中文语音识别（ASR）服务 —— 原型
 *
 * 设计要点：
 *  1. 只监听 127.0.0.1:6006（回环地址，不对外开端口）；
 *  2. 启动时一次性加载模型，之后所有请求复用同一个识别器；
 *  3. GET  /health → 服务与模型状态；
 *     POST /asr    → 请求体 = 原始 WAV 字节（Content-Type: audio/wav），返回识别文本；
 *  4. 所有失败路径都返回明确中文错误，绝不静默、不返回无 body 的 500；
 *  5. 除 sherpa-onnx-node 外不引入任何第三方依赖。
 *
 * 启动： node server.js
 * 环境变量（可选）：ASR_PORT / ASR_HOST / ASR_MODEL_DIR / ASR_THREADS
 */

'use strict';

const http = require('http');
const path = require('path');
const fs = require('fs');

const sherpa = require('sherpa-onnx-node');
// 底层原生扩展：readWaveFromBinary 可直接从内存字节解析 WAV，避免音频落盘
const addon = require('sherpa-onnx-node/addon.js');

// ---------------- 配置 ----------------
const HOST = process.env.ASR_HOST || '127.0.0.1';
const PORT = Number(process.env.ASR_PORT || 6006);
const MODEL_NAME = 'sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17';
const MODEL_DIR =
    process.env.ASR_MODEL_DIR || path.join(__dirname, 'models', MODEL_NAME);
const MODEL_FILE = path.join(MODEL_DIR, 'model.int8.onnx');
const TOKENS_FILE = path.join(MODEL_DIR, 'tokens.txt');
const NUM_THREADS = Number(process.env.ASR_THREADS || 2);
const TARGET_SAMPLE_RATE = 16000;           // 识别引擎要求的采样率
const MAX_BODY_BYTES = 20 * 1024 * 1024;    // 请求体上限 20MB，防内存被打爆

// ---------------- 模型加载（进程内只做一次） ----------------
let recognizer = null;
let loadedMs = 0;

function loadModel() {
  if (!fs.existsSync(MODEL_FILE)) {
    throw new Error(`模型文件不存在：${MODEL_FILE}`);
  }
  if (!fs.existsSync(TOKENS_FILE)) {
    throw new Error(`词表文件不存在：${TOKENS_FILE}`);
  }

  const t0 = Date.now();
  recognizer = new sherpa.OfflineRecognizer({
    featConfig: {sampleRate: TARGET_SAMPLE_RATE, featureDim: 80},
    modelConfig: {
      senseVoice: {
        model: MODEL_FILE,
        language: 'zh',                  // 中文
        useInverseTextNormalization: 1,   // 打开逆文本规整（数字/日期更自然）
      },
      tokens: TOKENS_FILE,
      numThreads: NUM_THREADS,
      provider: 'cpu',
      debug: 0,
    },
  });
  loadedMs = Date.now() - t0;
}

// ---------------- WAV -> 文本 ----------------
/**
 * 从原始 WAV 字节识别文本。
 * @param {Buffer} wavBytes
 * @returns {string} 识别文本
 */
function recognize(wavBytes) {
  let wave;
  try {
    wave = addon.readWaveFromBinary(new Uint8Array(wavBytes));
  } catch (e) {
    throw new Error(
        `音频解析失败（仅支持标准 WAV/PCM 文件）：${e && e.message ? e.message : e}`);
  }

  let samples = wave.samples;
  let sampleRate = wave.sampleRate;
  if (!samples || samples.length === 0) {
    throw new Error('音频内容为空（没有可识别的采样点）');
  }

  // 采样率不是 16k 时做线性重采样（例如 8k/44.1k 的录音）
  if (sampleRate !== TARGET_SAMPLE_RATE) {
    const resampler =
        new sherpa.LinearResampler(sampleRate, TARGET_SAMPLE_RATE);
    samples = resampler.flush(samples);
    sampleRate = TARGET_SAMPLE_RATE;
  }

  const stream = recognizer.createStream();
  stream.acceptWaveform({samples, sampleRate});
  recognizer.decode(stream);
  const result = recognizer.getResult(stream);
  return (result && result.text ? result.text : '').trim();
}

// ---------------- HTTP 工具 ----------------
function sendJson(res, status, obj) {
  const body = Buffer.from(JSON.stringify(obj), 'utf8');
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': body.length,
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY_BYTES) {
        reject(new Error('音频体积超过上限（20MB），请缩短录音后重试'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', (e) => reject(new Error(`读取请求体失败：${e.message}`)));
  });
}

// ---------------- 请求处理 ----------------
async function handle(req, res) {
  const url = (req.url || '').split('?')[0];

  // 健康检查
  if (req.method === 'GET' && (url === '/health' || url === '/')) {
    sendJson(res, 200, {ok: true, model: MODEL_NAME, loadedMs});
    return;
  }

  // 语音识别
  if (req.method === 'POST' && url === '/asr') {
    const t0 = Date.now();
    let body;
    try {
      body = await readBody(req);
    } catch (e) {
      sendJson(res, 400, {error: e.message, text: ''});
      return;
    }

    if (!body || body.length === 0) {
      sendJson(res, 400, {
        error: '请求体为空：请把 WAV 音频字节直接放在请求体里（Content-Type: audio/wav）',
        text: '',
      });
      return;
    }
    if (body.length < 44 || body.slice(0, 4).toString('ascii') !== 'RIFF' ||
        body.slice(8, 12).toString('ascii') !== 'WAVE') {
      sendJson(res, 400, {
        error: `音频格式不支持：只接受标准 WAV 文件（当前 ${body.length} 字节，缺少 RIFF/WAVE 头）`,
        text: '',
      });
      return;
    }

    try {
      const text = recognize(body);
      if (!text) {
        sendJson(res, 200, {
          text: '',
          ms: Date.now() - t0,
          hint: '没有识别出文字（可能是静音或音量过小）',
        });
        return;
      }
      sendJson(res, 200, {text, ms: Date.now() - t0});
    } catch (e) {
      // 识别异常：返回明确中文错误，服务不退出
      console.error(`[ASR] 识别失败：${e && e.stack ? e.stack : e}`);
      sendJson(res, 500, {
        error: `识别失败：${e && e.message ? e.message : e}`,
        text: '',
        ms: Date.now() - t0,
      });
    }
    return;
  }

  // 其它路径
  sendJson(res, 404, {
    error: `接口不存在：${req.method} ${url}（可用：GET /health、POST /asr）`,
  });
}

// ---------------- 启动 ----------------
function main() {
  console.log(`[ASR] 正在加载模型：${MODEL_NAME}`);
  console.log(`[ASR] 模型路径：${MODEL_DIR}`);
  try {
    loadModel();
  } catch (e) {
    console.error(`[ASR] 模型加载失败，服务无法启动：${e.message}`);
    process.exit(1);
  }
  console.log(`[ASR] 模型加载完成，耗时 ${loadedMs} ms`);

  const server = http.createServer((req, res) => {
    handle(req, res).catch((e) => {
      // 兜底：任何未预期的异常都不能让进程挂掉
      console.error(`[ASR] 请求处理异常：${e && e.stack ? e.stack : e}`);
      if (!res.headersSent) {
        sendJson(res, 500, {
          error: `服务内部错误：${e && e.message ? e.message : e}`,
        });
      } else {
        res.end();
      }
    });
  });

  // 客户端连接异常（如半开连接）不让进程崩
  server.on('clientError', (err, socket) => {
    if (socket.writable) {
      socket.end('HTTP/1.1 400 Bad Request\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n请求格式错误');
    }
  });
  server.on('error', (e) => {
    if (e && e.code === 'EADDRINUSE') {
      console.error(`[ASR] 端口被占用，无法启动：${HOST}:${PORT}`);
    } else {
      console.error(`[ASR] 服务错误：${e && e.message ? e.message : e}`);
    }
    process.exit(1);
  });

  server.listen(PORT, HOST, () => {
    console.log(`[ASR] 服务已启动，监听地址：http://${HOST}:${PORT}`);
    console.log(`[ASR] 接口：GET /health（健康检查）、POST /asr（WAV 字节 -> 文本）`);
  });

  // 进程级兜底：单次请求出错不影响后续请求
  process.on('uncaughtException', (e) => {
    console.error(`[ASR] 未捕获异常（进程继续运行）：${e && e.stack ? e.stack : e}`);
  });
  process.on('unhandledRejection', (e) => {
    console.error(`[ASR] 未处理的 Promise 拒绝（进程继续运行）：${e && e.stack ? e.stack : e}`);
  });
}

main();
