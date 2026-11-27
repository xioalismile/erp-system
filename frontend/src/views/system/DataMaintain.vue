<template>
  <div class="data-maintain-page">
    <el-alert
      type="warning"
      :closable="false"
      :title="$tt('危险操作：以下功能会物理删除数据且不可恢复，请确认已做好数据备份后再执行。')"
      style="margin-bottom: 16px"
    />

    <div class="maintain-cards">
      <!-- 模式1：清空业务单据 -->
      <div class="maintain-card">
        <div class="card-icon business">
          <el-icon :size="28"><Document /></el-icon>
        </div>
        <div class="card-title">{{ $tt("一键清空全部业务单据") }}</div>
        <div class="card-desc">
          清空销售、采购、库存、生产、财务、委外、CRM 等全部业务单据和操作日志。
        </div>
        <div class="card-keep">
          <div class="keep-title">{{ $tt("保留内容") }}</div>
          <div>{{ $tt("物料、客户、供应商、仓库、员工、部门等基础档案；角色权限、系统参数、打印模板、自动编号设置。") }}</div>
        </div>
        <el-button v-permission="perm('clear')" type="warning" plain :loading="clearing === 'business'" @click="openConfirm('business')">
          清空业务单据
        </el-button>
      </div>

      <!-- 模式2：清空全部资料 -->
      <div class="maintain-card danger">
        <div class="card-icon danger">
          <el-icon :size="28"><DeleteFilled /></el-icon>
        </div>
        <div class="card-title">{{ $tt("一键清空全部资料") }}</div>
        <div class="card-desc">
          清空全部业务单据 + 全部基础档案（物料、客户、供应商、仓库、员工、部门等）。
        </div>
        <div class="card-keep">
          <div class="keep-title">{{ $tt("保留内容") }}</div>
          <div>{{ $tt("系统登录账号（admin 自动恢复，初始密码 admin123）、角色权限、菜单、字典、系统参数、打印模板、自动编号设置、自定义报表。") }}</div>
        </div>
        <el-button v-permission="perm('clear')" type="danger" :loading="clearing === 'all'" @click="openConfirm('all')">
          清空全部资料
        </el-button>
      </div>
    </div>

    <!-- 结果反馈 -->
    <el-alert
      v-if="lastResult"
      :type="lastResult.failedCount > 0 ? 'warning' : 'success'"
      :closable="false"
      style="margin-top: 16px"
      :title="lastResult.title"
    >
      <div>已清空 {{ lastResult.truncated }} 张表，跳过不存在的表 {{ lastResult.skippedCount }} 张{{ lastResult.failedCount > 0 ? ('，' + lastResult.failedCount + ' 张失败：' + lastResult.failed.join('；')) : '' }}</div>
      <div v-if="lastResult.mode === 'clearAll'" style="margin-top:4px;color:#e6a23c">
        admin 账号已恢复，初始密码为 admin123，请刷新浏览器重新登录。
      </div>
    </el-alert>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:data-maintain')
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, DeleteFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { tt } from '@/i18n'

const clearing = ref('')
const lastResult = ref(null)

function openConfirm(mode) {
  const isAll = mode === 'all'
  const tip = isAll
    ? '将清空全部业务单据和全部基础档案（不可恢复），系统仅保留登录账号、角色权限和系统设置。确定继续吗？'
    : '将清空全部业务单据（不可恢复），基础档案和系统设置保持不变。确定继续吗？'
  ElMessageBox.prompt(tip, isAll ? '清空全部资料' : '清空业务单据', {
    type: 'warning',
    confirmButtonText: tt('确定清空'),
    cancelButtonText: tt('取消'),
    confirmButtonClass: isAll ? 'el-button--danger' : '',
    inputPlaceholder: '请输入"清空"二字确认操作',
    inputValidator: (v) => (v === '清空' ? true : '输入不正确，请输入"清空"二字')
  })
    .then(() => doClear(mode))
    .catch(() => {})
}

async function doClear(mode) {
  clearing.value = mode
  try {
    const url = mode === 'all' ? '/system/maintain/clear-all' : '/system/maintain/clear-business'
    const d = (await request.post(url)) || {}
    lastResult.value = {
      mode,
      title: mode === 'all' ? '全部资料清空完成' : '业务单据清空完成',
      truncated: d.truncated ?? 0,
      skippedCount: d.skippedCount ?? 0,
      failed: d.failed || [],
      failedCount: (d.failed || []).length
    }
    ElMessage.success(mode === 'all' ? tt('全部资料已清空') : tt('业务单据已清空'))
    if (mode === 'all') {
      setTimeout(() => { window.location.href = '/login' }, 2500)
    }
  } catch (e) {
    // 拦截器已弹错误提示，这里无需重复提示
  } finally {
    clearing.value = ''
  }
}
</script>

<style scoped lang="scss">
.data-maintain-page { padding: 8px 4px; }
.maintain-cards {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.maintain-card {
  flex: 1;
  min-width: 340px;
  max-width: 520px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #fafbfc;
  &.danger { background: #fdf6f6; border-color: #f5c6c6; }
}
.card-icon {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  &.business { background: linear-gradient(135deg, #e6a23c, #cf8a1e); }
  &.danger { background: linear-gradient(135deg, #f56c6c, #d03b3b); }
}
.card-title { font-size: 16px; font-weight: 600; color: #303133; }
.card-desc { font-size: 13px; color: #606266; line-height: 1.6; }
.card-keep {
  font-size: 12px;
  color: #909399;
  background: #fff;
  border: 1px dashed #e4e7ed;
  border-radius: 6px;
  padding: 8px 10px;
  line-height: 1.6;
  .keep-title { font-weight: 600; color: #606266; margin-bottom: 2px; }
}
.maintain-card .el-button { margin-top: auto; }
</style>
