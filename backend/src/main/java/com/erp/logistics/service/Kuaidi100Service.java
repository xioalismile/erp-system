package com.erp.logistics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.config.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 快递100 免费实时查询服务
 *
 * 接口说明：
 *   - 免费实时查询接口 query.json，每次调用即时返回最新物流轨迹
 *   - 频率限制：1秒1次，单日单号查询上限 100 次
 *   - 看板自动刷新间隔建议 ≥ 30 秒，避免触发频率限制
 *
 * 授权码配置（优先级从高到低）：
 *   1. sys_config 表的 logistics.kuaidi100.customer / logistics.kuaidi100.key（设置中心维护，推荐）
 *   2. application.yml 中的 erp.logistics.kuaidi100.customer / key（兜底默认值）
 *   3. 环境变量 ERP_KUAIDI100_CUSTOMER / ERP_KUAIDI100_KEY（部署时使用）
 *
 * 注意：授权码属于敏感信息，仅在后端内存中使用，绝不通过任何 API 返回明文。
 */
@Slf4j
@Service
public class Kuaidi100Service {

    /** sys_config 配置键 - 快递100 授权码 */
    public static final String CFG_CUSTOMER = "logistics.kuaidi100.customer";
    /** sys_config 配置键 - 快递100 签名密钥 */
    public static final String CFG_KEY = "logistics.kuaidi100.key";
    /** sys_config 配置键 - 物流查询功能开关 */
    public static final String CFG_ENABLED = "logistics.kuaidi100.enabled";

    @Value("${erp.logistics.kuaidi100.enabled:true}")
    private boolean enabledDefault;

    @Value("${erp.logistics.kuaidi100.customer:}")
    private String customerDefault;

    @Value("${erp.logistics.kuaidi100.key:}")
    private String keyDefault;

    /** 注入 sys_config 服务（可选依赖，本地开发无该 Bean 时使用 yml 默认值） */
    private final SysConfigService sysConfigService;

    public Kuaidi100Service(org.springframework.beans.factory.ObjectProvider<SysConfigService> sysConfigServiceProvider) {
        this.sysConfigService = sysConfigServiceProvider.getIfAvailable();
    }

    /**
     * 快递100 实时查询接口（企业版 POST，返回 JSON）
     * 官方文档：https://api.kuaidi100.com/ 实时查询API
     */
    private static final String QUERY_URL = "https://poll.kuaidi100.com/poll/query.do";
    /** 自动识别承运商接口 */
    private static final String AUTONUMBER_URL = "https://api.kuaidi100.com/autonumber/auto";

    private OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 5分钟内存缓存：key=carrier:trackingNo，value=查询结果
     * 避免看板自动刷新时频繁调用快递100接口触发频率限制
     */
    private static final long CACHE_TTL_SECONDS = 300;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 实时从 sys_config 读取授权码（如未配置则回退到 yml 默认值）
     * 保存授权码后无需重启服务即可生效
     */
    private String getCustomer() {
        if (sysConfigService != null) {
            try {
                String v = sysConfigService.getValue(CFG_CUSTOMER, "");
                if (v != null && !v.trim().isEmpty()) return v.trim();
            } catch (Exception e) {
                log.warn("Kuaidi100Service.getCustomer() read sys_config failed, degrade to empty. Error: {}", e.getMessage());
                return "";
            }
        }
        return customerDefault;
    }

    private String getKey() {
        if (sysConfigService != null) {
            try {
                String v = sysConfigService.getValue(CFG_KEY, "");
                if (v != null && !v.trim().isEmpty()) return v.trim();
            } catch (Exception e) {
                log.warn("Kuaidi100Service.getKey() read sys_config failed, degrade to empty. Error: {}", e.getMessage());
                return "";
            }
        }
        return keyDefault;
    }

    private boolean isEnabled() {
        if (sysConfigService != null) {
            try {
                return sysConfigService.getBooleanValue(CFG_ENABLED, enabledDefault);
            } catch (Exception e) {
                // sys_config 表结构异常或数据库不可用：降级为禁用，避免 @PostConstruct 抛错把整个应用挂掉
                log.warn("Kuaidi100Service.isEnabled() read sys_config failed, degrade to disabled. Error: {}", e.getMessage());
                return false;
            }
        }
        return enabledDefault;
    }

    /**
     * 暴露给 Controller 查询配置状态（脱敏：只返回是否已配置，不返回明文）
     */
    public boolean isConfigured() {
        String c = getCustomer();
        return c != null && !c.isEmpty();
    }

    /**
     * 脱敏后的授权码（前2后2，中间***），供「设置中心 → 物流配置」页展示。
     *
     * 必须与 isConfigured() 使用同一份取值逻辑（sys_config → application.yml → 环境变量），
     * 否则会出现"界面显示已配置、但掩码为空"的显示不一致（2026-09-11 修复）。
     * 原始授权码只在服务内部使用，绝不通过 API 外泄。
     */
    public String getCustomerMasked() {
        return maskSecret(getCustomer());
    }

