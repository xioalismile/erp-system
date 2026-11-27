<!--
  打印模板设计器（表格网格范式改版 2026-11-22）
  - 版面用表格搭建：布局表格（静态网格）+ 数据表格（明细表）
  - 字段只能拖进单元格绑定，不再生成浮动文本块
  - 数据表格：明细行按单据数据展开 / 每页固定行数补空行 / 合计行 / 分组统计 / 单元格格式
  - 撤销重做（50 步）、网格吸附与对齐参考线、层级调整
  - 存储：templateContent = 带 data-block-id 的 HTML（表格设置随 data-* 属性保存）；templateStyle = 全局 CSS
  - 旧模板兼容：无块结构自动包裹，不保存不动库
-->
<template>
  <div class="pt-designer" :class="{ 'pt-code-mode': mode === 'code' }" @paste="onCanvasPaste">
    <!-- 顶栏：第一行模式与动作，第二行插入内容与选中元素的位置宽高（不放底部） -->
    <div class="pt-topbar">
      <div class="pt-tb-row">
        <div class="pt-tabs">
          <span class="pt-tab" :class="{ on: mode === 'design' }" @click="switchMode('design')">设计</span>
          <span class="pt-tab" :class="{ on: mode === 'code' }" @click="switchMode('code')">源码</span>
        </div>
        <span class="pt-sep"></span>
        <span class="pt-quick" :class="{ on: snapOn }" @click="snapOn = !snapOn">网格吸附</span>
        <div class="pt-zoom">
          <span class="pt-zoom-btn" :class="{ on: zoomMode === 'fitWidth' }" @click="setZoom('fitWidth')">适应宽度</span>
          <span class="pt-zoom-btn" :class="{ on: zoomMode === 'fitPage' }" @click="setZoom('fitPage')">适应整页</span>
          <span class="pt-zoom-btn" :class="{ on: zoomMode === 'custom' }" @click="setZoom('custom', 1)">100%</span>
          <span class="pt-zoom-btn" @click="stepZoom(0.1)">＋</span>
          <span class="pt-zoom-btn" @click="stepZoom(-0.1)">－</span>
          <span class="pt-zoom-pct">{{ Math.round(displayScale * 100) }}%</span>
        </div>
        <span class="pt-quick" :class="{ on: undoCan }" :title="'撤销 Ctrl+Z'" @click="undo()">↶ 撤销</span>
        <span class="pt-quick" :class="{ on: redoCan }" :title="'重做 Ctrl+Shift+Z'" @click="redo()">↷ 重做</span>
        <div class="pt-actions">
          <el-button size="small" @click="generateStandard" :icon="MagicStick">一键生成标准模板</el-button>
          <el-button size="small" @click="previewInPlace" :icon="View">预览效果</el-button>
          <el-button size="small" type="primary" @click="save" :loading="saving" :icon="Check">保存</el-button>
        </div>
      </div>
      <div class="pt-tb-row pt-tb-row-sub">
        <div class="pt-tb-group">
          <span class="pt-tb-cap">插入内容</span>
          <span class="pt-tb-mini">行</span>
          <el-input-number v-model="newTableRows" :min="1" :max="50" size="small" controls-position="right" class="pt-tb-num" />
          <span class="pt-tb-mini">列</span>
          <el-input-number v-model="newTableCols" :min="1" :max="20" size="small" controls-position="right" class="pt-tb-num" />
          <el-button size="small" type="primary" @click="addTable()">插入表格</el-button>
          <el-button size="small" @click="addImageBlock()">图片</el-button>
          <el-button size="small" @click="addQrBlock()">二维码</el-button>
          <el-button size="small" @click="addTextBlock()">文本段</el-button>
        </div>
        <div class="pt-tb-group pt-tb-group-pos" v-if="selectedBlock && movable(selectedBlock.type)">
          <span class="pt-tb-cap">选中：{{ blockTypeLabel(selectedBlock.type) }}</span>
          <span class="pt-tb-mini">左</span>
          <el-input-number v-model="sp.left" :min="0" :max="paperW" size="small" controls-position="right" class="pt-tb-num" @change="applyPosition" />
          <span class="pt-tb-mini">上</span>
          <el-input-number v-model="sp.top" :min="0" :max="paperH" size="small" controls-position="right" class="pt-tb-num" @change="applyPosition" />
          <span class="pt-tb-mini">宽</span>
          <el-input-number v-model="sp.width" :min="10" :max="paperW" size="small" controls-position="right" class="pt-tb-num" @change="applySize" />
          <span class="pt-tb-mini">高</span>
          <el-input-number v-model="sp.height" :min="6" :max="paperH" size="small" controls-position="right" class="pt-tb-num" :disabled="isTableBlock(selectedBlock.type)" @change="applySize" />
          <span class="pt-tb-unit">{{ isTableBlock(selectedBlock.type) ? '毫米 · 表格高度随内容' : '毫米' }}</span>
        </div>
        <span v-else class="pt-tb-hint">选中元素后可在这一行调整位置与宽高 · 纸张 {{ paperLabel }}</span>
      </div>
    </div>

    <!-- ============ 设计模式 ============ -->
    <div v-show="mode === 'design'" class="pt-grid">
      <!-- 左：数据源 + 明细勾选 + 插入内容 -->
      <div class="pt-sidebar">
        <div class="pt-sb-title">数据源 · 拖入单元格</div>
        <template v-for="g in fieldGroups" :key="g.key">
          <div class="pt-group">
            <div class="pt-group-name" @click="toggleGroup(g.key)">
              <span class="pt-arrow" :class="{ open: groupOpen[g.key] }"></span>{{ g.label }}
              <span v-if="g.key === 'items'" class="pt-def">默认展开</span>
            </div>
            <div class="pt-chips" v-show="groupOpen[g.key]">
              <template v-if="g.fields.length">
                <span v-for="f in g.fields" :key="g.key + (f.vkey || f.key)" class="pt-chip" :class="{ it: g.key === 'items' }"
                  draggable="true" @dragstart="onChipDragStart($event, f)" @click="insertFieldToCell(f)">
                  {{ f.label }}
                </span>
              </template>
              <div v-else class="pt-chip-empty">
                {{ g.key === 'items' ? '该单据无明细数据' : '暂无字段' }}
                <el-button v-if="!hasFieldMap" size="small" text type="primary" @click="$emit('reload-fields')">重试</el-button>
              </div>
            </div>
          </div>
        </template>

        <div class="pt-sb-title" style="margin-top:10px;">明细区 · 勾选即生成</div>
        <div class="pt-detail-list">
          <div v-for="row in itemFieldRows" :key="row.key" class="pt-detail-row" :class="{ on: row.checked }"
            :draggable="row.checked && !(aliasEdit && aliasEdit.key === row.key)" @dragstart="onDetailDragStart(row, $event)" @dragover.prevent @drop.prevent="onDetailDrop(row)">
            <span class="pt-detail-handle" :class="{ off: !row.checked }">≡</span>
            <el-checkbox :model-value="row.checked" size="small" @change="toggleDetailField(row.key, $event)">{{ row.label }}</el-checkbox>
            <el-input v-if="aliasEdit && aliasEdit.key === row.key" v-model="aliasEdit.value" size="small" class="pt-alias-input"
              placeholder="打印时叫法" @keydown.enter.prevent="commitAlias(row)" @keydown.esc.prevent="cancelAlias" @blur="commitAlias(row)" />
            <span v-else class="pt-alias" :class="{ set: !!row.alias }" @click.stop="startAlias(row)">{{ row.alias || '改叫法' }}</span>
          </div>
          <div v-if="!itemFieldRows.length" class="pt-chip-empty">该单据无明细字段</div>
        </div>
        <div class="pt-hint">勾选明细字段即生成明细表；拖动 ≡ 调整列顺序；点「改叫法」可改打印时的列名，留空恢复系统原名；取消全部勾选会移除明细表</div>
        <div class="pt-hint">字段拖进单元格完成绑定；单击单元格直接输入文字；插入表格与元素在顶部第二行；表格右键可增删单元格、加行列、合并与解除合并</div>

        <!-- 【2026-11-25】工序区：一个字段清单，勾工序字段出工序列，勾物料字段把物料行挂到每道工序下面 -->
        <template v-if="fields.operations.length">
          <div class="pt-sb-title" style="margin-top:10px;">工序区 · 勾选即生成</div>
          <div class="pt-detail-list">
            <div v-for="row in opFieldRows" :key="row.src + ':' + row.key" class="pt-detail-row" :class="{ on: row.checked }">
              <el-checkbox :model-value="row.checked" size="small" @change="toggleOpField(row.key, row.src, $event)">{{ row.label }}</el-checkbox>
            </div>
            <div v-if="!opFieldRows.length" class="pt-chip-empty">该单据无工序字段</div>
          </div>
          <div class="pt-hint">勾工序字段生成工序表；勾物料字段会把物料行挂到每道工序下面；取消勾选即移除该列</div>
        </template>
      </div>

      <!-- 中：纸面画布（外层占位按缩放预留空间，纸面 transform 等比缩放） -->
      <div class="pt-canvas-wrap" ref="canvasWrapRef" @dragover.prevent @drop="onCanvasDrop">
        <div class="pt-paper-holder" :style="holderStyle">
          <div class="pt-paper" :style="paperStyle" @click.self="selectBlock(null)" @contextmenu.prevent="onPaperCtx($event)">
          <span class="pt-margin-guide" :style="marginGuideStyle"></span>
          <span v-for="(gv, i) in guides.v" :key="'gv' + i" class="pt-guide v" :style="{ left: gv * MM_TO_PX + 'px' }"></span>
          <span v-for="(gh, i) in guides.h" :key="'gh' + i" class="pt-guide h" :style="{ top: gh * MM_TO_PX + 'px' }"></span>
          <div v-if="!blocks.length" class="pt-empty-guide">
            <div class="pt-eg-title">从左侧插入表格，开始搭建版面</div>
            <div class="pt-eg-sub">填好行数与列数插入表格 · 左侧勾选明细字段即成明细表 · 字段拖进单元格绑定</div>
          </div>
          <div v-for="(b, bi) in blocks" :key="b.id" :data-id="b.id"
            :class="['pt-block', 'pt-block-' + b.type, { selected: selectedId === b.id }]"
            :style="blockStyle(b, bi)"
            @click.stop="onBlockClick(b, $event)"
            @mousedown="onBlockMouseDown($event, b)">
            <div v-if="b.type === 'text'" class="pt-html" v-html="canvasTextHtml(b.html)" @dblclick.stop="editTextBlock(b)"></div>
            <table v-else-if="b.type === 'grid'" class="pt-t" :style="frameStyleOf(b)" v-html="gridHtml(b)" @click.stop="onCellClick(b, $event)"></table>
            <table v-else-if="b.type === 'dataTable'" class="pt-t" :style="frameStyleOf(b)" v-html="dataTableHtml(b)" @mousedown="onDtResizeDown(b, $event)" @click.stop="onCellClick(b, $event)"></table>
            <table v-else-if="b.type === 'opTable'" class="pt-t" :style="frameStyleOf(b)" v-html="opTableHtml(b)" @click.stop="onCellClick(b, $event)"></table>
            <img v-else-if="b.type === 'image'" :src="b.cfg && b.cfg.src" style="width:100%;height:100%;display:block;pointer-events:none;user-select:none;" :style="{ objectFit: b.cfg && b.cfg.keepRatio ? 'contain' : 'fill' }" draggable="false" />
            <div v-else class="pt-html" v-html="b.html"></div>
          </div>
          <template v-if="selectedBlock && movable(selectedBlock.type) && !isTableBlock(selectedBlock.type)">
            <span v-for="h in handles" :key="h" class="pt-handle" :class="'pt-handle-' + h" :style="handleStyle(h)" @mousedown.stop="onHandleMouseDown($event, h)"></span>
          </template>
          <textarea v-if="cellEdit" ref="cellEditorRef" class="pt-cell-editor" :style="cellEditStyle" v-model="cellEdit.value"
            @keydown.enter.exact.prevent="commitCellEdit" @keydown.esc.stop.prevent="cancelCellEdit"></textarea>
          <div v-if="cellEdit && cellEdit.kind === 'gridText'" class="pt-cell-toolbar" :style="cellToolbarStyle">
            <span class="pt-ct-label">字号</span>
            <el-select :model-value="cellFontPt" size="small" style="width:82px" filterable allow-create placeholder="跟随" @change="setCellFontSize">
              <el-option v-for="s in tableFontSizes" :key="s" :label="s + 'pt'" :value="s" />
            </el-select>
            <span class="pt-ct-label">字体</span>
            <el-select :model-value="cellFontFamily" size="small" style="width:104px" filterable allow-create clearable placeholder="跟随" @change="setCellFontFamily">
              <el-option v-for="f in fontFamilies" :key="f.value" :label="f.label" :value="f.value" />
            </el-select>
          </div>
          </div>
        </div>
      </div>

      <!-- 右：属性面板 -->
      <div class="pt-props">
        <div class="pt-props-title">属性面板</div>
        <div v-if="!selectedBlock" class="pt-empty">请选中一个元素</div>
        <template v-else>
          <div class="pt-prop-row">
            <span class="pt-prop-label">元素</span>
            <span class="pt-block-tag">{{ blockTypeLabel(selectedBlock.type) }}</span>
            <el-button size="small" text type="danger" @click="deleteBlock">删除</el-button>
          </div>

          <!-- 层级 -->
          <div class="pt-prop-row" v-if="movable(selectedBlock.type) && selectedBlock.type !== 'image'">
            <span class="pt-prop-label">层级</span>
            <div class="pt-seg">
              <span @click="zOrder('up')">上移</span>
              <span @click="zOrder('down')">下移</span>
              <span @click="zOrder('top')">置顶</span>
              <span @click="zOrder('bottom')">置底</span>
            </div>
          </div>

          <!-- 文本段属性 -->
          <template v-if="selectedBlock.type === 'text'">
            <div class="pt-prop-row">
              <span class="pt-prop-label">编辑</span>
              <el-button size="small" @click="editTextBlock(selectedBlock)">编辑内容</el-button>
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">字号</span>
              <el-select v-model="sp.fontSize" size="small" style="width:90px" filterable allow-create @change="applyTextStyle">
                <el-option v-for="s in fontSizes" :key="s" :label="s + 'pt'" :value="s" />
              </el-select>
              <el-button size="small" :type="sp.fontWeight === 'bold' ? 'primary' : 'default'" @click="toggleBold"><b>B</b></el-button>
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">字体</span>
              <el-select v-model="sp.fontFamily" size="small" style="width:150px" filterable allow-create clearable placeholder="跟随默认" @change="applyTextStyle">
                <el-option v-for="f in fontFamilies" :key="f.value" :label="f.label" :value="f.value" />
              </el-select>
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">对齐</span>
              <div class="pt-seg">
                <span :class="{ on: sp.textAlign === 'left' }" @click="setAlign('left')">左</span>
                <span :class="{ on: sp.textAlign === 'center' }" @click="setAlign('center')">中</span>
                <span :class="{ on: sp.textAlign === 'right' }" @click="setAlign('right')">右</span>
              </div>
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">字色</span>
              <el-color-picker v-model="sp.color" size="small" @change="applyTextStyle" />
            </div>
          </template>

          <!-- 图片属性 -->
          <template v-if="selectedBlock.type === 'image'">
            <div class="pt-prop-row">
              <span class="pt-prop-label">图片</span>
              <el-button size="small" @click="triggerImageUpload">更换图片</el-button>
              <el-button size="small" @click="clearImage">移除</el-button>
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">外部地址</span>
              <el-input v-model="imageUrlInput" size="small" placeholder="粘贴图片地址回车" @keyup.enter="applyImageUrl" />
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">保持比例</span>
              <el-switch v-model="sp.keepRatio" @change="applyImageStyle" />
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">图片层级</span>
              <el-button size="small" @click="sendImageBack">衬于文字下方</el-button>
              <el-button size="small" @click="sendImageFront">浮于文字上方</el-button>
            </div>
            <div class="pt-prop-note">Logo 默认衬于文字下方，不遮挡表头字段</div>
          </template>

          <!-- 布局表格属性 -->
          <template v-if="selectedBlock.type === 'grid'">
            <div class="pt-prop-row">
              <span class="pt-prop-label">边框</span>
              <div class="pt-seg">
                <span :class="{ on: gridBorderMode === 'all' }" @click="setGridBorder('all')">全边框</span>
                <span :class="{ on: gridBorderMode === 'outer' }" @click="setGridBorder('outer')">仅外框</span>
                <span :class="{ on: gridBorderMode === 'none' }" @click="setGridBorder('none')">无边框</span>
              </div>
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">粗细</span>
              <el-select v-model="sp.borderWidth" size="small" style="width:80px" filterable allow-create @change="applyBorder">
                <el-option v-for="w in borderWidths.filter(x => x > 0)" :key="w" :label="w + 'pt'" :value="w" />
              </el-select>
              <el-color-picker v-model="sp.borderColor" size="small" @change="applyBorder" />
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">行高</span>
              <el-input-number v-model="gridRowHeight" size="small" :min="4" :max="30" style="width:110px" @change="applyGridRowHeight" />
              <span class="pt-unit">毫米</span>
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">字号</span>
              <el-select v-model="gridFontPt" size="small" style="width:100px" filterable allow-create @change="applyGridFontPt">
                <el-option v-for="s in tableFontSizes" :key="s" :label="s + 'pt'" :value="s" />
              </el-select>
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">字体</span>
              <el-select v-model="gridFontFamily" size="small" style="width:150px" filterable allow-create clearable placeholder="跟随默认" @change="applyGridFontFamily">
                <el-option v-for="f in fontFamilies" :key="f.value" :label="f.label" :value="f.value" />
              </el-select>
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">内边距</span>
              <el-input-number v-model="gridPadMM" size="small" :min="0" :max="10" :step="0.5" style="width:100px" @change="applyGridPad" />
              <span class="pt-unit">毫米</span>
              <div class="pt-seg">
                <span :class="{ on: gridPadMM === 1 }" @click="setGridPad(1)">1</span>
                <span :class="{ on: gridPadMM === 2 }" @click="setGridPad(2)">2</span>
                <span :class="{ on: gridPadMM === 3 }" @click="setGridPad(3)">3</span>
              </div>
            </div>
            <div class="pt-prop-note">单击单元格直接输入文字；拖左侧字段进单元格绑定；右键单元格可增删单元格、合并与解除合并、加行列</div>
            <template v-if="selectedCell">
              <div class="pt-prop-row"><span class="pt-prop-label">单元格</span><span class="pt-block-tag">第{{ selectedCell.r + 1 }}行 第{{ selectedCell.c + 1 }}格</span></div>
              <div class="pt-prop-row pt-cell-border-row">
                <span class="pt-prop-label">边框</span>
                <div class="pt-seg">
                  <span :class="{ on: cellBorderAll === 'follow' }" @click="setCellBorderAll(null)">跟随整表</span>
                  <span :class="{ on: cellBorderAll === 'on' }" @click="setCellBorderAll(1)">全有</span>
                  <span :class="{ on: cellBorderAll === 'off' }" @click="setCellBorderAll(0)">全无</span>
                </div>
              </div>
              <div class="pt-prop-row pt-cell-side-row">
                <span class="pt-prop-label pt-prop-label-mini">上</span>
                <el-select :model-value="cellSide('t')" size="small" style="width:96px" @change="v => setCellSide('t', v)">
                  <el-option label="跟随整表" value="" />
                  <el-option label="有边框" value="1" />
                  <el-option label="无边框" value="0" />
                </el-select>
                <span class="pt-prop-label pt-prop-label-mini">下</span>
                <el-select :model-value="cellSide('b')" size="small" style="width:96px" @change="v => setCellSide('b', v)">
                  <el-option label="跟随整表" value="" />
                  <el-option label="有边框" value="1" />
                  <el-option label="无边框" value="0" />
                </el-select>
              </div>
              <div class="pt-prop-row pt-cell-side-row">
                <span class="pt-prop-label pt-prop-label-mini">左</span>
                <el-select :model-value="cellSide('l')" size="small" style="width:96px" @change="v => setCellSide('l', v)">
                  <el-option label="跟随整表" value="" />
                  <el-option label="有边框" value="1" />
                  <el-option label="无边框" value="0" />
                </el-select>
                <span class="pt-prop-label pt-prop-label-mini">右</span>
                <el-select :model-value="cellSide('r')" size="small" style="width:96px" @change="v => setCellSide('r', v)">
                  <el-option label="跟随整表" value="" />
                  <el-option label="有边框" value="1" />
                  <el-option label="无边框" value="0" />
                </el-select>
              </div>
              <div class="pt-prop-row pt-cell-font-row">
                <span class="pt-prop-label">本格字号</span>
                <el-select :model-value="cellFontPt" size="small" style="width:104px" filterable allow-create clearable placeholder="跟随整表" @change="setCellFontSize">
                  <el-option v-for="s in tableFontSizes" :key="s" :label="s + 'pt'" :value="s" />
                </el-select>
                <span class="pt-unit">pt</span>
              </div>
              <div class="pt-prop-row pt-cell-font-row">
                <span class="pt-prop-label">本格字体</span>
                <el-select :model-value="cellFontFamily" size="small" style="width:150px" filterable allow-create clearable placeholder="跟随整表" @change="setCellFontFamily">
                  <el-option v-for="f in fontFamilies" :key="f.value" :label="f.label" :value="f.value" />
                </el-select>
              </div>
              <div class="pt-prop-row">
                <span class="pt-prop-label">对齐</span>
                <div class="pt-seg">
                  <span :class="{ on: cellAlign === 'left' }" @click="setCellAlign('left')">左</span>
                  <span :class="{ on: cellAlign === 'center' }" @click="setCellAlign('center')">中</span>
                  <span :class="{ on: cellAlign === 'right' }" @click="setCellAlign('right')">右</span>
                </div>
              </div>
              <div class="pt-prop-row">
                <span class="pt-prop-label">本格边距</span>
                <el-input-number v-model="gridCellPadMM" size="small" :min="0" :max="10" :step="0.5" style="width:100px" @change="applyGridCellPad" />
                <span class="pt-unit">毫米</span>
                <el-button size="small" text @click="applyGridCellPadToRow">整行</el-button>
                <el-button size="small" text @click="clearGridCellPad">跟随整表</el-button>
              </div>
              <div class="pt-prop-row">
                <span class="pt-prop-label">格式</span>
                <el-select :model-value="cellSelFmt" size="small" style="width:120px" @change="setCellFmt">
                  <el-option v-for="o in fmtOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </div>
              <div class="pt-prop-row">
                <span class="pt-prop-label">解除绑定</span>
                <el-button size="small" @click="clearCellBind">清除</el-button>
              </div>
            </template>
          </template>

          <!-- 数据表格属性 -->
          <template v-if="selectedBlock.type === 'dataTable'">
            <div class="pt-sec">明细行</div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">行数</span>
              <div class="pt-seg">
                <span :class="{ on: dc.rowMode === 'auto' }" @click="setRowMode('auto')">按实际行数</span>
                <span :class="{ on: dc.rowMode === 'fixed' }" @click="setRowMode('fixed')">每页固定行数</span>
              </div>
            </div>
            <div class="pt-prop-row" v-if="dc.rowMode === 'fixed'">
              <span class="pt-prop-label">行数</span>
              <el-input-number v-model="dc.fixedRows" size="small" :min="1" :max="50" style="width:100px" @change="touchDc" />
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">行高</span>
              <div class="pt-seg">
                <span :class="{ on: !dc.rowAuto }" @click="setRowAuto(false)">固定</span>
                <span :class="{ on: dc.rowAuto }" @click="setRowAuto(true)">自适应</span>
              </div>
              <el-input-number v-if="!dc.rowAuto" v-model="dc.rowHeight" size="small" :min="4" :max="30" style="width:100px" @change="touchDc" />
              <span v-if="!dc.rowAuto" class="pt-unit">毫米</span>
            </div>

            <div class="pt-sec">边框</div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">样式</span>
              <div class="pt-seg">
                <span :class="{ on: dc.borderMode === 'all' }" @click="setDcBorder('all')">全边框</span>
                <span :class="{ on: dc.borderMode === 'outer' }" @click="setDcBorder('outer')">仅外框</span>
                <span :class="{ on: dc.borderMode === 'none' }" @click="setDcBorder('none')">无边框</span>
              </div>
            </div>
            <div class="pt-prop-row" v-if="dc.borderMode !== 'none'">
              <span class="pt-prop-label">粗细</span>
              <el-select v-model="dc.borderWidth" size="small" style="width:80px" filterable allow-create @change="touchDc">
                <el-option v-for="w in borderWidths.filter(x => x > 0)" :key="w" :label="w + 'pt'" :value="w" />
              </el-select>
              <el-color-picker v-model="dc.borderColor" size="small" @change="touchDc" />
            </div>

            <div class="pt-sec">字号</div>
            <div class="pt-prop-row">
              <el-select v-model="dc.fontPt" size="small" style="width:100px" filterable allow-create @change="touchDc">
                <el-option v-for="s in tableFontSizes" :key="s" :label="s + 'pt'" :value="s" />
              </el-select>
              <el-select v-model="dc.fontFamily" size="small" style="width:150px" filterable allow-create clearable placeholder="跟随默认" @change="touchDc">
                <el-option v-for="f in fontFamilies" :key="f.value" :label="f.label" :value="f.value" />
              </el-select>
            </div>

            <div class="pt-sec">内容边距</div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">表头</span>
              <el-input-number v-model="dtThPadMM" size="small" :min="0" :max="10" :step="0.5" style="width:100px" @change="applyDtThPad" />
              <span class="pt-unit">毫米</span>
              <span class="pt-prop-label">明细</span>
              <el-input-number v-model="dtBodyPadMM" size="small" :min="0" :max="10" :step="0.5" style="width:100px" @change="applyDtBodyPad" />
              <span class="pt-unit">毫米</span>
            </div>

            <div class="pt-sec">列设置</div>
            <div class="pt-prop-note">按住列名左侧手柄上下拖动，即可调整列顺序</div>
            <div v-for="(col, ci) in dc.cols" :key="ci" class="pt-col-row" @dragover.prevent @drop.prevent="onColDrop(ci)">
              <span class="pt-col-drag" draggable="true" title="拖动调整列顺序" @dragstart="onColDragStart(ci, $event)">⋮⋮</span>
              <span class="pt-col-idx">{{ ci + 1 }}</span>
              <el-input v-model="col.label" size="small" style="width:86px" placeholder="列名" @change="touchDc" />
              <el-select v-model="col.bind" size="small" style="width:110px" clearable placeholder="绑定字段" @change="onColBindChange(col)">
                <el-option v-for="f in fields.items" :key="f.key" :label="f.label" :value="f.key" />
              </el-select>
              <el-select v-model="col.fmt" size="small" style="width:96px" @change="touchDc">
                <el-option v-for="o in fmtOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-button size="small" text type="danger" @click="delCol(ci)">删</el-button>
            </div>
            <div class="pt-prop-row">
              <el-button size="small" @click="insCol(-1)">左侧加列</el-button>
              <el-button size="small" @click="insCol(1)">右侧加列</el-button>
              <span class="pt-unit">列数上限 20</span>
            </div>

            <div class="pt-sec">合计行</div>
            <div class="pt-prop-row">
              <el-switch v-model="dc.totalOn" @change="touchDc" />
              <span class="pt-unit">勾选求和的列</span>
            </div>
            <div class="pt-prop-row" v-if="dc.totalOn">
              <el-checkbox-group v-model="dc.totalCols" @change="touchDc">
                <el-checkbox v-for="(col, ci) in dc.cols" :key="ci" :value="ci" :label="ci" class="pt-ck">{{ col.label || ('第' + (ci + 1) + '列') }}</el-checkbox>
              </el-checkbox-group>
            </div>
            <div class="pt-prop-row" v-if="dc.totalOn">
              <span class="pt-prop-label">金额大写</span>
              <el-switch v-model="dc.totalRmb" :disabled="!dc.totalCols.length" @change="touchDc" />
              <span class="pt-unit" v-if="dc.totalCols.length">按第一个合计列生成</span>
              <span class="pt-unit" v-else>先勾选要合计的列</span>
            </div>

            <div class="pt-sec">分组统计</div>
            <div class="pt-prop-row">
              <el-switch v-model="dc.groupOn" @change="touchDc" />
              <template v-if="dc.groupOn">
                <el-select v-model="dc.groupCol" size="small" style="width:110px" placeholder="分组字段" @change="touchDc">
                  <el-option v-for="(col, ci) in dc.cols" :key="ci" :label="col.label" :value="ci" />
                </el-select>
                <el-switch v-model="dc.groupTotalOn" active-text="小计" @change="touchDc" />
              </template>
            </div>
            <div class="pt-prop-note" v-if="dc.groupOn">组头显示分组字段值；小计按合计列设置汇总</div>
            <div class="pt-prop-note">明细行打印时按单据明细条数自动展开；固定行数不足时补空行</div>
          </template>

          <!-- 【2026-11-25】工序表属性：三种版式 + 列设置 + 边框字号 -->
          <template v-if="selectedBlock.type === 'opTable'">
            <div class="pt-sec">版式</div>
            <div class="pt-prop-row">
              <div class="pt-seg">
                <span :class="{ on: oc.mode === 'rows' }" @click="setOpMode('rows')">一行一道工序</span>
                <span :class="{ on: oc.mode === 'detail' }" @click="setOpMode('detail')">明细表式</span>
                <span :class="{ on: oc.mode === 'custom' }" @click="setOpMode('custom')">工序名称 + 自定义内容</span>
              </div>
            </div>
            <div class="pt-prop-note" v-if="oc.mode === 'detail'">工序名称固定首列并合并；勾了物料字段时，每道工序下展开全部物料行</div>
            <div class="pt-prop-note" v-if="oc.mode === 'custom'">每道工序占多行，一行一个字段；空值也占行，左列工序名称合并</div>

            <div class="pt-sec">列设置</div>
            <div class="pt-prop-note">字段在左侧「工序区」勾选增删；物料字段勾选后自动切到明细表式</div>
            <div v-for="(col, ci) in oc.cols" :key="ci" class="pt-col-row">
              <span class="pt-col-idx">{{ ci + 1 }}</span>
              <el-input v-model="col.label" size="small" style="width:96px" placeholder="列名" @change="syncToSource" />
              <span class="pt-unit">{{ col.src === 'item' ? '物料' : '工序' }}</span>
              <span v-if="col.bind === 'operationName' && col.src === 'operation'" class="pt-unit">固定首列</span>
              <el-button v-else size="small" text type="danger" @click="removeOpCol(ci)">删</el-button>
            </div>

            <div class="pt-sec">边框</div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">样式</span>
              <div class="pt-seg">
                <span :class="{ on: oc.borderMode === 'all' }" @click="oc.borderMode = 'all'; syncToSource()">全边框</span>
                <span :class="{ on: oc.borderMode === 'outer' }" @click="oc.borderMode = 'outer'; syncToSource()">仅外框</span>
                <span :class="{ on: oc.borderMode === 'none' }" @click="oc.borderMode = 'none'; syncToSource()">无边框</span>
              </div>
            </div>
            <div class="pt-prop-row" v-if="oc.borderMode !== 'none'">
              <span class="pt-prop-label">粗细</span>
              <el-select v-model="oc.borderWidth" size="small" style="width:80px" filterable allow-create @change="syncToSource">
                <el-option v-for="w in borderWidths.filter(x => x > 0)" :key="w" :label="w + 'pt'" :value="w" />
              </el-select>
              <el-color-picker v-model="oc.borderColor" size="small" @change="syncToSource" />
            </div>

            <div class="pt-sec">字号与行高</div>
            <div class="pt-prop-row">
              <el-select v-model="oc.fontPt" size="small" style="width:100px" filterable allow-create @change="syncToSource">
                <el-option v-for="s in tableFontSizes" :key="s" :label="s + 'pt'" :value="s" />
              </el-select>
              <el-select v-model="oc.fontFamily" size="small" style="width:150px" filterable allow-create clearable placeholder="跟随默认" @change="syncToSource">
                <el-option v-for="f in fontFamilies" :key="f.value" :label="f.label" :value="f.value" />
              </el-select>
            </div>
            <div class="pt-prop-row">
              <span class="pt-prop-label">行高</span>
              <el-input-number v-model="oc.rowHeight" size="small" :min="4" :max="30" style="width:100px" @change="syncToSource" />
              <span class="pt-unit">毫米</span>
            </div>
          </template>

          <!-- 位置 / 宽高已统一放在顶部第二行，属性面板只保留样式与内容设置 -->
          <div class="pt-prop-note" v-if="movable(selectedBlock.type)">
            {{ isTableBlock(selectedBlock.type) ? '表格高度随行数、行高、字号自动变化；宽度与位置在顶部第二行调整' : '位置与宽高在顶部第二行调整' }}
          </div>
        </template>
      </div>

      <!-- 表格右键菜单 -->
      <div v-if="ctxMenu.visible" class="pt-ctx" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
        <template v-for="(mi, miIdx) in ctxMenuItems" :key="miIdx">
          <div v-if="mi.k === 'sep'" class="pt-ctx-sep"></div>
          <div v-else class="pt-ctx-item" @click="runCtx(mi.k)">{{ mi.label }}</div>
        </template>
      </div>
      <input ref="fileInputRef" type="file" accept="image/png,image/jpeg,image/gif" style="display:none" @change="onFileChange" />
    </div>

    <!-- ============ 源码模式 ============ -->
    <div v-show="mode === 'code'" class="pt-code">
      <div class="pt-code-left">
        <div class="pt-code-head">模板内容 · 每个块一个元素；手工改过的块画布不覆盖</div>
        <textarea ref="sourceHtmlRef" class="pt-code-area" v-model="sourceHtml"></textarea>
      </div>
      <div class="pt-code-right">
        <div class="pt-code-head">全局 CSS</div>
        <textarea ref="sourceCssRef" class="pt-code-area" v-model="sourceCss"></textarea>
      </div>
      <div class="pt-code-hint">可用：{{ '\{\{字段\}\}' }} 占位、{{ '\{\{#items\}\}...\{\{/items\}\}' }} 明细循环、{{ '\{\{#header\}\}...\{\{/header\}\}' }} 条件块、{{ '\{\{qr:bill\}\}' }} 二维码</div>
    </div>

    <!-- 预览 iframe -->
    <el-dialog v-model="previewVisible" title="打印预览" width="900px" top="5vh" destroy-on-close>
      <iframe ref="previewIframeRef" class="pt-preview-iframe"></iframe>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, View, Check } from '@element-plus/icons-vue'
