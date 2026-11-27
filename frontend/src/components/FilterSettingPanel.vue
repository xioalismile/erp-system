<template>
  <Teleport to="body">
    <!-- 透明点击层：点击外部关闭（与 ColumnSettingDrawer 同一交互口径） -->
    <transition name="fs-fade">
      <div v-if="visible" class="fs-backdrop" :style="{ zIndex: panelZIndex - 1 }" @click="handleClose"></div>
    </transition>
    <transition name="fs-slide">
      <div v-if="visible" class="fs-panel" :style="{ top: panelTop + 'px', zIndex: panelZIndex }" @click.stop>
        <div class="fs-header">
          <span class="fs-title">{{ $tt(title || '筛选设置') }}</span>
          <div class="fs-header-actions">
            <el-button text type="warning" v-permission="perm('resetDefault')" @click="handleReset">{{ $tt("恢复默认") }}</el-button>
            <el-button v-permission="perm('dlgCancel')" @click="handleClose">{{ $tt("取消") }}</el-button>
            <el-button type="primary" v-permission="perm('save')" @click="handleSave">{{ $tt("保存") }}</el-button>
            <el-icon class="fs-close-btn" @click="handleClose"><Close /></el-icon>
          </div>
        </div>

        <div class="fs-search">
          <el-input v-model="keyword" :placeholder="$tt('搜索字段名（如 商品、金额、日期）')" clearable size="small">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>

        <div class="fs-toolbar">
          <el-checkbox :model-value="allChecked" :indeterminate="someChecked" @change="handleSelectAll">
            {{ $tt("全选") }}
          </el-checkbox>
          <el-button text type="primary" v-permission="perm('columnSetting')" @click="handleInvert">{{ $tt("反选") }}</el-button>
          <el-button text type="warning" v-permission="perm('resetDefault')" @click="handleReset">{{ $tt("恢复默认") }}</el-button>
          <span class="fs-count">{{ $tt('已选') }} {{ checkedCount }} / {{ localFilters.length }}</span>
        </div>
        <el-divider class="fs-divider" />

        <div class="fs-list">
          <div v-if="!shownFilters.length" class="fs-empty">{{ $tt('没有匹配的字段') }}</div>
          <draggable
            v-else
            v-model="localFilters"
            item-key="key"
            handle=".drag-handle"
            animation="200"
            ghost-class="ghost"
            chosen-class="chosen"
            drag-class="drag"
          >
            <template #item="{ element }">
              <div v-show="isShown(element)" class="fs-row">
                <el-icon class="drag-handle"><Rank /></el-icon>
                <el-checkbox v-model="element.checked" @change="handleCheckedChange(element)">
                  {{ $tf(element.label) }}
                </el-checkbox>
                <span class="fs-group-tag">{{ $tf(element.group) }}</span>
              </div>
            </template>
          </draggable>
        </div>

        <div class="fs-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ $tt("拖动") }} <el-icon><Rank /></el-icon> {{ $tt("调整查询区顺序；勾选的条件才会出现在查询区顶部") }}</span>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup>
/**
 * 筛选设置浮动面板（2026-09-22 新增，通用件）
 *
 * 与 ColumnSettingDrawer 的差异：
 *   - 只管「查询区筛选条件的显隐与顺序」，不碰表格列，也不碰 columnConfig store
 *   - 配置持久化由父页面持有的 useFilterSetting 实例负责（保存后 version 自增 → 查询区立即变化）
 *
 * 2026-09-22 增强（用户要求「全部字段都可作为筛选条件」）：
 *   - 候选条件可达 60+ 项 → 顶部加字段搜索框，行尾带分组标签（单据信息 / 商品明细 / 自定义字段）
 *   - 勾选数实时显示，方便确认"到底选中了几个"
 *
 * 用法：
 *   <FilterSettingPanel v-model="filterSettingVisible" page-key="SaleOrderDetailReport"
 *                       :filters="filterDefs" @save="saveFilterConfig" @reset="resetFilterConfig" />
 */
import { ref, computed, watch, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox, useZIndex } from 'element-plus'
import { Rank, InfoFilled, Close, Search } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import { getFilterPageConfig } from '@/composables/useFilterSetting'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 页面标识（本地配置按此维度存储，一经确定不可改） */
  pageKey: { type: String, required: true },
  /** 可选的筛选条件定义 [{ key, label, group, type, defaultChecked }] */
  filters: { type: Array, default: () => [] },
  title: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'save', 'reset'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const panelTop = ref(80)
const { nextZIndex } = useZIndex()
const panelZIndex = ref(2000)

const localFilters = ref([])
const keyword = ref('')

function initLocalFilters() {
  const cfg = getFilterPageConfig(props.pageKey)
  localFilters.value = props.filters.map((f, idx) => {
    let checked
    if (cfg && cfg.checked && cfg.checked[f.key] !== undefined) checked = !!cfg.checked[f.key]
    else checked = f.defaultChecked !== false
    let order = idx
    if (cfg && Array.isArray(cfg.order)) {
      const i = cfg.order.indexOf(f.key)
      if (i >= 0) order = i
    }
    return { key: f.key, label: f.label, group: f.group || '', checked, order }
  }).sort((a, b) => a.order - b.order)
}

