<template>
  <div class="help-page">
    <el-card shadow="never" class="help-header">
      <div class="help-title">
        <el-icon :size="32" color="#E6A23C"><Tools /></el-icon>
        <div>
          <h1>{{ $tt("委外管理模块操作手册") }}</h1>
          <p>委外管理覆盖委外加工单、委外发料、委外补料、委外退料与委外入库五类单据，实现将原料/半成品委托外部供应商加工、再回收完成件的全流程管理，与采购、库存、财务成本模块协同</p>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px;">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ============ 快速入门 ============ -->
        <el-tab-pane :label="$tf('快速入门')" name="quickstart">
          <div class="help-section">
            <h3>{{ $tt("一、模块概览") }}</h3>
            <p>{{ $tt("委外管理包含 委外加工单（委外核心单据）及 委外发料/补料/退料/入库 四个配套执行单据。点击下方任一功能卡片可直达对应说明位置。") }}</p>
            <div class="feature-grid">
              <div v-for="f in features" :key="f.label" class="feature-card" @click="goToDoc(f)" @keyup.enter="goToDoc(f)">
                <div class="feature-icon">{{ f.icon }}</div>
                <h4>{{ $tf(f.label) }}</h4>
                <p>{{ f.desc }}</p>
                <div class="feature-go">查看手册 -></div>
              </div>
            </div>

            <h3>{{ $tt("二、推荐业务流转") }}</h3>
            <div class="flow-diagram">
              <div class="flow-step"><div class="step-circle">1</div><div class="step-text">{{ $tt("生产工单 下推 委外加工单") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">2</div><div class="step-text">{{ $tt("加工单 下推 委外发料单 / 入库单") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">3</div><div class="step-text">{{ $tt("发料单 下推 补料单 / 退料单") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">4</div><div class="step-text">{{ $tt("委外入库（回收完成件，含加工费）") }}</div></div>
            </div>
            <el-alert type="info" :closable="false" show-icon style="margin-top:14px;"
              :title="$tt('单据链路：生产工单（已下达/执行中）→ 委外加工单；委外加工单（已下达）→ 委外发料单 & 委外入库单；委外发料单（已生效）→ 委外补料单 & 委外退料单。上下游单据可通过【下推】自动带入表头与明细。')" />

            <h3>{{ $tt("三、单据状态体系说明") }}</h3>
            <el-alert type="warning" :closable="false" show-icon style="margin-bottom:14px;"
              :title="$tt('注意：委外模块状态为数字态体系且各单据不一致——委外加工单为 8 态（草稿0/待审核1/已生效2/已下达3/执行中4/已完工5/已结案6/已作废7）；发料/补料/退料/入库单为 3 态（草稿0/待审核1/已生效2），审核生效即完成库存扣减或退回。详见各单据说明。')" />

            <h3>{{ $tt("四、通用操作及状态说明") }}</h3>
            <div class="op-table">
              <el-table :data="commonOps" border size="default">
                <el-table-column prop="op" :label="$tf('操作 / 状态')" width="200" />
                <el-table-column prop="desc" :label="$tf('说明')" />
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ 委外单据 Tab ============ -->
        <el-tab-pane :label="$tf('委外单据')" name="subcontract-docs">
          <div v-for="(doc, idx) in groups[0].docs" :key="doc.key" :id="'doc-' + doc.key" class="doc-block">
            <h2 class="doc-title">{{ idx + 1 }}、{{ doc.title }}</h2>
            <p class="doc-intro">{{ doc.intro }}</p>

            <h4 class="sub-title">{{ $tt("页面总览（列表页）") }}</h4>
            <div class="shot-wrap">
              <el-image :src="'/manual/subcontract/' + doc.key + '.png'"
                        :preview-src-list="['/manual/subcontract/' + doc.key + '.png']"
                        fit="contain" hide-on-click-modal class="shot-img" />
              <div class="shot-tip">{{ $tt("点击图片可放大查看") }}</div>
            </div>

            <template v-if="doc.editShot">
              <h4 class="sub-title">{{ $tt("新增 / 编辑界面") }}</h4>
              <div class="shot-wrap">
                <el-image :src="'/manual/subcontract/' + doc.key + '-edit.png'"
                          :preview-src-list="['/manual/subcontract/' + doc.key + '-edit.png']"
                          fit="contain" hide-on-click-modal class="shot-img" />
                <div class="shot-tip">{{ doc.editShot }}</div>
              </div>
            </template>

            <h4 class="sub-title">{{ $tt("核心字段说明") }}</h4>
            <el-table :data="doc.fields" border size="small">
              <el-table-column prop="name" :label="$tf('字段')" width="170" />
              <el-table-column prop="desc" :label="$tf('说明')" />
            </el-table>

            <h4 class="sub-title">{{ $tt("操作步骤") }}</h4>
            <ol class="steps">
              <li v-for="s in doc.steps" :key="s">{{ s }}</li>
            </ol>

            <h4 class="sub-title">{{ $tt("单据状态流转") }}</h4>
            <ol class="steps">
              <li v-for="s in doc.status" :key="s">{{ s }}</li>
            </ol>

            <h4 v-if="doc.push" class="sub-title">{{ $tt("单据下推") }}</h4>
            <el-table v-if="doc.push" :data="doc.push" border size="small" style="margin-bottom:10px;">
              <el-table-column prop="from" :label="$tf('来源单据')" width="180" />
              <el-table-column prop="arrow" label="" width="40" align="center" />
              <el-table-column prop="to" :label="$tf('目标单据')" width="180" />
              <el-table-column prop="desc" :label="$tf('说明')" />
            </el-table>

            <el-alert v-if="doc.note" :title="doc.note" type="warning" :closable="false" show-icon style="margin:12px 0 24px;" />
            <el-alert v-else-if="doc.goodNote" :title="doc.goodNote" type="success" :closable="false" show-icon style="margin:12px 0 24px;" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { Tools } from '@element-plus/icons-vue'

const activeTab = ref('quickstart')

// 点击功能卡片 -> 切换到对应Tab并平滑滚动到该档案的说明位置
async function goToDoc(f) {
  if (!f.group || !f.key) return
  activeTab.value = f.group
  await nextTick()
  await nextTick()
  const el = document.getElementById('doc-' + f.key)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    el.classList.add('doc-highlight')
    setTimeout(() => el.classList.remove('doc-highlight'), 2200)
  }
}

const features = [
  { icon: '📦', label: '委外加工单', desc: '委托外部供应商加工的核心单据', group: 'subcontract-docs', key: 'order' },
  { icon: '📤', label: '委外发料单', desc: '向委外供应商发出的物料/原料', group: 'subcontract-docs', key: 'pick' },
  { icon: '➕', label: '委外补料单', desc: '发货后因损耗/报废补充发料', group: 'subcontract-docs', key: 'supplement' },
  { icon: '↩️', label: '委外退料单', desc: '退回剩余/多余物料至我司仓库', group: 'subcontract-docs', key: 'return' },
  { icon: '📥', label: '委外入库单', desc: '回收供应商加工完成的完成件（含加工费）', group: 'subcontract-docs', key: 'inbound' }
]

const commonOps = [
  { op: '统计卡片', desc: '各单据列表顶部 4 张统计卡反映执行概览（如 本月委外/草稿/已下达/执行中），目前为演示数据' },
  { op: '状态筛选', desc: '顶部 Tabs 按 全部/草稿/待审核/已生效 快速过滤' },
  { op: '查询 / 重置', desc: '搜索栏支持按 单号/委外单号/供应商 关键词过滤；【重置】还原筛选条件' },
  { op: '列设置', desc: '列表右上角可打开【列设置】抽屉，动态显隐展示列' },
  { op: '行内操作', desc: '表格操作列提供 查看/编辑/提交/审核/驳回/结案/作废 等，按当前状态自动显隐；部分按钮仅在进入编辑页后可用' },
  { op: '单据状态', desc: '委外加工单为 8 态数字体系；发料/补料/退料单为 4 态；委外入库单为 3 态——各单据请以对应说明为准' },
  { op: '来源下推', desc: '委外各单据可由上游单据【下推】生成（生产工单→加工单→发料/入库→补料/退料），自动带入表头与明细，减少手工录入' }
]

const groups = [
  {
    name: 'subcontract-docs', label: '委外单据', docs: [
      {
        key: 'order', title: '委外加工单', editShot: '新增/编辑委外加工单表单（点击放大）',
        intro: '委外业务的核心单据（单据类型 SUBCONTRACT_ORDER），委托外部供应商（外协厂）对指定物料进行加工，记录物料、数量、供应商、工序与加工价格，并携带委外发料清单。',
        fields: [
          { name: '委外单号 / 委外日期', desc: '唯一单号（billNo，自动生成）及委外业务日期（必填）' },
          { name: '物料 / 单位 / 数量', desc: '被委外加工的物料（materialId/materialCode/materialName）、计量单位与委外数量 qty' },
          { name: '供应商', desc: '外协加工供应商（supplierId/supplierName），需合作的外协厂' },
          { name: '工序 / 计划时间', desc: 'processNo 加工工序；planStartDate/planFinishDate 计划开始/完成时间' },
          { name: '单价 / 金额', desc: 'unitPrice 加工单价、totalPrice 金额（明细数量×单价汇总）' },
          { name: '委外发料清单', desc: '明细区含 物料、来源单号 sourceBillNo、基准数量 baseQty、实际数量 actualQty、供料方式 supplyType（我方供料/客供料/不供料）' },
          { name: '状态', desc: '草稿0/待审核1/已生效2/已下达3/执行中4/已完工5/已结案6/已作废7' }
        ],
        steps: [
          '列表页点击【+ 新增】新建委外加工单；或从 生产工单（已下达/执行中）下推自动带入物料、数量与计划时间',
          '填写委外日期，选择 物料、供应商、工序，录入 单价 并填发料清单（物料、供料方式、实际数量）',
          '【保存】生成委外单号；草稿状态点击【提交】进入审批流',
          '【审核】通过后进入 已待下达；随后【下达】进入 已下达，开始执行进入 执行中',
          '加工完成后由委外入库单回收完成件并记录加工费；全部入库后完工、结案归档'
        ],
        status: [
          '草稿(0) → (提交) → 待审核(1) → (审核) → 已生效(2) → (下达) → 已下达(3) → (开始执行) → 执行中(4) → (完工) → 已完工(5)',
          '已完工后仍可 (结案) → 已结案(6) 归档；任意未结案状态可 (作废) → 已作废(7)',
          '可 (驳回) 回到草稿 / (重置草稿) / (反结案) 撤销，修改后重新流转'
        ],
        push: [
          { from: '生产工单', arrow: '→', to: '委外加工单', desc: '自动带入物料、数量、计划时间' },
          { from: '委外加工单', arrow: '→', to: '委外发料单', desc: '源需已生效，代出委外发料明细' },
          { from: '委外加工单', arrow: '→', to: '委外入库单', desc: '源需已生效，回收完成件时带入加工费' }
        ],
        note: '标注：委外加工单列表行内审核/下推按钮有显示但当前版本需进入编辑页操作；且提交/审核会复用“下达”接口、开始执行复用“完工”接口（后端暂无独立接口），状态推进与其按钮名不完全对应，请以最终列表状态为准。'
      },
      {
        key: 'pick', title: '委外发料单',
        intro: '将委外加工所需的原料/物料发往外协供应商的单据（单据类型 SUBCONTRACT_DELIVERY / 发料单号前缀 Pick），由委外加工单下推生成，审核通过后记录供货出库。',
        fields: [
          { name: '领料单号 / 领料日期', desc: '唯一单号 pickNo 及发料业务日期' },
          { name: '委外加工单', desc: 'orderId/orderNo 来源委外加工单（下拉仅列已下达状态及以后的加工单）' },
          { name: '供应商', desc: '由来源加工单带入（supplierId/supplierName）' },
          { name: '总数量', desc: '发料明细数量汇总 totalQuantity' },
          { name: '明细', desc: '物料、来源单号 sourceBillNo、单位、数量、仓库 warehouseId、备注' },
          { name: '状态', desc: '草稿0/待审核1/已生效2' }
        ],
        steps: [
          '在委外加工单（已下达）上点击【下推】选择 委外发料单，或新建时通过来源选择器带入加工单',
          '确认发料物料、数量、仓库与发货日期，可调整实际发料数量',
          '【保存】生成发料单号；【提交】进入审批；【审核】通过后单据生效并扣减库存',
          '发料单可继续下推生成 委外补料单 / 委外退料单'
        ],
        status: [
          '草稿(0) → (提交) → 待审核(1) → (审核) → 已生效(2)',
          '审核生效即扣减发料仓库库存，并冻结本次发料成本作为委外成品成本的材料部分；重置草稿按冻结成本退回'
        ],
        push: [
          { from: '委外加工单', arrow: '→', to: '委外发料单', desc: '源需已生效，自动带入加工单表头与委外发料明细' },
          { from: '委外发料单', arrow: '→', to: '委外补料单', desc: '补充因损耗/报废的再次发料' },
          { from: '委外发料单', arrow: '→', to: '委外退料单', desc: '退回剩余物料时使用' }
        ],
        goodNote: '说明：委外发料单按知识库规范应由委外加工单下推生成，不建议手工新增。发料单本身已是下游单据，无进一步入库/补料外的下推。'
      },
      {
        key: 'supplement', title: '委外补料单',
        intro: '委外加工过程中因损耗、报废需要向供应商再次补充发料的单据（单据类型 SUBCONTRACT_SUPPLEMENT），由委外发料单下推生成，需填写补料原因。',
        fields: [
          { name: '补料单号 / 补料日期', desc: '唯一单号 supplementNo 及补料业务日期' },
          { name: '委外加工单 / 发料单', desc: 'orderId/orderNo 委外加工单；pickId/pickNo 上级发料单' },
          { name: '供应商', desc: '由来源单带入（supplierId/supplierName）' },
          { name: '补料原因', desc: 'reason 必填，说明补充发料的原因（损耗/报废等）' },
          { name: '明细', desc: '物料、来源单号 sourceBillNo、单位、数量、仓库、备注' },
          { name: '状态', desc: '草稿0/待审核1/已生效2' }
        ],
        steps: [
          '在委外发料单（已生效）上【下推】选择 委外补料单，或新建时通过来源选择器带入发料单',
          '确认补料的 数量、仓库 与补料日期，并填写必填的【补料原因】',
          '【保存】生成补料单号；【提交】进入审批；【审核】通过后生效并扣减库存',
          '补料同样计入委外成品的材料成本'
        ],
        status: [
          '草稿(0) → (提交) → 待审核(1) → (审核) → 已生效(2)',
          '审核生效即扣减库存并冻结成本；重置草稿按冻结成本退回'
        ],
        push: [
          { from: '委外发料单', arrow: '→', to: '委外补料单', desc: '补充因损耗/报废的再次发料，需填补料原因' }
        ]
      },
      {
        key: 'return', title: '委外退料单', editShot: '新增/编辑委外退料单表单（点击放大）',
        intro: '委外加工完成后将剩余或多余的物料退回我司仓库的单据（单据类型 SUBCONTRACT_RETURN），由委外发料单下推生成，审核通过后记录退回入库。',
        fields: [
          { name: '退料单号 / 退料日期', desc: '唯一单号 returnNo 及退料业务日期' },
          { name: '委外加工单 / 发料单', desc: 'orderId/orderNo 委外加工单；pickId/pickNo 上级发料单' },
          { name: '供应商', desc: '由来源单带入（supplierId/supplierName）' },
          { name: '总数量', desc: '退回物料数量汇总 totalQuantity' },
          { name: '明细', desc: '物料、来源单号 sourceBillNo、单位、数量、仓库、备注' },
          { name: '状态', desc: '草稿0/待审核1/已生效2' }
        ],
        steps: [
          '在委外发料单（已生效）上【下推】选择 委外退料单，或新建时通过来源选择器带入发料单',
          '选择要退回的物料与数量，确认退回仓库（我司仓库）与退料日期',
          '【保存】生成退料单号；【提交】进入审批；【审核】通过后生效并增加库存',
          '退回成本取自同一加工单行最近一次发料的实际成本'
        ],
        status: [
          '草稿(0) → (提交) → 待审核(1) → (审核) → 已生效(2)',
          '审核生效即按发料成本加回库存；重置草稿按原成本红字冲回'
        ],
        push: [
          { from: '委外发料单', arrow: '→', to: '委外退料单', desc: '退回剩余物料，审核生效即加回库存' }
        ]
      },
      {
        key: 'inbound', title: '委外入库单', editShot: '新增/编辑委外入库单表单（点击放大）',
        intro: '回收供应商加工完成的完成件并登记入库的单据（单据类型 SUBCONTRACT_INBOUND / SUBCONTRACT_RECEIPT），由委外加工单下推生成，记录入库数量与委外加工费，是委外成本归集入口。',
        fields: [
          { name: '入库单号 / 入库日期', desc: '唯一单号 inboundNo 及入库业务日期' },
          { name: '委外加工单', desc: 'orderId/orderNo 来源委外加工单（下拉仅列已下达状态及以后的加工单）' },
          { name: '供应商', desc: '由来源加工单带入（supplierId/supplierName）' },
          { name: '总数量', desc: '入库数量汇总 totalQuantity' },
          { name: '加工费', desc: 'processingFee 委外特有的加工费用，作为委外成本的一部分归集' },
          { name: '明细', desc: '物料、来源单号、单位、数量、仓库、加工费列、备注' },
          { name: '状态', desc: '草稿0/待审核1/已生效2' }
        ],
        steps: [
          '在委外加工单（已生效）上【下推】选择 委外入库单，或新建时通过来源选择器带入加工单',
          '确认入库 完成件、数量、仓库，并将委外交付产生的【加工费】登记到明细',
          '【保存】生成入库单号；【提交】进入审批；【审核】通过后单据生效并办理库存入库',
          '委外完成件入库后，可结合发料/补料/退料与加工费归集委外成本'
        ],
        status: [
          '草稿(0) → (提交) → 待审核(1) → (审核) → 已生效(2)',
          '委外入库单为 3 态，审核通过即完成入库，无出库/入库的进一步终态'
        ],
        push: [
          { from: '委外加工单', arrow: '→', to: '委外入库单', desc: '源需已生效，回收完成件并登记加工费' }
        ],
        note: '标注：委外入库单编辑页打印弹窗使用 SUBCONTRACT_INBOUND、而按钮组与提交使用 SUBCONTRACT_RECEIPT，单据类型引用不一致，可能影响打印/审批开关，属已知缺陷。'
      }
    ]
  }
]
</script>

<style scoped>
.help-page { padding: 4px; }
.help-header { border-radius: 8px; }
.help-title { display: flex; align-items: center; gap: 16px; }
.help-title h1 { margin: 0; font-size: 22px; }
.help-title p { margin: 6px 0 0; color: #909399; font-size: 13px; }
.el-tabs__item { font-weight: 600; }
.doc-block { margin-bottom: 28px; }
.doc-title { font-size: 18px; margin: 16px 0 8px; }
.doc-intro { color: #606266; margin: 0 0 14px; line-height: 1.7; }
.sub-title { font-size: 14px; color: #303133; margin: 16px 0 8px; }
.shot-wrap { text-align: center; margin: 8px 0 16px; }
.shot-img { max-width: 100%; max-height: 560px; border: 1px solid #eee; border-radius: 6px; }
.shot-tip { color: #bbb; font-size: 12px; margin-top: 6px; }
.steps li { line-height: 1.9; color: #303133; }
.feature-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; margin: 12px 0 20px; }
.feature-card { border: 1px solid #ebeef5; border-radius: 8px; padding: 16px; cursor: pointer; transition: all .2s; background: #fff; }
.feature-card:hover { border-color: #E6A23C; box-shadow: 0 4px 12px rgba(230,162,60,.15); transform: translateY(-2px); }
.feature-icon { font-size: 28px; margin-bottom: 8px; }
.feature-card h4 { margin: 0 0 6px; font-size: 15px; }
.feature-card p { margin: 0 0 10px; color: #909399; font-size: 12px; line-height: 1.6; }
.feature-go { color: #E6A23C; font-size: 13px; font-weight: 600; }
.flow-diagram { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; background: #f8f8f8; padding: 14px; border-radius: 8px; font-size: 13px; }
.flow-step { display: flex; align-items: center; gap: 8px; }
.step-circle { width: 24px; height: 24px; border-radius: 50%; background: #E6A23C; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-weight: 700; flex-shrink: 0; }
.step-text { white-space: nowrap; }
.flow-arrow { color: #909399; font-weight: 700; }
.op-table { margin-top: 8px; }
.doc-highlight { background: #fdf6ec; border-radius: 6px; padding: 8px; }
</style>
