package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.produce.assemble.entity.ProduceAssemble;
import com.erp.produce.assemble.entity.ProduceAssembleDetail;
import com.erp.produce.assemble.mapper.ProduceAssembleDetailMapper;
import com.erp.produce.assemble.service.ProduceAssembleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产组装单「成品入库明细」行适配器（2026-09-29 通用插件适配：生产模块第 4 种单据）。
 *
 * <p>本单据有两张明细表：{@code produce_assemble_detail}（成品入库明细）与
 * {@code produce_assemble_bom}（原料出库 BOM 明细）。本适配器**只暴露成品入库明细**，
 * 原料 BOM 明细保持只读（理由见下方"双明细表口径"）。
 *
 * <p>保存链路 = {@code ProduceAssembleService.saveWithDetails(assemble)} —— 与组装单编辑页点"保存"完全同一条路：
 * 重算明细金额（数量×单价）与表头金额/税额/本位币金额，整单按 assemble_id 先删旧明细再重插。
 *
 * <p><b>双明细表口径（关键，勿省）</b>：该整单保存接口从主表实体的两个子集合
 * （{@code produceAssembleDetail} / {@code produceAssembleBom}）取数，编辑态会**同时删掉两张表**再重插。
 * 因此 {@link #loadBill} 必须取整单详情（两张明细都带），保存时只替换"成品明细"集合 ——
 * 若不带 BOM 集合，BOM 原料明细会被删掉且不重插（数据丢失）。BOM 明细本批不开放修改。
 *
 * <p><b>可改字段 2 个</b>：数量、单价。金额/税额/本位币金额为派生列（保存链路重算）；
 * 主单位数量在界面为只读列（由选料时的单位换算带出），故不开放。该表无折扣率列。
 *
 * <p>明细主键 = {@code id}（列 id）；行序字段 = {@code sortOrder}（列 sort_order，保存时按 1..N 重排）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProduceAssembleDetailItemHandler implements BillItemHandler {

    private final ProduceAssembleService produceAssembleService;
    private final ProduceAssembleDetailMapper produceAssembleDetailMapper;

    @Override
    public String billType() {
        return "PRODUCE_ASSEMBLE";
    }

    @Override
    public String itemLabel() {
        return "生产组装单成品明细";
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
        // 取整单详情：必须同时带成品明细与 BOM 原料明细（保存链路会先删两张表再重插，缺一即丢数据）
        return produceAssembleService.getDetail(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return produceAssembleDetailMapper.selectList(new QueryWrapper<ProduceAssembleDetail>()
                .eq("assemble_id", billId)
                .orderByAsc("sort_order"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof ProduceAssemble a ? a.getSerialNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        ProduceAssemble a = (ProduceAssemble) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", a.getSerialNo());
        m.put("状态", statusText(a.getStatus()));
        m.put("合计金额", a.getAmount());
        m.put("税额", a.getTaxAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof ProduceAssembleDetail it ? it.getId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof ProduceAssembleDetail it ? it.getSortOrder() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        ProduceAssembleDetail it = (ProduceAssembleDetail) item;
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
        ProduceAssembleDetail it = (ProduceAssembleDetail) item;
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
        ProduceAssemble assemble = (ProduceAssemble) bill;
        // 只替换"成品明细"集合；BOM 原料明细来自 loadBill 的整单详情，原样带回（避免被删后不重插）
        assemble.setProduceAssembleDetail((List<ProduceAssembleDetail>) items);
        produceAssembleService.saveWithDetails(assemble);
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
