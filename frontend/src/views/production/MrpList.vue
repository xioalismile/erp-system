<template>
  <div class="mrp-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h3>{{ $tt("MRP 物料需求计划运算") }}</h3>
        <div class="header-sub">净需求 = 毛需求 - 现有库存 - 在途量 + 安全库存</div>
      </div>
      <div class="header-right">
        <!-- 2026-09-20 入口改造：本页已降级为「新增」页，提供返回 MRP计划列表 的出口 -->
        <router-link to="/production/mrp" style="margin-right:8px;">
          <el-button v-permission="perm('dlgCancel')">返回列表</el-button>
        </router-link>
        <el-button type="primary" v-permission="perm('view')" @click="showHistoryDialog">
          <el-icon><Clock /></el-icon> 运算历史
        </el-button>
      </div>
    </div>

    <!-- 【2026-10-09 用户口径】不再沿用金蝶式分步向导：去掉步骤条，需求来源 / 运算配置 / 运算结果 同页上下排列 -->

    <!-- 需求来源 + 运算设置（【2026-10-09】运算设置按用户口径放在「表头」；
         外层不再用 el-card，避免与通用明细表自带的卡片「卡片套卡片」＝用户看到的两个 div） -->
    <div class="step-card mrp-source-panel">
      <div class="card-header-row">
        <span class="card-title">{{ $tt("MRP 物料需求计划运算") }}</span>
        <div class="header-actions">
          <el-input v-model="calcConfig.planName" size="small" style="width: 240px" :placeholder="$tt('方案名称，如：2026年7月第1周MRP')" />
          <el-button type="primary" size="small" v-permission="perm('start')" @click="executeCalc" :loading="calculating">
            <el-icon><Cpu /></el-icon> 执行MRP运算
          </el-button>
        </div>
      </div>

      <!-- 运算设置（表头一行）：下单批量 / 抵扣口径 / 暂不支持占位 -->
      <el-form :model="calcConfig" class="config-form header-form" label-width="auto">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item :label="$tf('下单数量怎么定')">
              <el-select v-model="calcConfig.lotSizeType" size="small" style="width:100%">
                <el-option :label="$tf('要多少下多少')" value="逐批法" />
                <el-option :label="$tf('每次固定下这么多')" value="固定批量" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('每次固定下多少')">
              <el-input-number v-model="calcConfig.fixedLotSize" size="small" :min="0" :precision="scaleFor('qty')" style="width:100%"
                :disabled="calcConfig.lotSizeType !== '固定批量'" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('最少要下多少')">
              <el-input-number v-model="calcConfig.minOrderQty" size="small" :min="0" :precision="scaleFor('qty')" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('用现有库存抵扣')">
              <el-switch v-model="calcConfig.includeStock" size="small" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('用采购在途抵扣')">
              <el-switch v-model="calcConfig.includeOpenPo" size="small" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('用生产中数量抵扣')">
              <el-switch v-model="calcConfig.includeOpenMo" size="small" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('再加上安全库存')">
              <el-switch v-model="calcConfig.includeSafetyStock" size="small" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('BOM下级用料也算上')">
              <el-switch v-model="calcConfig.expandAllLevel" size="small" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('往后看多少天')">
              <el-input-number v-model="calcConfig.planHorizon" size="small" :min="1" :max="365" style="width:100%" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('多少天内不许插单')">
              <el-input-number v-model="calcConfig.timeFence" size="small" :min="0" style="width:100%" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('缺料用替代料顶')">
              <el-switch v-model="calcConfig.includeAltBom" size="small" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('把已开的采购申请算上')">
              <el-switch v-model="calcConfig.includePr" size="small" disabled />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 【2026-10-09】来源切换放在「明细列表上方」的工具条里（与全站"明细表上方工具条"的现有风格一致） -->
      <div class="toolbar-row">
        <span class="toolbar-label">{{ $tt("需求来源") }}</span>
        <el-radio-group v-model="demandSourceType" size="small">
          <el-radio-button label="saleOrder">{{ $tt("销售订单") }}</el-radio-button>
          <el-radio-button label="manual">{{ $tt("手工录入") }}</el-radio-button>
        </el-radio-group>
        <el-tag v-if="demandSourceType === 'manual'" type="info" size="small">共 {{ manualDemands.length }} 条</el-tag>
        <el-tag v-else type="info" size="small">已选 {{ selectedMaterialItems.length }} 行物料</el-tag>
      </div>

      <!-- 销售订单来源：明细同样用全站通用明细表（含物料自定义字段列），不再是手写表格 -->
      <BillItemsTable
        v-if="demandSourceType === 'saleOrder'"
        v-model:collapsed="saleSourceOpen"
        :items="selectedMaterialItems"
        page-key="MrpCalc_saleSource"
        title="来源物料明细"
        :columns="saleSourceColumns"
        @row-change="onDemandRowChange"
      >
        <template #actions>
          <SourcePicker
            target-type="MrpPlan"
            items-field="items"
            :source-status="['已生效']"
            btn-type="primary"
            size="small"
            @pick="onSaleOrderSourcePicked"
          />
          <el-button size="small" v-permission="perm('reset')" :disabled="selectedMaterialItems.length === 0" @click="clearMaterialItems">清空</el-button>
        </template>
        <!-- 标准物料列（与手工录入、全站单据明细同一套） -->
        <template #cell-materialId="{ row }">
          <el-select v-model="row.materialId" filterable clearable size="small" class="table-select"
            :teleported="true" :popper-options="{ strategy: 'fixed' }" popper-class="material-select-popper"
            :placeholder="manualMaterialPlaceholder"
            :filter-method="v => onManualMaterialFilter(v, row)"
            @change="() => onManualMaterialPick(row)">
            <el-option v-for="m in (row._filteredMaterials || materialOptions)" :key="m.materialId"
              :label="`${m.materialCode} ${m.materialName || ''}`" :value="m.materialId">
              <span class="opt-code">{{ m.materialCode }}</span>
              <span class="opt-name">{{ m.materialName }}</span>
              <span v-for="cf in dropCfText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
            </el-option>
          </el-select>
        </template>
        <!-- BOM清单：同一物料可有多份 BOM，下拉切换；切换后立即按该 BOM 展开物料明细 -->
        <template #cell-bomCode="{ row }">
          <el-select
            v-model="row.bomId"
            size="small"
            filterable
            clearable
            class="table-select"
            :placeholder="bomsOfMaterial(row.materialId).length ? '选择BOM' : '该物料无BOM'"
            @change="(v) => onRowBomChange(row, v)"
          >
            <el-option
              v-for="b in bomsOfMaterial(row.materialId)"
              :key="b.bomId"
              :label="`${b.bomCode} / ${b.bomVersion || ''}`"
              :value="b.bomId"
            />
          </el-select>
        </template>
        <template #cell-hasBom="{ row }">
          <el-tag :type="row.hasBom ? 'success' : 'danger'" size="small">{{ row.hasBom ? '已有' : '无' }}</el-tag>
        </template>
        <template #operation="{ index }">
          <el-button size="small" link type="danger" @click="removeMaterialItem(index)">{{ $tt("移除") }}</el-button>
        </template>
      </BillItemsTable>

      <!-- 销售预测来源：按用户口径整块去掉（2026-10-09「销售预测去掉没啥用」） -->


      <!-- 手工录入（通用明细表本身已是一张卡片，这里不再套外层 div，避免"两个手工需求明细"） -->
      <template v-if="demandSourceType === 'manual'">
        <!-- 【2026-10-09】手工录入明细改用全站通用明细表（列设置/动态列/自定义字段列/汇总全站共用一套） -->
        <BillItemsTable
          v-model:collapsed="manualOpen"
          :items="manualDemands"
          page-key="MrpCalc_manual"
          title="手工需求明细"
          :columns="manualColumns"
          @row-change="onDemandRowChange"
        >
          <template #actions>
            <el-button type="success" size="small" v-permission="perm('addRow')" @click="addManualDemand">{{ $tt("+ 添加明细") }}</el-button>
            <el-button type="primary" plain size="small" v-permission="perm('query')" @click="materialPickerVisible = true">
              <el-icon><Grid /></el-icon> 从物料库选择
            </el-button>
            <el-button size="small" v-permission="perm('clear')" @click="clearManualDemands">{{ $tt("清空") }}</el-button>
          </template>
          <!-- 标准物料列：与全站单据明细同一套（绑物料ID + 自定义字段搜索；下拉里直接显示自定义字段） -->
          <template #cell-materialId="{ row }">
            <el-select v-model="row.materialId" filterable clearable size="small" class="table-select"
              :teleported="true" :popper-options="{ strategy: 'fixed' }" popper-class="material-select-popper"
              :placeholder="manualMaterialPlaceholder"
              :filter-method="v => onManualMaterialFilter(v, row)"
              @change="() => onManualMaterialPick(row)">
              <el-option v-for="m in (row._filteredMaterials || materialOptions)" :key="m.materialId"
                :label="`${m.materialCode} ${m.materialName || ''}`" :value="m.materialId">
                <span class="opt-code">{{ m.materialCode }}</span>
                <span class="opt-name">{{ m.materialName }}</span>
                <span v-for="cf in dropCfText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
              </el-option>
            </el-select>
          </template>
          <!-- BOM清单：同一物料可有多份 BOM，下拉切换；切换后立即按该 BOM 展开物料明细 -->
          <template #cell-bomCode="{ row }">
            <el-select
              v-model="row.bomId"
              size="small"
              filterable
              clearable
              class="table-select"
              :placeholder="bomsOfMaterial(row.materialId).length ? '选择BOM' : '该物料无BOM'"
              @change="(v) => onRowBomChange(row, v)"
            >
              <el-option
                v-for="b in bomsOfMaterial(row.materialId)"
                :key="b.bomId"
                :label="`${b.bomCode} / ${b.bomVersion || ''}`"
                :value="b.bomId"
              />
            </el-select>
          </template>
          <template #cell-priority="{ row }">
            <el-select v-model="row.priority" size="small" style="width:100%">
              <el-option :label="$tf('高')" value="高" />
              <el-option :label="$tf('中')" value="中" />
              <el-option :label="$tf('低')" value="低" />
            </el-select>
          </template>
        </BillItemsTable>
        <!-- 【2026-10-09】「从物料库选择」共用弹窗（自带物料自定义字段列）：选中的物料各加一行手工需求 -->
        <MaterialPickerDialog v-model="materialPickerVisible" :title="$tt('从物料库选择（可多选）')" @confirm="onManualMaterialsPicked" />
      </template>

    </div>

    <!-- 物料需求展开（2026-11-03 用户口径）：有 BOM 就立即展开、多级缩进；计算只把缺料值填进来；
         下推按「原材料→采购申请」「半成品→生产工单」分成两个页签，支持分批多次下推 -->
    <el-card shadow="never" class="step-card expand-card">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">{{ $tt("物料需求展开") }}</span>
          <div class="header-actions">
            <el-tag v-if="expandLoading" type="info" size="small">{{ $tt("展开中...") }}</el-tag>
            <el-tag v-else type="info" size="small">共 {{ expandedRows.length }} 行</el-tag>
            <el-tag v-if="calcResult.materialCount" type="success" size="small">{{ $tt("已计算") }}</el-tag>
          </div>
        </div>
      </template>

      <el-tabs v-model="expandTab">
        <el-tab-pane :label="$tf('需求展开')" name="all">
          <div class="expand-tip">{{ $tt("选完物料即有展开结果：母件为第 0 层，子件按层级缩进；点上方「执行MRP运算」只把缺多少填进本表。") }}</div>
        </el-tab-pane>
        <el-tab-pane :label="$tf('原材料 · 下推采购申请')" name="raw">
          <div class="expand-tab-bar">
            <el-button type="warning" size="small" :disabled="!canPushRaw" @click="pushExpandSelected('raw')">
              <el-icon><Download /></el-icon> {{ $tt("下推采购申请") }}（{{ selectedRawRows.length }}）
            </el-button>
            <span class="expand-tip">{{ $tt("只列原材料；已下推数量会记下，剩余还能再次下推。") }}</span>
          </div>
        </el-tab-pane>
        <el-tab-pane :label="$tf('半成品 · 下推生产工单')" name="make">
          <div class="expand-tab-bar">
            <el-button type="success" size="small" :disabled="!canPushMake" @click="pushExpandSelected('make')">
              <el-icon><Download /></el-icon> {{ $tt("下推生产工单") }}（{{ selectedMakeRows.length }}）
            </el-button>
            <span class="expand-tip">{{ $tt("只列半成品/成品；已下推数量会记下，剩余还能再次下推。") }}</span>
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-empty v-if="!expandLoading && expandedRows.length === 0" :image-size="70"
        :description="$tt('还没有展开物料：请先在上方明细里选择物料（选了有 BOM 的物料会自动展开多级明细）')" />
      <el-table v-else :data="expandTabRows" border size="small" height="360"
        @selection-change="onExpandSelectionChange">
        <el-table-column v-if="expandTab !== 'all'" type="selection" width="44" :selectable="canPushRow" />
        <el-table-column :label="$tf('物料')" min-width="240">
          <template #default="{ row }">
            <span :style="{ paddingLeft: ((row._level || 0) * 16) + 'px' }">
              <span v-if="row._level > 0" class="lv-mark">└</span>
              <span>{{ row.materialCode || '—' }}</span>
              <span class="mat-name">{{ row.materialName }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="specification" :label="$tf('规格')" width="110" />
        <el-table-column :label="$tf('属性')" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.materialType" size="small" :type="isRawType(row.materialType) ? 'warning' : 'success'">{{ $dt(row.materialType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="unit" :label="$tf('单位')" width="70" />
        <el-table-column :label="$tf('单件用量')" width="90" align="right">
          <template #default="{ row }">{{ row.usageQuantity == null ? '' : formatNum(row.usageQuantity) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('毛需求')" width="100" align="right">
          <template #default="{ row }">{{ row.grossRequirement == null ? '' : formatNum(row.grossRequirement) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('可用库存')" width="100" align="right">
          <template #default="{ row }">{{ row.availableStock == null ? '—' : formatNum(row.availableStock) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('净需求')" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.netDemand == null" class="dash">—</span>
            <span v-else class="net-demand">{{ formatNum(row.netDemand) }}</span>
          </template>
        </el-table-column>
        <!-- 2026-11-25：计划下单量 = 净需求按物料「最小订货量 + 采购批量」修正后的值（下推按它取数） -->
        <el-table-column :label="$tf('计划下单量')" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.planQuantity == null" class="dash">—</span>
            <span v-else>{{ formatNum(row.planQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bomCode" :label="$tf('BOM编号')" width="160" />
        <el-table-column prop="bomVersion" :label="$tf('BOM版本')" width="95" />
        <el-table-column v-if="expandTab !== 'all'" :label="$tf('已下推')" width="90" align="right">
          <template #default="{ row }">{{ formatNum(row.pushedQty || 0) }}</template>
        </el-table-column>
        <el-table-column v-if="expandTab !== 'all'" :label="$tf('可下推')" width="90" align="right">
          <template #default="{ row }">{{ formatNum(pushableQty(row)) }}</template>
        </el-table-column>
      </el-table>
    </el-card>


    <!-- 运算结果（执行后出现在同页下方） -->
    <el-card v-show="currentStep >= 2" shadow="never" class="step-card result-card">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">{{ $tt("运算结果") }}</span>
          <div class="header-actions">
            <el-tag type="success" size="small">运算方案：{{ calcConfig.planName || '临时运算' }}</el-tag>
            <el-tag type="info" size="small">运算时间：{{ calcResult.calcTime || '--' }}</el-tag>
            <el-button size="small" v-permission="perm('view')" @click="showDetailDialog(null)">{{ $tt("查看完整明细") }}</el-button>
          </div>
        </div>
      </template>

      <!-- 结果统计卡片 -->
      <el-row :gutter="16" class="stat-row">
        <el-col :span="6">
          <el-statistic :title="$tt('涉及物料数')" :value="calcResult.materialCount || 0">
            <template #suffix>{{ $tt("种") }}</template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic :title="$tt('毛需求总量')" :value="calcResult.grossTotal || 0" :precision="2" />
        </el-col>
        <el-col :span="6">
          <el-statistic :title="$tt('净需求总量')" :value="calcResult.netTotal || 0" :precision="2">
            <template #suffix>
              <el-tag v-if="calcResult.netTotal > 0" type="danger" size="small">{{ $tt("需采购/生产") }}</el-tag>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic :title="$tt('可满足物料')" :value="calcResult.satisfiedCount || 0">
            <template #suffix>
              <el-tag type="success" size="small">{{ $tt("库存充足") }}</el-tag>
            </template>
          </el-statistic>
        </el-col>
      </el-row>

      <!-- 2026-11-03 用户口径：结果明细统一到上方「物料需求展开」，按其属性分「原材料/半成品」页签下推；
           此处只保留统计与保存入口，不再重复一张结果表 -->
      <div class="toolbar-row">
        <el-tag type="info" size="small">{{ $tt("缺料与下推请在上方「物料需求展开」里按页签处理") }}</el-tag>
        <el-tag type="warning" size="small">{{ $tt("缺料物料") }} {{ shortageList.length }}</el-tag>
      </div>

      <div class="step-footer">
        <el-button v-permission="perm('reset')" @click="currentStep = 0">{{ $tt("重新运算") }}</el-button>
        <el-button type="primary" v-permission="perm('save')" @click="saveCurrentToHistory">
          <el-icon><FolderOpened /></el-icon> 保存到历史记录
        </el-button>
      </div>
    </el-card>

    <!-- 物料明细弹窗 -->
    <el-dialog v-model="detailDialog.visible" :title="$tf('物料需求分析明细')" width="900px" :close-on-click-modal="false">
      <template v-if="detailDialog.item">
        <el-descriptions :column="3" border class="detail-desc">
          <el-descriptions-item :label="$tf('物料编码')">{{ detailDialog.item.materialCode }}</el-descriptions-item>
          <el-descriptions-item :label="$tf('物料名称')">{{ detailDialog.item.materialName }}</el-descriptions-item>
          <el-descriptions-item :label="$tf('规格')">{{ detailDialog.item.specification || '--' }}</el-descriptions-item>
          <el-descriptions-item :label="$tf('物料属性')">
            <el-tag :type="materialTypeColor(detailDialog.item.materialType)" size="small">{{ detailDialog.item.materialType }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$tf('单位')">{{ detailDialog.item.unit }}</el-descriptions-item>
          <el-descriptions-item :label="$tf('提前期')">{{ detailDialog.item.leadTime }} 天</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">{{ $tt("需求计算过程") }}</el-divider>

        <div class="calc-process">
          <div class="process-row">
            <div class="process-item">
              <div class="process-label">{{ $tt("毛需求") }}</div>
              <div class="process-value">{{ formatNum(detailDialog.item.grossDemand) }}</div>
              <div class="process-note">{{ $tt("来自销售订单/预测") }}</div>
            </div>
            <div class="process-arrow">-</div>
            <div class="process-item">
              <div class="process-label">{{ $tt("可用库存") }}</div>
              <div class="process-value">{{ formatNum(detailDialog.item.availableStock) }}</div>
              <div class="process-note">{{ $tt("现有库存 - 已分配") }}</div>
            </div>
            <div class="process-arrow">-</div>
            <div class="process-item">
              <div class="process-label">{{ $tt("在途量") }}</div>
              <div class="process-value">{{ formatNum(detailDialog.item.inTransitQty || 0) }}</div>
              <div class="process-note">{{ $tt("在途采购/在制工单") }}</div>
            </div>
            <div class="process-arrow">+</div>
            <div class="process-item">
              <div class="process-label">{{ $tt("安全库存") }}</div>
              <div class="process-value">{{ formatNum(detailDialog.item.safetyStock || 0) }}</div>
              <div class="process-note">{{ $tt("物料主数据设置") }}</div>
            </div>
            <div class="process-arrow">=</div>
            <div class="process-item highlight">
              <div class="process-label">{{ $tt("净需求") }}</div>
              <div class="process-value net-demand">{{ formatNum(detailDialog.item.netDemand) }}</div>
              <div class="process-note">
                <el-tag :type="detailDialog.item.netDemand > 0 ? 'danger' : 'success'" size="small">
                  {{ detailDialog.item.netDemand > 0 ? '需要补货' : '库存满足' }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>

        <el-divider content-position="left">{{ $tt("建议操作") }}</el-divider>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-card shadow="never" class="suggest-card">
              <template #header>
                <span>
                  <el-tag type="warning" size="small">{{ $tt("采购建议") }}</el-tag>
                  {{ detailDialog.item.suggestType === 'PURCHASE' ? '推荐' : '可选' }}
                </span>
              </template>
              <el-form label-width="100px" size="small">
                <el-form-item :label="$tf('建议采购量')">
                  <el-input-number v-model="detailDialog.editQty" :min="0" :precision="scaleFor('qty')" />
                </el-form-item>
                <el-form-item :label="$tf('建议下单日')">
                  <el-date-picker v-model="detailDialog.editDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
                <el-form-item :label="$tf('预计交货日')">
                  <span>{{ calcExpectDeliveryDate() }}</span>
                </el-form-item>
                <el-button type="warning" size="small" v-permission="perm('pushTo')" @click="pushSingleToPurchase">{{ $tt("下推采购申请") }}</el-button>
              </el-form>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never" class="suggest-card" v-if="detailDialog.item.suggestType === 'PRODUCE' || detailDialog.item.materialType === '半成品'">
              <template #header>
                <span>
                  <el-tag type="success" size="small">{{ $tt("生产建议") }}</el-tag>
                  {{ detailDialog.item.suggestType === 'PRODUCE' ? '推荐' : '可选' }}
                </span>
              </template>
              <el-form label-width="100px" size="small">
                <el-form-item :label="$tf('建议生产量')">
                  <el-input-number v-model="detailDialog.editQty" :min="0" :precision="scaleFor('qty')" />
                </el-form-item>
                <el-form-item :label="$tf('计划开工日')">
                  <el-date-picker v-model="detailDialog.editStartDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
                <el-form-item :label="$tf('计划完工日')">
                  <span>{{ calcPlanEndDate() }}</span>
                </el-form-item>
                <el-button type="success" size="small" v-permission="perm('pushTo')" @click="pushSingleToProduction">{{ $tt("下推生产工单") }}</el-button>
              </el-form>
            </el-card>
          </el-col>
        </el-row>
      </template>

      <!-- 完整明细表格 -->
      <template v-else>
        <el-table :data="calcResult.allItems" border size="small" height="400">
          <el-table-column prop="materialCode" :label="$tf('物料编码')" width="120" />
          <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="160" />
          <!-- 行级 BOM（2026-11-06）：运算使用的 BOM 清单与版本 -->
          <el-table-column prop="bomCode" :label="$tf('BOM清单')" width="160" />
          <el-table-column prop="bomVersion" :label="$tf('BOM版本')" width="95" />
          <el-table-column prop="materialType" :label="$tf('属性')" width="90">
            <template #default="{ row }">
              <el-tag :type="materialTypeColor(row.materialType)" size="small">{{ $dt(row.materialType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="grossDemand" :label="$tf('毛需求')" width="100" align="right" />
          <el-table-column prop="availableStock" :label="$tf('可用库存')" width="100" align="right" />
          <el-table-column prop="safetyStock" :label="$tf('安全库存')" width="90" align="right" />
          <el-table-column prop="netDemand" :label="$tf('净需求')" width="100" align="right">
            <template #default="{ row }">
              <span :class="{ 'net-demand': row.netDemand > 0 }">{{ formatNum(row.netDemand) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="planQuantity" :label="$tf('计划下单量')" width="110" align="right">
            <template #default="{ row }">{{ formatNum(row.planQuantity || 0) }}</template>
          </el-table-column>
          <el-table-column prop="suggestType" :label="$tf('建议类型')" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.netDemand > 0" :type="row.suggestType === 'PURCHASE' ? 'warning' : 'success'" size="small">
                {{ row.suggestType === 'PURCHASE' ? '采购' : '生产' }}
              </el-tag>
              <span v-else>--</span>
            </template>
          </el-table-column>
          <el-table-column prop="suggestDate" :label="$tf('建议下单日')" width="110" />
          <el-table-column prop="leadTime" :label="$tf('提前期')" width="80" align="center" />
        </el-table>
      </template>
    </el-dialog>

    <!-- 运算历史弹窗 -->
    <el-dialog v-model="historyDialog.visible" :title="$tf('MRP运算历史记录')" width="1000px" :close-on-click-modal="false">
      <div class="toolbar-row">
        <el-date-picker v-model="historyDialog.dateRange" type="daterange" :range-separator="$tt('至')" :start-placeholder="$tt('开始日期')" :end-placeholder="$tt('结束日期')" value-format="YYYY-MM-DD" style="width:260px" />
        <el-select v-model="historyDialog.status" :placeholder="$tt('状态')" clearable style="width:120px">
          <el-option :label="$tf('已保存')" value="已保存" />
          <el-option :label="$tf('已下推')" value="已下推" />
        </el-select>
        <el-button type="primary" size="small" v-permission="perm('query')" @click="loadHistory">{{ $tt("查询") }}</el-button>
      </div>

      <el-table :data="historyDialog.list" border size="small" height="380" v-loading="historyDialog.loading">
        <el-table-column prop="planNo" :label="$tf('方案编号')" width="140" />
        <el-table-column prop="planName" :label="$tf('方案名称')" min-width="180" />
        <el-table-column prop="calcTime" :label="$tf('运算时间')" width="160" />
        <el-table-column prop="demandSource" :label="$tf('需求来源')" width="100" />
        <el-table-column prop="materialCount" :label="$tf('涉及物料')" width="90" align="center" />
        <el-table-column prop="netTotal" :label="$tf('净需求总量')" width="120" align="right">
          <template #default="{ row }">{{ formatNum(row.netTotal) }}</template>
        </el-table-column>
        <el-table-column prop="purchaseCount" :label="$tf('采购建议')" width="90" align="center" />
        <el-table-column prop="productionCount" :label="$tf('生产建议')" width="90" align="center" />
        <el-table-column prop="status" :label="$tf('状态')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '已下推' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="historyDialog.page.current"
        :page-size="historyDialog.page.size"
        :total="historyDialog.page.total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10,20,50]"
        style="margin-top:12px;justify-content:flex-end;display:flex;"
        @current-change="loadHistory"
        @size-change="loadHistory"
      />
    </el-dialog>

    <!-- 批量修改数量弹窗 -->
    <el-dialog v-model="batchEditDialog.visible" :title="$tf('批量修改下推数量')" width="600px">
      <el-form label-width="120px">
        <el-form-item :label="$tf('调整方式')">
          <el-radio-group v-model="batchEditDialog.mode">
            <el-radio label="fixed">{{ $tt("固定数量") }}</el-radio>
            <el-radio label="percent">{{ $tt("按比例") }}</el-radio>
            <el-radio label="add">{{ $tt("增减数量") }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$tf('调整值')">
          <el-input-number v-if="batchEditDialog.mode === 'fixed'" v-model="batchEditDialog.fixedValue" :min="0" :precision="scaleFor('qty')" style="width:200px" />
          <el-input-number v-if="batchEditDialog.mode === 'percent'" v-model="batchEditDialog.percentValue" :min="0" :max="200" style="width:200px" />
          <el-input-number v-if="batchEditDialog.mode === 'add'" v-model="batchEditDialog.addValue" :precision="scaleFor('qty')" style="width:200px" />
        </el-form-item>
      </el-form>
      <div class="preview-section">
        <div class="preview-title">调整预览（共 {{ selectedShortages.length }} 条）</div>
        <el-table :data="batchEditPreview" border size="small" max-height="200">
          <el-table-column prop="materialCode" :label="$tf('物料编码')" width="120" />
          <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="120" />
          <el-table-column prop="oldQty" :label="$tf('原净需求')" width="100" align="right" />
          <el-table-column prop="newQty" :label="$tf('调整后数量')" width="100" align="right">
            <template #default="{ row }">
              <span class="new-qty">{{ formatNum(row.newQty) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="batchEditDialog.visible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('apply')" @click="applyBatchEdit">{{ $tt("应用调整") }}</el-button>
      </template>
    </el-dialog>

    <!-- 下推进度弹窗 -->
    <el-dialog v-model="pushProgress.visible" :title="$tf('下推进度')" width="500px" :close-on-click-modal="false">
      <el-progress :percentage="pushProgress.percent" :status="pushProgress.status" />
      <div class="progress-log">
        <div v-for="(log, idx) in pushProgress.logs" :key="idx" :class="['log-item', log.type]">
          <el-icon v-if="log.type === 'success'"><CircleCheck /></el-icon>
          <el-icon v-if="log.type === 'error'"><CircleClose /></el-icon>
          <span>{{ log.message }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { useRoute } from 'vue-router'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Clock, Cpu, Warning, Download, Edit, View,
  ShoppingCart, Box, CircleCheck, CircleClose, FolderOpened, Plus
} from '@element-plus/icons-vue'
import AuditColumns from '@/components/AuditColumns.vue'
// 【2026-10-09】手工录入的「添加明细」= 打开共用「从物料库选择」弹窗（自带自定义字段列，与全站一致）
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
// 【2026-10-09】通用明细表（全站单据明细共用）：手工录入明细改用它，列设置/动态列/自定义字段列全站一套
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
// 【2026-10-09】通用「选来源单」组件：MRP 运算的来源销售订单从这里选（与销售订单页同一套）
import SourcePicker from '@/components/SourcePicker.vue'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { calculateMrp, calculateMrpBatch, expandBom } from '@/api/production/mrp'
import { pageWorkOrder } from '@/api/production/workOrder'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllBom } from '@/api/basedata/bom'
import { savePurchaseRequest } from '@/api/purchase/request'
import { createWorkOrder } from '@/api/production/workOrder'
import { listMrpPlan, saveMrpPlan, getMrpPlan, deleteMrpPlan } from '@/api/production/mrpPlan'

import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { usePrecision } from '@/composables/usePrecision'
const perm = usePagePerm('production:mrp')
const route = useRoute()

// 数量输入/显示的位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor, formatWith, trimZero } = usePrecision()

// 步骤控制
const currentStep = ref(0)

// 需求来源
const demandSourceType = ref('saleOrder')
const materialOptions = ref([])
const materialList = ref([])
/** 全部 BOM（2026-11-02）：选物料后要能直接看到该物料的 BOM 编号/版本 */
const bomList = ref([])

/** 已回填的物料清单（本次要分析的需求来源，可分批累加） */
const selectedMaterialItems = ref([])

// 销售预测
const forecastList = ref([])
const selectedForecasts = ref([])

// 手工录入
const manualDemands = ref([])
/** 【2026-10-09】「从物料库选择」弹窗（手工录入的「添加明细」入口，与全站共用同一个组件） */
const materialPickerVisible = ref(false)
/** 手工录入明细卡片展开态（通用明细表 BillItemsTable 需要 v-model:collapsed） */
const manualOpen = ref(true)
/** 每个物料当前使用的 BOM：默认BOM（已生效）→ 默认 → 主BOM（已生效）→ 主 → 最新已生效 → 最新 */
const activeBomByMaterial = computed(() => {
  const map = {}
  ;(bomList.value || []).forEach(b => {
    if (!b || b.materialId == null) return
    const cur = map[b.materialId]
    if (!cur || bomRank(b) < bomRank(cur) || (bomRank(b) === bomRank(cur) && Number(b.bomId || 0) > Number(cur.bomId || 0))) {
      map[b.materialId] = b
    }
  })
  return map
})
function bomRank (b) {
  const effective = b.status === '已生效'
  const isDefault = b.isDefault === 1
  const isPrimary = b.isPrimary === 1
  if (isDefault && effective) return 0
  if (isDefault) return 1
  if (isPrimary && effective) return 2
  if (isPrimary) return 3
  if (effective) return 4
  return 5
}
/** 选中物料后把 BOM 编号/版本落到行上（列表里直接可见；没有 BOM 时留空） */
function fillBomInfo (row, materialId) {
  const b = activeBomByMaterial.value[materialId]
  if (b) {
    row.bomId = b.bomId
    row.bomCode = b.bomCode || ''
    row.bomVersion = b.bomVersion || ''
    row.bomName = b.bomName || ''
  } else {
    row.bomId = null
    row.bomCode = ''
    row.bomVersion = ''
    row.bomName = ''
  }
}

/** 手工录入明细列定义（通用明细表消费）：物料列由 #cell-materialId 槽提供标准物料下拉；
 *  物料自定义字段追加为「只读列」（与全站物料明细一样能看到该物料的字段值）。 */
const manualColumns = computed(() => {
  const base = [
    { prop: 'materialMainImage', label: '图片', width: 60, align: 'center', type: 'image' },
    { prop: 'materialId', label: '物料', minWidth: 220 },
    { prop: 'materialCode', label: '物料编码', width: 130 },
    { prop: 'materialName', label: '物料名称', minWidth: 140 },
    { prop: 'specification', label: '规格', width: 110 },
    { prop: 'bomCode', label: 'BOM清单', width: 200 },
    { prop: 'bomVersion', label: 'BOM版本', width: 100 },
    { prop: 'quantity', label: '需求数量', width: 130, editor: 'number', precision: 4 },
    { prop: 'demandDate', label: '需求日期', width: 150, editor: 'date' },
    { prop: 'priority', label: '优先级', width: 100 }
  ]
  const cfCols = (mcf.allFields.value || []).map(f => ({
    prop: 'cf_' + f.fieldKey,
    label: f.fieldLabel || f.fieldKey,
    width: 130,
    type: 'text'
  }))
  return base.concat(cfCols)
})
/** 【2026-10-09】销售订单来源明细：同样走通用明细表 —— 列 = 来源单号/客户 + 标准物料列 + 需求日期 + 全部自定义字段列 */
const saleSourceOpen = ref(true)
const saleSourceColumns = computed(() => {
  const base = [
    { prop: 'orderNo', label: '来源订单号', width: 150 },
    { prop: 'customerName', label: '客户', minWidth: 140 },
    { prop: 'materialMainImage', label: '图片', width: 60, align: 'center', type: 'image' },
    { prop: 'materialId', label: '物料', minWidth: 220 },
    { prop: 'materialCode', label: '物料编码', width: 130 },
    { prop: 'materialName', label: '物料名称', minWidth: 140 },
    { prop: 'specification', label: '规格', width: 110 },
    { prop: 'bomCode', label: 'BOM清单', width: 200 },
    { prop: 'bomVersion', label: 'BOM版本', width: 100 },
    { prop: 'quantity', label: '数量', width: 110, editor: 'number', precision: 4 },
    { prop: 'demandDate', label: '需求日期', width: 150, editor: 'date' },
    { prop: 'hasBom', label: 'BOM', width: 90, align: 'center' }
  ]
  const cfCols = (mcf.allFields.value || []).map(f => ({
    prop: 'cf_' + f.fieldKey,
    label: f.fieldLabel || f.fieldKey,
    width: 130,
    type: 'text'
  }))
  return base.concat(cfCols)
})
/** 取某物料在某自定义字段上的值（值表按物料ID为主、退化为物料编码，兼容两种键） */
function cfValueOf (m, key) {
  const vm = mcf.valueMap.value || {}
  const rec = vm[m.materialId] || vm[m.materialCode] || {}
  return rec[key]
}
/** 选中物料后把「物料自定义字段」值一并落到行上（供明细里的自定义字段列显示） */
function fillMaterialCustomFields (row, m) {
  ;(mcf.allFields.value || []).forEach(f => {
    row['cf_' + f.fieldKey] = cfValueOf(m, f.fieldKey)
  })
}
/** 【2026-10-09】物料下拉里展示的自定义字段：用【全部】物料自定义字段（不只看「下拉显示」开关），
 *  否则开关没打开的字段在下拉里一个都看不到（用户反馈「下拉选择这里只有规格，我的自定义字段呢」）。 */
function dropCfText (m) {
  return (mcf.allFields.value || [])
    .map(f => ({ key: f.fieldKey, label: f.fieldLabel || f.fieldKey, value: cfValueOf(m, f.fieldKey) }))
    .filter(x => x.value !== null && x.value !== undefined && x.value !== '')
}
/** 物料自定义字段：下拉按自定义字段搜索、占位提示都从这里取（与全站同源，不再各页一套） */
const mcf = useMaterialCustomFields()
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))))
const manualMaterialPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

/** 手工录入：物料下拉输入即过滤（编码/名称/规格 + 自定义字段值），值表取全量预取，与销售订单同一套 */
function onManualMaterialFilter (val, row) {
  if (!val) { row._filteredMaterials = null; return }
  row._filteredMaterials = filterMaterials(materialOptions.value, val, mcf.valueMap.value, materialSearchableFields.value)
}
/** 选中后把编码/名称/规格同步到行上（提交给运算的仍是「物料编码」，后端与请求体零改动） */
function onManualMaterialPick (row) {
  const m = (materialOptions.value || []).find(x => x.materialId === row.materialId)
  if (!m) { row.materialCode = ''; row.materialName = ''; row.specification = ''; fillBomInfo(row, null); return }
  row.materialCode = m.materialCode
  row.materialName = m.materialName || ''
  row.specification = m.model || m.specification || ''
  // 物料自定义字段值一并落到行上 → 明细里的自定义字段列有值
  fillMaterialCustomFields(row, m)
  // BOM 编号/版本一并落到行上（用户 2026-11-02：选了母件却看不到 BOM 信息）
  fillBomInfo(row, m.materialId)
  // 2026-11-03：有 BOM 立即展开物料明细（不等点计算）
  rebuildExpanded()
}
/** 「添加明细」= 从物料库选择（可多选）：选中的物料各加一行手工需求
 *  注意：通用弹窗 emit('confirm', { rows, valueMap })，回传的是**对象**，必须解构 rows（曾按数组处理 → 确认后加不进去） */
function onManualMaterialsPicked ({ rows } = {}) {
  ;(rows || []).forEach(m => {
    const row = {
      materialId: m.materialId,
      materialCode: m.materialCode,
      materialName: m.materialName || '',
      specification: m.model || m.specification || '',
      quantity: 1,
      demandDate: new Date().toISOString().slice(0, 10),
      priority: '中'
    }
    const full = (materialOptions.value || []).find(x => x.materialId === m.materialId)
    if (full) fillMaterialCustomFields(row, full)
    fillBomInfo(row, m.materialId)
    manualDemands.value.push(row)
  })
  // 2026-11-03：选完物料立即展开
  rebuildExpanded()
}

// 运算配置
const calcConfig = reactive({
  planName: '',
  planHorizon: 90,
  timeFence: 7,
  lotSizeType: '逐批法',
  fixedLotSize: 0,
  minOrderQty: 0,
  includeStock: true,
  includeOpenPo: true,
  includeOpenMo: true,
  includeSafetyStock: true,
  includeAltBom: false,
  includePr: false,
  expandAllLevel: true
})

// 运算结果
const calculating = ref(false)
const calcResult = reactive({
  calcTime: '',
  materialCount: 0,
  grossTotal: 0,
  netTotal: 0,
  satisfiedCount: 0,
  allItems: [],
  shortages: [],
  purchases: [],
  productions: []
})

const resultTab = ref('shortage')

// 缺料勾选
const selectedShortages = ref([])
const selectAllShortage = ref(false)
const shortageFilter = reactive({ type: '', keyword: '' })

// 计算列表
const shortageList = computed(() => calcResult.shortages.filter(s => s.netDemand > 0))
const purchaseList = computed(() => calcResult.purchases)
const productionList = computed(() => calcResult.productions)
const satisfiedList = computed(() => calcResult.allItems.filter(s => s.netDemand <= 0))

const filteredShortageList = computed(() => {
  let list = shortageList.value
  if (shortageFilter.type) {
    list = list.filter(s => s.materialType === shortageFilter.type)
  }
  if (shortageFilter.keyword) {
    const kw = shortageFilter.keyword.toLowerCase()
    list = list.filter(s =>
      s.materialCode.toLowerCase().includes(kw) ||
      s.materialName.toLowerCase().includes(kw)
    )
  }
  return list
})

// ===================== 【2026-10-09】结果区一页式（去掉 4 个分页签） =====================
/** 结果类型筛选：all / purchase（需采购）/ production（需生产）/ satisfied（库存满足） */
const resultKind = ref('all')
/**
 * 合并后的结果行 = 缺料行（净需求>0，按建议类型分采购/生产） + 库存满足行。
 * 缺料行按物料编码挂上「采购建议 / 生产建议」里的建议数量与日期，保证一张表能看到原分页签的信息。
 */
const mergedResultRows = computed(() => {
  const purchaseByCode = {}
  ;(purchaseList.value || []).forEach(r => { if (r && r.materialCode) purchaseByCode[r.materialCode] = r })
  const produceByCode = {}
  ;(productionList.value || []).forEach(r => { if (r && r.materialCode) produceByCode[r.materialCode] = r })
  const shortageRows = shortageList.value.map(s => {
    const p = purchaseByCode[s.materialCode]
    const m = produceByCode[s.materialCode]
    return Object.assign({}, s, {
      _kind: s.suggestType === 'PURCHASE' ? 'purchase' : 'production',
      suggestQty: (p && p.suggestQty != null) ? p.suggestQty
        : ((m && m.suggestQty != null) ? m.suggestQty : s.netDemand),
      expectDeliveryDate: p ? p.expectDeliveryDate : undefined,
      estimatedAmount: p ? p.estimatedAmount : undefined,
      planStartDate: m ? m.planStartDate : undefined,
      planEndDate: m ? m.planEndDate : undefined,
      // BOM 编号/版本：优先需求行自带的（后端已按默认→主→已生效→最新补显），其次生产建议
      bomCode: s.bomCode || (m && m.bomCode) || undefined,
      bomVersion: s.bomVersion || (m && m.bomVersion) || undefined,
      routingName: m ? m.routingName : undefined
    })
  })
  const satisfiedRows = satisfiedList.value.map(s => Object.assign({}, s, { _kind: 'satisfied' }))
  return shortageRows.concat(satisfiedRows)
})
/** 结果表当前展示行：类型 + 属性 + 关键字三重过滤 */
const filteredResultRows = computed(() => {
  let list = mergedResultRows.value
  if (resultKind.value !== 'all') list = list.filter(r => r._kind === resultKind.value)
  if (shortageFilter.type) list = list.filter(r => r.materialType === shortageFilter.type)
  const kw = (shortageFilter.keyword || '').toLowerCase()
  if (kw) {
    list = list.filter(r =>
      String(r.materialCode || '').toLowerCase().includes(kw) ||
      String(r.materialName || '').toLowerCase().includes(kw))
  }
  return list
})
/** 只有缺料行可勾选下推（库存满足行没有可下推的东西） */
function isResultRowSelectable (row) { return !!row && row._kind !== 'satisfied' }
/**
 * 勾选变化：把表里的「副本行」映射回 calcResult.shortages 里的原始行，
 * 否则「批量修改数量」改的是副本，保存/下推时拿到的还是旧值（静默失效）。
 */
function onResultSelectionChange (rows) {
  const picked = (rows || []).filter(r => r._kind !== 'satisfied')
  selectedShortages.value = picked.map(r => shortageList.value.find(s => s.materialCode === r.materialCode) || r)
  selectAllShortage.value = picked.length > 0 && picked.length === shortageList.value.length
}

// 详情弹窗
const detailDialog = reactive({
  visible: false,
  item: null,
  editQty: 0,
  editDate: '',
  editStartDate: ''
})

// 历史记录弹窗
const historyDialog = reactive({
  visible: false,
  loading: false,
  dateRange: null,
  status: '',
  list: [],
  page: { current: 1, size: 20, total: 0 }
})

// 批量修改弹窗
const batchEditDialog = reactive({
  visible: false,
  mode: 'fixed',
  fixedValue: 0,
  percentValue: 100,
  addValue: 0
})

const batchEditPreview = computed(() => {
  return selectedShortages.value.map(s => {
    let newQty = s.netDemand
    if (batchEditDialog.mode === 'fixed') {
      newQty = batchEditDialog.fixedValue
    } else if (batchEditDialog.mode === 'percent') {
      newQty = s.netDemand * batchEditDialog.percentValue / 100
    } else if (batchEditDialog.mode === 'add') {
      newQty = s.netDemand + batchEditDialog.addValue
    }
    if (newQty < 0) newQty = 0
    return {
      materialCode: s.materialCode,
      materialName: s.materialName,
      oldQty: s.netDemand,
      newQty
    }
  })
})

// 下推进度
const pushProgress = reactive({
  visible: false,
  percent: 0,
  status: '',
  logs: []
})

// ===================== 【2026-11-03 用户口径】物料需求展开 =====================
// ① 一个物料可有多份 BOM → BOM 列改下拉，切换即重算展开；
// ② 只要有 BOM 就立即展开物料（不等点计算）；
// ③ 支持多级布局（母件 0 级，子件按层级缩进）；
// ④ 点计算只把「缺多少」填进已展开的明细，不再由计算产生行。
const expandedRows = ref([])
const expandLoading = ref(false)

/** 该物料的全部 BOM（默认→主→已生效→最新排序，供下拉切换） */
function bomsOfMaterial (materialId) {
  if (materialId == null) return []
  return (bomList.value || [])
    .filter(b => b && String(b.materialId) === String(materialId))
    .sort((a, b) => bomRank(a) - bomRank(b) || Number(b.bomId|| 0) - Number(a.bomId || 0))
}

/** 切换某行的 BOM → 同步编号/版本 → 重新展开物料明细 */
async function onRowBomChange (row, bomId) {
  const b = (bomList.value || []).find(x => x.bomId === bomId)
  row.bomId = bomId || null
  row.bomCode = b ? (b.bomCode || '') : ''
  row.bomVersion = b ? (b.bomVersion || '') : ''
  await rebuildExpanded()
}

/** 当前需求来源下的需求行 */
function currentDemandRows () {
  const rows = demandSourceType.value === 'saleOrder' ? selectedMaterialItems.value : manualDemands.value
  return (rows || []).filter(r => r && r.materialId != null && Number(r.quantity) > 0)
}

/** 重建展开明细：逐个需求行按其 BOM 展开，母件行为 0 级，子件按层级缩进 */
let expandSeq = 0
async function rebuildExpanded () {
  const rows = currentDemandRows()
  if (!rows.length) { expandedRows.value = []; return }
  const seq = ++expandSeq
  expandLoading.value = true
  const out = []
  try {
    for (const r of rows) {
      const res = await expandBom({ materialId: r.materialId, bomId: r.bomId || null, quantity: Number(r.quantity) || 1 })
      if (seq !== expandSeq) return // 期间又改了选择 → 丢弃过期结果
      const d = res || {}
      out.push({
        _root: r.materialCode,
        _level: 0,
        materialId: r.materialId,
        materialCode: d.materialCode || r.materialCode || '',
        materialName: d.materialName || r.materialName || '',
        specification: d.specification || r.specification || '',
        materialType: d.materialType || '',
        unit: d.unit || '',
        usageQuantity: 1,
        grossRequirement: Number(r.quantity) || 0,
        bomCode: d.bomCode || r.bomCode || '',
        bomVersion: d.bomVersion || r.bomVersion || '',
        pushedQty: 0
      })
      ;(d.items || []).forEach(it => {
        out.push(Object.assign({}, it, {
          _root: r.materialCode,
          _level: it.level || 1,
          pushedQty: 0
        }))
      })
    }
    expandedRows.value = out
  } catch (e) {
    console.warn('展开BOM失败', e)
  } finally {
    if (seq === expandSeq) expandLoading.value = false
  }
}

/** 物料属性分类：原材料 → 采购；半成品/成品 → 生产 */
function isRawType (t) {
  const s = String(t || '')
  return s.includes('原') || s.includes('外购') || s.includes('包材') || s.includes('辅')
}
function isMakeType (t) {
  const s = String(t || '')
  return s.includes('半') || s.includes('成') || s.includes('自制')
}

/**
 * 把计算出的「缺多少」填进已展开的明细行（2026-11-03 用户口径：计算只填值，不产生行）。
 * 只有完全没有展开行（例如该物料没有 BOM）时，才补一条母件行。
 */
function applyReqToExpanded (record) {
  const list = expandedRows.value || []
  const lv = Number(record.level || 0)
  const hit = list.find(r => r.materialCode === record.materialCode && Number(r._level || 0) === lv)
    || list.find(r => r.materialCode === record.materialCode)
  const patch = {
    availableStock: record.availableStock,
    safetyStock: record.safetyStock,
    inTransitQty: record.inTransitQty,
    netDemand: record.netDemand,
    planQuantity: record.planQuantity,
    leadTime: record.leadTime,
    suggestDate: record.suggestDate,
    bomCode: record.bomCode || '',
    bomVersion: record.bomVersion || '',
    grossRequirement: record.grossDemand != null ? record.grossDemand : record.grossRequirement,
    materialType: record.materialType || '',
    specification: record.specification || '',
    unit: record.unit || '',
    _calcDone: true
  }
  if (hit) {
    Object.assign(hit, patch)
    return
  }
  expandedRows.value.push(Object.assign({
    _root: record.materialCode,
    _level: lv,
    materialId: record.materialId,
    materialCode: record.materialCode,
    materialName: record.materialName,
    usageQuantity: 1,
    pushedQty: 0
  }, patch))
}

// ===== 展开区：页签筛选 + 分批多次下推（2026-11-03 用户口径） =====
const expandTab = ref('all')
const expandSelection = ref([])

const expandTabRows = computed(() => {
  const list = expandedRows.value || []
  if (expandTab.value === 'raw') return list.filter(r => r._calcDone && isRawType(r.materialType))
  if (expandTab.value === 'make') return list.filter(r => r._calcDone && isMakeType(r.materialType))
  return list
})
/** 还能下推多少 = 计划下单量 − 已下推（计划下单量 = 净需求按物料最小订货量/采购批量修正后的值） */
function pushableQty (row) {
  const plan = row.planQuantity != null ? row.planQuantity : row.netDemand
  return Math.max(0, Number(plan || 0) - Number(row.pushedQty || 0))
}
function canPushRow (row) {
  return pushableQty(row) > 0
}
const selectedRawRows = computed(() => (expandSelection.value || [])
  .filter(r => isRawType(r.materialType) && pushableQty(r) > 0))
const selectedMakeRows = computed(() => (expandSelection.value || [])
  .filter(r => isMakeType(r.materialType) && pushableQty(r) > 0))
const canPushRaw = computed(() => selectedRawRows.value.length > 0)
const canPushMake = computed(() => selectedMakeRows.value.length > 0)
function onExpandSelectionChange (rows) {
  expandSelection.value = rows || []
}

/** 按页签下推：只推勾选行、只推「可下推数量」，成功后把已下推数量记到行上 */
async function pushExpandSelected (type) {
  const rows = (type === 'raw' ? selectedRawRows.value : selectedMakeRows.value).slice()
  if (!rows.length) {
    ElMessage.warning(tt('没有可下推的物料（净需求已全部下推）'))
    return
  }
  const label = type === 'raw' ? tt('采购申请') : tt('生产工单')
  try {
    await ElMessageBox.confirm(`${tt("确认将 ")}${rows.length}${tt(" 条物料下推到")}${label}${tt("？")}`, tt('下推确认'), { type: 'warning' })
  } catch (e) {
    return
  }
  const qtyMap = new Map()
  rows.forEach(r => qtyMap.set(r, pushableQty(r)))
  const items = rows.map(r => Object.assign({}, r, { netDemand: qtyMap.get(r), planQuantity: qtyMap.get(r) }))
  const ok = type === 'raw' ? await pushToPurchase(items) : await pushToProduction(items)
  if (!ok) return
  rows.forEach(r => { r.pushedQty = Number(r.pushedQty || 0) + qtyMap.get(r) })
  expandSelection.value = []
}

/** 明细行数量/日期改动 → 稍后重算展开（毛需求随数量变） */
let rebuildTimer = null
function onDemandRowChange () {
  if (rebuildTimer) clearTimeout(rebuildTimer)
  rebuildTimer = setTimeout(() => rebuildExpanded(), 400)
}

function clearManualDemands () {
  manualDemands.value = []
  expandedRows.value = []
  expandSelection.value = []
}

// 需求来源切换：清掉上一来源的展开结果，按新来源重新展开
watch(demandSourceType, () => {
  expandedRows.value = []
  expandSelection.value = []
  rebuildExpanded()
})

// 加载 BOM 清单（选物料后直接显示 BOM 编号/版本；与 MRP 展开口径一致取默认BOM）
async function loadBomOptions() {
  try {
    const list = await listAllBom()
    bomList.value = Array.isArray(list) ? list : (list?.records || [])
    // 名单先到、选料后到：把已选行补上 BOM 信息（后端已带回的不覆盖）
    ;(selectedMaterialItems.value || []).forEach(row => {
      if (row.materialId != null && !row.bomCode) fillBomInfo(row, row.materialId)
    })
    ;(manualDemands.value || []).forEach(row => {
      if (row.materialId != null) fillBomInfo(row, row.materialId)
    })
  } catch (e) {
    console.warn('加载BOM清单失败', e)
  }
}

// 加载物料选项
async function loadMaterialOptions() {
  try {
    const list = await listAllMaterial()
    materialList.value = Array.isArray(list) ? list : (list?.records || [])
    materialOptions.value = materialList.value.map(m => ({
      value: m.materialCode,
      label: `${m.materialName} (${m.materialCode})`,
      ...m
    }))
    // 【2026-10-09】物料自定义字段：取字段定义 + 预取全量值，供手工录入下拉按自定义字段搜索（与全站同源）
    mcf.init().then(() => mcf.preloadAll(materialList.value)).catch(() => {})
  } catch (e) {
    console.warn('加载物料失败', e)
  }
}

// 从「重新计算」进入：带出原方案的需求来源与运算参数（物料需重新选择，实现“从头开始”）
async function loadRecalcSource() {
  const fromId = route.query.from
  if (!fromId) return
  try {
    const data = await getMrpPlan(fromId)
    if (!data) return
    demandSourceType.value = data.demandSource === '手工录入' ? 'manual' : 'saleOrder'
    Object.assign(calcConfig, {
      planName: (data.planName || '') + ' (重新计算)',
      planHorizon: data.planHorizon ?? 90,
      timeFence: data.timeFenceDays ?? 7,
      lotSizeType: data.lotSizeType || '逐批法',
      fixedLotSize: Number(data.lotSize ?? 0),
      minOrderQty: Number(data.minOrderQty ?? 0),
      includeStock: data.includeOnhandStock !== 0,
      includeOpenPo: data.includeOpenPo !== 0,
      includeOpenMo: data.includeOpenMo !== 0,
      includeSafetyStock: data.includeSafetyStock !== 0,
      includeAltBom: false,
      includePr: false,
      expandAllLevel: true
    })
    ElMessage.info(tt('已带出原方案配置，请重新选择要分析的物料'))
  } catch (e) {
    console.warn('带出原方案配置失败', e)
  }
}

function removeMaterialItem(index) {
  selectedMaterialItems.value.splice(index, 1)
  rebuildExpanded()
}

function clearMaterialItems() {
  selectedMaterialItems.value = []
  expandedRows.value = []
}

/** 【2026-10-09】通用「选来源单」回调：从销售订单取物料明细进需求列表（来源单 + 交期一并带出） */
function onSaleOrderSourcePicked (sourceData, items) {
  const today = new Date().toISOString().slice(0, 10)
  ;(items || []).forEach(it => {
    const row = {
      orderNo: it.orderNo || (sourceData && sourceData.orderNo) || '',
      customerName: it.customerName || (sourceData && sourceData.customerName) || '',
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      specification: it.specification,
      quantity: it.quantity,
      hasBom: it.hasBom,
      bomId: it.bomId,
      bomCode: it.bomCode || '',
      bomVersion: it.bomVersion || '',
      bomName: it.bomName || '',
      orderId: it.orderId || (sourceData && sourceData.orderId),
      orderItemId: it.orderItemId,
      deliveryDate: it.deliveryDate,
      orderDate: it.orderDate,
      demandDate: it.deliveryDate || it.orderDate || today
    }
    // 选单带进来的行也要带「物料自定义字段」值（否则明细里的自定义字段列全空，等于没用）
    const full = (materialOptions.value || []).find(x => x.materialId === row.materialId || x.materialCode === row.materialCode)
    if (full) fillMaterialCustomFields(row, full)
    // 后端未返回 BOM 信息时，用本地 BOM 清单兜底
    if (!row.bomCode) fillBomInfo(row, row.materialId)
    selectedMaterialItems.value.push(row)
  })
  if ((items || []).length) ElMessage.success(tt('已从销售订单带入物料明细'))
  // 2026-11-03：选完来源单立即展开
  rebuildExpanded()
}

// 【2026-10-09 用户口径】需求日期：有来源销售订单时自动代入该行交期（deliveryDate→orderDate），且允许手工改
watch(selectedMaterialItems, (list) => {
  ;(list || []).forEach(it => {
    if (!it.demandDate) it.demandDate = it.deliveryDate || it.orderDate || ''
  })
}, { deep: true })

// 销售预测选择
function onForecastSelectionChange(rows) {
  selectedForecasts.value = rows
}

// 添加手工需求
function addManualDemand() {
  manualDemands.value.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    quantity: 1,
    demandDate: new Date().toISOString().slice(0, 10),
    priority: '中'
  })
}

// 步骤导航
function nextStep() {
  if (currentStep.value === 0) {
    // 验证需求来源
    if (demandSourceType.value === 'saleOrder' && selectedMaterialItems.value.length === 0) {
      ElMessage.warning(tt('请先选择要分析的物料'))
      return
    }
    if (demandSourceType.value === 'manual' && manualDemands.value.filter(d => d.materialId).length === 0) {
      ElMessage.warning(tt('请先添加手工需求'))
      return
    }
  }
  currentStep.value++
}

function prevStep() {
  currentStep.value--
}

// 执行MRP运算
async function executeCalc() {
  calculating.value = true
  currentStep.value = 2

  try {
    // 构建需求列表
    const demands = []

    if (demandSourceType.value === 'saleOrder') {
      // 直接使用已回填的物料明细（含销售订单来源关联，供保存方案明细与“已分析过”判定）
      const today = new Date().toISOString().slice(0, 10)
      for (const it of selectedMaterialItems.value) {
        if (!it.materialCode) continue
        const qty = Number(it.quantity) || 0
        if (qty <= 0) continue
        demands.push({
          materialCode: it.materialCode,
          quantity: qty,
          demandDate: it.demandDate || it.deliveryDate || it.orderDate || today,
          sourceBillNo: it.orderNo,
          saleOrderId: it.orderId,
          saleOrderItemId: it.orderItemId,
          saleOrderNo: it.orderNo,
          // 2026-11-03：按该行选定的 BOM 运算（同一物料可有多份 BOM）
          bomId: it.bomId || null
        })
      }
    } else if (demandSourceType.value === 'manual') {
      demands.push(...manualDemands.value.filter(d => d.materialId).map(d => ({
        materialCode: d.materialCode,
        quantity: Number(d.quantity) || 0,
        demandDate: d.demandDate || new Date().toISOString().slice(0, 10),
        bomId: d.bomId || null
      })))
    }

    if (demands.length === 0) {
      ElMessage.warning(tt('未找到有效需求数据'))
      currentStep.value = 0
      return
    }

    // 执行批量运算（把页面「怎么算」设置一并发给后端，确保设置真正生效）
    const res = await calculateMrpBatch(demands, { ...calcConfig })

    // 处理结果
    calcResult.calcTime = new Date().toLocaleString()
    calcResult.allItems = []
    calcResult.shortages = []
    calcResult.purchases = []
    calcResult.productions = []

    let grossTotal = 0, netTotal = 0, satisfiedCount = 0

    for (const item of (res || [])) {
      const requirements = item.requirements || []
      const purchaseSuggestions = item.purchaseSuggestions || []
      const productionSuggestions = item.productionSuggestions || []

      for (const req of requirements) {
        const mat = materialList.value.find(m => m.materialCode === req.materialCode) || {}
        const record = {
          ...req,
          materialType: mat.materialType || req.materialType || '',
          specification: mat.model || mat.specification || req.specification || '',
          unit: mat.baseUnitName || req.unit || '',
          suggestType: determineSuggestType(mat.materialType || req.materialType),
          // 2026-11-02：库存满足行也要带全结果列（原先只有 netRequirement，导致母件/满足行被
          // satisfiedList 过滤掉、整行不显示，用户看不到母件的 BOM 编号/版本与物料信息）
          netDemand: req.netRequirement,
          grossDemand: req.grossRequirement,
          availableStock: req.onHandQuantity || req.availableStock || 0,
          safetyStock: req.safetyStock || 0,
          inTransitQty: req.inTransitQuantity || 0,
          leadTime: req.leadTimeDays || mat.leadTime || 7,
          suggestDate: req.plannedOrderDate || new Date().toISOString().slice(0, 10)
        }
        // 成品行（与本次需求同物料）带上来销售订单明细关联，保存方案时写入方案明细
        if (item.saleOrderItemId && req.materialCode === item.materialCode) {
          record.saleOrderId = item.saleOrderId
          record.saleOrderItemId = item.saleOrderItemId
          record.saleOrderNo = item.saleOrderNo
        }
        calcResult.allItems.push(record)
        // 2026-11-03：把「缺多少」填进已展开的物料明细（不新增行）
        applyReqToExpanded(record)

        grossTotal += Number(req.grossRequirement) || 0
        netTotal += Number(req.netRequirement) || 0

        if (Number(req.netRequirement) > 0) {
          calcResult.shortages.push({
            ...record,
            grossDemand: req.grossRequirement,
            availableStock: req.onHandQuantity || req.availableStock || 0,
            safetyStock: req.safetyStock || 0,
            inTransitQty: req.inTransitQuantity || 0,
            netDemand: req.netRequirement,
            leadTime: req.leadTimeDays || mat.leadTime || 7,
            suggestDate: req.plannedOrderDate || new Date().toISOString().slice(0, 10)
          })
        } else {
          satisfiedCount++
        }
      }

      for (const pur of purchaseSuggestions) {
        calcResult.purchases.push({
          ...pur,
          suggestQty: pur.quantity || pur.suggestQty,
          expectDeliveryDate: calcExpectDate(pur.suggestedOrderDate, pur.leadTime || 7),
          estimatedPrice: pur.estimatedPrice || 0,
          estimatedAmount: pur.estimatedAmount || (pur.quantity * pur.estimatedPrice)
        })
      }

      for (const prod of productionSuggestions) {
        calcResult.productions.push({
          ...prod,
          suggestQty: prod.quantity || prod.suggestQty,
          // 计划开工/完工日：后端键为 plannedStartDate/plannedFinishDate，表格列读 planStartDate/planEndDate
          planStartDate: prod.planStartDate || prod.plannedStartDate || prod.suggestedOrderDate,
          planEndDate: prod.planEndDate || prod.plannedFinishDate || prod.suggestedDeliveryDate,
          leadTime: prod.leadTime || prod.leadTimeDays || 7,
          subMaterialCount: prod.subMaterialCount || 0
        })
      }
    }

    calcResult.materialCount = calcResult.allItems.length
    calcResult.grossTotal = grossTotal
    calcResult.netTotal = netTotal
    calcResult.satisfiedCount = satisfiedCount

    ElMessage.success(`${tt("MRP运算完成：")}${calcResult.materialCount}${tt(" 种物料，")}${calcResult.shortages.length}${tt(" 条缺料")}`)
    currentStep.value = 3

  } catch (e) {
    console.error('MRP运算失败', e)
    ElMessage.error(tt('运算失败，请检查需求数据'))
    currentStep.value = 1
  } finally {
    calculating.value = false
  }
}

// 判断建议类型
function determineSuggestType(materialType) {
  if (!materialType) return 'PURCHASE'
  const mt = materialType.toLowerCase()
  if (mt.includes('原') || mt.includes('raw') || mt.includes('外购')) return 'PURCHASE'
  if (mt.includes('半') || mt.includes('成') || mt.includes('semi') || mt.includes('finish')) return 'PRODUCE'
  return 'PURCHASE'
}

// 缺料勾选
function onShortageSelectionChange(rows) {
  selectedShortages.value = rows
  selectAllShortage.value = rows.length === filteredShortageList.value.length
}

function toggleSelectAllShortage(val) {
  if (val) {
    selectedShortages.value = [...filteredShortageList.value]
  } else {
    selectedShortages.value = []
  }
}

// 显示详情弹窗
function showDetailDialog(item) {
  detailDialog.item = item
  // 下推数量默认按计划下单量（净需求经最小订货量/采购批量修正后的值），用户仍可手改
  detailDialog.editQty = item ? (item.planQuantity != null ? item.planQuantity : item.netDemand) : 0
  detailDialog.editDate = item ? item.suggestDate : new Date().toISOString().slice(0, 10)
  detailDialog.editStartDate = item ? item.suggestDate : new Date().toISOString().slice(0, 10)
  detailDialog.visible = true
}

// 计算预计交货日
function calcExpectDeliveryDate() {
  if (!detailDialog.editDate) return '--'
  const date = new Date(detailDialog.editDate)
  const leadTime = detailDialog.item?.leadTime || 7
  date.setDate(date.getDate() + leadTime)
  return date.toISOString().slice(0, 10)
}

// 计算计划完工日
function calcPlanEndDate() {
  if (!detailDialog.editStartDate) return '--'
  const date = new Date(detailDialog.editStartDate)
  const leadTime = detailDialog.item?.leadTime || 7
  date.setDate(date.getDate() + leadTime)
  return date.toISOString().slice(0, 10)
}

function calcExpectDate(startDate, leadTime) {
  if (!startDate) return '--'
  const date = new Date(startDate)
  date.setDate(date.getDate() + leadTime)
  return date.toISOString().slice(0, 10)
}

// 下推单个物料
async function pushSingleItem(row) {
  const type = row.suggestType === 'PURCHASE' ? '采购申请' : '生产工单'
  try {
    await ElMessageBox.confirm(`${tt("确认下推 ")}${row.materialName}${tt(" 到")}${type}？`, tt('下推确认'), { type: 'warning' })
    if (row.suggestType === 'PURCHASE') {
      await pushToPurchase([row])
    } else {
      await pushToProduction([row])
    }
  } catch (e) {
    // 取消
  }
}

// 下推单个到采购
async function pushSingleToPurchase() {
  const item = detailDialog.item
  const qty = detailDialog.editQty
  if (qty <= 0) {
    ElMessage.warning(tt('采购数量必须大于0'))
    return
  }
  const newItem = { ...item, netDemand: qty, planQuantity: qty, suggestDate: detailDialog.editDate }
  await pushToPurchase([newItem])
  detailDialog.visible = false
}

// 下推单个到生产
async function pushSingleToProduction() {
  const item = detailDialog.item
  const qty = detailDialog.editQty
  if (qty <= 0) {
    ElMessage.warning(tt('生产数量必须大于0'))
    return
  }
  const newItem = { ...item, netDemand: qty, planQuantity: qty, suggestDate: detailDialog.editStartDate }
  await pushToProduction([newItem])
  detailDialog.visible = false
}

// 下推选中的到采购
async function pushSelectedToPurchase() {
  const items = selectedShortages.value.filter(s => s.suggestType === 'PURCHASE' || s.materialType === '原材料')
  if (items.length === 0) {
    ElMessage.warning(tt('选中的物料中没有可下推采购的（需为原材料或建议采购类型）'))
    return
  }
  try {
    await ElMessageBox.confirm(`${tt("确认将 ")}${items.length}${tt(" 条物料下推到采购申请单？")}`, tt('下推确认'), { type: 'warning' })
    await pushToPurchase(items)
  } catch (e) {
    // 取消
  }
}

// 下推选中的到生产
async function pushSelectedToProduction() {
  const items = selectedShortages.value.filter(s => s.suggestType === 'PRODUCE' || s.materialType === '半成品' || s.materialType === '成品')
  if (items.length === 0) {
    ElMessage.warning(tt('选中的物料中没有可下推生产的（需为半成品/成品或建议生产类型）'))
    return
  }
  try {
    await ElMessageBox.confirm(`${tt("确认将 ")}${items.length}${tt(" 条物料下推到生产工单？")}`, tt('下推确认'), { type: 'warning' })
    await pushToProduction(items)
  } catch (e) {
    // 取消
  }
}

// 执行下推采购
async function pushToPurchase(items) {
  pushProgress.visible = true
  pushProgress.percent = 0
  pushProgress.status = ''
  pushProgress.logs = []

  const today = new Date().toISOString().slice(0, 10)
  const prItems = items.map((s, idx) => {
    const mat = materialList.value.find(m => m.materialCode === s.materialCode) || {}
    pushProgress.logs.push({ type: 'success', message: `${s.materialCode} - ${s.materialName}${tt(" 净需求 ")}${formatNum(s.netDemand)}` })
    return {
      materialId: mat.materialId || s.materialId,
      materialCode: s.materialCode,
      materialName: s.materialName || mat.materialName,
      model: mat.model || s.specification || '',
      unit: mat.baseUnitName || s.unit || '',
      applyQty: Number(s.planQuantity != null ? s.planQuantity : s.netDemand),
      approvedQty: 0,
      suggestedSupplierId: mat.defaultSupplierId || null,
      suggestedSupplierName: mat.defaultSupplierName || '',
      sourceType: 'MRP',
      sourceBillNo: calcConfig.planName || 'MRP-' + today,
      sourceRowNo: String(idx + 1),
      demandDate: s.suggestDate || today,
      rowRemark: 'MRP运算下推'
    }
  })

  pushProgress.percent = 50

  try {
    await savePurchaseRequest({
      requestDate: today,
      demandDate: today,
      applyType: 'MRP采购',
      sourceType: 'MRP',
      sourceBillNo: calcConfig.planName || 'MRP-' + today,
      isUrgent: 0,
      status: '草稿',
      remark: `MRP运算下推 - ${items.length}条物料`,
      items: prItems
    })
    pushProgress.percent = 100
    pushProgress.status = 'success'
    pushProgress.logs.push({ type: 'success', message: `${tt("采购申请单已生成，明细数：")}${prItems.length}` })
    ElMessage.success(tt('采购申请单下推成功'))
    return true
  } catch (e) {
    pushProgress.status = 'exception'
    pushProgress.logs.push({ type: 'error', message: tt('下推失败：') + (e.message || '未知错误') })
    ElMessage.error(tt('采购申请单下推失败'))
    return false
  }
}

// 执行下推生产
async function pushToProduction(items) {
  pushProgress.visible = true
  pushProgress.percent = 0
  pushProgress.status = ''
  pushProgress.logs = []

  let successCount = 0
  let failCount = 0

  for (let i = 0; i < items.length; i++) {
    const s = items[i]
    pushProgress.percent = Math.round((i / items.length) * 80)

    try {
      const mat = materialList.value.find(m => m.materialCode === s.materialCode) || {}
      const today = new Date().toISOString().slice(0, 10)
      const planStart = s.suggestDate || today
      const leadTime = s.leadTime || mat.leadTime || 7
      const planEnd = calcExpectDate(planStart, leadTime)

      await createWorkOrder({
        orderDate: today,
        materialId: mat.materialId || s.materialId,
        materialCode: s.materialCode,
        materialName: s.materialName || mat.materialName,
        model: mat.model || s.specification || '',
        unitId: mat.baseUnitId,
        quantity: Number(s.planQuantity != null ? s.planQuantity : s.netDemand),
        warehouseId: mat.defaultWarehouseId || null,
        // 2026-11-03：下推带上前端选定的 BOM（同一物料可有多份）
        bomId: s.bomId || mat.bomId || null,
        bomVersion: s.bomVersion || '',
        routingId: mat.routingId || null,
        priority: '普通',
        planStartDate: planStart,
        planEndDate: planEnd,
        status: '草稿',
        remark: 'MRP运算下推-' + (s.materialType || '生产')
      })
      pushProgress.logs.push({ type: 'success', message: `${s.materialCode}${tt(" - 工单已生成")}` })
      successCount++
    } catch (e) {
      pushProgress.logs.push({ type: 'error', message: `${s.materialCode}${tt(" - 下推失败")}` })
      failCount++
    }
  }

  pushProgress.percent = 100
  pushProgress.status = failCount > 0 ? 'warning' : 'success'

  if (successCount > 0) {
    ElMessage.success(`${tt("已生成 ")}${successCount}${tt(" 张生产工单")}`)
  }
  if (failCount > 0) {
    ElMessage.warning(`${failCount}${tt(" 张工单生成失败")}`)
  }
  return successCount > 0
}

// 批量修改
function batchEditSelected() {
  if (selectedShortages.value.length === 0) {
    ElMessage.warning(tt('请先勾选物料'))
    return
  }
  batchEditDialog.visible = true
}

function applyBatchEdit() {
  for (const preview of batchEditPreview.value) {
    const original = selectedShortages.value.find(s => s.materialCode === preview.materialCode)
    if (original) {
      original.netDemand = preview.newQty
      // 2026-11-25：手工改过的数量同步为计划下单量，下推按调整后的量取数
      original.planQuantity = preview.newQty
    }
  }
  batchEditDialog.visible = false
  ElMessage.success(tt('批量调整已应用'))
}

// 采购汇总
function getPurchaseSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, index) => {
    if (index === 0) { sums[index] = '合计'; return }
    const prop = col.property
    if (['suggestQty', 'estimatedAmount'].includes(prop)) {
      sums[index] = data.reduce((prev, curr) => prev + (Number(curr[prop]) || 0), 0)
      sums[index] = formatNum(sums[index])
    } else {
      sums[index] = ''
    }
  })
  return sums
}

// 显示历史记录弹窗
function showHistoryDialog() {
  historyDialog.visible = true
  loadHistory()
}

// 加载历史记录
async function loadHistory() {
  historyDialog.loading = true
  try {
    const params = {
      current: historyDialog.page.current,
      size: historyDialog.page.size,
      status: historyDialog.status
    }
    if (historyDialog.dateRange && historyDialog.dateRange[0]) {
      params.startDate = historyDialog.dateRange[0]
      params.endDate = historyDialog.dateRange[1]
    }
    const res = await listMrpPlan(params)
    historyDialog.list = (res.records || res || []).map(p => ({
      ...p,
      calcTime: p.planDate || p.createTime,
      materialCount: p.totalNetDemand ? Math.round(p.totalNetDemand) : 0,
      netTotal: p.totalNetDemand || 0,
      purchaseCount: Math.round(p.totalPurchaseSuggestion || 0),
      productionCount: Math.round(p.totalProduceSuggestion || 0),
      status: p.planStatus === 1 ? '已下推' : '已保存'
    }))
    historyDialog.page.total = res.total || historyDialog.list.length
  } catch (e) {
    console.warn('加载历史记录失败', e)
    historyDialog.list = []
  } finally {
    historyDialog.loading = false
  }
}

// 查看历史详情
async function viewHistoryDetail(row) {
  try {
    const detail = await getMrpPlan(row.id)
    // 将历史数据填充到当前结果
    calcResult.allItems = detail.items || []
    calcResult.shortages = (detail.items || []).filter(i => Number(i.netDemand) > 0)
    calcResult.materialCount = calcResult.allItems.length
    calcResult.calcTime = row.calcTime
    calcConfig.planName = row.planName
    currentStep.value = 3
    historyDialog.visible = false
    ElMessage.success(tt('已加载历史运算结果'))
  } catch (e) {
    console.warn('获取历史详情失败', e)
  }
}

// 从历史重新运算
function reCalcFromHistory(row) {
  calcConfig.planName = row.planName + ' (重新运算)'
  currentStep.value = 0
  historyDialog.visible = false
  ElMessage.info(tt('请重新配置需求来源后执行运算'))
}

// 删除历史
async function deleteHistory(row) {
  try {
    await ElMessageBox.confirm(tt('确认删除该运算记录？'), tt('删除确认'), { type: 'warning' })
    await deleteMrpPlan(row.id)
    ElMessage.success(tt('删除成功'))
    loadHistory()
  } catch (e) {
    // 取消或失败
  }
}

// 保存当前运算到历史
async function saveCurrentToHistory() {
  if (calcResult.allItems.length === 0) {
    ElMessage.warning(tt('没有运算结果可保存'))
    return
  }
  try {
    const today = new Date().toISOString().slice(0, 10)
    // 需求单据 + 每行来源销售明细（供“已分析过”判定与来源回写）
    const demandOrderIds = [...new Set(selectedMaterialItems.value.map(r => r.orderId).filter(v => v != null))]
    await saveMrpPlan({
      planNo: '',
      planName: calcConfig.planName || 'MRP运算-' + today,
      planDate: today,
      planHorizon: calcConfig.planHorizon,
      // 2026-10-06：运算设置随方案一并落库（此前不落库 → 页面改了不生效）
      timeFenceDays: calcConfig.timeFence,
      lotSizeType: calcConfig.lotSizeType,
      lotSize: calcConfig.fixedLotSize,
      minOrderQty: calcConfig.minOrderQty,
      includeSo: demandSourceType.value === 'saleOrder' ? 1 : 0,
      includeForecast: 0,
      includeSafetyStock: calcConfig.includeSafetyStock ? 1 : 0,
      includeOpenPo: calcConfig.includeOpenPo ? 1 : 0,
      includeOpenMo: calcConfig.includeOpenMo ? 1 : 0,
      includeOnhandStock: calcConfig.includeStock ? 1 : 0,
      demandSource: demandSourceType.value === 'saleOrder' ? '销售订单' : '手工录入',
      demandOrderIds: demandOrderIds.length ? JSON.stringify(demandOrderIds) : null,
      planStatus: 0,
      totalNetDemand: calcResult.netTotal,
      totalPurchaseSuggestion: purchaseList.value.reduce((p, c) => p + Number(c.suggestQty), 0),
      totalProduceSuggestion: productionList.value.reduce((p, c) => p + Number(c.suggestQty), 0),
      remark: `涉及${calcResult.materialCount}种物料，缺料${calcResult.shortages.length}条`,
      items: calcResult.allItems.map(i => ({
        materialId: i.materialId,
        materialCode: i.materialCode,
        materialName: i.materialName,
        specification: i.specification,
        unit: i.unit,
        grossDemand: i.grossDemand || i.grossRequirement,
        availableStock: i.availableStock || i.onHandQuantity,
        safetyStock: i.safetyStock,
        netDemand: i.netDemand || i.netRequirement,
        // 2026-11-25：计划下单量按运算结果落库（含物料最小订货量/采购批量修正），不再覆盖成净需求
        planQuantity: i.planQuantity != null ? i.planQuantity : (i.netDemand || i.netRequirement),
        suggestType: i.suggestType,
        leadTime: i.leadTime,
        suggestDate: i.suggestDate,
        saleOrderId: i.saleOrderId ?? null,
        saleOrderItemId: i.saleOrderItemId ?? null,
        saleOrderNo: i.saleOrderNo || null
      }))
    })
    ElMessage.success(tt('已保存到运算历史'))
    historyDialog.visible = false
  } catch (e) {
    console.warn('保存失败', e)
    ElMessage.error(tt('保存失败'))
  }
}

// 格式化函数
function formatNum(val) {
  if (val == null || val === '') return '0'
  return formatWith(val, scaleFor('qty'), trimZero.value)
}

function formatMoney(val) {
  if (!val) return '0.00'
  return Number(val).toFixed(2)
}

// 行样式
function shortageRowClass({ row }) {
  return row.netDemand > 0 ? 'shortage-row' : ''
}

// 物料类型颜色
function materialTypeColor(type) {
  const map = {
    '原材料': 'warning',
    '半成品': '',
    '成品': 'success',
    '外购件': 'warning',
    '自制件': ''
  }
  return map[type] || 'info'
}

// 初始化
onMounted(async () => {
  await Promise.all([loadMaterialOptions(), loadBomOptions()])
  await loadRecalcSource()
})
useAutoRefresh(() => {
  loadMaterialOptions()
  loadBomOptions()
})
</script>

<style scoped lang="scss">
.mrp-page {
  padding: 16px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .header-left h3 {
    margin: 0 0 4px 0;
    font-size: 20px;
    color: #303133;
  }
  .header-sub {
    color: #909399;
    font-size: 13px;
  }
}

.mrp-steps {
  margin-bottom: 20px;
  background: #fff;
  padding: 12px;
  border-radius: 4px;
}

.step-card {
  margin-bottom: 16px;
  border-radius: 4px;

  .card-header-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-title {
    font-weight: 600;
    font-size: 15px;
  }

  .step-footer {
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid #ebeef5;
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
}

.source-section {
  .toolbar-row {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 12px;
  }
}

.config-form {
  .el-form-item {
    margin-bottom: 16px;
  }
}

.result-card {
  .header-actions {
    display: flex;
    gap: 8px;
    align-items: center;
  }
}

.stat-row {
  margin-bottom: 20px;
  padding: 16px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);
  border-radius: 4px;
}

.result-tabs {
  .tab-label {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .tab-badge {
    margin-left: 4px;
  }
}

.toolbar-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.toolbar-hint {
  font-size: 12px;
  color: #909399;
}

:deep(.shortage-row) {
  background-color: #fef0f0 !important;
}

.low-stock {
  color: #E6A23C;
}

.enough-stock {
  color: #67C23A;
}

.net-demand {
  color: #F56C6C;
  font-weight: 600;
}

.new-qty {
  color: #409EFF;
  font-weight: 600;
}

.detail-desc {
  margin-bottom: 16px;
}

.calc-process {
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;

  .process-row {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
  }

  .process-item {
    text-align: center;
    padding: 12px;
    background: #fff;
    border-radius: 4px;
    min-width: 100px;

    &.highlight {
      background: #ecf5ff;
      border: 2px solid #409EFF;
    }

    .process-label {
      font-size: 12px;
      color: #909399;
      margin-bottom: 4px;
    }

    .process-value {
      font-size: 20px;
      font-weight: 600;
      color: #303133;

      &.net-demand {
        color: #F56C6C;
      }
    }

    .process-note {
      font-size: 11px;
      color: #C0C4CC;
      margin-top: 4px;
    }
  }

  .process-arrow {
    font-size: 24px;
    color: #909399;
    font-weight: 600;
  }
}

.suggest-card {
  .el-card__header {
    padding: 12px 16px;
    background: #f5f7fa;
  }
}

.preview-section {
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;

  .preview-title {
    font-size: 13px;
    color: #606266;
    margin-bottom: 8px;
  }
}

// ===== 物料需求展开（2026-11-03） =====
.expand-card {
  margin-top: 12px;

  :deep(.el-tabs__header) {
    margin-bottom: 8px;
  }
}
.expand-tip {
  font-size: 12px;
  color: #909399;
  line-height: 22px;
}
.expand-tab-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 4px;
}
.mat-name {
  margin-left: 8px;
  color: #606266;
}
.lv-mark {
  color: #c0c4cc;
  margin-right: 4px;
}
.dash {
  color: #c0c4cc;
}
</style>
