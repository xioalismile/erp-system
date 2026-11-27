package com.erp.common.bill;

import com.erp.config.BusinessException;

import java.util.HashMap;
import java.util.Map;

/**
 * 字符串状态 ↔ 数字枚举的双向兼容工具（历史兼容：老表 status 为 VARCHAR，新改造统一走数字语义）。
 *
 * 用法：
 *   BillStatusUtils.toNumber(form.getStatus())  →  任何 String/Integer/Number/null 都返回 code
 *   BillStatusUtils.toString(0)                  →  "草稿"
 */
public final class BillStatusUtils {

    private static final Map<String, Integer> STR_TO_CODE = new HashMap<>();
    private static final Map<Integer, String> CODE_TO_STR = new HashMap<>();

    static {
        // 草稿(0)
        register("草稿", 0);
        register("", 0);
        register(null, 0);
        register("未审核", 0);
        register("驳回", 0);
        register("已驳回", 0);        // 驳回结果 = 可修改的草稿族（2026-10-09 补：此前漏登记，被当"已生效"处理，导致已驳回单据删不掉）
        register("已取消", 0);        // 有些模块已取消视作草稿，后续作废按 status=7

        // 待审核(1)
        register("待审核", 1);
        register("待审批", 1);
        register("审批中", 1);

        // 已生效(2) 及其子类（下达/执行中/完工/已确认/已关闭-非结案 都按"可重置草稿子类=2"处理）
        register("已生效", 2);
        register("已审核", 2);   // 历史别名（2026-09-19 终态改名前的存量数据/旧调用），勿删
        register("审批通过", 2);
        register("已确认", 2);
        register("已下达", 2);
        register("执行中", 2);
        register("已完工", 2);
        register("已关闭", 2);  // 关闭=业务终止但未结案仍允许反审

        // 作废(7)
        register("已作废", 7);
        register("作废", 7);

        // 英文枚举别名（2026-10-09 补）：财务等模块存在按 "DRAFT"/"PENDING" 比较状态的防御性代码，
        // 存量数据也可能存英文串；显式登记，避免被 toNumber 兜底成"已生效"。
        register("DRAFT", 0);
        register("PENDING", 1);
        register("APPROVED", 2);
        register("VOIDED", 7);
    }

    private static void register(String s, int code) {
        STR_TO_CODE.put(s == null ? "" : s, code);
        CODE_TO_STR.putIfAbsent(code, s == null || s.isEmpty() ? BillStatusEnum.DRAFT.getLabel() : s);
    }

    private BillStatusUtils() {}

    /** 任何对象（String/Integer/Long/Number/null）都转成数字 code */
    public static int toNumber(Object status) {
        if (status == null) return BillStatusEnum.DRAFT.getCode();
        if (status instanceof Number n) {
            int v = n.intValue();
            if (v == BillStatusEnum.DRAFT.getCode()
                || v == BillStatusEnum.PENDING.getCode()
                || v == BillStatusEnum.APPROVED.getCode()
                || v == BillStatusEnum.VOIDED.getCode()) {
                return v;
            }
            // Subcontract/Production 内部扩展：3(已下达)/4(执行中)/5(已完工)/6(已结案)
            // 映射成 APPROVED 统一语义（close_status=1时另算）
            if (v == 3 || v == 4 || v == 5) return BillStatusEnum.APPROVED.getCode();
            if (v == 6) return BillStatusEnum.APPROVED.getCode(); // 6(已结案) 用 close_status=1 真实判断
            return BillStatusEnum.APPROVED.getCode();
        }
        String s = status.toString().trim();
        Integer code = STR_TO_CODE.get(s);
        return code != null ? code : BillStatusEnum.APPROVED.getCode(); // 未知态默认"已生效类"（避免误判成草稿可编辑）
    }

    /**
     * 写入「审核人」前的统一归一化（用户 2026-10-28 口径）：
     * 免审批自动通过时的系统占位（系统(免审批) / 系统(免审批自动通过)）一律视为「无人审核」，
     * 审核人留空，不得把系统占位当成审批人展示；真实审批人原样返回。
     */
    public static String normalizeApprover(String approver) {
        if (approver == null) return null;
        String s = approver.trim();
        if (s.isEmpty()) return null;
        if ("系统(免审批)".equals(s) || "系统(免审批自动通过)".equals(s)) return null;
        return approver;
    }

    /** 数字 code → 标准中文显示文案 */
    public static String toString(Integer code) {
        if (code == null) return BillStatusEnum.DRAFT.getLabel();
        String s = CODE_TO_STR.get(code);
        if (s != null) return s;
        if (code == 3 || code == 4 || code == 5) return BillStatusEnum.APPROVED.getLabel();
        return BillStatusEnum.APPROVED.getLabel();
    }

    public static boolean isDraft(Object status)          { return toNumber(status) == BillStatusEnum.DRAFT.getCode(); }
    public static boolean isPending(Object status)        { return toNumber(status) == BillStatusEnum.PENDING.getCode(); }
    public static boolean isApprovedClass(Object status)  { return toNumber(status) == BillStatusEnum.APPROVED.getCode(); }
    public static boolean isVoided(Object status)         { return toNumber(status) == BillStatusEnum.VOIDED.getCode(); }

    /** 是否终态（双终态：已结案 OR 已作废） */
    public static boolean isFinalized(Object status, Object closeStatus) {
        return isVoided(status) || CloseStatusEnum.isClosed(objectToInt(closeStatus));
    }

    /**
     * 生效/完结单据禁止直接修改（统一口径，2026-11-09）。
     *
     * <p>调用方必须传"数据库里的原状态"，而不是请求体里的状态；新建单据（id 为空）不要调用。
     * 判定只看明确的中文状态，不用 toNumber 的未知值兜底，避免把合法草稿误挡。</p>
     */
    public static void assertDirectModifiable(String billLabel, String billNo, Object status, Object closeStatus) {
        String no = (billNo == null || billNo.isBlank()) ? "" : "[" + billNo + "]";
        if (isFinalized(status, closeStatus)) {
            throw new BusinessException(billLabel + no + "已完结，不能修改");
        }
        String s = status == null ? "" : status.toString().trim();
        if ("已生效".equals(s) || "已审核".equals(s)) {
            throw new BusinessException("已生效的" + billLabel + "不可直接修改，请先重置草稿");
        }
    }

    private static int objectToInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        String s = v.toString().trim();
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            // 落到中文兜底
        }
        // 中文兜底（2026-09-20 全量验证修复）：与 toNumber 的"中文/数字双兼容"口径保持一致 ——
        // 此前只解析数字，导致 isFinalized(status, "已结案") 被静默判成"未结案"。
        if ("已结案".equals(s) || "已关闭".equals(s) || "结案".equals(s)) return 1;
        return 0;
    }
}
