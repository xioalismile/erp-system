package com.erp.logistics.service;

import com.erp.config.service.SysConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 17TRACK 国际物流查询服务（api.17track.net /track/v2.2）
 *
 * 查询链路：注册单号 + 获取详情
 *   - POST /register     注册订阅单号（每个单号一次性扣 1 个额度）
 *   - POST /track/v2.2/gettrackinfo  读取轨迹详情（注册成功后重复调用不再扣费）
 *
 * 为什么不用 getRealTimeTrackInfo（实时查询）？
 *   2026-09-11 实测：本账号调用该接口返回
 *   -18019912 "The real-time query interface is not authorized."（接口未授权），
 *   且该接口每次调用都扣额度（Standard 1 个 / Instant 10 个）。
 *   改用 register + gettrackinfo 后：code=0 正常返回，且单号注册一次后查询不再扣费。
 *
 * 密钥配置（优先级从高到低）：
 *   1. sys_config：logistics.17track.token（设置中心维护，推荐）
 *   2. application.yml：erp.logistics.seventeen-track.token（兜底）
 *   3. 环境变量 ERP_17TRACK_TOKEN（部署时）
 *
 * 安全：密钥属敏感信息，仅在后端内存使用，绝不通过任何 API 返回明文。
 */
@Slf4j
@Service
public class SeventeenTrackService {

    /** sys_config 配置键 - 17TRACK 密钥(17token) */
    public static final String CFG_TOKEN = "logistics.17track.token";
    /** sys_config 配置键 - 国际物流查询开关 */
    public static final String CFG_ENABLED = "logistics.17track.enabled";
    /** sys_config 配置键 - 国际承运商关键字清单（逗号分隔） */
    public static final String CFG_INTL_CARRIERS = "logistics.17track.international_carriers";

    private static final String BASE_URL = "https://api.17track.net/track/v2.2";
    /** 注册单号（扣 1 个额度，同一单号不重复扣） */
    private static final String REGISTER_URL = BASE_URL + "/register";
    /** 获取轨迹详情（不额外扣费） */
    private static final String GET_INFO_URL = BASE_URL + "/gettrackinfo";
    /** 剩余单量（无参数、不扣额度，用于校验密钥） */
    private static final String QUOTA_URL = BASE_URL + "/getquota";

    /** 官网注册地址（前端引导用） */
    public static final String REGISTER_URL_UI = "https://api.17track.net";

    /** 国际承运商关键字默认值（与 db/64_logistics_17track.sql 的默认值保持一致） */
    public static final String DEFAULT_INTL_CARRIERS =
            "DHL,UPS,FedEx,Fedex,USPS,TNT,Aramex,Royal Mail,La Poste,PostNL,Japan Post,"
            + "Singapore Post,e邮宝,China Post,燕文,4PX,云途,YunExpress,递四方,顺丰国际";

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 缓存时长：gettrackinfo 重复调用不扣费，但 17TRACK 官方对已注册单号的跟踪频率本身就是
     * 6~12 小时一次，5 分钟缓存既能保证界面响应速度，又不会频繁打对方接口。
     */
    private static final long CACHE_TTL_SECONDS = 300;

    @Value("${erp.logistics.seventeen-track.enabled:false}")
    private boolean enabledDefault;

    @Value("${erp.logistics.seventeen-track.token:}")
    private String tokenDefault;

    private final SysConfigService sysConfigService;
    private final ObjectMapper mapper = new ObjectMapper();
    private OkHttpClient httpClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public SeventeenTrackService(org.springframework.beans.factory.ObjectProvider<SysConfigService> sysConfigServiceProvider) {
        this.sysConfigService = sysConfigServiceProvider.getIfAvailable();
    }

