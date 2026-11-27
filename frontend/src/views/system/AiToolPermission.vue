<template>
  <div class="ai-tool-permission">
    <div class="panel-header">
      <h3>{{ $tt("AI助手工具权限") }}</h3>
      <div class="header-actions">
        <el-button v-permission="perm('save')" type="primary" size="small" @click="onSaveAiConfig" :loading="aiSaving">{{ $tt("保存") }}</el-button>
        <el-button v-permission="perm('refresh')" size="small" @click="loadAiConfig">{{ $tt("刷新") }}</el-button>
      </div>
    </div>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px">
      配置AI助手的功能权限范围。开启后AI助手才能执行对应操作，关闭则禁止。查询权限按模块控制（默认全开）；写操作需先开启总开关。
    </el-alert>
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px">
      开关之外还受「角色权限」约束：用户须拥有对应菜单/功能权限（如设置中心访问权限、对应业务模块权限）才能真正使用；无权限时AI会提示「无权限，请联系管理员授权」。删除类与单据流程动作执行前一律弹出确认卡片。
    </el-alert>
    <el-form :model="aiFormData" label-width="200px" class="ai-config-form">
      <el-form-item v-for="item in aiConfigItems" :key="item.configKey" :label="item.configName">
        <div class="ai-item-row">
          <el-switch v-if="item.valueType === 'boolean'" v-model="aiFormData[item.configKey]" />
          <el-input-number v-else-if="item.valueType === 'number'" v-model="aiFormData[item.configKey]" :min="0" controls-position="right" size="small" />
          <el-select v-else-if="item.valueType === 'select'" v-model="aiFormData[item.configKey]" size="small" style="width:200px">
            <el-option v-for="opt in item.options" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
          </el-select>
          <el-input v-else v-model="aiFormData[item.configKey]" size="small" style="width:300px" />
          <span v-if="item.remark" class="ai-item-remark">{{ item.remark }}</span>
        </div>
      </el-form-item>
    </el-form>
    <el-empty v-if="aiConfigItems.length === 0" :description="$tt('暂无AI助手权限配置项')" />
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:ai-tool-permission')
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSysConfigByGroup, batchUpdateSysConfig } from '@/api/system/sysConfig'
import { tt } from '@/i18n'

const aiSaving = ref(false)
const aiFormData = reactive({})
const aiOriginalData = reactive({})
const aiConfigItems = ref([])

