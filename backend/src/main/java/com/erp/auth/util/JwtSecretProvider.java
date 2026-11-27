package com.erp.auth.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JWT 密钥来源解析（安全审计 V-1 修复：默认密钥独立化）。
 *
 * <p>历史问题：yml 里写死的 {@code DevLocalFixedJwtSecretKey_Erp2026_DoNotChange} 随源码与部署包外泄，
 * 而鉴权只依赖 HS256 签名、token 无服务端吊销 → 任何拿到源码的人都能自签 {@code sub=1}(admin)
 * 的 token 通过 {@code AuthInterceptor}，等同于免密接管系统。</p>
 *
 * <p>2026-08-27 曾评估过"直接删默认值"，但当时所有部署脚本都没有注入 ERP_JWT_SECRET，
 * 删掉默认值会让 8cloud/121 重启即挂，故只加了 WARN。本类用"空值即自举"解决该矛盾：</p>
 *
 * <p>密钥来源优先级：</p>
 * <ol>
 *   <li>环境变量 {@code ERP_JWT_SECRET} 非空 → 直接使用（显式注入，运维可控）。</li>
 *   <li>否则读密钥文件（{@code erp.security.jwt-secret-file}，默认工作目录下
 *       {@code erp-jwt-secret.key}）→ 存在即复用（重启不掉线）。</li>
 *   <li>否则生成 48 字节随机密钥并落盘（CREATE_NEW，并发安全）→ 首次启动零配置可用。</li>
 * </ol>
 *
 * <p>安全闸门：显式注入历史公开默认密钥时，除非 {@code erp.security.allow-dev-secret=true}
 * （仅本地开发），否则按 {@code erp.security.strict-secret}（默认 true）拒绝启动，
 * 防止"改回公开密钥"的回归。</p>
 *
 * <p>日志只打印来源与文件路径，<b>绝不打印密钥明文</b>。</p>
 */
@Slf4j
@Component
public class JwtSecretProvider {

    /** 历史公开默认密钥：已随源码与部署包外泄，生产环境禁止使用 */
    public static final String WELL_KNOWN_DEV_SECRET = "DevLocalFixedJwtSecretKey_Erp2026_DoNotChange";

    /** 随机密钥字节数：HS256 要求密钥长度 >= 32 字节，取 48 字节留余量 */
    private static final int RANDOM_SECRET_BYTES = 48;

    private final String secret;
    private final String source;

    public JwtSecretProvider(
            @Value("${erp.jwt.secret:}") String configuredSecret,
            @Value("${erp.security.jwt-secret-file:erp-jwt-secret.key}") String secretFile,
            @Value("${erp.security.strict-secret:true}") boolean strictSecret,
            @Value("${erp.security.allow-dev-secret:false}") boolean allowDevSecret) {

        // ---- 分支 1：显式注入环境变量 ----
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            String trimmed = configuredSecret.trim();
            if (WELL_KNOWN_DEV_SECRET.equals(trimmed)) {
                if (!allowDevSecret && strictSecret) {
                    String msg = devSecretRejectedMessage();
                    log.error(msg);
                    throw new IllegalStateException(msg);
                }
                log.warn("==============================================================");
                log.warn("【安全告警】JWT 密钥使用历史公开默认值，已放行（仅限本地开发）！");
                log.warn("服务器环境必须改为：让 erp-jwt-secret.key 自动生成，或注入 ERP_JWT_SECRET");
                log.warn("==============================================================");
                this.secret = trimmed;
                this.source = "环境变量(本地开发公开密钥)";
                return;
            }
            log.info("[JWT] 密钥来源=环境变量 ERP_JWT_SECRET（长度={}）", trimmed.length());
            this.secret = trimmed;
            this.source = "环境变量 ERP_JWT_SECRET";
            return;
        }

        // ---- 分支 2/3：未注入 → 密钥文件 → 自动生成 ----
        Path path = resolvePath(secretFile);
        String fromFile = readSecret(path);
        if (fromFile != null) {
            log.info("[JWT] 密钥来源=密钥文件 path={}（长度={}）", path, fromFile.length());
            this.secret = fromFile;
            this.source = "密钥文件 " + path;
            return;
        }

        String generated = randomSecret();
        if (writeNewSecret(path, generated)) {
            log.info("[JWT] 密钥来源=首次启动自动生成 path={}（长度={}）—— 请勿删除该文件，删除会导致全部用户被登出",
                    path, generated.length());
            this.source = "自动生成 " + path;
        } else {
            // 生成时被其他进程抢先写入 → 复用它，保证同一部署内密钥一致
            String raced = readSecret(path);
            if (raced != null) {
                log.info("[JWT] 密钥来源=密钥文件(并发复用) path={}", path);
                this.source = "密钥文件 " + path;
                this.secret = raced;
                return;
            }
            log.warn("==============================================================");
            log.warn("【安全告警】JWT 密钥文件无法写入（{}），本次使用内存随机密钥！", path);
            log.warn("后果：下次重启会换密钥 → 所有用户需重新登录。请检查目录权限。");
            log.warn("==============================================================");
            this.source = "内存随机(落盘失败)";
        }
        this.secret = generated;
    }

    public String getSecret() {
        return secret;
    }

    /** 密钥来源描述（写启动日志/排查用，不含密钥明文）。 */
    public String getSource() {
        return source;
    }

    /** 相对路径按进程工作目录解析（jar 同级/启动脚本 cd 的目录），便于随部署走。 */
    private Path resolvePath(String secretFile) {
        Path p = Paths.get(secretFile);
        if (p.isAbsolute()) {
            return p;
        }
        String workDir = System.getProperty("user.dir", ".");
        return Paths.get(workDir).resolve(secretFile);
    }

    /** 读取密钥文件；不存在/空白返回 null。自动剥离 UTF-8 BOM，容忍手写文件。 */
    private String readSecret(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(path);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            text = text.trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            log.warn("[JWT] 读取密钥文件失败 path={}: {}", path, e.getMessage());
            return null;
        }
    }

    /** 以 CREATE_NEW 写入，避免覆盖已有密钥（覆盖=全员登出）。写入成功返回 true。 */
    private boolean writeNewSecret(Path path, String value) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // 不写 BOM：base64 纯 ASCII，便于 ps1/bat 直接读取
            Files.write(path, value.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        } catch (Exception e) {
            log.warn("[JWT] 写入密钥文件失败 path={}: {}", path, e.getMessage());
            return false;
        }
    }

    private String randomSecret() {
        byte[] buf = new byte[RANDOM_SECRET_BYTES];
        new SecureRandom().nextBytes(buf);
        return Base64.getEncoder().encodeToString(buf);
    }

    private String devSecretRejectedMessage() {
        return String.join(System.lineSeparator(),
                "==============================================================",
                "【启动中止】检测到 JWT 密钥为历史公开默认值（已随源码外泄）！",
                "该密钥可被任何人用来伪造管理员 token，拒绝以该配置启动。",
                "",
                "修复方式（任选其一）：",
                "  1) 删除启动脚本里的 ERP_JWT_SECRET= 设置，让后端首次启动自动生成独立密钥",
                "     （生成位置：进程工作目录下的 erp-jwt-secret.key，请勿提交/外发）",
                "  2) 注入随机密钥（PowerShell 生成后设为系统环境变量）：",
                "     $b=New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b);",
                "     setx ERP_JWT_SECRET ([Convert]::ToBase64String($b)) /M",
                "  3) 仅本机开发需要沿用旧密钥时，显式声明：ERP_SECURITY_ALLOW_DEV_SECRET=true",
                "==============================================================");
    }
}
