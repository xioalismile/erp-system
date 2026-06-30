package com.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.system.entity.SysOperateLog;

public interface SysOperateLogService extends IService<SysOperateLog> {

    /** 分页查询: keyword模糊搜, operateType过滤, 时间范围过滤 */
    PageResult<SysOperateLog> pageQuery(int current, int size, String keyword, String operateType, String startDate, String endDate);

    /** 记录日志(供其他模块调用) */
    void saveLog(SysOperateLog log);
}
