<template>
  <div class="kgw-auto-push">
    <div class="kgw-toolbar">
      <div class="toolbar-title">{{ $tt("自动下推设置") }}</div>
      <div class="kgw-actions">
        <el-button v-permission="perm('refresh')" size="small" @click="loadConfig">{{ $tt("刷新") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" size="small" :loading="saving" @click="onSave">{{ $tt("保存") }}</el-button>
      </div>
    </div>

    <div class="kgw-body">
      <div class="config-card">
        <div class="card-title">{{ $tt("自动下推单据") }}</div>

        <div v-for="item in items" :key="item.key" class="switch-row">
          <div class="switch-info">
            <div class="switch-name">{{ $tf(item.label) }}</div>
            <div class="switch-desc">{{ $tt(item.desc) }}</div>
          </div>
          <el-switch
            v-model="form[item.key]"
            inline-prompt
            :active-text="$tt('开')"
            :inactive-text="$tt('关')"
            style="--el-switch-on-color:#67C23A; --el-switch-off-color:#C0C4CC"
          />
        </div>

        <div class="tips">
          <div class="tip-line">{{ $tt("自动生成的单据状态为已生效，并记录来源单据类型与来源单号。") }}</div>
          <div class="tip-line">{{ $tt("同一来源已有对应单据时不会重复生成；自动生成的单据不会再次触发反向自动下推。") }}</div>
          <div class="tip-line">{{ $tt("自动生成失败时，源单生效会整体回滚并提示失败原因。") }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:auto-push')
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSysConfigByKey, batchUpdateSysConfig } from '@/api/system/sysConfig'
import { tt } from '@/i18n'

const items = [
  {
    key: 'sale.auto.push.contract.to.order',
    label: '销售合同生效后自动生成销售订单',
    desc: '开启后，销售合同审核生效时自动生成一份已生效的销售订单，并记录来源单号。'
  },
  {
    key: 'sale.auto.push.order.to.contract',
    label: '销售订单生效后自动生成销售合同',
    desc: '开启后，销售订单审核生效时自动生成一份已生效的销售合同，并记录来源单号。'
  }
]

const form = reactive({
  'sale.auto.push.contract.to.order': false,
  'sale.auto.push.order.to.contract': false
})
const saving = ref(false)

function toBool(value) {
  return value === true || value === 'true' || value === '1'
}

async function loadConfig() {
  for (const item of items) {
    try {
      const cfg = await getSysConfigByKey(item.key)
      form[item.key] = toBool(cfg?.configValue)
    } catch (e) {
      form[item.key] = false
    }
  }
}

async function onSave() {
  saving.value = true
  try {
    await batchUpdateSysConfig(items.map(item => ({
      configKey: item.key,
      configValue: form[item.key] ? 'true' : 'false'
    })))
    ElMessage.success(tt('自动下推设置已保存'))
    await loadConfig()
  } catch (e) {
    ElMessage.error(e?.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped lang="scss">
.kgw-auto-push {
  background: #fff;
  min-height: 100%;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
  display: flex;
  flex-direction: column;
}

.kgw-toolbar {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  padding: 0 16px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  .toolbar-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }
}
.kgw-actions { display: flex; gap: 8px; }

.kgw-body {
  padding: 12px;
  flex: 1;
  overflow: auto;
  background: #f0f2f5;
}

.config-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px 20px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 4px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px dashed #ebeef5;
  &:last-of-type { border-bottom: none; }
}

.switch-info { flex: 1; min-width: 0; }
.switch-name {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
  margin-bottom: 4px;
}
.switch-desc {
  font-size: 12.5px;
  color: #909399;
  line-height: 1.6;
}

.tips {
  margin-top: 8px;
  padding: 10px 12px;
  background: #f5f9ff;
  border: 1px solid #d9e8ff;
  border-radius: 4px;
}
.tip-line {
  font-size: 12.5px;
  color: #606266;
  line-height: 1.8;
}
</style>
