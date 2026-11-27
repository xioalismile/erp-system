<template>
  <el-space size="small" wrap>
    <el-button
      v-if="showView !== false"
      v-permission="rowPerm('view')"
      link
      type="primary"
      @click="safeEmit('view')"
    >{{ $tt("查看") }}</el-button>
    <el-button
      v-if="showEdit !== false && canEdit"
      v-permission="rowPerm('edit')"
      link
      type="primary"
      @click="safeEmit('edit')"
    >{{ $tt("编辑") }}</el-button>
    <el-button
      v-if="showSubmit !== false && canSubmit"
      v-permission="rowPerm('submit')"
      link
      type="warning"
      @click="safeEmit('submit')"
    >{{ $tt("提交") }}</el-button>
    <el-button
      v-if="showApprove !== false && canApprove"
      v-permission="rowPerm('approve')"
      link
      type="success"
      @click="safeEmit('approve')"
    >{{ $tt("审核") }}</el-button>
    <el-button
      v-if="showReject !== false && canReject"
      v-permission="rowPerm('reject')"
      link
      type="warning"
      @click="safeEmit('reject')"
    >{{ $tt("驳回") }}</el-button>
    <el-button
      v-if="showReverseApprove !== false && canReverseApprove"
      v-permission="rowPerm('reverseApprove')"
      link
      type="warning"
      @click="safeEmit('reverseApprove')"
    >{{ $tt("重置草稿") }}</el-button>
    <el-button
      v-if="showClose !== false && canClose"
      v-permission="rowPerm('close')"
      link
      type="info"
      @click="safeEmit('close')"
    >{{ $tt("结案") }}</el-button>
    <el-button
      v-if="showReopen !== false && canReopen"
      v-permission="rowPerm('reopen')"
      link
      type="info"
      @click="safeEmit('reopen')"
    >{{ $tt("反结案") }}</el-button>
    <el-button
      v-if="showVoid !== false && canVoid"
      v-permission="rowPerm('void')"
      link
      type="danger"
      @click="safeEmit('void')"
    >{{ $tt("作废") }}</el-button>
    <el-button
      v-if="showUnvoid !== false && canUnvoid"
      v-permission="rowPerm('unvoid')"
      link
      type="danger"
      @click="safeEmit('unvoid')"
    >{{ $tt("反作废") }}</el-button>
    <el-button
      v-if="showDelete !== false && canDelete"
      v-permission="rowPerm('delete')"
      link
      type="danger"
      @click="safeEmit('delete')"
    >{{ $tt("删除") }}</el-button>
    <slot :row="row" />
  </el-space>
</template>

<script>
import {
  canEdit, canSubmit, canApprove, canReject, canReverseApprove,
  canClose, canReopen, canVoid, canUnvoid, canDelete
} from '@/utils/bill-status'
import { isApprovalGloballyEnabled, isBillApprovalEnabled } from '@/utils/approval-gate'
import { billPermCode } from '@/utils/billPermCode'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'

