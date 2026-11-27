<template>
  <div class="base-edit-page">
    <!-- 顶部：返回 + 标题 + 操作按钮 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="goBack">
          <span class="back-arrow">←</span>
          <span class="back-text">{{ $tt('返回列表') }}</span>
        </div>
        <div class="title-group">
          <div class="page-title">{{ isEdit ? `${$tt('编辑')}${$tf(title)}` : `${$tt('新增')}${$tf(title)}` }}</div>
          <div class="page-sub">{{ $tt('填写') }}{{ $tf(title) }}{{ $tt('信息，带') }} <span class="req">*</span> {{ $tt('为必填项') }}</div>
        </div>
      </div>
      <div class="header-actions">
        <slot name="header-extras"></slot>
        <el-button v-if="showChangeRecord" v-permission="perm('view')" size="small" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <el-button v-permission="perm('dlgCancel')" @click="goBack">{{ $tt('取消') }}</el-button>
        <el-button v-permission="perm('saveDraft')" :loading="saving" @click="saveDraft">{{ $tt('保存为草稿') }}</el-button>
        <el-button type="primary" v-permission="perm('saveAndBack')" :loading="saving" @click="saveAndBack">{{ $tt('保存并返回') }}</el-button>
      </div>
    </div>

    <!-- 表单内容区：多个分区纵向排列 -->
    <div class="form-content">
      <div class="form-card" v-for="(section, sIdx) in allSections" :key="sIdx">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ $tf(section.title) }}</span>
          <span class="title-sub" v-if="section.subtitle">{{ $tf(section.subtitle) }}</span>
        </div>
        <el-form :model="form" label-position="right" label-width="128px" class="form-grid">
          <!-- v-for 放 template 上、v-if 留元素上：Vue3 中 v-if 优先级高于 v-for，同元素会导致 field 未定义 -->
          <template v-for="field in section.fields" :key="field.prop">
            <el-form-item
              :required="isFieldRequiredDisplay(field)"
              v-if="!isFieldHidden(field)"
            >
            <template #label>
              <span class="base-edit-label">
                <span>{{ $tf(field.label) }}</span>
                <el-tooltip
                  v-if="isStaticDefaultRequired(field)"
                  :content="$tt('系统默认必填：字段权限配置中强制锁定，管理员也不能取消必填')"
                  placement="top"
                >
                  <el-icon class="static-lock-icon"><Lock /></el-icon>
                </el-tooltip>
              </span>
            </template>
            <!-- 自定义组件（如付款条件：可选/可建/可停用，全站共用同一套实现） -->
            <component
              v-if="field.type === 'custom' && field.component"
              :is="field.component"
              v-model="form[field.prop]"
              :disabled="isControlDisabled(field)"
              v-bind="field.componentProps || {}"
              @change="onSelectChange(field, $event)"
            />
            <!-- 下拉选择 -->
            <el-select
              v-else-if="field.type === 'select'"
              v-model="form[field.prop]"
              :placeholder="field.placeholder ? $tt(field.placeholder) : $tt('请选择')"
              :filterable="field.filterable !== false"
              :disabled="isControlDisabled(field)"
              style="width:100%"
              @change="onSelectChange(field, $event)"
            >
              <el-option
                v-for="opt in optionsForField(field)"
                :key="opt.value"
                :label="$tf(opt.label)"
                :value="opt.value"
              />
            </el-select>
            <!-- 数字输入 -->
            <el-input-number
              v-else-if="field.type === 'number'"
              v-model="form[field.prop]"
              :precision="field.precision"
              :min="field.min !== undefined ? field.min : 0"
              :step="0.1"
              :disabled="isControlDisabled(field)"
              controls-position="right"
              style="width:100%"
            />
            <!-- 开关 -->
            <el-switch
              v-else-if="field.type === 'switch'"
              v-model="form[field.prop]"
              :active-value="field.activeValue !== undefined ? field.activeValue : 1"
              :inactive-value="field.inactiveValue !== undefined ? field.inactiveValue : 0"
              :disabled="isControlDisabled(field)"
            />
            <!-- 日期选择 -->
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="form[field.prop]"
              type="date"
              value-format="YYYY-MM-DD"
              :placeholder="field.placeholder ? $tt(field.placeholder) : $tt('选择日期')"
              :disabled="isControlDisabled(field)"
              style="width:100%"
            />
            <!-- 密码输入 -->
            <el-input
              v-else-if="field.type === 'password'"
              v-model="form[field.prop]"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="field.placeholder ? $tt(field.placeholder) : $tt('请输入')"
              :disabled="isControlDisabled(field)"
              style="width:100%"
            />
            <!-- 多行文本 -->
            <el-input
              v-else-if="field.type === 'textarea'"
              v-model="form[field.prop]"
              type="textarea"
              :rows="field.rows || 3"
              :placeholder="field.placeholder ? $tt(field.placeholder) : $tt('请输入')"
              :disabled="isControlDisabled(field)"
            />
            <!-- 级联选择器（树形选择） -->
            <el-cascader
              v-else-if="field.type === 'cascader'"
              v-model="form[field.prop]"
              :options="remoteOptionsMap[field.prop] || []"
              :props="field.cascaderProps || { checkStrictly: true, emitPath: false }"
              :placeholder="field.placeholder ? $tt(field.placeholder) : $tt('请选择')"
              :disabled="isControlDisabled(field)"
              filterable
              clearable
              style="width:100%"
              @change="onSelectChange(field, $event)"
            />
            <!-- 默认单行文本 -->
            <el-input
              v-else
              v-model="form[field.prop]"
              :placeholder="docFieldPlaceholder(field)"
              :disabled="isControlDisabled(field)"
            />
          </el-form-item>
          </template>
        </el-form>
      </div>
    </div>
    <ChangeRecordDialog
      v-if="showChangeRecord"
      v-model="changeRecordVisible"
      :bill-type="changeRecordBillType"
      :bill-no="changeRecordBillNo"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Memo, Lock } from '@element-plus/icons-vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import {
  loadCustomFieldDefinitions, loadCustomFieldValues,
  saveCustomFieldValues, computeFormulaFields
} from '@/composables/useCustomField'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useFieldPermission, clearFieldPermissionCache } from '@/composables/useFieldPermission'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { withFallbackOption } from '@/utils/labelFallback'