import { uploadFile } from '@/api/common/upload'
import { printTemplateApi } from '@/api/system/print'

const _p = defineProps({
  modelContent: { type: String, default: '' },
  modelStyle: { type: String, default: '' },
  docType: { type: String, default: '' },
  pageSize: { type: String, default: 'A4' },
  pageOrientation: { type: String, default: 'portrait' },
  marginTop: { type: Number, default: 15 },
  marginBottom: { type: Number, default: 15 },
  marginLeft: { type: Number, default: 15 },
  marginRight: { type: Number, default: 15 },
  fieldMap: { type: Object, default: () => ({}) },
  isEdit: { type: Boolean, default: false },
  docLabel: { type: String, default: '' }
})
const emit = defineEmits(['update:modelContent', 'update:modelStyle', 'save', 'reload-fields'])

// ============ 常量 ============
const MM_TO_PX = 3
const fontSizes = [8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36]
const borderWidths = [0, 0.25, 0.5, 0.75, 1, 1.5, 2]
const handles = ['tl', 'tr', 'bl', 'br']
const PAPER = { A4: { w: 210, h: 297 }, A5: { w: 148, h: 210 } }
const MAX_COLS = 20
const tableFontSizes = [8, 9, 10.5, 12, 14, 16]
const fmtOptions = [
  { value: '', label: '默认' },
  { value: 'thousand,d2', label: '金额' },
  { value: 'rmb', label: '金额大写' },
  { value: 'image', label: '图片' },
  { value: 'd2', label: '两位小数' },
  { value: 'date', label: '日期' },
  { value: 'upper', label: '大写' }
]
// 单元格四边（顺序固定：上、右、下、左，与 data-bd 的 4 位编码一致）
const GRID_SIDES = [['t', 'top'], ['r', 'right'], ['b', 'bottom'], ['l', 'left']]
// 常用中文字体（可手输自定义字体）；值用 CSS 字体名，保证打印与画布一致
const fontFamilies = [
  { label: '微软雅黑', value: 'Microsoft YaHei' },
  { label: '宋体', value: 'SimSun' },
  { label: '黑体', value: 'SimHei' },
  { label: '楷体', value: 'KaiTi' },
  { label: '仿宋', value: 'FangSong' },
  { label: 'Arial', value: 'Arial' }
]

// ============ 状态 ============
const mode = ref('design')
const blocks = ref([])
const selectedId = ref(null)
const selectedCell = ref(null) // { blockId, r, c }
const cellEdit = ref(null) // { blockId, r, c, kind, value }
const sourceHtml = ref('')
const sourceCss = ref('')
const saving = ref(false)
const previewVisible = ref(false)
const canvasWrapRef = ref()
const fileInputRef = ref()
const previewIframeRef = ref()
const cellEditorRef = ref()
const imageUrlInput = ref('')
let blockSeq = 0
let idSeed = 0

const snapOn = ref(true)
const guides = reactive({ v: [], h: [] })
const history = ref([])
const future = ref([])

const ctxMenu = reactive({ visible: false, x: 0, y: 0, blockId: null, r: -1, c: -1 })
const groupOpen = reactive({ header: true, items: true, footer: true, employee: true, customer: true })
const aliasEdit = ref(null) // 明细区自定义叫法的行内编辑 { key, value }
const pendingAlias = reactive({}) // 未勾选明细字段先改的叫法：key → 叫法，勾选生成明细列时自动采用

const sp = reactive({
  fontSize: 12, fontWeight: 'normal', textAlign: 'left', color: '#303133', fontFamily: '',
  keepRatio: true, borderWidth: 0.5, borderColor: '#303133',
  left: 0, top: 0, width: 100, height: 40
})
const dc = reactive({ // 数据表格设置（选中时从块同步）
  cols: [], rowMode: 'auto', fixedRows: 10, rowHeight: 8, rowAuto: false,
  totalOn: false, totalCols: [], totalRmb: false, groupOn: false, groupCol: null, groupTotalOn: false,
  borderMode: 'all', borderWidth: 0.5, borderColor: '#303133', thPad: '', cellPad: '', fontPt: 12, fontFamily: ''
})
const gridRowHeight = ref(8)
const gridFontPt = ref(9)
const gridFontFamily = ref('')
const cellFontPt = ref('')
const cellFontFamily = ref('')
const gridBorderMode = ref('all')
const gridPadMM = ref(2)
const gridCellPadMM = ref(2)
const dtThPadMM = ref(2)
const dtBodyPadMM = ref(2)
const newTableRows = ref(3)
const newTableCols = ref(5)
let detailDragKey = ''

// ============ 计算 ============
const paperW = computed(() => PAPER[_p.pageSize]?.w || 210)
const paperH = computed(() => PAPER[_p.pageSize]?.h || 297)
// 画布缩放：默认「适应宽度」让纸面撑满工作区（含放大），可切整页/100%/步进
const wrapW = ref(0)
const wrapH = ref(0)
const zoomMode = ref('fitPage')
const zoomCustom = ref(1)
const paperPxW = computed(() => (_p.pageOrientation === 'landscape' ? paperH.value : paperW.value) * MM_TO_PX)
const paperPxH = computed(() => (_p.pageOrientation === 'landscape' ? paperW.value : paperH.value) * MM_TO_PX)
// 页边距辅助线（毫米 → 画布 px）
const marginGuideStyle = computed(() => {
  const t = Math.max(0, Number(_p.marginTop) || 0)
  const b = Math.max(0, Number(_p.marginBottom) || 0)
  const l = Math.max(0, Number(_p.marginLeft) || 0)
  const r = Math.max(0, Number(_p.marginRight) || 0)
  const w = (_p.pageOrientation === 'landscape' ? paperH.value : paperW.value) - l - r
  const h = (_p.pageOrientation === 'landscape' ? paperW.value : paperH.value) - t - b
  return {
    left: (l * MM_TO_PX) + 'px',
    top: (t * MM_TO_PX) + 'px',
    width: (w * MM_TO_PX) + 'px',
    height: (h * MM_TO_PX) + 'px'
  }
})
const clampScale = (v) => Math.min(2.5, Math.max(0.3, v))
const displayScale = computed(() => {
  const availW = Math.max(200, wrapW.value - 24)
  const availH = Math.max(200, wrapH.value - 24)
  if (zoomMode.value === 'fitPage') return clampScale(Math.min(availW / paperPxW.value, availH / paperPxH.value))
  if (zoomMode.value === 'custom') return clampScale(zoomCustom.value)
  return clampScale(availW / paperPxW.value)
})
function setZoom(mode, v) {
  if (mode === 'custom' && v != null) zoomCustom.value = clampScale(v)
  zoomMode.value = mode
}
function stepZoom(d) {
  zoomCustom.value = clampScale(displayScale.value + d)
  zoomMode.value = 'custom'
}
const paperStyle = computed(() => {
  const w = _p.pageOrientation === 'landscape' ? paperH.value : paperW.value
  const h = _p.pageOrientation === 'landscape' ? paperW.value : paperH.value
  return {
    width: (w * MM_TO_PX) + 'px',
    height: (h * MM_TO_PX) + 'px',
    transform: `scale(${displayScale.value})`,
    transformOrigin: 'top left',
    fontFamily: '"Microsoft YaHei", "微软雅黑", SimSun, sans-serif'
  }
})
// 外层占位：按缩放后的实际大小预留布局空间，并保持居中
const holderStyle = computed(() => {
  const w = _p.pageOrientation === 'landscape' ? paperH.value : paperW.value
  const h = _p.pageOrientation === 'landscape' ? paperW.value : paperH.value
  const s = displayScale.value
  return { width: (w * MM_TO_PX * s) + 'px', height: (h * MM_TO_PX * s) + 'px' }
})
const paperLabel = computed(() => `${_p.pageSize} ${_p.pageOrientation === 'landscape' ? '横向' : '纵向'}`)
const selectedBlock = computed(() => blocks.value.find(b => b.id === selectedId.value) || null)
const hasFieldMap = computed(() => !!(_p.fieldMap && _p.fieldMap.docTypeFieldsMap))
const undoCan = computed(() => history.value.length > 0)
const redoCan = computed(() => future.value.length > 0)

