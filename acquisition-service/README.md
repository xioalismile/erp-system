# acquisition-service 企业获客采集服务

基于 **Node.js + Express + Playwright** 的独立采集服务，提供 HTTP API 给 ERP 后端调用，实现企查查自动化采集。

## 功能特性

- 基于 Playwright 的浏览器自动化采集

- 企查查企业搜索、企业详情抓取

- Cookie 管理（保存、加载、过期校验、删除）

- 二维码扫码登录支持

- 采集过程截图存档，供 ERP 前端展示

- 异步任务执行，通过 captureId 轮询任务状态

- 验证码检测，抛出特定错误供 ERP 处理

- 采集结果自动回传 ERP 后端

- 完善的日志（控制台 + 文件）

## 目录结构

```
acquisition-service/
├── src/
│   ├── app.js                    # 主应用入口
│   ├── routes/
│   │   ├── capture.js            # 采集路由
│   │   └── health.js             # 健康检查路由
│   ├── services/
│   │   ├── qccCollector.js       # 企查查采集器（核心）
│   │   └── cookieManager.js      # Cookie 管理器
│   └── utils/
│       ├── logger.js             # 日志工具
│       └── erpClient.js          # ERP API 客户端
├── screenshots/                  # 截图存储目录（运行时自动创建）
├── storage/
│   ├── cookies/                  # Cookie 存储
│   └── qrcodes/                  # 登录二维码存储
├── logs/                         # 日志输出目录
├── .env.example                  # 环境变量示例
├── .gitignore
├── package.json
└── README.md
```

## 安装步骤

### 1. 安装依赖

```bash
cd acquisition-service
npm install
```

### 2. 安装 Playwright 浏览器

```bash
npx playwright install chromium
```

### 3. 配置环境变量

复制 `.env.example` 为 `.env` 并按需修改：

```bash
cp .env.example .env
```

### 4. 启动服务

```bash
# 生产模式
npm start

# 开发模式（文件改动自动重启）
npm run dev
```

启动成功后会输出：`采集服务启动: http://localhost:3005`

## 配置说明

| 环境变量               | 默认值                              | 说明                                                       |
| ------------------ | -------------------------------- | -------------------------------------------------------- |
| `PORT`             | 3005                             | 服务监听端口                                                   |
| `ERP_API_BASE`     | <http://localhost:8080/api>      | ERP 后端 API 基础地址                                          |
| `HEADLESS`         | false                            | 是否使用无头浏览器（调试时设为 false）                                   |
| `SLOW_MO`          | 500                              | 操作间隔毫秒数                                                  |
| `DEFAULT_TIMEOUT`  | 30000                            | 默认超时毫秒数                                                  |
| `CAPTURE_INTERVAL` | 8000                             | 翻页 / 关键词之间的间隔毫秒数（2026-09-16 由 3000 调为 8000：企查查按 IP+账号限流） |
| `SCREENSHOT_DIR`   | ./screenshots                    | 截图保存目录                                                   |
| `QCC_LOGIN_URL`    | <https://www.qcc.com/weblogin>   | 企查查登录 URL（必须用 /weblogin，用 /login 会因 back 参数导致扫码后 404）    |
| `QCC_SEARCH_URL`   | <https://www.qcc.com/web/search> | 企查查搜索 URL                                                |

## API 文档

### 1. 健康检查

`GET /api/health`

**响应**：

```json
{ "status": "ok", "timestamp": 1690000000000 }
```

### 2. 启动采集任务

`POST /api/capture/start`

**请求体**：

```json
{
  "taskId": "task-001",
  "keywords": ["科技公司", "贸易公司"],
  "platform": "qcc",
  "maxPages": 5,
  "maxCompanies": 50,
  "loginCookie": null
}
```

**响应**（立即返回，异步执行）：

```json
{
  "success": true,
  "message": "采集任务已接收，正在后台执行",
  "captureId": "uuid-xxxx-xxxx",
  "status": "pending"
}
```

### 3. 查询采集状态

`GET /api/capture/:captureId/status`

**响应**：

```json
{
  "success": true,
  "captureId": "uuid-xxxx-xxxx",
  "taskId": "task-001",
  "status": "running",
  "capturedCount": 12,
  "message": "",
  "createdAt": "...",
  "startedAt": "...",
  "finishedAt": null,
  "resultsCount": 12
}
```

任务状态枚举：

- `pending` - 已接收，等待执行

- `running` - 正在执行

- `success` - 成功完成

- `failed` - 执行失败

- `stopped` - 已停止

### 4. 停止采集

`POST /api/capture/:captureId/stop`

**响应**：

```json
{
  "success": true,
  "message": "停止请求已提交，等待当前页面处理完成后停止"
}
```

### 5. 获取企查查登录二维码

`GET /api/capture/qrcode/:taskId`

返回 PNG 图片（`Content-Type: image/png`）。

### 6. 保存 Cookie

`POST /api/capture/save-cookie`

**请求体**：

```json
{
  "taskId": "task-001",
  "cookies": [
    { "name": "...", "value": "...", "domain": ".qcc.com", "path": "/", "expires": 1690000000 }
  ]
}
```

**响应**：

```json
{ "success": true, "message": "Cookie保存成功" }
```

## 与 ERP 后端的对接

采集服务在采集过程中会调用 ERP 后端的以下接口：

### 1. 更新任务状态

`PUT ${ERP_API_BASE}/acquisition/task/{taskId}/status`

请求体：

```json
{
  "taskId": "task-001",
  "status": "running",
  "capturedCount": 12,
  "errorMessage": ""
}
```

### 2. 批量上传采集结果

`POST ${ERP_API_BASE}/acquisition/result/batch`

请求体：

```json
{
  "taskId": "task-001",
  "results": [
    {
      "companyName": "XXX科技有限公司",
      "detailUrl": "https://www.qcc.com/firm/xxx",
      "creditCode": "91110000XXXXX",
      "legalPerson": "张三",
      "registeredCapital": "1000万人民币",
      "registeredAddress": "...",
      "businessScope": "...",
      "screenshotPath": "/path/to/screenshot.png",
      "rawHtml": "...",
      "searchKeyword": "科技公司",
      "pageNum": 1
    }
  ]
}
```

## 错误处理

- **验证码**：当采集过程中检测到验证码时，会抛出 `CAPTCHA_REQUIRED: ...` 错误，调用方可根据该错误提示用户人工处理。

- **未登录**：未检测到登录态时抛出 `LOGIN_REQUIRED: ...`，需先调用扫码登录接口获取 Cookie。

- **Cookie 过期**：Cookie 有效期 7 天，过期后需重新登录获取。

## 日志

- 控制台输出（彩色）

- `logs/acquisition.log` 全量日志

- `logs/error.log` 错误日志

## 开发建议

1. **调试模式**：开发时将 `HEADLESS=false`，可以直观看到浏览器操作过程。
2. **首次登录**：首次使用需通过扫码登录获取 Cookie，后续可复用 Cookie 直到过期。
3. **采集频率**：通过 `CAPTURE_INTERVAL` 和 `SLOW_MO` 控制采集速度，避免触发反爬。
4. **截图存档**：所有截图路径会返回给 ERP，建议 ERP 端将路径入库，前端按路径展示。

