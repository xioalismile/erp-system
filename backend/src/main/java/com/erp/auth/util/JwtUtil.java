package com.erp.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    /** 密钥统一由 JwtSecretProvider 解析（环境变量 > 密钥文件 > 自动生成），避免多处取值不一致 */
    private final JwtSecretProvider secretProvider;

    private final long expiration;

    public JwtUtil(JwtSecretProvider secretProvider,
                   @Value("${erp.jwt.expiration}") long expiration) {
        this.secretProvider = secretProvider;
        this.expiration = expiration;
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secretProvider.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    public String generateTokenWithTenant(Long userId, String username, Long tenantId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("tenantId", tenantId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    /**
     * 小楷独立版 token（FEAT-KAI-STANDALONE-001）：
     * 带 scope=kai-standalone 标记，与主系统 token 严格隔离——
     * 独立 token 只能访问 /api/kai-standalone/**，访问主系统一律 401（AuthInterceptor 拦）。
     */
    public String generateKaiToken(Long userId, String username) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("scope", "kai-standalone")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    /** 读 token 的 scope 标记；主系统 token 无该 claim，返回 null。 */
    public String parseScope(String token) {
        Claims claims = Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload();
        return claims.get("scope", String.class);
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public String parseUsername(String token) {
        Claims claims = Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload();
        return claims.get("username", String.class);
    }

    public Long parseTenantId(String token) {
        Claims claims = Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload();
        Object tenantId = claims.get("tenantId");
        if (tenantId == null) {
            return null;
        }
        if (tenantId instanceof Number) {
            return ((Number) tenantId).longValue();
        }
        return Long.parseLong(tenantId.toString());
    }
}
