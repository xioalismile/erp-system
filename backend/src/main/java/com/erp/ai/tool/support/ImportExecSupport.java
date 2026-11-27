package com.erp.ai.tool.support;

import com.erp.export.dto.ImportResultDTO;
import com.erp.export.service.ExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 导入执行支持：把"已读入内存的文件"按与 {@code ExportController.importData} **完全一致**的
 * 分派逻辑交给 {@link ExcelService}（专用实体调对应 importXxx，customer+poolType 调 importDataWithPool，
 * 其余调 importData），并把 {@link ImportResultDTO} 汇总成人话。
 *
 * <p>铁律：不绕过后端既有校验/事务；一律调 ExcelService 既有方法；AI 不直接写库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportExecSupport {

    private final ExcelService excelService;

    /**
     * 按 ExportController.importData 的 if/else 分支逐条对齐执行导入。
     *
     * @param entityName  实体名（白名单已由调用方校验）
     * @param file        内存 MultipartFile
     * @param poolType    仅 customer 有效（0 私海 / 1 公海）；其它实体传 null
     * @param ownerUserId 私海导入归属用户（= 当前用户）；其它情况可为 null
     */
    public ImportResultDTO dispatch(String entityName, MultipartFile file, Integer poolType, Long ownerUserId) {
        ImportResultDTO result;
        if ("lead".equals(entityName)) {
            result = excelService.importLeadsWithImages(file);
        } else if ("material".equals(entityName)) {
            result = excelService.importMaterialsWithImages(file);
        } else if ("saleOrder".equals(entityName)) {
            result = excelService.importSaleOrders(file);
        } else if ("saleQuote".equals(entityName)) {
            result = excelService.importSaleQuotes(file);
        } else if ("saleContract".equals(entityName)) {
            result = excelService.importSaleContracts(file);
        } else if ("saleDelivery".equals(entityName)) {
            result = excelService.importSaleDeliveries(file);
        } else if ("saleReturn".equals(entityName)) {
            result = excelService.importSaleReturns(file);
        } else if ("saleInvoice".equals(entityName)) {
            result = excelService.importSaleInvoices(file);
        } else if ("purchaseRequest".equals(entityName)) {
            result = excelService.importPurchaseRequests(file);
        } else if ("purchaseOrder".equals(entityName)) {
            result = excelService.importPurchaseOrders(file);
        } else if ("purchaseContract".equals(entityName)) {
            result = excelService.importPurchaseContracts(file);
        } else if ("purchaseInbound".equals(entityName)) {
            result = excelService.importPurchaseInbounds(file);
        } else if ("purchaseReturn".equals(entityName)) {
            result = excelService.importPurchaseReturns(file);
        } else if ("purchaseInvoice".equals(entityName)) {
            result = excelService.importPurchaseInvoices(file);
        } else if ("otherReceipt".equals(entityName)) {
            result = excelService.importOtherReceipts(file);
        } else if ("otherIssue".equals(entityName)) {
            result = excelService.importOtherIssues(file);
        } else if ("stockTransfer".equals(entityName)) {
            result = excelService.importStockTransfers(file);
        } else if ("stockCheck".equals(entityName)) {
            result = excelService.importStockChecks(file);
        } else if ("materialRequisition".equals(entityName)) {
            result = excelService.importMaterialRequisitions(file);
        } else if ("paymentVoucher".equals(entityName)) {
            result = excelService.importPaymentVouchers(file);
        } else if ("paymentReceipt".equals(entityName)) {
            result = excelService.importPaymentReceipts(file);
        } else if ("purchasePrePayment".equals(entityName)) {
            result = excelService.importPurchasePrePayments(file);
        } else if ("salePreReceipt".equals(entityName)) {
            result = excelService.importSalePreReceipts(file);
        } else if ("operationExpense".equals(entityName)) {
            result = excelService.importOperationExpenses(file);
        } else if ("customer".equals(entityName) && poolType != null) {
            result = excelService.importDataWithPool(entityName, file, poolType, ownerUserId);
        } else {
            result = excelService.importData(entityName, file);
        }
        return result;
    }

    /**
     * 把 ImportResultDTO 汇总为 AI 可读结果（成功/失败/错误明细聚合去重）。
     * 结构：{@code {success, totalCount, successCount, failCount, skipCount?, updatedCount?, errorMessages?, summary, message}}
     */
    public static Map<String, Object> summarize(ImportResultDTO imp, String label) {
        Map<String, Object> result = new LinkedHashMap<>();
        int total = imp.getTotalCount();
        int success = imp.getSuccessCount();
        int fail = imp.getFailCount();
        int skip = imp.getSkipCount() == null ? 0 : imp.getSkipCount();

        result.put("totalCount", total);
        result.put("successCount", success);
        result.put("failCount", fail);
        if (skip > 0) result.put("skipCount", skip);
        if (imp.getUpdatedCount() > 0) result.put("updatedCount", imp.getUpdatedCount());
        if (imp.getCreatedMaterialCount() > 0) result.put("createdMaterialCount", imp.getCreatedMaterialCount());

        List<String> errors = aggregateErrors(imp.getErrors());
        if (!errors.isEmpty()) result.put("errorMessages", errors);

        boolean ok;
        String summary;
        if (total == 0) {
            ok = false;
            summary = label + "导入失败：文件中没有可导入的数据行";
        } else if (fail == 0) {
            ok = true;
            summary = label + "导入完成：共" + total + "行，成功" + success + "行"
                    + (skip > 0 ? "，跳过" + skip + "行" : "");
        } else if (success > 0) {
            ok = true;
            result.put("partial", true);
            summary = label + "导入部分成功：共" + total + "行，成功" + success + "行，失败" + fail + "行"
                    + (skip > 0 ? "，跳过" + skip + "行" : "");
        } else {
            ok = false;
            summary = label + "导入失败：共" + total + "行，成功0行，失败" + fail + "行";
        }
        if (!errors.isEmpty()) {
            summary += "。失败原因：" + String.join("；", errors);
        }
        result.put("success", ok);
        result.put("summary", summary);
        result.put("message", summary);
        return result;
    }

    /**
     * 失败明细聚合去重：同一"字段+原因"合并成一条，带行号区间与条数，
     * 避免同一原因按行刷几十条（与 BomImportRowsTool 同口径）。
     */
    public static List<String> aggregateErrors(List<ImportResultDTO.ErrorDetail> errors) {
        List<String> lines = new ArrayList<>();
        if (errors == null || errors.isEmpty()) return lines;

        Map<String, Agg> aggMap = new LinkedHashMap<>();
        for (ImportResultDTO.ErrorDetail e : errors) {
            if (e == null) continue;
            String field = e.getFieldName() == null ? "" : e.getFieldName();
            String msg = e.getMessage() == null ? "" : e.getMessage();
            String key = field + "|" + msg;
            Agg agg = aggMap.computeIfAbsent(key, k -> new Agg(field, msg));
            agg.count++;
            if (e.getRowIndex() > 0) {
                if (agg.firstRow <= 0) agg.firstRow = e.getRowIndex();
                agg.lastRow = Math.max(agg.lastRow, e.getRowIndex());
            }
        }
        for (Agg agg : aggMap.values()) {
            StringBuilder sb = new StringBuilder();
            if (agg.firstRow > 0) {
                if (agg.firstRow == agg.lastRow) sb.append("第").append(agg.firstRow).append("行 ");
                else sb.append("第").append(agg.firstRow).append("-").append(agg.lastRow).append("行 ");
            }
            if (!agg.field.isBlank()) sb.append(agg.field).append("：");
            sb.append(agg.msg);
            if (agg.count > 1) sb.append("（共").append(agg.count).append("行）");
            lines.add(sb.toString());
        }
        return lines;
    }

    private static final class Agg {
        final String field;
        final String msg;
        int count = 0;
        int firstRow = -1;
        int lastRow = -1;

        Agg(String field, String msg) {
            this.field = field;
            this.msg = msg;
        }
    }
}
