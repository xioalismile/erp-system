package com.erp.ai.query;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.qc.entity.QcTemplate;
import com.erp.qc.mapper.QcTemplateMapper;
import org.springframework.stereotype.Service;

/**
 * 质检模板：供 AI 查询工具使用的只读 IService 适配（原服务不是 MyBatis-Plus 的 IService）。
 */
@Service("qcTemplateAiQueryService")
public class QcTemplateAiQueryService extends ServiceImpl<QcTemplateMapper, QcTemplate> {
}
