package com.erp.version.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.version.entity.DataVersionRecord;
import com.erp.version.mapper.DataVersionRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DataVersionRecordServiceImpl
        extends ServiceImpl<DataVersionRecordMapper, DataVersionRecord>
        implements DataVersionRecordService {

    private static final Logger log = LoggerFactory.getLogger(DataVersionRecordServiceImpl.class);

    @Override
    public void recordVersion(String tableName, Long recordId, String recordCode, String module,
                              String operationType, String beforeSnapshot, String afterSnapshot,
                              String operator, String remark) {
        try {
            // 计算下一个版本号
            Long maxVer = baseMapper.selectObjs(
                    new QueryWrapper<DataVersionRecord>()
                            .select("COALESCE(MAX(version_number),0) AS maxVer")
                            .eq("table_name", tableName)
                            .eq("record_id", recordId)
            ).stream()
                    .filter(java.util.Objects::nonNull)
                    .map(o -> ((Number) o).longValue())
                    .findFirst()
                    .orElse(0L);

            DataVersionRecord r = new DataVersionRecord();
            r.setTableName(tableName);
            r.setRecordId(recordId);
            r.setRecordCode(recordCode);
            r.setBusinessModule(module);
            r.setOperationType(operationType);
            r.setBeforeSnapshot(beforeSnapshot);
            r.setAfterSnapshot(afterSnapshot);
            r.setVersionNumber(maxVer + 1);
            r.setOperator(operator);
            r.setOperateTime(LocalDateTime.now());
            r.setRolledBack((short) 0);
            r.setRollbackEnabled((short) 1);
            r.setRemark(remark);
            save(r);
        } catch (Exception e) {
            // 版本记录失败不影响主流程
            log.error("记录版本日志失败 table={} id={}: {}", tableName, recordId, e.getMessage());
        }
    }

    @Override
    public List<DataVersionRecord> listVersions(String tableName, Long recordId) {
        return list(new QueryWrapper<DataVersionRecord>()
                .eq("table_name", tableName)
                .eq("record_id", recordId)
                .orderByDesc("version_number"));
    }

    @Override
    public List<DataVersionRecord> listByModule(String module, int limit) {
        return list(new QueryWrapper<DataVersionRecord>()
                .eq("business_module", module)
                .orderByDesc("version_number")
                .last("LIMIT " + limit));
    }

}
