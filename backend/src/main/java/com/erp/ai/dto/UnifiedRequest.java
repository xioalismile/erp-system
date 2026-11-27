package com.erp.ai.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 统一AI请求 - 所有AI渠道的请求都适配为这个结构
 */
@Data
public class UnifiedRequest {
    /** 全链路追踪ID */
    private String traceId;
    /** 渠道: WEB / QWEN / DEEPSEEK / OLLAMA / CUSTOM */
    private String channel;
    /** 自定义模型服务商ID（内置渠道为空）；用于多厂商路由 */
    private String providerId;
    /** 用户选择的模型名（空=用渠道默认模型或服务商第一个模型） */
    private String model;
    /** 本轮是否显式开启深度思考（2026-10-24 积分制：开启才乘深度思考倍率） */
    private Boolean deepThinking;
    /**
     * 思考强度（2026-11-22 照参考设计三档）：light=标准直答 / high=深度思考 / max=更长思考空间+更高输出上限。
     * 旧前端不传时按 deepThinking 布尔兜底（true=high / false=light），行为与升级前一致。
     */
    private String thoughtLevel;
    /**
     * 本次请求的上下文窗口覆盖（tokens，2026-11-22 三档：标准=不传走设置中心全局默认；加大=512000；最大=1000000）。
     * 范围 8000~1000000，越界回退全局默认；模型窗口小于所选档位时按模型实际能力生效（上游超窗会报错并转中文提示）。
     */
    private Integer contextLimit;
    /** 渠道用户ID */
    private String channelUserId;
    /** ERP用户ID(经身份映射后) */
    private Long erpUserId;
    /** 会话ID(多轮对话) */
    private String sessionId;
    /** 用户原话/输入文本 */
    private String userInput;
    /** 输入类型: TEXT / VOICE / IMAGE */
    private String inputType;
    /** 图片URL列表（视觉识别用） */
    private List<String> imageUrls;
    /**
     * 上传的非图片附件列表（如 BOM 的 Excel/CSV），元素形如 {name, url}
     * url 为系统内上传地址（以 /uploads/ 开头），供 bom_file_read 等"需要文件"的工具读取。
     * 旧前端/其它渠道不传时为空列表，行为与升级前一致。
     */
    private List<Map<String, String>> files;
    /** 渠道上下文 */
    private Map<String, Object> context;
}
