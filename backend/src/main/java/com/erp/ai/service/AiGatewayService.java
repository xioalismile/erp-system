package com.erp.ai.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.ai.adapter.AbstractOpenAiCompatibleAdapter;
import com.erp.ai.dto.UnifiedRequest;
import com.erp.ai.dto.UnifiedResponse;
import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.ai.util.SensitiveDataMasker;
import com.erp.config.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AI网关核心服务
 * 职责: 渠道选择 → 意图识别 → 参数提取 → 业务路由 → 审计记录
 */
@Slf4j
@Service
public class AiGatewayService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Resource
    private AiAdapterResolver adapterResolver;

    @Value("${erp.ai.gateway.default-channel:QWEN}")
    private String defaultChannel;

    @Value("${erp.ai.gateway.write-confirm-required:true}")
    private boolean writeConfirmRequired;

    /** 路径安全统一入口：upload 根 + 采集截图目录白名单校验（安全审计 H-2） */
    @Resource
    private com.erp.ai.tool.support.AiUploadFileReader uploadFileReader;

    @Resource
    private com.erp.config.CacheService cache;

    @Resource
    private AiConfigService aiConfigService;

    /** 会话历史数据库持久化（2026-10-20 起替代 Redis 存储，与 Agent 链路共用同一张表） */
    @Resource
    private AiChatHistoryStore aiChatHistoryStore;

    // 会话记忆上限与保留时长必须与 Agent 链路（ReActAgentService）保持一致：
    // 两条链路共用同一张会话历史表，口径不一致会导致"存量会话被这条链路截断"。
    // 轮数由设置中心「对话能力-会话记忆」控制（默认 60，2026-10-10 由固定 20 提升）。
    private int maxHistory() {
        try {
            return aiConfigService.getChatHistoryRounds();
        } catch (Exception e) {
            return 60;
        }
    }

    /**
     * 获取会话历史（2026-10-20 起落数据库，与 Agent 链路共用 ai_chat_history 表）
     */
    private List<Map<String, String>> getHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return new ArrayList<>();
        return aiChatHistoryStore.load(sessionId, maxHistory() * 2);
    }

    /**
     * 保存会话历史（数据库持久化，永久保留）
     */
    private void saveHistory(String sessionId, String userMsg, String aiReply) {
        if (sessionId == null || sessionId.isBlank()) return;
        aiChatHistoryStore.append(sessionId, userMsg, aiReply);
    }

    private static final String SYSTEM_PROMPT = """
            你是智能ERP系统的全能AI助手。用户会用自然语言描述需求，你需要识别意图并提取参数。

            ⚡ 重要能力：本系统支持图片识别查询功能！当用户上传图片时，系统会先调用视觉模型识别图片内容，提取关键信息（如订单号、客户名称、物料名称、仓库位置等），并将识别结果附在用户输入中。你需要根据这些图片识别信息进行相应的业务查询。

            ==============================================
            【全模块CRUD操作 - 支持8大模块60+实体】
            ==============================================

            意图命名规则: {动作}_{模块}_{实体}
            动作: QUERY(查询) / CREATE(新增) / UPDATE(修改) / DELETE(删除)

            支持的模块和实体（可以根据名称模糊匹配）:

            【BASEDATA - 基础数据模块】
            - BASEDATA_CUSTOMER - 客户档案
            - BASEDATA_SUPPLIER - 供应商档案
            - BASEDATA_MATERIAL - 物料档案
            - BASEDATA_DEPARTMENT - 部门
            - BASEDATA_EMPLOYEE - 员工
            - BASEDATA_WAREHOUSE - 仓库
            - BASEDATA_LOCATION - 库位
            - BASEDATA_CURRENCY - 货币
            - BASEDATA_UNIT - 计量单位
            - BASEDATA_ACCOUNT - 结算账户
            - BASEDATA_PAYMENTTERM - 付款条件
            - BASEDATA_PRICELEVEL - 价格等级
            - BASEDATA_SETTLE - 结算方式
            - BASEDATA_MATERIALCATEGORY - 物料分类

            【CRM - 客户关系管理模块】
            - CRM_LEAD - 销售线索
            - CRM_OPPORTUNITY - 商机
            - CRM_FOLLOWUP - 跟进记录
            - CRM_COMPLAINT - 客户投诉
            - CRM_SURVEY - 客户拜访

            【SALE - 销售模块】
            - SALE_QUOTE - 销售报价单
            - SALE_ORDER - 销售订单
            - SALE_DELIVERY - 销售发货单
            - SALE_RETURN - 销售退货单
            - SALE_INVOICE - 销售发票
            - SALE_CONTRACT - 销售合同

            【PURCHASE - 采购模块】
            - PURCHASE_REQUEST - 采购申请单
            - PURCHASE_ORDER - 采购订单
            - PURCHASE_INBOUND - 采购入库单
            - PURCHASE_RETURN - 采购退货单
            - PURCHASE_INVOICE - 采购发票
            - PURCHASE_CONTRACT - 采购合同

            【INVENTORY - 库存模块】
            - INVENTORY_STOCK - 库存查询
            - INVENTORY_WAREHOUSE - 仓库档案
            - INVENTORY_LOCATION - 库位档案
            - INVENTORY_TRANSFER - 库存调拨单
            - INVENTORY_CHECK - 库存盘点单
            - INVENTORY_REQUISITION - 领料单

            【PRODUCTION - 生产模块】
            - PRODUCTION_WORKORDER - 生产工单
            - PRODUCTION_BOM - BOM物料清单
            - PRODUCTION_ROUTING - 工艺路线
            - PRODUCTION_RECEIPT - 生产入库单
            - PRODUCTION_WORKCENTER - 工作中心
            - PRODUCTION_WORKSTATION - 工位

            【FINANCE - 财务模块】
            - FINANCE_RECEIVABLE - 应收账款
            - FINANCE_PAYABLE - 应付账款
            - FINANCE_EXPENSE - 费用单

            【SYSTEM - 系统管理模块】
            - SYSTEM_USER - 系统用户
            - SYSTEM_ROLE - 角色
            - SYSTEM_MENU - 菜单
            - SYSTEM_DICT - 数据字典

            ==============================================
            【参数提取规则】
            ==============================================

            1. QUERY(查询)操作参数:
               - keyword: 关键词搜索（名称/编码等）
               - 其他字段作为过滤条件（如status, customerName等）

            2. CREATE(新增)操作参数:
               - data: 对象，包含实体的各字段值
               - 也可以直接传字段名作为参数

            3. UPDATE(修改)操作参数:
               - id或对应主键字段: 记录ID
               - data: 对象，包含要修改的字段值
               - 也可以直接传字段名作为参数

            4. DELETE(删除)操作参数:
               - id或对应主键字段: 记录ID
               - 或通过名称/编码查找后删除

            ==============================================
            【特殊意图 - 复杂业务操作】
            ==============================================

            1. CREATE_ORDER - 快速创建销售订单 (需要: 客户名称customerName, 商品列表items[物料编码materialCode/名称materialName, 数量quantity, 单价unitPrice], 交货日期deliveryDate)
            2. QUERY_ORDER - 查询订单 (可选: 客户名称customerName, 状态status, 月份month)
            3. QUERY_INVENTORY - 查询库存 (可选: 物料编码materialCode, 物料名称materialName)
            4. QUERY_CUSTOMER - 查询客户 (可选: 客户名称customerName)
            5. GENERATE_REPORT - 生成报表 (需要: 报表类型reportType: sales/inventory, 维度dimension, 时间范围dateRange)
            6. IMAGE_SEARCH - 以图识图：当用户上传图片并要求根据图片找物料、找订单、找单据、查库存时，使用此意图。系统会自动对比图片相似度，找到匹配的物料和相关单据。
            7. FAQ - 业务问答

            ⚡ 重要：如果用户上传了图片并且用户的问题是"这是什么物料"、"找一下这个物料"、"查一下这个产品的订单"、"看看这个商品的库存"、"以图识图"、"图片搜索"等类似含义，优先使用 IMAGE_SEARCH 意图！

            ==============================================
            【回复格式要求】
            ==============================================

            请以JSON格式回复:
            {"intent":"意图","params":{参数对象},"reply":"给用户的友好回复"}

            重要:
            - 优先匹配具体的模块实体意图（如QUERY_SALE_ORDER），而不是通用的QUERY_ORDER
            - 如果用户说"查一下客户"，应该返回 QUERY_BASEDATA_CUSTOMER
            - 如果用户说"新增一个供应商"，应该返回 CREATE_BASEDATA_SUPPLIER
            - 如果用户说"删除物料XX"，应该返回 DELETE_BASEDATA_MATERIAL
            - 如果用户说"修改部门名称"，应该返回 UPDATE_BASEDATA_DEPARTMENT

            示例1:
            用户: 帮我查一下所有客户
            回复: {"intent":"QUERY_BASEDATA_CUSTOMER","params":{},"reply":"好的，我来帮您查询所有客户档案"}

            示例2:
            用户: 新增一个叫张三的员工，职位是销售经理
            回复: {"intent":"CREATE_BASEDATA_EMPLOYEE","params":{"data":{"employeeName":"张三","position":"销售经理"}},"reply":"好的，我来帮您新增员工：张三，职位：销售经理。请确认是否创建？"}

            示例3:
            用户: 帮我查一下这个月的销售订单
            回复: {"intent":"QUERY_SALE_ORDER","params":{"keyword":""},"reply":"好的，我来帮您查询本月的销售订单"}

            示例4:
            用户: 删除编码为MAT-001的物料
            回复: {"intent":"DELETE_BASEDATA_MATERIAL","params":{"materialCode":"MAT-001"},"reply":"您确定要删除物料MAT-001吗？此操作不可恢复。"}
            """;

    /**
     * 获取当前可用的适配器（内置渠道 或 自定义服务商）
     */
    public AiChannelAdapter getAdapter(UnifiedRequest request) {
        return adapterResolver.resolve(request.getChannel(), request.getProviderId(), request.getModel());
    }

    private List<String> resolveImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return imageUrls;
        List<String> result = new ArrayList<>();
        for (String url : imageUrls) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                result.add(url);
            } else if (url.startsWith("/uploads/")) {
                // 安全审计 H-2：原实现缺 canonical 校验（/uploads/../../ 越界读），
                // 统一走白名单根目录校验。
                File file = uploadFileReader.resolveImageFile(url);
                String base64 = file == null ? null : fileToBase64(file.getPath());
                if (base64 != null) {
                    result.add(base64);
                }
            } else if (url.startsWith("data:image/")) {
                result.add(url);
            }
        }
        return result;
    }

    private String fileToBase64(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                log.warn("[图片转Base64] 文件不存在: {}", filePath);
                return null;
            }
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.read(bytes);
            }
            String ext = getFileExtension(filePath).toLowerCase();
            String mime = switch (ext) {
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                case "bmp" -> "image/bmp";
                default -> "image/jpeg";
            };
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.error("[图片转Base64] 失败: {}", filePath, e);
            return null;
        }
    }

    private String getFileExtension(String fileName) {
        int dotIdx = fileName.lastIndexOf('.');
        return dotIdx >= 0 ? fileName.substring(dotIdx + 1) : "";
    }

    /**
     * 处理AI请求 - 主入口
     */
    public UnifiedResponse process(UnifiedRequest request) {
        if (request.getTraceId() == null) {
            request.setTraceId(IdUtil.fastSimpleUUID());
        }
        if (request.getChannel() == null) {
            request.setChannel(aiConfigService.getDefaultChannel());
        }

        // 读取数据使用政策
        boolean maskSensitive = aiConfigService.isDataMaskEnabled();
        boolean logRawContent = aiConfigService.isLogRawContentEnabled();

        // 日志：根据政策决定是否记录原文（生产环境关闭以避免敏感信息泄漏）
        if (logRawContent) {
            log.info("[AI网关] traceId={} channel={} input={}", request.getTraceId(), request.getChannel(),
                    SensitiveDataMasker.truncate(request.getUserInput(), 200));
        } else {
            log.info("[AI网关] traceId={} channel={} inputLen={}", request.getTraceId(), request.getChannel(),
                    request.getUserInput() == null ? 0 : request.getUserInput().length());
        }

        // 限流检查
        checkRateLimit(request.getChannelUserId());

        try {
            AiChannelAdapter adapter = getAdapter(request);

            // 获取会话历史
            List<Map<String, String>> history = getHistory(request.getSessionId());

            // 图片识别处理
            String finalUserInput = request.getUserInput();
            if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
                log.info("[AI网关] 开始图片识别, 图片数量: {}", request.getImageUrls().size());
                List<String> resolvedUrls = resolveImageUrls(request.getImageUrls());
                if (resolvedUrls.isEmpty()) {
                    log.warn("[AI网关] 图片解析失败，跳过图片识别");
                } else {
                    String visionPrompt = "请仔细识别这张图片的内容，提取所有可用于ERP系统查询的关键信息。\n\n" +
                            "📋 【订单/发票/单据类图片】需要提取：\n" +
                            "- 单据编号：订单号、发票号、报价单号、合同号、发货单号、入库单号、退货单号等\n" +
                            "- 日期信息：下单日期、交货日期、开票日期、发货日期、入库日期等\n" +
                            "- 客户信息：客户名称、客户编码、联系人、电话\n" +
                            "- 供应商信息：供应商名称、供应商编码\n" +
                            "- 金额信息：总金额、单价、数量、折扣、税额、价税合计\n" +
                            "- 商品/物料信息：物料名称、物料编码、规格型号、单位\n" +
                            "\n📦 【商品/物料/库存类图片】需要提取：\n" +
                            "- 物料信息：物料名称、物料编码、规格型号、品牌、批次号、序列号\n" +
                            "- 库存信息：数量、单位、单价、金额\n" +
                            "- 仓库信息：仓库名称、仓库编码、库位、货架位置\n" +
                            "- 日期信息：生产日期、有效期至、入库日期\n" +
                            "\n👥 【客户/供应商名片类图片】需要提取：\n" +
                            "- 公司名称、公司简称\n" +
                            "- 联系人姓名、职位\n" +
                            "- 电话、手机、邮箱、地址\n" +
                            "- 客户编码、供应商编码（如果有）\n" +
                            "\n🏭 【生产/工单类图片】需要提取：\n" +
                            "- 工单号、BOM编号、工艺路线编号\n" +
                            "- 产品名称、产品编码、规格\n" +
                            "- 生产数量、完成数量\n" +
                            "- 工作中心、工位\n" +
                            "- 生产日期、预计完工日期\n" +
                            "\n请用中文清晰列出所有识别到的关键信息，格式为【字段名：值】。如果识别到多个商品或物料，请分别列出。";
                    String visionResult = adapter.visionChat(
                            "你是一个专业的ERP图片识别助手，擅长识别各类业务单据、商品标签、库存照片、客户名片等图片。你能精准提取关键信息，包括文字、数字、表格内容等，并整理成结构化数据用于ERP系统查询。请尽可能完整地识别所有可见信息。",
                            visionPrompt,
                            resolvedUrls
                    );
                    if (logRawContent) {
                        log.info("[AI网关] 图片识别结果: {}", SensitiveDataMasker.truncate(visionResult, 500));
                    } else {
                        log.info("[AI网关] 图片识别完成, 结果长度: {}", visionResult == null ? 0 : visionResult.length());
                    }

                    // 严格模式下：图片识别结果也要脱敏后再发给意图识别
                    String safeVisionResult = maskSensitive ? SensitiveDataMasker.maskAll(visionResult) : visionResult;

                    if (finalUserInput == null || finalUserInput.isBlank()) {
                        finalUserInput = "请根据图片内容帮我查询相关信息。图片识别结果如下：\n" + safeVisionResult;
                    } else {
                        finalUserInput = finalUserInput + "\n\n【图片识别结果】：\n" + safeVisionResult;
                    }
                }
            }

            // 发送给AI前对用户输入做敏感字段脱敏
            // AI仍能理解脱敏后的字段含义（如"查询客户 138****0000 的订单"），但拿不到真实数据
            String safeUserInput = maskSensitive ? SensitiveDataMasker.maskAll(finalUserInput) : finalUserInput;

            // 调用大模型识别意图（带历史对话）——结构化输出：要求模型按 JSON 返回，
            // 不支持 response_format 的渠道自动回退普通对话（提示词已要求 JSON，解析另有兜底）
            String aiResponse = adapter.chatJson(SYSTEM_PROMPT, safeUserInput, history);
            if (logRawContent) {
                log.info("[AI网关] AI原始回复: {}", SensitiveDataMasker.truncate(aiResponse, 500));
            } else {
                log.info("[AI网关] AI回复长度: {}", aiResponse == null ? 0 : aiResponse.length());
            }

            // 保存会话历史（保存脱敏后的内容，避免Redis中存敏感信息）
            saveHistory(request.getSessionId(), safeUserInput, aiResponse);

            // 解析AI返回的JSON
            Map<String, Object> parsed = parseAiResponse(aiResponse);
            String intent = (String) parsed.getOrDefault("intent", "UNKNOWN");
            Map<String, Object> params = (Map<String, Object>) parsed.getOrDefault("params", new HashMap<>());
            String reply = (String) parsed.getOrDefault("reply", "我理解了您的需求");

            log.info("[AI网关] traceId={} intent={}", request.getTraceId(), intent);

            if (request.getImageUrls() != null && !request.getImageUrls().isEmpty() && "IMAGE_SEARCH".equals(intent)) {
                params.put("imageUrl", request.getImageUrls().get(0));
                params.put("query", request.getUserInput());
            }

            UnifiedResponse response = new UnifiedResponse();
            response.setTraceId(request.getTraceId());
            response.setIntent(intent);
            response.setExtractedParams(params);
            response.setReplyText(reply);
            response.setSuccess(true);

            // 写操作需要确认
            if (isWriteOperation(intent) && writeConfirmRequired) {
                String token = RandomUtil.randomNumbers(6);
                response.setNeedConfirm(true);
                response.setConfirmToken(token);
                // 将待确认数据存入Redis, 5分钟过期
                String key = "ai:confirm:" + token;
                Map<String, Object> confirmData = new HashMap<>();
                confirmData.put("intent", intent);
                confirmData.put("params", params);
                confirmData.put("erpUserId", request.getErpUserId());
                confirmData.put("channel", request.getChannel());
                cache.set(key, mapper.writeValueAsString(confirmData), 5, TimeUnit.MINUTES);
                response.setReplyText(reply + "\n\n请输入验证码 " + token + " 确认操作");
            } else {
                // 读操作直接执行
                Object result = executeAction(intent, params, request.getErpUserId());
                if (result instanceof Map) {
                    response.setActionResult((Map<String, Object>) result);
                } else {
                    response.setActionResult(Map.of("result", result));
                }
            }

            return response;
        } catch (Exception e) {
            log.error("[AI网关] 处理失败 traceId={}", request.getTraceId(), e);
            UnifiedResponse resp = UnifiedResponse.error(e.getMessage());
            resp.setTraceId(request.getTraceId());
            return resp;
        }
    }

    /**
     * 确认并执行写操作
     */
    public UnifiedResponse confirmAndExecute(String confirmToken) {
        String key = "ai:confirm:" + confirmToken;
        String json = cache.get(key);
        if (json == null) {
            return UnifiedResponse.error("验证码无效或已过期");
        }
        try {
            Map<String, Object> data = mapper.readValue(json, Map.class);
            String intent = (String) data.get("intent");
            Map<String, Object> params = (Map<String, Object>) data.get("params");
            Long erpUserId = ((Number) data.get("erpUserId")).longValue();

            Object result = executeAction(intent, params, erpUserId);
            cache.delete(key);

            UnifiedResponse resp = UnifiedResponse.ok("操作成功完成");
            resp.setIntent(intent);
            resp.setActionResult((Map<String, Object>) result);
            return resp;
        } catch (Exception e) {
            log.error("确认执行失败", e);
            return UnifiedResponse.error(e.getMessage());
        }
    }

    /**
     * 执行业务动作
     */
    private Object executeAction(String intent, Map<String, Object> params, Long erpUserId) {
        if (aiCrudAgentService.supports(intent)) {
            if (intent.startsWith("QUERY_")) {
                // 字段权限按用户解析：有请求上下文用完整身份（含角色），否则至少带上用户 ID
                return aiCrudAgentService.query(intent, params, currentAiScope(erpUserId));
            } else if (intent.startsWith("CREATE_")) {
                return aiCrudAgentService.create(intent, params, erpUserId);
            } else if (intent.startsWith("UPDATE_")) {
                return aiCrudAgentService.update(intent, params, erpUserId);
            } else if (intent.startsWith("DELETE_")) {
                return aiCrudAgentService.delete(intent, params, erpUserId);
            }
        }

        return switch (intent) {
            case "CREATE_ORDER" -> aiOrderService.createDraftOrder(params, erpUserId);
            case "QUERY_ORDER" -> aiOrderService.queryOrders(params);
            case "QUERY_INVENTORY" -> aiInventoryService.queryInventory(params);
            case "QUERY_CUSTOMER" -> aiOrderService.queryCustomer(params);
            case "GENERATE_REPORT" -> aiReportService.generateReport(params);
            case "IMAGE_SEARCH" -> aiImageSearchService.searchByImage(
                    (String) params.get("imageUrl"),
                    (String) params.get("query")
            );
            case "FAQ" -> Map.of("answer", "这是一个业务FAQ回答");
            default -> Map.of("message", "未识别的操作类型");
        };
    }

    /** 当前请求的 AI 权限上下文；无线程请求上下文时退回只带用户 ID 的最小上下文（按受限处理） */
    private com.erp.ai.dto.AiDataScope currentAiScope(Long erpUserId) {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
            return com.erp.ai.dto.AiDataScope.from(sra.getRequest());
        }
        com.erp.ai.dto.AiDataScope scope = new com.erp.ai.dto.AiDataScope();
        scope.setUserId(erpUserId);
        scope.setRoles(Collections.emptyList());
        scope.setPermissions(Collections.emptyList());
        scope.setMenuPaths(Collections.emptyList());
        return scope;
    }

    @Resource(name = "aiOrderService")
    private AiOrderService aiOrderService;

    @Resource(name = "aiInventoryService")
    private AiInventoryService aiInventoryService;

    @Resource(name = "aiReportService")
    private AiReportService aiReportService;

    @Resource(name = "aiCrudAgentService")
    private AiCrudAgentService aiCrudAgentService;

    @Resource
    private AiImageSearchService aiImageSearchService;

    private boolean isWriteOperation(String intent) {
        if (intent == null) return false;
        return "CREATE_ORDER".equals(intent)
            || intent.startsWith("CREATE_")
            || intent.startsWith("UPDATE_")
            || intent.startsWith("DELETE_");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAiResponse(String aiResponse) {
        try {
            // 尝试提取JSON部分
            String json = aiResponse.trim();
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7, json.lastIndexOf("```"));
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("```") + 3, json.lastIndexOf("```"));
            }
            json = json.trim();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("AI返回非JSON格式, 原文: {}", aiResponse);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("intent", "FAQ");
            fallback.put("reply", aiResponse);
            return fallback;
        }
    }

    private void checkRateLimit(String userId) {
        if (userId == null) userId = "anonymous";
        String minuteKey = "ai:rate:m:" + userId;
        String dayKey = "ai:rate:d:" + userId;

        Long minuteCount = cache.increment(minuteKey);
        Long dayCount = cache.increment(dayKey);
        if (minuteCount == 1) cache.expire(minuteKey, 60, TimeUnit.SECONDS);
        if (dayCount == 1) cache.expire(dayKey, 1, TimeUnit.DAYS);

        if (minuteCount > 5) {
            throw new BusinessException("请求过于频繁, 每分钟限5次");
        }
        if (dayCount > 200) {
            throw new BusinessException("今日AI请求次数已达上限(200次)");
        }
    }
}
