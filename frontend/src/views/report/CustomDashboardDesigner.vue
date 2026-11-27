<template>
  <div class="custom-dashboard-designer">
    <!-- 顶部标题栏 -->
    <div class="designer-header">
      <div class="header-left">
        <el-button size="small" @click="goBack"><el-icon><Back /></el-icon> {{ $tt("返回") }}</el-button>
        <span class="title-text">{{ isEdit ? '编辑仪表盘' : '新建仪表盘' }}</span>
        <el-tag v-if="dashCode" size="small" type="info">{{ dashCode }}</el-tag>
      </div>
      <div class="header-right">
        <el-button type="primary" :loading="saving" @click="handleSave">
          <el-icon><Check /></el-icon> {{ isEdit ? '保存' : '保存仪表盘' }}
        </el-button>
      </div>
    </div>

    <div class="designer-body">
      <!-- ========== 1. 基本信息 ========== -->
      <div class="section">
        <div class="section-title"><span class="step-no">1</span> {{ $tt("基本信息") }}</div>
        <div class="section-content">
          <el-form label-width="90px" label-position="right">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item :label="$tf('仪表盘名称')" required>
                  <el-input v-model="form.dashName" :placeholder="$tt('如：销售经营看板')" maxlength="100" show-word-limit />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item :label="$tf('备注')">
                  <el-input v-model="form.remark" :placeholder="$tt('可选')" maxlength="200" />
                </el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item :label="$tf('状态')">
                  <el-radio-group v-model="form.status">
                    <el-radio value="启用">{{ $tt("启用") }}</el-radio>
                    <el-radio value="停用">{{ $tt("停用") }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <!-- ========== 2. 报表卡片 ========== -->
      <div class="section">
        <div class="section-title"><span class="step-no">2</span> 报表卡片（{{ cards.length }}/{{ MAX_CARDS }})
          <span class="title-tip">{{ $tt("组合多个自定义报表到一页网格展示，汇总模式报表自动渲染图表，明细模式报表展示前 10 行迷你表格") }}</span>
        </div>
        <div class="section-content">
          <!-- 添加卡片 -->
          <div class="card-add-row" v-if="cards.length < MAX_CARDS">
            <el-select
              v-model="newCardReport"
              filterable
              :placeholder="$tt('选择报表（仅本人/共享可见的报表）')"
              style="width: 380px"
            >
              <el-option
                v-for="r in availableReports"
                :key="r.reportId"
                :label="`${r.reportName}（${r.status === '启用' ? '启用' : '停用'}）`"
                :value="r.reportId"
              />
            </el-select>
            <el-input v-model="newCardTitle" :placeholder="$tt('卡片标题（可选，缺省用报表名）')" maxlength="30" style="width: 240px" />
            <el-button type="primary" plain :disabled="!newCardReport" @click="addCard">
              <el-icon><Plus /></el-icon> 添加卡片
            </el-button>
          </div>
          <el-empty v-if="!cards.length" :description="$tt('请至少添加一张报表卡片')" :image-size="60" />

          <!-- 已选卡片列表 -->
          <div v-for="(c, idx) in cards" :key="c.reportId" class="card-item">
            <span class="card-index">{{ idx + 1 }}</span>
            <span class="card-name">{{ reportNameOf(c.reportId) }}</span>
            <el-tag size="small" :type="reportModeOf(c.reportId) === 'summary' ? 'warning' : 'info'">
              {{ reportModeOf(c.reportId) === 'summary' ? '汇总' : '明细' }}
            </el-tag>
            <el-input v-model="c.title" :placeholder="$tt('覆盖标题（可选）')" maxlength="30" style="width: 220px" />
            <span class="card-actions">
              <el-button size="small" link :disabled="idx === 0" @click="moveCard(idx, -1)">
                <el-icon><Top /></el-icon>
              </el-button>
              <el-button size="small" link :disabled="idx === cards.length - 1" @click="moveCard(idx, 1)">
                <el-icon><Bottom /></el-icon>
              </el-button>
              <el-button size="small" link type="danger" @click="cards.splice(idx, 1)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </span>
          </div>
          <div class="card-tip" v-if="cards.length">{{ $tt("卡片顺序即展示顺序；同一报表不可重复添加") }}</div>
        </div>
      </div>

      <!-- ========== 3. 共享设置 ========== -->
      <div class="section">
        <div class="section-title"><span class="step-no">3</span> 共享设置
          <span class="title-tip">{{ $tt("共享后，所选角色下的用户也能查看该仪表盘（卡片数据仍按报表自身权限双重校验）") }}</span>
        </div>
        <div class="section-content">
          <el-form label-width="90px" label-position="right">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item :label="$tf('共享范围')">
                  <el-radio-group v-model="form.shareScope">
                    <el-radio value="私有">{{ $tt("私有（仅自己可见）") }}</el-radio>
                    <el-radio value="共享">{{ $tt("共享") }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="10">
                <el-form-item :label="$tf('共享角色')" v-if="form.shareScope === '共享'">
                  <el-select v-model="shareRoleCodes" multiple filterable :placeholder="$tt('选择可查看的角色')" style="width: 100%">
                    <el-option v-for="r in roleOptions" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, Check, Plus, Top, Bottom, Delete } from '@element-plus/icons-vue'
import { getDashboard, createDashboard, updateDashboard } from '@/api/report/customDashboard'
import { listCustomReports } from '@/api/report/customReport'
import { listAllRole } from '@/api/system/role'
import { tt } from '@/i18n'

const MAX_CARDS = 8

const router = useRouter()
const route = useRoute()
const dashId = computed(() => route.query.id ? Number(route.query.id) : null)
const isEdit = computed(() => !!dashId.value)

