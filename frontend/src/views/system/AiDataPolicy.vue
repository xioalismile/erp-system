<template>
  <div class="erp-page">
    <el-card shadow="never" class="policy-card">
      <div class="page-header">
        <h2 class="page-title">{{ $tt("AI 数据使用策略") }}</h2>
        <div class="page-desc">
          控制 AI 调用过程中数据的脱敏、日志记录、访问权限和工具能力范围。保护企业敏感数据不被泄漏到 AI 厂商或日志文件。
        </div>
      </div>

      <el-tabs v-model="activeTab" class="policy-tabs">
        <!-- 子标签：数据政策 -->
        <el-tab-pane :label="$tf('数据政策')" name="policy">
          <!-- AI 厂商数据政策说明 -->
          <el-divider content-position="left">
            <span class="divider-text">{{ $tt("AI 厂商数据使用政策") }}</span>
          </el-divider>

          <el-alert
            :title="$tt('重要说明')"
            type="success"
            :closable="false"
            show-icon
            style="margin-bottom: 16px;"
          >
            <template #default>
              本系统通过<b>{{ $tt("商用 API") }}</b> {{ $tt("调用 AI 服务，") }}<b>{{ $tt("数据默认不会被 AI 厂商用于模型训练") }}</b>。但数据会传输到 AI 厂商的服务器做推理。
              <br/>{{ $tt("请注意：不要与") }} <b>{{ $tt("AI 厂商网页版") }}</b>（如 chat.qwen.com、chat.deepseek.com）混淆 —— 网页版默认会用对话数据训练模型。
            </template>
          </el-alert>

          <el-table :data="vendorPolicies" border stripe style="margin-bottom: 16px;">
            <el-table-column prop="name" :label="$tf('AI 厂商')" width="180" sortable />
            <el-table-column prop="url" :label="$tf('API 地址')" width="280" sortable />
            <el-table-column :label="$tf('数据用于训练')" width="120">
              <template #default="{ row }">
                <el-tag :type="row.dataForTraining ? 'danger' : 'success'" size="small">
                  {{ row.dataForTraining ? '会用于训练' : '不用于训练' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" :label="$tf('说明')" sortable />
          </el-table>

          <!-- 政策配置 -->
          <el-divider content-position="left">
            <span class="divider-text">{{ $tt("政策配置") }}</span>
          </el-divider>

          <div class="policy-list">
            <div class="policy-item">
              <div class="policy-item-header">
                <div class="policy-item-title">
                  <el-icon><Lock /></el-icon>
                  <span>{{ $tt("敏感字段脱敏") }}</span>
                  <el-tag v-if="policy.maskSensitive" type="success" size="small">{{ $tt("已开启") }}</el-tag>
                  <el-tag v-else type="warning" size="small">{{ $tt("已关闭") }}</el-tag>
                </div>
                <el-switch v-model="policy.maskSensitive" @change="savePolicy" />
              </div>
              <div class="policy-item-desc">
                开启后，发送给 AI 的内容中所有敏感字段都会被替换为脱敏形式。AI 仍能理解字段含义，但拿不到真实数据。
                <div class="mask-examples">
                  <div>{{ $tt("手机号: 13800138000 →") }} <code>138****0000</code></div>
                  <div>{{ $tt("身份证: 110101199001011234 →") }} <code>110101********1234</code></div>
                  <div>{{ $tt("银行卡: 6222020200012345678 →") }} <code>622202***********5678</code></div>
                  <div>{{ $tt("邮箱: zhangsan@company.com →") }} <code>z***@company.com</code></div>
                  <div>{{ $tt("座机: 010-12345678 →") }} <code>010-****5678</code></div>
                </div>
              </div>
            </div>

            <div class="policy-item">
              <div class="policy-item-header">
                <div class="policy-item-title">
                  <el-icon><Document /></el-icon>
                  <span>{{ $tt("日志记录 AI 原文") }}</span>
                  <el-tag v-if="policy.logRawContent" type="warning" size="small">{{ $tt("已开启") }}</el-tag>
                  <el-tag v-else type="success" size="small">{{ $tt("已关闭") }}</el-tag>
                </div>
                <el-switch v-model="policy.logRawContent" @change="savePolicy" />
              </div>
              <div class="policy-item-desc">
                开启后，AI 请求和响应原文会写入后端日志文件。<b>{{ $tt("生产环境建议关闭") }}</b>，避免日志文件泄漏敏感信息。关闭后日志只记录 traceId、意图、参数长度等元信息。
              </div>
            </div>

            <div class="policy-item">
              <div class="policy-item-header">
                <div class="policy-item-title">
                  <el-icon><Key /></el-icon>
                  <span>{{ $tt("AI 严格模式") }}</span>
                  <el-tag v-if="policy.strictMode" type="success" size="small">{{ $tt("已开启") }}</el-tag>
                  <el-tag v-else type="info" size="small">{{ $tt("已关闭") }}</el-tag>
                </div>
                <el-switch v-model="policy.strictMode" @change="savePolicy" />
              </div>
              <div class="policy-item-desc">
                开启后，敏感实体（客户/供应商/员工/订单）的查询结果不会发送给 AI 做进一步推理，只返回原始数据给前端。
                <br/>适用于需要查询敏感数据但不想让 AI 二次处理的场景。
              </div>
            </div>
          </div>

          <!-- 安全建议 -->
          <el-divider content-position="left">
            <span class="divider-text">{{ $tt("安全建议") }}</span>
          </el-divider>

          <el-alert
            :title="$tt('生产环境部署建议')"
            type="info"
            :closable="false"
            show-icon
          >
            <template #default>
              <ol style="margin: 0; padding-left: 20px; line-height: 1.8;">
                <li><b>{{ $tt("敏感字段脱敏") }}</b>{{ $tt("：保持开启（默认已开启）") }}</li>
                <li><b>{{ $tt("日志记录 AI 原文") }}</b>{{ $tt("：关闭（默认已关闭）") }}</li>
                <li><b>API Key</b>{{ $tt("：已用 AES-GCM 加密存储，不会明文写入数据库") }}</li>
                <li><b>{{ $tt("HTTPS 部署") }}</b>{{ $tt("：生产环境必须使用 HTTPS，防止传输过程被监听") }}</li>
                <li><b>{{ $tt("API Key 权限") }}</b>{{ $tt("：在 AI 厂商后台为 ERP 创建专用 API Key，设置调用额度上限") }}</li>
                <li><b>{{ $tt("定期审计") }}</b>：通过"操作日志"页面查看 AI 调用记录，发现异常及时处理</li>
              </ol>
            </template>
          </el-alert>
        </el-tab-pane>

        <!-- 子标签：工具权限 -->
        <el-tab-pane :label="$tf('工具权限')" name="tools" lazy>
          <AiToolPermission />
        </el-tab-pane>
      </el-tabs>

      <!-- 保存提示 -->
      <div v-if="saved" class="save-tip">
        <el-icon color="#67c23a"><Check /></el-icon>
        <span>{{ $tt("设置已保存") }}</span>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, Document, Key, Check } from '@element-plus/icons-vue'
import request from '@/utils/request'
import AiToolPermission from './AiToolPermission.vue'
import { tt } from '@/i18n'

const activeTab = ref('policy')
const policy = ref({
  maskSensitive: true,
  logRawContent: false,
  strictMode: false
})
const saved = ref(false)
const vendorPolicies = ref([])

const loadPolicy = async () => {
  try {
    const data = await request.get('/ai/config/data-policy')
    policy.value.maskSensitive = data.maskSensitive
    policy.value.logRawContent = data.logRawContent
    policy.value.strictMode = data.strictMode
    // 转换厂商政策为表格数据
    const vendors = data.vendorPolicies || {}
    vendorPolicies.value = Object.values(vendors)
  } catch (e) {
    // 错误已由 request 拦截器提示
  }
}

const savePolicy = async () => {
  try {
    await request.post('/ai/config/data-policy', {
      maskSensitive: policy.value.maskSensitive,
      logRawContent: policy.value.logRawContent,
      strictMode: policy.value.strictMode
    })
    saved.value = true
    ElMessage.success(tt('AI 数据使用政策已更新'))
    setTimeout(() => { saved.value = false }, 2000)
  } catch (e) {
    // 错误已由 request 拦截器提示
  }
}

onMounted(() => {
  loadPolicy()
})
</script>

<style scoped lang="scss">
.erp-page { padding: 0; }
.policy-card {
  max-width: 1000px;
  margin: 20px auto;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  font-size: 22px;
  color: #303133;
  margin: 0 0 8px 0;
  font-weight: 600;
}
.page-desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}
.divider-text {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}
.policy-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.policy-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px 20px;
  background: #fafafa;
}
.policy-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.policy-item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}
.policy-item-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}
.mask-examples {
  margin-top: 8px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.8;

  code {
    background: #fff;
    padding: 1px 6px;
    border-radius: 3px;
    color: #e96900;
    font-family: 'Consolas', monospace;
  }
}
.save-tip {
  position: fixed;
  bottom: 24px;
  right: 24px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  background: #f0f9eb;
  color: #67c23a;
  border-radius: 6px;
  font-size: 13px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
</style>
