<template>
  <el-container class="main-layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">智能ERP</div>
      <el-menu :default-active="$route.path" router class="menu">
        <el-menu-item index="/dashboard"><el-icon><DataLine /></el-icon><span>工作台</span></el-menu-item>
        <el-sub-menu index="sale">
          <template #title><el-icon><ShoppingCart /></el-icon><span>销售管理</span></template>
          <el-menu-item index="/sale/orders">销售订单</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="purchase">
          <template #title><el-icon><Goods /></el-icon><span>采购管理</span></template>
          <el-menu-item index="/purchase/orders">采购订单</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/inventory"><el-icon><Box /></el-icon><span>库存管理</span></el-menu-item>
        <el-sub-menu index="masterdata">
          <template #title><el-icon><Files /></el-icon><span>基础数据</span></template>
          <el-menu-item index="/masterdata/materials">物料档案</el-menu-item>
          <el-menu-item index="/masterdata/customers">客户档案</el-menu-item>
          <el-menu-item index="/masterdata/bom">BOM清单</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="production">
          <template #title><el-icon><Tools /></el-icon><span>生产管理</span></template>
          <el-menu-item index="/production/workorders">生产工单</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/finance"><el-icon><TrendCharts /></el-icon><span>财务管理</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="page-title">{{ $route.meta.title || '工作台' }}</span>
        <div class="header-right">
          <span class="username">{{ username }}</span>
          <el-button text @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>

    <!-- AI助手浮动按钮 -->
    <AiAssistant />
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AiAssistant from '../components/AiAssistant.vue'

const router = useRouter()
const username = computed(() => localStorage.getItem('username') || '用户')

const logout = () => {
  localStorage.clear()
  router.push('/login')
}
</script>

<style scoped>
.main-layout { height: 100vh; }
.sidebar { background: #1e293b; }
.logo { color: #fff; font-size: 18px; font-weight: 700; text-align: center; padding: 20px 0; border-bottom: 1px solid #334155; }
.menu { border: none; background: transparent; }
.menu .el-menu-item, .menu :deep(.el-sub-menu__title) { color: #cbd5e1; }
.menu .el-menu-item:hover, .menu :deep(.el-sub-menu__title:hover) { background: #334155; }
.menu .el-menu-item.is-active { background: #3b82f6; color: #fff; }
.header { background: #fff; border-bottom: 1px solid #e5e7eb; display: flex; align-items: center; justify-content: space-between; }
.page-title { font-size: 16px; font-weight: 600; color: #1e293b; }
.header-right { display: flex; align-items: center; gap: 12px; }
.username { color: #6b7280; font-size: 13px; }
.content { background: #f5f7fa; padding: 20px; overflow-y: auto; }
</style>
