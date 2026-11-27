package com.erp.ai.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语音合成前的文本规范化（2026-10-25）。
 *
 * 背景（用户实测「自然女声播报会跳字、发音不准」）：
 *   本地自然女声模型对阿拉伯数字和英文字母很不擅长——实测「12345.67」读成碎片、
 *   「12.5%」读成「125 倍」、单据号「SO20261025001」直接漏读字母和若干数字；
 *   另外「行号」被读成「型号」。
 *   这里在送合成前统一转成中文读法：日期、时间、百分比、金额、数字、英文字母、已实证的多音词。
 *
 * 边界：**只作用于 TTS 输入**，不改变界面、日志、数据库里的任何原文。
 */
public final class TtsTextNormalizer {

    private TtsTextNormalizer() {}

    private static final char[] DIGITS = "零一二三四五六七八九".toCharArray();

    /** 英文字母的中文读法（按字母表顺序 A-Z） */
    private static final String[] LETTERS = {
            "诶", "必", "西", "迪", "伊", "艾弗", "吉", "艾尺", "艾", "杰", "开", "艾勒", "艾姆",
            "恩", "欧", "屁", "丘", "啊", "艾斯", "提", "优", "维", "达不溜", "艾克斯", "歪", "贼"
    };

    private static final Pattern P_DATE =
            Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern P_TIME =
            Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern P_PERCENT =
            Pattern.compile("(\\d+(?:,\\d{3})*(?:\\.\\d+)?)\\s*[%％]");
    private static final Pattern P_CURRENCY =
            Pattern.compile("[¥￥]\\s*(\\d+(?:,\\d{3})*(?:\\.\\d+)?)");
    private static final Pattern P_GROUPED =
            Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?");
    private static final Pattern P_NUMBER =
            Pattern.compile("\\d+(?:\\.\\d+)?");

