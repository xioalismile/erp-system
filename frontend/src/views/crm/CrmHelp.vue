<template>
  <div class="help-page">
    <el-card shadow="never" class="help-header">
      <div class="help-title">
        <el-icon :size="32" color="#409EFF"><HelpFilled /></el-icon>
        <div>
          <h1>{{ $tt("客户管理模块操作手册") }}</h1>
          <p>{{ $tt("客户管理（CRM）覆盖客户从公海领取、线索转化、商机推进到成交服务全流程，帮助销售团队高效经营客户资源") }}</p>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px;">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ============ 快速入门 ============ -->
        <el-tab-pane :label="$tf('快速入门')" name="quickstart">
          <div class="help-section">
            <h3>{{ $tt("一、模块概览") }}</h3>
            <p>{{ $tt("客户管理模块共包含 13 个功能页，分为 4 大类：客户资源、销售推进、客户服务、目标报表。点击下方任一功能卡片可直达对应说明位置。") }}</p>
            <div class="feature-grid">
              <div v-for="f in features" :key="f.label" class="feature-card" @click="goToDoc(f)">
                <div class="feature-icon">{{ f.icon }}</div>
                <h4>{{ $tf(f.label) }}</h4>
                <p>{{ f.desc }}</p>
                <div class="feature-go">查看手册 -></div>
              </div>
            </div>

            <h3>{{ $tt("二、推荐业务流转") }}</h3>
            <div class="flow-diagram">
              <div class="flow-step"><div class="step-circle">1</div><div class="step-text">{{ $tt("录入线索") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">2</div><div class="step-text">{{ $tt("领取/创建客户") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">3</div><div class="step-text">{{ $tt("跟进登记") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">4</div><div class="step-text">{{ $tt("转化商机") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">5</div><div class="step-text">{{ $tt("成交/售后") }}</div></div>
            </div>
            <el-alert type="info" :closable="false" show-icon
              :title="$tt('线索可【转化】为正式客户自动进入私海；公海客户可【领取】到私海跟进；跟进记录与商机推进是客户经营的核心动作。')" />

            <h3>{{ $tt("三、通用操作说明（所有 CRM 页面一致）") }}</h3>
            <div class="op-table">
              <el-table :data="commonOps" border size="default">
                <el-table-column prop="op" :label="$tf('操作')" width="140" />
                <el-table-column prop="desc" :label="$tf('说明')" />
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ 各功能页 ============ -->
        <el-tab-pane v-for="group in groups" :key="group.name" :label="$tf(group.label)" :name="group.name">
          <div v-for="(doc, idx) in group.docs" :key="doc.key" :id="'doc-' + doc.key" class="doc-block">
            <h2 class="doc-title">{{ idx + 1 }}、{{ doc.title }}</h2>
            <p class="doc-intro">{{ doc.intro }}</p>

            <h4 class="sub-title">{{ $tt("页面总览（列表页）") }}</h4>
            <div class="shot-wrap">
              <el-image :src="'/manual/crm/' + doc.key + '.png'"
                        :preview-src-list="['/manual/crm/' + doc.key + '.png']"
                        fit="contain" hide-on-click-modal class="shot-img" />
              <div class="shot-tip">{{ $tt("点击图片可放大查看") }}</div>
            </div>

            <template v-if="doc.editShot">
              <h4 class="sub-title">{{ $tt("新增 / 编辑界面") }}</h4>
              <div class="shot-wrap">
                <el-image :src="'/manual/crm/' + doc.key + '-dialog.png'"
                          :preview-src-list="['/manual/crm/' + doc.key + '-dialog.png']"
                          fit="contain" hide-on-click-modal class="shot-img" />
                <div class="shot-tip">{{ doc.editShot }}</div>
              </div>
            </template>
            <el-alert v-else-if="doc.noEditNote" :title="doc.noEditNote" type="info" :closable="false" show-icon style="margin:12px 0;" />

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
  { icon: '📊', label: 'CRM仪表盘', desc: '漏斗/客户分布/跟进待办总览', group: 'resources', key: 'dashboard' },
  { icon: '🏠', label: '我的私海', desc: '名下客户资源池与经营', group: 'resources', key: 'customer' },
  { icon: '🌊', label: '公海池', desc: '共享客户领取/批量分配', group: 'resources', key: 'pool' },
  { icon: '⏰', label: '跟进超期预警', desc: '超期未跟进客户提醒', group: 'resources', key: 'warning' },
  { icon: '🎯', label: '销售线索', desc: '线索录入/跟进/转化', group: 'pipeline', key: 'lead' },
  { icon: '💎', label: '商机管理', desc: '销售阶段与赢率推进', group: 'pipeline', key: 'opportunity' },
  { icon: '📉', label: '销售漏斗', desc: '商机阶段分布可视化', group: 'pipeline', key: 'funnel' },
  { icon: '👤', label: '联系人管理', desc: '客户联系人档案', group: 'service', key: 'contact' },
  { icon: '📞', label: '跟进记录', desc: '沟通过程留痕与回访', group: 'service', key: 'follow-up' },
  { icon: '⚠️', label: '客户投诉', desc: '投诉登记/指派/处理', group: 'service', key: 'complaint' },
  { icon: '⭐', label: '满意度调查', desc: '多维评分与回访分析', group: 'service', key: 'survey' },
  { icon: '🎯', label: '销售目标', desc: '年度目标分配与达成', group: 'target', key: 'sales-target' },
  { icon: '📈', label: '报表中心', desc: '漏斗/业绩/回款报表', group: 'target', key: 'report' }
]

const commonOps = [
  { op: '查询 / 重置', desc: '顶部搜索框支持按"编码/名称/联系人/电话"等模糊查询，点击【查询】过滤，【重置】清空条件' },
  { op: '新增', desc: '点击【+ 新增客户 / 新增线索 / 新增商机】等按钮进入新增页，编码类字段保存后自动生成' },
  { op: '查看 / 编辑', desc: '列表行内或勾选后提供【查看】【编辑】按钮，编辑 CRM 状态、销售阶段、意向等级等' },
  { op: '跟进', desc: '行内【跟进】按钮登记沟通记录，系统自动更新最后联系日期与跟进次数' },
  { op: '更多', desc: '行内【更多】菜单可快速执行释放到公海、转移、设为置顶等操作' },
  { op: '导出 / 导入', desc: '支持 Excel 批量导出与导入；公海池/线索/投诉等页提供导入模板下载' }
]

const groups = [
  {
    name: 'resources', label: '客户资源', docs: [
      {
        key: 'dashboard', title: 'CRM仪表盘',
        intro: '客户管理模块的首页总览，集中展示销售漏斗分布、客户 CRM 状态分布与跟进待办，帮助快速掌握客户经营全局。',
        fields: [
          { name: '销售漏斗（商机阶段分布）', desc: '按销售阶段统计商机数量与金额，点击【进入看板】查看漏斗详情' },
          { name: '客户 CRM 状态分布', desc: '按潜在/意向/正式/休眠/已流失统计客户数量，直观了解客户结构' },
          { name: '跟进待办（逾期/今日）', desc: '列出超期未跟进与今日应跟进的客户，点击【查看全部】进入跟进记录；【全部已读】清除提醒' }
        ],
        steps: ['登录后进入 CRM 模块首页，即展示仪表盘', '查看销售漏斗分布，掌握各阶段商机数量', '查看客户状态分布，识别流失/休眠风险客户', '处理跟进待办：逾期客户优先回访，登记跟进记录'],
        noEditNote: '该页面为数据看板，无需新增/编辑操作；数据来自商机、客户与跟进记录的实时统计。',
        note: '提示：仪表盘数据来自商机、客户与跟进记录的实时统计，无需手工维护。'
      },
      {
        key: 'customer', title: '我的私海',
        intro: '每个销售员专属的客户资源池，从公海领取的客户与直接创建的客户都会进入私海，是日常客户经营的主阵地。',
        fields: [
          { name: '客户编码 / 名称', desc: '客户唯一标识，编码保存后自动生成，支持按名称/简称/联系人/电话搜索' },
          { name: '归属', desc: '客户所属销售员（当前登录人），决定数据查看范围' },
          { name: 'CRM状态', desc: '潜在/意向/正式/休眠/已流失，反映客户当前经营阶段' },
          { name: '销售阶段', desc: '初步接触/需求确认/方案报价/商务谈判/成交，与商机阶段联动' },
          { name: '意向等级 / 重要程度 / 满意度', desc: 'A/B/C/D 意向分级，重要程度与满意度标记便于跟进优先级排序' },
          { name: '跟进次数 / 最后联系 / 下次联系', desc: '跟进记录自动累计，下次联系日期到期触发预警' },
          { name: '订单数 / 累计订单金额', desc: '与销售模块联动的成交统计' },
          { name: '黑名单', desc: '标记黑名单后该客户不再参与公海领取等自动流转' }
        ],
        steps: [
          '点击【+ 新增客户】录入客户信息，或从公海池领取客户进入私海',
          '选择客户类型、等级，填写联系人信息，保存后客户进入私海',
          '行内【跟进】登记沟通记录，系统自动更新最后联系日期',
          '通过【更多】菜单执行释放到公海、转移归属、设为置顶等操作',
          '可对客户批量【全选】后执行状态/等级批量更新'
        ],
        editShot: '新增客户弹窗：录入客户编码/名称/联系人/电话/邮箱，选择客户类型/等级/CRM状态/销售阶段与标签',
        note: '提示：私海仅显示当前登录人归属的客户；管理员拥有全部数据权限可查看所有客户。'
      },
      {
        key: 'pool', title: '公海池',
        intro: '公司共享的客户资源池，未分配、被释放或长期未跟进的客户都会进入公海池，销售员可领取跟进，避免客户资源闲置。',
        fields: [
          { name: '客户编号 / 名称 / 联系人', desc: '支持按编号/名称/联系人/电话检索公海客户' },
          { name: '进入公海日期 / 公海天数', desc: '记录客户进入公海的时间与停留天数' },
          { name: '最后跟进日期', desc: '进入公海前的最后跟进时间' },
          { name: '原归属人', desc: '释放前归属的销售员，便于追溯' }
        ],
        steps: [
          '在公海池列表定位目标客户，点击行内【领取】进入您的私海',
          '管理员可勾选多个客户后【批量分配】给指定销售员，或【一键分配全部】',
          '超过公海天数阈值的客户支持【立即执行自动回收】重新回流',
          '可通过【导出】【导入】批量维护公海客户，导入前先【下载导入模板】'
        ],
        noEditNote: '公海池无新增入口：客户来源为私海释放或线索转化；本页操作以【领取】【批量分配】【一键分配全部】为主。',
        note: '提示：领取后客户自动进入私海开始跟进；私海客户超期未跟进会自动退回公海，保持客户资源高效流转。'
      },
      {
        key: 'warning', title: '跟进超期预警',
        intro: '自动扫描超过预警天数未跟进的客户，集中提醒销售员及时回访，防止客户流失。',
        fields: [
          { name: '客户编号 / 名称', desc: '超期未跟进的客户' },
          { name: '最后跟进日期 / 超过天数', desc: '距上次跟进的间隔天数，超过配置阈值即预警' },
          { name: '分配销售员', desc: '负责跟进该客户的销售员' },
          { name: '预警类型', desc: '区分常规超期与到期应跟进的预警场景' }
        ],
        steps: [
          '进入跟进超期预警页查看超期客户列表',
          '点击【预警配置】设置预警天数等规则',
          '点击【刷新】重新计算最新超期数据',
          '对预警客户逐一【查看】并登记跟进记录，处理后自动移出预警'
        ],
        noEditNote: '预警列表为系统自动扫描生成，无新增/编辑操作；点击【预警配置】跳转至 系统设置-业务参数 调整预警天数等规则。',
        note: '提示：当列表为空说明当前无超期未跟进的客户；预警配置按公司跟进策略统一设置。'
      }
    ]
  },
  {
    name: 'pipeline', label: '销售推进', docs: [
      {
        key: 'lead', title: '销售线索',
        intro: '记录潜在客户线索信息，是销售机会的起点。线索可通过市场活动、客户介绍、网络搜索等渠道获取，跟进成熟后可转化为正式客户。',
        fields: [
          { name: '线索编号', desc: '保存后自动生成，如 LD-20260825-0001' },
          { name: '线索名称 / 公司名称', desc: '线索主信息与所属公司' },
          { name: '联系人 / 联系电话', desc: '线索决策人或联系人信息' },
          { name: '线索类型', desc: '采购意向/咨询服务/升级改造等' },
          { name: '意向等级', desc: 'A/B/C/D 意向分级，决定跟进优先级' },
          { name: '线索状态', desc: '新线索/跟进中/已转化/已放弃，随操作自动流转' },
          { name: '跟进次数 / 最后跟进 / 下次跟进', desc: '跟进记录自动累计，下次跟进到期触发提醒' },
          { name: '图片', desc: '可上传线索相关图片资料' }
        ],
        steps: [
          '点击【+ 新增线索】录入线索信息（线索名称/公司/联系人/来源等）',
          '行内【跟进】登记沟通内容，线索状态自动进入"跟进中"',
          '跟进成熟后点击【转化】按钮，系统自动创建客户档案并进入私海，状态变为"已转化"',
          '无成交价值的线索可点击【更多】选择放弃并填写原因，状态变为"已放弃"',
          '大批量线索可通过【导出】【导入】维护'
        ],
        editShot: '新增线索弹窗：录入线索名称/公司名称/联系人/电话，选择线索类型/意向等级/来源类型/所属行业',
        note: '提示：线索转化是客户经营的关键动作，转化后请及时在私海继续跟进并推进商机。'
      },
      {
        key: 'opportunity', title: '商机管理',
        intro: '管理有明确购买意向的销售机会，通过销售阶段与赢率推进跟踪，帮助销售团队把握每个成交机会。',
        fields: [
          { name: '商机编号', desc: '保存后自动生成，如 OPP-20260825-0001' },
          { name: '商机名称 / 客户', desc: '商机内容与关联客户（必须关联已有客户）' },
          { name: '预计金额 / 赢率', desc: '预期成交金额与当前阶段对应赢率（自动映射）' },
          { name: '销售阶段', desc: '初步接触→需求确认→方案报价→商务谈判→成交/已关闭，随阶段推进赢率自动调整' },
          { name: '预计成交日期', desc: '预期的成交时间节点' },
          { name: '跟进次数 / 最后跟进', desc: '商机跟进记录统计' },
          { name: '关闭状态', desc: '已关闭商机标记赢单/丢单，关闭后不可修改' }
        ],
        steps: [
          '点击【新增商机】选择关联客户，填写商机名称、预计金额、预计成交日期',
          '随沟通推进点击【更改阶段】更新销售阶段，赢率自动映射',
          '行内【跟进】登记商机沟通记录，保持阶段信息同步',
          '成交后在行内点击【转销售订单】生成销售订单；无法成交时点击【关闭】标记丢单',
          '可通过【导出】【导入】批量维护商机'
        ],
        editShot: '新增商机弹窗：录入商机名称、选择关联客户，填写预计金额/预计成交日期，分配销售员与部门',
        note: '提示：阶段与赢率联动--初步接触 10%、需求确认 25%、方案报价 45%、商务谈判 70%、成交 100%。'
      },
      {
        key: 'funnel', title: '销售漏斗',
        intro: '按销售阶段可视化展示商机数量与金额分布，直观呈现商机转化漏斗，帮助识别推进瓶颈。',
        fields: [
          { name: '销售阶段分布', desc: '各阶段商机数量与金额占比，形成漏斗形态' },
          { name: '进入商机管理', desc: '一键跳转商机列表，查看具体商机明细' }
        ],
        steps: [
          '进入销售漏斗页查看各阶段商机分布',
          '对比各阶段商机数量，定位转化薄弱环节',
          '点击【进入商机管理】下钻查看并推进具体商机',
          '点击【刷新】更新最新漏斗数据'
        ],
        noEditNote: '该页面为可视化看板，无需新增/编辑操作；点击【进入商机管理】下钻推进具体商机。',
        note: '提示：漏斗底部越粗说明该阶段商机沉淀越多，需重点推进阶段流转。'
      }
    ]
  },
  {
    name: 'service', label: '客户服务', docs: [
      {
        key: 'contact', title: '联系人管理',
        intro: '维护客户名下多个联系人档案，一个客户可拥有多个联系人（如采购、财务、技术），并支持设置主联系人。',
        fields: [
          { name: '编号 / 姓名', desc: '联系人编号自动生成，姓名必填' },
          { name: '性别 / 职务 / 部门', desc: '联系人身份信息' },
          { name: '手机 / 座机 / 邮箱', desc: '联系方式，用于沟通与通知' },
          { name: '所属客户', desc: '联系人归属的客户档案' },
          { name: '主联系人', desc: '同一客户可设置一个主联系人作为默认对接人' }
        ],
        steps: [
          '点击【+ 新增联系人】选择所属客户，填写姓名、职务、联系方式',
          '点击【设为主联系人】指定客户默认对接人',
          '行内【编辑】【删除】维护联系人档案',
          '按姓名/手机/邮箱/客户名称搜索定位联系人'
        ],
        editShot: '新增联系人弹窗：选择所属客户，录入姓名/性别/部门/职务/手机/邮箱，可勾选"是否主联系人"',
        note: '提示：建议为客户至少维护一个主联系人，开单与通知时优先引用主联系人信息。'
      },
      {
        key: 'follow-up', title: '跟进记录',
        intro: '登记与客户/线索/商机的每一次沟通，形成完整跟进轨迹，支持按来源（客户/线索/商机）关联查看。',
        fields: [
          { name: '跟进单号', desc: '保存后自动生成' },
          { name: '来源类型 / 来源名称', desc: '跟进对象类型（客户/线索/商机）及其名称' },
          { name: '跟进日期 / 跟进方式', desc: '跟进时间与方式（电话/上门/微信等）' },
          { name: '联系人 / 跟进人', desc: '本次沟通的联系人与执行人' },
          { name: '跟进摘要 / 重要程度', desc: '沟通结论摘要与重要程度标记' },
          { name: '下次跟进日期', desc: '到期未跟进将触发超期预警' },
          { name: '附件', desc: '可上传沟通相关附件' }
        ],
        steps: [
          '在客户/线索/商机行内点击【跟进】，或进入跟进记录页点击【+ 新增跟进】',
          '选择来源类型与对象，填写跟进方式、内容、摘要与结果',
          '设置下次跟进日期，系统自动更新对应对象的最后跟进日期与跟进次数',
          '行内【编辑】【删除】维护跟进记录',
          '按跟进单号/来源名称/联系人/摘要搜索定位历史跟进'
        ],
        editShot: '新增跟进弹窗：选择来源类型（客户/线索/商机）与对象后，录入跟进方式/跟进内容/摘要/结果与下次跟进日期',
        note: '提示：跟进记录会同步更新客户/线索/商机的"最后跟进/下次跟进"字段，是超期预警的数据来源。'
      },
      {
        key: 'complaint', title: '客户投诉',
        intro: '登记客户投诉信息，支持指派处理人、登记处理结果与满意度评分，实现投诉闭环管理。',
        fields: [
          { name: '投诉单号', desc: '保存后自动生成，如 C-20260825-0001' },
          { name: '客户名称 / 联系人', desc: '投诉客户与联系人' },
          { name: '投诉日期 / 投诉类型', desc: '投诉时间与类型（产品质量/服务态度/物流问题/售后问题/价格问题等）' },
          { name: '投诉渠道 / 严重程度', desc: '投诉来源渠道与严重等级（高/中/低）' },
          { name: '处理状态 / 处理日期', desc: '待处理/处理中/已解决等状态流转' },
          { name: '满意度', desc: '处理完成后的客户满意度评分' }
        ],
        steps: [
          '点击【+ 新增投诉】登记客户、投诉类型、内容与严重程度',
          '点击【指派】指定处理人负责跟进',
          '处理完成后点击【处理/解决】登记处理结果',
          '客户确认后点击【关闭】并录入满意度评分，形成闭环',
          '可通过【导出】【导入】批量维护投诉记录'
        ],
        editShot: '新增投诉弹窗：选择投诉客户，录入联系人/电话、投诉类型/渠道/严重程度与投诉内容',
        note: '提示：投诉处理状态与满意度是客户服务质量的重要指标，建议及时处理并回访。'
      },
      {
        key: 'survey', title: '满意度调查',
        intro: '对客户进行多维满意度调查，涵盖产品/服务/价格/交付/售后等维度评分，并自动生成分析结果。',
        fields: [
          { name: '调查单号 / 调查名称', desc: '调查记录唯一编号与名称' },
          { name: '客户名称 / 联系人', desc: '被调查客户与联系人' },
          { name: '调查日期 / 调查类型 / 调查方式', desc: '调查时间、类型（交易后/定期回访等）与方式' },
          { name: '综合评分', desc: '各维度评分的综合结果' },
          { name: '状态 / 分析结果', desc: '调查状态与系统生成的分析结论' }
        ],
        steps: [
          '点击【+ 新增调查】选择客户，填写调查类型与方式',
          '录入产品/服务/价格/交付/售后等维度评分',
          '保存后系统自动计算综合评分并生成分析结果',
          '行内【编辑】或【更多】维护调查记录',
          '通过【导出】【导入】批量维护调查数据'
        ],
        editShot: '新增调查弹窗：选择调查客户，录入调查名称/类型/方式与产品/服务/价格/交期/售后各维度评分',
        note: '提示：综合评分由各维度评分自动汇总，分析结果辅助识别客户满意度短板。'
      }
    ]
  },
  {
    name: 'target', label: '目标报表', docs: [
      {
        key: 'sales-target', title: '销售目标',
        intro: '制定年度销售目标并分配到各销售员/部门，支持按季度分解目标并跟踪完成率，激励团队达成业绩。',
        fields: [
          { name: '年度 / 年度目标 / 增长率(%)', desc: '目标年度、总目标金额与较上年的增长率' },
          { name: '已分配 / 未分配', desc: '目标分配到销售员的情况' },
          { name: '自动生成下年', desc: '一键基于本年目标生成下年度目标' },
          { name: '分配明细（Q1-Q4）', desc: '各销售员/部门分季度的目标分解' },
          { name: '新客户目标 / 订单数目标', desc: '细分的新客户开发与订单数目标' },
          { name: '实际完成 / 完成率', desc: '与业务数据联动统计的目标完成情况' },
          { name: '排名', desc: '按完成率对各销售员业绩排名' }
        ],
        steps: [
          '点击【新增年度目标】录入年度与目标金额',
          '点击【分配销售目标】将目标分配到各销售员并填写季度分解',
          '点击【生成下年度】基于本年目标自动生成下年目标',
          '点击【刷新完成】同步最新业务完成数据，查看完成率与排名',
          '行内【编辑】【删除】维护目标'
        ],
        editShot: '新增年度目标弹窗：录入年度、年度目标金额与增长率，可勾选"自动生成下年"；保存后点击【分配销售目标】分解到各销售员',
        note: '提示：完成率与销售订单、新客户等业务数据实时联动，作为团队业绩考核依据。'
      },
      {
        key: 'report', title: '报表中心',
        intro: '集中展示销售漏斗分析、销售业绩排行与客户贡献回款等报表，支持按日期范围筛选与查看明细。',
        fields: [
          { name: '销售漏斗分析', desc: '按商机阶段统计数量/金额/转化率，点击【查看明细】下钻' },
          { name: '销售业绩排行', desc: '按订单金额对销售员排名，展示订单数与赢单数' },
          { name: '客户贡献', desc: '按客户统计订单数与金额占比，识别核心客户' },
          { name: '回款情况', desc: '按客户统计应收单数、应收金额与未回款' },
          { name: '开始日期 / 结束日期', desc: '报表统计的时间范围筛选' }
        ],
        steps: [
          '进入报表中心选择开始/结束日期范围',
          '点击【查询】刷新统计报表，【重置】恢复默认范围',
          '在各报表区域点击【查看明细】查看详细数据',
          '依据漏斗与业绩排行分析销售团队表现'
        ],
        noEditNote: '该页面为统计报表中心，无需新增/编辑操作；数据与商机、销售订单、应收模块实时联动，按日期范围筛选即可。',
        note: '提示：报表数据与商机、订单、应收等模块实时联动，无需手工填报。'
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
