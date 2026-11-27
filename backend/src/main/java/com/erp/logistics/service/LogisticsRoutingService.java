package com.erp.logistics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 物流通道路由：决定一个单号走「快递100（国内）」还是「17TRACK（国际）」。
 *
 * 判定顺序（命中即返回）：
 *   1. 快递100 的 enabled 是「物流查询功能」总开关：关闭 = 所有查询返回未启用（等价升级前的旧行为），
 *      绝不在总开关关闭时把单据转去 17TRACK（那会按次扣额度，属于误扣费）
 *   2. 17TRACK 未启用 → 一律快递100
 *   3. 单号符合万国邮联 S10 国际格式（2字母+9数字+2字母国别码）→ 17TRACK
 *   4. 承运商名称命中「国际承运商关键字」清单 → 17TRACK
 *   5. 其余 → 快递100
 *
 * 说明：S10 规则放在承运商判断之前，是为了区分「国际EMS」与「国内EMS」——
 * 国内EMS是纯数字单号，不会被 S10 命中，仍走快递100，保持旧行为不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsRoutingService {

    public static final String CHANNEL_KD100 = "KD100";
    public static final String CHANNEL_17TRACK = "17TRACK";

    /** 万国邮联 S10 单号格式：2位字母 + 9位数字 + 2位国家代码，如 RR123456789CN */
    private static final Pattern S10_PATTERN = Pattern.compile("^[A-Z]{2}\\d{9}[A-Z]{2}$");

    private final Kuaidi100Service kuaidi100Service;
    private final SeventeenTrackService seventeenTrackService;

    /**
     * 解析该单号应使用的通道
     *
     * @param carrier    承运商名称（可为空）
     * @param trackingNo 物流单号（可为空）
     * @return KD100 / 17TRACK
     */
    public String resolveChannel(String carrier, String trackingNo) {
        boolean kdEnabled = kuaidi100Service.isServiceEnabled();
        boolean t17Enabled = seventeenTrackService.isServiceEnabled();

        // 总开关关闭（或国际通道未启用）→ 走快递100：总开关关闭时快递100会返回"未启用"提示，
        // 与界面文案一致，也避免总开关关掉后国内单被转去17TRACK按次扣额度
        if (!kdEnabled || !t17Enabled) {
            return CHANNEL_KD100;
        }
        if (isInternational(trackingNo, carrier)) {
            return CHANNEL_17TRACK;
        }
        return CHANNEL_KD100;
    }

    /** 是否国际件（S10 单号 或 承运商命中国际关键字清单） */
    public boolean isInternational(String trackingNo, String carrier) {
        if (trackingNo != null) {
            String no = trackingNo.trim().toUpperCase();
            if (S10_PATTERN.matcher(no).matches()) {
                return true;
            }
        }
        if (carrier != null && !carrier.trim().isEmpty()) {
            String c = carrier.trim().toLowerCase();
            for (String kw : getInternationalCarriers()) {
                if (c.contains(kw)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 国际承运商关键字（已转小写，用于 contains 匹配） */
    public List<String> getInternationalCarriers() {
        List<String> list = new ArrayList<>();
        String raw = seventeenTrackService.getInternationalCarriersRaw();
        if (raw == null || raw.trim().isEmpty()) {
            return list;
        }
        for (String s : raw.split("[,，]")) {
            String kw = s.trim().toLowerCase();
            if (!kw.isEmpty()) {
                list.add(kw);
            }
        }
        return list;
    }
}
