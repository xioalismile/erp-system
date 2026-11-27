package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.basedata.entity.MaterialCategory;
import com.erp.basedata.mapper.MaterialCategoryMapper;
import com.erp.config.BusinessException;
import com.erp.customfield.entity.CustomField;
import com.erp.customfield.service.CustomFieldService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.production.entity.Routing;
import com.erp.production.entity.RoutingMaterial;
import com.erp.production.entity.RoutingMaterialCategory;
import com.erp.production.entity.RoutingOperation;
import com.erp.production.entity.RoutingOperationField;
import com.erp.production.mapper.RoutingMapper;
import com.erp.production.mapper.RoutingMaterialCategoryMapper;
import com.erp.production.mapper.RoutingMaterialMapper;
import com.erp.production.mapper.RoutingOperationFieldMapper;
import com.erp.production.mapper.RoutingOperationMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor

public class RoutingServiceImpl extends ServiceImpl<RoutingMapper, Routing> implements RoutingService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "approval_status",
            "approved_at",
            "approved_by",
            "bom_id",
            "created_at",
            "created_by",
            "effective_date",
            "expiry_date",
            "is_enabled",
            "is_primary",
            "line_no",
            "material_code",
            "material_id",
            "material_name",
            "remark",
            "routing_code",
            "routing_id",
            "routing_name",
            "routing_version",
            "status",
            "total_process_count",
            "total_work_hours",
            "updated_at",
            "updated_by"
        );

    private final RoutingOperationMapper operationMapper;

    private final ApprovalGateHelper approvalGateHelper;

    /** 工序编号（单据类型 ROUTING_OP）：工序编码一律自动生成，与全站"编号默认自动生成"同口径 */
    private final SysSerialNoConfigService serialNoConfigService;

    /** 2026-11-26 多物料/多分类绑定 */
    private final RoutingMaterialMapper routingMaterialMapper;
    private final RoutingMaterialCategoryMapper routingCategoryMapper;
    /** 2026-11-26 工序自定义字段定义 */
    private final RoutingOperationFieldMapper operationFieldMapper;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper materialCategoryMapper;
    private final CustomFieldService customFieldService;

    /** 工序类型取值（唯一事实来源，前端下拉同口径） */
    public static final String[] OPERATION_TYPES = {"状态类型", "数量类型", "入库类型", "金额类型"};

    @Override
    @Transactional
    public void saveRoutingWithOperations(Routing routing, List<RoutingOperation> operations) {
        boolean isNew = routing.getRoutingId() == null;

        if (routing.getRoutingCode() == null || routing.getRoutingCode().isBlank()) {
            routing.setRoutingCode(generateRoutingCode());
        }
        // 工艺路线是「主档」不是「单据」（2026-09-23 用户口径）：
        // 状态只有 启用/停用，不再有 草稿/待审核/已生效，也不写审批状态（approval_status 列保留不删，仅不再消费）
        if (routing.getStatus() == null || routing.getStatus().isBlank()) routing.setStatus("启用");
        if (routing.getIsPrimary() == null) routing.setIsPrimary((short) 1);
        routing.setIsEnabled("启用".equals(routing.getStatus()) ? (short) 1 : (short) 0);
        if (routing.getTotalWorkHours() == null) routing.setTotalWorkHours(BigDecimal.ZERO);
        if (routing.getTotalProcessCount() == null) routing.setTotalProcessCount(0);

        // 自动计算 total_process_count 和 total_work_hours
        recalculateRouting(routing, operations);

        // 【2026-11-26】适用物料：前端传了 materialIds 才覆盖；老客户端没传则保留既有绑定
        List<Long> materialIds = routing.getMaterialIds();
        boolean materialProvided = materialIds != null;
        if (!materialProvided && isNew) {
            materialIds = routing.getMaterialId() != null
                    ? new ArrayList<>(List.of(routing.getMaterialId())) : new ArrayList<>();
            materialProvided = true;
        }
        if (materialProvided) {
            List<Long> cleaned = new ArrayList<>();
            for (Long mid : materialIds) {
                if (mid != null && !cleaned.contains(mid)) cleaned.add(mid);
            }
            materialIds = cleaned;
            // 主表保留「第一个物料」为兼容字段：BOM 带出、工单取主工艺路线等老逻辑继续可用
            applyLegacyMaterialColumns(routing, materialIds);
        }
        List<Long> categoryIds = routing.getCategoryIds();
        boolean categoryProvided = categoryIds != null;

        if (isNew) {
            save(routing);
        } else {
            // 编辑: 先按 routing_id 删除旧明细, 再更新主单
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            if (operations != null) {
                operationMapper.delete(new QueryWrapper<RoutingOperation>().eq("routing_id", routing.getRoutingId()));
            }
            updateById(routing);
        }

        if (materialProvided) {
            syncRoutingMaterials(routing.getRoutingId(), materialIds);
        }
        if (categoryProvided) {
            List<Long> cleaned = new ArrayList<>();
            for (Long cid : categoryIds) {
                if (cid != null && !cleaned.contains(cid)) cleaned.add(cid);
            }
            syncRoutingCategories(routing.getRoutingId(), cleaned);
        }

        if (operations != null && !operations.isEmpty()) {
            // 第一遍：行号重排（按提交顺序 (i+1)*10）+ 默认值补齐 + 金额按类型收口。
            // 父工序引用由前端按「保存后的行号空间」提交（第 i 行 = (i+1)*10），
            // 因此这里重排行号后，父引用天然跟着一起重映射，增删/调序都不会指错。
            int lineNo = 10;
            Set<Integer> lineNos = new HashSet<>();
            for (RoutingOperation op : operations) {
                op.setOperationId(null);
                op.setRoutingId(routing.getRoutingId());
                op.setLineNo(lineNo);
                lineNos.add(lineNo);
                // 【2026-11-26】工序稳定标识：前端新行会带 UUID；老数据/未带时后端补一个，
                // 保存是全删全插，工序自定义字段绑这个 key 才能跨保存保留
                if (op.getOperationKey() == null || op.getOperationKey().isBlank()) {
                    op.setOperationKey("ro_" + UUID.randomUUID().toString().replace("-", ""));
                }
                if (op.getBatchSize() == null) op.setBatchSize(1);
                if (op.getWorkersRequired() == null) op.setWorkersRequired(1);
                if (op.getIsOutsourced() == null) op.setIsOutsourced((short) 0);
                if (op.getIsQcRequired() == null) op.setIsQcRequired((short) 0);
                if (op.getIsCritical() == null) op.setIsCritical((short) 0);
                // 工序编码一律自动生成（页面上是灰框只读；留空由后端补号）
                if (op.getOperationCode() == null || op.getOperationCode().isBlank()) {
                    op.setOperationCode(nextOperationCode());
                }
                // 工序类型必填（决定该工序点进去能做什么），默认「状态类型」= 改造前行为
                if (op.getOperationType() == null || op.getOperationType().isBlank()) {
                    op.setOperationType("状态类型");
                }
                // 计划金额只对「金额类型」工序有意义：其它类型一律清空，避免脏数据
                if (!WorkOperationStatus.TYPE_AMOUNT.equals(op.getOperationType())) {
                    op.setPlanAmount(null);
                }
                if (op.getPieceRate() == null) op.setPieceRate(BigDecimal.ZERO);
                // 工序级「谁可以改负责人」（2026-10-09）：默认「全部人」= 升级前行为；非「指定角色」时清空角色清单
                if (op.getAssignScope() == null || op.getAssignScope().isBlank()) op.setAssignScope("全部人");
                if (!"指定角色".equals(op.getAssignScope())) op.setAssignRoleIds(null);
                calculateOperationHours(op);
                // 计税口径（2026-10-09）：按表头「计税方式」由「计件单价」换算 含税价 / 不含税价
                BigDecimal[] opPrices = convertPriceByTaxType(op.getPieceRate(), null,
                        resolveTaxType(routing.getTaxCalcMethod()));
                op.setPriceWithTax(opPrices[0]);
                op.setPriceWithoutTax(opPrices[1]);
                lineNo += 10;
            }
            // 第二遍：父子工序校验（父必须在本路线内 / 不得自引用 / 不得成环）；跨工艺路线的引用在此被拦
            assertOperationParentsValid(operations, lineNos);
            // 第三遍：全部校验通过后才落库，避免校验失败留下半截明细
            for (RoutingOperation op : operations) {
                operationMapper.insert(op);
            }
            // 【2026-11-26】每道工序自己的自定义字段定义（前端传了 fieldDefs 才覆盖）
            syncOperationFields(routing.getRoutingId(), operations);
        }

        // 口径统一（2026-09-21 用户指令「按标准来」）：保存=草稿，保存链路里绝不自动审核。
        // 历史写法是「免审批时保存后自动审核生效」，导致用户只点一次保存就已入账/已生效、且不能再修改，
        // 与全站口径「保存=草稿、提交才生效」冲突。生效动作一律交给提交侧（免审批时提交即生效）。

        log.info("工艺路线{}: {} 工序明细{}项",
                isNew ? "新增" : "编辑", routing.getRoutingCode(),
                operations != null ? operations.size() : 0);
    }

    /**
     * 【第 13 期】父子工序引用校验（唯一入口，保存工艺路线时执行）。
     *
     * <p>三道闸门，全部不过关即整单拒绝（守则 11：状态/关系迁移只允许由统一入口执行）：</p>
     * <ol>
     *   <li><b>父必须存在</b>：父工序行号必须落在本次提交的行号集合内
     *       （行号集合由本路线重排产生 → 天然拦住"指向别的工艺路线/别的成品"的引用）；</li>
     *   <li><b>不得自引用</b>：工序不能把自己设为父；</li>
     *   <li><b>不得成环</b>：沿父链向上走，一旦重复访问即判定循环（任意层级都覆盖）。</li>
     * </ol>
     *
     * @param operations 本次提交的工序（行号已重排完成）
     * @param lineNos    本次提交产生的合法行号集合
     */
    private void assertOperationParentsValid(List<RoutingOperation> operations, Set<Integer> lineNos) {
        Map<Integer, Integer> parentOf = new HashMap<>();
        for (RoutingOperation op : operations) {
            Integer parent = op.getParentLineNo();
            if (parent == null) continue;
            if (!lineNos.contains(parent)) {
                throw new BusinessException("工序[" + safeName(op) + "]选择的父工序不在本工艺路线内，"
                        + "父子工序必须属于同一条工艺路线（同一个成品）");
            }
            if (parent.equals(op.getLineNo())) {
                throw new BusinessException("工序[" + safeName(op) + "]不能把自己设为父工序");
            }
            parentOf.put(op.getLineNo(), parent);
        }
        for (Integer start : parentOf.keySet()) {
            Set<Integer> seen = new HashSet<>();
            Integer cur = start;
            while (cur != null) {
                if (!seen.add(cur)) {
                    throw new BusinessException("工序[" + start + "]的父子关系形成循环，请检查「父工序」设置");
                }
                cur = parentOf.get(cur);
            }
        }
    }

    /** 工序名称（为空时用行号兜底，保证报错信息能定位到具体那一行） */
    private String safeName(RoutingOperation op) {
        return (op.getOperationName() == null || op.getOperationName().isBlank())
                ? ("行号" + op.getLineNo()) : op.getOperationName();
    }

    /** 计算单行工序总工时: setup + process + wait + move */
    private void calculateOperationHours(RoutingOperation op) {
        BigDecimal setup = op.getSetupHours() != null ? op.getSetupHours() : BigDecimal.ZERO;
        BigDecimal process = op.getProcessHours() != null ? op.getProcessHours() : BigDecimal.ZERO;
        BigDecimal wait = op.getWaitHours() != null ? op.getWaitHours() : BigDecimal.ZERO;
        BigDecimal move = op.getMoveHours() != null ? op.getMoveHours() : BigDecimal.ZERO;
        op.setSetupHours(setup);
        op.setProcessHours(process);
        op.setWaitHours(wait);
        op.setMoveHours(move);
        op.setTotalHours(setup.add(process).add(wait).add(move));
    }

    // 计税口径常量（与销售订单试点同一口径）：1=含税价、2=不含税价（默认）、3=不计税
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    /**
     * 表头「计税方式」归一：含税价=1、不含税价=2（默认）、不计税=3；兼容历史值 价内税→含税价、价外税→不含税价。
     */
    private int resolveTaxType(String method) {
        if (method == null || method.isBlank()) return TAX_TYPE_EXCLUSIVE;
        String v = method.trim();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    /** 按表头「计税方式」把「计件单价」换算成 含税价 / 不含税价（返回值 [含税价, 不含税价]） */
    private BigDecimal[] convertPriceByTaxType(BigDecimal pieceRate, BigDecimal taxRate, int taxType) {
        BigDecimal price = pieceRate != null ? pieceRate : BigDecimal.ZERO;
        BigDecimal rate = taxRate != null ? taxRate : BigDecimal.ZERO;
        BigDecimal factor = BigDecimal.ONE.add(rate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        BigDecimal withTax;
        BigDecimal withoutTax;
        if (taxType == TAX_TYPE_INCLUSIVE) {
            withTax = price;
            withoutTax = factor.compareTo(BigDecimal.ZERO) != 0
                    ? price.divide(factor, 6, RoundingMode.HALF_UP) : price;
        } else if (taxType == TAX_TYPE_NO_TAX) {
            withTax = price;
            withoutTax = price;
        } else {
            withoutTax = price;
            withTax = price.multiply(factor);
        }
        return new BigDecimal[]{
                withTax.setScale(6, RoundingMode.HALF_UP),
                withoutTax.setScale(6, RoundingMode.HALF_UP)
        };
    }

    /** 计算主单总工时和工序总数: 各工序 totalHours 之和, 工序数量 */
    private void recalculateRouting(Routing routing, List<RoutingOperation> operations) {
        BigDecimal totalWorkHours = BigDecimal.ZERO;
        int processCount = 0;
        if (operations != null) {
            for (RoutingOperation op : operations) {
                calculateOperationHours(op);
                totalWorkHours = totalWorkHours.add(op.getTotalHours());
                processCount++;
            }
        }
        routing.setTotalWorkHours(totalWorkHours);
        routing.setTotalProcessCount(processCount);
    }

    @Override
    public PageResult<Routing> pageQuery(int current, int size, String keyword, String status,
                                         String startDate, String endDate, String sortBy, String sortOrder) {
        QueryWrapper<Routing> wrapper = new QueryWrapper<>();
        // 关键词多列强制成组（平铺 or() 会让命中第一列的行绕过状态筛选与数据权限）
        com.erp.common.query.ListQuerySupport.applyKeywordGroup(wrapper, keyword, "routing_code", "routing_name");
        if (status != null && !status.isBlank()) {
            // 旧值兼容（2026-09-23 工艺路线从"单据"降为"主档"）：
            // 老前端/老书签仍可能传 草稿 / 待审核 / 已生效，统一映射到新口径 启用 / 停用
            wrapper.eq("status", normalizeStatus(status));
        }
        // 日期区间：本页无「单据日期」，日期维度 = 生效日期 effective_date
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "effective_date", startDate, endDate);
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, Routing.class);
        Page<Routing> page = new Page<>(current, size);
        Page<Routing> result = page(page, wrapper);
        // 【2026-11-26】列表「物料」列显示绑定的多个物料
        fillBindings(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public Routing getDetail(Long id) {
        Routing routing = getById(id);
        if (routing == null) throw new BusinessException("工艺路线不存在");
        List<RoutingOperation> operations = operationMapper.selectList(
                new QueryWrapper<RoutingOperation>().eq("routing_id", id).orderByAsc("line_no"));
        // 【第 13 期】回填「父工序名称」：父引用按 line_no 在本路线内解析，列表可直接显示而无需前端再查
        Map<Integer, String> nameByLine = new HashMap<>();
        for (RoutingOperation op : operations) {
            if (op.getLineNo() != null) nameByLine.put(op.getLineNo(), op.getOperationName());
        }
        for (RoutingOperation op : operations) {
            if (op.getParentLineNo() != null) {
                op.setParentOperationName(nameByLine.get(op.getParentLineNo()));
            }
        }
        // 【2026-11-26】每道工序自己的自定义字段定义
        List<RoutingOperationField> defs = operationFieldMapper.selectList(
                new QueryWrapper<RoutingOperationField>().eq("routing_id", id).orderByAsc("sort_no"));
        // 回填镜像字段 key：前端据此作为值的键（工单/打印同一口径）
        Map<Long, String> fieldKeyById = new HashMap<>();
        for (CustomField cf : customFieldService.listEnabledByEntity("work_order_operation")) {
            fieldKeyById.put(cf.getFieldId(), cf.getFieldKey());
        }
        for (RoutingOperationField f : defs) {
            f.setCustomFieldKey(f.getCustomFieldId() == null ? null : fieldKeyById.get(f.getCustomFieldId()));
        }
        Map<String, List<RoutingOperationField>> defsByKey = new HashMap<>();
        for (RoutingOperationField f : defs) {
            defsByKey.computeIfAbsent(f.getOperationKey() == null ? "" : f.getOperationKey(),
                    k -> new ArrayList<>()).add(f);
        }
        for (RoutingOperation op : operations) {
            op.setFieldDefs(defsByKey.getOrDefault(
                    op.getOperationKey() == null ? "" : op.getOperationKey(), new ArrayList<>()));
        }
        routing.setOperations(operations);
        // 【2026-11-26】适用物料/分类
        fillBindings(Collections.singletonList(routing));
        return routing;
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        // 兼容保留（老前端 / 老书签仍可能调）：工艺路线已从"单据"降为"主档"，不再走审核
        Routing routing = getById(id);
        if (routing == null) throw new BusinessException("工艺路线不存在");
        throw new BusinessException("工艺路线已改为「启用/停用」，不再需要审核；请在列表点「启用」或「停用」");
    }

    @Override
    @Transactional
    public void enable(Long id, String operator) {
        Routing routing = getById(id);
        if (routing == null) throw new BusinessException("工艺路线不存在");
        if ("启用".equals(routing.getStatus())) throw new BusinessException("该工艺路线已是启用状态");
        routing.setStatus("启用");
        routing.setIsEnabled((short) 1);
        routing.setUpdatedBy(operator);
        updateById(routing);
        log.info("工艺路线启用: {} 操作人: {}", routing.getRoutingCode(), operator);
    }

    @Override
    @Transactional
    public void disable(Long id, String operator) {
        Routing routing = getById(id);
        if (routing == null) throw new BusinessException("工艺路线不存在");
        if ("停用".equals(routing.getStatus())) throw new BusinessException("该工艺路线已是停用状态");
        routing.setStatus("停用");
        routing.setIsEnabled((short) 0);
        routing.setUpdatedBy(operator);
        updateById(routing);
        log.info("工艺路线停用: {} 操作人: {}", routing.getRoutingCode(), operator);
    }

    /** 状态旧值 → 新口径（启用 / 停用）；空值按停用处理（与存量迁移口径一致） */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "停用";
        return switch (status) {
            case "启用", "已生效", "APPROVED" -> "启用";
            default -> "停用";
        };
    }

    /**
     * 取下一个工序编码：优先走「设置中心 → 自动编号设置 → 工序(ROUTING_OP)」规则；
     * 规则缺失时退回同格式（OP-yyyyMMdd-####）的本地取号，保证工序永远能出号。
     */
    private String nextOperationCode() {
        return serialNoConfigService.generateOrDefault("ROUTING_OP", this::generateOperationCodeFallback);
    }

    /** 兜底取号：扫描 routing_operation 中同前缀的最大流水 +1（与编号规则格式保持一致） */
    private String generateOperationCodeFallback() {
        String prefix = "OP-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        List<RoutingOperation> rows = operationMapper.selectList(
                new QueryWrapper<RoutingOperation>().select("operation_code").likeRight("operation_code", prefix));
        int seq = 0;
        for (RoutingOperation r : rows) {
            String code = r.getOperationCode();
            if (code == null || code.length() <= prefix.length()) continue;
            try {
                seq = Math.max(seq, Integer.parseInt(code.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // 异形编码不参与流水判定
            }
        }
        return prefix + String.format("%04d", seq + 1);
    }

    @Override
    @Transactional
    public void deleteRouting(Long id) {
        if (getById(id) == null) throw new BusinessException("工艺路线不存在");
        // 先删明细再删主单：本库 routing_operation 没有级联外键，
        // 只删主单会留下孤儿工序行（历史遗留：库里已有 routing_id 指向已删主单的孤儿行）
        operationMapper.delete(new QueryWrapper<RoutingOperation>().eq("routing_id", id));
        // 【2026-11-26】连带清理多物料/多分类绑定与工序字段定义
        routingMaterialMapper.delete(new QueryWrapper<RoutingMaterial>().eq("routing_id", id));
        routingCategoryMapper.delete(new QueryWrapper<RoutingMaterialCategory>().eq("routing_id", id));
        operationFieldMapper.delete(new QueryWrapper<RoutingOperationField>().eq("routing_id", id));
        removeById(id);
        log.info("工艺路线删除: id={}（含明细）", id);
    }

    // ==================== 2026-11-26 多物料 / 多分类 / 工序自定义字段 ====================

    /** 主表兼容列：material_id / material_code / material_name = 适用物料里的第一个 */
    private void applyLegacyMaterialColumns(Routing routing, List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            routing.setMaterialId(null);
            routing.setMaterialCode(null);
            routing.setMaterialName(null);
            return;
        }
        Long first = materialIds.get(0);
        routing.setMaterialId(first);
        Material m = materialMapper.selectById(first);
        routing.setMaterialCode(m != null ? m.getMaterialCode() : null);
        routing.setMaterialName(m != null ? m.getMaterialName() : null);
    }

    /** 适用物料：整单覆盖（先清后插） */
    private void syncRoutingMaterials(Long routingId, List<Long> materialIds) {
        routingMaterialMapper.delete(new QueryWrapper<RoutingMaterial>().eq("routing_id", routingId));
        if (materialIds == null) return;
        for (Long mid : materialIds) {
            RoutingMaterial rm = new RoutingMaterial();
            rm.setRoutingId(routingId);
            rm.setMaterialId(mid);
            rm.setCreatedAt(LocalDateTime.now());
            routingMaterialMapper.insert(rm);
        }
    }

    /** 适用分类：整单覆盖（先清后插） */
    private void syncRoutingCategories(Long routingId, List<Long> categoryIds) {
        routingCategoryMapper.delete(new QueryWrapper<RoutingMaterialCategory>().eq("routing_id", routingId));
        if (categoryIds == null) return;
        for (Long cid : categoryIds) {
            RoutingMaterialCategory rc = new RoutingMaterialCategory();
            rc.setRoutingId(routingId);
            rc.setCategoryId(cid);
            rc.setCreatedAt(LocalDateTime.now());
            routingCategoryMapper.insert(rc);
        }
    }

    /**
     * 工序自定义字段定义同步：只覆盖前端明确传了 fieldDefs 的工序。
     * 旧定义按「工序 key + 字段名」保留镜像字段 ID，改配置时历史值仍能对上。
     */
    private void syncOperationFields(Long routingId, List<RoutingOperation> operations) {
        boolean provided = operations.stream().anyMatch(o -> o.getFieldDefs() != null);
        if (!provided) return;
        List<RoutingOperationField> olds = operationFieldMapper.selectList(
                new QueryWrapper<RoutingOperationField>().eq("routing_id", routingId));
        Map<String, Long> oldMirror = new HashMap<>();
        for (RoutingOperationField f : olds) {
            oldMirror.put((f.getOperationKey() == null ? "" : f.getOperationKey()) + "|" + f.getFieldLabel(),
                    f.getCustomFieldId());
        }
        // 旧镜像按 ID 建索引：字段类型/选项变了就不能沿用旧 key，否则打印按旧类型格式化（下拉变原值、勾选变 1/0）
        Map<Long, CustomField> mirrorById = new HashMap<>();
        for (CustomField cf : customFieldService.listEnabledByEntity("work_order_operation")) {
            mirrorById.put(cf.getFieldId(), cf);
        }
        operationFieldMapper.delete(new QueryWrapper<RoutingOperationField>().eq("routing_id", routingId));
        for (RoutingOperation op : operations) {
            if (op.getFieldDefs() == null) continue;
            int sort = 0;
            for (RoutingOperationField def : op.getFieldDefs()) {
                if (def == null || def.getFieldLabel() == null || def.getFieldLabel().isBlank()) continue;
                RoutingOperationField row = new RoutingOperationField();
                row.setRoutingId(routingId);
                row.setOperationKey(op.getOperationKey());
                row.setFieldLabel(def.getFieldLabel().trim());
                row.setFieldType(def.getFieldType() == null || def.getFieldType().isBlank()
                        ? "text" : def.getFieldType());
                row.setFieldOptions(def.getFieldOptions());
                row.setIsRequired(def.getIsRequired() == null ? 0 : def.getIsRequired());
                row.setSortNo(sort++);
                row.setCreatedAt(LocalDateTime.now());
                Long mirrorId = oldMirror.get(op.getOperationKey() + "|" + row.getFieldLabel());
                if (mirrorId != null) {
                    CustomField oldMirrorField = mirrorById.get(mirrorId);
                    String oldOptions = oldMirrorField == null || oldMirrorField.getFieldOptions() == null
                            ? "" : oldMirrorField.getFieldOptions().trim();
                    String newOptions = row.getFieldOptions() == null ? "" : row.getFieldOptions().trim();
                    boolean sameType = oldMirrorField != null
                            && row.getFieldType().equals(oldMirrorField.getFieldType());
                    if (!sameType || !oldOptions.equals(newOptions)) {
                        // 类型或选项变了：换一条匹配的镜像字段，历史值留在旧 key 下不影响新填报
                        mirrorId = null;
                    }
                }
                if (mirrorId == null) mirrorId = ensureMirrorField(row);
                row.setCustomFieldId(mirrorId);
                operationFieldMapper.insert(row);
            }
        }
    }

    /** 把工序字段镜像到通用自定义字段（值表与打印共用该字段 key）；同名同类型同选项复用同一条 */
    private Long ensureMirrorField(RoutingOperationField def) {
        List<CustomField> exists = customFieldService.listEnabledByEntity("work_order_operation");
        for (CustomField cf : exists) {
            if (!def.getFieldLabel().equals(cf.getFieldLabel())) continue;
            if (!def.getFieldType().equals(cf.getFieldType())) continue;
            String a = def.getFieldOptions() == null ? "" : def.getFieldOptions().trim();
            String b = cf.getFieldOptions() == null ? "" : cf.getFieldOptions().trim();
            if (a.equals(b)) return cf.getFieldId();
        }
        CustomField cf = new CustomField();
        cf.setEntityType("work_order_operation");
        cf.setFieldKey("rof_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        cf.setFieldLabel(def.getFieldLabel());
        cf.setFieldType(def.getFieldType());
        cf.setFieldOptions(def.getFieldOptions());
        cf.setIsRequired(def.getIsRequired() == null ? 0 : def.getIsRequired().intValue());
        cf.setIsEnabled(1);
        cf.setIsListShow(0);
        cf.setIsDropShow(0);
        cf.setIsSearch(0);
        cf.setSortOrder(def.getSortNo() == null ? 0 : def.getSortNo());
        // 数字字段小数位：从 {"precision":n} 里取，打印与操作面板按同一精度显示
        if ("number".equals(cf.getFieldType()) && cf.getFieldOptions() != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"precision\"\\s*:\\s*(\\d+)").matcher(cf.getFieldOptions());
            if (m.find()) {
                try {
                    cf.setPrecisionVal(Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {
                    // 精度脏值 → 按默认 2 位
                }
            }
        }
        customFieldService.saveField(cf);
        return cf.getFieldId();
    }

    /** 批量回填适用物料/分类（列表、下拉共用） */
    public void fillBindings(List<Routing> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> routingIds = list.stream().map(Routing::getRoutingId)
                .filter(x -> x != null).collect(Collectors.toList());
        if (routingIds.isEmpty()) return;
        List<RoutingMaterial> rms = routingMaterialMapper.selectList(
                new QueryWrapper<RoutingMaterial>().in("routing_id", routingIds));
        List<RoutingMaterialCategory> rcs = routingCategoryMapper.selectList(
                new QueryWrapper<RoutingMaterialCategory>().in("routing_id", routingIds));
        Set<Long> materialIds = rms.stream().map(RoutingMaterial::getMaterialId).collect(Collectors.toSet());
        Set<Long> categoryIds = rcs.stream().map(RoutingMaterialCategory::getCategoryId).collect(Collectors.toSet());
        Map<Long, Material> materialMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            for (Material m : materialMapper.selectBatchIds(materialIds)) materialMap.put(m.getMaterialId(), m);
        }
        Map<Long, MaterialCategory> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            for (MaterialCategory c : materialCategoryMapper.selectBatchIds(categoryIds)) {
                categoryMap.put(c.getCategoryId(), c);
            }
        }
        Map<Long, List<RoutingMaterial>> byRouting = rms.stream()
                .collect(Collectors.groupingBy(RoutingMaterial::getRoutingId));
        Map<Long, List<RoutingMaterialCategory>> catByRouting = rcs.stream()
                .collect(Collectors.groupingBy(RoutingMaterialCategory::getRoutingId));
        for (Routing r : list) {
            List<RoutingMaterial> mine = byRouting.getOrDefault(r.getRoutingId(), Collections.emptyList());
            List<Map<String, Object>> mviews = new ArrayList<>();
            List<Long> mids = new ArrayList<>();
            for (RoutingMaterial rm : mine) {
                Material m = materialMap.get(rm.getMaterialId());
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("id", rm.getMaterialId());
                v.put("code", m != null ? m.getMaterialCode() : null);
                v.put("name", m != null ? m.getMaterialName() : null);
                mviews.add(v);
                mids.add(rm.getMaterialId());
            }
            r.setMaterialIds(mids);
            r.setMaterials(mviews);
            // 导出用展示文本：名称(编码)、名称(编码)
            StringBuilder names = new StringBuilder();
            for (Map<String, Object> v : mviews) {
                if (names.length() > 0) names.append("、");
                names.append(v.get("name") == null ? "" : v.get("name"));
                if (v.get("code") != null) names.append("(").append(v.get("code")).append(")");
            }
            r.setMaterialNames(names.toString());
            List<RoutingMaterialCategory> mineCats = catByRouting.getOrDefault(r.getRoutingId(), Collections.emptyList());
            List<Map<String, Object>> cviews = new ArrayList<>();
            List<Long> cids = new ArrayList<>();
            for (RoutingMaterialCategory rc : mineCats) {
                MaterialCategory c = categoryMap.get(rc.getCategoryId());
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("id", rc.getCategoryId());
                v.put("name", c != null ? c.getCategoryName() : null);
                cviews.add(v);
                cids.add(rc.getCategoryId());
            }
            r.setCategoryIds(cids);
            r.setCategories(cviews);
        }
    }

    /**
     * 某物料适用的工艺路线：直接绑定该物料，或绑定了该物料所在分类/上级分类；
     * 未绑定任何物料和分类的通用路线也返回（升级前老路线行为不变）。
     */
    @Override
    public List<Routing> listApplicableByMaterial(Long materialId) {
        List<Routing> all = list();
        if (materialId == null) return all;
        Material m = materialMapper.selectById(materialId);
        Set<Long> categoryPath = new LinkedHashSet<>();
        if (m != null && m.getCategoryId() != null) {
            Long cid = m.getCategoryId();
            int guard = 0;
            while (cid != null && guard++ < 20) {
                if (!categoryPath.add(cid)) break;
                MaterialCategory c = materialCategoryMapper.selectById(cid);
                cid = c != null ? c.getParentId() : null;
            }
        }
        List<Routing> result = new ArrayList<>();
        for (Routing r : all) {
            boolean noBinding = (r.getMaterialIds() == null || r.getMaterialIds().isEmpty())
                    && (r.getCategoryIds() == null || r.getCategoryIds().isEmpty());
            boolean direct = r.getMaterialIds() != null && r.getMaterialIds().contains(materialId);
            boolean byCat = false;
            if (r.getCategoryIds() != null) {
                for (Long cid : r.getCategoryIds()) {
                    if (categoryPath.contains(cid)) {
                        byCat = true;
                        break;
                    }
                }
            }
            if (noBinding || direct || byCat) result.add(r);
        }
        return result;
    }

    /** 列表接口统一带上适用物料/分类（非库字段），前端「物料」列与工单筛选直接可用 */
    @Override
    public List<Routing> list() {
        List<Routing> rows = super.list();
        fillBindings(rows);
        return rows;
    }

    @Override
    public String generateRoutingCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "RT-" + dateStr + "-";
        QueryWrapper<Routing> wrapper = new QueryWrapper<>();
        wrapper.likeRight("routing_code", prefix).orderByDesc("routing_code").last("LIMIT 1");
        Routing last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getRoutingCode() != null) {
            try {
                seq = Integer.parseInt(last.getRoutingCode().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }

    /**
     * 工艺路线里配置过的工序名（去重 + 中文排序）—— 质检模板「适用工序」下拉的候选来源之一。
     * 用户 2026-10-07 反馈"下拉没有自动带出工艺路线里面的工序"：这里把路线明细里的工序名全部收上来。
     */
    @Override
    public List<String> listAllOperationNames() {
        List<RoutingOperation> rows = operationMapper.selectList(
                new QueryWrapper<RoutingOperation>().select("DISTINCT operation_name").isNotNull("operation_name"));
        Collator collator = Collator.getInstance(Locale.CHINA);
        return rows.stream()
                .map(RoutingOperation::getOperationName)
                .filter(n -> n != null && !n.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .sorted(collator::compare)
                .collect(Collectors.toList());
    }
}
