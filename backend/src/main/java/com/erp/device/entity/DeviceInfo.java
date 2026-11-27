package com.erp.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备档案
 * 对齐 PG device_info 表
 *
 * 口径：
 * - 设备编码租户内唯一，自动生成可改；
 * - 工作中心与工位至少填一项，工位必须属于所选工作中心；
 * - 可执行工序为空 = 该设备不参与自动关联，只能人工认领；
 * - status 是设备启用状态（启用/停用/报废），不是实时运行状态。
 */
@Data
@TableName("device_info")
public class DeviceInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备编码（租户内唯一） */
    @TableField("device_code")
    private String deviceCode;

    /** 设备名称 */
    @TableField("device_name")
    private String deviceName;

    /** 设备分类ID */
    @TableField("category_id")
    private Long categoryId;

    @TableField("brand")
    private String brand;

    @TableField("model")
    private String model;

    /** 出厂编号 */
    @TableField("serial_no")
    private String serialNo;

    /** 资产编号 */
    @TableField("asset_no")
    private String assetNo;

    @TableField("department_id")
    private Long departmentId;

    /** 工作中心 */
    @TableField("work_center_id")
    private Long workCenterId;

    /** 工位（工序主档） */
    @TableField("workstation_id")
    private Long workstationId;

    /** 可执行工序ID，逗号分隔；空 = 不参与自动关联 */
    @TableField("allowed_operation_ids")
    private String allowedOperationIds;

    /** 设备责任人（员工档案） */
    @TableField("owner_employee_id")
    private Long ownerEmployeeId;

    @TableField("location")
    private String location;

    @TableField("purchase_date")
    private LocalDate purchaseDate;

    /** 保修到期日 */
    @TableField("warranty_end_date")
    private LocalDate warrantyEndDate;

    @TableField("supplier_id")
    private Long supplierId;

    /** 额定节拍（件/小时），性能稼动率与 OEE 用 */
    @TableField("rated_speed")
    private BigDecimal ratedSpeed;

    /** 报工阈值：累计多少件生成一张待确认报工 */
    @TableField("report_threshold")
    private Integer reportThreshold;

    /** 计数合并口径：SUM 分别求和 / PRIMARY_ONLY 只认主设备 */
    @TableField("count_merge_mode")
    private String countMergeMode;

    /** 是否自动开工：1 是 / 0 否 */
    @TableField("auto_start")
    private Integer autoStart;

    /** 停机超时是否自动暂停工序：1 是 / 0 否 */
    @TableField("auto_pause")
    private Integer autoPause;

    /** 是否关键设备 */
    @TableField("is_key_device")
    private Integer isKeyDevice;

    /** 启用状态：启用 / 停用 / 报废 */
    @TableField("status")
    private String status;

    @TableField("image_url")
    private String imageUrl;

    @TableField("remark")
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    // ---------------- 列表回显（非数据库字段） ----------------

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String workCenterName;

    @TableField(exist = false)
    private String workstationName;

    @TableField(exist = false)
    private String ownerEmployeeName;

    /** 可执行工序名称，逗号分隔（列表/详情回显） */
    @TableField(exist = false)
    private String allowedOperationNames;
}
