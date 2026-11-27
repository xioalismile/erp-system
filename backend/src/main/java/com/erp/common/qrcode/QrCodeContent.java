package com.erp.common.qrcode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 二维码内容协议（2026-09-27）
 *
 * 码内容统一为可读网址，便于手机相机 / 微信直接打开，也便于系统内解析：
 *   {域名}/mobile/#/pages/scan/scan?t=MAT&id=123            商品码
 *   {域名}/mobile/#/pages/scan/scan?t=BILL&dt=sale_order&id=456   单据码
 *   {域名}/mobile/#/pages/scan/scan?t=OP&id=789             工序码
 *
 * 类型码写死为系统内部协议（客户不会改）：MAT=商品 / BILL=单据 / OP=工序。
 */
public final class QrCodeContent {

    public static final String TYPE_MATERIAL = "MAT";
    public static final String TYPE_BILL = "BILL";
    public static final String TYPE_OPERATION = "OP";

    private QrCodeContent() {
    }

    /** 拼接码内容；baseUrl 为空时退化为站内相对地址（仍可被系统内扫码解析） */
    public static String build(String baseUrl, String type, String docType, Long id) {
        return build(baseUrl, type, docType, id, null);
    }

    /**
     * 拼接码内容（带签名）
     *
     * @param signature 防伪造签名（QrCodeSigner.sign 的结果）；为空则不带 s 参数（兼容旧码）
     */
    public static String build(String baseUrl, String type, String docType, Long id, String signature) {
        return build(baseUrl, type, docType, id, signature, null);
    }

    /**
     * 拼接码内容（带签名 + 公司标识）
     *
     * @param signature 防伪造签名（QrCodeSigner.sign 的结果）；为空则不带 s 参数（兼容旧码）
     * @param tenant    公司标识（当前租户库名）。多公司各自独立库、主键会重复，
     *                  必须带公司标识才能拦住"扫到别家公司的码却落到自家同号工序"（2026-10-25）
     */
    public static String build(String baseUrl, String type, String docType, Long id, String signature, String tenant) {
        String base = (baseUrl == null || baseUrl.isBlank()) ? "" : baseUrl.replaceAll("/+$", "");
        StringBuilder sb = new StringBuilder(base);
        sb.append("/mobile/#/pages/scan/scan?t=").append(enc(type));
        if (docType != null && !docType.isBlank()) {
            sb.append("&dt=").append(enc(docType));
        }
        if (id != null) {
            sb.append("&id=").append(id);
        }
        sb.append("&v=").append(QrCodeSigner.VERSION);
        if (signature != null && !signature.isBlank()) {
            sb.append("&s=").append(signature);
        }
        if (tenant != null && !tenant.isBlank()) {
            sb.append("&tn=").append(enc(tenant.trim()));
        }
        return sb.toString();
    }

    public static String material(String baseUrl, Long materialId) {
        return build(baseUrl, TYPE_MATERIAL, null, materialId);
    }

    public static String material(String baseUrl, Long materialId, String signature) {
        return build(baseUrl, TYPE_MATERIAL, null, materialId, signature);
    }

    public static String material(String baseUrl, Long materialId, String signature, String tenant) {
        return build(baseUrl, TYPE_MATERIAL, null, materialId, signature, tenant);
    }

    public static String bill(String baseUrl, String docType, Long billId) {
        return build(baseUrl, TYPE_BILL, docType, billId);
    }

    public static String bill(String baseUrl, String docType, Long billId, String signature) {
        return build(baseUrl, TYPE_BILL, docType, billId, signature);
    }

    public static String bill(String baseUrl, String docType, Long billId, String signature, String tenant) {
        return build(baseUrl, TYPE_BILL, docType, billId, signature, tenant);
    }

    public static String operation(String baseUrl, Long operationRowId) {
        return build(baseUrl, TYPE_OPERATION, null, operationRowId);
    }

    public static String operation(String baseUrl, Long operationRowId, String signature) {
        return build(baseUrl, TYPE_OPERATION, null, operationRowId, signature);
    }

    public static String operation(String baseUrl, Long operationRowId, String signature, String tenant) {
        return build(baseUrl, TYPE_OPERATION, null, operationRowId, signature, tenant);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
