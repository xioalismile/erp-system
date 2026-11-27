package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.produce.assemble.entity.ProduceDisassemble;
import com.erp.produce.assemble.entity.ProduceDisassembleDetail;
import com.erp.produce.assemble.mapper.ProduceDisassembleDetailMapper;
import com.erp.produce.assemble.service.ProduceDisassembleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产拆卸单「成品出库明细」行适配器（2026-09-29 通用插件适配：生产模块第 5 种单据）。
 *
 * <p>本单据有两张明细表：{@code produce_disassemble_detail}（成品出库明细）与
 * {@code produce_disassemble_bom}（BOM 明细）。本适配器**只暴露成品出库明细**，
 * BOM 明细保持只读（理由同组装单：整单保存会同时删两张表再重插，缺 BOM 集合即丢数据）。
 *
 * <p>保存链路 = {@code ProduceDisassembleService.saveWithDetails(disassemble)} —— 与拆卸单编辑页点"保存"完全同一条路：
 * 重算明细金额（数量×单价）与表头金额/税额/本位币金额，整单按 disassemble_id 先删旧明细再重插。
 *
 * <p><b>可改字段 2 个</b>：数量、单价。金额/税额/本位币金额为派生列（保存链路重算）；
 * 主单位数量在界面为只读列（由选料时的单位换算带出），故不开放。该表无折扣率列。
 *
 * <p>明细主键 = {@code id}（列 id）；行序字段 = {@code sortOrder}（列 sort_order，保存时按 1..N 重排）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProduceDisassembleDetailItemHandler implements BillItemHandler {

    private final ProduceDisassembleService produceDisassembleService;
    private final ProduceDisassembleDetailMapper produceDisassembleDetailMapper;

    @Override
    public String billType() {
        return "PRODUCE_DISASSEMBLE";
    }

    @Override
    public String itemLabel() {
        return "生产拆卸单成品明细";
    }

    @Override
    public String itemIdField() {
        return "id";
    }

    @Override
    public String lineNoField() {
        return "sortOrder";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity", "price");
    }

    @Override
    public Object loadBill(Long billId) {
        // 取整单详情：必须同时带成品明细与 BOM 明细（保存链路会先删两张表再重插，缺一即丢数据）
        return produceDisassembleService.getDetail(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return produceDisassembleDetailMapper.selectList(new QueryWrapper<ProduceDisassembleDetail>()
                .eq("disassemble_id", billId)
                .orderByAsc("sort_order"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof ProduceDisassemble d ? d.getSerialNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        ProduceDisassemble d = (ProduceDisassemble) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", d.getSerialNo());
        m.put("状态", statusText(d.getStatus()));
        m.put("合计金额", d.getAmount());
        m.put("税额", d.getTaxAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof ProduceDisassembleDetail it ? it.getId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof ProduceDisassembleDetail it ? it.getSortOrder() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        ProduceDisassembleDetail it = (ProduceDisassembleDetail) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getSortOrder() != null ? it.getSortOrder() : fallbackLineNo);
        m.put("明细行ID", it.getId());
        m.put("物料编码", it.getProductCode());
        m.put("物料名称", it.getProductName());
        m.put("规格型号", it.getSpecification());
        m.put("数量", it.getQuantity());
        m.put("主单位数量", it.getBasicQuantity());
        m.put("单价", it.getPrice());
        m.put("金额", it.getAmount());
        m.put("仓库", it.getWhName());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        ProduceDisassembleDetail it = (ProduceDisassembleDetail) item;
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
        if (patch.containsKey("price")) {
            it.setPrice(toDecimal(patch.get("price"), "单价"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        ProduceDisassemble disassemble = (ProduceDisassemble) bill;
        // 只替换"成品明细"集合；BOM 明细来自 loadBill 的整单详情，原样带回（避免被删后不重插）
        disassemble.setProduceDisassembleDetail((List<ProduceDisassembleDetail>) items);
        produceDisassembleService.saveWithDetails(disassemble);
    }

    /** 单据状态：本单据 status 是数字编码 */
    private String statusText(Integer status) {
        if (status == null) return null;
        return switch (status) {
            case 0 -> "草稿";
            case 1 -> "待审批";
            case 2 -> "已生效";
            case 7 -> "已作废";
            default -> "未知(" + status + ")";
        };
    }

    /** 数值解析：允许 0，不允许负数/空/非数字（金额类不在这里改，交给保存链路重算） */
    private BigDecimal toDecimal(Object v, String fieldCn) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException(fieldCn + "不能为空");
        }
        BigDecimal d;
        try {
            d = new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldCn + "必须是数字，收到：" + v);
        }
        if (d.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldCn + "不能为负数，收到：" + v);
        }
        return d;
    }
}
