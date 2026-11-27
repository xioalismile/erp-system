package com.erp.sale.service;

import com.erp.sale.entity.SaleContract;
import com.erp.sale.entity.SaleOrder;

/**
 * 自动下推单据：源单生效后按设置中心开关自动生成下游单据并直接生效。
 *
 * <p>口径（2026-11-08 用户需求）：
 * ① 销售合同生效 → 自动生成一份已生效的销售订单；
 * ② 销售订单生效 → 自动生成一份已生效的销售合同；
 * ③ 下游单据必须记录来源单据类型与来源单号，来源/去向页签可查；
 * ④ 自动生成的单据不再触发反向自动下推，来源单已是对方单据时跳过，同一来源已有目标单时跳过。</p>
 */
public interface SaleAutoPushService {

    /** 销售合同生效后自动生成销售订单（开关关闭时静默跳过） */
    void autoPushContractToOrder(SaleContract contract, String operator);

    /** 销售订单生效后自动生成销售合同（开关关闭时静默跳过） */
    void autoPushOrderToContract(SaleOrder order, String operator);
}
