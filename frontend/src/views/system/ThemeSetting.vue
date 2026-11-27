<template>
  <div class="kgw-theme-setting">
    <!-- 顶部工具栏 -->
    <div class="param-toolbar">
      <el-button v-permission="perm('save')" type="primary" @click="onSave" :loading="saving" size="small">{{ $tt("保存") }}</el-button>
      <el-button v-permission="perm('resetDefault')" @click="onReset" size="small">{{ $tt("恢复默认") }}</el-button>
      <el-button v-permission="perm('refresh')" @click="onReload" size="small">{{ $tt("刷新") }}</el-button>
      <span class="toolbar-tip">修改后即时预览，点击"保存"后同步到服务器</span>
    </div>

    <!-- 1. 预设主题 -->
    <div class="section">
      <div class="section-title">{{ $tt("预设主题") }}</div>
      <div class="preset-grid">
        <div
          v-for="p in presets"
          :key="p.key"
          class="preset-card"
          :class="{ active: theme.preset === p.key }"
          @click="onApplyPreset(p.key)"
        >
          <div class="preset-preview" :style="buildPresetPreviewStyle(p)">
            <div class="preview-sidebar" :style="{ background: p.colors.sidebarBg }">
              <div class="preview-logo-dot" :style="{ background: p.colors.sidebarActiveBg }"></div>
              <div class="preview-menu-line" :style="{ background: p.colors.sidebarText, opacity: 0.5 }"></div>
              <div class="preview-menu-line short" :style="{ background: p.colors.sidebarText, opacity: 0.5 }"></div>
              <div class="preview-menu-line active" :style="{ background: p.colors.sidebarActiveBg }"></div>
              <div class="preview-menu-line" :style="{ background: p.colors.sidebarText, opacity: 0.5 }"></div>
            </div>
            <div class="preview-main" :style="{ background: p.colors.bgPage }">
              <div class="preview-header" :style="{ background: p.colors.headerBg }">
                <span class="preview-header-text" :style="{ color: p.colors.headerText }">{{ $tt("智能ERP") }}</span>
                <div class="preview-avatar" :style="{ background: p.colors.primaryColor }"></div>
              </div>
              <div class="preview-card">
                <div class="preview-btn" :style="{ background: p.colors.primaryColor }">{{ $tt("按钮") }}</div>
                <div class="preview-line" :style="{ background: p.colors.primaryColor, opacity: 0.3 }"></div>
                <div class="preview-line short" :style="{ background: p.colors.infoColor, opacity: 0.4 }"></div>
                <div class="preview-tag" :style="{ background: p.colors.successColor }">{{ $tt("已生效") }}</div>
              </div>
            </div>
          </div>
          <div class="preset-info">
            <div class="preset-name">
              <el-icon v-if="theme.preset === p.key" color="#409EFF"><Check /></el-icon>
              {{ p.name }}
            </div>
            <div class="preset-desc">{{ p.desc }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. 自定义颜色 -->
    <div class="section">
      <div class="section-title">{{ $tt("自定义颜色") }}</div>
      <el-row :gutter="20">
        <el-col :span="8" v-for="field in colorFields" :key="field.key">
          <div class="color-item">
            <div class="color-label">
              <el-tooltip :content="field.desc" placement="top">
                <el-icon class="help-icon"><InfoFilled /></el-icon>
              </el-tooltip>
              {{ $tf(field.label) }}
            </div>
            <div class="color-control">
              <el-color-picker
                v-model="theme[field.key]"
                size="small"
                :show-alpha="field.alpha"
                @change="(v) => onUpdateField(field.key, v)"
              />
              <el-input
                v-model="theme[field.key]"
                size="small"
                class="color-hex-input"
                @change="(v) => onUpdateField(field.key, v)"
              />
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 3. 实时预览 -->
    <div class="section">
      <div class="section-title">{{ $tt("实时预览") }}</div>
      <div class="live-preview" :style="{ background: theme.bgPage }">
        <div class="lp-sidebar" :style="{ background: theme.sidebarBg }">
          <div class="lp-logo">
            <el-icon :size="20" color="#fff"><Cpu /></el-icon>
            <span class="lp-logo-text" :style="{ color: theme.sidebarText }">{{ $tt("智能ERP") }}</span>
          </div>
          <div class="lp-menu-item" :style="{ color: theme.sidebarText, opacity: 0.7 }">
            <el-icon><DataLine /></el-icon><span>{{ $tt("工作台") }}</span>
          </div>
          <div class="lp-menu-item active" :style="{ background: theme.sidebarActiveBg, color: theme.sidebarText }">
            <el-icon><Goods /></el-icon><span>{{ $tt("销售管理") }}</span>
          </div>
          <div class="lp-menu-item" :style="{ color: theme.sidebarText, opacity: 0.7 }">
            <el-icon><Box /></el-icon><span>{{ $tt("库存管理") }}</span>
          </div>
          <div class="lp-menu-item" :style="{ color: theme.sidebarText, opacity: 0.7 }">
            <el-icon><Tools /></el-icon><span>{{ $tt("系统设置") }}</span>
          </div>
        </div>
        <div class="lp-main">
          <div class="lp-header" :style="{ background: theme.headerBg }">
            <div class="lp-breadcrumb" :style="{ color: theme.headerText }">{{ $tt("首页 / 销售管理") }}</div>
            <div class="lp-header-right">
              <el-avatar :size="24" :style="{ background: theme.primaryColor }">U</el-avatar>
            </div>
          </div>
          <div class="lp-content">
            <el-card shadow="never" class="lp-card">
              <div class="lp-card-title">{{ $tt("销售订单") }}</div>
              <div class="lp-actions">
                <el-button size="small" type="primary">{{ $tt("新增") }}</el-button>
                <el-button size="small">{{ $tt("查询") }}</el-button>
                <el-button size="small" type="success">{{ $tt("导入") }}</el-button>
                <el-button size="small" type="warning">{{ $tt("导出") }}</el-button>
                <el-button size="small" type="danger">{{ $tt("删除") }}</el-button>
              </div>
              <div class="lp-table">
                <div class="lp-table-header">
                  <span>{{ $tt("单号") }}</span><span>{{ $tt("客户") }}</span><span>{{ $tt("金额") }}</span><span>{{ $tt("状态") }}</span>
                </div>
                <div class="lp-table-row">
                  <span>SO-20260720-0001</span><span>{{ $tt("北京科技") }}</span><span>¥12,800.00</span>
                  <el-tag size="small" type="success">{{ $tt("已生效") }}</el-tag>
                </div>
                <div class="lp-table-row">
                  <span>SO-20260720-0002</span><span>{{ $tt("上海贸易") }}</span><span>¥8,560.00</span>
                  <el-tag size="small" type="warning">{{ $tt("待审核") }}</el-tag>
                </div>
                <div class="lp-table-row">
                  <span>SO-20260720-0003</span><span>{{ $tt("深圳集团") }}</span><span>¥45,200.00</span>
                  <el-tag size="small" type="info">{{ $tt("草稿") }}</el-tag>
                </div>
              </div>
            </el-card>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:theme-setting')
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check, InfoFilled, Cpu, DataLine, Goods, Box, Tools
} from '@element-plus/icons-vue'
import { useThemeStore, THEME_PRESETS, DEFAULT_THEME } from '@/stores/theme'
import { tt } from '@/i18n'

