const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const logger = require('../utils/logger');
const cookieManager = require('./cookieManager');
const { killBrowsersUsingProfile, firstLineOf } = require('../utils/browserProfile');

// 爱企查采集器（百度旗下）
// 注意：爱企查有百度安全验证，会拦截自动化浏览器（headless 直接提示"请关闭调试窗口"，
//       headed 首次访问可能弹滑块验证）。因此采用与风鸟相同的"人工辅助"模式：
//       - 浏览器强制非 headless，在服务器桌面弹出窗口
//       - 首次登录/验证时，用户通过远程桌面在弹出的浏览器里手动完成（滑块或扫码）
//       - 登录态持久化保存，之后采集自动带 Cookie，一般不再触发验证
class AqcCollector {
  constructor(options = {}) {
    this.context = null;
    this.page = null;
    this.captureMode = options.captureMode || false;
  }

  async init() {
    if (this.context) {
      logger.warn('爱企查浏览器已存在，跳过初始化');
      return;
    }
    // 默认非 headless（人工辅助），可用 AQC_HEADLESS=true 强制无头
    const headless = process.env.AQC_HEADLESS === 'true';
    const dirName = this.captureMode ? 'browser-data/aqc-capture' : 'browser-data/aqc';
    const userDataDir = path.resolve(__dirname, `../../storage/${dirName}`);
    if (!fs.existsSync(userDataDir)) fs.mkdirSync(userDataDir, { recursive: true });

    const lockFile = path.join(userDataDir, 'SingletonLock');
    try {
      if (fs.existsSync(lockFile)) fs.unlinkSync(lockFile);
    } catch (e) {
      logger.warn(`清理SingletonLock失败（可忽略）: ${e.message}`);
    }

    // 反爬加固：优先使用系统 Edge 浏览器（channel: 'msedge'）。
    // Playwright 自带 Chromium 的指纹特征（如 --enable-automation 残留、缺失 Chrome 插件列表等）
    // 会被百度安全系统识别并拦截（提示"请关闭调试窗口"）。Edge 是真实用户常用浏览器，指纹更真实。
    // 若服务器未安装 Edge，则回退到自带 Chromium。
    const launchOptions = {
      headless,
      // slowMo 默认 0（同企查查）：慢动作只用于人工观察，开着会让每次交互硬等数百毫秒
      slowMo: parseInt(process.env.SLOW_MO || '0'),
      no_viewport: true,
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0',
      args: [
        '--disable-blink-features=AutomationControlled',
        '--disable-infobars',
        '--disable-dev-shm-usage',
        '--start-maximized'
      ]
    };

    logger.info(`爱企查启动持久化浏览器，userDataDir=${userDataDir}, headless=${headless}, captureMode=${this.captureMode}`);
    try {
      this.context = await chromium.launchPersistentContext(userDataDir, { ...launchOptions, channel: 'msedge' });
      logger.info('爱企查使用系统 Edge 浏览器启动（反爬指纹更真实）');
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

    // 强化反自动化指纹伪装
    await this.context.addInitScript(() => {
      Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
      Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh', 'en'] });
      Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
      window.chrome = window.chrome || { runtime: {} };
      const originalQuery = window.navigator.permissions && window.navigator.permissions.query;
      if (originalQuery) {
        window.navigator.permissions.query = (parameters) =>
          parameters.name === 'notifications'
            ? Promise.resolve({ state: Notification.permission })
            : originalQuery(parameters);
      }
    });
    this.context.grantPermissions(['notifications']).catch(() => {});
    this.page = this.context.pages()[0] || await this.context.newPage();
    this.page.setDefaultTimeout(parseInt(process.env.DEFAULT_TIMEOUT || '30000'));
    logger.info(`爱企查浏览器初始化完成 (headless=${headless})`);
  }

  async loadLoginState(taskId) {
    const cookies = await cookieManager.loadCookieArray(taskId, 'aqc');
    if (cookies && cookies.length) {
      await this.context.addCookies(cookies);
      logger.info(`任务${taskId} 爱企查Cookie加载成功`);
      return true;
    }
    logger.warn(`任务${taskId} 无爱企查Cookie，需要先登录`);
    return false;
  }

  // 是否被百度安全验证/反调试拦截
  async _isBlocked() {
    const url = String(this.page.url());
    if (/wappass|captcha|passport\.baidu/.test(url)) return true;
    const txt = await this.page.evaluate(() => (document.body ? document.body.innerText : '')).catch(() => '');
    return /请关闭浏览器的调试窗口|安全验证|拖动左侧滑块/.test(txt);
  }

  // ========== 登录状态检测（双重实现） ==========
  // _checkLoginDom: 纯DOM检测，不做任何页面跳转。用于 waitForLogin 轮询。
  // checkLogin: 先跳转到首页再检测，用于初始化阶段。

  // 纯DOM检测（不跳转页面）
  async _checkLoginDom() {
    try {
      if (!this.page || this.page.isClosed()) return false;
      const currentUrl = String(this.page.url());
      if (/passport|login|wappass|captcha/.test(currentUrl)) return false;
      const txt = await this.page.evaluate(() => (document.body ? document.body.innerText : '')).catch(() => '');
      if (/(扫码登录|二维码|登录\/注册|登录账号|手机登录|验证码|百度账号)/.test(txt)) return false;
      if (/(退出|我的爱企查|个人中心|账号设置)/.test(txt)) return true;
      return false;
    } catch (e) {
      logger.warn(`爱企查_checkLoginDom异常: ${e.message}`);
      return false;
    }
  }

  async checkLogin() {
    try {
      if (!this.page || this.page.isClosed()) return false;
      const currentUrl = String(this.page.url());
      if (/passport|login|wappass|captcha/.test(currentUrl)) {
        logger.info('爱企查当前在登录/验证页面，强制判定为未登录');
        return false;
      }
      await this.page.goto('https://aiqicha.baidu.com/', { waitUntil: 'domcontentloaded', timeout: 30000 });
      await this.page.waitForTimeout(800);
      if (await this._isBlocked()) {
        logger.warn('爱企查被安全验证拦截，需要在爱企查登录页完成安全验证');
        return false;
      }
      const result = await this._checkLoginDom();
      logger.info(result ? '爱企查已登录（checkLogin跳转首页检测通过）' : '爱企查未登录');
      return result;
    } catch (e) {
      logger.warn(`爱企查checkLogin异常: ${e.message}`);
      return false;
    }
  }

  async searchCompanies(keyword, maxPages = 5) {
    const results = [];
    logger.info(`爱企查开始搜索: ${keyword}, 最大页数: ${maxPages}`);

    for (let pageNum = 1; pageNum <= maxPages; pageNum++) {
      const url = `https://aiqicha.baidu.com/s?q=${encodeURIComponent(keyword)}&p=${pageNum}`;
      await this.page.goto(url, { waitUntil: 'domcontentloaded' });
      await this.page.waitForTimeout(3000);

      if (await this._isBlocked()) throw new Error('CAPTCHA_REQUIRED: 爱企查需要人工在服务器桌面完成安全验证');

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
        const anchors = Array.from(document.querySelectorAll('a[href*="company"], a[href*="detail"]'));
        return anchors.map(a => {
          const name = (a.textContent || '').trim();
          const href = a.href || '';
          // 只要看起来像公司名的链接
          if (!name || name.length < 4 || !/公司|企业|集团|厂|店|中心|事务所/.test(name) || seen.has(href)) return null;
          seen.add(href);
          return { companyName: name, detailUrl: href, rawText: '', debugListText: pickRowText(a, name) };
        }).filter(Boolean);
      });

      logger.info(`爱企查第${pageNum}页采集到 ${companies.length} 家`);
      if (!companies.length) break;
      results.push(...companies.map(c => ({ ...c, searchKeyword: keyword, pageNum })));
      await this.page.waitForTimeout(parseInt(process.env.CAPTURE_INTERVAL || '8000'));
    }

