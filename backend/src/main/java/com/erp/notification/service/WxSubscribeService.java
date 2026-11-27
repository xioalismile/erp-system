package com.erp.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 微信小程序订阅消息推送（审批提醒）。
 *
 * <p>链路：用户在小程序里点"允许"授权 → 前端调 /api/auth/wx-subscribe-record 给该用户
 * sys_user.wx_subscribe_quota +1；产生审批待办 / 审批结果时，若该用户绑定了微信
 * （wx_openid 非空）且剩余额度 > 0，则调用微信 subscribe/send 推送"服务通知"，
 * 成功一次扣 1 次额度（微信规则：一次性订阅一次授权只能发一条）。</p>
 *
 * <p>降级口径（守则04 自检）：appid/secret/模板号任一未配置，或用户未授权、未绑定微信，
 * 一律静默跳过，只走站内铃铛，不影响任何业务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxSubscribeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";
    private static final String SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=";
    private static final String TPL_URL = "https://api.weixin.qq.com/wxaapi/newtmpl/gettemplate?access_token=";
    /** 小程序内落地页：审批任务列表（2026-08-27 起为普通页面，可作订阅消息 page）。 */
    private static final String LANDING_PAGE = "/pages/approval/tasks";
    /** 小程序内落地页：生产工序面板（2026-11-20 工序通知专用）。 */
    private static final String OPERATION_LANDING_PAGE = "/pages/production/operation-panel";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Value("${wx.miniapp.appid:}")
    private String appid;
    @Value("${wx.miniapp.secret:}")
    private String secret;
    @Value("${wx.miniapp.subscribe-template-id:}")
    private String templateId;
    /** 工序通知专用模板（2026-11-20：「报修工单通知」模板，与审批模板分开）。 */
    @Value("${wx.miniapp.operation-template-id:}")
    private String operationTemplateId;

    private final JdbcTemplate jdbcTemplate;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    /** access_token 缓存（stable_token，两小时有效，提前 5 分钟过期）。 */
    private volatile String accessToken;
    private volatile long tokenExpireAt = 0L;
    private final Object tokenLock = new Object();

    /** 模板关键词缓存（按模板号分桶）：tplId -> key(如 thing1) -> [名称, 类型]。取词失败为 null。 */
    private volatile Map<String, Map<String, String[]>> keywordMetaByTpl;
    private final Object tplLock = new Object();

    /** 启动自检（守则04 2.7.1-3）：明文打印配置状态与降级行为。 */
    @PostConstruct
    public void selfCheck() {
        log.info("[微信订阅消息] 自检：appid={}，secret={}，审批提醒模板号={}，工序通知模板号={}；"
                        + "任一未配置或用户未授权/未绑定微信时降级=只发站内铃铛，微信提醒静默跳过",
                isBlank(appid) ? "未配置" : "已配置",
                isBlank(secret) ? "未配置" : "已配置",
                isBlank(templateId) ? "未配置" : "已配置",
                isBlank(operationTemplateId) ? "未配置" : "已配置");
    }

    /**
     * 推送"新的审批待办"提醒（发给审批人）。
     */
    public void pushApprovalTask(Long approverUserId, String approverName,
                                 String typeName, String billNo, String nodeName, String submitterName) {
        if (approverUserId == null) return;
        PushCtx ctx = new PushCtx();
        ctx.typeName = typeName == null ? "" : typeName;
        ctx.thing = cut20(typeName + (billNo == null ? "" : " " + billNo));
        ctx.billNo = billNo == null ? "" : billNo;
        ctx.nodeName = nodeName == null ? "" : nodeName;
        ctx.approver = approverName == null ? "" : approverName;
        ctx.submitter = submitterName == null ? "" : submitterName;
        ctx.status = "待审批";
        ctx.remark = "请在小程序审批中心办理";
        ctx.time = LocalDateTime.now().format(TIME_FMT);
        sendToUser(approverUserId, ctx, "审批待办", templateId, LANDING_PAGE);
    }

    /**
     * 推送"审批结果"提醒（发给提交人 / 抄送人）。
     */
    public void pushApprovalResult(Long userId, String typeName, String billNo,
                                   boolean approved, String operatorName) {
        if (userId == null) return;
        PushCtx ctx = new PushCtx();
        ctx.typeName = typeName == null ? "" : typeName;
        ctx.thing = cut20(typeName + (billNo == null ? "" : " " + billNo));
        ctx.billNo = billNo == null ? "" : billNo;
        ctx.nodeName = "";
        ctx.approver = operatorName == null ? "" : operatorName;
        ctx.submitter = "";
        ctx.status = approved ? "审批通过" : "审批驳回";
        ctx.remark = operatorName == null || operatorName.isBlank() ? "" : (operatorName + " 审批");
        ctx.time = LocalDateTime.now().format(TIME_FMT);
        sendToUser(userId, ctx, "审批结果", templateId, LANDING_PAGE);
    }

    /**
     * 推送"工序通知"提醒（2026-11-20 新增，发给被@/下道工序负责人）。
     * 用「报修工单通知」专用模板；落地页=小程序生产工序面板。
     * status 填来源工序名称（2026-11-20 用户口径：让收件人看得出是哪道工序通知的；
     * 手动通知=被通知的工序，自动通知=刚完成的那道工序。微信状态字段上限 5 字，超长截断）。
     */
    public void pushOperationMention(Long userId, String workOrderNo,
                                     String content, String operatorName, String operationName) {
        if (userId == null || isBlank(operationTemplateId)) {
            if (userId != null) log.debug("[微信订阅消息] 工序通知跳过：工序通知模板号未配置");
            return;
        }
        PushCtx ctx = new PushCtx();
        ctx.typeName = "工序通知";
        ctx.thing = cut20(content == null ? "" : content);
        ctx.billNo = workOrderNo == null ? "" : workOrderNo;
        ctx.nodeName = "";
        ctx.approver = operatorName == null ? "" : operatorName;
        ctx.submitter = "";
        ctx.status = cut(operationName == null ? "" : operationName, 5);
        ctx.remark = "";
        ctx.time = LocalDateTime.now().format(TIME_FMT);
        sendToUser(userId, ctx, "工序通知", operationTemplateId, OPERATION_LANDING_PAGE, "operation");
    }

    private void sendToUser(Long userId, PushCtx ctx, String scene, String tplId, String landingPage) {
        this.sendToUser(userId, ctx, scene, tplId, landingPage, "approval");
    }

    /** quotaType：approval=审批额度 / operation=工序通知额度（2026-11-20 按模板分额度） */
    private void sendToUser(Long userId, PushCtx ctx, String scene, String tplId, String landingPage, String quotaType) {
        try {
            if (isBlank(appid) || isBlank(secret) || isBlank(tplId)) {
                log.debug("[微信订阅消息] {} 跳过：appid/secret/模板号未配置", scene);
                return;
            }
            Map<String, Object> user = readWxUser(userId);
            if (user == null) return;
            String openid = (String) user.get("wx_openid");
            // 按模板分额度：工序通知扣 wx_operation_quota，审批扣 wx_subscribe_quota
            String quotaCol = "operation".equals(quotaType) ? "wx_operation_quota" : "wx_subscribe_quota";
            Number quotaNum = (Number) user.get(quotaCol);
            int quota = quotaNum == null ? 0 : quotaNum.intValue();
            if (openid == null || openid.isBlank()) {
                log.debug("[微信订阅消息] {} 跳过：用户未绑定微信", scene);
                return;
            }
            if (quota <= 0) {
                log.debug("[微信订阅消息] {} 跳过：用户无剩余授权额度（需在小程序重新点允许）", scene);
                return;
            }

            Map<String, String[]> meta = getKeywordMeta(tplId);
            if (meta == null || meta.isEmpty()) {
                log.warn("[微信订阅消息] {} 跳过：模板关键词取不到（检查模板号是否属于当前 appid）", scene);
                return;
            }
            Map<String, Object> data = new HashMap<>();
            for (Map.Entry<String, String[]> e : meta.entrySet()) {
                data.put(e.getKey(), Map.of("value", pickValue(e.getValue()[0], e.getValue()[1], ctx)));
            }
            Map<String, Object> msg = new HashMap<>();
            msg.put("touser", openid);
            msg.put("template_id", tplId);
            msg.put("page", landingPage);
            msg.put("data", data);

            String resp = postJson(SEND_URL + getAccessToken(), MAPPER.writeValueAsString(msg));
            JsonNode node = MAPPER.readTree(resp == null ? "{}" : resp);
            int errcode = node.path("errcode").asInt(node.has("errcode") ? node.path("errcode").asInt() : 0);
            if (errcode == 0) {
                jdbcTemplate.update(
                        "UPDATE sys_user SET " + quotaCol + " = CASE WHEN " + quotaCol + " > 0 "
                                + "THEN " + quotaCol + " - 1 ELSE 0 END WHERE user_id = ?", userId);
                log.info("[微信订阅消息] {} 已推送 userId={} quota-1", scene, userId);
            } else if (errcode == 43101) {
                // 用户拒收/已取消订阅：额度清零，之后不再尝试
                jdbcTemplate.update("UPDATE sys_user SET " + quotaCol + " = 0 WHERE user_id = ?", userId);
                log.info("[微信订阅消息] {} 用户拒收(43101) userId={} 额度已清零", scene, userId);
            } else {
                log.warn("[微信订阅消息] {} 失败 errcode={} errmsg={}", scene, errcode, node.path("errmsg").asText(""));
            }
        } catch (Exception e) {
            // 提醒失败绝不影响审批主流程
            log.warn("[微信订阅消息] {} 推送异常（不影响业务）: {}", scene, e.getMessage());
        }
    }

    private Map<String, Object> readWxUser(Long userId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT wx_openid, wx_subscribe_quota, wx_operation_quota FROM sys_user WHERE user_id = ?", userId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("[微信订阅消息] 查询微信绑定信息失败（列可能不存在，重启后自愈）: {}", e.getMessage());
            return null;
        }
    }

    /** 按模板关键词标签/类型挑值：标签语义优先，兜底按类型。 */
    private String pickValue(String kwName, String kwType, PushCtx ctx) {
        String n = kwName == null ? "" : kwName;
        String t = kwType == null ? "" : kwType;
        if (n.contains("时间") || n.contains("日期") || t.contains("time") || t.contains("date")) return ctx.time;
        if (n.contains("状态") || n.contains("结果")) return cut(ctx.status, 5);
        if (n.contains("编号") || n.contains("单号")) return cut(ctx.billNo, 32);
        if (n.contains("单据类型") || n.contains("类型")) return cut(ctx.typeName, 20);
        if (n.contains("节点") || n.contains("环节")) return cut20(ctx.nodeName);
        if (n.contains("审批人") || n.contains("处理人") || n.contains("上报人") || n.contains("汇报人")
                || (t.contains("name") && !n.contains("名称"))) return cut(ctx.approver, 10);
        if (n.contains("发送人") || n.contains("提交人") || n.contains("申请人") || n.contains("发起人"))
            return cut(ctx.submitter, 20);
        if (n.contains("事项") || n.contains("内容") || n.contains("标题") || n.contains("摘要")) return ctx.thing;
        if (n.contains("备注") || n.contains("说明")) return cut20(ctx.remark);
        // 兜底：thing/character_string/phrase → 事项；其余 → 事项或时间
        if (t.contains("character")) return cut(ctx.billNo.isBlank() ? ctx.thing : ctx.billNo, 32);
        if (t.contains("phrase")) return cut(ctx.status, 5);
        if (t.contains("name")) return cut(ctx.approver.isBlank() ? ctx.submitter : ctx.approver, 10);
        if (t.contains("number")) return "1";
        return ctx.thing;
    }

    private String cut20(String s) {
        return cut(s, 20);
    }

    private String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String getAccessToken() throws Exception {
        String token = accessToken;
        if (token != null && System.currentTimeMillis() < tokenExpireAt) return token;
        synchronized (tokenLock) {
            if (accessToken != null && System.currentTimeMillis() < tokenExpireAt) return accessToken;
            String body = MAPPER.writeValueAsString(Map.of(
                    "grant_type", "client_credential", "appid", appid, "secret", secret));
            String resp = postJson(TOKEN_URL, body);
            JsonNode node = MAPPER.readTree(resp == null ? "{}" : resp);
            String at = node.path("access_token").asText(null);
            if (at == null || at.isBlank()) {
                log.warn("[微信订阅消息] 取 access_token 失败 resp={}",
                        resp == null ? "null" : resp.substring(0, Math.min(resp.length(), 300)));
                throw new IllegalStateException("微信 access_token 获取失败");
            }
            long expiresIn = node.path("expires_in").asLong(7200L);
            accessToken = at;
            tokenExpireAt = System.currentTimeMillis() + Math.max(60L, expiresIn - 300L) * 1000L;
            return at;
        }
    }

    /** 拉取指定模板的订阅消息关键词，缓存（按模板号分桶）。
     *  微信 gettemplate 真实返回：data[i].priTmplId + data[i].content（"标签:{{thing2.DATA}}\n..."），
     *  字段标签从 content 行前缀提取，类型从 key 前缀推断。 */
    private Map<String, String[]> getKeywordMeta(String tplId) {
        Map<String, Map<String, String[]>> all = keywordMetaByTpl;
        if (all != null && all.containsKey(tplId)) return all.get(tplId);
        synchronized (tplLock) {
            all = keywordMetaByTpl;
            if (all != null && all.containsKey(tplId)) return all.get(tplId);
            Map<String, String[]> found = null;
            try {
                String resp = getJson(TPL_URL + getAccessToken());
                JsonNode root = MAPPER.readTree(resp == null ? "{}" : resp);
                JsonNode list = root.path("data");
                if (list.isArray()) {
                    for (JsonNode tpl : list) {
                        String tid = tpl.path("priTmplId").asText(tpl.path("template_id").asText(""));
                        if (!tplId.equals(tid)) continue;
                        Map<String, String[]> m = new LinkedHashMap<>();
                        // 形态一（真实返回）：从 content 里解析 "标签:{{key.DATA}}"
                        String content = tpl.path("content").asText("");
                        java.util.regex.Matcher mc = CONTENT_KW.matcher(content);
                        while (mc.find()) {
                            String label = mc.group(1) == null ? "" : mc.group(1).trim();
                            m.put(mc.group(2), new String[]{label, typeOfKey(mc.group(2))});
                        }
                        // 形态二（兼容旧结构）：独立关键词数组
                        JsonNode kws = tpl.has("data") && tpl.path("data").isArray()
                                ? tpl.path("data") : tpl.path("keywords");
                        if (kws.isArray()) {
                            for (JsonNode kw : kws) {
                                String key = kw.path("key").asText(null);
                                if (key != null && !key.isBlank()) {
                                    m.put(key, new String[]{kw.path("name").asText(""), kw.path("type").asText("")});
                                }
                            }
                        }
                        if (m.isEmpty()) {
                            log.warn("[微信订阅消息] 模板 {} 内容里解析不出任何关键词：{}", tid, content);
                            found = null;
                        } else {
                            found = m;
                            log.info("[微信订阅消息] 模板关键词已加载：{} 个（{}）", m.size(), m.keySet());
                        }
                        break;
                    }
                }
                if (found == null) {
                    log.warn("[微信订阅消息] 模板列表里找不到配置的模板号：{}", tplId);
                }
            } catch (Exception e) {
                log.warn("[微信订阅消息] 拉取模板关键词失败（不影响业务，下次再试）: {}", e.getMessage());
            }
            if (keywordMetaByTpl == null) keywordMetaByTpl = new LinkedHashMap<>();
            keywordMetaByTpl.put(tplId, found);
            return found;
        }
    }

    /** content 关键词正则："标签:{{thing2.DATA}}" —— 标签允许全半角冒号。 */
    private static final java.util.regex.Pattern CONTENT_KW =
            java.util.regex.Pattern.compile("([^{:\\n]{0,30})[::]\\s*\\{\\{(\\w+)\\.DATA\\}\\}");

    /** 从 key 前缀推断字段类型（决定截断长度与兜底填法）。 */
    private String typeOfKey(String key) {
        if (key == null) return "";
        String k = key.toLowerCase();
        if (k.startsWith("character_string")) return "character_string";
        if (k.startsWith("thing")) return "thing";
        if (k.startsWith("time") || k.startsWith("date")) return "time";
        if (k.startsWith("name")) return "name";
        if (k.startsWith("phrase")) return "phrase";
        if (k.startsWith("number")) return "number";
        if (k.startsWith("amount")) return "amount";
        return k.replaceAll("\\d+$", "");
    }

    private String postJson(String url, String json) throws Exception {
        Request request = new Request.Builder().url(url)
                .post(RequestBody.create(json, JSON)).build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.body() == null ? null : response.body().string();
        }
    }

    private String getJson(String url) throws Exception {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.body() == null ? null : response.body().string();
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 一次推送的内容池。 */
    private static class PushCtx {
        String typeName;
        String thing;
        String billNo;
        String nodeName;
        String approver;
        String submitter;
        String status;
        String remark;
        String time;
    }
}
