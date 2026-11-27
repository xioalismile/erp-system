package com.erp.basedata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.MaterialCategory;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.service.MaterialCategoryService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.common.Result;
import com.erp.common.BatchDeleteCheckDTO;
import com.erp.common.scope.DataScopeResolver;
import com.erp.config.SysParamValidator;
import com.erp.config.service.SysConfigService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialCodeRuleService;
import com.erp.masterdata.service.MaterialDedupService;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.ReferenceCheckService;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/basedata/material")
public class MaterialController extends BaseCrudController<Material, Long> {

    private final MaterialService materialService;
    private final MaterialCodeRuleService materialCodeRuleService;
    private final MaterialCostService materialCostService;
    private final SysParamValidator sysParamValidator;
    private final SysSerialNoConfigService serialNoService;
    private final SysConfigService sysConfigService;
    private final MaterialDedupService materialDedupService;
    private final DataScopeResolver dataScopeResolver;
    private final MaterialCategoryService materialCategoryService;
    private final UnitOfMeasureService unitOfMeasureService;
    /** 字段权限（角色必填）兜底：防止绕过前端保存缺值物料（与 4 张单据同款做法） */
    private final com.erp.permission.service.FieldRequiredPermissionService fieldRequiredPermissionService;
    /** 删除前引用校验（2026-09-19）：物料被库存/单据/BOM 等引用时禁止删除，与客户/供应商同口径 */
    private final ReferenceCheckService referenceCheckService;
    /** 辅计量换算明细（2026-11-10）：详情随物料返回「辅计量换算」页签数据 */
    private final com.erp.basedata.service.MaterialUnitConversionService materialUnitConversionService;

    public MaterialController(MaterialService materialService,
                              MaterialCodeRuleService materialCodeRuleService,
                              MaterialCostService materialCostService,
                              SysParamValidator sysParamValidator,
                              SysSerialNoConfigService serialNoService,
                              SysConfigService sysConfigService,
                              MaterialDedupService materialDedupService,
                              DataScopeResolver dataScopeResolver,
                              MaterialCategoryService materialCategoryService,
                              UnitOfMeasureService unitOfMeasureService,
                              com.erp.permission.service.FieldRequiredPermissionService fieldRequiredPermissionService,
                              ReferenceCheckService referenceCheckService,
                              com.erp.basedata.service.MaterialUnitConversionService materialUnitConversionService) {
        this.materialService = materialService;
        this.materialCodeRuleService = materialCodeRuleService;
        this.materialCostService = materialCostService;
        this.sysParamValidator = sysParamValidator;
        this.serialNoService = serialNoService;
        this.sysConfigService = sysConfigService;
        this.materialDedupService = materialDedupService;
        this.dataScopeResolver = dataScopeResolver;
        this.materialCategoryService = materialCategoryService;
        this.unitOfMeasureService = unitOfMeasureService;
        this.fieldRequiredPermissionService = fieldRequiredPermissionService;
        this.referenceCheckService = referenceCheckService;
        this.materialUnitConversionService = materialUnitConversionService;
    }

