<template>
  <div class="kgw-batch-no">
    <div class="kgw-toolbar">
      <div class="kgw-actions">
        <el-button v-permission="perm('save')" type="success" size="small" @click="onSave" :loading="saving">{{ $tt("保存") }}</el-button>
        <el-button v-permission="perm('refresh')" size="small" @click="loadConfig">{{ $tt("刷新") }}</el-button>
        <el-button v-permission="perm('resetDefault')" size="small" @click="onReset">{{ $tt("重置默认") }}</el-button>
      </div>
      <div class="kgw-info">
        <el-tag type="info" size="small">当前分类：{{ currentCatName }}</el-tag>
      </div>
    </div>

    <div class="kgw-body">
      <div class="kgw-layout-split">
        <div class="kgw-side">
          <el-input v-model="catKeyword" :placeholder="$tt('搜索分类')" size="small" clearable class="kgw-side-search" />
          <div class="kgw-side-list">
            <div
              class="kgw-side-item"
              :class="{ active: currentCat === 'default' }"
              @click="currentCat = 'default'"
            >
              <el-icon><Setting /></el-icon>
              <span>{{ $tt("默认规则") }}</span>
            </div>
            <div
              v-for="c in filteredCats"
              :key="c.categoryId"
              class="kgw-side-item"
              :class="{ active: currentCat === String(c.categoryId) }"
              @click="currentCat = String(c.categoryId)"
            >
              <el-icon><Goods /></el-icon>
              <span>{{ c.categoryName }}</span>
            </div>
          </div>
        </div>

        <div class="kgw-main">
          <el-form :model="cfg" label-width="160px" size="default" class="rule-form">
            <el-divider content-position="left">{{ $tt("基础设置") }}</el-divider>
            <el-form-item :label="$tf('允许修改批次号')">
              <el-switch v-model="cfg.allowModify" :active-text="$tt('允许')" :inactive-text="$tt('不允许')" />
            </el-form-item>
            <el-form-item :label="$tf('默认过期时长')">
              <el-input-number v-model="cfg.expireYear" :min="0" :max="99" controls-position="right" />
              <span class="unit">{{ $tt("年") }}</span>
              <el-input-number v-model="cfg.expireMonth" :min="0" :max="11" controls-position="right" />
              <span class="unit">{{ $tt("月") }}</span>
              <el-input-number v-model="cfg.expireDay" :min="0" :max="365" controls-position="right" />
              <span class="unit">{{ $tt("天") }}</span>
              <span class="tip">（入库日期 + 此时长 = 过期日期，0 表示永不过期）</span>
            </el-form-item>

            <el-divider content-position="left">{{ $tt("批次号生成规则") }}</el-divider>
            <el-form-item :label="$tf('自动生成批次号')">
              <el-switch v-model="cfg.autoGenerate" :active-text="$tt('自动生成')" :inactive-text="$tt('手动填写')" />
            </el-form-item>
            <el-form-item :label="$tf('前缀')">
              <el-input v-model="cfg.prefix" :placeholder="$tt('如：PC、SC 等')" maxlength="10" style="width: 200px" />
            </el-form-item>
            <el-form-item :label="$tf('日期格式')">
              <el-radio-group v-model="cfg.dateFormat">
                <el-radio value="">{{ $tt("不包含日期") }}</el-radio>
                <el-radio value="YYMMDD">YYMMDD</el-radio>
                <el-radio value="YYYYMMDD">YYYYMMDD</el-radio>
                <el-radio value="YYYYMM">YYYYMM</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item :label="$tf('包含货品编码')">
              <el-switch v-model="cfg.includeMaterialCode" :active-text="$tt('是')" :inactive-text="$tt('否')" />
            </el-form-item>
            <el-form-item :label="$tf('流水号位数')">
              <el-input-number v-model="cfg.seqLength" :min="2" :max="8" controls-position="right" />
              <span class="unit">{{ $tt("位") }}</span>
            </el-form-item>
            <el-form-item :label="$tf('分隔符')">
              <el-input v-model="cfg.separator" :placeholder="$tt('如：-、_ 等')" maxlength="3" style="width: 120px" />
              <span class="tip">{{ $tt("（各段之间的分隔符，留空则无）") }}</span>
            </el-form-item>

            <el-divider content-position="left">{{ $tt("规则预览") }}</el-divider>
            <el-form-item :label="$tf('生成示例')">
              <span class="example-text">{{ exampleText }}</span>
            </el-form-item>

            <el-form-item>
              <el-button v-permission="perm('save')" type="primary" @click="onSave" :loading="saving">{{ $tt("保存当前分类设置") }}</el-button>
              <el-button v-permission="perm('apply')" @click="applyToAll">{{ $tt("应用到全部分类") }}</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:batch-no')
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting, Goods } from '@element-plus/icons-vue'
import { treeMaterialCategory } from '@/api/basedata/material-category'
import request from '@/utils/request'
import { tt } from '@/i18n'

const CONFIG_PREFIX = 'batch_no_rule_'
const CONFIG_GROUP = 'batch_no'

const saving = ref(false)
const catKeyword = ref('')
const currentCat = ref('default')
const categoryList = ref([])