    /**
     * 把一段准备朗读的文字转成"模型读得准"的中文写法。
     *
     * @param text 原始文本
     * @return 用于语音合成的文本（可能为空）
     */
    public static String normalize(String text) {
        if (text == null || text.trim().isEmpty()) return "";

        String t = toHalfWidth(text);

        // 去掉 Markdown / 装饰符号，避免念出"星号井号"
        t = t.replace("```", " ")
                .replaceAll("\\|[^\\n]*\\|", " ")
                .replaceAll("!?\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("[*_`#>~]", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s{2,}", " ");

        // 日期：2026-10-25 / 2026/10/25 → 二零二六年十月二十五日
        t = replaceAll(t, P_DATE, (m, src) -> chineseYear(m.group(1)) + "年"
                + numberToChinese(m.group(2)) + "月"
                + numberToChinese(m.group(3)) + "日");

        // 时间：09:30 → 九点三十分
        t = replaceAll(t, P_TIME, (m, src) -> numberToChinese(m.group(1)) + "点"
                + numberToChinese(m.group(2)) + "分");

        // 百分比：12.5% → 百分之十二点五
        t = replaceAll(t, P_PERCENT, (m, src) -> "百分之" + numberToChinese(m.group(1)));

        // 金额符号：¥123.45 → 一百二十三点四五元
        t = replaceAll(t, P_CURRENCY, (m, src) -> numberToChinese(m.group(1)) + "元");

        // 带千分位的数字：1,234,567 → 一百二十三万四千五百六十七
        t = replaceAll(t, P_GROUPED, (m, src) -> numberToChinese(m.group()));

        // 其余数字：数量按中文读，单号/长串/带字母的按位读
        t = replaceAll(t, P_NUMBER, (m, src) -> {
            String num = m.group();
            String intPart = num.contains(".") ? num.substring(0, num.indexOf('.')) : num;
            int at = m.start();
            boolean nearLetter = (at > 0 && isAsciiLetter(src.charAt(at - 1)))
                    || (m.end() < src.length() && isAsciiLetter(src.charAt(m.end())));
            boolean leadingZero = intPart.length() > 1 && intPart.charAt(0) == '0';
            boolean longDigits = intPart.length() >= 8;
            if (nearLetter || leadingZero || longDigits) return readDigitByDigit(num);
            return numberToChinese(num);
        });

        // 英文字母：模型会直接漏读，改成中文读法（S O → 艾斯 欧）
        t = replaceLetters(t);

        // 已实证的多音词（只加验证过的，避免过度替换反而读错）
        t = t.replace("行号", "航号");
        // 「第三行 / 第2行 / 两行」里的「行」读 háng，模型会读成 xíng（实测「第三行」被读成「第三型」）
        t = t.replaceAll("(第?[零一二三四五六七八九十百千万两]+)行", "$1航");

        // 数学/符号
        t = t.replace("×", "乘").replace("÷", "除以").replace("+", "加")
                .replace("=", "等于").replace("@", "艾特").replace("&", "和");

        return t.replaceAll("\\s{2,}", " ").trim();
    }

    // ===== 内部实现 =====

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /** 只把全角数字/字母/少数运算符号转半角；中文标点（，。！？：；）原样保留，避免破坏朗读停顿 */
    private static String toHalfWidth(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if ((c >= '\uFF10' && c <= '\uFF19')          // ０-９
                    || (c >= '\uFF21' && c <= '\uFF3A')   // Ａ-Ｚ
                    || (c >= '\uFF41' && c <= '\uFF5A')) { // ａ-ｚ
                sb.append((char) (c - 0xFEE0));
            } else if (c == '\uFF05' || c == '\uFF0B' || c == '\uFF1D'
                    || c == '\uFF20' || c == '\uFF06' || c == '\uFF0E') {
                sb.append((char) (c - 0xFEE0));
            } else if (c == '\u3000') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String chineseYear(String y) {
        StringBuilder sb = new StringBuilder();
        for (char c : y.toCharArray()) {
            if (c >= '0' && c <= '9') sb.append(DIGITS[c - '0']);
        }
        return sb.toString();
    }

    private static String readDigitByDigit(String num) {
        StringBuilder sb = new StringBuilder();
        for (char c : num.toCharArray()) {
            if (c >= '0' && c <= '9') sb.append(DIGITS[c - '0']);
            else if (c == '.') sb.append('点');
        }
        return sb.toString();
    }

    private static String replaceLetters(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 'A' && c <= 'Z') sb.append(LETTERS[c - 'A']);
            else if (c >= 'a' && c <= 'z') sb.append(LETTERS[c - 'a']);
            else sb.append(c);
        }
        return sb.toString();
    }

    /** 阿拉伯数字（可带小数）转中文读法 */
    private static String numberToChinese(String num) {
        String n = num.replace(",", "").trim();
        if (n.isEmpty()) return "";
        boolean negative = n.startsWith("-");
        if (negative) n = n.substring(1);
        boolean decimal = n.contains(".");
        String intPart = decimal ? n.substring(0, n.indexOf('.')) : n;
        String decPart = decimal ? n.substring(n.indexOf('.') + 1) : "";
        String head = negative ? "负" : "";
        if (decimal && intPart.isEmpty()) intPart = "0";
        String intCn = integerToChinese(intPart);
        if (!decimal) return head + intCn;
        StringBuilder sb = new StringBuilder(head).append(intCn).append("点");
        for (char c : decPart.toCharArray()) {
            if (c >= '0' && c <= '9') sb.append(DIGITS[c - '0']);
        }
        return sb.toString();
    }

    /** 整数转中文（支持到万亿；0 读「零」） */
    private static String integerToChinese(String digits) {
        String d = digits.replaceFirst("^0+(?=\\d)", "");
        if (d.isEmpty()) return "零";
        if ("0".equals(d)) return "零";

        String[] bigUnits = {"", "万", "亿", "万亿"};
        // 从右往左按 4 位分组
        int n = d.length();
        String[] groups = new String[4];
        int gi = 0;
        while (n > 0 && gi < groups.length) {
            int start = Math.max(0, n - 4);
            groups[gi++] = d.substring(start, n);
            n = start;
        }

        StringBuilder out = new StringBuilder();
        for (int i = gi - 1; i >= 0; i--) {
            String g = groups[i];
            String gcn = groupToChinese(g);
            if (gcn.isEmpty()) {
                if (out.length() > 0 && !out.toString().endsWith("零")) out.append('零');
                continue;
            }
            if (out.length() > 0 && g.length() == 4 && g.charAt(0) == '0'
                    && !out.toString().endsWith("零")) {
                out.append('零');
            }
            out.append(gcn).append(bigUnits[i]);
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '零') {
            out.setLength(out.length() - 1);
        }
        String result = out.toString();
        // 十二 / 十三…… 不读成「一十二」
        if (result.startsWith("一十")) result = result.substring(1);
        return result.isEmpty() ? "零" : result;
    }

    /** 4 位以内一组转中文（不含大单位） */
    private static String groupToChinese(String g) {
        String[] units = {"", "十", "百", "千"};
        StringBuilder sb = new StringBuilder();
        boolean zeroPending = false;
        for (int i = 0; i < g.length(); i++) {
            int digit = g.charAt(i) - '0';
            int pos = g.length() - i - 1;
            if (digit == 0) {
                zeroPending = true;
                continue;
            }
            if (zeroPending && sb.length() > 0) sb.append('零');
            zeroPending = false;
            sb.append(DIGITS[digit]).append(units[pos]);
        }
        return sb.toString();
    }

    private interface Replacer {
        String apply(Matcher m, String source);
    }

    private static String replaceAll(String input, Pattern pattern, Replacer replacer) {
        Matcher m = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(m, input)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
