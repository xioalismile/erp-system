package com.erp.ai.tool.impl;

import com.erp.ai.tool.AiTool;
import com.erp.basedata.entity.MaterialCategory;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.service.MaterialCategoryService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.export.dto.BomImportCheckDTO;
import com.erp.export.dto.ImportResultDTO;
import com.erp.export.service.ExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BOM 结构化导入工具（写操作，走两段式确认）。
 *
 * 设计原则：本工具**不看文件、不做字段映射**——入参就是大模型已经转换好的"系统标准BOM行"。
 * 后端把它按统一18列模板落成 Excel，复用与手工导入完全一致的导入链路（缺失物料检测/自动创建/落库），
 * 避免两套规则；这样任何新格式的文件都由模型自适应，不需要改代码。
 *
 * 缺失的必填信息（例如缺失物料的分类编码）一律**拒绝导入并返回需要补充的内容**，由模型向用户追问，禁止编造。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BomImportRowsTool implements AiTool {

    private final ExcelService excelService;
    private final MaterialCategoryService materialCategoryService;
    private final UnitOfMeasureService unitOfMeasureService;

    /** 统一18列模板列序（必须与 ExcelServiceImpl.BOM_UNIFIED_HEADERS 一致） */
    private static final String[] HEADERS = {
            "单据日期", "母件编号*", "母件名称", "BOM分类", "版本号", "数量",
            "层级", "父件编码(多级用)", "母件分类编码",
            "子件编码*", "子件名称", "子件规格", "用量*", "损耗率(%)", "工序", "关键件", "行备注", "子件分类编码"
    };

    @Override
    public String getName() {
        return "bom_import_rows";
    }

    @Override
    public String getDescription() {
        return "按【系统标准BOM结构】导入BOM（写操作，用户确认后执行）。入参是已经转换好的标准BOM行，"
                + "本工具不做文件解析、不做字段映射。标准流程：用户上传文件 → 先调 bom_file_read 读取内容 → "
                + "你自行理解并转换成标准行 → 把转换结果（母件/子件/用量/缺失项）展示给用户确认 → 用户确认后再调本工具导入。"
                + "母件编号、子件编码、用量为必填。**调用前必须先读系统现状**："
                + "query_basedata_materialcategory 查现有物料分类（编码/名称）、query_basedata_unit 查计量单位、"
                + "query_basedata_material 查这些物料是否已存在。缺失物料需要建时，分类只能来自系统查询结果"
                + "或用户明确给出的分类名称（也可传 parentCategoryName/childCategoryName 由系统按名称解析），"
                + "**禁止自己编造分类编码，禁止向用户索要分类编码**；系统里没有合适分类时，先建分类再建物料/BOM。"
                + "若仍缺分类，本工具会返回现有分类、现有单位与继续处理的指引，按指引补齐后重试。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> rowProps = new LinkedHashMap<>();
        rowProps.put("materialCode", str("母件编码（必填，BOM的成品/半成品编码）"));
        rowProps.put("materialName", str("母件名称（可选，空则按系统已有物料带出）"));
        rowProps.put("childMaterialCode", str("子件编码（必填）"));
        rowProps.put("childMaterialName", str("子件名称（可选）"));
        rowProps.put("childSpecification", str("子件规格（可选）"));
        rowProps.put("quantity", num("用量（必填，正数）"));
        rowProps.put("unit", str("单位（可选）"));
        rowProps.put("level", integer("层级（可选，默认1；多级BOM时>1）"));
        rowProps.put("parentMaterialCode", str("父件编码（可选，多级BOM时填直属父件；一级留空）"));
        rowProps.put("scrapRate", str("损耗率%（可选，空=0）"));
        rowProps.put("processNo", str("工序（可选）"));
        rowProps.put("keyPart", str("关键件（可选：是/否，空=否）"));
        rowProps.put("itemRemark", str("行备注（可选）"));
        rowProps.put("parentCategoryCode", str("母件分类编码（可选，必须来自 query_basedata_materialcategory 的查询结果，禁止编造）"));
        rowProps.put("parentCategoryName", str("母件分类名称（可选，与编码二选一；系统按名称解析，解析不到会返回现有分类清单）"));
        rowProps.put("childCategoryCode", str("子件分类编码（可选，必须来自 query_basedata_materialcategory 的查询结果，禁止编造）"));
        rowProps.put("childCategoryName", str("子件分类名称（可选，与编码二选一；系统按名称解析，解析不到会返回现有分类清单）"));
        rowProps.put("bomDate", str("BOM单据日期（可选，yyyy-MM-dd，同一母件只需第一行填）"));
        rowProps.put("bomCategory", str("BOM分类（可选：标准/工程/生产/成本/委外，空=标准BOM）"));
        rowProps.put("bomVersion", str("版本号（可选）"));

        Map<String, Object> rowSchema = new LinkedHashMap<>();
        rowSchema.put("type", "object");
        rowSchema.put("properties", rowProps);
        rowSchema.put("required", List.of("materialCode", "childMaterialCode", "quantity"));

        Map<String, Object> rows = new LinkedHashMap<>();
        rows.put("type", "array");
        rows.put("description", "标准BOM行列表（每行=一个子件；同一母件的多行重复填母件编码即可）");
        rows.put("items", rowSchema);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("rows", rows);
        Map<String, Object> autoCreate = new LinkedHashMap<>();
        autoCreate.put("type", "boolean");
        autoCreate.put("description", "缺失物料是否自动创建（默认true）。为false时若有缺失物料会返回需要用户处理的清单");
        props.put("autoCreateMaterials", autoCreate);
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("type", "string");
        conflict.put("description", "母件已有BOM时的策略：NEW_VERSION=新建一版（默认）/ COVER=覆盖 / SKIP=跳过");
        props.put("onExistingConflict", conflict);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("rows"));
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        Object rowsObj = params == null ? null : params.get("rows");
        if (!(rowsObj instanceof List<?> rows) || rows.isEmpty()) {
            return Map.of("success", false, "message", "没有可导入的BOM行（rows 为空）");
        }
        boolean autoCreate = true;
        Object ac = params.get("autoCreateMaterials");
        if (ac instanceof Boolean b) {
            autoCreate = b;
        } else if (ac != null) {
            autoCreate = Boolean.parseBoolean(String.valueOf(ac));
        }
        String conflictPolicy = params.get("onExistingConflict") == null
                ? "NEW_VERSION" : String.valueOf(params.get("onExistingConflict")).trim().toUpperCase();

        // 1) 标准行 → 统一模板的18列
        // 分类名称 → 分类编码只查一次：模型可以只给"化妆品"这类分类名称，不用编编码。
        Map<String, String> categoryCodeByName = loadCategoryCodeByName();
        List<List<String>> dataRows = new ArrayList<>();
        for (Object o : rows) {
            if (!(o instanceof Map<?, ?> r)) continue;
            List<String> line = new ArrayList<>(HEADERS.length);
            line.add(cell(r.get("bomDate")));            // 单据日期
            line.add(cell(r.get("materialCode")));       // 母件编号*
            line.add(cell(r.get("materialName")));       // 母件名称
            line.add(cell(r.get("bomCategory")));        // BOM分类
            line.add(cell(r.get("bomVersion")));         // 版本号
            line.add("");                                // 数量（母件产出批量，留空=1）
            line.add(cell(r.get("level")));              // 层级
            line.add(cell(r.get("parentMaterialCode"))); // 父件编码(多级用)
            line.add(resolveCategoryCode(r.get("parentCategoryCode"), r.get("parentCategoryName"), categoryCodeByName)); // 母件分类编码
            line.add(cell(r.get("childMaterialCode")));  // 子件编码*
            line.add(cell(r.get("childMaterialName")));  // 子件名称
            line.add(cell(r.get("childSpecification"))); // 子件规格
            line.add(cell(r.get("quantity")));           // 用量*
            line.add(cell(r.get("scrapRate")));          // 损耗率(%)
            line.add(cell(r.get("processNo")));          // 工序
            line.add(cell(r.get("keyPart")));            // 关键件
            line.add(cell(r.get("itemRemark")));         // 行备注
            line.add(resolveCategoryCode(r.get("childCategoryCode"), r.get("childCategoryName"), categoryCodeByName)); // 子件分类编码
            dataRows.add(line);
        }
        if (dataRows.isEmpty()) {
            return Map.of("success", false, "message", "没有可导入的BOM行（rows 内容为空）");
        }

        try {
            byte[] excel = excelService.buildBomStandardExcel(dataRows);

            // 2) 预检查：解析 + 缺失物料 + 分类编码校验
            BomImportCheckDTO check = excelService.checkBomImport(new ByteArrayMultipartFile("bom_ai_import.xlsx", excel));
            if (!check.isParseSuccess()) {
                return Map.of("success", false, "message", "BOM数据校验未通过: " + check.getParseError());
            }
            if (check.isHasMissingCategory()) {
                // 对用户如实说明"没成功 + 正在补什么"，不把"分类编码"这种内部标识丢给用户去填；
                // 具体缺失清单与现有分类/单位放在结构化字段里，供小楷下一步继续处理。
                return needUserInput(check, "BOM 这次没有创建成功：以下物料在系统中还不存在，也没有找到可用的物料分类："
                        + String.join("、", check.getMissingCategoryCodes())
                        + "。我先核对系统现有分类和计量单位，再给你补齐方案并继续创建。");
            }
            if (!autoCreate && check.getMissingMaterialCount() > 0) {
                return needUserInput(check, "BOM 这次没有创建成功：有 " + check.getMissingMaterialCount()
                        + " 个物料在系统中不存在。我先核对现有物料与分类，再给出补齐方案。");
            }

            // 3) 真正导入（复用与手工导入完全一致的链路）
            ImportResultDTO imp = excelService.confirmBomImport(
                    new ByteArrayMultipartFile("bom_ai_import.xlsx", excel), autoCreate, conflictPolicy);

            int totalRows = imp.getTotalCount();
            int successCount = imp.getSuccessCount();
            int failCount = imp.getFailCount();
            List<String> errorMessages = aggregateErrors(imp.getErrors());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalRows", totalRows);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("autoCreatedMaterials", imp.getCreatedMaterialCount());
            if (imp.getBomCodes() != null && !imp.getBomCodes().isEmpty()) {
                result.put("bomCodes", imp.getBomCodes());
            }
            if (!errorMessages.isEmpty()) {
                result.put("errorMessages", errorMessages);
            }

            String bomCodesText = (imp.getBomCodes() == null || imp.getBomCodes().isEmpty())
                    ? "无" : String.join("、", imp.getBomCodes());
            boolean ok;
            String summary;
            if (successCount == 0) {
                ok = false;
                summary = "BOM导入失败：共" + totalRows + "行，成功0行，失败" + failCount + "行";
            } else if (failCount > 0) {
                ok = true;
                result.put("partial", true);
                summary = "BOM导入部分成功：共" + totalRows + "行，成功" + successCount + "行，失败" + failCount
                        + "行，BOM编码：" + bomCodesText;
            } else {
                ok = true;
                summary = "BOM导入完成：共" + totalRows + "行，成功" + successCount + "行，自动创建物料"
                        + imp.getCreatedMaterialCount() + "个，BOM编码：" + bomCodesText;
            }
            if (!ok && !errorMessages.isEmpty()) {
                summary += "。失败原因：" + String.join("；", errorMessages);
            }
            result.put("success", ok);
            result.put("summary", summary);
            result.put("message", summary);
            log.info("[BOM结构化导入] success={} {}", ok, summary);
            return result;

        } catch (Exception e) {
            log.error("[BOM结构化导入] 执行失败", e);
            return Map.of("success", false, "message", "BOM导入失败: " + e.getMessage());
        }
    }

    /**
     * 缺必填信息时不导入：除缺失清单外，**把系统现有分类/单位一并返回**，
     * 并给出「继续处理」的指引（followUp），让前端/小楷能直接接着往下走，
     * 而不是丢一句失败就结束（2026-10-28 用户实测：失败了没有第二步）。
     */
    private Map<String, Object> needUserInput(BomImportCheckDTO check, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("needUserInput", true);
        result.put("message", message);
        List<Map<String, Object>> missing = new ArrayList<>();
        List<String> missingNames = new ArrayList<>();
        if (check.getMissingMaterials() != null) {
            for (BomImportCheckDTO.MissingMaterial m : check.getMissingMaterials()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("materialCode", m.getMaterialCode());
                item.put("materialName", m.getMaterialName());
                item.put("role", m.getMaterialRole());
                item.put("suggestedType", m.getSuggestedType());
                item.put("categoryCode", m.getCategoryCode());
                missing.add(item);
                String n = m.getMaterialName() == null || m.getMaterialName().isBlank()
                        ? m.getMaterialCode() : m.getMaterialName();
                if (n != null && !n.isBlank() && !missingNames.contains(n)) missingNames.add(n);
            }
        }
        result.put("missingMaterials", missing);

        List<Map<String, Object>> categories = listAvailableCategories();
        List<Map<String, Object>> units = listAvailableUnits();
        result.put("availableCategories", categories);
        result.put("availableUnits", units);

        // 结构化下一步：前端读到后会自动让小楷继续处理（读现状 → 补分类/物料 → 重试建 BOM）
        Map<String, Object> followUp = new LinkedHashMap<>();
        followUp.put("prompt", buildFollowUpPrompt(missingNames, categories, units));
        followUp.put("reason", "BOM创建缺少可用分类");
        result.put("followUp", followUp);
        return result;
    }

    /** 分类名称 → 分类编码（只查一次，供整批行共用；查询失败不阻塞主流程） */
    private Map<String, String> loadCategoryCodeByName() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            List<MaterialCategory> list = materialCategoryService.list();
            if (list == null) return map;
            for (MaterialCategory c : list) {
                if (c == null) continue;
                if (c.getCategoryName() == null || c.getCategoryName().isBlank()) continue;
                if (c.getCategoryCode() == null || c.getCategoryCode().isBlank()) continue;
                map.putIfAbsent(c.getCategoryName().trim(), c.getCategoryCode().trim());
            }
        } catch (Exception e) {
            log.warn("[BOM结构化导入] 读取物料分类失败（不阻塞导入）: {}", e.getMessage());
        }
        return map;
    }

    /** 分类编码优先；没给编码时按分类名称解析（名称必须来自系统现有分类） */
    private String resolveCategoryCode(Object codeObj, Object nameObj, Map<String, String> categoryCodeByName) {
        String code = cell(codeObj);
        if (!code.isEmpty()) return code;
        String name = cell(nameObj);
        if (name.isEmpty()) return "";
        return categoryCodeByName.getOrDefault(name, "");
    }

    /** 返回给模型的系统现有分类（编码 + 名称），供其直接引用，不需要编造编码 */
    private List<Map<String, Object>> listAvailableCategories() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<MaterialCategory> list = materialCategoryService.list();
            if (list == null) return out;
            int n = 0;
            for (MaterialCategory c : list) {
                if (c == null || c.getCategoryCode() == null || c.getCategoryCode().isBlank()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("categoryCode", c.getCategoryCode());
                item.put("categoryName", c.getCategoryName() == null ? "" : c.getCategoryName());
                out.add(item);
                if (++n >= 100) break;
            }
        } catch (Exception e) {
            log.warn("[BOM结构化导入] 读取现有分类失败: {}", e.getMessage());
        }
        return out;
    }

    /** 返回给模型的系统现有计量单位，避免它自己造单位或传错单位 */
    private List<Map<String, Object>> listAvailableUnits() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<UnitOfMeasure> list = unitOfMeasureService.list();
            if (list == null) return out;
            int n = 0;
            for (UnitOfMeasure u : list) {
                if (u == null || u.getUnitName() == null || u.getUnitName().isBlank()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("unitCode", u.getUnitCode() == null ? "" : u.getUnitCode());
                item.put("unitName", u.getUnitName());
                out.add(item);
                if (++n >= 100) break;
            }
        } catch (Exception e) {
            log.warn("[BOM结构化导入] 读取现有单位失败: {}", e.getMessage());
        }
        return out;
    }

    /** 面向模型的继续处理指引：先读现状、用现有分类或先建分类，再继续建物料/BOM */
    private String buildFollowUpPrompt(List<String> missingNames,
                                       List<Map<String, Object>> categories,
                                       List<Map<String, Object>> units) {
        StringBuilder sb = new StringBuilder();
        sb.append("刚才的BOM创建没有成功。缺失物料：")
                .append(missingNames.isEmpty() ? "见失败明细" : String.join("、", missingNames)).append("。");
        sb.append("请你现在继续处理，不要停在失败提示上：");
        sb.append("① 先读取系统现有物料分类与计量单位（不要向用户索要分类编码，也不要自己编编码）；");
        sb.append("② 优先使用现有分类；系统里没有合适分类时，先新建分类（名称由你按行业拟定），再建缺失物料；");
        sb.append("③ 把「新建分类 → 新建物料 → 新建三级BOM」组织成一条多步骤任务（task_plan_execute）");
        sb.append("或一张批量创建卡，一次确认后按顺序执行，不要一步弹一张卡；");
        sb.append("④ 向用户说明你读取到的现状、准备用哪些分类/单位、以及为什么这样选。");
        if (!categories.isEmpty()) {
            sb.append(" 系统现有分类：");
            int n = 0;
            for (Map<String, Object> c : categories) {
                if (n++ > 0) sb.append("、");
                sb.append(c.get("categoryName")).append("(").append(c.get("categoryCode")).append(")");
                if (n >= 30) { sb.append(" 等"); break; }
            }
            sb.append("。");
        }
        if (!units.isEmpty()) {
            sb.append(" 系统现有单位：");
            int n = 0;
            for (Map<String, Object> u : units) {
                if (n++ > 0) sb.append("、");
                sb.append(u.get("unitName"));
                if (n >= 30) { sb.append(" 等"); break; }
            }
            sb.append("。");
        }
        return sb.toString();
    }

    /**
     * 失败明细聚合去重：同一"字段+原因"合并成一条，带行号区间与条数，
     * 避免同一原因按行刷几十条（用户反馈的最大噪音来源）。
     */
    private List<String> aggregateErrors(List<ImportResultDTO.ErrorDetail> errors) {
        List<String> lines = new ArrayList<>();
        if (errors == null || errors.isEmpty()) return lines;

        Map<String, Agg> aggMap = new LinkedHashMap<>();
        for (ImportResultDTO.ErrorDetail e : errors) {
            if (e == null) continue;
            String field = e.getFieldName() == null ? "" : e.getFieldName();
            String msg = e.getMessage() == null ? "" : e.getMessage();
            String key = field + "|" + msg;
            Agg agg = aggMap.computeIfAbsent(key, k -> new Agg(field, msg));
            agg.count++;
            if (e.getRowIndex() > 0) {
                if (agg.firstRow <= 0) agg.firstRow = e.getRowIndex();
                agg.lastRow = Math.max(agg.lastRow, e.getRowIndex());
            }
        }
        for (Agg agg : aggMap.values()) {
            StringBuilder sb = new StringBuilder();
            if (agg.firstRow > 0) {
                if (agg.firstRow == agg.lastRow) sb.append("第").append(agg.firstRow).append("行 ");
                else sb.append("第").append(agg.firstRow).append("-").append(agg.lastRow).append("行 ");
            }
            if (!agg.field.isBlank()) sb.append(agg.field).append("：");
            sb.append(agg.msg);
            if (agg.count > 1) sb.append("（共").append(agg.count).append("行）");
            lines.add(sb.toString());
        }
        return lines;
    }

    private static final class Agg {
        final String field;
        final String msg;
        int count = 0;
        int firstRow = -1;
        int lastRow = -1;

        Agg(String field, String msg) {
            this.field = field;
            this.msg = msg;
        }
    }

    private String cell(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private Map<String, Object> str(String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", desc);
        return m;
    }

    private Map<String, Object> num(String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "number");
        m.put("description", desc);
        return m;
    }

    private Map<String, Object> integer(String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "integer");
        m.put("description", desc);
        return m;
    }

    /** 内存字节转 MultipartFile（同一份字节被预检查与正式导入各用一次，故每次新建实例） */
    private record ByteArrayMultipartFile(String name, byte[] content) implements MultipartFile {
        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return name;
        }

        @Override
        public String getContentType() {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content == null ? 0 : content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
