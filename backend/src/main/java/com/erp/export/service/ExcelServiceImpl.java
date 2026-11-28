package com.erp.export.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.poi.hssf.usermodel.HSSFPatriarch;
import org.apache.poi.hssf.usermodel.HSSFPicture;
import org.apache.poi.hssf.usermodel.HSSFShape;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.ShapeTypes;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFObjectData;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFShapeGroup;
import org.apache.poi.xssf.usermodel.XSSFSimpleShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.basedata.mapper.*;
import com.erp.crm.entity.Lead;
import com.erp.crm.mapper.LeadMapper;
import com.erp.crm.mapper.OpportunityMapper;
import com.erp.crm.mapper.ComplaintMapper;
import com.erp.crm.mapper.SurveyMapper;
import com.erp.export.annotation.ExportField;
import com.erp.export.dto.ExportConfigDTO;
import com.erp.export.dto.ImportResultDTO;
import com.erp.export.dto.BomImportCheckDTO;
import com.erp.export.dto.MaterialImportCheckDTO;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.finance.mapper.AccountsPayableMapper;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.BomMapper;
import com.erp.masterdata.mapper.BomItemMapper;
import com.erp.masterdata.mapper.CustomerMapper;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.masterdata.mapper.SupplierMapper;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.entity.SaleQuote;
import com.erp.sale.entity.SaleQuoteItem;
import com.erp.sale.entity.SaleContract;
import com.erp.sale.entity.SaleContractItem;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleDeliveryItem;
import com.erp.sale.entity.SaleReturn;
import com.erp.sale.entity.SaleReturnItem;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.entity.SaleInvoiceItem;
import com.erp.sale.mapper.*;
import com.erp.purchase.mapper.*;
import com.erp.production.mapper.*;
import com.erp.produce.mrp.mapper.MrpPlanMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelServiceImpl implements ExcelService {

    /** 文件上传根目录（与 FileUploadController/WebMvcConfig 保持一致） */
    @Value("${erp.upload.path:D:/erp-files}")
    private String uploadPath;

    /**
     * 统一数据权限解析器：导出取数必须与列表页同口径。
     * 历史 BUG：导出走 {@code selectPage(page, null)} 全表取数，普通账号能导出全公司的单据/主档。
     */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.scope.DataScopeResolver exportDataScopeResolver;

    private final CustomerMapper customerMapper;
    /** 客户导入默认人（归属人/分配销售员）需要取当前账号及其员工档案（2026-09-21） */
    private final com.erp.system.mapper.SysUserMapper sysUserMapper;
    private final UnitOfMeasureMapper unitOfMeasureMapper;
    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;
    private final MaterialCategoryMapper materialCategoryMapper;
    private final SupplierMapper supplierMapper;
    private final WarehouseMapper warehouseMapper;
    private final com.erp.basedata.mapper.LocationMapper locationMapper;
    private final AccountMapper accountMapper;
    private final CurrencyMapper currencyMapper;
    private final PaymentTermMapper paymentTermMapper;
    private final PriceLevelMapper priceLevelMapper;
    private final SettlementModeMapper settlementModeMapper;
    private final MaterialMapper materialMapper;
    private final BomMapper bomMapper;
    private final BomItemMapper bomItemMapper;
    private final com.erp.masterdata.service.BomService bomService;
    private final LeadMapper leadMapper;
    private final OpportunityMapper opportunityMapper;
    private final ComplaintMapper complaintMapper;
    private final SurveyMapper surveyMapper;
    private final PaymentReceiptMapper paymentReceiptMapper;
    private final AccountsReceivableMapper accountsReceivableMapper;
    private final AccountsPayableMapper accountsPayableMapper;
    private final SaleQuoteMapper saleQuoteMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final SaleDeliveryMapper saleDeliveryMapper;
    private final SaleReturnMapper saleReturnMapper;
    private final SaleInvoiceMapper saleInvoiceMapper;
    private final SaleContractMapper saleContractMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PurchaseInvoiceMapper purchaseInvoiceMapper;
    private final PurchaseContractMapper purchaseContractMapper;
    private final com.erp.produce.assemble.service.ProduceAssembleService produceAssembleService;
    private final com.erp.produce.assemble.service.ProduceDisassembleService produceDisassembleService;
    private final WorkOrderMapper workOrderMapper;
    private final ProductionReceiptMapper productionReceiptMapper;
    private final OperationReportMapper operationReportMapper;
    private final WorkCenterMapper workCenterMapper;
    private final RoutingMapper routingMapper;
    private final WorkstationMapper workstationMapper;
    private final MrpPlanMapper mrpPlanMapper;
    private final com.erp.serialno.service.SysSerialNoConfigService serialNoConfigService;
    private final com.erp.masterdata.service.MaterialCodeRuleService materialCodeRuleService;
    private final com.erp.customfield.service.CustomFieldService customFieldService;
    /** 设置中心「货品查重字段」规则（物料导入冲突判定 + 模板必填标记） */
    private final com.erp.masterdata.service.MaterialDedupService materialDedupService;
    /** 字段权限（角色必填）：模板星号与导入校验都叠加此口径，保证"界面必填 = 模板星号 = 导入必填" */
    private final com.erp.permission.service.FieldRequiredPermissionService fieldRequiredPermissionService;
    /** 字段权限-不可见：导出写盘前剔列（与列表响应过滤同一份口径，2026-11-27） */
    private final com.erp.permission.service.FieldVisiblePermissionService fieldVisiblePermissionService;
    /** 字段权限-非 JSON 出口统一过滤器：所有数据导出在写盘前统一剔列 */
    private final com.erp.permission.service.FieldPermissionOutputFilter fieldPermissionOutputFilter;
    private final com.erp.sale.service.SaleOrderService saleOrderService;
    private final com.erp.sale.service.SaleQuoteService saleQuoteService;
    private final com.erp.sale.service.SaleContractService saleContractService;
    private final com.erp.sale.service.SaleDeliveryService saleDeliveryService;
    private final com.erp.sale.service.SaleReturnService saleReturnService;
    private final com.erp.sale.service.SaleInvoiceService saleInvoiceService;
    private final com.erp.purchase.service.PurchaseRequestService purchaseRequestService;
    private final com.erp.purchase.service.PurchaseOrderService purchaseOrderService;
    private final com.erp.purchase.service.PurchaseContractService purchaseContractService;
    private final com.erp.purchase.service.PurchaseInboundService purchaseInboundService;
    private final com.erp.purchase.service.PurchaseReturnService purchaseReturnService;
    private final com.erp.purchase.service.PurchaseInvoiceService purchaseInvoiceService;
    private final com.erp.inventory.service.WarehouseService warehouseService;
    private final com.erp.inventory.service.OtherReceiptService otherReceiptService;
    private final com.erp.inventory.service.OtherIssueService otherIssueService;
    private final com.erp.inventory.service.StockTransferService stockTransferService;
    private final com.erp.inventory.service.StockCheckService stockCheckService;
    private final com.erp.inventory.service.MaterialRequisitionService materialRequisitionService;
    private final com.erp.finance.service.FinanceService financeService;
    private final com.erp.finance.service.OperationExpenseService operationExpenseService;
    private final com.erp.finance.prepayment.purchase.service.PurchasePrePaymentService purchasePrePaymentService;
    private final com.erp.finance.prepayment.sale.service.SalePreReceiptService salePreReceiptService;

    /** 销售/采购价格策略：导入必须复用现有服务（保存/校验/编码生成），导出取明细用 itemMapper */
    private final com.erp.price.sale.service.SalePriceStrategyService salePriceStrategyService;
    private final com.erp.price.purchase.service.PurchasePriceStrategyService purchasePriceStrategyService;
    private final com.erp.price.sale.mapper.SalePriceStrategyItemMapper salePriceStrategyItemMapper;
    private final com.erp.price.purchase.mapper.PurchasePriceStrategyItemMapper purchasePriceStrategyItemMapper;

    /**
     * 事务管理器（2026-09-19 导入状态分流用）：
     * 「保存单据 + 按状态列提交」必须同一事务 —— 提交失败（如审批开着但没配可用审批流程）
     * 要连保存一起回滚，否则会留下"报错但草稿已入库"的半成品，用户重导即重复单。
     */
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    // ===== 单据导出（带物料明细）用：单据主表 Mapper（原先未登记，导出会报"不支持的实体类型"）=====
    private final com.erp.inventory.mapper.OtherIssueMapper otherIssueMapper;
    private final com.erp.inventory.mapper.MaterialRequisitionMapper materialRequisitionMapper;
    private final com.erp.inventory.mapper.OtherReceiptMapper otherReceiptMapper;
    private final com.erp.inventory.mapper.StockTransferMapper stockTransferMapper;
    private final com.erp.inventory.mapper.StockCheckMapper stockCheckMapper;
    private final com.erp.basedata.mapper.BankAccountMapper bankAccountMapper;
    private final com.erp.subcontract.order.mapper.SubcontractOrderMapper subcontractOrderMapper;
    private final com.erp.subcontract.pick.mapper.SubcontractPickMapper subcontractPickMapper;
    private final com.erp.subcontract.inbound.mapper.SubcontractInboundMapper subcontractInboundMapper;
    private final com.erp.subcontract.returnn.mapper.SubcontractReturnMapper subcontractReturnMapper;
    private final com.erp.subcontract.supplement.mapper.SubcontractSupplementMapper subcontractSupplementMapper;

    // ===== 单据导出（带物料明细）用：明细表 Mapper（按单据外键批量取明细行）=====
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final SaleQuoteItemMapper saleQuoteItemMapper;
    private final SaleContractItemMapper saleContractItemMapper;
    private final SaleDeliveryItemMapper saleDeliveryItemMapper;
    private final SaleReturnItemMapper saleReturnItemMapper;
    private final SaleInvoiceItemMapper saleInvoiceItemMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final PurchaseContractItemMapper purchaseContractItemMapper;
    private final PurchaseInboundItemMapper purchaseInboundItemMapper;
    private final PurchaseReturnItemMapper purchaseReturnItemMapper;
    private final PurchaseInvoiceItemMapper purchaseInvoiceItemMapper;
    private final com.erp.inventory.mapper.OtherIssueItemMapper otherIssueItemMapper;
    private final com.erp.inventory.mapper.MaterialRequisitionItemMapper materialRequisitionItemMapper;
    private final com.erp.inventory.mapper.OtherReceiptItemMapper otherReceiptItemMapper;
    private final com.erp.inventory.mapper.StockTransferItemMapper stockTransferItemMapper;
    private final com.erp.inventory.mapper.StockCheckItemMapper stockCheckItemMapper;
    private final com.erp.production.mapper.WorkOrderMaterialMapper workOrderMaterialMapper;
    private final com.erp.production.mapper.ProductionReceiptItemMapper productionReceiptItemMapper;
    private final com.erp.subcontract.order.mapper.SubcontractOrderItemMapper subcontractOrderItemMapper;
    private final com.erp.subcontract.pick.mapper.SubcontractPickItemMapper subcontractPickItemMapper;
    private final com.erp.subcontract.inbound.mapper.SubcontractInboundItemMapper subcontractInboundItemMapper;
    private final com.erp.subcontract.returnn.mapper.SubcontractReturnItemMapper subcontractReturnItemMapper;
    private final com.erp.subcontract.supplement.mapper.SubcontractSupplementItemMapper subcontractSupplementItemMapper;
    // 2026-09-20 新增：组装单/拆卸单明细 Mapper（补上"前端在调、后端无导出"的断链）
    private final com.erp.produce.assemble.mapper.ProduceAssembleDetailMapper produceAssembleDetailMapper;
    private final com.erp.produce.assemble.mapper.ProduceDisassembleDetailMapper produceDisassembleDetailMapper;
    // 2026-11-19 新增：销售换货单 / 售后维修单明细 Mapper（导出带明细行）
    private final com.erp.sale.mapper.SaleExchangeItemMapper saleExchangeItemMapper;
    private final com.erp.sale.mapper.SaleRepairItemMapper saleRepairItemMapper;
    // 售后维修单第二张从表：配件领用（2026-11-19 用户口径：配件行必须纳入导出）
    private final com.erp.sale.mapper.SaleRepairPartMapper saleRepairPartMapper;
    // 售后维修单导入保存（复用页面同一条 saveRepairWithItems 链路）
    private final com.erp.sale.service.SaleRepairService saleRepairService;
    // 打样单 / 免费样品订单导入保存（2026-11-27 补导入导出）
    private final com.erp.sample.service.SampleOrderService sampleOrderService;
    private final com.erp.freesample.service.FreeSampleOrderService freeSampleOrderService;

    /** 按登记表声明的 mapperClass 解析 Mapper bean（见 getMapper），避免再手写实体→Mapper 清单 */
    private final org.springframework.context.ApplicationContext applicationContext;

    /** 解析自定义字段 select 选项 JSON 用 */
    private static final com.fasterxml.jackson.databind.ObjectMapper CUSTOM_FIELD_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private static final Map<String, Class<?>> ENTITY_CLASS_MAP = new HashMap<>();

    /**
     * 实体名 → 导出取数 Mapper 缓存（由登记表 mapperClass 经 Spring 容器解析，见 getMapper）。
     * 原先这里是空的 MAPPER_MAP + getMapper() 里 55 行手写 switch，与 ENTITY_CLASS_MAP 各自维护，
     * 结果出现"能查不能导"（stockTransfer 在 switch 里有、在导出表里没有）。
     */
    private final Map<String, BaseMapper<?>> mapperCache = new ConcurrentHashMap<>();

    /**
     * 导入实体 -> 单据编号规则 docType 映射。
     * 用于"设置中心开启自动编码后，导入模板编码列不带*且导入免校验、自动生成编码"：
     * 模板表头与导入校验均按 sys_serial_no_config.auto_generate 实时判断，
     * 未在此映射的实体保持原有必填行为不变。
     * 注：materialCategory 的 BD_MATERIAL_CATEGORY 默认 auto_generate=0（分类编码是
     * 分类体系锚点需手填）；若用户在设置中心打开开关，导入模板会自动跟随。
     */
    private static final Map<String, String> AUTO_CODE_DOC_TYPE = new HashMap<>();

    static {
        AUTO_CODE_DOC_TYPE.put("material", "MD_MATERIAL");
        AUTO_CODE_DOC_TYPE.put("customer", "MD_CUSTOMER");
        AUTO_CODE_DOC_TYPE.put("supplier", "MD_SUPPLIER");
        AUTO_CODE_DOC_TYPE.put("bom", "MD_BOM");
        AUTO_CODE_DOC_TYPE.put("warehouse", "BD_WAREHOUSE");
        AUTO_CODE_DOC_TYPE.put("employee", "BD_EMPLOYEE");
        AUTO_CODE_DOC_TYPE.put("department", "BD_DEPARTMENT");
        AUTO_CODE_DOC_TYPE.put("currency", "BD_CURRENCY");
        AUTO_CODE_DOC_TYPE.put("unit", "BD_UNIT");
        AUTO_CODE_DOC_TYPE.put("materialCategory", "BD_MATERIAL_CATEGORY");
        AUTO_CODE_DOC_TYPE.put("saleOrder", "SALE_ORDER");
        AUTO_CODE_DOC_TYPE.put("saleQuote", "SALE_QUOTE");
        AUTO_CODE_DOC_TYPE.put("saleContract", "SALE_CONTRACT");
        AUTO_CODE_DOC_TYPE.put("saleDelivery", "SALE_DELIVERY");
        AUTO_CODE_DOC_TYPE.put("saleReturn", "SALE_RETURN");
        AUTO_CODE_DOC_TYPE.put("saleInvoice", "SALE_INVOICE");
        AUTO_CODE_DOC_TYPE.put("purchaseRequest", "PURCHASE_REQUEST");
        AUTO_CODE_DOC_TYPE.put("purchaseOrder", "PURCHASE_ORDER");
        AUTO_CODE_DOC_TYPE.put("purchaseContract", "PURCHASE_CONTRACT");
        AUTO_CODE_DOC_TYPE.put("purchaseInbound", "PURCHASE_INBOUND");
        AUTO_CODE_DOC_TYPE.put("purchaseReturn", "PURCHASE_RETURN");
        AUTO_CODE_DOC_TYPE.put("purchaseInvoice", "PURCHASE_INVOICE");
        AUTO_CODE_DOC_TYPE.put("otherReceipt", "OTHER_RECEIPT");
        AUTO_CODE_DOC_TYPE.put("otherIssue", "OTHER_ISSUE");
        AUTO_CODE_DOC_TYPE.put("stockTransfer", "STOCK_TRANSFER");
        AUTO_CODE_DOC_TYPE.put("stockCheck", "STOCK_CHECK");
        AUTO_CODE_DOC_TYPE.put("materialRequisition", "MATERIAL_REQUISITION");
        AUTO_CODE_DOC_TYPE.put("paymentVoucher", "PAYMENT_VOUCHER");
        AUTO_CODE_DOC_TYPE.put("paymentReceipt", "PAYMENT_RECEIPT");
        AUTO_CODE_DOC_TYPE.put("purchasePrePayment", "PURCHASE_PRE_PAYMENT");
        AUTO_CODE_DOC_TYPE.put("salePreReceipt", "SALE_PRE_RECEIPT");
        AUTO_CODE_DOC_TYPE.put("operationExpense", "OPERATION_EXPENSE");
        // 打样单 / 免费样品单（2026-11-27 补导入导出时漏登记）：两张单的"打样单号/订单号"列声明了
        // @ExportField(required=true)，不在此表登记 → 导入必填校验把单号当必填，模板里单号留空（正常用法，
        // 保存时由服务自动取号 SAM-/FS-）会整单失败；手填单号又绕过自动编号并在唯一索引上冲突。
        AUTO_CODE_DOC_TYPE.put("sampleOrder", "SAMPLE_ORDER");
        AUTO_CODE_DOC_TYPE.put("freeSampleOrder", "FREE_SAMPLE_ORDER");
    }

    /**
     * 导入时启用「删除即释放 + 补最小空洞」的基础资料实体白名单。
     *
     * <p>与界面保存同一口径：这些主档在各自 Service 里也都传了"业务表实况"探测回调，
     * 所以导入直插 mapper 时也必须按业务表实况取号，否则会出现"界面保存补洞、导入不补洞"的两套流水。</p>
     *
     * <p>单据类（saleOrder 等）<b>不在</b>此列：其界面保存仍走计数器续号，导入保持一致行为（改动前口径）。</p>
     *
     * <p>material 也不在此列 —— 它有自己的实现（{@code materialMapper.selectCodesByPrefix}），
     * 额外覆盖"规则无前缀、产出纯数字定长编码"的口径。</p>
     */
    private static final Set<String> BASEDATA_SEGMENT_ENTITIES = Set.of(
            "customer", "supplier", "bom", "warehouse", "employee", "department", "currency", "unit");

    static {
        // 【单一登记表】实体类由 BillExcelRegistry 派生。
        // 铁律：新增单据只允许改 BillExcelRegistry 一行，禁止再回来手写 put（历史上这里漏登记
        // 导致 stockTransfer/stockCheck/otherReceipt/委外5张 导出报"不支持的实体类型"）。
        for (com.erp.export.registry.BillExcelRegistry.Bill bill : com.erp.export.registry.BillExcelRegistry.all()) {
            ENTITY_CLASS_MAP.put(bill.code(), bill.entityClass());
        }
    }

    /** 按登记表的模板分组取 {实体名 → 实体类}（采购/库存/财务三个模板共用，替代原先三张手写 Map） */
    private static Map<String, Class<?>> entityClassesOfGroup(com.erp.export.registry.BillExcelRegistry.Group... groups) {
        java.util.Set<com.erp.export.registry.BillExcelRegistry.Group> wanted = new java.util.HashSet<>(Arrays.asList(groups));
        Map<String, Class<?>> m = new LinkedHashMap<>();
        for (com.erp.export.registry.BillExcelRegistry.Bill bill : com.erp.export.registry.BillExcelRegistry.all()) {
            if (wanted.contains(bill.group())) {
                m.put(bill.code(), bill.entityClass());
            }
        }
        return Collections.unmodifiableMap(m);
    }

    // ==================== 可导入实体白名单 / 模板列来源（全部由登记表派生） ====================
    //
    // 铁律：这里不再维护任何实体名清单。新增单据 → 只改 BillExcelRegistry 一行，
    // 实体类 / Mapper / 模板分组 / 可导可入 / 中文名 全部从登记表读。
    // 历史教训：SPECIAL_IMPORT_ENTITIES + 四个 *_TEMPLATE_ENTITIES + EXPORT_ONLY_ENTITIES 五张手写清单
    // 与 ENTITY_CLASS_MAP 互相漂移，才出现"补了销售订单、漏了委外/生产"。

    @Override
    public Set<String> getImportableEntities() {
        return com.erp.export.registry.BillExcelRegistry.importableCodes();
    }

    /**
     * 销售单据模板统一入口（登记表 Group.SALE 分派到这里）。
     * 销售 6 张单据的列定义本就各不相同（各自的 *_HEADER_BLACKLIST / *_ALWAYS_INCLUDE），
     * 这里只做"实体 → 列表列构造器"的唯一分派点；新增销售单据必须同时改这里 + 登记表。
     */
    @Override
    public void exportSaleBillTemplate(String entityName, List<String> fields, HttpServletResponse response) {
        switch (entityName) {
            case "saleQuote" -> exportSaleQuoteTemplate(fields, response);
            case "saleOrder" -> exportSaleOrderTemplate(fields, response);
            case "saleContract" -> exportSaleContractTemplate(fields, response);
            case "saleDelivery" -> exportSaleDeliveryTemplate(fields, response);
            case "saleReturn" -> exportSaleReturnTemplate(fields, response);
            case "saleInvoice" -> exportSaleInvoiceTemplate(fields, response);
            case "saleRepair" -> exportSaleRepairTemplate(fields, response);
            default -> throw new IllegalArgumentException("未登记的销售单据: " + entityName);
        }
    }

    /**
     * 按登记表分派的导入入口（替代 ExportController 里原先 24 个 else-if）。
     * 历史教训：那 24 个分支漏加一个，单据就会掉进"通用导入"只落表头、明细静默丢失。
     * 现在新增单据的导入实现只需在 {@link #importHandlers()} 加一行。
     */
    @Override
    public ImportResultDTO importByEntity(String entityName, MultipartFile file) {
        com.erp.export.registry.BillExcelRegistry.Bill bill = com.erp.export.registry.BillExcelRegistry.get(entityName);
        if (bill == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }
        if (!bill.importable()) {
            throw new IllegalArgumentException("该单据不支持导入: " + entityName);
        }
        java.util.function.Function<MultipartFile, ImportResultDTO> handler = importHandlers().get(entityName);
        // 未登记专用实现的实体走通用单表导入（与改动前 else 分支口径一致）
        return handler != null ? handler.apply(file) : importData(entityName, file);
    }

    @Override
    public ImportResultDTO importByEntity(String entityName, MultipartFile file, String billType) {
        // 生产退料/补料与生产领料共用 material_requisition 表：用 billType 区分，避免导入后退料/补料被写成领料单。
        if ("materialRequisition".equals(entityName) && billType != null && !billType.isBlank()) {
            com.erp.export.registry.BillExcelRegistry.Bill bill = com.erp.export.registry.BillExcelRegistry.get(entityName);
            if (bill == null || !bill.importable()) {
                throw new IllegalArgumentException("该单据不支持导入: " + entityName);
            }
            return importMaterialRequisitions(file, billType);
        }
        return importByEntity(entityName, file);
    }

    /** 实体名 → 专用导入实现的唯一登记处（24 条，与界面"导入"按钮一一对应） */
    private java.util.Map<String, java.util.function.Function<MultipartFile, ImportResultDTO>> importHandlers() {
        return java.util.Map.ofEntries(
                java.util.Map.entry("lead", this::importLeadsWithImages),
                java.util.Map.entry("material", this::importMaterialsWithImages),
                java.util.Map.entry("saleOrder", this::importSaleOrders),
                java.util.Map.entry("saleQuote", this::importSaleQuotes),
                java.util.Map.entry("saleContract", this::importSaleContracts),
                java.util.Map.entry("saleDelivery", this::importSaleDeliveries),
                java.util.Map.entry("saleReturn", this::importSaleReturns),
                java.util.Map.entry("saleInvoice", this::importSaleInvoices),
                java.util.Map.entry("saleRepair", this::importSaleRepairs),
                java.util.Map.entry("sampleOrder", this::importSampleOrders),
                java.util.Map.entry("freeSampleOrder", this::importFreeSampleOrders),
                java.util.Map.entry("purchaseRequest", this::importPurchaseRequests),
                java.util.Map.entry("purchaseOrder", this::importPurchaseOrders),
                java.util.Map.entry("purchaseContract", this::importPurchaseContracts),
                java.util.Map.entry("purchaseInbound", this::importPurchaseInbounds),
                java.util.Map.entry("purchaseReturn", this::importPurchaseReturns),
                java.util.Map.entry("purchaseInvoice", this::importPurchaseInvoices),
                java.util.Map.entry("otherReceipt", this::importOtherReceipts),
                java.util.Map.entry("otherIssue", this::importOtherIssues),
                java.util.Map.entry("stockTransfer", this::importStockTransfers),
                java.util.Map.entry("stockCheck", this::importStockChecks),
                java.util.Map.entry("materialRequisition", this::importMaterialRequisitions),
                java.util.Map.entry("paymentVoucher", this::importPaymentVouchers),
                java.util.Map.entry("paymentReceipt", this::importPaymentReceipts),
                java.util.Map.entry("purchasePrePayment", this::importPurchasePrePayments),
                java.util.Map.entry("salePreReceipt", this::importSalePreReceipts),
                java.util.Map.entry("operationExpense", this::importOperationExpenses),
                // 生产组装/拆卸：主表 + 明细，复用统一库存单据导入骨架
                java.util.Map.entry("produceAssemble", this::importProduceAssembles),
                java.util.Map.entry("produceDisassemble", this::importProduceDisassembles),
                // 价格策略：表头+明细结构，按「策略编码」分组导入（一行=一个对象的价格）
                java.util.Map.entry("salePriceStrategy", this::importSalePriceStrategies),
                java.util.Map.entry("purchasePriceStrategy", this::importPurchasePriceStrategies));
    }

    // ==================== 库存现存量（查询视图，不是单据）专用导出 ====================

    /** 库存现存量固定列（位置行；实体无 @ExportField，故不能用反射列） */
    private static final List<String> INVENTORY_CURRENT_LABELS = Arrays.asList(
            "仓库编码", "仓库名称", "物料编码", "物料名称", "规格型号", "单位",
            "批次号", "现存量", "可用量", "冻结量", "单位成本", "金额");

    /**
     * 库存现存量专用导出。
     * 关联口径与 {@code InventoryController#fillRelatedInfo} 一致：
     * 物料编码/名称/规格取 material、单位名取 material.baseUnitId → unit_of_measure、仓库编码/名称取 warehouse。
     */
    @Override
    public void exportInventoryCurrent(HttpServletResponse response, boolean templateOnly) {
        List<List<Object>> rows = new ArrayList<>();
        if (templateOnly) {
            rows.add(Arrays.asList("WH001", "主仓库", "M001", "示例物料", "规格A", "个", "B20260901",
                    100, 90, 10, new BigDecimal("12.5"), new BigDecimal("1250")));
        } else {
            List<com.erp.inventory.entity.Inventory> list = listInventoryCurrent();
            if (!list.isEmpty()) {
                Set<Long> materialIds = new java.util.LinkedHashSet<>();
                Set<Long> stockIds = new java.util.LinkedHashSet<>();
                for (com.erp.inventory.entity.Inventory inv : list) {
                    if (inv.getMaterialId() != null) materialIds.add(inv.getMaterialId());
                    if (inv.getStockId() != null) stockIds.add(inv.getStockId());
                }
                Map<Long, Material> materialMap = new HashMap<>();
                Set<Long> unitIds = new java.util.LinkedHashSet<>();
                if (!materialIds.isEmpty()) {
                    for (Material m : materialMapper.selectBatchIds(materialIds)) {
                        materialMap.put(m.getMaterialId(), m);
                        if (m.getBaseUnitId() != null) unitIds.add(m.getBaseUnitId());
                    }
                }
                Map<Long, com.erp.basedata.entity.Warehouse> warehouseMap = new HashMap<>();
                if (!stockIds.isEmpty()) {
                    for (com.erp.basedata.entity.Warehouse w : warehouseMapper.selectBatchIds(stockIds)) {
                        warehouseMap.put(w.getStockId(), w);
                    }
                }
                Map<Long, com.erp.basedata.entity.UnitOfMeasure> unitMap = new HashMap<>();
                if (!unitIds.isEmpty()) {
                    for (com.erp.basedata.entity.UnitOfMeasure u : unitOfMeasureMapper.selectBatchIds(unitIds)) {
                        unitMap.put(u.getUnitId(), u);
                    }
                }
                for (com.erp.inventory.entity.Inventory inv : list) {
                    Material m = materialMap.get(inv.getMaterialId());
                    com.erp.basedata.entity.Warehouse w = warehouseMap.get(inv.getStockId());
                    com.erp.basedata.entity.UnitOfMeasure u = (m != null && m.getBaseUnitId() != null)
                            ? unitMap.get(m.getBaseUnitId()) : null;
                    rows.add(Arrays.asList(
                            w != null && w.getStockCode() != null ? w.getStockCode() : "",
                            w != null && w.getStockName() != null ? w.getStockName() : "",
                            m != null && m.getMaterialCode() != null ? m.getMaterialCode() : "",
                            m != null && m.getMaterialName() != null ? m.getMaterialName() : "",
                            m != null && m.getModel() != null ? m.getModel() : "",
                            u != null && u.getUnitName() != null ? u.getUnitName() : "",
                            inv.getBatchNo() != null ? inv.getBatchNo() : "",
                            inv.getQuantity(),
                            inv.getAvailableQuantity(),
                            inv.getFrozenQuantity(),
                            inv.getUnitCost(),
                            inv.getTotalCost()));
                }
            }
        }
        writePositionalSheet(response, getExportFileName("inventory"),
                templateOnly ? "库存现存量模板" : "库存现存量", INVENTORY_CURRENT_LABELS, rows);
    }

    // ==================== 销售 / 采购价格策略（表头 + 明细）专用导出 / 导入 ====================
    // 为什么走专用行：价格策略是「表头 + 明细」结构、实体零个 @ExportField，且导出/模板/导入三处
    // 共用同一套扁平列（一行 = 一个对象的价格），通用反射导出只会得到空表。

    /** 导出/模板/导入三处共用的固定 13 列（顺序固定；一行 = 一个对象的价格） */
    private static final List<String> PRICE_STRATEGY_HEADERS = Arrays.asList(
            "策略编码", "适用范围", "对象编码", "对象名称", "数量从", "数量到",
            "价格类型", "价格值", "备注", "优先级", "状态", "生效日期", "失效日期");

    /**
     * 模板专用表头 = 规范列名 + 给「必填列」补 `*` 标记（用户口径 2026-10-10：模板必须一眼看出哪些必填）。
     *
     * <p>必填口径与导入校验逐字对齐（见 {@code parsePriceItem}）：适用范围 / 对象编码 / 价格类型 / 价格值。
     * 三处「不带 *」的理由：
     * <ul>
     *   <li>策略编码 —— 留空由系统自动生成，与物料档案「自动编号去 *」同口径；</li>
     *   <li>对象名称 —— 与「对象编码」二选一即可（编码匹配不到时按名称兜底），故只给编码打 *；</li>
     *   <li>数量从/数量到、备注、优先级、状态、生效/失效日期 —— 均可留空（有默认值或表示"不限"）。</li>
     * </ul>
     * 导出数据文件仍用不带 `*` 的 {@link #PRICE_STRATEGY_HEADERS}；导入时统一剥掉表头末尾的 `*` 再匹配列名，
     * 因此「模板填写件」与「导出文件」两种来源都能导。</p>
     */
    private static final List<String> PRICE_STRATEGY_TEMPLATE_HEADERS = PRICE_STRATEGY_HEADERS.stream()
            .map(h -> Arrays.asList("适用范围", "对象编码", "价格类型", "价格值").contains(h) ? h + "*" : h)
            .collect(java.util.stream.Collectors.toList());

    /** 适用范围：存储码 → 中文名（销售侧 material/category/customer，采购侧 material/category/supplier） */
    private static final Map<String, String> SALE_TARGET_TYPE_LABELS = Map.of(
            "material", "按物料", "category", "按商品分类", "customer", "按客户");
    private static final Map<String, String> PURCHASE_TARGET_TYPE_LABELS = Map.of(
            "material", "按物料", "category", "按商品分类", "supplier", "按供应商");

    /** 价格策略导入模板：13 列表头 + 2 行可自解释的中文演示数据 */
    @Override
    public void exportPriceStrategyTemplate(String entityName, HttpServletResponse response) {
        boolean sale = !"purchasePriceStrategy".equals(entityName);
        List<List<Object>> rows = new ArrayList<>();
        rows.add(Arrays.asList("SP202610100001", "按物料", "M001", "示例物料", "1", "100",
                "固定价", new BigDecimal("99.90"), "示例：1~100 数量内固定价；对象编码/名称至少填一个（编码优先）", "1", "启用",
                LocalDate.now().toString(), ""));
        rows.add(Arrays.asList("", sale ? "按商品分类" : "按供应商", sale ? "C001" : "S001",
                sale ? "示例分类" : "示例供应商", "", "", "折扣率", new BigDecimal("95"),
                "策略编码留空=自动生成；相同编码的多行归为同一个策略", "", "启用", "", ""));
        writePositionalSheet(response, getTemplateName(entityName), "价格策略模板", PRICE_STRATEGY_TEMPLATE_HEADERS, rows);
    }

    /** 价格策略数据导出：查询全部策略（含明细），一行一个对象，对象编码/名称取对象实际值（不是 ID） */
    @Override
    public void exportPriceStrategyData(String entityName, HttpServletResponse response) {
        boolean sale = !"purchasePriceStrategy".equals(entityName);
        Map<String, String> labelMap = sale ? SALE_TARGET_TYPE_LABELS : PURCHASE_TARGET_TYPE_LABELS;
        List<List<Object>> rows = new ArrayList<>();

        // 1) 取全部策略 + 明细，并按「对象类型」汇总对象ID（一次性批量查对象主档，避免 N+1）
        Map<Long, List<Object>> itemsByStrategy = new HashMap<>();
        List<Object> strategies = new ArrayList<>();
        Set<Long> materialIds = new LinkedHashSet<>();
        Set<Long> categoryIds = new LinkedHashSet<>();
        Set<Long> customerIds = new LinkedHashSet<>();
        Set<Long> supplierIds = new LinkedHashSet<>();
        if (sale) {
            List<com.erp.price.sale.entity.SalePriceStrategy> list = salePriceStrategyService.list();
            strategies.addAll(list);
            List<Long> ids = new ArrayList<>();
            for (com.erp.price.sale.entity.SalePriceStrategy s : list) if (s.getId() != null) ids.add(s.getId());
            if (!ids.isEmpty()) {
                for (com.erp.price.sale.entity.SalePriceStrategyItem it : salePriceStrategyItemMapper.selectList(
                        new QueryWrapper<com.erp.price.sale.entity.SalePriceStrategyItem>().in("strategy_id", ids))) {
                    itemsByStrategy.computeIfAbsent(it.getStrategyId(), k -> new ArrayList<>()).add(it);
                    collectPriceTargetId(it.getTargetType(), it.getTargetId(), materialIds, categoryIds, customerIds, supplierIds);
                }
            }
        } else {
            List<com.erp.price.purchase.entity.PurchasePriceStrategy> list = purchasePriceStrategyService.list();
            strategies.addAll(list);
            List<Long> ids = new ArrayList<>();
            for (com.erp.price.purchase.entity.PurchasePriceStrategy s : list) if (s.getId() != null) ids.add(s.getId());
            if (!ids.isEmpty()) {
                for (com.erp.price.purchase.entity.PurchasePriceStrategyItem it : purchasePriceStrategyItemMapper.selectList(
                        new QueryWrapper<com.erp.price.purchase.entity.PurchasePriceStrategyItem>().in("strategy_id", ids))) {
                    itemsByStrategy.computeIfAbsent(it.getStrategyId(), k -> new ArrayList<>()).add(it);
                    collectPriceTargetId(it.getTargetType(), it.getTargetId(), materialIds, categoryIds, customerIds, supplierIds);
                }
            }
        }
        Map<String, String[]> objectLabels = loadPriceTargetLabels(materialIds, categoryIds, customerIds, supplierIds);

        // 2) 每条明细输出一行（对象编码/名称取实际值）
        for (Object so : strategies) {
            if (sale) {
                com.erp.price.sale.entity.SalePriceStrategy s = (com.erp.price.sale.entity.SalePriceStrategy) so;
                for (Object io : itemsByStrategy.getOrDefault(s.getId(), Collections.emptyList())) {
                    com.erp.price.sale.entity.SalePriceStrategyItem it = (com.erp.price.sale.entity.SalePriceStrategyItem) io;
                    String tt = blankTo(it.getTargetType(), "material");
                    rows.add(priceStrategyRow(s.getStrategyCode(), labelMap.getOrDefault(tt, tt), tt, it.getTargetId(),
                            objectLabels, it.getMinQuantity(), it.getMaxQuantity(), it.getPriceType(), it.getPriceValue(),
                            it.getRemark(), s.getPriority(), s.getStatus(), s.getStartDate(), s.getEndDate()));
                }
            } else {
                com.erp.price.purchase.entity.PurchasePriceStrategy s = (com.erp.price.purchase.entity.PurchasePriceStrategy) so;
                for (Object io : itemsByStrategy.getOrDefault(s.getId(), Collections.emptyList())) {
                    com.erp.price.purchase.entity.PurchasePriceStrategyItem it = (com.erp.price.purchase.entity.PurchasePriceStrategyItem) io;
                    String tt = blankTo(it.getTargetType(), "material");
                    rows.add(priceStrategyRow(s.getStrategyCode(), labelMap.getOrDefault(tt, tt), tt, it.getTargetId(),
                            objectLabels, it.getMinQuantity(), it.getMaxQuantity(), it.getPriceType(), it.getPriceValue(),
                            it.getRemark(), s.getPriority(), s.getStatus(), s.getStartDate(), s.getEndDate()));
                }
            }
        }
        writePositionalSheet(response, getExportFileName(entityName), "价格策略", PRICE_STRATEGY_HEADERS, rows);
    }

    @Override
    public ImportResultDTO importSalePriceStrategies(MultipartFile file) {
        return importPriceStrategies(file, true);
    }

    @Override
    public ImportResultDTO importPurchasePriceStrategies(MultipartFile file) {
        return importPriceStrategies(file, false);
    }

    /**
     * 价格策略导入：按「策略编码」分组（编码为空的行各自单独成组 = 各自一条新策略），
     * 同一组的多行 = 同一策略的多条明细；落库复用现有 save/update（不自己写 SQL）。
     */
    private ImportResultDTO importPriceStrategies(MultipartFile file, boolean sale) {
        ImportResultDTO result = new ImportResultDTO();
        List<List<Object>> excelData;
        try (InputStream in = file.getInputStream()) {
            excelData = convertExcelRowsToList(EasyExcel.read(in).headRowNumber(0).sheet().doReadSync());
        } catch (IOException e) {
            throw new RuntimeException("导入文件读取失败：" + e.getMessage(), e);
        }
        if (excelData.isEmpty()) {
            result.setTotalCount(0);
            return result;
        }
        // 首行按列名精确匹配建列索引
        List<String> headers = convertToStringList(excelData.get(0));
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i) == null ? "" : headers.get(i).trim();
            // 模板的必填列表头带 * 标记（如「适用范围*」），导出文件表头不带 *；
            // 这里统一剥掉末尾的 * 再匹配列名，保证「模板填写件」与「导出文件」两种来源都能导。
            while (h.endsWith("*")) h = h.substring(0, h.length() - 1).trim();
            if (!h.isEmpty() && !col.containsKey(h)) col.put(h, i);
        }
        // 缺列校验：适用范围 / 对象（编码或名称）/ 价格类型 / 价格值
        List<String> missing = new ArrayList<>();
        for (String need : new String[]{"适用范围", "价格类型", "价格值"}) {
            if (!col.containsKey(need)) missing.add(need);
        }
        if (!col.containsKey("对象编码") && !col.containsKey("对象名称")) missing.add("对象编码 或 对象名称");
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("模板缺少必要列：" + String.join("、", missing) + "，请下载最新模板填写");
        }

        // 分组：key=策略编码（空编码用唯一 key 单独成组）；同时记录每行原始数据（1-based 真实行号）
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        Map<String, String> groupCode = new HashMap<>();
        Map<Integer, List<Object>> rowByIndex = new LinkedHashMap<>();
        int total = 0;
        for (int i = 1; i < excelData.size(); i++) {
            List<Object> row = excelData.get(i);
            if (isRowEmpty(row)) continue;
            total++;
            rowByIndex.put(i + 1, row);
            String code = cell(row, col, "策略编码");
            String key = code.isEmpty() ? "\u0000NEW#" + (i + 1) : code;
            groupCode.putIfAbsent(key, code);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(i + 1);
        }
        result.setTotalCount(total);
        if (total == 0) return result;

        for (Map.Entry<String, List<Integer>> entry : groups.entrySet()) {
            String code = groupCode.getOrDefault(entry.getKey(), "");
            List<Integer> rowNums = entry.getValue();
            // 组内适用范围：取第一个「有效值」的行；空白按「按物料」处理
            String groupTt = "material";
            for (Integer rn : rowNums) {
                String t = resolveTargetType(sale, cell(rowByIndex.get(rn), col, "适用范围"));
                if (t != null) { groupTt = t; break; }
            }
            // 表头级字段（优先级/状态/生效/失效）取该组第一行；解析失败只记该行错，不阻断其它行
            Integer priority = null;
            Integer status = null;
            LocalDate start = null;
            LocalDate end = null;
            int headRn = rowNums.get(0);
            try {
                List<Object> headRow = rowByIndex.get(headRn);
                priority = parsePricePriority(cell(headRow, col, "优先级"));
                status = resolvePriceStatus(cell(headRow, col, "状态"));
                start = parsePriceDate(cell(headRow, col, "生效日期"), headRn, "生效日期");
                end = parsePriceDate(cell(headRow, col, "失效日期"), headRn, "失效日期");
            } catch (PriceRowException ex) {
                result.addError(headRn, ex.field, ex.getMessage());
                result.setFailCount(result.getFailCount() + 1);
            }

            // 逐行解析明细（一行一个对象）
            List<ParsedPriceItem> parsedItems = new ArrayList<>();
            List<Integer> parsedRowNums = new ArrayList<>();
            for (Integer rn : rowNums) {
                try {
                    parsedItems.add(parsePriceItem(rowByIndex.get(rn), col, sale, groupTt));
                    parsedRowNums.add(rn);
                } catch (PriceRowException ex) {
                    result.addError(rn, ex.field, ex.getMessage());
                    result.setFailCount(result.getFailCount() + 1);
                }
            }
            if (parsedItems.isEmpty()) continue;

            // 保存：复用现有服务（校验/编码生成/明细落库都在服务里）
            try {
                if (sale) {
                    com.erp.price.sale.entity.SalePriceStrategy entity = new com.erp.price.sale.entity.SalePriceStrategy();
                    if (!code.isEmpty()) entity.setStrategyCode(code);
                    entity.setTargetType(groupTt);
                    entity.setPriority(priority);
                    entity.setStatus(status);
                    entity.setStartDate(start);
                    entity.setEndDate(end);
                    List<com.erp.price.sale.entity.SalePriceStrategyItem> items = new ArrayList<>();
                    for (ParsedPriceItem p : parsedItems) items.add(toSaleItem(p));
                    com.erp.price.sale.entity.SalePriceStrategy existing = code.isEmpty() ? null
                            : salePriceStrategyService.getOne(new QueryWrapper<com.erp.price.sale.entity.SalePriceStrategy>()
                                    .eq("strategy_code", code).last("LIMIT 1"));
                    if (existing != null) {
                        entity.setId(existing.getId());
                        salePriceStrategyService.update(entity, items);
                    } else {
                        salePriceStrategyService.save(entity, items);
                    }
                } else {
                    com.erp.price.purchase.entity.PurchasePriceStrategy entity = new com.erp.price.purchase.entity.PurchasePriceStrategy();
                    if (!code.isEmpty()) entity.setStrategyCode(code);
                    entity.setTargetType(groupTt);
                    entity.setPriority(priority);
                    entity.setStatus(status);
                    entity.setStartDate(start);
                    entity.setEndDate(end);
                    List<com.erp.price.purchase.entity.PurchasePriceStrategyItem> items = new ArrayList<>();
                    for (ParsedPriceItem p : parsedItems) items.add(toPurchaseItem(p));
                    com.erp.price.purchase.entity.PurchasePriceStrategy existing = code.isEmpty() ? null
                            : purchasePriceStrategyService.getOne(new QueryWrapper<com.erp.price.purchase.entity.PurchasePriceStrategy>()
                                    .eq("strategy_code", code).last("LIMIT 1"));
                    if (existing != null) {
                        entity.setId(existing.getId());
                        purchasePriceStrategyService.update(entity, items);
                    } else {
                        purchasePriceStrategyService.save(entity, items);
                    }
                }
                result.setSuccessCount(result.getSuccessCount() + parsedItems.size());
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                for (Integer rn : parsedRowNums) {
                    result.addError(rn, "", "保存失败：" + msg);
                    result.setFailCount(result.getFailCount() + 1);
                }
            }
        }
        return result;
    }

    /** 价格策略导入的明细载体（销售/采购字段一致，先解析到此，再转成对应实体，避免两套解析逻辑） */
    private static class ParsedPriceItem {
        String targetType;
        Long targetId;
        Long materialId;
        BigDecimal minQuantity;
        BigDecimal maxQuantity;
        String priceType;
        BigDecimal priceValue;
        String remark;
    }

    /** 价格策略导入的行级校验异常：带列名，便于 addError 定位 */
    private static class PriceRowException extends RuntimeException {
        final String field;
        PriceRowException(String field, String message) {
            super(message);
            this.field = field;
        }
    }

    private ParsedPriceItem parsePriceItem(List<Object> row, Map<String, Integer> col, boolean sale, String groupTt) {
        ParsedPriceItem p = new ParsedPriceItem();
        String ttRaw = cell(row, col, "适用范围");
        String tt = resolveTargetType(sale, ttRaw);
        if (tt == null) {
            throw new PriceRowException("适用范围", "适用范围只支持「"
                    + (sale ? "按物料 / 按商品分类 / 按客户" : "按物料 / 按商品分类 / 按供应商") + "」，当前值：" + ttRaw);
        }
        if (!tt.equals(groupTt)) {
            throw new PriceRowException("适用范围", "与同一策略编码下的其它行不一致（其它行为「"
                    + targetTypeLabel(sale, groupTt) + "」）");
        }
        p.targetType = tt;
        String objCode = cell(row, col, "对象编码");
        String objName = cell(row, col, "对象名称");
        if (objCode.isEmpty() && objName.isEmpty()) {
            throw new PriceRowException("对象编码", "对象编码/名称未填写");
        }
        Long id = resolvePriceTargetId(tt, objCode, objName);
        if (id == null) {
            throw new PriceRowException("对象编码", "对象[" + (objCode.isEmpty() ? objName : objCode) + "]在系统中不存在");
        }
        p.targetId = id;
        if ("material".equals(tt)) p.materialId = id;
        p.minQuantity = parsePriceQty(cell(row, col, "数量从"), "数量从");
        p.maxQuantity = parsePriceQty(cell(row, col, "数量到"), "数量到");
        p.priceType = parsePriceType(sale, tt, cell(row, col, "价格类型"));
        p.priceValue = parsePriceValue(cell(row, col, "价格值"));
        p.remark = cell(row, col, "备注");
        return p;
    }

    private com.erp.price.sale.entity.SalePriceStrategyItem toSaleItem(ParsedPriceItem p) {
        com.erp.price.sale.entity.SalePriceStrategyItem it = new com.erp.price.sale.entity.SalePriceStrategyItem();
        it.setTargetType(p.targetType);
        it.setTargetId(p.targetId);
        it.setMaterialId(p.materialId);
        it.setPriceType(p.priceType);
        it.setPriceValue(p.priceValue);
        it.setMinQuantity(p.minQuantity);
        it.setMaxQuantity(p.maxQuantity);
        it.setRemark(p.remark);
        return it;
    }

    private com.erp.price.purchase.entity.PurchasePriceStrategyItem toPurchaseItem(ParsedPriceItem p) {
        com.erp.price.purchase.entity.PurchasePriceStrategyItem it = new com.erp.price.purchase.entity.PurchasePriceStrategyItem();
        it.setTargetType(p.targetType);
        it.setTargetId(p.targetId);
        it.setMaterialId(p.materialId);
        it.setPriceType(p.priceType);
        it.setPriceValue(p.priceValue);
        it.setMinQuantity(p.minQuantity);
        it.setMaxQuantity(p.maxQuantity);
        it.setRemark(p.remark);
        return it;
    }

    /** 适用范围中文名/代码 → 存储码（空白 = 按物料；非法值 = null） */
    private static String resolveTargetType(boolean sale, String raw) {
        if (raw == null || raw.isBlank()) return "material";
        String v = raw.trim();
        if (!sale) {
            return switch (v) {
                case "按物料", "material" -> "material";
                case "按商品分类", "按分类", "category" -> "category";
                case "按供应商", "supplier" -> "supplier";
                default -> null;
            };
        }
        return switch (v) {
            case "按物料", "material" -> "material";
            case "按商品分类", "按分类", "category" -> "category";
            case "按客户", "customer" -> "customer";
            default -> null;
        };
    }

    private static String targetTypeLabel(boolean sale, String tt) {
        return (sale ? SALE_TARGET_TYPE_LABELS : PURCHASE_TARGET_TYPE_LABELS).getOrDefault(tt, tt);
    }

    /** 对象「编码优先、名称兜底」解析成 ID（对象主档里查不到返回 null） */
    private Long resolvePriceTargetId(String tt, String code, String name) {
        String c = code == null ? "" : code.trim();
        String n = name == null ? "" : name.trim();
        switch (tt) {
            case "category": {
                com.erp.basedata.entity.MaterialCategory o = null;
                if (!c.isEmpty()) o = materialCategoryMapper.selectOne(new QueryWrapper<com.erp.basedata.entity.MaterialCategory>()
                        .eq("category_code", c).last("LIMIT 1"));
                if (o == null && !n.isEmpty()) o = materialCategoryMapper.selectOne(new QueryWrapper<com.erp.basedata.entity.MaterialCategory>()
                        .eq("category_name", n).last("LIMIT 1"));
                return o == null ? null : o.getCategoryId();
            }
            case "customer": {
                Customer o = null;
                if (!c.isEmpty()) o = customerMapper.selectOne(new QueryWrapper<Customer>()
                        .eq("customer_code", c).last("LIMIT 1"));
                if (o == null && !n.isEmpty()) o = customerMapper.selectOne(new QueryWrapper<Customer>()
                        .eq("customer_name", n).last("LIMIT 1"));
                return o == null ? null : o.getCustomerId();
            }
            case "supplier": {
                com.erp.masterdata.entity.Supplier o = null;
                if (!c.isEmpty()) o = supplierMapper.selectOne(new QueryWrapper<com.erp.masterdata.entity.Supplier>()
                        .eq("supplier_code", c).last("LIMIT 1"));
                if (o == null && !n.isEmpty()) o = supplierMapper.selectOne(new QueryWrapper<com.erp.masterdata.entity.Supplier>()
                        .eq("supplier_name", n).last("LIMIT 1"));
                return o == null ? null : o.getSupplierId();
            }
            default: {
                Material o = null;
                if (!c.isEmpty()) o = materialMapper.selectOne(new QueryWrapper<Material>()
                        .eq("material_code", c).last("LIMIT 1"));
                if (o == null && !n.isEmpty()) o = materialMapper.selectOne(new QueryWrapper<Material>()
                        .eq("material_name", n).last("LIMIT 1"));
                return o == null ? null : o.getMaterialId();
            }
        }
    }

    /** 按对象类型汇总目标ID（导出时用于批量查对象编码/名称） */
    private static void collectPriceTargetId(String tt, Long id, Set<Long> materialIds, Set<Long> categoryIds,
                                             Set<Long> customerIds, Set<Long> supplierIds) {
        if (id == null) return;
        switch (blankTo(tt, "material")) {
            case "category" -> categoryIds.add(id);
            case "customer" -> customerIds.add(id);
            case "supplier" -> supplierIds.add(id);
            default -> materialIds.add(id);
        }
    }

    /** 批量查对象主档，返回 key=`对象类型#ID` → [编码, 名称]（按类型分键，避免不同类型 ID 相同时互相覆盖） */
    private Map<String, String[]> loadPriceTargetLabels(Set<Long> materialIds, Set<Long> categoryIds,
                                                        Set<Long> customerIds, Set<Long> supplierIds) {
        Map<String, String[]> map = new HashMap<>();
        if (!materialIds.isEmpty()) {
            for (Material m : materialMapper.selectBatchIds(materialIds)) {
                map.put("material#" + m.getMaterialId(), new String[]{m.getMaterialCode(), m.getMaterialName()});
            }
        }
        if (!categoryIds.isEmpty()) {
            for (com.erp.basedata.entity.MaterialCategory c : materialCategoryMapper.selectBatchIds(categoryIds)) {
                map.put("category#" + c.getCategoryId(), new String[]{c.getCategoryCode(), c.getCategoryName()});
            }
        }
        if (!customerIds.isEmpty()) {
            for (Customer c : customerMapper.selectBatchIds(customerIds)) {
                map.put("customer#" + c.getCustomerId(), new String[]{c.getCustomerCode(), c.getCustomerName()});
            }
        }
        if (!supplierIds.isEmpty()) {
            for (com.erp.masterdata.entity.Supplier s : supplierMapper.selectBatchIds(supplierIds)) {
                map.put("supplier#" + s.getSupplierId(), new String[]{s.getSupplierCode(), s.getSupplierName()});
            }
        }
        return map;
    }

    /** 组装一行价格策略导出数据（对象编码/名称取对象实际值；状态 1=启用/0=禁用） */
    private static List<Object> priceStrategyRow(String strategyCode, String targetTypeLabel, String tt, Long targetId,
                                                 Map<String, String[]> objectLabels, BigDecimal minQty, BigDecimal maxQty,
                                                 String priceType, BigDecimal priceValue, String remark, Integer priority,
                                                 Integer status, LocalDate startDate, LocalDate endDate) {
        String[] label = objectLabels.get(tt + "#" + targetId);
        return Arrays.asList(
                strategyCode == null ? "" : strategyCode,
                targetTypeLabel,
                label != null && label[0] != null ? label[0] : "",
                label != null && label[1] != null ? label[1] : "",
                minQty, maxQty,
                priceType == null ? "" : priceType,
                priceValue,
                remark == null ? "" : remark,
                priority,
                status != null && status == 1 ? "启用" : "禁用",
                startDate == null ? "" : startDate.toString(),
                endDate == null ? "" : endDate.toString());
    }

    private static String cell(List<Object> row, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        if (i == null || i >= row.size() || row.get(i) == null) return "";
        return String.valueOf(row.get(i)).trim();
    }

    private static String blankTo(String v, String dft) {
        return (v == null || v.isBlank()) ? dft : v;
    }

    private BigDecimal parsePriceQty(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new PriceRowException(field, "「" + field + "」不是有效数字：" + raw);
        }
    }

    private BigDecimal parsePriceValue(String raw) {
        if (raw == null || raw.isBlank()) throw new PriceRowException("价格值", "「价格值」不能为空");
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new PriceRowException("价格值", "「价格值」不是有效数字：" + raw);
        }
    }

    private Integer parsePricePriority(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.trim()).intValue();
        } catch (NumberFormatException e) {
            throw new PriceRowException("优先级", "「优先级」不是有效数字：" + raw);
        }
    }

    private Integer resolvePriceStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        if (v.equals("启用") || v.equals("1") || v.equalsIgnoreCase("true")) return 1;
        if (v.equals("禁用") || v.equals("停用") || v.equals("0") || v.equalsIgnoreCase("false")) return 0;
        throw new PriceRowException("状态", "「状态」只支持「启用/禁用」，当前值：" + raw);
    }

    private String parsePriceType(boolean sale, String tt, String raw) {
        if (raw == null || raw.isBlank()) throw new PriceRowException("价格类型", "「价格类型」不能为空");
        String v = raw.trim();
        String norm = switch (v) {
            case "固定价", "fixed" -> "固定价";
            case "折扣率", "discount" -> "折扣率";
            case "加价率", "markup" -> "加价率";
            case "毛利率", "gross_profit" -> "毛利率";
            default -> null;
        };
        if (norm == null) {
            throw new PriceRowException("价格类型", "「价格类型」只支持「"
                    + (sale ? "固定价 / 折扣率 / 加价率 / 毛利率" : "固定价 / 折扣率 / 加价率") + "」，当前值：" + raw);
        }
        if ("毛利率".equals(norm)) {
            if (!sale) throw new PriceRowException("价格类型", "采购价格策略不支持「毛利率」");
            if (!"material".equals(tt)) throw new PriceRowException("价格类型", "「毛利率」只在适用范围为「按物料」时可用");
        }
        return norm;
    }

    private LocalDate parsePriceDate(String raw, int rowIndex, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return com.erp.common.util.FlexibleDateUtils.parseLocalDate(raw.trim());
        } catch (Exception e) {
            throw new PriceRowException(field, "「" + field + "」日期无法识别：" + raw
                    + "，支持 " + com.erp.common.util.FlexibleDateUtils.supportedPatternsHint());
        }
    }

    /** 通用"按列写出"：列名/顺序完全由调用方给（即时库存导出与页面列保持一致用，见 ExcelService 注释） */
    @Override
    public void writeSheetByLabels(HttpServletResponse response, String fileName, String sheetName,
                                   List<String> labels, List<List<Object>> rows) {
        writePositionalSheet(response, fileName, sheetName,
                labels == null ? Collections.emptyList() : labels,
                rows == null ? Collections.emptyList() : rows);
    }

    /**
     * 「页面看到什么就导出什么」通用导出实现（全站统一口径，见 {@link ExcelService#exportVisibleColumns}）。
     *
     * <p>取值规则：静态列按**实体属性名**反射取值（有 @ExportField 按其日期/数字格式，无则按默认日期格式）；
     * 自定义列按 EAV 表（sys_custom_field_value）取值，与列表页同一个批量取数口径。
     * **明细列不输出** —— 列表页上根本看不到明细列，输出它就不叫"看到什么导什么"。</p>
     */
    @Override
    public void exportVisibleColumns(String entityName, List<Long> ids, List<Map<String, Object>> rows,
                                     List<Map<String, Object>> fields, HttpServletResponse response) {
        Class<?> entityClass = ENTITY_CLASS_MAP.get(entityName);
        if (entityClass == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }
        // 字段权限：导出写盘前剔除不可见列（与列表响应同一份口径；管理员为空集）
        Set<String> hiddenFields = hiddenKeysOf(entityClass);
        if (!hiddenFields.isEmpty() && fields != null && !fields.isEmpty()) {
            List<Map<String, Object>> visibleFields = new ArrayList<>();
            for (Map<String, Object> f : fields) {
                String key = f.get("key") == null ? "" : String.valueOf(f.get("key"));
                if (!isHiddenField(hiddenFields, key)) visibleFields.add(f);
            }
            fields = visibleFields;
        }
        if (fields == null || fields.isEmpty()) {
            // 未给列定义（老调用方）：退回既有全量/选中导出，保证兼容
            boolean noIds0 = (ids == null || ids.isEmpty());
            exportData(entityName, noIds0 ? listAllData(entityName) : listByIdsScoped(entityName, ids), response);
            return;
        }
        List<String> labels = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            labels.add(f.get("label") == null ? "" : String.valueOf(f.get("label")));
        }

        // ①（首选）直接用**页面手上的那批行数据**取值：页面显示什么就导出什么 ——
        //    实体上的展示字段（@TableField(exist=false)，如销售订单的"客户/销售员"）、
        //    前端别名列（如委外订单的 billNo/qty）、蛇形列（组装/拆卸单）都能原样带上。
        if (rows != null && !rows.isEmpty()) {
            List<List<Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                List<Object> line = new ArrayList<>();
                for (Map<String, Object> f : fields) {
                    line.add(cellFromRowData(row, f.get("key") == null ? "" : String.valueOf(f.get("key"))));
                }
                out.add(line);
            }
            writeSheetByLabels(response, getExportFileName(entityName), "数据", labels, out);
            return;
        }

        boolean noIds = (ids == null || ids.isEmpty());
        List<?> dataList = noIds ? listAllData(entityName) : listByIdsScoped(entityName, ids);

        // 属性名 → Field（同时拿到 @ExportField 用于格式化）
        Map<String, Field> fieldByName = new LinkedHashMap<>();
        for (Field f : entityClass.getDeclaredFields()) {
            f.setAccessible(true);
            fieldByName.put(f.getName(), f);
        }
        String pkName = resolvePrimaryKeyFieldName(entityClass);

        // 自定义字段定义 + 值（一次批量取；与列表页/模板同一个 EAV 口径）
        Map<String, com.erp.customfield.entity.CustomField> customDefs = new LinkedHashMap<>();
        for (com.erp.customfield.entity.CustomField cf
                : exportCustomFields(entityName, entityClass, getFieldLabels(entityClass))) {
            if (cf.getFieldKey() != null) customDefs.put(cf.getFieldKey(), cf);
        }
        List<Long> rowIds = new ArrayList<>();
        for (Object row : dataList) {
            Object v = readProperty(row, fieldByName.get(pkName));
            if (v instanceof Number) rowIds.add(((Number) v).longValue());
        }
        Map<Long, Map<String, Object>> customValues = batchGetCustomValues(entityName, rowIds);

        // labels 已在方法开头按请求列生成（避免重复声明）
        List<List<Object>> outRows = new ArrayList<>();
        Set<String> warned = new HashSet<>();
        for (Object item : dataList) {
            Object pk = readProperty(item, fieldByName.get(pkName));
            Map<String, Object> cfVals = (pk instanceof Number)
                    ? customValues.getOrDefault(((Number) pk).longValue(), Collections.emptyMap())
                    : Collections.emptyMap();
            List<Object> row = new ArrayList<>();
            for (Map<String, Object> f : fields) {
                String key = f.get("key") == null ? "" : String.valueOf(f.get("key"));
                boolean custom = Boolean.TRUE.equals(f.get("custom"));
                if (custom || !fieldByName.containsKey(key)) {
                    com.erp.customfield.entity.CustomField cf = customDefs.get(key);
                    Object raw = cfVals.get(key);
                    row.add(cf == null ? (raw == null ? "" : raw) : formatCustomValue(cf, raw));
                    if (!custom && !customDefs.containsKey(key) && warned.add(key)) {
                        log.warn("[导出] 页面列 {} 在实体 {} 上既非属性也非自定义字段，本次导出为空"
                                + "（需要导就补实体字段/自定义字段定义）", key, entityName);
                    }
                } else {
                    Field fld = fieldByName.get(key);
                    Object raw = readProperty(item, fld);
                    ExportField ann = fld.getAnnotation(ExportField.class);
                    row.add(ann != null ? formatValue(raw, ann) : formatDefaultValue(raw));
                }
            }
            outRows.add(row);
        }
        writeSheetByLabels(response, getExportFileName(entityName), "数据", labels, outRows);
    }

    /** 反射读属性（无该字段或异常时返回 null） */
    private Object readProperty(Object item, Field field) {
        if (field == null || item == null) return null;
        try {
            return field.get(item);
        } catch (Exception e) {
            return null;
        }
    }

    /** 页面行数据取单元格值：空值统一空白（不写占位符）；集合/对象按文本兜底 */
    private Object cellFromRowData(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        if (v == null) return "";
        if (v instanceof Number || v instanceof Boolean) return v;
        if (v instanceof java.util.Collection || v instanceof java.util.Map) return String.valueOf(v);
        return v;
    }

    /** 主键属性名（@TableId 所在字段；找不到回退 id） */
    private String resolvePrimaryKeyFieldName(Class<?> entityClass) {
        for (Field f : entityClass.getDeclaredFields()) {
            if (f.getAnnotation(com.baomidou.mybatisplus.annotation.TableId.class) != null) return f.getName();
        }
        return "id";
    }

    /** 无 @ExportField 标注属性的默认格式（日期/时间按页面显示口径） */
    private Object formatDefaultValue(Object value) {
        if (value == null) return "";
        if (value instanceof java.time.LocalDate) {
            return ((java.time.LocalDate) value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (value instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return value;
    }

    /** 按主键集合取行（套数据权限），供"导出页面当前筛选结果"用 */
    private List<?> listByIdsScoped(String entityName, List<Long> ids) {
        BaseMapper<?> mapper = getMapper(entityName);
        if (mapper == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }
        QueryWrapper<?> wrapper = buildExportWrapper(entityName);
        if (wrapper == null) wrapper = new QueryWrapper<>();
        wrapper.in(resolvePrimaryKeyColumn(entityName), ids);
        return ((BaseMapper) mapper).selectList(wrapper);
    }

    @SuppressWarnings("unchecked")
    private List<com.erp.inventory.entity.Inventory> listInventoryCurrent() {
        BaseMapper<?> mapper = getMapper("inventory");
        if (mapper == null) {
            throw new IllegalArgumentException("不支持的实体类型: inventory");
        }
        IPage<com.erp.inventory.entity.Inventory> page = new Page<>(1, 100000);
        // 仓库数据权限：与「即时库存」列表同口径（可见仓库集合由 DataScopeResolver 计算）。
        // 历史 BUG：这里原先也是全表取数 → 仓管员能导出所有仓库的现存量。
        QueryWrapper<com.erp.inventory.entity.Inventory> qw = new QueryWrapper<>();
        jakarta.servlet.http.HttpServletRequest req = currentRequest();
        if (req != null) {
            List<Long> visibleStockIds =
                    exportDataScopeResolver.visibleWarehouseIds(exportDataScopeResolver.resolve(req));
            if (visibleStockIds != null) {
                if (visibleStockIds.isEmpty()) {
                    // 无任何可见仓库：与列表「直接返回空页」一致
                    return Collections.emptyList();
                }
                qw.in("stock_id", visibleStockIds);
            }
        }
        return ((BaseMapper<com.erp.inventory.entity.Inventory>) mapper).selectPage(page, qw).getRecords();
    }

    /**
     * 位置行写盘（铁律：本项目 EasyExcel 写出必须用位置行 List&lt;List&lt;Object&gt;&gt;，禁用 Map + 动态表头）。
     *
     * <p>2026-10-06 新增：**列名含"图片"的列，由"地址文本"改为内嵌真实图片**（取不到图片时退回地址文本）。
     * 全站导出（列表页导出 / 批量导出选中 / 旧版固定列导出）都收敛到本出口，故一处改动全站一致。</p>
     */
    private void writePositionalSheet(HttpServletResponse response, String fileName, String sheetName,
                                      List<String> labels, List<List<Object>> rows) {
        // 字段权限统一出口：导出控制器已标记页面键时，写盘前按不可见列剔除（所有数据导出共用这一处）
        List<String> writeLabels = labels;
        List<List<Object>> writeRows = rows;
        try {
            String permissionPageKey = fieldPermissionOutputFilter.currentExportPageKey();
            if (permissionPageKey != null) {
                com.erp.permission.service.FieldPermissionOutputFilter.Layout layout =
                        fieldPermissionOutputFilter.filterColumns(permissionPageKey, labels, rows);
                writeLabels = layout.labels();
                writeRows = layout.rows();
            }
        } catch (Exception e) {
            log.warn("[导出] 字段权限剔列失败（按原列导出）: file={}, err={}", fileName, e.getMessage());
        }
        List<List<String>> headers = buildSingleLevelHeaders(writeLabels);
        try {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            EasyExcel.write(buffer)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(sheetName)
                    .head(headers)
                    .doWrite(writeRows);
            // 图片列：地址文本 → 内嵌真实图片（无图片列 / 取不到图片时原样返回）
            byte[] bytes = embedImageColumns(buffer.toByteArray(), writeLabels, writeRows);
            setResponseHeader(response, fileName);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("导出失败 file={}", fileName, e);
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
    }

    // ==================== 导出图片列：内嵌真实图片（2026-10-06） ====================

    /** 内嵌图片行的行高（磅）与图片列的列宽（字符）——共同决定图片在单元格里的可见大小 */
    private static final float EXPORT_IMAGE_ROW_HEIGHT_PT = 44f;
    private static final int EXPORT_IMAGE_COL_WIDTH_CHARS = 13;

    /**
     * 把 xlsx 字节里"图片列"的地址文本替换为内嵌真实图片。
     *
     * <p>识别口径：列名含"图片"（如物料档案的"图片"、销售线索的"图片"、物料导入/导出的"物料图片"）。
     * 单元格值是图片地址（本机上传图片 / 网络图片 / base64 内嵌图片，多张逗号分隔取第一张）；
     * **全部数据行有图就带图，不设数量上限**（允许慢，但不漏图）；取不到图片时**保留原地址文本**，
     * 绝不因此让导出报错或空表。</p>
     */
    private byte[] embedImageColumns(byte[] xlsx, List<String> labels, List<List<Object>> rows) {
        if (xlsx == null || xlsx.length == 0 || labels == null || labels.isEmpty()
                || rows == null || rows.isEmpty()) {
            return xlsx;
        }
        List<Integer> imageCols = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            if (label != null && label.contains("图片")) imageCols.add(i);
        }
        if (imageCols.isEmpty()) return xlsx;

        List<int[]> coords = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            if (row == null) continue;
            for (Integer c : imageCols) {
                if (c >= row.size()) continue;
                String url = firstImageUrl(row.get(c));
                if (url == null) continue;
                coords.add(new int[]{r + 1, c}); // 数据行下标 0 起 + 表头 1 行 = Excel 行号
                urls.add(url);
            }
        }
        if (coords.isEmpty()) return xlsx;
        return insertImagesIntoSheet(xlsx, coords, urls, imageCols);
    }

    /** 单元格值 → 第一个图片地址（多张逗号分隔取第一张；空/无值返回 null） */
    private String firstImageUrl(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) return null;
        // base64 内嵌图片自身含逗号（data:image/png;base64,xxxx），不能按逗号切
        if (s.startsWith("data:")) return s;
        for (String part : s.split("[,，]")) {
            String p = part.trim();
            if (!p.isEmpty()) return p;
        }
        return null;
    }

    /** 用 POI 在已生成的 xlsx 上按单元格坐标插入图片（并清掉原地址文本、撑开行高与列宽） */
    private byte[] insertImagesIntoSheet(byte[] xlsx, List<int[]> coords, List<String> urls,
                                         List<Integer> imageCols) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            int inserted = 0;
            for (int i = 0; i < coords.size(); i++) {
                byte[] data = loadImageBytes(urls.get(i));
                if (data == null || data.length == 0) continue;
                int pictureType = detectPictureType(data);
                if (pictureType < 0) continue; // 非 PNG/JPEG（如 GIF/BMP）暂不支持内嵌，保留地址文本
                int rowIdx = coords.get(i)[0];
                int colIdx = coords.get(i)[1];
                XSSFRow row = sheet.getRow(rowIdx);
                if (row == null) row = sheet.createRow(rowIdx);
                XSSFCell cell = row.getCell(colIdx);
                if (cell == null) cell = row.createCell(colIdx);
                cell.setBlank(); // 清掉地址文本，单元格只显示图片
                int pictureIdx = workbook.addPicture(data, pictureType);
                XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0,
                        colIdx, rowIdx, colIdx + 1, rowIdx + 1);
                anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
                drawing.createPicture(anchor, pictureIdx);
                row.setHeightInPoints(EXPORT_IMAGE_ROW_HEIGHT_PT);
                inserted++;
            }
            if (inserted == 0) return xlsx; // 一张都没取到 → 保持地址文本
            // 图片列列宽固定（否则会被地址文本长度撑得很宽）
            for (Integer c : imageCols) sheet.setColumnWidth(c, EXPORT_IMAGE_COL_WIDTH_CHARS * 256);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("[导出] 内嵌图片写入失败，退化为地址文本: {}", e.getMessage());
            return xlsx;
        }
    }

    /** 读取图片字节：本机上传图片（/uploads/）→ 网络图片（http/https）→ base64 内嵌图片；失败返回 null */
    private byte[] loadImageBytes(String url) {
        try {
            String u = url == null ? "" : url.trim();
            if (u.isEmpty()) return null;
            if (u.startsWith("data:image/")) {
                int comma = u.indexOf(',');
                if (comma < 0) return null;
                return java.util.Base64.getDecoder().decode(u.substring(comma + 1).replaceAll("\\s", ""));
            }
            if (u.startsWith("/uploads/")) {
                java.io.File file = new java.io.File(uploadPath, u.substring("/uploads/".length()));
                if (!file.isFile() || file.length() <= 0) return null;
                return java.nio.file.Files.readAllBytes(file.toPath());
            }
            if (u.startsWith("http://") || u.startsWith("https://")) {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "ERP-Export/1.0");
                try (java.io.InputStream in = conn.getInputStream()) {
                    byte[] data = in.readAllBytes();
                    return data.length == 0 ? null : data;
                } finally {
                    conn.disconnect();
                }
            }
        } catch (Exception e) {
            log.debug("[导出] 图片读取失败 url={}: {}", url, e.getMessage());
        }
        return null;
    }

    /** 按文件头识别图片格式（POI 只支持 PNG/JPEG 落表；其它格式返回 -1 = 不内嵌，保留地址文本） */
    private int detectPictureType(byte[] d) {
        if (d.length >= 4 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return Workbook.PICTURE_TYPE_PNG;
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return Workbook.PICTURE_TYPE_JPEG;
        }
        return -1;
    }

    @Override
    public List<Map<String, Object>> getImportColumns(String entityName) {
        if (entityName == null || !getImportableEntities().contains(entityName)) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }
        List<Map<String, Object>> columns = new ArrayList<>();
        // 模板列来源由登记表的 group 决定（原先靠四个手写 Set 判断，已删除）
        com.erp.export.registry.BillExcelRegistry.Group group =
                com.erp.export.registry.BillExcelRegistry.groupOf(entityName);
        if (group == com.erp.export.registry.BillExcelRegistry.Group.SALE) {
            appendTemplateColumns(columns, getSaleTemplateFields(entityName));
        } else if (group == com.erp.export.registry.BillExcelRegistry.Group.PURCHASE) {
            appendTemplateColumns(columns, getPurchaseTemplateFields(entityName));
        } else if (group == com.erp.export.registry.BillExcelRegistry.Group.INVENTORY) {
            appendTemplateColumns(columns, getInventoryTemplateFields(entityName));
        } else if (group == com.erp.export.registry.BillExcelRegistry.Group.FINANCE) {
            appendTemplateColumns(columns, getFinanceTemplateFields(entityName));
        } else {
            // 基础资料/其它实体：与 exportTemplate 同一来源（@ExportField 反射列 + 自定义字段列）
            Class<?> entityClass = ENTITY_CLASS_MAP.get(entityName);
            boolean autoCode = isAutoCodeEnabled(entityName);
            List<String> labels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
            labels.addAll(getCustomFieldLabels(getEnabledCustomFields(entityName), labels));
            for (int i = 0; i < labels.size(); i++) {
                String marked = labels.get(i);
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("label", stripRequiredMark(marked));
                col.put("required", marked.endsWith("*"));
                col.put("order", i);
                columns.add(col);
            }
        }
        return columns;
    }

    /** 把 {required, optional} 模板字段清单拍平成标准列（required 在前，optional 在后，均不含 *） */
    private void appendTemplateColumns(List<Map<String, Object>> columns, Map<String, List<String>> fields) {
        int order = 0;
        for (String label : fields.getOrDefault("required", List.of())) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("label", stripRequiredMark(label));
            col.put("required", true);
            col.put("order", order++);
            columns.add(col);
        }
        for (String label : fields.getOrDefault("optional", List.of())) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("label", stripRequiredMark(label));
            col.put("required", false);
            col.put("order", order++);
            columns.add(col);
        }
    }

    @Override
    public byte[] buildStandardExcel(String entityName, java.util.List<java.util.List<String>> rows) {
        List<String> labels = new ArrayList<>();
        for (Map<String, Object> col : getImportColumns(entityName)) {
            labels.add(String.valueOf(col.get("label")));
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        List<List<String>> dataRows = rows == null ? new ArrayList<>() : rows;
        EasyExcel.write(bos)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("导入")
                .head(buildSingleLevelHeaders(labels))
                .doWrite(dataRows);
        return bos.toByteArray();
    }

    @Override
    public ExportConfigDTO getExportConfig(String entityName) {
        Class<?> entityClass = ENTITY_CLASS_MAP.get(entityName);
        if (entityClass == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }

        ExportConfigDTO config = new ExportConfigDTO();
        config.setEntityName(entityName);
        config.setTemplateName(getTemplateName(entityName));
        List<ExportConfigDTO.FieldConfig> fields = new ArrayList<>(getFieldConfigs(entityClass));
        fields.addAll(getCustomFieldConfigs(entityName, entityClass, fields));
        config.setFields(fields);

        return config;
    }

    /**
     * 导出列选择弹窗用：把启用的自定义字段追加到静态列之后（fieldName = fieldKey，与导出口径一致）。
     * 基础资料导出此前只回 @ExportField 静态列，导致"自定义导出列"弹窗里看不到自定义字段。
     */
    private List<ExportConfigDTO.FieldConfig> getCustomFieldConfigs(String entityName, Class<?> entityClass,
                                                                    List<ExportConfigDTO.FieldConfig> staticConfigs) {
        Set<String> staticLabels = new HashSet<>();
        int maxOrder = 0;
        for (ExportConfigDTO.FieldConfig c : staticConfigs) {
            staticLabels.add(c.getLabel());
            maxOrder = Math.max(maxOrder, c.getOrder());
        }
        List<ExportConfigDTO.FieldConfig> result = new ArrayList<>();
        int seq = 1;
        for (com.erp.customfield.entity.CustomField cf : exportCustomFields(entityName, entityClass, staticLabels)) {
            ExportConfigDTO.FieldConfig cfg = new ExportConfigDTO.FieldConfig();
            cfg.setFieldName(cf.getFieldKey());
            cfg.setLabel(cf.getFieldLabel());
            cfg.setOrder(maxOrder + seq++);
            cfg.setRequired(cf.getIsRequired() != null && cf.getIsRequired() == 1);
            cfg.setHidden(false);
            result.add(cfg);
        }
        return result;
    }

    /**
     * 模板里追加明细列的实体（表头 + 明细单 sheet 导入）：组装/拆卸（2026-11-14），
     * 打样单/免费样品订单（2026-11-27 补导入导出）。其余单据的模板仍只出表头列。
     */
    private static final java.util.Set<String> GENERIC_ITEM_TEMPLATE_ENTITIES =
            java.util.Set.of("produceAssemble", "produceDisassemble", "sampleOrder", "freeSampleOrder");

    @Override
    public void exportTemplate(String entityName, HttpServletResponse response) {
        // BOM实体：统一用合并后的18列模板（支持一级+多级），不再反射Bom实体列生成30+废模板
        if ("bom".equalsIgnoreCase(entityName)) {
            exportBomTemplate(null, response);
            return;
        }
        // 销售报价单专用模板：主子表结构（表头列 + 明细列）
        if ("saleQuote".equalsIgnoreCase(entityName)) {
            exportSaleQuoteTemplate(null, response);
            return;
        }
        Class<?> entityClass = ENTITY_CLASS_MAP.get(entityName);
        if (entityClass == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }

        // 必填字段在列头加 * 标记；演示数据按表头标签顺序转成位置行，
        // 规避 EasyExcel 动态表头 + Map 数据按字段名取值失败导致演示行全空的缺陷
        // 开启自动编码的实体：编码列不标必填（导入时空编码自动生成）
        boolean autoCode = isAutoCodeEnabled(entityName);
        List<String> labels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        // 追加自定义字段列（设置中心定义，必填加*；排除formula只读计算列），
        // 演示数据行按标签取值缺失自动补空，无需改动 toPositionalRows
        labels.addAll(getCustomFieldLabels(getEnabledCustomFields(entityName), labels));
        // 2026-11-14：组装/拆卸等「反射表头 + 主子表导入」的单据，模板明细区必须有列。
        // 只在有专用导入实现的实体上追加（produceAssemble / produceDisassemble），
        // 避免给"没有主子表导入"的单据生成看得见却导不进的明细列。
        BillItemSpec importItemSpec = BILL_ITEM_SPECS.get(entityName);
        if (importItemSpec != null && GENERIC_ITEM_TEMPLATE_ENTITIES.contains(entityName)) {
            List<String> itemLabels = new ArrayList<>();
            for (BillItemColumn bc : importItemSpec.columns()) itemLabels.add(bc.label());
            appendAutoItemLabels(itemLabels, importItemSpec.itemClass());
            List<String> itemStatic = new ArrayList<>(labels);
            labels.addAll(itemLabels);
            labels.addAll(getCustomFieldLabels(getEnabledCustomFields(importItemSpec.customEntityType()), itemStatic));
            appendImportGroupColumn(labels);
        }
        // 设置中心「货品查重字段」：所选字段列头补 * 必填标记（物料模板；选项已限定为模板可见字段）
        // 注意：自动编码已生效时不能再给"物料编码"补 *，否则会把上面刚去掉的必填标记又加回来
        markDedupFieldsRequired(entityName, labels, autoCode);
        List<List<String>> headers = buildSingleLevelHeaders(labels);
        // 基础资料模板附带演示数据行，便于用户理解每列填什么
        List<List<Object>> data = toPositionalRows(getTemplateDemoData(entityName), labels);
        if (importItemSpec != null && labels.contains(IMPORT_GROUP_LABEL)) {
            int gi = labels.indexOf(IMPORT_GROUP_LABEL);
            for (List<Object> row : data) {
                while (row.size() <= gi) row.add("");
                row.set(gi, 1);
            }
        }

        String fileName = getTemplateName(entityName);
        try {
            // 先写入内存缓冲，成功后再设置响应头并输出。
            // 避免生成阶段异常时已提交 xlsx 响应头导致浏览器收到"200 + 0 字节"的空模板
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            EasyExcel.write(buffer)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("导入模板")
                    .head(headers)
                    .doWrite(data);
            setResponseHeader(response, fileName);
            response.getOutputStream().write(buffer.toByteArray());
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("导出模板失败 entity={}", entityName, e);
            throw new RuntimeException("导出模板失败: " + e.getMessage(), e);
        }
    }

    // ==================== 导入：模板「单据状态」列 + 提交分流（2026-09-19 用户拍板） ====================
    //
    // 口径（五分支，全部由「解析状态列 + 保存后按需提交」实现）：
    //   ① 审批未开启 + 状态列=已生效 → 等同界面「提交并生效」：置已生效 + 触发业务影响（扣库存/回写下游/生成应收）+ 严格必填校验
    //   ② 审批未开启 + 状态列=草稿   → 与旧版完全一致（存草稿）
    //   ③ 审批已开启 + 状态列=已生效 → 不允许直接生效：提交进审批流程（待审核 + 审批待办）
    //   ④ 审批已开启 + 状态列=草稿   → 尊重用户选择，仍存草稿
    //   ⑤ 审批已开启但无可用流程     → 该行导入失败：保存+提交同事务回滚，不留"报错但草稿已入库"的半成品
    //
    // 关键设计：①/③ 的「审批开关分流」不在这里判断——各单据 Service 的 submit/submitForApproval
    // 内部已按 ApprovalGateHelper.needApproval 分流（开=待审核+建待办 / 关=直接已生效+业务影响）。
    // 本类只负责「决定要不要提交」，避免把审批开关逻辑实现成第二份。

    /** 导入模板可选列：单据状态 */
    private static final String IMPORT_STATUS_LABEL = "单据状态";

    /** 单据状态列允许值（同时写成 Excel 数据有效性下拉） */
    private static final String[] IMPORT_STATUS_OPTIONS = {"草稿", "已生效"};

    /** 草稿态取值（空=草稿，与旧模板兼容：老模板没有该列时取到 null） */
    private static final Set<String> IMPORT_STATUS_DRAFT_WORDS = Set.of("", "草稿", "未审核", "draft", "DRAFT");
    /** 生效态取值（含历史写法：终态改名前的「已审核」必须仍被接受，否则客户手里的老模板会整批报错） */
    private static final Set<String> IMPORT_STATUS_EFFECTIVE_WORDS = Set.of(
            "已生效", "生效", "已审核", "effective", "EFFECTIVE", "approved", "APPROVED");

    /** 状态列解析结果 */
    private enum ImportBillStatus { DRAFT, EFFECTIVE }

    /**
     * 解析导入模板「单据状态」列（HashMap 形态，单据导入用）。
     * 空/草稿 → DRAFT；已生效/已审核/生效 → EFFECTIVE；其它值 → 抛异常（该行导入失败并提示可填值）。
     */
    private ImportBillStatus parseImportStatus(Map<String, String> cells) {
        return parseImportStatusValue(cells.get(IMPORT_STATUS_LABEL));
    }

    /**
     * 【通用件】按原始单元格文本解析「单据状态」。
     * 两种入口共用（HashMap 形态 / 位置行形态如 BOM），保证全系统只有一份取值口径。
     */
    private ImportBillStatus parseImportStatusValue(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (IMPORT_STATUS_DRAFT_WORDS.contains(v)) return ImportBillStatus.DRAFT;
        if (IMPORT_STATUS_EFFECTIVE_WORDS.contains(v)) return ImportBillStatus.EFFECTIVE;
        throw new IllegalArgumentException("「" + IMPORT_STATUS_LABEL + "」只能填 "
                + String.join(" / ", IMPORT_STATUS_OPTIONS) + "（留空=草稿），当前填的是[" + v + "]");
    }

    /** snake_case → lowerCamelCase（sale_pre_receipt → salePreReceipt）；导入框架传 snake，登记表按 camel 写 */
    private static String snakeToCamel(String s) {
        if (s == null || s.indexOf('_') < 0) return s;
        String[] parts = s.split("_");
        StringBuilder sb = new StringBuilder(parts.length > 0 ? parts[0] : "");
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    /**
     * 取该单据的「提交」动作（entityName → 各单据 Service 的 submit/submitForApproval）。
     * 未登记（基础资料等无审批链路的实体）返回 null → 该实体不允许导入为「已生效」。
     */
    private java.util.function.Consumer<Long> importBillSubmitter(String entityName, String submitter) {
        final String op = (submitter == null || submitter.isBlank()) ? "system" : submitter;
        switch (snakeToCamel(entityName)) {
            case "saleOrder": return saleOrderService::submitForApproval;
            case "saleQuote": return saleQuoteService::submitForApproval;
            case "saleContract": return id -> saleContractService.submitForApproval(id, op);
            case "saleDelivery": return saleDeliveryService::submitForApproval;
            case "saleReturn": return saleReturnService::submitForApproval;
            case "saleInvoice": return saleInvoiceService::submitForApproval;
            case "saleRepair": return saleRepairService::submitForApproval;
            case "purchaseRequest": return id -> purchaseRequestService.submit(id, op);
            case "purchaseOrder": return id -> purchaseOrderService.submit(id, op);
            case "purchaseContract": return id -> purchaseContractService.submit(id, op);
            case "purchaseInbound": return id -> purchaseInboundService.submit(id, op);
            case "purchaseReturn": return id -> purchaseReturnService.submit(id, op);
            case "purchaseInvoice": return id -> purchaseInvoiceService.submit(id, op);
            case "otherReceipt": return otherReceiptService::submitForApproval;
            case "otherIssue": return otherIssueService::submitForApproval;
            case "stockTransfer": return stockTransferService::submitForApproval;
            case "stockCheck": return stockCheckService::submitForApproval;
            case "materialRequisition": return materialRequisitionService::submitForApproval;
            case "paymentVoucher": return id -> financeService.submitVoucher(id, op);
            case "paymentReceipt": return id -> financeService.submitReceipt(id, op);
            case "purchasePrePayment": return purchasePrePaymentService::submit;
            case "salePreReceipt": return salePreReceiptService::submit;
            case "operationExpense": return id -> operationExpenseService.submit(id, op);
            case "bom": return id -> bomService.submit(id, op);   // BOM 树形导入共用同一套「单据状态」分流
            default: return null;
        }
    }

    /** 统一解析"要不要提交"：状态列=已生效 且 该单据有提交链路 → 返回提交动作；否则 null（存草稿） */
    private java.util.function.Consumer<Long> resolveImportSubmitter(String entityName, ImportBillStatus want) {
        if (want != ImportBillStatus.EFFECTIVE) return null;
        java.util.function.Consumer<Long> submitter =
                importBillSubmitter(entityName, com.erp.common.bill.BillApprovalAdapter.currentSubmitter());
        if (submitter == null) {
            throw new IllegalArgumentException("该单据（" + entityName
                    + "）没有提交/审批链路，不支持导入为「已生效」，请把状态列填「草稿」");
        }
        return submitter;
    }

    /**
     * 保存导入的单据，并按「单据状态」列决定是否立即提交（**同一事务**，失败整行回滚）。
     *
     * @param header     表头实体（保存后其主键被回填，供自定义字段回填使用）
     * @param items      明细
     * @param saver      各单据的保存动作（与旧版同一个 lambda，不变）
     * @param entityName 实体名
     * @param want       状态列解析结果
     * @param result     导入结果（累计"已提交"单数并写提示语）
     * @return 单据主键
     */
    private Long saveImportedBill(Object header, List<?> items, BiConsumer<Object, List<?>> saver,
                                  String entityName, ImportBillStatus want, ImportResultDTO result) {
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            applyDefaultCurrencyIfAbsent(header);
            saver.accept(header, items);
            Long id = extractEntityId(header);
            applyImportStatusAfterSave(entityName, id, want, result);
            return id;
        });
    }

    /**
     * 单表头单据版本：保存动作**返回保存后的实体**（主键从返回值取）。
     * 费用分摊单等"保存方法返回实体"的单据用这个重载，比自己反射入参更准。
     */
    private Long saveImportedBill(Object header, java.util.function.Function<Object, Object> saver,
                                  String entityName, ImportBillStatus want, ImportResultDTO result) {
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            applyDefaultCurrencyIfAbsent(header);
            Object saved = saver.apply(header);
            Long id = extractEntityId(saved != null ? saved : header);
            applyImportStatusAfterSave(entityName, id, want, result);
            return id;
        });
    }

    /**
     * 【通用件】保存之后按「单据状态」列决定是否提交 —— 所有导入实现共用（22 张单据 + BOM 树形导入）。
     * 取不到主键必须显式报错回滚，禁止静默按草稿放行（历史上静默降级会让"已生效"变成假草稿）。
     */
    private void applyImportStatusAfterSave(String entityName, Long id, ImportBillStatus want, ImportResultDTO result) {
        java.util.function.Consumer<Long> submitAction = resolveImportSubmitter(entityName, want);
        if (submitAction == null) return;
        if (id == null) {
            throw new IllegalStateException("无法解析单据主键，导入已回滚（" + entityName + "），请反馈该问题");
        }
        submitAction.accept(id);
        result.setEffectiveSubmittedCount(result.getEffectiveSubmittedCount() + 1);
        result.setMessage("其中 " + result.getEffectiveSubmittedCount()
                + " 单因状态列填「已生效」已自动提交（该单据审批开关开启时=已转入审批流程，关闭时=提交即生效）");
    }

    // ============ 通用件：明细行「按数据库列名赋值」反射器 ============
    //
    // 背景（2026-09-19 全量验证暴露的既有缺陷）：同一份导入明细模板要落到 20+ 张单据的明细实体上，
    // 而这些实体的 Java 属性名并不统一 —— 规格型号有的叫 specification、有的叫 model；
    // 数量有的叫 quantity、有的叫 applyQty（采购申请单）、checkQuantity（库存盘点单）。
    // 历史做法是"给不匹配的单据单独写一套专用构建器"（如已删除的 buildPurchaseRequestItem），
    // 违背"有通用能力就不允许独立写法"的规范。现统一改为按 @TableField 列名解析 setter，
    // 通用构建器即可覆盖全部单据，新增单据零改动。

    /** 明细"数量"在各单据里的实际列名（按顺序取第一个命中的） */
    private static final String[] QTY_COLUMNS = {"quantity", "apply_qty", "check_quantity", "req_quantity"};
    /** 明细"备注"在各单据里的实际列名 */
    private static final String[] REMARK_COLUMNS = {"remark", "row_remark"};
    /** 明细"日期"语义的候选列名（采购订单/合同=交货日期，采购申请单=需求日期，均按列名自动命中） */
    private static final String[] DATE_COLUMNS = {"delivery_date", "demand_date", "require_date"};

    // ==================== 导入模板「全字段」自动补列（2026-11-14） ====================
    //
    // 口径：模板明细列 = 各单据既有固定列 + 下面字典里登记的全部业务列（按列名去重追加）。
    // 系统以后新增业务字段时，只需在字典里补一行中文列名，模板自动出现该列；未登记的业务列在
    // 生成模板时打 WARN 日志（模板完整性自检），不会被静默漏掉。
    // 注意：物料/仓库/单位/货位这类需要按档案解析的引用列不在这里（由构建器单独解析），
    //       系统列（ID/时间/创建人/状态/回写累计量）也不在这里。

    /** 导入模板的「单据分组号」控制列：同一张单填相同序号；不填则整份文件按一张单处理 */
    private static final String IMPORT_GROUP_LABEL = "单据分组号";

    /** 明细业务列 → 模板中文列名（自动补列 + 导入落库共用同一份字典） */
    private static final Map<String, String> ITEM_COLUMN_LABELS = new LinkedHashMap<>();
    static {
        ITEM_COLUMN_LABELS.put("specification", "规格型号");
        ITEM_COLUMN_LABELS.put("quantity", "数量");
        ITEM_COLUMN_LABELS.put("unit_price", "单价");
        ITEM_COLUMN_LABELS.put("price_with_tax", "含税价");
        ITEM_COLUMN_LABELS.put("price_without_tax", "不含税价");
        ITEM_COLUMN_LABELS.put("tax_rate", "税率(%)");
        ITEM_COLUMN_LABELS.put("discount_rate", "折扣率(%)");
        ITEM_COLUMN_LABELS.put("discount_amount", "折扣额");
        ITEM_COLUMN_LABELS.put("amount", "金额");
        ITEM_COLUMN_LABELS.put("tax_amount", "税额");
        ITEM_COLUMN_LABELS.put("net_amount", "价税合计");
        ITEM_COLUMN_LABELS.put("avg_cost", "成本");
        ITEM_COLUMN_LABELS.put("batch_no", "批号");
        ITEM_COLUMN_LABELS.put("production_date", "生产日期");
        ITEM_COLUMN_LABELS.put("expiry_date", "有效期至");
        ITEM_COLUMN_LABELS.put("delivery_date", "交货日期");
        ITEM_COLUMN_LABELS.put("demand_date", "需求日期");
        ITEM_COLUMN_LABELS.put("qualified_quantity", "合格数量");
        ITEM_COLUMN_LABELS.put("rejected_quantity", "不合格数量");
        ITEM_COLUMN_LABELS.put("handling_type", "处理方式");
        ITEM_COLUMN_LABELS.put("remark", "明细备注");
        ITEM_COLUMN_LABELS.put("row_remark", "明细备注");
        ITEM_COLUMN_LABELS.put("work_order_line_no", "工单行号");
        ITEM_COLUMN_LABELS.put("process_no", "工序号");
        ITEM_COLUMN_LABELS.put("unit_cost", "单位成本");
        ITEM_COLUMN_LABELS.put("total_cost", "总成本");
        ITEM_COLUMN_LABELS.put("serial_no", "序列号");
        ITEM_COLUMN_LABELS.put("operation_report_no", "报工单号");
        ITEM_COLUMN_LABELS.put("check_result", "盘点结果");
        ITEM_COLUMN_LABELS.put("profit_loss_reason", "盈亏原因");
        ITEM_COLUMN_LABELS.put("reason", "差异原因");
        ITEM_COLUMN_LABELS.put("tax_type", "计税方式");
        ITEM_COLUMN_LABELS.put("tax_calc_method", "计税方式");
        ITEM_COLUMN_LABELS.put("tax_category", "税类别");
        ITEM_COLUMN_LABELS.put("notes", "明细备注");
        ITEM_COLUMN_LABELS.put("price", "单价");
        ITEM_COLUMN_LABELS.put("product_code", "物料编码");
        ITEM_COLUMN_LABELS.put("product_name", "物料名称");
        ITEM_COLUMN_LABELS.put("unit", "单位");
        ITEM_COLUMN_LABELS.put("basic_unit", "基本单位");
        ITEM_COLUMN_LABELS.put("project_code", "项目号");
        ITEM_COLUMN_LABELS.put("return_reason", "退货原因");
        ITEM_COLUMN_LABELS.put("delivery_place", "交货地点");
        ITEM_COLUMN_LABELS.put("is_free", "是否赠品");
        ITEM_COLUMN_LABELS.put("sort_no", "排序号");
        ITEM_COLUMN_LABELS.put("base_unit_price", "本位币单价");
        ITEM_COLUMN_LABELS.put("base_amount", "本位币金额");
        ITEM_COLUMN_LABELS.put("base_tax_amount", "明细本位币税额");
        ITEM_COLUMN_LABELS.put("base_net_amount", "本位币价税合计");
        ITEM_COLUMN_LABELS.put("scrap_warehouse_name", "报废仓库");
        ITEM_COLUMN_LABELS.put("suggested_supplier_name", "建议供应商");
        ITEM_COLUMN_LABELS.put("wh_name", "明细仓库");
        // 售后维修明细业务列（2026-11-19）：与 SALE_REPAIR_ITEM_LABELS 同源中文名
        ITEM_COLUMN_LABELS.put("fault_description", "故障描述");
        ITEM_COLUMN_LABELS.put("repair_plan", "维修方案");
        ITEM_COLUMN_LABELS.put("labor_hours", "工时");
    }

    /**
     * 明细业务列 → 中文列名（只读共享字典）。
     * 打印模板「可用字段」复用这一份，避免导入模板与打印字段各存一份中文名。
     */
    public static Map<String, String> itemColumnLabels() {
        return ITEM_COLUMN_LABELS;
    }

    /** 自动补列/自动落库时排除的系统列（ID、外键、审计、状态、下游回写累计量） */
    private static final Set<String> ITEM_AUTO_EXCLUDE = Set.of(
            "id", "material_id", "material_code", "material_name", "unit_id", "stock_id", "warehouse_id",
            "bin_id", "bin_name", "stock_name", "warehouse_name", "storage_name",
            "line_no", "created_at", "updated_at", "created_by", "updated_by", "ext_data",
            "source_id", "source_item_id", "source_doc_id", "source_type", "source_bill_type",
            "source_bill_no", "source_line_no", "source_row_no", "source_serial_no",
            "delivered_quantity", "received_amount", "returned_quantity",
            "turned_output_quantity", "turned_return_quantity", "turned_invoice_quantity",
            "turned_invoice_amount", "turned_produce_quantity", "turned_subcontract_quantity",
            "book_quantity", "book_amount", "check_quantity", "check_amount",
            "profit_quantity", "profit_amount", "loss_quantity", "loss_amount",
            "issued_quantity",
            "bom_id", "bom_code", "bom_version", "delivered_line_no", "detail_close_status",
            // 需要按档案解析的调拨明细引用列（由 applyItemExtraColumns 单独处理，不参与字典自动列）
            "from_stock_id", "to_stock_id", "from_bin_id", "to_bin_id",
            // 组装/拆卸旧结构明细的系统列（无导入入口，避免模板自检日志噪音）
            "source_operation_id", "scrap_quantity", "supply_type", "model_no", "unit_code", "sort_order",
            "parent_row_id", "parent_material_id", "parent_material_code", "parent_material_name",
            // 多单位折算/本位币折算/行号/来源勾稽等系统列（不参与模板列，避免用户填错）
            "base_quantity", "basic_quantity", "basic_unit_id", "conversion_rate",
            "float_unit_id", "float_quantity", "float_conversion_rate", "sub_unit_id",
            "old_basic_quantity", "row_id", "order_line_no", "delivery_line_no", "request_line_no",
            "delivery_days", "wh_id", "storage_id", "assemble_id", "disassemble_id", "product_id",
            "scrap_warehouse_id",
            // 下游回写累计量（只读，不允许导入覆盖）
            "approved_qty", "received_quantity", "invoiced_quantity",
            "turned_input_quantity", "turned_order_quantity",
            // 售后维修明细的保修状态由表头保修判定统一判定并回写（保存时 applyWarranty 覆盖），不进模板
            "warranty_status");

    /** 表头自动落库时排除的列（引用ID由构建器按档案解析，状态/审计由系统维护） */
    private static final Set<String> HEADER_AUTO_SKIP = Set.of(
            "customer_id", "supplier_id", "warehouse_id", "stock_id", "currency_id",
            "department_id", "dept_id", "saler_id", "buyer_id", "applicant_id", "handler_id",
            "employee_id", "payment_term_id", "price_level_id", "settle_mode_id", "account_id",
            "bank_account_id", "status", "approval_status", "close_status", "bus_status",
            "approved_by", "approved_at", "created_by", "created_at", "updated_by", "updated_at",
            "voided_by", "voided_at", "void_reason", "bill_before_status", "closed_by", "closed_at",
            "closed_reason", "attachment_urls", "org_code");

    /** 取实体字段对应的数据库列名（@TableField 优先，否则驼峰转下划线） */
    private static String columnNameOf(java.lang.reflect.Field f) {
        com.baomidou.mybatisplus.annotation.TableField tf =
                f.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
        return tf != null && !tf.value().isBlank() ? tf.value() : camelToUnderline(f.getName());
    }

    /** 遍历实体及其父类的全部字段（跳过静态字段） */
    private static List<java.lang.reflect.Field> allInstanceFields(Class<?> clazz) {
        List<java.lang.reflect.Field> out = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                out.add(f);
            }
        }
        return out;
    }

    /**
     * 明细模板自动补列：把字典里登记、但模板静态列还没有的业务字段追加到明细列末尾。
     * 只追加不改顺序，保证老用户模板/习惯不被打乱。
     */
    private void appendAutoItemLabels(List<String> labels, Class<?> itemClass) {
        if (itemClass == null) return;
        Set<String> existing = new java.util.HashSet<>();
        for (String l : labels) existing.add(stripRequiredMark(l));
        List<java.lang.reflect.Field> fields = allInstanceFields(itemClass);
        // 引用列（需要按档案解析，不在字典里）：明细仓库 / 单位 / 货位
        boolean hasStock = false, hasUnit = false, hasBin = false;
        boolean hasFromStock = false, hasToStock = false, hasFromBin = false, hasToBin = false;
        for (java.lang.reflect.Field f : fields) {
            String col = columnNameOf(f);
            if ("stock_id".equals(col) || "warehouse_id".equals(col)
                    || "wh_id".equals(col) || "wh_name".equals(col)) hasStock = true;
            if ("unit_id".equals(col)) hasUnit = true;
            if ("bin_id".equals(col) || "bin_name".equals(col)) hasBin = true;
            if ("from_stock_id".equals(col)) hasFromStock = true;
            if ("to_stock_id".equals(col)) hasToStock = true;
            if ("from_bin_id".equals(col)) hasFromBin = true;
            if ("to_bin_id".equals(col)) hasToBin = true;
        }
        if (hasStock && !existing.contains("明细仓库")) { labels.add("明细仓库"); existing.add("明细仓库"); }
        if (hasUnit && !existing.contains("单位")) { labels.add("单位"); existing.add("单位"); }
        if (hasBin && !existing.contains("库位名称") && !existing.contains("货位")) {
            labels.add("库位名称");
            existing.add("库位名称");
        }
        if (hasFromStock && !existing.contains("明细调出仓库")) { labels.add("明细调出仓库"); existing.add("明细调出仓库"); }
        if (hasToStock && !existing.contains("明细调入仓库")) { labels.add("明细调入仓库"); existing.add("明细调入仓库"); }
        if (hasFromBin && !existing.contains("调出储位")) { labels.add("调出储位"); existing.add("调出储位"); }
        if (hasToBin && !existing.contains("调入储位")) { labels.add("调入储位"); existing.add("调入储位"); }
        for (java.lang.reflect.Field f : fields) {
            String col = columnNameOf(f);
            if (ITEM_AUTO_EXCLUDE.contains(col)) continue;
            if (col.endsWith("_id")) continue; // 外键/主键列不直接进模板（引用列已在上方单独加名称列）
            String label = ITEM_COLUMN_LABELS.get(col);
            if (label == null) {
                // 业务字段没有登记中文列名：模板完整性自检告警（不自动加英文列，避免用户看不懂）
                log.warn("[导入模板] 明细字段未登记中文列名，未自动加入模板: {}.{}", itemClass.getSimpleName(), col);
                continue;
            }
            if (existing.contains(label)) continue;
            labels.add(label);
            existing.add(label);
        }
    }

    /** 模板追加「单据分组号」控制列（放在自定义字段之前，避免与自定义字段重名判断互相影响） */
    private void appendImportGroupColumn(List<String> labels) {
        if (!labels.contains(IMPORT_GROUP_LABEL)) labels.add(IMPORT_GROUP_LABEL);
    }

    /** 模板示例数据填「单据分组号」= 1（两行示例属于同一张单） */
    private void fillImportGroupDemo(List<List<Object>> data, List<String> labels) {
        for (List<Object> row : data) setDemoValue(row, labels, IMPORT_GROUP_LABEL, 1);
    }

    /** 行分组键：单号优先；无单号时用「单据分组号」。都为空返回空串（沿用上一张单） */
    private String importGroupKey(Map<String, String> cells, String noLabel) {
        String no = noLabel == null ? "" : safeTrim(cells.get(noLabel));
        if (!no.isEmpty()) return "NO:" + no;
        String g = safeTrim(cells.get(IMPORT_GROUP_LABEL));
        return g.isEmpty() ? "" : "G:" + g;
    }

    /** 客户键（销售单据无单号时的分组兜底：客户变了才开新单，同一客户重复填写不拆单） */
    private String importCustomerKey(Map<String, String> cells) {
        for (String k : new String[]{"客户ID", "客户编码", "客户名称"}) {
            String v = safeTrim(cells.get(k));
            if (!v.isEmpty()) return "C:" + v;
        }
        return "";
    }

    /** 供应商键（采购单据无单号时的分组兜底：供应商变了才开新单） */
    private String importSupplierKey(Map<String, String> cells) {
        for (String k : new String[]{"供应商ID", "供应商编码", "供应商名称"}) {
            String v = safeTrim(cells.get(k));
            if (!v.isEmpty()) return "S:" + v;
        }
        return "";
    }

    /**
     * 明细行「模板新增列」统一落库：明细仓库、单位、货位、税/折扣/金额、日期、质检列等。
     * 核心目的：凡是模板里出现的业务列，导入后都能落到明细实体上（不再"模板有列、导了没值"）。
     */
    private void applyItemExtraColumns(Object item, Map<String, String> cells,
                                       Map<String, com.erp.basedata.entity.Warehouse> whCache,
                                       Long headerWarehouseId, int row) {
        if (item == null || cells == null) return;
        // 1) 明细仓库：有值按名称解析；没值沿用表头仓库（其它入库/采购入库以明细仓库为提交口径）
        Long itemStockId = null;
        if (hasValue(cells, "明细仓库")) {
            com.erp.basedata.entity.Warehouse w = resolveWarehouseByName(cells, whCache, row, "明细仓库");
            itemStockId = w.getStockId();
            setByAnyColumn(item, itemStockId, "stock_id", "warehouse_id", "wh_id");
            setByAnyColumn(item, w.getStockName(), "stock_name", "warehouse_name", "wh_name");
        } else if (headerWarehouseId != null
                && getLongProperty(item, "stockId") == null
                && getLongProperty(item, "warehouseId") == null
                && getLongProperty(item, "whId") == null) {
            setByAnyColumn(item, headerWarehouseId, "stock_id", "warehouse_id", "wh_id");
            itemStockId = headerWarehouseId;
        }
        // 2) 单位：按名称/编码解析成单位ID；实体同时有 unit 字符串时一并落值
        if (hasValue(cells, "单位")) {
            String unitName = safeTrim(cells.get("单位"));
            setByColumn(item, "unit", unitName);
            Long unitId = resolveUnitIdByName(unitName);
            if (unitId != null) setByColumn(item, "unit_id", unitId);
        }
        // 3) 货位：按名称 + 仓库解析成货位ID（历史模板列叫「库位名称」，新叫「货位」都兼容）
        String binLabel = hasValue(cells, "库位名称") ? "库位名称" : (hasValue(cells, "货位") ? "货位" : null);
        if (binLabel != null) {
            Long useStockId = itemStockId != null ? itemStockId : getLongProperty(item, "stockId");
            com.erp.basedata.entity.Location loc = resolveLocationByName(useStockId, safeTrim(cells.get(binLabel)));
            if (loc != null) {
                setByColumn(item, "bin_id", loc.getBinId());
                setByColumn(item, "bin_name", loc.getBinName());
            }
        }
        // 4) 调拨单明细的调出/调入仓库与储位（明细级，不是表头）
        if (hasValue(cells, "明细调出仓库")) {
            com.erp.basedata.entity.Warehouse w = resolveWarehouseByName(cells, whCache, row, "明细调出仓库");
            setByColumn(item, "from_stock_id", w.getStockId());
        }
        if (hasValue(cells, "明细调入仓库")) {
            com.erp.basedata.entity.Warehouse w = resolveWarehouseByName(cells, whCache, row, "明细调入仓库");
            setByColumn(item, "to_stock_id", w.getStockId());
        }
        if (hasValue(cells, "调出储位")) {
            com.erp.basedata.entity.Location loc = resolveLocationByName(
                    getLongProperty(item, "fromStockId"), safeTrim(cells.get("调出储位")));
            if (loc != null) setByColumn(item, "from_bin_id", loc.getBinId());
        }
        if (hasValue(cells, "调入储位")) {
            com.erp.basedata.entity.Location loc = resolveLocationByName(
                    getLongProperty(item, "toStockId"), safeTrim(cells.get("调入储位")));
            if (loc != null) setByColumn(item, "to_bin_id", loc.getBinId());
        }
        // 5) 计税方式（文本 → 行级 tax_type：1 含税价 / 2 不含税价 / 3 不计税）
        if (hasValue(cells, "计税方式")) {
            String v = safeTrim(cells.get("计税方式"));
            int t = v.contains("不含税") ? 2 : (v.contains("含税") ? 1 : (v.contains("不计税") ? 3 : 0));
            if (t > 0) setByColumn(item, "tax_type", t);
        }
        // 6) 其余登记在字典里的业务列：按列名统一落值（金额/税/折扣/含税价/日期/质检列等）
        for (Map.Entry<String, String> e : ITEM_COLUMN_LABELS.entrySet()) {
            String col = e.getKey();
            String label = e.getValue();
            if (!hasValue(cells, label)) continue;
            if ("数量".equals(label)) continue; // 数量按各单据真实列名解析（quantity/apply_qty/check_quantity…）
            if ("计税方式".equals(label)) continue; // 文本已在上方转成 tax_type
            if ("明细备注".equals(label)) { setByAnyColumn(item, cells.get(label), REMARK_COLUMNS); continue; }
            if ("生产日期".equals(label) || "有效期至".equals(label)
                    || "交货日期".equals(label) || "需求日期".equals(label)) {
                setByColumn(item, col, parseDate(cells.get(label), row, label));
                continue;
            }
            setByColumn(item, col, cells.get(label));
        }
    }

    /** 表头「模板新增列」统一落库：凡是实体上有同名列且模板里有该标签的，统一按列名赋值 */
    private void applyGenericHeaderColumns(Object header, Map<String, String> cells) {
        if (header == null || cells == null) return;
        for (java.lang.reflect.Field f : allInstanceFields(header.getClass())) {
            com.erp.export.annotation.ExportField ann =
                    f.getAnnotation(com.erp.export.annotation.ExportField.class);
            if (ann == null || ann.hidden()) continue;
            String label = ann.label();
            if (!hasValue(cells, label)) continue;
            String col = columnNameOf(f);
            if (HEADER_AUTO_SKIP.contains(col)) continue;
            setByColumn(header, col, cells.get(label));
        }
    }

    /** 取实体上某个属性的 Long 值（反射；取不到返回 null） */
    private static Long getLongProperty(Object target, String prop) {
        if (target == null || prop == null || prop.isBlank()) return null;
        try {
            String getter = "get" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
            Object v = target.getClass().getMethod(getter).invoke(target);
            return v instanceof Number ? ((Number) v).longValue() : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 表头仓库：绝大多数单据叫 warehouseId，采购入库/退货等实体属性叫 stockId（列名仍是 warehouse_id） */
    private static Long getHeaderWarehouseId(Object header) {
        Long id = getLongProperty(header, "warehouseId");
        if (id == null) id = getLongProperty(header, "stockId");
        return id != null ? id : getLongProperty(header, "whId");
    }

    /** 单位名称/编码 → 单位ID（找不到返回 null，不阻断导入；与页面"单位不存在"口径一致由后续校验兜底） */
    private Long resolveUnitIdByName(String unitName) {
        if (unitName == null || unitName.isBlank()) return null;
        try {
            com.erp.basedata.entity.UnitOfMeasure u = unitOfMeasureMapper.selectOne(
                    new QueryWrapper<com.erp.basedata.entity.UnitOfMeasure>()
                            .and(w -> w.eq("unit_name", unitName).or().eq("unit_code", unitName))
                            .last("LIMIT 1"));
            return u != null ? u.getUnitId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 货位名称 + 仓库 → 货位（找不到返回 null，不阻断导入） */
    private com.erp.basedata.entity.Location resolveLocationByName(Long stockId, String binName) {
        if (binName == null || binName.isBlank()) return null;
        try {
            QueryWrapper<com.erp.basedata.entity.Location> qw =
                    new QueryWrapper<com.erp.basedata.entity.Location>().eq("bin_name", binName);
            if (stockId != null) qw.eq("stock_id", stockId);
            return locationMapper.selectOne(qw.last("LIMIT 1"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 按数据库列名给实体属性赋值：命中并赋值成功返回 true；找不到该列/类型不能转则 false（不抛异常） */
    private static boolean setByColumn(Object target, String column, Object value) {
        if (target == null || column == null || value == null) return false;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                String col = camelToUnderline(f.getName());
                com.baomidou.mybatisplus.annotation.TableField tf =
                        f.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
                if (tf != null && !tf.value().isBlank()) col = tf.value();
                if (!column.equalsIgnoreCase(col)) continue;
                String setter = "set" + Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1);
                for (java.lang.reflect.Method m : target.getClass().getMethods()) {
                    if (!m.getName().equals(setter) || m.getParameterCount() != 1) continue;
                    Object converted = convertValue(m.getParameterTypes()[0], value);
                    if (converted == null) return false;
                    try {
                        m.invoke(target, converted);
                        return true;
                    } catch (Exception ignore) {
                        return false;
                    }
                }
                return false;
            }
        }
        return false;
    }

    /** 依次尝试多个候选列名，命中第一个即返回（用于"同一语义在不同单据列名不同"的场景） */
    private static boolean setByAnyColumn(Object target, Object value, String... columns) {
        for (String col : columns) {
            if (setByColumn(target, col, value)) return true;
        }
        return false;
    }

    /** 值类型转换：把导入解析出来的 String/BigDecimal 适配到 setter 的入参类型 */
    private static Object convertValue(Class<?> type, Object value) {
        if (value == null) return null;
        if (type.isInstance(value)) return value;
        try {
            if (type == String.class) return value.toString();
            String s = value.toString().trim();
            if (s.isEmpty()) return null;
            if (type == BigDecimal.class) return new BigDecimal(s);
            if (type == Long.class) return new BigDecimal(s).longValue();
            if (type == Integer.class) return new BigDecimal(s).intValue();
            if (type == Short.class) return new BigDecimal(s).shortValue();
            if (type == Double.class) return new BigDecimal(s).doubleValue();
            if (type == java.math.BigInteger.class) return new BigDecimal(s).toBigInteger();
            // 2026-11-14：模板全字段落库需要覆盖日期/布尔列（此前只支持文本与数值，日期列会被静默跳过）
            if (type == java.time.LocalDate.class) return java.time.LocalDate.parse(s);
            if (type == Boolean.class || type == boolean.class) {
                return "1".equals(s) || "true".equalsIgnoreCase(s) || "是".equals(s) || "启用".equals(s);
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    // ============ 通用件：单据「业务必填列」的声明式映射（模板列 ↔ 表头字段） ============
    //
    // 背景（2026-09-19 全量验证暴露的既有缺陷）：有 4 张单据的"仓库"类业务必填字段对不上：
    //   采购入库单/采购退货单：模板里有「仓库名称」列，但导入器压根没做映射 → 提交时必报"仓库不能为空"；
    //   销售出库单/销售退货单：模板里连仓库列都没有 → 提交时必报"出库/退货仓库为空"。
    // 结果都是"导入成功、提交必失败"。此处统一用声明式配置解决；新增单据只需加一行配置，
    // 禁止再为单张单据在 headerBuilder 里写专用分支（违背"有通用能力就不允许独立写法"的规范）。

    /** 模板「仓库名称」类列 → 表头仓库ID字段（值先经仓库解析，再落 ID + 冗余名称列） */
    private static final Map<String, Map<String, String>> BILL_WAREHOUSE_COLUMNS = Map.of(
            "saleDelivery", Map.of("出库仓库名称", "warehouse_id"),
            "saleReturn", Map.of("退货仓库名称", "warehouse_id"),
            "purchaseInbound", Map.of("仓库名称", "warehouse_id"),
            "purchaseReturn", Map.of("仓库名称", "warehouse_id"));

    /** 模板普通业务列 → 表头字段（直接按列名落值） */
    private static final Map<String, Map<String, String>> BILL_EXTRA_HEADER_COLUMNS = Map.of(
            "purchaseReturn", Map.of("退货原因", "return_reason"),
            "purchaseInvoice", Map.of("供应商税号", "tax_no"));

    /** 模板生成侧需要补出来的业务列（与上面两张映射表的列名一一对应；模板里已存在的列无需在此重复） */
    private static final Map<String, List<String>> BILL_EXTRA_TEMPLATE_LABELS = Map.of(
            "saleDelivery", List.of("出库仓库名称"),
            "saleReturn", List.of("退货仓库名称"),
            "purchaseInbound", List.of("仓库名称"),
            "purchaseReturn", List.of("仓库名称", "退货原因"),
            "purchaseInvoice", List.of("供应商税号"));

    /** 把声明式的"业务必填列"按值落到表头实体（列没填则整列跳过，交给服务层原有校验报错） */
    private void applyDeclaredBillColumns(String entityName, Object header, Map<String, String> cells, int rowIndex) {
        Map<String, String> whCols = BILL_WAREHOUSE_COLUMNS.get(entityName);
        if (whCols != null) {
            Map<String, com.erp.basedata.entity.Warehouse> cache = new HashMap<>();
            for (Map.Entry<String, String> en : whCols.entrySet()) {
                if (!hasValue(cells, en.getKey())) continue;
                com.erp.basedata.entity.Warehouse w = resolveWarehouseByName(cells, cache, rowIndex, en.getKey());
                setByColumn(header, en.getValue(), w.getStockId());
                setByColumn(header, "stock_name", w.getStockName());
            }
        }
        Map<String, String> extra = BILL_EXTRA_HEADER_COLUMNS.get(entityName);
        if (extra != null) {
            for (Map.Entry<String, String> en : extra.entrySet()) {
                if (hasValue(cells, en.getKey())) setByColumn(header, en.getValue(), cells.get(en.getKey()));
            }
        }
    }

    /** 按声明式配置把"模板必须补出来的列"插进表头（仅模板生成侧用；未配置的实体无副作用） */
    private void appendDeclaredTemplateLabels(String entityName, List<String> labels) {
        for (String label : BILL_EXTRA_TEMPLATE_LABELS.getOrDefault(entityName, List.of())) {
            if (!labels.contains(label)) labels.add(label);
        }
    }

    /** 按「模板字段设置」勾选结果追加「单据状态」列（可选列；未勾选则模板不含该列，导入取不到值=草稿） */
    private void appendImportStatusColumn(List<String> labels, List<String> fields) {
        if (fields != null && fields.contains(IMPORT_STATUS_LABEL) && !labels.contains(IMPORT_STATUS_LABEL)) {
            labels.add(IMPORT_STATUS_LABEL);
        }
    }

    /**
     * 【与界面同口径的兜底】单据币种为空时补本位币。
     *
     * <p>为什么需要：币种列是多数单据的 NOT NULL 字段，界面新建单据时会自动带出默认币种
     * （见 PurchaseOrderEdit「设置默认币种（currency_id 是 NOT NULL 字段）」）；
     * 而导入模板里**没有币种列**（币种ID 在模板黑名单里），于是出现"导入草稿能过、
     * 导入「已生效」必在严格提交校验被『币种不能为空』拦下"（2026-09-19 采购订单实测）。
     *
     * <p>用反射处理，避免为 22 张单据逐个写 setter 分派；无币种字段的单据（库存/部分财务单）静默跳过。
     */
    private void applyDefaultCurrencyIfAbsent(Object header) {
        try {
            Object current = header.getClass().getMethod("getCurrencyId").invoke(header);
            if (current != null) return;
            com.erp.basedata.entity.Currency base = currencyMapper.selectOne(
                    new QueryWrapper<com.erp.basedata.entity.Currency>()
                            .eq("is_base_currency", 1).orderByAsc("currency_id").last("LIMIT 1"));
            if (base == null) return;
            header.getClass().getMethod("setCurrencyId", Long.class).invoke(header, base.getCurrencyId());
            try {
                header.getClass().getMethod("setExchangeRate", BigDecimal.class)
                        .invoke(header, base.getExchangeRate() != null ? base.getExchangeRate() : BigDecimal.ONE);
            } catch (NoSuchMethodException ignore) {
                // 该单据没有汇率字段 → 忽略
            }
        } catch (Exception ignore) {
            // 无 getCurrencyId/setCurrencyId 的单据 → 忽略
        }
    }

    /** 演示行填「草稿」：直观告诉用户该列可填什么（与旧版行为一致：不填也是草稿） */
    private void fillImportStatusDemo(List<List<Object>> data, List<String> labels) {
        for (List<Object> row : data) {
            setDemoValue(row, labels, IMPORT_STATUS_LABEL, "草稿");
        }
    }

    /**
     * 给模板的「单据状态」列加 Excel 下拉（数据有效性：草稿 / 已生效），仍允许手输其它值
     * （手输非法值在导入时会被拦下并提示可填值）。模板未勾选该列时 colIndex=-1，不注册。
     */
    private static class BillStatusDropDownHandler implements com.alibaba.excel.write.handler.SheetWriteHandler {
        private final int colIndex;
        /** 下拉生效区间：第 1 行是表头，数据从第 2 行给到 5000 行 */
        private static final int FIRST_ROW = 1;
        private static final int LAST_ROW = 5000;

        BillStatusDropDownHandler(int colIndex) {
            this.colIndex = colIndex;
        }

        @Override
        public void afterSheetCreate(com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder wbHolder,
                                     com.alibaba.excel.write.metadata.holder.WriteSheetHolder shHolder) {
            if (colIndex < 0 || shHolder.getSheet() == null) return;
            org.apache.poi.ss.usermodel.Sheet sheet = shHolder.getSheet();
            org.apache.poi.ss.usermodel.DataValidationHelper helper = sheet.getDataValidationHelper();
            org.apache.poi.ss.usermodel.DataValidationConstraint constraint =
                    helper.createExplicitListConstraint(IMPORT_STATUS_OPTIONS);
            org.apache.poi.ss.util.CellRangeAddressList range =
                    new org.apache.poi.ss.util.CellRangeAddressList(FIRST_ROW, LAST_ROW, colIndex, colIndex);
            org.apache.poi.ss.usermodel.DataValidation validation = helper.createValidation(constraint, range);
            validation.setShowErrorBox(true);
            validation.setSuppressDropDownArrow(true);   // XSSF：true 才显示下拉箭头
            validation.createErrorBox("单据状态取值无效", "只能填 草稿 或 已生效（留空=草稿）");
            validation.createPromptBox("单据状态", "草稿=导入后不生效；已生效=提交并生效（该单据开启审批时自动转入审批流程）");
            sheet.addValidationData(validation);
        }
    }

    // ==================== 销售订单导入模板（可配置字段 + 明细列） ====================

    /** 销售订单模板明细列（追加在表头列之后） */
    private static final List<String> SALE_ORDER_ITEM_LABELS = List.of(
            "物料编码*", "物料名称", "规格型号", "单位", "数量*", "单价", "税率(%)", "金额", "交货日期", "明细备注");

    /** 销售订单模板表头排除列（隐藏/审核流转/系统控制字段；客户ID与币种ID用编码列+默认值代替，不进模板） */
    private static final Set<String> SALE_ORDER_HEADER_BLACKLIST = Set.of(
            "订单ID", "状态", "审核状态", "审核人", "审核时间", "关闭状态", "关闭原因", "已收款",
            "源单据类型", "源单据号", "商机ID", "商机编号", "附件", "关闭人", "关闭时间",
            "客户ID", "币种ID");

    /** 模板始终必带的表头列（除实体必填列外追加的用户友好列） */
    private static final Set<String> SALE_ORDER_ALWAYS_INCLUDE = Set.of("客户编码", "客户名称", "订单日期");

    /**
     * 销售订单导入模板：必带列始终包含，可选列按用户勾选(fields)过滤，最后追加明细列。
     * fields 为 null/空 = 仅必带列 + 明细列（精简模板）。
     */
    @Override
    public void exportSaleOrderTemplate(List<String> fields, HttpServletResponse response) {
        Class<?> entityClass = com.erp.sale.entity.SaleOrder.class;
        boolean autoCode = isAutoCodeEnabled("saleOrder");
        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);

        List<String> labels = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (SALE_ORDER_HEADER_BLACKLIST.contains(raw)) continue;
            boolean required = label.endsWith("*") || SALE_ORDER_ALWAYS_INCLUDE.contains(raw);
            // 必带列必带；订单号在开启自动编码时不标*也不必带（留空自动生成）
            if ("订单号".equals(raw) && autoCode) continue;
            if (required || (fields != null && fields.contains(raw))) {
                labels.add(label);
            }
        }
        // 实体注解里没有"客户名称"列（是 exist=false 字段），手工补上
        if (!labels.contains("客户名称")) {
            labels.add(1, "客户名称");
        }
        appendImportStatusColumn(labels, fields);
        appendImportGroupColumn(labels);
        List<String> orderHeaderStatic = new ArrayList<>(labels);
        // 明细列 = 既有静态列 + 明细实体全部业务字段（新增字段自动追加，不改变既有列顺序）
        List<String> orderItemLabels = new ArrayList<>(SALE_ORDER_ITEM_LABELS);
        appendAutoItemLabels(orderItemLabels, IMPORT_ITEM_CLASS.get("saleOrder"));
        appendSaleCustomFieldLabels(labels, "sale_order", "sale_order_item", orderHeaderStatic, orderItemLabels);

        // 演示数据：1个订单2行明细（第二行表头列留空，示范"表头继承"写法）
        // 用 setDemoValue 按 label 设值，兼容自定义字段动态列
        List<List<Object>> data = new ArrayList<>();
        List<Object> row1 = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) row1.add("");
        setDemoValue(row1, labels, "订单日期*", LocalDate.now().toString());
        setDemoValue(row1, labels, "客户编码", "KH001");
        setDemoValue(row1, labels, "客户名称", "深圳市华强电子有限公司");
        setDemoValue(row1, labels, "联系人", "张三");
        setDemoValue(row1, labels, "物料编码*", "WL001");
        setDemoValue(row1, labels, "物料名称", "精密电阻10K");
        setDemoValue(row1, labels, "规格型号", "0603 1%");
        setDemoValue(row1, labels, "单位", "个");
        setDemoValue(row1, labels, "数量*", 1000);
        setDemoValue(row1, labels, "单价", 0.15);
        setDemoValue(row1, labels, "税率(%)", 13);
        setDemoValue(row1, labels, "交货日期", LocalDate.now().plusDays(30).toString());
        data.add(row1);
        // 第2行明细：表头列全部留空
        List<Object> row2 = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) row2.add("");
        setDemoValue(row2, labels, "物料编码*", "WL002");
        setDemoValue(row2, labels, "物料名称", "贴片电容100nF");
        setDemoValue(row2, labels, "规格型号", "0805 X7R");
        setDemoValue(row2, labels, "单位", "个");
        setDemoValue(row2, labels, "数量*", 2000);
        setDemoValue(row2, labels, "单价", 0.08);
        setDemoValue(row2, labels, "税率(%)", 13);
        setDemoValue(row2, labels, "明细备注", "第二行明细：表头留空自动沿用上行");
        data.add(row2);

        fillImportStatusDemo(data, labels);
        fillImportGroupDemo(data, labels);
        setResponseHeader(response, "销售订单导入模板.xlsx");
        try {
            EasyExcel.write(response.getOutputStream())
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .registerWriteHandler(new BillStatusDropDownHandler(labels.indexOf(IMPORT_STATUS_LABEL)))
                    .sheet("导入模板")
                    .head(buildSingleLevelHeaders(labels))
                    .doWrite(data);
        } catch (IOException e) {
            log.error("导出销售订单模板失败", e);
            throw new RuntimeException("导出销售订单模板失败", e);
        }
    }

    // ==================== 销售报价单导入模板（表头列 + 明细列） ====================

    /** 销售报价单模板明细列 */
    private static final List<String> SALE_QUOTE_ITEM_LABELS = List.of(
            "物料编码*", "物料名称", "规格型号", "单位", "数量*", "单价", "税率(%)", "金额", "交货日期", "明细备注");

    /** 销售报价单模板表头排除列 */
    private static final Set<String> SALE_QUOTE_HEADER_BLACKLIST = Set.of(
            "报价单ID", "状态", "审核状态", "审核人", "审核时间", "关闭状态", "关闭原因",
            "源单据号", "商机ID", "商机编号", "附件", "关闭人", "关闭时间",
            "客户ID", "币种ID", "总金额", "税额", "折扣额", "净额");

    /** 销售报价单模板必带列 */
    private static final Set<String> SALE_QUOTE_ALWAYS_INCLUDE = Set.of("客户编码", "客户名称", "报价日期");

    @Override
    public void exportSaleQuoteTemplate(List<String> fields, HttpServletResponse response) {
        Class<?> entityClass = com.erp.sale.entity.SaleQuote.class;
        boolean autoCode = isAutoCodeEnabled("saleQuote");
        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);

        List<String> labels = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (SALE_QUOTE_HEADER_BLACKLIST.contains(raw)) continue;
            boolean required = label.endsWith("*") || SALE_QUOTE_ALWAYS_INCLUDE.contains(raw);
            if ("报价单号".equals(raw) && autoCode) continue;
            if (required || (fields != null && fields.contains(raw))) {
                labels.add(label);
            }
        }
        if (!labels.contains("客户名称")) {
            labels.add(1, "客户名称");
        }
        appendImportStatusColumn(labels, fields);
        appendImportGroupColumn(labels);
        List<String> quoteHeaderStatic = new ArrayList<>(labels);
        List<String> quoteItemLabels = new ArrayList<>(SALE_QUOTE_ITEM_LABELS);
        appendAutoItemLabels(quoteItemLabels, IMPORT_ITEM_CLASS.get("saleQuote"));
        appendSaleCustomFieldLabels(labels, "sale_quote", "sale_quote_item", quoteHeaderStatic, quoteItemLabels);

        List<List<Object>> data = new ArrayList<>();
        List<Object> row1 = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) row1.add("");
        setDemoValue(row1, labels, "报价日期*", LocalDate.now().toString());
        setDemoValue(row1, labels, "客户编码", "KH001");
        setDemoValue(row1, labels, "客户名称", "深圳市华强电子有限公司");
        setDemoValue(row1, labels, "联系人", "张三");
        setDemoValue(row1, labels, "物料编码*", "WL001");
        setDemoValue(row1, labels, "物料名称", "精密电阻10K");
        setDemoValue(row1, labels, "规格型号", "0603 1%");
        setDemoValue(row1, labels, "单位", "个");
        setDemoValue(row1, labels, "数量*", 1000);
        setDemoValue(row1, labels, "单价", 0.15);
        setDemoValue(row1, labels, "税率(%)", 13);
        setDemoValue(row1, labels, "交货日期", LocalDate.now().plusDays(30).toString());
        data.add(row1);
        List<Object> row2 = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) row2.add("");
        setDemoValue(row2, labels, "物料编码*", "WL002");
        setDemoValue(row2, labels, "物料名称", "贴片电容100nF");
        setDemoValue(row2, labels, "规格型号", "0805 X7R");
        setDemoValue(row2, labels, "单位", "个");
        setDemoValue(row2, labels, "数量*", 2000);
        setDemoValue(row2, labels, "单价", 0.08);
        setDemoValue(row2, labels, "税率(%)", 13);
        data.add(row2);

        fillImportStatusDemo(data, labels);
        fillImportGroupDemo(data, labels);
        setResponseHeader(response, "销售报价单导入模板.xlsx");
        try {
            EasyExcel.write(response.getOutputStream())
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .registerWriteHandler(new BillStatusDropDownHandler(labels.indexOf(IMPORT_STATUS_LABEL)))
                    .sheet("导入模板")
                    .head(buildSingleLevelHeaders(labels))
                    .doWrite(data);
        } catch (IOException e) {
            log.error("导出销售报价单模板失败", e);
            throw new RuntimeException("导出销售报价单模板失败", e);
        }
    }

    // ==================== 销售合同导入模板 ====================
    private static final List<String> SALE_CONTRACT_ITEM_LABELS = List.of(
            "物料编码*", "物料名称", "规格型号", "单位", "数量*", "单价", "税率(%)", "折扣率(%)", "交货日期", "明细备注");
    private static final Set<String> SALE_CONTRACT_HEADER_BLACKLIST = Set.of(
            "合同ID", "状态", "审核状态", "审核人", "审核时间", "关闭状态", "关闭原因", "关闭人", "关闭时间",
            "源单据类型", "源单据号", "商机ID", "商机编号", "附件", "客户ID", "币种ID", "部门ID",
            "合同金额", "税额", "折扣金额", "净额");
    private static final Set<String> SALE_CONTRACT_ALWAYS_INCLUDE = Set.of("客户编码", "客户名称", "合同日期");

    @Override
    public void exportSaleContractTemplate(List<String> fields, HttpServletResponse response) {
        Class<?> entityClass = SaleContract.class;
        boolean autoCode = isAutoCodeEnabled("saleContract");
        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        List<String> labels = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (SALE_CONTRACT_HEADER_BLACKLIST.contains(raw)) continue;
            boolean required = label.endsWith("*") || SALE_CONTRACT_ALWAYS_INCLUDE.contains(raw);
            if ("合同号".equals(raw) && autoCode) continue;
            if (required || (fields != null && fields.contains(raw))) labels.add(label);
        }
        if (!labels.contains("客户名称")) labels.add(1, "客户名称");
        appendImportStatusColumn(labels, fields);
        appendImportGroupColumn(labels);
        List<String> contractHeaderStatic = new ArrayList<>(labels);
        List<String> contractItemLabels = new ArrayList<>(SALE_CONTRACT_ITEM_LABELS);
        appendAutoItemLabels(contractItemLabels, IMPORT_ITEM_CLASS.get("saleContract"));
        appendSaleCustomFieldLabels(labels, "sale_contract", "sale_contract_item", contractHeaderStatic, contractItemLabels);
        List<List<Object>> data = buildDemoRows(labels, SALE_CONTRACT_ITEM_LABELS, "合同日期*");
        setResponseHeader(response, "销售合同导入模板.xlsx");
        writeTemplate(labels, data, response, "销售合同");
    }

    // ==================== 销售出库单导入模板 ====================
    private static final List<String> SALE_DELIVERY_ITEM_LABELS = List.of(
            "物料编码*", "物料名称", "规格型号", "单位", "数量*", "单价", "税率(%)", "折扣率(%)", "批号", "明细备注");
    private static final Set<String> SALE_DELIVERY_HEADER_BLACKLIST = Set.of(
            "出库单ID", "状态", "审核状态", "审核人", "审核时间", "关闭状态", "关闭原因", "关闭人", "关闭时间",
            "源单据类型", "源单据号", "商机ID", "商机编号", "附件", "客户ID", "币种ID", "仓库ID",
            "总金额", "税额", "折扣金额", "净额");
    private static final Set<String> SALE_DELIVERY_ALWAYS_INCLUDE = Set.of("客户编码", "客户名称", "出库日期");

    @Override
    public void exportSaleDeliveryTemplate(List<String> fields, HttpServletResponse response) {
        Class<?> entityClass = SaleDelivery.class;
        boolean autoCode = isAutoCodeEnabled("saleDelivery");
        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        List<String> labels = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (SALE_DELIVERY_HEADER_BLACKLIST.contains(raw)) continue;
            boolean required = label.endsWith("*") || SALE_DELIVERY_ALWAYS_INCLUDE.contains(raw);
            if ("出库单号".equals(raw) && autoCode) continue;
            if (required || (fields != null && fields.contains(raw))) labels.add(label);
        }
        if (!labels.contains("客户名称")) labels.add(1, "客户名称");
        appendImportStatusColumn(labels, fields);
        appendImportGroupColumn(labels);
        appendDeclaredTemplateLabels("saleDelivery", labels);
        List<String> deliveryHeaderStatic = new ArrayList<>(labels);
        List<String> deliveryItemLabels = new ArrayList<>(SALE_DELIVERY_ITEM_LABELS);
        appendAutoItemLabels(deliveryItemLabels, IMPORT_ITEM_CLASS.get("saleDelivery"));
        appendSaleCustomFieldLabels(labels, "sale_delivery", "sale_delivery_item", deliveryHeaderStatic, deliveryItemLabels);
        List<List<Object>> data = buildDemoRows(labels, SALE_DELIVERY_ITEM_LABELS, "出库日期*");
        setResponseHeader(response, "销售出库单导入模板.xlsx");
        writeTemplate(labels, data, response, "销售出库单");
    }

    // ==================== 销售退货单导入模板 ====================
    private static final List<String> SALE_RETURN_ITEM_LABELS = List.of(
            "物料编码*", "物料名称", "规格型号", "单位", "数量*", "单价", "税率(%)", "折扣率(%)", "退货原因", "明细备注");
    private static final Set<String> SALE_RETURN_HEADER_BLACKLIST = Set.of(
            "退货单ID", "状态", "审核状态", "审核人", "审核时间", "关闭状态", "关闭原因", "关闭人", "关闭时间",
            "源单据类型", "源单据号", "附件", "客户ID", "币种ID", "仓库ID",
            "总金额", "税额", "折扣金额", "净额");
    private static final Set<String> SALE_RETURN_ALWAYS_INCLUDE = Set.of("客户编码", "客户名称", "退货日期");

    @Override
    public void exportSaleReturnTemplate(List<String> fields, HttpServletResponse response) {
        Class<?> entityClass = SaleReturn.class;
        boolean autoCode = isAutoCodeEnabled("saleReturn");
        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        List<String> labels = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (SALE_RETURN_HEADER_BLACKLIST.contains(raw)) continue;
            boolean required = label.endsWith("*") || SALE_RETURN_ALWAYS_INCLUDE.contains(raw);
            if ("退货单号".equals(raw) && autoCode) continue;
            if (required || (fields != null && fields.contains(raw))) labels.add(label);
        }
        if (!labels.contains("客户名称")) labels.add(1, "客户名称");
        appendImportStatusColumn(labels, fields);
        appendImportGroupColumn(labels);
        appendDeclaredTemplateLabels("saleReturn", labels);
        List<String> returnHeaderStatic = new ArrayList<>(labels);
        List<String> returnItemLabels = new ArrayList<>(SALE_RETURN_ITEM_LABELS);
        appendAutoItemLabels(returnItemLabels, IMPORT_ITEM_CLASS.get("saleReturn"));
        appendSaleCustomFieldLabels(labels, "sale_return", "sale_return_item", returnHeaderStatic, returnItemLabels);
        List<List<Object>> data = buildDemoRows(labels, SALE_RETURN_ITEM_LABELS, "退货日期*");
        setResponseHeader(response, "销售退货单导入模板.xlsx");
        writeTemplate(labels, data, response, "销售退货单");
    }

    // ==================== 销售发票导入模板 ====================
    private static final List<String> SALE_INVOICE_ITEM_LABELS = List.of(
            "物料编码*", "物料名称", "规格型号", "单位", "数量*", "单价", "税率(%)", "折扣率(%)", "明细备注");
    private static final Set<String> SALE_INVOICE_HEADER_BLACKLIST = Set.of(
            "发票ID", "状态", "审核状态", "审核人", "审核时间", "关闭状态", "关闭原因", "关闭人", "关闭时间",
            "源单据类型", "源单据号", "商机ID", "商机编号", "附件", "客户ID", "币种ID",
            "总金额", "税额", "折扣金额", "净额", "平均税率");
    private static final Set<String> SALE_INVOICE_ALWAYS_INCLUDE = Set.of("客户编码", "客户名称", "开票日期");

    @Override
    public void exportSaleInvoiceTemplate(List<String> fields, HttpServletResponse response) {
        Class<?> entityClass = SaleInvoice.class;
        boolean autoCode = isAutoCodeEnabled("saleInvoice");
        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        List<String> labels = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (SALE_INVOICE_HEADER_BLACKLIST.contains(raw)) continue;
            boolean required = label.endsWith("*") || SALE_INVOICE_ALWAYS_INCLUDE.contains(raw);
            if ("发票号".equals(raw) && autoCode) continue;
            if (required || (fields != null && fields.contains(raw))) labels.add(label);
        }
        if (!labels.contains("客户名称")) labels.add(1, "客户名称");
        appendImportStatusColumn(labels, fields);
        appendImportGroupColumn(labels);
        List<String> invoiceHeaderStatic = new ArrayList<>(labels);
        List<String> invoiceItemLabels = new ArrayList<>(SALE_INVOICE_ITEM_LABELS);
        appendAutoItemLabels(invoiceItemLabels, IMPORT_ITEM_CLASS.get("saleInvoice"));
        appendSaleCustomFieldLabels(labels, "sale_invoice", "sale_invoice_item", invoiceHeaderStatic, invoiceItemLabels);
        List<List<Object>> data = buildDemoRows(labels, SALE_INVOICE_ITEM_LABELS, "开票日期*");
        setResponseHeader(response, "销售发票导入模板.xlsx");
        writeTemplate(labels, data, response, "销售发票");
    }

    // ==================== 售后维修单导入模板 ====================
    // 说明：SaleRepair 实体未加 @ExportField（导出以明细行为主），模板表头用显式清单，
    // 保证「客户 + 维修日期 + 服务/保修 + 费用」这些导入必需的入口列一定出现。
    private static final List<String> SALE_REPAIR_HEADER_LABELS = List.of(
            "客户编码", "客户名称*", "维修日期", "联系人", "联系电话", "服务方式",
            "保修判定", "故障现象", "维修费", "配件费", "备注");
    private static final List<String> SALE_REPAIR_ITEM_LABELS = List.of(
            "物料编码*", "物料名称", "规格型号", "单位", "序列号", "数量*",
            "故障描述", "维修方案", "工时", "明细备注");

    @Override
    public void exportSaleRepairTemplate(List<String> fields, HttpServletResponse response) {
        List<String> labels = new ArrayList<>();
        for (String label : SALE_REPAIR_HEADER_LABELS) {
            String raw = stripRequiredMark(label);
            if (fields != null && !fields.isEmpty() && !fields.contains(raw) && !fieldRequiredByDefault(label)) continue;
            labels.add(label);
        }
        appendImportStatusColumn(labels, fields);
        appendImportGroupColumn(labels);
        List<String> repairHeaderStatic = new ArrayList<>(labels);
        List<String> repairItemLabels = new ArrayList<>(SALE_REPAIR_ITEM_LABELS);
        appendAutoItemLabels(repairItemLabels, IMPORT_ITEM_CLASS.get("saleRepair"));
        appendSaleCustomFieldLabels(labels, "sale_repair", "sale_repair_item", repairHeaderStatic, repairItemLabels);
        List<List<Object>> data = buildDemoRows(labels, SALE_REPAIR_ITEM_LABELS, "维修日期");
        setResponseHeader(response, "售后维修单导入模板.xlsx");
        writeTemplate(labels, data, response, "售后维修单");
    }

    /** 模板列带 * 的视为必带（即使「模板字段设置」未勾选也保留） */
    private boolean fieldRequiredByDefault(String label) {
        return label != null && label.endsWith("*");
    }

    // ==================== 采购模块导入模板（通用：6类单据） ====================

    /** 采购明细列（统一：物料编码、名称、规格、单位、数量、单价、税率、折扣率、金额、交货日期、备注） */
    private static final List<String> PURCHASE_ITEM_LABELS = List.of(
            "物料编码*", "物料名称", "规格型号", "单位", "数量*", "单价", "税率(%)", "折扣率(%)", "金额", "交货日期", "明细备注");

    /** 采购表头排除列（隐藏/审核流转/系统控制字段） */
    private static final Set<String> PURCHASE_HEADER_BLACKLIST = Set.of(
            "申请ID", "订单ID", "合同ID", "入库ID", "退货ID", "发票ID",
            "状态", "审核状态", "审核人", "审核时间", "审核日期", "关闭状态", "关闭原因", "关闭人", "关闭时间",
            "源单据类型", "源单据号", "附件", "供应商ID", "币种ID", "部门ID", "采购员ID", "仓库ID",
            "总金额", "税额", "折扣金额", "净额");

    /** 采购模板始终必带的表头列 */
    private static final Set<String> PURCHASE_ALWAYS_INCLUDE = Set.of("供应商编码", "供应商名称");

    /** 采购单号字段映射 */
    private static final Map<String, String> PURCHASE_NO_LABEL = Map.of(
            "purchaseRequest", "申请单号",
            "purchaseOrder", "订单号",
            "purchaseContract", "合同号",
            "purchaseInbound", "入库单号",
            "purchaseReturn", "退货单号",
            "purchaseInvoice", "发票号");

    /** 采购日期字段映射 */
    private static final Map<String, String> PURCHASE_DATE_LABEL = Map.of(
            "purchaseRequest", "申请日期*",
            "purchaseOrder", "订单日期*",
            "purchaseContract", "合同日期*",
            "purchaseInbound", "入库日期*",
            "purchaseReturn", "退货日期*",
            "purchaseInvoice", "发票日期*");

    /** 采购单据实体类（由登记表按 Group.PURCHASE 派生，禁止手写实体清单） */
    private static final Map<String, Class<?>> PURCHASE_ENTITY_CLASS =
            entityClassesOfGroup(com.erp.export.registry.BillExcelRegistry.Group.PURCHASE);

    /** 采购 snake_case 实体名映射 */
    private static final Map<String, String> PURCHASE_SNAKE = Map.of(
            "purchaseRequest", "purchase_request",
            "purchaseOrder", "purchase_order",
            "purchaseContract", "purchase_contract",
            "purchaseInbound", "purchase_inbound",
            "purchaseReturn", "purchase_return",
            "purchaseInvoice", "purchase_invoice");

    // ==================== 库存模块常量 ====================

    /** 库存明细列（统一：物料编码、名称、规格、单位、数量、单价、金额、批号、库位、备注） */
    private static final List<String> INVENTORY_ITEM_LABELS = List.of(
            "物料编码*", "物料名称", "规格型号", "单位", "数量*", "单价", "金额", "批号", "库位名称", "明细备注");

    /** 库存表头排除列 */
    private static final Set<String> INVENTORY_HEADER_BLACKLIST = Set.of(
            "入库ID", "出库ID", "调拨ID", "盘点ID", "领料ID",
            "状态", "审核状态", "审核人", "审核时间", "审核日期", "关闭状态", "关闭原因", "关闭人", "关闭时间",
            "源单类型", "源单编号", "源单据类型", "源单据号", "附件",
            "仓库ID", "调出仓库ID", "调入仓库ID", "经办人ID", "币种ID", "部门ID", "领料人ID", "盘点人ID");

    /** 库存模板始终必带的表头列 */
    private static final Set<String> INVENTORY_ALWAYS_INCLUDE = Set.of("仓库名称");

    /**
     * 库存单据的「仓库列」：调拨单需要存出/存入两个仓库，其余库存单据只要一个「仓库名称」。
     * 此处是**模板列与导入器读取列名的唯一对应处**：调拨单导入器 importStockTransfers 读的是
     * 「调出仓库名称」「调入仓库名称」，而模板此前只给「仓库名称」→ 历史缺陷：调拨单导入必然失败。
     * 以后新增库存单据若要改仓库列，只改这里，模板与字段清单会一起跟着变。
     */
    private static final Map<String, List<String>> INVENTORY_WAREHOUSE_LABELS = Map.of(
            "stockTransfer", List.of("调出仓库名称", "调入仓库名称"));

    /** 取某张库存单据需要的仓库列（默认一个「仓库名称」） */
    private static List<String> inventoryWarehouseLabels(String entityName) {
        return INVENTORY_WAREHOUSE_LABELS.getOrDefault(entityName, List.of("仓库名称"));
    }

    /** 库存单号字段映射 */
    private static final Map<String, String> INVENTORY_NO_LABEL = Map.of(
            "otherReceipt", "入库单号",
            "otherIssue", "出库单号",
            "stockTransfer", "调拨单号",
            "stockCheck", "盘点单号",
            "materialRequisition", "领料单号");

    /** 库存日期字段映射 */
    private static final Map<String, String> INVENTORY_DATE_LABEL = Map.of(
            "otherReceipt", "入库日期*",
            "otherIssue", "出库日期*",
            "stockTransfer", "调拨日期*",
            "stockCheck", "盘点日期*",
            "materialRequisition", "领料日期*");

    /** 库存单据实体类（由登记表按 Group.INVENTORY 派生，禁止手写实体清单） */
    private static final Map<String, Class<?>> INVENTORY_ENTITY_CLASS =
            entityClassesOfGroup(com.erp.export.registry.BillExcelRegistry.Group.INVENTORY);

    /** 库存 snake_case 实体名映射 */
    private static final Map<String, String> INVENTORY_SNAKE = Map.of(
            "otherReceipt", "other_receipt",
            "otherIssue", "other_issue",
            "stockTransfer", "stock_transfer",
            "stockCheck", "stock_check",
            "materialRequisition", "material_requisition");

    // ==================== 财务模块常量 ====================

    /** 费用分摊明细列 */
    private static final List<String> FINANCE_EXPENSE_ITEM_LABELS = List.of(
            "分摊费用类型", "分摊基数", "分摊比例(%)", "来源单类型", "来源单编号", "分摊金额*", "明细备注");

    /** 财务表头排除列 */
    private static final Set<String> FINANCE_HEADER_BLACKLIST = Set.of(
            "付款单ID", "收款单ID", "预付款ID", "预收款ID", "费用ID",
            "状态", "审批状态", "审核人", "审核时间", "审核日期", "结案状态", "结案原因", "结案人", "结案时间",
            "源单类型", "源单编号", "源单据类型", "源单据号", "附件", "附件URL",
            "供应商ID", "客户ID", "结算方式ID", "币种ID", "出纳ID", "出纳员ID", "部门ID", "申请人ID", "账户ID",
            "已用金额", "剩余金额", "作废人", "作废时间", "作废原因", "作废前状态");

    /** 财务模板始终必带的表头列 */
    private static final Set<String> FINANCE_ALWAYS_INCLUDE = Set.of("供应商名称", "客户名称");

    /** 财务单号字段映射 */
    private static final Map<String, String> FINANCE_NO_LABEL = Map.of(
            "paymentVoucher", "付款单号",
            "paymentReceipt", "收款单号",
            "purchasePrePayment", "预付款单号",
            "salePreReceipt", "预收款单号",
            "operationExpense", "费用单号");

    /** 财务日期字段映射 */
    private static final Map<String, String> FINANCE_DATE_LABEL = Map.of(
            "paymentVoucher", "付款日期*",
            "paymentReceipt", "收款日期*",
            "purchasePrePayment", "付款日期*",
            "salePreReceipt", "收款日期*",
            "operationExpense", "费用日期*");

    /** 财务实体类映射 */
    /** 财务单据实体类（由登记表按 Group.FINANCE 派生，禁止手写实体清单） */
    private static final Map<String, Class<?>> FINANCE_ENTITY_CLASS =
            entityClassesOfGroup(com.erp.export.registry.BillExcelRegistry.Group.FINANCE);

    /** 财务 snake_case 实体名映射 */
    private static final Map<String, String> FINANCE_SNAKE = Map.of(
            "paymentVoucher", "payment_voucher",
            "paymentReceipt", "payment_receipt",
            "purchasePrePayment", "purchase_pre_payment",
            "salePreReceipt", "sale_pre_receipt",
            "operationExpense", "operation_expense");

    /**
     * 采购单据导入模板（通用）：必带列始终包含，可选列按用户勾选(fields)过滤，最后追加明细列。
     */
    @Override
    public void exportPurchaseTemplate(String entityName, List<String> fields, HttpServletResponse response) {
        Class<?> entityClass = PURCHASE_ENTITY_CLASS.get(entityName);
        if (entityClass == null) throw new IllegalArgumentException("不支持的采购单据: " + entityName);
        String noLabel = PURCHASE_NO_LABEL.get(entityName);
        String dateLabel = PURCHASE_DATE_LABEL.get(entityName);
        String snake = PURCHASE_SNAKE.get(entityName);
        boolean autoCode = isAutoCodeEnabled(entityName);

        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        List<String> labels = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (PURCHASE_HEADER_BLACKLIST.contains(raw)) continue;
            boolean required = label.endsWith("*") || PURCHASE_ALWAYS_INCLUDE.contains(raw);
            if (noLabel.equals(raw) && autoCode) continue;
            if (required || (fields != null && fields.contains(raw))) labels.add(label);
        }
        if (!labels.contains("供应商名称")) labels.add(1, "供应商名称");
        appendImportStatusColumn(labels, fields);
        appendImportGroupColumn(labels);
        appendDeclaredTemplateLabels(entityName, labels);
        List<String> headerStatic = new ArrayList<>(labels);
        List<String> purchaseItemLabels = new ArrayList<>(PURCHASE_ITEM_LABELS);
        appendAutoItemLabels(purchaseItemLabels, IMPORT_ITEM_CLASS.get(entityName));
        appendSaleCustomFieldLabels(labels, snake, snake + "_item", headerStatic, purchaseItemLabels);
        List<List<Object>> data = buildDemoRows(labels, PURCHASE_ITEM_LABELS, dateLabel);
        // 演示数据用供应商替换客户
        for (List<Object> row : data) {
            setDemoValue(row, labels, "供应商编码", "GYS001");
            setDemoValue(row, labels, "供应商名称", "深圳市华强电子有限公司");
        }
        String fileName = getTemplateName(entityName);
        setResponseHeader(response, fileName);
        writeTemplate(labels, data, response, entityName);
    }

    /**
     * 采购单据模板字段清单（通用）
     */
    @Override
    public Map<String, List<String>> getPurchaseTemplateFields(String entityName) {
        Class<?> entityClass = PURCHASE_ENTITY_CLASS.get(entityName);
        if (entityClass == null) throw new IllegalArgumentException("不支持的采购单据: " + entityName);
        String noLabel = PURCHASE_NO_LABEL.get(entityName);
        String snake = PURCHASE_SNAKE.get(entityName);
        boolean autoCode = isAutoCodeEnabled(entityName);

        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        List<String> required = new ArrayList<>();
        List<String> optional = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (PURCHASE_HEADER_BLACKLIST.contains(raw)) continue;
            boolean isRequired = label.endsWith("*") || PURCHASE_ALWAYS_INCLUDE.contains(raw);
            if (noLabel.equals(raw) && autoCode) continue;
            if (isRequired) required.add(raw); else optional.add(raw);
        }
        if (!required.contains("供应商名称")) required.add("供应商名称");
        // 「单据状态」列入可选列（导入端据此判断存草稿还是提交生效；默认不勾选=模板与旧版一致）
        if (!optional.contains(IMPORT_STATUS_LABEL)) optional.add(0, IMPORT_STATUS_LABEL);
        // 声明式业务必填列（采购入库/退货的仓库、退货原因、采购发票税号）与模板生成侧保持一致
        for (String extraLabel : BILL_EXTRA_TEMPLATE_LABELS.getOrDefault(entityName, List.of())) {
            if (!required.contains(extraLabel)) required.add(extraLabel);
        }
        List<String> purchaseItemLabelsForFields = new ArrayList<>(PURCHASE_ITEM_LABELS);
        appendAutoItemLabels(purchaseItemLabelsForFields, IMPORT_ITEM_CLASS.get(entityName));
        required.addAll(purchaseItemLabelsForFields.stream().map(this::stripRequiredMark).toList());
        List<com.erp.customfield.entity.CustomField> hdrFields = getEnabledCustomFields(snake);
        for (com.erp.customfield.entity.CustomField f : hdrFields) {
            if ("formula".equals(f.getFieldType())) continue;
            if (!required.contains(f.getFieldLabel())) required.add(f.getFieldLabel());
        }
        List<com.erp.customfield.entity.CustomField> itemFields = getEnabledCustomFields(snake + "_item");
        for (com.erp.customfield.entity.CustomField f : itemFields) {
            if ("formula".equals(f.getFieldType())) continue;
            if (!required.contains(f.getFieldLabel())) required.add(f.getFieldLabel());
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("required", required);
        result.put("optional", optional);
        return result;
    }

    /**
     * 库存单据导入模板（通用）
     */
    @Override
    public void exportInventoryTemplate(String entityName, List<String> fields, String billType, HttpServletResponse response) {
        Class<?> entityClass = INVENTORY_ENTITY_CLASS.get(entityName);
        if (entityClass == null) throw new IllegalArgumentException("不支持的库存单据: " + entityName);
        String noLabel = INVENTORY_NO_LABEL.get(entityName);
        String dateLabel = INVENTORY_DATE_LABEL.get(entityName);
        String snake = INVENTORY_SNAKE.get(entityName);
        boolean autoCode = isAutoCodeEnabled(entityName);

        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        List<String> labels = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (INVENTORY_HEADER_BLACKLIST.contains(raw)) continue;
            boolean required = label.endsWith("*") || INVENTORY_ALWAYS_INCLUDE.contains(raw);
            if (noLabel.equals(raw) && autoCode) continue;
            if (required || (fields != null && fields.contains(raw))) labels.add(label);
        }
        // 领料/退料/补料共用一张表：退料、补料的模板列名换成各自口径，并去掉「单据类型」列
        // （单据类型由导入弹窗传参决定，不该让用户填；列不在模板里，必填校验也不会拦）
        String bt = billType == null ? "" : billType.trim().toUpperCase();
        if ("materialRequisition".equals(entityName) && ("RETURN".equals(bt) || "SUPPLEMENT".equals(bt))) {
            String newNo = "RETURN".equals(bt) ? "退料单号" : "补料单号";
            String newDate = "RETURN".equals(bt) ? "退料日期" : "补料日期";
            String origNo = noLabel;
            String origDate = dateLabel;
            labels.replaceAll(l -> {
                boolean star = l.endsWith("*");
                String raw = stripRequiredMark(l);
                if (raw.equals(origNo)) return newNo + (star ? "*" : "");
                if (raw.equals(origDate)) return newDate + (star ? "*" : "");
                return l;
            });
            labels.removeIf(l -> stripRequiredMark(l).equals("单据类型"));
            noLabel = newNo;
            dateLabel = newDate;
        }
        List<String> whLabels = inventoryWarehouseLabels(entityName);
        for (String wh : whLabels) {
            if (!labels.contains(wh)) labels.add(1, wh);
        }
        appendImportStatusColumn(labels, fields);
        appendImportGroupColumn(labels);
        List<String> headerStatic = new ArrayList<>(labels);
        List<String> inventoryItemLabels = new ArrayList<>(INVENTORY_ITEM_LABELS);
        appendAutoItemLabels(inventoryItemLabels, IMPORT_ITEM_CLASS.get(entityName));
        appendSaleCustomFieldLabels(labels, snake, snake + "_item", headerStatic, inventoryItemLabels);
        List<List<Object>> data = buildDemoRows(labels, inventoryItemLabels, dateLabel);
        for (List<Object> row : data) {
            for (int i = 0; i < whLabels.size(); i++) {
                setDemoValue(row, labels, whLabels.get(i), i == 0 ? "主仓库" : "成品仓");
            }
        }
        String fileName = getTemplateName(entityName);
        setResponseHeader(response, fileName);
        writeTemplate(labels, data, response, entityName);
    }

    /** 库存单据模板字段清单（通用） */
    @Override
    public Map<String, List<String>> getInventoryTemplateFields(String entityName) {
        Class<?> entityClass = INVENTORY_ENTITY_CLASS.get(entityName);
        if (entityClass == null) throw new IllegalArgumentException("不支持的库存单据: " + entityName);
        String noLabel = INVENTORY_NO_LABEL.get(entityName);
        String snake = INVENTORY_SNAKE.get(entityName);
        boolean autoCode = isAutoCodeEnabled(entityName);

        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        List<String> required = new ArrayList<>();
        List<String> optional = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (INVENTORY_HEADER_BLACKLIST.contains(raw)) continue;
            boolean isRequired = label.endsWith("*") || INVENTORY_ALWAYS_INCLUDE.contains(raw);
            if (noLabel.equals(raw) && autoCode) continue;
            if (isRequired) required.add(raw); else optional.add(raw);
        }
        for (String wh : inventoryWarehouseLabels(entityName)) {
            if (!required.contains(wh)) required.add(wh);
        }
        // 「单据状态」列入可选列（导入端据此判断存草稿还是提交生效；默认不勾选=模板与旧版一致）
        if (!optional.contains(IMPORT_STATUS_LABEL)) optional.add(0, IMPORT_STATUS_LABEL);
        List<String> inventoryItemLabelsForFields = new ArrayList<>(INVENTORY_ITEM_LABELS);
        appendAutoItemLabels(inventoryItemLabelsForFields, IMPORT_ITEM_CLASS.get(entityName));
        required.addAll(inventoryItemLabelsForFields.stream().map(this::stripRequiredMark).toList());
        List<com.erp.customfield.entity.CustomField> hdrFields = getEnabledCustomFields(snake);
        for (com.erp.customfield.entity.CustomField f : hdrFields) {
            if ("formula".equals(f.getFieldType())) continue;
            if (!required.contains(f.getFieldLabel())) required.add(f.getFieldLabel());
        }
        List<com.erp.customfield.entity.CustomField> itemFields = getEnabledCustomFields(snake + "_item");
        for (com.erp.customfield.entity.CustomField f : itemFields) {
            if ("formula".equals(f.getFieldType())) continue;
            if (!required.contains(f.getFieldLabel())) required.add(f.getFieldLabel());
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("required", required);
        result.put("optional", optional);
        return result;
    }

    // ==================== 财务单据模板 ====================

    /**
     * 财务单据导入模板（通用）
     */
    @Override
    public void exportFinanceTemplate(String entityName, List<String> fields, HttpServletResponse response) {
        Class<?> entityClass = FINANCE_ENTITY_CLASS.get(entityName);
        if (entityClass == null) throw new IllegalArgumentException("不支持的财务单据: " + entityName);
        String noLabel = FINANCE_NO_LABEL.get(entityName);
        String dateLabel = FINANCE_DATE_LABEL.get(entityName);
        String snake = FINANCE_SNAKE.get(entityName);
        boolean autoCode = isAutoCodeEnabled(entityName);

        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        List<String> labels = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (FINANCE_HEADER_BLACKLIST.contains(raw)) continue;
            boolean required = label.endsWith("*") || FINANCE_ALWAYS_INCLUDE.contains(raw);
            if (noLabel.equals(raw) && autoCode) continue;
            if (required || (fields != null && fields.contains(raw))) labels.add(label);
        }
        // 费用支出单追加分摊明细列
        List<String> itemLabels = "operationExpense".equals(entityName) ? FINANCE_EXPENSE_ITEM_LABELS : List.of();
        appendImportStatusColumn(labels, fields);
        List<String> headerStatic = new ArrayList<>(labels);
        appendSaleCustomFieldLabels(labels, snake, snake + "_item", headerStatic, itemLabels);
        List<List<Object>> data = buildDemoRows(labels, itemLabels, dateLabel);
        String fileName = getTemplateName(entityName);
        setResponseHeader(response, fileName);
        writeTemplate(labels, data, response, entityName);
    }

    /** 财务单据模板字段清单（通用） */
    @Override
    public Map<String, List<String>> getFinanceTemplateFields(String entityName) {
        Class<?> entityClass = FINANCE_ENTITY_CLASS.get(entityName);
        if (entityClass == null) throw new IllegalArgumentException("不支持的财务单据: " + entityName);
        String noLabel = FINANCE_NO_LABEL.get(entityName);
        String snake = FINANCE_SNAKE.get(entityName);
        boolean autoCode = isAutoCodeEnabled(entityName);

        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        List<String> required = new ArrayList<>();
        List<String> optional = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (FINANCE_HEADER_BLACKLIST.contains(raw)) continue;
            boolean isRequired = label.endsWith("*") || FINANCE_ALWAYS_INCLUDE.contains(raw);
            if (noLabel.equals(raw) && autoCode) continue;
            if (isRequired) required.add(raw); else optional.add(raw);
        }
        // 费用支出单追加分摊明细列
        if ("operationExpense".equals(entityName)) {
            required.addAll(FINANCE_EXPENSE_ITEM_LABELS.stream().map(this::stripRequiredMark).toList());
        }
        // 「单据状态」列入可选列（导入端据此判断存草稿还是提交生效；默认不勾选=模板与旧版一致）
        if (!optional.contains(IMPORT_STATUS_LABEL)) optional.add(0, IMPORT_STATUS_LABEL);
        List<com.erp.customfield.entity.CustomField> hdrFields = getEnabledCustomFields(snake);
        for (com.erp.customfield.entity.CustomField f : hdrFields) {
            if ("formula".equals(f.getFieldType())) continue;
            if (!required.contains(f.getFieldLabel())) required.add(f.getFieldLabel());
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("required", required);
        result.put("optional", optional);
        return result;
    }

    /** 构造模板示例数据：第1行含表头示例+明细示例，第2行仅明细示例 */
    private List<List<Object>> buildDemoRows(List<String> labels, List<String> itemLabels, String dateLabel) {
        List<List<Object>> data = new ArrayList<>();
        // 用 setDemoValue 按 label 设值，兼容表头/明细自定义字段动态列
        List<Object> row1 = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) row1.add("");
        setDemoValue(row1, labels, dateLabel, LocalDate.now().toString());
        setDemoValue(row1, labels, "客户编码", "KH001");
        setDemoValue(row1, labels, "客户名称", "深圳市华强电子有限公司");
        setDemoValue(row1, labels, "联系人", "张三");
        setDemoValue(row1, labels, "物料编码*", "WL001");
        setDemoValue(row1, labels, "物料名称", "精密电阻10K");
        setDemoValue(row1, labels, "规格型号", "0603 1%");
        setDemoValue(row1, labels, "单位", "个");
        setDemoValue(row1, labels, "数量*", 1000);
        setDemoValue(row1, labels, "单价", 0.15);
        setDemoValue(row1, labels, "税率(%)", 13);
        data.add(row1);
        List<Object> row2 = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) row2.add("");
        setDemoValue(row2, labels, "物料编码*", "WL002");
        setDemoValue(row2, labels, "物料名称", "贴片电容100nF");
        setDemoValue(row2, labels, "规格型号", "0805 X7R");
        setDemoValue(row2, labels, "单位", "个");
        setDemoValue(row2, labels, "数量*", 2000);
        setDemoValue(row2, labels, "单价", 0.08);
        setDemoValue(row2, labels, "税率(%)", 13);
        data.add(row2);
        // 仓库类列的演示值集中处理：新增单据若有同类列，这里加一行即可，不必回改各模板生成方法
        for (List<Object> row : data) {
            setDemoValue(row, labels, "仓库名称", "主仓库");
            setDemoValue(row, labels, "出库仓库名称", "主仓库");
            setDemoValue(row, labels, "退货仓库名称", "主仓库");
            setDemoValue(row, labels, "入库仓库名称", "主仓库");
            setDemoValue(row, labels, "调出仓库名称", "主仓库");
            setDemoValue(row, labels, "调入仓库名称", "成品仓");
        }
        return data;
    }

    /** 通用模板写出 */
    private void writeTemplate(List<String> labels, List<List<Object>> data, HttpServletResponse response, String name) {
        try {
            fillImportStatusDemo(data, labels);
            fillImportGroupDemo(data, labels);
            EasyExcel.write(response.getOutputStream())
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .registerWriteHandler(new BillStatusDropDownHandler(labels.indexOf(IMPORT_STATUS_LABEL)))
                    .sheet("导入模板")
                    .head(buildSingleLevelHeaders(labels))
                    .doWrite(data);
        } catch (IOException e) {
            log.error("导出" + name + "模板失败", e);
            throw new RuntimeException("导出" + name + "模板失败", e);
        }
    }

    /** 去掉列头必填标记（*）得到原始标签 */
    private String stripRequiredMark(String label) {
        return label.endsWith("*") ? label.substring(0, label.length() - 1) : label;
    }

    /** 销售订单模板字段清单：required=必带列（表头必带+明细列），optional=可勾选表头列 */
    @Override
    public Map<String, List<String>> getSaleOrderTemplateFields() {
        Class<?> entityClass = com.erp.sale.entity.SaleOrder.class;
        boolean autoCode = isAutoCodeEnabled("saleOrder");
        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);

        List<String> required = new ArrayList<>();
        List<String> optional = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (SALE_ORDER_HEADER_BLACKLIST.contains(raw)) continue;
            boolean isRequired = label.endsWith("*") || SALE_ORDER_ALWAYS_INCLUDE.contains(raw);
            if ("订单号".equals(raw) && autoCode) continue;
            if (isRequired) {
                required.add(raw);
            } else {
                optional.add(raw);
            }
        }
        if (!required.contains("客户名称")) {
            required.add("客户名称");
        }
        // 「单据状态」列入可选列（导入端据此判断存草稿还是提交生效；默认不勾选=模板与旧版一致）
        if (!optional.contains(IMPORT_STATUS_LABEL)) optional.add(0, IMPORT_STATUS_LABEL);
        List<String> saleOrderItemLabelsForFields = new ArrayList<>(SALE_ORDER_ITEM_LABELS);
        appendAutoItemLabels(saleOrderItemLabelsForFields, IMPORT_ITEM_CLASS.get("saleOrder"));
        required.addAll(saleOrderItemLabelsForFields.stream().map(this::stripRequiredMark).toList());
        Map<String, List<String>> fields = new LinkedHashMap<>();
        fields.put("required", required);
        fields.put("optional", optional);
        return fields;
    }

    @Override
    public Map<String, List<String>> getSaleTemplateFields(String entityName) {
        // 售后维修单：实体无 @ExportField，模板列用显式清单（与 exportSaleRepairTemplate 同源）
        if ("saleRepair".equals(entityName)) {
            List<String> required = new ArrayList<>();
            for (String label : SALE_REPAIR_HEADER_LABELS) required.add(label);
            List<String> itemLabels = new ArrayList<>(SALE_REPAIR_ITEM_LABELS);
            appendAutoItemLabels(itemLabels, IMPORT_ITEM_CLASS.get("saleRepair"));
            for (String label : itemLabels) required.add(stripRequiredMark(label));
            for (com.erp.customfield.entity.CustomField f : getEnabledCustomFields("sale_repair")) {
                if ("formula".equals(f.getFieldType())) continue;
                if (!required.contains(f.getFieldLabel())) required.add(f.getFieldLabel());
            }
            for (com.erp.customfield.entity.CustomField f : getEnabledCustomFields("sale_repair_item")) {
                if ("formula".equals(f.getFieldType())) continue;
                if (!required.contains(f.getFieldLabel())) required.add(f.getFieldLabel());
            }
            List<String> optional = new ArrayList<>();
            optional.add(IMPORT_STATUS_LABEL);
            Map<String, List<String>> result = new LinkedHashMap<>();
            result.put("required", required);
            result.put("optional", optional);
            return result;
        }
        Class<?> entityClass;
        Set<String> blacklist;
        Set<String> alwaysInclude;
        List<String> itemLabels;
        String noLabel;
        switch (entityName) {
            case "saleOrder" -> { entityClass = SaleOrder.class; blacklist = SALE_ORDER_HEADER_BLACKLIST; alwaysInclude = SALE_ORDER_ALWAYS_INCLUDE; itemLabels = SALE_ORDER_ITEM_LABELS; noLabel = "订单号"; }
            case "saleQuote" -> { entityClass = SaleQuote.class; blacklist = SALE_QUOTE_HEADER_BLACKLIST; alwaysInclude = SALE_QUOTE_ALWAYS_INCLUDE; itemLabels = SALE_QUOTE_ITEM_LABELS; noLabel = "报价单号"; }
            case "saleContract" -> { entityClass = SaleContract.class; blacklist = SALE_CONTRACT_HEADER_BLACKLIST; alwaysInclude = SALE_CONTRACT_ALWAYS_INCLUDE; itemLabels = SALE_CONTRACT_ITEM_LABELS; noLabel = "合同号"; }
            case "saleDelivery" -> { entityClass = SaleDelivery.class; blacklist = SALE_DELIVERY_HEADER_BLACKLIST; alwaysInclude = SALE_DELIVERY_ALWAYS_INCLUDE; itemLabels = SALE_DELIVERY_ITEM_LABELS; noLabel = "出库单号"; }
            case "saleReturn" -> { entityClass = SaleReturn.class; blacklist = SALE_RETURN_HEADER_BLACKLIST; alwaysInclude = SALE_RETURN_ALWAYS_INCLUDE; itemLabels = SALE_RETURN_ITEM_LABELS; noLabel = "退货单号"; }
            case "saleInvoice" -> { entityClass = SaleInvoice.class; blacklist = SALE_INVOICE_HEADER_BLACKLIST; alwaysInclude = SALE_INVOICE_ALWAYS_INCLUDE; itemLabels = SALE_INVOICE_ITEM_LABELS; noLabel = "发票号"; }
            default -> throw new IllegalArgumentException("不支持的销售单据: " + entityName);
        }
        boolean autoCode = isAutoCodeEnabled(entityName);
        List<String> allLabels = getFieldLabelsWithRequiredMark(entityClass, autoCode);
        List<String> required = new ArrayList<>();
        List<String> optional = new ArrayList<>();
        for (String label : allLabels) {
            String raw = stripRequiredMark(label);
            if (blacklist.contains(raw)) continue;
            boolean isRequired = label.endsWith("*") || alwaysInclude.contains(raw);
            if (noLabel.equals(raw) && autoCode) continue;
            if (isRequired) required.add(raw); else optional.add(raw);
        }
        if (!required.contains("客户名称")) required.add("客户名称");
        // 「单据状态」列入可选列（导入端据此判断存草稿还是提交生效；默认不勾选=模板与旧版一致）
        if (!optional.contains(IMPORT_STATUS_LABEL)) optional.add(0, IMPORT_STATUS_LABEL);
        // 声明式业务必填列（如销售出库单的「出库仓库名称」）计入必填清单，与模板生成侧保持一致
        for (String extraLabel : BILL_EXTRA_TEMPLATE_LABELS.getOrDefault(entityName, List.of())) {
            if (!required.contains(extraLabel)) required.add(extraLabel);
        }
        // 明细列一律必带（既有静态列 + 明细实体全部业务字段）
        List<String> saleItemLabelsForFields = new ArrayList<>(itemLabels);
        appendAutoItemLabels(saleItemLabelsForFields, IMPORT_ITEM_CLASS.get(entityName));
        required.addAll(saleItemLabelsForFields.stream().map(this::stripRequiredMark).toList());
        // 自定义字段默认必带（随精简模板带出）
        String snake = camelToSnake(entityName);
        List<String> headerStatic = new ArrayList<>(required);
        headerStatic.addAll(optional);
        List<com.erp.customfield.entity.CustomField> hdrFields = getEnabledCustomFields(snake);
        for (com.erp.customfield.entity.CustomField f : hdrFields) {
            if ("formula".equals(f.getFieldType())) continue;
            if (!required.contains(f.getFieldLabel())) required.add(f.getFieldLabel());
        }
        List<com.erp.customfield.entity.CustomField> itemFields = getEnabledCustomFields(snake + "_item");
        for (com.erp.customfield.entity.CustomField f : itemFields) {
            if ("formula".equals(f.getFieldType())) continue;
            if (!required.contains(f.getFieldLabel())) required.add(f.getFieldLabel());
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("required", required);
        result.put("optional", optional);
        return result;
    }

    /** 按标签向演示行对应列位填值 */
    private void setDemoValue(List<Object> row, List<String> labels, String label, Object value) {
        int idx = labels.indexOf(label);
        if (idx >= 0 && idx < row.size()) {
            row.set(idx, value);
        }
    }

    /**
     * 将 Map 演示数据按表头标签顺序转换为位置行数据。
     * EasyExcel 动态表头写 Map 数据时按字段名取值（动态表头无字段名）会导致整行丢失，
     * 改为位置行后按列序写入，不会错位或丢失。
     */
    private List<List<Object>> toPositionalRows(List<Map<String, Object>> demoRows, List<String> labels) {
        List<List<Object>> rows = new ArrayList<>();
        if (demoRows == null) {
            return rows;
        }
        for (Map<String, Object> m : demoRows) {
            List<Object> r = new ArrayList<>();
            for (String label : labels) {
                Object v = m.get(label);
                r.add(v != null ? v : "");
            }
            rows.add(r);
        }
        return rows;
    }
    /**
     * 获取基础资料导入模板的演示数据（每类实体1行示例，方便用户理解各列填什么）
     * 注意：key 必须与模板表头（getFieldLabelsWithRequiredMark 生成的标签，必填带 * 后缀）
     * 完全一致，且按表头顺序填充（LinkedHashMap），否则Excel列会错位。
     * 未配置演示数据的实体返回空List，模板仅含表头。
     */
    private List<Map<String, Object>> getTemplateDemoData(String entityName) {
        Map<String, Object> row = new LinkedHashMap<>();
        switch (entityName) {
            case "customer" -> {
                row.put("客户编码*", "KH001");
                row.put("客户名称*", "深圳市华强电子有限公司");
                row.put("简称", "华强电子");
                row.put("客户类型", "企业客户");
                row.put("客户等级", "A");
                row.put("税号", "91440300MA5DA1234X");
                row.put("联系人", "张三");
                row.put("联系电话", "13800138001");
                row.put("邮箱", "zhangsan@hqdz.com");
                row.put("地址", "深圳市福田区华强北路100号");
                row.put("开户银行", "中国工商银行深圳华强支行");
                row.put("银行账号", "4000021909200123456");
                row.put("信用额度", 200000);
                row.put("付款条件ID", 1);
                row.put("货币ID", 1);
                row.put("默认销售员ID", "");
                row.put("默认价格等级ID", 1);
                row.put("状态", "正常");
                row.put("税务登记号", "");
                row.put("法人代表", "李华");
                row.put("客户来源", "展会");
                row.put("来源详情", "2026深圳国际电子展");
                row.put("所属行业", "电子制造");
                row.put("公司规模", "100-500人");
                row.put("年营业额", 5000000);
                row.put("微信", "hqdz_zhangsan");
                row.put("公司网站", "www.hqdz.com");
                row.put("CRM状态", "成交客户");
                row.put("最后联系日期", "2026-07-20");
                row.put("下次联系日期", "2026-08-20");
                row.put("重要程度", "高");
                row.put("潜在价值", 300000);
                row.put("满意度评分", 95);
                row.put("累计退款金额", 0);
                row.put("投诉次数", 0);
                row.put("商机数量", 2);
                row.put("订单数量", 15);
                row.put("累计订单金额", 1200000);
                row.put("客户标签", "重点客户");
                row.put("销售阶段", "成交");
                row.put("意向等级", "A");
                row.put("分配销售员ID", "");
                row.put("跟进次数", 12);
                row.put("是否黑名单", 0);
                row.put("客户池类型", 0);
                row.put("进入公海日期", "");
                row.put("最后开单日期", "2026-07-15");
                row.put("最后跟进日期", "2026-07-20");
                row.put("是否置顶", 0);
                row.put("置顶时间", "");
                row.put("归属用户ID", "");
                row.put("主营业务", "电子元器件生产与销售");
                row.put("项目类型", "");
                row.put("客户需求", "月度批量采购连接器");
                row.put("客户喜好", "");
                row.put("家庭背景", "");
                row.put("备注", "示例数据，正式导入前请删除本行");
            }
            case "supplier" -> {
                row.put("供应商编码*", "GYS001");
                row.put("供应商名称*", "东莞市金鑫五金制品有限公司");
                row.put("简称", "金鑫五金");
                row.put("供应商类型", "生产厂商");
                row.put("供应商等级", "A");
                row.put("税号", "91441900MA4W1234XB");
                row.put("联系人", "王强");
                row.put("联系电话", "13900139002");
                row.put("邮箱", "wangqiang@jxwj.com");
                row.put("地址", "东莞市长安镇振安路88号");
                row.put("开户银行", "中国建设银行东莞长安支行");
                row.put("银行账号", "44050123456700001234");
                row.put("付款条件ID", 1);
                row.put("货币ID", 1);
                row.put("默认采购员ID", "");
                row.put("交货天数", 7);
                row.put("状态", "正常");
                row.put("法人代表", "陈明");
            }
            case "material" -> {
                row.put("物料编码*", "WL001");
                row.put("物料名称*", "不锈钢螺丝M6");
                row.put("全称", "不锈钢内六角螺丝M6×20");
                row.put("规格型号", "M6×20");
                row.put("物料分类编码", "01");
                row.put("单位*", "件");
                row.put("物料类型", "原材料");
                row.put("供应类型", "外购");
                row.put("默认供应商", "东莞市金鑫五金制品有限公司");
                row.put("单位换算率", 1);
                row.put("是否批次管理", 0);
                row.put("是否序列号管理", 0);
                row.put("是否库位管理", 0);
                row.put("是否质检", 1);
                row.put("是否有效期管理", 0);
                row.put("有效期天数", "");
                row.put("销售价格", 0.5);
                row.put("采购价格", 0.25);
                row.put("税率(%)", 13);
                row.put("最小库存", 1000);
                row.put("最大库存", 50000);
                row.put("状态", "正常");
                row.put("物料图片", "【图片导入说明】方式1：在Excel中选中单元格→插入→图片→此设备(将图片嵌入到单元格位置)；方式2：直接填写图片URL(多张用英文逗号分隔，如https://example.com/1.jpg,https://example.com/2.jpg)；不支持本地文件路径");
                row.put("是否保密物料", 0);
                row.put("备注", "示例数据，正式导入前请删除本行");
            }
            case "employee" -> {
                row.put("员工编码*", "EMP001");
                row.put("员工姓名*", "张三");
                row.put("部门ID", 1);
                row.put("职位", "销售经理");
                row.put("性别", "男");
                row.put("手机号", "13800138003");
                row.put("邮箱", "zhangsan@demo-erp.com");
                row.put("入职日期", "2023-03-15");
                row.put("用户ID", "");
                row.put("状态", "在职");
                row.put("离职日期", "");
                row.put("学历", "本科");
                row.put("身份证号", "440301199001011234");
            }
            case "department" -> {
                row.put("部门编码*", "D001");
                row.put("部门名称*", "销售部");
                row.put("上级部门ID", "");
                row.put("部门类型", "业务部门");
                row.put("部门经理ID", 1);
                row.put("状态", "正常");
            }
            case "warehouse" -> {
                row.put("仓库编码*", "CK001");
                row.put("仓库名称*", "原材料仓");
                row.put("仓库类型", "实体仓");
                row.put("地址", "深圳市宝安区工业一路5号");
                row.put("负责人ID", 2);
                row.put("是否仓位管理", 0);
                row.put("是否参与MRP", 1);
                row.put("状态", "正常");
                row.put("备注", "");
            }
            case "location" -> {
                row.put("货位编码*", "HW001-A-01");
                row.put("货位名称*", "A区1号货架");
                row.put("所属仓库ID*", 1);
                row.put("状态", "正常");
                row.put("备注", "");
            }
            case "unit" -> {
                row.put("计量单位编码*", "GE");
                row.put("计量单位名称*", "个");
                row.put("计量单位组", "数量");
                row.put("是否基本单位", 1);
                row.put("换算率", 1);
                row.put("状态", "正常");
            }
            case "account" -> {
                row.put("科目编码*", "1001");
                row.put("科目名称*", "库存现金");
                row.put("科目全称", "资产类-库存现金");
                row.put("上级科目ID", "");
                row.put("科目类型", "资产");
                row.put("余额方向", "借");
                row.put("科目级别", 1);
                row.put("是否末级", 1);
                row.put("是否现金科目", 1);
                row.put("是否需要质检", "");
                row.put("是否质检数量", "");
                row.put("状态", "正常");
                row.put("现金流量项目", "");
                row.put("辅助核算", "");
            }
            case "currency" -> {
                row.put("币种编码*", "CNY");
                row.put("币种名称*", "人民币");
                row.put("币种符号", "¥");
                row.put("是否本位币", 1);
                row.put("汇率", 1);
                row.put("状态", "正常");
            }
            case "paymentTerm" -> {
                row.put("付款条件编码*", "NET30");
                row.put("付款条件名称*", "月结30天");
                row.put("付款天数", 30);
                row.put("折扣天数", 10);
                row.put("折扣率", 0.98);
                row.put("预付款比例", 0);
                row.put("状态", "正常");
            }
            case "priceLevel" -> {
                row.put("价格等级编码*", "P01");
                row.put("价格等级名称*", "一级批发价");
                row.put("价格调整率", 1);
                row.put("排序号", 1);
                row.put("状态", "正常");
            }
            case "settlementMode" -> {
                row.put("结算方式编码*", "JS01");
                row.put("结算方式名称*", "银行转账");
                row.put("结算类型", "转账");
                row.put("排序号", 1);
                row.put("状态", "正常");
            }
            case "materialCategory" -> {
                // 编码体系：各级填本位段（一级E + 二级01 + 三级101），物料编码自动拼接为 E01101xxxx
                // 上级引用：唯一编码直接填（E）；同编码挂不同父级时用路径（E/01）
                List<Map<String, Object>> demoRows = new ArrayList<>();
                Map<String, Object> r1 = new HashMap<>();
                r1.put("分类编码*", "E");
                r1.put("分类名称*", "动物系列");
                r1.put("上级分类编码", "");
                r1.put("分类级别", 1);
                r1.put("排序号", 1);
                r1.put("状态", "启用");
                r1.put("备注", "一级产品系列（1位编码），顶级上级留空");
                demoRows.add(r1);
                Map<String, Object> r2 = new HashMap<>();
                r2.put("分类编码*", "01");
                r2.put("分类名称*", "E001封孔耳标");
                r2.put("上级分类编码", "E");
                r2.put("分类级别", 2);
                r2.put("排序号", 2);
                r2.put("状态", "启用");
                r2.put("备注", "二级产品型号（2位编码），上级填一级编码");
                demoRows.add(r2);
                Map<String, Object> r3 = new HashMap<>();
                r3.put("分类编码*", "101");
                r3.put("分类名称*", "EM4200");
                r3.put("上级分类编码", "E/01");
                r3.put("分类级别", 3);
                r3.put("排序号", 3);
                r3.put("状态", "启用");
                r3.put("备注", "三级芯片类型（3位编码），同编码挂不同父级时上级用路径写法");
                demoRows.add(r3);
                return demoRows;
            }
            default -> {
                // 未配置演示数据的实体，模板仅含表头
                return new ArrayList<>();
            }
        }
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(row);
        return data;
    }

    @Override
    public void exportData(String entityName, List<?> dataList, HttpServletResponse response) {
        Class<?> entityClass = ENTITY_CLASS_MAP.get(entityName);
        if (entityClass == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }

        // 表头静态列（@ExportField 非隐藏列）：口径与改动前完全一致；再按字段权限剔除不可见列
        Set<String> hiddenFields = hiddenKeysOf(entityClass);
        List<String> staticLabels = visibleFieldLabels(entityClass, hiddenFields);
        // 表头自定义字段列（设置中心定义，与导入模板/导入同源；未定义该实体的自定义字段 = 与改动前完全一致）
        List<com.erp.customfield.entity.CustomField> headerCustom =
                exportCustomFields(entityName, entityClass, staticLabels);
        if (!hiddenFields.isEmpty()) {
            List<com.erp.customfield.entity.CustomField> visibleCustom = new ArrayList<>();
            for (com.erp.customfield.entity.CustomField cf : headerCustom) {
                if (!isHiddenField(hiddenFields, cf.getFieldKey())) visibleCustom.add(cf);
            }
            headerCustom = visibleCustom;
        }

        // 物料明细列：仅登记过明细规格的单据展开；基础资料/无明细单据 = 与改动前完全一致
        BillItemSpec spec = BILL_ITEM_SPECS.get(entityName);
        List<BillItemColumn> itemCols = spec == null ? Collections.emptyList() : effectiveItemColumns(spec);
        List<com.erp.customfield.entity.CustomField> itemCustom = spec == null ? Collections.emptyList()
                : exportCustomFields(spec.customEntityType(), spec.itemClass(), itemColLabels(itemCols));
        // 第二张从表（售后维修单配件领用）：未登记 = 与改动前完全一致
        BillItemSpec extraSpec = BILL_EXTRA_ITEM_SPECS.get(entityName);
        List<BillItemColumn> extraCols = extraSpec == null ? Collections.emptyList() : effectiveItemColumns(extraSpec);
        List<com.erp.customfield.entity.CustomField> extraCustom = extraSpec == null ? Collections.emptyList()
                : exportCustomFields(extraSpec.customEntityType(), extraSpec.itemClass(), itemColLabels(extraCols));

        List<String> labels = new ArrayList<>(staticLabels);
        for (com.erp.customfield.entity.CustomField cf : headerCustom) labels.add(cf.getFieldLabel());
        for (BillItemColumn col : itemCols) labels.add(col.label());
        for (com.erp.customfield.entity.CustomField cf : itemCustom) labels.add(cf.getFieldLabel());
        for (BillItemColumn col : extraCols) labels.add(col.label());
        for (com.erp.customfield.entity.CustomField cf : extraCustom) labels.add(cf.getFieldLabel());

        List<List<Object>> data = buildExportRows(entityName, dataList,
                toPositionalRows(convertToMapList(dataList, entityClass), staticLabels),
                staticLabels.size(), headerCustom, spec, itemCols, itemCustom,
                extraSpec, extraCols, extraCustom);

        // 统一走"按列写出"出口：图片列（物料图片/线索图片）自动内嵌真实图片
        writePositionalSheet(response, getExportFileName(entityName), "数据", labels, data);
    }

    // ==================== 单据导出：表头 + 物料明细（一行一条明细，可直接回导） ====================

    /**
     * 明细列定义：列标签 + 明细实体属性名 + 兜底属性名/类型（属性不存在时该列自动跳过）。
     * idKind：unit = 值空时按 unitId 查单位名称；bin = 按库位ID查库位名称（明细表常只存 ID）。
     */
    private record BillItemColumn(String label, String field, String idField, String idKind) {}

    /** 普通明细列（直接取属性值） */
    private static BillItemColumn col(String label, String field) { return new BillItemColumn(label, field, null, null); }

    /** 单位列（属性为空时按 unitId 查单位名称） */
    private static BillItemColumn colUnit(String label, String field) { return new BillItemColumn(label, field, "unitId", "unit"); }

    /** 库位列（属性为空时按指定库位ID属性查库位名称） */
    private static BillItemColumn colBin(String label, String field, String idField) { return new BillItemColumn(label, field, idField, "bin"); }

    /** 销售明细列（与销售导入模板明细列同口径；实体没有的属性列自动跳过） */
    private static final List<BillItemColumn> SALE_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "specification"),
            colUnit("单位", "unit"), col("数量", "quantity"), col("单价", "unitPrice"), col("税率(%)", "taxRate"),
            col("折扣率(%)", "discountRate"), col("金额", "amount"), col("批号", "batchNo"),
            col("交货日期", "deliveryDate"), col("退货原因", "returnReason"), col("明细备注", "remark"));

    /** 销售换货明细列（原货品快照 + 替换货品 + 退回台账；实体没有的属性列自动跳过） */
    private static final List<BillItemColumn> SALE_EXCHANGE_ITEM_COLUMNS = List.of(
            col("原货品编码", "originMaterialCode"), col("原货品名称", "originMaterialName"),
            col("原货品规格", "originSpecification"), col("原货品单位", "originUnit"),
            col("原货品单价", "originUnitPrice"),
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "specification"),
            col("单位", "unit"), col("数量", "quantity"), col("单价", "unitPrice"), col("税率(%)", "taxRate"),
            col("折扣率(%)", "discountRate"), col("金额", "amount"), col("税额", "taxAmount"),
            col("净额", "netAmount"), col("待退回数量", "pendingReturnQuantity"),
            col("已退回数量", "returnedQuantity"), col("已下推出库数量", "turnedDeliveryQuantity"),
            col("换货路线", "routeType"), col("明细备注", "remark"));

    /** 售后维修明细列（客户产品行：序列号/故障/维修方案/工时/收件返还数量） */
    private static final List<BillItemColumn> SALE_REPAIR_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "specification"),
            col("单位", "unit"), col("序列号", "serialNo"), col("批号", "batchNo"), col("数量", "quantity"),
            col("故障描述", "faultDescription"), col("维修方案", "repairPlan"), col("保修状态", "warrantyStatus"),
            col("工时", "laborHours"), col("收件数量", "receivedQuantity"), col("已返还数量", "returnedQuantity"),
            col("明细备注", "remark"));

    /** 打样单明细列（打样子件清单：用量/损耗/供应类型/单价） */
    private static final List<BillItemColumn> SAMPLE_ITEM_COLUMNS = List.of(
            col("物料编码", "childMaterialCode"), col("物料名称", "childMaterialName"),
            col("规格型号", "specification"), col("用量", "baseQty"), col("用量类型", "qtyType"),
            col("损耗率(%)", "scrapRate"), col("供应类型", "supplyType"), col("单价", "unitPrice"),
            col("金额", "amount"), col("备注", "remark"));

    /** 免费样品订单明细列 */
    private static final List<BillItemColumn> FREE_SAMPLE_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "model"),
            col("颜色", "color"), col("数量", "quantity"), col("单位", "unitName"),
            col("库位名称", "binName"), col("备注", "remark"));

    /** 采购明细列（与采购导入模板明细列同口径；申请单为专用列，见下） */
    private static final List<BillItemColumn> PURCHASE_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "specification"),
            colUnit("单位", "unit"), col("数量", "quantity"), col("单价", "unitPrice"), col("税率(%)", "taxRate"),
            col("折扣率(%)", "discountRate"), col("金额", "amount"), col("交货日期", "deliveryDate"),
            col("明细备注", "remark"));

    /** 采购申请单明细列（属性名与其它采购单据不同：model / applyQty / demandDate / rowRemark） */
    private static final List<BillItemColumn> PURCHASE_REQUEST_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "model"),
            col("数量", "applyQty"), col("需求日期", "demandDate"), col("明细备注", "rowRemark"));

    /** 库存明细列（与库存导入模板明细列同口径） */
    private static final List<BillItemColumn> INVENTORY_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "specification"),
            colUnit("单位", "unit"), col("数量", "quantity"), col("单价", "unitPrice"), col("金额", "amount"),
            col("批号", "batchNo"), colBin("库位名称", "binName", "binId"), col("明细备注", "remark"));

    /** 调拨单明细列（调出/调入库位是明细级 ID，导出为库位名称） */
    private static final List<BillItemColumn> STOCK_TRANSFER_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "specification"),
            colUnit("单位", "unit"), col("数量", "quantity"), col("单价", "unitPrice"), col("金额", "amount"),
            col("批号", "batchNo"), colBin("调出库位", "fromBinName", "fromBinId"),
            colBin("调入库位", "toBinName", "toBinId"), col("明细备注", "remark"));

    /** 盘点单明细列（盘点单没有 quantity 列，数量用账面/实盘/盈亏口径） */
    private static final List<BillItemColumn> STOCK_CHECK_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "specification"),
            colUnit("单位", "unit"), col("账面数量", "bookQuantity"), col("实盘数量", "checkQuantity"),
            col("盘盈数量", "profitQuantity"), col("盘亏数量", "lossQuantity"), col("单价", "unitPrice"),
            col("金额", "bookAmount"), col("批号", "batchNo"), colBin("库位名称", "binName", "binId"),
            col("盘点结果", "checkResult"), col("明细备注", "remark"));

    /** BOM 子件明细列（与 BOM 统一模板的子件列同口径） */
    private static final List<BillItemColumn> BOM_ITEM_COLUMNS = List.of(
            col("子件编码", "materialCode"), col("子件名称", "materialName"), col("子件规格", "specification"),
            colUnit("单位", "unitName"), col("用量", "quantity"), col("损耗率(%)", "scrapRate"),
            col("工序", "processNo"), col("关键件", "isKeyPart"), col("位置号", "positionNo"),
            col("层级", "level"), col("父件编码", "parentMaterialCode"), col("行备注", "remark"));

    /** 委外订单明细列（与委外订单编辑页明细表同口径） */
    private static final List<BillItemColumn> SUBCONTRACT_ORDER_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "specification"),
            col("单位", "unit"), col("应发数量", "quantity"), col("实发数量", "finishedQty"),
            col("实际数量", "actualQty"), col("单价", "unitPrice"), col("金额", "amount"),
            col("税率(%)", "taxRate"), col("税额", "taxAmount"), col("净额", "netAmount"),
            col("来源单号", "sourceBillNo"), col("明细备注", "remark"));

    /** 委外领料/入库/退料/补料明细列（各编辑页明细表同口径；单位直接持久化，无需查名称） */
    private static final List<BillItemColumn> SUBCONTRACT_MATERIAL_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "specification"),
            col("单位", "unit"), col("数量", "quantity"), col("仓库", "warehouseName"),
            col("来源单号", "sourceBillNo"), col("明细备注", "remark"));

    /** 生产：工单用料明细 */
    private static final List<BillItemColumn> WORK_ORDER_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "specification"),
            colUnit("单位", "unit"), col("数量", "requiredQuantity"), col("已发数量", "issuedQuantity"),
            col("工序号", "processNo"), col("单位成本", "unitCost"), col("金额", "totalCost"),
            col("明细备注", "remark"));

    /** 生产：入库明细 */
    private static final List<BillItemColumn> PRODUCTION_RECEIPT_ITEM_COLUMNS = List.of(
            col("物料编码", "materialCode"), col("物料名称", "materialName"), col("规格型号", "specification"),
            colUnit("单位", "unit"), col("数量", "quantity"), col("合格数量", "qualifiedQuantity"),
            col("不合格数量", "rejectedQuantity"), col("单价", "unitPrice"), col("金额", "amount"),
            col("批号", "batchNo"), col("生产日期", "productionDate"), col("明细备注", "remark"));

    /** 生产：组装单/拆卸单明细列（两张明细表字段名完全一致；物料列是 productCode/productName，单价格是 price，备注是 notes） */
    private static final List<BillItemColumn> ASSEMBLE_ITEM_COLUMNS = List.of(
            col("物料编码", "productCode"), col("物料名称", "productName"), col("规格型号", "specification"),
            col("单位", "unit"), col("数量", "quantity"), col("单价", "price"), col("金额", "amount"),
            col("税率(%)", "taxRate"), col("税额", "taxAmount"), col("来源单号", "sourceSerialNo"),
            col("明细备注", "notes"));

    /** 售后维修配件领用列（第二张从表，2026-11-19 用户口径：配件行必须纳入导出） */
    private static final List<BillItemColumn> SALE_REPAIR_PART_COLUMNS = List.of(
            col("配件编码", "materialCode"), col("配件名称", "materialName"), col("配件规格型号", "specification"),
            colUnit("配件单位", "unit"), col("配件数量", "quantity"),
            new BillItemColumn("配件仓库", "warehouseId", "warehouseId", "warehouse"),
            col("配件批次", "batchNo"), col("配件成本", "avgCost"), col("配件金额", "amount"),
            col("已领用数量", "issuedQuantity"), col("领用出库单号", "issueNo"), col("配件备注", "remark"));

    /** 第二张从表规格（一张单据除主明细外还有第二张明细表时登记；当前仅售后维修单的配件领用） */
    private static final Map<String, BillItemSpec> BILL_EXTRA_ITEM_SPECS = new LinkedHashMap<>();

    static {
        BILL_EXTRA_ITEM_SPECS.put("saleRepair", new BillItemSpec(
                com.erp.sale.entity.SaleRepairPart.class, "repairId", SALE_REPAIR_PART_COLUMNS, "sale_repair_part"));
    }

    /** 单据明细规格：明细实体类 + 明细指向单据的外键属性名 + 明细列 + 明细自定义字段的实体类型 */
    private record BillItemSpec(Class<?> itemClass, String fkField, List<BillItemColumn> columns, String customEntityType) {}

    /** 输出行：单据下标 + 主明细行 + 第二张从表行 + 是否该单首行 */
    private record ExportRowAt(int billIndex, Object item, Object extraItem, boolean firstOfBill) {}

    /** 单据 → 明细规格（未登记 = 无明细，导出行为与改动前完全一致） */
    private static final Map<String, BillItemSpec> BILL_ITEM_SPECS = new LinkedHashMap<>();

    static {
        BILL_ITEM_SPECS.put("saleOrder", new BillItemSpec(SaleOrderItem.class, "orderId", SALE_ITEM_COLUMNS, "sale_order_item"));
        BILL_ITEM_SPECS.put("saleQuote", new BillItemSpec(SaleQuoteItem.class, "quoteId", SALE_ITEM_COLUMNS, "sale_quote_item"));
        BILL_ITEM_SPECS.put("saleContract", new BillItemSpec(SaleContractItem.class, "contractId", SALE_ITEM_COLUMNS, "sale_contract_item"));
        BILL_ITEM_SPECS.put("saleDelivery", new BillItemSpec(SaleDeliveryItem.class, "deliveryId", SALE_ITEM_COLUMNS, "sale_delivery_item"));
        BILL_ITEM_SPECS.put("saleReturn", new BillItemSpec(SaleReturnItem.class, "returnId", SALE_ITEM_COLUMNS, "sale_return_item"));
        BILL_ITEM_SPECS.put("saleInvoice", new BillItemSpec(SaleInvoiceItem.class, "invoiceId", SALE_ITEM_COLUMNS, "sale_invoice_item"));
        // 2026-11-19 新增：销售换货单 / 售后维修单（导出带明细行；维修配件领用行为独立从表，见交付说明）
        BILL_ITEM_SPECS.put("saleExchange", new BillItemSpec(com.erp.sale.entity.SaleExchangeItem.class, "exchangeId", SALE_EXCHANGE_ITEM_COLUMNS, "sale_exchange_item"));
        BILL_ITEM_SPECS.put("saleRepair", new BillItemSpec(com.erp.sale.entity.SaleRepairItem.class, "repairId", SALE_REPAIR_ITEM_COLUMNS, "sale_repair_item"));
        // 打样单 / 免费样品订单（2026-11-27 补导入导出）：表头 + 子件明细
        BILL_ITEM_SPECS.put("sampleOrder", new BillItemSpec(com.erp.sample.entity.SampleOrderItem.class, "sampleId", SAMPLE_ITEM_COLUMNS, "sample_order_item"));
        BILL_ITEM_SPECS.put("freeSampleOrder", new BillItemSpec(com.erp.freesample.entity.FreeSampleOrderItem.class, "orderId", FREE_SAMPLE_ITEM_COLUMNS, "free_sample_order_item"));
        BILL_ITEM_SPECS.put("purchaseRequest", new BillItemSpec(com.erp.purchase.entity.PurchaseRequestItem.class, "requestId", PURCHASE_REQUEST_ITEM_COLUMNS, "purchase_request_item"));
        BILL_ITEM_SPECS.put("purchaseOrder", new BillItemSpec(com.erp.purchase.entity.PurchaseOrderItem.class, "orderId", PURCHASE_ITEM_COLUMNS, "purchase_order_item"));
        BILL_ITEM_SPECS.put("purchaseContract", new BillItemSpec(com.erp.purchase.entity.PurchaseContractItem.class, "contractId", PURCHASE_ITEM_COLUMNS, "purchase_contract_item"));
        BILL_ITEM_SPECS.put("purchaseInbound", new BillItemSpec(com.erp.purchase.entity.PurchaseInboundItem.class, "receiptId", PURCHASE_ITEM_COLUMNS, "purchase_inbound_item"));
        BILL_ITEM_SPECS.put("purchaseReturn", new BillItemSpec(com.erp.purchase.entity.PurchaseReturnItem.class, "returnId", PURCHASE_ITEM_COLUMNS, "purchase_return_item"));
        BILL_ITEM_SPECS.put("purchaseInvoice", new BillItemSpec(com.erp.purchase.entity.PurchaseInvoiceItem.class, "invoiceId", PURCHASE_ITEM_COLUMNS, "purchase_invoice_item"));
        BILL_ITEM_SPECS.put("otherIssue", new BillItemSpec(com.erp.inventory.entity.OtherIssueItem.class, "issueId", INVENTORY_ITEM_COLUMNS, "other_issue_item"));
        BILL_ITEM_SPECS.put("materialRequisition", new BillItemSpec(com.erp.inventory.entity.MaterialRequisitionItem.class, "requisitionId", INVENTORY_ITEM_COLUMNS, "material_requisition_item"));
        BILL_ITEM_SPECS.put("workOrder", new BillItemSpec(com.erp.production.entity.WorkOrderMaterial.class, "orderId", WORK_ORDER_ITEM_COLUMNS, "work_order_item"));
        BILL_ITEM_SPECS.put("productionReceipt", new BillItemSpec(com.erp.production.entity.ProductionReceiptItem.class, "receiptId", PRODUCTION_RECEIPT_ITEM_COLUMNS, "production_receipt_item"));
        BILL_ITEM_SPECS.put("bom", new BillItemSpec(com.erp.masterdata.entity.BomItem.class, "bomId", BOM_ITEM_COLUMNS, "bom_item"));
        BILL_ITEM_SPECS.put("otherReceipt", new BillItemSpec(com.erp.inventory.entity.OtherReceiptItem.class, "receiptId", INVENTORY_ITEM_COLUMNS, "other_receipt_item"));
        BILL_ITEM_SPECS.put("stockTransfer", new BillItemSpec(com.erp.inventory.entity.StockTransferItem.class, "transferId", STOCK_TRANSFER_ITEM_COLUMNS, "stock_transfer_item"));
        BILL_ITEM_SPECS.put("stockCheck", new BillItemSpec(com.erp.inventory.entity.StockCheckItem.class, "checkId", STOCK_CHECK_ITEM_COLUMNS, "stock_check_item"));
        BILL_ITEM_SPECS.put("subcontract", new BillItemSpec(com.erp.subcontract.order.entity.SubcontractOrderItem.class, "orderId", SUBCONTRACT_ORDER_ITEM_COLUMNS, "subcontract_order_item"));
        BILL_ITEM_SPECS.put("subcontractPick", new BillItemSpec(com.erp.subcontract.pick.entity.SubcontractPickItem.class, "pickId", SUBCONTRACT_MATERIAL_ITEM_COLUMNS, "subcontract_pick_item"));
        BILL_ITEM_SPECS.put("subcontractInbound", new BillItemSpec(com.erp.subcontract.inbound.entity.SubcontractInboundItem.class, "inboundId", SUBCONTRACT_MATERIAL_ITEM_COLUMNS, "subcontract_inbound_item"));
        BILL_ITEM_SPECS.put("subcontractReturn", new BillItemSpec(com.erp.subcontract.returnn.entity.SubcontractReturnItem.class, "returnId", SUBCONTRACT_MATERIAL_ITEM_COLUMNS, "subcontract_return_item"));
        BILL_ITEM_SPECS.put("subcontractSupplement", new BillItemSpec(com.erp.subcontract.supplement.entity.SubcontractSupplementItem.class, "supplementId", SUBCONTRACT_MATERIAL_ITEM_COLUMNS, "subcontract_supplement_item"));
        // 2026-09-20 新增：组装单 / 拆卸单（明细表字段名一致，共用 ASSEMBLE_ITEM_COLUMNS）
        BILL_ITEM_SPECS.put("produceAssemble", new BillItemSpec(com.erp.produce.assemble.entity.ProduceAssembleDetail.class, "assembleId", ASSEMBLE_ITEM_COLUMNS, "produce_assemble_detail"));
        BILL_ITEM_SPECS.put("produceDisassemble", new BillItemSpec(com.erp.produce.assemble.entity.ProduceDisassembleDetail.class, "disassembleId", ASSEMBLE_ITEM_COLUMNS, "produce_disassemble_detail"));
    }

    /**
     * 可导入单据 → 明细实体类（模板自动补列用）。
     * 与 BillExcelRegistry 的「可导入」清单同源维护；未登记的单据不自动补明细列（保持原模板）。
     */
    private static final Map<String, Class<?>> IMPORT_ITEM_CLASS;

    static {
        Map<String, Class<?>> m = new LinkedHashMap<>();
        m.put("saleOrder", com.erp.sale.entity.SaleOrderItem.class);
        m.put("saleQuote", com.erp.sale.entity.SaleQuoteItem.class);
        m.put("saleContract", com.erp.sale.entity.SaleContractItem.class);
        m.put("saleDelivery", com.erp.sale.entity.SaleDeliveryItem.class);
        m.put("saleReturn", com.erp.sale.entity.SaleReturnItem.class);
        m.put("saleInvoice", com.erp.sale.entity.SaleInvoiceItem.class);
        m.put("saleRepair", com.erp.sale.entity.SaleRepairItem.class);
        m.put("sampleOrder", com.erp.sample.entity.SampleOrderItem.class);
        m.put("freeSampleOrder", com.erp.freesample.entity.FreeSampleOrderItem.class);
        m.put("purchaseRequest", com.erp.purchase.entity.PurchaseRequestItem.class);
        m.put("purchaseOrder", com.erp.purchase.entity.PurchaseOrderItem.class);
        m.put("purchaseContract", com.erp.purchase.entity.PurchaseContractItem.class);
        m.put("purchaseInbound", com.erp.purchase.entity.PurchaseInboundItem.class);
        m.put("purchaseReturn", com.erp.purchase.entity.PurchaseReturnItem.class);
        m.put("purchaseInvoice", com.erp.purchase.entity.PurchaseInvoiceItem.class);
        m.put("otherReceipt", com.erp.inventory.entity.OtherReceiptItem.class);
        m.put("otherIssue", com.erp.inventory.entity.OtherIssueItem.class);
        m.put("stockTransfer", com.erp.inventory.entity.StockTransferItem.class);
        m.put("stockCheck", com.erp.inventory.entity.StockCheckItem.class);
        m.put("materialRequisition", com.erp.inventory.entity.MaterialRequisitionItem.class);
        m.put("produceAssemble", com.erp.produce.assemble.entity.ProduceAssembleDetail.class);
        m.put("produceDisassemble", com.erp.produce.assemble.entity.ProduceDisassembleDetail.class);
        IMPORT_ITEM_CLASS = Collections.unmodifiableMap(m);
    }

    /** 实体属性反射缓存：导出循环内不再重复查找 */
    private static final Map<Class<?>, Map<String, Field>> EXPORT_FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * 启动自检（2026-11-14）：导入模板「明细字段完整性」。
     * 逐张可导入单据扫描明细实体字段，凡是既不在自动补列字典、也不在排除清单里的业务字段，
     * 启动日志明确告警（不阻断启动）——保证以后新增字段不会被静默漏出模板。
     */
    @jakarta.annotation.PostConstruct
    public void checkImportTemplateFieldCoverage() {
        int missing = 0;
        for (Map.Entry<String, Class<?>> e : IMPORT_ITEM_CLASS.entrySet()) {
            for (java.lang.reflect.Field f : allInstanceFields(e.getValue())) {
                String col = columnNameOf(f);
                if (ITEM_AUTO_EXCLUDE.contains(col)) continue;
                if (col.endsWith("_id")) continue;
                if (ITEM_COLUMN_LABELS.containsKey(col)) continue;
                if ("stock_id".equals(col) || "warehouse_id".equals(col) || "unit_id".equals(col)
                        || "bin_id".equals(col) || "bin_name".equals(col)
                        || "from_stock_id".equals(col) || "to_stock_id".equals(col)
                        || "from_bin_id".equals(col) || "to_bin_id".equals(col)) continue;
                missing++;
                log.warn("[导入模板完整性] 明细字段未登记模板中文列名: {} -> {}", e.getKey(), col);
            }
        }
        if (missing == 0) {
            log.info("[导入模板完整性] 可导入单据明细业务字段已全部覆盖模板列（{} 张单据）", IMPORT_ITEM_CLASS.size());
        } else {
            log.warn("[导入模板完整性] 共有 {} 个明细业务字段未登记中文列名，模板会缺列，请补 ITEM_COLUMN_LABELS", missing);
        }
    }

    private static Map<String, Field> fieldCacheOf(Class<?> clazz) {
        return EXPORT_FIELD_CACHE.computeIfAbsent(clazz, k -> {
            Map<String, Field> m = new HashMap<>();
            for (Field f : k.getDeclaredFields()) {
                try { f.setAccessible(true); } catch (Exception ignored) { /* 访问受限则视为无此属性 */ }
                m.putIfAbsent(f.getName(), f);
            }
            return m;
        });
    }

    private boolean classHasField(Class<?> clazz, String name) {
        return clazz != null && name != null && fieldCacheOf(clazz).containsKey(name);
    }

    /** 反射读属性值（属性不存在或取值失败返回 null，不阻断导出） */
    private Object readFieldValue(Object bean, String name) {
        if (bean == null || name == null) return null;
        Field f = fieldCacheOf(bean.getClass()).get(name);
        if (f == null) return null;
        try { return f.get(bean); } catch (Exception e) { return null; }
    }

    private Long toLongValue(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    /** 只保留明细实体真实存在的列，避免输出整列为空（如销售退货/发票明细无税率列） */
    private List<BillItemColumn> effectiveItemColumns(BillItemSpec spec) {
        List<BillItemColumn> cols = new ArrayList<>();
        for (BillItemColumn c : spec.columns()) {
            if (classHasField(spec.itemClass(), c.field())) {
                cols.add(c);
                continue;
            }
            // 属性不存在但兜底ID存在时保留该列（明细只存 unit_id/bin_id/from_bin_id 的场景仍能按ID查名）
            if (c.idField() != null && classHasField(spec.itemClass(), c.idField())) cols.add(c);
        }
        return cols;
    }

    private List<String> itemColLabels(List<BillItemColumn> cols) {
        List<String> labels = new ArrayList<>();
        for (BillItemColumn c : cols) labels.add(c.label());
        return labels;
    }

    /**
     * 导出用：启用自定义字段（按 sort_order），跳过与静态列同名、或与实体属性同名的（避免重复列与选择歧义）。
     * 与模板/导入同源（同一份 sys_custom_field 定义）；该实体无自定义字段定义时返回空列表 = 导出行为与改动前一致。
     */
    private List<com.erp.customfield.entity.CustomField> exportCustomFields(
            String entityType, Class<?> entityClass, Collection<String> staticLabels) {
        List<com.erp.customfield.entity.CustomField> defs = getEnabledCustomFields(entityType);
        if (defs.isEmpty()) return Collections.emptyList();
        Set<String> labelSet = new HashSet<>(staticLabels == null ? Collections.emptyList() : staticLabels);
        List<com.erp.customfield.entity.CustomField> result = new ArrayList<>();
        for (com.erp.customfield.entity.CustomField cf : defs) {
            if (cf.getFieldKey() == null || cf.getFieldLabel() == null) continue;
            if (labelSet.contains(cf.getFieldLabel())) continue;
            if (classHasField(entityClass, cf.getFieldKey())) continue;
            result.add(cf);
        }
        return result;
    }

    private List<List<Long>> partitionIds(List<Long> ids, int size) {
        List<List<Long>> parts = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += size) {
            parts.add(new ArrayList<>(ids.subList(i, Math.min(i + size, ids.size()))));
        }
        return parts;
    }

    /** 批量取自定义字段值（分批 500 避免 IN 参数过长；失败降级为空 Map，不阻断导出） */
    private Map<Long, Map<String, Object>> batchGetCustomValues(String entityType, List<Long> ids) {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) return result;
        try {
            for (List<Long> part : partitionIds(ids, 500)) {
                Map<Long, Map<String, Object>> one = customFieldService.batchGetFieldValues(entityType, part);
                if (one != null) result.putAll(one);
            }
        } catch (Exception e) {
            log.warn("导出读取自定义字段值失败 entity={}: {}", entityType, e.getMessage());
        }
        return result;
    }

    /** 自定义字段值导出格式：数字/公式转数值（Excel 可直接求和），其余原样（与列表显示一致） */
    private Object formatCustomValue(com.erp.customfield.entity.CustomField cf, Object raw) {
        if (raw == null) return "";
        String s = String.valueOf(raw);
        if (s.isBlank()) return "";
        String type = cf.getFieldType();
        if ("number".equals(type) || "formula".equals(type)) {
            try { return new BigDecimal(s.trim()); } catch (Exception e) { return s; }
        }
        return s;
    }

    /** 单位ID → 单位名称（明细表只存 unit_id） */
    private Map<Long, String> loadUnitNames() {
        Map<Long, String> m = new HashMap<>();
        try {
            for (com.erp.basedata.entity.UnitOfMeasure u : unitOfMeasureMapper.selectList(null)) {
                if (u.getUnitId() != null) m.put(u.getUnitId(), u.getUnitName());
            }
        } catch (Exception e) {
            log.warn("导出加载计量单位失败: {}", e.getMessage());
        }
        return m;
    }

    /** 库位ID → 库位名称 */
    private Map<Long, String> loadBinNames() {
        Map<Long, String> m = new HashMap<>();
        try {
            for (com.erp.basedata.entity.Location l : locationMapper.selectList(null)) {
                if (l.getBinId() != null) m.put(l.getBinId(), l.getBinName());
            }
        } catch (Exception e) {
            log.warn("导出加载库位失败: {}", e.getMessage());
        }
        return m;
    }

    /** 单据 → 明细 Mapper */
    private BaseMapper<?> billItemMapper(BillItemSpec spec) {
        if (spec == null) return null;
        Class<?> c = spec.itemClass();
        if (c == SaleOrderItem.class) return saleOrderItemMapper;
        if (c == SaleQuoteItem.class) return saleQuoteItemMapper;
        if (c == SaleContractItem.class) return saleContractItemMapper;
        if (c == SaleDeliveryItem.class) return saleDeliveryItemMapper;
        if (c == SaleReturnItem.class) return saleReturnItemMapper;
        if (c == SaleInvoiceItem.class) return saleInvoiceItemMapper;
        if (c == com.erp.purchase.entity.PurchaseRequestItem.class) return purchaseRequestItemMapper;
        if (c == com.erp.purchase.entity.PurchaseOrderItem.class) return purchaseOrderItemMapper;
        if (c == com.erp.purchase.entity.PurchaseContractItem.class) return purchaseContractItemMapper;
        if (c == com.erp.purchase.entity.PurchaseInboundItem.class) return purchaseInboundItemMapper;
        if (c == com.erp.purchase.entity.PurchaseReturnItem.class) return purchaseReturnItemMapper;
        if (c == com.erp.purchase.entity.PurchaseInvoiceItem.class) return purchaseInvoiceItemMapper;
        if (c == com.erp.inventory.entity.OtherIssueItem.class) return otherIssueItemMapper;
        if (c == com.erp.inventory.entity.MaterialRequisitionItem.class) return materialRequisitionItemMapper;
        if (c == com.erp.production.entity.WorkOrderMaterial.class) return workOrderMaterialMapper;
        if (c == com.erp.production.entity.ProductionReceiptItem.class) return productionReceiptItemMapper;
        if (c == com.erp.masterdata.entity.BomItem.class) return bomItemMapper;
        if (c == com.erp.inventory.entity.OtherReceiptItem.class) return otherReceiptItemMapper;
        if (c == com.erp.inventory.entity.StockTransferItem.class) return stockTransferItemMapper;
        if (c == com.erp.inventory.entity.StockCheckItem.class) return stockCheckItemMapper;
        if (c == com.erp.subcontract.order.entity.SubcontractOrderItem.class) return subcontractOrderItemMapper;
        if (c == com.erp.subcontract.pick.entity.SubcontractPickItem.class) return subcontractPickItemMapper;
        if (c == com.erp.subcontract.inbound.entity.SubcontractInboundItem.class) return subcontractInboundItemMapper;
        if (c == com.erp.subcontract.returnn.entity.SubcontractReturnItem.class) return subcontractReturnItemMapper;
        if (c == com.erp.subcontract.supplement.entity.SubcontractSupplementItem.class) return subcontractSupplementItemMapper;
        // 2026-09-20 新增：组装单 / 拆卸单明细
        if (c == com.erp.produce.assemble.entity.ProduceAssembleDetail.class) return produceAssembleDetailMapper;
        if (c == com.erp.produce.assemble.entity.ProduceDisassembleDetail.class) return produceDisassembleDetailMapper;
        // 2026-11-19 新增：销售换货单 / 售后维修单明细
        if (c == com.erp.sale.entity.SaleExchangeItem.class) return saleExchangeItemMapper;
        if (c == com.erp.sale.entity.SaleRepairItem.class) return saleRepairItemMapper;
        if (c == com.erp.sale.entity.SaleRepairPart.class) return saleRepairPartMapper;
        return null;
    }

    /** 明细外键属性名 → 数据库列名（优先读 @TableField，取不到按驼峰转下划线兜底） */
    private String fkColumnOf(BillItemSpec spec) {
        Field f = fieldCacheOf(spec.itemClass()).get(spec.fkField());
        if (f != null) {
            com.baomidou.mybatisplus.annotation.TableField tf =
                    f.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
            if (tf != null && !tf.value().isBlank()) return tf.value();
        }
        return camelToSnake(spec.fkField());
    }

    /** 按单据ID批量取明细（分批 500），并按行号排序，保证明细顺序与界面一致 */
    private Map<Long, List<Object>> loadBillItems(BillItemSpec spec, List<Long> billIds) {
        Map<Long, List<Object>> result = new HashMap<>();
        if (spec == null || billIds.isEmpty()) return result;
        BaseMapper<?> mapper = billItemMapper(spec);
        if (mapper == null) return result;
        String fkColumn = fkColumnOf(spec);
        try {
            for (List<Long> part : partitionIds(billIds, 500)) {
                QueryWrapper<Object> qw = new QueryWrapper<>();
                qw.in(fkColumn, part);
                for (Object it : ((BaseMapper) mapper).selectList(qw)) {
                    Long fid = toLongValue(readFieldValue(it, spec.fkField()));
                    if (fid == null) continue;
                    result.computeIfAbsent(fid, k -> new ArrayList<>()).add(it);
                }
            }
            for (List<Object> list : result.values()) {
                list.sort(Comparator.comparingLong(o -> {
                    Long ln = toLongValue(readFieldValue(o, "lineNo"));
                    return ln == null ? 0L : ln;
                }));
            }
        } catch (Exception e) {
            log.warn("导出读取单据明细失败: {}", e.getMessage());
            return new HashMap<>();
        }
        return result;
    }

    /** 仓库ID → 仓库名称 */
    private Map<Long, String> loadWarehouseNames() {
        Map<Long, String> m = new HashMap<>();
        try {
            for (com.erp.basedata.entity.Warehouse w : warehouseMapper.selectList(null)) {
                if (w.getStockId() != null) m.put(w.getStockId(), w.getStockName());
            }
        } catch (Exception e) {
            log.warn("导出加载仓库失败: {}", e.getMessage());
        }
        return m;
    }

    /** 明细单元格取值（含单位/库位/仓库名称按ID查名兜底；日期时间统一格式化） */
    private Object itemCellValue(Object item, BillItemColumn c,
                                 Map<Long, String> unitNames, Map<Long, String> binNames,
                                 Map<Long, String> warehouseNames) {
        Object v = readFieldValue(item, c.field());
        // 引用列：属性本身就是ID（仓库）或属性为空但有兜底ID（单位/库位）→ 按ID查名称
        boolean needIdName = (v == null || String.valueOf(v).isBlank() || "warehouse".equals(c.idKind()));
        if (needIdName && c.idField() != null) {
            Long id = toLongValue(readFieldValue(item, c.idField()));
            if (id != null) {
                if ("unit".equals(c.idKind())) v = unitNames.get(id);
                else if ("warehouse".equals(c.idKind())) v = warehouseNames.get(id);
                else v = binNames.get(id);
            }
        }
        if (v == null) return "";
        if (v instanceof LocalDate d) return d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        if (v instanceof LocalDateTime dt) return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return v;
    }

    /**
     * 组装导出数据行（exportData 与"自定义导出列"两条路径共用）。
     * 有明细的实体（单据/BOM）：一行一条明细；表头列（含表头自定义字段）只在该单首行填写，续行留空
     * ——与导入模板示例、导入"单号/往来单位有值=新单"的分组规则一致，导出文件可直接回导不会重复建单。
     * 有第二张从表的单据（售后维修单的配件领用）：主明细行之后追加从表行，列位各自独立。
     * 无明细的实体：每单一行，明细列留空。
     *
     * @param billHeaderRows 每单的表头静态单元格（与 dataList 同序，由调用方按各自的口径生成）
     * @param staticCellCount 表头静态列数
     */
    private List<List<Object>> buildExportRows(String entityName, List<?> dataList,
                                               List<List<Object>> billHeaderRows, int staticCellCount,
                                               List<com.erp.customfield.entity.CustomField> headerCustom,
                                               BillItemSpec spec, List<BillItemColumn> itemCols,
                                               List<com.erp.customfield.entity.CustomField> itemCustom,
                                               BillItemSpec extraSpec, List<BillItemColumn> extraCols,
                                               List<com.erp.customfield.entity.CustomField> extraCustom) {
        int headerCells = staticCellCount + headerCustom.size();

        List<Long> billIds = new ArrayList<>();
        for (Object bill : dataList) {
            Long id = extractEntityId(bill);
            if (id != null) billIds.add(id);
        }
        Map<Long, List<Object>> itemsByBill = loadBillItems(spec, billIds);
        Map<Long, List<Object>> extrasByBill = loadBillItems(extraSpec, billIds);

        Map<Long, Map<String, Object>> headerValues = headerCustom.isEmpty()
                ? new HashMap<>() : batchGetCustomValues(camelToSnake(entityName), billIds);
        List<Long> itemIds = new ArrayList<>();
        for (List<Object> list : itemsByBill.values()) {
            for (Object it : list) {
                Long iid = extractEntityId(it);
                if (iid != null) itemIds.add(iid);
            }
        }
        Map<Long, Map<String, Object>> itemValues = (spec == null || itemCustom.isEmpty())
                ? new HashMap<>() : batchGetCustomValues(spec.customEntityType(), itemIds);
        List<Long> extraIds = new ArrayList<>();
        for (List<Object> list : extrasByBill.values()) {
            for (Object it : list) {
                Long iid = extractEntityId(it);
                if (iid != null) extraIds.add(iid);
            }
        }
        Map<Long, Map<String, Object>> extraValues = (extraSpec == null || extraCustom.isEmpty())
                ? new HashMap<>() : batchGetCustomValues(extraSpec.customEntityType(), extraIds);

        boolean needUnit = false;
        boolean needBin = false;
        boolean needWarehouse = false;
        for (BillItemColumn c : itemCols) {
            if ("unit".equals(c.idKind())) needUnit = true;
            if ("bin".equals(c.idKind())) needBin = true;
        }
        for (BillItemColumn c : extraCols) {
            if ("unit".equals(c.idKind())) needUnit = true;
            if ("bin".equals(c.idKind())) needBin = true;
            if ("warehouse".equals(c.idKind())) needWarehouse = true;
        }
        Map<Long, String> unitNames = needUnit ? loadUnitNames() : new HashMap<>();
        Map<Long, String> binNames = needBin ? loadBinNames() : new HashMap<>();
        Map<Long, String> warehouseNames = needWarehouse ? loadWarehouseNames() : new HashMap<>();

        List<ExportRowAt> plan = new ArrayList<>();
        for (int i = 0; i < dataList.size(); i++) {
            Long billId = extractEntityId(dataList.get(i));
            List<Object> items = billId == null ? null : itemsByBill.get(billId);
            List<Object> extras = billId == null ? null : extrasByBill.get(billId);
            boolean first = true;
            if (items != null) {
                for (Object it : items) {
                    plan.add(new ExportRowAt(i, it, null, first));
                    first = false;
                }
            }
            if (extras != null) {
                for (Object it : extras) {
                    plan.add(new ExportRowAt(i, null, it, first));
                    first = false;
                }
            }
            if (first) plan.add(new ExportRowAt(i, null, null, true));
        }

        List<List<Object>> rows = new ArrayList<>(plan.size());
        for (ExportRowAt p : plan) {
            List<Object> row = new ArrayList<>(headerCells + itemCols.size() + itemCustom.size()
                    + extraCols.size() + extraCustom.size());
            if (p.firstOfBill()) {
                row.addAll(billHeaderRows.get(p.billIndex()));
                Map<String, Object> hv = headerValues.get(extractEntityId(dataList.get(p.billIndex())));
                for (com.erp.customfield.entity.CustomField cf : headerCustom) {
                    row.add(formatCustomValue(cf, hv == null ? null : hv.get(cf.getFieldKey())));
                }
            } else {
                for (int k = 0; k < headerCells; k++) row.add("");
            }
            Map<String, Object> iv = p.item() == null ? null : itemValues.get(extractEntityId(p.item()));
            for (BillItemColumn c : itemCols) {
                row.add(p.item() == null ? "" : itemCellValue(p.item(), c, unitNames, binNames, warehouseNames));
            }
            for (com.erp.customfield.entity.CustomField cf : itemCustom) {
                row.add(p.item() == null ? "" : formatCustomValue(cf, iv == null ? null : iv.get(cf.getFieldKey())));
            }
            Map<String, Object> ev = p.extraItem() == null ? null : extraValues.get(extractEntityId(p.extraItem()));
            for (BillItemColumn c : extraCols) {
                row.add(p.extraItem() == null ? "" : itemCellValue(p.extraItem(), c, unitNames, binNames, warehouseNames));
            }
            for (com.erp.customfield.entity.CustomField cf : extraCustom) {
                row.add(p.extraItem() == null ? "" : formatCustomValue(cf, ev == null ? null : ev.get(cf.getFieldKey())));
            }
            rows.add(row);
        }
        return rows;
    }

    @Override
    public void exportDataWithSelectedFields(String entityName, List<String> selectedFields, HttpServletResponse response) {
        Class<?> entityClass = ENTITY_CLASS_MAP.get(entityName);
        if (entityClass == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }

        // 查询全部数据
        List<?> dataList = listAllData(entityName);
        // 字段权限：勾选列导出同样剔除不可见列（与列表响应同一份口径）
        Set<String> hiddenFields = hiddenKeysOf(entityClass);

        // 根据用户选择的字段构建表头和数据
        List<List<String>> headers = new ArrayList<>();
        List<String> selectedLabels = new ArrayList<>();
        List<Field> selectedFieldObjs = new ArrayList<>();
        List<ExportField> selectedAnnotations = new ArrayList<>();

        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            ExportField annotation = field.getAnnotation(ExportField.class);
            if (annotation == null || annotation.hidden()) continue;
            // 用户选中的字段
            if (selectedFields != null && !selectedFields.isEmpty()
                    && !selectedFields.contains(field.getName())) continue;
            // 字段权限：不可见字段不允许出现在导出文件里
            if (isHiddenField(hiddenFields, field.getName())) continue;
            field.setAccessible(true);
            headers.add(List.of(annotation.label()));
            selectedLabels.add(annotation.label());
            selectedFieldObjs.add(field);
            selectedAnnotations.add(annotation);
        }

        // 自定义字段列（设置中心定义，与"导出配置"弹窗、导入模板同源）：用户勾选才导出；未定义 = 与改动前完全一致
        List<com.erp.customfield.entity.CustomField> customFields = new ArrayList<>();
        for (com.erp.customfield.entity.CustomField cf
                : exportCustomFields(entityName, entityClass, getFieldLabels(entityClass))) {
            if (selectedFields != null && !selectedFields.isEmpty() && !selectedFields.contains(cf.getFieldKey())) continue;
            if (isHiddenField(hiddenFields, cf.getFieldKey())) continue;
            headers.add(List.of(cf.getFieldLabel()));
            customFields.add(cf);
        }

        // 明细列（与 exportData 同一套规格表）：未登记明细规格的实体 = 空，导出结果与改动前逐列一致
        BillItemSpec spec = BILL_ITEM_SPECS.get(entityName);
        List<BillItemColumn> itemCols = spec == null ? Collections.emptyList() : effectiveItemColumns(spec);
        List<com.erp.customfield.entity.CustomField> itemCustom = spec == null ? Collections.emptyList()
                : exportCustomFields(spec.customEntityType(), spec.itemClass(), itemColLabels(itemCols));
        for (BillItemColumn c : itemCols) headers.add(List.of(c.label()));
        for (com.erp.customfield.entity.CustomField cf : itemCustom) headers.add(List.of(cf.getFieldLabel()));
        // 第二张从表列（售后维修单配件领用）
        BillItemSpec extraSpec = BILL_EXTRA_ITEM_SPECS.get(entityName);
        List<BillItemColumn> extraCols = extraSpec == null ? Collections.emptyList() : effectiveItemColumns(extraSpec);
        List<com.erp.customfield.entity.CustomField> extraCustom = extraSpec == null ? Collections.emptyList()
                : exportCustomFields(extraSpec.customEntityType(), extraSpec.itemClass(), itemColLabels(extraCols));
        for (BillItemColumn c : extraCols) headers.add(List.of(c.label()));
        for (com.erp.customfield.entity.CustomField cf : extraCustom) headers.add(List.of(cf.getFieldLabel()));

        // 表头静态单元格（按勾选字段逐单一行）；明细展开 + 自定义字段列由 buildExportRows 统一处理
        // （位置行：规避 easyexcel 3.3.4 + POI 5.x 下「动态表头 + Map 数据」写不出值的缺陷）
        List<List<Object>> billHeaderRows = new ArrayList<>();
        for (Object item : dataList) {
            List<Object> row = new ArrayList<>();
            for (int i = 0; i < selectedFieldObjs.size(); i++) {
                try {
                    Object value = selectedFieldObjs.get(i).get(item);
                    row.add(formatValue(value, selectedAnnotations.get(i)));
                } catch (IllegalAccessException e) {
                    row.add("");
                }
            }
            billHeaderRows.add(row);
        }

        List<List<Object>> data = buildExportRows(entityName, dataList, billHeaderRows,
                selectedFieldObjs.size(), customFields, spec, itemCols, itemCustom,
                extraSpec, extraCols, extraCustom);

        // 统一走"按列写出"出口：图片列（物料图片/线索图片）自动内嵌真实图片
        List<String> allLabels = new ArrayList<>();
        for (List<String> h : headers) allLabels.add(h.isEmpty() ? "" : h.get(0));
        writePositionalSheet(response, getExportFileName(entityName), "数据", allLabels, data);
    }

    @Override
    public ImportResultDTO importData(String entityName, MultipartFile file) {
        Class<?> entityClass = ENTITY_CLASS_MAP.get(entityName);
        if (entityClass == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }

        ImportResultDTO result = new ImportResultDTO();
        List<Object> entities = new ArrayList<>();
        Map<String, String> fieldLabelMap = getFieldLabelMap(entityClass);
        // 自定义字段：列定义 + 表头列索引（与 entities 按序对应的值收集器）
        List<com.erp.customfield.entity.CustomField> customFields = getEnabledCustomFields(entityName);
        List<Map<String, Object>> customValuesList = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream()) {
            List<Object> rawData = EasyExcel.read(inputStream)
                    .headRowNumber(0)
                    .sheet()
                    .doReadSync();
            List<List<Object>> excelData = convertExcelRowsToList(rawData);

            if (excelData.isEmpty()) {
                result.setTotalCount(0);
                return result;
            }

            List<String> headers = convertToStringList(excelData.get(0));
            Map<String, Integer> headerIndexMap = buildHeaderIndexMap(headers, fieldLabelMap);
            Map<Integer, com.erp.customfield.entity.CustomField> customIndexMap = buildCustomFieldIndexMap(headers, customFields, headerIndexMap);
            // 字段权限（角色必填）一次性解析：循环内复用，避免逐行查库
            com.erp.permission.service.FieldRequiredPermissionService.PageRequired permRequired =
                    resolvePermRequired(camelToSnake(entityName));

            result.setTotalCount(excelData.size() - 1);

            // 客户档案导入默认人：归属人/分配销售员 = 当前登录账号（循环外解析一次，避免逐行查库）
            Long loginUserId = "customer".equals(entityName) ? currentLoginUserId() : null;
            Long loginEmployeeId = loginUserId == null ? null : currentLoginEmployeeId(loginUserId);

            for (int i = 1; i < excelData.size(); i++) {
                List<Object> row = excelData.get(i);
                boolean isEmpty = isRowEmpty(row);
                if (isEmpty) continue;

                try {
                    Object entity = entityClass.getDeclaredConstructor().newInstance();
                    int errorsBefore = result.getErrors().size();
                    populateEntity(entity, row, headerIndexMap, fieldLabelMap, entityClass, i + 1, result, isAutoCodeEnabled(entityName));
                    // 字段权限（角色必填）叠加：与静态必填同口径，缺失同样拦该行；该列不在上传模板中则跳过
                    if (!permRequired.staticFields().isEmpty()) {
                        Map<String, String> permLabelValues = new LinkedHashMap<>();
                        for (Map.Entry<String, Integer> he : headerIndexMap.entrySet()) {
                            String lb = fieldLabelMap.get(he.getKey());
                            if (lb == null) continue;
                            int ci = he.getValue();
                            permLabelValues.put(lb, (ci < row.size() && row.get(ci) != null) ? String.valueOf(row.get(ci)).trim() : "");
                        }
                        for (String missLabel : fieldRequiredPermissionService.findMissingStaticLabels(permLabelValues, permRequired)) {
                            result.addError(i + 1, missLabel, "不能为空");
                        }
                    }
                    // 必填缺失/格式错误：该行不入库（原实现只提示仍继续导入，导致"必填没填也能进"）
                    if (result.getErrors().size() > errorsBefore) {
                        result.setFailCount(result.getFailCount() + 1);
                        continue;
                    }
                    // 物料分类特殊处理：通过 parentCode 查找 parentId（支持 E/01 路径写法；
                    // 数据库暂未找到时不报错——父级可能在本批次内，留待保存阶段解析）
                    if ("materialCategory".equals(entityName)) {
                        resolveCategoryParent(entity, i + 1, result);
                    }
                    // 物料：单位名称→单位ID、分类编码→分类ID；任一解析失败即报错并拒绝该行
                    if ("material".equals(entityName) && entity instanceof com.erp.masterdata.entity.Material materialEntity) {
                        resolveMaterialRefs(materialEntity, i + 1, result);
                        if (result.getErrors().size() > errorsBefore) {
                            result.setFailCount(result.getFailCount() + 1);
                            continue;
                        }
                    }
                    // 自定义字段值解析（select支持选项名/值双写法；number校验格式；必填校验）。
                    // 必须先校验再入列：原实现直接 customValuesList.add(...)，自定义字段"必填"缺失
                    // 只记账错误不拦截，该行照样入库且 successCount+1（与静态字段会拦整行的口径不一致）。
                    Map<String, Object> customVals = parseCustomFieldValues(customIndexMap, row, i + 1, result, permRequired.allLabels());
                    if (result.getErrors().size() > errorsBefore) {
                        result.setFailCount(result.getFailCount() + 1);
                        continue;
                    }
                    // 校验通过后两个列表同步入列，保证索引一一对应
                    // 客户档案：归属人 = 当前导入账号、分配销售员 = 该账号的员工（2026-09-21，与页面新增客户同口径）
                    if (entity instanceof Customer) {
                        applyCustomerImportDefaults((Customer) entity, loginUserId, loginEmployeeId, result);
                    }
                    customValuesList.add(customVals);
                    entities.add(entity);
                    // 物料分类的成功数在保存成功后统计
                    if (!"materialCategory".equals(entityName)) {
                        result.setSuccessCount(result.getSuccessCount() + 1);
                    }
                } catch (Exception e) {
                    result.setFailCount(result.getFailCount() + 1);
                    result.addError(i + 1, "", "数据解析错误: " + e.getMessage());
                }
            }

            if (!entities.isEmpty()) {
                // 物料分类特殊保存：先保存父级再保存子级
                if ("materialCategory".equals(entityName)) {
                    saveMaterialCategories(entities, result);
                } else {
                    saveEntitiesWithCustomFields(entityName, entities, customValuesList, result);
                }
            }

        } catch (IOException e) {
            log.error("导入数据失败", e);
            throw new RuntimeException("导入数据失败", e);
        }

        return result;
    }

    /**
     * 物料导入的关联字段解析：
     * 1) 单位（模板必填列）按 unit_name / unit_code 查单位表 → 回填 base/purchase/sale/stock 单位ID；不存在报错
     * 2) 物料分类编码 → 查物料分类表回填 categoryId；不存在报错（不静默放过）
     * 调用方在发现新增错误后拒绝该行入库
     */
    private void resolveMaterialRefs(com.erp.masterdata.entity.Material material, int rowIndex, ImportResultDTO result) {
        String unitName = material.getUnitName();
        if (StringUtils.hasText(unitName)) {
            try {
                com.erp.basedata.entity.UnitOfMeasure unit = unitOfMeasureMapper.selectOne(
                        new QueryWrapper<com.erp.basedata.entity.UnitOfMeasure>()
                                .and(w -> w.eq("unit_name", unitName).or().eq("unit_code", unitName))
                                .last("LIMIT 1"));
                if (unit == null) {
                    result.addError(rowIndex, "单位", "单位[" + unitName + "]不存在，请先在基础资料中维护计量单位");
                } else {
                    material.setBaseUnitId(unit.getUnitId());
                    material.setPurchaseUnitId(unit.getUnitId());
                    material.setSaleUnitId(unit.getUnitId());
                    material.setStockUnitId(unit.getUnitId());
                }
            } catch (Exception e) {
                result.addError(rowIndex, "单位", "查找单位失败: " + e.getMessage());
            }
        }
        String categoryCode = material.getCategoryCode();
        if (StringUtils.hasText(categoryCode) && material.getCategoryId() == null) {
            try {
                com.erp.basedata.entity.MaterialCategory category = materialCategoryMapper.selectOne(
                        new QueryWrapper<com.erp.basedata.entity.MaterialCategory>()
                                .eq("category_code", categoryCode).last("LIMIT 1"));
                if (category == null) {
                    result.addError(rowIndex, "物料分类编码", "分类编码[" + categoryCode + "]不存在");
                } else {
                    material.setCategoryId(category.getCategoryId());
                }
            } catch (Exception e) {
                result.addError(rowIndex, "物料分类编码", "查找分类失败: " + e.getMessage());
            }
        }
    }

    // ==================== 销售订单专用导入（主子表聚合） ====================

    /**
     * 销售订单专用导入：单sheet多行明细。
     * 规则：
     *  - 一行 = 一条明细；同一订单的多行明细连续排列，首行填表头字段，后续行表头留空自动沿用上行
     *  - 客户支持 客户ID / 客户编码 / 客户名称 三种填法（按序取到即止）
     *  - 物料按编码解析（不存在则该行报错）；金额留空自动按 数量×单价 计算
     *  - 一律草稿状态，复用 saveOrderWithItems（自动单号/查重/默认值）
     *  - 结果按订单数统计：一个订单全部明细解析失败则该单失败；部分明细失败该单也失败（保证原子性）
     */
    @Override
    public ImportResultDTO importSaleOrders(MultipartFile file) {
        // 【通用件】统一走销售单据导入框架：聚合/解析/必填校验/自定义字段回填/「单据状态」分流 全部复用
        return importSaleBill(file, "订单号", "sale_order", "sale_order_item",
                this::buildSaleOrder,
                (cells, matCache, row) -> buildSaleItem(cells, matCache, row, SaleOrderItem.class),
                (header, items) -> saleOrderService.saveOrderWithItems((SaleOrder) header, (List<SaleOrderItem>) items));
    }

    // ==================== 销售报价单导入 ====================

    @Override
    public ImportResultDTO importSaleQuotes(MultipartFile file) {
        // 【通用件】统一走销售单据导入框架（同 importSaleOrders）
        return importSaleBill(file, "报价单号", "sale_quote", "sale_quote_item",
                this::buildSaleQuote,
                (cells, matCache, row) -> buildSaleItem(cells, matCache, row, SaleQuoteItem.class),
                (header, items) -> saleQuoteService.saveQuoteWithItems((SaleQuote) header, (List<SaleQuoteItem>) items));
    }

    private SaleQuote buildSaleQuote(Map<String, String> cells, Map<String, Customer> customerCache, int rowIndex) {
        SaleQuote quote = new SaleQuote();
        Customer customer = null;
        if (hasValue(cells, "客户ID")) {
            try { customer = customerMapper.selectById(Long.parseLong(cells.get("客户ID"))); } catch (NumberFormatException ignored) {}
            if (customer == null) throw new IllegalArgumentException("第" + rowIndex + "行: 客户ID[" + cells.get("客户ID") + "]不存在");
        } else if (hasValue(cells, "客户编码")) {
            customer = customerCache.computeIfAbsent("no:" + cells.get("客户编码"),
                    k -> customerMapper.selectOne(new QueryWrapper<Customer>().eq("customer_code", k.substring(3)).last("LIMIT 1")));
            if (customer == null) throw new IllegalArgumentException("第" + rowIndex + "行: 客户编码[" + cells.get("客户编码") + "]不存在");
        } else if (hasValue(cells, "客户名称")) {
            customer = customerCache.computeIfAbsent("name:" + cells.get("客户名称"),
                    k -> customerMapper.selectOne(new QueryWrapper<Customer>().eq("customer_name", k.substring(5)).last("LIMIT 1")));
            if (customer == null) throw new IllegalArgumentException("第" + rowIndex + "行: 客户名称[" + cells.get("客户名称") + "]不存在");
        } else {
            throw new IllegalArgumentException("第" + rowIndex + "行: 客户ID/客户编码/客户名称至少填一项");
        }
        quote.setCustomerId(customer.getCustomerId());

        if (hasValue(cells, "报价单号")) quote.setQuoteNo(cells.get("报价单号"));
        if (hasValue(cells, "报价日期")) quote.setQuoteDate(parseDate(cells.get("报价日期"), rowIndex, "报价日期"));
        if (hasValue(cells, "有效期起")) quote.setValidFrom(parseDate(cells.get("有效期起"), rowIndex, "有效期起"));
        if (hasValue(cells, "有效期止")) quote.setValidTo(parseDate(cells.get("有效期止"), rowIndex, "有效期止"));
        if (hasValue(cells, "交货日期")) quote.setDeliveryDate(parseDate(cells.get("交货日期"), rowIndex, "交货日期"));
        if (hasValue(cells, "联系人")) quote.setContactPerson(cells.get("联系人"));
        if (hasValue(cells, "联系电话")) quote.setContactPhone(cells.get("联系电话"));
        if (hasValue(cells, "备注")) quote.setRemark(cells.get("备注"));
        if (hasValue(cells, "销售员ID")) quote.setSalerId(Long.parseLong(cells.get("销售员ID")));
        if (hasValue(cells, "价格等级ID")) quote.setPriceLevelId(Long.parseLong(cells.get("价格等级ID")));
        if (hasValue(cells, "币种ID")) {
            quote.setCurrencyId(Long.parseLong(cells.get("币种ID")));
        } else {
            quote.setCurrencyId(currencyMapper.selectList(null).stream().map(c -> c.getCurrencyId()).findFirst().orElse(1L));
        }
        if (hasValue(cells, "汇率")) quote.setExchangeRate(new BigDecimal(cells.get("汇率")));
        if (hasValue(cells, "折扣率")) quote.setDiscountRate(new BigDecimal(cells.get("折扣率")));
        if (hasValue(cells, "付款条件ID")) quote.setPaymentTermId(Long.parseLong(cells.get("付款条件ID")));
        return quote;
    }

    /**
     * 【通用构建器·销售明细】按数据库列名落值，一套代码覆盖销售 6 张单据的明细实体。
     * 销售 6 张单据原来各写了一份复制粘贴的明细构建实现，现已全部删除、统一收敛到本方法：
     * 销售 6 张的明细字段名本就一致（specification/unit/quantity/remark），本就无需各写一份。
     */
    private Object buildSaleItem(Map<String, String> cells, Map<String, Material> materialCache, int rowIndex, Class<?> itemClass) {
        Material material = resolveMaterial(cells, materialCache, rowIndex);
        try {
            Object item = itemClass.getDeclaredConstructor().newInstance();
            itemClass.getMethod("setMaterialId", Long.class).invoke(item, material.getMaterialId());
            itemClass.getMethod("setMaterialCode", String.class).invoke(item, material.getMaterialCode());
            setByColumn(item, "material_name", hasValue(cells, "物料名称") ? cells.get("物料名称") : material.getMaterialName());
            setByColumn(item, "specification", hasValue(cells, "规格型号") ? cells.get("规格型号") : material.getModel());
            if (hasValue(cells, "单位")) setByColumn(item, "unit", cells.get("单位"));
            setByAnyColumn(item, parseBigDecimal(cells, "数量", rowIndex, true), QTY_COLUMNS);
            if (hasValue(cells, "单价")) setByColumn(item, "unit_price", parseBigDecimal(cells, "单价", rowIndex, false));
            if (hasValue(cells, "税率(%)")) setByColumn(item, "tax_rate", parseBigDecimal(cells, "税率(%)", rowIndex, false));
            if (hasValue(cells, "折扣率(%)")) setByColumn(item, "discount_rate", parseBigDecimal(cells, "折扣率(%)", rowIndex, false));
            if (hasValue(cells, "金额")) setByColumn(item, "amount", parseBigDecimal(cells, "金额", rowIndex, false));
            String dateRaw = hasValue(cells, "交货日期") ? cells.get("交货日期")
                    : (hasValue(cells, "需求日期") ? cells.get("需求日期") : null);
            if (dateRaw != null) setByAnyColumn(item, parseDate(dateRaw, rowIndex, "交货/需求日期"), DATE_COLUMNS);
            if (hasValue(cells, "明细备注")) setByAnyColumn(item, cells.get("明细备注"), REMARK_COLUMNS);
            return item;
        } catch (Exception e) {
            throw new IllegalArgumentException("第" + rowIndex + "行: 明细构建失败: " + e.getMessage());
        }
    }
    // ==================== 销售合同导入 ====================
    @Override
    public ImportResultDTO importSaleContracts(MultipartFile file) {
        return importSaleBill(file, "合同号", "sale_contract", "sale_contract_item",
                (cells, custCache, row) -> buildSaleContract(cells, custCache, row),
                (cells, matCache, row) -> buildSaleItem(cells, matCache, row, SaleContractItem.class),
                (header, items) -> saleContractService.saveContractWithItems((SaleContract) header, (List<SaleContractItem>) items));
    }

    private SaleContract buildSaleContract(Map<String, String> cells, Map<String, Customer> customerCache, int rowIndex) {
        SaleContract c = new SaleContract();
        Customer customer = resolveCustomer(cells, customerCache, rowIndex);
        c.setCustomerId(customer.getCustomerId());
        if (customer.getCustomerCode() != null) c.setCustomerNo(customer.getCustomerCode());
        if (customer.getShortName() != null) c.setCustomerShortName(customer.getShortName());
        if (hasValue(cells, "合同号")) c.setContractNo(cells.get("合同号"));
        if (hasValue(cells, "合同日期")) c.setContractDate(parseDate(cells.get("合同日期"), rowIndex, "合同日期"));
        if (hasValue(cells, "合同类型")) c.setContractType(cells.get("合同类型"));
        if (hasValue(cells, "有效期起")) c.setValidityStart(parseDate(cells.get("有效期起"), rowIndex, "有效期起"));
        if (hasValue(cells, "有效期止")) c.setValidityEnd(parseDate(cells.get("有效期止"), rowIndex, "有效期止"));
        if (hasValue(cells, "交货日期")) c.setDeliveryDate(parseDate(cells.get("交货日期"), rowIndex, "交货日期"));
        if (hasValue(cells, "联系人")) c.setContactPerson(cells.get("联系人"));
        if (hasValue(cells, "联系电话")) c.setContactPhone(cells.get("联系电话"));
        if (hasValue(cells, "客户编码")) c.setCustomerNo(cells.get("客户编码"));
        if (hasValue(cells, "客户简称")) c.setCustomerShortName(cells.get("客户简称"));
        if (hasValue(cells, "备注")) c.setRemark(cells.get("备注"));
        if (hasValue(cells, "销售员ID")) c.setSalerId(Long.parseLong(cells.get("销售员ID")));
        if (hasValue(cells, "部门ID")) c.setDepartmentId(Long.parseLong(cells.get("部门ID")));
        if (hasValue(cells, "价格等级ID")) c.setPriceLevelId(Long.parseLong(cells.get("价格等级ID")));
        if (hasValue(cells, "仓库ID")) c.setWarehouseId(Long.parseLong(cells.get("仓库ID")));
        if (hasValue(cells, "币种ID")) c.setCurrencyId(Long.parseLong(cells.get("币种ID")));
        else c.setCurrencyId(defaultCurrencyId());
        if (hasValue(cells, "汇率")) c.setExchangeRate(new BigDecimal(cells.get("汇率")));
        if (hasValue(cells, "折扣率")) c.setDiscountRate(new BigDecimal(cells.get("折扣率")));
        if (hasValue(cells, "付款条件ID")) c.setPaymentTermId(Long.parseLong(cells.get("付款条件ID")));
        if (hasValue(cells, "付款方式")) c.setPaymentMethod(cells.get("付款方式"));
        if (hasValue(cells, "订金金额")) c.setDepositAmount(new BigDecimal(cells.get("订金金额")));
        if (hasValue(cells, "收货地址")) c.setReceiveAddress(cells.get("收货地址"));
        if (hasValue(cells, "运输方式")) c.setTransportMethod(cells.get("运输方式"));
        if (hasValue(cells, "是否含税")) c.setIsTax(cells.get("是否含税"));
        if (hasValue(cells, "计税方式")) c.setTaxCalcMethod(cells.get("计税方式"));
        if (hasValue(cells, "是否开票")) c.setIsInvoiced(cells.get("是否开票"));
        if (hasValue(cells, "物流公司")) c.setLogisticsCompany(cells.get("物流公司"));
        if (hasValue(cells, "物流单号")) c.setLogisticsNo(cells.get("物流单号"));
        if (hasValue(cells, "发票类型")) c.setInvoiceType(cells.get("发票类型"));
        if (hasValue(cells, "项目号")) c.setProjectNo(cells.get("项目号"));
        if (hasValue(cells, "客户订单号")) c.setCustomerOrderNo(cells.get("客户订单号"));
        return c;
    }
    // ==================== 销售出库单导入 ====================
    @Override
    public ImportResultDTO importSaleDeliveries(MultipartFile file) {
        return importSaleBill(file, "出库单号", "sale_delivery", "sale_delivery_item",
                (cells, custCache, row) -> buildSaleDelivery(cells, custCache, row),
                (cells, matCache, row) -> buildSaleItem(cells, matCache, row, SaleDeliveryItem.class),
                (header, items) -> saleDeliveryService.saveDeliveryWithItems((SaleDelivery) header, (List<SaleDeliveryItem>) items));
    }

    private SaleDelivery buildSaleDelivery(Map<String, String> cells, Map<String, Customer> customerCache, int rowIndex) {
        SaleDelivery d = new SaleDelivery();
        applyDeclaredBillColumns("saleDelivery", d, cells, rowIndex);
        Customer customer = resolveCustomer(cells, customerCache, rowIndex);
        d.setCustomerId(customer.getCustomerId());
        if (hasValue(cells, "出库单号")) d.setDeliveryNo(cells.get("出库单号"));
        if (hasValue(cells, "出库日期")) d.setDeliveryDate(parseDate(cells.get("出库日期"), rowIndex, "出库日期"));
        if (hasValue(cells, "联系人")) d.setContactPerson(cells.get("联系人"));
        if (hasValue(cells, "联系电话")) d.setContactPhone(cells.get("联系电话"));
        if (hasValue(cells, "备注")) d.setRemark(cells.get("备注"));
        if (hasValue(cells, "销售员ID")) d.setSalerId(Long.parseLong(cells.get("销售员ID")));
        if (hasValue(cells, "仓库ID")) d.setWarehouseId(Long.parseLong(cells.get("仓库ID")));
        if (hasValue(cells, "币种ID")) d.setCurrencyId(Long.parseLong(cells.get("币种ID")));
        else d.setCurrencyId(defaultCurrencyId());
        if (hasValue(cells, "汇率")) d.setExchangeRate(new BigDecimal(cells.get("汇率")));
        if (hasValue(cells, "折扣率")) d.setDiscountRate(new BigDecimal(cells.get("折扣率")));
        if (hasValue(cells, "订单号")) d.setOrderNo(cells.get("订单号"));
        if (hasValue(cells, "发票号")) d.setInvoiceNo(cells.get("发票号"));
        if (hasValue(cells, "快递公司")) d.setExpressCompany(cells.get("快递公司"));
        if (hasValue(cells, "快递单号")) d.setExpressNo(cells.get("快递单号"));
        if (hasValue(cells, "联系地址")) d.setContactAddress(cells.get("联系地址"));
        if (hasValue(cells, "源单据类型")) d.setSourceBillType(cells.get("源单据类型"));
        if (hasValue(cells, "源单据号")) d.setSourceBillNo(cells.get("源单据号"));
        if (hasValue(cells, "承运人")) d.setCarrier(cells.get("承运人"));
        if (hasValue(cells, "跟踪号")) d.setTrackingNo(cells.get("跟踪号"));
        // 销售出库单上的「运费」字段是 freight（列名 freight）；历史调用写成 deliveryCost 会导致整包编译不过
        if (hasValue(cells, "运费")) d.setFreight(new BigDecimal(cells.get("运费")));
        return d;
    }
    // ==================== 销售退货单导入 ====================
    @Override
    public ImportResultDTO importSaleReturns(MultipartFile file) {
        return importSaleBill(file, "退货单号", "sale_return", "sale_return_item",
                (cells, custCache, row) -> buildSaleReturn(cells, custCache, row),
                (cells, matCache, row) -> buildSaleItem(cells, matCache, row, SaleReturnItem.class),
                (header, items) -> saleReturnService.saveReturnWithItems((SaleReturn) header, (List<SaleReturnItem>) items));
    }

    private SaleReturn buildSaleReturn(Map<String, String> cells, Map<String, Customer> customerCache, int rowIndex) {
        SaleReturn r = new SaleReturn();
        applyDeclaredBillColumns("saleReturn", r, cells, rowIndex);
        Customer customer = resolveCustomer(cells, customerCache, rowIndex);
        r.setCustomerId(customer.getCustomerId());
        if (hasValue(cells, "退货单号")) r.setReturnNo(cells.get("退货单号"));
        if (hasValue(cells, "退货日期")) r.setReturnDate(parseDate(cells.get("退货日期"), rowIndex, "退货日期"));
        if (hasValue(cells, "联系人")) r.setContactPerson(cells.get("联系人"));
        if (hasValue(cells, "联系电话")) r.setContactPhone(cells.get("联系电话"));
        if (hasValue(cells, "备注")) r.setRemark(cells.get("备注"));
        if (hasValue(cells, "销售员ID")) r.setSalerId(Long.parseLong(cells.get("销售员ID")));
        if (hasValue(cells, "仓库ID")) r.setWarehouseId(Long.parseLong(cells.get("仓库ID")));
        if (hasValue(cells, "币种ID")) r.setCurrencyId(Long.parseLong(cells.get("币种ID")));
        else r.setCurrencyId(defaultCurrencyId());
        if (hasValue(cells, "汇率")) r.setExchangeRate(new BigDecimal(cells.get("汇率")));
        if (hasValue(cells, "折扣率")) r.setDiscountRate(new BigDecimal(cells.get("折扣率")));
        if (hasValue(cells, "出库单号")) r.setDeliveryNo(cells.get("出库单号"));
        if (hasValue(cells, "发票号")) r.setInvoiceNo(cells.get("发票号"));
        if (hasValue(cells, "退货原因")) r.setReturnReason(cells.get("退货原因"));
        if (hasValue(cells, "源单据类型")) r.setSourceBillType(cells.get("源单据类型"));
        if (hasValue(cells, "源单据号")) r.setSourceBillNo(cells.get("源单据号"));
        return r;
    }
    // ==================== 销售发票导入 ====================
    @Override
    public ImportResultDTO importSaleInvoices(MultipartFile file) {
        return importSaleBill(file, "发票号", "sale_invoice", "sale_invoice_item",
                (cells, custCache, row) -> buildSaleInvoice(cells, custCache, row),
                (cells, matCache, row) -> buildSaleItem(cells, matCache, row, SaleInvoiceItem.class),
                (header, items) -> saleInvoiceService.saveInvoiceWithItems((SaleInvoice) header, (List<SaleInvoiceItem>) items));
    }

    private SaleInvoice buildSaleInvoice(Map<String, String> cells, Map<String, Customer> customerCache, int rowIndex) {
        SaleInvoice inv = new SaleInvoice();
        Customer customer = resolveCustomer(cells, customerCache, rowIndex);
        inv.setCustomerId(customer.getCustomerId());
        if (hasValue(cells, "发票号")) inv.setInvoiceNo(cells.get("发票号"));
        if (hasValue(cells, "开票日期")) inv.setInvoiceDate(parseDate(cells.get("开票日期"), rowIndex, "开票日期"));
        if (hasValue(cells, "发票类型")) inv.setInvoiceType(cells.get("发票类型"));
        if (hasValue(cells, "税号")) inv.setTaxNo(cells.get("税号"));
        if (hasValue(cells, "地址")) inv.setAddress(cells.get("地址"));
        if (hasValue(cells, "开户银行")) inv.setBankName(cells.get("开户银行"));
        if (hasValue(cells, "银行账号")) inv.setBankAccount(cells.get("银行账号"));
        if (hasValue(cells, "备注")) inv.setRemark(cells.get("备注"));
        if (hasValue(cells, "销售员ID")) inv.setSalerId(Long.parseLong(cells.get("销售员ID")));
        if (hasValue(cells, "币种ID")) inv.setCurrencyId(Long.parseLong(cells.get("币种ID")));
        else inv.setCurrencyId(defaultCurrencyId());
        if (hasValue(cells, "汇率")) inv.setExchangeRate(new BigDecimal(cells.get("汇率")));
        if (hasValue(cells, "折扣率")) inv.setDiscountRate(new BigDecimal(cells.get("折扣率")));
        if (hasValue(cells, "出库单号")) inv.setDeliveryNo(cells.get("出库单号"));
        if (hasValue(cells, "源单据类型")) inv.setSourceBillType(cells.get("源单据类型"));
        if (hasValue(cells, "源单据号")) inv.setSourceBillNo(cells.get("源单据号"));
        return inv;
    }
    // ==================== 售后维修单导入（2026-11-19 恢复通用导入） ====================
    @Override
    public ImportResultDTO importSaleRepairs(MultipartFile file) {
        return importSaleBill(file, "维修单号", "sale_repair", "sale_repair_item",
                this::buildSaleRepair,
                (cells, matCache, row) -> buildSaleRepairItem(cells, matCache, row),
                (header, items) -> saleRepairService.saveRepairWithItems((com.erp.sale.entity.SaleRepair) header,
                        (List<com.erp.sale.entity.SaleRepairItem>) items, new ArrayList<>()));
    }

    private com.erp.sale.entity.SaleRepair buildSaleRepair(Map<String, String> cells,
                                                            Map<String, Customer> customerCache, int rowIndex) {
        com.erp.sale.entity.SaleRepair r = new com.erp.sale.entity.SaleRepair();
        Customer customer = resolveCustomer(cells, customerCache, rowIndex);
        r.setCustomerId(customer.getCustomerId());
        r.setCustomerName(customer.getCustomerName());
        if (hasValue(cells, "维修单号")) r.setRepairNo(cells.get("维修单号"));
        if (hasValue(cells, "维修日期")) r.setRepairDate(parseDate(cells.get("维修日期"), rowIndex, "维修日期"));
        if (hasValue(cells, "联系人")) r.setContactPerson(cells.get("联系人"));
        if (hasValue(cells, "联系电话")) r.setContactPhone(cells.get("联系电话"));
        if (hasValue(cells, "服务方式")) r.setServiceType(cells.get("服务方式"));
        if (hasValue(cells, "保修判定")) r.setWarrantyStatus(cells.get("保修判定"));
        if (hasValue(cells, "故障现象")) r.setFaultDescription(cells.get("故障现象"));
        if (hasValue(cells, "维修费")) r.setRepairFee(new BigDecimal(cells.get("维修费")));
        if (hasValue(cells, "配件费")) r.setPartsFee(new BigDecimal(cells.get("配件费")));
        if (hasValue(cells, "备注")) r.setRemark(cells.get("备注"));
        return r;
    }

    private Object buildSaleRepairItem(Map<String, String> cells, Map<String, Material> materialCache, int rowIndex) {
        Material material = resolveMaterial(cells, materialCache, rowIndex);
        com.erp.sale.entity.SaleRepairItem item = new com.erp.sale.entity.SaleRepairItem();
        item.setMaterialId(material.getMaterialId());
        item.setMaterialCode(material.getMaterialCode());
        item.setMaterialName(hasValue(cells, "物料名称") ? cells.get("物料名称") : material.getMaterialName());
        item.setSpecification(hasValue(cells, "规格型号") ? cells.get("规格型号") : material.getModel());
        if (hasValue(cells, "单位")) item.setUnit(cells.get("单位"));
        if (hasValue(cells, "序列号")) item.setSerialNo(cells.get("序列号"));
        if (hasValue(cells, "批号")) item.setBatchNo(cells.get("批号"));
        item.setQuantity(parseBigDecimal(cells, "数量", rowIndex, true));
        if (hasValue(cells, "故障描述")) item.setFaultDescription(cells.get("故障描述"));
        if (hasValue(cells, "维修方案")) item.setRepairPlan(cells.get("维修方案"));
        if (hasValue(cells, "工时")) item.setLaborHours(parseBigDecimal(cells, "工时", rowIndex, false));
        if (hasValue(cells, "明细备注")) item.setRemark(cells.get("明细备注"));
        return item;
    }

    // ==================== 打样单 / 免费样品订单导入（2026-11-27 补） ====================
    @Override
    public ImportResultDTO importSampleOrders(MultipartFile file) {
        return importSaleBill(file, "打样单号", "sample_order", "sample_order_item",
                (cells, customerCache, rowIndex) -> {
                    com.erp.sample.entity.SampleOrder h = new com.erp.sample.entity.SampleOrder();
                    Customer customer = resolveCustomer(cells, customerCache, rowIndex);
                    h.setCustomerId(customer.getCustomerId());
                    h.setCustomerName(customer.getCustomerName());
                    return h;
                },
                this::buildSampleItem,
                (header, items) -> sampleOrderService.saveSampleWithItems(
                        (com.erp.sample.entity.SampleOrder) header,
                        (List<com.erp.sample.entity.SampleOrderItem>) items));
    }

    private Object buildSampleItem(Map<String, String> cells, Map<String, Material> materialCache, int rowIndex) {
        Material material = resolveMaterial(cells, materialCache, rowIndex);
        com.erp.sample.entity.SampleOrderItem item = new com.erp.sample.entity.SampleOrderItem();
        item.setChildMaterialId(material.getMaterialId());
        item.setChildMaterialCode(material.getMaterialCode());
        item.setChildMaterialName(hasValue(cells, "物料名称") ? cells.get("物料名称") : material.getMaterialName());
        item.setSpecification(hasValue(cells, "规格型号") ? cells.get("规格型号") : material.getModel());
        item.setBaseQty(parseBigDecimal(cells, "用量", rowIndex, true));
        if (hasValue(cells, "用量类型")) item.setQtyType(cells.get("用量类型"));
        if (hasValue(cells, "损耗率(%)")) item.setScrapRate(parseBigDecimal(cells, "损耗率(%)", rowIndex, false));
        if (hasValue(cells, "供应类型")) item.setSupplyType(cells.get("供应类型"));
        if (hasValue(cells, "单价")) item.setUnitPrice(parseBigDecimal(cells, "单价", rowIndex, false));
        if (hasValue(cells, "金额")) item.setAmount(parseBigDecimal(cells, "金额", rowIndex, false));
        if (hasValue(cells, "备注")) item.setRemark(cells.get("备注"));
        return item;
    }

    @Override
    public ImportResultDTO importFreeSampleOrders(MultipartFile file) {
        Map<String, com.erp.basedata.entity.Warehouse> whCache = new HashMap<>();
        return importSaleBill(file, "订单号", "free_sample_order", "free_sample_order_item",
                (cells, customerCache, rowIndex) -> {
                    com.erp.freesample.entity.FreeSampleOrder h = new com.erp.freesample.entity.FreeSampleOrder();
                    Customer customer = resolveCustomer(cells, customerCache, rowIndex);
                    h.setCustomerId(customer.getCustomerId());
                    h.setCustomerName(customer.getCustomerName());
                    if (hasValue(cells, "仓库")) {
                        com.erp.basedata.entity.Warehouse wh = resolveWarehouseByName(cells, whCache, rowIndex, "仓库");
                        h.setWarehouseId(wh.getStockId());
                        h.setWarehouseName(wh.getStockName());
                    }
                    return h;
                },
                this::buildFreeSampleItem,
                (header, items) -> {
                    com.erp.freesample.entity.FreeSampleOrder h = (com.erp.freesample.entity.FreeSampleOrder) header;
                    h.setItems((List<com.erp.freesample.entity.FreeSampleOrderItem>) items);
                    freeSampleOrderService.saveWithItems(h);
                });
    }

    private Object buildFreeSampleItem(Map<String, String> cells, Map<String, Material> materialCache, int rowIndex) {
        Material material = resolveMaterial(cells, materialCache, rowIndex);
        com.erp.freesample.entity.FreeSampleOrderItem item = new com.erp.freesample.entity.FreeSampleOrderItem();
        item.setMaterialId(material.getMaterialId());
        item.setMaterialCode(material.getMaterialCode());
        item.setMaterialName(hasValue(cells, "物料名称") ? cells.get("物料名称") : material.getMaterialName());
        item.setModel(hasValue(cells, "规格型号") ? cells.get("规格型号") : material.getModel());
        if (hasValue(cells, "颜色")) item.setColor(cells.get("颜色"));
        item.setQuantity(parseBigDecimal(cells, "数量", rowIndex, true));
        if (hasValue(cells, "单位")) {
            item.setUnitName(cells.get("单位"));
            item.setUnitId(resolveUnitIdByName(cells.get("单位")));
        }
        if (hasValue(cells, "库位名称")) item.setBinName(cells.get("库位名称"));
        if (hasValue(cells, "备注")) item.setRemark(cells.get("备注"));
        return item;
    }

    // ==================== 通用导入工具方法 ====================

    /** 通用主子表导入框架：解析Excel→聚合→逐单校验保存 */
    private ImportResultDTO importSaleBill(MultipartFile file, String noLabel,
                                           String headerEntityType, String itemEntityType,
                                           TriFunction<Map<String, String>, Map<String, Customer>, Integer, Object> headerBuilder,
                                           TriFunction<Map<String, String>, Map<String, Material>, Integer, Object> itemBuilder,
                                           BiConsumer<Object, List<?>> saver) {
        ImportResultDTO result = new ImportResultDTO();
        // 字段权限（角色必填）一次性解析：循环内复用，避免逐单查库
        com.erp.permission.service.FieldRequiredPermissionService.PageRequired permHdrRequired = resolvePermRequired(headerEntityType);
        com.erp.permission.service.FieldRequiredPermissionService.PageRequired permItemRequired = resolvePermRequired(itemEntityType);
        try (InputStream inputStream = file.getInputStream()) {
            List<Object> rawData = EasyExcel.read(inputStream).headRowNumber(0).sheet().doReadSync();
            List<List<Object>> excelData = convertExcelRowsToList(rawData);
            if (excelData.size() < 2) { result.setTotalCount(0); return result; }

            List<String> headers = convertToStringList(excelData.get(0));
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) idx.put(stripRequiredMark(headers.get(i).trim()), i);
            // 表头缺少物料列时一次性拦截并说清怎么处理（2026-09-22）：
            // 历史现象：客户用了表头被改过的文件/数据填在第 2 个 sheet → 逐行报"物料编码不能为空"，
            // 用户以为系统找不到物料（其实物料库里有）。这里把"文件问题"和"数据问题"分开报。
            if (!idx.containsKey("物料编码") && !idx.containsKey("物料名称")) {
                throw new IllegalArgumentException("导入文件缺少「物料编码」列（也没找到「物料名称」列）："
                        + "请在列表页点「导入模板」下载最新模板，把数据填在模板第一个 sheet 再导入；"
                        + "若模板里明明有该列，请检查表头是否被改动、或数据是否填到了第 2 个 sheet。");
            }

            Map<String, Customer> customerCache = new HashMap<>();
            Map<String, Material> materialCache = new HashMap<>();
            Map<String, com.erp.basedata.entity.Warehouse> warehouseCache = new HashMap<>();
            List<com.erp.customfield.entity.CustomField> hdrFields = getEnabledCustomFields(headerEntityType);
            List<com.erp.customfield.entity.CustomField> itemFields = getEnabledCustomFields(itemEntityType);

            List<Map<String, String>> headerRows = new ArrayList<>();
            List<List<Map<String, String>>> itemRowsPerBill = new ArrayList<>();
            List<Map<String, String>> currentItems = null;
            String prevGroupKey = "";
            String prevCustomerKey = "";

            for (int r = 1; r < excelData.size(); r++) {
                List<Object> row = excelData.get(r);
                if (isRowEmpty(row)) continue;
                Map<String, String> cells = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> e : idx.entrySet()) {
                    int i = e.getValue();
                    cells.put(e.getKey(), (i < row.size() && row.get(i) != null) ? row.get(i).toString().trim() : "");
                }
                // 2026-11-14：新单判定改为「单号 → 单据分组号 → 客户变化」；
                // 同一客户在每行重复填写不再拆单（历史行为会把一张单拆成多张）。
                String groupKey = importGroupKey(cells, noLabel);
                String customerKey = importCustomerKey(cells);
                boolean newBill = currentItems == null
                        || (!groupKey.isEmpty() && !groupKey.equals(prevGroupKey))
                        || (groupKey.isEmpty() && !customerKey.isEmpty() && !customerKey.equals(prevCustomerKey));
                if (newBill) {
                    headerRows.add(cells);
                    currentItems = new ArrayList<>();
                    itemRowsPerBill.add(currentItems);
                    if (!groupKey.isEmpty()) prevGroupKey = groupKey;
                    if (!customerKey.isEmpty()) prevCustomerKey = customerKey;
                }
                currentItems.add(cells);
            }

            result.setTotalCount(headerRows.size());
            for (int o = 0; o < headerRows.size(); o++) {
                int startRow = rowNumberOfOrder(itemRowsPerBill, o);
                try {
                    Object header = headerBuilder.apply(headerRows.get(o), customerCache, startRow);
                    applyGenericHeaderColumns(header, headerRows.get(o));
                    Long headerWarehouseId = getHeaderWarehouseId(header);
                    List<Object> items = new ArrayList<>();
                    String itemError = null;
                    for (Map<String, String> cells : itemRowsPerBill.get(o)) {
                        try {
                            Object item = itemBuilder.apply(cells, materialCache, startRow);
                            applyItemExtraColumns(item, cells, warehouseCache, headerWarehouseId, startRow);
                            items.add(item);
                        } catch (Exception e) { itemError = e.getMessage() != null ? e.getMessage() : "明细解析失败"; break; }
                    }
                    if (itemError != null) { result.setFailCount(result.getFailCount() + 1); result.addError(startRow, "", itemError); continue; }
                    if (items.isEmpty()) { result.setFailCount(result.getFailCount() + 1); result.addError(startRow, "", "单据无有效明细行"); continue; }
                    if (!checkBillRequiredFields(headerRows.get(o), itemRowsPerBill.get(o),
                            hdrFields, itemFields, idx, startRow, result,
                            permHdrRequired, permItemRequired, headerEntityType, itemEntityType)) {
                        result.setFailCount(result.getFailCount() + 1);
                        continue;
                    }
                    ImportBillStatus want = parseImportStatus(headerRows.get(o));
                    // 回填自定义字段（表头+明细）
                    Long headerId = saveImportedBill(header, items, saver, headerEntityType, want, result);
                    saveSaleCustomFields(headerEntityType, headerId, headerRows.get(o), hdrFields);
                    for (int i = 0; i < items.size(); i++) {
                        Long itemId = extractEntityId(items.get(i));
                        saveSaleCustomFields(itemEntityType, itemId, itemRowsPerBill.get(o).get(i), itemFields);
                    }
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception e) {
                    result.setFailCount(result.getFailCount() + 1);
                    result.addError(startRow, "", e.getMessage() != null ? e.getMessage() : "保存失败");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("销售单据导入失败", e);
        }
        return result;
    }

    /** 客户解析：ID/编码/名称三选一 */
    private Customer resolveCustomer(Map<String, String> cells, Map<String, Customer> cache, int rowIndex) {
        Customer customer = null;
        if (hasValue(cells, "客户ID")) {
            try { customer = customerMapper.selectById(Long.parseLong(cells.get("客户ID"))); } catch (NumberFormatException ignored) {}
            if (customer == null) throw new IllegalArgumentException("第" + rowIndex + "行: 客户ID[" + cells.get("客户ID") + "]不存在");
        } else if (hasValue(cells, "客户编码")) {
            customer = cache.computeIfAbsent("no:" + cells.get("客户编码"),
                    k -> customerMapper.selectOne(new QueryWrapper<Customer>().eq("customer_code", k.substring(3)).last("LIMIT 1")));
            if (customer == null) throw new IllegalArgumentException("第" + rowIndex + "行: 客户编码[" + cells.get("客户编码") + "]不存在");
        } else if (hasValue(cells, "客户名称")) {
            customer = cache.computeIfAbsent("name:" + cells.get("客户名称"),
                    k -> customerMapper.selectOne(new QueryWrapper<Customer>().eq("customer_name", k.substring(5)).last("LIMIT 1")));
            if (customer == null) throw new IllegalArgumentException("第" + rowIndex + "行: 客户名称[" + cells.get("客户名称") + "]不存在");
        } else {
            throw new IllegalArgumentException("第" + rowIndex + "行: 客户ID/客户编码/客户名称至少填一项");
        }
        return customer;
    }

    /**
     * 物料解析：**物料编码优先，缺失时按物料名称兜底**（2026-09-22 补齐）。
     *
     * <p>背景：121 生产环境导入其他入库单报"第N行: 物料编码不能为空"，客户确认物料库里确实有该物料。
     * 原因是本方法此前只认「物料编码」，客户文件里只填了「物料名称」（或该行编码列留空）；
     * 而客户/供应商解析都是 ID/编码/名称三选一 → 口径不一致属系统缺陷，故补齐：</p>
     * <ul>
     *   <li>编码有值 → 只按编码精确匹配（有值就不再按名称猜，避免同名歧义）；</li>
     *   <li>编码空、名称有值 → 按名称匹配；匹配到多条时**明确报错要求改填编码**，不静默取第一条；</li>
     *   <li>两者都空 → 报"物料编码/物料名称至少填一项"，方便客户定位到具体行。</li>
     * </ul>
     */
    private Material resolveMaterial(Map<String, String> cells, Map<String, Material> cache, int rowIndex) {
        String code = safeTrim(cells.get("物料编码"));
        if (!code.isEmpty()) {
            Material material = cache.computeIfAbsent(code,
                    k -> materialMapper.selectOne(new QueryWrapper<Material>().eq("material_code", k).last("LIMIT 1")));
            if (material == null) throw new IllegalArgumentException("第" + rowIndex + "行: 物料编码[" + code + "]不存在");
            return material;
        }
        String name = safeTrim(cells.get("物料名称"));
        if (!name.isEmpty()) {
            List<Material> byName = materialMapper.selectList(new QueryWrapper<Material>()
                    .eq("material_name", name).last("LIMIT 2"));
            if (byName.isEmpty()) {
                throw new IllegalArgumentException("第" + rowIndex + "行: 物料名称[" + name + "]不存在（请核对物料库，或改用物料编码）");
            }
            if (byName.size() > 1) {
                throw new IllegalArgumentException("第" + rowIndex + "行: 物料名称[" + name + "]在物料库中有多条同名物料，请改填物料编码");
            }
            return byName.get(0);
        }
        throw new IllegalArgumentException("第" + rowIndex + "行: 物料编码/物料名称至少填一项（推荐填物料编码）");
    }

    /** 去首尾空白并防 null（导入解析统一口径） */
    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    /** 供应商解析：ID/编码/名称三选一 */
    private com.erp.masterdata.entity.Supplier resolveSupplier(Map<String, String> cells, Map<String, com.erp.masterdata.entity.Supplier> cache, int rowIndex) {
        com.erp.masterdata.entity.Supplier supplier = null;
        if (hasValue(cells, "供应商ID")) {
            try { supplier = supplierMapper.selectById(Long.parseLong(cells.get("供应商ID"))); } catch (NumberFormatException ignored) {}
            if (supplier == null) throw new IllegalArgumentException("第" + rowIndex + "行: 供应商ID[" + cells.get("供应商ID") + "]不存在");
        } else if (hasValue(cells, "供应商编码")) {
            supplier = cache.computeIfAbsent("no:" + cells.get("供应商编码"),
                    k -> supplierMapper.selectOne(new QueryWrapper<com.erp.masterdata.entity.Supplier>().eq("supplier_code", k.substring(3)).last("LIMIT 1")));
            if (supplier == null) throw new IllegalArgumentException("第" + rowIndex + "行: 供应商编码[" + cells.get("供应商编码") + "]不存在");
        } else if (hasValue(cells, "供应商名称")) {
            supplier = cache.computeIfAbsent("name:" + cells.get("供应商名称"),
                    k -> supplierMapper.selectOne(new QueryWrapper<com.erp.masterdata.entity.Supplier>().eq("supplier_name", k.substring(5)).last("LIMIT 1")));
            if (supplier == null) throw new IllegalArgumentException("第" + rowIndex + "行: 供应商名称[" + cells.get("供应商名称") + "]不存在");
        } else {
            throw new IllegalArgumentException("第" + rowIndex + "行: 供应商ID/供应商编码/供应商名称至少填一项");
        }
        return supplier;
    }

    // ==================== 采购模块主子表导入 ====================

    /** 通用采购主子表导入框架：解析Excel→聚合→逐单校验保存 */
    private ImportResultDTO importPurchaseBill(MultipartFile file, String noLabel,
                                               String headerEntityType, String itemEntityType,
                                               TriFunction<Map<String, String>, Map<String, com.erp.masterdata.entity.Supplier>, Integer, Object> headerBuilder,
                                               TriFunction<Map<String, String>, Map<String, Material>, Integer, Object> itemBuilder,
                                               BiConsumer<Object, List<?>> saver) {
        ImportResultDTO result = new ImportResultDTO();
        // 字段权限（角色必填）一次性解析：循环内复用，避免逐单查库
        com.erp.permission.service.FieldRequiredPermissionService.PageRequired permHdrRequired = resolvePermRequired(headerEntityType);
        com.erp.permission.service.FieldRequiredPermissionService.PageRequired permItemRequired = resolvePermRequired(itemEntityType);
        try (InputStream inputStream = file.getInputStream()) {
            List<Object> rawData = EasyExcel.read(inputStream).headRowNumber(0).sheet().doReadSync();
            List<List<Object>> excelData = convertExcelRowsToList(rawData);
            if (excelData.size() < 2) { result.setTotalCount(0); return result; }

            List<String> headers = convertToStringList(excelData.get(0));
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) idx.put(stripRequiredMark(headers.get(i).trim()), i);

            Map<String, com.erp.masterdata.entity.Supplier> supplierCache = new HashMap<>();
            Map<String, Material> materialCache = new HashMap<>();
            Map<String, com.erp.basedata.entity.Warehouse> warehouseCache = new HashMap<>();
            List<com.erp.customfield.entity.CustomField> hdrFields = getEnabledCustomFields(headerEntityType);
            List<com.erp.customfield.entity.CustomField> itemFields = getEnabledCustomFields(itemEntityType);

            List<Map<String, String>> headerRows = new ArrayList<>();
            List<List<Map<String, String>>> itemRowsPerBill = new ArrayList<>();
            List<Map<String, String>> currentItems = null;
            String prevGroupKey = "";
            String prevSupplierKey = "";

            for (int r = 1; r < excelData.size(); r++) {
                List<Object> row = excelData.get(r);
                if (isRowEmpty(row)) continue;
                Map<String, String> cells = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> e : idx.entrySet()) {
                    int i = e.getValue();
                    cells.put(e.getKey(), (i < row.size() && row.get(i) != null) ? row.get(i).toString().trim() : "");
                }
                // 2026-11-14：新单判定改为「单号 → 单据分组号 → 供应商变化」；
                // 同一供应商在每行重复填写不再拆单。
                String groupKey = importGroupKey(cells, noLabel);
                String supplierKey = importSupplierKey(cells);
                boolean newBill = currentItems == null
                        || (!groupKey.isEmpty() && !groupKey.equals(prevGroupKey))
                        || (groupKey.isEmpty() && !supplierKey.isEmpty() && !supplierKey.equals(prevSupplierKey));
                if (newBill) {
                    headerRows.add(cells);
                    currentItems = new ArrayList<>();
                    itemRowsPerBill.add(currentItems);
                    if (!groupKey.isEmpty()) prevGroupKey = groupKey;
                    if (!supplierKey.isEmpty()) prevSupplierKey = supplierKey;
                }
                currentItems.add(cells);
            }

            result.setTotalCount(headerRows.size());
            for (int o = 0; o < headerRows.size(); o++) {
                int startRow = rowNumberOfOrder(itemRowsPerBill, o);
                try {
                    Object header = headerBuilder.apply(headerRows.get(o), supplierCache, startRow);
                    applyGenericHeaderColumns(header, headerRows.get(o));
                    Long headerWarehouseId = getHeaderWarehouseId(header);
                    List<Object> items = new ArrayList<>();
                    String itemError = null;
                    for (Map<String, String> cells : itemRowsPerBill.get(o)) {
                        try {
                            Object item = itemBuilder.apply(cells, materialCache, startRow);
                            applyItemExtraColumns(item, cells, warehouseCache, headerWarehouseId, startRow);
                            items.add(item);
                        } catch (Exception e) { itemError = e.getMessage() != null ? e.getMessage() : "明细解析失败"; break; }
                    }
                    if (itemError != null) { result.setFailCount(result.getFailCount() + 1); result.addError(startRow, "", itemError); continue; }
                    if (items.isEmpty()) { result.setFailCount(result.getFailCount() + 1); result.addError(startRow, "", "单据无有效明细行"); continue; }
                    if (!checkBillRequiredFields(headerRows.get(o), itemRowsPerBill.get(o),
                            hdrFields, itemFields, idx, startRow, result,
                            permHdrRequired, permItemRequired, headerEntityType, itemEntityType)) {
                        result.setFailCount(result.getFailCount() + 1);
                        continue;
                    }
                    ImportBillStatus want = parseImportStatus(headerRows.get(o));
                    Long headerId = saveImportedBill(header, items, saver, headerEntityType, want, result);
                    saveSaleCustomFields(headerEntityType, headerId, headerRows.get(o), hdrFields);
                    for (int i = 0; i < items.size(); i++) {
                        Long itemId = extractEntityId(items.get(i));
                        saveSaleCustomFields(itemEntityType, itemId, itemRowsPerBill.get(o).get(i), itemFields);
                    }
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception e) {
                    result.setFailCount(result.getFailCount() + 1);
                    result.addError(startRow, "", e.getMessage() != null ? e.getMessage() : "保存失败");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("采购单据导入失败", e);
        }
        return result;
    }

    /** 采购明细构建（通用） */
    /**
     * 【通用构建器·采购明细】按数据库列名落值，一套代码覆盖采购 6 张单据的全部明细实体。
     * 不再为"属性名不一致"的单据单写构建器（历史 buildPurchaseRequestItem 已删除，改由本方法 + setByColumn 覆盖）。
     */
    private Object buildPurchaseItem(Map<String, String> cells, Map<String, Material> materialCache, int rowIndex, Class<?> itemClass) {
        Material material = resolveMaterial(cells, materialCache, rowIndex);
        try {
            Object item = itemClass.getDeclaredConstructor().newInstance();
            itemClass.getMethod("setMaterialId", Long.class).invoke(item, material.getMaterialId());
            itemClass.getMethod("setMaterialCode", String.class).invoke(item, material.getMaterialCode());
            setByColumn(item, "material_name", hasValue(cells, "物料名称") ? cells.get("物料名称") : material.getMaterialName());
            setByColumn(item, "specification", hasValue(cells, "规格型号") ? cells.get("规格型号") : material.getModel());
            setByAnyColumn(item, parseBigDecimal(cells, "数量", rowIndex, true), QTY_COLUMNS);
            if (hasValue(cells, "单价")) setByColumn(item, "unit_price", parseBigDecimal(cells, "单价", rowIndex, false));
            if (hasValue(cells, "税率(%)")) setByColumn(item, "tax_rate", parseBigDecimal(cells, "税率(%)", rowIndex, false));
            if (hasValue(cells, "折扣率(%)")) setByColumn(item, "discount_rate", parseBigDecimal(cells, "折扣率(%)", rowIndex, false));
            if (hasValue(cells, "金额")) setByColumn(item, "amount", parseBigDecimal(cells, "金额", rowIndex, false));
            String dateRaw = hasValue(cells, "交货日期") ? cells.get("交货日期")
                    : (hasValue(cells, "需求日期") ? cells.get("需求日期") : null);
            if (dateRaw != null) setByAnyColumn(item, parseDate(dateRaw, rowIndex, "交货/需求日期"), DATE_COLUMNS);
            if (hasValue(cells, "明细备注")) setByAnyColumn(item, cells.get("明细备注"), REMARK_COLUMNS);
            return item;
        } catch (Exception e) {
            throw new IllegalArgumentException("第" + rowIndex + "行: 明细构建失败: " + e.getMessage());
        }
    }

    @Override
    public ImportResultDTO importPurchaseRequests(MultipartFile file) {
        return importPurchaseBill(file, "申请单号", "purchase_request", "purchase_request_item",
                (cells, supCache, row) -> {
                    com.erp.purchase.entity.PurchaseRequest h = new com.erp.purchase.entity.PurchaseRequest();
                    if (hasValue(cells, "申请单号")) h.setRequestNo(cells.get("申请单号"));
                    if (hasValue(cells, "申请日期")) h.setRequestDate(parseDate(cells.get("申请日期"), row, "申请日期"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                (cells, matCache, row) -> buildPurchaseItem(cells, matCache, row, com.erp.purchase.entity.PurchaseRequestItem.class),
                (header, items) -> purchaseRequestService.saveRequestWithItems((com.erp.purchase.entity.PurchaseRequest) header, (List<com.erp.purchase.entity.PurchaseRequestItem>) items));
    }

    @Override
    public ImportResultDTO importPurchaseOrders(MultipartFile file) {
        return importPurchaseBill(file, "订单号", "purchase_order", "purchase_order_item",
                (cells, supCache, row) -> {
                    com.erp.purchase.entity.PurchaseOrder h = new com.erp.purchase.entity.PurchaseOrder();
                    com.erp.masterdata.entity.Supplier s = resolveSupplier(cells, supCache, row);
                    h.setSupplierId(s.getSupplierId());
                    if (hasValue(cells, "订单号")) h.setOrderNo(cells.get("订单号"));
                    if (hasValue(cells, "订单日期")) h.setOrderDate(parseDate(cells.get("订单日期"), row, "订单日期"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                (cells, matCache, row) -> buildPurchaseItem(cells, matCache, row, com.erp.purchase.entity.PurchaseOrderItem.class),
                (header, items) -> purchaseOrderService.saveOrderWithItems((com.erp.purchase.entity.PurchaseOrder) header, (List<com.erp.purchase.entity.PurchaseOrderItem>) items));
    }

    @Override
    public ImportResultDTO importPurchaseContracts(MultipartFile file) {
        return importPurchaseBill(file, "合同号", "purchase_contract", "purchase_contract_item",
                (cells, supCache, row) -> {
                    com.erp.purchase.entity.PurchaseContract h = new com.erp.purchase.entity.PurchaseContract();
                    com.erp.masterdata.entity.Supplier s = resolveSupplier(cells, supCache, row);
                    h.setSupplierId(s.getSupplierId());
                    if (hasValue(cells, "合同号")) h.setContractNo(cells.get("合同号"));
                    if (hasValue(cells, "合同日期")) h.setContractDate(parseDate(cells.get("合同日期"), row, "合同日期"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                (cells, matCache, row) -> buildPurchaseItem(cells, matCache, row, com.erp.purchase.entity.PurchaseContractItem.class),
                (header, items) -> purchaseContractService.saveContractWithItems((com.erp.purchase.entity.PurchaseContract) header, (List<com.erp.purchase.entity.PurchaseContractItem>) items));
    }

    @Override
    public ImportResultDTO importPurchaseInbounds(MultipartFile file) {
        return importPurchaseBill(file, "入库单号", "purchase_inbound", "purchase_inbound_item",
                (cells, supCache, row) -> {
                    com.erp.purchase.entity.PurchaseInbound h = new com.erp.purchase.entity.PurchaseInbound();
                    com.erp.masterdata.entity.Supplier s = resolveSupplier(cells, supCache, row);
                    h.setSupplierId(s.getSupplierId());
                    applyDeclaredBillColumns("purchaseInbound", h, cells, row);
                    if (hasValue(cells, "入库单号")) h.setReceiptNo(cells.get("入库单号"));
                    if (hasValue(cells, "入库日期")) h.setInDate(parseDate(cells.get("入库日期"), row, "入库日期"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                (cells, matCache, row) -> buildPurchaseItem(cells, matCache, row, com.erp.purchase.entity.PurchaseInboundItem.class),
                (header, items) -> purchaseInboundService.saveInboundWithItems((com.erp.purchase.entity.PurchaseInbound) header, (List<com.erp.purchase.entity.PurchaseInboundItem>) items));
    }

    @Override
    public ImportResultDTO importPurchaseReturns(MultipartFile file) {
        return importPurchaseBill(file, "退货单号", "purchase_return", "purchase_return_item",
                (cells, supCache, row) -> {
                    com.erp.purchase.entity.PurchaseReturn h = new com.erp.purchase.entity.PurchaseReturn();
                    com.erp.masterdata.entity.Supplier s = resolveSupplier(cells, supCache, row);
                    h.setSupplierId(s.getSupplierId());
                    applyDeclaredBillColumns("purchaseReturn", h, cells, row);
                    if (hasValue(cells, "退货单号")) h.setReturnNo(cells.get("退货单号"));
                    if (hasValue(cells, "退货日期")) h.setReturnDate(parseDate(cells.get("退货日期"), row, "退货日期"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                (cells, matCache, row) -> buildPurchaseItem(cells, matCache, row, com.erp.purchase.entity.PurchaseReturnItem.class),
                (header, items) -> purchaseReturnService.saveReturnWithItems((com.erp.purchase.entity.PurchaseReturn) header, (List<com.erp.purchase.entity.PurchaseReturnItem>) items));
    }

    @Override
    public ImportResultDTO importPurchaseInvoices(MultipartFile file) {
        return importPurchaseBill(file, "发票号", "purchase_invoice", "purchase_invoice_item",
                (cells, supCache, row) -> {
                    com.erp.purchase.entity.PurchaseInvoice h = new com.erp.purchase.entity.PurchaseInvoice();
                    com.erp.masterdata.entity.Supplier s = resolveSupplier(cells, supCache, row);
                    h.setSupplierId(s.getSupplierId());
                    applyDeclaredBillColumns("purchaseInvoice", h, cells, row);
                    if (hasValue(cells, "发票号")) h.setInvoiceNo(cells.get("发票号"));
                    if (hasValue(cells, "发票日期")) h.setInvoiceDate(parseDate(cells.get("发票日期"), row, "发票日期"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                (cells, matCache, row) -> buildPurchaseItem(cells, matCache, row, com.erp.purchase.entity.PurchaseInvoiceItem.class),
                (header, items) -> purchaseInvoiceService.saveInvoiceWithItems((com.erp.purchase.entity.PurchaseInvoice) header, (List<com.erp.purchase.entity.PurchaseInvoiceItem>) items));
    }

    // ==================== 库存单据导入 ====================

    /** 库存单据通用导入（按单号或仓库判定新单） */
    private ImportResultDTO importInventoryBill(MultipartFile file, String noLabel,
                                                String headerEntityType, String itemEntityType,
                                                TriFunction<Map<String, String>, Map<String, com.erp.basedata.entity.Warehouse>, Integer, Object> headerBuilder,
                                                TriFunction<Map<String, String>, Map<String, Material>, Integer, Object> itemBuilder,
                                                BiConsumer<Object, List<?>> saver) {
        return importInventoryBill(file, noLabel, headerEntityType, itemEntityType, "仓库名称", headerBuilder, itemBuilder, saver);
    }

    /**
     * 同上，但可指定「表头起始行标记列」：库存单据模板的仓库列叫「仓库名称」，
     * 生产组装/拆卸单模板的仓库列叫「仓库」，标记列取错会把一张多行单据拆成多张单。
     */
    private ImportResultDTO importInventoryBill(MultipartFile file, String noLabel,
                                                String headerEntityType, String itemEntityType,
                                                String headerMarkerCol,
                                                TriFunction<Map<String, String>, Map<String, com.erp.basedata.entity.Warehouse>, Integer, Object> headerBuilder,
                                                TriFunction<Map<String, String>, Map<String, Material>, Integer, Object> itemBuilder,
                                                BiConsumer<Object, List<?>> saver) {
        ImportResultDTO result = new ImportResultDTO();
        // 字段权限（角色必填）一次性解析：循环内复用，避免逐单查库
        com.erp.permission.service.FieldRequiredPermissionService.PageRequired permHdrRequired = resolvePermRequired(headerEntityType);
        com.erp.permission.service.FieldRequiredPermissionService.PageRequired permItemRequired = resolvePermRequired(itemEntityType);
        try (InputStream inputStream = file.getInputStream()) {
            List<Object> rawData = EasyExcel.read(inputStream).headRowNumber(0).sheet().doReadSync();
            List<List<Object>> excelData = convertExcelRowsToList(rawData);
            if (excelData.size() < 2) { result.setTotalCount(0); return result; }

            List<String> headers = convertToStringList(excelData.get(0));
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) idx.put(stripRequiredMark(headers.get(i).trim()), i);
            // 表头缺少物料列 → 一次性报"文件问题"（2026-09-22，121 生产实报：客户文件表头不对，
            // 却逐行提示"物料编码不能为空"，用户误以为系统找不到物料）
            if (!idx.containsKey("物料编码") && !idx.containsKey("物料名称")) {
                throw new IllegalArgumentException("导入文件缺少「物料编码」列（也没找到「物料名称」列）："
                        + "请在列表页点「导入模板」下载最新模板，把数据填在模板第一个 sheet 再导入；"
                        + "若模板里明明有该列，请检查表头是否被改动、或数据是否填到了第 2 个 sheet。");
            }

            Map<String, com.erp.basedata.entity.Warehouse> warehouseCache = new HashMap<>();
            Map<String, Material> materialCache = new HashMap<>();
            List<com.erp.customfield.entity.CustomField> hdrFields = getEnabledCustomFields(headerEntityType);
            List<com.erp.customfield.entity.CustomField> itemFields = getEnabledCustomFields(itemEntityType);

            List<Map<String, String>> headerRows = new ArrayList<>();
            List<List<Map<String, String>>> itemRowsPerBill = new ArrayList<>();
            List<Map<String, String>> currentItems = null;
            String prevGroupKey = "";

            for (int r = 1; r < excelData.size(); r++) {
                List<Object> row = excelData.get(r);
                if (isRowEmpty(row)) continue;
                Map<String, String> cells = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> e : idx.entrySet()) {
                    int i = e.getValue();
                    cells.put(e.getKey(), (i < row.size() && row.get(i) != null) ? row.get(i).toString().trim() : "");
                }
                // 2026-11-14 修复：仓库不再作为"新单开始"的标记（模板示例每行都有仓库，会把一张多行单拆成多张）。
                // 新单判定只看「单号」或「单据分组号」；都为空 = 沿用上一张单。
                String groupKey = importGroupKey(cells, noLabel);
                boolean newBill = currentItems == null
                        || (!groupKey.isEmpty() && !groupKey.equals(prevGroupKey));
                if (newBill) {
                    headerRows.add(cells);
                    currentItems = new ArrayList<>();
                    itemRowsPerBill.add(currentItems);
                    if (!groupKey.isEmpty()) prevGroupKey = groupKey;
                }
                currentItems.add(cells);
            }

            result.setTotalCount(headerRows.size());
            for (int o = 0; o < headerRows.size(); o++) {
                int startRow = rowNumberOfOrder(itemRowsPerBill, o);
                try {
                    Object header = headerBuilder.apply(headerRows.get(o), warehouseCache, startRow);
                    applyGenericHeaderColumns(header, headerRows.get(o));
                    Long headerWarehouseId = getHeaderWarehouseId(header);
                    List<Object> items = new ArrayList<>();
                    String itemError = null;
                    for (Map<String, String> cells : itemRowsPerBill.get(o)) {
                        try {
                            Object item = itemBuilder.apply(cells, materialCache, startRow);
                            applyItemExtraColumns(item, cells, warehouseCache, headerWarehouseId, startRow);
                            items.add(item);
                        } catch (Exception e) { itemError = e.getMessage() != null ? e.getMessage() : "明细解析失败"; break; }
                    }
                    if (itemError != null) { result.setFailCount(result.getFailCount() + 1); result.addError(startRow, "", itemError); continue; }
                    if (items.isEmpty()) { result.setFailCount(result.getFailCount() + 1); result.addError(startRow, "", "单据无有效明细行"); continue; }
                    if (!checkBillRequiredFields(headerRows.get(o), itemRowsPerBill.get(o),
                            hdrFields, itemFields, idx, startRow, result,
                            permHdrRequired, permItemRequired, headerEntityType, itemEntityType)) {
                        result.setFailCount(result.getFailCount() + 1);
                        continue;
                    }
                    ImportBillStatus want = parseImportStatus(headerRows.get(o));
                    Long headerId = saveImportedBill(header, items, saver, headerEntityType, want, result);
                    saveSaleCustomFields(headerEntityType, headerId, headerRows.get(o), hdrFields);
                    for (int i = 0; i < items.size(); i++) {
                        Long itemId = extractEntityId(items.get(i));
                        saveSaleCustomFields(itemEntityType, itemId, itemRowsPerBill.get(o).get(i), itemFields);
                    }
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception e) {
                    result.setFailCount(result.getFailCount() + 1);
                    result.addError(startRow, "", e.getMessage() != null ? e.getMessage() : "保存失败");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("库存单据导入失败", e);
        }
        return result;
    }

    /** 仓库解析（按仓库名称，带缓存） */
    private com.erp.basedata.entity.Warehouse resolveWarehouse(Map<String, String> cells, Map<String, com.erp.basedata.entity.Warehouse> cache, int rowIndex) {
        return resolveWarehouseByName(cells, cache, rowIndex, "仓库名称");
    }

    /** 库存明细构建（通用） */
    /**
     * 【通用构建器·库存明细】按数据库列名落值，一套代码覆盖库存 5 张单据的明细实体
     * （入库/出库/调拨/领料的"数量"落在 quantity，盘点单落在 check_quantity）。
     */
    private Object buildInventoryItem(Map<String, String> cells, Map<String, Material> materialCache, int rowIndex, Class<?> itemClass) {
        Material material = resolveMaterial(cells, materialCache, rowIndex);
        try {
            Object item = itemClass.getDeclaredConstructor().newInstance();
            itemClass.getMethod("setMaterialId", Long.class).invoke(item, material.getMaterialId());
            itemClass.getMethod("setMaterialCode", String.class).invoke(item, material.getMaterialCode());
            setByColumn(item, "material_name", hasValue(cells, "物料名称") ? cells.get("物料名称") : material.getMaterialName());
            if (hasValue(cells, "规格型号")) setByColumn(item, "specification", cells.get("规格型号"));
            setByAnyColumn(item, parseBigDecimal(cells, "数量", rowIndex, true), QTY_COLUMNS);
            if (hasValue(cells, "单价")) setByColumn(item, "unit_price", parseBigDecimal(cells, "单价", rowIndex, false));
            if (hasValue(cells, "金额")) setByColumn(item, "amount", parseBigDecimal(cells, "金额", rowIndex, false));
            if (hasValue(cells, "批号")) setByColumn(item, "batch_no", cells.get("批号"));
            if (hasValue(cells, "库位名称")) setByColumn(item, "bin_name", cells.get("库位名称"));
            if (hasValue(cells, "明细备注")) setByAnyColumn(item, cells.get("明细备注"), REMARK_COLUMNS);
            return item;
        } catch (Exception e) {
            throw new IllegalArgumentException("第" + rowIndex + "行: 明细构建失败: " + e.getMessage());
        }
    }

    @Override
    public ImportResultDTO importOtherReceipts(MultipartFile file) {
        return importInventoryBill(file, "入库单号", "other_receipt", "other_receipt_item",
                (cells, whCache, row) -> {
                    com.erp.inventory.entity.OtherReceipt h = new com.erp.inventory.entity.OtherReceipt();
                    com.erp.basedata.entity.Warehouse w = resolveWarehouse(cells, whCache, row);
                    h.setWarehouseId(w.getStockId());
                    if (hasValue(cells, "入库单号")) h.setReceiptNo(cells.get("入库单号"));
                    if (hasValue(cells, "入库日期")) h.setReceiptDate(parseDate(cells.get("入库日期"), row, "入库日期"));
                    if (hasValue(cells, "入库类型")) h.setReceiptType(cells.get("入库类型"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                (cells, matCache, row) -> buildInventoryItem(cells, matCache, row, com.erp.inventory.entity.OtherReceiptItem.class),
                (header, items) -> otherReceiptService.saveReceiptWithItems((com.erp.inventory.entity.OtherReceipt) header, (List<com.erp.inventory.entity.OtherReceiptItem>) items));
    }

    @Override
    public ImportResultDTO importOtherIssues(MultipartFile file) {
        return importInventoryBill(file, "出库单号", "other_issue", "other_issue_item",
                (cells, whCache, row) -> {
                    com.erp.inventory.entity.OtherIssue h = new com.erp.inventory.entity.OtherIssue();
                    com.erp.basedata.entity.Warehouse w = resolveWarehouse(cells, whCache, row);
                    h.setWarehouseId(w.getStockId());
                    if (hasValue(cells, "出库单号")) h.setIssueNo(cells.get("出库单号"));
                    if (hasValue(cells, "出库日期")) h.setIssueDate(parseDate(cells.get("出库日期"), row, "出库日期"));
                    if (hasValue(cells, "出库类型")) h.setIssueType(cells.get("出库类型"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                (cells, matCache, row) -> buildInventoryItem(cells, matCache, row, com.erp.inventory.entity.OtherIssueItem.class),
                (header, items) -> otherIssueService.saveIssueWithItems((com.erp.inventory.entity.OtherIssue) header, (List<com.erp.inventory.entity.OtherIssueItem>) items));
    }

    @Override
    public ImportResultDTO importStockTransfers(MultipartFile file) {
        return importInventoryBill(file, "调拨单号", "stock_transfer", "stock_transfer_item",
                (cells, whCache, row) -> {
                    com.erp.inventory.entity.StockTransfer h = new com.erp.inventory.entity.StockTransfer();
                    com.erp.basedata.entity.Warehouse outW = resolveWarehouseByName(cells, whCache, row, "调出仓库名称");
                    com.erp.basedata.entity.Warehouse inW = resolveWarehouseByName(cells, whCache, row, "调入仓库名称");
                    h.setFromStockId(outW.getStockId());
                    h.setToStockId(inW.getStockId());
                    if (hasValue(cells, "调拨单号")) h.setTransferNo(cells.get("调拨单号"));
                    if (hasValue(cells, "调拨日期")) h.setTransferDate(parseDate(cells.get("调拨日期"), row, "调拨日期"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                (cells, matCache, row) -> buildInventoryItem(cells, matCache, row, com.erp.inventory.entity.StockTransferItem.class),
                (header, items) -> stockTransferService.saveTransferWithItems((com.erp.inventory.entity.StockTransfer) header, (List<com.erp.inventory.entity.StockTransferItem>) items));
    }

    /** 按指定列名解析仓库 */
    private com.erp.basedata.entity.Warehouse resolveWarehouseByName(Map<String, String> cells, Map<String, com.erp.basedata.entity.Warehouse> cache, int rowIndex, String colName) {
        String name = cells.getOrDefault(colName, "").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("第" + rowIndex + "行: " + colName + "不能为空");
        if (cache.containsKey(name)) {
            com.erp.basedata.entity.Warehouse w = cache.get(name);
            if (w == null) throw new IllegalArgumentException("第" + rowIndex + "行: 仓库[" + name + "]不存在");
            return w;
        }
        com.erp.basedata.entity.Warehouse w = warehouseService.lambdaQuery().eq(com.erp.basedata.entity.Warehouse::getStockName, name).one();
        cache.put(name, w);
        if (w == null) throw new IllegalArgumentException("第" + rowIndex + "行: 仓库[" + name + "]不存在");
        return w;
    }

    @Override
    public ImportResultDTO importStockChecks(MultipartFile file) {
        return importInventoryBill(file, "盘点单号", "stock_check", "stock_check_item",
                (cells, whCache, row) -> {
                    com.erp.inventory.entity.StockCheck h = new com.erp.inventory.entity.StockCheck();
                    com.erp.basedata.entity.Warehouse w = resolveWarehouse(cells, whCache, row);
                    h.setWarehouseId(w.getStockId());
                    if (hasValue(cells, "盘点单号")) h.setCheckNo(cells.get("盘点单号"));
                    if (hasValue(cells, "盘点日期")) h.setCheckDate(parseDate(cells.get("盘点日期"), row, "盘点日期"));
                    if (hasValue(cells, "库存类型")) h.setStockType(cells.get("库存类型"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                (cells, matCache, row) -> buildInventoryItem(cells, matCache, row, com.erp.inventory.entity.StockCheckItem.class),
                (header, items) -> stockCheckService.saveCheckWithItems((com.erp.inventory.entity.StockCheck) header, (List<com.erp.inventory.entity.StockCheckItem>) items));
    }

    private ImportResultDTO importProduceAssembles(MultipartFile file) {
        return importAssembleBill(file, true);
    }

    private ImportResultDTO importProduceDisassembles(MultipartFile file) {
        return importAssembleBill(file, false);
    }

    /**
     * 生产组装/拆卸导入：主表 + 明细。模板列与导出同源（物料编码/名称/规格/单位/数量/单价…）。
     * 组装与拆卸实体字段一致，仅单号列和实体类不同。
     */
    private ImportResultDTO importAssembleBill(MultipartFile file, boolean assemble) {
        String noLabel = assemble ? "组装单号" : "拆卸单号";
        // 明细行仓库解析缓存（明细构建器拿不到表头构建器的缓存，单独一份，按名称缓存开销相同）
        Map<String, com.erp.basedata.entity.Warehouse> itemWhCache = new HashMap<>();
        return importInventoryBill(file, noLabel,
                assemble ? "produce_assemble" : "produce_disassemble",
                assemble ? "produce_assemble_detail" : "produce_disassemble_detail",
                "仓库",
                (cells, whCache, row) -> {
                    // 仓库必填（成品仓库=「仓库」列，原料仓库=「BOM仓库」列，空则与成品仓库相同）：
                    // 提交时逐行校验明细与原料行的仓库，导入不落仓库ID会导致单据永远提交不了
                    com.erp.basedata.entity.Warehouse wh = resolveWarehouseByName(cells, whCache, row, "仓库");
                    com.erp.basedata.entity.Warehouse whBom = hasValue(cells, "BOM仓库")
                            ? resolveWarehouseByName(cells, whCache, row, "BOM仓库") : wh;
                    if (assemble) {
                        com.erp.produce.assemble.entity.ProduceAssemble h = new com.erp.produce.assemble.entity.ProduceAssemble();
                        if (hasValue(cells, noLabel)) h.setSerialNo(cells.get(noLabel));
                        if (hasValue(cells, "部门")) h.setDept(cells.get("部门"));
                        h.setWhId(wh.getStockId());
                        h.setWhName(wh.getStockName());
                        h.setWhIdBom(whBom.getStockId());
                        h.setWhNameBom(whBom.getStockName());
                        if (hasValue(cells, "金额")) h.setAmount(parseBigDecimal(cells, "金额", row, false));
                        if (hasValue(cells, "税额")) h.setTaxAmount(parseBigDecimal(cells, "税额", row, false));
                        if (hasValue(cells, "税率")) h.setTaxRate(parseBigDecimal(cells, "税率", row, false));
                        return h;
                    }
                    com.erp.produce.assemble.entity.ProduceDisassemble h = new com.erp.produce.assemble.entity.ProduceDisassemble();
                    if (hasValue(cells, noLabel)) h.setSerialNo(cells.get(noLabel));
                    if (hasValue(cells, "部门")) h.setDept(cells.get("部门"));
                    h.setWhId(wh.getStockId());
                    h.setWhName(wh.getStockName());
                    h.setWhIdBom(whBom.getStockId());
                    h.setWhNameBom(whBom.getStockName());
                    if (hasValue(cells, "金额")) h.setAmount(parseBigDecimal(cells, "金额", row, false));
                    if (hasValue(cells, "税额")) h.setTaxAmount(parseBigDecimal(cells, "税额", row, false));
                    if (hasValue(cells, "税率")) h.setTaxRate(parseBigDecimal(cells, "税率", row, false));
                    return h;
                },
                (cells, matCache, row) -> {
                    Material m = resolveMaterial(cells, matCache, row);
                    // 明细行仓库：与表头「仓库」同源（提交时逐行校验仓库ID，不能为空）
                    com.erp.basedata.entity.Warehouse itemWh = resolveWarehouseByName(cells, itemWhCache, row, "仓库");
                    if (assemble) {
                        com.erp.produce.assemble.entity.ProduceAssembleDetail d = new com.erp.produce.assemble.entity.ProduceAssembleDetail();
                        d.setProductId(m.getMaterialId());
                        d.setProductCode(m.getMaterialCode());
                        d.setProductName(m.getMaterialName());
                        d.setSpecification(m.getModel());
                        d.setUnit(m.getBaseUnitName());
                        d.setUnitId(m.getBaseUnitId());
                        d.setBasicUnit(m.getBaseUnitName());
                        d.setBasicUnitId(m.getBaseUnitId());
                        d.setQuantity(parseBigDecimal(cells, "数量", row, true));
                        d.setBasicQuantity(d.getQuantity());
                        d.setWhId(itemWh.getStockId());
                        d.setWhName(itemWh.getStockName());
                        if (hasValue(cells, "单价")) d.setPrice(parseBigDecimal(cells, "单价", row, false));
                        if (hasValue(cells, "金额")) d.setAmount(parseBigDecimal(cells, "金额", row, false));
                        if (hasValue(cells, "税率(%)")) d.setTaxRate(parseBigDecimal(cells, "税率(%)", row, false));
                        if (hasValue(cells, "税额")) d.setTaxAmount(parseBigDecimal(cells, "税额", row, false));
                        if (hasValue(cells, "来源单号")) d.setSourceSerialNo(cells.get("来源单号"));
                        if (hasValue(cells, "明细备注")) d.setNotes(cells.get("明细备注"));
                        return d;
                    }
                    com.erp.produce.assemble.entity.ProduceDisassembleDetail d = new com.erp.produce.assemble.entity.ProduceDisassembleDetail();
                    d.setProductId(m.getMaterialId());
                    d.setProductCode(m.getMaterialCode());
                    d.setProductName(m.getMaterialName());
                    d.setSpecification(m.getModel());
                    d.setUnit(m.getBaseUnitName());
                    d.setUnitId(m.getBaseUnitId());
                    d.setBasicUnit(m.getBaseUnitName());
                    d.setBasicUnitId(m.getBaseUnitId());
                    d.setQuantity(parseBigDecimal(cells, "数量", row, true));
                    d.setBasicQuantity(d.getQuantity());
                    d.setWhId(itemWh.getStockId());
                    d.setWhName(itemWh.getStockName());
                    if (hasValue(cells, "单价")) d.setPrice(parseBigDecimal(cells, "单价", row, false));
                    if (hasValue(cells, "金额")) d.setAmount(parseBigDecimal(cells, "金额", row, false));
                    if (hasValue(cells, "税率(%)")) d.setTaxRate(parseBigDecimal(cells, "税率(%)", row, false));
                    if (hasValue(cells, "税额")) d.setTaxAmount(parseBigDecimal(cells, "税额", row, false));
                    if (hasValue(cells, "来源单号")) d.setSourceSerialNo(cells.get("来源单号"));
                    if (hasValue(cells, "明细备注")) d.setNotes(cells.get("明细备注"));
                    return d;
                },
                (header, items) -> {
                    if (assemble) {
                        com.erp.produce.assemble.entity.ProduceAssemble h = (com.erp.produce.assemble.entity.ProduceAssemble) header;
                        h.setProduceAssembleDetail((List<com.erp.produce.assemble.entity.ProduceAssembleDetail>) items);
                        // 原料行按成品默认 BOM 自动展开（提交要求原料行非空且逐行有仓库）
                        h.setProduceAssembleBom(expandBomForAssemble(h.getProduceAssembleDetail(), h.getWhIdBom(), h.getWhNameBom()));
                        produceAssembleService.saveWithDetails(h);
                    } else {
                        com.erp.produce.assemble.entity.ProduceDisassemble h = (com.erp.produce.assemble.entity.ProduceDisassemble) header;
                        h.setProduceDisassembleDetail((List<com.erp.produce.assemble.entity.ProduceDisassembleDetail>) items);
                        // 拆卸原料行按成品默认 BOM 自动展开（提交要求原料行非空且逐行有仓库）
                        h.setProduceDisassembleBom(expandBomForDisassemble(h.getProduceDisassembleDetail(), h.getWhIdBom(), h.getWhNameBom()));
                        produceDisassembleService.saveWithDetails(h);
                    }
                });
    }

    /**
     * 组装导入的原料行：按成品物料的默认启用 BOM 展开。
     * 数量 = BOM 子件用量 × 成品数量 ÷ BOM 基数（BOM 基数为空或 0 时按 1 处理）。
     */
    private List<com.erp.produce.assemble.entity.ProduceAssembleBom> expandBomForAssemble(
            List<com.erp.produce.assemble.entity.ProduceAssembleDetail> details, Long whIdBom, String whNameBom) {
        List<com.erp.produce.assemble.entity.ProduceAssembleBom> out = new ArrayList<>();
        if (details == null) return out;
        for (com.erp.produce.assemble.entity.ProduceAssembleDetail d : details) {
            com.erp.masterdata.entity.Bom bom = findActiveBom(d.getProductId());
            if (bom == null) continue;
            BigDecimal base = bom.getQuantity() == null || bom.getQuantity().signum() == 0 ? BigDecimal.ONE : bom.getQuantity();
            BigDecimal factor = d.getQuantity() == null ? BigDecimal.ZERO : d.getQuantity();
            for (com.erp.masterdata.entity.BomItem bi : listBomItems(bom.getBomId())) {
                if (bi.getMaterialId() == null) continue;
                com.erp.produce.assemble.entity.ProduceAssembleBom b = new com.erp.produce.assemble.entity.ProduceAssembleBom();
                Material child = materialMapper.selectById(bi.getMaterialId());
                b.setProductId(bi.getMaterialId());
                b.setProductCode(bi.getMaterialCode() != null ? bi.getMaterialCode() : (child == null ? null : child.getMaterialCode()));
                b.setProductName(bi.getMaterialName() != null ? bi.getMaterialName() : (child == null ? null : child.getMaterialName()));
                b.setSpecification(bi.getSpecification() != null ? bi.getSpecification() : (child == null ? null : child.getModel()));
                b.setUnit(child == null ? null : child.getBaseUnitName());
                b.setUnitId(bi.getUnitId() != null ? bi.getUnitId() : (child == null ? null : child.getBaseUnitId()));
                b.setBasicUnit(child == null ? null : child.getBaseUnitName());
                b.setBasicUnitId(bi.getUnitId() != null ? bi.getUnitId() : (child == null ? null : child.getBaseUnitId()));
                b.setQuantity(bomLineQty(bi, factor, base));
                b.setBasicQuantity(b.getQuantity());
                b.setWhId(whIdBom);
                b.setWhName(whNameBom);
                b.setPrice(BigDecimal.ZERO);
                b.setTaxRate(BigDecimal.ZERO);
                b.setNotes("按BOM自动展开");
                out.add(b);
            }
        }
        return out;
    }

    /** 拆卸导入的原料行：与组装同一口径（按成品默认 BOM 展开），仅实体类型不同。 */
    private List<com.erp.produce.assemble.entity.ProduceDisassembleBom> expandBomForDisassemble(
            List<com.erp.produce.assemble.entity.ProduceDisassembleDetail> details, Long whIdBom, String whNameBom) {
        List<com.erp.produce.assemble.entity.ProduceDisassembleBom> out = new ArrayList<>();
        if (details == null) return out;
        for (com.erp.produce.assemble.entity.ProduceDisassembleDetail d : details) {
            com.erp.masterdata.entity.Bom bom = findActiveBom(d.getProductId());
            if (bom == null) continue;
            BigDecimal base = bom.getQuantity() == null || bom.getQuantity().signum() == 0 ? BigDecimal.ONE : bom.getQuantity();
            BigDecimal factor = d.getQuantity() == null ? BigDecimal.ZERO : d.getQuantity();
            for (com.erp.masterdata.entity.BomItem bi : listBomItems(bom.getBomId())) {
                if (bi.getMaterialId() == null) continue;
                com.erp.produce.assemble.entity.ProduceDisassembleBom b = new com.erp.produce.assemble.entity.ProduceDisassembleBom();
                Material child = materialMapper.selectById(bi.getMaterialId());
                b.setProductId(bi.getMaterialId());
                b.setProductCode(bi.getMaterialCode() != null ? bi.getMaterialCode() : (child == null ? null : child.getMaterialCode()));
                b.setProductName(bi.getMaterialName() != null ? bi.getMaterialName() : (child == null ? null : child.getMaterialName()));
                b.setSpecification(bi.getSpecification() != null ? bi.getSpecification() : (child == null ? null : child.getModel()));
                b.setUnit(child == null ? null : child.getBaseUnitName());
                b.setUnitId(bi.getUnitId() != null ? bi.getUnitId() : (child == null ? null : child.getBaseUnitId()));
                b.setBasicUnit(child == null ? null : child.getBaseUnitName());
                b.setBasicUnitId(bi.getUnitId() != null ? bi.getUnitId() : (child == null ? null : child.getBaseUnitId()));
                b.setQuantity(bomLineQty(bi, factor, base));
                b.setBasicQuantity(b.getQuantity());
                b.setWhId(whIdBom);
                b.setWhName(whNameBom);
                b.setPrice(BigDecimal.ZERO);
                b.setTaxRate(BigDecimal.ZERO);
                b.setNotes("按BOM自动展开");
                out.add(b);
            }
        }
        return out;
    }

    /** 成品物料的默认启用 BOM（没有则返回 null，导入时原料行留空由用户手工补） */
    private com.erp.masterdata.entity.Bom findActiveBom(Long materialId) {
        if (materialId == null) return null;
        List<com.erp.masterdata.entity.Bom> list = bomMapper.selectList(
                new QueryWrapper<com.erp.masterdata.entity.Bom>()
                        .eq("material_id", materialId)
                        .eq("is_enabled", 1)
                        .orderByDesc("is_default").orderByDesc("bom_id")
                        .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private List<com.erp.masterdata.entity.BomItem> listBomItems(Long bomId) {
        return bomItemMapper.selectList(new QueryWrapper<com.erp.masterdata.entity.BomItem>()
                .eq("bom_id", bomId).orderByAsc("line_no"));
    }

    /** BOM 子件行数量 = 子件用量 × 成品数量 ÷ BOM 基数 */
    private BigDecimal bomLineQty(com.erp.masterdata.entity.BomItem bi, BigDecimal factor, BigDecimal base) {
        BigDecimal per = bi.getQuantity() == null ? BigDecimal.ZERO : bi.getQuantity();
        return per.multiply(factor).divide(base, 4, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public ImportResultDTO importMaterialRequisitions(MultipartFile file) {
        return importMaterialRequisitions(file, "PICK");
    }

    /**
     * 生产领料/退料/补料共用一张表：billType 由前端导入弹窗传入
     * PICK=领料、RETURN=退料、SUPPLEMENT=补料；不传时保持历史行为（领料）。
     */
    private ImportResultDTO importMaterialRequisitions(MultipartFile file, String billType) {
        String type = (billType == null || billType.isBlank()) ? "PICK" : billType.trim().toUpperCase();
        // 退料/补料模板的单号、日期列是各自口径；旧模板（领料单号/领料日期）仍然兼容读取
        String noLbl = "RETURN".equals(type) ? "退料单号" : ("SUPPLEMENT".equals(type) ? "补料单号" : "领料单号");
        String dateLbl = "RETURN".equals(type) ? "退料日期" : ("SUPPLEMENT".equals(type) ? "补料日期" : "领料日期");
        return importInventoryBill(file, noLbl, "material_requisition", "material_requisition_item",
                (cells, whCache, row) -> {
                    com.erp.inventory.entity.MaterialRequisition h = new com.erp.inventory.entity.MaterialRequisition();
                    com.erp.basedata.entity.Warehouse w = resolveWarehouse(cells, whCache, row);
                    h.setWarehouseId(w.getStockId());
                    h.setBillType(type);
                    String noVal = hasValue(cells, noLbl) ? cells.get(noLbl) : cells.get("领料单号");
                    if (noVal != null && !noVal.isBlank()) h.setRequisitionNo(noVal);
                    String dateVal = hasValue(cells, dateLbl) ? cells.get(dateLbl) : cells.get("领料日期");
                    if (dateVal != null && !dateVal.isBlank()) h.setRequisitionDate(parseDate(dateVal, row, dateLbl));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                (cells, matCache, row) -> buildInventoryItem(cells, matCache, row, com.erp.inventory.entity.MaterialRequisitionItem.class),
                (header, items) -> materialRequisitionService.saveRequisitionWithItems((com.erp.inventory.entity.MaterialRequisition) header, (List<com.erp.inventory.entity.MaterialRequisitionItem>) items));
    }

    // ==================== 财务单据导入 ====================

    /** 财务单表单据通用导入（每行一条单据） */
    private ImportResultDTO importFinanceSingleBill(MultipartFile file, String headerEntityType,
                                                     java.util.function.Function<Map<String, String>, Object> builder,
                                                     java.util.function.Function<Object, Object> saver) {
        ImportResultDTO result = new ImportResultDTO();
        // 字段权限（角色必填）一次性解析：循环内复用，避免逐行查库（单表单单据无明细，明细侧传 null）
        com.erp.permission.service.FieldRequiredPermissionService.PageRequired permHdrRequired = resolvePermRequired(headerEntityType);
        try (InputStream inputStream = file.getInputStream()) {
            List<Object> rawData = EasyExcel.read(inputStream).headRowNumber(0).sheet().doReadSync();
            List<List<Object>> excelData = convertExcelRowsToList(rawData);
            if (excelData.size() < 2) { result.setTotalCount(0); return result; }

            List<String> headers = convertToStringList(excelData.get(0));
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) idx.put(stripRequiredMark(headers.get(i).trim()), i);

            List<com.erp.customfield.entity.CustomField> hdrFields = getEnabledCustomFields(headerEntityType);
            int success = 0, fail = 0;
            for (int r = 1; r < excelData.size(); r++) {
                List<Object> row = excelData.get(r);
                if (isRowEmpty(row)) continue;
                int rowIndex = r + 1;
                Map<String, String> cells = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> e : idx.entrySet()) {
                    int i = e.getValue();
                    cells.put(e.getKey(), (i < row.size() && row.get(i) != null) ? row.get(i).toString().trim() : "");
                }
                try {
                    // 必填校验（字段权限角色必填 + 自定义字段必填）：必须在保存前拦截
                    if (!checkBillRequiredFields(cells, null, hdrFields, null, idx, rowIndex, result,
                            permHdrRequired, resolvePermRequired(null), headerEntityType, null)) {
                        fail++;
                        continue;
                    }
                    Object entity = builder.apply(cells);
                    // 财务 5 张单表头单据：保存动作返回保存后的实体（与入参同一实例，主键由 insert 回填）
                    ImportBillStatus want = parseImportStatus(cells);
                    Long headerId = saveImportedBill(entity, List.of(), (h, it) -> saver.apply(h),
                            headerEntityType, want, result);
                    saveSaleCustomFields(headerEntityType, headerId, cells, hdrFields);
                    success++;
                } catch (Exception e) {
                    fail++;
                    result.addError(rowIndex, "", e.getMessage() != null ? e.getMessage() : "保存失败");
                }
            }
            result.setTotalCount(success + fail);
            result.setSuccessCount(success);
            result.setFailCount(fail);
        } catch (IOException e) {
            throw new RuntimeException("财务单据导入失败", e);
        }
        return result;
    }

    @Override
    public ImportResultDTO importPaymentVouchers(MultipartFile file) {
        return importFinanceSingleBill(file, "payment_voucher",
                cells -> {
                    com.erp.finance.entity.PaymentVoucher h = new com.erp.finance.entity.PaymentVoucher();
                    if (hasValue(cells, "付款单号")) h.setPaymentNo(cells.get("付款单号"));
                    if (hasValue(cells, "付款日期")) h.setPaymentDate(parseDate(cells.get("付款日期"), 0, "付款日期"));
                    if (hasValue(cells, "供应商名称")) h.setSupplierName(cells.get("供应商名称"));
                    if (hasValue(cells, "银行账号")) h.setBankAccount(cells.get("银行账号"));
                    if (hasValue(cells, "汇率")) h.setExchangeRate(parseBigDecimal(cells, "汇率", 0, false));
                    if (hasValue(cells, "总金额")) h.setTotalAmount(parseBigDecimal(cells, "总金额", 0, false));
                    if (hasValue(cells, "付款金额")) h.setAmount(parseBigDecimal(cells, "付款金额", 0, true));
                    if (hasValue(cells, "折扣金额")) h.setDiscountAmount(parseBigDecimal(cells, "折扣金额", 0, false));
                    if (hasValue(cells, "已付金额")) h.setPaidAmount(parseBigDecimal(cells, "已付金额", 0, false));
                    if (hasValue(cells, "凭证号")) h.setDocumentNo(cells.get("凭证号"));
                    if (hasValue(cells, "源单类型")) h.setSourceBillType(cells.get("源单类型"));
                    if (hasValue(cells, "源单编号")) h.setSourceBillNo(cells.get("源单编号"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                e -> financeService.createVoucher((com.erp.finance.entity.PaymentVoucher) e));
    }

    @Override
    public ImportResultDTO importPaymentReceipts(MultipartFile file) {
        return importFinanceSingleBill(file, "payment_receipt",
                cells -> {
                    com.erp.finance.entity.PaymentReceipt h = new com.erp.finance.entity.PaymentReceipt();
                    if (hasValue(cells, "收款单号")) h.setReceiptNo(cells.get("收款单号"));
                    if (hasValue(cells, "收款日期")) h.setReceiptDate(parseDate(cells.get("收款日期"), 0, "收款日期"));
                    if (hasValue(cells, "客户名称")) h.setCustomerName(cells.get("客户名称"));
                    if (hasValue(cells, "银行账户")) h.setBankAccount(cells.get("银行账户"));
                    if (hasValue(cells, "汇率")) h.setExchangeRate(parseBigDecimal(cells, "汇率", 0, false));
                    if (hasValue(cells, "总金额")) h.setTotalAmount(parseBigDecimal(cells, "总金额", 0, false));
                    if (hasValue(cells, "收款金额")) h.setAmount(parseBigDecimal(cells, "收款金额", 0, true));
                    if (hasValue(cells, "折扣金额")) h.setDiscountAmount(parseBigDecimal(cells, "折扣金额", 0, false));
                    if (hasValue(cells, "实收金额")) h.setReceivedAmount(parseBigDecimal(cells, "实收金额", 0, false));
                    if (hasValue(cells, "票据号")) h.setDocumentNo(cells.get("票据号"));
                    if (hasValue(cells, "来源单据类型")) h.setSourceBillType(cells.get("来源单据类型"));
                    if (hasValue(cells, "来源单据号")) h.setSourceBillNo(cells.get("来源单据号"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                e -> financeService.createReceipt((com.erp.finance.entity.PaymentReceipt) e));
    }

    @Override
    public ImportResultDTO importPurchasePrePayments(MultipartFile file) {
        return importFinanceSingleBill(file, "purchase_pre_payment",
                cells -> {
                    com.erp.finance.prepayment.purchase.entity.PurchasePrePayment h = new com.erp.finance.prepayment.purchase.entity.PurchasePrePayment();
                    if (hasValue(cells, "预付款单号")) h.setPaymentNo(cells.get("预付款单号"));
                    if (hasValue(cells, "供应商名称")) h.setSupplierName(cells.get("供应商名称"));
                    if (hasValue(cells, "订单编号")) h.setOrderNo(cells.get("订单编号"));
                    if (hasValue(cells, "付款日期")) h.setPaymentDate(parseDate(cells.get("付款日期"), 0, "付款日期"));
                    if (hasValue(cells, "预付金额")) h.setAmount(parseBigDecimal(cells, "预付金额", 0, true));
                    if (hasValue(cells, "本位币金额")) h.setBaseAmount(parseBigDecimal(cells, "本位币金额", 0, false));
                    if (hasValue(cells, "币种编码")) h.setCurrencyCode(cells.get("币种编码"));
                    if (hasValue(cells, "汇率")) h.setExchangeRate(parseBigDecimal(cells, "汇率", 0, false));
                    if (hasValue(cells, "付款方式")) h.setPaymentMethod(cells.get("付款方式"));
                    if (hasValue(cells, "账户名称")) h.setAccountName(cells.get("账户名称"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                e -> purchasePrePaymentService.savePrePayment((com.erp.finance.prepayment.purchase.entity.PurchasePrePayment) e));
    }

    @Override
    public ImportResultDTO importSalePreReceipts(MultipartFile file) {
        return importFinanceSingleBill(file, "sale_pre_receipt",
                cells -> {
                    com.erp.finance.prepayment.sale.entity.SalePreReceipt h = new com.erp.finance.prepayment.sale.entity.SalePreReceipt();
                    if (hasValue(cells, "预收款单号")) h.setReceiptNo(cells.get("预收款单号"));
                    if (hasValue(cells, "客户名称")) h.setCustomerName(cells.get("客户名称"));
                    if (hasValue(cells, "订单编号")) h.setOrderNo(cells.get("订单编号"));
                    if (hasValue(cells, "收款日期")) h.setReceiptDate(parseDate(cells.get("收款日期"), 0, "收款日期"));
                    if (hasValue(cells, "预收金额")) h.setAmount(parseBigDecimal(cells, "预收金额", 0, true));
                    if (hasValue(cells, "本位币金额")) h.setBaseAmount(parseBigDecimal(cells, "本位币金额", 0, false));
                    if (hasValue(cells, "币种编码")) h.setCurrencyCode(cells.get("币种编码"));
                    if (hasValue(cells, "汇率")) h.setExchangeRate(parseBigDecimal(cells, "汇率", 0, false));
                    if (hasValue(cells, "收款方式")) h.setPaymentMethod(cells.get("收款方式"));
                    if (hasValue(cells, "账户名称")) h.setAccountName(cells.get("账户名称"));
                    if (hasValue(cells, "备注")) h.setRemark(cells.get("备注"));
                    return h;
                },
                e -> salePreReceiptService.savePreReceipt((com.erp.finance.prepayment.sale.entity.SalePreReceipt) e));
    }

    @Override
    public ImportResultDTO importOperationExpenses(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        try (InputStream inputStream = file.getInputStream()) {
            List<Object> rawData = EasyExcel.read(inputStream).headRowNumber(0).sheet().doReadSync();
            List<List<Object>> excelData = convertExcelRowsToList(rawData);
            if (excelData.size() < 2) { result.setTotalCount(0); return result; }

            List<String> headers = convertToStringList(excelData.get(0));
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) idx.put(stripRequiredMark(headers.get(i).trim()), i);

            List<com.erp.customfield.entity.CustomField> hdrFields = getEnabledCustomFields("operation_expense");
            List<com.erp.customfield.entity.CustomField> itemFields = getEnabledCustomFields("operation_expense_item");
            // 字段权限（角色必填）一次性解析：循环内复用，避免逐单查库
            com.erp.permission.service.FieldRequiredPermissionService.PageRequired permHdrRequired = resolvePermRequired("operation_expense");
            com.erp.permission.service.FieldRequiredPermissionService.PageRequired permItemRequired = resolvePermRequired("operation_expense_item");

            List<Map<String, String>> headerRows = new ArrayList<>();
            List<List<Map<String, String>>> itemRowsPerBill = new ArrayList<>();
            List<Map<String, String>> currentItems = null;

            for (int r = 1; r < excelData.size(); r++) {
                List<Object> row = excelData.get(r);
                if (isRowEmpty(row)) continue;
                Map<String, String> cells = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> e : idx.entrySet()) {
                    int i = e.getValue();
                    cells.put(e.getKey(), (i < row.size() && row.get(i) != null) ? row.get(i).toString().trim() : "");
                }
                boolean newBill = currentItems == null || hasValue(cells, "费用单号") || hasValue(cells, "费用类型");
                if (newBill) {
                    headerRows.add(cells);
                    currentItems = new ArrayList<>();
                    itemRowsPerBill.add(currentItems);
                }
                currentItems.add(cells);
            }

            result.setTotalCount(headerRows.size());
            for (int o = 0; o < headerRows.size(); o++) {
                int startRow = rowNumberOfOrder(itemRowsPerBill, o);
                try {
                    Map<String, String> hc = headerRows.get(o);
                    com.erp.finance.entity.OperationExpense h = new com.erp.finance.entity.OperationExpense();
                    if (hasValue(hc, "费用单号")) h.setExpenseNo(hc.get("费用单号"));
                    if (hasValue(hc, "费用类型")) h.setExpenseType(hc.get("费用类型"));
                    if (hasValue(hc, "费用日期")) h.setExpenseDate(parseDate(hc.get("费用日期"), startRow, "费用日期"));
                    if (hasValue(hc, "金额")) h.setAmount(parseBigDecimal(hc, "金额", startRow, true));
                    if (hasValue(hc, "关联单据类型")) h.setRelatedOrderType(hc.get("关联单据类型"));
                    if (hasValue(hc, "关联单据编号")) h.setRelatedOrderNo(hc.get("关联单据编号"));
                    if (hasValue(hc, "申请人")) h.setApplicantName(hc.get("申请人"));
                    if (hasValue(hc, "部门")) h.setDepartmentName(hc.get("部门"));
                    if (hasValue(hc, "分摊方式")) h.setAllocationMethod(hc.get("分摊方式"));
                    if (hasValue(hc, "备注")) h.setRemark(hc.get("备注"));

                    List<com.erp.finance.entity.OperationExpenseAllocation> items = new ArrayList<>();
                    for (Map<String, String> ic : itemRowsPerBill.get(o)) {
                        com.erp.finance.entity.OperationExpenseAllocation item = new com.erp.finance.entity.OperationExpenseAllocation();
                        if (hasValue(ic, "分摊费用类型")) item.setExpenseType(ic.get("分摊费用类型"));
                        if (hasValue(ic, "分摊基数")) item.setAllocationBase(parseBigDecimal(ic, "分摊基数", startRow, false));
                        if (hasValue(ic, "分摊比例(%)")) item.setAllocationRatio(parseBigDecimal(ic, "分摊比例(%)", startRow, false));
                        if (hasValue(ic, "来源单类型")) item.setOrderType(ic.get("来源单类型"));
                        if (hasValue(ic, "来源单编号")) item.setOrderNo(ic.get("来源单编号"));
                        if (hasValue(ic, "分摊金额")) item.setAmount(parseBigDecimal(ic, "分摊金额", startRow, true));
                        if (hasValue(ic, "明细备注")) item.setRemark(ic.get("明细备注"));
                        items.add(item);
                    }
                    if (items.isEmpty()) { result.setFailCount(result.getFailCount() + 1); result.addError(startRow, "", "单据无有效分摊明细行"); continue; }
                    h.setAllocations(items);
                    if (!checkBillRequiredFields(hc, itemRowsPerBill.get(o),
                            hdrFields, itemFields, idx, startRow, result,
                            permHdrRequired, permItemRequired, "operation_expense", "operation_expense_item")) {
                        result.setFailCount(result.getFailCount() + 1);
                        continue;
                    }
                    // 「单据状态」列分流：草稿=只存；已生效=同一事务内提交（审批开→待审核，审批关→提交即生效）
                    ImportBillStatus want = parseImportStatus(hc);
                    Long headerId = saveImportedBill(h, (hh) -> operationExpenseService.saveEntity(
                            (com.erp.finance.entity.OperationExpense) hh), "operationExpense", want, result);
                    saveSaleCustomFields("operation_expense", headerId, hc, hdrFields);
                    for (int i = 0; i < items.size(); i++) {
                        saveSaleCustomFields("operation_expense_item", items.get(i).getAllocationId(), itemRowsPerBill.get(o).get(i), itemFields);
                    }
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception e) {
                    result.setFailCount(result.getFailCount() + 1);
                    result.addError(startRow, "", e.getMessage() != null ? e.getMessage() : "保存失败");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("费用支出单导入失败", e);
        }
        return result;
    }

    private BigDecimal parseBigDecimal(Map<String, String> cells, String key, int rowIndex, boolean required) {
        String v = cells.getOrDefault(key, "");
        if (v.isEmpty()) {
            if (required) throw new IllegalArgumentException("第" + rowIndex + "行: " + key + "不能为空");
            return null;
        }
        try { return new BigDecimal(v); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("第" + rowIndex + "行: " + key + "[" + v + "]格式错误"); }
    }

    private Long defaultCurrencyId() {
        return currencyMapper.selectList(null).stream().map(c -> c.getCurrencyId()).findFirst().orElse(1L);
    }

    @FunctionalInterface
    private interface TriFunction<T, U, V, R> { R apply(T t, U u, V v); }

    private boolean hasValue(Map<String, String> cells, String key) {
        String v = cells.get(key);
        return v != null && !v.isEmpty();
    }

    /** 计算第 o 个订单在 Excel 中的起始行号（用于错误提示定位） */
    private int rowNumberOfOrder(List<List<Map<String, String>>> itemRowsPerOrder, int o) {
        int row = 2; // 第1行是表头
        for (int i = 0; i < o; i++) {
            row += itemRowsPerOrder.get(i).size();
        }
        return row;
    }

    /** 组装订单表头实体：客户三选一解析 + 数值/日期字段转换 */
    private SaleOrder buildSaleOrder(Map<String, String> cells, Map<String, Customer> customerCache, int rowIndex) {
        SaleOrder order = new SaleOrder();
        // 客户解析：客户ID > 客户编码 > 客户名称
        Customer customer = null;
        if (hasValue(cells, "客户ID")) {
            try {
                customer = customerMapper.selectById(Long.parseLong(cells.get("客户ID")));
            } catch (NumberFormatException ignored) {
            }
            if (customer == null) throw new IllegalArgumentException("第" + rowIndex + "行: 客户ID[" + cells.get("客户ID") + "]不存在");
        } else if (hasValue(cells, "客户编码")) {
            customer = customerCache.computeIfAbsent("no:" + cells.get("客户编码"),
                    k -> customerMapper.selectOne(new QueryWrapper<Customer>().eq("customer_code", k.substring(3)).last("LIMIT 1")));
            if (customer == null) throw new IllegalArgumentException("第" + rowIndex + "行: 客户编码[" + cells.get("客户编码") + "]不存在");
        } else if (hasValue(cells, "客户名称")) {
            customer = customerCache.computeIfAbsent("name:" + cells.get("客户名称"),
                    k -> customerMapper.selectOne(new QueryWrapper<Customer>().eq("customer_name", k.substring(5)).last("LIMIT 1")));
            if (customer == null) throw new IllegalArgumentException("第" + rowIndex + "行: 客户名称[" + cells.get("客户名称") + "]不存在");
        } else {
            throw new IllegalArgumentException("第" + rowIndex + "行: 客户ID/客户编码/客户名称至少填一项");
        }
        order.setCustomerId(customer.getCustomerId());
        if (customer.getCustomerCode() != null) order.setCustomerNo(customer.getCustomerCode());
        if (customer.getShortName() != null) order.setCustomerShortName(customer.getShortName());

        if (hasValue(cells, "订单号")) order.setOrderNo(cells.get("订单号"));
        if (hasValue(cells, "订单日期")) order.setOrderDate(parseDate(cells.get("订单日期"), rowIndex, "订单日期"));
        if (hasValue(cells, "交货日期")) order.setDeliveryDate(parseDate(cells.get("交货日期"), rowIndex, "交货日期"));
        if (hasValue(cells, "联系人")) order.setContactPerson(cells.get("联系人"));
        if (hasValue(cells, "联系电话")) order.setContactPhone(cells.get("联系电话"));
        if (hasValue(cells, "客户简称")) order.setCustomerShortName(cells.get("客户简称"));
        if (hasValue(cells, "客户编码")) order.setCustomerNo(cells.get("客户编码"));
        if (hasValue(cells, "收货地址")) order.setReceiveAddress(cells.get("收货地址"));
        if (hasValue(cells, "付款方式")) order.setPaymentMethod(cells.get("付款方式"));
        if (hasValue(cells, "运输方式")) order.setTransportMethod(cells.get("运输方式"));
        if (hasValue(cells, "物流公司")) order.setLogisticsCompany(cells.get("物流公司"));
        if (hasValue(cells, "物流单号")) order.setLogisticsNo(cells.get("物流单号"));
        if (hasValue(cells, "项目号")) order.setProjectNo(cells.get("项目号"));
        if (hasValue(cells, "客户订单号")) order.setCustomerOrderNo(cells.get("客户订单号"));
        if (hasValue(cells, "业务类型")) order.setBusinessType(cells.get("业务类型"));
        if (hasValue(cells, "报价单号")) order.setQuoteNo(cells.get("报价单号"));
        if (hasValue(cells, "备注")) order.setRemark(cells.get("备注"));

        if (hasValue(cells, "销售员ID")) order.setSalerId(Long.parseLong(cells.get("销售员ID")));
        if (hasValue(cells, "部门ID")) order.setDepartmentId(Long.parseLong(cells.get("部门ID")));
        if (hasValue(cells, "币种ID")) {
            order.setCurrencyId(Long.parseLong(cells.get("币种ID")));
        } else {
            // currency_id 列 NOT NULL：留空时默认取第一个币种（导入模板已隐藏该列，用户友好）
            order.setCurrencyId(currencyMapper.selectList(null).stream()
                    .map(c -> c.getCurrencyId()).findFirst().orElse(1L));
        }
        if (hasValue(cells, "汇率")) order.setExchangeRate(new BigDecimal(cells.get("汇率")));
        if (hasValue(cells, "折扣率")) order.setDiscountRate(new BigDecimal(cells.get("折扣率")));
        if (hasValue(cells, "付款条件ID")) order.setPaymentTermId(Long.parseLong(cells.get("付款条件ID")));
        if (hasValue(cells, "价格等级ID")) order.setPriceLevelId(Long.parseLong(cells.get("价格等级ID")));
        if (hasValue(cells, "仓库ID")) order.setWarehouseId(Long.parseLong(cells.get("仓库ID")));

        return order;
    }

    /**
     * 日期解析（**宽松兼容**，2026-09-19 改造）。
     *
     * <p>背景：用户导入销售订单时填了 {@code 2026/9/19}，原实现只认 {@code yyyy-MM-dd} + Excel 数字序列号 →
     * 直接报"第2行: 订单日期[2026/9/19]格式错误，应为 yyyy-MM-dd"，用户得手工改成横杠。</p>
     *
     * <p>现在统一走 {@link com.erp.common.util.FlexibleDateUtils}：横杠 / 斜杠 / 点号 / 紧凑 / 中文 /
     * 带时间 / Excel 序列号 都能识别；非法日期（如 2026-13-45）仍报错，并在提示里列出支持的写法。
     * 本方法被**全部单据导入**（销售/采购/库存/财务…）共用，改这一处即全模块生效。</p>
     */
    private LocalDate parseDate(String value, int rowIndex, String fieldName) {
        try {
            return com.erp.common.util.FlexibleDateUtils.parseLocalDate(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("第" + rowIndex + "行: " + fieldName + "[" + value + "]日期格式无法识别，"
                    + "支持 " + com.erp.common.util.FlexibleDateUtils.supportedPatternsHint());
        }
    }

    @Override
    public ImportResultDTO importDataWithPool(String entityName, MultipartFile file, Integer poolType, Long ownerUserId) {
        // 非 customer 实体直接走通用导入
        if (!"customer".equals(entityName)) {
            return importData(entityName, file);
        }
        ImportResultDTO result = new ImportResultDTO();
        Class<?> entityClass = Customer.class;
        Map<String, String> fieldLabelMap = getFieldLabelMap(entityClass);
        List<Object> entities = new ArrayList<>();
        // 自定义字段：客户池导入同样支持（私海/公海入口）
        List<com.erp.customfield.entity.CustomField> customFields = getEnabledCustomFields(entityName);
        List<Map<String, Object>> customValuesList = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream()) {
            List<Object> rawData = EasyExcel.read(inputStream)
                    .headRowNumber(0)
                    .sheet()
                    .doReadSync();
            List<List<Object>> excelData = convertExcelRowsToList(rawData);

            if (excelData.isEmpty()) {
                result.setTotalCount(0);
                return result;
            }

            List<String> headers = convertToStringList(excelData.get(0));
            Map<String, Integer> headerIndexMap = buildHeaderIndexMap(headers, fieldLabelMap);
            Map<Integer, com.erp.customfield.entity.CustomField> customIndexMap = buildCustomFieldIndexMap(headers, customFields, headerIndexMap);
            // 字段权限（角色必填）一次性解析：循环内复用，避免逐行查库
            com.erp.permission.service.FieldRequiredPermissionService.PageRequired permRequired =
                    resolvePermRequired(camelToSnake(entityName));

            result.setTotalCount(excelData.size() - 1);

            // 客户池导入同样按「谁导入归谁」：归属人（私海时）/分配销售员 = 当前登录账号
            Long loginUserId = currentLoginUserId();
            Long loginEmployeeId = loginUserId == null ? null : currentLoginEmployeeId(loginUserId);

            for (int i = 1; i < excelData.size(); i++) {
                List<Object> row = excelData.get(i);
                boolean isEmpty = isRowEmpty(row);
                if (isEmpty) continue;

                try {
                    Object entity = entityClass.getDeclaredConstructor().newInstance();
                    int errorsBefore = result.getErrors().size();
                    populateEntity(entity, row, headerIndexMap, fieldLabelMap, entityClass, i + 1, result, isAutoCodeEnabled(entityName));
                    // 字段权限（角色必填）叠加：与静态必填同口径，缺失同样拦该行；该列不在上传模板中则跳过
                    if (!permRequired.staticFields().isEmpty()) {
                        Map<String, String> permLabelValues = new LinkedHashMap<>();
                        for (Map.Entry<String, Integer> he : headerIndexMap.entrySet()) {
                            String lb = fieldLabelMap.get(he.getKey());
                            if (lb == null) continue;
                            int ci = he.getValue();
                            permLabelValues.put(lb, (ci < row.size() && row.get(ci) != null) ? String.valueOf(row.get(ci)).trim() : "");
                        }
                        for (String missLabel : fieldRequiredPermissionService.findMissingStaticLabels(permLabelValues, permRequired)) {
                            result.addError(i + 1, missLabel, "不能为空");
                        }
                    }
                    // 自定义字段解析（必填口径同静态字段）
                    Map<String, Object> customVals = parseCustomFieldValues(customIndexMap, row, i + 1, result, permRequired.allLabels());
                    // 必填缺失/格式错误：该行不入库（原实现静态字段与自定义字段都不拦截，与主导入口径不一致）
                    if (result.getErrors().size() > errorsBefore) {
                        result.setFailCount(result.getFailCount() + 1);
                        continue;
                    }
                    // 校验通过后与 entities 同步收集（索引一一对应），保存阶段统一回填
                    // 分配销售员 = 当前导入账号的员工（池归属在下方按 私海/公海 覆盖 owner_user_id）
                    if (entity instanceof Customer) {
                        applyCustomerImportDefaults((Customer) entity, null, loginEmployeeId, result);
                    }
                    customValuesList.add(customVals);
                    entities.add(entity);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception e) {
                    result.setFailCount(result.getFailCount() + 1);
                    result.addError(i + 1, "", "数据解析错误: " + e.getMessage());
                }
            }

            // 客户池特殊处理：强制设置 poolType 与 ownerUserId
            if (!entities.isEmpty()) {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                for (Object entity : entities) {
                    if (entity instanceof Customer) {
                        Customer c = (Customer) entity;
                        if (poolType != null) {
                            c.setPoolType(poolType);
                        }
                        if (poolType != null && poolType == 0) {
                            // 私海：归属当前用户，无进入公海日期
                            c.setOwnerUserId(ownerUserId);
                            c.setPoolEnterDate(null);
                        } else if (poolType != null && poolType == 1) {
                            // 公海：无归属人，记录进入公海日期
                            c.setOwnerUserId(null);
                            if (c.getPoolEnterDate() == null) {
                                c.setPoolEnterDate(now);
                            }
                        }
                    }
                }
                saveEntitiesWithCustomFields(entityName, entities, customValuesList, result);
            }

        } catch (IOException e) {
            log.error("客户池导入失败", e);
            throw new RuntimeException("客户池导入失败", e);
        }

        return result;
    }

    @Override
    public List<?> listCustomersByPoolType(Integer poolType, Long ownerUserId) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        if (poolType != null) {
            wrapper.eq("pool_type", poolType);
        }
        if (ownerUserId != null) {
            wrapper.eq("owner_user_id", ownerUserId);
        }
        wrapper.orderByDesc("customer_id");
        return customerMapper.selectList(wrapper);
    }
    @Override
    public ImportResultDTO importLeadsWithImages(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        Class<?> entityClass = Lead.class;
        Map<String, String> fieldLabelMap = getFieldLabelMap(entityClass);

        // 行数据与内嵌图片均按 sheet 行号(0-based)收集，读取完毕后统一合并。
        // 注：当前 easyexcel 3.3.2 的 CellExtraTypeEnum 尚无 IMAGE 类型（3.3.3+ 才支持），
        // 因此这里直接用 POI 解析 drawing 中的图片，同时兼容 xls/xlsx
        Map<Integer, List<Object>> rowsByIndex = new TreeMap<>();
        Map<Integer, List<byte[]>> imagesByRowIndex = new HashMap<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            // 收集内嵌图片：按锚点所在行号关联
            collectSheetImages(sheet, imagesByRowIndex);

            // 收集行数据（DataFormatter 按 Excel 显示值取数，与 EasyExcel 读数行为一致）
            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                short lastCol = row.getLastCellNum();
                if (lastCol <= 0) {
                    rowsByIndex.put(row.getRowNum(), new ArrayList<>());
                    continue;
                }
                List<Object> values = new ArrayList<>(Collections.nCopies(lastCol, null));
                for (Cell cell : row) {
                    values.set(cell.getColumnIndex(), formatter.formatCellValue(cell));
                }
                rowsByIndex.put(row.getRowNum(), values);
            }
        } catch (IOException e) {
            log.error("导入线索数据失败", e);
            throw new RuntimeException("导入线索数据失败", e);
        }

        if (rowsByIndex.isEmpty()) {
            result.setTotalCount(0);
            return result;
        }

        List<List<Object>> excelData = new ArrayList<>(rowsByIndex.values());
        List<Integer> rowIndexes = new ArrayList<>(rowsByIndex.keySet());

        List<String> headers = convertToStringList(excelData.get(0));
        Map<String, Integer> headerIndexMap = buildHeaderIndexMap(headers, fieldLabelMap);

        result.setTotalCount(excelData.size() - 1);

        List<Object> entities = new ArrayList<>();
        for (int i = 1; i < excelData.size(); i++) {
            List<Object> row = excelData.get(i);
            boolean isEmpty = isRowEmpty(row);
            if (isEmpty) continue;

            int sheetRowIndex = rowIndexes.get(i);
            try {
                Lead lead = Lead.class.getDeclaredConstructor().newInstance();
                populateEntity(lead, row, headerIndexMap, fieldLabelMap, entityClass, sheetRowIndex + 1, result);

                // 合并该行关联的内嵌图片：落盘生成 /uploads/... URL，多个逗号拼接
                List<byte[]> images = imagesByRowIndex.get(sheetRowIndex);
                if (images != null && !images.isEmpty()) {
                    List<String> urls = new ArrayList<>();
                    for (byte[] image : images) {
                        urls.add(saveLeadImage(image));
                    }
                    lead.setImageUrl(String.join(",", urls));
                }

                entities.add(lead);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                result.setFailCount(result.getFailCount() + 1);
                result.addError(sheetRowIndex + 1, "", "数据解析错误: " + e.getMessage());
            }
        }

        if (!entities.isEmpty()) {
            saveEntities("lead", entities, result);
        }

        return result;
    }

    @Override
    public ImportResultDTO importMaterialsWithImages(MultipartFile file) {
        // 兼容旧入口：统一按"跳过已存在"策略执行（与物料档案页导入弹窗的默认策略一致）
        return confirmMaterialImport(file, MATERIAL_CONFLICT_SKIP);
    }

    // ==================== 物料导入：已存在检测 / 覆盖 / 跳过 ====================

    /** 已存在物料处理策略：跳过（默认；不覆盖已存在物料，仅导入新物料） */
    public static final String MATERIAL_CONFLICT_SKIP = "SKIP";
    /** 已存在物料处理策略：覆盖（用 Excel 数据更新已存在物料，物料编码保持不变） */
    public static final String MATERIAL_CONFLICT_OVERWRITE = "OVERWRITE";

    /** 导入行上下文：解析+校验通过的一行物料 */
    private static class MaterialRow {
        /** sheet 行号（0-based） */
        int sheetRowIndex;
        /** 报错用行号（1-based） */
        int rowIndex;
        /** populateEntity + resolveMaterialRefs 后的物料对象 */
        Material material;
        /** 自定义字段值（仅非空项） */
        Map<String, Object> customValues;
        /** Excel 原始行（用于判断"该字段是否真的填了值"） */
        List<Object> rawRow;
        /** 该行在 Excel 中是否带了图片（内嵌图片 或 图片URL文本） */
        boolean hasExcelImage;
    }

    /** 解析+校验结果集：预检查与确认导入两个阶段共用 */
    private static class MaterialImportBundle {
        boolean parseSuccess = true;
        String parseError;
        int totalCount;
        int failCount;
        List<MaterialRow> rows = new ArrayList<>();
        List<ImportResultDTO.ErrorDetail> errors = new ArrayList<>();
        Map<String, Integer> headerIndexMap = new HashMap<>();
    }

    /**
     * 物料导入预检查：解析文件 → 逐行校验（单位/分类编码）→ 与物料库比对存在性，返回冲突清单。
     * 不写库、不落盘图片。
     */
    @Override
    public MaterialImportCheckDTO checkMaterialImport(MultipartFile file) {
        MaterialImportCheckDTO out = new MaterialImportCheckDTO();
        MaterialImportBundle bundle = parseMaterialImport(file, false);
        if (!bundle.parseSuccess) {
            out.setParseSuccess(false);
            out.setParseError(bundle.parseError);
            return out;
        }
        out.setTotalCount(bundle.totalCount);
        out.setErrorCount(bundle.failCount);
        out.setErrors(bundle.errors);
        List<MaterialImportCheckDTO.ConflictItem> conflicts = detectMaterialConflicts(bundle.rows);
        out.setConflicts(conflicts);
        out.setConflictCount(conflicts.size());
        out.setHasConflict(!conflicts.isEmpty());
        out.setNewCount(bundle.rows.size() - conflicts.size());
        return out;
    }

    /**
     * 物料导入确认：按策略处理已存在物料。
     *
     * @param onExistingConflict SKIP=跳过已存在（默认） / OVERWRITE=覆盖已存在（编码保持原值）
     */
    @Override
    public ImportResultDTO confirmMaterialImport(MultipartFile file, String onExistingConflict) {
        final String policy = MATERIAL_CONFLICT_OVERWRITE.equalsIgnoreCase(
                onExistingConflict == null ? "" : onExistingConflict.trim())
                ? MATERIAL_CONFLICT_OVERWRITE : MATERIAL_CONFLICT_SKIP;

        ImportResultDTO result = new ImportResultDTO();
        MaterialImportBundle bundle = parseMaterialImport(file, true);
        if (!bundle.parseSuccess) {
            result.addError(0, "", bundle.parseError);
            result.setFailCount(1);
            return result;
        }
        result.setTotalCount(bundle.totalCount);
        result.setFailCount(bundle.failCount);
        result.getErrors().addAll(bundle.errors);

        List<MaterialImportCheckDTO.ConflictItem> conflicts = detectMaterialConflicts(bundle.rows);
        Map<Integer, MaterialImportCheckDTO.ConflictItem> conflictByRow = new HashMap<>();
        for (MaterialImportCheckDTO.ConflictItem c : conflicts) {
            conflictByRow.put(c.getRowIndex(), c);
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = bundle.failCount;

        for (MaterialRow r : bundle.rows) {
            MaterialImportCheckDTO.ConflictItem c = conflictByRow.get(r.rowIndex);
            try {
                if (c == null) {
                    // 新物料：正常入库
                    if (isAutoCodeEnabled("material")) {
                        fillAutoCode("material", r.material);
                    }
                    materialMapper.insert(r.material);
                    Long newId = r.material.getMaterialId();
                    if (newId != null && r.customValues != null && !r.customValues.isEmpty()) {
                        try {
                            customFieldService.saveFieldValues("material", newId, r.customValues);
                        } catch (Exception ex) {
                            log.warn("导入回填自定义字段失败, id={}: {}", newId, ex.getMessage());
                        }
                    }
                    created++;
                } else if (MATERIAL_CONFLICT_OVERWRITE.equals(policy) && c.getExistingMaterialId() != null) {
                    // 覆盖已存在物料（文件内重复行无目标ID，按跳过处理）
                    if (overwriteMaterial(c.getExistingMaterialId(), r, bundle.headerIndexMap)) {
                        updated++;
                    } else {
                        skipped++;
                    }
                } else {
                    // 跳过已存在物料
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                result.addError(r.rowIndex, "", "保存失败: " + e.getMessage());
                log.warn("物料导入保存失败 row={}: {}", r.rowIndex, e.getMessage());
            }
        }

        result.setSuccessCount(created);
        result.setUpdatedCount(updated);
        result.setSkipCount(skipped);
        result.setFailCount(failed);

        StringBuilder msg = new StringBuilder();
        msg.append("新增 ").append(created).append(" 条");
        if (updated > 0) msg.append("，覆盖 ").append(updated).append(" 条");
        if (skipped > 0) msg.append("，跳过已存在 ").append(skipped).append(" 条");
        if (failed > 0) msg.append("，失败 ").append(failed).append(" 条");
        if (MATERIAL_CONFLICT_SKIP.equals(policy) && skipped > 0) {
            msg.append("（当前策略：跳过已存在物料）");
        }
        result.setMessage(msg.toString());
        return result;
    }

    /**
     * 解析并校验物料导入文件（不写库）。
     *
     * @param persistImages true=把 Excel 内嵌图片落盘为 /uploads URL（确认导入阶段）；
     *                      false=只校验不落盘（预检查阶段，避免用户取消后留下孤儿图片）
     */
    private MaterialImportBundle parseMaterialImport(MultipartFile file, boolean persistImages) {
        MaterialImportBundle bundle = new MaterialImportBundle();
        Class<?> entityClass = Material.class;
        Map<String, String> fieldLabelMap = getFieldLabelMap(entityClass);
        ImportResultDTO errSink = new ImportResultDTO();
        // WPS「单元格内嵌图片」（DISPIMG）：按图片ID精确取图，避免被当成 URL 校验而误报
        Map<String, byte[]> wpsCellImages = parseWpsCellImages(file);

        Map<Integer, List<Object>> rowsByIndex = new TreeMap<>();
        Map<Integer, List<byte[]>> imagesByRowIndex = new HashMap<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            collectSheetImages(sheet, imagesByRowIndex);

            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                short lastCol = row.getLastCellNum();
                if (lastCol <= 0) {
                    rowsByIndex.put(row.getRowNum(), new ArrayList<>());
                    continue;
                }
                List<Object> values = new ArrayList<>(Collections.nCopies(lastCol, null));
                for (Cell cell : row) {
                    values.set(cell.getColumnIndex(), formatter.formatCellValue(cell));
                }
                rowsByIndex.put(row.getRowNum(), values);
            }
        } catch (Exception e) {
            log.error("解析物料导入文件失败", e);
            bundle.parseSuccess = false;
            bundle.parseError = "文件解析失败：" + e.getMessage();
            return bundle;
        }

        if (rowsByIndex.isEmpty()) {
            bundle.parseSuccess = false;
            bundle.parseError = "文件中没有可导入的数据，请检查模板";
            return bundle;
        }

        List<List<Object>> excelData = new ArrayList<>(rowsByIndex.values());
        List<Integer> rowIndexes = new ArrayList<>(rowsByIndex.keySet());

        List<String> headers = convertToStringList(excelData.get(0));
        Map<String, Integer> headerIndexMap = buildHeaderIndexMap(headers, fieldLabelMap);
        Map<Integer, com.erp.customfield.entity.CustomField> customIndexMap =
                buildCustomFieldIndexMap(headers, getEnabledCustomFields("material"), headerIndexMap);
        // 字段权限（角色必填）一次性解析：循环内复用，避免逐行查库
        com.erp.permission.service.FieldRequiredPermissionService.PageRequired permRequired =
                resolvePermRequired("material");
        bundle.headerIndexMap = headerIndexMap;

        Map<Integer, List<byte[]>> unmatchedImages = new HashMap<>(imagesByRowIndex);
        boolean autoCode = isAutoCodeEnabled("material");

        for (int i = 1; i < excelData.size(); i++) {
            List<Object> row = excelData.get(i);
            if (isRowEmpty(row)) continue;
            bundle.totalCount++;

            int sheetRowIndex = rowIndexes.get(i);
            int rowIndex = sheetRowIndex + 1;
            try {
                Material material = Material.class.getDeclaredConstructor().newInstance();
                int errorsBefore = errSink.getErrors().size();
                populateEntity(material, row, headerIndexMap, fieldLabelMap, entityClass, rowIndex, errSink, autoCode);

                // 单位（模板必填列）→单位ID；分类编码→分类ID；任一失败即拒绝该行（不静默入库）
                resolveMaterialRefs(material, rowIndex, errSink);
                if (errSink.getErrors().size() > errorsBefore) {
                    bundle.failCount++;
                    continue;
                }

                MaterialRow mr = new MaterialRow();
                mr.sheetRowIndex = sheetRowIndex;
                mr.rowIndex = rowIndex;
                mr.material = material;
                mr.rawRow = row;

                // WPS 单元格内嵌图片：按本行「物料图片」单元格里的图片ID取图并归到本行
                if (!wpsCellImages.isEmpty()) {
                    Integer wpsImgCol = headerIndexMap.get("imageUrls");
                    if (wpsImgCol != null && wpsImgCol >= 0 && wpsImgCol < row.size()) {
                        Object wpsCellVal = row.get(wpsImgCol);
                        String wpsId = extractDispImgId(wpsCellVal == null ? null : wpsCellVal.toString());
                        if (wpsId != null) {
                            byte[] wpsData = wpsCellImages.get(wpsId);
                            if (wpsData != null && wpsData.length > 0) {
                                imagesByRowIndex.computeIfAbsent(sheetRowIndex, k -> new ArrayList<>()).add(wpsData);
                            }
                        }
                    }
                }

                // 合并该行关联的内嵌图片：落盘生成 /uploads/... URL；预检查阶段只判断"有没有图"
                List<byte[]> images = imagesByRowIndex.get(sheetRowIndex);
                String imageUrlsText = material.getImageUrls() == null ? "" : material.getImageUrls().trim();
                if (images != null && !images.isEmpty()) {
                    mr.hasExcelImage = true;
                    if (persistImages) {
                        List<String> urls = new ArrayList<>();
                        for (byte[] image : images) {
                            urls.add(saveMaterialImage(image));
                        }
                        material.setImageUrls(String.join(",", urls));
                        material.setMainImage(urls.get(0));
                        log.debug("物料[{}]从Excel内嵌图片设置了{}张图片", material.getMaterialCode(), urls.size());
                    }
                    unmatchedImages.remove(sheetRowIndex);
                } else if (!imageUrlsText.isEmpty()) {
                    if (extractDispImgId(imageUrlsText) != null) {
                        // WPS「单元格内嵌图片」公式（DISPIMG）：图片已在上方按图片ID尝试提取；
                        // 未提取到时按“无图片”放行，不按 URL 校验（否则会误报“无效的图片URL格式”并拦掉整行），
                        // 同时清掉公式文本，避免把 DISPIMG(...) 当图片地址写进库。
                        log.debug("物料[{}]图片列为WPS内嵌图片(DISPIMG)，未提取到图片则按无图片继续", material.getMaterialCode());
                        material.setImageUrls(null);
                        material.setMainImage(null);
                    } else if (isLocalFilePath(imageUrlsText)) {
                        errSink.addError(rowIndex, "物料图片", "不支持本地文件路径，请使用网络URL或在Excel中插入图片");
                        bundle.failCount++;
                        continue;
                    } else {
                        String firstUrl = imageUrlsText.split(",")[0].trim();
                        if (firstUrl.startsWith("http://") || firstUrl.startsWith("https://")
                                || firstUrl.startsWith("/uploads/") || firstUrl.startsWith("data:image/")) {
                            mr.hasExcelImage = true;
                            material.setMainImage(firstUrl);
                        } else {
                            errSink.addError(rowIndex, "物料图片", "无效的图片URL格式: " + firstUrl);
                            bundle.failCount++;
                            continue;
                        }
                    }
                }

                // 兜底：该行没有匹配到图片，但存在未匹配的图片，尝试分配
                if (!mr.hasExcelImage && !unmatchedImages.isEmpty()) {
                    for (Map.Entry<Integer, List<byte[]>> entry : unmatchedImages.entrySet()) {
                        List<byte[]> fallbackImages = entry.getValue();
                        if (fallbackImages != null && !fallbackImages.isEmpty()) {
                            mr.hasExcelImage = true;
                            if (persistImages) {
                                List<String> urls = new ArrayList<>();
                                for (byte[] image : fallbackImages) {
                                    urls.add(saveMaterialImage(image));
                                }
                                material.setImageUrls(String.join(",", urls));
                                material.setMainImage(urls.get(0));
                                log.debug("物料[{}]从未匹配图片兜底设置了{}张图片", material.getMaterialCode(), urls.size());
                            }
                            unmatchedImages.remove(entry.getKey());
                            break;
                        }
                    }
                }

                // 自定义字段值解析（仅收集非空项，空值不覆盖库中原值）
                // 注意：该路径按物料编码 upsert，空单元格语义是"不修改"而非"缺值"，
                // 故这里只记账错误（含字段权限角色必填）不拦截该行 —— 与其余导入路径的"拦整行"口径不同，属有意为之。
                mr.customValues = parseCustomFieldValues(customIndexMap, row, rowIndex, errSink, permRequired.allLabels());
                bundle.rows.add(mr);
            } catch (Exception e) {
                bundle.failCount++;
                errSink.addError(rowIndex, "", "数据解析错误: " + e.getMessage());
            }
        }

        bundle.errors = errSink.getErrors();
        return bundle;
    }

    /**
     * 设置中心「货品查重字段」：物料导入模板里，所选查重字段列头补 * 必填标记。
     * 选项清单已限定为"模板可见字段"（系统字段 + 非 formula 自定义字段），故只标记不补列。
     * 失败不影响模板生成（最多少一个星号提示）。
     *
     * @param autoCode 该实体是否走自动编码（物料：存在启用中的物料编码规则或 MD_MATERIAL 自动生成）；
     *                 为 true 时跳过"编码/号"结尾的查重字段，避免把自动生成列又标成必填
     *                 —— 历史 BUG：自动编号已让"物料编码"不带 *，随后本方法按默认查重字段
     *                 （物料编码+物料名称）又把 * 补了回去，用户看到模板仍要求填编码。
     */
    private void markDedupFieldsRequired(String entityName, List<String> labels, boolean autoCode) {
        if (!"material".equalsIgnoreCase(entityName) || labels == null || labels.isEmpty()) return;
        try {
            for (com.erp.masterdata.service.MaterialDedupService.DedupField d
                    : materialDedupService.activeFields()) {
                if (autoCode && (d.label.endsWith("编码") || d.label.endsWith("号"))) {
                    continue;
                }
                for (int i = 0; i < labels.size(); i++) {
                    if (stripRequiredMark(labels.get(i)).equals(d.label)) {
                        if (!labels.get(i).endsWith("*")) labels.set(i, labels.get(i) + "*");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[物料导入模板] 查重字段必填标记失败: {}", e.getMessage());
        }
    }

    /**
     * 物料导入冲突判定（设置中心「货品查重字段」驱动）。
     *
     * <p>规则：所选字段<b>各自</b>不允许重复（OR 语义；默认 物料编码+物料名称 = 改造前行为）。
     * 按配置顺序逐字段判定：先判"文件内重复"（同一字段值在本文件前面已出现），再判"库中已存在"；
     * 同一行只报第一条命中的字段。空单元格不参与该字段比对。</p>
     *
     * <p>配置清空 = 不查重（返回空冲突列表，全部按新物料导入）。</p>
     */
    private List<MaterialImportCheckDTO.ConflictItem> detectMaterialConflicts(List<MaterialRow> rows) {
        List<MaterialImportCheckDTO.ConflictItem> conflicts = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return conflicts;

        List<com.erp.masterdata.service.MaterialDedupService.DedupField> fields = materialDedupService.activeFields();
        if (fields.isEmpty()) return conflicts;

        Map<Integer, MaterialImportCheckDTO.ConflictItem> hitByRow = new HashMap<>();

        // ===== 1. 物料编码：单独唯一（文件内 + 库中）=====
        com.erp.masterdata.service.MaterialDedupService.DedupField codeField = fields.stream()
                .filter(f -> "materialCode".equals(f.fieldKey)).findFirst().orElse(null);
        if (codeField != null) {
            Map<String, Integer> codeFirstRow = new HashMap<>();
            Set<String> codes = new LinkedHashSet<>();
            for (MaterialRow r : rows) {
                String v = dedupFieldValue(r, codeField);
                if (v == null) continue;
                codeFirstRow.putIfAbsent(v, r.rowIndex);
                codes.add(v);
            }
            if (!codes.isEmpty()) {
                Map<String, List<Long>> existing = materialDedupService.findExistingIds(codeField, codes);
                Map<Long, Material> hitMaterials = loadMaterialsByIds(existing.values());
                for (MaterialRow r : rows) {
                    if (hitByRow.containsKey(r.rowIndex)) continue;
                    String v = dedupFieldValue(r, codeField);
                    if (v == null) continue;
                    boolean inFileDup = !Objects.equals(codeFirstRow.get(v), r.rowIndex);
                    Material hit = null;
                    if (!inFileDup) {
                        List<Long> ids = existing.get(v);
                        if (ids != null && !ids.isEmpty()) hit = hitMaterials.get(ids.get(0));
                    }
                    if (!inFileDup && hit == null) continue;
                    hitByRow.put(r.rowIndex, buildConflictItem(r, codeField.label, v, inFileDup, hit));
                }
            }
        }

        // ===== 2. 其余字段：组合唯一（AND，文件内 + 库中）=====
        List<com.erp.masterdata.service.MaterialDedupService.DedupField> comboFields = fields.stream()
                .filter(f -> !"materialCode".equals(f.fieldKey)).toList();
        if (!comboFields.isEmpty()) {
            Map<String, Long> comboIndex = materialDedupService.buildCombinationIndex();
            Map<Long, Material> comboMaterials = comboIndex.isEmpty() ? new HashMap<>()
                    : loadMaterialsByIds(List.of(new ArrayList<>(comboIndex.values())));
            Map<String, Integer> comboFirstRow = new HashMap<>();
            for (MaterialRow r : rows) {
                String key = materialDedupService.combinationKey(r.material, r.customValues, comboFields);
                if (key == null) continue;
                comboFirstRow.putIfAbsent(key, r.rowIndex);
            }
            List<String> labels = new ArrayList<>();
            for (com.erp.masterdata.service.MaterialDedupService.DedupField f : comboFields) labels.add(f.label);
            String comboLabel = String.join(" + ", labels);
            for (MaterialRow r : rows) {
                if (hitByRow.containsKey(r.rowIndex)) continue;
                String key = materialDedupService.combinationKey(r.material, r.customValues, comboFields);
                if (key == null) continue;
                boolean inFileDup = !Objects.equals(comboFirstRow.get(key), r.rowIndex);
                Material hit = null;
                if (!inFileDup) {
                    Long id = comboIndex.get(key);
                    if (id != null) hit = comboMaterials.get(id);
                }
                if (!inFileDup && hit == null) continue;
                hitByRow.put(r.rowIndex, buildConflictItem(r, comboLabel, comboValue(r, comboFields), inFileDup, hit));
            }
        }

        conflicts.addAll(hitByRow.values());
        conflicts.sort(Comparator.comparingInt(MaterialImportCheckDTO.ConflictItem::getRowIndex));
        return conflicts;
    }

    /** 构造一条冲突明细（编码单独 / 组合字段共用） */
    private MaterialImportCheckDTO.ConflictItem buildConflictItem(MaterialRow r, String label, String excelValue,
                                                                  boolean inFileDup, Material hit) {
        MaterialImportCheckDTO.ConflictItem item = new MaterialImportCheckDTO.ConflictItem();
        item.setRowIndex(r.rowIndex);
        item.setMatchType(inFileDup ? "文件内重复" : label);
        item.setMatchFieldLabel(label);
        item.setExcelValue(excelValue);
        item.setExcelCode(r.material.getMaterialCode());
        item.setExcelName(r.material.getMaterialName());
        item.setExcelSpec(r.material.getModel());
        if (hit != null) {
            item.setExistingMaterialId(hit.getMaterialId());
            item.setExistingCode(hit.getMaterialCode());
            item.setExistingName(hit.getMaterialName());
            item.setExistingSpec(hit.getModel());
            item.setExistingStatus(hit.getStatus());
        }
        return item;
    }

    /** 组合字段命中时的 Excel 值描述（label=value / label=value ...） */
    private String comboValue(MaterialRow r,
                              List<com.erp.masterdata.service.MaterialDedupService.DedupField> fields) {
        List<String> parts = new ArrayList<>();
        for (com.erp.masterdata.service.MaterialDedupService.DedupField d : fields) {
            String v = dedupFieldValue(r, d);
            if (v == null) continue;
            parts.add(d.label + "=" + v);
        }
        return String.join(" / ", parts);
    }

    /** 取导入行在某查重字段上的值（空/空白 → null，表示不参与该字段查重） */
    private String dedupFieldValue(MaterialRow r,
                                   com.erp.masterdata.service.MaterialDedupService.DedupField field) {
        if (field.custom) {
            if (r.customValues == null) return null;
            Object v = r.customValues.get(field.fieldKey);
            if (v == null) return null;
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : s;
        }
        return materialDedupService.fieldValue(r.material, field.fieldKey);
    }

    /** 批量取物料（冲突明细展示"库中现有"用） */
    private Map<Long, Material> loadMaterialsByIds(Collection<List<Long>> idLists) {
        List<Long> ids = new ArrayList<>();
        if (idLists != null) {
            for (List<Long> l : idLists) {
                if (l != null) ids.addAll(l);
            }
        }
        if (ids.isEmpty()) return Collections.emptyMap();
        List<Material> list = materialMapper.selectBatchIds(ids.stream().distinct().toList());
        Map<Long, Material> map = new HashMap<>();
        if (list != null) {
            for (Material m : list) map.put(m.getMaterialId(), m);
        }
        return map;
    }

    /**
     * 覆盖已存在物料：以库中原记录为基底，仅把 Excel 中"确实填了值"的字段写上去。
     * 铁律：①物料编码永不覆盖；②Excel 空单元格保留原值
     * （convertValue 会把空单元格转成类型默认值 0/""/ZERO，故必须按原始单元格判空，不能用实体字段判空）。
     */
    private boolean overwriteMaterial(Long materialId, MaterialRow r, Map<String, Integer> headerIndexMap) {
        Material base = materialMapper.selectById(materialId);
        if (base == null) {
            log.warn("覆盖物料失败：物料不存在 id={}", materialId);
            return false;
        }
        Material excel = r.material;

        // 1) 通用字段：Excel 原始单元格非空才覆盖
        for (Field field : Material.class.getDeclaredFields()) {
            ExportField ann = field.getAnnotation(ExportField.class);
            if (ann == null || ann.hidden()) continue;
            String name = field.getName();
            // 编码永不覆盖；图片/分类/单位走下方专项处理
            if ("materialCode".equals(name) || "imageUrls".equals(name)
                    || "categoryCode".equals(name) || "unitName".equals(name)) {
                continue;
            }
            Integer idx = headerIndexMap.get(name);
            if (idx == null || idx >= r.rawRow.size()) continue;
            if (isEmptyValue(r.rawRow.get(idx))) continue;
            try {
                field.setAccessible(true);
                Object v = field.get(excel);
                if (v != null) {
                    field.set(base, v);
                }
            } catch (Exception e) {
                log.warn("覆盖字段[{}]失败: {}", name, e.getMessage());
            }
        }

        // 2) 单位：Excel 填了才覆盖
        if (isCellNonBlank(r, headerIndexMap, "unitName")) {
            base.setBaseUnitId(excel.getBaseUnitId());
            base.setPurchaseUnitId(excel.getPurchaseUnitId());
            base.setSaleUnitId(excel.getSaleUnitId());
            base.setStockUnitId(excel.getStockUnitId());
            base.setProductionUnitId(excel.getProductionUnitId());
        }

        // 3) 物料分类：Excel 填了分类编码才覆盖
        if (isCellNonBlank(r, headerIndexMap, "categoryCode")) {
            base.setCategoryId(excel.getCategoryId());
        }

        // 4) 图片：Excel 带图才替换，否则保留库中原图
        if (r.hasExcelImage) {
            base.setImageUrls(excel.getImageUrls());
            base.setMainImage(excel.getMainImage());
        }

        base.setMaterialId(materialId);
        materialMapper.updateById(base);

        // 5) 自定义字段：仅非空项 upsert
        if (r.customValues != null && !r.customValues.isEmpty()) {
            try {
                customFieldService.saveFieldValues("material", materialId, r.customValues);
            } catch (Exception ex) {
                log.warn("覆盖回填自定义字段失败, id={}: {}", materialId, ex.getMessage());
            }
        }
        return true;
    }

    /** 该字段在 Excel 原始行中是否填了值 */
    private boolean isCellNonBlank(MaterialRow r, Map<String, Integer> headerIndexMap, String fieldName) {
        Integer idx = headerIndexMap.get(fieldName);
        return idx != null && idx < r.rawRow.size() && !isEmptyValue(r.rawRow.get(idx));
    }

    /** 收集 sheet 中的内嵌图片，按锚点行号(0-based)归组，兼容 xls/xlsx/WPS */
    private void collectSheetImages(Sheet sheet, Map<Integer, List<byte[]>> imagesByRowIndex) {
        if (sheet instanceof XSSFSheet xssfSheet) {
            boolean found = collectImagesFromDrawing(xssfSheet, imagesByRowIndex);
            if (!found) {
                log.debug("标准drawing方式未找到图片，尝试从XSSF包级别读取...");
                collectImagesFromXssfPackage(xssfSheet, imagesByRowIndex);
            }
        } else if (sheet instanceof org.apache.poi.hssf.usermodel.HSSFSheet hssfSheet) {
            HSSFPatriarch patriarch = hssfSheet.getDrawingPatriarch();
            if (patriarch == null) return;
            for (HSSFShape shape : patriarch.getChildren()) {
                if (shape instanceof HSSFPicture picture) {
                    byte[] data = picture.getPictureData().getData();
                    if (data != null && data.length > 0) {
                        imagesByRowIndex.computeIfAbsent(picture.getClientAnchor().getRow1(), k -> new ArrayList<>())
                                .add(data);
                    }
                }
            }
        }
    }

    private boolean collectImagesFromDrawing(XSSFSheet xssfSheet, Map<Integer, List<byte[]>> imagesByRowIndex) {
        XSSFDrawing drawing = xssfSheet.getDrawingPatriarch();
        if (drawing == null) {
            log.debug("Excel sheet无drawing");
            return false;
        }
        List<XSSFShape> allShapes = new ArrayList<>();
        collectAllShapes(drawing.getShapes(), allShapes);

        int imageCount = 0;
        for (XSSFShape shape : allShapes) {
            byte[] imgData = null;
            int anchorRow = -1;

            if (shape instanceof XSSFPicture picture) {
                imgData = picture.getPictureData().getData();
                anchorRow = getAnchorRow(picture.getAnchor());
            } else {
                imgData = tryExtractImageFromShape(shape);
                anchorRow = getShapeAnchorRow(shape);
            }

            if (imgData != null && imgData.length > 0 && anchorRow >= 0) {
                imagesByRowIndex.computeIfAbsent(anchorRow, k -> new ArrayList<>())
                        .add(imgData);
                imageCount++;
                log.debug("收集到Excel图片: row={}, size={}bytes, shapeType={}", anchorRow, imgData.length, shape.getClass().getSimpleName());
            }
        }
        log.debug("Drawing图片收集完成，共收集{}张图片, 遍历{}个shape", imageCount, allShapes.size());
        return imageCount > 0;
    }

    /**
     * WPS兼容：直接从XSSF workbook级别读取图片，遍历所有关联关系。
     * 当WPS保存的Excel文件drawing结构不标准时使用此fallback。
     */
    private void collectImagesFromXssfPackage(XSSFSheet xssfSheet, Map<Integer, List<byte[]>> imagesByRowIndex) {
        try {
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = xssfSheet.getWorkbook();
            if (workbook == null) return;

            List<org.apache.poi.xssf.usermodel.XSSFPictureData> allPictures = workbook.getAllPictures();
            if (allPictures == null || allPictures.isEmpty()) {
                log.debug("XSSF包级别未找到任何图片");
                return;
            }

            log.debug("XSSF包级别找到{}张图片，尝试通过drawing XML关联行号", allPictures.size());

            org.apache.poi.openxml4j.opc.PackagePart sheetPart = xssfSheet.getPackagePart();
            java.io.InputStream is = null;
            org.w3c.dom.Document doc = null;
            try {
                is = sheetPart.getInputStream();
                javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
                doc = builder.parse(is);

                org.w3c.dom.NodeList oneCellAnchors = doc.getElementsByTagName("xdr:oneCellAnchor");
                org.w3c.dom.NodeList twoCellAnchors = doc.getElementsByTagName("xdr:twoCellAnchor");

                int pictureIdx = 0;
                for (int i = 0; i < oneCellAnchors.getLength() && pictureIdx < allPictures.size(); i++) {
                    org.w3c.dom.Element anchor = (org.w3c.dom.Element) oneCellAnchors.item(i);
                    int row = parseAnchorRow(anchor);
                    if (row >= 0) {
                        byte[] data = allPictures.get(pictureIdx).getData();
                        imagesByRowIndex.computeIfAbsent(row, k -> new ArrayList<>()).add(data);
                        pictureIdx++;
                        log.debug("XML解析图片: oneCellAnchor row={}, size={}bytes", row, data.length);
                    }
                }

                for (int i = 0; i < twoCellAnchors.getLength() && pictureIdx < allPictures.size(); i++) {
                    org.w3c.dom.Element anchor = (org.w3c.dom.Element) twoCellAnchors.item(i);
                    int row = parseAnchorRow(anchor);
                    if (row >= 0) {
                        byte[] data = allPictures.get(pictureIdx).getData();
                        imagesByRowIndex.computeIfAbsent(row, k -> new ArrayList<>()).add(data);
                        pictureIdx++;
                        log.debug("XML解析图片: twoCellAnchor row={}, size={}bytes", row, data.length);
                    }
                }

                log.debug("XML图片解析完成，共收集{}张图片", pictureIdx);

                if (pictureIdx == 0 && !allPictures.isEmpty()) {
                    log.debug("未能通过XML关联图片到行，将所有图片归到第1行数据");
                    for (org.apache.poi.xssf.usermodel.XSSFPictureData pd : allPictures) {
                        imagesByRowIndex.computeIfAbsent(1, k -> new ArrayList<>()).add(pd.getData());
                    }
                }
            } catch (Exception e) {
                log.warn("XML解析图片失败: {}", e.getMessage());
                if (!allPictures.isEmpty()) {
                    log.debug("降级处理：将所有{}张图片归到第1行", allPictures.size());
                    for (org.apache.poi.xssf.usermodel.XSSFPictureData pd : allPictures) {
                        imagesByRowIndex.computeIfAbsent(1, k -> new ArrayList<>()).add(pd.getData());
                    }
                }
            } finally {
                if (is != null) try { is.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("XSSF包级别图片读取失败: {}", e.getMessage());
        }
    }

    /**
     * 解析 WPS「单元格内嵌图片」（DISPIMG 公式）：xl/cellimages.xml + 其 rels → 图片ID → 图片字节。
     *
     * <p>WPS 把“嵌入单元格的图片”存成 DISPIMG("ID_xxx",n) 公式，图片放在 xl/cellimages.xml 引用的 media 里，
     * 标准 drawing 中没有 anchor，POI 按行遍历读不到行号 → 旧逻辑会把这类单元格文本当 URL 校验并报错
     * “无效的图片URL格式”。本方法按图片ID精确取出字节，供导入按行归位。</p>
     *
     * <p>非 WPS 文件（无 cellimages.xml）返回空 Map，不影响原有导入。</p>
     */
    private Map<String, byte[]> parseWpsCellImages(MultipartFile file) {
        Map<String, byte[]> result = new HashMap<>();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(file.getInputStream())) {
            byte[] cellImagesXml = null;
            byte[] relsXml = null;
            Map<String, byte[]> media = new HashMap<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("xl/media/")) {
                    media.put(name, zis.readAllBytes());
                } else if ("xl/cellimages.xml".equals(name)) {
                    cellImagesXml = zis.readAllBytes();
                } else if ("xl/_rels/cellimages.xml.rels".equals(name)) {
                    relsXml = zis.readAllBytes();
                }
            }
            if (cellImagesXml == null || media.isEmpty()) return result;

            Map<String, String> ridToTarget = new HashMap<>();
            if (relsXml != null) {
                org.w3c.dom.Document relDoc = parseXmlSafely(relsXml);
                if (relDoc != null) {
                    org.w3c.dom.NodeList rels = relDoc.getElementsByTagName("Relationship");
                    for (int i = 0; i < rels.getLength(); i++) {
                        org.w3c.dom.Element e = (org.w3c.dom.Element) rels.item(i);
                        ridToTarget.put(e.getAttribute("Id"), e.getAttribute("Target"));
                    }
                }
            }

            org.w3c.dom.Document doc = parseXmlSafely(cellImagesXml);
            if (doc == null) return result;
            org.w3c.dom.NodeList pics = doc.getElementsByTagNameNS("*", "pic");
            for (int i = 0; i < pics.getLength(); i++) {
                org.w3c.dom.Element pic = (org.w3c.dom.Element) pics.item(i);
                String imageId = null;
                org.w3c.dom.NodeList nv = pic.getElementsByTagNameNS("*", "cNvPr");
                if (nv.getLength() > 0) imageId = ((org.w3c.dom.Element) nv.item(0)).getAttribute("name");
                String rid = null;
                org.w3c.dom.NodeList blips = pic.getElementsByTagNameNS("*", "blip");
                if (blips.getLength() > 0) {
                    org.w3c.dom.NamedNodeMap attrs = blips.item(0).getAttributes();
                    for (int k = 0; k < attrs.getLength(); k++) {
                        org.w3c.dom.Node a = attrs.item(k);
                        if ("embed".equals(a.getLocalName()) || "embed".equals(a.getNodeName())) {
                            rid = a.getNodeValue();
                            break;
                        }
                    }
                }
                if (imageId == null || rid == null) continue;
                String target = ridToTarget.get(rid);
                if (target == null) continue;
                if (target.startsWith("/")) target = target.substring(1);
                else if (!target.startsWith("xl/")) target = "xl/" + target;
                byte[] data = media.get(target);
                if (data != null && data.length > 0) result.put(imageId, data);
            }
            log.debug("WPS内嵌图片解析完成：图片ID {} 个 / media {} 个", result.size(), media.size());
        } catch (Exception e) {
            log.warn("解析WPS内嵌图片(cellimages)失败: {}", e.getMessage());
        }
        return result;
    }

    /** 安全解析 XML（禁 DOCTYPE 防 XXE、命名空间感知） */
    private org.w3c.dom.Document parseXmlSafely(byte[] bytes) {
        try {
            javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.debug("XML解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从单元格文本提取 WPS 单元格图片ID：_xlfn.DISPIMG("ID_xxx",1) → ID_xxx；非该格式返回 null */
    private String extractDispImgId(String cellText) {
        if (cellText == null || cellText.isBlank()) return null;
        if (!cellText.contains("DISPIMG")) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("DISPIMG\\s*\\(\\s*\"([^\"]+)\"").matcher(cellText);
        return m.find() ? m.group(1) : null;
    }

    private int parseAnchorRow(org.w3c.dom.Element anchor) {
        try {
            org.w3c.dom.NodeList fromNodes = anchor.getElementsByTagName("xdr:from");
            if (fromNodes.getLength() > 0) {
                org.w3c.dom.Element from = (org.w3c.dom.Element) fromNodes.item(0);
                org.w3c.dom.NodeList rowNodes = from.getElementsByTagName("xdr:row");
                if (rowNodes.getLength() > 0) {
                    return Integer.parseInt(rowNodes.item(0).getTextContent().trim());
                }
            }
        } catch (Exception e) {
            log.debug("解析anchor行号失败: {}", e.getMessage());
        }
        return -1;
    }

    private void collectAllShapes(List<XSSFShape> shapes, List<XSSFShape> result) {
        if (shapes == null) return;
        for (XSSFShape shape : shapes) {
            result.add(shape);
            if (shape instanceof org.apache.poi.xssf.usermodel.XSSFShapeGroup group) {
                for (XSSFShape child : group) {
                    result.add(child);
                    if (child instanceof org.apache.poi.xssf.usermodel.XSSFShapeGroup subGroup) {
                        collectAllShapes(java.util.Collections.singletonList(child), result);
                    }
                }
            }
        }
    }

    private byte[] tryExtractImageFromShape(XSSFShape shape) {
        try {
            if (shape instanceof org.apache.poi.xssf.usermodel.XSSFObjectData obj) {
                return obj.getObjectData();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private int getAnchorRow(org.apache.poi.xssf.usermodel.XSSFAnchor anchor) {
        try {
            java.lang.reflect.Method getStartRow = findMethod(anchor.getClass(), "getStartRow");
            if (getStartRow != null) {
                Object val = getStartRow.invoke(anchor);
                if (val instanceof Number) return ((Number) val).intValue();
            }
            java.lang.reflect.Method getRow1 = findMethod(anchor.getClass(), "getRow1");
            if (getRow1 != null) {
                Object val = getRow1.invoke(anchor);
                if (val instanceof Number) return ((Number) val).intValue();
            }
            java.lang.reflect.Method getFrom = findMethod(anchor.getClass(), "getFrom");
            if (getFrom != null) {
                Object from = getFrom.invoke(anchor);
                if (from != null) {
                    java.lang.reflect.Method getRow = findMethod(from.getClass(), "getRow");
                    if (getRow != null) {
                        Object val = getRow.invoke(from);
                        if (val instanceof Number) return ((Number) val).intValue();
                    }
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private int getShapeAnchorRow(XSSFShape shape) {
        try {
            org.apache.poi.xssf.usermodel.XSSFAnchor anchor = shape.getAnchor();
            return getAnchorRow(anchor);
        } catch (Exception ignored) {}
        return -1;
    }

    private java.lang.reflect.Method findMethod(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getMethod(name);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 保存线索内嵌图片到本地，返回访问URL。
     * 规则与 FileUploadController.saveFile 一致：{uploadPath}/lead/yyyy/MM/dd/{uuid}.{ext}
     */
    private String saveLeadImage(byte[] data) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + detectImageExt(data);
        File dir = new File(uploadPath + File.separator + "lead" + File.separator + datePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            Files.write(new File(dir, fileName).toPath(), data);
        } catch (IOException e) {
            log.error("保存线索图片失败", e);
            throw new RuntimeException("保存线索图片失败", e);
        }
        return "/uploads/lead/" + datePath + "/" + fileName;
    }

    /**
     * 保存物料内嵌图片到本地，返回访问URL。
     * 规则与 FileUploadController.saveFile 一致：{uploadPath}/material/yyyy/MM/dd/{uuid}.{ext}
     */
    private String saveMaterialImage(byte[] data) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + detectImageExt(data);
        File dir = new File(uploadPath + File.separator + "material" + File.separator + datePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            Files.write(new File(dir, fileName).toPath(), data);
        } catch (IOException e) {
            log.error("保存物料图片失败", e);
            throw new RuntimeException("保存物料图片失败", e);
        }
        return "/uploads/material/" + datePath + "/" + fileName;
    }

    /** 按魔数识别图片扩展名，识别不了默认 png */
    private String detectImageExt(byte[] data) {
        if (data == null || data.length < 4) return ".png";
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) return ".jpg";
        if ((data[0] & 0xFF) == 0x89 && (data[1] & 0xFF) == 0x50) return ".png";
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') return ".gif";
        if (data[0] == 'B' && data[1] == 'M') return ".bmp";
        return ".png";
    }

    /** 检测是否为本地文件路径（非网络URL） */
    private boolean isLocalFilePath(String path) {
        if (path == null || path.isBlank()) return false;
        String trimmed = path.trim();
        // Windows本地路径: C:\, D:\, etc.
        if (trimmed.matches("^[a-zA-Z]:[\\\\/].*")) return true;
        // Unix绝对路径: /usr/xxx
        if (trimmed.startsWith("/") && !trimmed.startsWith("/uploads/")) return true;
        // 相对路径包含反斜杠或点开头
        if (trimmed.contains("\\") || trimmed.startsWith("./") || trimmed.startsWith("../")) return true;
        // 文件名带扩展名且不以http开头
        if (trimmed.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$") && !trimmed.startsWith("http")) return true;
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<?> listAllData(String entityName) {
        BaseMapper<?> mapper = getMapper(entityName);
        if (mapper == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }
        IPage<?> page = new Page<>(1, 10000);
        // 导出必须与列表页同口径（口径见唯一登记表 BillExcelRegistry）：
        // ① 业务准入门槛（列表页 Controller#applyExtraFilter）；② 数据权限（列表页 applyDataScopeFilter）。
        // 历史 BUG1：这里原先是 selectPage(page, null) 全表取数 → 普通账号能把全公司数据导成 Excel。
        // 历史 BUG2（2026-09-17 121 报障）：上一轮只补了数据权限、漏了业务准入门槛 →
        //   客户档案列表 0 条（潜在/意向/公海客户被门槛挡掉），导出却出了 2 条客户。
        QueryWrapper<?> wrapper = buildExportWrapper(entityName);
        return ((BaseMapper) mapper).selectPage(page, wrapper).getRecords();
    }

    /**
     * 导出「勾选的 N 条」（列表页批量导出选中，2026-09-19 新增）。
     *
     * <p>与 {@link #listAllData(String)} 同一口径：仍套 {@code buildExportWrapper}
     * （业务准入门槛 + 数据权限），再按主键集合收窄范围 —— 保证"勾选的单据里，
     * 当前账号没有权限的那几条同样导不出来"，不会因为走了批量口子而放宽权限。</p>
     *
     * <p>主键列名必须从 MyBatis-Plus 元数据解析（sale_order 是 order_id、采购入库是 inbound_id…），
     * 不能写死 "id"，否则非 id 主键的表会直接 SQL 报错。</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    public void exportSelected(String entityName, List<Long> ids, HttpServletResponse response) {
        BaseMapper<?> mapper = getMapper(entityName);
        if (mapper == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }
        if (ids == null || ids.isEmpty()) {
            // 没有选中 → 导出空表头（**不回退全量**，避免误用把整表导出去）
            exportData(entityName, new java.util.ArrayList<>(), response);
            return;
        }
        QueryWrapper<?> wrapper = buildExportWrapper(entityName);
        // 注意：buildExportWrapper 在"既无业务门槛也无数据权限"时返回 null（老逻辑直接把它交给 selectPage，
        // 语义＝无条件查询）；这里要再追加 in 条件，必须先把 null 换成空 wrapper（2026-09-19 实测 NPE 根因）
        if (wrapper == null) {
            wrapper = new QueryWrapper<>();
        }
        wrapper.in(resolvePrimaryKeyColumn(entityName), ids);
        List<?> rows = ((BaseMapper) mapper).selectList(wrapper);
        exportData(entityName, rows, response);
    }

    /** 实体主键列名（读 @TableId 注解，异常兜底 "id"） */
    private String resolvePrimaryKeyColumn(String entityName) {
        try {
            com.erp.export.registry.BillExcelRegistry.Bill bill =
                    com.erp.export.registry.BillExcelRegistry.get(entityName);
            if (bill != null && bill.entityClass() != null) {
                // 直接用 @TableId 注解取列名：本工程 MyBatis-Plus 版本没有 TableInfo 元数据类，
                // 而实体一律标了 @TableId(value = "xxx_id")，够用且零依赖
                for (java.lang.reflect.Field f : bill.entityClass().getDeclaredFields()) {
                    com.baomidou.mybatisplus.annotation.TableId tid =
                            f.getAnnotation(com.baomidou.mybatisplus.annotation.TableId.class);
                    if (tid == null) continue;
                    String col = tid.value();
                    if (col != null && !col.isBlank()) return col;
                    return camelToUnderline(f.getName());
                }
            }
        } catch (Exception e) {
            log.warn("[导出] 解析实体主键列失败，回退 id: entity={} err={}", entityName, e.getMessage());
        }
        return "id";
    }

    /** 驼峰转下划线（@TableId 未显式指定列名时兜底；纯函数，供静态 setByColumn 复用） */
    private static String camelToUnderline(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 导出取数条件 ＝ 业务准入门槛 + 数据权限（与各列表页逐字同口径）；两者皆无 → 返回 null（保持改造前行为）。
     *
     * <p>门槛来源＝唯一登记表 {@link com.erp.export.registry.BillExcelRegistry.Bill} 的 {@code extraGate}
     * （对应列表页 {@code applyExtraFilter}）；权限来源＝同表的
     * scopeOwnerColumn / scopeCreatedByColumn / scopeWhitelist（对应列表页 {@code applyDataScopeFilter}）。</p>
     *
     * <p>未声明数据权限（scopeEnabled=false）= 该实体列表页本身也未接数据权限，保持不动，
     * 避免制造"列表全量、导出只剩一半"的新差异（那属于列表侧待补，已在交付报告中列出）。</p>
     */
    private QueryWrapper<?> buildExportWrapper(String entityName) {
        com.erp.export.registry.BillExcelRegistry.Bill bill = com.erp.export.registry.BillExcelRegistry.get(entityName);
        QueryWrapper<Object> qw = new QueryWrapper<>();
        boolean conditioned = false;

        // ① 业务准入门槛：与列表页 Controller#applyExtraFilter 同口径。
        //    注意：管理员也受限（列表对管理员同样套这道门槛），否则又会出现"导出比列表宽"。
        if (bill != null
                && bill.extraGate() == com.erp.export.registry.BillExcelRegistry.ExtraGate.CUSTOMER_ARCHIVE) {
            // 与 basedata/CustomerController#applyExtraFilter 逐字一致：
            // 仅「正式客户」与历史存量（crm_status 为空）属于客户档案；
            // CRM 私海培育中的潜在/意向客户、公海客户都不进客户档案，因此也不能从客户档案导出。
            qw.and(w -> w.eq("crm_status", "正式客户").or().isNull("crm_status"));
            conditioned = true;
        }

        // ② 数据权限（与各列表 Service 的 DataScopeResolver.applyTo(...) 一一对应）
        if (bill != null && bill.scopeEnabled()) {
            jakarta.servlet.http.HttpServletRequest req = currentRequest();
            if (req != null) { // 非 Web 上下文（内部调用/定时任务）：保持旧行为
                com.erp.common.scope.DataScopeResolver.DataScopeContext ctx = exportDataScopeResolver.resolve(req);
                if (!ctx.isAll()) { // 全部数据（管理员等）：不加权限条件，与改造前逐条一致
                    String idColumn = whitelistIdColumn(bill.scopeWhitelist());
                    if (idColumn != null) {
                        ctx.applyToWithWhitelist(qw, idColumn, whitelistOf(bill, ctx),
                                bill.scopeOwnerColumn(), bill.scopeCreatedByColumn());
                    } else {
                        ctx.applyTo(qw, bill.scopeOwnerColumn(), bill.scopeCreatedByColumn());
                    }
                    conditioned = true;
                }
            }
        }
        return conditioned ? qw : null;
    }

    /** 白名单对应的主键列（与 Warehouse/Supplier/Material/Customer 列表页覆写的 applyDataScopeFilter 一致） */
    private static String whitelistIdColumn(com.erp.export.registry.BillExcelRegistry.WhitelistKind kind) {
        if (kind == null) return null;
        return switch (kind) {
            case CUSTOMER -> "customer_id";
            case SUPPLIER -> "supplier_id";
            case WAREHOUSE -> "stock_id";
            case MATERIAL_CATEGORY -> "category_id";
            default -> null;
        };
    }

    private static List<Long> whitelistOf(com.erp.export.registry.BillExcelRegistry.Bill bill,
                                          com.erp.common.scope.DataScopeResolver.DataScopeContext ctx) {
        com.erp.export.registry.BillExcelRegistry.WhitelistKind kind = bill.scopeWhitelist();
        if (kind == null) return Collections.emptyList();
        return switch (kind) {
            case CUSTOMER -> ctx.getCustomerWhitelist();
            case SUPPLIER -> ctx.getSupplierWhitelist();
            case WAREHOUSE -> ctx.getWarehouseWhitelist();
            case MATERIAL_CATEGORY -> ctx.getCategoryWhitelist();
            default -> Collections.emptyList();
        };
    }

    /** 取当前 Web 请求（导出接口必然在请求内；拿不到则视为无上下文，不套权限，保持旧行为） */
    private static jakarta.servlet.http.HttpServletRequest currentRequest() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 实体名 → 取数 Mapper：由登记表声明 mapperClass，Spring 容器按类型取 bean。
     * 铁律：新增单据只改 BillExcelRegistry 一行，本方法不需要动。
     */
    private BaseMapper<?> getMapper(String entityName) {
        if (!com.erp.export.registry.BillExcelRegistry.exists(entityName)) {
            return null;
        }
        return mapperCache.computeIfAbsent(entityName, code -> {
            Class<?> mapperClass = com.erp.export.registry.BillExcelRegistry.get(code).mapperClass();
            if (mapperClass == null || !BaseMapper.class.isAssignableFrom(mapperClass)) {
                return null;
            }
            Object bean = applicationContext.getBean(mapperClass);
            return bean instanceof BaseMapper<?> bm ? bm : null;
        });
    }

    private List<ExportConfigDTO.FieldConfig> getFieldConfigs(Class<?> entityClass) {
        List<ExportConfigDTO.FieldConfig> configs = new ArrayList<>();
        Field[] fields = entityClass.getDeclaredFields();

        for (Field field : fields) {
            ExportField annotation = field.getAnnotation(ExportField.class);
            if (annotation != null && !annotation.hidden()) {
                ExportConfigDTO.FieldConfig config = new ExportConfigDTO.FieldConfig();
                config.setFieldName(field.getName());
                config.setLabel(annotation.label());
                config.setOrder(annotation.order());
                config.setRequired(annotation.required());
                config.setDateFormat(annotation.dateFormat());
                config.setNumberFormat(annotation.numberFormat());
                config.setHidden(annotation.hidden());
                configs.add(config);
            }
        }

        configs.sort(Comparator.comparingInt(ExportConfigDTO.FieldConfig::getOrder));
        return configs;
    }

    private List<String> getFieldLabels(Class<?> entityClass) {
        List<String> labels = new ArrayList<>();
        Field[] fields = entityClass.getDeclaredFields();

        for (Field field : fields) {
            ExportField annotation = field.getAnnotation(ExportField.class);
            if (annotation != null && !annotation.hidden()) {
                labels.add(annotation.label());
            }
        }

        labels.sort((a, b) -> {
            Field f1 = findFieldByLabel(entityClass, a);
            Field f2 = findFieldByLabel(entityClass, b);
            ExportField e1 = f1 != null ? f1.getAnnotation(ExportField.class) : null;
            ExportField e2 = f2 != null ? f2.getAnnotation(ExportField.class) : null;
            int o1 = e1 != null ? e1.order() : 0;
            int o2 = e2 != null ? e2.order() : 0;
            return Integer.compare(o1, o2);
        });

        return labels;
    }

    /**
     * 获取带必填标记的字段标签列表（用于导入模板）
     * 必填字段的标签后追加 "*" 标记，便于用户识别
     */
    private List<String> getFieldLabelsWithRequiredMark(Class<?> entityClass) {
        return getFieldLabelsWithRequiredMark(entityClass, false);
    }

    /**
     * 获取带必填标记的字段标签列表（用于导入模板）
     * @param autoCode 开启自动编码时，"编码"结尾的字段不追加 *（导入时空编码自动生成）
     */
    private List<String> getFieldLabelsWithRequiredMark(Class<?> entityClass, boolean autoCode) {
        List<String> labels = new ArrayList<>();
        Field[] fields = entityClass.getDeclaredFields();
        // 字段权限（角色必填）叠加：与实体静态必填取并集，保证"界面必填 = 模板星号 = 导入必填"
        Set<String> permRequiredLabels = roleRequiredLabelsOfPage(pageKeyOf(entityClass));

        for (Field field : fields) {
            ExportField annotation = field.getAnnotation(ExportField.class);
            if (annotation != null && !annotation.hidden()) {
                // 必填字段在标签后加 * 标记；自动编码开启时编码列除外
                String label = annotation.label();
                boolean required = annotation.required() || permRequiredLabels.contains(label);
                if (required && !(autoCode && (label.endsWith("编码") || label.endsWith("号")))) {
                    label = label + "*";
                }
                labels.add(label);
            }
        }

        labels.sort((a, b) -> {
            // 排序时去掉 * 标记再查找字段
            String rawA = a.endsWith("*") ? a.substring(0, a.length() - 1) : a;
            String rawB = b.endsWith("*") ? b.substring(0, b.length() - 1) : b;
            Field f1 = findFieldByLabel(entityClass, rawA);
            Field f2 = findFieldByLabel(entityClass, rawB);
            ExportField e1 = f1 != null ? f1.getAnnotation(ExportField.class) : null;
            ExportField e2 = f2 != null ? f2.getAnnotation(ExportField.class) : null;
            int o1 = e1 != null ? e1.order() : 0;
            int o2 = e2 != null ? e2.order() : 0;
            return Integer.compare(o1, o2);
        });

        return labels;
    }

    private Field findFieldByLabel(Class<?> entityClass, String label) {
        for (Field field : entityClass.getDeclaredFields()) {
            ExportField annotation = field.getAnnotation(ExportField.class);
            if (annotation != null && annotation.label().equals(label)) {
                return field;
            }
        }
        return null;
    }

    private Map<String, String> getFieldLabelMap(Class<?> entityClass) {
        Map<String, String> map = new HashMap<>();
        Field[] fields = entityClass.getDeclaredFields();

        for (Field field : fields) {
            ExportField annotation = field.getAnnotation(ExportField.class);
            if (annotation != null && !annotation.hidden()) {
                map.put(field.getName(), annotation.label());
            }
        }
        return map;
    }

    private List<Map<String, Object>> convertToMapList(List<?> dataList, Class<?> entityClass) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, String> fieldLabelMap = getFieldLabelMap(entityClass);

        for (Object item : dataList) {
            Map<String, Object> row = new LinkedHashMap<>();
            Field[] fields = entityClass.getDeclaredFields();

            for (Field field : fields) {
                ExportField annotation = field.getAnnotation(ExportField.class);
                if (annotation != null && !annotation.hidden()) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(item);
                        row.put(annotation.label(), formatValue(value, annotation));
                    } catch (IllegalAccessException e) {
                        row.put(annotation.label(), "");
                    }
                }
            }
            result.add(row);
        }
        return result;
    }

    private Object formatValue(Object value, ExportField annotation) {
        if (value == null) return "";

        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DateTimeFormatter.ofPattern(annotation.dateFormat()));
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if (value instanceof BigDecimal) {
            if (StringUtils.hasText(annotation.numberFormat())) {
                return ((BigDecimal) value).toPlainString();
            }
            return value;
        }
        return value;
    }

    private Map<String, Integer> buildHeaderIndexMap(List<String> headers, Map<String, String> fieldLabelMap) {
        Map<String, Integer> map = new HashMap<>();
        Set<String> expectedLabels = new HashSet<>(fieldLabelMap.values());

        for (int i = 0; i < headers.size(); i++) {
            String header = StringUtils.trimWhitespace(headers.get(i));
            // 兼容带 * 必填标记的表头：去掉末尾的 * 再匹配
            // 这样用户用下载的模板（表头带 *）填写数据后上传，也能正确识别列
            String normalizedHeader = header.endsWith("*")
                    ? header.substring(0, header.length() - 1).trim()
                    : header;
            if (expectedLabels.contains(normalizedHeader)) {
                for (Map.Entry<String, String> entry : fieldLabelMap.entrySet()) {
                    if (entry.getValue().equals(normalizedHeader)) {
                        map.put(entry.getKey(), i);
                        break;
                    }
                }
            }
        }
        return map;
    }

    private void populateEntity(Object entity, List<Object> row, Map<String, Integer> headerIndexMap,
                                Map<String, String> fieldLabelMap, Class<?> entityClass, int rowIndex, ImportResultDTO result) {
        populateEntity(entity, row, headerIndexMap, fieldLabelMap, entityClass, rowIndex, result, false);
    }

    /**
     * @param autoCode 开启自动编码时，编码列（label 以"编码"结尾）为空不报"不能为空"，
     *                 入库前由 saveEntities 统一自动生成
     */
    private void populateEntity(Object entity, List<Object> row, Map<String, Integer> headerIndexMap,
                                Map<String, String> fieldLabelMap, Class<?> entityClass, int rowIndex, ImportResultDTO result,
                                boolean autoCode) {
        Field[] fields = entityClass.getDeclaredFields();

        for (Field field : fields) {
            ExportField annotation = field.getAnnotation(ExportField.class);
            if (annotation != null && !annotation.hidden()) {
                Integer index = headerIndexMap.get(field.getName());
                if (index != null && index < row.size()) {
                    Object cellValue = row.get(index);
                    field.setAccessible(true);

                    try {
                        boolean codeFieldSkipped = autoCode && annotation.label().endsWith("编码");
                        if (annotation.required() && isEmptyValue(cellValue) && !codeFieldSkipped) {
                            result.addError(rowIndex, annotation.label(), "不能为空");
                            continue;
                        }

                        // ID 型列（如「分配销售员ID」「默认业务员ID」）：空白或填 0 一律按"没填"处理 → 写 null。
                        // 原实现 convertValue 把空白数字列统一转成 0，而 0 不是任何合法自增ID：
                        // 在带外键的库（121 生产的 customer→employee）会直接报"违反外键约束"，把整行导入打挂。
                        if (isIdField(field) && (isEmptyValue(cellValue) || isZeroValue(cellValue))) {
                            field.set(entity, null);
                            continue;
                        }

                        Object convertedValue = convertValue(cellValue, field.getType(), annotation);
                        field.set(entity, convertedValue);
                    } catch (Exception e) {
                        result.addError(rowIndex, annotation.label(), "格式错误: " + e.getMessage());
                    }
                }
            }
        }
    }

    private Object convertValue(Object cellValue, Class<?> targetType, ExportField annotation) {
        if (cellValue == null || cellValue.toString().trim().isEmpty()) {
            if (targetType == String.class) return "";
            if (targetType == Integer.class || targetType == int.class) return 0;
            if (targetType == Long.class || targetType == long.class) return 0L;
            if (targetType == BigDecimal.class) return BigDecimal.ZERO;
            if (targetType == Short.class || targetType == short.class) return (short) 0;
            return null;
        }

        String valueStr = cellValue.toString().trim();

        if (targetType == String.class) return valueStr;
        if (targetType == Integer.class || targetType == int.class) return Integer.parseInt(valueStr);
        if (targetType == Long.class || targetType == long.class) return Long.parseLong(valueStr);
        if (targetType == BigDecimal.class) return new BigDecimal(valueStr);
        if (targetType == Short.class || targetType == short.class) return Short.parseShort(valueStr);

        // 日期列：先按注解声明的格式解析（导出/模板口径），失败再走宽松兼容
        // （2026-09-19：用户粘过来的 2026/9/19、2026.9.19、20260919、2026年9月19日 都要能导入）
        if (targetType == LocalDate.class) {
            try {
                return LocalDate.parse(valueStr, DateTimeFormatter.ofPattern(annotation.dateFormat()));
            } catch (Exception ignore) {
                return com.erp.common.util.FlexibleDateUtils.parseLocalDate(valueStr);
            }
        }
        if (targetType == LocalDateTime.class) {
            try {
                return LocalDateTime.parse(valueStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception ignore) {
                return com.erp.common.util.FlexibleDateUtils.parseLocalDateTime(valueStr);
            }
        }

        return cellValue;
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) return true;
        if (value instanceof String) return ((String) value).trim().isEmpty();
        return false;
    }

    /** ID 型字段：以 Id 结尾的包装数字类型（Long/Integer/Short），如 assignedSalesId */
    private boolean isIdField(Field field) {
        if (field == null || !field.getName().endsWith("Id")) return false;
        Class<?> t = field.getType();
        return t == Long.class || t == Integer.class || t == Short.class;
    }

    /** 单元格是否显式填了 0（数字 0 或文本 "0"）——ID 列填 0 等同于没填 */
    private boolean isZeroValue(Object cellValue) {
        if (cellValue == null) return false;
        if (cellValue instanceof Number) {
            return ((Number) cellValue).doubleValue() == 0d;
        }
        String s = cellValue.toString().trim();
        return "0".equals(s) || "0.0".equals(s);
    }

    /** 当前登录账号ID（请求属性优先，取不到则按用户名兜底查库） */
    private Long currentLoginUserId() {
        jakarta.servlet.http.HttpServletRequest req = currentRequest();
        if (req == null) return null;
        Long userId = com.erp.common.util.SecurityUtils.getCurrentUserId(req);
        if (userId != null) return userId;
        String username = com.erp.common.util.SecurityUtils.getCurrentUsername(req);
        if (!StringUtils.hasText(username)) return null;
        try {
            com.erp.system.entity.SysUser u = sysUserMapper.selectOne(
                    new QueryWrapper<com.erp.system.entity.SysUser>().eq("username", username).last("LIMIT 1"));
            return u == null ? null : u.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 当前登录账号绑定的员工ID。
     * 优先查库取实时值（账号刚绑定员工但未重登时，token 里的快照会是旧值）；
     * 查询异常时退回请求属性。
     */
    private Long currentLoginEmployeeId(Long userId) {
        if (userId != null) {
            try {
                com.erp.system.entity.SysUser u = sysUserMapper.selectById(userId);
                // 实时值即口径：账号当前没绑员工就是 null（走"未绑定员工档案"提示），
                // 不能用登录时快照兜底，否则刚解绑的账号会被填上过期员工ID
                if (u != null) return u.getEmployeeId();
            } catch (Exception e) {
                // 查询异常时才退回请求属性
            }
        }
        jakarta.servlet.http.HttpServletRequest req = currentRequest();
        return req == null ? null : com.erp.common.util.SecurityUtils.getCurrentEmployeeId(req);
    }

    /**
     * 客户档案导入默认人（2026-09-21，与页面「新增客户」同口径）：
     * ① 归属人 = 当前登录账号（页面新增客户默认归属当前用户，私海可见性按它统计）；
     * ② 分配销售员 = 当前登录账号绑定的员工；账号没有员工档案时留空并在导入结果里提示；
     * ③ 默认业务员不自动填（用户口径：避免新建销售单据时自动带出业务员）。
     */
    private void applyCustomerImportDefaults(Customer customer, Long loginUserId, Long loginEmployeeId,
                                            ImportResultDTO result) {
        if (customer.getOwnerUserId() == null && loginUserId != null) {
            customer.setOwnerUserId(loginUserId);
        }
        if (customer.getAssignedSalesId() == null) {
            if (loginEmployeeId != null) {
                customer.setAssignedSalesId(loginEmployeeId);
            } else {
                appendNoEmployeeHint(result);
            }
        }
    }

    /** 汇总提示：当前账号未绑定员工档案（同一批导入只提示一次） */
    private void appendNoEmployeeHint(ImportResultDTO result) {
        if (result == null) return;
        String hint = "当前登录账号未绑定员工档案，「分配销售员」未自动填写；可在 基础资料→员工档案 给该账号建员工档案后重新导入。";
        String existed = result.getMessage();
        if (existed != null && existed.contains("未绑定员工档案")) return;
        result.setMessage(existed == null || existed.isBlank() ? hint : existed + " " + hint);
    }

    /** 列名(snake_case) → 导出标签（中文），把数据库约束报错里的字段名换成人看得懂的名字 */
    private Map<String, String> columnLabelMap(Class<?> entityClass) {
        Map<String, String> m = new HashMap<>();
        if (entityClass == null) return m;
        for (Field f : entityClass.getDeclaredFields()) {
            ExportField ann = f.getAnnotation(ExportField.class);
            if (ann != null && !ann.hidden()) {
                m.put(camelToSnake(f.getName()), ann.label());
            }
        }
        return m;
    }

    /**
     * 逐行落库失败 → 用户看得懂的中文提示。
     * 原实现直接把 MyBatis 原始报文（### Error updating database …）抛给用户；
     * 这里复用 GlobalExceptionHandler 的翻译（兼容中文/英文 PG 报文），并把字段名换成中文标签。
     */
    private String friendlyImportError(Exception e, Map<String, String> columnLabels) {
        try {
            String tip = com.erp.config.GlobalExceptionHandler.friendlyDataError(e);
            if (tip != null) {
                if (columnLabels != null) {
                    for (Map.Entry<String, String> en : columnLabels.entrySet()) {
                        tip = tip.replace("字段:" + en.getKey(), "字段:" + en.getValue());
                    }
                }
                return tip;
            }
        } catch (Exception ignore) {
            // 翻译失败时保留原始消息
        }
        return e.getMessage();
    }

    private boolean isRowEmpty(List<Object> row) {
        return row.stream().allMatch(this::isEmptyValue);
    }

    /** 无自定义字段的保存（线索带图片导入用）：insert 失败必须如实计失败，不能谎报成功 */
    @SuppressWarnings("unchecked")
    private void saveEntities(String entityName, List<Object> entities, ImportResultDTO result) {
        BaseMapper<?> mapper = getMapper(entityName);
        if (mapper == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }

        boolean autoCode = isAutoCodeEnabled(entityName);
        // 落库报错的字段名 → 中文标签
        Map<String, String> columnLabels = entities.isEmpty() ? Collections.emptyMap()
                : columnLabelMap(entities.get(0).getClass());
        for (int i = 0; i < entities.size(); i++) {
            Object entity = entities.get(i);
            try {
                // 开启自动编码时，编码为空的行在入库前自动生成（导入直插 mapper 不经过各 Service 的兜底逻辑）
                if (autoCode) {
                    fillAutoCode(entityName, entity);
                }
                ((BaseMapper<Object>) mapper).insert(entity);
            } catch (Exception e) {
                // 【2026-09-14 修复】原实现只 log.warn 吞掉异常：解析阶段已经 successCount+1，
                // 落库失败后计数不回退也不记错误 → 接口/AI 汇报"成功 N 行"，实际少入库（谎报成功）。
                log.warn("保存实体失败: {}", e.getMessage());
                if (result != null) {
                    if (result.getSuccessCount() > 0) {
                        result.setSuccessCount(result.getSuccessCount() - 1);
                    }
                    result.setFailCount(result.getFailCount() + 1);
                    // 2026-09-21：改用中文友好提示（原实现直接把 MyBatis 原始报文抛给用户）
                    result.addError(0, "", "第" + (i + 1) + "条记录保存失败: " + friendlyImportError(e, columnLabels));
                }
            }
        }
    }

    /**
     * 该导入实体是否可自动生成编码（决定模板编码列是否标 * 与导入时是否免填）。
     * 通用口径：sys_serial_no_config.auto_generate 实时判断；
     * 物料补充口径：存在启用中的"物料编码规则"（material_code_rule.is_enabled=1）时同样视为可自动生成——
     * 与页面新增物料（MaterialServiceImpl：分类规则优先）及导入落库补码（fillAutoCode）保持同一口径，
     * 避免客户使用"分类规则自动生成编码"时，导入模板仍把编码标为必填。
     */
    private boolean isAutoCodeEnabled(String entityName) {
        String docType = AUTO_CODE_DOC_TYPE.get(entityName);
        if (docType == null) return false;
        try {
            if (serialNoConfigService.isAutoGenerate(docType)) {
                return true;
            }
            if ("material".equals(entityName)) {
                return hasEnabledMaterialCodeRule();
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** 是否存在启用中的物料编码规则（含 category_id 为空的通用规则） */
    private boolean hasEnabledMaterialCodeRule() {
        try {
            List<com.erp.masterdata.entity.MaterialCodeRule> rules = materialCodeRuleService.listAll();
            if (rules == null || rules.isEmpty()) {
                return false;
            }
            return rules.stream().anyMatch(r -> r.getIsEnabled() != null && r.getIsEnabled() == 1);
        } catch (Exception e) {
            log.warn("查询物料编码规则失败，按未启用处理: {}", e.getMessage());
            return false;
        }
    }

    // ========== 自定义字段导入导出支持 ==========

    /**
     * 带自定义字段回填的保存：insert 成功后（MyBatis-Plus 回填自增ID）写入 EAV 值表。
     * customValues 与 entities 按索引一一对应（调用方在同一 try 块内同步收集）。
     * 字段值保存失败仅告警不阻断——主数据已入库，不能因扩展字段失败让用户误以为整行失败。
     * 主表 insert 失败必须如实计失败（解析阶段已 successCount+1，不回退会谎报成功）。
     */
    @SuppressWarnings("unchecked")
    private void saveEntitiesWithCustomFields(String entityName, List<Object> entities, List<Map<String, Object>> customValues,
                                              ImportResultDTO result) {
        BaseMapper<?> mapper = getMapper(entityName);
        if (mapper == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }

        String entityType = camelToSnake(entityName);
        boolean autoCode = isAutoCodeEnabled(entityName);
        // 落库报错的字段名 → 中文标签（如 assigned_sales_id → 分配销售员ID）
        Map<String, String> columnLabels = entities.isEmpty() ? Collections.emptyMap()
                : columnLabelMap(entities.get(0).getClass());
        for (int i = 0; i < entities.size(); i++) {
            Object entity = entities.get(i);
            try {
                // 开启自动编码时，编码为空的行在入库前自动生成（导入直插 mapper 不经过各 Service 的兜底逻辑）
                if (autoCode) {
                    fillAutoCode(entityName, entity);
                }
                ((BaseMapper<Object>) mapper).insert(entity);
                // 回填自定义字段值（无定义/无值/无ID时自动跳过）
                Map<String, Object> vals = (customValues != null && i < customValues.size()) ? customValues.get(i) : null;
                if (vals != null && !vals.isEmpty()) {
                    Long entityId = extractEntityId(entity);
                    if (entityId != null) {
                        try {
                            customFieldService.saveFieldValues(entityType, entityId, vals);
                        } catch (Exception ex) {
                            log.warn("导入回填自定义字段失败, entity={}, id={}: {}", entityName, entityId, ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                // 【2026-09-14 修复】原实现只 log.warn 吞掉异常：解析阶段已经 successCount+1，
                // 落库失败后计数不回退也不记错误 → 接口/AI 汇报"成功 N 行"，实际少入库（谎报成功）。
                log.warn("保存实体失败: {}", e.getMessage());
                if (result != null) {
                    if (result.getSuccessCount() > 0) {
                        result.setSuccessCount(result.getSuccessCount() - 1);
                    }
                    result.setFailCount(result.getFailCount() + 1);
                    // 2026-09-21：改用中文友好提示（原实现直接把 MyBatis 原始报文抛给用户）
                    result.addError(0, "", "第" + (i + 1) + "条记录保存失败: " + friendlyImportError(e, columnLabels));
                }
            }
        }
    }

    /** entityName(驼峰, 如 saleOrder) -> 自定义字段 entityType(下划线, 如 sale_order)，与设置中心口径一致 */
    private static String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 取实体启用的自定义字段定义（异常或无定义返回空列表，行为与未接入前完全一致） */
    private List<com.erp.customfield.entity.CustomField> getEnabledCustomFields(String entityName) {
        try {
            return customFieldService.listEnabledByEntity(camelToSnake(entityName));
        } catch (Exception e) {
            log.warn("查询自定义字段定义失败, entity={}: {}", entityName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 实体类 → 字段权限 pageKey（与 registry dataSource key / 自定义字段 entity_type 同口径：类名首字母转小写再转下划线） */
    private String pageKeyOf(Class<?> entityClass) {
        if (entityClass == null) return null;
        String simple = entityClass.getSimpleName();
        if (simple.isEmpty()) return null;
        return camelToSnake(Character.toLowerCase(simple.charAt(0)) + simple.substring(1));
    }

    /** 实体类 → 字段权限 pageKey 的少量例外（类名推出的库名与权限配置页键不一致时在此对齐） */
    private static final Map<String, String> PERMISSION_PAGE_KEY_ALIAS = Map.of(
            "purchase_inbound", "purchase_receipt"
    );

    /** 当前用户在实体对应页面上不可见的字段集合（管理员/无上下文 → 空集；异常不阻断导出） */
    private Set<String> hiddenKeysOf(Class<?> entityClass) {
        try {
            String pageKey = pageKeyOf(entityClass);
            if (pageKey == null) return Collections.emptySet();
            pageKey = PERMISSION_PAGE_KEY_ALIAS.getOrDefault(pageKey, pageKey);
            Set<String> hidden = fieldVisiblePermissionService.currentUserHiddenKeys(pageKey);
            return hidden == null ? Collections.emptySet() : hidden;
        } catch (Exception e) {
            log.warn("[导出] 解析字段权限失败（按不限制处理）: entity={}, err={}",
                    entityClass == null ? null : entityClass.getSimpleName(), e.getMessage());
            return Collections.emptySet();
        }
    }

    /** 字段是否不可见（同时兼容 camelCase / snake_case 两种 fieldKey 写法） */
    private boolean isHiddenField(Set<String> hidden, String key) {
        if (hidden == null || hidden.isEmpty() || key == null) return false;
        return hidden.contains(key)
                || hidden.contains(com.erp.permission.service.FieldVisiblePermissionService.snakeToCamel(key));
    }

    /** 按字段权限过滤后的静态列标签（顺序与 getFieldLabels 完全一致，只少了不可见列） */
    private List<String> visibleFieldLabels(Class<?> entityClass, Set<String> hidden) {
        if (hidden == null || hidden.isEmpty()) return getFieldLabels(entityClass);
        List<String> labels = new ArrayList<>();
        for (Field field : entityClass.getDeclaredFields()) {
            ExportField annotation = field.getAnnotation(ExportField.class);
            if (annotation == null || annotation.hidden()) continue;
            if (isHiddenField(hidden, field.getName())) continue;
            labels.add(annotation.label());
        }
        labels.sort((a, b) -> {
            Field f1 = findFieldByLabel(entityClass, a);
            Field f2 = findFieldByLabel(entityClass, b);
            ExportField e1 = f1 != null ? f1.getAnnotation(ExportField.class) : null;
            ExportField e2 = f2 != null ? f2.getAnnotation(ExportField.class) : null;
            int o1 = e1 != null ? e1.order() : 0;
            int o2 = e2 != null ? e2.order() : 0;
            return Integer.compare(o1, o2);
        });
        return labels;
    }

    /** 当前登录用户在该页面被角色配置为必填的字段标签集合（取不到/管理员 → 空集；异常不阻断导出） */
    private Set<String> roleRequiredLabelsOfPage(String pageKey) {
        if (pageKey == null || pageKey.isBlank()) return Collections.emptySet();
        try {
            return fieldRequiredPermissionService.currentUserRequiredLabels(pageKey);
        } catch (Exception e) {
            log.warn("读取字段权限必填失败, pageKey={}: {}", pageKey, e.getMessage());
            return Collections.emptySet();
        }
    }

    /** 字段权限必填的一次性解析（导入循环内复用，避免逐行查库；异常降级为空结果） */
    private com.erp.permission.service.FieldRequiredPermissionService.PageRequired resolvePermRequired(String pageKey) {
        try {
            return fieldRequiredPermissionService.resolve(pageKey);
        } catch (Exception e) {
            log.warn("解析字段权限必填失败, pageKey={}: {}", pageKey, e.getMessage());
            return new com.erp.permission.service.FieldRequiredPermissionService.PageRequired(
                    Collections.emptyMap(), Collections.emptySet());
        }
    }

    /**
     * 模板用：自定义字段列标签（必填加 *，与静态字段口径一致）。
     * 排除 formula 类型（值由公式后端计算，用户填写也会被覆盖，带出只会误导）；
     * 跳过与静态字段标签同名的自定义字段（避免模板出现同名列导致导入时静态列被误占用）。
     */
    private List<String> getCustomFieldLabels(List<com.erp.customfield.entity.CustomField> fields, List<String> staticLabels) {
        Set<String> staticRaw = new HashSet<>();
        for (String s : staticLabels) {
            staticRaw.add(s.endsWith("*") ? s.substring(0, s.length() - 1) : s);
        }
        List<String> labels = new ArrayList<>();
        // 字段权限（角色必填）叠加：同一实体下的自定义字段 entityType 相同，取一次即可
        Set<String> permRequiredLabels = fields.isEmpty()
                ? Collections.emptySet()
                : roleRequiredLabelsOfPage(fields.get(0).getEntityType());
        for (com.erp.customfield.entity.CustomField f : fields) {
            if ("formula".equals(f.getFieldType())) continue;
            if (staticRaw.contains(f.getFieldLabel())) continue;
            boolean required = (f.getIsRequired() != null && f.getIsRequired() == 1)
                    || permRequiredLabels.contains(f.getFieldLabel());
            labels.add(f.getFieldLabel() + (required ? "*" : ""));
        }
        return labels;
    }

    /**
     * 导入用：表头 -> 自定义字段列索引映射。
     * 表头去 * 后按 fieldLabel 匹配；已被静态字段占用的列跳过（静态优先）；
     * formula 列不解析（值由 saveFieldValues 后端重算）。
     */
    private Map<Integer, com.erp.customfield.entity.CustomField> buildCustomFieldIndexMap(
            List<String> headers, List<com.erp.customfield.entity.CustomField> customFields, Map<String, Integer> headerIndexMap) {
        Map<Integer, com.erp.customfield.entity.CustomField> map = new HashMap<>();
        if (customFields == null || customFields.isEmpty()) return map;

        Map<String, com.erp.customfield.entity.CustomField> labelMap = new HashMap<>();
        for (com.erp.customfield.entity.CustomField f : customFields) {
            if ("formula".equals(f.getFieldType())) continue;
            labelMap.put(f.getFieldLabel(), f);
        }
        Set<Integer> staticIndexes = new HashSet<>(headerIndexMap.values());

        for (int i = 0; i < headers.size(); i++) {
            if (staticIndexes.contains(i)) continue;
            String h = headers.get(i) == null ? "" : headers.get(i).trim();
            if (h.endsWith("*")) h = h.substring(0, h.length() - 1).trim();
            com.erp.customfield.entity.CustomField f = labelMap.get(h);
            if (f != null) map.put(i, f);
        }
        return map;
    }

    /**
     * 从行数据解析自定义字段值：
     * - select：填选项值或选项名均接受，统一转选项值存储
     * - number：校验数值格式，非法报错跳过该字段
     * - 必填空值报"不能为空"（与静态字段口径一致：调用方据此拦截该行/该单，不再"只提示仍入库"）
     * 其余类型统一文本存储（field_value 为 text 列）。
     */
    private Map<String, Object> parseCustomFieldValues(
            Map<Integer, com.erp.customfield.entity.CustomField> indexMap, List<Object> row, int rowIndex,
            ImportResultDTO result, Set<String> roleRequiredLabels) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, com.erp.customfield.entity.CustomField> e : indexMap.entrySet()) {
            int idx = e.getKey();
            com.erp.customfield.entity.CustomField f = e.getValue();
            Object cell = idx < row.size() ? row.get(idx) : null;
            String v = cell == null ? "" : String.valueOf(cell).trim();
            if (v.isEmpty()) {
                // 必填口径 = 自定义字段自身 is_required=1 或 该角色在字段权限里叠加的必填
                if ((f.getIsRequired() != null && f.getIsRequired() == 1)
                        || (roleRequiredLabels != null && roleRequiredLabels.contains(f.getFieldLabel()))) {
                    result.addError(rowIndex, f.getFieldLabel(), "不能为空");
                }
                continue;
            }
            if ("number".equals(f.getFieldType())) {
                try {
                    new BigDecimal(v);
                } catch (Exception ex) {
                    result.addError(rowIndex, f.getFieldLabel(), "格式错误: 不是有效数字");
                    continue;
                }
            } else if ("select".equals(f.getFieldType())) {
                v = resolveSelectValue(f, v);
            }
            values.put(f.getFieldKey(), v);
        }
        return values;
    }

    /** select 单元格值：填选项 value 或 label 均接受，统一转成 value 存储；匹配不到按原值存储（前端显示原文本） */
    private String resolveSelectValue(com.erp.customfield.entity.CustomField f, String cellVal) {
        try {
            if (f.getFieldOptions() == null || f.getFieldOptions().isBlank()) return cellVal;
            com.fasterxml.jackson.databind.JsonNode arr = CUSTOM_FIELD_MAPPER.readTree(f.getFieldOptions());
            if (!arr.isArray()) return cellVal;
            for (com.fasterxml.jackson.databind.JsonNode o : arr) {
                if (cellVal.equals(o.path("value").asText(null))) {
                    return cellVal; // 直接填的就是选项值
                }
            }
            for (com.fasterxml.jackson.databind.JsonNode o : arr) {
                if (cellVal.equals(o.path("label").asText(null))) {
                    return o.path("value").asText(cellVal); // 填选项名 → 转选项值
                }
            }
        } catch (Exception e) {
            log.debug("解析select选项失败, fieldKey={}: {}", f.getFieldKey(), e.getMessage());
        }
        return cellVal;
    }

    // ========== 销售单据主子表自定义字段辅助 ==========

    /**
     * 向模板 labels 追加销售单据自定义字段：表头自定义字段放在表头静态列之后、明细列之前；
     * 明细自定义字段放在明细静态列之后。自定义字段默认随精简模板带出（不受 fields 过滤）。
     *
     * @param labels             模板列标签列表（调用时已含表头静态列）
     * @param headerEntityType   表头实体类型（snake_case，如 sale_order）
     * @param itemEntityType     明细实体类型（snake_case，如 sale_order_item）
     * @param headerStaticLabels 表头静态列标签（用于自定义字段去重）
     * @param itemStaticLabels   明细静态列标签
     */
    private void appendSaleCustomFieldLabels(List<String> labels, String headerEntityType, String itemEntityType,
                                             List<String> headerStaticLabels, List<String> itemStaticLabels) {
        // 表头自定义字段
        List<com.erp.customfield.entity.CustomField> hdrFields = getEnabledCustomFields(headerEntityType);
        List<String> hdrCustom = getCustomFieldLabels(hdrFields, headerStaticLabels);
        labels.addAll(hdrCustom);
        // 明细静态列
        labels.addAll(itemStaticLabels);
        // 明细自定义字段
        List<com.erp.customfield.entity.CustomField> itemFields = getEnabledCustomFields(itemEntityType);
        List<String> allStatic = new ArrayList<>(headerStaticLabels);
        allStatic.addAll(hdrCustom);
        allStatic.addAll(itemStaticLabels);
        List<String> itemCustom = getCustomFieldLabels(itemFields, allStatic);
        labels.addAll(itemCustom);
    }

    /**
     * 从按列名映射的 cells 中提取自定义字段值（fieldKey -> value）。
     * number 类型转 BigDecimal，select 类型走选项值转换，其余按文本存储。
     */
    private Map<String, Object> extractCustomValsFromCells(Map<String, String> cells,
                                                           List<com.erp.customfield.entity.CustomField> fields) {
        Map<String, Object> vals = new LinkedHashMap<>();
        if (fields == null || fields.isEmpty() || cells == null) return vals;
        for (com.erp.customfield.entity.CustomField f : fields) {
            if ("formula".equals(f.getFieldType())) continue;
            String v = cells.get(f.getFieldLabel());
            if (v == null || v.isEmpty()) continue;
            if ("number".equals(f.getFieldType())) {
                try {
                    vals.put(f.getFieldKey(), new java.math.BigDecimal(v));
                } catch (NumberFormatException ignored) {
                    vals.put(f.getFieldKey(), v);
                }
            } else if ("select".equals(f.getFieldType())) {
                vals.put(f.getFieldKey(), resolveSelectValue(f, v));
            } else {
                vals.put(f.getFieldKey(), v);
            }
        }
        return vals;
    }

    /**
     * 单据导入「保存前」必填校验：字段权限角色必填（静态字段）+ 自定义字段必填（is_required 或角色叠加），
     * 表头与明细都校验；返回 true 表示全部通过（调用方据此决定整单不入库）。
     * 统一在此处记账错误，避免 7 条单据导入路径各写一遍。
     */
    /**
     * 【通用件】实体类型（camel 或 snake）→ 导出导入登记表条目。
     * 由登记表派生（BillExcelRegistry.all()），**禁止手写实体清单**；未登记返回 null。
     */
    private com.erp.export.registry.BillExcelRegistry.Bill billOfEntityType(String entityType) {
        if (entityType == null || entityType.isBlank()) return null;
        for (com.erp.export.registry.BillExcelRegistry.Bill b : com.erp.export.registry.BillExcelRegistry.all()) {
            String code = b.code();
            if (code.equals(entityType) || camelToSnake(code).equals(entityType)) return b;
        }
        return null;
    }

    /**
     * 【通用件】实体「静态必填」标签（与导入模板列头的 * 同源）。
     * 口径：@ExportField(required=true)；自动编码开启时"编码/号"结尾列不加 *（导入时自动生成）。
     * 说明：与"字段权限必填"分工不重叠——权限那一半仍由 findMissingStaticLabels 负责，两者取并集即为模板星号口径。
     * 纯反射、不查库，可在导入逐行循环内安全调用。
     * 包级可见：供 {@code ExcelImportRequiredLabelTest} 直接断言"单号列是否被自动编码豁免"。
     */
    static Set<String> entityRequiredLabels(Class<?> entityClass, boolean autoCode) {
        Set<String> labels = new java.util.LinkedHashSet<>();
        if (entityClass == null) return labels;
        for (java.lang.reflect.Field f : entityClass.getDeclaredFields()) {
            ExportField a = f.getAnnotation(ExportField.class);
            if (a == null || a.hidden() || !a.required()) continue;
            String label = a.label();
            if (autoCode && (label.endsWith("编码") || label.endsWith("号"))) continue;
            labels.add(label);
        }
        return labels;
    }

    /**
     * 【通用件】单据导入：静态必填列校验（唯一的"必填校验"挂载点，覆盖全部单据）。
     * 背景：模板列头星号来自实体必填声明，但导入侧原先只校验"字段权限必填 + 自定义字段必填"，
     *      导致"带 * 却可空"的口径分裂（如其他入库单的入库类型）。
     * 规则：① 该列在**上传的模板中存在**才校验（精简模板不误拦）
     *      ② 空串/纯空格视为未填 ③ 缺失即报"XX不能为空"，由调用方拦整行不入库。
     */
    private boolean validateRequiredStaticLabels(Map<String, Integer> idx, Map<String, String> cells,
                                                 String entityType, int rowIndex, ImportResultDTO result) {
        if (entityType == null || cells == null) return true;
        com.erp.export.registry.BillExcelRegistry.Bill bill = billOfEntityType(entityType);
        if (bill == null) return true;
        Set<String> required = entityRequiredLabels(bill.entityClass(), isAutoCodeEnabled(bill.code()));
        if (required.isEmpty()) return true;
        boolean ok = true;
        for (String label : required) {
            if (idx != null && !idx.containsKey(label)) continue;
            String v = cells.get(label);
            if (v == null || v.isBlank()) {
                result.addError(rowIndex, label, "不能为空");
                ok = false;
            }
        }
        return ok;
    }

    private boolean checkBillRequiredFields(Map<String, String> headerCells,
                                            List<Map<String, String>> itemCells,
                                            List<com.erp.customfield.entity.CustomField> hdrFields,
                                            List<com.erp.customfield.entity.CustomField> itemFields,
                                            Map<String, Integer> idx, int rowIndex, ImportResultDTO result,
                                            com.erp.permission.service.FieldRequiredPermissionService.PageRequired hdrRequired,
                                            com.erp.permission.service.FieldRequiredPermissionService.PageRequired itemRequired,
                                            String headerEntityType, String itemEntityType) {
        boolean ok = true;
        // 静态必填（模板星号口径）：列在上传模板中存在即为必填，缺失拦整行（覆盖全部单据，一处生效）
        if (!validateRequiredStaticLabels(idx, headerCells, headerEntityType, rowIndex, result)) ok = false;
        if (itemCells != null) {
            for (Map<String, String> cells : itemCells) {
                if (!validateRequiredStaticLabels(idx, cells, itemEntityType, rowIndex, result)) ok = false;
            }
        }
        for (String miss : fieldRequiredPermissionService.findMissingStaticLabels(headerCells, hdrRequired)) {
            result.addError(rowIndex, miss, "不能为空");
            ok = false;
        }
        for (Map<String, String> cells : itemCells != null ? itemCells : Collections.<Map<String, String>>emptyList()) {
            for (String miss : fieldRequiredPermissionService.findMissingStaticLabels(cells, itemRequired)) {
                result.addError(rowIndex, miss, "不能为空");
                ok = false;
            }
        }
        if (!validateRequiredCustomFields(idx, headerCells, hdrFields, rowIndex, result, hdrRequired.allLabels())) {
            ok = false;
        }
        if (!validateRequiredCustomItemFields(idx, itemCells, itemFields, rowIndex, result, itemRequired.allLabels())) {
            ok = false;
        }
        return ok;
    }

    /**
     * 单据导入用：校验「自定义字段」必填（缺失则记账错误并返回 false，由调用方决定整单不入库）。
     * 口径与模板一致：① formula 类型不参与（模板本就不带该列）② 未启用字段不参与（getEnabledCustomFields 已过滤）
     * ③ 该列不在上传模板里时不校验（idx 中无该标签），避免用户用不含该列的精简模板导入被卡死
     * ④ 必填 = 自定义字段自身 is_required=1 或 该角色在字段权限里叠加的必填。
     */
    private boolean validateRequiredCustomFields(Map<String, Integer> idx, Map<String, String> cells,
                                                 List<com.erp.customfield.entity.CustomField> fields,
                                                 int rowIndex, ImportResultDTO result,
                                                 Set<String> roleRequiredLabels) {
        if (fields == null || fields.isEmpty() || cells == null) return true;
        boolean ok = true;
        for (com.erp.customfield.entity.CustomField f : fields) {
            if ("formula".equals(f.getFieldType())) continue;
            // 必填口径 = 自定义字段自身 is_required=1 或 该角色在字段权限里叠加的必填
            boolean required = (f.getIsRequired() != null && f.getIsRequired() == 1)
                    || (roleRequiredLabels != null && roleRequiredLabels.contains(f.getFieldLabel()));
            if (!required) continue;
            if (idx != null && !idx.containsKey(f.getFieldLabel())) continue;
            String v = cells.get(f.getFieldLabel());
            if (v == null || v.isEmpty()) {
                result.addError(rowIndex, f.getFieldLabel(), "不能为空");
                ok = false;
            }
        }
        return ok;
    }

    /** 单据导入用：校验一单下「所有明细行」的自定义字段必填（逐行记错误，任一缺失即返回 false） */
    private boolean validateRequiredCustomItemFields(Map<String, Integer> idx, List<Map<String, String>> itemCells,
                                                     List<com.erp.customfield.entity.CustomField> fields,
                                                     int rowIndex, ImportResultDTO result,
                                                     Set<String> roleRequiredLabels) {
        if (fields == null || fields.isEmpty() || itemCells == null || itemCells.isEmpty()) return true;
        boolean ok = true;
        for (Map<String, String> cells : itemCells) {
            if (!validateRequiredCustomFields(idx, cells, fields, rowIndex, result, roleRequiredLabels)) ok = false;
        }
        return ok;
    }

    /** 保存销售单据（表头/明细）自定义字段值：无值/无ID时跳过，失败仅告警不阻断主流程 */
    private void saveSaleCustomFields(String entityType, Long entityId, Map<String, String> cells,
                                      List<com.erp.customfield.entity.CustomField> fields) {
        if (entityId == null || fields == null || fields.isEmpty()) return;
        Map<String, Object> vals = extractCustomValsFromCells(cells, fields);
        if (vals.isEmpty()) return;
        try {
            customFieldService.saveFieldValues(entityType, entityId, vals);
        } catch (Exception ex) {
            log.warn("销售单据自定义字段保存失败 entity={} id={}: {}", entityType, entityId, ex.getMessage());
        }
    }

    /** 反射取实体主键ID（@TableId 注解字段），用于 insert 后回填自定义字段值 */
    private Long extractEntityId(Object entity) {
        for (Field field : entity.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(com.baomidou.mybatisplus.annotation.TableId.class)) {
                try {
                    field.setAccessible(true);
                    Object v = field.get(entity);
                    return v instanceof Number ? ((Number) v).longValue() : null;
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return null;
    }

    /** 找实体上 label 以"编码"结尾的导出字段（与模板表头必填标记同源，保证口径一致） */
    private Field findCodeField(Class<?> entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            ExportField annotation = field.getAnnotation(ExportField.class);
            if (annotation != null && !annotation.hidden() && annotation.label().endsWith("编码")) {
                return field;
            }
        }
        return null;
    }

    /**
     * 为编码为空的导入行自动生成编码。
     * 物料走"分类编码规则优先，回退通用流水号"（与 MaterialServiceImpl.save 保持一致）；
     * 其余实体直接按 docType 流水号生成。生成失败仅告警不阻断导入（保留原值入库由唯一约束兜底）。
     */
    private void fillAutoCode(String entityName, Object entity) {
        try {
            Field codeField = findCodeField(entity.getClass());
            if (codeField == null) return;
            codeField.setAccessible(true);
            Object current = codeField.get(entity);
            if (current != null && !current.toString().isBlank()) return;

            String docType = AUTO_CODE_DOC_TYPE.get(entityName);
            String code = null;
            if ("material".equals(entityName)) {
                Long categoryId = null;
                try {
                    Field categoryField = entity.getClass().getDeclaredField("categoryId");
                    categoryField.setAccessible(true);
                    categoryId = (Long) categoryField.get(entity);
                } catch (Exception ignore) {
                    // 物料实体无 categoryId 或未填，走通用流水号
                }
                if (categoryId != null) {
                    try {
                        code = materialCodeRuleService.generateCode(categoryId);
                    } catch (Exception e) {
                        log.warn("分类编码规则生成失败，回退通用规则: {}", e.getMessage());
                    }
                }
            }
            if (code == null || code.isBlank()) {
                // 带业务表防重抬底：导入直插 mapper 不经各 Service，若此前手动录码/停用规则期间已有存量编码导致计数落后，直接生成会重复
                code = generateImportCode(docType, entityName, codeField);
            }
            if (code != null && !code.isBlank()) {
                codeField.set(entity, code);
            }
        } catch (Exception e) {
            log.warn("导入自动生成编码失败: {}", e.getMessage());
        }
    }

    /**
     * 导入行自动生成编码。
     * ① 业务表防重抬底：先按"当前编号段已用最大编码"把自动编号计数抬到不低于业务表真实水平，杜绝与存量编码重复；
     * ② 物料口径额外启用"删除即释放 + 补最小空洞"：以物料表实况取最小未用流水，删除的编码会被自动复用。
     */
    private String generateImportCode(String docType, String entityName, Field codeField) {
        String codeCol = camelToSnake(codeField.getName());
        // 补最小空洞探测：物料保持既有实现（额外覆盖"无前缀纯数字"口径）；
        // 其余基础资料主档按同一口径接入；单据类传 null，行为与改动前完全一致
        java.util.function.Function<String, List<String>> segmentQuery;
        if ("material".equals(entityName)) {
            segmentQuery = materialMapper::selectCodesByPrefix;
        } else if (BASEDATA_SEGMENT_ENTITIES.contains(entityName)) {
            segmentQuery = com.erp.serialno.support.SegmentCodeProbe.of(getMapper(entityName), codeCol);
        } else {
            segmentQuery = null;
        }
        return serialNoConfigService.generateSerialNo(docType, prefix -> {
            if (prefix == null || prefix.isBlank()) {
                return null;
            }
            try {
                BaseMapper<?> mapper = getMapper(entityName);
                if (mapper == null) {
                    return null;
                }
                QueryWrapper<Object> qw = new QueryWrapper<>();
                qw.likeRight(codeCol, prefix).orderByDesc(codeCol).last("LIMIT 1");
                List<Map<String, Object>> rows = ((BaseMapper<Object>) mapper).selectMaps(qw);
                if (rows == null || rows.isEmpty()) {
                    return null;
                }
                Object val = rows.get(0).get(codeCol);
                if (val == null) {
                    return null;
                }
                String lastCode = String.valueOf(val);
                return prefix + (Long.parseLong(lastCode.substring(prefix.length())) + 1);
            } catch (Exception e) {
                return null;
            }
        }, segmentQuery);
    }

    /**
     * 物料分类导入专用：通过 parentCode 解析 parentId
     * 1. 如果 parentId 已有值，直接跳过
     * 2. 如果 parentCode 有值，查数据库获取对应的 categoryId
     */
    private void resolveCategoryParent(Object entity, int rowIndex, ImportResultDTO result) {
        try {
            var clazz = entity.getClass();
            java.lang.reflect.Field parentIdField = clazz.getDeclaredField("parentId");
            java.lang.reflect.Field parentCodeField = clazz.getDeclaredField("parentCode");
            parentIdField.setAccessible(true);
            parentCodeField.setAccessible(true);

            Long parentId = (Long) parentIdField.get(entity);
            if (parentId != null && parentId > 0) {
                return;
            }

            String parentCode = (String) parentCodeField.get(entity);
            if (parentCode == null || parentCode.trim().isEmpty()) {
                return;
            }

            parentCode = parentCode.trim().replace('\\', '/');
            Long resolved;
            if (parentCode.contains("/")) {
                // 路径写法（如 E/01）：逐级消歧解析
                resolved = dbResolveCategoryPath(parentCode);
                if (resolved == null) {
                    return; // 父级可能在本批次内，留待保存阶段解析
                }
            } else {
                // 纯编码写法：仅当数据库全局唯一时直接解析
                var hits = materialCategoryMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.basedata.entity.MaterialCategory>()
                        .eq("category_code", parentCode));
                if (hits.size() == 1) {
                    resolved = hits.get(0).getCategoryId();
                } else {
                    // 0个：父级可能在本批次内；多个：歧义，保存阶段结合批次索引再解析
                    return;
                }
            }
            parentIdField.set(entity, resolved);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.warn("解析物料分类父级失败: {}", e.getMessage());
        }
    }

    /**
     * 按路径逐级解析分类（每级在其父级子节点中查找，天然消歧）。
     * 例：E/01 → 先在顶级找 E，再在 E 的子级找 01。
     * @return 目标分类ID，任一段找不到返回 null
     */
    private Long dbResolveCategoryPath(String path) {
        String[] segs = path.split("/");
        Long parent = null; // null 表示根级
        com.erp.basedata.entity.MaterialCategory node = null;
        for (String seg : segs) {
            String s = seg.trim();
            if (s.isEmpty()) {
                return null;
            }
            var qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.basedata.entity.MaterialCategory>()
                    .eq("category_code", s);
            if (parent == null) {
                qw.and(w -> w.isNull("parent_id").or().eq("parent_id", 0));
            } else {
                qw.eq("parent_id", parent);
            }
            node = materialCategoryMapper.selectOne(qw.last("LIMIT 1"));
            if (node == null) {
                return null;
            }
            parent = node.getCategoryId();
        }
        return node == null ? null : node.getCategoryId();
    }

    /** 查库构建分类完整路径（编码用 / 连接，如 E/01/101） */
    private String dbCategoryPathOf(Long categoryId) {
        java.util.List<String> chain = new java.util.ArrayList<>();
        Long cur = categoryId;
        int guard = 0;
        while (cur != null && cur > 0 && guard++ < 20) {
            com.erp.basedata.entity.MaterialCategory c = materialCategoryMapper.selectById(cur);
            if (c == null) {
                break;
            }
            chain.add(0, c.getCategoryCode());
            cur = c.getParentId();
        }
        return String.join("/", chain);
    }

    /**
     * 物料分类专用保存：分轮保存（先父后子）。
     * 分类编码为「本位段」（如 E / 01 / 101），同一编码可挂在不同父级下，
     * 因此用「路径 → id」索引消歧（E、E/01、E/01/101）；
     * 纯编码引用仅在其全局（库+批次）唯一时生效。
     * 成功数在保存成功后计入 result；因父级无法解析而未保存的行记为失败。
     */
    private void saveMaterialCategories(List<Object> entities, ImportResultDTO result) {
        Map<String, Long> batchPathToId = new HashMap<>();  // 本批次已保存：路径 -> id
        Map<Long, String> batchIdToPath = new HashMap<>();  // 本批次已保存：id -> 路径
        Map<String, Long> batchCodeToId = new HashMap<>();  // 本批次：编码 -> id（重复时置 -1 标记歧义）
        List<Object> remaining = new ArrayList<>(entities);
        int maxRounds = 10;
        int round = 0;

        while (!remaining.isEmpty() && round < maxRounds) {
            round++;

            // 步骤1：收集本轮可保存的实体（父级为空=顶级，或父级已解析，或 parentCode 可解析）
            List<Object> thisRound = new ArrayList<>();
            Iterator<Object> it = remaining.iterator();

            while (it.hasNext()) {
                Object entity = it.next();
                try {
                    var clazz = entity.getClass();
                    java.lang.reflect.Field parentIdField = clazz.getDeclaredField("parentId");
                    java.lang.reflect.Field parentCodeField = clazz.getDeclaredField("parentCode");
                    parentIdField.setAccessible(true);
                    parentCodeField.setAccessible(true);

                    Long parentId = (Long) parentIdField.get(entity);
                    String parentCode = (String) parentCodeField.get(entity);

                    if (parentId != null && parentId > 0) {
                        // 父级已解析（数据库中已存在）
                        thisRound.add(entity);
                        it.remove();
                    } else if (parentCode == null || parentCode.trim().isEmpty()) {
                        // 顶级分类
                        thisRound.add(entity);
                        it.remove();
                    } else {
                        // 尝试解析 parentCode：路径（批次索引→数据库）；纯编码（库唯一→批次唯一）
                        Long resolved = resolveCategoryRef(parentCode.trim(), batchPathToId, batchCodeToId);
                        if (resolved != null) {
                            parentIdField.set(entity, resolved);
                            thisRound.add(entity);
                            it.remove();
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析物料分类失败: {}", e.getMessage());
                }
            }

            if (thisRound.isEmpty()) {
                log.warn("存在无法保存的物料分类，可能存在无效的父级依赖");
                break;
            }

            // 步骤2：保存本轮实体并登记路径索引
            for (Object entity : thisRound) {
                try {
                    var clazz = entity.getClass();
                    java.lang.reflect.Field idField = clazz.getDeclaredField("categoryId");
                    java.lang.reflect.Field codeField = clazz.getDeclaredField("categoryCode");
                    java.lang.reflect.Field pidField = clazz.getDeclaredField("parentId");
                    idField.setAccessible(true);
                    codeField.setAccessible(true);
                    pidField.setAccessible(true);

                    // 分类自动编码开关（BD_MATERIAL_CATEGORY.auto_generate）打开时空编码自动生成；默认关闭保持手填
                    if (isAutoCodeEnabled("materialCategory")) {
                        fillAutoCode("materialCategory", entity);
                    }

                    materialCategoryMapper.insert((com.erp.basedata.entity.MaterialCategory) entity);

                    Long newId = (Long) idField.get(entity);
                    String code = (String) codeField.get(entity);
                    Long pid = (Long) pidField.get(entity);

                    String parentPath = null;
                    if (pid != null && pid > 0) {
                        parentPath = batchIdToPath.containsKey(pid) ? batchIdToPath.get(pid) : dbCategoryPathOf(pid);
                    }
                    String myPath = (parentPath == null || parentPath.isBlank()) ? code : parentPath + "/" + code;
                    batchPathToId.put(myPath, newId);
                    batchIdToPath.put(newId, myPath);
                    batchCodeToId.merge(code, newId, (a, b) -> a.equals(b) ? a : -1L);

                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception e) {
                    log.warn("保存物料分类失败: {}", e.getMessage());
                    result.setFailCount(result.getFailCount() + 1);
                    result.addError(0, "", "保存物料分类失败: " + e.getMessage());
                }
            }
        }

        // 因父级依赖未保存的行：记为失败并提示
        for (Object entity : remaining) {
            try {
                var clazz = entity.getClass();
                java.lang.reflect.Field codeField = clazz.getDeclaredField("categoryCode");
                java.lang.reflect.Field parentCodeField = clazz.getDeclaredField("parentCode");
                codeField.setAccessible(true);
                parentCodeField.setAccessible(true);
                String code = (String) codeField.get(entity);
                String pc = (String) parentCodeField.get(entity);
                result.setFailCount(result.getFailCount() + 1);
                result.addError(0, "分类编码", "分类 '" + code + "' 的上级引用 '" + pc
                        + "' 无法解析（该编码存在多个匹配时请用路径写法，如 E/01）");
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 解析上级分类引用。支持两种写法：
     * 1. 路径：E/01（优先批次索引，其次数据库逐级解析）
     * 2. 纯编码：E 或 101（仅当数据库+批次内指向唯一节点时生效）
     */
    private Long resolveCategoryRef(String ref, Map<String, Long> batchPathToId, Map<String, Long> batchCodeToId) {
        String r = ref.replace('\\', '/').trim();
        if (r.contains("/")) {
            Long batchId = batchPathToId.get(r);
            if (batchId != null) {
                return batchId;
            }
            return dbResolveCategoryPath(r);
        }
        var dbHits = materialCategoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.basedata.entity.MaterialCategory>()
                        .eq("category_code", r));
        if (dbHits.size() == 1) {
            return dbHits.get(0).getCategoryId();
        }
        if (dbHits.size() > 1) {
            return null; // 数据库内歧义
        }
        Long batchId = batchCodeToId.get(r);
        return (batchId != null && batchId > 0) ? batchId : null;
    }

    private List<String> convertToStringList(List<Object> list) {
        return list.stream()
                .map(o -> o != null ? o.toString() : "")
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<List<Object>> convertExcelRowsToList(List<Object> rows) {
        List<List<Object>> result = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map) {
                Map<Integer, Object> map = (Map<Integer, Object>) row;
                if (map.isEmpty()) {
                    result.add(new ArrayList<>());
                    continue;
                }
                int maxIndex = map.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                List<Object> list = new ArrayList<>(maxIndex + 1);
                for (int i = 0; i <= maxIndex; i++) {
                    list.add(map.get(i));
                }
                result.add(list);
            } else if (row instanceof List) {
                result.add((List<Object>) row);
            } else {
                result.add(new ArrayList<>());
            }
        }
        return result;
    }

    private List<List<String>> buildSingleLevelHeaders(List<String> labels) {
        return labels.stream()
                .map(Collections::singletonList)
                .toList();
    }

    private void setResponseHeader(HttpServletResponse response, String fileName) {
        try {
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-disposition",
                    "attachment;filename*=UTF-8''" + encodedFileName);
        } catch (Exception e) {
            log.error("设置响应头失败", e);
        }
    }

    /** 模板文件名 = 登记表中文名 + "导入模板.xlsx"（原先是一张 60 行手写 switch） */
    private String getTemplateName(String entityName) {
        String name = com.erp.export.registry.BillExcelRegistry.nameOf(entityName);
        return name == null ? "导入模板.xlsx" : name + "导入模板.xlsx";
    }

    /** 导出文件名 = 登记表中文名 + "数据_yyyyMMdd.xlsx"（原先是一张 60 行手写 switch） */
    private String getExportFileName(String entityName) {
        String name = com.erp.export.registry.BillExcelRegistry.nameOf(entityName);
        String baseName = name == null ? "数据" : name + "数据";
        return baseName + "_" + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
    }

    // ========== BOM统一导入（同时支持一级和多级） ==========

    /** 统一模板18列：左半BOM主属性（表头继承） + 右半子件明细（每行一条）；带*为必填列 */
    private static final List<String> BOM_UNIFIED_HEADERS = Arrays.asList(
            "单据日期",                 // 空=今天；同BOM仅第一行填
            "母件编号*",                // 必填；识别同一BOM的主键（LOCKED）
            "母件名称",                 // 辅助，空=自动带出
            "BOM分类",                  // 标准/工程/生产/成本/委外，空=标准BOM
            "版本号",                   // 空=留空
            "数量",                     // 母件产出批量，空=1
            "层级",                     // 空=1（直接子件=简单一级BOM）；>1=多级配合父件编码
            "父件编码(多级用)",          // 空=继承左侧母件编号；层级>1时必填直属父件
            "母件分类编码",              // 缺失物料同步时用于定母件分类（自动生成物料编码需用到分类编码）
            "子件编码*",                // 必填（LOCKED）
            "子件名称",                 // 空=自动带出
            "子件规格",                 // 空=自动带出
            "用量*",                    // 必填，正数（LOCKED）
            "损耗率(%)",                // 空=0
            "工序",                     // 空=不设
            "关键件",                   // 是/否/1/0；空=否
            "行备注",                   // bom_item.remark
            "子件分类编码"               // 缺失物料同步时用于定子件分类
    );

    /** 系统锁定必填列：即使前端不勾选也要强制进模板 */
    private static final java.util.Set<String> BOM_LOCKED_COLUMNS = new HashSet<>(
            Arrays.asList("母件编号*", "子件编码*", "用量*"));

    /** 字段可选列分组：mainProps = 左主属性（index 0-7）；itemProps = 右子件明细（index 8-15）  */
    private static final List<String> BOM_MAIN_GROUP_LABELS = Arrays.asList(
            "单据日期","母件编号*","母件名称","BOM分类","版本号","数量","层级","父件编码(多级用)","母件分类编码");
    private static final List<String> BOM_ITEM_GROUP_LABELS = Arrays.asList(
            "子件编码*","子件名称","子件规格","用量*","损耗率(%)","工序","关键件","行备注","子件分类编码");

    /** 旧11列兼容模板（纯树形，根节点行声明母件） */
    private static final List<String> BOM_LEGACY_HEADERS = Arrays.asList(
            "层级","父件编码","父件名称","子件编码","子件名称","子件规格","单位","用量","损耗率(%)","工序","备注"
    );

    @Override
    public Map<String, Object> getBomTemplateFields() {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (String label : BOM_MAIN_GROUP_LABELS) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("label", label);
            c.put("group", "mainProps");
            c.put("locked", BOM_LOCKED_COLUMNS.contains(label));
            c.put("defaultChecked", true);
            columns.add(c);
        }
        for (String label : BOM_ITEM_GROUP_LABELS) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("label", label);
            c.put("group", "itemProps");
            c.put("locked", BOM_LOCKED_COLUMNS.contains(label));
            c.put("defaultChecked", true);
            columns.add(c);
        }
        // 扩展可选列（不在默认18列中，defaultChecked=false），白名单在 exportBomTemplate 内同样接受
        addOptionalColumn(columns, "mainProps", "单据状态", false);  // 表头扩展：草稿/已生效等
        addOptionalColumn(columns, "mainProps", "是否默认BOM", false);
        addOptionalColumn(columns, "itemProps", "发料仓库", false);
        addOptionalColumn(columns, "itemProps", "是否关键物料", false); // 关键件同义项

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("maxColumns", 30);
        return result;
    }

    private static void addOptionalColumn(List<Map<String, Object>> columns, String group, String label, boolean locked) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("label", label);
        c.put("group", group);
        c.put("locked", locked);
        c.put("defaultChecked", false);
        columns.add(c);
    }

    /** 允许出现在模板中的全部列标签（含扩展列）——白名单；未在此集合的传参静默忽略 */
    private static final java.util.Set<String> BOM_COLUMN_WHITELIST;
    static {
        java.util.Set<String> wl = new HashSet<>(BOM_UNIFIED_HEADERS);
        wl.add("单据状态"); wl.add("是否默认BOM");
        wl.add("发料仓库"); wl.add("是否关键物料");
        BOM_COLUMN_WHITELIST = Collections.unmodifiableSet(wl);
    }

    @Override
    public void exportBomTemplate(List<String> fields, HttpServletResponse response) {
        // 1) 构建实际导出列 headers（按 fields 过滤 + 白名单校验 + 锁定列兜底）
        List<String> actualHeaders;
        if (fields == null || fields.isEmpty()) {
            actualHeaders = new ArrayList<>(BOM_UNIFIED_HEADERS);
        } else {
            // 按传参顺序去重+白名单过滤
            List<String> userCols = new ArrayList<>();
            java.util.Set<String> seen = new HashSet<>();
            for (String f : fields) {
                if (f == null) continue;
                String clean = f.trim();
                if (clean.isEmpty()) continue;
                if (!BOM_COLUMN_WHITELIST.contains(clean)) continue; // 不在白名单静默忽略（安全）
                if (seen.add(clean)) userCols.add(clean);
            }
            // 锁定列强制补进去（若用户取消勾选了锁定列）
            for (String locked : BOM_LOCKED_COLUMNS) {
                if (!seen.contains(locked)) userCols.add(locked);
            }
            // 超过最大列数截断：避免恶意大列
            if (userCols.size() > 30) userCols = new ArrayList<>(userCols.subList(0, 30));
            actualHeaders = userCols;
        }

        // 2) 构建表头→索引映射：18列默认示例数据在 BOM_UNIFIED_HEADERS 中的位置 → actualHeaders 中的新位置
        //    扩展列（不在18列中的）demo值写空字符串
        Map<String, Integer> unifiedIdx = new HashMap<>();
        for (int i = 0; i < BOM_UNIFIED_HEADERS.size(); i++) {
            unifiedIdx.put(BOM_UNIFIED_HEADERS.get(i), i);
        }
        List<List<String>> slicedDemo = new ArrayList<>();
        for (List<String> row : buildBomUnifiedDemoData()) {
            List<String> newRow = new ArrayList<>(actualHeaders.size());
            for (String h : actualHeaders) {
                Integer oldIdx = unifiedIdx.get(h);
                if (oldIdx != null && oldIdx < row.size()) {
                    newRow.add(row.get(oldIdx));
                } else {
                    newRow.add(""); // 扩展列：demo 填空，等用户自填
                }
            }
            slicedDemo.add(newRow);
        }

        String fileName = "BOM导入模板_" + (fields == null || fields.isEmpty() ? "默认18列" : "自定义" + actualHeaders.size() + "列")
                + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
        setResponseHeader(response, fileName);

        try {
            EasyExcel.write(response.getOutputStream())
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("BOM导入")
                    .head(buildSingleLevelHeaders(actualHeaders))
                    .doWrite(slicedDemo);
        } catch (IOException e) {
            log.error("BOM模板导出失败(columns={})", actualHeaders.size(), e);
            throw new RuntimeException("BOM模板导出失败", e);
        }
    }

    /** 默认18列的完整demo数据集（位置与 BOM_UNIFIED_HEADERS 一一对应，18个元素） */
    private List<List<String>> buildBomUnifiedDemoData() {
        List<List<String>> demoData = new ArrayList<>();
        // 列序：单据日期,母件编号*,母件名称,BOM分类,版本号,数量,层级,父件编码(多级用),母件分类编码,
        //       子件编码*,子件名称,子件规格,用量*,损耗率(%),工序,关键件,行备注,子件分类编码
        // ===== 示例1：一级BOM简单写法（层级/父件编码两列留空，和销售订单一样）=====
        demoData.add(Arrays.asList(
                "2026-09-01", "FG-001", "成品A", "标准BOM", "V1.0", "1",
                "", "", "01",
                "SFG-001", "半成品1", "半成品规格",
                "2", "0", "", "否", "一级BOM简单写法：表头列这行填全，下面同母件的行表头留空自动继承",
                "01"
        ));
        demoData.add(Arrays.asList(
                "", "", "", "", "", "",
                "", "", "",
                "RM-001", "原材料1", "原材料规格",
                "0.5", "1", "工序1", "否", "（继承FG-001）",
                "01"
        ));
        demoData.add(Arrays.asList(
                "", "", "", "", "", "",
                "", "", "",
                "RM-002", "原材料2", "",
                "3", "0", "", "是", "（继承FG-001）关键件示例",
                "01"
        ));
        // ===== 示例2：多级BOM写法（层级+父件编码配合使用）==========
        demoData.add(Arrays.asList(
                "2026-09-02", "FG-999", "多级成品", "生产BOM", "V2.0", "100",
                "1", "FG-999", "01",
                "SFG-900", "半成品900", "",
                "1", "0", "", "否", "多级写法：层级1为FG-999的直接子件；层级>1写直属父件编码",
                "01"
        ));
        demoData.add(Arrays.asList(
                "", "", "", "", "", "",
                "2", "SFG-900", "",
                "RM-900", "原材料900", "",
                "5", "0.5", "", "否", "",
                "01"
        ));
        demoData.add(Arrays.asList(
                "", "", "", "", "", "",
                "2", "SFG-900", "",
                "RM-901", "原材料901", "",
                "2", "0", "", "否", "",
                "01"
        ));
        demoData.add(Arrays.asList(
                "", "", "", "", "", "",
                "1", "FG-999", "",
                "PACK-001", "包装盒", "",
                "1", "0", "", "否", "",
                "01"
        ));
        return demoData;
    }

    @Override
    public void exportBomLegacyTemplate(HttpServletResponse response) {
        String fileName = "BOM导入模板_旧版兼容11列_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
        setResponseHeader(response, fileName);

        List<List<String>> demo = new ArrayList<>();
        // 根节点（第1行：level=1，父件编码=子件编码=母件，用于声明BOM母件）
        demo.add(Arrays.asList("1","FG-001","成品A","FG-001","成品A","成品规格","个","1","0","","根节点：父件编码=子件编码=母件，用于声明BOM表头母件，本身不是子件会被跳过"));
        // level=1的直接子件（父件编码=母件）
        demo.add(Arrays.asList("1","FG-001","","SFG-001","半成品1","半成品规格","","2","0","","同母件的直属子件：层级=1，父件编码写母件编号"));
        demo.add(Arrays.asList("1","FG-001","","RM-001","原材料1","原材料规格","","0.5","1","工序1","","关键件标记不支持，导入后在BOM列表手动设置"));
        // level=2的多级子件（SFG-001下面挂原材料）
        demo.add(Arrays.asList("2","SFG-001","","RM-900","原材料900","","","5","0.5","","多级：父件编码写上一层父件SFG-001，层级=2"));
        // 另一根BOM
        demo.add(Arrays.asList("1","FG-999","多级成品","FG-999","多级成品","","个","1","0","",""));
        demo.add(Arrays.asList("1","FG-999","","PACK-001","包装盒","","","1","0","",""));

        try {
            EasyExcel.write(response.getOutputStream())
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("BOM导入(旧版)")
                    .head(buildSingleLevelHeaders(BOM_LEGACY_HEADERS))
                    .doWrite(demo);
        } catch (IOException e) {
            log.error("BOM旧版兼容模板导出失败", e);
            throw new RuntimeException("BOM旧版兼容模板导出失败", e);
        }
    }

    @Override
    public void exportBomMultiLevel(Long bomId, HttpServletResponse response) {
        Bom bom = bomMapper.selectById(bomId);
        if (bom == null) {
            throw new IllegalArgumentException("BOM不存在: " + bomId);
        }
        // BOM主属性值，所有行统一共享（左半列）
        final String masterBomDate = bom.getBomDate() != null ? bom.getBomDate().toString() : "";
        final String masterMaterialCode = bom.getMaterialCode() != null ? bom.getMaterialCode() : "";
        final String masterMaterialName = bom.getMaterialName() != null ? bom.getMaterialName() : "";
        final String masterBomCategory = bom.getBomCategory() != null ? bom.getBomCategory() : "";
        final String masterVersion = bom.getBomVersion() != null ? bom.getBomVersion() : "";
        final String masterQuantity = bom.getQuantity() != null ? bom.getQuantity().toPlainString() : "1";

        List<Map<String, Object>> dataList = new ArrayList<>();
        expandBomForExportUnified(bom, masterBomDate, masterMaterialCode, masterMaterialName,
                masterBomCategory, masterVersion, masterQuantity,
                bom.getMaterialCode(), bom.getMaterialName(), bom.getSpecification(), 1, dataList);

        String fileName = "BOM结构_" + bom.getMaterialCode() + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
        setResponseHeader(response, fileName);

        try {
            EasyExcel.write(response.getOutputStream())
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet("BOM")
                    .head(buildSingleLevelHeaders(BOM_UNIFIED_HEADERS))
                    // 位置行写出：规避 easyexcel 3.3.4 + POI 5.x 下「动态表头 + Map 数据」写不出值的缺陷
                    .doWrite(toPositionalRows(dataList, BOM_UNIFIED_HEADERS));
        } catch (IOException e) {
            log.error("BOM导出失败", e);
            throw new RuntimeException("BOM导出失败", e);
        }
    }

    /** 统一18列格式的BOM树展开：每一行左半共享BOM主属性，右半写对应层级的子件 */
    private void expandBomForExportUnified(Bom rootBom,
                                           String mDate, String mCode, String mName, String mCat, String mVer, String mQty,
                                           String parentMaterialCode, String parentMaterialName, String parentSpec,
                                           int level, List<Map<String, Object>> dataList) {
        // 取当前层父件对应的BOM（多级时找子件自己的主BOM）
        Material parentMaterial = materialMapper.selectOne(
                new QueryWrapper<Material>().eq("material_code", parentMaterialCode).last("LIMIT 1"));
        if (parentMaterial == null) {
            return;
        }
        Bom bom = bomMapper.selectOne(new QueryWrapper<Bom>()
                .eq("material_id", parentMaterial.getMaterialId())
                .eq("is_primary", (short) 1)
                .last("LIMIT 1"));
        if (bom == null) {
            return;
        }
        List<BomItem> bomItems = bomItemMapper.selectList(
                new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()).orderByAsc("line_no"));
        for (BomItem item : bomItems) {
            Material childMaterial = materialMapper.selectById(item.getMaterialId());
            if (childMaterial == null) continue;

            String unitName = "";
            if (childMaterial.getBaseUnitId() != null) {
                com.erp.basedata.entity.UnitOfMeasure unit = unitOfMeasureMapper.selectById(childMaterial.getBaseUnitId());
                if (unit != null) unitName = unit.getUnitName();
            }

            Map<String, Object> row = new LinkedHashMap<>();
            // 左半：BOM主属性（仅根级填真实值，多级下层填空=继承表头写法规范，但这里为直观显示还是全部填，导入时有空继承非空覆盖）
            row.put("单据日期", mDate);
            row.put("母件编号*", mCode);
            row.put("母件名称", mName);
            row.put("BOM分类", mCat);
            row.put("版本号", mVer);
            row.put("数量", mQty);
            // 右半：子件
            row.put("层级", level);
            row.put("父件编码(多级用)", parentMaterialCode);
            row.put("子件编码*", item.getMaterialCode());
            row.put("子件名称", item.getMaterialName());
            row.put("子件规格", item.getSpecification() != null ? item.getSpecification() : childMaterial.getModel());
            row.put("用量*", item.getQuantity());
            row.put("损耗率(%)", item.getScrapRate() != null ? item.getScrapRate() : BigDecimal.ZERO);
            row.put("工序", item.getProcessNo() != null ? item.getProcessNo() : "");
            String isKey = item.getIsKeyPart() != null && item.getIsKeyPart() == 1 ? "是" : "否";
            row.put("关键件", isKey);
            row.put("行备注", item.getRemark() != null ? item.getRemark() : "");
            // 单位：统一模板没有"单位"列（用户说列太多精简掉了），自动带出，写在行备注末尾方便查看但不单独列
            dataList.add(row);
            expandBomForExportUnified(rootBom, mDate, mCode, mName, mCat, mVer, mQty,
                    item.getMaterialCode(), item.getMaterialName(), item.getSpecification(),
                    level + 1, dataList);
        }
    }

    /** BOM主属性继承上下文：按母件编号分组，记录"从第一行非空继承"的各字段值。作为方法参数避免并发串单。 */
    @lombok.Data
    private static class BomParseContext {
        /** 当前行继承到的单据日期字符串(yyyy-MM-dd或空) */
        String bomDate = "";
        /** 当前母件编号（聚合主键） */
        String materialCode = "";
        String materialName = "";
        String bomCategory = "";
        String bomVersion = "";
        String quantity = "";
        /** 母件分类编码（左半表头继承；缺失物料同步时用于定母件分类） */
        String parentCategoryCode = "";
        /** 上一行的materialCode，用于"空则继承"判断；和materialCode通常相同 */
        String lastMaterialCode = "";
        /** true = 当前是旧格式11列模板（缺"母件编号*"列），需降级兼容 */
        boolean legacyMode = false;
        /** 行N与行M主属性冲突消息，用于导入结果告警 */
        List<String> conflictMessages = new ArrayList<>();
    }

    /** 旧多级导入入口（兼容旧代码调用）：统一走confirmBomImport，默认不自动建物料+冲突策略NEW_VERSION */
    @Override
    public ImportResultDTO importBomMultiLevel(MultipartFile file) {
        return confirmBomImport(file, false, "NEW_VERSION");
    }

    /**
     * 统一行解析：新/旧模板→BomRowData；同时更新BomParseContext（主属性继承）。
     * 返回 null = 该行应跳过（空行或无法解析）。
     * materialCache 用于编码清洗。
     */
    private BomImportCheckDTO.BomRowData parseUnifiedRow(List<Object> row,
                                                         Map<String, Integer> h,
                                                         BomParseContext ctx,
                                                         int rowIndex,
                                                         Map<String, Material> materialCache,
                                                         ImportResultDTO result) {
        if (row == null || isRowEmpty(row)) return null;

        BomImportCheckDTO.BomRowData d = new BomImportCheckDTO.BomRowData();
        d.setRowIndex(rowIndex);

        // ======= 判定是否旧格式：缺少任何"母件编号*"或"母件编号"列 =======
        Integer mCodeIdx = firstNonNullIdx(h, "母件编号*", "母件编号");
        if (!ctx.legacyMode) {
            ctx.legacyMode = (mCodeIdx == null); // 没母件编号列=旧11列模板
        }

        if (ctx.legacyMode) {
            return parseLegacyRowToUnified(row, h, ctx, rowIndex, materialCache, result, d);
        }

        // ========== 新18列统一模板解析 ==========
        // ---- 左半主属性：空则继承上下文 ----
        String bomDateV = cell(row, h, "单据日期");
        String mcV     = cleanCode(cell(row, h, "母件编号*", "母件编号"));
        String mnV     = cell(row, h, "母件名称");
        String catV    = cell(row, h, "BOM分类", "Bom分类", "bom分类");
        String verV    = cell(row, h, "版本号", "版本");
        String qtyV    = cell(row, h, "数量");
        String pcV     = cell(row, h, "母件分类编码");   // 缺失物料同步时用于定母件分类

        // 左半表头继承规则：materialCode变（非空且≠last）→ 重置上下文；否则空字段从上文继承
        if (!mcV.isEmpty() && !mcV.equals(ctx.lastMaterialCode)) {
            // 母件变了 → 从头继承（非空新值覆盖，空字段保留当前上下文，但materialCode是新的必须有值）
            ctx.bomDate      = !bomDateV.isEmpty() ? bomDateV : "";   // 新母件第一行的日期必须写，否则后面继承会是上个母件的
            ctx.materialCode = mcV;
            ctx.materialName = mnV;            // 允许空，后面从物料带
            ctx.bomCategory  = catV;
            ctx.bomVersion   = verV;
            ctx.quantity     = qtyV;
            ctx.parentCategoryCode = pcV;
            ctx.lastMaterialCode = mcV;
        } else {
            // 同母件（或空）→ 非空值局部覆盖，空值不继承保留上下文（即复用前一行的）
            if (!bomDateV.isEmpty()) {
                if (!ctx.bomDate.isEmpty() && !ctx.bomDate.equals(bomDateV)) {
                    ctx.conflictMessages.add("第" + rowIndex + "行[单据日期](" + bomDateV + ")与第一行(" + ctx.bomDate + ")冲突，已取第一行值");
                } else if (ctx.bomDate.isEmpty()) ctx.bomDate = bomDateV;
            }
            if (!mcV.isEmpty()) {
                if (ctx.materialCode.isEmpty()) ctx.materialCode = mcV;
            }
            if (!mnV.isEmpty()) {
                if (ctx.materialName.isEmpty() || !ctx.materialName.equals(mnV)) ctx.materialName = mnV;
            }
            if (!catV.isEmpty()) {
                if (!ctx.bomCategory.isEmpty() && !ctx.bomCategory.equals(catV)) {
                    ctx.conflictMessages.add("第" + rowIndex + "行[BOM分类]冲突，已取第一行值");
                } else if (ctx.bomCategory.isEmpty()) ctx.bomCategory = catV;
            }
            if (!verV.isEmpty()) {
                if (!ctx.bomVersion.isEmpty() && !ctx.bomVersion.equals(verV)) {
                    ctx.conflictMessages.add("第" + rowIndex + "行[版本号]冲突，已取第一行值");
                } else if (ctx.bomVersion.isEmpty()) ctx.bomVersion = verV;
            }
            if (!qtyV.isEmpty()) {
                if (!ctx.quantity.isEmpty() && !ctx.quantity.equals(qtyV)) {
                    ctx.conflictMessages.add("第" + rowIndex + "行[母件数量]冲突，已取第一行值");
                } else if (ctx.quantity.isEmpty()) ctx.quantity = qtyV;
            }
        }
        // 写到返回对象（继承后的值）
        d.setBomDate(ctx.bomDate);
        d.setMaterialCode(ctx.materialCode);
        d.setMaterialName(ctx.materialName);
        d.setBomCategory(ctx.bomCategory);
        d.setBomVersion(ctx.bomVersion);
        d.setQuantity(ctx.quantity);
        d.setBillStatus(cell(row, h, IMPORT_STATUS_LABEL));

        if (d.getMaterialCode() == null || d.getMaterialCode().isEmpty()) {
            result.addError(rowIndex, "母件编号*", "该列第一行必须填写母件编号（表头继承）");
            return null;
        }

        // ---- 右半子件属性 ----
        String levelStr = cell(row, h, "层级");
        int level = 1;
        if (!levelStr.isEmpty()) {
            try { level = Integer.parseInt(levelStr); }
            catch (NumberFormatException e) { level = 1; }
        }
        d.setLevel(level < 1 ? 1 : level);

        String parentCode = cleanCode(cell(row, h, "父件编码(多级用)", "父件编码"));
        if (parentCode.isEmpty()) {
            parentCode = d.getMaterialCode(); // 空=继承左半母件
        }
        d.setParentMaterialCode(parentCode);
        if (d.getLevel() > 1 && (parentCode == null || parentCode.isEmpty())) {
            result.addError(rowIndex, "父件编码(多级用)", "层级>1时必须指定直属父件编码");
        }

        String childCode = cleanCode(cell(row, h, "子件编码*", "子件编码"));
        if (childCode.isEmpty()) {
            result.addError(rowIndex, "子件编码*", "不能为空");
            return null;
        }
        d.setChildMaterialCode(childCode);
        d.setChildMaterialName(cell(row, h, "子件名称"));
        d.setSpecification(cell(row, h, "子件规格"));
        d.setChildCategoryCode(cell(row, h, "子件分类编码")); // 缺失物料同步时用于定子件分类
        d.setUnit(cell(row, h, "单位"));   // 新模板没单位列但兼容老写法或用户手工加

        String qty = cell(row, h, "用量*", "用量");
        if (qty.isEmpty()) {
            result.addError(rowIndex, "用量*", "不能为空");
            return null;
        }
        try {
            BigDecimal q = new BigDecimal(qty);
            if (q.compareTo(BigDecimal.ZERO) <= 0) {
                result.addError(rowIndex, "用量*", "必须大于0");
                return null;
            }
        } catch (NumberFormatException e) {
            result.addError(rowIndex, "用量*", "数字格式错误: " + qty);
            return null;
        }
        d.setQuantityValue(qty);

        String scrap = cell(row, h, "损耗率(%)", "损耗率");
        if (!scrap.isEmpty()) {
            try {
                BigDecimal s = new BigDecimal(scrap);
                if (s.compareTo(BigDecimal.ZERO) < 0) {
                    result.addError(rowIndex, "损耗率(%)", "不能为负数");
                    return null;
                }
            } catch (NumberFormatException e) {
                result.addError(rowIndex, "损耗率(%)", "数字格式错误: " + scrap);
                return null;
            }
        }
        d.setScrapRate(scrap);
        d.setProcessNo(cell(row, h, "工序"));
        d.setKeyPart(cell(row, h, "关键件"));
        d.setItemRemark(cell(row, h, "行备注", "备注", "明细备注"));

        return d;
    }

    /** 旧11列模板（层级/父件编码/父件名称/子件编码/子件名称/子件规格/单位/用量/损耗率(%)/工序/备注）降级兼容解析 */
    private BomImportCheckDTO.BomRowData parseLegacyRowToUnified(List<Object> row,
                                                                 Map<String, Integer> h,
                                                                 BomParseContext ctx,
                                                                 int rowIndex,
                                                                 Map<String, Material> materialCache,
                                                                 ImportResultDTO result,
                                                                 BomImportCheckDTO.BomRowData d) {
        // 左半主属性：旧模板没有这些列，全部取默认（日期空后面填今天；分类空→标准BOM；数量空→1；母件编号从"层级1且父件编码"推导，或从"父件编码=子件编码"的根节点推导）
        String levelStr = cell(row, h, "层级");
        int level = 1;
        if (!levelStr.isEmpty()) {
            try { level = Integer.parseInt(levelStr); } catch (NumberFormatException ignored) {}
        }
        d.setLevel(level < 1 ? 1 : level);

        String parentCode = cleanCode(cell(row, h, "父件编码"));
        String childCode  = cleanCode(cell(row, h, "子件编码"));

        // 从"根节点（父件编码=子件编码且层级=1）"推导母件编号
        if (level == 1 && !parentCode.isEmpty() && parentCode.equals(childCode)) {
            // 根节点行：更新上下文母件编号
            ctx.materialCode = parentCode;
            ctx.lastMaterialCode = parentCode;
            ctx.materialName = cell(row, h, "父件名称");
            ctx.bomDate = "";   // 后面默认填今天
            ctx.bomCategory = "";
            ctx.bomVersion = "";
            ctx.quantity = "";
            // 根节点自己本身不是一条bom_item，是描述BOM母件的，按旧逻辑会跳过（根节点只是约定）
            // 为了和旧逻辑一致：此物料是母件不是子件，跳过（老逻辑把它当level1 item处理，但老逻辑在saveBomMultiLevel里父件编码等于parentCode才能创建BOM，结果根节点其实不会写bom_item因为后续没有children——现在不跳也没问题，但会多一条"自己是自己子件"的错误关联，所以显式跳过）
            result.setSkipCount(result.getSkipCount() == null ? 1 : result.getSkipCount() + 1);
            return null; // 跳根节点行
        }
        // 如果还没有推导出母件，用"level=1行的父件编码"填
        if (ctx.materialCode.isEmpty() && level == 1 && !parentCode.isEmpty()) {
            ctx.materialCode = parentCode;
            ctx.lastMaterialCode = parentCode;
            String parentName = cell(row, h, "父件名称");
            if (!parentName.isEmpty()) ctx.materialName = parentName;
        }

        d.setBomDate(ctx.bomDate);
        d.setMaterialCode(ctx.materialCode);
        d.setMaterialName(ctx.materialName);
        d.setBomCategory(ctx.bomCategory);
        d.setBomVersion(ctx.bomVersion);
        d.setQuantity(ctx.quantity);
        d.setBillStatus(cell(row, h, IMPORT_STATUS_LABEL));

        if (d.getMaterialCode() == null || d.getMaterialCode().isEmpty()) {
            result.addError(rowIndex, "母件编号(根节点)", "旧模板需在第一行(层级1且父件编码=子件编码)声明母件，或在层级=1行填写父件编码（即母件）");
            return null;
        }
        if (level == 1) d.setParentMaterialCode(ctx.materialCode); // 旧逻辑：level=1父件编码本来就是母件
        else d.setParentMaterialCode(parentCode); // level>1保留写的父件

        if (childCode.isEmpty()) {
            result.addError(rowIndex, "子件编码", "不能为空");
            return null;
        }
        d.setChildMaterialCode(childCode);
        d.setChildMaterialName(cell(row, h, "子件名称"));
        d.setSpecification(cell(row, h, "子件规格"));
        d.setUnit(cell(row, h, "单位"));

        String qty = cell(row, h, "用量");
        if (qty.isEmpty()) {
            result.addError(rowIndex, "用量", "不能为空");
            return null;
        }
        try {
            BigDecimal q = new BigDecimal(qty);
            if (q.compareTo(BigDecimal.ZERO) <= 0) {
                result.addError(rowIndex, "用量", "必须大于0");
                return null;
            }
        } catch (NumberFormatException e) {
            result.addError(rowIndex, "用量", "数字格式错误");
            return null;
        }
        d.setQuantityValue(qty);
        String scrap = cell(row, h, "损耗率(%)", "损耗率");
        if (!scrap.isEmpty()) {
            try {
                BigDecimal s = new BigDecimal(scrap);
                if (s.compareTo(BigDecimal.ZERO) < 0) {
                    result.addError(rowIndex, "损耗率(%)", "不能为负");
                    return null;
                }
            } catch (NumberFormatException e) {
                result.addError(rowIndex, "损耗率(%)", "数字格式错误");
                return null;
            }
        }
        d.setScrapRate(scrap);
        d.setProcessNo(cell(row, h, "工序"));
        d.setKeyPart("否");
        d.setItemRemark(cell(row, h, "备注"));
        return d;
    }

    // ---------- 单元格/表头辅助 ----------
    /** 取列值：按候选列名顺序查找第一个存在且非空的cell */
    private String cell(List<Object> row, Map<String, Integer> h, String... names) {
        for (String n : names) {
            Integer idx = h.get(n);
            if (idx != null && idx < row.size()) {
                String s = getCellString(row.get(idx));
                if (s != null && !s.isEmpty()) return s;
            }
        }
        return "";
    }
    /** 候选列名查找第一个非空索引（兼容"列*"/无星号） */
    private Integer firstNonNullIdx(Map<String, Integer> h, String... names) {
        for (String n : names) {
            Integer i = h.get(n);
            if (i != null) return i;
        }
        return null;
    }
    /** 编码清洗：去所有空白+全角空格 */
    private String cleanCode(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "").replace("\u3000", "");
    }

    // ========== BOM导入：预检查、确认导入、保存 ==========

    private Material getMaterialByCode(String materialCode, Map<String, Material> cache) {
        if (materialCode == null || materialCode.isEmpty()) return null;
        Material cached = cache.get(materialCode);
        if (cached != null) return cached;
        Material material = materialMapper.selectOne(
                new QueryWrapper<Material>().eq("material_code", materialCode).last("LIMIT 1"));
        if (material != null) cache.put(materialCode, material);
        return material;
    }
    private String getCellString(Object cellValue) {
        if (cellValue == null) return "";
        return cellValue.toString().trim();
    }

    /** 解析+构建bomRows + 物料缺失检查（通用，被checkBomImport和confirm复用）。结果对象里含冲突、缺失、bomRows。 */
    private BomImportCheckDTO parseAndCheckBomData(List<List<Object>> excelData,
                                                   Map<String, Material> materialCache,
                                                   ImportResultDTO forErrorSink) {
        BomImportCheckDTO result = new BomImportCheckDTO();
        result.setParseSuccess(false);
        result.setTotalRows(0); result.setExistingMaterialCount(0); result.setMissingMaterialCount(0);

        if (excelData == null || excelData.isEmpty()) {
            result.setParseError("文件内容为空");
            return result;
        }
        List<String> headers = convertToStringList(excelData.get(0));
        Map<String, Integer> h = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) h.put(headers.get(i).trim(), i);
        result.setTotalRows(excelData.size() - 1);

        BomParseContext ctx = new BomParseContext();
        Set<String> checkedCodes = new HashSet<>();
        Set<String> parentCodes = new LinkedHashSet<>();     // 左半母件编码集合（去重）
        List<BomImportCheckDTO.MissingMaterial> missingList = new ArrayList<>();
        List<BomImportCheckDTO.BomRowData> bomRows = new ArrayList<>();
        // 物料编码 → 分类编码：母件取解析上下文（表头继承），子件取本行"子件分类编码"
        Map<String, String> parentCatByCode = new HashMap<>();
        Map<String, String> childCatByCode = new HashMap<>();

        ImportResultDTO errSink = forErrorSink != null ? forErrorSink : new ImportResultDTO();

        for (int i = 1; i < excelData.size(); i++) {
            List<Object> row = excelData.get(i);
            if (isRowEmpty(row)) continue;
            BomImportCheckDTO.BomRowData d;
            try {
                d = parseUnifiedRow(row, h, ctx, i + 1, materialCache, errSink);
            } catch (Exception e) {
                errSink.addError(i + 1, "", "数据解析错误: " + e.getMessage());
                continue;
            }
            if (d == null) continue;
            bomRows.add(d);
            parentCodes.add(d.getMaterialCode());
            if (!d.getMaterialCode().isEmpty()) {
                parentCatByCode.put(d.getMaterialCode(), ctx.parentCategoryCode == null ? "" : ctx.parentCategoryCode);
            }
            if (!d.getChildMaterialCode().isEmpty()) {
                childCatByCode.put(d.getChildMaterialCode(), d.getChildCategoryCode() == null ? "" : d.getChildCategoryCode());
            }

            // === 缺失物料检查：先查母件 ===
            String mCode = d.getMaterialCode();
            if (!mCode.isEmpty() && !checkedCodes.contains(mCode)) {
                checkedCodes.add(mCode);
                Material m = getMaterialByCode(mCode, materialCache);
                if (m == null) {
                    BomImportCheckDTO.MissingMaterial mis = new BomImportCheckDTO.MissingMaterial();
                    mis.setMaterialCode(mCode); mis.setMaterialName(d.getMaterialName());
                    mis.setSpecification(""); mis.setRowIndex(d.getRowIndex()); mis.setLevel(0);
                    mis.setMaterialRole("父件"); mis.setSuggestedType("成品"); mis.setSuggestedUnit(d.getUnit());
                    missingList.add(mis);
                }
            }
            // === 缺失物料检查：子件 ===
            String cCode = d.getChildMaterialCode();
            if (!cCode.isEmpty() && !checkedCodes.contains(cCode)) {
                checkedCodes.add(cCode);
                Material cm = getMaterialByCode(cCode, materialCache);
                if (cm == null) {
                    BomImportCheckDTO.MissingMaterial mis = new BomImportCheckDTO.MissingMaterial();
                    mis.setMaterialCode(cCode); mis.setMaterialName(d.getChildMaterialName());
                    mis.setSpecification(d.getSpecification()); mis.setRowIndex(d.getRowIndex());
                    mis.setLevel(d.getLevel()); mis.setMaterialRole("子件");
                    if (d.getLevel() >= 3) mis.setSuggestedType("原材料");
                    else if (d.getLevel() == 2) mis.setSuggestedType("半成品");
                    else mis.setSuggestedType("半成品");
                    mis.setSuggestedUnit(d.getUnit());
                    mis.setCategoryCode(d.getChildCategoryCode());
                    missingList.add(mis);
                }
            }
            // === 缺失物料检查：多级的父件编码（level>1的parent） !== 母件本身（已经查过），也要查存在因为需要知道它是哪个物料id ===
            String pCode = d.getParentMaterialCode();
            if (d.getLevel() > 1 && !pCode.isEmpty() && !checkedCodes.contains(pCode)) {
                checkedCodes.add(pCode);
                Material pm = getMaterialByCode(pCode, materialCache);
                if (pm == null) {
                    BomImportCheckDTO.MissingMaterial mis = new BomImportCheckDTO.MissingMaterial();
                    mis.setMaterialCode(pCode); mis.setMaterialName(""); mis.setSpecification("");
                    mis.setRowIndex(d.getRowIndex()); mis.setLevel(d.getLevel() - 1);
                    mis.setMaterialRole("中间层父件"); mis.setSuggestedType("半成品");
                    missingList.add(mis);
                }
            }
        }

        // === 冲突BOM检查：对每个parentCode（母件），看系统中是否已有同物料的任何BOM记录 ===
        List<BomImportCheckDTO.ConflictBomInfo> conflictBoms = new ArrayList<>();
        for (String mc : parentCodes) {
            Material m = getMaterialByCode(mc, materialCache); // 先查存在（上面missing里可能有不存在的就跳过）
            if (m == null) continue;
            // 取该物料下is_primary=1优先；若多份取bom_id最大那份（最近的）
            Bom exist = bomMapper.selectOne(new QueryWrapper<Bom>()
                    .eq("material_id", m.getMaterialId())
                    .orderByDesc("is_primary", "bom_id")
                    .last("LIMIT 1"));
            if (exist != null) {
                BomImportCheckDTO.ConflictBomInfo c = new BomImportCheckDTO.ConflictBomInfo();
                c.setMaterialCode(m.getMaterialCode());
                c.setMaterialName((m.getMaterialName() != null) ? m.getMaterialName() : dflt(m.getFullName(), m.getMaterialCode()));
                c.setExistingBomCode(exist.getBomCode());
                c.setExistingVersion(exist.getBomVersion() != null ? exist.getBomVersion() : "");
                c.setExistingBomDate(exist.getBomDate() != null ? exist.getBomDate().toString() : "");
                c.setExistingIsDefault(exist.getIsDefault() != null && exist.getIsDefault() == 1);
                conflictBoms.add(c);
            }
        }

        // === 缺失物料的分类编码补全与校验 ===
        // 中间层父件可能在后文作为子件出现 → 用完整映射回填分类编码
        for (BomImportCheckDTO.MissingMaterial mis : missingList) {
            if (mis.getCategoryCode() == null || mis.getCategoryCode().isBlank()) {
                String cc = parentCatByCode.get(mis.getMaterialCode());
                if (cc == null || cc.isBlank()) cc = childCatByCode.get(mis.getMaterialCode());
                if (cc != null && !cc.isBlank()) mis.setCategoryCode(cc);
            }
        }
        List<String> noCategoryCodes = new ArrayList<>();
        Map<String, Boolean> categoryExistsCache = new HashMap<>();
        for (BomImportCheckDTO.MissingMaterial mis : missingList) {
            String cc = mis.getCategoryCode();
            if (cc == null || cc.isBlank()) {
                // 未填写分类编码 → 拦截（自动生成物料编码需要用到分类编码）
                noCategoryCodes.add(mis.getMaterialCode());
            } else {
                String key = cc.trim();
                Boolean exists = categoryExistsCache.get(key);
                if (exists == null) {
                    exists = resolveCategoryIdByCode(key) != null;
                    categoryExistsCache.put(key, exists);
                }
                if (!exists) {
                    // 分类编码在系统中不存在 → 拦截，并提示具体编码
                    noCategoryCodes.add(mis.getMaterialCode() + "(分类编码[" + key + "]不存在)");
                }
            }
        }
        result.setMissingCategoryCodes(noCategoryCodes);
        result.setHasMissingCategory(!noCategoryCodes.isEmpty());

        result.setParseSuccess(true);
        result.setMissingMaterials(missingList);
        result.setMissingMaterialCount(missingList.size());
        result.setExistingMaterialCount(checkedCodes.size() - missingList.size());
        result.setBomRows(bomRows);
        result.setParentMaterialCodes(new ArrayList<>(parentCodes));
        result.setConflictBoms(conflictBoms);
        result.setHasConflict(!conflictBoms.isEmpty());
        return result;
    }
    private static String dflt(String... cands) {
        for (String s : cands) { if (s != null && !s.isEmpty()) return s; }
        return "";
    }

    @Override
    public byte[] buildBomStandardExcel(java.util.List<java.util.List<String>> dataRows) {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        List<List<String>> rows = dataRows == null ? new ArrayList<>() : dataRows;
        EasyExcel.write(bos)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("BOM导入")
                .head(buildSingleLevelHeaders(new ArrayList<>(BOM_UNIFIED_HEADERS)))
                .doWrite(rows);
        return bos.toByteArray();
    }

    @Override
    public BomImportCheckDTO checkBomImport(MultipartFile file) {
        Map<String, Material> cache = new HashMap<>();
        try (InputStream in = file.getInputStream()) {
            List<Object> raw = EasyExcel.read(in).headRowNumber(0).sheet().doReadSync();
            List<List<Object>> data = convertExcelRowsToList(raw);
            return parseAndCheckBomData(data, cache, null);
        } catch (IOException e) {
            log.error("BOM导入预检查失败", e);
            BomImportCheckDTO r = new BomImportCheckDTO();
            r.setParseSuccess(false); r.setParseError("文件解析失败: " + e.getMessage());
            return r;
        }
    }

    @Override
    public java.util.Map<String, Object> syncBomMissingMaterials(MultipartFile file) {
        BomImportCheckDTO check = checkBomImport(file);
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("parseSuccess", check.isParseSuccess());
        out.put("parseError", check.getParseError());
        out.put("missingCount", check.getMissingMaterialCount());
        out.put("createdCount", 0);
        out.put("createdMaterials", new ArrayList<>());
        out.put("failed", new ArrayList<>());
        if (!check.isParseSuccess()) return out;

        // 口径（客户确认）：缺失物料必须有分类编码；未填 → 直接提示并终止本次操作，不创建任何物料
        if (check.isHasMissingCategory()) {
            out.put("parseSuccess", false);
            out.put("parseError", "以下缺失物料未填写分类编码，本次同步已终止，请在模板中补全后重试："
                    + String.join("、", check.getMissingCategoryCodes()));
            return out;
        }

        int created = 0;
        List<String> failed = new ArrayList<>();
        List<java.util.Map<String, String>> createdList = new ArrayList<>();

        // 先整体预校验分类编码存在性：任一条不存在则终止（不创建任何物料）
        Map<String, Long> resolvedCategoryIds = new HashMap<>();
        for (BomImportCheckDTO.MissingMaterial miss : check.getMissingMaterials()) {
            Long cid = resolveCategoryIdByCode(miss.getCategoryCode());
            if (cid == null) {
                out.put("parseSuccess", false);
                out.put("parseError", "分类编码[" + miss.getCategoryCode() + "]不存在，本次同步已终止");
                return out;
            }
            resolvedCategoryIds.put(miss.getMaterialCode(), cid);
        }

        for (BomImportCheckDTO.MissingMaterial miss : check.getMissingMaterials()) {
            try {
                Material nm = createMissingMaterial(
                        miss.getMaterialCode(), miss.getMaterialName(),
                        miss.getSpecification(), miss.getSuggestedType(), miss.getSuggestedUnit(),
                        resolvedCategoryIds.get(miss.getMaterialCode()));
                created++;
                java.util.Map<String, String> row = new java.util.HashMap<>();
                row.put("materialCode", nm.getMaterialCode());
                row.put("materialName", nm.getMaterialName());
                createdList.add(row);
            } catch (Exception e) {
                log.warn("同步缺失物料失败: {} - {}", miss.getMaterialCode(), e.getMessage());
                failed.add(miss.getMaterialCode() + ": " + e.getMessage());
            }
        }
        out.put("createdCount", created);
        out.put("createdMaterials", createdList);
        out.put("failed", failed);
        return out;
    }

    /** 按分类编码查物料分类ID；不存在返回 null */
    private Long resolveCategoryIdByCode(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) return null;
        try {
            com.erp.basedata.entity.MaterialCategory c = materialCategoryMapper.selectOne(
                    new QueryWrapper<com.erp.basedata.entity.MaterialCategory>()
                            .eq("category_code", categoryCode.trim()).last("LIMIT 1"));
            return c == null ? null : c.getCategoryId();
        } catch (Exception e) {
            log.warn("查询物料分类失败: {} - {}", categoryCode, e.getMessage());
            return null;
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ImportResultDTO confirmBomImport(MultipartFile file, boolean autoCreateMaterials, String onExistingConflict) {
        final String conflictPolicy = (onExistingConflict == null || onExistingConflict.isEmpty())
                ? "NEW_VERSION" : onExistingConflict.toUpperCase();
        ImportResultDTO result = new ImportResultDTO();
        Map<String, Material> cache = new HashMap<>();

        try (InputStream in = file.getInputStream()) {
            List<Object> raw = EasyExcel.read(in).headRowNumber(0).sheet().doReadSync();
            List<List<Object>> excelData = convertExcelRowsToList(raw);
            if (excelData.isEmpty()) { result.setTotalCount(0); return result; }

            BomImportCheckDTO check = parseAndCheckBomData(excelData, cache, result);
            if (!check.isParseSuccess()) {
                result.addError(0, "", check.getParseError());
                return result;
            }
            // 把parse阶段记录的主属性冲突告警合并到最终message里
            // (parseUnifiedRow的ctx冲突消息没传出来，简化：后续此处可扩展)

            if (autoCreateMaterials && check.getMissingMaterialCount() > 0) {
                // 口径：缺失物料必须填写分类编码；未填 → 提示并终止本次导入（不创建物料、不写BOM）
                if (check.isHasMissingCategory()) {
                    result.addError(0, "分类编码", "以下缺失物料未填写分类编码，本次导入已终止，请补全后重试："
                            + String.join("、", check.getMissingCategoryCodes()));
                    return result;
                }
                int created = 0;
                for (BomImportCheckDTO.MissingMaterial miss : check.getMissingMaterials()) {
                    Long cid = resolveCategoryIdByCode(miss.getCategoryCode());
                    if (cid == null) {
                        result.addError(miss.getRowIndex(), "分类编码",
                                "分类编码[" + miss.getCategoryCode() + "]不存在，本次导入已终止");
                        return result;
                    }
                    try {
                        Material nm = createMissingMaterial(
                                miss.getMaterialCode(), miss.getMaterialName(),
                                miss.getSpecification(), miss.getSuggestedType(), miss.getSuggestedUnit(), cid);
                        cache.put(nm.getMaterialCode(), nm);
                        created++;
                    } catch (Exception e) {
                        log.warn("自动创建物料失败: {} - {}", miss.getMaterialCode(), e.getMessage());
                        result.addError(miss.getRowIndex(), "物料编码", "自动创建失败: " + e.getMessage());
                    }
                }
                result.setCreatedMaterialCount(created);
            }

            result.setTotalCount(check.getBomRows().size());
            // 【事务说明】ExcelServiceImpl.confirmBomImport 方法本身已加 @Transactional，
            //            与 saveBomMultiLevelUnified 的 REQUIRED 传播合并为同一事务，保证
            //            冲突覆盖时"删旧明细+写主表+插新明细"的原子性，且与上层 Controller
            //            注入的 ExcelService 代理调用一致（AOP开启事务）。
            result = saveBomMultiLevelUnified(check.getBomRows(), cache, result, conflictPolicy);
        } catch (IOException e) {
            log.error("BOM确认导入失败", e);
            throw new RuntimeException("BOM确认导入失败", e);
        }
        return result;
    }

    /**
     * 新保存逻辑：按18列解析后的bomRows写入BOM。
     * 加@Transactional保证"先删后插明细+更新主表"原子。
     * conflictPolicy: COVER（覆盖已有BOM，保留原bomCode+原isDefault） / NEW_VERSION（新建一份is_default=0）/ SKIP（跳过冲突母件）
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ImportResultDTO saveBomMultiLevelUnified(List<BomImportCheckDTO.BomRowData> bomRows,
                                                     Map<String, Material> materialCache,
                                                     ImportResultDTO result,
                                                     String conflictPolicy) {
        if (bomRows == null || bomRows.isEmpty()) return result;

        // ---------- Step1: 行聚合：按"母件编号"分组，同母件bomRows+取继承后主属性(取该分组第一行) ----------
        Map<String, List<BomImportCheckDTO.BomRowData>> rowsByMaterial = new LinkedHashMap<>();
        Map<String, BomImportCheckDTO.BomRowData> headerByMaterial = new LinkedHashMap<>();
        for (BomImportCheckDTO.BomRowData r : bomRows) {
            String mc = r.getMaterialCode();
            if (mc == null || mc.isEmpty()) continue;
            rowsByMaterial.computeIfAbsent(mc, k -> new ArrayList<>()).add(r);
            headerByMaterial.putIfAbsent(mc, r); // 第一行作为BOM主属性源
        }
        if (rowsByMaterial.isEmpty()) {
            result.addError(0, "", "没有任何有效的BOM母件数据行");
            return result;
        }

        int success = 0, fail = 0;

        // ---------- Step2: 逐个母件处理 ----------
        for (Map.Entry<String, List<BomImportCheckDTO.BomRowData>> e : rowsByMaterial.entrySet()) {
            String parentCode = e.getKey();
            List<BomImportCheckDTO.BomRowData> rows = e.getValue();
            BomImportCheckDTO.BomRowData hdr = headerByMaterial.get(parentCode);

            try {
                // 「单据状态」列必须在**保存前**解析：非法值要让该母件直接失败，
                // 否则会出现"报错但BOM已入库"的半成品（违反"不允许半成品"口径）
                ImportBillStatus bomWant = parseImportStatusValue(hdr.getBillStatus());
                // 拿母件物料
                Material parentMaterial = getMaterialByCode(parentCode, materialCache);
                if (parentMaterial == null) {
                    result.addError(0, "母件编码", "物料不存在(请先创建或勾选自动创建): " + parentCode);
                    fail += rows.size();
                    continue;
                }

                // 取该物料下已存在的主BOM
                Bom existingBom = bomMapper.selectOne(new QueryWrapper<Bom>()
                        .eq("material_id", parentMaterial.getMaterialId())
                        .orderByDesc("is_primary", "bom_id")
                        .last("LIMIT 1"));

                Bom bom;
                boolean isNewBom;
                if (existingBom != null) {
                    // === 有冲突 ===
                    switch (conflictPolicy) {
                        case "SKIP":
                            // 跳过冲突母件，不计失败（成功数也不计），记一条提示信息
                            result.addError(0, "",
                                    "母件[" + parentCode + "]已存在BOM[" + existingBom.getBomCode() + "]，按策略跳过未导入(" + rows.size() + "行)");
                            continue;
                        case "NEW_VERSION":
                            // 新建一份：isDefault isPrimary 强制null让saveBomWithItems兜底=同母件已有默认→新的设0
                            bom = buildBomFromHeader(parentMaterial, hdr, null);
                            bom.setIsDefault(null);
                            bom.setIsPrimary(null);
                            bomService.saveBomWithItems(bom, java.util.Collections.emptyList()); // 空明细先创建主表拿bom_id
                            isNewBom = true;
                            break;
                        case "COVER":
                        default:
                            // 覆盖：沿用原bom对象 + 主属性从模板更新（is_default、is_primary不动，保留用户设置）
                            bom = existingBom;
                            applyBomHeaderFromTemplate(bom, parentMaterial, hdr,
                                    /*keepDefaultAndPrimary*/ true);
                            // 先删该bom的旧明细
                            bomItemMapper.delete(new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()));
                            bomService.updateById(bom);
                            isNewBom = false;
                            break;
                    }
                } else {
                    // === 无冲突：正常新建一份 ===
                    bom = buildBomFromHeader(parentMaterial, hdr, null);
                    bom.setIsDefault(null); // 交给bomService.saveBomWithItems兜底规则（无默认→1，有→0）
                    bom.setIsPrimary(null);
                    bomService.saveBomWithItems(bom, java.util.Collections.emptyList());
                    isNewBom = true;
                }

                if (bom.getBomId() == null) {
                    throw new IllegalStateException("BOM ID未生成，母件=" + parentCode);
                }

                // ---------- Step3: 把bomRows转成bomItem并按层级写入明细 ----------
                // 把rows按层级分组，同saveBomMultiLevel老逻辑
                Map<Integer, List<BomItem>> levelItemsMap = new TreeMap<>();
                // 转item（填充material_id等DB需要的字段）
                for (BomImportCheckDTO.BomRowData rd : rows) {
                    BomItem item = rowDataToItem(rd, bom, materialCache);
                    if (item == null) {
                        fail++;
                        continue;
                    }
                    int lv = rd.getLevel() < 1 ? 1 : rd.getLevel();
                    item.setLevel(lv);
                    levelItemsMap.computeIfAbsent(lv, k -> new ArrayList<>()).add(item);
                }
                if (levelItemsMap.isEmpty()) continue;

                // 写明细：lineNo从10递增；处理多级parent_line_no
                Map<String, Long> matLine = new HashMap<>();
                int lineNo = 10;
                for (Map.Entry<Integer, List<BomItem>> lve : levelItemsMap.entrySet()) {
                    int lv = lve.getKey();
                    for (BomItem it : lve.getValue()) {
                        // 判断当前item是否属于当前bom（避免multi-parent共享代码时误写）
                        boolean belongs = isItemBelongsToBom(it, parentCode, bomRows, lv);
                        if (!belongs) continue;

                        BomItem ni = new BomItem();
                        ni.setBomId(bom.getBomId());
                        ni.setLineNo(lineNo);
                        ni.setMaterialId(it.getMaterialId());
                        ni.setMaterialCode(it.getMaterialCode());
                        ni.setMaterialName(it.getMaterialName());
                        ni.setSpecification(it.getSpecification());
                        ni.setModelNo(it.getModelNo());
                        ni.setQuantity(it.getQuantity());
                        ni.setScrapRate(it.getScrapRate());
                        ni.setProcessNo(it.getProcessNo());
                        ni.setRemark(it.getRemark());
                        ni.setUnitId(it.getUnitId());
                        ni.setQtyType("固定");
                        ni.setIsKeyPart(it.getIsKeyPart());
                        ni.setSupplyType(it.getSupplyType() != null ? it.getSupplyType() : "领料");
                        ni.setIsOptional((short) 0);
                        ni.setEffectiveDate(it.getEffectiveDate());
                        ni.setExpiryDate(it.getExpiryDate());
                        ni.setQtyType(it.getQtyType() != null ? it.getQtyType() : "固定");
                        ni.setPositionNo(it.getPositionNo());
                        if (lv > 1) {
                            String pm = it.getParentMaterialCode();
                            Long parentLn = matLine.get(pm + "_" + (lv - 1));
                            if (parentLn != null) ni.setParentLineNo(parentLn.intValue());
                        }
                        bomItemMapper.insert(ni);
                        matLine.put(it.getMaterialCode() + "_" + lv, (long) lineNo);
                        lineNo += 10;
                    }
                }
                success += rows.size();
                // 「单据状态」列分流：草稿=只存草稿；已生效=提交（审批开关开→待审核+待办，关→提交即生效）。
                // 与 22 张单据共用同一套通用件（解析/提交动作/取不到主键即报错回滚），不另写一套口径。
                if (bomWant == ImportBillStatus.EFFECTIVE) {
                    Bom current = bomService.getById(bom.getBomId());
                    // 覆盖导入(COVER)时目标 BOM 可能已是生效态 → 再提交会被服务层拒绝，此处按"已生效"跳过，避免整批报错
                    if (current == null || !com.erp.common.bill.BillStatusUtils.isApprovedClass(current.getStatus())) {
                        applyImportStatusAfterSave("bom", bom.getBomId(), ImportBillStatus.EFFECTIVE, result);
                    }
                }
                // 回传本次实际生成/覆盖的 BOM 编码，让用户明确"到底导入了哪张BOM"，而不是只回一句成功
                String bomCode = bom.getBomCode();
                if (bomCode == null || bomCode.isBlank()) {
                    Bom savedBom = bomService.getById(bom.getBomId());
                    bomCode = savedBom != null ? savedBom.getBomCode() : null;
                }
                if (bomCode != null && !bomCode.isBlank() && !result.getBomCodes().contains(bomCode)) {
                    result.getBomCodes().add(bomCode);
                }
                log.info("BOM导入：母件={}, bomId={}, bomCode={}, 策略={}, {}行明细",
                        parentCode, bom.getBomId(), bomCode, conflictPolicy, rows.size());
            } catch (Exception ex) {
                log.error("保存BOM失败：母件=" + parentCode, ex);
                fail += rows.size();
                result.addError(0, "", "保存BOM失败(" + parentCode + "): " + ex.getMessage());
            }
        }

        result.setSuccessCount(result.getSuccessCount() + success);
        result.setFailCount(result.getFailCount() + fail);
        result.setMessage(mergeMessages(result.getMessage()));
        return result;
    }

    private static String mergeMessages(String old) {
        return old != null ? old : "";
    }

    /** 属于当前母件的判断（和旧saveBomMultiLevel的"祖先判断"一致）。用原始bomRows集合做层级追溯。 */
    private boolean isItemBelongsToBom(BomItem it, String parentCode,
                                        List<BomImportCheckDTO.BomRowData> allRows, int lv) {
        if (lv == 1) {
            return parentCode.equals(it.getParentMaterialCode());
        }
        // lv>1: 追溯直属父件在rows里的level=lv-1记录，递归判断是否最终归属到parentCode
        String p = it.getParentMaterialCode();
        if (p == null || p.isEmpty()) return false;
        if (parentCode.equals(p)) return true;
        BomImportCheckDTO.BomRowData parentRow = allRows.stream()
                .filter(r -> r.getLevel() == lv - 1 && p.equals(r.getChildMaterialCode()))
                .findFirst().orElse(null);
        if (parentRow == null) return false;
        // 用临时BomItem模拟父件，再次递归
        BomItem tmp = new BomItem();
        tmp.setLevel(lv - 1);
        tmp.setParentMaterialCode(parentRow.getParentMaterialCode());
        tmp.setMaterialCode(parentRow.getChildMaterialCode());
        return isItemBelongsToBom(tmp, parentCode, allRows, lv - 1);
    }

    /** 从解析行 + 模板默认值 构建 BomItem */
    private BomItem rowDataToItem(BomImportCheckDTO.BomRowData rd, Bom bom,
                                  Map<String, Material> materialCache) {
        BomItem it = new BomItem();
        it.setLevel(rd.getLevel());
        it.setParentMaterialCode(rd.getParentMaterialCode());

        Material cm = getMaterialByCode(rd.getChildMaterialCode(), materialCache);
        if (cm == null) {
            return null;  // 缺失物料时不写入（前面的自动创建/skip已经处理过，能到这的是物料必须存在）
        }
        it.setMaterialId(cm.getMaterialId());
        it.setMaterialCode(cm.getMaterialCode());
        it.setMaterialName((rd.getChildMaterialName() != null && !rd.getChildMaterialName().isEmpty())
                ? rd.getChildMaterialName() : cm.getMaterialName());
        String spec = (rd.getSpecification() != null && !rd.getSpecification().isEmpty())
                ? rd.getSpecification() : cm.getModel();
        it.setSpecification(spec);
        it.setModelNo(spec);
        it.setUnitId(cm.getBaseUnitId());

        // 用量
        try {
            it.setQuantity(new BigDecimal(rd.getQuantityValue() != null ? rd.getQuantityValue() : "0"));
        } catch (NumberFormatException e) {
            it.setQuantity(BigDecimal.ZERO);
        }
        // 损耗率
        try {
            it.setScrapRate(new BigDecimal(rd.getScrapRate() != null && !rd.getScrapRate().isEmpty() ? rd.getScrapRate() : "0"));
        } catch (NumberFormatException e) {
            it.setScrapRate(BigDecimal.ZERO);
        }
        it.setProcessNo(rd.getProcessNo());
        // 关键件：是/Y/1 → 1；其他→0
        String kp = rd.getKeyPart() != null ? rd.getKeyPart().trim() : "";
        it.setIsKeyPart("是".equals(kp) || "Y".equalsIgnoreCase(kp) || "1".equals(kp) ? (short) 1 : (short) 0);
        it.setRemark(rd.getItemRemark());
        it.setSupplyType("领料");
        it.setIsOptional((short) 0);
        it.setQtyType("固定");
        return it;
    }

    /** 从母件物料 + Excel行继承后的主属性（hdr）构建Bom对象。
     *  注意：status/approvalStatus/isDefault/isPrimary 留空/null，交给bomService.saveBomWithItems统一处理（其内部会按审批开关/默认唯一规则兜底），避免在此硬编码覆盖正确默认值。*/
    private Bom buildBomFromHeader(Material pm, BomImportCheckDTO.BomRowData hdr, Long existingBomId) {
        Bom bom = new Bom();
        if (existingBomId != null) bom.setBomId(existingBomId);
        bom.setMaterialId(pm.getMaterialId());
        bom.setMaterialCode(pm.getMaterialCode());
        bom.setMaterialName(pm.getMaterialName());
        bom.setSpecification(pm.getModel());
        bom.setBomName(pm.getMaterialName());
        // 单据日期
        if (hdr != null && hdr.getBomDate() != null && !hdr.getBomDate().isEmpty()) {
            try { bom.setBomDate(LocalDate.parse(hdr.getBomDate())); }
            catch (Exception ignored) { bom.setBomDate(LocalDate.now()); }
        } else {
            bom.setBomDate(LocalDate.now());
        }
        // BOM分类/BOM类型
        String cat = (hdr != null && hdr.getBomCategory() != null && !hdr.getBomCategory().isEmpty())
                ? hdr.getBomCategory() : "标准BOM";
        bom.setBomCategory(cat);
        bom.setBomType(cat);
        // 版本号
        bom.setBomVersion(hdr != null ? hdr.getBomVersion() : null);
        // 数量
        BigDecimal qty = BigDecimal.ONE;
        if (hdr != null && hdr.getQuantity() != null && !hdr.getQuantity().isEmpty()) {
            try { qty = new BigDecimal(hdr.getQuantity()); }
            catch (NumberFormatException ignored) {}
        }
        if (qty.compareTo(BigDecimal.ZERO) <= 0) qty = BigDecimal.ONE;
        bom.setQuantity(qty);

        bom.setIsEnabled((short) 1);
        bom.setUnitId(pm.getBaseUnitId());
        // status/approvalStatus/isDefault/isPrimary = null → 交给 BomServiceImpl.saveBomWithItems 处理默认值
        return bom;
    }

    /** 覆盖时用：从hdr更新bom主属性，isDefault/isPrimary可选保留（keepDefaultAndPrimary=true不动用户的默认/主设置） */
    private void applyBomHeaderFromTemplate(Bom bom, Material pm, BomImportCheckDTO.BomRowData hdr,
                                            boolean keepDefaultAndPrimary) {
        bom.setMaterialId(pm.getMaterialId());
        bom.setMaterialCode(pm.getMaterialCode());
        if (bom.getMaterialName() == null || bom.getMaterialName().isEmpty())
            bom.setMaterialName(pm.getMaterialName());
        bom.setSpecification(pm.getModel());
        if (hdr != null && hdr.getBomDate() != null && !hdr.getBomDate().isEmpty()) {
            try { bom.setBomDate(LocalDate.parse(hdr.getBomDate())); }
            catch (Exception ignored) {}
        }
        if (hdr != null && hdr.getBomCategory() != null && !hdr.getBomCategory().isEmpty()) {
            bom.setBomCategory(hdr.getBomCategory());
            bom.setBomType(hdr.getBomCategory());
        }
        if (hdr != null) bom.setBomVersion(hdr.getBomVersion());
        if (hdr != null && hdr.getQuantity() != null && !hdr.getQuantity().isEmpty()) {
            try {
                BigDecimal q = new BigDecimal(hdr.getQuantity());
                if (q.compareTo(BigDecimal.ZERO) > 0) bom.setQuantity(q);
            } catch (NumberFormatException ignored) {}
        }
        // 默认BOM/主 BOM 不动（保留用户原设置）
        if (!keepDefaultAndPrimary) {
            // 不保留时（NEW_VERSION场景不会到这），当前场景永远keep
        }
    }

    /** 自动创建缺失物料（复用旧逻辑，位置移到此处保持前面代码可用） */
    private Material createMissingMaterial(String code, String name, String spec, String type, String unitName, Long categoryId) {
        Long unitId = null;
        if (unitName != null && !unitName.isEmpty()) {
            com.erp.basedata.entity.UnitOfMeasure unit = unitOfMeasureMapper.selectOne(
                    new QueryWrapper<com.erp.basedata.entity.UnitOfMeasure>()
                            .eq("unit_name", unitName).last("LIMIT 1"));
            if (unit != null) unitId = unit.getUnitId();
        }
        if (unitId == null) {
            // 模板未带单位时兜底：优先单位编码 PCS（件），其次名称"件"/"个"，最后取系统第一个计量单位
            com.erp.basedata.entity.UnitOfMeasure def = unitOfMeasureMapper.selectOne(
                    new QueryWrapper<com.erp.basedata.entity.UnitOfMeasure>()
                            .eq("unit_code", "PCS").orderByAsc("unit_id").last("LIMIT 1"));
            if (def == null) {
                def = unitOfMeasureMapper.selectOne(new QueryWrapper<com.erp.basedata.entity.UnitOfMeasure>()
                        .eq("unit_name", "件").last("LIMIT 1"));
            }
            if (def == null) {
                def = unitOfMeasureMapper.selectOne(new QueryWrapper<com.erp.basedata.entity.UnitOfMeasure>()
                        .eq("unit_name", "个").last("LIMIT 1"));
            }
            if (def == null) {
                def = unitOfMeasureMapper.selectOne(new QueryWrapper<com.erp.basedata.entity.UnitOfMeasure>()
                        .orderByAsc("unit_id").last("LIMIT 1"));
            }
            if (def != null) unitId = def.getUnitId();
        }
        Material m = new Material();
        m.setMaterialCode(code);
        m.setMaterialName((name != null && !name.isEmpty()) ? name : code);
        m.setFullName((name != null && !name.isEmpty()) ? name : code);
        m.setModel(spec);
        m.setCategoryId(categoryId);
        m.setMaterialType((type != null && !type.isEmpty()) ? type : "原材料");
        m.setBaseUnitId(unitId); m.setPurchaseUnitId(unitId); m.setSaleUnitId(unitId); m.setStockUnitId(unitId);
        m.setStatus("正常");
        m.setIsLotControl((short)0); m.setIsSnControl((short)0); m.setIsBinControl((short)0);
        m.setIsQcRequired((short)0); m.setIsExpiryControl((short)0);
        m.setSalePrice(BigDecimal.ZERO); m.setPurchasePrice(BigDecimal.ZERO);
        m.setMinStock(BigDecimal.ZERO); m.setMaxStock(BigDecimal.ZERO);
        m.setCreatedAt(LocalDateTime.now()); m.setUpdatedAt(LocalDateTime.now());
        materialMapper.insert(m);
        return m;
    }
}