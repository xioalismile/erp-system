const express = require('express');
const path = require('path');
const fs = require('fs');
const { v4: uuidv4 } = require('uuid');
const logger = require('../utils/logger');
const QccCollector = require('../services/qccCollector');
const FnbCollector = require('../services/fnbCollector');
const TycCollector = require('../services/tycCollector');
const AqcCollector = require('../services/aqcCollector');
const cookieManager = require('../services/cookieManager');
const erpClient = require('../utils/erpClient');
const { matchArea, judgeListTextProvince } = require('../utils/regionFilter');

const router = express.Router();

const captureTasks = new Map();
const loginTasks = new Map();

// 登录任务最大存活时间（7分钟），超时后自动清理
const LOGIN_TASK_MAX_AGE_MS = 7 * 60 * 1000;

/**
 * 清理过期的登录任务
 * 防止前端忘记停止轮询导致任务状态永久驻留
 */
function cleanupExpiredLoginTasks() {
  const now = Date.now();
  let cleaned = 0;
  for (const [taskId, task] of loginTasks.entries()) {
    const createdAt = task.createdAt ? new Date(task.createdAt).getTime() : 0;
    const age = now - createdAt;
    // 已完成（success/failed）的任务保留30分钟供前端查询
    if (task.finishedAt) {
      const finishedAge = now - new Date(task.finishedAt).getTime();
      if (finishedAge > 30 * 60 * 1000) {
        loginTasks.delete(taskId);
        cleaned++;
        logger.info(`清理过期登录任务(已完成): taskId=${taskId}`);
      }
    } else if (age > LOGIN_TASK_MAX_AGE_MS) {
      // 运行超过7分钟的任务，强制标记为失败
      task.status = 'failed';
      task.message = '任务超时（系统自动清理）';
      task.finishedAt = new Date().toISOString();
      if (task.collector) {
        task.collector.close().catch(() => {});
      }
      logger.warn(`清理超时登录任务: taskId=${taskId}, 运行时长=${Math.round(age/60000)}分钟`);
      cleaned++;
    }
  }
  if (cleaned > 0) {
    logger.info(`登录任务清理完成: 清理${cleaned}个过期任务，剩余${loginTasks.size}个`);
  }
}

// 每60秒执行一次清理
setInterval(cleanupExpiredLoginTasks, 60000);
logger.info('登录任务自动清理机制已启动（每60秒检查）');

/**
 * 登录流程浏览器的无头开关。
 * 默认 false = **可见窗口**：登录（扫码 / 手机验证码）要用户能直接看到并手动操作浏览器；
 * 服务器无人值守（窗口起不来）时可设 LOGIN_HEADLESS=true 退回无头，改用右侧「实时投屏」操作。
 */
const LOGIN_HEADLESS = process.env.LOGIN_HEADLESS === 'true';

/**
 * 根据 platform 创建独立的 collector 实例
 * 重要：每个任务都创建新实例，避免共用浏览器导致冲突
 * 采集任务传 captureMode=true（用独立 userDataDir，与登录浏览器目录一致，故两者不能同时开）
 *
 * @param {string} platform qcc=企查查 / fnb=风鸟 / tyc=天眼查（aqc 爱企查已下线）
 * @param {Object} [options] { headless } 显式指定无头开关；不传则按"采集/登录"各自默认值
 */
function createCollector(platform, options = {}) {
  const { headless } = options;
  if (platform === 'fnb') return new FnbCollector({ captureMode: true, headless });
  if (platform === 'tyc') return new TycCollector({ captureMode: true, headless });
  if (platform === 'aqc') return new AqcCollector({ captureMode: true, headless });
  return new QccCollector({ captureMode: true, headless });
}

// platform -> 中文名（日志/提示用）
function platformNameOf(platform) {
  return { qcc: '企查查', fnb: '风鸟', tyc: '天眼查', aqc: '爱企查' }[platform] || '企查查';
}

/**
 * 风控/验证类错误识别：这类错误**不该直接判任务失败**。
 * 企查查的「用户验证」风控页（CAPTCHA_REQUIRED）与搜索结果页没加载出来（PAGE_NOT_READY）
 * 都是"人要出面点一下"的场景，而用户做远程浏览器的目的就是自己过风控
 * （2026-09-22 用户明确要求：撞风控要把页面加载到前端让他自己验证，不能一句"风控"就完事）。
 */
function isRiskError(err) {
  const m = (err && err.message) || '';
  return /^CAPTCHA_REQUIRED/.test(m) || /^PAGE_NOT_READY/.test(m);
}

// 撞风控后等用户人工处理的最长时间（默认 10 分钟，可用 MANUAL_VERIFY_WAIT_MS 调整，下限 1 分钟）
const MANUAL_VERIFY_WAIT_MS = Math.max(60000, Number(process.env.MANUAL_VERIFY_WAIT_MS) || 10 * 60 * 1000);

// platform -> 浏览器持久化目录名（白名单，禁止用外部输入拼接路径，防目录穿越）
// 【2026-09-17】爱企查（aqc）已下线：百度对该站做自动化特征硬拦截（打开即被判定"调试窗口"），
//   Playwright 方案无法稳定通过，故从白名单中移除（登录/实时投屏/远程操作/重置 一并拒绝 aqc）；
//   如日后要恢复：把 aqc 加回本表，并恢复前端两处平台选项（登录页 + 获客任务）。
const PLATFORM_PROFILE_DIRS = {
  qcc: 'qcc-capture',
  fnb: 'fnb-capture',
  tyc: 'tyc-capture'
};

/**
 * 平台白名单。
 * 【安全】platform 来自请求体，会被拼成登录任务 key（login_${platform}）并参与二维码截图/
 * 浏览器持久化目录等路径拼接 —— 必须命中白名单后再使用，否则 "../../x" 之类的值可穿越目录。
 */
const SUPPORTED_PLATFORMS = Object.keys(PLATFORM_PROFILE_DIRS);

/** 平台不合法时返回 400，返回 null 表示校验通过 */
function rejectUnsupportedPlatform(res, platform) {
  if (SUPPORTED_PLATFORMS.includes(platform)) return false;
  res.status(400).json({ success: false, message: `暂不支持平台: ${platform}，支持: ${SUPPORTED_PLATFORMS.join(', ')}` });
  return true;
}

/**
 * 登录流程结束后的兜底校验：确认 Cookie 真的有效落盘
 * 修复"登录假成功"Bug：cookieManager.saveCookie 在 Cookie 全部过期时返回 false 且不写文件，
 * 旧逻辑忽略返回值仍上报"登录成功"，导致前端看到成功、采集却报未登录。
 * @returns {Promise<boolean>} true=Cookie 已有效保存
 */
async function verifyCookieSaved(taskId, platformName) {
  const ok = await cookieManager.isCookieValid(taskId);
  if (!ok) {
    logger.warn(`任务${taskId} ${platformName}登录流程报成功，但Cookie未有效落盘（可能全部已过期），判定为失败`);
  }
  return ok;
}

// 二维码元素选择器（按平台）——只截二维码区域，避免整页截图里二维码太小
const QR_ELEMENT_SELECTORS = {
  qcc: '.qrcode, .login-qr, img[src*="qr"]',
  tyc: 'img[src*="login-scan"], img[class*="scan"], .scan-hover'
};

// 二维码重截间隔：企查查/天眼查的二维码会在浏览器内自动轮换，
// 只截一次会让用户扫到已过期的码（表现为"扫码没反应、页面状态不变"）
const QR_REFRESH_MS = 8000;

// 记录上次截图方式，避免每 8 秒刷一条同样的日志
let lastQrDesc = '';
// 是否已成功截到过「裁剪后的二维码」——成功过就不要再退回整页小图
let lastQrWasCropped = false;

/**
 * 定位二维码元素：先按平台选择器精确匹配，再退回「页面上最大的近似正方形可视元素」。
 * 平台改版后选择器经常失效，正方形视觉元素（img/canvas/svg）是最稳的兜底信号。
 * @returns {Promise<{el: (Object|null), desc: string}>}
 */
async function findQrElement(page, platform) {
  let picked = null;
  try {
    const handle = await page.evaluateHandle((sel) => {
      // 可见性：Playwright 截不到不可见元素（会报 element is not visible）
      // 最小边长取 140：企查查登录页除主二维码(180x180)外还有个 114x114 的小 canvas，
      // 门槛太低会误截到它
      const visible = (el) => {
        const r = el.getBoundingClientRect();
        if (r.width < 140 || r.height < 140) return false;
        if (r.bottom < 0 || r.right < 0) return false;
        const st = window.getComputedStyle(el);
        if (st.display === 'none' || st.visibility === 'hidden' || Number(st.opacity) === 0) return false;
        return true;
      };
      // 二维码一定是近似正方形；用它排掉 360x142 这类横条元素
      const square = (el) => {
        const r = el.getBoundingClientRect();
        const ratio = r.width / r.height;
        return ratio >= 0.8 && ratio <= 1.25;
      };
      // 1) 优先平台选择器命中，且必须可见 + 近似正方形
      if (sel) {
        for (const el of Array.from(document.querySelectorAll(sel))) {
          if (visible(el) && square(el)) return el;
        }
      }
      // 2) 兜底：页面上最大的「可见 + 近似正方形」元素
      let best = null;
      let bestArea = 0;
      for (const el of Array.from(document.querySelectorAll('img, canvas, svg'))) {
        if (!visible(el) || !square(el)) continue;
        const r = el.getBoundingClientRect();
        const area = r.width * r.height;
        if (area > bestArea) { bestArea = area; best = el; }
      }
      return best;
    }, QR_ELEMENT_SELECTORS[platform] || '');
    picked = handle.asElement();
  } catch (e) {
    picked = null;
  }

  if (!picked) {
    // 诊断：把页面上所有 img/canvas/svg 的尺寸打出来，便于判断"是页面没出二维码"还是"门槛不匹配"
    const stats = await page.evaluate(() => Array.from(document.querySelectorAll('img, canvas, svg'))
      .map((el) => {
        const r = el.getBoundingClientRect();
        return `${el.tagName}${Math.round(r.width)}x${Math.round(r.height)}`;
      })
      .filter((s) => !/^(IMG|CANVAS|SVG)0x0$/.test(s))
      .slice(0, 12)
      .join(', ')).catch(() => '');
    return { el: null, desc: `未找到二维码元素(候选: ${stats || '无'})，退回整页截图` };
  }
  const box = await picked.boundingBox().catch(() => null);
  if (!box || box.width < 140) return { el: null, desc: '二维码元素尺寸异常，退回整页截图' };
  // 带上元素特征（标签/类名/图片源片段），便于在日志里核对到底截到了什么
  const meta = await picked.evaluate((n) => {
    const cls = typeof n.className === 'string' ? n.className.split(/\s+/).filter(Boolean).slice(0, 2).join('.') : '';
    const src = n.getAttribute && n.getAttribute('src') ? String(n.getAttribute('src')).slice(0, 50) : '';
    return `${n.tagName}${cls ? '.' + cls : ''}${src ? '|' + src : ''}`;
  }).catch(() => '');
  return { el: picked, desc: `命中 ${Math.round(box.width)}x${Math.round(box.height)} ${meta}` };
}

