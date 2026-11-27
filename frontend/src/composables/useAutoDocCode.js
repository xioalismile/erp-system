import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { isAutoGenerate } from '@/api/system/serialNoConfig'
import { tt } from '@/i18n'

/**
 * 通用单据编码自动生成 composable。
 *
 * 用法示例（在单据 Edit 页面 script setup 中）：
 *   import { useAutoDocCode } from '@/composables/useAutoDocCode'
 *   const { docCodeDisabled, autoFillDocCodeOnCreate } = useAutoDocCode('SALE_ORDER')
 *   // 新建模式下初始化时调用：
 *   //   onMounted(() => { if (!isEdit.value) autoFillDocCodeOnCreate(form, 'orderNo') })
 *   // 模板中：<el-input v-model="form.orderNo" :disabled="docCodeDisabled" ... />
 *
 * @param {string} docType 单据类型，与 sys_serial_no_config.doc_type 对应
 */
export function useAutoDocCode(docType) {
  const docCodeDisabled = ref(false)
  const autoConfigLoaded = ref(false)
  const isAuto = ref(true)

  /**
   * 新建单据模式下调用：根据后端配置决定编码是否自动生成（自动模式禁用输入框）。
   * 编辑模式不要调用。
   *
   * 注意：这里**不向后端取号**。
   * 历史实现调用 `GET /serial-no-config/generate/{docType}`，而该接口会持久化推进
   * sys_serial_no_config.current_serial —— 于是"点新增→关闭（未保存）"也永久消耗一个流水号，
   * 业务表里留下空洞（用户现象：编码乱跳、下一张号突然变大）。
   * 现改为：编码留空，保存时由后端在"编码为空"时按编号规则生成（内部有行锁 + 业务表实况取号/防重抬底），
   * 保存成功后由列表/详情回显真实编号。
   *
   * @param {object} formRef 表单对象 (reactive/ref)
   * @param {string} codeField 编号字段名，如 'orderNo' / 'quoteNo' / 'deliveryNo'
   */
  async function autoFillDocCodeOnCreate(formRef, codeField) {
    try {
      let auto = true
      try {
        const res = await isAutoGenerate(docType)
        auto = res === true || res?.data === true || Number(res?.data ?? res) === 1
      } catch (_) {
        auto = true
      }
      isAuto.value = auto
      autoConfigLoaded.value = true

      if (!auto) {
        docCodeDisabled.value = false
        return
      }
      docCodeDisabled.value = true

      // 自动编号：清空编码，交由后端在保存时生成（此处不消耗流水号）
      if (formRef && typeof formRef === 'object') {
        const target = formRef && typeof formRef.value === 'object' ? formRef.value : formRef
        if (target && codeField) {
          target[codeField] = ''
        }
      }
    } catch (e) {
      console.warn('[useAutoDocCode] 初始化失败，切换为手动输入模式', e)
      docCodeDisabled.value = false
    }
  }

  // 编号输入框占位符：自动模式提示保存后生成，手动模式提示手动输入
  const docCodePlaceholder = computed(() => (isAuto.value ? tt('保存后自动生成') : tt('请输入编号')))

  return {
    docCodeDisabled,
    autoConfigLoaded,
    isAuto,
    docCodePlaceholder,
    autoFillDocCodeOnCreate
  }
}
