<template>
  <div class="field-name-setting">
    <el-alert type="info" :closable="false" class="guide-tip">
      <template #title>
        全站字段显示名自定义：这里改的是「叫法」，只影响界面显示文字，不影响数据、校验、导入导出。
        修改保存后全站立即生效；恢复默认即改回系统原叫法。
      </template>
    </el-alert>

    <!-- 添加行 -->
    <div class="add-bar">
      <el-select
        v-model="pickField"
        filterable
        allow-create
        default-first-option
        :placeholder="$tt('选择或输入原字段名（如：物料）')"
        class="add-field"
      >
        <el-option v-for="f in commonFields" :key="f" :label="f" :value="f" />
      </el-select>
      <el-input
        v-model="pickName"
        :placeholder="$tt('新显示名（最多20字）')"
        maxlength="20"
        class="add-name"
      />
      <el-button type="primary" :disabled="!pickField || !pickName" @click="addRow">{{ $tt("添加") }}</el-button>
      <span class="add-hint">{{ $tt("例如把「物料」改叫「商品」，全站所有页面的「物料」都会变成「商品」") }}</span>
    </div>

    <!-- 已有覆盖 -->
    <el-table :data="rows" border size="small" class="label-table" empty-text="当前全部显示系统默认名称">
      <el-table-column prop="fieldKey" :label="$tf('原字段名')" width="220" />
      <el-table-column prop="displayName" :label="$tf('当前显示名')" width="220">
        <template #default="{ row }">
          <el-input v-model="row.displayName" size="small" maxlength="20" />
        </template>
      </el-table-column>
      <el-table-column prop="updatedBy" :label="$tf('最近修改人')" width="140">
        <template #default="{ row }">{{ row.updatedBy || '—' }}</template>
      </el-table-column>
      <el-table-column prop="updatedAt" :label="$tf('修改时间')" width="170">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column :label="$tt('操作')" min-width="120">
        <template #default="{ row }">
          <el-button v-permission="perm('resetDefault')" size="small" type="danger" plain @click="resetRow(row)">{{ $tt("恢复默认") }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="save-bar">
      <el-button v-permission="perm('save')" type="primary" :loading="saving" :disabled="rows.length === 0" @click="saveAll">
        {{ $tt("保存全部修改") }}
      </el-button>
      <el-button v-permission="perm('resetDefault')" :disabled="rows.length === 0" @click="resetAll">{{ $tt("全部恢复默认") }}</el-button>
      <span class="save-hint">{{ saveHint }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listFieldNames, saveFieldNames, resetFieldNames } from '@/api/system/fieldLabel'
import { setFieldLabelOverrides, reloadFieldLabelsForScope } from '@/i18n'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
// 按钮权限：字段名称设置（保存/恢复默认；与后端「管理员或显式勾选」同源）
const perm = usePagePerm('system:field-name')

/** 常用字段快速添加清单（全站字段名统一改，改前先想清楚全局叫法） */
const commonFields = [
  '物料', '物料编码', '物料名称', '规格型号', '单位', '单价', '数量', '金额', '税率', '价税合计',
  '客户', '供应商', '仓库', '部门', '销售员', '采购员', '批次号', '订单日期', '交货日期', '备注',
  '联系人', '联系电话', '地址', '结算方式', '付款条件', '运输方式', '价格等级', '整单折扣', '折扣率', '折扣额',
  '币种', '汇率', '折前金额', '税额', '成本', '库存量', '可用量', '审核人', '制单人', '来源单号'
]

const rows = ref([])
const pickField = ref('')
const pickName = ref('')
const saving = ref(false)

const saveHint = computed(() => tt(`共 ${rows.value.length} 个字段名已自定义`))

onMounted(loadData)

async function loadData() {
  try {
    const list = await listFieldNames()
    rows.value = (Array.isArray(list) ? list : []).map(r => ({
      fieldKey: r.field_key,
      displayName: r.display_name,
      updatedBy: r.updated_by || '',
      updatedAt: r.updated_at || ''
    }))
  } catch (_) {
    rows.value = []
  }
  // 同步刷新全局覆盖（保持与后端一致）
  reloadFieldLabelsForScope()
}

function addRow() {
  const key = (pickField.value || '').trim()
  const name = (pickName.value || '').trim()
  if (!key || !name) return
  const exist = rows.value.find(r => r.fieldKey === key)
  if (exist) {
    exist.displayName = name
  } else {
    rows.value.push({ fieldKey: key, displayName: name, updatedBy: '', updatedAt: '' })
  }
  pickField.value = ''
  pickName.value = ''
  ElMessage.success(tt('已加入清单，点「保存全部修改」后生效'))
}

async function resetRow(row) {
  saving.value = true
  try {
    await saveFieldNames([{ fieldKey: row.fieldKey, displayName: '' }])
    ElMessage.success(tt('已恢复默认，全站立即生效'))
    await loadData()
  } finally {
    saving.value = false
  }
}

async function saveAll() {
  const items = rows.value
    .filter(r => r.fieldKey && r.displayName && r.displayName.trim())
    .map(r => ({ fieldKey: r.fieldKey.trim(), displayName: r.displayName.trim() }))
  if (items.length === 0) {
    ElMessage.warning(tt('没有要保存的内容'))
    return
  }
  saving.value = true
  try {
    await saveFieldNames(items)
    // 立即套用新映射（不整页刷新也全站生效）
    const map = {}
    items.forEach(i => { map[i.fieldKey] = i.displayName })
    setFieldLabelOverrides(map)
    ElMessage.success(tt('保存成功，全站字段叫法已更新'))
    await loadData()
  } finally {
    saving.value = false
  }
}

async function resetAll() {
  try {
    await ElMessageBox.confirm(tt('将清空全部自定义字段名，全站恢复系统默认叫法。确定继续吗？'), tt('全部恢复默认'), {
      type: 'warning',
      confirmButtonText: tt('确定恢复'),
      cancelButtonText: tt('取消')
    })
  } catch (_) {
    return
  }
  saving.value = true
  try {
    await resetFieldNames()
    setFieldLabelOverrides({})
    ElMessage.success(tt('已全部恢复默认'))
    rows.value = []
  } finally {
    saving.value = false
  }
}

function formatTime(t) {
  if (!t) return ''
  const s = String(t)
  return s.length >= 19 ? s.slice(0, 19).replace('T', ' ') : s
}
</script>

<style scoped>
.field-name-setting {
  padding: 8px 4px;
}
.guide-tip {
  margin-bottom: 14px;
}
.add-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.add-field {
  width: 260px;
}
.add-name {
  width: 220px;
}
.add-hint {
  color: #909399;
  font-size: 12px;
}
.label-table {
  margin-bottom: 14px;
}
.save-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.save-hint {
  color: #909399;
  font-size: 12px;
}
</style>
