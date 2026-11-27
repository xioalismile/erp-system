package com.erp.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Currency;
import com.erp.basedata.service.CurrencyService;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.ecommerce.entity.EcomPlatformOrder;
import com.erp.ecommerce.entity.EcomPlatformOrderItem;
import com.erp.ecommerce.entity.EcomProductMapping;
import com.erp.ecommerce.entity.EcomShop;
import com.erp.ecommerce.mapper.EcomPlatformOrderItemMapper;
import com.erp.ecommerce.mapper.EcomPlatformOrderMapper;
import com.erp.ecommerce.mapper.EcomProductMappingMapper;
import com.erp.ecommerce.service.EcomOrderService;
import com.erp.ecommerce.service.EcomPddService;
import com.erp.ecommerce.service.EcomShopService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.service.SaleOrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EcomOrderServiceImpl extends ServiceImpl<EcomPlatformOrderMapper, EcomPlatformOrder>
        implements EcomOrderService {

    private final EcomPlatformOrderItemMapper itemMapper;
    private final EcomProductMappingMapper mappingMapper;
    private final EcomShopService shopService;
    private final EcomPddService pddService;
    private final CustomerService customerService;
    private final MaterialService materialService;
    private final CurrencyService currencyService;
    private final SaleOrderService saleOrderService;
    private final ObjectMapper objectMapper;

    /** 各站点区域对应的卖家平台接口主机 */
    private static final Map<String, String> REGION_HOSTS = Map.of(
            "NA", "https://sellingpartnerapi-na.amazon.com",
            "EU", "https://sellingpartnerapi-eu.amazon.com",
            "FE", "https://sellingpartnerapi-fe.amazon.com"
    );

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .build();

    @Override
    public PageResult<EcomPlatformOrder> pageQuery(int current, int size, Long shopId, String keyword, Integer converted, String platform) {
        QueryWrapper<EcomPlatformOrder> qw = new QueryWrapper<>();
        if (shopId != null) qw.eq("shop_id", shopId);
        if (converted != null) qw.eq("converted", converted);
        if (platform != null && !platform.isBlank()) qw.eq("platform", platform);
        if (keyword != null && !keyword.isBlank()) {
            // 关键词多列检索必须 and() 包组，避免 OR 吃掉后续过滤条件
            qw.and(w -> w.like("platform_order_id", keyword).or().like("ship_name", keyword)
                    .or().like("ship_city", keyword).or().like("converted_sale_order_no", keyword));
        }
        qw.orderByDesc("purchase_date");
        Page<EcomPlatformOrder> page = page(new Page<>(current, size), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    @Override
    public EcomPlatformOrder getDetail(Long id) {
        EcomPlatformOrder order = getById(id);
        if (order == null) throw new BusinessException("平台订单不存在或已被删除");
        order.setItems(itemMapper.selectList(new QueryWrapper<EcomPlatformOrderItem>()
                .eq("order_id", id).orderByAsc("id")));
        return order;
    }

    // ==================== 拉单 ====================

    @Override
    @Transactional
    public Map<String, Object> pullOrders(Long shopId, Integer days) {
        List<EcomShop> shops;
        if (shopId != null) {
            EcomShop shop = shopService.getById(shopId);
            if (shop == null) throw new BusinessException("所选店铺不存在或已被删除");
            shops = List.of(shop);
        } else {
            shops = shopService.listEnabled();
            if (shops.isEmpty()) throw new BusinessException("没有启用的店铺，请先到店铺授权页新增并启用店铺");
        }
        int lookbackDays = (days == null || days < 1) ? 7 : Math.min(days, 90);

        int fetched = 0, added = 0, skipped = 0;
        for (EcomShop shop : shops) {
            int[] r = pullOneShop(shop, lookbackDays);
            fetched += r[0];
            added += r[1];
            skipped += r[2];
        }
        Map<String, Object> result = new HashMap<>();
        result.put("fetched", fetched);
        result.put("added", added);
        result.put("skipped", skipped);
        result.put("days", lookbackDays);
        return result;
    }

    /** 拉单个店铺：返回 [拉到, 新入库, 跳过] */
    private int[] pullOneShop(EcomShop shop, int lookbackDays) {
        if ("0".equals(String.valueOf(shop.getStatus()))) {
            log.info("[电商平台] 店铺[{}]已停用，跳过拉单", shop.getShopName());
            return new int[]{0, 0, 0};
        }
        // 平台分流：拼多多走拼多多网关，其余走亚马逊卖家平台
        if ("PDD".equals(shop.getPlatform())) {
            return pullPddShop(shop, lookbackDays);
        }
        String host = REGION_HOSTS.getOrDefault(shop.getRegion() == null ? "NA" : shop.getRegion(),
                "https://sellingpartnerapi-na.amazon.com");
        String marketplaceId = shop.getMarketplaceId();
        String token = shopService.getAccessToken(shop);
        String lastUpdatedAfter = DateTimeFormatter.ISO_INSTANT.format(
                Instant.now().minusSeconds((long) lookbackDays * 24 * 3600));

        int fetched = 0, added = 0, skipped = 0;
        String nextToken = null;
        do {
            String url;
            if (nextToken == null) {
                url = host + "/orders/v0/orders?MarketplaceIds=" + urlEncode(marketplaceId)
                        + "&LastUpdatedAfter=" + urlEncode(lastUpdatedAfter)
                        + "&MaxResultsPerPage=50";
            } else {
                url = host + "/orders/v0/orders?NextToken=" + urlEncode(nextToken);
            }
            JsonNode payload = callSpApi(url, token, shop.getShopName());
            nextToken = payload.path("NextToken").asText(null);
            JsonNode orders = payload.path("Orders");
            if (orders.isArray()) {
                for (JsonNode o : orders) {
                    fetched++;
                    String platformOrderId = o.path("AmazonOrderId").asText("");
                    if (platformOrderId.isBlank()) continue;
                    // 先查后插去重（PG 下禁止 catch 唯一键异常兜底）
                    Long exists = lambdaQuery().eq(EcomPlatformOrder::getPlatformOrderId, platformOrderId).count();
                    if (exists != null && exists > 0) {
                        skipped++;
                        continue;
                    }
                    saveOneOrder(shop, o, platformOrderId, token, host);
                    added++;
                }
            }
        } while (nextToken != null && !nextToken.isBlank());

        log.info("[电商平台] 店铺[{}]拉单完成：往前看 {} 天，拉到 {} 张，新入库 {} 张，跳过 {} 张",
                shop.getShopName(), lookbackDays, fetched, added, skipped);
        return new int[]{fetched, added, skipped};
    }

    /** 拼多多订单状态官方枚举 → 中文（未知值原样显示数字） */
    private static final Map<String, String> PDD_ORDER_STATUS = Map.of(
            "1", "待付款", "2", "待发货", "3", "已发货", "4", "已完成", "5", "已取消"
    );

    /**
     * 拉单个拼多多店铺：增量接口按「最近更新时间」拉单号列表，再逐单取详情。
     * 返回 [拉到, 新入库, 跳过]。
     */
    private int[] pullPddShop(EcomShop shop, int lookbackDays) {
        long endTs = System.currentTimeMillis() / 1000;
        long startTs = endTs - (long) lookbackDays * 24 * 3600;
        int fetched = 0, added = 0, skipped = 0;
        int page = 1;
        final int pageSize = 100;
        while (true) {
            JsonNode resp = pddService.getIncrementOrders(shop, startTs, endTs, page, pageSize);
            JsonNode list = resp.path("order_list_get_response").path("order_list");
            if (!list.isArray() || list.size() == 0) break;
            for (JsonNode o : list) {
                fetched++;
                String orderSn = o.path("order_sn").asText("");
                if (orderSn.isBlank()) continue;
                // 先查后插去重（PG 下禁止 catch 唯一键异常兜底）
                Long exists = lambdaQuery().eq(EcomPlatformOrder::getPlatformOrderId, orderSn).count();
                if (exists != null && exists > 0) {
                    skipped++;
                    continue;
                }
                // 逐单取详情（含明细行与收货信息），原始报文留档排查
                JsonNode detail = pddService.getOrderDetail(shop, orderSn)
                        .path("order_detail_get_response").path("order_list");
                JsonNode orderNode = (detail.isArray() && detail.size() > 0) ? detail.get(0) : o;
                saveOnePddOrder(shop, orderNode, orderSn);
                added++;
            }
            if (list.size() < pageSize) break;
            page++;
            if (page > 50) break; // 单店铺单次拉取上限保护
        }
        log.info("[电商平台] 拼多多店铺[{}]拉单完成：往前看 {} 天，拉到 {} 张，新入库 {} 张，跳过 {} 张",
                shop.getShopName(), lookbackDays, fetched, added, skipped);
        return new int[]{fetched, added, skipped};
    }

    /** 保存一张拼多多订单 + 明细（金额官方单位为分，落库换算成元；映射快照在拉单时写入） */
    private void saveOnePddOrder(EcomShop shop, JsonNode o, String orderSn) {
        EcomPlatformOrder order = new EcomPlatformOrder();
        order.setPlatform("PDD");
        order.setShopId(shop.getId());
        order.setShopName(shop.getShopName());
        order.setPlatformOrderId(orderSn);
        long createdAt = o.path("created_at").asLong(0);
        order.setPurchaseDate(createdAt > 0
                ? LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(createdAt), ZoneId.systemDefault())
                : null);
        String statusRaw = String.valueOf(o.path("order_status").asInt(0));
        order.setOrderStatus(PDD_ORDER_STATUS.getOrDefault(statusRaw, statusRaw));
        order.setPaymentMethod("");
        order.setCurrencyCode("CNY"); // 国内平台人民币结算
        order.setOrderTotal(fenToYuan(o.path("pay_amount")));
        JsonNode items = o.path("item_list");
        order.setItemCount(items.isArray() ? items.size() : null);
        // 收件人信息（官方按隐私口径可能返回脱敏或加密字段，取得到就存，取不到留空）
        order.setShipName(firstNonBlank(o.path("receiver_name").asText(""), o.path("receiver_name_mask").asText("")));
        order.setShipCity(o.path("city").asText(""));
        order.setShipState(o.path("province").asText(""));
        order.setShipCountry("CN");
        order.setShipAddress(joinPddAddress(o));
        order.setConverted(0);
        order.setPullTime(LocalDateTime.now());
        order.setRawJson(o.toString());
        baseMapper.insert(order);

        // 明细行（商品编码优先取商家自编 outer_id，无则用 sku_id 占位）
        int lineNo = 0;
        if (items.isArray()) {
            for (JsonNode it : items) {
                lineNo++;
                EcomPlatformOrderItem item = new EcomPlatformOrderItem();
                item.setOrderId(order.getId());
                item.setPlatformItemId(it.path("sku_id").asText(""));
                item.setLineNo(lineNo);
                String sellerSku = firstNonBlank(it.path("outer_id").asText(""), it.path("outer_goods_id").asText(""),
                        it.path("sku_id").isMissingNode() ? "" : "PDD-SKU-" + it.path("sku_id").asText(""));
                item.setSellerSku(sellerSku);
                item.setProductName(firstNonBlank(it.path("goods_name").asText(""),
                        (it.path("goods_spec").asText("").isBlank() ? "" : "规格:" + it.path("goods_spec").asText(""))));
                BigDecimal qty = it.path("goods_count").isNumber()
                        ? it.path("goods_count").decimalValue() : BigDecimal.ZERO;
                item.setQuantity(qty);
                BigDecimal unitPrice = fenToYuan(it.path("goods_price"));
                item.setUnitPrice(unitPrice);
                if (unitPrice != null && qty.signum() > 0) {
                    item.setItemTotal(unitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP));
                }
                // 拉单时把当时的商品映射写进快照
                if (!sellerSku.isBlank()) {
                    EcomProductMapping mapping = mappingMapper.selectOne(new QueryWrapper<EcomProductMapping>()
                            .eq("shop_id", shop.getId()).eq("seller_sku", sellerSku).last("LIMIT 1"));
                    if (mapping != null) {
                        item.setMappedMaterialId(mapping.getMaterialId());
                        item.setMappedMaterialCode(mapping.getMaterialCode());
                        item.setMappedMaterialName(mapping.getMaterialName());
                    }
                }
                itemMapper.insert(item);
            }
        }
    }

    private String joinPddAddress(JsonNode o) {
        List<String> parts = new ArrayList<>();
        for (String f : new String[]{"province", "city", "district", "town", "address", "detail_address", "receiver_address"}) {
            String v = o.path(f).asText("");
            if (!v.isBlank() && !parts.contains(v)) parts.add(v);
        }
        return String.join(" ", parts);
    }

    private String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    /** 拼多多金额单位为分 → 元（两位小数）；空/非法返回 null */
    private BigDecimal fenToYuan(JsonNode node) {
        if (node == null || !node.isNumber()) return null;
        return node.decimalValue().divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    /** 保存一张亚马逊平台订单 + 明细（映射快照在拉单时写入） */
    private void saveOneOrder(EcomShop shop, JsonNode o, String platformOrderId, String token, String host) {
        EcomPlatformOrder order = new EcomPlatformOrder();
        order.setPlatform("AMAZON");
        order.setShopId(shop.getId());
        order.setShopName(shop.getShopName());
        order.setPlatformOrderId(platformOrderId);
        String purchaseDate = o.path("PurchaseDate").asText(null);
        order.setPurchaseDate(parseAmazonTime(purchaseDate));
        order.setOrderStatus(o.path("OrderStatus").asText(""));
        order.setPaymentMethod(o.path("PaymentMethod").asText(""));
        order.setCurrencyCode(o.path("OrderTotal").path("CurrencyCode").asText(""));
        order.setOrderTotal(parseAmount(o.path("OrderTotal").path("Amount").asText(null)));
        int shipped = o.path("NumberOfItemsShipped").asInt(0);
        int unshipped = o.path("NumberOfItemsUnshipped").asInt(0);
        order.setItemCount(shipped + unshipped > 0 ? shipped + unshipped : null);
        JsonNode addr = o.path("ShippingAddress");
        order.setShipName(addr.path("Name").asText(""));
        order.setShipCity(addr.path("City").asText(""));
        order.setShipState(addr.path("StateOrRegion").asText(""));
        order.setShipCountry(addr.path("CountryCode").asText(""));
        order.setShipAddress(joinAddress(addr));
        order.setConverted(0);
        order.setPullTime(LocalDateTime.now());
        order.setRawJson(o.toString());
        baseMapper.insert(order);

        // 明细行
        JsonNode itemPayload = callSpApi(host + "/orders/v0/orders/" + urlEncode(platformOrderId) + "/orderItems",
                token, shop.getShopName());
        JsonNode items = itemPayload.path("OrderItems");
        int lineNo = 0;
        if (items.isArray()) {
            for (JsonNode it : items) {
                lineNo++;
                EcomPlatformOrderItem item = new EcomPlatformOrderItem();
                item.setOrderId(order.getId());
                item.setPlatformItemId(it.path("OrderItemId").asText(""));
                item.setLineNo(lineNo);
                item.setSellerSku(it.path("SellerSKU").asText(""));
                item.setAsin(it.path("ASIN").asText(""));
                item.setProductName(it.path("Title").asText(""));
                BigDecimal qty = it.path("QuantityOrdered").isNumber()
                        ? it.path("QuantityOrdered").decimalValue() : BigDecimal.ZERO;
                item.setQuantity(qty);
                BigDecimal lineTotal = parseAmount(it.path("ItemPrice").path("Amount").asText(null));
                item.setItemTotal(lineTotal);
                if (lineTotal != null && qty.signum() > 0) {
                    item.setUnitPrice(lineTotal.divide(qty, 6, RoundingMode.HALF_UP));
                }
                // 拉单时把当时的商品映射写进快照
                if (!item.getSellerSku().isBlank()) {
                    EcomProductMapping mapping = mappingMapper.selectOne(new QueryWrapper<EcomProductMapping>()
                            .eq("shop_id", shop.getId()).eq("seller_sku", item.getSellerSku()).last("LIMIT 1"));
                    if (mapping != null) {
                        item.setMappedMaterialId(mapping.getMaterialId());
                        item.setMappedMaterialCode(mapping.getMaterialCode());
                        item.setMappedMaterialName(mapping.getMaterialName());
                    }
                }
                itemMapper.insert(item);
            }
        }
        if (order.getItemCount() == null) {
            order.setItemCount(lineNo);
            lambdaUpdate().eq(EcomPlatformOrder::getId, order.getId())
                    .set(EcomPlatformOrder::getItemCount, lineNo).update();
        }
    }

    private JsonNode callSpApi(String url, String token, String shopName) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("x-amz-access-token", token)
                .header("Accept", "application/json")
                .header("User-Agent", "KaiyuERP/1.0")
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                String reason = translateSpApiError(body, response.code());
                log.warn("[电商平台] 店铺[{}]调用卖家平台接口失败: HTTP {} {}", shopName, response.code(), reason);
                throw new BusinessException("从卖家平台拉取订单失败：" + reason);
            }
            JsonNode root = objectMapper.readTree(body);
            return root.path("payload");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[电商平台] 店铺[{}]调用卖家平台接口异常", shopName, e);
            throw new BusinessException("连接卖家平台失败：" + e.getMessage());
        }
    }

    private String translateSpApiError(String body, int httpCode) {
        try {
            JsonNode json = objectMapper.readTree(body);
            JsonNode errors = json.path("errors");
            if (errors.isArray() && errors.size() > 0) {
                String code = errors.get(0).path("code").asText("");
                String msg = errors.get(0).path("message").asText("");
                if ("Unauthorized".equalsIgnoreCase(code) || httpCode == 403) {
                    return "访问令牌无效或店铺授权已失效，请到店铺授权页重新测试授权";
                }
                if (httpCode == 429 || code.contains("Throttl")) {
                    return "请求过于频繁被卖家平台限流，请稍后再试";
                }
                return msg.isBlank() ? ("卖家平台返回 " + httpCode) : msg;
            }
        } catch (Exception ignore) { /* 保持兜底文案 */ }
        return "卖家平台返回 " + httpCode;
    }

    // ==================== 转销售订单 ====================

    @Override
    @Transactional
    public Map<String, Object> convertToSaleOrder(Long id) {
        EcomPlatformOrder order = getById(id);
        if (order == null) throw new BusinessException("平台订单不存在或已被删除");
        if (order.getConverted() != null && order.getConverted() == 1) {
            throw new BusinessException("该平台订单已转销售订单[" + order.getConvertedSaleOrderNo() + "]，不能重复转单");
        }
        EcomShop shop = shopService.getById(order.getShopId());
        if (shop == null || shop.getAssociateCustomerId() == null) {
            throw new BusinessException("店铺[" + order.getShopName() + "]未设置关联客户，请先到店铺授权页补齐后再转单");
        }
        Customer customer = customerService.getById(shop.getAssociateCustomerId());
        if (customer == null) throw new BusinessException("店铺关联的客户不存在或已被删除，请重新选择");
        // 来源单据类型按平台：亚马逊订单 / 拼多多订单
        String sourceType = "PDD".equals(order.getPlatform()) ? "拼多多订单" : "亚马逊订单";

        List<EcomPlatformOrderItem> items = itemMapper.selectList(
                new QueryWrapper<EcomPlatformOrderItem>().eq("order_id", id).orderByAsc("id"));
        if (items.isEmpty()) throw new BusinessException("该平台订单没有明细行，无法转单");

        // 未映射的 SKU 先拦截，给出明确清单（映射先行是第一期口径）
        List<String> unmapped = items.stream()
                .filter(it -> it.getMappedMaterialId() == null)
                .map(it -> it.getSellerSku() + (it.getProductName() == null ? "" : "（" + it.getProductName() + "）"))
                .toList();
        if (!unmapped.isEmpty()) {
            throw new BusinessException("以下平台货品还没有建立商品映射，请先到商品映射页补齐：" + String.join("、", unmapped));
        }

        // 币种：按平台结算币种取，取不到回退本位币
        Currency currency = null;
        if (order.getCurrencyCode() != null && !order.getCurrencyCode().isBlank()) {
            currency = currencyService.getOne(new QueryWrapper<Currency>().eq("currency_code", order.getCurrencyCode()).last("LIMIT 1"));
        }
        if (currency == null) {
            currency = currencyService.getOne(new QueryWrapper<Currency>().eq("is_base_currency", 1).last("LIMIT 1"));
        }
        if (currency == null) throw new BusinessException("未找到结算币种[" + order.getCurrencyCode() + "]对应的币种档案，请先在基础资料-币种里维护");

        // 原子抢占转单标记（防并发双开单）；失败即说明已被转走
        int claimed = baseMapper.update(null, new UpdateWrapper<EcomPlatformOrder>()
                .eq("id", id).eq("converted", 0).set("converted", 1));
        if (claimed == 0) {
            throw new BusinessException("该平台订单已被其他用户转单，不能重复转单");
        }

        SaleOrder saleOrder = new SaleOrder();
        saleOrder.setOrderDate(order.getPurchaseDate() == null
                ? java.time.LocalDate.now() : order.getPurchaseDate().toLocalDate());
        saleOrder.setCustomerId(customer.getCustomerId());
        saleOrder.setCustomerNo(customer.getCustomerCode());
        saleOrder.setCustomerShortName(customer.getShortName());
        saleOrder.setContactPerson(customer.getContactPerson());
        saleOrder.setContactPhone(customer.getContactPhone());
        saleOrder.setCurrencyId(currency.getCurrencyId());
        saleOrder.setExchangeRate(currency.getExchangeRate() == null ? BigDecimal.ONE : currency.getExchangeRate());
        saleOrder.setReceiveAddress(order.getShipAddress());
        saleOrder.setSourceBillType(sourceType);
        saleOrder.setSourceBillNo(order.getPlatformOrderId());
        saleOrder.setCustomerOrderNo(order.getPlatformOrderId());
        saleOrder.setRemark("由" + sourceType + "[" + order.getPlatformOrderId() + "]转单生成（店铺：" + order.getShopName() + "）");

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<SaleOrderItem> saleItems = new ArrayList<>();
        int lineNo = 0;
        for (EcomPlatformOrderItem it : items) {
            lineNo++;
            Material material = materialService.getById(it.getMappedMaterialId());
            if (material == null) {
                throw new BusinessException("明细第 " + lineNo + " 行映射的物料[" + it.getMappedMaterialCode() + "]已不存在，请重新维护商品映射");
            }
            SaleOrderItem si = new SaleOrderItem();
            si.setLineNo(lineNo);
            si.setMaterialId(material.getMaterialId());
            si.setMaterialCode(material.getMaterialCode());
            si.setMaterialName(material.getMaterialName());
            si.setSpecification(material.getModel());
            si.setUnitId(material.getBaseUnitId());
            BigDecimal qty = it.getQuantity() == null ? BigDecimal.ZERO : it.getQuantity();
            BigDecimal lineTotal = it.getItemTotal() == null ? BigDecimal.ZERO : it.getItemTotal();
            si.setQuantity(qty);
            si.setUnitPrice(it.getUnitPrice());
            si.setAmount(lineTotal);
            si.setTaxRate(BigDecimal.ZERO);
            si.setTaxAmount(BigDecimal.ZERO);
            si.setNetAmount(lineTotal);
            si.setSourceBillType(sourceType);
            si.setSourceBillNo(order.getPlatformOrderId());
            si.setSourceLineNo(it.getLineNo());
            saleItems.add(si);
            totalAmount = totalAmount.add(lineTotal);
        }
        saleOrder.setItems(saleItems);
        saleOrder.setTotalAmount(totalAmount);
        saleOrder.setNetAmount(totalAmount);
        saleOrder.setTaxAmount(BigDecimal.ZERO);

        // 保存 = 草稿（沿用现有销售订单保存链路，单号自动生成）
        saleOrderService.saveOrderWithItems(saleOrder, saleItems);

        // 回写转换结果
        lambdaUpdate().eq(EcomPlatformOrder::getId, id)
                .set(EcomPlatformOrder::getConvertedSaleOrderId, saleOrder.getOrderId())
                .set(EcomPlatformOrder::getConvertedSaleOrderNo, saleOrder.getOrderNo())
                .update();

        log.info("[电商平台] 平台订单[{}]已转销售订单[{}]（草稿）", order.getPlatformOrderId(), saleOrder.getOrderNo());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saleOrderId", saleOrder.getOrderId());
        result.put("saleOrderNo", saleOrder.getOrderNo());
        return result;
    }

    // ==================== 工具 ====================

    private LocalDateTime parseAmazonTime(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String joinAddress(JsonNode addr) {
        if (addr.isMissingNode() || addr.isNull()) return "";
        List<String> parts = new ArrayList<>();
        for (String f : new String[]{"AddressLine1", "AddressLine2", "AddressLine3", "City", "StateOrRegion", "PostalCode", "CountryCode"}) {
            String v = addr.path(f).asText("");
            if (!v.isBlank()) parts.add(v);
        }
        return String.join(" ", parts);
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
