const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const logger = require('../utils/logger');
const cookieManager = require('./cookieManager');
const { killBrowsersUsingProfile, firstLineOf } = require('../utils/browserProfile');

// 天眼查采集器
// 登录方式：扫码登录（登录页自带二维码，截图给前端展示）
// 登录态检测：正向判断（看到"登录/注册"=未登录；看到"我的天眼查/退出"=已登录；不确定=未登录）
class TycCollector {
  constructor(options = {}) {
    this.context = null;
    this.page = null;
    this.captureMode = options.captureMode || false;
    // 无头开关覆盖：登录任务必须"可见窗口"（用户要在弹出的浏览器里扫码），
    // 但登录任务仍需与采集任务共用同一个持久化目录（captureMode=true），
    // 故把"用哪个目录"和"是否无头"解耦，由调用方显式指定 headless。
    this.headlessOverride = typeof options.headless === 'boolean' ? options.headless : null;
  }

  async init() {
    if (this.context) {
      logger.warn('天眼查浏览器已存在，跳过初始化');
      return;
    }
    const dirName = this.captureMode ? 'browser-data/tyc-capture' : 'browser-data/tyc';
    const userDataDir = path.resolve(__dirname, `../../storage/${dirName}`);
    if (!fs.existsSync(userDataDir)) fs.mkdirSync(userDataDir, { recursive: true });

    const lockFile = path.join(userDataDir, 'SingletonLock');
    try {
      if (fs.existsSync(lockFile)) fs.unlinkSync(lockFile);
    } catch (e) {
      logger.warn(`清理SingletonLock失败（可忽略）: ${e.message}`);
    }

    // 同企查查：采集任务默认无头（不弹窗、不会被误关），登录任务默认**可见窗口**供扫码。
    const headless = this.headlessOverride !== null
      ? this.headlessOverride
      : (this.captureMode ? process.env.CAPTURE_HEADLESS !== 'false' : process.env.HEADLESS === 'true');
    logger.info(`天眼查启动持久化浏览器，userDataDir=${userDataDir}, headless=${headless}, captureMode=${this.captureMode}`);
    const launchOptions = {
      headless,
      // slowMo 默认 0（同企查查）：慢动作只用于人工观察，开着会让每次交互硬等数百毫秒
      slowMo: parseInt(process.env.SLOW_MO || '0'),
      no_viewport: true,
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
      args: [
        '--disable-blink-features=AutomationControlled',
        '--disable-infobars',
        '--disable-dev-shm-usage',
        '--start-maximized'
      ]
    };
    try {
      this.context = await chromium.launchPersistentContext(userDataDir, { ...launchOptions, channel: 'msedge' });
      logger.info('天眼查使用系统 Edge 浏览器启动（反爬指纹更真实）');
    } catch (e) {
      // 常见原因：上一次的浏览器进程残留占住了这个持久化目录（服务重启/异常退出留下的孤儿进程），
      // 新实例会立刻退出（Playwright: Target page, context or browser has been closed，Edge exitCode=21）。
      // 服务器上没人能进桌面手工处理，这里自动清理占用该目录的残留浏览器进程后重试一次。
      const killed = killBrowsersUsingProfile(userDataDir);
      logger.warn(`系统 Edge 启动失败（${firstLineOf(e.message)}），已清理 ${killed} 个占用该目录的残留浏览器进程${killed > 0 ? '，重试一次' : ''}`);
      if (killed > 0) {
        await new Promise((r) => setTimeout(r, 1500));
        try {
          this.context = await chromium.launchPersistentContext(userDataDir, { ...launchOptions, channel: 'msedge' });
          logger.info('清理残留进程后系统 Edge 启动成功');
        } catch (e2) {
          logger.warn(`重试系统 Edge 仍失败（${firstLineOf(e2.message)}），回退到自带 Chromium`);
          this.context = await chromium.launchPersistentContext(userDataDir, launchOptions);
        }
      } else {
        this.context = await chromium.launchPersistentContext(userDataDir, launchOptions);
      }
    }
    await this.context.addInitScript(() => {
      Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
      Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh', 'en'] });
      Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
      window.chrome = window.chrome || { runtime: {} };
    });
    this.context.grantPermissions(['notifications']).catch(() => {});
    this.page = this.context.pages()[0] || await this.context.newPage();
    this.page.setDefaultTimeout(parseInt(process.env.DEFAULT_TIMEOUT || '30000'));
    logger.info('天眼查浏览器初始化完成（持久化模式）');
  }

  async loadLoginState(taskId) {
    const cookies = await cookieManager.loadCookieArray(taskId, 'tyc');
    if (cookies && cookies.length) {
      await this.context.addCookies(cookies);
      logger.info(`任务${taskId} 天眼查Cookie加载成功`);
      return true;
    }
    logger.warn(`任务${taskId} 无天眼查Cookie，需要先登录`);
    return false;
  }

