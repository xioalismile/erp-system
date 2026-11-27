package com.erp.board.custom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自定义看板 / 工作台自定义卡（2026-11-05 新增，独立于报表中心 custom_dashboard）。
 *
 * <p>board_type = 'board'：数据看板 → 自定义看板（一整套卡片画布）；
 * board_type = 'workbench'：工作台首页设置里的自定义卡片（一行 = 一张卡）。</p>
 *
 * <p>layout_json 结构：{"cards":[{id,type,title,icon,color,size,source,timeField,period,dim,field,agg,tokens,compare,style,fields,rows}]}。
 * 卡片取数只接受数据源注册表白名单内的表与列（见 BoardDataAggregator）。</p>
 */
@Data
@TableName("custom_board")
public class CustomBoard {

    @TableId(value = "board_id", type = IdType.AUTO)
    private Long boardId;

    /** board=自定义看板 / workbench=工作台自定义卡 / page=现有看板页内的卡片配置 */
    @TableField("board_type")
    private String boardType;

    /** 现有看板页标识（board_type=page 时生效）：finance / sale / inventory / operation */
    @TableField("page_key")
    private String pageKey;

    /** 编码：CB+日期+流水，自动生成 */
    @TableField("board_code")
    private String boardCode;

    @TableField("board_name")
    private String boardName;

    @TableField("layout_json")
    private String layoutJson;

    /** 启用/停用 */
    @TableField("status")
    private String status;

    /** 私有/共享 */
    @TableField("share_scope")
    private String shareScope;

    /** 共享角色编码，逗号分隔（share_scope=共享时生效） */
    @TableField("share_roles")
    private String shareRoles;

    @TableField("created_by")
    private String createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("remark")
    private String remark;
}
