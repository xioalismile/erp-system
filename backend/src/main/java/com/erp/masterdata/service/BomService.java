package com.erp.masterdata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.masterdata.entity.Bom;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BomService extends IService<Bom> {

    /** 保存BOM主单 + 明细 */
    void saveBomWithItems(Bom bom, List<com.erp.masterdata.entity.BomItem> items);

    /** 分页查询（支持 UI 列设置中的多条件过滤） */
    PageResult<Bom> pageQuery(int current, int size, String keyword,
                              String bomCategory, String status, String isEnabled,
                              LocalDate dateFrom, LocalDate dateTo, String sortBy, String sortOrder, String customSearch);

    /** 获取BOM详情(含明细) */
    Bom getDetail(Long id);

    /** BOM 树形展开 - 递归获取所有层级子件（按物料编码自动查找生效BOM） */
    Map<String, Object> expandBom(String materialCode, BigDecimal quantity);

    /** BOM 树形展开 - 从指定BOM开始展开（列表页"展开"按钮用，保证展开的就是当前行BOM） */
    Map<String, Object> expandBomByBomId(Long bomId, BigDecimal quantity);

    /** BOM 扁平化展开 - 所有层级子件平铺, 按物料聚合 */
    List<Map<String, Object>> flattenBom(String materialCode, BigDecimal quantity);

    /** 提交审核: 草稿 → 待审批 (等待审核人审批) */
    void submit(Long id, String operator);

    /** 审核: 待审批 → 已生效 (审核通过) */
    void approve(Long id, String approver);

    /** 重置草稿: 已生效 → 草稿 (打回重新编辑) */
    void reject(Long id, String operator);

    /** 重置草稿: 已生效 → 草稿 */
    void reverseApprove(Long id, String operator);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 停用: 已生效 → 已停用 */
    void disable(Long id, String operator);

    /** 启用: 已停用 → 已生效 */
    void enable(Long id, String operator);

    /** 设置为默认BOM - 同一物料下唯一启用为默认 */
    void setDefault(Long id, String operator);

    /** 批量提交审核: 草稿 → 待审批 */
    int batchSubmit(List<Long> ids, String operator);

    /** 批量审核: 待审批 → 已生效 */
    int batchApprove(List<Long> ids, String operator);

    /** 批量更新: 启用/禁用 */
    int batchUpdateStatus(List<Long> ids, String status, String operator);

    /** 更新BOM所有子项的损耗率 */
    int updateScrapRate(Long id, BigDecimal scrapRate, String operator);

    /** 单号生成 */
    String generateBomCode();
}
