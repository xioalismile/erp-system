<template>
  <!-- 物料列统一渲染件（全站唯一一份）
       为什么必须有它：图片 / 是-否 / 金额 这段渲染分支，原先在「物料库弹窗」「物料明细表」「单据明细件」三处各写了一遍，
       新增一个列类型就要改三处 → 必然漂移 → 用户看到的就是"缝缝补补"。
       现在三处都调本组件，物料列的渲染口径只剩这一处。
       类名沿用 material-thumb / thumb-placeholder：与各单据页既有样式保持兼容，避免观感变化。 -->
  <el-image
    v-if="col.type === 'image' && img"
    :src="img"
    :preview-src-list="imgList"
    fit="cover"
    class="material-thumb"
  />
  <span v-else-if="col.type === 'image'" class="thumb-placeholder">—</span>
  <span v-else-if="col.type === 'flag'">{{ Number(row[col.prop]) === 1 ? $tt('是') : $tt('否') }}</span>
  <span v-else-if="col.type === 'amount2' || col.type === 'amount4'" :class="col.cellClass">{{ amountText }}</span>
  <span v-else>{{ plainText }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { pickerFirstImage, pickerImageList } from '@/composables/useMaterialCustomFields'
import { usePrecision } from '@/composables/usePrecision'

const props = defineProps({
  /**
   * 列定义：{ prop, type: 'image' | 'flag' | 'amount2' | 'amount4' | 'text', cellClass?, scaleKey?, ... }
   * scaleKey（可选）：'qty' | 'price' | 'amount' | 'rate' | 'percent' —— 显式指定该列按哪类小数位显示；
   *   不传时按 type 回落（amount2→金额位、amount4→单价位），保证「不改设置 = 观感不变」。
   */
  col: { type: Object, required: true },
  row: { type: Object, required: true }
})

const { scaleFor, trimZero, formatWith } = usePrecision()

/**
 * 该列的小数位：显式 scaleKey > 按 type 回落（amount2→金额位、amount4→单价位）。
 * 不做"字段名推断"：物料库弹窗的销售单价/采购单价历史按 amount2（2位）展示，
 * 若按字段名推断会变成单价位(4)，等于"不改设置观感也变了"——不符合适配口径，故只认显式声明。
 */
const digits = computed(() => {
  const col = props.col
  if (col.scaleKey) return scaleFor(col.scaleKey)
  return col.type === 'amount4' ? scaleFor('price') : scaleFor('amount')
})

/** 图片：多图取第一张；兼容只存单图（mainImage / materialMainImage 这类纯路径字符串） */
const img = computed(() => pickerFirstImage(props.row[props.col.prop]) || props.row.mainImage || '')
const imgList = computed(() => {
  const list = pickerImageList(props.row[props.col.prop])
  if (list.length) return list
  return props.row.mainImage ? [props.row.mainImage] : []
})

/**
 * 金额：小数位改由设置中心统一控制（金额位/单价位，可再按 col.scaleKey 指定）；
 * **空值一律显示 0.00 / 0.0000，不显示空白**（用户 2026-10-10 口径）；
 * 「省略小数点后多余的 0」开启时按开关去掉末尾 0。
 */
const amountText = computed(() => formatWith(props.row[props.col.prop], digits.value, trimZero.value))

/** 文本 / 物料自定义字段：空值显示空白 */
const plainText = computed(() => {
  const v = props.row[props.col.prop]
  return v === null || v === undefined ? '' : v
})
</script>

<style scoped lang="scss">
.material-thumb { width: 32px; height: 32px; border-radius: 4px; border: 1px solid #ebeef5; cursor: pointer; }
.thumb-placeholder { color: #c0c4cc; font-size: 12px; }
</style>