export default {
  name: 'BillRowActions',
  props: {
    row: { type: Object, default: () => ({}) },
    // 支持两种传参方式：
    //   方式 1：<BillRowActions :row="row" />              (status/closeStatus 自动从 row 取)
    //   方式 2：<BillRowActions :status="..." :close-status="..." :row="scope.row" /> (显式传入，优先级更高)
    status: { type: [String, Number], default: undefined },
    closeStatus: { type: [String, Number], default: undefined },
    // 兼容历史传参：statusKey / closeStatusKey 指定从 row 中读取的字段名
    statusKey: { type: String, default: 'status' },
    closeStatusKey: { type: String, default: 'closeStatus' },
    /**
     * 单据类型编码（与设置中心「单据审批开关」一致，如 SALE_ORDER / PURCHASE_REQUEST）。
     * 传空 = 未接入开关 → 一律隐藏审核/驳回/重置草稿
     */
    billType: { type: String, default: '' },
    showView: { type: Boolean, default: true },
    showEdit: { type: Boolean, default: true },
    showSubmit: { type: Boolean, default: true },
    showApprove: { type: Boolean, default: true },
    showReject: { type: Boolean, default: true },
    showReverseApprove: { type: Boolean, default: true },
    showClose: { type: Boolean, default: true },
    showReopen: { type: Boolean, default: true },
    showVoid: { type: Boolean, default: true },
    showUnvoid: { type: Boolean, default: true },
    showDelete: { type: Boolean, default: true }
  },
  emits: [
    'view', 'edit', 'submit', 'approve', 'reject', 'reverseApprove',
    'close', 'reopen', 'void', 'unvoid', 'delete'
  ],
  data () {
    return {
      // null=未加载 true=审批开 false=审批关 → 强制隐藏 审核/驳回/重置草稿
      approvalGlobalEnabled: null,
      billApprovalEnabled: null,
      gateLoading: true
    }
  },
  async created () {
    try {
      this.approvalGlobalEnabled = await isApprovalGloballyEnabled()
    } catch (e) {
      this.approvalGlobalEnabled = true
    }
    try {
      this.billApprovalEnabled = await isBillApprovalEnabled(this.billType)
    } catch (e) {
      this.billApprovalEnabled = true
    } finally {
      this.gateLoading = false
    }
  },
  computed: {
    resolvedStatus () {
      if (this.status !== undefined) return this.status
      return this.row?.[this.statusKey]
    },
    resolvedCloseStatus () {
      if (this.closeStatus !== undefined) return this.closeStatus
      return this.row?.[this.closeStatusKey]
    },
    /** 综合：全局关 或 单据级关 → 隐藏审核/驳回/重置草稿按钮 */
    approvalOff () {
      if (this.approvalGlobalEnabled === false) return true
      if (this.gateLoading || this.billApprovalEnabled === null) return false
      return this.billApprovalEnabled === false
    },
    canEdit () { return canEdit(this.resolvedStatus, this.resolvedCloseStatus) },
    canSubmit () { return canSubmit(this.resolvedStatus, this.resolvedCloseStatus) },
    // 关审批时，approve/reject 永远 false；reverseApprove 不受审批开关影响（状态回退操作）
    canApprove () { return !this.approvalOff && canApprove(this.resolvedStatus, this.resolvedCloseStatus) },
    canReject () { return !this.approvalOff && canReject(this.resolvedStatus, this.resolvedCloseStatus) },
    canReverseApprove () { return canReverseApprove(this.resolvedStatus, this.resolvedCloseStatus) },
    canClose () { return canClose(this.resolvedStatus, this.resolvedCloseStatus) },
    canReopen () { return canReopen(this.resolvedStatus, this.resolvedCloseStatus) },
    canVoid () { return canVoid(this.resolvedStatus, this.resolvedCloseStatus) },
    canUnvoid () { return canUnvoid(this.resolvedStatus) },
    canDelete () { return canDelete(this.resolvedStatus, this.resolvedCloseStatus) }
  },
  methods: {
    /**
     * 行内按钮权限码：按 `billType` 映射到 `<菜单码>:<动作>`（见 utils/billPermCode.js）。
     * billType 未登记 / 动作无码时返回空串 → v-permission 放行（宁可不管，也不能误隐藏）。
     */
    rowPerm (action) {
      return billPermCode(this.billType, action)
    },
    /**
     * 按钮点击统一安全 emit：
     * - 父未监听 / 父处理函数抛错时，立即 ElMessage.error 给出肉眼可见反馈，绝对禁止"点了没反应"
     * - emit payload: 优先使用父显式传入的 row prop；若父没传 row，回退由 statusKey/closeStatusKey 生成安全对象（保证父侧取 id/status/closeStatus 不炸）
     */
    safeEmit (evt) {
      let payload = (this.row && typeof this.row === 'object' && Object.keys(this.row).length > 0)
        ? this.row
        : {
            [this.statusKey]: this.resolvedStatus,
            [this.closeStatusKey]: this.resolvedCloseStatus
          }
      try {
        this.$emit(evt, payload)
      } catch (e) {
        if (typeof console !== 'undefined' && console.error) console.error('[BillRowActions] emit error', evt, e)
        try {
          ElMessage.error(tt('操作失败：') + (e && e.message ? e.message : String(e)))
        } catch (_) {}
      }
    }
  }
}
</script>
