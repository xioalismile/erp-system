package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.production.entity.WorkCenter;
import com.erp.production.mapper.WorkCenterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkCenterServiceImpl extends ServiceImpl<WorkCenterMapper, WorkCenter> implements WorkCenterService {

    @Override
    public PageResult<WorkCenter> pageQuery(int current, int size, String keyword, String status) {
        QueryWrapper<WorkCenter> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("work_center_code", keyword)
                    .or().like("work_center_name", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        Page<WorkCenter> page = new Page<>(current, size);
        Page<WorkCenter> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public boolean existsByCode(String code) {
        return count(new QueryWrapper<WorkCenter>().eq("work_center_code", code)) > 0;
    }

    @Override
    public boolean existsByCodeExcludeId(String code, Long id) {
        return count(new QueryWrapper<WorkCenter>()
                .eq("work_center_code", code)
                .ne("work_center_id", id)) > 0;
    }
}
