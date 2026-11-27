<template>
  <div class="ai-assistant-wrapper">
    <div class="ai-fab" :class="{ 'ai-fab-listening': wakeupListening }" @click="openAssistant" v-show="!visible">
      <img :src="fabCurrentImg" class="ai-fab-img" :class="'ai-fab-img-' + fabState" />
      <span v-if="wakeupListening" class="ai-fab-listening-text">{{ $tt("聆听中…") }}</span>
      <span v-if="pendingApproval > 0" class="ai-fab-badge">{{ pendingApproval > 99 ? '99+' : pendingApproval }}</span>
    </div>

    <el-drawer class="ai-assistant-drawer" v-model="visible" :title="$tf('🤖 小楷 · ERP全能智能助手')" direction="rtl" size="80%" :with-header="true">
      <div class="channel-tabs">
        <!-- 语音三功能（语音提醒/语音唤醒/语音直发）：客户包（VITE_VOICE_FEATURES=false）整块不显示 -->
        <template v-if="VOICE_FEATURES_ENABLED">
        <el-tooltip :content="muted ? $tt('当前已静音，点击恢复语音提醒') : $tt('语音提醒已开启，点击可静音')" placement="top">
          <el-tag :type="muted ? 'info' : 'success'" effect="plain" size="small" class="voice-mute-tag" @click="onToggleMute">
            {{ muted ? $tt('🔇 语音已静音') : $tt('🔊 语音提醒') }}
          </el-tag>
        </el-tooltip>
        <el-tooltip :content="voiceConfirmTip" placement="top">
          <el-tag :type="voiceConfirm ? 'warning' : 'success'" effect="plain" size="small" class="voice-mute-tag" @click="onToggleVoiceConfirm">
            {{ voiceConfirm ? $tt('🗣️ 语音需确认') : $tt('⚡ 语音直发') }}
          </el-tag>
        </el-tooltip>
        <!-- 纯语音（不打开界面）：语音指令不弹抽屉，直接语音播报结果；写操作改口语确认后直接执行 -->
        <el-tooltip :content="voiceSilentTip" placement="top">
          <el-tag :type="voiceSilent ? 'success' : 'info'" effect="plain" size="small" class="voice-mute-tag" @click="onToggleVoiceSilent">
            {{ voiceSilent ? $tt('🚀 纯语音开') : $tt('🚀 纯语音') }}
          </el-tag>
        </el-tooltip>
        <!-- 本地语音采音开关：浏览器只做能量门限+上传短片段，识别与唤醒词匹配都在自己的服务器 -->
        <el-tooltip :content="wakeupTagTip" placement="top">
          <el-tag :type="swOn ? 'success' : (wakeupPending ? 'warning' : 'info')" effect="plain" size="small" class="voice-mute-tag" @click="toggleServerWakeup">
            {{ swOn ? $tt('🧪 语音采音：开') : (wakeupPending ? $tt('🧪 语音采音：待点击恢复') : $tt('🧪 语音采音：关')) }}
          </el-tag>
        </el-tooltip>
        </template>
        <!-- 会话管理（2026-10-13 升级）：新建会话 → 清屏 + 换新会话；会话记录永久保留，随时可以接回 -->
        <!-- 2026-10-22：新增「历史会话」入口 —— 只列本人会话，可接回继续聊、可删除 -->
        <el-tooltip :content="$tt('查看我的历史会话：只显示当前登录人的会话，点开可继续聊，也可删除')" placement="top">
          <el-tag type="success" effect="plain" size="small" class="voice-mute-tag" @click="openHistory" style="margin-left:auto">{{ $tt("🕘 历史会话") }}</el-tag>
        </el-tooltip>
        <el-tooltip :content="$tt('新建一个会话（上下文重新开始，不影响任何业务数据；旧会话记录永久保留）')" placement="top">
          <el-tag type="info" effect="plain" size="small" class="voice-mute-tag" @click="onClearConversation">{{ $tt("🆕 新建会话") }}</el-tag>
        </el-tooltip>
        <el-tag type="warning" effect="plain" size="small" @click="openSettings" style="cursor:pointer">{{ $tt("⚙️ 设置") }}</el-tag>
        <!-- 积分余额（2026-10-24）：个人积分优先；未分配则显示公司共享积分 -->
        <el-tooltip v-if="creditLoaded && creditControlled" :content="creditTip" placement="top">
          <el-tag :type="creditWarn ? 'warning' : 'success'" effect="plain" size="small" class="credit-chip">
            {{ $tt("积分") }} {{ creditText }}
          </el-tag>
        </el-tooltip>
        <!-- 深度思考开关（2026-10-24）：旧版界面（121 客户包）保留原开关；新版界面由模型选择弹层内的"思考强度"三档接管 -->
        <el-tooltip v-if="!AI_UI_V2" :content="deepThinkingTip" placement="top">
          <el-tag :type="deepThinking ? 'primary' : 'info'" effect="plain" size="small" class="deep-toggle" @click="onToggleDeepThinking">
            {{ $tt("🧠 深度思考") }}
          </el-tag>
        </el-tooltip>
      </div>

      <!-- 低余额提示（2026-10-24）：只有已启用积分控制时才提示，存量客户未启用不打扰 -->
      <div v-if="creditMessage" class="credit-warn">
        <span>{{ creditMessage }}</span>
      </div>

      <!-- 语音采集自测面板及其后台统计已按用户要求整套删除（2026-09-18）：界面不显示、也不再记录。
           采音/唤醒功能本身不受影响（在 utils/serverWakeup.js）。 -->

      <div class="chat-area" ref="chatArea">
        <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role, { 'legacy-ui': !AI_UI_V2 && msg.role === 'bot' }]">
          <div class="msg-role">{{ msg.role === 'user' ? '👤 我' : '🤖 小楷' }}</div>
          <div v-if="msg.images && msg.images.length > 0" class="msg-images">
            <img v-for="(img, idx) in msg.images" :key="idx" :src="img" class="msg-image" />
          </div>
          <div v-if="msg.files && msg.files.length > 0" class="msg-files">
            <span v-for="(f, idx) in msg.files" :key="idx" class="msg-file">📄 {{ f.name }}</span>
          </div>

          <!-- 答复文字（用户 2026-10-06 拍板：按 Trae 顺序 —— 执行过程在上、答复在下）。
               视觉顺序由样式 order 统一控制，DOM 仍保留答复在前，不复制模板。
               2026-11-22 改版（结论进结果表）：答复里出现表格时，表格渲染成真正的结果表，
               文字只保留结论与口径说明；旧版界面与无表格答复仍按原样整段文字显示。 -->
          <template v-if="AI_UI_V2 && answerParts(msg)">
            <div v-for="(line, li) in answerParts(msg).before" :key="'atb' + li" class="msg-text-line">{{ line }}</div>
            <div class="msg-answer-table">
              <el-table :data="answerParts(msg).rows" size="mini" max-height="320" border stripe>
                <el-table-column
                  v-for="col in answerParts(msg).columns"
                  :key="col"
                  :prop="col"
                  :label="col"
                  min-width="72"
                  show-overflow-tooltip />
              </el-table>
            </div>
            <div v-for="(line, li) in answerParts(msg).after" :key="'ata' + li" class="msg-text-line">{{ line }}</div>
          </template>
          <div v-else-if="msg.text" class="msg-text">{{ msg.text }}</div>
          
          <div v-if="msg.imageSearchResult && msg.imageSearchResult.success && msg.imageSearchResult.matched" class="image-search-result">
            <div class="image-search-header">
              <span class="result-title">{{ $tt("🔍 以图识图结果") }}</span>
              <el-tag :type="getAccuracyTagType(msg.imageSearchResult.accuracyLevel)" size="small" effect="dark">
                准确度: {{ msg.imageSearchResult.accuracyLevel }} ({{ msg.imageSearchResult.topSimilarity.toFixed(1) }}%)
              </el-tag>
            </div>

            <div v-if="msg.imageSearchResult.materials && msg.imageSearchResult.materials.length > 0" class="matched-materials">
              <div class="section-subtitle">匹配物料 ({{ msg.imageSearchResult.materials.length }} 个)</div>
              <div class="material-list">
                <div 
                  v-for="(mat, idx) in msg.imageSearchResult.materials" 
                  :key="idx" 
                  class="material-item"
                  :class="{ 'top-match': idx === 0 }">
                  <div class="mat-image-wrap">
                    <img :src="mat.mainImage" class="mat-image" />
                    <div v-if="idx === 0" class="mat-badge">{{ $tt("最佳匹配") }}</div>
                  </div>
                  <div class="mat-info">
                    <div class="mat-name">{{ mat.materialName }}</div>
                    <div class="mat-code">{{ mat.materialCode }}</div>
                    <div v-if="mat.model" class="mat-spec">规格: {{ mat.model }}</div>
                    <div class="mat-similarity">
                      <el-progress 
                        :percentage="mat.similarity" 
                        :color="getSimilarityColor(mat.similarity)"
                        :stroke-width="8" />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="msg.imageSearchResult.documents && msg.imageSearchResult.documents.length > 0" class="related-docs">
              <div class="section-subtitle">
                📄 相关单据 ({{ msg.imageSearchResult.documents.length }} 条)
              </div>
              <div class="doc-list">
                <div 
                  v-for="(doc, idx) in msg.imageSearchResult.documents" 
                  :key="idx" 
                  class="doc-item">
                  <div class="doc-type-tag">{{ doc.documentType }}</div>
                  <div class="doc-main">
                    <el-link type="primary" :underline="false" class="doc-no" @click="openDocDetail(doc)">
                      {{ doc.documentNo }}
                    </el-link>
                    <div v-if="doc.customerName" class="doc-customer">客户: {{ doc.customerName }}</div>
                    <div v-if="doc.orderDate" class="doc-date">日期: {{ doc.orderDate }}</div>
                  </div>
                  <div class="doc-right">
                    <div v-if="doc.quantity" class="doc-qty">数量: {{ doc.quantity }}</div>
                    <div v-if="doc.amount" class="doc-amount">金额: ¥{{ doc.amount }}</div>
                    <el-tag v-if="doc.status" :type="getStatusTagType(doc.status)" size="small">
                      {{ $dt(doc.status) }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="msg.imageSearchResult && msg.imageSearchResult.success && !msg.imageSearchResult.matched" class="msg-action">
            <el-alert :title="$tt('🔍 未找到匹配的物料，建议上传更清晰的物料图片')" type="warning" :closable="false" show-icon />
          </div>
          
          <div v-if="msg.actionResult && msg.actionResult.success && msg.actionResult.data && msg.actionResult.data.length > 0" class="msg-result">
            <div class="result-header">
              <span class="result-title">📊 查询结果 ({{ msg.actionResult.total }} 条)</span>
              <el-tooltip :content="$tt('点击编码/单号列可跳转到详情页')" placement="top">
                <el-tag size="small" type="info" effect="plain">{{ $tt("💡 可点击") }}</el-tag>
              </el-tooltip>
            </div>
            <div class="result-table-wrap">
              <el-table :data="msg.actionResult.data" size="mini" max-height="280" border stripe>
                <el-table-column 
                  v-for="(col, idx) in getResultColumns(msg.actionResult.data, msg.actionResult, 10)"
                  :key="idx"
                  :prop="col"
                  :label="formatLabel(col, msg.actionResult)"
                  min-width="100"
                  show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-link 
                      v-if="isClickableColumn(col, msg.actionResult)"
                      type="primary" 
                      :underline="false" 
                      @click="openEntityDetail(row, msg.actionResult)"
                      style="font-weight:500;">
                      {{ row[col] }}
                    </el-link>
                    <span v-else>{{ row[col] }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>

          <!-- 多步骤任务卡：一条任务一张卡，逐步展示状态；失败可只重跑失败步骤，可撤销本次新建 -->
          <div v-if="msg.actionResult && msg.actionResult.taskId" class="msg-action">
            <div class="task-card">
              <div class="task-head">
                <span class="task-title">任务 {{ msg.actionResult.taskNo || ('#' + msg.actionResult.taskId) }}</span>
                <span class="task-status" :class="'ts-' + taskStatusClass(msg.actionResult.status)">{{ msg.actionResult.status }}</span>
              </div>
              <div v-if="msg.actionResult.goal" class="task-goal">{{ msg.actionResult.goal }}</div>
              <div class="task-steps">
                <div v-for="s in (msg.actionResult.steps || [])" :key="s.line" class="task-step" :class="'ts-' + taskStatusClass(s.status)">
                  <div class="task-step-line">
                    <span class="task-step-title">{{ s.line }}. {{ s.title }}</span>
                    <span class="task-step-badge">{{ s.status }}</span>
                  </div>
                  <div v-if="s.message" class="task-step-msg">{{ s.message }}</div>
                </div>
              </div>
              <div class="task-actions">
                <el-button
                  v-if="hasFailedSteps(msg.actionResult)"
                  size="small" type="primary" plain
                  :loading="msg.taskRetrying"
                  v-permission="perm('dlgConfirm')"
                  @click="retryTask(msg)">
                  重跑失败步骤
                </el-button>
                <el-button
                  v-if="hasCreatedSteps(msg.actionResult)"
                  size="small" type="danger" plain
                  :loading="msg.taskUndoing"
                  v-permission="perm('dlgConfirm')"
                  @click="undoTask(msg)">
                  撤销本次新建
                </el-button>
              </div>
            </div>
          </div>

          <div v-if="msg.actionResult && msg.actionResult.success && msg.actionResult.id" class="msg-action">
            <div class="success-card">
              <div class="success-title">✅ 已创建{{ createdEntityLabel(msg.actionResult) }}</div>
              <div class="success-info">
                <span v-if="msg.actionResult.name" class="success-name">{{ msg.actionResult.name }}</span>
                <span v-if="msg.actionResult.code && msg.actionResult.code !== msg.actionResult.name" class="success-code">{{ msg.actionResult.code }}</span>
              </div>
              <el-button v-if="hasCreatedRoute(msg.actionResult)" v-permission="perm('view')" type="primary" size="small" text bg @click="openCreatedEntity(msg.actionResult)">
                查看{{ createdEntityLabel(msg.actionResult) }} →
              </el-button>
            </div>
          </div>
          
          <div v-if="msg.actionResult && !msg.actionResult.success && !msg.actionResult.taskId" class="msg-action">
            <el-alert :title="'❌ 操作失败: ' + msg.actionResult.message" type="error" :closable="false" show-icon />
          </div>

          <!-- 失败/部分成功明细：必须让用户看到"为什么失败"，而不是只回一句成功或失败 -->
          <div v-if="msg.actionResult && msg.actionResult.errorMessages && msg.actionResult.errorMessages.length > 0" class="msg-action">
            <div class="err-title">失败/提示明细（{{ msg.actionResult.errorMessages.length }} 条）</div>
            <div class="err-list">
              <div v-for="(line, idx) in msg.actionResult.errorMessages.slice(0, 20)" :key="idx" class="err-line">· {{ line }}</div>
              <div v-if="msg.actionResult.errorMessages.length > 20" class="err-line">…还有 {{ msg.actionResult.errorMessages.length - 20 }} 条</div>
            </div>
          </div>

          <div v-if="msg.action && !msg.actionResult" class="msg-action">
            <el-alert :title="msg.action" type="success" :closable="false" show-icon />
          </div>

          <!-- ② 查询结果数据：本轮查询结果合并成一张「结果表」（有共同业务键按共同键横向拼；
               无共同键只显示主查询结果，其余留在执行过程），明细模式始终可见 -->
          <div v-if="answerMode === 'detail' && mergedResult(msg)" class="msg-result">
            <div class="result-header">
              <span class="result-title">📊 结果表 ({{ mergedResult(msg).rows.length }} 条)</span>
              <span v-if="mergedResult(msg).mergeHint" class="result-merge-hint">{{ mergedResult(msg).mergeHint }}</span>
            </div>
            <div class="result-table-wrap">
              <el-table :data="mergedResult(msg).rows" size="mini" max-height="320" border stripe>
                <el-table-column
                  v-for="(col, idx) in mergedResult(msg).columns"
                  :key="idx"
                  :prop="col.prop"
                  :label="col.label"
                  min-width="100"
                  show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-link
                      v-if="col.clickable"
                      type="primary"
                      :underline="false"
                      @click="openEntityDetail(row, msg.resultSets[0] && msg.resultSets[0].toolResult)"
                      style="font-weight:500;">
                      {{ row[col.prop] }}
                    </el-link>
                    <span v-else>{{ row[col.prop] }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>

          <!-- ④ 待确认卡：这是「AI 的提问」，始终可见、不参与折叠（用户必须看到并拍板）。
               2026-10-06 Trae 式重排：卡片从"折叠区内部"提到独立区块，视觉上排在最底（答复文字之下），
               后端"请在下方确认卡片"的措辞据此保持成立。 -->
          <div v-if="confirmSteps(msg).length > 0" class="agent-steps confirm-area msg-steps-confirm">
            <div
              v-for="(step, idx) in confirmSteps(msg)"
              :key="'confirm' + idx"
              class="agent-step step-confirm_needed">
              <div class="step-header">
                <span class="step-icon">🔐</span>
                <span class="step-label" style="color:#b45309">{{ $tt("待确认操作") }}</span>
              </div>
              <div class="step-content">
                <pre class="confirm-summary">{{ step.content }}</pre>
                <div v-if="!step.confirmStatus" class="confirm-buttons">
                  <el-button type="primary" size="small" v-permission="perm('dlgConfirm')" :loading="step.confirming" @click="confirmWriteTool(step)">
                    ✓ 确认执行
                  </el-button>
                  <el-button size="small" v-permission="perm('dlgCancel')" @click="cancelWriteTool(step)">{{ $tt("取消") }}</el-button>
                  <span class="confirm-hint" v-if="AI_UI_V2">{{ $tt("也可以直接回复「确认」") }}</span>
                </div>
                <el-alert v-if="step.confirmStatus === 'confirmed'" :title="$tt('已确认执行')" type="success" :closable="false" show-icon />
                <el-alert v-if="step.confirmStatus === 'cancelled'" :title="$tt('已取消，未做任何数据变更')" type="info" :closable="false" show-icon />
                <el-alert v-if="step.confirmStatus === 'failed'" :title="'执行失败: ' + (step.confirmMessage || '未知错误')" type="error" :closable="false" show-icon />
                <el-alert v-if="step.confirmStatus === 'superseded'" :title="$tt('该操作已失效（已被下方新的操作取代）')" type="info" :closable="false" show-icon />
              </div>
            </div>
          </div>

          <!-- 答复呈现方式=汇总时，失败信息也必须看得到（过程被收起，不能把错误一起藏掉） -->
          <div v-if="answerMode === 'summary' && !msg.text && errorSteps(msg).length" class="agent-steps msg-steps-process">
            <div v-for="(step, eidx) in errorSteps(msg)" :key="'err' + eidx" class="agent-step step-error">
              <div class="step-header">
                <span class="step-icon">⚠️</span>
                <span class="step-label">{{ $tt("失败") }}</span>
              </div>
              <div class="step-content"><div class="step-brief">{{ step.content }}</div></div>
            </div>
          </div>

          <!-- ⑤ 执行过程 —— 2026-11-22 改版（TRAE 式思考链路，仅新版界面）：运行中自动展开，
               思考与查数据逐条长出来；跑完整条自动收起，点状态行再展开。
               思考 = 一行点开推理卡片；每次查数据 = 一行业务摘要 + 结果小字，点开看参数与明细；错误 = 红色一行。
               旧版界面（121 客户包）保留原折叠条，原样不动。2026-10-22 口径不变：汇总模式整块不显示。 -->
          <div v-if="answerMode === 'detail' && hasProcessSteps(msg) && AI_UI_V2" class="agent-steps msg-steps-process tl-wrap">
            <div class="agent-steps-header clickable tl-header" @click="msg.stepsExpanded = !msg.stepsExpanded">
              <span class="steps-title" :class="{ 'is-running': isMsgRunning(msg) }">
                <span class="tl-title-ico">{{ isMsgRunning(msg) ? '' : '💭' }}</span>
                <span v-if="isMsgRunning(msg)" class="step-thinking-dots"><i></i><i></i><i></i></span>
                {{ isMsgRunning(msg) ? $tt("思考中") : $tt("思考链路") }}
                <span class="tl-count">{{ timelineItems(msg).length }} 步</span>
              </span>
              <span class="steps-toggle tl-arrow">{{ msg.stepsExpanded ? '∨' : '›' }}</span>
            </div>
            <div v-show="msg.stepsExpanded" class="agent-steps-list tl-mode">
              <template v-for="(item, idx) in timelineItems(msg)" :key="idx">
                <!-- 思考节点：一行，点开推理卡片；流式期间卡片实时展开 -->
                <div v-if="item.kind === 'thought'" class="tl-item tl-thought" :class="{ 'is-streaming': item.step.streaming }">
                  <div class="tl-row clickable" @click="toggleThoughtItem(item)">
                    <span class="tl-ico">💭</span>
                    <span class="tl-label">{{ item.step.streaming ? $tt("思考中") : $tt("思考") }}</span>
                    <span v-if="item.step.streaming" class="step-thinking-dots"><i></i><i></i><i></i></span>
                    <span v-else class="tl-arrow-sm">{{ tlIsOpen(item) ? '∨' : '›' }}</span>
                  </div>
                  <div v-show="item.step.streaming || tlIsOpen(item)" class="tl-card" :class="{ 'tl-card-live': item.step.streaming }">
                    <pre v-if="item.step.reasoning" class="reasoning-text" :class="{ 'reasoning-live': item.step.streaming }">{{ item.step.reasoning }}</pre>
                    <div v-if="item.step.reasoning && item.step.content" class="step-brief">{{ item.step.content }}</div>
                    <div v-else-if="!item.step.reasoning" class="tl-card-text">{{ item.step.content }}</div>
                  </div>
                </div>
                <!-- 工具节点：调用与结果并为一个节点，一行摘要 + 结果小字 -->
                <div v-else-if="item.kind === 'tool'" class="tl-item tl-tool">
                  <div class="tl-row clickable" @click="toggleTlOpen(item)">
                    <span class="tl-ico">🛠️</span>
                    <span class="tl-label">{{ toolLabel(item.action ? item.action.toolName : (item.obs && item.obs.toolName)) }}</span>
                    <span class="tl-sub" :class="tlSubClass(item)">{{ toolResultBrief(item) }}</span>
                    <span class="tl-arrow-sm">{{ tlIsOpen(item) ? '∨' : '›' }}</span>
                  </div>
                  <div v-show="tlIsOpen(item)" class="tl-detail">
                    <div v-if="item.action && item.action.toolParams && Object.keys(item.action.toolParams).length" class="tool-params">
                      <span class="params-label">{{ $tt("参数:") }}</span>
                      <pre class="params-json">{{ JSON.stringify(item.action.toolParams, null, 2) }}</pre>
                    </div>
                    <div v-if="item.obs && item.obs.toolResult && item.obs.toolResult.data && item.obs.toolResult.data.length > 0" class="obs-result">
                      <el-table :data="pagedResult(item.obs)" size="mini" max-height="240" border stripe>
                        <el-table-column
                          v-for="(col, cidx) in getResultColumns(item.obs.toolResult.data, item.obs.toolResult).slice(0, 5)"
                          :key="cidx"
                          :prop="col"
                          :label="formatLabel(col, item.obs.toolResult)"
                          min-width="80"
                          show-overflow-tooltip />
                      </el-table>
                      <div v-if="item.obs.toolResult.data.length > 10" class="obs-more">
                        <el-pagination
                          size="small"
                          layout="prev, pager, next, total"
                          :total="item.obs.toolResult.data.length"
                          :page-size="10"
                          :current-page="item.obs.resultPage || 1"
                          @current-change="(p) => item.obs.resultPage = p" />
                      </div>
                    </div>
                    <div v-else-if="item.obs && item.obs.toolResult" class="obs-text">
                      {{ item.obs.toolResult.message || JSON.stringify(item.obs.toolResult).slice(0, 200) }}
                    </div>
                  </div>
                </div>
                <!-- 错误节点 -->
                <div v-else-if="item.kind === 'error'" class="tl-item tl-error">
                  <div class="tl-row">
                    <span class="tl-ico">⚠️</span>
                    <span class="tl-label tl-error-text">{{ item.step.content }}</span>
                  </div>
                </div>
              </template>
            </div>
          </div>
          <!-- 旧版界面（121 客户包）：执行过程折叠条，样式与交互原样保留 -->
          <div v-else-if="answerMode === 'detail' && hasProcessSteps(msg)" class="agent-steps msg-steps-process">
            <div class="agent-steps-header clickable" @click="msg.stepsExpanded = !msg.stepsExpanded">
              <span class="steps-title">{{ $tt("💭 查看执行过程") }}</span>
              <el-tag size="small" type="info" effect="plain">{{ actionCount(msg) + ' 次工具调用' }}</el-tag>
              <el-tag v-if="errorCount(msg) > 0" size="small" type="danger" effect="plain">{{ errorCount(msg) }} 个错误</el-tag>
              <span class="steps-toggle">{{ msg.stepsExpanded ? '收起' : '展开' }}</span>
            </div>
            <div v-show="msg.stepsExpanded" class="agent-steps-list">
              <div 
                v-for="(step, idx) in processSteps(msg)" 
                :key="idx" 
                :class="['agent-step', `step-${step.type.toLowerCase()}`]">
                <div v-if="step.type === 'THOUGHT'" class="step-header">
                  <span class="step-icon">💭</span>
                  <span class="step-label">{{ step.streaming ? $tt("思考中") : (step.reasoning ? $tt("思考（模型推理）") : $tt("思考")) }}</span>
                  <span v-if="step.streaming" class="step-thinking-dots"><i></i><i></i><i></i></span>
                </div>
                <div v-if="step.type === 'THOUGHT'" class="step-content">
                  <!-- 2026-10-13 用户要求"思考过程不再静默"：模型真实推理优先展示（滚动区防超长）；
                       模型未返回推理时降级为系统业务化步骤说明；
                       2026-10-14 流式分步思考：思考中（streaming）推理文本实时追加显示 -->
                  <pre v-if="step.reasoning" class="reasoning-text" :class="{ 'reasoning-live': step.streaming }">{{ step.reasoning }}</pre>
                  <div v-if="step.reasoning && step.content" class="step-brief">{{ step.content }}</div>
                  <template v-if="!step.reasoning">{{ step.content }}</template>
                </div>

                <div v-if="step.type === 'ACTION'" class="step-header">
                  <span class="step-icon">🛠️</span>
                  <span class="step-label">{{ $tt("调用工具") }}: {{ step.toolName }}</span>
                </div>
                <div v-if="step.type === 'ACTION'" class="step-content">
                  <div class="tool-params">
                    <span class="params-label">{{ $tt("参数:") }}</span>
                    <pre class="params-json">{{ JSON.stringify(step.toolParams, null, 2) }}</pre>
                  </div>
                </div>

                <!-- 工具结果默认只显示一行摘要，点这一行才展开原始表格（用户 2026-11-12：结果不该直接展开） -->
                <div v-if="step.type === 'OBSERVATION'" class="step-header clickable" @click="step.obsExpanded = !step.obsExpanded">
                  <span class="step-icon">📋</span>
                  <span class="step-label">{{ $tt("工具结果") }}</span>
                  <el-tag v-if="step.toolResult && step.toolResult.success" size="small" type="success" effect="dark">{{ $tt("成功") }}</el-tag>
                  <el-tag v-else size="small" type="danger" effect="dark">{{ $tt("失败") }}</el-tag>
                  <span class="steps-toggle">{{ step.obsExpanded ? $tt('收起结果') : obsSummary(step) }}</span>
                </div>
                <div v-if="step.type === 'OBSERVATION'" class="step-content" v-show="step.obsExpanded">
                  <div v-if="step.toolResult && step.toolResult.data && step.toolResult.data.length > 0" class="obs-result">
                    <el-table :data="pagedResult(step)" size="mini" max-height="240" border stripe>
                      <el-table-column
                        v-for="(col, cidx) in getResultColumns(step.toolResult.data, step.toolResult).slice(0, 5)"
                        :key="cidx"
                        :prop="col"
                        :label="formatLabel(col, step.toolResult)"
                        min-width="80"
                        show-overflow-tooltip />
                    </el-table>
                    <div v-if="step.toolResult.data.length > 10" class="obs-more">
                      <el-pagination
                        size="small"
                        layout="prev, pager, next, total"
                        :total="step.toolResult.data.length"
                        :page-size="10"
                        :current-page="step.resultPage || 1"
                        @current-change="(p) => step.resultPage = p" />
                    </div>
                  </div>
                  <div v-else class="obs-text">
                    {{ step.toolResult ? (step.toolResult.message || JSON.stringify(step.toolResult).slice(0, 200)) : '无结果' }}
                  </div>
                </div>

                <div v-if="step.type === 'ERROR'" class="step-header">
                  <span class="step-icon">⚠️</span>
                  <span class="step-label" style="color:#f56c6c">{{ $tt("错误") }}</span>
                </div>
                <div v-if="step.type === 'ERROR'" class="step-content error-content">
                  {{ step.content }}
                </div>
              </div>
            </div>
          </div>

          <!-- ⑥ 本轮用量（耗时 + 额度消耗）：答复末尾灰色小字（2026-10-13 用户要求）。
               老消息没有 USAGE 步骤时不渲染，行为与升级前一致。 -->
          <div v-if="usageSummaryText(msg)" class="msg-usage">{{ usageSummaryText(msg) }}</div>
        </div>
        <div v-if="loading && !(AI_UI_V2 && lastBotHasSteps())" class="msg bot">
          <div class="msg-role">{{ $tt("🤖 小楷") }}</div>
          <div class="msg-text thinking">
            <span class="dot">●</span>
            <span class="dot">●</span>
            <span class="dot">●</span>
            {{ waitSeconds >= 15 ? `AI分析中（${waitSeconds}秒）...` : '思考中...' }}
          </div>
        </div>
      </div>

      <div class="quick-cmds-section">
        <div class="section-label">{{ $tt("常用指令：") }}</div>
        <div class="quick-cmds">
          <el-button 
            v-for="(cmd, idx) in fixedQuickCmds" 
            :key="idx"
            text 
            size="small" 
            @click="sendQuick(cmd.text)"
            v-permission="perm('view')">
            {{ cmd.icon }} {{ cmd.text }}
          </el-button>
        </div>
      </div>

      <!-- 已上传文件/图片预览 -->
      <div v-if="attachments.length > 0" class="attachments-area">
        <div class="attachments-list">
          <div v-for="(att, idx) in attachments" :key="idx" class="att-item">
            <div v-if="att.type.startsWith('image')" class="att-image-wrap">
              <img :src="att.preview" :alt="att.name" class="att-image" />
            </div>
            <div v-else class="att-file">
              <span class="att-icon">📄</span>
              <span class="att-name">{{ att.name }}</span>
            </div>
            <div class="att-remove" @click="removeAttachment(idx)">×</div>
          </div>
        </div>
      </div>

      <div class="input-area">
        <div class="input-toolbar">
          <input ref="fileInputRef" type="file" multiple style="display:none" @change="handleFileSelect" />
          <span class="tool-btn" @click="triggerFileSelect" :title="$tt('上传图片/文件')">📎</span>
          <span class="tool-btn" @click="toggleRecord" :class="{ recording: isRecording }" :title="isRecording ? '停止录音' : '语音输入'">
            <span v-if="!isRecording">🎤</span>
            <span v-else class="rec-pulse">🔴</span>
          </span>
          <span class="tool-tip" v-if="isRecording">{{ recordTip }}</span>
        </div>
        <div class="input-box" :class="{ 'drag-over': isDragOver }"
          @dragenter="onDragOver"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="handleDrop">
          <div v-if="isDragOver" class="drag-tip">{{ $tt("松开鼠标即可上传文件") }}</div>
          <div class="input-text-wrap" @paste="handlePaste" ref="inputWrap">
            <el-input 
              ref="msgInputRef"
              v-model="input" 
              type="textarea"
              :rows="3"
              :placeholder="$tt('问我任何问题，或直接下达指令；可点 📎 上传文件、直接 Ctrl+V 粘贴，或把文件拖进来')" 
              @keyup.enter.ctrl="send"
              class="msg-input" />
          </div>
          <div class="input-bottom">
            <!-- 模型选择（2026-11-22 照参考设计）：新版 = 弹层（左模型列表 + 右能力面板：思考强度三档 / 上下文窗口三档）；
                 旧版界面（121 客户包）保留原下拉，原样不动 -->
            <template v-if="AI_UI_V2">
              <el-popover placement="top-start" :width="560" trigger="click" :teleported="false" popper-class="ai-model-picker">
                <template #reference>
                  <span class="model-trigger">
                    {{ selectedModel ? selectedModel.label : $tt('选择模型') }}
                    <span class="model-trigger-caret">▾</span>
                  </span>
                </template>
                <div class="mp-body">
                  <div class="mp-list">
                    <template v-for="g in modelOptionGroups" :key="g.label">
                      <div class="mp-group">{{ g.label }}</div>
                      <div
                        v-for="o in g.options"
                        :key="o.value"
                        :class="['mp-item', { 'is-active': o.value === selectedModelKey, 'is-disabled': o.disabled }]"
                        @click="!o.disabled && pickModel(o.value)">
                        <span class="mp-name">{{ o.label }}</span>
                        <span v-if="o.value === selectedModelKey" class="mp-check">✓</span>
                      </div>
                    </template>
                  </div>
                  <div class="mp-panel">
                    <div class="mp-sec-title">{{ $tt('思考强度') }}</div>
                    <div class="mp-seg">
                      <span
                        v-for="lv in THOUGHT_LEVELS"
                        :key="lv.value"
                        :class="['mp-seg-btn', { 'is-on': thoughtLevel === lv.value }]"
                        @click="thoughtLevel = lv.value">{{ lv.label }}</span>
                    </div>
                    <div class="mp-desc">{{ thoughtLevelDesc }}</div>
                    <div class="mp-sec-title">{{ $tt('上下文窗口') }}</div>
                    <div class="mp-seg">
                      <span
                        v-for="cm in CONTEXT_LEVELS"
                        :key="cm.value"
                        :class="['mp-seg-btn', { 'is-on': contextMode === cm.value }]"
                        @click="contextMode = cm.value">{{ cm.label }}</span>
                    </div>
                    <div class="mp-desc">{{ contextModeDesc }}</div>
                  </div>
                </div>
              </el-popover>
            </template>
            <el-select
              v-else
              v-model="selectedModelKey"
              size="small"
              class="model-select"
              :placeholder="$tt('选择模型')"
              @change="onModelChange">
              <el-option-group
                v-for="g in modelOptionGroups"
                :key="g.label"
                :label="g.label">
                <el-option
                  v-for="o in g.options"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                  :disabled="o.disabled" />
              </el-option-group>
            </el-select>
            <el-button type="primary" v-permission="perm('query')" @click="send" :loading="loading" class="send-btn">
              发送
            </el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 模型服务商配置对话框 -->
    <el-dialog v-model="settingsVisible" :title="$tf('⚙️ 设置')" width="640px" :close-on-click-modal="false">
      <AiAssistantSettingsPanel v-if="settingsVisible" />
    </el-dialog>

    <!-- 历史会话（2026-10-22）：只列本人会话，点开接回继续聊，可删除 -->
    <el-dialog v-model="historyVisible" :title="$tf('🕘 我的历史会话')" width="560px">
      <div v-loading="historyLoading" class="history-list">
        <div v-if="!historyLoading && historySessions.length === 0" class="history-empty">
          {{ $tt("还没有历史会话，先去跟小楷聊几句吧") }}
        </div>
        <div v-for="s in historySessions" :key="s.sessionId" class="history-item"
             :class="{ 'history-item-current': s.sessionId === sessionId }" @click="switchToSession(s)">
          <div class="history-item-main">
            <div class="history-item-title">{{ s.title || $tt('新对话') }}</div>
            <div class="history-item-meta">{{ formatSessionTime(s.lastAt) }} · {{ s.messageCount }}{{ $tt(' 条') }}</div>
          </div>
          <el-button size="small" type="danger" plain @click.stop="removeSession(s)">{{ $tf('删除') }}</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 首次使用协议闸门（每个登录账号勾选一次，永久生效；未同意时小楷全部入口不可用） -->
    <XiaokaiDisclaimerDialog v-model="disclaimerVisible" @accepted="onDisclaimerAccepted" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
// 模型服务商 / 渠道配置：此处只保留对话模型选择器所需的内置渠道 + 自定义服务商列表；
// 服务商配置表单（保存 Key / 测试连接 / 增删改）已抽到 components/Ai/AiAssistantSettingsPanel.vue 自行调用
import { listChannels as apiListChannels, listProviders as apiListProviders } from '../api/ai/modelChannel'
import { getMyCreditWallet } from '@/api/ai/credit'
import { listChatSessions as apiChatSessionList, getChatSessionMessages as apiChatSessionMessages, deleteChatSession as apiChatSessionDelete } from '@/api/ai/chatSession'
import { retryAiTaskFailed, undoAiTask } from '@/api/ai/task'
import { tt } from '@/i18n'
// 首次使用协议闸门：协议正文写死在前端常量（constants/xiaokaiDisclaimer.js），同意记录存后端账号级
import XiaokaiDisclaimerDialog from './Ai/XiaokaiDisclaimerDialog.vue'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('system:ai-assistant-settings')

// ⚙️ 设置弹窗内容（内置服务商 / 自定义服务商 / 展示与播报）：与设置中心「AI 助手设置」共用同一面板
import AiAssistantSettingsPanel from './Ai/AiAssistantSettingsPanel.vue'
import { useAiDisplayPrefs } from '@/composables/useAiDisplayPrefs'
import { getDisclaimerStatus } from '../api/ai/disclaimer'
import { DISCLAIMER_VERSION } from '@/constants/xiaokaiDisclaimer'
import { isMuted, toggleMute, EVENT_APPROVAL_INCREASE, getLastApprovalContext, speakText, speakProgress, buildSpeakSummary, isSpeaking, playWakeAck, playCue } from '@/utils/voiceNotify'
import { VOICE_FEATURES_ENABLED, AI_UI_V2 } from '@/config/featureFlags'
import {
  EVENT_WAKEUP, isVoiceConfirmEnabled, toggleVoiceConfirm,
  isVoiceSilentEnabled, toggleVoiceSilent, isVoiceEndPhrase, isVoiceCancelPhrase
} from '@/utils/voiceWakeup'
// WAV 编码抽成公共模块（前端统一一份实现，避免多处重复）
import { encodeWav } from '@/utils/wavEncode'
// 服务器端唤醒（本地语音）：浏览器只做能量门限+上传短片段，唤醒词匹配与识别都在自建 ASR
import {
  startServerWakeup, stopServerWakeup, isServerWakeupRunning, armVoiceCommand, setCmdWaitTimeoutMs
} from '@/utils/serverWakeup'
// 界面导航：页面清单的单一来源 = menuConfig.js（含设置中心子项）；自定义菜单名来自 sys_config 的 menu_name_*
import { MENU_GROUPS, getMenuConfigKey } from '@/config/menuConfig'
import { useUserStore } from '@/stores/user'
import { getSysConfigByGroup } from '@/api/system/sysConfig'
import { getVoiceRuntimeConfig } from '@/api/ai/modelChannel'
import { scopedGet, scopedSet } from '@/utils/userScopedStorage'

// ===== 小楷机器人形象动作帧（待机/唤醒/干活/结束）=====
import idle1 from '@/assets/ai-assistant/idle_1.png'
import idle2 from '@/assets/ai-assistant/idle_2.png'
import idle3 from '@/assets/ai-assistant/idle_3.png'
import wakeup1 from '@/assets/ai-assistant/wakeup_1.png'
import wakeup2 from '@/assets/ai-assistant/wakeup_2.png'
import wakeup3 from '@/assets/ai-assistant/wakeup_3.png'
import work1 from '@/assets/ai-assistant/work_1.png'
import work2 from '@/assets/ai-assistant/work_2.png'
import work3 from '@/assets/ai-assistant/work_3.png'
import end1 from '@/assets/ai-assistant/end_1.png'
import end2 from '@/assets/ai-assistant/end_2.png'
import end3 from '@/assets/ai-assistant/end_3.png'

const fabFrames = {
  idle: [idle1, idle2, idle3],
  wakeup: [wakeup1, wakeup2, wakeup3],
  work: [work1, work2, work3],
  end: [end1, end2, end3]
}
// 浮球动画状态：idle 待机随机 / wakeup 唤醒 / work 干活 / end 结束
const fabState = ref('idle')
const fabFrameIndex = ref(0)
let fabTimer = null
function stopFabTimer() { if (fabTimer) { clearInterval(fabTimer); clearTimeout(fabTimer); fabTimer = null } }
function playFabSequence(frames, interval, onDone) {
  stopFabTimer()
  fabFrameIndex.value = 0
  let i = 0
  fabTimer = setInterval(() => {
    i++
    if (i >= frames.length) {
      stopFabTimer()
      if (onDone) onDone()
      return
    }
    fabFrameIndex.value = i
  }, interval)
}
function startIdleRandom() {
  fabState.value = 'idle'
  fabFrameIndex.value = Math.floor(Math.random() * fabFrames.idle.length)
  stopFabTimer()
  // 待机：每 2~4 秒随机切一帧，避免一动不动显得死板
  fabTimer = setInterval(() => {
    let next = Math.floor(Math.random() * fabFrames.idle.length)
    if (next === fabFrameIndex.value) next = (next + 1) % fabFrames.idle.length
    fabFrameIndex.value = next
  }, 2500)
}
function playWakeup() {
  fabState.value = 'wakeup'
  playFabSequence(fabFrames.wakeup, 500, () => { /* 唤醒帧播完停在最后一帧，等聆听结束 */ })
}
function playWork() {
  fabState.value = 'work'
  fabFrameIndex.value = 0
  stopFabTimer()
  // 干活：3 帧循环播放，直到 loading 结束
  fabTimer = setInterval(() => {
    fabFrameIndex.value = (fabFrameIndex.value + 1) % fabFrames.work.length
  }, 700)
}
function playEnding(onDone) {
  fabState.value = 'end'
  playFabSequence(fabFrames.end, 500, onDone)
}
const fabCurrentImg = computed(() => fabFrames[fabState.value]?.[fabFrameIndex.value] || fabFrames.idle[0])

const router = useRouter()
const visible = ref(false)
const input = ref('')
const loading = ref(false)
const waitSeconds = ref(0)
let waitTimer = null

// ===== 首次使用协议闸门（2026-09-19）=====
// 口径：每个登录账号勾选一次、永久生效；未同意前小楷**全部入口**（浮球点击 / 语音唤醒 / 纯语音后台执行 /
// 通知与审批跳转）一律拦下；点 ✕ 或「不同意」→ 关弹窗并关抽屉，浮球保留，下次打开仍弹协议。
const disclaimerVisible = ref(false)
const disclaimerAccepted = ref(false)   // true = 已同意（放行，不再查接口）
const disclaimerChecked = ref(false)    // 是否已向后端确认过状态（避免每次点击都请求）
let openAfterAccept = false             // 被闸门挡下的打开动作：同意后自动把抽屉打开

/** 查询后端同意状态（接口失败按"未同意"处理：宁可多弹，不可漏放） */
async function refreshDisclaimerStatus() {
  try {
    const res = await getDisclaimerStatus(DISCLAIMER_VERSION)
    disclaimerAccepted.value = !!(res && res.accepted)
  } catch (e) {
    disclaimerAccepted.value = false
  } finally {
    disclaimerChecked.value = true
  }
  return disclaimerAccepted.value
}

/** 闸门判定：已同意 → true；未同意 → 弹协议并返回 false */
async function ensureDisclaimer() {
  if (disclaimerAccepted.value) return true
  if (!disclaimerChecked.value) await refreshDisclaimerStatus()
  if (disclaimerAccepted.value) return true
  disclaimerVisible.value = true
  return false
}

/** 浮球点击（唯一的显式打开入口；语音/通知等其它路径由下方 watch(visible) 兜底拦截） */
async function openAssistant() {
  const ok = await ensureDisclaimer()
  openAfterAccept = !ok
  if (ok) visible.value = true
}

/** 同意成功：放行；若本次是被闸门挡下的打开动作，同意后自动打开抽屉 */
function onDisclaimerAccepted() {
  disclaimerAccepted.value = true
  disclaimerChecked.value = true
  if (openAfterAccept) {
    openAfterAccept = false
    visible.value = true
  }
}

// 兜底拦截：任何路径想把抽屉打开（语音唤醒、通知跳转、内部逻辑）而尚未同意 → 撤回并弹协议
watch(visible, (v) => {
  if (v && !disclaimerAccepted.value) {
    visible.value = false
    openAfterAccept = true
    ensureDisclaimer()
  }
})

// ===== 语音提醒（本组件只做「浮球气泡 + 本机静音开关」呈现，实际播报在 utils/voiceNotify） =====
const pendingApproval = ref(0)
const muted = ref(false)

function onToggleMute() {
  muted.value = toggleMute()
  if (muted.value) ElMessage.info(tt('已静音：不再语音播报，浮球气泡仍会提示'))
  else ElMessage.success(tt('已恢复语音提醒'))
}

function onApprovalIncrease(e) {
  pendingApproval.value = Number((e && e.detail && e.detail.count) || 0)
}

// ===== 语音交互状态（唤醒采集在 utils/serverWakeup.js，识别在服务器自建 ASR） =====
// 唤醒后「聆听中」状态：浮球变绿呼吸、先不打开界面，等用户说完话才处理（用户要求）
const wakeupListening = ref(false)
// 语音指令是否需要"二次确认"（用户本地偏好，默认关闭 = 说完直发）
const voiceConfirm = ref(false)
// 「纯语音（不打开界面）」：语音指令执行完不弹抽屉，直接语音播报结果（用户本地偏好，默认关闭）
const voiceSilent = ref(false)
// 本轮是否由语音发起（用户 2026-09-17 要求：只播报语音指令的结果，打字发的对话不发声）
let voiceRound = false
// 语音指令触发了写操作确认：等用户拍板后再播报真正的执行结果（成功/失败都播）
let voiceConfirmPending = false
// 写操作执行失败后的"自动继续处理"只允许连续触发一次，防止模型反复失败导致无限弹卡。
// 用户下一次主动发消息时复位（见 send()）。
let autoFollowUpUsed = false
// 纯语音模式下的"口头确认"：正在语音问询用户是否执行（此时不打开界面）
let voiceConfirmAsk = false
let voiceConfirmStep = null
// 口头确认：超时重试次数 + "等播报结束后再开等待窗口"的定时器
// （2026-09-18 用户实测：窗口在播报期间就被吃掉 → 用户开口时已过期 → 永远接不上，只能点卡片）
let voiceConfirmRetry = 0
let confirmTimer = null
const VOICE_CONFIRM_WAIT_MS = 30000      // 口头确认等待窗口（比采集器默认 15 秒长：要留出用户反应时间）
const VOICE_CONFIRM_MAX_RETRY = 2        // 超时最多再问 2 次，避免"一次没接上就掉回点卡片"
// 连续"没听清"次数：连续 2 次就收口（《小楷语音交互设计》v1.0 场景 8），不再无休止复问
let voiceMissCount = 0
const VOICE_MISS_LIMIT = 2
// 语音运行参数（等待指令时长）：全局统一、客户界面无入口，取不到就按设计默认 5 秒
let voiceCfgTimer = null

async function loadVoiceRuntimeConfig() {
  try {
    const data = await getVoiceRuntimeConfig()
    const sec = Number(data && data.cmdWaitSec)
    if (!Number.isNaN(sec) && sec > 0) setCmdWaitTimeoutMs(sec * 1000)
  } catch (e) {
    // 取不到不影响语音链路：采集器内部按默认 5 秒运行
  }
}

// ===== 展示与播报偏好（用户自选；模块级共享单例：小助手 ⚙️ 弹窗 与 设置中心「AI 助手设置」共用同一份状态）=====
// 单据标识方式随 agent 请求下发后端（context.displayPrefs），播报字数用于 buildSpeakSummary；
// 下拉切换与提示文案都在 composable（useAiDisplayPrefs）里，本组件只取仍需使用的两项。
const { billLabelStyle, speakLimit } = useAiDisplayPrefs()

// ===== 积分与深度思考（2026-10-24 积分制，照独立版小楷）=====
const creditLoaded = ref(false)
const creditBalance = ref(0)
const creditOwnerType = ref('TENANT')
const creditEnforced = ref(0)
const creditLowBalance = ref(100)
const creditTokensPerCredit = ref(1000)
const creditText = computed(() => {
  const n = Number(creditBalance.value || 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
})
const creditControlled = computed(() => creditOwnerType.value === 'USER' || creditEnforced.value === 1)
const creditExhausted = computed(() => creditLoaded.value && creditControlled.value && Number(creditBalance.value) <= 0)
const creditWarn = computed(() => creditExhausted.value
  || (creditLoaded.value && creditControlled.value
      && Number(creditBalance.value) < Number(creditLowBalance.value || 0)))
const creditTip = computed(() => creditOwnerType.value === 'USER'
  ? '我的个人积分，点击设置中心可查看用量与流水'
  : '公司共享积分，全员共用')
const creditMessage = computed(() => {
  if (creditExhausted.value) return '积分已用完，继续对话前请联系管理员充值'
  if (creditWarn.value) return `积分不足 ${creditLowBalance.value}，建议及时联系管理员充值`
  return ''
})
const loadCredit = async () => {
  try {
    const data = await getMyCreditWallet()
    creditBalance.value = Number(data?.balance || 0)
    creditOwnerType.value = data?.ownerType || 'TENANT'
    creditEnforced.value = Number(data?.enforced || 0)
    creditLowBalance.value = Number(data?.lowBalance || 0)
    creditTokensPerCredit.value = Number(data?.tokensPerCredit || 1000)
    creditLoaded.value = true
  } catch (e) {
    // 积分接口不可用时不打扰用户（对话仍可继续）
  }
}

const DEEP_THINKING_KEY = 'ai_deep_thinking'
const deepThinking = ref(scopedGet(DEEP_THINKING_KEY, '0') === '1')
const deepThinkingTip = computed(() => deepThinking.value
  ? `已开启：本轮按深度思考倍率扣积分，点击关闭`
  : `已关闭：本轮按标准倍率扣积分，开启后按深度倍率扣积分`)
function onToggleDeepThinking() {
  deepThinking.value = !deepThinking.value
  scopedSet(DEEP_THINKING_KEY, deepThinking.value ? '1' : '0')
}

// ===== 思考强度三档 + 上下文窗口三档（2026-11-22 照参考设计，新版界面专用）=====
// 轻=标准直答（标准倍率）；高=深度思考（深度倍率，口径同旧开关"开"）；极高=更长思考空间+更高单轮输出上限（计费同高）。
// 上下文：标准=跟随系统全局默认；加大=512K；最大=1M。按登录人+公司记忆（与旧开关同一套 scoped 记忆）。
const THOUGHT_LEVELS = [
  { value: 'light', label: '轻' },
  { value: 'high', label: '高' },
  { value: 'max', label: '极高' },
]
const CONTEXT_LEVELS = [
  { value: 'standard', label: '128K' },
  { value: 'large', label: '512K' },
  { value: 'max', label: '1M' },
]
const THOUGHT_LEVEL_KEY = 'ai_thought_level'
const CONTEXT_MODE_KEY = 'ai_context_mode'
const THOUGHT_LEVEL_DESC = {
  light: '直接回答，速度快，按标准倍率计费',
  high: '先深度推理再回答，按深度思考倍率计费',
  max: '更长思考空间与更高单轮输出上限，适合复杂长任务；计费同高',
}
const CONTEXT_MODE_DESC = {
  standard: '上下文窗口 128K，适合常规多轮对话',
  large: '上下文窗口 512K，适合多轮长任务',
  max: '上下文窗口 1M；需所选模型支持，超出模型窗口时按模型实际窗口生效',
}
// 旧记忆迁移（适配守则 2.7.2-6，默认值=旧行为）：旧"深度思考"开关开着 → 高；没开过 → 轻
const THOUGHT_LEVEL_INIT = (() => {
  const saved = scopedGet(THOUGHT_LEVEL_KEY, '')
  if (saved === 'light' || saved === 'high' || saved === 'max') return saved
  return scopedGet(DEEP_THINKING_KEY, '0') === '1' ? 'high' : 'light'
})()
const thoughtLevel = ref(THOUGHT_LEVEL_INIT)
watch(thoughtLevel, v => scopedSet(THOUGHT_LEVEL_KEY, v))
const contextMode = ref(scopedGet(CONTEXT_MODE_KEY, '') || 'standard')
watch(contextMode, v => scopedSet(CONTEXT_MODE_KEY, v))
const thoughtLevelDesc = computed(() => THOUGHT_LEVEL_DESC[thoughtLevel.value] || THOUGHT_LEVEL_DESC.light)
const contextModeDesc = computed(() => CONTEXT_MODE_DESC[contextMode.value] || CONTEXT_MODE_DESC.standard)
// 上下文档位 → 本次请求窗口覆盖（2026-11-22 用户拍板：档位直接用数值命名，三档都是明确数值）
const ctxLimitOverride = computed(() =>
  contextMode.value === 'large' ? 512000 : contextMode.value === 'max' ? 1000000 : 128000)
// 新版是否按深度倍率计费（高/极高都按深度倍率，计费口径用户已拍板）
const isDeep = computed(() => thoughtLevel.value !== 'light')
const modelPickerVisible = ref(false)
function pickModel(v) {
  selectedModelKey.value = v
  if (typeof onModelChange === 'function') onModelChange(v)
  modelPickerVisible.value = false
}

// ===== 答复呈现方式（2026-10-22）：明细 = 展示执行过程，汇总 = 只报结果 =====
// 不提供开关：默认明细；对话里明确说"看过程 / 只要结果"时切换，并记住在本机
const ANSWER_MODE_KEY = 'ai_answer_mode'
const answerMode = ref(localStorage.getItem(ANSWER_MODE_KEY) === 'summary' ? 'summary' : 'detail')
function setAnswerMode(mode) {
  answerMode.value = mode === 'summary' ? 'summary' : 'detail'
  try { localStorage.setItem(ANSWER_MODE_KEY, answerMode.value) } catch (e) { /* 隐私模式忽略 */ }
}
/** 只认明确说法，避免把业务问句里的"过程"当成展示指令 */
const SUMMARY_WORDS = [/只(看|要|给|说|报)(一下)?(最终)?结果/, /结果(就行|即可|就好了|给我就行)/,
  /不(用|要)(给我)?(看|展示|显示|说)?(过程|思考)/, /(过程|思考)(就)?不(用|要)/]
const DETAIL_WORDS = [/(看|要|展示|显示|打开|展开)(一下|下)?(执行)?(过程|思考)/,
  /(过程|思考)(给我|放出来|展开|看一下|发出来)/, /(把|将)(执行)?(过程|思考)(放|列|展示|展开)出来/]
function detectAnswerMode(text) {
  const t = String(text || '')
  if (SUMMARY_WORDS.some((re) => re.test(t))) return 'summary'
  if (DETAIL_WORDS.some((re) => re.test(t))) return 'detail'
  return null
}

const voiceConfirmTip = computed(() => voiceConfirm.value
  ? tt('语音内容只填入输入框，你确认后再发送（点标签可切回直发）')
  : tt('语音内容识别到就自动发送；写操作仍有确认卡片兜底（点标签可改为需确认）'))

function onToggleVoiceConfirm() {
  voiceConfirm.value = toggleVoiceConfirm()
  ElMessage.info(voiceConfirm.value
    ? tt('已改为「语音需确认」：说完只填入输入框，确认后发送')
    : tt('已改为「语音直发」：说完自动发送'))
}

const voiceSilentTip = computed(() => voiceSilent.value
  ? tt('已开启：语音指令不打开界面，等待时会语音告知"正在思考/正在查询"，执行完直接播报结果；**答复完 10 秒内可以直接接着说下一句**（不用再喊唤醒词）；写操作改为语音问一句、你答"确认"就直接执行（点标签可关闭）')
  : tt('开启后语音指令**不再打开界面**：等待时语音告知进度，执行完直接播报结果，答复完 10 秒内可以直接接着说下一句；写操作也会语音问一句、你答"确认"就直接执行'))

/** 纯语音必须能"听"到唤醒词 → 打开它时顺带把服务器端唤醒采集启起来（需要用户手势，点标签正好是） */
async function onToggleVoiceSilent() {
  const on = toggleVoiceSilent()
  voiceSilent.value = on
  if (!on) {
    // 关掉纯语音：若当时正在"口头问确认"，一并结束，避免麦克风关掉后还在等一个永远接不上的回答
    resetVoiceConfirmState()
    ElMessage.info(tt('已关闭「纯语音」：语音指令执行前会打开界面'))
    return
  }
  // 「说完只填输入框等你确认」与「不打开界面」互相矛盾 → 开纯语音时自动关掉语音需确认，避免出现死状态
  if (voiceConfirm.value) {
    voiceConfirm.value = toggleVoiceConfirm()
    ElMessage.info(tt('「纯语音」与「语音需确认」不能同时生效，已自动切回「语音直发」'))
  }
  unbindVoiceAutostart()   // 手动开启：给"刷新后自动恢复"用的首次交互监听可以撤掉
  if (!await startVoiceCapture()) {
    voiceSilent.value = false
    toggleVoiceSilent()
    return
  }
  ElMessage.success(tt('「纯语音」已开启：喊「你好，小楷」后再说指令，等待时语音报进度、结果直接播报、不打开界面；答复完 10 秒内可以直接接着说下一句'))
}

/**
 * 唤醒事件处理：
 * 1) 喊"你好，小楷" → 浮球变「🎙️ 聆听中…」+ 语音应答；**不打开界面、不发送任何内容**
 * 2) 说的指令收集完成 → 打开抽屉发送（纯语音模式下**不打开界面**，后台执行 + 语音播报结果）
 * 3) 唤醒后一直没说话（超时）→ 取消聆听状态，浮球恢复常态
 */
function onWakeupEvent(e) {
  const d = (e && e.detail) || {}
  // 协议闸门：未同意前不进入任何语音链路（唤醒 / 指令 / 口头确认全部拦下，先让用户看到协议）
  if (!disclaimerAccepted.value) {
    wakeupListening.value = false
    ensureDisclaimer()
    if (!d.wakeOnly && !d.cancel) {
      speakText(tt('请先阅读并同意小楷助手免责声明，同意后就能用了。'))
    }
    return
  }
  if (d.cancel) {
    wakeupListening.value = false
    // 语音问询超时：**先再问一次**（最多 2 次），仍没接上才显示确认卡交给用户点（不丢单、不静默）
    if (voiceConfirmAsk) {
      if (voiceConfirmRetry < VOICE_CONFIRM_MAX_RETRY) {
        voiceConfirmRetry++
        speakText(tt('还没听到你的确认，请说"确认"或"取消"。'))
        armConfirmWindow()
        return
      }
      voiceConfirmAsk = false
      voiceConfirmStep = null
      clearConfirmWindow()
      visible.value = true
      speakText(tt('没听到你的确认，确认卡片已显示在界面上，请点确认或取消。'))
      return
    }
    // 唤醒失败 / 唤醒后没听到指令：**不许静默**，明确答复并结束本轮（用户 2026-10-21 要求）
    if (d.missed) {
      voiceMissCount++
      if (voiceMissCount >= VOICE_MISS_LIMIT) {
        voiceMissCount = 0
        playCue('stop')                  // 连续两次没听清：结束本次语音对话
        return
      }
      // 三种失败各有明确答复：5 秒没等到指令 → 待命；唤醒词带指令 → 提示分两步；只喊名字 → 没听清
      playCue(d.reason === 'no-command'
        ? 'idle'
        : (d.reason === 'wake-with-command' ? 'wakeCmd' : 'notHeard'))
    }
    return
  }
  if (d.wakeOnly) {
    if (voiceConfirmAsk) return     // 正在等"确认/取消"的回答，不打断问询
    wakeupListening.value = true
    // 语音应答（预置音频，命中即响；用户换了语言/系统音色时自动退回普通播报）
    // 2026-10-25 用户实测"命中后答复等太久"：固定文案不再现场等服务器合成
    playWakeAck(tt('我在，请说。'))
    return
  }
  if (!d.text) return
  // 用户对小助手"要不要执行"的语音回答（纯语音模式，全程不打开界面）
  if (voiceConfirmAsk) { voiceMissCount = 0; onVoiceConfirmAnswer(d.text); return }
  // ==== 语音会话控制（《小楷语音交互设计》v1.0）：结束语 / 取消语一律本地判定，不进模型 ====
  // ① 有待确认的操作时说「不用了/取消」→ 撤掉这次操作，播报"没有做任何改动"；
  // ② 说「没事了/算了/知道了」→ 回「好的。」并结束本次语音对话。
  // 这两种都不调模型、不进会话历史、不扣积分（此前「没事了」会被当普通文本发出去）。
  const pendingStep = findPendingConfirmStep()
  if (pendingStep && isVoiceCancelPhrase(d.text)) {
    voiceMissCount = 0
    wakeupListening.value = false
    voiceRound = false
    voiceConfirmPending = false       // 由下面的固定话术播报，避免 cancelWriteTool 再念一遍
    resetVoiceConfirmState()
    cancelWriteTool(pendingStep)
    playCue('cancelled')
    return
  }
  if (isVoiceEndPhrase(d.text)) {
    voiceMissCount = 0
    wakeupListening.value = false
    voiceRound = false
    resetVoiceConfirmState()
    playCue('bye')
    return
  }
  voiceMissCount = 0
  // 指令已完整收到，标记"本轮来自语音"（执行完播报结果）
  wakeupListening.value = false
  voiceRound = true
  if (voiceSilent.value) {
    // 纯语音：不打开界面，直接在后台走同一条发送链路（对话仍记进消息列表，用户之后打开能看到）
    input.value = d.text
    if (input.value) send()
    return
  }
  visible.value = true
  nextTick(() => {
    input.value = d.text
    if (voiceConfirm.value) {
      // 「语音需确认」模式：只填入输入框，由用户确认后自行发送（不自动发）
      ElMessage.info(tt('语音内容已填入，确认后点发送或按回车'))
      return
    }
    setTimeout(() => { if (input.value) send() }, 300)
  })
}

/** 口头确认的回答：说"确认/提交他/可以"直接执行、说"取消/不要"直接放弃（纯语音模式，不打开界面） */
// 词表口径（2026-09-18 用户实测后放宽）：用户在等确认时说「提交他」「可以」也应算同意 ——
// 原来只认"确认/确定/执行"，导致「提交他」被判成"没听清"，来回问，用户以为没反应。
const VOICE_YES_RE = /(确认|确定|执行|提交|通过|可以|好的|好|是的|是|对|行|ok|yes|就这么办|没问题)/i
const VOICE_NO_RE = /(取消|不用|不要|别|算了|否|不是|不对|不行|不可以|no)/i

function onVoiceConfirmAnswer(text) {
  const step = voiceConfirmStep
  if (!step) { voiceConfirmAsk = false; return }
  const t = String(text || '')
  // 先判"取消"：否则"不要执行"里的"执行"会被当成同意
  if (VOICE_NO_RE.test(t)) {
    voiceConfirmAsk = false
    voiceConfirmStep = null
    clearConfirmWindow()
    cancelWriteTool(step)
    return
  }
  if (VOICE_YES_RE.test(t)) {
    voiceConfirmAsk = false
    voiceConfirmStep = null
    clearConfirmWindow()
    confirmWriteTool(step)
    return
  }
  // 没听清：再问一次（重问同样要"等播报结束再开窗口"，否则又白等）
  speakText(tt('没听清，请说"确认"或"取消"。'))
  armConfirmWindow()
}

/**
 * 判断"只有确认/动作词、没有别的信息"的短句（如"确认""确认执行""提交他""提交这张单""可以"）。
 * 用途：**没有在等确认时**用户说出这种话，先别急着发给 AI（防 AI 重复发起提交、幻觉"已提交"）；
 * 但**是否真的本地兜底还要看小楷上一条回复**（见 lastBotClaimsPendingAction）——
 * 上一条是正常提问时必须放行，否则对话中的「确认」会被吞掉，用户体感"小楷上下文丢了"。
 * （2026-09-18 用户实测：连说三次"确认执行"，每次都触发一轮新提交，最后小楷说"已收到你的确认，提交操作已发起"但啥也没执行）。
 */
const BARE_CONFIRM_A_RE = /(确认|确定|执行|提交|通过|可以|好的|好|行|是的|是|对|ok|yes)/g
// 旧版（121 客户包 VITE_ASSISTANT_UI_V2=false）用改造前的词表：不剥「操作」，使「执行操作」照旧交给 AI 处理
const BARE_CONFIRM_B_LEGACY_RE = /(他|它|这个|这张|这张单|那个|那单|吧|一下|了)/g
// 新版：补「操作」「这张单子」「这个单」——用户实测说「执行操作」时因残留"操作"二字没被剥掉
const BARE_CONFIRM_B_V2_RE = /(他|它|这个|这个单|这张|这张单|这张单子|那个|那单|操作|操作吧|吧|一下|了)/g
const BARE_CONFIRM_B_RE = AI_UI_V2 ? BARE_CONFIRM_B_V2_RE : BARE_CONFIRM_B_LEGACY_RE
function isBareConfirm(text) {
  const t = normalizeText(text)
  if (!t || t.length > 10) return false
  if (!/(确认|确定|执行|提交|通过|可以|好的|好|行|是的|是|对|ok|yes)/.test(t)) return false
  let rest = t.replace(BARE_CONFIRM_A_RE, '').replace(BARE_CONFIRM_B_RE, '')
  return rest === ''
}

/** 取消类短句（无别的内容）：有待确认卡时，等价于点卡片上的「取消」 */
const BARE_CANCEL_RE = /^(取消|取消吧|算了|不用了|不要了|不执行|不了|否|no)$/

/**
 * 找出界面上**最后一张还在等你拍板**的确认卡。
 * 已处置（confirmed/cancelled/failed）与已失效（superseded）都不算。
 *
 * ⚠️ 2026-09-18 用户实测根因：原 tryLocalConfirmOnly 无条件回"没有正在等待确认的操作"，
 * 把用户打的「确认」直接吞掉 —— 明明卡片就在眼前（截图实证：打字说确认毫无反应）。
 */
function findPendingConfirmStep() {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const m = messages.value[i]
    const steps = m && m.agentSteps
    if (!Array.isArray(steps)) continue
    for (let j = steps.length - 1; j >= 0; j--) {
      const s = steps[j]
      if (s.type === 'CONFIRM_NEEDED' && s.confirmToken && !s.confirmStatus) return s
    }
  }
  return null
}

/**
 * 新的待确认卡到达时，把之前所有还没处理的卡标记为「已失效」。
 *
 * ⚠️ 2026-09-18 用户实测：同一个写操作被 AI 反复发起时会并列多张可点的卡
 * （G10002 / G10478 两张都亮着「确认执行」），用户根本不知道该点哪张，点错那张必然失败。
 */
function supersedeOlderConfirmSteps(currentStep) {
  // ⚠️ 必须按 confirmToken 比较，不能用 `s === currentStep`：
  // messages 是响应式数组，push 后读回来的是元素的**代理对象**，引用比较恒为 false
  // → 新卡会把自己也标成"已失效"（实测：卡片只显示"已失效"、按钮全没、打字"确认"永远无卡可执行）
  const currentToken = currentStep && currentStep.confirmToken
  if (!currentToken) return
  for (const m of messages.value) {
    const steps = m && m.agentSteps
    if (!Array.isArray(steps)) continue
    for (const s of steps) {
      if (s.type !== 'CONFIRM_NEEDED' || s.confirmStatus) continue
      if (s.confirmToken === currentToken) continue
      s.confirmStatus = 'superseded'
    }
  }
}

/**
 * 小楷上一条回复是否"声称已经为你生成/执行了操作"（如"已准备好，请在确认卡片中确认"）。
 *
 * 用途：没有待确认卡片时，用户回「确认」是否需要本地兜底，取决于这句话是不是在回应一个**并不存在的操作**：
 * - 上一条声称已生成卡片/已发起/已提交 → 多半是模型没真调工具的"假成功" → 本地兜底（防重复发起、防幻觉"已提交"）
 * - 上一条是正常提问（如"请回复确认""这套结构可以吗"）→ **必须放行给 AI**，否则用户体感"小楷上下文丢了"
 *   （2026-10-06 用户实测：小楷问"请回复确认"后用户回「确认」，被本地吞掉，AI 只看到下一条"开始"）
 */
const BOT_ACTION_CLAIM_RE = /(已准备好|已准备就绪|已生成[^。；\n]{0,8}卡片|待确认卡片已|请在[^。；\n]{0,12}确认卡片|确认卡片[^。；\n]{0,4}点|已发起|已提交|已为你准备)/
function lastBotClaimsPendingAction() {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const m = messages.value[i]
    if (!m || m.role !== 'bot') continue
    return BOT_ACTION_CLAIM_RE.test(String(m.text || ''))
  }
  return false
}

/**
 * 用户只说"确认/取消/提交他"这类话时的本地处理。
 *
 * - **界面上有待确认卡** → 等价于点卡片按钮（本次修复的核心）
 * - **没有待确认卡、且小楷上一条回复声称已生成/执行了操作** → 本地如实回一句，不发给 AI
 *   （否则 AI 会重新发起一遍、甚至幻觉"已提交"）
 * - **没有待确认卡、上一条是正常提问** → 放行给 AI（2026-10-06 修复：不再吞掉对话中的「确认」）
 */
async function tryLocalConfirmOnly(text) {
  if (voiceConfirmAsk) return false          // 正在等口头确认：由 onVoiceConfirmAnswer 处理
  const raw = String(text || '')
  // 1) 新版（8cloud/本地）：有卡时打字说「确认 / 取消」= 点卡片按钮（本次修复的核心）
  //    旧版（121 客户包 VITE_ASSISTANT_UI_V2=false）：保持改造前行为 —— 打字「确认」不执行卡片，
  //    落到底下的兜底话术（点卡片按钮仍然是好的）
  if (AI_UI_V2) {
    const pending = findPendingConfirmStep()
    const isCancel = BARE_CANCEL_RE.test(normalizeText(raw))
    if (pending && (isBareConfirm(text) || isCancel)) {
      const fromVoice = voiceRound
      voiceRound = false
      messages.value.push({ role: 'user', text: raw })
      input.value = ''
      if (isCancel) {
        voiceConfirmPending = false
        await cancelWriteTool(pending)
      } else {
        // 语音发起的轮次要播报执行结果；打字轮次不发声
        voiceConfirmPending = fromVoice
        await confirmWriteTool(pending)
      }
      return true
    }
  }
  // 2) 没卡：仅当"小楷上一条回复声称已生成/执行了操作"时才本地兜底（防重复发起 + 防"假成功"幻觉）；
  //    上一条是正常提问（如"请回复确认""这套结构可以吗"）→ 放行给 AI，否则用户体感"上下文丢失"
  if (!isBareConfirm(text)) return false
  if (!lastBotClaimsPendingAction()) return false
  const fromVoice = voiceRound
  voiceRound = false
  messages.value.push({ role: 'user', text: raw })
  input.value = ''
  const msg = tt('我这边没有正在等待确认的操作。你先说要做什么（例如"提交 SO202609140004 这张销售订单"），我再帮你办。')
  messages.value.push({ role: 'bot', text: `ℹ️ ${msg}` })
  await scrollToBottom()
  if (fromVoice) speakRoundResult(true, msg)
  return true
}

/**
 * 纯语音模式下「需要确认」怎么办（用户 2026-09-18 要求：也别打开界面）：
 * 不弹卡片，改为**语音问一句**，用户口头答"确认/取消"后直接执行。
 * 采集不在跑（麦克风没开）时降级为显示确认卡，保证用户仍能拍板。
 *
 * ⚠️ 2026-09-18 用户实测根因：原实现**问完立刻开 15 秒窗口**，而采集器在播报期丢弃音频，
 * 播报本身还要念好几秒 → 用户听完再开口时窗口已被吃掉/已过期 → 永远接不上，只能点卡片。
 * 现在：**等播报真正结束再开窗口**，窗口 30 秒，超时可再问（最多 2 次）。
 */
function askVoiceConfirm(botMsg) {
  const step = (botMsg.agentSteps || []).find(s => s.type === 'CONFIRM_NEEDED')
  if (!step || !step.confirmToken) return
  if (!isServerWakeupRunning()) {
    speakText(tt('麦克风没有开启，请在界面上点确认或取消。'))
    visible.value = true
    return
  }
  voiceConfirmStep = step
  voiceConfirmAsk = true
  voiceConfirmRetry = 0
  askVoiceConfirmOnce()
}

/** 语音问一次"要执行X，请说确认或取消"（首次问询与超时重问都走这里） */
function askVoiceConfirmOnce() {
  const step = voiceConfirmStep
  if (!step) return
  const summary = buildSpeakSummary(step.content, speakLimit.value)
  speakText(summary
    ? tt('要执行') + summary + tt('，请说"确认"或"取消"。')
    : tt('有一步操作需要你确认，请说"确认"或"取消"。'))
  armConfirmWindow()
}

/** 清掉"等确认窗口"的定时器（已答应 / 已取消 / 组件卸载时调用） */
function clearConfirmWindow() {
  if (confirmTimer) { clearTimeout(confirmTimer); confirmTimer = null }
}

/**
 * 彻底结束"口头确认"状态：用户**已经拍板**（点卡片确认/取消、或点开纯语音开关）时调用。
 * ⚠️ 2026-10-21 用户实测：点了卡片「取消」后小楷还在反复问"请说确认或取消" ——
 *   原因是 cancelWriteTool 只停了定时器，没有复位 voiceConfirmAsk，下一次 tick 又按"仍在等回答"重问。
 *   凡是"已经有人替用户做了决定"的入口，都必须调用本函数，而不是只 clearConfirmWindow()。
 */
function resetVoiceConfirmState() {
  voiceConfirmAsk = false
  voiceConfirmStep = null
  voiceConfirmRetry = 0
  clearConfirmWindow()
}

/** 等播报结束后再武装采集器并开等待窗口（播报期采集被丢弃，早开等于白给） */
function armConfirmWindow() {
  clearConfirmWindow()
  const started = Date.now()
  let sawSpeaking = false
  const tick = () => {
    confirmTimer = null
    if (!voiceConfirmAsk) return
    const speaking = isSpeaking()
    if (speaking) sawSpeaking = true
    const waited = Date.now() - started
    // 最多等 20 秒；始终没检测到播报（如不支持语音合成）则最多等 3 秒，避免死等
    if (waited < 20000 && (speaking || (!sawSpeaking && waited < 3000))) {
      confirmTimer = setTimeout(tick, 200)
      return
    }
    if (!armVoiceCommand(VOICE_CONFIRM_WAIT_MS)) {
      // 采集掉了（麦克风关了）→ 降级为界面确认，不静默卡住
      voiceConfirmAsk = false
      voiceConfirmStep = null
      speakText(tt('麦克风没有开启，请在界面上点确认或取消。'))
      visible.value = true
    }
  }
  confirmTimer = setTimeout(tick, 300)
}

/* ============ 服务器端唤醒（本地语音，由 🚀纯语音 或 🧪标签 开启） ============
 * 目的：喊『小楷助手』就唤醒，用"浏览器采音 + 服务器自建识别"实现（不依赖浏览器厂商识别服务）。
 * 原「自测面板」与其统计记录已整套删除（2026-09-18），这里只负责开关采音、命中时给个界面提示。 */
const swOn = ref(false)

/**
 * 刷新后「纯语音开着、但采音还没跑」的状态：浏览器硬规定麦克风必须由用户手势触发，
 * 所以刷新后要先点一下页面（任意位置）才会自动恢复 —— 标签明确显示"待点击恢复"，
 * 避免用户以为"开关是开的但功能坏了"（2026-09-18 用户反馈）。
 */
const wakeupPending = computed(() => voiceSilent.value && !swOn.value)
const wakeupTagTip = computed(() => wakeupPending.value
  ? tt('语音采集还没启动：点一下页面任意位置就会自动恢复（浏览器规定麦克风必须由你的点击触发）')
  : tt('本地语音开关：开启后喊「你好，小楷」就能唤醒（采音用浏览器、识别用你自己的服务器，不依赖浏览器厂商的识别服务）。开启后可配合 🚀纯语音 实现「不看界面直接出结果」'))

async function toggleServerWakeup () {
  if (swOn.value) {
    await stopServerWakeup()
    swOn.value = false
    ElMessage.info(tt('语音采音已关闭（喊唤醒词不会再响应）'))
    return
  }
  if (!await startVoiceCapture()) return
  ElMessage.success(tt('语音采音已开启：喊「你好，小楷」试试'))
}

/** 开启本地语音采音（幂等）：供 🧪 标签、🚀纯语音、刷新后自动恢复三处复用 */
async function startVoiceCapture () {
  if (swOn.value && isServerWakeupRunning()) return true
  // 开麦前先取一次内部参数（等待指令时长）：保证这一轮唤醒用的就是平台最新值
  await loadVoiceRuntimeConfig()
  const r = await startServerWakeup({
    upload: (blob) => recognizeSpeech(blob),   // 复用既有上传：后端 provider=local 时走自建识别（不需要 Key）
    isSpeaking,                               // 播报期丢弃采集（带估算兜底，见 voiceNotify.isSpeaking）
    onResult: (res) => {
      if (res.hit) ElMessage.success(tt('唤醒命中：') + (res.text || ''))
    }
  })
  if (!r.ok) {
    ElMessage.error(tt('语音采音启动失败：') + (r.error || ''))
    return false
  }
  swOn.value = true
  return true
}

/**
 * 刷新后自动恢复「纯语音」的采音：开麦克风必须先有用户手势（浏览器硬规定，无法在页面加载时直接开）
 * → 记住"本页第一次点击/按键"再启动，避免"刷新一次纯语音就哑了"（用户此前对唤醒开关明确提过这个要求）。
 */
let voiceAutostartHandler = null

function bindVoiceAutostart () {
  if (voiceAutostartHandler || !voiceSilent.value || isServerWakeupRunning()) return
  voiceAutostartHandler = async () => {
    document.removeEventListener('pointerdown', voiceAutostartHandler)
    document.removeEventListener('keydown', voiceAutostartHandler)
    voiceAutostartHandler = null
    if (!voiceSilent.value || isServerWakeupRunning()) return
    await startVoiceCapture()
  }
  document.addEventListener('pointerdown', voiceAutostartHandler, { once: true })
  document.addEventListener('keydown', voiceAutostartHandler, { once: true })
  // 一次说明（每次刷新最多一条）：让用户知道"点一下页面任意位置"就能恢复，不用专门去找小助手卡片
  ElMessage.info(tt('语音采集已就绪：点一下页面任意位置即可恢复（浏览器规定麦克风需由你的点击触发）'))
}

function unbindVoiceAutostart () {
  if (!voiceAutostartHandler) return
  document.removeEventListener('pointerdown', voiceAutostartHandler)
  document.removeEventListener('keydown', voiceAutostartHandler)
  voiceAutostartHandler = null
}

onUnmounted(() => {
  if (isServerWakeupRunning()) stopServerWakeup()
})

/** 答复是否带"失败前缀"（后端错误与前端兜底统一用 ⚠️/❌ 开头） */
function hasWarnPrefix(text) {
  return /^\s*(?:⚠️|⚠|❌)/.test(String(text || ''))
}

/**
 * 语音指令的结果播报（用户 2026-09-17 要求：成功和失败都要播报，只念简短摘要）。
 * 强制发声、不受 🔊 语音提醒开关 / 🔇 本机静音影响 —— 这是用户主动用语音问的，必须听到答复；
 * 客户包（VITE_VOICE_FEATURES=false）直接不发声。
 */
function speakRoundResult(ok, text, isWrite) {
  if (!VOICE_FEATURES_ENABLED) return
  const limit = speakLimit.value > 0 ? speakLimit.value : 40
  const base = buildSpeakSummary(text, limit)
  if (!base) return
  // 摘要本身已带"失败"字样时不再叠加前缀，避免念成"操作失败，操作失败：…"
  const failed = /^(?:操作失败|失败)/.test(base)
  const prefix = ok ? (isWrite ? tt('已完成，') : '') : (failed ? '' : tt('操作失败，'))
  // 前缀也占字数 → 超长时按剩余空间重取正文，保证「整句」不超用户设定字数
  // （用户 2026-09-18：设 60 字就必须真的念到 60 字；原实现此处写死 40，白读了设置）
  const room = limit - prefix.length
  const summary = (prefix && base.length > room)
    ? buildSpeakSummary(text, room > 10 ? room : 10)
    : base
  speakText(prefix + summary)
  armNextVoiceRound()
}

// ===== 纯语音的「进度播报」与「多轮续接」（用户 2026-09-18） =====
// 背景：① 纯语音模式不打开界面，用户看不到"发送中/思考中/查询中"，等待时以为卡住了；
//      ② 一轮结束后采集器已退出"等指令"态 → 第二句直接说没人理 → 用户报"缺少多次语音沟通"。
const VOICE_PROGRESS_DELAY_MS = 1200   // 1.2 秒内出结果就不出声（避免"快答也报进度"变成噪音）
const VOICE_PROGRESS_LONG_MS = 8000    // 等超过 8 秒还没结果：补一句"还在查询中"，别让用户以为断了
const VOICE_PROGRESS_MIN_GAP_MS = 1500 // 两次进度至少隔 1.5 秒（否则"正在思考"刚出口就被"正在查询"打断，听着像连珠炮）
const VOICE_FOLLOWUP_MS = 10000        // 结果播报后的"接着说"窗口：10 秒内可直接说下一句，不必再喊唤醒词

let progressThinkTimer = null   // "正在思考"的延时定时器
let progressLongTimer = null    // "还在查询中"的兜底定时器
let progressDelayTimer = null   // 两次进度太近时的延后补念
let progressStage = ''          // '' | 'think' | 'query' | 'long'：同一阶段只念一次
let progressSaidQuery = false
let lastProgressAt = 0
let speakIdleTimer = null

/** 开始进度播报：只在「纯语音（不打开界面）」下开口 —— 界面打开时用户能看到"思考中/查询中"，不用念 */
function startProgressVoice() {
  clearProgressTimers()
  if (!VOICE_FEATURES_ENABLED || !voiceSilent.value) return
  progressStage = ''
  progressSaidQuery = false
  progressThinkTimer = setTimeout(() => {
    progressThinkTimer = null
    sayProgress(tt('好的，我正在思考。'), 'think')
  }, VOICE_PROGRESS_DELAY_MS)
  progressLongTimer = setTimeout(() => {
    progressLongTimer = null
    sayProgress(tt('还在查询中，请稍等。'), 'long')
  }, VOICE_PROGRESS_LONG_MS)
}

/**
 * 念当前阶段：同阶段只念一次；距上次进度不足 1.5 秒则**延后补念**（结果一到就被 stopProgressVoice 取消）。
 * "正在思考"会被后面的"正在查询数据"取代 —— 永远只念最新进度。
 */
function sayProgress(text, stage) {
  if (!VOICE_FEATURES_ENABLED || !voiceSilent.value) return
  if (progressStage === stage) return
  if (stage === 'long' && progressSaidQuery) return   // 已经报过"正在查询数据"，就不再重复报
  if (stage === 'query') progressSaidQuery = true
  progressStage = stage
  if (progressDelayTimer) { clearTimeout(progressDelayTimer); progressDelayTimer = null }
  const wait = Math.max(0, VOICE_PROGRESS_MIN_GAP_MS - (Date.now() - lastProgressAt))
  if (wait > 0) {
    progressDelayTimer = setTimeout(() => { progressDelayTimer = null; speakProgressNow(text) }, wait)
  } else {
    speakProgressNow(text)
  }
}

function speakProgressNow(text) {
  if (!VOICE_FEATURES_ENABLED || !voiceSilent.value) return
  lastProgressAt = Date.now()
  // 用 speakProgress：若正在念别的内容（含上一轮结果）就丢弃，**绝不掐断**正在念的句子
  // （2026-09-18 用户实测"纯语音吞播报、听不全"→ 结果播报与本函数都改为走 voiceNotify 的播报队列）
  speakProgress(text)
}

function clearProgressTimers() {
  if (progressThinkTimer) { clearTimeout(progressThinkTimer); progressThinkTimer = null }
  if (progressLongTimer) { clearTimeout(progressLongTimer); progressLongTimer = null }
  if (progressDelayTimer) { clearTimeout(progressDelayTimer); progressDelayTimer = null }
}

function stopProgressVoice() {
  clearProgressTimers()
  progressStage = ''
}

/**
 * 本轮结束后重新武装采集器 → 实现「多轮语音沟通」（喊一次唤醒词后可以连续对话）。
 * 必须等**播报真正结束**再武装，否则用户开口的字会被"播报期丢弃采集"吃掉（这正是"第二轮收不到"的一半原因）。
 * 窗口内没说话 → 采集器自己超时派发 cancel，前端把浮球恢复常态（不会一直占着"聆听中"）。
 */
function armNextVoiceRound() {
  if (!VOICE_FEATURES_ENABLED || !voiceSilent.value) return
  if (!isServerWakeupRunning()) return
  if (speakIdleTimer) { clearTimeout(speakIdleTimer); speakIdleTimer = null }
  let waited = 0
  const tick = () => {
    speakIdleTimer = null
    if (!voiceSilent.value || !isServerWakeupRunning()) return
    if (isSpeaking() && waited < 30000) {          // 还在播报：每 200ms 再看一次（最多 30 秒，防死等）
      waited += 200
      speakIdleTimer = setTimeout(tick, 200)
      return
    }
    // 播报结束后再等 300ms 让尾音过去，然后进入"等你说下一句"
    speakIdleTimer = setTimeout(() => {
      speakIdleTimer = null
      if (!voiceSilent.value || !isServerWakeupRunning()) return
      if (armVoiceCommand(VOICE_FOLLOWUP_MS)) wakeupListening.value = true   // 浮球显示"聆听中"，用户知道可以直接说
    }, 300)
  }
  tick()
}

const startWaitTimer = () => {
  stopWaitTimer()
  waitSeconds.value = 0
  waitTimer = setInterval(() => { waitSeconds.value += 1 }, 1000)
}
const stopWaitTimer = () => {
  if (waitTimer) { clearInterval(waitTimer); waitTimer = null }
}
const sessionId = ref('session_' + Date.now())
const currentAgentSteps = ref([])
let agentEventSource = null

// ===== 会话持久化 + 清空（2026-09-18 用户要求）=====
// 背景：sessionId 原为内存值 `session_<ts>`，**刷新页面 / 进出设置中心（该页小助手被 v-if 卸载）即换新** → 上下文直接断
// （2026-08-29 CHATLOG 已登记为"未做的另一个改动点"）。现改为按**用户**持久化到本机：
//   · key 带 userId —— 同一台电脑换账号登录不会串上下文（沿用"全局缓存必须按用户隔离"的既有铁律）
//   · 只存一个会话 id 字符串，不含任何业务数据；localStorage 不可用时退化为内存会话（= 升级前行为）
const SESSION_KEY_PREFIX = 'ai.sessionId.'

function sessionStorageKey() {
  let uid
  try { uid = localStorage.getItem('userId') || '' } catch (e) { uid = '' }
  return SESSION_KEY_PREFIX + (uid || 'anon')
}

function newSessionId() { return 'session_' + Date.now() }

function loadSessionId() {
  try { return localStorage.getItem(sessionStorageKey()) || '' } catch (e) { return '' }
}

function saveSessionId(id) {
  try { localStorage.setItem(sessionStorageKey(), id) } catch (e) { /* 隐私模式：忽略 */ }
}

/** 恢复上次会话（首次/清空后无记录 → 用当前新 id 并落盘） */
function initSessionId() {
  const saved = loadSessionId()
  if (saved) sessionId.value = saved
  else saveSessionId(sessionId.value)
}

/** 新建会话：清屏 + 换新会话（不写库、不改任何业务数据；2026-10-13 起会话记录永久保留，不再超时清空） */
async function onClearConversation() {
  try {
    await ElMessageBox.confirm(
      tt('将开始一个新会话，小楷不再带着当前话题的上下文（不影响任何业务数据，当前会话记录会永久保留）。是否继续？'),
      tt('新建会话'),
      { type: 'warning', confirmButtonText: tt('新建'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return                                     // 用户取消
  }
  try { agentEventSource?.close?.() } catch (e) { /* 忽略 */ }   // 有正在跑的流先断掉，避免清空后又被写回消息
  agentEventSource = null
  resetVoiceConfirmState()
  messages.value = []
  currentAgentSteps.value = []
  sessionId.value = newSessionId()
  saveSessionId(sessionId.value)
  // 新会话欢迎语：与首次打开一致 —— 不能清屏后一片空白（2026-10-13 用户反馈）
  messages.value.push({
    role: 'bot',
    text: '👋 新会话已开始。直接说出您的需求即可：查询、下单、开权限、建报表都可以。写操作会先弹确认卡片。'
  })
  ElMessage.success(tt('新会话已开始，上下文已重新开始'))
}

// ===================== 历史会话（2026-10-22） =====================
// 口径：只列当前登录人自己的会话（后端按登录人过滤）；删除只删得到本人的记录；
// 点开某个会话 = 把会话号接回 + 把消息灌进面板，接着聊仍是同一上下文。
const historyVisible = ref(false)
const historyLoading = ref(false)
const historySessions = ref([])

async function openHistory() {
  historyVisible.value = true
  await loadHistorySessions()
}

async function loadHistorySessions() {
  historyLoading.value = true
  try {
    const resp = await apiChatSessionList()
    const data = resp && resp.data !== undefined ? resp.data : resp
    historySessions.value = Array.isArray(data) ? data : []
  } catch (e) {
    historySessions.value = []
    ElMessage.warning(tt('历史会话加载失败，请稍后重试'))
  } finally {
    historyLoading.value = false
  }
}

function formatSessionTime(v) {
  if (!v) return ''
  const s = String(v).replace('T', ' ').slice(0, 16)
  return s
}

/** 接回某个历史会话：换会话号 + 灌入历史消息（只展示纯文字气泡，富卡片不回放） */
async function switchToSession(s) {
  if (!s || !s.sessionId) return
  try { agentEventSource?.close?.() } catch (e) { /* 忽略 */ }
  agentEventSource = null
  resetVoiceConfirmState()
  historyLoading.value = true
  try {
    const resp = await apiChatSessionMessages(s.sessionId)
    const rows = (resp && resp.data !== undefined ? resp.data : resp) || []
    sessionId.value = s.sessionId
    saveSessionId(s.sessionId)
    currentAgentSteps.value = []
    messages.value = (Array.isArray(rows) ? rows : []).map(r => ({
      role: r.role === 'user' ? 'user' : 'bot',
      text: r.content || ''
    }))
    if (messages.value.length === 0) {
      messages.value.push({ role: 'bot', text: '👋 这个会话还没有内容，直接说出您的需求即可。' })
    }
    historyVisible.value = false
    ElMessage.success(tt('已接回该会话，可以接着聊'))
    nextTick(() => scrollToBottom())
  } catch (e) {
    ElMessage.warning(tt('会话内容加载失败，请稍后重试'))
  } finally {
    historyLoading.value = false
  }
}

/** 删除某个历史会话（只删自己的）；删的正好是当前会话 → 自动开一个新会话 */
async function removeSession(s) {
  if (!s || !s.sessionId) return
  try {
    await ElMessageBox.confirm(
      tt('删除后，这段对话记录将无法恢复。确认删除？'),
      tt('删除会话'),
      { type: 'warning', confirmButtonText: tt('删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await apiChatSessionDelete(s.sessionId)
    historySessions.value = historySessions.value.filter(x => x.sessionId !== s.sessionId)
    ElMessage.success(tt('会话已删除'))
    if (s.sessionId === sessionId.value) {
      messages.value = []
      sessionId.value = newSessionId()
      saveSessionId(sessionId.value)
      messages.value.push({ role: 'bot', text: '👋 已删除上一段对话，这是新的会话。' })
    }
  } catch (e) {
    ElMessage.error(tt('删除失败：只能删除自己的会话'))
  }
}

// 附件相关
const attachments = ref([])
const fileInputRef = ref(null)
const inputWrap = ref(null)
const msgInputRef = ref(null)

// 语音录音相关
const isRecording = ref(false)
const recordTip = ref('正在录音...')
let audioContext = null       // Web Audio 上下文（WAV 采集用）
let analyser = null
let stream = null
let audioSource = null        // 麦克风源节点
let audioProcessor = null     // 采集回调节点
let audioFrames = []          // 采集到的 Float32 PCM 分片
let audioSampleRate = 16000   // 实际采集采样率（WAV 头按真实值写，识别服务会再重采样）

// 模型服务商配置相关（对话模型选择器所需；服务商配置表单的状态与逻辑已随面板抽到 AiAssistantSettingsPanel.vue）
const settingsVisible = ref(false)
const builtinChannels = ref([])
const customProviders = ref([])
const selectedModelKey = ref('')

// 内置渠道兜底优先级（用户 2026-09-18 明确：不再使用通义千问，对话默认走 DeepSeek）。
// 只在"本机没有记忆 / 记忆对应渠道已不可用"时生效；用户在界面上的手动选择始终优先并被长期记住。
const PREFERRED_CHANNELS = ['DEEPSEEK', 'QWEN', 'OLLAMA']

// 模型选择器选项：内置渠道 + 自定义服务商，统一为"服务商 · 模型"
const modelOptionGroups = computed(() => {
  const groups = []
  const builtinOpts = builtinChannels.value.map(c => ({
    value: `BUILTIN::${c.channel}::${c.model || ''}`,
    label: `${c.name} · ${c.model || '未设置模型'}`,
    channel: c.channel,
    providerId: null,
    model: null,
    disabled: !c.hasApiKey
  }))
  if (builtinOpts.length) groups.push({ label: '内置服务商', options: builtinOpts })

  const customOpts = []
  for (const p of customProviders.value) {
    const models = Array.isArray(p.models) ? p.models : []
    const usable = p.enabled !== false && p.hasApiKey
    if (models.length === 0) {
      customOpts.push({
        value: `CUSTOM::${p.id}::`, label: `${p.name} · 未设置模型`,
        channel: 'CUSTOM', providerId: p.id, model: null, disabled: true
      })
    } else {
      for (const m of models) {
        customOpts.push({
          value: `CUSTOM::${p.id}::${m}`, label: `${p.name} · ${m}`,
          channel: 'CUSTOM', providerId: p.id, model: m, disabled: !usable
        })
      }
    }
  }
  if (customOpts.length) groups.push({ label: '自定义服务商', options: customOpts })
  return groups
})
const allModelOptions = computed(() => modelOptionGroups.value.flatMap(g => g.options))
const selectedModel = computed(() => allModelOptions.value.find(o => o.value === selectedModelKey.value) || null)

// 选择模型后记住（刷新后保持，不再回退通义千问）
// 按「用户 + 公司」隔离存：同一台电脑换用户/切公司不得继承别人选的模型
const onModelChange = () => {
  if (selectedModelKey.value) {
    scopedSet('ai.selectedModel', selectedModelKey.value)
  }
}

// 加载模型列表（内置渠道 + 自定义服务商），并恢复上次选择
const loadModels = async () => {
  try {
    const [chs, provs] = await Promise.all([
      apiListChannels(),
      apiListProviders()
    ])
    builtinChannels.value = chs || []
    customProviders.value = provs || []
    const opts = allModelOptions.value
    const saved = scopedGet('ai.selectedModel', '')
    let pick = opts.find(o => o.value === saved && !o.disabled)
    // 没有记忆（新环境/清过缓存）或记忆里的渠道已不可用时：按 PREFERRED_CHANNELS 兜底（DeepSeek 优先），
    // 不再落到列表里的第一家（通义千问）
    if (!pick) {
      for (const ch of PREFERRED_CHANNELS) {
        const hit = opts.find(o => !o.disabled && String(o.value).startsWith(`BUILTIN::${ch}::`))
        if (hit) { pick = hit; break }
      }
    }
    if (!pick) pick = opts.find(o => !o.disabled)
    selectedModelKey.value = pick ? pick.value : ''
    onModelChange()
  } catch (e) {
    console.error('加载模型列表失败', e)
  }
}

const messages = ref([
  { 
    role: 'bot', 
    text: '您好，直接说出您的需求即可：查询、下单、开权限、建报表都可以。写操作会先弹确认卡片。' 
  }
])

const chatArea = ref(null)

// 常用指令（固定一排，不随模块切换 —— 模块快捷选择器已按用户要求移除，给回复区腾出空间）
const fixedQuickCmds = [
  { icon: '📊', text: '查询库存' },
  { icon: '📋', text: '查一下销售订单' },
  { icon: '📝', text: '查一下采购订单' },
  { icon: '💵', text: '查询应收账款' },
  { icon: '💳', text: '查询应付账款' },
  { icon: '👥', text: '帮我查一下所有客户' },
  { icon: '📦', text: '查询所有物料' },
  { icon: '🏭', text: '查生产工单' },
]

/**
 * 查询结果表格的列：隐藏「内部主键」列（如 orderId）。
 *
 * 用户 2026-09-18 反馈：查询结果第一列是系统内部编号（258），既看不懂也不是他界面上的单号。
 * 主键仍然保留在行数据里（点击"单号"列跳详情时取自它），只是不再当列展示。
 * 仅当该实体有业务编码列（codeField，如 orderNo）时才隐藏 —— 否则会失去唯一的点击跳转入口。
 */
const getResultColumns = (data, result, maxCols = 6) => {
  if (!data || data.length === 0) return []
  const pkField = result && result.primaryKeyField
  const codeField = result && result.codeField
  const cols = Object.keys(data[0])
  if (pkField && codeField && codeField !== pkField) {
    return cols.filter(c => c !== pkField).slice(0, maxCols)
  }
  return cols.slice(0, maxCols)
}

// 工具结果表分页：每页10条（step.resultPage 记录该步骤当前页码）
const pagedResult = (step) => {
  const data = step.toolResult?.data || []
  const page = step.resultPage || 1
  return data.slice((page - 1) * 10, page * 10)
}

/** 工具结果折叠行的一句话摘要（默认只显示这一行，点开才看原始表格） */
const obsSummary = (step) => {
  const r = step && step.toolResult
  if (!r) return tt('无结果')
  const n = Array.isArray(r.data) ? r.data.length : 0
  return n > 0 ? `${n} ${tt('条结果')}` : (r.message || tt('无结果'))
}

/** 合并时的业务主键优先级（命中越靠前，越适合作横向合并键） */
const MERGE_KEY_CANDIDATES = ['materialCode', 'orderNo', 'deliveryNo', 'contractNo', 'quoteNo',
  'customerName', 'supplierName', 'materialName', 'code', 'no', 'billNo']

/**
 * 本轮结果表：所有查询结果合并成一张表。
 * - 有共同业务键 → 按共同键横向拼（同物料/同单号并到同一行）
 * - 无共同业务键 → 只展示第一个结果集（用户问的那个主查询），其余见执行过程
 *   （2026-11-13：不再纵向拼接 —— 查采购订单时把订单行和明细行拼成一张表，用户看到的就是"答非所问"）
 */
const mergedResult = (msg) => {
  const sets = Array.isArray(msg?.resultSets)
    ? msg.resultSets.filter(s => s && s.toolResult && Array.isArray(s.toolResult.data) && s.toolResult.data.length)
    : []
  if (!sets.length) return null
  if (msg._mergedFor === sets.length && msg._merged) return msg._merged
  const out = buildMergedTable(sets)
  msg._mergedFor = sets.length
  msg._merged = out
  return out
}

const buildMergedTable = (sets) => {
  if (sets.length === 1) {
    const r = sets[0].toolResult
    return {
      columns: getResultColumns(r.data, r).map(c => ({ prop: c, srcCol: c, setIndex: 0, label: formatLabel(c, r), clickable: isClickableColumn(c, r) })),
      rows: r.data,
      mergeHint: ''
    }
  }
  const keySets = sets.map(s => new Set(Object.keys(s.toolResult.data[0] || {})))
  const common = Object.keys(sets[0].toolResult.data[0] || {}).filter(k => keySets.every(ks => ks.has(k)))
  const key = MERGE_KEY_CANDIDATES.find(k => common.includes(k)) || ''
  const columns = []
  const usedProps = new Set()
  sets.forEach((s, si) => {
    const r = s.toolResult
    getResultColumns(r.data, r).forEach(c => {
      // 合并键只保留一列，避免同一物料编码出现在两组列里
      if (key && c === key && si > 0) return
      let prop = c
      if (usedProps.has(prop)) prop = `${c}_${si + 1}`
      usedProps.add(prop)
      columns.push({ prop, srcCol: c, setIndex: si, label: formatLabel(c, r), clickable: si === 0 && isClickableColumn(c, r) })
    })
  })
  const rows = []
  if (key) {
    const byKey = new Map()
    sets.forEach((s, si) => {
      const r = s.toolResult
      const cols = getResultColumns(r.data, r)
      r.data.forEach(srcRow => {
        const kv = srcRow[key] == null ? '' : String(srcRow[key])
        let row = byKey.get(kv)
        if (!row) { row = {}; byKey.set(kv, row); rows.push(row) }
        if (key && row[key] === undefined) row[key] = srcRow[key]
        cols.forEach(c => {
          const def = columns.find(x => x.setIndex === si && x.srcCol === c)
          if (def) row[def.prop] = srcRow[c]
        })
      })
    })
    return { columns, rows, mergeHint: `${tt('已按')}「${formatLabel(key, sets[0] && sets[0].toolResult)}」${tt('合并')} ${sets.length} ${tt('个查询')}` }
  }
  // 无共同业务键 = 多个互不相关的查询（如采购订单主表 + 采购订单明细）。
  // 纵向拼接会让订单行和明细行混在一张表里（重复列、行数翻倍、看起来"答非所问"）。
  // 2026-11-22 改版（结论进结果表）：不再固定显示第一个结果集——提问类问题的答案
  // 通常由最后一次查询/汇总得出，因此横条显示最后一个结果集，其余结果留在「执行过程」里可展开查看。
  const last = sets[sets.length - 1].toolResult
  return {
    columns: getResultColumns(last.data, last).map(c => ({
      prop: c, srcCol: c, setIndex: 0, label: formatLabel(c, last), clickable: isClickableColumn(c, last)
    })),
    rows: last.data,
    mergeHint: `${tt('其余')} ${sets.length - 1} ${tt('个查询结果见执行过程')}`
  }
}

// 执行过程（思考 / 调用参数 / 工具结果 / 错误）属内部调用，默认折叠，需要时点开；
// 用户默认只看到「结果」（查询结果表、成功/失败卡、最终答复）与「提问」（待确认卡）
const hasProcessSteps = (msg) => Array.isArray(msg?.agentSteps)
  && msg.agentSteps.some(s => s.type === 'THOUGHT' || s.type === 'ACTION' || s.type === 'OBSERVATION' || s.type === 'ERROR')
const actionCount = (msg) => (Array.isArray(msg?.agentSteps) ? msg.agentSteps : []).filter(s => s.type === 'ACTION').length
const errorCount = (msg) => (Array.isArray(msg?.agentSteps) ? msg.agentSteps : []).filter(s => s.type === 'ERROR').length
const errorSteps = (msg) => (Array.isArray(msg?.agentSteps) ? msg.agentSteps : []).filter(s => s.type === 'ERROR')

// ===== 2026-11-22 改版：TRAE 式思考链路时间线 + 结论进结果表（仅新版界面） =====

/** 本轮是否仍在执行（最新一条机器人消息且整体等待中）→ 链路状态行显示"思考中" */
const isMsgRunning = (msg) => loading.value && messages.value.length > 0 && messages.value[messages.value.length - 1] === msg

/** 最新一条机器人消息已有链路步骤 → 等待气泡不再重复显示"思考中"（状态行已有） */
const lastBotHasSteps = () => {
  const last = messages.value[messages.value.length - 1]
  return !!(last && last.role === 'bot' && Array.isArray(last.agentSteps) && last.agentSteps.length > 0)
}

/** 节点展开态挂在步骤对象上（随消息留存，历史消息也可展开） */
const tlIsOpen = (item) => {
  const host = item.action || item.obs || item.step
  return !!(host && host._tlOpen)
}
const toggleTlOpen = (item) => {
  const host = item.action || item.obs || item.step
  if (host) host._tlOpen = !host._tlOpen
}
const toggleThoughtItem = (item) => {
  if (item.step.streaming) return   // 流式中的思考卡片保持实时展开
  toggleTlOpen(item)
}

/**
 * 思考链路时间线：思考一项；一次调用 + 紧随其后的同名结果并为一个工具节点；错误一项。
 * 没等到结果的调用不硬配对，节点显示"执行中"。
 */
const timelineItems = (msg) => {
  const steps = processSteps(msg) || []
  const items = []
  for (let i = 0; i < steps.length; i++) {
    const s = steps[i]
    if (s.type === 'THOUGHT') { items.push({ kind: 'thought', step: s }); continue }
    if (s.type === 'ERROR') { items.push({ kind: 'error', step: s }); continue }
    if (s.type === 'ACTION') {
      let obs = null
      for (let j = i + 1; j < steps.length; j++) {
        const n = steps[j]
        if (n.type === 'OBSERVATION' && n.toolName === s.toolName) { obs = n; i = j; break }
        if (n.type === 'ACTION' || n.type === 'THOUGHT') break   // 调用与结果之间插入的思考不拆散配对
      }
      items.push({ kind: 'tool', action: s, obs })
    } else if (s.type === 'OBSERVATION') {
      items.push({ kind: 'tool', action: null, obs: s })
    }
  }
  return items
}

/** 工具节点结果小字：执行中 / 找到 N 条 / 一句话结果 / 失败 */
const toolResultBrief = (item) => {
  if (!item.obs) return tt('执行中…')
  const r = item.obs.toolResult
  if (!r) return tt('无结果')
  if (r.success === false) return tt('失败') + (r.message ? '：' + r.message : '')
  const n = Array.isArray(r.data) ? r.data.length : 0
  if (n > 0) return `${tt('找到')} ${n} ${tt('条')}`
  return r.message || obsSummary(item.obs)
}
const tlSubClass = (item) => {
  if (!item.obs) return 'tl-sub-run'
  const r = item.obs.toolResult
  if (r && r.success === false) return 'tl-sub-bad'
  return 'tl-sub-ok'
}

/** 工具名 → 业务说法：动词后缀 + 业务对象；对照不到的对象退回原名 */
const TOOL_VERBS = [
  [/(^|_)(list|query|search|get|find)$/, '查询'],
  [/(^|_)(create|add|new|save)$/, '新建'],
  [/(^|_)(update|edit|set|modify|writeBack)$/, '修改'],
  [/(^|_)(delete|remove|del)$/, '删除'],
  [/(^|_)(submit|approve|audit|confirm)$/, '提交/审核'],
  [/(^|_)(void|cancel|reverse)$/, '作废/冲回'],
  [/(^|_)(export|download)$/, '导出'],
  [/(^|_)(import|upload)$/, '导入'],
  [/(^|_)(aggregate|stat|sum|count)$/, '汇总统计'],
  [/(^|_)(compare|diff)$/, '对比'],
  [/(^|_)(open|page|navigate)$/, '打开页面'],
]
const TOOL_ENTITIES = {
  sale_order: '销售订单', sale_delivery: '发货单', sale_contract: '销售合同', sale_quote: '报价单',
  sale_return: '销售退货', sale_invoice: '销售发票', sale_receipt: '收款单',
  purchase_order: '采购订单', purchase_request: '采购申请', purchase_inbound: '采购入库单', purchase_return: '采购退货',
  purchase_invoice: '采购发票', purchase_payment: '付款单', purchase_contract: '采购合同',
  basedata_material: '物料', basedata_customer: '客户', basedata_supplier: '供应商', basedata_employee: '员工',
  basedata_department: '部门', basedata_currency: '币种', basedata_unit: '计量单位', basedata_account: '会计科目',
  basedata_paymentterm: '付款条件', basedata_pricelevel: '价格等级', basedata_settle: '结算方式',
  basedata_materialcategory: '物料分类', basedata_warehouse: '仓库', basedata_stock: '仓库',
  crm_lead: '线索', crm_opportunity: '商机', crm_followup: '跟进记录', crm_complaint: '客诉', crm_survey: '拜访调研',
  produce_order: '生产工单', production_order: '生产工单', produce_mrp: 'MRP运算', produce_aps: 'APS排产',
  inventory_current: '现存量', inventory_stock: '仓库库存', inventory_io: '出入库流水',
  finance_invoice: '发票', finance_receipt: '收款单', finance_payment: '付款单', finance_receivable: '应收单',
  finance_payable: '应付单', finance_expense: '费用单', finance_voucher: '凭证',
  setting_config: '系统参数', setting_user: '用户', setting_role: '角色权限', setting_menu: '菜单',
  custom_report: '自定义报表', ai_usage: 'AI用量', outsource_order: '委外加工单', sample_order: '打样单',
}
const toolLabel = (name) => {
  const n = String(name || '').trim()
  if (!n) return tt('调用工具')
  if (n === 'open_page') return tt('打开页面')
  if (n === 'ext_result_query') return tt('查看已存结果')
  if (n === 'ext_result_aggregate') return tt('汇总统计')
  if (n === 'ext_result_compare') return tt('对比两期结果')
  if (n.startsWith('ext_')) return tt('操作接入系统')
  for (const [re, verb] of TOOL_VERBS) {
    if (re.test(n)) {
      const base = n.replace(re, '').replace(/_+$/, '')
      const ent = TOOL_ENTITIES[base]
      return verb + (ent ? ' · ' + ent : '')
    }
  }
  return tt('调用') + ' ' + n
}

/** 最终答复里的表格 → 结构化结果表（2026-11-22 结论进结果表）。
 *  识别「| 开头连续行 + 第二行 |---| 分隔」的块；解析成功则表格单独渲染成结果表，
 *  文字只保留结论与口径说明，加粗标记一并去掉。 */
const answerParts = (msg) => {
  const text = String(msg?.text || '')
  if (!text.includes('|')) return null
  if (msg._apFor === text.length) return msg._ap
  const lines = text.split('\n')
  let start = -1
  for (let i = 0; i < lines.length - 1; i++) {
    const l = lines[i].trim()
    const sep = (lines[i + 1] || '').trim()
    if (l.startsWith('|') && sep.startsWith('|') && /^[\|:\-\s]+$/.test(sep) && sep.includes('-')) { start = i; break }
  }
  let out = null
  if (start >= 0) {
    let end = start
    while (end + 1 < lines.length && lines[end + 1].trim().startsWith('|')) end++
    const cells = (l) => l.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(c => c.trim().replace(/\*\*/g, ''))
    const seen = {}
    const columns = []
    cells(lines[start]).forEach(c => {
      if (!c) return
      if (seen[c]) { seen[c]++; columns.push(c + ' ' + seen[c]) } else { seen[c] = 1; columns.push(c) }
    })
    const rows = []
    for (let i = start + 2; i <= end; i++) {
      const cs = cells(lines[i])
      if (cs.length === 1 && cs[0] === '') continue
      const row = {}
      columns.forEach((c, ci) => { row[c] = cs[ci] ?? '' })
      rows.push(row)
    }
    if (columns.length && rows.length) {
      out = {
        columns,
        rows,
        before: lines.slice(0, start).map(s => s.replace(/\*\*/g, '').trim()).filter(Boolean),
        after: lines.slice(end + 1).map(s => s.replace(/\*\*/g, '').trim()).filter(Boolean)
      }
    }
  }
  msg._apFor = text.length
  msg._ap = out
  return out
}

/** 本轮用量（耗时 + token 消耗）：后端在流末尾推 USAGE 步骤，渲染在答复末尾（2026-10-13 用户要求） */
const usageStepOf = (msg) => (Array.isArray(msg?.agentSteps) ? msg.agentSteps : []).find(s => s.type === 'USAGE' && s.usage) || null
const formatElapsed = (ms) => {
  const n = Number(ms)
  if (!isFinite(n) || n <= 0) return ''
  return n >= 60000 ? `${Math.floor(n / 60000)}分${Math.round((n % 60000) / 1000)}秒` : `${(n / 1000).toFixed(1)}秒`
}
/** token 数的紧凑写法：1500 → 1.5K，128000 → 128K，1000000 → 1M */
const formatTokenAmount = (n) => {
  const v = Number(n) || 0
  if (v >= 1000000) return `${(v / 1000000).toFixed(1)}M`.replace('.0M', 'M')
  if (v >= 1000) return `${(v / 1000).toFixed(v >= 100000 ? 0 : 1)}K`.replace('.0K', 'K')
  return String(v)
}
/** 上下文使用率文案：上下文 12% 约 15K/128K tokens */
const contextUsageText = (step) => {
  const used = Number(step && step.contextTokens)
  const limit = Number(step && step.contextLimit)
  if (!used || !limit) return ''
  const pct = Math.min(100, Math.max(1, Math.round(used / limit * 100)))
  return `上下文 ${pct}% 约 ${formatTokenAmount(used)}/${formatTokenAmount(limit)} tokens`
}
const usageSummaryText = (msg) => {
  const s = usageStepOf(msg)
  if (!s) return ''
  const u = s.usage || []
  const parts = []
  const elapsed = formatElapsed(s.elapsedMs)
  if (elapsed) parts.push(`耗时 ${elapsed}`)
  // 上下文使用率（2026-11-13）：本次请求开始时的上下文估算 / 设置中心「上下文上限」
  const ctxText = contextUsageText(s)
  if (ctxText) parts.push(ctxText)
  // 积分消耗（2026-10-24）：用户先看积分，token 明细放括号里
  if (s.credits !== null && s.credits !== undefined) {
    const n = Number(s.credits)
    const text = Number.isInteger(n) ? String(n) : n.toFixed(2)
    let creditText = `消耗 ${text} 积分`
    const detail = []
    if (u[2] > 0) detail.push(`${u[2]} tokens`)
    if (u[3] > 0) detail.push(`${u[3]} 次模型调用`)
    if (detail.length) creditText += `（${detail.join(' / ')}）`
    parts.push(creditText)
  } else if (u[2] > 0) {
    let text = `消耗 ${u[2]} tokens`
    const detail = []
    if (u[0] > 0) detail.push(`输入 ${u[0]}`)
    if (u[1] > 0) detail.push(`输出 ${u[1]}`)
    if (u[3] > 0) detail.push(`${u[3]} 次模型调用`)
    if (detail.length) text += `（${detail.join(' / ')}）`
    parts.push(text)
  }
  return parts.join(' · ')
}
// 待确认卡单独成块（始终可见、不折叠）；其余步骤进「执行过程」折叠区。
// 2026-09-18 布局重排：卡片必须在结果之后、折叠区之前，所以两段分开渲染。
const confirmSteps = (msg) => (Array.isArray(msg?.agentSteps) ? msg.agentSteps : []).filter(s => s.type === 'CONFIRM_NEEDED')
const processSteps = (msg) => (Array.isArray(msg?.agentSteps) ? msg.agentSteps : []).filter(s => s.type !== 'CONFIRM_NEEDED' && s.type !== 'USAGE')

// 实体名到路由的映射表（AI助手查询结果点击跳转）
const entityRouteMap = {
  // 基础数据
  Department: { route: '/basedata/department/edit', pk: 'deptId' },
  Employee: { route: '/basedata/employee/edit', pk: 'employeeId' },
  Currency: { route: '/basedata/currency/edit', pk: 'currencyId' },
  UnitOfMeasure: { route: '/basedata/unit/edit', pk: 'unitId' },
  Account: { route: '/basedata/account/edit', pk: 'accountId' },
  PriceLevel: { route: '/basedata/price-level/edit', pk: 'priceLevelId' },
  SettlementMode: { route: '/basedata/settle/edit', pk: 'settleModeId' },
  MaterialCategory: { route: '/basedata/material-category/edit', pk: 'categoryId' },
  Customer: { route: '/basedata/customer/edit', pk: 'customerId' },
  Supplier: { route: '/basedata/supplier/edit', pk: 'supplierId' },
  Material: { route: '/basedata/material/edit', pk: 'materialId' },
  Warehouse: { route: '/basedata/warehouse/edit', pk: 'stockId' },
  // CRM
  Lead: { route: '/crm/lead', pk: 'leadId', inline: true },
  Opportunity: { route: '/crm/opportunity', pk: 'opportunityId', inline: true },
  FollowUp: { route: '/crm/follow-up', pk: 'followUpId', inline: true },
  Complaint: { route: '/crm/complaint', pk: 'complaintId', inline: true },
  Survey: { route: '/crm/survey', pk: 'surveyId', inline: true },
  // 销售
  SaleQuote: { route: '/sale/quotes', pk: 'quoteId', pathId: true },
  SaleOrder: { route: '/sale/orders', pk: 'orderId', pathId: true },
  SaleDelivery: { route: '/sale/deliveries', pk: 'deliveryId', pathId: true },
  SaleReturn: { route: '/sale/returns', pk: 'returnId', pathId: true },
  SaleInvoice: { route: '/sale/invoices', pk: 'invoiceId', pathId: true },
  SaleContract: { route: '/sale/contracts', pk: 'contractId', pathId: true },
  // 采购
  PurchaseRequest: { route: '/purchase/requests', pk: 'requestId', pathId: true },
  PurchaseOrder: { route: '/purchase/orders', pk: 'orderId', pathId: true },
  PurchaseInbound: { route: '/purchase/inbounds', pk: 'receiptId', pathId: true },
  PurchaseReturn: { route: '/purchase/returns', pk: 'returnId', pathId: true },
  PurchaseInvoice: { route: '/purchase/invoices', pk: 'invoiceId', pathId: true },
  PurchaseContract: { route: '/purchase/contracts', pk: 'contractId', pathId: true },
  // 库存
  StockTransfer: { route: '/inventory/stock-transfers', pk: 'transferId', inline: true },
  StockCheck: { route: '/inventory/stock-checks', pk: 'checkId', inline: true },
  MaterialRequisition: { route: '/inventory/material-requisitions', pk: 'requisitionId', inline: true },
  // 生产
  Bom: { route: '/masterdata/bom', pk: 'bomId', pathId: true },
  WorkOrder: { route: '/production/workorders', pk: 'workOrderId', inline: true },
  Routing: { route: '/production/routings', pk: 'routingId', inline: true },
  ProductionReceipt: { route: '/production/receipts', pk: 'receiptId', inline: true },
  // 财务
  CostCalculation: { route: '/finance/cost-calculation', pk: 'calcId', inline: true },
  // 报表
  CustomReport: { route: '/report/custom/run', pk: 'reportId', pathId: true, label: '报表' },
}

// 判断某列是否可点击（编码/单号列）
const isClickableColumn = (col, actionResult) => {
  if (!actionResult || !actionResult.entityName) return false
  const codeField = actionResult.codeField
  const pkField = actionResult.primaryKeyField
  // 主键列或编码列可点击
  return col === codeField || col === pkField
}

// 点击编码列跳转到详情页
const openEntityDetail = (row, actionResult) => {
  const entityName = actionResult.entityName
  const pkField = actionResult.primaryKeyField
  const idValue = row[pkField]
  if (!idValue) {
    ElMessage.warning(tt('未找到记录ID，无法跳转'))
    return
  }
  const config = entityRouteMap[entityName]
  if (!config) {
    ElMessage.info(`${tt("实体 ")}${entityName}${tt(" 暂未配置详情页跳转")}`)
    return
  }
  // 关闭AI助手抽屉
  visible.value = false
  if (config.pathId) {
    // 路由跳转到 /path/:id 形式的编辑页
    router.push(`${config.route}/${idValue}`)
  } else {
    // 跳转到列表页（内联弹窗类型），列表页会自动加载
    router.push(config.route)
    ElMessage.info(`${tt("已跳转到列表页，请点击对应单号查看详情")}`)
  }
}

// ===================== 写操作成功卡：结果信息 + 一键直达 =====================
const createdEntityLabel = (r) => {
  const config = r && r.entityName ? entityRouteMap[r.entityName] : null
  return config && config.label ? config.label : '记录'
}

const hasCreatedRoute = (r) => {
  return !!(r && r.entityName && entityRouteMap[r.entityName] && r.id)
}

const openCreatedEntity = (r) => {
  const config = r.entityName ? entityRouteMap[r.entityName] : null
  if (!config || !r.id) {
    ElMessage.warning(tt('未找到记录ID，无法跳转'))
    return
  }
  visible.value = false
  if (config.pathId) {
    router.push(`${config.route}/${r.id}`)
  } else {
    router.push(config.route)
    ElMessage.info(`${tt("已跳转到列表页，请点击对应单号查看详情")}`)
  }
}

// ===================== 界面导航：说「打开设置中心」就跳过去（2026-09-18） =====================
// 设计（用户确认口径）：页面清单单一来源 = menuConfig.js；后端 open_page 只回"页面名"，
// 由前端解析成路由 + 用 menuRoutePaths 做菜单权限校验（与左侧菜单渲染同源），路由守卫是第二道防线。
const userStore = useUserStore()

// 自定义菜单名 menu_name_<group>_<itemKey>（懒加载一次；组件级缓存，不跨用户共享）
const customMenuNames = ref({})
let menuNamesLoading = null
async function ensureMenuNames() {
  if (Object.keys(customMenuNames.value).length > 0) return
  if (!menuNamesLoading) {
    menuNamesLoading = (async () => {
      try {
        const list = await getSysConfigByGroup('menu_name')
        const map = {}
        if (Array.isArray(list)) {
          list.forEach(it => { if (it && it.configKey && it.configValue) map[it.configKey] = it.configValue })
        }
        customMenuNames.value = map
      } catch (e) {
        customMenuNames.value = {}
      }
    })()
  }
  await menuNamesLoading
  menuNamesLoading = null
}

/** 扁平页面清单（叶子菜单项）：{ names:[默认名,自定义名], label, path, groupKey, groupLabel, settingsGroup } */
function buildPageList() {
  const list = []
  const walk = (items, group, settingsGroup) => {
    (items || []).forEach(item => {
      if (item.children && item.children.length) {
        // 设置中心子分组 itemKey = sub-<group>，其中 <group> 就是 SettingsCenter.vue 的分组 key
        // （menuConfig.js 的 sub-system/sub-business/sub-custom/sub-log ↔ SettingsCenter.vue 的 system/business/custom/log）
        const sub = /^sub-(.+)$/.exec(item.itemKey || '')
        walk(item.children, group, sub ? sub[1] : settingsGroup)
        return
      }
      if (!item.path || !item.label) return
      const custom = customMenuNames.value[getMenuConfigKey(group.key, item.itemKey)]
      list.push({
        names: [item.label, custom].filter(Boolean),
        label: custom || item.label,
        path: item.path,
        groupKey: group.key,
        groupLabel: customMenuNames.value['menu_group_' + group.key] || group.label,
        settingsGroup
      })
    })
  }
  MENU_GROUPS.forEach(g => walk(g.items, g, null))
  return list
}

/** 归一化：去空白/标点、忽略大小写，让「打开设置中心。」这类带标点的说法也能命中 */
const normalizeText = (s) => String(s == null ? '' : s)
  .replace(/[\s，。！？、,.!?：:;"'“”‘’（）()【】[\]]/g, '')
  .toLowerCase()

/**
 * 菜单分组匹配：「基础资料」「销售管理」这类是**菜单分组**、不是页面（没有独立路由）。
 * 用户这么说时不能跳（跳过去只能是空白/404），要回问"你想打开里面哪一项"
 * —— 2026-09-18 用户实测踩到：「打开基础资料」AI 说已打开，前端只能回"没找到"。
 */
function matchGroup(pageName) {
  const target = normalizeText(pageName)
  if (!target) return null
  const pages = buildPageList()
  let hit = null
  for (const g of MENU_GROUPS) {
    const custom = customMenuNames.value['menu_group_' + g.key]
    const label = custom || g.label
    const nm = normalizeText(label)
    if (!nm) continue
    if (nm !== target && !nm.includes(target) && !target.includes(nm)) continue
    if (!hit || nm.length > hit.len) {
      hit = { len: nm.length, key: g.key, label, pages: pages.filter(p => p.groupKey === g.key) }
    }
  }
  return hit
}

/** 未命中页面时的候选：按"与输入的字面重合度"排序（比凭空列前 3 个有用） */
function rankPageCandidates(pageName, pages, limit = 3) {
  const t = normalizeText(pageName)
  if (!t) return []
  const scored = []
  pages.filter(canAccessPage).forEach(p => {
    let score = 0
    p.names.forEach(n => {
      const nm = normalizeText(n)
      if (!nm) return
      if (nm.includes(t)) score += 5
      for (const ch of new Set(t)) {
        if (nm.includes(ch)) score += 1
      }
    })
    if (score > 0) scored.push({ label: p.label, score })
  })
  scored.sort((a, b) => b.score - a.score)
  return scored.slice(0, limit).map(s => s.label)
}

/** 菜单分组名清单（页面没找到又给不出相近候选时的兜底提示） */
function groupLabels(limit = 5) {
  return MENU_GROUPS.map(g => customMenuNames.value['menu_group_' + g.key] || g.label).slice(0, limit)
}

/**
 * 页面名匹配：精确优先，其次互相包含，取最长命中；
 * 同长度出现多个不同页面（如"订单"→销售订单/采购订单、"操作手册"→多个模块）→ 视为有歧义，返回 null 交 AI 追问。
 */
function matchPage(pageName) {
  const target = normalizeText(pageName)
  if (!target) return null
  const exacts = []
  const contains = []
  for (const p of buildPageList()) {
    for (const n of p.names) {
      const nm = normalizeText(n)
      if (!nm) continue
      if (nm === target) exacts.push({ page: p, len: nm.length })
      else if (nm.includes(target) || target.includes(nm)) contains.push({ page: p, len: nm.length })
    }
  }
  const pick = (arr) => {
    if (!arr.length) return null
    const max = Math.max(...arr.map(x => x.len))
    const best = arr.filter(x => x.len === max)
    if (new Set(best.map(x => x.page.path)).size > 1) return null
    return best[0].page
  }
  return pick(exacts) || pick(contains)
}

/** 能否访问：与左侧菜单渲染、设置中心子项可见性同源（menuRoutePaths 精确匹配） */
function canAccessPage(page) {
  if (!page) return false
  const roles = Array.isArray(userStore.roles) ? userStore.roles : []
  if (roles.includes('ADMIN') || roles.includes('SUPER_ADMIN')) return true
  const paths = Array.isArray(userStore.menuRoutePaths) ? userStore.menuRoutePaths : []
  if (paths.includes('*')) return true
  return paths.includes(page.path)
}

/** 生成路由参数：设置中心子项要带 group（SettingsCenter 只在有 group 时才按 item 定位到具体子项） */
function routeOf(page) {
  const idx = page.path.indexOf('?')
  if (idx < 0) return { path: page.path, query: {} }
  const query = {}
  new URLSearchParams(page.path.slice(idx + 1)).forEach((v, k) => { query[k] = v })
  if (query.item && !query.group && page.settingsGroup) query.group = page.settingsGroup
  return { path: page.path.slice(0, idx), query }
}

// 列表页支持的「地址栏筛选键」（与各列表页 applyDrillQuery / 数据看板穿透同一套）；不在表里的一律丢弃
const LIST_QUERY_KEYS = ['status', 'startDate', 'endDate', 'customerId', 'customerLevel', 'materialId', 'productLineId', 'keyword']
/** 只放行列表页认识的筛选键，避免 AI 传的键污染地址栏 */
function pickListQuery(query) {
  const out = {}
  if (!query || typeof query !== 'object') return out
  LIST_QUERY_KEYS.forEach(k => {
    const v = query[k]
    if (v !== undefined && v !== null && String(v).trim() !== '') out[k] = String(v)
  })
  return out
}
/** 把筛选条件翻成人话（用于"已为你打开【销售订单】：状态=草稿…"的提示） */
function describeQuery(q) {
  const cn = {
    status: '状态', startDate: '起始日期', endDate: '结束日期', customerId: '客户',
    customerLevel: '客户等级', materialId: '物料', productLineId: '产品线', keyword: '关键词'
  }
  return Object.entries(q).map(([k, v]) => (cn[k] || k) + '=' + v).join('、')
}

const NO_PERM_TIP = tt('无权限，请联系管理员在 设置中心 → 角色权限设置 中授权')

/**
 * 打开页面。成功 → { ok:true, label }；失败 → { ok:false, reason:'not_found'|'no_permission', message, candidates }
 * opts.recordId/opts.entityType = 打开某张具体单据（复用 entityRouteMap）
 */
async function navigateToPage(pageName, opts = {}) {
  await ensureMenuNames()
  const pages = buildPageList()
  // 1) 打开具体单据：先按实体定位列表页（顺带做该模块的菜单权限校验）
  //    ⚠️ 详情页路径参数必须是**主键（数字 ID）**：传单号详情页查不到数据、整页空白
  //    （2026-09-18 用户实测踩到：/sale/orders/SO2026xxxx 打开后单据内容全空）
  const cfg = opts.entityType ? entityRouteMap[opts.entityType] : null
  if (opts.recordId && cfg) {
    const listPage = pages.find(p => p.path === cfg.route || p.path.startsWith(cfg.route))
    if (listPage && !canAccessPage(listPage)) {
      return { ok: false, reason: 'no_permission', message: `${tt('你没有')}【${listPage.label}】${tt('的访问权限，')}${NO_PERM_TIP}` }
    }
    const id = String(opts.recordId).trim()
    const listLabel = listPage ? listPage.label : (cfg.label || '')
    const isPk = /^\d+$/.test(id)
    // 只有"主键 + 详情在路径上（pathId）"才拼详情路径
    if (cfg.pathId && isPk) {
      visible.value = false
      router.push(`${cfg.route}/${id}`)
      return { ok: true, label: `${listLabel} ${id}`.trim() }
    }
    // 拿到的是单号（或该实体是内联弹窗列表页）→ 只打开列表页，并如实说明（不猜主键、不拼详情路径）
    visible.value = false
    router.push(cfg.route)
    return {
      ok: true,
      label: listLabel || pageName,
      note: isPk ? '' : `${tt('我拿到的是单号')}${id}${tt('，请在列表里按这个单号查一下')}`
    }
  }
  // 2) 打开菜单页面
  const page = matchPage(pageName)
  if (!page) {
    // 2.1 「基础资料 / 销售管理」这类是菜单分组（没有独立页面）→ 回问要打开哪一项，绝不乱跳
    const group = matchGroup(pageName)
    if (group) {
      const children = group.pages.filter(canAccessPage).slice(0, 4).map(p => p.label)
      return {
        ok: false,
        reason: 'group',
        candidates: children,
        message: `【${group.label}】${tt('是一个菜单分组，不是某一个页面')}`
          + (children.length ? `，${tt('你要打开里面哪一项？')}${children.join(' / ')}` : '')
      }
    }
    // 2.2 页面没找到：先按字面重合度给候选，给不出相近的就用菜单分组名兜底
    const near = rankPageCandidates(pageName, pages)
    const tips = near.length ? near : groupLabels()
    return {
      ok: false,
      reason: 'not_found',
      candidates: tips,
      message: `${tt('没找到叫')}【${pageName}】${tt('的页面')}`
        + (tips.length ? `，${tt('是否要找：')}${tips.join(' / ')}？` : '')
    }
  }
  if (!canAccessPage(page)) {
    return { ok: false, reason: 'no_permission', message: `${tt('你没有')}【${page.label}】${tt('的访问权限，')}${NO_PERM_TIP}` }
  }
  visible.value = false
  const target = routeOf(page)
  // AI 传的筛选条件（状态/日期区间/客户…）：只放行列表页认识的键，拼进地址栏由列表页自己应用（数据看板穿透同一机制）
  const extra = pickListQuery(opts.query)
  if (Object.keys(extra).length) target.query = { ...target.query, ...extra }
  router.push(target)
  return { ok: true, label: page.label, note: Object.keys(extra).length ? describeQuery(extra) : '' }
}

// 「打开XX」句式 + 需要剥掉的动词/语气词/水词（本地快匹配用）
const OPEN_PAGE_RE = /(打开|进入|跳转到|跳到|切换到|转到|带我去|我要去|帮我打开|帮我进入)/
const OPEN_PAGE_STRIP_RE = /(打开|进入|跳转到|跳到|切换到|转到|带我去|我要去|帮我|一下|这个|那个|系统里的|页面|界面|列表|页|吧|呢|谢谢)/g

/**
 * 「打开X」之外是否还带了条件（日期/数字/别的实词）？
 * 有 → 返回残余文本（交 AI 处理，AI 能查到具体那张单再打开）；没有 → 返回 ''（本地直接跳）。
 * 2026-09-18 用户实测：「打开销售订单，2026年7月26号的订单。」被本地拦成"只打开列表"、条件被吞，
 * 所以带条件的句子一律不许本地吞掉。
 */
function extraInfo(pageName, page) {
  let rest = normalizeText(pageName)
  const names = page.names.map(normalizeText).filter(n => n && rest.includes(n)).sort((a, b) => b.length - a.length)
  if (names.length) rest = rest.replace(names[0], '')
  // 常见水词不算"信息"
  rest = rest.replace(/(的|了|里|中|上|里面|下面|单据|单子|记录|信息|数据|全部|所有)/g, '')
  return rest
}

/**
 * 本地快匹配（用户确认的双层方案）：说「打开设置中心」时直接跳，不走后端 AI（最快、零 token）。
 * 命中返回 true（已处理）；未命中返回 false，交回 AI 理解（如"我要去改主题颜色"）。
 */
async function tryLocalOpenPage(text) {
  const raw = String(text || '')
  if (!OPEN_PAGE_RE.test(raw) || raw.length > 40) return false
  const pageName = raw.replace(OPEN_PAGE_STRIP_RE, '').trim()
  if (!pageName) return false
  const page = matchPage(pageName)
  if (page) {
    // 除页面名外还带筛选条件 → 交 AI（否则条件被吞掉）
    if (extraInfo(pageName, page)) return false
  } else if (!matchGroup(pageName)) {
    // 既不是页面、也不是菜单分组 → 交回 AI（不猜、不乱跳）
    return false
  }
  const fromVoice = voiceRound
  voiceRound = false
  messages.value.push({ role: 'user', text: raw })
  input.value = ''
  const r = await navigateToPage(pageName)
  messages.value.push({
    role: 'bot',
    text: r.ok ? `✅ ${tt('已为你打开')}【${r.label}】` + (r.note ? `：${r.note}` : '') : `⚠️ ${r.message}`
  })
  await scrollToBottom()
  if (fromVoice) speakRoundResult(r.ok, r.ok ? tt('已为你打开') + r.label : r.message)
  return true
}

const formatLabel = (col, result) => {
  // 自动注册机制（2026-11-22 立项一期）：查询结果自带 labels 字段中文名映射，优先于本地字典——
  // 新字段/派生列（如 超期天数/未收款金额）无需在前端逐个补词条
  if (result && result.labels && result.labels[col]) return result.labels[col]
  const labelMap = {
    // —— 基础资料 ——
    customerCode: '客户编码',
    customerName: '客户名称',
    contactPerson: '联系人',
    contactPhone: '联系电话',
    customerLevel: '等级',
    supplierCode: '供应商编码',
    supplierName: '供应商名称',
    materialCode: '物料编码',
    materialName: '物料名称',
    outQty: '出库数量',
    outAmount: '出库金额',
    outCount: '出库件数',
    inQty: '入库数量',
    inAmount: '入库金额',
    billNos: '出自单据',
    billNo: '单据号',
    model: '规格型号',
    specification: '规格',
    materialType: '物料类型',
    employeeCode: '员工编码',
    employeeName: '员工姓名',
    position: '职位',
    mobilePhone: '手机号',
    status: '状态',
    deptName: '部门名称',
    deptCode: '部门编码',
    parentDeptId: '上级部门',
    stockName: '仓库名称',
    stockCode: '仓库编码',
    currencyCode: '货币编码',
    currencyName: '货币名称',
    exchangeRate: '汇率',
    unitCode: '单位编码',
    unitName: '单位名称',
    accountCode: '账户编码',
    accountName: '账户名称',
    accountType: '账户类型',
    paymentTermCode: '账期编码',
    paymentTermName: '账期名称',
    paymentDays: '账期天数',
    priceLevelCode: '价格等级编码',
    priceLevelName: '价格等级名称',
    settleModeCode: '结算方式编码',
    settleModeName: '结算方式名称',
    categoryCode: '分类编码',
    categoryName: '分类名称',
    // —— 单据通用 ——
    orderNo: '订单号',
    orderDate: '订单日期',
    totalAmount: '金额',
    quantity: '数量',
    deliveryNo: '发货单号',
    deliveryDate: '发货日期',
    returnNo: '退货单号',
    invoiceNo: '发票号',
    quoteNo: '报价单号',
    contractNo: '合同号',
    requestNo: '申请单号',
    requestDate: '申请日期',
    totalQty: '申请数量',
    amount: '金额',
    expenseNo: '费用单号',
    expenseType: '费用类型',
    // —— 库存 ——
    binCode: '库位编码',
    binName: '库位名称',
    warehouseName: '仓库',
    transferNo: '调拨单号',
    fromStockName: '调出仓库',
    toStockName: '调入仓库',
    checkNo: '盘点单号',
    requisitionNo: '领料单号',
    // —— 生产 ——
    bomCode: 'BOM编号',
    bomName: 'BOM名称',
    routingCode: '工艺路线编号',
    routingName: '工艺路线名称',
    receiptNo: '入库单号',
    productionOrderNo: '生产工单号',
    totalQuantity: '入库数量',
    workCenterCode: '工作中心编码',
    workCenterName: '工作中心名称',
    workstationCode: '工位编码',
    workstationName: '工位名称',
    // —— 财务 ——
    arNo: '应收单号',
    apNo: '应付单号',
    // —— 系统 ——
    username: '用户名',
    realName: '姓名',
    roleCode: '角色编码',
    roleName: '角色名称',
    menuCode: '菜单编码',
    menuName: '菜单名称',
    dictTypeCode: '字典类型编码',
    dictTypeName: '字典类型名称',
    // —— CRM ——
    leadNo: '线索编号',
    leadName: '线索名称',
    companyName: '公司名称',
    leadStatus: '状态',
    opportunityNo: '商机编号',
    opportunityName: '商机名称',
    salesStage: '销售阶段',
    probability: '成交概率',
    followUpNo: '跟进编号',
    summary: '跟进摘要',
    followUpType: '跟进类型',
    followUpDate: '跟进时间',
    complaintNo: '投诉编号',
    complaintContent: '投诉内容',
    handleStatus: '处理状态',
    surveyNo: '拜访编号',
    surveyName: '拜访主题',
  }
  return labelMap[col] || col
}

// 打开设置面板（模型服务商管理；面板自身 onMounted 负责加载数据与重置展开态）
const openSettings = () => {
  settingsVisible.value = true
  // 联动：面板内可能增删服务商 / 改密钥，同步刷新对话模型选择器（承接原 saveProvider / deleteProvider 里的 loadModels 职责）
  loadModels()
}

// 以图识图辅助函数
const getAccuracyTagType = (level) => {
  const map = {
    '非常准确': 'success',
    '高': 'success',
    '中等': 'warning',
    '较低': 'info',
    '低': 'danger'
  }
  return map[level] || 'info'
}

const getSimilarityColor = (pct) => {
  if (pct >= 85) return '#10b981'
  if (pct >= 65) return '#3b82f6'
  if (pct >= 45) return '#f59e0b'
  return '#ef4444'
}

const getStatusTagType = (status) => {
  if (!status) return 'info'
  if (status.includes('已生效') || status.includes('完成')) return 'success'
  if (status.includes('待审核') || status.includes('待')) return 'warning'
  if (status.includes('已驳回') || status.includes('取消')) return 'danger'
  return 'info'
}

const openDocDetail = (doc) => {
  if (doc.documentType === '销售订单' && doc.documentId) {
    router.push(`/sale/orders/${doc.documentId}`)
    visible.value = false
  }
}

const send = async () => {
  if (!input.value.trim() && attachments.value.length === 0) return
  // 用户主动发新消息：允许下一轮失败后再次自动继续
  autoFollowUpUsed = false
  // 协议闸门：未同意前不得发送（纯语音模式不打开界面，也必须拦住）
  if (!(await ensureDisclaimer())) {
    if (voiceRound) speakText(tt('请先在界面上阅读并同意小楷助手免责声明，同意后就能用了。'))
    return
  }
  // 界面导航本地快匹配：说「打开设置中心」直接跳，不走后端 AI（省一次调用；语音场景响应最快）
  if (attachments.value.length === 0 && await tryLocalOpenPage(input.value)) return
  // 没有待确认操作时，用户只说「确认/提交他」这类纯确认词 → 本地如实回一句，别让 AI 重新发起一遍
  if (attachments.value.length === 0 && await tryLocalConfirmOnly(input.value)) return
  // 统一走 Agent 工具化链路：
  // 后端旧版对话入口 /api/ai/chat 已停用（erp.ai.gateway.legacy-enabled 默认 false，
  // 调用会直接返回"旧版AI对话入口已停用"），且上传的**非图片文件**只有在 Agent 链路里
  // 才会被真正读取（fileUrl 随请求下发给工具，如 bom_import）。
  await sendAgent(input.value)
}

const sendAgent = async (userMsg, opts = {}) => {
  // 用户这句话里明确说了"看过程 / 只要结果"→ 切换呈现方式并记住（指令优先于记忆）
  const wantedMode = detectAnswerMode(userMsg)
  if (wantedMode) setAnswerMode(wantedMode)
  // 本轮是否由语音唤醒发起（决定执行完是否语音播报结果）：打字/快捷指令发起的不发声
  const fromVoice = voiceRound
  voiceRound = false
  const uploading = attachments.value.filter(a => a.uploading)
  if (uploading.length > 0) {
    ElMessage.info(tt('图片上传中，请稍候...'))
    await new Promise(resolve => {
      const check = setInterval(() => {
        const stillUploading = attachments.value.filter(a => a.uploading)
        if (stillUploading.length === 0) {
          clearInterval(check)
          resolve()
        }
      }, 200)
    })
  }

  const attImages = attachments.value.filter(a => a.type && a.type.startsWith('image/') && a.url)
  // 非图片附件（如 BOM 的 Excel/CSV）也必须带上：Agent 后端靠 fileUrl 才能读文件，
  // 历史 bug：这里只取图片，导致文件被静默丢弃，AI 只能凭空调用 bom_import。
  const attFiles = attachments.value.filter(a => !(a.type && a.type.startsWith('image/')) && a.url)

  // 失败后的自动继续：不把系统内部补全指令当成"用户说的话"显示在对话里，
  // 只展示小楷继续处理的过程与结果（opts.silentUser=true）。
  if (!opts.silentUser) {
    messages.value.push({
      role: 'user',
      text: userMsg,
      images: attImages.map(a => a.url),
      files: attFiles.map(a => ({ name: a.name, url: a.url }))
    })
  }
  input.value = ''
  attachments.value = []
  loading.value = true
  startWaitTimer()
  currentAgentSteps.value = []
  // 纯语音：等待期间用语音报进度（"正在思考 / 正在查询数据"），否则用户不看界面不知道在干什么
  if (fromVoice) startProgressVoice()

  // 2026-11-22：消息对象纳入响应式 —— 步骤推送/链路开合走代理才能触发渲染，
  // 否则"边跑边长出来"（TRAE 式思考链路）在运行中一次渲染都不发生，界面冻结到跑完才刷出来
  const botMsg = reactive({
    role: 'bot',
    text: '',
    agentSteps: [],
    /* 执行过程一律默认收起（用户 2026-11-12 拍板：只默认收起，需要时点开；新版运行中先展开、跑完自动收起） */
    stepsExpanded: false
  })
  messages.value.push(botMsg)
  await scrollToBottom()

  // 看门狗：服务异常时不允许界面永远停在"思考中"（后端没启动 / 流被掐断 / 上游模型卡住都算）。
  // 无数据超过 90 秒，或整轮超过 5 分钟 → 主动断开，并在下面给出明确中文原因。
  const WATCHDOG_IDLE_MS = 90000
  const WATCHDOG_MAX_MS = 300000
  const startedAt = Date.now()
  let lastDataAt = Date.now()
  let watchdogFired = false
  const abortController = new AbortController()
  const watchdogTimer = setInterval(() => {
    if (Date.now() - lastDataAt > WATCHDOG_IDLE_MS || Date.now() - startedAt > WATCHDOG_MAX_MS) {
      watchdogFired = true
      try { abortController.abort() } catch (e) { /* 忽略 */ }
    }
  }, 1000)
  const clearWatchdog = () => { if (watchdogTimer) clearInterval(watchdogTimer) }

  try {
    const imageUrls = attImages.map(a => a.url)
    const payload = {
      userInput: userMsg,
      channel: selectedModel.value?.channel || null,
      providerId: selectedModel.value?.providerId || null,
      model: selectedModel.value?.model || null,
      sessionId: sessionId.value,
      // 深度思考显式开关（2026-10-24）：开启才按深度倍率扣积分。
      // 2026-11-22 三档：新版界面按思考强度（高/极高都按深度倍率，计费同高）+ 上下文档位随请求覆盖；
      // 旧版界面沿用原开关布尔，不传新字段（后端按 deepThinking 布尔兜底，行为与升级前一致）
      deepThinking: AI_UI_V2 ? isDeep.value : !!deepThinking.value,
      thoughtLevel: AI_UI_V2 ? thoughtLevel.value : undefined,
      contextLimit: AI_UI_V2 ? ctxLimitOverride.value : undefined
    }
    if (imageUrls.length > 0) {
      payload.imageUrls = imageUrls
    }
    // 非图片附件随请求一起带上，Agent 后端才能拿到 fileUrl 去读文件（如 BOM 导入）
    if (attFiles.length > 0) {
      payload.files = attFiles.map(a => ({ name: a.name, url: a.url }))
    }
    // 展示口径偏好（单据标识方式）：确认卡片与执行结果文案都按它写 —— 始终带上，
    // 让后端不再把"内部主键 ID"写进卡片和播报（用户 2026-09-18 反馈）
    payload.context = { displayPrefs: { billLabelStyle: billLabelStyle.value }, answerMode: answerMode.value }
    // 带上"刚被提醒的待审批单据"作为上下文：用户说"打开它 / 帮我审批通过"时 AI 能定位到这张单
    // （不传时后端不注入，行为与升级前完全一致）
    try {
      const pending = getLastApprovalContext()
      if (pending && (pending.billType || pending.billNo)) {
        payload.context.pendingApproval = pending
      }
    } catch (e) { /* 上下文缺失不影响主流程 */ }

    const token = localStorage.getItem('token')
    const response = await fetch('/api/ai/chat/agent', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify(payload),
      signal: abortController.signal
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let stopped = false
    // 界面导航：本轮 open_page 要打开的页面（记下来，等本轮流结束再跳，避免把流打断在半路）
    let pendingNavigate = null
    agentEventSource = { close: () => { stopped = true } }

    while (!stopped) {
      const { value, done } = await reader.read()
      lastDataAt = Date.now()   // 收到数据就给看门狗续期
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      
      for (const line of lines) {
        if (!line.trim()) continue
        if (line.startsWith('event:')) continue
        if (line.startsWith('data:')) {
          const data = line.substring(5).trim()
          if (data === '') continue
          try {
            let step = JSON.parse(data)
            // 流式思考增量（2026-10-14）：不单独成行，追加到当前"思考中"占位步骤实时滚动显示，
            // 思考过程在界面上一段段长出来（用户诉求："思考不应该一整个都默认思考中，没有分步骤的吗"）
            if (step.type === 'REASONING_DELTA') {
              let holder = null
              for (let i = botMsg.agentSteps.length - 1; i >= 0; i--) {
                const s = botMsg.agentSteps[i]
                if (s.type === 'THOUGHT' && s.streaming) { holder = s; break }
              }
              if (!holder) {
                holder = { type: 'THOUGHT', content: tt('正在思考…'), reasoning: '', streaming: true }
                botMsg.agentSteps.push(holder)
                currentAgentSteps.value.push(holder)
                // 取代理引用：后续 reasoning 追加走代理才能实时渲染（2026-11-22）
                holder = botMsg.agentSteps[botMsg.agentSteps.length - 1]
              }
              // 2026-11-12：思考流不再自动展开执行过程（用户要求默认收起，避免聊天区被过程占满）
              holder.reasoning = (holder.reasoning || '') + (step.content || '')
              nextTick(() => {
                const nodes = document.querySelectorAll('.agent-steps-list .reasoning-text')
                const last = nodes && nodes[nodes.length - 1]
                if (last) last.scrollTop = last.scrollHeight
              })
              scrollToBottom()
              continue   // 增量事件处理完直接读下一行（不能 return：会跳出整个发送流程、跳过收尾复位）
            }
            // 正式思考步骤到达（模型本轮返回）：移除"思考中"占位（推理增量已在其身上，
            // 正式步骤自带完整推理，二者只留其一），保证执行过程一行一个真实阶段不重复
            if (step.type === 'THOUGHT' && !step.streaming) {
              for (let i = botMsg.agentSteps.length - 1; i >= 0; i--) {
                if (botMsg.agentSteps[i].type === 'THOUGHT' && botMsg.agentSteps[i].streaming) {
                  botMsg.agentSteps.splice(i, 1)
                  break
                }
              }
              for (let i = currentAgentSteps.value.length - 1; i >= 0; i--) {
                if (currentAgentSteps.value[i].type === 'THOUGHT' && currentAgentSteps.value[i].streaming) {
                  currentAgentSteps.value.splice(i, 1)
                  break
                }
              }
            }
            // 工具结果默认收起：只显示一行摘要，用户点这一行才展开原始表格
            if (step.type === 'OBSERVATION') step.obsExpanded = false
            botMsg.agentSteps.push(step)
            currentAgentSteps.value.push(step)
            // 取代理引用：后续改步骤状态走代理才能触发渲染（2026-11-22）
            step = botMsg.agentSteps[botMsg.agentSteps.length - 1]
            // 新版界面：首个步骤到达自动展开链路（TRAE 式边跑边看）；此后用户手动收起不再强行展开
            if (AI_UI_V2 && !botMsg._autoOpened) {
              botMsg._autoOpened = true
              botMsg.stepsExpanded = true
            }
            // 本轮用量返回 → 用服务端扣减后的余额刷新顶部积分胶囊（2026-10-24）
            if (step.type === 'USAGE' && step.creditBalance !== null && step.creditBalance !== undefined) {
              creditBalance.value = Number(step.creditBalance)
              creditOwnerType.value = step.creditOwnerType || creditOwnerType.value
              creditLoaded.value = true
            }
            // 新的待确认卡到达 → 之前没处理的旧卡全部标记失效（否则并列多张可点的卡，用户不知道该点哪张）
            // 旧版（121 客户包）不做这个标记，保持改造前行为
            if (AI_UI_V2 && step.type === 'CONFIRM_NEEDED') supersedeOlderConfirmSteps(step)
            // 查询结果收集：本轮所有带数据的工具结果都收进来，最后合并成一张「结果表」
            if (step.type === 'OBSERVATION' && step.toolResult && step.toolResult.success
                && Array.isArray(step.toolResult.data) && step.toolResult.data.length > 0) {
              if (!Array.isArray(botMsg.resultSets)) botMsg.resultSets = []
              const dup = botMsg.resultSets.some(s => s.toolName === step.toolName
                && JSON.stringify(s.toolResult.data) === JSON.stringify(step.toolResult.data))
              if (!dup) botMsg.resultSets.push({ toolName: step.toolName, toolResult: step.toolResult })
              botMsg.mainResult = step.toolResult
            }
            // 界面导航：open_page 回传的 navigate 先记下，等流结束再执行跳转
            if (step.type === 'OBSERVATION' && step.toolName === 'open_page'
                && step.toolResult && step.toolResult.success && step.toolResult.navigate) {
              pendingNavigate = step.toolResult.navigate
            }
            // 走到"调工具"这一步 = 正在查数据（纯语音下用语音告知，避免用户以为卡住）
            if (step.type === 'ACTION') sayProgress(tt('正在查询数据。'), 'query')
            if (step.type === 'FINAL_ANSWER') {
              botMsg.text = step.content
            }
            if (step.type === 'ERROR') {
              if (!botMsg.text) {
                botMsg.text = '⚠️ ' + step.content
              }
            }
            scrollToBottom()
          } catch (e) {
            console.error('解析Agent步骤失败:', e, data)
          }
        }
      }
    }
    // 流结束：若无最终答复且未弹确认卡片，给出明确兜底提示（避免空白/永远"思考中"）
    const hasConfirmCard = botMsg.agentSteps.some(s => s.type === 'CONFIRM_NEEDED')
    if (!botMsg.text && !hasConfirmCard) {
      botMsg.text = '⚠️ AI未返回最终答复（连接可能中断或超时），请重试或简化问题。'
    }
    stopWaitTimer()
    stopProgressVoice()   // 结果已经拿到，进度播报到此为止（下面播真正结果）
    loading.value = false
    // 新版界面：跑完整条链路自动收起（过程看过一眼就收，结果表与答复留在眼前）
    if (AI_UI_V2) botMsg.stepsExpanded = false
    // 语音指令：执行完必须播报结果（成功/失败都播）。
    // 弹了待确认卡时"还没执行"，先不播报，等用户拍板后播真正的执行结果。
    if (fromVoice) {
      if (hasConfirmCard) {
        voiceConfirmPending = true
        // 纯语音：确认也不打开界面 —— 语音问一句，用户口头答"确认/取消"后直接执行
        if (voiceSilent.value) askVoiceConfirm(botMsg)
      } else {
        speakRoundResult(!hasWarnPrefix(botMsg.text), botMsg.text)
      }
    }
    // 界面导航：AI 调了 open_page → 本轮流已结束，现在真正跳转；
    // 跳不过去（页面没找到 / 无权限）时补一条明确提示，不让用户停在原地等
    if (pendingNavigate) {
      const nav = pendingNavigate
      pendingNavigate = null
      const r = await navigateToPage(nav.page, { recordId: nav.recordId, entityType: nav.entityType, query: nav.query })
      if (!r.ok) {
        messages.value.push({ role: 'bot', text: `⚠️ ${r.message}` })
        await scrollToBottom()
      } else if (r.note) {
        // 拿到的是单号（不是主键）→ 只打开了列表页，如实说明，避免"以为打开了、进去却是空白"
        messages.value.push({ role: 'bot', text: `${tt('ℹ️ 已为你打开')}【${r.label}】：${r.note}` })
        await scrollToBottom()
      }
    }

    clearWatchdog()
  } catch (e) {
    clearWatchdog()
    stopWaitTimer()
    stopProgressVoice()
    // 出错也必须收起"思考中"气泡（历史 bug：这里漏了 loading=false，
    // 后端没启动/请求失败时错误文字已经写进气泡，但"思考中…"会永远留在界面上）
    loading.value = false
    // 冻结残留的"思考中"占位步骤（思考增量已在占位身上，冻结后用户可展开回看）
    ;(botMsg.agentSteps || []).forEach(s => { if (s.type === 'THOUGHT' && s.streaming) s.streaming = false })
    console.error('Agent模式发送失败:', e)
    let errorMsg = '服务异常，请稍后重试'
    if (watchdogFired) {
      // 看门狗主动断开，AbortError 的英文原文对用户没有意义，换成能读懂的原因 + 下一步
      errorMsg = 'AI服务长时间没有响应（超过 90 秒无返回）。请确认后端服务已启动、网络正常后重试。'
    } else if (e && e.message) {
      errorMsg = e.message
    }
    botMsg.text = '⚠️ ' + errorMsg
    if (fromVoice) speakRoundResult(false, errorMsg)
  }
}

const sendQuick = (text) => {
  input.value = text
  send()
}

// ===================== 写操作两段式确认（token，无需手输验证码） =====================
const confirmWriteTool = async (step) => {
  if (!step.confirmToken || step.confirming) return
  step.confirming = true
  resetVoiceConfirmState()   // 已拍板：停掉"等口头确认"的窗口（必须连状态一起复位，见函数注释）
  try {
    const data = await api.post('/ai/chat/confirm-tool', { token: step.confirmToken })
    // 多步骤任务：只要返回了 taskId 就说明任务已经跑过（部分完成/失败也走任务卡展示，不当作"确认失败"）
    if (data && (data.success || data.taskId)) {
      step.confirmStatus = 'confirmed'
      // 任务卡自带逐步骤结果，这里不再重复整段文字
      const detail = data.taskId ? '' : (data.message || data.summary || data.orderNo || '')
      // 部分成功（有失败行/未执行步骤）不能用"✅ 已执行"掩盖，必须显式提示
      const partial = data.taskId
        ? data.status !== '完成'
        : (data.partial === true || (data.failCount || 0) > 0)
      const taskText = data.taskId
        ? (data.status === '完成' ? '✅ 任务已完成'
          : ((data.successCount || 0) > 0 ? '⚠️ 任务部分完成' : '❌ 任务执行失败'))
        : (partial ? '⚠️ 操作部分完成' : '✅ 操作已执行')
      // 部分失败时后端会带「继续处理」指引：文案上先说明"还没做完，我接着补"，
      // 避免用户看到"已执行"却不知道还差一半（2026-10-28 用户实测）
      const partialFollowUp = data && data.followUp && data.followUp.prompt
      messages.value.push({
        role: 'bot',
        text: taskText + (detail ? '：' + detail : '') + (partialFollowUp ? '，我继续把没成功的补上：' : ''),
        actionResult: data
      })
      await scrollToBottom()
      // 语音指令触发的动作：执行完播报结果（"部分完成"不谎报成"已完成"）
      if (voiceConfirmPending) {
        voiceConfirmPending = false
        if (partial) speakRoundResult(true, tt('操作部分完成') + (detail ? '：' + detail : ''))
        else speakRoundResult(true, detail, true)
      }
      // 部分失败 → 自动接着补（只连续自动继续一次）
      if (partialFollowUp && !autoFollowUpUsed && !loading.value) {
        autoFollowUpUsed = true
        await sendAgent(partialFollowUp, { silentUser: true })
      }
    } else {
      // 工具执行失败（令牌无效/权限拒绝/导入未成功等），卡片置为失败态，并把失败明细一起带出来
      step.confirmStatus = 'failed'
      step.confirmMessage = (data && data.message) || '未知错误'
      const followUpPrompt = data && data.followUp && data.followUp.prompt
      messages.value.push({
        role: 'bot',
        // 有 actionResult 时由下方失败卡片统一展示，避免同一句话出现两遍
        text: followUpPrompt
          ? '⚠️ 这次没有成功，我继续处理：'
          : (data ? '' : '❌ 操作失败：' + step.confirmMessage),
        actionResult: data || null
      })
      await scrollToBottom()
      if (voiceConfirmPending) {
        voiceConfirmPending = false
        speakRoundResult(false, step.confirmMessage)
      }
      // 失败不是终点：后端明确给出「继续处理」指引时，自动让小楷接着读系统现状、
      // 补齐分类/物料并重新发起创建（只连续自动继续一次，避免无限弹卡）。
      if (followUpPrompt && !autoFollowUpUsed && !loading.value) {
        autoFollowUpUsed = true
        await sendAgent(followUpPrompt, { silentUser: true })
      }
    }
  } catch (e) {
    step.confirmStatus = 'failed'
    step.confirmMessage = (e && e.message) || '网络异常，请重新发起操作'
    // 拦截器已统一提示，此处不再弹窗
    if (voiceConfirmPending) {
      voiceConfirmPending = false
      speakRoundResult(false, step.confirmMessage)
    }
  } finally {
    step.confirming = false
  }
}

// ===================== 多步骤任务卡（重跑失败步骤 / 撤销本次新建） =====================
const taskStatusClass = (status) => {
  const s = String(status || '')
  if (s === '成功' || s === '完成' || s === '已撤销') return 'ok'
  if (s === '失败') return 'bad'
  if (s === '执行中') return 'running'
  return 'pending'
}
// 任务已撤销/部分撤销后不再提供重跑/撤销入口，避免重复撤销
const taskClosed = (task) => ['已撤销', '部分撤销', '撤销未执行'].includes(String((task && task.status) || ''))
const hasFailedSteps = (task) => !taskClosed(task) && (task && task.steps ? task.steps : []).some(s => s.status === '失败' || s.status === '未执行')
const hasCreatedSteps = (task) => !taskClosed(task) && (task && task.steps ? task.steps : []).some(s => s.status === '成功' && String(s.tool || '').startsWith('create_'))

const retryTask = async (msg) => {
  const task = msg && msg.actionResult
  if (!task || !task.taskId || msg.taskRetrying) return
  msg.taskRetrying = true
  try {
    const data = await retryAiTaskFailed(task.taskId)
    msg.actionResult = data
    msg.text = data.status === '完成' ? '✅ 任务已完成' : ((data.successCount || 0) > 0 ? '⚠️ 任务部分完成' : '❌ 任务执行失败')
    messages.value.push({ role: 'bot', text: data.message || '已重跑失败步骤' })
    await scrollToBottom()
  } catch (e) {
    messages.value.push({ role: 'bot', text: '重跑失败：' + ((e && e.message) || '未知原因') })
    await scrollToBottom()
  } finally {
    msg.taskRetrying = false
  }
}

const undoTask = async (msg) => {
  const task = msg && msg.actionResult
  if (!task || !task.taskId || msg.taskUndoing) return
  try {
    await ElMessageBox.confirm(
      '将按创建的反向逐条撤销本次任务新建的数据；被业务数据引用的记录不会删除，撤销会立即停止。是否继续？',
      '撤销本次新建',
      { confirmButtonText: '确认撤销', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  msg.taskUndoing = true
  try {
    const data = await undoAiTask(task.taskId)
    msg.actionResult = data
    msg.text = data.success ? '✅ 已撤销本次新建' : '⚠️ 撤销已停止'
    messages.value.push({ role: 'bot', text: data.message || '已撤销本次新建' })
    await scrollToBottom()
  } catch (e) {
    messages.value.push({ role: 'bot', text: '撤销未完成：' + ((e && e.message) || '未知原因') })
    await scrollToBottom()
  } finally {
    msg.taskUndoing = false
  }
}

const cancelWriteTool = async (step) => {
  resetVoiceConfirmState()   // 已取消：连"等口头确认"的状态一起复位，否则会反复追问"请说确认或取消"
  try {
    await api.post('/ai/chat/cancel-tool', { token: step.confirmToken })
  } catch (e) { /* 取消失败不阻塞 UI，后端5分钟自动过期 */ }
  step.confirmStatus = 'cancelled'
  const wasVoiced = voiceConfirmPending   // 本轮由语音发起：取消也要说一声，不能静默结束
  voiceConfirmPending = false
  messages.value.push({ role: 'bot', text: '已取消该操作，未做任何数据变更' })
  await scrollToBottom()
  if (wasVoiced) speakRoundResult(true, tt('已取消该操作，未做任何数据变更'))
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatArea.value) chatArea.value.scrollTop = chatArea.value.scrollHeight
  // 二次兜底：确认卡/任务卡/图片在下一帧才撑开高度，一次性滚动会差几十像素，
  // 表现为最新卡片的确认按钮被裁在可视区外、第一次点击点不到。
  requestAnimationFrame(() => {
    if (chatArea.value) chatArea.value.scrollTop = chatArea.value.scrollHeight
    setTimeout(() => {
      if (chatArea.value) chatArea.value.scrollTop = chatArea.value.scrollHeight
    }, 150)
  })
}

// ===================== 附件上传相关 =====================
const triggerFileSelect = () => {
  if (fileInputRef.value) fileInputRef.value.click()
}

const handleFileSelect = (e) => {
  const files = e.target.files
  if (files && files.length > 0) {
    addAttachments(Array.from(files))
  }
  e.target.value = ''
}

const handlePaste = (e) => {
  // 同一个 paste 事件会被两条链路各接一次（textarea 捕获监听 + 外层 div 冒泡），
  // 用事件对象打标去重，避免同一个文件被添加两份
  if (e.__aiPasteHandled) return
  e.__aiPasteHandled = true
  const cd = e.clipboardData
  if (!cd) return
  const files = []
  // 1) 标准做法：clipboardData.items（截图粘贴、部分浏览器复制文件）
  const items = cd.items
  if (items && items.length > 0) {
    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      if (item.kind === 'file') {
        const file = item.getAsFile()
        if (file) files.push(file)
      }
    }
  }
  // 2) 兜底：clipboardData.files（从资源管理器复制文件时部分浏览器只有 files）
  if (files.length === 0 && cd.files && cd.files.length > 0) {
    files.push(...Array.from(cd.files))
  }

  if (files.length > 0) {
    e.preventDefault()
    addAttachments(files)
  }
}

// ===================== 拖拽文件到输入框 =====================
// 边界：只有拖入文件才接管；拖文本/链接不拦截，保持输入框原生拖拽文本行为；空拖拽忽略
const isDragOver = ref(false)

const dragHasFiles = (e) => {
  const dt = e && e.dataTransfer
  return !!dt && Array.from(dt.types || []).includes('Files')
}

const onDragOver = (e) => {
  if (!dragHasFiles(e)) return
  e.preventDefault()
  e.dataTransfer.dropEffect = 'copy'
  isDragOver.value = true
}

const onDragLeave = (e) => {
  // 在输入框内部子元素之间移动也会触发 dragleave，此时不取消高亮
  if (e.currentTarget && e.relatedTarget && e.currentTarget.contains(e.relatedTarget)) return
  isDragOver.value = false
}

const handleDrop = (e) => {
  isDragOver.value = false
  if (!dragHasFiles(e)) return
  e.preventDefault()
  e.stopPropagation()
  const files = e.dataTransfer && e.dataTransfer.files
  if (files && files.length > 0) addAttachments(Array.from(files))
}

const addAttachments = async (files) => {
  for (const file of files) {
    const isImage = file.type.startsWith('image/')
    const att = {
      name: file.name || (isImage ? 'pasted-image.png' : 'pasted-file'),
      type: file.type || 'application/octet-stream',
      size: file.size,
      file: file,
      preview: isImage ? URL.createObjectURL(file) : null,
      uploading: false,
      uploaded: false,
      url: null
    }
    attachments.value.push(att)
    
    att.uploading = true
    try {
      const url = await uploadFile(file)
      att.url = url
      att.uploaded = true
      att.uploading = false
    } catch (err) {
      att.uploading = false
      ElMessage.error(tt('上传失败: ') + (err.message || err))
      const idx = attachments.value.indexOf(att)
      if (idx > -1) setTimeout(() => removeAttachment(idx), 1500)
    }
  }
}

const uploadFile = (file) => {
  return new Promise((resolve, reject) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('type', 'ai-chat')
    
    const xhr = new XMLHttpRequest()
    xhr.open('POST', '/api/files/upload')
    const token = localStorage.getItem('token') || localStorage.getItem('accessToken')
    if (token) {
      xhr.setRequestHeader('Authorization', 'Bearer ' + token)
    }
    xhr.onload = () => {
      try {
        const res = JSON.parse(xhr.responseText)
        if (res.code === 200) {
          resolve(res.data)
        } else {
          reject(new Error(res.message || '上传失败'))
        }
      } catch (e) {
        reject(e)
      }
    }
    xhr.onerror = () => reject(new Error('网络错误'))
    xhr.send(formData)
  })
}

const removeAttachment = (idx) => {
  const att = attachments.value[idx]
  if (att && att.preview) {
    URL.revokeObjectURL(att.preview)
  }
  attachments.value.splice(idx, 1)
}

// ===================== 语音识别相关 =====================
// 识别统一走「录音 + 服务器自建 ASR」（/api/ai/speech/recognize，provider=local 时不需要任何 Key）。
// 浏览器自带的 SpeechRecognition 那套已按用户要求整体删除（云端国内连不通、设备端语音包网页无权安装）。
const toggleRecord = async () => {
  if (isRecording.value) stopRecord()
  else startRecord()
}

const startRecord = async () => {
  await startCloudRecord()
}

/**
 * 录音链路：用 Web Audio 直接采集 PCM → 编码成「16k 单声道 WAV」上传。
 * 为什么不用 MediaRecorder(webm/opus)：① 识别服务吃 WAV，**前端直接产 WAV 最准**（无损 PCM，无中间有损编码、
 * 也没有 webm 容器时间戳问题）；② 服务端不必装 ffmpeg，少一个组件与故障点。
 */
const startCloudRecord = async () => {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    ElMessage.warning(tt('当前浏览器不支持录音功能'))
    return
  }
  const Ctx = window.AudioContext || window.webkitAudioContext
  if (!Ctx) {
    ElMessage.warning(tt('当前浏览器不支持音频采集（Web Audio），无法录音'))
    return
  }
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true }
    })
    // 优先让浏览器直接以 16k 采集（内部高质量重采样）；个别浏览器不支持时退回设备默认采样率
    let ctx
    try { ctx = new Ctx({ sampleRate: 16000 }) } catch (e) { ctx = new Ctx() }
    if (ctx.state === 'suspended') { try { await ctx.resume() } catch (e) { /* 忽略 */ } }
    audioContext = ctx
    audioSampleRate = ctx.sampleRate || 16000
    audioFrames = []
    const source = ctx.createMediaStreamSource(stream)
    const processor = ctx.createScriptProcessor(4096, 1, 1)
    processor.onaudioprocess = (e) => {
      // 只采不播：不往输出写数据，避免回声
      audioFrames.push(new Float32Array(e.inputBuffer.getChannelData(0)))
    }
    source.connect(processor)
    processor.connect(ctx.destination)   // Chrome 系需连到 destination 才会持续回调
    audioSource = source
    audioProcessor = processor
    isRecording.value = true
    recordTip.value = '正在录音，点击结束...'
  } catch (err) {
    ElMessage.error(tt('无法访问麦克风: ') + (err.message || err))
  }
}

const stopRecord = () => {
  isRecording.value = false
  recordTip.value = '识别中...'
  try { if (audioProcessor) { audioProcessor.disconnect(); audioProcessor.onaudioprocess = null } } catch (e) { /* 忽略 */ }
  try { if (audioSource) audioSource.disconnect() } catch (e) { /* 忽略 */ }
  if (stream) {
    stream.getTracks().forEach(track => track.stop())
    stream = null
  }
  const ctx = audioContext
  audioContext = null
  audioSource = null
  audioProcessor = null
  if (ctx) { try { ctx.close() } catch (e) { /* 忽略 */ } }
  handleRecordStop()
}

/* WAV 编码已抽到 @/utils/wavEncode.js（前端统一一份实现，避免多处重复） */

const handleRecordStop = async () => {
  const frames = audioFrames
  audioFrames = []
  const total = frames.reduce((n, f) => n + f.length, 0)
  if (total === 0) {
    recordTip.value = '正在录音...'
    ElMessage.warning(tt('未录到声音'))
    return
  }
  const pcm = new Float32Array(total)
  let offset = 0
  for (const f of frames) { pcm.set(f, offset); offset += f.length }
  const blob = new Blob([encodeWav(pcm, audioSampleRate || 16000)], { type: 'audio/wav' })

  try {
    const text = await recognizeSpeech(blob)
    if (text) {
      input.value += (input.value ? ' ' : '') + text
      voiceRound = true   // 🎤 说出的指令与"喊唤醒"同等对待：发送后执行完也语音播报结果
      ElMessage.success(tt('语音识别成功（发送后执行完会语音播报结果）'))
    } else {
      ElMessage.warning(tt('未识别到有效内容'))
    }
  } catch (err) {
    const msg = String((err && err.message) || err || '')
    // 云端识别的 Key 失效/未配置：把一长串 API 原文换成人能看懂的一句话 + 下一步动作
    if (/invalid_api_key|Incorrect API key|401/.test(msg)) {
      ElMessage.error(tt('云端语音识别不可用：通义千问 API Key 未配置或已失效。请到【小助手右上角 ⚙️ 设置 → 内置服务商「通义千问」→ 配置】填入阿里云百炼（sk- 开头）的 Key，或把识别方式切换为「服务器自建识别」（不需要 Key）'))
    } else {
      ElMessage.error(tt('语音识别失败: ') + msg)
    }
  } finally {
    recordTip.value = '正在录音...'
  }
}

const recognizeSpeech = (blob) => {
  return new Promise((resolve, reject) => {
    const formData = new FormData()
    formData.append('file', blob, 'recording.wav')
    
    const xhr = new XMLHttpRequest()
    xhr.open('POST', '/api/ai/speech/recognize')
    const token = localStorage.getItem('token') || localStorage.getItem('accessToken')
    if (token) {
      xhr.setRequestHeader('Authorization', 'Bearer ' + token)
    }
    xhr.onload = () => {
      try {
        const res = JSON.parse(xhr.responseText)
        if (res.code === 200 && res.data) {
          resolve(res.data.text || '')
        } else {
          reject(new Error(res.message || '识别失败'))
        }
      } catch (e) {
        reject(e)
      }
    }
    xhr.onerror = () => reject(new Error('网络错误'))
    xhr.send(formData)
  })
}

// ===================== 粘贴兜底监听 =====================
// Element Plus 的 el-input 内部才是真正的 textarea，外层 div 上的 @paste 依赖事件冒泡。
// 这里再直接给 textarea 挂一个捕获阶段监听做双保险，避免"粘贴没反应"。
// 说明：drawer 首次打开才渲染内部 DOM，所以 onMounted 与每次打开都要尝试绑定一次；
// 同一个 listener+capture 重复 addEventListener 会被浏览器自动去重，无需额外判重。
const onNativePaste = (e) => handlePaste(e)

const bindNativePaste = () => {
  nextTick(() => {
    const ta = msgInputRef.value?.textarea || msgInputRef.value?.input
    if (ta && ta.addEventListener) {
      ta.addEventListener('paste', onNativePaste, true)
    }
  })
}

onMounted(initSessionId)   // 会话持久化：恢复上次会话（刷新页面 / 进出设置中心不再断上下文）
onMounted(bindNativePaste)
onMounted(loadModels)
// 积分余额（2026-10-24）：进入页面即取；每次打开抽屉再刷一次（管理员刚分配完立即可见）
onMounted(loadCredit)
// 协议闸门：进入页面即预查后端同意状态（已同意 → 后续点击零等待放行）
onMounted(refreshDisclaimerStatus)
// 语音提醒 + 语音唤醒：同步本机状态并绑定事件
onMounted(() => {
  // 语音三功能被过滤的环境（客户包 VITE_VOICE_FEATURES=false）：直接跳过
  // —— 不读本机静音状态、不监听唤醒事件、不申请麦克风
  if (!VOICE_FEATURES_ENABLED) return
  muted.value = isMuted()
  window.addEventListener(EVENT_APPROVAL_INCREASE, onApprovalIncrease)
  // 唤醒事件（由 utils/serverWakeup.js 在命中唤醒词 / 收到指令时派发）
  window.addEventListener(EVENT_WAKEUP, onWakeupEvent)
  // 本机偏好：语音需确认 / 纯语音不打开界面（纯语音的采音仍需点一下标签才会开，见 onToggleVoiceSilent）
  voiceConfirm.value = isVoiceConfirmEnabled()
  voiceSilent.value = isVoiceSilentEnabled()
  bindVoiceAutostart()   // 刷新后：本页第一次点击/按键时自动恢复纯语音的采音
  // 内部参数（等待指令时长）：进页面取一次，之后每分钟同步一次（平台改了参数，用户不用重登/重开）
  loadVoiceRuntimeConfig()
  voiceCfgTimer = setInterval(loadVoiceRuntimeConfig, 60000)
})
// 打开抽屉视为已查看 → 气泡清零
watch(visible, (v) => { if (v) { bindNativePaste(); pendingApproval.value = 0; loadCredit() } })

// ===== 小楷浮球动画状态联动 =====
onMounted(() => { startIdleRandom() })

// 唤醒聆听中 → 播放唤醒帧序列（唤醒帧播完停在最后一帧，等聆听结束再回待机）
watch(wakeupListening, (v) => {
  if (v) { playWakeup() }
  else if (fabState.value === 'wakeup') { startIdleRandom() }
})

// 发送消息加载中 → 干活帧循环；加载结束 → 回归待机
watch(loading, (v) => {
  if (v) { playWork() }
  else if (fabState.value === 'work') { startIdleRandom() }
})

// 抽屉关闭 → 播结束帧序列，播完回待机
watch(visible, (v) => {
  if (!v && fabState.value !== 'end') {
    playEnding(() => { startIdleRandom() })
  }
})

onUnmounted(() => { stopFabTimer() })

// 清理资源
onUnmounted(() => {
  unbindVoiceAutostart()
  if (voiceCfgTimer) { clearInterval(voiceCfgTimer); voiceCfgTimer = null }
  stopProgressVoice()
  clearConfirmWindow()
  if (speakIdleTimer) { clearTimeout(speakIdleTimer); speakIdleTimer = null }
  window.removeEventListener(EVENT_APPROVAL_INCREASE, onApprovalIncrease)
  window.removeEventListener(EVENT_WAKEUP, onWakeupEvent)
  const ta = msgInputRef.value?.textarea || msgInputRef.value?.input
  if (ta && ta.removeEventListener) {
    ta.removeEventListener('paste', onNativePaste, true)
  }
  // 组件卸载：停掉录音采集链（麦克风不占用、音频上下文释放）
  try { if (audioProcessor) { audioProcessor.disconnect(); audioProcessor.onaudioprocess = null } } catch (e) { /* 忽略 */ }
  try { if (audioSource) audioSource.disconnect() } catch (e) { /* 忽略 */ }
  audioProcessor = null
  audioSource = null
  if (audioContext) { try { audioContext.close() } catch (e) { /* 忽略 */ } audioContext = null }
  if (stream) {
    stream.getTracks().forEach(track => track.stop())
  }
  attachments.value.forEach(att => {
    if (att.preview) URL.revokeObjectURL(att.preview)
  })
})
</script>

<style scoped>
.ai-assistant-wrapper {
  position: relative;
}

/* 历史会话列表（2026-10-22） */
.history-list { max-height: 60vh; overflow-y: auto; }
.history-empty { padding: 24px 0; text-align: center; color: #909399; font-size: 13px; }
.history-item {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 10px 12px; border-radius: 6px; cursor: pointer;
  border: 1px solid #ebeef5; margin-bottom: 8px;
}
.history-item:hover { background: #f5f7fa; }
.history-item-current { border-color: #409eff; background: #ecf5ff; }
.history-item-main { min-width: 0; }
.history-item-title {
  font-size: 13px; color: #303133; overflow: hidden;
  text-overflow: ellipsis; white-space: nowrap; max-width: 380px;
}
.history-item-meta { font-size: 12px; color: #909399; margin-top: 2px; }

.ai-fab {
  position: fixed; right: 24px; bottom: 24px;
  width: 72px; height: 72px;
  background: transparent;
  border-radius: 50%;
  cursor: pointer;
  z-index: 999;
  transition: transform .2s;
  display: flex; align-items: center; justify-content: center;
}
.ai-fab:hover { transform: scale(1.08); }

/* 机器人形象图片：不同状态轻微缩放/位移增强动感 */
.ai-fab-img {
  width: 100%; height: 100%;
  object-fit: contain;
  filter: drop-shadow(0 4px 12px rgba(99, 102, 241, 0.35));
  transition: transform .3s ease;
}
.ai-fab-img-idle { animation: fab-idle-float 3s ease-in-out infinite; }
.ai-fab-img-wakeup { filter: drop-shadow(0 4px 16px rgba(16, 185, 129, 0.5)); }
.ai-fab-img-work { animation: fab-work-bounce 0.7s ease-in-out infinite; }
.ai-fab-img-end { animation: fab-end-settle 1.5s ease-out forwards; }

@keyframes fab-idle-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px); }
}
@keyframes fab-work-bounce {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-3px) scale(1.04); }
}
@keyframes fab-end-settle {
  0% { transform: scale(1.05); opacity: 1; }
  100% { transform: scale(1); opacity: 1; }
}

/* 聆听中文字提示 */
.ai-fab-listening-text {
  position: absolute; bottom: -18px; left: 50%; transform: translateX(-50%);
  white-space: nowrap; font-size: 11px; font-weight: 600;
  color: #10b981; background: rgba(255,255,255,0.9);
  padding: 2px 8px; border-radius: 10px;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.3);
}

/* 语音唤醒后进入"聆听中"：浮球呼吸放大，明确告知"说完话才会打开界面" */
.ai-fab-listening {
  animation: ai-fab-pulse 1.2s ease-in-out infinite;
}
@keyframes ai-fab-pulse {
  0%, 100% { transform: scale(1); filter: drop-shadow(0 4px 20px rgba(16, 185, 129, 0.45)); }
  50% { transform: scale(1.08); filter: drop-shadow(0 8px 30px rgba(16, 185, 129, 0.85)); }
}

/* 待审批数字气泡：有新增待审批未读时显示，打开小楷后清零 */
.ai-fab-badge {
  position: absolute; top: -6px; right: -6px;
  min-width: 20px; height: 20px; padding: 0 6px;
  background: #f56c6c; color: #fff;
  border-radius: 10px; font-size: 12px; font-weight: 700;
  line-height: 20px; text-align: center;
  box-shadow: 0 2px 8px rgba(245, 108, 108, 0.5);
}

.channel-tabs { display: flex; gap: 8px; margin-bottom: 12px; }
/* 积分胶囊与深度思考开关（2026-10-24） */
.credit-chip { display: inline-flex; align-items: center; font-weight: 600; }
.deep-toggle { cursor: pointer; user-select: none; }
.credit-warn {
  margin: -4px 0 10px;
  padding: 7px 12px;
  background: #fdf6ec;
  border: 1px solid #f5dab1;
  border-radius: 6px;
  color: #b26a00;
  font-size: 12.5px;
}
.voice-mute-tag { cursor: pointer; }


.section-label {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 6px;
  font-weight: 500;
}

.chat-area { 
  /* 兜底高度：万一 .ai-assistant-drawer 未命中，表现与改造前一致，不会更差；
     命中时抽屉主体已是 flex 列布局，该值仅作 flex-basis，会被自动拉伸/压缩到剩余空间 */
  height: calc(100vh - 360px); 
  overflow-y: auto; 
  padding: 12px; 
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 12px;
}

.msg {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  align-items: flex-start; /* bot 默认左对齐 */
}
.msg.user {
  align-items: flex-end; /* 用户消息右对齐 */
}
.msg-role { font-size: 12px; font-weight: 600; margin-bottom: 4px; }
.msg.user .msg-role { color: #2563eb; }
.msg.bot .msg-role { color: #7c3aed; }
.msg-text {
  background: #f3f4f6;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  color: #374151;
  max-width: 100%; /* 助手答复通栏铺满（对齐 Trae 的答复版式，不浪费宽度） */
  word-break: break-word;
}
.msg.user .msg-text {
  background: #dbeafe;
  color: #1e40af;
  max-width: 80%; /* 用户自己的提问保留气泡状，便于区分谁在说话 */
}

/* 以图识图结果样式 */
.image-search-result {
  margin-top: 10px;
  background: #fff;
  border: 1px solid #e0e7ff;
  border-radius: 10px;
  overflow: hidden;
  align-self: stretch; /* 数据卡片撑满父容器宽度，不受气泡限宽影响 */
}

.image-search-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: linear-gradient(135deg, #eef2ff, #faf5ff);
  border-bottom: 1px solid #e0e7ff;
}

.image-search-header .result-title {
  font-size: 13px;
  font-weight: 600;
  color: #4f46e5;
}

.section-subtitle {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.matched-materials {
  padding: 12px 14px;
  border-bottom: 1px solid #f3f4f6;
}

.material-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.material-item {
  display: flex;
  gap: 12px;
  padding: 10px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  transition: all .2s;
}

.material-item.top-match {
  background: linear-gradient(135deg, #ecfdf5, #f0fdf4);
  border-color: #86efac;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.1);
}

.mat-image-wrap {
  position: relative;
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
}

.mat-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mat-badge {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  background: linear-gradient(135deg, #10b981, #059669);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  text-align: center;
  padding: 2px 0;
}

.mat-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.mat-name {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.mat-code {
  font-size: 11px;
  color: #6b7280;
  font-family: monospace;
}

.mat-spec {
  font-size: 11px;
  color: #6b7280;
}

.mat-similarity {
  margin-top: 4px;
}

.related-docs {
  padding: 12px 14px;
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: #f9fafb;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.doc-type-tag {
  flex-shrink: 0;
  background: #6366f1;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 4px;
}

.doc-main {
  flex: 1;
  min-width: 0;
}

.doc-no {
  font-size: 13px;
  font-weight: 600;
  color: #4f46e5;
}

.doc-customer {
  font-size: 11px;
  color: #6b7280;
  margin-top: 2px;
}

.doc-date {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 1px;
}

.doc-right {
  flex-shrink: 0;
  text-align: right;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.doc-qty {
  font-size: 12px;
  color: #374151;
}

.doc-amount {
  font-size: 13px;
  font-weight: 600;
  color: #dc2626;
}

.msg-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.msg-image {
  max-width: 150px;
  max-height: 150px;
  border-radius: 8px;
  object-fit: cover;
  border: 2px solid #e5e7eb;
}
.msg-files {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.msg-file {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 12px;
  color: #303133;
  background: #f4f6f8;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.msg-result {
  margin-top: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  align-self: stretch; /* 查询结果表格撑满宽度 */
}

.result-header {
  padding: 8px 12px;
  background: #f0fdf4;
  border-bottom: 1px solid #bbf7d0;
  display: flex;
  align-items: center;
  gap: 10px;
}
.result-merge-hint {
  font-size: 12px;
  color: #15803d;
  background: #dcfce7;
  border-radius: 10px;
  padding: 1px 8px;
}

.result-title {
  font-size: 12px;
  font-weight: 600;
  color: #166534;
}

.result-table-wrap {
  padding: 8px;
}

.msg-action { margin-top: 6px; }

/* 失败/提示明细列表 */
.err-title {
  font-size: 12px;
  font-weight: 600;
  color: #b45309;
  margin-bottom: 4px;
}
.err-list {
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 6px;
  padding: 8px 10px;
  max-height: 220px;
  overflow-y: auto;
}
.err-line {
  font-size: 12px;
  color: #92400e;
  line-height: 1.6;
  word-break: break-all;
}

/* 写操作成功卡：结果信息 + 一键直达 */
.success-card {
  border: 1px solid #b3e19d;
  background: #f0f9eb;
  border-radius: 8px;
  padding: 10px 14px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.success-title { font-weight: 600; color: #529b2e; }
.success-info { display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; }
.success-name { font-weight: 500; color: #303133; word-break: break-all; }
.success-code { color: #909399; font-size: 12px; word-break: break-all; }

/* 多步骤任务卡：紧凑行距，状态用徽标区分；按钮在卡内，不做底部操作条 */
.task-card {
  border: 1px solid #dcdfe6;
  background: #fafcff;
  border-radius: 8px;
  padding: 10px 12px;
}
.task-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.task-title { font-weight: 600; color: #303133; font-size: 13px; }
.task-status { font-size: 12px; padding: 1px 8px; border-radius: 10px; background: #f0f2f5; color: #606266; }
.task-status.ts-ok { background: #e7f6e7; color: #2e7d32; }
.task-status.ts-bad { background: #fdecec; color: #c0392b; }
.task-status.ts-running { background: #eaf2ff; color: #2d6cdf; }
.task-goal { margin-top: 4px; font-size: 12px; color: #909399; }
.task-steps { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.task-step { border-left: 3px solid #dcdfe6; padding: 4px 8px; background: #fff; border-radius: 4px; }
.task-step.ts-ok { border-left-color: #67c23a; }
.task-step.ts-bad { border-left-color: #f56c6c; }
.task-step.ts-running { border-left-color: #409eff; }
.task-step-line { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.task-step-title { font-size: 12px; color: #303133; }
.task-step-badge { font-size: 11px; color: #909399; white-space: nowrap; }
.task-step.ts-ok .task-step-badge { color: #2e7d32; }
.task-step.ts-bad .task-step-badge { color: #c0392b; }
.task-step-msg { margin-top: 2px; font-size: 11px; color: #909399; word-break: break-all; }
.task-actions { margin-top: 8px; display: flex; gap: 8px; flex-wrap: wrap; }

.thinking {
  display: flex;
  align-items: center;
  gap: 4px;
}
.thinking .dot {
  font-size: 10px;
  opacity: 0.3;
  animation: blink 1.4s infinite both;
}
.thinking .dot:nth-child(2) { animation-delay: .2s; }
.thinking .dot:nth-child(3) { animation-delay: .4s; }

@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; }
  40% { opacity: 1; }
}

/* 写操作确认卡片 */
.confirm-summary {
  margin: 0 0 8px 0;
  padding: 8px 10px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 6px;
  font-size: 12px;
  font-family: inherit;
  color: #92400e;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}

.confirm-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 提示"也可以直接回复「确认」"：用户实测更习惯打字/说话，不给提示就只会去找按钮 */
.confirm-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 2px;
}

/* 待确认卡独立成块：与折叠区同款外边距，但默认展开、不参与折叠 */
.confirm-area {
  margin-top: 8px;
}

/* ---- 小助手消息块顺序（2026-10-06 改为 Trae 式：过程在上、答复在下）-------------------
   .msg 本身已是 flex column，只靠 order 调整块顺序，**不复制模板**。
   新版（默认，8cloud / 本地）：按 Trae 的答复顺序 ——
        ① 执行过程（思考/调用工具/工具结果，默认折叠，压在最上）
        ② 工具结果（查询结果表 / 以图识图结果）
        ③ 写操作结果卡 / 失败与提示明细
        ④ 答复文字   ⑤ 待确认卡（最底，等用户拍板；后端"请在下方确认卡片"措辞据此保持正确）
   旧版（.legacy-ui，121 客户包 VITE_ASSISTANT_UI_V2=false）：保持 2026-09-18 版式不变 ——
        执行过程折叠条 → 待确认卡 → 查询结果表 → 答复置底。
   ⚠️ 只影响视觉顺序：键盘 Tab 顺序仍按 DOM，与视觉顺序不一致（新旧版同）。 */
.msg.bot:not(.legacy-ui) .msg-steps-process { order: 1; }
.msg.bot:not(.legacy-ui) .msg-result { order: 2; }
.msg.bot:not(.legacy-ui) .image-search-result { order: 2; }
.msg.bot:not(.legacy-ui) .msg-action { order: 3; }
.msg.bot:not(.legacy-ui) .msg-text { order: 4; }
/* 2026-11-22 结论进结果表：答复区拆成"结论行+结果表+口径行"三段，与答复文字同组置底（同 order 保持 DOM 内部先后） */
.msg.bot:not(.legacy-ui) .msg-text-line { order: 4; }
.msg.bot:not(.legacy-ui) .msg-answer-table { order: 4; }
.msg.bot:not(.legacy-ui) .msg-steps-confirm { order: 5; }
.msg.bot:not(.legacy-ui) .msg-usage { order: 6; align-self: flex-end; }
.msg.bot.legacy-ui .msg-steps-process { order: 1; }
.msg.bot.legacy-ui .msg-steps-confirm { order: 2; }
.msg.bot.legacy-ui .msg-result { order: 3; }
.msg.bot.legacy-ui .msg-text { order: 4; }
.msg.bot.legacy-ui .msg-usage { order: 5; align-self: flex-end; }

.step-confirm_needed .step-header {
  color: #b45309;
}

.quick-cmds-section {
  padding: 8px 0;
  border-top: 1px solid #e5e7eb;
}

.quick-cmds { 
  display: flex; 
  flex-wrap: wrap; 
  gap: 4px; 
}

.input-area { 
  display: flex; 
  gap: 8px; 
  padding-top: 10px; 
  border-top: 1px solid #e5e7eb;
  align-items: flex-end;
}

.send-btn {
  height: 60px;
  white-space: nowrap;
}

.config-channel-select {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.config-label {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}

.channel-info-box {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
}

.channel-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

/* 附件预览区 */
.attachments-area {
  background: #f9fafb;
  border-radius: 8px;
  padding: 8px;
  margin-bottom: 10px;
  border: 1px solid #e5e7eb;
}

.attachments-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.att-item {
  position: relative;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
  min-width: 80px;
}

.att-image-wrap {
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f3f4f6;
}

.att-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: cover;
}

.att-file {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #374151;
  max-width: 180px;
}

.att-icon {
  font-size: 20px;
}

.att-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.att-remove {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  background: rgba(0,0,0,0.5);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
}

.att-remove:hover {
  background: rgba(239, 68, 68, 0.8);
}

/* 输入区工具栏 */
.input-area {
  flex-direction: column;
  gap: 6px;
  align-items: stretch;
}

.input-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 4px;
}

.tool-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  cursor: pointer;
  font-size: 16px;
  transition: all .15s;
  user-select: none;
}

.tool-btn:hover {
  background: #f3f4f6;
}

.tool-btn.recording {
  background: #fee2e2;
  animation: pulse 1s infinite;
}

.rec-pulse {
  animation: pulse-ring 1s infinite;
}

@keyframes pulse {
  0%, 100% { background: #fee2e2; }
  50% { background: #fecaca; }
}

@keyframes pulse-ring {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.tool-tip {
  font-size: 12px;
  color: #ef4444;
  margin-left: 8px;
}

.input-text-wrap {
  flex: 1;
}

.msg-input :deep(.el-textarea__inner) {
  resize: none;
  border: none;
  box-shadow: none;
  background: transparent;
}

/* 输入框容器：多行输入区与模型选择器同处一个盒子内 */
.input-box {
  position: relative;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  padding: 4px 8px;
  transition: border-color .2s, background-color .2s;
}
.input-box:focus-within {
  border-color: #7c3aed;
}
/* 拖拽文件悬停：高亮 + 覆盖提示（覆盖层禁止拦截拖拽事件，否则 drop 收不到） */
.input-box.drag-over {
  border-color: #7c3aed;
  background: #f5f3ff;
}
.drag-tip {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(245, 243, 255, 0.92);
  color: #7c3aed;
  font-size: 13px;
  font-weight: 600;
  pointer-events: none;
}

.send-btn {
  align-self: flex-end;
  height: 40px;
  width: 100%;
}

/* Agent模式样式 */
.agent-steps {
  margin-top: 8px;
  align-self: stretch; /* 结果区撑满宽度 */
}

.agent-steps-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  cursor: pointer; /* 点击展开/收起 */
  user-select: none;
}
.agent-steps-header:hover .steps-title,
.agent-steps-header:hover .steps-toggle {
  color: #4f46e5; /* hover 高亮提示可点击 */
}
.steps-toggle {
  margin-left: 4px;
  font-size: 12px;
  color: #6366f1;
  font-weight: 600;
}

.steps-title {
  font-size: 12px;
  font-weight: 500;
  color: #6b7280;
}

.agent-steps-list {
  margin-top: 6px;
  max-height: 400px;
  overflow-y: auto;
}

.agent-step {
  margin-bottom: 8px;
  padding-left: 10px;
  border-left: 3px solid #e5e7eb; /* 用左侧色条替代卡片边框，减少"盒子"感 */
}

.agent-step:last-child {
  margin-bottom: 0;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  font-size: 12px;
  font-weight: 500;
  color: #6b7280;
}

/* 用左侧色条区分步骤类型（替代原来的整块彩色卡片底） */
.step-thought { border-left-color: #fbbf24; }
.step-thought .step-header { color: #92400e; }

.step-action { border-left-color: #93c5fd; }
.step-action .step-header { color: #1e40af; }

.step-observation { border-left-color: #34d399; }
.step-observation .step-header { color: #065f46; }

.step-final_answer { border-left-color: #a5b4fc; }
.step-final_answer .step-header { color: #3730a3; }

.step-error { border-left-color: #f87171; }
.step-error .step-header { color: #991b1b; }

.step-confirm_needed { border-left-color: #f59e0b; }

.step-icon {
  font-size: 14px;
}

.step-label {
  flex: 1;
}

.step-content {
  padding: 4px 0 2px;
  font-size: 12px;
  color: #374151;
  line-height: 1.5;
}

.tool-params {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.params-label {
  font-weight: 500;
  color: #6b7280;
  font-size: 11px;
}

.params-json {
  margin: 0;
  padding: 6px;
  background: #1f2937;
  color: #e5e7eb;
  border-radius: 4px;
  font-size: 11px;
  font-family: monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow-y: auto;
}

.obs-more {
  margin-top: 4px;
  display: flex;
  justify-content: flex-end;
}

/* 模型真实推理内容（思考不再静默）：滚动区防超长，样式与参数 JSON 区分（浅底深字） */
.reasoning-text {
  margin: 0 0 4px;
  padding: 6px 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-left: 3px solid #94a3b8;
  border-radius: 4px;
  color: #334155;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 180px;
  overflow-y: auto;
}

/* 流式思考中：左边线高亮 + 微光背景，与"已完成的思考"区分 */
.reasoning-text.reasoning-live {
  border-left-color: #409eff;
  background: #f0f7ff;
}

/* 思考中的动态省略号（三点跳动） */
.step-thinking-dots {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  margin-left: 6px;
}
.step-thinking-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #409eff;
  animation: thinking-dot-bounce 1.2s infinite ease-in-out;
}
.step-thinking-dots i:nth-child(2) { animation-delay: 0.15s; }
.step-thinking-dots i:nth-child(3) { animation-delay: 0.3s; }
@keyframes thinking-dot-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-3px); opacity: 1; }
}

/* 有推理时的步骤说明一行小字（辅助信息） */
.step-brief {
  color: #9ca3af;
  font-size: 11px;
}

/* ===== 2026-11-22 改版：TRAE 式思考链路时间线（仅新版界面） ===== */
.tl-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fff;
}
.tl-header .steps-title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6b7280;
}
.tl-header .steps-title.is-running { color: #4f46e5; }
.tl-title-ico { font-size: 12px; }
.tl-count {
  color: #b0b6bf;
  font-size: 11px;
  margin-left: 2px;
}
.tl-arrow { color: #9ca3af; }
.agent-steps-list.tl-mode {
  position: relative;
  margin-top: 6px;
  padding: 2px 0;
  max-height: 440px;
  overflow-y: auto;
}
.tl-item {
  position: relative;
  padding: 2px 0 2px 26px;
  font-size: 12px;
  line-height: 20px;
}
/* 细竖线串起各节点（TRAE 式时间线） */
.tl-item::before {
  content: '';
  position: absolute;
  left: 9px;
  top: 0;
  bottom: 0;
  width: 1px;
  background: #e8eaee;
}
.tl-item:first-child::before { top: 11px; }
.tl-item:last-child::before { height: 12px; bottom: auto; }
.tl-row {
  display: flex;
  align-items: center;
  gap: 6px;
  border-radius: 6px;
  padding: 0 6px;
  margin-left: -6px;
  min-height: 20px;
}
.tl-row.clickable:hover { background: #f5f6fa; }
.tl-ico {
  position: absolute;
  left: 3px;
  top: 4px;
  width: 13px;
  font-size: 11px;
  line-height: 13px;
  text-align: center;
  background: #fff;
  z-index: 1;
}
.tl-label { color: #374151; white-space: nowrap; }
.tl-sub {
  color: #9ca3af;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}
.tl-sub-ok { color: #9ca3af; }
.tl-sub-bad { color: #f56c6c; }
.tl-sub-run { color: #409eff; }
.tl-arrow-sm { color: #c0c4cc; font-size: 11px; margin-left: auto; }
.tl-error-text { color: #f56c6c; white-space: normal; }
/* 思考卡片：圆角边框、内部滚动（TRAE 式） */
.tl-card {
  margin: 4px 0 4px 26px;
  padding: 8px 10px;
  border: 1px solid #e8eaee;
  border-radius: 8px;
  background: #fafbfc;
  max-height: 200px;
  overflow-y: auto;
}
.tl-card-live { border-color: #b6ccf7; background: #f8faff; }
.tl-card-text {
  font-size: 12px;
  color: #4b5563;
  white-space: pre-wrap;
  word-break: break-word;
}
.tl-card .reasoning-text {
  margin: 0;
  font-size: 12px;
  color: #4b5563;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 180px;
  overflow-y: auto;
}
/* 工具节点展开明细：参数 + 原始结果 */
.tl-detail {
  margin: 4px 0 4px 26px;
  padding: 6px 8px;
  border: 1px dashed #e8eaee;
  border-radius: 8px;
  background: #fff;
}
.tl-detail .tool-params { margin-bottom: 6px; }
.tl-detail .params-json {
  margin: 4px 0 0;
  font-size: 11px;
  color: #6b7280;
  max-height: 120px;
  overflow-y: auto;
}

/* ===== 2026-11-22 改版：结论进结果表（答复里的表格结构化渲染） ===== */
.msg-text-line {
  font-size: 13px;
  color: #374151;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-answer-table {
  margin: 6px 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  align-self: stretch;
}

/* 本轮用量行（耗时 + 额度消耗）：答复末尾灰色小字 */
.msg-usage {
  margin-top: 4px;
  font-size: 11px;
  color: #b0b6bf;
  text-align: right;
  user-select: none;
}

.error-content {
  color: #dc2626;
}

/* ===== 输入区底部：模型选择器 + 发送（模型切换紧挨发送按钮右侧排布，无边框文字样式） ===== */
.input-bottom {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  padding-top: 4px;
  border-top: 1px solid #f0f2f5;
}
.model-select {
  /* 必须给确定宽度：EP 选中值的文字渲染在绝对定位的 .el-select__placeholder 里（不参与宽度计算），
     用 width:auto 会让控件塌陷成只剩箭头宽，选中后文字被 overflow:hidden 裁掉而看不见。 */
  width: 210px;
  flex-shrink: 0;
}

/* ===== 2026-11-22 照参考设计：模型选择弹层（左模型列表 + 右能力面板） ===== */
.model-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 240px;
  padding: 5px 10px;
  font-size: 12px;
  color: #374151;
  background: #f5f6fa;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.model-trigger:hover { border-color: #c7d2fe; color: #4f46e5; }
.model-trigger-caret { color: #9ca3af; font-size: 10px; }
.mp-body {
  display: flex;
  gap: 12px;
  height: 320px;
}
.mp-list {
  flex: 1.1;
  overflow-y: auto;
  border-right: 1px solid #eef0f3;
  padding-right: 10px;
}
.mp-group {
  font-size: 11px;
  color: #9ca3af;
  margin: 8px 0 4px;
}
.mp-group:first-child { margin-top: 0; }
.mp-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 12px;
  color: #374151;
  cursor: pointer;
}
.mp-item:hover { background: #f5f6fa; }
.mp-item.is-active { background: #eef2ff; color: #4f46e5; font-weight: 600; }
.mp-item.is-disabled { color: #c0c4cc; cursor: not-allowed; }
.mp-check { color: #4f46e5; }
.mp-panel {
  flex: 1;
  overflow-y: auto;
}
.mp-sec-title {
  font-size: 12px;
  color: #6b7280;
  margin: 6px 0 6px;
}
.mp-sec-title:first-child { margin-top: 0; }
.mp-seg {
  display: inline-flex;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}
.mp-seg-btn {
  padding: 4px 16px;
  font-size: 12px;
  color: #374151;
  cursor: pointer;
  user-select: none;
  background: #fff;
}
.mp-seg-btn + .mp-seg-btn { border-left: 1px solid #e5e7eb; }
.mp-seg-btn:hover { color: #4f46e5; }
.mp-seg-btn.is-on {
  background: #4f46e5;
  color: #fff;
  font-weight: 600;
}
.mp-desc {
  margin-top: 6px;
  font-size: 11px;
  color: #9ca3af;
  line-height: 1.6;
  white-space: pre-wrap;
}
/* 去边框：只保留模型名 + 下拉箭头（无框、无底色，hover/focus 也不出现描边） */
.model-select :deep(.el-select__wrapper) {
  box-shadow: none;
  background: transparent;
  padding: 0 4px;
  min-height: 30px;
}
.model-select :deep(.el-select__wrapper.is-hovering),
.model-select :deep(.el-select__wrapper.is-focused) {
  box-shadow: none;
}
.model-select :deep(.el-select__placeholder),
.model-select :deep(.el-select__selected-item) {
  font-size: 13px;
  color: #374151; /* 加深文字黑度（原 #6b7280 偏浅） */
}
.model-select :deep(.el-select__caret) {
  color: #6b7280;
}
.input-bottom .send-btn {
  width: auto;
  min-width: 96px;
  height: 32px;
  align-self: center;
}
</style>

<style>
/* 抽屉主体纵向铺满：消息区自适应压缩、输入区常驻可见。
   必须用非 scoped 样式：抽屉 DOM 由 element-plus 渲染并挂到 body，scoped 的 :deep 匹配不到 .el-drawer__body。
   用自定义类 .ai-assistant-drawer 限定范围，不影响其它 el-drawer。 */
.ai-assistant-drawer .el-drawer__header {
  margin-bottom: 8px;
}
.ai-assistant-drawer .el-drawer__body {
  display: flex;
  flex-direction: column;
}
/* 除消息区外都保持固有高度，避免被压缩后把输入区挤出可视区 */
.ai-assistant-drawer .el-drawer__body > * {
  flex: none;
}
.ai-assistant-drawer .el-drawer__body > .chat-area {
  flex: 1 1 auto;
  min-height: 120px;
}
</style>
