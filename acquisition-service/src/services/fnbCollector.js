const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const logger = require('../utils/logger');
const cookieManager = require('./cookieManager');
const { killBrowsersUsingProfile, firstLineOf } = require('../utils/browserProfile');

/**
 * 风鸟（RiskBird）采集器
 * 官网: https://www.riskbird.com
 * 登录方式: 手机号验证码登录 或 密码登录（首页即登录页）
 * 注册即送5年SVIP，登录后查询基本免费
 * 基于 Playwright 实现搜索、详情、登录Cookie管理等核心采集逻辑
 *
 * 重要：每个任务应 new 一个独立的 FnbCollector 实例，避免共用浏览器实例导致冲突
 *      （采集任务和登录任务同时运行会互相打断 page.waitForTimeout）
 */
class FnbCollector {
  constructor(options = {}) {
    this.browser = null;
    this.context = null;
    this.page = null;
    this.baseUrl = 'https://www.riskbird.com';
    // 采集模式使用独立的 userDataDir，避免与登录浏览器单例冲突
    // captureMode=true 时使用 fnb-capture 目录，false 时使用 fnb 目录（登录用）
    this.captureMode = options.captureMode || false;
    // 无头开关覆盖：登录任务必须"可见窗口"（用户要在弹出的浏览器里输手机号验证码），
    // 但登录任务仍需与采集任务共用同一个持久化目录（captureMode=true），
    // 故把"用哪个目录"和"是否无头"解耦，由调用方显式指定 headless。
    this.headlessOverride = typeof options.headless === 'boolean' ? options.headless : null;
  }

