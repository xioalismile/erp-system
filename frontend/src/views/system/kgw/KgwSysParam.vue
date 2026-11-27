<template>
  <div class="kgw-sys-param">
    <div class="param-toolbar">
      <el-button v-permission="perm('save')" type="primary" @click="onSave" :loading="saving" size="small">{{ $tt("保存") }}</el-button>
      <el-button v-permission="perm('refresh')" @click="loadConfig" size="small">{{ $tt("刷新") }}</el-button>
    </div>

    <el-tabs v-model="activeTab" class="param-tabs" tab-position="top" @tab-change="onTabChange">
      <el-tab-pane v-for="tab in tabs" :key="tab.key" :label="$tf(tab.label)" :name="tab.key">
        <!-- 加载中 -->
        <div v-if="loading" class="loading-tip">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>{{ $tt("加载中...") }}</span>
        </div>

        <!-- 动态渲染：按 config_section 分组 -->
        <template v-else>
          <div v-for="section in currentSections" :key="section.title" class="section">
            <div class="section-title">{{ section.title }}</div>
            <el-row :gutter="20" class="param-row" v-for="(row, rIdx) in chunked(section.items, 3)" :key="rIdx">
              <el-col :span="8" v-for="item in row" :key="item.configKey">
                <div class="param-item">
                  <div class="param-label">
                    <el-tooltip v-if="item.remark" :content="item.remark" placement="top">
                      <el-icon class="help-icon"><InfoFilled /></el-icon>
                    </el-tooltip>
                    {{ item.configName || item.configKey }}：
                  </div>
                  <div class="param-control">
                    <!-- 布尔开关 -->
                    <template v-if="item.configType === 'boolean'">
                      <el-switch v-model="cfgValues[item.configKey]" />
                    </template>
                    <!-- 下拉选择 -->
                    <template v-else-if="item.configType === 'select'">
                      <el-select v-model="cfgValues[item.configKey]" size="small" style="width:200px" :placeholder="'请选择'" @change="onSelectChange(item)">
                        <el-option
                          v-for="opt in parseOptions(item.configOptions)"
                          :key="opt.value"
                          :label="$tf(opt.label)"
                          :value="opt.value"
                        />
                      </el-select>
                    </template>
                    <!-- 多选字段（如「货品查重字段」）：选项按 config_options.source 动态加载，含自定义字段 -->
                    <template v-else-if="item.configType === 'multi_fields'">
                      <el-select
                        v-model="cfgValues[item.configKey]"
                        multiple
                        collapse-tags
                        collapse-tags-tooltip
                        size="small"
                        style="width:360px"
                        :placeholder="'请选择'"
                        :loading="fieldOptionsLoading[optionSource(item)]"
                      >
                        <el-option-group
                          v-for="grp in groupedFieldOptions(item)"
                          :key="grp.group"
                          :label="$tf(grp.group)"
                        >
                          <el-option
                            v-for="opt in grp.options"
                            :key="opt.value"
                            :label="$tf(opt.label)"
                            :value="opt.value"
                          />
                        </el-option-group>
                      </el-select>
                      <div class="field-hint">{{ $tt("所选字段各自不允许重复；清空表示不做查重") }}</div>
                    </template>
                    <!-- 数字输入 -->
                    <template v-else-if="item.configType === 'number'">
                      <el-input-number
                        v-model="cfgValues[item.configKey]"
                        :min="0"
                        :precision="item.configKey === 'precision.quantity' || item.configKey === 'precision.amount' ? 0 : 2"
                        controls-position="right"
                        size="small"
                        style="width:120px"
                      />
                    </template>
                    <!-- 密码/密钥 -->
                    <template v-else-if="item.configType === 'password' || item.configType === 'secret'">
                      <el-input v-model="cfgValues[item.configKey]" type="password" show-password autocomplete="new-password" :placeholder="$tt('留空则不修改')" size="small" style="width:200px" />
                    </template>
                    <!-- 普通文本 -->
                    <template v-else>
                      <el-input v-model="cfgValues[item.configKey]" size="small" style="width:200px" />
                    </template>
                  </div>
                </div>
              </el-col>
            </el-row>
          </div>
          <!-- 空提示 -->
          <div v-if="currentSections.length === 0" class="empty-tip">
            <el-empty :description="$tt('该分组暂无参数配置项')" :image-size="80" />
          </div>
        </template>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:sys-param')
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled, Loading } from '@element-plus/icons-vue'
import { getSysConfigByGroup, batchUpdateSysConfig } from '@/api/system/sysConfig'
import { useColumnConfigStore } from '@/stores/columnConfig'
import { getMaterialDedupFieldOptions } from '@/api/basedata/material'
import { tt } from '@/i18n'

