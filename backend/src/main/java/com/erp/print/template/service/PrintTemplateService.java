package com.erp.print.template.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.print.template.dto.PrintTemplateDTO;
import com.erp.print.template.entity.PrintTemplate;
import com.erp.print.template.entity.PrintTemplateField;

import java.util.List;
import java.util.Map;

public interface PrintTemplateService extends IService<PrintTemplate> {

    Page<PrintTemplate> pageQuery(int current, int size, String keyword, String docType, Integer status, String sortBy, String sortOrder);

    PrintTemplateDTO getDetail(Long id);

    PrintTemplate getDefaultTemplate(String docType);

    void saveTemplate(PrintTemplateDTO templateDTO);

    void updateTemplate(PrintTemplateDTO templateDTO);

    void deleteTemplate(Long id);

    void setDefault(Long id);

    Long copyTemplate(Long id, String newName);

    List<PrintTemplateField> getTemplateFields(Long templateId);

    String renderTemplate(Long templateId, Map<String, Object> data);

    /**
     * 设计态预览：用样例数据渲染一份尚未保存的模板内容。
     * 与真实打印走同一套渲染逻辑，保证「预览所见 = 打印所得」。
     */
    String previewHtml(PrintTemplateDTO templateDTO);

    /**
     * 按单据打印: 自动查找默认模板 → 获取单据数据 → 渲染HTML
     *
     * @param billType 单据类型 (如 SALE_ORDER)
     * @param billId   单据ID
     * @return 渲染后的完整HTML
     */
    String printByBill(String billType, Long billId);

    /**
     * 按单据打印（带二维码域名）：qrBaseUrl 用于把 {{qr:...}} 占位渲染成可扫的完整网址；
     * 为空时退化为站内相对地址（仍可被系统内扫码解析）。
     */
    String printByBill(String billType, Long billId, String qrBaseUrl);

    /**
     * 按指定模板打印单据
     *
     * @param templateId 模板ID
     * @param billType   单据类型
     * @param billId     单据ID
     * @return 渲染后的完整HTML
     */
    String printByTemplate(Long templateId, String billType, Long billId);

    /** 按指定模板打印单据（带二维码域名） */
    String printByTemplate(Long templateId, String billType, Long billId, String qrBaseUrl);
}
