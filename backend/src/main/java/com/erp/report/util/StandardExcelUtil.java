package com.erp.report.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 标准台账报表 Excel 导出工具
 *
 * 使用 EasyExcel 动态表头模式，支持任意 List<Map<String,Object>> 数据导出。
 * 列定义通过 headers 参数传入（与前端列定义保持一致）。
 */
@Slf4j
public class StandardExcelUtil {

    /**
     * 导出 Excel 到 HttpServletResponse
     *
     * @param response  HTTP 响应
     * @param fileName  文件名（不含扩展名）
     * @param headers   表头定义，每个元素为 {label, field} 的二元组
     *                  - label: Excel 列名（中文）
     *                  - field: 对应 records 中的 Map key
     * @param records   数据记录列表（List<Map<String,Object>>）
     */
    public static void export(HttpServletResponse response, String fileName,
                              List<String[]> headers, List<Map<String, Object>> records) {
        try {
            // 1. 设置响应头
            String encodedFileName = URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName);
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

            // 2. 构建动态表头：List<List<String>>
            List<List<String>> head = new ArrayList<>();
            for (String[] h : headers) {
                List<String> col = new ArrayList<>();
                col.add(h[0]); // label
                head.add(col);
            }

            // 3. 构建数据行：List<List<Object>>
            List<List<Object>> data = new ArrayList<>();
            if (records != null) {
                for (Map<String, Object> row : records) {
                    List<Object> line = new ArrayList<>();
                    for (String[] h : headers) {
                        line.add(normalizeValue(row.get(h[1])));
                    }
                    data.add(line);
                }
            }

            // 4. 表头样式
            WriteCellStyle headStyle = new WriteCellStyle();
            headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            WriteFont headFont = new WriteFont();
            headFont.setFontHeightInPoints((short) 11);
            headFont.setBold(true);
            headStyle.setWriteFont(headFont);

            // 5. 写出
            OutputStream out = response.getOutputStream();
            EasyExcel.write(out)
                    .head(head)
                    .registerWriteHandler(new com.alibaba.excel.write.style.HorizontalCellStyleStrategy(headStyle, new WriteCellStyle()))
                    .sheet(fileName)
                    .doWrite(data);

            log.info("Excel 导出成功: {} 共 {} 行 {} 列", fileName, data.size(), head.size());
        } catch (Exception e) {
            log.error("Excel 导出失败: {}", fileName, e);
            // 若响应流已开始（表头已写出去），此时再写 JSON 错误体只会得到"200 + 半截文件"，
            // 前端表现为空/损坏的 xlsx。故仅在未提交时才回写错误 JSON。
            if (!response.isCommitted()) {
                try {
                    response.reset();
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"code\":500,\"message\":\"Excel导出失败: " + e.getMessage() + "\"}");
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 单元格取值归一化（2026-09-22 新增）。
     *
     * <p><b>为什么必须做</b>：EasyExcel 3.3.4 的默认转换器<b>不认识</b> JDBC 的日期类型，
     * 写入 {@code java.sql.Date} / {@code java.sql.Timestamp} 会抛
     * {@code ExcelWriteDataConvertException: Can not find 'Converter' support class Date/Timestamp}；
     * 而 JdbcTemplate + pgjdbc 取 date/timestamp 列返回的正是这两种类型。
     * 异常发生在响应流已开始之后 → 浏览器收到 200 + 空体（本地实测：含「单据日期」「制单时间」列的
     * 导出响应体 0 字节，文件下载下来是 0KB）。
     *
     * <p>做法：日期/时间一律按可读文本写入（{@code yyyy-MM-dd} / {@code yyyy-MM-dd HH:mm:ss}）。
     * 对不含日期列的导出完全无影响（值原样透传）。
     */
    private static Object normalizeValue(Object v) {
        if (v == null) return "";
        if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate().toString();
        if (v instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) v).toLocalDateTime().toString().replace('T', ' ');
        }
        if (v instanceof java.sql.Time) return v.toString();
        if (v instanceof java.time.LocalDate) return v.toString();
        if (v instanceof java.time.LocalDateTime) return String.valueOf(v).replace('T', ' ');
        if (v instanceof java.time.LocalTime) return v.toString();
        if (v instanceof java.util.Date) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((java.util.Date) v);
        }
        return v;
    }
}