    /**
     * 物料档案「页面可填字段」白名单：fieldKey → 页面标签。
     *
     * <p>字段权限兜底只覆盖这些字段：元数据里有、但页面上**没有输入口**的字段
     * （createdAt/updatedAt/createdBy/updatedBy/defaultSupplierId/supplyType/stockUnitId/
     * productionUnitId/purchaseUnitId/saleUnitId/unitConversion，后三个 2026-11-10 起页面已撤）即便被配成必填也无处可填，若一并校验会让物料永远保存不了。
     * 提示文案用页面标签，与用户界面一致。</p>
     *
     * <p>维护约定：与 frontend/src/views/basedata/MaterialEdit.vue 的 PAGE_FIELD_LABELS 保持一致，改一处必须同步两处。
     * <b>materialCode 不在其中</b>——它由自动编号/后端生成，页面允许留空（见 MaterialServiceImpl.save）。</p>
     */
    private static final Map<String, String> FILLABLE_LABELS = Map.ofEntries(
            Map.entry("materialName", "物料名称"),
            Map.entry("fullName", "全称"),
            Map.entry("model", "规格型号"),
            Map.entry("materialType", "物料类型"),
            Map.entry("baseUnitId", "基本单位"),
            Map.entry("categoryId", "物料分类"),
            Map.entry("salePrice", "销售单价"),
            Map.entry("purchasePrice", "采购单价"),
            Map.entry("taxRate", "税率"),
            Map.entry("minStock", "最低库存"),
            Map.entry("maxStock", "最高库存"),
            Map.entry("purchaseBatchQty", "采购批量"),
            Map.entry("minOrderQty", "最小订货量"),
            Map.entry("defaultWarehouseId", "默认仓库"),
            Map.entry("isLotControl", "批次管理"),
            Map.entry("isSnControl", "序列号管理"),
            Map.entry("isBinControl", "货位管理"),
            Map.entry("isQcRequired", "质检要求"),
            Map.entry("isExpiryControl", "保质期管理"),
            Map.entry("expiryDays", "有效期天数"),
            Map.entry("isConfidential", "保密物料"),
            Map.entry("status", "状态"),
            Map.entry("remark", "备注"),
            Map.entry("imageUrls", "产品图片")
    );

    @Override
    protected IService<Material> getService() { return materialService; }

    @Override
    protected String getCodeField() { return "material_code"; }

