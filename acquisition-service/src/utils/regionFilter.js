/**
 * 获客采集「地区筛选」匹配工具（纯函数，便于单测）
 *
 * 背景（2026-09-23 优化）：
 *  1. 原实现把地区过滤放在**详情采集之后**，逐家开详情页才判定，命中率低时大量详情采集白跑；
 *  2. 原拆词用「去掉省/市/区后缀后按空格切分」，对手输连写（"广州白云区"→整串"广州白云"）
 *     会要求地址含连续串"广州白云"，而详情地址是"广东省广州市白云区"→ 本应匹配的企业被误剔除；
 *  3. 原后缀词表漏了"地区/盟/林区"，"大兴安岭地区"会被切成"大兴安岭地"→ 同样误剔除。
 *
 * 本模块只做一件事：判定「详情地址是否属于目标地区」，拆词更准 + 长段贪心切分兜底。
 * 原则：宁可宽松放行（交给后续兜底），也绝不错杀。
 */

// 行政区划后缀（长后缀必须排在前面，否则"自治区"会被"区"先切走）
const AREA_SUFFIXES = [
  '维吾尔自治区', '壮族自治区', '回族自治区', '自治区', '特别行政区',
  '自治州', '自治县', '自治旗', '地区', '林区', '盟',
  '省', '市', '区', '县', '旗'
]

const SPLIT_RE = new RegExp(`(${AREA_SUFFIXES.join('|')})`, 'g')

/**
 * 地区文本 → 匹配关键词数组
 * '广东省广州市白云区' → ['广东','广州','白云']
 * '广州白云区'         → ['广州白云']（再交由 matchSegment 贪心切分）
 * '白云区'             → ['白云']
 * '大兴安岭地区'       → ['大兴安岭']
 * '内蒙古自治区'       → ['内蒙古']
 */
function parseAreaKeywords(area) {
  const raw = String(area || '').trim()
  if (!raw) return []
  const segments = raw
    .split(SPLIT_RE)
    .filter((s) => s && !AREA_SUFFIXES.includes(s))
    .map((s) => s.trim())
    .filter(Boolean)
  const words = segments.filter((s) => s.length >= 2)
  return words.length ? words : segments
}

/**
 * 单段匹配：
 *  - 整段命中 → 通过
 *  - 长段（≥4 字，如"广州白云"）→ 贪心切成 3/2 字前缀，子词全部命中也算通过
 * 这样"广州白云区"能匹配上"广东省广州市白云区"，且"石家庄长安"能匹配"河北省石家庄市长安区"
 */
function matchSegment(segment, text) {
  if (!segment) return true
  if (text.includes(segment)) return true
  if (segment.length < 4) return false
  for (let len = Math.min(3, segment.length - 2); len >= 2; len--) {
    const head = segment.slice(0, len)
    const rest = segment.slice(len)
    if (text.includes(head) && matchSegment(rest, text)) return true
  }
  return false
}

/**
 * 地区匹配判定
 * @param {string} detailArea        详情页抓到的「所属地区」
 * @param {string} registeredAddress 详情页抓到的「注册地址」
 * @param {string} areaInput         任务里配置的地区筛选（可为空）
 * @returns {{matched:boolean, reason:string, keywords:string[]}}
 *          reason: no-area(未设筛选) / no-address(无地址可判) / matched / mismatch
 */
function matchArea(detailArea, registeredAddress, areaInput) {
  const keywords = parseAreaKeywords(areaInput)
  if (!keywords.length) return { matched: true, reason: 'no-area', keywords }
  const text = `${detailArea || ''} ${registeredAddress || ''}`.trim()
  if (!text) return { matched: false, reason: 'no-address', keywords }
  const matched = keywords.every((kw) => matchSegment(kw, text))
  return { matched, reason: matched ? 'matched' : 'mismatch', keywords }
}

// ==================== 列表页前置粗筛（2026-09-23） ====================
// 目的：搜索结果列表里每一行往往带着注册地址（企查查实测：行内含「地址：广东省佛山市顺德区…」），
//      可以在「打开详情页之前」就把明显不在目标地区的企业叉掉，省掉最贵的详情采集。
//      实测依据：2026-09-23 那一轮共打开 60+ 家详情，最终只留下 2 家（约 97% 白跑）。
// 原则：只做「省级 + 保守」判定 —— 只有从「地址」段里读出明确的**其他**省份、且完全读不到目标省份时才剔除；
//      读不到地址、读不出省份、目标省也在行内，一律放行，交给详情阶段兜底。宁可漏筛，绝不错杀。

// 省级行政区核心词（去掉「省/市/自治区/特别行政区」等后缀，用于文本包含判定）
const PROVINCE_CORES = [
  '北京', '天津', '上海', '重庆',
  '河北', '山西', '内蒙古', '辽宁', '吉林', '黑龙江',
  '江苏', '浙江', '安徽', '福建', '江西', '山东', '河南', '湖北', '湖南',
  '广东', '广西', '海南', '四川', '贵州', '云南', '西藏', '陕西', '甘肃', '青海', '宁夏', '新疆',
  '香港', '澳门', '台湾'
]

/** 从地区文本里取「省级核心词」：'广东省广州市白云区' → '广东'，'内蒙古自治区…' → '内蒙古'；取不到返回 '' */
function provinceCoreOf(area) {
  const t = String(area || '').trim()
  if (!t) return ''
  const hits = PROVINCE_CORES.filter((p) => t.includes(p))
  if (!hits.length) return ''
  return hits.sort((a, b) => b.length - a.length)[0]
}

/**
 * 列表行前置判定
 * @returns {'other-province'|'unknown'}  other-province = 可确定在别的省（应提前剔除）；unknown = 判不出（放行）
 */
function judgeListTextProvince(listText, targetArea) {
  const target = provinceCoreOf(targetArea)
  if (!target) return 'unknown'
  const text = String(listText || '')
  if (!text) return 'unknown'
  // 只认「地址」之后的片段，避免公司名/经营范围里的省份名干扰判定
  const idx = text.indexOf('地址')
  if (idx < 0) return 'unknown'
  const addrPart = text.slice(idx, idx + 120)
  const hitOther = PROVINCE_CORES.some((p) => p !== target && addrPart.includes(p))
  if (!hitOther) return 'unknown'
  if (addrPart.includes(target)) return 'unknown' // 目标省也出现在地址里 → 保守放行
  return 'other-province'
}

module.exports = { parseAreaKeywords, matchSegment, matchArea, AREA_SUFFIXES, PROVINCE_CORES, provinceCoreOf, judgeListTextProvince }
