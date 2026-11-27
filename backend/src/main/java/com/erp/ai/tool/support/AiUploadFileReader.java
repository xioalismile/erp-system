package com.erp.ai.tool.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.PictureData;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Shape;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 侧「用户上传文件」的统一读取入口（只读）。
 *
 * <p>抽公共类的动机：BOM 文件读取（bom_file_read）与通用导入的文件预览（import_file_read）
 * 需要<b>完全相同</b>的路径安全逻辑与读取上限。历史做法是各写一份，一旦路径安全加固只改一处
 * 就会出现"另一个工具仍可越界读文件"的漏洞。故统一到此，两个工具都只调用本类，不得自行拼路径。
 *
 * <p>安全约束（与 FileUploadController 的 erp.upload.path 保持一致）：
 * <ul>
 *   <li>仅接受 {@code /uploads/} 前缀；</li>
 *   <li>URL 解码并规范化（canonical path）后必须仍落在 erp.upload.path 目录内，拒绝 ../ 越界与其它目录。</li>
 * </ul>
 */
@Slf4j
@Component
public class AiUploadFileReader {

    /** 上传根目录（与 FileUploadController 的 erp.upload.path 保持一致） */
    @Value("${erp.upload.path:d:/erp/uploads}")
    private String uploadRoot;

    /** 系统上传目录前缀：只允许读取本系统上传的文件 */
    private static final String UPLOAD_URL_PREFIX = "/uploads/";

    /**
     * 图片类本地路径的额外允许根目录（逗号分隔），默认含获客采集服务截图目录。
     * 采集服务 start-acquisition.bat 中 SCREENSHOT_DIR=./screenshots，运行目录为
     * C:\smart-erp\acquisition-service，故截图实际落在该目录下；采集结果回传 ERP 时
     * 存的就是这个绝对路径（见 acquisition-service/src/utils/erpClient.js）。
     * 环境差异可经 ERP_AI_VISION_EXTRA_ROOTS 覆盖，无需改代码。
     */
    @Value("${erp.ai.vision.extra-allowed-roots:}")
    private String extraAllowedRoots;

    /** Excel 嵌入图片落盘子目录（与读文档抽图共用，保证返回的 /uploads/ 地址可被读回识别） */
    private static final String EXCEL_IMAGE_DIR = "ai-doc";

