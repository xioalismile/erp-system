package com.erp.ai.bill.impl;

import com.erp.inventory.mapper.MaterialRequisitionItemMapper;
import com.erp.inventory.service.MaterialRequisitionService;
import org.springframework.stereotype.Component;

/**
 * 生产补料单明细行适配器（2026-11-22 立项二期补登）。
 *
 * <p>补料单与领料单共用同一实体/明细/保存链路（bill_type=SUPPLEMENT 区分），因此
 * 直接继承领料单适配器，仅改单据类型标识与明细中文名；可改字段（数量/单价）、
 * 保存链路（saveRequisitionWithItems：重算金额、回填成本、拦生效/关闭态）与领料单完全一致。
 */
@Component
public class ProductionSupplementItemHandler extends MaterialRequisitionItemHandler {

    public ProductionSupplementItemHandler(MaterialRequisitionService materialRequisitionService,
                                           MaterialRequisitionItemMapper materialRequisitionItemMapper) {
        super(materialRequisitionService, materialRequisitionItemMapper);
    }

    @Override
    public String billType() {
        return "PRODUCTION_SUPPLEMENT";
    }

    @Override
    public String itemLabel() {
        return "生产补料单明细";
    }
}
