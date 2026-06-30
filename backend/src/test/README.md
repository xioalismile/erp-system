# 客户管理模块测试文档

## 测试概览

本测试模块覆盖客户管理（Customer）模块的完整功能测试。

## 测试文件结构

```
src/test/
├── java/com/erp/
│   ├── TestUtil.java                    # 测试工具类
│   └── masterdata/
│       ├── controller/
│       │   └── CustomerControllerTest.java  # 接口测试
│       └── service/
│           └── CustomerServiceTest.java      # 单元测试
└── resources/
    └── application-test.yml            # 测试配置
```

## 测试覆盖范围

### Service层测试 (CustomerServiceTest)

| 测试用例 | 测试内容 | 覆盖率 |
|---------|---------|--------|
| testFindPage_Success | 分页查询-正常查询 | ✓ |
| testFindPage_WithKeyword | 分页查询-带关键字 | ✓ |
| testFindByCustomerCode_Exists | 根据编码查询-存在 | ✓ |
| testFindByCustomerCode_NotExists | 根据编码查询-不存在 | ✓ |
| testExistsByCustomerCode_Exists | 编码唯一性检查-存在 | ✓ |
| testExistsByCustomerCode_NotExists | 编码唯一性检查-不存在 | ✓ |
| testExistsByCustomerCodeExcludeId_FoundDuplicate | 排除ID的编码检查-重复 | ✓ |
| testExistsByCustomerCodeExcludeId_NoDuplicate | 排除ID的编码检查-无重复 | ✓ |
| testFindPage_FirstPage | 分页边界值-第一页 | ✓ |
| testFindPage_EmptyData | 分页边界值-空数据 | ✓ |

**Service层覆盖率目标：> 80%**

### Controller层测试 (CustomerControllerTest)

| 测试用例 | 测试内容 | HTTP方法 |
|---------|---------|----------|
| testCreateCustomer_Success | 新增客户-成功 | POST |
| testCreateCustomer_DuplicateCode | 新增客户-编码重复 | POST |
| testCreateCustomer_EmptyName | 新增客户-名称为空 | POST |
| testCreateCustomer_EmptyCode | 新增客户-编码为空 | POST |
| testCreateCustomer_InvalidPhone | 新增客户-电话格式错误 | POST |
| testUpdateCustomer_Success | 修改客户-成功 | PUT |
| testUpdateCustomer_NotFound | 修改客户-不存在 | PUT |
| testUpdateCustomer_DuplicateCode | 修改客户-编码重复 | PUT |
| testDeleteCustomer_Success | 删除客户-成功 | DELETE |
| testDeleteCustomer_NotFound | 删除客户-不存在 | DELETE |
| testGetCustomerById_Success | 查询单个-成功 | GET |
| testGetCustomerById_NotFound | 查询单个-不存在 | GET |
| testListCustomers_Success | 分页列表-成功 | GET |
| testListCustomers_WithKeyword | 分页列表-带关键字 | GET |
| testListCustomers_Empty | 分页列表-空结果 | GET |
| testListCustomers_DefaultPagination | 分页列表-默认参数 | GET |

**Controller层覆盖率目标：> 70%**

## 运行测试

### 方式一：使用测试脚本（推荐）

```bash
cd c:\智能ERP-项目\backend
run-tests.bat
```

### 方式二：使用Maven命令

```bash
# 运行所有测试
mvn test

# 运行客户模块测试
mvn test -Dtest=CustomerServiceTest,CustomerControllerTest

# 只运行Service层测试
mvn test -Dtest=*ServiceTest

# 只运行Controller层测试
mvn test -Dtest=*ControllerTest

# 生成覆盖率报告
mvn test jacoco:report
```

### 方式三：在IDE中运行

1. 使用IntelliJ IDEA打开项目
2. 右键点击测试类或测试目录
3. 选择 "Run 'TestName'" 或 "Run 'All Tests'"

## 测试配置

### 数据库配置 (application-test.yml)

测试环境使用PostgreSQL数据库，需要确保：
- PostgreSQL服务已启动
- erp_db数据库已创建
- customer表已存在（运行过建表脚本）

### 测试数据库连接

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/erp_db
    username: erp_user
    password: Erp@2026
```

## 测试数据

测试使用以下测试数据：

| 字段 | 测试值 |
|------|--------|
| customerCode | C001 |
| customerName | 测试客户 |
| contactPhone | 13800138000 |
| email | test@example.com |
| creditLimit | 100000.00 |

## 常见问题

### 1. Maven命令找不到

确保Maven已安装并配置到系统PATH环境变量。

### 2. 数据库连接失败

确保PostgreSQL服务已启动：
```bash
sc start postgresql-x64-16
```

### 3. 测试数据不存在

运行建表脚本：
```bash
psql -U postgres -d erp_db -f "c:\智能ERP-项目\database\schema\01_basics.sql"
```

## 后续扩展

当您完成客户模块测试后，可以按照相同的模式生成其他模块的测试：

1. 供应商档案（Supplier）
2. 仓库档案（Warehouse）
3. 部门档案（Department）
4. 员工档案（Employee）
5. 计量单位（UnitOfMeasure）
6. 币种（Currency）

每个模块只需：
1. 创建对应的 `XxxServiceTest.java`
2. 创建对应的 `XxxControllerTest.java`
3. 使用相同的测试模式