/**
 * 【高危参数切换确认（2026-10-14）】：切换前弹确认框、写明影响与不可逆，取消则回滚选择。
 * 影响说明必须讲"人话"：新单据走什么口径 / 历史数据不重算 / 已分摊结果不随来回切换回退。
 */
const CONFIRM_ON_CHANGE = {
  'sale.receipt.receive.mode': {
    title: '切换收款方式确认',
    build: (newLabel) => `收款方式是全局设置，切换后：\n`
      + `1、以后新开的收款单按「${newLabel}」收钱记账；\n`
      + `2、已收过钱的历史收款单不重算、金额不变；\n`
      + `3、之前汇总方式下收的钱，已按"从上到下顺序"固定分摊到订单明细行，这个分摊结果不随再次切换而回退或重排；\n`
      + `4、本操作不可逆。确认切换？`
  }
}

/**
 * 页签 → config_group 映射
 * 这是唯一硬编码的部分：5个页签对应的数据库 group 名
 * 新增参数项只需 INSERT 数据库（指定正确的 group/section/type/options），前端自动渲染
 */
const tabs = [
  { key: 'sys_param', label: '系统参数' },
  { key: 'purchase', label: '采购参数' },
  { key: 'sale', label: '销售参数' },
  { key: 'inventory', label: '库存参数' },
  { key: 'produce', label: '生产参数' },
  { key: 'subcontract', label: '委外参数' },
  { key: 'finance', label: '财务参数' }
]

const activeTab = ref('sys_param')
const loading = ref(false)
const saving = ref(false)

/** 当前页签的所有参数（原始数据，从后端加载） */
const currentItems = ref([])

/** 参数值的编辑态：{ configKey: value } —— 根据类型自动转换 */
const cfgValues = reactive({})

/**
 * 已停用的配置项黑名单：不再在界面渲染（数据仍保留在库里，随时可恢复）。
 * 2026-11-09 参数清理：新增 53 项经逐项反查确认「后端与前端业务代码均不读取」或「与其他键重复」，
 * 连同此前已停用的 4 项共 57 项只做界面隐藏，配置行保留、后端零改动；把键从这里移出即可恢复展示。
 */
const HIDDEN_CONFIG_KEYS = [
  // ---- 历史遗留（本次之前已停用）----
  'sys.display.specs', 'sys.material.duplicate.check', 'sys.auto.customer.no', 'sys.auto.supplier.no',
  // ---- 基础参数：无业务消费 ----
  'system.default.lang', 'system.default.timezone', 'system.default.page.size',
  'sys.phone.duplicate.check', 'sys.show.spec.in.dropdown', 'sys.base.currency',
  'sys.discount.calc.method', 'sys.loss.calc.method', 'sys.unified.column.enabled',
  'sys.ip.limit.enabled', 'sys.ip.limit.segment',
  // ---- 销售参数：无业务消费 ----
  'sale.delivery.default.days', 'sale.discount.max', 'sale.invoice.default', 'sale.order.prefix',
  'sale.price.manual.modify', 'sale.quote.price.strategy',
  'sale.order.allow.add.material', 'sale.outbound.allow.add.material',
  'sale.order.below.min.stock', 'sale.outbound.below.min.stock',
  // ---- 销售参数：与「价格策略启用 + 取价优先级」重复 ----
  'kgw_sale_price_strategy',
  // ---- 采购参数：无业务消费 ----
  'purchase.order.prefix', 'purchase.delivery.default.days', 'purchase.return.days',
  'purchase.invoice.default', 'purchase.price.manual.modify', 'purchase.price.over.max',
  'purchase.auto.generate.po', 'purchase.request.price.strategy', 'purchase.order.skip.request.price',
  'purchase.order.allow.add.material', 'purchase.inbound.allow.add.material',
  'purchase.order.over.max.stock', 'purchase.inbound.over.max.stock',
  'purchase.order.over.qty', 'purchase.inbound.over.qty', 'purchase.return.over.qty',
  'purchase.supplier.credit.check',
  // ---- 采购参数：与「价格策略启用 + 取价优先级」重复 ----
  'kgw_purchase_price_strategy',
  // ---- 库存参数：无业务消费 ----
  'inv.allow.expired.batch.outbound', 'inv.location.enabled',
  'inv.sync.default.bom.to.order', 'inv.sync.material.no.to.bom', 'inv.sync.material.price.to.bom',
  'inv.warning.reference', 'inventory.check.cycle.days',
  // ---- 库存参数：与其他真实生效项重复 ----
  'inv.check.stock.when.approving', 'inv.check.stock.when.draft', 'inv.sale.order.check.stock',
  'inventory.cost.method', 'inventory.default.warehouse'
]

