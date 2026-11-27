<template>
  <transition name="obw-fade">
    <div v-if="visible" class="obw-overlay">
      <div class="obw-shell">
        <header class="obw-header">
          <div class="obw-brand">
            <span class="obw-brand-dot"></span>
            <span>{{ $tt("新客户开账 · 系统预设") }}</span>
          </div>
          <button class="obw-skip" @click="onSkip">{{ $tt("跳过设置，直接进入") }}</button>
        </header>

        <div class="obw-progress" v-if="step >= 1 && step <= 3">
          <div v-for="(s, i) in steps" :key="i" class="obw-step-dot" :class="{ active: step === s.step, done: step > s.step }">
            <span class="obw-step-num">{{ s.step }}</span>
            <span class="obw-step-label">{{ s.label }}</span>
          </div>
          <div class="obw-step-line" :style="{ width: ((step - 1) / 2 * 100) + '%' }"></div>
        </div>

        <main class="obw-body">
          <section v-if="step === 0" class="obw-welcome">
            <div class="obw-hero">
              <div class="obw-hero-icon">
                <svg viewBox="0 0 48 48" width="56" height="56" fill="none">
                  <path d="M24 4L4 14v20l20 10 20-10V14L24 4z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>
                  <path d="M24 24v26M4 14l20 10 20-10" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>
                </svg>
              </div>
              <h1 class="obw-title">{{ $tt("欢迎使用系统") }}</h1>
              <p class="obw-sub">{{ $tt("用 1 分钟，把系统按您的行业和规模预设好：部门、岗位权限、计量单位、演示资料、行业叫法，一次到位。") }}</p>
            </div>
            <div class="obw-nl-card">
              <label class="obw-nl-label">{{ $tt("一句话描述您的公司") }}</label>
              <textarea v-model="nlText" class="obw-nl-input" :placeholder="$tt('例如：我们做食品饮料的，大概12个人，有自己的工厂')" rows="3" @keydown.enter.ctrl="runParse"></textarea>
              <div class="obw-nl-examples">
                <span class="obw-nl-hint">{{ $tt("试试：") }}</span>
                <button v-for="ex in examples" :key="ex" class="obw-example-chip" @click="nlText = ex; runParse()">{{ ex }}</button>
              </div>
              <div class="obw-nl-actions">
                <el-button type="primary" size="large" :loading="parsing" :disabled="!nlText.trim()" class="obw-primary-btn" @click="runParse">
                  <el-icon><MagicStick /></el-icon>
                  <span>{{ $tt("智能识别并生成") }}</span>
                </el-button>
                <el-button size="large" class="obw-ghost-btn" @click="goManual">{{ $tt("手动选择行业和规模") }}</el-button>
              </div>
              <div v-if="parseResult" class="obw-parse-result" :class="{ low: parseResult.confidence === 0 }">
                <el-icon class="obw-parse-icon" :class="{ warn: parseResult.confidence === 0 }"><component :is="parseResult.confidence === 0 ? Warning : CircleCheck" /></el-icon>
                <span>{{ parseResult.summary }}</span>
              </div>
            </div>
          </section>

          <section v-if="step === 1" class="obw-step">
            <div class="obw-step-head">
              <h2 class="obw-step-title">{{ $tt("您公司属于哪个行业？") }}</h2>
              <p class="obw-step-sub">{{ $tt("选择最接近的行业，系统会按行业准备部门、角色和演示资料") }}</p>
            </div>
            <div class="obw-grid">
              <div v-for="ind in industries" :key="ind.code" class="obw-card" :class="{ active: pickIndustry === ind.code }" @click="pickIndustry = ind.code">
                <div class="obw-card-icon-wrap"><el-icon class="obw-card-icon"><component :is="iconOf(ind.code)" /></el-icon></div>
                <div class="obw-card-name">{{ ind.name }}</div>
                <div class="obw-card-desc">{{ ind.desc }}</div>
                <div class="obw-card-check"><el-icon><Check /></el-icon></div>
              </div>
            </div>
          </section>

          <section v-if="step === 2" class="obw-step">
            <div class="obw-step-head">
              <h2 class="obw-step-title">{{ $tt("公司大概多少人？") }}</h2>
              <p class="obw-step-sub">{{ $tt("人数不同，为您准备的部门、岗位和仓库数量也不同") }}</p>
            </div>
            <div class="obw-scale-grid">
              <div v-for="s in scales" :key="s.count" class="obw-scale-card" :class="{ active: pickCount === s.count }" @click="pickCount = s.count">
                <div class="obw-scale-people"><span v-for="n in s.peopleCount" :key="n" class="obw-person-dot" :class="{ dim: n > s.count }"></span></div>
                <div class="obw-scale-name">{{ s.label }}</div>
                <div class="obw-scale-desc">{{ s.desc }}</div>
                <div class="obw-scale-meta"><span>{{ s.deptHint }}</span></div>
                <div class="obw-card-check"><el-icon><Check /></el-icon></div>
              </div>
            </div>
          </section>

          <section v-if="step === 3" class="obw-step">
            <div class="obw-step-head">
              <h2 class="obw-step-title">{{ $tt("为你准备了") }}</h2>
              <p class="obw-step-sub" v-if="plan">{{ plan.industryName }} · {{ plan.scaleLabel }} · {{ $tt("演示资料") }} {{ plan.demoPerTable }} {{ $tt("条/表") }}</p>
            </div>
            <div v-if="previewLoading" class="obw-loading">
              <el-icon class="is-loading obw-loading-icon"><Loading /></el-icon>
              <p>{{ $tt("正在按行业和规模生成计划…") }}</p>
            </div>
            <template v-else-if="plan">
              <div class="obw-plan">
                <div class="obw-plan-section">
                  <div class="obw-plan-section-head"><el-icon><OfficeBuilding /></el-icon><span>{{ $tt("部门") }}（{{ plan.departments.length }} {{ $tt("个") }}）</span></div>
                  <div class="obw-plan-chips"><el-tag v-for="d in plan.departments" :key="d" size="large" effect="light" round>{{ d }}</el-tag></div>
                </div>
                <div class="obw-plan-section">
                  <div class="obw-plan-section-head"><el-icon><UserFilled /></el-icon><span>{{ $tt("岗位角色") }}（{{ plan.roles.length }} {{ $tt("个，含菜单权限") }}）</span></div>
                  <div class="obw-plan-chips"><el-tag v-for="r in plan.roles" :key="r" size="large" type="success" effect="light" round>{{ r }}</el-tag></div>
                </div>
                <div class="obw-plan-row-2">
                  <div class="obw-plan-section">
                    <div class="obw-plan-section-head"><el-icon><ScaleToOriginal /></el-icon><span>{{ $tt("计量单位") }}（{{ plan.units.length }}）</span></div>
                    <div class="obw-plan-chips"><el-tag v-for="u in plan.units" :key="u" size="large" type="info" effect="light" round>{{ u }}</el-tag></div>
                  </div>
                  <div class="obw-plan-section">
                    <div class="obw-plan-section-head"><el-icon><Box /></el-icon><span>{{ $tt("仓库") }}（{{ plan.warehouses.length }}）</span></div>
                    <div class="obw-plan-chips"><el-tag v-for="w in plan.warehouses" :key="w" size="large" type="warning" effect="light" round>{{ w }}</el-tag></div>
                  </div>
                </div>
                <div class="obw-plan-section">
                  <div class="obw-plan-section-head"><el-icon><Files /></el-icon><span>{{ $tt("演示资料") }}</span></div>
                  <div class="obw-plan-chips">
                    <el-tag size="large" type="info" effect="light" round>{{ $tt("物料") }} {{ plan.materialCount }}</el-tag>
                    <el-tag size="large" type="info" effect="light" round>{{ $tt("客户") }} {{ plan.customerCount }}</el-tag>
                    <el-tag size="large" type="info" effect="light" round>{{ $tt("供应商") }} {{ plan.supplierCount }}</el-tag>
                  </div>
                </div>
                <div class="obw-plan-section" v-if="renameList.length > 0">
                  <div class="obw-plan-section-head"><el-icon><EditPen /></el-icon><span>{{ $tt("系统叫法将调整为") }}</span></div>
                  <div class="obw-plan-chips"><el-tag v-for="r in renameList" :key="r.key" size="large" type="danger" effect="light" round>{{ r.key }} → {{ r.value }}</el-tag></div>
                </div>
                <div class="obw-plan-note"><el-icon><InfoFilled /></el-icon>{{ $tt("以上均为演示数据，可随时删除；重复执行只会补缺，不会动您已建的数据。") }}</div>
              </div>
            </template>
          </section>

          <section v-if="step === 4" class="obw-done">
            <div class="obw-done-icon"><el-icon><CircleCheckFilled /></el-icon></div>
            <h1 class="obw-title">{{ $tt("系统预设完成！") }}</h1>
            <p class="obw-sub" v-if="doneResult">{{ $tt("已为您生成") }} {{ doneResult.industryName }} {{ $tt("行业的基础配置") }}</p>
            <div class="obw-done-grid" v-if="doneResult">
              <div class="obw-done-item"><div class="obw-done-num">{{ doneResult.departments }}</div><div class="obw-done-label">{{ $tt("个部门") }}</div></div>
              <div class="obw-done-item"><div class="obw-done-num">{{ doneResult.roles }}</div><div class="obw-done-label">{{ $tt("个岗位角色") }}</div></div>
              <div class="obw-done-item"><div class="obw-done-num">{{ doneResult.units }}</div><div class="obw-done-label">{{ $tt("个计量单位") }}</div></div>
              <div class="obw-done-item"><div class="obw-done-num">{{ doneResult.warehouses }}</div><div class="obw-done-label">{{ $tt("个仓库") }}</div></div>
              <div class="obw-done-item obw-done-item-wide"><div class="obw-done-num">{{ doneResult.materials }} / {{ doneResult.customers }} / {{ doneResult.suppliers }}</div><div class="obw-done-label">{{ $tt("条演示物料/客户/供应商") }}</div></div>
            </div>
            <p class="obw-step-sub">{{ $tt("想微调系统叫法，去 设置中心 → 自定义 → 字段名称设置。") }}</p>
          </section>
        </main>

        <footer class="obw-footer" v-if="step >= 1 && step <= 3">
          <el-button v-if="step > 1" size="large" @click="step--">{{ $tt("上一步") }}</el-button>
          <el-button v-if="step === 1" type="primary" size="large" :disabled="!pickIndustry" @click="step = 2">{{ $tt("下一步") }}</el-button>
          <el-button v-if="step === 2" type="primary" size="large" :disabled="!pickCount" @click="goPreview">{{ $tt("下一步：查看计划") }}</el-button>
          <el-button v-if="step === 3" type="primary" size="large" :loading="running" @click="runNow">{{ $tt("确认，开始设置") }}</el-button>
        </footer>
        <footer class="obw-footer" v-if="step === 4">
          <el-button size="large" @click="finishGoDept">{{ $tt("去看看部门") }}</el-button>
          <el-button type="primary" size="large" @click="finish">{{ $tt("开始使用系统") }}</el-button>
        </footer>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Grid, Shop, Operation, Cpu, Tools, ShoppingBag, CoffeeCup, Filter,
  HomeFilled, ShoppingCart, Service, Loading,
  UserFilled, OfficeBuilding,
  Check, CircleCheck, CircleCheckFilled, Warning, InfoFilled,
  MagicStick, ScaleToOriginal, Box, Files, EditPen
} from '@element-plus/icons-vue'
import { getOnboardingStatus, skipOnboarding, listIndustries, previewOnboarding, runOnboarding, parseOnboarding } from '@/api/system/onboarding'
import { reloadFieldLabelsForScope } from '@/i18n'
import { tt } from '@/i18n'
import { useUserStore } from '@/stores/user'

