<template>
  <!-- 工序操作面板（第 2 版 2026-10-07 重做）
       布局：① 工序信息 → ② 物料信息 → ③ 本次操作 → ④ 动作按钮 → ⑤ 备注 → ⑥ 附件 → ⑦ 工序通知 → ⑧ 操作日志
       动作按钮「可点/置灰」一律取自后端 status-meta 下发的 actions（前端不自算状态，守则 11） -->
  <el-dialog v-model="visible" width="880px" @open="onOpen">
    <template #header>
      <div class="op-dlg-head">
        <span class="op-dlg-title">{{ $tt("工序操作") }}</span>
        <span
          v-if="op"
          class="op-dlg-badge"
          :style="{ color: style.color, borderColor: style.color }"
        >{{ $dt(op.operationStatus || '待派工') }}</span>
        <span class="op-dlg-grow"></span>
        <!-- 列设置（工序明细列 + 物料明细列一处配置；不用固定定位，跟着按钮自适应） -->
        <el-popover v-model:visible="colSettingVisible" trigger="click" placement="bottom-end" :width="430">
          <template #reference>
            <el-button size="small" type="primary" :icon="Setting">{{ $tt("列设置") }}</el-button>
          </template>
          <div class="ocs">
            <div class="ocs-title">{{ $tt("显示的列（勾选 + 拖动排序）") }}</div>
            <div class="ocs-group">{{ $tt("工序明细") }}</div>
            <div class="ocs-list">
              <draggable v-model="opSettingLocal" item-key="prop" handle=".ocs-drag" animation="180" ghost-class="ocs-ghost">
                <template #item="{ element }">
                  <div class="ocs-row">
                    <el-icon class="ocs-drag"><Rank /></el-icon>
                    <el-checkbox v-model="element.visible">{{ $tf(element.label) }}</el-checkbox>
                  </div>
                </template>
              </draggable>
            </div>
            <div class="ocs-group">{{ $tt("物料明细") }}</div>
            <div class="ocs-list">
              <draggable v-model="matSettingLocal" item-key="prop" handle=".ocs-drag" animation="180" ghost-class="ocs-ghost">
                <template #item="{ element }">
                  <div class="ocs-row">
                    <el-icon class="ocs-drag"><Rank /></el-icon>
                    <el-checkbox v-model="element.visible">{{ $tf(element.label) }}</el-checkbox>
                  </div>
                </template>
              </draggable>
            </div>
            <div class="ocs-footer">
              <el-button size="small" @click="resetColSetting">{{ $tt("重置默认") }}</el-button>
              <el-button size="small" type="primary" @click="saveColSetting">{{ $tt("保存") }}</el-button>
            </div>
          </div>
        </el-popover>
      </div>
    </template>
    <div v-if="op" class="op-panel">
      <!-- ① 工序明细（表头一行列名 + 值一行；显示哪些列由「列设置」勾选/排序） -->
      <div class="op-detail">
        <div class="op-detail-head">
          <span class="op-detail-title">{{ $tt("工序明细") }}</span>
          <el-tag v-if="op.operationType" size="small" effect="plain">{{ $tf(op.operationType) }}</el-tag>
          <el-tag v-if="op.isCritical === 1" size="small" type="danger" effect="dark">{{ $tt("关键工序") }}</el-tag>
          <el-tag v-if="op.isQcRequired === 1" size="small" type="warning">{{ $tt("需质检") }}</el-tag>
        </div>
        <div class="op-detail-wrap">
          <el-table
            ref="opTableRef"
            :data="opDetailRows"
            border
            size="small"
            class="op-detail-table"
            @header-dragend="opColWidth.onHeaderDragend"
          >
            <el-table-column
              v-for="c in opShownCols"
              :key="c.prop"
              :prop="c.prop"
              :label="$tf(c.label)"
              :align="c.num ? 'right' : 'left'"
            >
              <template #default>{{ c.value }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- ② 物料明细（表头 + 值行；取不到物料显示「未关联物料」） -->
      <div class="op-detail">
        <div class="op-detail-head">
          <span class="op-detail-title">{{ $tt("物料明细") }}</span>
        </div>
        <div v-if="material" class="op-detail-wrap">
          <el-table
            ref="matTableRef"
            :data="matDetailRows"
            border
            size="small"
            class="op-detail-table"
            @header-dragend="matColWidth.onHeaderDragend"
          >
            <el-table-column prop="__thumb" :label="$tt('图')" width="60" align="center">
              <template #default>
                <el-image
                  v-if="materialImages.length"
                  :src="materialImages[0]"
                  :preview-src-list="materialImages"
                  :initial-index="0"
                  fit="cover"
                  class="op-detail-img"
                  preview-teleported
                />
              </template>
            </el-table-column>
            <el-table-column v-for="c in matShownCols" :key="c.prop" :prop="c.prop" :label="$tf(c.label)">
              <template #default>{{ c.value }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div v-else class="op-detail-empty">{{ $tt("未关联物料") }}</div>
      </div>

      <!-- ③ 本次操作（按工序类型渲染；数量类型自动带出，可改） -->
      <el-divider content-position="left">{{ $tt("本次操作") }}</el-divider>
      <el-form :inline="true" size="small" class="op-plan-row">
        <el-form-item :label="$tf('负责人')">
          <!-- 【2026-11-26】负责人多选：第一位为主负责人，其余自动成为共同负责人；选完即存 -->
          <el-select v-model="form.assigneeIds" multiple filterable clearable collapse-tags collapse-tags-tooltip
                     :disabled="!assignEditable" style="width: 280px" :placeholder="$tt('选择负责人')" @change="onAssigneesChange">
            <el-option v-for="e in employees" :key="e.value" :label="e.label" :value="e.value" />
          </el-select>
          <span v-if="planEditable && !assignEditable" class="plan-hint warn">{{ $tt("按工艺路线设置，你不能修改负责人") }}</span>
          <span v-else class="plan-hint">{{ $tt("可多选，第一位为主负责人，其余为共同负责人") }}</span>
        </el-form-item>
        <el-form-item :label="$tf('计划完工')">
          <el-date-picker v-model="form.planEndDate" type="date" value-format="YYYY-MM-DD" :disabled="!planEditable" style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <span class="plan-hint">{{ $tt("负责人选完即存；计划完工随「保存」或任一动作提交") }}</span>
        </el-form-item>
      </el-form>

      <!-- 【2026-10-25】共同负责人（扫码认领）：主负责人仍是系统派工的人，这里只追加一起做的人 -->
      <div class="op-workers">
        <span class="workers-label">{{ $tt("共同负责人") }}</span>
        <template v-if="workerInfo.workers && workerInfo.workers.length">
          <el-tag
            v-for="w in workerInfo.workers"
            :key="w.employeeId"
            size="small"
            :type="w.primaryAssignee ? 'primary' : 'info'"
            class="worker-tag"
          >{{ w.employeeName || '' }}<template v-if="w.claimQuantity != null"> · {{ $tt("认领") }} {{ w.claimQuantity }} · {{ $tt("已报") }} {{ w.reportedQuantity || 0 }}</template></el-tag>
        </template>
        <span v-else class="workers-empty">{{ $tt("暂无共同负责人") }}</span>
        <span v-if="workerInfo.myJoined" class="workers-mine">
          {{ $tt("我") }} · {{ $tt("认领") }} {{ workerInfo.myClaim == null ? $tt("不限") : workerInfo.myClaim }} · {{ $tt("已报") }} {{ workerInfo.myReported || 0 }}<template v-if="workerInfo.myRemaining != null"> · {{ $tt("还可报") }} {{ workerInfo.myRemaining }}</template>
        </span>
        <template v-if="workerInfo.canClaim">
          <el-input v-model="claimQty" size="small" :placeholder="$tt('打算做多少，可不填')" style="width: 160px" />
          <el-button type="primary" plain size="small" :loading="claiming" @click="doClaim">{{ $tt("我来做") }}</el-button>
        </template>
        <template v-else-if="workerInfo.reason">
          <span class="workers-hint">{{ workerInfo.reason }}</span>
          <el-button v-if="workerInfo.myJoined" size="small" :loading="claiming" @click="doUnclaim">{{ $tt("退出") }}</el-button>
        </template>
        <span v-if="workerInfo.remainingClaimable != null" class="workers-hint">{{ $tt("本道还可认领") }} {{ workerInfo.remainingClaimable }}</span>
      </div>

      <!-- 工时体系：所有工序类型都有（工时是通用的，不随类型变化）
           实际工时由系统按 开工/暂停/恢复/完工 自动累计，只读；计划 = 准备+加工工时；差异行只读 -->
      <el-form :inline="true" size="small" class="op-hours-row">
        <el-form-item :label="$tf('本次工时')">
          <span class="hours-readonly">{{ actualHoursText }}</span>
          <span class="hours-timing">
            <span class="hours-dot" :class="timingState.on ? 'on' : 'off'"></span>
            <span :class="timingState.on ? 'timing-on' : 'timing-off'">{{ timingState.text }}</span>
          </span>
          <span class="hours-hint">{{ $tt("由开工/暂停/完工自动累计，不用手填") }}</span>
        </el-form-item>
        <el-form-item>
          <span class="op-hours-cmp">
            <span class="cmp-part">{{ $tt("计划") }} {{ planHoursText }}</span>
            <span class="cmp-sep">｜</span>
            <span class="cmp-part">{{ $tt("实际") }} {{ actualHoursText }}</span>
            <template v-if="diffHours != null">
              <span class="cmp-sep">｜</span>
              <span class="cmp-part">{{ $tt("误差") }} <b :class="diffClass">{{ diffHoursText }}<template v-if="diffRateText">（{{ diffRateText }}）</template></b></span>
            </template>
          </span>
        </el-form-item>
      </el-form>

      <!-- 数量类型：本次投入 / 本次产出 / 本次报废 -->
      <el-form v-if="opType === '数量类型'" :inline="true" size="small" class="op-report">
        <el-form-item :label="$tf('本次投入')">
          <el-input-number v-model="form.inputQuantity" :min="0" :precision="scaleFor('qty')" :controls="false" :disabled="!reportNowAllowed" style="width: 120px" />
        </el-form-item>
        <el-form-item :label="$tf('本次产出（合格）')">
          <el-input-number v-model="form.quantity" :min="0" :precision="scaleFor('qty')" :controls="false" :disabled="!reportNowAllowed" style="width: 120px" />
        </el-form-item>
        <el-form-item :label="$tf('本次报废')">
          <el-input-number v-model="form.rejectedQuantity" :min="0" :precision="scaleFor('qty')" :controls="false" :disabled="!reportNowAllowed" style="width: 120px" />
        </el-form-item>
      </el-form>

      <div v-if="opType === '数量类型'" class="op-progress">
        <el-progress :percentage="progressPercent" :stroke-width="10" :show-text="false" style="flex:1" />
        <span class="op-progress-txt">
          {{ $tt("已报工") }} {{ infoNum(op.reportedQuantity) }} / {{ $tt("计划") }} {{ infoNum(op.planQuantity) }}
        </span>
      </div>

      <!-- 金额类型：本次金额 -->
      <el-form v-else-if="opType === '金额类型'" :inline="true" size="small" class="op-report">
        <el-form-item :label="$tf('本次金额')">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :controls="false" :disabled="!typeAreaEditable" style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <span class="remark-hint">{{ $tt("登记后累加进「已确认金额」；计划金额在「工艺路线」的工序明细里维护") }}</span>
        </el-form-item>
      </el-form>

      <!-- 入库类型：入库数量 + 一键发起入库单 -->
      <el-form v-else-if="opType === '入库类型'" :inline="true" size="small" class="op-report">
        <el-form-item :label="$tf('入库数量')">
          <el-input-number v-model="receiptForm.quantity" :min="0" :precision="scaleFor('qty')" :controls="false" :disabled="!typeAreaEditable" style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <span class="remark-hint">
            {{ $tt("剩余可入库量") }}：{{ remainingReceiptQty }}（{{ $tt("合格产出") }} {{ infoNum(op.outputQuantity) || 0 }} − {{ $tt("已入库量") }} {{ infoNum(op.receivedQty) || 0 }}）
          </span>
        </el-form-item>
      </el-form>

      <!-- 状态类型：无输入框，灰字提示 -->
      <div v-else class="op-tip muted">{{ $tt("该工序为状态类型，按状态推进即可") }}</div>

      <!-- 【2026-11-25】工序自定义字段：随「保存」或任一动作一并提交；终态与完工待检只读 -->
      <template v-if="cfDefs.length">
        <el-divider content-position="left">{{ $tt("工序自定义字段") }}</el-divider>
        <div class="op-cf-grid">
          <div v-for="f in cfDefs" :key="f.fieldKey" class="op-cf-item">
            <span class="op-cf-label">
              {{ $tf(f.fieldLabel) }}<b v-if="f.isRequired === 1" class="op-cf-required">*</b>
            </span>
            <el-select
              v-if="f.fieldType === 'select'"
              v-model="cfValues[f.fieldKey]"
              filterable
              clearable
              size="small"
              :placeholder="f.placeholder || $tt('请选择')"
              :disabled="!cfEditable"
            >
              <el-option v-for="opt in cfOptions(f)" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-input-number
              v-else-if="f.fieldType === 'number'"
              v-model="cfValues[f.fieldKey]"
              :precision="f.precisionVal || 2"
              :min="f.minValue != null ? Number(f.minValue) : undefined"
              :max="f.maxValue != null ? Number(f.maxValue) : undefined"
              :controls="false"
              size="small"
              :disabled="!cfEditable"
            />
            <el-switch
              v-else-if="f.fieldType === 'switch'"
              v-model="cfValues[f.fieldKey]"
              :active-value="1"
              :inactive-value="0"
              :disabled="!cfEditable"
            />
            <el-date-picker
              v-else-if="f.fieldType === 'date' || f.fieldType === 'datetime'"
              v-model="cfValues[f.fieldKey]"
              :type="f.fieldType === 'datetime' ? 'datetime' : 'date'"
              :value-format="f.fieldType === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'"
              size="small"
              :disabled="!cfEditable"
            />
            <span v-else-if="f.fieldType === 'formula'" class="op-cf-readonly">{{ cfValues[f.fieldKey] ?? '' }}</span>
            <el-input
              v-else
              v-model="cfValues[f.fieldKey]"
              :type="f.fieldType === 'textarea' ? 'textarea' : 'text'"
              :rows="2"
              size="small"
              :placeholder="f.placeholder || ''"
              :disabled="!cfEditable"
            />
          </div>
        </div>
      </template>

      <!-- 异常处理（默认收起）：取消 / 恢复工序 + 回退确认 / 判废确认；按钮只渲染当前可执行的 -->
      <el-collapse v-if="abnormalHint" class="op-fold">
        <el-collapse-item name="abnormal">
          <template #title>
            <span class="op-fold-title">{{ $tt("异常处理") }}</span>
            <span class="op-fold-sub">{{ abnormalHint }}</span>
          </template>
          <div v-if="abnormalActions.length" class="op-act-row">
            <el-button
              v-for="b in abnormalActions"
              :key="b.code"
              class="op-act-btn"
              size="small"
              type="warning"
              plain
              :loading="acting && activeCode === b.code"
              @click="onActionButton(b)"
            >{{ $tt(b.label) }}</el-button>
          </div>
          <template v-if="opType === '数量类型'">
            <el-form v-if="typeAreaEditable" :inline="true" size="small" class="op-rework">
              <el-form-item :label="$tf('本次回退数')">
                <el-input-number v-model="reworkForm.quantity" :min="0" :precision="scaleFor('qty')" :controls="false" :disabled="!typeAreaEditable" style="width: 120px" />
                <el-button type="warning" plain :loading="acting" :disabled="!typeAreaEditable" @click="submitRework">{{ $tt("回退确认") }}</el-button>
              </el-form-item>
              <el-form-item :label="$tf('本次判废数')">
                <el-input-number v-model="scrapForm.quantity" :min="0" :precision="scaleFor('qty')" :controls="false" :disabled="!typeAreaEditable" style="width: 120px" />
                <el-button type="danger" plain :loading="acting" :disabled="!typeAreaEditable" @click="submitScrap">{{ $tt("判废确认") }}</el-button>
              </el-form-item>
            </el-form>
            <div class="op-supply">
              <span class="qty-label">{{ $tt("可下推补料量") }}</span>
              <b :class="pendingScrap > 0 ? 'bad' : ''">{{ pendingScrap }}</b>
              <span class="supply-hint">{{ $tt("判废数 − 已下推数；领料量按成品主BOM用量自动计算，点「单据下推 → 下推补料」生成") }}</span>
              <span v-if="op.supplementBillNo" class="op-bill">
                {{ $tt("已生成补料领料单：") }}<b>{{ op.supplementBillNo }}</b>
              </span>
            </div>
            <div class="op-tip">
              {{ $tt("回退：下道工序发现不良退回本道的数量；判废：本道师傅判定回退料中不能用、需要额外领料的数量；判废后点「下推补料」按 BOM 自动开补料领料单，发起人记当前登录人。") }}
            </div>
          </template>
        </el-collapse-item>
      </el-collapse>

      <!-- ④ 动作按钮：只渲染当前状态可执行的按钮（不能执行的不出现），按「报工 / 质检 / 单据下推」分组 -->
      <div class="op-actions-wrap">
        <div v-if="reportActions.length" class="op-act-group">
          <span class="op-act-group-title">{{ $tt("报工") }}</span>
          <div class="op-act-row">
            <el-button
              v-for="b in reportActions"
              :key="b.code"
              class="op-act-btn"
              size="small"
              type="primary"
              plain
              :loading="acting && activeCode === b.code"
              @click="onActionButton(b)"
            >{{ $tt(b.label) }}</el-button>
          </div>
        </div>
        <div v-if="qcActions.length" class="op-act-group">
          <span class="op-act-group-title">{{ $tt("质检") }}</span>
          <div class="op-act-row">
            <el-button
              v-for="b in qcActions"
              :key="b.code"
              class="op-act-btn"
              size="small"
              type="primary"
              plain
              :loading="acting && activeCode === b.code"
              @click="onActionButton(b)"
            >{{ $tt(b.label) }}</el-button>
          </div>
        </div>
        <div v-if="pushActions.length" class="op-act-group">
          <span class="op-act-group-title">{{ $tt("单据下推") }}</span>
          <div class="op-act-row">
            <el-button
              v-if="canPushReceipt"
              class="op-act-btn"
              size="small"
              type="primary"
              plain
              :loading="acting"
              @click="doPushReceipt"
            >{{ $tt("发起入库单") }}</el-button>
            <el-button
              v-if="canPushSupplement"
              class="op-act-btn"
              size="small"
              type="primary"
              plain
              :loading="acting"
              @click="doPushSupplement"
            >{{ $tt("下推补料") }}</el-button>
            <span v-if="canPushReceipt" class="op-push-hint">{{ $tt("剩余可入库量") }} {{ remainingReceiptQty }}</span>
            <span v-if="canPushSupplement" class="op-push-hint">{{ $tt("可下推补料量") }} {{ pendingScrap }}</span>
          </div>
        </div>
        <div class="op-act-row op-act-foot">
          <el-button
            class="op-save-btn"
            type="primary"
            size="small"
            :loading="acting && activeCode === '保存'"
            :disabled="!saveEnabled"
            @click="executeAction('保存', '保存')"
          >{{ $tt("保存") }}</el-button>
        </div>
        <div v-if="!hasMeta" class="op-tip muted">{{ $tt("暂不可操作（未取到状态元数据）") }}</div>
        <div v-else-if="!currentActions.length" class="op-terminal-line">{{ $tt("该工序已结束") }}</div>
        <div v-if="op.operationStatus === '完工待检'" class="op-tip">
          {{ $tt("该工序需质检：请点「填质检」录入检验项（可留证拍照）并提交，系统自动判定「检验通过 / 检验退回」；未通过前下道工序不能开工") }}
        </div>
      </div>

      <!-- ⑤ 备注（四种类型都有，统一一个框；终态只读） -->
      <el-form size="small" class="op-remark">
        <el-form-item :label="$tf('备注')">
          <el-input v-model="form.remark" :disabled="!remarkEditable" style="width: 420px" :placeholder="$tt('如暂停/退回原因')" />
          <span class="remark-hint">{{ $tt("随「保存」或任一动作一并记入操作日志") }}</span>
        </el-form-item>
      </el-form>

      <!-- ⑥ 附件（上传/预览/删除；完工/取消后只读不可删；随保存或任一动作落库） -->
      <el-divider content-position="left">{{ $tt("附件") }}</el-divider>
      <div class="op-attach">
        <!-- 生产工单附件：只读带入，每道工序都能看到；图片显示缩略图、点击放大，其它文件点击下载 -->
        <div v-if="billAttachList.length" class="bill-attach">
          <div class="bill-attach-title">{{ $tt("生产工单附件") }}</div>
          <div class="attach-grid">
            <div v-for="a in billAttachList" :key="'bill-' + a.url" class="attach-cell">
              <el-image
                v-if="a.isImage"
                class="attach-thumb"
                :src="a.url"
                fit="cover"
                @click="previewBillAttachment(a)"
              />
              <div v-else class="attach-file" @click="previewBillAttachment(a)">
                <el-icon class="attach-file__ico"><Document /></el-icon>
                <span class="attach-file__name" :title="a.name">{{ a.name }}</span>
              </div>
              <span class="attach-badge">{{ $tt("只读") }}</span>
            </div>
          </div>
        </div>
        <el-upload
          v-if="!attachReadonly"
          :show-file-list="false"
          :http-request="doUpload"
          :before-upload="beforeUpload"
          multiple
        >
          <el-button type="primary" plain size="small" :icon="Upload" :loading="uploading">{{ $tt("上传附件") }}</el-button>
          <template #tip>
            <div class="upload-tip">{{ $tt("支持图片/文档/压缩包，单个不超过 20MB，最多 20 个") }}</div>
          </template>
        </el-upload>
        <!-- 工序附件：图片显示缩略图、点击放大；其它文件显示文件卡片、点击下载 -->
        <div v-if="attachList.length" class="attach-grid">
          <div v-for="(a, idx) in attachList" :key="a.url" class="attach-cell">
            <el-image
              v-if="a.isImage"
              class="attach-thumb"
              :src="a.url"
              fit="cover"
              @click="previewOrDownload(a)"
            />
            <div v-else class="attach-file" @click="previewOrDownload(a)">
              <el-icon class="attach-file__ico"><Document /></el-icon>
              <span class="attach-file__name" :title="a.name">{{ a.name }}</span>
            </div>
            <span v-if="!attachReadonly" class="attach-del" @click.stop="removeAttachment(idx)">×</span>
          </div>
        </div>
        <div v-else class="attach-empty">{{ $tt("还没有附件，点上方上传") }}</div>
        <el-image-viewer
          v-if="viewerVisible"
          :url-list="attachImageUrls"
          :initial-index="viewerIndex"
          @close="viewerVisible = false"
        />
        <el-image-viewer
          v-if="billViewerVisible"
          :url-list="billImageUrls"
          :initial-index="billViewerIndex"
          @close="billViewerVisible = false"
        />
      </div>

      <!-- ⑦ 工序通知（@下道工序负责人；完成后自动通知为只读提示，改在工艺路线） -->
      <el-divider content-position="left">{{ $tt("工序通知") }}</el-divider>
      <div class="op-notify">
        <template v-if="hasNextOperation">
          <el-form :inline="true" size="small" class="op-notify-form">
            <el-form-item :label="$tf('通知给')">
              <el-select v-model="notifyForm.receiverIds" multiple filterable clearable style="width: 280px" :placeholder="$tt('请选择接收人')">
                <el-option v-for="e in employees" :key="e.value" :label="e.label" :value="e.value" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$tf('完成后自动通知')">
              <el-checkbox :model-value="autoNotify" disabled />
              <span class="notify-hint">{{ $tt("开启位置：生产管理 → 工艺路线 → 编辑") }}</span>
            </el-form-item>
          </el-form>
          <el-form label-width="80px" size="small">
            <el-form-item :label="$tf('通知内容')">
              <el-input v-model="notifyForm.content" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
          <div class="op-notify-actions">
            <span v-if="nextAssigneeName" class="notify-hint">{{ $tt("下一道负责人：") }}{{ nextAssigneeName }}</span>
            <span v-if="!nextOperation || !nextOperation.assigneeId" class="notify-hint warn">{{ $tt("下道工序尚未派工，自动通知不会发出") }}</span>
            <el-button type="primary" size="small" :loading="notifying" :disabled="!notifyForm.receiverIds.length" @click="doNotify">{{ $tt("发送通知") }}</el-button>
          </div>
        </template>
        <div v-else class="op-terminal-line">{{ $tt("本道为最后一道工序，无下道负责人可通知") }}</div>
      </div>

      <!-- ⑧ 操作日志（只增不改不删） -->
      <el-divider content-position="left">{{ $tt("操作日志") }}</el-divider>
      <el-table :data="logs" border size="small" max-height="260" :empty-text="$tt('暂无操作记录')">
        <el-table-column prop="operateTime" :label="$tf('时间')" width="165">
          <template #default="{ row }">{{ fmtTime(row.operateTime) }}</template>
        </el-table-column>
        <el-table-column prop="actionType" :label="$tf('动作')" width="100" />
        <el-table-column :label="$tf('状态变化')" width="170">
          <template #default="{ row }">
            <span>{{ $dt(row.fromStatus || '—') }}</span>
            <el-icon class="log-arrow"><Right /></el-icon>
            <span :style="{ color: opStatusColor(row.toStatus), fontWeight: 600 }">{{ $dt(row.toStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('数量')" width="90" align="right">
          <template #default="{ row }">{{ row.quantity == null ? '' : num(row.quantity) }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" :label="$tf('操作人')" width="110" />
        <el-table-column prop="remark" :label="$tf('备注')" min-width="120" show-overflow-tooltip />
      </el-table>

    </div>

    <template #footer>
      <el-button @click="visible = false">{{ $tt("关闭") }}</el-button>
    </template>
  </el-dialog>

  <!-- 第 12 期：填质检弹窗 —— 按取到的模板渲染检验项；按质检类型切换三种表单形态
       首检：只录一次实测值；抽检：抽样数 + 允许不良率 + 实测值；全检：合格数/不合格数 + 实测值 -->
  <el-dialog v-model="qcVisible" :title="$tt('填质检')" width="820px" append-to-body>
    <div v-if="qcOp" class="qc-panel">
      <div class="qc-head">
        <span class="qc-opname">{{ qcOp.operationCode }} · {{ qcOp.operationName }}</span>
        <el-tag size="small" effect="plain">{{ $tt("质检类型：") }}{{ $dt(qcType) }}</el-tag>
        <el-tag v-if="qcTemplate" size="small" type="info" effect="plain">{{ $tt("模板：") }}{{ qcTemplate.templateName }}</el-tag>
      </div>
      <div class="qc-tip">
        <template v-if="qcType === '首检'">{{ $tt("首检：只检首件/首批。检验项全部合格 → 整批放行（合格数 = 报工数量）；不合格 → 走不合格处理。") }}</template>
        <template v-else-if="qcType === '抽检'">{{ $tt("抽检：抽样数 = 报工数量 × 抽样比例（可手工改）。不良率 ≤ 允许不良率 → 整批放行（合格数 = 报工数量 − 判定不良数）；超出 → 走不合格处理。") }}</template>
        <template v-else>{{ $tt("全检：逐件全检。合格数 = 实测合格数，不合格数进不合格处理。") }}</template>
      </div>

      <el-form :inline="true" size="small" class="qc-meta">
        <el-form-item v-if="qcType === '抽检'" :label="$tf('抽样数')">
          <el-input-number v-model="qcForm.inspectQty" :min="0" :precision="scaleFor('qty')" :controls="false" style="width: 130px" />
        </el-form-item>
        <el-form-item v-if="qcType === '抽检'" :label="$tf('允许不良率(%)')">
          <span class="qc-const">{{ num(qcTemplate && qcTemplate.allowedDefectRatio) }}</span>
        </el-form-item>
        <el-form-item v-if="qcType === '全检'" :label="$tf('合格数')">
          <el-input-number v-model="qcForm.qualifiedQty" :min="0" :precision="scaleFor('qty')" :controls="false" style="width: 130px" />
        </el-form-item>
        <el-form-item v-if="qcType === '全检'" :label="$tf('不合格数')">
          <el-input-number v-model="qcForm.defectQty" :min="0" :precision="scaleFor('qty')" :controls="false" style="width: 130px" />
        </el-form-item>
      </el-form>

      <el-table :data="qcItems" border size="small" max-height="260" :empty-text="$tt('该模板还没有检验项')">
        <el-table-column type="index" :label="$tf('行号')" width="60" />
        <el-table-column prop="itemName" :label="$tf('检验项')" min-width="130" />
        <el-table-column prop="inspectMethod" :label="$tf('检验方法')" width="120" show-overflow-tooltip />
        <el-table-column prop="standardValue" :label="$tf('标准值')" width="130" show-overflow-tooltip />
        <el-table-column :label="$tf('下限/上限')" width="130" align="center">
          <template #default="{ row }">{{ row.lowerLimit ?? '' }} / {{ row.upperLimit ?? '' }}</template>
        </el-table-column>
        <el-table-column prop="unit" :label="$tf('单位')" width="70" />
        <el-table-column :label="$tf('必填')" width="70" align="center">
          <template #default="{ row }">{{ row.isRequired === 1 ? $tt('是') : $tt('否') }}</template>
        </el-table-column>
        <el-table-column :label="$tf('实测值')" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.measuredValue" size="small" :placeholder="$tt('填写实测值')" />
          </template>
        </el-table-column>
        <!-- 【第 19 期】逐项留证：可为每个检验项上传/拍照（不强制，可多张） -->
        <el-table-column :label="$tf('留证（可拍照/上传）')" min-width="200">
          <template #default="{ row }">
            <div class="qc-ev">
              <span v-for="(u, i) in parseUrls(row.images)" :key="u + i" class="qc-ev-item">
                <img :src="u" class="qc-ev-img" @click="previewQcImage(row, i)" />
                <span class="qc-ev-del" :title="$tt('删除')" @click="removeQcImage(row, i)">×</span>
              </span>
              <el-upload
                :show-file-list="false"
                :http-request="(opt) => doQcUpload(opt, row)"
                :before-upload="beforeQcUpload"
                accept="image/*"
                multiple
              >
                <span class="qc-ev-add" :title="$tt('上传图片')">+</span>
              </el-upload>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-image-viewer
        v-if="qcViewerVisible"
        :url-list="qcViewerList"
        :initial-index="qcViewerIndex"
        @close="qcViewerVisible = false"
      />

      <!-- 不合格处理（判定不合格时必填） -->
      <el-divider content-position="left">{{ $tt("不合格处理方式（判定不合格时必填）") }}</el-divider>
      <el-form label-width="110px" size="small">
        <el-form-item :label="$tf('处理方式')">
          <el-radio-group v-model="qcForm.handleType">
            <el-radio-button value="补料">{{ $tt("补料") }}</el-radio-button>
            <el-radio-button value="返工回退">{{ $tt("返工回退") }}</el-radio-button>
            <el-radio-button value="报废">{{ $tt("报废") }}</el-radio-button>
          </el-radio-group>
          <span class="qc-hint">{{ $tt("补料=按BOM生成补料领料单；返工回退=退回指定工序重做；报废=记为报废") }}</span>
        </el-form-item>
        <el-form-item v-if="qcForm.handleType === '返工回退'" :label="$tf('返工目标工序')">
          <el-select v-model="qcForm.handleOperationName" filterable clearable style="width: 260px" :placeholder="$tt('选择退回重做的工序')">
            <el-option v-for="s in qcSiblings" :key="s.id" :label="`${s.lineNo || ''} ${s.operationName || ''}`" :value="s.operationName" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$tf('处理备注')">
          <el-input v-model="qcForm.handleRemark" style="width: 320px" :placeholder="$tt('选填')" />
        </el-form-item>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="qcForm.remark" style="width: 320px" :placeholder="$tt('选填')" />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="qcVisible = false">{{ $tt("取消") }}</el-button>
      <el-button type="primary" :loading="qcSubmitting" @click="submitQc">{{ $tt("提交检验") }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Right, Upload, Rank, Document, Setting } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import {
  getOperationLogs, getOperationStatusMeta, actOperation, notifyOperation,
  confirmOperationRework, confirmOperationScrap, pushOperationSupplement, pushOperationReceipt,
  getOperationQcTemplate, submitOperationQc, listWorkOrderOperation, getOperationAssignPermission,
  getOperationWorkerDetail, claimOperation, unclaimOperation, reportOperation, undoOperationReport,
  setOperationAssignees
} from '@/api/production/workOrderOperation'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllMaterial, getMaterial } from '@/api/basedata/material'
import { getRouting } from '@/api/production/routing'
import { parseUrls, joinUrls, uploadFiles } from '@/api/common/upload'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { loadCustomFieldDefinitions, loadCustomFieldValues } from '@/composables/useCustomField'
import { useColumnConfigStore } from '@/stores/columnConfig'
import { useColWidth } from '@/composables/useColWidth'
import { opStatusStyle, opStatusColor } from '@/utils/operationStatus'
import { tt } from '@/i18n'
import { usePrecision } from '@/composables/usePrecision'

// 数量输入/显示的位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor } = usePrecision()

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  operation: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const op = ref(null)
const logs = ref([])
const acting = ref(false)
// 【2026-11-25】生产工序自定义字段：定义来自设置中心「生产工序」，值按工序行ID存取
const cfDefs = ref([])
const cfValues = ref({})
const cfFormulaFields = ref([])
/** 正在提交的动作码（用于给对应按钮显示 loading，不影响其它按钮） */
const activeCode = ref('')
const employees = ref([])
// 状态 → 允许动作（后端下发，避免前后端各写一份流转表）
const statusMeta = ref({})
// 状态动作表单：inputQuantity/quantity/rejectedQuantity 用于「数量类型」，amount 用于「金额类型」
const form = reactive({ assigneeId: null, assigneeIds: [], planEndDate: '', inputQuantity: 0, quantity: 0, rejectedQuantity: 0, amount: null, remark: '', attachments: '' })
// 【2026-10-25】共同负责人（扫码认领）与我的报工数据：能不能认领、原因、我的认领/已报/剩余
const workerInfo = reactive({ workers: [], canClaim: false, reason: '', myClaim: null, myReported: 0, myRemaining: null, remainingClaimable: null, myJoined: false })
const claiming = ref(false)
const assigning = ref(false)
const claimQty = ref(null)
// 第 11 期：入库类型工序的入库数量（独立于状态动作表单，走「发起入库单」接口）
const receiptForm = reactive({ quantity: 0 })
// 第 4 期：回退 / 判废 —— 只记数量、不改工序状态，因此独立于上面的状态动作表单
const reworkForm = reactive({ quantity: 0 })
const scrapForm = reactive({ quantity: 0 })
// 附件上传中标记
const uploading = ref(false)
// 工序通知（@下道工序负责人）
const notifying = ref(false)
const nextOperation = ref(null)
const prevOperation = ref(null)
const notifyForm = reactive({ receiverIds: [], content: '' })
// 本工单全部工序行（用于取下道工序 / 上一道工序）
const siblingOps = ref([])
// 「投入自动带出上道产出」开关（工艺路线设置；取不到按 1 = 开）
const autoInputFromPrev = ref(1)
// 状态元数据按「工序类型」缓存：动作 = 状态 ∩ 工序类型（后端过滤，前端不自己算）
const metaCache = {}
/** 可下推补料量 = 判废数 − 已下推数（≤0 时「下推补料」按钮置灰，避免重复补料） */
const pendingScrap = computed(() => {
  const s = Number(op.value?.scrapQuantity || 0)
  const p = Number(op.value?.scrapPushedQuantity || 0)
  const v = s - p
  return v > 0 ? Number(v.toFixed(4)) : 0
})
/** 剩余可入库量（入库类型）= 合格产出 − 已入库量（≥0） */
const remainingReceiptQty = computed(() => {
  const out = Number(op.value?.outputQuantity || 0)
  const inQty = Number(op.value?.receivedQty || 0)
  const v = out - inQty
  return v > 0 ? Number(v.toFixed(4)) : 0
})

// ===== 第 12 期：填质检（需质检工序在「完工待检」时出现「填质检」按钮） =====
const qcVisible = ref(false)
const qcOp = ref(null)
const qcTemplate = ref(null)
const qcItems = ref([])
const qcSiblings = ref([])
const qcSubmitting = ref(false)
const qcForm = reactive({
  inspectQty: 0, qualifiedQty: 0, defectQty: 0,
  handleType: '', handleOperationName: '', handleRemark: '', remark: ''
})

// 【第 19 期】逐项留证图片：查看器 + 上传/删除（复用系统通用上传，结果写回该检验项的 images）
const qcViewerVisible = ref(false)
const qcViewerList = ref([])
const qcViewerIndex = ref(0)
function previewQcImage (row, idx) {
  qcViewerList.value = parseUrls(row.images)
  qcViewerIndex.value = idx
  qcViewerVisible.value = true
}
/** 留证只收图片；单张 ≤ 20MB */
function beforeQcUpload (file) {
  if (!/^image\//.test(file.type || '')) { ElMessage.error(tt('留证只能上传图片')); return false }
  if (file.size / 1024 / 1024 >= 20) { ElMessage.error(tt('单张图片不能超过 20MB')); return false }
  return true
}
async function doQcUpload (opt, row) {
  try {
    const res = await uploadFiles([opt.file], 'qc-record')
    const urls = (Array.isArray(res) ? res : [res]).filter(u => typeof u === 'string' && u)
    if (!urls.length) { ElMessage.error(tt('上传失败')); return }
    row.images = joinUrls([...parseUrls(row.images), ...urls])
    ElMessage.success(tt('已添加留证图片'))
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  }
}
function removeQcImage (row, idx) {
  const list = parseUrls(row.images)
  list.splice(idx, 1)
  row.images = joinUrls(list)
}
/** 「填质检」按钮显示条件：该工序需质检 + 当前状态为「完工待检」 */
const canFillQc = computed(() => op.value?.isQcRequired === 1 && op.value?.operationStatus === '完工待检')
/** 质检类型（由模板决定表单形态）：首检 / 抽检 / 全检 */
const qcType = computed(() => (qcTemplate.value && qcTemplate.value.qcType) || '首检')

const style = computed(() => opStatusStyle(op.value?.operationStatus))
/** 工序类型（默认「状态类型」）：决定操作区渲染哪套输入（第 11 期） */
const opType = computed(() => op.value?.operationType || '状态类型')
/** 终态（已完工 / 已取消）：整区只读 + 隐藏计划保存 */
const isTerminal = computed(() => ['已完工', '已取消'].includes(op.value?.operationStatus))
/** 按类型操作区可编辑：仅「已派工 / 已开工 / 进行中」可写（终态、待派工、暂停、完工待检只读） */
const typeAreaEditable = computed(() => ['已派工', '已开工', '进行中'].includes(op.value?.operationStatus))
/**
 * 【2026-10-30】数量报工是否可填/可报：常规为「已派工 / 已开工 / 进行中」；
 * 历史数据已认领但状态仍停在「待派工」时同样放行（提交时后端自动把状态推进为「已派工」），
 * 避免用户"认领了却找不到填数量的地方、报工按钮点不动"。
 */
const reportNowAllowed = computed(() => {
  if ((op.value?.operationType || '') !== '数量类型') return false
  const st = op.value?.operationStatus
  if (['已派工', '已开工', '进行中'].includes(st)) return true
  return st === '待派工' && workerInfo.myJoined === true
})
/** 备注可写：终态与「完工待检」只读 */
const remarkEditable = computed(() => !['完工待检', '已完工', '已取消'].includes(op.value?.operationStatus))
/** 【2026-11-25】工序自定义字段可写：与备注同口径（终态与「完工待检」只读） */
const cfEditable = computed(() => remarkEditable.value)
/** 工序自定义字段：下拉选项解析（与设置中心定义同一份 JSON） */
function cfOptions (f) {
  try {
    const arr = JSON.parse(f.fieldOptions || '[]')
    return Array.isArray(arr) ? arr : []
  } catch (e) { return [] }
}
/** 【2026-11-26】值表存文本，勾选回读 "1" 需归一化为数字 1，否则开关显示未勾选；数字回读转 Number */
function normalizeCfValue (f, v) {
  if (v === undefined || v === null || v === '') return v
  if (f.fieldType === 'switch') return (v === 1 || v === '1' || v === true || v === 'true') ? 1 : 0
  if (f.fieldType === 'number' || f.fieldType === 'formula') {
    const n = Number(v)
    return Number.isNaN(n) ? v : n
  }
  return v
}
/** 计划调整可编辑：终态与「完工待检」只读 */
const planEditable = computed(() => !['完工待检', '已完工', '已取消'].includes(op.value?.operationStatus))
/** 【第 4 版】当前登录人能否改负责人（后端按工艺路线档位判定）；null=未取到，按"可改"兜底（后端仍是最终门槛） */
const canAssign = ref(null)
/** 【第 4 版】负责人下拉可改 = 计划可编辑 且 有权改负责人 */
const assignEditable = computed(() => planEditable.value && canAssign.value !== false)
/** 附件只读：终态（已完工 / 已取消）不可传不可删 */
const attachReadonly = computed(() => isTerminal.value)
/** 下道工序（同工单内行号大于本道的最近一道） */
const hasNextOperation = computed(() => !!nextOperation.value)
const nextAssigneeName = computed(() => nextOperation.value?.assigneeName || '')
/** 完工进度（%）：已报工 / 计划数量 */
const progressPercent = computed(() => {
  const plan = Number(op.value?.planQuantity || 0)
  if (!(plan > 0)) return 0
  const p = Number(op.value?.reportedQuantity || 0) / plan * 100
  return Math.max(0, Math.min(100, Number(p.toFixed(1))))
})
// ===== 工时体系（2026-10-07）：实际工时由后端自动累计，前端只读展示 =====
/** 实际工时（小时，累计值）：空 / 脏数据按 0 */
const actualHoursNum = computed(() => {
  const v = op.value?.actualHours
  if (v === null || v === undefined || v === '') return 0
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
})
/** 实际工时展示：保留 2 位小数 + 单位 h（如 2.50 h） */
const actualHoursText = computed(() => `${actualHoursNum.value.toFixed(2)} h`)
/** 计时状态：timingSince 非空 = 计时中（自 MM-DD HH:mm）；为空 = 已停表；脏数据（仅日期无时间）只显示「计时中」 */
const timingState = computed(() => {
  const s = op.value?.timingSince
  if (s === null || s === undefined || s === '') return { on: false, text: tt('已停表') }
  const m = String(s).match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  if (m) return { on: true, text: `${tt('计时中（自 ')}${m[2]}-${m[3]} ${m[4]}:${m[5]}${tt('）')}` }
  return { on: true, text: tt('计时中') }
})
/** 计划工时 = 准备工时 + 加工工时（小时） */
const planHoursNum = computed(() => {
  const setup = Number(op.value?.setupHours)
  const process = Number(op.value?.processHours)
  return (Number.isFinite(setup) ? setup : 0) + (Number.isFinite(process) ? process : 0)
})
const planHoursText = computed(() => `${planHoursNum.value.toFixed(2)} h`)
/** 误差 = 实际 − 计划；计划为 0/空 → null（前端整段留空，不显示 NaN/Infinity/-） */
const diffHours = computed(() => (planHoursNum.value > 0 ? actualHoursNum.value - planHoursNum.value : null))
const diffHoursText = computed(() => (diffHours.value == null ? '' : `${diffHours.value.toFixed(2)} h`))
/** 误差率 = 误差 ÷ 计划 × 100；计划为 0/空 → 空 */
const diffRateText = computed(() => (diffHours.value == null ? '' : `${(diffHours.value / planHoursNum.value * 100).toFixed(1)}%`))
/** 误差配色：超出计划(正) = 红，快于计划(负) = 绿，持平 = 中性 */
const diffClass = computed(() => {
  if (diffHours.value == null) return ''
  if (diffHours.value > 0) return 'cmp-slow'
  if (diffHours.value < 0) return 'cmp-fast'
  return 'cmp-flat'
})
/** 完成后自动通知（只读展示）：取自工序所属工艺路线的开关；未携带时按「关」 */
const autoNotify = computed(() => Number(op.value?.autoNotifyNext ?? op.value?.routingAutoNotifyNext ?? 0) === 1)

/** 报工组（只渲染当前状态可执行的）：开工 / 报工 / 完工 / 撤销完工 / 暂停 / 恢复 */
const REPORT_BUTTONS = [
  { code: '开工', label: '开工' },
  { code: '数量报工', label: '报工' },
  { code: '完工', label: '完工' },
  // 撤销完工（2026-10-07 用户口径）：已完工填错了要能改 —— 退回「进行中」，可点否由后端状态元数据下发
  { code: '撤销完工', label: '撤销完工' },
  { code: '暂停', label: '暂停' },
  { code: '恢复', label: '恢复' }
]
/** 质检组：填质检 / 检验通过 / 检验退回 */
const QC_BUTTONS = [
  { code: '填质检', label: '填质检', special: 'qc' },
  { code: '检验通过', label: '检验通过' },
  { code: '检验退回', label: '检验退回' }
]
/** 异常处理组（默认收起，避免误点）：取消工序 / 恢复工序 */
const ABNORMAL_BUTTONS = [
  { code: '取消', label: '取消工序' },
  { code: '恢复工序', label: '恢复工序' }
]
/** 是否取到状态元数据（取不到时动作区提示「暂不可操作」） */
const hasMeta = computed(() => Array.isArray(statusMeta.value?.states) && statusMeta.value.states.length > 0)
/** 当前状态允许的动作（后端下发，前端不自算） */
const currentActions = computed(() => {
  const st = op.value?.operationStatus || '待派工'
  const row = (statusMeta.value.states || []).find(s => s.status === st)
  return row ? (row.actions || []) : []
})
/** 是否有可撤销的报工：报工数量 > 0，且不在「已完工 / 完工待检 / 已取消」（后端同样拦截） */
const hasReportRecord = computed(() => {
  const st = op.value?.operationStatus
  if (['已完工', '完工待检', '已取消'].includes(st)) return false
  return Number(op.value?.reportedQuantity || 0) > 0
})
/** 报工组：当前可执行的 + 有报工记录才出现的「撤销上次报工」 */
const reportActions = computed(() => {
  const list = REPORT_BUTTONS.filter(b => isActionEnabled(b))
  if (hasReportRecord.value) list.push({ code: '撤销上次报工', label: '撤销上次报工', special: 'undo' })
  return list
})
/** 质检组：只渲染当前可执行的 */
const qcActions = computed(() => QC_BUTTONS.filter(b => isActionEnabled(b)))
/** 异常处理组：只渲染当前可执行的 */
const abnormalActions = computed(() => ABNORMAL_BUTTONS.filter(b => isActionEnabled(b)))
/** 异常处理折叠条副标题：只列出本次真正能用的项（没有可执行项时整条不渲染） */
const abnormalHint = computed(() => {
  const list = abnormalActions.value.map(b => b.label)
  if (opType.value === '数量类型' && typeAreaEditable.value) list.push('回退确认', '判废确认')
  return list.join(' / ')
})
/** 单据下推：入库类型且还有可入库量才出现「发起入库单」 */
const canPushReceipt = computed(() => opType.value === '入库类型' && typeAreaEditable.value && remainingReceiptQty.value > 0)
/** 单据下推：数量类型且有可下推补料量才出现「下推补料」 */
const canPushSupplement = computed(() => opType.value === '数量类型' && typeAreaEditable.value && pendingScrap.value > 0)
/** 单据下推组：两项都没有时不渲染整组 */
const pushActions = computed(() => {
  const list = []
  if (canPushReceipt.value) list.push('发起入库单')
  if (canPushSupplement.value) list.push('下推补料')
  return list
})
/** 「保存」可点条件 = 该动作在当前状态的 actions 里（终态里没有它 → 自动置灰） */
const saveEnabled = computed(() => currentActions.value.includes('保存'))
/** 某个动作按钮是否可执行：填质检走专属条件，其余看 actions 是否包含该动作码 */
function isActionEnabled (b) {
  if (b.special === 'qc') return canFillQc.value
  // 【第 19 期】需质检工序在「完工待检」时：「检验通过 / 检验退回」必须走「填质检」，
  // 后端已硬拦（先填质检才允许判定），前端同步不渲染，避免"点了报错"。
  if (canFillQc.value && (b.code === '检验通过' || b.code === '检验退回')) return false
  // 【2026-10-30】数量报工：除状态允许外，已认领但状态仍待派工的历史工序也放行
  if (b.code === '数量报工') return reportNowAllowed.value
  return currentActions.value.includes(b.code)
}
/** 点动作按钮：填质检走弹窗、撤销上次报工走专属提交，其余走统一提交 */
function onActionButton (b) {
  if (b.special === 'qc') { openQc(); return }
  if (b.special === 'undo') { doUndoReport(); return }
  executeAction(b.code, b.label)
}

// ===== 附件 =====
/** 附件列表（解析逗号分隔 URL） */
const attachList = computed(() => parseUrls(form.attachments).map(url => ({
  url,
  name: fileNameOf(url),
  isImage: /\.(jpg|jpeg|png|gif|bmp|webp|svg)$/i.test(url)
})))
const attachImageUrls = computed(() => attachList.value.filter(a => a.isImage).map(a => a.url))
const viewerVisible = ref(false)
const viewerIndex = ref(0)
// 生产工单附件（只读带入）：图片走独立预览器，其它文件直接下载
const billAttachList = computed(() => parseUrls(op.value?.billAttachments).map(url => ({
  url,
  name: fileNameOf(url),
  isImage: /\.(jpg|jpeg|png|gif|bmp|webp|svg)$/i.test(url)
})))
const billImageUrls = computed(() => billAttachList.value.filter(a => a.isImage).map(a => a.url))
const billViewerVisible = ref(false)
const billViewerIndex = ref(0)
function previewBillAttachment (a) {
  if (a.isImage) {
    const i = billImageUrls.value.indexOf(a.url)
    billViewerIndex.value = i >= 0 ? i : 0
    billViewerVisible.value = true
  } else {
    const link = document.createElement('a')
    link.href = a.url
    link.download = a.name || ''
    link.target = '_blank'
    link.click()
  }
}
function fileNameOf (url) {
  const raw = url.substring(url.lastIndexOf('/') + 1)
  try { return decodeURIComponent(raw) } catch (_) { return raw }
}
function beforeUpload (file) {
  const okExt = /\.(png|jpe?g|gif|bmp|webp|pdf|txt|csv|docx?|xlsx?|pptx?|zip|rar|7z)$/i.test(file.name || '')
  if (!okExt) { ElMessage.error(tt('不支持的文件类型（允许：图片/文档/压缩包）')); return false }
  if (file.size / 1024 / 1024 >= 20) { ElMessage.error(tt('单个文件不能超过 20MB')); return false }
  return true
}
/** 上传（复用系统通用上传封装 uploadFiles，不硬写上传协议）；上传结果暂存，随保存/动作落库 */
async function doUpload (opt) {
  if (attachList.value.length >= 20) { ElMessage.warning(tt('单道工序最多 20 个附件')); return }
  uploading.value = true
  try {
    const res = await uploadFiles([opt.file], 'work-order-operation')
    const urls = (Array.isArray(res) ? res : [res]).filter(u => typeof u === 'string' && u)
    if (!urls.length) { ElMessage.error(tt('上传失败')); return }
    form.attachments = joinUrls([...parseUrls(form.attachments), ...urls])
    ElMessage.success(tt('上传成功'))
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    uploading.value = false
  }
}
function removeAttachment (idx) {
  const list = parseUrls(form.attachments)
  list.splice(idx, 1)
  form.attachments = joinUrls(list)
}
function previewOrDownload (a) {
  if (a.isImage) {
    const i = attachImageUrls.value.indexOf(a.url)
    viewerIndex.value = i >= 0 ? i : 0
    viewerVisible.value = true
  } else {
    const link = document.createElement('a')
    link.href = a.url
    link.download = a.name || ''
    link.target = '_blank'
    link.click()
  }
}

// ===== ② 工序明细 / 物料明细（表头一行列名 + 值一行；列由「列设置」统一配置） =====
const mcf = useMaterialCustomFields()
const mcfReady = ref(false)
const material = ref(null)
const materialListCache = ref(null)
/** 工序明细列配置 key */
const OP_PAGE_KEY = 'OperationPanelOpCols'
/** 物料明细列配置 key（沿用旧 key，用户已保存的设置继续生效） */
const MAT_PAGE_KEY = 'OperationPanelMaterial'
const columnStore = useColumnConfigStore()
/** 工序明细候选列 */
const opFieldDefs = ref([])
/** 物料明细候选列（基础字段 + 物料自定义字段） */
const matFieldDefs = ref([])

function disp (v) {
  if (v === null || v === undefined) return ''
  const t = typeof v
  if (t === 'string') return v
  if (t === 'number' || t === 'boolean') return String(v)
  return ''
}
function flagTxt (v) {
  if (v === null || v === undefined || v === '') return ''
  return Number(v) === 1 ? tt('是') : tt('否')
}
function numTxt (v, digits) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  return Number.isFinite(n) ? String(Number(n.toFixed(digits))) : ''
}
/** 工序明细列：默认只显示最常用的 10 列，其余在「列设置」里勾开 */
function buildOpFields () {
  opFieldDefs.value = [
    { prop: 'operationCode', label: '工序编码', get: o => infoText(o.operationCode) },
    { prop: 'operationName', label: '工序名称', get: o => infoText(o.operationName) },
    { prop: 'lineNo', label: '工序号', num: true, get: o => (o.lineNo == null || o.lineNo === '' ? '' : String(o.lineNo)) },
    { prop: 'workCenterName', label: '工作中心', get: o => infoText(o.workCenterName) },
    { prop: 'assigneeName', label: '负责人', get: o => infoText(o.assigneeName) },
    { prop: 'planEndDate', label: '计划完工', get: o => infoText(o.planEndDate) },
    { prop: 'planQuantity', label: '计划数量', num: true, get: o => numTxt(o.planQuantity, 4) },
    { prop: 'reportedQuantity', label: '已汇报', num: true, get: o => numTxt(o.reportedQuantity, 4) },
    { prop: 'outputQuantity', label: '合格', num: true, get: o => numTxt(o.outputQuantity, 4) },
    { prop: 'operationStatus', label: '状态', get: o => infoText(o.operationStatus || '待派工') },
    { prop: 'operationType', label: '工序类型', defaultHidden: true, get: o => infoText(o.operationType || '状态类型') },
    { prop: 'orderNo', label: '工单号', defaultHidden: true, get: o => infoText(o.orderNo) },
    { prop: 'rejectedQuantity', label: '报废', num: true, defaultHidden: true, get: o => numTxt(o.rejectedQuantity, 4) },
    { prop: 'inputQuantity', label: '投入', num: true, defaultHidden: true, get: o => numTxt(o.inputQuantity, 4) },
    { prop: 'reworkQuantity', label: '回退', num: true, defaultHidden: true, get: o => numTxt(o.reworkQuantity, 4) },
    { prop: 'scrapQuantity', label: '判废', num: true, defaultHidden: true, get: o => numTxt(o.scrapQuantity, 4) }
  ]
}
// 工序列定义是静态的，setup 时就绪（物料自定义字段列在 mcf 初始化后追加）
buildOpFields()
/** 物料明细列 */
function buildMatFields () {
  const base = [
    { prop: 'materialCode', label: '物料编码', get: m => disp(m.materialCode) },
    { prop: 'materialName', label: '物料名称', get: m => disp(m.materialName) },
    { prop: 'model', label: '规格型号', get: m => disp(m.model) },
    { prop: 'baseUnitName', label: '单位', get: m => disp(m.baseUnitName || m.unitName) },
    { prop: 'categoryName', label: '物料分类', get: m => disp(m.categoryName) },
    { prop: 'fullName', label: '全称', defaultHidden: true, get: m => disp(m.fullName) },
    { prop: 'isLotControl', label: '批次管理', defaultHidden: true, get: m => flagTxt(m.isLotControl) },
    { prop: 'isSnControl', label: '序列号管理', defaultHidden: true, get: m => flagTxt(m.isSnControl) },
    { prop: 'expiryDays', label: '保质期(天)', defaultHidden: true, get: m => disp(m.expiryDays) },
    { prop: 'minStock', label: '安全库存', defaultHidden: true, get: m => numTxt(m.minStock, 4) },
    { prop: 'defaultWarehouseName', label: '默认仓库', defaultHidden: true, get: m => disp(m.defaultWarehouseName) },
    { prop: 'defaultSupplierName', label: '默认供应商', defaultHidden: true, get: m => disp(m.defaultSupplierName) },
    { prop: 'purchasePrice', label: '采购价', defaultHidden: true, get: m => numTxt(m.purchasePrice, 2) },
    { prop: 'salePrice', label: '销售价', defaultHidden: true, get: m => numTxt(m.salePrice, 2) },
    { prop: 'taxRate', label: '税率(%)', defaultHidden: true, get: m => numTxt(m.taxRate, 2) },
    { prop: 'remark', label: '备注', defaultHidden: true, get: m => disp(m.remark) }
  ]
  const cf = (mcf.allFields.value || []).map(f => ({
    prop: `mcf_${f.fieldKey}`,
    label: f.fieldLabel,
    get: (m) => {
      const id = m?.materialId
      if (id == null) return ''
      const vals = mcf.valueMap.value[id]
      return vals ? disp(vals[f.fieldKey]) : ''
    }
  }))
  matFieldDefs.value = [...base, ...cf]
}
/** 某一列是否可见（配置来自 columnConfig store，按登录人+公司隔离保存） */
function colVisible (key, defs, prop) {
  const cfg = columnStore.getPageConfig(key)
  const c = cfg[prop]
  if (c) return c.visible !== false
  const def = defs.find(f => f.prop === prop)
  return !(def && def.defaultHidden)
}
/** 按用户配置顺序排序后的列定义 */
function sortedCols (key, defs) {
  void columnStore.version
  const cfg = columnStore.getPageConfig(key)
  return [...defs]
    .map((f, idx) => ({ ...f, _order: cfg[f.prop]?.order ?? (idx + 10000) }))
    .sort((a, b) => a._order - b._order)
}
/** 工序明细实际渲染的列（值为空留白；不删列，保证表头与单元格一一对齐） */
const opShownCols = computed(() => {
  if (!op.value) return []
  return sortedCols(OP_PAGE_KEY, opFieldDefs.value)
    .filter(f => colVisible(OP_PAGE_KEY, opFieldDefs.value, f.prop))
    .map(f => {
      let v
      try { v = f.get(op.value) } catch (e) { /* 单列取值失败不影响其它列 */ }
      return { prop: f.prop, label: f.label, num: !!f.num, value: v == null ? '' : String(v) }
    })
})
/** 物料明细实际渲染的列 */
const matShownCols = computed(() => {
  if (!material.value) return []
  return sortedCols(MAT_PAGE_KEY, matFieldDefs.value)
    .filter(f => colVisible(MAT_PAGE_KEY, matFieldDefs.value, f.prop))
    .map(f => {
      let v
      try { v = f.get(material.value) } catch (e) { /* 单列取值失败不影响其它列 */ }
      return { prop: f.prop, label: f.label, value: v == null ? '' : String(v) }
    })
})

// ===== 工序明细 / 物料明细两张表的列宽拖拽 + 持久化（2026-10-08）=====
// 沿用各自已有的列设置 pageKey：宽度存在 columns[prop].width，与列显隐/顺序同一条记录、互不覆盖
const opColWidth = useColWidth(OP_PAGE_KEY)
const matColWidth = useColWidth(MAT_PAGE_KEY)
const opTableRef = ref(null)
const matTableRef = ref(null)
/** 单行数据（表头 + 值一行）：el-table 需要数组 data */
const opDetailRows = computed(() => (op.value && opShownCols.value.length ? [op.value] : []))
const matDetailRows = computed(() => (material.value && matShownCols.value.length ? [material.value] : []))
function restoreDetailWidths () {
  nextTick(() => {
    opColWidth.applySavedWidths(opTableRef)
    matColWidth.applySavedWidths(matTableRef)
  })
}
watch(() => props.modelValue, (v) => { if (v) restoreDetailWidths() })
watch(opShownCols, restoreDetailWidths)
watch(matShownCols, restoreDetailWidths)
const materialImages = computed(() => {
  const m = material.value
  if (!m) return []
  const urls = parseUrls(m.imageUrls)
  if (!urls.length && m.mainImage) urls.push(m.mainImage)
  return urls
})

// ===== 列设置（工序明细列 + 物料明细列，一处配置；跟随按钮自适应，不用固定定位） =====
const colSettingVisible = ref(false)
const opSettingLocal = ref([])
const matSettingLocal = ref([])
function buildSettingLocal (key, defs) {
  const cfg = columnStore.getPageConfig(key)
  return defs.map((f, idx) => ({
    prop: f.prop,
    label: f.label,
    visible: cfg[f.prop] ? cfg[f.prop].visible !== false : !f.defaultHidden,
    order: cfg[f.prop]?.order ?? idx
  })).sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
}
watch(colSettingVisible, (v) => {
  if (!v) return
  opSettingLocal.value = buildSettingLocal(OP_PAGE_KEY, opFieldDefs.value)
  matSettingLocal.value = buildSettingLocal(MAT_PAGE_KEY, matFieldDefs.value)
})
function saveColSetting () {
  const opCfg = {}
  opSettingLocal.value.forEach((c, idx) => { opCfg[c.prop] = { visible: c.visible, order: idx } })
  columnStore.setPageConfig(OP_PAGE_KEY, opCfg)
  const matCfg = {}
  matSettingLocal.value.forEach((c, idx) => { matCfg[c.prop] = { visible: c.visible, order: idx } })
  columnStore.setPageConfig(MAT_PAGE_KEY, matCfg)
  colSettingVisible.value = false
  ElMessage.success(tt('列设置已保存'))
}
function resetColSetting () {
  columnStore.resetPage(OP_PAGE_KEY)
  columnStore.resetPage(MAT_PAGE_KEY)
  opSettingLocal.value = buildSettingLocal(OP_PAGE_KEY, opFieldDefs.value)
  matSettingLocal.value = buildSettingLocal(MAT_PAGE_KEY, matFieldDefs.value)
  ElMessage.success(tt('已重置默认列设置'))
}
async function loadMaterial () {
  material.value = null
  const id = op.value?.materialId
  if (id == null) return
  let m = null
  try {
    if (!materialListCache.value) {
      const res = await listAllMaterial()
      materialListCache.value = Array.isArray(res) ? res : (res?.records || [])
    }
    m = materialListCache.value.find(x => x && x.materialId === id) || null
  } catch (e) { if (!materialListCache.value) materialListCache.value = [] }
  if (!m) { try { m = await getMaterial(id) } catch (e) { m = null } }
  material.value = m
  if (m) { try { await mcf.ensureValues([id]) } catch (e) { /* 降级：不显示自定义字段值 */ } }
}

function num (v) { return v == null ? 0 : Number(v) }
/** 单据信息区：文本为空时显示空白（不显示 -/undefined） */
function infoText (v) { return v == null ? '' : String(v) }
/** 单据信息区：数值为空时显示空白（不显示 0 冒充空值） */
function infoNum (v) {
  if (v == null || v === '') return ''
  const n = Number(v)
  return Number.isFinite(n) ? n : ''
}
function fmtTime (t) { return (t || '').replace('T', ' ').slice(0, 19) }
function round4 (v) { return Number(Number(v || 0).toFixed(4)) }

/** 【2026-11-26】工艺路线工序字段定义 → 面板统一结构（含数字精度） */
function normRoutingDef (d) {
  let precision = 2
  try {
    const opt = d.fieldOptions ? JSON.parse(d.fieldOptions) : null
    if (opt && !Array.isArray(opt) && opt.precision != null) precision = Number(opt.precision)
  } catch (e) { /* 选项脏值按默认精度 */ }
  return {
    fieldKey: d.fieldKey,
    fieldLabel: d.fieldLabel || d.label || '',
    fieldType: d.fieldType || 'text',
    fieldOptions: d.fieldOptions || null,
    isRequired: Number(d.isRequired || 0),
    sortOrder: d.sortOrder != null ? d.sortOrder : (d.sortNo != null ? d.sortNo : 0),
    // 精度以工序字段配置 {"precision":n} 为准；后端回传的默认 2 位只作兜底
    precisionVal: precision,
    placeholder: ''
  }
}

/** 【2026-11-25】工序自定义字段定义（按需加载一次）+ 当前工序已填值 */
async function loadCfDefs () {
  // 【2026-11-26】新口径：工序带 routingOperationKey → 只显示这道工序自己的字段（0 个也不回落全局）
  if (op.value?.routingOperationKey) {
    cfDefs.value = (op.value.operationFieldDefs || []).map(normRoutingDef)
    cfFormulaFields.value = []
    return
  }
  if (cfDefs.value.length) return
  try {
    const result = await loadCustomFieldDefinitions('work_order_operation')
    cfDefs.value = Array.from(result.fieldMap.values())
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    cfFormulaFields.value = result.formulaFields
  } catch (e) {
    cfDefs.value = []
  }
}
async function loadCfValues () {
  cfValues.value = {}
  if (!op.value?.id) return
  try {
    // 新口径：详情接口已带出本工序已填值，不再走全局值查询
    const raw = (op.value.routingOperationKey && op.value.customFields)
      ? op.value.customFields
      : ((await loadCustomFieldValues('work_order_operation', op.value.id)) || {})
    if (!cfDefs.value.length) {
      cfValues.value = raw
      return
    }
    const norm = {}
    cfDefs.value.forEach(f => {
      if (raw[f.fieldKey] !== undefined && raw[f.fieldKey] !== null) {
        norm[f.fieldKey] = normalizeCfValue(f, raw[f.fieldKey])
      }
    })
    cfValues.value = norm
  } catch (e) {
    cfValues.value = {}
  }
}
/** 必填校验（只在「保存」动作拦截，不拦开工/完工，避免卡产线） */
function validateCfRequired () {
  for (const f of cfDefs.value) {
    if (f.isRequired !== 1) continue
    const v = cfValues.value[f.fieldKey]
    if (v === undefined || v === null || v === '') return `请填写「${f.fieldLabel}」`
  }
  return null
}

async function loadMeta (type) {
  const key = type || '状态类型'
  if (metaCache[key]) { statusMeta.value = metaCache[key]; return }
  try {
    // 传入工序类型：后端按「状态 ∩ 工序类型」过滤后下发动作（状态/金额/入库类型不再出现「数量报工」）
    const d = await getOperationStatusMeta(key)
    if (d?.states) { statusMeta.value = d; metaCache[key] = d } else { statusMeta.value = {} }
  } catch (e) { /* 静默降级：拿不到元数据时所有动作按钮置灰，不阻断日志查看 */ statusMeta.value = {} }
}

/** 【第 4 版】取「谁可以改负责人」判定（前端只控制下拉可编辑性，后端才是最终门槛） */
async function loadAssignPermission () {
  if (!op.value?.id) { canAssign.value = true; return }
  try {
    const d = await getOperationAssignPermission(op.value.id)
    canAssign.value = d?.canAssign !== false
  } catch (e) { canAssign.value = true }
}

/** 【2026-10-25】共同负责人 + 我的认领/已报/剩余（口径全在后端，前端只渲染） */
async function loadWorkerInfo () {
  if (!op.value?.id) return
  try {
    const d = await getOperationWorkerDetail(op.value.id)
    if (d) {
      // 【2026-11-26】详情接口同时带出：本工序自己的字段定义/已填值、负责人、附件等
      op.value = {
        ...op.value,
        assigneeId: d.assigneeId ?? op.value.assigneeId,
        assigneeName: d.assigneeName ?? op.value.assigneeName,
        operationFieldDefs: d.operationFieldDefs || op.value.operationFieldDefs,
        customFields: d.customFields || op.value.customFields,
        billAttachments: d.billAttachments || op.value.billAttachments
      }
      if (d.billAttachments) op.value.billAttachments = d.billAttachments
    }
    workerInfo.workers = d?.workers || []
    workerInfo.canClaim = d?.canClaim === true
    workerInfo.reason = d?.claimDisabledReason || ''
    workerInfo.myClaim = d?.myClaimQuantity ?? null
    workerInfo.myReported = d?.myReportedQuantity ?? 0
    workerInfo.myRemaining = d?.myRemainingQuantity ?? null
    workerInfo.remainingClaimable = d?.remainingClaimable ?? null
    workerInfo.myJoined = d?.myJoined === true
    // 负责人多选回显：在岗名单（含主负责人）；为空时回落主负责人
    const ids = (workerInfo.workers || []).map(w => w.employeeId).filter(x => x != null)
    form.assigneeIds = ids.length ? ids : (op.value.assigneeId ? [op.value.assigneeId] : [])
    form.assigneeId = op.value.assigneeId || null
  } catch (e) {
    // 降级：拿不到参与人数据时不影响工序操作本身
    workerInfo.workers = []
    workerInfo.canClaim = false
    workerInfo.reason = ''
    form.assigneeIds = op.value?.assigneeId ? [op.value.assigneeId] : []
  }
}

/** 扫码认领「我来做」：加入为共同负责人（不覆盖系统派工的主负责人） */
async function doClaim () {
  if (!op.value?.id) return
  claiming.value = true
  try {
    const updated = await claimOperation(op.value.id, { claimQuantity: claimQty.value ?? null })
    if (updated) await afterWorkerChange(updated)
    ElMessage.success(tt('已加入本道工序'))
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    claiming.value = false
  }
}

/** 退出认领：已报数量保留，未报份额释放 */
async function doUnclaim () {
  if (!op.value?.id) return
  const { ElMessageBox } = await import('element-plus')
  try {
    await ElMessageBox.confirm(tt('确认退出本道工序？已报数量会保留，未报的份额释放回工序。'), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  claiming.value = true
  try {
    const updated = await unclaimOperation(op.value.id)
    if (updated) await afterWorkerChange(updated)
    ElMessage.success(tt('已退出本道工序'))
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    claiming.value = false
  }
}

/** 认领/退出成功后：只合并认领相关字段，保留面板上的展示字段（物料/看板等） */
async function afterWorkerChange (updated) {
  op.value = {
    ...op.value,
    assigneeId: updated.assigneeId,
    assigneeName: updated.assigneeName,
    operationStatus: updated.operationStatus
  }
  // 负责人下拉同步：认领补为主负责人时显示自己，退出清空主负责人时回到占位（否则同会话仍显示旧值）
  form.assigneeId = updated.assigneeId || null
  const ids = (updated.workers || []).map(w => w.employeeId).filter(x => x != null)
  form.assigneeIds = ids.length ? ids : (updated.assigneeId ? [updated.assigneeId] : [])
  await loadWorkerInfo()
  await refreshLogs()
  emit('changed', op.value)
}

/** 撤销上一次报工（本人当班最近一笔；管理员可撤他人，入口在日志里按需扩展） */
async function doUndoReport () {
  if (!op.value?.id) return
  const { ElMessageBox } = await import('element-plus')
  try {
    await ElMessageBox.confirm(tt('确认撤销最近一笔报工？工序与个人已报数量会同步回退。'), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  acting.value = true
  try {
    const r = await undoOperationReport(op.value.id, {})
    if (r?.operation) op.value = { ...op.value, ...r.operation }
    await loadWorkerInfo()
    await refreshLogs()
    emit('changed', op.value)
    ElMessage.success(tt('已撤销最近一笔报工'))
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    acting.value = false
  }
}

/**
 * 【2026-11-26】负责人多选，选完即存：
 *  - 整份名单一次提交，第一位为主负责人，其余自动成为共同负责人；
 *  - 待派工 → 自动推进为「已派工」；已派工/已开工/进行中/暂停 → 状态不变只换人；
 *  - 清空 → 不下发（派工必须至少一位负责人），回退原名单并提示。
 */
async function onAssigneesChange (ids) {
  if (!op.value) return
  const list = (ids || []).filter(x => x != null)
  const currentIds = (workerInfo.workers || []).map(w => w.employeeId).filter(x => x != null)
  if (!list.length) {
    form.assigneeIds = currentIds.length ? currentIds : (op.value.assigneeId ? [op.value.assigneeId] : [])
    ElMessage.warning(tt('派工必须至少选择一位负责人'))
    return
  }
  // 名单没变（顺序也没变）→ 不重复提交
  if (list.length === currentIds.length && list.every((v, i) => v === currentIds[i])) return
  assigning.value = true
  try {
    const updated = await setOperationAssignees(op.value.id, list)
    if (updated) await afterWorkerChange(updated)
    ElMessage.success(tt('负责人已保存'))
  } catch (e) {
    form.assigneeIds = currentIds.length ? currentIds : (op.value.assigneeId ? [op.value.assigneeId] : [])
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    assigning.value = false
  }
}

async function refreshLogs () {
  if (!op.value) return
  try { logs.value = (await getOperationLogs(op.value.id)) || [] } catch (e) { logs.value = [] }
}

async function onOpen () {
  op.value = props.operation ? { ...props.operation } : null
  if (!op.value) return
  form.assigneeId = op.value.assigneeId || null
  form.assigneeIds = op.value.assigneeId ? [op.value.assigneeId] : []
  form.planEndDate = op.value.planEndDate || ''
  form.inputQuantity = 0
  form.quantity = 0
  form.rejectedQuantity = 0
  form.amount = null
  form.remark = ''
  form.attachments = op.value.attachments || ''
  receiptForm.quantity = 0
  reworkForm.quantity = 0
  scrapForm.quantity = 0
  notifying.value = false
  nextOperation.value = null
  prevOperation.value = null
  notifyForm.receiverIds = []
  notifyForm.content = ''
  await loadMeta(opType.value)
  // 【2026-11-26】先取详情：本工序自己的字段定义/已填值与负责人名单都从详情回来
  await loadWorkerInfo()
  await loadCfDefs()
  await loadCfValues()
  await loadAssignPermission()
  await loadEmployees()
  await loadSiblings()
  await loadRoutingSwitch()
  applyAutoDefaults()
  await loadMaterialConfig()
  await loadMaterial()
  await refreshLogs()
}

async function loadEmployees () {
  if (employees.value.length) return
  try {
    const list = await listAllEmployee()
    employees.value = (Array.isArray(list) ? list : (list?.records || []))
      .map(e => ({ value: e.employeeId, label: e.employeeName }))
  } catch (e) { /* 负责人可空，取不到不影响其它操作 */ }
}

/** 取同工单全部工序行，解析「下道工序」（通知默认接收人）与「上一道工序」（投入自动带出） */
async function loadSiblings () {
  siblingOps.value = []
  try {
    siblingOps.value = (await listWorkOrderOperation(op.value.orderId)) || []
  } catch (e) { siblingOps.value = [] }
  const curLine = Number(op.value.lineNo)
  const withLine = siblingOps.value
    .filter(r => r && r.id !== op.value.id && r.lineNo != null)
    .sort((a, b) => Number(a.lineNo) - Number(b.lineNo))
  if (op.value.lineNo == null) {
    nextOperation.value = null
    notifyForm.receiverIds = []
    notifyForm.content = buildNotifyTemplate()
    return
  }
  const after = withLine.filter(r => Number(r.lineNo) > curLine)
  nextOperation.value = after.length ? after[0] : null
  // 上一道：优先按 prevLineNo 命中，否则取行号小于本道的最近一道
  const byLine = new Map(withLine.map(r => [Number(r.lineNo), r]))
  prevOperation.value = (op.value.prevLineNo != null && byLine.get(Number(op.value.prevLineNo)))
    || withLine.filter(r => Number(r.lineNo) < curLine).slice(-1)[0]
    || null
  notifyForm.receiverIds = nextOperation.value?.assigneeId != null ? [nextOperation.value.assigneeId] : []
  notifyForm.content = buildNotifyTemplate()
}

/** 读取工艺路线「投入自动带出上道产出」开关；取不到按开（1） */
async function loadRoutingSwitch () {
  autoInputFromPrev.value = 1
  const rid = op.value?.routingId
  if (rid == null) return
  try {
    const r = await getRouting(rid)
    autoInputFromPrev.value = Number(r?.autoInputFromPrev) === 0 ? 0 : 1
  } catch (e) { autoInputFromPrev.value = 1 }
}

/** 数量类型：自动带出「本次投入 / 本次产出 / 本次报废」默认值（可改） */
function applyAutoDefaults () {
  if (opType.value !== '数量类型') return
  const plan = Number(op.value.planQuantity || 0)
  const reported = Number(op.value.reportedQuantity || 0)
  form.quantity = Math.max(0, round4(plan - reported))
  const prevOut = prevOperation.value ? Number(prevOperation.value.outputQuantity || 0) : null
  const base = autoInputFromPrev.value === 1 ? (prevOperation.value ? prevOut : plan) : plan
  form.inputQuantity = Math.max(0, round4(base))
  form.rejectedQuantity = 0
}

/** 物料自定义字段定义：面板首次打开时初始化一次（失败降级为不显示自定义字段） */
async function loadMaterialConfig () {
  if (mcfReady.value) return
  try { await mcf.init() } catch (e) { /* 降级 */ }
  mcfReady.value = true
  buildMatFields()
}

/** 通知内容默认模板（可编辑）：工序【本道】已完成，请你开始【下道】。（工单 工单号） */
function buildNotifyTemplate () {
  const selfName = op.value?.operationName || ''
  const nextName = nextOperation.value?.operationName || ''
  const orderNo = op.value?.orderNo || ''
  return `${tt('工序【')}${selfName}${tt('】已完成，请你开始【')}${nextName}${tt('】。（工单 ')}${orderNo}${tt('）')}`
}

/** 发送工序通知（@下道负责人等）：接收人为空时按钮已置灰，这里再兜底一次 */
async function doNotify () {
  if (!op.value) return
  if (!notifyForm.receiverIds.length) return
  notifying.value = true
  try {
    await notifyOperation(op.value.id, {
      receiverEmployeeIds: notifyForm.receiverIds,
      content: notifyForm.content
    })
    ElMessage.success(tt('通知已发送'))
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    notifying.value = false
  }
}

/** 执行动作：提交当前表单（负责人/计划完工/数量/备注/附件）+ 该动作（「保存」只存不推进状态） */
async function executeAction (code, label) {
  if (!op.value) return
  if (code === '派工' || code === '改派') {
    if (!form.assigneeId) { ElMessage.warning(tt('请选择工序负责人')); return }
  }
  // 【2026-11-25】工序自定义字段必填只在「保存」时拦截，不卡开工/完工
  if (code === '保存') {
    const miss = validateCfRequired()
    if (miss) { ElMessage.warning(miss); return }
  }
  let remark = form.remark || ''
  // 取消工序必须二次确认并填写取消原因（与后端强校验一致）
  if (code === '取消') {
    const { ElMessageBox } = await import('element-plus')
    try {
      const { value } = await ElMessageBox.prompt(tt('请输入取消原因'), tt('取消工序'), {
        confirmButtonText: tt('确认取消'),
        cancelButtonText: tt('返回'),
        inputPlaceholder: tt('取消原因（必填）'),
        inputValidator: (v) => (v && String(v).trim()) ? true : tt('请填写取消原因')
      })
      remark = String(value || '').trim()
    } catch (e) { return }
  }
  // 恢复工序（原「重新派工」）：把「已取消」的工序拉回「待派工」（取消后的回程）
  if (code === '恢复工序') {
    const { ElMessageBox } = await import('element-plus')
    try {
      await ElMessageBox.confirm(tt('确认恢复该工序（回到「待派工」）？'), tt('提示'), { type: 'warning' })
    } catch (e) { return }
  }
  acting.value = true
  activeCode.value = code
  try {
    const payload = {
      action: code,
      remark: remark || null,
      attachments: form.attachments || '',
      // 工序自定义字段随动作一并提交（后端同一事务落库）
      customFields: { ...cfValues.value }
    }
    if (form.assigneeId) {
      const hit = employees.value.find(e => e.value === form.assigneeId)
      payload.assigneeId = form.assigneeId
      payload.assigneeName = hit ? hit.label : null
    }
    if (form.planEndDate) payload.planEndDate = form.planEndDate
    if (opType.value === '数量类型' && code === '数量报工') {
      payload.inputQuantity = form.inputQuantity
      payload.quantity = form.quantity
      payload.rejectedQuantity = form.rejectedQuantity
    }
    // 金额类型：本次金额既记入操作日志备注，也作为 amount 提交后端累加到「已确认金额」
    if (opType.value === '金额类型' && form.amount != null && Number(form.amount) !== 0) {
      payload.amount = Number(form.amount)
      const amt = `${tt('本次金额')}：${form.amount}`
      payload.remark = payload.remark ? `${payload.remark}；${amt}` : amt
    }
    // 【2026-10-25】数量报工改为「页内直接填数量」：写报工流水 + 按人记账，不打开任何单据
    if (opType.value === '数量类型' && code === '数量报工') {
      const r = await reportOperation(op.value.id, {
        quantity: form.quantity,
        rejectedQuantity: form.rejectedQuantity,
        inputQuantity: form.inputQuantity,
        remark: remark || null,
        customFields: { ...cfValues.value },
        idemKey: `${op.value.id}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
      })
      ElMessage.success(`${tt(label)}${tt('成功')}`)
      if (r?.operation) op.value = { ...op.value, ...r.operation }
      form.attachments = op.value.attachments || ''
      form.quantity = 0
      form.rejectedQuantity = 0
      form.inputQuantity = 0
      await loadAssignPermission()
      await loadWorkerInfo()
      await refreshLogs()
      emit('changed', op.value)
      return
    }
    const updated = await actOperation(op.value.id, payload)
    ElMessage.success(`${tt(label)}${tt('成功')}`)
    op.value = { ...op.value, ...(updated || {}) }
    form.attachments = op.value.attachments || ''
    // 状态可能改变（如 恢复工序 → 待派工），重新判定「负责人」是否可改
    await loadAssignPermission()
    await refreshLogs()
    emit('changed', op.value)
  } catch (e) {
    // 失败原因由请求拦截器统一提示；此处兜底避免控制台报错
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    acting.value = false
    activeCode.value = ''
  }
}

/** 回退确认：下道退回本道的数量（累计；只记数量，不改工序状态） */
async function submitRework () {
  if (!op.value) return
  if (!(reworkForm.quantity > 0)) { ElMessage.warning(tt('请输入大于 0 的回退数量')); return }
  acting.value = true
  try {
    const updated = await confirmOperationRework(op.value.id, { quantity: reworkForm.quantity, remark: form.remark || null })
    op.value = { ...op.value, ...(updated || {}) }
    reworkForm.quantity = 0
    ElMessage.success(tt('回退确认成功'))
    await refreshLogs()
    emit('changed', op.value)
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    acting.value = false
  }
}

/** 判废确认：本道判定回退料中不能用的数量（累计；决定能否下推补料） */
async function submitScrap () {
  if (!op.value) return
  if (!(scrapForm.quantity > 0)) { ElMessage.warning(tt('请输入大于 0 的判废数量')); return }
  acting.value = true
  try {
    const updated = await confirmOperationScrap(op.value.id, { quantity: scrapForm.quantity, remark: form.remark || null })
    op.value = { ...op.value, ...(updated || {}) }
    scrapForm.quantity = 0
    ElMessage.success(tt('判废确认成功'))
    await refreshLogs()
    emit('changed', op.value)
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    acting.value = false
  }
}

/** 下推补料：按可下推判废量 × 主BOM 用量生成补料领料单（发起人=当前登录人） */
async function doPushSupplement () {
  if (!op.value) return
  if (pendingScrap.value <= 0) { ElMessage.warning(tt('没有待下推的判废数量')); return }
  acting.value = true
  try {
    const r = await pushOperationSupplement(op.value.id, { remark: form.remark || null })
    if (r?.operation) op.value = { ...op.value, ...r.operation }
    ElMessage.success(`${tt('已生成补料领料单：')}${r?.billNo || ''}`)
    await refreshLogs()
    emit('changed', op.value)
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    acting.value = false
  }
}

/** 第 11 期：入库类型工序 —— 一键发起入库单（草稿），成功后提示入库单号并刷新工序行 */
async function doPushReceipt () {
  if (!op.value) return
  if (!(receiptForm.quantity > 0)) { ElMessage.warning(tt('请输入大于 0 的入库数量')); return }
  acting.value = true
  try {
    const r = await pushOperationReceipt(op.value.id, { quantity: receiptForm.quantity })
    if (r?.operation) op.value = { ...op.value, ...r.operation }
    ElMessage.success(`${tt('已生成入库单：')}${r?.billNo || ''}`)
    receiptForm.quantity = 0
    await refreshLogs()
    emit('changed', op.value)
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    acting.value = false
  }
}

/** 第 12 期：打开「填质检」弹窗 —— 取该工序应使用的模板，按模板渲染检验项 */
async function openQc () {
  if (!op.value) return
  qcSubmitting.value = true
  try {
    const tpl = await getOperationQcTemplate(op.value.id)
    if (!tpl) {
      ElMessage.warning(tt('未找到该工序的质检模板：请先在「基础资料 → 质检模板」按工序或按物料配置并启用'))
      return
    }
    qcTemplate.value = tpl
    qcItems.value = (tpl.items || []).map(it => ({ ...it, measuredValue: '', images: '' }))
    qcOp.value = { ...op.value }
    // 抽检：抽样数默认按 抽样比例 × 报工数量 预算，可手工改；全检：合格数默认 = 报工数量
    const reportQty = Number(op.value.reportedQuantity || op.value.planQuantity || 0)
    const ratio = Number(tpl.sampleRatio || 0)
    Object.assign(qcForm, {
      inspectQty: ratio > 0 ? Number((reportQty * ratio / 100).toFixed(4)) : reportQty,
      qualifiedQty: reportQty,
      defectQty: 0,
      handleType: '', handleOperationName: '', handleRemark: '', remark: ''
    })
    // 返工回退目标工序：同工单的其它工序（排除本道）
    try {
      const list = await listWorkOrderOperation(op.value.orderId)
      qcSiblings.value = (list || []).filter(r => r.id !== op.value.id)
    } catch (e) {
      qcSiblings.value = []
    }
    qcVisible.value = true
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    qcSubmitting.value = false
  }
}

/** 第 12 期：提交检验（后端按质检类型判定；合格 → 已完工；不合格 → 按所选处理方式处置后退回） */
async function submitQc () {
  if (!op.value) return
  if (qcType.value === '全检') {
    const q = Number(qcForm.qualifiedQty || 0)
    const d = Number(qcForm.defectQty || 0)
    if (q + d <= 0) { ElMessage.warning(tt('请填写合格数或不合格数')); return }
  }
  if (qcForm.handleType === '返工回退' && !qcForm.handleOperationName) {
    ElMessage.warning(tt('请选择返工回退的目标工序'))
    return
  }
  qcSubmitting.value = true
  try {
    const payload = {
      inspectQty: qcForm.inspectQty,
      qualifiedQty: qcForm.qualifiedQty,
      defectQty: qcForm.defectQty,
      handleType: qcForm.handleType || null,
      handleOperationName: qcForm.handleOperationName || null,
      handleRemark: qcForm.handleRemark || null,
      remark: qcForm.remark || null,
      items: qcItems.value.map(it => ({ itemName: it.itemName, measuredValue: it.measuredValue, images: it.images || '' }))
    }
    const r = await submitOperationQc(op.value.id, payload)
    if (r?.operation) op.value = { ...op.value, ...r.operation }
    if (r?.result === '合格') {
      ElMessage.success(tt('检验通过，工序已完工'))
    } else {
      ElMessage.success(`${tt('判定不合格，已按「')}${qcForm.handleType}${tt('」处理')}${r?.handleBillNo ? tt('：') + r.handleBillNo : ''}`)
    }
    qcVisible.value = false
    await refreshLogs()
    emit('changed', op.value)
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  } finally {
    qcSubmitting.value = false
  }
}

watch(() => props.operation, (v) => { if (v) { op.value = { ...v }; loadWorkerInfo() } })
</script>

<style scoped>
.op-panel { display: flex; flex-direction: column; gap: 12px; }

/* 【2026-10-25】共同负责人（扫码认领）：一行内标签 + 我 + 认领入口 */
.op-workers { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 0 0 4px; }
.workers-label { font-size: 13px; color: #6b7280; }
.worker-tag { max-width: 100%; }
.workers-empty { font-size: 12px; color: #9ca3af; }
.workers-mine { font-size: 12px; color: #2563eb; }
.workers-hint { font-size: 12px; color: #b45309; }
.op-meta, .op-report, .op-plan-row, .op-rework, .op-hours-row { margin: 0; }
.op-meta :deep(.el-form-item), .op-report :deep(.el-form-item), .op-plan-row :deep(.el-form-item), .op-rework :deep(.el-form-item), .op-hours-row :deep(.el-form-item) { margin-bottom: 8px; }
.op-tip { font-size: 12px; color: #8b5cf6; background: #f5f3ff; padding: 8px 12px; border-radius: 6px; }
.op-tip.muted { color: #9ca3af; background: #f3f4f6; }
.op-terminal-line { font-size: 13px; color: #9ca3af; padding: 6px 0; }
/* 【2026-11-25】工序自定义字段：两列紧凑表单，填完随「保存」或任一动作提交 */
.op-cf-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 16px; }
.op-cf-item { display: flex; flex-direction: column; gap: 4px; }
.op-cf-label { font-size: 12px; line-height: 18px; color: #6b7280; }
.op-cf-required { color: #ef4444; margin-left: 2px; }
.op-cf-item :deep(.el-select),
.op-cf-item :deep(.el-input),
.op-cf-item :deep(.el-input-number),
.op-cf-item :deep(.el-date-editor) { width: 100%; }
.op-cf-readonly { font-size: 13px; color: #606266; min-height: 28px; line-height: 28px; }

/* ① 工序明细 / ② 物料明细（单据明细式：表头一行列名 + 值一行） */
.op-dlg-head { display: flex; align-items: center; gap: 8px; }
.op-dlg-title { font-size: 16px; font-weight: 600; color: #1f2937; }
.op-dlg-badge { font-size: 12px; font-weight: 600; border: 1px solid; border-radius: 999px; padding: 1px 8px; }
.op-dlg-grow { flex: 1 1 auto; }
.op-detail { border: 1px solid #eef1f6; border-radius: 8px; padding: 10px 12px; }
.op-detail + .op-detail { margin-top: 10px; }
.op-detail-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.op-detail-title { font-size: 13px; font-weight: 600; color: #1f2937; }
.op-detail-wrap { overflow: hidden; }
.op-detail-table { width: 100%; font-size: 13px; }
.op-detail-table :deep(.el-table__header th) { background: #f5f7fa !important; color: #606266; font-weight: 600; font-size: 12px; padding: 6px 4px; }
.op-detail-table :deep(.el-table__body td) { padding: 6px 4px; color: #1f2937; font-weight: 600; }
.op-detail-img { display: block; width: 44px; height: 44px; border-radius: 6px; border: 1px solid #e5e7eb; cursor: zoom-in; }
.op-detail-empty { font-size: 13px; color: #9ca3af; padding: 8px 0; }

/* ③ 本次操作 / 进度条 / 折叠区 */
.plan-hint, .remark-hint, .notify-hint, .supply-hint { font-size: 12px; color: #9ca3af; margin-left: 8px; }
.plan-hint.warn { color: #f59e0b; }
.notify-hint.warn { color: #f59e0b; }
.op-progress { display: flex; align-items: center; gap: 10px; }
.op-progress-txt { font-size: 12px; color: #6b7280; white-space: nowrap; }
/* 工时体系：只读实际工时 + 计时状态 + 计划/实际/误差对比 */
.hours-readonly { font-weight: 600; color: #1f2937; font-variant-numeric: tabular-nums; }
.hours-timing { display: inline-flex; align-items: center; gap: 4px; margin-left: 10px; font-size: 12px; }
.hours-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; }
.hours-dot.on { background: #22c55e; }
.hours-dot.off { background: #c0c4cc; }
.timing-on { color: #16a34a; }
.timing-off { color: #9ca3af; }
.hours-hint { font-size: 12px; color: #9ca3af; margin-left: 10px; }
.op-hours-cmp { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; font-size: 13px; color: #6b7280; padding: 4px 0; }
.cmp-sep { color: #d1d5db; }
.cmp-part b { font-weight: 600; }
.cmp-part b.cmp-slow { color: #ef4444; }
.cmp-part b.cmp-fast { color: #16a34a; }
.cmp-part b.cmp-flat { color: #6b7280; }
.op-fold { border-top: 1px solid #f0f0f0; border-bottom: 1px solid #f0f0f0; }
.op-fold-title { font-size: 13px; color: #6b7280; }
.op-fold-sub { margin-left: 8px; font-size: 12px; color: #b0b3b8; }
.op-supply { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 10px 14px; background: #fff7ed; border-radius: 8px; margin-bottom: 8px; }
.op-supply b { font-size: 15px; color: #1f2937; }
.op-supply b.bad { color: #ef4444; }
.qty-label { font-size: 12px; color: #9ca3af; }
.op-bill { font-size: 12px; color: #16a34a; }

/* ④ 动作按钮：只渲染当前可执行的，按 报工 / 质检 / 单据下推 分组 */
.op-actions-wrap { display: flex; flex-direction: column; gap: 8px; }
.op-act-group { display: flex; align-items: center; gap: 10px; }
.op-act-group-title {
  flex: none;
  min-width: 56px;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
}
.op-act-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.op-act-foot { justify-content: flex-end; border-top: 1px solid #f0f0f0; padding-top: 8px; }
.op-push-hint { font-size: 12px; color: #9ca3af; }
.op-save-btn { margin-left: auto; }

/* ⑤ 备注 */
.op-remark { margin: 0; }
.op-remark :deep(.el-form-item) { margin-bottom: 0; }

/* ⑥ 附件 */
.op-attach { display: flex; flex-direction: column; gap: 8px; }
.bill-attach { border: 1px dashed #e5e7eb; border-radius: 6px; padding: 8px 10px; background: #fafbfc; }
.bill-attach-title { font-size: 12px; font-weight: 600; color: #6b7280; margin-bottom: 6px; }
.upload-tip { font-size: 12px; color: #909399; margin-top: 4px; }
/* 【2026-10-30】附件缩略图网格：图片显示缩略图、点击放大；非图片显示文件卡片 */
.attach-grid { display: flex; flex-wrap: wrap; gap: 10px; }
.attach-cell { position: relative; width: 72px; }
.attach-thumb {
  width: 72px;
  height: 72px;
  display: block;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  cursor: zoom-in;
  background: #fafbfc;
}
.attach-file {
  width: 72px;
  height: 72px;
  box-sizing: border-box;
  padding: 4px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafbfc;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  cursor: pointer;
}
.attach-file__ico { font-size: 20px; color: #909399; }
.attach-file__name {
  width: 100%;
  font-size: 10px;
  color: #606266;
  text-align: center;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.attach-badge {
  position: absolute;
  left: 2px;
  top: 2px;
  padding: 0 3px;
  font-size: 10px;
  line-height: 14px;
  color: #fff;
  background: rgba(0, 0, 0, .45);
  border-radius: 2px;
}
.attach-del {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 16px;
  height: 16px;
  line-height: 14px;
  text-align: center;
  font-size: 12px;
  color: #fff;
  background: #ef4444;
  border-radius: 50%;
  cursor: pointer;
}
.attach-empty { font-size: 12px; color: #9ca3af; }

/* ⑦ 工序通知 */
.op-notify { display: flex; flex-direction: column; gap: 4px; }
.op-notify-form { margin: 0; }
.op-notify-form :deep(.el-form-item) { margin-bottom: 8px; }
.op-notify-actions { display: flex; align-items: center; justify-content: flex-end; gap: 12px; }

.log-arrow { color: #c7d2fe; margin: 0 4px; vertical-align: middle; }

/* 列设置浮层（工序明细列 + 物料明细列；跟着按钮走，不用固定定位） */
.ocs { display: flex; flex-direction: column; }
.ocs-title { font-size: 12px; color: #909399; margin-bottom: 8px; }
.ocs-group { font-size: 12px; font-weight: 600; color: #303133; padding: 6px 0 4px; border-top: 1px solid #f0f0f0; }
.ocs-group:first-of-type { border-top: 0; padding-top: 0; }
.ocs-list { max-height: 168px; overflow-y: auto; }
.ocs-row { display: flex; align-items: center; gap: 8px; padding: 4px 2px; }
.ocs-row:hover { background: #f5f7fa; }
.ocs-drag { cursor: move; color: #909399; font-size: 15px; flex-shrink: 0; }
.ocs-row :deep(.el-checkbox) { margin-right: 0; }
.ocs-row :deep(.el-checkbox__label) { font-size: 13px; color: #303133; }
.ocs-ghost { opacity: .5; background: #e6f7ff !important; }
.ocs-footer { display: flex; justify-content: flex-end; gap: 8px; padding-top: 10px; border-top: 1px solid #f0f0f0; margin-top: 8px; }

/* 第 12 期：填质检弹窗 */
.qc-panel { display: flex; flex-direction: column; gap: 10px; }
.qc-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }

/* 【第 19 期】质检逐项留证：缩略图 + 删除角标 + 上传按钮 */
.qc-ev { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.qc-ev-item {
  position: relative;
  display: inline-block;
  width: 34px;
  height: 34px;
}
.qc-ev-img {
  width: 34px;
  height: 34px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
  cursor: pointer;
}
.qc-ev-del {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 14px;
  height: 14px;
  line-height: 12px;
  text-align: center;
  font-size: 12px;
  color: #fff;
  background: #ef4444;
  border-radius: 50%;
  cursor: pointer;
}
.qc-ev-add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  font-size: 18px;
  line-height: 1;
  color: #4f46e5;
  border: 1px dashed #a5b4fc;
  border-radius: 4px;
  cursor: pointer;
}
.qc-opname { font-weight: 600; color: #1f2937; }
.qc-tip { font-size: 12px; color: #8b5cf6; background: #f5f3ff; padding: 8px 12px; border-radius: 6px; }
.qc-meta { margin: 0; }
.qc-meta :deep(.el-form-item) { margin-bottom: 8px; }
.qc-const { font-weight: 600; color: #1f2937; }
.qc-hint { font-size: 12px; color: #9ca3af; margin-left: 8px; }
</style>
