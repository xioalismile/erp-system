<template>
  <div class="erp-page field-permission-page">
    <el-card shadow="never" class="permission-card">
      <template #header>
        <div class="card-header">
          <div class="title-wrap">
            <span class="title">{{ $tt("字段权限配置") }}</span>
            <el-tooltip :content="$tt('按角色控制每个业务页面的字段：可见/可编辑/必填；系统默认必填字段（如『员工姓名』）强制锁定，不可修改')" placement="top">
              <el-icon class="info-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
          <!-- 统计面板（Step6） -->
          <div class="stat-bar" v-if="fieldList.length > 0">
            <el-tag type="info" effect="plain" size="small">共 {{ fieldList.length }} 字段</el-tag>
            <el-tag type="success" effect="plain" size="small">可见 {{ visibleCount }}</el-tag>
            <el-tag type="warning" effect="plain" size="small">隐藏 {{ hiddenCount }}</el-tag>
            <el-tag type="success" effect="light" size="small">可编辑 {{ editableCount }}</el-tag>
            <el-tag type="warning" effect="light" size="small">只读 {{ readonlyCount }}</el-tag>
            <el-tag type="danger" effect="plain" size="small">必填 {{ requiredCount }}</el-tag>
            <el-tag type="primary" effect="dark" size="small">🔒 默认必填 {{ defaultRequiredCount }}</el-tag>
            <!-- 脏数据提示（Step7） -->
            <el-tag v-if="dirtyCount > 0" type="danger" effect="dark" size="small" class="dirty-tag">
              📝 有未保存变更 {{ dirtyCount }} 处
            </el-tag>
          </div>
          <div>
            <el-button v-permission="perm('save')" type="primary" @click="onSave" :disabled="!canSave || dirtyCount === 0">{{ $tt("保存配置") }}</el-button>
            <el-button v-permission="perm('reset')" @click="onReset" :disabled="!selectedRoleId || !selectedPageKey">{{ $tt("重置") }}</el-button>
            <el-button v-permission="perm('copy')" type="warning" @click="copyDialogVisible = true" :disabled="!selectedRoleId">{{ $tt("复制权限") }}</el-button>
          </div>
        </div>
      </template>

      <div class="permission-body">
        <!-- ========== 左侧：模块快速导航 + 角色选择（Step4） ========== -->
        <div class="left-nav">
          <div class="role-select-block">
            <span class="block-label">{{ $tt("角色") }}</span>
            <el-select
              v-model="selectedRoleId"
              :placeholder="$tt('选择角色')"
              style="width:100%"
              @change="onRoleChange"
            >
              <el-option
                v-for="role in roleList"
                :key="role.roleId"
                :label="role.roleName"
                :value="role.roleId"
              />
            </el-select>
          </div>

          <div class="tree-block-label">{{ $tt("页面（按模块分组）") }}</div>
          <el-input
            v-model="navSearch"
            size="small"
            :placeholder="$tt('搜索页面名称...')"
            clearable
            class="nav-search"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>

          <el-tree
            ref="navTreeRef"
            class="page-tree"
            :data="treeData"
            node-key="key"
            :expand-on-click-node="false"
            :default-expanded-keys="defaultExpandedKeys"
            :highlight-current="true"
            :filter-node-method="filterNavNode"
            @node-click="onNavNodeClick"
          >
            <template #default="{ data }">
              <div class="tree-node" :class="{'tree-node-group': data.isGroup}">
                <span class="node-label">{{ data.label }}</span>
                <el-tag v-if="data.isGroup" effect="plain" size="small" type="info" class="count-tag">
                  {{ data.children ? data.children.length : 0 }}
                </el-tag>
                <span v-if="!data.isGroup && selectedPageKey === data.key" class="cur-arrow">→</span>
              </div>
            </template>
          </el-tree>
        </div>

        <!-- ========== 右侧：筛选条 + 表格 + 批量操作 ========== -->
        <div class="right-main">
          <!-- 快速筛选条（Step5） -->
          <div class="filter-bar" v-if="selectedPageKey">
            <div class="filter-item filter-search">
              <el-input
                v-model="fieldSearch"
                size="default"
                :placeholder="$tt('搜索字段名/字段标识...')"
                clearable
                style="width: 260px;"
              >
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
            </div>
            <div class="filter-item">
              <span class="filter-label">{{ $tt("可见") }}</span>
              <el-radio-group v-model="filterVisible" size="default">
                <el-radio-button value="all">{{ $tt("全部") }}</el-radio-button>
                <el-radio-button value="show">{{ $tt("可见") }}</el-radio-button>
                <el-radio-button value="hide">{{ $tt("隐藏") }}</el-radio-button>
              </el-radio-group>
            </div>
            <div class="filter-item">
              <span class="filter-label">{{ $tt("编辑") }}</span>
              <el-radio-group v-model="filterEditable" size="default">
                <el-radio-button value="all">{{ $tt("全部") }}</el-radio-button>
                <el-radio-button value="edit">{{ $tt("可编辑") }}</el-radio-button>
                <el-radio-button value="readonly">{{ $tt("只读") }}</el-radio-button>
              </el-radio-group>
            </div>
            <div class="filter-item">
              <span class="filter-label">{{ $tt("必填") }}</span>
              <el-radio-group v-model="filterRequired" size="default">
                <el-radio-button value="all">{{ $tt("全部") }}</el-radio-button>
                <el-radio-button value="required">{{ $tt("必填") }}</el-radio-button>
                <el-radio-button value="optional">{{ $tt("非必填") }}</el-radio-button>
                <el-radio-button value="default">{{ $tt("🔒 默认必填") }}</el-radio-button>
              </el-radio-group>
            </div>
          </div>

          <div class="table-wrapper" v-loading="loading">
            <template v-if="selectedPageKey && filteredFieldList.length > 0">
              <el-table
                :data="filteredFieldList"
                border
                stripe
                size="default"
                :row-class-name="tableRowClassName"
                class="field-table"
                @selection-change="()=>{}"
              >
                <el-table-column :label="$tf('字段名称')" min-width="170" sortable>
                  <template #default="{ row }">
                    <div class="field-name-cell" :class="{'default-req-cell': row.defaultRequired}">
                      <el-tooltip
                        v-if="row.defaultRequired"
                        :content="$tt('系统默认必填：可见/可编辑/必填三开关强制锁定，不可修改（业务单据强制校验）')"
                        placement="top"
                      >
                        <el-icon class="lock-icon default-lock"><Lock /></el-icon>
                      </el-tooltip>
                      <span class="fn-name">{{ row.fieldName }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('字段标识')" prop="fieldKey" min-width="180" sortable show-overflow-tooltip />
                <el-table-column :label="$tf('可见')" width="110" align="center">
                  <template #default="{ row }">
                    <el-switch
                      v-model="row.visible"
                      :disabled="row.defaultRequired"
                      inline-prompt
                      :active-text="$tt('开')"
                      :inactive-text="$tt('关')"
                      @change="onRowVisibleChange(row)"
                    />
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('可编辑')" width="110" align="center">
                  <template #default="{ row }">
                    <el-switch
                      v-model="row.editable"
                      :disabled="row.defaultRequired || !row.visible"
                      inline-prompt
                      :active-text="$tt('开')"
                      :inactive-text="$tt('关')"
                      @change="onRowEditableChange(row)"
                    />
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('必填')" width="110" align="center">
                  <template #default="{ row }">
                    <el-switch
                      v-model="row.required"
                      :disabled="row.defaultRequired || !row.visible || !row.editable"
                      inline-prompt
                      :active-text="$tt('是')"
                      :inactive-text="$tt('否')"
                    />
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('状态')" width="160" align="center">
                  <template #default="{ row }">
                    <div class="status-tags">
                      <el-tag v-if="row.defaultRequired" type="primary" effect="dark" size="small" class="mr">{{ $tt("🔒默认") }}</el-tag>
                      <el-tag v-else-if="!row.visible" type="warning" effect="plain" size="small" class="mr">{{ $tt("隐藏") }}</el-tag>
                      <el-tag v-else-if="!row.editable" type="warning" effect="light" size="small" class="mr">{{ $tt("只读") }}</el-tag>
                      <el-tag v-if="row.required" type="danger" effect="plain" size="small">{{ $tt("必填") }}</el-tag>
                      <el-tag v-else-if="row.visible && row.editable" type="info" effect="plain" size="small">{{ $tt("普通") }}</el-tag>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </template>

            <el-empty
              v-else-if="!selectedPageKey"
              :description="$tt('← 请先在左侧选择角色，再点击页面进行配置')"
              :image-size="120"
            />
            <el-empty
              v-else-if="filteredFieldList.length === 0"
              :description="`筛选结果为空（共${fieldList.length}个字段），请尝试调整筛选条件`"
            />

            <!-- 批量操作 -->
            <div class="quick-actions" v-if="selectedPageKey && fieldList.length > 0">
              <el-button v-permission="perm('enableAll')" size="small" @click="onAllVisible(true)" type="primary" plain>{{ $tt("全部可见") }}</el-button>
              <el-button v-permission="perm('disableAll')" size="small" @click="onAllVisible(false)" type="warning" plain>{{ $tt("全部隐藏") }}</el-button>
              <el-button v-permission="perm('enableAll')" size="small" @click="onAllEditable(true)" type="primary" plain>{{ $tt("全部可编辑") }}</el-button>
              <el-button v-permission="perm('disableAll')" size="small" @click="onAllEditable(false)" type="warning" plain>{{ $tt("全部只读") }}</el-button>
              <el-button v-permission="perm('enableAll')" size="small" @click="onAllRequired(true)" type="danger" plain>{{ $tt("全部必填") }}</el-button>
              <el-button v-permission="perm('disableAll')" size="small" @click="onAllRequired(false)" type="info" plain>{{ $tt("全部非必填") }}</el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 复制权限 Dialog -->
    <el-dialog :title="$tf('复制权限到目标角色')" v-model="copyDialogVisible" width="460px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item :label="$tf('源角色')">
          <span style="color:#374151;font-weight:500;">{{ currentRoleName || '—' }}</span>
        </el-form-item>
        <el-form-item :label="$tf('目标角色')">
          <el-select
            v-model="copyTargetRoleId"
            :placeholder="$tt('请选择目标角色')"
            style="width: 100%"
          >
            <el-option
              v-for="role in roleList.filter(r => r.roleId !== selectedRoleId)"
              :key="role.roleId"
              :label="role.roleName"
              :value="role.roleId"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$tf('说明')">
          <span style="color: #909399; font-size: 13px">
            将【源角色】的<strong>{{ $tt("所有页面字段权限") }}</strong>（含隐藏/只读/必填配置）完整复制到目标角色，已有配置会被覆盖。
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="copyDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('copy')" type="primary" :disabled="!copyTargetRoleId" @click="onCopyPermission">{{ $tt("确定复制") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:role-perm')
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, Search, QuestionFilled } from '@element-plus/icons-vue'
import { listAllRole } from '@/api/system/role'
import {
  getRoleFieldPermission,
  saveRoleFieldPermission,
  copyFieldPermission,
  getFieldMetaList,
  getAvailablePages
} from '@/api/system/fieldPermission'
import { clearFieldPermissionCache } from '@/composables/useFieldPermission'
import { tt } from '@/i18n'

const navTreeRef = ref(null)
const navSearch = ref('')
watch(navSearch, (v) => { navTreeRef.value && navTreeRef.value.filter(v) })
function filterNavNode(value, data) {
  if (!value) return true
  if (data.isGroup) {
    return data.children && data.children.some(c => (c.label + '').includes(value))
  }
  return (data.label + '').includes(value) || (data.key + '').includes(value)
}

// ======= 页面分组（动态加载后端） =======
const rawPages = ref([])     // getAvailablePages() 原始返回：[{module, key, name}]
const pageGroups = ref([])   // 同旧版，兼容原逻辑
const treeData = ref([])     // 新增：el-tree 数据（module分组）
const defaultExpandedKeys = ref([])

async function loadPageGroups() {
  try {
    const list = await getAvailablePages()
    rawPages.value = Array.isArray(list) ? list : []
    // 按 module 分组（"basedata"基础资料强制排第一）
    const byModule = new Map()
    for (const p of rawPages.value) {
      if (!p || !p.key) continue
      const moduleName = p.module || '其他'
      if (!byModule.has(moduleName)) byModule.set(moduleName, [])
      byModule.get(moduleName).push(p)
    }
    // 构造：groupEntries = [['basedata', [...]], ['crm', [...]], ...] 按自定义顺序
    const groupEntries = [...byModule.entries()].sort((a, b) => {
      const [m1] = a, [m2] = b
      const order = { basedata: 0, sale: 1, purchase: 2, inventory: 3, production: 4, subcontract: 5, finance: 6, crm: 7, sample: 8, engineering: 9 }
      const o1 = order[m1] ?? 100, o2 = order[m2] ?? 100
      if (o1 !== o2) return o1 - o2
      return m1.localeCompare(m2, 'zh')
    })
    pageGroups.value = groupEntries.map(([label, pages]) => ({ key: label, label, pages }))
    // 构造 el-tree 数据（group 节点 + 子页面节点）
    const moduleLabel = {
      basedata: '📚 基础资料', sale: '🛒 销售', purchase: '🛍️ 采购',
      inventory: '📦 库存', production: '🏭 生产', subcontract: '🔧 委外',
      finance: '💰 财务', crm: '📞 CRM', sample: '🧪 打样',
      engineering: '⚙️ 工程数据'
    }
    treeData.value = groupEntries.map(([moduleKey, pages]) => ({
      key: 'group_' + moduleKey,
      label: moduleLabel[moduleKey] || moduleKey,
      isGroup: true,
      children: pages.map(p => ({
        key: p.key,
        label: p.name || p.key,
        isGroup: false
      }))
    }))
    defaultExpandedKeys.value = treeData.value.map(g => g.key)
  } catch (e) {
    pageGroups.value = []
    treeData.value = []
    console.warn('[FieldPermission] 加载页面列表失败', e)
  }
}

const loading = ref(false)
const roleList = ref([])
const selectedRoleId = ref(null)
const selectedPageKey = ref('')
const fieldList = ref([])
const originalFieldList = ref([])
const copyDialogVisible = ref(false)
const copyTargetRoleId = ref(null)

// ======= 脏数据（Step7）：行级 diff =======
function calcDirty() {
  if (fieldList.value.length !== originalFieldList.value.length) return fieldList.value.length
  const orig = new Map(originalFieldList.value.map(x => [x.fieldKey, x]))
  let cnt = 0
  for (const row of fieldList.value) {
    const o = orig.get(row.fieldKey)
    if (!o) { cnt++; continue }
    if (!!row.visible !== !!o.visible || !!row.editable !== !!o.editable || !!row.required !== !!o.required) cnt++
  }
  return cnt
}
const dirtyCount = computed(calcDirty)
const canSave = computed(() => {
  return selectedRoleId.value && selectedPageKey.value && fieldList.value.length > 0
})
const currentRoleName = computed(() => {
  const role = roleList.value.find(r => r.roleId === selectedRoleId.value)
  return role ? role.roleName : ''
})

// ======= 实时统计（Step6）=======
const visibleCount = computed(() => fieldList.value.filter(f => f.visible).length)
const hiddenCount = computed(() => fieldList.value.filter(f => !f.visible).length)
const editableCount = computed(() => fieldList.value.filter(f => f.visible && f.editable).length)
const readonlyCount = computed(() => fieldList.value.filter(f => f.visible && !f.editable).length)
const requiredCount = computed(() => fieldList.value.filter(f => f.visible && f.editable && f.required).length)
const defaultRequiredCount = computed(() => fieldList.value.filter(f => f.defaultRequired).length)

// ======= 快速筛选（Step5）=======
const fieldSearch = ref('')
const filterVisible = ref('all')    // all / show / hide
const filterEditable = ref('all')   // all / edit / readonly
const filterRequired = ref('all')   // all / required / optional / default

const filteredFieldList = computed(() => {
  let list = fieldList.value.slice()
  const kw = fieldSearch.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(f =>
      (f.fieldName + '').toLowerCase().includes(kw) ||
      (f.fieldKey + '').toLowerCase().includes(kw)
    )
  }
  if (filterVisible.value === 'show') list = list.filter(f => f.visible)
  else if (filterVisible.value === 'hide') list = list.filter(f => !f.visible)
  if (filterEditable.value === 'edit') list = list.filter(f => f.visible && f.editable)
  else if (filterEditable.value === 'readonly') list = list.filter(f => f.visible && !f.editable)
  if (filterRequired.value === 'required') list = list.filter(f => f.required)
  else if (filterRequired.value === 'optional') list = list.filter(f => !f.required)
  else if (filterRequired.value === 'default') list = list.filter(f => f.defaultRequired)
  return list
})

// ======= 左侧树点击（Step4）========
function onNavNodeClick(node) {
  if (node.isGroup) return
  const mustAsk = dirtyCount.value > 0
  const go = () => {
    selectedPageKey.value = node.key
    // 重置筛选
    fieldSearch.value = ''
    filterVisible.value = 'all'
    filterEditable.value = 'all'
    filterRequired.value = 'all'
    loadFieldPermission()
  }
  if (mustAsk) {
    ElMessageBox.confirm(
      `${tt("当前页面有 ")}${dirtyCount.value}${tt(" 处未保存的更改，切换页面后会丢失。是否继续？")}`,
      tt('未保存更改'),
      { type: 'warning', confirmButtonText: tt('继续切换'), cancelButtonText: tt('取消') }
    ).then(go).catch(() => {})
  } else {
    go()
  }
}

async function loadRoleList() {
  try {
    roleList.value = (await listAllRole()) || []
  } catch (e) {
    roleList.value = []
  }
}

async function loadFieldPermission() {
  if (!selectedRoleId.value || !selectedPageKey.value) {
    fieldList.value = []
    originalFieldList.value = []
    return
  }
  loading.value = true
  try {
    const [metaList, permList] = await Promise.all([
      getFieldMetaList(selectedPageKey.value).catch(() => []),
      getRoleFieldPermission(selectedRoleId.value, selectedPageKey.value).catch(() => [])
    ])
    const permMap = {}
    if (Array.isArray(permList)) {
      permList.forEach(item => { permMap[item.fieldKey] = item })
    }
    // 后端存储为 Integer(0/1)，统一按数值解析（undefined=未配置，默认可见/可编辑）
    const asBool = (v, def) => (v === undefined || v === null) ? def : Number(v) === 1
    const list = []
    if (Array.isArray(metaList) && metaList.length > 0) {
      metaList.forEach(meta => {
        const perm = permMap[meta.fieldKey] || {}
        // 系统默认必填字段（defaultRequired=1）：UI必须显示为必填=true，前端回显+禁用，后端保存兜底。
        const isDefaultRequired = asBool(meta.defaultRequired, false)
        list.push({
          fieldKey: meta.fieldKey,
          fieldName: meta.fieldName || meta.fieldKey,
          visible: isDefaultRequired ? true : asBool(perm.visible, true),
          editable: isDefaultRequired ? true : asBool(perm.editable, true),
          required: isDefaultRequired ? true : asBool(perm.required, false),
          defaultRequired: isDefaultRequired
        })
      })
    } else if (Array.isArray(permList)) {
      permList.forEach(item => {
        list.push({
          fieldKey: item.fieldKey,
          fieldName: item.fieldName || item.fieldKey,
          visible: asBool(item.visible, true),
          editable: asBool(item.editable, true),
          required: asBool(item.required, false),
          defaultRequired: false
        })
      })
    }
    fieldList.value = list
    originalFieldList.value = JSON.parse(JSON.stringify(list))
  } finally {
    loading.value = false
  }
}

function onRoleChange() {
  const mustAsk = dirtyCount.value > 0
  const go = () => {
    selectedPageKey.value = ''
    if (navTreeRef.value) navTreeRef.value.setCurrentKey(null)
    loadFieldPermission()
  }
  if (mustAsk) {
    ElMessageBox.confirm(
      `${tt("当前页面有 ")}${dirtyCount.value}${tt(" 处未保存的更改，切换角色后会丢失。是否继续？")}`,
      tt('未保存更改'),
      { type: 'warning', confirmButtonText: tt('继续切换'), cancelButtonText: tt('取消') }
    ).then(go).catch(() => {})
  } else {
    go()
  }
}

function onPageChange() { /* 原逻辑改用左侧树事件，保留空函数以兼容旧属性 */ }

// ======= 行类名（整行变灰 Step8）=======
function tableRowClassName({ row }) {
  if (row.defaultRequired) return 'default-required-row'
  if (!row.visible) return 'hidden-row'
  return ''
}

// ======= 保存（后端 Integer 0/1）=======
async function onSave() {
  if (!canSave.value) return
  // 高危数据丢失检查：保存 payload 长度 === 源数组长度，禁止筛选项只存一部分
  if (fieldList.value.length === 0) return
  const savePayload = fieldList.value.map(f => ({
    fieldKey: f.fieldKey,
    visible: (f.defaultRequired ? true : f.visible) ? 1 : 0,
    editable: (f.defaultRequired ? true : f.editable) ? 1 : 0,
    required: (f.defaultRequired ? true : f.required) ? 1 : 0
  }))
  if (savePayload.length !== fieldList.value.length) {
    ElMessage.error(`${tt("保存失败：数据不一致(")}${savePayload.length}!==${fieldList.value.length}${tt(")，请刷新后重试")}`)
    return
  }
  loading.value = true
  try {
    await saveRoleFieldPermission(selectedRoleId.value, selectedPageKey.value, savePayload)
    originalFieldList.value = JSON.parse(JSON.stringify(fieldList.value))
    // 保存后清该页面的字段权限全局缓存→让所有BaseEdit业务表单下次打开时重新拉权限
    if (selectedPageKey.value) clearFieldPermissionCache(selectedPageKey.value)
    ElMessage.success(tt('保存成功，字段权限立即生效（刷新业务表单即可看到效果）'))
  } finally {
    loading.value = false
  }
}

function onReset() {
  if (originalFieldList.value.length === 0) {
    ElMessage.info(tt('无已保存配置可重置'))
    return
  }
  ElMessageBox.confirm(tt('确定重置当前页面的字段权限吗？未保存的修改将丢失。'), tt('重置确认'), {
    type: 'warning', confirmButtonText: tt('确定重置'), cancelButtonText: tt('取消')
  }).then(() => {
    fieldList.value = JSON.parse(JSON.stringify(originalFieldList.value))
    ElMessage.success(tt('已重置为上次保存的配置'))
  }).catch(() => {})
}

// ======= 批量操作（系统默认必填行一律跳过）=======
function onAllVisible(visible) {
  fieldList.value.forEach(item => {
    if (item.defaultRequired) return
    item.visible = visible
    if (!visible) { item.editable = false; item.required = false }
    if (visible && !item.editable && !item.defaultRequired) { item.editable = true }
  })
}
function onAllEditable(editable) {
  fieldList.value.forEach(item => {
    if (item.defaultRequired) return
    if (item.visible) {
      item.editable = editable
      if (!editable) item.required = false
    }
  })
}
function onAllRequired(required) {
  fieldList.value.forEach(item => {
    if (item.defaultRequired) return
    if (item.visible && item.editable) {
      item.required = required
    }
  })
}

// ======= 行级联动：不允许产生"必填但不可见/不可编辑"的矛盾组合 =======
// 业务表单口径：字段带 * 就一定能填写、点"保存并返回"一定被校验；
// 若配成"必填+只读"，该必填在表单上只会静默失效，故此处直接联动取消。
function onRowVisibleChange(row) {
  if (!row || row.defaultRequired) return
  if (!row.visible) {
    row.editable = false
    row.required = false
  } else if (!row.editable) {
    row.editable = true
  }
}
function onRowEditableChange(row) {
  if (!row || row.defaultRequired) return
  if (!row.editable) row.required = false
}

async function onCopyPermission() {
  if (!copyTargetRoleId.value) return
  try {
    loading.value = true
    await copyFieldPermission(selectedRoleId.value, copyTargetRoleId.value)
    // 复制权限影响目标角色的全部页面字段权限，清全局缓存让所有页面强制重拉
    clearFieldPermissionCache()
    ElMessage.success(`${tt("字段权限已完整复制到角色 ID=")}${copyTargetRoleId.value}`)
    copyDialogVisible.value = false
    copyTargetRoleId.value = null
  } catch (e) {
    ElMessage.error(e && e.message ? e.message : tt('复制失败'))
  } finally {
    loading.value = false
  }
}

// ======= 刷新前拦截（Step7 beforeunload）=======
function handleBeforeUnload(e) {
  if (dirtyCount.value > 0) {
    e.preventDefault()
    e.returnValue = `您有 ${dirtyCount.value} 处字段权限更改尚未保存，确定离开此页？`
    return e.returnValue
  }
}

onMounted(() => {
  loadRoleList()
  loadPageGroups()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<style lang="scss" scoped>
.field-permission-page { padding: 16px; }
.permission-card {
  height: calc(100vh - 120px);
  :deep(.el-card__body) { height: 100%; padding: 14px 18px; }
}
.card-header {
  display: flex; align-items: center; justify-content: space-between; gap: 14px; flex-wrap: wrap;
  .title-wrap { display: flex; align-items: center; gap: 8px; }
  .title { font-size: 16px; font-weight: 600; color: #111827; }
  .info-icon { color:#9ca3af; cursor:help; font-size:15px; }
}
.stat-bar {
  display: flex; gap: 8px; flex-wrap: wrap; align-items: center; flex: 1 1 420px; justify-content: center;
  .dirty-tag {
    background: #fef2f2 !important; color: #b91c1c !important; border-color: #fecaca !important;
    font-weight: 600; animation: pulse 1.4s infinite;
  }
}
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.55} }

/* ========== 左右栏布局 ========== */
.permission-body {
  display: flex; gap: 14px; height: calc(100% - 14px); margin-top: 8px;
}
.left-nav {
  width: 270px; flex-shrink: 0;
  border: 1px solid #eef0f4; border-radius: 8px;
  background: #fff; display: flex; flex-direction: column; padding: 14px 12px;
  overflow: hidden;
  .role-select-block { margin-bottom: 14px; padding-bottom: 12px; border-bottom: 1px dashed #eef0f4; }
  .block-label, .tree-block-label { font-size:12px; color:#6b7280; font-weight:500; margin-bottom:6px; display:block; }
  .tree-block-label { margin-top: 4px; }
  .nav-search { margin-bottom: 8px; }
  .page-tree { flex:1; overflow:auto; font-size:13px; }
  .tree-node {
    display:flex; align-items:center; justify-content:space-between; width:100%; padding-right:6px;
    &.tree-node-group { font-weight: 600; color: #1f2937; }
    .cur-arrow { color:#6366f1; font-weight: 700; }
  }
  .count-tag { font-weight: 500; margin-left: 6px; }
}
.right-main { flex: 1; min-width: 0; display: flex; flex-direction: column; }

/* ========== 筛选条 ========== */
.filter-bar {
  display: flex; gap: 16px; align-items: center; padding: 10px 14px;
  background: #f9fafb; border: 1px solid #eef0f4; border-radius: 8px;
  margin-bottom: 12px; flex-wrap: wrap;
  .filter-item {
    display: flex; align-items: center; gap: 8px;
    .filter-label { font-size: 13px; color: #374151; font-weight: 500; white-space: nowrap; }
  }
  .filter-search { margin-right: auto; }
}

/* ========== 表格 ========== */
.table-wrapper { flex:1; display:flex; flex-direction:column; min-height: 0; }
.field-table { flex:1; }
.field-name-cell {
  display: flex; align-items: center; gap: 6px;
  .lock-icon { color:#6366f1; }
  .lock-icon.default-lock { color:#7c3aed; }
  &.default-req-cell .fn-name { color:#4338ca; font-weight: 600; }
}
.status-tags { display:flex; gap:4px; justify-content:center; flex-wrap:wrap; .mr { margin-right:3px; } }

/* ========== 系统默认必填整行变灰（Step8 视觉） ========== */
:deep(.default-required-row) {
  background: #f3f4f6 !important;
  td .cell { color: #4b5563 !important; font-weight: 500; }
  &:hover > td { background: #e5e7eb !important; }
}
:deep(.hidden-row) {
  td .cell { color: #c0c4cc !important; text-decoration: line-through dotted #c0c4cc; }
}

/* ========== 批量操作 ========== */
.quick-actions { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }

/* ========== 响应式 ========== */
@media (max-width: 1000px) {
  .permission-body { flex-direction: column; }
  .left-nav { width: 100%; max-height: 320px; }
  .stat-bar { flex-direction: column; align-items: flex-start !important; }
}
</style>
