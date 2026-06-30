package com.erp.production.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.production.entity.Routing;
import com.erp.production.service.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production/routings")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingService routingService;

    @GetMapping("/page")
    public Result<PageResult<Routing>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(routingService.pageQuery(current, size, keyword, status));
    }

    @GetMapping("/list")
    public Result<List<Routing>> list() {
        return Result.success(routingService.list());
    }

    @GetMapping("/{id}")
    public Result<Routing> detail(@PathVariable Long id) {
        return Result.success(routingService.getDetail(id));
    }

    @PostMapping
    public Result<Routing> create(@RequestBody Routing routing) {
        routingService.saveRoutingWithOperations(routing, routing.getOperations());
        return Result.success(routing);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Routing routing) {
        routing.setRoutingId(id);
        routingService.saveRoutingWithOperations(routing, routing.getOperations());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        routingService.removeById(id);
        return Result.success();
    }
}
