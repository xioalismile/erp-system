<template>
  <div class="basic-info-wrap" v-loading="loading">
    <el-card shadow="never" class="info-card">
      <template #header>
        <div class="card-header">
          <span>{{ $tt("基础信息设置") }}</span>
          <el-button v-permission="perm('save')" type="primary" size="small" @click="onSave" :loading="saving">
            <el-icon style="margin-right:4px"><Check /></el-icon>保存设置
          </el-button>
        </div>
      </template>

      <el-form :model="form" label-width="140px" class="basic-form" label-position="right">
        <el-form-item :label="$tf('系统名称')" required>
          <el-input v-model="form['system.name']" :placeholder="$tt('请输入系统名称，如：XX贸易ERP')" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item :label="$tf('系统Logo')" required>
          <div class="logo-upload-row">
            <el-upload
              class="logo-uploader"
              :show-file-list="false"
              accept=".png,.jpg,.jpeg,.svg,.webp"
              :before-upload="beforeLogoUpload"
              :http-request="doUploadLogo"
            >
              <div v-if="logoPreviewUrl" class="logo-preview">
                <img :src="logoPreviewUrl" alt="logo" />
                <div class="logo-mask">
                  <el-icon><Edit /></el-icon>
                  <span>{{ $tt("更换") }}</span>
                </div>
              </div>
              <div v-else class="logo-empty">
                <el-icon :size="32"><Plus /></el-icon>
                <div class="upload-hint">{{ $tt("点击上传logo（≤2MB）") }}</div>
              </div>
            </el-upload>
            <div class="logo-tip">
              <div>{{ $tt("建议图片：正方形，透明底PNG效果最佳") }}</div>
              <el-button v-permission="perm('resetDefault')" v-if="logoPreviewUrl" type="danger" size="small" plain link @click="onClearLogo">
                <el-icon><Delete /></el-icon>移除Logo，恢复内置图标
              </el-button>
            </div>
          </div>
        </el-form-item>

        <el-form-item :label="$tf('登录页副标题')">
          <el-input v-model="form['system.subtitle']" :placeholder="$tt('标题下面显示的一句说明文字')" maxlength="100" show-word-limit />
        </el-form-item>

        <el-form-item :label="$tf('客服电话')">
          <el-input v-model="form['system.customer_service_phone']" :placeholder="$tt('设置中心页底部客服联系方式')" maxlength="50" />
        </el-form-item>

        <el-form-item :label="$tf('底部版权信息')">
          <el-input v-model="form['system.copyright']" :placeholder="$tt('例如：© 2026 XX公司 版权所有')" maxlength="100" show-word-limit />
        </el-form-item>

        <!-- 登录页改版（2026-09-13）：整张设计图当登录页背景（留空=默认登录页） -->
        <el-form-item :label="$tf('登录页背景图')">
          <el-input v-model="form['system.login_bg']" :placeholder="$tt('登录页背景图URL，如 /login-bg-rihui.jpg；留空=默认登录页')" maxlength="200" />
        </el-form-item>

        <el-form-item :label="$tf('登录页标题')">
          <el-input v-model="form['system.login_title']" :placeholder="$tt('登录表单标题，留空=使用系统名称')" maxlength="50" show-word-limit />
        </el-form-item>

        <!-- 登录页尺寸：改完保存 → 刷新登录页即生效，无需重新发版 -->
        <el-form-item :label="$tf('登录页标签字号')">
          <el-input v-model="form['system.login_label_size']" :placeholder="$tt('用户名/密码/手机登录 的字号，单位px，默认35')" maxlength="6" />
        </el-form-item>

        <el-form-item :label="$tf('登录页标题字号')">
          <el-input v-model="form['system.login_title_size']" :placeholder="$tt('登录页大标题字号，单位px，默认125')" maxlength="6" />
        </el-form-item>

        <el-form-item :label="$tf('登录页标题上移')">
          <el-input v-model="form['system.login_title_offset']" :placeholder="$tt('负值=继续上移；0=标题底边紧贴登录卡顶边，默认-100')" maxlength="6" />
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" show-icon class="tip-alert">
        <div>{{ $tt("保存后：侧边栏名称/logo、浏览器Tab标题、设置中心页底部客服电话 → F5刷新后立即生效。") }}</div>
        <div>{{ $tt("未登录的登录页：下次打开登录页时自动读取最新的名称/logo。") }}</div>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:basic-info')
