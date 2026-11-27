package com.erp.ai.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单据身份校验的防线断言（2026-11-22）。
 *
 * <p>覆盖：同一张 material_requisition 表承载 领料 / 退料 / 补料 三种身份时，
 * 声明类型与记录自身 bill_type 不一致必须拒绝；未登记的一表一身份单据保持原行为。
 * 只测纯判定逻辑，不连数据库。
 */
@DisplayName("AI 单据身份 - 一表多身份的类型校验")
class BillTypeIdentityGuardTest {

    private final BillTypeIdentityGuard guard = new BillTypeIdentityGuard(null);

    @Test
    @DisplayName("声明生产退料单且记录确为 RETURN：放行")
    void returnTypeMatches() {
        assertNull(guard.assess("PRODUCTION_RETURN", 100L, "RETURN"));
        assertNull(guard.assess("PRODUCTION_RETURN", 100L, " return "));
    }

    @Test
    @DisplayName("声明生产退料单但记录是领料单 PICK：拒绝并说明不是生产退料单")
    void pickRecordRejectedForReturn() {
        String msg = guard.assess("PRODUCTION_RETURN", 100L, "PICK");
        assertNotNull(msg, "声明退料单却指向领料单必须拒绝");
        assertTrue(msg.contains("生产退料单"), "提示要写明声明的单据类型，实际：" + msg);
        assertTrue(msg.contains("100"), "提示要带主键便于用户核对，实际：" + msg);
    }

    @Test
    @DisplayName("声明生产补料单但记录是生产退料单：拒绝")
    void returnRecordRejectedForSupplement() {
        assertNotNull(guard.assess("PRODUCTION_SUPPLEMENT", 200L, "RETURN"));
        assertNull(guard.assess("PRODUCTION_SUPPLEMENT", 200L, "SUPPLEMENT"));
    }

    @Test
    @DisplayName("历史空的 bill_type 视为领料单，不能被当成退料单操作")
    void nullBillTypeRejectedForReturn() {
        assertNotNull(guard.assess("PRODUCTION_RETURN", 300L, null));
    }

    @Test
    @DisplayName("未登记的一表一身份单据不做校验，行为与升级前一致")
    void unregisteredTypeUnaffected() {
        assertNull(guard.assess("MATERIAL_REQUISITION", 400L, "RETURN"));
        assertNull(guard.assess("SALE_ORDER", 500L, null));
    }
}
