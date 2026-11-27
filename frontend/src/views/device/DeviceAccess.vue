<template>
  <div class="device-access">
    <div class="dev-toolbar">
      <div class="dev-actions">
        <span class="label">{{ $tt('当前设备') }}</span>
        <el-select v-model="deviceId" filterable clearable style="width: 260px" :placeholder="$tt('请选择设备')"
                   @change="onDeviceChange">
          <el-option v-for="d in devices" :key="d.id" :label="`${d.deviceCode} · ${d.deviceName}`" :value="d.id" />
        </el-select>
        <el-button size="small" @click="reloadAll">{{ $tt('刷新') }}</el-button>
      </div>
      <div class="hint-right">{{ $tt('先建台账，再生成凭证；密钥只在生成/轮换时显示一次') }}</div>
    </div>

    <div v-if="!deviceId" class="empty-guide">
      <div class="empty-title">{{ $tt('请先选择一台设备') }}</div>
      <div class="empty-desc">{{ $tt('接入配置按设备生效：凭证、通道、数据点都属于某一台设备。还没有设备请先到「设备台账」新增。') }}</div>
      <el-button type="primary" size="small" @click="goLedger">{{ $tt('去设备台账') }}</el-button>
    </div>

    <el-tabs v-else v-model="activeTab" class="dev-tabs">
      <!-- ==================== 接入凭证 ==================== -->
      <el-tab-pane :label="$tt('接入凭证')" name="credential">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">{{ $tt('凭证状态') }}</span>
            <span class="panel-actions">
              <el-button v-if="!credential" v-permission="perm('genKey')" type="primary" size="small" @click="onGenerate">{{ $tt('生成接入凭证') }}</el-button>
              <template v-else>
                <el-button v-if="credential.status === '启用'" v-permission="perm('rotateKey')" size="small" @click="onRotate">{{ $tt('轮换密钥') }}</el-button>
                <el-button v-if="credential.status === '启用'" v-permission="perm('revokeKey')" type="danger" size="small" @click="onRevoke">{{ $tt('吊销凭证') }}</el-button>
              </template>
            </span>
          </div>

          <div v-if="!credential" class="empty-inline">
            {{ $tt('该设备还没有接入凭证。生成后把凭证号与密钥配置到设备端，设备才能上报数据。') }}
          </div>
          <el-descriptions v-else :column="2" border size="small">
            <el-descriptions-item :label="$tf('状态')">
              <el-tag :type="credential.status === '启用' ? 'success' : 'danger'" size="small">{{ credential.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$tf('凭证号')"><span class="mono">{{ credential.credentialNo }}</span></el-descriptions-item>
            <el-descriptions-item :label="$tf('密钥')"><span class="mono">{{ credential.secretMasked }}</span></el-descriptions-item>
            <el-descriptions-item :label="$tf('最近接入')">{{ formatTime(credential.lastAccessTime) || '—' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('最近来源')">{{ credential.lastAccessIp || '—' }}</el-descriptions-item>
            <el-descriptions-item :label="$tf('连续失败')">{{ credential.failCount || 0 }} {{ $tt('次') }}</el-descriptions-item>
          </el-descriptions>

          <div class="sign-tip">
            <div class="sign-title">{{ $tt('设备端签名规则') }}</div>
            <div class="mono sign-line">sign = HEX( HMAC-SHA256( secret, credentialNo + "\n" + timestamp + "\n" + nonce + "\n" + rawBody ) )</div>
            <div class="sign-note">{{ $tt('请求头：X-Device-Credential / X-Device-Timestamp（毫秒）/ X-Device-Nonce / X-Device-Sign；时间戳允许 ±5 分钟，nonce 5 分钟内不可重复。') }}</div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 接入通道 ==================== -->
      <el-tab-pane :label="$tt('接入通道')" name="channel">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">{{ $tt('通道配置') }}</span>
            <span class="panel-actions">
              <el-button type="primary" size="small" @click="openAddChannel">{{ $tt('新增通道') }}</el-button>
            </span>
          </div>
          <el-table :data="channels" border size="small" v-loading="loading">
            <el-table-column prop="channelType" :label="$tf('通道类型')" width="120">
              <template #default="{ row }">{{ channelLabel(row.channelType) }}</template>
            </el-table-column>
            <el-table-column prop="channelName" :label="$tf('通道名称')" min-width="130" />
            <el-table-column :label="$tf('连接参数')" min-width="200" show-overflow-tooltip>
              <template #default="{ row }"><span class="mono">{{ row.configJson || '—' }}</span></template>
            </el-table-column>
            <el-table-column prop="heartbeatInterval" :label="$tf('心跳(秒)')" width="90" align="center" />
            <el-table-column prop="offlineThreshold" :label="$tf('离线周期')" width="90" align="center" />
            <el-table-column :label="$tf('启用')" width="80" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="onToggleChannel(row)" />
              </template>
            </el-table-column>
            <el-table-column :label="$tf('最近连接')" width="150" align="center">
              <template #default="{ row }">{{ formatTime(row.lastConnectedTime) || '—' }}</template>
            </el-table-column>
            <el-table-column :label="$tf('操作')" width="130" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openEditChannel(row)">{{ $tt('编辑') }}</el-button>
                <el-button type="danger" link size="small" @click="removeChannel(row)">{{ $tt('删除') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="empty-inline" v-if="!loading && channels.length === 0">
            {{ $tt('还没有配置通道。阶段 2 先支持 HTTP 上报；MQTT / TCP / 轮询在阶段 3 接入。') }}
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 数据点 ==================== -->
      <el-tab-pane :label="$tt('数据点')" name="point">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">{{ $tt('点位表') }}</span>
            <span class="panel-actions">
              <el-button type="primary" size="small" @click="openAddPoint">{{ $tt('新增点位') }}</el-button>
            </span>
          </div>
          <el-table :data="points" border size="small" v-loading="loading">
            <el-table-column prop="pointKey" :label="$tf('点位标识')" min-width="120" />
            <el-table-column prop="pointName" :label="$tf('点位名称')" min-width="120" />
            <el-table-column :label="$tf('业务用途')" width="120">
              <template #default="{ row }">{{ purposeLabel(row.purpose) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('数据类型')" width="90" align="center">
              <template #default="{ row }">{{ dataTypeLabel(row.dataType) }}</template>
            </el-table-column>
            <el-table-column prop="unit" :label="$tf('单位')" width="70" align="center" />
            <el-table-column prop="sampleInterval" :label="$tf('采集频率')" width="90" align="center">
              <template #default="{ row }">{{ row.sampleInterval }}s</template>
            </el-table-column>
            <el-table-column :label="$tf('最近值')" min-width="120">
              <template #default="{ row }">
                <span v-if="row.lastValue !== null && row.lastValue !== undefined">{{ row.lastValue }}</span>
                <span v-else class="muted">{{ $tt('暂无数据') }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('最近时间')" width="150" align="center">
              <template #default="{ row }">{{ formatTime(row.lastValueTime) || '—' }}</template>
            </el-table-column>
            <el-table-column :label="$tf('启用')" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? $tt('启用') : $tt('停用') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('操作')" width="130" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openEditPoint(row)">{{ $tt('编辑') }}</el-button>
                <el-button type="danger" link size="small" @click="removePoint(row)">{{ $tt('删除') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="empty-inline" v-if="!loading && points.length === 0">
            {{ $tt('还没有点位。点位标识必须与设备上报报文里的字段名一致，否则会进「未关联」提示。') }}
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 协议模板 ==================== -->
      <el-tab-pane :label="$tt('协议模板')" name="protocol">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">{{ $tt('协议模板') }}</span>
            <span class="panel-actions">
              <el-button type="primary" size="small" @click="openAddProtocol">{{ $tt('新增模板') }}</el-button>
            </span>
          </div>
          <el-table :data="protocols" border size="small" v-loading="loading">
            <el-table-column prop="templateCode" :label="$tf('模板编码')" width="130" />
            <el-table-column prop="templateName" :label="$tf('模板名称')" min-width="160" />
            <el-table-column :label="$tf('协议类型')" width="130">
              <template #default="{ row }">{{ protocolLabel(row.protocolType) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('报文格式')" width="100">
              <template #default="{ row }">{{ parseFormatLabel(row.parseFormat) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('来源')" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isPreset === 1 ? 'info' : 'success'" size="small">{{ row.isPreset === 1 ? $tt('系统预置') : $tt('自建') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" :label="$tf('说明')" min-width="200" show-overflow-tooltip />
            <el-table-column :label="$tf('操作')" width="190" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="onCopyProtocol(row)">{{ $tt('复制') }}</el-button>
                <el-button v-if="row.isPreset !== 1" type="primary" link size="small" @click="openEditProtocol(row)">{{ $tt('编辑') }}</el-button>
                <el-button v-if="row.isPreset !== 1" type="danger" link size="small" @click="removeProtocol(row)">{{ $tt('删除') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 密钥只显示一次 -->
    <el-dialog v-model="secretVisible" width="560px" :close-on-click-modal="false" :show-close="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ $tt('请立即保存设备密钥') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="copySecret">{{ $tt('复制') }}</el-button>
            <el-button size="small" type="primary" @click="secretVisible = false">{{ $tt('我已保存') }}</el-button>
          </span>
        </div>
      </template>
      <div class="secret-box">
        <div class="secret-row"><span class="secret-label">{{ $tt('凭证号') }}</span><span class="mono">{{ secretData.credentialNo }}</span></div>
        <div class="secret-row"><span class="secret-label">{{ $tt('密钥') }}</span><span class="mono secret-value">{{ secretData.secretPlain }}</span></div>
        <el-alert type="warning" :closable="false" show-icon
                  :title="$tt('密钥只显示这一次，关闭后无法再次查看；如遗失请轮换密钥。')" />
      </div>
    </el-dialog>

    <!-- 通道表单 -->
    <el-dialog v-model="channelDialog" width="640px" :close-on-click-modal="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ channelEditId ? $tt('编辑通道') : $tt('新增通道') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="channelDialog = false">{{ $tt('取消') }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onSaveChannel">{{ $tt('保存') }}</el-button>
          </span>
        </div>
      </template>
      <el-form label-width="100px">
        <el-form-item :label="$tf('通道类型')" required>
          <el-select v-model="channelForm.channelType" style="width:100%" :disabled="!!channelEditId" @change="onChannelTypeChange">
            <el-option v-for="t in channelTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$tf('通道名称')"><el-input v-model="channelForm.channelName" maxlength="100" /></el-form-item>
        <el-form-item :label="$tf('连接参数')">
          <el-input v-model="channelForm.configJson" type="textarea" :rows="6" class="mono" />
          <div class="hint">{{ $tt('JSON 格式；HTTP 通道一般只需 path，其余字段按设备实际情况填写。') }}</div>
          <div class="hint">
            {{ $tt('MQTT/TCP 报文外层为信封 JSON：credentialNo、timestamp、nonce、sign、encoding、payload；payload 按点位绑定的协议模板解析。') }}
          </div>
          <div class="hint">
            {{ $tt('轮询协议填 MODBUS_TCP 或 OPC_UA；Modbus 地址写 HR:100 或 COIL:0，OPC UA 地址写 ns=2;s=节点名。') }}
          </div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="$tf('心跳间隔')">
              <el-input-number v-model="channelForm.heartbeatInterval" :min="5" :max="3600" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('离线周期')">
              <el-input-number v-model="channelForm.offlineThreshold" :min="1" :max="100" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('启用')">
          <el-switch v-model="channelForm.enabled" :active-value="1" :inactive-value="0" />
          <span class="hint">{{ $tt('默认关闭；配好并确认后再打开。') }}</span>
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 点位表单 -->
    <el-dialog v-model="pointDialog" width="760px" :close-on-click-modal="false" top="6vh">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ pointEditId ? $tt('编辑点位') : $tt('新增点位') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="pointDialog = false">{{ $tt('取消') }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onSavePoint">{{ $tt('保存') }}</el-button>
          </span>
        </div>
      </template>
      <el-form label-width="100px">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item :label="$tf('点位标识')" required>
              <el-input v-model="pointForm.pointKey" maxlength="64" :placeholder="$tt('与报文里的字段名一致，如 run_signal')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('点位名称')" required>
              <el-input v-model="pointForm.pointName" maxlength="100" :placeholder="$tt('如：运行信号')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('业务用途')" required>
              <el-select v-model="pointForm.purpose" style="width:100%">
                <el-option v-for="p in purposes" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('数据类型')">
              <el-select v-model="pointForm.dataType" style="width:100%">
                <el-option :label="$tf('布尔')" value="BOOLEAN" />
                <el-option :label="$tf('整数')" value="INTEGER" />
                <el-option :label="$tf('小数')" value="DECIMAL" />
                <el-option :label="$tf('文本')" value="TEXT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('单位')"><el-input v-model="pointForm.unit" maxlength="30" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('协议模板')">
              <el-select v-model="pointForm.templateId" clearable filterable style="width:100%">
                <el-option v-for="t in protocolOptions" :key="t.id" :label="`${t.templateName}（${protocolLabel(t.protocolType)}）`" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('设备地址')">
              <el-input v-model="pointForm.address" maxlength="200" :placeholder="$tt('Modbus 寄存器 / OPC UA 节点，可空')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('报文键')">
              <el-input v-model="pointForm.parseKey" maxlength="100" :placeholder="$tt('JSON 路径，默认同点位标识')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('换算系数')">
              <el-input-number v-model="pointForm.scaleFactor" :precision="4" :step="0.1" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('换算偏移')">
              <el-input-number v-model="pointForm.scaleOffset" :precision="4" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('采集频率')">
              <el-input-number v-model="pointForm.sampleInterval" :min="5" :max="3600" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('死区')">
              <el-input-number v-model="pointForm.deadZone" :precision="4" :min="0" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('报警下限')">
              <el-input-number v-model="pointForm.alarmLower" :precision="4" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('报警上限')">
              <el-input-number v-model="pointForm.alarmUpper" :precision="4" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('可写')">
              <el-switch v-model="pointForm.writable" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('启用')">
              <el-switch v-model="pointForm.enabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-dialog>

    <!-- 协议模板表单 -->
    <el-dialog v-model="protocolDialog" width="700px" :close-on-click-modal="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ protocolEditId ? $tt('编辑协议模板') : $tt('新增协议模板') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="protocolDialog = false">{{ $tt('取消') }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onSaveProtocol">{{ $tt('保存') }}</el-button>
          </span>
        </div>
      </template>
      <el-form label-width="100px">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item :label="$tf('模板编码')">
              <el-input v-model="protocolForm.templateCode" maxlength="50" :placeholder="$tt('留空自动生成')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('模板名称')" required>
              <el-input v-model="protocolForm.templateName" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('协议类型')" required>
              <el-select v-model="protocolForm.protocolType" style="width:100%">
                <el-option v-for="p in protocolTypes" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('报文格式')">
              <el-select v-model="protocolForm.parseFormat" style="width:100%">
                <el-option v-for="f in parseFormats" :key="f.value" :label="f.label" :value="f.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('字节序')">
              <el-select v-model="protocolForm.byteOrder" style="width:100%">
                <el-option :label="$tf('大端')" value="BIG_ENDIAN" />
                <el-option :label="$tf('小端')" value="LITTLE_ENDIAN" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('字序')">
              <el-select v-model="protocolForm.wordOrder" style="width:100%">
                <el-option :label="$tf('高字在前')" value="HIGH_FIRST" />
                <el-option :label="$tf('低字在前')" value="LOW_FIRST" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('时间来源')">
              <el-select v-model="protocolForm.timestampSource" style="width:100%">
                <el-option :label="$tf('云端接收时间')" value="CLOUD" />
                <el-option :label="$tf('设备时间')" value="DEVICE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('状态')">
              <el-select v-model="protocolForm.status" style="width:100%">
                <el-option :label="$tf('启用')" value="启用" />
                <el-option :label="$tf('停用')" value="停用" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('连接参数')">
          <el-input v-model="protocolForm.configJson" type="textarea" :rows="6" class="mono" />
          <div class="hint">{{ $tt('JSON 格式，按协议类型填写地址、端口、从站、主题等；系统预置模板可复制后修改。') }}</div>
        </el-form-item>
        <el-form-item :label="$tf('说明')"><el-input v-model="protocolForm.remark" maxlength="500" /></el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { listEnabledDevices } from '@/api/device/ledger'
import {
  getCurrentCredential, generateCredential, rotateCredential, revokeCredential
} from '@/api/device/credential'
import { listChannels, saveChannel, updateChannel, toggleChannel, deleteChannel } from '@/api/device/channel'
import { listPoints, createPoint, updatePoint, deletePoint } from '@/api/device/point'
import { pageProtocols, listProtocols, createProtocol, updateProtocol, copyProtocol, deleteProtocol } from '@/api/device/protocol'

const router = useRouter()
const perm = usePagePerm('device:access')

const devices = ref([])
const deviceId = ref(null)
const activeTab = ref('credential')
const loading = ref(false)
const saving = ref(false)

// ==================== 字典 ====================
const channelTypes = [
  { value: 'HTTP', label: tt('HTTP 上报') },
  { value: 'MQTT', label: tt('MQTT') },
  { value: 'TCP', label: tt('TCP 长连接') },
  { value: 'WEBSOCKET', label: tt('WebSocket') },
  { value: 'POLL', label: tt('云端轮询') }
]
const purposes = [
  { value: 'START_SIGNAL', label: tt('开工信号') },
  { value: 'STOP_SIGNAL', label: tt('停机信号') },
  { value: 'QUALIFIED_COUNT', label: tt('合格计数') },
  { value: 'REJECTED_COUNT', label: tt('报废计数') },
  { value: 'ALARM_CODE', label: tt('报警码') },
  { value: 'FAULT_BIT', label: tt('故障位') },
  { value: 'PARAM', label: tt('工艺参数') },
  { value: 'WRITABLE_PARAM', label: tt('可写参数') },
  { value: 'ORDER_NO', label: tt('工单号') },
  { value: 'OPERATION_NO', label: tt('工序号') },
  { value: 'MONITOR', label: tt('普通监控') }
]
const protocolTypes = [
  { value: 'MODBUS_TCP', label: 'Modbus TCP' },
  { value: 'MODBUS_RTU', label: 'Modbus RTU' },
  { value: 'OPC_UA', label: 'OPC UA' },
  { value: 'MQTT', label: 'MQTT' },
  { value: 'HTTP', label: 'HTTP' },
  { value: 'TCP', label: tt('自定义 TCP') },
  { value: 'SERIAL', label: tt('自定义串口') }
]
const parseFormats = [
  { value: 'JSON', label: 'JSON' },
  { value: 'FIXED', label: tt('定长') },
  { value: 'DELIMITER', label: tt('分隔符') },
  { value: 'REGEX', label: tt('正则') },
  { value: 'SCRIPT', label: tt('脚本') }
]

const channelLabel = (v) => channelTypes.find(t => t.value === v)?.label || v || ''
const purposeLabel = (v) => purposes.find(p => p.value === v)?.label || v || ''
const dataTypeLabel = (v) => ({ BOOLEAN: tt('布尔'), INTEGER: tt('整数'), DECIMAL: tt('小数'), TEXT: tt('文本') }[v] || v || '')
const protocolLabel = (v) => protocolTypes.find(p => p.value === v)?.label || v || ''
const parseFormatLabel = (v) => parseFormats.find(f => f.value === v)?.label || v || ''
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '')

// ==================== 设备 ====================
async function loadDevices() {
  try {
    devices.value = await listEnabledDevices() || []
    if (!deviceId.value && devices.value.length > 0) deviceId.value = devices.value[0].id
  } catch (e) {
    devices.value = []
  }
}

function onDeviceChange() {
  loadCredential()
  loadChannels()
  loadPoints()
}

function reloadAll() {
  loadDevices()
  onDeviceChange()
  loadProtocols()
}

function goLedger() {
  router.push('/device/ledger')
}

// ==================== 凭证 ====================
const credential = ref(null)
const secretVisible = ref(false)
const secretData = reactive({ credentialNo: '', secretPlain: '' })

async function loadCredential() {
  credential.value = null
  if (!deviceId.value) return
  try {
    credential.value = await getCurrentCredential(deviceId.value)
  } catch (e) {
    credential.value = null
  }
}

async function onGenerate() {
  try {
    const res = await generateCredential(deviceId.value)
    showSecret(res)
    await loadCredential()
  } catch (e) { /* 后端已给出原因 */ }
}

async function onRotate() {
  try {
    await ElMessageBox.confirm(tt('轮换后旧密钥立即失效，设备端必须同步更新为新密钥。确定轮换？'), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    const res = await rotateCredential(credential.value.id)
    showSecret(res)
    await loadCredential()
  } catch (e) { /* 后端已给出原因 */ }
}

async function onRevoke() {
  try {
    await ElMessageBox.confirm(tt('吊销后该设备所有上报都会被拒绝。确定吊销？'), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    await revokeCredential(credential.value.id)
    ElMessage.success(tt('已吊销'))
    await loadCredential()
  } catch (e) { /* 后端已给出原因 */ }
}

function showSecret(res) {
  secretData.credentialNo = res.credentialNo
  secretData.secretPlain = res.secretPlain
  secretVisible.value = true
}

async function copySecret() {
  const text = `${secretData.credentialNo}\n${secretData.secretPlain}`
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(tt('已复制'))
  } catch (e) {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    try { document.execCommand('copy'); ElMessage.success(tt('已复制')) } catch (err) { ElMessage.warning(tt('复制失败，请手动选择文本')) }
    document.body.removeChild(ta)
  }
}

// ==================== 通道 ====================
const channels = ref([])
const channelDialog = ref(false)
const channelEditId = ref(null)
const channelForm = reactive({ channelType: 'HTTP', channelName: '', configJson: '', heartbeatInterval: 30, offlineThreshold: 3, enabled: 0 })

async function loadChannels() {
  if (!deviceId.value) { channels.value = []; return }
  try {
    channels.value = await listChannels(deviceId.value) || []
  } catch (e) {
    channels.value = []
  }
}

function defaultConfig(type) {
  const map = {
    HTTP: { path: '/api/device/ingest/http' },
    MQTT: { host: '', port: 1883, topic: 'device/data', qos: 1, username: '', password: '' },
    TCP: { port: 9001, frame: 'LENGTH_JSON' },
    WEBSOCKET: { path: '/ws/device' },
    POLL: { protocol: 'MODBUS_TCP', host: '', port: 502, unitId: 1, interval: 30, timeoutMs: 3000 }
  }
  return JSON.stringify(map[type] || {}, null, 2)
}

function onChannelTypeChange(type) {
  channelForm.configJson = defaultConfig(type)
}

function openAddChannel() {
  channelEditId.value = null
  Object.assign(channelForm, {
    channelType: 'HTTP', channelName: '', configJson: defaultConfig('HTTP'),
    heartbeatInterval: 30, offlineThreshold: 3, enabled: 0
  })
  channelDialog.value = true
}

function openEditChannel(row) {
  channelEditId.value = row.id
  Object.assign(channelForm, {
    channelType: row.channelType, channelName: row.channelName || '',
    configJson: row.configJson || '', heartbeatInterval: row.heartbeatInterval || 30,
    offlineThreshold: row.offlineThreshold || 3, enabled: row.enabled ?? 0
  })
  channelDialog.value = true
}

async function onSaveChannel() {
  saving.value = true
  try {
    const payload = { ...channelForm, deviceId: deviceId.value }
    if (channelEditId.value) await updateChannel(channelEditId.value, payload)
    else await saveChannel(payload)
    ElMessage.success(tt('保存成功'))
    channelDialog.value = false
    await loadChannels()
  } catch (e) { /* 后端已给出原因 */ } finally {
    saving.value = false
  }
}

async function onToggleChannel(row) {
  try {
    await toggleChannel(row.id, row.enabled)
    ElMessage.success(tt('操作成功'))
  } catch (e) {
    row.enabled = row.enabled === 1 ? 0 : 1
  }
}

async function removeChannel(row) {
  try {
    await ElMessageBox.confirm(tt(`确定删除通道「${row.channelName}」？`), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    await deleteChannel(row.id)
    ElMessage.success(tt('删除成功'))
    await loadChannels()
  } catch (e) { /* 后端已给出原因 */ }
}

// ==================== 点位 ====================
const points = ref([])
const pointDialog = ref(false)
const pointEditId = ref(null)
const pointForm = reactive({})

function emptyPoint() {
  return {
    pointKey: '', pointName: '', purpose: 'MONITOR', dataType: 'DECIMAL', unit: '',
    templateId: null, address: '', parseKey: '', scaleFactor: 1, scaleOffset: 0,
    sampleInterval: 30, deadZone: null, writable: 0, alarmLower: null, alarmUpper: null,
    alarmLevel: '警告', enabled: 1
  }
}

async function loadPoints() {
  if (!deviceId.value) { points.value = []; return }
  try {
    points.value = await listPoints(deviceId.value) || []
  } catch (e) {
    points.value = []
  }
}

function openAddPoint() {
  pointEditId.value = null
  Object.assign(pointForm, emptyPoint())
  pointDialog.value = true
}

function openEditPoint(row) {
  pointEditId.value = row.id
  Object.assign(pointForm, emptyPoint(), {
    pointKey: row.pointKey, pointName: row.pointName, purpose: row.purpose,
    dataType: row.dataType, unit: row.unit || '', templateId: row.templateId,
    address: row.address || '', parseKey: row.parseKey || '',
    scaleFactor: row.scaleFactor ?? 1, scaleOffset: row.scaleOffset ?? 0,
    sampleInterval: row.sampleInterval ?? 30, deadZone: row.deadZone,
    writable: row.writable ?? 0, alarmLower: row.alarmLower, alarmUpper: row.alarmUpper,
    alarmLevel: row.alarmLevel || '警告', enabled: row.enabled ?? 1
  })
  pointDialog.value = true
}

async function onSavePoint() {
  if (!pointForm.pointKey || !pointForm.pointKey.trim()) { ElMessage.warning(tt('请填写点位标识')); return }
  if (!pointForm.pointName || !pointForm.pointName.trim()) { ElMessage.warning(tt('请填写点位名称')); return }
  saving.value = true
  try {
    const payload = { ...pointForm, deviceId: deviceId.value, parseKey: pointForm.parseKey || pointForm.pointKey }
    if (pointEditId.value) await updatePoint(pointEditId.value, payload)
    else await createPoint(payload)
    ElMessage.success(tt('保存成功'))
    pointDialog.value = false
    await loadPoints()
  } catch (e) { /* 后端已给出原因 */ } finally {
    saving.value = false
  }
}

async function removePoint(row) {
  try {
    await ElMessageBox.confirm(tt(`确定删除点位「${row.pointName}」？该点位的实时值也会一并清除。`), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    await deletePoint(row.id)
    ElMessage.success(tt('删除成功'))
    await loadPoints()
  } catch (e) { /* 后端已给出原因 */ }
}

// ==================== 协议模板 ====================
const protocols = ref([])
const protocolOptions = ref([])
const protocolDialog = ref(false)
const protocolEditId = ref(null)
const protocolForm = reactive({})

function emptyProtocol() {
  return {
    templateCode: '', templateName: '', protocolType: 'HTTP', parseFormat: 'JSON',
    byteOrder: 'BIG_ENDIAN', wordOrder: 'HIGH_FIRST', timestampSource: 'CLOUD',
    status: '启用', configJson: '{}', remark: ''
  }
}

async function loadProtocols() {
  try {
    const res = await pageProtocols({ current: 1, size: 100 })
    protocols.value = res.records || []
    protocolOptions.value = await listProtocols() || []
  } catch (e) {
    protocols.value = []
    protocolOptions.value = []
  }
}

function openAddProtocol() {
  protocolEditId.value = null
  Object.assign(protocolForm, emptyProtocol())
  protocolDialog.value = true
}

function openEditProtocol(row) {
  protocolEditId.value = row.id
  Object.assign(protocolForm, {
    templateCode: row.templateCode, templateName: row.templateName,
    protocolType: row.protocolType, parseFormat: row.parseFormat,
    byteOrder: row.byteOrder || 'BIG_ENDIAN', wordOrder: row.wordOrder || 'HIGH_FIRST',
    timestampSource: row.timestampSource || 'CLOUD', status: row.status || '启用',
    configJson: row.configJson || '{}', remark: row.remark || ''
  })
  protocolDialog.value = true
}

async function onSaveProtocol() {
  if (!protocolForm.templateName || !protocolForm.templateName.trim()) { ElMessage.warning(tt('请填写模板名称')); return }
  saving.value = true
  try {
    if (protocolEditId.value) await updateProtocol(protocolEditId.value, { ...protocolForm })
    else await createProtocol({ ...protocolForm })
    ElMessage.success(tt('保存成功'))
    protocolDialog.value = false
    await loadProtocols()
  } catch (e) { /* 后端已给出原因 */ } finally {
    saving.value = false
  }
}

async function onCopyProtocol(row) {
  try {
    const res = await copyProtocol(row.id)
    ElMessage.success(tt(`已复制为「${res.templateName}」`))
    await loadProtocols()
  } catch (e) { /* 后端已给出原因 */ }
}

async function removeProtocol(row) {
  try {
    await ElMessageBox.confirm(tt(`确定删除协议模板「${row.templateName}」？`), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    await deleteProtocol(row.id)
    ElMessage.success(tt('删除成功'))
    await loadProtocols()
  } catch (e) { /* 后端已给出原因 */ }
}

// ==================== 初始化 ====================
onMounted(async () => {
  await loadDevices()
  await Promise.all([loadCredential(), loadChannels(), loadPoints(), loadProtocols()])
})
</script>

<style scoped lang="scss">
.device-access {
  background: #fff;
  min-height: 100%;
  padding: 10px 12px 20px;
  box-sizing: border-box;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
}
.dev-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 0 10px;
  border-bottom: 1px solid #eef1f5;
  flex-wrap: wrap;
}
.dev-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.dev-actions .label {
  font-size: 13px;
  color: #475569;
}
.hint-right {
  font-size: 12px;
  color: #94a3b8;
}
.empty-guide {
  border: 1px dashed #d7dee9;
  border-radius: 8px;
  padding: 26px 20px;
  text-align: center;
  color: #64748b;
  margin-top: 16px;
}
.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 6px;
}
.empty-desc {
  font-size: 13px;
  margin-bottom: 14px;
  line-height: 1.7;
}
.empty-inline {
  font-size: 12.5px;
  color: #94a3b8;
  padding: 10px 2px;
  line-height: 1.7;
}
.dev-tabs {
  padding-top: 6px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 10px;
}
.panel-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #334155;
}
.panel-actions {
  display: inline-flex;
  gap: 8px;
}
.mono {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12.5px;
  word-break: break-all;
}
.muted {
  color: #94a3b8;
  font-size: 12.5px;
}
.sign-tip {
  margin-top: 14px;
  background: #f7f9fd;
  border: 1px solid #e6ecf5;
  border-radius: 8px;
  padding: 12px 14px;
}
.sign-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 6px;
}
.sign-line {
  font-size: 12px;
  color: #1f6feb;
  line-height: 1.7;
}
.sign-note {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.7;
  margin-top: 6px;
}
.dlg-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.dlg-title {
  font-size: 15px;
  font-weight: 600;
}
.dlg-header-actions {
  display: inline-flex;
  gap: 8px;
}
.hint {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}
.secret-box {
  padding: 4px 2px;
}
.secret-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 10px;
}
.secret-label {
  width: 64px;
  flex: 0 0 64px;
  color: #64748b;
  font-size: 13px;
}
.secret-value {
  color: #b45309;
  font-weight: 600;
}
</style>
