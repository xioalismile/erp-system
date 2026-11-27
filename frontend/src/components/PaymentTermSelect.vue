<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    filterable
    clearable
    default-first-option
    allow-create
    :disabled="disabled"
    :loading="loading"
    style="width:100%"
    @change="onChange"
    @visible-change="onVisibleChange"
  >
    <el-option
      v-for="o in options"
      :key="o.value"
      :label="o.label"
      :value="o.value"
    >
      <span class="pt-name">{{ o.label }}</span>
      <span v-if="o.days !== null && o.days !== undefined" class="pt-days">{{ $tt('账期') }} {{ o.days }} {{ $tt('天') }}</span>
      <el-icon
        v-permission="perm('delete')"
        class="pt-del"
        :title="$tt('停用（历史单据不受影响）')"
        @click.stop="askDisable(o)"
      ><Close /></el-icon>
    </el-option>
    <el-option
      v-if="!options.length"
      value="__pt_empty__"
      :label="$tt('暂无付款条件，输入名称后回车即可新增')"
      disabled
    />
  </el-select>

  <!-- 新建：只填「名称 + 账期天数」（编码由后端自动生成） -->
  <el-dialog v-model="createVisible" :title="$tt('新增付款条件')" width="420px" append-to-body>
    <el-form label-width="96px">
      <el-form-item :label="$tt('名称')" required>
        <el-input
          v-model="createForm.name"
          maxlength="50"
          :placeholder="$tt('如：月结45天')"
          @keyup.enter="doCreate"
        />
      </el-form-item>
      <el-form-item :label="$tt('账期天数')" required>
        <el-input-number
          v-model="createForm.paymentDays"
          :min="0"
          :max="999"
          controls-position="right"
          style="width:100%"
        />
      </el-form-item>
      <div class="pt-tip">{{ $tt('账期天数用于计算应收/应付到期日；创建后可在下拉里停用，历史单据不受影响。') }}</div>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">{{ $tt('取消') }}</el-button>
      <el-button type="primary" :loading="creating" @click="doCreate">{{ $tt('保存') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 付款条件下拉（全站唯一实现：单据编辑页 + 客户/供应商档案页共用同一套逻辑与数据）
 *
 * 交互（2026-09-28 用户确认）：
 *  - 输入新名称 → 回车 → 弹「名称 + 账期天数」→ 保存落库 → 下拉立即出现并选中；
 *  - 下拉项右侧「停用」= 删除（软删）：停用后不再出现在下拉，**历史单据/档案仍显示原值**；
 *  - 选项数据与新增/停用逻辑一律走 composables/usePaymentTermOptions（禁止各页自己调接口）。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import { usePaymentTermOptions } from '@/composables/usePaymentTermOptions'
import { usePagePerm } from '@/utils/pagePerm'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '请选择付款条件（输入新名称后回车即可新增）' }
})
const emit = defineEmits(['update:modelValue', 'change'])

const perm = usePagePerm('basedata:payment-term')
const { loading, loadTerms, activeOptions, termById, createTerm, disableTerm, isActiveTerm } = usePaymentTermOptions()

const createVisible = ref(false)
const creating = ref(false)
const createForm = ref({ name: '', paymentDays: 0 })

/** 下拉选项 = 未停用项 + 当前已选中的历史项（已停用也要能显示出来，避免老单据变空白） */
const options = computed(() => {
  const list = [...activeOptions.value]
  const cur = termById(props.modelValue)
  if (cur && !isActiveTerm(cur) && !list.some(o => String(o.value) === String(cur.paymentTermId))) {
    list.push({
      value: cur.paymentTermId,
      label: (cur.paymentTermName || '') + '（' + '已停用' + '）',
      days: cur.paymentDays,
      raw: cur
    })
  }
  return list
})

onMounted(() => { loadTerms() })

function onVisibleChange () { /* 每次展开都读缓存即可，不需要重复请求 */ }

function onChange (v) {
  // allow-create：输入的新名称会以字符串形式回传 → 走新建流程，不直接选中
  if (typeof v === 'string' && v !== '__pt_empty__') {
    createForm.value = { name: v.trim(), paymentDays: 0 }
    createVisible.value = true
    return
  }
  if (v === null || v === undefined || v === '') {
    emit('update:modelValue', null)
    emit('change', null)
    return
  }
  const term = termById(v)
  emit('update:modelValue', v)
  emit('change', term)
}

async function doCreate () {
  const name = String(createForm.value.name || '').trim()
  if (!name) { ElMessage.warning('请填写付款条件名称'); return }
  creating.value = true
  try {
    const created = await createTerm({ name, paymentDays: createForm.value.paymentDays })
    if (!created) { ElMessage.warning('已存在同名付款条件，请直接在下拉中选择'); createVisible.value = false; return }
    emit('update:modelValue', created.paymentTermId)
    emit('change', created)
    ElMessage.success(created.__reused ? '已存在同名同账期的付款条件，已直接选用' : '付款条件已新增')
    createVisible.value = false
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '新增失败')
  } finally {
    creating.value = false
  }
}

async function askDisable (o) {
  try {
    await ElMessageBox.confirm(
      `停用后「${o.label}」不再出现在下拉里，但历史单据与档案仍显示原值。是否停用？`,
      '停用付款条件',
      { type: 'warning', confirmButtonText: '停用', cancelButtonText: '取消' }
    )
  } catch (e) { return }
  try {
    await disableTerm(o.value)
    if (String(props.modelValue) === String(o.value)) {
      emit('update:modelValue', null)
      emit('change', null)
    }
    ElMessage.success('已停用')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '停用失败')
  }
}
</script>

<style scoped>
.pt-name { font-size: 13px; }
.pt-days { float: right; font-size: 12px; color: #909399; margin-left: 12px; }
.pt-del { float: right; margin-left: 10px; color: #c0c4cc; cursor: pointer; }
.pt-del:hover { color: #f56c6c; }
.pt-tip { font-size: 12px; color: #909399; line-height: 18px; padding-left: 96px; }
</style>
