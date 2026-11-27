package com.erp.kai.controller;

import com.erp.common.Result;
import com.erp.kai.service.KaiAuthService;
import com.erp.kai.service.KaiCreditService;
import com.erp.kai.service.KaiModelService;
import com.erp.kai.service.KaiRechargeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 小楷独立版 · 运营管理（2026-10-18）。
 * 仅运营管理员可用：用户列表、单用户积分调整、启停账号、全局配置、内置模型。
 */
@Slf4j
@RestController
@RequestMapping("/api/kai-standalone/admin")
@RequiredArgsConstructor
public class KaiAdminController {

    private final KaiCreditService creditService;
    private final KaiAuthService authService;
    private final KaiModelService modelService;
    private final KaiRechargeService rechargeService;

    @GetMapping("/users")
    public Result<?> users(HttpServletRequest request) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        return Result.success(creditService.listUsers());
    }

    @PostMapping("/users/{id}/credits")
    public Result<?> adjust(HttpServletRequest request, @PathVariable Long id,
                            @RequestBody Map<String, Object> body) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        try {
            BigDecimal credits = body.get("credits") == null ? null
                    : new BigDecimal(String.valueOf(body.get("credits")));
            String remark = body.get("remark") == null ? "" : String.valueOf(body.get("remark"));
            BigDecimal after = creditService.adjust(id, credits, remark);
            return Result.success(Map.of("balance", after));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "调整失败：" + e.getMessage());
        }
    }

    /** 单个客户的 token 消耗明细（月度 + 最近调用）。 */
    @GetMapping("/users/{id}/usage")
    public Result<?> userUsage(HttpServletRequest request, @PathVariable Long id) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        return Result.success(creditService.userUsage(id));
    }

    /** 修改客户资料（称呼/公司/手机号/邮箱/运营备注）。 */
    @PostMapping("/users/{id}/profile")
    public Result<?> updateProfile(HttpServletRequest request, @PathVariable Long id,
                                   @RequestBody Map<String, Object> body) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        try {
            authService.adminUpdateProfile(id, body);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "保存失败：" + e.getMessage());
        }
    }

    @PostMapping("/users/{id}/status")
    public Result<?> status(HttpServletRequest request, @PathVariable Long id,
                            @RequestBody Map<String, Object> body) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        int status = body.get("status") == null ? 1 : Integer.parseInt(String.valueOf(body.get("status")));
        creditService.setStatus(id, status);
        return Result.success();
    }

    @GetMapping("/config")
    public Result<?> config(HttpServletRequest request) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        return Result.success(creditService.adminConfig());
    }

    @PostMapping("/config")
    public Result<?> saveConfig(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        try {
            creditService.saveConfig(body);
            return Result.success(creditService.adminConfig());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "保存失败：" + e.getMessage());
        }
    }

    // ================= 内置模型（2026-10-20） =================

    @GetMapping("/builtin-models")
    public Result<?> builtinModels(HttpServletRequest request) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        return Result.success(modelService.listBuiltin());
    }

    /** 新增/更新内置模型。body: {id?, name, baseUrl, model, rate?, enabled?, apiKey?, reuseFromId?} */
    @PostMapping("/builtin-models")
    public Result<?> saveBuiltinModel(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        try {
            modelService.saveBuiltin(body);
            return Result.success(modelService.listBuiltin());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "保存失败：" + e.getMessage());
        }
    }

    @PostMapping("/builtin-models/{id}/toggle")
    public Result<?> toggleBuiltinModel(HttpServletRequest request, @PathVariable Long id,
                                        @RequestParam boolean enabled) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        modelService.toggleBuiltin(id, enabled);
        return Result.success(modelService.listBuiltin());
    }

    @DeleteMapping("/builtin-models/{id}")
    public Result<?> deleteBuiltinModel(HttpServletRequest request, @PathVariable Long id) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        modelService.deleteBuiltin(id);
        return Result.success(modelService.listBuiltin());
    }

    // ================= 支付宝充值（2026-11-07） =================

    @GetMapping("/pay/config")
    public Result<?> payConfig(HttpServletRequest request) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        return Result.success(rechargeService.adminConfig());
    }

    @PostMapping("/pay/config")
    public Result<?> savePayConfig(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        try {
            rechargeService.saveConfig(body);
            return Result.success(rechargeService.adminConfig());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "保存失败：" + e.getMessage());
        }
    }

    @GetMapping("/pay/packages")
    public Result<?> payPackages(HttpServletRequest request) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        return Result.success(rechargeService.listAllPackages());
    }

    @PostMapping("/pay/packages")
    public Result<?> savePayPackage(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        try {
            rechargeService.savePackage(body);
            return Result.success(rechargeService.listAllPackages());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "保存失败：" + e.getMessage());
        }
    }

    @PostMapping("/pay/packages/{id}/toggle")
    public Result<?> togglePayPackage(HttpServletRequest request, @PathVariable Long id,
                                      @RequestParam boolean enabled) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        rechargeService.togglePackage(id, enabled);
        return Result.success(rechargeService.listAllPackages());
    }

    @DeleteMapping("/pay/packages/{id}")
    public Result<?> deletePayPackage(HttpServletRequest request, @PathVariable Long id) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        try {
            rechargeService.deletePackage(id);
            return Result.success(rechargeService.listAllPackages());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "删除失败：" + e.getMessage());
        }
    }

    @GetMapping("/pay/orders")
    public Result<?> payOrders(HttpServletRequest request,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String keyword) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        return Result.success(rechargeService.adminOrders(status, keyword));
    }

    /** 向支付宝主动查单补记。 */
    @PostMapping("/pay/orders/{orderNo}/recheck")
    public Result<?> recheckPayOrder(HttpServletRequest request, @PathVariable String orderNo) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        try {
            return Result.success(rechargeService.adminRecheck(orderNo));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "查单失败：" + e.getMessage());
        }
    }

    /** 手工补记：仅对未支付订单生效，防止重复加积分。 */
    @PostMapping("/pay/orders/{orderNo}/mark-paid")
    public Result<?> markPaidPayOrder(HttpServletRequest request, @PathVariable String orderNo,
                                      @RequestBody(required = false) Map<String, Object> body) {
        if (!isAdmin(request)) return Result.error(403, "仅运营管理员可操作");
        try {
            String remark = body == null || body.get("remark") == null ? "" : String.valueOf(body.get("remark"));
            return Result.success(rechargeService.adminMarkPaid(orderNo, remark));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, "补记失败：" + e.getMessage());
        }
    }

    private boolean isAdmin(HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("kaiUserId");
        return uid != null && creditService.isAdmin(uid);
    }
}
