package com.erp.common.qrcode;

import com.erp.common.util.SecurityUtils;
import com.erp.config.service.SysConfigService;
import com.erp.print.template.service.PrintDataFetcherService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫码解析服务（2026-09-27；同日 P1 加固：防伪造 + 作废提示 + 落地权限 + 扫码审计）
 *
 * 职责：把二维码里的"类型 + 主键"解析成"能不能用 + 标题 + 跳转目标"，供 PC/手机端扫码使用。
 * 服务端单一事实来源，避免各端重复维护映射。
 *
 * P1 对外发码安全（用户 2026-09-27 决定"会对外发码"）：
 *  1) 防伪造：校验码里的签名；不匹配=伪造。无签名=旧码，可按 qrcode.sign.strict 收紧为一律拒绝。
 *  2) 作废提示：目标已删除/作废/停用时明确提示，不再当成"没找到"。
 *  3) 落地权限：按当前登录用户的菜单路径判断"有没有这个模块的查看权限"；
 *     为避免误伤（菜单路径格式变化/老 token），只在"能确认路径格式"时才拦截。
 *  4) 扫码审计：每次解析写一条 qr_scan_log（谁/何时/扫了什么/结果），写失败不影响扫码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrCodeResolveService {

    /** 收紧开关：1 = 无签名的旧码一律拒绝（对外发码建议开启） */
    private static final String CFG_SIGN_STRICT = "qrcode.sign.strict";

    /** 已知模块菜单前缀：用于判断"用户菜单路径格式是否认识"，避免误伤 */
    private static final List<String> KNOWN_MENU_PREFIXES = Arrays.asList(
            "/sale", "/purchase", "/production", "/inventory", "/subcontract", "/finance",
            "/sample", "/basedata", "/report", "/crm", "/customer", "/system", "/logistics", "/mrp");

    private final com.erp.masterdata.mapper.MaterialMapper materialMapper;
    private final com.erp.production.mapper.WorkOrderOperationMapper workOrderOperationMapper;
    private final PrintDataFetcherService printDataFetcherService;
    private final QrCodeSigner qrCodeSigner;
    private final SysConfigService sysConfigService;
    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> resolve(String type, String docType, Long id, String signature,
                                       String tenant, HttpServletRequest request) {
        Map<String, Object> r = new LinkedHashMap<>();
        String t = type == null ? "" : type.trim().toUpperCase();
        r.put("type", t);
        r.put("docType", docType);
        r.put("id", id);

        String invalid = null;      // 不可用原因（null = 可用）
        boolean found = false;
        boolean signed = signature != null && !signature.isBlank();
        r.put("signed", signed);

        // ---------- 1. 防伪造 + 公司归属 ----------
        if (found == false && invalid == null) {
            if (signed) {
                if (!qrCodeSigner.verify(t, docType, id, signature, tenant)) {
                    invalid = "二维码签名不匹配，可能不是本系统打印的码（请核对来源）";
                }
            } else if ("1".equals(sysConfigService.getValue(CFG_SIGN_STRICT, "0"))) {
                invalid = "二维码缺少签名（旧版码或伪造码），当前已开启严格校验";
            }
            // 公司归属（2026-10-25）：新码带公司标识并与签名绑定；扫到别家公司的码一律拒绝。
            // 旧码没有公司标识 → 按兼容放行（不破坏已打印的历史标签）。
            if (invalid == null && tenant != null && !tenant.isBlank()) {
                String cur = com.erp.tenant.TenantContextHolder.getTenantDbName();
                if (cur != null && !cur.isBlank() && !tenant.trim().equals(cur.trim())) {
                    invalid = "该二维码不属于当前公司";
                }
            }
        }

        // ---------- 2. 目标存在性 / 作废状态 ----------
        String title = "";
        String subtitle = "";
        String navPath = "";
        String navQuery = "";
        Object material = null;
        Object operation = null;

        if (invalid == null) {
            if (QrCodeContent.TYPE_MATERIAL.equals(t)) {
                com.erp.masterdata.entity.Material m = id == null ? null : materialMapper.selectById(id);
                if (m == null) {
                    invalid = "该商品不存在或已被删除";
                } else if (m.getStatus() != null && !"正常".equals(m.getStatus())) {
                    invalid = "该商品已停用（" + m.getStatus() + "），二维码不再可用";
                } else {
                    found = true;
                    material = m;
                    title = m.getMaterialName();
                    subtitle = buildMaterialSubtitle(m);
                }
            } else if (QrCodeContent.TYPE_BILL.equals(t)) {
                Map<String, Object> data = null;
                try {
                    data = printDataFetcherService.fetchBillData(docType, id);
                } catch (Exception e) {
                    log.warn("扫码解析取单据失败 type={} id={}: {}", docType, id, e.getMessage());
                }
                if (data == null || data.isEmpty()) {
                    invalid = "该单据不存在或已被删除";
                } else {
                    String status = str(data.get("status"));
                    if (status != null && status.contains("作废")) {
                        invalid = "该单据已作废，二维码不再可用";
                    } else {
                        found = true;
                        title = printDataFetcherService.getBillTypeName(docType);
                        String billNo = firstStr(data, "billNo", "orderNo", "deliveryNo", "receiptNo", "returnNo",
                                "invoiceNo", "contractNo", "quoteNo", "requestNo", "pickNo", "transferNo",
                                "reportNo", "inboundNo", "supplementNo", "planNo", "sampleNo");
                        subtitle = (billNo == null ? "" : ("单号：" + billNo + "　"))
                                + (status == null ? "" : ("状态：" + status));
                        navPath = "pages/common/bill-detail";
                        navQuery = "type=" + enc(docType) + "&id=" + id;
                    }
                }
            } else if (QrCodeContent.TYPE_OPERATION.equals(t)) {
                com.erp.production.entity.WorkOrderOperation op = id == null ? null : workOrderOperationMapper.selectById(id);
                if (op == null) {
                    invalid = "该工序不存在或已被删除";
                } else {
                    found = true;
                    operation = op;
                    title = op.getOperationName();
                    subtitle = "工单：" + (op.getOrderNo() == null ? "" : op.getOrderNo());
                    navPath = "pages/production/operation-panel";
                    navQuery = "opId=" + id + (op.getOrderId() == null ? "" : ("&orderId=" + op.getOrderId()));
                }
            } else {
                invalid = "不是本系统的二维码";
            }
        }

        // ---------- 3. 落地权限（仅对"确实存在的记录"判定） ----------
        if (invalid == null && found) {
            String deny = checkModulePermission(t, docType, request);
            if (deny != null) {
                invalid = deny;
            }
        }

        // ---------- 4. 输出 + 审计 ----------
        boolean valid = invalid == null && found;
        r.put("valid", valid);
        r.put("found", found);
        r.put("invalidReason", invalid);
        r.put("title", invalid != null && !found ? (QrCodeContent.TYPE_BILL.equals(t) ? "" : title) : title);
        r.put("subtitle", subtitle);
        r.put("navPath", valid ? navPath : "");
        r.put("navQuery", valid ? navQuery : "");
        if (material != null) r.put("material", material);
        if (operation != null) r.put("operation", operation);

        writeScanLog(request, t, docType, id, signed, valid, invalid);
        return r;
    }

    /**
     * 落地权限：管理员直接放行；用户菜单路径为空或格式不认识时放行（不误伤）；
     * 只有"能确认路径格式，且确实没有该模块路径"时才拒绝。
     */
    private String checkModulePermission(String type, String docType, HttpServletRequest request) {
        if (request == null) return null;
        try {
            if (SecurityUtils.isAdmin(request)) return null;
            List<String> paths = SecurityUtils.getCurrentMenuPaths(request);
            // 【2026-10-25】无权限口径收紧：菜单为空 = 该账号没有任何模块权限，一律拒绝。
            // 原写法"为空则放行"会导致没配权限的人反而能看到单据（与用户口径"无权限只提示无权限"相反）。
            if (paths == null || paths.isEmpty()) return "没有查看该模块的权限，请联系管理员开通";
            if (paths.contains("*")) return null;
            String prefix = modulePrefix(type, docType);
            if (prefix == null || prefix.isBlank()) return null;
            for (String p : paths) {
                if (p == null) continue;
                if (p.startsWith(prefix)) return null;
            }
            boolean formatRecognized = paths.stream().anyMatch(p -> p != null
                    && KNOWN_MENU_PREFIXES.stream().anyMatch(p::startsWith));
            if (!formatRecognized) return null;
            return "没有查看该模块的权限，请联系管理员开通";
        } catch (Exception e) {
            log.warn("扫码落地权限校验失败，按放行处理: {}", e.getMessage());
            return null;
        }
    }

    /** 单据/商品类型 → 所属模块菜单前缀（用于落地权限判定） */
    private String modulePrefix(String type, String docType) {
        if (QrCodeContent.TYPE_MATERIAL.equals(type)) return "/basedata";
        if (QrCodeContent.TYPE_OPERATION.equals(type)) return "/production";
        if (docType == null) return null;
        String d = docType.trim().toUpperCase();
        if (d.startsWith("SALE_")) return "/sale";
        if (d.startsWith("PURCHASE_")) return "/purchase";
        if (d.startsWith("SUBCONTRACT_")) return "/subcontract";
        if (d.startsWith("STOCK_") || d.startsWith("OTHER_")) return "/inventory";
        if (d.startsWith("PAYMENT_") || d.startsWith("COST_") || d.startsWith("SALE_PRE_")
                || d.startsWith("PURCHASE_PRE_")) return "/finance";
        if (d.startsWith("SAMPLE") || d.startsWith("FREE_SAMPLE")) return "/sample";
        if (d.startsWith("WORK_ORDER") || d.startsWith("PRODUCTION_") || d.startsWith("OPERATION_")
                || d.startsWith("PROCESS_") || d.startsWith("MRP_")) return "/production";
        if (d.startsWith("MATERIAL_REQUISITION")) return "/inventory";
        return null;
    }

    /** 审计日志：写失败只告警，绝不阻断扫码 */
    private void writeScanLog(HttpServletRequest request, String type, String docType, Long id,
                              boolean signed, boolean valid, String reason) {
        try {
            Long userId = request == null ? null : SecurityUtils.getCurrentUserId(request);
            String username = request == null ? null : SecurityUtils.getCurrentUsername(request);
            String ua = request == null ? null : request.getHeader("User-Agent");
            String ip = request == null ? null : clientIp(request);
            jdbcTemplate.update(
                    "INSERT INTO qr_scan_log (user_id, username, code_type, doc_type, biz_id, signed, result, reason, client, ip) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    userId, username, type, docType, id, signed ? 1 : 0, valid ? "OK" : "REJECTED",
                    reason == null ? null : truncate(reason, 200),
                    ua == null ? null : truncate(ua, 200), ip);
        } catch (Exception e) {
            log.warn("扫码审计日志写入失败（不影响扫码）: {}", e.getMessage());
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }

    private String buildMaterialSubtitle(com.erp.masterdata.entity.Material m) {
        StringBuilder sb = new StringBuilder();
        if (m.getMaterialCode() != null && !m.getMaterialCode().isBlank()) {
            sb.append("编号：").append(m.getMaterialCode());
        }
        if (m.getModel() != null && !m.getModel().isBlank()) {
            if (sb.length() > 0) sb.append("　");
            sb.append("型号：").append(m.getModel());
        }
        return sb.toString();
    }

    private String firstStr(Map<String, Object> data, String... keys) {
        if (data == null) return null;
        for (String k : keys) {
            Object v = data.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return null;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
