<template>
  <div class="bill-link-panel">
    <!-- ============ 来源单据 ============ -->
    <template v-if="mode === 'source'">
      <div v-if="loading" class="state-tip">{{ $tt("加载中…") }}</div>
      <div v-else-if="error" class="state-tip error">{{ error }}</div>
      <div v-else-if="sourceDocs.length === 0" class="state-tip">{{ $tt("暂无来源单据") }}</div>
      <template v-else>
        <!-- 来源单类型子页签（只有一张来源单时不显示） -->
        <el-tabs v-if="sourceDocs.length > 1" v-model="activeSourceTab" class="sub-tabs">
          <el-tab-pane
            v-for="d in sourceDocs"
            :key="d.docType + '-' + d.id"
            :label="d.docName"
            :name="d.docType + '-' + d.id"
          />
        </el-tabs>

        <div class="src-head">
          <span class="src-no">
            <el-icon><Tickets /></el-icon>
            {{ currentSource.docName }}：<strong>{{ currentSource.no }}</strong>
          </span>
          <span class="src-meta">日期 {{ currentSource.date || '—' }}</span>
          <span class="src-meta">状态 {{ $dt(currentSource.status || '—') }}</span>
          <el-button
            v-if="canJump(currentSource.docType) && currentSource.id"
            v-permission="perm('view')"
            size="small"
            type="primary"
            link
            @click="goTo(currentSource.docType, currentSource.id)"
          >{{ $tt("查看来源单 →") }}</el-button>
        </div>

        <el-table :data="currentSource.items || []" size="small" border class="compact-table">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="lineNo" :label="$tf('行号')" width="60" />
          <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="120" />
          <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="130" />
          <el-table-column prop="spec" :label="$tf('规格')" min-width="110" show-overflow-tooltip />
          <el-table-column :label="$tf('源单数量')" width="100" align="right">
            <template #default="{ row }">{{ fmtQty(row.srcQty) }}</template>
          </el-table-column>
          <el-table-column :label="$tf('源单单价')" width="100" align="right">
            <template #default="{ row }">¥{{ fmtAmt(row.srcPrice) }}</template>
          </el-table-column>
          <el-table-column :label="$tf('本单下推数量')" width="115" align="right">
            <template #default="{ row }">
              <span class="hl">{{ fmtQty(row.pushedQty) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('本单下推单价')" width="115" align="right">
            <template #default="{ row }">¥{{ fmtAmt(row.pushedPrice) }}</template>
          </el-table-column>
          <el-table-column :label="$tf('已下推')" width="100" align="right">
            <template #default="{ row }">{{ fmtQty(row.turnedTotal) }}</template>
          </el-table-column>
          <el-table-column :label="$tf('未下推')" width="100" align="right">
            <template #default="{ row }">
              <span :class="Number(row.remainQty || 0) > 0 ? 'remain' : ''">{{ fmtQty(row.remainQty) }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="sum-bar">
          <span>{{ $tt("源单数量：") }}<strong>{{ fmtQty(currentSource.total && currentSource.total.srcQty) }}</strong></span>
          <span>{{ $tt("本单下推：") }}<strong class="hl">{{ fmtQty(currentSource.total && currentSource.total.pushedQty) }}</strong></span>
          <span>{{ $tt("累计已下推：") }}<strong>{{ fmtQty(currentSource.total && currentSource.total.turnedTotal) }}</strong></span>
          <span>{{ $tt("未下推：") }}<strong class="remain">{{ fmtQty(currentSource.total && currentSource.total.remainQty) }}</strong></span>
          <span>{{ $tt("本单下推金额：") }}<strong class="hl">¥{{ fmtAmt(currentSource.total && currentSource.total.pushedAmount) }}</strong></span>
        </div>

        <div v-if="!turnedAvailable" class="note-tip">
          该来源单明细没有"累计已下推"回写列，上表"已下推"取的是本单下推数量；如需合同行级累计，需另行补回写字段。
        </div>
        <div v-if="currentSource.matchedByMaterial" class="note-tip">
          来源明细与本单明细未建立行级关联（该链路下推时只记了表头来源），上表的"本单下推数量/单价"按物料编码匹配统计。
        </div>
      </template>
    </template>

    <!-- ============ 去向单据 ============ -->
    <template v-else>
      <div v-if="loading" class="state-tip">{{ $tt("加载中…") }}</div>
      <div v-else-if="error" class="state-tip error">{{ error }}</div>
      <div v-else-if="targetRows.length === 0" class="state-tip">{{ $tt("暂无下游单据") }}</div>
      <template v-else>
        <el-tabs v-model="activeTargetTab" class="sub-tabs">
          <el-tab-pane :label="$tf('全部')" name="__all__" />
          <el-tab-pane
            v-for="g in targetGroups"
            :key="g.docType"
            :label="g.docName + ' (' + g.rows.length + ')'"
            :name="g.docType"
          />
        </el-tabs>

        <el-table :data="visibleTargets" size="small" border class="compact-table">
          <!-- 展开行：该下游单据的物料级明细（与来源单据信息量对齐） -->
          <el-table-column v-if="hasAnyTargetItems" type="expand" width="46">
            <template #default="{ row }">
              <div class="expand-detail">
                <el-table v-if="row.items && row.items.length" :data="row.items" size="mini" border class="compact-table">
                  <el-table-column prop="lineNo" :label="$tf('行号')" width="60" />
                  <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="120" />
                  <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="150" show-overflow-tooltip />
                  <el-table-column prop="spec" :label="$tf('规格')" min-width="120" show-overflow-tooltip />
                  <el-table-column :label="$tf('数量')" width="100" align="right">
                    <template #default="{ row: it }">{{ fmtQty(it.qty) }}</template>
                  </el-table-column>
                  <el-table-column :label="$tf('单价')" width="100" align="right">
                    <template #default="{ row: it }">
                      <span v-if="it.price !== null && it.price !== undefined">¥{{ fmtAmt(it.price) }}</span>
                      <span v-else class="muted">—</span>
                    </template>
                  </el-table-column>
                  <el-table-column :label="$tf('金额')" width="120" align="right">
                    <template #default="{ row: it }">
                      <span v-if="it.amount !== null && it.amount !== undefined">¥{{ fmtAmt(it.amount) }}</span>
                      <span v-else class="muted">—</span>
                    </template>
                  </el-table-column>
                  <!-- 对应上级单据哪一行 + 上级该行还剩多少（未下推） -->
                  <el-table-column :label="$tf('对应上级行')" width="110" align="center">
                    <template #default="{ row: it }">
                      <span v-if="it.srcLineNo !== null && it.srcLineNo !== undefined">第 {{ it.srcLineNo }} 行</span>
                      <span v-else class="muted">—</span>
                    </template>
                  </el-table-column>
                  <el-table-column :label="$tf('上级物料')" min-width="110" show-overflow-tooltip>
                    <template #default="{ row: it }">
                      <span v-if="it.srcMaterialCode">{{ it.srcMaterialCode }}</span>
                      <span v-else class="muted">—</span>
                    </template>
                  </el-table-column>
                  <el-table-column :label="$tf('上级剩余')" width="110" align="right">
                    <template #default="{ row: it }">
                      <span v-if="it.srcRemainQty !== null && it.srcRemainQty !== undefined"
                            :class="Number(it.srcRemainQty) > 0 ? 'remain' : ''">{{ fmtQty(it.srcRemainQty) }}</span>
                      <span v-else class="muted">—</span>
                    </template>
                  </el-table-column>
                </el-table>
                <div v-else class="state-tip">{{ $tt("该单据无物料明细") }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column :label="$tf('层级')" width="76" align="center">
            <template #default="{ row }">
              <el-tag v-if="Number(row.level) > 1" size="mini" type="warning" effect="plain">第{{ row.level }}级</el-tag>
              <span v-else class="muted">{{ $tt("直接") }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('单据类型')" width="180">
            <template #default="{ row }">
              <span>{{ row.docName }}</span>
              <!-- 2026-11-18：由销售换货单生成的出库单，明确标记为替代发货，避免与普通下推出库单混淆 -->
              <el-tag v-if="row.exchangeGenerated" size="mini" type="warning" effect="plain" style="margin-left:6px">{{ $tt('替代发货') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('单号')" min-width="180">
            <template #default="{ row }">
              <el-button
                v-if="canJump(row.docType) && row.id"
                v-permission="perm('view')"
                type="primary"
                link
                size="small"
                @click="goTo(row.docType, row.id)"
              >{{ row.no }}</el-button>
              <span v-else>{{ row.no }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('上级单据')" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.parentNo">{{ row.parentDocName }}：{{ row.parentNo }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="date" :label="$tf('日期')" width="115" />
          <el-table-column prop="status" :label="$tf('状态')" width="110">
            <template #default="{ row }">{{ $dt(row.status) }}</template>
          </el-table-column>
          <el-table-column v-if="hasAnyTargetItems" :label="$tf('数量合计')" width="110" align="right">
            <template #default="{ row }">
              <span class="hl">{{ fmtQty(row.qtyTotal) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$tf('金额')" width="130" align="right">
            <template #default="{ row }">
              <span v-if="row.amount !== null && row.amount !== undefined">¥{{ fmtAmt(row.amount) }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </template>
  </div>
</template>

<script setup>
/**
 * 来源单据 / 去向单据 页签内容（通用组件，配置驱动）
 *
 * 用法（放在单据编辑页的 el-tabs 页签里）：
 *   <BillLinkPanel bill-type="SALE_ORDER" :bill-id="form.orderId" mode="source" />
 *   <BillLinkPanel bill-type="SALE_ORDER" :bill-id="form.orderId" mode="target" />
 *
 * 后端：GET /api/bill-link/sources|targets（注册表见 BillLinkRegistry，未登记的单据类型返回空）
 * 跳转：复用各单据现有编辑路由；无可跳转页面的单据（如应收单）单号渲染为纯文本。
 */
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Tickets } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getBillSources, getBillTargets } from '@/api/billLink'
import { canJumpBill, resolveBillRoute, BILL_EDIT_ROUTE } from '@/utils/billRoute'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { useUserStore } from '@/stores/user'
import { isRouteVisible } from '@/utils/versionGate'

const perm = usePagePerm()
const userStore = useUserStore()


const props = defineProps({
  /** 当前单据类型编码（与后端注册表一致，如 SALE_ORDER） */
  billType: { type: String, required: true },
  /** 当前单据主键；为空表示未保存 */
  billId: { type: [Number, String], default: null },
  /** source=来源单据 target=去向单据 */
  mode: { type: String, default: 'source' }
})

const router = useRouter()

const loading = ref(false)
const error = ref('')
const sourceDocs = ref([])
const targetRows = ref([])
const activeSourceTab = ref('')
const activeTargetTab = ref('__all__')

/** 单据类型 → 编辑路由：全站唯一一份，见 utils/billRoute.js（列表页单号跳转共用同一份） */
function canJump (docType) {
  if (!canJumpBill(docType)) return false
  // 版本隔离（2026-10-10）：目标单据所属模块不在当前账号可见菜单里 → 单号降级为纯文本不可点
  const build = BILL_EDIT_ROUTE[docType]
  const sampleRoute = typeof build === 'function' ? build(1) : null
  return isRouteVisible(sampleRoute, userStore.menuRoutePaths || [])
}

function goTo (docType, id) {
  const path = resolveBillRoute(docType, id)
  // 单据已删除/无独立编辑页时给明确提示（2026-09-28：不再"点了没反应"）
  if (!path) { ElMessage.warning(tt('该单据已删除或无法打开')); return }
  try {
    router.push(path)
  } catch (e) {
    // 目标单据不存在/无权限时由路由与目标页面自行处理
  }
}

const currentSource = computed(() => {
  if (sourceDocs.value.length === 0) return {}
  const hit = sourceDocs.value.find(d => (d.docType + '-' + d.id) === activeSourceTab.value)
  return hit || sourceDocs.value[0]
})

const turnedAvailable = computed(() => {
  const items = (currentSource.value && currentSource.value.items) || []
  return items.some(i => i.turnedColAvailable)
})

const targetGroups = computed(() => {
  const map = new Map()
  targetRows.value.forEach(r => {
    if (!map.has(r.docType)) map.set(r.docType, { docType: r.docType, docName: r.docName, rows: [] })
    map.get(r.docType).rows.push(r)
  })
  return [...map.values()]
})

const visibleTargets = computed(() => {
  if (activeTargetTab.value === '__all__') return targetRows.value
  return targetRows.value.filter(r => r.docType === activeTargetTab.value)
})

/** 当前筛选中是否有带物料明细的去向单据（决定是否显示展开列与数量合计列） */
const hasAnyTargetItems = computed(() =>
  visibleTargets.value.some(r => Array.isArray(r.items) && r.items.length > 0)
)

function fmtAmt (v) {
  return Number(v || 0).toFixed(2)
}

function fmtQty (v) {
  const n = Number(v || 0)
  if (!Number.isFinite(n)) return '0'
  // 去尾零（最多 4 位小数）
  return String(Number(n.toFixed(4)))
}

async function load () {
  error.value = ''
  sourceDocs.value = []
  targetRows.value = []
  activeTargetTab.value = '__all__'
  if (!props.billId) return
  loading.value = true
  try {
    if (props.mode === 'source') {
      const res = await getBillSources(props.billType, props.billId)
      const list = (res && res.list) || []
      sourceDocs.value = list
      activeSourceTab.value = list.length > 0 ? (list[0].docType + '-' + list[0].id) : ''
    } else {
      const res = await getBillTargets(props.billType, props.billId)
      targetRows.value = (res && res.list) || []
    }
  } catch (e) {
    error.value = '加载失败：' + (e && e.message ? e.message : tt('未知错误'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => [props.billType, props.billId, props.mode], () => { load() })
</script>

<style scoped lang="scss">
.bill-link-panel {
  font-size: 12px;

  .state-tip {
    font-size: 12px;
    color: #909399;
    padding: 12px 4px;

    &.error { color: #f56c6c; }
  }

  .sub-tabs {
    :deep(.el-tabs__header) { margin-bottom: 8px; }
    :deep(.el-tabs__item) { font-size: 12px; height: 32px; line-height: 32px; }
  }

  .src-head {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 6px 4px 10px;
    color: #606266;

    .src-no {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      color: #303133;

      strong { color: #409eff; }
    }

    .src-meta { color: #909399; }
  }

  .compact-table {
    font-size: 12px;

    :deep(.el-table__header th) {
      background: #f5f7fa !important;
      color: #606266;
      font-weight: 600;
      font-size: 12px;
      padding: 6px 4px;
    }
    :deep(.el-table__body td) { padding: 6px 4px; }
  }

  .sum-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 24px;
    padding: 8px 4px 2px;
    color: #606266;

    strong { color: #303133; }
    .hl { color: #409eff; }
    .remain { color: #e6a23c; }
  }

  .hl { color: #409eff; font-weight: 600; }
  .remain { color: #e6a23c; font-weight: 600; }
  .muted { color: #c0c4cc; }

  /* 去向单据展开行内的明细表 */
  .expand-detail {
    padding: 6px 12px;
    background: #fafbfc;
  }

  .note-tip {
    margin-top: 6px;
    padding: 6px 10px;
    background: #fdf6ec;
    border: 1px solid #faecd8;
    border-radius: 4px;
    color: #b88230;
    font-size: 12px;
  }
}
</style>
