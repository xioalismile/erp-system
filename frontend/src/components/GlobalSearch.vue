<template>
  <div ref="wrapRef" class="global-search">
    <el-input
      ref="inputRef"
      v-model="keyword"
      class="global-search-input"
      size="default"
      clearable
      :placeholder="$t('common.globalSearchPlaceholder', '搜索菜单、单号、物料、客户')"
      @input="onInput"
      @focus="onFocus"
      @blur="onBlur"
      @keydown.esc.stop="close"
      @keydown.down.prevent="move(1)"
      @keydown.up.prevent="move(-1)"
      @keydown.enter.prevent="go(flatItems[activeIndex])"
    >
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>

    <transition name="gs-fade">
      <div ref="panelRef" v-if="panelVisible" class="gs-panel">
        <template v-for="g in visibleGroups" :key="g.key">
          <div class="gs-group-title">
            {{ g.title }}
            <span v-if="g.failed" class="gs-failed" title="点击重试" @mousedown.stop @click.stop="retry">加载失败，点击重试</span>
          </div>
          <div
            v-for="it in g.items"
            :key="g.key + '-' + it.key"
            class="gs-item"
            :class="{ 'is-active': it.flatIndex === activeIndex }"
            @mouseenter="activeIndex = it.flatIndex"
            @mousedown.prevent
            @click="go(it)"
          >
            <span class="gs-item-main">{{ it.main }}</span>
            <span v-if="it.exact" class="gs-exact">精准</span>
            <span v-if="it.sub" class="gs-item-sub">{{ it.sub }}</span>
          </div>
        </template>
        <div v-if="!loading && flatItems.length === 0 && !anyFailed" class="gs-empty">
          未找到与「{{ keyword }}」相关的结果
        </div>
        <div v-if="loading" class="gs-loading">搜索中...</div>
      </div>
    </transition>
  </div>
</template>

<script setup>
/**
 * 万能搜索框（页头全局搜索）
 *
 * 四类结果，全部按登录人权限过滤：
 * ① 页面：本地菜单配置过滤（与左侧菜单同一份权限口径，看不到的菜单搜不到）
 * ② 单据：后端按单号 精确命中优先 + 模糊包含（全站单据注册表全覆盖，自带数据权限），
 *    点击跳该单据编辑页（复用全站唯一单据跳转映射），仅显示登录人有菜单权限的单据类型
 * ③ 物料 / ④ 客户 / 供应商：复用档案列表既有搜索接口（自带数据权限），点击跳对应编辑页
 *
 * 交互：输入防抖 300ms；每组最多 5 条；↑↓ 选择、回车跳第一项、Esc 关闭、Ctrl+K 聚焦。
 */
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { searchBills } from '@/api/common/globalSearch'
import { pageMaterial } from '@/api/basedata/material'
import { listCustomer } from '@/api/basedata/customer'
import { listSupplier } from '@/api/basedata/supplier'
import { getMenuGroupsByRoutePaths } from '@/config/menuConfig'
import { BILL_EDIT_ROUTE, resolveBillRoute } from '@/utils/billRoute'
import { normalizeStatus } from '@/utils/bill-status'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const wrapRef = ref(null)
const inputRef = ref(null)
const keyword = ref('')
const focused = ref(false)
const loading = ref(false)

const menuItems = ref([])          // 页面结果
const billItems = ref([])          // 单据结果
const materialItems = ref([])
const customerItems = ref([])
const supplierItems = ref([])
const billFailed = ref(false)
const materialFailed = ref(false)
const customerFailed = ref(false)
const supplierFailed = ref(false)

const activeIndex = ref(0)

const panelVisible = computed(() => focused.value && !!keyword.value.trim())

