package com.erp.device.controller;

import com.erp.common.Result;
import com.erp.device.service.DeviceAccessEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 设备接入引擎运维入口 */
@RestController
@RequestMapping("/api/device/engine")
@RequiredArgsConstructor
public class DeviceEngineController {

    private final DeviceAccessEngine engine;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(engine.status());
    }

    /** 配置变更后立即重扫通道，不用等 60 秒自动对账 */
    @PostMapping("/reconcile")
    public Result<Map<String, Object>> reconcile() {
        engine.reconcileNow();
        return Result.success(engine.status());
    }
}
