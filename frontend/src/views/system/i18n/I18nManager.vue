<template>
  <div class="i18n-manager">
    <div class="toolbar">
      <div class="toolbar-left">
        <el-select v-model="lang" size="default" style="width: 140px" @change="load(1)">
          <el-option label="英文 (en)" value="en" />
          <el-option label="简体中文 (zh-CN)" value="zh-CN" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索键名 / 译文"
          clearable
          style="width: 240px"
          @keyup.enter="load(1)"
        />
        <el-select v-model="module" placeholder="全部分组" clearable style="width: 140px" @change="load(1)">
          <el-option label="通用 common" value="common" />
          <el-option label="登录 login" value="login" />
          <el-option label="主框架 layout" value="layout" />
          <el-option label="菜单 menu" value="menu" />
          <el-option label="页面 page" value="page" />
          <el-option label="文本提示 text" value="text" />
          <el-option label="字典值 dict" value="dict" />
          <el-option label="打印 print" value="print" />
        </el-select>
        <el-button v-permission="perm('query')" type="primary" :loading="loading" @click="load(1)">查询</el-button>
      </div>
      <div class="toolbar-right">
        <el-button v-permission="perm('save')" type="primary" :loading="saving" @click="save">保存译文</el-button>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" size="small" border stripe max-height="620">
      <el-table-column prop="i18nKey" label="翻译键" min-width="220" show-overflow-tooltip />
      <el-table-column prop="zhText" label="中文原文" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.zhText || '' }}</template>
      </el-table-column>
      <el-table-column label="译文" min-width="240">
        <template #default="{ row }">
          <el-input v-model="row.text" size="small" clearable />
        </template>
      </el-table-column>
      <el-table-column prop="module" label="分组" width="110" align="center" />
      <template #empty>
        <el-empty description="暂无语言包数据" :image-size="60" />
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next, sizes"
        :total="total"
        :current-page="current"
        :page-size="size"
        :page-sizes="[20, 50, 100]"
        @current-change="load"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:sys-param')
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pageI18n, saveI18n } from '@/api/system/i18n'

const list = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(20)
const keyword = ref('')
const module = ref('')
/** 编辑目标语言：默认英文（中文为原文，一般不改） */
const lang = ref('en')
const loading = ref(false)
const saving = ref(false)

async function load(page) {
  if (page) current.value = page
  loading.value = true
  try {
    const res = await pageI18n({
      current: current.value,
      size: size.value,
      keyword: keyword.value || undefined,
      module: module.value || undefined,
      lang: lang.value
    })
    list.value = Array.isArray(res?.records) ? res.records : []
    total.value = Number(res?.total || 0)
    current.value = Number(res?.current || current.value)
    size.value = Number(res?.size || size.value)
  } catch (e) {
    list.value = []
    total.value = 0
    ElMessage.error((e && (e.message || e.msg)) || '加载语言包失败')
  } finally {
    loading.value = false
  }
}

function onSizeChange(s) {
  size.value = s
  load(1)
}

async function save() {
  // 只提交改动过 text 的行（简单全量提交当前页有值行，后端幂等 upsert，安全）
  const rows = list.value
    .filter(r => r && r.i18nKey && (r.text !== null && r.text !== undefined))
    .map(r => ({ i18nKey: r.i18nKey, lang: lang.value, text: r.text, module: r.module || 'common' }))
  if (!rows.length) {
    ElMessage.warning('当前页没有可保存的译文')
    return
  }
  saving.value = true
  try {
    await saveI18n(rows)
    ElMessage.success('译文已保存')
    load()
  } catch (e) {
    ElMessage.error((e && (e.message || e.msg)) || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => load(1))
</script>

<style scoped>
.i18n-manager {
  padding: 4px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
