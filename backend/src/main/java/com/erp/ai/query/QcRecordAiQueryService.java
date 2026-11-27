package com.erp.ai.query;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.mapper.QcRecordMapper;
import org.springframework.stereotype.Service;

/**
 * 检验记录：供 AI 查询工具使用的只读 IService 适配（原服务不是 MyBatis-Plus 的 IService）。
 */
@Service("qcRecordAiQueryService")
public class QcRecordAiQueryService extends ServiceImpl<QcRecordMapper, QcRecord> {
}
