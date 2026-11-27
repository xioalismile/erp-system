-- 100_ai_asr_local_provider.sql
-- 2026-09-17 语音识别新增「服务器自建识别」提供方（不再依赖外部厂商 Key、用户端零安装）
--
-- 背景：浏览器那条语音识别两条路都靠不住 ——
--   ① 云端识别：浏览器把音频送厂商云（Chrome→Google / Edge→微软），国内常连不通，
--      现象是"喊了没反应""🎤 点一下秒退且毫无提示"；
--   ② 设备端识别：语音包是**浏览器自己的组件**，网页无权安装、且每台终端各一份
--      （实测本机 Edge 153：available() 对 zh-CN/en-US 等**全部 unavailable**，install() 0ms 空操作）。
--   改为：**识别放到服务器上**（sherpa-onnx 常驻服务，只监听 127.0.0.1，**不需要任何 Key、不依赖外网**），
--   前端只负责"录音 → 上传"，用户端零安装、零下载。
--
-- 落位：config_group='ai'（与设置中心「AI 渠道配置」同组），后端 AiSpeechService 读取：
--   ai.asr.provider      = qwen | local        （默认 qwen = 与升级前**完全一致**）
--   ai.asr.local.url     = 自建识别服务地址      （默认 http://127.0.0.1:6006/asr）
--   ai.asr.local.timeout = 超时秒数（5~60）      （默认 30）
--
-- 幂等：WHERE NOT EXISTS —— 已存在的配置绝不被覆盖（重复执行零副作用）。
--   【铁律】禁止用 ON CONFLICT (config_key)：PG 环境该唯一约束可能缺失，会报
--   "没有匹配 ON CONFLICT 说明的唯一或者排除约束" 并中止整次部署（2026-09-12 事故，见 66/74 号脚本注释）。
--
-- 影响范围：仅新增 3 条配置数据，**无 DDL、不改业务表、不动历史数据**。
--   provider 保持 qwen（默认）时，语音识别行为与升级前一致 —— 就算没部署自建服务也不会报错。

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ai.asr.provider', 'qwen', '语音识别方式', 'ai', 'string', 50,
       'qwen=通义千问云端识别（需要 Key）；local=服务器自建识别（不需要 Key，需先在服务器部署识别服务）', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.asr.provider');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ai.asr.local.url', 'http://127.0.0.1:6006/asr', '自建识别服务地址', 'ai', 'string', 51,
       '仅当"语音识别方式=local"时生效；自建服务只监听回环地址，不对外开端口', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.asr.local.url');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ai.asr.local.timeout', '30', '自建识别超时(秒)', 'ai', 'number', 52,
       '允许 5~60 秒；超时按"识别失败"明确提示，不会无限等待（英文/非法值自动回落默认 30）', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.asr.local.timeout');
