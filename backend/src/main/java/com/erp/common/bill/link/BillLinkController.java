package com.erp.common.bill.link;

import com.erp.common.Result;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 单据关联查询接口（来源单据 / 去向单据），供前端通用组件 BillLinkPanel 调用。
 *
 * <p>安全：billType 白名单格式校验 + 注册表命中校验；billId 必须为正整数；
 * 未登记的单据类型直接返回空结构，不做任何查询。</p>
 *
 * <p>功能开关：来源/去向属定制功能，用 sys_config 键 {@code ui.bill_link.enabled} 控制本部署是否可见
 * （设置中心 → 系统参数 可配）。关闭时本接口一律按"未登记"返回
 * （/registered 返回 false、/sources 与 /targets 返回空列表），前端页签随之不渲染——
 * 前端零改动。默认 true = 与未加开关前的行为完全一致。</p>
 */
@RestController
@RequestMapping("/api/bill-link")
@RequiredArgsConstructor
public class BillLinkController {

    private final BillLinkService billLinkService;
    private final BillPushStatusService billPushStatusService;
    private final SysConfigService sysConfigService;

    /** 单据类型编码格式白名单（如 SALE_ORDER / ACCOUNT_RECEIVABLE） */
    private static final Pattern BILL_TYPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");

    /** 来源/去向页签总开关（sys_config 键，设置中心「系统参数」分组可配） */
    private static final String CFG_ENABLED = "ui.bill_link.enabled";
    /** 默认开启：保证未配置该键的存量部署升级后行为与改动前完全一致 */
    private static final boolean CFG_ENABLED_DEFAULT = true;

    @GetMapping("/registered")
    public Result<Boolean> registered(@RequestParam String billType) {
        if (!billLinkEnabled()) return Result.success(false);
        if (!isValidType(billType)) return Result.success(false);
        return Result.success(billLinkService.isRegistered(billType));
    }

    @GetMapping("/sources")
    public Result<Map<String, Object>> sources(@RequestParam String billType, @RequestParam Long billId) {
        if (!billLinkEnabled()) return Result.success(emptyResult());
        validate(billType, billId);
        return Result.success(billLinkService.sources(billType, billId));
    }

    @GetMapping("/targets")
    public Result<Map<String, Object>> targets(@RequestParam String billType, @RequestParam Long billId) {
        if (!billLinkEnabled()) return Result.success(emptyResult());
        validate(billType, billId);
        return Result.success(billLinkService.targets(billType, billId));
    }

    /**
     * 批量下推状态汇总（列表页状态列，2026-10-10 新增）。
     *
     * <p>入参：billType + 逗号分隔主键（单页列表一批）。返回：{ "订单主键": { delivery/invoice/production: { state, ... } } }。
     * 未登记的单据类型返回空 Map（列表页通用调用，不报错）；关闭来源/去向开关时同样返回空。</p>
     */
    @GetMapping("/push-status")
    public Result<Map<String, Map<String, Object>>> pushStatus(@RequestParam String billType, @RequestParam String ids) {
        if (!billLinkEnabled()) return Result.success(Collections.emptyMap());
        if (!isValidType(billType)) throw new BusinessException("单据类型不合法: " + billType);
        List<Long> idList = new ArrayList<>();
        if (ids != null && !ids.isBlank()) {
            for (String token : ids.split(",")) {
                try {
                    long v = Long.parseLong(token.trim());
                    if (v > 0) idList.add(v);
                } catch (NumberFormatException ignore) {
                    // 非数字片段直接跳过（列表页只传本页主键，正常不会出现）
                }
                if (idList.size() >= BillPushStatusService.MAX_IDS) break;
            }
        }
        if (idList.isEmpty()) return Result.success(Collections.emptyMap());
        if (!billLinkService.isRegistered(billType)) return Result.success(Collections.emptyMap());
        return Result.success(billPushStatusService.summarize(billType, idList));
    }

    /**
     * 来源/去向页签是否启用。
     * 配置缺失或读取异常一律按默认值 true 处理——绝不因配置问题让功能不可用或报错。
     */
    private boolean billLinkEnabled() {
        try {
            return sysConfigService.getBooleanValue(CFG_ENABLED, CFG_ENABLED_DEFAULT);
        } catch (Exception e) {
            return CFG_ENABLED_DEFAULT;
        }
    }

    /** 开关关闭时返回的空结构：与"单据类型未登记"完全一致，避免前端出现解析差异 */
    private Map<String, Object> emptyResult() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registered", false);
        out.put("list", new ArrayList<>());
        return out;
    }

    private void validate(String billType, Long billId) {
        if (!isValidType(billType)) {
            throw new BusinessException("单据类型不合法: " + billType);
        }
        if (billId == null || billId <= 0) {
            throw new BusinessException("单据ID不合法");
        }
        if (!billLinkService.isRegistered(billType)) {
            throw new BusinessException("该单据类型未登记来源/去向配置: " + billType);
        }
    }

    private boolean isValidType(String billType) {
        return billType != null && BILL_TYPE_PATTERN.matcher(billType).matches();
    }
}
