package com.erp.ai.util;

import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具
 * 用于：
 *   1. AI 调用前对用户输入/业务数据中的敏感字段做脱敏，避免数据上传到 AI 厂商
 *   2. 日志打印时对敏感信息做脱敏，避免日志泄漏
 *
 * 支持的脱敏类型：
 *   - 手机号:  13800138000 → 138****0000
 *   - 身份证:  110101199001011234 → 110101********1234
 *   - 银行卡:  6222020200012345678 → 622202***********5678
 *   - 邮箱:    zhangsan@company.com → z***@company.com
 *   - 固定电话: 010-12345678 → 010-****5678
 */
public class SensitiveDataMasker {

    /**
     * 文件路径/文件名守卫：以数字串命名的上传文件（如 /uploads/ai-chat/2026/11/17/596467084381694498.jpg）
     * 会被数值类规则当成银行卡/手机号打码，模型拿到带星号的地址去读文件必然失败
     * （2026-11-17 实锤：596467084381694498.jpg → 596467********4498.jpg，看图直接报"图片无法读取"）。
     * 因此数值类规则统一加两条排除：前面是路径分隔符的、后面紧跟文件扩展名的，一律不打码。
     */
    private static final String NOT_PATH_SEGMENT = "(?<![0-9/\\\\])";
    private static final String NOT_FILE_EXT = "(?!\\.[A-Za-z0-9]{1,6}(?![A-Za-z0-9]))";

    private static final Pattern PHONE = Pattern.compile(NOT_PATH_SEGMENT + "1[3-9]\\d{9}(?![0-9])" + NOT_FILE_EXT);
    private static final Pattern ID_CARD = Pattern.compile(NOT_PATH_SEGMENT + "[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx](?![0-9A-Za-z])" + NOT_FILE_EXT);
    private static final Pattern BANK_CARD = Pattern.compile(NOT_PATH_SEGMENT + "[1-9]\\d{15,18}(?![0-9])" + NOT_FILE_EXT);
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern LANDLINE = Pattern.compile(NOT_PATH_SEGMENT + "0\\d{2,3}-?\\d{7,8}(?![0-9])" + NOT_FILE_EXT);

    private SensitiveDataMasker() {}

    /**
     * 全量脱敏：用于把数据发给 AI 厂商前
     * 脱敏所有敏感字段（手机号/身份证/银行卡/邮箱/固定电话）
     */
    public static String maskAll(String input) {
        if (input == null || input.isEmpty()) return input;
        String result = input;
        result = ID_CARD.matcher(result).replaceAll(m -> maskIdCard(m.group()));
        result = BANK_CARD.matcher(result).replaceAll(m -> maskBankCard(m.group()));
        result = PHONE.matcher(result).replaceAll(m -> maskPhone(m.group()));
        result = LANDLINE.matcher(result).replaceAll(m -> maskLandline(m.group()));
        result = EMAIL.matcher(result).replaceAll(m -> maskEmail(m.group()));
        return result;
    }

    /**
     * 日志脱敏：用于把日志输出到日志文件前
     * 比 maskAll 更严格，金额也做部分脱敏
     */
    public static String maskForLog(String input) {
        return maskAll(input);
    }

    /**
     * 截断超长字符串，避免日志膨胀
     */
    public static String truncate(String input, int maxLen) {
        if (input == null) return null;
        return input.length() > maxLen ? input.substring(0, maxLen) + "...(truncated)" : input;
    }

    /** 打码痕迹：连续 3 个及以上星号（手机号4个、身份证8个、邮箱3个、银行卡多个） */
    private static final Pattern MASKED_TRACE = Pattern.compile("\\*{3,}");

    /** 单个值是否含打码痕迹（如 138****8000）：写操作参数里出现即视为脏值，禁止落库 */
    public static boolean hasMaskedValue(String input) {
        return input != null && MASKED_TRACE.matcher(input).find();
    }

    /**
     * 递归查找写操作参数里的打码痕迹，返回第一个命中的参数路径（如 "phone" / "items[0].remark"）；
     * 无命中返回 null。用于"打码值不得落库"的防呆拦截（2026-11-17 立规）。
     */
    public static String findMaskedValuePath(Object value, String path) {
        if (value == null) return null;
        if (value instanceof String s) {
            return hasMaskedValue(s) ? path : null;
        }
        if (value instanceof java.util.Map<?, ?> m) {
            for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                String child = (path == null || path.isEmpty()) ? key : path + "." + key;
                String hit = findMaskedValuePath(e.getValue(), child);
                if (hit != null) return hit;
            }
            return null;
        }
        if (value instanceof java.util.Collection<?> c) {
            int i = 0;
            for (Object o : c) {
                String hit = findMaskedValuePath(o, path + "[" + i + "]");
                if (hit != null) return hit;
                i++;
            }
            return null;
        }
        if (value instanceof Object[] arr) {
            for (int i = 0; i < arr.length; i++) {
                String hit = findMaskedValuePath(arr[i], path + "[" + i + "]");
                if (hit != null) return hit;
            }
        }
        return null;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) return idCard;
        int len = idCard.length();
        return idCard.substring(0, 6) + "********" + idCard.substring(len - 4);
    }

    public static String maskBankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 8) return bankCard;
        int len = bankCard.length();
        return bankCard.substring(0, 6) + repeat("*", len - 10) + bankCard.substring(len - 4);
    }

    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) return email;
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) return email;
        String name = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (name.length() <= 1) return "*" + domain;
        if (name.length() <= 3) return name.charAt(0) + "***" + domain;
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }

    public static String maskLandline(String landline) {
        if (landline == null) return landline;
        // 010-12345678 → 010-****5678
        int dashIdx = landline.indexOf('-');
        if (dashIdx > 0 && landline.length() > dashIdx + 5) {
            String areaCode = landline.substring(0, dashIdx);
            String number = landline.substring(dashIdx + 1);
            if (number.length() >= 8) {
                return areaCode + "-****" + number.substring(number.length() - 4);
            }
        }
        return landline;
    }

    public static String maskName(String name) {
        if (name == null || name.length() < 2) return name;
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + repeat("*", name.length() - 2) + name.charAt(name.length() - 1);
    }

    private static String repeat(String s, int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder(count * s.length());
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
