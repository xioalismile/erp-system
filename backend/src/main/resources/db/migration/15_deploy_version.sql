-- 15_deploy_version.sql  部署版本号管理（低代码平台式：版本库 + 服务器版本追踪）
-- 幂等：可重复执行

-- 1. 版本库表
CREATE TABLE IF NOT EXISTS deploy_version (
    id              BIGSERIAL PRIMARY KEY,
    version_no      VARCHAR(50)  NOT NULL,            -- 版本号，如 20260827.01（自动生成，可改）
    build_mode      VARCHAR(20)  NOT NULL DEFAULT 'full', -- full=全量包 / customer=客户包
    note            VARCHAR(500),                     -- 版本说明
    zip_path        VARCHAR(500) NOT NULL,            -- 版本包本地存储路径（deploy-packages/）
    zip_size        BIGINT,                           -- 字节
    zip_md5         VARCHAR(64),                      -- 完整性校验
    operator        VARCHAR(100),
    created_at      TIMESTAMP DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_deploy_version_no ON deploy_version (version_no);

-- 2. 服务器表加列：当前版本追踪
ALTER TABLE deploy_server ADD COLUMN IF NOT EXISTS current_version VARCHAR(50);
ALTER TABLE deploy_server ADD COLUMN IF NOT EXISTS version_deployed_at TIMESTAMP;
ALTER TABLE deploy_server ADD COLUMN IF NOT EXISTS build_mode VARCHAR(20) DEFAULT 'full';

-- 3. 部署日志加列：历史记录带版本号
ALTER TABLE deploy_log ADD COLUMN IF NOT EXISTS version_no VARCHAR(50);
