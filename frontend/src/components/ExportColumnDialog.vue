<template>
  <el-dialog
    v-model="visible"
    :title="$tf('自定义导出列')"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="export-column-dialog">
      <!-- 全选/反选 -->
      <div class="dialog-toolbar">
        <el-checkbox v-model="checkAll" :indeterminate="isIndeterminate" @change="handleCheckAll">{{ $tt("全选") }}</el-checkbox>
        <el-button size="small" link type="primary" v-permission="perm('columnSetting')" @click="handleInvert">{{ $tt("反选") }}</el-button>
        <span class="selected-count">已选 {{ selectedFields.length }} / {{ fields.length }}</span>
      </div>

      <el-divider style="margin: 8px 0;" />

      <!-- 字段列表 -->
      <div class="field-list">
        <el-checkbox-group v-model="selectedFields">
          <div v-for="field in fields" :key="field.fieldName" class="field-item">
            <el-checkbox :value="field.fieldName" :label="field.fieldName">
              <span>{{ $tf(field.label) }}</span>
              <el-tag v-if="field.required" size="small" type="danger" style="margin-left: 6px;">{{ $tt("必填") }}</el-tag>
            </el-checkbox>
          </div>
        </el-checkbox-group>
      </div>

      <div v-if="fields.length === 0" class="empty-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ loading ? '加载中...' : '暂无可导出字段' }}</span>
      </div>
    </div>

    <template #footer>
      <el-button v-permission="perm('dlgCancel')" @click="visible = false">{{ $tt("取消") }}</el-button>
      <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
      <el-button type="primary" v-permission="perm('export')" :loading="exporting" @click="handleExport">
        导出选中列
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { getExportConfig, exportDataWithSelectedFields } from '@/utils/exportUtil'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('report:custom')

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  entityName: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const fields = ref([])
const selectedFields = ref([])
const loading = ref(false)
const exporting = ref(false)

// 全选状态
const checkAll = computed({
  get: () => fields.value.length > 0 && selectedFields.value.length === fields.value.length,
  set: () => {}
})
const isIndeterminate = computed(() =>
  selectedFields.value.length > 0 && selectedFields.value.length < fields.value.length
)

// 加载导出配置
watch(() => props.modelValue, async (val) => {
  if (val && props.entityName) {
    await loadConfig()
  }
}, { immediate: true })

async function loadConfig() {
  loading.value = true
  try {
    const config = await getExportConfig(props.entityName)
    // 只显示非 hidden 的字段
    fields.value = (config?.fields || []).filter(f => !f.hidden)
    // 默认全选
    selectedFields.value = fields.value.map(f => f.fieldName)
  } catch (err) {
    ElMessage.error(tt('获取导出字段失败：') + (err.message || tt('未知错误')))
    fields.value = []
    selectedFields.value = []
  } finally {
    loading.value = false
  }
}

function handleCheckAll(val) {
  selectedFields.value = val ? fields.value.map(f => f.fieldName) : []
}

function handleInvert() {
  const all = fields.value.map(f => f.fieldName)
  selectedFields.value = all.filter(f => !selectedFields.value.includes(f))
}

function handleReset() {
  selectedFields.value = fields.value.map(f => f.fieldName)
}

async function handleExport() {
  if (selectedFields.value.length === 0) {
    ElMessage.warning(tt('请至少选择一个字段'))
    return
  }
  exporting.value = true
  try {
    await exportDataWithSelectedFields(props.entityName, selectedFields.value)
    ElMessage.success(tt('导出成功'))
    emit('success')
    visible.value = false
  } catch (err) {
    // 错误已在 exportUtil 处理
  } finally {
    exporting.value = false
  }
}

function handleClose() {
  fields.value = []
  selectedFields.value = []
}
</script>

<style scoped>
.export-column-dialog { padding: 0 4px; }
.dialog-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0;
}
.dialog-toolbar .selected-count {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
}
.field-list {
  max-height: 400px;
  overflow-y: auto;
  padding: 8px 4px;
}
.field-item {
  padding: 6px 0;
  border-bottom: 1px dashed #f0f0f0;
}
.field-item:last-child { border-bottom: none; }
.empty-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 40px 0;
  color: #909399;
  font-size: 13px;
}
</style>
