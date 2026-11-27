/**
 * 售后维修单业务状态在统一状态机里的归类（防复发）
 *
 * 背景：售后维修单不使用「已生效」这个通用状态，而是走自己的业务态
 * 草稿 → 已受理 → 维修中 → 维修完成 → 已返还。
 * 后端的统一状态工具对未登记的中文态按「已生效类」处理，所以前端状态机
 * 也必须把这几个态登记为已生效类，否则编辑页的「作废 / 重置草稿」按钮
 * 会在提交后直接消失，单据变成无法撤销的死单。
 */
import { describe, it, expect } from 'vitest'
import {
  canVoid, canReverseApprove, canSave, canDelete, canEdit, canSubmit, isApproved
} from '@/utils/bill-status'

describe('售后维修单业务态归类', () => {
  const repairStatuses = ['已受理', '维修中', '维修完成', '已返还']

  it.each(repairStatuses)('%s 属于已生效类', (status) => {
    expect(isApproved(status)).toBe(true)
  })

  it.each(repairStatuses)('%s 允许作废', (status) => {
    expect(canVoid(status, 0)).toBe(true)
  })

  it.each(repairStatuses)('%s 允许重置草稿', (status) => {
    expect(canReverseApprove(status, 0)).toBe(true)
  })

  it.each(repairStatuses)('%s 不允许保存 / 提交 / 编辑', (status) => {
    expect(canSave(status, 0)).toBe(false)
    expect(canSubmit(status, 0)).toBe(false)
    expect(canEdit(status, 0)).toBe(false)
  })

  it('已受理 不允许直接删除（删除仍限草稿族）', () => {
    expect(canDelete('已受理', 0)).toBe(false)
  })

  it('已作废 仍不可作废', () => {
    expect(canVoid('已作废', 0)).toBe(false)
  })

  it('草稿 仍可保存但不能作废归类被放宽', () => {
    expect(canSave('草稿', 0)).toBe(true)
    expect(canVoid('草稿', 0)).toBe(true)
  })
})
