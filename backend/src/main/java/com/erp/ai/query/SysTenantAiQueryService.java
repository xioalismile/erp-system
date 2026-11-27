package com.erp.ai.query;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.tenant.entity.SysTenant;
import com.erp.tenant.mapper.SysTenantMapper;
import org.springframework.stereotype.Service;

/**
 * 租户：供 AI 查询工具使用的只读 IService 适配（原服务不是 MyBatis-Plus 的 IService）。
 */
@Service("sysTenantAiQueryService")
public class SysTenantAiQueryService extends ServiceImpl<SysTenantMapper, SysTenant> {
}
