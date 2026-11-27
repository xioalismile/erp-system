package com.erp.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.scope.DataScopeResolver;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.crm.entity.PromotionRecord;
import com.erp.crm.entity.PromotionTarget;
import com.erp.crm.mapper.PromotionRecordMapper;
import com.erp.crm.mapper.PromotionTargetMapper;
import com.erp.crm.service.PromotionRecordService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推广记录实现（销售过程动作量化 · 2026-10-07）
 *
 * <p>口径（立项登记表第七节）：
 * ① 朋友圈推广次数 = 当周「朋友圈图片 / 朋友圈视频」且截图非空的记录数；
 * ② RFID 素材覆盖 = 材质类型「RFID素材」所覆盖的客户去重数；
 * ③ 全部按数据范围（本人 / 本部门 / 全部）过滤。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionRecordServiceImpl extends ServiceImpl<PromotionRecordMapper, PromotionRecord>
        implements PromotionRecordService {

    private final PromotionTargetMapper promotionTargetMapper;
    private final CustomerService customerService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SysConfigService sysConfigService;

    /** 素材类型白名单（写死 5 项，与方案 4.2 表逐条对应） */
    private static final Set<String> MATERIAL_TYPES = Set.of(
            "朋友圈图片", "朋友圈视频", "产品图", "车间视频", "RFID素材");
    /** 计入「每周朋友圈推广次数」的素材类型 */
    private static final Set<String> MOMENT_TYPES = Set.of("朋友圈图片", "朋友圈视频");
    private static final String RFID_TYPE = "RFID素材";

    @Override
    public PageResult<PromotionRecord> pageQuery(int current, int size, String keyword, String materialType,
                                                 String startDate, String endDate,
                                                 DataScopeResolver.DataScopeContext scopeCtx) {
        QueryWrapper<PromotionRecord> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            // 多字段 OR 必须包 and(...)，否则会绕过后续过滤与数据权限（历史铁律）
            wrapper.and(w -> w.like("promotion_no", keyword)
                    .or().like("material_name", keyword)
                    .or().like("staff_name", keyword)
                    .or().like("remark", keyword));
        }
        if (materialType != null && !materialType.isBlank()) {
            wrapper.eq("material_type", materialType);
        }
        if (startDate != null && !startDate.isBlank()) {
            wrapper.ge("promotion_date", LocalDate.parse(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            wrapper.le("promotion_date", LocalDate.parse(endDate));
        }
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        wrapper.orderByDesc("promotion_date").orderByDesc("promotion_id");

        Page<PromotionRecord> page = new Page<>(current, size);
        Page<PromotionRecord> result = page(page, wrapper);
        fillCustomerNames(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PromotionRecord getDetail(Long id) {
        PromotionRecord r = getById(id);
        if (r == null) throw new BusinessException("推广记录不存在");
        List<PromotionTarget> targets = promotionTargetMapper.selectList(
                new QueryWrapper<PromotionTarget>().eq("promotion_id", id));
        r.setCustomerIds(targets.stream().map(PromotionTarget::getCustomerId).collect(Collectors.toList()));
        r.setCustomerNames(targets.stream().map(PromotionTarget::getCustomerName)
                .filter(Objects::nonNull).collect(Collectors.joining("、")));
        return r;
    }

    @Override
    @Transactional
    public void savePromotion(PromotionRecord record) {
        validate(record);
        if (record.getPromotionNo() == null || record.getPromotionNo().isBlank()) {
            record.setPromotionNo(generatePromotionNo());
        }
        if (record.getPromotionDate() == null) {
            record.setPromotionDate(LocalDate.now());
        }
        if (record.getPromotionId() != null) {
            // 防止前端重复提交造成重复单号
            record.setPromotionId(null);
        }
        save(record);
        replaceTargets(record);
        log.info("推广记录保存成功: {} 素材类型: {} 对象数: {}",
                record.getPromotionNo(), record.getMaterialType(), record.getTargetCount());
    }

    @Override
    @Transactional
    public void updatePromotion(Long id, PromotionRecord record) {
        PromotionRecord exist = getById(id);
        if (exist == null) throw new BusinessException("推广记录不存在");
        validate(record);
        record.setPromotionId(id);
        if (record.getPromotionNo() == null || record.getPromotionNo().isBlank()) {
            record.setPromotionNo(exist.getPromotionNo());
        }
        if (record.getPromotionDate() == null) {
            record.setPromotionDate(exist.getPromotionDate());
        }
        updateById(record);
        replaceTargets(record);
    }

    @Override
    @Transactional
    public void removePromotion(Long id) {
        removeById(id);
        promotionTargetMapper.delete(new QueryWrapper<PromotionTarget>().eq("promotion_id", id));
    }

    @Override
    public Map<String, Object> weekStats(LocalDate anyDayInWeek, Long staffId,
                                         DataScopeResolver.DataScopeContext scopeCtx) {
        LocalDate day = anyDayInWeek == null ? LocalDate.now() : anyDayInWeek;
        // 周口径：周一 00:00 ~ 周日 23:59（北京时间）
        LocalDate monday = day.minusDays(day.getDayOfWeek().getValue() - 1L);
        LocalDate sunday = monday.plusDays(6);

        QueryWrapper<PromotionRecord> wrapper = new QueryWrapper<>();
        wrapper.ge("promotion_date", monday).le("promotion_date", sunday);
        if (staffId != null) wrapper.eq("staff_id", staffId);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        List<PromotionRecord> list = list(wrapper);

        int promotionCount = 0;               // 计入「每周朋友圈推广次数」的记录数
        Map<Long, String> typeById = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        for (PromotionRecord r : list) {
            ids.add(r.getPromotionId());
            typeById.put(r.getPromotionId(), r.getMaterialType() == null ? "" : r.getMaterialType());
            boolean hasShot = r.getAttachment() != null && !r.getAttachment().isBlank();
            if (hasShot && MOMENT_TYPES.contains(r.getMaterialType())) {
                promotionCount++;
            }
        }

        Set<Long> coveredAll = new LinkedHashSet<>();
        Set<Long> coveredRfid = new LinkedHashSet<>();
        if (!ids.isEmpty()) {
            List<PromotionTarget> targets = promotionTargetMapper.selectList(
                    new QueryWrapper<PromotionTarget>().in("promotion_id", ids));
            for (PromotionTarget t : targets) {
                if (t.getCustomerId() == null) continue;
                coveredAll.add(t.getCustomerId());
                if (RFID_TYPE.equals(typeById.get(t.getPromotionId()))) {
                    coveredRfid.add(t.getCustomerId());
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weekStart", monday.toString());
        data.put("weekEnd", sunday.toString());
        data.put("promotionCount", promotionCount);
        data.put("recordCount", list.size());
        data.put("coveredCustomerCount", coveredAll.size());
        data.put("rfidCoveredCustomerCount", coveredRfid.size());
        data.put("weeklyPromotionTarget", sysConfigService.getIntValue("sales.process.weekly_promotion", 3));
        return data;
    }

    @Override
    public String generatePromotionNo() {
        return sysSerialNoConfigService.generateOrDefault("CRM_PROMOTION", () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "PR-" + dateStr + "-";
            QueryWrapper<PromotionRecord> wrapper = new QueryWrapper<>();
            wrapper.likeRight("promotion_no", prefix).orderByDesc("promotion_no").last("LIMIT 1");
            PromotionRecord last = getOne(wrapper, false);
            int seq = 1;
            if (last != null && last.getPromotionNo() != null) {
                try {
                    seq = Integer.parseInt(last.getPromotionNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    // ==================== 私有方法 ====================

    private void validate(PromotionRecord record) {
        if (record.getMaterialType() == null || record.getMaterialType().isBlank()) {
            throw new BusinessException("素材类型不能为空");
        }
        if (!MATERIAL_TYPES.contains(record.getMaterialType())) {
            throw new BusinessException("无效的素材类型：" + record.getMaterialType());
        }
    }

    /** 落明细 + 回填数量与达标标志（截图非空是朋友圈类达标的前置条件） */
    private void replaceTargets(PromotionRecord record) {
        Long pid = record.getPromotionId();
        if (pid == null) return;
        promotionTargetMapper.delete(new QueryWrapper<PromotionTarget>().eq("promotion_id", pid));

        List<Long> customerIds = record.getCustomerIds();
        int count = 0;
        if (customerIds != null && !customerIds.isEmpty()) {
            List<Long> distinct = customerIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
            Map<Long, String> nameById = new HashMap<>();
            if (!distinct.isEmpty()) {
                List<Customer> customers = customerService.listByIds(distinct);
                for (Customer c : customers) {
                    nameById.put(c.getCustomerId(), c.getCustomerName());
                }
            }
            for (Long cid : distinct) {
                PromotionTarget t = new PromotionTarget();
                t.setPromotionId(pid);
                t.setCustomerId(cid);
                t.setCustomerName(nameById.get(cid));
                promotionTargetMapper.insert(t);
                count++;
            }
        }

        boolean hasShot = record.getAttachment() != null && !record.getAttachment().isBlank();
        int qualified = (!MOMENT_TYPES.contains(record.getMaterialType()) || hasShot) ? 1 : 0;

        PromotionRecord patch = new PromotionRecord();
        patch.setPromotionId(pid);
        patch.setTargetCount(count);
        patch.setIsQualified(qualified);
        updateById(patch);
        record.setTargetCount(count);
        record.setIsQualified(qualified);
    }

    /** 列表回填发送对象客户名（避免 N+1，按批次一次查） */
    private void fillCustomerNames(List<PromotionRecord> records) {
        if (records == null || records.isEmpty()) return;
        List<Long> ids = records.stream().map(PromotionRecord::getPromotionId).collect(Collectors.toList());
        List<PromotionTarget> targets = promotionTargetMapper.selectList(
                new QueryWrapper<PromotionTarget>().in("promotion_id", ids));
        Map<Long, List<String>> namesById = new HashMap<>();
        for (PromotionTarget t : targets) {
            namesById.computeIfAbsent(t.getPromotionId(), k -> new ArrayList<>()).add(t.getCustomerName());
        }
        for (PromotionRecord r : records) {
            List<String> names = namesById.get(r.getPromotionId());
            if (names != null) {
                r.setCustomerNames(names.stream().filter(Objects::nonNull).collect(Collectors.joining("、")));
            }
        }
    }
}
