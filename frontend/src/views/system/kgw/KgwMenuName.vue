<template>
  <div class="kgw-menu-name">
    <!-- 顶部工具栏 -->
    <div class="kgw-toolbar">
      <div class="kgw-actions">
        <el-button v-permission="perm('save')" type="success" size="small" @click="onSave" :loading="saving">{{ $tt("保存") }}</el-button>
        <el-button v-permission="perm('refresh')" size="small" @click="loadConfig">{{ $tt("刷新") }}</el-button>
        <el-button v-permission="perm('clear')" size="small" @click="onReset">{{ $tt("清空当前分组自定义") }}</el-button>
      </div>
      <div class="kgw-info">
        <el-tag type="info" size="small">{{ $tt("修改后保存并刷新页面生效") }}</el-tag>
      </div>
    </div>

    <div class="kgw-body">
      <!-- 左侧：分组列表 -->
      <div class="kgw-side">
        <el-input v-model="groupKeyword" :placeholder="$tt('搜索分组')" size="small" clearable class="kgw-side-search" />
        <div class="kgw-side-list">
          <div
            v-for="g in filteredGroups"
            :key="g.key"
            class="kgw-side-item"
            :class="{ active: currentGroupKey === g.key }"
            @click="currentGroupKey = g.key"
          >
            <el-icon><Menu /></el-icon>
            <span>{{ formData[`menu_group_${g.key}`] || g.label }}</span>
            <el-badge v-if="hasCustomInGroup(g)" is-dot type="success" class="kgw-side-badge" />
          </div>
        </div>
      </div>

      <!-- 右侧：当前分组的菜单项编辑 -->
      <div class="kgw-main" v-if="currentGroup">
        <el-alert type="info" :closable="false" style="margin-bottom:12px;">
          自定义左侧菜单的显示名称，留空则使用默认名称。修改后保存并刷新页面生效。
        </el-alert>

        <!-- 分组名称 -->
        <div class="group-name-row">
          <el-input
            v-model="formData[`menu_group_${currentGroup.key}`]"
            :placeholder="currentGroup.label"
            style="width:320px"
          >
            <template #prepend>{{ $tt("分组名称") }}</template>
          </el-input>
        </div>

        <!-- 菜单项表格 -->
        <el-table :data="currentGroup.items" border stripe size="small" style="width:100%">
          <el-table-column :label="$tf('默认名称')" prop="label" width="200" sortable />
          <el-table-column :label="$tf('自定义名称')">
            <template #default="{ row }">
              <el-input
                v-model="formData[`menu_name_${currentGroup.key}_${row.itemKey}`]"
                :placeholder="row.label"
                clearable
              />
            </template>
          </el-table-column>
          <el-table-column :label="$tf('路径')" width="280">
            <template #default="{ row }">
              <span class="path-text">{{ row.path || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>

        <!-- 子菜单项（报表等折叠菜单） -->
        <template v-for="item in currentGroup.items" :key="item.itemKey">
          <div v-if="item.children && item.children.length" class="sub-items-block">
            <div class="sub-items-title">{{ item.label }} - 子菜单</div>
            <el-table :data="item.children" border stripe size="small">
              <el-table-column :label="$tf('默认名称')" prop="label" width="200" />
              <el-table-column :label="$tf('自定义名称')">
                <template #default="{ row }">
                  <el-input
                    v-model="formData[`menu_name_${currentGroup.key}_${row.itemKey}`]"
                    :placeholder="row.label"
                    clearable
                  />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:menu-name')
import { ref, reactive, computed, onMounted } from 'vue'
import { Menu } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { MENU_GROUPS } from '@/config/menuConfig'
import { getSysConfigByGroup, batchUpdateSysConfig } from '@/api/system/sysConfig'
import { tt } from '@/i18n'

const props = defineProps({
  embedded: { type: Boolean, default: false }
})

const saving = ref(false)
const groupKeyword = ref('')
const currentGroupKey = ref(MENU_GROUPS[0]?.key || '')
const formData = reactive({})
const originalData = reactive({})

const filteredGroups = computed(() => {
  if (!groupKeyword.value) return MENU_GROUPS
  const kw = groupKeyword.value.toLowerCase()
  return MENU_GROUPS.filter(g => g.label.toLowerCase().includes(kw))
})

const currentGroup = computed(() => MENU_GROUPS.find(g => g.key === currentGroupKey.value))

// 初始化所有字段为空
function initFields() {
  MENU_GROUPS.forEach(group => {
    const gKey = `menu_group_${group.key}`
    if (!(gKey in formData)) {
      formData[gKey] = ''
      originalData[gKey] = ''
    }
    const walk = (items) => {
      items.forEach(item => {
        const iKey = `menu_name_${group.key}_${item.itemKey}`
        if (!(iKey in formData)) {
          formData[iKey] = ''
          originalData[iKey] = ''
        }
        if (item.children && item.children.length) walk(item.children)
      })
    }
    walk(group.items)
  })
}

async function loadConfig() {
  initFields()
  try {
    const list = await getSysConfigByGroup('menu_name')
    const arr = Array.isArray(list) ? list : []
    arr.forEach(item => {
      if (item.configKey) {
        formData[item.configKey] = item.configValue || ''
        originalData[item.configKey] = item.configValue || ''
      }
    })
  } catch (e) {
    // 首次使用时可能没有配置，忽略错误
  }
}

// 检查分组中是否有自定义项
function hasCustomInGroup(group) {
  const gKey = `menu_group_${group.key}`
  if (formData[gKey]) return true
  const walk = (items) => items.some(item => {
    const iKey = `menu_name_${group.key}_${item.itemKey}`
    if (formData[iKey]) return true
    if (item.children) return walk(item.children)
    return false
  })
  return walk(group.items)
}

async function onSave() {
  saving.value = true
  try {
    const changedList = []
    MENU_GROUPS.forEach(group => {
      const gKey = `menu_group_${group.key}`
      if (formData[gKey] !== originalData[gKey]) {
        changedList.push({
          configKey: gKey,
          configValue: formData[gKey] || '',
          configName: `菜单分组-${group.label}`,
          configGroup: 'menu_name',
          valueType: 'string'
        })
      }
      const walk = (items) => {
        items.forEach(item => {
          const iKey = `menu_name_${group.key}_${item.itemKey}`
          if (formData[iKey] !== originalData[iKey]) {
            changedList.push({
              configKey: iKey,
              configValue: formData[iKey] || '',
              configName: `菜单-${group.label}-${item.label}`,
              configGroup: 'menu_name',
              valueType: 'string'
            })
          }
          if (item.children) walk(item.children)
        })
      }
      walk(group.items)
    })

    if (changedList.length === 0) {
      ElMessage.info(tt('没有修改项需要保存'))
      return
    }

    await batchUpdateSysConfig(changedList)
    changedList.forEach(item => { originalData[item.configKey] = formData[item.configKey] })
    ElMessage.success(`${tt("保存成功，共更新 ")}${changedList.length}${tt(" 项，请刷新页面查看菜单变更")}`)
  } catch (e) {
    ElMessage.error(tt('保存失败'))
  } finally {
    saving.value = false
  }
}

function onReset() {
  if (!currentGroup.value) return
  const g = currentGroup.value
  formData[`menu_group_${g.key}`] = ''
  const walk = (items) => {
    items.forEach(item => {
      formData[`menu_name_${g.key}_${item.itemKey}`] = ''
      if (item.children) walk(item.children)
    })
  }
  walk(g.items)
  ElMessage.info(tt('已清空当前分组的自定义名称，请点击保存生效'))
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped lang="scss">
.kgw-menu-name {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.kgw-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}
.kgw-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.kgw-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.kgw-side {
  width: 220px;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.kgw-side-search {
  padding: 8px;
}
.kgw-side-list {
  flex: 1;
  overflow-y: auto;
}
.kgw-side-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  transition: all .2s;
  &:hover {
    background: #f0f7ff;
    color: #409eff;
  }
  &.active {
    background: #ecf5ff;
    color: #409eff;
    font-weight: 600;
    border-right: 3px solid #409eff;
  }
}
.kgw-side-badge {
  margin-left: auto;
}
.kgw-main {
  flex: 1;
  padding: 16px 20px;
  overflow-y: auto;
  background: #fff;
}
.group-name-row {
  margin-bottom: 16px;
}
.sub-items-block {
  margin-top: 16px;
}
.sub-items-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid #dcdfe6;
}
.path-text {
  color: #c0c4cc;
  font-size: 12px;
  font-family: monospace;
}
</style>
