<template>
  <div class="kgw-settings">
    <!-- 第一列：一级分组（4 大类） -->
    <aside class="kgw-col1">
      <div
        v-for="group in visibleGroups"
        :key="group.key"
        class="col1-item"
        :class="{ active: activeGroupKey === group.key }"
        @click="selectGroup(group.key)"
      >
        <el-icon class="col1-icon"><component :is="group.icon" /></el-icon>
        <span class="col1-label">{{ $tf(group.label) }}</span>
      </div>
    </aside>

    <!-- 第二列：当前分组的子菜单 -->
    <aside class="kgw-col2">
      <div
        v-for="item in currentGroupItems"
        :key="item.key"
        class="col2-item"
        :class="{ active: activeItemKey === item.key }"
        @click="selectItem(item)"
      >
        <el-icon v-if="item.icon" class="col2-icon"><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </div>
    </aside>

    <!-- 第三列：内容区 -->
    <main class="kgw-col3">
      <div v-if="activeItem" class="col3-toolbar">
        <!-- AI 分组：进入具体功能后显示面包屑与返回总览（2026-09-19 改版） -->
        <el-button v-permission="perm('dlgCancel')"
          v-if="activeGroupKey === 'ai' && activeItemKey !== 'ai-overview'"
          size="small"
          text
          @click="backToAiOverview">← 返回总览</el-button>
        <span class="toolbar-title">
          <template v-if="activeGroupKey === 'ai'">AI / </template>{{ $tf(activeItem.label) }}
        </span>
        <el-button v-permission="perm('refreshCache')" size="small" @click="onRefreshCache" :loading="refreshing">
          <el-icon style="margin-right:4px"><Refresh /></el-icon>刷新缓存
        </el-button>
      </div>
      <AiSettingsOverview
        v-if="activeItemKey === 'ai-overview' && activeItem"
        :key="activeItem.key"
        :items="aiOverviewItems"
        @open="onOverviewOpen" />
      <component v-else-if="activeItem" :is="activeItem.component" :key="activeItem.key" embedded />
      <div v-else class="welcome-page">
        <div class="welcome-banner">
          <div class="welcome-illustration">
            <svg viewBox="0 0 320 240" xmlns="http://www.w3.org/2000/svg" class="kgw-3d-illust">
              <defs>
                <linearGradient id="floorGrad" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0" stop-color="#5b9dd9"/><stop offset="1" stop-color="#1e4d8b"/>
                </linearGradient>
                <linearGradient id="machineGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0" stop-color="#a3d2ff"/><stop offset="1" stop-color="#4f8ed1"/>
                </linearGradient>
                <linearGradient id="bubbleGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0" stop-color="#fff" stop-opacity="0.9"/><stop offset="1" stop-color="#cfe6ff" stop-opacity="0.5"/>
                </linearGradient>
              </defs>
              <polygon points="60,180 260,180 320,210 0,210" fill="url(#floorGrad)" opacity="0.5"/>
              <g>
                <polygon points="70,180 70,140 95,128 95,180" fill="url(#machineGrad)" stroke="#fff" stroke-width="1.5"/>
                <polygon points="95,180 95,128 120,140 120,180" fill="#7eb5ed" stroke="#fff" stroke-width="1.5"/>
                <text x="92" y="158" text-anchor="middle" fill="#fff" font-size="22" font-weight="700">1</text>
                <polygon points="120,180 120,140 145,128 145,180" fill="url(#machineGrad)" stroke="#fff" stroke-width="1.5"/>
                <polygon points="145,180 145,128 170,140 170,180" fill="#7eb5ed" stroke="#fff" stroke-width="1.5"/>
                <text x="142" y="158" text-anchor="middle" fill="#fff" font-size="22" font-weight="700">2</text>
                <polygon points="170,180 170,140 195,128 195,180" fill="url(#machineGrad)" stroke="#fff" stroke-width="1.5"/>
                <polygon points="195,180 195,128 220,140 220,180" fill="#7eb5ed" stroke="#fff" stroke-width="1.5"/>
                <text x="192" y="158" text-anchor="middle" fill="#fff" font-size="22" font-weight="700">3</text>
                <polygon points="220,180 220,140 245,128 245,180" fill="url(#machineGrad)" stroke="#fff" stroke-width="1.5"/>
                <polygon points="245,180 245,128 270,140 270,180" fill="#7eb5ed" stroke="#fff" stroke-width="1.5"/>
                <text x="242" y="158" text-anchor="middle" fill="#fff" font-size="22" font-weight="700">4</text>
                <polygon points="20,180 20,140 45,128 45,180" fill="url(#machineGrad)" stroke="#fff" stroke-width="1.5"/>
                <polygon points="45,180 45,128 70,140 70,180" fill="#7eb5ed" stroke="#fff" stroke-width="1.5"/>
                <text x="42" y="158" text-anchor="middle" fill="#fff" font-size="22" font-weight="700">5</text>
              </g>
              <g>
                <polygon points="100,128 220,128 230,110 90,110" fill="#3a7bc8" stroke="#fff" stroke-width="1.5"/>
                <rect x="160" y="80" width="20" height="30" fill="#5b9dd9" stroke="#fff" stroke-width="1"/>
                <polygon points="160,80 180,80 175,70 165,70" fill="#1e4d8b" stroke="#fff" stroke-width="1"/>
                <circle cx="170" cy="58" r="6" fill="url(#bubbleGrad)"/>
                <circle cx="178" cy="48" r="7" fill="url(#bubbleGrad)"/>
                <circle cx="166" cy="40" r="8" fill="url(#bubbleGrad)"/>
                <circle cx="180" cy="30" r="6" fill="url(#bubbleGrad)"/>
                <line x1="220" y1="110" x2="220" y2="80" stroke="#666" stroke-width="1.5"/>
                <polygon points="220,80 235,84 220,90" fill="#e74c3c"/>
              </g>
            </svg>
          </div>
          <div class="welcome-info-wrap">
            <h2 class="welcome-title">{{ userInfo.username || '您好' }}，欢迎您来到设置中心</h2>
            <div class="info-card">
              <table class="info-table">
                <tr><td class="info-label">{{ $tt("当前版本") }}</td><td class="info-value">{{ $tt("商贸版") }}</td></tr>
                <tr><td class="info-label">{{ $tt("用户量") }}</td><td class="info-value">{{ $tt("不限制") }}</td></tr>
                <tr><td class="info-label">{{ $tt("到期时间") }}</td><td class="info-value">2030-12-30</td></tr>
                <tr><td class="info-label">{{ $tt("付费代码") }}</td><td class="info-value">17996859</td></tr>
              </table>
            </div>
            <div class="contact-line">
              <el-icon class="contact-icon"><Phone /></el-icon>
              <span>客服电话 {{ appConfig.servicePhone }}</span>
            </div>
            <el-button v-permission="perm('refreshCache')" type="primary" size="small" @click="onRefreshCache" :loading="refreshing" class="refresh-cache-btn">
              <el-icon style="margin-right:4px"><Refresh /></el-icon>刷新系统缓存
            </el-button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:settings')
