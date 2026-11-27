// 手机浏览器自动进手机版：必须最先执行（放在所有业务导入之前）
import './mobile-guard'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
// 组件库从统一出口引出（JS+样式整体进"组件库缓存包"，发版不变文件名）
import { ElementPlus, zhCn } from './vendor/elementPlus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './styles/index.scss'
import { permissionDirective } from './directives/permission'
import { useAppConfigStore } from './stores/appConfig'
import { t, tf, tt, dictT, lang, ensureLangPack } from './i18n'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

const pinia = createPinia()
app.use(pinia)

// 启动时第一时间加载公开的系统名称/logo/副标题配置（登录页也能显示）
const appConfig = useAppConfigStore()
appConfig.loadPublicConfig().catch(() => {})

app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.directive('permission', permissionDirective)

// ---- 多语言（L1）：全局 $t / $lang + 启动预热当前语言包（失败静默，走中文回退）----
app.config.globalProperties.$t = t
app.config.globalProperties.$lang = lang
// 字段标题翻译（L2）：模板里用 $tf('客户') / $tf(col.label)
app.config.globalProperties.$tf = tf
// 界面文案翻译（L3-a）：$tt('保存成功') 提示语/按钮/标题/placeholder；$dt('已生效') 状态/枚举显示值
app.config.globalProperties.$tt = tt
app.config.globalProperties.$dt = dictT
ensureLangPack(lang.value)

// ---- 白屏兜底 1：懒加载 chunk 失败（Python dev-server/Nginx SPA 路由把 *.js 返回成 index.html 时 ChunkLoadError）
//      表现：登录/选租户后跳首页白屏；处理：自动清缓存重试一次，再失败就弹提示
//      注意：去重标记放 sessionStorage（模块级变量整页刷新后会重置，可能形成"刷新→失败→再刷新"死循环）
router.onError((err, to, from) => {
  try {
    const msg = (err && err.message) ? err.message : String(err || '')
    const isChunk = /ChunkLoadError|Failed to fetch dynamically imported module|Load chunk|Unexpected token '</i.test(msg)
    console.error('[router.onError]', err)
    if (isChunk) {
      const dest = (to && to.fullPath) ? to.fullPath : '/dashboard'
      const reloadKey = 'chunk-reloaded:' + dest
      let alreadyReloaded = false
      try { alreadyReloaded = !!sessionStorage.getItem(reloadKey) } catch (_) {}
      if (!alreadyReloaded) {
        try { sessionStorage.setItem(reloadKey, '1') } catch (_) {}
        try {
          // 清一下有可能的老缓存（避免用户上次构建的 hash 还留在浏览器里）
          Object.keys(localStorage).forEach(k => {
            if (k.startsWith('vite:') || k.startsWith('vue-tsc') || k.includes('chunk-hash')) {
              try { localStorage.removeItem(k) } catch (_) {}
            }
          })
        } catch (_) {}
        window.location.href = dest
        return
      }
      // 本次会话已重试过仍失败：不再 reload，直接提示用户手动强刷，避免无限刷新
      try {
        const ElMessage = ElementPlus.ElMessage || (window.ElementPlus && window.ElementPlus.ElMessage)
        if (ElMessage) ElMessage({ type: 'error', message: tt('页面资源加载失败，请按 Ctrl+F5 强制刷新浏览器'), duration: 6000, showClose: true })
        else alert(tt('页面资源加载失败，请按 Ctrl+F5 强制刷新浏览器'))
      } catch (_) { alert(tt('页面资源加载失败，请按 Ctrl+F5 强制刷新浏览器')) }
      return
    }
    // 非 Chunk 的错误：至少打出来，别静默白屏
    try {
      const ElMessage = ElementPlus.ElMessage || (window.ElementPlus && window.ElementPlus.ElMessage)
      if (ElMessage) ElMessage({ type: 'error', message: tt('页面加载异常：') + (msg || tt('未知错误')), duration: 5000, showClose: true })
    } catch (_) {}
  } catch (_) {}
})

// ---- 白屏兜底 2：组件渲染期报错（例如接口返回空结构但 computed 解包了字段）
//      Vue 3 errorHandler 处理完后，父组件 <router-view> 不会整棵卸载
app.config.errorHandler = (err, instance, info) => {
  try { console.error('[app.errorHandler]', err, info) } catch (_) {}
  try {
    const ElMessage = ElementPlus.ElMessage || (window.ElementPlus && window.ElementPlus.ElMessage)
    const msg = (err && err.message) ? err.message : String(err || tt('页面渲染异常'))
    if (ElMessage) ElMessage({ type: 'warning', message: tt('页面提示：') + msg, duration: 4000, showClose: true })
  } catch (_) {}
}

app.mount('#app')
