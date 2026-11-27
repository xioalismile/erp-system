package com.erp.ai.query;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.notification.entity.SysNotification;
import com.erp.notification.mapper.SysNotificationMapper;
import org.springframework.stereotype.Service;

/**
 * 消息通知：供 AI 查询工具使用的只读 IService 适配（原服务不是 MyBatis-Plus 的 IService）。
 */
@Service("sysNotificationAiQueryService")
public class SysNotificationAiQueryService extends ServiceImpl<SysNotificationMapper, SysNotification> {
}