/** 登录人可见的全部菜单项（与左侧菜单同一份权限口径），结果缓存 */
let cachedMenus = null
function collectMenus() {
  if (cachedMenus) return cachedMenus
  const out = []
  const groups = getMenuGroupsByRoutePaths(userStore.menuRoutePaths || [], userStore.roles || []) || []
  groups.forEach(g => {
    ;(g.items || []).forEach(it => {
      if (it.children && it.children.length) {
        it.children.forEach(c => { if (c.path) out.push({ label: c.label, group: g.label, path: c.path }) })
      } else if (it.path) {
        out.push({ label: it.label, group: g.label, path: it.path })
      }
    })
  })
  cachedMenus = out
  return out
}

/** 某功能路径是否在登录人可见菜单内（如 /sale/orders）；与菜单搜索同源判断 */
function menuAllowed(prefix) {
  return collectMenus().some(m => m.path === prefix || m.path.startsWith(prefix + '/'))
}

/** 单据类型 → 其编辑页所属的菜单路径前缀（/sale/orders/0 → /sale/orders）；不可跳返回 null */
function billMenuPrefix(docType) {
  const build = BILL_EDIT_ROUTE[docType]
  if (typeof build !== 'function') return null
  let sample
  try { sample = build('0') } catch (e) { return null }
  if (typeof sample !== 'string') return null
  const parts = sample.split('/').filter(Boolean)
  return parts.length >= 2 ? '/' + parts.slice(0, 2).join('/') : null
}

const GROUP_DEFS = [
  { key: 'page', title: '页面', items: menuItems, failed: () => false },
  { key: 'bill', title: '单据', items: billItems, failed: () => billFailed.value },
  { key: 'material', title: '物料', items: materialItems, failed: () => materialFailed.value },
  { key: 'customer', title: '客户', items: customerItems, failed: () => customerFailed.value },
  { key: 'supplier', title: '供应商', items: supplierItems, failed: () => supplierFailed.value }
]

const groups = computed(() => GROUP_DEFS.map(g => ({ key: g.key, title: g.title, items: g.items.value, failed: g.failed() })))
// 空分组整个隐藏（不显示"有标题无内容"的空架子）；加载失败的分组保留并给出重试入口
const visibleGroups = computed(() => groups.value.filter(g => g.items.length > 0 || g.failed))
const anyFailed = computed(() => groups.value.some(g => g.failed))
const flatItems = computed(() => groups.value.flatMap(g => g.items))

function dateOnly(s) {
  const str = String(s || '')
  return /^\d{4}-\d{2}-\d{2}/.test(str) ? str.slice(0, 10) : ''
}

function filterMenus(kw) {
  const list = collectMenus()
  // 排序权重：名称精准 > 所属模块名命中（搜"销售"→"销售管理"组整体排前，不被其它模块的零散命中挤出）> 名称开头 > 名称包含 > 仅模块名包含
  const weight = m => {
    if (m.label === kw) return 0
    if (m.group.includes(kw)) return 1
    if (m.label.startsWith(kw)) return 2
    if (m.label.includes(kw)) return 3
    return 4
  }
  const hit = list.filter(m => m.label.includes(kw) || m.group.includes(kw))
  // 页面组不显示右侧副文字（分组名与分组标题重复，刷屏无信息量）
  return hit.sort((a, b) => weight(a) - weight(b)).slice(0, 5).map(m => ({
    key: m.path, main: m.label, path: m.path
  }))
}

// ============================== 搜索 ==============================
let seq = 0
let timer = null

function onInput() {
  clearTimeout(timer)
  const kw = (keyword.value || '').trim()
  if (!kw) { resetResults(); return }
  timer = setTimeout(() => doSearch(kw, ++seq), 300)
}

let blurTimer = null
function onFocus() {
  // 重新聚焦必须取消上一次失焦安排的延迟关闭（否则快速 Esc→再点输入框会被旧定时器误关面板）
  clearTimeout(blurTimer)
  focused.value = true
  const kw = (keyword.value || '').trim()
  if (kw && menuItems.value.length === 0 && billItems.value.length === 0) doSearch(kw, ++seq)
}