import { ref, reactive, computed, onMounted, markRaw } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { uploadFile } from '@/api/common/upload'
import { batchUpdateSysConfig, refreshSysConfig } from '@/api/system/sysConfig'
import { useAppConfigStore, DEFAULTS } from '@/stores/appConfig'
import { tt } from '@/i18n'

defineProps({
  embedded: { type: Boolean, default: false }
})

const appConfig = useAppConfigStore()
const loading = ref(false)
const saving = ref(false)
const logoPreviewUrl = ref('')

const form = reactive({
  'system.name': '',
  'system.logo': '',
  'system.subtitle': '',
  'system.copyright': '',
  'system.customer_service_phone': '',
  'system.login_bg': '',
  'system.login_title': '',
  'system.login_label_size': '',
  'system.login_title_size': '',
  'system.login_title_offset': ''
})

function initFormFromAppConfig() {
  form['system.name'] = appConfig['system.name'] || DEFAULTS['system.name']
  form['system.logo'] = appConfig['system.logo'] || ''
  form['system.subtitle'] = appConfig['system.subtitle'] || DEFAULTS['system.subtitle']
  form['system.copyright'] = appConfig['system.copyright'] || DEFAULTS['system.copyright']
  form['system.customer_service_phone'] = appConfig['servicePhone'] || DEFAULTS['system.customer_service_phone']
  form['system.login_bg'] = appConfig['system.login_bg'] || ''
  form['system.login_title'] = appConfig['system.login_title'] || ''
  form['system.login_label_size'] = appConfig['system.login_label_size'] || ''
  form['system.login_title_size'] = appConfig['system.login_title_size'] || ''
  form['system.login_title_offset'] = appConfig['system.login_title_offset'] || ''
  logoPreviewUrl.value = resolveLogo(form['system.logo'])
}

function resolveLogo(v) {
  if (!v) return ''
  const t = String(v).trim()
  if (!t) return ''
  if (t === '/logo.png') return ''
  return t
}

onMounted(async () => {
  loading.value = true
  try {
    await appConfig.loadPublicConfig(true)
  } catch (_) {} finally {
    initFormFromAppConfig()
    loading.value = false
  }
})

function beforeLogoUpload(file) {
  const okType = /\.(png|jpe?g|svg|webp)$/i.test(file.name || '')
  if (!okType) {
    ElMessage.warning(tt('仅支持 png/jpg/jpeg/svg/webp 格式'))
    return false
  }
  const overSize = file.size > 2 * 1024 * 1024
  if (overSize) {
    ElMessage.warning(tt('图片大小不能超过 2MB'))
    return false
  }
  return true
}

async function doUploadUpload(options) {
  try {
    const url = await uploadFile(options.file, 'system-logo')
    form['system.logo'] = url || ''
    logoPreviewUrl.value = resolveLogo(url)
    ElMessage.success(tt('Logo 上传成功，记得点【保存设置】写入系统'))
    if (options && options.onSuccess) options.onSuccess()
  } catch (e) {
    ElMessage.error(tt('上传失败：') + (e?.message || tt('未知错误')))
    if (options && options.onError) options.onError(e)
  }
}
// 兼容 el-upload :http-request 的函数名差异（内部也兼容别名）
const doUploadLogo = doUploadUpload

function onClearLogo() {
  ElMessageBox.confirm(tt('确定移除自定义Logo？系统将自动使用内置图标显示。'), tt('提示'), {
    type: 'warning', confirmButtonText: tt('确定移除'), cancelButtonText: tt('取消')
  }).then(() => {
    form['system.logo'] = ''
    logoPreviewUrl.value = ''
    ElMessage.success(tt('已标记移除，记得点【保存设置】写入系统'))
  }).catch(() => {})
}