watch(() => [props.modelValue, props.filters], ([open]) => {
  if (open) {
    keyword.value = ''
    initLocalFilters()
  }
}, { immediate: true })

watch(visible, (v) => {
  if (v) {
    panelZIndex.value = nextZIndex()
    window.addEventListener('keydown', onKeydown)
  } else {
    window.removeEventListener('keydown', onKeydown)
  }
})

function onKeydown(e) {
  if (e.key === 'Escape' && visible.value) handleClose()
}
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

/** 关键字过滤：按字段名模糊匹配（同时保留已勾选项，避免搜不到就把勾选藏没了） */
const shownFilters = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return localFilters.value
  return localFilters.value.filter(f =>
    f.checked || String(f.label || '').toLowerCase().includes(kw) || String(f.group || '').toLowerCase().includes(kw))
})

function isShown(f) {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return true
  return f.checked || String(f.label || '').toLowerCase().includes(kw) || String(f.group || '').toLowerCase().includes(kw)
}

const checkedCount = computed(() => localFilters.value.filter(f => f.checked).length)

const allChecked = computed({
  get: () => localFilters.value.length > 0 && localFilters.value.every(f => f.checked),
  set: (val) => localFilters.value.forEach(f => { f.checked = val })
})
const someChecked = computed(() => {
  const n = checkedCount.value
  return n > 0 && n < localFilters.value.length
})

function handleSelectAll(val) {
  allChecked.value = val
}

function handleInvert() {
  localFilters.value.forEach(f => { f.checked = !f.checked })
}

/** 至少保留 1 个筛选条件：全不勾 = 查询区空无一物，用户会以为页面坏了 */
function handleCheckedChange(element) {
  if (localFilters.value.every(f => !f.checked)) {
    element.checked = true
    ElMessage.warning(tt('至少保留 1 个筛选条件'))
  }
}

function handleSave() {
  if (localFilters.value.every(f => !f.checked)) {
    ElMessage.warning(tt('至少保留 1 个筛选条件'))
    return
  }
  const order = localFilters.value.map(f => f.key)
  const checked = {}
  localFilters.value.forEach(f => { checked[f.key] = f.checked })
  emit('save', { order, checked })
  ElMessage.success(tt('筛选设置已保存'))
  visible.value = false
}

async function handleReset() {
  try {
    await ElMessageBox.confirm(tt('确定恢复默认筛选条件吗？'), tt('提示'), { type: 'warning' })
    emit('reset')
    initLocalFilters()
    keyword.value = ''
    ElMessage.success(tt('已恢复默认筛选条件'))
  } catch (e) {
    /* 用户取消 */
  }
}

function handleClose() {
  visible.value = false
}
</script>

<style scoped lang="scss">
.fs-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: transparent;
}

.fs-panel {
  position: fixed;
  right: 24px;
  width: fit-content;
  min-width: 360px;
  max-width: 440px;
  max-height: 78vh;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.14);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.fs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f0f0;

  .fs-title { font-size: 15px; font-weight: 600; color: #1f2937; }
  /* 操作按钮放标题栏右侧（2026-10-07 界面铁律：弹窗操作按钮不落底部） */
  .fs-header-actions { display: flex; align-items: center; gap: 8px; }
  .fs-close-btn {
    cursor: pointer;
    font-size: 18px;
    color: #9ca3af;
    transition: color .2s;
    &:hover { color: #ef4444; }
  }
}

.fs-search { padding: 10px 16px 0; }

.fs-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  flex-wrap: wrap;

  .fs-count { font-size: 12px; color: #909399; margin-left: auto; }
}

.fs-divider { margin: 0; }

.fs-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 16px 8px;
  min-height: 160px;

  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 3px; }
  &::-webkit-scrollbar-track { background: transparent; }
}

.fs-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 12px;
  color: #909399;
}

.fs-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  border-bottom: 1px solid #f5f5f5;
  transition: background .2s;

  &:hover { background: #f5f7fa; }

  .drag-handle { cursor: move; color: #909399; font-size: 16px; flex-shrink: 0; }

  :deep(.el-checkbox) {
    flex: 0 1 auto;
    margin-right: 0;
    .el-checkbox__label { font-size: 13px; color: #303133; }
  }

  .fs-group-tag {
    margin-left: auto;
    flex: 0 0 auto;
    font-size: 11px;
    color: #7c8db5;
    background: #f2f5ff;
    border-radius: 4px;
    padding: 1px 6px;
    white-space: nowrap;
  }
}

.ghost { opacity: .5; background: #e6f7ff !important; }
.chosen { background: #f0f5ff !important; }
.drag { opacity: .8; }

.fs-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 16px;
  font-size: 12px;
  color: #909399;
  border-top: 1px solid #f0f0f0;
  .el-icon { color: #409eff; font-size: 14px; }
}

.fs-fade-enter-active, .fs-fade-leave-active { transition: opacity .2s; }
.fs-fade-enter-from, .fs-fade-leave-to { opacity: 0; }

.fs-slide-enter-active { transition: all .25s ease-out; }
.fs-slide-leave-active { transition: all .2s ease-in; }
.fs-slide-enter-from { opacity: 0; transform: translateX(30px); }
.fs-slide-leave-to { opacity: 0; transform: translateX(30px); }
</style>
