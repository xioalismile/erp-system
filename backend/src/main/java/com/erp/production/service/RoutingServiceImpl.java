package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.production.entity.Routing;
import com.erp.production.entity.RoutingOperation;
import com.erp.production.mapper.RoutingMapper;
import com.erp.production.mapper.RoutingOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingServiceImpl extends ServiceImpl<RoutingMapper, Routing> implements RoutingService {

    private final RoutingOperationMapper operationMapper;

    @Override
    @Transactional
    public void saveRoutingWithOperations(Routing routing, List<RoutingOperation> operations) {
        boolean isNew = routing.getRoutingId() == null;

        if (routing.getRoutingCode() == null || routing.getRoutingCode().isBlank()) {
            routing.setRoutingCode(generateRoutingCode());
        }
        if (routing.getStatus() == null) routing.setStatus("已审核");
        if (routing.getApprovalStatus() == null) routing.setApprovalStatus("待审核");
        if (routing.getIsPrimary() == null) routing.setIsPrimary((short) 1);
        if (routing.getIsEnabled() == null) routing.setIsEnabled((short) 1);
        if (routing.getTotalWorkHours() == null) routing.setTotalWorkHours(BigDecimal.ZERO);

        recalculateRouting(routing, operations);

        if (isNew) {
            save(routing);
        } else {
            // 编辑: 先按 routing_id 删除旧明细, 再更新主单
            operationMapper.delete(new QueryWrapper<RoutingOperation>().eq("routing_id", routing.getRoutingId()));
            updateById(routing);
        }

        if (operations != null && !operations.isEmpty()) {
            int lineNo = 10;
            for (RoutingOperation op : operations) {
                op.setOperationId(null);
                op.setRoutingId(routing.getRoutingId());
                op.setLineNo(lineNo);
                if (op.getBatchSize() == null) op.setBatchSize(1);
                if (op.getWorkersRequired() == null) op.setWorkersRequired(1);
                if (op.getIsQcRequired() == null) op.setIsQcRequired((short) 0);
                if (op.getIsCritical() == null) op.setIsCritical((short) 0);
                calculateOperationHours(op);
                operationMapper.insert(op);
                lineNo += 10;
            }
        }

        log.info("工艺路线{}: {} 工序明细{}项",
                isNew ? "新增" : "编辑", routing.getRoutingCode(),
                operations != null ? operations.size() : 0);
    }

    /** 计算单行工序总工时: setup + process + wait + move */
    private void calculateOperationHours(RoutingOperation op) {
        BigDecimal setup = op.getSetupHours() != null ? op.getSetupHours() : BigDecimal.ZERO;
        BigDecimal process = op.getProcessHours() != null ? op.getProcessHours() : BigDecimal.ZERO;
        BigDecimal wait = op.getWaitHours() != null ? op.getWaitHours() : BigDecimal.ZERO;
        BigDecimal move = op.getMoveHours() != null ? op.getMoveHours() : BigDecimal.ZERO;
        op.setSetupHours(setup);
        op.setProcessHours(process);
        op.setWaitHours(wait);
        op.setMoveHours(move);
        op.setTotalHours(setup.add(process).add(wait).add(move));
    }

    /** 计算主单总工时: 各工序 totalHours 之和 */
    private void recalculateRouting(Routing routing, List<RoutingOperation> operations) {
        BigDecimal totalWorkHours = BigDecimal.ZERO;
        if (operations != null) {
            for (RoutingOperation op : operations) {
                calculateOperationHours(op);
                totalWorkHours = totalWorkHours.add(op.getTotalHours());
            }
        }
        routing.setTotalWorkHours(totalWorkHours);
    }

    @Override
    public PageResult<Routing> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<Routing> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("routing_code", keyword)
                    .or().like("routing_name", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        Page<Routing> page = new Page<>(current, size);
        Page<Routing> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public Routing getDetail(Long id) {
        Routing routing = getById(id);
        if (routing == null) throw new BusinessException("工艺路线不存在");
        List<RoutingOperation> operations = operationMapper.selectList(
                new QueryWrapper<RoutingOperation>().eq("routing_id", id).orderByAsc("line_no"));
        routing.setOperations(operations);
        return routing;
    }

    @Override
    public String generateRoutingCode() {
        String prefix = "RT" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<Routing> wrapper = new QueryWrapper<>();
        wrapper.likeRight("routing_code", prefix).orderByDesc("routing_code").last("LIMIT 1");
        Routing last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getRoutingCode() != null) {
            try {
                seq = Integer.parseInt(last.getRoutingCode().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
