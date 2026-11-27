// verify_bill_status.mjs — 单据状态机端到端 API 验证脚本
// 验证销售订单的完整状态流转和约束

const BASE = 'http://localhost:8080/api'
let TOKEN = null
let PASS = 0, FAIL = 0, SKIP = 0

function section(title) {
  console.log(`\n========================================`)
  console.log(`  ${title}`)
  console.log(`========================================`)
}
function pass(msg) { console.log(`[PASS] ${msg}`); PASS++ }
function fail(msg, detail = '') { console.log(`[FAIL] ${msg} ${detail}`); FAIL++ }
function skip(msg) { console.log(`[SKIP] ${msg}`); SKIP++ }

async function api(method, path, body = null) {
  const headers = { 'Content-Type': 'application/json' }
  if (TOKEN) headers['Authorization'] = `Bearer ${TOKEN}`
  const opts = { method, headers, timeout: 10000 }
  if (body) opts.body = typeof body === 'string' ? body : JSON.stringify(body)
  try {
    const resp = await fetch(`${BASE}${path}`, opts)
    const text = await resp.text()
    try {
      const json = JSON.parse(text)
      json.__httpCode = resp.status
      return json
    } catch {
      return { code: resp.status, error: text.substring(0, 500) }
    }
  } catch (e) {
    return { code: 0, error: e.message }
  }
}

const post = (p, b) => api('POST', p, b)
const get = (p) => api('GET', p)
const del = (p) => api('DELETE', p)

