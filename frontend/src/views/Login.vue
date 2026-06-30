<template>
  <div class="login-container">
    <div class="login-card">
      <h2>智能ERP系统</h2>
      <p class="subtitle">支持多AI渠道接入 · 企业级管理平台</p>
      <el-form :model="form" @submit.prevent="handleLogin" label-width="0">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" @click="handleLogin" :loading="loading">登 录</el-button>
      </el-form>
      <div class="tips">
        <p>默认账号: admin / admin123</p>
        <p>销售账号: sale01 / 123456</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const router = useRouter()
const loading = ref(false)
const form = ref({ username: 'admin', password: 'admin123' })

const handleLogin = async () => {
  loading.value = true
  try {
    const res = await api.post('/auth/login', form.value)
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('username', res.data.username)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a5fb4, #3b82f6);
}
.login-card {
  background: #fff;
  padding: 40px;
  border-radius: 12px;
  width: 400px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2);
}
h2 { text-align: center; color: #1a5fb4; margin-bottom: 8px; }
.subtitle { text-align: center; color: #6b7280; font-size: 13px; margin-bottom: 30px; }
.tips { margin-top: 20px; text-align: center; font-size: 12px; color: #9ca3af; }
</style>