  // ========== 登录状态检测（双重实现） ==========
  // _checkLoginDom: 纯DOM检测，不做任何页面跳转。用于 waitForLogin 轮询。
  // checkLogin: 先跳转到首页再检测，用于初始化阶段。
  //
  // 严格正向判断 + 反向排除。宁可多扫一次码也不误报已登录。

  // 纯DOM检测（不跳转页面）- 用于 waitForLogin 轮询
  async _checkLoginDom() {
    try {
      if (!this.page || this.page.isClosed()) return false;
      const currentUrl = String(this.page.url());
      if (/\/login/i.test(currentUrl)) return false;
      const txt = await this.page.evaluate(() => (document.body ? document.body.innerText : '')).catch(() => '');
      if (/(扫码登录|二维码|登录\/注册|登录账号|手机登录|验证码)/.test(txt)) return false;
      const hasQr = await this.page.evaluate(() => {
        return !!(document.querySelector('img[src*="login-scan"], img[class*="scan"], [class*="qrcode"], [class*="qr-code"]') ||
          document.querySelector('.scan-hover'));
      }).catch(() => false);
      if (hasQr) return false;
      const hasPositive = await this.page.evaluate(() => {
        const all = document.querySelectorAll('a, button, span, div, [class*="user"], [class*="account"]');
        for (const el of all) {
          const t = (el.textContent || '').trim();
          if (/(退出|我的天眼查|个人中心|账号设置|VIP中心)/.test(t)) return true;
        }
        return false;
      }).catch(() => false);
      return hasPositive;
    } catch (e) {
      logger.warn(`天眼查_checkLoginDom异常: ${e.message}`);
      return false;
    }
  }

  // 判断当前页是否仍处于登录页：只用 URL 判断（天眼查登录页是 /login）。
  // 【重要】不要用页面文案判断——平台登录页正文未必含"扫码登录/二维码"等词，
  // 误判会把用户还没扫完的二维码页跳走。
  async _looksLikeLoginPage() {
    return /login/i.test(String(this.page ? this.page.url() : ''));
  }

  // 带页面跳转的登录检测（用于初始化阶段）
  async checkLogin() {
    try {
      if (!this.page || this.page.isClosed()) return false;
      const currentUrl = String(this.page.url());
      if (/\/login/i.test(currentUrl)) {
        logger.info('天眼查当前在登录页，强制判定为未登录');
        return false;
      }
      await this.page.goto('https://www.tianyancha.com/', { waitUntil: 'domcontentloaded' });
      await this.page.waitForLoadState('networkidle').catch(() => {});
      const result = await this._checkLoginDom();
      logger.info(result ? '天眼查已登录（checkLogin跳转首页检测通过）' : '天眼查未登录');
      return result;
    } catch (e) {
      logger.warn(`天眼查checkLogin异常: ${e.message}`);
      return false;
    }
  }

  async checkCaptcha() {
    const captcha = await this.page.$('.captcha, .verify-wrap, [class*="captcha"], [class*="verify-wrap"], .slider, [class*="slider-verify"]');
    if (captcha) {
      logger.warn('天眼查检测到验证码');
      return true;
    }
    return false;
  }

  // 搜索企业列表（未登录也能拿到列表，登录后更稳定）
  async searchCompanies(keyword, maxPages = 5) {
    const results = [];
    logger.info(`天眼查开始搜索: ${keyword}, 最大页数: ${maxPages}`);

    for (let pageNum = 1; pageNum <= maxPages; pageNum++) {
      const url = pageNum === 1
        ? `https://www.tianyancha.com/search?key=${encodeURIComponent(keyword)}`
        : `https://www.tianyancha.com/search?key=${encodeURIComponent(keyword)}&pn=${pageNum}`;
      await this.page.goto(url, { waitUntil: 'domcontentloaded' });
      await this.page.waitForTimeout(3000);

      if (await this.checkCaptcha()) throw new Error('CAPTCHA_REQUIRED: 天眼查需要人工处理验证码');

      const companies = await this.page.evaluate(() => {
        const seen = new Set();
        // 【诊断·2026-09-23】抓取列表卡片的整行文本（含法人/地址等），用于判定列表页能否前置粗筛地区。
        // 只写入 debugListText 字段、只打日志，不参与任何过滤逻辑（原 rawText 保持空串不变）。
        const pickRowText = (el, name) => {
          let cur = el;
          for (let i = 0; i < 6 && cur; i++) {
            cur = cur.parentElement;
            if (!cur) break;
            const t = (cur.textContent || '').replace(/\s+/g, ' ').trim();
            if (t.length > name.length + 5 && t.length <= 400) return t.slice(0, 300);
          }
          return '';
        };
        return Array.from(document.querySelectorAll('a[href*="/company/"]')).map(a => {
          const name = (a.textContent || '').trim();
          const href = a.href || '';
          if (!name || name.length < 4 || seen.has(href)) return null;
          seen.add(href);
          return { companyName: name, detailUrl: href, rawText: '', debugListText: pickRowText(a, name) };
        }).filter(Boolean);
      });

      logger.info(`天眼查第${pageNum}页采集到 ${companies.length} 家`);
      if (!companies.length) break;
      results.push(...companies.map(c => ({ ...c, searchKeyword: keyword, pageNum })));

      // 翻页：下一页按钮（找不到就停）
      const nextBtn = await this.page.$('button:has-text("下一页"):not([disabled]), a:has-text("下一页"):not([disabled]), [class*="next"]:not([class*="disabled"]):not([class*="prev"])');
      if (!nextBtn) break;
      await this.page.waitForTimeout(parseInt(process.env.CAPTURE_INTERVAL || '8000'));
    }

    logger.info(`天眼查搜索完成: ${keyword}, 共${results.length}家企业`);
    return results;
  }