const perm = usePagePerm()


const emit = defineEmits(['select-change'])

const props = defineProps({
  // 标题（如"计量单位"）
  title: { type: String, default: '记录' },
  // 分区配置数组
  sections: { type: Array, default: () => [] },
  // 根据 id 加载数据的函数
  fetchData: { type: Function, default: null },
  // 保存函数 (data, isEdit) => Promise
  onSave: { type: Function, required: true },
  // 主键字段名
  idField: { type: String, default: 'id' },
  // 返回列表的路由路径
  listRoute: { type: String, default: '/' },
  // 远程选项加载器：{ fieldProp: () => Promise<{value,label}[]> }
  optionLoaders: { type: Object, default: () => ({}) },
  // 自定义字段实体类型（如 'customer'），传入则自动加载自定义字段
  customFieldEntity: { type: String, default: '' },
  // 操作日志相关
  showChangeRecord: { type: Boolean, default: false },
  changeRecordBillType: { type: String, default: '' },
  changeRecordBillField: { type: String, default: '' },
  // 自动编码：单据类型（对应 sys_serial_no_config.doc_type），传入则启用自动编码
  autoDocType: { type: String, default: '' },
  // 自动编码：编码字段名，如 'employeeCode' / 'deptCode' / 'stockCode'
  autoDocCodeField: { type: String, default: '' },
  // 外部传入的禁用字段Map：{ fieldProp: true }（兼容旧版直接在父组件用 useAutoDocCode 的写法）
  disabledFields: { type: Object, default: () => ({}) },
  // 新建模式初始化回调：(form, isEdit) => void | Promise<void>（兼容旧版写法）
  onCreateInit: { type: Function, default: null },
  // 字段权限 pageKey（与设置中心"页面"选项 key 一致，如 'customer'、'sale_order'）。
  // 传入则按当前登录用户角色动态控制字段 visible/editable/required；不传则完全回退到 field 对象静态值。
  pageKey: { type: String, default: '' }
})

