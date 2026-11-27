/**
 * 单据提交确认弹窗（按审批开关动态提示）。
 *
 * 与后端语义一致（AbstractBillService / 各 ServiceImpl 的 approvalGateHelper.needApproval）：
 *   - 未开审批（全局关 或 单据级关 或 未接入开关）→ 提交后单据直接生效（已生效）
 *   - 开启审批 → 提交后进入审批流程：待审核 → 审批通过后生效
 *
 * 用法（编辑页/列表页通用）：
 *   import { confirmSubmit } from '@/utils/submit-confirm'
 *   const needApproval = await confirmSubmit({ billType: 'SALE_ORDER', billLabel: '订单', billNo: row.orderNo })
 *   // 用户点确定 → resolve(true/false)；点取消 → reject('cancel')，与 ElMessageBox 行为一致
 *   ElMessage.success(needApproval ? tt('已提交审批') : tt('提交成功，单据已生效'))
 */
import { ElMessageBox } from 'element-plus'
import { isBillApprovalEnabled } from './approval-gate'
import { tt } from '@/i18n'

export async function confirmSubmit (options = {}) {
  const { billType = '', billLabel = tt('单据'), billNo = '', messageBoxOptions = {} } = options
  const needApproval = await isBillApprovalEnabled(billType)
  const label = tt(billLabel)
  const prefix = billNo
    ? `${tt('确认提交')}${label}【${billNo}】？`
    : `${tt('确认提交该')}${label}？`
  const suffix = needApproval
    ? tt('提交后将进入审批流程，审批通过后生效')
    : tt('提交后单据将直接生效')
  await ElMessageBox.confirm(prefix + suffix, tt('提示'), {
    confirmButtonText: tt('确定'),
    cancelButtonText: tt('取消'),
    type: 'warning',
    ...messageBoxOptions
  })
  return needApproval
}

/** 提交成功后的提示文案（按审批开关区分） */
export function submitSuccessMessage (needApproval) {
  return needApproval ? tt('已提交审批') : tt('提交成功，单据已生效')
}
