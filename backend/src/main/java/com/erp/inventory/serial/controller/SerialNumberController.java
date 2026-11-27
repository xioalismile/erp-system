package com.erp.inventory.serial.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.inventory.serial.entity.SerialNumber;
import com.erp.inventory.serial.entity.SerialNumberTrace;
import com.erp.inventory.serial.service.SerialNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/serial-number")
@RequiredArgsConstructor
public class SerialNumberController {

    private final SerialNumberService serialNumberService;

    @GetMapping
    public Result<PageResult<SerialNumber>> pageQuery(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(serialNumberService.pageQuery(current, size, keyword, status, materialId, warehouseId, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<SerialNumber> getDetail(@PathVariable Long id) {
        return Result.success(serialNumberService.getDetail(id));
    }

    @GetMapping("/{id}/trace")
    public Result<List<SerialNumberTrace>> getTraceList(@PathVariable Long id) {
        return Result.success(serialNumberService.getTraceList(id));
    }

    @GetMapping("/no/{serialNo}")
    public Result<SerialNumber> getSerialNumberByNo(@PathVariable String serialNo) {
        return Result.success(serialNumberService.getSerialNumberByNo(serialNo));
    }

    @PostMapping("/batch")
    public Result<Void> batchCreate(@RequestBody List<SerialNumber> list) {
        serialNumberService.createSerialNumbers(list);
        return Result.success();
    }

    @PutMapping("/{id}/scrap")
    public Result<Void> scrap(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("remark") : null;
        serialNumberService.scrapSerialNumbers(List.of(serialNumberService.getById(id).getSerialNo()), remark);
        return Result.success();
    }

    @PostMapping("/check")
    public Result<Boolean> checkSerialNumber(@RequestParam String serialNo) {
        return Result.success(serialNumberService.checkSerialNumberExists(serialNo));
    }
}
