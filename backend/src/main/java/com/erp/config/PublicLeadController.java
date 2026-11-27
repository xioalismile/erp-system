package com.erp.config;

import com.erp.common.Result;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 官网公开线索收集接口（免登录）。
 * <p>
 * 入口：宣传页 promo.html /promo.html 「立即体验」表单。
 * 出口：CRM 客户表 customer，归属 admin 私海（poolType=0, ownerUserId=1），
 * 客户来源=网站注册（sys_dict customer_source 内置字典项），
 * 来源渠道=官网宣传页。
 * <p>
 * 安全：手机号必填且校验正则；同手机号 60 秒冷却；同 IP 每小时最多 10 条。
 * 不暴露任何查询/修改能力，仅单向写入 customer 表。
 */
@Slf4j
@RestController("publicLeadController")
@RequestMapping("/api/public/lead")
@RequiredArgsConstructor
@Validated
public class PublicLeadController {

    private final CustomerService customerService;

    /** 手机号冷却：phone → 上次创建 epoch ms，60s 内直接拒绝 */
    private final Map<String, Long> phoneCooldown = new ConcurrentHashMap<>();
    /** IP 限流：ipAtomic 桶每小时最多 10 条；简单滚动窗口（不严格精确，但够用） */
    private final ConcurrentHashMap<String, IpBucket> ipBuckets = new ConcurrentHashMap<>();
    private static final long PHONE_COOLDOWN_MS = 60_000L;
    private static final int IP_HOURLY_LIMIT = 10;
    private static final long IP_WINDOW_MS = 3_600_000L;

    @Data
    public static class LeadDTO {
        @Size(max = 30, message = "联系人最多30字")
        @NotBlank(message = "联系人不能为空")
        private String contactName;

        @Size(max = 60, message = "公司名称最多60字")
        @NotBlank(message = "公司名称不能为空")
        private String customerName;

        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String contactPhone;

        @Size(max = 500, message = "需求描述最多500字")
        private String customerDemand;
    }

    private static class IpBucket {
        long start;
        final AtomicInteger count = new AtomicInteger(0);
    }

    @PostMapping
    public Result<Map<String, String>> submit(@Valid @RequestBody LeadDTO dto) {
        // 1. 手机号冷却（60s 同号不重复灌库）
        Long last = phoneCooldown.put(dto.getContactPhone(), System.currentTimeMillis());
        if (last != null && System.currentTimeMillis() - last < PHONE_COOLDOWN_MS) {
            return Result.error("提交太频繁，请稍后再试");
        }
        // 2. IP 限流：每小时 10 条
        String ip = resolveClientIp();
        IpBucket b = ipBuckets.compute(ip, (k, old) -> {
            long now = System.currentTimeMillis();
            if (old == null || now - old.start > IP_WINDOW_MS) {
                IpBucket nb = new IpBucket(); nb.start = now; return nb;
            }
            return old;
        });
        if (b.count.incrementAndGet() > IP_HOURLY_LIMIT) {
            // 补偿：超限时把计数再 -1（不占用配额指标），但仍拒绝
            b.count.decrementAndGet();
            return Result.error("提交过于频繁，请稍后再试");
        }

        try {
            // 3. 客户编号：官网留资是免登录入口，没有"手动输入编号"的机会，必须由系统生成。
            // 原实现按系统参数 sys.auto.customer.no 二选一，该开关已下线（客户编号统一由
            // 「设置中心 → 自动编号设置 → 客户档案」规则管理），故这里恒走生成逻辑，不再依赖开关。
            String code = generateCustomerNo();
            if (customerService.existsByCustomerCode(code)) {
                // 毫秒级冲突再生成一次
                code = generateCustomerNo() + "A";
            }

            Customer c = new Customer();
            c.setCustomerCode(code);
            c.setCustomerName(dto.getCustomerName().trim());
            c.setContactPerson(dto.getContactName().trim());
            c.setContactPhone(dto.getContactPhone().trim());
            if (dto.getCustomerDemand() != null && !dto.getCustomerDemand().isBlank()) {
                c.setCustomerDemand(dto.getCustomerDemand().trim());
            }
            c.setSourceType("网站注册");   // 字典 customer_source 内置：线上推广/线上广告/线下展会/客户转介绍/市场活动/网站注册/电话咨询/其他
            c.setSourceDetail("官网宣传页");
            c.setCustomerType("企业客户");
            c.setCustomerLevel("普通客户");
            c.setCrmStatus("潜在客户");
            c.setImportantLevel("普通");
            c.setPoolType(0);                 // 私海（1=公海）
            c.setOwnerUserId(1L);             // 归属 admin（本地/8cloud 两库 user_id=1 均是 admin）
            c.setStatus("正常");
            c.setIsBlacklist(0);
            c.setSatisfactionScore(0);
            c.setComplaintCount(0);
            c.setBusinessOpportunityCount(0);
            c.setOrderCount(0);
            c.setFollowUpCount(0);
            c.setAnnualRevenue(java.math.BigDecimal.ZERO);
            c.setPotentialValue(java.math.BigDecimal.ZERO);
            c.setRefundAmount(java.math.BigDecimal.ZERO);
            c.setTotalOrderAmount(java.math.BigDecimal.ZERO);

            customerService.save(c);
            log.info("官网线索入库: customerCode={}, phone={}, company={}",
                    c.getCustomerCode(), maskPhone(c.getContactPhone()), c.getCustomerName());
            return Result.success(Map.of(
                    "customerCode", c.getCustomerCode(),
                    "tip", "已收到您的信息，我们会尽快与您联系"
            ));
        } catch (Exception e) {
            // 发生异常释放冷却，避免用户被无意义冷却卡死
            phoneCooldown.remove(dto.getContactPhone());
            b.count.decrementAndGet();
            // 客户名称重复拦截等友好提示
            String msg = e.getMessage() == null ? "提交失败" : e.getMessage();
            if (msg.contains("duplicate") || msg.toLowerCase().contains("duplicate name")) {
                msg = "该公司名称已记录，我们已看到您的意向，会尽快联系您";
            }
            log.warn("官网线索写入失败: {}, phone={}", msg, maskPhone(dto.getContactPhone()), e);
            return Result.error(msg);
        }
    }

    private String generateCustomerNo() {
        String dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "CUST" + dateStr;
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Customer> qw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        qw.likeRight("customer_code", prefix).orderByDesc("customer_code").last("LIMIT 1");
        Customer last = customerService.getOne(qw);
        int seq = 1;
        if (last != null && last.getCustomerCode() != null) {
            try {
                String tail = last.getCustomerCode().substring(prefix.length());
                seq = Integer.parseInt(tail) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }

    private String maskPhone(String p) {
        if (p == null || p.length() < 7) return p;
        return p.substring(0, 3) + "****" + p.substring(p.length() - 4);
    }

    /**
     * 取真实客户端 IP：本地 dev 前端与后端同机时 hostIp 是 127.0.0.1 也能正确工作。
     */
    private static String resolveClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest req = attrs == null ? null : attrs.getRequest();
        if (req == null) return "127.0.0.1";
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
        return (ip == null || ip.isEmpty()) ? "127.0.0.1" : ip;
    }
}
