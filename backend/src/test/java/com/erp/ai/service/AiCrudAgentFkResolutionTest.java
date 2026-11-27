package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.erp.ai.dto.EntityCrudConfig;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * 缺口覆盖：AI 建单解析主档外键时，「模糊命中多条」不能替用户猜。
 *
 * <p>Bug 来源（2026-10-28 外键解析改动）：为了修「计量单位『千克』有 5 条重复主档导致批量建物料全失败」，
 * 把「命中多条直接报错」整体改成了「取最早建档的一条」。精确命中多条（同名重复主档，本质是同一个业务对象）
 * 取最早一条是对的；但**模糊**命中多条是多个不同对象——用户说「客户=华为」，主档里有「华为技术」「华为终端」，
 * 修改前会静默挂到最早建档的那条，单据与应收就落到错误客户上（仓库/供应商同理）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AI 建单 - 主档外键解析（模糊多条不猜）")
class AiCrudAgentFkResolutionTest {

    @Mock private ApplicationContext applicationContext;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private CustomerService customerService;
    @Mock private com.erp.masterdata.service.MaterialCodeRuleService materialCodeRuleService;
    @Mock private com.erp.serialno.service.SysSerialNoConfigService serialNoConfigService;
    @Mock private com.erp.ai.bill.BillActionRegistry billActionRegistry;
    @Mock private com.erp.permission.config.BillPermissionPathRegistry billPermissionPathRegistry;
    @Mock private com.erp.permission.service.FieldPermissionOutputFilter fieldPermissionOutputFilter;

    private AiCrudAgentService service;

    @BeforeEach
    void setUp() {
        service = new AiCrudAgentService(applicationContext, jdbcTemplate, materialCodeRuleService,
                serialNoConfigService, billActionRegistry, billPermissionPathRegistry, fieldPermissionOutputFilter);
        lenient().when(applicationContext.getBean(CustomerService.class)).thenReturn(customerService);
    }

    private EntityCrudConfig customerConfig() {
        return EntityCrudConfig.builder()
                .intentPrefix("BASEDATA_CUSTOMER")
                .entityClass(Customer.class)
                .serviceClass(CustomerService.class)
                .serviceBeanName("customerService")
                .primaryKeyField("customerId")
                .primaryKeyColumn("customer_id")
                .nameField("customerName")
                .codeField("customerCode")
                .isMasterData(true)
                .build();
    }

    private Customer customer(Long id, String code, String name) {
        Customer c = new Customer();
        c.setCustomerId(id);
        c.setCustomerCode(code);
        c.setCustomerName(name);
        return c;
    }

    /** 直接调私有方法（外键解析的内部查询），调用异常按原样抛出，便于断言 */
    private Object queryTargetOne(EntityCrudConfig cfg, String field, String value, boolean like) throws Exception {
        Method m = AiCrudAgentService.class.getDeclaredMethod("queryTargetOne",
                EntityCrudConfig.class, String.class, String.class, boolean.class);
        m.setAccessible(true);
        try {
            return m.invoke(service, cfg, field, value, like);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    @Test
    @DisplayName("模糊命中多个不同客户：必须报错并列出候选，不能静默挂到某一客户")
    void likeMultiMatch_mustNotGuess() throws Exception {
        Customer a = customer(11L, "C001", "华为技术有限公司");
        Customer b = customer(12L, "C002", "华为终端有限公司");
        lenient().when(customerService.list(ArgumentMatchers.<Wrapper<Customer>>any()))
                .thenReturn(List.of(a, b));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> queryTargetOne(customerConfig(), "customerName", "华为", true));

        assertTrue(ex.getMessage().contains("华为技术有限公司"), "报错要列出候选，实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("华为终端有限公司"), "报错要列出候选，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("精确命中多条重复主档：仍按最早建档的一条处理（保留 2026-10-28 修复口径）")
    void exactMultiMatch_stillPicksEarliest() throws Exception {
        Customer first = customer(3L, "KG", "千克");
        Customer second = customer(9L, "UNIT07", "千克");
        lenient().when(customerService.list(ArgumentMatchers.<Wrapper<Customer>>any()))
                .thenReturn(List.of(first, second));

        Object matched = queryTargetOne(customerConfig(), "customerName", "千克", false);

        assertSame(first, matched, "同名重复主档应取最早建档（列表第一条）而非报错");
    }

    @Test
    @DisplayName("模糊命中唯一一条：正常返回该条")
    void likeSingleMatch_returnsIt() throws Exception {
        Customer only = customer(21L, "C021", "华为技术有限公司");
        lenient().when(customerService.list(ArgumentMatchers.<Wrapper<Customer>>any()))
                .thenReturn(List.of(only));

        Object matched = queryTargetOne(customerConfig(), "customerName", "华为", true);

        assertSame(only, matched);
    }
}
