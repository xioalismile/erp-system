package com.erp.config;

import com.erp.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 通用文件上传控制器
 * 支持图片和文档上传, 保存到本地 erp.upload.path 目录
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    @Value("${erp.upload.path:D:/erp-files}")
    private String uploadPath;

    /**
     * 业务类型白名单（安全审计 P2：原 type 直接拼路径，可路径遍历）。
     * 前端实际取值 common/logo/system-logo 均匹配。
     */
    private static final java.util.regex.Pattern TYPE_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9_-]{1,32}$");

    /**
     * 扩展名白名单（安全审计 P2：原无校验可上传 html/svg/js 造成存储型 XSS，exe 钓鱼风险）。
     * 覆盖业务场景：物料/系统 logo 图片、AI 助手与单据附件（文档/表格/压缩包）。
     */
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            // 图片
            "png", "jpg", "jpeg", "gif", "bmp", "webp",
            // 文档
            "pdf", "txt", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            // 压缩包
            "zip", "rar", "7z"
    );

    /**
     * 单文件上传
     * @param file 上传的文件
     * @param type 业务类型(如 material/sale-order/delivery), 用于分子目录
     * @return 文件访问URL
     */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "type", defaultValue = "common") String type) {
        if (file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        try {
            String url = saveFile(file, type);
            return Result.success(url);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IOException e) {
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 多文件上传
     */
    @PostMapping("/uploads")
    public Result<List<String>> uploads(@RequestParam("files") MultipartFile[] files,
                                        @RequestParam(value = "type", defaultValue = "common") String type) {
        List<String> urls = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    urls.add(saveFile(file, type));
                }
            }
            return Result.success(urls);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IOException e) {
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 保存文件到本地, 返回访问URL
     * 落盘文件名 = 清洗后的原始文件名（保留用户可读名称），同目录重名自动追加序号
     * @throws IllegalArgumentException 类型或扩展名不合法（安全审计 P2 白名单校验）
     */
    private String saveFile(MultipartFile file, String type) throws IOException {
        // 业务类型白名单：阻断 ../ 等路径遍历字符
        if (!TYPE_PATTERN.matcher(type).matches()) {
            throw new IllegalArgumentException("非法的业务类型: " + type);
        }
        // 原始文件名 → 小写扩展名白名单校验
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
        }
        if (ext.isEmpty() || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型: " + originalName + "（允许: 图片/文档/压缩包）");
        }
        // 按日期分目录: yyyy/MM/dd
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        // 完整目录路径
        File dir = new File(uploadPath + File.separator + type + File.separator + datePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 落盘文件名 = 清洗后的原始文件名, 重名追加序号
        File destFile = uniqueFile(dir, sanitizeFileName(originalName, ext));
        // 双保险：规范化路径必须落在上传根目录内（防路径遍历）
        String canonicalRoot = new File(uploadPath).getCanonicalPath();
        if (!destFile.getCanonicalPath().startsWith(canonicalRoot + File.separator)) {
            throw new IllegalArgumentException("非法的存储路径");
        }
        file.transferTo(destFile);
        // 返回访问URL (与 WebMvcConfig 的静态资源映射对应), 文件名百分号编码以支持中文原名
        String encodedName = java.net.URLEncoder.encode(destFile.getName(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "/uploads/" + type + "/" + datePath + "/" + encodedName;
    }

    /**
     * 清洗原始文件名为安全落盘文件名:
     * 1. 剥离路径部分（部分浏览器老行为会传全路径）
     * 2. 替换路径分隔符/控制字符/URL保留字符/Windows保留字符/逗号（前端逗号分隔存储）为下划线
     * 3. 主名截断至80字符, 防Windows路径超长; 空名兜底为 file
     */
    private String sanitizeFileName(String originalName, String ext) {
        String name = originalName == null ? "" : originalName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        name = name.replaceAll("[\\\\/:*?\"<>|%#&+,\\s\\x00-\\x1F]", "_");
        if (name.isEmpty()) {
            name = "file";
        }
        if (name.length() > 80) {
            name = name.substring(0, 80);
        }
        return name + "." + ext;
    }

    /**
     * 同目录防覆盖: 已存在同名文件时追加 _1、_2… 序号, 极端冲突时UUID兜底
     */
    private File uniqueFile(File dir, String fileName) {
        File candidate = new File(dir, fileName);
        if (!candidate.exists()) {
            return candidate;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extPart = dot > 0 ? fileName.substring(dot + 1) : "";
        for (int i = 1; i < 1000; i++) {
            candidate = new File(dir, base + "_" + i + (extPart.isEmpty() ? "" : "." + extPart));
            if (!candidate.exists()) {
                return candidate;
            }
        }
        return new File(dir, UUID.randomUUID().toString().replace("-", "") + (extPart.isEmpty() ? "" : "." + extPart));
    }
}