    logger.info(`爱企查搜索完成: ${keyword}, 共${results.length}家企业`);
    return results;
  }

  async getCompanyDetail(detailUrl, companyName) {
    if (!detailUrl) {
      logger.warn(`爱企查无 detailUrl，跳过详情采集（companyName=${companyName || '无'}）`);
      return { detailError: '爱企查无 detailUrl，无法采集详情' };
    }
    logger.info(`爱企查访问企业详情: ${detailUrl}`);
    await this.page.goto(detailUrl, { waitUntil: 'domcontentloaded' });
    await this.page.waitForTimeout(3000);

    if (await this._isBlocked()) throw new Error('CAPTCHA_REQUIRED: 爱企查详情页安全验证');

    const screenshotPath = await this.takeScreenshot('aqc_detail_' + Date.now(), 1);

    const detail = await this.page.evaluate(() => {
      const txt = document.body ? document.body.innerText : '';
      const pick = (labels) => {
        for (const label of labels) {
          const m = txt.match(new RegExp(label + '[:：]\\s*([^\\n]{2,200})'));
          if (m && m[1]) return m[1].trim();
        }
        return '';
      };
      const h1 = document.querySelector('h1, .company-name, [class*="company-name"]');
      const h1Text = h1 ? (h1.textContent || '').trim() : '';
      return {
        companyName: h1Text,
        creditCode: pick(['统一社会信用代码']),
        legalPerson: pick(['法定代表人', '法人代表']),
        registeredCapital: pick(['注册资本']),
        registeredAddress: pick(['注册地址', '地址', '住所']),
        establishDate: pick(['成立日期', '成立时间']),
        area: pick(['所属地区', '地区']),
        industry: pick(['所属行业', '行业']),
        businessScope: pick(['经营范围']),
        companyStatus: (txt.match(/(存续|在业|开业|注销|吊销|迁出|停业|清算)/) || [])[1] || '',
        contactPhone: (txt.match(/(?:电话|联系电话|手机)[:：\s]*([0-9\-+*]{6,20})/) || [])[1] || '',
        contactEmail: (txt.match(/(?:邮箱|电子邮件)[:：\s]*([\w.+-]+@[\w-]+\.[\w.-]+)/) || [])[1] || '',
        rawHtml: document.body.innerHTML.substring(0, 10000)
      };
    });

    logger.info(`爱企查详情提取完成: ${detail.companyName || companyName || '无'}, 信用代码=${detail.creditCode || '无'}, 法人=${detail.legalPerson || '无'}`);
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

  // 打开爱企查首页/登录页并截图。
  // 不跳转页面，仅用DOM检测当前状态
  async prepareQrCode(taskId) {
    await this.init();
    await this.page.goto('https://aiqicha.baidu.com/', { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(e => logger.warn(`打开爱企查首页异常: ${e.message}`));
    // 固定等待从 3000ms 降到 800ms：登录页截图不需要等全站加载完，等太久只会让用户干等
    await this.page.waitForTimeout(800);

    // 用纯DOM检测当前登录态（不跳转）
    try {
      const txt = await this.page.evaluate(() => (document.body ? document.body.innerText : '')).catch(() => '');
      const hasLoggedInEvidence = /(退出|我的爱企查|个人中心)/.test(txt) && !/(扫码登录|二维码|登录账号|百度账号)/.test(txt);
      if (hasLoggedInEvidence) {
        logger.info(`任务${taskId} 爱企查检测到可能的登录态，跳转首页确认`);
        const confirmed = await this.checkLogin();
        if (confirmed) {
          const cookies = await this.context.cookies();
          await cookieManager.saveCookie(taskId, cookies);
          logger.info(`任务${taskId} 爱企查确认已登录，自动保存Cookie（共${cookies.length}条）`);
          return { success: true, qrPath: null, alreadyLoggedIn: true, cookieCount: cookies.length };
        }
      }
    } catch (e) {
      logger.warn(`任务${taskId} 爱企查prepareQrCode检测异常: ${e.message}`);
    }

    const qrDir = path.resolve('./storage/qrcodes');
    if (!fs.existsSync(qrDir)) fs.mkdirSync(qrDir, { recursive: true });
    const qrPath = path.join(qrDir, `${taskId}_login.png`);
    await this.page.screenshot({ path: qrPath });
    logger.info(`任务${taskId} 爱企查登录页/验证页已截图: ${qrPath}`);
    return { success: true, qrPath, alreadyLoggedIn: false };
  }

  // 等待用户在服务器桌面浏览器完成验证+登录（最长5分钟）
  // 新增：心跳检测 + 验证码弹窗检测（检测到验证码时立即提示用户）
  async waitForLogin(taskId) {
    try {
      if (!this.page || this.page.isClosed()) {
        return { success: false, error: '浏览器已关闭，请重新打开登录页' };
      }
      const deadline = Date.now() + 300000;
      let lastLoggedCheck = false;
      let heartbeatCount = 0;
      let consecutiveHeartbeatFails = 0;
      let captchaNotified = false;
      while (Date.now() < deadline) {
        if (!this.page || this.page.isClosed()) {
          logger.warn(`任务${taskId} 爱企查浏览器被关闭，终止等待`);
          return { success: false, error: '浏览器已关闭' };
        }
        heartbeatCount++;
        if (heartbeatCount % 20 === 0) {
          try {
            const alive = await this.page.evaluate(() => document.readyState).catch(() => null);
            if (alive === null) {
              consecutiveHeartbeatFails++;
              if (consecutiveHeartbeatFails >= 3) {
                logger.error(`任务${taskId} 爱企查浏览器页面无响应`);
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
        if (heartbeatCount % 10 === 0) {
          try {
            const isCaptcha = await this._isBlocked();
            if (isCaptcha && !captchaNotified) {
              captchaNotified = true;
              logger.warn(`任务${taskId} 爱企查检测到验证码/安全验证，请在服务器桌面手动完成`);
              return { success: false, error: '检测到验证码/安全验证，请在服务器桌面浏览器手动完成' };
            }
          } catch (e) {
            // ignore
          }
        }
        try {
          const loggedIn = await this._checkLoginDom();
          if (loggedIn) {
            if (lastLoggedCheck) {
              const cookies = await this.context.cookies();
              await cookieManager.saveCookie(taskId, cookies);
              logger.info(`任务${taskId} 爱企查登录成功，Cookie已保存（共${cookies.length}条）`);
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
        // 轮询间隔 3000ms → 1500ms：让"登录完成后页面反应过来"更快
        await this.page.waitForTimeout(1500);
      }
      return { success: false, error: '登录超时（5分钟内未完成登录）' };
    } catch (e) {
      logger.error(`任务${taskId} 爱企查登录等待异常: ${e.message}`);
      return { success: false, error: e.message };
    }
  }

  async close() {
    if (this.context) {
      await this.context.close().catch(() => {});
      this.context = null;
      this.page = null;
      logger.info('爱企查浏览器已关闭，登录态已持久化保存');
    }
  }
}

module.exports = AqcCollector;
