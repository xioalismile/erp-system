<template>
  <el-select
    v-model="inner"
    :placeholder="placeholder"
    filterable clearable :filter-method="doFilter"
    :loading="loading" style="width:100%"
    @change="onChange"
    @visible-change="onVisibleChange"
  >
    <el-option-group v-for="g in visibleGroups" :key="g.id" :label="$tf(g.label)">
      <el-option
        v-for="leaf in g.leaves"
        :key="leaf.id"
        :label="$tf(leaf.label)"
        :value="leaf.id"
      >
        <span class="cat-leaf-name">{{ leaf.nameText }}</span>
        <span v-if="leaf.pathText !== leaf.nameText" class="cat-leaf-path">{{ leaf.pathText }}</span>
      </el-option>
    </el-option-group>
    <el-option v-if="visibleGroups.length === 0" :label="$tf('无匹配分类')" :value="'__none__'" disabled />
  </el-select>
</template>

<script setup>
/**
 * 物料分类分组选择器
 * 左侧分组头 = 一级系列（不可选），选项 = 最下级分类（叶子）
 * 一次点击展开全部分组，直接点叶子即选；支持按编码/名称/系列搜索
 */
import { ref, computed, watch, onMounted } from 'vue'
import { listAllMaterialCategory } from '@/api/basedata/material-category'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  placeholder: { type: String, default: '请选择分类（支持搜索）' }
})
const emit = defineEmits(['update:modelValue', 'change'])
const inner = ref(props.modelValue)
watch(() => props.modelValue, v => { if (v !== inner.value) inner.value = v })

const loading = ref(false)
const keyword = ref('')
const groups = ref([])

/** 将扁平分类列表构建为树结构 */
function buildTree(list) {
  const map = {}
  const roots = []
  list.forEach(c => { map[c.categoryId] = { ...c, children: [] } })
  list.forEach(c => {
    const node = map[c.categoryId]
    if (c.parentId && map[c.parentId]) map[c.parentId].children.push(node)
    else roots.push(node)
  })
  return roots
}

/** 递归收集一个节点下的所有叶子，并拼接路径文本 */
function collectLeaves(node, parentNames, rootText, out) {
  const selfText = `${node.categoryCode || ''} ${node.categoryName || ''}`.trim()
  const path = [...parentNames, selfText]
  if (!node.children || node.children.length === 0) {
    out.push({
      id: node.categoryId,
      nameText: selfText,
      label: path.join(' / '),
      pathText: path.join(' / '),
      searchText: (rootText + ' ' + path.join(' ')).toLowerCase()
    })
    return
  }
  node.children.forEach(ch => collectLeaves(ch, path, rootText, out))
}

async function loadTree() {
  loading.value = true
  try {
    const res = await listAllMaterialCategory()
    const arr = Array.isArray(res) ? res : (res?.records || [])
    const roots = buildTree(arr)
    const gs = []
    roots.forEach(root => {
      const rootText = `${root.categoryCode || ''} ${root.categoryName || ''}`.trim()
      const leaves = []
      if (!root.children || root.children.length === 0) {
        // 一级本身就是叶子（无下级）：可直接选择
        leaves.push({
          id: root.categoryId,
          nameText: rootText,
          label: rootText,
          pathText: rootText,
          searchText: rootText.toLowerCase()
        })
      } else {
        root.children.forEach(ch => collectLeaves(ch, [], rootText, leaves))
      }
      if (leaves.length > 0) {
        gs.push({ id: root.categoryId, label: rootText, leaves })
      }
    })
    groups.value = gs
  } catch (e) {
    console.warn('加载物料分类失败', e)
  } finally {
    loading.value = false
  }
}
onMounted(loadTree)

/** 自定义过滤：按 编码/名称/系列 匹配；同时保证当前已选项始终渲染（避免回显丢失） */
const visibleGroups = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return groups.value
  const out = []
  groups.value.forEach(g => {
    const leaves = g.leaves.filter(l => l.searchText.includes(kw) || l.id === inner.value)
    if (leaves.length > 0) out.push({ ...g, leaves })
  })
  return out
})

function doFilter(q) { keyword.value = q || '' }
function onVisibleChange(vis) { if (!vis) keyword.value = '' }
function onChange(v) {
  const val = (v === undefined || v === '__none__') ? null : v
  inner.value = val
  emit('update:modelValue', val)
  emit('change', val)
}
</script>

<style scoped>
.cat-leaf-name { font-size: 13px; }
.cat-leaf-path { float: right; font-size: 12px; color: #999; margin-left: 12px; }
</style>
