package com.erp.system.service;

import com.erp.common.PageResult;
import com.erp.system.entity.SysI18n;

import java.util.List;
import java.util.Map;

/**
 * 多语言语言包服务（L1）
 */
public interface I18nService {

    /** 按语言取整包（匿名可用，登录页也要翻译）：key -> text */
    Map<String, String> packageByLang(String lang);

    /** 超管分页查询：keyword 匹配 key/译文，module 可选，lang 目标语言；返回记录带 zhText 对照 */
    PageResult<SysI18n> pageItems(int current, int size, String keyword, String module, String lang);

    /** 超管批量保存译文（key+lang 幂等 upsert），返回写入条数 */
    int saveTranslations(List<SysI18n> rows);
}
