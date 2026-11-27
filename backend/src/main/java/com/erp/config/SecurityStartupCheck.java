package com.erp.config;

import com.erp.auth.util.JwtSecretProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 安全启动校验（安全审计 P2）。
 *
 * <p>JWT 密钥的解析与"公开默认值"闸门已下沉到 {@link JwtSecretProvider}
 * （环境变量 &gt; 密钥文件 &gt; 首次自动生成；检测到历史公开默认值时默认拒绝启动）。
 * 本类保留：数据库密码默认值告警 + 汇总打印 JWT 密钥来源（不含密钥明文）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityStartupCheck {

    private static final String DEFAULT_DB_PASSWORD = "Erp@2026";

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    private final JwtSecretProvider jwtSecretProvider;

    @PostConstruct
    public void check() {
        int warnings = 0;
        if (DEFAULT_DB_PASSWORD.equals(dbPassword)) {
            log.warn("==============================================================");
            log.warn("【安全告警】数据库密码使用默认值（Erp@2026）！");
            log.warn("生产环境必须通过环境变量 ERP_DB_PASSWORD 覆盖（含平台库 ERP_PLATFORM_DB_PASSWORD）");
            log.warn("==============================================================");
            warnings++;
        }
        // JWT 密钥来源（不打印明文）：ops 只需看这一行即可确认用的是哪把密钥
        log.info("[安全启动校验] JWT 密钥来源={}", jwtSecretProvider.getSource());
        if (warnings == 0) {
            log.info("[安全启动校验] 未检测到默认数据库密码，通过");
        }
    }
}
