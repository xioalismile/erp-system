<template>
  <div class="erp-page">
    <!-- CRM 仪表盘 -->
    <el-card class="dashboard-card" shadow="never">
      <div class="dashboard-row">
        <div class="stat-item" v-for="item in statItems" :key="item.key" @click="handleStatClick(item.key)">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
          <div class="stat-hint">{{ $tt("点击查看明细") }}</div>
        </div>
      </div>
      <div class="dashboard-row" style="margin-top:12px;">
        <div class="dist-block">
          <div class="dist-title">{{ $tt("客户状态分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.crmStatusDist || {}" :key="k"
                    :type="crmStatusType(k)" size="large" class="dist-tag"
                    @click="handleDistTagClick('crmStatus', k)">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
        <div class="dist-block">
          <div class="dist-title">{{ $tt("销售阶段分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.salesStageDist || {}" :key="k"
                    :type="salesStageType(k)" size="large" class="dist-tag"
                    @click="handleDistTagClick('salesStage', k)">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
        <div class="dist-block">
          <div class="dist-title">{{ $tt("意向等级分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.intentionLevelDist || {}" :key="k"
                    :type="intentionLevelType(k)" size="large" class="dist-tag"
                    @click="handleDistTagClick('intentionLevel', k)">
              {{ k }}级: {{ v }}
            </el-tag>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 筛选区 -->
    <el-card shadow="never" style="margin-top:12px;">
      <div class="filter-bar">
        <el-input v-model="search.keyword" :placeholder="$tt('编码/名称/简称/联系人/电话')" style="width:240px;" clearable />
        <el-select v-model="search.crmStatus" :placeholder="$tt('CRM状态')" style="width:130px;" clearable>
          <el-option v-for="s in crmStatusOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="search.salesStage" :placeholder="$tt('销售阶段')" style="width:130px;" clearable>
          <el-option v-for="s in salesStageOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="search.intentionLevel" :placeholder="$tt('意向等级')" style="width:110px;" clearable>
          <el-option v-for="s in ['A','B','C','D']" :key="s" :label="s + '级'" :value="s" />
        </el-select>
        <el-select v-model="search.isBlacklist" :placeholder="$tt('黑名单')" style="width:110px;" clearable>
          <el-option :label="$tf('正常')" :value="0" />
          <el-option :label="$tf('黑名单')" :value="1" />
        </el-select>
        <el-select v-model="search.poolType" :placeholder="$tt('归属')" style="width:110px;" clearable @change="onPoolTypeChange">
          <el-option :label="$tf('私海')" :value="0" />
          <el-option :label="$tf('公海')" :value="1" />
          <el-option :label="$tf('全部')" :value="''" />
        </el-select>
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
        </el-tooltip>
        <div style="flex:1;"></div>
        <el-button type="warning" v-permission="perm('recycle')" @click="handleBatchRelease" :disabled="selection.length === 0">
          批量退回公海({{ selection.length }})
        </el-button>
        <el-button type="primary" v-permission="perm('add')" @click="handleAdd">{{ $tt("+ 新增客户") }}</el-button>
      </div>
    </el-card>

    <!-- 客户列表 -->
    <el-card shadow="never" style="margin-top:12px;">
      <el-table :data="sortedCustomers" v-loading="loading" border stripe @sort-change="onSortChange" @selection-change="onSelectionChange" :key="configVersion">
        <el-table-column type="selection" width="50" />
        <el-table-column label="" width="50" align="center" fixed="left">
          <template #default="{ row }">
            <el-icon
              :size="18"
              :class="['top-star', { 'is-top': row.isTop === 1, 'can-top': row.poolType === 0 }]"
              @click="handleToggleTop(row)"
            ><StarFilled v-if="row.isTop === 1" /><Star v-else /></el-icon>
          </template>
        </el-table-column>
        <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
        <template v-for="col in visibleSortedColumns" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="$tf(col.label)"
            :width="col.width"
            :min-width="col.minWidth"
            :align="col.align || 'left'"
            :sortable="col.sortable === 'local' ? true : (col.sortable ? 'custom' : false)"
            :show-overflow-tooltip="!!col.tooltip"
            :fixed="getColFixed(col.prop, col.fixed)"
          >
            <template v-if="col.type" #default="{ row }">
              <el-link v-if="col.type === 'link'" type="primary" :underline="false" style="font-weight:500;" @click="col.linkClick && col.linkClick(row)">{{ row[col.prop] }}</el-link>
              <el-tag v-else-if="col.type === 'tag'" :type="col.tagType ? col.tagType(row) : 'info'" size="small">{{ col.tagText ? col.tagText(row) : (row[col.prop] || '-') }}</el-tag>
              <span v-else-if="col.type === 'format'">{{ col.format(row) }}</span>
              <template v-else-if="col.type === 'custom'">
                <el-select v-if="col.prop === 'crmStatus'" v-model="row.crmStatus" size="small" :placeholder="'潜在客户'" clearable style="width: 100%;" @change="handleInlineEdit(row, 'crmStatus')">
                  <el-option v-for="s in crmStatusOptions" :key="s" :label="s" :value="s" />
                </el-select>
                <el-select v-else-if="col.prop === 'salesStage'" v-model="row.salesStage" size="small" placeholder="-" clearable style="width: 100%;" @change="handleInlineEdit(row, 'salesStage')">
                  <el-option v-for="s in salesStageOptions" :key="s" :label="s" :value="s" />
                </el-select>
                <el-select v-else-if="col.prop === 'intentionLevel'" v-model="row.intentionLevel" size="small" placeholder="-" clearable style="width: 100%;" @change="handleInlineEdit(row, 'intentionLevel')">
                  <el-option v-for="s in ['A', 'B', 'C', 'D']" :key="s" :label="`${s}级`" :value="s" />
                </el-select>
                <span v-else-if="col.prop === 'nextContactDate'" :style="isToday(row.nextContactDate) ? 'color:#f56c6c;font-weight:bold;' : ''">{{ row.nextContactDate || '-' }}</span>
              </template>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
        <el-table-column :label="$tf('操作')" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="perm('view')" @click="handleView(row)">{{ $tt("查看") }}</el-button>
            <el-button type="primary" link size="small" v-permission="perm('edit')" @click="handleEdit(row)">{{ $tt("编辑") }}</el-button>
            <el-button type="success" link size="small" v-permission="perm('add')" @click="handleFollowUp(row)">{{ $tt("跟进") }}</el-button>
            <el-dropdown trigger="click" @command="(cmd) => handleRowCommand(cmd, row)">
              <el-button type="primary" link size="small">{{ $tt("更多") }}<el-icon style="margin-left:2px;"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="crmStatus">{{ $tt("更新CRM状态") }}</el-dropdown-item>
                  <el-dropdown-item command="salesStage">{{ $tt("更新销售阶段") }}</el-dropdown-item>
                  <el-dropdown-item command="intentionLevel">{{ $tt("更新意向等级") }}</el-dropdown-item>
                  <el-dropdown-item command="blacklist">{{ row.isBlacklist === 1 ? '移出黑名单' : '加入黑名单' }}</el-dropdown-item>
                  <el-dropdown-item command="top" :disabled="row.poolType === 1">{{ row.isTop === 1 ? '取消置顶' : '置顶' }}</el-dropdown-item>
                  <el-dropdown-item command="release" divided :disabled="row.poolType === 1">{{ $tt("退回公海") }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top:16px;display:flex;justify-content:flex-end;">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="page.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑客户' : '新增客户'" width="900px" :close-on-click-modal="false">
      <div style="display:flex;justify-content:flex-end;gap:8px;margin-bottom:12px;">
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-if="isEdit" type="danger" v-permission="perm('delete')" @click="handleDeleteInDialog" :loading="deleting" :disabled="saving">{{ $tt("删除") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="handleSave" :loading="saving">{{ $tt("保存") }}</el-button>
      </div>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-divider content-position="left">{{ $tt("基础信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('客户编码')" prop="customerCode">
              <el-input v-model="formData.customerCode" :placeholder="$tt('如 C001')" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$tf('客户名称')" prop="customerName">
              <el-input v-model="formData.customerName" :placeholder="$tt('如 XX科技有限公司')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('简称')"><el-input v-model="formData.shortName" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('客户类型')">
              <DictSelect type-code="customer_type" v-model="formData.customerType" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('客户等级')">
              <DictSelect type-code="customer_level" v-model="formData.customerLevel" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('联系人')"><el-input v-model="formData.contactPerson" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('联系电话')"><el-input v-model="formData.contactPhone" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('邮箱')"><el-input v-model="formData.email" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('税号')"><el-input v-model="formData.taxNo" /></el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$tf('地址')"><el-input v-model="formData.address" /></el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">{{ $tt("CRM 扩展信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('CRM状态')">
              <el-select v-model="formData.crmStatus" style="width:100%">
                <el-option v-for="s in crmStatusOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('销售阶段')">
              <el-select v-model="formData.salesStage" style="width:100%" clearable>
                <el-option v-for="s in salesStageOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('意向等级')">
              <el-select v-model="formData.intentionLevel" style="width:100%" clearable>
                <el-option v-for="s in ['A','B','C','D']" :key="s" :label="s + '级'" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('客户来源')">
              <DictSelect type-code="customer_source" v-model="formData.sourceType" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$tf('来源详情')"><el-input v-model="formData.sourceDetail" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('所属行业')">
              <DictSelect type-code="industry" v-model="formData.industry" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('公司规模')">
              <el-select v-model="formData.companyScale" style="width:100%" clearable>
                <el-option v-for="s in companyScaleOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('年营业额')">
              <el-input-number v-model="formData.annualRevenue" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('重要程度')">
              <el-select v-model="formData.importantLevel" style="width:100%">
                <el-option v-for="s in importantLevelOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('潜在价值')">
              <el-input-number v-model="formData.potentialValue" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('满意度评分')">
              <el-rate v-model="formData.satisfactionScore" :max="5" show-score />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('最后联系日期')">
              <el-date-picker v-model="formData.lastContactDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('下次联系日期')">
              <el-date-picker v-model="formData.nextContactDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('分配销售员ID')">
              <el-select v-model="formData.assignedSalesId" :placeholder="$tt('请选择员工')" filterable clearable style="width:100%">
                <el-option v-for="opt in employeeOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('微信')"><el-input v-model="formData.wechat" /></el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$tf('公司网站')"><el-input v-model="formData.website" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item :label="$tf('客户标签')">
              <el-input v-model="formData.tag" :placeholder="$tt('多标签用逗号分隔，如: 重点,制造业,华东')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('是否黑名单')">
              <el-switch v-model="formData.isBlacklist" :active-value="1" :inactive-value="0" :active-text="$tt('黑名单')" :inactive-text="$tt('正常')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('信用额度')">
              <el-input-number v-model="formData.creditLimit" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('付款条件ID')">
              <PaymentTermSelect v-model="formData.paymentTermId" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('币种ID')">
              <el-select v-model="formData.currencyId" :placeholder="$tt('请选择币种')" filterable clearable style="width:100%">
                <el-option v-for="opt in currencyOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('默认业务员ID')">
              <el-select v-model="formData.defaultSalerId" :placeholder="$tt('请选择员工')" filterable clearable style="width:100%">
                <el-option v-for="opt in employeeOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('默认价格等级ID')">
              <el-select v-model="formData.defaultPriceLevelId" :placeholder="$tt('请选择价格等级')" filterable clearable style="width:100%">
                <el-option v-for="opt in priceLevelOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('状态')">
              <el-select v-model="formData.status" style="width:100%">
                <el-option :label="$tf('正常')" value="正常" />
                <el-option :label="$tf('停用')" value="停用" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('备注')">
              <el-input v-model="formData.remark" type="textarea" :rows="2" :placeholder="$tt('备注信息')" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-dialog>

    <!-- 跟进登记弹窗 -->
    <el-dialog v-model="followUpVisible" :title="$tf('客户跟进登记')" width="600px">
      <el-form :model="followUpForm" label-width="100px">
        <el-form-item :label="$tf('客户')">{{ followUpForm.customerName }}</el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$tf('联系人')" required>
              <el-input v-model="followUpForm.contactPerson" :placeholder="$tt('请输入联系人')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('跟进日期')">
              <el-date-picker v-model="followUpForm.followUpDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('跟进方式')">
              <el-select v-model="followUpForm.followUpType" style="width:100%" :placeholder="$tt('请选择')">
                <el-option :label="$tf('电话')" value="电话" />
                <el-option :label="$tf('邮件')" value="邮件" />
                <el-option :label="$tf('上门拜访')" value="上门拜访" />
                <el-option :label="$tf('微信')" value="微信" />
                <el-option :label="$tf('视频会议')" value="视频会议" />
                <el-option :label="$tf('其他')" value="其他" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('跟进内容')">
          <el-input v-model="followUpForm.content" type="textarea" :rows="3" :placeholder="$tt('请输入跟进内容')" />
        </el-form-item>
        <el-form-item :label="$tf('跟进结果')">
          <el-input v-model="followUpForm.result" :placeholder="$tt('如：客户表示有意向，待报价')" />
        </el-form-item>
        <el-form-item :label="$tf('下次计划')">
          <el-input v-model="followUpForm.nextPlan" :placeholder="$tt('下次跟进计划')" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$tf('下次跟进日期')">
              <el-date-picker v-model="followUpForm.nextFollowUpDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('重要标记')">
              <el-switch v-model="followUpForm.importantFlag" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('附件')">
          <el-upload
            ref="followUpUploadRef"
            :action="uploadAction"
            :headers="uploadHeaders"
            :multiple="true"
            :auto-upload="false"
            :file-list="followUpFileList"
            :on-change="handleFollowUpFileChange"
            :on-remove="handleFollowUpFileRemove"
            class="follow-up-upload"
          >
            <el-button size="small" type="primary" v-permission="perm('upload')">{{ $tt("点击上传") }}</el-button>
            <div class="el-upload__tip">{{ $tt("支持图片、文档等格式，可多选") }}</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="followUpVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="handleSaveFollowUp" :loading="saving">{{ $tt("确认跟进") }}</el-button>
      </template>
    </el-dialog>

    <!-- 快速操作弹窗 -->
    <el-dialog v-model="quickActionVisible" :title="quickActionTitle" width="400px">
      <el-form label-width="100px">
        <el-form-item :label="quickActionLabel">
          <el-select v-model="quickActionValue" style="width:100%">
            <el-option v-for="s in quickActionOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="quickActionVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="confirmQuickAction" :loading="saving">{{ $tt("确认") }}</el-button>
      </template>
    </el-dialog>

    <!-- 客户详情弹窗 -->
    <el-dialog v-model="customerDetailVisible" :title="$tf('客户详情')" width="800px" append-to-body>
      <el-tabs v-model="detailActiveTab">
        <el-tab-pane :label="$tf('基本信息')" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="$tf('客户编码')">{{ customerDetail.customerCode }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('客户名称')">{{ customerDetail.customerName }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('归属')">
              <el-tag v-if="customerDetail.poolType === 1" type="warning" size="small">{{ $tt("公海") }}</el-tag>
              <el-tag v-else type="success" size="small">{{ $tt("私海") }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$tf('CRM状态')">
              <el-tag :type="crmStatusType(customerDetail.crmStatus)" size="small">{{ customerDetail.crmStatus || '潜在客户' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$tf('联系人')">{{ customerDetail.contactPerson }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('联系电话')">{{ customerDetail.contactPhone }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('销售阶段')">{{ customerDetail.salesStage || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('意向等级')">{{ customerDetail.intentionLevel || '-' }}级</el-descriptions-item>
            <el-descriptions-item :label="$tf('跟进次数')">{{ customerDetail.followUpCount || 0 }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('最后联系')">{{ customerDetail.lastContactDate || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('下次联系')">{{ customerDetail.nextContactDate || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('累计订单金额')">¥{{ formatMoney(customerDetail.totalOrderAmount) }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane :label="$tf('跟进历史')" name="followUp">
          <div v-if="customerFollowUpList.length === 0" class="empty-tip">{{ $tt("暂无跟进记录") }}</div>
          <div v-else class="follow-up-timeline">
            <div v-for="(item, index) in customerFollowUpList" :key="item.followUpId" class="timeline-item">
              <div class="timeline-dot" :class="{ important: item.importantFlag === 1 }"></div>
              <div class="timeline-content">
                <div class="timeline-header">
                  <span class="follow-date">{{ item.followUpDate }}</span>
                  <span class="follow-type">{{ item.followUpType }}</span>
                  <span v-if="item.importantFlag === 1" class="important-badge">{{ $tt("重要") }}</span>
                </div>
                <div class="follow-content">{{ item.content }}</div>
                <div v-if="item.result" class="follow-result">结果：{{ $dt(item.result) }}</div>
                <div v-if="item.nextPlan" class="follow-next">下次计划：{{ item.nextPlan }}</div>
                <div v-if="item.attachment" class="follow-attachments">
                  <div class="attach-label">{{ $tt("附件：") }}</div>
                  <div class="attach-list">
                    <a
                      v-for="(url, idx) in parseAttachmentUrls(item.attachment)"
                      :key="idx"
                      :href="url"
                      target="_blank"
                      class="attach-item"
                    >
                      <el-icon v-if="isImageUrl(url)"><Picture /></el-icon>
                      <el-icon v-else><Document /></el-icon>
                      {{ getFileName(url) }}
                    </a>
                  </div>
                </div>
                <div class="timeline-footer">
                  <span class="follow-staff">{{ item.followUpStaffName || '未知员工' }}</span>
                  <span class="follow-create-time">{{ item.createDate }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'crm-customer'" :columns="allColumns" />
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Star, StarFilled, Picture, Document, Setting, ArrowDown } from '@element-plus/icons-vue'
import DictSelect from '@/components/DictSelect.vue'
import {
  listCrmCustomer, crmDashboard, getCustomer, saveCustomer,
  updateCustomer, deleteCustomer, updateCrmStatus, updateSalesStage,
  updateIntentionLevel, registerFollowUp, toggleBlacklist,
  toggleCustomerTop
} from '@/api/crm/customer'
import { saveFollowUp, listFollowUp } from '@/api/crm/followUp'
import { releaseToPool } from '@/api/crm/pool'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllPaymentTerm } from '@/api/basedata/payment-term'
import { listAllPriceLevel } from '@/api/basedata/price-level'
import { parseUrls } from '@/api/common/upload'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import PaymentTermSelect from '@/components/PaymentTermSelect.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:customer')
const { handleSortChange, getSortParams } = useTableSort()
// 排序变化后立即重载数据
const onSortChange = (e) => { handleSortChange(e); loadData() }

// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支
// link=编码链接列(点击编辑) tag=归属/黑名单 format=满意度/累计订单金额 custom=行内编辑下拉/带样式日期 纯文本列无 type
const allColumns = [
  { prop: 'customerCode', label: '客户编码', width: 150, sortable: true, fixed: 'left', type: 'link', linkClick: (row) => handleEdit(row) },
  { prop: 'customerName', label: '客户名称', minWidth: 160, sortable: true, tooltip: true },
  { prop: 'poolType', label: '归属', width: 80, align: 'center', type: 'tag', tagType: (row) => (row.poolType === 1 ? 'warning' : 'success'), tagText: (row) => (row.poolType === 1 ? '公海' : '私海') },
  { prop: 'contactPerson', label: '联系人', width: 90, sortable: true },
  { prop: 'contactPhone', label: '联系电话', width: 120, sortable: true },
  { prop: 'crmStatus', label: 'CRM状态', width: 120, align: 'center', type: 'custom' },
  { prop: 'salesStage', label: '销售阶段', width: 120, align: 'center', type: 'custom' },
  { prop: 'intentionLevel', label: '意向等级', width: 90, align: 'center', type: 'custom' },
  { prop: 'importantLevel', label: '重要程度', width: 90, sortable: true },
  { prop: 'satisfactionScore', label: '满意度', width: 70, align: 'center', sortable: true, type: 'format', format: (row) => `${row.satisfactionScore || 0}分` },
  { prop: 'followUpCount', label: '跟进次数', width: 80, align: 'center', sortable: true },
  { prop: 'lastContactDate', label: '最后联系', width: 100, sortable: true },
  { prop: 'nextContactDate', label: '下次联系', width: 100, sortable: true, type: 'custom' },
  { prop: 'orderCount', label: '订单数', width: 70, align: 'center', sortable: true },
  { prop: 'totalOrderAmount', label: '累计订单金额', width: 120, sortable: true, type: 'format', format: (row) => `¥${formatMoney(row.totalOrderAmount)}` },
  { prop: 'isBlacklist', label: '黑名单', width: 80, align: 'center', type: 'tag', tagType: (row) => (row.isBlacklist === 1 ? 'danger' : 'success'), tagText: (row) => (row.isBlacklist === 1 ? '黑名单' : '正常') },
  { prop: 'remark', label: '备注', minWidth: 120, sortable: true, tooltip: true }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('crm-customer', allColumns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

// 选项常量 (严格遵循文档定义；客户类型/客户等级/客户来源/所属行业已改用数据字典 DictSelect)
const crmStatusOptions = ['潜在客户', '意向客户', '正式客户', '休眠客户', '已流失']
const salesStageOptions = ['初步接触', '需求确认', '方案报价', '商务谈判', '成交', '已流失']
const companyScaleOptions = ['10人以下', '10-50人', '50-100人', '100-500人', '500人以上']
const importantLevelOptions = ['VIP', '重要', '普通', '不重要']

// 数据状态
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const customers = ref([])
const dashboard = ref({})
const search = reactive({ keyword: '', crmStatus: '', salesStage: '', intentionLevel: '', isBlacklist: null, poolType: 0 })
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const selection = ref([])

function onSelectionChange(rows) { selection.value = rows }

// 仪表盘统计项
// 总客户数卡片跟随当前归属筛选口径（私海/公海/全部），与列表一致，避免与列表条数不一致造成误导
const customerTotal = ref(0)
const statItems = computed(() => [
  { key: 'total', label: search.poolType === 1 ? '公海客户数' : (search.poolType === '' || search.poolType == null ? '全部客户数' : '私海客户数'), value: customerTotal.value, color: '#409EFF' },
  { key: 'today', label: '今日待跟进', value: dashboard.value.todayFollowUps || 0, color: '#E6A23C' },
  { key: 'blacklist', label: '黑名单数', value: dashboard.value.blacklistCount || 0, color: '#F56C6C' }
])

// 按当前归属刷新总客户数卡片（仅归属维度，不含关键词等其它筛选）
const refreshCustomerTotal = async () => {
  try {
    const pool = search.poolType === '' || search.poolType == null ? undefined : search.poolType
    const res = await listCrmCustomer({ page: 1, size: 1, poolType: pool })
    customerTotal.value = res.total || 0
  } catch (e) { customerTotal.value = 0 }
}
// 归属切换：刷新列表 + 刷新卡片总数
const onPoolTypeChange = () => {
  page.current = 1
  loadData()
  refreshCustomerTotal()
}

// 置顶优先排序
const sortedCustomers = computed(() => {
  const list = [...customers.value]
  list.sort((a, b) => {
    const aTop = a.isTop === 1 ? 1 : 0
    const bTop = b.isTop === 1 ? 1 : 0
    if (aTop !== bTop) return bTop - aTop
    return 0
  })
  return list
})

// 弹窗状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const defaultForm = () => ({
  customerId: null, customerCode: '', customerName: '', shortName: '',
  customerType: '企业客户', customerLevel: '普通客户', taxNo: '',
  contactPerson: '', contactPhone: '', email: '', address: '',
  creditLimit: 0, paymentTermId: null, currencyId: null,
  defaultSalerId: null, defaultPriceLevelId: null, status: '正常',
  // CRM字段
  sourceType: '', sourceDetail: '', industry: '', companyScale: '',
  annualRevenue: 0, wechat: '', website: '', crmStatus: '潜在客户',
  lastContactDate: null, nextContactDate: null, importantLevel: '普通',
  potentialValue: 0, satisfactionScore: 0, tag: '', salesStage: '',
  intentionLevel: '', assignedSalesId: null, isBlacklist: 0, remark: ''
})
const formData = reactive(defaultForm())
const formRules = {
  customerCode: [{ required: true, message: tt('请输入客户编码'), trigger: 'blur' }],
  customerName: [{ required: true, message: tt('请输入客户名称'), trigger: 'blur' }]
}

// 跟进登记
const followUpVisible = ref(false)
const followUpForm = reactive({
  customerId: null, customerName: '', contactPerson: '',
  followUpDate: '', followUpType: '电话', content: '',
  result: '', nextPlan: '', nextFollowUpDate: '', importantFlag: 0
})
const followUpUploadRef = ref()
const followUpFileList = ref([])
const uploadedUrls = ref([])

// 客户详情
const customerDetailVisible = ref(false)
const detailActiveTab = ref('basic')
const customerDetail = ref({})
const customerFollowUpList = ref([])

// 上传配置
const uploadAction = '/api/files/upload'
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + localStorage.getItem('token') }))

// 快速操作
const quickActionVisible = ref(false)
const quickActionType = ref('')
const quickActionRow = ref(null)
const quickActionValue = ref('')
const quickActionTitle = computed(() => {
  const map = { crmStatus: '更新CRM状态', salesStage: '更新销售阶段', intentionLevel: '更新意向等级' }
  return map[quickActionType.value] || ''
})
const quickActionLabel = computed(() => {
  const map = { crmStatus: 'CRM状态', salesStage: '销售阶段', intentionLevel: '意向等级' }
  return map[quickActionType.value] || ''
})
const quickActionOptions = computed(() => {
  if (quickActionType.value === 'crmStatus') return crmStatusOptions
  if (quickActionType.value === 'salesStage') return salesStageOptions
  if (quickActionType.value === 'intentionLevel') return ['A', 'B', 'C', 'D']
  return []
})

// 颜色映射
const crmStatusType = (s) => {
  const map = { '潜在客户': 'info', '意向客户': 'warning', '正式客户': 'success', '休眠客户': '', '已流失': 'danger' }
  return map[s] || 'info'
}
const salesStageType = (s) => {
  const map = { '初步接触': 'info', '需求确认': '', '方案报价': 'warning', '商务谈判': 'warning', '成交': 'success', '已流失': 'danger' }
  return map[s] || 'info'
}
const intentionLevelType = (s) => {
  const map = { 'A': 'danger', 'B': 'warning', 'C': '', 'D': 'info' }
  return map[s] || 'info'
}

const isToday = (dateStr) => {
  if (!dateStr) return false
  const today = new Date()
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return dateStr === todayStr
}

const formatMoney = (val) => {
  if (val == null) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const handleStatClick = (key) => {
  if (key === 'today') {
    search.crmStatus = ''
    page.current = 1
    loadData()
    ElMessage.info(tt('已筛选今日待跟进客户'))
  } else if (key === 'blacklist') {
    search.isBlacklist = 1
    search.crmStatus = ''
    search.salesStage = ''
    search.intentionLevel = ''
    search.poolType = 0
    page.current = 1
    loadData()
    refreshCustomerTotal()
    ElMessage.info(tt('已筛选黑名单客户'))
  } else {
    search.isBlacklist = null
    search.crmStatus = ''
    search.salesStage = ''
    search.intentionLevel = ''
    search.poolType = 0
    page.current = 1
    loadData()
    refreshCustomerTotal()
    ElMessage.info(tt('已显示全部私海客户'))
  }
}

const handleDistTagClick = (type, value) => {
  if (type === 'crmStatus') {
    search.crmStatus = value
    search.salesStage = ''
    search.intentionLevel = ''
  } else if (type === 'salesStage') {
    search.salesStage = value
    search.crmStatus = ''
    search.intentionLevel = ''
  } else if (type === 'intentionLevel') {
    search.intentionLevel = value
    search.crmStatus = ''
    search.salesStage = ''
  }
  search.isBlacklist = null
  search.poolType = 0
  page.current = 1
  loadData()
  refreshCustomerTotal()
  ElMessage.info(`${tt("已筛选")}${value}${tt("相关私海客户")}`)
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.current, size: page.size,
      keyword: search.keyword || undefined,
      crmStatus: search.crmStatus || undefined,
      salesStage: search.salesStage || undefined,
      intentionLevel: search.intentionLevel || undefined,
      isBlacklist: search.isBlacklist ?? undefined,
      poolType: search.poolType ?? undefined,
      assignedSalesId: undefined,
      ...getSortParams()
    }
    const res = await listCrmCustomer(params)
    customers.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
  }
}

const loadDashboard = async () => {
  try {
    dashboard.value = await crmDashboard() || {}
  } catch (e) { /* 忽略仪表盘加载错误 */ }
  // 私海客户数卡片（customerTotal）与仪表盘卡片必须同一时刻刷新：
  // 历史 BUG：本方法原先只刷仪表盘（状态分布/今日待跟进/黑名单），私海客户数要单独调 refreshCustomerTotal，
  // 而新增/编辑/删除/跟进/退回公海等变更后只调了 loadDashboard() → 状态分布自动变了、私海客户数要手动点一下才变。
  // 现统一收敛在本方法内，凡调用 loadDashboard() 的地方（含全部变更后刷新）卡片都会一起更新。
  refreshCustomerTotal()
}

const handleSearch = () => { page.current = 1; loadData() }
const handleReset = () => {
  search.keyword = ''; search.crmStatus = ''; search.salesStage = ''
  search.intentionLevel = ''; search.isBlacklist = null; search.poolType = 0
  page.current = 1; loadData(); refreshCustomerTotal()
}

// 新增/编辑
const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, defaultForm())
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  isEdit.value = true
  try {
    const detail = await getCustomer(row.customerId)
    Object.assign(formData, defaultForm(), detail)
    dialogVisible.value = true
  } catch (e) { /* 忽略 */ }
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateCustomer(formData.customerId, formData)
    } else {
      await saveCustomer(formData)
    }
    ElMessage.success(isEdit.value ? tt('修改成功') : tt('新增成功'))
    dialogVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

// 编辑弹窗内删除客户
const handleDeleteInDialog = async () => {
  if (!formData.customerId) return
  try {
    await ElMessageBox.confirm(
      `${tt("确认删除客户【")}${formData.customerName}${tt("】？删除后不可恢复，且被业务单据引用的客户无法删除。")}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteCustomer(formData.customerId)
    ElMessage.success(tt('删除成功'))
    dialogVisible.value = false
    loadData()
    loadDashboard()
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
  } finally {
    deleting.value = false
  }
}

const handleFollowUpFileChange = (file, fileList) => {
  followUpFileList.value = fileList.filter(f => f.status !== 'removed')
}

const handleFollowUpFileRemove = (file, fileList) => {
  followUpFileList.value = fileList
  uploadedUrls.value = uploadedUrls.value.filter(u => u !== file.response?.data)
}

const handleSaveFollowUp = async () => {
  if (!followUpForm.contactPerson || followUpForm.contactPerson.trim() === '') {
    ElMessage.warning(tt('请填写联系人'))
    return
  }
  if (!followUpForm.content || followUpForm.content.trim() === '') {
    ElMessage.warning(tt('请填写跟进内容'))
    return
  }
  saving.value = true
  try {
    const pendingFiles = followUpFileList.value.filter(f => f.status === 'ready')
    if (pendingFiles.length > 0) {
      for (const file of pendingFiles) {
        const formData = new FormData()
        formData.append('file', file.raw)
        const res = await fetch(uploadAction, {
          method: 'POST',
          headers: { ...uploadHeaders.value },
          body: formData
        })
        const data = await res.json()
        if (data.code === 200) {
          uploadedUrls.value.push(data.data)
        }
      }
    }
    const fuData = {
      sourceType: '客户',
      sourceId: followUpForm.customerId,
      sourceName: followUpForm.customerName,
      contactPerson: followUpForm.contactPerson,
      followUpDate: followUpForm.followUpDate,
      followUpType: followUpForm.followUpType || '电话',
      content: followUpForm.content,
      result: followUpForm.result,
      nextPlan: followUpForm.nextPlan,
      nextFollowUpDate: followUpForm.nextFollowUpDate,
      importantFlag: followUpForm.importantFlag,
      attachment: uploadedUrls.value.join(',')
    }
    await saveFollowUp(fuData)
    await registerFollowUp(followUpForm.customerId, {
      lastContactDate: followUpForm.followUpDate,
      nextContactDate: followUpForm.nextFollowUpDate
    })
    ElMessage.success(tt('跟进登记成功'))
    followUpVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

const parseAttachmentUrls = (str) => {
  if (!str) return []
  return str.split(',').filter(u => u.trim())
}

const isImageUrl = (url) => {
  const ext = url.split('.').pop().toLowerCase()
  return ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext)
}

const getFileName = (url) => {
  return url.split('/').pop()
}

// 切换置顶
const handleToggleTop = async (row) => {
  if (row.poolType === 1) {
    ElMessage.warning(tt('公海客户暂不支持置顶，请先领取到私海'))
    return
  }
  const newTop = row.isTop === 1 ? 0 : 1
  const action = newTop === 1 ? '置顶' : '取消置顶'
  try {
    await toggleCustomerTop(row.customerId, newTop)
    ElMessage.success(`${action}${tt("成功")}`)
    row.isTop = newTop
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
  }
}

// 单个退回公海
const handleRelease = async (row) => {
  try {
    const { value: reason } = await ElMessageBox.prompt(
      `${tt("确认将客户「")}${row.customerName}${tt("」退回公海？")}`,
      tt('退回公海'),
      { confirmButtonText: tt('退回'), cancelButtonText: tt('取消'), inputPlaceholder: tt('请输入退回原因（可选）'), inputType: 'textarea' }
    )
    await releaseToPool(row.customerId, reason || '')
    ElMessage.success(tt('已退回公海'))
    loadData()
    loadDashboard()
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
  }
}

// 批量退回公海
const handleBatchRelease = async () => {
  const rows = selection.value.filter(r => r.poolType === 0)
  if (rows.length === 0) {
    ElMessage.warning(tt('请选择私海客户'))
    return
  }
  try {
    const { value: reason } = await ElMessageBox.prompt(
      `${tt("确认将选中的 ")}${rows.length}${tt(" 个私海客户退回公海？")}`,
      tt('批量退回公海'),
      { confirmButtonText: tt('退回'), cancelButtonText: tt('取消'), inputPlaceholder: tt('请输入退回原因（可选）'), inputType: 'textarea' }
    )
    saving.value = true
    let success = 0, fail = 0
    for (const row of rows) {
      try {
        await releaseToPool(row.customerId, reason || '')
        success++
      } catch (e) {
        fail++
      }
    }
    ElMessage.success(`批量退回完成：成功 ${success} 个${fail > 0 ? `${tt("，失败 ")}${fail}${tt(" 个")}` : ''}`)
    loadData()
    loadDashboard()
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

// 行内编辑（CRM状态/销售阶段/意向等级）
const handleInlineEdit = async (row, field) => {
  const labelMap = { crmStatus: 'CRM状态', salesStage: '销售阶段', intentionLevel: '意向等级' }
  const val = row[field]
  try {
    if (field === 'crmStatus') await updateCrmStatus(row.customerId, val || '')
    else if (field === 'salesStage') await updateSalesStage(row.customerId, val || '')
    else if (field === 'intentionLevel') await updateIntentionLevel(row.customerId, val || '')
    ElMessage.success(`${labelMap[field]}${tt("更新成功")}`)
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
    loadData()
  }
}

// 查看客户详情（基本信息 + 跟进历史）
const handleView = async (row) => {
  try {
    const detail = await getCustomer(row.customerId)
    customerDetail.value = detail || {}
    // 加载该客户的跟进历史
    try {
      const res = await listFollowUp({ sourceType: '客户', sourceId: row.customerId, size: 100 })
      customerFollowUpList.value = (res && res.records) || []
    } catch (e) {
      customerFollowUpList.value = []
    }
    detailActiveTab.value = 'basic'
    customerDetailVisible.value = true
  } catch (e) { /* 忽略 */ }
}

// 跟进登记入口：填充跟进表单并打开弹窗
const handleFollowUp = (row) => {
  Object.assign(followUpForm, {
    customerId: row.customerId,
    customerName: row.customerName,
    contactPerson: row.contactPerson || '',
    followUpDate: new Date().toISOString().slice(0, 10),
    followUpType: '电话',
    content: '',
    result: '',
    nextPlan: '',
    nextFollowUpDate: row.nextContactDate || '',
    importantFlag: 0
  })
  followUpFileList.value = []
  uploadedUrls.value = []
  followUpVisible.value = true
}

// 行内"更多"下拉分发：快速操作/黑名单/置顶/退回公海
const handleRowCommand = (cmd, row) => {
  if (cmd === 'crmStatus' || cmd === 'salesStage' || cmd === 'intentionLevel') {
    quickActionType.value = cmd
    quickActionRow.value = row
    quickActionValue.value = row[cmd] || ''
    quickActionVisible.value = true
  } else if (cmd === 'blacklist') {
    handleToggleBlacklist(row)
  } else if (cmd === 'top') {
    handleToggleTop(row)
  } else if (cmd === 'release') {
    handleRelease(row)
  }
}

// 切换黑名单状态
const handleToggleBlacklist = async (row) => {
  const newFlag = row.isBlacklist === 1 ? 0 : 1
  const action = newFlag === 1 ? '加入黑名单' : '移出黑名单'
  try {
    await ElMessageBox.confirm(
      `${tt("确认将客户「")}${row.customerName}」${action}？`,
      action,
      { type: 'warning', confirmButtonText: tt('确认'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await toggleBlacklist(row.customerId, newFlag)
    ElMessage.success(`${action}${tt("成功")}`)
    loadData()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

const confirmQuickAction = async () => {
  if (!quickActionValue.value) {
    ElMessage.warning(tt('请选择值'))
    return
  }
  saving.value = true
  try {
    const id = quickActionRow.value.customerId
    if (quickActionType.value === 'crmStatus') await updateCrmStatus(id, quickActionValue.value)
    else if (quickActionType.value === 'salesStage') await updateSalesStage(id, quickActionValue.value)
    else if (quickActionType.value === 'intentionLevel') await updateIntentionLevel(id, quickActionValue.value)
    ElMessage.success(tt('更新成功'))
    quickActionVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

// 外键下拉选项
const employeeOptions = ref([])
const currencyOptions = ref([])
const paymentTermOptions = ref([])
const priceLevelOptions = ref([])
async function loadDropdownOptions() {
  try {
    const empList = await listAllEmployee()
    employeeOptions.value = (Array.isArray(empList) ? empList : (empList?.records || []))
      .map(e => ({ value: e.employeeId, label: e.employeeName + (e.deptName ? ' (' + e.deptName + ')' : '') }))
  } catch (e) { console.warn('加载员工失败', e) }
  try {
    const curList = await listAllCurrency()
    currencyOptions.value = (Array.isArray(curList) ? curList : (curList?.records || []))
      .map(c => ({ value: c.currencyId, label: c.currencyName }))
  } catch (e) { console.warn('加载币种失败', e) }
  try {
    const ptList = await listAllPaymentTerm()
    paymentTermOptions.value = (Array.isArray(ptList) ? ptList : (ptList?.records || []))
      .map(p => ({ value: p.paymentTermId, label: p.paymentTermName }))
  } catch (e) { console.warn('加载付款条件失败', e) }
  try {
    const plList = await listAllPriceLevel()
    priceLevelOptions.value = (Array.isArray(plList) ? plList : (plList?.records || []))
      .map(p => ({ value: p.priceLevelId, label: p.priceLevelName }))
  } catch (e) { console.warn('加载价格等级失败', e) }
}

// 2026-10-09：从「销售预警 → 去处理」跳入时带条件（kw = 客户名称），直接定位到该客户
const _route = useRoute()
if (_route.query.kw) search.keyword = String(_route.query.kw)

onMounted(() => {
  loadData()
  loadDashboard()   // 内含私海客户数卡片刷新
  loadDropdownOptions()
})
// 切回标签页自动刷新：必须传"会被真正执行的函数"。
// 历史 BUG：原写法 useAutoRefresh(() => { () => {...} }) 传进去的是"返回函数的函数"，
// loadFn() 只返回一个函数、从不执行 → 该入口一直空转（切回标签页数字不变，要手动点一下卡片才刷新）。
useAutoRefresh(() => {
  loadData()
  loadDashboard()
})
</script>

<style scoped lang="scss">
.erp-page { padding: 0; }
.dashboard-card { background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%); }
.dashboard-row { display: flex; gap: 16px; flex-wrap: wrap; align-items: stretch; }
.stat-item {
  flex: 1; min-width: 140px;
  background: #fff; border-radius: 8px; padding: 12px 20px;
  border: 1px solid #ebeef5;
  display: flex; flex-direction: column; justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  &:hover { background: #f5f7fa; border-color: #409EFF; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15); }
}
.stat-label { font-size: 13px; color: #909399; margin-bottom: 4px; }
.stat-value { font-size: 26px; font-weight: 700; }
.stat-hint { font-size: 11px; color: #c0c4cc; margin-top: 4px; }
.dist-block {
  flex: 1; min-width: 280px;
  background: #fff; border-radius: 8px; padding: 10px 16px;
  border: 1px solid #ebeef5;
}
.dist-title { font-size: 13px; color: #606266; margin-bottom: 8px; font-weight: 600; }
.dist-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.dist-tag { margin: 0; cursor: pointer; transition: all 0.2s; &:hover { transform: scale(1.05); } }
.filter-bar {
  display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
}
.top-icon { animation: pulse 2s infinite; }
.action-btns {
  display: flex;
  align-items: center;
  gap: 2px;
}
.action-btns .el-button,
.action-btns .el-dropdown {
  display: inline-flex;
  align-items: center;
}
.action-btns .el-icon {
  margin-left: 2px;
  font-size: 12px;
}
.top-star {
  cursor: default;
  color: #dcdfe6;
  transition: all 0.2s;
  vertical-align: middle;
  &.can-top { cursor: pointer; }
  &.can-top:hover { color: #f0b43d; transform: scale(1.2); }
  &.is-top { color: #e6a23c; animation: pulse 2s infinite; }
  &.is-top:hover { color: #f0b43d; }
}
@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.2); }
}
.empty-tip {
  text-align: center; padding: 40px 0; color: #909399; font-size: 14px;
}
.follow-up-timeline {
  max-height: 500px; overflow-y: auto; padding: 10px 0;
}
.timeline-item {
  display: flex; position: relative; padding-bottom: 24px;
  &:not(:last-child)::before {
    content: ''; position: absolute; left: 7px; top: 18px; bottom: -4px;
    width: 2px; background: #e4e7ed;
  }
}
.timeline-dot {
  width: 16px; height: 16px; border-radius: 50%; background: #c0c4cc;
  margin-right: 16px; margin-top: 2px; flex-shrink: 0; z-index: 1;
  &.important { background: #f56c6c; box-shadow: 0 0 6px #f56c6c; }
}
.timeline-content {
  flex: 1; background: #f5f7fa; border-radius: 6px; padding: 12px 16px;
}
.timeline-header {
  display: flex; align-items: center; gap: 8px; margin-bottom: 8px;
}
.follow-date { font-weight: 600; color: #303133; font-size: 14px; }
.follow-type {
  font-size: 12px; color: #409eff; background: #ecf5ff; padding: 2px 8px;
  border-radius: 10px;
}
.important-badge {
  font-size: 12px; color: #fff; background: #f56c6c; padding: 2px 8px;
  border-radius: 10px;
}
.follow-content {
  color: #606266; font-size: 14px; line-height: 1.6; margin-bottom: 6px;
  white-space: pre-wrap;
}
.follow-result, .follow-next {
  color: #909399; font-size: 13px; margin-bottom: 4px;
}
.follow-attachments {
  margin-top: 8px; padding-top: 8px; border-top: 1px dashed #dcdfe6;
}
.attach-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.attach-list { display: flex; flex-wrap: wrap; gap: 8px; }
.attach-item {
  display: inline-flex; align-items: center; gap: 4px; font-size: 12px;
  color: #409eff; text-decoration: none; padding: 4px 10px;
  background: #ecf5ff; border-radius: 4px; transition: all 0.2s;
  &:hover { background: #409eff; color: #fff; }
}
.timeline-footer {
  display: flex; justify-content: space-between; margin-top: 8px;
  padding-top: 6px; border-top: 1px dashed #dcdfe6;
  font-size: 12px; color: #c0c4cc;
}
.follow-up-upload { width: 100%; }
.follow-up-upload :deep(.el-upload-list) { max-height: 200px; overflow-y: auto; }
</style>
