package com.erp.config;

import com.erp.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败 ( @Valid / @Validated )
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败: {}", msg);
        return Result.error(400, msg.isEmpty() ? "参数校验失败" : msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<?> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数绑定失败: {}", msg);
        return Result.error(400, msg.isEmpty() ? "参数校验失败" : msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        log.warn("约束校验失败: {}", msg);
        return Result.error(400, msg.isEmpty() ? "参数校验失败" : msg);
    }

    /**
     * 请求体缺失或JSON格式错误
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(400, "请求数据格式错误，请检查输入");
    }

    /**
     * 缺少必填请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.error(400, "缺少必填参数：" + e.getParameterName());
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        String paramName = e.getName();
        Class<?> requiredType = e.getRequiredType();
        String requiredTypeName = requiredType != null ? requiredType.getSimpleName() : "未知类型";
        Object actualValue = e.getValue();
        String actualTypeName = actualValue != null ? actualValue.getClass().getSimpleName() : "null";
        String msg = String.format("参数[%s]格式不正确：期望[%s]类型，实际为[%s]类型（值: %s）",
                paramName, requiredTypeName, actualTypeName, actualValue);
        return Result.error(400, msg);
    }

    /**
     * 数据库完整性约束违反（外键、唯一键、非空约束等）
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<?> handleDataIntegrity(DataIntegrityViolationException e) {
        String rootMsg = getRootMessage(e);
        log.warn("数据完整性约束违反: {}", rootMsg);

        String tip = friendlyDataError(e);
        if (tip != null) {
            return Result.error(400, tip);
        }
        return Result.error(400, "数据保存失败：请检查必填字段是否完整（详情: " + truncate(rootMsg, 150) + "）");
    }

    /**
     * 数据库完整性约束错误 → 用户看得懂的中文提示（2026-09-21 抽出为公共方法）。
     * 供本处理器与"导入逐行落库"等自行捕获异常的链路复用；返回 null 表示无法判定，由调用方兜底。
     * 同时兼容 PostgreSQL 的英文与中文报错文案（121 生产库 lc_messages 为中文）。
     */
    public static String friendlyDataError(Throwable e) {
        String rootMsg = getRootMessage(e);
        if (rootMsg == null) {
            return null;
        }
        if (rootMsg.contains("null") || rootMsg.contains("NOT NULL") || rootMsg.contains("非空约束")) {
            // 把字段名挖出来，例如 "null value in column \"currency_id\"" / "字段 \"currency_id\" 的 null 值违反非空约束"
            String field = extractColumn(rootMsg);
            return field != null
                ? "必填字段[" + field + "]不能为空，请完善后再提交"
                : "必填字段不能为空，请检查所有标注*的字段";
        }
        if (rootMsg.contains("duplicate") || rootMsg.contains("Duplicate") || rootMsg.contains("unique")
                || rootMsg.contains("Unique") || rootMsg.contains("唯一约束") || rootMsg.contains("重复键")) {
            String field = extractColumn(rootMsg);
            return field != null
                ? "数据重复，[" + field + "]已存在，请修改编码/名称"
                : "数据重复，该记录已存在或编码/名称冲突";
        }
        if (rootMsg.contains("foreign key") || rootMsg.contains("foreign_key")
                || rootMsg.contains("referenced") || rootMsg.contains("外键约束")) {
            // PostgreSQL: insert or update on table "xxx" violates foreign key constraint "yyy"
            //             / 插入或更新表 "xxx" 违反外键约束 "yyy"
            // Detail: Key (column)=(value) is not present in table "ref_table".
            //             / 详细：键值对(column)=(value)没有在表"ref_table"中出现
            String field = extractColumn(rootMsg);
            String refTable = extractReferencedTable(rootMsg);
            String fkValue = extractFkValue(rootMsg);
            StringBuilder sb = new StringBuilder();
            sb.append("关联数据不存在");
            if (field != null) sb.append("（字段:").append(field);
            if (fkValue != null) sb.append("=").append(fkValue);
            if (field != null) sb.append("）");
            if (refTable != null) sb.append("，请检查关联表[").append(refTable).append("]的该记录");
            sb.append("，修复后再提交");
            return sb.toString();
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private static String extractColumn(String msg) {
        if (msg == null) return null;
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile("column \"([^\"]+)\"");
        java.util.regex.Matcher m1 = p1.matcher(msg);
        if (m1.find()) return m1.group(1);
        // 中文文案：字段 "currency_id" 的 null 值违反非空约束
        java.util.regex.Pattern p1c = java.util.regex.Pattern.compile("字段 \"([^\"]+)\"");
        java.util.regex.Matcher m1c = p1c.matcher(msg);
        if (m1c.find()) return m1c.group(1);
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("Key \\(([^)]+)\\)");
        java.util.regex.Matcher m2 = p2.matcher(msg);
        if (m2.find()) return m2.group(1);
        // 中文文案：详细：键值对(assigned_sales_id)=(0)没有在表"employee"中出现
        java.util.regex.Pattern p2c = java.util.regex.Pattern.compile("键值对\\s*\\(([^)]+)\\)");
        java.util.regex.Matcher m2c = p2c.matcher(msg);
        if (m2c.find()) return m2c.group(1);
        return null;
    }

    private static String extractReferencedTable(String msg) {
        if (msg == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("in table \"([^\"]+)\"");
        java.util.regex.Matcher m = p.matcher(msg);
        String last = null;
        while (m.find()) last = m.group(1);
        if (last != null) return last;
        // 中文文案：没有在表"employee"中出现
        java.util.regex.Pattern pc = java.util.regex.Pattern.compile("在表\\s*\"([^\"]+)\"");
        java.util.regex.Matcher mc = pc.matcher(msg);
        while (mc.find()) last = mc.group(1);
        return last;
    }

    private static String extractFkValue(String msg) {
        if (msg == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("=\\(([^)]+)\\)");
        java.util.regex.Matcher m = p.matcher(msg);
        if (m.find()) return m.group(1);
        return null;
    }

    /**
     * SQL异常
     */
    @ExceptionHandler(SQLException.class)
    public Result<?> handleSql(SQLException e) {
        log.error("SQL异常: SQLState={}, errorCode={}", e.getSQLState(), e.getErrorCode(), e);
        String msg = e.getMessage();
        if (msg != null && msg.contains("null value")) {
            return Result.error(400, "必填字段不能为空，请检查所有标注*的字段");
        }
        if (msg != null && (msg.contains("duplicate key") || msg.contains("unique constraint"))) {
            return Result.error(400, "数据重复，编码或名称已存在");
        }
        return Result.error(400, "数据操作失败，请检查输入是否正确");
    }

    /**
     * 非法参数
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArg(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * 空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public Result<?> handleNullPointer(NullPointerException e) {
        log.error("空指针异常", e);
        return Result.error(400, "操作失败：缺少必要的输入数据，请检查必填字段");
    }

    /**
     * 其他未捕获异常 - 保留原始错误消息给前端（不带堆栈），避免用笼统的"关联数据不存在"掩盖真实原因
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "操作失败，请检查输入数据是否正确";
        }
        // 消息过长时截断，避免把堆栈信息整体返回前端
        if (msg.length() > 500) {
            msg = msg.substring(0, 500);
        }
        // 只有真正"找不到"的场景才返回400（通用前端非GET会弹窗提示），其他统一500
        if (msg.contains("不存在") || msg.contains("未找到") || msg.contains("not found") || msg.contains("不存在")) {
            return Result.error(400, msg);
        }
        if (msg.contains("不足") || msg.contains("为空") || msg.contains("必填")) {
            return Result.error(400, msg);
        }
        return Result.error(500, msg);
    }

    /**
     * 提取异常链最底层的错误消息
     */
    private static String getRootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : e.getMessage();
    }
}
