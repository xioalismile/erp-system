<template>
  <el-container class="main-layout">
    <el-container class="inner-layout">
    <el-aside v-if="!isSettingsPage" :width="collapsed ? '64px' : '220px'" class="sidebar" style="flex-shrink:0; min-width: 64px; border-right: 1px solid rgba(255,255,255,0.06);">
      <div class="logo">
        <img v-if="appConfig.resolvedLogoUrl" :src="appConfig.resolvedLogoUrl" class="logo-img" alt="logo" />
        <el-icon v-else :size="24" color="#fff"><Cpu /></el-icon>
        <span v-show="!collapsed" class="logo-text">{{ appConfig.systemName }}</span>
      </div>
      <el-menu
        :default-active="$route.path"
        :collapse="collapsed"
        router
        class="menu"
        unique-opened
      >
        <template v-for="group in menuGroups" :key="group.key">
          <!-- 单项菜单（如工作台） -->
          <el-menu-item v-if="group.isSingle" :index="group.items[0].path">
            <el-icon><component :is="resolveIcon(group.icon)" /></el-icon>
            <span>{{ getGroupName(group.key) }}</span>
          </el-menu-item>
          <!-- 分组菜单 -->
          <el-sub-menu v-else :index="group.key">
            <template #title>
              <el-icon><component :is="resolveIcon(group.icon)" /></el-icon>
              <span>{{ getGroupName(group.key) }}</span>
            </template>
            <template v-for="item in group.items" :key="item.itemKey || item.path">
              <!-- 三级子菜单：item.children 存在时渲染为可展开的子菜单 -->
              <el-sub-menu v-if="item.children && item.children.length" :index="item.itemKey">
                <template #title>{{ getItemName(group.key, item.itemKey, item.label) }}</template>
                <el-menu-item
                  v-for="child in item.children"
                  :key="child.path"
                  :index="child.path"
                >{{ getItemName(group.key, child.itemKey, child.label) }}</el-menu-item>
              </el-sub-menu>
              <!-- 普通菜单项 -->
              <el-menu-item
                v-else
                :index="item.path"
              >{{ getItemName(group.key, item.itemKey, item.label) }}</el-menu-item>
            </template>
          </el-sub-menu>
        </template>
      </el-menu>
      <!-- 无菜单权限空态：仅登录后没有任何菜单权限时提示（不做权限伪造） -->
      <div v-if="hasNoMenuPermission && !collapsed" class="menu-empty-tip">
        <el-icon><Warning /></el-icon>
        <p>{{ $t('layout.noMenu', '当前账号暂无可访问的菜单') }}</p>
        <p class="menu-empty-sub">{{ $t('layout.noMenuSub', '请联系管理员在「设置中心 → 角色权限」中分配') }}</p>
      </div>
    </el-aside>

    <el-container direction="vertical">
      <el-header v-if="!isSettingsPage" class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" :size="20" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" /><Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.title">{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
          <GlobalSearch class="header-search" />
        </div>
        <div class="header-right">
          <LanguageSwitch />
          <NotificationBell />
          <el-button v-if="hasTutorial" size="small" type="primary" plain v-permission="perm('view')" class="tutorial-btn" @click="startTutorial">
            <el-icon><Guide /></el-icon>
            <span>{{ $t('layout.tutorial', '新手教程') }}</span>
          </el-button>
          <el-tooltip v-if="canAccessSettings && !settingsHidden" content="设置中心" placement="bottom">
            <el-icon class="header-icon-btn" :size="18" @click="router.push('/system/settings')">
              <Setting />
            </el-icon>
          </el-tooltip>
          <el-dropdown>
            <span class="user-info">
              <el-avatar :size="28" style="background: var(--erp-primary, #409EFF)">{{ (realName || username || 'U').charAt(0) }}</el-avatar>
              <span class="username">{{ realName || username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/dashboard')">{{ $t('common.workbench', '工作台') }}</el-dropdown-item>
                <el-dropdown-item v-if="hasTutorial" @click="startTutorial">
                  <el-icon><Guide /></el-icon> {{ $t('layout.tutorial', '新手教程') }}
                </el-dropdown-item>
                <el-dropdown-item v-if="canAccessSettings && !settingsHidden" @click="router.push('/system/settings')">{{ $t('common.settingsCenter', '设置中心') }}</el-dropdown-item>
                <el-dropdown-item v-if="tenantName" disabled>
                  <el-icon><OfficeBuilding /></el-icon> {{ $t('layout.currentTenant', '当前') }}: {{ tenantName }}
                </el-dropdown-item>
                <el-dropdown-item @click="switchTenant">
                  <el-icon><Switch /></el-icon> {{ $t('common.switchCompany', '切换公司') }}
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">{{ $t('common.logout', '退出登录') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <!-- 设置中心专用：精简顶部条（库管王风格：折叠按钮 + 面包屑 + 用户） -->
      <el-header v-else class="header header-settings">
        <div class="header-left">
          <el-icon class="collapse-btn" :size="20" @click="goBack">
            <Back />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ $route.meta.title || $t('common.settingsCenter', '设置中心') }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <LanguageSwitch />
          <NotificationBell />
          <el-button v-if="hasTutorial" size="small" type="primary" plain v-permission="perm('view')" class="tutorial-btn" @click="startTutorial">
            <el-icon><Guide /></el-icon>
            <span>新手教程</span>
          </el-button>
          <el-tooltip content="刷新当前页" placement="bottom">
            <el-icon class="header-icon-btn" :size="18" @click="router.go(0)">
              <Refresh />
            </el-icon>
          </el-tooltip>
          <el-dropdown>
            <span class="user-info">
              <el-avatar :size="28" style="background: var(--erp-primary, #409EFF)">{{ (realName || username || 'U').charAt(0) }}</el-avatar>
              <span class="username">{{ realName || username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/dashboard')">{{ $t('common.workbench', '工作台') }}</el-dropdown-item>
                <el-dropdown-item v-if="hasTutorial" @click="startTutorial">
                  <el-icon><Guide /></el-icon> {{ $t('layout.tutorial', '新手教程') }}
                </el-dropdown-item>
                <el-dropdown-item @click="router.go(0)">{{ $t('common.refresh', '刷新页面') }}</el-dropdown-item>
                <el-dropdown-item v-if="tenantName" disabled>
                  <el-icon><OfficeBuilding /></el-icon> {{ $t('layout.currentTenant', '当前') }}: {{ tenantName }}
                </el-dropdown-item>
                <el-dropdown-item @click="switchTenant">
                  <el-icon><Switch /></el-icon> {{ $t('common.switchCompany', '切换公司') }}
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">{{ $t('common.logout', '退出登录') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <!-- 多标签页栏：仅业务页面显示，设置中心隐藏 -->
      <TagsView v-if="!isSettingsPage" />
      <el-main :class="['content', { 'content-settings': isSettingsPage }]" style="position:relative;">
        <router-view v-slot="{ Component, route: viewRoute }">
          <keep-alive :include="tagsStore.cachedViewNames">
            <component :is="Component" :key="keepAliveKey(viewRoute)" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
    </el-container>

    <AiAssistant v-if="!isSettingsPage" />

    <!-- 全局弹窗提醒（设置中心→弹窗设置 控制）：设置中心页也保持监听，保存后立即生效 -->
    <PopupNotify />

    <!-- 新客户开账向导（2026-10-12 独立全屏分步界面，替代"首登弹小楷"）：管理员&未引导时出现 -->
    <OnboardingWizard v-if="onboardingWizardVisible" @finished="onboardingWizardVisible = false" />

    <!-- 新手教程组件（全局接入，路由自动匹配） -->
    <OnboardingTutorial
      v-model:visible="tutorialVisible"
      :steps="tutorialSteps"
      @finish="onTutorialFinish"
      @skip="onTutorialSkip"
    />
  </el-container>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  DataLine, Files, User, ShoppingCart, Goods, Box, Tools,
  Cpu, TrendCharts, Setting, Connection, ArrowDown, Fold, Expand, Back, Refresh, PieChart, Guide,
  Switch, OfficeBuilding, Promotion, Warning, DataAnalysis
} from '@element-plus/icons-vue'
import AiAssistant from '../components/AiAssistant.vue'
import OnboardingWizard from '../components/OnboardingWizard.vue'
import NotificationBell from '../components/NotificationBell.vue'
import PopupNotify from '../components/PopupNotify.vue'
import GlobalSearch from '../components/GlobalSearch.vue'
import { initVoiceNotify } from '../utils/voiceNotify'
import { VOICE_FEATURES_ENABLED } from '../config/featureFlags'
import LanguageSwitch from '../components/LanguageSwitch.vue'
import OnboardingTutorial from '../components/OnboardingTutorial.vue'
import TagsView from '../components/TagsView.vue'
import { t, reloadFieldLabelsForScope } from '@/i18n'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { useUiSizeStore } from '@/stores/uiSize'
import { useTagsViewStore } from '@/stores/tagsView'
import { useAppConfigStore } from '@/stores/appConfig'
import { getMenuGroupsByRoutePaths } from '@/config/menuConfig'
import { getSysConfigByGroup } from '@/api/system/sysConfig'
import { getOnboardingStepsByRoute, isEditPageRoute } from '@/utils/onboardingSteps'
import { isAddPagePath } from '@/utils/addPage'
import { usePagePerm } from '@/utils/pagePerm'
// 角色字段权限「必填星号」全局标注（机制层）：路由级自动生效，页面无需各自接入
import { applyFieldRequiredMark, resolveFieldPageKey } from '@/utils/fieldRequiredMark'
import { getAvailablePages } from '@/api/system/fieldPermission'

const perm = usePagePerm('system:settings')


const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const themeStore = useThemeStore()
const uiSizeStore = useUiSizeStore()
const tagsStore = useTagsViewStore()
const appConfig = useAppConfigStore()
const collapsed = ref(false)
const username = computed(() => userStore.username || localStorage.getItem('username') || t('common.user', '用户'))
const realName = computed(() => userStore.realName || localStorage.getItem('realName') || '')
const tenantName = computed(() => userStore.tenantName || localStorage.getItem('tenantName') || '')

// 切换公司：回到公司选择页面
async function switchTenant() {
  try {
    // 调 /api/tenant/my 获取最新公司列表
    const request = (await import('../utils/request')).default
    const myTenants = await request.get('/tenant/my').catch(() => null)
    if (myTenants && myTenants.length > 0) {
      localStorage.setItem('pendingTenants', JSON.stringify(myTenants))
      localStorage.setItem('realName', realName.value)
      localStorage.setItem('tenantSwitchBack', '1')
      router.push('/tenant-select')
      return
    }
  } catch (e) {}
  // fallback: 如果接口失败，用缓存或登出
  const saved = localStorage.getItem('pendingTenants')
  if (saved) {
    router.push('/tenant-select')
  } else {
    handleLogout()
  }
}

// 设置中心走库管王风格（隐藏主菜单）
const isSettingsPage = computed(() => route.path === '/system/settings')

/**
 * 设置中心入口可见性：与路由守卫 /system 放行口径完全一致
 * （ADMIN / SUPER_ADMIN 角色，或 menuRoutePaths 含 /system/settings 菜单权限）。
 * 无权限用户直接隐藏齿轮图标与下拉入口，避免点进去被拦到 403 死胡同页。
 */
const canAccessSettings = computed(() => {
  try {
    const roles = Array.isArray(userStore.roles) ? userStore.roles : []
    if (roles.includes('ADMIN') || roles.includes('SUPER_ADMIN')) return true
    const paths = Array.isArray(userStore.menuRoutePaths) ? userStore.menuRoutePaths : []
    return paths.includes('/system/settings')
  } catch (_) {
    return false
  }
})

/**
 * 设置中心整体隐藏开关（构建期）：客户包 .env.customer 设 VITE_HIDDEN_SETTINGS=true
 * 时，齿轮图标与下拉「设置中心」入口都不渲染（发布到客户/生产环境不想让客户动设置的场景）。
 * 恢复 = 从环境文件删掉该变量重新构建，数据库与权限不受影响。
 */
const settingsHidden = import.meta.env.VITE_HIDDEN_SETTINGS === 'true'

// ===== 新手教程 =====
const tutorialVisible = ref(false)
const tutorialSteps = ref([])
// 仅在编辑页面显示教程按钮（支持 /new、/:id、/edit、/edit/:id 等多种路由格式）
const isEditPage = computed(() => isEditPageRoute(route.path))
const hasTutorial = computed(() => isEditPage.value && tutorialSteps.value.length > 0)

function startTutorial() {
  if (tutorialSteps.value.length === 0) return
  tutorialVisible.value = true
}

function onTutorialFinish() {
  // 教程完成：仅关闭（用户可随时通过按钮重新打开）
}

function onTutorialSkip() {
  // 跳过教程：仅关闭，不记录（用户可随时通过按钮重新打开）
}

// 监听路由变化，仅加载对应模块的教程步骤（不自动弹出）
// 教程只在用户手动点击"新手教程"按钮时才显示
watch(() => route.path, (newPath) => {
  const steps = getOnboardingStepsByRoute(newPath)
  tutorialSteps.value = steps || []
  // 切换路由时关闭正在进行的教程
  tutorialVisible.value = false
}, { immediate: true })

// ===== 全局表格布局修复（keep-alive 页签切换） =====
// 现象：带 fixed 列的 el-table 在页签切走（v-show 隐藏）再切回后，Element Plus 不会自动重算布局，
// fixed 克隆层错位，遮挡表格上方的搜索栏/按钮（全项目 100 处 fixed 列，含 CrudTable 与手写表格）。
// 修复：每次路由切换（页签切换）后，统一对当前页面所有表格调用 doLayout() 强制重算。
function doLayoutForTable(el) {
  try {
    // .el-table 根元素 → 沿组件链向上找 expose 了 doLayout 的 ElTable 实例
    let inst = el.__vueParentComponent
    for (let i = 0; i < 4 && inst; i++) {
      const fn = inst.exposed?.doLayout
      if (typeof fn === 'function') { fn(); return }
      inst = inst.parent
    }
  } catch (_) { /* 个别表格实例异常不影响其他表格 */ }
}

function relayoutAllTables() {
  document.querySelectorAll('.el-table').forEach(doLayoutForTable)
}

// ===== 角色字段权限「必填星号」全局标注（机制层，2026-09-28） =====
// 不再要求每个编辑页自己 import 接入：路由变化后自动推导"当前页面所属实体"（目标表名），
// 把该用户在该页面被配成必填的字段标上星号。新增页面只要路由能推导出表名即自动生效；
// 推导不出来的页面由发布闸门拦下（不允许靠肉眼漏掉）。
let knownFieldPageKeys = null
async function ensureFieldPageKeys() {
  if (knownFieldPageKeys) return knownFieldPageKeys
  try {
    const res = await getAvailablePages()
    const list = Array.isArray(res) ? res : []
    knownFieldPageKeys = list.map(p => p.key || p.dataSourceKey || p.code).filter(Boolean)
  } catch (e) {
    knownFieldPageKeys = []
  }
  return knownFieldPageKeys
}

let fieldMarkTimer = null
function scheduleFieldRequiredMark () {
  if (fieldMarkTimer) clearTimeout(fieldMarkTimer)
  // 延迟一点：等编辑页把表单/明细异步渲染出来（标注是幂等的，多跑一次只会有一次真实生效）
  fieldMarkTimer = setTimeout(async () => {
    try {
      const keys = await ensureFieldPageKeys()
      const pageKey = resolveFieldPageKey(route.path, keys)
      if (!pageKey) return
      applyFieldRequiredMark(pageKey, document)
    } catch (e) { /* 标注失败不影响页面 */ }
  }, 350)
}

watch(() => route.fullPath, () => {
  scheduleFieldRequiredMark()
  nextTick(() => {
    requestAnimationFrame(() => {
      relayoutAllTables()
      // 双帧保险：首帧容器尺寸恢复后再重算一次，覆盖 fixed 克隆层延迟同步的场景
      requestAnimationFrame(relayoutAllTables)
    })
  })
})

function goBack() { router.push('/dashboard') }

// 自定义菜单名称缓存
const customNames = reactive({})

const ICON_MAP = {
  DataLine, Files, User, ShoppingCart, Goods, Box, Tools,
  Cpu, TrendCharts, Setting, Connection, PieChart, Promotion, DataAnalysis
}
function resolveIcon(name) {
  return ICON_MAP[name] || DataLine
}

// 菜单渲染：严格按 menuRoutePaths 权限计算
// 使用后端返回的 menuRoutePaths（路径字符串列表）过滤菜单
// 注意：computed 必须是纯函数，禁止在此调用 rehydrate 等会修改 store 的方法
const menuGroups = computed(() => {
  let groups = []
  try {
    // 优先使用 menuRoutePaths（后端已根据版本和角色过滤）
    const routePaths = userStore.menuRoutePaths || []
    const roles = userStore.roles || []
    groups = getMenuGroupsByRoutePaths(routePaths, roles) || []
  } catch (_) {
    groups = []
  }
  return groups
})

/** 已登录但无任何菜单权限（显示空态提示，不伪造菜单） */
const hasNoMenuPermission = computed(() =>
  menuGroups.value.length === 0 && !!(userStore.token || localStorage.getItem('token'))
)

function getGroupName(groupKey) {
  const k = `menu_group_${groupKey}`
  const label = customNames[k] || (menuGroups.value.find(g => g.key === groupKey)?.label || groupKey)
  // 多语言（L1）：菜单分组名按中文名做翻译键，未命中回退中文
  return t(`menu.${label}`, label)
}

function getItemName(groupKey, itemKey, defaultLabel) {
  const k = `menu_name_${groupKey}_${itemKey}`
  const label = customNames[k] || defaultLabel
  // 多语言（L1）：菜单项名按中文名做翻译键，未命中回退中文
  return t(`menu.${label}`, label)
}

async function loadMenuNames() {
  try {
    const list = await getSysConfigByGroup('menu_name')
    if (Array.isArray(list)) {
      list.forEach(item => {
        if (item.configKey && item.configValue) {
          customNames[item.configKey] = item.configValue
        }
      })
    }
  } catch (e) {
    // 首次使用时可能没有配置，忽略错误
  }
}

// 权限自愈：已登录但本地权限数据是坏值（menuIds 为空且 roles 只有 'user' 兜底，
// 来自旧版本登录流程写入的脏数据）时，自动重拉 /auth/userinfo 修正 store 与 localStorage。
// 不伪造任何权限——接口拿不到有效数据就保持原状。
let _healing = false
async function healPermissionData() {
  if (_healing) return  // 防重入：避免并发触发反复写 store
  _healing = true
  try {
    const token = localStorage.getItem('token')
    if (!token) return
    const menuIds = Array.isArray(userStore.menuIds) ? userStore.menuIds : []
    const roles = Array.isArray(userStore.roles) ? userStore.roles : []
    const broken = menuIds.length === 0 || (roles.length === 1 && String(roles[0]).toLowerCase() === 'user')
    if (!broken) return
    // 走统一入口 fetchUserinfo（2026-10-12）：既修正 store 与 localStorage，
    // 也会顺带启动「权限变更监视」——此前这里直接裸调 userinfo 写缓存，
    // 导致从这条路径进来的会话不会自动感知按钮权限改动（表现为"必须重新登录才生效"）。
    await userStore.fetchUserinfo()
  } catch (e) {
    console.warn('权限自愈失败（不阻塞页面）', e && e.message)
  } finally {
    _healing = false
  }
}

/**
 * keep-alive 渲染 key：新增页"重开序号"变化时强制挂载全新实例
 * - 普通页/编辑页：序号恒为 0，key 等价于原来的 fullPath，缓存行为与旧版完全一致
 * - 新增页被再次点开（/xxx/new 重开）：序号 +1 → 不复用上次实例 → 表单是空白的
 * 序号由路由守卫在"二次点新增"时写入（见 router/index.js 的 handleAddPageReopen）
 */
function keepAliveKey(viewRoute) {
  if (!viewRoute) return ''
  return `${viewRoute.fullPath}@${tagsStore.nonceOf(viewRoute.fullPath)}`
}

/** 新增页路径判定（与路由守卫同用 utils/addPage 的一套口径：/new 或 /edit 无单号） */

/**
 * 新增页"未保存内容"侦测（全局通用，不需要改任何业务页面）：
 * 用户在新增页内有输入/选择/点操作按钮 → 标记该页"有未保存内容"；
 * 二次点「新增」时路由守卫据此弹确认框，避免静默丢弃已填内容。
 * 保存成功后由 utils/request.js 清除标记（写请求成功=已落库，不再提示未保存）。
 */
function onUserEditCapture(e) {
  try {
    if (!isAddPagePath(route.path)) return
    const el = e.target
    if (!el) return
    if (e.type === 'click') {
      // click 只认"操作按钮"（明细加行/删除行/带出数据等），避免点空白处也判为改动
      const btn = el.closest && el.closest('.el-button')
      if (!btn) return
      const txt = ((btn.innerText || btn.textContent || '') + '').trim()
      // 返回/取消/关闭 属于"离开页面"，不算改动了表单内容
      if (/^(取消|返回|关闭)/.test(txt)) return
    }
    tagsStore.markDirty(route.fullPath)
  } catch (_) { /* 标记失败不影响用户操作 */ }
}

onMounted(async () => {
  // 权限自愈（异步执行，不阻塞首屏渲染）：修复旧版本登录写入的坏权限数据
  healPermissionData()
  // 同步 localStorage → Pinia（页面刷新后保证 store 与磁盘一致）
  try { userStore.rehydrate && userStore.rehydrate(true) } catch (_) {}
  // 加载全局品牌配置（logo/名称/副标题/客服电话/版权），失败不阻塞后面逻辑
  try { await appConfig.loadPublicConfig() } catch (_) {}
  // 加载主题配置（先本地、再后端，最后注入 CSS 变量）
  themeStore.applyTheme()
  themeStore.loadFromServer()
  // 界面尺寸配置（设置中心 → 自定义 → 界面尺寸设置）：先用本地缓存/系统默认值注入 CSS 变量，
  // 再异步拉服务器配置覆盖；未配置时 = 改造前原值，界面零变化
  // 功能状态=未完成 → 构建期开关控制：仅本地开发（.env.development）开启，两个发布包不设置该变量即整块过滤
  if (import.meta.env.VITE_UI_SIZE_SETTINGS === 'true') {
    uiSizeStore.apply()
    uiSizeStore.loadFromServer()
  }
  loadMenuNames()
  // 初始化多标签页：注入 router/route 实例，扫描 affix 路由并监听路由变化
  tagsStore.init(router, route)
  // 初始化语音提醒（幂等）：与 AI 小助手组件生命周期解耦，设置中心页也能播报
  // 语音三功能被过滤的环境（客户包 VITE_VOICE_FEATURES=false）：不做初始化、不注册播报，全程静默
  if (VOICE_FEATURES_ENABLED) initVoiceNotify()
  // 新增页未保存内容侦测（捕获阶段监听原生事件，不依赖任何业务页面配合）
  document.addEventListener('input', onUserEditCapture, true)
  document.addEventListener('change', onUserEditCapture, true)
  document.addEventListener('click', onUserEditCapture, true)
  // 首屏也要标一次必填星号（路由 watch 只在切换时触发，直接刷新进编辑页时不会触发）
  scheduleFieldRequiredMark()
  // 字段显示名覆盖（2026-10-11 开账引导）：F5/重开页面后内存为空 → 必须重拉，
  // 否则自定义叫法"刷新就丢"（后端有值，前端没装载）。登录/切公司由 sessionCacheReset 负责。
  reloadFieldLabelsForScope()
  // 新客户开账向导（2026-10-12 独立分步界面）：管理员 & 引导状态=未引导 → 全屏向导；
  // 状态查不到/已完成/已跳过 → 不打扰。等身份就绪由向导组件内部处理（首屏后异步出现）。
  onboardingWizardVisible.value = true
})

// 开账向导：挂载即渲染，组件内部自查状态与角色（pending&管理员才显示界面，其余自隐藏）
const onboardingWizardVisible = ref(true)

onUnmounted(() => {
  document.removeEventListener('input', onUserEditCapture, true)
  document.removeEventListener('change', onUserEditCapture, true)
  document.removeEventListener('click', onUserEditCapture, true)
})

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}
</script>

<style scoped lang="scss">
.main-layout { height: 100vh; display: flex; flex-direction: column; }
.inner-layout { height: 100vh; flex: 1; }
.sidebar {
  background: var(--erp-sidebar-bg, #001529);
  transition: width 0.2s;
  overflow-x: hidden;
  overflow-y: auto;
  height: 100%;
}
.sidebar::-webkit-scrollbar { width: 4px; }
.sidebar::-webkit-scrollbar-thumb { background: #475569; border-radius: 2px; }
/* 无菜单权限空态提示 */
.menu-empty-tip {
  margin: 24px 12px;
  padding: 16px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.6;
  .el-icon { font-size: 28px; color: #64748b; }
  p { margin: 8px 0 0; }
  .menu-empty-sub { font-size: 12px; color: #64748b; }
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  .logo-img {
    height: 34px;
    width: 34px;
    object-fit: contain;
    border-radius: 6px;
    background: #fff;
    padding: 2px;
    flex-shrink: 0;
  }
  .logo-text {
    color: var(--erp-sidebar-text, #fff); font-size: 18px; font-weight: 600; white-space: nowrap;
  }
}
.menu {
  border-right: none;
  --el-menu-bg-color: var(--erp-sidebar-bg, #001529);
  --el-menu-text-color: var(--erp-sidebar-text, #cbd5e1);
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-active-color: var(--erp-primary, #409EFF);
  height: calc(100vh - 60px);
  overflow-y: auto;
}
.menu:not(.el-menu--collapse) {
  width: 220px;
}
.header {
  display: flex; align-items: center; justify-content: space-between;
  background: var(--erp-header-bg, #fff); border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  .header-left { display: flex; align-items: center; gap: 16px; }
  .collapse-btn { cursor: pointer; color: #606266; &:hover { color: var(--erp-primary, #409EFF); } }
  .header-right { display: flex; align-items: center; gap: 12px; .user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; } .header-icon-btn { display: inline-flex; align-items: center; justify-content: center; cursor: pointer; &:hover { color: var(--erp-primary, #409EFF); } } .tutorial-btn { flex-shrink: 0; padding: 6px 12px; font-size: 13px; animation: tutorial-pulse 2s ease-in-out infinite; } }
  .username { font-size: 14px; color: var(--erp-header-text, #303133); }
}
@keyframes tutorial-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.3); }
  50% { box-shadow: 0 0 8px 2px rgba(64, 158, 255, 0.5); }
}
.content {
  background: var(--erp-bg-page, #f0f2f5);
  // 内容区左右/上下留白随「界面密度」联动（设置中心 → 界面尺寸设置）；默认紧凑 = 8px
  padding: var(--erp-content-padding, 8px);
  overflow: auto;
  flex: 1;
  min-height: 0;
}
.content-settings {
  background: var(--erp-bg-page, #f0f2f5);
  padding: 0;
}
.header-settings {
  background: var(--erp-header-bg, #fff);
  border-bottom: 1px solid #e8e8e8;
}
:deep(.el-menu-item.is-active) {
  background: var(--erp-sidebar-active-bg, var(--erp-primary, #409EFF)) !important;
  color: var(--erp-sidebar-text, #fff) !important;
}
:deep(.el-menu--collapse .el-sub-menu__title span),
:deep(.el-menu--collapse .el-sub-menu .el-menu-item span) {
  display: none;
}
:deep(.el-menu--collapse .el-sub-menu__title .el-sub-menu__icon-arrow) {
  display: none;
}
</style>
