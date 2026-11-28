<template>
  <div class="bill-actions" :class="{ 'bill-actions--collapse': collapse }" ref="barEl">
    <!-- 按钮权限（2026-09-19）：全部受「设置中心 → 角色权限 → 按钮权限」控制；
         默认口径 = 能看见菜单即按钮全有（v-permission 规则③：该对象下无显式按钮配置时命中菜单码放行）；
         保存/复制无按钮码（permCode 返回空串）→ 不判定，保持可见 -->
    <el-space :wrap="!collapse" ref="spaceEl">
      <el-button
        v-for="a in displayActions"
        :key="a.key"
        :type="a.type || ''"
        :plain="!!a.plain"
        :loading="!!a.loading"
        v-permission="a.perm"
        @click="onAction(a)"
      >
        <el-icon v-if="a.icon" class="bill-actions__icon"><component :is="a.icon" /></el-icon>{{ a.label }}
      </el-button>

      <!-- 放不下时，末尾次要按钮收进「更多」下拉（仅 collapse 模式；默认关闭，行为与旧版一致） -->
      <el-dropdown v-if="collapse && foldedActions.length" trigger="click" @command="onMoreCommand">
        <el-button>
          {{ $tt("更多") }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="a in foldedActions" :key="a.key" :command="a.key">
              <el-icon v-if="a.icon"><component :is="a.icon" /></el-icon>{{ a.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <slot />
    </el-space>
    <!-- 单据二维码（2026-09-27）：保存后的单据在本页常驻显示，手机对屏即可扫，无需点按钮/打印。
         视觉：竖向码牌（白底 + 细边框 + 圆角 + 标题小字），与左边一排按钮明确分层，既不裸奔也不抢戏。
         权限复用「打印」；新增页或无主键时不显示。
         collapse 模式下若按钮折到极限仍放不下，二维码会临时让位（腾出宽度给按钮）。 -->
    <template v-if="showQrBlock && !qrHidden">
      <span class="bill-actions__split" aria-hidden="true"></span>
      <QrCodeCard
        v-permission="permCode('print')"
        class="bill-actions__qr"
        type="BILL"
        :doc-type="billType"
        :biz-id="qrBizId"
        :size="56"
        :title="$tt('单据码')"
      />
    </template>
  </div>
</template>

<script>
import {
  canSave, canSubmit, canApprove, canReject, canReverseApprove,
  canClose, canReopen, canVoid, canUnvoid, canDelete
} from '@/utils/bill-status'
import { isApprovalGloballyEnabled, isBillApprovalEnabled } from '@/utils/approval-gate'
// 按钮权限码（2026-09-19）：按 billType 推导「设置中心按钮权限」同源码，空串=不判定
import { billPermCode } from '@/utils/billPermCode'
import { isBillApprover } from '@/api/dashboard'
import { ArrowDown } from '@element-plus/icons-vue'
import QrCodeCard from '@/components/QrCodeCard.vue'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'

export default {
  name: 'BillActions',
  props: {
    status: { type: [String, Number], default: '草稿' },
    closeStatus: { type: Number, default: 0 },
    isNew: { type: Boolean, default: false },
    /**
     * 单据类型编码（与设置中心「单据审批开关」一致，如 SALE_ORDER / PURCHASE_REQUEST）。
     * 传空 = 按未接入开关处理（一律隐藏 approve/reject/reverseApprove，提交即生效）
     */
    billType: { type: String, default: '' },
    /**
     * 本页真实具备执行能力的动作（由 useBillActions 的 supportedActions 传入）。
     * null = 未传（旧行为：不过滤，按钮只看状态）
     * 数组（含空数组）= 按能力过滤：不在列表里的动作按钮**不渲染**，
     * 杜绝"按钮在、点了弹「当前单据不支持」"的假按钮。
     */
    supportedActions: { type: Array, default: null },
    // 按钮显隐总开关（true = 允许该按钮出现在页面中，再由状态权限决定能不能点）
    showSave: { type: Boolean, default: true },
    showSubmit: { type: Boolean, default: true },
    showApprove: { type: Boolean, default: true },
    showReject: { type: Boolean, default: true },
    showReverseApprove: { type: Boolean, default: true },
    showClose: { type: Boolean, default: true },
    showReopen: { type: Boolean, default: true },
    showVoid: { type: Boolean, default: true },
    showUnvoid: { type: Boolean, default: true },
    showDelete: { type: Boolean, default: true },
    showPrint: { type: Boolean, default: true },
    showCopy: { type: Boolean, default: true },
    /** 是否在页头常驻显示「单据二维码」（2026-09-27）；新增页/无主键时无论传什么都不会显示 */
    showQrcode: { type: Boolean, default: true },
    /**
     * 放不下自动折叠（2026-10-09 新增，默认 false = 老行为，全站其余单据不受影响）。
     * 开启后：按钮一行排布，超出可用宽度时把末尾的次要按钮收进「更多」下拉；
     * 窗口拉宽 / 折叠态变化时自动重排。**保存、提交、打印、复制** 四个主操作永不折叠。
     * 采用页：由页面把「操作日志 / 单据专属动作」通过 extraActions 传入，一并参与折叠。
     */
    collapse: { type: Boolean, default: false },
    /**
     * 折叠模式下与统一按钮条同排渲染的「页面专属按钮」（2026-10-09）。
     * 每项：{ key, label, icon?, type?, plain?, perm?, loading?, show?, handler }
     * - perm 走 v-permission（空串不判定）
     * - show=false 时不渲染（由页面按状态/数据计算）
     * - 默认参与折叠（可 pin:true 固定不折）
     */
    extraActions: { type: Array, default: () => [] }
  },
  emits: [
    'save', 'submit', 'approve', 'reject', 'reverseApprove',
    'close', 'reopen', 'void', 'unvoid', 'delete', 'print', 'copy'
  ],
  components: { QrCodeCard, ArrowDown },
  data () {
    return {
      // null=未加载 true=审批已启用 false=审批已关闭（隐藏审核/驳回/重置草稿按钮）
      approvalGlobalEnabled: null,
      billApprovalEnabled: null,   // null=未知 true=单据级开 false=单据级关
      gateLoading: true,
      // null=未确认 true=当前用户是该单审批人 false=不是（2026-11-19：待审核状态下按此隐藏 审核/驳回 按钮）
      isApprover: null,
      approverCache: null,         // {key: 'type:id', status} 同单同状态不重复查询（不要用 _ 前缀：Vue 3 不代理 _ 开头的 data 键）
      // —— 折叠模式状态 ——
      foldedKeys: [],              // 已收进「更多」的动作 key（渲染顺序 = 从右往左依次折）
      qrHidden: false,             // 折到极限仍放不下时，二维码临时让位
      widthCache: {}               // key → 按钮自然宽度（用于判断折几个才够）
    }
  },
  watch: {
    collapse () {
      if (this.collapse) this.scheduleReflow()
      else { this.foldedKeys = []; this.qrHidden = false }
    },
    collapseSignature () {
      if (this.collapse) this.scheduleReflow()
    },
    status () { this.checkApprover() },
    '$route.params.id' () { this.checkApprover() }
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
      // 接口异常走保守：显示（后续还会由 canApprove 校验状态兜底）
      this.billApprovalEnabled = true
    } finally {
      this.gateLoading = false
    }
    this.checkApprover()
  },
  mounted () {
    if (!this.collapse) return
    this._onResize = () => this.scheduleReflow()
    window.addEventListener('resize', this._onResize)
    try {
      const host = this.$refs.barEl && this.$refs.barEl.parentElement
      if (host && typeof ResizeObserver !== 'undefined') {
        // 观察外层容器（宽度由页面布局决定，与按钮内容无关）→ 侧边栏收放 / 容器变宽变窄都能重排，
        // 且不会因自身内容变化产生回环。
        this._ro = new ResizeObserver(() => this.scheduleReflow())
        this._ro.observe(host)
      }
    } catch (_) {}
    this.scheduleReflow()
  },
  beforeUnmount () {
    if (this._onResize) window.removeEventListener('resize', this._onResize)
    if (this._raf) cancelAnimationFrame(this._raf)
    if (this._ro) { try { this._ro.disconnect() } catch (_) {} }
  },
  computed: {
    /**
     * 单据二维码主键：编辑页路由统一是 `.../:id`（新增页无该参数）。
     * 直接取路由 id，是为了"一处改动覆盖全部单据编辑页"——40+ 页无需逐页传参。
     */
    qrBizId () {
      try {
        const id = this.$route && this.$route.params ? this.$route.params.id : ''
        return id || ''
      } catch (e) {
        return ''
      }
    },
    /** 是否显示页头二维码：显式开 + 非新增 + 有单据类型 + 有主键 */
    showQrBlock () {
      return this.showQrcode !== false && !this.isNew && !!this.billType && !!this.qrBizId
    },
    /** 综合：全局关 或 单据级关 → 隐藏审核/驳回/重置草稿按钮 */
    approvalOff () {
      if (this.approvalGlobalEnabled === false) return true
      // 尚未完成初始化（gateLoading + billApprovalEnabled 未知）：
      //  用同步缓存兜底，拿不到就返回 true 先隐藏（异步加载完后 computed 自动重算）
      if (this.gateLoading || this.billApprovalEnabled === null) return false
      return this.billApprovalEnabled === false
    },
    /**
     * 审批人闸门（2026-11-19）：仅「待审核」状态且审批开着时，
     * 审核/驳回按钮按「当前用户是否该单审批人」显示——不是审批人直接看不到按钮。
     * 其它状态这两个按钮本就不显示，闸门恒放行。
     */
    approverGateOn () {
      return !this.approvalOff && !this.isNew && String(this.status) === '待审核'
    },
    isApproverOk () {
      if (!this.approverGateOn) return true
      return this.isApprover === true
    },
    // 显示许可：新建时只允许保存/提交，其它按钮一律隐藏；
    //          审批关时，approve/reject 按钮不可见；
    //          reverseApprove（重置草稿）是状态回退操作，不受审批开关影响——
    //          否则审批关时提交直接到「已生效」，用户将无法重置草稿回草稿从而无法删除
    showGate () {
      const approvalOff = this.approvalOff
      if (this.isNew) {
        return {
          save: true, submit: true, approve: false, reject: false,
          reverseApprove: false, close: false, reopen: false,
          void: false, unvoid: false, delete: false
        }
      }
      return {
        save: this.showSave,
        submit: this.showSubmit,
        approve: this.showApprove && !approvalOff,
        reject: this.showReject && !approvalOff,
        reverseApprove: this.showReverseApprove,
        close: this.showClose,
        reopen: this.showReopen,
        void: this.showVoid,
        unvoid: this.showUnvoid,
        delete: this.showDelete
      }
    },
    // 最终可见 = 总开关开 且 单据状态允许 且 本页具备执行能力
    // 能力过滤只作用于 useBillActions 驱动的动作（保存/提交由页面自行实现，不参与过滤）
    visible () {
      const s = this.status
      const c = this.closeStatus
      const g = this.showGate
      const hasCap = (a) => this.supportedActions === null || this.supportedActions.includes(a)
      return {
        canSave: g.save && canSave(s, c),
        canSubmit: g.submit && canSubmit(s, c),
        canApprove: !this.isNew && g.approve && hasCap('approve') && this.isApproverOk && canApprove(s, c),
        canReject: !this.isNew && g.reject && hasCap('reject') && this.isApproverOk && canReject(s, c),
        canReverseApprove: !this.isNew && g.reverseApprove && hasCap('reverseApprove') && canReverseApprove(s, c),
        canClose: !this.isNew && g.close && hasCap('close') && canClose(s, c),
        canReopen: !this.isNew && g.reopen && hasCap('reopen') && canReopen(s, c),
        canVoid: !this.isNew && g.void && hasCap('void') && canVoid(s, c),
        canUnvoid: !this.isNew && g.unvoid && hasCap('unvoid') && canUnvoid(s),
        canDelete: !this.isNew && g.delete && hasCap('delete') && canDelete(s, c)
      }
    },
    /** 统一动作清单：pin=true 的主操作（保存/提交/打印/复制）永不折叠 */
    ownActions () {
      const v = this.visible
      return [
        { key: 'save', label: tt('保存'), type: 'primary', isVisible: v.canSave, emit: 'save', perm: '', pin: true },
        { key: 'submit', label: tt('提交'), type: 'primary', plain: true, isVisible: v.canSubmit, emit: 'submit', perm: this.permCode('submit'), pin: true },
        { key: 'approve', label: tt('审核'), type: 'success', isVisible: v.canApprove, emit: 'approve', perm: this.permCode('approve') },
        { key: 'reject', label: tt('驳回'), type: 'warning', isVisible: v.canReject, emit: 'reject', perm: this.permCode('reject') },
        { key: 'reverseApprove', label: tt('重置草稿'), type: 'warning', plain: true, isVisible: v.canReverseApprove, emit: 'reverseApprove', perm: this.permCode('reverseApprove') },
        { key: 'close', label: tt('结案'), type: 'info', plain: true, isVisible: v.canClose, emit: 'close', perm: this.permCode('close') },
        { key: 'reopen', label: tt('反结案'), type: 'info', plain: true, isVisible: v.canReopen, emit: 'reopen', perm: this.permCode('reopen') },
        { key: 'void', label: tt('作废'), type: 'danger', plain: true, isVisible: v.canVoid, emit: 'void', perm: this.permCode('void') },
        { key: 'unvoid', label: tt('反作废'), type: 'danger', plain: true, isVisible: v.canUnvoid, emit: 'unvoid', perm: this.permCode('unvoid') },
        { key: 'delete', label: tt('删除'), type: 'danger', plain: true, isVisible: v.canDelete, emit: 'delete', perm: this.permCode('delete') },
        { key: 'print', label: tt('打印'), isVisible: this.showPrint, emit: 'print', perm: this.permCode('print'), pin: true },
        { key: 'copy', label: tt('复制'), isVisible: this.showCopy, emit: 'copy', perm: '', pin: true }
      ].filter(a => a.isVisible)
    },
    /** 页面专属按钮（统一 key 前缀 x_，避免与统一动作撞名） */
    extraList () {
      return (this.extraActions || [])
        .filter(e => e && e.show !== false)
        .map(e => ({ ...e, key: 'x_' + e.key, perm: e.perm || '' }))
    },
    /** 全部参与渲染的动作（未折叠 + 已折叠），顺序 = 展示顺序 */
    allActions () {
      return [...this.ownActions, ...this.extraList]
    },
    /** 当前实际渲染的动作（已折进「更多」的不渲染） */
    displayActions () {
      return this.allActions.filter(a => !this.foldedKeys.includes(a.key))
    },
    /** 已折进「更多」的动作（按原展示顺序，供下拉菜单渲染） */
    foldedActions () {
      return this.allActions.filter(a => this.foldedKeys.includes(a.key))
    },
    /** 可折叠动作的 key（渲染顺序；从末尾往前依次折） */
    collapsibleKeys () {
      return [...this.ownActions, ...this.extraList].filter(a => !a.pin).map(a => a.key)
    },
    /** 全量渲染时的 key 顺序（重排测量时 foldedKeys 已清空，此刻与 DOM 一一对应） */
    measureKeys () {
      const keys = this.allActions.map(a => a.key)
      if (this.$slots && this.$slots.default) keys.push('__slot__')
      return keys
    },
    /** 动作集合签名：集合变化（状态流转 / 权限变化）时触发重排 */
    collapseSignature () {
      return this.allActions.map(a => a.key).join(',')
    }
  },
  methods: {
    /**
     * 查询当前用户是否为该单据的审批人（仅待审核状态需要；同单同状态只查一次）。
     * 非审批人 → 审核/驳回按钮直接不渲染（2026-11-19 用户口径：不是我的东西我为什么要看）。
     */
    async checkApprover () {
      try {
        const id = this.$route && this.$route.params ? this.$route.params.id : ''
        if (!this.billType || !id || String(this.status) !== '待审核') {
          this.isApprover = null
          this.approverCache = null
          return
        }
        const key = this.billType + ':' + id
        if (this.approverCache && this.approverCache.key === key && this.approverCache.status === String(this.status)) return
        this.approverCache = { key, status: String(this.status) }
        this.isApprover = await isBillApprover(this.billType, id)
      } catch (e) {
        // 查询失败走保守显示（按钮出现，后端引擎仍会校验兜底）
        this.isApprover = true
      }
    },
    /**
     * 按钮权限码（2026-09-19）：`<模块:对象>:<动作后缀>`，取值来自 utils/billPermCode.js
     * （与后端 MENU_CODE / ACTION_BUTTON_SUFFIX、设置中心按钮权限三方同源）。
     * 返回空串 = 该单据未登记菜单码或该动作无按钮码 → v-permission 不判定（保持可见），
     * 保证"默认能看见菜单就有全部按钮"。
     */
    permCode (action) {
      return billPermCode(this.billType, action)
    },
    /**
     * 安全 emit：父处理函数 throw / 父未监听事件时，立即给出肉眼可见的错误提示，禁止"点了没反应"
     */
    safeEmit (evt, payload) {
      try {
        this.$emit(evt, payload)
      } catch (e) {
        if (typeof console !== 'undefined' && console.error) console.error('[BillActions] emit error', evt, e)
        try {
          ElMessage.error(tt('操作失败：') + (e && e.message ? e.message : String(e)))
        } catch (_) {}
      }
    },
    /** 统一动作点击 */
    onAction (a) {
      if (a.emit) return this.safeEmit(a.emit)
      if (typeof a.handler === 'function') {
        try { a.handler() } catch (e) {
          if (typeof console !== 'undefined' && console.error) console.error('[BillActions] extra action error', a.key, e)
          try { ElMessage.error(tt('操作失败：') + (e && e.message ? e.message : String(e))) } catch (_) {}
        }
      }
    },
    /** 「更多」下拉命令 → 触发对应动作 */
    onMoreCommand (key) {
      const a = this.allActions.find(x => x.key === key)
      if (a) this.onAction(a)
    },
    spaceElDom () {
      const c = this.$refs.spaceEl
      return c ? (c.$el || c) : null
    },
    /** 合并多次触发（窗口 resize / 容器变化 / 状态变化）为一帧一次重排 */
    scheduleReflow () {
      if (!this.collapse) return
      if (this._raf) cancelAnimationFrame(this._raf)
      this._raf = requestAnimationFrame(() => { this._raf = null; this.reflow() })
    },
    /**
     * 放不下自动折叠：先按全量渲染测一遍每个按钮的自然宽度，
     * 再「从末尾往前」折最少的按钮直到放得下；折过头（有富余）则逐个放回。
     * 折到极限仍放不下 → 二维码临时让位再试一次。
     */
    async reflow () {
      if (!this.collapse || this._busy) return
      this._busy = true
      try {
        this.foldedKeys = []
        this.qrHidden = false
        await this.$nextTick()
        const space = this.spaceElDom()
        if (!space) return
        // 缓存自然宽度（此刻未折叠，全部在 DOM 中）
        const items = space.querySelectorAll('.el-space__item')
        const keys = this.measureKeys
        items.forEach((itm, i) => {
          const k = keys[i]
          if (k) this.widthCache[k] = itm.getBoundingClientRect().width
        })
        for (let i = 0; i < 16; i++) {
          await this.$nextTick()
          if (!this.spaceElDom()) return
          const over = space.scrollWidth - space.clientWidth
          if (over > 1) {
            const rest = this.collapsibleKeys.filter(k => !this.foldedKeys.includes(k))
            if (!rest.length) break
            // 折一个：估算「省下的按钮宽度 + 一个间距」是否值得（自校正由下一轮实测完成）
            const nextKey = rest[rest.length - 1]
            this.foldedKeys = [...this.foldedKeys, nextKey]
            continue
          }
          // 有富余：尝试把最近折进去的放回（保证不过度折叠）
          if (this.foldedKeys.length) {
            const last = this.foldedKeys[this.foldedKeys.length - 1]
            const trial = this.foldedKeys.slice(0, -1)
            this.foldedKeys = trial
            await this.$nextTick()
            if (!this.spaceElDom()) return
            if (space.scrollWidth - space.clientWidth > 1) {
              this.foldedKeys = [...trial, last]
              break
            }
            continue
          }
          break
        }
        // 折到极限仍放不下 → 二维码让位，再尽量放回按钮
        await this.$nextTick()
        if (space.scrollWidth - space.clientWidth > 1 && !this.qrHidden) {
          this.qrHidden = true
          for (let i = 0; i < 6; i++) {
            if (!this.foldedKeys.length) break
            await this.$nextTick()
            if (!this.spaceElDom()) return
            if (space.scrollWidth - space.clientWidth > 1) break
            const last = this.foldedKeys[this.foldedKeys.length - 1]
            const trial = this.foldedKeys.slice(0, -1)
            this.foldedKeys = trial
            await this.$nextTick()
            if (!this.spaceElDom()) return
            if (space.scrollWidth - space.clientWidth > 1) {
              this.foldedKeys = [...trial, last]
              break
            }
          }
        }
      } finally {
        this._busy = false
      }
    }
  }
}
</script>

<style scoped>
/*
  顶部工具栏用法：必须与同一排的「操作日志」「下推」按钮同高、同一条水平线。
  历史坑（2026-09-14 实测）：
    这里原本是「表单底部操作栏」的样式 —— padding: 12px 0 4px（上下不对称）+ border-top 虚线 + margin-top: 16px。
    但 BillActions 在全部页面里都只被放在顶部工具栏（<div class="header-right">）里用，从不做底部操作栏，
    于是它占了 48px（12+32+4）且内容不居中（上 12 下 4 → 中心偏下 4px），
    而相邻的「操作日志」「下推」是干净的 32px 按钮 → 必然错位 4px，还会多出一条虚线和 16px 位移。
  修正：去掉底部操作栏的 padding/边框/外边距，改为一行 flex 居中。
  另外 EP 的 .el-space 是 display:inline-flex（vertical-align:top），包在块级 div 里会多出基线缝隙；
  这里把父级变成 flex 后它就变成 flex item，基线缝隙自然消失。
*/
.bill-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bill-actions__icon {
  margin-right: 4px;
}

/* 放不下自动折叠（2026-10-09，仅 collapse 模式生效，默认关闭时以下规则不命中）：
   本行整体可收缩并裁切，按钮行让出宽度给「更多」；每个按钮不参与压缩，便于用 scrollWidth 精确判定溢出。 */
.bill-actions--collapse {
  min-width: 0;
  flex: 0 1 auto;
  overflow: hidden;
}
.bill-actions--collapse :deep(.el-space) {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
}
.bill-actions--collapse :deep(.el-space__item) {
  flex: 0 0 auto;
}

/* 页头常驻单据二维码（2026-09-27）：
   左侧一条细分隔线把「动作区」与「身份码」切开，右侧是竖向码牌（白底圆角小卡）。
   这样码牌读起来是"这张单据的标识"，而不是"又一个按钮"。 */
.bill-actions__split {
  width: 1px;
  height: 26px;
  background: #e4e7ed;
  flex: 0 0 auto;
}
.bill-actions__qr {
  flex: 0 0 auto;
}
</style>
