<template>
  <div class="tenant-select-wrapper">
    <div class="tenant-select-card">
      <h2>👋 欢迎回来，{{ realName }}，请选择公司</h2>
      <p class="subtitle">{{ $tt("不同公司之间数据完全独立，请谨慎选择") }}</p>
      <div class="tenant-list">
        <div v-for="t in tenants" :key="t.tenantId"
             class="tenant-item"
             :class="{ active: selectedId === t.tenantId }"
             @click="handleSelect(t)">
          <div class="tenant-icon">🏢</div>
          <div class="tenant-info">
            <div class="tenant-name">{{ t.tenantName || '未知名称' }}
              <span v-if="t.expired === true || t.status === 'expired'" class="tenant-expired">{{ $tt("已过期") }}</span>
            </div>
            <div class="tenant-code">{{ t.tenantCode }}</div>
            <div class="tenant-last-login" v-if="t.lastLoginTime">上次登录: {{ formatTime(t.lastLoginTime) }}</div>
            <div class="tenant-last-login" v-else>{{ $tt("首次登录") }}</div>
          </div>
          <div class="tenant-arrow">→</div>
        </div>
      </div>
      <el-button v-if="loading" type="primary" loading style="margin-top: 20px">{{ $tt("正在进入...") }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useAppConfigStore } from '@/stores/appConfig'
import { useUserStore } from '@/stores/user'
import { tt } from '@/i18n'

const router = useRouter()
const route = useRoute()
const appConfig = useAppConfigStore()
const userStore = useUserStore()
const tenants = ref([])
const realName = ref('')
const selectedId = ref(null)
const loading = ref(false)

// 与 Login.vue 同步：写 localStorage 后同步 Pinia userStore，保证菜单组 computed 响应式立即刷新
function syncUserLanding(info, fallbackUsername) {
  if (!info || typeof info !== 'object') info = {}
  const roles = Array.isArray(info.roles) && info.roles.length ? info.roles : (fallbackUsername ? ['user'] : [])
  const isAdmin = roles.some(r => String(r).toLowerCase() === 'admin')
  const menuIds = Array.isArray(info.menuIds) && info.menuIds.length
    ? info.menuIds
    : (isAdmin ? ['*'] : [])
  const permissions = Array.isArray(info.permissions) && info.permissions.length ? info.permissions : (isAdmin ? ['*'] : [])
  // 关键修复：同步保存 menuRoutePaths（MainLayout 的菜单渲染依赖此字段）
  const menuRoutePaths = Array.isArray(info.menuRoutePaths) && info.menuRoutePaths.length
    ? info.menuRoutePaths
    : (isAdmin ? ['*'] : [])
  localStorage.setItem('roles', JSON.stringify(roles))
  localStorage.setItem('menuIds', JSON.stringify(menuIds))
  localStorage.setItem('permissions', JSON.stringify(permissions))
  localStorage.setItem('menuRoutePaths', JSON.stringify(menuRoutePaths))
  localStorage.setItem('userInfo', JSON.stringify({
    username: info.username || fallbackUsername || localStorage.getItem('username') || '',
    realName: info.realName || localStorage.getItem('realName') || '',
    roles,
    menuIds,
    permissions,
    menuRoutePaths,
    ...info
  }))
  try {
    userStore.roles = roles
    userStore.menuIds = menuIds
    userStore.permissions = permissions
    userStore.menuRoutePaths = menuRoutePaths
    userStore.rehydrate()
  } catch (_) {}
}

onMounted(() => {
  realName.value = localStorage.getItem('realName') || '用户'
  const tenantsStr = localStorage.getItem('pendingTenants')
  try {
    if (tenantsStr) tenants.value = JSON.parse(tenantsStr)
  } catch (e) {
    console.warn('解析待选租户失败', e)
  }
  if (!tenants.value || tenants.value.length === 0) {
    // 没有租户可选时也支持 redirect 回跳
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    if (redirect && redirect.startsWith('/') && !redirect.startsWith('//')) {
      router.replace(redirect)
    } else {
      router.replace('/dashboard')
    }
  }
})

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  return d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0')
    + ' ' + String(d.getHours()).padStart(2,'0') + ':' + String(d.getMinutes()).padStart(2,'0')
}

