import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import fs from 'fs'

// 读取构建版本号（由 Publish.ps1 在构建前写入 frontend/build-version.txt）
// 找不到时降级为 "dev-unknown"，开发态兼容
function readBuildVersion() {
  const versionFile = path.resolve(__dirname, 'build-version.txt')
  try {
    if (fs.existsSync(versionFile)) {
      const v = fs.readFileSync(versionFile, 'utf-8').trim()
      if (v) return v
    }
  } catch (e) {
    // 读取失败不阻塞构建，降级为 dev-unknown
  }
  return 'dev-unknown'
}

const BUILD_VERSION = readBuildVersion()

export default defineConfig({
  plugins: [
    vue(),
    // 注入构建版本号到 index.html 的 meta 标签，update-agent.ps1 探针会提取此值
    // 与后端 /api/system/build-info 返回的版本号比对，不一致 → 自动回滚
    {
      name: 'inject-build-version',
      transformIndexHtml(html) {
        return html.replace(
          '<meta name="robots" content="noindex,nofollow">',
          '<meta name="robots" content="noindex,nofollow">\n  <meta name="build-version" content="' + BUILD_VERSION + '">'
        )
      }
    }
  ],
  // Vitest：只跑 src 下的单测；根目录 tests/ 是 Playwright 用例（含 test.describe），
  // 被 Vitest 误加载会让 `npm run test` 整体失败（2026-11-27 修复）。
  // 注意：不能写 **/tests/**，会把 src/tests 单测一起排除。
  test: {
    exclude: ['**/node_modules/**', '**/dist/**', 'tests/**']
  },
  define: {
    // 运行时常量，前端代码可通过 __BUILD_VERSION__ 访问（如显示在"关于"页面）
    __BUILD_VERSION__: JSON.stringify(BUILD_VERSION)
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    // 【性能关键】必须绑双栈 '::'，不能写 '0.0.0.0'。
    // 原因：Windows 上 localhost 会优先解析为 IPv6 ::1；若只监听 IPv4，
    // Chrome 每次新建连接都要先试 ::1 失败、再回退 127.0.0.1，单次罚约 70ms。
    // 一个单据页要发十几个请求，累计就是好几秒（实测 connect 66~74ms）。
    // 绑 '::' 后 Node 默认双栈（IPv4-mapped 仍可访问），::1 直接命中，connect 降到 <1ms。
    host: '::',
    strictPort: false,
    proxy: {
      '/api/capture': {
        target: 'http://localhost:3005',
        changeOrigin: true,
        rewrite: (path) => path
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@import "@/styles/variables.scss";`
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1000,
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
        pure_funcs: ['console.log', 'console.info', 'console.debug']
      },
      format: {
        comments: false
      }
    },
    rollupOptions: {
      output: {
        // 2026-10-14 性能优化：第三方库拆稳定缓存包。
        // 组件库/框架/图表库内容日常发版不变 → 内容哈希不变 → 发版后老用户命中缓存，
        // 只需重新下载变小的业务主包（实测 1.42MB → 193KB）。加载顺序与功能完全不变。
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            // 组件库统一出口（JS+样式一起进组件库缓存包）
            if (/[\\/]src[\\/]vendor[\\/]/.test(id)) return 'vendor'
            return undefined
          }
          // 图表库自成缓存包（依赖独立：只依赖 zrender），其余第三方全部同包 ——
          // 注意：vue 系框架不能与组件库拆成两个包（组件库模块求值期会用到框架工具函数，
          // 拆开会产生跨包求值顺序问题 "Cannot access before initialization" 白屏，实测踩过）
          if (/[\\/]node_modules[\\/](echarts|zrender)[\\/]/.test(id)) return 'echarts-vendor'
          return 'vendor'
        }
      }
    }
  }
})