    private String cfg(String key, String fallback) {
        if (sysConfigService != null) {
            try {
                String v = sysConfigService.getValue(key, "");
                if (v != null && !v.trim().isEmpty()) return v.trim();
            } catch (Exception e) {
                log.warn("SeventeenTrackService 读取 sys_config 失败 key={}, 降级用默认值. Error: {}", key, e.getMessage());
            }
        }
        return fallback;
    }

    public String getToken() {
        return cfg(CFG_TOKEN, tokenDefault);
    }

    public boolean isServiceEnabled() {
        if (sysConfigService != null) {
            try {
                return sysConfigService.getBooleanValue(CFG_ENABLED, enabledDefault);
            } catch (Exception e) {
                log.warn("SeventeenTrackService.isServiceEnabled() 读取 sys_config 失败，降级为关闭. Error: {}", e.getMessage());
                return false;
            }
        }
        return enabledDefault;
    }

    /** 国际承运商关键字原始串（逗号分隔），供通道路由判定使用 */
    public String getInternationalCarriersRaw() {
        return cfg(CFG_INTL_CARRIERS, DEFAULT_INTL_CARRIERS);
    }

    /** 配置状态（脱敏：只返回是否已配置） */
    public boolean isConfigured() {
        String t = getToken();
        return t != null && !t.isEmpty();
    }

    /**
     * 脱敏后的密钥（前2后2，中间***），供「设置中心 → 物流配置」页展示。
     * 与 isConfigured() 同源（sys_config → application.yml → 环境变量），避免"已配置但掩码为空"。
     */
    public String getTokenMasked() {
        return maskSecret(getToken());
    }

