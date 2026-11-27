package com.erp.common.qrcode;

import com.erp.print.template.service.PrintDataFetcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 批量标签渲染（2026-09-27，P0-1）
 *
 * 用途：物料档案"批量打印标签"、工单工序明细"批量打印工序卡"。
 * 特征：多选 + 份数 + 连排（A4 每页 3×8）+ 条码与二维码双打。
 * 输出：整页 HTML（内联图片），由前端放入打印框直接打印，不依赖网络。
 *
 * 纸张："A4-3x8"（默认，普通打印机）、"60x40"、"50x30"（标签机）。
 */
@Service
@RequiredArgsConstructor
public class LabelPrintService {

    private final PrintDataFetcherService printDataFetcherService;
    private final QrCodeService qrCodeService;
    private final QrCodeSigner qrCodeSigner;

    /** 纸张排版参数 */
    public static class Paper {
        String code;
        String pageSize;
        int marginMm;
        double labelWmm;
        double labelHmm;
        int qrSize;
        int nameFont;
        int lineFont;
        double barcodeHmm;
    }

    /** 一张标签的数据 */
    public static class LabelItem {
        public String name;
        public String code;
        public String spec;
        public String unit;
        public String fullName;
        public String qrUri;
        public String barcodeUri;
    }

    private static final Set<String> DEFAULT_FIELDS = new HashSet<>(Arrays.asList("name", "code", "spec"));

    /** 纸张参数表：新增纸张只在这里加一行 */
    public static Paper paperOf(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        Paper p = new Paper();
        if ("60X40".equals(c)) {
            p.code = "60x40"; p.pageSize = "60mm 40mm"; p.marginMm = 2;
            p.labelWmm = 56; p.labelHmm = 36; p.qrSize = 110; p.nameFont = 13; p.lineFont = 10; p.barcodeHmm = 10;
        } else if ("50X30".equals(c)) {
            p.code = "50x30"; p.pageSize = "50mm 30mm"; p.marginMm = 2;
            p.labelWmm = 46; p.labelHmm = 26; p.qrSize = 80; p.nameFont = 11; p.lineFont = 9; p.barcodeHmm = 8;
        } else {
            // 默认 A4 每页 3 列 × 8 行（普通打印机即可，无需专用标签机）
            p.code = "A4-3x8"; p.pageSize = "A4"; p.marginMm = 8;
            p.labelWmm = 64; p.labelHmm = 34; p.qrSize = 90; p.nameFont = 12; p.lineFont = 10; p.barcodeHmm = 9;
        }
        return p;
    }

