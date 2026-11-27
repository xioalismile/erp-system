<template>
  <div class="kgw-download-log">
    <div class="kgw-toolbar">
      <div class="kgw-tabs">
        <div class="kgw-tab active">{{ $tt("下载日志") }}</div>
      </div>
      <div class="kgw-actions">
        <el-button v-permission="perm('query')" type="primary" size="small" @click="onQuery">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('clear')" type="danger" size="small" @click="onClear">{{ $tt("清空日志") }}</el-button>
      </div>
    </div>
    <div class="kgw-body">
      <el-form :inline="true" :model="query" size="small">
        <el-form-item :label="$tf('操作人')"><el-input v-model="query.operator" clearable style="width:140px" /></el-form-item>
        <el-form-item :label="$tf('文件类型')">
          <el-select v-model="query.fileType" clearable style="width:140px">
            <el-option label="Excel" value="excel" />
            <el-option label="PDF" value="pdf" />
            <el-option :label="$tf('图片')" value="image" />
            <el-option :label="$tf('压缩包')" value="zip" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$tf('日期')">
          <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD" style="width:240px" />
        </el-form-item>
      </el-form>
      <el-table :data="logs" border size="small" v-loading="loading" style="margin-top:12px" @sort-change="onSortChange">
        <el-table-column prop="time"     :label="$tf('下载时间')"  width="180" sortable="custom" />
        <el-table-column prop="operator" :label="$tf('操作人')"    width="120" sortable="custom" />
        <el-table-column prop="fileName" :label="$tf('文件名')" sortable="custom">
          <template #default="{ row }">
            <el-link type="primary" @click="onReDownload(row)">{{ row.fileName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" :label="$tf('类型')"      width="100" sortable="custom" />
        <el-table-column prop="fileSize" :label="$tf('大小')"      width="120" sortable="custom" />
        <el-table-column prop="ip"       label="IP"        width="140" sortable="custom" />
      </el-table>
      <el-pagination
        style="margin-top: 12px; text-align: right;"
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :page-sizes="[20, 50, 100]"
        :total="page.total"
        layout="total, sizes, prev, pager, next, jumper"
      />
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:operate-log')
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageDownloadLog, clearDownloadLog } from '@/api/system/sysDownloadLog'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadList() }
const loading = ref(false)
const query = reactive({ operator: '', fileType: '', dateRange: [] })
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams()})
const logs = ref([])

async function loadList() {
  loading.value = true
  try {
    const params = { ...query, current: page.current, size: page.size, ...getSortParams() }
    if (query.dateRange && query.dateRange.length === 2) {
      params.startDate = query.dateRange[0]; params.endDate = query.dateRange[1]
    }
    delete params.dateRange
    const res = await pageDownloadLog(params)
    logs.value = res.records || []
    page.total = res.total || 0
  } catch (e) { logs.value = [] }
  finally { loading.value = false }
}

function onQuery() { page.current = 1; loadList() }
async function onClear() {
  await ElMessageBox.confirm(tt('确认清空所有下载日志？'), tt('提示'), { type: 'warning' })
  try { await clearDownloadLog(); ElMessage.success(tt('已清空')); loadList() }
  catch (e) { ElMessage.error(tt('清空失败')) }
}
function onReDownload(row) { ElMessage.success(tt('重新下载: ') + row.fileName) }

onMounted(() => loadList())
</script>

<style scoped lang="scss">
@import './_kgw-common.scss';
</style>
