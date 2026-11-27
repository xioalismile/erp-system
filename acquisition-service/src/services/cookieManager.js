const fs = require('fs');
const path = require('path');
const logger = require('../utils/logger');

const COOKIE_DIR = path.resolve(__dirname, '../../storage/cookies');
const COOKIE_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000;

function ensureDir() {
  if (!fs.existsSync(COOKIE_DIR)) {
    fs.mkdirSync(COOKIE_DIR, { recursive: true });
    logger.info(`创建Cookie存储目录: ${COOKIE_DIR}`);
  }
}

/**
 * taskId 会被直接拼进文件名，必须白名单校验：
 * 未校验时传入 "../../xxx" 可让 path.join 逃出 storage/cookies，
 * 在服务器任意可写目录写入受控 JSON（POST /api/capture/save-cookie 无需登录即可触发）。
 * 合法值形态：login_qcc / login_tyc / login_aqc / login_fnb 等。
 */
const TASK_ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

function getCookiePath(taskId) {
  ensureDir();
  const id = taskId == null ? '' : String(taskId);
  if (!TASK_ID_PATTERN.test(id)) {
    throw new Error(`非法的 taskId（仅允许字母/数字/下划线/连字符，最长64位）: ${id}`);
  }
  return path.join(COOKIE_DIR, `${id}.json`);
}

/**
 * 判断单个 Cookie 是否仍然可用。
 * 【重要】Playwright 的 cookie.expires 单位是「秒」，会话 Cookie 为 -1。
 * 历史 Bug：原代码写成 `c.expires > Date.now()`（毫秒），导致**所有带过期时间的
 * Cookie 都被判为"已过期"并被丢弃**（日志表现为"保存Cookie时全部已过期"），
 * 进而出现"登录成功但采集报未登录"、"刚扫完码就判登录失败"。
 * @private
 */
function _isCookieAlive(c) {
  if (!c || typeof c.expires !== 'number') return true;
  if (c.expires <= 0) return true;                 // -1 / 0 = 会话 Cookie，必须保留
  return c.expires * 1000 > Date.now();            // 秒 → 毫秒 再比较
}

/**
 * 内部：验证Cookie数据是否仍然有效
 * 检查 savedAt 时间是否在有效期内，同时检查每个cookie的expires字段
 * @private
 */
function _validateCookieData(data) {
  if (!data || !data.savedAt) return false;
  const savedTime = new Date(data.savedAt).getTime();
  if (isNaN(savedTime)) return false;
  if ((Date.now() - savedTime) >= COOKIE_EXPIRE_MS) {
    logger.warn(`Cookie已过期(保存时间): savedAt=${data.savedAt}`);
    return false;
  }
  const cookies = data.cookies || [];
  if (cookies.length === 0) return false;
  let validCount = 0;
  for (const c of cookies) {
    if (_isCookieAlive(c)) validCount++;
  }
  if (validCount === 0) {
    logger.warn(`Cookie已过期(全部expires字段过期): 共${cookies.length}条，0条有效`);
    return false;
  }
  if (validCount < cookies.length) {
    logger.info(`Cookie部分过期: ${validCount}/${cookies.length}条有效`);
  }
  return true;
}

async function saveCookie(taskId, cookies) {
  const filePath = getCookiePath(taskId);
  try {
    const cookieList = Array.isArray(cookies) ? cookies : (cookies.cookies || []);
    const origins = Array.isArray(cookies) ? [] : (cookies.origins || []);
    const validCookies = cookieList.filter(_isCookieAlive);
    if (validCookies.length === 0 && cookieList.length > 0) {
      logger.warn(`保存Cookie时全部已过期: taskId=${taskId}, 原始${cookieList.length}条`);
      return false;
    }
    if (validCookies.length < cookieList.length) {
      logger.info(`保存Cookie过滤掉${cookieList.length - validCookies.length}条已过期的Cookie`);
    }
    const data = {
      taskId,
      cookies: validCookies,
      origins,
      savedAt: new Date().toISOString()
    };
    fs.writeFileSync(filePath, JSON.stringify(data, null, 2), 'utf-8');
    logger.info(`Cookie已保存: taskId=${taskId}, file=${filePath}, cookies数量=${validCookies.length}`);
    return true;
  } catch (err) {
    logger.error(`保存Cookie失败: taskId=${taskId}, ${err.message}`);
    return false;
  }
}

