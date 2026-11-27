package com.erp.common.bill.link;

import com.erp.config.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 【2026-10-31 全模块下推守卫】统一判断“目标是否已全部下推”。
 *
 * <p>口径：已下推数量 = 已生效 + 草稿/待审核（由 BillPushStatusService 通用汇总）；
 * 目标 FULL 时禁止再次下推。状态服务异常时放行，不阻断正常业务，前端按钮隐藏与
 * 各单据原有业务校验仍作兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushGuardService {

    private final BillPushStatusService pushStatusService;

    @SuppressWarnings("unchecked")
    public void assertCanPush(String sourceType, Long sourceId, String targetType) {
        if (sourceType == null || sourceId == null || targetType == null) return;
        try {
            Map<String, Map<String, Object>> map = pushStatusService.summarize(sourceType, List.of(sourceId));
            Map<String, Object> row = map.get(String.valueOf(sourceId));
            if (row == null) return;
            Object targetsObj = row.get("targets");
            if (!(targetsObj instanceof Map)) return;
            Map<String, Object> targets = (Map<String, Object>) targetsObj;
            Object statusObj = targets.get(targetType);
            if (!(statusObj instanceof Map)) return;
            Map<String, Object> status = (Map<String, Object>) statusObj;
            if ("FULL".equals(String.valueOf(status.get("state")))) {
                String name = status.get("name") == null ? targetType : String.valueOf(status.get("name"));
                throw new BusinessException("该单据的「" + name + "」已全部下推（生效+草稿），不能重复下推");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("下推守卫状态检查失败 sourceType={} sourceId={} targetType={}",
                    sourceType, sourceId, targetType, e);
        }
    }
}
