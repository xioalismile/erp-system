package com.erp.license.util;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 网卡 MAC 地址工具类
 * <p>
 * 用于 License 硬件绑定校验：
 * - 启动时获取本机所有非虚拟网卡 MAC 地址
 * - 与 LicenseInfo.macAddresses 白名单比对
 * - 任一 MAC 命中即通过（支持多网卡机器）
 */
public final class MacAddressUtil {

    private MacAddressUtil() {}

    /**
     * 获取本机所有非虚拟网卡的 MAC 地址（去掉冒号大写）
     *
     * @return MAC 地址列表（如 ["001122334455", "AABBCCDDEEFF"]）
     */
    public static List<String> getLocalMacAddresses() {
        List<String> macs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;
                byte[] mac = ni.getHardwareAddress();
                if (mac == null) continue;
                StringBuilder sb = new StringBuilder();
                for (byte b : mac) {
                    sb.append(String.format("%02X", b));
                }
                macs.add(sb.toString());
            }
        } catch (Exception e) {
            // ignore
        }
        return macs;
    }

    /**
     * 校验本机 MAC 是否在白名单中
     *
     * @param allowedMacList 授权 MAC 白名单（去冒号大写格式）
     * @return true 表示本机 MAC 命中白名单（或白名单为空不限制）
     */
    public static boolean isLocalMacAllowed(List<String> allowedMacList) {
        if (allowedMacList == null || allowedMacList.isEmpty()) {
            return true; // 未配置白名单，不限制硬件
        }
        List<String> localMacs = getLocalMacAddresses();
        return localMacs.stream().anyMatch(allowedMacList::contains);
    }
}
