-- ====================================================================
-- 50: 下载日志表 + 导入任务表（设置中心「下载日志」「导入进度」页面支撑）
-- 背景：设置中心 KgwDownloadLog.vue 调用 /api/system/log/downloadPage 与
--       /api/system/log/download，KgwImportProgress.vue 调用 /api/system/log/importTasks，
--       原先三接口 404，本次补建表与后端接口。
-- 幂等：CREATE TABLE IF NOT EXISTS，重复执行零副作用。
-- ====================================================================

-- 下载日志表（导出/下载行为留痕，财务汇总导出等端点写入）
CREATE TABLE IF NOT EXISTS sys_download_log (
    id            BIGSERIAL PRIMARY KEY,
    operator      VARCHAR(50),
    file_name     VARCHAR(500),
    file_type     VARCHAR(20),
    file_size     BIGINT,
    ip            VARCHAR(50),
    download_time TIMESTAMP,
    created_at    TIMESTAMP DEFAULT now()
);

COMMENT ON TABLE sys_download_log IS '下载日志（设置中心-下载日志页面）';

-- 导入任务表（异步导入进度留痕；当前无写入方，先建表供页面查询，预期返回空列表）
CREATE TABLE IF NOT EXISTS sys_import_task (
    id            BIGSERIAL PRIMARY KEY,
    task_name     VARCHAR(200),
    import_type   VARCHAR(50),
    total         INT DEFAULT 0,
    success_count INT DEFAULT 0,
    failed_count  INT DEFAULT 0,
    created_by    VARCHAR(50),
    start_time    TIMESTAMP,
    end_time      TIMESTAMP,
    status        VARCHAR(20) DEFAULT 'pending',
    remark        VARCHAR(500),
    created_at    TIMESTAMP DEFAULT now()
);

COMMENT ON TABLE sys_import_task IS '导入任务（设置中心-导入进度页面）';
