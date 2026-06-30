package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.production.entity.Routing;
import com.erp.production.entity.RoutingOperation;

import java.util.List;

public interface RoutingService extends IService<Routing> {
    void saveRoutingWithOperations(Routing routing, List<RoutingOperation> operations);
    PageResult<Routing> pageQuery(int current, int size, String keyword, String status);
    Routing getDetail(Long id);
    String generateRoutingCode();
}
