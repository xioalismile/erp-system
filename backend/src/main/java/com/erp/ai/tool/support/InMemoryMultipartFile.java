package com.erp.ai.tool.support;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 内存 MultipartFile：把已读入内存的字节内容包装成 MultipartFile，
 * 以便复用与"界面导入"完全相同的 {@code ExcelService} 导入链路（不经过 HTTP、不落临时文件）。
 *
 * <p>先例：BomImportRowsTool 的内嵌 record。此处抽公共类，供通用导入/物料导入/行级导入共用。
 */
public record InMemoryMultipartFile(String name, byte[] content) implements MultipartFile {

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return name;
    }

    @Override
    public String getContentType() {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public boolean isEmpty() {
        return content == null || content.length == 0;
    }

    @Override
    public long getSize() {
        return content == null ? 0 : content.length;
    }

    @Override
    public byte[] getBytes() {
        return content;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content == null ? new byte[0] : content);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), content == null ? new byte[0] : content);
    }
}
