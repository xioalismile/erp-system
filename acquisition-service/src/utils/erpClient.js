const axios = require('axios');
const logger = require('./logger');

/**
 * ERP API 客户端
 * 封装调用 ERP 后端接口的工具方法
 *
 * 改造说明：
 *   旧版直接调用 ERP API，没有 JWT Token，导致 401 未授权，采集结果无法上传
 *   新版启动时自动登录获取 token，所有调用都带 Authorization: Bearer {token}
 *   通过环境变量 ERP_AUTH_USERNAME / ERP_AUTH_PASSWORD 配置登录账号
 *   默认使用 admin/admin123（与前端默认账号一致）
 */
/**
 * 校验 ERP 的业务返回码。
 * 【重要】ERP 后端的 Result.error 返回的是 HTTP 200 + body {code:400,...}，
 * 只判断 HTTP 状态码会把业务失败当成成功（历史 Bug：日志打"上传成功"，
 * 实际采集结果 0 条落库，用户看到"任务已完成 10 条、采集结果却是空的"）。
 * @param {Object} data ERP 响应体
 * @param {string} action 动作名，用于拼错误信息
 * @returns {Object} 校验通过时原样返回
 */
function assertErpOk(data, action) {
  if (data && typeof data.code === 'number' && data.code !== 200) {
    throw new Error(`${action}被ERP拒绝(code=${data.code}): ${data.message || '无返回信息'}`);
  }
  return data;
}

