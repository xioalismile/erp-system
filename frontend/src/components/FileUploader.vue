<template>
  <!--
    通用附件上传组件 - 支持图片和任意文件
    用法:
      <FileUploader v-model="form.attachmentUrls" :type="'sale-order'" :disabled="false" />
      v-model 绑定逗号分隔的URL字符串
    展示: 图片显示缩略图卡片(与物料档案产品图片同风格), 其他文件显示彩色类型图标
  -->
  <div class="file-uploader">
    <!-- 上传按钮 (关闭默认文本列表, 使用下方自定义附件卡片) -->
    <el-upload
      :action="uploadAction"
      :data="{ type: type }"
      :headers="uploadHeaders"
      :show-file-list="false"
      :before-upload="beforeUpload"
      :on-success="onSuccess"
      :on-error="onError"
      :disabled="disabled"
      multiple
    >
      <el-button :icon="Upload" :disabled="disabled" size="small" type="primary" plain v-permission="perm('upload')">
        {{ buttonText }}
      </el-button>
      <template #tip>
        <div class="upload-tip">{{ tipText }}</div>
      </template>
    </el-upload>

    <!-- 附件卡片列表 -->
    <div v-if="items.length" class="attach-grid">
      <div v-for="item in items" :key="item.url" class="attach-card">
        <div class="attach-preview" @click="onCardClick(item)">
          <!-- 图片: 缩略图 -->
          <img v-if="item.isImage" :src="item.url" class="thumb-img" :alt="item.name" />
          <!-- 非图片: 文件类型彩色图标 -->
          <div v-else class="file-icon" :style="{ background: item.color }">{{ item.badge }}</div>
          <!-- 删除按钮 (草稿态显示) -->
          <div v-if="!disabled" class="attach-remove" :title="$tt('删除附件')" @click.stop="removeFile(item)">
            <el-icon><Close /></el-icon>
          </div>
        </div>
        <!-- 原始文件名 -->
        <div class="attach-name" :title="item.name">{{ item.name }}</div>
      </div>
    </div>

    <!-- 图片全屏预览 -->
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="imageUrls"
      :initial-index="viewerIndex"
      @close="viewerVisible = false"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Close } from '@element-plus/icons-vue'
import { parseUrls, joinUrls } from '@/api/common/upload'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()


const props = defineProps({
  // v-model 绑定值 (逗号分隔URL字符串)
  modelValue: { type: String, default: '' },
  // 业务类型(子目录)
  type: { type: String, default: 'common' },
  disabled: { type: Boolean, default: false },
  // 上传按钮文字
  buttonText: { type: String, default: '上传附件' },
  // 提示文字
  tipText: { type: String, default: '支持图片、PDF、Word、Excel等文件, 单文件不超过50MB' },
  maxSize: { type: Number, default: 50 } // MB
})

const emit = defineEmits(['update:modelValue'])

const uploadAction = '/api/files/upload'
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}))

// URL 列表
const urlList = computed(() => parseUrls(props.modelValue))

// 图片扩展名 (与后端白名单对齐)
const IMAGE_RE = /\.(jpg|jpeg|png|gif|bmp|webp|svg)$/i

// 非图片文件类型 → 彩色徽标 (Excel绿/PDF红/Word蓝/PPT橙/压缩包紫/其他灰)
const FILE_TYPES = {
  xlsx: { badge: 'XLS', color: '#67C23A' },
  xls: { badge: 'XLS', color: '#67C23A' },
  csv: { badge: 'CSV', color: '#67C23A' },
  pdf: { badge: 'PDF', color: '#F56C6C' },
  doc: { badge: 'DOC', color: '#409EFF' },
  docx: { badge: 'DOC', color: '#409EFF' },
  ppt: { badge: 'PPT', color: '#E6A23C' },
  pptx: { badge: 'PPT', color: '#E6A23C' },
  zip: { badge: 'ZIP', color: '#8D71E0' },
  rar: { badge: 'RAR', color: '#8D71E0' },
  '7z': { badge: '7Z', color: '#8D71E0' },
  txt: { badge: 'TXT', color: '#909399' }
}

// 从URL提取显示文件名 (还原百分号编码的中文原名)
function fileNameOf(url) {
  const raw = url.substring(url.lastIndexOf('/') + 1)
  try {
    return decodeURIComponent(raw)
  } catch (_) {
    return raw
  }
}