/** 当前页签按 config_section 分组后的结构 */
const currentSections = computed(() => {
  if (!currentItems.value.length) return []
  const sectionMap = new Map()
  currentItems.value.forEach(item => {
    if (HIDDEN_CONFIG_KEYS.includes(item.configKey)) return
    const section = item.configSection || '其他参数'
    if (!sectionMap.has(section)) {
      sectionMap.set(section, [])
    }
    sectionMap.get(section).push(item)
  })
  // Map 保持插入顺序，但数据库已按 sort_order 排序，所以这里顺序正确
  return Array.from(sectionMap.entries()).map(([title, items]) => ({ title, items }))
})

/** 解析 config_options JSON 字符串为 [{label, value}] 数组 */
function parseOptions(raw) {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) return parsed
    // 兼容旧格式：逗号分隔字符串
    if (typeof raw === 'string' && raw.includes('=')) {
      return raw.split('|').map(pair => {
        const [label, value] = pair.split('=')
        return { label: label.trim(), value: value.trim() }
      })
    }
    return []
  } catch {
    return []
  }
}

/** 高危下拉参数切换确认（2026-10-14）：命中清单的键在切换时弹影响说明（含不可逆），取消则回滚本次选择 */
const originalValues = {}
async function onSelectChange (item) {
  const rule = CONFIRM_ON_CHANGE[item.configKey]
  if (!rule) return
  const newVal = String(cfgValues[item.configKey] ?? '')
  if (newVal === String(originalValues[item.configKey] ?? '')) return
  const opt = parseOptions(item.configOptions).find(o => String(o.value) === newVal)
  const newLabel = (opt && opt.label) || newVal
  try {
    await ElMessageBox.confirm(rule.build(newLabel), rule.title, {
      type: 'warning',
      confirmButtonText: '确认切换',
      cancelButtonText: '取消',
      closeOnClickModal: false
    })
    originalValues[item.configKey] = newVal
  } catch (e) {
    // 取消 → 回滚为库中现值
    cfgValues[item.configKey] = originalValues[item.configKey]
  }
}

/**
 * multi_fields 控件的字段选项：按 config_options.source 动态加载（含自定义字段），同一来源只拉一次。
 * 目前支持 source='material'（物料档案可查重字段，接口 /basedata/material/dedup-field-options）；
 * 新增来源时在此登记映射即可，不改渲染结构。
 */
const fieldOptions = reactive({})
const fieldOptionsLoading = reactive({})
const OPTION_SOURCE_LOADERS = {
  material: getMaterialDedupFieldOptions
}

/** 解析 config_options 中的选项来源标识 */
function optionSource(item) {
  try {
    const opt = JSON.parse(item.configOptions || '{}')
    return opt && opt.source ? String(opt.source) : ''
  } catch (e) {
    return ''
  }
}

/** 按来源加载选项（失败静默降级为空列表，不影响其它参数保存） */
async function ensureFieldOptions(item) {
  const src = optionSource(item)
  if (!src || fieldOptions[src] || fieldOptionsLoading[src]) return
  const loader = OPTION_SOURCE_LOADERS[src]
  if (!loader) {
    fieldOptions[src] = []
    return
  }
  fieldOptionsLoading[src] = true
  try {
    const list = await loader()
    fieldOptions[src] = Array.isArray(list) ? list : []
  } catch (e) {
    fieldOptions[src] = []
  } finally {
    fieldOptionsLoading[src] = false
  }
}

/** 选项按 group 归组（系统字段 / 自定义字段） */
function groupedFieldOptions(item) {
  const list = fieldOptions[optionSource(item)] || []
  const groups = []
  list.forEach(o => {
    const g = o.group || '可选字段'
    let grp = groups.find(x => x.group === g)
    if (!grp) {
      grp = { group: g, options: [] }
      groups.push(grp)
    }
    grp.options.push(o)
  })
  return groups
}

/** 默认查重字段（与后端 MaterialDedupService.DEFAULT_FIELDS 保持一致） */
const DEFAULT_MULTI_FIELDS = ['materialCode', 'materialName']

/**
 * 解析 multi_fields 配置值：
 * 缺失 / boolean 时代旧值 / 解析失败 → 默认「物料编码+物料名称」；解析出空数组 → 不查重
 */
function parseFieldList(raw) {
  if (raw == null || raw === '') return [...DEFAULT_MULTI_FIELDS]
  const s = String(raw).trim()
  if (s === 'true' || s === 'false') return [...DEFAULT_MULTI_FIELDS]
  if (s.startsWith('[')) {
    try {
      const arr = JSON.parse(s)
      return Array.isArray(arr) ? arr.map(v => String(v)) : [...DEFAULT_MULTI_FIELDS]
    } catch (e) {
      return [...DEFAULT_MULTI_FIELDS]
    }
  }
  const arr = s.split(',').map(x => x.trim()).filter(Boolean)
  return arr.length ? arr : []
}

