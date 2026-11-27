package com.erp.permission.config;

import com.erp.common.Result;
import com.erp.permission.service.FieldVisiblePermissionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Set;

/**
 * 字段权限响应过滤：按角色把不可见字段从查询接口的响应 JSON 中移除。
 *
 * <p>安全语义：前端隐藏只是展示层控制，此处在后端出参层面做数据脱敏，
 * 防止绕过前端直接调接口读取被关闭的字段（如联系人/联系电话）。</p>
 *
 * <p>2026-11-27 改造：</p>
 * <ul>
 *   <li>路径 → page_key 改由 {@link BillPermissionPathRegistry} 统一解析：
 *       列表接口集中登记；明细报表接口启动扫描自动对应（新增报表零改动）；</li>
 *   <li>不可见字段集合改由 {@link FieldVisiblePermissionService} 统一解析
 *       （与导出剔列共用同一份口径与缓存）；</li>
 *   <li>导出是文件响应、不经过本类，已在 ExcelServiceImpl 写盘前单独剔列。</li>
 * </ul>
 *
 * <p>规则：没有登录上下文不处理；管理员/经理/超管短路；仅 GET 的 Result 响应；
 * data 对象 / data.records 数组 / data.totals 合计对象都会剔除不可见字段。</p>
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class FieldPermissionResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;
    private final FieldVisiblePermissionService fieldVisiblePermissionService;
    private final BillPermissionPathRegistry pathRegistry;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 仅处理统一 Result 包装的响应
        return Result.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        try {
            if (!(body instanceof Result<?> result)) return body;
            if (!(request instanceof ServletServerHttpRequest servletRequest)) return body;

            HttpServletRequest req = servletRequest.getServletRequest();
            if (!"GET".equalsIgnoreCase(req.getMethod())) return body;

            String path = req.getRequestURI();
            // 首选按"返回数据的实体类"自动对应 page_key（新单据/新接口零登记）；
            // Map/视图类响应无法从数据判断实体，回退到报表/列表路径登记。
            String pageKey = pathRegistry.fromEntityClass(firstEntityClass(result.getData()));
            if (pageKey == null) pageKey = pathRegistry.resolve(path);
            if (pageKey == null) return body;

            Set<String> hidden = fieldVisiblePermissionService.currentUserHiddenKeys(pageKey);
            if (hidden.isEmpty()) return body;

            return filterResult(result, hidden);
        } catch (Exception e) {
            // 过滤异常不阻断正常业务响应
            log.warn("字段权限响应过滤失败（放行原始数据）: path={}, err={}",
                    request.getURI().getPath(), e.getMessage());
            return body;
        }
    }

    /** 响应数据里第一条业务对象/实体的类（PageResult.records / List / 单对象；Map 等视图返回 null） */
    private Class<?> firstEntityClass(Object data) {
        if (data == null) return null;
        if (data instanceof com.erp.common.PageResult<?> pr) return firstEntityClass(pr.getRecords());
        if (data instanceof java.util.List<?> list) {
            for (Object item : list) {
                if (item == null || item instanceof java.util.Map || item instanceof java.util.Collection) continue;
                return item.getClass();
            }
            return null;
        }
        if (data instanceof java.util.Map || data instanceof java.util.Collection
                || data instanceof CharSequence || data instanceof Number
                || data instanceof Boolean) return null;
        return data.getClass();
    }

    /** 把 Result 序列化为 JSON 树并移除隐藏字段（支持 data 为对象 / 数组 / PageResult.records 数组） */
    private Object filterResult(Result<?> result, Set<String> hidden) {
        JsonNode root = objectMapper.valueToTree(result);
        if (!(root instanceof ObjectNode rootObj)) return result;
        JsonNode data = rootObj.get("data");
        if (data == null || data.isNull()) return result;
        if (data.isObject()) {
            removeHidden((ObjectNode) data, hidden);
            // PageResult 形态：data.records 为数组
            JsonNode records = data.get("records");
            if (records instanceof ArrayNode arr) {
                arr.forEach(n -> { if (n.isObject()) removeHidden((ObjectNode) n, hidden); });
            }
            // 明细报表形态：data.totals 是列 key 的合计对象（隐藏字段的合计同样不能下发）
            JsonNode totals = data.get("totals");
            if (totals instanceof ObjectNode totalsObj) removeHidden(totalsObj, hidden);
        } else if (data instanceof ArrayNode arr) {
            arr.forEach(n -> { if (n.isObject()) removeHidden((ObjectNode) n, hidden); });
        }
        return root;
    }

    private void removeHidden(ObjectNode node, Set<String> hidden) {
        node.remove(hidden);
    }
}