  // 初始化浏览器（风鸟登录需要在浏览器中手动输入手机号验证码，因此强制非headless）
  //
  // 重要改造：使用 launchPersistentContext 替代 launch + newContext
  // 浏览器数据目录持久化到磁盘，登录态（Cookie/localStorage/sessionStorage）会被保存
  // 下次启动浏览器自动带登录态，无需重复扫码登录
  async init() {
    if (this.context) {
      logger.warn('风鸟浏览器已存在，跳过初始化');
      return;
    }
    // 采集任务默认无头：不在桌面弹窗、不会被误关（服务部署后没人能进服务器桌面，
    // 窗口被关 = 采集立刻失败：Target page, context or browser has been closed）。
    // 实测 2026-09-16：风鸟无头采集可正常跑通（10 家全部落库）。
    // 登录任务默认**可见窗口**（风鸟需人工输入手机验证码，可用 LOGIN_HEADLESS=true 改回无头）。
    const headless = this.headlessOverride !== null
      ? this.headlessOverride
      : (this.captureMode ? process.env.CAPTURE_HEADLESS !== 'false' : process.env.FNB_HEADLESS === 'true');

    // 持久化目录：登录任务用 fnb，采集任务用 fnb-capture（避免单例冲突）
    // 两个浏览器可同时运行：登录浏览器等待用户登录，采集浏览器用 Cookie 自动采集
    const dirName = this.captureMode ? 'browser-data/fnb-capture' : 'browser-data/fnb';
    const userDataDir = path.resolve(__dirname, `../../storage/${dirName}`);
    if (!fs.existsSync(userDataDir)) {
      fs.mkdirSync(userDataDir, { recursive: true });
    }

    // 清理可能遗留的 SingletonLock（上次未正常关闭会残留，导致启动失败）
    const lockFile = path.join(userDataDir, 'SingletonLock');
    try {
      if (fs.existsSync(lockFile)) fs.unlinkSync(lockFile);
    } catch (e) {
      logger.warn(`清理SingletonLock失败（可忽略）: ${e.message}`);
    }

    logger.info(`风鸟启动持久化浏览器，userDataDir=${userDataDir}, headless=${headless}, captureMode=${this.captureMode}`);
    const launchOptions = {
      headless,
      // slowMo 默认 0（同企查查）：风鸟要人工输手机号验证码，慢动作会让输入明显发卡
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
      logger.info('风鸟使用系统 Edge 浏览器启动（反爬指纹更真实）');
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
    const defaultTimeout = parseInt(process.env.DEFAULT_TIMEOUT || '30000');
    this.page.setDefaultTimeout(defaultTimeout);
    logger.info(`风鸟浏览器初始化完成 (headless=${headless})`);
  }

  // 加载登录态
  async loadLoginState(taskId) {
    const state = await cookieManager.loadCookie(taskId, 'fnb');
    if (state && state.cookies && state.cookies.length) {
      await this.context.addCookies(state.cookies);
      if (state.origins && state.origins.length) {
        await this.context.addCookies(state.origins);
      }
      logger.info(`风鸟任务${taskId} Cookie加载成功，共${state.cookies.length}条`);
      return true;
    }
    logger.info(`风鸟任务${taskId} 无Cookie，需要登录`);
    return false;
  }

  // ========== 登录状态检测（双重实现） ==========
  // _checkLoginDom: 纯DOM检测，不做任何页面跳转。用于 waitForLogin 轮询。
  // checkLogin: 先跳转到首页再检测，用于初始化阶段。

  // 纯DOM检测（不跳转页面）
  async _checkLoginDom() {
    try {
      if (!this.page || this.page.isClosed()) return false;
      const currentUrl = String(this.page.url());
      if (/login|passport/i.test(currentUrl)) return false;
      const txt = await this.page.evaluate(() => (document.body ? document.body.innerText : '')).catch(() => '');
      if (/(扫码登录|二维码|登录\/注册|登录账号|手机登录|验证码)/.test(txt)) return false;
      const hasLoginForm = await this.page.evaluate(() => {
        return !!(document.querySelector('input[type="tel"], input[placeholder*="手机"], input[placeholder*="账号"], input[type="password"]'));
      }).catch(() => false);
      if (hasLoginForm) return false;
      const hasPositive = await this.page.evaluate(() => {
        const positiveSelectors = [
          '.logout', '[class*="logout"]', '.user-avatar', '.avatar',
          '[class*="user-info"]', '[class*="userInfo"]', '[class*="account-info"]',
          '.user-menu', '[class*="user-menu"]', '.header-user', '[class*="header-user"]'
        ];
        for (const sel of positiveSelectors) {
          if (document.querySelector(sel)) return true;
        }
        if ((/(退出|退出登录|我的订单|我的收藏|会员中心|个人中心|账号设置)/).test(document.body ? document.body.innerText : '')) return true;
        return false;
      }).catch(() => false);
      return hasPositive;
    } catch (e) {
      logger.warn(`风鸟_checkLoginDom异常: ${e.message}`);
      return false;
    }
  }

  async checkLogin() {
    try {
      if (!this.page || this.page.isClosed()) return false;
      const currentUrl = String(this.page.url());
      if (/login|passport/i.test(currentUrl)) {
        logger.info('风鸟当前在登录页，强制判定为未登录');
        return false;
      }
      const cookies = await this.context.cookies();
      if (cookies.length === 0) {
        logger.warn('风鸟未登录（无Cookie）');
        return false;
      }
      await this.page.goto(this.baseUrl, { waitUntil: 'domcontentloaded' });
      const result = await this._checkLoginDom();
      logger.info(result ? '风鸟已登录（checkLogin跳转首页检测通过）' : '风鸟未登录');
      return result;
    } catch (e) {
      logger.error(`风鸟checkLogin异常: ${e.message}`);
      return false;
    }
  }

  // 准备登录页：打开风鸟首页/登录页，截图返回
  // 不跳转页面，仅用DOM检测当前状态
  async prepareQrCode(taskId) {
    await this.init();
    await this.page.goto(this.baseUrl, { waitUntil: 'domcontentloaded' });
    // 选择器等待从 15000ms 降到 1200ms：该选择器在风鸟上未必命中，等太久纯属让用户干等
    // （投屏在 page 一存在就开始推帧，这里只影响"登录态初判 + 兜底截图"的时机）
    await this.page.waitForSelector('input[type="tel"], input[placeholder*="手机"], input[placeholder*="账号"], form', { timeout: 1200 }).catch(() => {});

    // 用纯DOM检测当前登录态（不跳转）
    try {
      const txt = await this.page.evaluate(() => (document.body ? document.body.innerText : '')).catch(() => '');
      const hasLoggedInEvidence = /(退出|退出登录|用户头像)/.test(txt) && !/(扫码登录|二维码|登录账号|手机登录|验证码)/.test(txt);
      if (hasLoggedInEvidence) {
        logger.info(`任务${taskId} 风鸟检测到可能的登录态，跳转首页确认`);
        const confirmed = await this.checkLogin();
        if (confirmed) {
          const cookies = await this.context.cookies();
          await cookieManager.saveCookie(taskId, cookies);
          logger.info(`任务${taskId} 风鸟确认已登录，自动保存Cookie（共${cookies.length}条）`);
          return { success: true, qrPath: null, alreadyLoggedIn: true, cookieCount: cookies.length };
        }
      }
    } catch (e) {
      logger.warn(`任务${taskId} 风鸟prepareQrCode检测异常: ${e.message}`);
    }

    const qrDir = path.resolve('./storage/qrcodes');
    if (!fs.existsSync(qrDir)) fs.mkdirSync(qrDir, { recursive: true });
    const qrPath = path.join(qrDir, `${taskId}_login.png`);
    await this.page.screenshot({ path: qrPath, fullPage: true });
    logger.info(`任务${taskId} 风鸟登录页已截图保存: ${qrPath}`);
    return { success: true, qrPath, alreadyLoggedIn: false };
  }

  // 等待用户在浏览器中完成登录（最长5分钟）
  // 新增：心跳检测 + 页面崩溃检测
  async waitForLogin(taskId) {
    try {
      if (!this.page || this.page.isClosed()) {
        return { success: false, error: '浏览器已关闭，请重新打开登录页' };
      }
      logger.info(`任务${taskId} 等待用户在风鸟浏览器中手动登录...`);
      const deadline = Date.now() + 300000;
      let lastLoggedCheck = false;
      let heartbeatCount = 0;
      let consecutiveHeartbeatFails = 0;
      while (Date.now() < deadline) {
        if (!this.page || this.page.isClosed()) {
          logger.warn(`任务${taskId} 风鸟浏览器被关闭，终止等待`);
          return { success: false, error: '浏览器已关闭' };
        }
        heartbeatCount++;
        if (heartbeatCount % 20 === 0) {
          try {
            const alive = await this.page.evaluate(() => document.readyState).catch(() => null);
            if (alive === null) {
              consecutiveHeartbeatFails++;
              if (consecutiveHeartbeatFails >= 3) {
                logger.error(`任务${taskId} 风鸟浏览器页面无响应`);
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
          const loggedIn = await this._checkLoginDom();
          if (loggedIn) {
            if (lastLoggedCheck) {
              const cookies = await this.context.cookies();
              await cookieManager.saveCookie(taskId, cookies);
              logger.info(`任务${taskId} 风鸟登录成功，Cookie已保存（共${cookies.length}条）`);
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
      logger.warn(`任务${taskId} 风鸟登录等待超时（5分钟）`);
      return { success: false, error: '登录超时（5分钟内未检测到登录成功）' };
    } catch (e) {
      logger.error(`任务${taskId} 风鸟waitForLogin异常: ${e.message}`);
      return { success: false, error: e.message };
    }
  }

  // 兼容旧调用：等价于 prepareQrCode + waitForLogin
  async scanLogin(taskId) {
    const qrResult = await this.prepareQrCode(taskId);
    if (!qrResult.success) return qrResult;
    const loginResult = await this.waitForLogin(taskId);
    return { ...loginResult, qrPath: qrResult.qrPath };
  }

  // 搜索企业
  // 改造：风鸟没有 /search?keyword=xxx 这种 URL，必须通过首页搜索框输入提交
  // options.area: 地区筛选（如"广东"、"西安"），在搜索结果中按公司地址包含 area 过滤
  async searchCompanies(keyword, maxPages = 5, options = {}) {
    const results = [];
    const area = options.area || '';
    logger.info(`风鸟开始搜索: ${keyword}, 最大页数: ${maxPages}, 地区筛选: ${area || '无'}`);

    // 第1步：访问风鸟首页
    await this.page.goto(this.baseUrl, { waitUntil: 'domcontentloaded' });
    await this.page.waitForTimeout(1500);

    // 检查是否有验证码（首页也可能弹验证码）
    const hasCaptchaHome = await this.checkCaptcha();
    if (hasCaptchaHome) {
      throw new Error('CAPTCHA_REQUIRED: 风鸟首页需要人工处理验证码');
    }

    // 第2步：查找首页搜索输入框
    const searchInput = await this.page.$(
      'input[type="text"]:not([disabled]):not([readonly]), ' +
      'input[type="search"], ' +
      'input[placeholder*="搜索"], ' +
      'input[placeholder*="企业"], ' +
      'input[placeholder*="公司"], ' +
      'input[placeholder*="输入"], ' +
      'input[placeholder*="查询"], ' +
      'input[name*="search"], input[name*="keyword"], input[name*="key"]'
    );
    if (!searchInput) {
      // 输出页面所有 input 元素信息辅助定位
      const inputDebug = await this.page.evaluate(() => {
        const inputs = Array.from(document.querySelectorAll('input'));
        return {
          url: window.location.href,
          title: document.title,
          inputCount: inputs.length,
          inputs: inputs.slice(0, 15).map(i => ({
            type: i.type,
            placeholder: i.placeholder || '',
            name: i.name || '',
            id: i.id || '',
            className: (i.className || '').substring(0, 80),
            visible: i.offsetParent !== null
          })),
          bodyTextHead: (document.body.innerText || '').substring(0, 800)
        };
      }).catch(() => ({ error: 'evaluate失败' }));
      logger.error(`风鸟未找到搜索输入框, 调试: ${JSON.stringify(inputDebug).substring(0, 2500)}`);
      throw new Error('风鸟未找到搜索输入框（可能页面结构变化或登录态失效）');
    }

    // 第3步：输入关键词并提交（回车或点击搜索按钮）
    await searchInput.fill('');
    await searchInput.fill(keyword);
    await this.page.waitForTimeout(300);

    // 优先尝试点击搜索按钮，找不到按钮则按回车
    const searchBtn = await this.page.$(
      'button[type="submit"], ' +
      'button[class*="search"], button[class*="Search"], ' +
      '[class*="search-btn"], [class*="searchBtn"], ' +
      'a[class*="search"], ' +
      'svg[class*="search"], [class*="icon-search"]'
    );
    if (searchBtn) {
      logger.info(`风鸟点击搜索按钮提交`);
      await searchBtn.click();
    } else {
      logger.info(`风鸟未找到搜索按钮，按回车提交`);
      await searchInput.press('Enter');
    }

    // 第4步：等待搜索结果页加载
    // networkidle 可能因风鸟页面的长连接永久不达，改用 domcontentloaded + 固定等待
    await this.page.waitForLoadState('domcontentloaded').catch(() => {});
    await this.page.waitForTimeout(3000);
    logger.info(`风鸟搜索提交后 URL: ${this.page.url()}`);

    // 检查是否有验证码
    const hasCaptcha = await this.checkCaptcha();
    if (hasCaptcha) {
      throw new Error('CAPTCHA_REQUIRED: 风鸟需要人工处理验证码');
    }

    // 解析搜索结果列表
    for (let pageNum = 1; pageNum <= maxPages; pageNum++) {
      logger.info(`风鸟正在采集第${pageNum}页`);

      // 截图当前页（清理文件名非法字符）
      const safePrefix = this.sanitizeFilename(keyword);
      const screenshotPath = await this.takeScreenshot(safePrefix, pageNum);

      // 提取企业列表
      const companies = await this.extractCompanyList();
      logger.info(`风鸟第${pageNum}页提取到${companies.length}家企业${area ? `（地区[${area}]将在详情采集后过滤）` : ''}`);
      results.push(...companies.map(c => ({ ...c, screenshotPath, searchKeyword: keyword, pageNum })));

      // 翻到下一页
      if (pageNum < maxPages) {
        const nextSuccess = await this.gotoNextPage();
        if (!nextSuccess) break;
        await this.page.waitForTimeout(parseInt(process.env.CAPTURE_INTERVAL || '8000'));
      }
    }

    logger.info(`风鸟搜索完成: ${keyword}, 共采集${results.length}家企业`);
    return results;
  }

  // 检查验证码
  async checkCaptcha() {
    const captcha = await this.page.$('.captcha, .verify-wrap, .nc_iconfont, .slider, .geetest, [class*="captcha"], [class*="verify"]');
    if (captcha) {
      logger.warn('风鸟检测到验证码');
      return true;
    }
    return false;
  }

  // 提取企业列表
  // 多策略提取，确保至少能拿到公司名 + 详情链接
  // 严格过滤：排除新闻摘要、导航按钮（"查公司/查老板/找客户"等）以及非企业名片元素
  async extractCompanyList() {
    const items = await this.page.evaluate((searchKeyword) => {
      // 公司名正则：要求结尾是公司后缀
      const companyNameRegex = /([\u4e00-\u9fa5（）()A-Za-z0-9]{2,40}(?:有限公司|股份有限公司|有限责任公司|合伙企业|集团有限公司|实业有限公司|科技有限公司|控股有限公司|集团有限责任公司|股份有限责任公司))/;

      // 状态后缀清洗
      const cleanName = (raw) => (raw || '')
        .replace(/(在营|存续|注销|吊销|迁出|迁入|停业|清算|破产|正常|非正常|停产|撤销|开业|在业)\s*$/g, '')
        .replace(/\s+/g, ' ')
        .trim();

      // ====== 严格过滤规则 ======
      // 1. 导航按钮关键字（风鸟首页的"查公司/查老板/查风险/找客户"等卡片）
      const NAVI_KEYWORDS = ['查公司', '查老板', '查风险', '查文书', '查关系', '找客户',
        '批量查询', '我的收藏', '深刻洞察', '探查', '识别失信', '检索海量', '挖掘潜在',
        '快速访问', '企业全景', '商业网络', '失信规避', '法律文书', '关联关系', '潜在客户'];
      const isNavigationText = (text) => {
        if (!text) return false;
        return NAVI_KEYWORDS.some(kw => text.includes(kw));
      };

      // 2. 新闻摘要特征（"查看更多新闻>>"、"发布时间："、"来源："、"热度 "）
      const NEWS_PATTERNS = ['查看更多新闻', '查看更多资讯', '发布时间', '来源：', '来源:',
        '热度', '展开', '相关新闻', '资讯', ' ITBEAR', 'C114', '中金在线', 'fhtai',
        '科技资讯', '经营模式创新', '技术创新', '主要财务指标', '经营相关'];
      const isNewsContent = (text) => {
        if (!text) return false;
        return NEWS_PATTERNS.some(p => text.includes(p));
      };

      // 3. 综合校验：判断文本是否为有效的公司名（而非新闻/导航/广告）
      const isValidCompanyName = (text) => {
        if (!text || text.length < 4) return false;
        if (text.length > 80) return false;  // 公司名一般不超过80字符
        if (isNavigationText(text)) return false;
        if (isNewsContent(text)) return false;
        // 必须以公司后缀结尾（不允许后接新闻/导航文本）
        if (!companyNameRegex.test(text)) return false;
        // 提取出真正的公司名（防止后面拼接新闻）
        const match = text.match(companyNameRegex);
        if (!match || !match[1]) return false;
        // 提取的公司名长度合理
        if (match[1].length < 4 || match[1].length > 50) return false;
        return true;
      };

      // 从原始文本提取纯净公司名（去掉后面拼接的新闻摘要等噪声）
      const extractCleanCompanyName = (text) => {
        if (!text) return '';
        const match = text.match(companyNameRegex);
        return match && match[1] ? cleanName(match[1]) : '';
      };

      // 详情链接有效性校验：必须是真正的企业详情链接
      const isValidDetailUrl = (href) => {
        if (!href || !href.startsWith('http')) return false;
        if (href.startsWith('javascript:')) return false;
        if (href.endsWith('#')) return false;
        // 必须含 company/firm/detail 等关键字
        if (!(/\/(company|firm|detail|enterprise|qiye)/i.test(href))) return false;
        // 排除 list/search/首页链接
        if (/\/(list|search|home|index)/i.test(href)) return false;
        return true;
      };

      // 策略1：尝试常见的搜索结果列表项选择器
      let cards = Array.from(document.querySelectorAll(
        '.company-list-item, .search-result-item, .result-card, .list-item, .company-item, ' +
        '[class*="company-item"], [class*="search-item"], [class*="result-item"], ' +
        '.list-card, .card-item, .ant-list-item'
      ));

      // 策略2：扫描所有包含企业名称的链接（href 真的是详情链接 或 文本是干净公司名）
      if (cards.length === 0) {
        const allLinks = Array.from(document.querySelectorAll('a'));
        const companyLinks = allLinks.filter(a => {
          const href = a.href || '';
          const text = (a.textContent || '').trim();
          // 优先校验：href 是真正的详情链接 且 文本不是新闻/导航
          if (isValidDetailUrl(href) && !isNavigationText(text) && !isNewsContent(text)) {
            return true;
          }
          // 文本必须是干净的公司名（不含新闻/导航/广告）
          if (isValidCompanyName(text)) {
            return true;
          }
          return false;
        });
        cards = companyLinks.map(a => a.closest('div, li, tr, .card, [class*="item"]') || a);
        // 去重（同一个父元素可能被多个 a 命中）
        const seen = new Set();
        cards = cards.filter(el => {
          if (seen.has(el)) return false;
          seen.add(el);
          return true;
        });
      }

      // 策略3：兜底，从所有 a 链接里挑公司名作为 companyName，href 作为 detailUrl
      // 严格过滤：必须 isValidCompanyName 校验通过
      if (cards.length === 0) {
        const allLinks = Array.from(document.querySelectorAll('a'));
        const items = [];
        for (const a of allLinks) {
          const text = (a.textContent || '').trim();
          if (!isValidCompanyName(text)) continue;
          const companyName = extractCleanCompanyName(text);
          if (!companyName || companyName.length < 4) continue;
          // 二次校验：公司名本身不能含新闻/导航关键字
          if (isNavigationText(companyName) || isNewsContent(companyName)) continue;
          // 如果有 detailUrl 必须是有效详情链接
          const href = a.href || '';
          const detailUrl = isValidDetailUrl(href) ? href : '';
          items.push({
            companyName: companyName.substring(0, 100),
            detailUrl,
            rawText: companyName,  // 只保留干净公司名，不再带噪声
            // 【诊断/前置粗筛用·2026-09-23】卡片整行文本，不参与结果展示与过滤；向上找更完整的容器
            debugListText: (() => {
              let cur = a;
              for (let i = 0; i < 6 && cur; i++) {
                cur = cur.parentElement;
                if (!cur) break;
                const t = (cur.textContent || '').replace(/\s+/g, ' ').trim();
                if (t.length > companyName.length + 5 && t.length <= 400) return t.slice(0, 300);
              }
              return (a.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 300);
            })()
          });
        }
        // 去重（按 companyName）
        const seen = new Set();
        const unique = items.filter(it => {
          if (seen.has(it.companyName)) return false;
          seen.add(it.companyName);
          return true;
        });
        return unique.slice(0, 50);
      }

      return cards.map(el => {
        // 公司名称：从链接文本或标题元素
        const nameEl = el.querySelector(
          '.company-name, .title, h3, h4, .name, a[href*="company"], a[href*="/firm/"]'
        );
        const rawName = nameEl?.textContent?.trim() || el.textContent?.trim()?.substring(0, 100) || '';
        // 提取干净公司名（去掉后面可能拼接的新闻/导航噪声）
        const companyName = extractCleanCompanyName(rawName);

        // 详情链接：从元素内或元素本身找
        const linkEl = el.tagName === 'A' ? el : el.querySelector('a[href]');
        let detailUrl = linkEl?.href || '';
        if (detailUrl && !detailUrl.startsWith('http')) {
          detailUrl = new URL(detailUrl, window.location.origin).href;
        }
        // 过滤掉 javascript: 和 # 锚点；必须是有效详情链接
        if (!isValidDetailUrl(detailUrl)) {
          detailUrl = '';
        }

        // 二次校验：公司名必须通过 isValidCompanyName
        if (!isValidCompanyName(rawName)) return null;

        return {
          companyName: companyName.substring(0, 100),
          detailUrl,
          rawText: companyName,  // 只保留干净公司名
          // 【诊断/前置粗筛用·2026-09-23】卡片整行文本（含注册地/法人等），不参与结果展示与过滤。
          // 修正：原来只取卡片元素自身，风鸟卡片过窄 → 只拿到公司名；现向上找更完整的容器（长度合理即采用）。
          debugListText: (() => {
            let cur = el;
            for (let i = 0; i < 6 && cur; i++) {
              cur = cur.parentElement;
              if (!cur) break;
              const t = (cur.textContent || '').replace(/\s+/g, ' ').trim();
              if (t.length > rawName.length + 5 && t.length <= 400) return t.slice(0, 300);
            }
            return (el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 300);
          })()
        };
      }).filter(c => c && c.companyName && c.companyName.length >= 4);
    }, this.lastSearchKeyword || '');

    // 如果还是空，使用兜底策略：从 main/content 区域正则提取企业名称模式
    // 注意：不再从 body.innerText 全文匹配，避免抓到新闻里的公司名
    if (items.length === 0) {
      // 打印页面调试信息：URL、标题、所有 a 链接数量、前 1500 字符文本
      const debugInfo = await this.page.evaluate(() => {
        const links = Array.from(document.querySelectorAll('a'));
        const linkSamples = links.slice(0, 15).map(a => ({ text: (a.textContent || '').trim().substring(0, 60), href: a.href }));
        return {
          url: window.location.href,
          title: document.title,
          linkCount: links.length,
          bodyTextHead: (document.body.innerText || '').substring(0, 1500),
          linkSamples
        };
      }).catch(() => ({ error: 'evaluate失败' }));
      logger.warn(`风鸟提取0家，页面调试: ${JSON.stringify(debugInfo).substring(0, 2500)}`);

      // 兜底：只在 main/content 区域内匹配，且每个公司名必须独立出现（非新闻段落）
      const fallbackItems = await this.page.evaluate(() => {
        // 限定在 main / content / search-result 等正文区域
        const mainEl = document.querySelector('main, .main, .content, .search-result, .result-list, [class*="content"], [class*="main"], [class*="result"]');
        const scope = mainEl || document.body;
        const allText = scope.innerText || '';
        // 匹配常见的公司名后缀
        const regex = /([\u4e00-\u9fa5（）()A-Za-z0-9]{2,30}(?:有限公司|股份有限公司|有限责任公司|合伙企业|集团有限公司|实业有限公司|科技有限公司|控股有限公司))/g;
        const matches = allText.match(regex) || [];
        // 去重
        const unique = [...new Set(matches)];
        return unique.slice(0, 50).map(name => ({
          companyName: name,
          detailUrl: '',
          rawText: name
        }));
      });
      logger.info(`风鸟通用提取策略提取到${fallbackItems.length}家企业（兜底正则匹配）`);
      return fallbackItems;
    }

    return items;
  }

  // 获取企业详情并截图
  // 参数：
  //   detailUrl  - 详情页 URL，有则直接访问
  //   companyName - 公司名称，detailUrl 为空时通过公司名搜索风鸟获取详情
  async getCompanyDetail(detailUrl, companyName) {
    // 无 detailUrl 时，通过公司名在风鸟搜索这家公司，再点击进入详情页
    if (!detailUrl && companyName) {
      logger.info(`风鸟无detailUrl，通过公司名搜索获取详情: ${companyName}`);
      try {
        detailUrl = await this.searchCompanyDetailUrl(companyName);
      } catch (e) {
        logger.warn(`风鸟搜索公司详情失败: ${companyName}, ${e.message}`);
        return { detailError: '无法找到详情链接: ' + e.message };
      }
      if (!detailUrl) {
        logger.warn(`风鸟搜索公司详情未找到链接: ${companyName}`);
        return { detailError: '搜索结果中未找到该公司详情链接' };
      }
    }
    if (!detailUrl) {
      logger.warn(`风鸟无detailUrl且无公司名，跳过详情采集`);
      return {};
    }
    logger.info(`风鸟访问企业详情: ${detailUrl}`);
    await this.page.goto(detailUrl, { waitUntil: 'domcontentloaded' });
    await this.page.waitForTimeout(2000);

    const hasCaptcha = await this.checkCaptcha();
    if (hasCaptcha) {
      throw new Error('CAPTCHA_REQUIRED: 风鸟详情页验证码');
    }

    // 截图详情页
    const screenshotPath = await this.takeScreenshot('fnb_detail_' + Date.now(), 1);

    // 尝试点击"工商信息"标签页/卡片（风鸟详情页默认可能在"概览"页，需要切到工商信息）
    // 找含"工商信息"的标签或按钮，点击它
    try {
      const tabClicked = await this.page.evaluate(() => {
        // 找含"工商信息"文本的可点击元素
        const allEls = Array.from(document.querySelectorAll('a, button, .tab, .tabs-item, [role="tab"], [class*="tab"]'));
        const target = allEls.find(el => (el.textContent || '').trim().includes('工商信息'));
        if (target) {
          target.click();
          return true;
        }
        return false;
      });
      if (tabClicked) {
        logger.info(`风鸟点击"工商信息"标签成功`);
        await this.page.waitForTimeout(1500);
      }
    } catch (e) {
      logger.warn(`风鸟点击工商信息标签失败（可忽略）: ${e.message}`);
    }

    // 提取工商信息栏字段
    // 用户要求字段：公司名称、法人、成立日期、注册资本、所属地区、所属行业、注册地址、通信地址、经营范围
    const detail = await this.page.evaluate(() => {
      // ====== 字段值清洗 ======
      // 去除末尾噪声："复制点击复制"、"关联X家企业>"、"查看X家企业"等
      // 去除法人名重复（风鸟页面法人名可能重复出现："康增辉康增辉" → 取第一个）
      const cleanValue = (v) => {
        if (!v) return '';
        let s = String(v)
          .replace(/复制点击复制$/g, '')
          .replace(/点击复制$/g, '')
          .replace(/复制$/g, '')
          .replace(/关联\d+家企业>?\s*$/g, '')
          .replace(/关联\d+家.*$/g, '')
          .replace(/查看\d+家企业>?\s*$/g, '')
          .replace(/>\s*$/g, '')
          .trim();
        return s;
      };

      // 法人名去重：风鸟详情页里可能出现以下重复模式：
      //   1. "康增辉康增辉关联2家企业" - 完整重复
      //   2. "康康增辉" - 首字重复（风鸟把姓氏渲染了两次）
      //   3. "小小田智宏" - 首字重复（少见但存在）
      // 处理思路：先尝试完整重复去重，再尝试首字重复去重（要求去重后长度 >= 3）
      const cleanLegalPerson = (v) => {
        let s = cleanValue(v);
        if (!s) return '';
        // 策略1：检测完整重复模式 "康增辉康增辉" → "康增辉"
        const len = s.length;
        for (let n = Math.floor(len / 2); n >= 2; n--) {
          if (s.substring(0, n) === s.substring(n, n * 2)) {
            s = s.substring(0, n);
            break;
          }
        }
        // 策略2：检测首字重复 "康康增辉" → "康增辉"，"小小田智宏" → "小田智宏"
        // 条件：长度 >= 4，第1字符 == 第2字符，去重后长度 >= 3
        // 避免误伤 "康康"（真名）这种长度2的情况
        if (s.length >= 4 && s[0] === s[1]) {
          const candidate = s.substring(1);
          if (candidate.length >= 3) {
            s = candidate;
          }
        }
        // 去掉常见后缀（关联企业、查看企业）
        s = s.replace(/关联\d+家企业.*$/g, '').replace(/查看\d+家企业.*$/g, '').trim();
        return s;
      };

      // ====== 字段提取 ======
      // 风鸟工商信息表通常是 <table><tr><td>字段名</td><td>值</td><td>状态</td></tr> 结构
      // 改进策略：精准定位"字段名"所在的 td，取紧邻的下一个兄弟 td（跳过"复制"等噪声节点）
      const findValueByLabel = (labels) => {
        if (!Array.isArray(labels)) labels = [labels];
        for (const label of labels) {
          // 策略1：找 td/th/.label 文本完全等于 label 的，取紧邻的下一个兄弟 td
          const cells = Array.from(document.querySelectorAll('td, th, .label, .field-label, [class*="label"]'));
          for (const cell of cells) {
            const cellText = (cell.textContent || '').trim();
            if (cellText === label) {
              let nextEl = cell.nextElementSibling;
              // 跳过纯文本节点和"复制"按钮
              while (nextEl) {
                const text = (nextEl.textContent || '').trim();
                if (text && text !== label && !/^(复制|查看|关联)$/.test(text) && !nextEl.classList.contains('copy-btn')) {
                  break;
                }
                nextEl = nextEl.nextElementSibling;
              }
              if (nextEl) {
                const value = (nextEl.textContent || '').trim();
                if (value && value !== label && value.length < 300) {
                  // 排除状态值
                  if (!/^(在营|存续|注销|吊销|迁出|迁入|停业|清算|破产|正常|非正常|停产|撤销|开业|在业)$/.test(value)) {
                    return value;
                  }
                }
              }
            }
          }
          // 策略2：在 tr 容器内，找含 label 的 td，取紧邻的下一个 td
          const rows = Array.from(document.querySelectorAll('tr, .row, [class*="row"], [class*="item"]'));
          for (const row of rows) {
            const tds = Array.from(row.querySelectorAll('td, dt, .label, .field-label, [class*="label"]'));
            const labelTd = tds.find(td => (td.textContent || '').trim() === label || (td.textContent || '').trim().includes(label));
            if (labelTd) {
              let nextEl = labelTd.nextElementSibling;
              while (nextEl) {
                const text = (nextEl.textContent || '').trim();
                if (text && text !== label && !/^(复制|查看|关联)$/.test(text)) break;
                nextEl = nextEl.nextElementSibling;
              }
              if (nextEl) {
                const value = (nextEl.textContent || '').trim();
                if (value && value !== label && value.length < 300 && !/^(在营|存续|注销|吊销|迁出|迁入|停业|清算|破产|正常|非正常|停产|撤销|开业|在业)$/.test(value)) {
                  return value;
                }
              }
            }
          }
          // 策略3：在任意含 label 的容器里找 .value/.content 子元素
          const containers = Array.from(document.querySelectorAll('.info-item, .detail-row, .field-item, .basic-info, .detail-item, [class*="info"], [class*="detail"], [class*="field"]'));
          for (const c of containers) {
            const text = c.textContent || '';
            if (text.includes(label)) {
              const valueEl = c.querySelector('.value, .content, dd, .field-value, span:last-child, p:last-child');
              const value = valueEl ? (valueEl.textContent || '').trim() : '';
              if (value && value !== label && value.length < 300 && !/^(在营|存续|注销|吊销|迁出|迁入|停业|清算|破产|正常|非正常|停产|撤销|开业|在业)$/.test(value)) {
                return value;
              }
            }
          }
        }
        return '';
      };

      // 公司名：从详情页可能找不到正确的 h1（可能选到 section 标题），所以返回空让上层用原 company.companyName
      const h1El = document.querySelector('h1');
      const h1Text = h1El?.textContent?.trim() || '';
      const companyNameFromPage = (h1Text.length >= 4 && /公司|集团|厂|店|中心/.test(h1Text) && !/信息图|详情|股东|风险|关联|图谱/.test(h1Text)) ? h1Text : '';

      // 联系方式（电话/邮箱）- 通过正则匹配整页文本
      const phoneMatch = (document.body.innerText.match(/(?:电话|联系电话|联系方式|手机|手机号)[:：\s]*([0-9\-+]{6,20})/) || [])[1] || '';
      const emailMatch = (document.body.innerText.match(/(?:邮箱|电子邮件|E-mail|email)[:：\s]*([\w.+-]+@[\w-]+\.[\w.-]+)/) || [])[1] || '';

      return {
        companyName: companyNameFromPage,
        creditCode: cleanValue(findValueByLabel(['统一社会信用代码', '信用代码', '信用证号'])),
        legalPerson: cleanLegalPerson(findValueByLabel(['法定代表人', '法人代表', '法人'])),
        registeredCapital: cleanValue(findValueByLabel(['注册资本'])),
        // 用户要求新增字段
        establishDate: cleanValue(findValueByLabel(['成立日期', '成立时间', '注册日期'])),
        // 所属地区：风鸟可能显示为"所属地区"、"地区"、"省份"
        area: cleanValue(findValueByLabel(['所属地区', '地区', '省份', '行政区域'])),
        // 所属行业
        industry: cleanValue(findValueByLabel(['所属行业', '行业', '行业分类'])),
        // 注册地址
        registeredAddress: cleanValue(findValueByLabel(['注册地址', '住所', '企业地址', '注册地'])),
        // 通信地址（用户要求新增）
        contactAddress: cleanValue(findValueByLabel(['通信地址', '通讯地址', '联系地址', '办公地址'])),
        businessScope: cleanValue(findValueByLabel(['经营范围', '经营行业范围', '经营业务范围'])),
        contactPhone: phoneMatch,
        contactEmail: emailMatch,
        rawHtml: document.body.innerHTML.substring(0, 10000)
      };
    });

    // 详情提取结果调试日志
    logger.info(`风鸟详情提取完成: ${detail.companyName || companyName}, 信用代码=${detail.creditCode || '无'}, 法人=${detail.legalPerson || '无'}, 成立日期=${detail.establishDate || '无'}, 地区=${detail.area || '无'}, 行业=${detail.industry || '无'}`);

    return { ...detail, screenshotPath, detailUrl };
  }

  // 通过公司名在风鸟搜索并找到该公司详情页 URL
  // 实现：访问首页→输入公司名→搜索→在搜索结果中找到精确匹配的公司详情链接
  async searchCompanyDetailUrl(companyName) {
    // 访问首页
    await this.page.goto(this.baseUrl, { waitUntil: 'domcontentloaded' });
    await this.page.waitForTimeout(1500);

    // 找搜索框
    const searchInput = await this.page.$(
      'input[type="text"]:not([disabled]):not([readonly]), ' +
      'input[type="search"], ' +
      'input[placeholder*="搜索"], input[placeholder*="企业"], input[placeholder*="公司"], ' +
      'input[placeholder*="输入"], input[placeholder*="查询"], ' +
      'input[name*="search"], input[name*="keyword"], input[name*="key"]'
    );
    if (!searchInput) {
      throw new Error('风鸟首页未找到搜索框');
    }

    await searchInput.fill('');
    await searchInput.fill(companyName);
    await this.page.waitForTimeout(300);

    // 找搜索按钮，否则按回车
    const searchBtn = await this.page.$(
      'button[type="submit"], button[class*="search"], button[class*="Search"], ' +
      '[class*="search-btn"], [class*="searchBtn"], ' +
      'a[class*="search"], svg[class*="search"], [class*="icon-search"]'
    );
    if (searchBtn) {
      await searchBtn.click();
    } else {
      await searchInput.press('Enter');
    }

    await this.page.waitForLoadState('domcontentloaded').catch(() => {});
    await this.page.waitForTimeout(2500);
    logger.info(`风鸟详情搜索提交后 URL: ${this.page.url()}`);

    // 在搜索结果中找完全匹配 companyName 的链接
    const match = await this.page.evaluate((targetName) => {
      const allLinks = Array.from(document.querySelectorAll('a'));
      // 优先匹配链接文本完全等于公司名的
      for (const a of allLinks) {
        const text = (a.textContent || '').trim();
        if (text === targetName && a.href && a.href.startsWith('http')) {
          return { href: a.href, text };
        }
      }
      // 其次匹配链接文本包含公司名 且 href 含 company/firm/detail 的
      for (const a of allLinks) {
        const text = (a.textContent || '').trim();
        const href = a.href || '';
        if (text.includes(targetName)
          && (href.includes('/company/') || href.includes('/firm/') || href.includes('/detail/'))) {
          return { href, text };
        }
      }
      // 再次匹配链接文本以公司名开头的
      for (const a of allLinks) {
        const text = (a.textContent || '').trim();
        if (text.startsWith(targetName) && a.href && a.href.startsWith('http') && !a.href.includes('javascript:')) {
          return { href: a.href, text };
        }
      }
      return null;
    }, companyName);

    return match ? match.href : '';
  }

  // 文件名清理：去除Windows不允许的字符
  sanitizeFilename(name) {
    if (!name) return 'unknown';
    return String(name).replace(/[\\/:*?"<>|]/g, '_').substring(0, 50);
  }

  // 截图
  async takeScreenshot(prefix, pageNum) {
    const dir = path.resolve(process.env.SCREENSHOT_DIR || './screenshots');
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });

    const safePrefix = this.sanitizeFilename(prefix);
    const filename = `fnb_${safePrefix}_p${pageNum}_${Date.now()}.png`;
    const filepath = path.join(dir, filename);
    await this.page.screenshot({ path: filepath, fullPage: true });
    logger.info(`风鸟截图已保存: ${filepath}`);
    return filepath;
  }

  // 翻到下一页
  async gotoNextPage() {
    try {
      const nextBtn = await this.page.$(
        '.next:not(.disabled), .pagination-next:not(.disabled), [aria-label="下一页"]:not(.disabled), ' +
        'a.next:not(.disabled), .ant-pagination-next:not(.disabled), [class*="next"]:not(.disabled)'
      );
      if (nextBtn) {
        await nextBtn.click();
        await this.page.waitForLoadState('domcontentloaded').catch(() => {});
        await this.page.waitForTimeout(1500);
        return true;
      }
      return false;
    } catch (e) {
      logger.warn('风鸟翻页失败: ' + e.message);
      return false;
    }
  }

  // 关闭浏览器（保留持久化数据，下次启动自动带登录态，无需重新扫码）
  async close() {
    if (this.context) {
      await this.context.close().catch(() => {});
      this.context = null;
      this.page = null;
      this.browser = null;
      logger.info('风鸟浏览器已关闭，登录态已持久化保存');
    }
  }
}

module.exports = FnbCollector;