    /**
     * 渲染批量标签整页 HTML
     *
     * @param type      MATERIAL（商品标签）/ WORK_OPERATION（工序卡）
     * @param ids       主键列表
     * @param qrBaseUrl 二维码域名（为空则码内为站内相对地址）
     * @param copies    份数（1~999）
     * @param paperCode 纸张（空=A4-3x8）
     * @param barcode   是否同时打一维条码
     * @param fieldsCsv 标签包含字段（name,code,spec,unit,fullName），空=name,code,spec
     */
    public String render(String type, List<Long> ids, String qrBaseUrl, Integer copies,
                         String paperCode, boolean barcode, String fieldsCsv) {
        Paper paper = paperOf(paperCode);
        int cp = copies == null || copies < 1 ? 1 : Math.min(copies, 999);
        Set<String> fields = parseFields(fieldsCsv);
        String docType = type == null ? "MATERIAL" : type.trim().toUpperCase(Locale.ROOT);

        List<String> cells = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id == null) continue;
                Map<String, Object> data = fetchData(docType, id);
                if (data == null) continue;
                LabelItem item = buildItem(docType, id, data, qrBaseUrl, barcode);
                for (int i = 0; i < cp; i++) {
                    cells.add(renderCell(item, paper, barcode, fields));
                }
            }
        }
        String title = "WORK_OPERATION".equals(docType) ? "工序卡" : "商品标签";
        return buildHtml(cells, paper, title);
    }

    /** 取数失败不抛异常（一张失败不影响整批） */
    private Map<String, Object> fetchData(String docType, Long id) {
        try {
            Map<String, Object> data = printDataFetcherService.fetchBillData(docType, id);
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private LabelItem buildItem(String docType, Long id, Map<String, Object> data, String qrBaseUrl, boolean barcode) {
        LabelItem it = new LabelItem();
        it.name = pick(data, "materialName", "operationName", "name");
        it.code = pick(data, "materialCode", "operationCode", "code");
        it.spec = pick(data, "model", "specification");
        it.unit = pick(data, "baseUnitName", "unitName");
        it.fullName = pick(data, "fullName");
        String qrContent;
        // 公司标识随码下发并纳入签名（2026-10-25）：多公司独立库主键会重复，防止扫到别家的码落到自家同号记录
        String tenant = com.erp.tenant.TenantContextHolder.getTenantDbName();
        if ("WORK_OPERATION".equals(docType)) {
            qrContent = QrCodeContent.operation(qrBaseUrl, id,
                    qrCodeSigner.sign(QrCodeContent.TYPE_OPERATION, null, id, tenant), tenant);
        } else {
            qrContent = QrCodeContent.material(qrBaseUrl, id,
                    qrCodeSigner.sign(QrCodeContent.TYPE_MATERIAL, null, id, tenant), tenant);
        }
        it.qrUri = qrCodeService.dataUri(qrContent, 320);
        if (barcode) {
            String bc = it.code != null && !it.code.isBlank() ? it.code : it.name;
            it.barcodeUri = qrCodeService.barcodeDataUri(bc, 320, 80);
        }
        return it;
    }

    private Set<String> parseFields(String csv) {
        if (csv == null || csv.isBlank()) return new HashSet<>(DEFAULT_FIELDS);
        Set<String> set = new HashSet<>();
        for (String s : csv.split(",")) {
            if (s != null && !s.trim().isBlank()) set.add(s.trim());
        }
        return set.isEmpty() ? new HashSet<>(DEFAULT_FIELDS) : set;
    }

    /** 单张标签（纯函数，便于单测） */
    public static String renderCell(LabelItem it, Paper p, boolean barcode, Set<String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"label\">");
        sb.append("<div class=\"label__left\">");
        sb.append("<img class=\"qr\" src=\"").append(it.qrUri == null ? "" : it.qrUri)
          .append("\" width=\"").append(p.qrSize).append("\" height=\"").append(p.qrSize).append("\" alt=\"QR\">");
        sb.append("</div><div class=\"label__main\">");
        sb.append("<div class=\"label__name\">").append(esc(it.name)).append("</div>");
        if (fields.contains("code")) sb.append(line("编号", it.code));
        if (fields.contains("spec")) sb.append(line("规格", it.spec));
        if (fields.contains("unit")) sb.append(line("单位", it.unit));
        if (fields.contains("fullName")) sb.append(line("全称", it.fullName));
        if (barcode && it.barcodeUri != null) {
            sb.append("<img class=\"barcode\" src=\"").append(it.barcodeUri).append("\" alt=\"BARCODE\">");
        }
        sb.append("</div></div>");
        return sb.toString();
    }

    /** 整页 HTML（纯函数，便于单测） */
    public static String buildHtml(List<String> cells, Paper p, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>").append(esc(title)).append("</title>");
        sb.append("<style>");
        sb.append("@page { size: ").append(p.pageSize).append("; margin: ").append(p.marginMm).append("mm; }");
        sb.append("* { -webkit-print-color-adjust: exact; print-color-adjust: exact; box-sizing: border-box; }");
        sb.append("body { margin:0; font-family: \"Microsoft YaHei\", \"PingFang SC\", SimSun, sans-serif; color:#000; }");
        sb.append(".labels { display:flex; flex-wrap:wrap; }");
        sb.append(".label { width:").append(p.labelWmm).append("mm; height:").append(p.labelHmm)
          .append("mm; border:1px dashed #bbb; padding:1.5mm 2mm; display:flex; align-items:center; gap:2mm;")
          .append(" overflow:hidden; page-break-inside:avoid; }");
        sb.append(".label__left { flex:0 0 auto; }");
        sb.append(".label__main { flex:1; min-width:0; }");
        sb.append(".label__name { font-size:").append(p.nameFont).append("px; font-weight:700; line-height:1.15; margin-bottom:0.6mm; word-break:break-all; }");
        sb.append(".label__line { font-size:").append(p.lineFont).append("px; line-height:1.25; color:#222; word-break:break-all; }");
        sb.append(".qr { display:block; }");
        sb.append(".barcode { display:block; width:100%; height:").append(p.barcodeHmm).append("mm; margin-top:0.6mm; }");
        sb.append("</style></head><body><div class=\"labels\">");
        if (cells != null) {
            for (String c : cells) sb.append(c);
        }
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static String line(String label, String value) {
        if (value == null || value.isBlank()) return "";
        return "<div class=\"label__line\">" + esc(label) + "：" + esc(value) + "</div>";
    }

    private static String pick(Map<String, Object> data, String... keys) {
        if (data == null) return null;
        for (String k : keys) {
            Object v = data.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        // 兼容下划线/大写键名
        Map<String, Object> lower = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (e.getKey() != null) lower.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
        }
        for (String k : keys) {
            Object v = lower.get(k.toLowerCase(Locale.ROOT));
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return null;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
