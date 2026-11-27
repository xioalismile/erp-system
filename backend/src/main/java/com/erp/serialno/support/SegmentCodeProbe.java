package com.erp.serialno.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 号段实况探测工具：为「删除即释放 + 补最小空洞」提供统一的业务表探测回调。
 *
 * <p>用法：{@code serialNoService.generateSerialNo(docType, this::nextXxxCode, SegmentCodeProbe.of(baseMapper, "xxx_code"))}</p>
 *
 * <p>与 {@code SysSerialNoConfigServiceImpl.firstFreeSeqInSegment} 的约定（不可违反）：</p>
 * <ul>
 *   <li>返回该号段下已使用的全部编码；号段内确实没有数据时返回空列表（生成逻辑会取最小未用流水 1）</li>
 *   <li>探测<b>不可用</b>时必须返回 {@code null} 或抛异常 → 生成逻辑退回计数器续号（保证不重号）；
 *       绝不能用空列表冒充「号段为空」，否则会从 1 号重新发号并与存量编码撞车</li>
 * </ul>
 */
public final class SegmentCodeProbe {

    private SegmentCodeProbe() {
    }

    /**
     * @param mapper     业务表 Mapper（如 UnitOfMeasureMapper）
     * @param codeColumn 编码列名（数据库列名，如 unit_code）
     * @return 号段探测回调
     */
    public static Function<String, List<String>> of(BaseMapper<?> mapper, String codeColumn) {
        return prefix -> {
            if (mapper == null) {
                // 探测不可用：抛异常让调用方退回计数器续号（安全兜底，绝不重号）
                throw new IllegalStateException("号段探测 mapper 缺失: " + codeColumn);
            }
            if (prefix == null || prefix.isBlank()) {
                return null;
            }
            QueryWrapper<Object> qw = new QueryWrapper<>();
            qw.select(codeColumn).likeRight(codeColumn, prefix);
            @SuppressWarnings("unchecked")
            BaseMapper<Object> m = (BaseMapper<Object>) mapper;
            List<Object> objs = m.selectObjs(qw);
            if (objs == null || objs.isEmpty()) {
                return Collections.emptyList();
            }
            return objs.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        };
    }

    /**
     * 取号段内「最小未用流水」（删除即释放 + 补最小空洞）。
     *
     * <p>解析规则：编码必须以号段前缀开头，且其后剩余部分全部为数字；不匹配的（手工录入/异形编码）跳过，
     * 不参与占用判定 —— 宁可少回收，绝不重号。</p>
     *
     * @param codes         号段内已使用的编码（可为 null/空 = 号段内没有数据）
     * @param segmentPrefix 号段前缀（不含流水）
     * @return 最小未用流水（≥1）
     */
    public static long minFreeSeq(java.util.Collection<String> codes, String segmentPrefix) {
        java.util.Set<Long> used = new java.util.HashSet<>();
        if (codes != null && segmentPrefix != null) {
            for (String c : codes) {
                Long seq = parseSeqAfterPrefix(c, segmentPrefix);
                if (seq != null) {
                    used.add(seq);
                }
            }
        }
        long candidate = 1L;
        while (used.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }

    /** 解析「号段前缀之后」的流水数字；不匹配（前缀不符 / 含非数字 / 超长）返回 null */
    private static Long parseSeqAfterPrefix(String code, String segmentPrefix) {
        if (code == null || segmentPrefix == null) {
            return null;
        }
        String c = code.trim();
        if (!c.startsWith(segmentPrefix)) {
            return null;
        }
        String tail = c.substring(segmentPrefix.length());
        if (tail.isEmpty() || tail.length() > 18) {
            return null;
        }
        for (int i = 0; i < tail.length(); i++) {
            if (!Character.isDigit(tail.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.parseLong(tail);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
