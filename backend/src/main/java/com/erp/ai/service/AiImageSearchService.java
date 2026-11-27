package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.ai.util.AiModelContextHolder;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.stream.Collectors;
import com.erp.common.util.SortHelper;

@Slf4j
@Service
public class AiImageSearchService {

    @Resource
    private MaterialService materialService;

    @Resource
    private com.erp.ai.service.AiConfigService aiConfigService;

    @Resource
    private List<com.erp.ai.gateway.AiChannelAdapter> adapters;

    @Resource
    private SaleOrderMapper saleOrderMapper;

    @Resource
    private SaleOrderItemMapper saleOrderItemMapper;

    /** 路径安全统一入口：upload 根 + 采集截图目录白名单校验（安全审计 H-2） */
    @Resource
    private com.erp.ai.tool.support.AiUploadFileReader uploadFileReader;

    public Map<String, Object> searchByImage(String imageUrl, String userQuery) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> matchedMaterials = new ArrayList<>();

        try {
            String resolvedImageUrl = resolveSingleImageUrl(imageUrl);
            if (resolvedImageUrl == null) {
                result.put("success", false);
                result.put("message", "图片解析失败");
                return result;
            }

            // 检查AI渠道可用性
            String aiStatus = checkAiAvailability();
            if (!"OK".equals(aiStatus)) {
                log.warn("[以图识图] AI服务不可用，使用降级模式: {}", aiStatus);
                return fallbackSearch(result, resolvedImageUrl, userQuery, aiStatus);
            }

            String recognitionResult;
            try {
                recognitionResult = recognizeImage(resolvedImageUrl);
            } catch (Exception e) {
                // 不再把异常吞成"空结果"：把真实原因（模型不支持图片/模型不存在/Key无效等）交给上层提示用户
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("[以图识图] 图片识别失败: {}", reason);
                return fallbackSearch(result, resolvedImageUrl, userQuery, reason);
            }
            log.info("[以图识图] 图片识别结果: {}", recognitionResult);

            if (recognitionResult == null || recognitionResult.isBlank()) {
                log.warn("[以图识图] 图片识别返回空，使用降级搜索");
                return fallbackSearch(result, resolvedImageUrl, userQuery, "模型没有返回识别内容");
            }

            List<Material> candidates = searchCandidateMaterials(recognitionResult);
            log.info("[以图识图] 初筛候选物料数量: {}", candidates.size());

            if (candidates.isEmpty()) {
                result.put("success", true);
                result.put("matched", false);
                result.put("message", "未找到匹配的物料");
                result.put("recognitionResult", recognitionResult);
                result.put("materials", Collections.emptyList());
                result.put("documents", Collections.emptyList());
                return result;
            }

            for (Material mat : candidates) {
                if (mat.getMainImage() == null || mat.getMainImage().isBlank()) {
                    continue;
                }
                String matImageUrl = resolveSingleImageUrl(mat.getMainImage());
                if (matImageUrl == null) continue;

                double similarity = calculateSimilarity(resolvedImageUrl, matImageUrl);
                log.info("[以图识图] 物料 {} ({}) 相似度: {}%", mat.getMaterialName(), mat.getMaterialCode(), similarity);

                Map<String, Object> matInfo = new HashMap<>();
                matInfo.put("materialId", mat.getMaterialId());
                matInfo.put("materialCode", mat.getMaterialCode());
                matInfo.put("materialName", mat.getMaterialName());
                matInfo.put("model", mat.getModel());
                matInfo.put("mainImage", mat.getMainImage());
                matInfo.put("similarity", similarity);
                matchedMaterials.add(matInfo);
            }

            matchedMaterials.sort((a, b) -> Double.compare((Double) b.get("similarity"), (Double) a.get("similarity")));

            if (matchedMaterials.size() > 5) {
                matchedMaterials = matchedMaterials.subList(0, 5);
            }

            List<Map<String, Object>> relatedDocs = new ArrayList<>();
            if (!matchedMaterials.isEmpty()) {
                Long topMaterialId = (Long) matchedMaterials.get(0).get("materialId");
                relatedDocs = findRelatedDocuments(topMaterialId);
            }

            double topSimilarity = matchedMaterials.isEmpty() ? 0 : (Double) matchedMaterials.get(0).get("similarity");
            String accuracyLevel = getAccuracyLevel(topSimilarity);

            result.put("success", true);
            result.put("matched", !matchedMaterials.isEmpty());
            result.put("recognitionResult", recognitionResult);
            result.put("materials", matchedMaterials);
            result.put("documents", relatedDocs);
            result.put("topSimilarity", topSimilarity);
            result.put("accuracyLevel", accuracyLevel);
            result.put("totalMatched", matchedMaterials.size());

        } catch (Exception e) {
            log.error("[以图识图] 搜索失败", e);
            result.put("success", false);
            result.put("message", "图片搜索失败: " + e.getMessage());
        }

