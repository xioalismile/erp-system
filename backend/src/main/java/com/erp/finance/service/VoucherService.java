package com.erp.finance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.entity.Voucher;

/**
 * 会计凭证服务
 */
public interface VoucherService extends IService<Voucher> {

    /**
     * 分页查询凭证
     */
    PageResult<Voucher> pageVoucher(int current, int size, String voucherNo, String voucherType,
                                     String status, Integer periodYear, Integer periodMonth);

    /**
     * 获取凭证详情（含分录列表）
     */
    Voucher getVoucherDetail(Long voucherId);

    /**
     * 创建凭证（同时保存分录，校验借贷平衡）
     */
    Voucher createVoucher(Voucher voucher);

    /**
     * 更新凭证（只有待审核状态可修改）
     */
    Voucher updateVoucher(Voucher voucher);

    /**
     * 删除凭证（同时删除分录）
     */
    void deleteVoucher(Long voucherId);

    /**
     * 记账（状态从待记账→已记账，更新科目余额表）
     */
    void postVoucher(Long voucherId, String operator);

    /**
     * 审核（approval_status从待审核→已审核，status变为待记账）
     */
    void auditVoucher(Long voucherId, String operator);
}