const fields = computed(() => {
  const m = _p.fieldMap?.docTypeFieldsMap?.[_p.docType] || {}
  return {
    header: m.header || [],
    items: m.items || _p.fieldMap?.commonItemFields || [],
    // 【2026-11-25】生产工单工序表字段（标准工序字段 + 工序自定义字段）
    operations: m.operations || [],
    footer: m.footer || [],
    employee: _p.fieldMap?.employeeFields || [],
    customer: _p.fieldMap?.customerFields || []
  }
})
// 把金额合计 / 金额大写做成可拖字段：底层仍绑真实金额键，格式随字段一起带过去
const virtualFooterFields = computed(() => {
  const list = fields.value.footer || []
  const amount = list.find(f => /netAmount|totalAmount|amount/i.test(f.key))
  if (!amount) return []
  const out = [
    { key: amount.key, vkey: amount.key + '@sum', label: '金额合计', fmt: 'thousand,d2' },
    { key: amount.key, vkey: amount.key + '@rmb', label: '金额大写', fmt: 'rmb' }
  ]
  // 价税合计大写：与金额大写并列提供（价税合计字段在合计区或表头任一存在即可）
  const net = list.find(f => f.key === 'netAmount') || list.find(f => /价税合计/.test(f.label || ''))
    || fields.value.header.find(f => f.key === 'netAmount') || fields.value.header.find(f => /价税合计/.test(f.label || ''))
  if (net && net.key !== amount.key) {
    out.push({ key: net.key, vkey: net.key + '@rmb', label: '价税合计大写', fmt: 'rmb' })
  }
  return out
})
const fieldGroups = computed(() => [
  { key: 'header', label: '单据信息', fields: fields.value.header },
  { key: 'items', label: '明细字段', fields: fields.value.items },
  { key: 'footer', label: '合计 / 签字', fields: [...fields.value.footer, ...virtualFooterFields.value] },
  { key: 'employee', label: '员工信息', fields: fields.value.employee },
  { key: 'customer', label: '客户信息', fields: fields.value.customer }
])
const detailTable = computed(() => {
  const sel = selectedBlock.value
  if (sel && sel.type === 'dataTable') return sel
  return blocks.value.find(b => b.type === 'dataTable') || null
})
const itemFieldRows = computed(() => {
  const cols = detailTable.value?.cfg?.cols || []
  const rows = []
  const seen = new Set()
  cols.forEach(col => {
    if (!col.bind || seen.has(col.bind)) return
    seen.add(col.bind)
    const f = fields.value.items.find(x => x.key === col.bind)
    const sys = f?.label || col.bind
    // label 固定显示系统字段名；alias 是仅打印时显示的自定义叫法
    rows.push({ key: col.bind, label: sys, alias: col.label && col.label !== sys ? col.label : '', checked: true })
  })
  fields.value.items.forEach(f => {
    if (!seen.has(f.key)) rows.push({ key: f.key, label: f.label, alias: pendingAlias[f.key] || '', checked: false })
  })
  return rows
})
// ===== 工序表（2026-11-25）：一个字段清单，勾工序字段出工序列，勾物料字段把物料行挂到每道工序下面 =====
const opTableBlock = computed(() => {
  const sel = selectedBlock.value
  if (sel && sel.type === 'opTable') return sel
  return blocks.value.find(b => b.type === 'opTable') || null
})
const opFieldRows = computed(() => {
  const block = opTableBlock.value
  const mode = block?.cfg?.mode || 'rows'
  const seen = new Set()
  ;(block?.cfg?.cols || []).forEach(c => { if (c.bind) seen.add(c.src + ':' + c.bind) })
  const rows = []
  ;(fields.value.operations || []).forEach(f => rows.push({
    key: f.key, label: f.label, src: 'operation', checked: seen.has('operation:' + f.key)
  }))
  if (mode !== 'custom') {
    // 只列前 12 个物料字段，避免侧栏过长；纯物料表仍走上面的「明细字段」
    ;(fields.value.items || []).slice(0, 12).forEach(f => rows.push({
      key: f.key, label: '物料·' + f.label, src: 'item', checked: seen.has('item:' + f.key)
    }))
  }
  return rows
})
const oc = computed(() => opTableBlock.value?.cfg || {})
function opModeLabel (m) {
  return { rows: '一行一道工序', detail: '明细表式', custom: '工序名称 + 自定义内容' }[m] || ''
}
/** 取当前工序表；没有则创建（取消最后一个字段时会移除整块） */
function ensureOpTable (create = true) {
  let b = opTableBlock.value
  if (!b && create) {
    b = newBlock('opTable', 15, nextFreeTop(), {
      width: paperW.value - 30,
      height: 40,
      style: '',
      cfg: {
        mode: 'rows',
        cols: [],
        rowHeight: 8,
        rowAuto: false,
        borderMode: 'all',
        borderWidth: 0.5,
        borderColor: '#303133',
        thPad: '2mm',
        cellPad: '2mm',
        fontPt: 9
      }
    })
    blocks.value.push(b)
    selectBlock(b.id)
  }
  return b
}
/** 明细表式 / 自定义内容式：工序名称固定第一列（合并锚点） */
function ensureOpNameCol (b) {
  if (!b || !b.cfg) return
  if (!b.cfg.cols.some(c => c.bind === 'operationName' && c.src === 'operation')) {
    b.cfg.cols.unshift({ label: '工序名称', bind: 'operationName', src: 'operation', fmt: '', align: '', w: 0 })
  }
}
function toggleOpField (key, src, checked) {
  const b = ensureOpTable(checked)
  if (!b) return
  pushHistory()
  if (checked) {
    if (!b.cfg.cols.some(c => c.bind === key && c.src === src)) {
      const f = (src === 'operation' ? fields.value.operations : fields.value.items).find(x => x.key === key)
      b.cfg.cols.push({
        label: f?.label || key,
        bind: key,
        src,
        fmt: defaultFmtOf(key),
        align: '',
        w: 0
      })
    }
    if (src === 'item') b.cfg.mode = 'detail'
    if (b.cfg.mode !== 'rows') ensureOpNameCol(b)
    selectBlock(b.id)
  } else {
    // 明细表式 / 自定义内容式的「工序名称」是固定首列与合并锚点，不允许取消
    if (key === 'operationName' && src === 'operation' && b.cfg.mode !== 'rows') return
    b.cfg.cols = b.cfg.cols.filter(c => !(c.bind === key && c.src === src))
    if (!b.cfg.cols.length) {
      blocks.value = blocks.value.filter(x => x.id !== b.id)
      selectedId.value = null
      selectedCell.value = null
    } else {
      selectBlock(b.id)
    }
  }
  syncToSource()
}
function setOpMode (m) {
  const b = opTableBlock.value
  if (!b) return
  pushHistory()
  b.cfg.mode = m
  // 一行一道工序 / 自定义内容式不支持物料列；物料列只在明细表式下生效
  if (m !== 'detail') b.cfg.cols = b.cfg.cols.filter(c => c.src !== 'item')
  if (m !== 'rows') ensureOpNameCol(b)
  syncToSource()
}
function removeOpCol (idx) {
  const b = opTableBlock.value
  if (!b) return
  pushHistory()
  b.cfg.cols.splice(idx, 1)
  if (!b.cfg.cols.length) {
    blocks.value = blocks.value.filter(x => x.id !== b.id)
    selectedId.value = null
    selectedCell.value = null
  }
  syncToSource()
}

const cellSelFmt = computed(() => {
  const b = selectedBlock.value
  const loc = selectedCell.value
  if (!b || !loc) return ''
  if (b.type === 'grid') return b.cfg.rows[loc.r]?.[loc.c]?.fmt || ''
  if (b.type === 'dataTable' && loc.r >= 0) return b.cfg.cols[loc.c]?.fmt || ''
  return ''
})

const ctxMenuItems = computed(() => {
  const b = blocks.value.find(x => x.id === ctxMenu.blockId)
  if (!b) return []
  if (b.type === 'grid') {
    return [
      { k: 'mergeRight', label: '向右合并' },
      { k: 'mergeDown', label: '向下合并' },
      { k: 'split', label: '解除合并' },
      { k: 'sep' },
      { k: 'insCell', label: '新增单元格' },
      { k: 'delCell', label: '删除单元格' },
      { k: 'sep' },
      { k: 'insRowAbove', label: '上方插入行' },
      { k: 'insRowBelow', label: '下方插入行' },
      { k: 'delRow', label: '删除行' },
      { k: 'sep' },
      { k: 'insColLeft', label: '左侧插入列' },
      { k: 'insColRight', label: '右侧插入列' },
      { k: 'delCol', label: '删除列' }
    ]
  }
  if (b.type === 'dataTable') {
    return [
      { k: 'dtColLeft', label: '左侧插入列' },
      { k: 'dtColRight', label: '右侧插入列' },
      { k: 'dtColDel', label: '删除该列' }
    ]
  }
  return []
})

const cellEditStyle = computed(() => {
  if (!cellEdit.value) return {}
  const ed = cellEdit.value
  // 输入框按当前单元格的字号/字体显示，选中字段或改字号时所见即所得
  const fontStyle = {}
  const b = blocks.value.find(x => x.id === ed.blockId)
  if (b && b.type === 'grid') {
    const cell = b.cfg.rows[ed.r]?.[ed.c]
    const sizePt = (cell && cell.fs) ? cell.fs : (b.cfg.fontPt || 9)
    if (sizePt > 0) fontStyle.fontSize = (sizePt * 1.0584).toFixed(1) + 'px'
    const ff = (cell && cell.ff) ? cell.ff : (b.cfg.fontFamily || '')
    if (ff) fontStyle.fontFamily = ff
  }
  // 单元格编辑：输入框精确覆盖被点的那个单元格
  if (ed.rect) {
    const r = ed.rect
    return Object.assign(fontStyle, {
      left: r.left + 'px',
      top: r.top + 'px',
      width: Math.max(28, r.width) + 'px',
      height: Math.max(18, r.height) + 'px'
    })
  }
  if (!b) return fontStyle
  return Object.assign(fontStyle, {
    left: (b.left * MM_TO_PX) + 'px',
    top: (b.top * MM_TO_PX) + 'px',
    width: (b.width * MM_TO_PX) + 'px',
    height: (b.height * MM_TO_PX) + 'px'
  })
})
const cellToolbarStyle = computed(() => {
  const ed = cellEdit.value
  if (!ed || !ed.rect) return { display: 'none' }
  const r = ed.rect
  const barH = 34
  const top = r.top > barH + 4 ? r.top - barH - 2 : r.top + r.height + 4
  return { left: r.left + 'px', top: top + 'px' }
})

