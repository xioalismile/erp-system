package com.erp.license.entity;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * License 授权信息载体（对应 license.lic 文件解析后的内容）
 * <p>
 * 字段说明：
 * - licenseId：授权证书唯一编号（UUID 形式）
 * - customer：客户名称
 * - projectName：项目名称
 * - macAddresses：授权绑定的网卡 MAC 地址白名单（空表示不绑定）
 * - issueDate：颁发日期
 * - expireDate：到期日期（到期后系统停止服务）
 * - maxUsers：最大并发用户数（0 表示不限制）
 * - features：授权功能模块列表（如 ["sale","purchase","finance"]）
 * - signature：RSA 签名（对上述字段拼接后的 SHA256withRSA 签名，Base64 编码）
 */
@Data
public class LicenseInfo {
    /** 授权证书编号 */
    private String licenseId;

    /** 客户名称 */
    private String customer;

    /** 项目名称 */
    private String projectName;

    /** 授权 MAC 地址白名单（空列表表示不绑定硬件） */
    private List<String> macAddresses;

    /** 颁发日期 */
    private LocalDate issueDate;

    /** 到期日期 */
    private LocalDate expireDate;

    /** 最大用户数（0=不限） */
    private int maxUsers;

    /** 授权功能模块列表 */
    private List<String> features;

    /** RSA 签名（Base64） */
    private String signature;

    /**
     * 拼接待签名内容（顺序必须与生成端一致）
     */
    public String toSignPayload() {
        return String.join("|",
                licenseId == null ? "" : licenseId,
                customer == null ? "" : customer,
                projectName == null ? "" : projectName,
                macAddresses == null ? "" : String.join(",", macAddresses),
                issueDate == null ? "" : issueDate.toString(),
                expireDate == null ? "" : expireDate.toString(),
                String.valueOf(maxUsers),
                features == null ? "" : String.join(",", features)
        );
    }
}
