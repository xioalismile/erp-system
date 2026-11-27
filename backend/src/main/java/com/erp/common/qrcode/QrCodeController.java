package com.erp.common.qrcode;

import com.erp.common.Result;
import com.erp.config.service.SysConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 二维码接口（2026-09-27）
 *
 * - GET /api/qrcode            → 返回 PNG 图片（可直接用于 <img>，需带登录令牌）
 * - GET /api/qrcode/data-uri   → 返回内联 data:image/png;base64,...（打印模板 / 前端画布用）
 * - GET /api/qrcode/content    → 返回码内容文本（前端展示/复制/自绘用）
 *
 * 参数：t=码类型(MAT/BILL/OP)，dt=单据类型(仅 BILL)，id=主键，size=像素(60~600)；
 *       也可直接传 content 指定任意文本（需登录）。
 * 域名来源：系统参数 qrcode.base_url 优先；未配置时按当前请求的域名自动生成（换域名不必改码）。
 */
@RestController
@RequestMapping("/api/qrcode")
public class QrCodeController {

    private static final String CFG_BASE_URL = "qrcode.base_url";

    private final QrCodeService qrCodeService;
    private final SysConfigService sysConfigService;
    private final QrCodeResolveService qrCodeResolveService;
    private final LabelPrintService labelPrintService;
    private final QrCodeSigner qrCodeSigner;

    public QrCodeController(QrCodeService qrCodeService, SysConfigService sysConfigService,
                            QrCodeResolveService qrCodeResolveService, LabelPrintService labelPrintService,
                            QrCodeSigner qrCodeSigner) {
        this.qrCodeService = qrCodeService;
        this.sysConfigService = sysConfigService;
        this.qrCodeResolveService = qrCodeResolveService;
        this.labelPrintService = labelPrintService;
        this.qrCodeSigner = qrCodeSigner;
    }

    /**
     * 批量标签打印：多选 + 份数 + 连排 + 条码/二维码双打，返回整页 HTML 供浏览器打印
     *
     * @param t       MATERIAL（商品标签）/ WORK_OPERATION（工序卡）
     * @param ids     主键，逗号分隔
     * @param copies  份数（默认 1）
     * @param paper   纸张 A4-3x8（默认）/ 60x40 / 50x30
     * @param barcode 是否双打一维条码（1=是，默认 1）
     * @param fields  标签字段 name,code,spec,unit,fullName（默认 name,code,spec）
     */
    @GetMapping("/labels")
    public Result<String> labels(@RequestParam(defaultValue = "MATERIAL") String t,
                                @RequestParam String ids,
                                @RequestParam(required = false) Integer copies,
                                @RequestParam(required = false) String paper,
                                @RequestParam(required = false) Integer barcode,
                                @RequestParam(required = false) String fields,
                                HttpServletRequest request) {
        java.util.List<Long> idList = new java.util.ArrayList<>();
        if (ids != null) {
            for (String s : ids.split(",")) {
                String v = s == null ? "" : s.trim();
                if (v.isEmpty()) continue;
                try {
                    idList.add(Long.parseLong(v));
                } catch (Exception ignore) {
                    // 非法主键直接跳过
                }
            }
        }
        if (idList.isEmpty()) {
            return Result.error("请先选择要打印的数据");
        }
        boolean withBarcode = barcode == null || barcode == 1;
        String html = labelPrintService.render(t, idList, resolveBaseUrl(request), copies, paper, withBarcode, fields);
        return Result.success(html);
    }

    /** 扫码解析：把"类型 + 主键"解析成标题与跳转目标；含签名校验/作废提示/落地权限/扫码审计 */
    @GetMapping("/resolve")
    public Result<Map<String, Object>> resolve(@RequestParam(defaultValue = "MAT") String t,
                                               @RequestParam(required = false) String dt,
                                               @RequestParam(required = false) Long id,
                                               @RequestParam(required = false) String s,
                                               @RequestParam(required = false) String tn,
                                               HttpServletRequest request) {
        return Result.success(qrCodeResolveService.resolve(t, dt, id, s, tn, request));
    }

    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@RequestParam(defaultValue = "MAT") String t,
                                        @RequestParam(required = false) String dt,
                                        @RequestParam(required = false) Long id,
                                        @RequestParam(required = false) Integer size,
                                        @RequestParam(required = false) String content,
                                        HttpServletRequest request) {
        String text = resolveText(t, dt, id, content, request);
        byte[] png = qrCodeService.png(text, qrCodeService.clampSize(size));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping("/data-uri")
    public Result<String> dataUri(@RequestParam(defaultValue = "MAT") String t,
                                  @RequestParam(required = false) String dt,
                                  @RequestParam(required = false) Long id,
                                  @RequestParam(required = false) Integer size,
                                  @RequestParam(required = false) String content,
                                  HttpServletRequest request) {
        String text = resolveText(t, dt, id, content, request);
        return Result.success(qrCodeService.dataUri(text, qrCodeService.clampSize(size)));
    }

    @GetMapping("/content")
    public Result<Map<String, Object>> content(@RequestParam(defaultValue = "MAT") String t,
                                               @RequestParam(required = false) String dt,
                                               @RequestParam(required = false) Long id,
                                               @RequestParam(required = false) String content,
                                               HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", t);
        data.put("docType", dt);
        data.put("id", id);
        data.put("content", resolveText(t, dt, id, content, request));
        return Result.success(data);
    }

    private String resolveText(String t, String dt, Long id, String content, HttpServletRequest request) {
        if (content != null && !content.isBlank()) {
            return content;
        }
        // 防伪造 + 公司归属（2026-10-25）：签名与公司标识一起算；换域名不影响校验。
        String tenant = com.erp.tenant.TenantContextHolder.getTenantDbName();
        String signature = id == null ? null : qrCodeSigner.sign(t, dt, id, tenant);
        return QrCodeContent.build(resolveBaseUrl(request), t, dt, id, signature, tenant);
    }

    /** 域名：系统参数优先，未配置按当前请求自动生成（支持反代头） */
    private String resolveBaseUrl(HttpServletRequest request) {
        String configured = sysConfigService.getValue(CFG_BASE_URL, "");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            int port = request.getServerPort();
            host = request.getServerName() + (port > 0 && port != 80 && port != 443 ? ":" + port : "");
        }
        return scheme + "://" + host;
    }
}
