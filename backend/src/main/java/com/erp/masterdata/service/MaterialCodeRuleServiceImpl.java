package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.MaterialCategory;
import com.erp.basedata.service.MaterialCategoryService;
import com.erp.config.BusinessException;
import com.erp.masterdata.entity.MaterialCodeRule;
import com.erp.masterdata.mapper.MaterialCodeRuleMapper;
import com.erp.masterdata.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 物料编码规则服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialCodeRuleServiceImpl extends ServiceImpl<MaterialCodeRuleMapper, MaterialCodeRule>
        implements MaterialCodeRuleService {

    /** 默认流水号位数 */
    private static final int DEFAULT_SEQ_LENGTH = 6;

    private final MaterialCategoryService materialCategoryService;
    /** 物料编码探测：用于"删除即释放 + 补最小空洞"取号（按号段查已用编码） */
    private final MaterialMapper materialMapper;

    @Override
    public List<MaterialCodeRule> listAll() {
        QueryWrapper<MaterialCodeRule> wrapper = new QueryWrapper<>();
        // 通用规则（category_id 为 NULL）排在最后
        wrapper.orderByAsc("category_id");
        return list(wrapper);
    }

    @Override
    @Transactional
    public MaterialCodeRule saveEntity(MaterialCodeRule rule) {
        // 当不拼接分类编码时，前缀必填
        boolean useCategoryCode = rule.getIncludeCategoryCode() != null && rule.getIncludeCategoryCode() == 1;
        if (!useCategoryCode && (rule.getPrefix() == null || rule.getPrefix().isBlank())) {
            throw new BusinessException("编码前缀不能为空（或开启\"拼接分类编码\"）");
        }
        if (rule.getPrefix() == null) rule.setPrefix("");
        if (rule.getSeqLength() == null || rule.getSeqLength() <= 0) {
            rule.setSeqLength(DEFAULT_SEQ_LENGTH);
        }
        if (rule.getIsEnabled() == null) {
            rule.setIsEnabled(1);
        }
        if (rule.getIncludeCategoryCode() == null) {
            rule.setIncludeCategoryCode(0);
        }
        if (rule.getIncludeDate() == null) {
            rule.setIncludeDate(0);
        }
        if (rule.getLastSeq() == null) {
            rule.setLastSeq(0L);
        }

        if (rule.getRuleId() == null) {
            // 新增：校验同分类下不能重复配置
            if (rule.getCategoryId() != null) {
                QueryWrapper<MaterialCodeRule> qw = new QueryWrapper<>();
                qw.eq("category_id", rule.getCategoryId());
                if (count(qw) > 0) {
                    throw new BusinessException("该物料分类已存在编码规则");
                }
            } else {
                // 通用规则只能有一条
                QueryWrapper<MaterialCodeRule> qw = new QueryWrapper<>();
                qw.isNull("category_id");
                if (count(qw) > 0) {
                    throw new BusinessException("通用编码规则已存在，不能重复创建");
                }
            }
            rule.setCreatedAt(LocalDateTime.now());
            rule.setUpdatedAt(LocalDateTime.now());
            save(rule);
            log.info("新增物料编码规则: categoryId={} prefix={}", rule.getCategoryId(), rule.getPrefix());
        } else {
            // 修改：同样校验同分类（或通用规则）不能重复，避免把规则改到已配置的分类上产生第二条
            if (rule.getCategoryId() != null) {
                QueryWrapper<MaterialCodeRule> qw = new QueryWrapper<>();
                qw.eq("category_id", rule.getCategoryId()).ne("rule_id", rule.getRuleId());
                if (count(qw) > 0) {
                    throw new BusinessException("该物料分类已存在编码规则");
                }
            } else {
                QueryWrapper<MaterialCodeRule> qw = new QueryWrapper<>();
                qw.isNull("category_id").ne("rule_id", rule.getRuleId());
                if (count(qw) > 0) {
                    throw new BusinessException("通用编码规则已存在，不能重复创建");
                }
            }
            rule.setUpdatedAt(LocalDateTime.now());
            updateById(rule);
            log.info("修改物料编码规则: ruleId={}", rule.getRuleId());
        }
        return rule;
    }

    @Override
    public boolean delete(Long ruleId) {
        if (ruleId == null) return false;
        return removeById(ruleId);
    }

    /**
     * 根据物料分类生成物料编码
     *
     * 编码格式由规则配置决定：
     * - include_category_code=1 时：从根分类到当前分类的 categoryCode 依次拼接
     * - prefix：额外前缀（与分类编码可共存，prefix在前）
     * - include_date=1 时：拼接 yyyyMMdd
     * - 末尾追加 N位流水号（last_seq+1）
     *
     * 示例：prefix="", includeCategoryCode=1, includeDate=0, seqLength=3
     *   分类路径 A > BCD > EFG → 编码 = ABCDEFG001
     *
     * 示例：prefix="MAT", includeCategoryCode=0, includeDate=1, seqLength=4
     *   → 编码 = MAT202607150001
     */
    @Override
    @Transactional
    public String generateCode(Long categoryId) {
        MaterialCodeRule rule = findRuleForCategory(categoryId);

        if (rule == null) {
            throw new BusinessException("未找到启用的物料编码规则，请先配置编码规则");
        }

        // 行锁：采用"补最小空洞"后，并发请求会争抢同一个最小空闲号，必须对规则行加锁使取号串行化
        MaterialCodeRule locked = baseMapper.selectByRuleIdForUpdate(rule.getRuleId());
        if (locked != null) {
            rule = locked;
        }

        int seqLength = rule.getSeqLength() != null && rule.getSeqLength() > 0
                ? rule.getSeqLength() : DEFAULT_SEQ_LENGTH;

        // 1~3. 先拼出"流水号之前的号段"：前缀 + 分类编码链 + 可选日期
        StringBuilder segment = new StringBuilder();
        if (rule.getPrefix() != null && !rule.getPrefix().isBlank()) {
            segment.append(rule.getPrefix());
        }
        if (rule.getIncludeCategoryCode() != null && rule.getIncludeCategoryCode() == 1 && categoryId != null) {
            segment.append(buildCategoryCodeChain(categoryId));
        }
        if (rule.getIncludeDate() != null && rule.getIncludeDate() == 1) {
            segment.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        }
        String segmentPrefix = segment.toString();

        // 4. 以业务表实况取"最小可用流水"：删除物料即释放其编号，下次生成优先补回最小空洞（含历史空洞）
        long lastSeq = rule.getLastSeq() != null ? rule.getLastSeq() : 0L;
        long newSeq = firstFreeSeq(segmentPrefix, seqLength, lastSeq);

        String code = segmentPrefix + String.format("%0" + seqLength + "d", newSeq);

        // 回写 last_seq：保持"号段已用最大流水"语义，只增不减
        UpdateWrapper<MaterialCodeRule> uw = new UpdateWrapper<>();
        uw.eq("rule_id", rule.getRuleId())
                .set("last_seq", Math.max(lastSeq, newSeq))
                .set("updated_at", LocalDateTime.now());
        update(uw);

        log.info("生成物料编码: categoryId={} code={} ruleId={} seq={}",
                categoryId, code, rule.getRuleId(), newSeq);
        return code.toString();
    }

    /**
     * 在号段内探测"最小可用流水"（删除即释放 / 补最小空洞）。
     *
     * 号段界定两种口径：
     * ① 有前缀/分类编码/日期 → 按"前缀"界定：编码必须以号段开头，其后剩余部分全部为数字；
     * ② 三者都为空（规则产出纯数字定长编码）→ 按"格式"界定：全部为数字且长度等于 seqLength。
     * 两种口径下，不符合格式的编码（手工录入/异形/其它规则）一律跳过，不参与占用判定。
     *
     * @param segmentPrefix 号段前缀（流水之前的部分，如 WL0100）；可为空
     * @param seqLength     流水位数（seq_length）
     * @param lastSeq       该规则计数器值；探测失败时用于退回旧行为（lastSeq+1）
     */
    private long firstFreeSeq(String segmentPrefix, int seqLength, long lastSeq) {
        boolean hasSegment = segmentPrefix != null && !segmentPrefix.isBlank();
        List<String> codes;
        try {
            codes = hasSegment
                    ? materialMapper.selectCodesByPrefix(segmentPrefix)
                    : materialMapper.selectPureNumericCodes(seqLength);
        } catch (Exception e) {
            // 探测失败不能影响生成 → 退回计数器续号（避免误判为空号段造成重号）
            log.warn("探测物料编码号段失败，回退计数器续号: {}", e.getMessage());
            return lastSeq + 1;
        }
        java.util.Set<Long> used = new java.util.HashSet<>();
        if (codes != null) {
            for (String c : codes) {
                Long seq = hasSegment ? parseSeqAfterPrefix(c, segmentPrefix) : parsePureNumeric(c, seqLength);
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

    /** 纯数字定长编码 → 流水；长度不符或含非数字返回 null */
    private Long parsePureNumeric(String code, int seqLength) {
        if (code == null) {
            return null;
        }
        String c = code.trim();
        if (seqLength <= 0 || c.length() != seqLength) {
            return null;
        }
        for (int i = 0; i < c.length(); i++) {
            if (!Character.isDigit(c.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.parseLong(c);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析"号段前缀之后"的流水数字；不匹配（前缀不符 / 含非数字 / 超长）返回 null */
    private Long parseSeqAfterPrefix(String code, String segmentPrefix) {
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

    /**
     * 构建分类编码链：从根分类到当前分类的 categoryCode 依次拼接
     * 例如：A(成品) > BCD(螺丝) > EFG(颜色) → "ABCDEFG"
     */
    private String buildCategoryCodeChain(Long categoryId) {
        java.util.List<String> chain = new java.util.ArrayList<>();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        Long currentId = categoryId;

        while (currentId != null && !visited.contains(currentId)) {
            visited.add(currentId);
            MaterialCategory cat = materialCategoryService.getById(currentId);
            if (cat == null) break;
            if (cat.getCategoryCode() != null && !cat.getCategoryCode().isBlank()) {
                chain.add(0, cat.getCategoryCode());
            }
            currentId = cat.getParentId();
        }

        return String.join("", chain);
    }

    /**
     * 查找分类对应的编码规则：
     * 1. 先查分类自身规则
     * 2. 没有则沿 parentId 向上查找（多级分类继承）
     * 3. 最后查通用规则兜底
     */
    private MaterialCodeRule findRuleForCategory(Long categoryId) {
        if (categoryId != null) {
            // 已查过的分类ID，防止循环引用
            java.util.Set<Long> visited = new java.util.HashSet<>();
            Long currentId = categoryId;

            while (currentId != null && !visited.contains(currentId)) {
                visited.add(currentId);

                // 查找当前分类的规则
                QueryWrapper<MaterialCodeRule> qw = new QueryWrapper<>();
                qw.eq("category_id", currentId).eq("is_enabled", 1).last("LIMIT 1");
                MaterialCodeRule rule = getOne(qw);
                if (rule != null) {
                    return rule;
                }

                // 查找父分类ID
                MaterialCategory cat = materialCategoryService.getById(currentId);
                currentId = (cat != null && cat.getParentId() != null) ? cat.getParentId() : null;
            }
        }

        // 查找通用规则（category_id IS NULL）
        QueryWrapper<MaterialCodeRule> qw = new QueryWrapper<>();
        qw.isNull("category_id").eq("is_enabled", 1).last("LIMIT 1");
        return getOne(qw);
    }
}
