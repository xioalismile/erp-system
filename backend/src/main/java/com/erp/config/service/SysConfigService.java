package com.erp.config.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.config.entity.SysConfig;

import java.util.List;

public interface SysConfigService extends IService<SysConfig> {

    String getValue(String key);

    /** getConfigValue 是 getValue 的别名，提供给旧代码使用 */
    default String getConfigValue(String key) {
        return getValue(key);
    }

    String getValue(String key, String defaultValue);

    int getIntValue(String key, int defaultValue);

    boolean getBooleanValue(String key, boolean defaultValue);

    boolean updateValue(String key, String value);

    List<SysConfig> listByGroup(String group);

    List<SysConfig> listAll();

    boolean batchUpdate(List<SysConfig> list);

    void refreshCache();
}
