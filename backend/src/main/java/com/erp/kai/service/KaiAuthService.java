package com.erp.kai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.auth.util.JwtUtil;
import com.erp.kai.entity.KaiExtConnection;
import com.erp.kai.entity.KaiUser;
import com.erp.kai.mapper.KaiExtConnectionMapper;
import com.erp.kai.mapper.KaiUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 小楷独立版 · 注册与登录（FEAT-KAI-STANDALONE-001）。
 * 独立用户体系：与 sys_user 无任何关联；密码 BCrypt（与主系统登录同款）；
 * 签发 scope=kai-standalone 的独立 token。
 * 2026-10-19：注册即预置一条内置「演示系统」连接（默认停用，用户自己在连接列表开启）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiAuthService {

    private final KaiUserMapper kaiUserMapper;
    private final KaiExtConnectionMapper connectionMapper;
    private final JwtUtil jwtUtil;
    private final KaiCreditService creditService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 注册：用户名唯一；返回 token。手机号/邮箱可空。 */
    public KaiUser register(String username, String password, String displayName, String companyName,
                            String phone, String email) {
        if (!creditService.getBool(KaiCreditService.K_REGISTER_ENABLED)) {
            throw new IllegalArgumentException("当前未开放注册，请联系管理员开通账号");
        }
        String name = username == null ? "" : username.trim();
        if (name.isEmpty() || name.length() < 3 || name.length() > 64) {
            throw new IllegalArgumentException("登录账号需 3~64 个字符");
        }
        if (!name.matches("[A-Za-z0-9_@.\\-]+")) {
            throw new IllegalArgumentException("登录账号只能包含字母、数字与 _ @ . - 符号");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        Long count = kaiUserMapper.selectCount(new LambdaQueryWrapper<KaiUser>().eq(KaiUser::getUsername, name));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("该登录账号已被注册");
        }
        KaiUser user = new KaiUser();
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName == null || displayName.isBlank() ? name : displayName.trim());
        user.setCompanyName(companyName == null ? "" : companyName.trim());
        user.setPhone(normalizePhone(phone));
        user.setEmail(normalizeEmail(email));
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        kaiUserMapper.insert(user);
        creditService.grantRegisterGift(user.getId());
        seedDemoConnection(user.getId());
        user.setCreditBalance(creditService.balance(user.getId()));
        log.info("[小楷独立版] 新用户注册: id={}, username={}, 积分={}", user.getId(), user.getUsername(), user.getCreditBalance());
        return user;
    }

    /** 注册即预置内置「演示系统」连接：默认停用，用户自己在连接列表开启；已有同名连接则不重复预置。 */
    private void seedDemoConnection(Long kaiUserId) {
        try {
            Long exists = connectionMapper.selectCount(new LambdaQueryWrapper<KaiExtConnection>()
                    .eq(KaiExtConnection::getKaiUserId, kaiUserId)
                    .eq(KaiExtConnection::getType, "DEMO"));
            if (exists != null && exists > 0) return;
            KaiExtConnection demo = new KaiExtConnection();
            demo.setKaiUserId(kaiUserId);
            demo.setName("演示系统");
            demo.setType("DEMO");
            demo.setBaseUrl("");
            demo.setAuthType("API_KEY");
            demo.setEnabled(0);
            demo.setCreatedAt(LocalDateTime.now());
            connectionMapper.insert(demo);
        } catch (Exception e) {
            log.warn("[小楷独立版] 预置演示系统连接失败（不影响注册）: {}", e.getMessage());
        }
    }

    /** 登录：校验密码 → 更新最近登录时间 → 返回用户（token 由 controller 签发）。失败抛中文异常。 */
    public KaiUser login(String username, String password) {
        KaiUser user = kaiUserMapper.selectOne(new LambdaQueryWrapper<KaiUser>().eq(KaiUser::getUsername,
                username == null ? "" : username.trim()));
        if (user == null || !passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            throw new IllegalArgumentException("登录账号或密码不正确");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new IllegalArgumentException("账号已停用，请联系管理员");
        }
        user.setLastLoginAt(LocalDateTime.now());
        kaiUserMapper.updateById(user);
        user.setCreditBalance(creditService.balance(user.getId()));
        return user;
    }

    /** 是否已初始化运营管理员（只认显式初始化的运营账号，客户注册永远不会自动升级）。 */
    public boolean operatorInitialized() {
        Long c = kaiUserMapper.selectCount(new LambdaQueryWrapper<KaiUser>().eq(KaiUser::getIsAdmin, 1));
        return c != null && c > 0;
    }

    /** 首次初始化运营管理员：仅当没有任何运营账号时允许；运营账号不是客户，不赠送积分。 */
    public synchronized KaiUser initOperator(String username, String password, String displayName) {
        if (operatorInitialized()) {
            throw new IllegalArgumentException("运营管理员已初始化，不能重复创建");
        }
        String name = username == null ? "" : username.trim();
        if (name.isEmpty() || name.length() < 3 || name.length() > 64) {
            throw new IllegalArgumentException("运营账号需 3~64 个字符");
        }
        if (!name.matches("[A-Za-z0-9_@.\\-]+")) {
            throw new IllegalArgumentException("运营账号只能包含字母、数字与 _ @ . - 符号");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("运营密码至少 6 位");
        }
        Long count = kaiUserMapper.selectCount(new LambdaQueryWrapper<KaiUser>().eq(KaiUser::getUsername, name));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("该账号已被注册，请换一个运营账号");
        }
        KaiUser user = new KaiUser();
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName == null || displayName.isBlank() ? "运营管理员" : displayName.trim());
        user.setCompanyName("");
        user.setPhone("");
        user.setEmail("");
        user.setStatus(1);
        user.setIsAdmin(1);
        user.setCreatedAt(LocalDateTime.now());
        kaiUserMapper.insert(user);
        log.info("[小楷独立版] 运营管理员初始化: id={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    /** 运营管理员修改客户资料（只改资料字段，不动账号、密码与积分）。 */
    public KaiUser adminUpdateProfile(Long id, Map<String, Object> body) {
        KaiUser user = byId(id);
        if (user == null) throw new IllegalArgumentException("账号不存在");
        if (body.containsKey("displayName")) {
            String v = body.get("displayName") == null ? "" : String.valueOf(body.get("displayName")).trim();
            if (v.length() > 64) throw new IllegalArgumentException("称呼最多 64 个字符");
            user.setDisplayName(v.isEmpty() ? user.getUsername() : v);
        }
        if (body.containsKey("companyName")) {
            String v = body.get("companyName") == null ? "" : String.valueOf(body.get("companyName")).trim();
            if (v.length() > 128) throw new IllegalArgumentException("公司名称最多 128 个字符");
            user.setCompanyName(v);
        }
        if (body.containsKey("phone")) {
            user.setPhone(normalizePhone(body.get("phone") == null ? "" : String.valueOf(body.get("phone"))));
        }
        if (body.containsKey("email")) {
            user.setEmail(normalizeEmail(body.get("email") == null ? "" : String.valueOf(body.get("email"))));
        }
        if (body.containsKey("remark")) {
            String v = body.get("remark") == null ? "" : String.valueOf(body.get("remark")).trim();
            if (v.length() > 500) throw new IllegalArgumentException("备注最多 500 个字符");
            user.setRemark(v);
        }
        kaiUserMapper.updateById(user);
        user.setCreditBalance(creditService.balance(user.getId()));
        return user;
    }

    private String normalizePhone(String phone) {
        String v = phone == null ? "" : phone.trim();
        if (v.isEmpty()) return "";
        if (!v.matches("[0-9+\\-\\s()]{6,20}")) {
            throw new IllegalArgumentException("手机号格式不正确（6~20 位数字，可含 + - 空格括号）");
        }
        return v;
    }

    private String normalizeEmail(String email) {
        String v = email == null ? "" : email.trim();
        if (v.isEmpty()) return "";
        if (v.length() > 128 || !v.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        return v;
    }

    /** 按主键读取（当前登录用户资料用）。 */
    public KaiUser byId(Long id) {
        return id == null ? null : kaiUserMapper.selectById(id);
    }

    /** 为用户签发独立版 token。 */
    public String issueToken(KaiUser user) {
        return jwtUtil.generateKaiToken(user.getId(), user.getUsername());
    }
}
