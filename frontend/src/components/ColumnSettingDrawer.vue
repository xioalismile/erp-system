<template>
  <Teleport to="body">
    <!-- 透明点击层，用于点击外部关闭 -->
    <transition name="cs-fade">
      <div v-if="visible" class="cs-backdrop" :style="{ zIndex: panelZIndex - 1 }" @click="handleClose"></div>
    </transition>
    <!-- 浮动面板（fixed 定位，固定在视口右侧） -->
    <transition name="cs-slide">
      <div v-if="visible" class="cs-panel" :style="{ top: panelTop + 'px', zIndex: panelZIndex }" @click.stop>
        <!-- 头部 -->
        <div class="cs-header">
          <span class="cs-title">{{ title || '列设置' }}</span>
          <el-icon class="cs-close-btn" @click="handleClose"><Close /></el-icon>
        </div>

        <!-- 视图模式（2026-10-12 列表页明细模式）：仅声明了明细能力的页面出现；切换立即生效，两种模式各存一套列配置 -->
        <div v-if="showViewMode" class="cs-viewmode">
          <div class="cs-viewmode-title">{{ $tt("视图模式") }}</div>
          <el-radio-group :model-value="viewMode" size="small" @change="handleViewModeChange">
            <el-radio-button value="summary">{{ $tt("汇总模式") }}</el-radio-button>
            <el-radio-button value="detail">{{ $tt("明细模式") }}</el-radio-button>
          </el-radio-group>
          <div class="cs-viewmode-tip">{{ $tt("汇总 = 现有列表（一行一张单据）；明细 = 一行一条商品明细（报表式）") }}</div>
        </div>
        <el-divider v-if="showViewMode" class="cs-divider" />

        <!-- 工具栏 -->
        <div class="cs-toolbar">
          <el-checkbox
            :model-value="allVisible"
            :indeterminate="someVisible"
            @change="handleSelectAll"
          >{{ $tt("全选") }}</el-checkbox>
          <el-button text type="primary" v-permission="perm('columnSetting')" @click="handleInvert">{{ $tt("反选") }}</el-button>
          <el-button text type="warning" v-permission="perm('resetDefault')" @click="handleReset">{{ $tt("重置默认") }}</el-button>
          <el-button text type="warning" v-permission="perm('resetDefault')" @click="handleResetWidths">{{ $tt("恢复默认列宽") }}</el-button>
        </div>
        <el-divider class="cs-divider" />

        <!-- 列列表（可拖拽） -->
        <div class="cs-list">
          <draggable
            v-model="localColumns"
            item-key="prop"
            handle=".drag-handle"
            animation="200"
            ghost-class="ghost"
            chosen-class="chosen"
            drag-class="drag"
            @end="handleDragEnd"
          >
            <template #item="{ element }">
              <div
                class="cs-row"
                :class="{ 'not-configurable': !element.isConfigurable }"
              >
                <el-icon v-if="element.isConfigurable" class="drag-handle">
                  <Rank />
                </el-icon>
                <el-icon v-else class="drag-handle disabled">
                  <Lock />
                </el-icon>
                <el-checkbox
                  v-model="element.visible"
                  :disabled="!element.isConfigurable"
                  @change="handleVisibleChange(element)"
                >{{ $tf(element.label) || element.prop }}</el-checkbox>
                <el-select
                  v-model="element.fixed"
                  size="small"
                  class="fixed-select"
                  :disabled="!element.isConfigurable"
                >
                  <el-option :value="false" :label="$tf('流动')" />
                  <el-option value="left" :label="$tf('固定左侧')" />
                  <el-option value="right" :label="$tf('固定右侧')" />
                </el-select>
              </div>
            </template>
          </draggable>
        </div>

        <!-- 提示 -->
        <div class="cs-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ $tt("拖动") }} <el-icon><Rank /></el-icon> {{ $tt("调整列顺序，勾选控制列显示") }}</span>
        </div>

        <!-- 底部按钮 -->
        <div class="cs-footer">
          <el-button v-permission="perm('resetDefault')" @click="handleReset">{{ $tt("重置默认") }}</el-button>
          <el-button v-permission="perm('dlgCancel')" @click="handleClose">{{ $tt("取消") }}</el-button>
          <el-button type="primary" v-permission="perm('save')" :loading="saving" @click="handleSave">{{ $tt("保存") }}</el-button>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox, useZIndex } from 'element-plus'
