<template>
  <div class="forbidden">
    <div class="bg-403">
      <span class="num">4</span>
      <el-icon class="lock"><Lock /></el-icon>
      <span class="num">3</span>
    </div>
    <h2>{{ $tt("您没有权限访问该页面") }}</h2>
    <p>当前角色：{{ roles.join(', ') || '未登录' }}</p>
    <p class="hint">{{ $tt("请使用具备对应模块权限的账号登录，或联系管理员调整权限") }}</p>
    <el-button type="primary" @click="$router.push('/dashboard')">{{ $tt("返回工作台") }}</el-button>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Lock } from '@element-plus/icons-vue'
const roles = ref([])
onMounted(() => {
  try { roles.value = JSON.parse(localStorage.getItem('userInfo') || '{}').roles || [] } catch (e) {}
})
</script>

<style scoped lang="scss">
.forbidden {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 70vh;
  text-align: center;
  padding: 40px;
  h2 { color: #303133; font-size: 22px; margin: 24px 0 12px; }
  p { color: #606266; margin: 4px 0; }
  .hint { color: #909399; font-size: 13px; margin-bottom: 24px; }
}
.bg-403 {
  display: flex;
  align-items: center;
  gap: 8px;
  .num { font-size: 160px; font-weight: 700; color: #1890ff; line-height: 1; }
  .lock { font-size: 100px; color: #1890ff; }
}
</style>
