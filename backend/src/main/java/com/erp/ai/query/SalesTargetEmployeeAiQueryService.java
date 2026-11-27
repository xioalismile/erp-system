package com.erp.ai.query;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.crm.entity.SalesTargetEmployee;
import com.erp.crm.mapper.SalesTargetEmployeeMapper;
import org.springframework.stereotype.Service;

/**
 * 销售目标分配：供 AI 查询工具使用的只读 IService 适配（原服务不是 MyBatis-Plus 的 IService）。
 */
@Service("salesTargetEmployeeAiQueryService")
public class SalesTargetEmployeeAiQueryService extends ServiceImpl<SalesTargetEmployeeMapper, SalesTargetEmployee> {
}
