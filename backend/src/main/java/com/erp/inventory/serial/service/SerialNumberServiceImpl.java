package com.erp.inventory.serial.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.inventory.serial.entity.SerialNumber;
import com.erp.inventory.serial.entity.SerialNumberTrace;
import com.erp.inventory.serial.mapper.SerialNumberMapper;
import com.erp.inventory.serial.mapper.SerialNumberTraceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class SerialNumberServiceImpl extends ServiceImpl<SerialNumberMapper, SerialNumber> implements SerialNumberService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "batch_no",
            "create_time",
            "created_at",
            "expiry_date",
            "id",
            "inbound_id",
            "inbound_no",
            "location_id",
            "location_name",
            "material_code",
            "material_id",
            "material_name",
            "outbound_id",
            "outbound_no",
            "production_date",
            "remark",
            "serial_no",
            "specification",
            "status",
            "supplier_id",
            "supplier_name",
            "unit",
            "update_time",
            "warehouse_id",
            "warehouse_name"
        );

    private final SerialNumberTraceMapper traceMapper;

    @Override
    public PageResult<SerialNumber> pageQuery(int current, int size, String keyword, Integer status, Long materialId, Long warehouseId, String sortBy, String sortOrder) {
        Page<SerialNumber> page = new Page<>(current, size);
        QueryWrapper<SerialNumber> wrapper = new QueryWrapper<>();
        
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("serial_no", keyword)
                    .or().like("material_name", keyword)
                    .or().like("batch_no", keyword));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (materialId != null) {
            wrapper.eq("material_id", materialId);
        }
        if (warehouseId != null) {
            wrapper.eq("warehouse_id", warehouseId);
        }
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SerialNumber.class);
        
        Page<SerialNumber> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SerialNumber getDetail(Long id) {
        return getById(id);
    }

    @Override
    public List<SerialNumberTrace> getTraceList(Long serialNumberId) {
        return traceMapper.selectBySerialNumberId(serialNumberId);
    }

    @Override
    @Transactional
    public void createSerialNumbers(List<SerialNumber> list) {
        if (list == null || list.isEmpty()) {
            throw new BusinessException("序列号列表不能为空");
        }
        
        for (SerialNumber serial : list) {
            if (serial.getSerialNo() == null || serial.getSerialNo().isBlank()) {
                throw new BusinessException("序列号不能为空");
            }
            SerialNumber existing = baseMapper.selectBySerialNo(serial.getSerialNo());
            if (existing != null) {
                throw new BusinessException("序列号已存在: " + serial.getSerialNo());
            }
            if (serial.getStatus() == null) {
                serial.setStatus(0);
            }
        }
        
        saveBatch(list);
        log.info("批量创建序列号成功，数量: {}", list.size());
        
        for (SerialNumber serial : list) {
            SerialNumberTrace trace = new SerialNumberTrace();
            trace.setSerialNumberId(serial.getId());
            trace.setSerialNo(serial.getSerialNo());
            trace.setOperationType("INBOUND");
            trace.setWarehouseId(serial.getWarehouseId());
            trace.setWarehouseName(serial.getWarehouseName());
            trace.setLocationId(serial.getLocationId());
            trace.setLocationName(serial.getLocationName());
            trace.setRelatedDocId(serial.getInboundId());
            trace.setRelatedDocNo(serial.getInboundNo());
            trace.setRelatedDocType("INBOUND");
            trace.setOperationTime(LocalDateTime.now());
            trace.setRemark(serial.getRemark());
            traceMapper.insert(trace);
        }
    }

    @Override
    @Transactional
    public void outboundSerialNumbers(List<String> serialNos, Map<String, Object> outboundDoc) {
        if (serialNos == null || serialNos.isEmpty()) {
            throw new BusinessException("序列号列表不能为空");
        }
        
        Long outboundId = outboundDoc != null ? (Long) outboundDoc.get("id") : null;
        String outboundNo = outboundDoc != null ? (String) outboundDoc.get("no") : null;
        
        for (String serialNo : serialNos) {
            SerialNumber serial = baseMapper.selectBySerialNo(serialNo);
            if (serial == null) {
                throw new BusinessException("序列号不存在: " + serialNo);
            }
            if (serial.getStatus() != 0) {
                throw new BusinessException("序列号状态异常，无法出库: " + serialNo);
            }
            
            serial.setStatus(1);
            serial.setOutboundId(outboundId);
            serial.setOutboundNo(outboundNo);
            updateById(serial);
            
            SerialNumberTrace trace = new SerialNumberTrace();
            trace.setSerialNumberId(serial.getId());
            trace.setSerialNo(serial.getSerialNo());
            trace.setOperationType("OUTBOUND");
            trace.setWarehouseId(serial.getWarehouseId());
            trace.setWarehouseName(serial.getWarehouseName());
            trace.setLocationId(serial.getLocationId());
            trace.setLocationName(serial.getLocationName());
            trace.setRelatedDocId(outboundId);
            trace.setRelatedDocNo(outboundNo);
            trace.setRelatedDocType("OUTBOUND");
            trace.setOperationTime(LocalDateTime.now());
            traceMapper.insert(trace);
        }
        
        log.info("序列号出库成功，数量: {}", serialNos.size());
    }

    @Override
    @Transactional
    public void transferSerialNumbers(List<String> serialNos, Long targetWarehouseId, String targetWarehouseName, Long targetLocationId, String targetLocationName) {
        if (serialNos == null || serialNos.isEmpty()) {
            throw new BusinessException("序列号列表不能为空");
        }
        
        for (String serialNo : serialNos) {
            SerialNumber serial = baseMapper.selectBySerialNo(serialNo);
            if (serial == null) {
                throw new BusinessException("序列号不存在: " + serialNo);
            }
            if (serial.getStatus() != 0) {
                throw new BusinessException("序列号状态异常，无法调拨: " + serialNo);
            }
            
            Long oldWarehouseId = serial.getWarehouseId();
            String oldWarehouseName = serial.getWarehouseName();
            Long oldLocationId = serial.getLocationId();
            String oldLocationName = serial.getLocationName();
            
            serial.setWarehouseId(targetWarehouseId);
            serial.setWarehouseName(targetWarehouseName);
            serial.setLocationId(targetLocationId);
            serial.setLocationName(targetLocationName);
            updateById(serial);
            
            SerialNumberTrace trace = new SerialNumberTrace();
            trace.setSerialNumberId(serial.getId());
            trace.setSerialNo(serial.getSerialNo());
            trace.setOperationType("TRANSFER");
            trace.setWarehouseId(targetWarehouseId);
            trace.setWarehouseName(targetWarehouseName);
            trace.setLocationId(targetLocationId);
            trace.setLocationName(targetLocationName);
            trace.setRelatedDocType("TRANSFER");
            trace.setOperationTime(LocalDateTime.now());
            trace.setRemark("从 " + oldWarehouseName + " " + oldLocationName + " 调拨至 " + targetWarehouseName + " " + targetLocationName);
            traceMapper.insert(trace);
        }
        
        log.info("序列号调拨成功，数量: {}", serialNos.size());
    }

    @Override
    @Transactional
    public void scrapSerialNumbers(List<String> serialNos, String remark) {
        if (serialNos == null || serialNos.isEmpty()) {
            throw new BusinessException("序列号列表不能为空");
        }
        
        for (String serialNo : serialNos) {
            SerialNumber serial = baseMapper.selectBySerialNo(serialNo);
            if (serial == null) {
                throw new BusinessException("序列号不存在: " + serialNo);
            }
            if (serial.getStatus() != 0) {
                throw new BusinessException("序列号状态异常，无法报废: " + serialNo);
            }
            
            serial.setStatus(2);
            serial.setRemark(remark);
            updateById(serial);
            
            SerialNumberTrace trace = new SerialNumberTrace();
            trace.setSerialNumberId(serial.getId());
            trace.setSerialNo(serial.getSerialNo());
            trace.setOperationType("SCRAP");
            trace.setWarehouseId(serial.getWarehouseId());
            trace.setWarehouseName(serial.getWarehouseName());
            trace.setLocationId(serial.getLocationId());
            trace.setLocationName(serial.getLocationName());
            trace.setRelatedDocType("SCRAP");
            trace.setOperationTime(LocalDateTime.now());
            trace.setRemark(remark);
            traceMapper.insert(trace);
        }
        
        log.info("序列号报废成功，数量: {}", serialNos.size());
    }

    @Override
    public SerialNumber getSerialNumberByNo(String serialNo) {
        return baseMapper.selectBySerialNo(serialNo);
    }

    @Override
    public boolean checkSerialNumberExists(String serialNo) {
        SerialNumber serial = baseMapper.selectAvailableBySerialNo(serialNo);
        return serial != null;
    }
}
