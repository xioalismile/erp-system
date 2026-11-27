package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.mapper.SupplierMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.serialno.support.SegmentCodeProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {

    private final SysSerialNoConfigService serialNoService;

    @Override
    public boolean save(Supplier entity) {
        if (entity.getSupplierCode() == null || entity.getSupplierCode().isBlank()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("MD_SUPPLIER")) {
                throw new BusinessException("供应商已关闭自动编号，请手动输入编码");
            }
            // 三参重载：以业务表实况取号（删除即释放 + 补最小空洞）
            entity.setSupplierCode(serialNoService.generateSerialNo("MD_SUPPLIER", this::nextSupplierCode,
                    SegmentCodeProbe.of(baseMapper, "supplier_code")));
        }
        long count = count(new QueryWrapper<Supplier>().eq("supplier_code", entity.getSupplierCode()));
        if (count > 0) {
            throw new BusinessException("供应商编码已存在: " + entity.getSupplierCode());
        }
        return super.save(entity);
    }

    /**
     * 防重续号探测：查询供应商表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextSupplierCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        Supplier last = getOne(new QueryWrapper<Supplier>().likeRight("supplier_code", prefix)
                .orderByDesc("supplier_code").last("LIMIT 1"));
        if (last == null || last.getSupplierCode() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getSupplierCode().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }
}