import { ref, computed, markRaw, onMounted, nextTick, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import {
  Phone, Setting, User, Lock, Bell, PriceTag,
  Printer, Edit, List, DataLine, Document, Tools,
  Box, Ticket, Postcard, Calendar, ChatLineRound, ChatDotRound,
  Notification, TrendCharts, Coin, Key, Brush, Histogram, Menu, Refresh, Van, Delete, Switch, MagicStick, Connection
} from '@element-plus/icons-vue'

import SystemBasicInfo from './kgw/SystemBasicInfo.vue'
import KgwSysParam from './kgw/KgwSysParam.vue'
import RoleList from './RoleList.vue'
import EmployeePermission from './EmployeePermission.vue'
import DataMaintain from './DataMaintain.vue'
import KgwInventoryWarning from './kgw/KgwInventoryWarning.vue'
import SalePriceStrategyList from '../price/SalePriceStrategyList.vue'
import PurchasePriceStrategyList from '../price/PurchasePriceStrategyList.vue'
import SerialNoConfig from './SerialNoConfig.vue'
import KgwApproval from './kgw/KgwApproval.vue'
import KgwBatchNo from './kgw/KgwBatchNo.vue'
import PrintTemplateList from './PrintTemplateList.vue'
import CustomFieldConfig from './CustomFieldConfig.vue'
import AiDataPolicy from './AiDataPolicy.vue'
import AiToolPermission from './AiToolPermission.vue'
import AiTokenUsage from './AiTokenUsage.vue'
import { tt } from '@/i18n'
// @feature FEAT-AIKB-001
import AiKnowledge from './AiKnowledge.vue'
// 设置中心「AI」分组新增组件（2026-09-19）：总览卡片 / 助手设置面板 / 协议与免责声明
import AiAssistantSettings from './AiAssistantSettings.vue'
import AiDisclaimer from './AiDisclaimer.vue'
import AiSettingsOverview from './AiSettingsOverview.vue'
import I18nManager from './i18n/I18nManager.vue'
import LogList from './LogList.vue'
import KgwCommissionRate from './kgw/KgwCommissionRate.vue'
import KgwCrmWarning from './kgw/KgwCrmWarning.vue'
import KgwNotification from './kgw/KgwNotification.vue'
import KgwPopupNotice from './kgw/KgwPopupNotice.vue'
import ThemeSetting from './ThemeSetting.vue'
import KgwMenuName from './kgw/KgwMenuName.vue'
import KgwLogisticsConfig from './kgw/KgwLogisticsConfig.vue'
import KgwAutoPush from './kgw/KgwAutoPush.vue'
// 开账引导配套（2026-10-11）：全站字段显示名自定义
import FieldNameSetting from './FieldNameSetting.vue'
import { refreshSysConfig } from '@/api/system/sysConfig'
import { useAppConfigStore } from '@/stores/appConfig'
import { ElMessage } from 'element-plus'

const appConfig = useAppConfigStore()

// 5 大分组（一级菜单）
const settingGroups = [
  {
    key: 'system', label: '系统', icon: Tools,
    items: [
      { key: 'basic-info', label: '基础信息设置', icon: Setting, component: markRaw(SystemBasicInfo) },
      { key: 'sys-param', label: '系统参数设置', icon: Setting, component: markRaw(KgwSysParam) },
      { key: 'role-perm', label: '角色权限设置', icon: User, component: markRaw(RoleList) },
      { key: 'employee-perm', label: '员工权限补充', icon: Lock, component: markRaw(EmployeePermission) },
      { key: 'data-maintain', label: '数据维护', icon: Delete, component: markRaw(DataMaintain) },
      // 多语言（L1）：语言管理（超管维护译文；语言切换入口在右上角用户区）
      { key: 'i18n-manager', label: '语言管理', icon: Switch, component: markRaw(I18nManager) }
    ]
  },
  {
    key: 'business', label: '业务', icon: DataLine,
    items: [
      { key: 'inventory-warning', label: '库存预警设置', icon: Bell, component: markRaw(KgwInventoryWarning) },
      { key: 'crm-warning', label: 'CRM预警设置', icon: Bell, component: markRaw(KgwCrmWarning) },
      { key: 'sale-price', label: '销售价格策略', icon: PriceTag, component: markRaw(SalePriceStrategyList) },
      { key: 'purchase-price', label: '采购价格策略', icon: PriceTag, component: markRaw(PurchasePriceStrategyList) },
      { key: 'commission-rate', label: '提成率配置', icon: Coin, component: markRaw(KgwCommissionRate) },
      { key: 'logistics-config', label: '物流配置', icon: Van, component: markRaw(KgwLogisticsConfig) },
      { key: 'auto-push', label: '自动下推设置', icon: Connection, component: markRaw(KgwAutoPush) }
    ]
  },
  {
    key: 'custom', label: '自定义', icon: Edit,
    items: [
      { key: 'menu-name', label: '菜单名称设置', icon: Menu, component: markRaw(KgwMenuName) },
      { key: 'field-name', label: '字段名称设置', icon: Edit, component: markRaw(FieldNameSetting) },
      { key: 'theme-setting', label: '主题设置', icon: Brush, component: markRaw(ThemeSetting) },
      // 界面尺寸设置：功能状态=未完成 → 构建期开关控制（仅 .env.development 开启 VITE_UI_SIZE_SETTINGS）；
      // 发布包不设置该变量 → 本项整体剔除，组件也走懒加载不会被打进发布包
      ...(import.meta.env.VITE_UI_SIZE_SETTINGS === 'true'
        ? [{
            key: 'ui-size', label: '界面尺寸设置', icon: Histogram,
            component: markRaw(defineAsyncComponent(() => import('./UiSizeSetting.vue')))
          }]
        : []),
      { key: 'notification-setting', label: '消息通知设置', icon: Bell, component: markRaw(KgwNotification) },
      { key: 'popup-setting', label: '弹窗设置', icon: ChatDotRound, component: markRaw(KgwPopupNotice) },
      { key: 'serial-no', label: '自动编号设置', icon: Document, component: markRaw(SerialNoConfig) },
      { key: 'approval', label: '审批设置', icon: Notification, component: markRaw(KgwApproval) },
      { key: 'batch-no', label: '批次编号设置', icon: Calendar, component: markRaw(KgwBatchNo) },
      { key: 'print-setting', label: '打印设置', icon: Printer, component: markRaw(PrintTemplateList) },
      { key: 'custom-field', label: '自定义字段设置', icon: Postcard, component: markRaw(CustomFieldConfig) }
    ]
  },
  {
    key: 'log', label: '日志', icon: List,
    items: [
      { key: 'operate-log', label: '操作日志', icon: ChatLineRound, component: markRaw(LogList) }
    ]
  },
  {
    // AI 分组（2026-09-19 改版）：第二列只保留「AI 设置总览」一个菜单页，
    // 其余 AI 功能收进总览页的卡片（见下方 aiSubItems），不再在左栏平铺。
    key: 'ai', label: 'AI', icon: MagicStick,
    items: [
      { key: 'ai-overview', label: 'AI 设置总览', icon: MagicStick, component: markRaw(AiSettingsOverview) }
    ]
  }
]

// AI 组子功能：不进第二列，只作为「AI 设置总览」的卡片 + 内容区目标页。
// item key / route_path 口径与菜单权限完全一致（/system/settings?item=<key>），逐项按权限过滤。
const aiSubItems = [
  { key: 'ai-assistant-settings', label: 'AI 助手设置', icon: Setting, component: markRaw(AiAssistantSettings) },
  { key: 'ai-disclaimer', label: '协议与免责声明', icon: Document, component: markRaw(AiDisclaimer) },
  { key: 'ai-data-policy', label: 'AI 数据使用政策', icon: Key, component: markRaw(AiDataPolicy) },
  { key: 'ai-tool-permission', label: 'AI 工具权限', icon: Key, component: markRaw(AiToolPermission) },
  { key: 'ai-token-usage', label: 'AI 用量管理', icon: Key, component: markRaw(AiTokenUsage) },
  // @feature FEAT-AIKB-001
  { key: 'ai-knowledge', label: 'AI 知识库管理', icon: Key, component: markRaw(AiKnowledge) }
]

const activeGroupKey = ref('system')   // 默认选中"系统"
const activeItemKey = ref('basic-info') // 默认选中"基础信息设置"

// ===== 权限过滤：根据 menuRoutePaths 控制子功能可见性 =====
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
const allowedPaths = computed(() => {
  const paths = Array.isArray(userStore.menuRoutePaths) ? userStore.menuRoutePaths : []
  const roles = Array.isArray(userStore.roles) ? userStore.roles : []
  // ADMIN/SUPER_ADMIN / 租户管理员(is_admin=1，角色码不一定是 ADMIN_*)或通配符权限：全部可见
  if (roles.includes('ADMIN') || roles.includes('SUPER_ADMIN') || userStore.isAdmin || paths.includes('*')) {
    return null // null 表示全部放行
  }
  return new Set(paths)
})
function canAccessItem(item) {
  if (allowedPaths.value === null) return true
  // 子功能路径格式：/system/settings?item=<key>
  return allowedPaths.value.has(`/system/settings?item=${item.key}`)
}
// 过滤后的分组（仅保留有权限的子项；分组下无可见子项则隐藏整个分组）
// AI 组特例（2026-09-19）：子功能收在「AI 设置总览」卡片里、不进第二列，
// 因此只要总览本身或任一 AI 子项有权限，就显示该分组（第二列仍只有「AI 设置总览」一项）。
const visibleGroups = computed(() => {
  const groups = settingGroups.map(g => ({
    ...g,
    items: g.items.filter(i => canAccessItem(i))
  }))
  const aiGroup = groups.find(g => g.key === 'ai')
  if (aiGroup && aiGroup.items.length === 0 && aiSubItems.some(canAccessItem)) {
    const overview = settingGroups.find(g => g.key === 'ai').items.find(i => i.key === 'ai-overview')
    if (overview) aiGroup.items = [overview]
  }
  return groups.filter(g => g.items.length > 0)
})

const currentGroup = computed(() => visibleGroups.value.find(g => g.key === activeGroupKey.value))
const currentGroupItems = computed(() => currentGroup.value?.items || [])

// AI 组可见子项（总览卡片与内容区共用，按权限过滤）
const visibleAiSubItems = computed(() => aiSubItems.filter(canAccessItem))
const allItems = computed(() => {
  const base = visibleGroups.value.flatMap(g => g.items.map(i => ({ ...i, groupKey: g.key, groupLabel: g.label })))
  const subs = visibleAiSubItems.value.map(i => ({ ...i, groupKey: 'ai', groupLabel: 'AI' }))
  return base.concat(subs)
})
const activeItem = computed(() => allItems.value.find(i => i.key === activeItemKey.value))

// ===== AI 组「AI 设置总览」卡片数据（AI 子功能，逐项按权限过滤）=====
const AI_ITEM_DESC = {
  'ai-assistant-settings': '管理小楷助手使用的模型服务商、密钥与展示播报偏好',
  'ai-disclaimer': '查看 AI 服务使用协议与免责声明全文及当前账号同意状态',
  'ai-data-policy': '控制 AI 调用过程中的数据脱敏、日志记录与严格模式',
  'ai-tool-permission': '配置 AI 小助手可调用的工具能力范围',
  'ai-token-usage': '查看 AI 调用量与额度使用情况',
  'ai-knowledge': '维护 AI 知识库文档与向量化内容'
}
const AI_NEW_ITEM_KEYS = ['ai-assistant-settings', 'ai-disclaimer']
const aiOverviewItems = computed(() => visibleAiSubItems.value.map(i => ({
  key: i.key,
  label: i.label,
  desc: AI_ITEM_DESC[i.key] || '',
  isNew: AI_NEW_ITEM_KEYS.includes(i.key)
})))
function onOverviewOpen(key) {
  const it = allItems.value.find(i => i.key === key)
  if (it) selectItem(it)
}
/** 回到「AI 设置总览」 */
function backToAiOverview() {
  activeGroupKey.value = 'ai'
  activeItemKey.value = 'ai-overview'
}

const userInfo = ref({})
const route = useRoute()
onMounted(async () => {
  try {
    await appConfig.loadPublicConfig()
  } catch (_) {}
  try { userInfo.value = JSON.parse(localStorage.getItem('userInfo') || '{}') } catch (e) {}
  // 支持通过路由 query 跳转：?group=business&item=commission-rate
  // 也支持只带 item（左侧菜单「AI 设置总览」等入口只带 item）→ 自动反查所属分组
  const q = route.query
  if (q && q.item && allItems.value.some(i => i.key === q.item)) {
    const target = allItems.value.find(i => i.key === q.item)
    activeGroupKey.value = target?.groupKey || (q.group && settingGroups.some(g => g.key === q.group) ? q.group : 'system')
    activeItemKey.value = q.item
  } else if (q && q.group && settingGroups.some(g => g.key === q.group)) {
    activeGroupKey.value = q.group
    if (q.item && allItems.value.some(i => i.key === q.item)) {
      activeItemKey.value = q.item
    }
  }
  // 权限修正：若当前选中项无权限，自动跳到第一个可见子项
  await nextTick()
  if (!activeItem.value && visibleGroups.value.length > 0) {
    const firstGroup = visibleGroups.value[0]
    activeGroupKey.value = firstGroup.key
    activeItemKey.value = firstGroup.items[0]?.key || ''
  }
})

function selectGroup(key) {
  activeGroupKey.value = key
  // AI 分组（2026-09-19）：该组第二列只有「AI 设置总览」一项，点分组直接进总览，不再先给欢迎页
  if (key === 'ai') {
    const first = currentGroupItems.value[0]
    activeItemKey.value = first ? first.key : ''
    return
  }
  activeItemKey.value = ''  // 切分组清空右侧内容（显示欢迎页）
}
function selectItem(item) {
  activeItemKey.value = item.key
}

// 刷新系统配置缓存
const refreshing = ref(false)
async function onRefreshCache() {
  refreshing.value = true
  try {
    await refreshSysConfig()
    await appConfig.refreshConfig()
    ElMessage.success(tt('系统配置缓存已刷新'))
  } catch (e) {
    ElMessage.error(tt('刷新缓存失败'))
  } finally {
    refreshing.value = false
  }
}
</script>

<style scoped lang="scss">
.kgw-settings {
  display: flex;
  height: 100%;
  background: #f0f2f5;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
}

/* ===== 第一列：一级分组 ===== */
.kgw-col1 {
  width: 100px;
  flex-shrink: 0;
  background: #001529;
  display: flex;
  flex-direction: column;
  padding-top: 12px;
}
.col1-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 18px 8px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.65);
  border-left: 3px solid transparent;
  font-size: 13px;
  &:hover {
    color: #fff;
    background: rgba(255, 255, 255, 0.05);
  }
  &.active {
    background: #1890ff;
    color: #fff;
    border-left-color: #1890ff;
  }
}
.col1-icon { font-size: 22px; }
.col1-label { font-size: 13px; }

