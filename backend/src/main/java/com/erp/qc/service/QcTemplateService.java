package com.erp.qc.service;

import com.erp.common.PageResult;
import com.erp.qc.entity.QcTemplate;

import java.util.List;

/**
 * 质检模板服务：模板头 + 检验项明细的增删改查，以及「按工序解析应使用的模板」。
 *
 * <p>解析优先级（用户口径）：① 工单工序上显式绑定的模板 → ② 按工序名称绑定 → ③ 按物料绑定 → ④ 返回空（不绑定）。</p>
 */
public interface QcTemplateService {

    /** 分页查询模板（keyword 匹配编码/名称；qcType 质检类型；isEnabled 启停） */
    PageResult<QcTemplate> pageQuery(int current, int size, String keyword, String qcType, Integer isEnabled);

    /** 详情（含检验项明细 items + 适用物料名称 materialName） */
    QcTemplate getDetail(Long templateId);

    /** 新增模板（编码留空自动生成），返回模板ID */
    Long createTemplate(QcTemplate template);

    /** 编辑模板（整单覆盖：先删旧检验项再插新行） */
    void updateTemplate(Long templateId, QcTemplate template);

    /** 删除模板（级联删除检验项明细） */
    void deleteTemplate(Long templateId);

    /** 启用中的模板全量列表（下拉/选择用；不含单据级模板） */
    List<QcTemplate> listEnabled();

    /** 单据级模板列表：已绑定该单据类型且启用（入库单点质检时选择模板用） */
    List<QcTemplate> listByBillType(String billType);

    /** 取某道工单工序应使用的质检模板；按工序绑定优先，其次按物料绑定，都没有返回 null */
    QcTemplate resolveTemplateForOperation(Long operationRowId);

    /**
     * 取某物料应使用的质检模板（化妆品批记录·批次检验用）。
     * 优先级：① 按物料绑定 → ② 适用分类（该物料所属分类及其上级） → ③ 返回 null。
     */
    QcTemplate resolveTemplateForMaterial(Long materialId);
}