// ========== 基本信息 ==========
const form = reactive({
  dashName: '',
  remark: '',
  status: '启用',
  shareScope: '私有'
})
const dashCode = ref('')
const shareRoleCodes = ref([])
const saving = ref(false)

// ========== 卡片 ==========
const cards = ref([]) // [{reportId, title}]
const reportOptions = ref([]) // 可见报表选项（含模式/名称信息）
const newCardReport = ref(null)
const newCardTitle = ref('')

/** 未被使用的报表才可继续添加 */
const availableReports = computed(() =>
  reportOptions.value.filter(r => !cards.value.some(c => c.reportId === r.reportId))
)

function reportNameOf(id) {
  return reportOptions.value.find(r => r.reportId === id)?.reportName || `报表 ${id}`
}

function reportModeOf(id) {
  return reportOptions.value.find(r => r.reportId === id)?.mode || ''
}

function addCard() {
  if (!newCardReport.value) return
  if (cards.value.some(c => c.reportId === newCardReport.value)) {
    ElMessage.warning(tt('该报表已添加，不可重复'))
    return
  }
  cards.value.push({ reportId: newCardReport.value, title: newCardTitle.value.trim() })
  newCardReport.value = null
  newCardTitle.value = ''
}

function moveCard(idx, delta) {
  const target = idx + delta
  if (target < 0 || target >= cards.value.length) return
  const tmp = cards.value[idx]
  cards.value[idx] = cards.value[target]
  cards.value[target] = tmp
}

// ========== 加载 ==========
async function loadReportOptions() {
  try {
    const res = await listCustomReports({ page: 1, size: 100 })
    reportOptions.value = (res?.records || []).map(r => ({
      reportId: r.reportId,
      reportName: r.reportName,
      status: r.status,
      mode: r.mode || (r.configJson ? 'detail' : 'detail')
    }))
  } catch (e) {
    reportOptions.value = []
  }
}

const roleOptions = ref([])
async function loadRoles() {
  try {
    roleOptions.value = (await listAllRole()) || []
  } catch (e) {
    roleOptions.value = []
  }
}

async function loadForEdit() {
  if (!dashId.value) return
  try {
    const detail = await getDashboard(dashId.value)
    form.dashName = detail.dashName || ''
    form.remark = detail.remark || ''
    form.status = detail.status || '启用'
    form.shareScope = detail.shareScope || '私有'
    dashCode.value = detail.dashCode || ''
    shareRoleCodes.value = detail.shareRoles ? detail.shareRoles.split(',').map(s => s.trim()).filter(Boolean) : []
    // 后端 detail.cards 已解析报表信息（reportId/title/reportName/mode），补进选项映射
    ;(detail.cards || []).forEach(c => {
      if (c.reportId && !reportOptions.value.some(r => r.reportId === c.reportId)) {
        reportOptions.value.push({ reportId: c.reportId, reportName: c.reportName || `报表 ${c.reportId}`, status: c.reportStatus || '启用', mode: c.mode || '' })
      }
    })
    cards.value = (detail.cards || []).map(c => ({
      reportId: c.reportId,
      title: c.title && c.title !== c.reportName ? c.title : ''
    }))
  } catch (e) {
    ElMessage.error(e.message || tt('仪表盘加载失败'))
  }
}

// ========== 保存 ==========
function validate() {
  if (!form.dashName || !form.dashName.trim()) {
    ElMessage.warning(tt('请输入仪表盘名称'))
    return false
  }
  if (!cards.value.length) {
    ElMessage.warning(tt('请至少添加一张报表卡片'))
    return false
  }
  return true
}

async function handleSave() {
  if (!validate()) return
  saving.value = true
  try {
    const payload = {
      dashName: form.dashName.trim(),
      layoutJson: JSON.stringify({
        cards: cards.value.map(c => {
          const card = { reportId: c.reportId }
          if (c.title?.trim()) card.title = c.title.trim()
          return card
        })
      }),
      status: form.status,
      shareScope: form.shareScope,
      shareRoles: form.shareScope === '共享' ? shareRoleCodes.value.join(',') : null,
      remark: form.remark || null
    }
    if (isEdit.value) {
      await updateDashboard(dashId.value, payload)
      ElMessage.success(tt('保存成功'))
    } else {
      const created = await createDashboard(payload)
      ElMessage.success(tt('创建成功'))
      router.replace({ path: '/report/dashboard/designer', query: { id: created.dashId } })
      dashCode.value = created.dashCode || ''
      return
    }
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/report/dashboard')
}

onMounted(async () => {
  await loadReportOptions()
  await loadRoles()
  await loadForEdit()
})
</script>

<style scoped lang="scss">
.custom-dashboard-designer {
  background: #f5f7fa;
  min-height: calc(100vh - 90px);
  display: flex;
  flex-direction: column;
}
.designer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 10px 16px;
  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
    .title-text { font-size: 16px; font-weight: 600; color: #303133; }
  }
  .header-right { display: flex; gap: 8px; }
}
.designer-body {
  flex: 1;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: auto;
}
.section {
  background: #fff;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  .section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    border-bottom: 1px solid #f0f0f0;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    .step-no {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #409eff;
      color: #fff;
      font-size: 12px;
    }
    .title-tip { font-size: 12px; font-weight: 400; color: #909399; }
  }
  .section-content { padding: 14px; }
}
.card-add-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.card-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  font-size: 13px;
  border: 1px dashed #ebeef5;
  border-radius: 4px;
  margin-bottom: 8px;
  &:hover { background: #fafcff; }
  .card-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #409eff;
    color: #fff;
    font-size: 11px;
    flex-shrink: 0;
  }
  .card-name { color: #303133; min-width: 160px; }
  .card-actions { display: flex; gap: 2px; margin-left: auto; }
}
.card-tip { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
