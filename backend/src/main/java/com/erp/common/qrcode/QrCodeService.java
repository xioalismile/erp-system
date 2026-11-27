package com.erp.common.qrcode;

import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成服务：统一出口，供"二维码接口"与"打印模板内联"复用。
 */
@Service
public class QrCodeService {

    private static final int MIN_SIZE = 60;
    private static final int MAX_SIZE = 600;
    private static final int DEFAULT_SIZE = 160;

    /** 尺寸收敛到安全区间（多比少好：允许大到 600，避免打标签时模糊） */
    public int clampSize(Integer size) {
        if (size == null) return DEFAULT_SIZE;
        if (size < MIN_SIZE) return MIN_SIZE;
        if (size > MAX_SIZE) return MAX_SIZE;
        return size;
    }

    /** 生成 PNG 字节 */
    public byte[] png(String content, int size) {
        QrConfig cfg = QrConfig.create().setWidth(size).setHeight(size).setMargin(1);
        BufferedImage image = QrCodeUtil.generate(content == null ? "" : content, cfg);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("二维码生成失败: " + e.getMessage(), e);
        }
    }

    /** 生成内联图片（打印模板用，避免打印时断网/跨域导致二维码打不出来） */
    public String dataUri(String content, int size) {
        byte[] png = png(content, size);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    /**
     * 一维条码（Code128）：与二维码双打用。内部编码为字母数字，Code128 通吃且不限长度。
     * 内容为空或非法时返回 null（调用方跳过条码，不影响标签其余内容）。
     */
    public byte[] barcodePng(String content, int width, int height) {
        if (content == null || content.isBlank()) return null;
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new MultiFormatWriter()
                    .encode(content.trim(), BarcodeFormat.CODE_128, width, height, hints);
            return matrixToPng(matrix);
        } catch (Exception e) {
            return null;
        }
    }

    public String barcodeDataUri(String content, int width, int height) {
        byte[] png = barcodePng(content, width, height);
        return png == null ? null : "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    /** BitMatrix → PNG（不引入 zxing javase，自行转换，避免多一个依赖） */
    private byte[] matrixToPng(BitMatrix matrix) {
        int w = matrix.getWidth();
        int h = matrix.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("条码生成失败: " + e.getMessage(), e);
        }
    }
}
