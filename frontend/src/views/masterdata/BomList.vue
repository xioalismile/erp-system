<template>
  <div class="app-container">
    <div class="filter-bar">
      <el-input v-model="parentMaterialCode" placeholder="父件编码" class="filter-input" @keyup.enter="loadBomList" />
      <el-button type="primary" @click="loadBomList">查询</el-button>
      <el-button type="success" @click="showAddDialog">新增BOM项</el-button>
      <el-button type="warning" @click="showExpandDialog">BOM展开</el-button>
    </div>

    <el-table :data="bomList" border>
      <el-table-column prop="parentMaterialCode" label="父件编码" />
      <el-table-column prop="childMaterialCode" label="子件编码" />
      <el-table-column label="用量">
        <template #default="scope">
          {{ scope.row.usageQuantity }} {{ getUnit(scope.row.childMaterialCode) }}
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" />
      <el-table-column prop="effectiveDate" label="生效日期" />
      <el-table-column prop="expiryDate" label="失效日期" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="editBom(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteBom(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="isEdit ? '编辑BOM' : '新增BOM项'" :visible.sync="showDialog" width="500px">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="父件编码" required>
          <el-input v-model="formData.parentMaterialCode" />
        </el-form-item>
        <el-form-item label="子件编码" required>
          <el-input v-model="formData.childMaterialCode" />
        </el-form-item>
        <el-form-item label="用量" required>
          <el-input type="number" v-model="formData.usageQuantity" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="formData.version" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="formData.effectiveDate" type="date" />
        </el-form-item>
        <el-form-item label="失效日期">
          <el-date-picker v-model="formData.expiryDate" type="date" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="saveBom">保存</el-button>
      </div>
    </el-dialog>

    <el-dialog title="BOM展开（树形结构）" :visible.sync="showExpandModal" width="600px">
      <el-form :model="expandData" inline>
        <el-form-item label="产品编码">
          <el-input v-model="expandData.materialCode" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input type="number" v-model="expandData.quantity" />
        </el-form-item>
        <el-button type="primary" @click="runExpand">展开</el-button>
      </el-form>
      <el-divider v-if="expandResult" />
      <div v-if="expandResult" class="bom-tree">
        <el-tree :data="treeData" :props="treeProps" default-expand-all>
          <template #default="{ node, data }">
            <span class="tree-node">
              <span class="material-code">{{ data.materialCode }}</span>
              <span class="material-name">{{ data.materialName }}</span>
              <el-tag v-if="data.usageQuantity" size="small" type="warning">用量: {{ data.usageQuantity }}</el-tag>
              <el-tag v-if="data.totalQuantity" size="small" type="success">× {{ data.totalQuantity }}</el-tag>
              <el-tag v-if="!data.children || data.children.length === 0" size="small" type="info">叶物料</el-tag>
            </span>
          </template>
        </el-tree>
      </div>
      <el-divider v-if="flattenResult && flattenResult.length > 0" />
      <div v-if="flattenResult && flattenResult.length > 0">
        <h4>物料需求汇总（扁平化）</h4>
        <el-table :data="flattenResult" border size="small" max-height="300">
          <el-table-column prop="materialCode" label="物料编码" />
          <el-table-column prop="materialName" label="物料名称" />
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column prop="totalQuantity" label="总需求量" />
        </el-table>
      </div>
      <div slot="footer">
        <el-button @click="showExpandModal = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { api } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const parentMaterialCode = ref('')
const bomList = ref([])
const showDialog = ref(false)
const isEdit = ref(false)
const showExpandModal = ref(false)
const expandResult = ref(null)
const flattenResult = ref([])
const treeData = ref([])
const treeProps = {
  children: 'children',
  label: 'materialCode'
}

const formData = reactive({
  id: null,
  parentMaterialCode: '',
  childMaterialCode: '',
  usageQuantity: 1,
  version: '1.0',
  effectiveDate: null,
  expiryDate: null
})

const expandData = reactive({
  materialCode: '',
  quantity: 1
})

const loadBomList = async () => {
  if (!parentMaterialCode.value) {
    ElMessage.warning('请输入父件编码')
    return
  }
  const res = await api.get('/api/bom/list', { params: { parentMaterialCode: parentMaterialCode.value } })
  bomList.value = res.data
}

const getUnit = (materialCode) => {
  return ''
}

const showAddDialog = () => {
  isEdit.value = false
  Object.assign(formData, {
    id: null,
    parentMaterialCode: parentMaterialCode.value,
    childMaterialCode: '',
    usageQuantity: 1,
    version: '1.0',
    effectiveDate: null,
    expiryDate: null
  })
  showDialog.value = true
}

const editBom = (row) => {
  isEdit.value = true
  Object.assign(formData, row)
  showDialog.value = true
}

const saveBom = async () => {
  if (isEdit.value) {
    await api.put(`/api/bom/${formData.id}`, formData)
    ElMessage.success('更新成功')
  } else {
    await api.post('/api/bom', formData)
    ElMessage.success('新增成功')
  }
  showDialog.value = false
  loadBomList()
}

const deleteBom = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除BOM项 ${row.parentMaterialCode} → ${row.childMaterialCode} 吗?`, '提示')
    await api.delete(`/api/bom/${row.id}`)
    ElMessage.success('删除成功')
    loadBomList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const showExpandDialog = () => {
  expandResult.value = null
  flattenResult.value = []
  treeData.value = []
  showExpandModal.value = true
}

const runExpand = async () => {
  if (!expandData.materialCode) {
    ElMessage.warning('请输入产品编码')
    return
  }
  try {
    const [treeRes, flatRes] = await Promise.all([
      api.get('/api/bom/expand', { params: { materialCode: expandData.materialCode, quantity: expandData.quantity } }),
      api.get('/api/bom/flatten', { params: { materialCode: expandData.materialCode, quantity: expandData.quantity } })
    ])
    expandResult.value = treeRes.data
    flattenResult.value = flatRes.data
    treeData.value = [treeRes.data]
  } catch (e) {
    ElMessage.error('BOM展开失败')
  }
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}
.filter-input {
  width: 200px;
}
.bom-tree {
  margin: 15px 0;
  max-height: 400px;
  overflow-y: auto;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
}
.material-code {
  font-weight: bold;
  color: #409EFF;
}
.material-name {
  color: #666;
}
</style>
