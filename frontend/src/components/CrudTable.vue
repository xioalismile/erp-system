<template>
  <!--
    通用CRUD表格组件 - 基础资料模块统一模板
    用法示例:
    <CrudTable
      title="计量单位"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
    />
  -->
  <div class="erp-card">
    <!-- 搜索栏 -->
    <div class="erp-search-bar" v-if="searchFields && searchFields.length">
      <div class="erp-search-item" v-for="f in searchFields" :key="f.prop">
        <span class="label">{{ f.label }}:</span>
        <el-input
          v-model="searchForm[f.prop]"
          :placeholder="f.placeholder || '请输入'"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
      </div>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </div>

    <!-- 操作按钮区 -->
    <div class="erp-toolbar">
      <div class="erp-toolbar-left">
        <el-button type="primary" :icon="Plus" @click="handleAdd" v-permission="permAdd">新增</el-button>
        <el-button type="danger" :icon="Delete" :disabled="!selectedRows.length" @click="handleBatchDelete" v-permission="permDelete">批量删除</el-button>
      </div>
      <div class="erp-toolbar-right">
        <el-button :icon="RefreshRight" circle @click="loadData" />
      </div>
    </div>

    <!-- 表格 -->
    <el-table
      :data="tableData"
      v-loading="loading"
      border
      stripe
      height="100%"
      @selection-change="onSelectionChange"
      style="width: 100%"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth || 120"
        :formatter="col.formatter"
        :show-overflow-tooltip="true"
      >
        <template #default="scope" v-if="col.slot">
          <slot :name="col.slot" :row="scope.row" :index="scope.$index" />
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" :width="operateWidth">
        <template #default="scope">
          <el-button link type="primary" :icon="Edit" @click="handleEdit(scope.row)" v-permission="permEdit">编辑</el-button>
          <el-button link type="danger" :icon="Delete" @click="handleDelete(scope.row)" v-permission="permDelete">删除</el-button>
          <slot name="extra-ops" :row="scope.row" />
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="erp-pagination">
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="page.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>
  </div>

  <!-- 新增/编辑弹窗 -->
  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? `编辑${title}` : `新增${title}`"
    width="640px"
    :close-on-click-modal="false"
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" class="erp-form-dialog">
      <template v-for="f in formFields" :key="f.prop">
        <el-form-item :label="f.label" :prop="f.prop" v-if="!f.hidden">
          <el-select
            v-if="f.type === 'select'"
            v-model="formData[f.prop]"
            :placeholder="f.placeholder || '请选择'"
            :disabled="f.disabled"
            style="width: 100%"
          >
            <el-option v-for="o in f.options" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-input-number
            v-else-if="f.type === 'number'"
            v-model="formData[f.prop]"
            :min="0"
            :precision="f.precision || 0"
            :disabled="f.disabled"
            style="width: 100%"
          />
          <el-switch
            v-else-if="f.type === 'switch'"
            v-model="formData[f.prop]"
            :active-value="f.activeValue ?? 1"
            :inactive-value="f.inactiveValue ?? 0"
            :disabled="f.disabled"
          />
          <el-date-picker
            v-else-if="f.type === 'date'"
            v-model="formData[f.prop]"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="f.disabled"
            style="width: 100%"
          />
          <el-input
            v-else
            v-model="formData[f.prop]"
            :type="f.textarea ? 'textarea' : 'text'"
            :placeholder="f.placeholder || '请输入'"
            :disabled="f.disabled"
            :rows="f.rows || 3"
          />
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Edit, RefreshRight } from '@element-plus/icons-vue'

const props = defineProps({
  title: { type: String, default: '记录' },
  // 列定义: [{ prop, label, width, minWidth, formatter, slot }]
  columns: { type: Array, default: () => [] },
  // 搜索字段: [{ prop, label, placeholder }]
  searchFields: { type: Array, default: () => [] },
  // 表单字段: [{ prop, label, type, options, required, disabled, hidden, placeholder, precision, textarea, rows, activeValue, inactiveValue }]
  formFields: { type: Array, default: () => [] },
  // 数据加载函数: (params) => Promise<{records, total}>
  fetchData: { type: Function, required: true },
  // 保存函数: (formData, isEdit) => Promise
  onSave: { type: Function, required: true },
  // 删除函数: (row) => Promise
  onDelete: { type: Function, required: true },
  // 权限码
  permAdd: { type: String, default: '' },
  permEdit: { type: String, default: '' },
  permDelete: { type: String, default: '' },
  // 主键字段名
  idField: { type: String, default: 'id' }
})

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const selectedRows = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const formData = reactive({})
const searchForm = reactive({})
const page = reactive({ current: 1, size: 20, total: 0 })

const operateWidth = computed(() => {
  return props.permEdit || props.permDelete ? 160 : 120
})

// 根据formFields自动生成校验规则
const formRules = computed(() => {
  const rules = {}
  props.formFields.forEach(f => {
    if (f.required) {
      rules[f.prop] = [{ required: true, message: `${f.label}不能为空`, trigger: f.type === 'select' ? 'change' : 'blur' }]
    }
  })
  return rules
})

async function loadData() {
  loading.value = true
  try {
    const params = { current: page.current, size: page.size, ...searchForm }
    const res = await props.fetchData(params)
    tableData.value = res.records || res || []
    page.total = res.total || tableData.value.length
  } catch (e) {
    // 错误已在拦截器处理
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows) {
  selectedRows.value = rows
}

function handleSearch() {
  page.current = 1
  loadData()
}

function handleReset() {
  Object.keys(searchForm).forEach(k => delete searchForm[k])
  page.current = 1
  loadData()
}

function handleAdd() {
  isEdit.value = false
  props.formFields.forEach(f => {
    formData[f.prop] = f.default !== undefined ? f.default : (f.type === 'switch' ? (f.inactiveValue ?? 0) : (f.type === 'number' ? 0 : ''))
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  props.formFields.forEach(f => {
    formData[f.prop] = row[f.prop] ?? formData[f.prop]
  })
  formData[props.idField] = row[props.idField]
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await props.onSave({ ...formData }, isEdit.value)
      ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    } catch (e) {
      // 错误已在拦截器处理
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除该${props.title}吗？`, '提示', { type: 'warning' })
    await props.onDelete(row)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {} // 拦截器已处理
  }
}

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedRows.value.length} 条记录吗？`, '提示', { type: 'warning' })
    for (const row of selectedRows.value) {
      await props.onDelete(row)
    }
    ElMessage.success('批量删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {}
  }
}

function resetForm() {
  formRef.value?.resetFields()
  Object.keys(formData).forEach(k => delete formData[k])
}

onMounted(loadData)

defineExpose({ loadData, handleAdd })
</script>
