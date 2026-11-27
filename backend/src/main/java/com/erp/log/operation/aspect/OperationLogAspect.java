package com.erp.log.operation.aspect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.erp.common.Result;
import com.erp.log.operation.annotation.OperationLogAnnotation;
import com.erp.log.operation.entity.OperationLog;
import com.erp.log.operation.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;

    @Around("@annotation(operationLogAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLogAnnotation operationLogAnnotation) throws Throwable {
        long startTime = System.currentTimeMillis();
        LocalDateTime operationTime = LocalDateTime.now();
        Object result = null;
        Throwable throwable = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            throwable = e;
            throw e;
        } finally {
            try {
                saveLog(joinPoint, operationLogAnnotation, startTime, operationTime, result, throwable);
            } catch (Exception e) {
                log.error("保存操作日志失败", e);
            }
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, OperationLogAnnotation annotation,
                         long startTime, LocalDateTime operationTime, Object result, Throwable throwable) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        OperationLog operationLog = new OperationLog();
        operationLog.setLogNo("LOG" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        operationLog.setModule(annotation.module());
        operationLog.setModuleName(annotation.moduleName());
        operationLog.setOperationType(annotation.operationType());
        operationLog.setOperationName(annotation.operationName());
        operationLog.setOperationTime(operationTime);

        if (request != null) {
            Long userId = (Long) request.getAttribute("currentUserId");
            String username = (String) request.getAttribute("currentUsername");
            operationLog.setUserId(userId);
            operationLog.setUsername(username);
            operationLog.setIpAddress(getIpAddress(request));
            operationLog.setUserAgent(request.getHeader("User-Agent"));
            operationLog.setRequestMethod(request.getMethod());
            operationLog.setRequestUrl(request.getRequestURI());
        }

        if (annotation.saveRequestParams()) {
            try {
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                String[] paramNames = signature.getParameterNames();
                Object[] paramValues = joinPoint.getArgs();
                if (paramNames != null && paramValues != null && paramNames.length > 0) {
                    Map<String, Object> paramsMap = new java.util.HashMap<>();
                    for (int i = 0; i < paramNames.length; i++) {
                        if (isIgnoreParam(paramValues[i])) {
                            continue;
                        }
                        paramsMap.put(paramNames[i], paramValues[i]);
                    }
                    if (!paramsMap.isEmpty()) {
                        operationLog.setRequestParams(JSONUtil.toJsonStr(paramsMap));
                    }
                }
            } catch (Exception e) {
                log.warn("获取请求参数失败", e);
            }
        }

        if (annotation.saveResult() && result != null) {
            try {
                if (result instanceof Result<?> r) {
                    operationLog.setResultCode(r.getCode());
                    operationLog.setResultMessage(r.getMessage());
                    operationLog.setStatus(r.getCode() == 200 ? 1 : 0);
                } else {
                    operationLog.setResultCode(200);
                    operationLog.setStatus(1);
                }
            } catch (Exception e) {
                log.warn("解析返回结果失败", e);
            }
        }

        if (throwable != null) {
            operationLog.setStatus(0);
            operationLog.setResultCode(500);
            operationLog.setResultMessage(StrUtil.sub(throwable.getMessage(), 0, 500));
        }

        operationLog.setCostTime(System.currentTimeMillis() - startTime);

        try {
            operationLogService.createLog(operationLog);
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
    }

    private boolean isIgnoreParam(Object param) {
        if (param == null) {
            return true;
        }
        return param instanceof jakarta.servlet.http.HttpServletRequest
                || param instanceof jakarta.servlet.http.HttpServletResponse
                || param instanceof org.springframework.web.multipart.MultipartFile;
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (StrUtil.isNotBlank(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
