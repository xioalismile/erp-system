<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="bottom-end"
    :width="320"
    trigger="click"
    popper-class="col-settings-popper"
  >
    <template #reference>
      <el-tooltip :content="$tt('列设置')" placement="top">
        <el-button :icon="Setting" circle size="default" class="col-settings-btn" v-permission="perm('columnSetting')" />
      </el-tooltip>
    </template>

    <div class="col-settings-panel">
      <div class="panel-header">
        <span class="panel-title">{{ $tt("列设置") }}</span>
        <div class="panel-actions">
          <el-button text size="small" v-permission="perm('columnSetting')" @click="selectAll">{{ $tt("全选") }}</el-button>
          <el-button text size="small" v-permission="perm('reset')" @click="reset">{{ $tt("重置") }}</el-button>
        </div>
      </div>
      <el-divider style="margin:8px 0;" />
      <div class="panel-tip">{{ $tt("勾选要显示的列，可拖拽调整顺序") }}</div>
      <div class="col-list">
        <div
          v-for="(col, idx) in localConfig"
          :key="col.prop"
          class="col-item"
          :class="{ 'is-fixed': col.fixed }"
          draggable="true"
          @dragstart="onDragStart(idx)"
          @dragover.prevent="onDragOver(idx)"
          @drop="onDrop(idx)"
          @dragend="onDragEnd"
        >
          <el-icon class="drag-handle"><Rank /></el-icon>
          <el-checkbox v-model="col.visible" @change="emitUpdate">{{ $tf(col.label) }}</el-checkbox>
          <el-tooltip :content="col.fixed ? '取消固定' : '固定列'" placement="top">
            <el-icon
              class="fixed-icon"
              :class="{ active: col.fixed }"
              @click="toggleFixed(idx)"
            >
              <Lock v-if="col.fixed" />
              <Unlock v-else />
            </el-icon>
          </el-tooltip>
        </div>
      </div>
      <el-divider style="margin:8px 0;" />
      <div class="panel-footer">
        <span class="footer-info">已选 {{ visibleCount }}/{{ localConfig.length }} 列</span>
        <el-button type="primary" size="small" v-permission="perm('apply')" @click="apply">{{ $tt("应用") }}</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { Setting, Rank, Lock, Unlock } from '@element-plus/icons-vue'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('report:custom')

const props = defineProps({
  storageKey: { type: String, required: true },
  config: { type: Array, default: () => [] }
})
const emit = defineEmits(['update'])

const popoverVisible = ref(false)
const localConfig = ref([])
const dragIndex = ref(-1)

// 初始化/同步配置
watch(() => props.config, (newConfig) => {
  if (newConfig && newConfig.length > 0) {
    localConfig.value = JSON.parse(JSON.stringify(newConfig))
  }
}, { immediate: true, deep: true })

const visibleCount = computed(() => localConfig.value.filter(c => c.visible).length)

function emitUpdate() {
  // 复选框变化时不立即应用，等用户点"应用"
}

function selectAll() {
  localConfig.value.forEach(c => { c.visible = true })
}

function reset() {
  // 取消所有固定，全选
  localConfig.value.forEach(c => {
    c.visible = true
    c.fixed = false
  })
}

function toggleFixed(idx) {
  localConfig.value[idx].fixed = !localConfig.value[idx].fixed
}

// 拖拽排序
function onDragStart(idx) {
  dragIndex.value = idx
}
function onDragOver(idx) {
  // 阻止默认行为允许 drop
}
function onDrop(idx) {
  if (dragIndex.value === -1 || dragIndex.value === idx) return
  const moved = localConfig.value.splice(dragIndex.value, 1)[0]
  localConfig.value.splice(idx, 0, moved)
  dragIndex.value = -1
}
function onDragEnd() {
  dragIndex.value = -1
}

function apply() {
  emit('update', JSON.parse(JSON.stringify(localConfig.value)))
  popoverVisible.value = false
}
</script>

<style scoped lang="scss">
.col-settings-btn {
  margin-left: 8px;
}

.col-settings-panel {
  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    .panel-title {
      font-size: 14px;
      font-weight: 600;
      color: #1f2937;
    }
    .panel-actions {
      display: flex;
      gap: 4px;
    }
  }

  .panel-tip {
    font-size: 11px;
    color: #9ca3af;
    margin-bottom: 8px;
  }

  .col-list {
    max-height: 280px;
    overflow-y: auto;
    padding-right: 4px;

    &::-webkit-scrollbar { width: 6px; }
    &::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 3px; }
    &::-webkit-scrollbar-track { background: #f9fafb; }
  }

  .col-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 8px;
    border-radius: 6px;
    cursor: move;
    transition: background .15s;
    font-size: 13px;

    &:hover {
      background: #f3f4f6;
    }

    &.is-fixed {
      background: #fef3c7;
    }

    .drag-handle {
      color: #c7c9cc;
      cursor: grab;
      font-size: 14px;
      &:hover { color: #6b7280; }
    }

    :deep(.el-checkbox) {
      flex: 1;
      .el-checkbox__label {
        font-size: 13px;
        color: #374151;
      }
    }

    .fixed-icon {
      color: #c7c9cc;
      cursor: pointer;
      font-size: 14px;
      padding: 2px;
      border-radius: 4px;
      &:hover {
        color: #6b7280;
        background: #e5e7eb;
      }
      &.active {
        color: #f59e0b;
      }
    }
  }

  .panel-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    .footer-info {
      font-size: 12px;
      color: #6b7280;
    }
  }
}
</style>

<style>
.col-settings-popper {
  padding: 12px !important;
}
</style>
