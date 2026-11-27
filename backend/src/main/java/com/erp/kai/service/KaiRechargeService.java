package com.erp.kai.service;

import com.erp.kai.service.pay.AlipayPagePayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 小楷独立版 · 支付宝充值（2026-11-07）。
 *
 * <p>业务链路：客户选充值等级 → 生成充值订单 → 跳支付宝电脑网站收银台扫码 →
 * 异步通知或主动查单确认已支付 → 积分入账（写充值流水）→ 前台轮询显示支付成功。</p>
 *
 * <p>安全口径：金额与积分只取服务端订单快照；异步通知先验签再核对 APPID、订单号、金额；
 * 到账走「仅 CREATED → PAID」的原子更新，重复通知与重复查单都不会重复加积分。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiRechargeService {

    private final JdbcTemplate jdbcTemplate;
    private final KaiCreditService creditService;
    private final KaiCryptoService cryptoService;

    public static final String K_ENABLED      = "pay.enabled";
    public static final String K_APP_ID       = "pay.alipay.app_id";
    public static final String K_PRIVATE_KEY  = "pay.alipay.private_key";
    public static final String K_PUBLIC_KEY   = "pay.alipay.public_key";
    public static final String K_SANDBOX      = "pay.alipay.sandbox";
    public static final String K_NOTIFY_URL   = "pay.alipay.notify_url";
    public static final String K_RETURN_URL   = "pay.alipay.return_url";

    private static final String GATEWAY_PROD = "https://openapi.alipay.com/gateway.do";
    private static final String GATEWAY_SANDBOX = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private static final String DEFAULT_NOTIFY = "https://kaiyu.pro/api/kai-standalone/pay/alipay/notify";
    private static final String DEFAULT_RETURN = "https://kaiyu.pro/kai/settings?tab=usage";

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put(K_ENABLED, "false");
        DEFAULTS.put(K_APP_ID, "");
        DEFAULTS.put(K_PRIVATE_KEY, "");
        DEFAULTS.put(K_PUBLIC_KEY, "");
        DEFAULTS.put(K_SANDBOX, "true");
        DEFAULTS.put(K_NOTIFY_URL, DEFAULT_NOTIFY);
        DEFAULTS.put(K_RETURN_URL, DEFAULT_RETURN);
    }

    /** 同一订单查单节流：避免前台每秒轮询都打支付宝。 */
    private final Map<String, Long> lastQueryAt = new ConcurrentHashMap<>();

    // ---------------- 配置 ----------------

    public String get(String key) {
        String def = DEFAULTS.getOrDefault(key, "");
        try {
            List<String> rows = jdbcTemplate.queryForList(
                    "SELECT config_value FROM kai_config WHERE config_key = ?", String.class, key);
            if (rows.isEmpty()) return def;
            String v = rows.get(0);
            return v == null ? "" : v;
        } catch (Exception e) {
            return def;
        }
    }

    public boolean getBool(String key) {
        String v = get(key).trim().toLowerCase();
        if (v.isEmpty()) v = DEFAULTS.getOrDefault(key, "false");
        return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v);
    }

    private void put(String key, String value) {
        jdbcTemplate.update("""
                INSERT INTO kai_config (config_key, config_value, updated_at) VALUES (?,?,CURRENT_TIMESTAMP)
                ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value, updated_at = CURRENT_TIMESTAMP
                """, key, value);
    }

    private String plainKey(String configKey) {
        try {
            return cryptoService.decrypt(get(configKey));
        } catch (Exception e) {
            log.warn("[小楷独立版] 支付密钥解密失败：{}", e.getMessage());
            return "";
        }
    }

    public boolean enabled() {
        return getBool(K_ENABLED);
    }

    public boolean configured() {
        return client().usable();
    }

    private AlipayPagePayClient client() {
        String gateway = getBool(K_SANDBOX) ? GATEWAY_SANDBOX : GATEWAY_PROD;
        return new AlipayPagePayClient(gateway, get(K_APP_ID), plainKey(K_PRIVATE_KEY), plainKey(K_PUBLIC_KEY));
    }

    /** 客户侧状态：enabled = 已开启且配置完整。 */
    public Map<String, Object> publicState() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean ready = enabled() && configured();
        out.put("enabled", ready);
        out.put("packages", ready ? listEnabledPackages() : List.of());
        return out;
    }

    public Map<String, Object> adminConfig() {
        String priv = plainKey(K_PRIVATE_KEY);
        String pub = plainKey(K_PUBLIC_KEY);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled());
        out.put("appId", get(K_APP_ID));
        out.put("sandbox", getBool(K_SANDBOX));
        out.put("notifyUrl", get(K_NOTIFY_URL));
        out.put("returnUrl", get(K_RETURN_URL));
        out.put("hasPrivateKey", !priv.isBlank());
        out.put("privateKeyMasked", priv.isBlank() ? "" : cryptoService.maskTail(priv));
        out.put("hasPublicKey", !pub.isBlank());
        out.put("publicKeyMasked", pub.isBlank() ? "" : cryptoService.maskTail(pub));
        out.put("configured", configured());
        out.put("gateway", getBool(K_SANDBOX) ? GATEWAY_SANDBOX : GATEWAY_PROD);
        return out;
    }

    /** 保存支付配置；密钥留空 = 保持原值，"__CLEAR__" = 清空。 */
    public void saveConfig(Map<String, Object> body) {
        if (body.containsKey("enabled")) put(K_ENABLED, boolText(body.get("enabled")));
        if (body.containsKey("sandbox")) put(K_SANDBOX, boolText(body.get("sandbox")));
        if (body.containsKey("appId")) put(K_APP_ID, text(body.get("appId"), 64));
        if (body.containsKey("notifyUrl")) put(K_NOTIFY_URL, text(body.get("notifyUrl"), 300));
        if (body.containsKey("returnUrl")) put(K_RETURN_URL, text(body.get("returnUrl"), 300));
        saveSecret(body, "privateKey", K_PRIVATE_KEY);
        saveSecret(body, "publicKey", K_PUBLIC_KEY);
    }

    private void saveSecret(Map<String, Object> body, String field, String configKey) {
        if (!body.containsKey(field) || body.get(field) == null) return;
        String raw = String.valueOf(body.get(field)).trim();
        if (raw.isEmpty()) return;
        if ("__CLEAR__".equals(raw)) {
            put(configKey, "");
            return;
        }
        put(configKey, cryptoService.encrypt(raw));
    }

    // ---------------- 充值等级 ----------------

    public List<Map<String, Object>> listEnabledPackages() {
        return listPackages(true);
    }

    public List<Map<String, Object>> listAllPackages() {
        return listPackages(false);
    }

    private List<Map<String, Object>> listPackages(boolean onlyEnabled) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            String sql = """
                    SELECT id, name, price, credits, sort_no, enabled
                    FROM kai_recharge_package
                    """ + (onlyEnabled ? " WHERE enabled = 1 " : " ")
                    + " ORDER BY sort_no ASC, id ASC";
            jdbcTemplate.query(sql, rs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("name", rs.getString("name"));
                m.put("price", rs.getBigDecimal("price"));
                m.put("credits", rs.getBigDecimal("credits"));
                m.put("sortNo", rs.getInt("sort_no"));
                m.put("enabled", rs.getInt("enabled") == 1);
                out.add(m);
            });
        } catch (Exception e) {
            log.warn("[小楷独立版] 读取充值等级失败：{}", e.getMessage());
        }
        return out;
    }

    private Map<String, Object> packageById(Long id) {
        if (id == null) return null;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.query("""
                    SELECT id, name, price, credits, enabled FROM kai_recharge_package WHERE id = ?
                    """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("name", rs.getString("name"));
                m.put("price", rs.getBigDecimal("price"));
                m.put("credits", rs.getBigDecimal("credits"));
                m.put("enabled", rs.getInt("enabled") == 1);
                return m;
            }, id);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    public void savePackage(Map<String, Object> body) {
        Long id = longOrNull(body.get("id"));
        String name = text(body.get("name"), 100).trim();
        BigDecimal price = decimal(body.get("price"));
        BigDecimal credits = decimal(body.get("credits"));
        int sortNo = body.get("sortNo") == null ? 0 : (int) Math.max(0, longOf(body.get("sortNo"), 0));
        boolean enabled = body.get("enabled") == null || boolOf(body.get("enabled"));
        if (name.isEmpty()) throw new IllegalArgumentException("请填写充值等级名称");
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("售价必须大于 0");
        if (credits == null || credits.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("到账积分必须大于 0");
        price = price.setScale(2, RoundingMode.HALF_UP);
        credits = credits.setScale(4, RoundingMode.HALF_UP);
        if (id == null) {
            jdbcTemplate.update("""
                    INSERT INTO kai_recharge_package (name, price, credits, sort_no, enabled)
                    VALUES (?,?,?,?,?)
                    """, name, price, credits, sortNo, enabled ? 1 : 0);
        } else {
            int n = jdbcTemplate.update("""
                    UPDATE kai_recharge_package SET name=?, price=?, credits=?, sort_no=?, enabled=?,
                                                    updated_at=CURRENT_TIMESTAMP
                    WHERE id=?
                    """, name, price, credits, sortNo, enabled ? 1 : 0, id);
            if (n == 0) throw new IllegalArgumentException("充值等级不存在");
        }
    }

    public void togglePackage(Long id, boolean enabled) {
        jdbcTemplate.update("UPDATE kai_recharge_package SET enabled=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                enabled ? 1 : 0, id);
    }

    public void deletePackage(Long id) {
        Integer used = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kai_recharge_order WHERE package_id = ?", Integer.class, id);
        if (used != null && used > 0) {
            throw new IllegalArgumentException("该充值等级已有充值订单，不能删除，请改为停用");
        }
        jdbcTemplate.update("DELETE FROM kai_recharge_package WHERE id = ?", id);
    }

    // ---------------- 充值订单 ----------------

    public Map<String, Object> createOrder(Long userId, Long packageId) {
        if (!enabled() || !configured()) throw new IllegalArgumentException("在线充值暂未开放");
        Map<String, Object> pkg = packageById(packageId);
        if (pkg == null || !Boolean.TRUE.equals(pkg.get("enabled"))) {
            throw new IllegalArgumentException("充值等级不存在或已停用");
        }
        BigDecimal amount = ((BigDecimal) pkg.get("price")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal credits = ((BigDecimal) pkg.get("credits")).setScale(4, RoundingMode.HALF_UP);
        String name = String.valueOf(pkg.get("name"));

        String orderNo = newOrderNo();
        long packageIdValue = ((Number) pkg.get("id")).longValue();
        jdbcTemplate.update("""
                INSERT INTO kai_recharge_order
                    (order_no, kai_user_id, package_id, package_name, amount, credits, status, expire_at)
                VALUES (?,?,?,?,?,?, 'CREATED', CURRENT_TIMESTAMP + INTERVAL '2 hour')
                """, orderNo, userId, packageIdValue, name, amount, credits);

        String subject = "小楷积分充值 " + credits.stripTrailingZeros().toPlainString() + " 积分";
        String form = client().buildPagePayForm(orderNo, amount.toPlainString(), subject,
                get(K_NOTIFY_URL), get(K_RETURN_URL));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderNo", orderNo);
        out.put("amount", amount);
        out.put("credits", credits);
        out.put("form", form);
        return out;
    }

    /** 客户查看自己订单状态；待支付订单按 2 秒节流主动查单，保证支付成功后秒级到账。 */
    public Map<String, Object> orderState(Long userId, String orderNo) {
        Map<String, Object> order = findOrder(orderNo);
        if (order == null || !userId.equals(order.get("kaiUserId"))) {
            throw new IllegalArgumentException("充值订单不存在");
        }
        String status = String.valueOf(order.get("status"));
        if ("CREATED".equals(status)) {
            if (expired(order)) {
                closeOrder(orderNo);
            } else if (shouldQuery(orderNo)) {
                AlipayPagePayClient client = client();
                if (client.usable()) {
                    AlipayPagePayClient.TradeStatus t = client.query(orderNo);
                    if (t.paid()) {
                        markPaid(orderNo, t.tradeNo());
                    } else if (t.closed()) {
                        closeOrder(orderNo);
                    }
                }
            }
        }
        return orderView(orderNo);
    }

    public List<Map<String, Object>> myOrders(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            jdbcTemplate.query("""
                    SELECT order_no, package_name, amount, credits, status, pay_time, created_at
                    FROM kai_recharge_order WHERE kai_user_id = ? ORDER BY id DESC LIMIT 20
                    """, rs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderNo", rs.getString("order_no"));
                m.put("packageName", rs.getString("package_name"));
                m.put("amount", rs.getBigDecimal("amount"));
                m.put("credits", rs.getBigDecimal("credits"));
                m.put("status", rs.getString("status"));
                m.put("payTime", rs.getTimestamp("pay_time") == null ? "" : String.valueOf(rs.getTimestamp("pay_time")));
                m.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                out.add(m);
            }, userId);
        } catch (Exception e) {
            log.warn("[小楷独立版] 读取充值记录失败：{}", e.getMessage());
        }
        return out;
    }

    /** 支付结果跳回地址：只带订单号，由前台继续查单，不采信跳转参数本身。 */
    public String returnRedirectUrl(String orderNo) {
        String base = get(K_RETURN_URL);
        if (base == null || base.isBlank()) base = DEFAULT_RETURN;
        String sep = base.contains("?") ? "&" : "?";
        String no = orderNo == null ? "" : URLEncoder.encode(orderNo, StandardCharsets.UTF_8);
        return base + sep + "pay=1&orderNo=" + no;
    }

    /**
     * 处理支付宝异步通知。
     *
     * @return true = 已验签且处理成功，应回 "success"；false = 拒绝，应回 "failure"
     */
    public boolean handleNotify(Map<String, String> params) {
        String orderNo = params.get("out_trade_no");
        try {
            String publicKey = plainKey(K_PUBLIC_KEY);
            if (publicKey.isBlank() || !AlipayPagePayClient.verifyNotify(params, publicKey)) {
                log.warn("[小楷独立版] 支付宝通知验签失败：orderNo={}", orderNo);
                return false;
            }
            String appId = params.get("app_id");
            if (appId == null || !appId.equals(get(K_APP_ID))) {
                log.warn("[小楷独立版] 支付宝通知 APPID 不匹配：{}", appId);
                return false;
            }
            if (orderNo == null || orderNo.isBlank()) return false;
            Map<String, Object> order = findOrder(orderNo);
            if (order == null) {
                log.warn("[小楷独立版] 支付宝通知订单不存在：{}", orderNo);
                return false;
            }
            String total = params.get("total_amount");
            if (total != null && !total.isBlank()) {
                BigDecimal notified = new BigDecimal(total);
                BigDecimal expected = (BigDecimal) order.get("amount");
                if (notified.compareTo(expected) != 0) {
                    log.warn("[小楷独立版] 支付宝通知金额不符：orderNo={} 通知={} 订单={}", orderNo, notified, expected);
                    return false;
                }
            }
            String tradeStatus = params.get("trade_status");
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                markPaid(orderNo, params.get("trade_no"));
            } else if ("TRADE_CLOSED".equals(tradeStatus)) {
                closeOrder(orderNo);
            }
            return true;
        } catch (Exception e) {
            log.error("[小楷独立版] 支付宝通知处理异常：orderNo={} {}", orderNo, e.getMessage());
            return false;
        }
    }

    /**
     * 标记已支付并发积分。仅 CREATED → PAID 的原子更新能拿到订单行，
     * 因此重复通知、查单与手工补记不会重复加积分。
     */
    public boolean markPaid(String orderNo, String tradeNo) {
        List<Map<String, Object>> rows = jdbcTemplate.query("""
                UPDATE kai_recharge_order
                   SET status='PAID', trade_no=?, pay_time=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                 WHERE order_no=? AND status='CREATED'
                 RETURNING kai_user_id, credits, package_name, amount
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kaiUserId", rs.getLong("kai_user_id"));
            m.put("credits", rs.getBigDecimal("credits"));
            m.put("packageName", rs.getString("package_name"));
            m.put("amount", rs.getBigDecimal("amount"));
            return m;
        }, emptyToNull(tradeNo), orderNo);
        if (rows.isEmpty()) {
            // 已支付过 = 幂等成功；已关闭 = 拒绝
            Map<String, Object> current = findOrder(orderNo);
            return current != null && "PAID".equals(current.get("status"));
        }
        Map<String, Object> row = rows.get(0);
        Long userId = ((Number) row.get("kaiUserId")).longValue();
        BigDecimal credits = (BigDecimal) row.get("credits");
        String remark = "充值到账：" + row.get("packageName") + "，支付 " + row.get("amount") + " 元";
        creditService.grant(userId, credits, "RECHARGE", remark);
        lastQueryAt.remove(orderNo);
        return true;
    }

    public void closeOrder(String orderNo) {
        try {
            jdbcTemplate.update("""
                    UPDATE kai_recharge_order SET status='CLOSED', updated_at=CURRENT_TIMESTAMP
                    WHERE order_no=? AND status='CREATED'
                    """, orderNo);
        } catch (Exception e) {
            log.warn("[小楷独立版] 关闭充值订单失败：{}", e.getMessage());
        }
        lastQueryAt.remove(orderNo);
    }

    private boolean expired(Map<String, Object> order) {
        Object expireAt = order.get("expireAt");
        if (!(expireAt instanceof java.sql.Timestamp ts)) return false;
        return ts.toLocalDateTime().isBefore(LocalDateTime.now());
    }

    private boolean shouldQuery(String orderNo) {
        long now = System.currentTimeMillis();
        Long last = lastQueryAt.get(orderNo);
        if (last != null && now - last < 2000) return false;
        if (lastQueryAt.size() > 2000) lastQueryAt.clear();
        lastQueryAt.put(orderNo, now);
        return true;
    }

    private Map<String, Object> findOrder(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) return null;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.query("""
                    SELECT order_no, kai_user_id, package_name, amount, credits, status,
                           trade_no, pay_time, expire_at, created_at
                    FROM kai_recharge_order WHERE order_no = ?
                    """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderNo", rs.getString("order_no"));
                m.put("kaiUserId", rs.getLong("kai_user_id"));
                m.put("packageName", rs.getString("package_name"));
                m.put("amount", rs.getBigDecimal("amount"));
                m.put("credits", rs.getBigDecimal("credits"));
                m.put("status", rs.getString("status"));
                m.put("tradeNo", rs.getString("trade_no"));
                m.put("payTime", rs.getTimestamp("pay_time"));
                m.put("expireAt", rs.getTimestamp("expire_at"));
                m.put("createdAt", rs.getTimestamp("created_at"));
                return m;
            }, orderNo);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> orderView(String orderNo) {
        Map<String, Object> order = findOrder(orderNo);
        Map<String, Object> out = new LinkedHashMap<>();
        if (order == null) {
            out.put("orderNo", orderNo);
            out.put("status", "UNKNOWN");
            return out;
        }
        out.put("orderNo", order.get("orderNo"));
        out.put("packageName", order.get("packageName"));
        out.put("amount", order.get("amount"));
        out.put("credits", order.get("credits"));
        out.put("status", order.get("status"));
        out.put("tradeNo", order.get("tradeNo") == null ? "" : order.get("tradeNo"));
        out.put("payTime", order.get("payTime") == null ? "" : String.valueOf(order.get("payTime")));
        return out;
    }

    // ---------------- 管理端 ----------------

    public List<Map<String, Object>> adminOrders(String status, String keyword) {
        List<Map<String, Object>> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT o.order_no, o.kai_user_id, u.username AS username, o.package_name, o.amount, o.credits,
                       o.status, o.trade_no, o.pay_time, o.created_at
                  FROM kai_recharge_order o
                  LEFT JOIN kai_user u ON u.id = o.kai_user_id
                 WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND o.status = ?");
            args.add(status.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (o.order_no LIKE ? OR u.username LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY o.id DESC LIMIT 200");
        try {
            jdbcTemplate.query(sql.toString(), rs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderNo", rs.getString("order_no"));
                m.put("userId", rs.getLong("kai_user_id"));
                m.put("username", rs.getString("username"));
                m.put("packageName", rs.getString("package_name"));
                m.put("amount", rs.getBigDecimal("amount"));
                m.put("credits", rs.getBigDecimal("credits"));
                m.put("status", rs.getString("status"));
                m.put("tradeNo", rs.getString("trade_no"));
                m.put("payTime", rs.getTimestamp("pay_time") == null ? "" : String.valueOf(rs.getTimestamp("pay_time")));
                m.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                out.add(m);
            }, args.toArray());
        } catch (Exception e) {
            log.warn("[小楷独立版] 读取充值订单失败：{}", e.getMessage());
        }
        return out;
    }

    /** 管理端主动查单补记。 */
    public Map<String, Object> adminRecheck(String orderNo) {
        Map<String, Object> order = findOrder(orderNo);
        if (order == null) throw new IllegalArgumentException("充值订单不存在");
        String status = String.valueOf(order.get("status"));
        if (!"PAID".equals(status)) {
            AlipayPagePayClient client = client();
            if (!client.usable()) throw new IllegalArgumentException("请先在支付设置里完成支付宝应用配置");
            AlipayPagePayClient.TradeStatus t = client.query(orderNo);
            if (t.paid()) {
                markPaid(orderNo, t.tradeNo());
            } else if (t.closed()) {
                closeOrder(orderNo);
            } else {
                throw new IllegalArgumentException("支付宝侧暂未查到支付成功" + (t.message().isBlank() ? "" : "：" + t.message()));
            }
        }
        return orderView(orderNo);
    }

    /** 管理端手工补记（仅处理未支付订单，防止重复加积分）。 */
    public Map<String, Object> adminMarkPaid(String orderNo, String remark) {
        Map<String, Object> order = findOrder(orderNo);
        if (order == null) throw new IllegalArgumentException("充值订单不存在");
        if ("PAID".equals(order.get("status"))) throw new IllegalArgumentException("该订单已支付，无需补记");
        if ("CLOSED".equals(order.get("status"))) throw new IllegalArgumentException("该订单已关闭，不能补记");
        boolean ok = markPaid(orderNo, null);
        if (!ok) throw new IllegalArgumentException("补记失败，请刷新后重试");
        log.info("[小楷独立版] 运营手工补记充值订单：{} 原因：{}", orderNo, remark == null ? "" : remark);
        return orderView(orderNo);
    }

    // ---------------- 小工具 ----------------

    private String newOrderNo() {
        String ts = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int rand = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "R" + ts + rand;
    }

    private static boolean boolOf(Object v) {
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s);
    }

    private static String boolText(Object v) {
        return boolOf(v) ? "true" : "false";
    }

    private static String text(Object v, int max) {
        if (v == null) return "";
        String s = String.valueOf(v).trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static Long longOrNull(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("编号必须是数字");
        }
    }

    private static long longOf(Object v, long def) {
        if (v == null || String.valueOf(v).isBlank()) return def;
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static BigDecimal decimal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("金额与积分必须是数字");
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
