package com.erp.produce.assemble.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.produce.assemble.entity.ProduceDisassemble;

import java.util.List;

/**
 * 生产拆卸单Service接口
 */
public interface ProduceDisassembleService extends IService<ProduceDisassemble> {

    /**
     * 保存拆卸单(含明细)
     */
    void saveWithDetails(ProduceDisassemble disassemble);

    /**
     * 分页查询
     *
     * @param status        单据状态入参（2026-10-14 起收中文文案或数字串：草稿/待审核/已生效/已作废 或 0/1/2/7）
     * @param serialNo      单据编号(模糊) —— 旧入参，保留不删
     * @param productName   成品名称(模糊, 走明细表 produce_disassemble_detail.product_name)
     * @param datelineStart 单据日期起(含当天) —— 旧入参，保留不删
     * @param datelineEnd   单据日期止(含当天) —— 旧入参，保留不删
     * @param startDate     单据日期起(统一入参, 优先于 datelineStart)
     * @param endDate       单据日期止(统一入参, 优先于 datelineEnd)
     */
    PageResult<ProduceDisassemble> pageQuery(int current, int size, String keyword, String status,
                                             String sortBy, String sortOrder,
                                             String serialNo, String productName, String datelineStart, String datelineEnd, String customSearch,
                                             String startDate, String endDate);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<ProduceDisassemble> pageQuery(int current, int size, String keyword, String status,
                                                     String sortBy, String sortOrder,
                                                     String serialNo, String productName, String datelineStart, String datelineEnd, String customSearch,
                                                     String startDate, String endDate, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, sortBy, sortOrder,
                serialNo, productName, datelineStart, datelineEnd, customSearch, startDate, endDate);
    }

    /**
     * 获取详情(含明细)
     */
    ProduceDisassemble getDetail(Long id);

    /**
     * 提交审批
     */
    void submit(Long id);

    /**
     * 审批通过
     */
    void approve(Long id, String approver);

    /**
     * 驳回
     */
    void reject(Long id, String reason);

    /**
     * 撤销
     */
    void revoke(Long id);

    /**
     * 作废
     */
    void cancel(Long id, String reason);

    /**
     * 重置草稿
     */
    void resetToDraft(Long id);

    /**
     * 生成单据编号
     */
    String generateSerialNo();

    /** 重置草稿: 生效 → 草稿 */
    void reverseApprove(Long id, String operator);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 结案: 生效类 → close_status=1 (status 保持不变, 前端按 closeStatus 渲染已结案) */
    void closeOrder(Long id, String operator);

    /** 反结案: close_status=0 */
    void reopenOrder(Long id, String operator);

    /** 删除（仅草稿族 + 无已生效下游；整单明细级联删除，统一状态机口径） */
    void deleteBill(Long id);

    /** 复制单据: 读原单+明细生成新草稿, 返回新单(含新id) */
    ProduceDisassemble copyBill(Long id);
}