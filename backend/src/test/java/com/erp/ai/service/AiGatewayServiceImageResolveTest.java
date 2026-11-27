package com.erp.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI网关服务图片路径解析测试 - 验证本地图片URL转Base64逻辑
 */
class AiGatewayServiceImageResolveTest {

    @Test
    void testResolveImageUrls_UploadPathWithoutSlash() throws Exception {
        AiGatewayService service = new AiGatewayService();
        File tempDir = Files.createTempDirectory("erp-upload").toFile();
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.getAbsolutePath());

        // 创建测试目录和图片
        File imageDir = new File(tempDir, "ai-chat/2026/07/08");
        imageDir.mkdirs();
        File testImage = new File(imageDir, "test.png");
        byte[] pngBytes = createMinimalPng();
        Files.write(testImage.toPath(), pngBytes);

        Method method = AiGatewayService.class.getDeclaredMethod("resolveImageUrls", List.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service,
                List.of("/uploads/ai-chat/2026/07/08/test.png"));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).startsWith("data:image/png;base64,"));
    }

    @Test
    void testResolveImageUrls_UploadPathWithSlash() throws Exception {
        AiGatewayService service = new AiGatewayService();
        File tempDir = Files.createTempDirectory("erp-upload").toFile();
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.getAbsolutePath() + "/");

        File imageDir = new File(tempDir, "ai-chat/2026/07/08");
        imageDir.mkdirs();
        File testImage = new File(imageDir, "test.png");
        byte[] pngBytes = createMinimalPng();
        Files.write(testImage.toPath(), pngBytes);

        Method method = AiGatewayService.class.getDeclaredMethod("resolveImageUrls", List.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service,
                List.of("/uploads/ai-chat/2026/07/08/test.png"));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).startsWith("data:image/png;base64,"));
    }

    @Test
    void testResolveImageUrls_KeepHttpUrl() throws Exception {
        AiGatewayService service = new AiGatewayService();
        ReflectionTestUtils.setField(service, "uploadPath", "D:/erp-files");

        Method method = AiGatewayService.class.getDeclaredMethod("resolveImageUrls", List.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service,
                List.of("https://example.com/image.png"));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("https://example.com/image.png", result.get(0));
    }

    @Test
    void testResolveImageUrls_KeepBase64DataUrl() throws Exception {
        AiGatewayService service = new AiGatewayService();
        ReflectionTestUtils.setField(service, "uploadPath", "D:/erp-files");

        Method method = AiGatewayService.class.getDeclaredMethod("resolveImageUrls", List.class);
        method.setAccessible(true);

        String base64Url = "data:image/png;base64,iVBORw0KGgoAAAA";
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, List.of(base64Url));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(base64Url, result.get(0));
    }

    @Test
    void testResolveImageUrls_FileNotExist() throws Exception {
        AiGatewayService service = new AiGatewayService();
        ReflectionTestUtils.setField(service, "uploadPath", "D:/erp-files");

        Method method = AiGatewayService.class.getDeclaredMethod("resolveImageUrls", List.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service,
                List.of("/uploads/ai-chat/2026/07/08/not-exist.png"));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

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