import { Rank, Lock, InfoFilled, Close } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import { useColumnConfigStore } from '@/stores/columnConfig'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('report:custom')

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  pageKey: { type: String, required: true },
  columns: { type: Array, default: () => [] },
  title: { type: String, default: '' },
  // 视图模式（2026-10-12 列表页明细模式）：showViewMode=true 时抽屉顶部出现「汇总/明细」切换，
  // 切换立即通过 update:viewMode 抛回给页面（页面负责持久化并切换下方列清单）。
  showViewMode: { type: Boolean, default: false },
  viewMode: { type: String, default: 'summary' }
})

const emit = defineEmits(['update:modelValue', 'change', 'update:viewMode'])

const store = useColumnConfigStore()
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// 面板位置：fixed 定位，固定在视口右侧，不随滚动移动
const panelTop = ref(80)

// 层级：必须由 Element Plus 的层级管理器分配，不能用固定值。
// 原因：el-dialog / el-message-box 等弹层的 z-index 从 2000 起逐个自增，
// 固定写 2000 时面板会落在"从物料库选择"这类弹窗遮罩之下 → 表现为"点列设置没反应"。
const { nextZIndex } = useZIndex()
const panelZIndex = ref(2000)

function onKeydown(e) {
  if (e.key === 'Escape' && visible.value) handleClose()
}

watch(visible, (v) => {
  if (v) {
    // 每次打开都取一个比当前所有弹层更高的层级，保证面板一定在最上层
    panelZIndex.value = nextZIndex()
    window.addEventListener('keydown', onKeydown)
  } else {
    window.removeEventListener('keydown', onKeydown)
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})

// 本地列副本
const localColumns = ref([])
const saving = ref(false)

function initLocalColumns() {
  const userCfg = store.getPageConfig(props.pageKey)
  localColumns.value = props.columns.map((col, idx) => {
    const isSpecial = col.type === 'selection' || col.type === 'index' || col.type === 'action' || col.prop === 'action'
    const u = userCfg[col.prop] || {}
    return {
      prop: col.prop,
      label: col.label || col.prop,
      type: col.type,
      // 无用户配置时：尊重列定义 defaultHidden（自定义字段 isListShow=0 默认不勾选）
      visible: u.visible !== undefined ? u.visible : !(col.defaultHidden === true),
      fixed: u.fixed !== undefined ? u.fixed : (col.fixed ?? false),
      order: u.order !== undefined ? u.order : idx,
      isConfigurable: !isSpecial
    }
  }).sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
}

watch(() => [props.modelValue, props.columns], ([isOpen]) => {
  if (isOpen) initLocalColumns()
}, { immediate: true })

const allVisible = computed({
  get: () => localColumns.value.filter(c => c.isConfigurable).every(c => c.visible),
  set: (val) => {
    localColumns.value.forEach(c => {
      if (c.isConfigurable) c.visible = val
    })
  }
})
const someVisible = computed(() => {
  const configurable = localColumns.value.filter(c => c.isConfigurable)
  const visibleCount = configurable.filter(c => c.visible).length
  return visibleCount > 0 && visibleCount < configurable.length
})

function handleSelectAll(val) { allVisible.value = val }
function handleInvert() {
  localColumns.value.forEach(c => { if (c.isConfigurable) c.visible = !c.visible })
}
function handleVisibleChange(element) {
  const configurable = localColumns.value.filter(c => c.isConfigurable)
  if (configurable.filter(c => c.visible).length === 0) {
    element.visible = true
    ElMessage.warning(tt('至少保留 1 列可见'))
  }
}
function handleDragEnd() {}

/** 视图模式切换（2026-10-12）：立即抛回页面（页面持久化 + 列清单随 props.columns 换成新模式那套） */
function handleViewModeChange(mode) {
  emit('update:viewMode', mode)
}

async function handleSave() {
  const configurable = localColumns.value.filter(c => c.isConfigurable)
  if (configurable.length > 0 && configurable.every(c => !c.visible)) {
    ElMessage.warning(tt('至少保留 1 列可见'))
    return
  }
  saving.value = true
  try {
    const columnsCfg = {}
    localColumns.value.forEach((c, idx) => {
      columnsCfg[c.prop] = { visible: c.visible, fixed: c.fixed, order: idx }
    })
    store.setPageConfig(props.pageKey, columnsCfg)
    emit('change', { type: 'save' })
    ElMessage.success(tt('列设置已保存'))
    visible.value = false
  } finally {
    saving.value = false
  }
}

async function handleReset() {
  try {
    await ElMessageBox.confirm(tt('确定重置当前页面的列设置为默认值吗？'), tt('提示'), { type: 'warning' })
    store.resetPage(props.pageKey)
    initLocalColumns()
    ElMessage.success(tt('已重置为默认列设置'))
    emit('change', { type: 'reset' })
  } catch (e) {}
}

/**
 * 恢复默认列宽（2026-09-22 新增）
 *
 * 列宽是用户拖拽表头单独产生的记忆（存在同一 pageKey 的 columns[prop].width 上），
 * 与「列顺序 / 显隐」互不影响；本按钮只清列宽，不动勾选与排序。
 * 本组件拿不到表格实例，故清完 store 后 emit change(type=resetWidths)，
 * 由持有 el-table 的容器（BillListPage / BillItemsTable / 报表页等）负责重排。
 */
async function handleResetWidths() {
  try {
    await ElMessageBox.confirm(tt('确定恢复默认列宽吗？（列顺序与显示状态不受影响）'), tt('提示'), { type: 'warning' })
    store.resetColumnWidths(props.pageKey)
    ElMessage.success(tt('已恢复默认列宽'))
    emit('change', { type: 'resetWidths' })
  } catch (e) {}
}

function handleClose() { visible.value = false }
</script>

<style scoped lang="scss">
/* 透明背景层：不遮挡页面，仅用于捕获点击关闭 */
.cs-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 1999;
  background: transparent;
}

