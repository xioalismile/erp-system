package com.erp.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.service.AiVisionService;
import com.erp.ai.tool.AiTool;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.BomService;
import com.erp.masterdata.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 图片识别BOM并创建工具
 * 流程：识别BOM图片/图纸 → AI解析父子件结构 → 匹配物料库 → 创建BOM
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageBomTool implements AiTool {

    private final AiVisionService aiVisionService;
    private final BomService bomService;
    private final MaterialService materialService;

    @Override
    public String getName() {
        return "image_create_bom";
    }

    @Override
    public String getDescription() {
        return "图片识别BOM：识别用户上传的BOM表/产品结构图，解析父子件层级关系，匹配物料库后创建BOM单据。" +
                "适用场景：用户上传BOM图片并说'录入BOM'、'识别这张BOM图'、'帮我把图纸转成BOM'。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> imageUrl = new LinkedHashMap<>();
        imageUrl.put("type", "string");
        imageUrl.put("description", "BOM图片或产品图纸的URL");
        props.put("imageUrl", imageUrl);

        Map<String, Object> userQuery = new LinkedHashMap<>();
        userQuery.put("type", "string");
        userQuery.put("description", "用户的原始请求，可能包含父件名、版本等信息");
        props.put("userQuery", userQuery);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("imageUrl"));
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        String imageUrl = (String) params.get("imageUrl");
        String userQuery = (String) params.getOrDefault("userQuery", "");

        try {
            log.info("[图片BOM] 开始处理 imageUrl={}, query={}", imageUrl, userQuery);

            // 1. 识别图片中的BOM结构
            String recognitionResult = aiVisionService.recognizeImage(imageUrl, buildBomRecognitionPrompt());
            if (recognitionResult == null || recognitionResult.isBlank()) {
                return Map.of("success", false, "message", "图片识别失败，请确保图片清晰且包含BOM信息");
            }
            log.info("[图片BOM] 识别结果: {}", recognitionResult);

            // 2. 用AI解析BOM结构（提取父子件关系）
            List<BomRow> bomRows = parseBomStructure(recognitionResult);
            if (bomRows.isEmpty()) {
                return Map.of(
                        "success", false,
                        "message", "未能从图片中解析出BOM结构，请确认图片是BOM表或产品结构图",
                        "recognitionResult", recognitionResult
                );
            }
            log.info("[图片BOM] 解析出{}行BOM数据", bomRows.size());

            // 3. 匹配物料库
            MatchResult matchResult = matchMaterialsForBom(bomRows);
            if (matchResult.parentMaterial == null) {
                return Map.of(
                        "success", false,
                        "message", "未找到父件物料，请先在物料档案中添加父件: " + bomRows.get(0).parentName,
                        "recognitionResult", recognitionResult,
                        "missingParent", bomRows.get(0).parentName
                );
            }

            // 4. 构建BOM对象
            Bom bom = new Bom();
            bom.setMaterialId(matchResult.parentMaterial.getMaterialId());
            bom.setMaterialCode(matchResult.parentMaterial.getMaterialCode());
            bom.setMaterialName(matchResult.parentMaterial.getMaterialName());
            bom.setSpecification(matchResult.parentMaterial.getModel());
            bom.setBomDate(LocalDate.now());
            bom.setBomName(matchResult.parentMaterial.getMaterialName() + "-V1.0");
            bom.setBomVersion("V1.0");
            bom.setBomCategory("标准");
            bom.setBomType("标准BOM");
            bom.setIsPrimary((short) 1);
            bom.setIsDefault((short) 1);
            bom.setIsEnabled((short) 1);
            bom.setStatus("草稿");
            bom.setApprovalStatus("待审核");
            bom.setQuantity(BigDecimal.ONE);
            if (userId != null) {
                bom.setCreatedBy(userId.toString());
            }

            // 5. 构建BOM明细
            List<BomItem> items = new ArrayList<>();
            int lineNo = 10;
            for (int i = 0; i < matchResult.matchedItems.size(); i++) {
                MatchedItem mi = matchResult.matchedItems.get(i);
                if (mi.material == null) continue; // 跳过未匹配的

                BomItem item = new BomItem();
                item.setMaterialId(mi.material.getMaterialId());
                item.setMaterialCode(mi.material.getMaterialCode());
                item.setMaterialName(mi.material.getMaterialName());
                item.setModelNo(mi.material.getModel());
                item.setQuantity(mi.quantity != null ? mi.quantity : BigDecimal.ONE);
                item.setScrapRate(BigDecimal.ZERO);
                item.setSupplyType("领料");
                item.setIsKeyPart((short) 0);
                items.add(item);
                lineNo += 10;
            }

            if (items.isEmpty()) {
                return Map.of(
                        "success", false,
                        "message", "所有子件都未在物料库中找到匹配，请先添加物料",
                        "recognitionResult", recognitionResult,
                        "missingMaterials", matchResult.missingMaterialNames
                );
            }

            // 6. 保存BOM
            bomService.saveBomWithItems(bom, items);

            // 7. 构建返回结果
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("bomCode", bom.getBomCode());
            result.put("bomName", bom.getBomName());
            result.put("parentMaterial", Map.of(
                    "materialCode", matchResult.parentMaterial.getMaterialCode(),
                    "materialName", matchResult.parentMaterial.getMaterialName()
            ));
            result.put("itemCount", items.size());
            result.put("missingMaterialCount", matchResult.missingMaterialNames.size());
            if (!matchResult.missingMaterialNames.isEmpty()) {
                result.put("missingMaterials", matchResult.missingMaterialNames);
            }
            result.put("recognitionResult", recognitionResult);
            result.put("summary", String.format("BOM创建成功：%s，父件：%s，共%d个子件，%d个未匹配物料",
                    bom.getBomCode(), matchResult.parentMaterial.getMaterialName(),
                    items.size(), matchResult.missingMaterialNames.size()));

            log.info("[图片BOM] 创建成功: {}", bom.getBomCode());
            return result;

        } catch (Exception e) {
            log.error("[图片BOM] 执行失败", e);
            return Map.of("success", false, "message", "图片BOM创建失败: " + e.getMessage());
        }
    }

    private String buildBomRecognitionPrompt() {
        return "请仔细识别这张图片中的BOM（物料清单）信息，提取所有父子件关系。\n" +
                "请按以下格式输出每一行：\n" +
                "父件名称：xxx | 子件名称：xxx | 用量：xxx | 单位：xxx\n" +
                "如果有多行，每行一条。如果图片中有层级关系，请标注层级。\n" +
                "示例：\n" +
                "父件名称：电脑主机 | 子件名称：主板 | 用量：1 | 单位：个\n" +
                "父件名称：电脑主机 | 子件名称：CPU | 用量：1 | 单位：个\n" +
                "父件名称：主板 | 子件名称：电容 | 用量：10 | 单位：个\n" +
                "\n请只输出识别到的BOM行，不要输出其他说明文字。";
    }

    /**
     * 从AI识别结果中解析BOM结构
     */
    private List<BomRow> parseBomStructure(String recognitionResult) {
        List<BomRow> rows = new ArrayList<>();
        if (recognitionResult == null || recognitionResult.isBlank()) return rows;

        String[] lines = recognitionResult.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            // 解析格式：父件名称：xxx | 子件名称：xxx | 用量：xxx | 单位：xxx
            if (line.contains("|") && line.contains("：")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 2) {
                    BomRow row = new BomRow();
                    for (String part : parts) {
                        part = part.trim();
                        if (part.startsWith("父件名称：") || part.startsWith("父件名称:")) {
                            row.parentName = part.substring(part.indexOf("：") + 1).trim();
                        } else if (part.startsWith("子件名称：") || part.startsWith("子件名称:")) {
                            row.childName = part.substring(part.indexOf("：") + 1).trim();
                        } else if (part.startsWith("用量：") || part.startsWith("用量:")) {
                            String qtyStr = part.substring(part.indexOf("：") + 1).trim();
                            try { row.quantity = new BigDecimal(qtyStr); } catch (Exception ignored) {}
                        } else if (part.startsWith("单位：") || part.startsWith("单位:")) {
                            row.unit = part.substring(part.indexOf("：") + 1).trim();
                        }
                    }
                    if (row.parentName != null && row.childName != null) {
                        rows.add(row);
                    }
                }
            }
        }
        return rows;
    }

    private MatchResult matchMaterialsForBom(List<BomRow> bomRows) {
        MatchResult result = new MatchResult();

        // 找到父件（第一行的parentName）
        if (!bomRows.isEmpty()) {
            String parentName = bomRows.get(0).parentName;
            result.parentMaterial = findMaterialByName(parentName);
            if (result.parentMaterial == null) {
                result.missingMaterialNames.add(parentName);
            }
        }

        // 匹配子件
        for (BomRow row : bomRows) {
            MatchedItem mi = new MatchedItem();
            mi.childName = row.childName;
            mi.quantity = row.quantity;
            mi.material = findMaterialByName(row.childName);
            if (mi.material == null) {
                result.missingMaterialNames.add(row.childName);
            }
            result.matchedItems.add(mi);
        }
        return result;
    }

    private Material findMaterialByName(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            QueryWrapper<Material> qw = new QueryWrapper<>();
            qw.in("status", "启用", "正常");
            qw.and(w -> w.like("material_name", name).or().like("full_name", name));
            qw.last("LIMIT 1");
            return materialService.getOne(qw);
        } catch (Exception e) {
            return null;
        }
    }

    // ===== 内部数据类 =====
    private static class BomRow {
        String parentName;
        String childName;
        BigDecimal quantity = BigDecimal.ONE;
        String unit;
    }

    private static class MatchResult {
        Material parentMaterial;
        List<MatchedItem> matchedItems = new ArrayList<>();
        List<String> missingMaterialNames = new ArrayList<>();
    }

    private static class MatchedItem {
        String childName;
        BigDecimal quantity;
        Material material;
    }
}
