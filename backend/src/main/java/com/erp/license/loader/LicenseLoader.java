package com.erp.license.loader;

import com.erp.license.entity.LicenseInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * License 文件加载器
 * <p>
 * license.lic 是 JSON 格式（便于客户肉眼检查授权信息），
 * 内含 signature 字段（RSA 签名）防止篡改：
 * - 客户可以"看到"授权信息（透明）
 * - 但无法"修改"任何字段（修改后签名校验失败）
 */
public class LicenseLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JavaTimeModule());
    }

    /**
     * 从指定路径加载 License 文件
     *
     * @param path license.lic 文件路径
     * @return LicenseInfo 对象
     * @throws Exception 文件不存在或解析失败
     */
    public static LicenseInfo load(String path) throws Exception {
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalStateException("License 文件不存在: " + path);
        }
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return MAPPER.readValue(content, LicenseInfo.class);
    }
}