    /** 脱敏：前2后2，中间***；长度 ≤ 4 全部 * */
    private static String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) return "";
        if (secret.length() <= 4) return "****";
        return secret.substring(0, 2) + "***" + secret.substring(secret.length() - 2);
    }

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        log.info("17TRACK国际物流服务初始化: enabled={}, token={}, 查询链路=register+gettrackinfo",
                isServiceEnabled(), isConfigured() ? "已配置" : "未配置");
    }

    // ==================== 对外查询 ====================

    /**
     * 查询国际物流轨迹
     * 流程：gettrackinfo → 没有轨迹则 register 注册 → 再 gettrackinfo
     *
     * @param trackingNo 物流单号
     * @return 与快递100统一的查询结果对象
     */
    public Kuaidi100Service.LogisticsTrackResult query(String trackingNo) {
        if (!isServiceEnabled()) {
            return withChannel(fail("17TRACK国际物流查询未启用，请到「设置中心 → 物流配置」开启"));
        }
        if (trackingNo == null || trackingNo.trim().isEmpty()) {
            return withChannel(fail("物流单号不能为空"));
        }
        String token = getToken();
        if (token == null || token.isEmpty()) {
            return withChannel(fail("17TRACK密钥(17token)未配置，请到「设置中心 → 物流配置」填写"));
        }

        String no = trackingNo.trim().toUpperCase();
        CacheEntry cached = cache.get(no);
        if (cached != null && cached.isValid()) {
            log.debug("17TRACK命中缓存: {}", no);
            return cached.result;
        }

        try {
            // 第1步：直接读详情
            DetailResult detail = fetchDetail(token, no);

            // 第2步：没有任何轨迹 → 说明该单号可能还没注册，先注册再读一次
            //（register 每个单号只扣 1 次额度；已注册的单号再注册不重复扣费）
            if (!detail.hasTracks()) {
                String regError = registerNumber(token, no);
                if (regError != null) {
                    return withChannel(fail(regError));
                }
                if (!detail.acceptedEmpty()) {
                    // 之前能取到数据只是暂时没轨迹，不必再查一次
                    log.info("17TRACK单号已注册但暂无轨迹: {}", no);
                } else {
                    detail = fetchDetail(token, no);
                }
            }

            if (detail.hasTracks()) {
                Kuaidi100Service.LogisticsTrackResult ok = detail.toResult(no);
                withChannel(ok);
                cache.put(no, new CacheEntry(ok, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
                return ok;
            }

            // 已注册但仍无轨迹：正常返回"暂无轨迹"提示（后续 17TRACK 更新后会自动同步）
            Kuaidi100Service.LogisticsTrackResult empty = fail(
                    "暂无轨迹（17TRACK：该单号已订阅跟踪，运输商尚未更新物流信息，请稍后再查询）");
            withChannel(empty);
            cache.put(no, new CacheEntry(empty, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
            return empty;
        } catch (Exception e) {
            log.error("17TRACK查询异常: trackingNo={}", no, e);
            return withChannel(fail("17TRACK查询异常: " + e.getMessage()));
        }
    }

    /**
     * 测试连接：调用 getquota（无参数、不扣额度），同时返回剩余单量。
     */
    public Track17Quota testConnection() {
        Track17Quota q = new Track17Quota();
        String token = getToken();
        if (token == null || token.isEmpty()) {
            q.setSuccess(false);
            q.setMessage("17TRACK密钥(17token)未配置");
            return q;
        }
        try {
            Request request = new Request.Builder()
                    .url(QUOTA_URL)
                    .header("17token", token)
                    .header("Accept", "application/json")
                    .post(RequestBody.create("", JSON_TYPE))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                int httpCode = response.code();
                if (httpCode == 401) {
                    q.setSuccess(false);
                    q.setMessage("密钥无效（HTTP 401），请确认17token是否填写正确");
                    return q;
                }
                if (httpCode != 200) {
                    log.warn("17TRACK getquota HTTP异常: HTTP {} body(前200)={}", httpCode, truncate(body, 200));
                    q.setSuccess(false);
                    q.setMessage("连接失败：HTTP " + httpCode);
                    return q;
                }
                JsonNode root = mapper.readTree(body);
                int code = root.path("code").asInt(-1);
                if (code != 0) {
                    q.setSuccess(false);
                    q.setMessage("连接失败：" + errorCodeText(code, root.path("message").asText("")));
                    return q;
                }
                JsonNode data = root.path("data");
                q.setSuccess(true);
                q.setQuotaTotal(data.path("quota_total").asLong(0));
                q.setQuotaUsed(data.path("quota_used").asLong(0));
                q.setQuotaRemain(data.path("quota_remain").asLong(0));
                q.setMessage("连接成功，密钥有效；剩余单量 " + q.getQuotaRemain());
                return q;
            }
        } catch (Exception e) {
            log.warn("17TRACK测试连接异常: {}", e.getMessage());
            q.setSuccess(false);
            q.setMessage("连接异常: " + e.getMessage());
            return q;
        }
    }

    // ==================== 接口调用 ====================

    /** 调用 gettrackinfo 读取轨迹详情 */
    private DetailResult fetchDetail(String token, String no) throws Exception {
        String body = postJson(token, GET_INFO_URL, "[{\"number\":\"" + escapeJson(no) + "\"}]");
        return DetailResult.parse(mapper, body, no, log);
    }

    /**
     * 注册订阅单号（首次注册扣 1 个额度；已注册单号不会重复扣费）
     *
     * @return null=注册成功或已注册；非null=给用户的失败提示
     */
    private String registerNumber(String token, String no) {
        try {
            String body = postJson(token, REGISTER_URL, "[{\"number\":\"" + escapeJson(no) + "\"}]");
            JsonNode root = mapper.readTree(body);
            int code = root.path("code").asInt(-1);
            JsonNode data = root.path("data");

            // 已注册：不算失败
            if (containsErrorCode(data, -18019901)) {
                log.info("17TRACK单号已注册，无需重复注册: {}", no);
                return null;
            }
            // 成功
            if (code == 0 && data.path("accepted").isArray() && data.path("accepted").size() > 0) {
                log.info("17TRACK单号注册成功: {} → carrier={}", no,
                        data.path("accepted").get(0).path("carrier").asInt(0));
                return null;
            }
            // 失败：优先取 rejected/errors 里的原因
            Integer ec = firstErrorCode(data);
            String em = firstErrorMessage(data);
            if (ec == null && code != 0) {
                ec = code;
                em = root.path("message").asText("");
            }
            String desc = errorCodeText(ec == null ? code : ec, em);
            log.warn("17TRACK单号注册失败: {} code={} message={}", no, ec, em);
            return "17TRACK：" + desc;
        } catch (Exception e) {
            log.error("17TRACK注册单号异常: {}", no, e);
            return "17TRACK注册单号异常: " + e.getMessage();
        }
    }

    /** 统一的 POST JSON 调用 */
    private String postJson(String token, String url, String jsonBody) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("17token", token)
                .header("Accept", "application/json")
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            int httpCode = response.code();
            if (httpCode == 429) {
                throw new IllegalStateException("17TRACK调用频率超限（3次/秒），请稍后再查询");
            }
            if (httpCode == 401) {
                throw new IllegalStateException("17TRACK密钥无效或账号不可用，请检查「设置中心 → 物流配置」中的17token");
            }
            if (httpCode != 200) {
                log.warn("17TRACK HTTP异常: {} HTTP {} body(前300)={}", url, httpCode, truncate(body, 300));
                throw new IllegalStateException("17TRACK查询失败: HTTP " + httpCode);
            }
            if (body == null || body.trim().isEmpty()) {
                throw new IllegalStateException("17TRACK返回空响应");
            }
            if (body.trim().startsWith("<")) {
                log.error("17TRACK返回HTML而非JSON: body(前300)={}", truncate(body, 300));
                throw new IllegalStateException("17TRACK接口返回异常页面，请检查网络或密钥配置");
            }
            return body;
        }
    }

    // ==================== 响应解析 ====================

    /** gettrackinfo 解析结果 */
    private static class DetailResult {
        boolean acceptedEmpty = true;
        boolean hasTracks;
        /** 顶层业务错误码（code != 0 时非空） */
        Integer topErrorCode;
        String topErrorMessage;
        List<Kuaidi100Service.TrackItem> items = new ArrayList<>();
        String statusEn = "";
        String carrierName = "";

        boolean hasTracks() { return hasTracks; }
        boolean acceptedEmpty() { return acceptedEmpty; }

        static DetailResult parse(ObjectMapper mapper, String body, String no, org.slf4j.Logger log) throws Exception {
            DetailResult r = new DetailResult();
            JsonNode root = mapper.readTree(body);
            int code = root.path("code").asInt(-1);
            JsonNode data = root.path("data");
            JsonNode accepted = data.path("accepted");

            if (code != 0) {
                r.topErrorCode = code;
                r.topErrorMessage = root.path("message").asText("");
                return r;
            }

            JsonNode chosen = null;
            JsonNode events = null;
            if (accepted.isArray()) {
                r.acceptedEmpty = accepted.size() == 0;
                for (JsonNode a : accepted) {
                    JsonNode ev = firstProviderEvents(a);
                    if (ev != null && ev.size() > 0) {
                        chosen = a;
                        events = ev;
                        break;
                    }
                }
                if (chosen == null && accepted.size() > 0) {
                    chosen = accepted.get(0);
                }
            }

            if (chosen == null) {
                return r;
            }

            JsonNode trackInfo = chosen.path("track_info");
            r.statusEn = trackInfo.path("latest_status").path("status").asText("");
            JsonNode provider = trackInfo.path("tracking").path("providers").path(0).path("provider");
            r.carrierName = !provider.path("alias").asText("").isEmpty()
                    ? provider.path("alias").asText("")
                    : provider.path("name").asText("");

            if (events != null && events.isArray()) {
                for (JsonNode ev : events) {
                    String time = normalizeTime(firstText(ev, "time_iso", "time_utc", "time"));
                    String desc = firstText(ev, "description", "desc");
                    String location = firstText(ev, "location");
                    String context = desc;
                    if (location != null && !location.isEmpty()) {
                        context = (desc == null || desc.isEmpty()) ? location : (desc + "  【" + location + "】");
                    }
                    if (!time.isEmpty() || !context.isEmpty()) {
                        r.items.add(new Kuaidi100Service.TrackItem(time, context));
                    }
                }
            }
            // 统一"最新在前"（按归一化后的本地时间倒序）
            r.items.sort((a, b) -> b.getTime().compareTo(a.getTime()));
            r.hasTracks = !r.items.isEmpty();
            return r;
        }

        Kuaidi100Service.LogisticsTrackResult toResult(String no) {
            Kuaidi100Service.LogisticsTrackResult result = new Kuaidi100Service.LogisticsTrackResult(
                    true, "", no, no, "", carrierName, statusEn, mapStatus(statusEn), items, LocalDateTime.now());
            result.setStatusColor(mapStatusColor(statusEn));
            return result;
        }
    }

    /** 取 track_info.tracking.providers[0].events */
    private static JsonNode firstProviderEvents(JsonNode acceptedItem) {
        JsonNode providers = acceptedItem.path("track_info").path("tracking").path("providers");
        if (providers.isArray() && providers.size() > 0) {
            return providers.get(0).path("events");
        }
        return null;
    }

    /** 在 data.rejected[].error.code 或 data.errors[].code 中查找指定错误码 */
    private static boolean containsErrorCode(JsonNode data, int code) {
        Integer found = firstErrorCode(data);
        return found != null && found == code;
    }

    private static Integer firstErrorCode(JsonNode data) {
        Integer c = scanErrorCode(data.path("rejected"));
        if (c != null) return c;
        return scanErrorCode(data.path("errors"));
    }

    private static Integer scanErrorCode(JsonNode arr) {
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                JsonNode err = n.path("error").isMissingNode() ? n : n.path("error");
                if (err.path("code").isInt()) return err.path("code").asInt();
            }
        }
        return null;
    }

    private static String firstErrorMessage(JsonNode data) {
        JsonNode arr = data.path("rejected");
        if (!arr.isArray() || arr.size() == 0) arr = data.path("errors");
        if (arr.isArray() && arr.size() > 0) {
            JsonNode n = arr.get(0);
            JsonNode err = n.path("error").isMissingNode() ? n : n.path("error");
            return err.path("message").asText("");
        }
        return "";
    }

    private static String firstText(JsonNode node, String... names) {
        for (String n : names) {
            JsonNode v = node.path(n);
            if (v.isTextual() && !v.asText().isEmpty()) {
                return v.asText();
            }
        }
        return "";
    }

    /**
     * 17TRACK 返回时间为 UTC，统一转成北京时间 yyyy-MM-dd HH:mm:ss 展示。
     */
    private static String normalizeTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String s = raw.trim();
        try {
            Instant instant;
            try {
                instant = OffsetDateTime.parse(s).toInstant();
            } catch (Exception e1) {
                try {
                    instant = Instant.parse(s);
                } catch (Exception e2) {
                    instant = LocalDateTime.parse(s.replace(" ", "T")).atZone(ZoneId.of("UTC")).toInstant();
                }
            }
            return LocalDateTime.ofInstant(instant, CN_ZONE).format(DISPLAY_FMT);
        } catch (Exception e) {
            return s;
        }
    }

    /** 17TRACK 物流主状态 → 中文 */
    public static String mapStatus(String status) {
        if (status == null || status.isEmpty()) return "未知";
        return switch (status) {
            case "NotFound" -> "查询不到";
            case "InfoReceived" -> "已收到信息";
            case "InTransit" -> "运输中";
            case "Expired" -> "已超期";
            case "AvailableForPickup" -> "待取件";
            case "OutForDelivery" -> "派送中";
            case "DeliveryFailure" -> "派送失败";
            case "Delivered" -> "已签收";
            case "Exception" -> "异常";
            default -> status;
        };
    }

    /** 17TRACK 物流主状态 → 看板颜色 */
    public static String mapStatusColor(String status) {
        if (status == null) return "info";
        return switch (status) {
            case "Delivered" -> "success";
            case "InfoReceived" -> "primary";
            case "InTransit", "AvailableForPickup", "Expired" -> "warning";
            case "DeliveryFailure", "Exception" -> "danger";
            default -> "info";
        };
    }

    /** 17TRACK 错误码 → 中文说明（来源：官方《错误代码》+ 实测） */
    private static String errorCodeText(Integer code, String rawMessage) {
        if (code == null) {
            return rawMessage != null && !rawMessage.isEmpty() ? rawMessage : "未知错误";
        }
        String text = switch (code) {
            case -18010001 -> "服务器IP不在白名单，请到17TRACK后台配置授权IP";
            case -18010002 -> "密钥无效（17token不正确）";
            case -18010004 -> "17TRACK账号已被禁用";
            case -18010005 -> "未授权访问";
            case -18010012 -> "单号格式不正确";
            case -18010013 -> "请求数据不合法";
            case -18019901 -> "该单号已注册（无需重复注册）";
            case -18019902 -> "该单号未注册，系统已自动注册后重试";
            case -18019903 -> "无法识别承运商，请手动指定承运商";
            case -18019907 -> "已超出当日查询上限";
            case -18019908 -> "17TRACK额度不足，请登录官网充值";
            case -18019909 -> "该单号暂时没有物流信息";
            case -18019911 -> "该承运商的单号目前无法注册，请稍后再试";
            case -18019912 -> "账号未开通「实时查询接口」授权（当前已改用注册+查详情链路，如仍出现请联系17TRACK）";
            case -18019823 -> "该承运商（如USPS）平台暂不支持注册，请先在17TRACK完成包裹购买等前置流程";
            default -> null;
        };
        if (text != null) return text;
        return (rawMessage != null && !rawMessage.isEmpty()) ? rawMessage : ("错误码 " + code);
    }

    /** JSON 字符串转义（单号理论上只有字母数字，兜底防注入） */
    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Kuaidi100Service.LogisticsTrackResult fail(String msg) {
        Kuaidi100Service.LogisticsTrackResult r = Kuaidi100Service.LogisticsTrackResult.fail(msg);
        r.setStatusColor("danger");
        return r;
    }

    /** 统一打通道标识，避免每个分支重复写 */
    private Kuaidi100Service.LogisticsTrackResult withChannel(Kuaidi100Service.LogisticsTrackResult r) {
        r.setChannel("17TRACK");
        r.setChannelName("17TRACK");
        r.setQuotaNote("17TRACK：单号注册一次扣1个额度，注册后重复查询不再扣费");
        return r;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...[truncated " + s.length() + " chars]";
    }

    private static class CacheEntry {
        final Kuaidi100Service.LogisticsTrackResult result;
        final Instant expireAt;
        CacheEntry(Kuaidi100Service.LogisticsTrackResult result, Instant expireAt) {
            this.result = result;
            this.expireAt = expireAt;
        }
        boolean isValid() {
            return Instant.now().isBefore(expireAt);
        }
    }

    /** 额度信息 DTO */
    public static class Track17Quota {
        private boolean success;
        private String message;
        private long quotaTotal;
        private long quotaUsed;
        private long quotaRemain;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getQuotaTotal() { return quotaTotal; }
        public void setQuotaTotal(long quotaTotal) { this.quotaTotal = quotaTotal; }
        public long getQuotaUsed() { return quotaUsed; }
        public void setQuotaUsed(long quotaUsed) { this.quotaUsed = quotaUsed; }
        public long getQuotaRemain() { return quotaRemain; }
        public void setQuotaRemain(long quotaRemain) { this.quotaRemain = quotaRemain; }
    }
}