    @Override
    protected String getIdField() { return "material_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("material_code", "material_name", "full_name"); }

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='material'） */
    @Override
    protected String getCustomFieldEntityType() { return "material"; }

    /**
     * 数据权限（2026-09-02 体系落地）：
     * 可见物料 = 数据范围内的物料（created_by 按角色 data_scope） ∪ 货品类别白名单内的物料。
     */
    @Override
    protected void applyDataScopeFilter(QueryWrapper<Material> qw) {
        // 2026-10-09 收口：数据范围 + 货品类别白名单，唯一一份；物料下拉与本档案列表同口径
        dataScopeResolver.applyVisibleMaterials(qw, currentRequest());
    }

    /**
     * 新增物料时自动生成编码：
     * 如果编码规则已启用且物料编码为空，后端自动生成；
     * 前端编码字段灰色只读，用户无需手动输入。
     */
    @Override
    @PostMapping
    public Result<Void> save(@RequestBody Material entity) {
        // 字段权限兜底（限定页面可填字段）：角色配了必填却绕过前端直接提交 → 拒绝保存
        fieldRequiredPermissionService.validateStaticRequiredOrThrow(entity, "material", "物料档案", FILLABLE_LABELS);
        // 编码为空时尝试自动生成：总开关必须最先校验（关闭后规则/通用流水都不许再取号），
        // 否则规则启用时开关成了摆设（2026-11-19 状态C实测）；分类编码规则优先，未配置时回退通用单据编号规则
        if (entity.getMaterialCode() == null || entity.getMaterialCode().isBlank()) {
            if (!serialNoService.isAutoGenerate("MD_MATERIAL")) {
                return Result.error(400, "物料已关闭自动编号，请手动输入编码");
            }
            try {
                String code = materialCodeRuleService.generateCode(entity.getCategoryId());
                entity.setMaterialCode(code);
            } catch (Exception e) {
                // 三参重载：①业务表防重抬底（手动录码/停用规则期间产生的编码以业务表真实流水续号）
                //            ②以业务表实况补最小空洞 —— 删除物料后其编码会被自动复用，不再出现流水断层
                entity.setMaterialCode(serialNoService.generateSerialNo("MD_MATERIAL", prefix -> {
                    if (prefix == null || prefix.isBlank()) {
                        return null;
                    }
                    Material last = materialService.getOne(new QueryWrapper<Material>().likeRight("material_code", prefix)
                            .orderByDesc("material_code").last("LIMIT 1"));
                    if (last == null || last.getMaterialCode() == null) {
                        return null;
                    }
                    try {
                        return prefix + (Integer.parseInt(last.getMaterialCode().substring(prefix.length())) + 1);
                    } catch (Exception ex) {
                        return null;
                    }
                }, materialService::listCodesByPrefix));
            }
        }
        // 编码唯一性校验
        long count = materialService.count(new QueryWrapper<Material>().eq("material_code", entity.getMaterialCode()));
        if (count > 0) {
            return Result.error(409, "编码已存在: " + entity.getMaterialCode());
        }
        // === 设置中心：货品查重（组合唯一 AND：勾选字段值全部相同才判重复，编码单独唯一）===
        String dupErr = materialDedupService.checkUnique(entity, entity.getCustomFieldValues(), null);
        if (dupErr != null) {
            return Result.error(409, dupErr);
        }
        ensureMainImage(entity);
        materialService.save(entity);
        return Result.success();
    }

    /**
     * 主图兜底：mainImage 为空但 imageUrls 有图时，自动取第一张作为主图
     * （历史数据/旧版本前端上传时可能未写 mainImage，导致库存页图片列空白）
     */
    private void ensureMainImage(Material entity) {
        if ((entity.getMainImage() == null || entity.getMainImage().isBlank())
                && entity.getImageUrls() != null && !entity.getImageUrls().isBlank()) {
            String first = entity.getImageUrls().split(",")[0].trim();
            if (!first.isEmpty()) {
                entity.setMainImage(first);
            }
        }
    }

    /**
     * 重写更新：增加设置中心货品名称查重（直接查询数据库，绕过缓存）
     */
    @Override
    @PutMapping
    public Result<Void> update(@RequestBody Material entity) {
        // 字段权限兜底（限定页面可填字段）：与 save 同口径
        fieldRequiredPermissionService.validateStaticRequiredOrThrow(entity, "material", "物料档案", FILLABLE_LABELS);
        // 编码唯一性校验（排除自身）
        if (entity.getMaterialCode() != null && entity.getMaterialId() != null) {
            long codeCount = materialService.count(new QueryWrapper<Material>()
                    .eq("material_code", entity.getMaterialCode())
                    .ne("material_id", entity.getMaterialId()));
            if (codeCount > 0) {
                return Result.error(409, "编码已存在");
            }
        }
        // === 设置中心：货品查重（组合唯一 AND：勾选字段值全部相同才判重复，编码单独唯一）===
        String dupErr = materialDedupService.checkUnique(entity, entity.getCustomFieldValues(), entity.getMaterialId());
        if (dupErr != null) {
            return Result.error(409, dupErr);
        }
        ensureMainImage(entity);
        materialService.updateById(entity);
        return Result.success();
    }

    /**
     * 设置中心可选查重字段清单（物料档案系统字段 + 全部已启用自定义字段）
     * 供「系统参数设置 → 界面显示 → 货品查重字段」下拉多选使用；选项动态生成、不落库。
     */
    @GetMapping("/dedup-field-options")
    public Result<List<Map<String, Object>>> dedupFieldOptions() {
        return Result.success(materialDedupService.listFieldOptions());
    }

    /**
     * 覆写 /page：在父类分页结果之上，对当前页记录批量按外键解析回填三个只读展示字段
     *   ① categoryName  分类名称（material_category.category_name）
     *   ② baseUnitName  基本单位名称（unit_of_measure.unit_name）
     *   ③ avgCost       最新核算成本（与 /list 口径一致）
     * 三者均为 @TableField(exist=false) 的响应载体：不落库、不改表结构、不改入参。
     * 解析失败仅留空，不抛异常，不影响分页结果与其它列。
     * 注意：父类 9 个参数签名必须逐字一致，否则会丢失关键字/状态/排序/数据权限/时间区间过滤能力。
     */
    @Override
    @GetMapping("/page")
    public Result<Page<Material>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String customSearch,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo) {
        // 物料分类筛选（「从物料库选择」弹窗的「物料分类」下拉）：
        // 该参数不能进子类覆写签名（父类 9 参覆写契约不允许加参），改为从请求参数读取后
        // 经 pageQuery 的附加条件注入。此前弹窗把 categoryId 传给不接收该参数的 /page，
        // 分类条件被 Spring 静默丢弃 → 选完分类搜不到（2026-09-20 修复）。
        Long categoryId = null;
        String raw = currentRequest().getParameter("categoryId");
        if (raw != null && !raw.isBlank()) {
            try {
                categoryId = Long.valueOf(raw);
            } catch (NumberFormatException ignored) {
                // 非法分类 id 忽略，不阻断查询
            }
        }
        // 物料属性筛选（2026-11-10）：生产工单「成品明细」只允许选 成品/半成品/自制件，
        // 由调用方以 materialTypes=成品,半成品,自制件 传入；不传 = 不过滤（其它页面行为零变化）。
        // 与 categoryId 同理不能进子类覆写签名，改为请求参数读取后经 extraFilter 注入，
        // 这样分页总数、翻页结果都按属性过滤算，不会出现"本页被前端过滤成空白、总数虚高"。
        List<String> materialTypes = new java.util.ArrayList<>();
        String rawTypes = currentRequest().getParameter("materialTypes");
        if (rawTypes != null && !rawTypes.isBlank()) {
            for (String s : rawTypes.split(",")) {
                if (!s.isBlank()) materialTypes.add(s.trim());
            }
        }
        // final 包装：lambda 只能捕获"事实上最终"的变量
        final Long cid = categoryId;
        final List<String> types = materialTypes;
        java.util.function.Consumer<QueryWrapper<Material>> extraFilter = null;
        if (cid != null || !types.isEmpty()) {
            extraFilter = qw -> {
                if (cid != null) qw.eq("category_id", cid);
                if (!types.isEmpty()) qw.in("material_type", types);
            };
        }
        Result<Page<Material>> result = pageQuery(current, size, keyword, status,
                sortBy, sortOrder, customSearch, createdFrom, createdTo, extraFilter);
        try {
            Page<Material> data = result.getData();
            if (data != null && data.getRecords() != null && !data.getRecords().isEmpty()) {
                fillDisplayNames(data.getRecords());
            }
        } catch (Exception e) {
            // 回填失败不影响分页结果：名称/成本留空即可，不得阻断物料列表与选料弹窗
            log.warn("物料分页回填分类名/单位名/成本失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 批量回填物料列表的分类名称 / 基本单位名称 / 最新核算成本（只读响应字段，不落库）
     * 按当前页 ID 批量查询，避免 N+1；空集合直接跳过，避免空 IN 查询。
     */
    private void fillDisplayNames(List<Material> records) {
        List<Long> materialIds = records.stream()
                .map(Material::getMaterialId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (materialIds.isEmpty()) return;
        // ① 分类名称：按 category_id 批量取 material_category
        Set<Long> categoryIds = records.stream()
                .map(Material::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!categoryIds.isEmpty()) {
            Map<Long, String> categoryNameMap = materialCategoryService.listByIds(categoryIds).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            MaterialCategory::getCategoryId, MaterialCategory::getCategoryName, (a, b) -> a));
            records.forEach(m -> m.setCategoryName(categoryNameMap.get(m.getCategoryId())));
        }
        // ② 基本单位名称：按 base_unit_id 批量取 unit_of_measure
        Set<Long> unitIds = records.stream()
                .map(Material::getBaseUnitId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!unitIds.isEmpty()) {
            Map<Long, String> unitNameMap = unitOfMeasureService.listByIds(unitIds).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            UnitOfMeasure::getUnitId, UnitOfMeasure::getUnitName, (a, b) -> a));
            records.forEach(m -> m.setBaseUnitName(unitNameMap.get(m.getBaseUnitId())));
        }
        // ③ 最新核算成本：与 /list 同口径（无核算记录回填 0）
        Map<Long, java.math.BigDecimal> costMap = materialCostService.batchGetLatestCalcCost(materialIds);
        records.forEach(m -> {
            java.math.BigDecimal cost = costMap.get(m.getMaterialId());
            m.setAvgCost(cost != null ? cost : java.math.BigDecimal.ZERO);
        });
    }

