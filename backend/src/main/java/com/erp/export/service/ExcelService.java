package com.erp.export.service;

import com.erp.export.dto.ExportConfigDTO;
import com.erp.export.dto.ImportResultDTO;
import com.erp.export.dto.BomImportCheckDTO;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

public interface ExcelService {

    ExportConfigDTO getExportConfig(String entityName);

    void exportTemplate(String entityName, HttpServletResponse response);

    /**
     * 销售订单导入模板：支持按用户勾选字段过滤列（必填列始终包含），并追加明细列。
     * @param fields 用户勾选的表头字段标签列表（null=仅必填列）
     * @param response HTTP 响应
     */
    void exportSaleOrderTemplate(List<String> fields, HttpServletResponse response);

    /** 销售报价单导入模板：表头列 + 明细列 */
    void exportSaleQuoteTemplate(List<String> fields, HttpServletResponse response);

    /** 销售合同导入模板：表头列 + 明细列 */
    void exportSaleContractTemplate(List<String> fields, HttpServletResponse response);

    /** 销售出库单导入模板：表头列 + 明细列 */
    void exportSaleDeliveryTemplate(List<String> fields, HttpServletResponse response);

    /** 销售退货单导入模板：表头列 + 明细列 */
    void exportSaleReturnTemplate(List<String> fields, HttpServletResponse response);

    /** 销售发票导入模板：表头列 + 明细列 */
    void exportSaleInvoiceTemplate(List<String> fields, HttpServletResponse response);

    /** 售后维修单导入模板：表头列 + 维修明细列 */
    void exportSaleRepairTemplate(List<String> fields, HttpServletResponse response);

    /**
     * 销售单据导入模板统一入口（表头列 + 明细列）。
     * 由 ExportController 按登记表 {@code Group.SALE} 分派，控制器不再维护 6 个 equals 分支。
     */
    void exportSaleBillTemplate(String entityName, List<String> fields, HttpServletResponse response);

    /**
     * 按登记表分派的导入入口：ExportController 不再维护 24 个 if/else 实体分支。
     * 未登记、或登记为 {@code importable=false} 的实体一律拒绝。
     */
    ImportResultDTO importByEntity(String entityName, MultipartFile file);

    /**
     * 按登记表分派的导入入口（带单据类型）：用于共用同一张表的单据（生产退料/补料共用领料表），
     * billType 为空时与 {@link #importByEntity(String, MultipartFile)} 完全一致。
     */
    ImportResultDTO importByEntity(String entityName, MultipartFile file, String billType);

    /**
     * 导出【库存现存量】（查询视图，不是单据）。
     * 专用实现原因：{@code inventory_current} 的实体没有任何 {@code @ExportField}，
     * 且用户要看的"物料编码/名称/规格/仓库编码/名称/单位"全是需要关联查询的展示字段，
     * 走通用反射导出只会得到一张空表（历史实测：20 行 0 单元格）。
     *
     * @param templateOnly true=只出表头（下载模板用）；false=表头 + 全量现存量数据
     */
    void exportInventoryCurrent(HttpServletResponse response, boolean templateOnly);

    /**
     * 销售/采购价格策略导入模板（表头 + 明细结构，专用扁平列：一行 = 一个对象的价格）。
     * 专用实现原因：价格策略是「表头 + 明细」结构、实体无 @ExportField，
     * 且导出/模板/导入三处共用同一套 13 列（策略编码/适用范围/对象编码/对象名称/数量从/数量到/
     * 价格类型/价格值/备注/优先级/状态/生效日期/失效日期），走通用反射导出只会得到空表。
     *
     * @param entityName salePriceStrategy 或 purchasePriceStrategy
     */
    void exportPriceStrategyTemplate(String entityName, HttpServletResponse response);

    /**
     * 销售/采购价格策略数据导出：查询全部策略（含明细），一行一个对象，对象编码/名称取对象实际值（不是 ID）。
     *
     * @param entityName salePriceStrategy 或 purchasePriceStrategy
     */
    void exportPriceStrategyData(String entityName, HttpServletResponse response);

