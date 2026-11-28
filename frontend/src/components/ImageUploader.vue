<template>
  <!--
    通用图片上传组件 - 支持多图上传 + 主图设置
    用法:
      <ImageUploader v-model="form.imageUrls" v-model:main-image="form.mainImage" :type="'material'" :disabled="false" />
      v-model 绑定逗号分隔的URL字符串, v-model:main-image 绑定主图URL（Vue 3 不支持 :main-image.sync，写了会静默失效）
  -->
  <div class="image-uploader">
    <el-upload
      :action="uploadAction"
      :data="{ type: type }"
      :headers="uploadHeaders"
      :file-list="fileList"
      list-type="picture-card"
      :before-upload="beforeUpload"
      :on-success="onSuccess"
      :on-remove="onRemove"
      :on-preview="onPreview"
      :on-error="onError"
      :disabled="disabled"
      accept="image/*"
      multiple
    >
      <el-icon><Plus /></el-icon>
    </el-upload>

    <!-- 主图设置 -->
    <div v-if="urlList.length > 0 && !disabled" class="main-image-tip">
      <span>{{ $tt("提示: 点击图片右上角删除按钮可移除,") }} </span>
      <el-select v-model="mainImageRef" :placeholder="$tt('选择主图')" size="small" style="width:280px" @change="onMainChange">
        <el-option v-for="url in urlList" :key="url" :label="url" :value="url">
          <span style="display:flex;align-items:center;">
            <img :src="url" style="width:24px;height:24px;margin-right:6px;" />
            {{ url.substring(url.lastIndexOf('/') + 1) }}
          </span>
        </el-option>
      </el-select>
    </div>

    <!-- 主图预览 -->
    <div v-if="mainImageRef" class="main-preview">
      <span class="label">{{ $tt("主图:") }}</span>
      <img :src="mainImageRef" class="main-img" @click="previewMain" />
    </div>

    <!-- 图片预览 -->
    <el-image-viewer v-if="viewerVisible" :url-list="urlList" :initial-index="viewerIndex" @close="viewerVisible = false" />
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { parseUrls, joinUrls } from '@/api/common/upload'
import { tt } from '@/i18n'

const props = defineProps({
  modelValue: { type: String, default: '' },
  mainImage: { type: String, default: '' },
  type: { type: String, default: 'common' },
  disabled: { type: Boolean, default: false },
  maxSize: { type: Number, default: 5 }
})

const emit = defineEmits(['update:modelValue', 'update:mainImage'])

const uploadAction = '/api/files/upload'
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}))

const urlList = computed(() => parseUrls(props.modelValue))

const fileList = computed(() => urlList.value.map((url, idx) => ({
  name: url.substring(url.lastIndexOf('/') + 1),
  url,
  uid: idx + 1
})))

const mainImageRef = ref(props.mainImage)
watch(() => props.mainImage, (v) => { mainImageRef.value = v })

function onMainChange(val) {
  mainImageRef.value = val
  emit('update:mainImage', val)
}

function beforeUpload(file) {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error(tt('只能上传图片文件'))
    return false
  }
  const isLtSize = file.size / 1024 / 1024 < props.maxSize
  if (!isLtSize) {
    ElMessage.error(`${tt("图片大小不能超过 ")}${props.maxSize}MB`)
    return false
  }
  return true
}

function onSuccess(res) {
  // el-upload 不走 axios 拦截器，res 是后端原始响应体 {code,success,data}
  // 需稳健提取URL字符串，避免把对象转成"[object Object]"
  const newUrl = extractUrl(res)
  if (!newUrl) {
    ElMessage.error(tt('上传失败: 返回数据格式无效'))
    console.error('上传返回数据格式无效:', res)
    return
  }
  const list = [...urlList.value, newUrl]
  emit('update:modelValue', joinUrls(list))
  if (!mainImageRef.value && list.length > 0) {
    mainImageRef.value = newUrl
    nextTick(() => emit('update:mainImage', newUrl))
  }
}

/**
 * 从后端响应中稳健提取文件URL字符串
 * 支持格式:
 *   "/uploads/xxx.png" (纯字符串)
 *   { data: "/uploads/xxx.png" }
 *   { data: { url: "/uploads/xxx.png" } }
 *   { url: "/uploads/xxx.png" }
 */
function extractUrl(res) {
  if (!res) return ''
  // 纯字符串
  if (typeof res === 'string') return isValidUrl(res) ? res : ''
  // 对象: 依次尝试 res.data / res.url
  let candidate = ''
  if (res.data != null) {
    if (typeof res.data === 'string') candidate = res.data
    else if (typeof res.data === 'object') candidate = res.data.url || res.data.path || ''
  }
  if (!candidate && typeof res.url === 'string') candidate = res.url
  if (!candidate && typeof res.path === 'string') candidate = res.path
  return isValidUrl(candidate) ? candidate : ''
}

/**
 * 校验是否为有效图片URL
 * 必须以 /uploads/ 或 http(s):// 或 data:image 开头
 */
function isValidUrl(url) {
  if (!url || typeof url !== 'string') return false
  const u = url.trim()
  return u.startsWith('/uploads/') || u.startsWith('http://') || u.startsWith('https://') || u.startsWith('data:image/')
}

/** 上传请求本身失败（网络断开/401/500等）时提示用户，避免"看似上传成功实则失败" */
function onError(err) {
  console.error('图片上传失败:', err)
  let reason = '网络或权限错误，请检查登录状态后重试'
  try {
    const body = typeof err === 'string' ? JSON.parse(err) : (err && err.message ? { message: err.message } : null)
    if (body && body.message) reason = body.message
  } catch (_) {}
  ElMessage.error(tt('图片上传失败：') + reason)
}

function onRemove(file) {
  const targetUrl = file.url
  const list = urlList.value.filter(u => u !== targetUrl)
  emit('update:modelValue', joinUrls(list))
  if (mainImageRef.value === targetUrl) {
    const newMain = list[0] || ''
    mainImageRef.value = newMain
    nextTick(() => emit('update:mainImage', newMain))
  }
}

const viewerVisible = ref(false)
const viewerIndex = ref(0)
function onPreview(file) {
  const idx = urlList.value.indexOf(file.url)
  viewerIndex.value = idx >= 0 ? idx : 0
  viewerVisible.value = true
}
function previewMain() {
  const idx = urlList.value.indexOf(mainImageRef.value)
  viewerIndex.value = idx >= 0 ? idx : 0
  viewerVisible.value = true
}

watch(() => props.modelValue, (val) => {
  if (!props.mainImage && val) {
    const list = parseUrls(val)
    if (list.length > 0) {
      mainImageRef.value = list[0]
      nextTick(() => emit('update:mainImage', list[0]))
    }
  }
}, { immediate: true })

watch(urlList, (list) => {
  if (list.length === 1 && !mainImageRef.value) {
    mainImageRef.value = list[0]
    nextTick(() => emit('update:mainImage', list[0]))
  }
}, { immediate: true })
</script>

<style scoped lang="scss">
.image-uploader { width: 100%; }
.main-image-tip {
  margin-top: 8px; font-size: 12px; color: #909399;
  display: flex; align-items: center; gap: 8px;
}
.main-preview {
  margin-top: 8px; display: flex; align-items: center; gap: 8px;
  .label { font-size: 13px; color: #606266; }
  .main-img {
    width: 80px; height: 80px; object-fit: cover; border-radius: 4px;
    border: 2px solid #409eff; cursor: pointer;
  }
}
</style>
