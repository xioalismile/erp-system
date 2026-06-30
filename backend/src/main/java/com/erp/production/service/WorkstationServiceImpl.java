package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.production.entity.Workstation;
import com.erp.production.mapper.WorkstationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkstationServiceImpl extends ServiceImpl<WorkstationMapper, Workstation> implements WorkstationService {

    @Override
    public PageResult<Workstation> pageQuery(int current, int size, String keyword, String status, Long workCenterId) {
        QueryWrapper<Workstation> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("workstation_code", keyword)
                    .or().like("workstation_name", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (workCenterId != null) {
            wrapper.eq("work_center_id", workCenterId);
        }
        wrapper.orderByDesc("created_at");
        Page<Workstation> page = new Page<>(current, size);
        Page<Workstation> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public boolean existsByCode(String code) {
        return count(new QueryWrapper<Workstation>().eq("workstation_code", code)) > 0;
    }

    @Override
    public boolean existsByCodeExcludeId(String code, Long id) {
        return count(new QueryWrapper<Workstation>()
                .eq("workstation_code", code)
                .ne("workstation_id", id)) > 0;
    }
}