    /** 销售价格策略导入：按「策略编码」分组，同一编码的多行 = 同一策略的多条明细；复用现有保存逻辑落库 */
    ImportResultDTO importSalePriceStrategies(MultipartFile file);

    /** 采购价格策略导入：按「策略编码」分组，同一编码的多行 = 同一策略的多条明细；复用现有保存逻辑落库 */
    ImportResultDTO importPurchasePriceStrategies(MultipartFile file);

    /**
     * 通用"按列写出"Excel（2026-09-22 新增，供查询类页面导出与页面列保持一致）。
     *
     * <p>用途：即时库存导出要求"页面上看到什么，导出就带什么"——列名与列顺序由调用方按
     * **页面当前可见列**传入（含自定义字段列），本方法只负责按位置行写盘，
     * 不做任何列裁剪或重排，避免再出现"页面有的列导出没有"。</p>
     *
     * @param fileName  下载文件名（不含 .xlsx 后缀）
     * @param sheetName sheet 名
     * @param labels    表头（顺序 = 导出列顺序）
     * @param rows      数据行（每行长度与 labels 一致；可为空 → 只出表头）
     */
    void writeSheetByLabels(HttpServletResponse response, String fileName, String sheetName,
                            java.util.List<String> labels, java.util.List<java.util.List<Object>> rows);

    /**
     * 「页面看到什么就导出什么」通用导出（2026-09-24 用户口径，全站统一）。
     *
     * <p>与既有 {@code exportData / exportDataWithSelectedFields} 的区别：
     * ①**列完全由调用方（页面）决定**——按页面当前可见列的顺序与列名逐列输出（含物料/单据自定义字段列），
     *   请求里没有的列（含明细列、未显示的自定义列）一律不输出；②**行由调用方给的主键集合决定**
     *   （即"页面当前筛选条件下的结果"），不再固定全量；③表头文字直接用页面列名，保证与页面逐字一致。</p>
     *
     * @param entityName 实体名（与 /api/export/data/{entityName} 同一套登记表）
     * @param ids        要导出的行主键；为空 = 该实体全量（套数据权限）
     * @param rows       **页面手上那批行数据**（推荐）：传了就用它直接取值（页面显示什么就导出什么，
     *                   展示字段/别名列/蛇形列都能带上）；不传则按 ids 回查实体
     * @param fields     导出列，按页面顺序：[{key:实体属性名/自定义字段fieldKey, label:页面列名, custom:true/false}]
     */
    void exportVisibleColumns(String entityName, java.util.List<Long> ids,
                              java.util.List<java.util.Map<String, Object>> rows,
                              java.util.List<java.util.Map<String, Object>> fields,
                              HttpServletResponse response);

    /**
     * 销售订单模板可选字段清单（导入弹窗"模板字段设置"数据源）。
     * @return required=必带列标签（含明细列）；optional=用户可勾选的表头列标签
     */
    java.util.Map<String, List<String>> getSaleOrderTemplateFields();

    /**
     * 通用销售单据模板字段清单（导入弹窗"自定义模板字段"数据源）。
     * @param entityName 销售单据实体名（saleOrder/saleQuote/saleContract/saleDelivery/saleReturn/saleInvoice）
     * @return required=必带列标签（含明细列+自定义字段）；optional=用户可勾选的表头列标签
     */
    java.util.Map<String, List<String>> getSaleTemplateFields(String entityName);

    /**
     * 销售订单专用导入：单sheet多行明细（一行一条明细，同订单多行表头留空继承），
     * 客户支持 ID/编码/名称三种填法，物料按编码解析，一律存草稿状态。
     * @param file 上传的Excel文件
     * @return 导入结果（按订单数统计）
     */
    ImportResultDTO importSaleOrders(MultipartFile file);

    /** 销售报价单专用导入：主子表聚合，客户三选一，物料按编码，存草稿 */
    ImportResultDTO importSaleQuotes(MultipartFile file);

    /** 销售合同专用导入：主子表聚合，客户三选一，物料按编码，存草稿 */
    ImportResultDTO importSaleContracts(MultipartFile file);

