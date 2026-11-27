<template>
  <div class="erp-page">
    <el-card shadow="never">
      <!-- 工具栏 -->
      <div class="erp-search-bar">
        <el-button type="primary" v-permission="perm('add')" @click="handleAdd">{{ $tt("+ 新增规则") }}</el-button>
        <el-button v-permission="perm('refresh')" @click="loadData">{{ $tt("刷新") }}</el-button>
      </div>

      <!-- 列表 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column type="index" :label="$tf('序号')" width="60" />
        <el-table-column prop="categoryName" :label="$tf('分类名')" min-width="160" show-overflow-tooltip sortable>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="handleEdit(row)" style="font-weight:500;">{{ row.categoryName || '通用规则' }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="prefix" :label="$tf('前缀')" width="110" sortable />
        <el-table-column :label="$tf('拼接分类编码')" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="row.includeCategoryCode === 1 ? 'success' : 'info'" size="small">
              {{ row.includeCategoryCode === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('包含日期')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.includeDate === 1 ? 'success' : 'info'" size="small">
              {{ row.includeDate === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="seqLength" :label="$tf('流水位数')" width="110" align="right" sortable />
        <el-table-column prop="lastSeq" :label="$tf('当前流水号')" width="130" align="right" sortable />
        <el-table-column :label="$tf('是否启用')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'" size="small">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <AuditColumns />
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="550px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="$tf('物料分类')" prop="categoryId">
          <el-select v-model="form.categoryId" :placeholder="$tt('留空表示通用规则')" filterable clearable style="width:100%" @change="onCategoryChange">
            <el-option v-for="c in categoryOptions" :key="c.categoryId" :label="c.categoryName" :value="c.categoryId" />
          </el-select>
          <div style="font-size:12px;color:#999;margin-top:4px">{{ $tt("不选分类则创建通用规则（所有分类兜底使用）") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('前缀')" prop="prefix">
          <el-input v-model="form.prefix" :placeholder="$tt('如: RM（开启拼接分类编码时可留空）')" />
          <div style="font-size:12px;color:#999;margin-top:4px">编码开头部分；若开启"拼接分类编码"可留空</div>
        </el-form-item>
        <el-form-item :label="$tf('拼接分类编码')" prop="includeCategoryCode">
          <el-switch v-model="form.includeCategoryCode" :active-value="1" :inactive-value="0" />
          <div style="font-size:12px;color:#999;margin-top:4px">
            开启后编码 = 前缀 + 各级分类编码依次拼接 + [日期] + 流水号<br/>
            例：分类 A(成品) › BCD(螺丝类) › EFG(颜色) → 编码 ABCDEFG001
          </div>
        </el-form-item>
        <el-form-item :label="$tf('包含日期')" prop="includeDate">
          <el-switch v-model="form.includeDate" :active-value="1" :inactive-value="0" />
          <div style="font-size:12px;color:#999;margin-top:4px">{{ $tt("开启后编码中插入 yyyyMMdd 日期段") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('流水位数')" prop="seqLength">
          <el-input-number v-model="form.seqLength" :min="1" :max="10" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$tf('是否启用')" prop="isEnabled">
          <el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="handleSave" :loading="saving">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="$tf('编码预览')" width="480px">
      <el-form label-width="120px">
        <el-form-item :label="$tf('分类')">
          <span>{{ previewRow?.categoryName || '通用规则' }}</span>
        </el-form-item>
        <el-form-item :label="$tf('前缀')">
          <span>{{ previewRow?.prefix || '（空）' }}</span>
        </el-form-item>
        <el-form-item :label="$tf('拼接分类编码')">
          <el-tag :type="previewRow?.includeCategoryCode === 1 ? 'success' : 'info'" size="small">
            {{ previewRow?.includeCategoryCode === 1 ? '是' : '否' }}
          </el-tag>
        </el-form-item>
        <el-form-item :label="$tf('包含日期')">
          <el-tag :type="previewRow?.includeDate === 1 ? 'success' : 'info'" size="small">
            {{ previewRow?.includeDate === 1 ? '是' : '否' }}
          </el-tag>
        </el-form-item>
        <el-form-item :label="$tf('下一编码')">
          <el-tag type="success" size="large">{{ previewCode }}</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgClose')" @click="previewVisible = false">{{ $tt("关闭") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listCodeRule, saveCodeRule } from '@/api/basedata/material-code-rule'
import { listAllMaterialCategory } from '@/api/basedata/material-category'
import AuditColumns from '@/components/AuditColumns.vue'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('basedata:material')
const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const categoryOptions = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await listCodeRule()
    tableData.value = Array.isArray(res) ? res : (res?.records || [])
  } catch (e) {
    ElMessage.error(e.message || tt('加载失败'))
  } finally {
    loading.value = false
  }
}

async function loadCategoryOptions() {
  try {
    const res = await listAllMaterialCategory()
    categoryOptions.value = Array.isArray(res) ? res : (res?.records || [])
  } catch (e) {
    console.warn('加载物料分类失败', e)
  }
}

// 新增/编辑
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const isEdit = ref(false)
const form = reactive({
  ruleId: null,
  categoryId: null,
  categoryName: '',
  prefix: '',
  includeCategoryCode: 0,
  includeDate: 0,
  seqLength: 4,
  isEnabled: 1,
  lastSeq: 0
})
const rules = {
  seqLength: [{ required: true, message: tt('请输入流水位数'), trigger: 'blur' }]
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, { ruleId: null, categoryId: null, categoryName: '', prefix: '', includeCategoryCode: 0, includeDate: 0, seqLength: 4, isEnabled: 1, lastSeq: 0 })
  dialogTitle.value = '新增编码规则'
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    ruleId: row.ruleId,
    categoryId: row.categoryId,
    categoryName: row.categoryName || '',
    prefix: row.prefix || '',
    includeCategoryCode: row.includeCategoryCode != null ? row.includeCategoryCode : 0,
    includeDate: row.includeDate != null ? row.includeDate : 0,
    seqLength: row.seqLength || 4,
    isEnabled: row.isEnabled != null ? row.isEnabled : 1,
    lastSeq: row.lastSeq || 0
  })
  dialogTitle.value = '编辑编码规则'
  dialogVisible.value = true
}

function onCategoryChange(v) {
  const c = categoryOptions.value.find(x => x.categoryId === v)
  form.categoryName = c ? c.categoryName : ''
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await saveCodeRule({ ...form })
    ElMessage.success(tt('保存成功'))
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

function resetForm() {
  formRef.value && formRef.value.resetFields()
}

onMounted(() => {
  loadData()
  loadCategoryOptions()
})
useAutoRefresh(() => {
  loadData()
  loadCategoryOptions()
})
</script>

<style scoped>
.erp-page { padding: 16px; }
.erp-search-bar { margin-bottom: 16px; display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
</style>
