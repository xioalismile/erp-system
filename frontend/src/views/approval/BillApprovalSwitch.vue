<template>
  <div class="bill-approval-switch-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">{{ $tt("单据审批开关") }}</span>
            <el-tag type="info" size="small">{{ $tt("单独控制每种单据是否需要审批") }}</el-tag>
          </div>
          <el-button :icon="Refresh" v-permission="perm('refresh')" @click="loadData">{{ $tt("刷新") }}</el-button>
        </div>
      </template>

      <div class="global-switch-bar">
        <span class="label">{{ $tt("全局审批功能:") }}</span>
        <el-switch v-model="globalEnabled" @change="saveGlobalEnabled" :active-text="$tt('启用')" :inactive-text="$tt('停用')" />
        <span class="tip" v-if="!globalEnabled" style="color:#f56c6c;">
          注意：全局审批已停用，所有单据都不会进入审批流程
        </span>
      </div>

      <el-table :data="billTypes" v-loading="loading" border stripe style="width: 100%; margin-top: 16px;">
        <el-table-column :label="$tf('单据类型')" width="180">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.billType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="billName" :label="$tf('单据名称')" min-width="180" sortable />
        <el-table-column :label="$tf('启用审批')" width="120" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="handleToggle(row)" :loading="row._loading" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('状态')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '需要审批' : '免审批' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('说明')">
          <template #default="{ row }">
            <span class="desc">{{ row.enabled ? '该单据创建后需提交审批才能生效' : '该单据创建后直接生效，无需审批' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="tip-bar">
        <el-alert type="info" :closable="false">
          <template #title>
            <span><b>{{ $tt("使用说明：") }}</b></span>
          </template>
          <ul style="margin: 4px 0 0 16px; line-height: 1.8;">
            <li>{{ $tt("开启某单据的审批开关后，需要到") }} <b>{{ $tt("审批流程管理") }}</b> {{ $tt("中配置对应的审批流程") }}</li>
            <li>{{ $tt("若未配置审批流程，单据提交时会提示错误") }}</li>
            <li>{{ $tt("关闭审批后，单据创建即生效，不再需要审核") }}</li>
            <li>{{ $tt("全局审批开关停用时，所有单据级开关都不生效") }}</li>
          </ul>
        </el-alert>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('system:approval')

const loading = ref(false)
const billTypes = ref([])
const globalEnabled = ref(true)

const api = {
  listBillTypes: () => request.get('/approval/bill-types'),
  setEnabled: (billType, enabled) => request.put(`/approval/bill-enabled/${billType}`, null, { params: { enabled } }),
  getGlobal: () => request.get('/sys-config/approval.enabled'),
  saveGlobal: (enabled) => request.put('/sys-config/approval.enabled', { configValue: String(enabled) })
}

async function loadData() {
  loading.value = true
  try {
    const res = await api.listBillTypes()
    // 注意：request 响应拦截器已把 {code,message,data} 解包，res 就是业务数据本身（列表）
    billTypes.value = (Array.isArray(res) ? res : []).map(b => ({ ...b, _loading: false }))
    const g = await api.getGlobal()
    const v = g?.configValue
    globalEnabled.value = v === 'true' || v === true || v === undefined
  } catch (e) {
    ElMessage.error(tt('加载失败'))
  } finally {
    loading.value = false
  }
}

async function handleToggle(row) {
  row._loading = true
  try {
    await api.setEnabled(row.billType, row.enabled)
    ElMessage.success(`${row.billName}${tt("审批已")}${row.enabled ? tt('启用') : tt('停用')}`)
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error(tt('更新失败'))
  } finally {
    row._loading = false
  }
}

async function saveGlobalEnabled(val) {
  try {
    await api.saveGlobal(val)
    ElMessage.success(`${tt("全局审批已")}${val ? tt('启用') : tt('停用')}`)
  } catch (e) {
    globalEnabled.value = !val
    ElMessage.error(tt('保存失败'))
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.bill-approval-switch-page { padding: 0; }
.card-header {
  display: flex; justify-content: space-between; align-items: center;
  .header-left { display: flex; align-items: center; gap: 12px; }
  .title { font-size: 16px; font-weight: 600; color: #303133; }
}
.global-switch-bar {
  display: flex; align-items: center; gap: 12px;
  background: #f5f7fa; padding: 12px 16px; border-radius: 6px;
  border: 1px solid #ebeef5;
  .label { font-size: 14px; color: #303133; font-weight: 600; }
  .tip { font-size: 12px; }
}
.desc { font-size: 12px; color: #909399; }
.tip-bar { margin-top: 16px; }
</style>
