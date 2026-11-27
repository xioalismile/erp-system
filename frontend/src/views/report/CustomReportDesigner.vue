<template>
  <div class="custom-report-designer">
    <!-- 顶部标题栏 -->
    <div class="designer-header">
      <div class="header-left">
        <el-button size="small" @click="goBack"><el-icon><Back /></el-icon> {{ $tt("返回") }}</el-button>
        <span class="title-text">{{ isEdit ? '编辑报表' : '新建报表' }}</span>
        <el-tag v-if="reportCode" size="small" type="info">{{ reportCode }}</el-tag>
      </div>
      <div class="header-right">
        <el-button v-if="isEdit" type="success" plain :loading="previewing" @click="handlePreview">
          <el-icon><VideoPlay /></el-icon> 试运行
        </el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          <el-icon><Check /></el-icon> {{ isEdit ? '保存' : '保存报表' }}
        </el-button>
      </div>
    </div>

    <div class="designer-body">
      <!-- ========== 1. 基本信息（常显） ========== -->
      <div class="section">
        <div class="section-title"><span class="step-no">1</span> {{ $tt("基本信息") }}</div>
        <div class="section-content">
          <el-form label-width="90px" label-position="right">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item :label="$tf('报表名称')" required>
                  <el-input v-model="form.reportName" :placeholder="$tt('如：销售订单明细汇总')" maxlength="100" show-word-limit />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item :label="$tf('数据源')" required>
                  <el-select
                    v-model="form.dataSource"
                    :placeholder="$tt('选择单据/明细/基础数据')"
                    filterable
                    style="width: 100%"
                    @change="onDataSourceChange"
                  >
                    <el-option-group
                      v-for="g in dataSourceGroups"
                      :key="g.module"
                      :label="$tf(g.label)"
                    >
                      <el-option v-for="ds in g.items" :key="ds.key" :label="ds.name" :value="ds.key" />
                    </el-option-group>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item :label="$tf('报表模式')">
                  <el-radio-group v-model="form.mode" @change="onModeChange">
                    <el-radio value="detail">{{ $tt("明细") }}</el-radio>
                    <el-radio value="summary">{{ $tt("汇总") }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item :label="$tf('备注')">
                  <el-input v-model="form.remark" :placeholder="$tt('可选')" maxlength="200" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row v-if="form.mode === 'summary'">
              <el-col :span="14">
                <el-form-item :label="$tf('图表类型')">
                  <el-radio-group v-model="form.chartType">
                    <el-radio value="">{{ $tt("不使用图表") }}</el-radio>
                    <el-radio value="bar">{{ $tt("柱状图") }}</el-radio>
                    <el-radio value="pie">{{ $tt("饼图") }}</el-radio>
                    <el-radio value="line">{{ $tt("折线图") }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <!-- ========== 子标签页：字段与计算 / 跨单关联 / 筛选排序 / 共享设置 ========== -->
      <div class="section tabs-section" v-if="form.dataSource">
        <el-tabs v-model="activeTab" class="designer-tabs">
          <!-- Tab 1: 字段与计算 -->
          <el-tab-pane name="fields">
            <template #label>
              <span class="tab-label">{{ $tt("字段与计算") }}<span v-if="tabBadge.fields" class="tab-badge">{{ tabBadge.fields }}</span></span>
            </template>
            <div class="section-content field-pickers">
              <!-- 左侧：字段树 -->
              <div class="field-source-panel">
                <el-input v-model="fieldSearch" :placeholder="$tt('搜索字段')" clearable size="small" class="field-search">
                  <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
                <el-scrollbar class="field-source-scroll">
                  <div v-for="g in filteredFieldGroups" :key="g.key" class="field-group">
                    <div class="field-group-title" @click="toggleGroup(g.key)">
                      <el-icon class="arrow" :class="{ expanded: expandedGroups[g.key] }"><ArrowRight /></el-icon>
                      <span class="group-name">{{ g.name }}</span>
                      <el-tag size="small" type="info" round>{{ g.fields.length }}</el-tag>
                    </div>
                    <div v-show="expandedGroups[g.key]" class="field-list">
                      <div
                        v-for="f in g.fields"
                        :key="g.key + '.' + f.field"
                        class="field-item"
                        :class="{ checked: isSelected(g.key, f.field) }"
                        @click="toggleField(g.key, f.field)"
                      >
                        <el-checkbox :model-value="isSelected(g.key, f.field)" @change="() => toggleField(g.key, f.field)" @click.stop />
                        <span class="field-label">{{ $tf(f.label) }}</span>
                        <span class="field-type" :class="f.type">{{ typeLabel(f.type) }}</span>
                      </div>
                    </div>
                  </div>
                </el-scrollbar>
              </div>

              <!-- 右侧：已选（明细列 / 分组字段） -->
              <div class="selected-panel">
                <div class="selected-panel-header">
                  <span>{{ form.mode === 'summary' ? '已选分组' : '已选列' }}（{{ currentSelection.length }}/{{ form.mode === 'summary' ? MAX_GROUP : MAX_COLUMNS }}）</span>
                  <span class="selected-tip">{{ $tt("顺序即生效顺序，可上下调整") }}</span>
                </div>
                <el-scrollbar class="selected-scroll">
                  <el-empty v-if="!currentSelection.length" :description="form.mode === 'summary' ? '请从左侧勾选分组字段' : '请从左侧勾选字段'" :image-size="60" />
                  <div v-for="(col, idx) in currentSelection" :key="col.source + '.' + col.field" class="selected-item">
                    <span class="col-index">{{ idx + 1 }}</span>
                    <span class="col-label">{{ $tf(col.label) }}</span>
                    <el-select
                      v-if="form.mode === 'summary' && (col.type === 'date' || col.type === 'datetime')"
                      v-model="col.granularity"
                      size="small"
                      class="granularity-select"
                      :title="$tt('日期分组粒度')"
                    >
                      <el-option value="" :label="$tf('按天')" />
                      <el-option value="month" :label="$tf('按月')" />
                      <el-option value="quarter" :label="$tf('按季')" />
                      <el-option value="year" :label="$tf('按年')" />
                    </el-select>
                    <span class="col-source">{{ col.groupName }}</span>
                    <span class="col-actions">
                      <el-button size="small" link :disabled="idx === 0" @click="moveSelected(idx, -1)">
                        <el-icon><Top /></el-icon>
                      </el-button>
                      <el-button size="small" link :disabled="idx === currentSelection.length - 1" @click="moveSelected(idx, 1)">
                        <el-icon><Bottom /></el-icon>
                      </el-button>
                      <el-button size="small" link type="danger" @click="removeSelected(idx)">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </span>
                  </div>
                </el-scrollbar>
              </div>
            </div>
            <div class="empty-ds" v-if="!form.dataSource">
              <el-empty :description="$tt('请先在上方选择数据源')" :image-size="60" />
            </div>

            <!-- 汇总模式：聚合指标 -->
            <div class="section-content agg-panel" v-if="form.mode === 'summary'">
              <div class="agg-panel-header">
                <span class="agg-title">聚合指标（{{ aggregates.length }}/{{ MAX_AGG }}）</span>
                <el-button v-if="aggregates.length < MAX_AGG" size="small" type="primary" plain @click="addAggregate">
                  <el-icon><Plus /></el-icon> 添加指标
                </el-button>
              </div>
              <el-empty v-if="!aggregates.length" :description="$tt('请至少添加一个聚合指标（如合计金额、行数）')" :image-size="50" />
              <div v-for="(a, idx) in aggregates" :key="idx" class="agg-item">
                <el-select v-model="a.func" style="width: 110px" @change="onAggFuncChange(a)">
                  <el-option :label="$tf('合计 SUM')" value="SUM" />
                  <el-option :label="$tf('计数 COUNT')" value="COUNT" />
                  <el-option :label="$tf('平均 AVG')" value="AVG" />
                  <el-option :label="$tf('最大 MAX')" value="MAX" />
                  <el-option :label="$tf('最小 MIN')" value="MIN" />
                </el-select>
                <el-select
                  v-model="a.ref"
                  filterable
                  clearable
                  :placeholder="$tt('选择字段（计数可选行数）')"
                  style="width: 320px"
                  @change="onAggRefChange(a)"
                >
                  <el-option :label="$tf('行数（COUNT *）')" value="__rows__" />
                  <el-option v-for="f in allFieldOptions" :key="f.value" :label="$tf(f.label)" :value="f.value" />
                </el-select>
                <el-input v-model="a.label" :placeholder="$tt('显示名（可选）')" maxlength="30" style="width: 200px" />
                <el-input v-model="a.alias" :placeholder="$tt('输出列名（英文，可选）')" maxlength="64" style="width: 170px" />
                <el-tooltip :content="$tt('额外生成一列：与上一行（按分组列升序）比较的增减百分比，首行/上期为0显示空白')" placement="top">
                  <el-checkbox v-model="a.compare" true-value="pct" false-value="" :label="$tf('环比%')" style="margin-right: 4px" />
                </el-tooltip>
                <el-button link type="primary" @click="toggleAggCond(a)">
                  {{ (a.conditions && a.conditions.length) ? ($tt('口径条件') + ' ' + a.conditions.length) : $tt('口径条件') }}
                </el-button>
                <span class="agg-preview">{{ defaultAggLabel(a) }}</span>
                <el-button link type="danger" @click="aggregates.splice(idx, 1)">
                  <el-icon><Delete /></el-icon>
                </el-button>
                <div v-if="a.condOpen" class="agg-conds">
                  <div v-for="(c, ci) in (a.conditions || [])" :key="ci" class="agg-cond-row">
                    <el-select v-model="c.fieldRef" filterable :placeholder="$tt('字段')" style="width: 240px">
                      <el-option v-for="f in allFieldOptions" :key="f.value" :label="$tf(f.label)" :value="f.value" />
                    </el-select>
                    <el-select v-model="c.relative" clearable :placeholder="$tt('相对时间（可选）')" style="width: 150px">
                      <el-option :label="$tt('今年')" value="THIS_YEAR" />
                      <el-option :label="$tt('去年')" value="LAST_YEAR" />
                      <el-option :label="$tt('前年')" value="YEAR_BEFORE" />
                      <el-option :label="$tt('本月')" value="THIS_MONTH" />
                      <el-option :label="$tt('上月')" value="LAST_MONTH" />
                    </el-select>
                    <el-select v-model="c.op" :disabled="!!c.relative" style="width: 130px">
                      <el-option :label="$tt('等于')" value="eq" />
                      <el-option :label="$tt('不等于')" value="ne" />
                      <el-option :label="$tt('大于')" value="gt" />
                      <el-option :label="$tt('大于等于')" value="ge" />
                      <el-option :label="$tt('小于')" value="lt" />
                      <el-option :label="$tt('小于等于')" value="le" />
                      <el-option :label="$tt('区间')" value="between" />
                      <el-option :label="$tt('包含')" value="like" />
                      <el-option :label="$tt('为空')" value="isNull" />
                      <el-option :label="$tt('不为空')" value="isNotNull" />
                    </el-select>
                    <template v-if="!c.relative && c.op !== 'isNull' && c.op !== 'isNotNull'">
                      <el-input v-model="c.value" :placeholder="$tt('值')" style="width: 150px" />
                      <el-input v-if="c.op === 'between'" v-model="c.value2" :placeholder="$tt('结束值')" style="width: 150px" />
                    </template>
                    <el-button link type="danger" @click="a.conditions.splice(ci, 1)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                  <div class="agg-cond-actions">
                    <el-button size="small" plain @click="addAggCondition(a)">
                      <el-icon><Plus /></el-icon> {{ $tt('添加条件') }}
                    </el-button>
                    <span class="agg-tip">{{ $tt('条件只影响这一个指标的口径，例如今年/前年对比、排除草稿作废') }}</span>
                  </div>
                </div>
              </div>
              <div class="agg-tip">SUM/AVG 仅支持数值（金额/数值）字段；COUNT 选择"行数"即统计记录条数</div>
            </div>

            <!-- 计算列（明细：数值字段/更早计算列 ±×÷ 字段/常量；汇总：聚合指标 ±×÷ 指标/常量） -->
            <div class="section-content agg-panel">
              <div class="agg-panel-header">
                <span class="agg-title">计算列（{{ calcColumns.length }}/{{ MAX_CALC }}）</span>
                <el-button v-if="calcColumns.length < MAX_CALC" size="small" type="primary" plain @click="addCalc">
                  <el-icon><Plus /></el-icon> 添加计算列
                </el-button>
              </div>
              <el-empty v-if="!calcColumns.length" :description="calcEmptyTip" :image-size="50" />
              <div v-for="(c, idx) in calcColumns" :key="idx" class="agg-item">
                <el-select v-model="c.refA" filterable :placeholder="form.mode === 'summary' ? '指标A' : '字段A（数值）'" style="width: 250px">
                  <el-option v-for="f in calcOperandOptions(idx)" :key="f.value" :label="$tf(f.label)" :value="f.value" />
                </el-select>
                <el-select v-model="c.op" style="width: 84px">
                  <el-option :label="$tf('＋ 加')" value="+" />
                  <el-option :label="$tf('－ 减')" value="-" />
                  <el-option :label="$tf('× 乘')" value="*" />
                  <el-option :label="$tf('÷ 除')" value="/" />
                </el-select>
                <el-select
                  v-model="c.refB"
                  filterable
                  clearable
                  :placeholder="form.mode === 'summary' ? '指标B（可选）' : '字段B（可选）'"
                  style="width: 250px"
                  @change="onCalcRefBChange(c)"
                >
                  <el-option v-for="f in calcOperandOptions(idx)" :key="f.value" :label="$tf(f.label)" :value="f.value" />
                </el-select>
                <el-input
                  v-if="!c.refB"
                  v-model="c.constB"
                  :placeholder="$tt('常量B（数字）')"
                  style="width: 120px"
                />
                <el-input v-model="c.label" :placeholder="$tt('列名（可选，如：剩余数量）')" maxlength="30" style="width: 170px" />
                <el-input v-model="c.formula" :placeholder="$tt('公式（可选，优先于左侧三项）')" style="width: 260px" />
                <span class="agg-preview">{{ defaultCalcLabel(c) }}</span>
                <el-button link type="danger" @click="calcColumns.splice(idx, 1)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <div class="agg-tip">{{ calcTip }}</div>
            </div>
          </el-tab-pane>

          <!-- Tab 2: 跨单据关联 -->
          <el-tab-pane name="joins" v-if="form.dataSource">
            <template #label>
              <span class="tab-label">{{ $tt("跨单关联") }}<span v-if="tabBadge.joins" class="tab-badge">{{ tabBadge.joins }}</span></span>
            </template>
            <div class="section-content">
              <div class="tab-desc">关联其它单据实现数据联动：本表字段 = 目标字段（类型一致即按值匹配，不要求同名），最多 {{ MAX_JOINS }} 个；一对多场景（订单→多次发货/收款）请开启"聚合挂载"防止行数翻倍金额虚增，聚合字段出现在字段树"跨单聚合"分组</div>
              <div v-for="(j, idx) in extraJoinList" :key="idx" class="join-item">
                <div class="join-row">
                  <el-select
                    v-model="j.dataSourceKey"
                    :placeholder="$tt('选择关联数据源')"
                    filterable
                    style="width: 220px"
                    @change="onJoinTargetChange(idx)"
                  >
                    <el-option-group v-for="g in dataSourceGroups" :key="g.module" :label="$tf(g.label)">
                      <el-option v-for="ds in joinTargetOptions(idx, g)" :key="ds.key" :label="ds.name" :value="ds.key" />
                    </el-option-group>
                  </el-select>
                  <el-select v-model="j.leftRef" filterable :placeholder="$tt('本表字段')" style="width: 230px">
                    <el-option-group v-for="grp in mainTreeGroups" :key="grp.key" :label="grp.name">
                      <el-option v-for="f in grp.fields" :key="f.field" :label="$tf(f.label)" :value="`${grp.key}.${f.field}`" />
                    </el-option-group>
                  </el-select>
                  <span class="join-eq">=</span>
                  <el-select
                    v-model="j.rightRef"
                    filterable
                    :loading="j.loading"
                    :disabled="!j.dataSourceKey"
                    :placeholder="j.aggMode ? '目标主表字段' : '目标字段'"
                    style="width: 230px"
                  >
                    <el-option-group v-for="grp in compatibleRightTree(j)" :key="grp.source" :label="grp.name">
                      <el-option v-for="f in grp.fields" :key="f.field" :label="$tf(f.label)" :value="`${grp.source}.${f.field}`" />
                    </el-option-group>
                  </el-select>
                  <el-switch v-model="j.aggMode" :active-text="$tt('聚合挂载')" @change="onAggModeChange(idx)" />
                  <span v-if="j.dataSourceKey && !j.loading && !j.rightTree.length" class="join-empty">{{ $tt("该数据源无可关联字段") }}</span>
                  <el-button link type="danger" @click="removeJoin(idx)">
                    <el-icon><Delete /></el-icon> 移除
                  </el-button>
                </div>
                <!-- 聚合挂载指标编辑 -->
                <div v-if="j.aggMode" class="agg-fields">
                  <div v-for="(af, ai) in j.aggFields" :key="ai" class="agg-field-row">
                    <el-select v-model="af.source" :placeholder="$tt('来源表')" style="width: 150px" @change="af.field = ''">
                      <el-option :label="$tf('主表')" value="" />
                      <el-option v-for="g in (j.detailTree || [])" :key="g.source" :label="g.name" :value="g.source" />
                    </el-select>
                    <el-select v-model="af.field" filterable :placeholder="af.source ? '明细表字段' : '主表字段'" style="width: 190px">
                      <el-option v-if="!af.source" :label="$tf('单据数 COUNT(*)（关联单张数）')" value="" />
                      <el-option v-for="f in aggFieldOptions(j, af)" :key="f.field" :label="$tf(f.label)" :value="f.field" />
                    </el-select>
                    <el-select v-model="af.func" :placeholder="$tt('函数')" style="width: 96px">
                      <el-option v-for="fn in ['SUM', 'COUNT', 'AVG', 'MAX', 'MIN']" :key="fn" :label="fn" :value="fn" />
                    </el-select>
                    <el-input v-model="af.label" :placeholder="$tt('显示名（如 已发数量）')" style="width: 170px" />
                    <el-input v-model="af.alias" :placeholder="$tt('输出名（英文，对比时必填）')" style="width: 180px" />
                    <el-button link type="primary" @click="toggleAggCond(af)">
                      {{ (af.conditions && af.conditions.length) ? ($tt('口径条件') + ' ' + af.conditions.length) : $tt('口径条件') }}
                    </el-button>
                    <el-button link type="danger" @click="j.aggFields.splice(ai, 1)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                    <div v-if="af.condOpen" class="agg-conds">
                      <div v-for="(c, ci) in (af.conditions || [])" :key="ci" class="agg-cond-row">
                        <el-select v-model="c.fieldRef" filterable :placeholder="$tt('目标单字段/表头字段')" style="width: 240px">
                          <el-option-group v-for="grp in (j.rightTree || [])" :key="grp.source" :label="grp.name">
                            <el-option v-for="f in (grp.fields || [])" :key="grp.source + '.' + f.field"
                              :label="$tf(f.label)" :value="`${grp.source}.${f.field}`" />
                          </el-option-group>
                        </el-select>
                        <el-select v-model="c.relative" clearable :placeholder="$tt('相对时间（可选）')" style="width: 150px">
                          <el-option :label="$tt('今年')" value="THIS_YEAR" />
                          <el-option :label="$tt('去年')" value="LAST_YEAR" />
                          <el-option :label="$tt('前年')" value="YEAR_BEFORE" />
                          <el-option :label="$tt('本月')" value="THIS_MONTH" />
                          <el-option :label="$tt('上月')" value="LAST_MONTH" />
                        </el-select>
                        <el-select v-model="c.op" :disabled="!!c.relative" style="width: 130px">
                          <el-option :label="$tt('等于')" value="eq" />
                          <el-option :label="$tt('不等于')" value="ne" />
                          <el-option :label="$tt('大于')" value="gt" />
                          <el-option :label="$tt('小于')" value="lt" />
                          <el-option :label="$tt('区间')" value="between" />
                          <el-option :label="$tt('包含')" value="like" />
                          <el-option :label="$tt('为空')" value="isNull" />
                          <el-option :label="$tt('不为空')" value="isNotNull" />
                        </el-select>
                        <template v-if="!c.relative && c.op !== 'isNull' && c.op !== 'isNotNull'">
                          <el-input v-model="c.value" :placeholder="$tt('值')" style="width: 150px" />
                          <el-input v-if="c.op === 'between'" v-model="c.value2" :placeholder="$tt('结束值')" style="width: 150px" />
                        </template>
                        <el-button link type="danger" @click="af.conditions.splice(ci, 1)">
                          <el-icon><Delete /></el-icon>
                        </el-button>
                      </div>
                      <div class="agg-cond-actions">
                        <el-button size="small" plain @click="addAggCondition(af)">
                          <el-icon><Plus /></el-icon> {{ $tt('添加条件') }}
                        </el-button>
                        <span class="agg-tip">{{ $tt('在这里按日期拆分今年/前年，例如订单日期 今年、订单日期 前年') }}</span>
                      </div>
                    </div>
                  </div>
                  <el-button v-if="j.aggFields.length < 10" size="small" plain @click="j.aggFields.push({ source: '', field: '', func: 'SUM', label: '', alias: '', conditions: [] })">
                    <el-icon><Plus /></el-icon> 添加聚合指标
                  </el-button>
                  <div v-if="hasAggSourceMix(j)" class="agg-mix-warning">{{ $tt("同一关联不可混配主表字段与明细表字段（明细JOIN会放大主表SUM），请拆成两个关联") }}</div>
                  <!-- 两跳追溯（via）：目标单经跳板表回溯主表，如销售订单→采购订单→采购入库单 -->
                  <div class="agg-field-row">
                    <span class="via-label">{{ $tt("两跳追溯") }}</span>
                    <el-select v-model="j.viaKey" filterable clearable :placeholder="$tt('经中间表（可选）')" style="width: 200px" @change="onViaKeyChange(idx)">
                      <el-option-group v-for="g in dataSourceGroups" :key="g.module" :label="$tf(g.label)">
                        <el-option v-for="ds in g.items" :key="ds.key" :label="ds.name" :value="ds.key" :disabled="ds.key === form.dataSource || ds.key === j.dataSourceKey" />
                      </el-option-group>
                    </el-select>
                    <template v-if="j.viaKey">
                      <el-select v-model="j.viaTarget" filterable :loading="j.viaLoading" :placeholder="$tt('跳板单号字段（=目标来源单号）')" style="width: 230px">
                        <el-option v-for="f in (j.viaTree || [])" :key="f.field" :label="$tf(f.label)" :value="f.field" />
                      </el-select>
                      <el-select v-model="j.viaMain" filterable :loading="j.viaLoading" :placeholder="$tt('跳板来源单号字段（=本表单号）')" style="width: 230px">
                        <el-option v-for="f in (j.viaTree || [])" :key="f.field" :label="$tf(f.label)" :value="f.field" />
                      </el-select>
                    </template>
                  </div>
                  <div v-if="j.viaKey && (!j.viaTarget || !j.viaMain)" class="agg-mix-warning">链路：本表单号 = 跳板.来源单号，跳板.单号 = 目标.来源单号（两个跳板字段都选完才生效）</div>
                </div>
              </div>
              <el-button v-if="extraJoinList.length < MAX_JOINS" size="small" type="primary" plain @click="addJoin">
                <el-icon><Plus /></el-icon> 添加关联
              </el-button>
              <div class="join-example">示例：销售订单明细报表关联销售出库明细 —— 本表"订单明细ID" = 目标"源单明细ID"，即可看到每行订货数量与已发数量，再用计算列相减得出剩余数量</div>
            </div>
          </el-tab-pane>

          <!-- Tab 3: 筛选与排序 -->
          <el-tab-pane name="filters">
            <template #label>
              <span class="tab-label">{{ $tt("筛选排序") }}</span>
            </template>
            <div class="section-content">
              <el-form label-width="90px" label-position="right">
                <el-row :gutter="16">
                  <el-col :span="8">
                    <el-form-item :label="$tf('日期筛选')">
                      <el-select v-model="filterRefs.dateField" :placeholder="$tt('选择日期字段（如单据日期）')" clearable filterable style="width: 100%">
                        <el-option v-for="f in fieldsOfType(['date', 'datetime'])" :key="f.value" :label="$tf(f.label)" :value="f.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item :label="$tf('关键字筛选')">
                      <el-select v-model="filterRefs.keywordField" :placeholder="$tt('选择关键字字段（如单号/名称）')" clearable filterable style="width: 100%">
                        <el-option v-for="f in fieldsOfType(['string'])" :key="f.value" :label="$tf(f.label)" :value="f.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item :label="$tf('状态筛选')">
                      <el-select v-model="filterRefs.statusField" :placeholder="$tt('选择状态字段')" clearable filterable style="width: 100%">
                        <el-option v-for="f in fieldsOfType(['string'])" :key="f.value" :label="$tf(f.label)" :value="f.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row>
                  <el-col :span="24">
                    <el-form-item :label="$tf('默认排序')">
                      <div class="order-list">
                        <div v-for="(o, idx) in orderList" :key="idx" class="order-item">
                          <el-select v-model="o.field" :placeholder="$tt('选择排序字段')" filterable style="width: 320px">
                            <el-option v-for="f in orderFieldOptions" :key="f.value" :label="$tf(f.label)" :value="f.value" />
                          </el-select>
                          <el-select v-model="o.dir" style="width: 100px">
                            <el-option :label="$tf('升序')" value="asc" />
                            <el-option :label="$tf('降序')" value="desc" />
                          </el-select>
                          <el-button link type="danger" @click="orderList.splice(idx, 1)"><el-icon><Delete /></el-icon></el-button>
                        </div>
                        <el-button v-if="orderList.length < 3" size="small" type="primary" plain @click="orderList.push({ field: '', dir: 'desc' })">
                          <el-icon><Plus /></el-icon> 添加排序
                        </el-button>
                        <span class="order-tip">{{ $tt("最多 3 个排序字段，按添加顺序生效") }}</span>
                      </div>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row>
                  <el-col :span="24">
                    <el-form-item :label="$tf('默认筛选')">
                      <div class="order-list">
                        <div v-for="(c, idx) in defaultFilters" :key="idx" class="df-row">
                          <el-select v-if="idx > 0" v-model="c.logic" style="width: 82px">
                            <el-option :label="$tt('并且')" value="AND" />
                            <el-option :label="$tt('或者')" value="OR" />
                          </el-select>
                          <span v-else class="df-logic-ph">{{ $tt("条件") }}</span>
                          <el-select
                            v-model="c.fieldRef"
                            filterable
                            clearable
                            :placeholder="$tt('选择字段（支持自定义字段）')"
                            style="width: 260px"
                            @change="onDfFieldChange(c)"
                          >
                            <el-option
                              v-for="f in allFieldOptions"
                              :key="f.value"
                              :label="f.label"
                              :value="f.value"
                            />
                          </el-select>
                          <el-select v-model="c.op" style="width: 120px">
                            <el-option v-for="o in dfOpOptions(c)" :key="o.value" :label="o.label" :value="o.value" />
                          </el-select>
                          <template v-if="c.op !== 'isNull' && c.op !== 'isNotNull'">
                            <el-select
                              v-if="c.op === 'in' || c.op === 'notIn'"
                              v-model="c.values"
                              multiple
                              filterable
                              allow-create
                              default-first-option
                              :placeholder="$tt('回车逐个输入值（可多选）')"
                              style="width: 260px"
                            >
                              <el-option v-for="o in dfOptionsOf(c)" :key="o.value" :label="o.label" :value="o.value" />
                            </el-select>
                            <el-select
                              v-else-if="dfOptionsOf(c).length"
                              v-model="c.value"
                              clearable
                              filterable
                              :placeholder="$tt('选择值')"
                              style="width: 160px"
                            >
                              <el-option v-for="o in dfOptionsOf(c)" :key="o.value" :label="o.label" :value="o.value" />
                            </el-select>
                            <el-input
                              v-else
                              v-model="c.value"
                              clearable
                              :placeholder="dfValuePlaceholder(c)"
                              style="width: 160px"
                            />
                            <template v-if="c.op === 'between'">
                              <span class="df-eq">{{ $tt("至") }}</span>
                              <el-input v-model="c.value2" clearable :placeholder="$tt('结束值')" style="width: 160px" />
                            </template>
                          </template>
                          <el-button link type="danger" @click="defaultFilters.splice(idx, 1)">
                            <el-icon><Delete /></el-icon>
                          </el-button>
                        </div>
                        <el-button v-if="defaultFilters.length < 20" size="small" type="primary" plain @click="addDefaultFilter">
                          <el-icon><Plus /></el-icon> 添加默认筛选
                        </el-button>
                        <span class="order-tip">{{ $tt("打开报表时预填这些条件（可改可清）；用户自己加了条件则以用户的为准。日期区间请用「区间」操作符") }}</span>
                      </div>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </div>
          </el-tab-pane>

          <!-- Tab 4: 下钻 -->
          <el-tab-pane name="drill">
            <template #label>
              <span class="tab-label">{{ $tt("下钻") }}<span v-if="drillList.length" class="tab-badge">{{ drillList.length }}</span></span>
            </template>
            <div class="section-content agg-panel">
              <div class="agg-panel-header">
                <span class="agg-title">{{ $tt('下钻目标') }}（{{ drillList.length }}/{{ MAX_DRILL }}）</span>
                <el-button v-if="drillList.length < MAX_DRILL" size="small" type="primary" plain @click="drillList.push({ label: '', route: '', keyField: '', param: '' })">
                  <el-icon><Plus /></el-icon> {{ $tt('添加下钻目标') }}
                </el-button>
              </div>
              <div class="agg-tip">{{ $tt('汇总行点击后可跳到目标单据列表：传参列 = 本报表的列名，参数名 = 目标页面接收的参数名') }}</div>
              <div v-for="(d, idx) in drillList" :key="idx" class="agg-item">
                <el-input v-model="d.label" :placeholder="$tt('名称，如 销售订单明细')" style="width: 200px" />
                <el-input v-model="d.route" :placeholder="$tt('目标页面，如 /sale/orders')" style="width: 220px" />
                <el-input v-model="d.keyField" :placeholder="$tt('本报表列名，如 materialCode')" style="width: 220px" />
                <el-input v-model="d.param" :placeholder="$tt('目标页面参数名，如 materialCode')" style="width: 240px" />
                <el-button link type="danger" @click="drillList.splice(idx, 1)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab 5: 共享设置 -->
          <el-tab-pane name="share">
            <template #label>
              <span class="tab-label">{{ $tt("共享设置") }}</span>
            </template>
            <div class="section-content">
              <el-form label-width="90px" label-position="right">
                <el-row :gutter="16">
                  <el-col :span="8">
                    <el-form-item :label="$tf('共享范围')">
                      <el-radio-group v-model="form.shareScope">
                        <el-radio value="私有">{{ $tt("私有（仅自己可见）") }}</el-radio>
                        <el-radio value="共享">{{ $tt("共享") }}</el-radio>
                      </el-radio-group>
                    </el-form-item>
                  </el-col>
                  <el-col :span="10">
                    <el-form-item :label="$tf('共享角色')" v-if="form.shareScope === '共享'">
                      <el-select v-model="shareRoleCodes" multiple filterable :placeholder="$tt('选择可查看的角色')" style="width: 100%">
                        <el-option v-for="r in roleOptions" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="6">
                    <el-form-item :label="$tf('状态')">
                      <el-radio-group v-model="form.status">
                        <el-radio value="启用">{{ $tt("启用") }}</el-radio>
                        <el-radio value="停用">{{ $tt("停用") }}</el-radio>
                      </el-radio-group>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 试运行弹窗 -->
    <el-dialog v-model="previewVisible" :title="$tf('试运行（前 20 条）')" width="86%" top="5vh" destroy-on-close>
      <el-table :data="previewData" border size="small" v-loading="previewing" max-height="480">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column
          v-for="c in previewColumns"
          :key="c.field"
          :prop="c.field"
          :label="$tf(c.label)"
          :align="c.type === 'amount' || c.type === 'number' || c.type === 'int' ? 'right' : 'left'"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span v-if="c.type === 'amount'" class="cell-amount">¥{{ formatNumber(row[c.field]) }}</span>
            <span v-else-if="c.type === 'number' || c.type === 'int'" class="cell-number">{{ formatNumber(row[c.field]) }}</span>
            <span v-else>{{ row[c.field] ?? '' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, Check, Search, ArrowRight, Top, Bottom, Delete, Plus, VideoPlay } from '@element-plus/icons-vue'
import {
  listDataSources, describeDataSource, joinableColumns, getCustomReport,
  createCustomReport, updateCustomReport, runCustomReport
} from '@/api/report/customReport'
import { listAllRole } from '@/api/system/role'
import { tt } from '@/i18n'

const MAX_COLUMNS = 60
const MAX_GROUP = 5
const MAX_AGG = 10
const MAX_JOINS = 2
const MAX_CALC = 10
const FUNC_LABELS = { SUM: '合计', COUNT: '计数', AVG: '平均', MAX: '最大', MIN: '最小' }
const NUMERIC_TYPES = ['amount', 'number', 'int']
const CALC_OPS = ['+', '-', '*', '/']
const OP_SYMBOLS = { '+': '＋', '-': '－', '*': '×', '/': '÷' }
/** 常量操作数校验（与后端 NUMERIC_CONST 一致：可选负号 + 数字 + 可选小数） */
function validConst(v) {
  return v != null && /^-?\d+(\.\d+)?$/.test(String(v).trim())
}

/** 类型归并（与后端 typeKind 口径一致）：数值/文本/日期 内部互通 */
function typeKindOf(t) {
  if (NUMERIC_TYPES.includes(t)) return 'number'
  if (t === 'string') return 'string'
  if (t === 'date' || t === 'datetime') return 'date'
  return t || ''
}

const router = useRouter()
const route = useRoute()
const reportId = computed(() => route.query.id ? Number(route.query.id) : null)
const isEdit = computed(() => !!reportId.value)

// ========== 基本信息 ==========
const form = reactive({
  reportName: '',
  dataSource: '',
  remark: '',
  status: '启用',
  shareScope: '私有',
  mode: 'detail',       // detail 明细 / summary 汇总
  chartType: ''         // '' / bar / pie / line（汇总模式）
})
const reportCode = ref('')
const shareRoleCodes = ref([])
const saving = ref(false)

// ========== 子标签页 ==========
const activeTab = ref('fields')
/** 标签角标：字段与计算 = 已选列/分组数；跨单关联 = 已配关联数 */
const tabBadge = computed(() => ({
  fields: currentSelection.value.length || null,
  joins: extraJoinList.value.filter(j => j.dataSourceKey && j.leftRef && j.rightRef).length || null
}))

// ========== 元数据缓存（sessionStorage + buildId 失效） ==========
const CACHE_LIST_KEY = 'cr_ds_list'
const META_KEY_PREFIX = 'cr_meta_'
const currentBuildId = ref('')

function cacheGet(key) {
  try {
    const raw = sessionStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch (_) {
    return null
  }
}
function cacheSet(key, val) {
  try {
    sessionStorage.setItem(key, JSON.stringify(val))
  } catch (_) { /* 存储满等异常忽略，降级为每次拉取 */ }
}

// ========== 数据源 ==========
const dataSourceGroups = ref([])
const meta = ref(null) // describeDataSource 结果
const extMetas = reactive([]) // 跨单据关联目标元数据 [{prefix:'ext0', meta}]

const MODULE_LABELS = {
  sale: '销售', purchase: '采购', inventory: '库存', production: '生产',
  subcontract: '委外', finance: '财务', crm: '客户管理', sample: '打样'
}

function groupDataSources(list) {
  const groupMap = new Map()
  list.forEach(ds => {
    const label = MODULE_LABELS[ds.module] || ds.module
    if (!groupMap.has(ds.module)) groupMap.set(ds.module, { module: ds.module, label, items: [] })
    groupMap.get(ds.module).items.push(ds)
  })
  return [...groupMap.values()]
}

async function loadDataSources() {
  let cached = cacheGet(CACHE_LIST_KEY)
  if (!cached) {
    try {
      const res = await listDataSources()
      cacheSet(CACHE_LIST_KEY, res)
      cached = res
    } catch (e) {
      dataSourceGroups.value = []
      return
    }
  }
  currentBuildId.value = cached.buildId || ''
  dataSourceGroups.value = groupDataSources(cached.dataSources || [])
}

async function loadMeta(key) {
  const cacheKey = META_KEY_PREFIX + key
  const cached = cacheGet(cacheKey)
  if (cached && cached.buildId === currentBuildId.value) return cached.data
  const res = await describeDataSource(key)
  cacheSet(cacheKey, { buildId: currentBuildId.value, data: res })
  return res
}

// ========== 字段组 ==========
const fieldSearch = ref('')
const expandedGroups = reactive({})
const selectedColumns = ref([])  // 明细模式：已选显示列
const groupColumns = ref([])     // 汇总模式：已选分组字段
const aggregates = ref([])       // 汇总模式：[{func, ref:'source.field'|'__rows__', label}]
const calcColumns = ref([])      // 计算列（明细/汇总通用）：[{refA, op:'+|-|*|/', refB, constB, formula, label}]，refA/refB 可为 'source.field'/'calc.calcN'/'agg.别名'
const drillList = ref([])        // 行下钻目标：[{label, route, keyField, param}]
const MAX_DRILL = 4

/** 展开/收起某个聚合指标的口径条件编辑区 */
function toggleAggCond(a) {
  if (!Array.isArray(a.conditions)) a.conditions = []
  a.condOpen = !a.condOpen
}
function addAggCondition(a) {
  if (!Array.isArray(a.conditions)) a.conditions = []
  a.conditions.push({ fieldRef: '', op: 'eq', value: '', value2: '', relative: '' })
}

/** 条件行 → 报表配置结构（与运行页/后端一致） */
function serializeConditionRows(rows) {
  return (rows || [])
    .filter(c => c && c.fieldRef)
    .map(c => {
      const r = splitRef(c.fieldRef)
      const out = { source: r.source, field: r.field, op: c.relative ? 'between' : (c.op || 'eq') }
      if (c.relative) {
        out.relative = c.relative
      } else if (c.op === 'between') {
        out.value = c.value
        out.value2 = c.value2
      } else if (c.op === 'isNull' || c.op === 'isNotNull') {
        // 无需值
      } else {
        out.value = c.value
      }
      return out
    })
}

/** 报表配置结构 → 条件行 */
function parseConditionRows(list) {
  return (list || [])
    .filter(c => c && c.field)
    .map(c => ({
      fieldRef: `${c.source || 'main'}.${c.field}`,
      op: c.op || 'eq',
      value: c.value || '',
      value2: c.value2 || '',
      relative: c.relative || ''
    }))
}

const fieldGroups = computed(() => {
  const m = meta.value
  if (!m) return []
  const groups = [{ key: 'main', name: `主表 · ${m.name}`, fields: m.fields || [] }]
  ;(m.joins || []).forEach(j => {
    groups.push({ key: j.key, name: `关联 · ${j.name}`, fields: j.fields || [] })
  })
  extMetas.forEach(em => {
    const join = extraJoinList.value.find(x => x.dataSourceKey === em.meta.key)
    if (join?.aggMode) {
      // 聚合挂载：ext 分组只暴露虚拟聚合字段（agg_xxx/agg_count），不暴露目标原始字段（后端不提供）
      groups.push({ key: em.prefix, name: `跨单聚合 · ${em.meta.name}`, fields: joinVirtualFields(join) })
      return
    }
    groups.push({ key: em.prefix, name: `跨单 · ${em.meta.name}`, fields: em.meta.fields || [] })
    ;(em.meta.joins || []).forEach(j => {
      groups.push({ key: `${em.prefix}.${j.key}`, name: `跨单关联 · ${j.name}`, fields: j.fields || [] })
    })
  })
  return groups
})

/** 全部字段扁平清单（source.field 引用 + 元数据；options 供默认筛选条件用 select 型自定义字段的下拉候选） */
const allFieldRefs = computed(() => {
  const list = []
  fieldGroups.value.forEach(g => {
    g.fields.forEach(f => {
      list.push({ source: g.key, field: f.field, label: f.label, type: f.type, options: f.options || [], groupName: g.name })
    })
  })
  return list
})

const allFieldOptions = computed(() =>
  allFieldRefs.value.map(x => ({
    value: `${x.source}.${x.field}`,
    label: `${x.groupName.startsWith('主表') ? '' : x.groupName.split(' · ')[0] + ' · '}${x.label}`
  }))
)

/** 主数据源树（不含跨单 ext 分组）：跨单关联"本表字段"选项 */
const mainTreeGroups = computed(() => fieldGroups.value.filter(g => !g.key.startsWith('ext')))

/** 数值字段选项（计算列用） */
const numericFieldOptions = computed(() =>
  allFieldRefs.value
    .filter(x => NUMERIC_TYPES.includes(x.type))
    .map(x => ({
      value: `${x.source}.${x.field}`,
      label: `${x.groupName.startsWith('主表') ? '' : x.groupName.split(' · ')[0] + ' · '}${x.label}`
    }))
)

const filteredFieldGroups = computed(() => {
  const kw = fieldSearch.value.trim().toLowerCase()
  if (!kw) return fieldGroups.value
  return fieldGroups.value
    .map(g => ({
      ...g,
      fields: g.fields.filter(f =>
        (f.label || '').toLowerCase().includes(kw) || (f.field || '').toLowerCase().includes(kw))
    }))
    .filter(g => g.fields.length > 0)
})

function toggleGroup(key) {
  expandedGroups[key] = !expandedGroups[key]
}

// ----- 明细列 / 分组字段 统一操作（按模式分流） -----
const currentSelection = computed(() => form.mode === 'summary' ? groupColumns.value : selectedColumns.value)
const currentMax = computed(() => form.mode === 'summary' ? MAX_GROUP : MAX_COLUMNS)

function isSelected(source, field) {
  return currentSelection.value.some(c => c.source === source && c.field === field)
}

function toggleField(source, field) {
  const arr = currentSelection.value
  const idx = arr.findIndex(c => c.source === source && c.field === field)
  if (idx >= 0) {
    arr.splice(idx, 1)
    return
  }
  if (arr.length >= currentMax.value) {
    ElMessage.warning(form.mode === 'summary' ? `${tt("最多可选 ")}${MAX_GROUP}${tt(" 个分组字段")}` : `${tt("最多可选 ")}${MAX_COLUMNS}${tt(" 列")}`)
    return
  }
  const g = fieldGroups.value.find(x => x.key === source)
  const f = g?.fields.find(x => x.field === field)
  arr.push({ source, field, label: f?.label || field, groupName: g?.name || source, type: f?.type || 'string', granularity: '' })
}

function moveSelected(idx, delta) {
  const arr = currentSelection.value
  const target = idx + delta
  if (target < 0 || target >= arr.length) return
  const tmp = arr[idx]
  arr[idx] = arr[target]
  arr[target] = tmp
}

function removeSelected(idx) {
  currentSelection.value.splice(idx, 1)
}

// ========== 聚合指标 ==========
function addAggregate() {
  aggregates.value.push({ func: 'SUM', ref: '', label: '', compare: '' })
}

function onAggFuncChange(a) {
  if (a.ref === '__rows__' && a.func !== 'COUNT') {
    a.func = 'COUNT'
    ElMessage.info('tt("行数")仅支持 COUNT 计数')
  }
}

function onAggRefChange(a) {
  if (a.ref === '__rows__') a.func = 'COUNT'
}

function defaultAggLabel(a) {
  if (!a.func) return ''
  if (!a.ref || a.ref === '__rows__') return '行数'
  const f = allFieldRefs.value.find(x => `${x.source}.${x.field}` === a.ref)
  return `${FUNC_LABELS[a.func] || a.func}(${f?.label || a.ref})`
}

/** 聚合输出列名（与后端 parseSummarySpec 命名一致，排序引用用） */
function aggOutputField(a) {
  if (!a.ref || a.ref === '__rows__') return 'count_all'
  const r = splitRef(a.ref)
  const base = `${a.func.toLowerCase()}_${r.field}`
  return r.source && r.source.startsWith('ext') ? `e${r.source.slice(3)}_${base}` : base
}

// ========== 计算列（明细/汇总通用，批次3：±×÷ + 链式引用 + 常量） ==========

const calcEmptyTip = computed(() => form.mode === 'summary'
  ? '可选：聚合指标 ±×÷ 聚合指标/常量 生成新列（如 合计金额 × 0.13 = 税额）'
  : '可选：数值字段A ±×÷ 数值字段B/更早计算列/常量 生成新列（如 订货数量 - 已发数量 = 剩余数量）')

const calcTip = computed(() => form.mode === 'summary'
  ? '仅可引用上方聚合指标；字段B留空时可填数字常量；空值按 0 参与运算；除数为 0 时结果为空白'
  : '仅支持数值字段；可引用更早定义的计算列链式运算；字段B留空时可填数字常量；空值按 0 参与运算，除数为 0 时结果为空白')

function addCalc() {
  calcColumns.value.push({ refA: '', op: '-', refB: '', constB: '', label: '' })
}

/** 选了字段B时清掉常量（两者互斥，字段B优先） */
function onCalcRefBChange(c) {
  if (c.refB) c.constB = ''
}

/** 计算列操作数选项：明细=数值字段+更早计算列；汇总=聚合指标输出别名 */
function calcOperandOptions(idx) {
  if (form.mode === 'summary') {
    return aggregates.value
      .filter(a => a.func && (a.ref || a.func === 'COUNT'))
      .map(a => ({ value: `agg.${aggOutputField(a)}`, label: `聚合 · ${a.label?.trim() || defaultAggLabel(a)}` }))
  }
  const opts = numericFieldOptions.value.map(x => ({ ...x }))
  calcColumns.value.forEach((c, i) => {
    // 仅可引用更早定义且配置完整的计算列（calcN 为 1 基序号，n ≤ 当前 0 基 idx）
    if (i < idx && c.refA && (c.refB || validConst(c.constB))) {
      opts.push({ value: `calc.calc${i + 1}`, label: `计算 · ${c.label?.trim() || defaultCalcLabel(c)}` })
    }
  })
  return opts
}

/** 操作数引用 → 显示名（支持 字段/更早计算列/聚合指标） */
function calcRefLabel(refStr) {
  if (!refStr) return ''
  if (refStr.startsWith('calc.')) {
    const n = Number(refStr.slice(5))
    const c = calcColumns.value[n - 1]
    return c ? (c.label?.trim() || defaultCalcLabel(c)) : refStr
  }
  if (refStr.startsWith('agg.')) {
    const alias = refStr.slice(4)
    const a = aggregates.value.find(x => aggOutputField(x) === alias)
    return a ? (a.label?.trim() || defaultAggLabel(a)) : alias
  }
  const f = allFieldRefs.value.find(x => `${x.source}.${x.field}` === refStr)
  return f?.label || refStr
}

function defaultCalcLabel(c) {
  if (!c.refA) return ''
  const la = calcRefLabel(c.refA)
  const lb = c.refB ? calcRefLabel(c.refB) : (validConst(c.constB) ? String(c.constB).trim() : '')
  if (!la || !lb) return ''
  return `${la} ${OP_SYMBOLS[c.op] || c.op} ${lb}`
}

// ========== 模式切换：明细/汇总各自预填常用配置 + 清理另一模式的计算列引用 ==========
function onModeChange(v) {
  // 计算列操作数随模式不同（明细=字段/更早计算列，汇总=聚合指标），切换后全部失效
  calcColumns.value = []
  if (v === 'summary' && !groupColumns.value.length && !aggregates.value.length) {
    prefillSummaryDefaults()
  }
  if (v === 'detail' && !selectedColumns.value.length) {
    prefillDetailDefaults()
  }
}

/** 汇总模式预填：分组=客户/供应商/物料名称（否则首个文本字段），聚合=首个金额字段SUM + 行数COUNT */
function prefillSummaryDefaults() {
  const refs = allFieldRefs.value
  if (!refs.length) return
  const g = refs.find(x => ['customerName', 'supplierName', 'materialName'].includes(x.field) && x.type === 'string')
    || refs.find(x => x.type === 'string' && /Name$/.test(x.field))
    || refs.find(x => x.type === 'string')
  if (g) {
    groupColumns.value.push({ source: g.source, field: g.field, label: g.label, groupName: g.groupName, type: g.type, granularity: '' })
  }
  const amt = refs.find(x => x.type === 'amount')
  if (amt) aggregates.value.push({ func: 'SUM', ref: `${amt.source}.${amt.field}`, label: '', compare: '' })
  aggregates.value.push({ func: 'COUNT', ref: '__rows__', label: '', compare: '' })
}

/** 明细模式预填：单号/日期/往来单位/物料名称 + 数量 + 金额等常用列（按字段名命中，最多 8 列） */
function prefillDetailDefaults() {
  const refs = allFieldRefs.value
  if (!refs.length || selectedColumns.value.length) return
  // 按优先级排列的常用字段名（camelCase 精确匹配）
  const wanted = [
    { match: ['orderNo', 'purchaseNo', 'saleNo', 'receiptNo', 'invoiceNo', 'docNo', 'stockNo', 'code'], type: 'string', why: '单号' },
    { match: [/Date$/, 'bizDate', 'createTime'], type: 'date', why: '日期' },
    { match: ['customerName', 'supplierName', 'materialName', 'productName'], type: 'string', why: '名称' },
    { match: ['quantity', 'qty', 'number'], type: 'number', why: '数量' },
    { match: [/Amount$/, 'money', 'price'], type: 'amount', why: '金额' },
    { match: ['status', 'state'], type: 'string', why: '状态' }
  ]
  const picked = []
  for (const w of wanted) {
    const hit = refs.find(x => !picked.includes(x)
      && w.match.some(m => (m instanceof RegExp ? m.test(x.field) : x.field === m))
      && (w.type ? x.type === w.type : true))
    if (hit) picked.push(hit)
    if (picked.length >= 8) break
  }
  selectedColumns.value = picked.map(x => ({ source: x.source, field: x.field, label: x.label, groupName: x.groupName, type: x.type }))
}

// ========== 跨单据关联 ==========
const extraJoinList = ref([]) // [{dataSourceKey, leftRef, rightRef, rightTree, detailTree, loading, aggMode, aggFields:[{source,field,func,label}]}]

function addJoin() {
  extraJoinList.value.push({ dataSourceKey: '', leftRef: '', rightRef: '', rightTree: [], detailTree: [], loading: false, aggMode: false, aggFields: [], viaKey: '', viaTarget: '', viaMain: '', viaTree: [], viaLoading: false })
}

/** 两跳追溯：跳板数据源切换时加载其主表字段（选跳板单号/来源单号字段用） */
async function onViaKeyChange(idx) {
  const j = extraJoinList.value[idx]
  j.viaTarget = ''
  j.viaMain = ''
  j.viaTree = []
  if (!j.viaKey) return
  j.viaLoading = true
  try {
    const res = await loadMeta(j.viaKey)
    j.viaTree = res?.fields || []
  } catch (e) {
    j.viaTree = []
  } finally {
    j.viaLoading = false
  }
}

/** 聚合挂载开关切换：开启时目标字段锁定目标主表，且预置一条空聚合指标 */
function onAggModeChange(idx) {
  const j = extraJoinList.value[idx]
  if (j.aggMode) {
    if (!j.aggFields.length) j.aggFields.push({ source: '', field: '', func: 'SUM', label: '' })
    // 目标字段必须是目标主表：若当前选了非 main 源则清空
    if (j.rightRef && !j.rightRef.startsWith('main.')) j.rightRef = ''
  }
  purgeInvalidRefs()
}

/** 目标主表字段（聚合指标字段下拉选项：全部字段，含数量/金额等） */
function targetMainFields(j) {
  const main = (j.rightTree || []).find(g => g.source === 'main')
  return main?.fields || []
}

/** 聚合挂载虚拟字段清单（与后端 SqlBuilder 命名约定一致：agg_{field} / agg_count） */
function joinVirtualFields(j) {
  const items = []
  const mainFields = targetMainFields(j)
  ;(j.aggFields || []).forEach(af => {
    if (af.func === 'COUNT' && !af.field) {
      items.push({ field: 'agg_count', label: af.label?.trim() || '单据数', type: 'number' })
    } else if (af.field) {
      const pool = af.source ? detailFieldsOf(j, af.source) : mainFields
      const src = pool.find(f => f.field === af.field)
      items.push({
        field: 'agg_' + af.field,
        label: af.label?.trim() || `${af.func}(${src?.label || af.field})`,
        type: src?.type === 'amount' ? 'amount' : 'number'
      })
    }
  })
  return items
}

/** 明细表字段清单（聚合挂载选明细字段用，来源=detailTree 中对应明细表分组） */
function detailFieldsOf(j, sourceKey) {
  const g = (j.detailTree || []).find(x => x.source === sourceKey)
  return g?.fields || []
}

/** 聚合指标字段选项：来源表=明细表时取明细字段，否则取目标主表字段 */
function aggFieldOptions(j, af) {
  if (af.source) return detailFieldsOf(j, af.source)
  return targetMainFields(j)
}

/** 同一关联内主表字段与明细表字段混配检测（COUNT 单据数不算主表字段，可与明细混用） */
function hasAggSourceMix(j) {
  const list = j.aggFields || []
  const hasDetail = list.some(af => af.source && af.field)
  const hasMain = list.some(af => !af.source && af.field)
  return hasDetail && hasMain
}

function removeJoin(idx) {
  extraJoinList.value.splice(idx, 1)
  syncExtMetas()
}

function joinTargetOptions(idx, group) {
  const used = extraJoinList.value.filter((_, i) => i !== idx).map(j => j.dataSourceKey)
  const cur = extraJoinList.value[idx]
  // 聚合挂载模式允许选主表自身（自挂载聚合本表明细，如订单数量=SUM(订单明细.quantity)）；平铺模式排除（纯行翻倍无意义）
  const allowSelf = cur && cur.aggMode
  return group.items.filter(ds => (allowSelf || ds.key !== form.dataSource) && !used.includes(ds.key))
}

async function onJoinTargetChange(idx) {
  const j = extraJoinList.value[idx]
  j.rightRef = ''
  j.rightTree = []
  j.detailTree = []
  if (j.viaKey === j.dataSourceKey) {
    j.viaKey = ''
    j.viaTarget = ''
    j.viaMain = ''
    j.viaTree = []
  }
  if (!j.dataSourceKey) {
    syncExtMetas()
    return
  }
  j.loading = true
  try {
    const res = await joinableColumns(form.dataSource, j.dataSourceKey)
    j.rightTree = res?.rightTree || []
    j.detailTree = res?.rightDetailTree || []
  } catch (e) {
    j.rightTree = []
    j.detailTree = []
    if (e?.message) ElMessage.error(e.message)
  } finally {
    j.loading = false
  }
  await syncExtMetas()
}

/** 目标字段选项：已选本表字段时，仅保留类型兼容（值匹配）的目标字段 */
function compatibleRightTree(j) {
  const tree = j.rightTree || []
  if (!j.leftRef) return tree
  const lf = allFieldRefs.value.find(x => `${x.source}.${x.field}` === j.leftRef)
  if (!lf) return tree
  const kind = typeKindOf(lf.type)
  const filtered = tree
    .map(g => ({ ...g, fields: (g.fields || []).filter(f => typeKindOf(f.type) === kind) }))
    .filter(g => g.fields.length > 0)
  // 左字段类型变化后，原右字段可能不再兼容，自动清空
  if (j.rightRef) {
    const hit = filtered.some(g => (g.fields || []).some(f => `${g.source}.${f.field}` === j.rightRef))
    if (!hit) j.rightRef = ''
  }
  return filtered
}

/** 使 extMetas 与有效关联列表对齐（prefix=ext{i} 按顺序），并清理失效字段引用 */
async function syncExtMetas() {
  const valid = extraJoinList.value.filter(j => j.dataSourceKey)
  const newMetas = []
  for (let i = 0; i < valid.length; i++) {
    const prefix = 'ext' + i
    const existing = extMetas.find(m => m.prefix === prefix && m.meta?.key === valid[i].dataSourceKey)
    if (existing) {
      newMetas.push(existing)
    } else {
      try {
        const targetMeta = await loadMeta(valid[i].dataSourceKey)
        newMetas.push({ prefix, meta: targetMeta })
      } catch (e) {
        // 目标元数据加载失败：跳过该关联的字段树扩展（不影响其它功能）
      }
    }
  }
  extMetas.splice(0, extMetas.length, ...newMetas)
  purgeInvalidRefs()
}

/** 清理指向已不存在 source 的字段引用（跨单关联变动后） */
function purgeInvalidRefs() {
  const validSources = new Set(fieldGroups.value.map(g => g.key))
  selectedColumns.value = selectedColumns.value.filter(c => validSources.has(c.source))
  groupColumns.value = groupColumns.value.filter(c => validSources.has(c.source))
  aggregates.value = aggregates.value.filter(a => !a.ref || a.ref === '__rows__' || validSources.has(splitRef(a.ref).source))
  // 计算列引用：calc.*/agg.* 前缀不依赖字段树，其余按 source 有效性清理
  calcColumns.value = calcColumns.value.filter(c => {
    const refOk = v => !v || v.startsWith('calc.') || v.startsWith('agg.') || validSources.has(splitRef(v).source)
    return refOk(c.refA) && refOk(c.refB)
  })
  const cleanFilter = v => (!v || validSources.has(splitRef(v).source)) ? v : ''
  filterRefs.dateField = cleanFilter(filterRefs.dateField)
  filterRefs.keywordField = cleanFilter(filterRefs.keywordField)
  filterRefs.statusField = cleanFilter(filterRefs.statusField)
  orderList.value = orderList.value.filter(o => !o.field || o.field.startsWith('agg.') || o.field.startsWith('calc.') || validSources.has(splitRef(o.field).source))
  // 默认筛选条件同样按源失效修剪（元数据变更后不留死引用）
  defaultFilters.value = defaultFilters.value.filter(c => !c.fieldRef || validSources.has(splitRef(c.fieldRef).source))
}

// ========== 筛选与排序 ==========
const filterRefs = reactive({ dateField: '', keywordField: '', statusField: '' })
const orderList = ref([])

// ========== 默认筛选条件（config.filters）：打开报表时预填，支持自定义字段与「且/或」成组 ==========
const defaultFilters = ref([])
const DF_OP_LABELS = {
  eq: '等于', ne: '不等于', gt: '大于', ge: '大于等于', lt: '小于', le: '小于等于',
  like: '包含', notLike: '不包含', between: '区间', in: '属于', notIn: '不属于',
  isNull: '为空', isNotNull: '不为空'
}

function addDefaultFilter() {
  defaultFilters.value.push({ fieldRef: '', op: 'eq', value: undefined, value2: undefined, values: [], logic: 'AND' })
}

function dfMeta(c) {
  if (!c.fieldRef) return null
  const dot = c.fieldRef.indexOf('.')
  if (dot <= 0) return null
  const source = c.fieldRef.slice(0, dot)
  const field = c.fieldRef.slice(dot + 1)
  return allFieldRefs.value.find(x => x.source === source && x.field === field) || null
}

function dfOptionsOf(c) {
  const m = dfMeta(c)
  return (m && Array.isArray(m.options)) ? m.options : []
}

function dfIsNumType(t) {
  return t === 'number' || t === 'amount' || t === 'int'
}

function dfOpOptions(c) {
  const t = dfMeta(c)?.type || 'string'
  let ops
  if (dfIsNumType(t) || t === 'date' || t === 'datetime') {
    ops = ['eq', 'ne', 'gt', 'ge', 'lt', 'le', 'between']
  } else if (t === 'select' || t === 'bool') {
    ops = ['eq', 'ne', 'in', 'notIn']
  } else {
    ops = ['eq', 'ne', 'like', 'notLike', 'in', 'notIn']
  }
  return ops.concat(['isNull', 'isNotNull']).map(v => ({ value: v, label: DF_OP_LABELS[v] || v }))
}

function dfValuePlaceholder(c) {
  const t = dfMeta(c)?.type || 'string'
  if (dfIsNumType(t)) return '数值'
  if (t === 'date') return 'YYYY-MM-DD'
  if (t === 'datetime') return 'YYYY-MM-DD HH:mm:ss'
  return '值'
}

function onDfFieldChange(c) {
  c.op = dfOpOptions(c)[0]?.value || 'eq'
  c.value = undefined
  c.value2 = undefined
  c.values = []
}

/** 序列化默认筛选（未填值的行丢弃，与后端"空值条件不生效"口径一致） */
function buildDefaultFilters() {
  const out = []
  for (const c of defaultFilters.value) {
    if (!c.fieldRef) continue
    const dot = c.fieldRef.indexOf('.')
    if (dot <= 0) continue
    const op = c.op || 'eq'
    const item = { source: c.fieldRef.slice(0, dot), field: c.fieldRef.slice(dot + 1), op }
    if (op === 'in' || op === 'notIn') {
      const vals = (c.values || []).filter(v => v !== null && v !== undefined && String(v).trim() !== '')
      if (!vals.length) continue
      item.values = vals.map(v => String(v))
    } else if (op === 'isNull' || op === 'isNotNull') {
      // 无需值
    } else if (op === 'between') {
      if (c.value === undefined || c.value === null || String(c.value).trim() === '') continue
      if (c.value2 === undefined || c.value2 === null || String(c.value2).trim() === '') continue
      item.value = String(c.value)
      item.value2 = String(c.value2)
    } else {
      if (c.value === undefined || c.value === null || String(c.value).trim() === '') continue
      item.value = String(c.value)
    }
    if (c.logic === 'OR') item.logic = 'OR'
    out.push(item)
  }
  return out
}

function fieldsOfType(types) {
  return allFieldRefs.value
    .filter(x => types.includes(x.type))
    .map(x => ({
      value: `${x.source}.${x.field}`,
      label: `${x.groupName.startsWith('主表') ? '' : x.groupName.split(' · ')[0] + ' · '}${x.label}`
    }))
}

/** 排序字段选项：明细=全部字段+计算列；汇总=分组字段+聚合指标+计算列 */
const orderFieldOptions = computed(() => {
  if (form.mode !== 'summary') {
    const opts = [...allFieldOptions.value]
    calcColumns.value.forEach((c, i) => {
      if (c.refA && (c.refB || validConst(c.constB))) {
        opts.push({ value: `calc.calc${i + 1}`, label: `计算 · ${c.label?.trim() || defaultCalcLabel(c)}` })
      }
    })
    return opts
  }
  const opts = []
  groupColumns.value.forEach(c => {
    opts.push({ value: `${c.source}.${c.field}`, label: `分组 · ${c.label}` })
  })
  aggregates.value.forEach(a => {
    if (a.func && (a.ref || a.func === 'COUNT')) {
      opts.push({ value: `agg.${aggOutputField(a)}`, label: `聚合 · ${a.label?.trim() || defaultAggLabel(a)}` })
    }
  })
  // 汇总计算列输出别名 calc_N 亦在聚合排序白名单内（后端 aggAliases 含全部非分组列）
  calcColumns.value.forEach((c, i) => {
    if (c.refA && (c.refB || validConst(c.constB))) {
      opts.push({ value: `agg.calc_${i + 1}`, label: `计算 · ${c.label?.trim() || defaultCalcLabel(c)}` })
    }
  })
  return opts
})

// ========== 数据源切换 ==========
async function onDataSourceChange(key) {
  if (!key) { meta.value = null; return }
  try {
    meta.value = await loadMeta(key)
    // 切换数据源后清空全部字段引用（含跨单关联）
    selectedColumns.value = []
    groupColumns.value = []
    aggregates.value = []
    calcColumns.value = []
    extraJoinList.value = []
    extMetas.splice(0, extMetas.length)
    filterRefs.dateField = ''
    filterRefs.keywordField = ''
    filterRefs.statusField = ''
    orderList.value = []
    defaultFilters.value = []
    Object.keys(expandedGroups).forEach(k => delete expandedGroups[k])
    expandedGroups['main'] = true
    // 按模式预填常用配置
    if (form.mode === 'summary') prefillSummaryDefaults()
    else prefillDetailDefaults()
  } catch (e) {
    meta.value = null
    ElMessage.error(e.message || tt('数据源元数据加载失败'))
  }
}

// ========== 编辑加载 ==========
async function loadForEdit() {
  if (!reportId.value) return
  try {
    const detail = await getCustomReport(reportId.value)
    form.reportName = detail.reportName || ''
    form.dataSource = detail.dataSource || ''
    form.remark = detail.remark || ''
    form.status = detail.status || '启用'
    form.shareScope = detail.shareScope || '私有'
    reportCode.value = detail.reportCode || ''
    shareRoleCodes.value = detail.shareRoles ? detail.shareRoles.split(',').map(s => s.trim()).filter(Boolean) : []
    // 加载元数据（不触发 onDataSourceChange 清空）
    if (form.dataSource) {
      meta.value = await loadMeta(form.dataSource)
      expandedGroups['main'] = true
    }
    // 恢复配置
    let config = null
    try { config = detail.configJson ? JSON.parse(detail.configJson) : null } catch (_) { config = null }
    if (!config) return
    form.mode = config.mode === 'summary' ? 'summary' : 'detail'
    form.chartType = config.chart?.type || ''

    // 先恢复跨单关联（字段树需要 ext 分组），再恢复字段引用
    if (Array.isArray(config.extraJoins)) {
      for (const ej of config.extraJoins) {
        if (!ej?.dataSourceKey) continue
        const row = {
          dataSourceKey: ej.dataSourceKey,
          leftRef: ej.leftField?.source ? `${ej.leftField.source}.${ej.leftField.field}` : '',
          rightRef: ej.rightField?.source ? `${ej.rightField.source}.${ej.rightField.field}` : '',
          rightTree: [],
          detailTree: [],
          loading: false,
          aggMode: Array.isArray(ej.aggFields) && ej.aggFields.length > 0,
          aggFields: (ej.aggFields || []).map(af => ({
            source: af.source || '',
            field: af.field || '',
            func: (af.func || 'SUM').toUpperCase(),
            label: af.label || '',
            alias: af.alias || '',
            conditions: parseConditionRows(af.filters),
            condOpen: false
          })),
          viaKey: ej.via?.dataSourceKey || '',
          viaTarget: ej.via?.targetField || '',
          viaMain: ej.via?.mainField || '',
          viaTree: [],
          viaLoading: false
        }
        extraJoinList.value.push(row)
        try {
          const res = await joinableColumns(form.dataSource, ej.dataSourceKey)
          row.rightTree = res?.rightTree || []
          row.detailTree = res?.rightDetailTree || []
        } catch (_) { /* 关联字段树加载失败不阻塞编辑 */ }
        if (row.viaKey) {
          try {
            const viaMeta = await loadMeta(row.viaKey)
            row.viaTree = viaMeta?.fields || []
          } catch (_) { /* 跳板字段加载失败不阻塞编辑 */ }
        }
      }
      await syncExtMetas()
    }

    // 恢复列/分组/聚合/计算列
    const restoreRefs = (refs, target) => {
      ;(refs || []).forEach(c => {
        if (!c?.source || !c.field) return
        const g = fieldGroups.value.find(x => x.key === c.source)
        const f = g?.fields.find(x => x.field === c.field)
        if (g && f) {
          target.push({ source: c.source, field: c.field, label: f.label, groupName: g.name, type: f.type, granularity: c.granularity && c.granularity !== 'day' ? c.granularity : '' })
        }
      })
    }
    restoreRefs(config.columns, selectedColumns.value)
    restoreRefs(config.groupBy, groupColumns.value)
    ;(config.aggregates || []).forEach(a => {
      aggregates.value.push({
        func: (a.func || 'SUM').toUpperCase(),
        ref: a.field ? `${a.source || 'main'}.${a.field}` : '__rows__',
        label: a.label || '',
        compare: a.compare === 'pct' ? 'pct' : '',
        alias: a.alias || '',
        conditions: parseConditionRows(a.filters),
        condOpen: false
      })
    })
    ;(config.calcColumns || []).forEach(c => {
      const toRef = r => (r?.source && r?.field) ? `${r.source}.${r.field}` : ''
      const refA = toRef(c?.refA)
      if (!refA && !c.formula) return
      calcColumns.value.push({
        refA,
        op: CALC_OPS.includes(c.op) ? c.op : '+',
        refB: toRef(c?.refB),
        constB: c?.constB || '',
        formula: c?.formula || '',
        label: c?.label || ''
      })
    })
    drillList.value = (config.drill || []).map(d => ({
      label: d.label || '',
      route: d.route || '',
      keyField: d.keyField || '',
      param: d.param || ''
    }))

    // 恢复筛选与排序
    if (config.dateField) filterRefs.dateField = `${config.dateField.source}.${config.dateField.field}`
    if (config.keywordField) filterRefs.keywordField = `${config.keywordField.source}.${config.keywordField.field}`
    if (config.statusField) filterRefs.statusField = `${config.statusField.source}.${config.statusField.field}`
    defaultFilters.value = []
    ;(config.filters || []).forEach(f => {
      if (!f || !f.source || !f.field) return
      defaultFilters.value.push({
        fieldRef: `${f.source}.${f.field}`,
        op: f.op || 'eq',
        value: f.value,
        value2: f.value2,
        values: Array.isArray(f.values) ? f.values.slice() : [],
        logic: String(f.logic || 'AND').toUpperCase() === 'OR' ? 'OR' : 'AND'
      })
    })
    ;(config.orderBy || []).forEach(o => {
      if (o && o.source && o.field) orderList.value.push({ field: `${o.source}.${o.field}`, dir: o.dir || 'desc' })
    })
  } catch (e) {
    ElMessage.error(e.message || tt('报表加载失败'))
  }
}

// ========== 角色选项 ==========
const roleOptions = ref([])
async function loadRoles() {
  try {
    roleOptions.value = (await listAllRole()) || []
  } catch (e) {
    roleOptions.value = []
  }
}

// ========== 保存 ==========
/** 按"最后一个点"拆分（source 可能含点：ext0.customer；field 为 camelCase 无点） */
function splitRef(refStr) {
  if (!refStr) return { source: '', field: '' }
  const idx = refStr.lastIndexOf('.')
  if (idx < 0) return { source: '', field: refStr }
  return { source: refStr.slice(0, idx), field: refStr.slice(idx + 1) }
}

function buildConfig() {
  const config = { mode: form.mode === 'summary' ? 'summary' : 'detail' }
  if (form.mode === 'summary') {
    config.groupBy = groupColumns.value.map(c => {
      const item = { source: c.source, field: c.field }
      if (c.granularity && c.granularity !== 'day') item.granularity = c.granularity
      return item
    })
    config.aggregates = aggregates.value
      .filter(a => a.func && (a.ref || a.func === 'COUNT'))
      .map(a => {
        const r = splitRef(a.ref)
        const agg = { func: a.func.toUpperCase() }
        if (a.ref && a.ref !== '__rows__') {
          agg.source = r.source
          agg.field = r.field
        }
        if (a.label?.trim()) agg.label = a.label.trim()
        if (a.alias?.trim()) agg.alias = a.alias.trim()
        if (a.compare === 'pct') agg.compare = 'pct'
        const conds = serializeConditionRows(a.conditions)
        if (conds.length) agg.filters = conds
        return agg
      })
    if (form.chartType) config.chart = { type: form.chartType }
  } else {
    config.columns = selectedColumns.value.map(c => ({ source: c.source, field: c.field }))
  }
  // 计算列（明细/汇总通用）：操作数A/B = 字段引用 或 calc.calcN/agg.别名，refB 空时用数字常量
  const calcs = calcColumns.value
    .filter(c => (c.formula && String(c.formula).trim()) || (c.refA && CALC_OPS.includes(c.op) && (c.refB || validConst(c.constB))))
    .map(c => {
      const cc = {}
      if (c.formula && String(c.formula).trim()) {
        cc.formula = String(c.formula).trim()
      } else {
        const a = splitRef(c.refA)
        cc.refA = { source: a.source, field: a.field }
        cc.op = c.op
        if (c.refB) {
          const b = splitRef(c.refB)
          cc.refB = { source: b.source, field: b.field }
        } else {
          cc.constB = String(c.constB).trim()
        }
      }
      if (c.label?.trim()) cc.label = c.label.trim()
      return cc
    })
  if (calcs.length) config.calcColumns = calcs
  // 行下钻目标
  const drills = drillList.value
    .filter(d => d.label?.trim() && d.route?.trim() && d.keyField?.trim() && d.param?.trim())
    .map(d => ({
      label: d.label.trim(),
      route: d.route.trim(),
      keyField: d.keyField.trim(),
      param: d.param.trim()
    }))
  if (drills.length) config.drill = drills
  if (extraJoinList.value.length) {
    const joins = extraJoinList.value
      .filter(j => j.dataSourceKey && j.leftRef && j.rightRef)
      .map(j => {
        const l = splitRef(j.leftRef)
        const r = splitRef(j.rightRef)
        const item = {
          dataSourceKey: j.dataSourceKey,
          leftField: { source: l.source, field: l.field },
          rightField: { source: r.source, field: r.field }
        }
        if (j.aggMode) {
          item.aggFields = (j.aggFields || [])
            .filter(af => af.func && (af.field || af.func === 'COUNT'))
            .map(af => {
              const a = { func: af.func.toUpperCase() }
              if (af.source) a.source = af.source
              if (af.field) a.field = af.field
              if (af.label?.trim()) a.label = af.label.trim()
              if (af.alias?.trim()) a.alias = af.alias.trim()
              const fs = serializeConditionRows(af.conditions)
              if (fs.length) a.filters = fs
              return a
            })
          // 两跳追溯：跳板两个字段都选完才序列化（半配不生效）
          if (j.viaKey && j.viaTarget && j.viaMain) {
            item.via = { dataSourceKey: j.viaKey, targetField: j.viaTarget, mainField: j.viaMain }
          }
        }
        return item
      })
    if (joins.length) config.extraJoins = joins
  }
  const d = splitRef(filterRefs.dateField)
  if (d.field) config.dateField = d
  const k = splitRef(filterRefs.keywordField)
  if (k.field) config.keywordField = k
  const s = splitRef(filterRefs.statusField)
  if (s.field) config.statusField = s
  const orderBy = orderList.value
    .filter(o => o.field)
    .map(o => {
      const r = splitRef(o.field)
      return r.field ? { source: r.source, field: r.field, dir: o.dir || 'desc' } : null
    })
    .filter(Boolean)
  if (orderBy.length) config.orderBy = orderBy
  // 默认筛选条件（config.filters）：打开报表时预填；用户运行页自带条件时以其为准
  const df = buildDefaultFilters()
  if (df.length) config.filters = df
  return config
}

function validate() {
  if (!form.reportName || !form.reportName.trim()) {
    ElMessage.warning(tt('请输入报表名称'))
    return false
  }
  if (!form.dataSource) {
    ElMessage.warning(tt('请选择数据源'))
    return false
  }
  if (form.mode === 'summary') {
    if (!groupColumns.value.length) {
      ElMessage.warning(tt('汇总模式请至少选择一个分组字段'))
      return false
    }
    if (groupColumns.value.length > MAX_GROUP) {
      ElMessage.warning(`${tt("分组字段最多 ")}${MAX_GROUP}${tt(" 个")}`)
      return false
    }
    const validAggs = aggregates.value.filter(a => a.func)
    if (!validAggs.length) {
      ElMessage.warning(tt('汇总模式请至少添加一个聚合指标'))
      return false
    }
    for (const a of validAggs) {
      if (!a.ref || a.ref === '__rows__') {
        if (a.func !== 'COUNT') {
          ElMessage.warning(tt('只有 COUNT 可统计行数，其它聚合函数请选择字段'))
          return false
        }
        continue
      }
      const r = splitRef(a.ref)
      const f = allFieldRefs.value.find(x => x.source === r.source && x.field === r.field)
      if (!f) {
        ElMessage.warning(tt('聚合指标存在失效字段，请重新选择'))
        return false
      }
      if ((a.func === 'SUM' || a.func === 'AVG') && f.type !== 'amount' && f.type !== 'int') {
        ElMessage.warning(`${tt("SUM/AVG 仅支持数值字段：")}${f.label}`)
        return false
      }
    }
  } else {
    if (!selectedColumns.value.length) {
      ElMessage.warning(tt('请至少选择一个显示字段'))
      return false
    }
  }
  // 计算列校验（明细/汇总通用：空行忽略，填了一半拦截；模式来源与链式引用校验）
  for (let i = 0; i < calcColumns.value.length; i++) {
    const c = calcColumns.value[i]
    // 自由公式行：字段/运算符由后端公式引擎校验，这里不再套两两运算的必填规则
    if (c.formula && String(c.formula).trim()) continue
    if (!c.refA && !c.refB && !String(c.constB || '').trim()) continue
    if (!CALC_OPS.includes(c.op)) {
      ElMessage.warning(tt('计算列运算符仅支持 + - * /'))
      return false
    }
    if (!c.refA) {
      ElMessage.warning(tt('存在未配置完整的计算列，请补全操作数A或删除该行'))
      return false
    }
    if (!c.refB && !validConst(c.constB)) {
      ElMessage.warning(tt('计算列操作数B需选择字段/指标，或填写数字常量'))
      return false
    }
    const refOkForMode = v => {
      if (!v) return true
      if (form.mode === 'summary') return v.startsWith('agg.')
      return !v.startsWith('agg.') // 明细模式 calc. 引用合法，由 chainOk 校验序号
    }
    const chainOk = v => {
      if (!v || !v.startsWith('calc.')) return true
      const n = Number(v.slice(5))
      return Number.isInteger(n) && n >= 1 && n <= i // 仅可引用更早定义的计算列
    }
    if (!refOkForMode(c.refA) || !refOkForMode(c.refB)) {
      ElMessage.warning(form.mode === 'summary'
        ? tt('汇总模式计算列仅可引用聚合指标')
        : tt('明细模式计算列仅可引用数值（金额/数值）字段或更早的计算列'))
      return false
    }
    if (!chainOk(c.refA) || !chainOk(c.refB)) {
      ElMessage.warning(tt('计算列仅可引用更早定义的计算列（不能引用自身或之后的计算列）'))
      return false
    }
  }
  for (const j of extraJoinList.value) {
    if (j.dataSourceKey && (!j.leftRef || !j.rightRef)) {
      ElMessage.warning('存在未选择关联字段的跨单据关联，请补全tt("本表字段 = 目标字段")或移除')
      return false
    }
    if (j.dataSourceKey && j.leftRef && j.rightRef) {
      const lf = allFieldRefs.value.find(x => `${x.source}.${x.field}` === j.leftRef)
      const rf = (j.rightTree || []).some(g => (g.fields || []).some(f => `${g.source}.${f.field}` === j.rightRef))
      if (lf && !rf) {
        ElMessage.warning(`${tt("关联的目标字段已失效（")}${lf.label}${tt("），请重新选择目标字段")}`)
        return false
      }
    }
    // 聚合挂载校验：指标完整性 + 主表/明细混配拦截（与后端 SqlBuilder 口径一致）
    if (j.aggMode && j.dataSourceKey && j.leftRef && j.rightRef) {
      const validAgg = (j.aggFields || []).filter(af => af.func && (af.field || af.func === 'COUNT'))
      if (!validAgg.length) {
        ElMessage.warning(tt('聚合挂载关联至少需要一条完整的聚合指标'))
        return false
      }
      const incomplete = (j.aggFields || []).some(af => af.func && af.func !== 'COUNT' && !af.field)
      if (incomplete) {
        ElMessage.warning(tt('存在未选择字段的聚合指标，请补全或删除该行'))
        return false
      }
      if (hasAggSourceMix(j)) {
        ElMessage.warning(tt('同一关联的聚合指标不可混配主表字段与明细表字段（明细JOIN会放大主表SUM），请拆成两个关联'))
        return false
      }
      // 两跳追溯半配拦截：选了跳板表但两个关联字段没选全，按半配不生效处理并提示
      if (j.viaKey && (!j.viaTarget || !j.viaMain)) {
        ElMessage.warning('两跳追溯配置不完整：跳板单号字段与跳板来源单号字段都必须选择，或清空tt("经中间表")')
        return false
      }
    }
  }
  return true
}

async function handleSave() {
  if (!validate()) return
  saving.value = true
  try {
    const payload = {
      reportName: form.reportName.trim(),
      dataSource: form.dataSource,
      configJson: JSON.stringify(buildConfig()),
      status: form.status,
      shareScope: form.shareScope,
      shareRoles: form.shareScope === '共享' ? shareRoleCodes.value.join(',') : null,
      remark: form.remark || null
    }
    if (isEdit.value) {
      await updateCustomReport(reportId.value, payload)
      ElMessage.success(tt('保存成功'))
    } else {
      const created = await createCustomReport(payload)
      ElMessage.success(tt('创建成功'))
      // 新建成功后切换到编辑态，便于继续试运行
      router.replace({ path: '/report/custom/designer', query: { id: created.reportId } })
      reportCode.value = created.reportCode || ''
      return
    }
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

// ========== 试运行 ==========
const previewVisible = ref(false)
const previewing = ref(false)
const previewData = ref([])
const previewColumns = ref([])

async function handlePreview() {
  if (!isEdit.value) return
  previewing.value = true
  previewVisible.value = true
  try {
    const res = await runCustomReport(reportId.value, { page: 1, size: 20 })
    previewData.value = res?.records || []
    previewColumns.value = res?.columns || []
    // 汇总模式追加整体合计行
    if (res?.mode === 'summary' && res?.totals) {
      previewData.value = [...previewData.value, res.totals]
    }
  } catch (e) {
    previewData.value = []
    previewColumns.value = []
    if (e?.message) ElMessage.error(e.message)
  } finally {
    previewing.value = false
  }
}

// ========== 工具 ==========
function typeLabel(t) {
  const map = { string: '文本', date: '日期', datetime: '时间', amount: '金额', number: '数量', int: '整数', bool: '是/否' }
  return map[t] || t
}

function formatNumber(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  if (isNaN(n)) return v
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function goBack() {
  router.push('/report/custom')
}

onMounted(async () => {
  await loadDataSources()
  await loadRoles()
  await loadForEdit()
})
</script>

<style scoped lang="scss">
.custom-report-designer {
  background: #f5f7fa;
  min-height: calc(100vh - 90px);
  display: flex;
  flex-direction: column;
}
.designer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 10px 16px;
  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
    .title-text { font-size: 16px; font-weight: 600; color: #303133; }
  }
  .header-right { display: flex; gap: 8px; }
}
.designer-body {
  flex: 1;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: auto;
}
.tabs-section {
  .designer-tabs {
    :deep(.el-tabs__header) {
      margin: 0;
      padding: 0 14px;
      background: #fafafa;
      border-bottom: 1px solid #ebeef5;
    }
    :deep(.el-tabs__content) { padding: 0; }
    :deep(.el-tabs__nav-wrap::after) { display: none; }
    .tab-label {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      .tab-badge {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 18px;
        height: 18px;
        padding: 0 5px;
        border-radius: 9px;
        background: #409eff;
        color: #fff;
        font-size: 11px;
      }
    }
  }
  .tab-desc {
    font-size: 12px;
    color: #909399;
    margin-bottom: 10px;
    line-height: 1.6;
  }
}
.section {
  background: #fff;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  .section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    border-bottom: 1px solid #f0f0f0;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    .step-no {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #409eff;
      color: #fff;
      font-size: 12px;
    }
    .title-tip { font-size: 12px; font-weight: 400; color: #909399; }
  }
  .section-content { padding: 14px; }
  .empty-ds { display: flex; justify-content: center; }
}
.field-pickers {
  display: flex;
  gap: 14px;
  height: 420px;
}
.field-source-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
  .field-search { margin: 8px; width: calc(100% - 16px); }
  .field-source-scroll { flex: 1; }
}
.field-group {
  border-bottom: 1px solid #f5f5f5;
  .field-group-title {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 10px;
    cursor: pointer;
    font-size: 13px;
    font-weight: 600;
    color: #606266;
    background: #fafafa;
    &:hover { background: #f0f7ff; }
    .arrow { transition: transform 0.2s; font-size: 12px; color: #909399; }
    .arrow.expanded { transform: rotate(90deg); }
    .group-name { flex: 1; }
  }
  .field-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 5px 10px 5px 26px;
    cursor: pointer;
    font-size: 13px;
    &:hover { background: #ecf5ff; }
    &.checked { background: #f0f9ff; }
    .field-label { flex: 1; color: #303133; }
    .field-type {
      font-size: 11px;
      padding: 0 6px;
      border-radius: 8px;
      color: #909399;
      background: #f4f4f5;
      &.amount, &.number, &.int { color: #409eff; background: #ecf5ff; }
      &.date, &.datetime { color: #e6a23c; background: #fdf6ec; }
    }
  }
}
.selected-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
  .selected-panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 12px;
    font-size: 13px;
    font-weight: 600;
    color: #606266;
    background: #fafafa;
    border-bottom: 1px solid #f0f0f0;
    .selected-tip { font-size: 12px; font-weight: 400; color: #909399; }
  }
  .selected-scroll { flex: 1; }
  .selected-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 5px 10px;
    font-size: 13px;
    border-bottom: 1px dashed #f5f5f5;
    &:hover { background: #fafcff; }
    .col-index {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #409eff;
      color: #fff;
      font-size: 11px;
      flex-shrink: 0;
    }
    .col-label { color: #303133; }
    .col-source { flex: 1; font-size: 12px; color: #909399; }
    .col-actions { display: flex; gap: 2px; }
  }
}
.agg-panel {
  border-top: 1px dashed #ebeef5;
  .agg-panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
    .agg-title { font-size: 13px; font-weight: 600; color: #606266; }
  }
  .agg-item {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
    .agg-preview { font-size: 12px; color: #909399; flex: 1; }
  }
  .agg-tip { font-size: 12px; color: #909399; margin-top: 4px; }
}
/* 聚合指标口径条件（2026-11-12）：每个指标独立的条件行，缩进在指标下方 */
.agg-conds {
  width: 100%;
  margin-top: 8px;
  padding: 8px 10px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.agg-cond-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.agg-cond-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.join-item {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  margin-bottom: 8px;
  .join-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .join-empty { font-size: 12px; color: #e6a23c; }
  .join-eq { font-weight: 600; color: #409eff; }
  .agg-fields {
    padding: 8px 10px;
    background: #f5f7fa;
    border-radius: 4px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    .agg-field-row {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .agg-mix-warning { font-size: 12px; color: #e6a23c; }
    .via-label { font-size: 12px; color: #909399; flex-shrink: 0; }
  }
}
.join-example {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.order-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  .order-item { display: flex; align-items: center; gap: 8px; }
  .order-tip { font-size: 12px; color: #909399; }
  /* 默认筛选条件行（含「且/或」连接符） */
  .df-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
  .df-logic-ph { width: 82px; font-size: 12px; color: #c0c4cc; }
  .df-eq { font-size: 12px; color: #909399; }
}
.cell-amount { color: #f56c6c; font-weight: 500; }
.cell-number { color: #409eff; font-weight: 500; }
</style>
