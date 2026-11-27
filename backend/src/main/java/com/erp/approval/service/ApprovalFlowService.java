package com.erp.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.approval.entity.*;
import com.erp.approval.mapper.*;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审批流程管理服务 - 流程定义/节点/审批人/条件 CRUD
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalFlowService extends ServiceImpl<ApprovalFlowMapper, ApprovalFlow> {

    private final ApprovalNodeMapper nodeMapper;
    private final ApprovalNodeApproverMapper approverMapper;
    private final ApprovalConditionMapper conditionMapper;
    private final ApprovalTaskMapper taskMapper;
    private final ApprovalLogMapper logMapper;
    private final SysSerialNoConfigService serialNoService;

    /** 获取流程完整详情（含节点、审批人、条件） */
    public ApprovalFlow getFlowDetail(Long flowId) {
        ApprovalFlow flow = getById(flowId);
        if (flow == null) throw new BusinessException("流程不存在");
        loadFlowChildren(flow);
        return flow;
    }

    /** 按 billType 获取启用流程（支持条件路由：多流程时按 priority + conditionExpr 匹配） */
    public ApprovalFlow getEnabledFlowByBillType(String billType) {
        return getEnabledFlowByBillType(billType, null);
    }

    /**
     * 按 billType + 单据数据获取启用流程
     * 多流程时按 priority 排序，依次匹配 conditionExpr，无条件的兜底
     */
    public ApprovalFlow getEnabledFlowByBillType(String billType, Map<String, Object> billData) {
        // 排序必须带 flow_id 兜底：priority 相同的多条流程在 PG 下返回顺序不确定，
        // 曾导致单据被随机派到历史脏流程（详见 db/76_approval_dirty_flow_cleanup.sql），
        // 固定为 priority asc, flow_id asc 后，同配置下的选流结果可复现。
        List<ApprovalFlow> flows = list(new QueryWrapper<ApprovalFlow>()
                .eq("bill_type", billType).eq("enabled", 1)
                .orderByAsc("priority").orderByAsc("flow_id"));
        if (flows.isEmpty()) return null;

        // 如果有单据数据，尝试条件匹配
        if (billData != null && !billData.isEmpty()) {
            for (ApprovalFlow flow : flows) {
                if (flow.getConditionExpr() != null && !flow.getConditionExpr().isBlank()) {
                    if (matchCondition(flow.getConditionExpr(), billData)) {
                        loadFlowChildren(flow);
                        return flow;
                    }
                }
            }
        }

        // 兜底：取第一个没有条件表达式的流程（flows 已按 priority asc, flow_id asc 固定排序）
        List<ApprovalFlow> fallbacks = flows.stream()
                .filter(f -> f.getConditionExpr() == null || f.getConditionExpr().isBlank())
                .collect(Collectors.toList());
        if (fallbacks.size() > 1) {
            log.warn("[审批流程] 单据类型 {} 存在 {} 条无条件兜底流程，按 flow_id 最小者命中：{}；"
                            + "建议在设置中心-审批流程管理停用多余流程，或为其配置触发条件",
                    billType, fallbacks.size(), fallbacks.get(0).getFlowCode());
        }
        ApprovalFlow flow = fallbacks.isEmpty() ? flows.get(0) : fallbacks.get(0);
        loadFlowChildren(flow);
        return flow;
    }

    /**
     * 同一单据类型下，启用中的「无条件兜底流程」只允许存在一条。
     * 多条兜底流程并存时选流结果依赖排序，历史上曾导致单据被派到错误流程
     * （详见 db/76_approval_dirty_flow_cleanup.sql），因此在保存入口直接拦截。
     */
    private void validateFallbackFlowUnique(ApprovalFlow flow) {
        if (flow.getBillType() == null || flow.getBillType().isBlank()) return;
        Short enabled = flow.getEnabled();
        if (enabled == null && flow.getFlowId() != null) {
            ApprovalFlow db = getById(flow.getFlowId());
            enabled = db == null ? null : db.getEnabled();
        }
        // 未启用的流程不参与选流，允许多条并存（如草稿期备选流程）
        if (enabled != null && enabled == 0) return;
        // 有条件表达式的流程不参与兜底，可与兜底流程共存
        if (flow.getConditionExpr() != null && !flow.getConditionExpr().isBlank()) return;

        List<ApprovalFlow> sameTypeEnabled = list(new QueryWrapper<ApprovalFlow>()
                .eq("bill_type", flow.getBillType()).eq("enabled", 1));
        for (ApprovalFlow other : sameTypeEnabled) {
            if (flow.getFlowId() != null && flow.getFlowId().equals(other.getFlowId())) continue;
            if (other.getConditionExpr() == null || other.getConditionExpr().isBlank()) {
                throw new BusinessException("单据类型「" + flow.getBillType() + "」已存在无条件兜底流程「"
                        + other.getFlowName() + "」（编码 " + other.getFlowCode() + "）。"
                        + "同一单据类型只允许一条兜底流程，请先停用/删除其中一条，或为本流程设置触发条件");
            }
        }
    }

    /**
     * 条件表达式匹配（两种JSON格式，AND 关系，全部满足才返回 true）
     *
     * 新格式（结构化规则，前端可视化配置生成）：
     *   {"rules":[{"field":"contactPerson","op":"eq","value":"张三"},
     *             {"field":"totalAmount","op":"ge","value":50000}]}
     *   op 支持：eq 等于 / ne 不等于 / contains 包含 / gt 大于 / ge 大于等于 / lt 小于 / le 小于等于
     *
     * 旧格式（兼容保留，已存在流程不受影响）：
     *   {"payment_category":"月结客户","min_amount":50000,"max_amount":100000}
     */
    @SuppressWarnings("unchecked")
    private boolean matchCondition(String conditionExpr, Map<String, Object> billData) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> conditions = mapper.readValue(conditionExpr, Map.class);
            // ---- 新格式：rules 结构 ----
            Object rulesObj = conditions.get("rules");
            if (rulesObj instanceof List<?> rules) {
                for (Object r : rules) {
                    if (!(r instanceof Map)) return false;
                    Map<String, Object> rule = (Map<String, Object>) r;
                    String field = String.valueOf(rule.get("field"));
                    String op = String.valueOf(rule.get("op") == null ? "eq" : rule.get("op"));
                    Object expected = rule.get("value");
                    Object actual = billData.get(field);
                    if (!matchSingleRule(op, actual, expected)) return false;
                }
                return true;
            }
            // ---- 旧格式：key=value（min_amount/max_amount 特殊语义，其余等值比较） ----
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                String key = entry.getKey();
                Object expected = entry.getValue();
                Object actual = billData.get(key);
                if (actual == null) return false;
                if ("min_amount".equals(key)) {
                    BigDecimal actualAmount = new BigDecimal(actual.toString());
                    BigDecimal expectedAmount = new BigDecimal(expected.toString());
                    if (actualAmount.compareTo(expectedAmount) < 0) return false;
                } else if ("max_amount".equals(key)) {
                    BigDecimal actualAmount = new BigDecimal(actual.toString());
                    BigDecimal expectedAmount = new BigDecimal(expected.toString());
                    if (actualAmount.compareTo(expectedAmount) > 0) return false;
                } else {
                    if (!expected.toString().equals(actual.toString())) return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("条件表达式匹配失败: {}", conditionExpr, e);
            return false;
        }
    }

    /** 单条规则匹配：actual 为 null 时仅 ne 运算符成立 */
    private boolean matchSingleRule(String op, Object actual, Object expected) {
        switch (op) {
            case "eq":
                if (actual == null || expected == null) return actual == expected;
                return compareValue(actual, expected) == 0;
            case "ne":
                if (actual == null || expected == null) return actual != expected;
                return compareValue(actual, expected) != 0;
            case "contains":
                if (actual == null || expected == null) return false;
                return actual.toString().contains(expected.toString());
            case "gt": return compareNullable(actual, expected) > 0;
            case "ge": return compareNullable(actual, expected) >= 0;
            case "lt": return compareNullable(actual, expected) < 0;
            case "le": return compareNullable(actual, expected) <= 0;
            default:
                log.warn("未知条件运算符: {}，按不匹配处理", op);
                return false;
        }
    }

    /** 数字按数值比较，其余按字符串比较；返回负数/0/正数 */
    private int compareValue(Object actual, Object expected) {
        BigDecimal a = toBigDecimal(actual);
        BigDecimal b = toBigDecimal(expected);
        if (a != null && b != null) return a.compareTo(b);
        return actual.toString().compareTo(expected.toString());
    }

    private int compareNullable(Object actual, Object expected) {
        if (actual == null || expected == null) return -1; // 空值不满足大小比较
        return compareValue(actual, expected);
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal d) return d;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private void loadFlowChildren(ApprovalFlow flow) {
        List<ApprovalNode> nodes = nodeMapper.selectList(
                new QueryWrapper<ApprovalNode>().eq("flow_id", flow.getFlowId()).orderByAsc("node_order"));
        if (!nodes.isEmpty()) {
            List<Long> nodeIds = nodes.stream().map(ApprovalNode::getNodeId).collect(Collectors.toList());
            List<ApprovalNodeApprover> approvers = approverMapper.selectList(
                    new QueryWrapper<ApprovalNodeApprover>().in("node_id", nodeIds));
            Map<Long, List<ApprovalNodeApprover>> approverMap = approvers.stream()
                    .collect(Collectors.groupingBy(ApprovalNodeApprover::getNodeId));
            nodes.forEach(n -> {
                n.setApprovers(approverMap.getOrDefault(n.getNodeId(), Collections.emptyList()));
                n.setApproverCount(n.getApprovers().size());
            });
        }
        flow.setNodes(nodes);
        flow.setNodeCount(nodes.size());
    }

    /** 列表查询 */
    public List<ApprovalFlow> listFlows(String billType, String keyword) {
        QueryWrapper<ApprovalFlow> qw = new QueryWrapper<>();
        if (billType != null && !billType.isBlank()) qw.eq("bill_type", billType);
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("flow_code", keyword).or().like("flow_name", keyword));
        }
        qw.orderByDesc("create_time");
        return list(qw);
    }

    /** 新建流程（含节点和审批人） */
    @Transactional
    public void saveFlow(ApprovalFlow flow) {
        if (flow.getFlowCode() == null || flow.getFlowCode().isEmpty()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("WF_APPROVAL_FLOW")) {
                throw new BusinessException("审批流程已关闭自动编号，请手动输入编码");
            }
            // generateSerialNo 带业务表防重抬底：手动录码/停用规则期间产生的流程编码会以业务表真实流水续号，杜绝重号
            flow.setFlowCode(serialNoService.generateSerialNo("WF_APPROVAL_FLOW", prefix -> {
                if (prefix == null || prefix.isBlank()) {
                    return null;
                }
                ApprovalFlow last = getOne(new QueryWrapper<ApprovalFlow>().likeRight("flow_code", prefix)
                        .orderByDesc("flow_code").last("LIMIT 1"));
                if (last == null || last.getFlowCode() == null) {
                    return null;
                }
                try {
                    return prefix + (Integer.parseInt(last.getFlowCode().substring(prefix.length())) + 1);
                } catch (Exception e) {
                    return null;
                }
            }));
        }
        if (baseMapper.exists(new QueryWrapper<ApprovalFlow>().eq("flow_code", flow.getFlowCode()))) {
            throw new BusinessException("流程编码已存在: " + flow.getFlowCode());
        }
        if (flow.getEnabled() == null) flow.setEnabled((short) 1);
        validateFallbackFlowUnique(flow);
        save(flow);
        if (flow.getNodes() != null) {
            for (ApprovalNode node : flow.getNodes()) {
                node.setFlowId(flow.getFlowId());
                node.setNodeId(null);
                if (node.getNodeOrder() == null) node.setNodeOrder(1);
                if (node.getApproverMode() == null) node.setApproverMode("single");
                nodeMapper.insert(node);
                saveApprovers(node);
            }
        }
        log.info("新建审批流程: {}", flow.getFlowCode());
    }

    /** 更新流程（含节点和审批人 - 删除重建） */
    @Transactional
    public void updateFlow(ApprovalFlow flow) {
        if (flow.getFlowCode() != null && !flow.getFlowCode().isEmpty()
                && flow.getFlowId() != null) {
            if (baseMapper.exists(new QueryWrapper<ApprovalFlow>()
                    .eq("flow_code", flow.getFlowCode()).ne("flow_id", flow.getFlowId()))) {
                throw new BusinessException("流程编码已存在: " + flow.getFlowCode());
            }
        }
        // 删除旧节点和审批人
        List<ApprovalNode> oldNodes = nodeMapper.selectList(
                new QueryWrapper<ApprovalNode>().eq("flow_id", flow.getFlowId()));
        for (ApprovalNode old : oldNodes) {
            approverMapper.delete(new QueryWrapper<ApprovalNodeApprover>().eq("node_id", old.getNodeId()));
        }
        nodeMapper.delete(new QueryWrapper<ApprovalNode>().eq("flow_id", flow.getFlowId()));
        // 注意：条件模板(approval_condition)不在更新时删除——条件按 targetNodeOrder 关联节点序号，
        // 由「条件模板」独立入口维护；此前在此删除会导致编辑流程后条件全部丢失（重新打开变空白）

        validateFallbackFlowUnique(flow);
        updateById(flow);
        if (flow.getNodes() != null) {
            for (ApprovalNode node : flow.getNodes()) {
                node.setNodeId(null);
                node.setFlowId(flow.getFlowId());
                if (node.getNodeOrder() == null) node.setNodeOrder(1);
                if (node.getApproverMode() == null) node.setApproverMode("single");
                nodeMapper.insert(node);
                saveApprovers(node);
            }
        }
        log.info("更新审批流程: {}", flow.getFlowCode());
    }

    private void saveApprovers(ApprovalNode node) {
        if (node.getApprovers() == null) return;
        for (ApprovalNodeApprover a : node.getApprovers()) {
            a.setId(null);
            a.setNodeId(node.getNodeId());
            approverMapper.insert(a);
        }
    }

    /** 删除流程（级联删除节点和审批人） */
    @Transactional
    public void deleteFlow(Long flowId) {
        List<ApprovalNode> nodes = nodeMapper.selectList(
                new QueryWrapper<ApprovalNode>().eq("flow_id", flowId));
        for (ApprovalNode n : nodes) {
            approverMapper.delete(new QueryWrapper<ApprovalNodeApprover>().eq("node_id", n.getNodeId()));
        }
        nodeMapper.delete(new QueryWrapper<ApprovalNode>().eq("flow_id", flowId));
        conditionMapper.delete(new QueryWrapper<ApprovalCondition>().eq("flow_id", flowId));
        removeById(flowId);
        log.info("删除审批流程: {}", flowId);
    }

    /** 设置启用状态 */
    @Transactional
    public void setEnabled(Long flowId, Short enabled) {
        ApprovalFlow flow = getById(flowId);
        if (flow == null) throw new BusinessException("流程不存在");
        flow.setEnabled(enabled);
        updateById(flow);
    }

    /** 保存条件模板 */
    @Transactional
    public void saveConditions(Long flowId, List<ApprovalCondition> conditions) {
        conditionMapper.delete(new QueryWrapper<ApprovalCondition>().eq("flow_id", flowId));
        if (conditions == null) return;
        for (ApprovalCondition c : conditions) {
            c.setConditionId(null);
            c.setFlowId(flowId);
            conditionMapper.insert(c);
        }
    }

    /** 获取条件列表 */
    public List<ApprovalCondition> getConditions(Long flowId) {
        return conditionMapper.selectList(
                new QueryWrapper<ApprovalCondition>().eq("flow_id", flowId).orderByAsc("condition_id"));
    }

    /** 获取单据审批历史 */
    public List<ApprovalLog> getBillApprovalHistory(String billType, Long billId) {
        return logMapper.selectList(new QueryWrapper<ApprovalLog>()
                .eq("bill_type", billType).eq("bill_id", billId).orderByAsc("action_time"));
    }
}