/**
 * 截取二维码：只截二维码元素（图大、无需手动放大），找不到元素才退回整页截图。
 * 二维码是异步渲染的，首帧常常还没出现，故内置重试（默认 5 次 × 1.5 秒）。
 * 注意：临时文件名必须保留 .png 后缀，Playwright 按扩展名判断图片类型
 *（曾用 xxx.png.tmp 导致 unsupported mime type "null" 而截图全失败）。
 * @returns {Promise<boolean>} 是否成功写入
 */
async function captureQrShot(collector, platform, qrPath, attempts = 5) {
  const page = collector && collector.page;
  if (!page || page.isClosed()) return false;
  const tmpPath = qrPath.replace(/\.png$/i, '.tmp.png');
  let lastErr = null;

  for (let i = 0; i < attempts; i++) {
    if (!page.isClosed()) {
      try {
        const { el, desc } = await findQrElement(page, platform);
        if (el) {
          // 必须带 timeout：元素若正在重渲染，Playwright 会一直等"元素稳定"，
          // 默认 30 秒起步，会把首次出图卡住
          await el.screenshot({ path: tmpPath, timeout: 5000 });
          fs.renameSync(tmpPath, qrPath);
          lastQrWasCropped = true;
          if (desc !== lastQrDesc) {
            lastQrDesc = desc;
            logger.info(`二维码截图方式(platform=${platform}): ${desc}`);
          }
          return true;
        }
        lastErr = new Error('未找到二维码元素');
      } catch (e) {
        lastErr = e;
      }
    }
    if (i < attempts - 1) await page.waitForTimeout(1500).catch(() => {});
  }

  // 重试用尽仍找不到元素：若之前已成功截到过二维码，就保留那张
  //（否则会把清晰的大二维码换成整页小图，用户又要手动放大）
  if (lastQrWasCropped) {
    if (lastQrDesc !== '保留上一张二维码') {
      lastQrDesc = '保留上一张二维码';
      logger.warn(`二维码截图(platform=${platform}): ${lastErr ? lastErr.message : '未找到元素'}，保留上一张二维码图`);
    }
    return false;
  }

  // 尚未成功过：退回整页截图，至少保证页面上有图可看
  try {
    await page.screenshot({ path: tmpPath, timeout: 8000 });
    fs.renameSync(tmpPath, qrPath);
    if (lastQrDesc !== '整页兜底') {
      lastQrDesc = '整页兜底';
      logger.warn(`二维码截图(platform=${platform}): ${lastErr ? lastErr.message : '未找到元素'}，退回整页截图`);
    }
    return true;
  } catch (e) {
    try { fs.rmSync(tmpPath, { force: true }); } catch (ignore) { /* ignore */ }
    logger.warn(`二维码截图失败(platform=${platform}, ${path.basename(qrPath)}): ${e.message}`);
    return false;
  }
}

/**
 * 采集状态枚举
 *  - pending: 已接收，等待执行
 *  - running: 正在执行
 *  - success: 成功完成
 *  - failed:  执行失败
 *  - stopped: 已停止
 */

/**
 * 失败现场快照：把采集浏览器"此刻看到的页面"截图存盘。
 *
 * 为什么必须做（2026-09-18 用户实测反馈）：平台风控/异常时只抛错误、不留图，
 * 而**采集浏览器是 headless、且不进投屏**（投屏默认只绑登录浏览器）
 * → 事后完全看不到现场，只能靠猜。
 * 用户原话：「你说是风控，出验证页面了但是我在企查查的服务器浏览器实时画面看不到验证的那个信息呢？」
 *
 * 【2026-09-22 补充】用户的诉求已经升级：**撞风控时要把页面直接加载到前端让他自己过验证**
 * （见 waitForManualVerify + getActiveCaptureSession：采集浏览器现在会接进实时投屏/远程操作，
 * 并在验证页等待人工处理）。这个"失败现场截图"只在**真正判定失败**时兜底留证，两者是互补的。
 *
 * 容错铁律：截图失败绝不影响失败原因上报（整段吞异常）。
 * @returns {Promise<string|null>} 成功返回截图绝对路径，失败返回 null
 */
async function captureRiskSnapshot(collector, platform, taskId) {
  try {
    const page = collector && collector.page;
    if (!page || page.isClosed()) return null;
    const dir = path.resolve(process.env.SCREENSHOT_DIR || './screenshots');
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const file = path.join(dir, `risk_${platform}_task${taskId}_${stamp}.png`);
    await page.screenshot({ path: file, fullPage: false });
    logger.warn(`失败现场快照已保存: ${file}`);
    return file;
  } catch (e) {
    logger.warn(`失败现场快照保存失败（不影响主流程）: ${e.message}`);
    return null;
  }
}

/**
 * 【诊断·2026-09-23】打印搜索结果列表行的原文样本，用于判定「列表页前置粗筛」在各平台是否可行。
 * 只打日志、不参与过滤：各平台列表行是否含地址信息，需实测一轮后（看本日志）再决定粗筛实现范围。
 */
function logListTextDiagnostics(taskId, keyword, companies, platform) {
  if (!Array.isArray(companies) || companies.length === 0) return;
  const samples = companies.slice(0, 3).map((c, i) => ({
    idx: i + 1,
    name: c.companyName || '',
    listText: String(c.rawText || c.debugListText || '').slice(0, 200)
  }));
  logger.info(`【列表行诊断】任务${taskId} 关键词「${keyword}」平台=${platform} 共${companies.length}家，前${samples.length}家列表行原文: ${JSON.stringify(samples)}`);
}

/**
 * 【2026-09-23】列表页前置粗筛：在打开详情页之前，把「列表行里已能看出在别的省」的企业叉掉，
 * 省掉最贵的详情采集（实测：一轮 60+ 家详情只留 2 家，约 97% 白跑）。
 * 只在列表行文本带地址时生效；判不出（无地址 / 无省名 / 目标省也在行内）一律放行，交给详情阶段兜底。
 */
function coarseFilterByListText(taskId, keyword, companies, area) {
  if (!area || !Array.isArray(companies) || companies.length === 0) return companies;
  const kept = companies.filter((c) => judgeListTextProvince(c.rawText || c.debugListText || '', area) !== 'other-province');
  const dropped = companies.length - kept.length;
  if (dropped > 0) {
    logger.info(`任务${taskId} 列表页前置粗筛: 关键词「${keyword}」共 ${companies.length} 家 → 提前剔除外省 ${dropped} 家，进入详情 ${kept.length} 家（判不出的一律放行）`);
  } else {
    logger.info(`任务${taskId} 列表页前置粗筛: 关键词「${keyword}」共 ${companies.length} 家，无可提前剔除（列表行未带可判定地址），全部进入详情`);
  }
  return kept;
}

/**
 * 后台执行采集任务
 */
