package com.erp.test.export;

import com.erp.export.annotation.ExportField;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.entity.Material;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleReturn;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.entity.SaleContract;
import com.erp.sale.entity.SaleQuote;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseReturn;
import com.erp.purchase.entity.PurchaseInvoice;
import com.erp.purchase.entity.PurchaseContract;
import com.erp.purchase.entity.PurchaseRequest;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkCenter;
import com.erp.production.entity.Routing;
import com.erp.production.entity.Workstation;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.OperationReport;
import com.erp.produce.mrp.entity.MrpPlan;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("导入导出功能自动化测试")
public class ExportImportTest {

    @Test
    @DisplayName("测试所有实体的 @ExportField 注解是否正确")
    void testExportFieldAnnotations() {
        System.out.println("========== 测试 @ExportField 注解 ==========\n");
        
        int totalEntities = 0;
        int totalFields = 0;
        List<String> failedEntities = new ArrayList<>();
        
        List<Class<?>> allEntities = getAllEntities();
        
        for (Class<?> clazz : allEntities) {
            totalEntities++;
            try {
                int fieldCount = testEntityAnnotations(clazz);
                totalFields += fieldCount;
            } catch (Exception e) {
                failedEntities.add(clazz.getSimpleName() + ": " + e.getMessage());
            }
        }
        
        System.out.println("\n========== 测试结果汇总 ==========");
        System.out.println("实体总数: " + totalEntities);
        System.out.println("导出字段总数: " + totalFields);
        System.out.println("失败实体数: " + failedEntities.size());
        
        if (failedEntities.size() > 0) {
            System.out.println("\n失败列表:");
            for (String failed : failedEntities) {
                System.err.println("  - " + failed);
            }
        }
        
        assertEquals(0, failedEntities.size(), "所有实体都应该有正确的 @ExportField 注解");
    }

    private List<Class<?>> getAllEntities() {
        List<Class<?>> entities = new ArrayList<>();
        
        entities.add(Customer.class);
        entities.add(Supplier.class);
        entities.add(Material.class);
        entities.add(Bom.class);
        
        entities.add(SaleQuote.class);
        entities.add(SaleOrder.class);
        entities.add(SaleDelivery.class);
        entities.add(SaleReturn.class);
        entities.add(SaleInvoice.class);
        entities.add(SaleContract.class);
        
        entities.add(PurchaseRequest.class);
        entities.add(PurchaseOrder.class);
        entities.add(PurchaseInbound.class);
        entities.add(PurchaseReturn.class);
        entities.add(PurchaseInvoice.class);
        entities.add(PurchaseContract.class);
        
        entities.add(WorkCenter.class);
        entities.add(Workstation.class);
        entities.add(Routing.class);
        entities.add(WorkOrder.class);
        entities.add(ProductionReceipt.class);
        entities.add(OperationReport.class);
        entities.add(MrpPlan.class);
        
        return entities;
    }

    private int testEntityAnnotations(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        List<Field> exportFields = new ArrayList<>();
        
        for (Field field : fields) {
            if (field.isAnnotationPresent(ExportField.class)) {
                exportFields.add(field);
            }
        }
        
        String entityName = clazz.getSimpleName();
        
        List<Integer> orders = exportFields.stream()
                .map(f -> f.getAnnotation(ExportField.class).order())
                .collect(Collectors.toList());
        
        long duplicates = orders.size() - orders.stream().distinct().count();
        
        exportFields.sort(Comparator.comparingInt(f -> f.getAnnotation(ExportField.class).order()));
        
        long requiredCount = exportFields.stream()
                .filter(f -> f.getAnnotation(ExportField.class).required())
                .count();
        
        String status = exportFields.size() > 0 ? "✓" : "✗";
        String dupNote = duplicates > 0 ? " (警告: " + duplicates + "个重复order)" : "";
        
        System.out.println("[" + status + "] " + entityName + 
                ": " + exportFields.size() + " 个导出字段" +
                ", 必填: " + requiredCount + dupNote);
        
        if (exportFields.size() > 0 && exportFields.size() <= 20) {
            System.out.print("     字段: ");
            for (Field field : exportFields) {
                ExportField ann = field.getAnnotation(ExportField.class);
                System.out.print(ann.label() + " ");
            }
            System.out.println();
        }
        
        assertTrue(exportFields.size() > 0, entityName + " 应该至少有一个导出字段");
        
        return exportFields.size();
    }

