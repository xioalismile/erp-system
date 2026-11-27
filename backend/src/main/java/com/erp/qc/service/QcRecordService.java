package com.erp.qc.service;

import com.erp.common.PageResult;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.entity.QcRecordItem;

import java.util.List;
import java.util.Map;

/**
 * 工序检验记录查询服务：只读的报表数据源（分页列表 + 汇总）。
 *
 * <p>检验记录由工序报工/检验流程写入 {@code qc_record}，本服务只负责「生产工序质检报表」的查询展示，
 * 不做任何写操作。汇总口径与「经营看板 → 质量」块保持一致：
 * 检验批次 = COUNT(*)，不合格批次 = result='不合格' 的行数，合格率 = (检验批次 − 不合格批次) / 检验批次
 * （分母为 0 时返回空值，不用 0 冒充）。</p>
 */
public interface QcRecordService {

    /** 分页查询工序检验记录（日期按检验时间，缺省回落创建时间；均支持模糊筛选） */
    PageResult<QcRecord> pageQuery(int current, int size,
                                   String dateFrom, String dateTo,
                                   String qcType, String result,
                                   String orderNo, String operationName,
                                   String inspectorName, String keyword);

    /** 汇总（与分页同一套筛选条件）：检验批次 / 合格批次 / 不合格批次 / 合格率 / 检验总数 / 合格数 / 不合格数 */
    Map<String, Object> summary(String dateFrom, String dateTo,
                                String qcType, String result,
                                String orderNo, String operationName,
                                String inspectorName, String keyword);

    /** 某条检验记录的实测明细（逐项实测值 + 单项判定 + 留证图片），按行号升序 */
    List<QcRecordItem> listItems(Long recordId);
}
