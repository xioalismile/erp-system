package com.erp.device.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.device.entity.DeviceProtocolTemplate;
import com.erp.device.service.DeviceProtocolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 协议模板 */
@RestController
@RequestMapping("/api/device/protocol")
@RequiredArgsConstructor
public class DeviceProtocolController {

    private final DeviceProtocolService protocolService;

    @GetMapping("/page")
    public Result<PageResult<DeviceProtocolTemplate>> page(@RequestParam(defaultValue = "1") int current,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String protocolType) {
        return Result.success(protocolService.pageQuery(current, size, keyword, protocolType));
    }

    @GetMapping("/list")
    public Result<List<DeviceProtocolTemplate>> list() {
        return Result.success(protocolService.listEnabled());
    }

    @PostMapping
    public Result<DeviceProtocolTemplate> create(@RequestBody DeviceProtocolTemplate template) {
        return Result.success(protocolService.create(template));
    }

    @PutMapping("/{id}")
    public Result<DeviceProtocolTemplate> update(@PathVariable Long id,
                                                 @RequestBody DeviceProtocolTemplate template) {
        return Result.success(protocolService.update(id, template));
    }

    @PostMapping("/{id}/copy")
    public Result<DeviceProtocolTemplate> copy(@PathVariable Long id) {
        return Result.success(protocolService.copy(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        protocolService.delete(id);
        return Result.success();
    }
}