// 附件卡片列表
const items = computed(() => urlList.value.map(url => {
  const name = fileNameOf(url)
  const isImage = IMAGE_RE.test(url)
  const ext = name.includes('.') ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : ''
  const meta = FILE_TYPES[ext] || {
    badge: (ext || 'FILE').substring(0, 4).toUpperCase(),
    color: '#909399'
  }
  return { url, name, isImage, badge: meta.badge, color: meta.color }
}))

// 图片URL列表 (用于全屏预览)
const imageUrls = computed(() => items.value.filter(i => i.isImage).map(i => i.url))

// 上传前校验
function beforeUpload(file) {
  const isLtSize = file.size / 1024 / 1024 < props.maxSize
  if (!isLtSize) {
    ElMessage.error(`${tt("文件大小不能超过 ")}${props.maxSize}MB`)
    return false
  }
  return true
}

// 上传成功
function onSuccess(res, file) {
  const newUrl = extractUrl(res)
  if (!newUrl) {
    ElMessage.error(tt('上传失败: 返回数据格式无效'))
    console.error('上传返回数据格式无效:', res)
    return
  }
  const list = [...urlList.value, newUrl]
  emit('update:modelValue', joinUrls(list))
  ElMessage.success(`${file.name}${tt(" 上传成功")}`)
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
  if (typeof res === 'string') return isValidUrl(res) ? res : ''
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
 * 校验是否为有效文件URL (必须以 /uploads/ 或 http(s):// 开头)
 */
function isValidUrl(url) {
  if (!url || typeof url !== 'string') return false
  const u = url.trim()
  return u.startsWith('/uploads/') || u.startsWith('http://') || u.startsWith('https://')
}

/** 上传请求本身失败（网络断开/401/500等）时提示用户，避免"看似上传成功实则失败" */
function onError(err) {
  console.error('附件上传失败:', err)
  let reason = '网络或权限错误，请检查登录状态后重试'
  try {
    const body = typeof err === 'string' ? JSON.parse(err) : (err && err.message ? { message: err.message } : null)
    if (body && body.message) reason = body.message
  } catch (_) {}
  ElMessage.error(tt('附件上传失败：') + reason)
}

// 删除附件
function removeFile(item) {
  const list = urlList.value.filter(u => u !== item.url)
  emit('update:modelValue', joinUrls(list))
}

// 卡片点击: 图片全屏预览, 其他文件下载
const viewerVisible = ref(false)
const viewerIndex = ref(0)
function onCardClick(item) {
  if (item.isImage) {
    const idx = imageUrls.value.indexOf(item.url)
    viewerIndex.value = idx >= 0 ? idx : 0
    viewerVisible.value = true
  } else {
    downloadFile(item)
  }
}

// 下载文件 (download属性还原原始文件名)
function downloadFile(item) {
  const link = document.createElement('a')
  link.href = item.url
  link.download = item.name || ''
  link.target = '_blank'
  link.click()
}
</script>

<style scoped lang="scss">
.file-uploader { width: 100%; }
.upload-tip {
  font-size: 12px; color: #909399; margin-top: 4px;
}
.attach-grid {
  display: flex; flex-wrap: wrap; gap: 12px; margin-top: 12px;
}
.attach-card { width: 96px; }
.attach-preview {
  position: relative; width: 96px; height: 96px;
  border: 1px solid #c0ccda; border-radius: 6px; background: #fbfdff;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; overflow: hidden; transition: border-color .2s;
  &:hover { border-color: #409eff; }
}
.thumb-img {
  width: 100%; height: 100%; object-fit: cover; display: block;
}
.file-icon {
  min-width: 48px; height: 48px; padding: 0 6px; border-radius: 8px;
  color: #fff; font-size: 12px; font-weight: 700; letter-spacing: .5px;
  display: flex; align-items: center; justify-content: center;
  box-sizing: border-box;
}
.attach-remove {
  position: absolute; top: 3px; right: 3px; width: 20px; height: 20px;
  border-radius: 50%; background: rgba(0, 0, 0, .45); color: #fff;
  display: none; align-items: center; justify-content: center;
  cursor: pointer; font-size: 12px;
  &:hover { background: #f56c6c; }
}
.attach-preview:hover .attach-remove { display: flex; }
.attach-name {
  margin-top: 4px; font-size: 11px; color: #606266;
  text-align: center; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
</style>
