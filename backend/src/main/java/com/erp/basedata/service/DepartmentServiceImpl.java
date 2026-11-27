package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Department;
import com.erp.basedata.mapper.DepartmentMapper;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.serialno.support.SegmentCodeProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements DepartmentService {

    private final SysSerialNoConfigService serialNoService;

    @Override
    public Department getByCode(String deptCode) {
        return getOne(new QueryWrapper<Department>().eq("dept_code", deptCode));
    }

    @Override
    public boolean existsByCode(String deptCode) {
        return baseMapper.exists(new QueryWrapper<Department>().eq("dept_code", deptCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String deptCode, Long deptId) {
        return baseMapper.exists(new QueryWrapper<Department>()
                .eq("dept_code", deptCode).ne("dept_id", deptId));
    }

    @Override
    public boolean save(Department entity) {
        if (entity.getDeptCode() == null || entity.getDeptCode().isBlank()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("BD_DEPARTMENT")) {
                throw new BusinessException("部门已关闭自动编号，请手动输入编码");
            }
            // 三参重载：以业务表实况取号（删除即释放 + 补最小空洞）
            entity.setDeptCode(serialNoService.generateSerialNo("BD_DEPARTMENT", this::nextDeptCode,
                    SegmentCodeProbe.of(baseMapper, "dept_code")));
        }
        if (exists(new QueryWrapper<Department>().eq("dept_code", entity.getDeptCode()))) {
            throw new BusinessException("部门编码已存在");
        }
        return super.save(entity);
    }

    /**
     * 防重续号探测：查询部门表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextDeptCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        Department last = getOne(new QueryWrapper<Department>().likeRight("dept_code", prefix)
                .orderByDesc("dept_code").last("LIMIT 1"));
        if (last == null || last.getDeptCode() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getDeptCode().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }
}
