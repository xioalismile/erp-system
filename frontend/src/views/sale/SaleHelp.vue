<template>
  <div class="help-page">
    <el-card shadow="never" class="help-header">
      <div class="help-title">
        <el-icon :size="32" color="#409EFF"><ShoppingCart /></el-icon>
        <div>
          <h1>{{ $tt("销售管理模块操作手册") }}</h1>
          <p>{{ $tt("销售管理覆盖从销售报价、销售合同、销售订单到销售出库、销售退货、销售开票的全流程单据管理，配合物流监控看板与销售报表中心，实现销售业务的闭环跟踪与数据分析") }}</p>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px;">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ============ 快速入门 ============ -->
        <el-tab-pane :label="$tf('快速入门')" name="quickstart">
          <div class="help-section">
            <h3>{{ $tt("一、模块概览") }}</h3>
            <p>{{ $tt("销售管理模块共包含 6 张业务单据（报价单、合同、订单、出库单、退货单、发票）及物流监控看板、销售报表中心。点击下方任一功能卡片可直达对应说明位置。") }}</p>
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
              <div class="flow-step"><div class="step-circle">1</div><div class="step-text">{{ $tt("创建销售报价单") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">2</div><div class="step-text">{{ $tt("签订销售合同") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">3</div><div class="step-text">{{ $tt("录入销售订单") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">4</div><div class="step-text">{{ $tt("销售出库") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">5</div><div class="step-text">{{ $tt("销售开票") }}</div></div>
            </div>
            <el-alert type="info" :closable="false" show-icon style="margin-top:14px;"
              :title="$tt('多数单据支持【下推】生成下游单据：报价单 / 合同可下推订单，订单可下推出库单 / 发票，出库单可下推退货 / 发票，退货单可下推红字发票。详见各单据说明中的“单据下推”。')" />

            <h3>{{ $tt("三、单据通用流程（所有销售单据一致）") }}</h3>
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

        <!-- ============ 销售单据 ============ -->
        <el-tab-pane v-for="group in groups" :key="group.name" :label="$tf(group.label)" :name="group.name">
          <div v-for="(doc, idx) in group.docs" :key="doc.key" :id="'doc-' + doc.key" class="doc-block">
            <h2 class="doc-title">{{ idx + 1 }}、{{ doc.title }}</h2>
            <p class="doc-intro">{{ doc.intro }}</p>

            <h4 class="sub-title">{{ $tt("页面总览（列表页）") }}</h4>
            <div class="shot-wrap">
              <el-image :src="'/manual/sale/' + doc.key + '.png'"
                        :preview-src-list="['/manual/sale/' + doc.key + '.png']"
                        fit="contain" hide-on-click-modal class="shot-img" />
              <div class="shot-tip">{{ $tt("点击图片可放大查看") }}</div>
            </div>

            <template v-if="doc.editShot">
              <h4 class="sub-title">{{ $tt("新增 / 编辑界面") }}</h4>
              <div class="shot-wrap">
                <el-image :src="'/manual/sale/' + doc.key + '-edit.png'"
                          :preview-src-list="['/manual/sale/' + doc.key + '-edit.png']"
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
import { ShoppingCart } from '@element-plus/icons-vue'

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
  { icon: '💰', label: '销售报价单', desc: '对外报价与下推订单/合同', group: 'sale-bills', key: 'quote' },
  { icon: '📄', label: '销售合同', desc: '合同签订与下推订单', group: 'sale-bills', key: 'contract' },
  { icon: '📦', label: '销售订单', desc: '订单录入、收款进度与下推', group: 'sale-bills', key: 'order' },
  { icon: '🚚', label: '销售出库单', desc: '货物出库、扣减库存与物流', group: 'sale-bills', key: 'delivery' },
  { icon: '↩️', label: '销售退货单', desc: '退货登记与回退库存', group: 'sale-bills', key: 'return' },
  { icon: '🧾', label: '销售发票', desc: '开票登记与多来源汇聚', group: 'sale-bills', key: 'invoice' },
  { icon: '🗺️', label: '物流监控看板', desc: '快递100物流跟踪查询', group: 'monitor', key: 'dashboard' },
  { icon: '📊', label: '销售报表中心', desc: 'KPI、目标完成率与毛利分析', group: 'monitor', key: 'report' }
]

const commonFlow = [
  { op: '创建单据', desc: '在各单据列表页点击【+ 新增】进入编辑页，填写单据信息与商品明细后保存；也可通过上游单据【下推】自动带入表头与明细' },
  { op: '提交审核', desc: '草稿状态下点击【提交】，提示“提交后将进入审批流程，审批通过后生效”；若未开启审批，提交后单据直接生效' },
  { op: '审核 / 驳回', desc: '待审核状态下【审核】通过后单据生效并可下推；【驳回】需填写原因，单据回到草稿/已驳回状态可修改后重新提交' },
  { op: '结案 / 作废', desc: '已生效单据可【结案】归档（结案后不可修改、不可下推）；草稿/待审核/已生效单据均可【作废】标记无效' }
]

const commonOps = [
  { op: '统计卡片', desc: '各列表页顶部展示 4 个统计卡片：本月XX / 草稿 / 待审核 / 已生效（当前演示数据展示为统计数量）' },
  { op: '状态筛选', desc: '顶部 Tabs 支持按 全部/草稿/待审核/已生效/已作废/已关闭 快速过滤' },
  { op: '查询 / 重置', desc: '搜索栏支持按关键词 + 日期范围过滤；【重置】还原筛选条件' },
  { op: '列设置', desc: '列表右上角可打开【列设置】抽屉，动态显隐展示列' },
  { op: '行内操作', desc: '表格操作列提供 查看/编辑/提交/审核/驳回/重置草稿/结案/反结案/作废/反作废/删除，按当前状态自动显隐' },
  { op: '单据状态', desc: '草稿/已驳回/已取消（可编辑），待审核（待审批），已生效（生效），已结案/已关闭（归档），已作废（无效标记）' },
  { op: '复制 / 打印', desc: '编辑页支持单据【复制】生成新单，并提供【打印预览】导出单据' }
]

const groups = [
  {
    name: 'sale-bills', label: '销售单据', docs: [
      {
        key: 'quote', title: '销售报价单',
        intro: '对外向客户报价的单据（单号前缀 QUT-），记录报价有效期、客户信息与商品明细报价，审核通过后可下推生成销售订单或销售合同。',
        fields: [
          { name: '报价单号 / 报价日期', desc: '唯一单号（前缀 QUT，保存后自动生成）及报价业务日期' },
          { name: '有效期起 / 有效期止', desc: '报价单对外有效的起止时间范围' },
          { name: '客户信息', desc: '客户、客户编号/简称、联系人、联系电话、收货地址' },
          { name: '销售 / 交易信息', desc: '销售员、币种与汇率、付款条件、结算方式、价格等级' },
          { name: '金额与折扣', desc: '整单折扣率、预付金额、价税合计；明细含 数量/单价/税率/税额/价税合计' },
          { name: '来源追溯', desc: '明细可携带 sourceBillType/sourceBillNo/sourceLineNo 来源字段，用于下推溯源' }
        ],
        steps: [
          '在销售报价单列表点击【+ 新增】进入编辑页，填写报价日期、客户与销售员等基本信息',
          '录入商品明细：选择物料（支持从物料库选择弹窗/从源单选单），填写数量、单价，系统按税率自动计算金额',
          '设置报价有效期起止，确认整单折扣与税率后【保存】生成报价单号',
          '草稿状态点击【提交】进入审批；【审核】通过后单据生效',
          '审核通过后可在编辑页执行【下推】生成销售订单或销售合同'
        ],
        push: [
          { from: '销售报价单', arrow: '->', to: '销售订单', desc: '源状态已生效，下推到订单编辑页自动带入表头与明细' },
          { from: '销售报价单', arrow: '->', to: '销售合同', desc: '源状态已生效，生成对应销售合同' }
        ],
        note: '提示：报价单下推判断使用“源状态为已生效”，下推按钮仅出现在编辑页，列表页不提供下推入口。'
      },
      {
        key: 'contract', title: '销售合同',
        intro: '记录与客户签订的销售合同（单号前缀 SC-），支持合同类型、有效期、付款方式、运输方式、发票类型等合同专属字段，审核通过后可下推生成销售订单。',
        fields: [
          { name: '合同编号 / 合同日期', desc: '唯一合同编号（前缀 SC）及签订日期' },
          { name: '合同类型', desc: '销售合同 / 框架协议 / 年度合同' },
          { name: '有效期起 / 有效期止', desc: '合同约定生效与截止时间' },
          { name: '付款方式 / 预收款', desc: '现金/银行转账/支付宝/微信支付/支票；预收款 depositAmount' },
          { name: '运输 / 开票信息', desc: '运输方式（快递/物流/自提/空运/海运）、是否开票、发票类型（增值税专票/普票/电子发票）' },
          { name: '金额与明细', desc: '价税合计、折扣、税额；明细含物料/规格/数量/单价/税率/价税合计及来源字段' }
        ],
        steps: [
          '在销售合同列表点击【+ 新增】进入编辑页，填写合同日期、合同类型与客户信息',
          '配置有效期、付款方式、运输方式、是否开票与发票类型等合同条款',
          '录入商品明细并保存，生成合同编号',
          '【提交】进入审批流程，【审核】通过后合同生效',
          '审核通过后可【下推】生成销售订单，生成时注明“由销售合同 xxx 下推生成”'
        ],
        push: [
          { from: '销售合同', arrow: '->', to: '销售订单', desc: '源状态已生效，下推到订单页并自动带入客户与明细' }
        ],
        note: '提示：合同的取消/驳回/作废操作均会弹出原因填写框，原因必填以便追溯。'
      },
      {
        key: 'order', title: '销售订单',
        intro: '销售业务核心单据（单号前缀 SO-），承载客户订单录入、收款进度跟踪与多向下推，是销售、出库、开票、生产环节的连接桥梁。',
        fields: [
          { name: '订单号 / 订单日期', desc: '唯一订单号（前缀 SO）及下单日期' },
          { name: '付款分类', desc: '现结/月结（现结客户提交合同免审批直接生效；月结客户走审批并受超期应收管控）' },
          { name: '商机关联', desc: '可选关联商机（opportunity），选择商机自动带出客户及物料明细' },
          { name: '收款进度', desc: '已生效订单展示 订单金额/已收款/未收款 及进度条，掌握回款情况' },
          { name: '仓库与物流', desc: '默认仓库、收货地址、运输方式、物流公司与物流单号' },
          { name: '明细与金额', desc: '明细支持行级仓库/储位；价税合计、已收款 receivedAmount、约定交付日期' }
        ],
        steps: [
          '在销售订单列表点击【+ 新增】进入编辑页；客户字段支持【+】快捷新建客户',
          '填写订单日期与付款分类；如需关联商机可从商机列表选择，自动带出客户和物料明细',
          '选择价格等级（按 priceAdjustRate 自动调整单价），录入商品明细',
          '保存生成订单号，【提交】进入审批，【审核】通过后订单生效',
          '已生效订单在编辑页展示收款进度块，跟踪回款',
          '依业务需要【下推】生成销售出库单 / 销售发票，或联接到生产工单'
        ],
        push: [
          { from: '销售订单', arrow: '->', to: '销售出库单', desc: '源状态已生效，生成出库单自动带入客户与明细' },
          { from: '销售订单', arrow: '->', to: '销售发票', desc: '源状态已生效，发票默认增值税专用发票' },
          { from: '销售订单', arrow: '->', to: '生产工单', desc: '源状态已生效，需选择订单明细行生成工单' },
          { from: '销售报价单 / 销售合同', arrow: '->', to: '销售订单', desc: '订单也是报价单、合同的下推目标' }
        ],
        note: '提示：下推单据明细需携带来源字段（sourceBillType/sourceBillNo/sourceLineNo）实现“来源追溯”，与上游单据建立勾稽关系。'
      },
      {
        key: 'delivery', title: '销售出库单', editShot: '新增/编辑销售出库单表单（点击放大）',
        intro: '货物发出登记单据（单号前缀 SOUT-），审核通过后扣减库存并开放物流查询与向下游下推，是库存与销售衔接的关键环节。',
        fields: [
          { name: '出库单号 / 出库日期', desc: '唯一出库单号（前缀 SOUT）及出库日期' },
          { name: '仓库', desc: '出库来源仓库（如原材料仓等）' },
          { name: '物流信息', desc: '承运商 carrier（可自动识别）、物流单号 trackingNo、运输费' },
          { name: '金额与明细', desc: '整单折扣、价税合计；明细含物料/数量/单价/税额/价税合计及来源字段' },
          { name: '审批状态', desc: '审核通过后扣减库存且不可修改，重置草稿恢复库存' }
        ],
        steps: [
          '在销售出库单列表点击【+ 新增】，填写出库日期、客户与仓库',
          '可直接从销售订单【下推】生成，自动带入客户与明细（需源单已生效）',
          '填写承运商与物流单号，保存后【提交】【审核】',
          '审核通过后系统自动扣减库存，单据进入已生效状态',
          '需要时执行【下推】生成销售退货单或以该出库单开销售发票'
        ],
        push: [
          { from: '销售出库单', arrow: '->', to: '销售退货单', desc: '源状态已生效，生成退货单登记退回货物' },
          { from: '销售出库单', arrow: '->', to: '销售发票', desc: '源状态已生效，按出库明细开票' },
          { from: '销售订单', arrow: '->', to: '销售出库单', desc: '出库单作为订单下推目标' }
        ],
        goodNote: '提示：物流单号填写后可在编辑页【查询物流】或列表页打开物流抽屉，基于快递100查看运输轨迹；承运商可自动识别（顺丰/京东需提供手机号后四位）。'
      },
      {
        key: 'return', title: '销售退货单',
        intro: '登记客户退回货物（单号前缀 SRT-），记录退货原因，审核通过后回退库存，并可下推生成红字发票。',
        fields: [
          { name: '退货单号 / 退货日期', desc: '唯一退货单号（前缀 SRT）及退货日期' },
          { name: '客户 / 仓库存', desc: '退货客户、退货仓库、联系人电话与收货地址' },
          { name: '退货原因', desc: 'returnReason 记录本次退货的原因' },
          { name: '金额与明细', desc: '预付比例/金额、整单折扣、价税合计；明细含数量/单价/税额/价税合计及来源字段' }
        ],
        steps: [
          '在销售退货单列表点击【+ 新增】，填写退货日期、客户与退货仓库',
          '可从销售出库单【下推】生成退货单，自动带入客户与明细',
          '填写退货原因，录入退货明细后保存',
          '【提交】【审核】，审核通过后系统自动回退库存',
          '需要时执行【下推】生成红字发票冲销'
        ],
        push: [
          { from: '销售退货单', arrow: '->', to: '销售发票（红字）', desc: '源状态已生效，发票备注“由销售退货单 xxx 下推生成(红字)”' },
          { from: '销售出库单', arrow: '->', to: '销售退货单', desc: '退货单作为出库单下推目标' }
        ],
        note: '警示：当前销售退货列表页仅“查看/编辑/提交/审核”可用；删除/驳回/重置草稿/作废/反结案等行内操作存在页面脚本缺失问题，暂不可用，请从其它单据或升级版本处理后使用。'
      },
      {
        key: 'invoice', title: '销售发票', editShot: '新增/编辑销售发票表单（点击放大）',
        intro: '销售开票登记单据（单号前缀 SINV-），是销售环节的终点单据，可由订单、出库单、退货单等多上游下推汇聚，支持专票/普票与红字冲销。',
        fields: [
          { name: '发票号 / 开票日期', desc: '唯一发票号（前缀 SINV）及开票日期' },
          { name: '发票类型', desc: '增值税专用发票 / 增值税普通发票' },
          { name: '开票信息', desc: '税号、地址、开户行、银行账号' },
          { name: '结算与预付', desc: '结算方式 settle、预付比例/金额' },
          { name: '来源 / 物流', desc: 'sourceBillType（默认“无来源”）、sourceBillNo、关联出库单号 deliveryNo' },
          { name: '明细与保质期', desc: '明细含生产日期/有效期至（物料启用批次/保质期控制时必填）、发票金额与价税合计' }
        ],
        steps: [
          '在销售发票列表点击【+ 新增】进入编辑页，填写开票日期、客户与发票类型',
          '可从销售订单 / 销售出库单【下推】生成发票，自动带入客户与明细；也可从销售退货单下推生成红字发票',
          '填写税号、开户行、银行账号等开票信息，确认结算方式与预付',
          '保存后【提交】【审核】，审核通过后发票生效',
          '发票为终点单，不再向下游下推；保存成功后将自动返回发票列表'
        ],
        push: [
          { from: '销售订单 / 销售出库单', arrow: '->', to: '销售发票', desc: '源状态已生效，汇聚生成销项发票' },
          { from: '销售退货单', arrow: '->', to: '销售发票（红字）', desc: '源状态已生效，生成红字发票冲销' }
        ],
        note: '提示：物料启用保质期控制时明细需填写生产日期并按 expiryDays 自动计算有效期至；启用批次控制时批次号必填。'
      }
    ]
  },
  {
    name: 'monitor', label: '监控报表', docs: [
      {
        key: 'dashboard', title: '物流监控看板',
        intro: '基于快递100的物流查询跟踪页面，支持从销售出库单进入，查看承运商、物流单号并在全国快递数据中实时跟踪货物运输轨迹。',
        fields: [
          { name: '物流单号 trackingNo', desc: '填写物流单号后查询；未填写时提示“该出库单未填写物流单号”' },
          { name: '承运商 carrier', desc: '系统自动识别承运商（顺丰/京东等需提供手机号后四位）' },
          { name: '物流状态', desc: '状态码图标：在库箱/运输中/派送/签收/异常/疑难等' },
          { name: '物流轨迹', desc: '分运输节点展示最新物流事件与时间' },
          { name: '回填出库单', desc: '识别到的承运商可回填到销售出库单的承运商字段' }
        ],
        steps: [
          '在销售出库单编辑页填写物流单号后点击【查询物流】，或在出库单列表打开物流抽屉',
          '系统自动识别承运商并查询快递100运输轨迹',
          '按状态码图标判断货物当前所处阶段（在库/运输/派送/签收/异常）',
          '识别到的承运商若需写回出库单，可通过“回填”操作保存'
        ],
        note: '提示：物流接入基于快递100，承运商覆盖顺丰、京东等主流快递；顺丰/京东查询需提供收件人手机号后四位以校验。'
      },
      {
        key: 'report', title: '销售报表中心',
        intro: '集中展示销售经营指标与多维度分析报表：KPI 卡片、销售目标完成率、订单趋势、销售额占比、毛利分析与提成计算，支持分维度钻取。',
        fields: [
          { name: 'KPI 卡片', desc: '本年销售总额 / 目标完成率 / 本月订单数 / 本月毛利率' },
          { name: '目标完成率', desc: '仪表盘 + 季度柱状图，展示销售目标达成情况' },
          { name: '订单趋势分析', desc: '近 12 月订单走势双轴图' },
          { name: '销售额占比', desc: '饼图，可按客户 / 物料 / 销售员维度切换' },
          { name: '毛利分析', desc: '销售收入/成本/毛利/毛利率 KPI + 低毛利订单列表' },
          { name: '提成计算表', desc: '按月份 + 提成基数（订单额/回款/毛利）+ 提成率版本计算；提成率自 2026-07 起历史留痕' }
        ],
        steps: [
          '进入销售报表中心，顶部查看本年销售 KPI 概览',
          '在各报表区块点击【查看明细】打开抽屉查看详细数据',
          '销售额占比报表可按客户/物料/销售员切换分析维度',
          '提成计算表需先在“设置中心->业务->提成率配置”配置提成率，未配置时报表提示引导配置'
        ],
        note: '提示：报表导出功能当前为开发中（按钮禁用）；提成率版本自 2026-07 起按开票日期匹配对应版本的提成率。'
      }
    ]
  }
]
</script>

<style scoped>
.help-header :deep(.el-card__body) { padding: 20px 24px; }
.help-title { display: flex; align-items: center; gap: 16px; }
.help-title h1 { margin: 0; font-size: 22px; color: #303133; }
.help-title p { margin: 6px 0 0; color: #909399; font-size: 14px; }

.help-section h3 { margin: 20px 0 12px; color: #303133; font-size: 16px; border-left: 4px solid #409EFF; padding-left: 10px; }
.help-section p { color: #606266; line-height: 1.8; }

.feature-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; margin: 12px 0; }
.feature-card { border: 1px solid #ebeef5; border-radius: 8px; padding: 14px; text-align: center; cursor: pointer; transition: all .2s; }
.feature-card:hover { box-shadow: 0 4px 14px rgba(64,158,255,.18); border-color: #409EFF; transform: translateY(-2px); }
.feature-icon { font-size: 28px; }
.feature-card h4 { margin: 8px 0 4px; color: #303133; font-size: 14px; }
.feature-card p { margin: 0; color: #909399; font-size: 12px; }
.feature-go { margin-top: 8px; color: #409EFF; font-size: 12px; opacity: 0; transition: opacity .2s; }
.feature-card:hover .feature-go { opacity: 1; }

.flow-diagram { display: flex; align-items: center; justify-content: center; gap: 10px; flex-wrap: wrap; margin: 16px 0; }
.flow-step { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.step-circle { width: 28px; height: 28px; border-radius: 50%; background: #409EFF; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 14px; }
.step-text { color: #606266; font-size: 13px; }
.flow-arrow { color: #c0c4cc; }

.doc-block { padding: 4px 8px; scroll-margin-top: 70px; }
.doc-highlight { animation: docFlash 2.2s ease; }
@keyframes docFlash {
  0% { background: rgba(64,158,255,.16); box-shadow: inset 4px 0 0 #409EFF; }
  60% { background: rgba(64,158,255,.10); box-shadow: inset 4px 0 0 #409EFF; }
  100% { background: transparent; box-shadow: none; }
}

.doc-title { font-size: 18px; color: #303133; margin: 8px 0 10px; border-left: 4px solid #409EFF; padding-left: 10px; }
.doc-intro { color: #606266; line-height: 1.8; margin-bottom: 8px; }
.sub-title { margin: 16px 0 10px; color: #303133; font-size: 15px; }
.sub-title::before { content: '▎'; color: #409EFF; }

.shot-wrap { text-align: center; }
.shot-img { width: 100%; max-width: 1100px; border: 1px solid #dcdfe6; border-radius: 6px; box-shadow: 0 2px 10px rgba(0,0,0,.06); cursor: zoom-in; }
.shot-tip { color: #c0c4cc; font-size: 12px; margin: 6px 0 4px; }

.steps { margin: 0 0 12px; padding-left: 24px; }
.steps li { color: #606266; line-height: 2; font-size: 14px; }
</style>
