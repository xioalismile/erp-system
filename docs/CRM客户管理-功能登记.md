# CRM客户管理模块 - 功能登记

## 公海池管理

### 公海客户领取（claim）
- **入口**：公海池列表（CustomerPoolList.vue）
- **单个领取**：表格操作列「领取」按钮 → 确认弹窗 → 调用 `POST /api/customer/pool/{id}/claim`
- **批量领取**：工具栏「批量领取」按钮 → 确认弹窗 → 循环调用 claim 接口
- **后端逻辑**（CustomerServiceImpl.claimFromPool）：
  - 校验客户必须在公海（pool_type=1）
  - 每日领取上限校验（sys_config `daily_claim_limit`，默认5个/天）
  - 设置 pool_type=0, owner_user_id=当前用户, pool_enter_date=null
- **权限**：后端从 JWT 取 currentUserId，禁止前端传 userId 越权

### 私海客户退回公海（release）
- **入口**：客户列表（CrmCustomerList.vue）
- **单个退回**：表格操作列「退回公海」按钮（仅私海客户显示）→ 输入原因 → 调用 `POST /api/customer/pool/{id}/release`
- **批量退回**：工具栏「批量退回公海」按钮 → 勾选私海客户 → 输入原因 → 循环调用 release 接口
- **后端逻辑**（CustomerServiceImpl.releaseToPool）：
  - 设置 pool_type=1, pool_enter_date=当前时间, owner_user_id=null
- **权限校验**（CustomerPoolController.release）：
  - 数据范围=全部数据 的用户可释放任意客户
  - 否则只能释放自己负责的客户（owner_user_id == currentUserId）

### 已有的公海相关功能
- 公海池列表（分页+关键字搜索）
- 批量分配客户给指定销售员
- 一键分配全部公海客户
- 手动触发自动回收（按配置规则回收私海客户到公海）
- 公海回收规则配置（未跟进天数/未开单天数/预警天数）
- 公海客户新增/导入/导出

## 私海客户管理
- 客户列表（仪表盘统计+筛选+分页）
- 新增/编辑/删除客户
- 客户详情弹窗（基本信息/跟进历史）
- CRM状态/销售阶段/意向等级行内编辑
- 客户置顶/取消置顶
- 跟进记录登记

## 列表页行内操作按钮（2026-09-06 回退恢复）

背景：2026-09-03「列表页不能有提交/删除等按钮」清理时误将客户管理模块的行内操作一并删除。本次依据 crm_facts.json（UI 抓取的功能清单）仅回退客户管理模块，其余模块（48个单据列表页等）保持不变。

| 页面 | 恢复的行内按钮 | 说明 |
|------|---------------|------|
| 我的私海（CrmCustomerList.vue） | 查看/编辑/跟进/更多（更新CRM状态/销售阶段/意向等级/黑名单/置顶/退回公海） | 详情+跟进历史弹窗、快速操作弹窗均为原有存量 |
| 销售线索（LeadList.vue） | 查看/编辑/跟进/更多（更新状态/分配销售员/转化为客户/放弃线索/删除） | 详情弹窗含跟进历史时间线 |
| 联系人管理（ContactList.vue） | 编辑/设为主联系人/删除 | 补齐 handler：setPrimaryContact/deleteContact |
| 跟进记录（FollowUpList.vue） | 编辑/删除 | 单号链接点击编辑原有 |
| 客户投诉（ComplaintList.vue） | 编辑/更多（指派处理人/处理/关闭/删除） | 指派/处理/关闭弹窗原有，恢复入口 |
| 满意度调查（SurveyList.vue） | 编辑/更多（状态变更/调查分析/删除） | 状态/分析弹窗原有，恢复入口 |
| 销售目标（SalesTargetList.vue） | 公司目标表：编辑/生成下年度/删除；销售员分配表：编辑/刷新完成/删除 | generateNextYear/refreshActual/delete API 原有 |
| 商机管理（OpportunityList.vue） | 编辑/转销售订单/更多（更改阶段/关闭商机/删除） | 转销售订单=sessionStorage 带明细+路由跳 /sale/orders/new（SaleOrderEdit 原生支持 opportunityId 参数） |

## 客户档案域准入（2026-09-06 私海培育客户隔离）

**背景**：CRM 私海新建客户直接出现在客户档案（基础资料→客户）。设计意图是私海客户经培育转为正式客户、或下过单推送销售模块后才进入客户档案。

**改动**：
1. `BaseCrudController` 新增 `applyExtraFilter(qw)` 业务准入钩子（page/listAll/list 三处调用，默认空实现，其它子类零影响）
2. `basedata/CustomerController` 覆写钩子：客户档案/单据选客户下拉只显示 `crm_status='正式客户' OR crm_status IS NULL`（NULL=客户档案入口直接建的存量客户）
3. `SaleOrderServiceImpl.onApproved`：无商机关联的销售订单审核通过后同样把客户升级为"正式客户"（商机关联分支原有 markWonBySaleOrder 已升级）

**升级为正式客户的 4 个入口**（原有）：商机成交、线索转化、销售阶段改为成交、CRM状态手动更新。

**验证**：mvn compile/package + vite build 通过；CRM 新建客户（crm=潜在客户）不在客户档案、CRM私海可见；意向客户不在档案；档案分页只见正式客户+NULL（10条）；测试数据已清理还原。

代码规范：删除类操作一律 ElMessageBox.confirm 二次确认；低频操作用 el-dropdown「更多」收纳；不动列表查询逻辑与数据权限；后端零改动（全部接口已存在）。构建验证 vite build 通过，5 个 CRM 分页接口探活 http=200。