  // 详情提取（实测未登录也能拿到基础字段，用 innerText 正则，最稳）
  async getCompanyDetail(detailUrl, companyName) {
    if (!detailUrl) {
      logger.warn(`天眼查无 detailUrl，跳过详情采集（companyName=${companyName || '无'}）`);
      return { detailError: '天眼查无 detailUrl，无法采集详情' };
    }
    logger.info(`天眼查访问企业详情: ${detailUrl}`);
    await this.page.goto(detailUrl, { waitUntil: 'domcontentloaded' });
    await this.page.waitForTimeout(3000);

    if (await this.checkCaptcha()) throw new Error('CAPTCHA_REQUIRED: 天眼查详情页验证码');

    const screenshotPath = await this.takeScreenshot('tyc_detail_' + Date.now(), 1);

    const detail = await this.page.evaluate(() => {
      const txt = document.body ? document.body.innerText : '';
      const pick = (labels) => {
        for (const label of labels) {
          const m = txt.match(new RegExp(label + '[:：]\\s*([^\\n]{2,200})'));
          if (m && m[1]) {
            return m[1].replace(/复制|点击复制|登录查看/g, '').trim();
          }
        }
        return '';
      };
      const h1 = document.querySelector('h1, .company-name, [class*="company_name"]');
      const h1Text = h1 ? (h1.textContent || '').trim() : '';
      return {
        companyName: h1Text,
        creditCode: pick(['统一社会信用代码']),
        legalPerson: pick(['法定代表人']),
        registeredCapital: pick(['注册资本']),
        registeredAddress: pick(['地址', '注册地址', '住所']),
        establishDate: pick(['成立日期', '成立时间']),
        area: pick(['所属地区', '地区']),
        industry: pick(['国标行业', '所属行业', '行业']),
        businessScope: pick(['经营范围']),
        companyStatus: (txt.match(/(存续|在业|注销|吊销|迁出|停业|清算)/) || [])[1] || '',
        contactPhone: (txt.match(/(?:电话|联系电话)[:：\s]*([0-9\-+*]{6,20})/) || [])[1] || '',
        contactEmail: (txt.match(/(?:邮箱|电子邮件)[:：\s]*([\w.+-]+@[\w-]+\.[\w.-]+)/) || [])[1] || '',
        rawHtml: document.body.innerHTML.substring(0, 10000)
      };
    });

    logger.info(`天眼查详情提取完成: ${detail.companyName || companyName || '无'}, 信用代码=${detail.creditCode || '无'}, 法人=${detail.legalPerson || '无'}`);
    return { ...detail, screenshotPath, detailUrl };
  }

  async takeScreenshot(prefix, pageNum) {
    const dir = path.resolve(process.env.SCREENSHOT_DIR || './screenshots');
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const filepath = path.join(dir, `${prefix}_p${pageNum}_${Date.now()}.png`);
    await this.page.screenshot({ path: filepath, fullPage: true });
    logger.info(`截图已保存: ${filepath}`);
    return filepath;
  }

