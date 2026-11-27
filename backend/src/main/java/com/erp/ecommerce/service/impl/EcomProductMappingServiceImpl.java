package com.erp.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.ecommerce.entity.EcomProductMapping;
import com.erp.ecommerce.entity.EcomShop;
import com.erp.ecommerce.mapper.EcomProductMappingMapper;
import com.erp.ecommerce.mapper.EcomShopMapper;
import com.erp.ecommerce.service.EcomProductMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EcomProductMappingServiceImpl extends ServiceImpl<EcomProductMappingMapper, EcomProductMapping>
        implements EcomProductMappingService {

    private final EcomShopMapper shopMapper;

    @Override
    public PageResult<EcomProductMapping> pageQuery(int current, int size, Long shopId, String keyword) {
        QueryWrapper<EcomProductMapping> qw = new QueryWrapper<>();
        if (shopId != null) qw.eq("shop_id", shopId);
        if (keyword != null && !keyword.isBlank()) {
            // 关键词多列检索必须 and() 包组，避免 OR 吃掉后续过滤条件
            qw.and(w -> w.like("seller_sku", keyword).or().like("asin", keyword)
                    .or().like("product_name", keyword).or().like("material_code", keyword)
                    .or().like("material_name", keyword));
        }
        qw.orderByDesc("id");
        Page<EcomProductMapping> page = page(new Page<>(current, size), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    @Override
    public void saveMapping(EcomProductMapping mapping) {
        validate(mapping);
        // 同店铺同SKU唯一：先查后插（PG 下不靠唯一键异常兜底）
        Long dup = lambdaQuery()
                .eq(EcomProductMapping::getShopId, mapping.getShopId())
                .eq(EcomProductMapping::getSellerSku, mapping.getSellerSku().trim())
                .count();
        if (dup != null && dup > 0) {
            throw new BusinessException("该店铺下卖家SKU[" + mapping.getSellerSku().trim() + "]已建映射，请直接修改原映射");
        }
        fillShopAndMaterial(mapping);
        mapping.setSellerSku(mapping.getSellerSku().trim());
        save(mapping);
    }

    @Override
    public void updateMapping(EcomProductMapping mapping) {
        if (mapping.getId() == null) throw new BusinessException("缺少映射标识，无法修改");
        EcomProductMapping old = getById(mapping.getId());
        if (old == null) throw new BusinessException("映射记录不存在或已被删除");
        validate(mapping);
        Long dup = lambdaQuery()
                .eq(EcomProductMapping::getShopId, mapping.getShopId())
                .eq(EcomProductMapping::getSellerSku, mapping.getSellerSku().trim())
                .ne(EcomProductMapping::getId, mapping.getId())
                .count();
        if (dup != null && dup > 0) {
            throw new BusinessException("该店铺下卖家SKU[" + mapping.getSellerSku().trim() + "]已建映射，请直接修改原映射");
        }
        old.setShopId(mapping.getShopId());
        old.setSellerSku(mapping.getSellerSku().trim());
        old.setAsin(mapping.getAsin());
        old.setProductName(mapping.getProductName());
        old.setMaterialId(mapping.getMaterialId());
        old.setMaterialCode(mapping.getMaterialCode());
        old.setMaterialName(mapping.getMaterialName());
        fillShopAndMaterial(old);
        updateById(old);
    }

    @Override
    public void deleteMapping(Long id) {
        if (getById(id) == null) return;
        removeById(id);
    }

    private void validate(EcomProductMapping mapping) {
        if (mapping.getShopId() == null) throw new BusinessException("请选择所属店铺");
        if (mapping.getSellerSku() == null || mapping.getSellerSku().isBlank()) throw new BusinessException("请填写平台卖家SKU");
        if (mapping.getMaterialId() == null) throw new BusinessException("请选择对应物料");
    }

    /** 冗余店铺名称与物料编码名称，保证列表不联表也有值 */
    private void fillShopAndMaterial(EcomProductMapping mapping) {
        if (mapping.getShopId() != null) {
            EcomShop shop = shopMapper.selectById(mapping.getShopId());
            if (shop == null) throw new BusinessException("所选店铺不存在或已被删除");
            mapping.setShopName(shop.getShopName());
        }
    }
}
