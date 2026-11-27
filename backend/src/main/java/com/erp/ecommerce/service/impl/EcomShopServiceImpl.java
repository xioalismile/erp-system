package com.erp.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.ecommerce.entity.EcomPlatformOrder;
import com.erp.ecommerce.entity.EcomProductMapping;
import com.erp.ecommerce.entity.EcomShop;
import com.erp.ecommerce.mapper.EcomPlatformOrderMapper;
import com.erp.ecommerce.mapper.EcomProductMappingMapper;
import com.erp.ecommerce.mapper.EcomShopMapper;
import com.erp.ecommerce.service.EcomCryptoService;
import com.erp.ecommerce.service.EcomPddService;
import com.erp.ecommerce.service.EcomShopService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EcomShopServiceImpl extends ServiceImpl<EcomShopMapper, EcomShop> implements EcomShopService {

    private final EcomCryptoService cryptoService;
    private final EcomPlatformOrderMapper platformOrderMapper;
    private final EcomProductMappingMapper productMappingMapper;
    private final EcomPddService pddService;
    private final ObjectMapper objectMapper;

    /** 卖家平台登录换令牌地址（固定，全球一致） */
    private static final String LWA_TOKEN_URL = "https://api.amazon.com/auth/o2/token";

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(30))
            .build();

    /** 访问令牌缓存：店铺ID → (token, 过期时间)。令牌本身非敏感数据（1小时自动过期） */
    private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    private record CachedToken(String token, LocalDateTime expireAt) {
        boolean valid() { return token != null && expireAt != null && LocalDateTime.now().isBefore(expireAt.minusMinutes(5)); }
    }

    @Override
    public PageResult<EcomShop> pageQuery(int current, int size, String keyword, Integer status) {
        QueryWrapper<EcomShop> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            // 关键词多列检索必须 and() 包组，避免 OR 吃掉后续条件
            qw.and(w -> w.like("shop_name", keyword).or().like("seller_id", keyword)
                    .or().like("associate_customer_name", keyword));
        }
        if (status != null) qw.eq("status", status);
        qw.orderByAsc("id");
        Page<EcomShop> page = page(new Page<>(current, size), qw);
        page.getRecords().forEach(this::maskSensitive);
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    @Override
    public List<EcomShop> listEnabled() {
        List<EcomShop> list = list(new QueryWrapper<EcomShop>().eq("status", 1).orderByAsc("id"));
        list.forEach(this::maskSensitive);
        return list;
    }

    @Override
    public EcomShop getDetail(Long id) {
        EcomShop shop = getById(id);
        if (shop == null) throw new BusinessException("店铺不存在或已被删除");
        maskSensitive(shop);
        return shop;
    }

    @Override
    public void saveShop(EcomShop shop) {
        String platform = "PDD".equalsIgnoreCase(shop.getPlatform()) ? "PDD" : "AMAZON";
        shop.setPlatform(platform);
        if ("PDD".equals(platform)) {
            shop.setRegion("CN");
            shop.setMarketplaceId("PDD"); // 国内店铺无市场编号概念，存占位值
        } else {
            if (isBlank(shop.getRegion())) shop.setRegion("NA");
            if (isBlank(shop.getMarketplaceId())) shop.setMarketplaceId("ATVPDKIKX0DER");
        }
        validateBasic(shop);
        // 店铺名称查重（先查后插，PG 下不靠异常兜底）
        Long dup = lambdaQuery().eq(EcomShop::getShopName, shop.getShopName().trim()).count();
        if (dup != null && dup > 0) throw new BusinessException("店铺名称[" + shop.getShopName().trim() + "]已存在，请换个名称");
        // 新增必须带齐凭据
        if (isBlank(shop.getClientId())) throw new BusinessException("请填写客户端编号");
        if (isBlank(shop.getClientSecret())) throw new BusinessException("请填写客户端密钥");
        if ("PDD".equals(platform)) {
            if (isBlank(shop.getAccessToken())) throw new BusinessException("请填写拼多多访问令牌（应用授权后生成）");
        } else {
            if (isBlank(shop.getRefreshToken())) throw new BusinessException("请填写卖家平台刷新令牌");
        }
        shop.setClientId(cryptoService.encrypt(shop.getClientId().trim()));
        shop.setClientSecret(cryptoService.encrypt(shop.getClientSecret().trim()));
        if (!isBlank(shop.getRefreshToken())) shop.setRefreshToken(cryptoService.encrypt(shop.getRefreshToken().trim()));
        if (!isBlank(shop.getAccessToken())) shop.setAccessToken(cryptoService.encrypt(shop.getAccessToken().trim()));
        if (shop.getAuthStatus() == null || shop.getAuthStatus().isBlank()) shop.setAuthStatus("未授权");
        if (shop.getStatus() == null) shop.setStatus(1);
        shop.setShopName(shop.getShopName().trim());
        save(shop);
    }

    @Override
    public void updateShop(EcomShop shop) {
        if (shop.getId() == null) throw new BusinessException("缺少店铺标识，无法修改");
        EcomShop old = getById(shop.getId());
        if (old == null) throw new BusinessException("店铺不存在或已被删除");
        validateBasic(shop);
        String name = shop.getShopName().trim();
        Long dup = lambdaQuery().eq(EcomShop::getShopName, name).ne(EcomShop::getId, shop.getId()).count();
        if (dup != null && dup > 0) throw new BusinessException("店铺名称[" + name + "]已存在，请换个名称");
        old.setShopName(name);
        String platform = "PDD".equalsIgnoreCase(shop.getPlatform()) ? "PDD" : (old.getPlatform() == null ? "AMAZON" : old.getPlatform());
        old.setPlatform(platform);
        if ("PDD".equals(platform)) {
            old.setRegion("CN");
            old.setMarketplaceId("PDD");
        } else {
            old.setRegion(shop.getRegion());
            old.setMarketplaceId(shop.getMarketplaceId());
        }
        old.setSellerId(shop.getSellerId());
        old.setAssociateCustomerId(shop.getAssociateCustomerId());
        old.setAssociateCustomerCode(shop.getAssociateCustomerCode());
        old.setAssociateCustomerName(shop.getAssociateCustomerName());
        old.setStatus(shop.getStatus() == null ? old.getStatus() : shop.getStatus());
        old.setRemark(shop.getRemark());
        // 凭据：留空 = 保持原值；填了 = 重新加密覆盖
        if (!isBlank(shop.getClientId())) old.setClientId(cryptoService.encrypt(shop.getClientId().trim()));
        if (!isBlank(shop.getClientSecret())) old.setClientSecret(cryptoService.encrypt(shop.getClientSecret().trim()));
        if (!isBlank(shop.getRefreshToken())) old.setRefreshToken(cryptoService.encrypt(shop.getRefreshToken().trim()));
        if (!isBlank(shop.getAccessToken())) old.setAccessToken(cryptoService.encrypt(shop.getAccessToken().trim()));
        updateById(old);
        tokenCache.remove(shop.getId());
    }

    @Override
    public void deleteShop(Long id) {
        EcomShop shop = getById(id);
        if (shop == null) return;
        Long orderCount = platformOrderMapper.selectCount(
                new QueryWrapper<EcomPlatformOrder>().eq("shop_id", id));
        if (orderCount != null && orderCount > 0) {
            throw new BusinessException("该店铺已拉取 " + orderCount + " 张平台订单，不能删除；如确需删除请先清理平台订单");
        }
        Long mappingCount = productMappingMapper.selectCount(
                new QueryWrapper<EcomProductMapping>().eq("shop_id", id));
        if (mappingCount != null && mappingCount > 0) {
            throw new BusinessException("该店铺已建 " + mappingCount + " 条商品映射，不能删除；请先在商品映射页删除对应条目");
        }
        removeById(id);
        tokenCache.remove(id);
    }

    @Override
    public void testAuth(Long id) {
        EcomShop shop = getById(id);
        if (shop == null) throw new BusinessException("店铺不存在或已被删除");
        boolean isPdd = "PDD".equals(shop.getPlatform());
        try {
            if (isPdd) {
                // 拼多多：免令牌接口验凭据签名 + 带令牌接口验令牌权限（内部已回写授权状态）
                pddService.testAuth(shop);
            } else {
                // 亚马逊：卖家平台登录换令牌
                String token = requestAccessToken(shop);
                if (token == null || token.isBlank()) throw new BusinessException("卖家平台未返回访问令牌");
                shop.setAuthStatus("已授权");
                shop.setLastAuthTime(LocalDateTime.now());
                updateById(shop);
                tokenCache.put(id, new CachedToken(token, LocalDateTime.now().plusHours(1)));
            }
            log.info("[电商平台] 店铺[{}]授权测试成功", shop.getShopName());
        } catch (BusinessException e) {
            shop.setAuthStatus("已失效");
            updateById(shop);
            throw e;
        } catch (Exception e) {
            shop.setAuthStatus("已失效");
            updateById(shop);
            log.error("[电商平台] 店铺[{}]授权测试失败", shop.getShopName(), e);
            throw new BusinessException("授权测试失败：" + e.getMessage());
        }
    }

    @Override
    public String getAccessToken(EcomShop shop) {
        CachedToken cached = tokenCache.get(shop.getId());
        if (cached != null && cached.valid()) return cached.token();
        String token = requestAccessToken(shop);
        tokenCache.put(shop.getId(), new CachedToken(token, LocalDateTime.now().plusHours(1)));
        return token;
    }

    /** 调卖家平台登录接口换访问令牌；失败抛中文业务异常 */
    private String requestAccessToken(EcomShop shop) {
        String clientId = cryptoService.decrypt(shop.getClientId());
        String clientSecret = cryptoService.decrypt(shop.getClientSecret());
        String refreshToken = cryptoService.decrypt(shop.getRefreshToken());
        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(refreshToken)) {
            throw new BusinessException("店铺凭据不完整或解密失败，请到店铺授权页重新填写");
        }
        FormBody body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build();
        Request request = new Request.Builder()
                .url(LWA_TOKEN_URL)
                .post(body)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            String respBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new BusinessException(translateLwaError(respBody, response.code()));
            }
            JsonNode json = objectMapper.readTree(respBody);
            String token = json.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new BusinessException("卖家平台未返回访问令牌，请稍后重试");
            }
            return token;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[电商平台] 店铺[{}]换取访问令牌异常", shop.getShopName(), e);
            throw new BusinessException("连接卖家平台失败：" + e.getMessage());
        }
    }

    /** 把卖家平台的错误码翻译成中文 */
    private String translateLwaError(String respBody, int httpCode) {
        try {
            JsonNode json = objectMapper.readTree(respBody);
            String error = json.path("error").asText("");
            String desc = json.path("error_description").asText("");
            return switch (error) {
                case "invalid_client" -> "授权失败：客户端编号或客户端密钥不正确";
                case "invalid_grant" -> "授权失败：刷新令牌无效或已被卖家平台吊销，请重新生成并更新";
                case "unsupported_grant_type" -> "授权失败：令牌类型不受支持，请检查刷新令牌是否复制完整";
                case "SlowDown", "RequestThrottled" -> "授权请求过于频繁，请稍后再试";
                default -> "授权失败（卖家平台返回 " + httpCode + "）" + (desc.isBlank() ? "" : "：" + desc);
            };
        } catch (Exception ignore) {
            return "授权失败（卖家平台返回 " + httpCode + "）";
        }
    }

    private void validateBasic(EcomShop shop) {
        if (isBlank(shop.getShopName())) throw new BusinessException("请填写店铺名称");
        if (!"PDD".equals(shop.getPlatform())) {
            if (isBlank(shop.getRegion())) throw new BusinessException("请选择站点区域");
            if (isBlank(shop.getMarketplaceId())) throw new BusinessException("请填写市场编号（北美站默认 ATVPDKIKX0DER）");
        }
        if (shop.getAssociateCustomerId() == null) throw new BusinessException("请选择关联客户（转销售订单时使用）");
    }

    private void maskSensitive(EcomShop shop) {
        String secretPlain = cryptoService.decrypt(shop.getClientSecret());
        shop.setClientSecretMasked(cryptoService.mask(secretPlain));
        shop.setRefreshTokenConfigured(!isBlank(shop.getRefreshToken()));
        shop.setAccessTokenConfigured(!isBlank(shop.getAccessToken()));
        // 密文不出接口：全部凭据一律置空，编辑留空 = 保持原值，避免密文被当成新值二次加密
        shop.setClientId(null);
        shop.setClientSecret(null);
        shop.setRefreshToken(null);
        shop.setAccessToken(null);
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
