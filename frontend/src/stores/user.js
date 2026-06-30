// ====================================================================
// 用户Store - 管理登录态、用户信息、权限列表
// ====================================================================
import { defineStore } from 'pinia'
import request from '@/utils/request'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    username: localStorage.getItem('username') || '',
    realName: localStorage.getItem('realName') || '',
    permissions: JSON.parse(localStorage.getItem('permissions') || '[]'),
    roles: JSON.parse(localStorage.getItem('roles') || '[]')
  }),
  actions: {
    async login(payload) {
      const data = await request.post('/auth/login', payload)
      this.token = data.token
      this.username = data.username
      this.realName = data.realName || data.username
      localStorage.setItem('token', this.token)
      localStorage.setItem('username', this.username)
      localStorage.setItem('realName', this.realName)
      // 拉取用户权限(若接口可用)
      try {
        const info = await request.get('/auth/userinfo')
        this.permissions = info.permissions || ['*']
        this.roles = info.roles || []
        localStorage.setItem('permissions', JSON.stringify(this.permissions))
        localStorage.setItem('roles', JSON.stringify(this.roles))
      } catch (e) {
        // 权限接口未就绪时，默认给全部权限(开发阶段)
        this.permissions = ['*']
        localStorage.setItem('permissions', JSON.stringify(this.permissions))
      }
      return data
    },
    async logout() {
      try { await request.post('/auth/logout') } catch (e) {}
      this.reset()
    },
    reset() {
      this.token = ''
      this.username = ''
      this.realName = ''
      this.permissions = []
      this.roles = []
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('realName')
      localStorage.removeItem('permissions')
      localStorage.removeItem('roles')
    }
  }
})