async function loadCookie(taskId, platform) {
  const filePath = getCookiePath(taskId);
  if (fs.existsSync(filePath)) {
    try {
      const raw = fs.readFileSync(filePath, 'utf-8');
      const data = JSON.parse(raw);
      if (!_validateCookieData(data)) {
        logger.warn(`Cookie已失效，删除: taskId=${taskId}`);
        try { fs.unlinkSync(filePath); } catch (e) {}
        return null;
      }
      logger.info(`Cookie已加载: taskId=${taskId}, cookies数量=${(data.cookies || []).length}`);
      return {
        cookies: data.cookies || [],
        origins: data.origins || [],
        savedAt: data.savedAt
      };
    } catch (err) {
      logger.error(`加载Cookie失败: taskId=${taskId}, ${err.message}`);
    }
  }

  if (platform) {
    const platformPath = getCookiePath(`login_${platform}`);
    if (fs.existsSync(platformPath)) {
      try {
        const raw = fs.readFileSync(platformPath, 'utf-8');
        const data = JSON.parse(raw);
        if (!_validateCookieData(data)) {
          logger.warn(`平台Cookie已失效: platform=${platform}`);
          try { fs.unlinkSync(platformPath); } catch (e) {}
          return null;
        }
        logger.info(`taskId=${taskId} 使用平台专属Cookie(${platform}): cookies数量=${(data.cookies || []).length}`);
        return {
          cookies: data.cookies || [],
          origins: data.origins || [],
          savedAt: data.savedAt
        };
      } catch (err) {
        logger.error(`加载平台Cookie失败(${platform}): ${err.message}`);
      }
    }
  }

  if (String(taskId) !== '0') {
    const globalPath = getCookiePath(0);
    if (fs.existsSync(globalPath)) {
      try {
        const raw = fs.readFileSync(globalPath, 'utf-8');
        const data = JSON.parse(raw);
        if (!_validateCookieData(data)) {
          logger.warn(`全局Cookie已失效`);
          try { fs.unlinkSync(globalPath); } catch (e) {}
          return null;
        }
        logger.info(`使用全局共享Cookie: cookies数量=${(data.cookies || []).length}, savedAt=${data.savedAt}`);
        return {
          cookies: data.cookies || [],
          origins: data.origins || [],
          savedAt: data.savedAt
        };
      } catch (err) {
        logger.error(`加载全局Cookie失败: ${err.message}`);
      }
    }
  }

  logger.warn(`Cookie文件不存在: taskId=${taskId}, platform=${platform || '无'}`);
  return null;
}

async function loadCookieArray(taskId, platform) {
  const state = await loadCookie(taskId, platform);
  return state ? state.cookies : null;
}

async function isCookieValid(taskId) {
  const filePath = getCookiePath(taskId);
  if (!fs.existsSync(filePath)) return false;
  try {
    const raw = fs.readFileSync(filePath, 'utf-8');
    const data = JSON.parse(raw);
    return _validateCookieData(data);
  } catch (err) {
    logger.error(`校验Cookie失败: taskId=${taskId}, ${err.message}`);
    return false;
  }
}

/**
 * 读取「平台登录态」文件本身，不做全局Cookie兜底。
 * 与 loadCookie 的区别：loadCookie 在平台文件缺失时会回退到全局 Cookie（0.json），
 * 那是给采集任务共用登录态用的；登录页要回答的是"这个平台自己是否已登录"，
 * 回退会张冠李戴（A 平台没登录也显示已登录）。
 * 已过期（含全部 Cookie 过期、超过 7 天保存窗口）视为未登录，返回 null。
 * 注意：不返回 Cookie 的 expires 时间——单条 Cookie（多为埋点/统计）到期时间短，
 * 拿来当"登录态有效期"会误导，登录态有效期统一按本模块的 7 天窗口口径展示。
 * @param {string} taskId 平台登录任务ID，如 login_qcc / login_fnb
 * @returns {Promise<{cookies: Array, savedAt: string}|null>}
 */
async function getPlatformLoginState(taskId) {
  const filePath = getCookiePath(taskId);
  if (!fs.existsSync(filePath)) return null;
  try {
    const data = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
    if (!_validateCookieData(data)) return null;
    return { cookies: data.cookies || [], savedAt: data.savedAt };
  } catch (err) {
    logger.error(`读取平台登录态失败: taskId=${taskId}, ${err.message}`);
    return null;
  }
}

async function deleteCookie(taskId) {
  const filePath = getCookiePath(taskId);
  try {
    if (fs.existsSync(filePath)) {
      fs.unlinkSync(filePath);
      logger.info(`Cookie已删除: taskId=${taskId}`);
      return true;
    }
    return false;
  } catch (err) {
    logger.error(`删除Cookie失败: taskId=${taskId}, ${err.message}`);
    return false;
  }
}

module.exports = {
  saveCookie,
  loadCookie,
  loadCookieArray,
  isCookieValid,
  getPlatformLoginState,
  deleteCookie,
  COOKIE_DIR,
  COOKIE_EXPIRE_MS
};
