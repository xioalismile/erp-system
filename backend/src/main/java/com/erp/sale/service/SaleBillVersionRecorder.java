package com.erp.sale.service;

import com.erp.version.service.DataVersionRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 销售单据数据版本记录器（销售换货单 / 售后维修单）。
 *
 * <p>背景：这两张单据的控制器不是 {@code BaseCrudController} 子类，版本切面不会自动拦截，
 * 因此由单据服务在保存与删除成功后主动调用本记录器。</p>
 *
 * <p>事务口径（守则 10 第 7.4 条）：版本记录是旁路写，必须在<b>独立事务</b>里执行。
 * PostgreSQL 下事务内任何语句报错都会把整个事务置为 aborted，裸 catch 救不回来 ——
 * 内层失败只能回滚内层事务，绝不能把已经保存的业务单据一起回滚。</p>
 *
 * <p>调用方约定：调用本类方法时必须自行 try-catch，记录失败只告警、不影响主流程。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleBillVersionRecorder {

    private final DataVersionRecordService versionService;
    private final ObjectMapper objectMapper;

    /**
     * 记录一条单据版本（独立事务）。
     *
     * @param tableName     业务表名，如 sale_exchange / sale_repair
     * @param recordId      单据主键
     * @param recordCode    单据编号
     * @param module        业务模块（固定 sale）
     * @param operationType INSERT / UPDATE / DELETE
     * @param before        修改前对象（INSERT 传 null）
     * @param after         修改后对象（DELETE 传 null）
     * @param operator      操作人（为空时从当前请求取登录账号）
     * @param remark        备注
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String tableName, Long recordId, String recordCode, String module,
                       String operationType, Object before, Object after,
                       String operator, String remark) {
        if (recordId == null || tableName == null) return;
        versionService.recordVersion(tableName, recordId, recordCode, module, operationType,
                toJson(before, tableName, recordId), toJson(after, tableName, recordId),
                resolveOperator(operator), remark);
    }

    /** 快照序列化：失败不阻断版本记录，按 null 落库 */
    private String toJson(Object obj, String tableName, Long recordId) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("销售单据版本快照序列化失败 table={} id={}: {}", tableName, recordId, e.getMessage());
            return null;
        }
    }

    /** 操作人：优先调用方传入，其次当前请求的登录账号，最后 system */
    private String resolveOperator(String operator) {
        if (operator != null && !operator.isBlank()) return operator;
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                Object u = req.getAttribute("currentUsername");
                if (u instanceof String s && !s.isBlank()) return s;
            }
        } catch (Exception ignored) {
            // 非 Web 线程（定时任务/内部调用）取不到登录人，按 system 记录
        }
        return "system";
    }
}