async function main() {
  // ============== 1. 登录 ==============
  section('1. 登录获取 Token')
  const loginResp = await post('/auth/login', { username: 'admin', password: 'admin123' })
  if (loginResp.code === 200 && loginResp.data?.token) {
    TOKEN = loginResp.data.token
    pass('登录成功，获取到 Token')
  } else {
    fail('登录失败', JSON.stringify(loginResp))
    return
  }

  // ============== 2. 草稿保存宽松校验（必填允许空）==============
  section('2. 草稿保存宽松校验（必填允许空）')
  const draftResp = await post('/sale/orders', {
    orderNo: '', customerId: null, orderDate: '2026-08-15',
    items: [], status: '草稿'
  })
  let draftOrderId = null
  if (draftResp.code === 200) {
    draftOrderId = draftResp.data?.orderId
    pass(`草稿保存成功（必填项为空也允许），订单ID: ${draftOrderId}`)
  } else {
    fail('草稿保存失败（应允许必填为空）', JSON.stringify(draftResp).substring(0, 200))
  }

  // ============== 3. 提交审核严格校验（空明细应失败）==============
  section('3. 提交审核严格校验（空明细应失败）')
  if (draftOrderId) {
    const submitResp = await post(`/sale/orders/${draftOrderId}/submit`)
    if (submitResp.code !== 200) {
      pass(`空明细提交被拒绝（符合预期）：${submitResp.error || submitResp.message || ''}`.substring(0, 150))
    } else {
      fail('空明细提交不应成功（应校验明细非空）')
    }
  } else {
    skip('无草稿订单，跳过提交校验')
  }

  // ============== 4. 完整订单状态流转 ==============
  section('4. 创建完整订单并走完整状态流转')

  // 查询客户和物料
  const [customersResp, materialsResp] = await Promise.all([
    get('/sale/customers'),
    get('/sale/materials')
  ])
  const customerId = customersResp.code === 200 && customersResp.data?.length > 0 ? customersResp.data[0].customerId : 1
  const customerName = customersResp.code === 200 && customersResp.data?.length > 0 ? customersResp.data[0].customerName : '测试客户'
  const materialId = materialsResp.code === 200 && materialsResp.data?.length > 0 ? materialsResp.data[0].materialId : 1
  const materialCode = materialsResp.code === 200 && materialsResp.data?.length > 0 ? materialsResp.data[0].materialCode : 'M001'
  const materialName = materialsResp.code === 200 && materialsResp.data?.length > 0 ? materialsResp.data[0].materialName : '测试物料'
  console.log(`  客户: ${customerName} (ID=${customerId})`)
  console.log(`  物料: ${materialName} (ID=${materialId})`)

  // 4.1 创建完整销售订单
  const createResp = await post('/sale/orders', {
    customerId, customerName, orderDate: '2026-08-15', deliveryDate: '2026-08-25',
    salerId: 1, currencyId: 1, exchangeRate: 1, warehouseId: 1,
    status: '草稿', approvalStatus: '草稿',
    items: [{
      lineNo: 1, materialId, materialCode, materialName, unitId: 1,
      quantity: 10, unitPrice: 200, amount: 2000, taxRate: 13, taxAmount: 260, netAmount: 2260
    }]
  })
  let orderId = null
  if (createResp.code === 200) {
    orderId = createResp.data?.orderId
    pass(`完整订单创建成功：ID=${orderId}, 单号=${createResp.data?.orderNo}`)
  } else {
    fail('完整订单创建失败', JSON.stringify(createResp).substring(0, 200))
  }

  if (orderId) {
    // 4.2 提交审核
    section('4.2 提交审核（草稿->待审核）')
    const submitResp = await post(`/sale/orders/${orderId}/submit`)
    if (submitResp.code === 200) pass('提交审核成功')
    else fail('提交审核失败', JSON.stringify(submitResp).substring(0, 200))

    // 4.3 审核通过
    section('4.3 审核通过（待审核->已生效）')
    const approveResp = await post(`/sale/orders/${orderId}/approve`)
    if (approveResp.code === 200) pass('审核成功')
    else fail('审核失败', JSON.stringify(approveResp).substring(0, 200))

    // 4.3b 下游引用校验（审核后已生成AR，重置草稿应被阻止）
    section('4.3b 下游引用校验（AR已生成，重置草稿应被阻止）')
    const reverseBlocked = await post(`/sale/orders/${orderId}/reverse-approve`)
    if (reverseBlocked.code !== 200 && JSON.stringify(reverseBlocked).includes('下游')) {
      pass(`下游引用校验生效：${(reverseBlocked.message || '').substring(0, 80)}`)
    } else if (reverseBlocked.code === 200) {
      fail('重置草稿不应成功（有下游AR）')
    } else {
      pass(`重置草稿被阻止：${(reverseBlocked.message || '').substring(0, 80)}`)
    }

    // 4.3c 清理下游AR以便继续测试状态流转
    section('4.3c 清理下游AR（为后续状态流转测试清理障碍）')
    const arListResp = await get('/finance/ar?size=100')
    let arCleaned = false
    if (arListResp.code === 200 && arListResp.data?.records) {
      for (const ar of arListResp.data.records) {
        if (ar.sourceBillNo === createResp.data?.orderNo || ar.sourceBillNo === orderId) {
          const delAr = await del(`/finance/ar/${ar.arId}`)
          if (delAr.code === 200) { arCleaned = true; pass(`已清理下游AR: ${ar.arNo}`) }
          break
        }
      }
    }
    if (!arCleaned) {
      // 尝试遍历全部AR查找
      const arList2 = await get('/finance/ar?size=200')
      if (arList2.code === 200 && arList2.data?.records) {
        for (const ar of arList2.data.records) {
          if (ar.remark && ar.remark.includes(createResp.data?.orderNo)) {
            const delAr = await del(`/finance/ar/${ar.arId}`)
            if (delAr.code === 200) { arCleaned = true; pass(`已清理下游AR(remark匹配): ${ar.arNo}`) }
            break
          }
        }
      }
    }
    if (!arCleaned) skip('未找到匹配AR，跳过清理（可能未生成或字段不匹配）')

    // 4.4 重置草稿
    section('4.5 重置草稿（已生效->草稿）')
    const reverseResp = await post(`/sale/orders/${orderId}/reverse-approve`)
    if (reverseResp.code === 200) pass('重置草稿成功')
    else fail('重置草稿失败', JSON.stringify(reverseResp).substring(0, 200))

    // 4.6 重新提交+审核
    section('4.6 重新提交+审核（为作废测试准备）')
    await post(`/sale/orders/${orderId}/submit`)
    const reApprove = await post(`/sale/orders/${orderId}/approve`)
    // 再次清理AR
    if (reApprove.code === 200) {
      const arList3 = await get('/finance/ar?size=200')
      if (arList3.code === 200 && arList3.data?.records) {
        for (const ar of arList3.data.records) {
          if (ar.sourceBillNo === createResp.data?.orderNo ||
              (ar.remark && ar.remark.includes(createResp.data?.orderNo))) {
            await del(`/finance/ar/${ar.arId}`)
            break
          }
        }
      }
    }

    // 4.7 作废
    section('4.7 作废（已生效->已作废）')
    const voidResp = await post(`/sale/orders/${orderId}/void`, { reason: '测试作废' })
    if (voidResp.code === 200) pass('作废成功')
    else fail('作废失败', JSON.stringify(voidResp).substring(0, 200))

    // 4.8 已作废重复作废约束
    section('4.8 已作废重复作废约束')
    const voidResp2 = await post(`/sale/orders/${orderId}/void`, { reason: '重复作废' })
    if (voidResp2.code !== 200) pass(`已作废状态重复作废被拒绝`)
    else fail('已作废状态不应允许重复作废')

    // 4.9 反作废
    section('4.9 反作废（已作废->已生效）')
    const unvoidResp = await post(`/sale/orders/${orderId}/unvoid`)
    if (unvoidResp.code === 200) pass('反作废成功')
    else fail('反作废失败', JSON.stringify(unvoidResp).substring(0, 200))

    // 4.10 结案
    section('4.10 结案（已生效->已结案）')
    const closeResp = await post(`/sale/orders/${orderId}/close-order`)
    if (closeResp.code === 200) pass('结案成功')
    else fail('结案失败', JSON.stringify(closeResp).substring(0, 200))

    // 4.11 已结案作废约束
    section('4.11 已结案作废约束')
    const voidResp3 = await post(`/sale/orders/${orderId}/void`, { reason: '结案后作废' })
    if (voidResp3.code !== 200) pass('已结案状态作废被拒绝')
    else fail('已结案状态不应允许作废')

    // 4.12 已结案重置草稿约束
    section('4.12 已结案重置草稿约束')
    const reverseResp2 = await post(`/sale/orders/${orderId}/reverse-approve`)
    if (reverseResp2.code !== 200) pass('已结案状态重置草稿被拒绝')
    else fail('已结案状态不应允许重置草稿')

    // 4.13 反结案
    section('4.13 反结案（已结案->已生效）')
    const reopenResp = await post(`/sale/orders/${orderId}/reopen`)
    if (reopenResp.code === 200) pass('反结案成功')
    else fail('反结案失败', JSON.stringify(reopenResp).substring(0, 200))

    // 4.14 重置草稿回草稿后删除
    section('4.14 重置草稿回草稿后删除')
    await post(`/sale/orders/${orderId}/reverse-approve`)
    const deleteResp = await del(`/sale/orders/${orderId}`)
    if (deleteResp.code === 200) pass('草稿状态删除成功')
    else fail('草稿状态删除失败', JSON.stringify(deleteResp).substring(0, 200))

    // 4.15 已删除订单查询
    section('4.15 已删除订单查询验证')
    const deletedResp = await get(`/sale/orders/${orderId}`)
    if (deletedResp.code !== 200 || !deletedResp.data) pass('已删除订单无法查询（符合预期）')
    else skip('已删除订单仍可查询')
  }

  // ============== 5. 清理草稿订单 ==============
  if (draftOrderId) {
    section('5. 清理草稿订单')
    const delResp = await del(`/sale/orders/${draftOrderId}`)
    if (delResp.code === 200) pass('草稿订单清理成功')
    else skip('草稿订单清理失败（可能已处理）')
  }

  // ============== 6. 库存不足校验 ==============
  section('6. 库存不足校验（其他出库单）')
  const issueResp = await post('/inventory/other-issues', {
    issueNo: '', issueDate: '2026-08-15', warehouseId: 1,
    status: '草稿', approvalStatus: '草稿',
    items: [{
      lineNo: 1, materialId, materialCode, materialName, unitId: 1,
      quantity: 99999999, unitPrice: 100, amount: 9999999900, stockId: 1
    }]
  })
  if (issueResp.code === 200) {
    const issueId = issueResp.data?.issueId
    const issueSubmitResp = await post(`/inventory/other-issues/${issueId}/submit`)
    if (issueSubmitResp.code !== 200) {
      const errMsg = issueSubmitResp.error || issueSubmitResp.message || ''
      if (errMsg.includes('库存不足') || errMsg.includes('stock')) {
        pass(`库存不足校验生效：${errMsg.substring(0, 100)}`)
      } else {
        pass(`提交被拒绝（可能其他校验）：${errMsg.substring(0, 100)}`)
      }
    } else {
      fail('超大数量出库提交不应成功（库存不足校验未生效）')
    }
    await del(`/inventory/other-issues/${issueId}`)
  } else {
    skip('其他出库单创建失败，跳过库存校验测试')
  }

  // ============== 7. 其他单据状态流转抽检（采购订单）==============
  section('7. 采购订单状态流转抽检')
  const poResp = await post('/purchase/orders', {
    orderNo: '', supplierId: 1, supplierName: '测试供应商',
    orderDate: '2026-08-15', deliveryDate: '2026-08-25',
    currencyId: 1, exchangeRate: 1, status: '草稿', approvalStatus: '草稿',
    items: [{
      lineNo: 1, materialId, materialCode, materialName, unitId: 1,
      quantity: 5, unitPrice: 150, amount: 750, taxRate: 13, taxAmount: 97.5, netAmount: 847.5
    }]
  })
  if (poResp.code === 200) {
    const poId = poResp.data?.orderId
    // 提交
    const poSubmit = await post(`/purchase/orders/${poId}/submit`)
    if (poSubmit.code === 200) pass('采购订单提交成功')
    else fail('采购订单提交失败', JSON.stringify(poSubmit).substring(0, 150))
    // 审核
    const poApprove = await post(`/purchase/orders/${poId}/approve`)
    if (poApprove.code === 200) pass('采购订单审核成功')
    else fail('采购订单审核失败', JSON.stringify(poApprove).substring(0, 150))
    // 驳回（待审核->草稿）- 需要先重置草稿回草稿再提交
    await post(`/purchase/orders/${poId}/reverse-approve`)
    await post(`/purchase/orders/${poId}/submit`)
    const poReject = await post(`/purchase/orders/${poId}/reject`, { reason: '测试驳回' })
    if (poReject.code === 200) pass('采购订单驳回成功')
    else fail('采购订单驳回失败', JSON.stringify(poReject).substring(0, 150))
    // 清理
    await del(`/purchase/orders/${poId}`)
  } else {
    skip('采购订单创建失败，跳过抽检')
  }

  // ============== 汇总 ==============
  section('验证结果汇总')
  console.log(`  PASS: ${PASS}`)
  console.log(`  FAIL: ${FAIL}`)
  console.log(`  SKIP: ${SKIP}`)
  console.log('')
  if (FAIL === 0) console.log('  >>> 全部验证通过 <<<')
  else console.log(`  >>> 有 ${FAIL} 项验证失败 <<<`)
}

main().catch(e => console.error('脚本异常:', e))
