<template>
  <el-dialog
    v-model="visible"
    :title="title || '操作日志'"
    width="900px"
    :close-on-click-modal="false"
    class="change-record-dialog"
    @close="onClose"
  >
    <div class="cr-toolbar">
      <span class="cr-bill-info">
        <el-tag v-if="billNo" type="info" size="small">单号：{{ billNo }}</el-tag>
      </span>
      <div class="cr-filter">
        <el-select v-model="filterType" :placeholder="$tt('操作类型')" size="small" clearable style="width: 120px" @change="loadList">
          <el-option :label="$tf('新增')" value="新增" />
          <el-option :label="$tf('修改')" value="修改" />
          <el-option :label="$tf('删除')" value="删除" />
          <el-option :label="$tf('审核')" value="审核" />
          <el-option :label="$tf('重置草稿')" value="重置草稿" />
        </el-select>
        <el-button type="primary" size="small" v-permission="perm('refresh')" @click="loadList">{{ $tt("刷新") }}</el-button>
      </div>
    </div>

    <div class="cr-list" v-loading="loading">
      <div v-if="list.length === 0 && !loading" class="cr-empty">
        <el-empty :description="$tt('暂无操作日志')" :image-size="60" />
      </div>
      <div
        v-for="item in list"
        :key="item.logId"
        class="cr-item"
        :class="{ 'cr-item-expanded': expandedLogId === item.logId }"
      >
        <div class="cr-item-header" @click="toggleExpand(item)">
          <div class="cr-item-left">
            <el-tag :type="tagType(item.operateType)" size="small" effect="dark">{{ $dt(item.operateType || '操作') }}</el-tag>
            <el-tag v-if="item.operateResult === '失败'" type="danger" size="small" effect="plain">{{ $tt('失败') }}</el-tag>
            <span class="cr-item-user" :title="item.userName || ''">{{ item.realName || item.userName || '-' }}</span>
            <span class="cr-item-time">{{ formatTime(item.operateTime) }}</span>
          </div>
          <div class="cr-item-right">
            <span v-if="item.operateSummary" class="cr-item-summary">{{ item.operateSummary }}</span>
            <el-icon class="cr-item-arrow" :class="{ 'is-expanded': expandedLogId === item.logId }">
              <ArrowDown />
            </el-icon>
          </div>
        </div>
        <div v-if="expandedLogId === item.logId" class="cr-item-detail">
          <div v-if="item.failReason" class="cr-fail-reason" style="display:flex;align-items:flex-start;gap:6px;margin-bottom:8px;padding:8px 10px;background:#fef0f0;border:1px solid #fde2e2;border-radius:4px;color:#f56c6c;font-size:13px;line-height:1.5;">
            <el-icon style="margin-top:2px;"><InfoFilled /></el-icon>
            <span>{{ $tt('失败原因：') }}{{ item.failReason }}</span>
          </div>
          <div v-if="(detailMap[item.logId] || []).length > 0" class="cr-diff-table">
            <table class="diff-table">
              <thead>
                <tr>
                  <th width="26%">{{ $tt("字段") }}</th>
                  <th width="28%">{{ $tt("修改前") }}</th>
                  <th width="28%">{{ $tt("修改后") }}</th>
                  <!-- 2026-10-01 新增：每条变更都能直接看到是谁改的 -->
                  <th width="18%">{{ $tt("修改人") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="d in detailMap[item.logId]" :key="d.detailId" class="diff-row">
                  <td class="diff-field">{{ d.fieldDesc || d.fieldName }}</td>
                  <td class="diff-old">
                    <span v-if="d.oldValue !== null && d.oldValue !== undefined && d.oldValue !== ''">{{ d.oldValue }}</span>
                    <span v-else class="diff-empty">{{ $tt("（空）") }}</span>
                  </td>
                  <td class="diff-new">
                    <span v-if="d.newValue !== null && d.newValue !== undefined && d.newValue !== ''">{{ d.newValue }}</span>
                    <span v-else class="diff-empty">{{ $tt("（空）") }}</span>
                  </td>
                  <td class="diff-user" :title="item.userName || ''">{{ item.realName || item.userName || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else-if="detailMap[item.logId] && detailMap[item.logId].length === 0" class="cr-no-detail">
            <el-icon><InfoFilled /></el-icon>
            <span>{{ $tt("该操作无字段级变更明细") }}</span>
          </div>
          <div v-else class="cr-no-detail">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>{{ $tt("加载中...") }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="cr-pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, prev, pager, next, jumper"
        small
        background
        @current-change="loadList"
        @size-change="loadList"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ArrowDown, InfoFilled, Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('sale:orders')

const props = defineProps({
  modelValue: Boolean,
  billType: { type: String, default: '' },
  billNo: { type: String, default: '' },
  title: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterType = ref('')
const expandedLogId = ref(null)
const detailMap = ref({})

function tagType(type) {
  if (!type) return 'info'
  if (type.includes('新增') || type.includes('创建')) return 'success'
  if (type.includes('修改') || type.includes('编辑')) return 'warning'
  if (type.includes('删除')) return 'danger'
  if (type.includes('审核')) return 'primary'
  if (type.includes('重置草稿')) return 'info'
  return 'info'
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 19)
}

async function loadList() {
  if (!props.billType || !props.billNo) return
  loading.value = true
  try {
    const params = { current: page.value, size: pageSize.value }
    if (filterType.value) params.operateType = filterType.value
    const res = await request.get(`/system/log/bill/${props.billType}/${props.billNo}`, { params })
    list.value = res.records || res || []
    total.value = res.total || list.value.length
    // 自动展开第一条日志，方便用户立即查看字段级变更明细
    if (list.value.length > 0 && list.value[0].logId != null) {
      expandedLogId.value = list.value[0].logId
      // 预加载第一条的明细数据
      const firstId = list.value[0].logId
      if (!detailMap.value[firstId]) {
        try {
          const detailRes = await request.get(`/system/log/detail/${firstId}`)
          detailMap.value[firstId] = detailRes || []
        } catch (e) {
          detailMap.value[firstId] = []
        }
      }
    }
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function toggleExpand(item) {
  if (expandedLogId.value === item.logId) {
    expandedLogId.value = null
    return
  }
  expandedLogId.value = item.logId
  if (!detailMap.value[item.logId]) {
    try {
      const res = await request.get(`/system/log/detail/${item.logId}`)
      detailMap.value[item.logId] = res || []
    } catch (e) {
      detailMap.value[item.logId] = []
    }
  }
}

function onClose() {
  expandedLogId.value = null
}

watch(() => [props.modelValue, props.billType, props.billNo], ([v]) => {
  if (v) {
    page.value = 1
    detailMap.value = {}
    loadList()
  }
}, { immediate: true })
</script>

<style scoped lang="scss">
.change-record-dialog {
  :deep(.el-dialog__body) {
    padding: 12px 20px;
  }
}
.cr-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}
.cr-filter {
  display: flex;
  gap: 8px;
  align-items: center;
}
.cr-list {
  max-height: 500px;
  overflow-y: auto;
}
.cr-empty {
  padding: 40px 0;
}
.cr-item {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all .2s;
  &:hover {
    border-color: #91d5ff;
  }
  &.cr-item-expanded {
    border-color: #1890ff;
  }
}
.cr-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #fafafa;
}
.cr-item-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cr-item-user {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}
.cr-item-time {
  font-size: 12px;
  color: #909399;
}
.cr-item-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cr-item-summary {
  font-size: 12px;
  color: #606266;
}
.cr-item-arrow {
  color: #909399;
  font-size: 12px;
  transition: transform .2s;
  &.is-expanded {
    transform: rotate(180deg);
  }
}
.cr-item-detail {
  padding: 12px 14px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
}
.cr-diff-table {
  width: 100%;
}
.diff-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  th, td {
    border: 1px solid #ebeef5;
    padding: 6px 10px;
    text-align: left;
    vertical-align: top;
  }
  thead th {
    background: #f5f7fa;
    font-weight: 600;
    color: #303133;
    text-align: center;
  }
}
.diff-row {
  .diff-field {
    color: #606266;
    font-weight: 500;
    background: #fafcff;
  }
  .diff-old {
    color: #f56c6c;
    background: #fef0f0;
    text-decoration: line-through;
  }
  .diff-new {
    color: #67c23a;
    background: #f0f9eb;
    font-weight: 500;
  }
  /* 修改人：中性色，与"改前/改后"的红绿区分开，避免抢视觉 */
  .diff-user {
    color: #606266;
    background: #fff;
  }
}
.diff-empty {
  color: #c0c4cc;
  font-style: italic;
}
.cr-no-detail {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 16px;
  color: #909399;
  font-size: 13px;
}
.cr-pagination {
  display: flex;
  justify-content: center;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
  margin-top: 8px;
}
</style>
