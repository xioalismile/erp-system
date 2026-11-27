<template>
  <div class="help-page">
    <el-card shadow="never" class="help-header">
      <div class="help-title">
        <el-icon :size="32" color="#409EFF"><HelpFilled /></el-icon>
        <div>
          <h1>{{ $tt("基础资料模块操作手册") }}</h1>
          <p>{{ $tt("基础资料是整个ERP系统运行的数据基石，销售、采购、库存、财务等业务单据均引用此处维护的档案数据") }}</p>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px;">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ============ 快速入门 ============ -->
        <el-tab-pane :label="$tf('快速入门')" name="quickstart">
          <div class="help-section">
            <h3>{{ $tt("一、模块概览") }}</h3>
            <p>基础资料共包含 14 类档案，分为 5 大类：基础设置、组织与人员、仓储档案、物料档案、往来单位。业务单据开单时会自动引用这里维护的编码、名称、价格、账期等信息。</p>
            <div class="feature-grid">
              <div v-for="f in features" :key="f.label" class="feature-card" @click="goToDoc(f)">
                <div class="feature-icon">{{ f.icon }}</div>
                <h4>{{ $tf(f.label) }}</h4>
                <p>{{ f.desc }}</p>
                <div class="feature-go">查看手册 -></div>
              </div>
            </div>

            <h3>{{ $tt("二、推荐建档顺序") }}</h3>
            <div class="flow-diagram">
              <div class="flow-step"><div class="step-circle">1</div><div class="step-text">{{ $tt("计量单位/币种") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">2</div><div class="step-text">{{ $tt("部门/员工") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">3</div><div class="step-text">{{ $tt("仓库/货位") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">4</div><div class="step-text">{{ $tt("物料分类/物料") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">5</div><div class="step-text">{{ $tt("客户/供应商") }}</div></div>
            </div>
            <el-alert type="info" :closable="false" show-icon
              :title="$tt('先建基础档案（单位/币种/部门），再建业务档案（物料/客户/供应商）——后者的录入表单会引用前者的数据作为下拉选项。')" />

            <h3>{{ $tt("三、通用操作说明（所有档案页面一致）") }}</h3>
            <div class="op-table">
              <el-table :data="commonOps" border size="default">
                <el-table-column prop="op" :label="$tf('操作')" width="140" />
                <el-table-column prop="desc" :label="$tf('说明')" />
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ 各档案页 ============ -->
        <el-tab-pane v-for="group in groups" :key="group.name" :label="$tf(group.label)" :name="group.name">
          <div v-for="(doc, idx) in group.docs" :key="doc.key" :id="'doc-' + doc.key" class="doc-block">
            <h2 class="doc-title">{{ idx + 1 }}、{{ doc.title }}</h2>
            <p class="doc-intro">{{ doc.intro }}</p>

            <h4 class="sub-title">{{ $tt("页面总览（列表页）") }}</h4>
            <div class="shot-wrap">
              <el-image :src="'/manual/basedata/' + doc.key + '.png'"
                        :preview-src-list="['/manual/basedata/' + doc.key + '.png']"
                        fit="contain" hide-on-click-modal class="shot-img" />
              <div class="shot-tip">{{ $tt("点击图片可放大查看") }}</div>
            </div>

            <template v-if="doc.editShot">
              <h4 class="sub-title">{{ $tt("编辑页总览") }}</h4>
              <div class="shot-wrap">
                <el-image :src="'/manual/basedata/' + doc.key + '-edit.png'"
                          :preview-src-list="['/manual/basedata/' + doc.key + '-edit.png']"
                          fit="contain" hide-on-click-modal class="shot-img" />
                <div class="shot-tip">{{ doc.editShot }}</div>
              </div>
            </template>

            <h4 class="sub-title">{{ $tt("核心字段说明") }}</h4>
            <el-table :data="doc.fields" border size="small">
              <el-table-column prop="name" :label="$tf('字段')" width="150" />
              <el-table-column prop="desc" :label="$tf('说明')" />
            </el-table>

            <h4 class="sub-title">{{ $tt("操作步骤") }}</h4>
            <ol class="steps">
              <li v-for="s in doc.steps" :key="s">{{ s }}</li>
            </ol>

            <el-alert v-if="doc.note" :title="doc.note" type="warning" :closable="false" show-icon style="margin:12px 0 24px;" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { HelpFilled } from '@element-plus/icons-vue'

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
  { icon: '📐', label: '计量单位', desc: '长度/重量/数量等单位及换算率', group: 'basic', key: 'unit' },
  { icon: '💱', label: '币种', desc: '外币汇率与本位币设置', group: 'basic', key: 'currency' },
  { icon: '🧾', label: '结算方式', desc: '电汇/现金/承兑等结算类型', group: 'basic', key: 'settle' },
  { icon: '📅', label: '付款条件', desc: '账期天数/折扣/预付比例', group: 'basic', key: 'payment-term' },
  { icon: '🏷️', label: '价格等级', desc: '不同客户等级的价格调整率', group: 'basic', key: 'price-level' },
  { icon: '🏢', label: '部门档案', desc: '公司组织架构', group: 'org', key: 'department' },
  { icon: '👤', label: '员工档案', desc: '员工信息与业务归属', group: 'org', key: 'employee' },
  { icon: '🏭', label: '仓库档案', desc: '仓库类型/货位管理/MRP可用', group: 'wh', key: 'warehouse' },
  { icon: '📦', label: '货位档案', desc: '仓库内具体存放位置', group: 'wh', key: 'location' },
  { icon: '🗂️', label: '物料分类', desc: '多级物料分类树', group: 'material', key: 'material-category' },
  { icon: '🧱', label: '物料档案', desc: '物料主数据与购销价格', group: 'material', key: 'material' },
  { icon: '🧩', label: 'BOM', desc: '产品物料清单与多级子件构成', group: 'material', key: 'bom' },
  { icon: '🤝', label: '客户档案', desc: '客户类型/等级/联系人', group: 'partner', key: 'customer' },
  { icon: '🚚', label: '供应商档案', desc: '供应商类型/等级/联系人', group: 'partner', key: 'supplier' }
]

const commonOps = [
  { op: '查询 / 重置', desc: '顶部搜索框支持按"编码/名称"模糊查询（物料、客户、供应商还支持简称），点击【查询】过滤，【重置】清空条件' },
  { op: '新增', desc: '点击左上角【新增】按钮打开编辑页，编码留空时系统按编码规则自动生成' },
  { op: '修改 / 编辑', desc: '列表勾选一条记录后点【修改】，或直接点行内【编辑】按钮' },
  { op: '删除', desc: '勾选记录后点【删除】，已被业务单据引用的档案不允许删除' },
  { op: '导入 / 导出', desc: '支持 Excel 批量导入与导出；物料分类页提供专门的【下载导入模板】' },
  { op: '状态列', desc: '启用状态的档案才能在业务单据中被选用；停用后仅历史单据保留引用' }
]

const groups = [
  {
    name: 'basic', label: '基础设置', docs: [
      {
        key: 'unit', title: '计量单位',
        intro: '维护系统使用的计量单位及同组单位间的换算关系，销售/采购单据的数量单位均从此处选择。',
        fields: [
          { name: '单位编码 / 名称', desc: '如 PC/个、KG/千克，编码唯一' },
          { name: '单位组', desc: '同组单位之间按换算率互算，如"重量组"包含克/千克/吨' },
          { name: '换算率', desc: '相对基本单位的倍数，如千克相对吨 = 0.001' },
          { name: '基本单位', desc: '每个单位组的基本计量单位，库存核算按基本单位换算' }
        ],
        steps: ['点击【新增】进入编辑页', '选择或新建单位组，填写单位编码、名称', '填写相对基本单位的换算率', '保存后即可在单据中选择该单位'],
        editShot: '编辑页：录入单位编码/名称，选择单位类别并填写换算率',
        note: '提示：换算率影响库存与金额的换算口径，已产生业务的单位不建议修改换算率。'
      },
      {
        key: 'currency', title: '币种',
        intro: '维护外币币种及对账汇率，本位币是系统核算的基准货币。',
        fields: [
          { name: '币种编码 / 名称 / 符号', desc: '如 USD/美元/$' },
          { name: '汇率', desc: '相对本位币的折算汇率，外币单据金额按此换算' },
          { name: '本位币', desc: '全系统仅一个本位币（如 CNY），决定账务核算基准' }
        ],
        steps: ['点击【新增】填写币种信息', '录入当前汇率', '如为本位币勾选"本位币"标记', '保存后外币单据即可选用'],
        editShot: '编辑页：录入币种编码/名称/符号与汇率，可勾选"本位币"',
        note: '提示：汇率变动时直接编辑币种记录更新汇率即可，历史单据按开单时汇率保留。'
      },
      {
        key: 'settle', title: '结算方式',
        intro: '维护收付款的结算方式，供销售收款、采购付款等单据选择。',
        fields: [
          { name: '结算编码 / 名称', desc: '如 DDFZ/电汇、XJ/现金、CD/承兑' },
          { name: '结算类型', desc: '按业务场景归类，便于资金报表分类统计' },
          { name: '排序', desc: '控制下拉选项中的显示顺序' }
        ],
        steps: ['点击【新增】录入结算编码与名称', '选择结算类型，设置排序值', '保存后在收付款单据中即可选用'],
        editShot: '编辑页：录入结算编码/名称，选择结算类型'
      },
      {
        key: 'price-level', title: '价格等级',
        intro: '维护价格等级及相对基准价的调整率，实现同一物料按客户等级差异化定价。',
        fields: [
          { name: '价格等级编码 / 名称', desc: '如 A/一级批发、B/二级批发' },
          { name: '调整率%', desc: '相对基准售价的上浮/下调百分比，如 95 表示 95 折' },
          { name: '排序', desc: '下拉选项显示顺序' }
        ],
        steps: ['点击【新增】录入等级信息', '填写调整率（低于 100 为打折）', '保存后在物料价格策略及客户档案中选用'],
        editShot: '编辑页：录入价格等级编码/名称与调整率'
      }
    ]
  },
  {
    name: 'org', label: '组织与人员', docs: [
      {
        key: 'department', title: '部门档案',
        intro: '维护公司组织架构，员工档案、业务数据权限按部门归属。',
        fields: [
          { name: '部门编码 / 名称', desc: '组织唯一标识，如 XS/销售部' },
          { name: '部门类型', desc: '区分业务/职能部门，用于报表分类' }
        ],
        steps: ['点击【新增】录入部门信息', '选择部门类型', '保存后在员工档案中归属该部门'],
        editShot: '编辑页：录入部门编码/名称，选择部门类型与上级部门'
      },
      {
        key: 'employee', title: '员工档案',
        intro: '维护员工基础信息，销售/采购人员开单及业绩统计均以员工为依据。',
        fields: [
          { name: '员工编码 / 姓名', desc: '业务人员唯一标识' },
          { name: '性别 / 手机 / 邮箱', desc: '联系方式，用于单据打印与消息通知' }
        ],
        steps: ['点击【新增】录入员工信息', '填写联系方式', '保存后在单据的"业务员"字段中选择'],
        editShot: '编辑页：录入员工编码/姓名/联系方式，可选填登录账号（留空则不创建账号）',
        note: '提示：员工的部门归属在员工编辑页中选择；离职员工请"停用"而非删除，以保留历史单据业绩归属。'
      }
    ]
  },
  {
    name: 'wh', label: '仓储档案', docs: [
      {
        key: 'warehouse', title: '仓库档案',
        intro: '维护仓库信息，出入库单据、库存查询、MRP 运算均按仓库维度进行。',
        fields: [
          { name: '仓库编码 / 名称', desc: '如 CK01/成品仓' },
          { name: '仓库类型', desc: '原料仓/成品仓/不良品仓等分类' },
          { name: '货位管理', desc: '开启后该仓库出入库需选择具体货位' },
          { name: 'MRP可用', desc: '勾选后该仓库库存参与 MRP 物料需求运算的可用量' }
        ],
        steps: ['点击【新增】录入仓库信息', '按需勾选"货位管理"与"MRP可用"', '保存后出入库单据即可选择该仓库'],
        editShot: '编辑页：录入仓库编码/名称/地址，勾选"货位管理"与"MRP可用"开关',
        note: '提示："货位管理"开启后需先维护货位档案才能收发货；"MRP可用"决定该仓库存是否算作需求可用量。'
      },
      {
        key: 'location', title: '货位档案',
        intro: '维护开启货位管理的仓库内的具体存放位置，实现精细到货位的库存管理。',
        fields: [
          { name: '货位编码 / 名称', desc: '如 A-01-01 表示 A 区 01 排 01 层' },
          { name: '所属仓库', desc: '货位必须挂靠在某个仓库下' }
        ],
        steps: ['先在仓库档案中开启目标仓库的"货位管理"', '进入货位档案点击【新增】', '选择所属仓库，录入货位编码', '保存后该仓库的出入库单据可选择此货位'],
        editShot: '编辑页：选择所属仓库，录入货位编码/名称'
      }
    ]
  },
  {
    name: 'material', label: '物料档案', docs: [
      {
        key: 'material-category', title: '物料分类',
        intro: '以树形结构维护物料分类，物料档案必须归属到末级分类，支持 Excel 批量导入。',
        fields: [
          { name: '分类编码 / 名称', desc: '分类唯一标识，如 01/原材料' },
          { name: '层级', desc: '支持多级分类，如 原材料 > 五金 > 螺丝' },
          { name: '排序', desc: '同级分类的显示顺序' }
        ],
        steps: ['点击【+ 新增顶级分类】创建一级分类', '选中某分类后点【新增子分类】创建下级', '批量建档可【下载导入模板】填写后【导入分类】', '删除分类前需确保分类下无物料'],
        editShot: '编辑页：录入分类编码/名称，选择上级分类（留空表示顶级分类）'
      },
      {
        key: 'material', title: '物料档案',
        intro: '物料主数据是 ERP 的核心档案，进销存及生产业务全部围绕物料展开，支持图片、规格型号及购销双价维护。',
        fields: [
          { name: '物料编码 / 名称', desc: '物料唯一标识，编码留空自动按规则生成' },
          { name: '规格型号', desc: '规格描述，同物料不同规格需分别建档' },
          { name: '物料类型', desc: '原材料/成品/半成品等属性，决定业务流向' },
          { name: '销售单价 / 采购单价', desc: '默认价格，销售/采购订单开单时自动带出' },
          { name: '图片', desc: '物料图片，列表与单据中直观辨认' }
        ],
        steps: ['先维护好物料分类与计量单位', '点击【新增】选择物料分类，录入名称、规格', '选择物料类型与计量单位', '维护销售/采购单价与图片', '保存后即可在所有业务单据中选择'],
        editShot: '编辑页分 5 个分区：基本信息/价格与库存/控制选项/产品图片/信息确认；产品图片支持多图上传与主图设置，选择分类后自动生成物料编码',
        note: '提示：物料被单据引用后编码不可修改；价格调整可后续编辑，历史单据按开单时价格保留。'
      },
      {
        key: 'bom', title: 'BOM（物料清单）',
        intro: 'BOM（Bill of Materials）是产品（母件）与所需原材料/半成品（子件）的组成清单。生产工单、MRP物料需求、成本核算均以 BOM 为依据展开；BOM 属于带审批流程的单据。',
        fields: [
          { name: '单据编号 / 日期', desc: '编号保存后自动生成，日期默认当天' },
          { name: 'BOM分类', desc: '标准/工程/生产/成本/委外BOM，按用途归类' },
          { name: '母件物料', desc: 'BOM 的产出品（成品/半成品），必填；同物料可维护多版本' },
          { name: '数量 / 损耗率%', desc: '母件产量基准数量及生产损耗比例' },
          { name: 'BOM明细（子件）', desc: '子件编码/名称、用量、用量类型（变动/固定）、损耗率%、供应类型（领料/倒冲/虚拟件/委外）' },
          { name: '默认BOM', desc: '同一物料存在多版本时，勾选其中一个作为默认版本' },
          { name: '启用状态', desc: '启用后才能在工单/MRP/委外等业务中被引用' },
          { name: '单据状态', desc: '草稿→待审批→已生效；已生效等同生效，可重置草稿/停用' }
        ],
        steps: [
          '点击【新增】进入 BOM 编辑页，选择母件物料，填写基本单位与数量',
          '在"BOM明细"中点击【从物料库选择】或【添加子件】录入子件物料',
          '维护每个子件的用量、用量类型、损耗率、供应类型等属性',
          '保存后为草稿状态；点击【提交审核】进入审批，审核通过后状态变为"已生效"',
          '点击【BOM展开】查看该 BOM 的全部层级子件构成（半成品显示其下级BOM编号）',
          '同物料多版本可用"默认BOM"指定生效版本；大批量建档可用【多级导入】'
        ],
        editShot: '编辑页：选择母件物料与基本单位/数量后，在"BOM明细"中添加子件并维护用量/损耗率/供应类型；子件图片自动从物料档案带出，半成品子件显示其下级BOM编号',
        note: '提示：BOM 属于审批单据，被生产/委外单据引用后请勿删除；停用后不再参与 MRP 与生产运算。'
      }
    ]
  },
  {
    name: 'partner', label: '往来单位', docs: [
      {
        key: 'customer', title: '客户档案',
        intro: '维护客户主数据，销售报价、订单、发货、应收账款均以客户档案为往来依据，销售订单页还支持"+"快捷新增客户。',
        fields: [
          { name: '客户编码 / 名称 / 简称', desc: '编码唯一，简称用于快速检索' },
          { name: '客户类型', desc: '经销商/直销/终端等分类' },
          { name: '客户等级', desc: '关联价格等级，决定默认售价水平' },
          { name: '联系人 / 联系电话', desc: '下单、发货通知的联系信息' }
        ],
        steps: ['点击【新增】录入客户信息（编码自动生成）', '选择客户类型与等级，填写联系人', '保存后在销售单据中选择该客户'],
        editShot: '编辑页：录入客户编码/名称/简称与联系人信息，选择客户类型/等级/付款条件等',
        note: '提示：客户等级对应"价格等级"档案的调整率，开单价格按等级自动取价。'
      },
      {
        key: 'supplier', title: '供应商档案',
        intro: '维护供应商主数据，采购订单、进货、应付账款均以供应商档案为往来依据。',
        fields: [
          { name: '供应商编码 / 名称 / 简称', desc: '编码唯一，简称用于快速检索' },
          { name: '供应商类型', desc: '生产型/贸易型等分类' },
          { name: '供应商等级', desc: '用于供应商分级管理' },
          { name: '联系人 / 联系电话', desc: '下单、对账的联系信息' }
        ],
        steps: ['点击【新增】录入供应商信息（编码自动生成）', '选择供应商类型与等级，填写联系人', '保存后在采购单据中选择该供应商'],
        editShot: '编辑页：录入供应商编码/名称/简称与联系人信息，选择供应商类型/等级/付款条件等'
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

.doc-block { padding: 4px 8px; scroll-margin-top: 70px; }
.doc-highlight { animation: docFlash 2.2s ease; }
@keyframes docFlash {
  0% { background: rgba(64,158,255,.16); box-shadow: inset 4px 0 0 #409EFF; }
  60% { background: rgba(64,158,255,.10); box-shadow: inset 4px 0 0 #409EFF; }
  100% { background: transparent; box-shadow: none; }
}

.flow-diagram { display: flex; align-items: center; justify-content: center; gap: 10px; flex-wrap: wrap; margin: 16px 0; }
.flow-step { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.step-circle { width: 28px; height: 28px; border-radius: 50%; background: #409EFF; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 14px; }
.step-text { color: #606266; font-size: 13px; }
.flow-arrow { color: #c0c4cc; }

.doc-block { padding: 4px 8px; }
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
