<template>
  <teleport to="body">
    <div v-if="modelValue" class="ss-backdrop" @click="close"></div>
    <div v-if="modelValue" class="ss-panel">
      <div class="ss-header">
        <span class="ss-title">{{ $tt("站点设置") }}</span>
        <el-icon class="ss-close" @click="close"><Close /></el-icon>
      </div>
      <div class="ss-toolbar">
        <el-checkbox :model-value="ssAllVisible" :indeterminate="ssSomeVisible" @change="ssSelectAll">{{ $tt("全选") }}</el-checkbox>
        <el-button text type="primary" @click="ssInvert">{{ $tt("反选") }}</el-button>
      </div>
      <el-divider class="ss-divider" />
      <div class="ss-list">
        <draggable v-model="ssLocal" item-key="prop" handle=".ss-drag" animation="200" ghost-class="ss-ghost">
          <template #item="{ element }">
            <div class="ss-row">
              <el-icon class="ss-drag"><Rank /></el-icon>
              <el-checkbox v-model="element.visible" @change="ssOnVisibleChange(element)">{{ $tf(element.label) }}</el-checkbox>
              <span class="ss-group">{{ $tf(element.group) }}</span>
            </div>
          </template>
        </draggable>
        <div v-if="!ssLocal.length" class="ss-empty">{{ $tt("暂无可配置字段") }}</div>
      </div>
      <div class="ss-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ tip ? $tt(tip) : $tt("拖动调整顺序；勾选控制在工序站点上显示哪些字段（一行 2 个，值为空不占位）") }}</span>
      </div>
      <div class="ss-footer">
        <el-button @click="ssReset">{{ $tt("重置默认") }}</el-button>
        <el-button @click="close">{{ $tt("取消") }}</el-button>
        <el-button type="primary" @click="ssSave">{{ $tt("保存") }}</el-button>
      </div>
    </div>
  </teleport>
</template>

<script setup>
/**
 * 站点设置面板（两个看板页共用一套）：勾选显示/隐藏 + 拖动排序，
 * 按「登录人 + 所属公司」隔离保存（复用 columnConfig store → userScopedStorage）。
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Close, Rank, InfoFilled } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import { useColumnConfigStore } from '@/stores/columnConfig'
import { tt } from '@/i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 候选字段定义 [{ prop, label, group, defaultHidden }] */
  fields: { type: Array, default: () => [] },
  /** 持久化 pageKey（两页共用同一份） */
  pageKey: { type: String, required: true },
  /** 面板底部提示文案（不传 = 站点字段口径；车间进度跟踪页传「悬停浮层字段」口径） */
  tip: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const columnStore = useColumnConfigStore()
const ssLocal = ref([])

function initSsLocal () {
  const cfg = columnStore.getPageConfig(props.pageKey)
  ssLocal.value = props.fields.map((f, idx) => {
    const u = cfg[f.prop] || {}
    return {
      prop: f.prop,
      label: f.label,
      group: f.group,
      visible: u.visible !== undefined ? u.visible : !f.defaultHidden,
      order: u.order !== undefined ? u.order : idx
    }
  }).sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
}

watch(() => props.modelValue, (v) => { if (v) initSsLocal() })

function close () { emit('update:modelValue', false) }

const ssAllVisible = computed({
  get: () => ssLocal.value.length > 0 && ssLocal.value.every(c => c.visible),
  set: (val) => ssLocal.value.forEach(c => { c.visible = val })
})
const ssSomeVisible = computed(() => {
  const n = ssLocal.value.filter(c => c.visible).length
  return n > 0 && n < ssLocal.value.length
})
function ssSelectAll (val) { ssAllVisible.value = val }
function ssInvert () { ssLocal.value.forEach(c => { c.visible = !c.visible }) }
function ssOnVisibleChange (element) {
  if (ssLocal.value.some(c => c.visible)) return
  element.visible = true
  ElMessage.warning(tt('至少保留 1 个字段可见'))
}
function ssSave () {
  if (!ssLocal.value.some(c => c.visible)) {
    ElMessage.warning(tt('至少保留 1 个字段可见'))
    return
  }
  const cfg = {}
  ssLocal.value.forEach((c, idx) => { cfg[c.prop] = { visible: c.visible, order: idx } })
  columnStore.setPageConfig(props.pageKey, cfg)
  ElMessage.success(tt('站点设置已保存'))
  close()
}
function ssReset () {
  columnStore.resetPage(props.pageKey)
  initSsLocal()
  ElMessage.success(tt('已重置为默认站点设置'))
}
</script>

<style scoped lang="scss">
.ss-backdrop { position: fixed; inset: 0; z-index: 2999; background: transparent; }

.ss-panel {
  position: fixed;
  right: 24px;
  top: 80px;
  z-index: 3000;
  width: 380px;
  max-height: 76vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, .16);
}

.ss-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 10px;
  border-bottom: 1px solid #f0f0f0;

  .ss-title { font-size: 15px; font-weight: 600; color: #1f2937; }
  .ss-close { cursor: pointer; font-size: 18px; color: #9ca3af; &:hover { color: #ef4444; } }
}

.ss-toolbar { display: flex; align-items: center; gap: 12px; padding: 8px 16px; }
.ss-divider { margin: 0; }

.ss-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 16px;

  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 3px; }
}

.ss-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 4px;
  border-bottom: 1px solid #f5f5f5;

  &:hover { background: #f5f7fa; }

  .ss-drag { cursor: move; color: #909399; font-size: 16px; flex-shrink: 0; }

  :deep(.el-checkbox) {
    flex: 1;
    margin-right: 0;
    .el-checkbox__label { font-size: 13px; color: #303133; }
  }

  .ss-group { font-size: 11px; color: #b0b8c4; white-space: nowrap; flex-shrink: 0; }
}

.ss-ghost { opacity: .5; background: #e6f7ff !important; }
.ss-empty { padding: 24px 0; text-align: center; font-size: 12px; color: #b0b8c4; }

.ss-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 16px;
  font-size: 12px;
  color: #909399;
  border-top: 1px solid #f0f0f0;
  .el-icon { color: #409eff; font-size: 14px; }
}

.ss-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
