<template>
  <Teleport to="body">
    <!-- 透明点击层，用于点击外部关闭 -->
    <transition name="dcs-fade">
      <div v-if="visible" class="dcs-backdrop" :style="{ zIndex: panelZIndex - 1 }" @click="handleClose"></div>
    </transition>
    <!-- 浮动面板（fixed 定位，固定在视口右侧）—— 交互与列设置抽屉保持一致 -->
    <transition name="dcs-slide">
      <div v-if="visible" class="dcs-panel" :style="{ zIndex: panelZIndex }" @click.stop>
        <div class="dcs-header">
          <span class="dcs-title">{{ $tt("首页设置") }}</span>
          <el-icon class="dcs-close-btn" @click="handleClose"><Close /></el-icon>
        </div>

        <div class="dcs-toolbar">
          <el-checkbox :model-value="allVisible" :indeterminate="someVisible" @change="handleSelectAll">{{ $tt("全选") }}</el-checkbox>
          <el-button text type="warning" @click="handleReset">{{ $tt("恢复默认") }}</el-button>
          <el-button v-if="allowAdd" text type="primary" class="dcs-add-btn" @click="handleAdd">
            {{ $tt("添加自定义卡片") }}
          </el-button>
        </div>
        <el-divider class="dcs-divider" />

        <div class="dcs-list">
          <draggable
            v-model="localCards"
            item-key="id"
            handle=".drag-handle"
            animation="200"
            ghost-class="ghost"
            chosen-class="chosen"
            drag-class="drag"
          >
            <template #item="{ element }">
              <div class="dcs-row">
                <el-icon class="drag-handle"><Rank /></el-icon>
                <el-checkbox v-model="element.visible" @change="handleVisibleChange(element)">{{ element.title }}</el-checkbox>
                <span v-if="element.custom" class="dcs-custom-tag">{{ $tt("自定义") }}</span>
                <el-button
                  v-if="element.custom"
                  text
                  type="danger"
                  size="small"
                  class="dcs-del-btn"
                  @click="handleDelete(element)"
                >{{ armedIds.includes(element.id) ? $tt("确认删除") : $tt("删除") }}</el-button>
              </div>
            </template>
          </draggable>
        </div>

        <div class="dcs-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ $tt("拖动") }} <el-icon><Rank /></el-icon> {{ $tt("调整卡片顺序，勾选控制卡片显示") }}</span>
        </div>

        <div class="dcs-footer">
          <el-button @click="handleReset">{{ $tt("恢复默认") }}</el-button>
          <el-button @click="handleClose">{{ $tt("取消") }}</el-button>
          <el-button type="primary" @click="handleSave">{{ $tt("保存") }}</el-button>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { ElMessage, useZIndex } from 'element-plus'
import { Rank, InfoFilled, Close } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import { tt } from '@/i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // [{ id, title, visible, custom? }] 按当前首页顺序传入（打开时作为初始值）
  cards: { type: Array, default: () => [] },
  // 是否允许「添加自定义卡片」（工作台自定义卡，2026-11-05）
  allowAdd: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'save', 'reset', 'add', 'delete'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// 层级：必须由 Element Plus 的层级管理器分配，不能用固定值（同列设置抽屉）
const { nextZIndex } = useZIndex()
const panelZIndex = ref(2000)

const localCards = ref([])

function initLocalCards () {
  localCards.value = props.cards.map(c => ({ id: c.id, title: c.title, visible: c.visible !== false, custom: c.custom === true }))
}

watch(() => [props.modelValue, props.cards], ([isOpen]) => {
  if (isOpen) initLocalCards()
}, { immediate: true })

function onKeydown (e) {
  if (e.key === 'Escape' && visible.value) handleClose()
}

