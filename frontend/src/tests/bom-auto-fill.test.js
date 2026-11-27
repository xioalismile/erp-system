/**
 * 明细自动带出BOM的并发防护（2026-10-26）
 *
 * 覆盖缺陷：选完成品/委外物料后，BOM 明细是异步返回的。
 * 用户在返回前再次改选物料 → 两次请求都通过「明细为空才带出」的前置判断，
 * 返回后各自追加 → 两套 BOM 叠加 / 同一套重复，生效时按行重复扣料。
 */
import { describe, it, expect } from 'vitest'
import { createAutoFillToken, hasFilledRows } from '@/utils/bomAutoFill'

describe('BOM 自动带出并发令牌', () => {
  it('只有最后一次 begin() 的令牌有效', () => {
    const token = createAutoFillToken()
    const first = token.begin()
    expect(token.isLatest(first)).toBe(true)

    const second = token.begin()
    // 第一次请求（旧物料）返回时必须被丢弃
    expect(token.isLatest(first)).toBe(false)
    expect(token.isLatest(second)).toBe(true)
  })

  it('连续多次选择只有最后一个落地', () => {
    const token = createAutoFillToken()
    const a = token.begin()
    const b = token.begin()
    const c = token.begin()
    expect([a, b, c].filter(t => token.isLatest(t))).toHaveLength(1)
    expect(token.isLatest(c)).toBe(true)
  })
})

describe('明细是否已有已选物料的行', () => {
  it('空明细 / 未传 返回 false（允许带出）', () => {
    expect(hasFilledRows([], 'product_id')).toBe(false)
    expect(hasFilledRows(null, 'product_id')).toBe(false)
    expect(hasFilledRows(undefined, 'product_id')).toBe(false)
  })

  it('只有空行（未选物料）返回 false（仍允许带出）', () => {
    expect(hasFilledRows([{ product_id: null }, { product_id: undefined }], 'product_id')).toBe(false)
  })

  it('已有选了物料的行返回 true（不再重复带出）', () => {
    expect(hasFilledRows([{ product_id: 11 }], 'product_id')).toBe(true)
  })

  it('委外发料清单按 materialId 判断', () => {
    expect(hasFilledRows([{ materialId: 0 }], 'materialId')).toBe(true)
    expect(hasFilledRows([{ materialId: null }], 'materialId')).toBe(false)
  })
})