const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode(props.autoDocType || '')

// 字段权限控制（visible / editable / required 动态叠加）
// 若无 pageKey，isFieldVisible / isFieldEditable / isFieldRequired 全返回 "无权限配置" 的结果
// （useFieldPermission 内部：无 perm 时 visible/editable 默认 true，required 默认 false → 等价于纯静态）
const {
  isFieldVisible: permFieldVisible,
  isFieldEditable: permFieldEditable,
  isFieldRequired: permFieldRequired,
  loadFieldPermission
} = useFieldPermission('')

// pageKey 变化时重新加载字段权限（若父组件切换 pageKey 场景）
watch(() => props.pageKey, (newKey, oldKey) => {
  if (newKey && newKey !== oldKey) {
    // 切换 pageKey 前先清旧缓存（跨页面/跨角色污染防御线），保证重新拉后端
    clearFieldPermissionCache(newKey)
    loadFieldPermission(newKey)
  } else if (newKey && oldKey === undefined && newKey) {
    // immediate 首次：如果之前已有同名脏缓存（例如字段权限页刚改完权限），先清再拉
    clearFieldPermissionCache(newKey)
    loadFieldPermission(newKey)
  }
}, { immediate: true })

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)

const changeRecordVisible = ref(false)
const changeRecordBillNo = computed(() => {
  if (!props.changeRecordBillField) return ''
  return form[props.changeRecordBillField] || ''
})

// 保存防重复提交锁（2026-09-02 修复：双击保存会导致第二次请求查重时
// 撞上第一次刚创建的数据，被误判"已被占用"）
const saving = ref(false)

// 自定义字段相关状态
const customFieldSections = ref([])
const customFieldMap = ref(new Map())
const customFormulaFields = ref([])
const customFieldValues = reactive({}) // 自定义字段值缓存

// 合并业务分区和自定义字段分区
const allSections = computed(() => [...props.sections, ...customFieldSections.value])

// 收集所有字段（业务字段 + 自定义字段）
const allFields = computed(() => allSections.value.flatMap(s => s.fields || []))
const totalFields = computed(() => allFields.value.length)
const requiredFields = computed(() => allFields.value.filter(f => isFieldRequiredDisplay(f)).length)

// 远程加载的下拉选项缓存
const remoteOptionsMap = reactive({})
async function loadRemoteOptions() {
  const loaders = props.optionLoaders || {}
  for (const prop in loaders) {
    try {
      const list = await loaders[prop]()
      remoteOptionsMap[prop] = list || []
    } catch (e) {
      console.warn(`加载字段 ${prop} 的下拉选项失败`, e)
      remoteOptionsMap[prop] = []
    }
  }
}

/**
 * 下拉候选项（带兜底）：当前值不在候选项里时，用表单里的名称字段临时补一个显示项，
 * 避免编辑页进入时下拉框直接显示数字主键（如客户/部门/仓库等）。
 */
function optionsForField(field) {
  const list = field.options || remoteOptionsMap[field.prop] || []
  return withFallbackOption(list, field.prop, field.nameProp, form)
}

// 下拉变更回调（用于联动回填名称字段）
function onSelectChange(field, value) {
  emit('select-change', { field, value, form })
}