function trimForm() {
  for (const k of Object.keys(form)) {
    if (typeof form[k] === 'string') form[k] = form[k].trim()
  }
}

async function onSave() {
  trimForm()
  if (!form['system.name']) {
    ElMessage.warning(tt('请填写系统名称'))
    return
  }
  saving.value = true
  try {
    const list = [
      { configKey: 'system.name', configValue: form['system.name'] },
      { configKey: 'system.logo', configValue: form['system.logo'] || '' },
      { configKey: 'system.subtitle', configValue: form['system.subtitle'] || '' },
      { configKey: 'system.copyright', configValue: form['system.copyright'] || '' },
      { configKey: 'system.customer_service_phone', configValue: form['system.customer_service_phone'] || '' },
      { configKey: 'system.login_bg', configValue: form['system.login_bg'] || '' },
      { configKey: 'system.login_title', configValue: form['system.login_title'] || '' },
      { configKey: 'system.login_label_size', configValue: form['system.login_label_size'] || '' },
      { configKey: 'system.login_title_size', configValue: form['system.login_title_size'] || '' },
      { configKey: 'system.login_title_offset', configValue: form['system.login_title_offset'] || '' }
    ]
    await batchUpdateSysConfig(list)
    // 写入本地 store + 缓存
    appConfig.applyPatches({
      'system.name': form['system.name'],
      'system.logo': form['system.logo'],
      'system.subtitle': form['system.subtitle'],
      'system.copyright': form['system.copyright'],
      'system.customer_service_phone': form['system.customer_service_phone'],
      'system.login_bg': form['system.login_bg'],
      'system.login_title': form['system.login_title'],
      'system.login_label_size': form['system.login_label_size'],
      'system.login_title_size': form['system.login_title_size'],
      'system.login_title_offset': form['system.login_title_offset']
    })
    // 刷新后端缓存
    try { await refreshSysConfig() } catch (_) {}
    // 再从接口拉一次确认
    try { await appConfig.refreshConfig() } catch (_) {}

    ElMessage.success(tt('保存成功！刷新浏览器后登录页与侧边栏都会显示新名称与Logo'))
  } catch (e) {
    ElMessage.error(tt('保存失败：') + (e?.message || tt('未知错误')))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.basic-info-wrap { padding: 20px; }
.info-card { max-width: 860px; }
.card-header {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 15px; font-weight: 600; color: #303133;
}
.basic-form { padding: 4px 12px 12px 4px; }

.logo-upload-row {
  display: flex; align-items: flex-start; gap: 20px;
}
.logo-uploader {
  width: 120px; height: 120px; flex-shrink: 0;
  border: 1px dashed #dcdfe6; border-radius: 8px;
  overflow: hidden; background: #fafafa;
}
.logo-preview {
  width: 100%; height: 100%; position: relative; cursor: pointer;
  img { width: 100%; height: 100%; object-fit: contain; background:#fff; display:block; padding:8px; box-sizing:border-box; }
  .logo-mask {
    position: absolute; left:0; right:0; bottom:0; top:0;
    background: rgba(0,0,0,0.5);
    color: #fff; font-size: 13px;
    display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px;
    opacity: 0; transition: opacity .2s;
  }
  &:hover .logo-mask { opacity: 1; }
}
.logo-empty {
  width:100%; height:100%;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  color: #8c939d; cursor: pointer; gap: 4px;
  &:hover { color: #409EFF; background: #ecf5ff; }
  .upload-hint { font-size: 12px; text-align: center; line-height: 1.4; padding: 0 8px; }
}
.logo-tip {
  font-size: 12px; color: #909399;
  display: flex; flex-direction: column; gap: 8px; padding-top: 4px;
  line-height: 1.6;
}
.tip-alert { margin-top: 8px; }
</style>
