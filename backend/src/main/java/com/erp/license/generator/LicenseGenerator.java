package com.erp.license.generator;

import com.erp.license.entity.LicenseInfo;
import com.erp.license.util.LicenseSignatureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * License 离线生成工具
 * <p>
 * 使用方式（供应商内部使用，绝不交付给客户）：
 * <pre>
 * # 1. 生成 RSA 密钥对（仅一次）
 * openssl genrsa -out private_key.pem 2048
 * openssl rsa -in private_key.pem -pubout -out public_key.pem
 * openssl pkcs8 -topk8 -in private_key.pem -inform PEM -out private_key_pkcs8.pem -outform PEM -nocrypt
 *
 * # 2. 提取 Base64 格式私钥（去除头尾的 BEGIN/END 标记）
 * cat private_key_pkcs8.pem | grep -v "^-" | tr -d '\n' > private_key_base64.txt
 *
 * # 3. 运行生成器
 * java -cp smart-erp.jar com.erp.license.generator.LicenseGenerator \
 *   --customer "客户A" \
 *   --project "智能ERP" \
 *   --mac "001122334455" \
 *   --expire 2026-12-31 \
 *   --maxUsers 50 \
 *   --features "sale,purchase,inventory,production,finance" \
 *   --privateKeyFile private_key_base64.txt \
 *   --output license.lic
 * </pre>
 * <p>
 * 然后把 license.lic 和公钥（注入环境变量 ERP_LICENSE_PUBLIC_KEY）一起发给客户部署。
 */
public class LicenseGenerator {

    public static void main(String[] args) throws Exception {
        // 解析命令行参数
        String customer = getArg(args, "--customer", "默认客户");
        String project = getArg(args, "--project", "智能ERP");
        String macStr = getArg(args, "--mac", "");
        String expireDateStr = getArg(args, "--expire", "2026-12-31");
        String maxUsersStr = getArg(args, "--maxUsers", "0");
        String featuresStr = getArg(args, "--features", "sale,purchase,inventory,production,finance");
        String privateKeyFile = getArg(args, "--privateKeyFile", "private_key_base64.txt");
        String outputFile = getArg(args, "--output", "license.lic");

        // 加载私钥
        String privateKeyBase64 = new String(Files.readAllBytes(Path.of(privateKeyFile))).trim();

        // 构建 LicenseInfo
        LicenseInfo info = new LicenseInfo();
        info.setLicenseId(UUID.randomUUID().toString());
        info.setCustomer(customer);
        info.setProjectName(project);

        // MAC 地址白名单
        if (!macStr.isEmpty()) {
            List<String> macList = Arrays.stream(macStr.split(","))
                    .map(String::trim)
                    .map(s -> s.replace(":", "").replace("-", "").toUpperCase())
                    .toList();
            info.setMacAddresses(macList);
        }

        info.setIssueDate(LocalDate.now());
        info.setExpireDate(LocalDate.parse(expireDateStr));
        info.setMaxUsers(Integer.parseInt(maxUsersStr));

        // 功能模块列表
        List<String> features = Arrays.stream(featuresStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        info.setFeatures(features);

        // 生成签名
        String payload = info.toSignPayload();
        String signature = LicenseSignatureUtil.sign(privateKeyBase64, payload);
        info.setSignature(signature);

        // 输出为 JSON 文件
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(info);

        Files.writeString(Path.of(outputFile), json);

        System.out.println("License 生成成功：" + outputFile);
        System.out.println("License ID: " + info.getLicenseId());
        System.out.println("客户: " + info.getCustomer());
        System.out.println("项目: " + info.getProjectName());
        System.out.println("MAC 白名单: " + info.getMacAddresses());
        System.out.println("颁发日期: " + info.getIssueDate());
        System.out.println("到期日期: " + info.getExpireDate());
        System.out.println("最大用户数: " + info.getMaxUsers());
        System.out.println("功能模块: " + info.getFeatures());
    }

    private static String getArg(String[] args, String name, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) return args[i + 1];
        }
        return defaultValue;
    }
}
