package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.basedata.entity.Warehouse;
import com.erp.inventory.mapper.WarehouseMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.serialno.support.SegmentCodeProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("inventoryWarehouseServiceImpl")
@RequiredArgsConstructor
public class WarehouseServiceImpl extends ServiceImpl<WarehouseMapper, Warehouse> implements WarehouseService {

    private final SysSerialNoConfigService serialNoService;

    @Override
    public Warehouse getByCode(String warehouseCode) {
        return getOne(new QueryWrapper<Warehouse>().eq("stock_code", warehouseCode));
    }

    @Override
    public boolean save(Warehouse entity) {
        if (entity.getStockCode() == null || entity.getStockCode().isBlank()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("BD_WAREHOUSE")) {
                throw new BusinessException("仓库已关闭自动编号，请手动输入编码");
            }
            // 三参重载：以业务表实况取号（删除即释放 + 补最小空洞）
            entity.setStockCode(serialNoService.generateSerialNo("BD_WAREHOUSE", this::nextStockCode,
                    SegmentCodeProbe.of(baseMapper, "stock_code")));
        }
        if (exists(new QueryWrapper<Warehouse>().eq("stock_code", entity.getStockCode()))) {
            throw new BusinessException("仓库编码已存在");
        }
        return super.save(entity);
    }

    /**
     * 防重续号探测：查询仓库表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextStockCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        Warehouse last = getOne(new QueryWrapper<Warehouse>().likeRight("stock_code", prefix)
                .orderByDesc("stock_code").last("LIMIT 1"));
        if (last == null || last.getStockCode() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getStockCode().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }
}