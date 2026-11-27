<template>
  <el-dialog
    v-model="visible"
    :title="$tf('打印预览')"
    width="900px"
    top="5vh"
    :close-on-click-modal="false"
    destroy-on-close
    @close="onClose"
  >
    <template #header>
      <div class="dialog-header">
        <span class="title">{{ $tt("打印预览") }}</span>
        <div class="header-actions">
          <el-select
            v-if="templates.length > 1"
            v-model="selectedTemplateId"
            :placeholder="$tt('选择模板')"
            style="width: 240px"
            @change="onTemplateChange"
          >
            <el-option
              v-for="t in templates"
              :key="t.id"
              :label="t.templateName"
              :value="t.id"
            />
          </el-select>
          <el-button type="primary" :icon="Printer" v-permission="perm('print')" :loading="loading" @click="doPrint">
            打印
          </el-button>
        </div>
      </div>
    </template>

    <div v-loading="loading" class="preview-container">
      <iframe
        ref="iframeRef"
        class="preview-iframe"
        frameborder="0"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { Printer } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { printApi, printTemplateApi } from '@/api/system/print'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('report:custom')

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  billType: { type: String, default: '' },
  billId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const loading = ref(false)
const iframeRef = ref(null)
const htmlContent = ref('')
const templates = ref([])
const selectedTemplateId = ref(null)

// 监听弹窗显隐
watch(() => props.modelValue, async (val) => {
  visible.value = val
  if (val && props.billType && props.billId) {
    await loadTemplates()
    await loadPrintHtml()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 加载该单据类型的可用模板列表
async function loadTemplates() {
  try {
    templates.value = await printTemplateApi.listByDocType(props.billType) || []
    // 默认选中 is_default=1 的模板
    const def = templates.value.find(t => t.isDefault === 1)
    selectedTemplateId.value = def ? def.id : (templates.value[0]?.id || null)
  } catch {
    templates.value = []
  }
}

// 加载打印HTML
async function loadPrintHtml() {
  if (!props.billType || !props.billId) return
  loading.value = true
  try {
    let html
    if (selectedTemplateId.value) {
      html = await printApi.printByTemplate(props.billType, props.billId, selectedTemplateId.value)
    } else {
      html = await printApi.printByBill(props.billType, props.billId)
    }
    htmlContent.value = html || ''
    // 写入iframe
    await nextTick()
    writeHtmlToIframe(htmlContent.value)
  } catch (e) {
    ElMessage.error(tt('加载打印内容失败'))
  } finally {
    loading.value = false
  }
}

// 切换模板时重新加载
async function onTemplateChange() {
  await loadPrintHtml()
}

// 将HTML写入iframe
function writeHtmlToIframe(html) {
  const iframe = iframeRef.value
  if (!iframe) return
  const doc = iframe.contentDocument || iframe.contentWindow?.document
  if (!doc) return
  doc.open()
  doc.write(html)
  doc.close()
}

// 执行打印
function doPrint() {
  const iframe = iframeRef.value
  if (!iframe) return
  try {
    iframe.contentWindow.focus()
    iframe.contentWindow.print()
  } catch (e) {
    ElMessage.error(tt('打印失败, 请重试'))
  }
}

function onClose() {
  htmlContent.value = ''
  templates.value = []
  selectedTemplateId.value = null
}
</script>

<style scoped lang="scss">
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  .title { font-size: 16px; font-weight: 600; }
  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-right: 20px;
  }
}
.preview-container {
  width: 100%;
  height: 70vh;
  overflow: hidden;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}
.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
