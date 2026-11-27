package com.erp.system.onboarding;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 开账引导自然语言解析器（2026-10-12）。
 *
 * <p>用户在向导里用一句话描述公司（如"我们做食品饮料的，大概12个人，有自己的工厂"），
 * 本解析器从中识别行业 + 人数，返回给前端用于自动定位行业卡片和规模档位。
 * 不依赖 AI，纯关键词 + 正则，毫秒级返回，失败降级为"通用兜底 + 5人"。</p>
 *
 * <p>识别逻辑：</p>
 * <ul>
 *   <li>行业：按关键词命中分，命中分最高的行业胜出；同分时取先定义的行业。</li>
 *   <li>人数：匹配"数字+人/名/位/员工/团队"等模式，取第一个合理值（1~9999）。</li>
 * </ul>
 */
@Component
public class OnboardingNlParser {

    /** 行业关键词表（code → 关键词数组，顺序即优先级） */
    private static final Map<String, String[]> INDUSTRY_KEYWORDS = new LinkedHashMap<>();
    static {
        INDUSTRY_KEYWORDS.put("FOOD", new String[]{"食品", "饮料", "餐饮", "零食", "粮油", "乳品", "奶", "烘焙", "速冻", "农副产品", "生鲜", "调味", "中央厨房", "冷链", "快消", "溯源"});
        INDUSTRY_KEYWORDS.put("APPAREL", new String[]{"服装", "纺织", "面料", "鞋帽", "家纺", "内衣", "童装", "女装", "男装", "针织", "丝绸", "裁剪", "缝制", "印花", "绣花"});
        INDUSTRY_KEYWORDS.put("ELECTRONICS", new String[]{"电子", "电器", "3c", "数码", "芯片", "元器件", "家电", "手机", "通讯", "pcb", "电路", "smt", "贴片", "半导体"});
        INDUSTRY_KEYWORDS.put("MANUFACTURE", new String[]{"制造", "生产", "工厂", "加工", "组装", "机械加工", "工业", "制造厂", "装配", "车间", "来料加工"});
        INDUSTRY_KEYWORDS.put("HARDWARE", new String[]{"五金", "机械", "紧固件", "工具", "钢材", "金属", "模具", "标准件", "机床", "钣金", "焊接", "冲压"});
        INDUSTRY_KEYWORDS.put("CHEMICAL", new String[]{"化工", "涂料", "日化", "化学", "清洗", "溶剂", "树脂", "颜料", "塑料", "橡胶", "危化", "试剂"});
        INDUSTRY_KEYWORDS.put("FURNITURE", new String[]{"家具", "家居", "家装", "建材", "板材", "沙发", "床垫", "衣柜", "桌椅", "定制家具", "全屋定制"});
        INDUSTRY_KEYWORDS.put("ECOMMERCE", new String[]{"电商", "淘宝", "天猫", "京东", "拼多多", "抖音", "直播", "带货", "社群", "团购", "跨境", "网店", "店铺", "商城", "选品"});
        INDUSTRY_KEYWORDS.put("TRADE", new String[]{"贸易", "批发", "经销", "代理", "分销", "商贸", "二批", "渠道", "经销商", "商贸公司", "供应链"});
        INDUSTRY_KEYWORDS.put("SERVICE", new String[]{"服务", "咨询", "设计", "维修", "家政", "劳务", "物业", "培训", "物流服务", "外包", "运维", "安装"});
        INDUSTRY_KEYWORDS.put("COSMETICS", new String[]{"化妆品", "美妆", "护肤", "个护", "彩妆", "香水", "面膜", "口红", "面霜", "精华", "洗护", "美容", "日化", "oem化妆品", "代加工化妆品"});
        INDUSTRY_KEYWORDS.put("GENERAL", new String[]{"通用", "综合", "其他", "科技", "网络", "软件", "互联网"});
    }

    /** 人数匹配：数字 + 人/名/位/个员工/团队 */
    private static final Pattern COUNT_PATTERN = Pattern.compile(
            "(\\d{1,4})\\s*(?:多)?\\s*(?:人|名|位|个(?:员工)?|员工|团队成员|号人)");

    /** 纯数字兜底（用户只写"12"） */
    private static final Pattern PURE_NUMBER = Pattern.compile("\\b(\\d{1,4})\\b");

    private final IndustryCatalog catalog;

    public OnboardingNlParser(IndustryCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 解析自然语言描述。
     *
     * @param text 用户输入的一句话
     * @return {industryCode, industryName, employeeCount, summary, matchedKeywords, confidence}
     */
    public Map<String, Object> parse(String text) {
        String raw = text == null ? "" : text.trim();
        Map<String, Object> result = new LinkedHashMap<>();

        if (raw.isEmpty()) {
            result.put("industryCode", "GENERAL");
            result.put("industryName", "通用兜底");
            result.put("employeeCount", 5);
            result.put("summary", "请描述一下您的公司，例如：做食品饮料的，12个人");
            result.put("matchedKeywords", List.of());
            result.put("confidence", 0);
            return result;
        }

        // 1. 行业识别
        IndustryMatch best = matchIndustry(raw);
        String industryCode = best.code;
        IndustryCatalog.Industry ind = catalog.ofOrDefault(industryCode);

        // 2. 人数识别
        Integer count = extractCount(raw);
        if (count == null) count = 5;

        // 3. 生成摘要
        String summary = buildSummary(ind.name, count, best.keywords, raw);

        result.put("industryCode", ind.code);
        result.put("industryName", ind.name);
        result.put("employeeCount", count);
        result.put("summary", summary);
        result.put("matchedKeywords", best.keywords);
        result.put("confidence", best.score);
        return result;
    }

    private IndustryMatch matchIndustry(String text) {
        String lower = text.toLowerCase();
        String bestCode = "GENERAL";
        int bestScore = 0;
        List<String> bestKeywords = new ArrayList<>();

        for (Map.Entry<String, String[]> e : INDUSTRY_KEYWORDS.entrySet()) {
            int score = 0;
            List<String> hit = new ArrayList<>();
            for (String kw : e.getValue()) {
                if (lower.contains(kw.toLowerCase())) {
                    score++;
                    hit.add(kw);
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestCode = e.getKey();
                bestKeywords = hit;
            }
        }
        return new IndustryMatch(bestCode, bestScore, bestKeywords);
    }

    private Integer extractCount(String text) {
        Matcher m = COUNT_PATTERN.matcher(text);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= 9999) return n;
        }
        // 兜底：纯数字
        Matcher m2 = PURE_NUMBER.matcher(text);
        if (m2.find()) {
            int n = Integer.parseInt(m2.group(1));
            if (n >= 1 && n <= 9999) return n;
        }
        return null;
    }

    private String buildSummary(String industryName, int count, List<String> keywords, String raw) {
        StringBuilder sb = new StringBuilder();
        sb.append("我理解您是一家 ").append(count).append(" 人的「").append(industryName).append("」企业");
        if (!keywords.isEmpty()) {
            sb.append("（识别到关键词：").append(String.join("、", keywords)).append("）");
        }
        // 如果用户提到"工厂/生产/制造"，追加一句
        if (raw.contains("工厂") || raw.contains("生产") || raw.contains("制造")) {
            sb.append("，已为您包含生产相关部门与角色");
        }
        sb.append("。如果不对，您可以在下方手动调整。");
        return sb.toString();
    }

    private static class IndustryMatch {
        final String code;
        final int score;
        final List<String> keywords;

        IndustryMatch(String code, int score, List<String> keywords) {
            this.code = code;
            this.score = score;
            this.keywords = keywords;
        }
    }
}
