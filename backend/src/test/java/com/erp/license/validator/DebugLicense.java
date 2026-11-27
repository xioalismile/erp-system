package com.erp.license.validator;

import com.erp.license.entity.LicenseInfo;
import com.erp.license.loader.LicenseLoader;
import com.erp.license.util.LicenseSignatureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class DebugLicense {
    public static void main(String[] args) throws Exception {
        String privKey = new String(Files.readAllBytes(Path.of("private_key_base64.txt"))).trim();
        String pubKey = new String(Files.readAllBytes(Path.of("public_key_base64.txt"))).trim();

        // Step 1: Create LicenseInfo
        LicenseInfo info = new LicenseInfo();
        info.setLicenseId(UUID.randomUUID().toString());
        info.setCustomer("测试");
        info.setProjectName("智能ERP");
        info.setMacAddresses(List.of("00163E0B0315"));
        info.setIssueDate(LocalDate.of(2026, 7, 22));
        info.setExpireDate(LocalDate.of(2026, 9, 25));
        info.setMaxUsers(50);
        info.setFeatures(Arrays.asList("sale", "purchase", "inventory", "production", "finance"));

        // Step 2: Generate payload and sign
        String payloadBefore = info.toSignPayload();
        System.out.println("Payload BEFORE serialization: " + payloadBefore);

        String signature = LicenseSignatureUtil.sign(privKey, payloadBefore);
        info.setSignature(signature);

        // Step 3: Serialize to JSON
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(info);
        Files.writeString(Path.of("test_debug.lic"), json);

        // Step 4: Load back from JSON
        LicenseInfo loaded = LicenseLoader.load("test_debug.lic");
        String payloadAfter = loaded.toSignPayload();
        System.out.println("Payload AFTER deserialization:  " + payloadAfter);

        System.out.println("Payloads match: " + payloadBefore.equals(payloadAfter));

        // Step 5: Verify signature
        boolean valid = LicenseSignatureUtil.verify(pubKey, payloadAfter, loaded.getSignature());
        System.out.println("Signature valid: " + valid);

        if (!valid) {
            // Debug: try verifying with the before payload
            boolean validBefore = LicenseSignatureUtil.verify(pubKey, payloadBefore, loaded.getSignature());
            System.out.println("Signature valid with before-payload: " + validBefore);
        }
    }
}
