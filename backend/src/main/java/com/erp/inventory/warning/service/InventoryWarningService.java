package com.erp.inventory.warning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.export.dto.ImportResultDTO;
import com.erp.inventory.warning.dto.InventoryWarningImportCheckDTO;
import com.erp.inventory.warning.dto.InventoryWarningRuleBatchDTO;
import com.erp.inventory.warning.entity.InventoryWarningLog;
import com.erp.inventory.warning.entity.InventoryWarningRule;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface InventoryWarningService extends IService<InventoryWarningRule> {

    PageResult<InventoryWarningRule> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder);

    InventoryWarningRule getDetail(Long id);

    void saveRule(InventoryWarningRule rule);

    /** 批量新增：一次提交一批货品，每个货品生成一条规则 */
    void saveRuleBatch(InventoryWarningRuleBatchDTO dto);

    void updateRule(InventoryWarningRule rule);

    /** 生成导入模板（xlsx 字节流） */
    byte[] buildImportTemplate();

    /** 导入预检查（只读不写库） */
    InventoryWarningImportCheckDTO checkImport(MultipartFile file);

    /** 导入确认：onExistingConflict = SKIP 跳过已存在 / OVERWRITE 覆盖已存在 */
    ImportResultDTO confirmImport(MultipartFile file, String onExistingConflict);

    void deleteRule(Long id);

    void toggleStatus(Long id);

    void checkWarning();

    PageResult<InventoryWarningLog> getWarningList(int current, int size, Integer isHandled);

    void handleWarning(Long id, String handleRemark);

    Map<String, Object> getWarningStatistics();
}
