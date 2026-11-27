<template>
  <div class="kgw-import-progress">
    <div class="kgw-toolbar">
      <div class="kgw-tabs">
        <div class="kgw-tab active">{{ $tt("导入进度") }}</div>
      </div>
      <div class="kgw-actions">
        <el-button v-permission="perm('import')" type="primary" size="small">{{ $tt("开始导入") }}</el-button>
        <el-button v-permission="perm('clear')" size="small">{{ $tt("清空已完成") }}</el-button>
      </div>
    </div>
    <div class="kgw-body">
      <el-table :data="tasks" border size="small" v-loading="loading">
        <el-table-column prop="name"      :label="$tf('任务名称')" width="200" sortable>
          <template #default="{ row }">
            <el-link type="primary" @click="onViewError(row)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="type"      :label="$tf('导入类型')" width="120" sortable />
        <el-table-column                :label="$tf('进度')"     width="240">
          <template #default="{ row }">
            <el-progress :percentage="row.percent" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="total"     :label="$tf('总条数')"   width="100" sortable />
        <el-table-column prop="success"   :label="$tf('成功')"     width="100" sortable />
        <el-table-column prop="failed"    :label="$tf('失败')"     width="100" sortable />
        <el-table-column prop="createBy"  :label="$tf('操作人')"   width="120" sortable />
        <el-table-column prop="startTime" :label="$tf('开始时间')" width="180" sortable />
        <el-table-column prop="endTime"   :label="$tf('结束时间')" width="180" sortable />
        <el-table-column                :label="$tf('状态')"     width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.tag" size="small">{{ $dt(statusMap[row.status]?.label) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:sys-param')
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listImportTask } from '@/api/system/sysImportTask'
import { tt } from '@/i18n'

const loading = ref(false)
const tasks = ref([])
const statusMap = {
  pending:   { label: '等待中', tag: 'info' },
  running:   { label: '进行中', tag: 'warning' },
  success:   { label: '已完成', tag: 'success' },
  failed:    { label: '失败',   tag: 'danger' },
  partial:   { label: '部分失败', tag: 'warning' }
}
let timer = null

async function loadList() {
  loading.value = true
  try {
    const res = await listImportTask()
    tasks.value = (res || []).map(t => ({ ...t, percent: t.total ? Math.round(t.success / t.total * 100) : 0 }))
  } catch (e) { tasks.value = [] }
  finally { loading.value = false }
}

function onViewError(row) { ElMessage.info(tt('查看错误: ') + row.name) }
function onCancel(row)    { ElMessage.warning(tt('已取消: ') + row.name) }

onMounted(() => {
  loadList()
  timer = setInterval(loadList, 5000) // 每5秒刷新
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped lang="scss">
@import './_kgw-common.scss';
</style>