function onBlur() {
  clearTimeout(blurTimer)
  // 延迟关闭：给面板点击留一拍（面板内 mousedown 已 preventDefault，双保险）
  blurTimer = setTimeout(() => { focused.value = false }, 150)
}

function close() {
  clearTimeout(blurTimer)
  focused.value = false
  inputRef.value?.blur?.()
}

async function doSearch(kw, mySeq) {
  loading.value = true
  billFailed.value = materialFailed.value = customerFailed.value = supplierFailed.value = false

  // ① 页面：本地即时出结果
  menuItems.value = filterMenus(kw)

  // ②③④⑤ 单据 + 三个档案：并行查询
  const [bills, mats, cuss, sups] = await Promise.allSettled([
    searchBills(kw),
    pageMaterial({ current: 1, size: 5, keyword: kw }),
    listCustomer({ current: 1, size: 5, keyword: kw }),
    listSupplier({ current: 1, size: 5, keyword: kw })
  ])
  if (mySeq !== seq) return // 竞态保护：只接受最新一次输入的结果

  // 单据：仅保留"有编辑页路由 + 登录人有该菜单权限"的类型
  if (bills.status === 'fulfilled' && Array.isArray(bills.value)) {
    billItems.value = bills.value
      .map(b => {
        const prefix = billMenuPrefix(b.docType)
        if (!prefix || !menuAllowed(prefix)) return null
        const path = resolveBillRoute(b.docType, b.id)
        if (!path) return null
        const subBits = [b.name, dateOnly(b.date), normalizeStatus(b.status)].filter(Boolean)
        return { key: `${b.docType}-${b.id}`, main: b.no, sub: subBits.join(' · '), exact: !!b.exact, path }
      })
      .filter(Boolean)
      .slice(0, 5)
  } else if (bills.status === 'rejected') {
    billFailed.value = true
  }

  materialItems.value = []
  if (mats.status === 'fulfilled' && Array.isArray(mats.value?.records) && menuAllowed('/basedata/material')) {
    materialItems.value = mats.value.records
      .map(m => ({
        key: `mat-${m.materialId}`,
        main: m.materialName || m.materialCode || '',
        sub: [m.materialCode, m.specification].filter(Boolean).join(' / '),
        path: `/basedata/material/edit/${m.materialId}`
      }))
      .filter(x => x.main)
      .slice(0, 5)
  } else if (mats.status === 'rejected') {
    materialFailed.value = true
  }

  customerItems.value = []
  if (cuss.status === 'fulfilled' && Array.isArray(cuss.value?.records) && menuAllowed('/basedata/customer')) {
    customerItems.value = cuss.value.records
      .map(c => ({
        key: `cus-${c.customerId}`,
        main: c.customerName || c.customerCode || '',
        sub: c.customerCode || '',
        path: `/basedata/customer/edit/${c.customerId}`
      }))
      .filter(x => x.main)
      .slice(0, 5)
  } else if (cuss.status === 'rejected') {
    customerFailed.value = true
  }

  supplierItems.value = []
  if (sups.status === 'fulfilled' && Array.isArray(sups.value?.records) && menuAllowed('/basedata/supplier')) {
    supplierItems.value = sups.value.records
      .map(s => ({
        key: `sup-${s.supplierId}`,
        main: s.supplierName || s.supplierCode || '',
        sub: s.supplierCode || '',
        path: `/basedata/supplier/edit/${s.supplierId}`
      }))
      .filter(x => x.main)
      .slice(0, 5)
  } else if (sups.status === 'rejected') {
    supplierFailed.value = true
  }

  // 统一编号：高亮样式与键盘跟随滚动都依赖 flatIndex（此前漏赋值导致 ↑↓ 高亮永不显示）
  let idx = 0
  for (const arr of [menuItems.value, billItems.value, materialItems.value, customerItems.value, supplierItems.value]) {
    for (const it of arr) it.flatIndex = idx++
  }

  loading.value = false
  activeIndex.value = 0
}

