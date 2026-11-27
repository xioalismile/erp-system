package com.erp.ai.query;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.masterdata.entity.CustomerWarningConfig;
import com.erp.masterdata.mapper.CustomerWarningConfigMapper;
import org.springframework.stereotype.Service;

/**
 * 客户跟进预警配置：供 AI 查询工具使用的只读 IService 适配（原服务不是 MyBatis-Plus 的 IService）。
 */
@Service("customerWarningConfigAiQueryService")
public class CustomerWarningConfigAiQueryService extends ServiceImpl<CustomerWarningConfigMapper, CustomerWarningConfig> {
}