const emit = defineEmits(['finished'])
const router = useRouter()
const userStore = useUserStore()

const visible = ref(false)
const step = ref(0)
const industries = ref([])
const pickIndustry = ref('')
const pickCount = ref(5)
const previewLoading = ref(false)
const running = ref(false)
const plan = ref(null)
const doneResult = ref(null)

const nlText = ref('')
const parsing = ref(false)
const parseResult = ref(null)

const steps = [
  { step: 1, label: tt('行业') },
  { step: 2, label: tt('规模') },
  { step: 3, label: tt('确认') }
]

const examples = [
  tt('做食品饮料的，12个人'),
  tt('服装批发，5个人小团队'),
  tt('电子厂，30人左右'),
  tt('电商直播带货，8个人')
]

const scales = [
  { count: 5, label: tt('5人以内'), desc: tt('小团队 / 刚起步'), peopleCount: 5, deptHint: tt('精简部门配置') },
  { count: 12, label: tt('6 ~ 20人'), desc: tt('小型公司'), peopleCount: 8, deptHint: tt('标准部门配置') },
  { count: 30, label: tt('21 ~ 50人'), desc: tt('成长型企业'), peopleCount: 10, deptHint: tt('完整部门配置') },
  { count: 60, label: tt('50人以上'), desc: tt('规模化企业'), peopleCount: 12, deptHint: tt('全量部门配置') }
]

