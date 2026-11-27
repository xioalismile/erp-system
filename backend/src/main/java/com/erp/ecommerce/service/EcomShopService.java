package com.erp.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.ecommerce.entity.EcomShop;

import java.util.List;

public interface EcomShopService extends IService<EcomShop> {

    PageResult<EcomShop> pageQuery(int current, int size, String keyword, Integer status);

    List<EcomShop> listEnabled();

    /** 详情（凭据脱敏，永不回明文） */
    EcomShop getDetail(Long id);

    /** 新增（凭据加密落库） */
    void saveShop(EcomShop shop);

    /** 修改（凭据留空 = 保持原值不覆盖） */
    void updateShop(EcomShop shop);

    void deleteShop(Long id);

    /** 授权测试：用已存的凭据向卖家平台换取访问令牌，成功记已授权，失败记已失效并报中文原因 */
    void testAuth(Long id);

    /** 换取访问令牌（带缓存，过期前 5 分钟自动重取），拉单/测试共用 */
    String getAccessToken(EcomShop shop);
}
