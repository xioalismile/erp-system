package com.erp.kai.service;

import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.kai.service.KaiModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小楷独立版 · API 文档自动分析（FEAT-KAI-STANDALONE-002）。
 * 用户粘贴对方接口文档（或给文档地址由系统拉取），AI 读文档自动产出：
 * 各对象的查询/建档/开单接口地址（endpoints）、对方字段与小楷标准字段的映射（fieldMap）、
 * 建议鉴权方式——用户核对修改后才保存，不静默落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiDocAnalyzerService {

    private final KaiModelService modelService;
    private final KaiConnectionService connectionService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是 ERP 接口文档分析器。任务：从用户提供的第三方系统 API 文档中，找出以下四类对象对应的接口与字段，输出严格 JSON。

            对象与标准字段（小楷侧命名）：
            - material（物料）：code 编码 / name 名称 / category 分类 / unit 单位 / price 单价
            - customer（客户）：code / name / contact 联系人 / phone 电话
            - supplier（供应商）：code / name / contact / phone
            - sale_order（销售订单）：customerName 客户 / orderDate 单据日期 / items 明细行（materialName 物料 / quantity 数量 / unitPrice 单价）

            输出格式（只输出 JSON 本体，禁止 markdown 围栏、禁止解释文字）：
            {
              "authType": "API_KEY 或 BEARER_TOKEN 或 BASIC 或 LOGIN_TOKEN 或 API_KEY_PAIR（按文档鉴权方式选一个）",
              "endpoints": {
                "login": "登录换令牌接口地址（仅 LOGIN_TOKEN 时给）",
                "material": {"list": "查询接口路径", "create": "建档接口路径"},
                "customer": {"list": "...", "create": "..."},
                "supplier": {"list": "...", "create": "..."},
                "sale_order": {"list": "...", "create": "开单接口路径"}
              },
              "fieldMap": {
                "material": {"小楷字段": "对方接口字段"},
                "customer": {...},
                "supplier": {...},
                "sale_order": {...}
              },
              "notes": "一句话说明（文档里没覆盖的对象/字段如实说）"
            }

            铁律：
            1. 只用文档里真实写明的路径与字段名，一个都不许编造；文档没有的对象/接口直接省略该键。
            2. 路径保留文档原样（含路径参数占位符、版本前缀）。
            3. fieldMap 只在对方字段名与小楷标准字段名不一致时才需要；一致就不写该键。
            4. 文档里的字段名若带表名前缀（如 material_name、FName），按文档原样填。
            5. 文档写明 authId/authKey（或类似"标识+密钥"成对出现且拼在请求地址/查询参数上）→ authType 选 API_KEY_PAIR；写明请求头 X-API-Key → API_KEY；写明账号密码登录拿 token → LOGIN_TOKEN。
            6. 文档站地址（看文档要登录的网址）不是接口地址：接口路径以文档写明的接口请求地址为准，禁止把文档站域名当接口主机。
            7. baseUrl：若资料写明"接口与文档/业务在同一主机"或能确定接口基础地址，必须把它填进输出键 baseUrl（含 http:// 或 https://，末尾不带斜杠）；文档只给相对路径（如 /api/v2/xxx）时 baseUrl 尤其必填。
            """;

    /** 分析文档文本 → 结构化配置。失败抛 IllegalArgumentException（中文原因）。 */
    public Map<String, Object> analyze(Long kaiUserId, String docText) {
        if (docText == null || docText.trim().length() < 20) {
            throw new IllegalArgumentException("文档内容太短，请粘贴完整的 API 文档");
        }
        Probe probe = probeGenericObjectApi(docText);
        Map<String, Object> out = runAnalysis(kaiUserId, docText, probe, null);
        if (out == null || out.isEmpty() || !out.containsKey("endpoints")) {
            throw new IllegalArgumentException("没能从文档中识别出接口配置——请确认粘贴的是 API 文档（含接口路径与字段说明）");
        }
        return out;
    }

    /** 共享分析管道：模型分析（探测结果作为权威增强），模型失败时探测结果兜底，都没有则返回 null。originHint = 文档直链来源主机（相对路径的主机兜底）。 */
    private Map<String, Object> runAnalysis(Long kaiUserId, String docText, Probe probe, String originHint) {
        String modelError = null;
        Map<String, Object> out = null;
        try {
            AiChannelAdapter model = modelService.resolveAdapter(kaiUserId);
            // 文档全量送模型，不截断（2026-10-22 用户立规：小楷要全量读取，不做"省上下文"式截断）
            String llmInput = docText;
            if (originHint != null && !originHint.isBlank()) {
                llmInput = llmInput + "\n\n【资料来源地址】" + originHint
                        + "（资料由该地址拉取；若文档说明接口与业务同主机或只给相对路径，baseUrl 用它）";
            }
            if (probe != null) {
                llmInput = llmInput + "\n\n" + probe.contextForModel();
            }
            out = extractJson(model.chat(SYSTEM_PROMPT, llmInput));
        } catch (Exception e) {
            modelError = e.getMessage();
        }
        if (probe != null) {
            // 探测结果为权威：endpoints/鉴权方式强制以探测为准（模型只补 fieldMap 与说明），空缺时兜底
            out = probe.merge(out);
            if (modelError != null && out != null) {
                out.put("notes", "已通过对方表清单接口自动识别并生成配置（模型分析未参与：" + modelError + "）");
            }
        }
        if (out != null && (out.get("baseUrl") == null || String.valueOf(out.get("baseUrl")).isBlank()) && originHint != null && !originHint.isBlank()) {
            out.put("baseUrl", originHint);
        }
        return out;
    }

    // ---------------- 只分析不落库（对话「自己理解 → 提问 → 再保存」流程，FEAT-KAI-STANDALONE-011） ----------------

    /** 一次只分析的会话缓存：原始资料 + 分析结果 + 探测到的凭据（保存时直接复用，不再二次调模型）。 */
    public static final class AnalysisSession {
        public final String text;
        public final Map<String, Object> config;
        public final String origin;
        public final String authId;
        public final String authKey;
        final long expireAt;

        AnalysisSession(String text, Map<String, Object> config, String origin, String authId, String authKey) {
            this.text = text;
            this.config = config;
            this.origin = origin;
            this.authId = authId;
            this.authKey = authKey;
            this.expireAt = System.currentTimeMillis() + 30 * 60 * 1000L;
        }
    }

    private final Map<String, AnalysisSession> analysisCache = new ConcurrentHashMap<>();

    /** 按令牌取分析会话（过期/不存在返回 null）。 */
    public AnalysisSession analysisSession(String token) {
        AnalysisSession s = token == null ? null : analysisCache.get(token);
        if (s == null) {
            return null;
        }
        if (System.currentTimeMillis() > s.expireAt) {
            analysisCache.remove(token);
            return null;
        }
        return s;
    }

    /**
     * 只分析不落库：识别成功返回 token + 中文理解摘要 + 还缺什么；
     * 识别不出**不抛异常**——返回 ready=false + 中文说明，让小楷照着向用户提问，而不是报错终止。
     */
    public Map<String, Object> analyzeOnly(Long kaiUserId, String docText) {
        return analyzeOnly(kaiUserId, docText, null);
    }

    /** 同上，originHint = 文档直链来源主机（接口只给相对路径时的主机兜底）。 */
    public Map<String, Object> analyzeOnly(Long kaiUserId, String docText, String originHint) {
        Map<String, Object> res = new LinkedHashMap<>();
        if (docText == null || docText.trim().length() < 20) {
            res.put("ready", false);
            res.put("missing", List.of("资料内容太短。请向用户要接口文档：接口地址、鉴权方式、字段说明，粘贴文本或给文档直链都可以"));
            return res;
        }
        Probe probe = probeGenericObjectApi(docText);
        Map<String, Object> out = runAnalysis(kaiUserId, docText, probe, originHint);
        if (out == null || out.isEmpty() || !(out.get("endpoints") instanceof Map<?, ?> ep) || ep.isEmpty()) {
            res.put("ready", false);
            List<String> missing = new ArrayList<>();
            missing.add("没能从资料里识别出任何可用的接口地址");
            missing.add("请向用户确认：① 这是不是 API 接口文档；② 接口请求地址前缀是什么；③ 鉴权方式（密钥/令牌/账号密码）；④ 请把文档正文复制后直接粘贴过来");
            res.put("missing", missing);
            return res;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        String sessionOrigin = probe != null && probe.origin != null ? probe.origin
                : (out.get("baseUrl") != null && !String.valueOf(out.get("baseUrl")).isBlank()
                        ? String.valueOf(out.get("baseUrl")) : originHint);
        analysisCache.put(token, new AnalysisSession(docText, out, sessionOrigin,
                probe == null ? null : probe.authId,
                probe == null ? null : probe.authKey));
        res.put("token", token);
        res.put("ready", true);
        res.put("understood", describeConfig(out, probe));
        res.put("missing", missingPieces(out));
        return res;
    }

    /** 对象英文名 → 中文名（向用户汇报用）。 */
    private String objCn(String key) {
        return switch (key) {
            case "material" -> "物料";
            case "customer" -> "客户";
            case "supplier" -> "供应商";
            case "sale_order" -> "销售订单";
            case "login" -> "登录换令牌";
            default -> key;
        };
    }

    /** 把分析结果讲成人话（汇报给用户用）。 */
    private String describeConfig(Map<String, Object> out, Probe probe) {
        StringBuilder sb = new StringBuilder();
        sb.append("鉴权方式：").append(out.getOrDefault("authType", "文档没写明"));
        String origin = probe == null ? null : probe.origin;
        if (origin != null) {
            sb.append("；接口主机：").append(origin).append("（已通过真实请求自动探测确认）");
        }
        if (out.get("endpoints") instanceof Map<?, ?> ep) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> e : ep.entrySet()) {
                String k = String.valueOf(e.getKey());
                if (e.getValue() instanceof Map<?, ?> m && !m.isEmpty()) {
                    parts.add(objCn(k));
                }
            }
            if (!parts.isEmpty()) {
                sb.append("；识别到的对象：").append(String.join("、", parts));
            }
        }
        Object notes = out.get("notes");
        if (notes != null && !String.valueOf(notes).isBlank()) {
            sb.append("。说明：").append(notes);
        }
        return sb.toString();
    }

    /** 还缺什么（结构层面；凭据类提示放在文案里让小楷主动跟用户要）。 */
    private List<String> missingPieces(Map<String, Object> out) {
        List<String> miss = new ArrayList<>();
        String auth = String.valueOf(out.getOrDefault("authType", ""));
        if ("LOGIN_TOKEN".equals(auth) && out.get("endpoints") instanceof Map<?, ?> ep && !ep.containsKey("login")) {
            miss.add("鉴权方式是「登录换令牌」，但没识别到登录接口地址——请向用户要登录接口路径");
        }
        return miss;
    }

    /**
     * 聊天路径专用：分析上传的接口文档并**直接保存为系统连接**（供对话写工具 ext_integrate_system 调用）。
     * 通用对象接口走自动探测（含密钥对自动落加密凭据）；传统文档走纯模型分析。
     * 返回脱敏摘要（不含任何明文凭据）。
     */
    public Map<String, Object> connectFromDoc(Long kaiUserId, String docText, String connName) {
        return connectFromDoc(kaiUserId, docText, connName, null);
    }

    /** 同上，originHint = 文档直链来源主机（接口只给相对路径时的主机兜底）。 */
    public Map<String, Object> connectFromDoc(Long kaiUserId, String docText, String connName, String originHint) {
        Probe probe = probeGenericObjectApi(docText);
        Map<String, Object> out = runAnalysis(kaiUserId, docText, probe, originHint);
        if (out == null || out.isEmpty() || !(out.get("endpoints") instanceof Map<?, ?> ep) || ep.isEmpty()) {
            throw new IllegalArgumentException("没能从文档中识别出接口配置，无法接入");
        }
        return saveConnection(kaiUserId, out, probe, null, connName, null, null, null);
    }

    /**
     * 从「只分析」的缓存结果直接保存（不二次调模型）；用户补充了新资料则带补充整体重析。
     * 凭据参数 = 小楷从聊天里收集到的（密钥 / 账号 / 密码），用户给的优先于文档探测。
     */
    public Map<String, Object> connectFromSession(Long kaiUserId, AnalysisSession s, String extraText,
            String connName, String apiKey, String authUsername, String authPassword) {
        if (extraText != null && !extraText.isBlank()) {
            return connectFromDoc(kaiUserId, s.text + "\n\n【用户补充】\n" + extraText, connName);
        }
        return saveConnection(kaiUserId, s.config, null, connName, apiKey, authUsername, authPassword, s);
    }

    /** 保存连接（分析结果 → 连接）。probe/session 二选一提供探测凭据；credentialXxx = 用户在聊天里补的凭据，优先。 */
    private Map<String, Object> saveConnection(Long kaiUserId, Map<String, Object> out, Probe probe,
            String connName, String apiKey, String authUsername, String authPassword, AnalysisSession session) {
        String authType = String.valueOf(out.getOrDefault("authType", "API_KEY_PAIR"));
        Map<String, Object> body = new LinkedHashMap<>();
        String name = (connName == null || connName.isBlank()) ? "自动接入-" + java.time.LocalDate.now() : connName.trim();
        body.put("name", name);
        body.put("type", "GENERIC_REST");
        String origin = probe != null ? probe.origin : (session != null ? session.origin : null);
        if (origin != null) {
            body.put("baseUrl", origin);
        } else if (out.get("baseUrl") != null) {
            body.put("baseUrl", String.valueOf(out.get("baseUrl")));
        }
        body.put("authType", authType);
        String probeAuthId = probe != null ? probe.authId : (session != null ? session.authId : null);
        String probeAuthKey = probe != null ? probe.authKey : (session != null ? session.authKey : null);
        if ("API_KEY_PAIR".equals(authType) && probeAuthId != null && probeAuthKey != null) {
            body.put("credentialUsername", probeAuthId);
            body.put("credentialPassword", probeAuthKey);
        }
        // 用户在聊天里补的凭据优先（覆盖文档探测值）
        if (apiKey != null && !apiKey.isBlank()) {
            body.put("credential", apiKey.trim());
        }
        if (authUsername != null && !authUsername.isBlank()) {
            body.put("credentialUsername", authUsername.trim());
        }
        if (authPassword != null && !authPassword.isBlank()) {
            body.put("credentialPassword", authPassword.trim());
        }
        // 附加配置：endpoints + fieldMap
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("endpoints", out.get("endpoints"));
        if (out.get("fieldMap") instanceof Map && !((Map<?, ?>) out.get("fieldMap")).isEmpty()) {
            cfg.put("fieldMap", out.get("fieldMap"));
        }
        try {
            body.put("extraConfig", mapper.writeValueAsString(cfg));
        } catch (Exception e) {
            throw new IllegalArgumentException("接口配置序列化失败：" + e.getMessage());
        }
        body.put("timeoutSeconds", 30);
        Long id = connectionService.save(kaiUserId, body);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("connectionId", id);
        summary.put("name", name);
        summary.put("baseUrl", String.valueOf(body.get("baseUrl")));
        summary.put("authType", authType);
        String credDesc = ("API_KEY_PAIR".equals(authType) && probeAuthId != null && probeAuthKey != null && body.get("credential") == null)
                ? "已加密保存（authId/authKey）" : "已加密保存（凭据）";
        summary.put("credential", credDesc);
        summary.put("notes", String.valueOf(out.getOrDefault("notes", "")));
        return summary;
    }

    // ---------------- 通用对象接口自动探测（FEAT-KAI-STANDALONE-009） ----------------

    /** 探测结果：目标信息 + 按标准对象自动匹配的接口地址。 */
    private static class Probe {
        String origin;
        String appId;
        String authId;
        String authKey;
        final Map<String, String> matched = new LinkedHashMap<>();   // 标准对象 → 表id
        final Map<String, String> tableNames = new LinkedHashMap<>(); // 表id → 中文名
        final Map<String, List<String[]>> fields = new LinkedHashMap<>(); // 表id → [字段id,字段中文名,必填]
        int tablesCount;

        String contextForModel() {
            StringBuilder sb = new StringBuilder();
            sb.append("【系统自动探测结果（真实调用对方表清单接口所得，可信，endpoints 必须采用以下地址）】\n");
            sb.append("目标系统类型：通用对象接口（一套地址 + schemaid 换表 + authId/authKey 拼地址鉴权）\n");
            sb.append("表总数：").append(tablesCount).append(" 张；自动匹配到标准对象：\n");
            for (Map.Entry<String, String> e : matched.entrySet()) {
                String url = origin + "/api/object?appid=" + appId + "&schemaid=" + e.getValue();
                sb.append("- ").append(e.getKey()).append(" → 表 ").append(e.getValue())
                  .append("（").append(tableNames.getOrDefault(e.getValue(), "")).append("），list/create 地址：").append(url).append("\n");
                List<String[]> fs = fields.get(e.getValue());
                if (fs != null && !fs.isEmpty()) {
                    sb.append("  该表字段：");
                    for (String[] f : fs) sb.append(f[0]).append("(").append(f[1]).append(f[2] != null && f[2].equals("1") ? ",必填" : "").append(") ");
                    sb.append("\n");
                }
            }
            sb.append("请按输出格式生成：endpoints 使用上述地址；authType 固定为 API_KEY_PAIR；fieldMap 把小楷标准字段映射到对方字段（按字段中文名对应，没有对应的省略）；notes 简述匹配结果。");
            return sb.toString();
        }

        /** 合并：模型输出（可空）+ 探测权威结果。保证 endpoints/鉴权方式必然可用。 */
        Map<String, Object> merge(Map<String, Object> modelOut) {
            Map<String, Object> out = (modelOut != null) ? modelOut : new LinkedHashMap<>();
            out.put("authType", "API_KEY_PAIR");
            Map<String, Object> eps = new LinkedHashMap<>();
            Object me = out.get("endpoints");
            if (me instanceof Map) {
                for (Map.Entry<?, ?> en : ((Map<?, ?>) me).entrySet()) {
                    eps.put(String.valueOf(en.getKey()), en.getValue());
                }
            }
            for (Map.Entry<String, String> e : matched.entrySet()) {
                String url = origin + "/api/object?appid=" + appId + "&schemaid=" + e.getValue();
                Map<String, Object> ep = new LinkedHashMap<>();
                ep.put("list", url);
                ep.put("create", url);
                eps.put(e.getKey(), ep);
            }
            out.put("endpoints", eps);
            String note = "已自动识别为通用对象接口：匹配到 " + matched.size() + "/4 类标准对象（"
                    + matched.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue() + tableNames.getOrDefault(e.getValue(), "")).collect(java.util.stream.Collectors.joining("、"))
                    + "）；对方共 " + tablesCount + " 张表，全部表可先用对话里“看有哪些表”类问题或表清单接口查询";
            Object old = out.get("notes");
            out.put("notes", note + (old == null || String.valueOf(old).isBlank() ? "" : "；" + old));
            return out;
        }
    }

    /**
     * 从粘贴文本中识别 gzzd 风格通用对象接口：完整示例地址（appid+schemaid）+ authId/authKey 密钥对。
     * 识别成功 → 真实调用对方表清单接口（10 秒超时，失败静默返回 null，走原纯模型分析）。
     */
    private Probe probeGenericObjectApi(String docText) {
        try {
            java.util.regex.Matcher um = java.util.regex.Pattern
                    .compile("https?://[^\\s，,。；;）)】\"'<>]+?/api/object\\?appid=([^&\\s]+)&schemaid=([^&\\s]+)")
                    .matcher(docText);
            if (!um.find()) return null;
            String origin = um.group().split("/api/object")[0];
            String appId = um.group(1);
            java.util.regex.Matcher am = java.util.regex.Pattern.compile("authId\\s*[:：=]\\s*([A-Za-z0-9_-]+)").matcher(docText);
            java.util.regex.Matcher km = java.util.regex.Pattern.compile("authKey\\s*[:：=]\\s*([A-Za-z0-9_-]+)").matcher(docText);
            if (!am.find() || !km.find()) return null;
            String authId = am.group(1);
            String authKey = km.group(1);

            String url = origin + "/api/schema?appid=" + java.net.URLEncoder.encode(appId, "UTF-8")
                    + "&authId=" + java.net.URLEncoder.encode(authId, "UTF-8")
                    + "&authKey=" + java.net.URLEncoder.encode(authKey, "UTF-8");
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) return null;
            Map<String, Object> j = mapper.readValue(resp.body(), Map.class);
            Object data = j.get("data");
            if (!(data instanceof List)) return null;
            List<?> tables = (List<?>) data;
            if (tables.isEmpty()) return null;

            Probe p = new Probe();
            p.origin = origin;
            p.appId = appId;
            p.authId = authId;
            p.authKey = authKey;
            p.tablesCount = tables.size();
            for (Object o : tables) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> s = (Map<?, ?>) o;
                String id = String.valueOf(s.get("id"));
                String name = s.get("name") == null ? id : String.valueOf(s.get("name"));
                p.tableNames.put(id, name);
                if (s.get("fields") instanceof List) {
                    List<String[]> fs = new ArrayList<>();
                    for (Object fo : (List<?>) s.get("fields")) {
                        if (!(fo instanceof Map)) continue;
                        Map<?, ?> f = (Map<?, ?>) fo;
                        fs.add(new String[]{String.valueOf(f.get("id")),
                                f.get("name") == null ? "" : String.valueOf(f.get("name")),
                                Boolean.TRUE.equals(f.get("required")) ? "1" : "0"});
                    }
                    p.fields.put(id, fs);
                }
            }
            // 标准对象匹配（中文/英文关键词；表头优先，排除 *_detail 明细表）
            matchTable(p, "material", t -> t[0].equals("basic_product") || t[0].equals("material") || t[1].contains("产品") || t[1].contains("物料"));
            matchTable(p, "customer", t -> t[0].equals("basic_customer") || t[0].equals("customer") || (t[1].contains("客户") && !t[1].contains("明细")));
            matchTable(p, "supplier", t -> t[0].equals("basic_supplier") || t[0].equals("supplier") || (t[1].contains("供应商") && !t[1].contains("明细")));
            matchTable(p, "sale_order", t -> t[0].equals("sell") || t[0].equals("sale_order") || (t[1].contains("销售订单") && !t[1].contains("明细")));
            return p.matched.isEmpty() ? null : p;
        } catch (Exception e) {
            log.warn("[小楷独立版] 通用对象接口探测未命中或失败，走常规分析: {}", e.getMessage());
            return null;
        }
    }

    /** 按 matcher 挑表：优先完全一致，其次非明细表，避免把明细表错配成主表。t[0]=表id，t[1]=中文名。 */
    private void matchTable(Probe p, String stdObj, java.util.function.Predicate<String[]> rule) {
        String best = null;
        for (Map.Entry<String, String> e : p.tableNames.entrySet()) {
            String[] t = new String[]{e.getKey(), e.getValue()};
            if (!rule.test(t)) continue;
            boolean isDetail = t[0].endsWith("_detail") || t[1].contains("明细");
            boolean exact = stdObj.equals(t[0]) || t[1].equals(stdObj);
            if (exact) { best = t[0]; break; }
            if (!isDetail && best == null) best = t[0];
        }
        if (best != null) p.matched.put(stdObj, best);
    }

    /** 拉取的文档：正文 + 来源主机（相对路径接口的主机兜底）。 */
    public static final class DocSource {
        public final String text;
        public final String origin;

        public DocSource(String text, String origin) {
            this.text = text;
            this.origin = origin;
        }
    }

    /** 拉取文档地址并返回正文（仅文本，兼容旧调用）。 */
    public String fetchDoc(String docUrl) {
        return fetchDocSource(docUrl).text;
    }

    /**
     * 拉取文档地址（GET，限 500KB，带浏览器标识防最简反爬），返回正文 + 来源主机。
     * HTML 自动提取正文（去脚本/样式/标签）；提取后仍是空壳（动态页面正文靠脚本加载）时给明确中文解释，
     * 让小楷引导用户复制粘贴正文，而不是丢一句"打不开"。
     */
    public DocSource fetchDocSource(String docUrl) {
        if (docUrl == null || docUrl.isBlank()) {
            throw new IllegalArgumentException("请填写文档地址或直接粘贴文档内容");
        }
        URI uri;
        try {
            uri = URI.create(docUrl.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("文档地址格式不对，请检查链接");
        }
        String origin = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        byte[] bytes;
        String contentType;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 KaiyuXiaoKai/1.0")
                    .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 400) {
                throw new IllegalArgumentException("文档地址返回 " + resp.statusCode()
                        + "——若链接需要登录才能看，请打开链接把文档内容复制后直接粘贴给我");
            }
            bytes = resp.body();
            contentType = resp.headers().firstValue("content-type").orElse("");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("拉取文档失败（" + e.getMessage() + "）——内网地址请改用直接粘贴文档内容");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("文档地址返回空内容——若文档需要登录才能看，请把文档内容复制后直接粘贴给我");
        }
        String body = decodeBody(bytes, contentType);
        String trimmed = body.trim().toLowerCase();
        boolean looksHtml = trimmed.startsWith("<!doctype") || trimmed.startsWith("<html") || body.contains("<div");
        String text = looksHtml ? htmlToText(body) : body;
        if (text.trim().length() < 200 && bytes.length > 2000) {
            throw new IllegalArgumentException("这个链接是动态网页（正文由脚本加载），自动抓取拿不到正文——请打开链接把文档内容复制后直接粘贴给我");
        }
        return new DocSource(text, origin);
    }

    /** 按响应头 charset 解码字节（缺省 UTF-8，非法名回退 UTF-8）。 */
    private String decodeBody(byte[] bytes, String contentType) {
        String cs = "UTF-8";
        if (contentType != null) {
            Matcher m = Pattern.compile("charset=([\\w\\-]+)", Pattern.CASE_INSENSITIVE).matcher(contentType);
            if (m.find()) {
                cs = m.group(1);
            }
        }
        try {
            return new String(bytes, cs);
        } catch (Exception e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** 粗粒度 HTML→正文：去脚本/样式/注释与标签，还原常见实体，压平空白。 */
    private String htmlToText(String html) {
        String s = html.replaceAll("(?is)<(script|style|noscript|svg|iframe)[^>]*>.*?</\\1>", " ");
        s = s.replaceAll("(?is)<!--.*?-->", " ");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(p|div|li|tr|h[1-6]|table|section|pre|code)>", "\n");
        s = s.replaceAll("<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&ldquo;", "“").replace("&rdquo;", "”");
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        s = s.replaceAll(" ?\\n ?", "\n");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    /** 从模型回复中提取 JSON（容忍 ```json 围栏与前后解释文字）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        String json = s.substring(start, end + 1);
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[小楷独立版] 文档分析结果解析失败: {}", e.getMessage());
            return null;
        }
    }
}
