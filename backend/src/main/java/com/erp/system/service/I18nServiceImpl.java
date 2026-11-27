package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.system.entity.SysI18n;
import com.erp.system.mapper.SysI18nMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 多语言语言包服务实现（L1）
 * 安全：key/lang 白名单正则校验；译文长度限制；批量上限 200，防滥用与越权写入。
 */
@Slf4j
@Service
public class I18nServiceImpl extends ServiceImpl<SysI18nMapper, SysI18n> implements I18nService {

    private static final Pattern LANG_PATTERN = Pattern.compile("^[a-zA-Z]{2,10}(-[A-Za-z]{2,8})?$");
    // 键允许中文（field.<中文标题> 形态），长度放宽到 512 以容纳长句标题
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_\\u4e00-\\u9fa5.\\-]{1,512}$");
    private static final int MAX_BATCH = 200;
    private static final int MAX_TEXT_LEN = 500;

    @Override
    public Map<String, String> packageByLang(String lang) {
        if (lang == null || !LANG_PATTERN.matcher(lang).matches()) {
            throw new BusinessException("不支持的语言: " + lang);
        }
        List<SysI18n> rows = list(new QueryWrapper<SysI18n>()
                .eq("lang", lang)
                .orderByAsc("i18n_key"));
        Map<String, String> map = new LinkedHashMap<>();
        for (SysI18n r : rows) {
            if (r.getI18nKey() != null && r.getText() != null && !r.getText().isEmpty()) {
                map.put(r.getI18nKey(), r.getText());
            }
        }
        return map;
    }

    @Override
    public PageResult<SysI18n> pageItems(int current, int size, String keyword, String module, String lang) {
        String targetLang = (lang == null || lang.isBlank()) ? "en" : lang;
        if (!LANG_PATTERN.matcher(targetLang).matches()) {
            throw new BusinessException("不支持的语言: " + lang);
        }
        QueryWrapper<SysI18n> wrapper = new QueryWrapper<SysI18n>().eq("lang", targetLang);
        if (module != null && !module.isBlank()) {
            wrapper.eq("module", module);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("i18n_key", kw).or().like("text", kw));
        }
        wrapper.orderByAsc("i18n_key");
        Page<SysI18n> page = page(new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 500)), wrapper);

        // 回填中文原文对照（一次 in 查询，按 key 批量取 zh-CN）
        List<SysI18n> records = page.getRecords();
        if (!records.isEmpty()) {
            List<String> keys = records.stream().map(SysI18n::getI18nKey).filter(Objects::nonNull).collect(Collectors.toList());
            if (!keys.isEmpty()) {
                Map<String, String> zhMap = new HashMap<>();
                for (SysI18n zh : list(new QueryWrapper<SysI18n>().eq("lang", "zh-CN").in("i18n_key", keys))) {
                    zhMap.put(zh.getI18nKey(), zh.getText());
                }
                for (SysI18n r : records) {
                    r.setZhText(zhMap.get(r.getI18nKey()));
                }
            }
        }
        return PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional
    public int saveTranslations(List<SysI18n> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("没有需要保存的译文");
        }
        if (rows.size() > MAX_BATCH) {
            throw new BusinessException("单次最多保存 " + MAX_BATCH + " 条译文");
        }
        int affected = 0;
        for (SysI18n row : rows) {
            if (row == null) continue;
            String key = row.getI18nKey();
            String lang = row.getLang();
            if (key == null || !KEY_PATTERN.matcher(key).matches()) {
                throw new BusinessException("非法翻译键: " + key);
            }
            if (lang == null || !LANG_PATTERN.matcher(lang).matches()) {
                throw new BusinessException("非法语言: " + lang);
            }
            String text = row.getText() == null ? "" : row.getText();
            if (text.length() > MAX_TEXT_LEN) {
                throw new BusinessException("译文过长(>" + MAX_TEXT_LEN + "): " + key);
            }
            String module = (row.getModule() == null || row.getModule().isBlank()) ? "common" : row.getModule();

            SysI18n existing = getOne(new QueryWrapper<SysI18n>()
                    .eq("i18n_key", key)
                    .eq("lang", lang)
                    .last("LIMIT 1"));
            if (existing == null) {
                SysI18n fresh = new SysI18n();
                fresh.setI18nKey(key);
                fresh.setLang(lang);
                fresh.setText(text);
                fresh.setModule(module);
                save(fresh);
                affected++;
            } else {
                existing.setText(text);
                existing.setModule(module);
                updateById(existing);
                affected++;
            }
        }
        log.info("保存多语言译文: lang 批量 {} 条", affected);
        return affected;
    }
}
