const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const logger = require('../utils/logger');
const cookieManager = require('./cookieManager');
const { killBrowsersUsingProfile, firstLineOf } = require('../utils/browserProfile');

/**
 * 企查查采集器
 * 基于 Playwright 实现企查查搜索、详情、登录Cookie管理等核心采集逻辑
 */
class QccCollector {
  constructor(options = {}) {
    this.browser = null;
    this.context = null;
    this.page = null;
    // 采集模式使用独立的 userDataDir，避免与登录浏览器单例冲突
    this.captureMode = options.captureMode || false;
    // 无头开关覆盖：登录任务必须"可见窗口"（用户要在弹出的浏览器里扫码/输验证码），
    // 但登录任务仍需与采集任务共用同一个持久化目录（captureMode=true），
    // 故把"用哪个目录"和"是否无头"解耦，由调用方显式指定 headless。
    this.headlessOverride = typeof options.headless === 'boolean' ? options.headless : null;
  }

  // 初始化浏览器
  //
  // 重要改造：使用 launchPersistentContext 替代 launch + newContext
  // 浏览器数据目录持久化到磁盘，登录态（Cookie/localStorage/sessionStorage）会被保存
  // 下次启动浏览器自动带登录态，无需重复扫码登录
  async init() {
    if (this.context) {
      logger.warn('浏览器已存在，跳过初始化');
      return;
    }

    // 持久化目录：登录任务用 qcc，采集任务用 qcc-capture（避免单例冲突）
    const dirName = this.captureMode ? 'browser-data/qcc-capture' : 'browser-data/qcc';
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

    // 采集任务默认用无头模式：不再在桌面弹窗，用户不会误关（服务部署后没人能进服务器桌面，
    // 窗口被关 = 采集立即失败，实测失败原因就是 "Target page, context or browser has been closed"）。
    // 登录任务默认**可见窗口**：用户要直接在浏览器里扫码/输验证码（可用 LOGIN_HEADLESS=true 改回无头）。
    const headless = this.headlessOverride !== null
      ? this.headlessOverride
      : (this.captureMode ? process.env.CAPTURE_HEADLESS !== 'false' : process.env.HEADLESS === 'true');
    logger.info(`企查查启动持久化浏览器，userDataDir=${userDataDir}, headless=${headless}, captureMode=${this.captureMode}`);
    const launchOptions = {
      headless,
      // slowMo 默认 0：Playwright 的"慢动作"只用于人工观察自动化过程，
      // 开着会让每次点击/按键硬等 500ms（实测一次点击 2.1 秒、输 1 个字符 1 秒），
      // 远程投屏交互必须为 0；要观察采集过程时用 .env 的 SLOW_MO 临时调大。
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
      logger.info('企查查使用系统 Edge 浏览器启动（反爬指纹更真实）');
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
    logger.info('浏览器初始化完成（持久化模式）');
  }

  // 加载Cookie并访问首页（平台隔离：优先读本任务Cookie，其次读企查查专属登录Cookie login_qcc.json）
  async loadLoginState(taskId) {
    const cookies = await cookieManager.loadCookieArray(taskId, 'qcc');
    if (cookies && cookies.length) {
      await this.context.addCookies(cookies);
      logger.info(`任务${taskId} Cookie加载成功`);
      return true;
    }
    logger.warn(`任务${taskId} 无Cookie，需要先登录`);
    return false;
  }

  // ========== 登录状态检测 ==========
  // 判据只有两条（2026-09-22 重做，依据 8 服务器实测）：
  //   ① 是否还停在登录页：/weblogin（二维码页），停在即未登录
  //   ② 页面 header 是否还挂着「登录 | 注册」：挂着即未登录，消失即已登录
  //
  // 【永久禁令】不得再用 `.qrcode` / `img[src*="qr"]` 这类元素当"未登录入口"判据：
  //   企查查首页 footer（footer-top clearfix）里**常驻**一个宽高为 0（不可见）的 DIV.qrcode，
  //   querySelector 不看可见性 → 该判据恒为真 → 已登录也会被判成未登录。
  //   实测后果（2026-09-22 8 服务器）：连续 7 分钟、100+ 次判定全部"未登录"，扫码成功也
  //   永远不落盘 Cookie → 前端一直"未保存登录态 / 请扫码"，采集任务直接 LOGIN_REQUIRED。
  // 【同样禁令】不得再用"退出/个人中心/会员中心"这类正向文案当已登录证据：
  //   企查查全程不出现这些字（2026-09-14 起多次实测），只会让已登录永远判不出来。

  // 是否停在企查查登录页。只认 pathname 以 /weblogin 开头：
  // 旧写法 /\/login/i 会被 URL 里的 back=%2Flogin 误命中，把首页当登录页。
  _isLoginPath(u) {
    try {
      return new URL(String(u)).pathname.startsWith('/weblogin');
    } catch (e) {
      return false;
    }
  }

  // header 未登录时固定显示的文案（实测：登录后该文案从 header 消失）。
  // 同时兼容 "登录 | 注册"（实测形态）与 "登录/注册"（旧形态，防止企查查改文案后判据失效）
  _homepageShowsLoggedOut(txt) {
    return /登录\s*[|｜]\s*注册|登录\s*\/\s*注册/.test(String(txt || ''));
  }

  // 登录页地址（集中一处，避免 .env 与代码各自写一份）
  loginUrl() {
    return process.env.QCC_LOGIN_URL || 'https://www.qcc.com/weblogin';
  }

  /**
   * 判断"这个页面自己"是否处于登录态（纯 DOM 读取 + 读 URL，不跳转、不开新标签页）。
   * 空白/未渲染完的页面一律判未登录（防"假成功"：页面还在加载时既无登录入口也无文案）。
   * @param {Object} page Playwright page
   * @returns {Promise<boolean>}
   */
  async _pageShowsLoggedIn(page) {
    try {
      if (!page || page.isClosed()) return false;
      const txt = await page.evaluate(() => (document.body ? document.body.innerText : '')).catch(() => '');
      const t = String(txt || '').trim();
      if (t.length < 200) return false;             // 页面没渲染出来 → 不判已登录
      if (this._isLoginPath(page.url())) return false;   // 还停在登录页 = 未登录
      if (this._homepageShowsLoggedOut(t)) return false; // header 仍挂「登录 | 注册」= 未登录
      return true;
    } catch (e) {
      logger.warn(`企查查登录态判定异常: ${e.message}`);
      return false;
    }
  }

  // 主页面自身是否已登录（纯 DOM 读取，不跳转、不开新标签页）- 用于 waitForLogin 每轮快速判定
  async _checkLoginDom() {
    return this._pageShowsLoggedIn(this.page);
  }

  // `_nb_` 前缀会话 Cookie 是否出现（值非空）。
  // 【2026-09-22 降级】只作"触发一次首页复核"的信号，**不再单独当作已登录的判决**：
  //   企查查给匿名访客也可能下发非空 `_nb_`。实测 2026-09-18 17:35/17:52/17:57 三次都按
  //   这条 Cookie 判了"登录成功"并落盘 8 条 Cookie，随后采集任务仍然报 LOGIN_REQUIRED
  //   —— 典型的假阳性。而 2026-09-22 的 8 服务器匿名态里连 `_nb_` 都没有，两个方向都不可靠。
  // 判决一律以 _pageShowsLoggedIn（首页 header 是否还挂「登录 | 注册」）为准。
  async _hasAuthCookie() {
    try {
      const cookies = await this.context.cookies();
      return cookies.some((c) => /qcc\.com$/i.test(c.domain)
        && /^_nb_/.test(c.name)
        && !!String(c.value || '').trim());
    } catch (e) {
      return false;
    }
  }

  // 登录态校验：开一个**新标签页**访问首页复核（主页面＝二维码页完全不动，避免把用户
  // 正在扫的页面跳走）。用于采集任务启动前的登录校验、以及登录流程里的低频兜底复核。
  // 判据复用 _pageShowsLoggedIn：首页 header 挂着「登录 | 注册」= 未登录，该入口消失 = 已登录。
  async checkLogin() {
    let probe = null;
    try {
      if (!this.page || this.page.isClosed()) return false;
      probe = await this.context.newPage();
      await probe.goto('https://www.qcc.com/', { waitUntil: 'domcontentloaded', timeout: 30000 });
      await probe.waitForLoadState('networkidle').catch(() => {});
      const loggedIn = await this._pageShowsLoggedIn(probe);
      logger.info(loggedIn
        ? '企查查登录态校验：已登录（首页 header 已无「登录 | 注册」入口）'
        : `企查查登录态校验：未登录（${this._isLoginPath(probe.url()) ? '落在登录页' : '首页仍有「登录 | 注册」入口或页面未渲染完'}）`);
      return loggedIn;
    } catch (e) {
      logger.warn(`企查查checkLogin异常: ${e.message}`);
      return false;
    } finally {
      if (probe) await probe.close().catch(() => {});
      // 复核完把二维码页切回前台，保证截图/渲染正常
      if (this.page && !this.page.isClosed()) await this.page.bringToFront().catch(() => {});
    }
  }

  // 搜索企业
  async searchCompanies(keyword, maxPages = 5) {
    const results = [];
    logger.info(`开始搜索: ${keyword}, 最大页数: ${maxPages}`);

    // 访问搜索页
    const searchUrl = `https://www.qcc.com/web/search?key=${encodeURIComponent(keyword)}`;
    await this.page.goto(searchUrl, { waitUntil: 'domcontentloaded', timeout: 45000 }).catch((e) => {
      // 打开异常也继续往下走：至少把当前页截下来/解析一次，让日志留下真实现场
      logger.warn(`企查查搜索页打开异常（继续尝试解析）: ${e.message}`);
    });
    // 【必须设防】企查查搜索页带长连接与埋点轮询，networkidle 常常永远不达成，
    // 原来这里是不带 catch 的 waitForLoadState('networkidle') → 30 秒超时抛错，
    // 整个采集任务直接失败（用户看到的失败原因：page.waitForLoadState: Timeout 30000ms exceeded）。
    // 改为：限时 8 秒等 networkidle（失败不抛错）+ 等搜索结果容器出现，超时就往下走，
    // 由后面的验证码检测 / 提取结果 / 截图日志暴露真实原因。
    await this.page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
    const resultReady = await this.page.waitForSelector(
      '.maintable, .app-lists, .search-result-item, .list-item, table tbody tr',
      { timeout: 15000 }
    ).then(() => true).catch(() => false);
    if (!resultReady) {
      const debug = await this.page.evaluate(() => ({
        url: window.location.href,
        title: document.title
      })).catch(() => ({}));
      logger.warn(`企查查搜索结果容器未出现（url=${debug.url || '未知'}, title=${debug.title || '未知'}），继续尝试解析`);
    }

    // 验证码 / 风控页检测：企查查风控页标题是"用户验证 - 企查查"、限流页是"访问限制 - 企查查"，
    // 而老选择器（.captcha/.slider）不一定命中。
    // 命中即中止任务并给出中文原因——否则会静默采到 0 家，用户以为是"没数据"
    //（实测 2026-09-16：关键词"电子"命中"用户验证"；高频重跑后全部命中"访问限制"）。
    const pageInfo = await this.page.evaluate(() => ({
      title: document.title || '',
      text: (document.body ? document.body.innerText : '').slice(0, 800)
    })).catch(() => ({ title: '', text: '' }));
    const riskTitle = /(用户验证|安全验证|访问验证|访问限制|访问受限|验证 -)/;
    const riskText = /(请完成安全验证|拖动滑块|滑动验证|验证后继续|请进行验证|点击进行验证|访问过于频繁|请求过于频繁|访问频率|请稍后再试)/;
    if ((await this.checkCaptcha()) || riskTitle.test(pageInfo.title) || riskText.test(pageInfo.text)) {
      logger.warn(`企查查命中验证/风控页: title=${pageInfo.title}`);
      throw new Error(`CAPTCHA_REQUIRED: 企查查要求人工验证或已触发访问限制（页面标题：${pageInfo.title || '未知'}）。请稍等几分钟不要连续重跑，再在「采集平台登录」页点「清除登录态并重新扫码」后重试`);
    }

    // 解析搜索结果列表
    for (let pageNum = 1; pageNum <= maxPages; pageNum++) {
      logger.info(`正在采集第${pageNum}页`);

      // 截图当前页
      const screenshotPath = await this.takeScreenshot(keyword, pageNum);

      // 提取企业列表
      const companies = await this.extractCompanyList();
      // 一家都提不到、且结果容器从未出现 → 页面没正常加载（限流/风控/改版）。
      // 不能静默算 0 家往下走，否则用户看到"已完成 0 条"却不知道原因
      if (companies.length === 0 && !resultReady) {
        throw new Error(`PAGE_NOT_READY: 企查查搜索结果页未正常加载（页面标题：${pageInfo.title || '未知'}），可能触发了访问限制/风控，请稍后再试`);
      }
      results.push(...companies.map(c => ({ ...c, screenshotPath, searchKeyword: keyword, pageNum })));

      // 翻到下一页
      if (pageNum < maxPages) {
        const nextSuccess = await this.gotoNextPage();
        if (!nextSuccess) break;
        await this.page.waitForTimeout(parseInt(process.env.CAPTURE_INTERVAL || '8000'));
      }
    }

    logger.info(`搜索完成: ${keyword}, 共采集${results.length}家企业`);
    return results;
  }

  // 检查验证码
  async checkCaptcha() {
    const captcha = await this.page.$('.captcha, .verify-wrap, .nc_iconfont, .slider');
    if (captcha) {
      logger.warn('检测到验证码');
      return true;
    }
    return false;
  }

  // 提取企业列表
  // 2026-09-16 修复：企查查搜索结果已改版为 <table class="app-ltable"> 的行结构，
  // 老选择器 .search-result-item / .list-item 一个都匹配不到 → 采 0 家（实测：
  // 用登录态打开搜索页，table.app-ltable 有 22 个 tr，每行第一个 a[href*="/firm/"] 就是公司名+详情链接）。
  // 现按"行内第一个企业详情链接"提取，并保留老选择器兜底。
  async extractCompanyList() {
    const items = await this.page.evaluate(() => {
      const clean = (s) => (s || '').replace(/\s+/g, ' ').trim();
      const cards = Array.from(document.querySelectorAll('table.app-ltable tr'));
      const useCards = cards.length > 0
        ? cards
        : Array.from(document.querySelectorAll('.search-result-item, .list-item'));
      const list = useCards.map((el) => {
        const a = el.querySelector('a[href*="/firm/"]');
        if (a) {
          return { companyName: clean(a.textContent), detailUrl: a.getAttribute('href') || '', rawText: clean(el.textContent).slice(0, 500) };
        }
        // 兜底：老结构里从 .title/.company-name 取名字
        const nameEl = el.querySelector('.title, .company-name');
        const linkEl = el.querySelector('a[href*="firm"]');
        return {
          companyName: clean(nameEl ? nameEl.textContent : ''),
          detailUrl: (linkEl && linkEl.getAttribute('href')) || '',
          rawText: clean(el.textContent).slice(0, 500)
        };
      }).filter((c) => c.companyName && c.companyName.length >= 4);
      // 按详情链接去重（同一行可能挂多个企业链接，取到的始终是行内第一个）
      const seen = new Set();
      return list.filter((c) => {
        const key = c.detailUrl || c.companyName;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      });
    });
    logger.info(`企查查提取到${items.length}家企业`);
    return items;
  }

  // 获取企业详情并截图
  // 参数：
  //   detailUrl   - 详情页 URL
  //   companyName - 公司名称（可选，detailUrl 为空时未实现兜底，仅作日志记录）
  //
  // 重要修复：原实现使用 jQuery 伪选择器（:has / :contains）配合 document.querySelector，
  // 这些伪选择器是 jQuery 专属语法，浏览器原生 querySelector 不支持，会静默返回 null，
  // 导致 creditCode/legalPerson/registeredCapital/registeredAddress/businessScope 全部为空。
  // 改为标准 DOM 遍历：按 label 文本匹配 td/label，取相邻 value 节点。
  async getCompanyDetail(detailUrl, companyName) {
    if (!detailUrl) {
      logger.warn(`企查查无 detailUrl，跳过详情采集（companyName=${companyName || '无'}）`);
      return { detailError: '企查查无 detailUrl，无法采集详情' };
    }
    logger.info(`访问企业详情: ${detailUrl} (companyName=${companyName || '无'})`);
    const _t0 = Date.now();
    await this.page.goto(detailUrl, { waitUntil: 'domcontentloaded' });
    const _t1 = Date.now();
    await this.page.waitForTimeout(2000);
    const _t2 = Date.now();

    const hasCaptcha = await this.checkCaptcha();
    if (hasCaptcha) {
      throw new Error('CAPTCHA_REQUIRED: 详情页验证码');
    }

    // 截图详情页
    const screenshotPath = await this.takeScreenshot('detail_' + Date.now(), 1);
    const _t3 = Date.now();

    // 提取详情（标准 DOM 遍历，不使用 jQuery 伪选择器）
    const detail = await this.page.evaluate(() => {
      // ====== 值清洗 ======
      const cleanValue = (v) => {
        if (!v) return '';
        return String(v)
          .replace(/复制点击复制$/g, '')
          .replace(/点击复制$/g, '')
          .replace(/复制$/g, '')
          .replace(/关联\d+家企业>?\s*$/g, '')
          .replace(/查看\d+家企业>?\s*$/g, '')
          .replace(/>\s*$/g, '')
          .trim();
      };

      // 法人名去重（企查查有时把法人名重复渲染）
      const cleanLegalPerson = (v) => {
        let s = cleanValue(v);
        if (!s) return '';
        const len = s.length;
        for (let n = Math.floor(len / 2); n >= 2; n--) {
          if (s.substring(0, n) === s.substring(n, n * 2)) {
            s = s.substring(0, n);
            break;
          }
        }
        return s;
      };

      // ====== 按字段名取值（标准 DOM 遍历，多策略）======
      // 策略1: table 结构 - 找到 td/th/.label 文本完全等于 label，取紧邻下一个 td
      // 策略2: 任意 .detail-item / .info-item 容器，含 label 文本，找 .value/.content 子元素
      // 策略3: 全文正则兜底
      const findValueByLabel = (labels) => {
        if (!Array.isArray(labels)) labels = [labels];
        for (const label of labels) {
          // 策略1: table 行内匹配
          const cells = Array.from(document.querySelectorAll('td, th, .label, .field-label, [class*="label"], dt'));
          for (const cell of cells) {
            const cellText = (cell.textContent || '').trim();
            if (cellText === label || cellText.replace(/[:：\s]/g, '') === label) {
              let nextEl = cell.nextElementSibling;
              while (nextEl) {
                const text = (nextEl.textContent || '').trim();
                if (text && text !== label && !/^(复制|查看|关联)$/.test(text) && !nextEl.classList.contains('copy-btn')) {
                  break;
                }
                nextEl = nextEl.nextElementSibling;
              }
              if (nextEl) {
                const value = (nextEl.textContent || '').trim();
                if (value && value !== label && value.length < 500) {
                  if (!/^(在营|存续|注销|吊销|迁出|迁入|停业|清算|破产|正常|非正常|停产|撤销|开业|在业)$/.test(value)) {
                    return value;
                  }
                }
              }
            }
          }

          // 策略2: 在 tr/.row/[class*="item"] 容器内匹配
          const rows = Array.from(document.querySelectorAll('tr, .row, [class*="row"], [class*="item"], .detail-item, .info-item'));
          for (const row of rows) {
            const labelTexts = Array.from(row.querySelectorAll('td, dt, .label, .field-label, [class*="label"], .title'));
            const labelEl = labelTexts.find(el => {
              const t = (el.textContent || '').trim();
              return t === label || t.includes(label) || t.replace(/[:：\s]/g, '') === label;
            });
            if (labelEl) {
              // 优先取 .value/.content 子元素
              const valueEl = row.querySelector('.value, .content, dd, .field-value, span:last-child, p:last-child');
              if (valueEl) {
                const value = (valueEl.textContent || '').trim();
                if (value && value !== label && value.length < 500 && !/^(在营|存续|注销|吊销|迁出|迁入|停业|清算|破产|正常|非正常|停产|撤销|开业|在业)$/.test(value)) {
                  return value;
                }
              }
              // 退而求其次：取紧邻的兄弟节点
              let nextEl = labelEl.nextElementSibling;
              while (nextEl) {
                const text = (nextEl.textContent || '').trim();
                if (text && text !== label && !/^(复制|查看|关联)$/.test(text)) break;
                nextEl = nextEl.nextElementSibling;
              }
              if (nextEl) {
                const value = (nextEl.textContent || '').trim();
                if (value && value !== label && value.length < 500 && !/^(在营|存续|注销|吊销|迁出|迁入|停业|清算|破产|正常|非正常|停产|撤销|开业|在业)$/.test(value)) {
                  return value;
                }
              }
            }
          }

          // 策略3: 在含 label 文本的任意容器内找 .value
          const containers = Array.from(document.querySelectorAll('.detail-item, .info-item, .basic-info, .field-item, [class*="info"], [class*="detail"], [class*="field"]'));
          for (const c of containers) {
            const text = c.textContent || '';
            if (text.includes(label)) {
              const valueEl = c.querySelector('.value, .content, dd, .field-value, span:last-child, p:last-child');
              const value = valueEl ? (valueEl.textContent || '').trim() : '';
              if (value && value !== label && value.length < 500 && !/^(在营|存续|注销|吊销|迁出|迁入|停业|清算|破产|正常|非正常|停产|撤销|开业|在业)$/.test(value)) {
                return value;
              }
            }
          }
        }

        // 策略4: 全文正则兜底（label: value 或 label：value 格式）
        for (const label of labels) {
          const re = new RegExp(label + '[:：]\\s*([^\\n\\r<>]{2,200})', 'i');
          const m = (document.body.innerText || '').match(re);
          if (m && m[1]) {
            const value = m[1].trim();
            if (!/^(在营|存续|注销|吊销|迁出|迁入|停业|清算|破产|正常|非正常|停产|撤销|开业|在业)$/.test(value)) {
              return value;
            }
          }
        }

        return '';
      };

      // 公司名：优先取 header/h1，校验合理性
      const h1El = document.querySelector('h1, .header-title, .company-name');
      const h1Text = h1El?.textContent?.trim() || '';
      const companyNameFromPage = (h1Text.length >= 4 && /公司|集团|厂|店|中心/.test(h1Text) && !/信息图|详情|股东|风险|关联|图谱/.test(h1Text)) ? h1Text : '';

      // 联系方式（电话/邮箱）- 全文正则
      const phoneMatch = (document.body.innerText.match(/(?:电话|联系电话|联系方式|手机|手机号)[:：\s]*([0-9\-+]{6,20})/) || [])[1] || '';
      const emailMatch = (document.body.innerText.match(/(?:邮箱|电子邮件|E-mail|email)[:：\s]*([\w.+-]+@[\w-]+\.[\w.-]+)/) || [])[1] || '';

      return {
        companyName: companyNameFromPage,
        creditCode: cleanValue(findValueByLabel(['统一社会信用代码', '信用代码', '信用证号'])),
        legalPerson: cleanLegalPerson(findValueByLabel(['法定代表人', '法人代表', '法人'])),
        registeredCapital: cleanValue(findValueByLabel(['注册资本'])),
        registeredAddress: cleanValue(findValueByLabel(['注册地址', '住所', '企业地址', '注册地'])),
        // 新增字段：与 Lead 实体对齐
        establishDate: cleanValue(findValueByLabel(['成立日期', '成立时间', '注册日期'])),
        area: cleanValue(findValueByLabel(['所属地区', '地区', '省份', '行政区域'])),
        industry: cleanValue(findValueByLabel(['所属行业', '行业', '行业分类'])),
        contactAddress: cleanValue(findValueByLabel(['通信地址', '通讯地址', '联系地址', '办公地址'])),
        businessScope: cleanValue(findValueByLabel(['经营范围', '经营业务范围'])),
        contactPhone: phoneMatch,
        contactEmail: emailMatch,
        companyStatus: cleanValue(findValueByLabel(['经营状态', '企业状态', '状态'])),
        // 原始 HTML 供后续 AI 识别或排查（截断到 10000 字符）
        rawHtml: document.body.innerHTML.substring(0, 10000)
      };
    });

    const _t4 = Date.now();

    // 详情提取结果调试日志（便于排查字段提取是否成功）
    logger.info(`企查查详情提取完成: ${detail.companyName || companyName || '无'}, 信用代码=${detail.creditCode || '无'}, 法人=${detail.legalPerson || '无'}, 成立日期=${detail.establishDate || '无'}, 地区=${detail.area || '无'}, 注册资本=${detail.registeredCapital || '无'}`);
    // 耗时打点（仅企查查启用，供阶段B提速定量参考）：详情加载 / 固定等待 / 截图 / 解析 四段
    logger.info(`[耗时打点] ${companyName || '无'} 详情加载=${_t1 - _t0}ms 固定等待=${_t2 - _t1}ms 截图=${_t3 - _t2}ms 解析=${_t4 - _t3}ms`);

    return { ...detail, screenshotPath, detailUrl };
  }

  // 文件名清理：去除路径分隔符与Windows不允许的字符（keyword 来自请求体，未清理时可路径穿越写文件）
  sanitizeFilename(name) {
    if (!name) return 'unknown';
    return String(name).replace(/[\\/:*?"<>|]/g, '_').substring(0, 50);
  }

  // 截图
  async takeScreenshot(prefix, pageNum) {
    const dir = path.resolve(process.env.SCREENSHOT_DIR || './screenshots');
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });

    const safePrefix = this.sanitizeFilename(prefix);
    const filename = `${safePrefix}_p${pageNum}_${Date.now()}.png`;
    const filepath = path.join(dir, filename);
    await this.page.screenshot({ path: filepath, fullPage: true });
    logger.info(`截图已保存: ${filepath}`);
    return filepath;
  }

  // 翻到下一页
  // 2026-09-16 修复：企查查分页是 ul.pagination（"下一页"是文本为 > 的 li/a，禁用时 li 带 disabled），
  // 老选择器 .next / [aria-label="下一页"] 在企查查上不存在 → 多页任务会静默只采第 1 页。
  async gotoNextPage() {
    try {
      const nextBtn = await this.page.$('.next:not(.disabled), [aria-label="下一页"]:not(.disabled)');
      if (nextBtn) {
        await nextBtn.click();
        // 与搜索页同因：networkidle 在企查查上常常不达成，限时且不抛错
        await this.page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
        return true;
      }
      // 企查查分页：点“>”（最后一条可用页项），禁用态不点
      const paginated = await this.page.evaluate(() => {
        const ul = document.querySelector('ul.pagination');
        if (!ul) return false;
        const li = Array.from(ul.querySelectorAll('li')).find((el) => {
          const a = el.querySelector('a');
          if (!a || a.textContent.trim() !== '>') return false;
          const cls = `${el.className} ${a.className}`;
          return !/disabled/i.test(cls);
        });
        if (!li) return false;
        const a = li.querySelector('a');
        a.click();
        return true;
      });
      if (paginated) {
        await this.page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
        await this.page.waitForTimeout(parseInt(process.env.CAPTURE_INTERVAL || '8000'));
        return true;
      }
      logger.info('企查查未找到可用的“下一页”按钮，本关键词只采集已取到的页');
      return false;
    } catch (e) {
      logger.warn('翻页失败: ' + e.message);
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
      logger.info('浏览器已关闭，登录态已持久化保存');
    }
  }

  // 准备二维码：打开企查查登录页并截图二维码，立即返回二维码路径
  // 不跳转页面，不做checkLogin()的跳转逻辑，仅用DOM检测
  // 二次验证：仅用DOM检测确认已登录才返回alreadyLoggedIn
  async prepareQrCode(taskId) {
    await this.init();
    // 打开登录页
    // 必须用 /weblogin（真实登录页）。用 /login 会被重定向成
    // /weblogin?back=%2Flogin，扫码成功后企查查按 back 参数把浏览器送回不存在的
    // /login → 用户看到"扫码后直接 404"。
    await this.page.goto(this.loginUrl(), { waitUntil: 'domcontentloaded' });
    // 只等页面可交互即可，不再等二维码"出现"——那个选择器在企查查上根本不命中，
    // 每等一次就要白烧 10 秒（这是"获取二维码很慢"的主因之一）。
    // 二维码的实际定位与重试交给 capture.js 的 findQrElement/captureQrShot。
    await this.page.waitForTimeout(600);

    // 用纯DOM检测当前登录态（不跳转页面）
    // 注意：企查查登录页本身无法判断登录态，需要跳首页检查
    // 但我们不想在等待扫码期间跳走，所以这里只做初步检测
    try {
      // 检测当前页面是否已有用户信息（可能持久化cookie已自动登录）
      // 【2026-09-22】这里只算"线索"，不作判决：命中后必须交给 checkLogin()（新标签页看首页
      // header 是否还挂「登录 | 注册」）确认。实测 23:15 那次线索命中、但当时的 checkLogin
      // 因 .qrcode 误判恒为未登录 → 明明浏览器已登录，还是给用户出了二维码。
      const txt = await this.page.evaluate(() => (document.body ? document.body.innerText : '')).catch(() => '');
      const hasLoggedInEvidence = /(退出|注销|个人中心|会员中心|我的订单)/.test(txt) && !/(扫码登录|二维码|登录账号|手机登录)/.test(txt);
      if (hasLoggedInEvidence) {
        // 当前页已有登录态证据（可能首页自动加载了），再跳首页确认
        logger.info(`任务${taskId} 企查查prepareQrCode检测到可能的登录态，跳转首页确认`);
        const confirmed = await this.checkLogin();
        if (confirmed) {
          const cookies = await this.context.cookies();
          await cookieManager.saveCookie(taskId, cookies);
          logger.info(`任务${taskId} 企查查确认已登录，自动保存Cookie（共${cookies.length}条）`);
          return { success: true, qrPath: null, alreadyLoggedIn: true, cookieCount: cookies.length };
        }
      }
    } catch (e) {
      logger.warn(`任务${taskId} 企查查prepareQrCode检测登录态异常: ${e.message}`);
    }

    // 未登录：截图二维码区域
    const qrDir = path.resolve('./storage/qrcodes');
    if (!fs.existsSync(qrDir)) fs.mkdirSync(qrDir, { recursive: true });
    const qrPath = path.join(qrDir, `${taskId}_login.png`);

    // 等待二维码出现（仅作短暂兜底；选择器在企查查上不命中，等太久纯属浪费）
    // 【2026-09-17】这里原来固定 waitForSelector 2 秒：该选择器在企查查上从不命中，
    //   相当于每点一次「打开采集浏览器」就白等 2 秒。二维码的定位/裁剪由 capture.js 的
    //   findQrElement + captureQrShot 负责，这里只需尽快截一张兜底图。
    await this.page.screenshot({ path: qrPath });
    logger.info(`任务${taskId} 企查查二维码已截图保存: ${qrPath}`);
    return { success: true, qrPath, alreadyLoggedIn: false };
  }

  /**
   * 主页面被点走/被站点跳走时，回到登录页重新出二维码。
   * 为什么必须有（2026-09-22 实测踩坑）：用户在实时画面上误点企查查页头，页面从 /weblogin
   * 跳到首页 → 二维码元素消失，旧实现只是"保留上一张二维码图"，前端一直显示那张**已失效的
   * 二维码**，用户反复扫死码却没有任何提示，只能靠人工重开浏览器。
   * @returns {Promise<boolean>} true = 确实重新导航回了登录页
   */
  async reopenLoginPage() {
    if (!this.page || this.page.isClosed()) return false;
    if (this._isLoginPath(this.page.url())) return false;
    const url = this.loginUrl();
    await this.page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await this.page.waitForTimeout(600);
    logger.warn(`企查查主页面已离开登录页，已自动回到登录页重新出二维码: ${url}`);
    return true;
  }

  // 等待用户扫码登录完成（最长5分钟），保存Cookie
  // 严格验证：轮询调用_checkLoginDom()（纯DOM，不跳转）验证登录状态
  // 新增：浏览器存活心跳 + 页面崩溃检测
  async waitForLogin(taskId) {
    try {
      if (!this.page || this.page.isClosed()) {
        return { success: false, error: '浏览器已关闭，请重新打开登录页' };
      }
      const deadline = Date.now() + 300000;
      // 最近一次"开新标签页去首页复核"的时间：复核最密 5 秒一次（见循环内 ②）
      let lastProbeAt = 0;
      let heartbeatCount = 0;
      let consecutiveHeartbeatFails = 0;
      while (Date.now() < deadline) {
        if (!this.page || this.page.isClosed()) {
          logger.warn(`任务${taskId} 企查查浏览器被关闭，终止等待`);
          return { success: false, error: '浏览器已关闭' };
        }
        heartbeatCount++;
        if (heartbeatCount % 20 === 0) {
          try {
            const alive = await this.page.evaluate(() => document.readyState).catch(() => null);
            if (alive === null) {
              consecutiveHeartbeatFails++;
              logger.warn(`任务${taskId} 企查查心跳检测失败(${consecutiveHeartbeatFails}/3)`);
              if (consecutiveHeartbeatFails >= 3) {
                logger.error(`任务${taskId} 企查查浏览器页面无响应，终止等待`);
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
          // ① 主页面自身判定（纯 DOM 读取，不跳转、不开新标签页）：
          //    企查查登录页固定是 /weblogin，扫码成功后企查查会让**这个页面自己**跳到首页 →
          //    用同一套「登录 | 注册」判据即可，登录成功通常 1~2 秒就能判出来。
          //    旧实现是每 3~4 秒开一个新标签页去首页复核（7 分钟 100+ 次）——实测把企查查的
          //    风控招来了（浏览器里多出一个"用户验证"页签），已废弃。
          let loggedIn = await this._checkLoginDom();
          // ② 兜底复核（新标签页，绝不动二维码页）：每 8 轮（≈12 秒）一次；
          //    出现 _nb_ 会话 Cookie 时立即触发（企查查匿名态一般没有该 Cookie，不会刷屏）；
          //    并加 5 秒最小间隔硬兜底，避免把复核请求打成风暴。
          if (!loggedIn) {
            const needProbe = heartbeatCount % 8 === 0 || await this._hasAuthCookie();
            if (needProbe && Date.now() - lastProbeAt >= 5000) {
              lastProbeAt = Date.now();
              loggedIn = await this.checkLogin();
            }
          }
          if (loggedIn) {
            const cookies = await this.context.cookies();
            await cookieManager.saveCookie(taskId, cookies);
            logger.info(`任务${taskId} 企查查登录成功，Cookie已保存（共${cookies.length}条）`);
            return { success: true };
          }
        } catch (e) {
          // ignore
        }
        // 轮询间隔 3000ms → 1500ms：让"扫完码后页面反应过来"更快
        await this.page.waitForTimeout(1500);
      }
      logger.warn(`任务${taskId} 企查查登录等待超时（5分钟）`);
      return { success: false, error: '登录超时（5分钟内未完成扫码登录）' };
    } catch (e) {
      logger.error(`任务${taskId} 企查查waitForLogin异常: ${e.message}`);
      return { success: false, error: e.message };
    }
  }

  // 扫码登录获取Cookie（兼容旧调用，等价于 prepareQrCode + waitForLogin）
  async scanLogin(taskId) {
    const qrResult = await this.prepareQrCode(taskId);
    if (!qrResult.success) return qrResult;
    const loginResult = await this.waitForLogin(taskId);
    return { ...loginResult, qrPath: qrResult.qrPath };
  }
}

module.exports = QccCollector;
