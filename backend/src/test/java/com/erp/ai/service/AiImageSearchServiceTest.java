package com.erp.ai.service;

import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

/**
 * AI以图识图服务测试 - 使用Mock避免消耗真实AI Token
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiImageSearchServiceTest {

    @InjectMocks
    private AiImageSearchService aiImageSearchService;

    @Mock
    private MaterialService materialService;

    @Mock
    private AiConfigService aiConfigService;

    @Mock
    private AiChannelAdapter adapter;

    @Mock
    private SaleOrderMapper saleOrderMapper;

    @Mock
    private SaleOrderItemMapper saleOrderItemMapper;

    private File testUploadDir;

    @BeforeEach
    void setUp() throws IOException {
        ReflectionTestUtils.setField(aiImageSearchService, "aiConfigService", aiConfigService);
        ReflectionTestUtils.setField(aiImageSearchService, "adapters", List.of(adapter));
        ReflectionTestUtils.setField(aiImageSearchService, "uploadPath", "D:/erp-files");

        testUploadDir = Files.createTempDirectory("erp-test").toFile();
        ReflectionTestUtils.setField(aiImageSearchService, "uploadPath", testUploadDir.getAbsolutePath());

        when(adapter.getChannel()).thenReturn("QWEN");
        when(adapter.isEnabled()).thenReturn(true);
        when(aiConfigService.getDefaultChannel()).thenReturn("QWEN");
        when(aiConfigService.getChannelApiKey("QWEN")).thenReturn("sk-valid-api-key-for-test");
        when(aiConfigService.validateApiKey(eq("QWEN"), anyString())).thenReturn(null);
    }

    @Test
    void testSearchByImage_Success() throws IOException {
        // 准备测试图片
        File imageDir = new File(testUploadDir, "ai-chat/2026/07/08");
        imageDir.mkdirs();
        File testImage = new File(imageDir, "test-screw.png");
        byte[] fakeImageBytes = createMinimalPng();
        Files.write(testImage.toPath(), fakeImageBytes);

        // 物料主图
        File materialDir = new File(testUploadDir, "material/2026/07/08");
        materialDir.mkdirs();
        File materialImage = new File(materialDir, "screw.png");
        Files.write(materialImage.toPath(), fakeImageBytes);

        String userImageUrl = "/uploads/ai-chat/2026/07/08/test-screw.png";
        String materialImageUrl = "/uploads/material/2026/07/08/screw.png";

        Material material = new Material();
        material.setMaterialId(1L);
        material.setMaterialCode("SCREW-001");
        material.setMaterialName("镀锌十字螺丝");
        material.setModel("M4*20");
        material.setStatus("启用");
        material.setMainImage(materialImageUrl);

        SaleOrderItem item = new SaleOrderItem();
        item.setOrderId(100L);
        item.setQuantity(new BigDecimal("100"));
        item.setNetAmount(new BigDecimal("500.00"));

        SaleOrder order = new SaleOrder();
        order.setOrderId(100L);
        order.setOrderNo("SO-20260708-0001");
        order.setCustomerName("五金客户A");

        // Mock AI识别结果
        when(adapter.visionChat(
                contains("工业产品识别"),
                contains("物品名称"),
                argThat(list -> list != null && list.size() == 1)
        )).thenReturn("物品名称：十字螺丝\n规格型号：M4*20\n颜色特征：镀锌银色\n品牌标识：无\n其他特征：用于五金固定");

        // Mock 相似度对比结果
        when(adapter.visionChat(
                contains("产品图片对比"),
                contains("相似度百分比"),
                argThat(list -> list != null && list.size() == 2)
        )).thenReturn("92");

        when(materialService.list(any(Wrapper.class))).thenReturn(List.of(material));
        when(saleOrderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(saleOrderMapper.selectBatchIds(any())).thenReturn(List.of(order));

        Map<String, Object> result = aiImageSearchService.searchByImage(userImageUrl, "找一下这个物料");

        assertTrue((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("matched"));
        assertEquals(92.0, (Double) result.get("topSimilarity"), 0.01);
        assertEquals("非常准确", result.get("accuracyLevel"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> materials = (List<Map<String, Object>>) result.get("materials");
        assertEquals(1, materials.size());
        assertEquals("SCREW-001", materials.get(0).get("materialCode"));
        assertEquals(92.0, materials.get(0).get("similarity"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = (List<Map<String, Object>>) result.get("documents");
        assertEquals(1, documents.size());
        assertEquals("SO-20260708-0001", documents.get(0).get("documentNo"));
        assertEquals("五金客户A", documents.get(0).get("customerName"));
    }

    @Test
    void testSearchByImage_NoCandidateMaterials() throws IOException {
        // 创建测试图片文件
        File imageDir = new File(testUploadDir, "ai-chat/2026/07/08");
        imageDir.mkdirs();
        File testImage = new File(imageDir, "test-no-match.png");
        Files.write(testImage.toPath(), createMinimalPng());

        when(adapter.visionChat(any(), any(), any()))
                .thenReturn("物品名称：未知物品\n规格型号：未知");
        when(materialService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> result = aiImageSearchService.searchByImage("/uploads/ai-chat/2026/07/08/test-no-match.png", "找一下");

        assertTrue((Boolean) result.get("success"));
        assertFalse((Boolean) result.get("matched"));
        assertEquals("未找到匹配的物料", result.get("message"));
    }

    @Test
    void testSearchByImage_ImageResolveFailed() {
        Map<String, Object> result = aiImageSearchService.searchByImage("invalid-url", "找一下");

        assertFalse((Boolean) result.get("success"));
        assertEquals("图片解析失败", result.get("message"));
    }

    @Test
    void testSearchByImage_MaterialWithoutImage() {
        when(adapter.visionChat(any(), any(), any()))
                .thenReturn("物品名称：螺丝\n规格型号：M4");

        Material material = new Material();
        material.setMaterialId(1L);
        material.setMaterialCode("NO-IMAGE-001");
        material.setMaterialName("无图物料");
        material.setStatus("启用");
        material.setMainImage(null);

        when(materialService.list(any(Wrapper.class))).thenReturn(List.of(material));

        Map<String, Object> result = aiImageSearchService.searchByImage("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", "找一下");

        assertTrue((Boolean) result.get("success"));
        assertFalse((Boolean) result.get("matched"));
    }

    @Test
    void testSearchByImage_MultipleCandidatesSortedBySimilarity() {
        String base64Image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

        // 第一次调用(识别)返回1张图片的结果，后续调用(相似度对比)返回2张图片的结果
        when(adapter.visionChat(any(), any(), argThat(list -> list != null && list.size() == 1)))
                .thenReturn("物品名称：螺丝\n规格型号：M4");
        when(adapter.visionChat(any(), any(), argThat(list -> list != null && list.size() == 2)))
                .thenReturn("85", "62", "95");

        Material mat1 = createMaterial(1L, "SCREW-001", "螺丝A", "M4", base64Image);
        Material mat2 = createMaterial(2L, "SCREW-002", "螺丝B", "M5", base64Image);
        Material mat3 = createMaterial(3L, "SCREW-003", "螺丝C", "M6", base64Image);

        when(materialService.list(any(Wrapper.class))).thenReturn(List.of(mat1, mat2, mat3));

        Map<String, Object> result = aiImageSearchService.searchByImage(base64Image, "找一下");

        assertTrue((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("matched"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> materials = (List<Map<String, Object>>) result.get("materials");
        assertEquals(3, materials.size());
        // 验证按相似度降序排列
        assertEquals("SCREW-003", materials.get(0).get("materialCode"));
        assertEquals(95.0, materials.get(0).get("similarity"));
        assertEquals("SCREW-001", materials.get(1).get("materialCode"));
        assertEquals(85.0, materials.get(1).get("similarity"));
        assertEquals("SCREW-002", materials.get(2).get("materialCode"));
        assertEquals(62.0, materials.get(2).get("similarity"));
    }

    @Test
    void testSearchByImage_NoRelatedDocuments() throws IOException {
        File imageDir = new File(testUploadDir, "ai-chat/2026/07/08");
        imageDir.mkdirs();
        File testImage = new File(imageDir, "test-no-docs.png");
        Files.write(testImage.toPath(), createMinimalPng());

        File materialDir = new File(testUploadDir, "material/2026/07/08");
        materialDir.mkdirs();
        File materialImage = new File(materialDir, "screw.png");
        Files.write(materialImage.toPath(), createMinimalPng());

        Material material = createMaterial(1L, "SCREW-001", "镀锌十字螺丝", "M4*20", "/uploads/material/2026/07/08/screw.png");

        when(adapter.visionChat(any(), any(), any())).thenReturn("物品名称：十字螺丝\n规格型号：M4*20");
        when(materialService.list(any(Wrapper.class))).thenReturn(List.of(material));
        when(saleOrderItemMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> result = aiImageSearchService.searchByImage("/uploads/ai-chat/2026/07/08/test-no-docs.png", "找一下");

        assertTrue((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("matched"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = (List<Map<String, Object>>) result.get("documents");
        assertTrue(documents.isEmpty());
    }

    @Test
    void testSearchByImage_CalculateSimilarityInvalidResponse() {
        String base64Image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

        when(adapter.visionChat(any(), any(), argThat(list -> list != null && list.size() == 1)))
                .thenReturn("物品名称：螺丝\n规格型号：M4");
        when(adapter.visionChat(any(), any(), argThat(list -> list != null && list.size() == 2)))
                .thenReturn("无法判断相似度"); // 非数字响应

        Material material = createMaterial(1L, "SCREW-001", "螺丝", "M4", base64Image);
        when(materialService.list(any(Wrapper.class))).thenReturn(List.of(material));

        Map<String, Object> result = aiImageSearchService.searchByImage(base64Image, "找一下");

        assertTrue((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("matched"));
        // 非数字响应时默认返回30.0，30%属于低准确度(<40)
        assertEquals(30.0, (Double) result.get("topSimilarity"), 0.01);
        assertEquals("低", result.get("accuracyLevel"));
    }

    @Test
    void testSearchByImage_RecognitionReturnsEmpty() {
        when(adapter.visionChat(any(), any(), any())).thenReturn("");
        when(materialService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> result = aiImageSearchService.searchByImage(
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
                "找一下"
        );

        assertTrue((Boolean) result.get("success"));
        assertFalse((Boolean) result.get("matched"));
    }

    @Test
    void testSearchByImage_SimilarityLowerThan40() {
        String base64Image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

        when(adapter.visionChat(any(), any(), argThat(list -> list != null && list.size() == 1)))
                .thenReturn("物品名称：螺丝\n规格型号：M4");
        when(adapter.visionChat(any(), any(), argThat(list -> list != null && list.size() == 2)))
                .thenReturn("25.5");

        Material material = createMaterial(1L, "SCREW-001", "螺丝", "M4", base64Image);
        when(materialService.list(any(Wrapper.class))).thenReturn(List.of(material));

        Map<String, Object> result = aiImageSearchService.searchByImage(base64Image, "找一下");

        assertTrue((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("matched"));
        assertEquals(25.5, (Double) result.get("topSimilarity"), 0.01);
        assertEquals("低", result.get("accuracyLevel"));
    }

    @Test
    void testSearchByImage_CandidateQueryFailureGracefulDegradation() {
        String base64Image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        when(adapter.visionChat(any(), any(), any())).thenReturn("物品名称：螺丝\n规格型号：M4");
        when(materialService.list(any(Wrapper.class))).thenThrow(new RuntimeException("数据库查询异常"));

        Map<String, Object> result = aiImageSearchService.searchByImage(base64Image, "找一下");

        assertTrue((Boolean) result.get("success"));
        assertFalse((Boolean) result.get("matched"));
        assertEquals("未找到匹配的物料", result.get("message"));
    }

    @Test
    void testSearchByImage_AiServiceNotConfigured() {
        String base64Image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        when(aiConfigService.getDefaultChannel()).thenReturn("QWEN");
        when(aiConfigService.getChannelApiKey("QWEN")).thenReturn(null);

        Map<String, Object> result = aiImageSearchService.searchByImage(base64Image, "找一下");

        assertTrue((Boolean) result.get("success"));
        assertFalse((Boolean) result.get("matched"));
        assertTrue(((String) result.get("message")).contains("AI服务暂时不可用"));
        assertEquals("未配置API Key", result.get("fallbackReason"));
        assertEquals(0.0, result.get("topSimilarity"));
        assertEquals("低", result.get("accuracyLevel"));
    }

    @Test
    void testSearchByImage_AiApiKeyInvalidFormat() {
        String base64Image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        when(aiConfigService.getDefaultChannel()).thenReturn("QWEN");
        when(aiConfigService.getChannelApiKey("QWEN")).thenReturn("invalid-key-without-sk-prefix");
        when(aiConfigService.validateApiKey("QWEN", "invalid-key-without-sk-prefix"))
                .thenReturn("通义千问API Key必须以 sk- 开头");

        Map<String, Object> result = aiImageSearchService.searchByImage(base64Image, "找一下");

        assertTrue((Boolean) result.get("success"));
        assertFalse((Boolean) result.get("matched"));
        assertTrue(((String) result.get("message")).contains("AI服务暂时不可用"));
        assertEquals("通义千问API Key必须以 sk- 开头", result.get("fallbackReason"));
    }

    @Test
    void testSimilarityAccuracyLevel() {
        assertEquals("非常准确", invokeGetAccuracyLevel(95.0));
        assertEquals("高", invokeGetAccuracyLevel(80.0));
        assertEquals("中等", invokeGetAccuracyLevel(65.0));
        assertEquals("较低", invokeGetAccuracyLevel(50.0));
        assertEquals("低", invokeGetAccuracyLevel(30.0));
        assertEquals("非常准确", invokeGetAccuracyLevel(100.0));
        assertEquals("低", invokeGetAccuracyLevel(0.0));
    }

    private String invokeGetAccuracyLevel(double similarity) {
        return (String) ReflectionTestUtils.invokeMethod(aiImageSearchService, "getAccuracyLevel", similarity);
    }

    private Material createMaterial(Long id, String code, String name, String model, String mainImage) {
        Material material = new Material();
        material.setMaterialId(id);
        material.setMaterialCode(code);
        material.setMaterialName(name);
        material.setModel(model);
        material.setStatus("启用");
        material.setMainImage(mainImage);
        return material;
    }

    /**
     * 创建一个最小化的有效PNG图片字节数组
     */
    private byte[] createMinimalPng() {
        int[] data = {
                0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53,
                0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
                0x54, 0x08, 0xD7, 0x63, 0xF8, 0xFF, 0xFF,
                0x3F, 0x00, 0x05, 0xFE, 0x02, 0xFE, 0xDC,
                0xCC, 0x59, 0xE7, 0x00, 0x00, 0x00,
                0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60,
                0x82
        };
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) data[i];
        }
        return result;
    }
}
