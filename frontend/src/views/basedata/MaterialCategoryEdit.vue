<template>
  <BaseEdit
    :title="$tt('物料分类')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    :option-loaders="optionLoaders"
    :on-create-init="onCreateInit"
    id-field="categoryId"
    list-route="/basedata/material-category"
    @select-change="onSelectChange"
    custom-field-entity="material_category"
    :show-change-record="true"
    change-record-bill-type="物料分类"
    change-record-bill-field="categoryCode"
    auto-doc-type="BD_MATERIAL_CATEGORY"
    auto-doc-code-field="categoryCode"
    page-key="material_category"
  />
</template>

<script setup>
import BaseEdit from '@/components/BaseEdit.vue'
import { useRoute } from 'vue-router'
import { getMaterialCategory, saveMaterialCategory, updateMaterialCategory, listAllMaterialCategory } from '@/api/basedata/material-category'

const route = useRoute()

function buildCategoryTree(list, excludeId) {
  const map = {}
  const roots = []
  list.forEach(c => { map[c.categoryId] = { categoryId: c.categoryId, categoryName: c.categoryName, parentId: c.parentId, children: [] } })
  list.forEach(c => {
    const node = map[c.categoryId]
    if (c.parentId && map[c.parentId]) {
      map[c.parentId].children.push(node)
    } else {
      roots.push(node)
    }
  })
  function trim(nodes) {
    return nodes.filter(n => n.categoryId !== excludeId)
  }
  function process(nodes) {
    nodes.forEach(n => {
      if (n.children.length === 0) {
        delete n.children
      } else {
        n.children = trim(n.children)
        if (n.children.length > 0) process(n.children)
        else delete n.children
      }
    })
  }
  const filtered = trim(roots)
  process(filtered)
  return filtered
}

const optionLoaders = {
  parentId: async () => {
    const list = await listAllMaterialCategory()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    const editId = window.location.pathname.split('/').pop()
    const excludeId = /^\d+$/.test(editId) ? Number(editId) : null
    return buildCategoryTree(arr, excludeId)
  }
}

const sections = [
  {
    title: '基本信息',
    subtitle: '分类的标识和层级',
    fields: [
      { prop: 'categoryCode', label: '分类编码', placeholder: '如 1001' },
      { prop: 'categoryName', label: '分类名称', required: true, placeholder: '如 原材料' },
      { prop: 'parentId', label: '上级分类', type: 'cascader',
        cascaderProps: { value: 'categoryId', label: 'categoryName', children: 'children', checkStrictly: true, emitPath: false },
        placeholder: '留空表示顶级分类' }
    ]
  },
  {
    title: '层级与排序',
    subtitle: '分类的层级和排序',
    fields: [
      { prop: 'categoryLevel', label: '分类层级', type: 'number', default: 1 },
      { prop: 'sortOrder', label: '排序', type: 'number', default: 0 }
    ]
  },
  {
    title: '状态',
    subtitle: '启用状态',
    fields: [
      { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
        { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

function onSelectChange({ field, value, form }) {
  if (field.prop === 'parentId') {
    if (value) {
      findLevel(value).then(parentLevel => {
        form.categoryLevel = parentLevel + 1
      })
    } else {
      form.categoryLevel = 1
    }
  }
}

async function findLevel(categoryId) {
  const list = await listAllMaterialCategory()
  const arr = Array.isArray(list) ? list : (list?.records || [])
  const map = {}
  arr.forEach(c => { map[c.categoryId] = c })
  let level = 0
  let current = map[categoryId]
  while (current) {
    level++
    current = current.parentId ? map[current.parentId] : null
  }
  return level
}

async function fetchData(id) {
  return await getMaterialCategory(id)
}
async function onSave(data, isEdit) {
  if (isEdit) await updateMaterialCategory(data)
  else await saveMaterialCategory(data)
}

// 新建模式初始化：从列表页"新增子分类"跳转时，自动带入上级分类与层级
async function onCreateInit(form, isEdit) {
  if (isEdit) return
  const parentId = route.query.parentId
  if (parentId) {
    const pid = Number(parentId)
    if (!Number.isNaN(pid)) {
      form.parentId = pid
      try {
        const parentLevel = await findLevel(pid)
        form.categoryLevel = parentLevel + 1
      } catch (e) {
        // 忽略层级计算失败
      }
    }
  }
}
</script>
