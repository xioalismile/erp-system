<template>
  <div class="erp-page">
    <!-- 顶部工具栏 -->
    <el-card shadow="never">
      <div class="erp-search-bar">
        <el-input v-model="searchKeyword" :placeholder="$tt('编码/名称')" clearable style="width:220px" @keyup.enter="loadData" @clear="loadData" />
        <el-button type="primary" v-permission="perm('query')" @click="loadData">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
        <div style="flex:1"></div>
        <el-button type="primary" @click="handleAdd()" v-permission="'basedata:material-category:add'">{{ $tt("+ 新增顶级分类") }}</el-button>
        <el-button type="info" v-permission="perm('downloadTemplate')" @click="handleDownloadTemplate">{{ $tt("下载导入模板") }}</el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="handleImport"
          accept=".xlsx,.xls"
          style="margin-left: 10px; display: inline-flex;"
        >
          <el-button type="warning" v-permission="'basedata:material-category:add'">{{ $tt("导入分类") }}</el-button>
        </el-upload>
      </div>
    </el-card>

    <!-- 树形表格 -->
    <el-card shadow="never" style="margin-top:12px;">
      <el-table
        ref="tableRef"
        :data="treeData"
        v-loading="loading"
        row-key="categoryId"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        default-expand-all
        border
        stripe
      >
        <el-table-column prop="categoryCode" :label="$tf('分类编码')" width="200" fixed="left">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="handleEdit(row)" style="font-weight:500;">{{ row.categoryCode }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" :label="$tf('分类名称')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="$tf('层级')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.categoryLevel)" size="small">{{ row.categoryLevel }}级</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" :label="$tf('排序')" width="90" align="center" />
        <el-table-column :label="$tf('状态')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ $dt(row.status || '启用') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$tf('备注')" min-width="160" show-overflow-tooltip />
      </el-table>

      <div class="tree-tip">
        <el-icon><InfoFilled /></el-icon>
        共 {{ total }} 条分类，已默认展开全部层级。点击「新增子分类」可在选中分类下级创建，编码规则：1位系列码 + 2位型号码 + 3位芯片类型码 + 4位流水号。
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, computed, onMounted, onActivated, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import {
  listAllMaterialCategory
} from '@/api/basedata/material-category'
import { downloadTemplate, importData } from '@/utils/exportUtil'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()
const router = useRouter()
const loading = ref(false)
const rawList = ref([])
const searchKeyword = ref('')
const tableRef = ref(null)

// keep-alive 页签切换（v-show 隐藏→显示）后，带 fixed 列的表格不会自动重算布局，
// fixed 克隆层会错位盖住上方搜索栏/按钮，需手动 doLayout 重算
function relayoutTable() {
  nextTick(() => {
    tableRef.value?.doLayout?.()
  })
}
onActivated(relayoutTable)

// 平铺列表 -> 树结构
const treeData = computed(() => buildTree(rawList.value, searchKeyword.value.trim()))

const total = computed(() => rawList.value.length)

function buildTree(list, keyword) {
  const map = {}
  const roots = []
  // 先建索引
  list.forEach(c => {
    map[c.categoryId] = { ...c, children: [] }
  })
  // 再挂载父子关系
  list.forEach(c => {
    const node = map[c.categoryId]
    if (c.parentId && map[c.parentId]) {
      map[c.parentId].children.push(node)
    } else {
      roots.push(node)
    }
  })
  // 关键字过滤：保留命中节点及其所有祖先链与直接子树
  if (!keyword) {
    // 清理空 children，避免展开箭头
    cleanupEmpty(roots)
    return roots
  }
  const kw = keyword.toLowerCase()
  const matched = new Set()
  function collectMatch(nodes) {
    nodes.forEach(n => {
      const hit = (n.categoryCode || '').toLowerCase().includes(kw)
        || (n.categoryName || '').toLowerCase().includes(kw)
      if (hit) {
        matched.add(n.categoryId)
      }
      if (n.children && n.children.length) collectMatch(n.children)
    })
  }
  collectMatch(roots)
  function filterTree(nodes) {
    return nodes
      .map(n => ({ ...n, children: n.children ? filterTree(n.children) : [] }))
      .filter(n => matched.has(n.categoryId) || (n.children && n.children.length > 0))
  }
  const filtered = filterTree(roots)
  cleanupEmpty(filtered)
  return filtered
}

function cleanupEmpty(nodes) {
  nodes.forEach(n => {
    if (n.children && n.children.length === 0) {
      delete n.children
    } else if (n.children) {
      cleanupEmpty(n.children)
    }
  })
}

async function loadData() {
  loading.value = true
  try {
    const res = await listAllMaterialCategory()
    rawList.value = Array.isArray(res) ? res : (res?.records || [])
  } catch (e) {
    ElMessage.error(tt('加载分类失败：') + (e.message || ''))
  } finally {
    loading.value = false
    relayoutTable()
  }
}

function handleReset() {
  searchKeyword.value = ''
  loadData()
}

function handleAdd(parentRow) {
  if (parentRow && parentRow.categoryId) {
    router.push({ path: '/basedata/material-category/new', query: { parentId: parentRow.categoryId } })
  } else {
    router.push('/basedata/material-category/new')
  }
}

function handleEdit(row) {
  router.push(`/basedata/material-category/edit/${row.categoryId}`)
}

// 层级标签颜色：1级主色、2级成功、3级警告、4级+信息
function levelTagType(level) {
  const map = { 1: 'primary', 2: 'success', 3: 'warning', 4: 'info' }
  return map[level] || 'info'
}

// 导入导出
async function handleDownloadTemplate() {
  try {
    await downloadTemplate('materialCategory')
    ElMessage.success(tt('模板下载成功'))
  } catch (e) {
    ElMessage.error(tt('模板下载失败：') + (e.message || ''))
  }
}

async function handleImport(file) {
  try {
    const result = await importData('materialCategory', file)
    ElMessage.success(`${tt("导入成功: ")}${result.successCount}${tt("条, 失败: ")}${result.failCount}${tt("条")}`)
    loadData()
  } catch (e) {
    ElMessage.error(tt('导入失败：') + (e.message || ''))
  }
  return false
}

onMounted(loadData)
useAutoRefresh(loadData)
</script>

<style scoped>
.erp-page { padding: 0; }
.erp-search-bar { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.tree-tip {
  margin-top: 12px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  color: #606266;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>