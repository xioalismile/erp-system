package com.erp.serialno.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.serialno.entity.SysSerialNoConfig;

import java.util.List;
import java.util.function.UnaryOperator;

public interface SysSerialNoConfigService extends IService<SysSerialNoConfig> {

    List<SysSerialNoConfig> listAll();

    SysSerialNoConfig getByDocType(String docType);

    boolean saveConfig(SysSerialNoConfig config);

    boolean updateConfig(SysSerialNoConfig config);

    boolean deleteConfig(Long id);

    String generateSerialNo(String docType);

    /**
     * 自动生成编号（带业务表防重抬底）。
     *
     * 背景：关闭自动编号/手动录入/默认规则期间产生的业务单号不会推进 sys_serial_no_config.current_serial，
     * 重新开启自动编号后若仍从旧计数续号，必然与存量单号重复（2026-09-07 bug 修复）。
     * 本方法在生成前调用 {@code nextNoQuery} 取得业务表"当前编号段已用的下一张完整单号"，
     * 若其流水大于配置计数，则先把计数抬升到业务表真实水平再生成，保证不与存量单号重复。
     *
     * @param docType      单据类型
     * @param nextNoQuery  业务表探测回调：入参为当前编号段前缀(不含流水，如 SO-20260907- / CUS)，
     *                     返回该编号段"下一张完整单号"（已用最大值+1）；未配置/查询失败可返回 null。
     *                     ️ 仅在返回单号确实以入参前缀开头时才会参与抬底，避免误伤不同号段。
     *                     传 null 时行为与 {@link #generateSerialNo(String)} 完全一致。
     * @return 生成的编号
     */
    String generateSerialNo(String docType, UnaryOperator<String> nextNoQuery);

    /**
     * 自动生成编号（支持"删除即释放 + 补最小空洞"）。
     *
     * 背景：编号一旦被使用就不再复用，删除业务数据后计数器不会回退，导致流水号出现永久断层。
     * 传入 {@code segmentCodesQuery} 后改为<b>以业务表实况探测</b>：在"当前编号段"内找出最小的
     * 未使用流水，删除即自动释放、下次生成优先补回最小空洞（含历史遗留空洞）。
     *
     * 说明：
     * <ul>
     *   <li>{@code segmentCodesQuery} 为 null 时，行为与 {@link #generateSerialNo(String, UnaryOperator)} 完全一致（其它 docType 不受影响）</li>
     *   <li>探测不可用（抛异常/号段为空）时自动回退到计数器续号，保证不重号</li>
     *   <li>计数器只增不减（写回 max(原值, 本次流水)）</li>
     * </ul>
     *
     * @param docType           单据类型
     * @param nextNoQuery       业务表探测回调（可选，见两参重载）
     * @param segmentCodesQuery 号段已用编码探测回调：入参为编号段前缀（不含流水，如 WL0100 / MAT），
     *                          返回该号段下已使用的完整编码列表；返回 null/空列表表示号段内无数据。
     *                          传 null 时退回计数器续号逻辑。
     * @return 生成的编号
     */
    String generateSerialNo(String docType, UnaryOperator<String> nextNoQuery,
                            java.util.function.Function<String, List<String>> segmentCodesQuery);

    List<String> batchGenerate(List<String> docTypes);

    /**
     * 指定单据类型是否自动生成编号
     * @param docType 单据类型
     * @return true=自动生成 false=需要手动输入
     */
    boolean isAutoGenerate(String docType);

    /**
     * 兼容兜底：如果未配置或异常，返回默认规则生成的编号
     * @param docType 单据类型
     * @param fallbackSupplier 兜底生成方法
     * @return 编号
     */
    String generateOrDefault(String docType, java.util.function.Supplier<String> fallbackSupplier);

    /**
     * 兼容兜底 + 「删除即释放 + 补最小空洞」。
     *
     * <p>与两参版本行为完全一致，唯一区别：生成时额外传入 {@code segmentCodesQuery}，
     * 以业务表实况取号段内最小未用流水（删除的编码自动复用）。</p>
     *
     * @param docType           单据类型
     * @param fallbackSupplier  兜底生成方法（未配置规则/生成异常时使用）
     * @param segmentCodesQuery 号段已用编码探测回调，见
     *                          {@link #generateSerialNo(String, java.util.function.UnaryOperator, java.util.function.Function)}；
     *                          传 null 时行为与两参版本完全一致
     * @return 编号
     */
    String generateOrDefault(String docType, java.util.function.Supplier<String> fallbackSupplier,
                             java.util.function.Function<String, List<String>> segmentCodesQuery);

    /**
     * 系统内部自动建单专用编号生成：忽略「自动生成」关闭标志，仍按编号规则取号。
     *
     * <p>使用场景：设置中心把某单据设为手动编号后，人工新建页面仍需手输单号；
     * 但系统自动下推生成的单据没有人工输入环节，必须由系统按同一编号规则取号，
     * 否则自动下推会因"已关闭自动编号"而失败。</p>
     *
     * <p>规则未配置、规则被禁用或生成异常时使用 {@code fallbackSupplier} 兜底。</p>
     */
    String generateForSystem(String docType, java.util.function.Supplier<String> fallbackSupplier);
}
