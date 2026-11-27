package com.erp.ai.bill;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 明细行编辑接入表：收集所有 {@link BillItemHandler}（Spring Bean），按 billType 索引。
 *
 * <p>与 {@link BillActionRegistry} 分工：后者管"单据流程动作"（提交/审核/下推…），
 * 本表管"明细行编辑"（改单价/数量…）。两者 billType 命名必须一致。
 *
 * <p>启动时打印已接入清单 —— 便于排查"某单据为什么不能改明细"（历史坑：静默不生效无法定位）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillItemRegistry {

    private final ApplicationContext applicationContext;
    private final Map<String, BillItemHandler> handlers = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        Map<String, BillItemHandler> beans = applicationContext.getBeansOfType(BillItemHandler.class);
        int conflict = 0;
        for (BillItemHandler handler : beans.values()) {
            BillItemHandler exists = handlers.putIfAbsent(handler.billType(), handler);
            if (exists != null) {
                conflict++;
                log.warn("[明细行编辑] 单据类型冲突：{} 已由 {} 接入，本次的 {} 未生效（保留先注册者）",
                        handler.billType(), exists.getClass().getSimpleName(), handler.getClass().getSimpleName());
            } else {
                log.info("[明细行编辑] 已接入单据: {}（{}，可改字段={}）",
                        handler.billType(), handler.itemLabel(), handler.editableFields());
            }
        }
        log.info("[明细行编辑] 共接入 {} 种单据的明细行编辑（冲突丢弃 {}）", handlers.size(), conflict);
    }

    /** 取适配器；不支持返回 null */
    public BillItemHandler get(String billType) {
        return billType == null ? null : handlers.get(billType);
    }

    /** 是否支持该单据的明细行编辑 */
    public boolean supports(String billType) {
        return billType != null && handlers.containsKey(billType);
    }

    /** 已接入的单据类型（提示"暂不支持"时用） */
    public Collection<String> allBillTypes() {
        return handlers.keySet();
    }
}
