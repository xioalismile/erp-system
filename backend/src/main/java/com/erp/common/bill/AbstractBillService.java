package com.erp.common.bill;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.approval.entity.ApprovalTask;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.approval.service.BillApprovalCallback;
import com.erp.config.BusinessException;
import com.erp.customfield.service.CustomFieldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全部单据Service的统一抽象基类。
 *
 * <p>统一了9个单据操作接口（金蝶/用友标准双终态状态机）：
 * <pre>
 *     保存草稿(宽松) → 提交审核(严格) ↔ 驳回(回草稿)
 *           ↓
 *       审核通过 ↔ 重置草稿(回草稿)
 *           ↓
 *      结案 ↔ 反结案    (终态1)
 *      作废 ↔ 反作废    (终态2，与结案互斥)
 *           ↓
 *      删除 (仅草稿/驳回)
 * </pre>
 * </p>
 *
 * <p>子类必须实现以下抽象字段访问钩子（由于各单据主键/状态列命名不统一）：
 * <ul>
 *     <li>{@link #getBillNo(Object)}  — 取单号用于日志/报错</li>
 *     <li>{@link #getId(Object)} / {@link #getStatus(Object)} / {@link #getCloseStatus(Object)}</li>
 *     <li>{@link #setStatus(Object, String)} — 写入 status 列（保留中文 label 写法兼容老表）</li>
 *     <li>{@link #setCloseStatus(Object, Integer)}</li>
 *     <li>作废/结案/审核 相关8个审计字段写入钩子：setClosed*, setVoided*, setApproved*, setBillBeforeStatus</li>
 *     <li>{@link #validateStrictSubmit(Object)}  — 提交审核前的严格业务校验（必填/数量/关联数据/库存充足等）</li>
 * </ul>
 * </p>
 *
 * <p>子类可选实现：
 * <ul>
 *     <li>{@link DownstreamBillValidator} — 作废/重置草稿/删除 前的下游引用检查</li>
 *     <li>{@link BillImpactHandler} — 各状态流转的业务影响(库存/应收/信用/MRP)</li>
 * </ul>
 * </p>
 *
 * @param <M> MyBatis Mapper
 * @param <B> 主实体类型
 */
@Slf4j
public abstract class AbstractBillService<M extends BaseMapper<B>, B>
        extends ServiceImpl<M, B>
        implements DownstreamBillValidator<B>, BillImpactHandler<B>, BillApprovalCallback {

    // =============== 子类必须实现：字段访问抽象 ===============

    protected abstract String getBillNo(B bill);
    protected abstract Object getId(B bill);
    protected abstract String getStatus(B bill);
    protected abstract void setStatus(B bill, String statusLabel);
    protected abstract Integer getCloseStatus(B bill);
    protected abstract void setCloseStatus(B bill, Integer closeStatus);

    protected abstract void setApprovedBy(B bill, String approver);
    protected abstract void setApprovedAt(B bill, LocalDateTime approvedAt);
    protected abstract void setApprovalStatus(B bill, String approvalStatusLabel);

    protected abstract void setClosedBy(B bill, String user);
    protected abstract void setClosedAt(B bill, LocalDateTime t);
    protected abstract void setClosedReason(B bill, String reason);

    protected abstract void setBillBeforeStatus(B bill, Integer numericStatus);
    protected abstract Integer getBillBeforeStatus(B bill);
    protected abstract void setVoidedBy(B bill, String user);
    protected abstract void setVoidedAt(B bill, LocalDateTime t);
    protected abstract void setVoidReason(B bill, String reason);

    /**
     * 提交审核前的严格校验（非草稿态保存不触发此钩子）。
     * 子类需要在此抛出 {@link BusinessException} 包含精确错误消息。
     */
    protected abstract void validateStrictSubmit(B bill);

    /** 供调试/日志：单据类型中文名，如"销售订单" */
    protected abstract String getBillTypeLabel();

    /**
     * 审批开关接入钩子：返回设置中心「单据审批开关」使用的英文单据类型编码（如 SALE_ORDER）。
     * 默认 null = 未接入开关体系 → 按免审批处理（提交即生效）。
     * 子类覆盖示例：{@code @Override protected String getApprovalBillType() { return "SALE_ORDER"; }}
     */
    protected String getApprovalBillType() { return null; }

    /** 审批开关网关（Spring 注入到子类实例） */
    @Autowired
    protected ApprovalGateHelper approvalGateHelper;

    /**
     * 审批运行时引擎（提交时创建审批任务）。
     * @Lazy 打破循环依赖：审批引擎按 BillApprovalCallback 回调全部单据Service，
     * 单据Service又需要审批引擎启动任务 —— 延迟代理避免启动期循环创建失败。
     */
    @Lazy
    @Autowired
    protected ApprovalRuntimeService approvalRuntimeService;

    /** 自定义字段服务（构建审批条件 billData 用） */
    @Autowired
    protected CustomFieldService customFieldService;

    /**
     * 整单删除级联服务（2026-09-19）：
     * 删主表时一并清理明细/从表，杜绝"删了主表、明细残留"的孤儿数据（用户硬要求）。
     */
    @Autowired
    protected BillCascadeDeleteService billCascadeDeleteService;

    /** 当前单据类型是否需要人工审批（全局关 或 单据级关 = 免审批，提交即生效） */
    protected final boolean needApprovalNow() {
        String billType = getApprovalBillType();
        if (billType == null || billType.isBlank()) return false;
        return approvalGateHelper.needApproval(billType);
    }

    // =============== 下游校验默认实现（空=无下游约束） ===============
    @Override public List<String> listApprovedDownstreamBills(B bill) { return List.of(); }

    // =============== 统一工具：状态机断言 ===============

    protected final void assertNotFinalized(String opName, B bill) {
        Object status = getStatus(bill);
        Object cs = getCloseStatus(bill);
        if (BillStatusUtils.isFinalized(status, cs)) {
            String csLabel = CloseStatusEnum.isClosed(objectToInt(cs)) ? "已结案" : "";
            String voidLabel = BillStatusUtils.isVoided(status) ? "已作废" : "";
            String fin = (voidLabel + csLabel).isEmpty() ? "已完结" : voidLabel + csLabel;
            throw new BusinessException(getBillTypeLabel() + "[" + getBillNo(bill) + "]"
                    + fin + "，不能" + opName);
        }
    }

    protected static int objectToInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString().trim()); } catch (Exception e) { return 0; }
    }

    // =============== 1. 保存草稿（宽松，必填允许为空） ===============

    /**
     * 保存草稿（宽松模式，不触发严格校验）。
     * 说明：按用户要求，保存=存为草稿状态，任何必填项允许留空。
     */
    @Transactional
    public void saveDraft(B bill) {
        if (getStatus(bill) == null || getStatus(bill).toString().isBlank()) {
            setStatus(bill, BillStatusEnum.DRAFT.getLabel());
        }
        if (getCloseStatus(bill) == null) {
            setCloseStatus(bill, CloseStatusEnum.OPEN.getCode());
        }
        boolean isNew = getId(bill) == null;
        if (isNew) save(bill); else updateById(bill);
        log.info("{}保存草稿: {}  id={}", getBillTypeLabel(), getBillNo(bill), getId(bill));
    }

    // =============== 2. 提交审核（严格校验，保存+提交一步到位） ===============

    /**
     * 提交审核（严格校验一步到位，用户要求"提交不一定要先保存"）。
     *
     * 执行顺序：
     *  1. 状态校验：必须草稿或驳回(isDraft)
     *  2. 严格校验：调用子类 validateStrictSubmit
     *  3. 审批开关判断（设置中心-单据审批开关）：
     *     - 免审批（全局关/单据关/未接入）→ 直接置「已生效」生效，并触发 onApproved 业务影响
     *     - 启用审批 → 写入 status/approval_status=待审核
     *  4. 持久化
     */
    @Transactional
    public void submitForApproval(B bill, String submitter) {
        int statusNum = BillStatusUtils.toNumber(getStatus(bill));
        if (statusNum != BillStatusEnum.DRAFT.getCode()) {
            throw new BusinessException("只有草稿状态可提交审核，当前状态: "
                    + BillStatusUtils.toString(statusNum));
        }
        validateStrictSubmit(bill);
        boolean needApproval = needApprovalNow();
        if (needApproval) {
            setStatus(bill, BillStatusEnum.PENDING.getLabel());
            setApprovalStatus(bill, BillStatusEnum.PENDING.getLabel());
        } else {
            // 免审批：提交即生效（设置中心承诺「关闭审批后单据创建后直接生效」）
            setStatus(bill, BillStatusEnum.APPROVED.getLabel());
            setApprovalStatus(bill, BillStatusEnum.APPROVED.getLabel());
            // 用户 2026-10-28 口径：免审批单据没有人工审核人，审核人留空，不写任何系统占位
            setApprovedBy(bill, null);
            setApprovedAt(bill, LocalDateTime.now());
        }
        boolean isNew = getId(bill) == null;
        if (isNew) save(bill); else updateById(bill);
        if (!needApproval) {
            onApproved(bill); // 免审批直接生效：释放正向业务影响（库存/应收/信用/MRP等）
            log.info("{}免审批自动生效: {}  id={}", getBillTypeLabel(), getBillNo(bill), getId(bill));
        } else {
            // 启用审批：创建审批任务（首节点待办）。
            // 无可用流程时 startApproval 抛错 → 整个提交事务回滚（单据留在草稿），
            // 避免出现"待审核却无待办"的卡死单据。
            approvalRuntimeService.startApproval(getApprovalBillType(), (Long) getId(bill), getBillNo(bill), submitter, buildBillData(bill));
            log.info("{}提交审核并创建审批任务: {}  id={}", getBillTypeLabel(), getBillNo(bill), getId(bill));
        }
    }

    /**
     * 构建审批条件判断用的单据数据快照（billData）。
     * 包含：
     *  - 表头全部字段（camelCase 与 snake_case 双写，条件配置可用任一格式）
     *  - 自定义字段值（key = 自定义字段 fieldKey，如 cf_xxx）
     * 审批流程的 conditionExpr / ApprovalCondition 均基于此数据匹配。
     */
    protected Map<String, Object> buildBillData(B bill) {
        Map<String, Object> billData = new HashMap<>();
        // 1) 表头字段：沿类层次反射全部字段（含父类），跳过集合类字段
        for (Class<?> c = bill.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                if (List.class.isAssignableFrom(ft) || Map.class.isAssignableFrom(ft)) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(bill);
                    billData.put(f.getName(), v);                                  // camelCase（如 contactPerson）
                    billData.put(camelToSnake(f.getName()), v);                    // snake_case（如 contact_person）
                } catch (Exception ignore) {
                    // 反射读取失败的字段跳过，不阻断审批提交
                }
            }
        }
        // 2) 自定义字段值：entityType = billType 小写（与自定义字段配置口径一致，如 SALE_ORDER → sale_order）
        try {
            String billType = getApprovalBillType();
            Object id = getId(bill);
            if (billType != null && id != null) {
                billData.putAll(customFieldService.getFieldValues(billType.toLowerCase(), (Long) id));
            }
        } catch (Exception e) {
            // 读取失败必须如实抛出（审批快照缺自定义字段数据 = 提交了残缺单据），留痕后抛出便于排障
            log.warn("加载自定义字段值失败: billType={}, id={}, err={}",
                    getApprovalBillType(), getId(bill), e.getMessage());
            throw new BusinessException("读取单据自定义字段失败：" + e.getMessage());
        }
        return billData;
    }

    /** camelCase → snake_case（contactPerson → contact_person） */
    protected static String camelToSnake(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        for (char ch : name.toCharArray()) {
            if (Character.isUpperCase(ch)) sb.append('_').append(Character.toLowerCase(ch));
            else sb.append(ch);
        }
        return sb.toString();
    }

    // =============== 审批引擎回调桥接（BillApprovalCallback） ===============

    /** 审批任务按 billType 路由回本单据：与设置中心「单据审批开关」使用同一编码 */
    @Override
    public String getBillType() {
        return getApprovalBillType();
    }

    /** 审批流程全部节点通过 → 单据置「已生效」并触发业务影响（复用统一审核入口，含状态机断言） */
    @Override
    public void onApprovalApproved(Long billId, String approver, String comment) {
        approve(billId, approver);
    }

    /** 审批驳回 → 单据退回「草稿」（复用统一驳回入口） */
    @Override
    public void onApprovalRejected(Long billId, String rejector, String comment) {
        reject(billId, rejector, comment);
    }

    /** 兼容老签名：无提交人时免审批通过记录为系统 */
    @Transactional
    public void submitForApproval(B bill) {
        submitForApproval(bill, null);
    }

    /** 老接口兼容：按ID提交（内部先load再走上面 submitForApproval(bill)，子类可override后调用） */
    @Transactional
    public void submitForApprovalById(Object id) {
        submitForApprovalById(id, null);
    }

    /** 该单据是否存在进行中的审批任务（未接入审批开关体系的单据恒为 false） */
    private boolean hasRunningApprovalTask(Object id) {
        String billType = getApprovalBillType();
        if (billType == null || billType.isBlank() || id == null) return false;
        try {
            return approvalRuntimeService.getRunningTask(billType, (Long) id) != null;
        } catch (Exception e) {
            // 查询失败必须如实抛出：吞掉返回 false 会放行重复提交审批（留痕后抛出便于排障）
            log.warn("{}检查进行中审批任务失败: {}", billType, e.getMessage());
            throw new BusinessException("检查进行中审批任务失败：" + e.getMessage());
        }
    }

    /** 按ID提交（携带提交人，用于免审批自动通过时的审计字段） */
    @Transactional
    public void submitForApprovalById(Object id, String submitter) {
        B bill = getById((Long) id);
        if (bill == null) throw new BusinessException(getBillTypeLabel() + "不存在");
        submitForApproval(bill, submitter);
    }

    // =============== 3. 审核通过 ===============

    @Transactional
    public void approve(Object id, String approver) {
        B bill = getById((Long) id);
        if (bill == null) throw new BusinessException(getBillTypeLabel() + "不存在");
        int statusNum = BillStatusUtils.toNumber(getStatus(bill));
        // 前端审核按钮在「草稿」状态也显示（bill-status.js canApprove 含草稿）：
        // 允许从草稿直接审核 = 提交+审核一步到位，但必须先过严格校验保证数据完整
        if (statusNum == BillStatusEnum.DRAFT.getCode()) {
            validateStrictSubmit(bill);
            statusNum = BillStatusEnum.PENDING.getCode();
        }
        if (statusNum != BillStatusEnum.PENDING.getCode()) {
            throw new BusinessException("只有待审核状态可审核，当前状态: " + BillStatusUtils.toString(statusNum));
        }
        assertNotFinalized("审核", bill);
        setStatus(bill, BillStatusEnum.APPROVED.getLabel());
        setApprovalStatus(bill, BillStatusEnum.APPROVED.getLabel());
        setApprovedBy(bill, BillStatusUtils.normalizeApprover(approver));
        setApprovedAt(bill, LocalDateTime.now());
        updateById(bill);
        onApproved(bill);
        log.info("{}审核通过: {}  审核人: {}", getBillTypeLabel(), getBillNo(bill), approver);
    }

    // =============== 4. 驳回（回到草稿） ===============

    @Transactional
    public void reject(Object id, String rejector, String reason) {
        B bill = getById((Long) id);
        if (bill == null) throw new BusinessException(getBillTypeLabel() + "不存在");
        // 审批引擎防护：已有进行中的审批任务时禁止绕过引擎直驳。
        // 2026-11-19 放行口径：当前用户就是该任务的审批人（当前节点指定人/any候选人）时，
        // 直接走审批引擎驳回（任务完结、单据经回调退回草稿），不再拦截；
        // 非审批人仍拦截，提示就一句「您不是该单据的审批人，无法驳回」（2026-11-19 用户定稿：不带引导不带审批人名）。
        if (hasRunningApprovalTask(id)) {
            ApprovalTask running = approvalRuntimeService.getRunningTask(getApprovalBillType(), (Long) id);
            if (running != null && approvalRuntimeService.isTaskApprover(running, rejector)) {
                approvalRuntimeService.reject(running.getTaskId(), rejector, reason);
                return; // 引擎驳回已回写单据状态（退回草稿），此处无需再改
            }
            throw new BusinessException("您不是该单据的审批人，无法驳回");
        }
        int statusNum = BillStatusUtils.toNumber(getStatus(bill));
        if (statusNum != BillStatusEnum.PENDING.getCode()) {
            throw new BusinessException("只有待审核状态可驳回，当前状态: " + BillStatusUtils.toString(statusNum));
        }
        setStatus(bill, BillStatusEnum.DRAFT.getLabel());
        setApprovalStatus(bill, "驳回");
        updateById(bill);
        log.info("{}驳回: {}  驳回人: {}  原因: {}",
                getBillTypeLabel(), getBillNo(bill), rejector, reason);
    }

    // =============== 5. 重置草稿（已生效 → 草稿） ===============

    @Transactional
    public void reverseApprove(Object id, String operator) {
        B bill = getById((Long) id);
        if (bill == null) throw new BusinessException(getBillTypeLabel() + "不存在");
        int statusNum = BillStatusUtils.toNumber(getStatus(bill));
        if (statusNum != BillStatusEnum.APPROVED.getCode()) {
            throw new BusinessException("只有已生效状态可重置草稿，当前状态: " + BillStatusUtils.toString(statusNum));
        }
        assertNotFinalized("重置草稿", bill);
        // 重置草稿需校验：没有下游已生效单据（否则数据不一致）
        List<String> downstream = listApprovedDownstreamBills(bill);
        if (!downstream.isEmpty()) {
            throw new BusinessException(getBillTypeLabel() + "[" + getBillNo(bill) + "]已被下游单据引用: "
                    + String.join("、", downstream) + "，请先作废下游后再重置草稿");
        }
        setStatus(bill, BillStatusEnum.DRAFT.getLabel());
        setApprovalStatus(bill, BillStatusEnum.DRAFT.getLabel());
        // 钩子先执行：库存冲回/勾稽回滚等钩子需要读取「原审核人」做流水留痕，
        // 若先置空 approved_by，钩子里拿到的操作人永远是 null（2026-11-09 修复）。
        onReverseApproved(bill);
        setApprovedBy(bill, null);
        setApprovedAt(bill, null);
        updateById(bill);
        // MyBatis-Plus 默认忽略 null 更新：上面 setApprovedBy(null)+updateById 清不掉旧值，
        // 必须再走一次显式 UPDATE 置空（否则「重置草稿」后仍显示上一次的审核人/审核时间）。
        clearColumns(bill, "approved_by", "approved_at");
        log.info("{}重置草稿: {}  操作人: {}", getBillTypeLabel(), getBillNo(bill), operator);
    }

    /**
     * 显式把指定列置空（2026-11-09）。
     *
     * <p>MyBatis-Plus 的 updateById 会忽略 null 字段，导致「审核人/作废人/作废原因」这类
     * 需要清空的审计列清不掉。统一在基类用主键列 + 显式 SET NULL 兜底，
     * 各单据不必再各写一份（历史单据里已写的重复清空无副作用）。</p>
     */
    protected final void clearColumns(B bill, String... columns) {
        Object id = getId(bill);
        if (id == null || columns == null || columns.length == 0) return;
        try {
            com.baomidou.mybatisplus.core.metadata.TableInfo ti =
                    com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(getEntityClass());
            if (ti == null || ti.getKeyColumn() == null) return;
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<B> uw =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            uw.eq(ti.getKeyColumn(), id);
            for (String col : columns) {
                if (col != null && !col.isBlank()) uw.set(col, null);
            }
            update(uw);
        } catch (Exception e) {
            // 清空失败必须如实抛出回滚（否则会变成"重置后仍显示审核人"的静默问题），留痕后抛出便于排障
            log.warn("{}清空审计列失败 {}: {}", getBillTypeLabel(), getBillNo(bill), e.getMessage());
            throw new BusinessException("更新单据数据失败：" + e.getMessage());
        }
    }

    // =============== 6. 结案（终态1） ===============

    @Transactional
    public void close(Object id, String operator, String reason) {
        B bill = getById((Long) id);
        if (bill == null) throw new BusinessException(getBillTypeLabel() + "不存在");
        int statusNum = BillStatusUtils.toNumber(getStatus(bill));
        if (statusNum != BillStatusEnum.APPROVED.getCode()) {
            throw new BusinessException("只有已生效状态可结案，当前状态: " + BillStatusUtils.toString(statusNum));
        }
        if (CloseStatusEnum.isClosed(getCloseStatus(bill))) {
            throw new BusinessException(getBillTypeLabel() + "[" + getBillNo(bill) + "]已结案，不能重复结案");
        }
        setCloseStatus(bill, CloseStatusEnum.CLOSED.getCode());
        setClosedBy(bill, operator);
        setClosedAt(bill, LocalDateTime.now());
        setClosedReason(bill, reason);
        updateById(bill);
        onClosed(bill);
        log.info("{}结案: {}  操作人: {}  原因: {}",
                getBillTypeLabel(), getBillNo(bill), operator, reason);
    }

    // =============== 7. 反结案 ===============

    @Transactional
    public void reopen(Object id, String operator) {
        B bill = getById((Long) id);
        if (bill == null) throw new BusinessException(getBillTypeLabel() + "不存在");
        if (!CloseStatusEnum.isClosed(getCloseStatus(bill))) {
            throw new BusinessException("只有已结案状态可反结案");
        }
        setCloseStatus(bill, CloseStatusEnum.OPEN.getCode());
        setClosedBy(bill, operator);
        setClosedAt(bill, LocalDateTime.now());
        updateById(bill);
        onReopened(bill);
        log.info("{}反结案: {}  操作人: {}", getBillTypeLabel(), getBillNo(bill), operator);
    }

    // =============== 8. 作废（终态2，与结案互斥） ===============

    @Transactional
    public void voidBill(Object id, String operator, String reason) {
        B bill = getById((Long) id);
        if (bill == null) throw new BusinessException(getBillTypeLabel() + "不存在");
        if (BillStatusUtils.isVoided(getStatus(bill))) {
            throw new BusinessException(getBillTypeLabel() + "[" + getBillNo(bill) + "]已作废，不能重复作废");
        }
        if (CloseStatusEnum.isClosed(getCloseStatus(bill))) {
            throw new BusinessException(getBillTypeLabel() + "[" + getBillNo(bill) + "]已结案，如需作废请先反结案");
        }
        // 草稿态也允许作废；但作废前若有下游已生效单据，阻止
        List<String> downstream = listApprovedDownstreamBills(bill);
        if (!downstream.isEmpty()) {
            throw new BusinessException(getBillTypeLabel() + "[" + getBillNo(bill) + "]已被下游单据引用: "
                    + String.join("、", downstream) + "，请先作废下游后再作废");
        }
        // 记录作废前状态（数字code），供反作废恢复
        int beforeStatus = BillStatusUtils.toNumber(getStatus(bill));
        setBillBeforeStatus(bill, beforeStatus);
        setStatus(bill, BillStatusEnum.VOIDED.getLabel());
        setVoidedBy(bill, operator);
        setVoidedAt(bill, LocalDateTime.now());
        setVoidReason(bill, reason);
        updateById(bill);
        // 若作废前是已生效类，需做影响回收
        if (beforeStatus == BillStatusEnum.APPROVED.getCode()) {
            onVoided(bill);
        }
        log.info("{}作废: {}  作废前状态: {}  操作人: {}  原因: {}",
                getBillTypeLabel(), getBillNo(bill), BillStatusUtils.toString(beforeStatus), operator, reason);
    }

    // =============== 9. 反作废 ===============

    @Transactional
    public void unvoidBill(Object id, String operator) {
        B bill = getById((Long) id);
        if (bill == null) throw new BusinessException(getBillTypeLabel() + "不存在");
        if (!BillStatusUtils.isVoided(getStatus(bill))) {
            throw new BusinessException("只有已作废状态可反作废");
        }
        Integer before = getBillBeforeStatus(bill);
        // 若未记录 before，默认回草稿（容错）
        int target = (before == null) ? BillStatusEnum.DRAFT.getCode() : before;
        setStatus(bill, BillStatusUtils.toString(target));
        setBillBeforeStatus(bill, null);
        setVoidedBy(bill, null);
        setVoidedAt(bill, null);
        setVoidReason(bill, null);
        if (target == BillStatusEnum.APPROVED.getCode()) {
            setApprovalStatus(bill, BillStatusEnum.APPROVED.getLabel());
        } else if (target == BillStatusEnum.PENDING.getCode()) {
            setApprovalStatus(bill, BillStatusEnum.PENDING.getLabel());
        } else {
            setApprovalStatus(bill, BillStatusEnum.DRAFT.getLabel());
        }
        updateById(bill);
        // 同上：updateById 忽略 null → 作废人/作废时间/作废原因/作废前状态必须显式置空，
        // 否则「反作废」后单据上仍留着上一次的作废痕迹（2026-11-09 跨模块验收实锤）。
        clearColumns(bill, "voided_by", "voided_at", "void_reason", "bill_before_status");
        if (target == BillStatusEnum.APPROVED.getCode()) {
            onUnvoided(bill);
        }
        log.info("{}反作废: {}  恢复状态: {}  操作人: {}",
                getBillTypeLabel(), getBillNo(bill), BillStatusUtils.toString(target), operator);
    }

    // =============== 10. 删除（草稿直接删；已生效先对冲库存——2026-11-16 用户要求，标准ERP口径） ===============

    /**
     * 删除前的业务守卫钩子（默认无约束）。
     *
     * <p>删除已生效单据会先走 {@link #onReverseApproved(Object)} 冲回库存并清理衍生单据，
     * 因此各单据在「重置草稿 / 作废」里已有的前置守卫（如已生成发票、已付款应付单、质检自动退货单非草稿）
     * 必须在删除入口同样生效，否则删除会绕过守卫、把仍在被引用的衍生单据一并删掉。
     * 有守卫的单据覆盖本方法，并自行判断状态（草稿删除不受影响）。</p>
     */
    protected void validateBeforeDelete(B bill) { }

    @Transactional
    public void deleteBill(Object id) {
        B bill = getById((Long) id);
        if (bill == null) return; // 幂等
        validateBeforeDelete(bill);
        int statusNum = BillStatusUtils.toNumber(getStatus(bill));
        if (BillStatusEnum.isDraftClass(statusNum)) {
            // 草稿族（草稿/已驳回/已取消）：未过账或冲回已完成，无库存影响，直接删
        } else if (statusNum == BillStatusEnum.PENDING.getCode()) {
            throw new BusinessException("待审核单据不能删除（当前状态：" + BillStatusUtils.toString(statusNum) + "），请先驳回");
        } else if (statusNum == BillStatusEnum.VOIDED.getCode()) {
            throw new BusinessException("已作废单据不能删除（当前状态：" + BillStatusUtils.toString(statusNum) + "）");
        } else {
            // 已生效族（含历史已关闭/已取消子态 3~6）：删除前必须对冲库存 —— 走与重置草稿完全相同的冲回钩子；
            // 红冲产生的流水行随后由级联服务「按单据编号清库存流水」一并清掉，整单删除不留任何库存与流水痕迹
            onReverseApproved(bill);
            log.info("{}删除前库存对冲完成: {}  id={}", getBillTypeLabel(), getBillNo(bill), id);
        }
        List<String> downstream = listApprovedDownstreamBills(bill);
        if (!downstream.isEmpty()) {
            throw new BusinessException(getBillTypeLabel() + "[" + getBillNo(bill) + "]已被下游引用，不能删除");
        }
        // 【2026-09-19 用户要求：删除必须整单】同事务内先清理明细/从表，再删主表，不留孤儿明细
        int childRows = billCascadeDeleteService.deleteChildrenByEntity(getEntityClass(), id);
        removeById((Long) id);
        log.info("{}删除: {}  id={}  连带清理明细 {} 行", getBillTypeLabel(), getBillNo(bill), id, childRows);
    }
}
