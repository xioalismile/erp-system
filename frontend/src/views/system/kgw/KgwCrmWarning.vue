<template>
  <div class="kgw-crm-warning">
    <div class="kgw-toolbar">
      <div class="kgw-actions">
        <el-button v-permission="perm('save')" type="primary" size="small" @click="onSave" :loading="saving">{{ $tt("保存") }}</el-button>
        <el-button v-permission="perm('resetDefault')" size="small" @click="onReset">{{ $tt("恢复默认") }}</el-button>
        <el-button v-permission="perm('refresh')" size="small" @click="loadConfig">{{ $tt("刷新") }}</el-button>
      </div>
      <div class="kgw-search">
        <span class="kgw-tip">{{ $tt("配置公海回收规则与跟进超期预警阈值，保存后立即生效") }}</span>
      </div>
    </div>

    <div class="kgw-body">
      <!-- Section 1: 公海回收规则 -->
      <div class="kgw-section">
        <div class="kgw-section-title">
          <el-icon><Refresh /></el-icon>
          <span>{{ $tt("公海回收规则") }}</span>
          <el-tag size="small" type="warning" effect="plain">{{ $tt("每日凌晨 01:00 自动执行") }}</el-tag>
        </div>
        <div class="kgw-section-body">
          <el-row :gutter="20">
            <el-col :span="8">
              <div class="param-item">
                <div class="param-label">
                  <el-tooltip :content="$tt('启用后系统每日凌晨1:00自动将长期未跟进/未开单的私海客户回收到公海')" placement="top">
                    <el-icon class="help-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                  启用公海自动回收
                </div>
                <div class="param-control">
                  <el-switch v-model="poolConfig.isEnabled" />
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="param-item">
                <div class="param-label">
                  <el-tooltip :content="$tt('销售员每天最多可从公海池领取的客户数量，0表示不限制')" placement="top">
                    <el-icon class="help-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                  每人每日领取上限
                </div>
                <div class="param-control">
                  <el-input-number v-model="claimLimit" :min="0" :max="999" controls-position="right" size="small" style="width:120px" />
                  <span class="param-suffix">{{ $tt("个/天") }}</span>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="param-item">
                <div class="param-label">
                  <el-tooltip :content="$tt('超过该天数未跟进的私海客户，自动回收到公海池')" placement="top">
                    <el-icon class="help-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                  未跟进自动回收天数
                </div>
                <div class="param-control">
                  <el-input-number v-model="poolConfig.noFollowDays" :min="7" :max="365" controls-position="right" size="small" style="width:120px" />
                  <span class="param-suffix">{{ $tt("天") }}</span>
                </div>
              </div>
            </el-col>
          </el-row>
          <el-row :gutter="20" style="margin-top:16px;">
            <el-col :span="8">
              <div class="param-item">
                <div class="param-label">
                  <el-tooltip :content="$tt('超过该天数未开单的私海客户，自动回收到公海池')" placement="top">
                    <el-icon class="help-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                  未开单自动回收天数
                </div>
                <div class="param-control">
                  <el-input-number v-model="poolConfig.noOrderDays" :min="30" :max="730" controls-position="right" size="small" style="width:120px" />
                  <span class="param-suffix">{{ $tt("天") }}</span>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="param-item">
                <div class="param-label">
                  <el-tooltip :content="$tt('回收前N天向责任销售员发送预警提醒，0表示不提醒')" placement="top">
                    <el-icon class="help-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                  回收前预警提醒天数
                </div>
                <div class="param-control">
                  <el-input-number v-model="poolConfig.warningDays" :min="0" :max="30" controls-position="right" size="small" style="width:120px" />
                  <span class="param-suffix">{{ $tt("天") }}</span>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="param-item">
                <div class="param-label">{{ $tt("规则名称") }}</div>
                <div class="param-control">
                  <el-input v-model="poolConfig.configName" size="small" style="width:180px" />
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
      </div>

      <!-- Section 2: 跟进超期预警 -->
      <div class="kgw-section">
        <div class="kgw-section-title">
          <el-icon><Bell /></el-icon>
          <span>{{ $tt("跟进超期预警规则") }}</span>
          <el-tag size="small" type="info" effect="plain">{{ $tt("不同预警类型可独立配置") }}</el-tag>
        </div>
        <div class="kgw-section-body">
          <el-table :data="warningRules" border size="small" style="width:100%">
            <el-table-column :label="$tf('启用')" width="80" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" />
              </template>
            </el-table-column>
            <el-table-column :label="$tf('预警类型')" width="140">
              <template #default="{ row }">
                <el-tag :type="row.tagType" size="small" effect="plain">{{ row.label }}</el-tag>
                <div style="font-size:11px;color:#909399;margin-top:4px;">{{ row.desc }}</div>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('阈值天数')" width="160">
              <template #default="{ row }">
                <el-input-number v-model="row.thresholdDays" :min="1" :max="365" controls-position="right" size="small" style="width:120px" />
                <span style="margin-left:4px;color:#666;">{{ $tt("天") }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('预警等级')" width="160">
              <template #default="{ row }">
                <el-select v-model="row.level" size="small" style="width:130px">
                  <el-option :label="$tf('提醒')" value="info" />
                  <el-option :label="$tf('警告')" value="warning" />
                  <el-option :label="$tf('紧急')" value="danger" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('接收角色')" min-width="220">
              <template #default="{ row }">
                <el-select v-model="row.receiverRoles" multiple filterable collapse-tags size="small" :placeholder="$tt('请选择接收角色')" style="width:100%">
                  <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('提醒方式')" min-width="180">
              <template #default="{ row }">
                <el-checkbox-group v-model="row.notifyMethods" size="small">
                  <el-checkbox value="system">{{ $tt("站内") }}</el-checkbox>
                  <el-checkbox value="sms">{{ $tt("短信") }}</el-checkbox>
                  <el-checkbox value="email">{{ $tt("邮件") }}</el-checkbox>
                </el-checkbox-group>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('操作')" width="80">
              <template #default="{ row }">
                <el-button v-permission="perm('delete')" link type="danger" size="small" @click="removeWarningRule(row)" v-if="row.id">{{ $tt("删除") }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- Section 3: 提醒与分配策略 -->
      <div class="kgw-section">
        <div class="kgw-section-title">
          <el-icon><Setting /></el-icon>
          <span>{{ $tt("提醒与分配策略") }}</span>
        </div>
        <div class="kgw-section-body">
          <el-row :gutter="20">
            <el-col :span="8">
              <div class="param-item">
                <div class="param-label">
                  <el-tooltip :content="$tt('启用后预警列表中的提醒按钮会真实发送消息，否则仅记录日志')" placement="top">
                    <el-icon class="help-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                  启用一键提醒
                </div>
                <div class="param-control">
                  <el-switch v-model="remindEnabled" />
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="param-item">
                <div class="param-label">
                  <el-tooltip :content="$tt('同一客户N天内不重复发送提醒，避免骚扰')" placement="top">
                    <el-icon class="help-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                  提醒去重周期
                </div>
                <div class="param-control">
                  <el-input-number v-model="remindDedupDays" :min="0" :max="30" controls-position="right" size="small" style="width:120px" />
                  <span class="param-suffix">{{ $tt("天") }}</span>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="param-item">
                <div class="param-label">
                  <el-tooltip :content="$tt('超过该天数的预警客户自动建议重新分配，0表示不自动建议')" placement="top">
                    <el-icon class="help-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                  自动建议重新分配阈值
                </div>
                <div class="param-control">
                  <el-input-number v-model="autoReassignDays" :min="0" :max="365" controls-position="right" size="small" style="width:120px" />
                  <span class="param-suffix">{{ $tt("天") }}</span>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
      </div>

      <!-- 销售过程动作量化（2026-10-07）：过程任务标准 / 预警等级与处理时限 / 过程奖金 -->
      <div class="kgw-section" v-for="grp in processGroups" :key="grp.name">
        <div class="kgw-section-title">
          <el-icon><Setting /></el-icon>
          <span>{{ $tt(grp.name) }}</span>
          <el-tag size="small" type="info" effect="plain">{{ $tt(grp.tag) }}</el-tag>
        </div>
        <div class="kgw-section-body">
          <el-row :gutter="20">
            <el-col :span="8" v-for="f in grp.fields" :key="f.key">
              <div class="param-item">
                <div class="param-label">
                  <el-tooltip :content="$tt(f.tip)" placement="top">
                    <el-icon class="help-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                  {{ $tt(f.label) }}
                </div>
                <div class="param-control">
                  <el-date-picker v-if="f.type === 'date'" v-model="processCfg[f.key]" type="date"
                                  value-format="YYYY-MM-DD" size="small" style="width:150px" />
                  <el-input v-else-if="f.type === 'text'" v-model="processCfg[f.key]" size="small" style="width:110px" />
                  <el-input-number v-else v-model="processCfg[f.key]" :min="0" :max="f.max || 99999"
                                   controls-position="right" size="small" style="width:120px" />
                  <span class="param-suffix" v-if="f.unit">{{ $tt(f.unit) }}</span>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:crm-warning')
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled, Bell, Setting, Refresh } from '@element-plus/icons-vue'
import {
  getActivePoolConfig, savePoolConfig,
  listWarningConfig, saveWarningConfig, deleteWarningConfig
} from '@/api/crm/pool'
import { getSysConfigByKey, updateSysConfig } from '@/api/system/sysConfig'
import { tt } from '@/i18n'

// ==================== 销售过程动作量化（2026-10-07）====================
// 口径来源：.trae/documents/销售过程动作量化与销售预警_立项登记表.md（用户已逐条拍板）
const PROCESS_FIELDS = [
  { key: 'sales.process.daily_visit', label: '每日回访老客户', unit: '家', type: 'int', def: 30, max: 999,
    tip: '方案默认 30 家/天；判据：通话≥30秒 或 微信有实质回复', group: '过程任务标准' },
  { key: 'sales.process.daily_new_wechat', label: '每日新增意向微信', unit: '家', type: 'int', def: 5, max: 999,
    tip: '方案默认 5 家/天（当天建档且已填微信）', group: '过程任务标准' },
  { key: 'sales.process.daily_deep_chat', label: '每日深入业务交流', unit: '家', type: 'int', def: 3, max: 999,
    tip: '方案默认 3 家/天；判据：通话≥5分钟 或 微信交流≥10句', group: '过程任务标准' },
  { key: 'sales.process.weekly_sample', label: '每周寄样品', unit: '份', type: 'int', def: 5, max: 999,
    tip: '方案默认 5 份/周；免费样品订单状态为已发货/已完成且快递单号非空', group: '过程任务标准' },
  { key: 'sales.process.weekly_promotion', label: '每周朋友圈推广', unit: '次', type: 'int', def: 3, max: 999,
    tip: '方案默认 3 次/周；需上传截图，无截图不计入', group: '过程任务标准' },
  { key: 'sales.process.private_overdue_days', label: '私海超期未联系天数', unit: '天', type: 'int', def: 30, max: 365,
    tip: '方案默认 30 天；超过该天数未联系的私海客户计入超期', group: '过程任务标准' },
  { key: 'sales.process.rfid_coverage', label: 'RFID 素材覆盖率', unit: '%', type: 'int', def: 100, max: 100,
    tip: '方案默认 100%；意向客户中已被推送 RFID 素材的占比', group: '过程任务标准' },
  { key: 'sales.process.level.yellow_rate', label: '黄色预警完成率下限', unit: '%', type: 'int', def: 80, max: 100,
    tip: '黄=该值~99%；橙=橙色下限~该值-1；红=低于橙色下限', group: '预警等级阈值' },
  { key: 'sales.process.level.orange_rate', label: '橙色预警完成率下限', unit: '%', type: 'int', def: 60, max: 100,
    tip: '低于该值即为红色（严重）', group: '预警等级阈值' },
  { key: 'sales.process.level.yellow_days', label: '黄色预警私海天数下限', unit: '天', type: 'int', def: 25, max: 365,
    tip: '文档第八节：私海 30~44 天为橙、>45 天为红', group: '预警等级阈值' },
  { key: 'sales.process.level.orange_days', label: '橙色预警私海天数下限', unit: '天', type: 'int', def: 30, max: 365,
    tip: '私海 30 天起触发「私海30天未联系」（橙）', group: '预警等级阈值' },
  { key: 'sales.process.level.ar_yellow_days', label: '回款逾期黄色天数上限', unit: '天', type: 'int', def: 15, max: 365,
    tip: '文档第八节：回款逾期 1~15 天为黄', group: '预警等级阈值' },
  { key: 'sales.process.level.ar_orange_days', label: '回款逾期橙色天数上限', unit: '天', type: 'int', def: 30, max: 365,
    tip: '文档第八节：回款逾期 16~30 天为橙、超过为红', group: '预警等级阈值' },
  { key: 'sales.process.deadline.yellow', label: '黄色预警处理时限', type: 'text', def: '当日',
    tip: '黄=销售本人 / 当日处理', group: '推送对象与处理时限' },
  { key: 'sales.process.deadline.orange', label: '橙色预警处理时限', unit: '小时', type: 'int', def: 24, max: 999,
    tip: '橙=销售本人 + 总监 / 24 小时', group: '推送对象与处理时限' },
  { key: 'sales.process.deadline.red', label: '红色预警处理时限', unit: '小时', type: 'int', def: 48, max: 999,
    tip: '红=销售本人 + 总监 + 总经理 / 48 小时；超时未处理自动升级', group: '推送对象与处理时限' },
  { key: 'sales.process.push.yellow', label: '黄色预警推送对象', type: 'text', def: '销售本人',
    tip: '仅在预警类型未单独规定推送对象时生效（第八节逐类型规定优先）', group: '推送对象与处理时限' },
  { key: 'sales.process.push.orange', label: '橙色预警推送对象', type: 'text', def: '销售 + 总监',
    tip: '仅在预警类型未单独规定推送对象时生效', group: '推送对象与处理时限' },
  { key: 'sales.process.push.red', label: '红色预警推送对象', type: 'text', def: '销售 + 总监 + 总经理',
    tip: '仅在预警类型未单独规定推送对象时生效', group: '推送对象与处理时限' },
  { key: 'sales.process.bonus_amount', label: '过程奖金金额', unit: '元/人/月', type: 'int', def: 500, max: 99999,
    tip: '方案默认 500 元/人/月', group: '过程奖金规则' },
  { key: 'sales.process.bonus_tolerance', label: '过程奖金容差天数', unit: '天', type: 'int', def: 0, max: 31,
    tip: '0 = 一刀切：四项（每日/每周/私海/RFID）任一不足 100% 即为 0 元（当前已确认口径）', group: '过程奖金规则' },
  { key: 'sales.process.stat_start_date', label: '过程达标统计起算日', type: 'date', def: '',
    tip: '该日之前的历史跟进记录不参与达标统计、不产生预警、不算奖金（避免上线首月全员 0 元）', group: '过程奖金规则' }
]

const processCfg = reactive({})
// 文档 4.6 要求 4 个配置区：过程任务标准 / 预警等级阈值 / 推送对象与处理时限 / 过程奖金规则
const processGroups = ['过程任务标准', '预警等级阈值', '推送对象与处理时限', '过程奖金规则'].map(name => ({
  name,
  tag: name === '过程任务标准' ? '保存后立即生效'
     : (name === '预警等级阈值' ? '黄 / 橙 / 红 三级'
     : (name === '推送对象与处理时限' ? '按等级默认，类型另有规定时以第八节为准' : '四项全 100% 才发放')),
  fields: PROCESS_FIELDS.filter(f => f.group === name)
}))

async function loadProcessCfg() {
  for (const f of PROCESS_FIELDS) {
    processCfg[f.key] = f.def
    try {
      const c = await getSysConfigByKey(f.key)
      const v = c && c.configValue
      if (v !== null && v !== undefined && v !== '') {
        processCfg[f.key] = f.type === 'int' ? Number(v) : v
      }
    } catch (e) { /* 读不到保持默认值 */ }
  }
}

async function saveProcessCfg() {
  for (const f of PROCESS_FIELDS) {
    const v = processCfg[f.key]
    await updateSysConfig(f.key, v === null || v === undefined ? '' : String(v))
  }
}

function resetProcessCfg() {
  for (const f of PROCESS_FIELDS) processCfg[f.key] = f.def
}

// 进入页面即加载（与顶层 loadConfig 并列，互不影响）
loadProcessCfg()

const roleOptions = ['销售经理', '销售主管', '销售员', '客服']

const warningRules = ref([
  { id: null, label: '跟进预警', tagType: 'warning', desc: '超过阈值未跟进客户',
    warningType: '跟进预警', enabled: true, thresholdDays: 7, level: 'warning',
    receiverRoles: ['销售员', '销售主管'], notifyMethods: ['system'] },
  { id: null, label: '开单预警', tagType: 'danger', desc: '超过阈值未开单客户',
    warningType: '开单预警', enabled: true, thresholdDays: 30, level: 'danger',
    receiverRoles: ['销售主管', '销售经理'], notifyMethods: ['system', 'sms'] },
  { id: null, label: '流失预警', tagType: 'danger', desc: '即将流失的高价值客户',
    warningType: '流失预警', enabled: true, thresholdDays: 60, level: 'danger',
    receiverRoles: ['销售经理'], notifyMethods: ['system', 'sms', 'email'] }
])

const poolConfig = reactive({
  id: null,
  configName: '默认公海规则',
  noFollowDays: 90,
  noOrderDays: 180,
  warningDays: 7,
  isEnabled: true
})

const claimLimit = ref(5)
const remindEnabled = ref(true)
const remindDedupDays = ref(3)
const autoReassignDays = ref(90)

const saving = ref(false)

async function loadConfig() {
  try {
    const pool = await getActivePoolConfig()
    if (pool) {
      poolConfig.id = pool.configId
      poolConfig.configName = pool.configName || '默认公海规则'
      poolConfig.noFollowDays = pool.noFollowDays || 90
      poolConfig.noOrderDays = pool.noOrderDays || 180
      poolConfig.warningDays = pool.warningDays || 7
      poolConfig.isEnabled = pool.isEnabled === 1 || pool.isEnabled === true
    }
  } catch (e) { /* 静默 */ }

  try {
    const limit = await getSysConfigByKey('crm.pool.daily.claim.limit')
    claimLimit.value = limit ? Number(limit.configValue) : 5
  } catch (e) { claimLimit.value = 5 }

  try {
    const warnConfigs = await listWarningConfig()
    const list = Array.isArray(warnConfigs) ? warnConfigs : (warnConfigs?.records || [])
    if (list.length > 0) {
      warningRules.value = list.map(w => {
        // 从 remark 解析 level 和 notifyMethods
        let level = 'warning', notifyMethods = ['system']
        try {
          if (w.remark) {
            const parsed = JSON.parse(w.remark)
            if (parsed.level) level = parsed.level
            if (Array.isArray(parsed.notifyMethods)) notifyMethods = parsed.notifyMethods
          }
        } catch (e) { /* remark 不是 JSON，保持默认值 */ }
        return {
          id: w.warningId,
          label: w.warningType || '跟进预警',
          tagType: w.warningType === '开单预警' || w.warningType === '流失预警' ? 'danger' : 'warning',
          desc: w.warningType === '跟进预警' ? '超过阈值未跟进客户' :
                w.warningType === '开单预警' ? '超过阈值未开单客户' : '即将流失的高价值客户',
          warningType: w.warningType || '跟进预警',
          enabled: w.isEnabled === 1 || w.isEnabled === true,
          thresholdDays: w.thresholdDays || 7,
          level: level,
          receiverRoles: w.receiverRole ? w.receiverRole.split(',') : [],
          notifyMethods: notifyMethods
        }
      })
    }
  } catch (e) { /* 静默 */ }

  try {
    const remEnabled = await getSysConfigByKey('crm.warning.remind.enabled')
    remindEnabled.value = remEnabled ? remEnabled.configValue === 'true' : true
  } catch (e) { remindEnabled.value = true }

  try {
    const dedup = await getSysConfigByKey('crm.warning.remind.dedup.days')
    remindDedupDays.value = dedup ? Number(dedup.configValue) : 3
  } catch (e) { remindDedupDays.value = 3 }

  try {
    const reassign = await getSysConfigByKey('crm.warning.auto.reassign.days')
    autoReassignDays.value = reassign ? Number(reassign.configValue) : 90
  } catch (e) { autoReassignDays.value = 90 }
}

async function onSave() {
  saving.value = true
  try {
    const savedPool = await savePoolConfig({
      configId: poolConfig.id,
      configName: poolConfig.configName,
      noFollowDays: poolConfig.noFollowDays,
      noOrderDays: poolConfig.noOrderDays,
      warningDays: poolConfig.warningDays,
      isEnabled: poolConfig.isEnabled ? 1 : 0
    })
    // 保存后更新 id，避免下次保存重复新增
    if (savedPool && savedPool.configId) {
      poolConfig.id = savedPool.configId
    }

    await updateSysConfig('crm.pool.daily.claim.limit', String(claimLimit.value))

    for (const rule of warningRules.value) {
      const saved = await saveWarningConfig({
        warningId: rule.id,
        warningType: rule.warningType,
        thresholdDays: rule.thresholdDays,
        isEnabled: rule.enabled ? 1 : 0,
        receiverRole: Array.isArray(rule.receiverRoles) ? rule.receiverRoles.join(',') : '',
        remark: JSON.stringify({ level: rule.level, notifyMethods: rule.notifyMethods })
      })
      // 保存后更新 id，避免下次保存重复新增
      if (saved && saved.warningId) {
        rule.id = saved.warningId
      }
    }

    await updateSysConfig('crm.warning.remind.enabled', String(remindEnabled.value))
    await updateSysConfig('crm.warning.remind.dedup.days', String(remindDedupDays.value))
    await updateSysConfig('crm.warning.auto.reassign.days', String(autoReassignDays.value))

    // 销售过程动作量化（2026-10-07）：过程任务标准 / 预警等级与时限 / 过程奖金
    await saveProcessCfg()

    ElMessage.success(tt('保存成功'))
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

async function removeWarningRule(row) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(tt('确认删除该预警规则？'), tt('提示'), { type: 'warning' })
    await deleteWarningConfig(row.id)
    warningRules.value = warningRules.value.filter(r => r.id !== row.id)
    ElMessage.success(tt('删除成功'))
  } catch (e) { /* 取消 */ }
}

async function onReset() {
  try {
    await ElMessageBox.confirm(tt('确认恢复所有 CRM 预警配置为默认值？已保存的配置将被覆盖。'), tt('提示'), { type: 'warning' })
    poolConfig.id = null
    poolConfig.configName = '默认公海规则'
    poolConfig.noFollowDays = 90
    poolConfig.noOrderDays = 180
    poolConfig.warningDays = 7
    poolConfig.isEnabled = true
    claimLimit.value = 5
    warningRules.value = [
      { id: null, label: '跟进预警', tagType: 'warning', desc: '超过阈值未跟进客户',
        warningType: '跟进预警', enabled: true, thresholdDays: 7, level: 'warning',
        receiverRoles: ['销售员', '销售主管'], notifyMethods: ['system'] },
      { id: null, label: '开单预警', tagType: 'danger', desc: '超过阈值未开单客户',
        warningType: '开单预警', enabled: true, thresholdDays: 30, level: 'danger',
        receiverRoles: ['销售主管', '销售经理'], notifyMethods: ['system', 'sms'] },
      { id: null, label: '流失预警', tagType: 'danger', desc: '即将流失的高价值客户',
        warningType: '流失预警', enabled: true, thresholdDays: 60, level: 'danger',
        receiverRoles: ['销售经理'], notifyMethods: ['system', 'sms', 'email'] }
    ]
    remindEnabled.value = true
    remindDedupDays.value = 3
    autoReassignDays.value = 90
    // 销售过程动作量化（2026-10-07）：一并恢复为方案默认值
    resetProcessCfg()
    ElMessage.success(tt('已恢复默认值（需点击保存才生效）'))
  } catch (e) { /* 取消 */ }
}

onMounted(loadConfig)
</script>

<style scoped lang="scss">
.kgw-crm-warning {
  background: #f0f2f5;
  min-height: 100%;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
  display: flex;
  flex-direction: column;
}
.kgw-toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  padding: 0 16px;
  height: 48px;
  flex-shrink: 0;
  gap: 12px;
}
.kgw-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.kgw-search {
  display: flex;
  gap: 8px;
  align-items: center;
}
.kgw-tip {
  font-size: 12px;
  color: #909399;
}
.kgw-body {
  padding: 12px;
  flex: 1;
  overflow: auto;
}
.kgw-section {
  background: #fff;
  margin-bottom: 12px;
  border-radius: 4px;
  overflow: hidden;
}
.kgw-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  font-size: 14px;
  font-weight: 700;
  color: #303133;
  .el-icon { color: #1890ff; font-size: 16px; }
}
.kgw-section-body {
  padding: 16px;
}
.param-item {
  min-height: 32px;
}
.param-label {
  font-size: 13px;
  color: #333;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
}
.help-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #1890ff;
  color: #fff;
  font-size: 11px;
  margin-right: 6px;
  cursor: help;
  flex-shrink: 0;
}
.param-control {
  display: flex;
  align-items: center;
  gap: 8px;
}
.param-suffix {
  font-size: 12px;
  color: #666;
}
:deep(.el-switch.is-checked .el-switch__core) { background-color: #1890ff; border-color: #1890ff; }
:deep(.el-button--primary) { background-color: #1890ff; border-color: #1890ff; }
:deep(.el-button--primary:hover) { background-color: #40a9ff; border-color: #40a9ff; }
:deep(.el-checkbox__input.is-checked .el-checkbox__inner) { background-color: #1890ff; border-color: #1890ff; }
:deep(.el-checkbox__input.is-checked + .el-checkbox__label) { color: #1890ff; }
</style>