const defaultCfg = {
  autoGenerate: true,
  allowModify: true,
  expireYear: 2,
  expireMonth: 0,
  expireDay: 0,
  prefix: '',
  dateFormat: 'YYMMDD',
  includeMaterialCode: false,
  seqLength: 4,
  separator: '-'
}

const cfg = reactive({ ...defaultCfg })

const currentCatName = computed(() => {
  if (currentCat.value === 'default') return '默认规则'
  const c = categoryList.value.find(c => String(c.categoryId) === currentCat.value)
  return c ? c.categoryName : '未知分类'
})

const filteredCats = computed(() => {
  if (!catKeyword.value) return categoryList.value
  const kw = catKeyword.value.toLowerCase()
  return categoryList.value.filter(c => (c.categoryName || '').toLowerCase().includes(kw))
})

const exampleText = computed(() => {
  if (!cfg.autoGenerate) return tt('手动填写')
  const parts = []
  if (cfg.prefix) parts.push(cfg.prefix)
  if (cfg.dateFormat) parts.push(cfg.dateFormat)
  if (cfg.includeMaterialCode) parts.push('SP001')
  parts.push('0'.repeat(cfg.seqLength) || '0001')
  return parts.join(cfg.separator || '')
})

watch(currentCat, () => loadConfig())

async function loadCategories() {
  try {
    const tree = await treeMaterialCategory()
    function flat(t, r = []) {
      (t || []).forEach(n => { r.push(n); if (n.children) flat(n.children, r) })
      return r
    }
    categoryList.value = flat(tree)
  } catch (e) {
    categoryList.value = []
  }
}

async function loadConfig() {
  try {
    const key = CONFIG_PREFIX + currentCat.value
    const res = await request.get(`/sys-config/${key}`)
    if (res && res.configValue) {
      const saved = JSON.parse(res.configValue)
      Object.assign(cfg, defaultCfg, saved)
    } else {
      Object.assign(cfg, defaultCfg)
    }
  } catch (e) {
    Object.assign(cfg, defaultCfg)
  }
}

async function saveForCat(catId) {
  const key = CONFIG_PREFIX + catId
  await request.put('/sys-config/batch', [{
    configKey: key,
    configValue: JSON.stringify(cfg),
    configName: `批次编号规则-${catId === 'default' ? '默认' : currentCatName.value}`,
    configGroup: CONFIG_GROUP,
    configType: 'json'
  }])
}

async function onSave() {
  saving.value = true
  try {
    await saveForCat(currentCat.value)
    ElMessage.success(tt('保存成功'))
  } catch (e) {
    ElMessage.error(tt('保存失败'))
  } finally {
    saving.value = false
  }
}

async function applyToAll() {
  await ElMessageBox.confirm(tt('将当前规则应用到所有货品分类？此操作不可撤销。'), tt('提示'), { type: 'warning' })
  saving.value = true
  try {
    const cats = ['default', ...categoryList.value.map(c => String(c.categoryId))]
    const items = cats.map(catId => ({
      configKey: CONFIG_PREFIX + catId,
      configValue: JSON.stringify(cfg),
      configName: `批次编号规则-${catId === 'default' ? '默认' : catId}`,
      configGroup: CONFIG_GROUP,
      configType: 'json'
    }))
    await request.put('/sys-config/batch', items)
    ElMessage.success(`${tt("已应用到 ")}${cats.length}${tt(" 个分类")}`)
  } catch (e) {
    ElMessage.error(tt('操作失败'))
  } finally {
    saving.value = false
  }
}

function onReset() {
  Object.assign(cfg, defaultCfg)
  ElMessage.info(tt('已重置为默认值'))
}

onMounted(() => { loadCategories(); loadConfig() })
</script>

<style scoped lang="scss">
.kgw-batch-no {
  background: #fff;
  min-height: 100%;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
  display: flex;
  flex-direction: column;
}
.kgw-toolbar {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  padding: 0 16px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.kgw-actions { display: flex; gap: 8px; }
.kgw-body { padding: 12px; flex: 1; }
.kgw-layout-split { display: flex; min-height: 500px; }
.kgw-side {
  width: 200px;
  border-right: 1px solid #ebeef5;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
}
.kgw-side-search { padding: 8px; }
.kgw-side-list { flex: 1; overflow: auto; padding: 4px 0; }
.kgw-side-item {
  padding: 8px 16px;
  cursor: pointer;
  font-size: 13px;
  color: #303133;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  gap: 6px;
  &:hover { background: #e6f7ff; color: #1890ff; }
  &.active {
    background: #1890ff;
    color: #fff;
    font-weight: 600;
  }
}
.kgw-main {
  flex: 1;
  padding: 24px 32px;
  background: #fff;
}
.rule-form { max-width: 800px; }
.unit {
  font-size: 13px;
  color: #606266;
  margin: 0 4px;
}
.tip {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
.example-text {
  font-family: 'Courier New', monospace;
  font-size: 14px;
  color: #1890ff;
  font-weight: 600;
  background: #f0f9ff;
  border: 1px solid #91d5ff;
  border-radius: 4px;
  padding: 6px 16px;
}
:deep(.el-button--primary), :deep(.el-button--success) {
  background-color: #1890ff; border-color: #1890ff;
}
:deep(.el-switch.is-checked .el-switch__core) { background-color: #1890ff; border-color: #1890ff; }
</style>
