package com.erp.license.validator;

import com.erp.license.entity.LicenseInfo;
import com.erp.license.loader.LicenseLoader;
import com.erp.license.util.LicenseSignatureUtil;

/**
 * License 校验流程独立测试入口
 * <p>
 * 运行方式：
 *   java -cp "target/classes;<jackson-jars>" com.erp.license.validator.LicenseValidatorTest
 *   环境变量：ERP_LICENSE_PUBLIC_KEY=<公钥>  ERP_LICENSE_FILE=license.lic
 */
public class LicenseValidatorTest {

    public static void main(String[] args) throws Exception {
        String publicKey = System.getenv("ERP_LICENSE_PUBLIC_KEY");
        String licenseFile = System.getenv("ERP_LICENSE_FILE");
        if (licenseFile == null || licenseFile.isEmpty()) licenseFile = "license.lic";

        if (publicKey == null || publicKey.isEmpty()) {
            System.err.println("FAIL: 未设置 ERP_LICENSE_PUBLIC_KEY 环境变量");
            System.exit(1);
        }

        // 1. 加载 License
        LicenseInfo info = LicenseLoader.load(licenseFile);
        System.out.println("加载 License 成功: 客户=" + info.getCustomer()
                + " 到期=" + info.getExpireDate());

        // 2. 验证签名
        String payload = info.toSignPayload();
        System.out.println("待签名内容: " + payload);
        boolean valid = LicenseSignatureUtil.verify(publicKey, payload, info.getSignature());
        System.out.println("签名校验结果: " + (valid ? "✓ 通过" : "✗ 失败"));

        if (valid) {
            System.out.println("✓✓✓ License 全部校验通过 ✓✓✓");
        } else {
            System.err.println("✗✗✗ License 校验失败 ✗✗✗");
            System.exit(2);
        }
    }
}
