package com.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.system.entity.SysOperateLog;

import java.util.List;

public interface SysOperateLogService extends IService<SysOperateLog> {

    /**
     * 操作日志分页查询。
     *
     * @param operateUser 操作人筛选（可选）：按登录账号 / 真实姓名 / 用户编码模糊匹配；
     *                    为 null 或空表示不按人过滤（与旧口径一致）
     */
    PageResult<SysOperateLog> pageQuery(int current, int size, String keyword, String operateType, String moduleName,
                                       String startDate, String endDate, String sortBy, String sortOrder, String operateUser);

    void saveLog(SysOperateLog log);

    PageResult<SysOperateLog> getByBill(String billType, String billNo, int current, int size);

    /**
     * 便捷方法：记录单据操作日志（含字段级变更明细）
     *
     * @param billType        单据类型（如"销售订单"、"采购订单"、"客户"等，与 ChangeRecordDialog 中传入的 billType 保持一致）
     * @param billNo          单据编号
     * @param operateType     操作类型（"新增" / "修改" / "删除" / "审核" / "重置草稿" / "提交" 等）
     * @param operateSummary  操作摘要（如"修改了客户名称"）
     * @param userName        操作用户账号
     * @param realName        操作用户姓名
     * @param moduleName      模块名（如"销售管理"、"采购管理"、"基础资料"）
     * @param changes         字段级变更明细列表，每项 {fieldDesc, oldValue, newValue}，可为 null/empty
     */
    void saveBillLog(String billType, String billNo, String operateType,
                     String operateSummary, String userName, String realName,
                     String moduleName, List<FieldChange> changes);

    /**
     * 便捷方法（含请求上下文）：记录单据操作日志（含字段级变更明细）。
     * 适用于 AOP 切面拦截 HTTP 请求的场景，可同时记录 IP、URL、HTTP 方法、用户编码等。
     *
     * @param userCode        操作用户编码（用于填充 sys_operate_log.user_code NOT NULL 列，可传 userName 作为兜底）
     * @param ipAddress       客户端 IP 地址
     * @param requestUrl      请求 URL
     * @param requestMethod   请求 HTTP 方法（POST/PUT/DELETE 等）
     * @param operateResult   操作结果："成功" / "失败"（空则按"成功"处理）
     * @param failReason      失败原因（仅失败时有值，写入 sys_operate_log.fail_reason）
     */
    void saveBillLog(String billType, String billNo, String operateType,
                     String operateSummary, String userName, String realName,
                     String moduleName, List<FieldChange> changes,
                     String userCode, String ipAddress, String requestUrl, String requestMethod,
                     String operateResult, String failReason);

    /**
     * 字段变更项
     */
    class FieldChange {
        public String fieldDesc;
        public String oldValue;
        public String newValue;
        public String dataType;

        public FieldChange() {}

        public FieldChange(String fieldDesc, String oldValue, String newValue) {
            this.fieldDesc = fieldDesc;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public FieldChange(String fieldDesc, String oldValue, String newValue, String dataType) {
            this(fieldDesc, oldValue, newValue);
            this.dataType = dataType;
        }
    }
}
