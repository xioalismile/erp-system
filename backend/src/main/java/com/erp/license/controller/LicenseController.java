package com.erp.license.controller;

import com.erp.license.entity.LicenseInfo;
import com.erp.license.validator.LicenseValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * License 状态查询接口
 * <p>
 * 供前端登录页展示 License 失效信息
 */
@RestController
@RequestMapping("/api/license")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseValidator licenseValidator;

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new HashMap<>();
        LicenseInfo info = licenseValidator.getCurrentLicense();
        result.put("valid", licenseValidator.isLicenseValid());
        result.put("failureReason", licenseValidator.getFailureReason());
        if (info != null) {
            result.put("customer", info.getCustomer());
            result.put("projectName", info.getProjectName());
            result.put("issueDate", info.getIssueDate());
            result.put("expireDate", info.getExpireDate());
            result.put("maxUsers", info.getMaxUsers());
            result.put("features", info.getFeatures());
        }
        return result;
    }
}
