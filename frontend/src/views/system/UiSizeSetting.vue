<template>
  <div class="ui-size-setting">
    <!-- 顶部工具栏 -->
    <div class="param-toolbar">
      <el-button v-permission="perm('save')" type="primary" size="small" :loading="saving" @click="onSave">{{ $tt("保存") }}</el-button>
      <el-button v-permission="perm('resetDefault')" size="small" @click="onReset">{{ $tt("恢复默认") }}</el-button>
      <el-button v-permission="perm('refresh')" size="small" @click="onReload">{{ $tt("重新加载") }}</el-button>
      <span class="toolbar-tip">{{ $tt("改动即时预览，点“保存”后同步到服务器、全站生效") }}</span>
    </div>

    <!-- 当前状态说明（未配置时使用系统默认值，界面与改造前一致） -->
    <div class="tip-card">
      <div class="tip-line">
        <span class="tip-key">{{ $tt("当前状态") }}：</span>
        <el-tag :type="store.isAllDefault ? 'info' : 'success'" size="small" effect="plain">
          {{ store.isAllDefault ? $tt('全部为系统默认值') : $tt('已自定义') }}
        </el-tag>
      </div>
      <div class="tip-line tip-gray">
        {{ $tt("未设置时使用系统默认值") }}：{{ $tt("菜单文字") }} {{ UI_SIZE_DEFAULTS.menuFontSize }}px ·
        {{ $tt("列表字段") }} {{ UI_SIZE_DEFAULTS.listFontSize }}px ·
        {{ $tt("编辑页字段") }} {{ UI_SIZE_DEFAULTS.formFontSize }}px ·
        {{ $tt("搜索框宽度") }} {{ UI_SIZE_DEFAULTS.searchWidth }}px ·
        {{ $tt("区块间距") }} {{ UI_SIZE_DEFAULTS.sectionGap }}px ·
        {{ $tt("界面密度") }} {{ $tt(UI_SIZE_DENSITY_LABELS[UI_SIZE_DENSITY_DEFAULT]) }}
      </div>
    </div>

    <!-- 界面密度：一处切换，全站列表页 + 编辑页同时变 -->
    <div class="section">
      <div class="section-title">{{ $tt("一、界面密度（全站列表页 + 编辑页同时生效）") }}</div>
      <div class="density-row">
        <span class="row-label">{{ $tt("界面密度") }}</span>
        <el-radio-group v-model="form[UI_SIZE_DENSITY_KEY]" size="small">
          <el-radio-button
            v-for="lv in UI_SIZE_DENSITY_LEVELS"
            :key="lv"
            :label="lv"
          >{{ $tt(UI_SIZE_DENSITY_LABELS[lv]) }}</el-radio-button>
        </el-radio-group>
        <span class="row-desc">{{ $tt("紧凑：一屏看的内容最多（默认）；标准：与改造前一致；宽松：留白最大") }}</span>
      </div>
      <div class="density-hint">
        {{ $tt("影响范围") }}：{{ $tt("内容区左右/上下留白、卡片留白与间距、工具栏按钮间距、表格行高与单元格留白、编辑页表单每行字段数（长字段如备注/地址仍独占整行）") }}
      </div>
    </div>

    <!-- 分组设置项 -->
    <div class="section" v-for="group in groups" :key="group.title">
      <div class="section-title">{{ $tt(group.title) }}</div>
      <div class="size-row" v-for="item in group.items" :key="item.key">
        <span class="row-label">{{ $tt(item.label) }}</span>
        <el-input-number
          v-model="form[item.key]"
          :min="item.min"
          :max="item.max"
          :step="1"
          :precision="0"
          size="small"
          controls-position="right"
          class="row-input"
        />
        <span class="row-unit">px</span>
        <span class="row-desc">{{ $tt(item.desc) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:ui-size')
// 界面尺寸设置：菜单文字 / 列表字段文字 / 编辑页字段文字 / 搜索框宽度 / 编辑页区块高度
// 存储：sys_config(group=ui, 键前缀 ui.size.)；生效：注入 CSS 变量（stores/uiSize.js）
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tt } from '@/i18n'
import {
  useUiSizeStore, UI_SIZE_DEFAULTS, UI_SIZE_RANGE,
  UI_SIZE_DENSITY_KEY, UI_SIZE_DENSITY_LEVELS, UI_SIZE_DENSITY_LABELS, UI_SIZE_DENSITY_DEFAULT
} from '@/stores/uiSize'

// 设置中心以 <component :is="..." :embedded /> 内嵌渲染，声明该 prop 避免落到 DOM 属性上
defineProps({ embedded: { type: Boolean, default: false } })

const store = useUiSizeStore()
const saving = ref(false)

// 【注意】必须在 store 声明之后取初值（此前项目踩过 TDZ 白屏坑：变量在声明前被引用）
const form = reactive({ ...store.config })

// 改动即时预览（顶部文案承诺）：改任何一项立刻全站生效预览，点“保存”才同步到服务器。
// 刷新页面时 MainLayout 会重新执行 apply() + loadFromServer()，未保存的预览值会被服务器值覆盖回来。
watch(form, () => { store.updateConfig({ ...form }) }, { deep: true })

const r = (k) => ({ min: UI_SIZE_RANGE[k][0], max: UI_SIZE_RANGE[k][1] })

const groups = [
  {
    title: '二、左侧菜单（全站所有页面生效）',
    items: [
      {
        key: 'menuFontSize', label: '菜单文字大小',
        desc: '左侧导航菜单每一项的文字大小；改大后菜单文字更醒目',
        ...r('menuFontSize')
      }
    ]
  },
  {
    title: '三、列表页（本期：销售订单列表）',
    items: [
      {
        key: 'listFontSize', label: '列表字段文字大小',
        desc: '列表表头与表格内容的文字大小',
        ...r('listFontSize')
      },
      {
        key: 'searchWidth', label: '搜索框宽度',
        desc: '列表页顶部“输入订单号/客户名称搜索”输入框的宽度',
        ...r('searchWidth')
      }
    ]
  },
  {
    title: '四、编辑页（本期：销售订单编辑页）',
    items: [
      {
        key: 'formFontSize', label: '编辑页字段文字大小',
        desc: '编辑页表单的字段名与输入内容文字大小',
        ...r('formFontSize')
      },
      {
        key: 'sectionGap', label: '区块间距',
        desc: '编辑页上下两个区块（基本信息卡片之间、单据信息/客户信息等分区之间）的高度',
        ...r('sectionGap')
      }
    ]
  }
]

async function onSave() {
  saving.value = true
  try {
    store.updateConfig({ ...form })
    await store.save()
    Object.assign(form, store.config)
    ElMessage.success(tt('界面尺寸已保存到服务器，全站已生效'))
  } catch (e) {
    ElMessage.error(tt('保存失败：') + (e?.message || e))
  } finally {
    saving.value = false
  }
}

async function onReset() {
  try {
    await ElMessageBox.confirm(
      tt('确定恢复默认尺寸？恢复后需要再点“保存”才会同步到服务器。'),
      tt('确认'),
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  store.reset()
  Object.assign(form, store.config)
  ElMessage.success(tt('已恢复系统默认值，点“保存”后全站生效'))
}

async function onReload() {
  await store.loadFromServer()
  Object.assign(form, store.config)
  ElMessage.success(tt('已从服务器重新加载'))
}
</script>

<style scoped lang="scss">
.ui-size-setting {
  padding: 12px 16px;
  font-size: 13px;
  color: #303133;
}

.param-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;

  .toolbar-tip {
    font-size: 12px;
    color: #909399;
  }
}

.tip-card {
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px 12px;
  margin-bottom: 14px;

  .tip-line { line-height: 22px; }
  .tip-key { color: #606266; }
  .tip-gray { color: #909399; font-size: 12px; }
}

.section {
  margin-bottom: 16px;

  .section-title {
    display: flex;
    align-items: center;
    font-size: 13px;
    font-weight: 600;
    color: #409eff;
    margin-bottom: 8px;

    &::before {
      content: '';
      display: inline-block;
      width: 3px;
      height: 14px;
      background: #409eff;
      margin-right: 6px;
    }
  }
}

.density-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px dashed #ebeef5;

  .row-label {
    width: 130px;
    color: #606266;
    flex: 0 0 130px;
  }
}

.density-hint {
  padding: 6px 12px 2px;
  color: #909399;
  font-size: 12px;
  line-height: 20px;
}

.size-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px dashed #ebeef5;

  &:last-child { border-bottom: none; }

  .row-label {
    width: 130px;
    color: #606266;
    flex: 0 0 130px;
  }

  .row-input { width: 130px; }

  .row-unit {
    color: #909399;
    flex: 0 0 20px;
  }

  .row-desc {
    color: #909399;
    font-size: 12px;
  }
}
</style>
