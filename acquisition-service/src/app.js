require('dotenv').config();

const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const path = require('path');
const fs = require('fs');
const logger = require('./utils/logger');
const captureRouter = require('./routes/capture');
const healthRouter = require('./routes/health');

const app = express();
const PORT = process.env.PORT || 3005;

// 确保必要目录存在
const dirs = [
  path.resolve(__dirname, '../logs'),
  path.resolve(__dirname, '../storage/cookies'),
  path.resolve(__dirname, '../storage/qrcodes'),
  path.resolve(process.env.SCREENSHOT_DIR || './screenshots')
];
dirs.forEach(dir => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
    logger.info(`创建目录: ${dir}`);
  }
});

// 中间件
app.use(cors());
app.use(bodyParser.json({ limit: '50mb' }));
app.use(bodyParser.urlencoded({ extended: true, limit: '50mb' }));

// 请求日志
app.use((req, res, next) => {
  logger.info(`收到请求: ${req.method} ${req.url}`);
  next();
});

// 路由注册
app.use('/api/health', healthRouter);
app.use('/api/capture', captureRouter);

// 根路径
app.get('/', (req, res) => {
  res.json({
    name: 'acquisition-service',
    version: '1.1.0',
    description: '企业获客采集服务 - 基于Playwright的企查查自动化采集',
    endpoints: {
      health: 'GET /api/health',
      startCapture: 'POST /api/capture/start',
      captureStatus: 'GET /api/capture/:captureId/status',
      stopCapture: 'POST /api/capture/:captureId/stop',
      startLogin: 'POST /api/capture/login/:taskId',
      loginStatus: 'GET /api/capture/login/:taskId/status',
      qrcode: 'GET /api/capture/qrcode/:taskId',
      saveCookie: 'POST /api/capture/save-cookie'
    }
  });
});

// 404
app.use((req, res) => {
  res.status(404).json({ success: false, message: `路由不存在: ${req.method} ${req.url}` });
});

// 全局错误处理
app.use((err, req, res, next) => {
  logger.error(`服务器错误: ${err.message}`, err);
  res.status(500).json({ success: false, message: err.message });
});

// 启动自愈：采集任务的运行状态只活在采集服务内存里，服务重启（发版/崩溃）会让进行中的任务凭空消失，
// ERP 侧状态会永远停在"运行中"——页面上既没有失败原因、也没有"执行"按钮，用户看到的是"跑了很久没有结果"。
// 这里在启动后把遗留的 running/pending 任务标记为失败并写明原因，用户可直接重新执行。
async function reconcileStaleTasks() {
  try {
    const erpClient = require('./utils/erpClient');
    const stale = await erpClient.listUnfinishedTasks();
    if (!stale.length) {
      logger.info('启动自检：没有遗留的进行中采集任务');
      return;
    }
    for (const t of stale) {
      await erpClient.updateTaskStatus(
        t.taskId,
        'failed',
        t.capturedCount || 0,
        '采集服务重启导致任务中断（本次未采完），请点【执行】重新采集'
      ).catch((e) => logger.warn(`启动自愈：任务${t.taskId} 状态修正失败: ${e.message}`));
      logger.warn(`启动自愈：任务${t.taskId}(${t.taskNo || ''}) 原状态=${t.status} → failed（采集服务重启，进行中的采集已中断）`);
    }
  } catch (e) {
    // ERP 没起来 / 网络不通都不影响采集服务本身启动
    logger.warn(`启动自检（遗留任务修正）跳过: ${e.message}`);
  }
}

// 启动服务
app.listen(PORT, () => {
  logger.info(`采集服务启动: http://localhost:${PORT}`);
  logger.info(`ERP API Base: ${process.env.ERP_API_BASE || 'http://localhost:8080/api'}`);
  // 启动自检：把关键采集参数打进日志，改完 .env 忘记重启/没生效时能一眼看出来
  // LOGIN_HEADLESS=false（默认）= 登录浏览器弹出可见窗口；true = 无头（只能靠右侧实时投屏操作）
  const loginHeadless = process.env.LOGIN_HEADLESS === 'true';
  logger.info(`采集配置: CAPTURE_INTERVAL=${process.env.CAPTURE_INTERVAL || '8000'}ms, CAPTURE_COMPANY_INTERVAL=${process.env.CAPTURE_COMPANY_INTERVAL || '4000'}ms, CAPTURE_HEADLESS=${process.env.CAPTURE_HEADLESS !== 'false'}, LOGIN_HEADLESS=${loginHeadless}（登录浏览器${loginHeadless ? '无头' : '可见窗口'}）, SLOW_MO=${process.env.SLOW_MO || '0'}ms（非 0 会拖慢每次点击/按键）`);
  // 启动自愈：修正上一次被中断、状态卡在"运行中"的任务
  reconcileStaleTasks();
});

// 优雅退出
process.on('SIGINT', async () => {
  logger.info('收到 SIGINT 信号，准备退出...');
  process.exit(0);
});
process.on('SIGTERM', async () => {
  logger.info('收到 SIGTERM 信号，准备退出...');
  process.exit(0);
});
process.on('uncaughtException', (err) => {
  logger.error(`未捕获异常: ${err.message}`, err);
});
process.on('unhandledRejection', (reason) => {
  logger.error(`未处理的Promise拒绝: ${reason}`);
});

module.exports = app;