async function handleSelect(t) {
  if (loading.value) return
  if (!t || t.tenantId == null) {
    ElMessage.error(tt('租户ID无效，请重新登录'))
    return
  }
  selectedId.value = t.tenantId
  loading.value = true
  try {
    try {
      const res = await api.post('/tenant/select', { tenantId: t.tenantId })
      localStorage.setItem('token', res.token)
      localStorage.setItem('tenantId', String(res.tenantId))
      localStorage.setItem('tenantName', res.tenantName || t.tenantName)
      localStorage.setItem('tenantCode', res.tenantCode || t.tenantCode)
      localStorage.removeItem('pendingTenants')

      let info = null
      try {
        info = await userStore.fetchUserinfo()
      } catch (e) {
        console.warn('userinfo 加载失败，使用本地兜底', e)
        info = {
          username: localStorage.getItem('username'),
          realName: localStorage.getItem('realName'),
          roles: ['user']
        }
      }
      // 核心：把切换租户后的权限/菜单写回独立 LS key（与 stores/user.js 初始化路径一致）
      syncUserLanding(info, localStorage.getItem('username'))
      // 有 token 后再刷一次系统配置（权限范围内最新值）
      try { await appConfig.refreshConfig() } catch (_) {}

      ElMessage.success(tt('进入 ') + (res.tenantName || t.tenantName))
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
      if (redirect && redirect.startsWith('/') && !redirect.startsWith('//')) {
        router.replace(redirect)
      } else {
        router.replace('/dashboard')
      }
    } catch (e) {
      console.error('选择公司失败:', e)
      // 错误提示也包一层 try...catch，避免极端场景下 ElMessage 自身抛错传播出去
      try {
        const msg = '选择公司失败: ' + ((e && (e.message || e.msg)) || '未知错误')
        ElMessage.error(msg)
      } catch (_) {}
    }
  } finally {
    // 最外层 finally：保证 loading 一定复位。
    // 即使 catch 内部 ElMessage 或 syncUserLanding 抛错，也不会出现"正在进入..."一直转圈。
    loading.value = false
  }
}
</script>

<style scoped>
.tenant-select-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a5fb4, #3b82f6);
}
.tenant-select-card {
  background: #fff;
  padding: 40px;
  border-radius: 16px;
  width: 600px;
  max-width: 90vw;
  box-shadow: 0 20px 60px rgba(0,0,0,0.25);
}
.tenant-select-card h2 { text-align: center; color: #1f2937; margin-bottom: 8px; font-size: 22px; }
.subtitle { text-align: center; color: #6b7280; font-size: 13px; margin-bottom: 28px; }
.tenant-list { display: flex; flex-direction: column; gap: 12px; max-height: 50vh; overflow-y: auto; }
.tenant-item {
  display: flex; align-items: center; gap: 16px;
  padding: 16px; border-radius: 12px;
  border: 2px solid #e5e7eb; background: #f9fafb;
  cursor: pointer; transition: all .2s;
}
.tenant-item:hover { border-color: #3b82f6; background: #eff6ff; transform: translateY(-1px); }
.tenant-item.active { border-color: #3b82f6; background: #dbeafe; }
.tenant-icon { font-size: 36px; }
.tenant-info { flex: 1; }
.tenant-name { font-size: 16px; font-weight: 600; color: #111827; margin-bottom: 2px; }
.tenant-expired {
  margin-left: 8px;
  padding: 0 6px;
  font-size: 12px;
  font-weight: 500;
  color: #f56c6c;
  border: 1px solid #f56c6c;
  border-radius: 4px;
  vertical-align: middle;
}
.tenant-code { font-size: 12px; color: #6b7280; margin-bottom: 4px; }
.tenant-last-login { font-size: 12px; color: #9ca3af; }
.tenant-arrow { color: #3b82f6; font-size: 20px; font-weight: 600; }
</style>
