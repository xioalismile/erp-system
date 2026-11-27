package com.erp.serialno.controller;

import com.erp.common.Result;
import com.erp.serialno.entity.SysSerialNoConfig;
import com.erp.serialno.service.SysSerialNoConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/serial-no-config")
public class SysSerialNoConfigController {

    private final SysSerialNoConfigService sysSerialNoConfigService;

    public SysSerialNoConfigController(SysSerialNoConfigService sysSerialNoConfigService) {
        this.sysSerialNoConfigService = sysSerialNoConfigService;
    }

    @GetMapping
    public Result<List<SysSerialNoConfig>> list() {
        return Result.success(sysSerialNoConfigService.listAll());
    }

    @GetMapping("/{id}")
    public Result<SysSerialNoConfig> getById(@PathVariable Long id) {
        return Result.success(sysSerialNoConfigService.getById(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody SysSerialNoConfig config) {
        sysSerialNoConfigService.saveConfig(config);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody SysSerialNoConfig config) {
        sysSerialNoConfigService.updateConfig(config);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysSerialNoConfigService.deleteConfig(id);
        return Result.success();
    }

    @GetMapping("/generate/{docType}")
    public Result<String> generate(@PathVariable String docType) {
        return Result.success(sysSerialNoConfigService.generateSerialNo(docType));
    }

    @PostMapping("/batch-generate")
    public Result<List<String>> batchGenerate(@RequestBody List<String> docTypes) {
        return Result.success(sysSerialNoConfigService.batchGenerate(docTypes));
    }

    @GetMapping("/by-doc-type/{docType}")
    public Result<SysSerialNoConfig> getByDocType(@PathVariable String docType) {
        return Result.success(sysSerialNoConfigService.getByDocType(docType));
    }

    @GetMapping("/is-auto-generate/{docType}")
    public Result<Boolean> isAutoGenerate(@PathVariable String docType) {
        return Result.success(sysSerialNoConfigService.isAutoGenerate(docType));
    }
}
