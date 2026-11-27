<template>
  <div class="help-page">
    <el-card shadow="never" class="help-header">
      <div class="help-title">
        <el-icon :size="32" color="#409EFF"><HelpFilled /></el-icon>
        <div>
          <h1>{{ $tt("获客管理模块操作手册") }}</h1>
          <p>{{ $tt("获客管理基于浏览器自动采集企业公开工商信息，通过配置采集任务、审核采集结果、维护平台登录态，实现销售线索的自动获取与筛选入库") }}</p>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px;">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ============ 快速入门 ============ -->
        <el-tab-pane :label="$tf('快速入门')" name="quickstart">
          <div class="help-section">
            <h3>{{ $tt("一、模块概览") }}</h3>
            <p>{{ $tt("获客管理模块共包含 3 个功能页，分为 3 大流程：采集任务、结果审核、平台登录。点击下方任一功能卡片可直达对应说明位置。") }}</p>
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
              <div class="flow-step"><div class="step-circle">1</div><div class="step-text">{{ $tt("配置采集平台登录") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">2</div><div class="step-text">{{ $tt("创建采集任务") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">3</div><div class="step-text">{{ $tt("启动任务采集") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">4</div><div class="step-text">{{ $tt("审核采集结果") }}</div></div>
              <div class="flow-arrow">-></div>
              <div class="flow-step"><div class="step-circle">5</div><div class="step-text">{{ $tt("自动写入公海池/查看线索") }}</div></div>
            </div>
            <el-alert type="info" :closable="false" show-icon
              :title="$tt('采集结果须经人工审核方可入库：审核通过后将自动写入公海池可供销售领取，也可跳转查看对应线索；驳回或未通过的结果不会进入客户资源池。')" />

            <h3>{{ $tt("三、通用操作说明（获客管理所有页面一致）") }}</h3>
            <div class="op-table">
              <el-table :data="commonOps" border size="default">
                <el-table-column prop="op" :label="$tf('操作')" width="150" />
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
              <el-image :src="'/manual/acquisition/' + doc.key + '.png'"
                        :preview-src-list="['/manual/acquisition/' + doc.key + '.png']"
                        fit="contain" hide-on-click-modal class="shot-img" />
              <div class="shot-tip">{{ $tt("点击图片可放大查看") }}</div>
            </div>

            <template v-if="doc.editShot">
              <h4 class="sub-title">{{ $tt("新增 / 编辑界面") }}</h4>
              <div class="shot-wrap">
                <el-image :src="'/manual/acquisition/' + doc.key + '-dialog.png'"
                          :preview-src-list="['/manual/acquisition/' + doc.key + '-dialog.png']"
                          fit="contain" hide-on-click-modal class="shot-img" />
                <div class="shot-tip">{{ doc.editShot }}</div>
              </div>
            </template>
            <el-alert v-else-if="doc.noEditNote" :title="doc.noEditNote" type="info" :closable="false" show-icon style="margin:12px 0;" />

            <h4 class="sub-title">{{ $tt("核心字段说明") }}</h4>
            <el-table :data="doc.fields" border size="small">
              <el-table-column prop="name" :label="$tf('字段')" width="170" />
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
  { icon: '⏱️', label: '获客任务', desc: '关键词采集任务与定时调度', group: 'task', key: 'task' },
  { icon: '✅', label: '采集结果审核', desc: '采集结果审查与导入入库', group: 'task', key: 'result' },
  { icon: '🔐', label: '企查查登录', desc: '采集平台登录态维护', group: 'login', key: 'login' }
]

const commonOps = [
  { op: '查询 / 重置', desc: '顶部搜索框支持按"关键词/采集平台/任务状态"等条件过滤；勾选统计项可快速切换筛选范围' },
  { op: '新增 / 编辑', desc: '点击【+ 新增任务】等按钮进入新增弹窗；行内【编辑】可修改任务名称、关键词、调度计划等配置' },
  { op: '启动 / 暂停', desc: '任务行内【启动采集】立即执行；【暂停】挂起定时任务；已运行任务可强制重启' },
  { op: '查看结果', desc: '任务行内【查看结果】跳转至"采集结果审核"页并自动带入该任务筛选' },
  { op: '删除', desc: '行内【删除】移除任务或结果记录；结果删除为物理删除，请谨慎操作' }
]

const groups = [
  {
    name: 'task', label: '采集任务', docs: [
      {
        key: 'task', title: '获客任务',
        intro: '定义一个或多个关键词的采集任务，指定采集平台、行业/地区筛选与数量上限，并可配置 Cron 定时调度，由系统在后台浏览器自动执行采集。',
        fields: [
          { name: '任务编号 / 任务名称', desc: '任务唯一编号（保存后自动生成，如 ACQUISITION_TASK-xxx），名称便于识别' },
          { name: '采集平台', desc: 'qcc=企查查 / fengniao=风鸟 / tianyancha=天眼查 / gsxt=国家企业信用信息公示系统' },
          { name: '搜索关键词', desc: '可录入多个关键词，保存后以标签形式展示；用于在采集平台按关键词搜索企业' },
          { name: '行业 / 地区筛选', desc: '可选填，用于进一步限定采集范围，缩小目标企业集合' },
          { name: '最大采集页数 / 最大企业数', desc: '任务的采集深度上限，避免资源消耗过大' },
          { name: '定时执行（Cron）', desc: '可视化 Cron 构建器：提供每日/工作日/每小时/每月等预设，也可逐字段自定义秒/分/时/日/月/周，并实时预览可读调度说明' },
          { name: '任务状态', desc: 'pending=待执行 / running=运行中 / completed=已完成 / failed=失败 / paused=已暂停' },
          { name: '已采集 / 已导入', desc: 'capturedCount 记录已采集企业数，importedCount 记录已生效导入的线索数' },
          { name: '最后执行时间', desc: '任务最近一次实际执行的启动时间' }
        ],
        steps: [
          '在获客任务页点击【+ 新增任务】，填写任务名称并选择采集平台（建议先在"企查查登录"页完成对应平台登录）',
          '录入搜索关键词（支持多关键词标签），可进一步选择行业与地区筛选',
          '设置最大采集页数与最大企业数，控制采集范围与资源消耗',
          '在"定时执行"区域选择 Cron 预设或逐字段配置调度计划，预览可读调度说明确认',
          '保存后任务进入"待执行"状态；点击行内【启动采集】立即执行，或等待定时调度自动触发',
          '执行过程中【暂停】挂起任务；点击【查看结果】跳转采集结果审核页处理本次采集数据',
          '不再需要的任务点击【删除】移除'
        ],
        editShot: '新增任务弹窗：配置任务名称/采集平台/关键词（可加标签）、行业地区筛选、最大采集页数与企业数，以及 Cron 定时调度',
        note: '提示：任务执行依赖采集平台登录态，执行前请确认已登录对应平台；状态为"运行中"的任务再次启动会弹出二次确认以强制重启。'
      },
      {
        key: 'result', title: '采集结果审核',
        intro: '集中展示各任务采集到的企业工商信息，需人工审核筛选后通过入库：审核通过的结果会作为线索自动写入公海池，供销售后续领取与跟进。',
        fields: [
          { name: '公司名称', desc: '采集到的企业全称' },
          { name: '法人代表 / 成立日期', desc: '企业法定代表人及成立时间' },
          { name: '注册资本 / 所属行业', desc: '注册资本金额与企业所属行业分类' },
          { name: '所属地区 / 注册地址', desc: '企业注册地与详细地址' },
          { name: '联系电话 / 邮箱', desc: '公开的联系方式，用于后续触达' },
          { name: '经营范围', desc: '企业工商登记的经营内容摘要' },
          { name: '审核状态', desc: 'pending=待审核 / approved=已通过 / rejected=已驳回 / imported=已导入' },
          { name: '截图', desc: '采集时的平台页面截图，支持点击预览与放大核对' }
        ],
        steps: [
          '顶部按任务、公司名称关键词、审核状态、行业等条件筛选目标结果，【查询】过滤、【重置】还原',
          '对待审核结果逐条核对工商信息，可通过【详情】查看完整信息与【采集截图】以及 AI 识别结果',
          '确认有效点击行内【通过】，系统自动将结果写入公海池并标记"已通过"',
          '无效结果点击【驳回】并填写驳回原因；也可勾选多条后【批量通过】或【批量驳回】',
          '已通过但未入库的结果可点击【重试导入】重新执行写入；已导入的结果点击【查看线索】跳转线索详请',
          '确认无害的结果可【删除】移除记录'
        ],
        editShot: '详情弹窗：包含基本信息（可读的企业工商字段）、采集截图与 AI 识别结果三个页签；驳回时弹出原因填写框',
        note: '提示：驳回原因必填，便于追溯为何不通过；顶部"审批通过后将自动写入公海池"为状态流转说明，企业成功入库后可交由销售在客户资源中领取跟进。'
      }
    ]
  },
  {
    name: 'login', label: '平台登录', docs: [
      {
        key: 'login', title: '平台登录',
        intro: '为各采集平台维护浏览器登录态。不同平台登录方式不同：企查查/天眼查扫码登录，风鸟手机号验证码登录。登录态有效期约 7 天，过期后需重新登录。',
        fields: [
          { name: '平台 Tab', desc: '企查查 / 风鸟 / 天眼查 三个页签，各平台登录态相互独立；Tab 前的小圆点变绿＝该平台已保存登录态' },
          { name: '打开采集浏览器 / 重新打开', desc: '点击后系统在采集服务端启动浏览器并加载登录页，浏览器实时画面投在下方（整宽），可直接在画面上扫码、点按钮、用键盘输入手机号验证码' },
          { name: '清除登录态并重新扫码', desc: '清除该平台在服务器上的 Cookie 与浏览器登录态，并自动重新打开登录页；按钮下方状态会立刻变为「未保存登录态」' },
          { name: '刷新画面 / 关闭浏览器', desc: '刷新画面＝重连实时投屏；关闭浏览器＝立即关闭服务器上的登录浏览器（不用等保活到点）' },
          { name: '登录状态', desc: '等待登录 / 正在启动浏览器 / 已生成二维码请扫码 / 已成功登录 / 登录失败 / 登录超时' }
        ],
        steps: [
          '进入"平台登录"页，在上方 Tab 选择要维护登录态的采集平台（圆点为绿色表示已有登录态）',
          '点击【打开采集浏览器】，系统自动启动浏览器加载对应平台登录页，下方出现服务器浏览器的实时画面',
          '企查查/天眼查：画面里显示二维码，用 APP 扫码，扫码成功系统自动保存 Cookie',
          '风鸟：在画面里点一下手机号输入框，直接用键盘输入手机号与短信验证码（点画面即可操作，退格/回车/Tab/方向键都支持），登录成功后系统自动保存 Cookie',
          '状态变为"已成功登录"后，可在画面里继续处理风控/验证码；需要采集时点【返回】进入获客任务页',
          '若扫码失败或要换账号，点【清除登录态并重新扫码】强制重新登录'
        ],
        noEditNote: '该页为采集平台登录态维护页面，无传统的新增/编辑列表；浏览器实时画面由采集服务端推送，登录操作直接在该画面上完成。',
        note: '提示：各平台登录态相互独立，各自保存的 Cookie 仅用于对应平台的采集任务；登录态有效期约 7 天，过期后需重新至此页登录。'
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