// 判断字段是否隐藏（支持 hideOnCreate / hideOnEdit / visibleFn 条件 + 字段权限 visible）
function isFieldHidden(field) {
  if (!field) return false
  if (field.hideOnCreate && !isEdit.value) return true
  if (field.hideOnEdit && isEdit.value) return true
  if (typeof field.visibleFn === 'function') {
    try {
      if (!field.visibleFn({ form, isEdit: isEdit.value })) return true
    } catch (e) {
      console.warn('[BaseEdit] visibleFn 执行失败:', e)
    }
  }
  // 字段权限 visible：只有当 "有 pageKey 且权限明确=false" 时才隐藏（无配置=默认可见）
  const vis = props.pageKey ? permFieldVisible(field.prop) : true
  if (props.pageKey && !vis) return true
  return false
}

/**
 * 控件禁用状态合并：
 * 1. field.disabled 静态声明
 * 2. disabledFields[prop] 外部动态禁用（如 autoDocCode 手动传）
 * 3. 自动编码字段且已禁用（docCodeDisabled）
 * 4. 字段权限 editable=false（有 pageKey 时叠加）
 * 任一项满足即禁用；系统默认必填字段即使 editable=false（静态字段里声明disabled=true 除外），
 * 也由后端 savePermissions 兜底强制 editable=1，所以这里只按规则直译即可。
 */
function isControlDisabled(field) {
  if (!field) return true
  if (field.disabled) return true
  if (props.disabledFields && props.disabledFields[field.prop]) return true
  if (isDocCodeFieldDisabled(field)) return true
  if (props.pageKey && !permFieldEditable(field.prop)) return true
  return false
}

/**
 * 必填语义判定（不看"能不能填写"）：静态 required === true  OR  角色字段权限 required === true
 * 仅供"草稿占位值兜底"使用（草稿要保证 NOT NULL 列有值，即便该字段当前不可编辑）
 */
function isFieldRequiredRaw(field) {
  if (!field) return false
  const byStatic = !!field.required
  const byPerm = !!(props.pageKey && permFieldRequired(field.prop))
  return byStatic || byPerm
}

/**
 * 必填判定（唯一真相源）：label 星号、保存校验、requiredFields 计数 全部复用此函数
 * 规则（顺序即语义）：
 *  - 隐藏字段（hideOnCreate/hideOnEdit/visibleFn/权限不可见）：不打星、不校验
 *  - 填不了的字段（静态 disabled / 外部 disabledFields / 自动编号占位 / 角色字段权限不可编辑）：
 *    不打星、不校验 —— 否则会出现"打了星号却填不上"或"打了星号却不拦"的错位
 *  - 其余：静态必填 OR 角色字段权限必填
 * ⇒ 结论：界面上带 * 的字段 = 点"保存并返回"一定会被拦的字段
 */
function isFieldRequiredDisplay(field) {
  if (!field) return false
  if (isFieldHidden(field)) return false
  if (isControlDisabled(field)) return false
  return isFieldRequiredRaw(field)
}

/**
 * 静态系统默认必填判定（用户要求：这类字段在字段权限配置中被强制锁定为灰色🔒不可修改，在业务表单label旁也要显示🔒）
 *   判断依据：sections 数组中静态声明 field.required === true（不是权限叠加出来的required）
 *   与 isFieldRequiredDisplay 中的 byStatic 口径保持绝对一致
 */
function isStaticDefaultRequired(field) {
  return !!(field && field.required === true)
}

// 根据字段配置初始化表单
function getDefault(field) {
  if (field.default !== undefined) return field.default
  switch (field.type) {
    case 'switch':
      return field.inactiveValue !== undefined ? field.inactiveValue : 0
    case 'number':
      return 0
    default:
      return ''
  }
}

const form = reactive({})
// 初始化默认值
allFields.value.forEach(f => {
  form[f.prop] = getDefault(f)
})
form[props.idField] = null

function goBack() {
  router.push(props.listRoute)
}

// 重置表单到默认值（包括自定义字段）
function resetForm() {
  // 清理 form 上不在字段定义里的残留字段（如后端返回的额外字段）
  const validProps = new Set(allFields.value.map(f => f.prop))
  customFieldMap.value.forEach((_, key) => validProps.add(key))
  validProps.add(props.idField)
  Object.keys(form).forEach(k => {
    if (!validProps.has(k)) delete form[k]
  })
  allFields.value.forEach(f => {
    form[f.prop] = getDefault(f)
  })
  form[props.idField] = null
  // 重置自定义字段值
  Object.keys(customFieldValues).forEach(k => delete customFieldValues[k])
  customFieldMap.value.forEach((def, key) => {
    form[key] = getDefault(def)
    customFieldValues[key] = getDefault(def)
  })
}

