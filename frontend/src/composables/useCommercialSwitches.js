/**
 * 全站商业口径总开关（设置中心 → 系统参数 → 基础设置）
 *
 * 两个开关：
 *   ① 启用含税业务（sys.tax.enabled）——关闭时全站单据按「不计税」：明细不显示税率/税额/含税价/不含税价，
 *      也不参与税额计算（税额恒 0、价税合计 = 金额）。
 *   ② 启用外币业务（sys.foreign.currency.enabled）——关闭时单据只用本位币：币种锁定为本位币、汇率锁 1。
 *
 * 默认值对齐升级前行为：含税 = 开、外币 = 关（用户不改配置则与旧版本一致）。
 * 共享同一份内存标记，任意页面读取到的都是同一次装载的值。
 */
import { ref } from 'vue'
import { useSysConfig } from './useSysConfig'

/** 是否启用含税业务（默认开） */
const taxEnabled = ref(true)
/** 是否启用外币业务（默认关） */
const foreignCurrencyEnabled = ref(false)

let loadPromise = null

function toBool(v, dft) {
  if (v === undefined || v === null || v === '') return dft
  return v === true || v === 'true' || v === '1' || v === 1
}

export function useCommercialSwitches() {
  const { loadSysConfig, getConfig } = useSysConfig()

  /**
   * 装载两个开关。默认强制向后端取一次最新值（设置中心改完保存后，重新打开单据立即生效）。
   * force=false 时走 useSysConfig 的本地缓存。
   */
  async function ensureLoaded(force = true) {
    if (loadPromise) return loadPromise
    loadPromise = (async () => {
      try {
        await loadSysConfig(force)
        taxEnabled.value = toBool(getConfig('sys.tax.enabled'), true)
        foreignCurrencyEnabled.value = toBool(getConfig('sys.foreign.currency.enabled'), false)
      } catch (e) {
        // 读取失败保持默认值（含税=开、外币=关），不阻断页面
      } finally {
        loadPromise = null
      }
      return { taxEnabled, foreignCurrencyEnabled }
    })()
    return loadPromise
  }

  return { taxEnabled, foreignCurrencyEnabled, ensureLoaded }
}

/** 明细中受「启用含税业务」总开关控制的列（关闭时整列隐藏） */
export const TAX_RELATED_COLUMN_PROPS = ['taxRate', 'taxAmount', 'taxType', 'priceWithTax', 'priceWithoutTax']