    /** 销售出库单专用导入：主子表聚合，客户三选一，物料按编码，存草稿 */
    ImportResultDTO importSaleDeliveries(MultipartFile file);

    /** 销售退货单专用导入：主子表聚合，客户三选一，物料按编码，存草稿 */
    ImportResultDTO importSaleReturns(MultipartFile file);

    /** 销售发票专用导入：主子表聚合，客户三选一，物料按编码，存草稿 */
    ImportResultDTO importSaleInvoices(MultipartFile file);

    /** 售后维修单专用导入：主子表聚合，客户三选一，物料按编码，存草稿 */
    ImportResultDTO importSaleRepairs(MultipartFile file);

    /** 采购单据模板字段清单（通用） */
    java.util.Map<String, List<String>> getPurchaseTemplateFields(String entityName);

    /** 采购单据导入模板（通用） */
    void exportPurchaseTemplate(String entityName, List<String> fields, HttpServletResponse response);

    /** 采购申请单专用导入：主子表聚合，供应商三选一，物料按编码，存草稿 */
    ImportResultDTO importPurchaseRequests(MultipartFile file);

    /** 采购订单专用导入：主子表聚合，供应商三选一，物料按编码，存草稿 */
    ImportResultDTO importPurchaseOrders(MultipartFile file);

    /** 采购合同专用导入：主子表聚合，供应商三选一，物料按编码，存草稿 */
    ImportResultDTO importPurchaseContracts(MultipartFile file);

    /** 采购入库单专用导入：主子表聚合，供应商三选一，物料按编码，存草稿 */
    ImportResultDTO importPurchaseInbounds(MultipartFile file);

    /** 采购退货单专用导入：主子表聚合，供应商三选一，物料按编码，存草稿 */
    ImportResultDTO importPurchaseReturns(MultipartFile file);

    /** 采购发票专用导入：主子表聚合，供应商三选一，物料按编码，存草稿 */
    ImportResultDTO importPurchaseInvoices(MultipartFile file);

    // ==================== 库存模块 ====================
    /**
     * 库存单据导入模板。
     * billType 仅对「生产领料/退料/补料」这张共用表有意义：传 RETURN / SUPPLEMENT 时
     * 模板单号/日期列换成各自口径，并去掉由导入弹窗决定的「单据类型」列。
     */
    void exportInventoryTemplate(String entityName, List<String> fields, String billType, HttpServletResponse response);
    /** 兼容旧调用：不带单据类型（行为与改造前完全一致） */
    default void exportInventoryTemplate(String entityName, List<String> fields, HttpServletResponse response) {
        exportInventoryTemplate(entityName, fields, null, response);
    }
    java.util.Map<String, List<String>> getInventoryTemplateFields(String entityName);
    ImportResultDTO importOtherReceipts(MultipartFile file);
    ImportResultDTO importOtherIssues(MultipartFile file);
    ImportResultDTO importStockTransfers(MultipartFile file);
    ImportResultDTO importStockChecks(MultipartFile file);
    ImportResultDTO importMaterialRequisitions(MultipartFile file);

    // ==================== 财务模块 ====================
    void exportFinanceTemplate(String entityName, List<String> fields, HttpServletResponse response);
    java.util.Map<String, List<String>> getFinanceTemplateFields(String entityName);
    ImportResultDTO importPaymentVouchers(MultipartFile file);
    ImportResultDTO importPaymentReceipts(MultipartFile file);
    ImportResultDTO importPurchasePrePayments(MultipartFile file);
    ImportResultDTO importSalePreReceipts(MultipartFile file);
    ImportResultDTO importOperationExpenses(MultipartFile file);

    void exportData(String entityName, List<?> dataList, HttpServletResponse response);

    /**
     * 按用户选择的列导出数据（自定义导出）
     * @param entityName 实体名
     * @param selectedFields 用户选择的字段名列表（Java 字段名）
     * @param response HTTP 响应
     */
    void exportDataWithSelectedFields(String entityName, List<String> selectedFields, HttpServletResponse response);

