<template>
  <!--
    通用下推按钮组件 - 在源单据列表/编辑页使用
    点击后弹出可选目标单据, 选择后跳转到目标单据新建页并携带源单参数
    用法:
      <PushDownButton source-type="SaleOrder" :source-row="row" type="primary" />
      <PushDownButton source-type="SaleQuote" :source-doc="form" />
    【2026-10-09】extraItems：把"服务端直接生成"类入口（如销售订单下推采购订单）并进同一个「下推」菜单，
      避免页面上出现两个各自叫「下推」的按钮（用户反馈）。选到此类项时 emit('extra', key)，由页面自行处理。
  -->
  <!-- 【2026-10-19 按钮权限接线修复】下拉形态与单按钮形态同码同效（同一处功能必须同效口径），
       均按"所在页面对象"判定，不再固定按销售订单对象判定（PRD：按钮权限改版 FR-03） -->
  <el-dropdown v-if="hasMenu" ref="dropdownRef" split-button :type="type" :size="size" v-permission="perm('pushTo')" @click="onPushFirst" @command="onCommand" @visible-change="onDropdownVisible">
    下推
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-for="t in targets" :key="t.targetType" :command="{ kind: 'target', key: t.targetType }">
          下推生成{{ t.targetName }}
        </el-dropdown-item>
        <el-dropdown-item
          v-for="e in extraItems"
          :key="'x-' + e.key"
          :command="{ kind: 'extra', key: e.key }"
          divided
        >
          {{ e.label }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <el-button v-else-if="targets.length === 1" :type="type" :size="size" v-permission="perm('pushTo')" @click="onPush(targets[0].targetType)">
    {{ singleButtonText }}
  </el-button>
</template>

<script setup>
import { computed, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPushTargets, getDocType, getRelation, DOC_BACKEND_TYPE } from '@/utils/pushRelations'
import { getBillPushStatus } from '@/api/billLink'
import { getGetApi } from '@/utils/pushApiRegistry'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { useUserStore } from '@/stores/user'
import { isRouteVisible } from '@/utils/versionGate'

// 【2026-10-19 按钮权限接线修复】不再写死销售订单对象：按组件所在页面路由自动解析「模块:对象」，
// 编辑页/列表页都能落到本页对象码（如 采购订单编辑页 → 采购订单）；解析不到时返回空串 → v-permission 放行（安全兜底）
const perm = usePagePerm()
const userStore = useUserStore()

const props = defineProps({
  // 源单类型 key (如 'SaleOrder')
  sourceType: { type: String, required: true },
  // 源单行 (列表页传入) 或源单对象 (编辑页传入)
  sourceRow: { type: Object, default: null },
  sourceDoc: { type: Object, default: null },
  // 按钮样式
  type: { type: String, default: 'success' },
  size: { type: String, default: 'default' },
  // 可选: 显式传入状态 (默认从 sourceRow/sourceDoc.status 读取)
  status: { type: String, default: '' },
  // 【2026-10-09】并进「下推」菜单的额外项：[{ key, label }]；选中时 emit('extra', key)
  extraItems: { type: Array, default: () => [] }
})
const emit = defineEmits(['extra'])

const router = useRouter()
const dropdownRef = ref()

// 【2026-10-31 全模块下推按钮隐藏】进入页面/打开菜单时取一次通用下推状态：
// 目标已 FULL（生效+草稿已覆盖来源数量）→ 该目标不显示；所有目标都 FULL 且无额外项 → 整个下推按钮不显示。
const pushStatus = ref(null)

async function loadPushStatus () {
  const billType = DOC_BACKEND_TYPE[props.sourceType]
  const doc = getDocType(props.sourceType)
  const srcId = sourceObj.value[doc?.idField]
  if (!billType || !srcId) return
  try {
    const map = await getBillPushStatus(billType, [srcId])
    pushStatus.value = (map && map[String(srcId)]) || null
  } catch (e) {
    // 状态取数失败不阻断下推；后端守卫仍会兜底
    pushStatus.value = null
  }
}

onMounted(loadPushStatus)

function onDropdownVisible (visible) {
  if (visible) loadPushStatus()
}

function targetStatus (targetType) {
  const backendType = DOC_BACKEND_TYPE[targetType]
  const map = pushStatus.value && pushStatus.value.targets
  return backendType && map ? map[backendType] : null
}

const sourceObj = computed(() => props.sourceDoc || props.sourceRow || {})
const sourceStatus = computed(() => props.status || sourceObj.value.status || '')

// 【2026-11-08 下推状态刷新】详情异步加载、审核生效、自动下推生成下游后，源单状态/主键会变化；
// 只在 onMounted 取一次会导致"下游已经生成了，下推按钮还在"。这里跟随状态与主键变化重新取状态。
watch(
  [sourceStatus, () => sourceObj.value[getDocType(props.sourceType)?.idField]],
  () => loadPushStatus()
)

// 查询可下推的目标单据列表（按当前账号可见菜单过滤：不属于本版本/无权限的目标不显示，2026-10-10）
// 【2026-10-19 下推接线修复（PRD FR-03"开启后恢复显示"）】受限角色菜单清单只有具体页面路径、
// 没有模块根路径（如 /sale），原一级前缀判定会把已授权目标误杀 → 本组件内补"授权路径是目标路由前缀"
// 的兼容判定；共享的可见性工具口径不动（看板卡片/来源去向单号等不受影响）。
function targetVisible (doc, paths) {
  const route = (doc && (doc.editRoute || doc.route)) || ''
  if (!route) return true
  if (isRouteVisible(route, paths)) return true
  const list = paths || []
  return list.some(p => typeof p === 'string' && p.length > 1 && route.startsWith(p))
}
const targets = computed(() => {
  const paths = userStore.menuRoutePaths || []
  return getPushTargets(props.sourceType)
    .map(r => ({ r, doc: getDocType(r.targetType) }))
    .filter(({ r }) => !(targetStatus(r.targetType)?.state === 'FULL'))
    .filter(({ doc }) => targetVisible(doc, paths))
    .map(({ r, doc }) => ({
      targetType: r.targetType,
      targetName: doc?.name || r.targetType,
      requiredStatus: r.sourceStatusRequired
    }))
})

// 【2026-11-08 下推按钮文案】原本有多个目标、部分目标已 FULL 被隐藏后只剩 1 个时，
// 单按钮必须直接显示剩余目标名称，避免用户误以为剩下的「下推」还是推刚才那个已完成的目标。
const hiddenByFull = computed(() => {
  const paths = userStore.menuRoutePaths || []
  const visibleAll = getPushTargets(props.sourceType)
    .map(r => ({ r, doc: getDocType(r.targetType) }))
    .filter(({ doc }) => targetVisible(doc, paths))
  return visibleAll.length > targets.value.length
})
const singleButtonText = computed(() =>
  hiddenByFull.value && targets.value.length === 1
    ? tt('下推生成') + targets.value[0].targetName
    : tt('下推')
)

/** 是否渲染下拉菜单：多于 1 个跳转目标，或存在并进来的额外项 */
const hasMenu = computed(() => targets.value.length > 1 || props.extraItems.length > 0)

/** 菜单命令分发：额外项 → emit('extra')；原目标 → 原下推逻辑 */
function onCommand (cmd) {
  if (!cmd) return
  if (cmd.kind === 'extra') { emit('extra', cmd.key); return }
  onPush(cmd.key)
}

function onPushFirst() {
  if (targets.value.length === 1) {
    onPush(targets.value[0].targetType)
  } else if (targets.value.length === 0 && props.extraItems.length === 1) {
    // 2026-11-18：已无可下推目标、只剩一个并入项（如已结案订单只剩「销售替代出库」）时，
    // 点主按钮直接执行该项，避免主按钮点了没反应造成"点击静默"。
    emit('extra', props.extraItems[0].key)
  } else {
    // 2026-11-18：有多个目标时主按钮没有唯一的"直接动作"，改为展开下拉，
    // 不再出现"点下推没反应"（用户反馈的静默问题）。
    dropdownRef.value?.handleOpen?.()
  }
}

function onPush(targetType) {
  const target = targets.value.find(t => t.targetType === targetType)
  if (!target) return

  // 状态兜底：目标在生效+草稿下已 FULL 时，不再允许下推
  const fullStatus = targetStatus(targetType)
  if (fullStatus && fullStatus.state === 'FULL') {
    ElMessage.warning(tt('该目标已全部下推，不能重复下推'))
    return
  }

  // 校验源单状态 (支持字符串或数组)
  const required = target.requiredStatus
  if (required) {
    const ok = Array.isArray(required) ? required.includes(sourceStatus.value) : sourceStatus.value === required
    if (!ok) {
      const display = Array.isArray(required) ? required.join(' / ') : required
      ElMessage.warning(`${tt("源单状态需为「")}${display}${tt("」才能下推, 当前状态: ")}${sourceStatus.value || tt('未知')}`)
      return
    }
  }

  const srcId = sourceObj.value[getDocType(props.sourceType)?.idField]
  if (!srcId) {
    ElMessage.warning(tt('请先保存源单据后再下推'))
    return
  }

  // 【下推前拦截】配置了"已全部开票提示"的链路（销售退货单→销售发票）：
  // 全部明细行剩余可开票数量 ≤ 0 时提示并中止，不跳转空白目标页
  const relation = getRelation(props.sourceType, targetType)
  if (relation && relation.fullyInvoicedTip) {
    checkFullyInvoiced(relation, srcId).then(blocked => {
      if (!blocked) doNavigate(targetType, srcId)
    })
    return
  }

  doNavigate(targetType, srcId)
}

/** 检查源单明细是否已全部开票：明细优先用当前页已有数据（编辑页），没有再拉源单详情 */
async function checkFullyInvoiced(relation, srcId) {
  try {
    let items = sourceObj.value.items
    if (!Array.isArray(items) || items.length === 0) {
      const getApi = getGetApi(props.sourceType)
      if (!getApi || !getApi.get) return false
      const fullDoc = await getApi.get(srcId)
      items = (fullDoc && fullDoc.items) || []
    }
    if (items.length === 0) return false
    const hasRemain = items.some(it =>
      ((it.quantity || 0) - (it.turnedInvoiceQuantity || 0)) > 0)
    if (!hasRemain) {
      ElMessage.warning(tt(relation.fullyInvoicedTip))
      return true
    }
    return false
  } catch (e) {
    // 检查失败不阻断下推，交给目标页装载与后端校验兜底
    console.warn('[PushDownButton] 开票检查失败', e)
    return false
  }
}

function doNavigate(targetType, srcId) {
  // 跳转到目标单据新建页, 携带源单参数
  const targetDoc = getDocType(targetType)
  const editPath = `${targetDoc.editRoute}/new`
  router.push({
    path: editPath,
    query: {
      srcType: props.sourceType,
      srcId: srcId
    }
  })
}
</script>
