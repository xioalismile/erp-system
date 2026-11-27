<template>
  <div class="help-page">
    <el-card shadow="never" class="help-header">
      <div class="help-title">
        <el-icon :size="32" color="#409EFF"><TrendCharts /></el-icon>
        <div>
          <h1>{{ $tt("财务管理模块操作手册") }}</h1>
          <p>{{ $tt("财务管理覆盖销售预收款、采购预付款、收款单、付款单等资金单据，配合费用分摊、成本核算与成本配置构成成本闭环，并通过统一报表中心呈现应收应付、账龄与资金流水分析") }}</p>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px;">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ============ 快速入门 ============ -->
        <el-tab-pane :label="$tf('快速入门')" name="quickstart">
          <div class="help-section">
            <h3>{{ $tt("一、模块概览") }}</h3>
            <p>财务管理模块含 4 张资金单据（销售预收款、采购预付款、收款单、付款单），以及费用分摊/成本核算/成本配置组成的成本闭环与凭证、报表中心。点击下方任一功能卡片可直达对应说明位置。</p>
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
              <div class="flow-step"><div class="step-circle">1</div><div class="step-text">销售侧：订单定金/预收 -> {{ $tt("销售预收款 登记") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">2</div><div class="step-text">采购侧：采购定金/预付 -> {{ $tt("采购预付款 登记") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">3</div><div class="step-text">实际收付款 -> {{ $tt("收款单 / 付款单 核销订单预收与应付") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">4</div><div class="step-text">费用分摊 -> 成本核算 -> {{ $tt("形成产品成本与报表") }}</div></div>
            </div>
            <el-alert type="info" :closable="false" show-icon style="margin-top:14px;"
              :title="$tt('财务单据状态体系不统一：收款单/付款单/费用分摊使用中文状态（草稿/待确认/待审核/已确认/已取消），而销售预收款/采购预付款使用数字状态（0-4）。请以各单据实际状态为准。')" />

            <h3>{{ $tt("三、单据通用流程（资金类单据）") }}</h3>
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

        <!-- ============ 资金单据 ============ -->
        <el-tab-pane v-for="group in groups" :key="group.name" :label="$tf(group.label)" :name="group.name">
          <div v-for="(doc, idx) in group.docs" :key="doc.key" :id="'doc-' + doc.key" class="doc-block">
            <h2 class="doc-title">{{ idx + 1 }}、{{ doc.title }}</h2>
            <p class="doc-intro">{{ doc.intro }}</p>

            <h4 class="sub-title">{{ $tt("页面总览（列表页）") }}</h4>
            <div class="shot-wrap">
              <el-image :src="'/manual/finance/' + doc.key + '.png'"
                        :preview-src-list="['/manual/finance/' + doc.key + '.png']"
                        fit="contain" hide-on-click-modal class="shot-img" />
              <div class="shot-tip">{{ $tt("点击图片可放大查看") }}</div>
            </div>

            <template v-if="doc.editShot">
              <h4 class="sub-title">{{ $tt("新增 / 编辑界面") }}</h4>
              <div class="shot-wrap">
                <el-image :src="'/manual/finance/' + doc.key + '-edit.png'"
                          :preview-src-list="['/manual/finance/' + doc.key + '-edit.png']"
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

            <h4 v-if="doc.push" class="sub-title">{{ $tt("单据下推 / 核销") }}</h4>
            <el-table v-if="doc.push" :data="doc.push" border size="small" style="margin-bottom:10px;">
              <el-table-column prop="from" :label="$tf('来源')" width="180" />
              <el-table-column prop="arrow" label="" width="40" align="center" />
              <el-table-column prop="to" :label="$tf('去向')" width="180" />
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
import { TrendCharts } from '@element-plus/icons-vue'

const activeTab = ref('quickstart')

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
  { icon: '💰', label: '收款单', desc: '实际收款入账与订单预收/发货应收核销（RC-）', group: 'fund-docs', key: 'receipt' },
  { icon: '💳', label: '付款单', desc: '实际付款出账与采购预付/应付核销（PV-）', group: 'fund-docs', key: 'payment' },
  { icon: '🏦', label: '销售预收款单', desc: '销售订单定金/预收登记（预收单）', group: 'fund-docs', key: 'sale-pre-receipt' },
  { icon: '🛒', label: '采购预付款单', desc: '采购订单定金/预付登记（预付单）', group: 'fund-docs', key: 'purchase-pre-payment' },
  { icon: '🧾', label: '费用分摊', desc: '费用登记并按平均/数量/金额分摊到产品', group: 'cost-docs', key: 'operation-expense' },
  { icon: '⚖️', label: '成本核算', desc: '汇总已生效费用+领料+报工形成产品成本', group: 'cost-docs', key: 'cost-calculation' },
  { icon: '⚙️', label: '成本核算配置', desc: '成本计算方法与核算规则配置', group: 'cost-docs', key: 'cost-config' },
  { icon: '📊', label: '财务报表', desc: '应收应付/账龄/利润/资金流水统一分析', group: 'report-docs', key: 'report' }
]

const commonFlow = [
  { op: '创建单据', desc: '在各单据列表页点击【+ 新增】进入编辑页，选择往来单位（客户/供应商），填写金额与资金信息后保存' },
  { op: '提交确认', desc: '草稿状态点击【提交】进入审批流程；部分单据无独立审批节点，确认后即可生效' },
  { op: '确认 / 作废', desc: '待确认/待审核状态可【确认】生效或【作废】作废单据，已作废单据不可编辑、不可核销' },
  { op: '核销联动', desc: '收款单可核销销售订单预收与发货应收；付款单可核销采购预付与应付，形成资金闭环' }
]

const commonOps = [
  { op: '统计卡片', desc: '单据列表顶部统计卡反映概览（销售预收/采购预付顶部为“本月新增/待审核/已核销”等），部分统计卡当前为硬编码 0（展示数据仅供参考）' },
  { op: '状态筛选', desc: '顶部 Tabs 支持按 全部/草稿/待确认/已确认/已作废（资金类）或 数字态 0-4（预收/预付）过滤' },
  { op: '查询 / 重置', desc: '搜索栏支持按 单号/往来单位/日期范围 过滤；【重置】还原筛选条件' },
  { op: '列设置', desc: '列表右上角可打开【列设置】抽屉，动态显隐展示列' },
  { op: '行内操作', desc: '表格操作列提供 查看/编辑/提交/审核/确认/作废/删除 等，按当前状态自动显隐' },
  { op: '状态体系', desc: '中文态：草稿/待确认/已确认/已取消、草稿/已核算/已确认；数字态：0草稿/1待审核/2已生效/3已核销/4已作废' },
  { op: '后端能力差异', desc: '收款单应收核销、付款单应付核销、费用分摊等后端接口当前部分为 TODO 占位，功能以前端界面展示为准' }
]

const groups = [
  {
    name: 'fund-docs', label: '资金单据', docs: [
      {
        key: 'receipt', title: '收款单',
        intro: '登记销售收款业务的实际入账（单号前缀 RC-，单据类型 PAYMENT_RECEIPT），支持核销销售订单预收与发货应收，形成完整的客户回款闭环。',
        fields: [
          { name: '收款单号 / 收款日期', desc: '唯一单号（前缀 RC）及实际入账日期' },
          { name: '往来单位（客户）', desc: 'customerId 收款客户，决定应收归属' },
          { name: '收款方式 / 账号', desc: 'paymentMethod 收款方式、资金账号等入账信息' },
          { name: '本次收款金额', desc: 'amount 本次实际收款金额' },
          { name: '核销明细', desc: '核销 Tab：订单预收（销售预收款）+ 发货应收（销售出库/发货应收）两轨核销' }
        ],
        steps: [
          '在收款单列表点击【+ 新增】进入编辑页，选择收款客户、收款日期与收款方式',
          '填写本次收款金额与资金账号',
          '在核销 Tab 选择待核销的销售订单预收/发货应收明细进行核销（支持自动顺序核销）',
          '保存生成收款单号，提交后【确认】单据生效',
          '确认后核销金额回写对应预收/应收，形成客户回款闭环'
        ],
        note: '提示：收款单的“发货应收”核销依赖后端应收核销接口，当前该接口为 TODO 占位，涉及应收核销的回写以前端界面交互为准，实际核销结果请以后端联调确认。'
      },
      {
        key: 'payment', title: '付款单',
        intro: '登记采购付款业务的实际出账（单号前缀 PV-，单据类型 PAYMENT_VOUCHER），支持核销采购订单预付与应付，形成完整的供应商付款闭环。',
        fields: [
          { name: '付款单号 / 付款日期', desc: '唯一单号（前缀 PV）及实际出账日期' },
          { name: '往来单位（供应商）', desc: 'supplierId 付款供应商，决定应付归属' },
          { name: '付款方式 / 账号', desc: 'paymentMethod 付款方式、资金账号等出账信息' },
          { name: '本次付款金额', desc: 'amount 本次实际付款金额' },
          { name: '核销明细', desc: '核销 Tab：采购预付（采购预付款）+ 发货应付 两轨核销' }
        ],
        steps: [
          '在付款单列表点击【+ 新增】进入编辑页，选择付款供应商、付款日期与付款方式',
          '填写本次付款金额与资金账号',
          '在核销 Tab 选择待核销的采购预付/应付明细进行核销',
          '保存生成付款单号，提交后【确认】单据生效',
          '确认后核销金额回写对应预付/应付，形成供应商付款闭环'
        ],
        note: '提示：付款单的“应付核销”依赖后端应付核销接口，当前该接口为 TODO 占位；且核销明细按供应商过滤的接口能力可能受限，涉及应付核销请以前端界面交互为准并后续联调确认。'
      },
      {
        key: 'sale-pre-receipt', title: '销售预收款单',
        intro: '登记销售订单的定金/预收款项（单据类型 SALE_PRE_RECEIPT），在发货收款前提前锁定客户资金，作为收款单核销订单预收的来源。',
        fields: [
          { name: '预收单号 / 状态', desc: '单号前缀 SP；数字状态 0草稿/1待审核/2已生效/3已核销/4已作废' },
          { name: '往来单位（客户）', desc: 'customerId 预收客户' },
          { name: '关联订单', desc: '可关联销售订单，作为订单定金' },
          { name: '预收金额', desc: 'amount 本次预收金额' },
          { name: '核销情况', desc: '已核销状态表示该预收已被收款单全部/部分冲销' }
        ],
        steps: [
          '在销售预收款单列表点击【+ 新增】进入编辑页，选择预收客户、填写预收金额',
          '可关联销售订单作为定金预收',
          '保存生成预收单号，提交进入【审核】，审核通过后状态为已生效（2）',
          '后续在收款单核销 Tab 中选择该预收明细进行核销，核销后状态更新为已核销（3）',
          '审核通过后的单据可打印/查看，作废需在草稿或待审核状态'
        ],
        note: '提示：销售预收款单列表顶部“本月新增/待审核/已核销”等统计卡当前为硬编码 0，展示数据仅供参考，实际以预收单明细列表为准。'
      },
      {
        key: 'purchase-pre-payment', title: '采购预付款单', editShot: '新增/编辑采购预付款单表单（点击放大）',
        intro: '登记采购订单的定金/预付款项（单据类型 PURCHASE_PRE_PAYMENT），在收货付款前提前向供应商支付款项，作为付款单核销采购预付的来源。',
        fields: [
          { name: '预付单号 / 状态', desc: '单号前缀 PP；数字状态 0草稿/1待审核/2已生效/3已核销/4已作废' },
          { name: '往来单位（供应商）', desc: 'supplierId 预付供应商' },
          { name: '关联订单', desc: '可关联采购订单，作为订单定金' },
          { name: '预付金额', desc: 'amount 本次预付金额' },
          { name: '核销情况', desc: '已核销状态表示该预付已由付款单全部/部分冲销' }
        ],
        steps: [
          '在采购预付款单列表点击【+ 新增】进入编辑页，选择预付供应商、填写预付金额',
          '可关联采购订单作为定金预付',
          '保存生成预付单号，提交进入【审核】，审核通过后状态为已生效（2）',
          '后续在付款单核销 Tab 中选择该预付明细进行核销，核销后状态更新为已核销（3）',
          '审核通过后的单据可查看/作废，作废需在草稿或待审核状态'
        ],
        note: '提示：采购预付款单列表的列配置与渲染内容存在不一致（已知缺陷），部分展示列可能与实际字段不符，请在编辑页核对实际字段值。'
      }
    ]
  },
  {
    name: 'cost-docs', label: '成本管理', docs: [
      {
        key: 'operation-expense', title: '费用分摊', editShot: '新增/编辑费用分摊表单（点击放大）',
        intro: '登记费用（单据类型 OPERATION_EXPENSE 相关），通过 平均/按数量/按金额 三种分摊方式将期间费用分摊到产品，作为成本核算的输入。',
        fields: [
          { name: '费用单号 / 状态', desc: '中文状态 草稿/已提交/已生效/已取消' },
          { name: '费用类型 / 部门', desc: '费用科目、发生部门等归属（部门选择当前“待接入”）' },
          { name: '费用金额', desc: '本次运营/制造费用金额' },
          { name: '分摊方式', desc: '分摊算法：平均分摊 / 按数量分摊 / 按金额分摊' },
          { name: '分摊明细', desc: '选择参与分摊的产品/对象并按方式计算分摊金额' }
        ],
        steps: [
          '在费用分摊列表点击【+ 新增】进入编辑页，填写费用类型、金额与发生期间',
          '录入费用分摊明细：选择参与分摊的产品，选择分摊方式（平均/按数量/按金额）',
          '系统按所选方式计算各产品分摊金额',
          '保存后提交进入【审核】，审核通过后生效',
          '已生效费用作为成本核算的“已生效费用”输入参与产品成本归集'
        ],
        note: '提示：费用分摊单据当前存在已知缺陷——单据类型误用为 PURCHASE_EXPENSE、部门选择与附件下载“待接入”、提交按钮引用了尚未定义的提交函数；实际功能可能部分不可用，请以界面实际交互为准。'
      },
      {
        key: 'cost-calculation', title: '成本核算',
        intro: '按核算期间汇总已生效的 费用分摊 + 领料单 + 生产报工单，通过 periodCalculate 归集计算产品成本，形成产品单位成本与总成本。',
        fields: [
          { name: '核算期间', desc: '按月份/期间维度归集成本' },
          { name: '成本类型', desc: '默认含多个成本类型行（可配置成本类型与归集科目）' },
          { name: '费用归集', desc: '汇总已生效 费用分摊 + 领料单 + 报工单 的本期金额' },
          { name: '单位成本 / 总成本', desc: '计算各产品的分摊单位成本与期间总成本' },
          { name: '状态', desc: '中文状态 草稿/已核算/已确认' }
        ],
        steps: [
          '在成本核算列表点击【+ 新增】进入编辑页，选择核算期间与成本类型',
          '点击归集/计算，系统汇总该期间已生效的费用、领料、报工',
          '核对成本归集明细与分摊结果',
          '保存生成核算单，可【确认】完成该期间成本核算',
          '已确认的成本核算作为产品成本参考与凭证/报表依据'
        ],
        note: '提示：成本核算当前存在已知缺陷——单据类型误用为 PURCHASE_EXPENSE，且成本归集需依赖后端 periodCalculate 接口能力，实际归集结果请以界面交互与后端联调确认为准。'
      },
      {
        key: 'cost-config', title: '成本核算配置',
        intro: '配置成本核算的方法与规则，决定成本计算采用何种加权方式及核算口径，是成本闭环的基准配置。',
        fields: [
          { name: '成本计算方法', desc: '移动加权平均 / 月末加权平均 / 固定成本 等' },
          { name: '启用 / 停用', desc: '布尔状态控制该配置是否生效（停用则成本核算按默认规则）' },
          { name: '默认成本类型', desc: '配置成本核算的默认成本类别行' }
        ],
        steps: [
          '进入成本核算配置列表，查看当前启用的成本方法',
          '可切换成本计算方法（移动/月末加权/固定成本）',
          '点击【启用/停用】开关控制配置生效状态',
          '保存后配置作为成本核算的计算依据'
        ],
        goodNote: '说明：成本配置为全局配置项，修改需谨慎；成本计算方法影响核算后产品单位成本口径。'
      }
    ]
  },
  {
    name: 'report-docs', label: '财务汇总', docs: [
      {
        key: 'report', title: '财务分析报表',
        intro: '财务管理统一报表中心（/finance/report），覆盖应收应付汇总/明细、账龄分析、资金收付流水与产品利润分析，为资金与成本决策提供数据视图。',
        fields: [
          { name: '应收/应付汇总', desc: '按往来单位维度的应收（AR）/应付（AP）汇总表' },
          { name: '应收/应付明细', desc: '应收/应付的往来明细流水台账' },
          { name: '账龄分析', desc: '按账龄区间分布应收/应付未结余额' },
          { name: '收付款流水', desc: '按时间的收付款资金流水台账' },
          { name: '产品利润分析', desc: '单价/成本/毛利维度的产品利润分析' }
        ],
        steps: [
          '进入 财务管理 -> 报表 -> 分析报表（/finance/report/analysis），顶部筛选日期/往来单位范围',
          '切换应收应付汇总/明细、账龄、收付款流水、产品利润等报表查看分析',
          '点击图表或表格数据查看明细记录'
        ],
        note: '提示：财务分析报表部分区块当前使用 mock/占位数据（开发占位）；应收/应付汇总表依赖后端 receivable/payable summary 接口，实际数据请确认后端已实现并返回。'
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