        return result;
    }

    /** 用"本次所选模型"识别图片；失败时抛出带真实原因的异常，由调用方转成用户可读提示 */
    private String recognizeImage(String imageUrl) {
        AiChannelAdapter adapter = getAdapter();
        String prompt = "请仔细识别这张图片中的物品，提取以下关键信息：\n" +
                "1. 物品名称：这是什么物品（如：螺丝、螺母、螺栓、轴承等）\n" +
                "2. 规格型号：尺寸、规格、型号等信息\n" +
                "3. 颜色特征：物品的颜色、材质外观\n" +
                "4. 品牌标识：如果有品牌logo或文字\n" +
                "5. 其他特征：形状、用途等可用于搜索的特征\n\n" +
                "请用中文清晰描述，格式为【字段名：值】。";
        return adapter.visionChat(
                "你是一个专业的工业产品识别助手，擅长识别五金、机械、电子等物料产品。你能从图片中准确提取产品名称、规格、颜色、材质等特征信息。",
                prompt,
                Collections.singletonList(imageUrl)
        );
    }

    private List<Material> searchCandidateMaterials(String recognitionResult) {
        try {
            QueryWrapper<Material> qw = new QueryWrapper<>();
            qw.in("status", "启用", "正常");
            qw.isNotNull("main_image");
            qw.ne("main_image", "");
            qw.and(w -> w.ne("is_confidential", 1).or().isNull("is_confidential"));

            String[] keywords = extractKeywords(recognitionResult);
            if (keywords.length > 0) {
                qw.and(w -> {
                    for (String kw : keywords) {
                        if (kw != null && kw.length() >= 2) {
                            w.or().like("material_name", kw)
                                    .or().like("full_name", kw)
                                    .or().like("model", kw)
                                    .or().like("material_code", kw);
                        }
                    }
                });
            }

            qw.last("LIMIT 20");
            List<Material> result = materialService.list(qw);

            if (result.isEmpty()) {
                log.info("[以图识图] 关键词搜索无结果，返回所有有图片的物料进行相似度比较");
                QueryWrapper<Material> qw2 = new QueryWrapper<>();
                qw2.in("status", "启用", "正常");
                qw2.isNotNull("main_image");
                qw2.ne("main_image", "");
                qw2.and(w -> w.ne("is_confidential", 1).or().isNull("is_confidential"));
                qw2.last("LIMIT 20");
                result = materialService.list(qw2);
            }

            return result;
        } catch (Exception e) {
            log.warn("[以图识图] 候选物料搜索失败", e);
            return Collections.emptyList();
        }
    }

    private String[] extractKeywords(String text) {
        if (text == null || text.isBlank()) return new String[0];
        List<String> keywords = new ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains("：")) {
                String value = line.substring(line.indexOf("：") + 1).trim();
                value = value.replace("【", "").replace("】", "").trim();
                if (value.length() >= 2 && value.length() <= 20) {
                    keywords.add(value);
                }
            }
        }
        return keywords.toArray(new String[0]);
    }

    private double calculateSimilarity(String imageUrl1, String imageUrl2) {
        try {
            AiChannelAdapter adapter = getAdapter();
            String prompt = "请对比这两张图片中的物品，判断它们是否是同一种物料。\n\n" +
                    "请从以下维度进行对比：\n" +
                    "1. 物品类型：是否是同一类产品\n" +
                    "2. 外观形状：形状、结构是否一致\n" +
                    "3. 颜色材质：颜色、材质外观是否相似\n" +
                    "4. 规格尺寸：相对大小、比例是否一致\n\n" +
                    "请只回复一个0到100之间的数字，表示相似度百分比。" +
                    "完全相同为100，非常相似为80-99，部分相似为50-79，完全不同为0-49。" +
                    "只回复数字，不要有任何其他文字。";

            List<String> images = Arrays.asList(imageUrl1, imageUrl2);
            String result = adapter.visionChat(
                    "你是一个专业的产品图片对比助手，擅长判断两张图片中的物品是否为同一种产品。你能从形状、颜色、材质、规格等多个维度进行准确对比。",
                    prompt,
                    images
            );

            result = result.trim().replaceAll("[^0-9.]", "");
            if (result.isBlank()) return 30.0;
            double score = Double.parseDouble(result);
            return Math.min(100, Math.max(0, score));
        } catch (Exception e) {
            log.warn("[以图识图] 相似度计算失败", e);
            return 30.0;
        }
    }

    private List<Map<String, Object>> findRelatedDocuments(Long materialId) {
        List<Map<String, Object>> docs = new ArrayList<>();
        try {
            QueryWrapper<SaleOrderItem> itemQw = new QueryWrapper<>();
            itemQw.eq("material_id", materialId);
            SortHelper.orderByNewest(itemQw, SaleOrderItem.class);
            itemQw.last("LIMIT 10");
            List<SaleOrderItem> items = saleOrderItemMapper.selectList(itemQw);

            if (!items.isEmpty()) {
                Set<Long> orderIds = items.stream()
                        .map(SaleOrderItem::getOrderId)
                        .collect(Collectors.toSet());
                if (!orderIds.isEmpty()) {
                    List<SaleOrder> orders = saleOrderMapper.selectBatchIds(orderIds);
                    Map<Long, SaleOrder> orderMap = orders.stream()
                            .collect(Collectors.toMap(SaleOrder::getOrderId, o -> o));
                    for (SaleOrderItem item : items) {
                        SaleOrder order = orderMap.get(item.getOrderId());
                        if (order != null) {
                            Map<String, Object> doc = new HashMap<>();
                            doc.put("documentType", "销售订单");
                            doc.put("documentNo", order.getOrderNo());
                            doc.put("documentId", order.getOrderId());
                            doc.put("orderDate", order.getOrderDate());
                            doc.put("customerName", order.getCustomerName());
                            doc.put("quantity", item.getQuantity());
                            doc.put("unitPrice", item.getUnitPrice());
                            doc.put("amount", item.getNetAmount());
                            doc.put("status", order.getStatus());
                            docs.add(doc);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[以图识图] 查询相关单据失败", e);
        }
        return docs;
    }

    private String getAccuracyLevel(double similarity) {
        if (similarity >= 90) return "非常准确";
        if (similarity >= 75) return "高";
        if (similarity >= 60) return "中等";
        if (similarity >= 40) return "较低";
        return "低";
    }

    private String resolveSingleImageUrl(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        } else if (url.startsWith("/uploads/")) {
            // 安全审计 H-2：原实现直接拼 uploadPath + relativePath，缺 canonical 校验
            // （/uploads/../../ 可越界读任意文件）；统一走白名单根目录校验。
            File file = uploadFileReader.resolveImageFile(url);
            return file == null ? null : fileToBase64(file.getPath());
        } else if (url.startsWith("data:image/")) {
            return url;
        }
        return null;
    }

    private String fileToBase64(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return null;
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
            log.warn("[以图识图] 图片转Base64失败: {}", filePath, e);
            return null;
        }
    }

    private String getFileExtension(String fileName) {
        int dotIdx = fileName.lastIndexOf('.');
        return dotIdx >= 0 ? fileName.substring(dotIdx + 1) : "";
    }

    private AiChannelAdapter getAdapter() {
        // 优先用"用户本次所选模型"（用户 2026-09-28：我选那个模型就给我用那个模型，不走固定后台通道）
        AiChannelAdapter ctx = AiModelContextHolder.get();
        if (ctx != null && ctx.isEnabled()) {
            return ctx;
        }
        // 无请求上下文时（无界面的后台调用）仍按默认通道，行为与升级前一致
        String defaultCh = aiConfigService.getDefaultChannel();
        for (AiChannelAdapter a : adapters) {
            if (a.getChannel().equalsIgnoreCase(defaultCh) && a.isEnabled()) {
                return a;
            }
        }
        for (AiChannelAdapter a : adapters) {
            if (a.isEnabled()) return a;
        }
        throw new RuntimeException("没有可用的AI渠道");
    }

    /**
     * 检查本次所选模型是否可用
     * @return "OK"表示可用，其他字符串表示不可用原因
     */
    private String checkAiAvailability() {
        try {
            AiChannelAdapter adapter = getAdapter();
            if (!adapter.isEnabled()) {
                return "所选模型未配置 API Key";
            }
            return "OK";
        } catch (Exception e) {
            log.warn("[以图识图] 检查AI可用性异常", e);
            return e.getMessage() == null ? "未找到可用模型" : e.getMessage();
        }
    }

    /**
     * 降级搜索模式：当AI服务不可用时，从用户查询文本中提取关键词搜索
     */
    private Map<String, Object> fallbackSearch(Map<String, Object> result, String imageUrl, String userQuery, String reason) {
        String modelName = AiModelContextHolder.getModelName();
        String modelText = (modelName == null) ? "当前所选模型" : "当前所选模型（" + modelName + "）";
        result.put("success", true);
        result.put("matched", false);
        result.put("message", modelText + "无法识别图片：" + reason
                + "。请点输入框右下角的模型选择换一个支持图片的模型（DeepSeek 用 deepseek-flash），"
                + "或直接把物料编码/名称告诉我，我按文字帮你查。");
        result.put("recognitionResult", "（AI识别不可用）");
        result.put("materials", Collections.emptyList());
        result.put("documents", Collections.emptyList());
        result.put("topSimilarity", 0.0);
        result.put("accuracyLevel", "低");
        result.put("totalMatched", 0);
        result.put("fallbackReason", reason);
        return result;
    }
}
