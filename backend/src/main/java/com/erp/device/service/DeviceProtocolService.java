package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.device.entity.DeviceProtocolTemplate;
import com.erp.device.mapper.DeviceProtocolTemplateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** 协议模板：系统预置只读可复制，租户模板可编辑 */
@Service
@RequiredArgsConstructor
public class DeviceProtocolService extends ServiceImpl<DeviceProtocolTemplateMapper, DeviceProtocolTemplate> {

    private static final Set<String> PROTOCOLS = Set.of(
            "MODBUS_TCP", "MODBUS_RTU", "OPC_UA", "MQTT", "HTTP", "TCP", "SERIAL");
    private static final Set<String> FORMATS = Set.of(
            "JSON", "FIXED", "DELIMITER", "REGEX", "SCRIPT");

    private final ObjectMapper objectMapper;

    public PageResult<DeviceProtocolTemplate> pageQuery(int current, int size, String keyword, String protocolType) {
        QueryWrapper<DeviceProtocolTemplate> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like("template_code", kw).or().like("template_name", kw));
        }
        if (protocolType != null && !protocolType.isBlank()) qw.eq("protocol_type", protocolType);
        qw.orderByDesc("is_preset").orderByAsc("template_code");
        Page<DeviceProtocolTemplate> page = page(new Page<>(current, size), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    public List<DeviceProtocolTemplate> listEnabled() {
        return list(new QueryWrapper<DeviceProtocolTemplate>()
                .eq("status", "启用").orderByDesc("is_preset").orderByAsc("template_code"));
    }

    public DeviceProtocolTemplate create(DeviceProtocolTemplate t) {
        t.setIsPreset(0);
        if (t.getTemplateCode() == null || t.getTemplateCode().isBlank()) {
            t.setTemplateCode(nextCode());
        }
        validate(t, null);
        save(t);
        return getById(t.getId());
    }

    public DeviceProtocolTemplate update(Long id, DeviceProtocolTemplate t) {
        DeviceProtocolTemplate old = getById(id);
        if (old == null) throw new BusinessException("协议模板不存在");
        if (old.getIsPreset() != null && old.getIsPreset() == 1) {
            throw new BusinessException("系统预置模板只读，请先「复制为我的模板」再修改");
        }
        t.setTemplateCode(old.getTemplateCode());
        validate(t, id);
        old.setTemplateName(t.getTemplateName());
        old.setProtocolType(t.getProtocolType());
        old.setConfigJson(t.getConfigJson());
        old.setParseFormat(t.getParseFormat());
        old.setByteOrder(t.getByteOrder());
        old.setWordOrder(t.getWordOrder());
        old.setTimestampSource(t.getTimestampSource());
        old.setStatus(t.getStatus());
        old.setRemark(t.getRemark());
        updateById(old);
        return getById(id);
    }

    public DeviceProtocolTemplate copy(Long id) {
        DeviceProtocolTemplate src = getById(id);
        if (src == null) throw new BusinessException("协议模板不存在");
        DeviceProtocolTemplate t = new DeviceProtocolTemplate();
        t.setTemplateCode(nextCode());
        t.setTemplateName(src.getTemplateName() + "（副本）");
        t.setProtocolType(src.getProtocolType());
        t.setIsPreset(0);
        t.setConfigJson(src.getConfigJson());
        t.setParseFormat(src.getParseFormat());
        t.setByteOrder(src.getByteOrder());
        t.setWordOrder(src.getWordOrder());
        t.setTimestampSource(src.getTimestampSource());
        t.setStatus("启用");
        t.setRemark(src.getRemark());
        save(t);
        return getById(t.getId());
    }

    public void delete(Long id) {
        DeviceProtocolTemplate old = getById(id);
        if (old == null) return;
        if (old.getIsPreset() != null && old.getIsPreset() == 1) {
            throw new BusinessException("系统预置模板不能删除");
        }
        removeById(id);
    }

    private void validate(DeviceProtocolTemplate t, Long selfId) {
        if (t.getTemplateName() == null || t.getTemplateName().isBlank()) {
            throw new BusinessException("请填写模板名称");
        }
        if (t.getProtocolType() == null || !PROTOCOLS.contains(t.getProtocolType())) {
            throw new BusinessException("协议类型不在支持范围（Modbus TCP/RTU、OPC UA、MQTT、HTTP、自定义 TCP/串口）");
        }
        if (t.getParseFormat() == null || !FORMATS.contains(t.getParseFormat())) {
            throw new BusinessException("报文格式只能是：JSON / 定长 / 分隔符 / 正则 / 脚本");
        }
        if (t.getStatus() == null || t.getStatus().isBlank()) t.setStatus("启用");
        if (t.getConfigJson() != null && !t.getConfigJson().isBlank()) {
            try {
                objectMapper.readTree(t.getConfigJson());
            } catch (Exception e) {
                throw new BusinessException("连接参数不是合法 JSON，请检查格式");
            }
        }
        QueryWrapper<DeviceProtocolTemplate> qw = new QueryWrapper<DeviceProtocolTemplate>()
                .eq("template_code", t.getTemplateCode());
        if (selfId != null) qw.ne("id", selfId);
        if (count(qw) > 0) throw new BusinessException("模板编码[" + t.getTemplateCode() + "]已存在");
    }

    private String nextCode() {
        long n = count() + 1;
        String code;
        do {
            code = String.format("TPL%04d", n++);
        } while (count(new QueryWrapper<DeviceProtocolTemplate>().eq("template_code", code)) > 0);
        return code;
    }
}
