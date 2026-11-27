<template>
  <div class="tags-view-container">
    <div class="tags-view-scroll" ref="scrollRef">
      <div class="tags-view-list">
        <div
          v-for="tag in visitedViews"
          :key="tag.fullPath"
          class="tags-view-item"
          :class="{ active: isActive(tag) }"
          @click="onTagClick(tag)"
          @contextmenu.prevent="openContextMenu($event, tag)"
        >
          <span class="tag-dot" v-if="isActive(tag)"></span>
          <span class="tag-title">{{ tag.title || tag.name || tag.fullPath }}</span>
          <el-icon
            v-if="!tag.affix"
            class="tag-close"
            @click.stop="onClose(tag)"
          >
            <Close />
          </el-icon>
        </div>
      </div>
    </div>

    <!-- 右侧操作按钮（原生 title 提示：避免 el-tooltip popper 在 Edge 下悬停闪跳） -->
    <div class="tags-actions">
      <el-icon class="action-icon" :title="$tt('刷新当前页')" @click="refreshActive"><Refresh /></el-icon>
      <el-icon class="action-icon" :title="$tt('关闭其他')" @click="closeOthers"><CircleClose /></el-icon>
      <el-icon class="action-icon" :title="$tt('全部关闭')" @click="closeAll"><CloseBold /></el-icon>
    </div>

    <!-- 右键菜单 -->
    <ul
      v-show="contextMenu.visible"
      class="context-menu"
      :style="{ top: contextMenu.top + 'px', left: contextMenu.left + 'px' }"
    >
      <li @click="refreshTag(contextMenu.tag)">{{ $tt("刷新") }}</li>
      <li v-if="contextMenu.tag && !contextMenu.tag.affix" @click="onClose(contextMenu.tag)">{{ $tt("关闭") }}</li>
      <li @click="closeOthers">{{ $tt("关闭其他") }}</li>
      <li @click="closeAll">{{ $tt("全部关闭") }}</li>
    </ul>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useTagsViewStore } from '@/stores/tagsView'
import { triggerPageRefresh } from '@/composables/useAutoRefresh'
import { Close, Refresh, CircleClose, CloseBold } from '@element-plus/icons-vue'

const router = useRouter()
const tagsStore = useTagsViewStore()

// 从 store 解构响应式数据供模板使用
const visitedViews = computed(() => tagsStore.visitedViews)

const scrollRef = ref(null)

function isActive(tag) {
  return tag.fullPath === tagsStore.activeFullPath
}

function onTagClick(tag) {
  tagsStore.activate(tag.fullPath)
}

async function onClose(tag) {
  await tagsStore.closeView(tag)
  scrollToActive()
}

function closeOthers() {
  tagsStore.closeOthers()
  hideContextMenu()
  scrollToActive()
}

async function closeAll() {
  await tagsStore.closeAll()
  hideContextMenu()
  scrollToActive()
}

/** 刷新当前激活标签 */
function refreshActive() {
  refreshTag(tagsStore.activeView)
}

/**
 * 刷新指定标签：
 * 1. 优先软刷新：页面已注册加载函数时只重拉数据，保留筛选条件/页码/组件状态
 * 2. 未注册页面（含编辑页）：强制销毁重建（移除缓存后经 /redirect 中转页跳回原路径）
 */
async function refreshTag(tag) {
  if (!tag) return
  hideContextMenu()
  // 软刷新：只重新拉取数据，不销毁组件
  if (triggerPageRefresh(tag.fullPath)) return
  // 强制刷新：不区分编辑页，统一销毁重建
  const name = tag.name
  if (name) {
    const idx = tagsStore.cachedViewNames.indexOf(name)
    if (idx >= 0) tagsStore.cachedViewNames.splice(idx, 1)
  }
  await nextTick()
  if (router) {
    await router.replace('/redirect' + tag.fullPath).catch(() => {})
  }
}

// ===== 右键菜单 =====
const contextMenu = reactive({
  visible: false,
  top: 0,
  left: 0,
  tag: null
})

function openContextMenu(e, tag) {
  contextMenu.tag = tag
  const maxLeft = window.innerWidth - 160
  const maxTop = window.innerHeight - 140
  contextMenu.left = Math.min(e.clientX, maxLeft)
  contextMenu.top = Math.min(e.clientY, maxTop)
  contextMenu.visible = true
}

function hideContextMenu() {
  contextMenu.visible = false
}

function onDocumentClick() {
  hideContextMenu()
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
})
onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
})

// 监听激活标签变化，滚动到可见区域
watch(() => tagsStore.activeFullPath, () => {
  nextTick(() => scrollToActive())
})

function scrollToActive() {
  if (!scrollRef.value) return
  const container = scrollRef.value
  const activeEl = container.querySelector('.tags-view-item.active')
  if (!activeEl) return
  const containerRect = container.getBoundingClientRect()
  const activeRect = activeEl.getBoundingClientRect()
  if (activeRect.left < containerRect.left) {
    container.scrollLeft -= containerRect.left - activeRect.left + 8
  } else if (activeRect.right > containerRect.right) {
    container.scrollLeft += activeRect.right - containerRect.right + 8
  }
}
</script>

<style scoped lang="scss">
.tags-view-container {
  display: flex;
  align-items: center;
  height: 36px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.tags-view-scroll {
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  height: 100%;
  &::-webkit-scrollbar { height: 4px; }
  &::-webkit-scrollbar-thumb { background: #dcdfe6; border-radius: 2px; }
}

.tags-view-list {
  display: inline-flex;
  align-items: center;
  height: 36px;
  padding: 0 8px;
  white-space: nowrap;
}

.tags-view-item {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  margin: 0 4px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fafafa;
  color: #595959;
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s;
  position: relative;
  flex-shrink: 0;

  &:hover {
    color: var(--erp-primary, #409EFF);
    border-color: var(--erp-primary, #409EFF);
  }

  &.active {
    background: var(--erp-primary, #409EFF);
    color: #fff;
    border-color: var(--erp-primary, #409EFF);
  }

  .tag-dot {
    display: inline-block;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #fff;
    margin-right: 6px;
    flex-shrink: 0;
  }

  .tag-title {
    max-width: 180px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .tag-close {
    margin-left: 6px;
    font-size: 12px;
    border-radius: 50%;
    padding: 1px;
    transition: all 0.2s;
    flex-shrink: 0;
    &:hover {
      background: rgba(255, 255, 255, 0.3);
      color: #fff;
    }
  }
  &:not(.active) .tag-close:hover {
    background: var(--erp-primary, #409EFF);
    color: #fff;
  }
}

.tags-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 14px;
  border-left: 1px solid #e4e7ed;
  height: 100%;
  flex-shrink: 0;

  .action-icon {
    font-size: 16px;
    color: #606266;
    cursor: pointer;
    transition: color 0.2s;
    &:hover {
      color: var(--erp-primary, #409EFF);
    }
  }
}

.context-menu {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  margin: 0;
  list-style: none;
  font-size: 13px;
  min-width: 120px;

  li {
    padding: 8px 16px;
    cursor: pointer;
    color: #303133;
    transition: background 0.2s;
    &:hover {
      background: #f5f7fa;
      color: var(--erp-primary, #409EFF);
    }
  }
}
</style>
