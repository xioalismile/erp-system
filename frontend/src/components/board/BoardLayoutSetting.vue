<template>
  <el-drawer v-model="visible" :title="$tt('看板设置')" size="620px" :close-on-click-modal="false">
    <div class="bls-tip">
      {{ $tt("这里的设置只对当前登录人生效：可以隐藏区块、调整顺序与宽度、把区块换用自己的数据源与计算公式、也能新增自定义卡片。不保存则页面保持原样。") }}
    </div>

    <div class="bls-list">
      <div v-for="(it, idx) in list" :key="it.id" class="bls-row">
        <div class="bls-main">
          <el-switch
            :model-value="!it.hidden"
            size="small"
            @update:model-value="v => (it.hidden = !v)"
          />
          <el-input v-model="it.title" size="small" class="bls-title" @input="syncTitle(it)" />
          <el-select v-model="it.size" size="small" class="bls-size">
            <el-option v-for="s in SIZE_OPTIONS" :key="s" :label="SIZE_LABELS[s]" :value="s" />
          </el-select>
          <el-button-group>
            <el-button size="small" :icon="ArrowUp" :disabled="idx === 0" @click="layout.move(it.id, -1)" />
            <el-button size="small" :icon="ArrowDown" :disabled="idx === list.length - 1" @click="layout.move(it.id, 1)" />
          </el-button-group>
        </div>
        <div class="bls-sub">
          <template v-if="it.builtin">
            <el-checkbox :model-value="it.override" @change="v => onOverride(it, v)">
              {{ $tt("改用自己的数据源与公式") }}
            </el-checkbox>
            <el-button v-if="it.override" size="small" link type="primary" @click="editBuiltin(it)">
              {{ $tt("设置取数") }}
            </el-button>
          </template>
          <template v-else>
            <span class="bls-tag">{{ $tt("自定义卡片") }}</span>
            <el-button size="small" link type="primary" @click="editCustom(it)">{{ $tt("设置取数") }}</el-button>
            <el-button size="small" link type="danger" @click="layout.removeItem(it.id)">{{ $tt("删除") }}</el-button>
          </template>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="bls-footer">
        <el-button @click="openEditor(null, 'metric', '')">{{ $tt("添加自定义卡片") }}</el-button>
        <el-button @click="onReset">{{ $tt("恢复默认") }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $tt("保存") }}</el-button>
      </div>
    </template>

    <BoardCardEditorDialog
      v-model="editorVisible"
      :card="editorCard"
      :default-title="editorTitle"
      :default-type="editorType"
      :period="layout.period.value"
      @confirm="onEditorConfirm"
    />
  </el-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import BoardCardEditorDialog from '@/components/board/BoardCardEditorDialog.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  layout: { type: Object, required: true }
})
const emit = defineEmits(['update:modelValue'])

const SIZE_OPTIONS = [3, 4, 6, 8, 12]
const SIZE_LABELS = {
  3: '窄',
  4: '1/3 行',
  6: '半行',
  8: '2/3 行',
  12: '整行'
}

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const list = computed(() => props.layout.items.value || [])
const saving = computed(() => !!props.layout.saving.value)

const editorVisible = ref(false)
const editorCard = ref(null)
const editorTitle = ref('')
const editorType = ref('metric')
/** 正在编辑的原有区块（null = 新增自定义卡片） */
const editingItem = ref(null)

function syncTitle(it) {
  if (it.card) it.card.title = it.title
}

function onOverride(it, v) {
  it.override = !!v
  if (it.override && !it.card) editBuiltin(it)
}

function openEditor(card, type, title) {
  editorCard.value = card || null
  editorType.value = type || 'metric'
  editorTitle.value = title || ''
  editorVisible.value = true
}

function editBuiltin(it) {
  editingItem.value = it
  openEditor(it.card, 'metric', it.title)
}

function editCustom(it) {
  editingItem.value = it
  openEditor(it.card, it.card?.type || 'metric', it.title)
}

function onEditorConfirm(card) {
  const it = editingItem.value
  if (it) {
    it.card = card
    if (it.builtin) {
      it.override = true
    } else {
      it.title = card.title
      it.size = Number(card.size) || it.size
    }
  } else {
    props.layout.addCustomCard(card)
  }
  editingItem.value = null
  // 保存前先刷新这张卡的数据，用户能立刻看到取数结果
  props.layout.refreshData()
}

async function onSave() {
  const ok = await props.layout.save()
  if (ok) visible.value = false
}

async function onReset() {
  await props.layout.reset()
  visible.value = false
}
</script>

<style scoped>
.bls-tip {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 12px;
}
.bls-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bls-row {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 10px;
  background: #fff;
}
.bls-main {
  display: flex;
  align-items: center;
  gap: 8px;
}
.bls-title {
  flex: 1;
  min-width: 0;
}
.bls-size {
  width: 96px;
  flex: none;
}
.bls-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  padding-left: 2px;
}
.bls-tag {
  font-size: 11px;
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 5px;
  padding: 1px 6px;
}
.bls-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
