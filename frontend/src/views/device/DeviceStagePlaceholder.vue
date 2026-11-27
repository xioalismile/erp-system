<template>
  <div class="stage-page">
    <div class="stage-card">
      <div class="stage-tag">{{ stageLabel }}</div>
      <h2>{{ title }}</h2>
      <p class="stage-desc">{{ desc }}</p>
      <div class="stage-todo">
        <div class="todo-title">{{ $tt('这个页面将提供') }}</div>
        <ul>
          <li v-for="(item, idx) in todos" :key="idx">{{ item }}</li>
        </ul>
      </div>
      <div class="stage-actions">
        <el-button type="primary" size="small" @click="goLedger">{{ $tt('先去设备台账') }}</el-button>
      </div>
      <p class="stage-note">
        {{ $tt('说明：设备管理按阶段交付，本页面在对应阶段完成后会自动上线，不需要重新配置菜单。') }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { tt } from '@/i18n'

const route = useRoute()
const router = useRouter()

function pageInfoMap() {
  return {
  '/device/access': {
    stage: 2,
    desc: tt('这里用于生成设备接入凭证、选择接入通道、维护协议模板与数据点；设备能不能连上云端，全看这一页配得对不对。'),
    todos: [tt('一设备一凭证：生成、轮换、吊销'), tt('接入通道：MQTT / HTTP / TCP / WebSocket / 云端轮询'), tt('协议模板：Modbus TCP/RTU、OPC UA、MQTT、HTTP、自定义报文'), tt('数据点：地址、数据类型、单位换算、业务用途、报警上下限')]
  },
  '/device/debug': {
    stage: 2,
    desc: tt('配置完成后，在调试台看实时报文、点位实时值与解析结果对照，先在这里把设备调通，再谈生产联动。'),
    todos: [tt('连接状态与最近心跳'), tt('原始报文与解析结果双栏对照'), tt('点位试读与写值测试'), tt('模拟上报：走完整解析与关联链路，但不写入生产数据')]
  },
  '/device/monitor': {
    stage: 6,
    desc: tt('车间设备状态一屏看清：在线、运行、待机、报警、离线，以及每台设备当前在做哪张工单、今天做了多少。'),
    todos: [tt('设备卡片与状态总览'), tt('当前工单工序与实时产量'), tt('车间大屏模式（只读轮播）'), tt('离线与数据停滞提示')]
  },
  '/device/task': {
    stage: 4,
    desc: tt('设备与生产工单工序的关联清单。自动关联上的任务、人工下发的任务、需要改绑或解除的任务都在这里处理。'),
    todos: [tt('关联清单与来源标记（自动 / 人工 / 下发）'), tt('任务下发、改绑、解除、重试'), tt('关联留痕：谁、何时、什么原因')]
  },
  '/device/report-confirm': {
    stage: 5,
    desc: tt('设备产出的唯一人工闸门：设备计数达到阈值后先生成待确认单，确认后才写进工序进度。'),
    todos: [tt('待确认单列表与批量确认'), tt('确认后生成工序汇报单并推进工序'), tt('驳回需填原因，同周期不再自动生成')]
  },
  '/device/unmatched': {
    stage: 4,
    desc: tt('设备在跑但认不到工单工序时，数据不会丢也不会乱写，而是进入这里等人工认领。'),
    todos: [tt('按设备、时间段、计数留存'), tt('人工认领到指定工序'), tt('超期未认领自动标记过期')]
  },
  '/device/alarm': {
    stage: 6,
    desc: tt('设备报警的统一入口：点位越限、故障位、数据停滞与离线报警都在这里处理并可一键转维修。'),
    todos: [tt('报警等级、防抖与恢复'), tt('处理、忽略（需填原因）、转维修'), tt('同设备历史报警与触发曲线')]
  },
  '/device/inspection': {
    stage: 7,
    desc: tt('把班前班后的设备检查固定下来，异常可以直接转成维修工单，而不是只在口头说一句。'),
    todos: [tt('点检模板：点检项、标准、是否必填、是否拍照'), tt('按班次 / 日 / 周 / 月生成待点检任务'), tt('异常项转维修并互相引用')]
  },
  '/device/maintenance': {
    stage: 7,
    desc: tt('按天数、运行小时或累计产量自动提醒保养，避免「坏了才修」。'),
    todos: [tt('保养计划与周期'), tt('到期提醒与逾期升级'), tt('保养工单与备件更换')]
  },
  '/device/repair': {
    stage: 7,
    desc: tt('故障从报修到验收关闭的全过程记录，停机时长、故障原因与更换备件都可追溯。'),
    todos: [tt('人工报修 / 报警转单 / 点检异常转单'), tt('派工、维修、完工、验收'), tt('更换备件联动库存出库')]
  },
  '/device/spare': {
    stage: 7,
    desc: tt('备件不再是糊涂账：谁换的、换到哪台设备、消耗多少、库存还剩多少。'),
    todos: [tt('备件档案与关联物料'), tt('维修 / 保养更换备件'), tt('领用后生成库存出库单与安全库存提醒')]
  },
  '/device/report': {
    stage: 8,
    desc: tt('设备在线率、稼动率与 OEE、产量、故障、维保完成率、备件消耗的统一报表入口。'),
    todos: [tt('设备状态总览与在线率'), tt('OEE：时间稼动率 × 性能稼动率 × 合格率'), tt('故障分析、维保完成率、备件消耗')]
  },
  '/device/help': {
    stage: 8,
    desc: tt('接入指引、协议配置说明与常见问题，第一次接入设备时按这里一步步做。'),
    todos: [tt('三步走：建台账 → 配接入 → 看数据'), tt('各协议配置要点'), tt('常见连通性问题排查')]
  }
  }
}

const info = computed(() => pageInfoMap()[route.path] || { stage: 0, desc: tt('该页面正在规划中。'), todos: [] })
const title = computed(() => route.meta.title || tt('设备管理'))
const desc = computed(() => info.value.desc)
const todos = computed(() => info.value.todos)
const stageLabel = computed(() => info.value.stage ? tt(`计划第 ${info.value.stage} 阶段交付`) : tt('规划中'))

function goLedger() {
  router.push('/device/ledger')
}
</script>

<style scoped lang="scss">
.stage-page {
  min-height: 100%;
  background: #fff;
  padding: 28px 24px;
  box-sizing: border-box;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
}
.stage-card {
  max-width: 720px;
  margin: 0 auto;
  border: 1px solid #e6ebf2;
  border-radius: 10px;
  padding: 24px 26px;
  background: #fbfcfe;
}
.stage-tag {
  display: inline-block;
  background: #eaf1ff;
  color: #1f6feb;
  border-radius: 999px;
  padding: 2px 12px;
  font-size: 12px;
  margin-bottom: 10px;
}
h2 {
  margin: 0 0 10px;
  font-size: 18px;
  color: #1c2430;
}
.stage-desc {
  margin: 0 0 18px;
  font-size: 13.5px;
  line-height: 1.8;
  color: #5b6675;
}
.stage-todo {
  background: #fff;
  border: 1px solid #eef1f5;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 18px;
}
.todo-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
}
ul {
  margin: 0;
  padding-left: 18px;
}
li {
  font-size: 13px;
  color: #5b6675;
  line-height: 1.9;
}
.stage-actions {
  margin-bottom: 12px;
}
.stage-note {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.7;
}
</style>
