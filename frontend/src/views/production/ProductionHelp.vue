<template>
  <div class="help-page">
    <el-card shadow="never" class="help-header">
      <div class="help-title">
        <el-icon :size="32" color="#E6A23C"><Tools /></el-icon>
        <div>
          <h1>{{ $tt("生产管理模块操作手册") }}</h1>
          <p>生产管理覆盖从工艺基础资料、MRP物料需求计划、生产工单下达执行、报工到生产入库/退料/补料及组装拆卸的全流程，配合委外加工协同，实现生产业务闭环与成本归集</p>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px;">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ============ 快速入门 ============ -->
        <el-tab-pane :label="$tf('快速入门')" name="quickstart">
          <div class="help-section">
            <h3>{{ $tt("一、模块概览") }}</h3>
            <p>生产管理模块包含 3 项基础档案（工艺路线、工作中心、工序·工位）、生产工单与 MRP 计划、以及生产入库/退料/补料/报工/组装/拆卸等执行单据。点击下方任一功能卡片可直达对应说明位置。</p>
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
              <div class="flow-step"><div class="step-circle">1</div><div class="step-text">{{ $tt("维护工艺路线 / 工作中心 / 工序") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">2</div><div class="step-text">{{ $tt("MRP 运算生成生产建议 / 采购建议") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">3</div><div class="step-text">{{ $tt("生成并审核生效生产工单") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">4</div><div class="step-text">领料 -> {{ $tt("报工") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">5</div><div class="step-text">{{ $tt("生产入库（完工）") }}</div></div>
            </div>
            <el-alert type="info" :closable="false" show-icon style="margin-top:14px;"
              :title="$tt('生产工单是执行中枢：支持下推 生产领料/补料/退料单（库存模块）、工序汇报（报工）单、生产入库单与委外加工单；MRP 运算结果可下推 采购申请（外购件）与 生产工单（自制件）。')" />

            <h3>{{ $tt("三、单据状态体系说明") }}</h3>
            <el-alert type="warning" :closable="false" show-icon style="margin-bottom:14px;"
              :title="$tt('注意：生产模块单据统计口径并不完全统一——生产工单采用通用状态流（草稿→待审核→已生效→已作废），生产入库累计合格数量达到计划数量后自动结案（显示为“已结案”，如需继续处理可反结案）；MRP 方案为 草稿→已生效→已执行；组装/拆卸为数字态（草稿→待审批→生效）；委外加工单为 8 态数字体系。详见各单据说明。')" />

            <h3>{{ $tt("四、通用操作及状态说明") }}</h3>
            <div class="op-table">
              <el-table :data="commonOps" border size="default">
                <el-table-column prop="op" :label="$tf('操作 / 状态')" width="200" />
                <el-table-column prop="desc" :label="$tf('说明')" />
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ 分组 Tabs ============ -->
        <el-tab-pane v-for="group in groups" :key="group.name" :label="$tf(group.label)" :name="group.name">
          <div v-for="(doc, idx) in group.docs" :key="doc.key" :id="'doc-' + doc.key" class="doc-block">
            <h2 class="doc-title">{{ idx + 1 }}、{{ doc.title }}</h2>
            <p class="doc-intro">{{ doc.intro }}</p>

            <h4 class="sub-title">{{ $tt("页面总览（列表页）") }}</h4>
            <div class="shot-wrap">
              <el-image :src="'/manual/production/' + doc.key + '.png'"
                        :preview-src-list="['/manual/production/' + doc.key + '.png']"
                        fit="contain" hide-on-click-modal class="shot-img" />
              <div class="shot-tip">{{ $tt("点击图片可放大查看") }}</div>
            </div>

            <template v-if="doc.editShot">
              <h4 class="sub-title">{{ $tt("新增 / 编辑界面") }}</h4>
              <div class="shot-wrap">
                <el-image :src="'/manual/production/' + doc.key + '-edit.png'"
                          :preview-src-list="['/manual/production/' + doc.key + '-edit.png']"
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
              <el-table-column prop="from" :label="$tf('来源单据')" width="160" />
              <el-table-column prop="arrow" label="" width="40" align="center" />
              <el-table-column prop="to" :label="$tf('目标单据')" width="160" />
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
  { icon: '🗺️', label: '工艺路线', desc: '定义加工路径、工序与工时', group: 'masterdata', key: 'routing' },
  { icon: '🏭', label: '工作中心', desc: '产线/设备/班组产能单元', group: 'masterdata', key: 'workcenter' },
  { icon: '🛠️', label: '工序 · 工位', desc: '加工步骤与作业位置档案', group: 'masterdata', key: 'workstation' },
  { icon: '📋', label: '生产工单', desc: '生产任务执行与状态流转', group: 'plan', key: 'workorder' },
  { icon: '📅', label: '车间排程看板', desc: '工单执行卡片式派工看板', group: 'plan', key: 'schedule-board' },
  { icon: '🧮', label: 'MRP 分析', desc: '净需求运算向导与下推', group: 'plan', key: 'mrp' },
  { icon: '🗂️', label: 'MRP 运算方案', desc: '方案配置、审核与执行', group: 'plan', key: 'mrp-plan' },
  { icon: '📥', label: '生产入库单', desc: '完工合格品入库、增加库存', group: 'exec', key: 'receipt' },
  { icon: '↩️', label: '生产退料单', desc: '剩余/报废原料退回仓库', group: 'exec', key: 'return' },
  { icon: '➕', label: '生产补料单', desc: '损耗/报废补充领料', group: 'exec', key: 'supplement' },

  { icon: '📝', label: '工序汇报单', desc: '报工：合格/报废数量与工时', group: 'exec', key: 'operation-report' },
  { icon: '🧩', label: '生产组装单', desc: '原料 BOM 组装为成品', group: 'assemble-dis', key: 'assemble' },
  { icon: '🔧', label: '生产拆卸单', desc: '成品拆卸还原为原料', group: 'assemble-dis', key: 'disassemble' }
]

const commonOps = [
  { op: '统计卡片', desc: '各列表顶部概览统计卡（如本月入库/本月组装 等）。部分页面统计值为开发占位，可能显示为固定数字' },
  { op: '状态筛选', desc: '多数单据列表顶部有 全部/草稿/已生效/已关闭 等页签；生产工单按通用单据状态（全部/草稿/待审核/已生效/已作废）过滤，已结案单在工单详情可反结案' },
  { op: '查询 / 重置', desc: '搜索栏支持按关键词过滤；【重置】还原筛选条件' },
  { op: '状态流转位置', desc: '生产类单据的 提交/审核/结案/下推 等流转动作请进入单据【详情页】顶栏或“更多”菜单操作；部分列表行内仅提供 查看/编辑/删除' },
  { op: '单据状态', desc: '工单：草稿→待审核→已生效→已作废（通用状态流），生产入库累计合格数量达到计划数量后自动结案（显示为“已结案”），如需继续处理可反结案；入库/退料/补料/转移/报工：草稿→待审核→已生效；组装/拆卸：草稿→待审批→生效；委外：草稿→待审核→已生效→已下达→执行中→已完工→已结案' }
]

const groups = [
  {
    name: 'masterdata', label: '基础档案', docs: [
      {
        key: 'routing', title: '工艺路线',
        intro: '定义产品生产过程所需的加工路径，包含工序明细（工序、工作中心类型/工位/工作中心、工时、批量、人数、是否需质检/关键工序），被生产工单与 BOM 引用。',
        fields: [
          { name: '工艺路线编码 / 名称', desc: '唯一编码与名称标识一条工艺路线' },
          { name: '版本 / 物料', desc: '路线版本号及关联的产品物料（来自物料档案）' },
          { name: '工序明细', desc: '含工序编码/名称、工作中心类型、工位、工作中心、准备/加工工时、批量、人数、是否需质检、是否关键工序' },
          { name: '总工时', desc: '整条工艺路线的工时汇总展示' },
          { name: '状态', desc: '草稿 / 已生效 / 已停用' }
        ],
        steps: [
          '在工艺路线列表点击【新增工艺路线】，填写路线编码、名称、版本与关联物料',
          '在工序明细中添加工序，按生产顺序排列并设置工序号',
          '为每道工序指定工作中心类型/工位/工作中心、准备工时与加工工时',
          '保存后即可被生产工单选择引用'
        ],
        note: '当前版本工艺路线的【审核】【停用】接口已实现但列表未提供按钮入口，状态默认停留在草稿，保存后即可使用；列表操作列随外部工程存在类型复用，不影响主要功能。'
      },
      {
        key: 'workcenter', title: '工作中心',
        intro: '生产的基本产能单元（设备组/产线/车间/工作单元），供工艺路线与工单调度引用，用于产能计算与成本分摊。',
        fields: [
          { name: '编码 / 名称', desc: '工作中心唯一编码与名称' },
          { name: '类型', desc: '设备 / 设备组 / 生产线 / 车间 / 工作单元' },
          { name: '产能类型 / 日产能 / 单位', desc: '产能计算方式、每日产能数值及产能单位' },
          { name: '人工 / 制造费用率', desc: '人工费率与制造费用率，用于成本归集' },
          { name: '最大人数 / 是否委外', desc: '最大作业人数；启用委外时需选择外部供应商' },
          { name: '状态', desc: '启用 / 停用' }
        ],
        steps: [
          '在工作中心列表点击【新增】，填写编码、名称与类型',
          '设置产能类型、日产能、产能单位，以及人工/制造费用率',
          '维护需要时填写最大人数；如为委外类型可勾选委外并选择供应商',
          '保存后状态为启用，可供工艺路线与工单引用'
        ],
        note: '工作中心顶部统计（启用中/已停用/委外类型）当前为开发占位固定值；列表“类型”字段已用标签化展示便于识别。'
      },
      {
        key: 'workstation', title: '工序 · 工位',
        intro: '维护生产的工序/工位档案，归属到工作中心，供工艺路线引用，是生产执行的最小作业位置单元。',
        fields: [
          { name: '工序编码 / 名称', desc: '工序/工位唯一编码与名称' },
          { name: '所属工作中心', desc: '该工序归属的工作中心' },
          { name: '工序类型', desc: '工序的业务类型分类' },
          { name: '位置 / 状态', desc: '作业位置说明；启用 / 停用' }
        ],
        steps: [
          '在工序·工位列表点击【新增】，填写工序编码、名称并选择所属工作中心',
          '设置工序类型与位置说明',
          '保存后状态为启用，可在工艺路线明细中被引用'
        ],
        note: '工序·工位顶部统计（启用中/已停用/工序类型）为开发占位固定值；该项为档案类页面，不涉及审批流。'
      }
    ]
  },
  {
    name: 'plan', label: '工单与计划', docs: [
      {
        key: 'workorder', title: '生产工单',
        intro: '生产任务的正式单据，记录生产什么、生产多少、何时完成，是生产执行的枢纽。由 MRP 或销售订单下推而来，又可下推领料/补料/退料、报工、生产入库与委外加工等单据。',
        fields: [
          { name: '工单编号 / 日期', desc: '唯一单号（自动生成）及工单业务日期（必填）' },
          { name: '优先级', desc: '普通 / 加急 / 紧急；列表对加急单带“急”标记' },
          { name: '计划开始 / 完工', desc: '计划起止时间；提前期为只读、实际开始/完工为禁用态' },
          { name: '成品明细', desc: '成品编码/名称/规格型号/计划数量/单位/来源单号；草稿状态可更换物料' },
          { name: '原料明细', desc: '需求数量/已领料数量/仓库，依据工单 BOM 自动带出' },
          { name: '进度', desc: '完成/计划数量及百分比、当前工序；顶部含计划完成进度' },
          { name: '来源', desc: '手工 / MRP / 销售 三种来源方式' }
        ],
        steps: [
          '通过 MRP 下推、销售订单下推或在列表【+ 新增】创建生产工单',
          '选择产品物料与计划数量，工单自动带入该物料的 BOM 结构作为原料明细',
          '设置优先级、计划开始/完工日期，选择工艺路线（可选）',
          '在详情页执行【提交/审核】进入审批流；生产入库累计合格数量达到计划数量后系统自动结案（如需继续处理可在详情页反结案）',
          '在详情页通过【下推/快速报工】生成领料、报工、入库、委外等下游单据'
        ],
        push: [
          { from: 'MRP 运算', arrow: '->', to: '生产工单', desc: '自制件（属性含“半/成品”）建议下推生成工单（草稿状态）' },
          { from: '销售订单', arrow: '->', to: '生产工单', desc: '按销售订单下推生成工单，带入产品与数量' },
          { from: '生产工单', arrow: '->', to: '领料/补料/退料单', desc: '详情页下推至库存模块生产领料体系，按 BOM 领用扣减库存' },
          { from: '生产工单', arrow: '->', to: '工序汇报（报工）单', desc: '下推或“快速报工”生成报工单（草稿状态，需再提交审核）' },
          { from: '生产工单', arrow: '->', to: '生产入库单', desc: '完工产品下推入库单，审核后增加库存' },
          { from: '生产工单', arrow: '->', to: '委外加工单', desc: '需委外的工序下推生成委外加工单' }
        ],
        note: '提示：生产工单采用通用状态流（草稿→待审核→已生效→已作废）；生产入库累计合格数量达到计划数量后自动结案（显示为“已结案”），如需继续处理可在工单详情执行反结案。工单详情页的“快速报工”操作工下拉当前为示例数据（张三/李四/王五），可手动输入真实人员。'
      },
      {
        key: 'schedule-board', title: '车间排程看板',
        intro: '工单执行的卡片式派工看板（非单据），按状态与工作中心筛选，直观展示各工单的计划完工与生产进度，支持点击进入工单详情。',
        fields: [
          { name: '工单卡片', desc: '工单号（带“急”标记）、状态、产品、当前工序、计划完工、进度条' },
          { name: '筛选条件', desc: '关键字 / 工作中心 / 状态（在制 / 已结案 / 全部）' },
          { name: '看板统计', desc: '工单总数 / 在制 / 已结案 / 即将到期（3天内）' }
        ],
        steps: [
          '进入生产管理 -> 车间排程看板，按需用关键字/工作中心/状态筛选',
          '通过进度条查看各工单完成情况与计划完工时间',
          '点击工单卡片跳转至工单详情进行后续操作'
        ],
        goodNote: '车间排程看板为查看类页面（无编辑入口），用于生产调度与进度跟踪。'
      },
      {
        key: 'mrp', title: 'MRP 分析',
        intro: 'MRP 需求运算向导，通过 5 步（需求来源→运算配置→执行运算→结果审核→下推执行）计算净需求并下推采购建议与生产建议。',
        fields: [
          { name: '需求来源', desc: '销售订单（仅已生效可选）/ 销售预测 / 手工录入（物料/数量/需求日期/优先级）' },
          { name: '运算配置', desc: '计划展望期（天）、时界（天）、批量规则（逐批/固定/经济）、固定批量、最小订货量' },
          { name: '开关项', desc: '考虑现有库存 / 在途采购 / 在制工单 / 安全库存 / BOM替代 / 采购申请 / 展开所有层级' },
          { name: '净需求公式', desc: '净需求 = 毛需求 - 现有库存 - 在途量 + 安全库存' },
          { name: '运算结果', desc: '缺料明细 / 采购建议 / 生产建议 / 库存满足 四个页签；统计 涉及物料数/毛需求总量/净需求总量/可满足物料' },
          { name: '建议类型', desc: '建议采购（PURCHASE）/ 建议生产（PRODUCE），按物料属性判断' }
        ],
        steps: [
          '进入 MRP 分析，选择需求来源（销售订单/手工录入等方式）',
          '配置运算参数（展望期、时界、批量规则、开关项）并保存运算方案',
          '点击开始运算，系统按 BOM 逐层展开计算净需求',
          '在结果页查看缺料明细与建议，可勾选后执行【下推】',
          '外购件下推采购申请、自制件下推生产工单'
        ],
        push: [
          { from: 'MRP 运算（采购类型）', arrow: '->', to: '采购申请单', desc: 'applyType=MRP采购、sourceType=MRP，草稿状态，可改数量/日期' },
          { from: 'MRP 运算（生产类型）', arrow: '->', to: '生产工单', desc: '属性含“半/成品”的物料下推生成草稿工单' }
        ],
        note: '提示：MRP 结果“建议类型”按物料类型字符串判断（含“原/外购”→采购，含“半/成”→生产），判断较为朴素，建议以物料档案属性为准；“销售预测”来源当前无数据加载逻辑，列表恒为空，属开发占位。'
      },
      {
        key: 'mrp-plan', title: 'MRP 运算方案',
        intro: 'MRP 运算方案的记录管理，可保存多个方案供不同场景复用，状态按 草稿→已生效→已执行 流转，执行后生成计划采购与计划生产建议。',
        fields: [
          { name: '方案编号 / 名称', desc: '运算方案唯一标识与名称' },
          { name: '运算日期 / 展望期', desc: '方案运算日期与计划展望期（天）' },
          { name: '需求来源', desc: '包含销售订单 / 预测等需求来源' },
          { name: '批量规则', desc: '逐批法 / 固定批量 / 经济批量 / 期间批量' },
          { name: '时界(天)', desc: 'MRP 运算的时间边界参数' },
          { name: '状态', desc: '草稿 / 已生效 / 已执行' }
        ],
        steps: [
          '在 MRP 运算方案列表点击【新增】或【编辑】创建方案',
          '配置运算范围开关（销售订单/预测/安全库存/在途采购/在制工单/现有库存）',
          '设置批量规则、展望期与时界参数后保存',
          '对方案执行【审核】后不可修改；【执行】将生成计划采购订单与计划生产工单'
        ],
        note: '注意：MRP 方案状态为 草稿→已生效→已执行 三段制；列表行内的 审核/执行 按钮需进入详情页操作（当前列表仅 查看/编辑/删除 可直达）。批量规则与范围开关在编辑页配置。'
      }
    ]
  },
  {
    name: 'exec', label: '生产执行单据', docs: [
      {
        key: 'receipt', title: '生产入库单',
        intro: '生产完工后的产品入库单据，由生产工单下推生成，记录合格品/不合格品数量，审核通过后增加库存。',
        fields: [
          { name: '入库单号 / 入库日期', desc: '唯一单号及入库业务日期' },
          { name: '生产订单号', desc: '来源生产工单单号（由工单下推带入）' },
          { name: '入仓仓库', desc: '生产入库的目标仓库' },
          { name: '数量', desc: '总数量 / 合格数量 / 不合格数量' },
          { name: '金额', desc: '入库总金额（明细数量×单价汇总）' },
          { name: '状态', desc: '草稿 / 已生效 / 已关闭 / 已取消' }
        ],
        steps: [
          '在列表点击【新增】（或从生产工单下推）填写入库日期与入仓仓库',
          '录入产品明细与数量（合格/不合格分开记录）',
          '保存后生成入库单号',
          '草稿状态点击【提交】进入审批；【审核】通过后增加库存'
        ],
        push: [
          { from: '生产工单', arrow: '->', to: '生产入库单', desc: '工单下推生成入库单，审核后增加库存' }
        ],
        note: '提示：生产入库单审核即增加库存；列表顶部“本月入库”统计当前为开发占位固定值。'
      },
      {
        key: 'return', title: '生产退料单', editShot: '新增/编辑生产退料单表单（点击放大）',
        intro: '将生产中剩余或报废的原料退回仓库（或退回供应商）的单据，由生产工单下推生成，审核后库存回补。',
        fields: [
          { name: '退料单号 / 退料日期', desc: '唯一单号及退料业务日期' },
          { name: '工单号', desc: '来源生产工单单号' },
          { name: '仓库', desc: '退料退回的目标仓库' },
          { name: '合计金额', desc: '退料明细金额汇总' },
          { name: '状态', desc: '草稿 / 已生效 / 已关闭 / 已取消' }
        ],
        steps: [
          '在列表点击【新增】（或从生产工单下推）填写退料日期与仓库',
          '录入退料明细（物料、数量、单位）',
          '保存后提交审核；【审核】通过后退料入库、库存回补'
        ],
        push: [
          { from: '生产工单', arrow: '->', to: '生产退料单', desc: '工单下推生成退料单，审核后回补库存' }
        ],
        note: '提示：退料单审核将把物料退回库存；当前列表行内审核动作需进入详情页操作。'
      },
      {
        key: 'supplement', title: '生产补料单', editShot: '新增/编辑生产补料单表单（点击放大）',
        intro: '生产过程中因损耗、报废导致原料不足时进行补充领料的单据，由生产工单下推生成，审核后扣减库存。',
        fields: [
          { name: '补料单号 / 补料日期', desc: '唯一单号及补料业务日期' },
          { name: '工单号 / 仓库', desc: '来源工单号与领出仓库' },
          { name: '合计金额', desc: '补料明细金额汇总' },
          { name: '状态', desc: '草稿 / 已生效 / 已关闭 / 已取消' }
        ],
        steps: [
          '在列表点击【新增】（或从生产工单下推）填写补料日期与仓库',
          '录入补料明细（物料、数量）',
          '保存后提交审核；【审核】通过后按 BOM 扣减库存'
        ],
        push: [
          { from: '生产工单', arrow: '->', to: '生产补料单', desc: '工单下推生成补料单，审核后扣减库存' }
        ],
        note: '提示：补料单关闭/取消需填写原因；当前列表行内审核动作需进入详情页操作。'
      },
      {
        key: 'operation-report', title: '工序汇报单（报工）', editShot: '新增/编辑工序汇报单表单（点击放大）',
        intro: '记录每道工序的实际完成情况（合格/报废数量与实际工时），用于生产进度跟踪，可由生产工单“快速报工”下推生成。',
        fields: [
          { name: '汇报单号 / 汇报日期', desc: '唯一单号与汇报日期' },
          { name: '工单号 / 工序', desc: '来源工单、工序号与工序名称' },
          { name: '工作中心 / 操作工', desc: '汇报工作中心与操作人员' },
          { name: '数量', desc: '合格数量 / 报废数量' },
          { name: '实际工时', desc: '工序实际耗用工时' },
          { name: '状态', desc: '草稿 / 待审核 / 已生效' }
        ],
        steps: [
          '从生产工单【快速报工】下推，或在列表【+ 新增】创建报工单',
          '填写工序、工作中心、操作工与合格/报废数量、实际工时',
          '保存后提交审核；快速报工生成的草稿单需到报工单详情再提交审核'
        ],
        push: [
          { from: '生产工单', arrow: '->', to: '工序汇报（报工）单', desc: '工单“快速报工”下推生成' }
        ],
        note: '提示：报工单状态为 草稿→待审核→已生效 标准流；列表行内提交/审核需进入详情页操作。'
      }
    ]
  },
  {
    name: 'assemble-dis', label: '组装与拆卸', docs: [
      {
        key: 'assemble', title: '生产组装单',
        intro: '将多个原料 BOM 组装成成品的单据，成品入成品仓、原料出原料仓，采用独立数字审批状态（草稿→待审批→生效/作废）。',
        fields: [
          { name: '单据编号 / 单据日期', desc: '唯一单号与业务日期' },
          { name: '生产部门', desc: '执行组装的部门' },
          { name: '成品仓库 / 原料仓库', desc: '成品入库仓库（wh_name）与原料出库仓库（wh_name_bom）' },
          { name: '成品明细 / 原料明细', desc: '成品（入库）来自物料库选择；BOM 原料（出库）手动添加' },
          { name: '单据金额', desc: '组装单金额汇总' },
          { name: '状态', desc: '草稿(0) / 待审批(1) / 生效(6) / 作废(3)' }
        ],
        steps: [
          '在组装单列表点击【新增】进入编辑页',
          '填写生产部门、成品仓库与原料仓库、单据日期',
          '添加成品（从物料库选择）与 BOM 原料明细（点击添加）',
          '保存后进入审批弹窗，可同意/驳回/重置草稿/作废',
          '审批通过（生效）后成品入库、原料出库'
        ],
        note: '提示：组装单采用数字审批态（草稿→待审批→生效）；审批在详情页通过审批弹窗操作，列表行内仅提供 查看/编辑/删除。顶部“本月组装”统计为开发占位固定值。'
      },
      {
        key: 'disassemble', title: '生产拆卸单', editShot: '新增/编辑拆卸单表单（点击放大）',
        intro: '将成品拆卸还原为原料（与组装相反）的单据，成品出库、原料入库，同样采用独立数字审批状态。',
        fields: [
          { name: '单据编号 / 单据日期', desc: '唯一单号与业务日期' },
          { name: '生产部门', desc: '执行拆卸的部门' },
          { name: '成品仓库 / 原料仓库', desc: '成品出库仓库与原料入库仓库' },
          { name: '成品明细 / 原料明细', desc: '成品（出库）与原料（入库）明细' },
          { name: '单据金额 / 状态', desc: '拆卸单金额汇总；草稿(0)/待审批(1)/生效(6)/作废(3)' }
        ],
        steps: [
          '在拆卸单列表点击【新增】进入编辑页',
          '填写生产部门、成品仓库与原料仓库、单据日期',
          '添加成品（出库）与原料（入库）明细',
          '保存后进入审批弹窗完成审批',
          '审批通过（生效）后成品出库、原料入库'
        ],
        note: '提示：拆卸单为组装单的反向操作；审批在详情页审批弹窗完成，列表行内仅提供 查看/编辑/删除。顶部“本月拆卸”统计为开发占位固定值。'
      }
    ]
  }
]
</script>

<style scoped>
.help-page { padding: 16px; }
.help-header { border-radius: 8px; background: linear-gradient(135deg, #fdf6ec 0%, #faecd8 100%); border: none; }
.help-title { display: flex; align-items: center; gap: 16px; }
.help-title h1 { margin: 0; font-size: 22px; color: #b88230; }
.help-title p { margin: 6px 0 0; color: #90601d; font-size: 14px; }

.help-section { padding: 4px 0; }
.help-section h3 { margin: 18px 0 10px; font-size: 16px; color: #303133; }
.feature-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 8px; }
@media (max-width: 1200px) { .feature-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 900px) { .feature-grid { grid-template-columns: repeat(2, 1fr); } }
.feature-card {
  border: 1px solid #EBEEF5; border-radius: 8px; padding: 16px; cursor: pointer;
  transition: all .25s ease; background: #fff;
}
.feature-card:hover { border-color: #E6A23C; box-shadow: 0 4px 14px rgba(230,162,60,.18); transform: translateY(-2px); }
.feature-icon { font-size: 28px; margin-bottom: 8px; }
.feature-card h4 { margin: 0 0 6px; font-size: 15px; color: #303133; }
.feature-card p { margin: 0 0 8px; font-size: 13px; color: #909399; }
.feature-go { font-size: 13px; color: #E6A23C; }

.flow-diagram { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 8px 0 4px; }
.flow-step { display: flex; align-items: center; gap: 6px; background: #fdf6ec; border-radius: 8px; padding: 8px 14px; }
.step-circle { width: 24px; height: 24px; border-radius: 50%; background: #E6A23C; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 13px; }
.step-text { font-size: 13px; color: #303133; }
.flow-arrow { color: #E6A23C; font-size: 16px; font-weight: bold; }

.op-table { margin: 8px 0; }

.doc-block { border-bottom: 1px dashed #DCDFE6; padding-bottom: 26px; }
.doc-title { font-size: 18px; color: #303133; margin: 10px 0 6px; }
.doc-intro { color: #606266; font-size: 14px; margin: 0 0 14px; }
.sub-title { font-size: 15px; color: #E6A23C; margin: 18px 0 8px; }
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
