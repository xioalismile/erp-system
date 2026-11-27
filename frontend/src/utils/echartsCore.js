// 图表库按需注册统一出口（2026-10-14 性能优化：图表包 998KB → 约 400KB）
//
// 全系统图表只用到 4 种图（扫描于 2026-10-14）：柱状图 / 折线图 / 饼图 / 漏斗图，
// 辅助能力只用到 网格 / 悬浮提示 / 图例 / 标题 / 渐变色(echarts.graphic)。
// 新页面要画新图型（仪表盘/散点/雷达等）时，必须先在下面 use([...]) 补登记，
// 否则该图会静默空白（控制台不报错）。
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart, FunnelChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  // 标线（控制限 / 规格限 / 参考线）：SPC 控制图与直方图依赖它；漏登记会静默不画且控制台不报错
  MarkLineComponent,
  MarkPointComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { LabelLayout, UniversalTransition } from 'echarts/features'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  FunnelChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  MarkLineComponent,
  MarkPointComponent,
  CanvasRenderer,
  LabelLayout,
  UniversalTransition
])

export default echarts
export { echarts }