    @Test
    @DisplayName("测试销售模块导入导出字段")
    void testSaleModuleFields() {
        System.out.println("\n========== 测试销售模块 ==========\n");
        
        Class<?>[] saleEntities = {
            SaleQuote.class, SaleOrder.class, SaleDelivery.class,
            SaleReturn.class, SaleInvoice.class, SaleContract.class
        };
        
        for (Class<?> clazz : saleEntities) {
            testEntityAnnotations(clazz);
        }
        
        System.out.println("\n销售模块验证完成！");
    }

    @Test
    @DisplayName("测试采购模块导入导出字段")
    void testPurchaseModuleFields() {
        System.out.println("\n========== 测试采购模块 ==========\n");
        
        Class<?>[] purchaseEntities = {
            PurchaseRequest.class, PurchaseOrder.class, PurchaseInbound.class,
            PurchaseReturn.class, PurchaseInvoice.class, PurchaseContract.class
        };
        
        for (Class<?> clazz : purchaseEntities) {
            testEntityAnnotations(clazz);
        }
        
        System.out.println("\n采购模块验证完成！");
    }

    @Test
    @DisplayName("测试生产模块导入导出字段")
    void testProductionModuleFields() {
        System.out.println("\n========== 测试生产模块 ==========\n");
        
        Class<?>[] productionEntities = {
            WorkCenter.class, Workstation.class, Routing.class,
            WorkOrder.class, ProductionReceipt.class,
            OperationReport.class, MrpPlan.class
        };
        
        for (Class<?> clazz : productionEntities) {
            testEntityAnnotations(clazz);
        }
        
        System.out.println("\n生产模块验证完成！");
    }

