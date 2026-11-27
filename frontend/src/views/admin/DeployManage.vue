<template>
  <div class="erp-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ $tt("客户升级部署") }}</span>
          <div style="display:flex;gap:12px;align-items:center">
            <el-button icon="Box" v-permission="perm('view')" @click="openVersions">{{ $tt("版本库") }}</el-button>
            <el-button type="primary" icon="Promotion" v-permission="perm('pack')" @click="openBuild">{{ $tt("一键打包") }}</el-button>
            <el-button icon="Plus" v-permission="perm('add')" @click="openEdit(null)">{{ $tt("新增服务器") }}</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="!servers.length" type="info" :closable="false" :title="$tt('还没有客户服务器，点击右上角「新增服务器」添加第一台（需服务器已开启 OpenSSH，可在服务器上运行 D:\\erp\\deploy\\server\\INIT-AGENT.bat 一键开启）')" />

      <!-- 服务器卡片 -->
      <div v-loading="loading" class="server-grid">
        <div v-for="s in servers" :key="s.id" class="server-card">
          <div class="server-card-head">
            <div>
              <span class="server-name">{{ s.name }}</span>
              <el-tag size="small" :type="s.enabled ? 'success' : 'info'" effect="plain">{{ s.enabled ? '启用' : '停用' }}</el-tag>
            </div>
            <el-dropdown trigger="click" @command="(cmd) => onCardCommand(cmd, s)">
              <el-button icon="MoreFilled" circle size="small" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">{{ $tt("编辑") }}</el-dropdown-item>
                  <el-dropdown-item command="test">{{ $tt("测试连接") }}</el-dropdown-item>
                  <el-dropdown-item command="backups" divided>{{ $tt("备份列表 / 回滚") }}</el-dropdown-item>
                  <el-dropdown-item command="logs">{{ $tt("部署历史") }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided><span style="color: var(--el-color-danger)">{{ $tt("删除") }}</span></el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="server-meta">
            <div class="meta-row"><span class="meta-label">{{ $tt("主机") }}</span><span>{{ s.host }}:{{ s.sshPort || 22 }}</span></div>
            <div class="meta-row"><span class="meta-label">{{ $tt("当前版本") }}</span><span><el-tag v-if="s.currentVersion" size="small" effect="plain" type="success">{{ s.currentVersion }}</el-tag><span v-else style="color:var(--el-text-color-secondary)">{{ $tt("未知（未通过版本库部署）") }}</span></span></div>
            <div class="meta-row"><span class="meta-label">{{ $tt("包模式") }}</span><span><el-tag size="small" :type="s.buildMode === 'customer' ? 'warning' : 'primary'" effect="plain">{{ s.buildMode === 'customer' ? '客户包' : '全量包' }}</el-tag></span></div>
            <div class="meta-row"><span class="meta-label">{{ $tt("后端jar") }}</span><span class="meta-value">{{ s.jarTarget || '-' }}</span></div>
            <div class="meta-row"><span class="meta-label">{{ $tt("前端目录") }}</span><span class="meta-value">{{ s.frontendTargets || '-' }}</span></div>
          </div>
          <div class="server-actions">
            <el-button type="primary" icon="Upload" v-permission="perm('deploy')" :disabled="!s.enabled" @click="openDeploy(s)">{{ $tt("部署新版本") }}</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 新增/编辑服务器 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑服务器' : '新增服务器'" width="680px" :close-on-click-modal="false">
      <el-form :model="editForm" label-width="110px">
        <el-form-item :label="$tf('名称')" required><el-input v-model="editForm.name" autocomplete="off" :placeholder="$tt('如 121客户生产服务器')" maxlength="100" /></el-form-item>
        <el-form-item :label="$tf('主机IP')" required><el-input v-model="editForm.host" autocomplete="off" :placeholder="$tt('如 121.40.168.247')" /></el-form-item>
        <el-form-item :label="$tf('SSH端口')"><el-input-number v-model="editForm.sshPort" :min="1" :max="65535" /></el-form-item>
        <el-form-item :label="$tf('SSH账号')"><el-input v-model="editForm.sshUser" autocomplete="off" placeholder="Administrator" /></el-form-item>
        <el-form-item :label="editForm.id ? 'SSH密码' : 'SSH密码'" :required="!editForm.id">
          <el-input v-model="editForm.sshPassword" type="password" show-password autocomplete="new-password" :placeholder="editForm.id ? '留空 = 不修改' : '服务器管理员密码'" />
        </el-form-item>
        <el-form-item :label="$tf('工作目录')"><el-input v-model="editForm.remoteWorkDir" autocomplete="off" placeholder="C:/smart-erp-update" /></el-form-item>
        <el-form-item :label="$tf('后端jar路径')" required><el-input v-model="editForm.jarTarget" autocomplete="off" placeholder="C:/smart-erp/backend/smart-erp.jar" /></el-form-item>
        <el-form-item :label="$tf('前端目录')">
          <el-input v-model="editForm.frontendTargets" type="textarea" autocomplete="off" :rows="2" :placeholder="$tt('分号分隔，存在即全部覆盖，如：C:/smart-erp/frontend;C:/nginx-1.30.4/html')" />
        </el-form-item>
        <el-form-item :label="$tf('停止命令')"><el-input v-model="editForm.stopCommand" autocomplete="off" :placeholder="$tt('如 schtasks /End /TN SmartERP-Backend')" /></el-form-item>
        <el-form-item :label="$tf('启动命令')"><el-input v-model="editForm.startCommand" autocomplete="off" :placeholder="$tt('如 schtasks /Run /TN SmartERP-Backend')" /></el-form-item>
        <el-form-item :label="$tf('健康检查URL')"><el-input v-model="editForm.healthUrl" autocomplete="off" placeholder="http://localhost/api/auth/login" /></el-form-item>
        <el-form-item :label="$tf('数据库名')">
          <el-input v-model="editForm.dbName" autocomplete="off" :placeholder="$tt('如 erp_db；三项齐全则部署时自动补缺失列')" />
        </el-form-item>
        <el-form-item :label="$tf('数据库账号')"><el-input v-model="editForm.dbUser" autocomplete="off" :placeholder="$tt('如 erp_user（须有建表/改表权限）')" /></el-form-item>
        <el-form-item :label="$tf('数据库密码')">
          <el-input v-model="editForm.dbPassword" type="password" show-password autocomplete="new-password" :placeholder="editForm.id ? '留空 = 不修改' : '部署补列用数据库密码'" />
        </el-form-item>
        <el-form-item :label="$tf('备注')"><el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="editVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" :loading="saving" @click="saveServer">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 部署对话框 -->
    <el-dialog v-model="deployVisible" :title="'部署新版本 → ' + (deployServer ? deployServer.name : '')" width="640px" :close-on-click-modal="false">
      <el-tabs v-model="deployTab">
        <el-tab-pane :label="$tf('从版本库选择')" name="version">
          <el-form label-width="90px">
            <el-form-item :label="$tf('选择版本')" required>
              <el-select v-model="selectedVersionId" :placeholder="$tt('选择要部署的版本')" style="width:100%">
                <el-option v-for="v in deployableVersions" :key="v.id" :value="v.id"
                  :label="v.versionNo + '（' + (v.buildMode === 'customer' ? '客户包' : '全量包') + (v.note ? ' - ' + v.note : '') + '）'" />
              </el-select>
              <div class="zip-tip">仅显示与服务器包模式匹配的版本（当前服务器：{{ deployServer && deployServer.buildMode === 'customer' ? '客户包' : '全量包' }}）</div>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane :label="$tf('上传zip')" name="upload">
          <el-form label-width="90px">
            <el-form-item :label="$tf('更新包')" required>
              <input ref="zipInput" type="file" accept=".zip" class="zip-input" @change="onZipChange" />
              <div v-if="zipFile" class="zip-info">
                {{ zipFile.name }}（{{ (zipFile.size / 1024 / 1024).toFixed(2) }} MB）
              </div>
              <div class="zip-tip">{{ $tt("zip 内应含：smart-erp.jar + frontend/ 目录（与 Publish.ps1 打包结构一致）") }}</div>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <el-form label-width="90px">
        <el-form-item :label="$tf('版本说明')">
          <el-input v-model="deployNote" type="textarea" :rows="2" :placeholder="$tt('如：1.1.2 修复获客任务列表排序')" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="deployVisible = false" :disabled="deploying">{{ $tt("取消") }}</el-button>
        <el-button type="primary" :loading="deploying" :disabled="deployTab === 'version' ? !selectedVersionId : !zipFile" v-permission="perm('deploy')" @click="startDeploy">{{ $tt("开始部署（自动备份，失败自动回滚）") }}</el-button>
      </template>
    </el-dialog>

    <!-- 一键打包对话框 -->
    <el-dialog v-model="buildVisible" :title="$tf('一键打包')" width="560px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item :label="$tf('打包模式')" required>
          <el-radio-group v-model="buildForm.buildMode">
            <el-radio value="full">{{ $tt("全量包（个人服务器）") }}</el-radio>
            <el-radio value="customer">{{ $tt("客户包（无平台管理）") }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$tf('版本说明')">
          <el-input v-model="buildForm.note" type="textarea" :rows="2" :placeholder="$tt('如：修复对账报表显示、新增客户删除校验')" maxlength="200" />
        </el-form-item>
      </el-form>
      <div class="zip-tip">{{ $tt("将在本地完成构建（Maven + npm + H5）并登记版本库，过程约几分钟，可关闭对话框后台执行。") }}</div>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="buildVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('pack')" :loading="building" @click="startBuild">{{ $tt("开始打包") }}</el-button>
      </template>
    </el-dialog>

    <!-- 版本库 -->
    <el-dialog v-model="versionsVisible" :title="$tf('版本库')" width="860px">
      <el-table v-loading="versionsLoading" :data="versions" border size="small">
        <el-table-column prop="versionNo" :label="$tf('版本号')" width="110" />
        <el-table-column :label="$tf('模式')" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.buildMode === 'customer' ? 'warning' : 'primary'" effect="plain">{{ row.buildMode === 'customer' ? '客户包' : '全量包' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="note" :label="$tf('说明')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="$tf('大小')" width="90">
          <template #default="{ row }">{{ row.zipSize ? (row.zipSize / 1024 / 1024).toFixed(1) + 'MB' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="operator" :label="$tf('打包人')" width="80" />
        <el-table-column :label="$tf('打包时间')" width="150">
          <template #default="{ row }"><span>{{ fmtTime(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column :label="$tf('操作')" width="160" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link v-permission="perm('deploy')" @click="deployVersionTo(row)">{{ $tt("部署") }}</el-button>
            <el-button size="small" type="danger" link v-permission="perm('delete')" @click="deleteVersion(row)">{{ $tt("删除") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 备份列表 / 回滚 -->
    <el-dialog v-model="backupVisible" :title="'备份与回滚 → ' + (backupServer ? backupServer.name : '')" width="560px">
      <div v-loading="backupLoading">
        <el-alert v-if="!backupLoading && !backups.length" type="info" :closable="false" :title="$tt('服务器上还没有备份（首次部署成功后自动产生）')" />
        <el-table v-else :data="backups" border size="small">
          <el-table-column prop="name" :label="$tf('备份（时间戳）')" />
          <el-table-column :label="$tf('操作')" width="120" align="center">
            <template #default="{ row }">
              <el-button size="small" type="warning" v-permission="perm('rollback')" @click="doRollback(row.name)">{{ $tt("回滚到此版本") }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 执行日志（部署/回滚共用，实时轮询） -->
    <el-dialog v-model="logVisible" :title="logTitle" width="820px" :close-on-click-modal="false" top="5vh">
      <div class="log-status-row">
        <el-tag v-if="logData.status === 'RUNNING'" type="warning">{{ $tt("执行中…") }}</el-tag>
        <el-tag v-else-if="logData.status === 'SUCCESS'" type="success">{{ $tt("成功") }}</el-tag>
        <el-tag v-else-if="logData.status === 'ROLLED_BACK'" type="warning">{{ $tt("失败已自动回滚") }}</el-tag>
        <el-tag v-else-if="logData.status" type="danger">{{ $tt("失败") }}</el-tag>
        <span v-if="logData.versionNote" class="log-note">{{ logData.versionNote }}</span>
      </div>
      <pre class="log-box">{{ logData.logText || '等待输出…' }}</pre>
      <template #footer>
        <el-button v-permission="perm('dlgClose')" @click="logVisible = false">{{ $tt(logData.status === 'RUNNING' ? '后台执行' : '关闭') }}</el-button>
      </template>
    </el-dialog>

    <!-- 部署历史 -->
    <el-dialog v-model="logsVisible" :title="'部署历史 → ' + (logsServer ? logsServer.name : '')" width="860px">
      <el-table v-loading="logsLoading" :data="logs" border size="small">
        <el-table-column prop="createdAt" :label="$tf('时间')" width="160">
          <template #default="{ row }"><span>{{ fmtTime(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column prop="action" :label="$tf('类型')" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.action === 'DEPLOY' ? 'primary' : (row.action === 'BUILD' ? 'success' : 'warning')">{{ row.action === 'DEPLOY' ? '部署' : (row.action === 'BUILD' ? '打包' : '回滚') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="versionNo" :label="$tf('版本号')" width="110" />
        <el-table-column prop="status" :label="$tf('结果')" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ $dt(statusText(row.status)) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="versionNote" :label="$tf('说明')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="operator" :label="$tf('操作人')" width="90" />
        <el-table-column :label="$tf('日志')" width="80" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" v-permission="perm('view')" @click="viewLog(row)">{{ $tt("查看") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('platform:deploy-manage')

const servers = ref([])
const loading = ref(false)

// ----- 服务器编辑 -----
const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({ id: null, name: '', host: '', sshPort: 22, sshUser: 'Administrator', sshPassword: '', remoteWorkDir: 'C:/smart-erp-update', jarTarget: '', frontendTargets: '', stopCommand: '', startCommand: '', healthUrl: 'http://localhost/api/auth/login', dbName: '', dbUser: '', dbPassword: '', remark: '', enabled: 1 })

function openEdit(s) {
  if (s) {
    Object.assign(editForm, s, { sshPassword: '', dbPassword: '' })
  } else {
    Object.assign(editForm, { id: null, name: '', host: '', sshPort: 22, sshUser: 'Administrator', sshPassword: '', remoteWorkDir: 'C:/smart-erp-update', jarTarget: '', frontendTargets: '', stopCommand: '', startCommand: '', healthUrl: 'http://localhost/api/auth/login', dbName: '', dbUser: '', dbPassword: '', remark: '', enabled: 1 })
  }
  editVisible.value = true
}

async function saveServer() {
  if (!editForm.name || !editForm.host || !editForm.jarTarget) { ElMessage.warning(tt('请填写名称、主机IP、后端jar路径')); return }
  saving.value = true
  try {
    await request.post('/admin/deploy/server', editForm)
    ElMessage.success(tt('已保存'))
    editVisible.value = false
    await loadServers()
  } catch (e) {
    // 安全：只记录错误信息，不打印整条请求对象（否则 F12 控制台能看到服务器/数据库密码明文）
    console.error('保存服务器失败:', (e && (e.message || e.msg)) || e)
  } finally { saving.value = false }
}

async function onCardCommand(cmd, s) {
  if (cmd === 'edit') openEdit(s)
  else if (cmd === 'test') {
    try {
      const r = await request.post(`/admin/deploy/server/${s.id}/test`)
      ElMessage.success(r || tt('连接成功'))
    } catch (e) { console.error('连接测试失败:', e) }
  }
  else if (cmd === 'backups') openBackups(s)
  else if (cmd === 'logs') openLogs(s)
  else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm(`${tt("确定删除服务器「")}${s.name}${tt("」？部署历史将保留。")}`, tt('确认'), { type: 'warning' })
      await request.delete(`/admin/deploy/server/${s.id}`)
      ElMessage.success(tt('已删除'))
      await loadServers()
    } catch (e) { if (e !== 'cancel') console.error('删除失败:', e) }
  }
}

// ----- 部署 -----
const deployVisible = ref(false)
const deploying = ref(false)
const deployServer = ref(null)
const deployNote = ref('')
const zipFile = ref(null)
const zipInput = ref(null)
const deployTab = ref('version')
const selectedVersionId = ref(null)
const versions = ref([])

const deployableVersions = computed(() => {
  const mode = deployServer.value && deployServer.value.buildMode === 'customer' ? 'customer' : 'full'
  return versions.value.filter(v => v.buildMode === mode)
})

async function loadVersions() {
  try {
    const r = await request.get('/admin/deploy-version/versions')
    versions.value = r || []
  } catch (e) { console.error('加载版本库失败:', e) }
}

function openDeploy(s) {
  deployServer.value = s
  deployNote.value = ''
  zipFile.value = null
  selectedVersionId.value = null
  deployTab.value = 'version'
  if (zipInput.value) zipInput.value.value = ''
  deployVisible.value = true
  loadVersions()
}

function onZipChange(e) {
  const f = e.target.files && e.target.files[0]
  if (f && !f.name.toLowerCase().endsWith('.zip')) { ElMessage.warning(tt('请选择 zip 更新包')); e.target.value = ''; return }
  zipFile.value = f || null
}

async function startDeploy() {
  if (deployTab.value === 'version') {
    if (!selectedVersionId.value) return
    deploying.value = true
    try {
      const r = await request.post('/admin/deploy-version/deploy', { versionId: selectedVersionId.value, serverId: deployServer.value.id }, { timeout: 60000 })
      deployVisible.value = false
      openLogDialog(r.logId, '部署执行')
    } catch (e) {
      console.error('启动部署失败:', e)
    } finally { deploying.value = false }
  } else {
    if (!zipFile.value) return
    deploying.value = true
    try {
      const fd = new FormData()
      fd.append('serverId', deployServer.value.id)
      fd.append('file', zipFile.value)
      if (deployNote.value) fd.append('note', deployNote.value)
      const r = await request.post('/admin/deploy/deploy', fd, { timeout: 60000 })
      deployVisible.value = false
      openLogDialog(r.logId, '部署执行')
    } catch (e) {
      console.error('启动部署失败:', e)
    } finally { deploying.value = false }
  }
}

// ----- 一键打包 -----
const buildVisible = ref(false)
const building = ref(false)
const buildForm = reactive({ buildMode: 'full', note: '' })

function openBuild() {
  buildForm.buildMode = 'full'
  buildForm.note = ''
  buildVisible.value = true
}

async function startBuild() {
  building.value = true
  try {
    const r = await request.post('/admin/deploy-version/build', { buildMode: buildForm.buildMode, note: buildForm.note }, { timeout: 60000 })
    buildVisible.value = false
    openLogDialog(r.logId, '打包执行', '/admin/deploy-version/log/')
  } catch (e) {
    console.error('启动打包失败:', e)
  } finally { building.value = false }
}

// ----- 版本库 -----
const versionsVisible = ref(false)
const versionsLoading = ref(false)

function openVersions() {
  versionsVisible.value = true
  versionsLoading.value = true
  loadVersions().finally(() => { versionsLoading.value = false })
}

async function deployVersionTo(v) {
  const matchServers = servers.value.filter(s => (s.buildMode || 'full') === v.buildMode && s.enabled)
  if (!matchServers.length) { ElMessage.warning(tt('没有包模式匹配且启用的服务器')); return }
  try {
    const { value } = await ElMessageBox.confirm(`${tt("将版本 ")}${v.versionNo}${tt(" 部署到哪台服务器？\\n\\n")}` + matchServers.map((s, i) => `${i + 1}. ${s.name}${tt("（当前: ")}${s.currentVersion || '未知'}）`).join('\n') + '\n\n请在「部署新版本」对话框操作：点击服务器卡片上的「部署新版本」按钮选择该版本。', tt('版本部署'), { confirmButtonText: tt('知道了'), showCancelButton: false })
    versionsVisible.value = false
  } catch (e) { /* ignore */ }
}

async function deleteVersion(v) {
  try {
    await ElMessageBox.confirm(`${tt("确定删除版本 ")}${v.versionNo}${tt("？仅删除本地包文件和版本记录，不影响已部署服务器。")}`, tt('确认'), { type: 'warning' })
    await request.delete(`/admin/deploy-version/version/${v.id}`)
    ElMessage.success(tt('已删除'))
    await loadVersions()
  } catch (e) { if (e !== 'cancel') console.error('删除版本失败:', e) }
}

// ----- 备份/回滚 -----
const backupVisible = ref(false)
const backupLoading = ref(false)
const backupServer = ref(null)
const backups = ref([])

async function openBackups(s) {
  backupServer.value = s
  backupVisible.value = true
  backupLoading.value = true
  backups.value = []
  try {
    const r = await request.get('/admin/deploy/backups', { params: { serverId: s.id } })
    backups.value = r || []
  } catch (e) { ElMessage.error(e.message || tt('获取备份失败')) } finally { backupLoading.value = false }
}

async function doRollback(name) {
  try {
    await ElMessageBox.confirm(`${tt("确定回滚到备份 ")}${name}${tt("？当前版本将被该备份覆盖。")}`, tt('确认回滚'), { type: 'warning' })
    const r = await request.post('/admin/deploy/rollback', { serverId: backupServer.value.id, backupName: name })
    backupVisible.value = false
    openLogDialog(r.logId, '回滚执行')
  } catch (e) { if (e !== 'cancel') console.error('回滚失败:', e) }
}

// ----- 日志（实时轮询） -----
const logVisible = ref(false)
const logTitle = ref('')
const logData = reactive({ status: '', logText: '', versionNote: '' })
let pollTimer = null
let currentLogId = null
let currentLogUrl = '/admin/deploy/log/'

function openLogDialog(logId, title, logUrlPrefix) {
  currentLogId = logId
  currentLogUrl = logUrlPrefix || '/admin/deploy/log/'
  logTitle.value = (title || '执行') + '日志'
  logData.status = 'RUNNING'
  logData.logText = ''
  logVisible.value = true
  startPoll()
}

async function pollOnce() {
  if (!currentLogId) return
  try {
    const r = await request.get(currentLogUrl + currentLogId)
    if (r) {
      logData.status = r.status
      logData.logText = r.logText || ''
      logData.versionNote = r.versionNote
      if (r.status !== 'RUNNING') { stopPoll(); if (r.action === 'BUILD') loadVersions() }
    }
  } catch (e) { stopPoll() }
}

function startPoll() {
  stopPoll()
  pollOnce()
  pollTimer = setInterval(pollOnce, 2000)
}
function stopPoll() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null } }
onBeforeUnmount(stopPoll)

// ----- 部署历史 -----
const logsVisible = ref(false)
const logsLoading = ref(false)
const logsServer = ref(null)
const logs = ref([])

async function openLogs(s) {
  logsServer.value = s
  logsVisible.value = true
  logsLoading.value = true
  try {
    const r = await request.get('/admin/deploy/logs', { params: { limit: 50 } })
    // 服务器维度过滤；BUILD（打包）无 serverId，全局显示
    logs.value = (r || []).filter(l => !s || !l.serverId || l.serverId === s.id)
  } catch (e) { ElMessage.error(e.message || tt('加载失败')) } finally { logsLoading.value = false }
}

function viewLog(row) {
  logsVisible.value = false
  const title = row.action === 'DEPLOY' ? '部署' : (row.action === 'BUILD' ? '打包' : '回滚')
  openLogDialog(row.id, title, row.action === 'BUILD' ? '/admin/deploy-version/log/' : '/admin/deploy/log/')
}

// ----- 工具 -----
function statusTag(s) { return s === 'SUCCESS' ? 'success' : (s === 'RUNNING' ? 'warning' : (s === 'ROLLED_BACK' ? 'warning' : 'danger')) }
function statusText(s) { return { RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败', ROLLED_BACK: '失败已回滚' }[s] || s }
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '' }

async function loadServers() {
  loading.value = true
  try {
    const r = await request.get('/admin/deploy/servers')
    servers.value = r || []
  } catch (e) { ElMessage.error(e.message || tt('加载服务器列表失败')) } finally { loading.value = false }
}

loadServers()
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-bar { display: flex; gap: 12px; margin-bottom: 14px; }

.server-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; }
.server-card { border: 1px solid var(--el-border-color-light); border-radius: 8px; padding: 14px 16px; }
.server-card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.server-name { font-weight: 600; font-size: 15px; margin-right: 8px; }
.server-meta { margin-bottom: 12px; }
.meta-row { display: flex; gap: 12px; align-items: center; padding: 3px 0; font-size: 13px; }
.meta-label { color: var(--el-text-color-secondary); flex-shrink: 0; }
.meta-value { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.server-actions { display: flex; gap: 12px; align-items: center; }

.zip-input { width: 100%; }
.zip-info { margin-top: 6px; color: var(--el-color-success); font-size: 13px; }
.zip-tip { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }

.log-status-row { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.log-note { color: var(--el-text-color-secondary); font-size: 13px; }
.log-box { background: #1e1e1e; color: #d4d4d4; border-radius: 6px; padding: 12px; max-height: 56vh; overflow: auto; font-size: 12px; line-height: 1.6; white-space: pre-wrap; word-break: break-all; margin: 0; }
</style>