// 加载自定义字段定义
async function loadCustomFields() {
  if (!props.customFieldEntity) return
  try {
    const { sections, fieldMap, formulaFields } = await loadCustomFieldDefinitions(props.customFieldEntity)
    customFieldSections.value = sections
    customFieldMap.value = fieldMap
    customFormulaFields.value = formulaFields
    // 初始化自定义字段默认值
    fieldMap.forEach((def, key) => {
      if (!(key in form)) {
        const dv = def.defaultValue || getDefault({ type: def.fieldType === 'number' ? 'number' : 'input', default: def.defaultValue })
        form[key] = dv
        customFieldValues[key] = dv
      }
    })
    // 加载完后立即触发一次公式计算
    if (formulaFields.length > 0) {
      recalcFormulaFields()
    }
  } catch (e) {
    console.warn('[BaseEdit] 加载自定义字段失败:', e)
  }
}

// 判断字段是否是编码字段且已禁用（自动编码场景）
function isDocCodeFieldDisabled(field) {
  if (!props.autoDocCodeField || !docCodeDisabled.value) return false
  return field.prop === props.autoDocCodeField
}

// 编码字段占位符：按自动编号配置动态显示（自动模式"保存后自动生成"，手动模式"请输入编号"）
function docFieldPlaceholder(field) {
  if (props.autoDocType && props.autoDocCodeField && field.prop === props.autoDocCodeField) {
    return docCodePlaceholder.value
  }
  return field.placeholder ? tt(field.placeholder) : tt('请输入')
}

// 监听路由路径变化：从编辑跳到新增时重置表单，从新增跳到编辑时加载数据
watch(() => route.fullPath, async () => {
  // 确保自定义字段已加载
  if (props.customFieldEntity && customFieldSections.value.length === 0) {
    await loadCustomFields()
  }
  // 每次路由变化时，根据是否有id决定行为
  if (route.params.id) {
    loadData()
  } else {
    // 新增模式：重置表单
    resetForm()
    // 自动编码：新建模式自动填充编码并禁用（新版prop方式）
    if (props.autoDocType && props.autoDocCodeField) {
      autoFillDocCodeOnCreate(form, props.autoDocCodeField)
    }
    // 调用外部传入的新建初始化回调（兼容旧版写法）
    if (props.onCreateInit) {
      try {
        await props.onCreateInit(form, false)
      } catch (e) {
        console.warn('[BaseEdit] onCreateInit 回调执行失败:', e)
      }
    }
  }
}, { immediate: true })

async function loadData() {
  if (!route.params.id || !props.fetchData) return
  const reqId = route.params.id
  try {
    const data = await props.fetchData(route.params.id)
    // 竞态保护：如果用户已切到其他页面（新增/其他编辑），丢弃本次响应
    if (reqId !== route.params.id) return
    // 先重置再赋值，避免旧数据残留
    resetForm()
    Object.assign(form, data)
    // 安全处理：密码字段永远不显示实际值（编辑时留空，由用户决定是否修改）
    if (form.password !== undefined) {
      form.password = ''
    }
    // 加载自定义字段值
    if (props.customFieldEntity && form[props.idField]) {
      const cfValues = await loadCustomFieldValues(props.customFieldEntity, form[props.idField])
      Object.assign(customFieldValues, cfValues)
      // 把自定义字段值合并到 form 中（用于表单回显）
      Object.keys(cfValues).forEach(k => {
        form[k] = cfValues[k]
      })
      // 加载完后立即触发一次公式计算（确保公式字段有值）
      recalcFormulaFields()
    }
  } catch (e) {
    ElMessage.error(tt('加载数据失败'))
  }
}

