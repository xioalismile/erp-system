package com.erp.basedata.controller;

import com.erp.basedata.entity.Location;
import com.erp.basedata.service.LocationService;
import com.erp.common.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 货位 Controller
 * 路由: /api/basedata/location
 */
@RestController
@RequestMapping("/api/basedata/location")
public class LocationController extends BaseCrudController<Location, Long> {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @Override
    protected IService<Location> getService() { return locationService; }

    @Override
    protected String getCodeField() { return "bin_code"; }

    @Override
    protected String getIdField() { return "bin_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("bin_code", "bin_name"); }
}
