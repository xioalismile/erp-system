package com.erp.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 宽松日期解析工具（导入/粘贴场景专用，2026-09-19 新增）。
 *
 * <p><b>背景（用户实测报障）</b>：销售订单导入模板里"订单日期"填 {@code 2026/9/19}（Excel/WPS 复制来的常见写法）
 * 直接被拒："第2行: 订单日期[2026/9/19]格式错误，应为 yyyy-MM-dd"。
 * 原实现只认 {@code yyyy-MM-dd} 与 Excel 数字序列号，用户必须手工改成横杠才能导入 —— 对一线操作是纯摩擦。</p>
 *
 * <p><b>口径</b>：模板仍推荐 {@code yyyy-MM-dd} 导出/填写（导出保持不变），
 * 但**导入解析要宽容**：横杠 / 斜杠 / 点号 / 紧凑 / 中文 / 带时间 都能认，Excel 原生日期单元格也能认。
 * 宽松 ≠ 放水：非法日期（如 2026-13-45、2026-02-30）仍然报错，错误提示里给出支持的写法清单。</p>
 */
public final class FlexibleDateUtils {

    /** 纯日期写法（按优先级；yyyy-M-d 同时覆盖补零与不补零） */
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy年M月d日")
    };

    /** 日期+时间写法（导入列里带了时分秒时；也用于兼容 LocalDateTime.toString() 的 ISO 'T' 写法） */
    private static final DateTimeFormatter[] DATE_TIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:m:s"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:m"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:m"),
            DateTimeFormatter.ofPattern("yyyy-M-d'T'H:m:s"),
            DateTimeFormatter.ofPattern("yyyy-M-d'T'H:m")
    };

    /** Excel 日期序列号基准：Excel 把 1900-01-01 记作 1，且沿用 1900 闰年 bug → 用 1899-12-30 换算 */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    /** 序列号上限：9999-12-31 */
    private static final long EXCEL_SERIAL_MAX = 2958465L;

    private FlexibleDateUtils() {
    }

    /**
     * 解析为日期（宽松）。
     *
     * @param raw 原始文本（可为 Excel 序列号、斜杠日期、中文日期、带时间日期）
     * @return 解析结果；{@code null} 表示入参为空
     * @throws IllegalArgumentException 无法识别（调用方补上"第N行/字段名"后抛给用户）
     */
    public static LocalDate parseLocalDate(String raw) {
        LocalDateTime dt = parseLocalDateTime(raw);
        return dt == null ? null : dt.toLocalDate();
    }

    /**
     * 解析为日期时间（宽松）；纯日期按当天 00:00:00 处理。
     *
     * @throws IllegalArgumentException 无法识别
     */
    public static LocalDateTime parseLocalDateTime(String raw) {
        if (raw == null) {
            return null;
        }
        String s = normalize(raw);
        if (s.isEmpty()) {
            return null;
        }
        // ① 含时间：先按原样（保留中间空格）试
        for (DateTimeFormatter f : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (Exception ignore) {
                // 继续试下一种
            }
        }
        // ② 纯日期：把内部空白去掉后再试（覆盖 "2026 年 9 月 19 日" 这类带空格的写法）
        String compact = s.replaceAll("\\s+", "");
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(compact, f).atStartOfDay();
            } catch (Exception ignore) {
                // 继续试下一种
            }
        }
        // ③ Excel 序列号：8 位已在上面的 yyyyMMdd 处理过，这里只认 1~7 位
        if (compact.matches("\\d{1,7}")) {
            try {
                long serial = Long.parseLong(compact);
                if (serial >= 1 && serial <= EXCEL_SERIAL_MAX) {
                    return EXCEL_EPOCH.plusDays(serial).atStartOfDay();
                }
            } catch (Exception ignore) {
                // 落到下面统一报错
            }
        }
        throw new IllegalArgumentException("无法识别的日期：" + raw);
    }

    /** 报错文案里给出的"支持的写法"清单（前后端提示统一用这里） */
    public static String supportedPatternsHint() {
        return "2026-09-19 / 2026/9/19 / 2026.9.19 / 20260919 / 2026年9月19日";
    }

    /** 归一化：全角字符转半角（中文输入法下的 ／ ． － ： 等），并去掉首尾空白 */
    private static String normalize(String raw) {
        String half = toHalfWidth(raw);
        return half == null ? "" : half.trim();
    }

    /** 全角 → 半角（ASCII 可见区 0xFF01~0xFF5E 与 0x21~0x7E 一一对应；全角空格单独处理） */
    private static String toHalfWidth(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            if (c == '\u3000') {
                sb.append(' ');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
