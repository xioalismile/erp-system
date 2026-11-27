// 修复：错插到 if 语句里的 sortBy/sortOrder 参数 → 恢复 if 语句并把参数插到方法签名
const fs = require('fs');
const path = require('path');
const ROOT = 'D:/erp/prodsrc/智能ERP-项目/backend/src/main/java';

const targets = [
    'com/erp/sale/controller/SaleQuoteController.java',
    'com/erp/purchase/controller/PurchaseRequestController.java',
    'com/erp/sale/controller/SaleOrderController.java',
    'com/erp/sale/controller/SaleDeliveryController.java',
    'com/erp/sale/controller/SaleContractController.java',
    'com/erp/purchase/controller/PurchaseInboundController.java',
    'com/erp/purchase/controller/PurchaseOrderController.java',
    'com/erp/system/controller/SysOperateLogController.java',
    'com/erp/sale/controller/SaleInvoiceController.java',
    'com/erp/purchase/controller/PurchaseReturnController.java',
    'com/erp/inventory/controller/MaterialRequisitionController.java',
    'com/erp/sale/controller/SaleReturnController.java',
    'com/erp/purchase/controller/PurchaseContractController.java',
    'com/erp/purchase/controller/PurchaseInvoiceController.java',
];

const SB = '            @RequestParam(required = false) String sortBy,';
const SO = '            @RequestParam(required = false) String sortOrder) {';

for (const t of targets) {
    const f = path.join(ROOT, t);
    let lines = fs.readFileSync(f, 'utf-8').split('\n');
    let changed = false;
    for (let i = 0; i < lines.length - 2; i++) {
        const a = lines[i].trimEnd();
        if (a.endsWith(',') && lines[i + 1].trim() === '@RequestParam(required = false) String sortBy,' && lines[i + 2].trim() === '@RequestParam(required = false) String sortOrder) {') {
            // 损坏块：恢复第一行
            lines[i] = a.replace(/,$/, ') {');
            lines.splice(i + 1, 2);
            changed = true;
            // 向上找真正的方法签名末尾（以 ) { 结尾且含 @RequestParam 或 HttpServletRequest）
            for (let j = i - 1; j > 0; j--) {
                const s = lines[j];
                if (/\)\s*\{\s*$/.test(s) && (s.includes('@RequestParam') || s.includes('HttpServletRequest'))) {
                    lines[j] = s.replace(/\)\s*\{\s*$/, ',') + '\n' + SB + '\n' + SO;
                    break;
                }
            }
        }
    }
    if (changed) {
        fs.writeFileSync(f, lines.join('\n'));
        console.log('FIXED', t);
    } else {
        console.log('NOCHANGE', t);
    }
}