watch(visible, (v) => {
  if (v) {
    panelZIndex.value = nextZIndex()
    window.addEventListener('keydown', onKeydown)
  } else {
    window.removeEventListener('keydown', onKeydown)
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})

const allVisible = computed({
  get: () => localCards.value.length > 0 && localCards.value.every(c => c.visible),
  set: (val) => { localCards.value.forEach(c => { c.visible = val }) }
})
const someVisible = computed(() => {
  const n = localCards.value.filter(c => c.visible).length
  return n > 0 && n < localCards.value.length
})

function handleSelectAll (val) { allVisible.value = val }

function handleVisibleChange (element) {
  if (!localCards.value.some(c => c.visible)) {
    element.visible = true
    ElMessage.warning(tt('至少保留 1 张卡片显示'))
  }
}

function handleReset () { emit('reset') }

function handleAdd () { emit('add') }

/** 自定义卡片删除：第一次点变「确认删除」，3 秒内再点才真删（不用原生弹窗） */
const armedIds = ref([])
function handleDelete (element) {
  const id = element.id
  if (armedIds.value.includes(id)) {
    armedIds.value = armedIds.value.filter(x => x !== id)
    emit('delete', id)
    return
  }
  armedIds.value = [...armedIds.value, id]
  setTimeout(() => {
    armedIds.value = armedIds.value.filter(x => x !== id)
  }, 3000)
}

function handleSave () {
  if (!localCards.value.some(c => c.visible)) {
    ElMessage.warning(tt('至少保留 1 张卡片显示'))
    return
  }
  emit('save', localCards.value.map(c => ({ id: c.id, visible: c.visible })))
  visible.value = false
}

function handleClose () { visible.value = false }
</script>

<style scoped>
.dcs-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: transparent;
}

.dcs-panel {
  position: fixed;
  right: 24px;
  top: 80px;
  width: fit-content;
  min-width: 320px;
  max-width: 380px;
  max-height: 75vh;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.14);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dcs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 10px;
  border-bottom: 1px solid #f0f0f0;
}
.dcs-header .dcs-title { font-size: 15px; font-weight: 600; color: #1f2937; }
.dcs-header .dcs-close-btn { cursor: pointer; font-size: 18px; color: #9ca3af; transition: color 0.2s; }
.dcs-header .dcs-close-btn:hover { color: #ef4444; }

.dcs-toolbar { display: flex; align-items: center; gap: 12px; padding: 8px 16px; }
.dcs-divider { margin: 0; }

.dcs-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 16px;
  min-height: 0;
}
.dcs-list::-webkit-scrollbar { width: 6px; }
.dcs-list::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 3px; }
.dcs-list::-webkit-scrollbar-track { background: transparent; }

.dcs-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 4px;
  border-bottom: 1px solid #f5f5f5;
  transition: background 0.2s;
}
.dcs-row:hover { background: #f5f7fa; }
.dcs-row .drag-handle { cursor: move; color: #909399; font-size: 16px; flex-shrink: 0; }
.dcs-row :deep(.el-checkbox) { flex: 1; margin-right: 0; }
.dcs-row :deep(.el-checkbox__label) { font-size: 13px; color: #303133; }
.dcs-custom-tag {
  font-size: 10px;
  background: #eff6ff;
  color: #2563eb;
  border-radius: 4px;
  padding: 1px 5px;
  flex-shrink: 0;
}
.dcs-del-btn { padding: 2px 4px; font-size: 12px; flex-shrink: 0; }
.dcs-add-btn { margin-left: auto; }

.ghost { opacity: 0.5; background: #e6f7ff !important; }
.chosen { background: #f0f5ff !important; }
.drag { opacity: 0.8; }

.dcs-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 16px;
  font-size: 12px;
  color: #909399;
  border-top: 1px solid #f0f0f0;
}
.dcs-tip .el-icon { color: #409eff; font-size: 14px; }

.dcs-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}

.dcs-fade-enter-active, .dcs-fade-leave-active { transition: opacity 0.2s; }
.dcs-fade-enter-from, .dcs-fade-leave-to { opacity: 0; }
.dcs-slide-enter-active { transition: all 0.25s ease-out; }
.dcs-slide-leave-active { transition: all 0.2s ease-in; }
.dcs-slide-enter-from { opacity: 0; transform: translateX(30px); }
.dcs-slide-leave-to { opacity: 0; transform: translateX(30px); }
</style>
