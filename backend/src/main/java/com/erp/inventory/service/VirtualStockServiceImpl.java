package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 虚拟库存服务实现
 *
 * 占用统计口径：status='已生效'（生效）且 close_status 未结案的销售订单明细，
 * 按 (quantity - COALESCE(delivered_quantity, 0)) 汇总。
 * 出库审核后回写 delivered_quantity 自动释放占用，无需额外钩子。
 */
@Service
@RequiredArgsConstructor
public class VirtualStockServiceImpl implements VirtualStockService {

    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final InventoryService inventoryService;

    @Override
    public BigDecimal getReservedQuantity(Long materialId, String excludeOrderNo) {
        if (materialId == null) {
            return BigDecimal.ZERO;
        }
        List<Long> orderIds = listActiveOrderIds(excludeOrderNo);
        if (orderIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        QueryWrapper<SaleOrderItem> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(COALESCE(quantity,0) - COALESCE(delivered_quantity,0)),0) AS reserved")
                .eq("material_id", materialId)
                .in("order_id", orderIds);
        List<Map<String, Object>> rows = saleOrderItemMapper.selectMaps(wrapper);
        return toBigDecimal(rows, "reserved");
    }

    @Override
    public Map<Long, BigDecimal> batchGetReservedQuantity(Collection<Long> materialIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (materialIds == null || materialIds.isEmpty()) {
            return result;
        }
        List<Long> orderIds = listActiveOrderIds(null);
        if (orderIds.isEmpty()) {
            return result;
        }
        QueryWrapper<SaleOrderItem> wrapper = new QueryWrapper<>();
        wrapper.select("material_id",
                        "COALESCE(SUM(COALESCE(quantity,0) - COALESCE(delivered_quantity,0)),0) AS reserved")
                .in("material_id", materialIds)
                .in("order_id", orderIds)
                .groupBy("material_id");
        for (Map<String, Object> row : saleOrderItemMapper.selectMaps(wrapper)) {
            Object mid = row.get("material_id");
            if (mid != null) {
                result.put(Long.valueOf(mid.toString()), toBigDecimalValue(row.get("reserved")));
            }
        }
        return result;
    }

    @Override
    public BigDecimal getVirtualAvailable(Long materialId) {
        if (materialId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal available = inventoryService.getTotalAvailableQuantity(materialId);
        BigDecimal reserved = getReservedQuantity(materialId, null);
        return available.subtract(reserved);
    }

    /**
     * 生效订单ID列表：已生效且未结案（close_status 为空或0）
     */
    private List<Long> listActiveOrderIds(String excludeOrderNo) {
        QueryWrapper<SaleOrder> wrapper = new QueryWrapper<>();
        wrapper.select("order_id")
                .eq("status", "已生效")
                .and(w -> w.isNull("close_status").or().eq("close_status", 0));
        if (excludeOrderNo != null && !excludeOrderNo.isBlank()) {
            wrapper.ne("order_no", excludeOrderNo);
        }
        return saleOrderMapper.selectList(wrapper).stream()
                .map(SaleOrder::getOrderId)
                .collect(java.util.stream.Collectors.toList());
    }

    private BigDecimal toBigDecimal(List<Map<String, Object>> rows, String alias) {
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return toBigDecimalValue(rows.get(0).get(alias));
    }

    private BigDecimal toBigDecimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }
}
