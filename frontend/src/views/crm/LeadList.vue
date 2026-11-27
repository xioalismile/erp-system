<template>
  <div class="erp-page">
    <!-- 销售线索仪表盘 -->
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
          <div class="dist-title">{{ $tt("线索状态分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.statusDistribution || {}" :key="k"
                    :type="leadStatusType(k)" size="large" class="dist-tag dist-tag-clickable"
                    @click="handleDistClick('leadStatus', k)">
              {{ k }}: {{ v }}
            </el-tag>
          </div>
        </div>
        <div class="dist-block">
          <div class="dist-title">{{ $tt("意向等级分布") }}</div>
          <div class="dist-tags">
            <el-tag v-for="(v, k) in dashboard.intentionLevelDist || {}" :key="k"
                    :type="intentionLevelType(k)" size="large" class="dist-tag dist-tag-clickable"
                    @click="handleDistClick('intentionLevel', k)">
              {{ k }}级: {{ v }}
            </el-tag>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 筛选区 -->
    <el-card shadow="never" style="margin-top:12px;">
      <div class="filter-bar">
        <el-input v-model="search.keyword" :placeholder="$tt('线索编号/名称/公司/联系人/电话')" style="width:260px;" clearable />
        <el-select v-model="search.leadStatus" :placeholder="$tt('线索状态')" style="width:130px;" clearable>
          <el-option v-for="s in leadStatusOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="search.intentionLevel" :placeholder="$tt('意向等级')" style="width:110px;" clearable>
          <el-option v-for="s in intentionLevelOptions" :key="s" :label="s + '级'" :value="s" />
        </el-select>
        <el-button type="primary" v-permission="perm('query')" @click="handleSearch">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
        </el-tooltip>
        <div style="flex:1;"></div>
        <el-button type="primary" v-permission="perm('add')" @click="handleAdd">{{ $tt("+ 新增线索") }}</el-button>
        <el-button type="success" v-permission="perm('export')" @click="handleExport">{{ $tt("导出") }}</el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="handleImport"
          accept=".xlsx,.xls"
          style="margin-left: 10px;"
        >
          <el-button type="warning" v-permission="perm('import')">{{ $tt("导入") }}</el-button>
        </el-upload>
      </div>
    </el-card>

    <!-- 线索列表 -->
    <el-card shadow="never" style="margin-top:12px;">
      <el-table :data="leads" v-loading="loading" border stripe @sort-change="onSortChange" :key="configVersion">
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
              <el-tag v-else-if="col.type === 'tag'" :type="col.tagType ? col.tagType(row) : 'info'" :effect="col.tagEffect || 'light'" size="small">{{ col.tagText ? col.tagText(row) : (row[col.prop] || '-') }}</el-tag>
              <span v-else-if="col.type === 'format'">{{ col.format(row) }}</span>
              <el-image
                v-else-if="col.type === 'custom' && col.prop === 'imageUrl' && row.imageUrl"
                :src="row.imageUrl.split(',')[0]"
                :preview-src-list="row.imageUrl.split(',')"
                preview-teleported
                fit="cover"
                style="width:36px;height:36px;border-radius:4px;"
              />
              <span v-else-if="col.type === 'custom' && col.prop === 'nextFollowUpDate'" :style="isToday(row.nextFollowUpDate) ? 'color:#f56c6c;font-weight:bold;' : ''">
                {{ row.nextFollowUpDate || '-' }}
              </span>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
        <!-- 操作列：查看/编辑/跟进/更多（状态/分配/放弃/转化/删除） -->
        <el-table-column :label="$tf('操作')" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="perm('view')" @click="handleView(row)">{{ $tt("查看") }}</el-button>
            <el-button type="primary" link size="small" v-permission="perm('edit')" @click="handleEdit(row)">{{ $tt("编辑") }}</el-button>
            <el-button type="success" link size="small" v-permission="perm('add')" @click="handleFollowUp(row)">{{ $tt("跟进") }}</el-button>
            <el-dropdown trigger="click" @command="(cmd) => handleRowCommand(cmd, row)">
              <el-button type="primary" link size="small">{{ $tt("更多") }}<el-icon style="margin-left:2px;"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="status" :disabled="row.leadStatus === '已转化' || row.leadStatus === '已放弃'">{{ $tt("更新状态") }}</el-dropdown-item>
                  <el-dropdown-item command="assign" :disabled="row.leadStatus === '已转化' || row.leadStatus === '已放弃'">{{ $tt("分配销售员") }}</el-dropdown-item>
                  <el-dropdown-item command="convert" :disabled="row.leadStatus !== '跟进中'">{{ $tt("转化为客户") }}</el-dropdown-item>
                  <el-dropdown-item command="abandon" :disabled="row.leadStatus === '已转化' || row.leadStatus === '已放弃'">{{ $tt("放弃线索") }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>{{ $tt("删除") }}</el-dropdown-item>
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
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑线索' : '新增线索'" width="900px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-divider content-position="left">{{ $tt("基本信息") }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('线索编号')">
              <el-input v-model="formData.leadNo" :disabled="docCodeDisabled && !isEdit" :placeholder="docCodePlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('线索名称')" prop="leadName">
              <el-input v-model="formData.leadName" :placeholder="$tt('如 XX采购需求')" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$tf('公司名称')" prop="companyName">
              <el-select
                v-model="formData.companyName"
                filterable
                allow-create
                default-first-option
                clearable
                :placeholder="$tt('请选择客户（支持模糊搜索）')"
                style="width:100%"
                @change="onCompanyChange"
              >
                <el-option
                  v-for="opt in withFallbackOption(customerOptions, 'companyName', 'customerName', formData)"
                  :key="opt.value"
                  :label="$tf(opt.label)"
                  :value="opt.label"
                >
                  <span style="float:left;">{{ $tf(opt.label) }}</span>
                  <span v-if="opt.shortName" style="float:right;color:#909399;font-size:12px;">{{ opt.shortName }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('联系人')" prop="contactPerson">
              <el-input v-model="formData.contactPerson" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('联系电话')" prop="contactPhone">
              <el-input v-model="formData.contactPhone" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('邮箱')">
              <el-input v-model="formData.contactEmail" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('职位')">
              <el-input v-model="formData.contactPosition" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('线索类型')">
              <el-select v-model="formData.leadType" style="width:100%">
                <el-option v-for="s in leadTypeOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$tf('意向等级')">
              <el-select v-model="formData.intentionLevel" style="width:100%">
                <el-option v-for="s in intentionLevelOptions" :key="s" :label="s + '级'" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('来源类型')">
              <DictSelect type-code="customer_source" v-model="formData.sourceType" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$tf('来源详情')">
              <el-input v-model="formData.sourceDetail" />
            </el-form-item>
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
            <el-form-item :label="$tf('预计金额')">
              <el-input-number v-model="formData.estimatedAmount" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$tf('下次跟进日期')">
              <el-date-picker v-model="formData.nextFollowUpDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
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
          <el-col :span="24">
            <el-form-item :label="$tf('需求描述')">
              <el-input v-model="formData.demandDesc" type="textarea" :rows="3" :placeholder="$tt('请输入客户需求描述')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('备注')">
              <el-input v-model="formData.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$tf('线索图片')">
              <ImageUploader v-model="formData.imageUrl" type="lead" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="handleSave" :loading="saving" v-if="!formData.leadStatus || formData.leadStatus === '新线索' || formData.leadStatus === '跟进中'">{{ $tt("保存") }}</el-button>
        <el-alert v-if="formData.leadStatus === '已转化' || formData.leadStatus === '已放弃'" :title="$tt('当前状态不允许编辑')" type="warning" :closable="false" show-icon style="margin-right:auto;" />
      </template>
    </el-dialog>

    <!-- 跟进登记弹窗 -->
    <el-dialog v-model="followUpVisible" :title="$tf('线索跟进登记')" width="600px">
      <el-form :model="followUpForm" label-width="100px">
        <el-form-item :label="$tf('线索')">{{ followUpForm.leadName }}</el-form-item>
        <el-row :gutter="12">
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
        <el-form-item :label="$tf('联系人')" required>
          <el-input v-model="followUpForm.contactPerson" :placeholder="$tt('请输入联系人')" />
        </el-form-item>
        <el-form-item :label="$tf('跟进内容')" required>
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
            <el-form-item :label="$tf('下次跟进')">
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

    <!-- 线索详情弹窗 -->
    <el-dialog v-model="leadDetailVisible" :title="$tf('线索详情')" width="800px" append-to-body>
      <el-tabs v-model="detailActiveTab">
        <el-tab-pane :label="$tf('基本信息')" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="$tf('线索名称')">{{ leadDetail.leadName }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('线索编号')">{{ leadDetail.leadNo }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('线索状态')">
              <el-tag :type="leadStatusType(leadDetail.leadStatus)" size="small">{{ leadDetail.leadStatus }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$tf('线索类型')">{{ leadDetail.leadType || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('联系人')">{{ leadDetail.contactPerson }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('联系电话')">{{ leadDetail.contactPhone }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('意向等级')">{{ leadDetail.intentionLevel }}级</el-descriptions-item>
            <el-descriptions-item :label="$tf('来源')">{{ leadDetail.sourceType || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('跟进次数')">{{ leadDetail.followUpCount || 0 }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('最后跟进')">{{ leadDetail.lastFollowUpDate || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('下次跟进')">{{ leadDetail.nextFollowUpDate || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('创建日期')">{{ leadDetail.createDate }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane :label="$tf('跟进历史')" name="followUp">
          <div v-if="leadFollowUpList.length === 0" class="empty-tip">{{ $tt("暂无跟进记录") }}</div>
          <div v-else class="follow-up-timeline">
            <div v-for="item in leadFollowUpList" :key="item.followUpId" class="timeline-item">
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
                    <a v-for="(url, idx) in parseAttachmentUrls(item.attachment)" :key="idx" :href="url" target="_blank" class="attach-item">
                      <el-icon v-if="isImageUrl(url)"><Picture /></el-icon>
                      <el-icon v-else><Document /></el-icon>
                      {{ getFileName(url) }}
                    </a>
                  </div>
                </div>
                <div class="timeline-footer">
                  <span class="follow-staff">{{ item.followUpStaffName || '未知' }}</span>
                  <span class="follow-create-time">{{ item.createDate }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 快速操作弹窗 -->
    <el-dialog v-model="quickActionVisible" :title="quickActionTitle" width="400px">
      <el-form label-width="100px">
        <el-form-item :label="quickActionLabel">
          <el-select v-if="quickActionType === 'status'" v-model="quickActionValue" style="width:100%">
            <el-option v-for="s in leadStatusOptions" :key="s" :label="s" :value="s" />
          </el-select>
          <el-select v-else-if="quickActionType === 'assign'" v-model="quickActionValue" :placeholder="$tt('请选择员工')" filterable clearable style="width:100%">
            <el-option v-for="opt in employeeOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="quickActionVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="confirmQuickAction" :loading="saving">{{ $tt("确认") }}</el-button>
      </template>
    </el-dialog>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'crm-lead'" :columns="allColumns" />
  </div>
</template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture, Document, Setting, ArrowDown } from '@element-plus/icons-vue'
import {
  listLead, getLead, saveLead, updateLead, deleteLead,
  updateLeadStatus, assignLead, abandonLead, convertLead, leadDashboard
} from '@/api/crm/lead'
import { saveFollowUp, listFollowUp } from '@/api/crm/followUp'
import { listAllEmployee } from '@/api/basedata/employee'
import { listCrmCustomer } from '@/api/crm/customer'
import { exportListAsSeen, importData } from '@/utils/exportUtil'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import DictSelect from '@/components/DictSelect.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:lead')
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }// === 列设置 ===
// 列定义（用于列设置抽屉+动态渲染）：type 标记单元格渲染分支
// link=单号链接列 tag=标签列 custom=特殊插槽（imageUrl 图片预览 / nextFollowUpDate 今日高亮）
const allColumns = [
  { prop: 'leadNo', label: '线索编号', width: 130, fixed: 'left', sortable: true, type: 'link', linkClick: (row) => handleEdit(row) },
  { prop: 'leadName', label: '线索名称', minWidth: 160, sortable: true, tooltip: true },
  { prop: 'companyName', label: '公司名称', minWidth: 160, sortable: true, tooltip: true },
  { prop: 'contactPerson', label: '联系人', width: 100, sortable: true },
  { prop: 'contactPhone', label: '联系电话', width: 130, sortable: true },
  { prop: 'leadType', label: '线索类型', width: 110, sortable: true },
  { prop: 'intentionLevel', label: '意向等级', width: 90, type: 'tag', tagType: (row) => intentionLevelType(row.intentionLevel) },
  { prop: 'leadStatus', label: '线索状态', width: 100, type: 'tag', tagType: (row) => leadStatusType(row.leadStatus), tagText: (row) => row.leadStatus || '新线索' },
  { prop: 'followUpCount', label: '跟进次数', width: 90, sortable: true },
  { prop: 'lastFollowUpDate', label: '最后跟进', width: 110, sortable: true },
  { prop: 'nextFollowUpDate', label: '下次跟进', width: 110, sortable: true, type: 'custom' },
  { prop: 'remark', label: '备注', minWidth: 120, sortable: true, tooltip: true },
  { prop: 'imageUrl', label: '图片', width: 80, align: 'center', sortable: true, type: 'custom' }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('crm-lead', allColumns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

// 选项常量 (严格遵循文档 7.2 定义；来源类型、所属行业已改用数据字典 DictSelect)
const leadStatusOptions = ['新线索', '跟进中', '已转化', '已放弃']
const intentionLevelOptions = ['A', 'B', 'C']
const leadTypeOptions = ['采购意向', '合作咨询', '产品试用', '代理申请', '其他']
const companyScaleOptions = ['10人以下', '10-50人', '50-100人', '100-500人', '500人以上']

// 数据状态
const loading = ref(false)
const saving = ref(false)
const leads = ref([])
const dashboard = ref({})
const search = reactive({ keyword: '', leadStatus: '', intentionLevel: '' })
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })

// 仪表盘统计项
const statItems = computed(() => [
  { key: 'total', label: '总线索数', value: dashboard.value.totalLeads || 0, color: '#409EFF' },
  { key: 'today', label: '今日待跟进', value: dashboard.value.todayFollowUps || 0, color: '#E6A23C' }
])

// 弹窗状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('LEAD')
const formRef = ref(null)
const defaultForm = () => ({
  leadId: null, leadNo: '', leadName: '', companyName: '',
  contactPerson: '', contactPhone: '', contactEmail: '', contactPosition: '',
  sourceType: '线上广告', sourceDetail: '', leadType: '采购意向',
  industry: '', companyScale: '', demandDesc: '', estimatedAmount: 0,
  intentionLevel: 'C', assignedSalesId: null, nextFollowUpDate: null,
  remark: '', leadStatus: '新线索', imageUrl: ''
})
const formData = reactive(defaultForm())
const formRules = {
  leadName: [{ required: true, message: tt('请输入线索名称'), trigger: 'blur' }],
  companyName: [{ required: true, message: tt('请输入公司名称'), trigger: 'blur' }],
  contactPerson: [{ required: true, message: tt('请输入联系人'), trigger: 'blur' }],
  contactPhone: [{ required: true, message: tt('请输入联系电话'), trigger: 'blur' }]
}

// 跟进登记
const followUpVisible = ref(false)
const followUpForm = reactive({
  leadId: null, leadName: '', contactPerson: '',
  followUpDate: '', followUpType: '电话', content: '',
  result: '', nextPlan: '', nextFollowUpDate: '', importantFlag: 0
})
const followUpUploadRef = ref()
const followUpFileList = ref([])
const uploadedUrls = ref([])
const uploadAction = '/api/files/upload'
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + localStorage.getItem('token') }))

// 线索详情
const leadDetailVisible = ref(false)
const leadDetail = ref({})
const leadFollowUpList = ref([])
const detailActiveTab = ref('basic')

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

// 快速操作
const quickActionVisible = ref(false)
const quickActionType = ref('')
const quickActionRow = ref(null)
const quickActionValue = ref('')
const quickActionTitle = computed(() => {
  const map = { status: '更新线索状态', assign: '分配销售员' }
  return map[quickActionType.value] || ''
})
const quickActionLabel = computed(() => {
  const map = { status: '线索状态', assign: '销售员ID' }
  return map[quickActionType.value] || ''
})

// 颜色映射
const leadStatusType = (s) => {
  const map = { '新线索': 'info', '跟进中': 'warning', '已转化': 'success', '已放弃': 'danger' }
  return map[s] || 'info'
}
const intentionLevelType = (s) => {
  const map = { 'A': 'danger', 'B': 'warning', 'C': 'info' }
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

// 加载数据
/**
 * 列表与导出**共用**的查询条件（2026-09-24 全站口径）：
 * 导出必须与列表用同一套条件，抽成一个函数避免两处各写一份导致口径漂移
 * （用户要求"页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部"）。
 */
const buildQueryParams = () => ({
  keyword: search.keyword || undefined,
  leadStatus: search.leadStatus || undefined,
  intentionLevel: search.intentionLevel || undefined,
  ...getSortParams()
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await listLead({ page: page.current, size: page.size, ...buildQueryParams() })
    leads.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
  }
}

const loadDashboard = async () => {
  try {
    dashboard.value = await leadDashboard() || {}
  } catch (e) { /* 忽略仪表盘加载错误 */ }
}

const handleSearch = () => { page.current = 1; loadData() }
const handleReset = () => {
  search.keyword = ''; search.leadStatus = ''; search.intentionLevel = ''
  page.current = 1; loadData()
}

const handleStatClick = (key) => {
  // 重置筛选
  search.keyword = ''; search.leadStatus = ''; search.intentionLevel = ''
  page.current = 1
  if (key === 'today') {
    loadData()
    ElMessage.info('已筛选今日待跟进线索（请查看tt("下次联系日期")为今天的记录）')
  } else if (key === 'total') {
    loadData()
    ElMessage.info(tt('已显示全部线索明细'))
  }
}

// 分布标签点击：按状态/等级筛选并查看明细
const handleDistClick = (type, value) => {
  search.keyword = ''
  if (type === 'leadStatus') {
    search.leadStatus = value
    search.intentionLevel = ''
  } else if (type === 'intentionLevel') {
    search.intentionLevel = value
    search.leadStatus = ''
  }
  page.current = 1
  loadData()
  ElMessage.info(`${tt("已按")}${type === 'leadStatus' ? tt('线索状态') : tt('意向等级')}【${value}${tt("】筛选明细")}`)
}

// 导入导出
const handleExport = async () => {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'lead',
      columns: visibleSortedColumns.value,
      fetchPage: listLead,
      params: buildQueryParams(),
      idField: 'leadId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

const handleImport = async (file) => {
  try {
    const result = await importData('lead', file)
    ElMessage.success(`${tt("导入成功: ")}${result.successCount}${tt("条, 失败: ")}${result.failCount}${tt("条")}`)
    loadData()
  } catch (e) {
    ElMessage.error(tt('导入失败'))
  }
  return false
}

// 新增/编辑
const handleAdd = () => {
  isEdit.value = false
  Object.assign(formData, defaultForm())
  autoFillDocCodeOnCreate(formData, 'leadNo')
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  isEdit.value = true
  try {
    const detail = await getLead(row.leadId)
    Object.assign(formData, defaultForm(), detail)
    dialogVisible.value = true
  } catch (e) { /* 忽略 */ }
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateLead(formData.leadId, formData)
    } else {
      await saveLead(formData)
    }
    ElMessage.success(isEdit.value ? tt('修改成功') : tt('新增成功'))
    dialogVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
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
    // 上传待传文件
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
    // 保存跟进记录
    await saveFollowUp({
      sourceType: '线索',
      sourceId: followUpForm.leadId,
      sourceName: followUpForm.leadName,
      contactPerson: followUpForm.contactPerson,
      followUpDate: followUpForm.followUpDate,
      followUpType: followUpForm.followUpType || '电话',
      content: followUpForm.content,
      result: followUpForm.result,
      nextPlan: followUpForm.nextPlan,
      nextFollowUpDate: followUpForm.nextFollowUpDate,
      importantFlag: followUpForm.importantFlag,
      attachment: uploadedUrls.value.join(',')
    })
    // 更新线索跟进日期
    await updateLead(followUpForm.leadId, {
      lastFollowUpDate: followUpForm.followUpDate,
      nextFollowUpDate: followUpForm.nextFollowUpDate
    })
    ElMessage.success(tt('跟进登记成功'))
    followUpVisible.value = false
    loadData()
    loadDashboard()
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

const confirmQuickAction = async () => {
  if (quickActionValue.value === '' || quickActionValue.value === null) {
    ElMessage.warning(tt('请填写值'))
    return
  }
  saving.value = true
  try {
    const id = quickActionRow.value.leadId
    if (quickActionType.value === 'status') await updateLeadStatus(id, quickActionValue.value)
    else if (quickActionType.value === 'assign') await assignLead(id, quickActionValue.value)
    ElMessage.success(tt('操作成功'))
    quickActionVisible.value = false
    loadData()
    loadDashboard()
  } finally {
    saving.value = false
  }
}

// 查看线索详情（基本信息 + 跟进历史）
const handleView = async (row) => {
  try {
    const detail = await getLead(row.leadId)
    leadDetail.value = detail || {}
    // 加载该线索的跟进历史
    try {
      const res = await listFollowUp({ sourceType: '线索', sourceId: row.leadId, size: 100 })
      leadFollowUpList.value = (res && res.records) || []
    } catch (e) {
      leadFollowUpList.value = []
    }
    detailActiveTab.value = 'basic'
    leadDetailVisible.value = true
  } catch (e) { /* 忽略 */ }
}

// 跟进登记入口：填充跟进表单并打开弹窗
const handleFollowUp = (row) => {
  Object.assign(followUpForm, {
    leadId: row.leadId,
    leadName: row.leadName,
    contactPerson: row.contactPerson || '',
    followUpDate: new Date().toISOString().slice(0, 10),
    followUpType: '电话',
    content: '',
    result: '',
    nextPlan: '',
    nextFollowUpDate: row.nextFollowUpDate || '',
    importantFlag: 0
  })
  followUpFileList.value = []
  uploadedUrls.value = []
  followUpVisible.value = true
}

// 行内"更多"下拉分发：状态/分配/转化/放弃/删除
const handleRowCommand = (cmd, row) => {
  if (cmd === 'status' || cmd === 'assign') {
    quickActionType.value = cmd
    quickActionRow.value = row
    quickActionValue.value = cmd === 'status' ? (row.leadStatus || '') : null
    quickActionVisible.value = true
  } else if (cmd === 'convert') {
    handleConvert(row)
  } else if (cmd === 'abandon') {
    handleAbandon(row)
  } else if (cmd === 'delete') {
    handleDelete(row)
  }
}

// 线索转化为客户
const handleConvert = async (row) => {
  try {
    await ElMessageBox.confirm(
      `${tt("确认将线索【")}${row.leadName}${tt("】转化为客户？转化后线索状态将变为\"已转化\"。")}`,
      tt('转化为客户'),
      { type: 'warning', confirmButtonText: tt('确认转化'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await convertLead(row.leadId)
    ElMessage.success(tt('转化成功'))
    loadData()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

// 放弃线索（需填写原因）
const handleAbandon = async (row) => {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(tt('请输入放弃原因'), tt('放弃线索'), {
      confirmButtonText: tt('确认放弃'),
      cancelButtonText: tt('取消'),
      inputPlaceholder: tt('如：客户明确拒绝、长期无回应等'),
      inputValidator: (v) => (v && v.trim()) ? true : '放弃原因不能为空'
    })
    reason = value
  } catch (e) {
    return
  }
  try {
    await abandonLead(row.leadId, reason)
    ElMessage.success(tt('已放弃该线索'))
    loadData()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

// 删除线索
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `${tt("确认删除线索【")}${row.leadName}${tt("】？删除后不可恢复。")}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await deleteLead(row.leadId)
    ElMessage.success(tt('删除成功'))
    loadData()
    loadDashboard()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

// 外键下拉选项
const employeeOptions = ref([])
const customerOptions = ref([])
async function loadDropdownOptions() {
  try {
    const empList = await listAllEmployee()
    employeeOptions.value = (Array.isArray(empList) ? empList : (empList?.records || []))
      .map(e => ({ value: e.employeeId, label: e.employeeName + (e.deptName ? ' (' + e.deptName + ')' : '') }))
  } catch (e) { console.warn('加载员工失败', e) }
  try {
    // 客户下拉只取"我的私海客户"（pool_type=0），后端按数据权限过滤 owner_user_id
    const custResp = await listCrmCustomer({ poolType: 0, page: 1, size: 9999 })
    const list = custResp?.records || []
    customerOptions.value = list.map(c => ({
      value: c.customerId,
      label: c.customerName || c.shortName || '',
      shortName: c.shortName || '',
      contactPerson: c.contactPerson,
      contactPhone: c.contactPhone,
      industry: c.industry
    }))
  } catch (e) { console.warn('加载私海客户失败', e) }
}

// 选择客户后自动带出联系人、电话、行业
function onCompanyChange(companyName) {
  if (!companyName) return
  const opt = customerOptions.value.find(c => c.label === companyName)
  if (opt) {
    if (opt.contactPerson && !formData.contactPerson) formData.contactPerson = opt.contactPerson
    if (opt.contactPhone && !formData.contactPhone) formData.contactPhone = opt.contactPhone
    if (opt.industry && !formData.industry) formData.industry = opt.industry
  }
}

onMounted(() => {
  loadData()
  loadDashboard()
  loadDropdownOptions()
})
useAutoRefresh(() => {
  loadData()
  loadDashboard()
  loadDropdownOptions()
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
  cursor: pointer; transition: all 0.2s;
}
.stat-item:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.1); transform: translateY(-2px); }
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
.dist-tag { margin: 0; }
.dist-tag-clickable {
  cursor: pointer;
  transition: all 0.2s;
  &:hover { transform: translateY(-2px); box-shadow: 0 2px 8px rgba(0,0,0,0.15); }
}
.filter-bar {
  display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
}
/* 操作列按钮对齐：el-dropdown 内的按钮与同行 el-button link 基线对齐 */
:deep(.el-table__cell .el-dropdown) {
  vertical-align: baseline;
  display: inline-flex;
  align-items: center;
}
:deep(.el-table__cell .el-button + .el-dropdown) {
  margin-left: 12px;
}
:deep(.el-table__cell .el-dropdown .el-button) {
  vertical-align: baseline;
}
.empty-tip { text-align: center; padding: 40px 0; color: #909399; font-size: 14px; }
.lead-detail-images {
  margin-top: 12px; display: flex; align-items: center; flex-wrap: wrap; gap: 8px;
  .lead-detail-images-label { font-size: 13px; color: #606266; }
  .lead-detail-img { width: 80px; height: 80px; border-radius: 4px; border: 1px solid #ebeef5; }
}
.follow-up-timeline { max-height: 500px; overflow-y: auto; padding: 10px 0; }
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
.timeline-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.follow-date { font-weight: 600; color: #303133; font-size: 14px; }
.follow-type { font-size: 12px; color: #409eff; background: #ecf5ff; padding: 2px 8px; border-radius: 10px; }
.important-badge { font-size: 12px; color: #fff; background: #f56c6c; padding: 2px 8px; border-radius: 10px; }
.follow-content { color: #606266; font-size: 14px; line-height: 1.6; margin-bottom: 6px; white-space: pre-wrap; }
.follow-result, .follow-next { color: #909399; font-size: 13px; margin-bottom: 4px; }
.follow-attachments { margin-top: 8px; padding-top: 8px; border-top: 1px dashed #dcdfe6; }
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