async function runCapture(captureId, params) {
  const task = captureTasks.get(captureId);
  if (!task) return;

  task.status = 'running';
  task.startedAt = new Date().toISOString();
  const { taskId, keywords, maxPages, maxCompanies, loginCookie, platform, area, callerToken } = params;

  // 根据platform选择不同的collector：fnb=风鸟，默认qcc=企查查
  // 每个任务创建独立的collector实例，避免共用浏览器导致冲突
  const collector = createCollector(platform);
  const platformName = platformNameOf(platform);
  // 把 collector 挂到任务状态上：
  //   ① 实时投屏 / 远程操作接口要能拿到这个采集浏览器 —— 撞到风控/验证页时用户要自己在前端画面上过验证
  //   ② 便于从 /api/capture/session 看出"该平台还有没有活着的采集浏览器"
  task.collector = collector;
  task.platformName = platformName;

  // 公司之间的采集间隔（默认 4 秒，2026-09-16 新增）：
  // 逐家公司连续抓详情是触发平台限流/风控的主因，CAPTURE_INTERVAL 只管翻页与关键词之间
  const companyIntervalMs = parseInt(process.env.CAPTURE_COMPANY_INTERVAL || '4000');

  // 提到 try 外面：任务中途因验证码/异常中止时，已采集的部分仍要落库（见 catch 分支）
  const allResults = [];
  const detailResults = [];

  // ===== 采集结果「边采边写」（2026-09-22 用户要求：不等整单跑完，采到一家就立刻回写一家）=====
  // 旧行为：整个关键词/翻页循环跑完（或中途异常）才调一次 /result/batch 批量写库，
  //        用户必须等整单结束才看得到数据。
  // 新行为：每采到一家、校验通过就立刻单条写库，并立刻把「已入库绝对数」回写 ERP。
  // 注意：ERP 侧 batchSave 内部是 capturedCount += 条数 的**累加**口径，所以每条写完必须
  //      再用 updateTaskStatus 写一次绝对数覆盖，否则数量会与真实入库数漂移。
  let uploadedCount = 0;        // 已成功写入 ERP 的条数
  let uploadFailedCount = 0;    // 写入失败的条数
  let firstUploadError = '';    // 第一条失败原因（用于最终汇总）

  /**
   * 把单条采集结果立刻回写到 ERP（边采边写）。
   * 单条失败**不中断整单采集**：只记失败数与首个原因，最后如实汇总给用户。
   * @returns {Promise<boolean>} true=已写入成功
   */
  const writeBackOne = async (item) => {
    try {
      await erpClient.postResultsToErp(taskId, [item]);
      uploadedCount += 1;
      // 状态回写失败不影响采集本身，但任务状态最终还会再写一次
      await erpClient.updateTaskStatus(taskId, 'running', uploadedCount, '').catch(() => {});
      logger.info(`任务${taskId} 已回写第 ${uploadedCount} 家: ${item.companyName || '未知公司'}`);
      return true;
    } catch (e) {
      uploadFailedCount += 1;
      if (!firstUploadError) firstUploadError = e.message;
      logger.error(`任务${taskId} 结果回写失败（${item.companyName || '未知公司'}）: ${e.message}`);
      return false;
    }
  };

  /**
   * 撞到风控/验证页：**不判失败、不关浏览器**，把任务挂成「等待人工验证」并保持画面可用，
   * 等用户到「获客管理 → 采集平台登录」在实时画面上处理完、点【继续采集】后，从这里往下跑。
   * 旧行为是直接抛错 → 任务失败 + 关浏览器 + 只在服务器留一张截图，用户在前端什么都看不到、
   * 也没法自己过验证（用户 2026-09-22 明确要求把风控页面加载到前端）。
   * @param {string} reason 给用户看的场景说明（会写进任务 message）
   * @returns {Promise<'resumed'|'stopped'|'timeout'>}
   */
  const waitForManualVerify = async (reason) => {
    task.status = 'blocked';
    task.resumeRequested = false;
    task.message = `${reason}｜已停在验证页面等待人工处理：请到「获客管理 → 采集平台登录」，在下方浏览器画面里完成验证，然后点【继续采集】`;
    logger.warn(`任务${taskId} 进入等待人工处理（最长 ${Math.round(MANUAL_VERIFY_WAIT_MS / 60000)} 分钟）: ${reason}`);
    // ERP 侧保持「运行中」：任务确实没结束，只是在等人在服务器浏览器上过验证
    await erpClient.updateTaskStatus(taskId, 'running', uploadedCount, '').catch(() => {});
    const deadline = Date.now() + MANUAL_VERIFY_WAIT_MS;
    while (Date.now() < deadline) {
      if (task.stopRequested) {
        task.status = 'stopped';
        logger.info(`任务${taskId} 等待人工处理期间被用户停止`);
        return 'stopped';
      }
      if (task.resumeRequested) {
        task.resumeRequested = false;
        task.status = 'running';
        task.message = '人工验证完成，继续采集';
        logger.info(`任务${taskId} 收到【继续采集】，恢复采集`);
        await erpClient.updateTaskStatus(taskId, 'running', uploadedCount, '').catch(() => {});
        return 'resumed';
      }
      await new Promise((r) => setTimeout(r, 2000));
    }
    logger.warn(`任务${taskId} 等待人工处理超时（${Math.round(MANUAL_VERIFY_WAIT_MS / 60000)} 分钟未处理）`);
    return 'timeout';
  };

  try {
    // 更新 ERP 任务状态为 running
    await erpClient.updateTaskStatus(taskId, 'running', 0, '').catch(err => {
      logger.warn(`更新ERP任务状态(running)失败，继续采集: ${err.message}`);
    });

    // 初始化浏览器
    // 采集浏览器与登录浏览器共用同一个持久化目录（qcc-capture / fnb-capture）：
    // 若该平台的登录浏览器还开着（登录后保活期内），必须先关掉，否则新浏览器会因为
    // "浏览器目录被占用"直接启动失败（实测 2026-09-17 8cloud：Edge exitCode=21 → 采集无结果）
    const runningLoginTask = loginTasks.get(`login_${platform}`);
    if (runningLoginTask && runningLoginTask.collector) {
      await runningLoginTask.collector.close().catch(() => {});
      logger.info(`任务${taskId} 启动采集前已关闭${platformName}登录浏览器（释放浏览器目录）`);
      await new Promise((r) => setTimeout(r, 1000));
    }
    await collector.init();

    // 优先使用请求传入的 loginCookie
    if (loginCookie) {
      try {
        const parsed = typeof loginCookie === 'string' ? JSON.parse(loginCookie) : loginCookie;
        await cookieManager.saveCookie(taskId, parsed);
        logger.info(`任务${taskId} 使用请求传入的Cookie已保存`);
      } catch (e) {
        logger.warn(`任务${taskId} 传入Cookie格式无效，忽略: ${e.message}`);
      }
    }

    // 加载Cookie
    await collector.loadLoginState(taskId);

    // 校验登录状态
    const loggedIn = await collector.checkLogin();
    if (!loggedIn) {
      throw new Error(`LOGIN_REQUIRED: ${platformName}未登录，请先调用二维码登录接口获取Cookie`);
    }

    let capturedCount = 0;
    let stopped = false;

    // 遍历关键词采集
    for (const keyword of keywords) {
      if (task.stopRequested) { stopped = true; break; }
      if (capturedCount >= maxCompanies) break;

      logger.info(`任务${taskId} 开始处理关键词: ${keyword}, 地区筛选: ${area || '无'}`);
      // 把 area 传给 collector，由 collector 决定如何应用地区筛选。
      // 撞到风控/验证页**不判失败**：挂成"等待人工验证"，等用户在前端画面上处理完再重试本关键词。
      let companies = [];
      for (;;) {
        try {
          companies = await collector.searchCompanies(keyword, maxPages, { area });
          logListTextDiagnostics(taskId, keyword, companies, platform);
          companies = coarseFilterByListText(taskId, keyword, companies, area);
          break;
        } catch (e) {
          if (!isRiskError(e) || task.stopRequested) throw e;
          const waited = await waitForManualVerify(`关键词「${keyword}」采集时${platformName}要求人工验证`);
          if (waited === 'resumed') continue;      // 用户已处理完 → 重试本关键词
          throw e;                                  // 用户停止 / 等待超时 → 按原逻辑失败收尾
        }
      }

      // 限制 maxCompanies
      for (const company of companies) {
        if (task.stopRequested) { stopped = true; break; }
        if (capturedCount >= maxCompanies) break;

        // 每采集 5 家公司后，校验前端用户登录态
        // 如果 callerToken 失效（401），停止采集任务
        if (callerToken && capturedCount > 0 && capturedCount % 5 === 0) {
          const tokenValid = await erpClient.verifyCallerToken(callerToken).catch(err => {
            logger.warn(`任务${taskId} 校验用户登录态异常（继续采集）: ${err.message}`);
            return true;  // 异常时不阻止采集
          });
          if (!tokenValid) {
            logger.warn(`任务${taskId} 启动者登录已过期，停止采集任务（已采集 ${capturedCount} 家）`);
            task.stopRequested = true;
            stopped = true;
            task.message = '启动者登录已过期，采集任务已自动停止';
            break;
          }
        }

        allResults.push(company);
        capturedCount += 1;
        task.capturedCount = capturedCount;
        logger.info(`任务${taskId} 处理第 ${capturedCount} 家: ${company.companyName} (detailUrl=${company.detailUrl ? '有' : '无'})`);

        // 逐个获取详情
        // 即使没有 detailUrl，也尝试调用 getCompanyDetail，让 collector 内部用公司名搜索详情
        try {
          const detail = await collector.getCompanyDetail(company.detailUrl, company.companyName);
          // 注意合并顺序：detail 在前，company 在后
          // 这样 detail 里错误提取的 companyName 会被 company 里正确的 companyName 覆盖
          // 同时保留 detail 里的截图、信用代码、法人等字段
          const merged = { ...detail, ...company, screenshotPath: detail.screenshotPath || company.screenshotPath };

          // 详情字段有效性校验：如果关键字段（信用代码、地区、法人、成立日期）全部为空
          // 说明详情页提取失败（可能是风鸟反爬、页面结构变化、搜索结果跳到了占位页）
          // 这种记录没有业务价值，直接剔除，避免污染采集结果
          const keyFields = [merged.creditCode, merged.area, merged.legalPerson, merged.establishDate, merged.registeredAddress];
          const allEmpty = keyFields.every(v => !v || !String(v).trim() || String(v).trim() === '无');
          if (allEmpty) {
            logger.warn(`任务${taskId} 详情字段全空，剔除: ${company.companyName} (可能风鸟反爬或页面结构变化，截图保留供排查: ${merged.screenshotPath || '无'})`);
            capturedCount -= 1;
            task.capturedCount = capturedCount;
            continue;
          }

          // 地区过滤：详情采集后按 detail.area 或注册地址判定（拆词/匹配规则统一收在 utils/regionFilter.js）
          // 2026-09-23 修正：原实现把"广州白云区"去掉后缀后得到整串"广州白云"，又要求地址含连续串，
          //   而详情地址是"广东省广州市白云区" → 本应匹配的企业被误剔除；且后缀词表漏了"地区/盟/林区"，
          //   "大兴安岭地区"被切成"大兴安岭地" → 同样误剔除。现改为「后缀切分 + 长段贪心切分兜底」。
          // 原则：宁可宽松放行（后面还有入库校验），绝不错杀。
          // 关键约定：设置了 area 但详情 area 和注册地址都为空（详情提取失败）→ 无法确认，剔除。
          if (area) {
            const regionCheck = matchArea(merged.area, merged.registeredAddress, area);
            if (regionCheck.reason === 'no-address') {
              logger.warn(`任务${taskId} 详情字段为空，剔除: ${company.companyName} (无法获取地区信息，无法匹配[${area}])`);
              capturedCount -= 1;
              task.capturedCount = capturedCount;
              continue;
            }
            if (!regionCheck.matched) {
              logger.info(`任务${taskId} 地区过滤剔除: ${company.companyName} (area=${merged.area || '无'}, 地址=${merged.registeredAddress || '无'}, 不含[${area}], 拆词=${JSON.stringify(regionCheck.keywords)})`);
              capturedCount -= 1;
              task.capturedCount = capturedCount;
              continue;
            }
            logger.info(`任务${taskId} 地区匹配成功: ${company.companyName} (area=${merged.area})`);
          }
          detailResults.push(merged);
          // 采到一家、校验通过就立刻回写一家（不等整单跑完）
          await writeBackOne(merged);
        } catch (detailErr) {
          let failReason = detailErr.message;
          // 详情页撞到验证码/风控：先把画面交给用户自己处理，不直接判整单失败
          if (isRiskError(detailErr) && !task.stopRequested) {
            const waited = await waitForManualVerify(`采集「${company.companyName}」详情时${platformName}要求人工验证`);
            // 用户停止 / 等待超时 → 按原逻辑失败收尾；处理完了则继续下一家（这一家记进明细并提示重跑补齐），
            // 避免为同一家反复等满 10 分钟
            if (waited !== 'resumed') throw detailErr;
            failReason = `${detailErr.message}（人工验证已完成，本家需重跑任务补齐）`;
          }
          logger.warn(`获取详情失败: ${company.companyName}, ${detailErr.message}`);
          const failedItem = { ...company, detailError: failReason };
          detailResults.push(failedItem);
          await writeBackOne(failedItem);
        }

        // 周期性更新ERP任务进度
        if (capturedCount % 5 === 0) {
          await erpClient.updateTaskStatus(taskId, 'running', capturedCount, '').catch(() => {});
        }

        // 公司之间的间隔：降低连续请求频率，避免触发平台风控/限流
        if (companyIntervalMs > 0 && !task.stopRequested && capturedCount < maxCompanies) {
          await new Promise((resolve) => setTimeout(resolve, companyIntervalMs));
        }
      }
      if (stopped) break;
    }

    if (stopped && task.message !== '启动者登录已过期，采集任务已自动停止') {
      task.status = 'stopped';
      task.message = '用户主动停止采集';
    } else if (stopped) {
      task.status = 'stopped';
    } else {
      task.status = 'success';
      task.message = '采集完成';
    }

    task.results = detailResults;
    task.capturedCount = capturedCount;
    task.finishedAt = new Date().toISOString();

    // 结果已在采集过程中逐条回写（见 writeBackOne），这里不再整批上传，只做最终口径汇总。
    // 【如实上报铁律】写库条数与采集条数对不上时必须让用户看见，不能用"已完成"掩盖：
    //   有写失败 → 任务标 failed + 写明「已入库 X 家，Y 家未保存成功：原因」，
    //   计数以**真实入库数**为准（旧版曾出现"任务显示已采集 10 条、采集结果却是空的"假成功）。
    if (uploadFailedCount > 0) {
      task.status = 'failed';
      task.capturedCount = uploadedCount;
      task.message = `已入库 ${uploadedCount} 家，${uploadFailedCount} 家未保存成功：${firstUploadError}`;
    } else if (task.status === 'success') {
      task.message = `采集完成，已入库 ${uploadedCount} 家`;
    }

    // 更新 ERP 任务状态
    // 状态映射：采集服务内部用 success/stopped，但 ERP 后端期望 completed/paused
    //   success -> completed（已完成）
    //   stopped -> paused（已暂停）
    //   failed  -> failed（失败）
    //   running -> running（运行中）
    const erpStatus = task.status === 'success' ? 'completed'
                    : task.status === 'stopped' ? 'paused'
                    : task.status;
    await erpClient.updateTaskStatus(
      taskId,
      erpStatus,
      task.capturedCount,
      task.message
    ).catch(() => {});

    logger.info(`采集任务完成: captureId=${captureId}, taskId=${taskId}, 共采集${capturedCount}家`);
  } catch (err) {
    logger.error(`采集任务失败: captureId=${captureId}, ${err.message}`, err);
    task.status = 'failed';
    task.finishedAt = new Date().toISOString();

    // 失败现场快照：catch 跑在 finally（关浏览器）之前，此刻页面还在，能截到真实画面
    const riskShot = await captureRiskSnapshot(collector, platform, taskId);

    // 中途中止（如撞上平台验证码/风控页）时不需要再补写：结果在采集过程中已逐条回写
    //（见 writeBackOne），已写入的不受影响，未写成功的由下面的失败原因如实汇总。
    // 失败原因给人看：去掉内部错误前缀，并说明已入库了多少
    const reason = /^CAPTCHA_REQUIRED/.test(err.message)
      ? err.message.replace(/^CAPTCHA_REQUIRED:\s*/, '')
      : err.message;
    task.capturedCount = uploadedCount;
    // 有现场截图时在原因末尾补一句提示：否则用户不知道服务器上留了证据，只能干等
    // （放末尾是刻意的：erpClient 侧按 200 字截断，极端长报错时优先保住"原因"本身）
    const shotHint = riskShot ? '（已留存失败现场截图，可让技术提取）' : '';
    const savedHint = uploadedCount > 0 ? `（已入库 ${uploadedCount} 家）` : '';
    const uploadHint = uploadFailedCount > 0
      ? `（另有 ${uploadFailedCount} 家未保存成功：${firstUploadError}）`
      : '';
    task.error = `${reason}${savedHint}${uploadHint}${shotHint}`;
    await erpClient.updateTaskStatus(taskId, 'failed', uploadedCount, task.error).catch(() => {});
  } finally {
    // 关闭浏览器
    await collector.close().catch(() => {});
  }
}