const ICONS = {
  GENERAL: Grid, TRADE: Shop, MANUFACTURE: Operation, ELECTRONICS: Cpu,
  HARDWARE: Tools, APPAREL: ShoppingBag, FOOD: CoffeeCup, CHEMICAL: Filter,
  FURNITURE: HomeFilled, ECOMMERCE: ShoppingCart, SERVICE: Service
}
function iconOf(code) { return ICONS[code] || Grid }

const renameList = computed(() => {
  const m = (plan.value && plan.value.fieldRenames) || {}
  return Object.keys(m).map(k => ({ key: k, value: m[k] }))
})

onMounted(async () => {
  try {
    for (let i = 0; i < 40; i++) {
      const r = Array.isArray(userStore.roles) ? userStore.roles : []
      if (r.length > 0) break
      await new Promise(resolve => setTimeout(resolve, 200))
    }
    const roles = Array.isArray(userStore.roles) ? userStore.roles : []
    if (!roles.includes('ADMIN') && !roles.includes('MANAGER') && !roles.includes('SUPER_ADMIN')) return
    const st = await getOnboardingStatus()
    if (!st || st.status !== 'pending') return
    const list = await listIndustries()
    industries.value = Array.isArray(list) ? list : []
    visible.value = true
  } catch (_) {}
})

