package com.erp.kai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小楷独立版 · 聊天文件暂存（FEAT-KAI-STANDALONE-010 / 2026-10-19 全类型升级）。
 * 分级处理：图片 → 视觉识别（用客户所选模型，按真实 tokens 扣积分，备注「图片识别」）；
 * pdf/docx/xlsx → 解析器提取文字；文本类直读；其余二进制类型明确告知暂不支持。
 * 暂存 30 分钟（按用户隔离），对话工具凭 fileToken 读取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiChatFileService {

    private static final long TTL_MS = 30 * 60 * 1000L;
    private static final int MAX_TEXT_LEN = 60_000;                  // 提取文本上限（够放接口文档）

    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "webp", "gif", "bmp");
    private static final Set<String> TEXT_EXTS = Set.of("txt", "md", "json", "csv", "tsv", "html", "htm",
            "log", "xml", "yaml", "yml", "ini", "sql", "properties");

    /** 暂存条目。content=原始文件字节（楷煜满血转发时转传用；本地工具循环只用 text）。 */
    public record Entry(Long userId, String fileName, String text, long expireAt, byte[] content) {}

    /** 上传结果：kind=IMAGE（已识别）/DOC（已提取文字）；credits=本次识别扣减积分（文档=0）；
     *  warning=图片未能识别时的原因（原图仍已暂存，可随消息交给接入的系统识别）。 */
    public record SaveResult(String token, String fileName, String kind, BigDecimal credits,
                             long totalTokens, String warning) {}

    private final KaiVisionService visionService;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** 保存上传文件（图片即识别即扣积分），返回 fileToken 与识别信息。 */
    public SaveResult save(Long kaiUserId, MultipartFile file, Long modelProviderId) {
        try {
            if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的文件");
            String name = file.getOriginalFilename() == null ? "未命名" : file.getOriginalFilename();
            byte[] bytes = file.getBytes();
            String ext = extOf(name);

            String text;
            String kind;
            BigDecimal credits = BigDecimal.ZERO;
            long totalTokens = 0;
            String warning = null;
            if (IMAGE_EXTS.contains(ext)) {
                kind = "IMAGE";
                // 本站识别失败（最常见：未配置可用的识别模型）不应拦住上传：
                // 原图照样暂存，满血连接时转传给楷煜由那边的小楷看图；这里只把原因回给前端提示。
                try {
                    KaiVisionService.VisionResult v = visionService.recognize(kaiUserId, modelProviderId, name, bytes);
                    text = "【图片识别结果（" + name + "）】\n" + v.text();
                    credits = v.charged() == null ? BigDecimal.ZERO : v.charged();
                    totalTokens = v.totalTokens();
                } catch (IllegalArgumentException e) {
                    warning = e.getMessage();
                    text = "【图片已暂存（" + name + "）】本站未能识别图片内容：" + e.getMessage()
                            + " 原图会随本次消息一并交给已接入的系统处理。";
                }
            } else if (ext.equals("xlsx")) {
                text = parseExcel(bytes);
                kind = "DOC";
            } else if (ext.equals("pdf") || ext.equals("docx")
                    || TEXT_EXTS.contains(ext) || ext.isBlank()) {
                text = ext.equals("pdf") || ext.equals("docx")
                        ? extract(name, bytes)
                        : readTextStrict(bytes);
                kind = "DOC";
            } else {
                throw new IllegalArgumentException("暂不支持读取 ." + ext + " 文件的内容——支持图片（png/jpg/webp/gif/bmp，自动识别）、"
                        + "pdf / docx / xlsx、以及文本类文件（txt/md/json/csv/html/log 等）。请先转成以上格式再上传");
            }
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("没能从文件中提取出内容——若为扫描件请改用图片格式上传（会自动识别）");
            }
            if (text.length() > MAX_TEXT_LEN) text = text.substring(0, MAX_TEXT_LEN) + "\n…（内容过长已截断）";
            sweep();
            String token = UUID.randomUUID().toString().replace("-", "");
            store.put(token, new Entry(kaiUserId, name, text, System.currentTimeMillis() + TTL_MS, bytes));
            return new SaveResult(token, name, kind, credits, totalTokens, warning);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[小楷独立版] 文件解析失败: {}", e.getMessage());
            throw new IllegalArgumentException("文件解析失败：" + e.getMessage());
        }
    }

    /** 取本人文件内容（token 不存在/过期/非本人 → 中文报错）。 */
    public Entry own(Long kaiUserId, String token) {
        Entry e = token == null ? null : store.get(token);
        if (e == null) throw new IllegalArgumentException("文件不存在或已过期（上传后 30 分钟内有效），请重新上传");
        if (!e.userId().equals(kaiUserId)) throw new IllegalArgumentException("无权使用该文件");
        if (System.currentTimeMillis() > e.expireAt()) {
            store.remove(token);
            throw new IllegalArgumentException("文件已过期（上传后 30 分钟内有效），请重新上传");
        }
        return e;
    }

    /** 按扩展名提取文本（知识库上传复用同源解析器）。 */
    public String extract(String name, byte[] bytes) throws Exception {
        String lower = name.toLowerCase();
        if (lower.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                return new PDFTextStripper().getText(doc);
            }
        }
        if (lower.endsWith(".docx")) {
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                 XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                return ex.getText();
            }
        }
        // txt / md / json / csv / html / log / 无扩展名：按 UTF-8 直读
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 文本直读（含二进制拦截）：含 0x00 字节或不可打印比例过高 → 明确报错，不产出乱码。 */
    private String readTextStrict(byte[] bytes) {
        int nonPrintable = 0;
        int check = Math.min(bytes.length, 4096);
        for (int i = 0; i < check; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0) throw new IllegalArgumentException("这是一个二进制文件，暂时读不出文字内容——支持图片（自动识别）、pdf/docx/xlsx、文本类文件，请先转换格式");
            if (b < 0x09 || (b > 0x0D && b < 0x20 && b != 0x1B)) nonPrintable++;
        }
        if (check > 0 && nonPrintable * 10 > check) {
            throw new IllegalArgumentException("文件内容不是可读文字（可能是二进制/加密文件）——支持图片（自动识别）、pdf/docx/xlsx、文本类文件，请先转换格式");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** xlsx → 文本（每个工作表一段，单元格制表符分隔；与主系统知识库解析口径一致）。 */
    private String parseExcel(byte[] bytes) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                sb.append("【工作表: ").append(sheet.getSheetName()).append("】\n");
                for (Row row : sheet) {
                    StringBuilder rowSb = new StringBuilder();
                    for (Cell cell : row) {
                        rowSb.append(cellText(cell)).append("\t");
                    }
                    sb.append(rowSb.toString().trim()).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String cellText(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) yield cell.getDateCellValue().toString();
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }

    private String extOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase();
    }

    /** 过期清理。 */
    private void sweep() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now > e.getValue().expireAt());
    }
}