/**
 * POST /api/capture/start
 * 启动采集任务
 * body: { taskId, keywords, platform, maxPages, maxCompanies, loginCookie, area, callerToken }
 */
router.post('/start', async (req, res) => {
  try {
    const { taskId, keywords, platform = 'qcc', maxPages = 5, maxCompanies = 50, loginCookie, area, callerToken } = req.body || {};

    if (!taskId) {
      return res.status(400).json({ success: false, message: '缺少参数 taskId' });
    }
    if (!Array.isArray(keywords) || keywords.length === 0) {
      return res.status(400).json({ success: false, message: '缺少参数 keywords 或为空' });
    }
    // 平台白名单与登录/实时投屏/重置等接口共用同一份（SUPPORTED_PLATFORMS 由 PLATFORM_PROFILE_DIRS 推导）
    if (!SUPPORTED_PLATFORMS.includes(platform)) {
      return res.status(400).json({ success: false, message: `暂不支持平台: ${platform}，支持: ${SUPPORTED_PLATFORMS.join(', ')}` });
    }

    const captureId = uuidv4();
    const taskState = {
      captureId,
      taskId,
      platform,
      keywords,
      maxPages,
      maxCompanies,
      area: area || '',
      callerToken: callerToken || '',  // 前端用户 token，用于定期校验登录态
      status: 'pending',
      capturedCount: 0,
      results: [],
      createdAt: new Date().toISOString(),
      stopRequested: false
    };
    captureTasks.set(captureId, taskState);

    logger.info(`收到采集任务: captureId=${captureId}, taskId=${taskId}, keywords=${JSON.stringify(keywords)}, area=${area || '无'}, maxPages=${maxPages}, maxCompanies=${maxCompanies}, callerToken=${callerToken ? '有' : '无'}`);

    // 异步执行采集，立即返回 captureId
    runCapture(captureId, { taskId, keywords, maxPages, maxCompanies, loginCookie, platform, area, callerToken }).catch(err => {
      logger.error(`采集任务异常: captureId=${captureId}, ${err.message}`, err);
    });

    return res.json({
      success: true,
      message: '采集任务已接收，正在后台执行',
      captureId,
      status: 'pending'
    });
  } catch (err) {
    logger.error(`启动采集任务失败: ${err.message}`, err);
    return res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * GET /api/capture/:captureId/status
 * 查询采集状态
 */
router.get('/:captureId/status', (req, res) => {
  const { captureId } = req.params;
  const task = captureTasks.get(captureId);
  if (!task) {
    return res.status(404).json({ success: false, message: '采集任务不存在' });
  }
  return res.json({
    success: true,
    captureId: task.captureId,
    taskId: task.taskId,
    status: task.status,
    capturedCount: task.capturedCount,
    message: task.message || task.error || '',
    createdAt: task.createdAt,
    startedAt: task.startedAt,
    finishedAt: task.finishedAt,
    resultsCount: (task.results || []).length
  });
});

/**
 * POST /api/capture/:captureId/stop
 * 停止采集
 */
router.post('/:captureId/stop', (req, res) => {
  const { captureId } = req.params;
  const task = captureTasks.get(captureId);
  if (!task) {
    return res.status(404).json({ success: false, message: '采集任务不存在' });
  }
  if (task.status === 'success' || task.status === 'failed' || task.status === 'stopped') {
    return res.json({ success: true, message: `任务已结束(${task.status})`, status: task.status });
  }
  task.stopRequested = true;
  logger.info(`请求停止采集任务: captureId=${captureId}`);
  return res.json({ success: true, message: '停止请求已提交，等待当前页面处理完成后停止' });
});

/**
 * GET /api/capture/session?platform=qcc
 * 查该平台"当前可操作的浏览器"是谁：
 *   source='login'   登录流程的浏览器（扫码/验证页面）
 *   source='capture' 采集任务的浏览器 —— status='blocked' 表示采集任务正停在验证/风控页等人工处理
 * 前端用途：① 采集平台登录页据此把画面（含风控页）自动接出来、并显示「继续采集」按钮
 *          ② 采集任务列表页据此提示"有任务在等你处理验证页"
 */
router.get('/session', (req, res) => {
  const platform = req.query.platform || 'qcc';
  if (rejectUnsupportedPlatform(res, platform)) return;
  const session = getLiveSession(platform);
  if (!session) {
    return res.json({ success: true, platform, hasSession: false, source: null });
  }
  if (session.source === 'capture') {
    return res.json({
      success: true,
      platform,
      hasSession: true,
      source: 'capture',
      captureId: session.capture.captureId,
      taskId: session.capture.task.taskId,
      status: session.capture.task.status,            // running（采集中）/ blocked（等人工验证）
      message: session.capture.task.message || '',
      pageUrl: (session.page && !session.page.isClosed()) ? session.page.url() : ''
    });
  }
  return res.json({
    success: true,
    platform,
    hasSession: true,
    source: 'login',
    status: session.loginTask.status,
    message: session.loginTask.message || '',
    pageUrl: (session.page && !session.page.isClosed()) ? session.page.url() : ''
  });
});

/**
 * POST /api/capture/capture/:captureId/resume
 * 用户在前端画面上完成人工验证（滑块/验证码/风控页）后，通知采集任务继续。
 * body: 无（captureId 从路径取）
 */
router.post('/capture/:captureId/resume', (req, res) => {
  const { captureId } = req.params;
  const t = captureTasks.get(captureId);
  if (!t) {
    return res.status(404).json({ success: false, message: '采集任务不存在或已结束' });
  }
  if (t.status !== 'blocked') {
    return res.json({ success: false, message: `当前状态是 ${t.status}，不需要点【继续采集】（只有停在验证页等待人工处理时才需要）` });
  }
  t.resumeRequested = true;
  logger.info(`任务${t.taskId} 收到【继续采集】指令（用户已完成人工验证）`);
  return res.json({ success: true, message: '已通知采集任务继续，请观察采集结果是否继续增加' });
});

/**
 * POST /api/capture/login/:taskId
 * 触发扫码登录/手动登录（启动浏览器→打开登录页→截图二维码或登录页→等待用户完成登录）
 * 异步执行，立即返回。前端通过 GET /api/capture/login/:taskId/status 查询登录状态
 * body: { platform?: 'qcc'|'fnb'|'tyc'|'aqc' }  默认 qcc
 *
 * 平台隔离改造：登录任务 key 使用 `login_${platform}`，四个平台各自独立，
 * 避免切换平台时读到上一个平台的登录状态/二维码截图/Cookie。
 */
router.post('/login/:taskId', async (req, res) => {
  const { platform = 'qcc' } = req.body || {};
  if (rejectUnsupportedPlatform(res, platform)) return;
  // 登录任务按平台隔离：key = login_qcc / login_fnb / login_tyc / login_aqc
  const taskId = `login_${platform}`;
  try {
    // 该平台有采集任务正用着同一个浏览器目录（采集中 / 停在验证页等人工处理）：
    // 再开一个登录浏览器会两个实例抢目录（历史实锤：Edge exitCode=21 起不来）。
    // 直接告诉用户"用下方画面操作"，而不是起一个必然失败的浏览器。
    const capSession = getActiveCaptureSession(platform);
    if (capSession) {
      const st = capSession.task.status === 'blocked' ? '正停在验证页面等待人工处理' : '正在采集中';
      return res.json({
        success: false,
        message: `该平台采集任务正在使用服务器浏览器（${st}）：画面已自动接过来，请直接在下方操作；若是验证页，处理完点【继续采集】`
      });
    }
    // 如果已有相同平台的登录任务在运行，直接返回
    const existing = loginTasks.get(taskId);
    if (existing && existing.status === 'running') {
      return res.json({ success: true, message: '登录任务已在运行', status: existing.status });
    }
    // 上一轮任务已结束、但浏览器还在保活期内：先关掉再开新的，
    // 否则同一浏览器持久化目录会被两个实例同时占用（新窗口起不来 / 登录态写乱）
    if (existing && existing.collector) {
      await existing.collector.close().catch(() => {});
      logger.info(`任务${taskId} 启动新登录流程前已关闭上一轮保活中的浏览器`);
    }

  // 根据platform选择collector：fnb=风鸟，默认qcc=企查查
  // 每个登录任务也创建独立的collector实例，避免与采集任务互相干扰
  // 登录任务默认**可见窗口**（LOGIN_HEADLESS=false）：用户要直接在浏览器里扫码/输验证码；
  // 目录仍是 *-capture（与采集共用），所以"是否无头"必须单独指定，不能跟着 captureMode 走
  const collector = createCollector(platform, { headless: LOGIN_HEADLESS });
  const platformName = platformNameOf(platform);

    // 创建新的登录任务
    const loginTask = {
      taskId,
      platform,
      status: 'running',
      message: `正在启动浏览器获取${platformName}登录页...`,
      qrImagePath: null,
      cookies: null,
      createdAt: new Date().toISOString(),
      finishedAt: null,
      collector: collector  // 保存 collector 引用，供 confirm 接口抓取 Cookie
    };
    loginTasks.set(taskId, loginTask);

    // 二维码截图状态归零（lastQrDesc / lastQrWasCropped 是模块级变量）：
    // 上一轮任务留下的"已截到过裁剪二维码"标记会让新任务永不退回整页截图，
    // 表现为前端一直显示上一轮那张旧图（2026-09-22 实测踩坑）。
    lastQrDesc = '';
    lastQrWasCropped = false;

    // 二维码轮换重截定时器（仅二维码平台使用），任务结束时清理
    let qrRefreshTimer = null;

    // 异步执行登录流程：先准备登录页截图（立即更新 qrImagePath），再等待用户完成登录
    (async () => {
      try {
        logger.info(`任务${taskId} 开始${platformName}登录流程`);
        // 第1步：准备登录页/二维码（打开浏览器→打开登录页→截图）
        const qrResult = await collector.prepareQrCode(taskId);
        if (qrResult.success) {
          // alreadyLoggedIn由collector内部严格验证后才会返回
          // （DOM检测 + 跳转首页确认 + _checkLoginDom二次验证）
          if (qrResult.alreadyLoggedIn) {
            // 兜底校验：只有 Cookie 真正有效落盘才算登录成功（修复"登录假成功"）
            const cookieOk = await verifyCookieSaved(taskId, platformName);
            if (cookieOk) {
              loginTask.status = 'success';
              loginTask.message = `检测到${platformName}已登录，Cookie已自动保存（共${qrResult.cookieCount}条）`;
              logger.info(`任务${taskId} ${platformName}检测到已有登录态，跳过扫码流程`);
            } else {
              loginTask.status = 'failed';
              loginTask.message = `${platformName}登录态已失效（Cookie全部过期），请点击"清除登录态并重新扫码"`;
            }
            loginTask.finishedAt = new Date().toISOString();
            return;
          }
          loginTask.qrImagePath = qrResult.qrPath;
          const loginMessages = {
            qcc: '二维码已生成，请使用企查查APP扫码',
            fnb: '风鸟登录页已打开，需要在服务器本机完成手机号验证码登录（平台限制，暂不支持远程）',
            tyc: '二维码已生成，请使用天眼查APP扫码',
            aqc: '爱企查登录页已打开，需要在服务器本机完成登录（平台限制，暂不支持远程）'
          };
          loginTask.message = loginMessages[platform] || loginMessages.qcc;

          // 二维码平台：改「只截二维码元素」并定期重截，前端按 qrVersion 刷新图片。
          // 企查查/天眼查的二维码会在浏览器内自动轮换，只截一次会让用户扫到已过期的码。
          if (QR_ELEMENT_SELECTORS[platform]) {
            // 先让前端立刻能看到图（用 collector 刚截的那张），
            // 再把「裁剪到二维码」放后台做：元素截图要等元素稳定，放在 await 里会把首帧卡十几秒
            loginTask.qrVersion = Date.now();
            const bumpQrVersion = () => { loginTask.qrVersion = Date.now(); };
            // 连续截不到二维码的次数：连续 3 次（≈24 秒）说明主页面已经不在登录页
            //（被误点/被站点跳走），此时前端那张二维码已经失效，用户会反复扫死码
            // → 自动回登录页重出二维码（2026-09-22 实测踩坑）
            let qrMissStreak = 0;
            const refreshQr = async (attempts) => {
              const ok = await captureQrShot(collector, platform, qrResult.qrPath, attempts);
              if (ok) {
                qrMissStreak = 0;
                bumpQrVersion();
                return;
              }
              qrMissStreak += 1;
              if (qrMissStreak >= 3 && typeof collector.reopenLoginPage === 'function') {
                const back = await collector.reopenLoginPage().catch(() => false);
                if (back) qrMissStreak = 0;
              }
            };
            refreshQr(3).catch(() => { /* ignore */ });
            qrRefreshTimer = setInterval(() => {
              if (loginTask.finishedAt) return;
              refreshQr(2).catch(() => { /* ignore */ });
            }, QR_REFRESH_MS);
          }
          logger.info(`任务${taskId} ${platformName}登录页已就绪: ${qrResult.qrPath}`);
        } else {
          loginTask.status = 'failed';
          loginTask.message = '登录页生成失败';
          loginTask.finishedAt = new Date().toISOString();
          await collector.close().catch(() => {});
          return;
        }
        // 第2步：等待用户完成登录（最长5分钟）
        // 注意：waitForLogin 内部检测到登录成功后会自动保存 cookie，无需用户点击任何按钮
        const loginResult = await collector.waitForLogin(taskId);
        if (loginResult.success) {
          // 兜底校验：只有 Cookie 真正有效落盘才算登录成功（修复"登录假成功"）
          const cookieOk = await verifyCookieSaved(taskId, platformName);
          if (cookieOk) {
            loginTask.status = 'success';
            loginTask.message = '登录成功，Cookie已自动保存';
            logger.info(`任务${taskId} ${platformName}登录成功`);
          } else {
            loginTask.status = 'failed';
            loginTask.message = `登录流程已完成，但${platformName}Cookie未有效保存（可能已过期），请点击"清除登录态并重新扫码"`;
          }
          loginTask.finishedAt = new Date().toISOString();
        } else if (!loginTask.userConfirmed) {
          // 用户已点过「手动确认登录」并保存成功时，不要被自动检测结果覆盖
          // （风鸟等平台的 DOM 检测不准，容易把已登录误判成失败）
          loginTask.status = 'failed';
          loginTask.message = loginResult.error || '登录失败';
          loginTask.finishedAt = new Date().toISOString();
          logger.warn(`任务${taskId} ${platformName}登录失败: ${loginResult.error}`);
        } else {
          logger.info(`任务${taskId} ${platformName}自动检测未通过，但用户已手动确认登录，保持成功状态`);
        }
      } catch (err) {
        loginTask.status = 'failed';
        loginTask.message = err.message;
        loginTask.finishedAt = new Date().toISOString();
        logger.error(`任务${taskId} 登录异常: ${err.message}`, err);
      } finally {
        // 停掉二维码重截定时器，避免任务结束后继续截图
        if (qrRefreshTimer) {
          clearInterval(qrRefreshTimer);
          qrRefreshTimer = null;
        }
        // 浏览器保活：登录流程结束后不立即关闭，保活窗口内「服务器浏览器画面」仍可看可操作
        // （处理风控页/验证码/弹窗），到点自动关闭；用户也可点「关闭浏览器」立即关闭。
        // 任务状态保留供前端查询，页面刷新后仍能看到"已登录/失败"结果与实时画面。
        setTimeout(async () => {
          try {
            await collector.close();
            logger.info(`任务${taskId} 登录浏览器已关闭（保活 ${Math.round(LOGIN_KEEP_ALIVE_MS / 60000)} 分钟已到）`);
          } catch (e) {
            // ignore
          }
        }, LOGIN_KEEP_ALIVE_MS);
      }
    })();

    return res.json({ success: true, message: '登录任务已启动', status: 'running' });
  } catch (err) {
    logger.error(`启动登录任务失败: taskId=${taskId}, ${err.message}`);
    return res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * GET /api/capture/login/:taskId/status
 * 查询登录任务状态和二维码路径
 * 平台隔离：通过 query 参数 platform 定位对应平台的登录任务（login_${platform}）
 */
router.get('/login/:taskId/status', (req, res) => {
  const platform = req.query.platform || 'qcc';
  const taskId = `login_${platform}`;
  const loginTask = loginTasks.get(taskId);
  if (!loginTask) {
    // 没有进行中的登录任务属于正常情况（服务重启/任务到期清理后前端仍可能来查一次），
    // 返回 status='none' 而不是 404：404 会让浏览器控制台每次都报一条红色错误
    return res.json({
      success: true,
      taskId,
      platform,
      status: 'none',
      message: '当前没有进行中的登录任务'
    });
  }
  return res.json({
    success: true,
    taskId: loginTask.taskId,
    platform: loginTask.platform,
    status: loginTask.status,
    message: loginTask.message,
    qrImageUrl: loginTask.qrImagePath ? `/api/capture/qrcode/${taskId}` : null,
    // 二维码版本（每次重截更新），前端据此刷新图片，避免一直显示过期二维码
    qrVersion: loginTask.qrVersion || null,
    // 浏览器当前页面地址（排障用：能直接看出扫码后跳到了哪个页面、是否落到 404）
    pageUrl: (loginTask.collector && loginTask.collector.page && !loginTask.collector.page.isClosed())
      ? loginTask.collector.page.url() : null,
    createdAt: loginTask.createdAt,
    finishedAt: loginTask.finishedAt
  });
});

/**
 * POST /api/capture/login/:taskId/confirm
 * 用户手动确认登录成功：让 Playwright 浏览器立即保存当前 Cookie
 * 用于解决风鸟登录后 DOM 检测不准导致自动 waitForLogin 超时的问题
 *
 * body: { platform?: 'qcc'|'fnb'|'tyc'|'aqc' }  默认 qcc
 * 平台隔离：通过 body 里的 platform 定位对应平台的登录任务（login_${platform}）
 */
router.post('/login/:taskId/confirm', async (req, res) => {
  const { platform = 'qcc' } = req.body || {};
  if (rejectUnsupportedPlatform(res, platform)) return;
  const taskId = `login_${platform}`;
  const loginTask = loginTasks.get(taskId);
  if (!loginTask) {
    return res.status(404).json({ success: false, message: '登录任务不存在' });
  }
  if (loginTask.status !== 'running') {
    return res.json({ success: true, message: `任务已结束(${loginTask.status})`, status: loginTask.status });
  }
  try {
    // 用对应的 collector 实例保存当前浏览器 Cookie
    // 这里需要把 login 闭包里的 collector 暴露出来，所以放在 loginTask 上
    const collector = loginTask.collector;
    if (!collector || !collector.context) {
      return res.status(400).json({ success: false, message: '浏览器未启动或已关闭，请重新启动登录' });
    }
    const cookies = await collector.context.cookies();
    if (!cookies || cookies.length === 0) {
      return res.status(400).json({ success: false, message: '浏览器中没有 Cookie，请确认已登录' });
    }
    const saved = await cookieManager.saveCookie(taskId, cookies);
    // 修复"登录假成功"：saveCookie 返回 false 表示 Cookie 全部已过期、未落盘，此时不能算登录成功
    if (!saved) {
      logger.warn(`任务${taskId} 手动确认登录失败：Cookie全部已过期，未保存`);
      return res.status(400).json({
        success: false,
        message: `捕获到的${platformNameOf(platform)}Cookie已全部过期，未保存成功，请重新扫码登录`
      });
    }
    // 标记登录任务为成功
    loginTask.status = 'success';
    loginTask.message = `手动确认登录成功，Cookie已保存（共${cookies.length}条）`;
    loginTask.finishedAt = new Date().toISOString();
    loginTask.userConfirmed = true;
    logger.info(`任务${taskId} 用户手动确认登录成功，Cookie已保存（共${cookies.length}条）`);
    // 不立即关闭浏览器：与自动登录成功保持一致，浏览器进入保活窗口（还可在画面里继续操作），
    // 到点自动关闭，或在页面上点「关闭浏览器」立即关闭。
    return res.json({
      success: true,
      message: `登录成功，Cookie已保存（共${cookies.length}条）`,
      status: 'success',
      cookieCount: cookies.length
    });
  } catch (err) {
    logger.error(`任务${taskId} 手动确认登录失败: ${err.message}`, err);
    return res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * POST /api/capture/login/:taskId/reset
 * 清除指定平台的登录态并强制重新扫码登录。
 * 用途：部署在服务器上时，无需登录服务器桌面/手工删除目录即可重置扫码登录。
 * 清除内容：① 平台Cookie文件（login_{platform}.json）② 全局Cookie文件（0.json）
 *          ③ 该平台浏览器持久化目录（含目录内登录态）
 * body: { platform?: 'qcc'|'fnb'|'tyc'|'aqc' }  默认 qcc
 */
router.post('/login/:taskId/reset', async (req, res) => {
  const { platform = 'qcc' } = req.body || {};
  const taskId = `login_${platform}`;
  try {
    // 白名单校验：只允许预置平台，禁止用外部输入拼接目录路径
    const dirName = PLATFORM_PROFILE_DIRS[platform];
    if (!dirName) {
      return res.status(400).json({ success: false, message: `不支持的平台: ${platform}` });
    }
    const platformName = platformNameOf(platform);
    const browserDir = path.resolve('./storage/browser-data', dirName);

    // 1) 该平台存在运行中/等待人工验证的采集任务时禁止重置，避免采集中途登录态被清空
    for (const [, t] of captureTasks.entries()) {
      if (t.platform === platform && (t.status === 'running' || t.status === 'blocked')) {
        return res.status(409).json({ success: false, message: `${platformName}有采集任务正在使用浏览器（采集中或等待人工验证），请先处理该任务再重置登录态` });
      }
    }

    // 2) 关闭该平台进行中的登录任务及其浏览器，释放目录占用
    const existing = loginTasks.get(taskId);
    if (existing) {
      if (existing.collector) {
        await existing.collector.close().catch(() => {});
      }
      loginTasks.delete(taskId);
      logger.info(`任务${taskId} 重置：已关闭进行中的登录浏览器`);
    }

    // 3) 删除该平台Cookie文件 + 全局Cookie文件
    await cookieManager.deleteCookie(taskId);
    await cookieManager.deleteCookie(0);

    // 4) 只删「登录态相关」文件（Cookies/本地存储/会话存储），不删整个 profile 目录。
    //    整个目录动辄几百 MB，删除要 26~65 秒；而登录态只落在这几个位置，删完是秒级，
    //    同时保留 profile 其余内容，反爬指纹的积累不会被清掉。
    const CLEAR_TARGETS = [
      'Default/Network/Cookies',
      'Default/Network/Cookies-journal',
      'Default/Cookies',            // 旧版 Chromium 目录结构
      'Default/Local Storage',
      'Default/Session Storage'
    ];
    const failedTargets = [];
    let hit = 0;
    for (const rel of CLEAR_TARGETS) {
      const target = path.join(browserDir, rel);
      if (!fs.existsSync(target)) continue;
      hit++;
      try {
        fs.rmSync(target, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 });
        logger.info(`任务${taskId} 重置：已清除 ${rel}`);
      } catch (e) {
        failedTargets.push(rel);
        logger.warn(`任务${taskId} 重置：清除 ${rel} 失败: ${e.message}`);
      }
    }
    if (failedTargets.length > 0) {
      return res.status(500).json({
        success: false,
        message: `清除登录态失败（${failedTargets.join('、')} 被占用），请确认该平台采集未在运行后重试`
      });
    }

    logger.info(`任务${taskId} 重置：${platformName}登录态已清除（命中${hit}项），dir=${browserDir}`);
    return res.json({ success: true, message: `${platformName}登录态已清除，请重新扫码登录` });
  } catch (err) {
    logger.error(`任务${taskId} 重置登录态异常: ${err.message}`, err);
    return res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * GET /api/capture/login-state?platform=qcc
 * 查询平台「已保存的登录态」（读磁盘 Cookie 文件，不依赖内存里的登录任务）
 *
 * 用途：登录页重新进入/切换平台/服务重启后，仍能看到该平台真实的登录状态，
 *       而不是永远显示"等待登录"（内存 loginTasks 会随服务重启、任务清理而消失）。
 * 返回：{ success, platform, loggedIn, cookieCount, savedAt }
 */
router.get('/login-state', async (req, res) => {
  const platform = req.query.platform || 'qcc';
  if (rejectUnsupportedPlatform(res, platform)) return;
  const taskId = `login_${platform}`;
  try {
    // 只读该平台自己的 Cookie 文件（不走全局 Cookie 兜底），已过期视为未登录
    const state = await cookieManager.getPlatformLoginState(taskId);
    if (!state) {
      return res.json({
        success: true,
        platform,
        loggedIn: false,
        cookieCount: 0,
        savedAt: null,
        message: `尚未保存${platformNameOf(platform)}登录态`
      });
    }
    return res.json({
      success: true,
      platform,
      loggedIn: true,
      cookieCount: (state.cookies || []).length,
      savedAt: state.savedAt,
      message: `${platformNameOf(platform)}登录态有效`
    });
  } catch (err) {
    logger.error(`查询登录态失败: platform=${platform}, ${err.message}`);
    return res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * 取该平台「进行中的登录任务」及其浏览器页面。
 * 实时画面 / 远程操作只在登录任务存活期内可用（登录任务最长 7 分钟、完成后 30 分钟清理），
 * 避免把"能控制浏览器"的能力长期暴露。
 */
/**
 * 登录流程结束后浏览器的保活时长（毫秒）。
 * 用途：登录成功/失败后浏览器不立即关闭，这段时间内「服务器浏览器画面」仍可看可操作
 *      （处理风控页、验证码、弹窗），到点自动关闭，用户也可点「关闭浏览器」立即关闭。
 * 默认 5 分钟，可用环境变量 LOGIN_KEEP_ALIVE_MS 调整（下限 1 分钟）。
 */
const LOGIN_KEEP_ALIVE_MS = Math.max(60000, Number(process.env.LOGIN_KEEP_ALIVE_MS) || 5 * 60 * 1000);

function getRunningLoginSession(platform) {
  const loginTask = loginTasks.get(`login_${platform}`);
  if (!loginTask) return null;
  // 登录流程进行中（running）→ 可用；
  // 已结束（success/failed）→ 保活窗口内浏览器仍开着，同样可用（否则前端永远看不到可操作的画面）
  if (loginTask.status !== 'running') {
    if (!loginTask.finishedAt) return null;
    if (Date.now() > new Date(loginTask.finishedAt).getTime() + LOGIN_KEEP_ALIVE_MS) return null;
  }
  const collector = loginTask.collector;
  const page = collector && collector.page;
  if (!page || page.isClosed()) return null;
  return { loginTask, page };
}

/**
 * 找出该平台"还活着的采集浏览器"。
 * 为什么需要：采集浏览器以前完全不可见（headless + 不进投屏 + 失败即关闭），
 * 用户撞上风控/验证页时前端什么都看不到，而做远程浏览器的目的就是自己过风控
 *（2026-09-22 用户明确要求）。现在采集任务撞风控会挂成 blocked 并保持浏览器存活，
 * 这个函数把它交给实时投屏/远程操作接口。
 * @returns {{task:Object, captureId:string, page:Object}|null}
 */
function getActiveCaptureSession(platform) {
  let fallback = null;
  for (const [captureId, t] of captureTasks.entries()) {
    if (t.platform !== platform) continue;
    if (t.status !== 'running' && t.status !== 'blocked') continue;
    const page = t.collector && t.collector.page;
    if (!page || page.isClosed()) continue;
    // 优先返回"停在验证页等人工处理"的那个：那才是用户要看的画面
    if (t.status === 'blocked') return { task: t, captureId, page };
    if (!fallback) fallback = { task: t, captureId, page };
  }
  return fallback;
}

/**
 * 实时画面 / 远程操作要操作的会话：登录浏览器优先（登录流程），否则用采集任务浏览器。
 * @returns {{loginTask?:Object, capture?:Object, page:Object, source:'login'|'capture'}|null}
 */
function getLiveSession(platform) {
  const loginSession = getRunningLoginSession(platform);
  if (loginSession) return { loginTask: loginSession.loginTask, page: loginSession.page, source: 'login' };
  const captureSession = getActiveCaptureSession(platform);
  if (captureSession) return { capture: captureSession, page: captureSession.page, source: 'capture' };
  return null;
}

/**
 * 采集任务正在"自动干活"时禁止远程操作（会互相打断）；
 * 停在验证页等人工处理（blocked）时必须允许 —— 否则用户永远没法自己过风控。
 */
function isInteractBlockedByCapture(session, platform) {
  if (session && session.capture) return session.capture.task.status === 'running';
  return isCaptureRunning(platform);
}

/** 该平台是否有采集任务在跑（采集中禁止远程操作，避免互相打断） */
function isCaptureRunning(platform) {
  for (const [, t] of captureTasks.entries()) {
    if (t.platform === platform && (t.status === 'running' || t.status === 'pending')) return true;
  }
  return false;
}

// 远程操作可用的按键白名单（避免被当通用键盘注入）
const ALLOWED_KEYS = new Set([
  'Enter', 'Escape', 'Tab', 'Backspace', 'Delete', 'Space',
  'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End', 'PageUp', 'PageDown'
]);

// 实时画面截图目录（每个平台只保留最新一张，不占用磁盘）
const LIVE_DIR = path.resolve('./storage/live');

/**
 * GET /api/capture/live/:platform
 * 取登录浏览器当前页面的实时画面（供「采集平台登录」页做远程操作）。
 * 返回 viewport 尺寸：前端按"截图的显示宽度 : 真实视口宽度"换算点击坐标，否则点不准。
 */
router.get('/live/:platform', async (req, res) => {
  const { platform } = req.params;
  if (rejectUnsupportedPlatform(res, platform)) return;
  const session = getLiveSession(platform);
  if (!session) {
    // 用 200 + success=false 而不是 409：前端进页面/切平台时必然会查一次，
    // 409 会让浏览器控制台每次都记一条红色错误（正常情况不该报错）
    return res.json({ success: false, message: '当前没有可操作的浏览器：点「打开采集浏览器」打开登录浏览器；若该平台有采集任务停在验证页，画面会自动接过来' });
  }
  try {
    if (!fs.existsSync(LIVE_DIR)) fs.mkdirSync(LIVE_DIR, { recursive: true });
    const shotPath = path.join(LIVE_DIR, `${platform}.png`);
    const tmpPath = path.join(LIVE_DIR, `${platform}.tmp.png`);
    await session.page.screenshot({ path: tmpPath, timeout: 8000 });
    fs.renameSync(tmpPath, shotPath);
    const view = await session.page.evaluate(() => ({ w: window.innerWidth, h: window.innerHeight }))
      .catch(() => ({ w: 1280, h: 900 }));
    const info = await session.page.evaluate(() => ({ title: document.title || '', url: location.href }))
      .catch(() => ({ title: '', url: '' }));
    return res.json({
      success: true,
      platform,
      version: Date.now(),
      imageUrl: `/api/capture/live-shot/${platform}`,
      viewport: { width: view.w, height: view.h },
      pageTitle: info.title,
      pageUrl: info.url
    });
  } catch (err) {
    logger.error(`获取实时画面失败: platform=${platform}, ${err.message}`);
    return res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * GET /api/capture/live-shot/:platform
 * 返回实时画面图片本身（给 <img :src> 用，前端带 ?t=版本号 防缓存）
 */
router.get('/live-shot/:platform', (req, res) => {
  const { platform } = req.params;
  if (rejectUnsupportedPlatform(res, platform)) return;
  const shotPath = path.join(LIVE_DIR, `${platform}.png`);
  if (!fs.existsSync(shotPath)) {
    return res.status(404).json({ success: false, message: '暂无实时画面，请点击「刷新画面」' });
  }
  return res.sendFile(shotPath);
});

/**
 * GET /api/capture/screencast/:platform
 * 登录浏览器「实时投屏」（SSE / text/event-stream）。
 * 把右侧画面从"每 4 秒一张截图"升级为接近实时的连续画面（CDP Page.startScreencast：页面一重绘就推帧）。
 * 事件（每条 data 都是 JSON）：
 *   { type:'wait', message }     ← 登录任务在跑、浏览器还在启动（随后就绪即开始推帧，不需前端重连）
 *   { type:'meta', pageTitle, pageUrl, viewport:{width,height} }
 *   { type:'frame', data(base64 jpeg), w, h }
 *   { type:'error', message }    ← 没有登录任务 / 浏览器已关闭，发一条后立即结束
 * 无会话不用 4xx：EventSource 读不到错误响应体，前端无法给出中文提示。
 * 点击/输入仍走 POST /interact（含 action='mouse'）。
 */
router.get('/screencast/:platform', async (req, res) => {
  const { platform } = req.params;
  if (rejectUnsupportedPlatform(res, platform)) return;
  let session = getLiveSession(platform);

  res.setHeader('Content-Type', 'text/event-stream; charset=utf-8');
  res.setHeader('Cache-Control', 'no-cache, no-transform');
  res.setHeader('Connection', 'keep-alive');
  // nginx 等反向代理默认缓冲响应体，一缓冲投屏就不实时了 → 显式要求不缓冲
  res.setHeader('X-Accel-Buffering', 'no');
  if (typeof res.flushHeaders === 'function') res.flushHeaders();

  let closed = false;
  const send = (obj) => {
    try { res.write(`data: ${JSON.stringify(obj)}\n\n`); } catch (e) { /* 连接已断开 */ }
  };
  req.on('close', () => { closed = true; });

  // 浏览器正在启动时不要直接报错：保持连接等一下（最长 20 秒），一就绪立刻开始推帧。
  // 这样前端点完「打开采集浏览器」就能马上连上，画面在浏览器起来的瞬间出现，不用重连、也不用白等。
  if (!session) {
    const loginTask = loginTasks.get(`login_${platform}`);
    const starting = !!(loginTask && loginTask.status === 'running' && !loginTask.finishedAt);
    if (starting) {
      send({ type: 'wait', message: '正在启动浏览器…' });
      const deadline = Date.now() + 20000;
      while (!closed && Date.now() < deadline) {
        await new Promise((r) => setTimeout(r, 250));
        session = getLiveSession(platform);
        if (session) break;
        const t = loginTasks.get(`login_${platform}`);
        if (!t || t.status !== 'running' || t.finishedAt) break;
      }
    }
  }

  if (!session) {
    send({ type: 'error', message: '当前没有可操作的浏览器：点「打开采集浏览器」打开登录浏览器；若该平台有采集任务停在验证页，画面会自动接过来' });
    return res.end();
  }

  let cdp = null;
  let heartbeat = null;
  let streamCleaned = false;
  const cleanup = async () => {
    if (streamCleaned) return;
    streamCleaned = true;
    closed = true;
    if (heartbeat) clearInterval(heartbeat);
    heartbeat = null;
    if (cdp) {
      await cdp.send('Page.stopScreencast').catch(() => {});
      await cdp.detach().catch(() => {});
      cdp = null;
    }
  };
  req.on('close', cleanup);

  // 视口尺寸 = 页面 CSS 像素，前端据此把画布上的点击换算成浏览器坐标（换算错了就会点偏）
  const viewport = await session.page.evaluate(() => ({ width: window.innerWidth, height: window.innerHeight }))
    .catch(() => ({ width: 1280, height: 800 }));

  const sendMeta = async () => {
    try {
      if (closed) return;
      const page = session.page;
      if (!page || page.isClosed()) return;
      const info = await page.evaluate(() => ({ title: document.title || '', url: location.href }))
        .catch(() => ({ title: '', url: '' }));
      send({ type: 'meta', pageTitle: info.title, pageUrl: info.url, viewport });
    } catch (e) { /* ignore */ }
  };

  try {
    await sendMeta();
    cdp = await session.page.context().newCDPSession(session.page);
    let lastAckAt = 0;
    cdp.on('Page.screencastFrame', async (evt) => {
      send({ type: 'frame', data: evt.data, w: (evt.metadata && evt.metadata.deviceWidth) || viewport.width, h: (evt.metadata && evt.metadata.deviceHeight) || viewport.height });
      // 控帧率：Chromium 只有收到上一帧回执（ack）才会编码下一帧，
      // 因此"延迟回执"能把编码+传输压到约 15 帧/秒（重绘密集的页面不会把 Node/CPU 打满）。
      const now = Date.now();
      const wait = Math.max(0, 66 - (now - lastAckAt));
      lastAckAt = now + wait;
      const ack = () => { if (!closed && cdp) cdp.send('Page.screencastFrameAck', { sessionId: evt.sessionId }).catch(() => {}); };
      if (wait > 0) setTimeout(ack, wait); else ack();
    });
    await cdp.send('Page.startScreencast', {
      format: 'jpeg',
      // 质量/尺寸按"能看清二维码与文字、又不至于每帧过大"取：1440→1280、70→60，
      // 单帧从约 80KB 降到约 50KB（公网/nginx 场景带宽更友好）
      quality: 60,
      maxWidth: 1280,
      maxHeight: 800,
      everyNthFrame: 1
    });
    logger.info(`实时投屏已开启: platform=${platform}, viewport=${viewport.width}x${viewport.height}`);
    // SSE 长连接保活 + 周期性带一次页面标题（前端因此不需要再轮询 /live）
    heartbeat = setInterval(() => {
      if (closed) return;
      // 浏览器被关掉 / 保活到期后页面对象会失效：必须主动收流并告知前端，
      // 否则前端一直显示最后一帧（用户看到"卡死的画面"，以为系统卡了）
      const page = session.page;
      if (!page || page.isClosed()) {
        send({ type: 'error', message: '服务器浏览器已关闭，请重新点「打开采集浏览器」' });
        cleanup();
        res.end();
        return;
      }
      try { res.write(': keep-alive\n\n'); } catch (e) { /* ignore */ }
      sendMeta();
    }, 10000);
  } catch (err) {
    logger.error(`实时投屏开启失败: platform=${platform}, ${err.message}`);
    send({ type: 'error', message: '实时画面不可用：' + err.message });
    await cleanup();
    res.end();
  }
});

/**
 * POST /api/capture/interact/:platform
 * 在登录浏览器里执行一步操作（远程操作浏览器：处理验证码/关弹窗/输手机号验证码等）。
 * body: { action: 'mouse'|'click'|'type'|'key'|'scroll'|'reload'|'back'|'dismiss', x, y, text, key, deltaY, type, button }
 *   action='mouse'：{ type:'down'|'move'|'up', x, y, button } —— 实时投屏用，可组合出点击与拖拽滑块
 * 说明：坐标必须是"浏览器视口坐标"（CSS 像素，由实时投屏的 viewport 换算），不是页面上的像素。
 */
router.post('/interact/:platform', async (req, res) => {
  const { platform } = req.params;
  if (rejectUnsupportedPlatform(res, platform)) return;
  const session = getLiveSession(platform);
  if (!session) {
    return res.json({ success: false, message: '当前没有可操作的浏览器：点「打开采集浏览器」打开登录浏览器；若该平台有采集任务停在验证页，画面会自动接过来' });
  }
  // 采集任务正在自动干活时禁止远程操作；停在验证页等人工处理时必须放行（远程浏览器就是为了过风控）
  if (isInteractBlockedByCapture(session, platform)) {
    // 同理用 200 + success=false，避免控制台红字
    return res.json({ success: false, message: '该平台正在采集中，请等它停在验证页或采集结束后再操作浏览器' });
  }
  const { action, x, y, text, key, deltaY } = req.body || {};
  // 鼠标动作类型（action='mouse' 时用）：click=一次完成点击 / down=按下 / move=移动 / up=抬起。
  // 前端实时投屏把 down+move*+up 组合成"拖拽滑块"，轻点则直接发 click（一次往返，跟手）。
  const MOUSE_TYPES = new Set(['click', 'down', 'move', 'up']);
  try {
    if (action === 'mouse') {
      const px = Number(x);
      const py = Number(y);
      if (!Number.isFinite(px) || !Number.isFinite(py) || px < 0 || py < 0 || px > 10000 || py > 10000) {
        return res.status(400).json({ success: false, message: '鼠标坐标不合法' });
      }
      const mouseType = String(req.body.type || '');
      if (!MOUSE_TYPES.has(mouseType)) {
        return res.status(400).json({ success: false, message: `不支持的鼠标动作: ${mouseType}` });
      }
      const button = req.body.button === 'right' ? 'right' : 'left';
      // click = 一次请求完成 move+down+up（前端"轻点"走这条，省掉一半往返，点起来才跟手）
      if (mouseType === 'click') {
        await session.page.mouse.click(px, py, { button });
      } else {
        await session.page.mouse.move(px, py);
        if (mouseType === 'down') await session.page.mouse.down({ button });
        if (mouseType === 'up') await session.page.mouse.up({ button });
      }
      logger.info(`远程操作(${platform}): 鼠标 ${mouseType} x=${Math.round(px)} y=${Math.round(py)}`);
    } else if (action === 'click') {
      const px = Number(x);
      const py = Number(y);
      if (!Number.isFinite(px) || !Number.isFinite(py) || px < 0 || py < 0 || px > 10000 || py > 10000) {
        return res.status(400).json({ success: false, message: '点击坐标不合法' });
      }
      await session.page.mouse.click(px, py);
      logger.info(`远程操作(${platform}): 点击 x=${Math.round(px)} y=${Math.round(py)}`);
    } else if (action === 'type') {
      const t = typeof text === 'string' ? text : '';
      if (!t.trim()) return res.status(400).json({ success: false, message: '请输入要发送的内容' });
      if (t.length > 200) return res.status(400).json({ success: false, message: '输入内容过长（最多 200 字符）' });
      // delay: 0 —— 交互输入要跟手；原来 60ms/字符 会让 11 位手机号多花 0.66 秒
      await session.page.keyboard.type(t, { delay: 0 });
      if (key === 'Enter') await session.page.keyboard.press('Enter');
      logger.info(`远程操作(${platform}): 输入${t.length}个字符${key === 'Enter' ? '并回车' : ''}`);
    } else if (action === 'key') {
      if (!ALLOWED_KEYS.has(String(key))) {
        return res.status(400).json({ success: false, message: `不支持的按键: ${key}` });
      }
      await session.page.keyboard.press(String(key));
      logger.info(`远程操作(${platform}): 按键 ${key}`);
    } else if (action === 'scroll') {
      const d = Number(deltaY) || 600;
      await session.page.mouse.wheel(0, Math.max(-3000, Math.min(3000, d)));
      logger.info(`远程操作(${platform}): 滚动 ${d}`);
    } else if (action === 'reload') {
      await session.page.reload({ waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
      logger.info(`远程操作(${platform}): 刷新页面`);
    } else if (action === 'back') {
      await session.page.goBack({ waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
      logger.info(`远程操作(${platform}): 后退`);
    } else if (action === 'dismiss') {
      // 关弹窗：先按 Esc，再尝试点常见的关闭按钮
      await session.page.keyboard.press('Escape').catch(() => {});
      const hit = await session.page.evaluate(() => {
        const sels = ['.close', '.close-btn', '.el-dialog__headerbtn', '.dialog-close', '[class*="close"]'];
        for (const s of sels) {
          const el = document.querySelector(s);
          if (el && el.offsetParent !== null) { el.click(); return s; }
        }
        return '';
      }).catch(() => '');
      logger.info(`远程操作(${platform}): 关闭弹窗（Esc${hit ? ' + 点击 ' + hit : ''}）`);
    } else {
      return res.status(400).json({ success: false, message: `不支持的操作: ${action}` });
    }
    // 不再插入固定等待：每个动作后硬等会把交互延迟直接放大（原来 400ms），
    // 画面变化由实时投屏自然推帧（cdp screencast 会在重绘时立刻出帧）。
    return res.json({ success: true, message: '操作已执行', version: Date.now() });
  } catch (err) {
    logger.error(`远程操作失败(${platform}, ${action}): ${err.message}`);
    return res.status(500).json({ success: false, message: '操作失败：' + err.message });
  }
});

/**
 * POST /api/capture/browser/:platform/close
 * 手动关闭该平台的登录浏览器（保活期内可用），关闭后实时画面立即不可用。
 * 用途：在实时画面里处理完风控/验证码后，不必等保活到点自动关闭。
 */
router.post('/browser/:platform/close', async (req, res) => {
  const { platform } = req.params;
  if (rejectUnsupportedPlatform(res, platform)) return;
  const taskId = `login_${platform}`;
  if (isCaptureRunning(platform)) {
    return res.json({ success: false, message: '该平台正在采集，请等采集结束再操作浏览器' });
  }
  const session = getLiveSession(platform);
  if (!session) {
    return res.json({ success: false, message: '浏览器未打开' });
  }
  if (session.source === 'capture') {
    // 这个浏览器在给采集任务用（停在验证页等人工处理），关掉就等于把用户的验证现场毁了
    return res.json({ success: false, message: '这个浏览器正被采集任务使用（等待人工验证），不能关闭；请在画面上完成验证后点【继续采集】' });
  }
  try {
    await session.loginTask.collector.close().catch(() => {});
    logger.info(`任务${taskId} 用户手动关闭登录浏览器`);
    return res.json({ success: true, message: '浏览器已关闭' });
  } catch (err) {
    logger.error(`关闭登录浏览器失败: platform=${platform}, ${err.message}`);
    return res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * GET /api/capture/qrcode/:taskId
 * 获取企查查登录二维码图片
 */
router.get('/qrcode/:taskId', async (req, res) => {
  const { taskId } = req.params;
  try {
    const qrDir = path.resolve('./storage/qrcodes');
    const qrPath = path.join(qrDir, `${taskId}_login.png`);
    if (!fs.existsSync(qrPath)) {
      return res.status(404).json({ success: false, message: '二维码图片不存在，请先调用 POST /api/capture/login/:taskId 启动扫码登录' });
    }
    return res.sendFile(qrPath);
  } catch (err) {
    logger.error(`获取二维码失败: taskId=${taskId}, ${err.message}`);
    return res.status(500).json({ success: false, message: err.message });
  }
});

/**
 * POST /api/capture/save-cookie
 * 保存Cookie
 * body: { taskId, cookies }
 */
router.post('/save-cookie', async (req, res) => {
  try {
    const { taskId, cookies } = req.body || {};
    if (!taskId) {
      return res.status(400).json({ success: false, message: '缺少参数 taskId' });
    }
    if (!cookies) {
      return res.status(400).json({ success: false, message: '缺少参数 cookies' });
    }
    const ok = await cookieManager.saveCookie(taskId, cookies);
    if (ok) {
      return res.json({ success: true, message: 'Cookie保存成功' });
    }
    return res.status(500).json({ success: false, message: 'Cookie保存失败' });
  } catch (err) {
    logger.error(`保存Cookie失败: ${err.message}`);
    return res.status(500).json({ success: false, message: err.message });
  }
});

module.exports = router;
