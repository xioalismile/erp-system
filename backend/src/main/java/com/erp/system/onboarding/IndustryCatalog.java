package com.erp.system.onboarding;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行业初始化模板目录（2026-10-11 开账引导）。
 *
 * <p>每套行业模板定义：部门树 / 计量单位 / 业务角色（含菜单授权范围）/ 仓库 /
 * 演示资料样例（物料·客户·供应商）/ 系统字段叫法偏好（全站字段显示名覆盖）。
 * 新增行业只需在 {@link #build()} 里追加一套，不动表结构、不动前端。</p>
 *
 * <p>口径（用户 2026-10-11 拍板）：
 * ① 演示资料 = 基础档案（不造单据），条数按人数分档（≤5人各5条 / 6~20人各10条 / >20人各20条）；
 * ② 员工权限 = 只建部门 + 角色权限（不建示例员工）；
 * ③ 字段叫法 = 全站按"原字段名"统一改（只影响显示文字，不影响校验/落库/导入导出）。</p>
 */
@Component
public class IndustryCatalog {

    /** 业务角色定义：code / name / dataScope / 菜单授权前缀（按前缀匹配 route_path） / minScale（最少人数才启用） */
    public static class RoleDef {
        public final String code;
        public final String name;
        public final String dataScope;
        public final List<String> routePrefixes;
        public final int minScale;

        public RoleDef(String code, String name, String dataScope, String... routePrefixes) {
            this(code, name, dataScope, 0, routePrefixes);
        }

        public RoleDef(String code, String name, String dataScope, int minScale, String... routePrefixes) {
            this.code = code;
            this.name = name;
            this.dataScope = dataScope;
            this.routePrefixes = Collections.unmodifiableList(Arrays.asList(routePrefixes));
            this.minScale = minScale;
        }
    }

    /** 一套行业模板 */
    public static class Industry {
        public final String code;
        public final String name;
        public final String desc;
        /** {code, name, parentCode(空=顶层), type} */
        public final List<String[]> departments;
        /** {code, name, group, isBase} */
        public final List<String[]> units;
        public final List<RoleDef> roles;
        /** {code, name, type} */
        public final List<String[]> warehouses;
        /** {name, model, type} 演示物料（编码自动 WL001 递增） */
        public final List<String[]> materials;
        /** {name, contact, phone} 演示客户（编码自动 CUS001 递增） */
        public final List<String[]> customers;
        /** {name, contact, phone} 演示供应商（编码自动 SUP001 递增） */
        public final List<String[]> suppliers;
        /** 系统字段叫法偏好：原字段名 → 显示名（全站统一改显示） */
        public final Map<String, String> fieldRenames;

        Industry(String code, String name, String desc,
                 List<String[]> departments, List<String[]> units, List<RoleDef> roles,
                 List<String[]> warehouses, List<String[]> materials,
                 List<String[]> customers, List<String[]> suppliers,
                 Map<String, String> fieldRenames) {
            this.code = code;
            this.name = name;
            this.desc = desc;
            this.departments = Collections.unmodifiableList(departments);
            this.units = Collections.unmodifiableList(units);
            this.roles = Collections.unmodifiableList(roles);
            this.warehouses = Collections.unmodifiableList(warehouses);
            this.materials = Collections.unmodifiableList(materials);
            this.customers = Collections.unmodifiableList(customers);
            this.suppliers = Collections.unmodifiableList(suppliers);
            this.fieldRenames = Collections.unmodifiableMap(fieldRenames);
        }
    }

    private final Map<String, Industry> industries = new LinkedHashMap<>();

    public IndustryCatalog() {
        build();
    }

    public List<Industry> all() {
        return new ArrayList<>(industries.values());
    }

    public Industry of(String code) {
        return code == null ? null : industries.get(code.trim().toUpperCase());
    }

    /** 取行业；未匹配时回落"通用兜底"（绝不返回 null，初始化永远可执行） */
    public Industry ofOrDefault(String code) {
        Industry i = of(code);
        return i != null ? i : industries.get("GENERAL");
    }

    /** 演示资料条数分档：≤5人各5条 / 6~20人各10条 / 21~50人各20条 / >50人各30条 */
    public static int demoCount(Integer employeeCount) {
        int n = employeeCount == null || employeeCount <= 0 ? 5 : employeeCount;
        if (n <= 5) return 5;
        if (n <= 20) return 10;
        if (n <= 50) return 20;
        return 30;
    }

    /** 规模分层：0=微型(≤5) / 1=小型(6~20) / 2=中型(21~50) / 3=大型(>50) */
    public static int scaleTier(Integer employeeCount) {
        int n = employeeCount == null || employeeCount <= 0 ? 5 : employeeCount;
        if (n <= 5) return 0;
        if (n <= 20) return 1;
        if (n <= 50) return 2;
        return 3;
    }

    /** 各规模档位的"最少人数门槛"：tier 0→0人, 1→6人, 2→21人, 3→51人 */
    public static int tierMinCount(int tier) {
        switch (tier) {
            case 1: return 6;
            case 2: return 21;
            case 3: return 51;
            default: return 0;
        }
    }

    /** 部门数组取 minScale（第5个元素，缺省=0=总是启用） */
    public static int deptMinScale(String[] d) {
        return d.length > 4 ? parseIntSafe(d[4]) : 0;
    }

    /** 仓库数组取 minScale（第4个元素，缺省=0） */
    public static int whMinScale(String[] w) {
        return w.length > 3 ? parseIntSafe(w[3]) : 0;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s == null ? "0" : s.trim()); } catch (Exception e) { return 0; }
    }

    // ==================== 模板数据 ====================

    private void build() {
        // ---------- 通用部门 / 单位 / 角色（绝大多数行业共用的底座） ----------
        List<String[]> baseUnits = new ArrayList<>(Arrays.asList(
                new String[]{"UNIT01", "个", "通用", "1"},
                new String[]{"UNIT02", "件", "通用", "0"},
                new String[]{"UNIT03", "台", "通用", "0"},
                new String[]{"UNIT04", "套", "通用", "0"},
                new String[]{"UNIT05", "只", "通用", "0"},
                new String[]{"UNIT06", "箱", "包装", "0"},
                new String[]{"UNIT07", "千克", "重量", "1"},
                new String[]{"UNIT08", "米", "长度", "1"},
                new String[]{"UNIT09", "升", "容积", "1"}));

        List<RoleDef> tradeRoles = Arrays.asList(
                new RoleDef("SALES", "销售员", "本人", 0, "/sale", "/crm", "/basedata/customer", "/dashboard"),
                new RoleDef("SALES_MANAGER", "销售主管", "本部门", 6, "/sale", "/crm", "/basedata/customer", "/databoard", "/dashboard"),
                new RoleDef("PURCHASE", "采购员", "本人", 0, "/purchase", "/basedata/supplier", "/dashboard"),
                new RoleDef("PURCHASE_MANAGER", "采购主管", "本部门", 6, "/purchase", "/basedata/supplier", "/databoard", "/dashboard"),
                new RoleDef("WAREHOUSE", "仓管员", "本部门", 0, "/inventory", "/basedata/warehouse", "/basedata/location", "/dashboard"),
                new RoleDef("WAREHOUSE_MANAGER", "仓库主管", "本部门", 6, "/inventory", "/basedata/warehouse", "/basedata/location", "/databoard", "/dashboard"),
                new RoleDef("FINANCE", "财务员", "全部", 0, "/finance", "/dashboard"),
                new RoleDef("FINANCE_MANAGER", "财务主管", "全部", 6, "/finance", "/databoard", "/dashboard"));

        List<RoleDef> factoryRoles = new ArrayList<>(tradeRoles);
        factoryRoles.add(new RoleDef("PRODUCTION", "生产员", "本部门", 6, "/production", "/dashboard"));
        factoryRoles.add(new RoleDef("PRODUCTION_MANAGER", "生产主管", "全部", 21, "/production", "/databoard", "/dashboard"));
        // 质检员 / 质检主管（用友U8·金蝶·管家婆·鼎捷 四大厂商均设此岗：IQC进料/IPQC过程/FQC成品检验）
        // 菜单前缀必须与系统实际路由一致：质检报表=/production/qc-record，质检模板=/masterdata/qc-template
        factoryRoles.add(new RoleDef("QC", "质检员", "本部门", 6, "/production/qc-record", "/masterdata/qc-template", "/dashboard"));
        factoryRoles.add(new RoleDef("QC_MANAGER", "质检主管", "全部", 21, "/production/qc-record", "/masterdata/qc-template", "/databoard", "/dashboard"));

        Map<String, String> noRename = Collections.emptyMap();
        Map<String, String> goodsRename = renames("物料", "商品", "物料编码", "商品编号");
        Map<String, String> partRename = renames("物料", "料号", "物料编码", "料号");

        // ---------- 1. 通用兜底 ----------
        industries.put("GENERAL", new Industry(
                "GENERAL", "通用兜底", "不分行业的通用配置，任何行业都适用",
                depts("总经理办公室", "业务部", "采购部", "仓储物流部", "财务部", "行政人事部"),
                baseUnits, tradeRoles,
                whs("CK01", "主仓库", "普通仓库", "CK02", "副仓库", "普通仓库"),
                mats(new String[]{"示范物料A", "标准型", "外购件"}, new String[]{"示范物料B", "加强型", "外购件"},
                        new String[]{"示范物料C", "紧凑型", "外购件"}, new String[]{"示范物料D", "豪华型", "外购件"},
                        new String[]{"示范物料E", "定制型", "外购件"}, new String[]{"包装耗材-纸箱", "五号箱", "外购件"},
                        new String[]{"包装耗材-缠绕膜", "50cm", "外购件"}, new String[]{"标签贴纸", "通用", "外购件"},
                        new String[]{"说明书", "通用", "外购件"}, new String[]{"合格证", "通用", "外购件"}),
                orgs("本地示范客户（批发）", "本地示范客户（零售）", "周边城市客户甲", "周边城市客户乙", "省外客户甲",
                        "省外客户乙", "连锁商超客户", "电商平台客户", "外贸客户甲", "外贸客户乙"),
                orgs("本地示范供应商（原料）", "本地示范供应商（包材）", "周边城市供应商甲", "周边城市供应商乙",
                        "省外供应商甲", "省外供应商乙", "物流服务商", "设备维保服务商", "办公用品供应商", "劳保用品供应商"),
                noRename));

        // ---------- 2. 贸易批发 ----------
        industries.put("TRADE", new Industry(
                "TRADE", "贸易批发", "批发、经销、代理、分销类企业",
                depts("总经理办公室", "销售部", "采购部", "仓储物流部", "财务部", "行政人事部"),
                baseUnits, tradeRoles,
                whs("CK01", "成品仓库", "普通仓库", "CK02", "中转仓库", "普通仓库"),
                mats(new String[]{"洗衣液2kg瓶装", "12瓶/箱", "外购件"}, new String[]{"洗发水400ml", "24瓶/箱", "外购件"},
                        new String[]{"抽纸3层100抽", "40包/箱", "外购件"}, new String[]{"洗洁精1.5kg", "12瓶/箱", "外购件"},
                        new String[]{"84消毒液1L", "12瓶/箱", "外购件"}, new String[]{"垃圾袋45×50cm", "15卷/箱", "外购件"},
                        new String[]{"香皂115g", "72块/箱", "外购件"}, new String[]{"纸杯250ml", "50杯/提", "外购件"},
                        new String[]{"湿巾80抽", "30包/箱", "外购件"}, new String[]{"洗手液500ml", "24瓶/箱", "外购件"}),
                orgs("城东日化批发部", "城南商贸有限公司", "连锁超市-中心店", "连锁超市-东门店",
                        "社区便利店联盟", "周边县市二批商", "企事业单位团购客户", "餐饮连锁采购部",
                        "外地经贸公司", "电商平台分销商"),
                orgs("日化生产厂家甲", "日化生产厂家乙", "纸品生产基地", "塑料制品厂",
                        "包装材料供应商", "运输车队", "仓储服务商", "装卸劳务公司",
                        "办公用品供应商", "设备维保服务商"),
                goodsRename));

        // ---------- 3. 生产制造 ----------
        industries.put("MANUFACTURE", new Industry(
                "MANUFACTURE", "生产制造", "自有工厂、来料加工、组装生产类企业",
                depts("总经理办公室", "销售部", "采购部", "生产部", "品质部", "技术部", "仓储部", "财务部", "行政人事部"),
                merge(baseUnits, new String[]{"UNIT10", "吨", "重量", "0"},
                        new String[]{"UNIT11", "平方米", "面积", "0"}),
                factoryRoles,
                whs("CK01", "原料仓库", "普通仓库", "CK02", "成品仓库", "普通仓库",
                        "CK03", "半成品仓库", "普通仓库", "CK04", "不合格品仓", "普通仓库"),
                mats(new String[]{"外壳组件", "铝合金", "自制件"}, new String[]{"控制主板", "V2.1", "自制件"},
                        new String[]{"电源模块", "12V/5A", "外购件"}, new String[]{"显示屏总成", "7寸", "外购件"},
                        new String[]{"紧固件套装", "M4×10", "外购件"}, new String[]{"包装纸箱", "产品专用", "外购件"},
                        new String[]{"成品样机A型", "标准配置", "自制件"}, new String[]{"成品样机B型", "高配", "自制件"},
                        new String[]{"线束总成", "1.5m", "自制件"}, new String[]{"散热片", "铝挤型", "外购件"}),
                orgs("设备经销有限公司", "工业品贸易公司", "终端工厂客户甲", "终端工厂客户乙",
                        "工程总包客户", "外贸出口客户", "维修服务商客户", "政府采购单位",
                        "连锁卖场采购部", "同行调货商"),
                orgs("铝材供应商", "电子元件代理商", "钣金加工厂", "线材供应商",
                        "包装材料厂", "表面处理厂", "模具供应商", "五金标准件商行",
                        "检测服务机构", "设备维修服务商"),
                noRename));

        // ---------- 4. 电子电器 ----------
        industries.put("ELECTRONICS", new Industry(
                "ELECTRONICS", "电子电器", "电子元器件、家电、3C 数码类企业",
                depts("总经理办公室", "销售部", "采购部", "生产部", "品质部", "仓储物流部", "财务部", "行政人事部"),
                merge(baseUnits, new String[]{"UNIT10", "PCS", "计数", "0"}),
                factoryRoles,
                whs("CK01", "元件仓库", "普通仓库", "CK02", "成品仓库", "普通仓库"),
                mats(new String[]{"贴片电阻10KΩ", "0805 1%", "外购件"}, new String[]{"贴片电容100nF", "0805 50V", "外购件"},
                        new String[]{"主控芯片", "32位 MCU", "外购件"}, new String[]{"电源适配器", "12V2A", "外购件"},
                        new String[]{"锂电池", "18650 2600mAh", "外购件"}, new String[]{"数据线", "Type-C 1m", "外购件"},
                        new String[]{"蓝牙耳机成品", "TWS 黑色", "自制件"}, new String[]{"充电宝成品", "10000mAh", "自制件"},
                        new String[]{"智能插座", "WiFi款", "自制件"}, new String[]{"遥控器", "红外通用款", "外购件"}),
                orgs("数码连锁卖场", "电商平台旗舰店客户", "通讯器材批发商", "家电专卖店",
                        "企业集采客户", "跨境贸易客户", "维修连锁客户", "直播带货客户",
                        "同行渠道商", "礼品定制客户"),
                orgs("芯片代理商", "被动元件原厂", "连接器供应商", "五金冲压件厂",
                        "注塑外壳供应商", "包装厂", "锂电池供应商", "线材厂",
                        "SMT贴片代工厂", "检测认证机构"),
                partRename));

        // ---------- 5. 机械五金 ----------
        industries.put("HARDWARE", new Industry(
                "HARDWARE", "机械五金", "机械设备、五金工具、紧固件类企业",
                depts("总经理办公室", "销售部", "采购部", "生产部", "品质部", "仓储物流部", "财务部", "行政人事部"),
                merge(baseUnits, new String[]{"UNIT10", "把", "计数", "0"},
                        new String[]{"UNIT11", "支", "计数", "0"}),
                factoryRoles,
                whs("CK01", "原料仓库", "普通仓库", "CK02", "成品仓库", "普通仓库"),
                mats(new String[]{"内六角螺栓M8×30", "8.8级", "外购件"}, new String[]{"自攻钉3.5×25", "镀锌", "外购件"},
                        new String[]{"角磨机切割片", "100×16mm", "外购件"}, new String[]{"电钻钻头套装", "1-10mm", "外购件"},
                        new String[]{"手动工具套装", "46件套", "外购件"}, new String[]{"不锈钢合页", "4寸", "外购件"},
                        new String[]{"门锁面板", "304不锈钢", "自制件"}, new String[]{"五金支架", "可调款", "自制件"},
                        new String[]{"脚轮", "3寸万向", "外购件"}, new String[]{"铁丝网", "1×1m", "外购件"}),
                orgs("五金机电市场客户甲", "五金机电市场客户乙", "建材城五金店", "装饰工程公司",
                        "门窗加工厂", "家具厂采购部", "设备制造厂", "汽修配件店",
                        "外贸五金客户", "电商平台五金店"),
                orgs("标准件制造厂", "钢材贸易公司", "表面处理厂", "线材拉丝厂",
                        "工量具代理商", "电动工具总代", "包装供应商", "电镀厂",
                        "模具供应商", "物流专线"),
                noRename));

        // ---------- 6. 服装纺织 ----------
        industries.put("APPAREL", new Industry(
                "APPAREL", "服装纺织", "服装、家纺、面料、鞋帽类企业",
                depts("总经理办公室", "销售部", "采购部", "生产部", "设计部", "品质部", "仓储物流部", "财务部", "行政人事部"),
                merge(baseUnits, new String[]{"UNIT10", "打", "计数", "0"},
                        new String[]{"UNIT11", "匹", "布匹", "0"}),
                factoryRoles,
                whs("CK01", "面料仓库", "普通仓库", "CK02", "成品仓库", "普通仓库"),
                mats(new String[]{"纯棉面料32支", "白色 1.5m门幅", "外购件"}, new String[]{"涤纶里布", "黑色", "外购件"},
                        new String[]{"男士T恤M码", "白色", "自制件"}, new String[]{"男士T恤L码", "黑色", "自制件"},
                        new String[]{"女式衬衫S码", "条纹", "自制件"}, new String[]{"牛仔裤30码", "深蓝", "自制件"},
                        new String[]{"纽扣15mm", "四眼白色", "外购件"}, new String[]{"拉链20cm", "尼龙黑", "外购件"},
                        new String[]{"缝纫线", "402白", "外购件"}, new String[]{"包装袋", "OPP自粘", "外购件"}),
                orgs("服装批发市场档口", "商场专柜客户", "电商女装店", "童装连锁客户",
                        "校服团购客户", "工作服定制客户", "外贸服装客户", "直播电商客户",
                        "品牌加盟商", "拼团团购客户"),
                orgs("面料织造厂", "辅料市场商户", "纽扣厂", "拉链厂",
                        "线厂", "印花绣花厂", "水洗厂", "包装厂",
                        "物流专线", "裁剪代工厂"),
                renames("物料", "货品", "规格型号", "颜色尺码")));

        // ---------- 7. 食品饮料 ----------
        industries.put("FOOD", new Industry(
                "FOOD", "食品饮料", "食品加工、饮料、农副产品、餐饮供应链类企业",
                depts("总经理办公室", "销售部", "采购部", "生产部", "品控部", "仓储物流部", "财务部", "行政人事部"),
                merge(baseUnits, new String[]{"UNIT10", "瓶", "包装", "0"},
                        new String[]{"UNIT11", "罐", "包装", "0"},
                        new String[]{"UNIT12", "袋", "包装", "0"},
                        new String[]{"UNIT13", "桶", "包装", "0"}),
                factoryRoles,
                whs("CK01", "常温仓库", "普通仓库", "CK02", "冷藏仓库", "冷藏仓库"),
                mats(new String[]{"纯牛奶250ml", "24盒/箱", "外购件"}, new String[]{"酸奶200g", "12杯/组", "外购件"},
                        new String[]{"饮用水550ml", "24瓶/箱", "外购件"}, new String[]{"面包400g", "切片装", "自制件"},
                        new String[]{"蛋糕礼盒", "8寸", "自制件"}, new String[]{"坚果礼盒", "混合1.5kg", "自制件"},
                        new String[]{"大米5kg", "真空包装", "外购件"}, new String[]{"食用油5L", "非转基因", "外购件"},
                        new String[]{"冷冻水饺1kg", "三鲜馅", "自制件"}, new String[]{"果汁饮料1L", "橙汁", "外购件"}),
                orgs("连锁超市配送中心", "社区团购团长", "餐饮连锁采购部", "学校食堂供应商",
                        "便利店联盟", "零食量贩店", "企事业单位福利采购", "电商平台食品店",
                        "批发市场二批商", "外贸食品客户"),
                orgs("乳制品厂家", "粮油批发商", "果蔬基地", "肉类加工厂",
                        "食品添加剂经销商", "包装印刷厂", "冷链物流公司", "烘焙原料商行",
                        "检测服务机构", "设备维保服务商"),
                renames("物料", "货品", "批次号", "生产批次")));

        // ---------- 8. 化工 ----------
        industries.put("CHEMICAL", new Industry(
                "CHEMICAL", "化工", "化学原料、涂料、清洗剂、日化原料类企业",
                depts("总经理办公室", "销售部", "采购部", "生产部", "品控部", "仓储物流部", "财务部", "行政人事部"),
                merge(baseUnits, new String[]{"UNIT10", "吨", "重量", "0"},
                        new String[]{"UNIT11", "桶", "包装", "0"}),
                factoryRoles,
                whs("CK01", "常温仓库", "普通仓库", "CK02", "危化品仓库", "危险品仓库"),
                mats(new String[]{"工业清洗剂", "25kg/桶", "自制件"}, new String[]{"水性涂料-白色", "20kg/桶", "自制件"},
                        new String[]{"环氧底漆", "18L/组", "自制件"}, new String[]{"固化剂", "5kg/桶", "外购件"},
                        new String[]{"稀释剂", "15kg/桶", "外购件"}, new String[]{"甲醇", "工业级", "外购件"},
                        new String[]{"片碱", "99% 25kg/袋", "外购件"}, new String[]{"润滑脂", "1kg/罐", "外购件"},
                        new String[]{"防锈油", "170kg/桶", "外购件"}, new String[]{"试剂瓶", "500ml", "外购件"}),
                orgs("化工商行甲", "化工商行乙", "涂料施工工程公司", "电镀厂采购部",
                        "水处理工程公司", "日化生产厂家", "制药企业采购部", "外贸化工客户",
                        "科研院所", "同行调货商"),
                orgs("基础化工原料厂", "溶剂供应商", "树脂供应商", "颜料填料商行",
                        "助剂供应商", "包装桶供应商", "危化品运输公司", "检测机构",
                        "设备维保服务商", "应急防护用品供应商"),
                renames("物料", "货品", "批次号", "批号")));

        // ---------- 9. 家具家居 ----------
        industries.put("FURNITURE", new Industry(
                "FURNITURE", "家具家居", "家具、家居用品、家装建材类企业",
                depts("总经理办公室", "销售部", "设计部", "采购部", "生产部", "品质部", "仓储物流部", "财务部", "行政人事部"),
                merge(baseUnits, new String[]{"UNIT10", "张", "计数", "0"},
                        new String[]{"UNIT11", "平方米", "面积", "0"}),
                factoryRoles,
                whs("CK01", "材料仓库", "普通仓库", "CK02", "成品仓库", "普通仓库"),
                mats(new String[]{"实木餐桌1.4m", "胡桃色", "自制件"}, new String[]{"餐椅", "软包座垫", "自制件"},
                        new String[]{"布艺沙发三人位", "米灰色", "自制件"}, new String[]{"茶几", "岩板1.2m", "自制件"},
                        new String[]{"衣柜两门", "白蜡木色", "自制件"}, new String[]{"床垫1.8m", "弹簧乳胶", "外购件"},
                        new String[]{"板材18mm", "E0级免漆板", "外购件"}, new String[]{"五金连接件", "三合一", "外购件"},
                        new String[]{"封边条", "PVC 1mm", "外购件"}, new String[]{"包装纸箱-家具", "加厚蜂窝", "外购件"}),
                orgs("家具卖场专柜", "家装公司集采", "房地产样板房项目", "民宿酒店项目",
                        "电商家具店", "外贸家具客户", "办公家具客户", "软装设计工作室",
                        "同城自提客户", "老客户转介绍"),
                orgs("板材供应商", "五金配件商行", "软包面料供应商", "海绵供应商",
                        "油漆涂料商行", "玻璃加工厂", "物流专线", "安装服务外包",
                        "包装材料厂", "仓储服务商"),
                goodsRename));

        // ---------- 10. 电子商务 ----------
        industries.put("ECOMMERCE", new Industry(
                "ECOMMERCE", "电子商务", "电商店铺、直播带货、社群团购类企业",
                depts("总经理办公室", "运营部", "客服部", "采购部", "仓储物流部", "财务部", "行政人事部"),
                baseUnits,
                tradeRoles,
                whs("CK01", "电商仓库", "普通仓库", "CK02", "退货仓库", "普通仓库"),
                mats(new String[]{"手机壳iPhone15", "透明防摔", "外购件"}, new String[]{"数据线1m", "Type-C", "外购件"},
                        new String[]{"车载支架", "磁吸款", "外购件"}, new String[]{"收纳盒三层", "抽屉式", "外购件"},
                        new String[]{"保温杯500ml", "316内胆", "外购件"}, new String[]{"化妆刷套装", "12支装", "外购件"},
                        new String[]{"瑜伽垫", "TPE 8mm", "外购件"}, new String[]{"香薰蜡烛", "大豆蜡200g", "外购件"},
                        new String[]{"快递纸箱2号", "五层加厚", "外购件"}, new String[]{"气泡膜", "50cm×100m", "外购件"}),
                orgs("抖音商城消费者售后", "淘宝天猫企业采购", "京东企业购客户", "拼多多批发客户",
                        "直播基地合作方", "社群团购团长", "微商代理", "跨境独立站客户",
                        "礼品定制企业客户", "同城闪购客户"),
                orgs("1688源头工厂甲", "1688源头工厂乙", "品牌方授权经销商", "快递公司网点",
                        "包装耗材商行", "仓储代发服务商", "退换货翻新服务商", "摄影道具供应商",
                        "设备维保服务商", "办公用品供应商"),
                goodsRename));

        // ---------- 11. 服务业 ----------
        industries.put("SERVICE", new Industry(
                "SERVICE", "服务业", "咨询、设计、维修、家政、劳务等服务类企业",
                depts("总经理办公室", "客户服务部", "项目部", "采购部", "财务部", "行政人事部"),
                merge(baseUnits, new String[]{"UNIT10", "次", "服务", "0"},
                        new String[]{"UNIT11", "人天", "服务", "0"},
                        new String[]{"UNIT12", "月", "服务", "0"}),
                Arrays.asList(
                        new RoleDef("SALES", "客户经理", "本人", 0, "/sale", "/crm", "/dashboard"),
                        new RoleDef("SALES_MANAGER", "客服主管", "本部门", 6, "/sale", "/crm", "/databoard", "/dashboard"),
                        new RoleDef("PURCHASE", "采购员", "本人", 0, "/purchase", "/dashboard"),
                        new RoleDef("WAREHOUSE", "物料管理员", "本部门", 0, "/inventory", "/dashboard"),
                        new RoleDef("FINANCE", "财务员", "全部", 0, "/finance", "/dashboard"),
                        new RoleDef("FINANCE_MANAGER", "财务主管", "全部", 6, "/finance", "/databoard", "/dashboard")),
                whs("CK01", "耗材仓库", "普通仓库"),
                mats(new String[]{"上门服务券", "标准工单", "外购件"}, new String[]{"工具箱", "检修标配", "外购件"},
                        new String[]{"清洁耗材套装", "标准", "外购件"}, new String[]{"更换配件包", "通用", "外购件"},
                        new String[]{"服务协议文本", "标准版", "外购件"}, new String[]{"工牌工装", "定制", "外购件"},
                        new String[]{"保养套餐卡", "年度", "外购件"}, new String[]{"宣传单页", "A4彩印", "外购件"},
                        new String[]{"礼品盒", "客户答谢", "外购件"}, new String[]{"耗材-手套", "劳保", "外购件"}),
                orgs("年度框架服务客户甲", "年度框架服务客户乙", "物业管理有限公司", "写字楼项目部",
                        "政府机关服务点", "连锁门店客户", "个人会员客户", "企业行政部客户",
                        "同行转介绍客户", "社区服务站"),
                orgs("耗材供应商", "工具设备商行", "劳保用品商行", "印刷厂",
                        "车辆租赁公司", "劳务外包公司", "培训认证机构", "保险经纪",
                        "办公设备维修商", "礼品供应商"),
                renames("物料", "服务项目", "单价", "服务单价")));

        // ---------- 12. 化妆品 ----------
        industries.put("COSMETICS", new Industry(
                "COSMETICS", "化妆品", "化妆品、护肤品、美妆、个护类企业",
                depts("总经理办公室", "销售部", "研发部", "采购部", "生产部", "品质部", "仓储物流部", "财务部", "行政人事部"),
                merge(baseUnits, new String[]{"UNIT10", "瓶", "包装", "0"},
                        new String[]{"UNIT11", "支", "包装", "0"},
                        new String[]{"UNIT12", "盒", "包装", "0"}),
                factoryRoles,
                whs("CK01", "原料仓库", "普通仓库", "CK02", "成品仓库", "普通仓库", "CK03", "包材仓库", "普通仓库"),
                mats(new String[]{"保湿面霜", "50g", "自制件"}, new String[]{"精华液", "30ml", "自制件"},
                        new String[]{"口红", "3.5g", "自制件"}, new String[]{"粉底液", "30ml", "自制件"},
                        new String[]{"洁面乳", "100g", "自制件"}, new String[]{"爽肤水", "150ml", "自制件"},
                        new String[]{"面膜", "25ml/片", "自制件"}, new String[]{"防晒霜", "50g", "自制件"},
                        new String[]{"玻璃瓶", "50ml", "外购件"}, new String[]{"包装盒", "彩印", "外购件"}),
                orgs("日化连锁渠道", "电商平台旗舰店", "美妆集合店", "美容院线客户",
                        "商场专柜客户", "跨境电商客户", "直播带货客户", "礼品定制客户",
                        "经销商客户甲", "经销商客户乙"),
                orgs("原料供应商", "香精香料供应商", "包材供应商", "玻璃瓶供应商",
                        "软管供应商", "印刷厂", "OEM代工厂", "检测认证机构",
                        "冷链物流公司", "设备维保服务商"),
                renames("物料", "货品", "批次号", "生产批号")));
    }

    // ==================== 构造辅助 ====================

    private static Map<String, String> renames(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, String> merge2(Map<String, String> a, Map<String, String> b) {
        Map<String, String> m = new LinkedHashMap<>();
        if (a != null) m.putAll(a);
        if (b != null) m.putAll(b);
        return m;
    }

    /**
     * 部门构造：按名称顺序自动分配编码 + 职能/业务类型 + 规模门槛(minScale)。
     * 规模门槛规则：前3个核心部门=0(总是启用)，第4~5个=6(6人+)，第6个起=21(21人+)；
     * 财务部/行政人事部/总经理办公室强制=0（再小的公司也需要）。
     */
    private static List<String[]> depts(String... names) {
        List<String[]> list = new ArrayList<>();
        int seq = 1;
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            boolean isFunc = name.contains("办公室") || name.contains("财务") || name.contains("行政") || name.contains("人事");
            int minScale;
            if (isFunc) minScale = 0;
            else if (i < 3) minScale = 0;
            else if (i < 5) minScale = 6;
            else minScale = 21;
            list.add(new String[]{String.format("D%02d", seq++), name, "", isFunc ? "职能部门" : "业务部门", String.valueOf(minScale)});
        }
        return list;
    }

    /** 仓库构造：{code, name, type, minScale}；第一个仓库总是启用，其余 6 人+启用 */
    @SafeVarargs
    private static List<String[]> whs(String... kv) {
        List<String[]> list = new ArrayList<>();
        int idx = 0;
        for (int i = 0; i + 2 < kv.length; i += 3) {
            int minScale = (idx == 0) ? 0 : 6;
            list.add(new String[]{kv[i], kv[i + 1], kv[i + 2], String.valueOf(minScale)});
            idx++;
        }
        return list;
    }

    private static List<String[]> mats(String[]... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static List<String[]> orgs(String... names) {
        List<String[]> list = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            // {name, contact, phone}：联系人/电话用演示占位（真实手机号不造假）
            list.add(new String[]{names[i], "演示联系人" + (i + 1), "000-0000-0000"});
        }
        return list;
    }

    @SafeVarargs
    private static List<String[]> merge(List<String[]> base, String[]... extra) {
        List<String[]> list = new ArrayList<>(base);
        list.addAll(Arrays.asList(extra));
        return list;
    }
}
