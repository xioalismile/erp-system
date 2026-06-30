<template>
  <el-container class="main-layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <el-icon :size="24" color="#fff"><Cpu /></el-icon>
        <span v-show="!collapsed" class="logo-text">智能ERP</span>
      </div>
      <el-menu
        :default-active="$route.path"
        :collapse="collapsed"
        router
        class="menu"
        unique-opened
      >
        <el-menu-item index="/dashboard"><el-icon><DataLine /></el-icon><span>工作台</span></el-menu-item>

        <el-sub-menu index="basedata">
          <template #title><el-icon><Files /></el-icon><span>基础资料</span></template>
          <el-menu-item index="/basedata/unit">计量单位</el-menu-item>
          <el-menu-item index="/basedata/currency">币种</el-menu-item>
          <el-menu-item index="/basedata/settle">结算方式</el-menu-item>
          <el-menu-item index="/basedata/payment-term">付款条件</el-menu-item>
          <el-menu-item index="/basedata/price-level">价格等级</el-menu-item>
          <el-menu-item index="/basedata/department">部门档案</el-menu-item>
          <el-menu-item index="/basedata/employee">员工档案</el-menu-item>
          <el-menu-item index="/basedata/warehouse">仓库档案</el-menu-item>
          <el-menu-item index="/basedata/location">货位档案</el-menu-item>
          <el-menu-item index="/basedata/material-category">物料分类</el-menu-item>
          <el-menu-item index="/basedata/material">物料档案</el-menu-item>
          <el-menu-item index="/basedata/customer">客户档案</el-menu-item>
          <el-menu-item index="/basedata/supplier">供应商档案</el-menu-item>
          <el-menu-item index="/basedata/account">会计科目</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="sale">
          <template #title><el-icon><ShoppingCart /></el-icon><span>销售管理</span></template>
          <el-menu-item index="/sale/orders">销售订单</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="purchase">
          <template #title><el-icon><Goods /></el-icon><span>采购管理</span></template>
          <el-menu-item index="/purchase/orders">采购订单</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="inventory">
          <template #title><el-icon><Box /></el-icon><span>库存管理</span></template>
          <el-menu-item index="/inventory/current">即时库存</el-menu-item>
          <el-menu-item index="/inventory/batches">批次库存</el-menu-item>
          <el-menu-item index="/inventory/ledger">库存流水</el-menu-item>
          <el-menu-item index="/inventory/other-receipts">其他入库单</el-menu-item>
          <el-menu-item index="/inventory/other-issues">其他出库单</el-menu-item>
          <el-menu-item index="/inventory/stock-transfers">库存调拨单</el-menu-item>
          <el-menu-item index="/inventory/stock-checks">库存盘点单</el-menu-item>
          <el-menu-item index="/inventory/material-requisitions">生产领料单</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="production">
          <template #title><el-icon><Tools /></el-icon><span>生产管理</span></template>
          <el-menu-item index="/masterdata/bom">BOM清单</el-menu-item>
          <el-menu-item index="/production/work-center">工作中心</el-menu-item>
          <el-menu-item index="/production/workstation">工序/工位</el-menu-item>
          <el-menu-item index="/production/routings">工艺路线</el-menu-item>
          <el-menu-item index="/production/workorders">生产工单</el-menu-item>
          <el-menu-item index="/production/receipts">生产入库单</el-menu-item>
          <el-menu-item index="/production/mrp">MRP运算</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="finance">
          <template #title><el-icon><TrendCharts /></el-icon><span>财务管理</span></template>
          <el-menu-item index="/finance/receivable">应收账款</el-menu-item>
          <el-menu-item index="/finance/payable">应付账款</el-menu-item>
          <el-menu-item index="/finance/receipt">收款单</el-menu-item>
          <el-menu-item index="/finance/payment">付款单</el-menu-item>
          <el-menu-item index="/finance/vouchers">会计凭证</el-menu-item>
          <el-menu-item index="/finance/balance">科目余额表</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="system">
          <template #title><el-icon><Setting /></el-icon><span>系统管理</span></template>
          <el-menu-item index="/system/user">用户管理</el-menu-item>
          <el-menu-item index="/system/role">角色管理</el-menu-item>
          <el-menu-item index="/system/menu">菜单管理</el-menu-item>
          <el-menu-item index="/system/dict">数据字典</el-menu-item>
          <el-menu-item index="/system/log">操作日志</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" :size="20" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" /><Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.title">{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown>
            <span class="user-info">
              <el-avatar :size="28" style="background: #409EFF">{{ (realName || username || 'U').charAt(0) }}</el-avatar>
              <span class="username">{{ realName || username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/dashboard')">工作台</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>

    <AiAssistant />
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import AiAssistant from '../components/AiAssistant.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
const username = computed(() => userStore.username || localStorage.getItem('username') || '用户')
const realName = computed(() => userStore.realName || localStorage.getItem('realName') || '')

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}
</script>

<style scoped lang="scss">
.main-layout { height: 100vh; }
.sidebar {
  background: #1e293b;
  transition: width 0.2s;
  overflow-x: hidden;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid #334155;
  color: #fff;
}
.logo-text { font-size: 18px; font-weight: 700; }

.menu { border: none; background: transparent; height: calc(100vh - 60px); overflow-y: auto; }
.menu:not(.el-menu--collapse) { width: 220px; }
.menu :deep(.el-menu-item), .menu :deep(.el-sub-menu__title) { color: #cbd5e1; }
.menu :deep(.el-menu-item:hover), .menu :deep(.el-sub-menu__title:hover) { background: #334155; }
.menu :deep(.el-menu-item.is-active) { background: #3b82f6; color: #fff; }
.menu :deep(.el-sub-menu .el-menu-item) { background: #0f172a !important; }

.header {
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.header-left { display: flex; align-items: center; gap: 16px; }
.collapse-btn { cursor: pointer; color: #606266; &:hover { color: $color-primary; } }
.header-right { display: flex; align-items: center; gap: 12px; }
.user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.username { color: #303133; font-size: 14px; }
.content { background: $bg-page; padding: 16px; overflow-y: auto; }
</style>
