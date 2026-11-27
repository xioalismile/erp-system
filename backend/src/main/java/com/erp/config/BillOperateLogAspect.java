package com.erp.config;

import com.erp.common.util.SecurityUtils;
import com.erp.export.annotation.ExportField;
import com.erp.system.service.SysOperateLogService;
import com.erp.system.service.SysOperateLogService.FieldChange;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单据操作日志切面 - 自动拦截 Controller 写操作并写入 sys_operate_log 表。
 *
 * 拦截范围：所有标注 @PostMapping / @PutMapping / @DeleteMapping 的 Controller 方法。
 * 记录内容：操作人、操作时间、模块、操作类型、单据类型、单据编号、操作摘要。
 *
 * 字段级变更明细：仅对 PUT 修改操作（@PutMapping）尝试通过反射对比请求体中
 * 与 PathVariable id 关联的旧数据；若 Service 暴露 getById(id) 方法，则自动调用获取旧值。
 *
 * 排除：/api/system/log/** (自身日志查询)、/api/auth/** (登录)、/api/dashboard/** (查询统计)。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class BillOperateLogAspect {

    private final SysOperateLogService sysOperateLogService;

    /** 排除拦截的路径前缀（自身查询、登录认证、仪表盘统计等） */
    private static final String[] EXCLUDE_PREFIXES = {
            "/api/system/log",
            "/api/auth",
            "/api/dashboard",
            "/api/system/menu",
            "/api/system/button-config",
            "/api/system/role",
            "/api/system/user",
            "/api/system/dict",
            "/api/system/custom-field",
            "/api/system/print-template",
            "/api/system/serial-no-config",
            "/api/system/settings",
            "/api/system/kgw"
    };

    /** URL 段 → (billType, moduleName) 映射 */
    private static final Map<String, String[]> URL_BILL_TYPE_MAP = Map.ofEntries(
            Map.entry("quotes", new String[]{"销售报价单", "销售管理"}),
            Map.entry("orders", new String[]{"销售订单", "销售管理"}),
            Map.entry("contracts", new String[]{"销售合同", "销售管理"}),
            Map.entry("deliveries", new String[]{"销售出库单", "销售管理"}),
            Map.entry("invoices", new String[]{"销售发票", "销售管理"}),
            Map.entry("returns", new String[]{"销售退货单", "销售管理"}),
            Map.entry("exchanges", new String[]{"销售换货单", "销售管理"}),
            Map.entry("repairs", new String[]{"售后维修单", "销售管理"}),
            Map.entry("purchase/orders", new String[]{"采购订单", "采购管理"}),
            Map.entry("purchase/contracts", new String[]{"采购合同", "采购管理"}),
            Map.entry("purchase/inbounds", new String[]{"采购入库单", "采购管理"}),
            Map.entry("purchase/invoices", new String[]{"采购发票", "采购管理"}),
            Map.entry("purchase/returns", new String[]{"采购退货单", "采购管理"}),
            Map.entry("purchase/requests", new String[]{"采购申请单", "采购管理"}),
            Map.entry("stock-transfers", new String[]{"库存调拨单", "库存管理"}),
            Map.entry("stock-checks", new String[]{"库存盘点单", "库存管理"}),
            Map.entry("other-receipts", new String[]{"其他入库单", "库存管理"}),
            Map.entry("other-issues", new String[]{"其他出库单", "库存管理"}),
            Map.entry("material-requisitions", new String[]{"生产领料单", "库存管理"}),
            Map.entry("work-orders", new String[]{"生产工单", "生产管理"}),
            Map.entry("operation-reports", new String[]{"报工单", "生产管理"}),
            Map.entry("produce/assemble", new String[]{"生产组装单", "生产管理"}),
            Map.entry("produce/disassemble", new String[]{"生产拆卸单", "生产管理"}),
            Map.entry("mrp-plans", new String[]{"MRP计划", "生产管理"}),
            Map.entry("subcontract/orders", new String[]{"委外加工单", "委外管理"}),
            Map.entry("subcontract/picks", new String[]{"委外发料单", "委外管理"}),
            Map.entry("subcontract/inbounds", new String[]{"委外入库单", "委外管理"}),
            Map.entry("subcontract/supplements", new String[]{"委外补料单", "委外管理"}),
            Map.entry("subcontract/returns", new String[]{"委外退料单", "委外管理"}),
            Map.entry("customers", new String[]{"客户", "基础资料"}),
            Map.entry("customer", new String[]{"客户", "基础资料"}),
            Map.entry("materials", new String[]{"物料", "基础资料"}),
            Map.entry("material", new String[]{"物料", "基础资料"}),
            Map.entry("suppliers", new String[]{"供应商", "基础资料"}),
            Map.entry("supplier", new String[]{"供应商", "基础资料"}),
            Map.entry("employees", new String[]{"员工", "基础资料"}),
            Map.entry("employee", new String[]{"员工", "基础资料"}),
            Map.entry("warehouses", new String[]{"仓库", "基础资料"}),
            Map.entry("warehouse", new String[]{"仓库", "基础资料"}),
            Map.entry("locations", new String[]{"库位", "基础资料"}),
            Map.entry("location", new String[]{"库位", "基础资料"}),
            Map.entry("units", new String[]{"计量单位", "基础资料"}),
            Map.entry("unit", new String[]{"计量单位", "基础资料"}),
            Map.entry("currencies", new String[]{"币种", "基础资料"}),
            Map.entry("currency", new String[]{"币种", "基础资料"}),
            Map.entry("payment-terms", new String[]{"付款条件", "基础资料"}),
            Map.entry("payment-term", new String[]{"付款条件", "基础资料"}),
            Map.entry("price-levels", new String[]{"价格等级", "基础资料"}),
            Map.entry("price-level", new String[]{"价格等级", "基础资料"}),
            Map.entry("material-categories", new String[]{"物料分类", "基础资料"}),
            Map.entry("material-category", new String[]{"物料分类", "基础资料"}),
            Map.entry("departments", new String[]{"部门", "基础资料"}),
            Map.entry("department", new String[]{"部门", "基础资料"}),
            Map.entry("accounts", new String[]{"会计科目", "基础资料"}),
            Map.entry("account", new String[]{"会计科目", "基础资料"}),
            Map.entry("settles", new String[]{"结算方式", "基础资料"}),
            Map.entry("settle", new String[]{"结算方式", "基础资料"}),
            Map.entry("boms", new String[]{"BOM", "基础资料"}),
            Map.entry("routings", new String[]{"工艺路线", "基础资料"}),
            Map.entry("work-centers", new String[]{"工作中心", "基础资料"}),
            Map.entry("workstations", new String[]{"工位", "基础资料"}),
            Map.entry("customers-pool", new String[]{"公海客户", "客户关系"}),
            Map.entry("leads", new String[]{"线索", "客户关系"}),
            Map.entry("opportunities", new String[]{"商机", "客户关系"}),
            Map.entry("contacts", new String[]{"联系人", "客户关系"}),
            Map.entry("follow-ups", new String[]{"跟进记录", "客户关系"}),
            Map.entry("complaints", new String[]{"投诉", "客户关系"}),
            Map.entry("surveys", new String[]{"调研", "客户关系"}),
            Map.entry("sales-targets", new String[]{"销售目标", "客户关系"}),
            Map.entry("sale-pre-receipts", new String[]{"销售预收款", "财务管理"}),
            Map.entry("payment-receipts", new String[]{"收款单", "财务管理"}),
            Map.entry("payment-vouchers", new String[]{"付款单", "财务管理"}),
            Map.entry("purchase-pre-payments", new String[]{"采购预付款", "财务管理"}),
            Map.entry("finance/operation-expense", new String[]{"费用分摊", "财务管理"}),
            Map.entry("vouchers", new String[]{"凭证", "财务管理"}),
            Map.entry("free-samples", new String[]{"免费样品", "样品管理"}),
            Map.entry("samples", new String[]{"打样单", "样品管理"})
    );

    /** 需要跳过记录变更的系统字段（不展示给用户） */
    private static final List<String> SKIP_FIELDS = List.of(
            "createdAt", "updatedAt", "createdBy", "updatedBy",
            "created_at", "updated_at", "created_by", "updated_by",
            "orgCode", "org_code",
            "items", "detailList", "details"
    );

    /** 通用字段中文名后备映射（当字段没有 @ExportField 注解时使用） */
    private static final Map<String, String> FIELD_NAME_FALLBACK = new HashMap<>();
    static {
        // 通用
        FIELD_NAME_FALLBACK.put("status", "状态");
        FIELD_NAME_FALLBACK.put("remark", "备注");
        FIELD_NAME_FALLBACK.put("approvalStatus", "审核状态");
        FIELD_NAME_FALLBACK.put("approvedBy", "审核人");
        FIELD_NAME_FALLBACK.put("approvedAt", "审核时间");
        FIELD_NAME_FALLBACK.put("sourceBillNo", "源单据号");
        FIELD_NAME_FALLBACK.put("attachmentUrls", "附件");

        // ID类 - 后缀翻译
        FIELD_NAME_FALLBACK.put("customerId", "客户ID");
        FIELD_NAME_FALLBACK.put("supplierId", "供应商ID");
        FIELD_NAME_FALLBACK.put("materialId", "物料ID");
        FIELD_NAME_FALLBACK.put("employeeId", "员工ID");
        FIELD_NAME_FALLBACK.put("warehouseId", "仓库ID");
        FIELD_NAME_FALLBACK.put("locationId", "库位ID");
        FIELD_NAME_FALLBACK.put("unitId", "单位ID");
        FIELD_NAME_FALLBACK.put("currencyId", "币种ID");
        FIELD_NAME_FALLBACK.put("paymentTermId", "付款条件ID");
        FIELD_NAME_FALLBACK.put("priceLevelId", "价格等级ID");
        FIELD_NAME_FALLBACK.put("salerId", "销售员ID");
        FIELD_NAME_FALLBACK.put("defaultSalerId", "默认销售员ID");
        FIELD_NAME_FALLBACK.put("defaultPriceLevelId", "默认价格等级ID");
        FIELD_NAME_FALLBACK.put("opportunityId", "商机ID");
        FIELD_NAME_FALLBACK.put("assignedSalesId", "分配销售员ID");
        FIELD_NAME_FALLBACK.put("ownerUserId", "归属用户ID");
        FIELD_NAME_FALLBACK.put("workCenterId", "工作中心ID");
        FIELD_NAME_FALLBACK.put("workstationId", "工位ID");
        FIELD_NAME_FALLBACK.put("bomId", "BOM ID");
        FIELD_NAME_FALLBACK.put("routingId", "工艺路线ID");
        FIELD_NAME_FALLBACK.put("departmentId", "部门ID");
        FIELD_NAME_FALLBACK.put("accountId", "会计科目ID");
        FIELD_NAME_FALLBACK.put("settleId", "结算方式ID");

        // 名称/编码类
        FIELD_NAME_FALLBACK.put("customerCode", "客户编码");
        FIELD_NAME_FALLBACK.put("customerName", "客户名称");
        FIELD_NAME_FALLBACK.put("shortName", "简称");
        FIELD_NAME_FALLBACK.put("supplierCode", "供应商编码");
        FIELD_NAME_FALLBACK.put("supplierName", "供应商名称");
        FIELD_NAME_FALLBACK.put("materialCode", "物料编码");
        FIELD_NAME_FALLBACK.put("materialName", "物料名称");
        FIELD_NAME_FALLBACK.put("employeeCode", "员工编码");
        FIELD_NAME_FALLBACK.put("employeeName", "员工名称");
        FIELD_NAME_FALLBACK.put("warehouseCode", "仓库编码");
        FIELD_NAME_FALLBACK.put("warehouseName", "仓库名称");
        FIELD_NAME_FALLBACK.put("locationCode", "库位编码");
        FIELD_NAME_FALLBACK.put("locationName", "库位名称");
        FIELD_NAME_FALLBACK.put("unitCode", "单位编码");
        FIELD_NAME_FALLBACK.put("unitName", "单位名称");
        FIELD_NAME_FALLBACK.put("currencyCode", "币种编码");
        FIELD_NAME_FALLBACK.put("currencyName", "币种名称");
        FIELD_NAME_FALLBACK.put("departmentCode", "部门编码");
        FIELD_NAME_FALLBACK.put("departmentName", "部门名称");
        FIELD_NAME_FALLBACK.put("materialCategoryCode", "物料分类编码");
        FIELD_NAME_FALLBACK.put("materialCategoryName", "物料分类名称");
        FIELD_NAME_FALLBACK.put("specification", "规格型号");
        FIELD_NAME_FALLBACK.put("barcode", "条码");

        // 联系类
        FIELD_NAME_FALLBACK.put("contactPerson", "联系人");
        FIELD_NAME_FALLBACK.put("contactPhone", "联系电话");
        FIELD_NAME_FALLBACK.put("email", "邮箱");
        FIELD_NAME_FALLBACK.put("address", "地址");
        FIELD_NAME_FALLBACK.put("wechat", "微信");
        FIELD_NAME_FALLBACK.put("website", "公司网站");
        FIELD_NAME_FALLBACK.put("legalPerson", "法人代表");
        FIELD_NAME_FALLBACK.put("taxNo", "税号");
        FIELD_NAME_FALLBACK.put("taxRegisterNo", "税务登记号");
        FIELD_NAME_FALLBACK.put("bankName", "开户银行");
        FIELD_NAME_FALLBACK.put("bankAccount", "银行账号");

        // 单据号/日期类
        FIELD_NAME_FALLBACK.put("quoteNo", "报价单号");
        FIELD_NAME_FALLBACK.put("quoteDate", "报价日期");
        FIELD_NAME_FALLBACK.put("orderNo", "订单号");
        FIELD_NAME_FALLBACK.put("orderDate", "订单日期");
        FIELD_NAME_FALLBACK.put("contractNo", "合同号");
        FIELD_NAME_FALLBACK.put("contractDate", "合同日期");
        FIELD_NAME_FALLBACK.put("deliveryNo", "出库单号");
        FIELD_NAME_FALLBACK.put("deliveryDate", "出库日期");
        FIELD_NAME_FALLBACK.put("invoiceNo", "发票号");
        FIELD_NAME_FALLBACK.put("invoiceDate", "发票日期");
        FIELD_NAME_FALLBACK.put("returnNo", "退货单号");
        FIELD_NAME_FALLBACK.put("returnDate", "退货日期");
        FIELD_NAME_FALLBACK.put("billNo", "单据编号");
        FIELD_NAME_FALLBACK.put("billDate", "单据日期");
        FIELD_NAME_FALLBACK.put("validFrom", "有效期起");
        FIELD_NAME_FALLBACK.put("validTo", "有效期止");
        FIELD_NAME_FALLBACK.put("deliveryDate", "交货日期");

        // 金额/数量类
        FIELD_NAME_FALLBACK.put("exchangeRate", "汇率");
        FIELD_NAME_FALLBACK.put("conversionRate", "换算率");
        FIELD_NAME_FALLBACK.put("totalAmount", "总金额");
        FIELD_NAME_FALLBACK.put("originalAmount", "原金额");
        FIELD_NAME_FALLBACK.put("discountRate", "折扣率");
        FIELD_NAME_FALLBACK.put("discountAmount", "折扣金额");
        FIELD_NAME_FALLBACK.put("discountedAmount", "折后金额");
        FIELD_NAME_FALLBACK.put("taxRate", "税率");
        FIELD_NAME_FALLBACK.put("taxAmount", "税额");
        FIELD_NAME_FALLBACK.put("netAmount", "净额");
        FIELD_NAME_FALLBACK.put("amount", "金额");
        FIELD_NAME_FALLBACK.put("price", "单价");
        FIELD_NAME_FALLBACK.put("qty", "数量");
        FIELD_NAME_FALLBACK.put("quantity", "数量");
        FIELD_NAME_FALLBACK.put("creditLimit", "信用额度");
        FIELD_NAME_FALLBACK.put("annualRevenue", "年营业额");
        FIELD_NAME_FALLBACK.put("potentialValue", "潜在价值");
        FIELD_NAME_FALLBACK.put("satisfactionScore", "满意度评分");
        FIELD_NAME_FALLBACK.put("refundAmount", "累计退款金额");
        FIELD_NAME_FALLBACK.put("complaintCount", "投诉次数");
        FIELD_NAME_FALLBACK.put("businessOpportunityCount", "商机数量");
        FIELD_NAME_FALLBACK.put("orderCount", "订单数量");
        FIELD_NAME_FALLBACK.put("totalOrderAmount", "累计订单金额");

        // 分类/类型类
        FIELD_NAME_FALLBACK.put("customerType", "客户类型");
        FIELD_NAME_FALLBACK.put("customerLevel", "客户等级");
        FIELD_NAME_FALLBACK.put("sourceType", "客户来源");
        FIELD_NAME_FALLBACK.put("sourceDetail", "来源详情");
        FIELD_NAME_FALLBACK.put("industry", "所属行业");
        FIELD_NAME_FALLBACK.put("companyScale", "公司规模");
        FIELD_NAME_FALLBACK.put("tag", "客户标签");
        FIELD_NAME_FALLBACK.put("salesStage", "销售阶段");
        FIELD_NAME_FALLBACK.put("intentionLevel", "意向等级");
        FIELD_NAME_FALLBACK.put("importantLevel", "重要程度");
        FIELD_NAME_FALLBACK.put("crmStatus", "CRM状态");
        FIELD_NAME_FALLBACK.put("projectType", "项目类型");

        // 日期类
        FIELD_NAME_FALLBACK.put("lastContactDate", "最后联系日期");
        FIELD_NAME_FALLBACK.put("nextContactDate", "下次联系日期");
        FIELD_NAME_FALLBACK.put("poolEnterDate", "进入公海日期");
        FIELD_NAME_FALLBACK.put("lastOrderDate", "最后开单日期");
        FIELD_NAME_FALLBACK.put("lastFollowUpDate", "最后跟进日期");
        FIELD_NAME_FALLBACK.put("topTime", "置顶时间");
        FIELD_NAME_FALLBACK.put("followUpCount", "跟进次数");

        // 布尔类
        FIELD_NAME_FALLBACK.put("isBlacklist", "是否黑名单");
        FIELD_NAME_FALLBACK.put("poolType", "客户池类型");
        FIELD_NAME_FALLBACK.put("isTop", "是否置顶");
        FIELD_NAME_FALLBACK.put("isConfidential", "是否保密");
        FIELD_NAME_FALLBACK.put("enabled", "是否启用");

        // 业务字段
        FIELD_NAME_FALLBACK.put("opportunityNo", "商机编号");
        FIELD_NAME_FALLBACK.put("actualAmount", "实际金额");
        FIELD_NAME_FALLBACK.put("mainBusiness", "主营业务");
        FIELD_NAME_FALLBACK.put("customerDemand", "客户需求");
        FIELD_NAME_FALLBACK.put("customerPreference", "客户喜好");
        FIELD_NAME_FALLBACK.put("familyBackground", "家庭背景");
        FIELD_NAME_FALLBACK.put("extData", "扩展数据");
    }

    /** URL 后缀 → 操作类型推断 */
    private static String inferOperateTypeFromUrl(String uri, String method) {
        if (uri.endsWith("/approve")) return "审核";
        if (uri.endsWith("/reverse-approve") || uri.endsWith("/unapprove")) return "重置草稿";
        if (uri.endsWith("/submit")) return "提交";
        if (uri.endsWith("/cancel")) return "取消";
        if (uri.endsWith("/close") || uri.endsWith("/close-order")) return "关闭";
        if (uri.endsWith("/reopen")) return "反结案";
        if (uri.endsWith("/push-to-delivery") || uri.endsWith("/push-to-purchase") || uri.endsWith("/push")) return "下推";
        if ("POST".equals(method)) return "新增";
        if ("PUT".equals(method)) return "修改";
        if ("DELETE".equals(method)) return "删除";
        return "操作";
    }

    @Around("@annotation(postMapping)")
    public Object aroundPost(ProceedingJoinPoint joinPoint, PostMapping postMapping) throws Throwable {
        return aroundWrite(joinPoint, "POST");
    }

    @Around("@annotation(putMapping)")
    public Object aroundPut(ProceedingJoinPoint joinPoint, PutMapping putMapping) throws Throwable {
        return aroundWrite(joinPoint, "PUT");
    }

    @Around("@annotation(deleteMapping)")
    public Object aroundDelete(ProceedingJoinPoint joinPoint, DeleteMapping deleteMapping) throws Throwable {
        return aroundWrite(joinPoint, "DELETE");
    }

    private Object aroundWrite(ProceedingJoinPoint joinPoint, String httpMethod) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        if (request == null) {
            return joinPoint.proceed();
        }

        String uri = request.getRequestURI();

        // 排除：自身日志查询、登录认证等
        for (String prefix : EXCLUDE_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return joinPoint.proceed();
            }
        }

        // 【时序铁律】修改操作必须在业务执行前抓旧值快照：
        // proceed() 之后新值已落库，此时再 getById 读到的"旧值"其实是"新值"，diff 恒为空。
        OldSnapshot snapshot = null;
        String[] preBillInfo = inferBillType(uri);
        if (preBillInfo[0] != null) {
            if ("PUT".equals(httpMethod) && "修改".equals(inferOperateTypeFromUrl(uri, httpMethod))) {
                Long billId = extractPathId(uri);
                if (billId == null) {
                    billId = extractIdFromBody(joinPoint);
                }
                if (billId != null) {
                    snapshot = captureOldSnapshot(joinPoint, billId);
                }
            } else if ("DELETE".equals(httpMethod)) {
                // 删除：请求体里没有实体，业务执行前按 id 读出来，供日志记"编码"而非主键 ID
                Long billId = extractPathId(uri);
                if (billId != null) {
                    Object before = loadEntityById(joinPoint, billId);
                    if (before != null) {
                        snapshot = new OldSnapshot(before, null);
                    }
                }
            }
        }

        // 执行业务逻辑
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                recordLog(joinPoint, httpMethod, uri, request, result, error, snapshot);
            } catch (Exception e) {
                log.warn("切面记录操作日志失败 uri={}", uri, e);
            }
        }
    }

    private void recordLog(ProceedingJoinPoint joinPoint, String httpMethod, String uri,
                           HttpServletRequest request, Object result, Throwable error,
                           OldSnapshot snapshot) {
        // 推断模块/单据类型
        String[] billInfo = inferBillType(uri);
        String billType = billInfo[0];
        String moduleName = billInfo[1];
        if (billType == null) {
            // 未知业务路径，跳过（避免记录无关的 POST）
            log.debug("切面跳过未知路径 uri={} method={}", uri, httpMethod);
            return;
        }
        log.info("切面记录操作日志 uri={} method={} billType={} module={}", uri, httpMethod, billType, moduleName);

        String operateType = inferOperateTypeFromUrl(uri, httpMethod);

        // 提取单据编号：优先从 PathVariable id 取；其次从 @RequestBody 中反射获取 xxxNo 字段
        Long billId = extractPathId(uri);
        // 基础资料 PUT 接口 URL 不带 {id}，尝试从请求体反射获取 id（如 customerId/supplierId/materialId 等）
        if (billId == null) {
            billId = extractIdFromBody(joinPoint);
        }
        String billNo = extractBillNoFromArgs(joinPoint, result);

        // 提取用户信息
        String userName = SecurityUtils.getCurrentUsername(request);
        // 【2026-10-01 修复】原先 realName 直接等于登录账号 → 日志里显示 "lyc123"，用户看不出是谁改的。
        // 改为取当前用户真实姓名（sys_user.real_name，登录鉴权时已装载并随请求注入），取不到才回落账号。
        String realName = SecurityUtils.getCurrentRealName(request);
        if (realName == null || realName.isBlank()) {
            realName = userName;
        }

        // 操作摘要
        String summary = operateType + billType + (billNo != null ? " " + billNo : (billId != null ? " #" + billId : ""));

        // 字段级变更明细（仅修改操作）：必须使用 proceed() 之前抓取的旧值快照做对比
        List<FieldChange> changes = null;
        if ("修改".equals(operateType) && snapshot != null) {
            changes = diffObjects(snapshot.oldObj, snapshot.newObj);
        } else if ("新增".equals(operateType) && result != null) {
            // 新增成功后从结果对象提取编号
            if (billNo == null) {
                billNo = extractNoFromObject(result);
            }
        } else if ("删除".equals(operateType) && snapshot != null && snapshot.oldObj != null && billNo == null) {
            // 删除操作的请求体里没有实体：用"删除前快照"里的编码当单据编号，避免日志里显示主键 ID
            billNo = extractNoFromObject(snapshot.oldObj);
        }

        if (billNo == null && billId != null) {
            billNo = String.valueOf(billId);
        }

        // 【2026-09-15 修复】判定真实结果：原先无论成败都记「成功」
        String[] judged = judgeResult(result, error);
        String operateResult = judged[0];
        String failReason = judged[1];

        sysOperateLogService.saveBillLog(billType, billNo, operateType, summary, userName, realName, moduleName,
                changes, userName, request.getRemoteAddr(), uri, httpMethod, operateResult, failReason);
    }

    /**
     * 判定本次请求的真实结果，返回 [操作结果, 失败原因]。
     * <p>
     * 两种失败形态都要覆盖：
     * ① 抛异常（如服务层校验失败，由全局异常处理器转成错误响应）；
     * ② Controller 正常返回但响应体是 {@code Result.error(...)}（如 409 编码已存在、404 记录不存在、业务校验 500）。
     * <p>
     * 修复背景：原先 {@code operate_result} 恒为「成功」，失败的操作在日志里也显示成功，
     * 还会写出"未生效的变更明细"，审计上是误导。
     */
    private String[] judgeResult(Object result, Throwable error) {
        if (error != null) {
            String msg = error.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = error.getClass().getSimpleName();
            }
            return new String[]{"失败", msg};
        }
        if (result != null) {
            try {
                Class<?> cls = result.getClass();
                // 只针对统一返回体 Result（避免误判其它对象）
                if (cls.getName().contains("Result")) {
                    Object code = cls.getMethod("getCode").invoke(result);
                    if (code instanceof Number n && n.intValue() != 200) {
                        String msg = null;
                        try {
                            Object m = cls.getMethod("getMessage").invoke(result);
                            if (m != null) msg = String.valueOf(m);
                        } catch (Exception ignored) {}
                        return new String[]{"失败", msg != null && !msg.isBlank() ? msg : ("业务返回码 " + n.intValue())};
                    }
                }
            } catch (Exception ignored) {
                // 反射失败按成功处理，绝不因日志影响业务
            }
        }
        return new String[]{"成功", null};
    }

    /**
     * URL 匹配用的键，按"最长优先"排序。
     * 【2026-09-15 修复】原先直接遍历 Map.ofEntries(...).entrySet()，其迭代顺序由 JVM 哈希盐决定、
     * 每次启动都可能不同 → /api/purchase/orders 可能先命中 "orders" 被误记成「销售订单」。
     * 改为按 key 长度降序（更具体的路径段优先），结果确定且语义正确。
     */
    private static final List<String> URL_BILL_TYPE_KEYS = URL_BILL_TYPE_MAP.keySet().stream()
            .sorted((a, b) -> b.length() - a.length())
            .toList();

    /** 从 URI 推断 (billType, moduleName) */
    private String[] inferBillType(String uri) {
        // uri 形如 /api/sale/orders/{id}/approve
        // 提取 /api/ 之后的路径段，逐段匹配 URL_BILL_TYPE_MAP（最长键优先，避免 orders 抢走 purchase/orders）
        for (String key : URL_BILL_TYPE_KEYS) {
            // 构造匹配模式：/api/{module}/{key} 或 /api/{key}
            Pattern p = Pattern.compile("/api/(?:[a-z]+/)?" + Pattern.quote(key) + "(?:/|$)");
            Matcher m = p.matcher(uri);
            if (m.find()) {
                return URL_BILL_TYPE_MAP.get(key);
            }
        }
        return new String[]{null, null};
    }

    /** 从 URI 中提取 PathVariable id（Long 型） */
    private Long extractPathId(String uri) {
        // 匹配 /api/xxx/{数字}
        Matcher m = Pattern.compile("/(\\d{1,19})(?:/|$)").matcher(uri);
        if (m.find()) {
            try {
                return Long.valueOf(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * 从方法参数（@RequestBody）中反射获取 id 字段值。
     * 适用于基础资料 PUT 接口（如 /api/basedata/customer），URL 不带 {id}，
     * id 通过请求体的 xxxId 字段传递（如 customerId、supplierId、materialId 等）。
     */
    private Long extractIdFromBody(ProceedingJoinPoint joinPoint) {
        try {
            for (Object arg : joinPoint.getArgs()) {
                if (arg == null) continue;
                Class<?> cls = arg.getClass();
                // 跳过基本类型和 String
                if (cls.isPrimitive() || cls == String.class || Number.class.isAssignableFrom(cls)
                        || cls.isEnum()) {
                    continue;
                }
                // 优先查找以 "Id" 结尾的非空 Long 型字段
                for (Field f : cls.getDeclaredFields()) {
                    if (!f.getName().endsWith("Id")) continue;
                    if (!f.getType().equals(Long.class) && !f.getType().equals(long.class)) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(arg);
                        if (v != null) {
                            return Long.valueOf(v.toString());
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.debug("从请求体提取 id 失败", e);
        }
        return null;
    }

    /** 从方法参数中提取单据编号 */
    private String extractBillNoFromArgs(ProceedingJoinPoint joinPoint, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (paramNames == null) return null;

        for (Object arg : args) {
            String no = extractNoFromObject(arg);
            if (no != null) return no;
        }
        // 从结果中提取
        return extractNoFromObject(result);
    }

    /** 反射获取对象的 xxxNo / xxxCode 字段值（字符串） */
    private String extractNoFromObject(Object obj) {
        if (obj == null) return null;
        // 包装 Result 时取 data
        Object target = obj;
        try {
            Class<?> resultCls = obj.getClass();
            if (resultCls.getName().contains("Result")) {
                try {
                    Object data = resultCls.getMethod("getData").invoke(obj);
                    if (data != null) target = data;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception ignored) {}

        Class<?> cls = target.getClass();
        for (Field f : cls.getDeclaredFields()) {
            String name = f.getName();
            if (name.endsWith("No") || name.endsWith("Code") || name.equals("billNo") || name.equals("orderNo")) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(target);
                    if (v != null) return v.toString();
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * 按 id 反射调用 Controller 注入 Service 的 getById(Serializable) 取实体。
     * 必须在 joinPoint.proceed() 之前调用：之后新值已落库，读到的"旧值"即新值。
     * Controller 中没有 Service 类型字段时返回 null（fail-safe，不影响业务）。
     */
    private Object loadEntityById(ProceedingJoinPoint joinPoint, Long id) {
        try {
            Object target = joinPoint.getTarget();
            Class<?> targetCls = target.getClass();

            // 在 Controller 中找一个 Service 类型的字段
            for (Field f : targetCls.getDeclaredFields()) {
                Class<?> fType = f.getType();
                // 判断是否为 Service（类名包含 Service 或 实现 IService）
                if (!fType.getName().contains("Service")) continue;
                f.setAccessible(true);
                Object serviceBean = f.get(target);
                if (serviceBean == null) continue;

                // 调用 getById：IService 的方法签名是 getById(Serializable)，因此用反射按方法名查找
                java.lang.reflect.Method getById = null;
                for (java.lang.reflect.Method m : fType.getMethods()) {
                    if ("getById".equals(m.getName()) && m.getParameterCount() == 1) {
                        getById = m;
                        break;
                    }
                }
                if (getById == null) continue;

                try {
                    Object entity = getById.invoke(serviceBean, id);
                    // 旧数据为空：可能是该 Service 不处理此 id（如 customerService 查询 materialId 返回 null），
                    // 继续尝试 Controller 中的下一个 Service 字段
                    if (entity != null) {
                        return entity;
                    }
                } catch (Exception ex) {
                    log.debug("调用 getById 失败 service={} id={}", fType.getSimpleName(), id, ex);
                }
            }
        } catch (Exception e) {
            log.debug("字段变更明细提取失败 id={}", id, e);
        }
        return null;
    }

    /**
     * 【2026-08-16 修复·2026-09-15 恢复】修改操作前抓取"旧值 + 新值"快照。
     * 必须在 joinPoint.proceed() 之前调用。旧对象按 id 反射 getById 取；
     * 新对象取方法参数中与旧对象同类型的那个（即 @RequestBody）；两者都拿到才返回快照。
     */
    private OldSnapshot captureOldSnapshot(ProceedingJoinPoint joinPoint, Long id) {
        Object oldObj = loadEntityById(joinPoint, id);
        if (oldObj == null) {
            return null;
        }
        // 从方法参数中获取新对象
        for (Object arg : joinPoint.getArgs()) {
            if (arg != null && arg.getClass().equals(oldObj.getClass())) {
                return new OldSnapshot(oldObj, arg);
            }
        }
        // 类型不匹配（如 customerService 返回 Customer，但请求体是 Material）：不生成明细
        return null;
    }

    /** 修改操作的新旧对象快照（oldObj 必须在业务执行前读取，否则会读到新值） */
    private static class OldSnapshot {
        final Object oldObj;
        final Object newObj;

        OldSnapshot(Object oldObj, Object newObj) {
            this.oldObj = oldObj;
            this.newObj = newObj;
        }
    }

    /**
     * 对比两个对象的字段差异（仅比较基本类型/String/Number/LocalDate 等简单字段）。
     * <p>
     * 修复：
     * 1. 对 BigDecimal 使用 compareTo 比较（避免 1.000000 vs 1 被误判为不同）
     * 2. 对数值/日期进行格式化后展示，避免显示 +1.000000 这类不友好内容
     * 3. 字段名翻译：优先 @ExportField.label，其次 FIELD_NAME_FALLBACK 映射，最后驼峰转中文
     * 4. 跳过 created/updated 等系统字段
     */
    private List<FieldChange> diffObjects(Object oldObj, Object newObj) {
        List<FieldChange> changes = new ArrayList<>();
        Class<?> cls = oldObj.getClass();

        // 预构建字段 → @ExportField 中文标签映射（含父类字段）
        Map<String, String> fieldLabelMap = buildFieldLabelMap(cls);

        for (Field f : cls.getDeclaredFields()) {
            String fieldName = f.getName();

            // 跳过系统字段和集合字段
            if (SKIP_FIELDS.contains(fieldName)) continue;
            // 跳过"非落库字段"（@TableField(exist=false)，如客户名称/销售员名称/币种编码等展示字段）：
            // 这些字段库里根本没有值 → 旧值恒空、新值恒来自请求体 → 会刷出"空 → 值"的假变更行
            com.baomidou.mybatisplus.annotation.TableField tf =
                    f.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
            if (tf != null && !tf.exist()) continue;
            Class<?> fType = f.getType();
            if (java.util.Collection.class.isAssignableFrom(fType)) continue;
            if (java.util.Map.class.isAssignableFrom(fType)) continue;
            if (fieldName.startsWith("this$")) continue; // 跳过内部类引用

            try {
                f.setAccessible(true);
                Object oldV = f.get(oldObj);
                Object newV = f.get(newObj);

                // 两者都为空 → 跳过
                if (oldV == null && newV == null) continue;

                // 使用值比较（特殊处理 BigDecimal）
                if (!valuesEqual(oldV, newV)) {
                    // 格式化后展示给用户
                    String oldStr = formatValueForDisplay(oldV, f);
                    String newStr = formatValueForDisplay(newV, f);

                    // 格式化后相同也跳过（如 BigDecimal 1.00 vs 1 格式化后相同）
                    if (oldStr.equals(newStr)) continue;

                    // 翻译字段名：优先 @ExportField，其次后备映射，最后驼峰转中文
                    String fieldDesc = fieldLabelMap.get(fieldName);
                    if (fieldDesc == null || fieldDesc.isBlank()) {
                        fieldDesc = FIELD_NAME_FALLBACK.getOrDefault(fieldName, camelToChinese(fieldName));
                    }

                    String dataType = fType.getSimpleName();
                    changes.add(new FieldChange(fieldDesc, oldStr, newStr, dataType));
                }
            } catch (Exception ignored) {}
        }
        return changes;
    }

    /**
     * 构建字段 → @ExportField.label 映射（含父类字段）
     */
    private Map<String, String> buildFieldLabelMap(Class<?> cls) {
        Map<String, String> map = new HashMap<>();
        Class<?> curr = cls;
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                ExportField ef = f.getAnnotation(ExportField.class);
                if (ef != null && ef.label() != null && !ef.label().isBlank()) {
                    // 未 hidden 的才用注解 label；hidden=true（如ID）仍保留，避免字段全空
                    map.putIfAbsent(f.getName(), ef.label());
                }
            }
            curr = curr.getSuperclass();
        }
        return map;
    }

    /**
     * 比较两个值是否相等，特殊处理 BigDecimal（用 compareTo 而非 equals）
     */
    private boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        // BigDecimal 特殊处理：1.000000 compareTo 1 == 0
        if (a instanceof BigDecimal && b instanceof BigDecimal) {
            return ((BigDecimal) a).compareTo((BigDecimal) b) == 0;
        }
        // Double/Float：转为 BigDecimal 后比较，避免浮点误差
        if ((a instanceof Double || a instanceof Float) && (b instanceof Double || b instanceof Float)) {
            BigDecimal ba = new BigDecimal(String.valueOf(a));
            BigDecimal bb = new BigDecimal(String.valueOf(b));
            return ba.compareTo(bb) == 0;
        }
        return a.equals(b);
    }

    /**
     * 格式化字段值用于日志展示：
     * - BigDecimal：去掉末尾多余 0（或按注解 numberFormat）
     * - LocalDate/LocalDateTime：按注解或默认格式
     * - 其他：toString()
     */
    private String formatValueForDisplay(Object v, Field f) {
        if (v == null) return "";

        ExportField ef = f.getAnnotation(ExportField.class);

        // 1. BigDecimal 格式化
        if (v instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) v;
            String pattern = ef != null ? ef.numberFormat() : "";
            if (pattern != null && !pattern.isBlank()) {
                // 简单按 numberFormat 的小数位
                int scale = countDecimalPlaces(pattern);
                if (scale >= 0) {
                    try {
                        return bd.setScale(scale, RoundingMode.HALF_UP).toPlainString();
                    } catch (Exception ignored) {}
                }
            }
            // 默认：去掉末尾多余零，避免显示 +1.000000
            try {
                return bd.stripTrailingZeros().toPlainString();
            } catch (Exception e) {
                return bd.toPlainString();
            }
        }

        // 2. Double/Float → 去掉多余小数位
        if (v instanceof Double || v instanceof Float) {
            BigDecimal bd = new BigDecimal(String.valueOf(v));
            try {
                return bd.stripTrailingZeros().toPlainString();
            } catch (Exception e) {
                return v.toString();
            }
        }

        // 3. LocalDate 格式化
        if (v instanceof LocalDate) {
            String fmt = (ef != null && ef.dateFormat() != null && !ef.dateFormat().isBlank())
                    ? ef.dateFormat() : "yyyy-MM-dd";
            try {
                return ((LocalDate) v).format(DateTimeFormatter.ofPattern(fmt));
            } catch (Exception e) {
                return v.toString();
            }
        }

        // 4. LocalDateTime 格式化
        if (v instanceof LocalDateTime) {
            String fmt = (ef != null && ef.dateFormat() != null && !ef.dateFormat().isBlank())
                    ? ef.dateFormat() : "yyyy-MM-dd HH:mm:ss";
            try {
                return ((LocalDateTime) v).format(DateTimeFormatter.ofPattern(fmt));
            } catch (Exception e) {
                return v.toString();
            }
        }

        return v.toString();
    }

    /** 根据 numberFormat 模式计算小数位数（简单实现：#.0000 → 4） */
    private int countDecimalPlaces(String pattern) {
        if (pattern == null) return -1;
        int dot = pattern.indexOf('.');
        if (dot < 0) return 0;
        int cnt = 0;
        for (int i = dot + 1; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '0' || c == '#') cnt++;
            else break;
        }
        return cnt;
    }

    /**
     * 驼峰命名 → 简易中文翻译（最后兜底）。
     * 对常见英文单词片段做简单映射；无法识别时保留原字段名。
     */
    private static String camelToChinese(String name) {
        if (name == null || name.isBlank()) return name;
        // 先试把常见后缀/前缀用替换表翻译
        String result = name;
        // 简单规则：customerId → 客户ID 等，已在 FIELD_NAME_FALLBACK 覆盖；
        // 这里只做兜底：把常见片段替换
        for (Map.Entry<String, String> e : CAMEL_WORD_DICT.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        // 如果全部替换完没有中文（即没命中），返回原字段名
        if (result.equals(name)) {
            return name;
        }
        return result;
    }

    private static final Map<String, String> CAMEL_WORD_DICT = new HashMap<>();
    static {
        CAMEL_WORD_DICT.put("customer", "客户");
        CAMEL_WORD_DICT.put("supplier", "供应商");
        CAMEL_WORD_DICT.put("material", "物料");
        CAMEL_WORD_DICT.put("employee", "员工");
        CAMEL_WORD_DICT.put("warehouse", "仓库");
        CAMEL_WORD_DICT.put("location", "库位");
        CAMEL_WORD_DICT.put("department", "部门");
        CAMEL_WORD_DICT.put("currency", "币种");
        CAMEL_WORD_DICT.put("payment", "付款");
        CAMEL_WORD_DICT.put("price", "价格");
        CAMEL_WORD_DICT.put("sale", "销售");
        CAMEL_WORD_DICT.put("purchase", "采购");
        CAMEL_WORD_DICT.put("delivery", "出库");
        CAMEL_WORD_DICT.put("invoice", "发票");
        CAMEL_WORD_DICT.put("return", "退货");
        CAMEL_WORD_DICT.put("quote", "报价");
        CAMEL_WORD_DICT.put("order", "订单");
        CAMEL_WORD_DICT.put("contract", "合同");
        CAMEL_WORD_DICT.put("status", "状态");
        CAMEL_WORD_DICT.put("remark", "备注");
        CAMEL_WORD_DICT.put("date", "日期");
        CAMEL_WORD_DICT.put("no", "单号");
        CAMEL_WORD_DICT.put("code", "编码");
        CAMEL_WORD_DICT.put("name", "名称");
        CAMEL_WORD_DICT.put("type", "类型");
        CAMEL_WORD_DICT.put("level", "等级");
        CAMEL_WORD_DICT.put("amount", "金额");
        CAMEL_WORD_DICT.put("qty", "数量");
        CAMEL_WORD_DICT.put("quantity", "数量");
        CAMEL_WORD_DICT.put("rate", "率");
        CAMEL_WORD_DICT.put("count", "数量");
        CAMEL_WORD_DICT.put("Id", "ID");
    }
}