function recalcFormulaFields() {
  if (customFormulaFields.value.length === 0) return
  const computed = computeFormulaFields(customFormulaFields.value, form)
  Object.keys(computed).forEach(k => {
    if (computed[k] !== null && computed[k] !== undefined) {
      form[k] = computed[k]
      customFieldValues[k] = computed[k]
    }
  })
}

// 公式字段实时计算：监听 form 变化，自动计算公式字段值
let formulaCalcTimer = null
watch(form, () => {
  if (customFormulaFields.value.length === 0) return
  // 防抖，避免频繁计算
  if (formulaCalcTimer) clearTimeout(formulaCalcTimer)
  formulaCalcTimer = setTimeout(() => {
    recalcFormulaFields()
  }, 50)
}, { deep: true })

// 校验必填项（口径与界面星号完全同源：凡是打了 * 的字段，这里一律拦）
function validate() {
  const missing = []
  allFields.value.forEach(f => {
    if (!isFieldRequiredDisplay(f)) return
    const val = form[f.prop]
    if (val === '' || val === null || val === undefined) {
      missing.push(f.label)
    }
  })
  if (missing.length) {
    ElMessage.warning(`${tt('请填写必填项：')}${missing.join('、')}`)
    return false
  }
  return true
}

async function saveAndBack() {
  if (saving.value) return
  if (!validate()) return
  saving.value = true
  try {
    await props.onSave({ ...form }, isEdit.value)
    // 保存自定义字段值（编辑模式下，新增模式由 onSave 返回 id 后处理）
    if (props.customFieldEntity && form[props.idField]) {
      // 收集自定义字段值
      const cfValues = {}
      customFieldMap.value.forEach((def, key) => {
        cfValues[key] = form[key]
      })
      await saveCustomFieldValues(props.customFieldEntity, form[props.idField], cfValues, customFormulaFields.value)
    }
    ElMessage.success(tt('保存成功'))
    goBack()
  } catch (e) {
    // 业务校验异常（如用户名被占用）必须告知用户，否则表现为"按钮点了没反应"
    ElMessage.error(e && e.message ? e.message : tt('保存失败，请检查填写内容'))
  } finally {
    saving.value = false
  }
}

