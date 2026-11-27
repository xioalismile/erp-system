package com.erp.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.basedata.entity.MaterialCategory;
import com.erp.basedata.mapper.MaterialCategoryMapper;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.entity.WorkOrderProduct;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderOperationMapper;
import com.erp.production.mapper.WorkOrderProductMapper;
import com.erp.qc.entity.QcTemplate;
import com.erp.qc.entity.QcTemplateCategory;
import com.erp.qc.entity.QcTemplateItem;
import com.erp.qc.entity.QcTemplateMaterial;
import com.erp.qc.mapper.QcTemplateCategoryMapper;
import com.erp.qc.mapper.QcTemplateItemMapper;
import com.erp.qc.mapper.QcTemplateMapper;
import com.erp.qc.mapper.QcTemplateMaterialMapper;
import com.erp.qc.service.QcTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 质检模板服务实现。
 *
 * <p>绑定模型（2026-10-07 改造后，用户确认的唯一事实来源）：
 * 模板可绑「多个物料」+「多个分类」+「一个工序名」（三者至少填一个）；
 * 匹配工单工序时按「物料+工序 &gt; 仅物料 &gt; 仅工序 &gt; 分类」顺序取唯一一条；
 * 同一「物料/分类 + 工序」组合不允许两条启用模板（保存时拦截）。</p>
 *
 * <p>只依赖各 Mapper（不依赖工单工序 Service），避免与 {@code WorkOrderOperationServiceImpl} 形成循环依赖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QcTemplateServiceImpl implements QcTemplateService {

    /** 质检类型取值：首检 / 抽检 / 全检（判定逻辑不同，写死为业务枚举，不可维护） */
    private static final List<String> QC_TYPES = List.of("首检", "抽检", "全检");

    /** 模板类型：物料质检模板（按物料/分类/工序绑定） */
    public static final String TEMPLATE_TYPE_MATERIAL = "MATERIAL";
    /** 模板类型：单据质检模板（按单据类型绑定，一张入库单用它） */
    public static final String TEMPLATE_TYPE_BILL = "BILL";

    private final QcTemplateMapper templateMapper;
    private final QcTemplateItemMapper itemMapper;
    private final QcTemplateMaterialMapper templateMaterialMapper;
    private final QcTemplateCategoryMapper templateCategoryMapper;
    private final WorkOrderOperationMapper workOrderOperationMapper;
    private final WorkOrderProductMapper workOrderProductMapper;
    private final WorkOrderMapper workOrderMapper;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper materialCategoryMapper;

    @Override
    public PageResult<QcTemplate> pageQuery(int current, int size, String keyword, String qcType, Integer isEnabled) {
        QueryWrapper<QcTemplate> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            // 关键词多列成组：避免平铺 or() 让命中第一列的行绕过其它筛选
            qw.and(w -> w.like("template_code", kw).or().like("template_name", kw)
                    .or().like("operation_name", kw));
        }
        if (qcType != null && !qcType.isBlank()) qw.eq("qc_type", qcType);
        if (isEnabled != null) qw.eq("is_enabled", isEnabled);
        qw.orderByDesc("template_id");

        Page<QcTemplate> page = templateMapper.selectPage(new Page<>(current, size), qw);
        List<QcTemplate> records = page.getRecords();
        fillBindings(records);
        fillItemCounts(records);
        return PageResult.of(records, page.getTotal(), current, size);
    }

    @Override
    public QcTemplate getDetail(Long templateId) {
        if (templateId == null) return null;
        QcTemplate t = templateMapper.selectById(templateId);
        if (t == null) return null;
        fillBindings(List.of(t));
        t.setItems(listItems(templateId));
        t.setItemCount(t.getItems() == null ? 0 : t.getItems().size());
        return t;
    }

    @Override
    @Transactional
    public Long createTemplate(QcTemplate template) {
        normalize(template);
        validate(template);
        checkDuplicateBinding(template, null);
        if (template.getTemplateCode() == null || template.getTemplateCode().isBlank()) {
            template.setTemplateCode(nextTemplateCode());
        }
        template.setTemplateId(null);
        templateMapper.insert(template);
        saveItems(template.getTemplateId(), template.getItems());
        saveBindings(template.getTemplateId(), template.getMaterialIds(), template.getCategoryIds());
        return template.getTemplateId();
    }

    @Override
    @Transactional
    public void updateTemplate(Long templateId, QcTemplate template) {
        QcTemplate exist = templateMapper.selectById(templateId);
        if (exist == null) throw new BusinessException("质检模板不存在");
        // 老调用方只改名称、不带多选集合时，沿用库中已有绑定（避免"改个名把绑定清空"）
        if (template.getMaterialIds() == null) template.setMaterialIds(listMaterialIds(templateId, exist));
        if (template.getCategoryIds() == null) template.setCategoryIds(listCategoryIds(templateId));
        normalize(template);
        // 编码不允许改（保持稳定引用），留空则沿用旧值
        if (template.getTemplateCode() == null || template.getTemplateCode().isBlank()) {
            template.setTemplateCode(exist.getTemplateCode());
        }
        if (template.getIsEnabled() == null) template.setIsEnabled(exist.getIsEnabled());
        validate(template);
        checkDuplicateBinding(template, templateId);
        template.setTemplateId(templateId);

        // 用显式 set 更新（而非 updateById）：工序名/备注等被清空时也要真的写空，
        // 否则 MyBatis-Plus 默认忽略 null → "清了工序保存后还在"
        templateMapper.update(null, new UpdateWrapper<QcTemplate>()
                .eq("template_id", templateId)
                .set("template_code", template.getTemplateCode())
                .set("template_name", template.getTemplateName())
                .set("template_type", template.getTemplateType())
                .set("bill_types", template.getBillTypes())
                .set("is_default", template.getIsDefault())
                .set("material_id", template.getMaterialId())
                .set("operation_name", template.getOperationName())
                .set("qc_type", template.getQcType())
                .set("sample_ratio", template.getSampleRatio())
                .set("allowed_defect_ratio", template.getAllowedDefectRatio())
                .set("is_enabled", template.getIsEnabled())
                .set("remark", template.getRemark())
                .set("updated_at", LocalDateTime.now()));

        // 检验项整单覆盖：先清理旧行再落新行（与 BOM 明细同款口径）
        itemMapper.delete(new QueryWrapper<QcTemplateItem>().eq("template_id", templateId));
        saveItems(templateId, template.getItems());
        saveBindings(templateId, template.getMaterialIds(), template.getCategoryIds());
    }

    @Override
    @Transactional
    public void deleteTemplate(Long templateId) {
        if (templateId == null) return;
        templateMapper.deleteById(templateId);
        itemMapper.delete(new QueryWrapper<QcTemplateItem>().eq("template_id", templateId));
        templateMaterialMapper.delete(new QueryWrapper<QcTemplateMaterial>().eq("template_id", templateId));
        templateCategoryMapper.delete(new QueryWrapper<QcTemplateCategory>().eq("template_id", templateId));
    }

    @Override
    public List<QcTemplate> listEnabled() {
        List<QcTemplate> list = templateMapper.selectList(new QueryWrapper<QcTemplate>()
                .eq("is_enabled", 1)
                .and(w -> w.isNull("template_type").or().ne("template_type", TEMPLATE_TYPE_BILL))
                .orderByAsc("template_name"));
        fillBindings(list);
        fillItemCounts(list);
        return list;
    }

    @Override
    public List<QcTemplate> listByBillType(String billType) {
        if (billType == null || billType.isBlank()) return List.of();
        List<QcTemplate> list = templateMapper.selectList(new QueryWrapper<QcTemplate>()
                .eq("is_enabled", 1)
                .eq("template_type", TEMPLATE_TYPE_BILL)
                .like("bill_types", billType.trim())
                .orderByDesc("is_default").orderByAsc("template_name"));
        // like 可能命中子串（如 PRODUCTION_RECEIPT 与 X_PRODUCTION_RECEIPT），按逗号清单精确过滤
        list.removeIf(t -> !containsBillType(t.getBillTypes(), billType.trim()));
        fillItemCounts(list);
        return list;
    }

    private static boolean containsBillType(String billTypes, String billType) {
        if (billTypes == null || billTypes.isBlank()) return false;
        for (String s : billTypes.split(",")) {
            if (billType.equals(s.trim())) return true;
        }
        return false;
    }

    /**
     * 取某道工单工序应使用的质检模板（找到即停）。
     *
     * <p>优先级：① 工序行手工指定 → ② 物料＋工序（最精确，专治同名） → ③ 仅物料 →
     * ④ 仅工序（模板未绑物料/分类） → ⑤ 适用分类（该物料所属分类及其上级分类） → ⑥ 不绑定。</p>
     */
    @Override
    public QcTemplate resolveTemplateForOperation(Long operationRowId) {
        if (operationRowId == null) return null;
        WorkOrderOperation op = workOrderOperationMapper.selectById(operationRowId);
        if (op == null) return null;

        // ① 工单工序上显式绑定的模板（工单编辑页选择）
        if (op.getQcTemplateId() != null) {
            QcTemplate t = getDetail(op.getQcTemplateId());
            if (t != null && (t.getIsEnabled() == null || t.getIsEnabled() == 1)) return t;
        }

        String opName = op.getOperationName() == null ? null : op.getOperationName().trim();
        boolean hasOp = opName != null && !opName.isBlank();
        Long materialId = resolveMaterialId(op);

        // ② 物料 + 工序（最精确）
        if (materialId != null && hasOp) {
            QcTemplate t = pick(templateIdsByMaterial(materialId), true, opName);
            if (t != null) return t;
        }
        // ③ 仅物料（模板按物料绑定、未指定工序）
        if (materialId != null) {
            QcTemplate t = pick(templateIdsByMaterial(materialId), false, null);
            if (t != null) return t;
        }
        // ④ 仅工序（模板只绑了工序，未绑物料与分类）
        if (hasOp) {
            QcTemplate t = pickOperationOnly(opName);
            if (t != null) return t;
        }
        // ⑤ 适用分类兜底（该物料所属分类及其上级分类）
        if (materialId != null) {
            List<Long> chain = categoryChainOfMaterial(materialId);
            if (!chain.isEmpty()) {
                QcTemplate t = pickCategory(templateIdsByCategory(chain), hasOp, opName);
                if (t != null) return t;
            }
        }
        // ⑥ 都没有 = 不绑定
        return null;
    }

    /**
     * 取某物料应使用的质检模板（化妆品批记录·批次检验用）。
     * 优先级：① 按物料绑定（模板工序名为空才命中，批次检验无工序上下文） →
     * ② 适用分类（该物料所属分类及其上级分类） → ③ 返回 null。
     */
    @Override
    public QcTemplate resolveTemplateForMaterial(Long materialId) {
        if (materialId == null) return null;
        // ① 仅物料（模板按物料绑定、未指定工序）
        QcTemplate t = pick(templateIdsByMaterial(materialId), false, null);
        if (t != null) return t;
        // ② 分类链兜底
        List<Long> chain = categoryChainOfMaterial(materialId);
        if (!chain.isEmpty()) {
            t = pickCategory(templateIdsByCategory(chain), false, null);
            if (t != null) return t;
        }
        return null;
    }

    // ---------------- 匹配辅助 ----------------

    /** 在给定模板集合里取一条启用模板：requireOp=true 要求工序名相同，否则要求工序名为空 */
    private QcTemplate pick(List<Long> templateIds, boolean requireOp, String opName) {
        if (templateIds == null || templateIds.isEmpty()) return null;
        QueryWrapper<QcTemplate> qw = new QueryWrapper<QcTemplate>()
                .in("template_id", templateIds)
                .eq("is_enabled", 1);
        if (requireOp) {
            qw.eq("operation_name", opName);
        } else {
            // 工序为空（NULL 或空串）；成组避免 or() 平铺吃掉其它条件
            qw.and(w -> w.isNull("operation_name").or().eq("operation_name", ""));
        }
        qw.orderByDesc("template_id").last("LIMIT 1");
        QcTemplate t = templateMapper.selectOne(qw);
        return t == null ? null : getDetail(t.getTemplateId());
    }

    /** 分类层取模板：模板工序名为空、或与本道工序相同，都算命中（分类是兜底维度，通常不配工序） */
    private QcTemplate pickCategory(List<Long> templateIds, boolean hasOp, String opName) {
        if (templateIds == null || templateIds.isEmpty()) return null;
        QueryWrapper<QcTemplate> qw = new QueryWrapper<QcTemplate>()
                .in("template_id", templateIds)
                .eq("is_enabled", 1);
        if (hasOp) {
            qw.and(w -> w.isNull("operation_name").or().eq("operation_name", "").or().eq("operation_name", opName));
        } else {
            qw.and(w -> w.isNull("operation_name").or().eq("operation_name", ""));
        }
        qw.orderByDesc("template_id").last("LIMIT 1");
        QcTemplate t = templateMapper.selectOne(qw);
        return t == null ? null : getDetail(t.getTemplateId());
    }

    /** 仅工序模板：工序名相同、启用、且没有绑任何物料与分类 */
    private QcTemplate pickOperationOnly(String opName) {
        List<QcTemplate> list = templateMapper.selectList(new QueryWrapper<QcTemplate>()
                .eq("operation_name", opName).eq("is_enabled", 1).orderByDesc("template_id"));
        if (list.isEmpty()) return null;
        List<Long> ids = list.stream().map(QcTemplate::getTemplateId).collect(Collectors.toList());
        Set<Long> bound = new HashSet<>();
        bound.addAll(templateMaterialMapper.selectList(new QueryWrapper<QcTemplateMaterial>().in("template_id", ids))
                .stream().map(QcTemplateMaterial::getTemplateId).collect(Collectors.toSet()));
        bound.addAll(templateCategoryMapper.selectList(new QueryWrapper<QcTemplateCategory>().in("template_id", ids))
                .stream().map(QcTemplateCategory::getTemplateId).collect(Collectors.toSet()));
        for (QcTemplate t : list) {
            if (!bound.contains(t.getTemplateId())) return getDetail(t.getTemplateId());
        }
        return null;
    }

    private List<Long> templateIdsByMaterial(Long materialId) {
        return templateMaterialMapper.selectList(new QueryWrapper<QcTemplateMaterial>()
                        .eq("material_id", materialId))
                .stream().map(QcTemplateMaterial::getTemplateId).distinct().collect(Collectors.toList());
    }

    private List<Long> templateIdsByCategory(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) return List.of();
        return templateCategoryMapper.selectList(new QueryWrapper<QcTemplateCategory>()
                        .in("category_id", categoryIds))
                .stream().map(QcTemplateCategory::getTemplateId).distinct().collect(Collectors.toList());
    }

    /** 物料所属分类及其所有上级分类（模板挂上级分类时，下级分类的物料也适用） */
    private List<Long> categoryChainOfMaterial(Long materialId) {
        Material m = materialMapper.selectById(materialId);
        if (m == null || m.getCategoryId() == null) return List.of();
        List<MaterialCategory> all = materialCategoryMapper.selectList(null);
        Map<Long, Long> parentOf = new HashMap<>();
        for (MaterialCategory c : all) parentOf.put(c.getCategoryId(), c.getParentId());
        List<Long> chain = new ArrayList<>();
        Long cur = m.getCategoryId();
        int guard = 0;
        while (cur != null && guard++ < 32) {
            chain.add(cur);
            Long p = parentOf.get(cur);
            if (p == null || p.equals(cur) || chain.contains(p)) break;
            cur = p;
        }
        return chain;
    }

    // ---------------- 保存辅助 ----------------

    /** 归一化：默认值 + 工序名去空格 + 旧单值列兼容写入（读关联表优先，旧列仅过渡） */
    private void normalize(QcTemplate t) {
        if (t.getIsEnabled() == null) t.setIsEnabled(1);
        if (t.getTemplateType() == null || t.getTemplateType().isBlank()) t.setTemplateType(TEMPLATE_TYPE_MATERIAL);
        if (t.getIsDefault() == null) t.setIsDefault(0);
        if (t.getBillTypes() != null) {
            String bt = Arrays.stream(t.getBillTypes().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .distinct().collect(Collectors.joining(","));
            t.setBillTypes(bt.isEmpty() ? null : bt);
        }
        if (t.getQcType() == null || t.getQcType().isBlank()) t.setQcType("首检");
        if (t.getSampleRatio() == null) t.setSampleRatio(BigDecimal.ZERO);
        if (t.getAllowedDefectRatio() == null) t.setAllowedDefectRatio(BigDecimal.ZERO);
        if (t.getOperationName() != null) {
            String opn = t.getOperationName().trim();
            t.setOperationName(opn.isEmpty() ? null : opn);
        }
        if (TEMPLATE_TYPE_BILL.equals(t.getTemplateType())) {
            // 单据级模板只按单据类型绑定，清掉物料/分类/工序绑定，避免两套口径混用
            t.setMaterialIds(new ArrayList<>());
            t.setCategoryIds(new ArrayList<>());
            t.setOperationName(null);
            t.setMaterialId(null);
        }
        List<Long> matIds = t.getMaterialIds();
        t.setMaterialId(matIds == null || matIds.isEmpty() ? null : matIds.get(0));
    }

    private List<Long> listMaterialIds(Long templateId, QcTemplate exist) {
        List<Long> ids = templateMaterialMapper.selectList(new QueryWrapper<QcTemplateMaterial>()
                        .eq("template_id", templateId).orderByAsc("id"))
                .stream().map(QcTemplateMaterial::getMaterialId).collect(Collectors.toList());
        if (ids.isEmpty() && exist != null && exist.getMaterialId() != null) {
            ids = new ArrayList<>(List.of(exist.getMaterialId()));
        }
        return ids;
    }

    private List<Long> listCategoryIds(Long templateId) {
        return templateCategoryMapper.selectList(new QueryWrapper<QcTemplateCategory>()
                        .eq("template_id", templateId).orderByAsc("id"))
                .stream().map(QcTemplateCategory::getCategoryId).collect(Collectors.toList());
    }

    /** 整单覆盖保存绑定（先清旧再落新，与检验项同款口径） */
    private void saveBindings(Long templateId, List<Long> materialIds, List<Long> categoryIds) {
        templateMaterialMapper.delete(new QueryWrapper<QcTemplateMaterial>().eq("template_id", templateId));
        templateCategoryMapper.delete(new QueryWrapper<QcTemplateCategory>().eq("template_id", templateId));
        if (materialIds != null) {
            for (Long mid : new LinkedHashSet<>(materialIds)) {
                if (mid == null) continue;
                QcTemplateMaterial row = new QcTemplateMaterial();
                row.setTemplateId(templateId);
                row.setMaterialId(mid);
                templateMaterialMapper.insert(row);
            }
        }
        if (categoryIds != null) {
            for (Long cid : new LinkedHashSet<>(categoryIds)) {
                if (cid == null) continue;
                QcTemplateCategory row = new QcTemplateCategory();
                row.setTemplateId(templateId);
                row.setCategoryId(cid);
                templateCategoryMapper.insert(row);
            }
        }
    }

    /**
     * 同一「物料/分类 + 工序」组合不允许两条启用模板（用户口径：保存时拦截）。
     *
     * <p>键 = 类型:取值:工序名；仅工序模板（无物料无分类）用 O:工序名 单独成键。
     * 两条启用模板的键集合有交集即视为重复。</p>
     */
    private void checkDuplicateBinding(QcTemplate current, Long selfTemplateId) {
        if (current.getIsEnabled() != null && current.getIsEnabled() == 0) return; // 停用模板不参与
        Set<String> keys = bindingKeys(current);
        if (keys.isEmpty()) return;

        List<QcTemplate> enabled = templateMapper.selectList(new QueryWrapper<QcTemplate>().eq("is_enabled", 1));
        enabled.removeIf(t -> selfTemplateId != null && selfTemplateId.equals(t.getTemplateId()));
        if (enabled.isEmpty()) return;
        fillBindings(enabled);

        List<String> conflicts = new ArrayList<>();
        for (QcTemplate other : enabled) {
            Set<String> otherKeys = new LinkedHashSet<>(bindingKeys(other));
            otherKeys.retainAll(keys);
            if (!otherKeys.isEmpty()) {
                conflicts.add(other.getTemplateName()
                        + (other.getTemplateCode() == null ? "" : "（" + other.getTemplateCode() + "）"));
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException("已存在相同绑定的启用模板：" + String.join("、", conflicts)
                    + "；同一「物料/分类＋工序」只能有一条启用模板，请修改那条或先停用");
        }
    }

    private Set<String> bindingKeys(QcTemplate t) {
        Set<String> keys = new LinkedHashSet<>();
        String op = t.getOperationName() == null ? "" : t.getOperationName().trim();
        List<Long> mIds = t.getMaterialIds() == null ? Collections.emptyList() : t.getMaterialIds();
        List<Long> cIds = t.getCategoryIds() == null ? Collections.emptyList() : t.getCategoryIds();
        for (Long mid : mIds) if (mid != null) keys.add("M:" + mid + ":" + op);
        for (Long cid : cIds) if (cid != null) keys.add("C:" + cid + ":" + op);
        if (keys.isEmpty() && !op.isEmpty()) keys.add("O:" + op);
        return keys;
    }

    /** 批量回填多选绑定与展示名称；顺带兼容旧单值字段（取第一条） */
    private void fillBindings(List<QcTemplate> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> ids = list.stream().map(QcTemplate::getTemplateId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return;

        Map<Long, List<Long>> matMap = new HashMap<>();
        for (QcTemplateMaterial m : templateMaterialMapper.selectList(new QueryWrapper<QcTemplateMaterial>()
                .in("template_id", ids).orderByAsc("id"))) {
            matMap.computeIfAbsent(m.getTemplateId(), k -> new ArrayList<>()).add(m.getMaterialId());
        }
        Map<Long, List<Long>> catMap = new HashMap<>();
        for (QcTemplateCategory c : templateCategoryMapper.selectList(new QueryWrapper<QcTemplateCategory>()
                .in("template_id", ids).orderByAsc("id"))) {
            catMap.computeIfAbsent(c.getTemplateId(), k -> new ArrayList<>()).add(c.getCategoryId());
        }

        Map<Long, Material> matDic = new HashMap<>();
        Set<Long> allMatIds = matMap.values().stream().flatMap(List::stream).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!allMatIds.isEmpty()) {
            for (Material m : materialMapper.selectBatchIds(allMatIds)) matDic.put(m.getMaterialId(), m);
        }
        Map<Long, MaterialCategory> catDic = new HashMap<>();
        Set<Long> allCatIds = catMap.values().stream().flatMap(List::stream).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!allCatIds.isEmpty()) {
            for (MaterialCategory c : materialCategoryMapper.selectBatchIds(allCatIds)) catDic.put(c.getCategoryId(), c);
        }

        for (QcTemplate t : list) {
            List<Long> mIds = matMap.get(t.getTemplateId());
            // 迁移兜底：关联表还没行时用旧单值列兜底，保证升级瞬间列表也有值
            if ((mIds == null || mIds.isEmpty()) && t.getMaterialId() != null) {
                mIds = new ArrayList<>(List.of(t.getMaterialId()));
            }
            t.setMaterialIds(mIds == null ? new ArrayList<>() : mIds);
            t.setCategoryIds(catMap.getOrDefault(t.getTemplateId(), new ArrayList<>()));

            List<String> mNames = new ArrayList<>();
            for (Long mid : t.getMaterialIds()) {
                Material m = matDic.get(mid);
                if (m == null && mid != null) {
                    m = materialMapper.selectById(mid);
                    if (m != null) matDic.put(mid, m);
                }
                if (m != null) mNames.add(label(m.getMaterialName(), m.getMaterialCode()));
            }
            t.setMaterialNames(mNames);

            List<String> cNames = new ArrayList<>();
            for (Long cid : t.getCategoryIds()) {
                MaterialCategory c = catDic.get(cid);
                if (c != null) cNames.add(c.getCategoryName());
            }
            t.setCategoryNames(cNames);

            // 旧单值字段保持有值（老调用方兼容；新逻辑一律读多选集合）
            if (!t.getMaterialIds().isEmpty()) {
                Long first = t.getMaterialIds().get(0);
                t.setMaterialId(first);
                Material fm = matDic.get(first);
                if (fm != null) {
                    t.setMaterialName(fm.getMaterialName());
                    t.setMaterialCode(fm.getMaterialCode());
                }
            } else {
                t.setMaterialId(null);
                t.setMaterialName(null);
                t.setMaterialCode(null);
            }
        }
    }

    private String label(String name, String code) {
        String n = name == null ? "" : name;
        String c = code == null ? "" : code;
        if (!n.isEmpty() && !c.isEmpty()) return n + " (" + c + ")";
        return n.isEmpty() ? c : n;
    }

    // ---------------- 内部工具 ----------------

    /** 取工序所属成品行的物料ID；老数据（无成品行归属）回落整单成品物料 */
    private Long resolveMaterialId(WorkOrderOperation op) {
        if (op.getOrderProductId() != null) {
            WorkOrderProduct product = workOrderProductMapper.selectById(op.getOrderProductId());
            if (product != null && product.getMaterialId() != null) return product.getMaterialId();
        }
        if (op.getOrderId() != null) {
            WorkOrder order = workOrderMapper.selectById(op.getOrderId());
            if (order != null) return order.getMaterialId();
        }
        return null;
    }

    private List<QcTemplateItem> listItems(Long templateId) {
        return itemMapper.selectList(new QueryWrapper<QcTemplateItem>()
                .eq("template_id", templateId)
                .orderByAsc("line_no").orderByAsc("item_id"));
    }

    private void saveItems(Long templateId, List<QcTemplateItem> items) {
        if (templateId == null || items == null || items.isEmpty()) return;
        int idx = 0;
        for (QcTemplateItem item : items) {
            if (item == null || item.getItemName() == null || item.getItemName().isBlank()) continue;
            idx++;
            item.setItemId(null);
            item.setTemplateId(templateId);
            item.setLineNo(item.getLineNo() != null ? item.getLineNo() : idx * 10);
            if (item.getIsRequired() == null) item.setIsRequired(1);
            itemMapper.insert(item);
        }
    }

    /** 补检验项数量（列表展示用；避免列表页做出"永远是 0"的空列） */
    private void fillItemCounts(List<QcTemplate> list) {
        if (list == null || list.isEmpty()) return;
        for (QcTemplate t : list) {
            if (t.getTemplateId() == null) continue;
            Long cnt = itemMapper.selectCount(new QueryWrapper<QcTemplateItem>()
                    .eq("template_id", t.getTemplateId()));
            t.setItemCount(cnt == null ? 0 : cnt.intValue());
        }
    }

    private void validate(QcTemplate template) {
        if (template == null) throw new BusinessException("模板内容不能为空");
        if (template.getTemplateName() == null || template.getTemplateName().isBlank()) {
            throw new BusinessException("请填写模板名称");
        }
        if (template.getQcType() != null && !template.getQcType().isBlank()
                && !QC_TYPES.contains(template.getQcType())) {
            throw new BusinessException("质检类型只能是 首检 / 抽检 / 全检");
        }
        // 单据级模板：必须绑定至少一个单据类型；不要求物料/分类/工序绑定
        if (TEMPLATE_TYPE_BILL.equals(template.getTemplateType())) {
            if (template.getBillTypes() == null || template.getBillTypes().isBlank()) {
                throw new BusinessException("单据质检模板请至少选择一个适用单据类型");
            }
            return;
        }
        // 物料级模板至少要能"被找到"：物料 / 工序 / 分类三者至少填一个（否则永远解析不到）
        boolean hasMaterial = template.getMaterialIds() != null
                && template.getMaterialIds().stream().anyMatch(Objects::nonNull);
        boolean hasCategory = template.getCategoryIds() != null
                && template.getCategoryIds().stream().anyMatch(Objects::nonNull);
        boolean hasOperation = template.getOperationName() != null && !template.getOperationName().isBlank();
        if (!hasMaterial && !hasCategory && !hasOperation) {
            throw new BusinessException("请至少指定适用物料、适用工序或适用分类（否则模板无法被使用）");
        }
    }

    /** 模板编码自动生成：QCT + 4 位流水（取当前最大值 +1；不依赖编号规则配置，避免未配置时抛错） */
    private String nextTemplateCode() {
        QcTemplate last = templateMapper.selectOne(new QueryWrapper<QcTemplate>()
                .likeRight("template_code", "QCT")
                .orderByDesc("template_id")
                .last("LIMIT 1"));
        int next = 1;
        if (last != null && last.getTemplateCode() != null) {
            String digits = last.getTemplateCode().replaceAll("[^0-9]", "");
            if (!digits.isBlank()) {
                try {
                    next = Integer.parseInt(digits) + 1;
                } catch (NumberFormatException ignore) {
                    next = 1;
                }
            }
        }
        return String.format("QCT%04d", next);
    }
}
