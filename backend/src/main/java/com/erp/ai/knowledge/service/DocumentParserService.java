// @feature FEAT-AIKB-001
package com.erp.ai.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档解析服务：将PDF/Word/Excel/txt/md解析为纯文本。
 *
 * <p>2026-10-10 增强（小楷文件理解）：Word 解析由"只读正文段落"改为按**文档顺序**遍历
 * 正文段落 + 表格（历史实现漏掉表格 → BOM/报价表这类最常见的表格内容整块丢失），
 * 并新增 {@link #extractImages} 抽取 Word 内嵌图片，供"文档里的图片带入资料"使用。
 */
@Slf4j
@Service
public class DocumentParserService {

    /**
     * 根据文件类型解析文档为纯文本
     */
    public String parse(File file, String fileType) throws Exception {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> parsePdf(file);
            case "docx" -> parseWord(file);
            case "xlsx" -> parseExcel(file);
            case "txt", "md" -> parseText(file);
            default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        };
    }

    private String parsePdf(File file) throws Exception {
        try (PDDocument doc = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /**
     * Word 解析：按文档真实顺序遍历"段落 + 表格"。
     * <p>历史实现只遍历 {@code getParagraphs()}（正文段落），表格里的内容一个字都读不到；
     * 现在表格按行输出、行内各列用 " | " 分隔，第一行即表头。
     */
    private String parseWord(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    String t = p.getText();
                    if (t != null && !t.isBlank()) sb.append(t).append("\n");
                } else if (el instanceof XWPFTable table) {
                    appendTable(sb, table);
                }
            }
        }
        return sb.toString();
    }

    /** 表格按行输出：单元格文本用 " | " 分隔（去掉行尾空单元格，减少无意义内容） */
    private void appendTable(StringBuilder sb, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String v = cell.getText();
                cells.add(v == null ? "" : v.replace("\n", " ").trim());
            }
            while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
                cells.remove(cells.size() - 1);
            }
            if (!cells.isEmpty()) sb.append(String.join(" | ", cells)).append("\n");
        }
        sb.append("\n");
    }

    private String parseExcel(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                sb.append("【工作表: ").append(sheet.getSheetName()).append("】\n");
                for (Row row : sheet) {
                    StringBuilder rowSb = new StringBuilder();
                    for (Cell cell : row) {
                        rowSb.append(getCellValueAsString(cell)).append("\t");
                    }
                    sb.append(rowSb.toString().trim()).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v)) yield String.valueOf((long) v);
                yield String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }

    private String parseText(File file) throws Exception {
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    /**
     * 抽取文档内嵌图片（本期支持 Word/docx；PDF 内图片常被拆成碎片、本期不抽取，返回空列表）。
     *
     * @return 每张图片的文件名（含扩展名）与原始字节；失败返回空列表（不影响正文解析）
     */
    public List<DocImage> extractImages(File file, String fileType) {
        List<DocImage> images = new ArrayList<>();
        if (file == null || fileType == null || !"docx".equalsIgnoreCase(fileType)) {
            return images;
        }
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {
            int idx = 0;
            for (XWPFPictureData pic : doc.getAllPictures()) {
                try {
                    byte[] data = pic.getData();
                    if (data == null || data.length == 0) continue;
                    idx++;
                    images.add(new DocImage("pic" + idx + "." + sniffExt(data, pic.getFileName()), data));
                } catch (Exception e) {
                    // 单张图片损坏不应影响整篇文档的其它图片与正文
                    log.warn("[文档解析] 跳过一张读取失败的图片: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[文档解析] 抽取文档内图片失败: {}", e.getMessage());
        }
        return images;
    }

    /**
     * 按文件头识别图片扩展名。
     * <p>Word 包内图片 part 名有时不带扩展名、或与实际格式不一致，只靠 part 名会得到错误后缀，
     * 进而影响后续按后缀判断 MIME 的视觉识别。故优先按魔数判定，兜底才用 part 名。
     */
    private String sniffExt(byte[] d, String fallbackName) {
        if (d != null && d.length >= 4) {
            int b0 = d[0] & 0xFF, b1 = d[1] & 0xFF, b2 = d[2] & 0xFF, b3 = d[3] & 0xFF;
            if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "png";
            if (b0 == 0xFF && b1 == 0xD8) return "jpg";
            if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46) return "gif";
            if (b0 == 0x42 && b1 == 0x4D) return "bmp";
            if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46) return "webp";
        }
        if (fallbackName != null && fallbackName.contains(".")) {
            String e = fallbackName.substring(fallbackName.lastIndexOf('.') + 1).toLowerCase();
            if (List.of("png", "jpg", "jpeg", "gif", "bmp", "webp").contains(e)) return e;
        }
        return "png";
    }

    /** 文档内嵌图片（原始字节 + 文件名） */
    public static class DocImage {
        private final String fileName;
        private final byte[] data;

        public DocImage(String fileName, byte[] data) {
            this.fileName = fileName;
            this.data = data;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getData() {
            return data;
        }
    }
}