/* ===== 第二列：子菜单 ===== */
.kgw-col2 {
  width: 200px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  padding-top: 8px;
  overflow-y: auto;
}
.col2-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  line-height: 40px;
  padding: 0 16px;
  cursor: pointer;
  font-size: 13px;
  color: #303133;
  border-left: 3px solid transparent;
  &:hover {
    background: #f0f5ff;
    color: #1890ff;
  }
  &.active {
    background: #e6f7ff;
    color: #1890ff;
    border-left-color: #1890ff;
    font-weight: 600;
  }
}
.col2-icon { font-size: 14px; }

/* ===== 第三列：内容区 ===== */
.kgw-col3 {
  flex: 1;
  overflow: auto;
  background: #f0f2f5;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.col3-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 40px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
  .toolbar-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }
}
.refresh-cache-btn {
  margin-top: 12px;
}

/* ===== 欢迎页 ===== */
.welcome-page {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
  padding: 24px;
}
.welcome-banner {
  background: #cfe6ff;
  border-radius: 4px;
  padding: 30px 40px;
  display: flex;
  align-items: center;
  gap: 40px;
  min-width: 760px;
  max-width: 1000px;
}
.welcome-illustration {
  width: 320px;
  flex-shrink: 0;
}
.kgw-3d-illust {
  width: 100%;
  height: auto;
  display: block;
}
.welcome-info-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 18px;
  align-items: center;
}
.welcome-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
  text-align: center;
}
.info-card {
  background: #fff;
  border-radius: 4px;
  padding: 18px 28px;
  min-width: 280px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}
.info-table {
  width: 100%;
  border-collapse: collapse;
  td {
    padding: 6px 0;
    font-size: 13px;
  }
  .info-label {
    color: #606266;
    width: 90px;
  }
  .info-value {
    color: #303133;
    font-weight: 500;
  }
}
.contact-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #303133;
}
.contact-icon {
  color: #1890ff;
  font-size: 16px;
}
</style>
