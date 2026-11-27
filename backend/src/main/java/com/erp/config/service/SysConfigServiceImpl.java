package com.erp.config.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.entity.SysConfig;
import com.erp.config.mapper.SysConfigMapper;
import com.erp.tenant.TenantContextHolder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    private Cache<String, SysConfig> configCache;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        configCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
        try {
            ensureTableExists();
            ensureColumnsExist();
            // 启动阶段没有租户上下文，预热只会按 default 数据源（租户1的库）加载，
            // 反而把租户1的配置灌进全局缓存。改为各租户首次访问时懒加载。
        } catch (Exception e) {
            log.warn("SysConfig initialization failed: {}", e.getMessage());
        }
    }

    /**
     * 幂等：给已存在的 sys_config 表补齐缺失的列。
     * 主要是历史上用 CREATE TABLE IF NOT EXISTS 建表时漏了 config_section，
     * 以及后续新增 config_type / config_options 列时没跑增量，导致新版本 JAR 启动直接 PSQLException "column does not exist"。
     */
    private void ensureColumnsExist() {
        alterColumnIfMissing("config_section", "VARCHAR(100)");
        alterColumnIfMissing("config_type",   "VARCHAR(20)");
        alterColumnIfMissing("config_options","TEXT");
    }

    private void alterColumnIfMissing(String column, String type) {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                " WHERE table_schema = current_schema() " +
                "   AND table_name   = 'sys_config' " +
                "   AND column_name  = ?",
                Integer.class,
                column
            );
            if (exists == null || exists == 0) {
                jdbcTemplate.execute("ALTER TABLE sys_config ADD COLUMN " + column + " " + type);
                log.info("sys_config: added missing column {} {}.", column, type);
            }
        } catch (Exception e) {
            log.warn("sys_config: ensure column {} failed (non-fatal, continuing): {}", column, e.getMessage());
        }
    }

    private void ensureTableExists() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_config", Integer.class);
            log.debug("sys_config table exists.");
        } catch (Exception e) {
            log.info("sys_config table not found, creating...");
            try {
                String createSql = """
                    CREATE TABLE IF NOT EXISTS sys_config (
                        id BIGSERIAL PRIMARY KEY,
                        config_key VARCHAR(100) NOT NULL,
                        config_value TEXT,
                        config_name VARCHAR(200),
                        config_group VARCHAR(50),
                        config_section VARCHAR(100),
                        config_type VARCHAR(20),
                        config_options TEXT,
                        sort_order INT DEFAULT 0,
                        remark VARCHAR(500),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        created_by VARCHAR(50),
                        updated_by VARCHAR(50)
                    )
                    """;
                jdbcTemplate.execute(createSql);
                try {
                    jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_config_key ON sys_config(config_key)");
                    jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_config_group ON sys_config(config_group)");
                } catch (Exception ex) {
                    log.warn("Create index failed: {}", ex.getMessage());
                }
                log.info("sys_config table created successfully.");
            } catch (Exception ex) {
                log.error("Create sys_config table failed: {}", ex.getMessage());
                throw ex;
            }
        }
    }

    /** 缓存 key 必须带租户前缀，否则多租户共用一个 JVM 时会互相读到对方的配置 */
    private String cacheKey(String configKey) {
        return TenantContextHolder.cacheKeyPrefix() + configKey;
    }

    @Override
    public String getValue(String key) {
        SysConfig config = configCache.getIfPresent(cacheKey(key));
        if (config != null) {
            return config.getConfigValue();
        }
        try {
            config = getOne(new QueryWrapper<SysConfig>().eq("config_key", key));
        } catch (Exception e) {
            // 典型：列缺失、连接异常等——不要把异常甩给 @PostConstruct 的 Bean.init()
            // 否则会直接 UnsatisfiedDependencyException 把 ApplicationContext 拉垮（502）。
            // 这里降级：cache 不存，调用方直接拿 defaultValue。
            log.warn("SysConfig.getValue({}) query failed, degrade to defaultValue. Error: {}", key, e.getMessage());
            return null;
        }
        if (config != null) {
            configCache.put(cacheKey(key), config);
            return config.getConfigValue();
        }
        return null;
    }

    @Override
    public String getValue(String key, String defaultValue) {
        String value = getValue(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public int getIntValue(String key, int defaultValue) {
        String value = getValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse int value for key: {}, value: {}", key, value);
            return defaultValue;
        }
    }

    @Override
    public boolean getBooleanValue(String key, boolean defaultValue) {
        String value = getValue(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateValue(String key, String value) {
        SysConfig config = getOne(new QueryWrapper<SysConfig>().eq("config_key", key));
        if (config == null) {
            // 不存在则自动创建（upsert 语义，与 batchUpdate 行为一致）
            SysConfig newConfig = new SysConfig();
            newConfig.setConfigKey(key);
            newConfig.setConfigValue(value);
            boolean saved = save(newConfig);
            if (saved) {
                configCache.put(cacheKey(key), newConfig);
            }
            return saved;
        }
        config.setConfigValue(value);
        boolean result = updateById(config);
        if (result) {
            configCache.put(cacheKey(key), config);
        }
        return result;
    }

    @Override
    public List<SysConfig> listByGroup(String group) {
        return list(new QueryWrapper<SysConfig>()
                .eq("config_group", group)
                .orderByAsc("sort_order", "id"));
    }

    @Override
    public List<SysConfig> listAll() {
        return list(new QueryWrapper<SysConfig>().orderByAsc("config_group", "sort_order", "id"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchUpdate(List<SysConfig> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        boolean result = true;
        for (SysConfig config : list) {
            if (config.getId() != null) {
                result = updateById(config) && result;
                if (config.getConfigKey() != null) {
                    SysConfig updated = getById(config.getId());
                    if (updated != null) {
                        configCache.put(cacheKey(updated.getConfigKey()), updated);
                    }
                }
            } else if (config.getConfigKey() != null) {
                // 按 configKey 做 upsert：存在则更新，不存在则新增
                QueryWrapper<SysConfig> qw = new QueryWrapper<>();
                qw.eq("config_key", config.getConfigKey());
                SysConfig existing = getOne(qw);
                if (existing != null) {
                    existing.setConfigValue(config.getConfigValue());
                    if (config.getConfigName() != null) existing.setConfigName(config.getConfigName());
                    if (config.getConfigGroup() != null) existing.setConfigGroup(config.getConfigGroup());
                    if (config.getConfigType() != null) existing.setConfigType(config.getConfigType());
                    result = updateById(existing) && result;
                    configCache.put(cacheKey(existing.getConfigKey()), existing);
                } else {
                    result = save(config) && result;
                    configCache.put(cacheKey(config.getConfigKey()), config);
                }
            }
        }
        return result;
    }

    @Override
    public void refreshCache() {
        // 只失效当前租户的 key；不再调用 loadAllToCache() 预热（预热会按当前线程的租户上下文灌数据，
        // 无上下文时等于把 default 库的数据写进全局缓存）
        String prefix = TenantContextHolder.cacheKeyPrefix();
        configCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        log.info("SysConfig cache refreshed. tenant={}", TenantContextHolder.cachePrefix());
    }
}
