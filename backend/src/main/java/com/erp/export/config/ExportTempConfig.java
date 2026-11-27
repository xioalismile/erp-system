package com.erp.export.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.DefaultTempFileCreationStrategy;
import org.apache.poi.util.TempFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * 导出模块 POI 临时目录初始化。
 *
 * 背景：POI 的 SXSSF（EasyExcel 模板导出底层）会把 sheet 先写到 %TEMP%\poifiles\*.xml 再打包，
 * 一旦系统 TEMP 被清理/受控，会抛 NoSuchFileException 且响应头已提交 → 浏览器下载到 0 字节文件。
 * 这里把 POI 临时目录固定到应用自管目录，与系统 TEMP 解耦。
 */
@Slf4j
@Configuration
public class ExportTempConfig {

    /** 可选：外部指定导出临时目录（默认 <应用工作目录>/temp） */
    @Value("${erp.export.temp-path:}")
    private String configuredTempPath;

    @PostConstruct
    public void initPoiTempDir() {
        try {
            File dir = resolveTempDir();
            if (dir == null) {
                log.warn("[导出模块] 无法创建应用自管 POI 临时目录，将回退系统默认 TEMP（存在被清理导致导出失败的风险）");
                return;
            }
            TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy(dir));
            log.info("[导出模块] POI临时目录={}（应用自管，不再依赖系统 TEMP，规避临时文件被清理导致导出失败）", dir.getAbsolutePath());
        } catch (Throwable e) {
            log.error("[导出模块] 初始化 POI 临时目录失败，将使用系统默认 TEMP: {}", e.getMessage(), e);
        }
    }

    /** 依次尝试：外部配置 → jar 同级目录/temp → 应用工作目录/temp；都不可写时返回 null 回退系统 TEMP */
    private File resolveTempDir() {
        if (configuredTempPath != null && !configuredTempPath.isBlank()) {
            File d = ensureDir(new File(configuredTempPath));
            if (d != null) return d;
        }
        try {
            File code = new File(ExportTempConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File jarDir = code.isFile() ? code.getParentFile() : code;
            File d = ensureDir(new File(jarDir, "temp"));
            if (d != null) return d;
        } catch (Throwable ignored) {
        }
        return ensureDir(new File(System.getProperty("user.dir"), "temp"));
    }

    private File ensureDir(File dir) {
        if (dir == null) return null;
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) return null;
        return dir.isDirectory() && dir.canWrite() ? dir : null;
    }
}
