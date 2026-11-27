package com.erp.masterdata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.CustomerPoolConfig;
import com.erp.masterdata.entity.CustomerTagDict;
import com.erp.masterdata.entity.CustomerWarningConfig;
import com.erp.masterdata.entity.CustomerSegment;
import com.erp.masterdata.mapper.CustomerPoolConfigMapper;
import com.erp.masterdata.mapper.CustomerTagDictMapper;
import com.erp.masterdata.mapper.CustomerWarningConfigMapper;
import com.erp.masterdata.mapper.CustomerSegmentMapper;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.CustomerServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 客户公海池 / 跟进预警 / 标签管理 / 分群 / 防造假
 * 来源：2026-07-01 客户录音访谈需求
 */
@Slf4j
@RestController
@RequestMapping("/api/customer/pool")
@RequiredArgsConstructor
public class CustomerPoolController {

    private final CustomerService customerService;
    private final CustomerServiceImpl customerServiceImpl;
    private final CustomerPoolConfigMapper poolConfigMapper;
    private final CustomerWarningConfigMapper warningConfigMapper;
    private final CustomerTagDictMapper tagDictMapper;
    private final CustomerSegmentMapper segmentMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DataScopeResolver dataScopeResolver;

    // ===========================================================================
    // 一、公海池管理
    // ===========================================================================

