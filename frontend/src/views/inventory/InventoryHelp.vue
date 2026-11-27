<template>
  <div class="help-page">
    <el-card shadow="never" class="help-header">
      <div class="help-title">
        <el-icon :size="32" color="#409EFF"><Box /></el-icon>
        <div>
          <h1>{{ $tt("库存管理模块操作手册") }}</h1>
          <p>{{ $tt("库存管理覆盖其他出入库、库存调拨、库存盘点、生产领料等业务单据，配合即时库存、批次库存、库存流水与库存分析报表，实现库存数量、批次与库龄的全程跟踪与盘点核对") }}</p>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px;">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ============ 快速入门 ============ -->
        <el-tab-pane :label="$tf('快速入门')" name="quickstart">
          <div class="help-section">
            <h3>{{ $tt("一、模块概览") }}</h3>
            <p>库存管理模块共包含 5 张库存业务单据（其他入库单、其他出库单、库存调拨单、库存盘点单、生产领料单），以及即时库存、批次库存、库存流水 3 个查询看板与库存分析报表。点击下方任一功能卡片可直达对应说明位置。</p>
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
              <div class="flow-step"><div class="step-circle">1</div><div class="step-text">{{ $tt("采购收货 / 生产入库 / 其他业务触发库存变动") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">2</div><div class="step-text">{{ $tt("通过 其他出入库/调拨/领料 登记库存变动") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">3</div><div class="step-text">{{ $tt("即时库存/批次/流水 实时核对余量与库龄") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">4</div><div class="step-text">{{ $tt("定期执行库存盘点，账实核对") }}</div></div>
            </div>
            <el-alert type="info" :closable="false" show-icon style="margin-top:14px;"
              :title="$tt('库存单据多数支持【下推】生成下游单据：生产工单可下推生产领料单；其他出库/领料出库后即时库存随之扣减。业务驱动的出入库（销售出库、采购入库、生产入库等）由对应业务模块自动更新库存。')" />

            <h3>{{ $tt("三、单据通用流程（所有库存单据一致）") }}</h3>
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

        <!-- ============ 库存单据 ============ -->
        <el-tab-pane v-for="group in groups" :key="group.name" :label="$tf(group.label)" :name="group.name">
          <div v-for="(doc, idx) in group.docs" :key="doc.key" :id="'doc-' + doc.key" class="doc-block">
            <h2 class="doc-title">{{ idx + 1 }}、{{ doc.title }}</h2>
            <p class="doc-intro">{{ doc.intro }}</p>

            <h4 class="sub-title">{{ $tt("页面总览（列表页）") }}</h4>
            <div class="shot-wrap">
              <el-image :src="'/manual/inventory/' + doc.key + '.png'"
                        :preview-src-list="['/manual/inventory/' + doc.key + '.png']"
                        fit="contain" hide-on-click-modal class="shot-img" />
              <div class="shot-tip">{{ $tt("点击图片可放大查看") }}</div>
            </div>

            <template v-if="doc.editShot">
              <h4 class="sub-title">{{ $tt("新增 / 编辑界面") }}</h4>
              <div class="shot-wrap">
                <el-image :src="'/manual/inventory/' + doc.key + '-edit.png'"
                          :preview-src-list="['/manual/inventory/' + doc.key + '-edit.png']"
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
import { Box } from '@element-plus/icons-vue'

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
  { icon: '📥', label: '其他入库单', desc: '非采购/生产来源的入库登记（OR-）', group: 'inventory-bills', key: 'other-receipt' },
  { icon: '📤', label: '其他出库单', desc: '非销售/领料来源的出库登记（OI-）', group: 'inventory-bills', key: 'other-issue' },
  { icon: '🔄', label: '库存调拨单', desc: '仓库/货位间库存调拨（ST-）', group: 'inventory-bills', key: 'stock-transfer' },
  { icon: '✅', label: '库存盘点单', desc: '定期盘点与账实差异调整（SC-）', group: 'inventory-bills', key: 'stock-check' },
  { icon: '🧺', label: '生产领料单', desc: '生产工单领料出库（MR-）', group: 'inventory-bills', key: 'material-requisition' },
  { icon: '📦', label: '即时库存', desc: '各仓库物料实时结存余量', group: 'inventory-query', key: 'current' },
  { icon: '🏷️', label: '批次与流水', desc: '批次数量的批次库存与逐笔流水台账', group: 'inventory-query', key: 'batch-flow' },
  { icon: '📊', label: '库存分析报表', desc: '周转率/安全库存/库龄/缺料综合分析', group: 'monitor', key: 'report' }
]

const commonFlow = [
  { op: '创建单据', desc: '在各单据列表页点击【+ 新增】进入编辑页，填写单据信息与商品明细后保存；也可通过上游单据【下推】自动带入表头与明细' },
  { op: '提交审核', desc: '草稿状态下点击【提交】，提示“提交后将进入审批流程，审批通过后生效”；若未开启审批，提交后单据直接生效并更新库存' },
  { op: '审核 / 驳回', desc: '待审核状态下【审核】通过后单据生效并变更库存；【驳回】需填写原因，单据回到草稿/已驳回状态可修改后重新提交' },
  { op: '结案 / 作废', desc: '已生效单据可【结案】归档（结案后不可修改、不可下推）；草稿/待审核/已生效单据均可【作废】标记无效' }
]

const commonOps = [
  { op: '统计卡片', desc: '单据列表顶部 4 张统计卡反映执行概览；其他入库/出库顶部为“本月入库/出库、待审核、合计数量”等概览卡片' },
  { op: '状态筛选', desc: '顶部 Tabs 支持按 全部/草稿/待审核/已生效/已关闭 快速过滤' },
  { op: '查询 / 重置', desc: '搜索栏支持按关键词 + 日期范围过滤；【重置】还原筛选条件' },
  { op: '列设置', desc: '列表右上角可打开【列设置】抽屉，动态显隐展示列' },
  { op: '行内操作', desc: '表格操作列提供 查看/编辑/提交/审核/驳回/重置草稿/删除 等，按当前状态自动显隐' },
  { op: '单据状态', desc: '草稿/已驳回（可编辑），待审核（待审批），已生效（生效并更新库存），已关闭（归档），已作废（无效标记）' },
  { op: '库存联动', desc: '出入库类单据（其他出入库/领料/调拨）审核通过后即更新即时库存与库存流水，是库存变动的入口' }
]

const groups = [
  {
    name: 'inventory-bills', label: '库存单据', docs: [
      {
        key: 'other-receipt', title: '其他入库单',
        intro: '登记非采购收货、非生产入库来源的库存增加（单号前缀 OR-，单据类型 OTHER_RECEIPT），如盘盈、退还、赠品等补充入库业务，审核通过后增加库存。',
        fields: [
          { name: '入库单号 / 入库日期', desc: '唯一单号（前缀 OR）及入库业务日期' },
          { name: '入库类型', desc: '区分入库业务类型（如盘盈入库/退货回库/赠品入库等）' },
          { name: '仓库 / 部门', desc: 'stockId 入库仓库、deptId 部门等库位与归属信息' },
          { name: '批次 / 货位控制', desc: 'isLotControl 批次管理开关；明细含 批次号/生产日期/有效期至/货位 binId' },
          { name: '金额与成本', desc: '明细含 数量/单价/金额；入库后按成本更新库存台账' }
        ],
        steps: [
          '在其他入库单列表点击【+ 新增】进入编辑页，选择入库类型、入库仓库并填写入库日期',
          '录入物料明细：选择物料、填写数量，开启批次管理时维护批次号、生产日期、有效期至与货位',
          '确认入库金额与合计后【保存】生成入库单号',
          '草稿状态点击【提交】进入审批；【审核】通过后单据生效并增加库存',
          '审核后可返回列表查看，即时库存与该仓批次数量同步增加'
        ],
        goodNote: '说明：其他入库单是库存增加的补充入口（采购入库/生产入库/销售退货等由对应业务模块自动更新库存）。审核通过后即时库存自动+1。'
      },
      {
        key: 'other-issue', title: '其他出库单',
        intro: '登记非销售出库、非领料来源的库存减少（单号前缀 OI-，单据类型 OTHER_ISSUE），如盘亏、报废、借出等出库业务，审核通过后扣减库存。',
        fields: [
          { name: '出库单号 / 出库日期', desc: '唯一单号（前缀 OI）及出库业务日期' },
          { name: '出库类型', desc: '区分出库业务类型（如盘亏出库/报废出库/样品出库等）' },
          { name: '仓库 / 部门', desc: 'stockId 出库仓库、deptId 部门等库位与归属信息' },
          { name: '批次 / 货位控制', desc: '开启批次管理时明细需指定 批次号、生产日期、有效期至 与货位' },
          { name: '金额与成本', desc: '明细含 数量/单价/金额；出库后按成本扣减库存台账' }
        ],
        steps: [
          '在其他出库单列表点击【+ 新增】进入编辑页，选择出库类型、出库仓库并填写出库日期',
          '录入物料明细：选择物料、填写数量，开启批次管理时指定批次号与货位',
          '确认出库金额与合计后【保存】生成出库单号',
          '草稿状态点击【提交】进入审批；【审核】通过后单据生效并扣减库存',
          '审核后可返回列表查看，即时库存与该仓批次数量同步减少'
        ],
        goodNote: '说明：其他出库单是库存减少的补充入口（销售出库/生产领料等由对应业务模块自动扣减库存）。审核通过后即时库存自动-1。'
      },
      {
        key: 'stock-transfer', title: '库存调拨单',
        intro: '在仓库/货位之间转移库存的单据（单号前缀 ST-，单据类型 STOCK_TRANSFER），审核通过后调出仓减少、调入仓增加，实现库位间的库存移动。',
        fields: [
          { name: '调拨单号 / 调拨日期', desc: '唯一单号（前缀 ST）及调拨业务日期' },
          { name: '调出 / 调入仓库', desc: 'fromStockId 调出仓库（源）、toStockId 调入仓库（目标），可细化到货位' },
          { name: '库位 / 批次管理', desc: '支持调出货位与调进货位；开启批次管理时指定批次号随单移动' },
          { name: '明细与数量', desc: '明细含 物料/数量/批次号；调拨不改变库存总量，只移动库位' }
        ],
        steps: [
          '在库存调拨单列表点击【+ 新增】进入编辑页，选择调出仓库与调入仓库',
          '录入调拨明细：选择物料、填写数量，开启批次管理时指定批次号与货位',
          '确认调拨方向与数量后【保存】生成调拨单号',
          '草稿状态点击【提交】进入审批；【审核】通过后调出仓扣减、调入仓增加',
          '审核后可返回列表查看，调拨不改变库存总量，仅移动库位'
        ],
        note: '提示：库存调拨单明细的物料/仓库选择器（SourcePicker）当前存在已知缺陷——因下推关系配置缺少库存调拨条目，选择器可能返回空列表；可改用编辑页直接录入物料明细。'
      },
      {
        key: 'stock-check', title: '库存盘点单', editShot: '新增/编辑库存盘点单表单（点击放大）',
        intro: '定期对库存进行实物清点的单据（单号前缀 SC-，单据类型 STOCK_CHECK），记录账面数量与实盘数量，审核通过后按差异调整库存。',
        fields: [
          { name: '盘点单号 / 盘点日期', desc: '唯一单号（前缀 SC）及盘点业务日期' },
          { name: '盘点类型', desc: '区分 全面盘点 / 抽盘 / 循环盘点 等盘点方式' },
          { name: '账面 / 实盘数量', desc: '明细含 账面结存数量 与 实盘数量；差异=实盘-账面' },
          { name: '仓库 / 批次 / 货位', desc: 'stockId 盘点仓库；开启批次管理时按批次+货位清点' },
          { name: '差异处理', desc: '盘点差异审核通过后自动调整库存（盘盈增、盘亏减），可关联其他出入库单' }
        ],
        steps: [
          '在库存盘点单列表点击【+ 新增】进入编辑页，选择盘点仓库、盘点类型与盘点日期',
          '录入盘点明细：选择物料，填入 账面数量 与 实盘数量，系统自动计算差异',
          '确认差异与合计后【保存】生成盘点单号',
          '草稿状态点击【提交】进入审批；【审核】通过后按差异调整库存',
          '审核后可返回列表查看，盘盈/盘亏差异已自动更新即时库存'
        ],
        note: '提示：库存盘点单列表当前顶部“待审核”统计卡可能错误引用“草稿”计数（已知缺陷，展示数据仅供参考）；盘点属于定期核对业务，一般不从其他单据下推生成。'
      },
      {
        key: 'material-requisition', title: '生产领料单', editShot: '新增/编辑生产领料单表单（点击放大）',
        intro: '生产工单领料出库的单据（单号前缀 MR-，单据类型 MATERIAL_REQUISITION），由生产工单下推生成，按 BOM 需求将领用物料出库扣减库存。',
        fields: [
          { name: '领料单号 / 领料日期', desc: '唯一单号（前缀 MR）及领料业务日期' },
          { name: '领料部门 / 领料人', desc: 'deptId 领料部门、领料人员等归属信息' },
          { name: '仓库 / 批次 / 货位', desc: 'stockId 出库仓库；开启批次管理时指定批次号与货位' },
          { name: '领料明细', desc: '明细含 物料/领用数量/用途；可关联生产工单与 BOM 展开领用' }
        ],
        steps: [
          '在生产领料单列表点击【+ 新增】（或从生产工单下推）进入编辑页，选择领料部门、领料仓库',
          '录入领料明细：选择物料、填写领用数量（可从工单/BOM 带出需求数量）',
          '确认领用数量与合计后【保存】生成领料单号',
          '草稿状态点击【提交】进入审批；【审核】通过后领用物料出库扣减库存',
          '审核后可返回列表查看，即时库存同步减少'
        ],
        push: [
          { from: '生产工单', arrow: '->', to: '生产领料单', desc: '工单状态已生效，下推生成领料单自动带入 BOM 需求明细' }
        ],
        note: '提示：生产领料单列表顶部“本月领料”统计卡当前硬编码为 0（已知缺陷，展示数据仅供参考）；实际领料数量以明细合计为准。'
      }
    ]
  },
  {
    name: 'inventory-query', label: '库存查询', docs: [
      {
        key: 'current', title: '即时库存',
        intro: '查看各仓库物料实时结存余量的查询看板，是库存核对的首选入口，展示当前可用数量、占用数量与批次分布。',
        fields: [
          { name: '仓库 / 物料', desc: '支持按仓库、物料编码/名称筛选即时库存明细' },
          { name: '结存数量', desc: '各仓库该物料的实时结存数量（随出入库审核自动更新）' },
          { name: '批次展开', desc: '开启批次管理物料支持展开到批次数量' }
        ],
        steps: [
          '进入 库存管理 -> 即时库存（/inventory/current），选择仓库或输入物料关键字',
          '查看各物料在各仓库的实时结存数量与合计',
          '筛选后关注库存是否充足，为补货/调拨/生产计划提供依据'
        ],
        goodNote: '说明：即时库存是只读查询页，无新增/编辑入口；库存变动一律通过出入库/调拨/领料单据审核来驱动。'
      },
      {
        key: 'batch-flow', title: '批次库存与库存流水',
        intro: '批次库存按物料批次维度展示结存数量与库龄；库存流水按时间顺序记录每一笔库存变动的出入明细与结存。',
        fields: [
          { name: '批次库存', desc: '按 物料×批次 展示结存数量、批号、生产日期、有效期与库龄' },
          { name: '库存流水', desc: '逐笔记录 单据类型/单据号/出入方向/数量/仓库，形成完整台账' },
          { name: '筛选', desc: '支持按物料、仓库、日期范围、单据类型过滤流水' }
        ],
        steps: [
          '进入 库存管理 -> 批次库存（/inventory/batches）查看各批次结存与库龄',
          '进入 库存管理 -> 库存流水（/inventory/ledger）按时间范围与物料筛选出入库明细',
          '结合批次有效期与库龄，识别临期/呆滞库存并安排处理'
        ],
        goodNote: '说明：批次库存与库存流水均为只读查询，可溯源每笔库存变动的来源单据；两页列表的“导出”功能当前为禁用/开发占位。'
      }
    ]
  },
  {
    name: 'monitor', label: '监控报表', docs: [
      {
        key: 'report', title: '库存分析报表',
        intro: '库存核心数据分析报表中心，覆盖库存周转率、安全库存预警、批次库龄分析与缺料分析四个维度，帮助掌握库存健康度与缺口。',
        fields: [
          { name: '报表1 库存周转率', desc: 'KPI 卡 + 周转率趋势/分布图表，评估库存周转效率' },
          { name: '报表2 安全库存预警', desc: '低于安全库存的物料预警清单，提示补货需求' },
          { name: '报表3 批次库龄分析', desc: '按批次库龄分布分析，识别呆滞/临期库存' },
          { name: '报表4 缺料分析', desc: '缺料物料清单，配合生产/采购计划' },
          { name: '明细抽屉', desc: '点击图表或表格数据可打开数据抽屉查看明细记录' }
        ],
        steps: [
          '进入 库存管理 -> 报表 -> 分析报表（/inventory/report/analysis），顶部可筛选日期范围与关键字',
          '切换库存周转率/安全库存预警/批次库龄/缺料分析等报表区块查看对应分析',
          '点击图表或表格数据打开“明细”抽屉查看明细记录'
        ],
        note: '提示：库存分析报表当前部分区块使用 mock/占位数据（开发占位），展示数据仅供参考；明细抽屉“导出”按钮为开发中占位。'
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
