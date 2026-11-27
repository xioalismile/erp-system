import { ref, readonly } from 'vue'
import { getSysConfigList } from '@/api/system/sysConfig'

const configMap = ref({})
const loaded = ref(false)
let loadingPromise = null

export function useSysConfig() {
  async function loadSysConfig(force = false) {
    if (loaded.value && !force) return configMap.value
    if (loadingPromise) return loadingPromise
    loadingPromise = (async () => {
      try {
        const list = await getSysConfigList()
        const map = {}
        if (Array.isArray(list)) {
          list.forEach(item => {
            map[item.configKey] = item.configValue
          })
        }
        configMap.value = map
        loaded.value = true
        return configMap.value
      } finally {
        loadingPromise = null
      }
    })()
    return loadingPromise
  }

  function getConfig(key, defaultValue = null) {
    const val = configMap.value[key]
    return val !== undefined ? val : defaultValue
  }

  function setConfig(key, value) {
    configMap.value[key] = value
  }

  return {
    config: readonly(configMap),
    loaded,
    loadSysConfig,
    getConfig,
    setConfig
  }
}
