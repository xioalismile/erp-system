import request from '@/utils/request'

/** 开账引导状态：{ status: 'pending'|'done'|'skipped', industry, isAdmin } */
export function getOnboardingStatus() {
  return request.get('/system/onboarding/status')
}

/** 跳过引导（永不再自动弹，之后仍可对小楷说/手动执行） */
export function skipOnboarding() {
  return request.post('/system/onboarding/skip')
}

/** 行业清单（向导快捷选项）：[{ code, name, desc, ... }] */
export function listIndustries() {
  return request.get('/system/onboarding/industries')
}

/** 自然语言解析：一句话描述公司 → { industryCode, industryName, employeeCount, summary, matchedKeywords } */
export function parseOnboarding(text) {
  return request.post('/system/onboarding/parse', { text })
}

/** 预览（不写库）：生成计划（确认卡数据源） */
export function previewOnboarding(data) {
  return request.post('/system/onboarding/preview', data)
}

/** 执行按行业初始化（幂等，失败整体回滚） */
export function runOnboarding(data) {
  return request.post('/system/onboarding/initialize', data)
}