/* 浮动面板：fixed 定位，固定在视口右侧，滚动时不移动 */
.cs-panel {
  position: fixed;
  right: 24px;
  width: fit-content;
  min-width: 320px;
  max-width: 380px;
  max-height: 75vh;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.14);
  z-index: 2000;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 10px;
  border-bottom: 1px solid #f0f0f0;

  .cs-title {
    font-size: 15px;
    font-weight: 600;
    color: #1f2937;
  }
  .cs-close-btn {
    cursor: pointer;
    font-size: 18px;
    color: #9ca3af;
    transition: color 0.2s;
    &:hover { color: #ef4444; }
  }
}

.cs-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
}

.cs-divider {
  margin: 0;
}

/* 视图模式（2026-10-12 列表页明细模式） */
.cs-viewmode {
  padding: 10px 16px 12px;

  .cs-viewmode-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 8px;
  }
  .cs-viewmode-tip {
    font-size: 12px;
    color: #909399;
    line-height: 18px;
    margin-top: 8px;
  }
}

.cs-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 16px;
  min-height: 0;

  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 3px; }
  &::-webkit-scrollbar-track { background: transparent; }
}

.cs-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 4px;
  border-bottom: 1px solid #f5f5f5;
  transition: background 0.2s;

  &:hover { background: #f5f7fa; }
  &.not-configurable { opacity: 0.55; background: #fafafa; }

  .drag-handle {
    cursor: move;
    color: #909399;
    font-size: 16px;
    flex-shrink: 0;
    &.disabled { cursor: not-allowed; color: #c0c4cc; }
  }

  :deep(.el-checkbox) {
    flex: 1;
    margin-right: 0;
    .el-checkbox__label { font-size: 13px; color: #303133; }
  }

  .fixed-select {
    width: 110px;
    flex-shrink: 0;
  }
}

.ghost { opacity: 0.5; background: #e6f7ff !important; }
.chosen { background: #f0f5ff !important; }
.drag { opacity: 0.8; }

.cs-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 16px;
  font-size: 12px;
  color: #909399;
  border-top: 1px solid #f0f0f0;
  .el-icon { color: #409eff; font-size: 14px; }
}

.cs-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}

/* 过渡动画 */
.cs-fade-enter-active, .cs-fade-leave-active { transition: opacity 0.2s; }
.cs-fade-enter-from, .cs-fade-leave-to { opacity: 0; }

.cs-slide-enter-active { transition: all 0.25s ease-out; }
.cs-slide-leave-active { transition: all 0.2s ease-in; }
.cs-slide-enter-from { opacity: 0; transform: translateX(30px); }
.cs-slide-leave-to { opacity: 0; transform: translateX(30px); }
</style>
