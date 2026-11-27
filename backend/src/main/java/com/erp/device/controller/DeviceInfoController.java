package com.erp.device.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.service.DeviceInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 设备台账 */
@RestController
@RequestMapping("/api/device/ledger")
@RequiredArgsConstructor
public class DeviceInfoController {

    private final DeviceInfoService deviceService;

    @GetMapping
    public Result<PageResult<DeviceInfo>> page(@RequestParam(defaultValue = "1") int current,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) Long workCenterId,
                                               @RequestParam(required = false) String status) {
        return Result.success(deviceService.pageQuery(current, size, keyword, categoryId, workCenterId, status));
    }

    /** 下拉用：全部启用设备 */
    @GetMapping("/enabled")
    public Result<List<DeviceInfo>> enabled() {
        return Result.success(deviceService.listEnabled());
    }

    @GetMapping("/{id}")
    public Result<DeviceInfo> detail(@PathVariable Long id) {
        return Result.success(deviceService.getDetail(id));
    }

    @PostMapping
    public Result<DeviceInfo> create(@RequestBody DeviceInfo device) {
        return Result.success(deviceService.createDevice(device));
    }

    @PutMapping("/{id}")
    public Result<DeviceInfo> update(@PathVariable Long id, @RequestBody DeviceInfo device) {
        return Result.success(deviceService.updateDevice(id, device));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.deleteDevice(id);
        return Result.success();
    }

    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        deviceService.changeStatus(id, "启用");
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        deviceService.changeStatus(id, "停用");
        return Result.success();
    }

    @PostMapping("/{id}/scrap")
    public Result<Void> scrap(@PathVariable Long id) {
        deviceService.changeStatus(id, "报废");
        return Result.success();
    }

    /** 设备二维码（内容 = 设备编码） */
    @GetMapping(value = "/{id}/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrcode(@PathVariable Long id,
                                         @RequestParam(required = false) Integer size) {
        byte[] png = deviceService.qrcode(id, size);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    /** 设备二维码 Data URI（前端 img 直接引用，避免图片请求带不上 Token） */
    @GetMapping("/{id}/qrcode-base64")
    public Result<String> qrcodeBase64(@PathVariable Long id,
                                       @RequestParam(required = false) Integer size) {
        return Result.success(deviceService.qrcodeDataUri(id, size));
    }
}