    /**
     * 脱敏：前2后2，中间***；长度 ≤ 4 全部 *
     */
    private static String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) return "";
        if (secret.length() <= 4) return "****";
        return secret.substring(0, 2) + "***" + secret.substring(secret.length() - 2);
    }

    /**
     * 暴露给 Controller 查询启用状态
     */
    public boolean isServiceEnabled() {
        return isEnabled();
    }

    /**
     * 承运商中文名 → 快递100公司编码映射表
     * 覆盖国内主流快递公司
     */
    private static final Map<String, String> CARRIER_CODE_MAP = new LinkedHashMap<>();
    static {
        CARRIER_CODE_MAP.put("顺丰", "shunfeng");
        CARRIER_CODE_MAP.put("顺丰速运", "shunfeng");
        CARRIER_CODE_MAP.put("顺丰快递", "shunfeng");
        CARRIER_CODE_MAP.put("中通", "zhongtong");
        CARRIER_CODE_MAP.put("中通快递", "zhongtong");
        CARRIER_CODE_MAP.put("圆通", "yuantong");
        CARRIER_CODE_MAP.put("圆通速递", "yuantong");
        CARRIER_CODE_MAP.put("申通", "shentong");
        CARRIER_CODE_MAP.put("申通快递", "shentong");
        CARRIER_CODE_MAP.put("韵达", "yunda");
        CARRIER_CODE_MAP.put("韵达快递", "yunda");
        CARRIER_CODE_MAP.put("京东", "jd");
        CARRIER_CODE_MAP.put("京东物流", "jd");
        CARRIER_CODE_MAP.put("京东快递", "jd");
        CARRIER_CODE_MAP.put("ems", "ems");
        CARRIER_CODE_MAP.put("EMS", "ems");
        CARRIER_CODE_MAP.put("邮政", "youzhengguonei");
        CARRIER_CODE_MAP.put("中国邮政", "youzhengguonei");
        CARRIER_CODE_MAP.put("邮政小包", "youzhengguonei");
        CARRIER_CODE_MAP.put("德邦", "debangwuliu");
        CARRIER_CODE_MAP.put("德邦物流", "debangwuliu");
        CARRIER_CODE_MAP.put("百世", "huitongkuaidi");
        CARRIER_CODE_MAP.put("百世快递", "huitongkuaidi");
        CARRIER_CODE_MAP.put("极兔", "jtexpress");
        CARRIER_CODE_MAP.put("极兔速递", "jtexpress");
        CARRIER_CODE_MAP.put("极兔快递", "jtexpress");
        CARRIER_CODE_MAP.put("天天", "tiantian");
        CARRIER_CODE_MAP.put("天天快递", "tiantian");
        CARRIER_CODE_MAP.put("宅急送", "zhaijisong");
        CARRIER_CODE_MAP.put("跨越", "kuayue");
        CARRIER_CODE_MAP.put("跨越速运", "kuayue");
        CARRIER_CODE_MAP.put("丰网", "fengwang");
        CARRIER_CODE_MAP.put("丰网速运", "fengwang");
        CARRIER_CODE_MAP.put("菜鸟", "cainiao");
        CARRIER_CODE_MAP.put("菜鸟驿站", "cainiao");
    }

    /** 编码 → 中文名（反向映射，给自动识别回填中文名用） */
    private static final Map<String, String> CODE_TO_NAME_MAP = new LinkedHashMap<>();
    static {
        for (Map.Entry<String, String> e : CARRIER_CODE_MAP.entrySet()) {
            if (!CODE_TO_NAME_MAP.containsKey(e.getValue())) {
                CODE_TO_NAME_MAP.put(e.getValue(), e.getKey());
            }
        }
        // 补充英文别名显示
        CODE_TO_NAME_MAP.put("ems", "EMS");
    }

    /**
     * 严格前缀识别表：只放【绝对不冲突、不会串号】的规则。
     * 所有"纯数字、或可能与其它公司前缀重叠"的规则一律不准放这里，
     * 统一走快递100 autonumber 官方接口识别并让用户确认。
     *
     * 匹配顺序：按列表顺序，先命中先返回
     */
    private static final List<PrefixRule> PREFIX_RULES = Arrays.asList(
            // 顺丰：SF + 数字（SF0/F开头+11/12位数字；电商件SF开头）
            new PrefixRule("SF", "shunfeng", "顺丰速运", false, 12),
            // 京东物流：JDV（京东特惠）、JDL（京东供应链）、JD（京东特快）、JDKE、V开头等
            new PrefixRule("JDVA", "jd", "京东物流", true, 15),
            new PrefixRule("JDVB", "jd", "京东物流", true, 15),
            new PrefixRule("JDVC", "jd", "京东物流", true, 15),
            new PrefixRule("JDV", "jd", "京东物流", true, 15),
            new PrefixRule("JDL", "jd", "京东物流", true, 15),
            new PrefixRule("JDKY", "jd", "京东航空", true, 15),
            new PrefixRule("JD", "jd", "京东快递", true, 13),
            // 圆通：YT + 13位数字（圆通国际YT+字母不在此，不包含）
            new PrefixRule("YT", "yuantong", "圆通速递", false, 13),
            // 中通：ZT + 数字（中通快运ZTF不在此）/ 77开头纯数字（中通传统单，纯数字兜底由autonumber负责）
            new PrefixRule("ZT", "zhongtong", "中通快递", false, 12),
            // 韵达：YD + 13位数字；韵达国际/快运不在此
            new PrefixRule("YD", "yunda", "韵达快递", false, 13),
            // 申通：STO + 数字 / 773开头纯数字（后者不在严格前缀，后面autonumber）
            new PrefixRule("STO", "shentong", "申通快递", false, 12),
            // 极兔：JT + 数字（标准15位）
            new PrefixRule("JT", "jtexpress", "极兔速递", false, 15),
            // EMS/邮政：EE、EG、RR、CP等国际EMS字母；国内EMS纯数字13位不在这里
            new PrefixRule("EE", "ems", "EMS", false, 13),
            new PrefixRule("EG", "ems", "EMS", false, 13),
            new PrefixRule("RR", "ems", "EMS", false, 13),
            new PrefixRule("CP", "ems", "EMS", false, 13),
            new PrefixRule("LP", "ems", "EMS", false, 13),
            // 德邦：DPK + 10位数字
            new PrefixRule("DPK", "debangwuliu", "德邦快递", false, 13),
            // 丰网：FW + 数字
            new PrefixRule("FW", "fengwang", "丰网速运", false, 15)
    );

    /** 京东/顺丰必须手机号后四位的快递公司编码集合 */
    private static final Set<String> NEED_PHONE_CODES = new HashSet<>(Arrays.asList("jd", "shunfeng"));

    /** 前缀规则 DTO */
    private static class PrefixRule {
        final String prefix;
        final String code;
        final String name;
        final boolean needPhone;
        final int typicalLength;
        PrefixRule(String prefix, String code, String name, boolean needPhone, int typicalLength) {
            this.prefix = prefix;
            this.code = code;
            this.name = name;
            this.needPhone = needPhone;
            this.typicalLength = typicalLength;
        }
    }

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        log.info("快递100服务初始化: enabled={}, customer={}", isEnabled(),
                (getCustomer() != null && !getCustomer().isEmpty()) ? "已配置" : "未配置");
    }

    // ============= 承运商自动识别（严格规则+官方接口兜底） =============

    /** 候选承运商 */
    public static class CarrierCandidate {
        private final String code;
        private final String name;
        private final boolean needPhone;
        public CarrierCandidate(String code, String name, boolean needPhone) {
            this.code = code;
            this.name = name;
            this.needPhone = needPhone;
        }
        public String getCode() { return code; }
        public String getName() { return name; }
        public boolean isNeedPhone() { return needPhone; }
    }

    /** 承运商识别结果 */
    public static class CarrierDetectResult {
        /** 是否识别成功（明确命中1个，或候选只有1个可直接用） */
        private final boolean matched;
        /** 识别出的编码（matched=true时唯一） */
        private final String matchedCode;
        /** 识别出的中文名（matched=true时唯一） */
        private final String matchedName;
        /** 命中的这个是否要手机号 */
        private final boolean needPhone;
        /** 候选列表（matched=false时不为空，需要前端让用户选） */
        private final List<CarrierCandidate> candidates;
        /** 识别来源：local_prefix / autonumber_api / empty */
        private final String source;

        public CarrierDetectResult(boolean matched, String matchedCode, String matchedName,
                                   boolean needPhone, List<CarrierCandidate> candidates, String source) {
            this.matched = matched;
            this.matchedCode = matchedCode;
            this.matchedName = matchedName;
            this.needPhone = needPhone;
            this.candidates = candidates == null ? new ArrayList<>() : candidates;
            this.source = source;
        }
        public boolean isMatched() { return matched; }
        public String getMatchedCode() { return matchedCode; }
        public String getMatchedName() { return matchedName; }
        public boolean isNeedPhone() { return needPhone; }
        public List<CarrierCandidate> getCandidates() { return candidates; }
        public String getSource() { return source; }
    }

    /**
     * 按单号识别承运商（仅识别，不调用查询接口）
     * 用户只输入一个单号时，前端先调此接口：
     *   - 如 matched=true → 直接用返回的 code/name 回填出库单carrier，然后调用查询
     *   - 如 matched=false，candidates 非空 → 前端弹候选选择器让用户选
     *   - candidates 也为空 → 提示用户手动填承运商
     *
     * @param trackingNo 单号
     */
    public CarrierDetectResult detectCarrier(String trackingNo) {
        if (trackingNo == null || trackingNo.trim().isEmpty()) {
            return new CarrierDetectResult(false, null, null, false,
                    new ArrayList<>(), "empty");
        }
        String no = trackingNo.trim().toUpperCase();
        // 第1步：严格前缀（本地，快，绝对不冲突）
        for (PrefixRule r : PREFIX_RULES) {
            if (no.startsWith(r.prefix)) {
                return new CarrierDetectResult(true, r.code, r.name,
                        NEED_PHONE_CODES.contains(r.code), new ArrayList<>(), "local_prefix[" + r.prefix + "]");
            }
        }
        // 第2步：调用快递100 autonumber 接口，取候选列表（绝不默认第一个，让用户确认）
        List<CarrierCandidate> candidates = autoDetectCarrierCandidates(no);
        if (candidates == null || candidates.isEmpty()) {
            return new CarrierDetectResult(false, null, null, false,
                    new ArrayList<>(), "autonumber_api_empty");
        }
        if (candidates.size() == 1) {
            CarrierCandidate c = candidates.get(0);
            return new CarrierDetectResult(true, c.getCode(), c.getName(), c.isNeedPhone(),
                    candidates, "autonumber_api_single");
        }
        // ≥2个候选，必须用户手动选择（绝不瞎猜，避免串单）
        return new CarrierDetectResult(false, null, null, false, candidates, "autonumber_api_multi");
    }

    /**
     * 查询物流轨迹（兼容旧调用：无手机号）
     *
     * @param trackingNo 物流单号
     * @param carrier    承运商（中文名，如"顺丰"）
     * @return 物流轨迹结果
     */
    public LogisticsTrackResult query(String trackingNo, String carrier) {
        return query(trackingNo, carrier, null, null, null);
    }

    /**
     * 查询物流轨迹（含手机号后四位，用于京东/顺丰等）
     *
     * @param trackingNo 物流单号
     * @param carrier    承运商（中文名，如"顺丰"）
     * @param phone      收件人完整手机号（会自动提取后四位），可为空
     * @return 物流轨迹结果
     */
    public LogisticsTrackResult query(String trackingNo, String carrier, String phone) {
        return query(trackingNo, carrier, phone, null, null);
    }

    /**
     * 查询物流轨迹（完整版，支持所有参数）
     *
     * @param trackingNo   物流单号
     * @param carrier      承运商中文名（优先用carrierCode）
     * @param phone        完整手机号，传了就从里面取后四位
     * @param carrierCode  承运商代码（jd/shunfeng/...，优先级高于carrier中文名）
     * @param phoneLast4   手机号后四位（可直接传4位，不走phone解析；与phone二选一）
     * @return 物流轨迹结果
     */
    public LogisticsTrackResult query(String trackingNo, String carrier, String phone,
                                      String carrierCode, String phoneLast4) {
        if (!isEnabled()) {
            return LogisticsTrackResult.fail("物流查询功能未启用，请到「设置中心 → 物流配置」开启");
        }
        if (trackingNo == null || trackingNo.trim().isEmpty()) {
            return LogisticsTrackResult.fail("物流单号不能为空");
        }

        String normalizedNo = trackingNo.trim();
        // 解析承运商编码：carrierCode 优先级最高 → 中文名 → 自动识别
        String code = null;
        String resolvedCarrierName = carrier;
        if (carrierCode != null && !carrierCode.trim().isEmpty()) {
            code = carrierCode.trim().toLowerCase();
            if (resolvedCarrierName == null || resolvedCarrierName.isEmpty()) {
                resolvedCarrierName = CODE_TO_NAME_MAP.getOrDefault(code, code);
            }
        }
        if (code == null) {
            code = resolveCarrierCode(carrier);
        }
        // 还没拿到code：调用 detectCarrier 自动识别（严格规则+autonumber候选）
        if (code == null) {
            CarrierDetectResult dr = detectCarrier(normalizedNo);
            if (dr.isMatched()) {
                code = dr.getMatchedCode();
                resolvedCarrierName = dr.getMatchedName();
                log.info("自动识别承运商成功(一个单号就能查): {} → {}({})", normalizedNo, resolvedCarrierName, code);
            } else if (!dr.getCandidates().isEmpty()) {
                // 多候选 → 告诉前端要用户选，不瞎猜第一个
                LogisticsTrackResult r = LogisticsTrackResult.fail("识别到多个可能的承运商，请手动选择");
                r.setCarrierCandidates(dr.getCandidates());
                r.setNeedUserSelectCarrier(true);
                return r;
            } else {
                return LogisticsTrackResult.fail("无法识别承运商，请在出库单【承运商】字段选择快递公司（如：顺丰/中通/圆通/京东等）");
            }
        }

        // 命中缓存直接返回（避免看板自动刷新时触发频率限制）
        String cacheKey = code + ":" + normalizedNo;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            log.debug("命中缓存: {}", cacheKey);
            return cached.result;
        }

        String customer = getCustomer();
        String key = getKey();
        if (customer == null || customer.isEmpty()) {
            return LogisticsTrackResult.fail("快递100授权码未配置，请到「设置中心 → 物流配置」填写授权码");
        }
        if (key == null || key.isEmpty()) {
            return LogisticsTrackResult.fail("快递100签名密钥未配置，请到「设置中心 → 物流配置」填写签名密钥(Key)");
        }

        // 计算手机号后四位：支持传完整 phone，或直接传 phoneLast4
        String last4 = "";
        if (phoneLast4 != null && !phoneLast4.trim().isEmpty()) {
            String d = phoneLast4.trim().replaceAll("[^0-9]", "");
            if (d.length() == 4) last4 = d;
            else if (d.length() > 4) last4 = d.substring(d.length() - 4);
        }
        if (last4.isEmpty() && phone != null && !phone.trim().isEmpty()) {
            String p = phone.trim().replaceAll("[^0-9]", "");
            if (p.length() >= 4) last4 = p.substring(p.length() - 4);
        }
        boolean needPhone = NEED_PHONE_CODES.contains(code);
        // 把"需要手机号"标记塞到返回结果里，前端根据needPhone+空显示输入框
        final String finalLast4 = last4;
        final String finalCarrierCode = code;
        final String finalCarrierName = resolvedCarrierName;

        try {
            // 构造 param JSON（快递100企业版要求）
            Map<String, String> paramMap = new LinkedHashMap<>();
            paramMap.put("com", finalCarrierCode);
            paramMap.put("num", normalizedNo);
            paramMap.put("phone", finalLast4);
            paramMap.put("from", "");
            paramMap.put("to", "");
            paramMap.put("resultv2", "1");
            paramMap.put("show", "0");
            paramMap.put("order", "desc");
            String param = mapper.writeValueAsString(paramMap);

            // 签名：MD5(param + key + customer)，然后大写
            String signRaw = param + key + customer;
            String sign = md5Hex(signRaw).toUpperCase();

            // POST 表单：customer / param / sign
            FormBody formBody = new FormBody.Builder(StandardCharsets.UTF_8)
                    .add("customer", customer)
                    .add("param", param)
                    .add("sign", sign)
                    .build();

            Request request = new Request.Builder()
                    .url(QUERY_URL)
                    .header("User-Agent", "Mozilla/5.0 (compatible; ERP-System/1.0)")
                    .header("Accept", "application/json")
                    .post(formBody)
                    .build();

            log.info("快递100查询请求: trackingNo={}, carrier={}({}), phoneSuffix={}",
                    normalizedNo, finalCarrierName, finalCarrierCode, finalLast4);

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (response.code() != 200) {
                    log.warn("快递100查询失败: HTTP {} body(前300)={}", response.code(), truncate(body, 300));
                    LogisticsTrackResult r = LogisticsTrackResult.fail("快递100查询失败: HTTP " + response.code());
                    r.setNeedPhone(needPhone);
                    return r;
                }

                // 前置检测：快递100有时返回HTML登录页/错误页（以 '<' 开头）
                String trimmedBody = body == null ? "" : body.trim();
                if (trimmedBody.isEmpty()) {
                    LogisticsTrackResult r = LogisticsTrackResult.fail("快递100返回空响应");
                    r.setNeedPhone(needPhone);
                    return r;
                }
                if (trimmedBody.startsWith("<")) {
                    String sample = truncate(trimmedBody, 120);
                    log.error("快递100返回HTML而非JSON: body(前300)={}", truncate(body, 300));
                    LogisticsTrackResult r;
                    if (sample.contains("登录") || sample.contains("login") || sample.contains("403") || sample.contains("授权")) {
                        r = LogisticsTrackResult.fail("快递100授权码无效，请检查「设置中心 → 物流配置」中的 Customer / Key 是否正确填写");
                    } else if (sample.toLowerCase().contains("sign") || sample.contains("签名") || sample.contains("签名错误")) {
                        r = LogisticsTrackResult.fail("快递100签名失败，请确认签名密钥(Key)是否正确");
                    } else {
                        r = LogisticsTrackResult.fail("快递100接口返回异常，请稍后重试或检查授权配置");
                    }
                    r.setNeedPhone(needPhone);
                    return r;
                }

                LogisticsTrackResult result = parseResponse(body, normalizedNo, finalCarrierCode, finalCarrierName);
                // 把前端需要的识别信息塞回结果
                result.setNeedPhone(needPhone);
                result.setDetectedCarrierCode(finalCarrierCode);
                result.setDetectedCarrierName(finalCarrierName);
                // 对于需要手机号但查失败的情况，提示用户补全手机号后四位
                if (!result.isSuccess() && needPhone && finalLast4.isEmpty()) {
                    String hint = finalCarrierName + " 需要收件人手机号后四位才能查询，请在下方输入手机号后四位后重新查询";
                    if (result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()) {
                        LogisticsTrackResult r = LogisticsTrackResult.fail(result.getErrorMessage() + "（提示：" + hint + "）");
                        r.setNeedPhone(true);
                        r.setDetectedCarrierCode(finalCarrierCode);
                        r.setDetectedCarrierName(finalCarrierName);
                        return r;
                    }
                    LogisticsTrackResult r = LogisticsTrackResult.fail("提示：" + hint);
                    r.setNeedPhone(true);
                    r.setDetectedCarrierCode(finalCarrierCode);
                    r.setDetectedCarrierName(finalCarrierName);
                    return r;
                }
                // 缓存成功结果
                if (result.isSuccess()) {
                    cache.put(cacheKey, new CacheEntry(result, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
                }
                return result;
            }
        } catch (Exception e) {
            log.error("快递100查询异常: trackingNo={}, carrier={}", normalizedNo, finalCarrierName, e);
            LogisticsTrackResult r = LogisticsTrackResult.fail("查询异常: " + e.getMessage());
            r.setNeedPhone(needPhone);
            return r;
        }
    }

    /**
     * 承运商名称 → 快递100公司编码
     * 支持：中文名（顺丰/中通...） / 已是编码（shunfeng）/ 别名
     */
    private String resolveCarrierCode(String carrier) {
        if (carrier == null || carrier.trim().isEmpty()) {
            return null;
        }
        String trimmed = carrier.trim();

        // 1. 精确匹配中文名
        String code = CARRIER_CODE_MAP.get(trimmed);
        if (code != null) return code;

        // 2. 包含匹配（如"顺丰速运有限公司" → 包含"顺丰"）
        for (Map.Entry<String, String> entry : CARRIER_CODE_MAP.entrySet()) {
            if (trimmed.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 3. 已经是编码（小写英文）直接返回
        if (trimmed.matches("^[a-z]+$")) {
            return trimmed;
        }
        return null;
    }

    /**
     * 调用快递100 autonumber 接口识别承运商候选列表
     * - 永远不自动挑第一个（避免串单，特别是77纯数字中通/申通极容易错）
     * - 失败 / 无授权 → 返回空列表（让前端提示用户手动选）
     */
    private List<CarrierCandidate> autoDetectCarrierCandidates(String trackingNo) {
        List<CarrierCandidate> result = new ArrayList<>();
        try {
            String customer = getCustomer();
            String key = getKey();
            StringBuilder urlBuilder = new StringBuilder(AUTONUMBER_URL)
                    .append("?num=").append(URLEncoder.encode(trackingNo, StandardCharsets.UTF_8));
            if (customer != null && !customer.isEmpty()) {
                urlBuilder.append("&customer=").append(URLEncoder.encode(customer, StandardCharsets.UTF_8));
            }
            if (key != null && !key.isEmpty()) {
                urlBuilder.append("&key=").append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            }
            Request request = new Request.Builder()
                    .url(urlBuilder.toString())
                    .header("User-Agent", "Mozilla/5.0 (compatible; ERP-System/1.0)")
                    .header("Accept", "application/json")
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                String trimmed = body == null ? "" : body.trim();
                if (response.code() == 200 && !trimmed.isEmpty() && !trimmed.startsWith("<")) {
                    JsonNode arr = mapper.readTree(body);
                    if (arr.isArray()) {
                        for (int i = 0; i < arr.size(); i++) {
                            JsonNode n = arr.get(i);
                            String code = n.path("comCode").asText("");
                            if (code == null || code.isEmpty()) continue;
                            String name = n.path("name").asText("");
                            if (name == null || name.isEmpty()) {
                                name = CODE_TO_NAME_MAP.getOrDefault(code, code);
                            }
                            boolean need = NEED_PHONE_CODES.contains(code);
                            result.add(new CarrierCandidate(code, name, need));
                        }
                        log.info("autonumber候选列表: trackingNo={}, candidates={}", trackingNo, result.size());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("autonumber接口异常(不阻断，返回空让用户手动选): {}", e.getMessage());
        }
        return result;
    }

    /**
     * 解析快递100 query 响应（企业版 / 免费版 通用）
     * 响应格式：{state, message, status, data:[{time, ftime, context}...]}
     */
    private LogisticsTrackResult parseResponse(String body, String trackingNo, String code, String carrierName) {
        try {
            JsonNode root = mapper.readTree(body);
            String status = root.path("state").asText(root.path("status").asText(""));
            String message = root.path("message").asText(root.path("msg").asText(""));
            String nu = root.path("nu").asText(root.path("number").asText(trackingNo));

            // 快递100返回 message!="ok" 等错误信息（企业版 message=OK / 免费版 ok / message=查询成功）
            String msgLower = message == null ? "" : message.trim().toLowerCase();
            boolean msgOk = msgLower.isEmpty() || "ok".equals(msgLower) || "success".equals(msgLower)
                    || "查询成功".equals(message) || "暂无信息".equals(message);
            if (!msgOk) {
                // 对"暂无轨迹"这类不视为失败，但仍要告知用户
                if (message.contains("暂无") || message.contains("查询不到") || message.contains("没有信息")) {
                    log.warn("快递100暂无轨迹: trackingNo={}, message={}", trackingNo, message);
                    List<Kuaidi100Service.TrackItem> empty = new ArrayList<>();
                    return new LogisticsTrackResult(
                            false, "暂无轨迹（" + message + "，请确认单号正确或稍后再查询）",
                            trackingNo, nu, code, carrierName, status,
                            mapStatusCode(status), empty, LocalDateTime.now()
                    );
                }
                log.warn("快递100返回错误: trackingNo={}, message={}", trackingNo, message);
                return LogisticsTrackResult.fail("快递100: " + message);
            }

            JsonNode dataNode = root.path("data");
            List<Kuaidi100Service.TrackItem> items = new ArrayList<>();
            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    String time = item.path("ftime").asText(item.path("time").asText(""));
                    String context = item.path("context").asText("");
                    if (!time.isEmpty() || !context.isEmpty()) {
                        items.add(new Kuaidi100Service.TrackItem(time, context));
                    }
                }
            }

            // 确保"最新在前"：如果data已经是倒序(order=desc)则不必反转；否则反转
            // 为保持兼容：当 order 参数传 desc 时，API返回新->旧；但这里统一做一次稳定性判断
            if (items.size() >= 2) {
                String first = items.get(0).getTime();
                String last = items.get(items.size() - 1).getTime();
                if (!first.isEmpty() && !last.isEmpty() && first.compareTo(last) < 0) {
                    // 旧->新 排的，要反转成 新->旧
                    Collections.reverse(items);
                }
            }

            String statusText = mapStatusCode(status);
            LogisticsTrackResult result = new LogisticsTrackResult(
                    true, "", trackingNo, nu, code, carrierName, status, statusText, items,
                    LocalDateTime.now()
            );
            return result;
        } catch (Exception e) {
            String sample = truncate(body == null ? "" : body, 200);
            log.error("解析快递100响应失败: body(前200)={}", sample, e);
            return LogisticsTrackResult.fail("解析快递100响应失败: 接口返回数据格式异常（已记录日志）");
        }
    }

    // ==================== 工具方法 ====================

    /** MD5 转 32位小写十六进制 */
    private static String md5Hex(String raw) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            int v = b & 0xff;
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    /** 截断字符串（日志输出安全） */
    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...[truncated " + s.length() + " chars]";
    }

    /**
     * 快递100 state 状态码 → 中文文案 + 看板颜色
     * 0=在途 1=揽收 2=疑难 3=已签收 4=退签 5=派件中 6=退回 7=转单 8=清关 10=待取件
     */
    public static String mapStatusCode(String state) {
        if (state == null) return "未知";
        return switch (state) {
            case "0" -> "在途";
            case "1" -> "已揽收";
            case "2" -> "疑难";
            case "3" -> "已签收";
            case "4" -> "退签";
            case "5" -> "派送中";
            case "6" -> "退回";
            case "7" -> "转单";
            case "8" -> "清关中";
            case "10" -> "待取件";
            default -> "未知(" + state + ")";
        };
    }

    /**
     * 状态码 → 颜色标识（前端看板用）
     * 已揽收=blue, 在途=orange, 派送中=purple, 已签收=success, 异常=danger
     */
    public static String statusColor(String state) {
        if (state == null) return "info";
        return switch (state) {
            case "1" -> "primary";        // 已揽收 蓝
            case "0", "8", "10" -> "warning"; // 在途/清关/待取件 橙
            case "5" -> "info";           // 派送中 紫（info在element中接近蓝灰，实际看板用purple类）
            case "3" -> "success";        // 已签收 绿
            case "2", "4", "6" -> "danger"; // 疑难/退签/退回 红
            case "7" -> "info";           // 转单
            default -> "info";
        };
    }

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        final LogisticsTrackResult result;
        final Instant expireAt;
        CacheEntry(LogisticsTrackResult result, Instant expireAt) {
            this.result = result;
            this.expireAt = expireAt;
        }
        boolean isValid() {
            return Instant.now().isBefore(expireAt);
        }
    }

    // ============= DTO =============

    /**
     * 物流轨迹查询结果
     */
    public static class LogisticsTrackResult {
        private boolean success;
        private String errorMessage;
        private String trackingNo;        // 请求时的单号
        private String returnedNo;        // 快递100返回的单号
        private String carrierCode;      // 快递100公司编码
        private String carrierName;      // 承运商中文名
        private String statusCode;       // 快递100 state 状态码
        private String statusText;      // 状态中文
        private List<TrackItem> tracks;  // 轨迹列表（最新在前）
        private LocalDateTime queryTime;

        // ======== 一键查询（仅输入单号）功能专用字段 ========
        /** 是否需要手机号后四位（顺丰/京东=true），前端根据此flag显示手机号输入框 */
        private boolean needPhone;
        /** 后端检测到多个候选承运商，需要用户手动选择（不瞎猜第一个避免串单） */
        private boolean needUserSelectCarrier;
        /** 候选承运商列表（needUserSelectCarrier=true时非空） */
        private List<CarrierCandidate> carrierCandidates;
        /** 后端自动识别到的承运商编码（回填出库单用） */
        private String detectedCarrierCode;
        /** 后端自动识别到的承运商中文名（回填出库单用） */
        private String detectedCarrierName;

        // ======== 多通道路由（快递100 / 17TRACK国际）新增字段 ========
        /** 本次结果来自哪个通道：KD100 / 17TRACK（默认KD100，保持旧行为） */
        private String channel = "KD100";
        /** 通道中文名（前端展示"由 快递100/17TRACK 提供"） */
        private String channelName = "快递100";
        /** 额度/费用提示（17TRACK 会按次扣额度，前端提示用户） */
        private String quotaNote;
        /** 通道自定义颜色（为空时按快递100状态码映射） */
        private String statusColor;

        public LogisticsTrackResult(boolean success, String errorMessage, String trackingNo,
                                     String returnedNo, String carrierCode, String carrierName,
                                     String statusCode, String statusText, List<TrackItem> tracks,
                                     LocalDateTime queryTime) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.trackingNo = trackingNo;
            this.returnedNo = returnedNo;
            this.carrierCode = carrierCode;
            this.carrierName = carrierName;
            this.statusCode = statusCode;
            this.statusText = statusText;
            this.tracks = tracks;
            this.queryTime = queryTime;
            this.carrierCandidates = new ArrayList<>();
        }

        public static LogisticsTrackResult fail(String msg) {
            return new LogisticsTrackResult(false, msg, "", "", "", "", "", "", new ArrayList<>(), LocalDateTime.now());
        }

        // getters
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public String getTrackingNo() { return trackingNo; }
        public String getReturnedNo() { return returnedNo; }
        public String getCarrierCode() { return carrierCode; }
        public String getCarrierName() { return carrierName; }
        public String getStatusCode() { return statusCode; }
        public String getStatusText() { return statusText; }
        public List<TrackItem> getTracks() { return tracks; }
        public LocalDateTime getQueryTime() { return queryTime; }

        public boolean isNeedPhone() { return needPhone; }
        public boolean isNeedUserSelectCarrier() { return needUserSelectCarrier; }
        public List<CarrierCandidate> getCarrierCandidates() { return carrierCandidates; }
        public String getDetectedCarrierCode() { return detectedCarrierCode; }
        public String getDetectedCarrierName() { return detectedCarrierName; }
        public String getChannel() { return channel; }
        public String getChannelName() { return channelName; }
        public String getQuotaNote() { return quotaNote; }

        // setters（给识别字段用）
        public void setNeedPhone(boolean needPhone) { this.needPhone = needPhone; }
        public void setNeedUserSelectCarrier(boolean v) { this.needUserSelectCarrier = v; }
        public void setCarrierCandidates(List<CarrierCandidate> l) { this.carrierCandidates = l == null ? new ArrayList<>() : l; }
        public void setDetectedCarrierCode(String s) { this.detectedCarrierCode = s; }
        public void setDetectedCarrierName(String s) { this.detectedCarrierName = s; }
        public void setChannel(String s) { this.channel = s; }
        public void setChannelName(String s) { this.channelName = s; }
        public void setQuotaNote(String s) { this.quotaNote = s; }
        /** 通道自定义颜色（17TRACK等非快递100状态码用）；为空时回退快递100状态码映射 */
        public void setStatusColor(String s) { this.statusColor = s; }
        /** 【2026-09-11 修复】失败分支不回填单号/承运商，导致前端看到空值；补 setter 以支持统一兜底回填 */
        public void setTrackingNo(String s) { this.trackingNo = s; }
        public void setCarrierName(String s) { this.carrierName = s; }

        public String getStatusColor() { return statusColor != null ? statusColor : Kuaidi100Service.statusColor(statusCode); }

        /**
         * 最新一条轨迹（看板列表用）
         */
        public String getLatestContext() {
            if (tracks == null || tracks.isEmpty()) return "";
            return tracks.get(0).getContext();
        }

        /**
         * 最新轨迹时间（看板列表用）
         */
        public String getLatestTime() {
            if (tracks == null || tracks.isEmpty()) return "";
            return tracks.get(0).getTime();
        }
    }

    /**
     * 单条轨迹
     */
    public static class TrackItem {
        private final String time;
        private final String context;

        public TrackItem(String time, String context) {
            this.time = time;
            this.context = context;
        }

        public String getTime() { return time; }
        public String getContext() { return context; }
    }
}