async function runParse() {
  if (!nlText.value.trim()) return
  parsing.value = true
  parseResult.value = null
  try {
    const r = await parseOnboarding(nlText.value.trim())
    parseResult.value = r
    pickIndustry.value = r.industryCode
    pickCount.value = r.employeeCount
    if (r.confidence > 0) {
      await goPreview()
    } else {
      step.value = 1
    }
  } catch (_) {
    ElMessage.error(tt('识别失败，请手动选择'))
  } finally {
    parsing.value = false
  }
}

function goManual() { step.value = 1 }

async function goPreview() {
  step.value = 3
  previewLoading.value = true
  plan.value = null
  try {
    plan.value = await previewOnboarding({ industryCode: pickIndustry.value, employeeCount: pickCount.value })
  } catch (_) {
    ElMessage.error(tt('生成计划失败，请重试'))
    step.value = 2
  } finally {
    previewLoading.value = false
  }
}

async function runNow() {
  running.value = true
  try {
    const res = await runOnboarding({ industryCode: pickIndustry.value, employeeCount: pickCount.value })
    doneResult.value = res
    step.value = 4
    reloadFieldLabelsForScope()
  } catch (_) {
    ElMessage.error(tt('初始化失败（已整体回滚），请重试'))
  } finally {
    running.value = false
  }
}