const erpClient = {
  // 缓存的 JWT token
  cachedToken: null,
  // token 过期时间（毫秒），JWT 默认 24 小时，这里设置 23 小时避免临界过期
  tokenExpireAt: 0,
  // token 过期阈值（提前1小时刷新）
  TOKEN_REFRESH_THRESHOLD: 60 * 60 * 1000,

  /**
   * 获取 ERP 基础地址
   */
  getBaseURL() {
    return process.env.ERP_API_BASE || 'http://localhost:8080/api';
  },

  /**
   * 获取登录账号（从环境变量读取，默认 admin/admin123）
   */
  getAuthCredentials() {
    return {
      username: process.env.ERP_AUTH_USERNAME || 'admin',
      password: process.env.ERP_AUTH_PASSWORD || 'admin123'
    };
  },

  /**
   * 登录 ERP 获取 JWT Token
   * @returns {Promise<string>} JWT token
   */
  async login() {
    const creds = this.getAuthCredentials();
    const url = `${this.getBaseURL()}/auth/login`;
    logger.info(`ERP登录: username=${creds.username}, url=${url}`);
    try {
      const resp = await axios.post(url, {
        username: creds.username,
        password: creds.password
      }, {
        timeout: 10000,
        headers: { 'Content-Type': 'application/json' }
      });
      const data = resp.data;
      // ERP 后端返回格式: { code: 200, data: { token: "xxx", username: "xxx" } }
      if (data && data.code === 200 && data.data && data.data.token) {
        this.cachedToken = data.data.token;
        // JWT 默认24小时过期，这里设置23小时后刷新
        this.tokenExpireAt = Date.now() + 23 * 60 * 60 * 1000;
        logger.info(`ERP登录成功: username=${creds.username}, token=${data.data.token.substring(0, 30)}...`);
        return this.cachedToken;
      }
      throw new Error(`ERP登录响应格式异常: ${JSON.stringify(data).substring(0, 200)}`);
    } catch (err) {
      const msg = err.response
        ? `status=${err.response.status}, data=${JSON.stringify(err.response.data)}`
        : err.message;
      logger.error(`ERP登录失败: ${msg}`);
      throw new Error(`ERP登录失败: ${msg}`);
    }
  },

  /**
   * 获取有效的 Token（过期则自动重新登录）
   * @returns {Promise<string>}
   */
  async getToken() {
    if (!this.cachedToken || Date.now() > this.tokenExpireAt - this.TOKEN_REFRESH_THRESHOLD) {
      logger.info('Token为空或即将过期，重新登录...');
      await this.login();
    }
    return this.cachedToken;
  },

  /**
   * 获取带认证的请求头
   */
  async getAuthHeaders(extraHeaders = {}) {
    const token = await this.getToken();
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      ...extraHeaders
    };
  },

  /**
   * 校验前端用户 token 是否仍然有效（未过期）
   * 用于采集任务在执行过程中检测启动者登录态，过期则停止采集
   * @param {string} callerToken 前端用户 JWT token
   * @returns {Promise<boolean>} true=有效，false=已过期/无效
   */
  async verifyCallerToken(callerToken) {
    if (!callerToken) {
      // 没传 token 视为不需要校验（兼容旧版调用）
      return true;
    }
    const url = `${this.getBaseURL()}/auth/userinfo`;
    try {
      const resp = await axios.get(url, {
        timeout: 8000,
        headers: {
          'Authorization': `Bearer ${callerToken}`
        }
      });
      // 200 表示 token 仍有效
      return resp.status === 200;
    } catch (err) {
      // 401 表示 token 失效（登录已过期）
      if (err.response && err.response.status === 401) {
        logger.warn(`callerToken 已失效（401）: 启动者登录过期`);
        return false;
      }
      // 其他错误（网络异常、500等）不阻止采集，避免误停
      logger.warn(`callerToken 校验异常（不阻止采集）: ${err.message}`);
      return true;
    }
  },

  /**
   * 批量上传采集结果到 ERP
   * 后端 /api/acquisition/result/batch 接口期望直接接收 List<AcquisitionResult> 作为请求体
   * 因此这里把 taskId 注入到每个 result 对象，然后直接发送数组
   * @param {string|number} taskId ERP 任务 ID
   * @param {Array} results 采集结果数组
   * @returns {Promise<Object>} ERP 响应
   */
  async postResultsToErp(taskId, results) {
    const url = `${this.getBaseURL()}/acquisition/result/batch`;
    // 把 taskId 注入到每个 result 对象
    // 重要：detailUrl → sourceUrl 映射（采集器返回 detailUrl，后端实体字段为 sourceUrl）
    //       所有扩展字段（contactAddress/area/establishDate/industry/companyStatus）显式映射，
    //       避免因采集器返回的字段大小写/命名差异导致数据丢失
    const payload = results.map(r => ({
      ...r,
      taskId: Number(taskId),
      // 标准化字段名（后端期望这些字段）
      resultId: r.resultId || null,
      companyName: r.companyName || r.company_name || '',
      creditCode: r.creditCode || r.credit_code || '',
      legalPerson: r.legalPerson || r.legal_person || '',
      registeredCapital: r.registeredCapital || r.registered_capital || '',
      registeredAddress: r.registeredAddress || r.registered_address || '',
      contactAddress: r.contactAddress || r.contact_address || '',
      area: r.area || '',
      establishDate: r.establishDate || r.establish_date || '',
      industry: r.industry || '',
      companyStatus: r.companyStatus || r.company_status || '',
      businessScope: r.businessScope || r.business_scope || '',
      contactPhone: r.contactPhone || r.contact_phone || '',
      contactEmail: r.contactEmail || r.contact_email || '',
      // detailUrl 映射到后端 sourceUrl 字段（AcquisitionResult.sourceUrl）
      sourceUrl: r.sourceUrl || r.source_url || r.detailUrl || r.detail_url || '',
      detailUrl: r.detailUrl || r.detail_url || '',
      screenshotPath: r.screenshotPath || r.screenshot_path || '',
      searchKeyword: r.searchKeyword || r.search_keyword || '',
      sourcePlatform: r.sourcePlatform || r.platform || 'fnb',
      reviewStatus: 'pending'
    }));
    logger.info(`上传采集结果到ERP: taskId=${taskId}, 数量=${payload.length}, url=${url}`);
    try {
      const headers = await this.getAuthHeaders();
      // 直接发送数组，不要包装成 {taskId, results}
      const resp = await axios.post(url, payload, {
        timeout: 30000,
        headers
      });
      // 必须校验业务码：后端 Result.error 是 HTTP 200 + body{code:400}，
      // 只看 HTTP 状态码会把"整批没落库"当成成功（历史 Bug：日志打"上传成功"，
      // 实际 0 条入库，用户看到"任务已完成 10 条、采集结果是空的"）
      assertErpOk(resp.data, '采集结果上传');
      logger.info(`采集结果上传成功: taskId=${taskId}, 响应=${JSON.stringify(resp.data)}`);
      return resp.data;
    } catch (err) {
      // 401 时清空 token 触发重新登录
      if (err.response && err.response.status === 401) {
        logger.warn('上传采集结果 401 未授权，清空token触发重新登录');
        this.cachedToken = null;
        this.tokenExpireAt = 0;
        // 重试一次
        try {
          const headers = await this.getAuthHeaders();
          const resp = await axios.post(url, payload, { timeout: 30000, headers });
          assertErpOk(resp.data, '采集结果上传(重试)');
          logger.info(`采集结果上传成功(重试): taskId=${taskId}, 响应=${JSON.stringify(resp.data)}`);
          return resp.data;
        } catch (retryErr) {
          const msg = retryErr.response
            ? `status=${retryErr.response.status}, data=${JSON.stringify(retryErr.response.data)}`
            : retryErr.message;
          logger.error(`采集结果上传失败(重试): taskId=${taskId}, ${msg}`);
          throw retryErr;
        }
      }
      const msg = err.response
        ? `status=${err.response.status}, data=${JSON.stringify(err.response.data)}`
        : err.message;
      logger.error(`采集结果上传失败: taskId=${taskId}, ${msg}`);
      throw err;
    }
  },

  /**
   * 查询 ERP 里"进行中"（running/pending）的采集任务。
   * 用途：采集服务启动自愈 —— 采集任务的运行状态只活在采集服务内存里，
   *      服务重启（发版/崩溃）会让进行中的任务凭空消失，ERP 侧状态永远停在"运行中"。
   * @returns {Promise<Array>} 任务列表（含 taskId/taskNo/status/capturedCount）
   */
  async listUnfinishedTasks() {
    const url = `${this.getBaseURL()}/acquisition/task/page?current=1&size=200`;
    const headers = await this.getAuthHeaders();
    const resp = await axios.get(url, { timeout: 15000, headers });
    const data = resp.data && resp.data.data;
    const records = (data && (data.records || data.list)) || [];
    return records.filter((t) => t && (t.status === 'running' || t.status === 'pending'));
  },

  /**
   * 更新 ERP 任务状态
   * @param {string|number} taskId ERP 任务 ID
   * @param {string} status 任务状态 (running/success/failed/stopped)
   * @param {number} capturedCount 已采集数量
   * @param {string} errorMessage 错误信息
   * @returns {Promise<Object>} ERP 响应
   */
  async updateTaskStatus(taskId, status, capturedCount = 0, errorMessage = '') {
    const url = `${this.getBaseURL()}/acquisition/task/${taskId}/status`;
    // ERP 侧 error_message 是 varchar(255)：失败原因是 Playwright 那种超长报错时，
    // 整条状态更新会被后端 400 拒绝 → 任务永远停在"运行中"、页面上看不到任何结果。
    // 这里统一截断（完整原文仍在采集服务本机日志里），保证任务状态一定能落库。
    const MAX_ERROR_LEN = 200;
    let errMsg = typeof errorMessage === 'string' ? errorMessage : String(errorMessage == null ? '' : errorMessage);
    if (errMsg.length > MAX_ERROR_LEN) {
      errMsg = errMsg.slice(0, MAX_ERROR_LEN - 1) + '…';
      logger.warn(`任务${taskId} 失败原因过长已截断到 ${MAX_ERROR_LEN} 字符后再回写 ERP（完整原文见本机日志）`);
    }
    logger.info(`更新任务状态: taskId=${taskId}, status=${status}, capturedCount=${capturedCount}`);
    try {
      const headers = await this.getAuthHeaders();
      const resp = await axios.put(url, {
        taskId,
        status,
        capturedCount,
        errorMessage: errMsg
      }, {
        timeout: 15000,
        headers
      });
      assertErpOk(resp.data, '任务状态更新');
      logger.info(`任务状态更新成功: taskId=${taskId}, status=${status}`);
      return resp.data;
    } catch (err) {
      // 401 时清空 token 触发重新登录，并重试一次
      if (err.response && err.response.status === 401) {
        logger.warn(`更新任务状态 401，清空token重新登录: taskId=${taskId}`);
        this.cachedToken = null;
        this.tokenExpireAt = 0;
        try {
          const headers = await this.getAuthHeaders();
          const resp = await axios.put(url, {
            taskId, status, capturedCount, errorMessage: errMsg
          }, { timeout: 15000, headers });
          assertErpOk(resp.data, '任务状态更新(重试)');
          logger.info(`任务状态更新成功(重试): taskId=${taskId}, status=${status}`);
          return resp.data;
        } catch (retryErr) {
          const msg = retryErr.response
            ? `status=${retryErr.response.status}, data=${JSON.stringify(retryErr.response.data)}`
            : retryErr.message;
          logger.error(`任务状态更新失败(重试): taskId=${taskId}, ${msg}`);
          throw retryErr;
        }
      }
      const msg = err.response
        ? `status=${err.response.status}, data=${JSON.stringify(err.response.data)}`
        : err.message;
      logger.error(`任务状态更新失败: taskId=${taskId}, ${msg}`);
      throw err;
    }
  }
};

module.exports = erpClient;
