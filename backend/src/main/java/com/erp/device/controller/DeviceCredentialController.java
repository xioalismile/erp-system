package com.erp.device.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.device.entity.DeviceAccessCredential;
import com.erp.device.service.DeviceCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 设备接入凭证 */
@RestController
@RequestMapping("/api/device/credential")
@RequiredArgsConstructor
public class DeviceCredentialController {

    private final DeviceCredentialService credentialService;

    @GetMapping
    public Result<PageResult<DeviceAccessCredential>> page(@RequestParam(defaultValue = "1") int current,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) Long deviceId,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status) {
        return Result.success(credentialService.pageQuery(current, size, deviceId, keyword, status));
    }

    @GetMapping("/current")
    public Result<DeviceAccessCredential> current(@RequestParam Long deviceId) {
        return Result.success(credentialService.current(deviceId));
    }

    /** 生成凭证：密钥只在此接口返回一次 */
    @PostMapping("/{deviceId}/generate")
    public Result<DeviceAccessCredential> generate(@PathVariable Long deviceId) {
        return Result.success(credentialService.generate(deviceId));
    }

    /** 轮换密钥：旧密钥立即失效，新密钥只返回一次 */
    @PostMapping("/{id}/rotate")
    public Result<DeviceAccessCredential> rotate(@PathVariable Long id) {
        return Result.success(credentialService.rotate(id));
    }

    @PostMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id) {
        credentialService.revoke(id);
        return Result.success();
    }
}