defineProps({ embedded: { type: Boolean, default: false } })

const themeStore = useThemeStore()
const presets = THEME_PRESETS
const saving = ref(false)

// 主题配置（响应式镜像，方便表单绑定）
const theme = reactive({ ...themeStore.config })

// 颜色字段定义
const colorFields = [
  { key: 'primaryColor',     label: '主色',           desc: '系统主色，用于按钮、链接、激活态等', alpha: false },
  { key: 'successColor',     label: '成功色',         desc: '成功状态标签/按钮颜色', alpha: false },
  { key: 'warningColor',     label: '警告色',         desc: '警告状态标签/按钮颜色', alpha: false },
  { key: 'dangerColor',      label: '危险色',         desc: '危险/错误状态标签/按钮颜色', alpha: false },
  { key: 'infoColor',        label: '信息色',         desc: '信息提示/中性状态颜色', alpha: false },
  { key: 'bgPage',           label: '页面背景色',     desc: '主内容区的页面背景颜色', alpha: false },
  { key: 'sidebarBg',        label: '侧边栏背景色',   desc: '左侧导航栏的背景颜色', alpha: false },
  { key: 'sidebarText',      label: '侧边栏文字色',   desc: '侧边栏菜单的文字颜色', alpha: false },
  { key: 'sidebarActiveBg',  label: '侧边栏选中色',   desc: '侧边栏当前选中项的高亮背景色', alpha: false },
  { key: 'headerBg',         label: '顶栏背景色',     desc: '顶部导航栏的背景颜色', alpha: false },
  { key: 'headerText',       label: '顶栏文字色',     desc: '顶部导航栏的文字颜色', alpha: false }
]

function buildPresetPreviewStyle(p) {
  return {
    border: theme.preset === p.key ? `2px solid ${p.colors.primaryColor}` : '1px solid #e4e7ed'
  }
}

function onApplyPreset(presetKey) {
  themeStore.applyPreset(presetKey)
  // 同步到本地响应式对象
  Object.assign(theme, themeStore.config)
  ElMessage.success(`${tt("已应用预设：")}${presets.find(p => p.key === presetKey)?.name}`)
}