/** 失败分组点击重试：按当前关键词重新搜索 */
function retry() {
  const kw = (keyword.value || '').trim()
  if (!kw) return
  doSearch(kw, ++seq)
}

function resetResults() {
  menuItems.value = []
  billItems.value = []
  materialItems.value = []
  customerItems.value = []
  supplierItems.value = []
  billFailed.value = materialFailed.value = customerFailed.value = supplierFailed.value = false
  loading.value = false
}

// ============================== 键盘与跳转 ==============================
const panelRef = ref(null)

function move(delta) {
  const total = flatItems.value.length
  if (!total) return
  activeIndex.value = (activeIndex.value + delta + total) % total
  // 高亮项滚出面板可视区时跟随滚动（否则按 ↓ 走到后面几条会"看不见也滚不动"）
  nextTick(() => {
    const active = panelRef.value?.querySelector('.gs-item.is-active')
    active?.scrollIntoView({ block: 'nearest' })
  })
}

function go(item) {
  if (!item || !item.path) return
  close()
  keyword.value = ''
  resetResults()
  router.push(item.path)
}

function focusInput() {
  inputRef.value?.focus?.()
}

// ============================== 全局事件 ==============================
function onGlobalKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && String(e.key).toLowerCase() === 'k') {
    e.preventDefault()
    focusInput()
  }
}
function onDocClick(e) {
  if (wrapRef.value && !wrapRef.value.contains(e.target)) focused.value = false
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeydown)
  document.addEventListener('mousedown', onDocClick)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
  document.removeEventListener('mousedown', onDocClick)
})

defineExpose({ focusInput })
</script>

<style scoped lang="scss">
.global-search { position: relative; width: 280px; }
.global-search-input :deep(.el-input__inner) { height: 30px; line-height: 30px; }

.gs-panel {
  position: absolute; top: 38px; left: 0; right: 0; z-index: 3000;
  background: #fff; border: 1px solid #e4e7ed; border-radius: 8px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
  max-height: min(65vh, 560px); overflow-y: auto; overscroll-behavior: contain; padding: 4px 0 6px;
  /* 常显细滚动条：内容超出即可见可拖，不再依赖 Windows 浮动滚动条（悬停才出现，用户会以为不能滚） */
  scrollbar-width: thin; scrollbar-color: #dcdfe6 transparent;
  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: #dcdfe6; border-radius: 3px; }
  &::-webkit-scrollbar-thumb:hover { background: #c0c4cc; }
  &::-webkit-scrollbar-track { background: transparent; }
}
.gs-group-title {
  padding: 7px 12px 3px; font-size: 12px; color: #909399;
  display: flex; align-items: center;
}
.gs-failed { color: #f56c6c; margin-left: 6px; cursor: pointer; &:hover { text-decoration: underline; } }
.gs-item {
  display: flex; align-items: baseline; gap: 8px;
  padding: 7px 12px; cursor: pointer; font-size: 13px; line-height: 1.4;
  &:hover, &.is-active { background: rgba(64, 158, 255, 0.08); }
}
.gs-item-main { color: #303133; flex-shrink: 0; max-width: 55%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gs-item-sub { color: #909399; font-size: 12px; flex: 1; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gs-exact {
  flex-shrink: 0; align-self: center; font-size: 11px; color: #409eff;
  border: 1px solid rgba(64, 158, 255, 0.4); border-radius: 3px; padding: 0 4px; line-height: 16px;
}
.gs-empty, .gs-loading { padding: 18px 0; text-align: center; color: #909399; font-size: 13px; }

.gs-fade-enter-active, .gs-fade-leave-active { transition: opacity 0.12s ease; }
.gs-fade-enter-from, .gs-fade-leave-to { opacity: 0; }
</style>