    /**
     * 2026-11-03 单个物料也回填只读展示字段（分类名称/基本单位名称/最新核算成本）：
     * 单据明细行的"物料档案字段补齐"按行调本接口，此前只有 /list 与 /page 回填，
     * 导致勾出的 档案分类/基本单位/最新成本 列在行上取不到值。与 /list、/page 复用同一份实现。
     */
    @Override
    @GetMapping("/{id}")
    public Result<Material> get(@PathVariable Long id) {
        Material m = materialService.getById(id);
        if (m != null) {
            try {
                fillDisplayNames(java.util.Collections.singletonList(m));
            } catch (Exception e) {
                log.warn("物料详情回填分类名/单位名/成本失败: {}", e.getMessage());
            }
            // 辅计量换算明细（2026-11-10）：随详情返回，编辑页「辅计量换算」页签回显
            try {
                m.setUnitConversions(materialUnitConversionService.listByMaterialId(id));
            } catch (Exception e) {
                log.warn("物料详情回填辅计量换算明细失败: {}", e.getMessage());
            }
        }
        return Result.success(m);
    }

    @Override
    @GetMapping("/list")
    public Result<List<Material>> list(@RequestParam(required = false) String status) {
        QueryWrapper<Material> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        // 2026-10-09 权限收口：本方法是覆写基类 /list，历史实现漏掉了数据权限 →
        // 所有单据明细行「选物料」下拉都能翻出全公司物料。改为与物料档案列表同口径。
        applyDataScopeFilter(qw);
        qw.orderByAsc(getCodeField());
        List<Material> list = materialService.list(qw);
        // 回填只读展示字段：分类名称 / 基本单位名称 / 最新核算成本（与 /page 复用同一份实现，避免两处口径漂移）
        // 为什么必须回填 baseUnitName：各单据明细行「选择物料后带出单位」读的就是它；
        // 此前 /list 只回填了 avgCost，单位名称永远取不到值（历史上误读导入专用字段 unitName 同样是空）。
        if (list != null && !list.isEmpty()) {
            try {
                fillDisplayNames(list);
            } catch (Exception e) {
                // 回填失败不影响物料列表：名称/成本留空即可，不得阻断选料
                log.warn("物料列表回填分类名/单位名/成本失败: {}", e.getMessage());
            }
        }
        return Result.success(list);
    }