function onUpdateField(field, value) {
  if (!value) return
  themeStore.updateField(field, value)
  theme[field] = value
}

async function onSave() {
  saving.value = true
  try {
    // 先把表单中的所有字段同步到 store
    themeStore.updateConfig({ ...theme })
    const ok = await themeStore.saveToServer()
    if (ok) {
      ElMessage.success(tt('主题配置已保存到服务器'))
    } else {
      ElMessage.warning(tt('服务器保存失败，已保存到本地缓存'))
    }
  } finally {
    saving.value = false
  }
}

function onReset() {
  ElMessageBox.confirm(tt('确定恢复默认主题？所有自定义颜色将被重置。'), tt('确认'), {
    type: 'warning'
  }).then(() => {
    themeStore.reset()
    Object.assign(theme, themeStore.config)
    ElMessage.success(tt('已恢复默认主题'))
  }).catch(() => {})
}

async function onReload() {
  await themeStore.loadFromServer()
  Object.assign(theme, themeStore.config)
  ElMessage.success(tt('已从服务器重新加载'))
}

onMounted(() => {
  // 进入页面时确保从 store 同步最新值
  Object.assign(theme, themeStore.config)
})
</script>

<style scoped lang="scss">
.kgw-theme-setting {
  padding: 16px;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
}

.param-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 10px 12px;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  .toolbar-tip {
    margin-left: 8px;
    font-size: 12px;
    color: #909399;
  }
}

.section {
  background: #fff;
  border-radius: 4px;
  padding: 16px 20px;
  margin-bottom: 16px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-left: 8px;
  border-left: 3px solid var(--erp-primary, #409EFF);
}

/* ===== 预设主题卡片 ===== */
.preset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.preset-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafafa;
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }
  &.active {
    border-color: var(--erp-primary, #409EFF);
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
  }
}
.preset-preview {
  height: 120px;
  display: flex;
  border-bottom: 1px solid #ebeef5;
}
.preview-sidebar {
  width: 48px;
  padding: 6px 4px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
}
.preview-logo-dot {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  margin-bottom: 4px;
}
.preview-menu-line {
  width: 80%;
  height: 4px;
  border-radius: 2px;
  &.short { width: 50%; }
  &.active { width: 90%; height: 6px; }
}
.preview-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.preview-header {
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 6px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}
.preview-header-text {
  font-size: 8px;
  font-weight: 600;
}
.preview-avatar {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.preview-card {
  flex: 1;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.preview-btn {
  width: 36px;
  height: 10px;
  border-radius: 2px;
  font-size: 7px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.preview-line {
  height: 3px;
  border-radius: 1px;
  width: 100%;
  &.short { width: 60%; }
}
.preview-tag {
  align-self: flex-start;
  font-size: 7px;
  padding: 1px 4px;
  border-radius: 2px;
  color: #fff;
}
.preset-info {
  padding: 8px 10px;
  background: #fff;
}
.preset-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 4px;
}
.preset-desc {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

/* ===== 自定义颜色 ===== */
.color-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fafafa;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
}
.color-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #303133;
  .help-icon {
    color: #c0c4cc;
    cursor: help;
    font-size: 14px;
  }
}
.color-control {
  display: flex;
  align-items: center;
  gap: 8px;
}
.color-hex-input {
  width: 100px;
  :deep(.el-input__wrapper) {
    box-shadow: 0 0 0 1px #dcdfe6 inset;
  }
}

/* ===== 实时预览 ===== */
.live-preview {
  display: flex;
  height: 380px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
}
.lp-sidebar {
  width: 160px;
  display: flex;
  flex-direction: column;
  padding-top: 0;
}
.lp-logo {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
.lp-logo-text {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}
.lp-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  font-size: 13px;
  cursor: pointer;
  &.active {
    font-weight: 600;
  }
  &:hover {
    opacity: 1 !important;
  }
}
.lp-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.lp-header {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}
.lp-breadcrumb {
  font-size: 13px;
}
.lp-content {
  flex: 1;
  padding: 12px;
  overflow: auto;
}
.lp-card {
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.lp-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}
.lp-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.lp-table {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}
.lp-table-header,
.lp-table-row {
  display: grid;
  grid-template-columns: 2fr 1.5fr 1fr 1fr;
  padding: 8px 12px;
  font-size: 12px;
  align-items: center;
}
.lp-table-header {
  background: #f5f7fa;
  font-weight: 600;
  color: #606266;
  border-bottom: 1px solid #ebeef5;
}
.lp-table-row {
  border-bottom: 1px solid #ebeef5;
  color: #303133;
  &:last-child {
    border-bottom: none;
  }
}
</style>
