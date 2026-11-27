package com.erp.license.validator;

import com.erp.license.entity.LicenseInfo;
import com.erp.license.loader.LicenseLoader;
import com.erp.license.util.LicenseSignatureUtil;

public class VerifyLicense {
    public static void main(String[] args) throws Exception {
        String licPath = args.length > 0 ? args[0] : "license.lic";
        String pubKey = args.length > 1 ? args[1] : "public_key_base64.txt";

        // Load license
        LicenseInfo info = LicenseLoader.load(licPath);
        System.out.println("License loaded:");
        System.out.println("  Customer: " + info.getCustomer());
        System.out.println("  MAC: " + info.getMacAddresses());
        System.out.println("  Issue: " + info.getIssueDate());
        System.out.println("  Expire: " + info.getExpireDate());
        System.out.println("  Signature: " + (info.getSignature() != null ? info.getSignature().substring(0, 30) + "..." : "null"));

        // Load public key
        String publicKey = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(pubKey))).trim();

        // Verify signature
        String payload = info.toSignPayload();
        System.out.println("\nPayload: " + payload);

        boolean valid = LicenseSignatureUtil.verify(publicKey, payload, info.getSignature());
        System.out.println("\nSignature valid: " + valid);

        if (!valid) {
            System.out.println("\nERROR: Signature verification failed!");
            System.exit(1);
        } else {
            System.out.println("\nOK: License signature is valid!");
        }
    }
}
