package com.erp.report.custom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.util.SecurityUtils;
import com.erp.report.custom.core.ReportConfig;
import com.erp.report.custom.entity.CustomDashboard;
import com.erp.report.custom.entity.CustomReport;
import com.erp.report.custom.mapper.CustomDashboardMapper;
import com.erp.report.custom.service.CustomDashboardService;
import com.erp.report.custom.service.CustomReportService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自定义仪表盘服务实现
 *
 * 卡片取数口径：
 * - 汇总模式报表：page=1/size=500（与运行页图表一致），含整体合计 totals
 * - 明细模式报表：page=1/size=10 迷你表格，含总行数
 * - 单卡片异常（报表删除/停用/数据源失效/配置非法）try-catch 隔离，卡片返回 error 提示
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomDashboardServiceImpl implements CustomDashboardService {

    private final CustomDashboardMapper dashboardMapper;
    private final CustomReportService customReportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_CARDS = 8;
    private static final int MAX_LAYOUT_JSON_LENGTH = 8 * 1024;

    /** 仪表盘布局结构 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DashboardLayout {
        public List<Card> cards;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Card {
        public Long reportId;
        public String title;
    }

    // ==================== 列表 ====================

    @Override
    public Map<String, Object> listMyDashboards(HttpServletRequest req, String keyword, int page, int size) {
        String username = SecurityUtils.getCurrentUsername(req);
        List<String> roles = SecurityUtils.getCurrentRoles(req);
        boolean admin = SecurityUtils.isAdmin(req);

        LambdaQueryWrapper<CustomDashboard> qw = new LambdaQueryWrapper<>();
        if (!admin) {
            qw.and(w -> w.eq(CustomDashboard::getCreatedBy, username)
                    .or().eq(CustomDashboard::getShareScope, "共享"));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            qw.and(w -> w.like(CustomDashboard::getDashName, keyword.trim())
                    .or().like(CustomDashboard::getDashCode, keyword.trim()));
        }
        qw.orderByDesc(CustomDashboard::getCreatedAt);
        List<CustomDashboard> all = dashboardMapper.selectList(qw);

        // 共享仪表盘需角色命中才可见（与自定义报表口径一致）
        List<CustomDashboard> visible = all.stream()
                .filter(d -> canView(d, username, roles, admin))
                .collect(Collectors.toList());

        int total = visible.size();
        int fromIndex = Math.max(0, (page - 1) * size);
        int toIndex = Math.min(total, fromIndex + size);
        List<Map<String, Object>> records = fromIndex >= total ? new ArrayList<>() :
                visible.subList(fromIndex, toIndex).stream().map(this::toSummary).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    // ==================== CRUD ====================

    @Override
    public Map<String, Object> create(HttpServletRequest req, CustomDashboard dashboard) {
        validateForSave(req, dashboard);
        dashboard.setDashId(null);
        dashboard.setDashCode(generateDashCode());
        dashboard.setCreatedBy(SecurityUtils.getCurrentUsername(req));
        dashboard.setCreatedAt(LocalDateTime.now());
        dashboard.setUpdatedAt(LocalDateTime.now());
        if (dashboard.getStatus() == null || dashboard.getStatus().isEmpty()) dashboard.setStatus("启用");
        if (dashboard.getShareScope() == null || dashboard.getShareScope().isEmpty()) dashboard.setShareScope("私有");
        if (!"共享".equals(dashboard.getShareScope())) dashboard.setShareRoles(null);
        dashboardMapper.insert(dashboard);
        return toDetail(dashboard);
    }

    @Override
    public Map<String, Object> update(HttpServletRequest req, Long id, CustomDashboard dashboard) {
        CustomDashboard exist = dashboardMapper.selectById(id);
        if (exist == null) throw new IllegalArgumentException("仪表盘不存在");
        requireOwner(req, exist);

        dashboard.setDashId(id);
        dashboard.setDashCode(exist.getDashCode());
        dashboard.setCreatedBy(exist.getCreatedBy());
        dashboard.setCreatedAt(exist.getCreatedAt());
        dashboard.setUpdatedAt(LocalDateTime.now());
        if (dashboard.getStatus() == null || dashboard.getStatus().isEmpty()) dashboard.setStatus(exist.getStatus());
        if (dashboard.getShareScope() == null || dashboard.getShareScope().isEmpty()) dashboard.setShareScope(exist.getShareScope());
        if (!"共享".equals(dashboard.getShareScope())) dashboard.setShareRoles(null);
        validateForSave(req, dashboard);
        dashboardMapper.updateById(dashboard);
        return toDetail(dashboardMapper.selectById(id));
    }

    @Override
    public void delete(HttpServletRequest req, Long id) {
        CustomDashboard exist = dashboardMapper.selectById(id);
        if (exist == null) throw new IllegalArgumentException("仪表盘不存在");
        requireOwner(req, exist);
        dashboardMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> detail(HttpServletRequest req, Long id) {
        CustomDashboard exist = requireVisible(req, id);
        Map<String, Object> m = toDetail(exist);
        m.put("cards", resolveCards(req, exist, false));
        return m;
    }

    @Override
    public Map<String, Object> data(HttpServletRequest req, Long id) {
        CustomDashboard exist = requireVisible(req, id);
        if (!"启用".equals(exist.getStatus())) {
            throw new IllegalArgumentException("仪表盘已停用");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dashId", exist.getDashId());
        result.put("dashName", exist.getDashName());
        result.put("cards", resolveCards(req, exist, true));
        return result;
    }

    // ==================== 内部方法 ====================

    /** 卡片解析：withData=false 仅解析信息；true 逐卡片执行取数（异常隔离） */
    private List<Map<String, Object>> resolveCards(HttpServletRequest req, CustomDashboard dash, boolean withData) {
        List<Card> cards = parseLayout(dash.getLayoutJson());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Card card : cards) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("reportId", card.reportId);
            try {
                CustomReport report = withData
                        ? customReportService.requireRunnable(req, card.reportId)
                        : customReportService.getReportIfVisible(req, card.reportId);
                ReportConfig config = parseReportConfig(report.getConfigJson());
                c.put("title", (card.title != null && !card.title.trim().isEmpty())
                        ? card.title.trim() : report.getReportName());
                c.put("reportName", report.getReportName());
                c.put("mode", config != null && config.isSummary() ? "summary" : "detail");
                c.put("chartType", (config != null && config.chart != null) ? config.chart.type : null);
                c.put("reportStatus", report.getStatus());
                if (withData) {
                    boolean summary = "summary".equals(c.get("mode"));
                    // 汇总：前500条分组数据（与运行页图表一致）；明细：前10行迷你表格
                    Map<String, Object> run = customReportService.run(req, card.reportId,
                            null, null, null, null, 1, summary ? 500 : 10);
                    c.put("columns", run.get("columns"));
                    c.put("records", run.get("records"));
                    c.put("total", run.get("total"));
                    if (summary) c.put("totals", run.get("totals"));
                }
            } catch (Exception e) {
                c.put("title", (card.title != null && !card.title.trim().isEmpty())
                        ? card.title.trim() : ("报表 " + card.reportId));
                c.put("error", e.getMessage() != null ? e.getMessage() : "报表不可用");
            }
            list.add(c);
        }
        return list;
    }

    /** 保存前校验：名称 + 布局结构（1~8卡、报表存在且当前用户可见、卡片不重复） */
    private void validateForSave(HttpServletRequest req, CustomDashboard dashboard) {
        if (dashboard.getDashName() == null || dashboard.getDashName().trim().isEmpty()) {
            throw new IllegalArgumentException("仪表盘名称不能为空");
        }
        if (dashboard.getDashName().length() > 100) {
            throw new IllegalArgumentException("仪表盘名称过长");
        }
        if (dashboard.getLayoutJson() != null && dashboard.getLayoutJson().length() > MAX_LAYOUT_JSON_LENGTH) {
            throw new IllegalArgumentException("仪表盘配置超出大小限制");
        }
        List<Card> cards = parseLayout(dashboard.getLayoutJson());
        if (cards.isEmpty()) {
            throw new IllegalArgumentException("请至少添加一张报表卡片");
        }
        if (cards.size() > MAX_CARDS) {
            throw new IllegalArgumentException("报表卡片超出上限 " + MAX_CARDS);
        }
        Set<Long> used = new HashSet<>();
        for (Card card : cards) {
            if (card.reportId == null) {
                throw new IllegalArgumentException("卡片配置不完整（缺少报表）");
            }
            if (!used.add(card.reportId)) {
                throw new IllegalArgumentException("卡片引用了重复的报表");
            }
            // 保存即校验可见性：防引用他人私有报表（后续被取消共享时查看端逐卡片隔离提示）
            customReportService.getReportIfVisible(req, card.reportId);
        }
    }

    private List<Card> parseLayout(String layoutJson) {
        if (layoutJson == null || layoutJson.trim().isEmpty()) {
            throw new IllegalArgumentException("仪表盘卡片配置不能为空");
        }
        DashboardLayout layout;
        try {
            layout = objectMapper.readValue(layoutJson, DashboardLayout.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("仪表盘卡片配置格式错误");
        }
        if (layout == null || layout.cards == null) {
            throw new IllegalArgumentException("仪表盘卡片配置不能为空");
        }
        return layout.cards;
    }

    private ReportConfig parseReportConfig(String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(configJson, ReportConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    private CustomDashboard requireVisible(HttpServletRequest req, Long id) {
        CustomDashboard dash = dashboardMapper.selectById(id);
        if (dash == null) throw new IllegalArgumentException("仪表盘不存在");
        if (!canView(dash, SecurityUtils.getCurrentUsername(req), SecurityUtils.getCurrentRoles(req), SecurityUtils.isAdmin(req))) {
            throw new IllegalArgumentException("无权访问该仪表盘");
        }
        return dash;
    }

    private boolean canView(CustomDashboard d, String username, List<String> roles, boolean admin) {
        if (admin) return true;
        if (username != null && username.equals(d.getCreatedBy())) return true;
        if ("共享".equals(d.getShareScope()) && d.getShareRoles() != null && roles != null && !roles.isEmpty()) {
            Set<String> mine = new HashSet<>(roles);
            return Arrays.stream(d.getShareRoles().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).anyMatch(mine::contains);
        }
        return false;
    }

    private void requireOwner(HttpServletRequest req, CustomDashboard dash) {
        if (SecurityUtils.isAdmin(req)) return;
        String username = SecurityUtils.getCurrentUsername(req);
        if (username == null || !username.equals(dash.getCreatedBy())) {
            throw new IllegalArgumentException("仅仪表盘创建人可修改/删除");
        }
    }

    /** CD + yyMMdd + 3位流水，冲突自增重试 */
    private String generateDashCode() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        long todayCount = dashboardMapper.selectCount(new LambdaQueryWrapper<CustomDashboard>()
                .likeRight(CustomDashboard::getDashCode, "CD" + datePart));
        int seq = (int) todayCount + 1;
        while (true) {
            String code = String.format("CD%s-%03d", datePart, seq);
            Long exists = dashboardMapper.selectCount(new LambdaQueryWrapper<CustomDashboard>()
                    .eq(CustomDashboard::getDashCode, code));
            if (exists == null || exists == 0) return code;
            seq++;
        }
    }

    private Map<String, Object> toSummary(CustomDashboard d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dashId", d.getDashId());
        m.put("dashCode", d.getDashCode());
        m.put("dashName", d.getDashName());
        int cardCount = 0;
        try {
            cardCount = parseLayout(d.getLayoutJson()).size();
        } catch (Exception ignored) {
        }
        m.put("cardCount", cardCount);
        m.put("status", d.getStatus());
        m.put("shareScope", d.getShareScope());
        m.put("shareRoles", d.getShareRoles());
        m.put("createdBy", d.getCreatedBy());
        m.put("createdAt", d.getCreatedAt());
        m.put("remark", d.getRemark());
        return m;
    }

    private Map<String, Object> toDetail(CustomDashboard d) {
        Map<String, Object> m = toSummary(d);
        m.put("layoutJson", d.getLayoutJson());
        m.put("updatedAt", d.getUpdatedAt());
        return m;
    }
}
