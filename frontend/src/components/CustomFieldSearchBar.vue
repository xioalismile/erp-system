<template>
  <!--
    自定义字段「可搜索」搜索项（通用件）
    - 每个勾了「可搜索」的字段渲染成「字段名: [输入框]」，与各页既有 .erp-search-bar 同款样式
    - 本组件只渲染搜索项片段，不包外层容器：由调用方放进自己的 .erp-search-bar 里，
      自然获得 flex-wrap 自动换行（全局样式 .erp-search-bar 已带 flex-wrap: wrap）
    - 用法：<CustomFieldSearchBar :fields="cf.searchFields" :model="cf.values" @search="load" />
  -->
  <div v-for="f in fields" :key="f.fieldKey" class="erp-search-item">
    <span class="label">{{ $tf(f.fieldLabel) }}:</span>
    <el-input
      :model-value="model[f.fieldKey]"
      :placeholder="$tt(placeholder)"
      clearable
      :style="{ width }"
      @update:model-value="(v) => $emit('change', { fieldKey: f.fieldKey, value: v })"
      @keyup.enter="$emit('search')"
    />
  </div>
</template>

<script setup>
/**
 * 自定义字段搜索栏（通用件）
 *
 * 设计要点：
 *  1. 只渲染「可搜索字段」的输入项，不含查询/重置按钮 —— 按钮归调用方的搜索区所有
 *  2. 输入变化只 emit('change', { fieldKey, value })，由调用方写回自己的值载体
 *     （调用方一般直接绑 useCustomFieldSearch(...).setValue）——子组件不改父组件数据
 *  3. 无「可搜索」字段时渲染 0 个节点 → 调用方搜索区与改造前完全一致（零回归）
 */
defineProps({
  /** 字段定义列表（一般是 useCustomFieldSearch(...).searchFields） */
  fields: { type: Array, default: () => [] },
  /** 搜索值载体：{ fieldKey: 输入值 }（由调用方持有，本组件直接写入） */
  model: { type: Object, required: true },
  /** 输入框宽度 */
  width: { type: String, default: '180px' },
  /** 输入提示 */
  placeholder: { type: String, default: '请输入' }
})

defineEmits(['search', 'change'])
</script>
