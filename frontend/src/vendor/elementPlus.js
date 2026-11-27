// UI library unified export (2026-10-14 perf: vendor chunk split for caching).
// Element-plus JS + full CSS are exported ONLY from this file so the bundler puts
// them into one stable vendor chunk (see manualChunks src/vendor rule in vite.config.js).
// Vendor content does not change on business releases -> same content hash -> browser cache hit.
// Pages must not import element-plus styles directly; import from here instead (main.js wired).
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'

export { ElementPlus, zhCn }