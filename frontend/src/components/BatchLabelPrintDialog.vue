<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="1000px"
    append-to-body
    destroy-on-close
    class="label-print-dialog"
  >
    <div class="label-print-body">
      <!-- 左侧参数区 -->
      <div class="label-print-side">
        <el-form label-width="72px" size="default" @submit.prevent>
          <el-form-item :label="$tt('纸张')">
            <el-select v-model="paper" style="width: 100%">
              <el-option :label="$tt('A4 连排（每页 3×8）')" value="A4-3x8" />
              <el-option :label="$tt('标签纸 60×40mm')" value="60x40" />
              <el-option :label="$tt('标签纸 50×30mm')" value="50x30" />
            </el-select>
          </el-form-item>

          <el-form-item :label="$tt('份数')">
            <el-input-number v-model="copies" :min="1" :max="999" style="width: 100%" />
          </el-form-item>

          <el-form-item :label="$tt('一维条码')">
            <el-switch v-model="barcode" :active-text="$tt('条码+二维码')" :inactive-text="$tt('仅二维码')" />
          </el-form-item>

          <el-form-item :label="$tt('标签字段')">
            <el-checkbox-group v-model="fields" class="label-print-fields">
              <el-checkbox v-for="f in fieldOptions" :key="f.value" :label="f.value">{{ $tt(f.label) }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item>
            <span class="label-print-count">
              {{ $tt('已选') }} {{ itemCount }} {{ $tt('项 ×') }} {{ copies }} {{ $tt('份 =') }} {{ totalLabels }} {{ $tt('张标签') }}
            </span>
          </el-form-item>
        </el-form>
      </div>

      <!-- 右侧实时预览：打印框必须常驻（加载时用 v-if 移除会导致写入时取不到引用 → 预览永远空白） -->
      <div class="label-print-preview">
        <div v-show="loading" class="label-print-loading">{{ $tt('正在生成标签...') }}</div>
        <iframe ref="iframeRef" class="label-iframe" frameborder="0" />
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ $tt('取消') }}</el-button>
      <el-button type="primary" :loading="loading" @click="doPrint">
        <el-icon><Printer /></el-icon>
        {{ $tt('打印') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { Printer } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { printApi } from '@/api/system/print'
import { tt } from '@/i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** MATERIAL=商品标签 / WORK_OPERATION=工序卡 */
  type: { type: String, default: 'MATERIAL' },
  /** 勾选的主键列表 */
  ids: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const loading = ref(false)
const iframeRef = ref(null)

// 参数（默认按"多比少好"：A4 连排、条码双打、编号+规格）
const paper = ref('A4-3x8')
const copies = ref(1)
const barcode = ref(true)
const fields = ref(['code', 'spec'])

const fieldOptions = [
  { value: 'code', label: '编号' },
  { value: 'spec', label: '规格' },
  { value: 'unit', label: '单位' },
  { value: 'fullName', label: '全称' }
]

const itemCount = computed(() => (props.ids ? props.ids.length : 0))
const totalLabels = computed(() => itemCount.value * (copies.value || 1))
const dialogTitle = computed(() => (props.type === 'WORK_OPERATION' ? tt('批量打印工序卡') : tt('批量打印商品标签')))

watch(() => props.modelValue, async (val) => {
  visible.value = val
  if (val) {
    await loadHtml()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 参数变化即刷新预览
watch([paper, copies, barcode, fields], () => {
  if (visible.value) loadHtml()
}, { deep: true })

async function loadHtml() {
  if (!props.ids || props.ids.length === 0) {
    ElMessage.warning(tt('请先勾选要打印的数据'))
    return
  }
  loading.value = true
  try {
    const html = await printApi.printLabels({
      t: props.type,
      ids: props.ids.join(','),
      copies: copies.value,
      paper: paper.value,
      barcode: barcode.value ? 1 : 0,
      fields: fields.value.join(',')
    })
    await nextTick()
    await writeHtmlToIframe(html)
  } catch (e) {
    ElMessage.error(tt('加载标签内容失败'))
  } finally {
    loading.value = false
  }
}

async function writeHtmlToIframe(html) {
  let iframe = iframeRef.value
  if (!iframe) {
    // 弹窗每次关闭都重建内容，挂载可能晚一帧，再等一次
    await nextTick()
    iframe = iframeRef.value
  }
  if (!iframe) return
  const doc = iframe.contentDocument || iframe.contentWindow?.document
  if (!doc) return
  doc.open()
  doc.write(html || '')
  doc.close()
}

function doPrint() {
  const iframe = iframeRef.value
  if (!iframe || !iframe.contentWindow) {
    ElMessage.error(tt('请稍候，标签还没生成完成'))
    return
  }
  try {
    iframe.contentWindow.focus()
    iframe.contentWindow.print()
  } catch (e) {
    ElMessage.error(tt('打印失败, 请重试'))
  }
}
</script>

<style scoped lang="scss">
.label-print-body {
  display: flex;
  gap: 12px;
  height: 68vh;
}
.label-print-side {
  flex: 0 0 300px;
  border-right: 1px solid #e4e7ed;
  padding-right: 12px;
  overflow-y: auto;
}
.label-print-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 0 12px;
}
.label-print-count {
  font-size: 12px;
  color: #606266;
  background: #f2f6ff;
  border: 1px solid #d6e4ff;
  border-radius: 4px;
  padding: 4px 8px;
}
.label-print-preview {
  flex: 1;
  min-width: 0;
  position: relative;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
}
.label-iframe {
  width: 100%;
  height: 100%;
  display: block;
}
.label-print-loading {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 14px;
}
</style>