    /**
     * 预检物料名称是否重复（前端实时校验用）
     * 直接查询数据库配置（绕过Caffeine缓存），确保设置中心修改后立即生效
     * <p>语义随设置中心「货品查重字段」联动：仅当「物料名称」在生效查重字段内时才校验，
     * 否则返回 enabled=false（前端不做提示）。</p>
     * @param materialName 物料名称
     * @param excludeId 排除的物料ID（编辑时排除自身）
     * @return { enabled: 是否开启名称查重, duplicate: 是否已存在重复名称 }
     */
    @GetMapping("/check-name")
    public Result<Map<String, Object>> checkName(
            @RequestParam String materialName,
            @RequestParam(required = false) Long excludeId) {
        Map<String, Object> result = new java.util.HashMap<>();
        // 直接查询数据库（绕过Caffeine缓存），确保设置变更后立即生效
        MaterialDedupService.DedupField nameField = null;
        java.util.List<MaterialDedupService.DedupField> fields = materialDedupService.activeFields();
        for (MaterialDedupService.DedupField d : fields) {
            if ("materialName".equals(d.fieldKey)) {
                nameField = d;
                break;
            }
        }
        boolean configEnabled = nameField != null;

        // 组合唯一(AND)：仅当「物料名称」是唯一非编码查重字段时，名称单独重复才等于组合重复；
        // 勾选了其它字段（规格型号/芯片/型号等）时，名称命中不等于重复，前端不再提示「保存时将被阻止」。
        java.util.List<String> nonCodeKeys = fields.stream()
                .filter(d -> !"materialCode".equals(d.fieldKey))
                .map(d -> d.fieldKey)
                .toList();
        boolean nameIsSoleField = nonCodeKeys.size() == 1 && "materialName".equals(nonCodeKeys.get(0));

        boolean duplicate = false;
        if (configEnabled && nameIsSoleField && materialName != null && !materialName.isBlank()) {
            QueryWrapper<Material> qw = new QueryWrapper<Material>().eq(nameField.column, materialName);
            if (excludeId != null) {
                qw.ne("material_id", excludeId);
            }
            duplicate = materialService.count(qw) > 0;
        }

        result.put("enabled", configEnabled);
        result.put("duplicate", duplicate);
        return Result.success(result);
    }