// 本地兜底配置项（后端无记录时使用）
// configKey 必须与后端 sys_config_init.sql / ToolRegistry 读取的 key 完全一致
const LOCAL_AI_CONFIG_ITEMS = [
  { configKey: 'ai.confirm.write.enabled', configName: 'AI写操作需人工确认', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '开启（默认）时AI执行写操作前会弹确认卡片由你拍板；关闭后审批/提交/驳回/重置草稿/下推等【可逆】动作直接执行（无需再回"确定"），但「删除/作废」等不可逆动作仍强制确认。改完立即生效，无需重启' },
  { configKey: 'ai.tool.write.enabled', configName: 'AI写操作总开关', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '是否允许AI助手执行写操作（创建/修改/删除单据等）。关闭后AI只能查询数据，所有写操作工具对AI不可见。子开关仅在此开关开启时生效' },
  { configKey: 'ai.tool.image_order.enabled', configName: '图片下单', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI通过识别商品图片自动创建销售订单草稿。需先开启总开关' },
  { configKey: 'ai.tool.bom_import.enabled', configName: 'BOM文件导入', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI导入BOM文件，自动识别字段映射并转换格式后导入系统。需先开启总开关' },
  { configKey: 'ai.tool.image_bom.enabled', configName: '图片识别BOM', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI通过识别BOM图片/产品图纸自动解析父子件结构并创建BOM。需先开启总开关' },
  { configKey: 'ai.tool.crud.enabled', configName: '通用增删改查', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI直接创建/修改/删除所有业务实体数据（高风险）。建议仅在受信任环境开启。需先开启总开关' },
  { configKey: 'ai.tool.setting.enabled', configName: '系统设置（设置中心）', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI操作设置中心能力（自定义字段/系统参数/语言/自动编号/审批/打印/价格策略等）。实际可用范围还取决于角色权限：用户须拥有设置中心对应权限，无权限时AI会提示「无权限，请联系管理员授权」。需先开启总开关' },
  { configKey: 'ai.tool.bill_action.enabled', configName: '单据流程动作', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI对业务单据执行提交/审核/驳回/重置草稿/关闭/结案/作废/删除等流程动作。实际可用范围取决于角色权限（需拥有对应业务模块权限）。所有动作都会先弹确认卡片。需先开启总开关' },
  { configKey: 'ai.tool.bill_item.enabled', configName: '单据明细行编辑', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI修改单据明细行（如把某行单价改成 1、改数量/税率/折扣率），金额与合计由系统自动重算。改前会先查看明细、并弹确认卡片（卡片显示“改前→改后”）。实际可用范围取决于角色权限。需先开启总开关；目前支持销售、采购、库存、打样、生产、委外、财务共34种单据明细（各单可改字段不同）' },
  { configKey: 'ai.tool.bill_batch.enabled', configName: '单据批量操作', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI对多张同类业务单据批量执行流程动作（批量提交/批量审核/批量删除/批量重置草稿），单次最多50张，逐张执行、逐张返回结果。实际可用范围取决于角色权限。需先开启总开关' },
  { configKey: 'ai.tool.work_operation.enabled', configName: '工序操作', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI对生产工单的工序执行「工序操作面板」的7个操作（状态流转/返工/报废/一键推补料/一键推入库/录入质检/发通知）。实际可用范围取决于角色权限：用户须拥有「生产工单」菜单权限。需先开启总开关' },
  { configKey: 'ai.tool.import.enabled', configName: '数据导入', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI读取用户上传的Excel/CSV并导入系统（客户/供应商/物料/销售订单/采购订单等55类模板）。实际可用范围取决于角色权限。物料导入必须由用户选择冲突策略。需先开启总开关' },
  { configKey: 'ai.tool.order.enabled', configName: '快速下单', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI通过用户粘贴的微信消息/口述内容快速创建销售订单草稿（进入确认流程）。需先开启总开关' },
  { configKey: 'ai.tool.admin.enabled', configName: '权限管理', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI给员工追加角色、给角色追加权限（仅支持开启/追加，且仅管理员可用）。需先开启总开关' },
  { configKey: 'ai.tool.report.enabled', configName: 'AI创建报表', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI根据用户描述设计并创建自定义报表（进入确认流程）。需先开启总开关' },
  { configKey: 'ai.tool.board_save.enabled', configName: 'AI保存看板公式卡', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI把多来源公式卡保存到看板，只影响当前登录人的看板配置，执行前会弹确认卡。需先开启总开关' },
  { configKey: 'ai.tool.platform_query.enabled', configName: '平台级数据查询', valueType: 'boolean', configGroup: 'ai', configValue: 'false', remark: '允许AI查询平台级数据（租户清单/部署版本）。这两类数据不在业务库里，不能走通用查询。仅平台管理员（超级管理员/管理员）可用；默认关闭' },
  { configKey: 'ai.tool.query.basedata.enabled', configName: '查询-基础资料', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询客户/供应商/物料/员工/部门等基础资料。关闭后该类查询工具对AI不可见' },
  { configKey: 'ai.tool.query.crm.enabled', configName: '查询-CRM客户关系', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询线索/商机/跟进/客诉/调研等CRM数据' },
  { configKey: 'ai.tool.query.sale.enabled', configName: '查询-销售', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询报价单/销售订单/发货/退货/发票/合同等销售数据' },
  { configKey: 'ai.tool.query.purchase.enabled', configName: '查询-采购', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询采购申请/订单/入库/退货/发票/合同等采购数据' },
  { configKey: 'ai.tool.query.inventory.enabled', configName: '查询-库存', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询库存/仓库/库位/调拨/盘点/领料等库存数据' },
  { configKey: 'ai.tool.query.production.enabled', configName: '查询-生产', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询工单/BOM/工艺路线/生产入库/工作中心等生产数据' },
  { configKey: 'ai.tool.query.finance.enabled', configName: '查询-财务', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询应收/应付/费用等财务数据。关闭后AI无法查询财务数据' },
  { configKey: 'ai.tool.query.system.enabled', configName: '查询-系统', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询系统用户/角色/菜单/数据字典等系统数据' },
  { configKey: 'ai.tool.query.subcontract.enabled', configName: '查询-委外', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询委外加工单/委外发料单/委外入库单/委外退料单/委外补料单等委外数据' },
  { configKey: 'ai.tool.query.sample.enabled', configName: '查询-打样', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询打样单/免费样品单等打样数据' },
  { configKey: 'ai.tool.query.acquisition.enabled', configName: '查询-获客', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询获客任务/采集结果等获客数据' },
  { configKey: 'ai.tool.query.board.enabled', configName: '查询-看板', valueType: 'boolean', configGroup: 'ai', configValue: 'true', remark: '允许AI查询财务/销售/仓储/经营看板指标、多来源公式试算与明细下钻。关闭后该类工具对AI不可见' }
]

// 解析配置值（字符串 → 对应类型）
function parseAiValue(value, type) {
  if (value === null || value === undefined) {
    return type === 'boolean' ? false : (type === 'number' ? 0 : '')
  }
  if (type === 'boolean') {
    return value === 'true' || value === true
  }
  if (type === 'number') {
    const n = Number(value)
    return isNaN(n) ? 0 : n
  }
  return String(value)
}

async function loadAiConfig() {
  try {
    const remoteList = await getSysConfigByGroup('ai') || []
    const remoteKeys = remoteList.map(c => c.configKey)
    // 合并后端配置和本地兜底配置；后端返回的记录可能缺 valueType，需从本地兜底补全
    const mergedItems = remoteList.map(remote => {
      const local = LOCAL_AI_CONFIG_ITEMS.find(l => l.configKey === remote.configKey)
      return {
        ...local,
        ...remote,
        valueType: remote.valueType || (local ? local.valueType : 'string'),
        configName: remote.configName || (local ? local.configName : remote.configKey),
        remark: remote.remark || (local ? local.remark : '')
      }
    })
    LOCAL_AI_CONFIG_ITEMS.forEach(local => {
      if (!remoteKeys.includes(local.configKey)) {
        mergedItems.push({ ...local })
      }
    })
    aiConfigItems.value = mergedItems
    // 解析配置值到 formData
    mergedItems.forEach(item => {
      const parsed = parseAiValue(item.configValue, item.valueType)
      aiFormData[item.configKey] = parsed
      aiOriginalData[item.configKey] = parsed
    })
  } catch (e) {
    // 后端加载失败时使用本地兜底
    aiConfigItems.value = LOCAL_AI_CONFIG_ITEMS.map(i => ({ ...i }))
    LOCAL_AI_CONFIG_ITEMS.forEach(item => {
      const parsed = parseAiValue(item.configValue, item.valueType)
      aiFormData[item.configKey] = parsed
      aiOriginalData[item.configKey] = parsed
    })
  }
}

async function onSaveAiConfig() {
  aiSaving.value = true
  try {
    const changedList = []
    aiConfigItems.value.forEach(item => {
      const key = item.configKey
      if (aiFormData[key] !== aiOriginalData[key]) {
        let val = aiFormData[key]
        if (item.valueType === 'boolean') val = val ? 'true' : 'false'
        else if (item.valueType === 'number') val = String(val)
        changedList.push({
          configKey: key,
          configValue: val,
          configName: item.configName,
          configGroup: 'ai',
          valueType: item.valueType
        })
      }
    })
    if (changedList.length === 0) {
      ElMessage.info(tt('没有需要保存的修改'))
      return
    }
    await batchUpdateSysConfig(changedList)
    changedList.forEach(item => {
      aiOriginalData[item.configKey] = aiFormData[item.configKey]
    })
    ElMessage.success(tt('保存成功'))
  } catch (e) {
    ElMessage.error(tt('保存失败'))
  } finally {
    aiSaving.value = false
  }
}

onMounted(() => {
  loadAiConfig()
})
</script>

<style scoped lang="scss">
.ai-tool-permission {
  background: #fff;
  border: 1px solid #ebeef5;
  padding: 16px 24px;
  border-radius: 4px;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 12px;
  margin-bottom: 16px;
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }
  .header-actions {
    display: flex;
    gap: 8px;
  }
}
.ai-config-form {
  max-width: 800px;
}
.ai-item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.ai-item-remark {
  font-size: 12px;
  color: #909399;
  flex-shrink: 1;
}
</style>
