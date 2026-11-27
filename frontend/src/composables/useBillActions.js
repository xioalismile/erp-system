/**
 * 单据编辑页通用操作按钮逻辑
 *
 * 为使用 BillActions 组件的编辑页统一提供：
 * 审核(onApprove)、驳回(handleReject/doReject)、重置草稿(handleReverseApprove)、
 * 结案(handleClose)、反结案(handleReopen)、作废(handleVoid/doVoid)、
 * 反作废(handleUnvoid)、删除(handleDelete)、复制(handleCopy)
 * 以及驳回/作废弹窗状态（rejectDialog/rejectReason、voidDialog/voidReason）。
 *
 * 用法：
 *   const {
 *     rejectDialog, rejectReason, doReject,
 *     voidDialog, voidReason, doVoid,
 *     onApprove, handleReject, handleReverseApprove,
 *     handleClose, handleReopen, handleVoid, handleUnvoid,
 *     handleDelete, handleCopy
 *   } = useBillActions({
 *     form,                                  // 响应式表单对象（含 status/closeStatus）
 *     getId: () => form.receiptId,           // 返回单据主键
 *     api: {                                 // 各动作 API（缺失的动作自动提示不支持）
 *       approve: confirmReceipt,
 *       reject: rejectReceipt,
 *       reverseApprove: reverseApproveReceipt,
 *       close: closeOrderReceipt,
 *       reopen: reopenReceipt,
 *       void: voidReceipt,
 *       unvoid: unvoidReceipt,
 *       delete: deleteReceipt
 *     },
 *     reload: loadDetail,                    // 操作成功后刷新（可选）
 *     router,                                // 路由实例
 *     listPath: '/finance/payment-receipts',  // 删除成功后跳转
 *     newPath: '/finance/payment-receipts/new', // 复制跳转（可选）
 *     billLabel: '收款单'                    // 提示文案中的单据名
 *   })
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as BillStatus from '@/utils/bill-status'
import { tt, tf } from '@/i18n'

export function useBillActions(opts) {
  const {
    form,
    getId,
    api = {},
    reload,
    router,
    listPath = '/',
    newPath,
    billLabel = tt('单据')
  } = opts

  const rejectDialog = ref(false)
  const rejectReason = ref('')
  const voidDialog = ref(false)
  const voidReason = ref('')

  /** 中文动作名（提示语一律用中文，禁止把 reverseApprove 这类英文动作名抛给用户） */
  const ACTION_LABELS = {
    save: '保存', submit: '提交', approve: '审核', reject: '驳回',
    reverseApprove: '重置草稿', close: '结案', reopen: '反结案',
    void: '作废', unvoid: '反作废', delete: '删除'
  }
  const actionLabel = (action) => tt(ACTION_LABELS[action] || action)

  /**
   * 本页真实具备执行能力的动作（= 传进来的 api 里确有函数的键）。
   * 供 BillActions 决定按钮是否渲染：能力缺失 → 按钮不出现，杜绝"点了才说不支持"。
   */
  const supportedActions = Object.keys(api).filter(k => typeof api[k] === 'function')

  const unsupported = (action) =>
    ElMessage.info(`${tt('当前单据不支持')}“${actionLabel(action)}”${tt('操作')}`)

  async function confirmThen(action, message, successText) {
    const id = getId()
    if (!id) { ElMessage.warning(`${tt('请先保存')}${tf(billLabel)}`); return }
    const fn = api[action]
    if (typeof fn !== 'function') { unsupported(action); return }
    try {
      await ElMessageBox.confirm(message, tt('提示'), {
        confirmButtonText: tt('确定'),
        cancelButtonText: tt('取消'),
        type: 'warning'
      })
      await fn(id)
      ElMessage.success(successText)
      if (typeof reload === 'function') await reload()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.message || `${successText}${tt('失败')}`)
    }
  }

  async function onApprove() {
    if (!BillStatus.canApprove(form.status, form.closeStatus)) {
      ElMessage.warning(tt('当前状态不可审核'))
      return
    }
    await confirmThen('approve', `${tt('确认审核该')}${tf(billLabel)}${tt('？审核后将不可修改')}`, tt('审核成功'))
  }

  function handleReject() {
    if (!getId()) { ElMessage.warning(`${tt('请先保存')}${tf(billLabel)}`); return }
    if (!BillStatus.canReject(form.status, form.closeStatus)) {
      ElMessage.warning(tt('当前状态不可驳回'))
      return
    }
    rejectReason.value = ''
    rejectDialog.value = true
  }

  async function doReject() {
    if (!rejectReason.value || !rejectReason.value.trim()) {
      ElMessage.warning(tt('请输入驳回原因'))
      return
    }
    const id = getId()
    const fn = api.reject
    if (typeof fn !== 'function') { unsupported('驳回'); return }
    try {
      await fn(id, rejectReason.value.trim())
      ElMessage.success(tt('已驳回'))
      rejectDialog.value = false
      rejectReason.value = ''
      if (typeof reload === 'function') await reload()
    } catch (e) {
      ElMessage.error(e.message || tt('驳回失败'))
    }
  }

  async function handleReverseApprove() {
    if (!BillStatus.canReverseApprove(form.status, form.closeStatus)) {
      ElMessage.warning(tt('不是已生效，不可重置草稿'))
      return
    }
    await confirmThen('reverseApprove', `${tt('确认重置草稿？重置草稿后')}${tf(billLabel)}${tt('回到草稿态，可重新编辑')}`, tt('重置草稿成功'))
  }

  async function handleClose() {
    if (!BillStatus.canClose(form.status, form.closeStatus)) {
      ElMessage.warning(tt('当前状态不可结案'))
      return
    }
    await confirmThen('close', tt('确认结案？结案后可反结案'), tt('结案成功'))
  }

  async function handleReopen() {
    if (!BillStatus.canReopen(form.status, form.closeStatus)) {
      ElMessage.warning(tt('当前状态不可反结案'))
      return
    }
    await confirmThen('reopen', tt('确认反结案？'), tt('反结案成功'))
  }

  function handleVoid() {
    if (!getId()) { ElMessage.warning(`${tt('请先保存')}${tf(billLabel)}`); return }
    if (!BillStatus.canVoid(form.status, form.closeStatus)) {
      ElMessage.warning(tt('当前状态不可作废'))
      return
    }
    voidReason.value = ''
    voidDialog.value = true
  }

  async function doVoid() {
    if (!voidReason.value || !voidReason.value.trim()) {
      ElMessage.warning(tt('请输入作废原因'))
      return
    }
    const id = getId()
    const fn = api.void
    if (typeof fn !== 'function') { unsupported('作废'); return }
    try {
      await fn(id, voidReason.value.trim())
      ElMessage.success(tt('已作废'))
      voidDialog.value = false
      voidReason.value = ''
      if (typeof reload === 'function') await reload()
    } catch (e) {
      ElMessage.error(e.message || tt('作废失败'))
    }
  }

  async function handleUnvoid() {
    if (!BillStatus.canUnvoid(form.status)) {
      ElMessage.warning(tt('当前状态不可反作废'))
      return
    }
    await confirmThen('unvoid', tt('确认反作废？反作废后单据回到作废前的状态'), tt('反作废成功'))
  }

  async function handleDelete() {
    if (!getId()) { ElMessage.warning(`${tt('请先保存')}${tf(billLabel)}`); return }
    if (!BillStatus.canDelete(form.status, form.closeStatus)) {
      ElMessage.warning(tt('当前状态不可删除'))
      return
    }
    const fn = api.delete
    if (typeof fn !== 'function') { unsupported('删除'); return }
    try {
      await ElMessageBox.confirm(`${tt('确认删除该')}${tf(billLabel)}${tt('？删除后不可恢复')}`, tt('提示'), {
        confirmButtonText: tt('确定删除'),
        cancelButtonText: tt('取消'),
        type: 'error'
      })
      await fn(getId())
      ElMessage.success(tt('已删除'))
      if (router && listPath) router.push(listPath)
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.message || tt('删除失败'))
    }
  }

  function handleCopy() {
    const id = getId()
    if (!id) { ElMessage.warning(`${tt('请先保存')}${tf(billLabel)}${tt('再复制')}`); return }
    if (router && newPath) {
      router.push({ path: newPath, query: { copy: id } })
    } else {
      ElMessage.info(tt('复制功能暂未开放'))
    }
  }

  return {
    supportedActions,
    rejectDialog, rejectReason, doReject,
    voidDialog, voidReason, doVoid,
    onApprove, handleReject, handleReverseApprove,
    handleClose, handleReopen, handleVoid, handleUnvoid,
    handleDelete, handleCopy
  }
}
