package com.erp.kai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小楷独立版 · 知识库（FEAT-KAI-STANDALONE-011）。
 * 上传文件 → 提取文本（与聊天文件同源解析器：pdf/docx/txt/md/csv/json）→ 分块 → 落库 → 尽力向量化。
 * 检索：向量余弦优先（所配模型服务商支持 /embeddings 时），失败/不可用自动降级关键词打分——
 * 不装向量库、不加新组件（设计蓝本：XXL-AI RAG 模块，按本工程 PostgreSQL 形态自研降级版）。
 * 检索结果整块注入对话系统提示词【参考资料】，小楷据此回答/开单/分类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiKnowledgeService {

    private final JdbcTemplate jdbcTemplate;
    private final KaiChatFileService chatFileService;
    private final KaiCryptoService cryptoService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final int MAX_CHUNKS_PER_DOC = 500;   // 单文档分块上限
    private static final int CHUNK_TARGET = 600;         // 分块目标长度
    private static final int CHUNK_HARD_CAP = 900;       // 单段超长硬切阈值
    private static final int RETRIEVE_CHUNK_CAP = 4000;  // 检索候选分块上限
    private static final int EMBED_CHAR_CAP = 1200;      // 单块送向量接口的长度上限
    private static final int CONTEXT_CHAR_CAP = 4000;    // 注入对话的参考资料总长上限
    private static final double VECTOR_MIN_SCORE = 0.10;
    private static final int EMBED_TIMEOUT_S = 20;

    /** 向量接口可用的服务商缓存（userId → provider），首次成功后复用；失败不缓存（下次重试）。 */
    private record ProviderInfo(String baseUrl, String apiKey, String model) {}
    private final Map<Long, ProviderInfo> embedProviderCache = new ConcurrentHashMap<>();

    // ---------------- 知识库主档 ----------------

    public List<Map<String, Object>> listBases(Long kaiUserId) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT b.id, b.name, b.description, b.enabled,
                       (SELECT COUNT(*) FROM kai_kb_doc d WHERE d.base_id = b.id) AS doc_count,
                       (SELECT COUNT(*) FROM kai_kb_chunk c WHERE c.base_id = b.id) AS chunk_count
                FROM kai_kb_base b WHERE b.kai_user_id = ? ORDER BY b.id DESC
                """, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("description", rs.getString("description"));
            m.put("enabled", rs.getInt("enabled") == 1);
            m.put("docCount", rs.getLong("doc_count"));
            m.put("chunkCount", rs.getLong("chunk_count"));
            out.add(m);
        }, kaiUserId);
        return out;
    }

    public Long saveBase(Long kaiUserId, Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        String name = str(body.get("name")).trim();
        if (name.isBlank()) throw new IllegalArgumentException("请填写知识库名称");
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(body.get("enabled").toString());
        if (id == null) {
            jdbcTemplate.update("INSERT INTO kai_kb_base (kai_user_id, name, description, enabled) VALUES (?,?,?,?)",
                    kaiUserId, name, str(body.get("description")), enabled ? 1 : 0);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM kai_kb_base WHERE kai_user_id=? ORDER BY id DESC LIMIT 1", Long.class, kaiUserId);
        }
        Integer owner = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kai_kb_base WHERE id=? AND kai_user_id=?", Integer.class, id, kaiUserId);
        if (owner == null || owner == 0) throw new IllegalArgumentException("知识库不存在或无权操作");
        jdbcTemplate.update("UPDATE kai_kb_base SET name=?, description=?, enabled=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                name, str(body.get("description")), enabled ? 1 : 0, id);
        return id;
    }

    public void deleteBase(Long kaiUserId, Long id) {
        jdbcTemplate.update("DELETE FROM kai_kb_chunk WHERE base_id IN (SELECT id FROM kai_kb_base WHERE id=? AND kai_user_id=?)", id, kaiUserId);
        jdbcTemplate.update("DELETE FROM kai_kb_doc WHERE base_id IN (SELECT id FROM kai_kb_base WHERE id=? AND kai_user_id=?)", id, kaiUserId);
        jdbcTemplate.update("DELETE FROM kai_kb_base WHERE id=? AND kai_user_id=?", id, kaiUserId);
    }

    public void toggleBase(Long kaiUserId, Long id, boolean enabled) {
        jdbcTemplate.update("UPDATE kai_kb_base SET enabled=? WHERE id=? AND kai_user_id=?", enabled ? 1 : 0, id, kaiUserId);
    }

    // ---------------- 文档 ----------------

    public List<Map<String, Object>> listDocs(Long kaiUserId, Long baseId) {
        requireBaseOwner(kaiUserId, baseId);
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query("SELECT id, file_name, file_size, status, chunk_count, error, created_at FROM kai_kb_doc "
                        + "WHERE base_id = ? AND kai_user_id = ? ORDER BY id DESC", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("fileName", rs.getString("file_name"));
            m.put("fileSize", rs.getLong("file_size"));
            m.put("status", rs.getString("status"));
            m.put("chunkCount", rs.getInt("chunk_count"));
            m.put("error", rs.getString("error"));
            m.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
            out.add(m);
        }, baseId, kaiUserId);
        return out;
    }

    /** 上传文档：提取文本 → 分块落库 → 尽力向量化。解析失败标 FAILED（可删可重传），向量化失败仍就绪（关键词兜底）。 */
    public Map<String, Object> uploadDoc(Long kaiUserId, Long baseId, MultipartFile file) {
        requireBaseOwner(kaiUserId, baseId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的文件");
        String name = file.getOriginalFilename() == null ? "未命名" : file.getOriginalFilename();
        String text;
        try {
            text = chatFileService.extract(name, file.getBytes());
        } catch (Exception e) {
            throw new IllegalArgumentException("文件解析失败：" + e.getMessage());
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("没能从文件中提取出文字——扫描件/图片类文件暂不支持，请改用文字版");
        }
        jdbcTemplate.update("INSERT INTO kai_kb_doc (kai_user_id, base_id, file_name, file_size, status) VALUES (?,?,?,?, 'PROCESSING')",
                kaiUserId, baseId, name, file.getSize());
        Long docId = jdbcTemplate.queryForObject(
                "SELECT id FROM kai_kb_doc WHERE kai_user_id=? ORDER BY id DESC LIMIT 1", Long.class, kaiUserId);
        try {
            List<String> chunks = chunkText(text);
            if (chunks.isEmpty()) throw new IllegalArgumentException("文档内容过短，无法分块");
            for (int i = 0; i < chunks.size(); i++) {
                jdbcTemplate.update("INSERT INTO kai_kb_chunk (kai_user_id, doc_id, base_id, seq, content) VALUES (?,?,?,?,?)",
                        kaiUserId, docId, baseId, i, chunks.get(i));
            }
            // 尽力向量化（失败不影响就绪，走关键词兜底）
            try {
                embedChunksOfDoc(kaiUserId, docId, chunks);
            } catch (Exception e) {
                log.info("[小楷独立版] 知识库向量化不可用（自动走关键词检索）: {}", e.getMessage());
            }
            jdbcTemplate.update("UPDATE kai_kb_doc SET status='READY', chunk_count=? WHERE id=?", chunks.size(), docId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", docId);
            out.put("fileName", name);
            out.put("chunkCount", chunks.size());
            out.put("status", "READY");
            return out;
        } catch (IllegalArgumentException e) {
            markFailed(docId, e.getMessage());
            throw e;
        } catch (Exception e) {
            markFailed(docId, e.getMessage());
            throw new IllegalArgumentException("文档处理失败：" + e.getMessage());
        }
    }

    public void deleteDoc(Long kaiUserId, Long id) {
        jdbcTemplate.update("DELETE FROM kai_kb_chunk WHERE doc_id=? AND kai_user_id=?", id, kaiUserId);
        jdbcTemplate.update("DELETE FROM kai_kb_doc WHERE id=? AND kai_user_id=?", id, kaiUserId);
    }

    private void markFailed(Long docId, String error) {
        try {
            jdbcTemplate.update("UPDATE kai_kb_doc SET status='FAILED', error=? WHERE id=?",
                    error == null ? "" : error.substring(0, Math.min(480, error.length())), docId);
        } catch (Exception ignore) { }
    }

    private void requireBaseOwner(Long kaiUserId, Long baseId) {
        Integer owner = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kai_kb_base WHERE id=? AND kai_user_id=?", Integer.class, baseId, kaiUserId);
        if (owner == null || owner == 0) throw new IllegalArgumentException("知识库不存在或无权操作");
    }

    // ---------------- 检索 ----------------

    /** 检索测试（界面用）：返回命中片段清单。baseId 可空=全部启用中的库。 */
    public Map<String, Object> search(Long kaiUserId, String query, Integer topK, Long baseId) {
        List<Map<String, Object>> hits = retrieve(kaiUserId, query, topK == null ? 6 : Math.min(20, topK), baseId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hits", hits);
        return out;
    }

    /**
     * 对话注入用：检索与提问相关的资料分块，拼成参考资料块。
     * 无启用知识库/无命中返回 null——对话行为保持与升级前完全一致。
     */
    public String retrieveContext(Long kaiUserId, String query, Long baseId) {
        List<Map<String, Object>> hits = retrieve(kaiUserId, query, 6, baseId);
        if (hits.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("【参考资料（来自用户知识库）】回答时可引用以下资料；资料里没有的不要编造：\n");
        int total = 0;
        for (Map<String, Object> h : hits) {
            String piece = "[" + h.get("docName") + "]\n" + h.get("content");
            if (total + piece.length() > CONTEXT_CHAR_CAP) break;
            sb.append(piece).append("\n---\n");
            total += piece.length();
        }
        return total == 0 ? null : sb.toString();
    }

    private List<Map<String, Object>> retrieve(Long kaiUserId, String query, int topK, Long baseId) {
        if (query == null || query.isBlank()) return new ArrayList<>();
        String baseFilter = baseId == null ? "" : " AND c.base_id = " + baseId + " ";
        Integer hasChunks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kai_kb_chunk c JOIN kai_kb_base b ON c.base_id=b.id "
                        + "WHERE c.kai_user_id=? AND b.enabled=1" + baseFilter, Integer.class, kaiUserId);
        if (hasChunks == null || hasChunks == 0) return new ArrayList<>();

        List<long[]> ids = new ArrayList<>();          // chunkId, docId
        List<String> contents = new ArrayList<>();
        List<String> embeddings = new ArrayList<>();
        Map<Long, String> docNames = new HashMap<>();
        jdbcTemplate.query("""
                SELECT c.id, c.doc_id, c.content, c.embedding, d.file_name
                FROM kai_kb_chunk c
                JOIN kai_kb_base b ON c.base_id = b.id AND b.enabled = 1
                LEFT JOIN kai_kb_doc d ON d.id = c.doc_id
                WHERE c.kai_user_id = ?%s
                ORDER BY c.id DESC LIMIT %d
                """.formatted(baseFilter, RETRIEVE_CHUNK_CAP), rs -> {
            long cid = rs.getLong("id");
            long did = rs.getLong("doc_id");
            ids.add(new long[]{cid, did});
            contents.add(rs.getString("content"));
            embeddings.add(rs.getString("embedding"));
            String fn = rs.getString("file_name");
            docNames.computeIfAbsent(did, k -> fn == null ? "未命名文档" : fn);
        }, kaiUserId);

        // 打分：向量优先，任一环节不可用自动关键词兜底
        double[] scores = tryVectorScores(kaiUserId, query, embeddings);
        double threshold;
        if (scores == null) {
            scores = keywordScores(query, contents);
            threshold = 0.0;
        } else {
            threshold = VECTOR_MIN_SCORE;
        }

        Integer[] order = new Integer[contents.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        final double[] finalScores = scores;
        Arrays.sort(order, (a, b) -> Double.compare(finalScores[b], finalScores[a]));
        List<Map<String, Object>> hits = new ArrayList<>();
        int taken = 0;
        for (int i = 0; i < order.length && taken < topK; i++) {
            int idx = order[i];
            double s = scores[idx];
            if (s <= threshold) break;
            Map<String, Object> h = new LinkedHashMap<>();
            long did = ids.get(idx)[1];
            h.put("docName", docNames.getOrDefault(did, "未命名文档"));
            String c = contents.get(idx);
            h.put("content", c.length() > 500 ? c.substring(0, 500) + "…" : c);
            h.put("score", Math.round(s * 1000.0) / 1000.0);
            hits.add(h);
            taken++;
        }
        return hits;
    }

    /** 关键词打分：整句 + 词元 + 中文二元组，命中次数求和。 */
    private double[] keywordScores(String query, List<String> contents) {
        List<String> terms = buildTerms(query);
        double[] scores = new double[contents.size()];
        for (int i = 0; i < contents.size(); i++) {
            String c = contents.get(i).toLowerCase();
            int s = 0;
            for (String t : terms) {
                String tl = t.toLowerCase();
                int idx = 0;
                while ((idx = c.indexOf(tl, idx)) >= 0) { s++; idx += tl.length(); }
            }
            scores[i] = s;
        }
        return scores;
    }

    /** 向量打分：查询向量与各分块余弦；任一环节不可用返回 null（走关键词兜底）。 */
    private double[] tryVectorScores(Long kaiUserId, String query, List<String> embeddings) {
        try {
            boolean anyVec = false;
            for (String e : embeddings) if (e != null && !e.isBlank()) { anyVec = true; break; }
            if (!anyVec) return null;
            List<float[]> vecs = new ArrayList<>();
            for (String e : embeddings) {
                if (e == null || e.isBlank()) { vecs.add(null); continue; }
                try {
                    JsonNode arr = mapper.readTree(e);
                    float[] v = new float[arr.size()];
                    for (int i = 0; i < arr.size(); i++) v[i] = (float) arr.get(i).asDouble();
                    vecs.add(v);
                } catch (Exception ex) { vecs.add(null); }
            }
            List<float[]> q = callEmbeddings(kaiUserId, List.of(truncate(query, EMBED_CHAR_CAP)));
            if (q == null || q.isEmpty() || q.get(0) == null) return null;
            double[] out = new double[embeddings.size()];
            for (int i = 0; i < embeddings.size(); i++) {
                out[i] = vecs.get(i) == null ? -1 : cosine(q.get(0), vecs.get(i));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- 向量化 ----------------

    private void embedChunksOfDoc(Long kaiUserId, Long docId, List<String> chunks) {
        ProviderInfo provider = resolveProvider(kaiUserId);
        if (provider == null) return; // 无可用服务商 → 保持无向量（关键词兜底）
        int batch = 16;
        for (int start = 0; start < Math.min(chunks.size(), MAX_CHUNKS_PER_DOC); start += batch) {
            List<String> part = new ArrayList<>();
            for (int i = start; i < Math.min(start + batch, chunks.size()); i++) {
                part.add(truncate(chunks.get(i), EMBED_CHAR_CAP));
            }
            List<float[]> vecs = callEmbeddingsOn(provider, part);
            if (vecs == null) throw new IllegalStateException("向量接口调用失败");
            for (int i = 0; i < vecs.size(); i++) {
                float[] v = vecs.get(i);
                if (v == null) continue;
                StringBuilder sb = new StringBuilder("[");
                for (int j = 0; j < v.length; j++) { if (j > 0) sb.append(','); sb.append(v[j]); }
                sb.append(']');
                jdbcTemplate.update("UPDATE kai_kb_chunk SET embedding=? WHERE doc_id=? AND seq=?",
                        sb.toString(), docId, start + i);
            }
        }
    }

    private ProviderInfo resolveProvider(Long kaiUserId) {
        ProviderInfo cached = embedProviderCache.get(kaiUserId);
        if (cached != null) return cached;
        List<Map<String, Object>> providers = new ArrayList<>();
        jdbcTemplate.query("SELECT id, base_url, api_key_enc, models FROM kai_model_provider WHERE kai_user_id=? AND enabled=1 ORDER BY id", rs -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", rs.getLong("id"));
            p.put("baseUrl", rs.getString("base_url"));
            p.put("apiKeyEnc", rs.getString("api_key_enc"));
            p.put("models", rs.getString("models"));
            providers.add(p);
        }, kaiUserId);
        for (Map<String, Object> p : providers) {
            String baseUrl = str(p.get("baseUrl")).replaceAll("/+$", "");
            String models = str(p.get("models"));
            String model = models.isBlank() ? "" : models.split("[,，]")[0].trim();
            if (baseUrl.isBlank() || model.isBlank()) continue;
            String key = decryptKey(String.valueOf(p.get("apiKeyEnc")));
            ProviderInfo info = new ProviderInfo(baseUrl, key, model);
            // 探活：对一句话请求向量，成功才缓存
            List<float[]> probe = callEmbeddingsOn(info, List.of("连通性测试"));
            if (probe != null && !probe.isEmpty() && probe.get(0) != null) {
                embedProviderCache.put(kaiUserId, info);
                return info;
            }
        }
        return null;
    }

    private List<float[]> callEmbeddings(Long kaiUserId, List<String> inputs) {
        ProviderInfo p = resolveProvider(kaiUserId);
        return p == null ? null : callEmbeddingsOn(p, inputs);
    }

    /** 调 OpenAI 兼容 /embeddings；失败返回 null（不抛错，调用方自行兜底）。 */
    private List<float[]> callEmbeddingsOn(ProviderInfo p, List<String> inputs) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", p.model());
            body.put("input", inputs);
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(p.baseUrl() + "/embeddings"))
                    .timeout(Duration.ofSeconds(EMBED_TIMEOUT_S))
                    .header("Content-Type", "application/json");
            if (p.apiKey() != null && !p.apiKey().isBlank()) rb.header("Authorization", "Bearer " + p.apiKey());
            rb.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
            HttpResponse<String> resp = HttpClient.newHttpClient().send(rb.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) return null;
            JsonNode data = mapper.readTree(resp.body()).path("data");
            if (!data.isArray() || data.isEmpty()) return null;
            List<float[]> out = new ArrayList<>();
            for (JsonNode d : data) {
                JsonNode emb = d.path("embedding");
                if (!emb.isArray()) { out.add(null); continue; }
                float[] v = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) v[i] = (float) emb.get(i).asDouble();
                out.add(v);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private String decryptKey(String enc) {
        if (enc == null || enc.isBlank()) return "";
        try {
            return cryptoService.decrypt(enc);
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------- 分块与工具 ----------------

    /** 分块：按空行分段 → 合并到目标长度；超长单段硬切；总量封顶。 */
    static List<String> chunkText(String text) {
        List<String> out = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        StringBuilder buf = new StringBuilder();
        for (String para : normalized.split("\n")) {
            String p = para.strip();
            if (p.isEmpty()) {
                if (buf.length() >= CHUNK_TARGET / 3) { out.add(flush(buf)); }
                continue;
            }
            if (p.length() > CHUNK_HARD_CAP) {
                if (buf.length() > 0) out.add(flush(buf));
                for (int i = 0; i < p.length(); i += CHUNK_TARGET) {
                    out.add(p.substring(i, Math.min(p.length(), i + CHUNK_TARGET)));
                    if (out.size() >= MAX_CHUNKS_PER_DOC) return out;
                }
                continue;
            }
            if (buf.length() + p.length() + 1 > CHUNK_TARGET) out.add(flush(buf));
            if (buf.length() > 0) buf.append('\n');
            buf.append(p);
            if (out.size() >= MAX_CHUNKS_PER_DOC) return out;
        }
        if (buf.length() > 0) out.add(flush(buf));
        return out;
    }

    private static String flush(StringBuilder buf) {
        String s = buf.toString();
        buf.setLength(0);
        return s;
    }

    /** 关键词词元：空白/标点切词（≥2 字）+ 中文相邻二元组 + 整句（≤20 字时）。 */
    static List<String> buildTerms(String query) {
        List<String> terms = new ArrayList<>();
        String q = query.strip();
        if (q.length() <= 20) terms.add(q);
        for (String t : q.split("[\\s\\p{Punct}，。；：、！？（）【】《》\"'']+")) {
            if (t.length() >= 2 && !terms.contains(t)) terms.add(t);
        }
        String cjk = q.replaceAll("[^\\u4e00-\\u9fa5]", "");
        for (int i = 0; i + 1 < cjk.length() && terms.size() < 40; i++) {
            String bg = cjk.substring(i, i + 2);
            if (!terms.contains(bg)) terms.add(bg);
        }
        return terms;
    }

    private static double cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) { dot += a[i] * b[i]; na += (double) a[i] * a[i]; nb += (double) b[i] * b[i]; }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
