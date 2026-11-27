package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.BankAccount;
import com.erp.basedata.mapper.BankAccountMapper;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.serialno.support.SegmentCodeProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 银行账户（财务账户，菜单挂在基础资料下）。
 *
 * <p>编码 account_no 由后端生成：原先只有前端"新增页取号"这一条路径（后端完全没有生成逻辑），
 * 前端改为"保存时后端生成"后必须在此补齐，否则账户编码会存成空值。</p>
 */
@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl extends ServiceImpl<BankAccountMapper, BankAccount> implements BankAccountService {

    private final SysSerialNoConfigService serialNoService;

    @Override
    public boolean save(BankAccount entity) {
        if (entity.getAccountNo() == null || entity.getAccountNo().isBlank()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("BANK_ACCOUNT")) {
                throw new BusinessException("银行账户已关闭自动编号，请手动输入编码");
            }
            // 三参重载：以业务表实况取号（删除即释放 + 补最小空洞）
            entity.setAccountNo(serialNoService.generateSerialNo("BANK_ACCOUNT", this::nextAccountNo,
                    SegmentCodeProbe.of(baseMapper, "account_no")));
        }
        if (baseMapper.exists(new QueryWrapper<BankAccount>().eq("account_no", entity.getAccountNo()))) {
            throw new BusinessException("账户编码已存在: " + entity.getAccountNo());
        }
        return super.save(entity);
    }

    /**
     * 防重续号探测：查询银行账户表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextAccountNo(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        BankAccount last = getOne(new QueryWrapper<BankAccount>().likeRight("account_no", prefix)
                .orderByDesc("account_no").last("LIMIT 1"));
        if (last == null || last.getAccountNo() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getAccountNo().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }
}
