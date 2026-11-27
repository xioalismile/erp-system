package com.erp.acquisition.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.acquisition.entity.AcquisitionTask;
import com.erp.acquisition.mapper.AcquisitionTaskMapper;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class AcquisitionTaskServiceImpl extends ServiceImpl<AcquisitionTaskMapper, AcquisitionTask>
        implements AcquisitionTaskService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "area",
            "captured_count",
            "create_date",
            "created_at",
            "creator_id",
            "cron_expression",
            "deleted",
            "error_message",
            "imported_count",
            "industry",
            "keywords",
            "last_run_time",
            "max_companies",
            "max_pages",
            "modifier_id",
            "modify_date",
            "next_run_time",
            "platform",
            "remark",
            "status",
            "task_id",
            "task_name",
            "task_no"
        );

    private final AcquisitionClientService acquisitionClientService;
    private final AcquisitionResultService acquisitionResultService;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    @Override
    public PageResult<AcquisitionTask> pageQuery(int current, int size, String keyword, String platform, String status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<AcquisitionTask> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("task_no", keyword)
                    .or().like("task_name", keyword));
        }
        if (platform != null && !platform.isBlank()) {
            wrapper.eq("platform", platform);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 数据权限（2026-09-06 补齐，与 dashboard 一致）
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) wrapper.orderByDesc("create_date");
        Page<AcquisitionTask> page = new Page<>(current, size);
        Page<AcquisitionTask> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public AcquisitionTask getDetail(Long id) {
        AcquisitionTask task = getById(id);
        if (task == null) throw new BusinessException("采集任务不存在");
        return task;
    }

    @Override
    @Transactional
    public AcquisitionTask createTask(AcquisitionTask task) {
        if (task.getTaskName() == null || task.getTaskName().isBlank()) {
            throw new BusinessException("任务名称不能为空");
        }
        if (task.getPlatform() == null || task.getPlatform().isBlank()) {
            throw new BusinessException("采集平台不能为空");
        }

        if (task.getTaskNo() == null || task.getTaskNo().isBlank()) {
            task.setTaskNo(generateTaskNo());
        }
        // 前端新增时 status 传空字符串，必须同时判空串，否则任务没有状态（列表状态列为空）
        if (task.getStatus() == null || task.getStatus().isBlank()) {
            task.setStatus("pending");
        }
        if (task.getMaxPages() == null) {
            task.setMaxPages(5);
        }
        if (task.getMaxCompanies() == null) {
            task.setMaxCompanies(50);
        }
        if (task.getCapturedCount() == null) {
            task.setCapturedCount(0);
        }
        if (task.getImportedCount() == null) {
            task.setImportedCount(0);
        }
        if (task.getDeleted() == null) {
            task.setDeleted(0);
        }

        task.setCreateDate(LocalDateTime.now());
        save(task);
        log.info("采集任务创建成功: {} 平台: {}", task.getTaskNo(), task.getPlatform());
        return task;
    }

    @Override
    @Transactional
    public void updateTask(AcquisitionTask task) {
        if (task.getTaskId() == null) {
            throw new BusinessException("任务ID不能为空");
        }
        task.setModifyDate(LocalDateTime.now());
        updateById(task);
        log.info("采集任务更新: ID={}", task.getTaskId());
    }

    @Override
    @Transactional
    public void deleteTask(Long id) {
        AcquisitionTask task = getById(id);
        if (task == null) throw new BusinessException("采集任务不存在");
        // 逻辑删除
        task.setDeleted(1);
        task.setModifyDate(LocalDateTime.now());
        updateById(task);
        log.info("采集任务删除: ID={}", id);
    }

    @Override
    @Transactional
    public void startTask(Long id) {
        AcquisitionTask task = getById(id);
        if (task == null) throw new BusinessException("采集任务不存在");
        if ("running".equals(task.getStatus())) {
            throw new BusinessException("任务已在运行中");
        }

        try {
            task.setStatus("running");
            task.setLastRunTime(LocalDateTime.now());
            task.setModifyDate(LocalDateTime.now());
            updateById(task);

            // 从当前 HTTP 请求中获取前端用户的 JWT token（用于采集服务定期校验用户登录态）
            // 如果用户登录过期（401），采集服务会自动停止采集任务
            String callerToken = extractCallerTokenFromRequest();

            // 调用外部独立采集服务触发采集
            // TODO: 实现具体采集逻辑，将返回的captureId保存到任务中以供后续状态查询
            acquisitionClientService.triggerCapture(task, callerToken);

            log.info("采集任务已启动: {}", task.getTaskNo());
        } catch (Exception e) {
            task.setStatus("failed");
            task.setErrorMessage(e.getMessage());
            task.setModifyDate(LocalDateTime.now());
            updateById(task);
            log.error("采集任务启动失败: {}", task.getTaskNo(), e);
            throw new BusinessException("采集任务启动失败: " + e.getMessage());
        }
    }

    /**
     * 从当前 HTTP 请求中提取前端用户的 Authorization Bearer token
     * 用于采集服务定期校验用户登录态，登录过期则停止采集任务
     */
    private String extractCallerTokenFromRequest() {
        try {
            jakarta.servlet.http.HttpServletRequest req = ((org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest();
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                return auth.substring(7);
            }
            return null;
        } catch (Exception e) {
            log.warn("提取 callerToken 失败（非 HTTP 请求上下文）: {}", e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public void pauseTask(Long id) {
        AcquisitionTask task = getById(id);
        if (task == null) throw new BusinessException("采集任务不存在");
        if (!"running".equals(task.getStatus())) {
            throw new BusinessException("仅运行中的任务可以暂停");
        }
        task.setStatus("paused");
        task.setModifyDate(LocalDateTime.now());
        updateById(task);
        log.info("采集任务已暂停: {}", task.getTaskNo());
    }

    @Override
    @Transactional
    public void updateStatus(Long taskId, String status, Integer capturedCount, String errorMessage) {
        AcquisitionTask task = getById(taskId);
        if (task == null) {
            log.warn("采集服务回调更新状态失败: 任务不存在 taskId={}, status={}", taskId, status);
            return;
        }
        // 校验状态值，只允许 running/completed/failed/paused
        if (status == null || !java.util.Set.of("running", "completed", "failed", "paused").contains(status)) {
            log.warn("采集服务回调更新状态失败: 非法状态值 taskId={}, status={}", taskId, status);
            return;
        }
        task.setStatus(status);
        if (capturedCount != null && capturedCount >= 0) {
            task.setCapturedCount(capturedCount);
        }
        if ("failed".equals(status) && errorMessage != null && !errorMessage.isBlank()) {
            task.setErrorMessage(errorMessage);
        }
        if ("completed".equals(status)) {
            // 完成时同步更新 importedCount = capturedCount（避免前端显示已采集/已导入不一致）
            // 注意：真实 importedCount 在导入 CRM 时再次更新
            if (task.getImportedCount() == null) {
                task.setImportedCount(0);
            }
        }
        task.setModifyDate(LocalDateTime.now());
        updateById(task);
        log.info("采集任务状态已更新: taskNo={}, status={}, capturedCount={}", task.getTaskNo(), status, capturedCount);
    }

    @Override
    @Transactional
    public void saveLoginCookie(Long taskId, String cookie) {
        AcquisitionTask task = getById(taskId);
        if (task == null) throw new BusinessException("采集任务不存在");
        if (cookie == null || cookie.isBlank()) {
            throw new BusinessException("Cookie不能为空");
        }
        task.setLoginCookie(cookie);
        task.setModifyDate(LocalDateTime.now());
        updateById(task);
        log.info("采集任务登录Cookie已保存: {}", task.getTaskNo());
    }

    @Override
    public String generateTaskNo() {
        String docType = "ACQUISITION_TASK";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "ACQ-" + dateStr + "-";
            QueryWrapper<AcquisitionTask> wrapper = new QueryWrapper<>();
            wrapper.likeRight("task_no", prefix).orderByDesc("task_no").last("LIMIT 1");
            AcquisitionTask last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getTaskNo() != null) {
                try {
                    seq = Integer.parseInt(last.getTaskNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }
}