    @GetMapping("/search")
    public Result<Page<Material>> search(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String materialType) {
        Page<Material> page = new Page<>(current, size);
        QueryWrapper<Material> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            List<String> fields = getSearchFields();
            qw.and(w -> {
                for (int i = 0; i < fields.size(); i++) {
                    if (i > 0) w.or();
                    w.like(fields.get(i), keyword);
                }
            });
        }
        if (status != null && !status.isBlank()) qw.eq("status", status);
        if (categoryId != null) qw.eq("category_id", categoryId);
        if (materialType != null && !materialType.isBlank()) qw.eq("material_type", materialType);
        applyDataScopeFilter(qw);
        qw.orderByDesc(getIdField());
        return Result.success(materialService.page(page, qw));
    }

    /**
     * 删除前引用校验（2026-09-19）：物料被库存 / 单据 / BOM / 价格策略 / 成本核算引用时禁止删除。
     * <p>
     * 与客户档案、供应商档案同口径（{@link ReferenceCheckService}）。提示语会列出引用来源与条数，
     * 用户如需下架改用【停用】——既不打断已有单据与库存台账，也不会再造成"台账物料编码永久空白"。
     */
    @Override
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        String err = referenceCheckService.checkMaterial(id);
        if (err != null) return Result.error(409, err);
        return super.delete(id);
    }

    /** 批量删除：逐个校验引用，命中即整体拦下（与单条删除同源，避免"单条删不掉、批量却删掉了"） */
    @Override
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids != null) {
            for (Long id : ids) {
                String err = referenceCheckService.checkMaterial(id);
                if (err != null) return Result.error(409, "ID=" + id + " " + err);
            }
        }
        return super.batchDelete(ids);
    }

    /**
     * 批量删除「预检」（只查不删）：逐个给出"可删 / 不可删（含原因）"，供前端弹窗展示后由用户自己决定。
     * 执行阶段 {@link #batchDelete} 会重新校验，预检结果不作为免检通行证。
     */
    @Override
    @PostMapping("/batch-delete-check")
    public Result<BatchDeleteCheckDTO> batchDeleteCheck(@RequestBody List<Long> ids) {
        Result<Void> denied = checkWriteRole();
        if (denied != null) return Result.error(denied.getCode(), denied.getMessage());
        BatchDeleteCheckDTO out = new BatchDeleteCheckDTO();
        if (ids == null || ids.isEmpty()) return Result.success(out);
        for (Long id : ids) {
            if (id == null || materialService.getById(id) == null) {
                out.getBlocked().add(new BatchDeleteCheckDTO.Blocked(id, "记录不存在或已被删除"));
                continue;
            }
            String err = referenceCheckService.checkMaterial(id);
            if (err != null) {
                out.getBlocked().add(new BatchDeleteCheckDTO.Blocked(id, err));
            } else {
                out.getDeletable().add(id);
            }
        }
        return Result.success(out);
    }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "base_unit_id",
            "category_id",
            "created_at",
            "created_by",
            "default_supplier_id",
            "default_supplier_name",
            "expiry_days",
            "full_name",
            "image_urls",
            "is_bin_control",
            "is_confidential",
            "is_expiry_control",
            "is_lot_control",
            "is_qc_required",
            "is_sn_control",
            "main_image",
            "material_code",
            "material_id",
            "material_name",
            "material_type",
            "max_stock",
            "min_stock",
            "model",
            "production_unit_id",
            "purchase_price",
            "purchase_unit_id",
            "remark",
            "sale_price",
            "sale_unit_id",
            "status",
            "stock_unit_id",
            "supply_type",
            "tax_rate",
            "unit_conversion",
            "updated_at",
            "updated_by"
        );
    }
}
