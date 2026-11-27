<template>
  <!--
    可增删字典下拉组件 - 选项来自数据字典（按 typeCode），支持输入新值回车自动保存为字典选项
    用法: <DictSelect type-code="customer_type" v-model="form.customerType" />
  -->
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :loading="loading"
    filterable
    allow-create
    default-first-option
    clearable
    style="width:100%"
    @change="handleChange"
  >
    <el-option v-for="opt in options" :key="opt.id" :label="$tf(opt.label)" :value="opt.value">
      <span style="float:left;">{{ $tf(opt.label) }}</span>
      <el-icon class="dict-option-close" @click.stop="handleRemove(opt)"><Close /></el-icon>
    </el-option>
  </el-select>
</template>

<script setup>
import { Close } from '@element-plus/icons-vue'
import { useDict } from '@/composables/useDict'

const props = defineProps({
  typeCode: { type: String, required: true },
  modelValue: { type: [String, Number], default: '' },
  placeholder: { type: String, default: '请选择或输入后回车' }
})

const emit = defineEmits(['update:modelValue'])

const { options, loading, addOption, removeOption } = useDict(props.typeCode)

// 最近删除的选项值：再次输入时不再自动重建，避免误恢复
const recentlyRemoved = new Set()

function handleChange(val) {
  emit('update:modelValue', val ?? '')
  const text = String(val ?? '').trim()
  if (!text) return
  // 已有选项不处理
  if (options.value.some(o => o.value === text || o.label === text)) return
  // 刚删除的选项不自动重建
  if (recentlyRemoved.has(text)) {
    recentlyRemoved.delete(text)
    return
  }
  // 输入新值回车：静默持久化为字典选项
  addOption(text)
}

async function handleRemove(opt) {
  const ok = await removeOption(opt)
  if (ok) {
    recentlyRemoved.add(opt.label)
    if (opt.value !== opt.label) recentlyRemoved.add(opt.value)
    // 当前选中的就是被删选项时清空选中
    if (props.modelValue === opt.value || props.modelValue === opt.label) {
      emit('update:modelValue', '')
    }
  }
}
</script>

<style scoped>
.dict-option-close {
  float: right;
  margin-top: 8px;
  color: #c0c4cc;
  transition: color 0.2s;
}
.dict-option-close:hover {
  color: #f56c6c;
}
</style>
