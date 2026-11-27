package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.device.entity.DeviceAccessCredential;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.mapper.DeviceAccessCredentialMapper;
import com.erp.device.mapper.DeviceInfoMapper;
import com.erp.tenant.TenantContextHolder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备接入凭证：生成 / 轮换 / 吊销 / 验签。
 *
 * 签名口径（设备端按此实现）：
 *   sign = HEX( HMAC-SHA256( secret, credentialNo + "\n" + timestamp + "\n" + nonce + "\n" + rawBody ) )
 * 请求头：X-Device-Credential / X-Device-Timestamp(毫秒) / X-Device-Nonce / X-Device-Sign
 * 时间戳允许偏差 ±5 分钟；nonce 5 分钟内不可重复（防重放）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCredentialService extends ServiceImpl<DeviceAccessCredentialMapper, DeviceAccessCredential> {

    private static final long TS_TOLERANCE_MS = 5 * 60 * 1000L;

    private final DeviceInfoMapper deviceInfoMapper;
    private final DeviceCryptoService cryptoService;
    private final SecureRandom random = new SecureRandom();

    /** 已用 nonce（5 分钟窗口，防重放） */
    private final Cache<String, Boolean> nonceCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(100_000)
            .build();

    public PageResult<DeviceAccessCredential> pageQuery(int current, int size, Long deviceId, String keyword, String status) {
        QueryWrapper<DeviceAccessCredential> qw = new QueryWrapper<>();
        if (deviceId != null) qw.eq("device_id", deviceId);
        if (keyword != null && !keyword.isBlank()) qw.like("credential_no", keyword.trim());
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByDesc("id");
        Page<DeviceAccessCredential> page = page(new Page<>(current, size), qw);
        enrichAndMask(page.getRecords());
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    public DeviceAccessCredential current(Long deviceId) {
        if (deviceId == null) return null;
        DeviceAccessCredential c = getOne(new QueryWrapper<DeviceAccessCredential>()
                .eq("device_id", deviceId).orderByDesc("id").last("limit 1"));
        if (c == null) return null;
        List<DeviceAccessCredential> one = new java.util.ArrayList<>();
        one.add(c);
        enrichAndMask(one);
        return c;
    }

    public DeviceAccessCredential generate(Long deviceId) {
        DeviceInfo device = deviceInfoMapper.selectById(deviceId);
        if (device == null) throw new BusinessException("设备不存在或已被删除");
        long exists = count(new QueryWrapper<DeviceAccessCredential>()
                .eq("device_id", deviceId).eq("status", "启用"));
        if (exists > 0) throw new BusinessException("该设备已有启用中的接入凭证；如需更换密钥请用「轮换」，如需停用请用「吊销」");

        Long tenantId = TenantContextHolder.getTenantId();
        String credentialNo = "DEVC-" + (tenantId == null ? 1L : tenantId) + "-" + randomToken(8);
        String secret = randomToken(32);

        DeviceAccessCredential c = new DeviceAccessCredential();
        c.setDeviceId(deviceId);
        c.setCredentialNo(credentialNo);
        c.setSecretHash(sha256Hex(secret));
        c.setSecretEnc(cryptoService.encrypt(secret));
        c.setStatus("启用");
        c.setFailCount(0);
        save(c);

        c.setDeviceCode(device.getDeviceCode());
        c.setDeviceName(device.getDeviceName());
        c.setSecretPlain(secret);
        c.setSecretMasked(cryptoService.mask(secret));
        c.setSecretEnc(null);
        c.setSecretHash(null);
        return c;
    }

    public DeviceAccessCredential rotate(Long id) {
        DeviceAccessCredential c = getById(id);
        if (c == null) throw new BusinessException("接入凭证不存在");
        if (!"启用".equals(c.getStatus())) throw new BusinessException("凭证已吊销，不能轮换；请重新生成");
        String secret = randomToken(32);
        c.setSecretHash(sha256Hex(secret));
        c.setSecretEnc(cryptoService.encrypt(secret));
        c.setFailCount(0);
        updateById(c);

        DeviceInfo device = deviceInfoMapper.selectById(c.getDeviceId());
        c.setDeviceCode(device == null ? null : device.getDeviceCode());
        c.setDeviceName(device == null ? null : device.getDeviceName());
        c.setSecretPlain(secret);
        c.setSecretMasked(cryptoService.mask(secret));
        c.setSecretEnc(null);
        c.setSecretHash(null);
        return c;
    }

    public void revoke(Long id) {
        DeviceAccessCredential c = getById(id);
        if (c == null) throw new BusinessException("接入凭证不存在");
        c.setStatus("已吊销");
        updateById(c);
    }

    /**
     * 校验设备上报签名。返回 null 表示通过；返回非空字符串表示失败原因。
     */
    public AuthResult authenticate(String credentialNo, String timestamp, String nonce, String sign,
                                   String rawBody, String ip) {
        if (isBlank(credentialNo)) return AuthResult.fail("缺少设备凭证");
        if (isBlank(timestamp)) return AuthResult.fail("缺少时间戳");
        if (isBlank(nonce)) return AuthResult.fail("缺少随机串");
        if (isBlank(sign)) return AuthResult.fail("缺少签名");

        DeviceAccessCredential c = getOne(new QueryWrapper<DeviceAccessCredential>()
                .eq("credential_no", credentialNo.trim()).last("limit 1"));
        if (c == null) return AuthResult.fail("设备凭证不存在");
        if (!"启用".equals(c.getStatus())) return AuthResult.fail("设备凭证已吊销");

        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return AuthResult.fail("时间戳格式不正确（毫秒）");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > TS_TOLERANCE_MS) {
            return AuthResult.fail("时间戳超出允许范围（±5 分钟）");
        }

        String nonceKey = c.getCredentialNo() + ":" + nonce.trim();
        if (nonceCache.getIfPresent(nonceKey) != null) {
            return AuthResult.fail("重复请求（nonce 已使用）");
        }

        String secret = cryptoService.decrypt(c.getSecretEnc());
        if (isBlank(secret)) return AuthResult.fail("设备密钥不可用，请在接入管理中轮换密钥");

        String payload = c.getCredentialNo() + "\n" + timestamp.trim() + "\n" + nonce.trim() + "\n"
                + (rawBody == null ? "" : rawBody);
        String expected = hmacHex(secret, payload);
        if (!expected.equalsIgnoreCase(sign.trim())) {
            c.setFailCount((c.getFailCount() == null ? 0 : c.getFailCount()) + 1);
            updateById(c);
            return AuthResult.fail("签名校验不通过");
        }

        DeviceInfo device = deviceInfoMapper.selectById(c.getDeviceId());
        if (device == null) return AuthResult.fail("凭证对应的设备不存在");
        if ("停用".equals(device.getStatus()) || "报废".equals(device.getStatus())) {
            return AuthResult.fail("设备已" + device.getStatus() + "，不允许上报数据");
        }

        nonceCache.put(nonceKey, Boolean.TRUE);
        c.setLastAccessTime(LocalDateTime.now());
        c.setLastAccessIp(ip);
        c.setFailCount(0);
        updateById(c);
        return AuthResult.ok(device, c);
    }

    // ==================== 内部 ====================

    private void enrichAndMask(List<DeviceAccessCredential> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> deviceIds = list.stream().map(DeviceAccessCredential::getDeviceId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, DeviceInfo> devices = deviceIds.isEmpty() ? Map.of()
                : deviceInfoMapper.selectBatchIds(deviceIds).stream()
                .collect(Collectors.toMap(DeviceInfo::getId, Function.identity(), (a, b) -> a));
        for (DeviceAccessCredential c : list) {
            DeviceInfo d = devices.get(c.getDeviceId());
            c.setDeviceCode(d == null ? null : d.getDeviceCode());
            c.setDeviceName(d == null ? null : d.getDeviceName());
            String secret = cryptoService.decrypt(c.getSecretEnc());
            c.setSecretMasked(isBlank(secret) ? "不可用" : cryptoService.mask(secret));
            c.setSecretEnc(null);
            c.setSecretHash(null);
            c.setSecretPlain(null);
        }
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    /** 验签结果 */
    public record AuthResult(boolean ok, String message, DeviceInfo device, DeviceAccessCredential credential) {
        static AuthResult ok(DeviceInfo device, DeviceAccessCredential credential) {
            return new AuthResult(true, null, device, credential);
        }
        static AuthResult fail(String message) {
            return new AuthResult(false, message, null, null);
        }
    }
}