  // 打开登录页并截图二维码
  // 不跳转页面，仅用DOM检测当前状态
  async prepareQrCode(taskId) {
    await this.init();
    await this.page.goto('https://www.tianyancha.com/login', { waitUntil: 'domcontentloaded' });
    // 【2026-09-17】固定等待 1500ms → 600ms：投屏在 page 一存在就开始推帧，
    // 这里只需保证"登录态初判 + 兜底截图"能拿到内容，不必等整页渲染完（少让用户干等）
    await this.page.waitForTimeout(600);

    // 先做初步DOM检测（不跳转）
    try {
      const txt = await this.page.evaluate(() => (document.body ? document.body.innerText : '')).catch(() => '');
      const hasLoggedInEvidence = /(退出|我的天眼查|个人中心)/.test(txt) && !/(扫码登录|二维码|登录账号)/.test(txt);
      if (hasLoggedInEvidence) {
        logger.info(`任务${taskId} 天眼查检测到可能的登录态，跳转首页确认`);
        const confirmed = await this.checkLogin();
        if (confirmed) {
          const cookies = await this.context.cookies();
          await cookieManager.saveCookie(taskId, cookies);
          logger.info(`任务${taskId} 天眼查确认已登录，自动保存Cookie（共${cookies.length}条）`);
          return { success: true, qrPath: null, alreadyLoggedIn: true, cookieCount: cookies.length };
        }
      }
    } catch (e) {
      logger.warn(`任务${taskId} 天眼查prepareQrCode检测异常: ${e.message}`);
    }

    const qrDir = path.resolve('./storage/qrcodes');
    if (!fs.existsSync(qrDir)) fs.mkdirSync(qrDir, { recursive: true });
    const qrPath = path.join(qrDir, `${taskId}_login.png`);
    // 选择器等待从 10000ms 降到 2000ms：该选择器在天眼查上未必命中，等太久纯属让用户干等
    await this.page.waitForSelector('img[src*="login-scan"], img[class*="scan"], .scan-hover', { timeout: 2000 }).catch(() => {});
    await this.page.screenshot({ path: qrPath });
    logger.info(`任务${taskId} 天眼查二维码已截图保存: ${qrPath}`);
    return { success: true, qrPath, alreadyLoggedIn: false };
  }

  // 等待扫码完成 - 轮询_checkLoginDom()不跳转 + 心跳检测
  async waitForLogin(taskId) {
    try {
      if (!this.page || this.page.isClosed()) {
        return { success: false, error: '浏览器已关闭，请重新打开登录页' };
      }
      const deadline = Date.now() + 300000;
      let lastLoggedCheck = false;
      let heartbeatCount = 0;
      let consecutiveHeartbeatFails = 0;
      while (Date.now() < deadline) {
        if (!this.page || this.page.isClosed()) {
          logger.warn(`任务${taskId} 天眼查浏览器被关闭，终止等待`);
          return { success: false, error: '浏览器已关闭' };
        }
        heartbeatCount++;
        if (heartbeatCount % 20 === 0) {
          try {
            const alive = await this.page.evaluate(() => document.readyState).catch(() => null);
            if (alive === null) {
              consecutiveHeartbeatFails++;
              if (consecutiveHeartbeatFails >= 3) {
                logger.error(`任务${taskId} 天眼查浏览器页面无响应`);
                return { success: false, error: '浏览器页面无响应，请重启' };
              }
            } else {
              consecutiveHeartbeatFails = 0;
            }
          } catch (hf) {
            consecutiveHeartbeatFails++;
            if (consecutiveHeartbeatFails >= 3) {
              return { success: false, error: '浏览器页面无响应，请重启' };
            }
          }
        }
        try {
          let loggedIn = await this._checkLoginDom();
          if (!loggedIn) {
            // 扫码后可能跳到一个异常页（404 之类），那种页面 DOM 上读不到任何登录标记。
            // 只有在确认「已经不在登录页」时才回首页复核，避免把还没扫码的登录页跳走。
            if (heartbeatCount % 3 === 0 && !(await this._looksLikeLoginPage())) {
              logger.info(`任务${taskId} 天眼查已不在登录页(当前: ${this.page.url()})，回首页复核登录态`);
              loggedIn = await this.checkLogin();
            }
          }
          if (loggedIn) {
            if (lastLoggedCheck) {
              const cookies = await this.context.cookies();
              await cookieManager.saveCookie(taskId, cookies);
              logger.info(`任务${taskId} 天眼查登录成功，Cookie已保存（共${cookies.length}条）`);
              return { success: true };
            } else {
              lastLoggedCheck = true;
            }
          } else {
            lastLoggedCheck = false;
          }
        } catch (e) {
          // ignore
        }
        // 轮询间隔 3000ms → 1500ms：让"扫完码后页面反应过来"更快
        await this.page.waitForTimeout(1500);
      }
      logger.warn(`任务${taskId} 天眼查登录等待超时（5分钟）`);
      return { success: false, error: '登录超时（5分钟内未完成扫码登录）' };
    } catch (e) {
      logger.error(`任务${taskId} 天眼查waitForLogin异常: ${e.message}`);
      return { success: false, error: e.message };
    }
  }

  async close() {
    if (this.context) {
      await this.context.close().catch(() => {});
      this.context = null;
      this.page = null;
      logger.info('天眼查浏览器已关闭，登录态已持久化保存');
    }
  }
}

module.exports = TycCollector;