/** 数组分块（每行显示3个参数） */
function chunked(arr, size) {
  const result = []
  for (let i = 0; i < arr.length; i += size) {
    result.push(arr.slice(i, i + size))
  }
  return result
}

/** 加载当前页签对应 group 的所有参数 */
async function loadConfig() {
  loading.value = true
  try {
    const list = await getSysConfigByGroup(activeTab.value)
    if (Array.isArray(list) && list.length > 0) {
      currentItems.value = list
      // 按类型转换值并填入编辑态
      list.forEach(item => {
        const key = item.configKey
        // 记录库中现值（高危参数切换确认取消时回滚用）
        originalValues[key] = item.configValue
        if (item.configType === 'boolean') {
          cfgValues[key] = item.configValue === 'true'
        } else if (item.configType === 'number') {
          cfgValues[key] = item.configValue != null && item.configValue !== '' ? Number(item.configValue) : 0
        } else if (item.configType === 'multi_fields') {
          // 多选字段：解析 JSON 数组填编辑态，并异步加载可选字段清单（含自定义字段）
          cfgValues[key] = parseFieldList(item.configValue)
          ensureFieldOptions(item)
        } else if (item.configType === 'password' || item.configType === 'secret') {
          // 安全：密钥/密码类不回填真值（留空＝不修改），避免把密钥明文放进页面
          cfgValues[key] = ''
        } else {
          cfgValues[key] = item.configValue || ''
        }
      })
    } else {
      currentItems.value = []
    }
  } catch (e) {
    ElMessage.error(tt('加载配置失败'))
    currentItems.value = []
  } finally {
    loading.value = false
  }
}

/** 切换页签时重新加载 */
function onTabChange() {
  loadConfig()
}

/** 保存当前页签的所有参数 */
async function onSave() {
  saving.value = true
  try {
    const items = currentItems.value
      // 安全：密钥/密码类参数留空＝不修改（不提交该键，避免把已配置的密钥/授权码清空）
      .filter(item => !((item.configType === 'password' || item.configType === 'secret')
        && !String(cfgValues[item.configKey] ?? '').trim()))
      .map(item => {
      const key = item.configKey
      let val = cfgValues[key]
      // 布尔转字符串
      if (item.configType === 'boolean') {
        val = String(val)
      } else if (item.configType === 'number') {
        val = String(val)
      } else if (Array.isArray(val)) {
        val = JSON.stringify(val)
      } else {
        val = String(val ?? '')
      }
      return {
        configKey: key,
        configValue: val,
        configName: item.configName,
        configGroup: item.configGroup,
        configType: item.configType
      }
    })
    await batchUpdateSysConfig(items)
    // 列表默认视图：立刻更新本机缓存，列表页下次打开（含刷新）首屏就按新值渲染，
    // 不再出现"设置成明细、刷新又看到汇总"的过渡态（2026-10-18）
    if (items.some(i => i.configKey === 'sys.list.default.view')) {
      useColumnConfigStore().setGlobalDefaultView(cfgValues['sys.list.default.view'])
    }
    ElMessage.success(tt('保存成功'))
  } catch (e) {
    ElMessage.error(tt('保存失败'))
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped lang="scss">
.kgw-sys-param {
  padding: 0;
  background: #f0f2f5;
  min-height: 100%;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
}
.param-toolbar {
  background: #fff;
  padding: 8px 16px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  gap: 8px;
  position: sticky;
  top: 0;
  z-index: 10;
}
.param-tabs {
  background: #fff;
  padding: 0 16px;
  :deep(.el-tabs__nav-wrap::after) { display: none; }
  :deep(.el-tabs__item) {
    font-size: 13px;
    height: 36px;
    line-height: 36px;
  }
  :deep(.el-tabs__active-bar) { background-color: #1890ff; }
  :deep(.el-tabs__item.is-active) { color: #1890ff; }
}
.section {
  background: #fff;
  padding: 16px 20px;
  margin-bottom: 8px;
}
.section-title {
  font-size: 14px;
  font-weight: 700;
  color: #333;
  margin-bottom: 16px;
  padding-left: 0;
  border: none;
  background: transparent;
}
.param-row {
  margin-bottom: 0 !important;
  &:not(:last-child) { margin-bottom: 12px !important; }
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
  flex-direction: column;
  gap: 6px;
}
.field-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
.loading-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: #999;
  font-size: 14px;
}
.empty-tip {
  padding: 40px 0;
}
:deep(.el-switch.is-checked .el-switch__core) { background-color: #1890ff; border-color: #1890ff; }
:deep(.el-button--primary) { background-color: #1890ff; border-color: #1890ff; }
:deep(.el-button--primary:hover) { background-color: #40a9ff; border-color: #40a9ff; }
</style>