    /**
     * 解析文件路径（安全加固）：只接受本系统上传目录下的文件，
     * 规范化后的绝对路径必须仍落在 erp.upload.path 目录内，拒绝越界与其它目录。
     */
    public File resolveFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return null;
        try {
            if (!fileUrl.startsWith(UPLOAD_URL_PREFIX)) {
                log.warn("[文件读取] 拒绝非上传目录的文件路径: {}", fileUrl);
                return null;
            }
            String relative = fileUrl.substring(UPLOAD_URL_PREFIX.length());
            int q = relative.indexOf('?');
            if (q >= 0) relative = relative.substring(0, q);
            relative = java.net.URLDecoder.decode(relative, StandardCharsets.UTF_8);

            File root = new File(uploadRoot);
            File target = new File(root, relative);
            String canonicalRoot = root.getCanonicalPath();
            String canonicalTarget = target.getCanonicalPath();
            if (!canonicalTarget.startsWith(canonicalRoot + File.separator)) {
                log.warn("[文件读取] 路径越界，已拒绝: {}", fileUrl);
                return null;
            }
            if (target.exists()) {
                return target;
            }
            // 兜底（2026-10-23）：模型有时不用系统给的完整 fileUrl，而是把「原文件名」重新拼成
            // /uploads/<类型>/<原文件名> 导致丢掉 yyyy/MM/dd 目录，文件必然读不到。
            // 原路径不存在时，按文件名在上传根目录内递归查找；仍严格限定在 uploadRoot 内。
            File byName = findByFileName(root, target.getName());
            if (byName != null) {
                log.info("[文件读取] 原路径不存在，按文件名兜底命中: {} -> {}", fileUrl, byName.getAbsolutePath());
                return byName;
            }
            return target;
        } catch (Exception e) {
            log.warn("[文件读取] 解析文件路径失败: {}", fileUrl, e);
            return null;
        }
    }

    /**
     * 按文件名在上传根目录内递归查找（仅在完整路径不存在时兜底使用）。
     * 限制遍历深度 6 层（uploads/类型/yyyy/MM/dd/文件 = 5 层），同名多份取最新修改的一份。
     */
    private File findByFileName(File root, String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(root.toPath(), 6)) {
            List<File> hits = new ArrayList<>();
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(fileName))
                    .forEach(p -> hits.add(p.toFile()));
            if (hits.isEmpty()) return null;
            if (hits.size() == 1) return hits.get(0);
            hits.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            return hits.get(0);
        } catch (Exception e) {
            log.warn("[文件读取] 按文件名兜底查找失败: {}", fileName, e);
            return null;
        }
    }

    /**
     * 解析「图片来源」（安全审计 H-2）：只允许
     * ① {@code /uploads/} 前缀的系统上传文件；
     * ② 位于 upload 根目录或 extra-allowed-roots 内的本地文件路径
     * （获客采集截图目录属此列，采集结果回传的就是绝对路径）。
     *
     * <p>关键约束：与 {@link #resolveFile} 同样做 canonical 规范化后校验前缀，
     * 拒绝 upload/截图目录之外的任意系统文件（如 application.yml、license.lic）。
     * 历史做法是各服务自己拼 {@code uploadPath + relativePath}，加固只改一处就会出现
     * "另一个链路仍可越界读文件"，故视觉/以图识图/旧网关三处统一改为调用本方法。
     */
    public File resolveImageFile(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return null;
        if (pathOrUrl.startsWith(UPLOAD_URL_PREFIX)) {
            return resolveFile(pathOrUrl);
        }
        try {
            File target = new File(pathOrUrl.replace('\\', '/'));
            String canonicalTarget = target.getCanonicalPath();
            for (String root : allowedImageRoots()) {
                String canonicalRoot = new File(root).getCanonicalPath();
                if (canonicalTarget.startsWith(canonicalRoot + File.separator)) {
                    return target;
                }
            }
            log.warn("[图片读取] 拒绝允许根目录之外的本地路径: {}（允许根={}）", pathOrUrl, allowedImageRoots());
            return null;
        } catch (Exception e) {
            log.warn("[图片读取] 解析本地路径失败: {}", pathOrUrl, e);
            return null;
        }
    }

    /** 图片可读根目录：upload 根 + 额外配置根（采集截图目录等） */
    private List<String> allowedImageRoots() {
        List<String> roots = new ArrayList<>();
        if (uploadRoot != null && !uploadRoot.isBlank()) roots.add(uploadRoot);
        if (extraAllowedRoots != null && !extraAllowedRoots.isBlank()) {
            for (String s : extraAllowedRoots.split(",")) {
                if (!s.isBlank()) roots.add(s.trim());
            }
        }
        return roots;
    }

    /**
     * 读取上传文件内容（csv/xlsx/xls），**全量读取、不截断**（2026-10-22 用户立规：小楷要智能全量读取，
     * 不做"省上下文"式截断）；Excel 嵌入图片全部按行抽出落盘返回地址（不再只报张数）。
     *
     * @return 成功：{@code {success:true, fileName, sheetCount, sheets:[{name,totalRows,returnedRows,rows,images}], imageCount}}；
     *         失败：{@code {success:false, message}}
     */
    public Map<String, Object> read(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return Map.of("success", false, "message", "未提供文件路径");
        }
        File file = resolveFile(fileUrl);
        if (file == null || !file.exists()) {
            return Map.of("success", false, "message", "文件不存在或不在系统上传目录内: " + fileUrl);
        }

        try {
            List<Map<String, Object>> sheets = new ArrayList<>();
            int imageCount = 0;

            if (file.getName().toLowerCase().endsWith(".csv")) {
                // CSV：按纯文本读取（POI 不支持 csv）
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                List<List<String>> rows = new ArrayList<>();
                for (String line : lines) {
                    List<String> cells = new ArrayList<>();
                    for (String c : line.split(",", -1)) {
                        cells.add(c.trim());
                    }
                    rows.add(cells);
                }
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("name", file.getName());
                s.put("totalRows", lines.size());
                s.put("returnedRows", rows.size());
                s.put("rows", rows);
                s.put("images", new ArrayList<Map<String, Object>>());
                sheets.add(s);
            } else {
                String baseName = baseNameOf(file.getName());
                try (InputStream in = Files.newInputStream(file.toPath())) {
                    Workbook wb = WorkbookFactory.create(in);
                    DataFormatter fmt = new DataFormatter();
                    for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                        Sheet sh = wb.getSheetAt(si);
                        int lastRow = sh.getLastRowNum();
                        List<List<String>> rows = new ArrayList<>();
                        for (int r = 0; r <= lastRow; r++) {
                            Row row = sh.getRow(r);
                            if (row == null) { rows.add(new ArrayList<>()); continue; }
                            List<String> cells = new ArrayList<>();
                            int lastCell = row.getLastCellNum();
                            for (int c = 0; c < lastCell; c++) {
                                String v = fmt.formatCellValue(row.getCell(c));
                                cells.add(v == null ? "" : v.trim());
                            }
                            // 去掉行尾空单元格，减少无意义内容
                            while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
                                cells.remove(cells.size() - 1);
                            }
                            rows.add(cells);
                        }
                        // 嵌入图片全量按行抽出（2026-10-22 用户立规：图片也是文件的一部分，不许只报数量）
                        List<Map<String, Object>> images = extractSheetImages(sh, baseName, si);
                        imageCount += images.size();
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("name", sh.getSheetName());
                        s.put("totalRows", lastRow + 1);
                        s.put("returnedRows", rows.size());
                        s.put("rows", rows);
                        s.put("images", images);
                        sheets.add(s);
                    }
                    wb.close();
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("fileName", file.getName());
            result.put("sheetCount", sheets.size());
            result.put("sheets", sheets);
            result.put("imageCount", imageCount);
            result.put("hint", "以上是文件**全量**内容（所有行、所有列，未截断；totalRows = returnedRows）。"
                    + "每个工作表的 images 是该表嵌入图片清单：row=图片锚定的第几行（从 1 起），url=可识别的图片地址——"
                    + "需要图片里的信息时，用 image_read 逐张读 url，**禁止说\"只有图片数量、读不到内容\"**。"
                    + "单元格里的 ■ ● ☑ √ 表示【勾选/打点/需要做】，□ ○ ☐ 表示【未勾选/不需要做】——请按符号判断，不要声称读不到。"
                    + "回答用户时必须逐条覆盖所有数据行，不许只报数量、只举例或漏行。");
            return result;
        } catch (Exception e) {
            log.error("[文件读取] 失败 fileUrl={}", fileUrl, e);
            return Map.of("success", false, "message", "文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 抽出一个工作表里的全部嵌入图片（带锚点行号），落盘到系统上传目录并返回 /uploads/ 地址。
     * 单张失败只跳过该张，不影响其它图片与文字读取。
     */
    private List<Map<String, Object>> extractSheetImages(Sheet sh, String baseName, int sheetIndex) {
        List<Map<String, Object>> images = new ArrayList<>();
        try {
            Drawing<?> drawing = sh.getDrawingPatriarch();
            if (drawing == null) return images;
            // POI 的统一 Drawing 接口取不到形状列表，按格式分支：.xls 走 HSSF，.xlsx 走 XSSF
            List<? extends Shape> shapes;
            if (drawing instanceof org.apache.poi.hssf.usermodel.HSSFPatriarch patriarch) {
                shapes = patriarch.getChildren();
            } else if (drawing instanceof org.apache.poi.xssf.usermodel.XSSFDrawing xssfDrawing) {
                shapes = xssfDrawing.getShapes();
            } else {
                return images;
            }
            int seq = 0;
            for (Shape shape : shapes) {
                if (!(shape instanceof Picture pic)) continue;
                PictureData pd = pic.getPictureData();
                if (pd == null || pd.getData() == null || pd.getData().length == 0) continue;
                ClientAnchor anchor = pic.getClientAnchor();
                int rowNo = (anchor == null || anchor.getRow1() < 0) ? 1 : anchor.getRow1() + 1; // 用户视角从 1 起
                seq++;
                String url = saveExtractedImage(pd.getData(),
                        baseName + "_表" + (sheetIndex + 1) + "_行" + rowNo + "_" + seq + "."
                                + extOf(pd.suggestFileExtension()));
                if (url == null) continue;
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("row", rowNo);
                im.put("url", url);
                images.add(im);
            }
        } catch (Exception e) {
            log.warn("[文件读取] 抽取嵌入图片失败（不影响文字内容）: {}", e.getMessage());
        }
        return images;
    }

    /** 把抽取出的图片落盘到 upload 根目录下，返回可访问的 /uploads/ 地址；失败返回 null */
    public String saveExtractedImage(byte[] data, String suggestedName) {
        try {
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            File dir = new File(uploadRoot + File.separator + EXCEL_IMAGE_DIR + File.separator + datePath);
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("[文件读取] 创建图片目录失败: {}", dir.getAbsolutePath());
                return null;
            }
            File dest = uniqueFile(dir, sanitize(suggestedName));
            Files.write(dest.toPath(), data);
            String encodedName = URLEncoder.encode(dest.getName(), StandardCharsets.UTF_8).replace("+", "%20");
            return "/uploads/" + EXCEL_IMAGE_DIR + "/" + datePath + "/" + encodedName;
        } catch (Exception e) {
            log.warn("[文件读取] 保存嵌入图片失败: {}", e.getMessage());
            return null;
        }
    }

    private static String extOf(String ext) {
        String e = ext == null ? "" : ext.trim().toLowerCase();
        return e.isEmpty() ? "png" : e;
    }

    private static String baseNameOf(String fileName) {
        if (fileName == null) return "file";
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.isBlank() ? "file" : base;
    }

    /** 清洗文件名：剥离路径分隔符与 Windows/URL 保留字符，避免落盘越界或不可访问 */
    private static String sanitize(String name) {
        String n = name == null ? "" : name.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|%#&+,\\s\\x00-\\x1F]", "_");
        if (n.isEmpty()) n = "image";
        return n.length() > 80 ? n.substring(0, 80) : n;
    }

    /** 同目录防覆盖：已存在同名文件时追加 _1、_2…序号 */
    private static File uniqueFile(File dir, String fileName) {
        File candidate = new File(dir, fileName);
        if (!candidate.exists()) return candidate;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            candidate = new File(dir, base + "_" + i + ext);
            if (!candidate.exists()) return candidate;
        }
        return new File(dir, base + "_" + System.currentTimeMillis() + ext);
    }
}
