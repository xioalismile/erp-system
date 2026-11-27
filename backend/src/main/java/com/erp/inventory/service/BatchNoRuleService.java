package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.inventory.batch.entity.BatchInfo;
import com.erp.inventory.batch.mapper.BatchInfoMapper;
import com.erp.inventory.entity.InventoryBatch;
import com.erp.inventory.mapper.InventoryBatchMapper;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批次号规则服务（2026-11-06）。
 *
 * <p>把设置中心「批次编号设置」的规则真正接到单据上：按「货品分类规则 → 默认规则 → 内置默认」三级取值，
 * 生成批次号时同时避开库存批次表与批次档案表里已存在的号，绝不产生重复。</p>
 *
 * <p>口径（2026-11-27 调整）：
 * <ul>
 *   <li>不再有「批次功能总开关」：单据是否显示/校验/生成批次号，只看该行物料档案「批次管理」；</li>
 *   <li>规则里 autoGenerate=false → 不自动生成，由人工填写；allowModify=false → 单据里只读。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchNoRuleService {

    public static final String KEY_REPEAT_INBOUND = "inv.allow.batch.repeat.inbound";
    public static final String RULE_PREFIX = "batch_no_rule_";

    private final SysConfigService sysConfigService;
    private final MaterialService materialService;
    private final InventoryBatchMapper inventoryBatchMapper;
    private final BatchInfoMapper batchInfoMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 是否允许同一物料同一批次号重复入库（设置中心 → 库存参数） */
    public boolean isRepeatInboundAllowed() {
        return sysConfigService.getBooleanValue(KEY_REPEAT_INBOUND, false);
    }

    /** 物料是否开启批次管理 */
    public boolean isLotControl(Long materialId) {
        if (materialId == null) return false;
        Material m = materialService.getById(materialId);
        return m != null && m.getIsLotControl() != null && m.getIsLotControl() == 1;
    }

    /** 取物料所属分类的批次号规则（分类规则 → 默认规则 → 内置默认） */
    public Map<String, Object> rule(Long materialId) {
        Material material = materialId == null ? null : materialService.getById(materialId);
        Long categoryId = material == null ? null : material.getCategoryId();
        Map<String, Object> rule = readRule(categoryId == null ? null : String.valueOf(categoryId));
        if (rule == null) {
            rule = readRule("default");
        }
        if (rule == null) {
            rule = builtInDefault();
        }
        return rule;
    }

    private Map<String, Object> readRule(String suffix) {
        try {
            String val = sysConfigService.getConfigValue(RULE_PREFIX + suffix);
            if (val != null && !val.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(val, HashMap.class);
                return map;
            }
        } catch (Exception e) {
            log.warn("读取批次号规则失败: {}{}", RULE_PREFIX, suffix, e);
        }
        return null;
    }

    private Map<String, Object> builtInDefault() {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("autoGenerate", true);
        def.put("allowModify", true);
        def.put("prefix", "");
        def.put("dateFormat", "YYMMDD");
        def.put("includeMaterialCode", false);
        def.put("seqLength", 4);
        def.put("separator", "-");
        return def;
    }

    /** 规则是否自动生成（默认开） */
    public boolean isAutoGenerate(Map<String, Object> rule) {
        return boolVal(rule.get("autoGenerate"), true);
    }

    /** 规则是否允许手动修改（默认允许） */
    public boolean isAllowModify(Map<String, Object> rule) {
        return boolVal(rule.get("allowModify"), true);
    }

    public boolean isAllowModify(Long materialId) {
        return isAllowModify(rule(materialId));
    }

    /**
     * 单据页使用：返回该物料的批次字段口径 + 下一个可用批次号。
     * 返回：enabled / lotControl / autoGenerate / allowModify / batchNo
     */
    public Map<String, Object> ruleInfo(Long materialId, Collection<String> usedNos) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean lotControl = isLotControl(materialId);
        Map<String, Object> rule = rule(materialId);
        boolean autoGenerate = isAutoGenerate(rule);
        // 批次功能按物料管理：enabled 与 lotControl 同口径（不再有全局开关）
        out.put("enabled", lotControl);
        out.put("lotControl", lotControl);
        out.put("autoGenerate", autoGenerate);
        out.put("allowModify", isAllowModify(rule));
        out.put("batchNo", lotControl && autoGenerate ? generate(materialId, usedNos) : null);
        return out;
    }

    /**
     * 按规则生成批次号；同时避开 usedNos、库存批次表与批次档案表里已有的号。
     * 未开启自动生成时返回 null。
     */
    public String generate(Long materialId, Collection<String> usedNos) {
        if (materialId == null) return null;
        Material material = materialService.getById(materialId);
        Map<String, Object> rule = rule(materialId);
        if (!isAutoGenerate(rule)) return null;

        String prefix = strVal(rule.get("prefix"), "");
        String dateFormat = strVal(rule.get("dateFormat"), "YYMMDD");
        boolean includeMaterialCode = boolVal(rule.get("includeMaterialCode"), false);
        int seqLength = Math.max(2, Math.min(8, intVal(rule.get("seqLength"), 4)));
        String separator = strVal(rule.get("separator"), "-");

        StringBuilder sb = new StringBuilder();
        if (!prefix.isBlank()) sb.append(prefix);
        if (!dateFormat.isBlank()) {
            if (sb.length() > 0 && !separator.isBlank()) sb.append(separator);
            String pattern = dateFormat.replace("YYYY", "uuuu").replace("YY", "uu").replace("DD", "dd");
            try {
                sb.append(LocalDate.now().format(DateTimeFormatter.ofPattern(pattern)));
            } catch (Exception e) {
                sb.append(LocalDate.now().format(DateTimeFormatter.ofPattern("uuMMdd")));
            }
        }
        String materialCode = material == null ? null : material.getMaterialCode();
        if (includeMaterialCode && materialCode != null && !materialCode.isBlank()) {
            if (sb.length() > 0 && !separator.isBlank()) sb.append(separator);
            sb.append(materialCode);
        }
        if (sb.length() > 0 && !separator.isBlank()) sb.append(separator);
        String base = sb.toString();

        int seq = nextSequence(base, materialId, usedNos);
        for (int i = 0; i < 10000; i++) {
            String candidate = base + String.format("%0" + seqLength + "d", seq + i);
            if (isNoUsed(candidate, materialId, usedNos)) continue;
            return candidate;
        }
        throw new BusinessException("批次号生成失败：当前规则下可用流水号已用尽，请调整流水号位数");
    }

    /** 取已有序号最大值 + 1（只看本物料、本前缀的号，避免跨物料互相顶号） */
    private int nextSequence(String base, Long materialId, Collection<String> usedNos) {
        int max = 0;
        List<InventoryBatch> rows = inventoryBatchMapper.selectList(new QueryWrapper<InventoryBatch>()
                .eq("material_id", materialId)
                .likeRight("batch_no", base));
        for (InventoryBatch row : rows) {
            max = Math.max(max, parseSeq(row.getBatchNo(), base));
        }
        if (usedNos != null) {
            for (String no : usedNos) {
                if (no != null) max = Math.max(max, parseSeq(no, base));
            }
        }
        return max + 1;
    }

    private int parseSeq(String no, String base) {
        if (no == null || base == null || !no.startsWith(base)) return 0;
        String tail = no.substring(base.length());
        try {
            return Integer.parseInt(tail);
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isNoUsed(String no, Long materialId, Collection<String> usedNos) {
        if (usedNos != null && usedNos.contains(no)) return true;
        Long count = inventoryBatchMapper.selectCount(new QueryWrapper<InventoryBatch>()
                .eq("material_id", materialId)
                .eq("batch_no", no));
        if (count != null && count > 0) return true;
        Long archiveCount = batchInfoMapper.selectCount(new QueryWrapper<BatchInfo>()
                .eq("material_id", materialId)
                .eq("batch_no", no));
        return archiveCount != null && archiveCount > 0;
    }

    /**
     * 入库保存/提交时兜底：批次管理物料且批号为空时按规则补齐；返回最终批号。
     * 物料未开批次管理时原样返回（空白仍为空白）。
     */
    public String ensureBatchNo(Long materialId, String currentNo, Collection<String> usedNos) {
        if (!isLotControl(materialId)) {
            return currentNo;
        }
        if (currentNo != null && !currentNo.isBlank()) {
            return currentNo.trim();
        }
        Map<String, Object> rule = rule(materialId);
        if (!isAutoGenerate(rule)) {
            return currentNo;
        }
        String generated = generate(materialId, usedNos);
        return generated == null ? currentNo : generated;
    }

    /**
     * 重复入库校验：参数关闭时，同一物料同一批次号已存在则拦截。
     * 未开启批次管理 / 批号为空 时不拦。
     */
    public void checkRepeatInbound(Long materialId, String batchNo, String lineLabel) {
        if (materialId == null || batchNo == null || batchNo.isBlank()) return;
        if (!isLotControl(materialId) || isRepeatInboundAllowed()) return;
        Long count = inventoryBatchMapper.selectCount(new QueryWrapper<InventoryBatch>()
                .eq("material_id", materialId)
                .eq("batch_no", batchNo.trim()));
        if (count != null && count > 0) {
            String prefix = lineLabel == null || lineLabel.isBlank() ? "" : lineLabel + " ";
            throw new BusinessException(prefix + "批次号「" + batchNo.trim()
                    + "」已存在，当前设置不允许同一批次重复入库；如需累加数量，请在设置中心开启「允许批次货品重复入库」");
        }
    }

    /**
     * 修改校验：规则不允许手动修改时，已保存明细的批次号不允许被改。
     * 仅比较「库中原值」与「本次提交值」，新建行（原值为空）不受影响。
     */
    public void checkModifyAllowed(Long materialId, String oldNo, String newNo, String lineLabel) {
        if (materialId == null || !isLotControl(materialId)) return;
        if (isAllowModify(materialId)) return;
        String o = oldNo == null ? "" : oldNo.trim();
        String n = newNo == null ? "" : newNo.trim();
        if (!o.isEmpty() && !o.equals(n)) {
            String prefix = lineLabel == null || lineLabel.isBlank() ? "" : lineLabel + " ";
            throw new BusinessException(prefix + "当前设置不允许手动修改批次号（原批次号：" + o + "）");
        }
    }

    private static String strVal(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }

    private static boolean boolVal(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "是".equals(s);
    }

    private static int intVal(Object v, int def) {
        if (v == null) return def;
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return def;
        }
    }
}
