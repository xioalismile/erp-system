package com.erp.board.custom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.board.custom.core.BoardDataAggregator;
import com.erp.board.custom.entity.CustomBoard;
import com.erp.board.custom.mapper.CustomBoardMapper;
import com.erp.common.scope.DataScopeResolver;
import com.erp.common.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义看板 / 工作台自定义卡（2026-11-05 新增）。
 *
 * <p>两种形态共用一张表：</p>
 * <ul>
 *   <li>board_type='board'：数据看板 → 自定义看板，可建多套、可共享给角色</li>
 *   <li>board_type='workbench'：工作台自定义卡，每个登录人一条记录、最多 6 张卡，
 *       与工作台 11 张固定卡共用「首页设置」的显隐/排序/折叠</li>
 * </ul>
 *
 * <p>权限：私有仅本人可见；共享按角色编码命中可见；管理员可见全部。
 * 卡片取数一律走 {@link BoardDataAggregator} 白名单，用户输入不拼表名列名。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomBoardService {

    private final CustomBoardMapper boardMapper;
    private final BoardDataAggregator aggregator;
    private final DataScopeResolver dataScopeResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_CARDS = 24;
    private static final int MAX_WORKBENCH_CARDS = 6;
    private static final int MAX_LAYOUT_LENGTH = 64 * 1024;

    // ==================== 列表 / 详情 ====================

    public Map<String, Object> list(HttpServletRequest req, String boardType, String keyword) {
        String type = normType(boardType);
        String username = SecurityUtils.getCurrentUsername(req);
        List<String> roles = SecurityUtils.getCurrentRoles(req);
        boolean admin = SecurityUtils.isAdmin(req);

        LambdaQueryWrapper<CustomBoard> qw = new LambdaQueryWrapper<>();
        qw.eq(CustomBoard::getBoardType, type);
        qw.eq(CustomBoard::getStatus, "启用");
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(CustomBoard::getBoardName, keyword.trim())
                    .or().like(CustomBoard::getBoardCode, keyword.trim()));
        }
        qw.orderByDesc(CustomBoard::getUpdatedAt);
        List<CustomBoard> all = boardMapper.selectList(qw);

        List<Map<String, Object>> records = new ArrayList<>();
        for (CustomBoard b : all) {
            if (!canView(b, username, roles, admin)) continue;
            records.add(toSummary(b, username));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", records.size());
        return out;
    }

    public Map<String, Object> detail(HttpServletRequest req, Long id) {
        CustomBoard b = requireVisible(req, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("boardId", b.getBoardId());
        out.put("boardType", b.getBoardType());
        out.put("boardCode", b.getBoardCode());
        out.put("boardName", b.getBoardName());
        out.put("shareScope", b.getShareScope());
        out.put("shareRoles", b.getShareRoles());
        out.put("remark", b.getRemark());
        out.put("createdBy", b.getCreatedBy());
        out.put("updatedAt", b.getUpdatedAt());
        out.put("cards", parseCards(b.getLayoutJson()));
        return out;
    }

    /** 逐卡取数：单卡异常隔离，失败卡返回 error 提示，不拖垮整页 */
    public Map<String, Object> data(HttpServletRequest req, Long id, String period) {
        CustomBoard b = requireVisible(req, id);
        List<Map<String, Object>> cards = parseCards(b.getLayoutJson());
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> c : cards) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.get("id"));
            item.put("type", c.get("type"));
            item.put("title", c.get("title"));
            try {
                Map<String, Object> d = aggregator.cardData(c, period, dataScopeResolver.resolve(req));
                item.put("data", d);
            } catch (Exception e) {
                item.put("error", friendly(e));
                log.warn("[自定义看板] 卡片取数失败 board={} card={}: {}", id, c.get("title"), e.getMessage());
            }
            results.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("boardId", b.getBoardId());
        out.put("boardName", b.getBoardName());
        out.put("period", period == null ? "month" : period);
        out.put("cards", results);
        return out;
    }

    // ==================== 保存 / 删除 ====================

    @Transactional
    public Map<String, Object> create(HttpServletRequest req, Map<String, Object> body) {
        String username = SecurityUtils.getCurrentUsername(req);
        String type = normType(str(body.get("boardType")));
        List<Map<String, Object>> cards = normalizeCards(body.get("cards"), type);

        CustomBoard b = new CustomBoard();
        b.setBoardType(type);
        b.setBoardCode(nextCode(type));
        b.setBoardName(strOr(body.get("boardName"), "未命名看板"));
        b.setLayoutJson(writeCards(cards));
        b.setStatus("启用");
        b.setShareScope(strOr(body.get("shareScope"), "私有"));
        b.setShareRoles(str(body.get("shareRoles")));
        b.setRemark(str(body.get("remark")));
        b.setCreatedBy(username);
        b.setCreatedAt(LocalDateTime.now());
        b.setUpdatedAt(LocalDateTime.now());
        boardMapper.insert(b);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("boardId", b.getBoardId());
        out.put("boardCode", b.getBoardCode());
        out.put("boardName", b.getBoardName());
        return out;
    }

    @Transactional
    public Map<String, Object> update(HttpServletRequest req, Long id, Map<String, Object> body) {
        CustomBoard b = requireOwnedOrAdmin(req, id);
        if (body.get("boardName") != null) b.setBoardName(strOr(body.get("boardName"), b.getBoardName()));
        if (body.get("cards") != null) b.setLayoutJson(writeCards(normalizeCards(body.get("cards"), b.getBoardType())));
        if (body.get("shareScope") != null) b.setShareScope(strOr(body.get("shareScope"), "私有"));
        if (body.get("shareRoles") != null) b.setShareRoles(str(body.get("shareRoles")));
        if (body.get("remark") != null) b.setRemark(str(body.get("remark")));
        b.setUpdatedAt(LocalDateTime.now());
        boardMapper.updateById(b);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("boardId", b.getBoardId());
        out.put("boardName", b.getBoardName());
        return out;
    }

    @Transactional
    public void delete(HttpServletRequest req, Long id) {
        CustomBoard b = requireOwnedOrAdmin(req, id);
        boardMapper.deleteById(b.getBoardId());
    }

    // ==================== 现有看板页内的卡片配置（财务/销售/仓储/经营） ====================

    /** 读取某看板页的卡片布局（每登录人每页一条；没有则空清单 = 与改造前完全一致） */
    public Map<String, Object> pageLayout(HttpServletRequest req, String pageKey) {
        return pageLayout(SecurityUtils.getCurrentUsername(req), pageKey);
    }

    /** 读取指定用户的看板页布局（AI 工具等无请求上下文场景用） */
    public Map<String, Object> pageLayout(String username, String pageKey) {
        CustomBoard b = findPage(username, pageKey);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pageKey", pageKey);
        out.put("boardId", b == null ? null : b.getBoardId());
        out.put("cards", b == null ? new ArrayList<>() : parseCards(b.getLayoutJson()));
        return out;
    }

    /** 保存某看板页的卡片布局（显隐 / 顺序 / 尺寸 / 改为自定义取数 / 新增自定义卡） */
    @Transactional
    public Map<String, Object> savePageLayout(HttpServletRequest req, String pageKey, Map<String, Object> body) {
        return savePageLayout(SecurityUtils.getCurrentUsername(req), pageKey, body);
    }

    /** 保存指定用户的看板页布局（AI 工具等无请求上下文场景用，只影响该用户） */
    @Transactional
    public Map<String, Object> savePageLayout(String username, String pageKey, Map<String, Object> body) {
        Object raw = body.get("cards");
        if (raw instanceof List<?> rawList && rawList.size() > MAX_CARDS)
            throw new IllegalArgumentException("单页自定义卡片最多 " + MAX_CARDS + " 个");
        List<Map<String, Object>> cards = normalizeCards(raw, "board");
        CustomBoard b = findPage(username, pageKey);
        if (b == null) {
            b = new CustomBoard();
            b.setBoardType("page");
            b.setPageKey(pageKey);
            b.setBoardCode("PG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            b.setBoardName(username + " · " + pageKey);
            b.setStatus("启用");
            b.setShareScope("私有");
            b.setCreatedBy(username);
            b.setCreatedAt(LocalDateTime.now());
            b.setUpdatedAt(LocalDateTime.now());
            b.setLayoutJson(writeCards(cards));
            boardMapper.insert(b);
        } else {
            b.setLayoutJson(writeCards(cards));
            b.setUpdatedAt(LocalDateTime.now());
            boardMapper.updateById(b);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pageKey", pageKey);
        out.put("cards", cards);
        return out;
    }

    private CustomBoard findPage(String username, String pageKey) {
        LambdaQueryWrapper<CustomBoard> qw = new LambdaQueryWrapper<>();
        qw.eq(CustomBoard::getBoardType, "page")
          .eq(CustomBoard::getPageKey, pageKey)
          .eq(CustomBoard::getCreatedBy, username)
          .orderByDesc(CustomBoard::getBoardId)
          .last("LIMIT 1");
        List<CustomBoard> list = boardMapper.selectList(qw);
        return list.isEmpty() ? null : list.get(0);
    }

    // ==================== 工作台自定义卡（每人一条） ====================

    /** 当前登录人的工作台自定义卡；没有则返回空清单（不自动建记录，避免污染） */
    public Map<String, Object> workbench(HttpServletRequest req) {
        String username = SecurityUtils.getCurrentUsername(req);
        CustomBoard b = findWorkbench(username);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("boardId", b == null ? null : b.getBoardId());
        out.put("cards", b == null ? new ArrayList<>() : parseCards(b.getLayoutJson()));
        out.put("max", MAX_WORKBENCH_CARDS);
        return out;
    }

    /** 保存工作台自定义卡（首存自动建记录；只影响本人） */
    @Transactional
    public Map<String, Object> saveWorkbench(HttpServletRequest req, Map<String, Object> body) {
        String username = SecurityUtils.getCurrentUsername(req);
        // 上限校验必须在归一化之前：归一化会按上限截断，截断后再判断永远不超限（实测踩过）
        Object raw = body.get("cards");
        if (raw instanceof List<?> rawList && rawList.size() > MAX_WORKBENCH_CARDS)
            throw new IllegalArgumentException("工作台自定义卡片最多 " + MAX_WORKBENCH_CARDS + " 张，当前 " + rawList.size() + " 张");
        List<Map<String, Object>> cards = normalizeCards(raw, "workbench");

        CustomBoard b = findWorkbench(username);
        if (b == null) {
            b = new CustomBoard();
            b.setBoardType("workbench");
            b.setBoardCode(nextCode("workbench"));
            b.setBoardName(username + " 的工作台卡片");
            b.setStatus("启用");
            b.setShareScope("私有");
            b.setCreatedBy(username);
            b.setCreatedAt(LocalDateTime.now());
            b.setUpdatedAt(LocalDateTime.now());
            b.setLayoutJson(writeCards(cards));
            boardMapper.insert(b);
        } else {
            b.setLayoutJson(writeCards(cards));
            b.setUpdatedAt(LocalDateTime.now());
            boardMapper.updateById(b);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("boardId", b.getBoardId());
        out.put("cards", cards);
        return out;
    }

    // ==================== 设计器预览 ====================

    /** 设计器单卡预览（不落库）：用于公式实时预览与图表预览 */
    public Map<String, Object> preview(Map<String, Object> card, String period, HttpServletRequest req) {
        return aggregator.cardData(card, period, dataScopeResolver.resolve(req));
    }

    /** 卡片下钻：卡片数字/图表点 → 单据级明细清单（同一口径、同一数据权限） */
    public Map<String, Object> drill(HttpServletRequest req, Map<String, Object> card,
                                     String period, String dimLabel, Integer limit, Integer itemIndex) {
        return aggregator.drill(card, period, dimLabel, limit, itemIndex, dataScopeResolver.resolve(req));
    }

    public List<Map<String, Object>> sources() {
        return aggregator.listSources();
    }

    public List<Map<String, Object>> fields(String source) {
        return aggregator.listFields(source);
    }

    /** 可关联单据清单（多级关联） */
    public List<Map<String, Object>> joins(String source) {
        return aggregator.listJoins(source);
    }

    // ==================== 内部 ====================

    private CustomBoard findWorkbench(String username) {
        LambdaQueryWrapper<CustomBoard> qw = new LambdaQueryWrapper<>();
        qw.eq(CustomBoard::getBoardType, "workbench")
          .eq(CustomBoard::getCreatedBy, username)
          .orderByDesc(CustomBoard::getBoardId)
          .last("LIMIT 1");
        List<CustomBoard> list = boardMapper.selectList(qw);
        return list.isEmpty() ? null : list.get(0);
    }

    private CustomBoard requireVisible(HttpServletRequest req, Long id) {
        CustomBoard b = boardMapper.selectById(id);
        if (b == null) throw new IllegalArgumentException("看板不存在或已删除");
        String username = SecurityUtils.getCurrentUsername(req);
        List<String> roles = SecurityUtils.getCurrentRoles(req);
        boolean admin = SecurityUtils.isAdmin(req);
        if (!canView(b, username, roles, admin)) throw new IllegalArgumentException("无权访问该看板");
        return b;
    }

    private CustomBoard requireOwnedOrAdmin(HttpServletRequest req, Long id) {
        CustomBoard b = boardMapper.selectById(id);
        if (b == null) throw new IllegalArgumentException("看板不存在或已删除");
        String username = SecurityUtils.getCurrentUsername(req);
        if (!SecurityUtils.isAdmin(req) && !username.equals(b.getCreatedBy()))
            throw new IllegalArgumentException("只有创建者或管理员可以修改/删除该看板");
        return b;
    }

    private boolean canView(CustomBoard b, String username, List<String> roles, boolean admin) {
        if (admin) return true;
        if (username != null && username.equals(b.getCreatedBy())) return true;
        if (!"共享".equals(b.getShareScope())) return false;
        String rs = b.getShareRoles();
        if (rs == null || rs.isBlank()) return true;   // 共享但未指定角色 = 全员可见
        for (String r : rs.split(",")) {
            String code = r.trim();
            if (code.isEmpty()) continue;
            if (roles != null && roles.contains(code)) return true;
        }
        return false;
    }

    private Map<String, Object> toSummary(CustomBoard b, String username) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("boardId", b.getBoardId());
        m.put("boardCode", b.getBoardCode());
        m.put("boardName", b.getBoardName());
        m.put("shareScope", b.getShareScope());
        m.put("shareRoles", b.getShareRoles());
        m.put("cardCount", parseCards(b.getLayoutJson()).size());
        m.put("mine", username != null && username.equals(b.getCreatedBy()));
        m.put("createdBy", b.getCreatedBy());
        m.put("updatedAt", b.getUpdatedAt());
        return m;
    }

    private List<Map<String, Object>> parseCards(String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) return new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(layoutJson);
            JsonNode cards = root.get("cards");
            if (cards == null || !cards.isArray()) return new ArrayList<>();
            return objectMapper.convertValue(cards, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("[自定义看板] 布局解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String writeCards(List<Map<String, Object>> cards) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("cards", cards);
            String json = objectMapper.writeValueAsString(root);
            if (json.length() > MAX_LAYOUT_LENGTH) throw new IllegalArgumentException("看板配置过大，请减少卡片数量");
            return json;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("看板配置保存失败: " + e.getMessage());
        }
    }

    /** 卡片结构归一：缺省值补齐 + 上限校验（前端不传也不会崩） */
    private List<Map<String, Object>> normalizeCards(Object raw, String boardType) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        int max = "workbench".equals(boardType) ? MAX_WORKBENCH_CARDS : MAX_CARDS;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) c.put(String.valueOf(e.getKey()), e.getValue());
            if (c.get("id") == null) c.put("id", "c" + System.nanoTime());
            // 自定义卡片必须有 type；现有看板页里的原有区块只需 builtin 标记（只记显隐/尺寸/顺序/是否改用自己的取数）
            if (c.get("type") == null && !Boolean.TRUE.equals(c.get("builtin"))) continue;
            c.putIfAbsent("title", "未命名卡片");
            c.putIfAbsent("size", 6);
            out.add(c);
            if (out.size() >= max) break;
        }
        return out;
    }

    private String nextCode(String type) {
        String prefix = "workbench".equals(type) ? "WB" : "CB";
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private static String normType(String t) {
        return "workbench".equalsIgnoreCase(t == null ? "" : t.trim()) ? "workbench" : "board";
    }

    private static String friendly(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) return "数据暂时无法获取";
        if (msg.contains("数据源未注册")) return "该数据源已下线";
        if (msg.contains("字段不是数值型")) return "所选字段不是数值型，请换一个";
        if (msg.contains("字段不存在")) return "所选字段已不存在";
        return "数据暂时无法获取";
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String strOr(Object o, String def) {
        String s = str(o);
        return s == null || s.isBlank() ? def : s;
    }
}
