# 服务器自建语音识别（ASR）· 部署步骤（8cloud / Windows）

> 对应立项登记表：`.trae/documents/服务器自建中文语音识别_立项登记表.md`
> 目标：**语音识别放到你自己的服务器上，用户端零安装、不需要任何厂商 Key、识别不联网**。

---

## 0. 这套东西是什么

- 一个**常驻小服务**：收到 WAV 音频 → 返回中文文字；**只监听 `127.0.0.1:6006`**（外网/局域网都打不到，安全）。
- ERP 后端通过配置 `ai.asr.provider=local` 把语音识别指向它；前端仍是"🎤 录音 → 上传"，**前端与接口路径都没变**。
- 效果：用户**不用装任何东西、不用联网**；管理员也不用买/配任何语音识别 Key。

---

## 1. 需要放到服务器上的文件

在服务器上建目录 `C:\smart-erp-asr\`，按下面放：

| 内容 | 从哪来 | 放到哪 |
|---|---|---|
| 服务代码（`server.js`、`package.json`、`node_modules\`） | 开发机 `d:\erp\prodsrc\智能ERP-项目\asr-service\` | `C:\smart-erp-asr\service\` |
| 识别模型（约 228MB 目录） | 见下方两种取法 | `C:\smart-erp-asr\models\` |
| Node 便携版（`node.exe` 等） | 见下方两种取法 | `C:\smart-erp-asr\node\` |
| 部署脚本（`install-asr.ps1`、`uninstall-asr.ps1`） | 开发机 `asr-service\deploy\` | `C:\smart-erp-asr\deploy\` |

**模型取法（二选一）**
- ① 直接在服务器上下载（推荐，省得拷 228MB）——服务器能上 hf-mirror 时用这条：
  ```powershell
  $d = 'C:\smart-erp-asr\models\sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17'
  New-Item -ItemType Directory -Force $d
  $base = 'https://hf-mirror.com/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main'
  curl.exe -L --retry 3 -o "$d\model.int8.onnx" "$base/model.int8.onnx"
  curl.exe -L --retry 3 -o "$d\tokens.txt"      "$base/tokens.txt"
  ```
- ② 从开发机整体拷贝已下好的目录：`d:\erp\prodsrc\智能ERP-项目\asr-service\models\sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17` → `C:\smart-erp-asr\models\`

**Node 便携版取法（二选一）**
- ① 服务器上下载（国内镜像，约 30MB）：
  ```powershell
  curl.exe -L -o "$env:TEMP\node.zip" https://registry.npmmirror.com/-/binary/node/v20.18.1/node-v20.18.1-win-x64.zip
  Expand-Archive "$env:TEMP\node.zip" -DestinationPath "$env:TEMP\node_extract" -Force
  New-Item -ItemType Directory -Force 'C:\smart-erp-asr\node'
  Copy-Item "$env:TEMP\node_extract\node-v20.18.1-win-x64\*" 'C:\smart-erp-asr\node\' -Recurse -Force
  ```
- ② 开发机上 `D:\erp\` 已有 Node（v20.18.1），可把 `C:\Program Files\nodejs` 整个拷到 `C:\smart-erp-asr\node\`（不改系统 PATH）。

---

## 2. 安装（一条命令，幂等，可重复执行）

在服务器上以**管理员身份**打开 PowerShell：

```powershell
powershell -ExecutionPolicy Bypass -File C:\smart-erp-asr\deploy\install-asr.ps1
```

它会依次做并打印结果：
1. 检查文件是否齐全（node / server.js / 模型两个文件）
2. 注册**开机自启**计划任务 `SmartERP-ASR`（与现有 `SmartERP-Backend` 同一套路，并设置**失败自动重启**）
3. 启动服务 → 等 `/health` 就绪
4. 打印自检：模型名、加载耗时、监听地址

**验收（必须做）**
```powershell
# ① 健康检查应返回 {"ok":true,...}
curl.exe -s http://127.0.0.1:6006/health
# ② 只应看到回环地址（不应出现 0.0.0.0:6006）
netstat -ano | findstr 6006
# ③ 用真实中文音频测一次（把任意 16k 单声道 wav 换进来）
#    返回 {"text":"...","ms":123} 即成功
```

---

## 3. 让 ERP 用上它（切一个配置，立即生效、无需重启后端）

发布时 `100_ai_asr_local_provider.sql` 会自动插入 3 条配置（默认 `qwen`）。确认部署好自建服务后，执行：

```sql
UPDATE sys_config SET config_value = 'local', updated_at = NOW()
 WHERE config_key = 'ai.asr.provider';
```

（以后也可以在 设置中心 → AI 渠道配置 里切换，见立项登记表的后续可选项）

**验收**：ERP 里点 🎤 说一句 → 文字出现在输入框；后端日志出现
`[语音识别] 本机服务 url=http://127.0.0.1:6006/asr, 状态=200, 耗时=xxx ms`。

---

## 4. 回滚（都在两分钟内）

| 想回到什么状态 | 怎么做 |
|---|---|
| 只想不语音识别 | 把 `ai.asr.provider` 改回 `'qwen'`（**立即生效**，不用重启） |
| 彻底停掉自建服务 | `powershell -File C:\smart-erp-asr\deploy\uninstall-asr.ps1`（注销计划任务 + 停进程，文件保留） |
| 服务坏了暂时不管它 | 不影响打字与小助手其它功能；🎤 会给出**明确提示**，不会静默、不会白屏 |

---

## 5. 常见问题

| 现象（ERP 里的提示） | 原因与处理 |
|---|---|
| 「本机语音识别服务未启动或不可达」 | 服务没起来：`schtasks /run /tn SmartERP-ASR`，然后看 `C:\smart-erp-asr\logs\asr.out.log` |
| 「本机语音识别只接受 WAV 音频」 | 浏览器页面是**旧版本**（还在传 webm）：让用户 `Ctrl+Shift+R` 硬刷新 |
| 「未识别到有效语音内容」 | 说话太短/太轻或环境噪音；属正常提示，重说一次即可 |
| 「本机语音识别超时」 | 服务器负载高或录音过长；可把 `ai.asr.local.timeout` 调大（5~60） |
| 识别不准 | 模型偏小（int8 量化）：可换同系列更大模型，替换 `models\` 目录后重启任务即可 |
| 占资源 | 进程常驻约 **350MB 内存**、CPU 空闲时几乎为 0；建议服务器空闲内存 ≥1GB |
| 多人同时说话 | 服务串行解码（4.7 秒音频约 0.4 秒），一般够用；并发很高再评估多进程 |

---

## 6. 日志与自愈

- 日志目录：`C:\smart-erp-asr\logs\`（`asr.out.log` / `asr.err.log`，启动时按天轮转，避免无限增长）
- 计划任务 `SmartERP-ASR`：开机自启 + 进程退出自动重启；查看状态：`schtasks /query /tn SmartERP-ASR /v /fo LIST`
