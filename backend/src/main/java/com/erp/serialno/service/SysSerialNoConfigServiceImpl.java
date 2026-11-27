package com.erp.serialno.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.serialno.entity.SysSerialNoConfig;
import com.erp.serialno.mapper.SysSerialNoConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

@Service
public class SysSerialNoConfigServiceImpl extends ServiceImpl<SysSerialNoConfigMapper, SysSerialNoConfig>
        implements SysSerialNoConfigService {

    @Override
    public List<SysSerialNoConfig> listAll() {
        return list(new QueryWrapper<SysSerialNoConfig>().orderByAsc("sort_order", "id"));
    }

    @Override
    public SysSerialNoConfig getByDocType(String docType) {
        return getOne(new QueryWrapper<SysSerialNoConfig>().eq("doc_type", docType));
    }

    @Override
    public boolean saveConfig(SysSerialNoConfig config) {
        if (config.getSerialLength() == null) {
            config.setSerialLength(4);
        }
        if (config.getCurrentSerial() == null) {
            config.setCurrentSerial(0);
        }
        if (config.getEnabled() == null) {
            config.setEnabled(1);
        }
        return save(config);
    }

    @Override
    public boolean updateConfig(SysSerialNoConfig input) {
        if (input == null || input.getId() == null) {
            throw new com.erp.config.BusinessException("参数不合法：缺少id");
        }
        SysSerialNoConfig db = getById(input.getId());
        if (db == null) {
            throw new com.erp.config.BusinessException("记录不存在，id=" + input.getId());
        }
        if (input.getDocType() != null) db.setDocType(input.getDocType());
        if (input.getDocName() != null) db.setDocName(input.getDocName());
        if (input.getPrefix() != null) db.setPrefix(input.getPrefix());
        if (input.getDateFormat() != null) db.setDateFormat(input.getDateFormat());
        if (input.getSerialLength() != null) db.setSerialLength(input.getSerialLength());
        if (input.getResetType() != null) db.setResetType(input.getResetType());
        if (input.getSeparator() != null) db.setSeparator(input.getSeparator());
        if (input.getCurrentSerial() != null) db.setCurrentSerial(input.getCurrentSerial());
        if (input.getCurrentDate() != null) db.setCurrentDate(input.getCurrentDate());
        if (input.getSortOrder() != null) db.setSortOrder(input.getSortOrder());
        if (input.getEnabled() != null) db.setEnabled(input.getEnabled());
        if (input.getAutoGenerate() != null) db.setAutoGenerate(input.getAutoGenerate());
        if (input.getRemark() != null) db.setRemark(input.getRemark());
        return updateById(db);
    }

    @Override
    public boolean deleteConfig(Long id) {
        return removeById(id);
    }

    @Override
    public String generateSerialNo(String docType) {
        return generateSerialNo(docType, null);
    }

    @Override
    public String generateSerialNo(String docType, UnaryOperator<String> nextNoQuery) {
        return generateSerialNo(docType, nextNoQuery, null);
    }

    @Override
    @Transactional
    public String generateSerialNo(String docType, UnaryOperator<String> nextNoQuery,
                                   java.util.function.Function<String, List<String>> segmentCodesQuery) {
        SysSerialNoConfig config = baseMapper.selectByDocTypeForUpdate(docType);
        if (config == null) {
            throw new BusinessException("单据类型 [" + docType + "] 未配置编号规则");
        }
        if (config.getEnabled() != 1) {
            throw new BusinessException("单据类型 [" + docType + "] 编号规则已禁用");
        }

        String dateFormat = config.getDateFormat();
        String resetType = config.getResetType();
        String separator = config.getSeparator() != null ? config.getSeparator() : "";
        int serialLength = config.getSerialLength() != null ? config.getSerialLength() : 4;

        LocalDate today = LocalDate.now();
        String formattedDate = (dateFormat != null && !dateFormat.isBlank())
                ? today.format(DateTimeFormatter.ofPattern(dateFormat))
                : "";

        // ===== 防重抬底（2026-09-07 bug修复）=====
        // 关闭自动编号/手动录入/默认规则期间产生的业务单号不会推进 current_serial，
        // 重新开启自动编号后若仍从旧计数续号，必然与存量单号重复。生成前以业务表真实已用流水抬底。
        if (nextNoQuery != null) {
            raiseBaselineIfNeeded(config, nextNoQuery, today, formattedDate, separator);
        }

        String resetKey = buildResetKey(resetType, today);
        String currentDate = config.getCurrentDate();

        // 既有口径：按计数器续号（周期切换则归 1）
        int counterNext;
        if (currentDate == null || !currentDate.equals(resetKey)) {
            counterNext = 1;
        } else {
            counterNext = (config.getCurrentSerial() != null ? config.getCurrentSerial() : 0) + 1;
        }

        int nextSerial = counterNext;
        Integer counterToWrite = counterNext;

        // ===== 物料口径：删除即释放 + 补最小空洞（2026-09-11）=====
        // 以业务表实况探测"当前编号段"内已用流水，取最小未用值：删除物料后其编号自动被复用，
        // 历史遗留空洞也会一并回收。探测不可用（号段为空/查询异常）时退回计数器续号，保证不重号。
        if (segmentCodesQuery != null) {
            String fullPrefix = buildFullPrefix(config.getPrefix(), formattedDate, separator);
            long freeSeq = (fullPrefix == null || fullPrefix.isBlank())
                    ? -1L : firstFreeSeqInSegment(segmentCodesQuery, fullPrefix);
            if (freeSeq > 0 && freeSeq <= Integer.MAX_VALUE) {
                nextSerial = (int) freeSeq;
                int cur = config.getCurrentSerial() != null ? config.getCurrentSerial() : 0;
                counterToWrite = Math.max(cur, nextSerial);   // 计数器只增不减
            }
        }

        String serialStr = String.format("%0" + serialLength + "d", nextSerial);

        StringBuilder sb = new StringBuilder();
        if (config.getPrefix() != null && !config.getPrefix().isBlank()) {
            sb.append(config.getPrefix());
        }
        if (!formattedDate.isBlank()) {
            if (!sb.isEmpty() && !separator.isBlank()) {
                sb.append(separator);
            }
            sb.append(formattedDate);
        }
        if (!sb.isEmpty() && !separator.isBlank()) {
            sb.append(separator);
        }
        sb.append(serialStr);

        baseMapper.updateCurrentSerial(config.getId(), counterToWrite, resetKey);

        return sb.toString();
    }

    @Override
    @Transactional
    public List<String> batchGenerate(List<String> docTypes) {
        List<String> result = new ArrayList<>();
        for (String docType : docTypes) {
            result.add(generateSerialNo(docType));
        }
        return result;
    }

    @Override
    public boolean isAutoGenerate(String docType) {
        if (docType == null || docType.isBlank()) return true;
        try {
            SysSerialNoConfig cfg = getByDocType(docType);
            if (cfg == null) return true;
            // 没配置或配置为1都走自动生成；只有显式=0才需要手动输入
            return cfg.getAutoGenerate() == null || cfg.getAutoGenerate() != 0;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public String generateOrDefault(String docType, java.util.function.Supplier<String> fallbackSupplier) {
        return generateOrDefault(docType, fallbackSupplier, null);
    }

    @Override
    public String generateOrDefault(String docType, java.util.function.Supplier<String> fallbackSupplier,
                                    java.util.function.Function<String, List<String>> segmentCodesQuery) {
        return doGenerate(docType, fallbackSupplier, segmentCodesQuery, false);
    }

    @Override
    public String generateForSystem(String docType, java.util.function.Supplier<String> fallbackSupplier) {
        // 系统内部自动建单：忽略「自动生成」关闭标志，仍按编号规则取号
        return doGenerate(docType, fallbackSupplier, null, true);
    }

    private String doGenerate(String docType, java.util.function.Supplier<String> fallbackSupplier,
                              java.util.function.Function<String, List<String>> segmentCodesQuery,
                              boolean ignoreAutoFlag) {
        SysSerialNoConfig cfg;
        try {
            cfg = getByDocType(docType);
        } catch (Exception e) {
            // 配置读取异常时走兜底，保证保存不被打断
            return fallbackSupplier != null ? fallbackSupplier.get() : null;
        }
        // 未配置时走兜底
        if (cfg == null) {
            return fallbackSupplier != null ? fallbackSupplier.get() : null;
        }
        // 已显式关闭自动生成：人工建单要求用户手动输入编号（系统内部自动建单不受此限制）
        if (!ignoreAutoFlag && cfg.getAutoGenerate() != null && cfg.getAutoGenerate() == 0) {
            throw new BusinessException("【" + (cfg.getDocName() != null ? cfg.getDocName() : docType)
                    + "】已关闭自动编号，请手动输入编号");
        }
        try {
            // 自动生成并做业务表防重抬底：fallback 的探测号段若与当前规则一致，则以其真实流水为基准续号
            return generateSerialNo(docType, prefix -> {
                if (fallbackSupplier == null) return null;
                String next = fallbackSupplier.get();
                return (next != null && prefix != null && next.startsWith(prefix)) ? next : null;
            }, segmentCodesQuery);
        } catch (Exception e) {
            return fallbackSupplier != null ? fallbackSupplier.get() : null;
        }
    }

    /**
     * 防重抬底：以业务表真实已用流水抬升配置计数，避免自动编号与存量单号重复。
     * 仅在探测返回单号确实属于当前配置编号段（以完整前缀开头）时才生效；
     * 探测失败或号段不一致时静默跳过，保持原生成逻辑。
     */
    private void raiseBaselineIfNeeded(SysSerialNoConfig config, UnaryOperator<String> nextNoQuery,
                                       LocalDate today, String formattedDate, String separator) {
        String fullPrefix = buildFullPrefix(config.getPrefix(), formattedDate, separator);
        if (fullPrefix == null || fullPrefix.isBlank()) {
            return;
        }
        String bizNext;
        try {
            bizNext = nextNoQuery.apply(fullPrefix);
        } catch (Exception e) {
            // 探测异常不影响正常生成
            return;
        }
        if (bizNext == null || !bizNext.startsWith(fullPrefix)) {
            // 探测号段与当前规则不一致，跳过（不同号段本身不会重号）
            return;
        }
        long bizNextSeq = parseTrailingSeq(bizNext);
        if (bizNextSeq <= 1) {
            return;
        }
        String resetKey = buildResetKey(config.getResetType(), today);
        int cur = config.getCurrentSerial() != null ? config.getCurrentSerial() : 0;
        boolean samePeriod = resetKey.equals(config.getCurrentDate());
        long cfgWillGen = samePeriod ? cur + 1L : 1L;
        if (bizNextSeq <= cfgWillGen) {
            return;
        }
        // 业务表已用流水领先 → 抬升计数到 bizNextSeq-1，保证下一张生成 bizNextSeq，与存量不重复
        baseMapper.updateCurrentSerial(config.getId(), (int) (bizNextSeq - 1), resetKey);
        config.setCurrentSerial((int) (bizNextSeq - 1));
        config.setCurrentDate(resetKey);
    }

    /** 按规则拼出"编号段前缀"（前缀+日期段+分隔符，不含流水），用于业务表 likeRight 探测 */
    private String buildFullPrefix(String prefix, String formattedDate, String separator) {
        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            sb.append(prefix);
        }
        if (formattedDate != null && !formattedDate.isBlank()) {
            if (sb.length() > 0 && separator != null && !separator.isBlank()) {
                sb.append(separator);
            }
            sb.append(formattedDate);
        }
        if (sb.length() > 0 && separator != null && !separator.isBlank()) {
            sb.append(separator);
        }
        return sb.toString();
    }

    /** 解析字符串尾部流水数字，如 SO-20260907-0005 -> 5；无数字返回 0 */
    private long parseTrailingSeq(String no) {
        if (no == null) {
            return 0L;
        }
        int i = no.length() - 1;
        while (i >= 0 && Character.isDigit(no.charAt(i))) {
            i--;
        }
        String tail = no.substring(i + 1);
        if (tail.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(tail);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 在号段内探测"最小可用流水"（删除即释放 / 补最小空洞）。
     * 解析规则：编码必须以号段前缀开头、且其后剩余部分全部为数字；否则视为手工录入或异形编码，跳过不参与占用判定。
     *
     * @return 最小未用流水（≥1）；探测异常返回 -1，交由调用方退回计数器续号
     */
    private long firstFreeSeqInSegment(java.util.function.Function<String, List<String>> segmentCodesQuery,
                                      String fullPrefix) {
        List<String> codes;
        try {
            codes = segmentCodesQuery.apply(fullPrefix);
        } catch (Exception e) {
            // 探测失败不能影响正常生成；返回 -1 让调用方退回计数器续号（避免误判为空号段造成重号）
            return -1L;
        }
        // 最小未用流水口径统一收敛在 SegmentCodeProbe.minFreeSeq（与各主档/导入/供应商控制器共用同一实现）
        return com.erp.serialno.support.SegmentCodeProbe.minFreeSeq(codes, fullPrefix);
    }

    private String buildResetKey(String resetType, LocalDate date) {
        if (resetType == null || "NONE".equals(resetType)) {
            return "NONE";
        }
        return switch (resetType) {
            case "DAILY" -> date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            case "MONTHLY" -> date.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "YEARLY" -> date.format(DateTimeFormatter.ofPattern("yyyy"));
            default -> "NONE";
        };
    }
}
