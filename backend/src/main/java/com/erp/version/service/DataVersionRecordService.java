package com.erp.version.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.version.entity.DataVersionRecord;

import java.util.List;

public interface DataVersionRecordService extends IService<DataVersionRecord> {

    /**
     * 记录一条版本日志
     *
     * @param tableName       表名
     * @param recordId        记录ID
     * @param recordCode      业务编码(可空)
     * @param module          业务模块
     * @param operationType   INSERT/UPDATE/DELETE
     * @param beforeSnapshot  修改前JSON(INSERT时为null)
     * @param afterSnapshot   修改后JSON(DELETE时为null)
     * @param operator        操作人
     * @param remark          备注
     */
    void recordVersion(String tableName, Long recordId, String recordCode, String module,
                       String operationType, String beforeSnapshot, String afterSnapshot,
                       String operator, String remark);

    /**
     * 查询某条记录的全部版本
     */
    List<DataVersionRecord> listVersions(String tableName, Long recordId);

    /**
     * 查询模块下所有版本
     */
    List<DataVersionRecord> listByModule(String module, int limit);
}
