package com.erp.kai.service.adapter;

import com.erp.kai.entity.KaiExtConnection;
import com.erp.kai.service.agent.KaiYunForwardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 小楷独立版 · 楷煜满血连接适配器（FEAT-KAI-STANDALONE-009）。
 *
 * KAIYUN 连接不走「标准动作」模式：对话整轮转发给楷煜 ERP 内小楷（KaiYunForwardService），
 * 用户在独立站获得与楷煜内一致的全部能力。本适配器只承担：
 * ① 测试连接 = 真实登录楷煜并选公司；
 * ② 工厂注册（对话分流判断连接类型前必须能通过工厂取到适配器）。
 * 其余标准动作给出中文说明（该类型不需要、也不应被调用）。
 */
@Component
@RequiredArgsConstructor
public class KaiYunForwardAdapter implements KaiExtAdapter {

    private final KaiYunForwardService forwardService;

    @Override
    public String type() {
        return "KAIYUN";
    }

    @Override
    public Map<String, Object> testConnection(KaiExtConnection conn) {
        return forwardService.loginTest(conn);
    }

    @Override
    public List<Map<String, Object>> listRecords(KaiExtConnection conn, String objectType, Map<String, Object> filters) {
        throw notApplicable();
    }

    @Override
    public Map<String, Object> createRecords(KaiExtConnection conn, String objectType, List<Map<String, Object>> records) {
        throw notApplicable();
    }

    @Override
    public Map<String, Object> createBill(KaiExtConnection conn, Map<String, Object> bill) {
        throw notApplicable();
    }

    @Override
    public Map<String, Object> executeAction(KaiExtConnection conn, String action, Map<String, Object> params) {
        throw notApplicable();
    }

    @Override
    public Map<String, String> supportedObjectTypes() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> writableFields(String objectType) {
        return Collections.emptyMap();
    }

    private IllegalArgumentException notApplicable() {
        return new IllegalArgumentException(
                "楷煜满血连接无需单独执行该动作——请直接在对话里说需求，由楷煜 ERP 内的小楷完成");
    }
}