    /** 公海池列表 */
    @GetMapping("/list")
    public Result<PageResult<Customer>> poolList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        IPage<Customer> p = customerService.poolFindPage(page, size, keyword);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), (int) p.getCurrent(), (int) p.getSize()));
    }

    /** 领取公海客户（从JWT取当前用户ID，禁止前端传userId越权） */
    @PostMapping("/{id}/claim")
    public Result<Void> claim(@PathVariable Long id,
                              jakarta.servlet.http.HttpServletRequest req) {
        Long currentUserId = (Long) req.getAttribute("currentUserId");
        if (currentUserId == null) return Result.error("未登录");
        boolean ok = customerService.claimFromPool(id, currentUserId);
        return ok ? Result.success() : Result.error("领取失败：客户不存在或不在公海");
    }

    /** 释放客户到公海（销售员只能释放自己私海客户；数据范围=全部数据的用户可释放任意） */
    @PostMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id,
                                @RequestBody(required = false) Map<String, String> body,
                                jakarta.servlet.http.HttpServletRequest req) {
        Long currentUserId = (Long) req.getAttribute("currentUserId");
        if (currentUserId == null) return Result.error("未登录");
        // 校验权限（2026-09-02 改为角色 data_scope 配置驱动）：数据范围=全部 或 客户归属人才能释放
        Customer c = customerService.getById(id);
        if (c == null) return Result.error("客户不存在");
        DataScopeResolver.DataScopeContext ctx = dataScopeResolver.resolve(req);
        if (!ctx.isAll()
                && !currentUserId.equals(c.getOwnerUserId())) {
            return Result.error("无权释放：仅可释放自己负责的客户");
        }
        String reason = body != null ? body.get("reason") : null;
        boolean ok = customerService.releaseToPool(id, reason);
        return ok ? Result.success() : Result.error("释放失败：客户不存在");
    }

    /** 批量分配客户给销售员 */
    @PostMapping("/batch-assign")
    public Result<Map<String, Object>> batchAssign(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("customerIds");
        Long salesId = body.get("salesId") != null ? Long.valueOf(body.get("salesId").toString()) : null;
        if (ids == null || ids.isEmpty() || salesId == null) {
            return Result.error("参数错误：customerIds 和 salesId 必填");
        }
        List<Long> customerIds = ids.stream().map(Number::longValue).toList();
        int n = customerService.batchAssign(customerIds, salesId);
        return Result.success(Map.of("success", n));
    }

    /** 离职人员客户转移 */
    @PostMapping("/transfer-by-resignation")
    public Result<Map<String, Object>> transferByResignation(@RequestBody Map<String, Object> body) {
        Long fromSalesId = body.get("fromSalesId") != null ? Long.valueOf(body.get("fromSalesId").toString()) : null;
        Long toSalesId = body.get("toSalesId") != null ? Long.valueOf(body.get("toSalesId").toString()) : null;
        Boolean toPool = body.get("toPool") != null ? Boolean.valueOf(body.get("toPool").toString()) : false;
        if (fromSalesId == null) return Result.error("参数错误：fromSalesId 必填");
        if (!toPool && toSalesId == null) return Result.error("参数错误：toSalesId 必填（除非 toPool=true）");
        int n = customerService.transferByResignation(fromSalesId, toSalesId, toPool);
        return Result.success(Map.of("transferred", n));
    }

    /** 切换置顶 */
    @PostMapping("/{id}/toggle-top")
    public Result<Void> toggleTop(@PathVariable Long id,
                                  @RequestBody Map<String, Integer> body) {
        Integer isTop = body.get("isTop");
        boolean ok = customerService.toggleTop(id, isTop);
        return ok ? Result.success() : Result.error("操作失败");
    }

    /** 手动触发公海池自动回收 */
    @PostMapping("/auto-recycle")
    public Result<Map<String, Object>> triggerAutoRecycle() {
        int n = customerService.autoRecycleToPool();
        return Result.success(Map.of("recycled", n));
    }

    // ===========================================================================
    // 二、公海池配置管理
    // ===========================================================================

    @GetMapping("/config")
    public Result<List<CustomerPoolConfig>> listConfig() {
        return Result.success(poolConfigMapper.selectList(new QueryWrapper<CustomerPoolConfig>().orderByDesc("config_id")));
    }

    @PostMapping("/config")
    public Result<CustomerPoolConfig> saveConfig(@RequestBody CustomerPoolConfig config) {
        if (config.getNoFollowDays() == null || config.getNoFollowDays() <= 0) {
            return Result.error("未跟进天数必须大于0");
        }
        if (config.getNoOrderDays() == null || config.getNoOrderDays() <= 0) {
            return Result.error("未开单天数必须大于0");
        }
        if (config.getIsEnabled() == null) config.setIsEnabled(1);
        if (config.getConfigId() == null) {
            poolConfigMapper.insert(config);
        } else {
            poolConfigMapper.updateById(config);
        }
        return Result.success(config);
    }

    @DeleteMapping("/config/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        poolConfigMapper.deleteById(id);
        return Result.success();
    }

    /** 获取当前生效的公海池配置 */
    @GetMapping("/config/active")
    public Result<CustomerPoolConfig> getActiveConfig() {
        return Result.success(customerServiceImpl.getActivePoolConfig());
    }

    // ===========================================================================
    // 三、客户跟进预警
    // ===========================================================================

    @GetMapping("/warning/list")
    public Result<List<Customer>> warningList() {
        return Result.success(customerService.checkFollowUpWarning());
    }

    @GetMapping("/warning/config")
    public Result<List<CustomerWarningConfig>> listWarningConfig() {
        return Result.success(warningConfigMapper.selectList(new QueryWrapper<CustomerWarningConfig>().orderByAsc("warning_type")));
    }

    @PostMapping("/warning/config")
    public Result<CustomerWarningConfig> saveWarningConfig(@RequestBody CustomerWarningConfig config) {
        if (config.getThresholdDays() == null || config.getThresholdDays() <= 0) {
            return Result.error("阈值天数必须大于0");
        }
        if (config.getIsEnabled() == null) config.setIsEnabled(1);
        // 按 warningType upsert：避免同一类型产生多条记录导致 selectOne 报错
        if (config.getWarningType() != null && !config.getWarningType().isBlank()) {
            QueryWrapper<CustomerWarningConfig> eq = new QueryWrapper<>();
            eq.eq("warning_type", config.getWarningType());
            CustomerWarningConfig existing = warningConfigMapper.selectOne(eq);
            if (existing != null) {
                config.setWarningId(existing.getWarningId());
            }
        }
        if (config.getWarningId() == null) {
            warningConfigMapper.insert(config);
        } else {
            warningConfigMapper.updateById(config);
        }
        return Result.success(config);
    }

    @DeleteMapping("/warning/config/{id}")
    public Result<Void> deleteWarningConfig(@PathVariable Long id) {
        warningConfigMapper.deleteById(id);
        return Result.success();
    }

    // ===========================================================================
    // 四、客户标签字典管理
    // ===========================================================================

    @GetMapping("/tag-dict")
    public Result<List<CustomerTagDict>> listTagDict(
            @RequestParam(required = false) String category) {
        QueryWrapper<CustomerTagDict> qw = new QueryWrapper<>();
        qw.eq("is_enabled", 1);
        if (category != null && !category.isBlank()) {
            qw.eq("tag_category", category);
        }
        qw.orderByAsc("tag_category", "tag_id");
        return Result.success(tagDictMapper.selectList(qw));
    }

    @PostMapping("/tag-dict")
    public Result<CustomerTagDict> saveTagDict(@RequestBody CustomerTagDict tag) {
        if (tag.getTagName() == null || tag.getTagName().isBlank()) {
            return Result.error("标签名称不能为空");
        }
        if (tag.getIsEnabled() == null) tag.setIsEnabled(1);
        if (tag.getTagId() == null) {
            tagDictMapper.insert(tag);
        } else {
            tagDictMapper.updateById(tag);
        }
        return Result.success(tag);
    }

    @DeleteMapping("/tag-dict/{id}")
    public Result<Void> deleteTagDict(@PathVariable Long id) {
        tagDictMapper.deleteById(id);
        return Result.success();
    }

    // ===========================================================================
    // 五、客户分群
    // ===========================================================================

    @GetMapping("/segment")
    public Result<List<CustomerSegment>> listSegment() {
        return Result.success(segmentMapper.selectList(new QueryWrapper<CustomerSegment>()
                .orderByAsc("created_at is null").orderByDesc("created_at")));
    }

    @PostMapping("/segment")
    public Result<CustomerSegment> saveSegment(@RequestBody CustomerSegment segment) {
        if (segment.getSegmentName() == null || segment.getSegmentName().isBlank()) {
            return Result.error("分群名称不能为空");
        }
        if (segment.getRuleJson() == null || segment.getRuleJson().isBlank()) {
            return Result.error("分群规则不能为空");
        }
        if (segment.getIsDynamic() == null) segment.setIsDynamic(1);
        if (segment.getSegmentId() == null) {
            segmentMapper.insert(segment);
        } else {
            segmentMapper.updateById(segment);
        }
        return Result.success(segment);
    }

    @DeleteMapping("/segment/{id}")
    public Result<Void> deleteSegment(@PathVariable Long id) {
        segmentMapper.deleteById(id);
        return Result.success();
    }

    // ===========================================================================
    // 六、客户防造假（重复检测）
    // ===========================================================================

    @GetMapping("/check-duplicate")
    public Result<List<Customer>> checkDuplicate(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String taxNo,
            @RequestParam(required = false) String address) {
        return Result.success(customerService.checkDuplicate(phone, taxNo, address));
    }
}
