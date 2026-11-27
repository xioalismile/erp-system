package com.erp.ai.bill;

import cn.hutool.core.util.ReflectUtil;
import com.erp.ai.service.AiCrudAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 单据"人话标识"解析器：把（单据类型 + 内部主键）翻成用户认识的标识。
 *
 * <p>背景（2026-09-18 用户反馈）：确认卡片与语音播报里一直是内部主键（如
 * {@code 对单据【SALE_ORDER】ID=258 执行「重置草稿」}）—— 单据名是英文枚举、编号是数据库自增 ID，
 * 用户既听不明白也对不上自己界面上的单号。本类负责解析出：
 * <ol>
 *   <li>单据中文名（销售订单）；</li>
 *   <li>业务单号（SO-20260914-0005）；</li>
 *   <li>往来单位名（客户名/供应商名，从关联主档回查）。</li>
 * </ol>
 *
 * <p>展示口径由用户在「小助手 → ⚙️ 设置」里选择（{@code billLabelStyle}）：
 * {@code code}=单号（默认）、{@code name}=客户名、{@code both}=单号（客户名）。
 *
 * <p><b>fail-safe 铁律</b>：任何一步解析失败（Service 无 getById / 实体不存在 / 字段为空）
 * 都只降级为「单据中文名 #内部ID」，绝不抛异常、绝不阻塞确认卡片与写操作执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillCodeResolver {

    /** 展示口径：单号（默认） */
    public static final String STYLE_CODE = "code";
    /** 展示口径：客户名/供应商名 */
    public static final String STYLE_NAME = "name";
    /** 展示口径：单号（客户名） */
    public static final String STYLE_BOTH = "both";

    /** AiDataScope.displayPrefs 里"单据标识方式"的键名（前后端唯一约定） */
    public static final String PREF_KEY_BILL_LABEL_STYLE = "billLabelStyle";

    private final BillActionRegistry billActionRegistry;
    private final AiCrudAgentService aiCrudAgentService;

    /** 解析结果：单据中文名 + 业务单号 + 往来单位名（任一可能为 null） */
    public record BillRef(String label, String code, String party) {
    }

    /**
     * 按用户选择的口径生成单据标识（用于确认卡片、执行结果、语音播报）。
     *
     * @param style code/name/both，为空或非法时按 code 处理
     * @return 形如「【销售订单】SO-20260914-0005（深圳远大智能科技有限公司）」
     */
    public String describe(String billType, Long billId, String style) {
        String label = labelOf(billType);
        if (billId == null) {
            return "【" + label + "】";
        }
        BillRef ref = resolve(billType, billId);
        String code = ref.code();
        String party = ref.party();

        if (STYLE_NAME.equals(style)) {
            // 客户名口径：解析不到客户名时不能让卡片变成空标识，退回单号
            if (notBlank(party)) return "【" + label + "】" + party;
            return "【" + label + "】" + (notBlank(code) ? code : "#" + billId);
        }
        if (STYLE_BOTH.equals(style)) {
            if (notBlank(code)) {
                return "【" + label + "】" + code + (notBlank(party) ? "（" + party + "）" : "");
            }
            return "【" + label + "】" + (notBlank(party) ? party : "#" + billId);
        }
        // 默认：单号
        return "【" + label + "】" + (notBlank(code) ? code : "#" + billId);
    }

    /** 解析单据的业务单号与往来单位名；解析不到时对应字段为 null（不抛异常） */
    public BillRef resolve(String billType, Long billId) {
        String label = labelOf(billType);
        if (billType == null || billId == null) {
            return new BillRef(label, null, null);
        }
        try {
            Object service = billActionRegistry.service(billType);
            Object entity = loadEntity(service, billId);
            if (entity == null) {
                return new BillRef(label, null, null);
            }
            return new BillRef(label, readCode(entity, billType), readParty(entity));
        } catch (Exception e) {
            log.warn("[AI单据标识] 解析失败 billType={} billId={}: {}", billType, billId, e.getMessage());
            return new BillRef(label, null, null);
        }
    }

    /** 取审计日志用的单据编号：解析不到时退回 "#内部ID"（与操作日志既有口径一致） */
    public String auditBillNo(String billType, Long billId) {
        BillRef ref = resolve(billType, billId);
        return notBlank(ref.code()) ? ref.code() : "#" + billId;
    }

    private String labelOf(String billType) {
        try {
            String label = billActionRegistry.label(billType);
            return notBlank(label) ? label : String.valueOf(billType);
        } catch (Exception e) {
            return String.valueOf(billType);
        }
    }

    /**
     * 按主键回查实体。
     *
     * <p>注意：BillActionRegistry 里注入的是接口/CGLIB 代理，方法签名是 {@code getById(Serializable)}，
     * 不能用「参数类型必须等于 Long.class」做精确匹配（历史上反射试错式匹配踩过坑），
     * 这里按「方法名 + 参数个数」匹配，参数值直接用 Long。
     */
    private Object loadEntity(Object service, Long billId) {
        if (service == null) return null;
        for (Method m : service.getClass().getMethods()) {
            if (!"getById".equals(m.getName()) || m.getParameterCount() != 1) continue;
            try {
                return m.invoke(service, billId);
            } catch (Exception e) {
                log.debug("[AI单据标识] getById 调用失败: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * 读业务单号：优先按主键字段推导（orderId → orderNo），再按项目既有约定兜底
     * （字段名以 No / Code 结尾，与 {@code BillOperateLogAspect} 同口径）。
     *
     * <p>兜底扫描时排除 customerNo/supplierNo/materialNo 这类"关联主档编号"，
     * 避免把客户编号当成销售订单的单号。
     */
    private String readCode(Object entity, String billType) {
        String idField = billActionRegistry.idField(billType);
        if (notBlank(idField) && idField.endsWith("Id")) {
            String candidate = idField.substring(0, idField.length() - 2) + "No";
            String v = readField(entity, candidate);
            if (notBlank(v)) return v;
        }
        for (Field f : entity.getClass().getDeclaredFields()) {
            String name = f.getName();
            if (!(name.endsWith("No") || name.endsWith("Code"))) continue;
            if (isForeignMasterCode(name)) continue;
            String v = readField(entity, name);
            if (notBlank(v)) return v;
        }
        return null;
    }

    /** 是否为关联主档编号字段（客户/供应商/物料编号），不作为本单号 */
    private boolean isForeignMasterCode(String name) {
        return name.startsWith("customer") || name.startsWith("supplier") || name.startsWith("material");
    }

    /**
     * 读往来单位名（客户名/供应商名）。
     * 这两列在单据实体上是 {@code @TableField(exist=false)} 的展示字段（反射恒为 null），
     * 必须走 AiCrudAgentService 既有的「外键 → 主档名」回查逻辑（单一适配点，避免重复写死表和列）。
     */
    private String readParty(Object entity) {
        try {
            return aiCrudAgentService.foreignDisplayName(entity);
        } catch (Exception e) {
            log.warn("[AI单据标识] 回查往来单位名失败: {}", e.getMessage());
            return null;
        }
    }

    /** 反射读取字段值（字段不存在或取值异常都返回 null） */
    private String readField(Object entity, String fieldName) {
        try {
            if (!ReflectUtil.hasField(entity.getClass(), fieldName)) return null;
            Object v = ReflectUtil.getFieldValue(entity, fieldName);
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 供工具/服务统一读取用户选择的口径；非法值一律按"单号"处理（默认值 = 旧行为之外的更可读口径） */
    public static String normalizeStyle(String style) {
        if (STYLE_NAME.equals(style) || STYLE_BOTH.equals(style)) return style;
        return STYLE_CODE;
    }

    /** 允许的展示口径清单（校验前端传值用） */
    public static List<String> allowedStyles() {
        return List.of(STYLE_CODE, STYLE_NAME, STYLE_BOTH);
    }
}
