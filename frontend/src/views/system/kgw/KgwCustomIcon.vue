<template>
  <div class="kgw-custom-icon">
    <div class="kgw-toolbar">
      <div class="kgw-tabs">
        <div class="kgw-tab active">{{ $tt("自定义图标") }}</div>
      </div>
      <div class="kgw-actions">
        <el-button v-permission="perm('save')" type="primary" size="small" @click="onSave" :loading="saving">{{ $tt("保存") }}</el-button>
        <el-button v-permission="perm('upload')" size="small">{{ $tt("上传图标") }}</el-button>
      </div>
    </div>
    <div class="kgw-body">
      <div class="kgw-grid-3">
        <div class="kgw-card" v-for="icon in cfg.icons" :key="icon.code">
          <div class="kgw-card-title">{{ icon.name }}</div>
          <div class="kgw-icon-preview">
            <i :class="icon.class" :style="{ color: icon.color, fontSize: '32px' }"></i>
          </div>
          <div class="kgw-hint">{{ icon.code }} · {{ icon.color }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:menu-name')
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSysConfigByKey, batchUpdateSysConfig } from '@/api/system/sysConfig'
import { tt } from '@/i18n'

const saving = ref(false)
const defaultCfg = {
  icons: [
    { name: '销售订单',   code: 'sale_order',    class: 'el-icon-document',         color: '#1890ff' },
    { name: '销售出库',   code: 'sale_delivery', class: 'el-icon-takeaway-box',     color: '#52c41a' },
    { name: '销售收款',   code: 'sale_receipt',  class: 'el-icon-wallet',           color: '#faad14' },
    { name: '采购订单',   code: 'purchase_order',class: 'el-icon-shopping-cart-2',  color: '#722ed1' },
    { name: '采购入库',   code: 'purchase_receipt', class: 'el-icon-box',           color: '#13c2c2' },
    { name: '采购付款',   code: 'purchase_payment', class: 'el-icon-money',         color: '#eb2f96' },
    { name: '库存查询',   code: 'inventory',     class: 'el-icon-goods',            color: '#f5222d' },
    { name: '调拨单',     code: 'transfer',      class: 'el-icon-sort',             color: '#fa541c' },
    { name: '盘点单',     code: 'stocktake',     class: 'el-icon-files',            color: '#a0d911' }
  ]
}
const cfg = reactive(JSON.parse(JSON.stringify(defaultCfg)))

async function loadConfig() {
  try {
    const raw = await getSysConfigByKey('kgw_custom_icon')
    if (raw && raw.configValue) Object.assign(cfg, JSON.parse(raw.configValue))
  } catch (e) {}
}
async function onSave() {
  saving.value = true
  try {
    await batchUpdateSysConfig([{
      configKey: 'kgw_custom_icon', configValue: JSON.stringify(cfg),
      configName: '自定义图标', configGroup: 'kgw_custom', configType: 'json'
    }])
    ElMessage.success(tt('保存成功'))
  } catch (e) { ElMessage.error(tt('保存失败: ') + e.message) }
  finally { saving.value = false }
}
onMounted(() => loadConfig())
</script>

<style scoped lang="scss">
@import './_kgw-common.scss';
.kgw-icon-preview {
  display: flex; align-items: center; justify-content: center;
  height: 50px;
  background: #fafafa; border-radius: 4px;
}
</style>