    ImportResultDTO importData(String entityName, MultipartFile file);

    /**
     * 客户池导入：在通用导入基础上，对客户实体强制设置 poolType 与 ownerUserId。
     * 仅对 entityName=customer 生效，其他实体忽略这两个参数。
     *
     * @param entityName  实体名（customer）
     * @param file        上传的Excel文件
     * @param poolType    池类型：0=私海，1=公海；null 表示不设置
     * @param ownerUserId 归属用户ID（私海导入传当前用户，公海导入传 null）
     * @return 导入结果
     */
    ImportResultDTO importDataWithPool(String entityName, MultipartFile file, Integer poolType, Long ownerUserId);

    /**
     * 按池类型查询客户列表（用于公海/私海导出过滤）。
     *
     * @param poolType    池类型：0=私海，1=公海；null 表示不过滤
     * @param ownerUserId 归属用户ID（私海导出时可按用户过滤，null 表示不过滤）
     * @return 客户列表
     */
    List<?> listCustomersByPoolType(Integer poolType, Long ownerUserId);

    /**
     * 线索专用导入：支持读取 Excel 内嵌图片并保存为文件，URL 回填 image_url
     * @param file 上传的Excel文件
     * @return 导入结果
     */
    ImportResultDTO importLeadsWithImages(MultipartFile file);

    /**
     * 物料专用导入：支持读取 Excel 内嵌图片并保存为文件，URL 回填 image_urls，首图回填 main_image
     * @param file 上传的Excel文件
     * @return 导入结果
     */
    ImportResultDTO importMaterialsWithImages(MultipartFile file);

    /**
     * 物料导入预检查：与物料库比对存在性（先编码、后名称），返回冲突清单，不写库。
     * @param file 上传的Excel文件
     * @return 冲突清单 + 行级校验错误
     */
    com.erp.export.dto.MaterialImportCheckDTO checkMaterialImport(MultipartFile file);

    /**
     * 物料导入确认：按策略处理已存在物料。
     * @param file 上传的Excel文件
     * @param onExistingConflict SKIP=跳过已存在（默认）/ OVERWRITE=覆盖已存在（物料编码保持原值）
     * @return 导入结果（新增/覆盖/跳过/失败）
     */
    ImportResultDTO confirmMaterialImport(MultipartFile file, String onExistingConflict);

    List<?> listAllData(String entityName);

    /**
     * 导出「勾选的 N 条」（列表页批量导出选中专用，2026-09-19 新增）。
     *
     * <p>与 {@link #listAllData(String)} 同一取数口径（业务准入门槛 + 数据权限），
     * 只是把范围收窄到传入的主键集合；主键通过 MyBatis-Plus 元数据解析，
     * 兼容 order_id / inbound_id 这类非 id 主键列名。</p>
     *
     * @param ids 勾选记录的主键；null 或空 视为"没有选中任何记录"，导出为空（**不回退成全量**，避免误用造成全表导出）
     */
    void exportSelected(String entityName, List<Long> ids, HttpServletResponse response);

    void exportBomMultiLevel(Long bomId, HttpServletResponse response);

    /**
     * 导出 BOM 精简统一模板（18列默认；支持用户按勾选字段自定义列）。
     * @param fields 用户勾选的字段标签列表（中文，与 BOM_UNIFIED_HEADERS 一致）。
     *               null 或 空列表 = 使用默认 18 列全部导出。
     *               系统锁定的必填列（母件编号、子件编码、用量三列，模板头自带星号标识）用户不勾选也会自动追加，保证导入时不报错。
     * @param response HTTP 响应
     */
    void exportBomTemplate(List<String> fields, HttpServletResponse response);

    /**
     * 导出 BOM 旧版兼容模板（11 列多级格式，纯树形层级写法：根节点声明母件=父件编码且父件编码=子件编码，level=1）。
     * 保留兼容历史用户，不支持字段自定义勾选（旧模板结构复杂不易定制）。
     */
    void exportBomLegacyTemplate(HttpServletResponse response);