// ============ 工具 ============
function esc(s) {
  if (s === null || s === undefined) return ''
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
// 单元格文字：首尾空格、纯空格、连续空格转不换行空格，保证输入的空白占位在画布、预览与真实打印中都保留宽度
function escCellText(s) {
  if (s === null || s === undefined) return ''
  return esc(s).replace(/^ +| +$| {2,}/g, m => '&nbsp;'.repeat(m.length))
}
function nextId() { return 'b' + (idSeed++) }
let uidSeq = 100
function uid() { return 'u' + (uidSeq++) }
function mkCell(extra) {
  return Object.assign({ t: '', bind: '', fmt: '', label: '', span: 1, rspan: 1, hide: false, mfrom: null, align: '', bold: false, bd: '', fs: '', ff: '', uid: uid() }, extra || {})
}
function movable(t) { return ['text', 'grid', 'dataTable', 'opTable', 'image', 'qr'].includes(t) }
function isTableBlock(t) { return t === 'grid' || t === 'dataTable' || t === 'opTable' }
function blockTypeLabel(t) {
  return { text: '文本段', grid: '布局表格', dataTable: '数据表格', opTable: '工序表', image: '图片', qr: '二维码' }[t] || t
}
function parseStyle(s) {
  const o = {}
  String(s || '').split(';').forEach(p => {
    const [k, v] = p.split(':').map(x => x && x.trim())
    if (k) o[k] = v
  })
  return o
}
function styleToStr(o) { return Object.entries(o).filter(([, v]) => v !== undefined && v !== '').map(([k, v]) => `${k}:${v}`).join(';') }
// 从样式串里取字体名（旧模板没存 data-font-family 时兜底）
function styleFontFamily(s) {
  const m = String(s || '').match(/font-family\s*:\s*([^;"]+)/i)
  return m ? m[1].trim().replace(/^['"]|['"]$/g, '') : ''
}
// 去掉样式里的边框声明：表格边框统一由「边框」设置决定，避免旧样式残留把「无边框」顶回来
function stripBorderProps(s) {
  return String(s || '').split(';').filter(p => p.trim() && !/^\s*border(-[a-z]+)?\s*:/.test(p)).join(';')
}
function mmVal(v) {
  if (!v) return 0
  const m = String(v).match(/([\d.]+)mm/)
  return m ? parseFloat(m[1]) : 0
}
// 只读样式串里的 width 声明（mm），避免把 height/padding 的毫米值误当列宽
function widthDeclMm(v) {
  if (!v) return 0
  const m = String(v).match(/(?:^|;)\s*width\s*:\s*([\d.]+)\s*mm/i)
  return m ? parseFloat(m[1]) : 0
}
function mmOfPad(v, fallback = 2) {
  const n = mmVal(v)
  return n > 0 ? n : fallback
}
function newBlock(type, x, y, extra) {
  return Object.assign({
    id: nextId(), type, left: x, top: y, width: 100, height: 30, style: '', html: '', locked: false, cfg: null, fmt: ''
  }, extra || {})
}

// ============ 历史（撤销 / 重做） ============
function snapshot() {
  return JSON.stringify({ blocks: blocks.value, seq: idSeed })
}
function pushHistory() {
  history.value.push(snapshot())
  if (history.value.length > 50) history.value.shift()
  future.value = []
}
function restore(json) {
  const s = JSON.parse(json)
  blocks.value = s.blocks
  idSeed = s.seq
  selectedId.value = null
  selectedCell.value = null
  syncToSource()
}
function undo() {
  if (!history.value.length) return
  future.value.push(snapshot())
  restore(history.value.pop())
}
function redo() {
  if (!future.value.length) return
  history.value.push(snapshot())
  restore(future.value.pop())
}
function clearHistory() { history.value = []; future.value = [] }

// ============ 初始化 / 旧模板兼容 ============
function init() {
  idSeed = 0
  Object.keys(pendingAlias).forEach(k => delete pendingAlias[k])
  if (_p.modelContent && _p.modelContent.trim()) {
    const parsed = parseHtmlToBlocks(_p.modelContent)
    blocks.value = parsed.blocks
    idSeed = parsed.nextId
  } else {
    blocks.value = []
  }
  sourceCss.value = _p.modelStyle || ''
  // 新增模板保持空白画布：需要标准版式时用顶部「一键生成标准模板」，避免一打开就被预置内容占满
  syncToSource()
  selectedId.value = null
  selectedCell.value = null
  clearHistory()
  // 旧模板无坐标块：等真实渲染后用实测高度重排一次，消灭"高度估算偏小导致的重叠"
  nextTick(() => { relayoutAuto(); fitTableBlocks() })
}

/** 旧模板自动流式块：按真实渲染高度自上而下重排（只跑一次，不写历史） */
function relayoutAuto(retry = 0) {
  const autoBlocks = blocks.value.filter(b => b.auto)
  if (!autoBlocks.length) return
  const paperEl = canvasWrapRef.value?.querySelector('.pt-paper')
  if (!paperEl) {
    if (retry < 3) setTimeout(() => relayoutAuto(retry + 1), 80)
    return
  }
  const k = MM_TO_PX * displayScale.value
  autoBlocks.sort((a, b) => a.top - b.top)
  // 起点：位于这些块上方的定位块底部
  let flow = 10
  blocks.value.forEach(b => {
    if (!b.auto) flow = Math.max(flow, b.top + b.height + 2)
  })
  for (const b of autoBlocks) {
    const el = paperEl.querySelector(`[data-id="${b.id}"]`)
    const hPx = el ? el.getBoundingClientRect().height : b.height * k
    const hMM = Math.max(6, Math.round(hPx / k) + 1)
    b.top = flow
    b.height = hMM
    flow += hMM + 2
  }
  syncToSource()
}
watch(() => [_p.modelContent, _p.docType], () => { init() }, { immediate: true })

function detectType(el) {
  const t = el.getAttribute && el.getAttribute('data-block-type')
  if (t) return t
  if (el.tagName === 'TABLE') {
    // 【2026-11-25】工序表：与明细数据表格同为 table，靠 data-table-kind 区分
    if ((el.getAttribute('data-table-kind') || '') === 'operation') return 'opTable'
    return 'dataTable'
  }
  if (el.tagName === 'IMG') return 'image'
  return 'text'
}

function parseHtmlToBlocks(html) {
  const div = document.createElement('div')
  div.innerHTML = html
  const out = []
  let nextId = 0
  // 旧模板可能是"无坐标的整包 HTML"（甚至多个块都缺坐标）→ 缺坐标的一律按内容实测高度纵向堆叠，
  // 绝不再套同一个默认坐标（否则多个块会全部叠在左上角，即用户反馈的"打开全挤在一起"）。
  const measure = document.createElement('div')
  measure.style.cssText = `position:fixed;left:-9999px;top:0;width:${(paperW.value - 20) * MM_TO_PX}px;visibility:hidden;`
  document.body.appendChild(measure)
  let flowTop = 10
  const usedPos = new Set()
  const stripPos = (s) => s.split(';').filter(p => p && !/^\s*(position|left|top|width|height)\s*:/.test(p)).join(';')

  for (const el of Array.from(div.children)) {
    const bid = el.getAttribute('data-block-id')
    const btype = detectType(el)
    const id = bid || ('b' + (nextId++))
    const styleStr = el.getAttribute('style') || ''
    const cs = parseStyle(styleStr)
    const fmt = el.getAttribute('data-fmt') || ''
    const positioned = !!(cs.left && cs.top)
    const autoFlow = !positioned || usedPos.has(`${mmVal(cs.left)}:${mmVal(cs.top)}`)
    // 逐个声明（不写成 let a, b, c）：发布闸门按「let 变量名」识别声明，合并声明会被误判为未声明
    let left
    let top
    let width
    let height
    let style
    if (!autoFlow) {
      left = mmVal(cs.left); top = mmVal(cs.top)
      width = mmVal(cs.width) || (paperW.value - 20)
      height = mmVal(cs.height) || 40
      // 坐标只由 left/top/width/height 四个字段承载；样式里必须剥掉旧的定位声明，
      // 否则保存后会同时存在新旧两套坐标，CSS 后者生效 → 移动过的元素回到原位
      style = stripPos(styleStr)
      usedPos.add(`${left}:${top}`)
      flowTop = Math.max(flowTop, top + height + 2)
    } else {
      // 测真实高度（临时挂到屏外同宽容器）
      measure.innerHTML = ''
      measure.appendChild(el.cloneNode(true))
      height = Math.max(6, Math.ceil(measure.offsetHeight / MM_TO_PX) + 1)
      left = 10
      top = flowTop
      width = paperW.value - 20
      style = stripPos(styleStr)
      flowTop += height + 2
    }
    if (btype === 'opTable') {
      out.push({ id, type: 'opTable', left, top, width, height, style, html: '', locked: false, fmt: '', cfg: parseOpTableCfg(el) })
    } else if (btype === 'dataTable' || btype === 'table') {
      out.push({ id, type: 'dataTable', left, top, width, height, style, html: '', locked: false, fmt: '', cfg: parseDataTableCfg(el) })
    } else if (btype === 'grid') {
      out.push({ id, type: 'grid', left, top, width, height, style, html: '', locked: false, fmt: '', cfg: parseGridCfg(el) })
    } else if (btype === 'image') {
      const src = el.getAttribute('src') || ''
      out.push({ id, type: 'image', left, top, width, height, style, html: '', locked: !!bid, fmt: '', cfg: { src, keepRatio: (cs['object-fit'] || 'contain') === 'contain' } })
    } else if (btype === 'qr') {
      out.push({ id, type: 'qr', left, top, width, height, style, html: el.innerHTML, locked: !!bid, fmt: '', cfg: null })
    } else {
      out.push({ id, type: 'text', left, top, width, height, style, html: el.innerHTML, locked: !!bid, fmt, cfg: null })
    }
    if (autoFlow) out[out.length - 1].auto = true
    const num = parseInt(String(id).replace('b', ''), 10)
    if (!isNaN(num)) nextId = Math.max(nextId, num + 1)
  }
  measure.remove()
  // 兜底：只有裸文本（无可拆元素）
  if (out.length === 0 && div.textContent.trim()) {
    out.push({ id: 'b0', type: 'text', left: 10, top: 10, width: paperW.value - 20, height: 200, style: '', html: div.innerHTML, locked: false, fmt: '', cfg: null })
    nextId = 1
  }
  return { blocks: out, nextId }
}

// 数据表格：从保存的 HTML 还原设置模型
function parseDataTableCfg(el) {
  // 明细行可能被 {{#items}}...{{/items}} 包裹（文本节点，DOM 查不到）→ 先剥标记再解析
  const tmp = document.createElement('div')
  tmp.innerHTML = el.outerHTML.replace(/\{\{\/?items\}\}/g, '')
  const table = tmp.querySelector('table') || (el.tagName === 'TABLE' ? el : null)
  const cfg = {
    cols: [], rowMode: 'auto', fixedRows: 10, rowHeight: 8, rowAuto: false,
    totalOn: false, totalCols: [], totalRmb: false, groupOn: false, groupCol: null, groupLabel: '', groupTotalOn: false
  }
  if (!table) return cfg
  const attrs = a => table.getAttribute(a)
  const rows = attrs('data-rows') || 'auto'
  if (rows.startsWith('fixed:')) { cfg.rowMode = 'fixed'; cfg.fixedRows = Math.max(1, Math.min(50, parseInt(rows.slice(6), 10) || 10)) }
  const rh = parseFloat(attrs('data-row-height'))
  if (rh > 0) cfg.rowHeight = rh
  const total = attrs('data-total')
  const group = attrs('data-group')
  const groupTotal = attrs('data-group-total')
  cfg.thBg = attrs('data-th-bg') || ''
  cfg.cellPad = attrs('data-cell-pad') || ''
  cfg.thPad = attrs('data-th-pad') || cfg.cellPad
  cfg.cellValign = attrs('data-cell-valign') || ''
  cfg.totalRmb = !!attrs('data-total-rmb')
  cfg.fontPt = parseFloat(attrs('data-font-pt')) || 12
  cfg.fontFamily = attrs('data-font-family') || styleFontFamily(table.getAttribute('style'))
  const ths = Array.from(table.querySelectorAll('thead th'))
  // 列宽以 colgroup 列定义为准（保存时写在这里）；表头 width 声明仅作旧模板兜底
  const colEls = Array.from(table.querySelectorAll('colgroup col'))
  const detailRow = Array.from(table.querySelectorAll('tbody tr')).find(tr => tr.getAttribute('data-role') === 'detail') || table.querySelector('tbody tr')
  const tds = detailRow ? Array.from(detailRow.querySelectorAll('td')) : []
  const n = Math.max(ths.length, tds.length, 1)
  for (let i = 0; i < n; i++) {
    const th = ths[i]
    const td = tds[i]
    const bindM = td ? (td.getAttribute('data-bind') || (td.textContent.match(/\{\{([^}]+)\}\}/)?.[1]?.trim() || '')) : ''
    const fmt = td ? (td.getAttribute('data-fmt') || '') : ''
    const w = (colEls[i] ? mmVal(colEls[i].getAttribute('style')) : 0) || (th ? widthDeclMm(th.getAttribute('style')) : 0)
    const align = td && td.getAttribute('style') && td.getAttribute('style').includes('text-align:right') ? 'right' : (td && td.getAttribute('style') && td.getAttribute('style').includes('text-align:center') ? 'center' : '')
    const rawLabel = th ? th.textContent.replace(/\u00a0/g, ' ') : ''
    cfg.cols.push({ label: th ? (rawLabel.trim() ? rawLabel.trim() : rawLabel) : ('列' + (i + 1)), bind: bindM, fmt, align, w: w || 0 })
  }
  if (total) {
    cfg.totalOn = true
    const keys = total.split(',').map(s => s.trim()).filter(Boolean)
    cfg.totalCols = keys.map(k => cfg.cols.findIndex(c => c.bind === k)).filter(i => i >= 0)
  }
  if (group) {
    cfg.groupOn = true
    cfg.groupCol = cfg.cols.findIndex(c => c.bind === group)
    cfg.groupLabel = attrs('data-group-label') || ''
    cfg.groupTotalOn = !!(groupTotal && groupTotal.trim())
  }
  // 边框：优先读保存时的边框设置，旧模板回退到表格样式里的 border
  const tstyle = table.getAttribute('style') || ''
  const bm = tstyle.match(/border:\s*([\d.]+)pt\s+solid\s+([^;"]+)/)
  cfg.borderMode = table.getAttribute('data-border-mode') || 'all'
  cfg.borderWidth = parseFloat(table.getAttribute('data-border-width')) || (bm ? parseFloat(bm[1]) : 0.5)
  cfg.borderColor = table.getAttribute('data-border-color') || (bm ? bm[2].trim() : '#303133')
  return cfg
}

// 工序表：从保存的 HTML 还原版式、列与自定义内容行
function parseOpTableCfg(el) {
  const tmp = document.createElement('div')
  tmp.innerHTML = el.outerHTML.replace(/\{\{\/?items\}\}/g, '')
  const table = tmp.querySelector('table') || (el.tagName === 'TABLE' ? el : null)
  const cfg = {
    mode: 'rows', cols: [], rowMode: 'auto', rowHeight: 8, rowAuto: false,
    borderMode: 'all', borderWidth: 0.5, borderColor: '#303133',
    thPad: '', cellPad: '', fontPt: 9, fontFamily: ''
  }
  if (!table) return cfg
  const attr = a => table.getAttribute(a)
  cfg.mode = attr('data-op-mode') || 'rows'
  const rh = parseFloat(attr('data-row-height'))
  if (rh > 0) cfg.rowHeight = rh
  cfg.fontPt = parseFloat(attr('data-font-pt')) || 9
  cfg.fontFamily = attr('data-font-family') || ''
  cfg.borderMode = attr('data-border-mode') || 'all'
  cfg.borderWidth = parseFloat(attr('data-border-width')) || 0.5
  cfg.borderColor = attr('data-border-color') || '#303133'
  cfg.thPad = attr('data-th-pad') || ''
  cfg.cellPad = attr('data-cell-pad') || ''
  const ths = Array.from(table.querySelectorAll('thead th'))
  const detailRow = Array.from(table.querySelectorAll('tbody tr')).find(tr => tr.getAttribute('data-role') === 'detail')
    || table.querySelector('tbody tr')
  const tds = detailRow ? Array.from(detailRow.querySelectorAll('td')) : []
  const colEls = Array.from(table.querySelectorAll('colgroup col'))
  if (cfg.mode === 'custom') {
    const name = ths[0] ? (ths[0].textContent || '').trim() : '工序名称'
    cfg.cols.push({ label: name || '工序名称', bind: 'operationName', src: 'operation', fmt: '', align: '', w: 0 })
    const spec = attr('data-op-lines') || ''
    spec.split(',').forEach(part => {
      if (!part.trim()) return
      const i = part.indexOf('|')
      const key = i >= 0 ? part.slice(0, i).trim() : part.trim()
      const label = i >= 0 ? part.slice(i + 1) : key
      if (key) cfg.cols.push({ label, bind: key, src: 'operation', fmt: '', align: '', w: 0 })
    })
  } else {
    const n = Math.max(ths.length, tds.length)
    for (let i = 0; i < n; i++) {
      const th = ths[i]
      const td = tds[i]
      const bind = td ? (td.getAttribute('data-bind') || '') : ''
      const src = td ? (td.getAttribute('data-col-src') || 'operation') : 'operation'
      const fmt = td ? (td.getAttribute('data-fmt') || '') : ''
      const w = colEls[i] ? mmVal(colEls[i].getAttribute('style')) : 0
      cfg.cols.push({
        label: th ? (th.textContent || '').trim() : ('列' + (i + 1)),
        bind, src, fmt, align: '', w: w || 0
      })
    }
  }
  return cfg
}

// 布局表格：从 HTML 还原矩阵模型（含合并还原）
function parseGridCfg(el) {
  const table = el.tagName === 'TABLE' ? el : el.querySelector('table')
  const cfg = { rows: [], colW: [] }
  if (!table) return cfg
  const cols = Array.from(table.querySelectorAll('colgroup col'))
  cfg.colW = cols.map(c => mmVal(c.getAttribute('style')) || 0)
  const trs = Array.from(table.querySelectorAll('tr'))
  const grid = [] // 占位矩阵
  let maxCols = 0
  const cellAt = (r, c) => {
    grid[r] = grid[r] || []
    return grid[r][c]
  }
  trs.forEach((tr, r) => {
    grid[r] = grid[r] || []
    let c = 0
    Array.from(tr.querySelectorAll('td,th')).forEach(td => {
      while (cellAt(r, c)) c++
      const span = Math.max(1, parseInt(td.getAttribute('colspan') || '1', 10))
      const rspan = Math.max(1, parseInt(td.getAttribute('rowspan') || '1', 10))
      const st = td.getAttribute('style') || ''
      const dataText = td.getAttribute('data-text')
      const cell = {
        t: dataText !== null ? dataText : (td.getAttribute('data-bind') ? '' : td.innerHTML.replace(/<[^>]+>/g, '').trim()),
        bind: td.getAttribute('data-bind') || '',
        fmt: td.getAttribute('data-fmt') || '',
        label: td.getAttribute('data-label') || '',
        span, rspan, hide: false, mfrom: null,
        align: st.includes('text-align:right') ? 'right' : (st.includes('text-align:center') ? 'center' : ''),
        bold: st.includes('font-weight:bold') || td.tagName === 'TH',
        pad: td.getAttribute('data-cell-pad') || '',
        bd: td.getAttribute('data-bd') || '',
        fs: parseFloat(td.getAttribute('data-fs')) || '',
        ff: td.getAttribute('data-ff') || ''
      }
      for (let dr = 0; dr < rspan; dr++) {
        for (let dc = 0; dc < span; dc++) {
          if (dr === 0 && dc === 0) continue
          grid[r + dr] = grid[r + dr] || []
          grid[r + dr][c + dc] = { hide: true, mfrom: [r, c] }
        }
      }
      grid[r][c] = cell
      c += span
      maxCols = Math.max(maxCols, c)
    })
  })
  // 归一化：每行按自身真实格数补齐（行内增删单元格会让各行格数不同，不能统一补满）
  for (let r = 0; r < grid.length; r++) {
    const gr = grid[r] || []
    let last = 0
    for (let c = 0; c < maxCols; c++) if (gr[c]) last = c
    cfg.rows[r] = []
    for (let c = 0; c <= last; c++) cfg.rows[r][c] = gr[c] || mkCell()
  }
  // 被合并掉的占位格还原为隐藏格，并关联宿主 uid（与设计态合并标记一致）
  cfg.rows.forEach(row => row.forEach(c2 => {
    if (c2 && Array.isArray(c2.mfrom)) {
      const src = cfg.rows[c2.mfrom[0]]?.[c2.mfrom[1]]
      if (src && !src.uid) src.uid = uid()
      const hostUid = src && src.uid
      c2.span = 1
      c2.rspan = 1
      c2.t = ''
      c2.bind = ''
      c2.fmt = ''
      c2.bd = ''
      c2.fs = ''
      c2.ff = ''
      if (hostUid) { c2.hide = true; c2.mfrom = hostUid } else { c2.hide = false; c2.mfrom = null }
    }
  }))
  // 补合并标记的宿主引用与缺失 uid
  cfg.rows.forEach(row => row.forEach(c2 => { if (c2 && !c2.uid) c2.uid = uid() }))
  if (!cfg.colW.length) cfg.colW = Array(maxCols).fill(0)
  cfg.cellPad = table.getAttribute('data-cell-pad') || ''
  cfg.cellValign = table.getAttribute('data-cell-valign') || ''
  cfg.rowH = parseFloat(table.getAttribute('data-row-h')) || 8
  cfg.fontPt = parseFloat(table.getAttribute('data-font-pt')) || 12
  cfg.borderMode = table.getAttribute('data-border-mode') || 'all'
  cfg.borderWidth = parseFloat(table.getAttribute('data-border-width')) || 0.5
  cfg.borderColor = table.getAttribute('data-border-color') || '#303133'
  cfg.fontFamily = table.getAttribute('data-font-family') || styleFontFamily(table.getAttribute('style'))
  return cfg
}

// ============ 块 → HTML 序列化 ============
function buildPosStyle(b) {
  // 定位四项统一由块坐标生成：先剥掉样式里残留的旧定位声明，
  // 避免同一元素出现两套 left/top（CSS 后者生效，元素移动后保存会回到原位）
  const extra = String(b.style || '').split(';')
    .filter(p => p.trim() && !/^\s*(position|left|top|width|height|min-height)\s*:/.test(p))
    .join(';')
  return `position:absolute;left:${b.left}mm;top:${b.top}mm;width:${b.width}mm;` + (b.type === 'text' || b.type === 'qr' ? `min-height:${b.height}mm;` : `height:${b.height}mm;`) + extra
}

// 画布内不能直接用 CSS 毫米（1mm≈3.78px），必须换算成画布比例 3px/mm，否则表格会比纸面大 26%
function mmToCanvasPx(v) {
  return String(v || '').replace(/([\d.]+)\s*mm/g, (_, n) => (parseFloat(n) * MM_TO_PX) + 'px')
}

// 字段键名 → 中文名：画布只显示中文，保存的模板内容仍保留 {{键名}}，打印口径不变
const allFieldList = computed(() => [...fields.value.header, ...fields.value.items, ...fields.value.operations, ...fields.value.footer, ...fields.value.employee, ...fields.value.customer])
function fieldLabel(key) {
  const k = String(key || '').trim()
  const f = allFieldList.value.find(x => x.key === k)
  return f ? (f.label || k) : k
}
function canvasFieldSpan(key, fmt, bindLabel) {
  // 画布显示用户拖进来的那个字段名（随模板保存）；旧模板无此信息时按字段键反查中文名
  const label = String(bindLabel || '').trim()
    || (String(fmt || '').split(/[,，]/).map(x => x.trim()).includes('rmb') ? '金额大写' : fieldLabel(key))
  return `<span style="color:#1f6feb;">${esc(label)}</span>`
}
// 文本段里的 {{键名}} 在画布上同样显示中文名
function canvasTextHtml(html) {
  return String(html || '').replace(/\{\{\s*([^#/{}]+?)\s*\}\}/g, (m, k) => {
    const f = allFieldList.value.find(x => x.key === String(k).trim())
    return f ? `<span style="color:#1f6feb;">${esc(f.label || k)}</span>` : m
  })
}
// 表格外框：布局表格的边框全部由单元格四边决定（单格可单独开关）；数据表格保留整表外框
// 字号：画布按 3px/mm 换算（1pt≈0.3528mm → pt×1.058px），保存出去用真实 pt
function frameStyleOf(b) {
  if (!b || !b.cfg) return {}
  const fs = ((b.cfg.fontPt || 12) * 1.0584).toFixed(1) + 'px'
  const ff = b.cfg.fontFamily || undefined
  if (b.type !== 'grid' && (b.cfg.borderMode || 'all') === 'outer') {
    return { borderCollapse: 'collapse', tableLayout: 'fixed', border: `${b.cfg.borderWidth ?? 0.5}pt solid ${b.cfg.borderColor || '#303133'}`, fontSize: fs, fontFamily: ff }
  }
  return { borderCollapse: 'collapse', tableLayout: 'fixed', fontSize: fs, fontFamily: ff }
}

function cellBdArr(cell) {
  const s = String((cell && cell.bd) || '')
  return [s[0], s[1], s[2], s[3]].map(ch => (ch === '1' || ch === '0') ? ch : '')
}
/**
 * 计算布局表格每个可见单元格的四边最终状态：
 * ① 单格显式设置优先；② 单格设为“无边框”时，同时压掉相邻格共享的那条线，保证真无框。
 */
function gridCellSideMap(b) {
  const cfg = b.cfg
  const rows = cfg.rows || []
  const rowCount = rows.length
  const recs = []
  rows.forEach((row, r) => {
    let cAbs = 0
    row.forEach(cell => {
      if (cell.hide) return
      const span = Math.max(1, cell.span)
      const rspan = Math.max(1, cell.rspan)
      recs.push({ cell, r0: r, r1: Math.min(rowCount - 1, r + rspan - 1), c0: cAbs, c1: cAbs + span - 1 })
      cAbs += span
    })
  })
  const maxCols = recs.reduce((m, rec) => Math.max(m, rec.c1 + 1), 1)
  const mode = cfg.borderMode || 'all'
  const overrides = recs.map(rec => cellBdArr(rec.cell))
  const forcedOff = recs.map(() => ({}))
  const opposite = { t: 'b', r: 'l', b: 't', l: 'r' }
  recs.forEach((rec, i) => {
    overrides[i].forEach((v, idx) => {
      if (v !== '0') return
      const side = GRID_SIDES[idx][0]
      recs.forEach((other, j) => {
        if (i === j) return
        const rowsOverlap = Math.max(rec.r0, other.r0) <= Math.min(rec.r1, other.r1)
        const colsOverlap = Math.max(rec.c0, other.c0) <= Math.min(rec.c1, other.c1)
        if (side === 'r' && other.c0 === rec.c1 + 1 && rowsOverlap) forcedOff[j][opposite[side]] = true
        if (side === 'l' && other.c1 === rec.c0 - 1 && rowsOverlap) forcedOff[j][opposite[side]] = true
        if (side === 'b' && other.r0 === rec.r1 + 1 && colsOverlap) forcedOff[j][opposite[side]] = true
        if (side === 't' && other.r1 === rec.r0 - 1 && colsOverlap) forcedOff[j][opposite[side]] = true
      })
    })
  })
  const map = new Map()
  recs.forEach((rec, i) => {
    const sides = {}
    GRID_SIDES.forEach(([side], idx) => {
      if (forcedOff[i][side]) { sides[side] = false; return }
      const ov = overrides[i][idx]
      if (ov === '1') { sides[side] = true; return }
      if (ov === '0') { sides[side] = false; return }
      if (mode === 'all') { sides[side] = true; return }
      if (mode === 'outer') {
        sides[side] = (side === 't' && rec.r0 === 0) || (side === 'b' && rec.r1 === rowCount - 1)
          || (side === 'l' && rec.c0 === 0) || (side === 'r' && rec.c1 === maxCols - 1)
        return
      }
      sides[side] = false
    })
    map.set(rec.cell, sides)
  })
  return map
}

function gridHtml(b, forCanvas = true) {
  const cfg = b.cfg
  const sel = selectedCell.value
  const bw = cfg.borderWidth ?? 0.5
  const bc = cfg.borderColor || '#303133'
  const sideMap = gridCellSideMap(b)
  let cg = '<colgroup>'
  cfg.colW.forEach(w => {
    const ws = w > 0 ? (forCanvas ? `${w * MM_TO_PX}px` : `${w}mm`) : ''
    cg += `<col style="width:${ws}">`
  })
  cg += '</colgroup>'
  let body = ''
  cfg.rows.forEach((row, r) => {
    const rowH = (cfg.rowH || 8)
    body += `<tr style="height:${forCanvas ? (rowH * MM_TO_PX) + 'px' : rowH + 'mm'}">`
    row.forEach((cell, c) => {
      if (cell.hide) return
      const attrs = []
      if (cell.span > 1) attrs.push(`colspan="${cell.span}"`)
      if (cell.rspan > 1) attrs.push(`rowspan="${cell.rspan}"`)
      attrs.push(`data-r="${r}" data-c="${c}"`)
      if (cell.t) attrs.push(`data-text="${esc(cell.t)}"`)
      if (cell.bind) attrs.push(`data-bind="${esc(cell.bind)}"`)
      if (cell.fmt) attrs.push(`data-fmt="${esc(cell.fmt)}"`)
      if (cell.bind && cell.label) attrs.push(`data-label="${esc(cell.label)}"`)
      if (cell.pad) attrs.push(`data-cell-pad="${esc(cell.pad)}"`)
      if (cell.bd) attrs.push(`data-bd="${esc(cell.bd)}"`)
      if (cell.fs) attrs.push(`data-fs="${cell.fs}"`)
      if (cell.ff) attrs.push(`data-ff="${esc(cell.ff)}"`)
      const st = []
      const sides = sideMap.get(cell) || {}
      GRID_SIDES.forEach(([side, cssSide]) => {
        if (sides[side]) st.push(`border-${cssSide}:${bw}pt solid ${bc}`)
      })
      const hasAnyBorder = GRID_SIDES.some(([side]) => sides[side])
      if (!hasAnyBorder && forCanvas) st.push('outline:1px dashed #e4e7ed')
      const pad = cell.pad || cfg.cellPad
      if (pad) st.push('padding:' + (forCanvas ? mmToCanvasPx(pad) : pad))
      if (cfg.cellValign) st.push('vertical-align:' + cfg.cellValign)
      if (cell.align) st.push('text-align:' + cell.align)
      if (cell.bold) st.push('font-weight:bold')
      // 本格字号/字体：覆盖整表设置，画布按 1pt≈1.058px 换算
      if (cell.fs) st.push('font-size:' + (forCanvas ? (cell.fs * 1.0584).toFixed(1) + 'px' : cell.fs + 'pt'))
      if (cell.ff) st.push('font-family:' + cell.ff)
      if (st.length) attrs.push(`style="${st.join(';')}"`)
      const cls = sel && sel.blockId === b.id && sel.r === r && sel.c === c ? ' class="pt-csel"' : ''
      const fieldHtml = cell.bind ? (forCanvas ? canvasFieldSpan(cell.bind, cell.fmt, cell.label) : '{{' + cell.bind + '}}') : ''
      // 文字与系统字段同格共存：文字在前、字段在后，拖动字段不会再吃掉已输入的文字
      const content = escCellText(cell.t) + fieldHtml
      body += `<td ${attrs.join(' ')}${cls}>${content}</td>`
    })
    body += '</tr>'
  })
  return cg + '<tbody>' + body + '</tbody>'
}

function dataTableHtml(b) {
  const cfg = b.cfg
  const sel = selectedCell.value
  const mode = cfg.borderMode || 'all'
  const bw = cfg.borderWidth ?? 0.5
  const bc = cfg.borderColor || '#303133'
  const guide = mode === 'none' ? 'outline:1px dashed #e4e7ed;' : ''
  let cg = '<colgroup>'
  // 画布按 3px/mm 换算（不能用 CSS 毫米，否则表格比纸面宽 26%）
  cfg.cols.forEach(col => { cg += `<col${col.w > 0 ? ` style="width:${col.w * MM_TO_PX}px"` : ''}>` })
  cg += '</colgroup>'
  let head = '<thead><tr>'
  const bd = mode === 'all' ? `border:${bw}pt solid ${bc};` : guide
  const thPad = cfg.thPad || cfg.cellPad
  const thExtra = (cfg.thBg ? `background:${cfg.thBg};` : '') + (thPad ? `padding:${mmToCanvasPx(thPad)};` : '') + (cfg.cellValign ? `vertical-align:${cfg.cellValign};` : '')
  const canResize = selectedId.value === b.id
  cfg.cols.forEach((col, ci) => {
    const selCls = sel && sel.blockId === b.id && sel.r === -1 && sel.c === ci ? ' class="pt-csel"' : ''
    // 列宽拖拽手柄：放在本列右边界，最后一列之外没有可交换的相邻列，故不加
    // v-html 内容拿不到 scoped 样式标记，尺寸与定位必须写成内联样式
    const rzStyle = 'position:absolute;top:0;right:0;width:7px;height:100%;cursor:col-resize;z-index:3;'
    const rz = canResize && ci < cfg.cols.length - 1 ? `<span class="pt-dt-resizer" data-col="${ci}" style="${rzStyle}"></span>` : ''
    head += `<th data-r="-1" data-c="${ci}"${selCls} style="${bd}${thExtra}text-align:center;height:${7 * MM_TO_PX}px;position:relative;">${escCellText(col.label)}${rz}</th>`
  })
  head += '</tr></thead>'
  let body = '<tbody>'
  body += `<tr data-role="detail"${cfg.rowAuto ? '' : ` style="height:${(cfg.rowHeight || 7) * MM_TO_PX}px"`}>`
  cfg.cols.forEach((col, ci) => {
    const selCls = sel && sel.blockId === b.id && sel.r === 0 && sel.c === ci ? ' class="pt-csel"' : ''
    const attrs = [`data-r="0"`, `data-c="${ci}"`]
    if (col.bind) attrs.push(`data-bind="${esc(col.bind)}"`)
    if (col.fmt) attrs.push(`data-fmt="${esc(col.fmt)}"`)
    const st = (col.align ? `text-align:${col.align};` : '') + bd
      + (cfg.cellPad ? `padding:${mmToCanvasPx(cfg.cellPad)};` : '') + (cfg.cellValign ? `vertical-align:${cfg.cellValign};` : '')
    if (st) attrs.push(`style="${st}"`)
    const content = col.bind ? canvasFieldSpan(col.bind, col.fmt, col.label) : '<span style="color:#e04141;">未绑定</span>'
    body += `<td ${attrs.join(' ')}${selCls}>${content}</td>`
  })
  body += '</tr>'
  const bodyPad = cfg.cellPad ? `padding:${mmToCanvasPx(cfg.cellPad)};` : ''
  if (cfg.totalOn) {
    body += '<tr class="total-preview">'
    const firstTotal = cfg.totalCols.length ? Math.min(...cfg.totalCols) : -1
    if (firstTotal <= 0) {
      cfg.cols.forEach((col, ci) => {
        const on = cfg.totalCols.includes(ci)
        const txt = on ? (ci === 0 ? '合计：Σ' : 'Σ') : ''
        body += `<td style="${bd}${bodyPad}">${txt}</td>`
      })
    } else {
      body += `<td colspan="${firstTotal}" style="${bd}${bodyPad}">合计</td>`
      cfg.cols.slice(firstTotal).forEach((col, i) => {
        const on = cfg.totalCols.includes(firstTotal + i)
        body += `<td style="${bd}${bodyPad}">${on ? 'Σ' : ''}</td>`
      })
    }
    body += '</tr>'
  }
  if (cfg.totalOn && cfg.totalRmb && cfg.totalCols.length) {
    body += `<tr class="total-preview"><td colspan="${cfg.cols.length}" style="${bd}${bodyPad}">金额大写：人民币<span style="color:#909399;">壹万贰仟叁佰元整</span></td></tr>`
  }
  body += '</tbody>'
  return cg + head + body
}

/** 工序表画布预览：三种版式（rows/detail/custom）的样例行，与打印渲染同结构 */
function opTableHtml (b) {
  const cfg = b.cfg || {}
  const mode = cfg.mode || 'rows'
  const cols = cfg.cols || []
  const bd = (cfg.borderMode || 'all') === 'all'
    ? `border:${cfg.borderWidth ?? 0.5}pt solid ${cfg.borderColor || '#303133'};`
    : 'outline:1px dashed #e4e7ed;'
  const pad = cfg.cellPad ? `padding:${mmToCanvasPx(cfg.cellPad)};` : ''
  const th = (label) => `<th style="${bd}${pad}text-align:center;height:${7 * MM_TO_PX}px;background:#fafafa;">${escCellText(label)}</th>`
  const td = (content, rowspan = 1) => `<td${rowspan > 1 ? ` rowspan="${rowspan}"` : ''} style="${bd}${pad}">${content}</td>`
  const shown = mode === 'custom'
    ? [cols[0] || { label: '工序名称' }, { label: '自定义内容' }]
    : cols
  let cg = '<colgroup>'
  shown.forEach(col => { cg += `<col${col.w > 0 ? ` style="width:${col.w * MM_TO_PX}px"` : ''}>` })
  cg += '</colgroup>'
  let head = '<thead><tr>' + shown.map(col => th(col.label || '')).join('') + '</tr></thead>'
  let body = '<tbody>'
  if (mode === 'custom') {
    const lines = cols.filter(c => c.bind && c.bind !== 'operationName')
    const lineCount = Math.max(1, lines.length)
    for (let i = 0; i < lineCount; i++) {
      body += '<tr>'
      if (i === 0) body += td(canvasFieldSpan('operationName', '', '工序名称'), lineCount)
      const c = lines[i]
      body += td(c ? `${escCellText(c.label)}：${canvasFieldSpan(c.bind, c.fmt, '')}` : '')
      body += '</tr>'
    }
  } else {
    const itemCols = cols.filter(c => c.src === 'item')
    const itemCount = (mode === 'detail' && itemCols.length) ? 2 : 1
    for (let o = 0; o < 3; o++) {
      for (let i = 0; i < itemCount; i++) {
        body += '<tr>'
        cols.forEach(c => {
          if (c.src === 'item') {
            body += td(canvasFieldSpan(c.bind, c.fmt, c.label))
          } else if (i === 0) {
            body += td(canvasFieldSpan(c.bind, c.fmt, c.label), itemCount)
          }
        })
        body += '</tr>'
      }
    }
  }
  body += '</tbody>'
  return cg + head + body
}

function blocksToHtml() {
  return blocks.value.map(b => {
    const attrs = [`data-block-id="${b.id}"`, `data-block-type="${b.type === 'dataTable' ? 'dataTable' : b.type}"`, `style="${esc(buildPosStyle(b))}"`]
    if (b.fmt) attrs.push(`data-fmt="${esc(b.fmt)}"`)
    if (b.type === 'image') {
      return `<img ${attrs.join(' ')} src="${esc(b.cfg?.src || '')}" alt="" />`
    }
    if (b.type === 'grid') {
      const gridFontPt = b.cfg.fontPt || 12
      // 布局表格不再由表格自身描边：外框、内线、单格边框全部由单元格四边统一输出
      let frame = 'border-collapse:collapse;table-layout:fixed;'
      frame += `font-size:${gridFontPt}pt;`
      if (b.cfg.fontFamily) frame += `font-family:${b.cfg.fontFamily};`
      const g = [`data-block-id="${b.id}"`, `data-block-type="grid"`, `data-font-pt="${gridFontPt}"`, `style="${esc(stripBorderProps(buildPosStyle(b)) + ';' + frame)}"`]
      g.push(`data-row-h="${b.cfg.rowH || 8}"`)
      g.push(`data-border-mode="${b.cfg.borderMode || 'all'}"`)
      g.push(`data-border-width="${b.cfg.borderWidth ?? 0.5}"`)
      g.push(`data-border-color="${esc(b.cfg.borderColor || '#303133')}"`)
      if (b.cfg.fontFamily) g.push(`data-font-family="${esc(b.cfg.fontFamily)}"`)
      if (b.cfg.cellPad) g.push(`data-cell-pad="${esc(b.cfg.cellPad)}"`)
      if (b.cfg.cellValign) g.push(`data-cell-valign="${esc(b.cfg.cellValign)}"`)
      return `<table ${g.join(' ')}>${gridHtml(b, false)}</table>`
    }
    if (b.type === 'dataTable') {
      return serializeDataTable(b)
    }
    if (b.type === 'opTable') {
      return serializeOpTable(b)
    }
    return `<div ${attrs.join(' ')}>${b.html}</div>`
  }).join('\n')
}

function serializeDataTable(b) {
  const cfg = b.cfg
  const fontPt = cfg.fontPt || 12
  let frame = (cfg.borderMode || 'all') === 'outer' ? `border:${cfg.borderWidth ?? 0.5}pt solid ${cfg.borderColor || '#303133'};border-collapse:collapse;table-layout:fixed;` : 'border-collapse:collapse;table-layout:fixed;'
  frame += `font-size:${fontPt}pt;`
  if (cfg.fontFamily) frame += `font-family:${cfg.fontFamily};`
  const attrs = [`data-block-id="${b.id}"`, `data-block-type="dataTable"`, `data-table-kind="data"`, `data-font-pt="${fontPt}"`, `style="${esc(stripBorderProps(buildPosStyle(b)) + ';' + frame)}"`]
  attrs.push(`data-border-mode="${cfg.borderMode || 'all'}"`)
  attrs.push(`data-border-width="${cfg.borderWidth ?? 0.5}"`)
  attrs.push(`data-border-color="${esc(cfg.borderColor || '#303133')}"`)
  if (cfg.fontFamily) attrs.push(`data-font-family="${esc(cfg.fontFamily)}"`)
  attrs.push(`data-rows="${cfg.rowMode === 'fixed' ? 'fixed:' + (cfg.fixedRows || 10) : 'auto'}"`)
  if (!cfg.rowAuto) attrs.push(`data-row-height="${cfg.rowHeight}"`)
  if (cfg.thBg) attrs.push(`data-th-bg="${esc(cfg.thBg)}"`)
  if (cfg.thPad) attrs.push(`data-th-pad="${esc(cfg.thPad)}"`)
  if (cfg.cellPad) attrs.push(`data-cell-pad="${esc(cfg.cellPad)}"`)
  if (cfg.cellValign) attrs.push(`data-cell-valign="${esc(cfg.cellValign)}"`)
  if (cfg.totalOn && cfg.totalCols.length) {
    attrs.push(`data-total="${cfg.totalCols.map(ci => cfg.cols[ci]?.bind).filter(Boolean).join(',')}"`)
    const capBind = cfg.cols[cfg.totalCols[0]]?.bind
    if (cfg.totalRmb && capBind) attrs.push(`data-total-rmb="${esc(capBind)}"`)
  }
  if (cfg.groupOn && cfg.groupCol != null && cfg.cols[cfg.groupCol]?.bind) {
    attrs.push(`data-group="${esc(cfg.cols[cfg.groupCol].bind)}"`)
    attrs.push(`data-group-label="${esc(cfg.cols[cfg.groupCol].label || '')}"`)
    if (cfg.groupTotalOn && cfg.totalCols.length) {
      attrs.push(`data-group-total="${cfg.totalCols.map(ci => cfg.cols[ci]?.bind).filter(Boolean).join(',')}"`)
    }
  }
  let cg = '<colgroup>'
  cfg.cols.forEach(col => { cg += `<col${col.w > 0 ? ` style="width:${col.w}mm"` : ''}>` })
  cg += '</colgroup>'
  let head = '<thead><tr>'
  const bd = cfg.borderMode === 'all' ? `border:${cfg.borderWidth ?? 0.5}pt solid ${cfg.borderColor || '#303133'};` : ''
  const thExtra = (cfg.thBg ? `background:${cfg.thBg};` : '') + ((cfg.thPad || cfg.cellPad) ? `padding:${cfg.thPad || cfg.cellPad};` : '') + (cfg.cellValign ? `vertical-align:${cfg.cellValign};` : '')
  cfg.cols.forEach(col => {
    const st = [`height:7mm;text-align:center;`]
    if (bd) st.push(bd)
    if (thExtra) st.push(thExtra)
    if (sp2thBold(b)) st.push('font-weight:bold;')
    head += `<th style="${st.join('')}">${escCellText(col.label)}</th>`
  })
  head += '</tr></thead>'
  const hstyle = cfg.rowAuto ? '' : ` style="height:${cfg.rowHeight}mm"`
  let det = `<tr data-role="detail"${hstyle}>`
  cfg.cols.forEach(col => {
    const a = []
    if (col.bind) a.push(`data-bind="${esc(col.bind)}"`)
    if (col.fmt) a.push(`data-fmt="${esc(col.fmt)}"`)
    const st = (col.align ? `text-align:${col.align};` : '') + bd
      + (cfg.cellPad ? `padding:${cfg.cellPad};` : '') + (cfg.cellValign ? `vertical-align:${cfg.cellValign};` : '')
    if (st) a.push(`style="${st}"`)
    det += `<td ${a.join(' ')}>{{${col.bind || ''}}}</td>`
  })
  det += '</tr>'
  const body = `<tbody>{{#items}}${det}{{/items}}</tbody>`
  return `<table ${attrs.join(' ')}>${cg}${head}${body}</table>`
}
/** 工序表序列化：三种版式统一写进 table 的 data-* 属性，后端 renderOperationTable 按属性渲染 */
function serializeOpTable (b) {
  const cfg = b.cfg || {}
  const mode = cfg.mode || 'rows'
  const cols = cfg.cols || []
  const fontPt = cfg.fontPt || 9
  let frame = (cfg.borderMode || 'all') === 'outer'
    ? `border:${cfg.borderWidth ?? 0.5}pt solid ${cfg.borderColor || '#303133'};`
    : ''
  frame += 'border-collapse:collapse;table-layout:fixed;'
  frame += `font-size:${fontPt}pt;`
  if (cfg.fontFamily) frame += `font-family:${cfg.fontFamily};`
  const attrs = [
    `data-block-id="${b.id}"`,
    'data-block-type="opTable"',
    'data-table-kind="operation"',
    `data-op-mode="${mode}"`,
    `data-font-pt="${fontPt}"`,
    `style="${esc(stripBorderProps(buildPosStyle(b)) + ';' + frame)}"`
  ]
  attrs.push(`data-border-mode="${cfg.borderMode || 'all'}"`)
  attrs.push(`data-border-width="${cfg.borderWidth ?? 0.5}"`)
  attrs.push(`data-border-color="${esc(cfg.borderColor || '#303133')}"`)
  if (cfg.fontFamily) attrs.push(`data-font-family="${esc(cfg.fontFamily)}"`)
  if (!cfg.rowAuto) attrs.push(`data-row-height="${cfg.rowHeight || 8}"`)
  if (cfg.thPad) attrs.push(`data-th-pad="${esc(cfg.thPad)}"`)
  if (cfg.cellPad) attrs.push(`data-cell-pad="${esc(cfg.cellPad)}"`)
  if (mode === 'detail') attrs.push('data-op-merge="1"')
  if (mode === 'custom') {
    const lines = cols
      .filter(c => c.bind && c.bind !== 'operationName')
      .map(c => `${c.bind}|${c.label || c.bind}`)
      .join(',')
    attrs.push(`data-op-lines="${esc(lines)}"`)
  }
  const bd = (cfg.borderMode || 'all') === 'all'
    ? `border:${cfg.borderWidth ?? 0.5}pt solid ${cfg.borderColor || '#303133'};`
    : ''
  const thExtra = (cfg.thPad || cfg.cellPad) ? `padding:${cfg.thPad || cfg.cellPad};` : ''
  const shown = mode === 'custom'
    ? [cols[0] || { label: '工序名称' }, { label: '自定义内容' }]
    : cols
  let cg = '<colgroup>'
  shown.forEach(c => { cg += `<col${c.w > 0 ? ` style="width:${c.w}mm"` : ''}>` })
  cg += '</colgroup>'
  let head = '<thead><tr>'
  shown.forEach(c => {
    head += `<th style="height:7mm;text-align:center;${bd}${thExtra}font-weight:bold;">${escCellText(c.label)}</th>`
  })
  head += '</tr></thead>'
  const hstyle = cfg.rowAuto ? '' : ` style="height:${cfg.rowHeight || 8}mm"`
  let det = `<tr data-role="detail"${hstyle}>`
  if (mode === 'custom') {
    det += `<td data-bind="operationName" data-col-src="operation" data-op-fixed="name" style="${bd}${thExtra}">{{operationName}}</td>`
    det += `<td data-op-lines-cell="1" style="${bd}${thExtra}"></td>`
  } else {
    cols.forEach(c => {
      const a = []
      if (c.bind) a.push(`data-bind="${esc(c.bind)}"`)
      if (c.fmt) a.push(`data-fmt="${esc(c.fmt)}"`)
      a.push(`data-col-src="${c.src}"`)
      if (c.bind === 'operationName') a.push('data-op-fixed="name"')
      const st = (c.align ? `text-align:${c.align};` : '') + bd + (cfg.cellPad ? `padding:${cfg.cellPad};` : '')
      if (st) a.push(`style="${st}"`)
      det += `<td ${a.join(' ')}>{{${c.bind || ''}}}</td>`
    })
  }
  det += '</tr>'
  return `<table ${attrs.join(' ')}>${cg}${head}<tbody>${det}</tbody></table>`
}

function sp2thBold(b) {
  const cs = parseStyle(b.style)
  return cs['--th-bold'] !== '0'
}

function syncToSource() {
  if (mode.value === 'design') sourceHtml.value = blocksToHtml()
}

/**
 * 表格块尺寸贴合内容：表格高度由行数、行高、内边距、字号共同决定，
 * 这里按实际渲染高度回写块高度，保证选中框、手柄与打印定位都以真实表格尺寸为准。
 */
function fitTableBlocks() {
  const paperEl = canvasWrapRef.value?.querySelector('.pt-paper')
  if (!paperEl) return
  let changed = false
  for (const b of blocks.value) {
    if (!isTableBlock(b.type)) continue
    const el = paperEl.querySelector(`[data-id="${b.id}"] table`)
    if (!el) continue
    const hMM = Math.max(6, Math.round(el.offsetHeight / MM_TO_PX))
    if (Math.abs((b.height || 0) - hMM) > 0.5) {
      b.height = hMM
      if (selectedId.value === b.id) sp.height = hMM
      changed = true
    }
  }
  if (changed) syncToSource()
}
watch(() => blocks.value, () => { nextTick(() => fitTableBlocks()) }, { deep: true })

// ============ 选中 ============
function selectBlock(id) {
  selectedId.value = id
  selectedCell.value = null
  if (id) loadPropsFromBlock(blocks.value.find(x => x.id === id))
}
function onBlockClick(b, e) {
  selectBlock(b.id)
}
function loadPropsFromBlock(b) {
  if (!b) return
  const cs = parseStyle(b.style)
  sp.fontSize = parseFloat(cs['font-size']) || 12
  sp.fontFamily = cs['font-family'] || ''
  sp.fontWeight = cs['font-weight'] === 'bold' ? 'bold' : 'normal'
  sp.textAlign = cs['text-align'] || 'left'
  sp.color = cs.color || '#303133'
  sp.keepRatio = b.type === 'image' ? (b.cfg?.keepRatio ?? true) : true
  const bw = parseFloat(cs['border-top-width']) || parseFloat(cs.border) || 0.5
  sp.borderWidth = bw
  sp.borderColor = cs['border-top-color'] || (cs.border ? (cs.border.split(' ').pop() || '#303133') : '#303133')
  sp.left = b.left; sp.top = b.top; sp.width = b.width; sp.height = b.height
  if (b.type === 'grid') {
    gridRowHeight.value = b.cfg.rowH || 8
    gridBorderMode.value = borderModeOf(b)
    sp.borderWidth = b.cfg.borderWidth ?? 0.5
    sp.borderColor = b.cfg.borderColor || '#303133'
    gridPadMM.value = mmOfPad(b.cfg.cellPad)
    gridCellPadMM.value = gridPadMM.value
    gridFontPt.value = b.cfg.fontPt || 12
    gridFontFamily.value = b.cfg.fontFamily || ''
  }
  if (b.type === 'dataTable' && b.cfg) {
    Object.assign(dc, {
      cols: b.cfg.cols, rowMode: b.cfg.rowMode, fixedRows: b.cfg.fixedRows, rowHeight: b.cfg.rowHeight,
      rowAuto: b.cfg.rowAuto, totalOn: b.cfg.totalOn, totalCols: [...b.cfg.totalCols], totalRmb: !!b.cfg.totalRmb,
      groupOn: b.cfg.groupOn, groupCol: b.cfg.groupCol, groupTotalOn: b.cfg.groupTotalOn,
      borderMode: b.cfg.borderMode || 'all', borderWidth: b.cfg.borderWidth ?? 0.5, borderColor: b.cfg.borderColor || '#303133',
      thPad: b.cfg.thPad || '', cellPad: b.cfg.cellPad || '', fontPt: b.cfg.fontPt || 12, fontFamily: b.cfg.fontFamily || ''
    })
    dtThPadMM.value = mmOfPad(b.cfg.thPad || b.cfg.cellPad)
    dtBodyPadMM.value = mmOfPad(b.cfg.cellPad)
  }
}
function touchDc() {
  const b = selectedBlock.value
  if (!b || b.type !== 'dataTable') return
  const fs = parseFloat(dc.fontPt)
  dc.fontPt = (isFinite(fs) && fs > 0) ? fs : 12
  const bw = parseFloat(dc.borderWidth)
  dc.borderWidth = (isFinite(bw) && bw > 0) ? bw : 0.5
  Object.assign(b.cfg, {
    cols: dc.cols, rowMode: dc.rowMode, fixedRows: dc.fixedRows, rowHeight: dc.rowHeight, rowAuto: dc.rowAuto,
    totalOn: dc.totalOn, totalCols: [...dc.totalCols], totalRmb: dc.totalRmb,
    groupOn: dc.groupOn, groupCol: dc.groupCol, groupTotalOn: dc.groupTotalOn,
    borderMode: dc.borderMode, borderWidth: dc.borderWidth, borderColor: dc.borderColor,
    thPad: dc.thPad, cellPad: dc.cellPad, fontPt: dc.fontPt, fontFamily: dc.fontFamily || ''
  })
  if (!b.cfg.totalCols.length) b.cfg.totalRmb = false
  syncToSource()
}
function setDcBorder(m) {
  const b = selectedBlock.value
  if (!b || b.type !== 'dataTable') return
  pushHistory()
  dc.borderMode = m
  const bw = parseFloat(dc.borderWidth)
  dc.borderWidth = (isFinite(bw) && bw > 0) ? bw : 0.5
  const cs = parseStyle(b.style)
  if (m === 'none') delete cs.border
  else cs.border = `${dc.borderWidth}pt solid ${dc.borderColor}`
  b.style = styleToStr(cs)
  touchDc()
}
function borderModeOf(b) {
  return (b && b.cfg && b.cfg.borderMode) || 'all'
}

// ============ 单元格点击 / 编辑 ============
function locateCell(b, e) {
  const td = e.target.closest('td,th')
  if (!td) return null
  const r = parseInt(td.getAttribute('data-r'), 10)
  const c = parseInt(td.getAttribute('data-c'), 10)
  if (isNaN(r) || isNaN(c)) return null
  return { blockId: b.id, r, c, td }
}
function onCellClick(b, e) {
  // 拖完列宽后浏览器仍会补一次 click，落在手柄上时只选中表格，不进单元格编辑
  if (e.target && e.target.classList && e.target.classList.contains('pt-dt-resizer')) { selectBlock(b.id); return }
  selectBlock(b.id)
  const loc = locateCell(b, e)
  if (!loc) return
  selectedCell.value = { blockId: loc.blockId, r: loc.r, c: loc.c }
  syncCellPadRefs(b, loc)
  startCellEdit(b, loc, loc.td)
}
function syncCellPadRefs(b, loc) {
  if (!b || !loc) return
  if (b.type === 'grid') {
    const cell = b.cfg.rows[loc.r]?.[loc.c]
    gridCellPadMM.value = cell && cell.pad ? mmVal(cell.pad) : gridPadMM.value
    cellFontPt.value = (cell && cell.fs) ? cell.fs : ''
    cellFontFamily.value = (cell && cell.ff) ? cell.ff : ''
  }
}

// ============ 布局表格：单格四边边框 ============
function selGridCell() {
  const b = selectedBlock.value
  const loc = selectedCell.value
  if (!b || b.type !== 'grid' || !loc || loc.blockId !== b.id) return null
  const cell = b.cfg.rows[loc.r]?.[loc.c]
  return cell && !cell.hide ? cell : null
}
const cellBorderAll = computed(() => {
  const cell = selGridCell()
  if (!cell) return ''
  const arr = cellBdArr(cell)
  if (arr.every(v => !v)) return 'follow'
  if (arr.every(v => v === '1')) return 'on'
  if (arr.every(v => v === '0')) return 'off'
  return 'custom'
})
function cellSide(side) {
  const cell = selGridCell()
  if (!cell) return ''
  const idx = { t: 0, r: 1, b: 2, l: 3 }[side]
  return cellBdArr(cell)[idx] || ''
}
function writeCellBd(cell, arr) {
  cell.bd = arr.every(v => !v) ? '' : arr.map(v => v || '-').join('')
}
function setCellBorderAll(v) {
  const cell = selGridCell()
  if (!cell) return
  pushHistory()
  cell.bd = v === null ? '' : (v === 1 ? '1111' : '0000')
  syncToSource()
}
function setCellSide(side, v) {
  const cell = selGridCell()
  if (!cell) return
  const idx = { t: 0, r: 1, b: 2, l: 3 }[side]
  if (idx == null) return
  pushHistory()
  const arr = cellBdArr(cell)
  arr[idx] = v
  writeCellBd(cell, arr)
  syncToSource()
}
// 本格字号 / 字体：清空 = 跟随整表；从编辑工具条修改时把焦点还给输入框，保持继续输入
function setCellFontSize(v) {
  const cell = selGridCell()
  if (!cell) return
  pushHistory()
  const fs = parseFloat(v)
  cell.fs = (isFinite(fs) && fs > 0) ? fs : ''
  cellFontPt.value = cell.fs || ''
  syncToSource()
  nextTick(() => { if (cellEditorRef.value) cellEditorRef.value.focus() })
}
function setCellFontFamily(v) {
  const cell = selGridCell()
  if (!cell) return
  pushHistory()
  cell.ff = v || ''
  cellFontFamily.value = cell.ff
  syncToSource()
  nextTick(() => { if (cellEditorRef.value) cellEditorRef.value.focus() })
}
function startCellEdit(b, loc, td) {
  if (!td) return
  if (cellEdit.value && cellEdit.value.blockId === b.id && cellEdit.value.r === loc.r && cellEdit.value.c === loc.c) return
  if (cellEdit.value) commitCellEdit()
  const paperEl = canvasWrapRef.value?.querySelector('.pt-paper')
  if (!paperEl) return
  const s = displayScale.value || 1
  const pr = paperEl.getBoundingClientRect()
  const tr = td.getBoundingClientRect()
  const rect = {
    left: (tr.left - pr.left) / s,
    top: (tr.top - pr.top) / s,
    width: tr.width / s,
    height: tr.height / s
  }
  if (b.type === 'grid') {
    const cell = b.cfg.rows[loc.r]?.[loc.c]
    if (!cell || cell.hide) return
    // 已绑定系统字段的单元格同样可编辑文字：文字与字段同格共存，不再要求先清除绑定
    openCellEdit(b, loc, cell.t || '', 'gridText', rect)
  } else if (b.type === 'dataTable') {
    if (loc.r !== -1) return // 明细行是打印数据行，只有表头列名可改
    const col = b.cfg.cols[loc.c]
    if (!col) return
    openCellEdit(b, loc, col.label || '', 'dtLabel', rect)
  }
}
function openCellEdit(b, loc, value, kind, rect) {
  pushHistory()
  cellEdit.value = { blockId: b.id, r: loc.r, c: loc.c, kind, value, orig: value, rect: rect || null }
  nextTick(() => {
    const el = cellEditorRef.value
    if (el) { el.focus(); el.select() }
  })
}
function cancelCellEdit() {
  if (!cellEdit.value) return
  history.value.pop()
  cellEdit.value = null
}
function commitCellEdit() {
  const ed = cellEdit.value
  if (!ed) return
  if (ed.value === ed.orig) {
    history.value.pop() // 没改动就不留撤销步
    cellEdit.value = null
    return
  }
  const b = blocks.value.find(x => x.id === ed.blockId)
  if (b) {
    if (ed.kind === 'gridText') {
      const cell = b.cfg.rows[ed.r]?.[ed.c]
      if (cell) cell.t = ed.value
    } else if (ed.kind === 'dtLabel') {
      const col = b.cfg.cols[ed.c]
      if (col) col.label = ed.value
    } else if (ed.kind === 'textBlock') {
      b.html = esc(ed.value).replace(/\n/g, '<br>')
    }
    syncToSource()
  }
  cellEdit.value = null
}
function editTextBlock(b) {
  selectBlock(b.id)
  const div = document.createElement('div')
  div.innerHTML = b.html
  openCellEdit(b, { r: 0, c: 0 }, div.textContent, 'textBlock')
}

// ============ 字段拖入 / 插入 ============
function onChipDragStart(e, f) {
  if (cellEdit.value) commitCellEdit()
  e.dataTransfer.setData('field', JSON.stringify(f))
}
function onCanvasDrop(e) {
  const bf = e.dataTransfer.getData('field')
  const rect = canvasWrapRef.value.querySelector('.pt-paper').getBoundingClientRect()
  const k = MM_TO_PX * displayScale.value
  const x = Math.max(0, (e.clientX - rect.left) / k)
  const y = Math.max(0, (e.clientY - rect.top) / k)
  if (bf) {
    const f = JSON.parse(bf)
    dropFieldToCell(e, f, x, y)
  }
}
function dropFieldToCell(e, f, x, y) {
  const blockEl = e.target.closest('.pt-block')
  const td = e.target.closest('td,th')
  if (!blockEl || !td) {
    ElMessage.warning('请先在纸面选中一个单元格，再把字段拖进去')
    return
  }
  const id = blockEl.getAttribute('data-id')
  const b = blocks.value.find(x2 => x2.id === id)
  if (!b) return
  let r = parseInt(td.getAttribute('data-r'), 10)
  const c = parseInt(td.getAttribute('data-c'), 10)
  if (isNaN(r) || isNaN(c)) return
  pushHistory()
  if (b.type === 'grid') {
    const cell = b.cfg.rows[r]?.[c]
    if (cell && !cell.hide) {
      cell.bind = f.key
      cell.fmt = f.fmt || defaultFmtOf(f.key)
      cell.label = f.label || ''
    }
  } else if (b.type === 'dataTable') {
    if (r === -1) {
      b.cfg.cols[c].label = f.label
    } else {
      b.cfg.cols[c].bind = f.key
      b.cfg.cols[c].fmt = f.fmt || defaultFmtOf(f.key)
    }
  } else {
    ElMessage.warning('字段只能放进表格单元格')
    return
  }
  selectedId.value = b.id
  selectedCell.value = { blockId: b.id, r, c }
  if (b.type === 'dataTable') loadPropsFromBlock(b)
  syncCellPadRefs(b, { r, c })
  syncToSource()
}
function insertFieldToCell(f) {
  const b = selectedBlock.value
  const loc = selectedCell.value
  if (!b || !loc || loc.blockId !== b.id) {
    ElMessage.warning('请先在纸面选中一个单元格，再点字段插入')
    return
  }
  pushHistory()
  if (b.type === 'grid') {
    const cell = b.cfg.rows[loc.r]?.[loc.c]
    if (cell && !cell.hide) {
      cell.bind = f.key
      cell.fmt = f.fmt || defaultFmtOf(f.key)
      cell.label = f.label || ''
    }
  } else if (b.type === 'dataTable') {
    if (loc.r === -1) b.cfg.cols[loc.c].label = f.label
    else { b.cfg.cols[loc.c].bind = f.key; b.cfg.cols[loc.c].fmt = f.fmt || defaultFmtOf(f.key) }
  } else {
    ElMessage.warning('字段只能放进表格单元格')
    return
  }
  syncToSource()
}
function clearCellBind() {
  const b = selectedBlock.value
  const loc = selectedCell.value
  if (!b || !loc || loc.blockId !== b.id) return
  pushHistory()
  if (b.type === 'grid') {
    const cell = b.cfg.rows[loc.r]?.[loc.c]
    if (cell) { cell.bind = ''; cell.fmt = ''; cell.label = '' }
  } else if (b.type === 'dataTable') {
    if (loc.r === -1) b.cfg.cols[loc.c].label = ''
    else { b.cfg.cols[loc.c].bind = ''; b.cfg.cols[loc.c].fmt = '' }
  }
  syncToSource()
}
// Delete 键：清空当前单元格的内容与绑定（保留单元格本身与样式）
function clearSelectedCellContent() {
  const b = selectedBlock.value
  const loc = selectedCell.value
  if (!b || !loc || loc.blockId !== b.id) return
  pushHistory()
  if (b.type === 'grid') {
    const cell = b.cfg.rows[loc.r]?.[loc.c]
    if (cell && !cell.hide) { cell.t = ''; cell.bind = ''; cell.fmt = ''; cell.label = '' }
  } else if (b.type === 'dataTable') {
    const col = b.cfg.cols[loc.c]
    if (col) {
      if (loc.r === -1) col.label = ''
      else { col.bind = ''; col.fmt = '' }
    }
    loadPropsFromBlock(b)
  }
  syncToSource()
}
function setCellAlign(a) {
  const b = selectedBlock.value
  const loc = selectedCell.value
  if (!b || !loc) return
  pushHistory()
  if (b.type === 'grid') {
    const cell = b.cfg.rows[loc.r]?.[loc.c]
    if (cell) cell.align = a
  } else if (b.type === 'dataTable' && loc.r === 0) {
    b.cfg.cols[loc.c].align = a
  }
  syncToSource()
}
const cellAlign = computed(() => {
  const b = selectedBlock.value
  const loc = selectedCell.value
  if (!b || !loc || loc.blockId !== b.id) return ''
  if (b.type === 'grid') return b.cfg.rows[loc.r]?.[loc.c]?.align || ''
  if (b.type === 'dataTable' && loc.r === 0) return b.cfg.cols[loc.c]?.align || ''
  return ''
})
function defaultFmtOf(key) {
  const k = String(key).toLowerCase()
  if (/image|picture|img|图片|照片/.test(k)) return 'image'
  if (/date|time|日期|时间/.test(k)) return 'date'
  if (/amount|money|total|价税|金额|合计/.test(k)) return 'thousand,d2'
  if (/price|cost|单价|成本/.test(k)) return 'thousand,d2'
  if (/qty|quantity|数量/.test(k)) return 'd2'
  return ''
}

// ============ 表格插入 / 明细勾选 / 边距 / 格式 ============
function nextFreeTop() {
  if (!blocks.value.length) return 20
  const bottom = Math.max(...blocks.value.map(b => b.top + b.height))
  return Math.min(paperH.value - 20, Math.max(20, bottom + 6))
}
function createDataTable(x, y, cols) {
  return newBlock('dataTable', x ?? 15, y ?? nextFreeTop(), {
    width: paperW.value - 30, height: 40,
    style: 'border-collapse:collapse;--th-bold:1;border:0.5pt solid #303133;',
    cfg: {
      cols: cols || [], rowMode: 'auto', fixedRows: 10, rowHeight: 8, rowAuto: false,
      totalOn: false, totalCols: [], totalRmb: false,
      groupOn: false, groupCol: null, groupLabel: '', groupTotalOn: false,
      borderMode: 'all', borderWidth: 0.5, borderColor: '#303133',
      thPad: '2mm', cellPad: '2mm', fontPt: 9
    }
  })
}
function addTable(x, y) {
  pushHistory()
  const rows = Math.max(1, Math.min(50, Number(newTableRows.value) || 3))
  const cols = Math.max(1, Math.min(MAX_COLS, Number(newTableCols.value) || 5))
  const cfg = { rows: [], colW: Array(cols).fill(0), rowH: 8, borderMode: 'all', borderWidth: 0.5, borderColor: '#303133', cellPad: '2mm', cellValign: 'middle', fontPt: 9 }
  for (let r = 0; r < rows; r++) {
    const row = []
    for (let c = 0; c < cols; c++) row.push(mkCell())
    cfg.rows.push(row)
  }
  const b = newBlock('grid', x ?? 15, y ?? nextFreeTop(), {
    width: paperW.value - 30, height: rows * 8,
    style: 'border-collapse:collapse;border:0.5pt solid #303133;', cfg
  })
  blocks.value.push(b)
  selectBlock(b.id)
  syncToSource()
}
// 明细区自定义叫法：只改打印时显示的列名，不动物料编码等系统字段名与绑定
function startAlias(row) {
  aliasEdit.value = { key: row.key, value: row.alias || '' }
}
function cancelAlias() {
  aliasEdit.value = null
}
function commitAlias(row) {
  const ed = aliasEdit.value
  aliasEdit.value = null
  if (!ed || ed.key !== row.key) return
  const next = String(ed.value || '').trim()
  const table = detailTable.value
  const col = table?.cfg?.cols.find(c => c.bind === row.key)
  if (!col) {
    // 尚未勾选：先把叫法记下来，勾选生成明细列时自动采用
    if (next) pendingAlias[row.key] = next
    else delete pendingAlias[row.key]
    return
  }
  const target = next || row.label
  if ((col.label || '') === target) return
  pushHistory()
  col.label = target
  syncToSource()
}
/**
 * 新增列的宽度适配：已有指定宽的列按原比例压缩，给新列让出"总宽÷列数"的份额；
 * 全列都无指定宽时交给浏览器均分，不动。勾选明细字段 / 右键插列共用。
 */
function fitNewColWidth(table) {
  const cols = table.cfg?.cols || []
  const fresh = cols.find(c => !c.w || c.w <= 0)
  const specified = cols.filter(c => c.w > 0)
  if (!fresh || !specified.length) return
  const W = table.width || (paperW.value - 30)
  const newW = Math.min(40, Math.max(12, Math.round(W / cols.length)))
  const rest = W - newW
  const sum = specified.reduce((s, c) => s + c.w, 0)
  const scaled = specified.map(c => Math.round(rest * c.w / sum))
  scaled[scaled.length - 1] += rest - scaled.reduce((s, x) => s + x, 0)
  specified.forEach((c, i) => { c.w = Math.max(1, scaled[i]) })
  fresh.w = newW
}
function toggleDetailField(key, checked) {
  let table = detailTable.value
  if (checked) {
    pushHistory()
    if (!table) {
      table = createDataTable(15, nextFreeTop(), [])
      blocks.value.push(table)
    }
    if (!table.cfg.cols.some(c => c.bind === key)) {
      const f = fields.value.items.find(x => x.key === key)
      // 勾选前已改过叫法的，生成明细列时直接用该叫法
      table.cfg.cols.push({ label: pendingAlias[key] || f?.label || key, bind: key, fmt: defaultFmtOf(key), align: '', w: 0 })
      fitNewColWidth(table)
      delete pendingAlias[key]
    }
    selectBlock(table.id)
  } else {
    if (!table) return
    const keep = []
    const map = {}
    let dropped = null
    table.cfg.cols.forEach((c, i) => {
      if (c.bind !== key) { map[i] = keep.length; keep.push(c) }
      else dropped = c
    })
    if (keep.length === table.cfg.cols.length) return
    // 取消勾选后叫法不丢：自定义过的先记住，重新勾选时恢复
    if (dropped) {
      const f = fields.value.items.find(x => x.key === key)
      const sys = f?.label || key
      if (dropped.label && dropped.label !== sys) pendingAlias[key] = dropped.label
    }
    pushHistory()
    table.cfg.cols = keep
    table.cfg.totalCols = table.cfg.totalCols.filter(i => map[i] != null).map(i => map[i])
    if (table.cfg.groupCol != null) table.cfg.groupCol = map[table.cfg.groupCol] ?? null
    if (!table.cfg.totalCols.length) table.cfg.totalRmb = false
    if (!table.cfg.cols.length) {
      blocks.value = blocks.value.filter(b => b.id !== table.id)
      selectedId.value = null
      selectedCell.value = null
      ElMessage.info('明细列已全部取消，明细表已移除')
    } else {
      selectBlock(table.id)
    }
  }
  syncToSource()
}
function onDetailDragStart(row, e) {
  if (!row.checked) { e.preventDefault(); return }
  detailDragKey = row.key
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', row.key)
  }
}
function onDetailDrop(row) {
  const table = detailTable.value
  const fromKey = detailDragKey
  detailDragKey = ''
  if (!table || !row.checked || !fromKey || fromKey === row.key) return
  const cols = table.cfg.cols
  const from = cols.findIndex(c => c.bind === fromKey)
  const to = cols.findIndex(c => c.bind === row.key)
  if (from < 0 || to < 0) return
  pushHistory()
  const [moved] = cols.splice(from, 1)
  cols.splice(to, 0, moved)
  const remap = (i) => {
    if (i === from) return to
    if (from < to && i > from && i <= to) return i - 1
    if (from > to && i >= to && i < from) return i + 1
    return i
  }
  table.cfg.totalCols = table.cfg.totalCols.map(remap)
  if (table.cfg.groupCol != null) table.cfg.groupCol = remap(table.cfg.groupCol)
  selectBlock(table.id)
  syncToSource()
}
function applyGridPad() {
  const b = selectedBlock.value
  if (!b || b.type !== 'grid') return
  pushHistory()
  b.cfg.cellPad = (Number(gridPadMM.value) || 0) + 'mm'
  syncToSource()
}
function setGridPad(v) {
  gridPadMM.value = v
  applyGridPad()
}
function applyGridCellPad() {
  const b = selectedBlock.value
  const loc = selectedCell.value
  if (!b || b.type !== 'grid' || !loc) return
  const cell = b.cfg.rows[loc.r]?.[loc.c]
  if (!cell || cell.hide) return
  pushHistory()
  cell.pad = (Number(gridCellPadMM.value) || 0) + 'mm'
  syncToSource()
}
function applyGridCellPadToRow() {
  const b = selectedBlock.value
  const loc = selectedCell.value
  if (!b || b.type !== 'grid' || !loc) return
  const row = b.cfg.rows[loc.r]
  if (!row) return
  pushHistory()
  const pad = (Number(gridCellPadMM.value) || 0) + 'mm'
  row.forEach(c => { if (!c.hide) c.pad = pad })
  syncToSource()
}
function clearGridCellPad() {
  const b = selectedBlock.value
  const loc = selectedCell.value
  if (!b || b.type !== 'grid' || !loc) return
  const cell = b.cfg.rows[loc.r]?.[loc.c]
  if (!cell) return
  pushHistory()
  cell.pad = ''
  gridCellPadMM.value = mmOfPad(b.cfg.cellPad)
  syncToSource()
}
function applyDtThPad() {
  if (!selectedBlock.value || selectedBlock.value.type !== 'dataTable') return
  dc.thPad = (Number(dtThPadMM.value) || 0) + 'mm'
  touchDc()
}
function applyDtBodyPad() {
  if (!selectedBlock.value || selectedBlock.value.type !== 'dataTable') return
  dc.cellPad = (Number(dtBodyPadMM.value) || 0) + 'mm'
  touchDc()
}
// ============ 明细表：拖动列边界调整单列宽（只与相邻列互换宽度，整表宽度不变） ============
let dtResize = null
function onDtResizeDown(b, e) {
  const handle = e.target
  if (!handle || !handle.classList || !handle.classList.contains('pt-dt-resizer')) return
  const ci = parseInt(handle.getAttribute('data-col'), 10)
  const cols = b.cfg?.cols || []
  if (isNaN(ci) || ci < 0 || ci + 1 >= cols.length) return
  e.preventDefault()
  e.stopPropagation()
  pushHistory()
  const k = MM_TO_PX * Math.max(0.1, displayScale.value)
  const paperEl = canvasWrapRef.value?.querySelector('.pt-paper')
  const ths = paperEl ? Array.from(paperEl.querySelectorAll(`[data-id="${b.id}"] thead th`)) : []
  // 自动分配的列先按当前渲染宽度落成毫米，否则一拖动整表列宽会重新洗牌
  if (cols.some(c => !(c.w > 0)) && ths.length === cols.length) {
    cols.forEach((c, i) => {
      const w = ths[i].getBoundingClientRect().width / k
      if (w > 0) c.w = Math.round(w * 10) / 10
    })
  }
  const a0 = cols[ci].w
  const c0 = cols[ci + 1].w
  if (!(a0 > 0 && c0 > 0)) return
  dtResize = { b, ci, startX: e.clientX, a0, c0, k }
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('mousemove', onDtResizeMove)
  window.addEventListener('mouseup', onDtResizeUp)
}
function onDtResizeMove(ev) {
  if (!dtResize) return
  const minW = 6
  const span = dtResize.a0 + dtResize.c0
  const d = (ev.clientX - dtResize.startX) / dtResize.k
  const a = Math.min(span - minW, Math.max(minW, dtResize.a0 + d))
  const cols = dtResize.b.cfg.cols
  cols[dtResize.ci].w = Math.round(a * 10) / 10
  cols[dtResize.ci + 1].w = Math.round((span - a) * 10) / 10
}
function onDtResizeUp() {
  if (!dtResize) return
  const b = dtResize.b
  dtResize = null
  window.removeEventListener('mousemove', onDtResizeMove)
  window.removeEventListener('mouseup', onDtResizeUp)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  loadPropsFromBlock(b)
  syncToSource()
}
function setCellFmt(v) {
  const b = selectedBlock.value
  const loc = selectedCell.value
  if (!b || !loc) return
  pushHistory()
  if (b.type === 'grid') {
    const cell = b.cfg.rows[loc.r]?.[loc.c]
    if (cell) cell.fmt = v
  } else if (b.type === 'dataTable' && loc.r >= 0) {
    const col = b.cfg.cols[loc.c]
    if (col) col.fmt = v
  }
  syncToSource()
}
function onColBindChange(col) {
  if (!col.bind) col.fmt = ''
  else if (!col.fmt) col.fmt = defaultFmtOf(col.bind)
  touchDc()
}
function sendImageBack() {
  if (selectedBlock.value?.type !== 'image') return
  zOrder('bottom')
  ElMessage.success('已衬于文字下方，点击空白处查看效果')
}
function sendImageFront() {
  if (selectedBlock.value?.type !== 'image') return
  zOrder('top')
  ElMessage.success('已浮于文字上方')
}

// ============ 新增块 ============
function addTextBlock() {
  pushHistory()
  const b = newBlock('text', 15, 15 + blocks.value.length * 12, {
    width: 80, height: 10,
    style: 'font-size:12pt;color:#303133;', html: '双击编辑文本'
  })
  blocks.value.push(b)
  selectBlock(b.id)
  syncToSource()
}
function addQrBlock(x, y) {
  pushHistory()
  const b = newBlock('qr', x ?? 20, y ?? 20, {
    width: 20, height: 20, style: '',
    html: '<img src="{{qrCode}}" style="width:100%;height:100%;" />'
  })
  blocks.value.push(b)
  selectBlock(b.id)
  syncToSource()
}
function addImageBlock() { fileInputRef.value.click() }
function clearImage() {
  const b = selectedBlock.value
  if (!b || b.type !== 'image') return
  pushHistory()
  b.cfg.src = ''
  syncToSource()
}
async function applyImageUrl() {
  const url = imageUrlInput.value.trim()
  const b = selectedBlock.value
  if (!b || b.type !== 'image') return
  if (!/^https?:\/\//i.test(url) && !url.startsWith('/uploads')) {
    ElMessage.error('图片地址需为 http(s) 链接或系统附件路径')
    return
  }
  pushHistory()
  b.cfg.src = url
  syncToSource()
  imageUrlInput.value = ''
  ElMessage.success('图片已更新')
}
function onFileChange(e) {
  const f = e.target.files[0]
  e.target.value = ''
  if (!f) return
  if (!/^image\/(png|jpeg|gif)$/.test(f.type)) {
    ElMessage.error('仅支持 jpg / png / gif 图片')
    return
  }
  const b0 = selectedBlock.value
  uploadFile(f, 'print-template').then(url => {
    pushHistory()
    if (b0 && b0.type === 'image' && selectedId.value === b0.id) {
      b0.cfg.src = url
    } else {
      const b = newBlock('image', 20, 20, { width: 30, height: 30, style: 'object-fit:contain;', cfg: { src: url, keepRatio: true } })
      blocks.value.unshift(b)
      selectBlock(b.id)
    }
    syncToSource()
    ElMessage.success('图片已插入')
  }).catch(() => {
    ElMessage.error('图片上传失败：网络或服务异常，请重试')
  })
}
function triggerImageUpload() { fileInputRef.value.click() }
function onCanvasPaste(e) {
  const items = e.clipboardData?.items
  if (!items) return
  for (const it of items) {
    if (it.type.startsWith('image/')) {
      const f = it.getAsFile()
      if (f) {
        e.preventDefault()
        uploadFile(f, 'print-template').then(url => {
          pushHistory()
          const b = newBlock('image', 40, 40, { width: 30, height: 30, style: 'object-fit:contain;', cfg: { src: url, keepRatio: true } })
          blocks.value.unshift(b)
          selectBlock(b.id)
          syncToSource()
          ElMessage.success('图片已粘贴插入')
        }).catch(() => ElMessage.error('图片上传失败：网络或服务异常，请重试'))
        return
      }
    }
  }
}

function deleteBlock() {
  if (!selectedId.value) return
  pushHistory()
  blocks.value = blocks.value.filter(b => b.id !== selectedId.value)
  selectedId.value = null
  selectedCell.value = null
  syncToSource()
}

// ============ 拖拽移动 / 缩放 / 吸附 ============
let dragState = null
function onBlockMouseDown(e, b) {
  if (e.target.closest('.pt-handle')) return
  if (e.target.closest('td,th') && (b.type === 'grid' || b.type === 'dataTable')) return
  selectBlock(b.id)
  pushHistory()
  dragState = { id: b.id, startX: e.clientX, startY: e.clientY, ol: b.left, ot: b.top, ow: b.width, oh: b.height, moved: false }
  document.addEventListener('mousemove', onBlockMove)
  document.addEventListener('mouseup', onBlockUp)
}
function snapCandidates(others) {
  const xs = [0, paperW.value / 2, paperW.value]
  const ys = [0, paperH.value / 2, paperH.value]
  others.forEach(o => { xs.push(o.left, o.left + o.width / 2, o.left + o.width); ys.push(o.top, o.top + o.height / 2, o.top + o.height) })
  return { xs, ys }
}
function onBlockMove(e) {
  if (!dragState) return
  const b = blocks.value.find(x => x.id === dragState.id)
  if (!b) return
  dragState.moved = true
  const k = MM_TO_PX * displayScale.value
  let dx = (e.clientX - dragState.startX) / k
  let dy = (e.clientY - dragState.startY) / k
  let l = dragState.ol + dx
  let t = dragState.ot + dy
  guides.v = []; guides.h = []
  if (snapOn.value) {
    const others = blocks.value.filter(x => x.id !== b.id)
    const { xs, ys } = snapCandidates(others)
    const targets = [l, l + b.width / 2, l + b.width]
    targets.forEach((tv, i) => {
      for (const gx of xs) {
        if (Math.abs(tv - gx) < 0.75) {
          l = i === 0 ? gx : (i === 1 ? gx - b.width / 2 : gx - b.width)
          guides.v = [gx]; break
        }
      }
    })
    const targetsY = [t, t + b.height / 2, t + b.height]
    targetsY.forEach((tv, i) => {
      for (const gy of ys) {
        if (Math.abs(tv - gy) < 0.75) {
          t = i === 0 ? gy : (i === 1 ? gy - b.height / 2 : gy - b.height)
          guides.h = [gy]; break
        }
      }
    })
    l = Math.round(l); t = Math.round(t)
  }
  b.left = Math.max(0, Math.min(paperW.value - b.width, l))
  b.top = Math.max(0, Math.min(paperH.value - 6, t))
  sp.left = b.left; sp.top = b.top
  syncToSource()
}
function onBlockUp() {
  if (dragState && dragState.moved) {
    const b = blocks.value.find(x => x.id === dragState.id)
    if (b) {
      // 重叠提示（超一半面积视为叠放，可层级调整）
      const others = blocks.value.filter(x => x.id !== b.id)
      for (const o of others) {
        const ox = Math.max(0, Math.min(b.left + b.width, o.left + o.width) - Math.max(b.left, o.left))
        const oy = Math.max(0, Math.min(b.top + b.height, o.top + o.height) - Math.max(b.top, o.top))
        if (ox * oy > 0.5 * Math.min(b.width * b.height, o.width * o.height)) {
          ElMessage.warning('元素有重叠：可通过属性面板层级调整叠放次序')
          break
        }
      }
    }
    syncToSource()
  } else if (dragState && !dragState.moved) {
    history.value.pop() // 未移动不进历史
  }
  dragState = null
  guides.v = []; guides.h = []
  document.removeEventListener('mousemove', onBlockMove)
  document.removeEventListener('mouseup', onBlockUp)
}

function onHandleMouseDown(e, h) {
  const b = selectedBlock.value
  if (!b) return
  pushHistory()
  dragState = { id: b.id, handle: h, startX: e.clientX, startY: e.clientY, ol: b.left, ot: b.top, ow: b.width, oh: b.height, moved: false }
  document.addEventListener('mousemove', onHandleMove)
  document.addEventListener('mouseup', onHandleUp)
}
function onHandleMove(e) {
  if (!dragState || !dragState.handle) return
  const b = blocks.value.find(x => x.id === dragState.id)
  if (!b) return
  dragState.moved = true
  const k = MM_TO_PX * displayScale.value
  const dx = (e.clientX - dragState.startX) / k
  const dy = (e.clientY - dragState.startY) / k
  let l = dragState.ol, t = dragState.ot, w = dragState.ow, h = dragState.oh
  if (dragState.handle.includes('r')) w = Math.max(10, dragState.ow + dx)
  if (dragState.handle.includes('b')) h = Math.max(8, dragState.oh + dy)
  if (dragState.handle.includes('l')) { w = Math.max(10, dragState.ow - dx); l = dragState.ol + (dragState.ow - w) }
  if (dragState.handle.includes('t')) { h = Math.max(8, dragState.oh - dy); t = dragState.ot + (dragState.oh - h) }
  if (snapOn.value) { w = Math.round(w); h = Math.round(h); l = Math.round(l); t = Math.round(t) }
  b.left = l; b.top = t; b.width = w; b.height = h
  sp.left = l; sp.top = t; sp.width = w; sp.height = h
  syncToSource()
}
function onHandleUp() {
  if (dragState && !dragState.moved) history.value.pop()
  dragState = null
  document.removeEventListener('mousemove', onHandleMove)
  document.removeEventListener('mouseup', onHandleUp)
}
function handleStyle(h) {
  const b = selectedBlock.value
  if (!b) return {}
  const map = { tl: [b.left, b.top], tr: [b.left + b.width, b.top], bl: [b.left, b.top + b.height], br: [b.left + b.width, b.top + b.height] }
  const [x, y] = map[h]
  return { left: (x * MM_TO_PX - 4) + 'px', top: (y * MM_TO_PX - 4) + 'px' }
}
function blockStyle(b, i = 0) {
  const st = {
    left: (b.left * MM_TO_PX) + 'px',
    top: (b.top * MM_TO_PX) + 'px',
    width: (b.width * MM_TO_PX) + 'px',
    // 层级：按块的排列顺序叠放，选中的块临时置顶；上移/下移/置顶/置底改的就是这个顺序
    zIndex: selectedId.value === b.id ? 900 : (i + 1)
  }
  // 文本段：把保存的字体样式同步到画布，字号按画布比例换算（1pt≈1.058px），与打印观感一致
  if (b.type === 'text') {
    const cs = parseStyle(b.style)
    Object.keys(cs).forEach(k => {
      if (/^(position|left|top|width|height|min-height|font-size)$/.test(k)) return
      st[k] = cs[k]
    })
    const fs = parseFloat(cs['font-size'])
    if (fs > 0) st.fontSize = (fs * 1.0584).toFixed(1) + 'px'
  }
  if (b.type === 'text') st.minHeight = (b.height * MM_TO_PX) + 'px'
  else if (isTableBlock(b.type)) st.height = 'auto'
  else st.height = (b.height * MM_TO_PX) + 'px'
  return st
}

// ============ 层级 ============
function zOrder(dir) {
  const b = selectedBlock.value
  if (!b) return
  pushHistory()
  const i = blocks.value.findIndex(x => x.id === b.id)
  if (i < 0) return
  blocks.value.splice(i, 1)
  if (dir === 'top') blocks.value.push(b)
  else if (dir === 'bottom') blocks.value.unshift(b)
  else if (dir === 'up') blocks.value.splice(Math.min(blocks.value.length, i + 1), 0, b)
  else blocks.value.splice(Math.max(0, i - 1), 0, b)
  syncToSource()
}

// ============ 样式应用 ============
function updateSelectedStyle(extra) {
  const b = selectedBlock.value
  if (!b) return
  const cs = parseStyle(b.style)
  Object.assign(cs, extra)
  b.style = styleToStr(cs)
  syncToSource()
}
function applyTextStyle() {
  const fs = parseFloat(sp.fontSize)
  sp.fontSize = (isFinite(fs) && fs > 0) ? fs : 12
  updateSelectedStyle({
    'font-size': sp.fontSize + 'pt',
    'font-weight': sp.fontWeight,
    'text-align': sp.textAlign,
    color: sp.color,
    'font-family': sp.fontFamily
  })
}
function toggleBold() { sp.fontWeight = sp.fontWeight === 'bold' ? 'normal' : 'bold'; applyTextStyle() }
function setAlign(a) { sp.textAlign = a; applyTextStyle() }
function applyImageStyle() {
  const b = selectedBlock.value
  if (!b || b.type !== 'image') return
  b.cfg.keepRatio = sp.keepRatio
  syncToSource()
}
function applyBorder() {
  const b = selectedBlock.value
  if (!b) return
  const bw = parseFloat(sp.borderWidth)
  sp.borderWidth = (isFinite(bw) && bw > 0) ? bw : 0.5
  if (b.type === 'grid') {
    pushHistory()
    b.cfg.borderWidth = sp.borderWidth
    b.cfg.borderColor = sp.borderColor
    syncToSource()
  } else if (b.type === 'dataTable') {
    pushHistory()
    dc.borderWidth = sp.borderWidth
    dc.borderColor = sp.borderColor
    touchDc()
  }
}
function setGridBorder(m) {
  const b = selectedBlock.value
  if (!b || b.type !== 'grid') return
  pushHistory()
  gridBorderMode.value = m
  b.cfg.borderMode = m
  const bw = parseFloat(sp.borderWidth)
  b.cfg.borderWidth = (isFinite(bw) && bw > 0) ? bw : 0.5
  b.cfg.borderColor = sp.borderColor
  syncToSource()
}
function applyGridRowHeight() {
  const b = selectedBlock.value
  if (!b || b.type !== 'grid') return
  pushHistory()
  b.cfg.rowH = gridRowHeight.value
  syncToSource()
}
function applyGridFontPt() {
  const b = selectedBlock.value
  if (!b || b.type !== 'grid') return
  pushHistory()
  const fs = parseFloat(gridFontPt.value)
  gridFontPt.value = (isFinite(fs) && fs > 0) ? fs : 12
  b.cfg.fontPt = gridFontPt.value
  syncToSource()
}
function applyGridFontFamily() {
  const b = selectedBlock.value
  if (!b || b.type !== 'grid') return
  pushHistory()
  b.cfg.fontFamily = gridFontFamily.value || ''
  syncToSource()
}
function applyPosition() {
  const b = selectedBlock.value
  if (!b) return
  b.left = sp.left; b.top = sp.top
  syncToSource()
}
function applySize() {
  const b = selectedBlock.value
  if (!b) return
  b.width = sp.width; b.height = sp.height
  syncToSource()
}

// ============ 布局表格：合并 / 拆分 / 行列 ============
function gridCells(b) { return b.cfg.rows }
function visibleRowCells(row) { return row.filter(c => !c.hide) }
function absStart(row, cell) {
  let x = 0
  for (const c of row) {
    if (c === cell) break
    if (!c.hide) x += Math.max(1, c.span)
  }
  return x
}
function findCellAtAbs(row, absCol) {
  let x = 0
  for (const c of row) {
    if (c.hide) continue
    const span = Math.max(1, c.span)
    if (absCol >= x && absCol < x + span) return c
    x += span
  }
  return null
}
function cellKey(r, c) { return r + ':' + c }

function gCtxCell() {
  const b = blocks.value.find(x => x.id === ctxMenu.blockId)
  if (!b || b.type !== 'grid') return null
  const cell = b.cfg.rows[ctxMenu.r]?.[ctxMenu.c]
  return (cell && !cell.hide) ? { b, cell, r: ctxMenu.r, c: ctxMenu.c } : null
}
// 解除单元格参与的合并：自身跨列/跨行归位，并把被它合并掉的隐藏格逐层恢复
function splitGridMerge(b, cell) {
  if (!cell) return
  const rows = b.cfg.rows
  const restore = (parentUid) => {
    rows.forEach(row => row.forEach(cc => {
      if (cc.hide && cc.mfrom === parentUid) {
        cc.hide = false
        cc.mfrom = null
        cc.span = 1
        cc.rspan = 1
        if (cc.uid) restore(cc.uid)
      }
    }))
  }
  if (cell.uid) restore(cell.uid)
  cell.span = 1
  cell.rspan = 1
  cell.mfrom = null
}
// 行内新增单元格：在当前格右侧插入一格，本行其后的格依次右移
function insGridCell(b, r, c) {
  const rows = b.cfg.rows
  const row = rows[r]
  const cell = row[c]
  if (!cell || cell.hide) return
  const at = absStart(row, cell) + Math.max(1, cell.span)
  // 插入点右侧的纵向合并会因右移错位，先解除
  row.filter(cc => !cc.hide && cc !== cell && cc.rspan > 1 && absStart(row, cc) >= at)
    .forEach(cc => splitGridMerge(b, cc))
  const idx = row.indexOf(cell)
  if (idx < 0) return
  row.splice(idx + 1, 0, mkCell())
  const rowCols = row.reduce((s, cc) => s + (cc.hide ? 0 : Math.max(1, cc.span)), 0)
  while (b.cfg.colW.length < rowCols) b.cfg.colW.push(0)
  selectedCell.value = { blockId: b.id, r, c: idx + 1 }
}
// 行内删除单元格：删除当前格，本行其后的格依次左移
function delGridCell(b, r, c) {
  const rows = b.cfg.rows
  const row = rows[r]
  const cell = row[c]
  if (!cell || cell.hide) return
  if (row.filter(x => !x.hide).length <= 1) { ElMessage.warning('至少保留一个单元格'); return }
  const at = absStart(row, cell)
  // 当前格参与的合并先解除，避免留下悬空合并
  if (cell.span > 1 || cell.rspan > 1) splitGridMerge(b, cell)
  // 删除点右侧的纵向合并会因左移错位，先解除
  row.filter(cc => !cc.hide && cc !== cell && cc.rspan > 1 && absStart(row, cc) >= at)
    .forEach(cc => splitGridMerge(b, cc))
  const idx = row.indexOf(cell)
  if (idx < 0) return
  row.splice(idx, 1)
  const maxCols = Math.max(1, ...rows.map(rr => rr.reduce((s, cc) => s + (cc.hide ? 0 : Math.max(1, cc.span)), 0)))
  while (b.cfg.colW.length > maxCols) b.cfg.colW.pop()
  selectedCell.value = { blockId: b.id, r, c: Math.max(0, Math.min(idx, row.length - 1)) }
}
function runCtx(k) {
  ctxMenu.visible = false
  if (k.startsWith('dt')) { runDtCtx(k); return }
  const g = gCtxCell()
  if (!g) return
  const { b, cell, r, c } = g
  const rows = b.cfg.rows
  pushHistory()
  if (k === 'mergeRight') {
    const row = rows[r]
    const idx = row.indexOf(cell)
    const next = row.slice(idx + 1).find(x => !x.hide)
    if (next) {
      if (!cell.uid) cell.uid = uid()
      cell.span += Math.max(1, next.span)
      next.hide = true
      next.mfrom = cell.uid
    }
  } else if (k === 'mergeDown') {
    const r2 = r + Math.max(1, cell.rspan)
    const below = rows[r2]
    if (below) {
      const abs = absStart(rows[r], cell)
      const target = findCellAtAbs(below, abs)
      if (target && !target.hide) {
        if (!cell.uid) cell.uid = uid()
        cell.rspan += Math.max(1, target.rspan)
        target.hide = true
        target.mfrom = cell.uid
      }
    }
  } else if (k === 'split') {
    splitGridMerge(b, cell)
  } else if (k === 'insCell') {
    insGridCell(b, r, c)
  } else if (k === 'delCell') {
    delGridCell(b, r, c)
  } else if (k === 'insRowAbove' || k === 'insRowBelow') {
    const at = k === 'insRowAbove' ? r : r + 1
    // 被纵向合并跨越的格延伸
    rows.forEach((row, ri) => row.forEach(cc => {
      if (!cc.hide && cc.rspan > 1) {
        const s = ri, e2 = ri + cc.rspan - 1
        if (at > s && at <= e2) cc.rspan++
      }
    }))
    const nrow = []
    const nCols = cfgColCount(b)
    for (let i = 0; i < nCols; i++) nrow.push(mkCell())
    rows.splice(at, 0, nrow)
  } else if (k === 'delRow') {
    if (rows.length <= 1) { ElMessage.warning('至少保留一行'); return }
    // 收缩跨越本行的纵向合并
    rows.forEach((row, ri) => row.forEach(cc => {
      if (!cc.hide && cc.rspan > 1 && ri < r && ri + cc.rspan - 1 >= r) cc.rspan--
    }))
    rows.splice(r, 1)
  } else if (k === 'insColLeft' || k === 'insColRight') {
    const at = k === 'insColLeft' ? absStart(rows[r], cell) : absStart(rows[r], cell) + Math.max(1, cell.span)
    insGridCol(b, at)
  } else if (k === 'delCol') {
    const abs = absStart(rows[r], cell)
    delGridCol(b, abs)
  }
  syncToSource()
}
function cfgColCount(b) {
  return b.cfg.colW.length || Math.max(1, ...b.cfg.rows.map(row => row.reduce((s, c) => s + (c.hide ? 0 : Math.max(1, c.span)), 0)))
}
function insGridCol(b, at) {
  if (cfgColCount(b) >= MAX_COLS) { ElMessage.warning('列数已达上限 20'); return }
  const nCols = cfgColCount(b)
  b.cfg.rows.forEach(row => {
    // 找覆盖 at 列的可见格
    let x = 0
    for (const c of row) {
      if (c.hide) continue
      const span = Math.max(1, c.span)
      if (at > x && at < x + span) { c.span++; return } // 落在合并格内部 → 加宽
      x += span
    }
    const nc = mkCell()
    // 插到该行正确位置（按绝对列序）
    let px = 0
    for (let i = 0; i < row.length; i++) {
      const c = row[i]
      if (c.hide) continue
      if (px >= at) { row.splice(i, 0, nc); return }
      px += Math.max(1, c.span)
    }
    row.push(nc)
  })
  b.cfg.colW.splice(Math.min(at, b.cfg.colW.length), 0, 0)
}
function delGridCol(b, at) {
  const nCols = cfgColCount(b)
  if (nCols <= 1) { ElMessage.warning('至少保留一列'); return }
  b.cfg.rows.forEach(row => {
    let x = 0
    for (let i = 0; i < row.length; i++) {
      const c = row[i]
      if (c.hide) continue
      const span = Math.max(1, c.span)
      if (at >= x && at < x + span) {
        if (span > 1) c.span--
        else row.splice(i, 1)
        return
      }
      x += span
    }
  })
  const wi = Math.min(at, b.cfg.colW.length - 1)
  if (wi >= 0) b.cfg.colW.splice(wi, 1)
}

// ============ 数据表格：列操作 ============
function insCol(dir) {
  const b = selectedBlock.value
  if (!b || b.type !== 'dataTable') return
  if (b.cfg.cols.length >= MAX_COLS) { ElMessage.warning('列数已达上限 20'); return }
  pushHistory()
  const at = dir < 0 ? 0 : b.cfg.cols.length
  b.cfg.cols.splice(at, 0, { label: '', bind: '', fmt: '', align: '', w: 0 })
  fitNewColWidth(b)
  touchDc()
}
function delCol(ci) {
  const b = selectedBlock.value
  if (!b || b.type !== 'dataTable') return
  if (b.cfg.cols.length <= 1) { ElMessage.warning('至少保留一列'); return }
  pushHistory()
  b.cfg.cols.splice(ci, 1)
  dc.totalCols = dc.totalCols.filter(x => x !== ci).map(x => (x > ci ? x - 1 : x))
  if (dc.groupCol === ci) dc.groupCol = null
  else if (dc.groupCol != null && dc.groupCol > ci) dc.groupCol--
  touchDc()
}
// 列顺序拖动：列名左侧手柄拖到目标列，松手即交换顺序
let colDragIdx = -1
function onColDragStart(ci, e) {
  colDragIdx = ci
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(ci))
  }
}
function onColDrop(ci) {
  const b = selectedBlock.value
  const from = colDragIdx
  colDragIdx = -1
  if (!b || b.type !== 'dataTable' || from < 0 || from === ci || ci < 0) return
  pushHistory()
  const [moved] = b.cfg.cols.splice(from, 1)
  b.cfg.cols.splice(ci, 0, moved)
  // 合计列 / 分组列记的是列位置，跟着一起挪
  const remap = (i) => {
    if (i === from) return ci
    if (from < ci && i > from && i <= ci) return i - 1
    if (from > ci && i >= ci && i < from) return i + 1
    return i
  }
  dc.totalCols = dc.totalCols.map(remap)
  if (dc.groupCol != null) dc.groupCol = remap(dc.groupCol)
  touchDc()
}
function runDtCtx(k) {
  const b = blocks.value.find(x => x.id === ctxMenu.blockId)
  if (!b || b.type !== 'dataTable') return
  const c = ctxMenu.c
  if (c == null || c < 0) return
  if (b.cfg.cols.length >= MAX_COLS && k !== 'dtColDel') { ElMessage.warning('列数已达上限 20'); return }
  pushHistory()
  if (k === 'dtColLeft') { b.cfg.cols.splice(c, 0, { label: '', bind: '', fmt: '', align: '', w: 0 }); fitNewColWidth(b) }
  else if (k === 'dtColRight') { b.cfg.cols.splice(c + 1, 0, { label: '', bind: '', fmt: '', align: '', w: 0 }); fitNewColWidth(b) }
  else if (k === 'dtColDel') {
    if (b.cfg.cols.length <= 1) { ElMessage.warning('至少保留一列'); return }
    b.cfg.cols.splice(c, 1)
  }
  loadPropsFromBlock(b)
  syncToSource()
}
function setRowMode(m) { dc.rowMode = m; if (m === 'fixed' && dc.rowAuto) dc.rowAuto = false; touchDc() }
function setRowAuto(v) {
  if (v && dc.rowMode === 'fixed') { ElMessage.warning('每页固定行数下行高固定，请切回按实际行数再开自适应'); return }
  dc.rowAuto = v
  touchDc()
}

// ============ 右键菜单 ============
// 统一在纸面拦截：单元格输入框覆盖在单元格之上时右键也在这里处理，避免弹出浏览器原生菜单
function onPaperCtx(e) {
  e.preventDefault()
  let blockId = null
  let r
  let c
  const ed = cellEdit.value
  if (ed && e.target.closest('.pt-cell-editor')) {
    // 正在输入文字时右键：先提交文字，再按原单元格弹出功能菜单
    commitCellEdit()
    blockId = ed.blockId
    r = ed.r
    c = ed.c
  } else {
    const blockEl = e.target.closest('.pt-block')
    const td = e.target.closest('td,th')
    if (!blockEl || !td) { ctxMenu.visible = false; return }
    blockId = blockEl.getAttribute('data-id')
    r = parseInt(td.getAttribute('data-r'), 10)
    c = parseInt(td.getAttribute('data-c'), 10)
  }
  const b = blocks.value.find(x => x.id === blockId)
  if (!b || (b.type !== 'grid' && b.type !== 'dataTable')) { ctxMenu.visible = false; return }
  selectedId.value = b.id
  selectedCell.value = (r >= 0 && c >= 0) ? { blockId: b.id, r, c } : null
  if (b.type === 'dataTable') loadPropsFromBlock(b)
  ctxMenu.blockId = b.id
  ctxMenu.r = r
  ctxMenu.c = c
  ctxMenu.x = e.clientX
  ctxMenu.y = e.clientY
  ctxMenu.visible = true
}
function onDocClick() { ctxMenu.visible = false }
// 单元格编辑不再靠 blur 提交：点工具条/下拉弹层不关闭输入框，点其它区域才提交
function onDocMouseDown(e) {
  if (!cellEdit.value) return
  const t = e.target
  if (!t || !t.closest) return
  if (t.closest('.pt-cell-editor') || t.closest('.pt-cell-toolbar')) return
  if (t.closest('.pt-cell-font-row')) return
  if (t.closest('td,th')) return
  if (t.closest('.el-popper') || t.closest('.el-select-dropdown') || t.closest('.el-select__popper')) return
  commitCellEdit()
}

// ============ 源码模式 ============
function switchMode(m) {
  if (m === 'code') { syncToSource(); clearHistory() }
  else {
    try {
      const parsed = parseHtmlToBlocks(sourceHtml.value)
      blocks.value = parsed.blocks
      idSeed = parsed.nextId
      selectedId.value = null
      selectedCell.value = null
      clearHistory()
    } catch {
      ElMessage.error('源码解析失败，请检查 HTML 语法')
      return
    }
  }
  mode.value = m
}

// ============ 一键生成标准模板 ============
/** 从真实存在的明细字段中挑选列（序号/物料/规格/单位/数量/单价/金额），不生成空列，并按内容分列宽 */
function pickItemColumns(contentW) {
  const items = fields.value.items || []
  const has = k => items.some(f => f.key === k)
  const defs = [
    { keys: ['sortNo', 'lineNo', 'seq', 'index'], label: '序号', align: 'center', weight: 0.06 },
    { keys: ['materialName', 'productName', 'itemName', 'materialCode', 'name'], label: '物料名称', align: 'left', weight: 0.29 },
    { keys: ['spec', 'specification', 'model', 'materialSpec'], label: '规格型号', align: 'left', weight: 0.21 },
    { keys: ['unit', 'materialUnit', 'unitName'], label: '单位', align: 'center', weight: 0.07 },
    { keys: ['quantity', 'qty', 'num'], label: '数量', align: 'right', num: true, weight: 0.09 },
    { keys: ['price', 'unitPrice'], label: '单价', align: 'right', num: true, money: true, weight: 0.12 },
    { keys: ['amount', 'totalAmount', 'netAmount', 'taxAmount'], label: '金额', align: 'right', num: true, money: true, total: true, weight: 0.16 }
  ]
  const cols = []
  const used = new Set()
  for (const d of defs) {
    const k = d.keys.find(x => !used.has(x) && has(x))
    if (!k) continue
    used.add(k)
    cols.push({ label: d.label, bind: k, fmt: d.money ? 'thousand,d2' : (d.num ? 'd2' : ''), align: d.align || '', w: 0, weight: d.weight, total: !!d.total })
  }
  if (cols.length < 3) {
    for (const f of items) {
      if (cols.length >= 4) break
      if (used.has(f.key)) continue
      used.add(f.key)
      cols.push({ label: f.label, bind: f.key, fmt: defaultFmtOf(f.key), align: '', w: 0, weight: 0.18 })
    }
  }
  if (!cols.length) cols.push({ label: '明细内容', bind: '', fmt: '', align: '', w: 0, weight: 1 })
  // 按权重折算成毫米列宽（保证合计正好等于版心宽）
  const totalW = cols.reduce((s, c) => s + (c.weight || 0.15), 0)
  cols.forEach(c => { c.w = Math.max(10, Math.round(contentW * (c.weight || 0.15) / totalW)) })
  return cols
}

function generateStandard(silent = false) {
  if (!silent) pushHistory()
  blocks.value = []
  const label = _p.docLabel || docTypeLabel(_p.docType)
  const M = 15
  const CW = paperW.value - M * 2
  // 1) 标题
  blocks.value.push(newBlock('text', 0, 12, {
    width: paperW.value, height: 12,
    style: 'font-size:20pt;text-align:center;font-weight:bold;color:#1f2329;', html: label
  }))
  // 2) 信息区：标签/值 四列布局表格
  const info = fields.value.header.slice(0, 6)
  const gcfg = {
    rows: [], colW: [CW * 0.16, CW * 0.34, CW * 0.16, CW * 0.34], rowH: 9,
    borderMode: 'all', borderWidth: 0.5, borderColor: '#909399', cellPad: '2mm', cellValign: 'middle', fontPt: 9
  }
  for (let i = 0; i < info.length; i += 2) {
    const a = info[i], b2 = info[i + 1]
    const row = [
      mkCell({ t: a.label, align: 'right', bold: true }),
      mkCell({ bind: a.key })
    ]
    if (b2) row.push(mkCell({ t: b2.label, align: 'right', bold: true }), mkCell({ bind: b2.key }))
    else row.push(mkCell({ t: '' }), mkCell({ t: '' }))
    gcfg.rows.push(row)
  }
  if (!gcfg.rows.length) {
    gcfg.rows.push([
      mkCell({ t: '制单日期', align: 'right', bold: true }), mkCell({ bind: 'createTime' }),
      mkCell({ t: '制单人', align: 'right', bold: true }), mkCell({ bind: 'createByName' })
    ])
  }
  const gridH = gcfg.rows.length * gcfg.rowH
  blocks.value.push(newBlock('grid', M, 30, {
    width: CW, height: gridH,
    style: 'border-collapse:collapse;border:0.5pt solid #909399;', cfg: gcfg
  }))
  // 3) 明细数据表格（只绑真实字段 + 表头底色 + 合计行）
  const cols = pickItemColumns(CW)
  const totalCols = cols.map((c, i) => (c.total ? i : -1)).filter(i => i >= 0)
  const dtCfg = {
    cols, rowMode: 'auto', fixedRows: 10, rowHeight: 7, rowAuto: false,
    totalOn: totalCols.length > 0, totalCols, totalRmb: totalCols.length > 0,
    groupOn: false, groupCol: null, groupLabel: '', groupTotalOn: false,
    borderMode: 'all', borderWidth: 0.5, borderColor: '#303133',
    thBg: '#f2f3f5', thPad: '2mm', cellPad: '2mm', fontPt: 9
  }
  const dtTop = 30 + gridH + 6
  blocks.value.push(newBlock('dataTable', M, dtTop, {
    width: CW, height: 26,
    style: 'border-collapse:collapse;--th-bold:1;border:0.5pt solid #303133;', cfg: dtCfg
  }))
  // 4) 签字区：紧跟明细表下方（不甩到页底）
  const scfg = {
    rows: [[
      mkCell({ t: '制单人：', align: 'left' }),
      mkCell({ t: '审核人：', align: 'left' }),
      mkCell({ t: '客户签字：', align: 'left' })
    ]],
    colW: [CW / 3, CW / 3, CW / 3], rowH: 10,
    borderMode: 'none', cellPad: '3mm 2mm', cellValign: 'bottom'
  }
  blocks.value.push(newBlock('grid', M, dtTop + 26 + 8, {
    width: CW, height: 10, style: 'border-collapse:collapse;', cfg: scfg
  }))
  selectBlock(blocks.value[0].id)
  blocks.value.forEach(x => { x.gen = true })
  syncToSource()
  // 生成后按真实渲染高度顺排（标题→信息区→明细→签字），杜绝"签字压表格/内容出纸"
  nextTick(() => stackGen())
  if (!silent) ElMessage.success('标准模板已生成：标题 / 信息区 / 明细表含合计 / 签字区，可直接微调')
}

/** 一键生成的块：保持各自左边界与宽度，按真实高度自上而下顺排 */
function stackGen(retry = 0) {
  const list = blocks.value.filter(x => x.gen)
  if (list.length < 2) return
  const paperEl = canvasWrapRef.value?.querySelector('.pt-paper')
  if (!paperEl) {
    if (retry < 3) setTimeout(() => stackGen(retry + 1), 80)
    return
  }
  const k = MM_TO_PX * displayScale.value
  let y = 12
  for (const b of list) {
    const el = paperEl.querySelector(`[data-id="${b.id}"]`)
    const hPx = el ? el.getBoundingClientRect().height : b.height * k
    const hMM = Math.max(6, Math.round(hPx / k))
    b.top = y
    b.height = hMM
    y += hMM + 6
  }
  syncToSource()
}

function docTypeLabel(dt) {
  const map = {
    SALE_ORDER: '销 售 订 单', SALE_CONTRACT: '销 售 合 同', SALE_QUOTE: '报 价 单', PURCHASE_ORDER: '采 购 订 单',
    SALE_DELIVERY: '销售出库单', SALE_RETURN: '销售退货单', PURCHASE_INBOUND: '采购入库单', MATERIAL: '物料标签', WORK_OPERATION: '工序卡'
  }
  return map[dt] || '打印单据'
}

// ============ 预览（与真实打印同一套后端渲染） ============
async function previewInPlace() {
  if (!_p.docType) { ElMessage.warning('请先选择单据类型'); return }
  if (!blocks.value.length) { ElMessage.warning('请先添加内容'); return }
  if (cellEdit.value) commitCellEdit()
  const payload = {
    templateName: _p.docLabel || docTypeLabel(_p.docType),
    docType: _p.docType,
    templateContent: blocksToHtml(),
    templateStyle: sourceCss.value,
    pageSize: _p.pageSize,
    pageOrientation: _p.pageOrientation,
    marginTop: _p.marginTop,
    marginBottom: _p.marginBottom,
    marginLeft: _p.marginLeft,
    marginRight: _p.marginRight
  }
  previewVisible.value = true
  // 弹窗销毁重建后 iframe 需等渲染完成再写入（否则首次点击无反应）
  await nextTick()
  const iframe = previewIframeRef.value
  if (!iframe) { ElMessage.error('预览打开失败，请重试'); return }
  try {
    const html = await printTemplateApi.previewHtml(payload)
    const doc = iframe.contentDocument || iframe.contentWindow.document
    doc.open()
    doc.write(html || '')
    doc.close()
  } catch (e) {
    ElMessage.error('预览生成失败，请重试')
  }
}

// ============ 保存 ============
function save() {
  if (!_p.docType) { ElMessage.warning('请先选择单据类型'); return }
  if (blocks.value.length === 0) { ElMessage.warning('请先添加内容'); return }
  if (cellEdit.value) commitCellEdit()
  saving.value = true
  const content = blocksToHtml()
  emit('update:modelContent', content)
  emit('update:modelStyle', sourceCss.value)
  emit('save')
  clearHistory()
  setTimeout(() => { saving.value = false }, 500)
}

// ============ 分组展开 ============
function toggleGroup(k) { groupOpen[k] = !groupOpen[k] }

// ============ 生命周期 ============
function onKeydown(e) {
  if (mode.value !== 'design') return
  const t = e.target
  if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return
  if (e.key === 'Escape' && selectedCell.value) {
    // 先退出单元格选中（表格仍是选中元素），再按 Delete 才能删除整张表格
    e.preventDefault()
    e.stopImmediatePropagation()
    selectedCell.value = null
    return
  }
  if (e.key === 'Delete') {
    if (selectedCell.value) {
      e.preventDefault()
      clearSelectedCellContent()
      return
    }
    if (selectedId.value) {
      e.preventDefault()
      deleteBlock()
      return
    }
  }
  if ((e.ctrlKey || e.metaKey) && !e.altKey && e.key.toLowerCase() === 'z') {
    e.preventDefault()
    if (e.shiftKey) redo()
    else undo()
  }
}
onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('mousedown', onDocMouseDown, true)
  document.addEventListener('keydown', onKeydown)
  // 画布视口宽度监测 → 自动适应缩放
  if (canvasWrapRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(entries => {
      for (const en of entries) { wrapW.value = en.contentRect.width; wrapH.value = en.contentRect.height }
    })
    resizeObserver.observe(canvasWrapRef.value)
    wrapW.value = canvasWrapRef.value.clientWidth
    wrapH.value = canvasWrapRef.value.clientHeight
  } else {
    wrapW.value = canvasWrapRef.value?.clientWidth || 0
    wrapH.value = canvasWrapRef.value?.clientHeight || 0
  }
})
let resizeObserver = null
onBeforeUnmount(() => {
  if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null }
  document.removeEventListener('mousemove', onBlockMove)
  document.removeEventListener('mouseup', onBlockUp)
  document.removeEventListener('mousemove', onHandleMove)
  document.removeEventListener('mouseup', onHandleUp)
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('mousedown', onDocMouseDown, true)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped lang="scss">
.pt-designer { display: flex; flex-direction: column; height: 100%; min-height: 420px; font-size: 13px; color: #303133; }
.pt-topbar { display: flex; flex-direction: column; gap: 6px; padding: 8px 12px; background: #fff; border-bottom: 1px solid #ebeef5; }
.pt-tb-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.pt-tb-row-sub { padding-top: 6px; border-top: 1px dashed #ebeef5; }
.pt-tb-group { display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.pt-tb-group-pos { padding-left: 12px; border-left: 1px solid #ebeef5; }
.pt-tb-cap { font-size: 12px; color: #1890ff; font-weight: 600; }
.pt-tb-mini { font-size: 11px; color: #909399; }
.pt-tb-unit { font-size: 11px; color: #c0c4cc; }
.pt-tb-hint { font-size: 11px; color: #b1b3b8; }
.pt-tb-num { width: 74px; }
.pt-tabs { display: inline-flex; border: 1px solid #dcdfe6; border-radius: 6px; overflow: hidden; }
.pt-tab { padding: 5px 14px; font-size: 13px; color: #606266; cursor: pointer; border-right: 1px solid #dcdfe6; user-select: none; }
.pt-tab:last-child { border-right: 0; }
.pt-tab.on { background: #e6f4ff; color: #1890ff; font-weight: 600; }
.pt-sep { width: 1px; height: 18px; background: #dcdfe6; }
.pt-quick { font-size: 12px; color: #909399; cursor: pointer; padding: 3px 8px; border-radius: 4px; user-select: none; }
.pt-quick:hover { background: #f0f2f5; }
.pt-quick.on { color: #1890ff; background: #e6f4ff; }
.pt-zoom { display: inline-flex; align-items: center; gap: 2px; border: 1px solid #dcdfe6; border-radius: 4px; padding: 1px; background: #fff; }
.pt-zoom-btn { font-size: 12px; color: #606266; padding: 2px 7px; border-radius: 3px; cursor: pointer; user-select: none; white-space: nowrap; }
.pt-zoom-btn:hover { background: #f0f2f5; }
.pt-zoom-btn.on { background: #e6f4ff; color: #1890ff; font-weight: 600; }
.pt-zoom-pct { font-size: 11px; color: #909399; padding: 0 6px; min-width: 38px; text-align: right; font-variant-numeric: tabular-nums; }
.pt-actions { margin-left: auto; display: flex; gap: 8px; }

.pt-grid { display: grid; grid-template-columns: 236px 1fr 280px; gap: 12px; padding: 12px; flex: 1; overflow: hidden; }
.pt-sidebar { background: #fafbfc; border: 1px solid #ebeef5; border-radius: 6px; padding: 10px; overflow-y: auto; }
.pt-sb-title { font-weight: 600; font-size: 14px; margin-bottom: 8px; padding-bottom: 6px; border-bottom: 2px solid #1890ff; }
.pt-group { margin-bottom: 8px; }
.pt-group-name { font-size: 12px; color: #606266; margin-bottom: 6px; cursor: pointer; display: flex; align-items: center; gap: 4px; user-select: none; }
.pt-arrow { width: 0; height: 0; border-left: 5px solid #909399; border-top: 4px solid transparent; border-bottom: 4px solid transparent; display: inline-block; transition: transform .15s; }
.pt-arrow.open { transform: rotate(90deg); }
.pt-def { font-size: 10px; color: #1890ff; background: #e6f4ff; border-radius: 3px; padding: 0 4px; }
.pt-chips { display: flex; flex-wrap: wrap; gap: 4px; }
.pt-chip { display: inline-block; background: #fff; border: 1px solid #dcdfe6; border-radius: 4px; padding: 3px 8px; font-size: 12px; color: #606266; cursor: grab; user-select: none; }
.pt-chip:hover { border-color: #1890ff; color: #1890ff; }
.pt-chip.it { background: #f0f7ff; border-color: #b3d8ff; color: #1890ff; }
.pt-chip.main { background: #e6f4ff; border-color: #1890ff; color: #1890ff; }
.pt-chip-block { display: inline-flex; align-items: center; gap: 4px; }
.pt-chip-empty { font-size: 11px; color: #c0c4cc; padding: 2px 0; }
.pt-hint { font-size: 11px; color: #909399; line-height: 1.6; margin-top: 8px; background: #ecf5ff; padding: 6px 8px; border-radius: 4px; }
.pt-detail-list { border: 1px solid #ebeef5; border-radius: 4px; background: #fff; padding: 4px; max-height: 220px; overflow-y: auto; }
.pt-detail-row { display: flex; align-items: center; gap: 4px; padding: 2px 4px; border-radius: 4px; }
.pt-detail-row.on { background: #f0f7ff; }
.pt-alias { margin-left: auto; flex: none; font-size: 11px; color: #1f6feb; cursor: pointer; padding: 0 2px; }
.pt-alias.set { color: #303133; background: #ecf5ff; border-radius: 3px; padding: 1px 5px; max-width: 92px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pt-alias-input { width: 92px; margin-left: auto; flex: none; }
.pt-detail-handle { color: #909399; font-size: 12px; cursor: grab; user-select: none; letter-spacing: -1px; }
.pt-detail-handle.off { color: #dcdfe6; cursor: default; }

.pt-canvas-wrap { background: #e9ecef; border: 1px solid #ebeef5; border-radius: 6px; overflow: auto; display: flex; align-items: flex-start; padding: 12px; min-width: 380px; }
.pt-paper-holder { margin: 0 auto; flex: 0 0 auto; }
.pt-paper { position: relative; background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,.1); }
.pt-margin-guide { position: absolute; border: 1px dashed #b8d4ff; pointer-events: none; }
.pt-empty-guide { position: absolute; top: 30%; left: 50%; transform: translate(-50%, -50%); text-align: center; color: #c0c4cc; user-select: none; }
.pt-eg-title { font-size: 15px; color: #909399; margin-bottom: 6px; }
.pt-eg-sub { font-size: 12px; }
.pt-guide { position: absolute; background: #ff9d00; z-index: 1200; pointer-events: none; }
.pt-guide.v { width: 1px; top: 0; bottom: 0; }
.pt-guide.h { height: 1px; left: 0; right: 0; }
.pt-block { position: absolute; box-sizing: border-box; cursor: move; }
.pt-block.selected { outline: 2px solid #1890ff; outline-offset: 1px; }
.pt-block-text .pt-html { width: 100%; height: 100%; overflow: visible; }
.pt-t { width: 100%; height: auto; border-collapse: collapse; table-layout: fixed; }
.pt-t td, .pt-t th { padding: 3px 5px; font-size: 12.7px; overflow: hidden; word-break: break-all; }
.pt-t td.pt-csel, .pt-t th.pt-csel { outline: 2px solid #ff9d00; outline-offset: -2px; cursor: pointer; }
.pt-t td { cursor: pointer; }
/* 明细表列宽拖拽手柄：几何与定位写在手柄内联样式里，这里只补悬停提示条 */
.pt-t :deep(.pt-dt-resizer:hover) { background: rgba(24,144,255,.35); }
.total-preview td { background: #f5f7fa; color: #909399; font-size: 11pt; }
.pt-cell-editor { position: absolute; z-index: 1100; font-size: 13px; line-height: 1.4; font-family: inherit; padding: 1px 4px; border: 2px solid #1890ff; box-sizing: border-box; resize: none; background: #fffdf5; outline: none; }
.pt-cell-toolbar { position: absolute; z-index: 1150; display: flex; align-items: center; gap: 4px; padding: 3px 6px; background: #fff; border: 1px solid #b3d8ff; border-radius: 4px; box-shadow: 0 2px 8px rgba(0,0,0,.12); white-space: nowrap; }
.pt-ct-label { font-size: 11px; color: #909399; }
.pt-handle { position: absolute; width: 10px; height: 10px; background: #fff; border: 2px solid #1890ff; border-radius: 2px; z-index: 1000; }
.pt-handle-tl { cursor: nwse-resize; }
.pt-handle-tr { cursor: nesw-resize; }
.pt-handle-bl { cursor: nesw-resize; }
.pt-handle-br { cursor: nwse-resize; }

.pt-props { background: #fafbfc; border: 1px solid #ebeef5; border-radius: 6px; padding: 10px; overflow-y: auto; }
.pt-props-title { font-weight: 600; font-size: 14px; margin-bottom: 8px; padding-bottom: 6px; border-bottom: 2px solid #1890ff; }
.pt-empty { color: #909399; font-size: 13px; text-align: center; padding: 40px 0; }
.pt-sec { font-size: 12px; color: #1890ff; font-weight: 600; margin: 10px 0 6px; padding-left: 6px; border-left: 3px solid #1890ff; }
.pt-prop-row { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.pt-prop-label { font-size: 12px; color: #909399; min-width: 44px; }
.pt-prop-label-mini { min-width: 16px; }
.pt-prop-note { font-size: 11px; color: #909399; line-height: 1.6; background: #f4f8ff; border-radius: 4px; padding: 6px 8px; margin: 6px 0; }
.pt-unit { font-size: 11px; color: #909399; }
.pt-block-tag { background: #e6f4ff; color: #1890ff; border-radius: 4px; padding: 2px 8px; font-size: 12px; }
.pt-seg { display: inline-flex; border: 1px solid #dcdfe6; border-radius: 4px; overflow: hidden; }
.pt-seg span { padding: 3px 10px; font-size: 12px; color: #606266; border-right: 1px solid #dcdfe6; cursor: pointer; user-select: none; }
.pt-seg span:last-child { border-right: 0; }
.pt-seg span.on { background: #e6f4ff; color: #1890ff; font-weight: 600; }
.pt-col-row { display: flex; align-items: center; gap: 4px; margin-bottom: 6px; flex-wrap: wrap; }
.pt-col-drag { cursor: grab; color: #909399; font-size: 12px; line-height: 1; user-select: none; letter-spacing: -1px; }
.pt-col-drag:active { cursor: grabbing; }
.pt-col-idx { font-size: 11px; color: #909399; width: 14px; text-align: right; }
.pt-ck { margin-right: 8px; }
.pt-ctx { position: fixed; z-index: 3000; background: #fff; border: 1px solid #e4e7ed; border-radius: 6px; box-shadow: 0 4px 16px rgba(0,0,0,.12); padding: 4px; min-width: 130px; }
.pt-ctx-item { padding: 6px 14px; font-size: 13px; color: #606266; cursor: pointer; border-radius: 4px; }
.pt-ctx-item:hover { background: #ecf5ff; color: #1890ff; }
.pt-ctx-sep { height: 1px; background: #ebeef5; margin: 4px 6px; }

.pt-code { display: grid; grid-template-columns: 1fr 320px; gap: 12px; padding: 12px; flex: 1; overflow: hidden; }
.pt-code-left, .pt-code-right { display: flex; flex-direction: column; }
.pt-code-head { font-size: 13px; font-weight: 600; margin-bottom: 6px; color: #606266; }
.pt-code-area { flex: 1; width: 100%; font-family: Consolas, "Courier New", monospace; font-size: 12px; line-height: 1.6; padding: 10px; border: 1px solid #dcdfe6; border-radius: 4px; resize: none; background: #fff; color: #303133; }
.pt-code-hint { grid-column: 1 / -1; font-size: 12px; color: #909399; background: #fff7e6; padding: 6px 10px; border-radius: 4px; }

.pt-preview-iframe { width: 100%; height: 720px; border: 1px solid #ebeef5; border-radius: 4px; background: #fff; }
</style>