async function onSkip() {
  try {
    await ElMessageBox.confirm(
      tt('跳过后系统保持空白，之后仍可在 设置中心 或 对小楷说"按行业初始化" 补做。确定跳过吗？'),
      tt('跳过开账设置'), { type: 'warning', confirmButtonText: tt('确定跳过'), cancelButtonText: tt('继续设置') })
  } catch (_) { return }
  try { await skipOnboarding() } catch (_) {}
  finish()
}

function finish() {
  visible.value = false
  emit('finished')
}

let goDept = false
function finishGoDept() {
  goDept = true
  finish()
}

watch(visible, (v) => {
  if (!v && goDept) {
    goDept = false
    router.push('/basedata/department')
  }
})
</script>

<style scoped>
.obw-fade-enter-active, .obw-fade-leave-active { transition: opacity .3s ease; }
.obw-fade-enter-from, .obw-fade-leave-to { opacity: 0; }

.obw-overlay {
  position: fixed; inset: 0; z-index: 1999;
  background:
    radial-gradient(ellipse at 20% 10%, rgba(99, 102, 241, 0.08) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 90%, rgba(16, 185, 129, 0.08) 0%, transparent 50%),
    linear-gradient(160deg, #f5f7fb 0%, #eef2f8 100%);
  display: flex; align-items: center; justify-content: center; padding: 20px;
}
.obw-shell {
  width: min(1040px, 96vw); max-height: 94vh;
  background: #fff; border-radius: 20px;
  box-shadow: 0 32px 80px rgba(30, 41, 59, 0.16), 0 0 0 1px rgba(255,255,255,0.6);
  display: flex; flex-direction: column; overflow: hidden;
  animation: obw-pop .4s cubic-bezier(.16,1,.3,1);
}
@keyframes obw-pop {
  from { opacity: 0; transform: translateY(16px) scale(.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.obw-header {
  padding: 18px 28px; display: flex; align-items: center; justify-content: space-between;
  border-bottom: 1px solid #f1f5f9;
}
.obw-brand { display: flex; align-items: center; gap: 10px; font-size: 14px; font-weight: 600; color: #334155; }
.obw-brand-dot {
  width: 10px; height: 10px; border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.obw-skip { border: none; background: transparent; color: #94a3b8; font-size: 13px; cursor: pointer; transition: color .2s; }
.obw-skip:hover { color: #6366f1; }

.obw-progress { padding: 18px 40px 0; display: flex; align-items: center; justify-content: center; position: relative; }
.obw-step-dot { display: flex; flex-direction: column; align-items: center; gap: 6px; z-index: 1; flex: 1; max-width: 180px; }
.obw-step-num {
  width: 28px; height: 28px; border-radius: 50%; background: #e2e8f0; color: #94a3b8;
  display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; transition: all .3s;
}
.obw-step-dot.active .obw-step-num { background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; box-shadow: 0 4px 12px rgba(99, 102, 241, 0.35); }
.obw-step-dot.done .obw-step-num { background: #10b981; color: #fff; }
.obw-step-label { font-size: 12px; color: #94a3b8; transition: color .2s; }
.obw-step-dot.active .obw-step-label, .obw-step-dot.done .obw-step-label { color: #475569; font-weight: 500; }
.obw-step-line {
  position: absolute; top: 32px; left: 50%; transform: translateX(-50%);
  height: 2px; background: linear-gradient(90deg, #10b981, #6366f1);
  border-radius: 2px; transition: width .4s ease; z-index: 0; max-width: 360px;
}

.obw-body { flex: 1; overflow-y: auto; padding: 28px 40px; }
.obw-step-head { text-align: center; margin-bottom: 24px; }
.obw-step-title { font-size: 22px; font-weight: 700; color: #1e293b; margin: 0 0 6px; }
.obw-step-sub { color: #94a3b8; font-size: 13px; margin: 0; }

.obw-welcome { text-align: center; }
.obw-hero { margin-bottom: 28px; }
.obw-hero-icon {
  width: 80px; height: 80px; margin: 0 auto 18px; border-radius: 22px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 12px 32px rgba(99, 102, 241, 0.3);
}
.obw-title { font-size: 26px; font-weight: 700; color: #1e293b; margin: 0 0 10px; }
.obw-sub { color: #64748b; font-size: 14px; max-width: 560px; margin: 0 auto; line-height: 1.7; }

.obw-nl-card {
  max-width: 640px; margin: 0 auto;
  background: linear-gradient(180deg, #fafbff 0%, #f5f7fb 100%);
  border: 1px solid #e2e8f0; border-radius: 16px; padding: 24px; text-align: left;
}
.obw-nl-label { display: block; font-size: 14px; font-weight: 600; color: #334155; margin-bottom: 10px; }
.obw-nl-input {
  width: 100%; box-sizing: border-box; border: 1.5px solid #e2e8f0; border-radius: 12px;
  padding: 14px 16px; font-size: 15px; color: #1e293b; resize: none; outline: none;
  transition: border-color .2s, box-shadow .2s; font-family: inherit; line-height: 1.6; background: #fff;
}
.obw-nl-input:focus { border-color: #6366f1; box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.12); }
.obw-nl-examples { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 12px; }
.obw-nl-hint { font-size: 12px; color: #94a3b8; }
.obw-example-chip {
  border: 1px solid #e2e8f0; background: #fff; color: #64748b; border-radius: 999px;
  padding: 5px 12px; font-size: 12px; cursor: pointer; transition: all .2s;
}
.obw-example-chip:hover { border-color: #6366f1; color: #6366f1; background: #eef2ff; }
.obw-nl-actions { display: flex; gap: 12px; margin-top: 18px; flex-wrap: wrap; }
.obw-primary-btn { height: 46px; font-size: 15px; font-weight: 600; background: linear-gradient(135deg, #6366f1, #8b5cf6) !important; border: none !important; }
.obw-primary-btn:hover { opacity: .92; }
.obw-ghost-btn { height: 46px; font-size: 15px; }
.obw-parse-result {
  margin-top: 16px; padding: 12px 16px; background: #ecfdf5; border: 1px solid #a7f3d0;
  border-radius: 10px; display: flex; align-items: center; gap: 10px;
  font-size: 13px; color: #065f46; line-height: 1.6;
}
.obw-parse-result.low { background: #fffbeb; border-color: #fde68a; color: #92400e; }
.obw-parse-icon { font-size: 18px; flex-shrink: 0; }
.obw-parse-icon.warn { color: #f59e0b; }

.obw-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.obw-card {
  position: relative; cursor: pointer; user-select: none; border: 1.5px solid #e2e8f0;
  border-radius: 14px; padding: 20px 14px 16px; text-align: center; transition: all .2s ease; background: #fff;
}
.obw-card:hover { border-color: #c7d2fe; transform: translateY(-3px); box-shadow: 0 8px 24px rgba(99, 102, 241, 0.12); }
.obw-card.active { border-color: #6366f1; background: #fafbff; box-shadow: 0 8px 24px rgba(99, 102, 241, 0.18); }
.obw-card-icon-wrap {
  width: 48px; height: 48px; margin: 0 auto 10px; border-radius: 12px;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff); color: #6366f1;
  display: flex; align-items: center; justify-content: center; transition: all .2s;
}
.obw-card.active .obw-card-icon-wrap { background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; }
.obw-card-icon { font-size: 26px; }
.obw-card-name { font-size: 14px; font-weight: 600; color: #1e293b; }
.obw-card-desc { font-size: 12px; color: #94a3b8; margin-top: 4px; line-height: 1.5; min-height: 18px; }
.obw-card-check {
  position: absolute; right: 10px; top: 10px; width: 22px; height: 22px; border-radius: 50%;
  background: #6366f1; color: #fff; font-size: 14px; display: none; align-items: center; justify-content: center;
}
.obw-card.active .obw-card-check { display: flex; }

.obw-scale-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; max-width: 820px; margin: 0 auto; }
.obw-scale-card {
  position: relative; cursor: pointer; user-select: none; border: 1.5px solid #e2e8f0;
  border-radius: 14px; padding: 22px 14px; text-align: center; transition: all .2s ease; background: #fff;
}
.obw-scale-card:hover { border-color: #c7d2fe; transform: translateY(-3px); box-shadow: 0 8px 24px rgba(99, 102, 241, 0.12); }
.obw-scale-card.active { border-color: #6366f1; background: #fafbff; box-shadow: 0 8px 24px rgba(99, 102, 241, 0.18); }
.obw-scale-people { display: flex; justify-content: center; gap: 4px; margin-bottom: 12px; min-height: 16px; }
.obw-person-dot { width: 10px; height: 10px; border-radius: 50%; background: linear-gradient(135deg, #6366f1, #8b5cf6); }
.obw-person-dot.dim { background: #e2e8f0; }
.obw-scale-name { font-size: 15px; font-weight: 600; color: #1e293b; }
.obw-scale-desc { font-size: 12px; color: #94a3b8; margin-top: 3px; }
.obw-scale-meta { margin-top: 10px; font-size: 11px; color: #6366f1; background: #eef2ff; padding: 3px 8px; border-radius: 6px; display: inline-block; }

.obw-loading { text-align: center; padding: 60px 0; color: #94a3b8; }
.obw-loading-icon { font-size: 36px; color: #6366f1; margin-bottom: 12px; }

.obw-plan { max-width: 860px; margin: 0 auto; }
.obw-plan-section { background: #f8fafc; border-radius: 12px; padding: 16px 18px; margin-bottom: 12px; }
.obw-plan-section-head { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: #334155; margin-bottom: 10px; }
.obw-plan-section-head .el-icon { color: #6366f1; }
.obw-plan-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.obw-plan-chips .el-tag { font-size: 13px; }
.obw-plan-row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.obw-plan-row-2 .obw-plan-section { margin-bottom: 0; }
.obw-plan-note { margin-top: 16px; padding: 12px 16px; background: #fffbeb; border-radius: 10px; font-size: 12px; color: #92400e; display: flex; align-items: center; gap: 8px; }

.obw-done { text-align: center; padding: 20px 0; }
.obw-done-icon {
  width: 72px; height: 72px; margin: 0 auto 18px; border-radius: 50%;
  background: linear-gradient(135deg, #10b981, #34d399); color: #fff; font-size: 40px;
  display: flex; align-items: center; justify-content: center; box-shadow: 0 12px 32px rgba(16, 185, 129, 0.3);
}
.obw-done-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; max-width: 720px; margin: 24px auto 12px; }
.obw-done-item { background: #f8fafc; border-radius: 12px; padding: 16px 10px; }
.obw-done-item-wide { grid-column: span 4; }
.obw-done-num { font-size: 26px; font-weight: 700; color: #6366f1; }
.obw-done-label { font-size: 12px; color: #64748b; margin-top: 2px; }

.obw-footer { padding: 16px 28px 22px; display: flex; justify-content: center; gap: 12px; border-top: 1px solid #f1f5f9; }

@media (max-width: 720px) {
  .obw-grid, .obw-scale-grid { grid-template-columns: repeat(2, 1fr); }
  .obw-done-grid { grid-template-columns: repeat(2, 1fr); }
  .obw-done-item-wide { grid-column: span 2; }
  .obw-plan-row-2 { grid-template-columns: 1fr; }
  .obw-body { padding: 20px; }
}
</style>