    @Test
    @DisplayName("测试 BOM 多级结构支持")
    void testBomMultiLevel() {
        System.out.println("\n========== 测试 BOM 多级结构支持 ==========\n");
        
        Bom bom = new Bom();
        bom.setBomId(1L);
        bom.setBomCode("BOM-001");
        bom.setMaterialCode("FG-001");
        bom.setMaterialName("成品A");
        bom.setLevel(1);
        
        List<BomItem> items = new ArrayList<>();
        
        BomItem item1 = new BomItem();
        item1.setBomItemId(1L);
        item1.setMaterialCode("SEM-001");
        item1.setMaterialName("半成品1");
        item1.setQuantity(new java.math.BigDecimal("2"));
        item1.setLevel(2);
        item1.setParentMaterialCode("FG-001");
        items.add(item1);
        
        BomItem item2 = new BomItem();
        item2.setBomItemId(2L);
        item2.setMaterialCode("RAW-001");
        item2.setMaterialName("原材料1");
        item2.setQuantity(new java.math.BigDecimal("3"));
        item2.setLevel(3);
        item2.setParentMaterialCode("SEM-001");
        items.add(item2);
        
        System.out.println("BOM 主表: " + bom.getMaterialName() + 
                " (编码: " + bom.getMaterialCode() + 
                ", 层级: " + bom.getLevel() + ")");
        System.out.println("子件数量: " + items.size());
        System.out.println();
        
        for (BomItem item : items) {
            String indent = "";
            for (int i = 1; i < item.getLevel(); i++) {
                indent += "  ";
            }
            System.out.println(indent + "└─ " + item.getMaterialName() + 
                    " (编码: " + item.getMaterialCode() + 
                    ", 层级: " + item.getLevel() + 
                    ", 父件: " + item.getParentMaterialCode() + 
                    ", 数量: " + item.getQuantity() + ")");
        }
        
        System.out.println();
        
        assertNotNull(bom.getLevel(), "BOM 主表应该有层级字段");
        assertNotNull(item1.getLevel(), "BOM 子件应该有层级字段");
        assertNotNull(item1.getParentMaterialCode(), "BOM 子件应该有父件编码字段");
        
        assertTrue(bom.getLevel() >= 1, "BOM 主表层级应 >= 1");
        assertTrue(item1.getLevel() > bom.getLevel(), "子件层级应大于父件层级");
        assertTrue(item2.getLevel() > item1.getLevel(), "孙子件层级应大于子件层级");
        
        assertEquals(1, bom.getLevel(), "顶层BOM层级应为1");
        assertEquals(2, item1.getLevel(), "第一层子件层级应为2");
        assertEquals(3, item2.getLevel(), "第二层子件层级应为3");
        
        Field[] bomFields = Bom.class.getDeclaredFields();
        boolean hasLevelField = false;
        for (Field f : bomFields) {
            if ("level".equals(f.getName())) {
                hasLevelField = true;
                break;
            }
        }
        assertTrue(hasLevelField, "Bom 实体应该有 level 字段");
        
        Field[] bomItemFields = BomItem.class.getDeclaredFields();
        boolean hasItemLevelField = false;
        boolean hasParentMaterialCodeField = false;
        for (Field f : bomItemFields) {
            if ("level".equals(f.getName())) hasItemLevelField = true;
            if ("parentMaterialCode".equals(f.getName())) hasParentMaterialCodeField = true;
        }
        assertTrue(hasItemLevelField, "BomItem 实体应该有 level 字段");
        assertTrue(hasParentMaterialCodeField, "BomItem 实体应该有 parentMaterialCode 字段");
        
        System.out.println("BOM 多级结构验证通过！");
        System.out.println("✓ Bom 实体支持 level 字段");
        System.out.println("✓ BomItem 实体支持 level 字段");
        System.out.println("✓ BomItem 实体支持 parentMaterialCode 字段");
        System.out.println("✓ 支持通过层级号和父件编码维护多级 BOM 结构");
    }

    @Test
    @DisplayName("测试模板字段与实体字段一致性")
    void testTemplateFieldConsistency() {
        System.out.println("\n========== 测试模板字段与实体字段一致性 ==========\n");
        
        List<Class<?>> entities = getAllEntities();
        int totalOk = 0;
        int totalWarn = 0;
        
        for (Class<?> clazz : entities) {
            Field[] allFields = clazz.getDeclaredFields();
            List<Field> exportFields = new ArrayList<>();
            
            for (Field field : allFields) {
                if (field.isAnnotationPresent(ExportField.class)) {
                    exportFields.add(field);
                }
            }
            
            boolean hasCodeField = false;
            boolean hasNameField = false;
            
            for (Field f : exportFields) {
                ExportField ann = f.getAnnotation(ExportField.class);
                if (ann.label().contains("编码") || ann.label().contains("编号")) {
                    hasCodeField = true;
                }
                if (ann.label().contains("名称")) {
                    hasNameField = true;
                }
            }
            
            String name = clazz.getSimpleName();
            if (hasCodeField && hasNameField) {
                System.out.println("[✓] " + name + ": 编码和名称字段完整");
                totalOk++;
            } else {
                String warn = "";
                if (!hasCodeField) warn += "缺少编码/编号字段 ";
                if (!hasNameField) warn += "缺少名称字段 ";
                System.out.println("[⚠] " + name + ": " + warn);
                totalWarn++;
            }
        }
        
        System.out.println("\n完整: " + totalOk + ", 警告: " + totalWarn);
        System.out.println("模板字段一致性检查完成！");
    }
}