    /**
     * BOM 模板可选字段清单（供前端"自定义模板字段"勾选面板使用）。
     * 返回结构：{ "columns": [{label, group, locked, defaultChecked}] }
     * - group："mainProps"=BOM主属性列（左侧表头继承） / "itemProps"=子件明细列（右侧每行一条）
     * - locked：true 表示系统锁定必填（前端禁用勾选框，不可取消），false 可自由勾选
     * - defaultChecked：true 表示默认 18 列精简模板选中的列
     */
    java.util.Map<String, Object> getBomTemplateFields();

    /**
     * 按统一18列模板，把"已经转换好的标准BOM行"写成 Excel 字节内容。
     * 用途：AI 结构化导入（bom_import_rows）——大模型负责把任意格式的文件转换成标准行，
     * 本方法负责按与手工导入完全一致的模板落成文件，从而复用同一条导入链路，避免两套规则。
     *
     * @param dataRows 每行元素顺序必须与统一模板列顺序一致：
     *                 单据日期、母件编号、母件名称、BOM分类、版本号、数量、层级、父件编码(多级用)、母件分类编码、
     *                 子件编码、子件名称、子件规格、用量、损耗率、工序、关键件、行备注、子件分类编码
     *                 （其中母件编号、子件编码、用量为必填列，模板表头带星号标识）
     * @return xlsx 字节内容
     */
    byte[] buildBomStandardExcel(java.util.List<java.util.List<String>> dataRows);

    /**
     * 可导入实体白名单（供 AI 导入工具校验 entityName，AI 不得扩展）：
     * = 通用单表导入实体（{@code ENTITY_CLASS_MAP}）+ 专用导入实体集合（其他出入库/调拨/盘点/领料/预收预付/费用单）。
     *
     * @return 实体名集合
     */
    java.util.Set<String> getImportableEntities();

    /**
     * 某实体的标准导入模板列（与界面"下载模板"同一来源）：
     * 基础资料取实体 {@code @ExportField} 反射列（按 order 排序，含自定义字段），
     * 单据类取各模块既有静态列常量（required=必带列含明细列；optional=可勾选表头列）。
     *
     * @param entityName 实体名（必须在 {@link #getImportableEntities()} 内）
     * @return 每列 {label=中文列名(不含*), required=是否必填, order=列序}
     */
    java.util.List<java.util.Map<String, Object>> getImportColumns(String entityName);

    /**
     * 把"结构化行"按该实体的标准模板列序造一份 Excel，供复用既有导入链路
     * （AI 行级导入：先 {@link #getImportColumns} 拿列序 → 映射成二维数组 → 本方法 → 复用导入）。
     *
     * @param entityName 实体名（必须在 {@link #getImportableEntities()} 内）
     * @param rows       每行元素顺序必须与 {@link #getImportColumns} 返回的列序严格一致
     * @return xlsx 字节内容
     */
    byte[] buildStandardExcel(String entityName, java.util.List<java.util.List<String>> rows);

    ImportResultDTO importBomMultiLevel(MultipartFile file);

    /**
     * 预检查BOM导入文件，检测缺失的物料
     * @param file BOM导入文件
     * @return 检查结果，包含缺失物料列表和解析的BOM数据
     */
    BomImportCheckDTO checkBomImport(MultipartFile file);

    /**
     * 一键同步 BOM 文件中缺失的物料到物料库（不导入BOM，仅补物料主档）
     * @return parseSuccess/missingCount/createdCount/createdMaterials/failed
     */
    java.util.Map<String, Object> syncBomMissingMaterials(MultipartFile file);

    /**
     * 确认导入BOM，可选择自动创建缺失物料
     * @param file BOM导入文件
     * @param autoCreateMaterials 是否自动创建缺失物料
     * @param onExistingConflict 同母件已有BOM冲突处理策略：COVER=覆盖(保留原BOM编号)、NEW_VERSION=新建一份同母件BOM、SKIP=跳过冲突母件
     * @return 导入结果
     */
    ImportResultDTO confirmBomImport(MultipartFile file, boolean autoCreateMaterials, String onExistingConflict);
}