async function saveDraft() {
  // 草稿状态：若有 status 字段则置为停用
  if ('status' in form) {
    form.status = '停用'
  }
  // 草稿模式不校验必填项，只记录内容
  // 但数据库NOT NULL字段（如编码、名称）为空时INSERT会失败，
  // 这里静默填充草稿占位值以保证保存成功，用户后续可在"保存并返回"时修改
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const ts = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  const rand = String(Math.floor(Math.random() * 1000)).padStart(3, '0')
  allFields.value.forEach(f => {
    // 草稿模式不校验必填，但对"静态必填 OR 权限必填"的字段仍要填占位值，
    // 避免 NOT NULL 列 INSERT 失败（这里用 isFieldRequiredRaw：即便该字段当前不可编辑，也要兜底）
    if (!isFieldRequiredRaw(f)) return
    const val = form[f.prop]
    if (val !== '' && val !== null && val !== undefined) return
    // 编码/编号字段：生成唯一草稿编码
    if (/Code$|No$/.test(f.prop)) {
      form[f.prop] = `DRAFT-${ts}-${rand}`
    }
    // 名称字段：使用草稿占位
    else if (/Name$|Title$/.test(f.prop)) {
      form[f.prop] = `（草稿）${ts}`
    }
    // 其他必填字段：优先使用字段默认值
    else if (f.default !== undefined && f.default !== '') {
      form[f.prop] = f.default
    }
    // select类型且无默认值：使用第一个选项
    else if (f.type === 'select' && f.options && f.options.length > 0) {
      form[f.prop] = f.options[0].value
    }
  })
  try {
    if (saving.value) return
    saving.value = true
    await props.onSave({ ...form }, isEdit.value)
    if (props.customFieldEntity && form[props.idField]) {
      const cfValues = {}
      customFieldMap.value.forEach((def, key) => {
        cfValues[key] = form[key]
      })
      await saveCustomFieldValues(props.customFieldEntity, form[props.idField], cfValues, customFormulaFields.value)
    }
    ElMessage.success(tt('保存成功'))
    goBack()
  } catch (e) {
    // 草稿保存失败同样需要告知用户
    ElMessage.error(e && e.message ? e.message : tt('保存失败，请检查填写内容'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadRemoteOptions()
})
</script>

<style scoped lang="scss">
.base-edit-page {
  padding: 20px 24px;
  background: #fafafa;
  min-height: calc(100vh - 60px);
}

/* 顶部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 16px;
  margin-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
  flex: 1 1 260px;
}
.header-actions {
  flex-shrink: 0;
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 0;
}
.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #6b7280;
  font-size: 12px;
  padding: 4px 0;
  &:hover { color: #6366f1; }
}
.back-arrow {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: #fff;
  border: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #4b5563;
  transition: all .2s;
}
.back-btn:hover .back-arrow {
  background: #eef2ff;
  border-color: #6366f1;
  color: #6366f1;
}
.title-group { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  letter-spacing: -.3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.page-sub {
  font-size: 12px;
  color: #9ca3af;
}
.req { color: #ef4444; }

/* 表单卡片 */
.form-content {
  max-width: 1100px;
}
.form-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 24px 28px;
  margin-bottom: 16px;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 14px;
  border-bottom: 1px solid #f5f5f5;
}
.title-bar {
  width: 3px;
  height: 14px;
  background: #6366f1;
  border-radius: 2px;
}
.title-text { color: #1f2937; }
.title-sub {
  font-size: 12px;
  color: #9ca3af;
  font-weight: 400;
  margin-left: 8px;
}
.form-grid {
  display: grid;
  /* 标签在左、一行 3 个字段；!important 覆盖全局「按容器宽度自动排」规则 */
  grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
}
.form-grid :deep(.el-form-item) { margin-bottom: 0; }
.form-grid :deep(.el-form-item__label) {
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
  height: auto;
}
.form-grid :deep(.el-form-item__content) {
  line-height: 1;
}
.form-grid :deep(.el-input__wrapper),
.form-grid :deep(.el-select__wrapper),
.form-grid :deep(.el-input-number) {
  box-shadow: 0 0 0 1px #e5e7eb inset;
  border-radius: 6px;
}
.form-grid :deep(.el-input__wrapper):hover,
.form-grid :deep(.el-select__wrapper):hover {
  box-shadow: 0 0 0 1px #c7d2fe inset;
}
.form-grid :deep(.el-input__wrapper.is-focus),
.form-grid :deep(.el-select__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #6366f1 inset;
}

/* 底部 */
.form-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  margin-top: 8px;
  max-width: 1100px;
}
.footer-info {
  font-size: 12px;
  color: #9ca3af;
}
.footer-actions { display: flex; gap: 8px; }

/* 按钮样式 */
:deep(.el-button) {
  border-radius: 6px;
  font-weight: 500;
}
:deep(.el-button--primary) {
  background: #6366f1;
  border-color: #6366f1;
  &:hover {
    background: #4f46e5;
    border-color: #4f46e5;
  }
}

/* ========== 系统默认必填 label 旁 🔒 标记（与 FieldPermission 中的🔒语义一致） ========== */
.base-edit-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  .static-lock-icon {
    color: #7c3aed;      /* 紫色，与 FieldPermission 🔒 统一颜色 */
    font-size: 14px;
    cursor: help;
    margin-left: 2px;
    transform: translateY(-1px);
  }
}

/* 响应式 */
@media (max-width: 1100px) {
  .form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)) !important; }
}
@media (max-width: 700px) {
  .form-grid { grid-template-columns: 1fr !important; }
  .page-header { flex-direction: column; align-items: flex-start; gap: 12px; }
}
</style>
