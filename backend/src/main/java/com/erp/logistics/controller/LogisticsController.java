package com.erp.logistics.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.config.BusinessException;
import com.erp.config.entity.SysConfig;
import com.erp.config.service.SysConfigService;
import com.erp.logistics.service.Kuaidi100Service;
import com.erp.logistics.service.LogisticsRoutingService;
import com.erp.logistics.service.SeventeenTrackService;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.mapper.SaleDeliveryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 物流查询 Controller
 *
 * 提供三个接口：
 *   1. GET  /api/logistics/query       - 单条物流查询（Edit/List抽屉用）
 *   2. GET  /api/logistics/in-transit  - 在途出库单列表（看板用）
 *   3. POST /api/logistics/batch-query - 批量查询（看板自动刷新用）
 *
 * 通道说明（多通道路由）：
 *   - 国内单 → 快递100（Kuaidi100Service）
 *   - 国际单 → 17TRACK（SeventeenTrackService）
 *   由 LogisticsRoutingService 按「单号格式 + 承运商关键字」自动判定，结果里带 channel 字段。
 */
@Slf4j
@RestController
@RequestMapping("/api/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final Kuaidi100Service kuaidi100Service;
    private final SeventeenTrackService seventeenTrackService;
    private final LogisticsRoutingService logisticsRoutingService;
    private final SaleDeliveryMapper saleDeliveryMapper;
    private final SysConfigService sysConfigService;
    /** 数据权限解析器（2026-10-09）：在途出库单按数据范围收敛 */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /**
     * 识别承运商（根据单号自动识别，不实际查询轨迹）
     * GET /api/logistics/detect-carrier?trackingNo=xxx
     *
     * 前端交互：
     *   - 用户输入一个单号
     *   - 前端先调此接口 → matched=true 直接用（carrierCode/Name回填出库单）
     *   - matched=false, candidates 非空 → 弹候选列表让用户选（绝不瞎猜第一个）
     *   - 否则提示用户手动选择承运商
     */
    @GetMapping("/detect-carrier")
    public Result<Kuaidi100Service.CarrierDetectResult> detectCarrier(@RequestParam String trackingNo) {
        if (trackingNo == null || trackingNo.trim().isEmpty()) {
            throw new BusinessException("物流单号不能为空");
        }
        return Result.success(kuaidi100Service.detectCarrier(trackingNo));
    }

    /**
     * 查询单条物流轨迹
     * GET /api/logistics/query?trackingNo=xxx&carrier=xxx&phone=xxx&carrierCode=xxx&phoneLast4=xxxx
     *
     * 核心（一个单号就能查）：
     *   - 只传 trackingNo 即可，不传carrier/carrierCode时后端自动识别承运商
     *   - 若后端识别到多个候选，返回 needUserSelectCarrier=true 并附 candidates，前端让用户选
     *   - 若识别结果 needPhone=true，前端显示手机号后四位输入框，再次查询传 phoneLast4
     *
     * @param trackingNo   物流单号（必填）
     * @param carrier      承运商中文名（如"顺丰""京东"，旧接口兼容用）
     * @param phone        收件人完整手机号（会自动提取后四位）
     * @param carrierCode  承运商代码（jd/shunfeng/...，优先级高于carrier中文名）
     * @param phoneLast4   手机号后四位（4位，与完整phone二选一，优先级更高）
     */
    @GetMapping("/query")
    public Result<Kuaidi100Service.LogisticsTrackResult> query(
            @RequestParam String trackingNo,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String carrierCode,
            @RequestParam(required = false) String phoneLast4) {
        if (trackingNo == null || trackingNo.trim().isEmpty()) {
            throw new BusinessException("物流单号不能为空");
        }
        Kuaidi100Service.LogisticsTrackResult result = routeQuery(
                carrier, trackingNo, phone, carrierCode, phoneLast4);
        return Result.success(result);
    }

    /**
     * 查询单条物流轨迹（按出库单ID）
     * GET /api/logistics/query/{deliveryId}
     * 自动从 sale_delivery 表读取 trackingNo / carrier / contactPhone
     */
    @GetMapping("/query/{deliveryId}")
    public Result<Kuaidi100Service.LogisticsTrackResult> queryByDelivery(@PathVariable Long deliveryId) {
        SaleDelivery delivery = saleDeliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException("出库单不存在");
        }
        if (delivery.getTrackingNo() == null || delivery.getTrackingNo().trim().isEmpty()) {
            throw new BusinessException("该出库单未填写物流单号");
        }
        Kuaidi100Service.LogisticsTrackResult result = routeQuery(
                delivery.getCarrier(), delivery.getTrackingNo(), delivery.getContactPhone(), null, null);
        return Result.success(result);
    }

    /**
     * 在途出库单列表
     * GET /api/logistics/in-transit
     * 返回所有已出库(approved)且填了物流单号的出库单，按出库时间倒序
     *
     * 可选参数：
     *   keyword - 模糊搜索（出库单号/客户名/物流单号）
     *   limit   - 限制返回数量（默认100）
     */
    @GetMapping("/in-transit")
    public Result<List<InTransitItem>> inTransit(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            jakarta.servlet.http.HttpServletRequest request) {
        QueryWrapper<SaleDelivery> wrapper = new QueryWrapper<>();
        // 已出库状态（审核通过的出库单，状态值为中文）
        wrapper.eq("status", "已生效");
        // 必须有物流单号
        wrapper.isNotNull("tracking_no").ne("tracking_no", "");
        // 关键词搜索
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("delivery_no", kw)
                    .or().like("tracking_no", kw)
                    .or().like("contact_person", kw));
        }
        // 数据权限（2026-10-09）：与销售出库单列表同口径，按 created_by + saler_id 收敛可见的出库单
        dataScopeResolver.resolve(request).applyTo(wrapper, "saler_id");
        wrapper.orderByDesc("delivery_date");
        wrapper.last("LIMIT " + Math.min(limit, 500));

        List<SaleDelivery> deliveries = saleDeliveryMapper.selectList(wrapper);

        // 批量查客户名（如果有客户表关联的简单做法，这里直接用 contact_person 作为显示名）
        List<InTransitItem> result = deliveries.stream()
                .map(this::toInTransitItem)
                .collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 批量查询物流（看板自动刷新用）
     * POST /api/logistics/batch-query
     * body: { "items": [{"trackingNo":"xxx","carrier":"顺丰","phone":"138****1234"}, ...] }
     *
     * 注意：受快递100频率限制（1秒1次），批量查询会串行调用并使用5分钟缓存
     * 建议每次批量 ≤ 20 单，避免单次刷新耗时过长
     */
    @PostMapping("/batch-query")
    public Result<List<Kuaidi100Service.LogisticsTrackResult>> batchQuery(
            @RequestBody BatchQueryRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        List<Kuaidi100Service.LogisticsTrackResult> results = new ArrayList<>();
        for (BatchItem item : request.getItems()) {
            try {
                Kuaidi100Service.LogisticsTrackResult r = routeQuery(
                        item.getCarrier(), item.getTrackingNo(), item.getPhone(), null, null);
                results.add(r);
                // 17TRACK 接口限频 3次/秒，国际单之间稍作停顿，避免触发 429
                if (LogisticsRoutingService.CHANNEL_17TRACK.equals(r.getChannel())) {
                    Thread.sleep(350);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("批量查询被中断: trackingNo={}", item.getTrackingNo());
                break;
            } catch (Exception e) {
                log.warn("批量查询单条失败: trackingNo={}, err={}", item.getTrackingNo(), e.getMessage());
                results.add(Kuaidi100Service.LogisticsTrackResult.fail("查询失败: " + e.getMessage()));
            }
        }
        return Result.success(results);
    }

    /**
     * 通道路由查询（国内走快递100，国际走17TRACK）
     *
     * @param carrier      承运商名称（可为空）
     * @param trackingNo   物流单号
     * @param phone        完整手机号（快递100用）
     * @param carrierCode  承运商编码（快递100用）
     * @param phoneLast4   手机号后四位（快递100用）
     */
    private Kuaidi100Service.LogisticsTrackResult routeQuery(String carrier, String trackingNo,
                                                             String phone, String carrierCode, String phoneLast4) {
        String channel = logisticsRoutingService.resolveChannel(carrier, trackingNo);
        Kuaidi100Service.LogisticsTrackResult result;
        if (LogisticsRoutingService.CHANNEL_17TRACK.equals(channel)) {
            log.info("通道路由 → 17TRACK国际: trackingNo={}, carrier={}", trackingNo, carrier);
            result = seventeenTrackService.query(trackingNo);
        } else {
            result = kuaidi100Service.query(trackingNo, carrier, phone, carrierCode, phoneLast4);
            // 明确标注通道，前端展示"由 快递100 提供"
            result.setChannel(LogisticsRoutingService.CHANNEL_KD100);
            result.setChannelName("快递100");
        }
        // 【2026-09-11 修复】失败分支不回填单号/承运商（前端会显示空值），此处统一兜底回填入参
        if (result != null) {
            if (result.getTrackingNo() == null || result.getTrackingNo().isEmpty()) {
                result.setTrackingNo(trackingNo);
            }
            if (result.getCarrierName() == null || result.getCarrierName().isEmpty()) {
                result.setCarrierName(carrier == null ? "" : carrier);
            }
        }
        return result;
    }

    /**
     * 物流看板自动刷新间隔（从 sys_config 读，默认30秒）
     * GET /api/logistics/refresh-interval
     */
    @GetMapping("/refresh-interval")
    public Result<Map<String, Object>> refreshInterval() {
        // 从 sys_config 读取配置项，前端看板启动时调用此接口决定刷新行为
        Map<String, Object> config = new HashMap<>();
        config.put("refreshInterval", sysConfigService.getIntValue("logistics.dashboard.refresh_interval", 30));
        config.put("maxItems", sysConfigService.getIntValue("logistics.dashboard.max_items", 50));
        config.put("autoRefresh", sysConfigService.getBooleanValue("logistics.dashboard.auto_refresh", true));
        return Result.success(config);
    }

    // ============= 快递100 授权配置（保密接口） =============

    /**
     * 查询快递100授权配置状态（脱敏）
     * GET /api/logistics/credentials
     *
     * 安全设计：
     *   - 永远不返回授权码明文
     *   - 仅返回是否已配置（configured: true/false）
     *   - 仅返回脱敏掩码（如 "AB***YZ"），仅用于UI展示当前状态
     *   - 启用状态可返回
     */
    @GetMapping("/credentials")
    public Result<Map<String, Object>> getCredentials() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("enabled", kuaidi100Service.isServiceEnabled());
        resp.put("configured", kuaidi100Service.isConfigured());
        // 脱敏掩码：与 isConfigured() 同源（sys_config → application.yml → 环境变量），
        // 避免"界面显示已配置、但掩码为空"的显示不一致（2026-09-11 修复）
        resp.put("customerMasked", kuaidi100Service.getCustomerMasked());
        // 配置时间
        String updatedAt = sysConfigService.getValue("__logistics.kuaidi100.updated_at__", "");
        resp.put("updatedAt", updatedAt);

        // ===== 17TRACK 国际物流（新增字段，旧前端忽略即可，不影响兼容） =====
        resp.put("track17Enabled", seventeenTrackService.isServiceEnabled());
        resp.put("track17Configured", seventeenTrackService.isConfigured());
        resp.put("track17TokenMasked", seventeenTrackService.getTokenMasked());
        resp.put("track17UpdatedAt", sysConfigService.getValue("__logistics.17track.updated_at__", ""));
        resp.put("track17Carriers", seventeenTrackService.getInternationalCarriersRaw());
        return Result.success(resp);
    }

    /**
     * 保存快递100授权配置
     * POST /api/logistics/credentials
     * body: { "customer": "xxx", "key": "yyy", "enabled": true }
     *
     * 安全设计：
     *   - POST 请求体走 HTTPS 加密传输（生产环境强制 HTTPS）
     *   - 保存到 sys_config 表（数据库层）
     *   - 不在日志中打印授权码明文
     *   - 前端密码框输入，浏览器 DOM 不明文显示
     *   - 保存后立即生效（Kuaidi100Service 每次查询动态读取）
     *   - 支持仅更新部分字段：customer/key 为空字符串则不更新该字段
     */
    @PostMapping("/credentials")
    public Result<Void> saveCredentials(@RequestBody CredentialsRequest req) {
        if (req == null) {
            throw new BusinessException("请求体不能为空");
        }
        // 更新 customer（非空才更新，避免误清空）
        if (req.getCustomer() != null && !req.getCustomer().trim().isEmpty()) {
            String c = req.getCustomer().trim();
            sysConfigService.updateValue(Kuaidi100Service.CFG_CUSTOMER, c);
            log.info("快递100 授权码已更新（不打印明文），长度={}", c.length());
        }
        // 更新 key
        if (req.getKey() != null && !req.getKey().trim().isEmpty()) {
            String k = req.getKey().trim();
            sysConfigService.updateValue(Kuaidi100Service.CFG_KEY, k);
            log.info("快递100 签名密钥已更新（不打印明文），长度={}", k.length());
        }
        // 更新 enabled
        if (req.getEnabled() != null) {
            sysConfigService.updateValue(Kuaidi100Service.CFG_ENABLED, String.valueOf(req.getEnabled()));
            log.info("快递100 启用状态已更新: enabled={}", req.getEnabled());
        }
        // 更新时间戳
        sysConfigService.updateValue("__logistics.kuaidi100.updated_at__",
                java.time.LocalDateTime.now().toString());

        // ===== 17TRACK 国际物流（新增，字段为空/未传表示不更新该字段） =====
        boolean track17Changed = false;
        if (req.getTrack17Token() != null && !req.getTrack17Token().trim().isEmpty()) {
            String t = req.getTrack17Token().trim();
            upsertConfig(SeventeenTrackService.CFG_TOKEN, t, "17TRACK密钥(17token)",
                    "logistics_17track", "password", null, 2,
                    "在 https://api.17track.net 注册后，登录后台「设置页」获取的 API 密钥(17token)");
            log.info("17TRACK 密钥已更新（不打印明文），长度={}", t.length());
            track17Changed = true;
        }
        if (req.getTrack17Enabled() != null) {
            upsertConfig(SeventeenTrackService.CFG_ENABLED, String.valueOf(req.getTrack17Enabled()),
                    "17TRACK国际物流开关", "logistics_17track", "boolean", null, 1,
                    "是否启用17TRACK国际物流查询。关闭后所有单号仍走快递100");
            log.info("17TRACK 启用状态已更新: enabled={}", req.getTrack17Enabled());
            track17Changed = true;
        }
        if (req.getTrack17Carriers() != null) {
            upsertConfig(SeventeenTrackService.CFG_INTL_CARRIERS, req.getTrack17Carriers().trim(),
                    "国际承运商关键字", "logistics_17track", "text", null, 4,
                    "逗号分隔。承运商名称包含其中任一关键字即判定为国际件，走17TRACK");
            track17Changed = true;
        }
        if (track17Changed) {
            sysConfigService.updateValue("__logistics.17track.updated_at__",
                    java.time.LocalDateTime.now().toString());
        }

        // 刷新 sys_config 缓存使配置立即生效
        sysConfigService.refreshCache();
        return Result.success(null);
    }

    /**
     * 保存 sys_config 项：行不存在时按完整元数据创建。
     * 原因：SysConfigService.updateValue 的行不存在时只会写入 key+value，
     * 造成"缺名称/缺分组/缺类型"的残缺配置项（设置中心里会显示异常）。
     * 这样老库没执行过 db/64_logistics_17track.sql 也能在保存时自动补齐。
     */
    private void upsertConfig(String key, String value, String name, String group,
                              String type, String options, int sort, String remark) {
        SysConfig existing = sysConfigService.getOne(new QueryWrapper<SysConfig>().eq("config_key", key));
        if (existing == null) {
            SysConfig c = new SysConfig();
            c.setConfigKey(key);
            c.setConfigValue(value);
            c.setConfigName(name);
            c.setConfigGroup(group);
            c.setConfigType(type);
            c.setConfigOptions(options);
            c.setSortOrder(sort);
            c.setRemark(remark);
            sysConfigService.save(c);
        } else {
            sysConfigService.updateValue(key, value);
        }
    }

    /**
     * 测试授权配置是否可用（用配置的授权码查一次测试单号）
     * POST /api/logistics/credentials/test
     * 用途：管理员配置后点"测试连接"按钮验证授权码是否正确
     */
    @PostMapping("/credentials/test")
    public Result<Map<String, Object>> testCredentials(
            @RequestParam(required = false) String channel) {
        Map<String, Object> resp = new HashMap<>();
        // 17TRACK 通道：调用 getquota 校验密钥（无参数、不消耗额度），并返回剩余单量
        if ("17TRACK".equalsIgnoreCase(channel)) {
            SeventeenTrackService.Track17Quota q = seventeenTrackService.testConnection();
            resp.put("success", q.isSuccess());
            resp.put("message", q.getMessage());
            resp.put("quotaRemain", q.getQuotaRemain());
            return Result.success(resp);
        }
        if (!kuaidi100Service.isConfigured()) {
            resp.put("success", false);
            resp.put("message", "授权码未配置");
            return Result.success(resp);
        }
        // 用测试单号查询（顺丰标准测试单号）
        Kuaidi100Service.LogisticsTrackResult r =
                kuaidi100Service.query("SF1234567890", "顺丰");
        resp.put("success", r.isSuccess());
        if (r.isSuccess()) {
            resp.put("message", "授权码有效，查询接口正常");
        } else {
            resp.put("message", r.getErrorMessage());
        }
        return Result.success(resp);
    }

    // ============= 私有方法 =============

    private InTransitItem toInTransitItem(SaleDelivery d) {
        InTransitItem item = new InTransitItem();
        item.setDeliveryId(d.getDeliveryId());
        item.setDeliveryNo(d.getDeliveryNo());
        item.setDeliveryDate(d.getDeliveryDate() != null ? d.getDeliveryDate().toString() : "");
        item.setCustomerId(d.getCustomerId());
        item.setCustomerName(d.getCustomerName() != null ? d.getCustomerName() : d.getContactPerson());
        item.setContactPerson(d.getContactPerson());
        item.setContactPhone(d.getContactPhone());
        item.setCarrier(d.getCarrier());
        item.setTrackingNo(d.getTrackingNo());
        return item;
    }

    // ============= DTO =============

    public static class InTransitItem {
        private Long deliveryId;
        private String deliveryNo;
        private String deliveryDate;
        private Long customerId;
        private String customerName;
        private String contactPerson;
        private String contactPhone;
        private String carrier;
        private String trackingNo;

        // getters / setters
        public Long getDeliveryId() { return deliveryId; }
        public void setDeliveryId(Long deliveryId) { this.deliveryId = deliveryId; }
        public String getDeliveryNo() { return deliveryNo; }
        public void setDeliveryNo(String deliveryNo) { this.deliveryNo = deliveryNo; }
        public String getDeliveryDate() { return deliveryDate; }
        public void setDeliveryDate(String deliveryDate) { this.deliveryDate = deliveryDate; }
        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public String getContactPerson() { return contactPerson; }
        public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
        public String getCarrier() { return carrier; }
        public void setCarrier(String carrier) { this.carrier = carrier; }
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    }

    public static class BatchQueryRequest {
        private List<BatchItem> items;
        public List<BatchItem> getItems() { return items; }
        public void setItems(List<BatchItem> items) { this.items = items; }
    }

    public static class BatchItem {
        private String trackingNo;
        private String carrier;
        private String phone;
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getCarrier() { return carrier; }
        public void setCarrier(String carrier) { this.carrier = carrier; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    /**
     * 快递100授权配置请求体
     * customer/key 为 null 或空字符串表示不更新该字段（避免误清空）
     */
    public static class CredentialsRequest {
        private String customer;
        private String key;
        private Boolean enabled;

        // ===== 17TRACK 国际物流（新增） =====
        /** 17TRACK 密钥(17token)，为空表示不更新 */
        private String track17Token;
        /** 17TRACK 国际物流开关，为 null 表示不更新 */
        private Boolean track17Enabled;
        /** 国际承运商关键字（逗号分隔），为 null 表示不更新；传空串表示清空 */
        private String track17Carriers;

        public String getCustomer() { return customer; }
        public void setCustomer(String customer) { this.customer = customer; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public String getTrack17Token() { return track17Token; }
        public void setTrack17Token(String track17Token) { this.track17Token = track17Token; }
        public Boolean getTrack17Enabled() { return track17Enabled; }
        public void setTrack17Enabled(Boolean track17Enabled) { this.track17Enabled = track17Enabled; }
        public String getTrack17Carriers() { return track17Carriers; }
        public void setTrack17Carriers(String track17Carriers) { this.track17Carriers = track17Carriers; }
    }
}
