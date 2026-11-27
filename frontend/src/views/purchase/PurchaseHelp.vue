<template>
  <div class="help-page">
    <el-card shadow="never" class="help-header">
      <div class="help-title">
        <el-icon :size="32" color="#409EFF"><Goods /></el-icon>
        <div>
          <h1>{{ $tt("采购管理模块操作手册") }}</h1>
          <p>{{ $tt("采购管理覆盖从采购申请、采购合同、采购订单到采购入库、采购退货、采购开票的全流程单据管理，配合采购分析报表与采购发票报表，实现采购业务的闭环跟踪与数据分析") }}</p>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px;">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ============ 快速入门 ============ -->
        <el-tab-pane :label="$tf('快速入门')" name="quickstart">
          <div class="help-section">
            <h3>{{ $tt("一、模块概览") }}</h3>
            <p>{{ $tt("采购管理模块共包含 6 张业务单据（申请单、合同、订单、入库单、退货单、发票）及采购分析报表、采购发票报表。点击下方任一功能卡片可直达对应说明位置。") }}</p>
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
              <div class="flow-step"><div class="step-circle">1</div><div class="step-text">{{ $tt("提交采购申请") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">2</div><div class="step-text">{{ $tt("签订采购合同 / 生成采购订单") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">3</div><div class="step-text">{{ $tt("采购收货入库") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">4</div><div class="step-text">{{ $tt("采购退货 / 采购开票") }}</div></div>
            </div>
            <el-alert type="info" :closable="false" show-icon style="margin-top:14px;"
              :title="$tt('多数单据支持【下推】生成下游单据：采购申请可下推合同 / 订单，合同可下推订单，订单可下推入库 / 发票，入库单可下推退货 / 发票，退货单可下推红字发票。详见各单据说明中的“单据下推”。')" />

            <h3>{{ $tt("三、单据通用流程（所有采购单据一致）") }}</h3>
            <div class="op-table">
              <el-table :data="commonFlow" border size="default">
                <el-table-column prop="op" :label="$tf('环节')" width="150" />
                <el-table-column prop="desc" :label="$tf('说明')" />
              </el-table>
            </div>

            <h3>{{ $tt("四、通用操作及状态说明") }}</h3>
            <div class="op-table">
              <el-table :data="commonOps" border size="default">
                <el-table-column prop="op" :label="$tf('操作 / 状态')" width="170" />
                <el-table-column prop="desc" :label="$tf('说明')" />
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ 采购单据 ============ -->
        <el-tab-pane v-for="group in groups" :key="group.name" :label="$tf(group.label)" :name="group.name">
          <div v-for="(doc, idx) in group.docs" :key="doc.key" :id="'doc-' + doc.key" class="doc-block">
            <h2 class="doc-title">{{ idx + 1 }}、{{ doc.title }}</h2>
            <p class="doc-intro">{{ doc.intro }}</p>

            <h4 class="sub-title">{{ $tt("页面总览（列表页）") }}</h4>
            <div class="shot-wrap">
              <el-image :src="'/manual/purchase/' + doc.key + '.png'"
                        :preview-src-list="['/manual/purchase/' + doc.key + '.png']"
                        fit="contain" hide-on-click-modal class="shot-img" />
              <div class="shot-tip">{{ $tt("点击图片可放大查看") }}</div>
            </div>

            <template v-if="doc.editShot">
              <h4 class="sub-title">{{ $tt("新增 / 编辑界面") }}</h4>
              <div class="shot-wrap">
                <el-image :src="'/manual/purchase/' + doc.key + '-edit.png'"
                          :preview-src-list="['/manual/purchase/' + doc.key + '-edit.png']"
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
import { Goods } from '@element-plus/icons-vue'

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
  { icon: '📨', label: '采购申请单', desc: '内部申购需求与下推合同/订单', group: 'purchase-bills', key: 'request' },
  { icon: '🤝', label: '采购合同', desc: '合同签订（含框架/年度）与下推订单', group: 'purchase-bills', key: 'contract' },
  { icon: '📋', label: '采购订单', desc: '订单录入、执行跟踪与下推', group: 'purchase-bills', key: 'order' },
  { icon: '📥', label: '采购入库单', desc: '收货入库、通过审核扣/增库存', group: 'purchase-bills', key: 'inbound' },
  { icon: '↩️', label: '采购退货单', desc: '退货登记与退货出库', group: 'purchase-bills', key: 'return' },
  { icon: '🧾', label: '采购发票', desc: '进项发票登记与多来源汇聚', group: 'purchase-bills', key: 'invoice' },
  { icon: '📊', label: '采购分析报表', desc: '价格走势、供应商分析与执行统计', group: 'monitor', key: 'report' },
  { icon: '📈', label: '采购发票报表', desc: '供应商发票、付款状态与税务分析', group: 'monitor', key: 'invoice-report' }
]

const commonFlow = [
  { op: '创建单据', desc: '在各单据列表页点击【+ 新增】进入编辑页，填写单据信息与商品明细后保存；也可通过上游单据【下推】自动带入表头与明细' },
  { op: '提交审核', desc: '草稿状态下点击【提交】，提示“提交后将进入审批流程，审批通过后生效”；若未开启审批，提交后单据直接生效' },
  { op: '审核 / 驳回', desc: '待审核状态下【审核】通过后单据生效并可下推；【驳回】需填写原因，单据回到草稿/已驳回状态可修改后重新提交' },
  { op: '结案 / 作废', desc: '已生效单据可【结案】归档（结案后不可修改、不可下推）；草稿/待审核/已生效单据均可【作废】标记无效' }
]

const commonOps = [
  { op: '统计卡片', desc: '采购订单列表顶部的 4 张统计卡为“本月采购 / 待入库 / 已入库 / 采购总额”；其余单据为概览型 4 卡统计' },
  { op: '状态筛选', desc: '顶部 Tabs 支持按 全部/草稿/待审核/已生效/已关闭（采购订单另有 已确认/已入库/已完成）快速过滤' },
  { op: '查询 / 重置', desc: '搜索栏支持按关键词 + 日期范围过滤；【重置】还原筛选条件' },
  { op: '列设置', desc: '列表右上角可打开【列设置】抽屉，动态显隐展示列' },
  { op: '行内操作', desc: '表格操作列提供 查看/编辑/提交/审核/驳回/重置草稿/结案/反结案/作废/反作废/删除，按当前状态自动显隐' },
  { op: '单据状态', desc: '草稿/已驳回/已取消（可编辑），待审核（待审批），已生效（生效），已结案/已关闭（归档），已作废（无效标记）' },
  { op: '复制 / 打印', desc: '订单/入库/退货/发票编辑页支持单据【复制】生成新单及【打印预览】导出单据（采购合同打印功能开发中）' }
]

const groups = [
  {
    name: 'purchase-bills', label: '采购单据', docs: [
      {
        key: 'request', title: '采购申请单',
        intro: '内部申购需求的单据（单号前缀 PR-），记录申请数量与需求日期，审核通过后可下推生成采购合同或采购订单。',
        fields: [
          { name: '申请单号 / 申请日期', desc: '唯一单号（前缀 PR，保存后自动生成）及申请业务日期' },
          { name: '需求日期', desc: '期望物料到位的时间；明细行也可单独设置需求日期' },
          { name: '申请类型 / 是否紧急', desc: '申请类型（普通/紧急/生产/研发/设备采购）及紧急标记' },
          { name: '申请 / 审批数量', desc: '明细行记录 申请数量 与 审批数量；整单汇总申请总数量/审批总数量' },
          { name: '建议供应商', desc: '明细行可指定 suggestedSupplierId 建议供应商，为后续下推打基础' },
          { name: '部门 / 申请人 / 付款 / 结算', desc: '申请部门、申请人、付款条件、结算方式等业务信息' }
        ],
        steps: [
          '在采购申请单列表点击【+ 新增】进入编辑页，填写申请日期、部门与申请人等基本信息',
          '录入物料明细：选择物料，填写 数量（申请数量），系统按申请类型归类；申请单按数量汇总不计算金额/税率',
          '设置需求日期与是否紧急，确认后【保存】生成申请单号',
          '草稿状态点击【提交】进入审批；【审核】通过后单据生效',
          '审核通过后可在编辑页执行【下推】生成采购合同或采购订单'
        ],
        push: [
          { from: '采购申请单', arrow: '->', to: '采购合同', desc: '源状态已生效，下推到合同编辑页自动带入表头与明细' },
          { from: '采购申请单', arrow: '->', to: '采购订单', desc: '源状态已生效，生成对应采购订单' }
        ],
        note: '提示：采购申请单只有数量、无金额/税率，下推到订单/合同时按“审批数量（无则用申请数量）”带出并允许补填单价。'
      },
      {
        key: 'contract', title: '采购合同', editShot: '新增/编辑采购合同表单（点击放大）',
        intro: '与供应商签订的采购合同（单号前缀 PC-），支持采购合同/框架协议/年度合同三种类型，审核通过后可下推生成采购订单。',
        fields: [
          { name: '合同编号 / 合同日期', desc: '唯一编号（前缀 PC）及签订日期' },
          { name: '合同类型', desc: '采购合同 / 框架协议 / 年度合同' },
          { name: '有效期起 / 有效期止', desc: '合同有效起止时间；含交货日期、付款方式栏位' },
          { name: '供应商 / 采购方', desc: 'supplierId 供应商、buyerId 采购方、币种、仓库' },
          { name: '是否含税 / 计税方式', desc: '是否含税（是/否）、计税方式（价内税/价外税）、整单折扣率' },
          { name: '金额与税率', desc: '明细含 数量/单价/税率%/计税方式/折扣率/金额/税额/价税合计/成本' }
        ],
        steps: [
          '在采购合同列表点击【+ 新增】进入编辑页，选择合同类型并填写供应商、合同日期',
          '录入商品明细：选择物料、填写数量与单价，系统按税率/计税方式自动计算金额',
          '设置有效期与交货日期，确认含税口径与整单折扣后【保存】生成合同编号',
          '草稿状态点击【提交】进入审批；【审核】通过后单据生效（采购合同同时维护 status 与 approvalStatus 双状态）',
          '审核通过后可在编辑页执行【下推】生成采购订单'
        ],
        push: [
          { from: '采购合同', arrow: '->', to: '采购订单', desc: '源状态已生效，下推到订单编辑页自动带入表头与明细' }
        ],
        note: '注意：采购合同的【打印】功能当前为“开发中”占位，尚未接入打印预览；订单/入库/退货/发票编辑页的打印功能可用。'
      },
      {
        key: 'order', title: '采购订单',
        intro: '向供应商下达的采购订单（单号前缀 PO-），是采购执行的核心单据，审核通过后可下推生成采购入库单或采购发票。',
        fields: [
          { name: '订单号 / 订单日期', desc: '唯一单号（前缀 PO）及下单日期、预计到货日期' },
          { name: '供应商 / 采购员', desc: 'supplierId 供应商、buyer 采购员、币种与汇率、结算方式' },
          { name: '订单状态 / 入库状态', desc: '状态页签含 已确认/已入库/已完成 执行态；另有 inboundStatus 入库状态字段' },
          { name: '整单折扣 / 预付', desc: '整单折扣率、预付比例、预付金额、本位币折算 baseTotalAmount' },
          { name: '金额与税率', desc: '明细含 数量/单价/税率%/计税方式(价内/价外)/折扣率/金额/税额/价税合计' },
          { name: '来源追溯', desc: '表头含 sourceType/sourceBillNo/sourceOrderNo；明细含 sourceBillNo/sourceLineNo/sourceId/sourceItemId' }
        ],
        steps: [
          '在采购订单列表点击【+ 新增】进入编辑页，填写供应商、采购员与订单日期',
          '录入物料明细：选择物料、填写数量与单价，设置计价方式与税率，系统自动计算金额',
          '可设置整单折扣、预付比例与预计到货日期，确认后【保存】生成订单号',
          '草稿状态点击【提交】进入审批；【审核】通过后单据生效',
          '审核通过后可在编辑页执行【下推】生成采购入库单或采购发票'
        ],
        push: [
          { from: '采购申请单', arrow: '->', to: '采购订单', desc: '申请审核后直接生成采购订单' },
          { from: '采购合同', arrow: '->', to: '采购订单', desc: '合同审核后生成采购订单' },
          { from: '采购订单', arrow: '->', to: '采购入库单', desc: '源状态已生效，下推收货' },
          { from: '采购订单', arrow: '->', to: '采购发票', desc: '源状态已生效，按订单开进项发票' }
        ],
        goodNote: '采购订单是采购执行中枢：列表顶部 4 张统计卡（本月采购/待入库/已入库/采购总额）反映在执行状态，便于跟踪采购进度。'
      },
      {
        key: 'inbound', title: '采购入库单',
        intro: '供应商到货收货的单据（单号前缀 PIN-），记录收货入库、批次与货位信息，审核通过后增加库存。',
        fields: [
          { name: '入库单号 / 入库日期', desc: '唯一单号（前缀 PIN，主键 receiptId）及收货入库业务日期' },
          { name: '送货单号 / 检验单号', desc: 'deliveryNo 送货单号、inspectionNo 检验单号（外部单据号）' },
          { name: '供应商 / 仓库', desc: 'supplierId 供应商、stockId 入库仓库、deptId 部门' },
          { name: '批次 / 货位控制', desc: 'isLotControl 批次管理开关；明细含 批次号/生产日期/有效期至/货位 binId' },
          { name: '金额与本位币折算', desc: '明细含 数量/单价/金额/成本 avgCost；摘要区“入库总额/合计数量”及“本位币折算”块' },
          { name: '来源追溯', desc: '表头 sourceType=采购订单、sourceBillNo；明细含 来源单号/来源行号' }
        ],
        steps: [
          '在采购入库单列表点击【+ 新增】（或从采购订单下推）进入编辑页，填写供应商、入库仓库与入库日期',
          '录入物料明细：选择物料、填写数量与单价，设置批次号、生产日期、有效期至与货位',
          '可维护送货单号与检验单号，确认入库总额后【保存】生成入库单号',
          '草稿状态点击【提交】进入审批；【审核】通过后单据生效并增加库存',
          '审核通过后可在编辑页执行【下推】生成采购退货单或采购发票'
        ],
        push: [
          { from: '采购订单', arrow: '->', to: '采购入库单', desc: '源状态已生效，下推收货' },
          { from: '采购入库单', arrow: '->', to: '采购退货单', desc: '源状态已生效，按入库单发起退货' },
          { from: '采购入库单', arrow: '->', to: '采购发票', desc: '源状态已生效，按入库单开进项发票' }
        ],
        note: '提示：采购入库单主键为 receiptId（非 inboundId），下推与列表跳转均以 receiptId 为准。默认记录“来源类型=采购订单”，保存后可返回列表。'
      },
      {
        key: 'return', title: '采购退货单',
        intro: '向供应商退货的单据（单号前缀 PRT-），由采购入库单下推生成，记录退货原因并退货出库。',
        fields: [
          { name: '退货单号 / 退货日期', desc: '唯一单号（前缀 PRT，主键 returnId）及退货业务日期' },
          { name: '退货类型', desc: '质量退货 / 错发退货 / 供应商原因 / 其他' },
          { name: '供应商 / 单据来源', desc: 'supplierId 供应商、sourceType=采购入库单、sourceBillNo 来源单号' },
          { name: '退货原因', desc: 'returnReason 文本描述退货原因' },
          { name: '金额与折扣', desc: '整单折扣、折扣额、税额、净额；明细含 税率% 与 来源单号' }
        ],
        steps: [
          '在采购退货单列表点击【+ 新增】（或从采购入库单下推）进入编辑页，填写供应商与退货日期',
          '录入退货明细：选择物料、填写数量与单价，说明退货类型与退货原因',
          '确认折扣与净额后【保存】生成退货单号',
          '草稿状态点击【提交】进入审批；【审核】通过后单据生效并可下推红字发票'
        ],
        push: [
          { from: '采购入库单', arrow: '->', to: '采购退货单', desc: '源状态已生效，下推发起退货' },
          { from: '采购退货单', arrow: '->', to: '采购发票(红字)', desc: '退货审核后生成红字进项发票冲减' }
        ],
        note: '警示：采购退货单列表页存在已知缺陷——删除/驳回/重置草稿/结案/反结案/作废/反作废按钮因缺少对应 API 导入会使运行时报错；当前仅 查看/编辑/提交/审核 可安全使用。'
      },
      {
        key: 'invoice', title: '采购发票', editShot: '新增/编辑采购发票表单（点击放大）',
        intro: '供应商开具的进项发票单（单号前缀 PINV-），由采购入库/订单下推生成，支持红字发票与专票税号校验，是采购下推链路的终点单据。',
        fields: [
          { name: '发票单号 / 发票日期', desc: '唯一单号（前缀 PINV，主键 invoiceId）及开票日期' },
          { name: '发票类型', desc: '专票 / 普票；专票强制校验供应商税号' },
          { name: '供应商 / 供应商税号', desc: 'supplierId 供应商、supplierTaxNo 供应商税号（专票必填），plus invoiceCode/invoiceNumber' },
          { name: '金额与税额', desc: 'amount 金额、taxAmount 税额、totalAmount 价税合计（=金额+税额）、整单折扣' },
          { name: '红字 / 勾稽', desc: 'isRedInvoice 红字发票、isMatch 勾稽状态；退货生成红字冲减' },
          { name: '来源追溯', desc: '表头 sourceType=采购入库单、sourceBillNo；明细行默认 税率13% 价外税' }
        ],
        steps: [
          '在采购发票列表点击【+ 新增】（或从采购入库/订单下推）进入编辑页，选择发票类型并填写供应商',
          '专票必须填写供应商税号；录入发票明细（数量、金额，默认 13% 价外税）',
          '确认税额与价税合计后【保存】生成发票单号',
          '草稿状态点击【提交】进入审批；【审核】通过后单据生效（发票为终点单，不再下推）'
        ],
        push: [
          { from: '采购订单', arrow: '->', to: '采购发票', desc: '源状态已生效，按订单开进项发票' },
          { from: '采购入库单', arrow: '->', to: '采购发票', desc: '源状态已生效，按入库单开进项发票' },
          { from: '采购退货单', arrow: '->', to: '采购发票(红字)', desc: '退货审核后生成红字进项发票' }
        ],
        note: '注意：采购发票列表页的【打印】按钮因无多选列会永久禁用（打印功能失效）；采购发票报表当前展示 mock/硬编码数据，属开发占位。'
      }
    ]
  },
  {
    name: 'monitor', label: '监控报表', docs: [
      {
        key: 'report', title: '采购分析报表',
        intro: '采购核心数据分析报表中心，全方位展示采购价格走势、供应商分布与采购执行情况，帮助跟踪整体采购绩效。',
        fields: [
          { name: '报表1 采购价格走势', desc: '多物料价格折线图 + 物料×月份明细表，跟踪采购价格趋势' },
          { name: '报表2 供应商分析', desc: '供应商采购占比饼图 + 供应商表格（订单数/金额/占比/准时率）' },
          { name: '报表3 采购执行统计', desc: '4 张 KPI 卡（采购订单数/采购金额/入库率/发票率） + 状态分布柱状图' },
          { name: '明细抽屉', desc: '点击明细可打开数据抽屉，按当前报表切换明细列' }
        ],
        steps: [
          '进入 采购管理 -> 报表 -> 分析报表，顶部可筛选日期范围与关键字',
          '切换不同的报表区块（价格走势/供应商分析/执行统计）查看对应图表',
          '点击图表或表格数据可打开“明细”抽屉查看明细记录'
        ],
        goodNote: '采购分析报表数据来自真实接口（价格走势/供应商分析/执行统计），报表可用。明细抽屉“导出”按钮为开发中占位。'
      },
      {
        key: 'invoice-report', title: '采购发票报表',
        intro: '聚焦采购进项发票的分析报表，覆盖供应商发票、发票付款状态与税务分析三个维度。',
        fields: [
          { name: '报表1 供应商发票分析', desc: '供应商发票占比饼图 + 发票表（发票数/发票金额/占比/平均税率）' },
          { name: '报表2 发票付款状态', desc: '4 张 KPI 卡（发票总数/总金额/未付款金额/已付款率） + 付款状态分布柱状图' },
          { name: '报表3 采购税务分析', desc: '税率分布饼图 + 税务 KPI（总税额/平均税率/税额占比/最高税率）' },
          { name: '明细抽屉', desc: 'supplier-invoice / payment-status / tax-analysis 三套明细列' }
        ],
        steps: [
          '进入 采购管理 -> 报表 -> 采购发票报表（/purchase/report/invoice），顶部可筛选日期与关键字',
          '切换报表区块查看供应商发票、付款状态与税务分析',
          '点击数据打开“明细”抽屉查看明细记录'
        ],
        note: '提示：采购发票报表当前使用 mock/硬编码数据（如 2856000/656000/77.0% 等），属开发占位，展示数据不代表真实业务；导出按钮为开发中占位。'
      }
    ]
  }
]
</script>

<style scoped>
.help-page { padding: 16px; }
.help-header { border-radius: 8px; }
.help-title { display: flex; align-items: center; gap: 16px; }
.help-title h1 { margin: 0; font-size: 22px; }
.help-title p { margin: 6px 0 0; color: #606266; font-size: 14px; }

.help-section { padding: 4px 0; }
.help-section h3 { margin: 18px 0 10px; font-size: 16px; color: #303133; }
.feature-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 8px; }
@media (max-width: 1200px) { .feature-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 900px) { .feature-grid { grid-template-columns: repeat(2, 1fr); } }
.feature-card {
  border: 1px solid #EBEEF5; border-radius: 8px; padding: 16px; cursor: pointer;
  transition: all .25s ease; background: #fff;
}
.feature-card:hover { border-color: #409EFF; box-shadow: 0 4px 14px rgba(64,158,255,.18); transform: translateY(-2px); }
.feature-icon { font-size: 28px; margin-bottom: 8px; }
.feature-card h4 { margin: 0 0 6px; font-size: 15px; color: #303133; }
.feature-card p { margin: 0 0 8px; font-size: 13px; color: #909399; }
.feature-go { font-size: 13px; color: #409EFF; }

.flow-diagram { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 8px 0 4px; }
.flow-step { display: flex; align-items: center; gap: 6px; background: #f0f7ff; border-radius: 8px; padding: 8px 14px; }
.step-circle { width: 24px; height: 24px; border-radius: 50%; background: #409EFF; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 13px; }
.step-text { font-size: 13px; color: #303133; }
.flow-arrow { color: #409EFF; font-size: 16px; font-weight: bold; }

.op-table { margin: 8px 0; }

.doc-block { border-bottom: 1px dashed #DCDFE6; padding-bottom: 26px; }
.doc-title { font-size: 18px; color: #303133; margin: 10px 0 6px; }
.doc-intro { color: #606266; font-size: 14px; margin: 0 0 14px; }
.sub-title { font-size: 15px; color: #409EFF; margin: 18px 0 8px; }
.shot-wrap { position: relative; }
.shot-img { width: 100%; border: 1px solid #EBEEF5; border-radius: 6px; background: #fafafa; }
.shot-tip { font-size: 12px; color: #909399; margin-top: 4px; }
.steps { padding-left: 20px; color: #606266; font-size: 14px; line-height: 1.8; }

.doc-highlight {
  animation: docFlash 2.2s ease;
  border-radius: 8px;
}
@keyframes docFlash {
  0% { background: #ecf5ff; }
  100% { background: transparent; }
}
</style>
