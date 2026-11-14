<template>
  <div class="page tabs-page">
    <div class="page-header">
      <span class="page-title">排课管理</span>
      <div class="actions">
        <!-- 教室筛选：选中后日/周/月视图只显示该教室课次，空白时段=该教室空闲（单间教室排班表） -->
        <el-select v-show="isCalView" v-model="roomFilter" clearable filterable
                   placeholder="全部教室" style="width: 150px"
                   title="按教室筛选日历：选教室后空白时段即空闲">
          <el-option v-for="r in classrooms" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增排课</el-button>
        <el-button @click="openFreeTime">教师空闲</el-button>
      </div>
    </div>

    <!-- 一级视图TAB：日视图 / 周视图 / 月视图 / 列表 / 排课历史 -->
    <el-tabs v-model="viewTab" class="view-tabs" @tab-change="onViewTab">
      <el-tab-pane label="日视图" name="day" />
      <el-tab-pane label="周视图" name="week" />
      <el-tab-pane label="月视图" name="month" />
      <el-tab-pane label="列表" name="list" />
      <el-tab-pane label="排课历史" name="history" />
    </el-tabs>

    <!-- ========== 日/周/月日历视图：双击日期看当日详情，拖拽调时，框选新增 ========== -->
    <div v-show="isCalView" class="panel cal-panel tabs-fill"
         style="padding: 12px"
         title="双击日期查看当日课次详情"
         @dblclick="onCalDblClick">
      <FullCalendar ref="calRef" :options="calOptions" />
    </div>

    <!-- ========== 课次清单抽屉：多教室折叠汇总块、月视图「+N 节」、双击日期、单课次点击 四处共用；
         操作仅保留 调时/删除（取消与删除功能重复已移除；到课/请假/旷课/补课登记仍在列表TAB） ========== -->
    <el-drawer v-model="slotVisible" :title="slotTitle" size="520px">
      <div v-for="r in pagedSlotRows" :key="r.id" class="panel slot-item">
        <div class="slot-head">
          <el-tag effect="dark" size="small" type="info">{{ fmtHM(r.startTime) }}~{{ fmtHM(r.endTime) }}</el-tag>
          <el-tag size="small" :type="r.clazzId ? 'primary' : 'warning'" effect="plain">
            {{ r.clazzId ? '班课' : '一对一' }}</el-tag>
          <!-- 点击课名展开/收缩 教师、班级、上课学生信息 -->
          <span class="slot-name slot-toggle" @click="toggleSlot(r)">
            <el-icon class="slot-arrow" :class="{ open: r._open }"><ArrowRight /></el-icon>
            {{ r.clazzId ? clazzName(r.clazzId) : studentName(r.studentId) }}
          </span>
          <el-tag size="small" :type="SCH_STATUS[r.status]?.type || 'info'" effect="light">
            {{ SCH_STATUS[r.status]?.label || '-' }}</el-tag>
        </div>
        <div class="slot-meta">
          {{ teacherName(r.teacherId) }}<template v-if="r.classroom"> · 📍{{ r.classroom }}</template>
        </div>
        <!-- 展开区：教师信息 / 班级信息 / 上课学生信息 -->
        <div v-if="r._open" class="slot-detail">
          <div class="sd-row"><span class="sd-label">教师</span>{{ teacherInfo(r.teacherId) }}</div>
          <template v-if="r.clazzId">
            <div class="sd-row">
              <span class="sd-label">班级</span>
              {{ courseNameOf(r.clazzId) }} · 班主任 {{ clazzTeacherOf(r.clazzId) }}
            </div>
            <div class="sd-row">
              <span class="sd-label">上课学生</span>
              <template v-if="(clazzStudentsCache[r.clazzId] || []).length">
                <el-tag v-for="s in clazzStudentsCache[r.clazzId]" :key="s.studentId"
                        size="small" effect="plain" class="sd-stu" :title="s.phone || ''">{{ s.name }}</el-tag>
              </template>
              <span v-else class="sd-muted">暂无在读学员</span>
            </div>
          </template>
          <div v-else class="sd-row"><span class="sd-label">学生</span>{{ studentInfo(r.studentId) }}</div>
        </div>
        <div class="slot-acts">
          <!-- 编辑上课：按学员登记 到课/请假/旷课（仅未登记课次） -->
          <el-button v-if="r.status === 0" link type="success" size="small"
                     @click="openMarkAtt(r)">编辑上课</el-button>
          <el-button v-if="r.status === 0 && canAdjustRow(r)" link type="primary" size="small"
                     @click="slotAction(r, openAdjust)">调时</el-button>
          <el-button v-if="r.status === 0 || r.status === 2" link type="danger" size="small"
                     @click="slotDelete(r)">删除</el-button>
        </div>
      </div>
      <el-empty v-if="!slotRows.length" description="无课次" :image-size="60" />
      <!-- 时段课次多于 10 节：分页展示 -->
      <div v-if="slotRows.length > SLOT_PAGE_SIZE" class="pager-row" style="margin-top: 4px">
        <el-pagination background small layout="total, prev, pager, next" :total="slotRows.length"
                       v-model:current-page="slotPage" :page-size="SLOT_PAGE_SIZE" />
      </div>
    </el-drawer>

    <!-- ========== 列表 TAB：4个阶段子TAB（徽标数与拆分一致） ========== -->
    <div v-show="viewTab === 'list'" class="panel" style="padding: 0 16px; margin-bottom: 0">
      <el-tabs v-model="phase" class="phase-tabs" @tab-change="onPhaseChange">
        <el-tab-pane name="tobe">
          <template #label>待排课<el-badge v-if="counts.tobe" :value="counts.tobe" :max="99" class="phase-badge" /></template>
        </el-tab-pane>
        <el-tab-pane name="today">
          <template #label>待上课<el-badge v-if="counts.today" :value="counts.today" :max="99" class="phase-badge" /></template>
        </el-tab-pane>
        <el-tab-pane name="ongoing">
          <template #label>上课中<el-badge v-if="counts.ongoing" :value="counts.ongoing" :max="99" class="phase-badge" /></template>
        </el-tab-pane>
        <el-tab-pane name="done">
          <template #label>已下课<el-badge v-if="counts.done" :value="counts.done" :max="99" class="phase-badge" /></template>
        </el-tab-pane>
      </el-tabs>
    </div>
    <div v-show="viewTab === 'list'" class="panel table-panel tabs-fill">
      <div class="toolbar">
        <el-select v-model="filters.teacherId" placeholder="教师" clearable filterable style="width: 140px" @change="onFilterChange">
          <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="filters.clazzId" placeholder="班级" clearable filterable style="width: 160px">
          <el-option v-for="c in clazzes" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="filters.orgId" placeholder="校区" clearable filterable style="width: 140px" @change="onFilterChange">
          <el-option v-for="o in orgList" :key="o.id" :label="o.name" :value="o.id" />
        </el-select>
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px" />
        <el-button type="primary" @click="doSearch">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="schedule" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.startTime" label="上课时间" width="290" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.phase === 'tobe'">{{ row.timeText || '待安排' }}<span class="tobe-hint">（待排）</span></template>
            <template v-else>{{ row.startTime }} ~ {{ row.endTime }}</template>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.teacherId" label="教师" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ teacherName(row.teacherId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.clazzId" label="班级/学生" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.clazzId ? clazzName(row.clazzId) : studentName(row.studentId) + '(一对一)' }}
            <el-tag v-if="row.phase === 'tobe'" size="small" type="danger" effect="plain" class="remain-tag">
              剩{{ row.remainingHours }}课时未排
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.classroom" prop="classroom" label="教室" width="95"  show-overflow-tooltip/>
        <el-table-column v-if="cols.status" label="状态" width="135" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.phase === 'done'" size="small" :type="outcomeMeta[row.status]?.type || 'info'" effect="plain">
              {{ outcomeMeta[row.status]?.label || '未登记' }}
            </el-tag>
            <el-tag v-else size="small" :type="phaseMeta[row.phase]?.type || 'info'" effect="plain">
              {{ phaseMeta[row.phase]?.label || '-' }}
            </el-tag>
            <el-tag v-if="row.compensation === 1" size="small" type="info" effect="plain">补</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right" class-name="no-wrap-col">
          <template #default="{ row }">
            <!-- 行内操作收进「更多」下拉：调时/取消（未开始）、到课/请假/旷课（已下课未登记）、补课（已取消）、去排课（待排课）；
                 容器 v-if = 各操作条件的并集，已登记完的课次保持空单元格 -->
            <el-dropdown v-if="(row.id && (row.status === 0 || row.status === 2)) || row.phase === 'tobe'"
                         trigger="click" popper-class="more-dropdown"
                         @visible-change="(v) => { row.__moreOpen = v }"
                         @command="(cmd) => onRowCommand(cmd, row)">
              <span class="more-trigger" :class="{ open: row.__moreOpen }">
                <el-icon :size="12"><MoreFilled /></el-icon>
                <span>更多</span>
                <el-icon :size="10" class="more-arrow"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.id && row.status === 0 && row.phase !== 'done'" command="adjust" class="more-item">
                    <span class="mi mi-purple"><el-icon :size="13"><EditPen /></el-icon></span>
                    <span class="mi-txt"><b>调时</b><i>调整上课时间</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.id && row.status === 0 && row.phase !== 'done'" command="cancel" class="more-item">
                    <span class="mi mi-orange"><el-icon :size="13"><CircleClose /></el-icon></span>
                    <span class="mi-txt"><b>取消</b><i>取消该课次</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.id && row.status === 0 && row.phase === 'done'" command="attend" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><CircleCheck /></el-icon></span>
                    <span class="mi-txt"><b>到课</b><i>登记并扣课时</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.id && row.status === 0 && row.phase === 'done'" command="teacherLeave" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><User /></el-icon></span>
                    <span class="mi-txt"><b>教师请假</b><i>教师缺课不扣课时</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.id && row.status === 0 && row.phase === 'done'" command="studentLeave" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><UserFilled /></el-icon></span>
                    <span class="mi-txt"><b>学生请假</b><i>学生缺课不扣课时</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.id && row.status === 0 && row.phase === 'done'" command="absent" class="more-item">
                    <span class="mi mi-orange"><el-icon :size="13"><Warning /></el-icon></span>
                    <span class="mi-txt"><b>旷课</b><i>72小时未批自动扣课</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.id && row.status === 2" command="compensate" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><Clock /></el-icon></span>
                    <span class="mi-txt"><b>补课</b><i>另排时间补课</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.phase === 'tobe'" command="goSchedule" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><Calendar /></el-icon></span>
                    <span class="mi-txt"><b>去排课</b><i>到周视图排课</i></span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <!-- ========== 排课历史 TAB：只读操作记录 ========== -->
    <div v-show="viewTab === 'history'" class="panel tabs-fill" style="padding: 12px 16px; display: flex; flex-direction: column">
      <div class="toolbar">
        <el-select v-model="filters.teacherId" placeholder="教师" clearable filterable style="width: 140px" @change="onHistoryFilter">
          <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="filters.orgId" placeholder="校区" clearable filterable style="width: 140px" @change="onHistoryFilter">
          <el-option v-for="o in orgList" :key="o.id" :label="o.name" :value="o.id" />
        </el-select>
        <el-select v-model="historyOpType" placeholder="操作类型" clearable filterable style="width: 130px" @change="onHistoryFilter">
          <el-option v-for="t in HISTORY_OP_TYPES" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="historyStatus" placeholder="记录状态" clearable filterable style="width: 140px" @change="onHistoryFilter">
          <el-option v-for="s in HISTORY_STATUSES" :key="s" :label="s" :value="s" />
        </el-select>
        <el-date-picker v-model="historyRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px" @change="onHistoryFilter" />
        <el-button @click="onHistoryFilter">刷新</el-button>
        <div style="flex: 1"></div>
        <el-button @click="doExportHistory">导出历史</el-button>
      </div>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="historyRows" v-loading="historyLoading" stripe size="small" style="flex: 1; overflow-y: auto" @sort-change="onHistorySort">
        <el-table-column type="index" label="序号" width="60" :index="historySeq" />
        <el-table-column label="操作类型" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="opTypeTag(row.opType)" effect="light">{{ row.opType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" label="教师" width="90"  show-overflow-tooltip/>
        <el-table-column prop="courseName" label="课程" min-width="150"  show-overflow-tooltip/>
        <el-table-column prop="time" label="上课时间" width="170" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.time || '-' }}</template>
        </el-table-column>
        <el-table-column prop="studentName" label="学员" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.studentName || '-' }}</template>
        </el-table-column>
        <el-table-column label="课时" width="80" show-overflow-tooltip>
          <template #default="{ row }">{{ row.hours ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="classroom" label="教室" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.classroom || '-' }}</template>
        </el-table-column>
        <el-table-column label="记录状态" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.recordStatus || '-' }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operator || '-' }}</template>
        </el-table-column>
        <el-table-column prop="opTime" label="操作时间" width="170" sortable="custom"  show-overflow-tooltip/>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="historyPager.total"
                       v-model:current-page="historyPager.page" v-model:page-size="historyPager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="loadHistory" @size-change="onHistorySizeChange" />
      </div>
    </div>

    <!-- 新增排课 -->
    <!-- 新增排课：类型（班课/一对一）+ 排课方式（自由/重复，重复仅班课）+ 时段预设/自定义 + 助教/主题 -->
    <el-drawer v-model="createVisible" title="新增排课" size="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="类型">
          <el-radio-group v-model="form.mode" @change="onTypeChange">
            <el-radio value="clazz">班课</el-radio>
            <el-radio value="one2one">一对一</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.mode === 'clazz'" label="班级" required>
          <el-select v-model="form.clazzId" style="width: 100%" @change="onClazzChange">
            <el-option v-for="c in clazzes" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="学生" required>
          <el-select v-model="form.studentId" filterable style="width: 100%">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排课方式">
          <el-radio-group v-model="form.schedMode">
            <el-radio-button value="free">自由排课</el-radio-button>
            <el-radio-button v-if="form.mode === 'clazz'" value="repeat">重复排课</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <!-- 自由排课：单次日期；重复排课：开始/结束方式/重复方式/周几/节假日过滤 -->
        <template v-if="form.schedMode === 'free'">
          <el-form-item label="上课日期" required>
            <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD"
                            :disabled-date="disablePastDate" style="width: 100%" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="开始日期" required>
            <el-date-picker v-model="form.beginDate" type="date" value-format="YYYY-MM-DD"
                            :disabled-date="disablePastDate" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束方式" required>
            <div style="display: flex; align-items: center; gap: 8px; width: 100%">
              <el-select v-model="form.endMode" style="width: 120px">
                <el-option label="按日期结束" value="date" />
                <el-option label="按次数结束" value="count" />
              </el-select>
              <el-date-picker v-if="form.endMode !== 'count'" v-model="form.endDate" type="date"
                              value-format="YYYY-MM-DD" :disabled-date="disablePastDate" style="flex: 1" />
              <el-input-number v-else v-model="form.endCount" :min="1" :max="200" :controls="false" style="width: 110px" />
              <span v-if="form.endMode === 'count'" style="font-size: 13px; color: #667c99">次</span>
            </div>
            <div v-if="attPreview && attPreview.count" style="width: 100%; margin-top: 4px; font-size: 12px; color: #667c99">
              预计排课 <b style="color: #f79009; padding: 0 2px">{{ attPreview.count }}</b> 次<template v-if="attPreview.holidaySkipped">（节假日跳过 {{ attPreview.holidaySkipped }} 次）</template><template v-if="form.endMode === 'count' && attPreview.lastDate">，最后一次 {{ attPreview.lastDate }}</template>
            </div>
          </el-form-item>
          <el-form-item label="重复方式" required>
            <el-radio-group v-model="form.repeatMode">
              <el-radio :value="1">每周重复</el-radio>
              <el-radio :value="2">隔周重复</el-radio>
              <el-radio :value="4">隔天重复</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.repeatMode !== 4" label="周几上课" required>
            <el-checkbox :model-value="(form.weekdays || []).length === 7" style="margin-right: 4px"
                         @change="(v) => (form.weekdays = v ? [1,2,3,4,5,6,7] : [])">全选</el-checkbox>
            <el-checkbox-group v-model="form.weekdays">
              <el-checkbox v-for="(w, i) in WEEK_NAMES" :key="w" :value="i + 1">{{ w }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="节假日过滤">
            <el-radio-group v-model="form.skipHoliday">
              <el-radio :value="true">节假日不排课</el-radio>
              <el-radio :value="false">节假日排课</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>
        <!-- 共用：时段（预设/自定义）+ 教师 + 助教 + 教室 + 主题 -->
        <el-form-item label="上课时段" required>
          <el-select v-if="!attCustomRange" v-model="attPreset" style="width: 160px" @change="(p) => { const [a, b] = String(p).split('~'); form.range = [a, b] }">
            <el-option v-for="p in TIME_PRESETS" :key="p" :label="p" :value="p" />
          </el-select>
          <el-time-picker v-else v-model="form.range" is-range range-separator="至"
                          start-placeholder="开始" end-placeholder="结束"
                          format="HH:mm" value-format="HH:mm" style="width: 200px" />
          <el-button link type="primary" style="margin-left: 8px" @click="toggleAttCustom">
            {{ attCustomRange ? '选择预设时段' : '自定义时段' }}
          </el-button>
        </el-form-item>
        <el-form-item label="教师" required>
          <el-select v-model="form.teacherId" filterable style="width: 100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="助教">
          <el-select v-model="form.assistantId" clearable filterable placeholder="选填" style="width: 100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教室" required>
          <el-select v-model="form.classroomId" filterable style="width: 100%"
                     placeholder="选择教室（显示可容纳人数）" @change="onRoomChange">
            <el-option v-for="r in classrooms" :key="r.id"
                       :label="`${r.name}（容纳${r.capacity}人）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="上课主题">
          <el-input v-model="form.topic" maxlength="20" placeholder="选填，最多20字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCreate">保存</el-button>
      </template>
    </el-drawer>

    <!-- 框选空白：确认创建排课（班级排课 / 一对一排课 二选一） -->
    <el-dialog v-model="pickVisible" title="创建排课" width="420px" append-to-body>
      <div class="pk-time">
        <i class="el-icon"><el-icon><Calendar /></el-icon></i>
        已选时段：<b>{{ pickForm.date }} {{ pickForm.range[0] }}~{{ pickForm.range[1] }}</b>，是否创建排课？
      </div>
      <div class="pk-btns">
        <div class="pk-btn" @click="pickCreate('clazz')">
          <el-icon :size="22" color="#155EEF"><School /></el-icon>
          <b>班级排课</b><span>选班级批量排课</span>
        </div>
        <div class="pk-btn" @click="pickCreate('one2one')">
          <el-icon :size="22" color="#12b76a"><User /></el-icon>
          <b>一对一排课</b><span>按学生/老师排课</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="pickVisible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 课程表弹窗：排课信息 + 编辑单次/编辑重复/编辑上课/删除 -->
    <el-dialog v-model="lessonVisible" title="课程表" width="660px" append-to-body>
      <div v-if="lessonRow" class="ls-info">
        <div class="ls-row"><label>上课时间：</label>{{ String(lessonRow.startTime).slice(0, 16) }} ~ {{ String(lessonRow.endTime || '').slice(11, 16) }}</div>
        <div class="ls-row"><label>班&nbsp;&nbsp;&nbsp;&nbsp;级：</label>
          <span class="ls-link">{{ lessonRow.clazzId ? clazzName(lessonRow.clazzId) : `一对一 · ${studentName(lessonRow.studentId)}` }}</span>
        </div>
        <div class="ls-row"><label>教&nbsp;&nbsp;&nbsp;&nbsp;师：</label>{{ teacherName(lessonRow.teacherId) }}</div>
        <div class="ls-row"><label>助&nbsp;&nbsp;&nbsp;&nbsp;教：</label>{{ lessonRow.assistantId ? teacherName(lessonRow.assistantId) : '—' }}</div>
        <div class="ls-row"><label>教&nbsp;&nbsp;&nbsp;&nbsp;室：</label>{{ lessonRow.classroom || '—' }}</div>
        <div class="ls-row"><label>上课主题：</label>{{ lessonRow.topic || '—' }}</div>
        <div class="ls-row"><label>实到 / 应到：</label>
          <b v-if="lessonAttend" style="color: #12b76a">{{ lessonAttend.attend }}</b>
          <span v-else>-</span> / <b style="color: #155EEF">{{ lessonAttend?.should ?? '-' }}</b>
        </div>
        <div class="ls-row"><label>课次状态：</label>{{ SCH_STATUS[lessonRow.status]?.label || lessonRow.status }}{{ lessonRow.schedType === 1 ? '（重复排课）' : '' }}</div>
      </div>
      <template #footer>
        <div class="ls-footer">
          <el-button @click="lessonVisible = false">取 消</el-button>
          <el-button v-if="lessonRow?.status === 0" @click="lessonVisible = false; openAdjust(lessonRow)">编辑单次</el-button>
          <el-button v-if="lessonRow?.status === 0 && lessonRow?.schedType === 1" @click="openRepeatEdit(lessonRow)">编辑重复</el-button>
          <el-button v-if="lessonRow?.status === 0 || lessonRow?.status === 2" type="danger" plain
                     @click="lessonVisible = false; slotDelete(lessonRow)">删 除</el-button>
          <el-button v-if="lessonRow?.status === 0" type="primary"
                     @click="lessonVisible = false; openMarkAtt(lessonRow)">编辑上课</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 编辑重复系列：统一改时段（可换教师/教室），冲突课次跳过 -->
    <el-dialog v-model="repVisible" title="编辑重复（同步本系列未来课次）" width="460px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="新时段" required>
          <el-time-picker v-model="repForm.range" is-range range-separator="至"
                          start-placeholder="开始" end-placeholder="结束"
                          format="HH:mm" value-format="HH:mm" style="width: 210px" />
        </el-form-item>
        <el-form-item label="教师">
          <el-select v-model="repForm.teacherId" filterable clearable style="width: 100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教室" required>
          <el-select v-model="repForm.classroomId" filterable style="width: 100%">
            <el-option v-for="r in classrooms" :key="r.id"
                       :label="`${r.name}（容纳${r.capacity}人）`" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="rec-hint" style="color: #8fa8c4; font-size: 12px">
        将同步修改同班级+同教师+同周几+同时段的全部未来待上课课次；逐个校验教师/教室冲突，冲突的跳过并汇总提示
      </div>
      <template #footer>
        <el-button @click="repVisible = false">取 消</el-button>
        <el-button type="primary" :loading="repSaving" @click="saveRepeatEdit">同步修改</el-button>
      </template>
    </el-dialog>

    <!-- 编辑上课（课程表弹窗/时段浮窗进入）：整页抽屉——头部信息章 + 上课信息 + 学员逐个登记（参考 schoolpal editTeachingLog） -->
    <el-drawer v-model="attDrawerVisible" title="编辑上课" size="960px" append-to-body>
      <div class="rah-header">
        <div class="rah-chip"><span class="rah-ic rah-ic-blue"><el-icon :size="13"><OfficeBuilding /></el-icon></span>{{ attRow ? (attRow.clazzId ? clazzName(attRow.clazzId) : '一对一') : '-' }}</div>
        <div class="rah-chip"><span class="rah-ic rah-ic-purple"><el-icon :size="13"><Location /></el-icon></span>{{ attCampus || '-' }}</div>
        <div class="rah-chip"><span class="rah-ic rah-ic-green"><el-icon :size="13"><Notebook /></el-icon></span>{{ attCourseName || '-' }}</div>
      </div>

      <div class="rah-sec">
        <div class="rah-title">上课信息</div>
        <el-form label-width="80px" label-position="left">
          <div class="rah-row">
            <el-form-item label="上课日期" required>
              <el-date-picker v-model="attForm.date" type="date" value-format="YYYY-MM-DD"
                              :disabled="attPast" style="width: 100%" />
            </el-form-item>
            <el-form-item label="上课时段" required>
              <el-select v-if="!attEditCustom" v-model="attEditPreset" :disabled="attPast" style="width: 150px" @change="onAttEditPreset">
                <el-option v-for="p in TIME_PRESETS" :key="p" :label="p" :value="p" />
              </el-select>
              <el-time-picker v-else v-model="attForm.range" is-range range-separator="至" :disabled="attPast"
                              start-placeholder="开始" end-placeholder="结束"
                              format="HH:mm" value-format="HH:mm" style="width: 190px" />
              <el-button link type="primary" style="margin-left: 8px" :disabled="attPast" @click="toggleAttEditCustom">
                {{ attEditCustom ? '选择预设时段' : '自定义时段' }}
              </el-button>
            </el-form-item>
            <el-form-item label="上课教师" required>
              <el-select v-model="attForm.teacherId" filterable clearable style="width: 100%">
                <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
          </div>
          <div class="rah-row">
            <el-form-item label="上课教室">
              <el-select v-model="attForm.classroomId" filterable clearable style="width: 100%">
                <el-option v-for="r in classrooms" :key="r.id"
                           :label="`${r.name}（容纳${r.capacity}人）`" :value="r.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="助教">
              <el-select v-model="attForm.assistantId" clearable filterable placeholder="选填" style="width: 100%">
                <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="上课主题">
              <el-input v-model="attForm.topic" maxlength="50" placeholder="选填" />
            </el-form-item>
          </div>
          <el-form-item label="备注">
            <el-input v-model="attForm.memo" type="textarea" :rows="2" maxlength="150" show-word-limit
                      placeholder="选填，最多150字，保存后记入课次备注（上课内容）" />
          </el-form-item>
          <div v-if="attPast" class="rec-hint">该课次已到/过上课时间：日期、时段、教室不可修改，仍可调整教师/助教/主题并登记出勤</div>
        </el-form>
      </div>

      <div class="rah-sec">
        <div class="rah-title">学员记上课</div>
        <div class="rah-stat">
          共 <b>{{ attStudents.length }}</b> 名学员，到课 <b style="color:#12b76a">{{ attCnt(1) }}</b> 人，
          请假 <b style="color:#f79009">{{ attCnt(2) }}</b> 人，旷课 <b style="color:#f04438">{{ attCnt(3) }}</b> 人，
          未记录 <b>{{ attCnt(0) }}</b> 人，已锁定 <b style="color:#155EEF">{{ attLocked }}</b> 人（行内「保存」锁定本行、「取消」还原，底部「保 存」一次性提交全部）
        </div>
        <div class="rah-toolbar">
          <el-button type="primary" size="small" @click="attBatchAttend">批量到课</el-button>
          <el-button size="small" @click="openAttPicker('temp')">添加临时学员</el-button>
          <el-button v-if="attRow?.clazzId" size="small" @click="openAttPicker('makeup')">添加补课学员</el-button>
          <span class="rec-hint" style="margin-left: 10px">临时学员到课不扣课时；补课学员按本人合同扣课</span>
          <el-input v-model="attSearch" size="small" placeholder="请输入学员姓名" clearable style="width: 200px; margin-left: auto">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <el-table :data="attStudentsFiltered" v-loading="attLoading" stripe size="small" max-height="360"
                  @selection-change="(v) => (attChecked = v)">
          <el-table-column type="selection" width="42" />
          <el-table-column label="学员头像" width="70" align="center">
            <template #default="{ row }">
              <span class="att-avatar" :style="{ background: attAvatarColor(row.name) }">{{ String(row.name || '?').slice(0, 1) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="学员姓名" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="att-stu">
                <span class="att-name">{{ row.name }}</span>
                <el-tag v-if="row.kind === 'makeup'" size="small" type="warning" effect="plain">补课</el-tag>
                <el-tag v-else-if="row.kind === 'temp'" size="small" type="info" effect="plain">临时</el-tag>
                <el-tag v-if="row.__saved" size="small" type="success" effect="plain">已锁定</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="学员状态" width="82" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.kind" size="small" type="info" effect="plain">-</el-tag>
              <el-tag v-else size="small" type="success" effect="plain">{{ row.status === 1 ? '在读' : row.statusText || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="剩余课时/天数" width="104" align="center">
            <template #default="{ row }">
              <b v-if="row.kind !== 'temp'" :style="{ color: Number(row.remain) > 0 ? '#12b76a' : '#f04438' }">{{ row.remain ?? '-' }}</b>{{ row.kind !== 'temp' ? (row.unit || '课时') : '' }}
              <span v-if="row.kind === 'temp'" class="rec-hint">不扣课</span>
            </template>
          </el-table-column>
          <el-table-column label="有效课时" width="82" align="center">
            <template #default="{ row }">
              <span v-if="row.kind !== 'temp'" :style="{ color: Number(row.remain) > 0 ? '#12b76a' : '#98a2b3' }">{{ row.remain ?? '-' }}</span>
              <span v-else class="rec-hint">-</span>
            </template>
          </el-table-column>
          <el-table-column label="请假次数" width="82" align="center">
            <template #default="{ row }">{{ row.kind ? '-' : row.leaveCount }}</template>
          </el-table-column>
          <el-table-column label="本次扣课时" width="122">
            <template #default="{ row }">
              <el-input-number v-if="row.kind !== 'temp'" v-model="row.hours" :min="1" :max="10" size="small"
                               :disabled="row.__saved || row.att !== 1" style="width: 100px" />
              <span v-else class="rec-hint">不扣课</span>
            </template>
          </el-table-column>
          <el-table-column label="到课状态" width="208">
            <template #default="{ row }">
              <el-radio-group v-if="row.kind !== 'temp'" v-model="row.att" size="small" :disabled="row.__saved">
                <el-radio-button :value="1">到课</el-radio-button>
                <el-radio-button :value="2">请假</el-radio-button>
                <el-radio-button :value="3">旷课</el-radio-button>
              </el-radio-group>
              <el-tag v-else size="small" type="success" effect="plain">到课（临时·旁听）</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="到课类型" width="76" align="center">
            <template #default="{ row }">
              <el-checkbox v-if="row.kind !== 'temp'" v-model="row.late" :disabled="row.__saved">迟到</el-checkbox>
              <span v-else class="rec-hint">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="118" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.kind !== 'temp' && !row.__saved" link type="primary" size="small" @click="lockAttRow(row)">保存</el-button>
              <el-button v-if="row.kind !== 'temp' && row.__saved" link type="warning" size="small" @click="resetAttRow(row)">取消</el-button>
              <el-button link type="danger" size="small" @click="removeAttRow(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!attStudentsFiltered.length && !attLoading" description="该课次无学员（班课学员在班级管理添加，或点上方按钮添加临时/补课学员）" :image-size="60" />
      </div>

      <template #footer>
        <el-button @click="attDrawerVisible = false">取 消</el-button>
        <el-button type="primary" :loading="attSaving" @click="saveMarkAtt">保 存</el-button>
      </template>
    </el-drawer>

    <!-- 添加临时/补课学员（编辑上课）：临时=全库搜索；补课=同课程已分班的请假学员 -->
    <el-dialog v-model="attPickVisible" :title="attPickKind === 'temp' ? '添加临时学员' : '添加补课学员'" width="760px" append-to-body>
      <template v-if="attPickKind === 'temp'">
        <div class="rec-hint" style="margin-bottom: 8px">临时学员：到课不扣课时，名单记入课次备注（出勤统计不含）</div>
        <el-input v-model="attPickKw" size="small" placeholder="输入学员姓名 / 手机号搜索" clearable style="margin-bottom: 8px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-table :data="attPickFiltered" stripe size="small" max-height="300" @selection-change="(v) => (attPickSel = v)">
          <el-table-column type="selection" width="42" />
          <el-table-column label="学员姓名" min-width="120">
            <template #default="{ row }">{{ row.name }}</template>
          </el-table-column>
          <el-table-column label="手机号" width="140">
            <template #default="{ row }">{{ row.phone || '-' }}</template>
          </el-table-column>
        </el-table>
      </template>
      <template v-else>
        <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 10px"
                  :title="`当前课程：${attCourseName || '-'}；仅支持添加同课程已分班的请假学员`" />
        <div class="rah-mk-filters">
          <span class="rah-mk-lb">报读校区：</span>
          <el-select v-model="attMkCampus" size="small" style="width: 160px" @change="attMkClazz = null">
            <el-option label="全部校区" value="" />
            <el-option v-for="c in attMkCampusOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <span class="rah-mk-lb" style="margin-left: 12px">班级名称：</span>
          <el-select v-model="attMkClazz" size="small" clearable placeholder="请选择班级" style="width: 170px">
            <el-option v-for="c in attMkClazzOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-input v-model="attMkKw" size="small" placeholder="请输入学员姓名" clearable style="width: 180px; margin-left: 12px">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <div class="rah-stat" style="margin: 8px 0">共 <b>{{ attMkFiltered.length }}</b> 名学员</div>
        <el-table :data="attMkFiltered" stripe size="small" max-height="280" @selection-change="(v) => (attPickSel = v)">
          <el-table-column type="selection" width="42" />
          <el-table-column label="学员姓名" min-width="110">
            <template #default="{ row }">{{ row.stuName }}</template>
          </el-table-column>
          <el-table-column label="报读校区" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.campus }}</template>
          </el-table-column>
          <el-table-column label="班级名称" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.clazzName }}</template>
          </el-table-column>
          <el-table-column label="请假时间" width="130">
            <template #default="{ row }">{{ row.leaveTime }}</template>
          </el-table-column>
          <el-table-column label="教师" width="90" show-overflow-tooltip>
            <template #default="{ row }">{{ teacherName(row.teacherId) }}</template>
          </el-table-column>
          <el-table-column label="扣除课时" width="96" align="center">
            <template #default>按本人合同</template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="attPickVisible = false">取 消</el-button>
        <el-button type="primary" @click="confirmAttPick">加 入</el-button>
      </template>
    </el-dialog>

    <!-- 教师共同空闲查询（原"教师空闲查询"菜单收编）：多师交集 + 禁排时段感知，点空闲段直接带入排课 -->
    <el-drawer v-model="freeVisible" title="教师共同空闲查询" size="640px">
      <div class="ft-bar">
        <el-date-picker v-model="freeRange" type="daterange" value-format="YYYY-MM-DD"
                        range-separator="~" start-placeholder="开始日期" end-placeholder="结束日期"
                        style="width: 260px" />
        <el-select v-model="freeTeacherIds" multiple collapse-tags filterable
                   placeholder="选择多位教师（取共同空闲）" style="width: 260px">
          <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="freePeriod" placeholder="时段" clearable style="width: 100px">
          <el-option label="上午" :value="1" /><el-option label="下午" :value="2" /><el-option label="晚上" :value="3" />
        </el-select>
        <el-button type="primary" :loading="freeLoading" @click="queryFree">查询</el-button>
      </div>
      <el-alert type="info" :closable="false" style="margin: 10px 0"
                title="空闲 = 工作时段(08:00-21:00) - 教师固定禁排时段 - 已有排课；结果为所选教师的共同空闲交集" />
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="freeRows" v-loading="freeLoading" stripe size="small">
        <el-table-column type="index" label="序号" width="55" />
        <el-table-column prop="date" label="空闲日期" width="110"  show-overflow-tooltip/>
        <el-table-column label="空闲时段" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag effect="light" size="small">{{ row.start }} ~ {{ row.end }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="空闲教师" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.teachers || []).join('、') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="applyFree(row)">排到此处</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!freeRows.length && !freeLoading" description="选择教师后查询共同空闲" :image-size="70" />
    </el-drawer>

    <!-- 调时/补课 -->
    <el-dialog v-model="timeVisible" :title="timeMode === 'adjust' ? '调整上课时间' : '安排补课'" width="430px">
      <el-form label-width="90px">
        <el-form-item label="开始时间">
          <el-date-picker v-model="timeForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          :disabled-date="disablePastDate" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="timeForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          :disabled-date="disablePastDate" />
        </el-form-item>
        <el-form-item label="教室">
          <el-select v-model="timeForm.classroomId" filterable placeholder="选择教室（可选）" style="width: 100%">
            <el-option v-for="r in classrooms" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="timeMode === 'adjust'" label="调时原因"><el-input v-model="timeForm.reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="timeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTime">确定</el-button>
      </template>
    </el-dialog>

    <!-- 到课登记：扣课节数=排课时长÷每节时长折算，扣减少于应扣须填说明 -->
    <el-dialog v-model="attendVisible" title="到课登记" width="460px">
      <el-alert type="warning" :closable="false" show-icon
                title="确认为到课并扣减课时，请选择本次扣课节数"
                :description="`本课次时长 ${attendForm.minutes ?? '-'} 分钟，课程每节 ${attendForm.durationMinutes ?? '-'} 分钟，最多可扣 ${attendForm.maxHours ?? '-'} 节。扣课将同步记入课时管理，并累计到学员合同已消课时。`" />
      <el-form label-width="90px" style="margin-top: 16px">
        <el-form-item label="扣课节数">
          <el-radio-group v-model="attendForm.hours">
            <el-radio-button v-for="n in attendForm.maxHours" :key="n" :value="n">{{ n }} 节</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="attendForm.hours < attendForm.maxHours" label="扣课说明" required>
          <el-input v-model="attendForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="本次扣课少于本课次应扣节数，请说明原因（如：提前下课 / 部分内容未上完等）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="attendVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAttend">确认扣课</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'
import { exportXlsx } from '../../utils/export'

/* ===== 一级视图与筛选 ===== */
const viewTab = ref('week') // day / week / month / list / history

/** 日/周/月日历视图可见性（非列表、非历史即为日历视图） */
const isCalView = computed(() => ['day', 'week', 'month'].includes(viewTab.value))
const dateRange = ref(null)
const historyRange = ref(null)
const filters = reactive({ teacherId: null, clazzId: null, status: null, orgId: null })
const phase = ref('tobe')
const counts = reactive({ tobe: 0, today: 0, ongoing: 0, done: 0 })

const teachers = ref([])
const clazzes = ref([])
const students = ref([])
const courses = ref([])
const orgList = ref([])
const classrooms = ref([])
const teacherName = (id) => teachers.value.find((t) => t.id === id)?.name || '-'
const clazzName = (id) => clazzes.value.find((c) => c.id === id)?.name || '-'
const studentName = (id) => students.value.find((s) => s.id === id)?.name || '-'
/** 教师信息（课次展开用）：姓名 + 联系电话 */
const teacherInfo = (id) => {
  const t = teachers.value.find((x) => x.id === id)
  return t ? t.name + (t.phone ? `（${t.phone}）` : '') : '-'
}
/** 学生信息（一对一课次展开用）：姓名 + 家长电话 */
const studentInfo = (id) => {
  const s = students.value.find((x) => x.id === id)
  return s ? s.name + (s.parentPhone ? `（家长 ${s.parentPhone}）` : '') : '-'
}
/** 班级信息（课次展开用）：课程 / 班主任 */
const courseNameOf = (clazzId) => {
  const c = clazzes.value.find((x) => x.id === clazzId)
  return (c && courses.value.find((x) => x.id === c.courseId)?.name) || '-'
}
const clazzTeacherOf = (clazzId) =>
  teacherName(clazzes.value.find((x) => x.id === clazzId)?.classTeacherId)

/* ===== 列表 TAB（4个阶段子TAB，后端 phase 过滤保持一致） ===== */
const listQuery = reactive({ keyword: '', page: 1 })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => {
    const p = { ...q, phase: phase.value, teacherId: filters.teacherId,
      clazzId: filters.clazzId, orgId: filters.orgId,
      begin: dateRange.value?.[0], end: dateRange.value?.[1] }
    delete p.status
    return api.schedulePage(p)
  }, listQuery)

function onPhaseChange() { listQuery.page = 1; load() }

/* 阶段标签（后端按系统时间推导：tobe待排课/today待上课/ongoing上课中/done已下课） */
const phaseMeta = {
  tobe: { label: '待排课', type: 'info' },
  today: { label: '待上课', type: 'warning' },
  ongoing: { label: '上课中', type: 'primary' },
  done: { label: '已下课', type: 'success' },
  canceled: { label: '已取消', type: 'danger' }
}
/* 已下课登记结果：0未登记 1已到课 3教师请假 4学生请假 5旷课待审批 6旷课已免扣 7旷课已扣课 */
const outcomeMeta = {
  0: { label: '未登记', type: 'info' },
  1: { label: '已到课·已扣课', type: 'success' },
  3: { label: '教师请假', type: 'warning' },
  4: { label: '学生请假', type: 'warning' },
  5: { label: '旷课·待审批', type: 'danger' },
  6: { label: '旷课·已免扣', type: 'success' },
  7: { label: '旷课·已扣课', type: 'danger' }
}

/** 待排课任务 → 跳到周视图日历排课，并带入教师筛选 */
function goSchedule(row) {
  if (row.teacherId) filters.teacherId = row.teacherId
  viewTab.value = 'week'
  ElMessage.info('已在周视图中，点击/拖选时间段即可排课')
}

/** 列表行「更多」下拉分发：命令 → 原按钮函数（显示条件在各下拉项 v-if 上） */
function onRowCommand(cmd, row) {
  if (cmd === 'adjust') openAdjust(row)
  else if (cmd === 'cancel') doCancel(row)
  else if (cmd === 'attend') openAttend(row)
  else if (cmd === 'teacherLeave') doLeave(row, 3)
  else if (cmd === 'studentLeave') doLeave(row, 4)
  else if (cmd === 'absent') doAbsent(row)
  else if (cmd === 'compensate') openCompensate(row)
  else if (cmd === 'goSchedule') goSchedule(row)
}

/* ===== 教师共同空闲查询（原独立菜单收编为排课辅助工具） ===== */
const freeVisible = ref(false)
const freeLoading = ref(false)
const freeRange = ref([new Date().toISOString().slice(0, 10),
  new Date(Date.now() + 6 * 86400000).toISOString().slice(0, 10)])
const freeTeacherIds = ref([])
const freePeriod = ref(null)
const freeRows = ref([])

function openFreeTime() { freeVisible.value = true }

async function queryFree() {
  if (!freeTeacherIds.value.length) return ElMessage.warning('请至少选择一名教师')
  if (!freeRange.value || freeRange.value.length !== 2) return ElMessage.warning('请选择日期范围')
  freeLoading.value = true
  try {
    freeRows.value = (await api.teacherFreeTime({
      begin: freeRange.value[0], end: freeRange.value[1],
      period: freePeriod.value, teacherIds: freeTeacherIds.value
    })) || []
    if (!freeRows.value.length) ElMessage.info('所选时间段内没有共同空闲时段')
  } finally { freeLoading.value = false }
}

/** 排到此处：日历定位到该日期，新增排课抽屉带入 时间段+教师（单师时） */
function applyFree(row) {
  freeVisible.value = false
  if (viewTab.value !== 'day' && viewTab.value !== 'week' && viewTab.value !== 'month') viewTab.value = 'week'
  nextTick(() => calRef.value?.getApi?.()?.gotoDate(row.date))
  openCreate()
  form.value = {
    ...form.value,
    date: row.date,
    range: [row.start, row.end],
    teacherId: (row.teacherIds || []).length === 1 ? row.teacherIds[0] : undefined
  }
  syncAttRangeInput()
  createVisible.value = true
  ElMessage.success('已带入空闲时段，选择班级/学生与教室后保存')
}

async function loadPhaseCounts() {
  try {
    const d = (await api.schedulePhaseCounts({
      teacherId: filters.teacherId, clazzId: filters.clazzId, orgId: filters.orgId,
      begin: dateRange.value?.[0], end: dateRange.value?.[1]
    })) || {}
    Object.assign(counts, d)
  } catch (e) { /* ignore */ }
}
watch([phase, filters.teacherId, filters.clazzId, filters.orgId], () => loadPhaseCounts())

/** 查询：列表与阶段徽标一起刷新（口径一致） */
function doSearch() { search(); loadPhaseCounts() }

/* ===== 排课历史 TAB（分页 + 排序） ===== */
const historyRows = ref([])
const historyLoading = ref(false)
const historyPager = reactive({ page: 1, size: 10, total: 0 })
const historySort = reactive({ sortBy: '', direction: '' })
const historySeq = (i) => (historyPager.page - 1) * historyPager.size + i + 1
/** 操作类型筛选：排课日志（排课/调课/取消/删除/补课/请假/旷课）+ 消课台账（消课/撤销消课） */
const historyOpType = ref('')
const historyStatus = ref('')
const HISTORY_OP_TYPES = ['排课', '调课', '取消排课', '删除排课', '补课', '教师请假', '学生请假', '旷课登记', '消课', '撤销消课']
/** 记录状态筛选：课次当前状态 + 已删除 + 消课审核状态 */
const HISTORY_STATUSES = ['待上课', '已到课', '已取消', '教师请假', '学生请假',
  '旷课·待审批', '旷课·已免扣', '旷课·已扣课', '已删除', '已审核', '待审核', '已驳回']

async function loadHistory() {
  historyLoading.value = true
  try {
    const d = (await api.scheduleHistory({
      page: historyPager.page, size: historyPager.size,
      sortBy: historySort.sortBy || undefined,
      direction: historySort.sortBy ? historySort.direction : undefined,
      teacherId: filters.teacherId, orgId: filters.orgId,
      opType: historyOpType.value || undefined,
      recordStatus: historyStatus.value || undefined,
      begin: historyRange.value?.[0], end: historyRange.value?.[1]
    })) || {}
    historyRows.value = d.records || []
    historyPager.total = d.total || 0
  } finally { historyLoading.value = false }
}
/** 筛选变化：回到第一页再查 */
function onHistoryFilter() { historyPager.page = 1; loadHistory() }
/** 表头排序（服务端：opTime 操作时间 / time 上课时间） */
function onHistorySort({ prop, order }) {
  historySort.sortBy = order ? prop : ''
  historySort.direction = order === 'ascending' ? 'asc' : 'desc'
  historyPager.page = 1
  loadHistory()
}
function onHistorySizeChange() { historyPager.page = 1; loadHistory() }
watch(historyRange, onHistoryFilter)
function opTypeTag(t) {
  return { '排课': 'primary', '调课': 'warning', '取消排课': 'danger', '删除排课': 'info', '补课': 'info',
           '教师请假': 'warning', '学生请假': 'warning', '旷课登记': 'danger',
           '消课': 'success', '撤销消课': 'info' }[t] || 'info'
}
function doExportHistory() {
  exportXlsx('排课历史', [
    { label: '操作类型', prop: 'opType' }, { label: '教师', prop: 'teacherName' },
    { label: '课程', prop: 'courseName' }, { label: '上课时间', prop: 'time' },
    { label: '学员', prop: 'studentName' },
    { label: '课时', prop: 'hours' }, { label: '教室', prop: 'classroom' },
    { label: '记录状态', prop: 'recordStatus' }, { label: '操作人', prop: 'operator' },
    { label: '操作时间', prop: 'opTime' }
  ], historyRows.value)
}

/* ===== 日历视图（日/周/月共用一个 FullCalendar） ===== */
const calRef = ref()
const rawSchedules = ref([])
/** 教室筛选后的当前展示集（月视图「+N 节」/双击日期的清单口径与日历一致） */
const shownSchedules = ref([])
/** 教室筛选：选中后日历只显示该教室课次（历史课次仅存教室名，按 ID 或名称任一命中） */
const roomFilter = ref('')
watch(roomFilter, () => calRef.value?.getApi?.()?.refetchEvents?.())
const CAL_COLORS = { 0: '#155EEF', 1: '#12b76a', 2: '#c0c4cc', 3: '#f79009', 4: '#fdb022', 5: '#f04438', 6: '#66c555', 7: '#d92d20' }
/* 课次状态（清单抽屉标签；0待上课 1已到课 2已取消 3/4请假 5旷课待审批 6旷课免扣 7旷课已扣） */
const SCH_STATUS = {
  0: { label: '待上课', type: 'primary' }, 1: { label: '已到课', type: 'success' },
  2: { label: '已取消', type: 'info' }, 3: { label: '教师请假', type: 'warning' },
  4: { label: '学生请假', type: 'warning' }, 5: { label: '旷课·待审批', type: 'danger' },
  6: { label: '旷课·已免扣', type: 'success' }, 7: { label: '旷课·已扣课', type: 'danger' }
}

/** 单条课次 → 日历事件（两行式色块：行1 名称，行2 📍教室 · 教师） */
function toCalEvent(r) {
  return {
    id: String(r.id),
    title: (r.clazzId ? clazzName(r.clazzId) : studentName(r.studentId)) + ' · ' + teacherName(r.teacherId)
           + (r.classroom ? ' @' + r.classroom : ''),
    start: r.startTime, end: r.endTime,
    backgroundColor: CAL_COLORS[r.status] || '#155EEF',
    borderColor: 'transparent', editable: r.status === 0,
    extendedProps: {
      status: r.status,
      name: r.clazzId ? clazzName(r.clazzId) : studentName(r.studentId),
      meta: [r.classroom ? '📍' + r.classroom : '', teacherName(r.teacherId)].filter(Boolean).join(' · ')
    }
  }
}

/**
 * 时段成链：同一天内时间上连通重叠（含部分重叠的阶梯链）的课次为一组。
 */
function groupOverlapChains(records) {
  const byDay = new Map()
  for (const r of records) {
    const d = String(r.startTime || '').slice(0, 10)
    if (!byDay.has(d)) byDay.set(d, [])
    byDay.get(d).push(r)
  }
  const groups = []
  for (const arr of byDay.values()) {
    arr.sort((a, b) => String(a.startTime).localeCompare(String(b.startTime)))
    let chain = [], chainEnd = ''
    const flush = () => {
      if (chain.length) groups.push({ chain })
      chain = []; chainEnd = ''
    }
    for (const r of arr) {
      // 与当前链无时间交集（开始晚于链最晚结束）则成组结算
      if (chain.length && String(r.startTime) >= chainEnd) flush()
      chain.push(r)
      if (String(r.endTime || '') > chainEnd) chainEnd = String(r.endTime || '')
    }
    flush()
  }
  return groups
}

async function loadCalEvents(info, successCallback, failureCallback) {
  try {
    const d = await api.schedulePage({
      page: 1, size: 500,
      begin: (info.startStr || '').slice(0, 10),
      end: (info.endStr || '').slice(0, 10),
      teacherId: filters.teacherId, clazzId: filters.clazzId, orgId: filters.orgId
    })
    const records = d.records || []
    rawSchedules.value = records
    const room = classrooms.value.find((x) => x.id === roomFilter.value)
    const shown = room ? records.filter((r) => r.classroomId === room.id || r.classroom === room.name) : records
    shownSchedules.value = shown
    // 折叠规则：日/周/月统一 —— 本时段只涉及 1 间教室时按原逻辑逐节直显；
    // 涉及多间教室则整段折叠为「已排 N 间教室」提示块（深灰蓝中性色，不可拖拽），
    // 点击开课次清单抽屉展示该时段全部课次的教室/教师等信息
    const evts = []
    for (const g of groupOverlapChains(shown)) {
      const rooms = [...new Set(g.chain.map((r) => r.classroom).filter(Boolean))]
      if (rooms.length <= 1) {
        for (const r of g.chain) evts.push(toCalEvent(r))
        continue
      }
      const chain = g.chain
      const maxEnd = chain.reduce((m, r) => (String(r.endTime || '') > m ? String(r.endTime || '') : m), '')
      evts.push({
        id: 'b' + evts.length,
        title: `已排 ${rooms.length} 间教室`,
        start: chain[0].startTime, end: maxEnd,
        backgroundColor: '#475469', borderColor: 'transparent', editable: false,
        extendedProps: {
          status: null, // 汇总块不可拖拽、不进调时，点击只开清单抽屉
          bundle: chain,
          range: [chain[0].startTime, maxEnd],
          name: `已排 ${rooms.length} 间教室`,
          meta: `${chain.length} 节课 · 点击查看教室/教师`
        }
      })
    }
    successCallback(evts)
  } catch (e) { console.error('日历课次加载失败:', e); failureCallback(e) }
}

/** 根据色块底色亮度选数字高亮色：深底用亮金、浅底用深橙，保证任何底色下对比突出 */
function numHighlightOn(bg) {
  const m = String(bg || '').replace('#', '')
  if (m.length < 6) return '#FFD666'
  const r = parseInt(m.slice(0, 2), 16), g = parseInt(m.slice(2, 4), 16), b = parseInt(m.slice(4, 6), 16)
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  return lum < 0.5 ? '#FFD666' : '#F79009'
}

/** 色块两行式内容：行1 时间+班级/学生（时间弱化），行2 📍教室 · 教师；窄块省略号截断不换行。
    折叠汇总块的教室数按底色亮度用高亮色呈现（深底亮金/浅底深橙） */
function renderEventContent(arg) {
  const p = arg.event.extendedProps
  const box = document.createElement('div')
  box.className = 'ev2'
  const line1 = document.createElement('div')
  line1.className = 'ev2-n'
  const t = document.createElement('span')
  t.className = 'ev2-t'
  t.textContent = (arg.timeText || '') + ' '
  line1.appendChild(t)
  const m = /^已排 (\d+) 间教室$/.exec(p.name || '')
  if (m) {
    line1.appendChild(document.createTextNode('已排 '))
    const num = document.createElement('span')
    num.className = 'ev2-num'
    num.textContent = m[1]
    num.style.color = numHighlightOn(arg.event.backgroundColor)
    line1.appendChild(num)
    line1.appendChild(document.createTextNode(' 间教室'))
  } else {
    line1.appendChild(document.createTextNode(p.name || arg.event.title))
  }
  const line2 = document.createElement('div')
  line2.className = 'ev2-m'
  line2.textContent = p.meta || ''
  box.appendChild(line1); box.appendChild(line2)
  return { domNodes: [box] }
}

/** 日历 ISO 串（带时区偏移，如 2026-10-29T10:00:00+08:00）→ 后端要求的 yyyy-MM-dd HH:mm:ss */
function fmtLocal(dt) {
  if (!dt) return dt
  const d = new Date(dt)
  if (isNaN(d.getTime())) return dt
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/* ===== 排课时间校验 ===== */
/** 日期面板禁选今天之前的日期（今天内晚于当前的时刻仍可选） */
function disablePastDate(d) {
  const t0 = new Date(); t0.setHours(0, 0, 0, 0)
  return d.getTime() < t0.getTime()
}
/** 开始时间必须晚于当前系统时间；返回错误文案，空串=通过 */
function checkStartFuture(startTime) {
  if (!startTime) return '请选择上课时间'
  if (new Date(startTime.replace(' ', 'T')).getTime() <= Date.now())
    return '开始时间必须晚于当前系统时间，不能选择已过去的时间'
  return ''
}
/** 开始时间距当前不足3小时即开课：弹提示确认（确定=true，取消=false） */
async function confirmUrgentStart(startTime) {
  const ms = new Date(startTime.replace(' ', 'T')).getTime() - Date.now()
  if (ms > 0 && ms < 3 * 60 * 60 * 1000) {
    try {
      await ElMessageBox.confirm(
        '所选开始时间距当前不足 3 小时即开课，请确认教师与学员已知晓。是否继续？',
        '开课时间临近提醒', { type: 'warning', confirmButtonText: '继续排课', cancelButtonText: '返回修改' })
    } catch { return false }
  }
  return true
}

async function onEventDrop(info) {
  const start = fmtLocal(info.event.startStr)
  if (checkStartFuture(start)) { ElMessage.warning(checkStartFuture(start)); return info.revert() }
  if (!(await confirmUrgentStart(start))) return info.revert()
  api.scheduleAdjust(Number(info.event.id), {
    startTime: start, endTime: fmtLocal(info.event.endStr), reason: '日历拖拽调整'
  }).then(() => { ElMessage.success('上课时间已调整'); refreshAll() })
    .catch(() => info.revert())
}

/** 排课数据变更后统一刷新：列表 + 阶段徽标 + 日历课次块（否则新排的课不显示色块） */
function refreshAll() {
  load(); loadPhaseCounts()
  calRef.value?.getApi?.()?.refetchEvents?.()
}

function onEventClick(info) {
  const p = info.event.extendedProps
  // 折叠汇总块：打开该时段的课次清单抽屉
  if (p.bundle) return openSlotDrawer(p.bundle, `${fmtHM(p.range[0])}~${fmtHM(p.range[1])} 时段课次（${p.bundle.length} 节）`)
  // 单个课次块：打开课程表弹窗（排课信息 + 编辑单次/编辑重复/编辑上课/删除）
  const raw = rawSchedules.value.find((r) => String(r.id) === info.event.id)
  if (!raw) return
  openLessonModal(raw)
}

/* ===== 课程表弹窗：排课信息 + 编辑单次/编辑重复/编辑上课/删除 ===== */
const lessonVisible = ref(false)
const lessonRow = ref(null)
const lessonAttend = ref(null)
async function openLessonModal(row) {
  lessonRow.value = row
  lessonAttend.value = null
  lessonVisible.value = true
  try { lessonAttend.value = await api.scheduleAttendSummary(row.id) } catch (e) { /* 忽略 */ }
}

/* ===== 编辑重复系列：统一改时段（可换教师/教室） ===== */
const repVisible = ref(false)
const repSaving = ref(false)
const repRow = ref(null)
const repForm = ref({})
function openRepeatEdit(row) {
  repRow.value = row
  repForm.value = { range: [String(row.startTime).slice(11, 16), String(row.endTime || '').slice(11, 16)],
    teacherId: row.teacherId, classroomId: row.classroomId }
  lessonVisible.value = false
  repVisible.value = true
}
async function saveRepeatEdit() {
  const f = repForm.value
  if (!f.range?.[0] || !f.range?.[1]) return ElMessage.warning('请选择上课时段')
  if (String(f.range[1]) <= String(f.range[0])) return ElMessage.warning('结束时间必须晚于开始时间')
  if (!f.classroomId) return ElMessage.warning('请选择教室')
  repSaving.value = true
  try {
    const r = await api.scheduleRepeatUpdate(repRow.value.id, {
      startTime: `${String(repRow.value.startTime).slice(0, 10)} ${f.range[0]}:00`,
      endTime: `${String(repRow.value.startTime).slice(0, 10)} ${f.range[1]}:00`,
      teacherId: f.teacherId, classroomId: f.classroomId })
    repVisible.value = false
    refreshAll()
    const skipped = r.skipped || []
    ElMessage.success(`已同步更新 ${r.updated}/${r.total} 个课次${skipped.length ? `，跳过 ${skipped.length} 个` : ''}`)
    if (skipped.length) {
      ElMessageBox.alert(skipped.slice(0, 40).map((x) => `${x.date}：${x.reason}`).join('<br>'),
        `跳过明细（共 ${skipped.length} 条）`, { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' })
    }
  } finally { repSaving.value = false }
}

/* ===== 课次清单抽屉：汇总块 / 月视图「+N 节」 / 双击日期 共用 ===== */
const slotVisible = ref(false)
const slotTitle = ref('')
const slotRows = ref([])
const fmtHM = (s) => String(s || '').slice(11, 16)
const toTs = (s) => new Date(String(s || '').replace(' ', 'T')).getTime()
/** 未开始（开始时间在未来）：可调时 */
const canAdjustRow = (r) => toTs(r.startTime) > Date.now()

/* 时段课次多于 10 节分页展示（每页 10 条） */
const SLOT_PAGE_SIZE = 10
const slotPage = ref(1)
const pagedSlotRows = computed(() => {
  const start = (slotPage.value - 1) * SLOT_PAGE_SIZE
  return slotRows.value.slice(start, start + SLOT_PAGE_SIZE)
})

/* 班级在读学员缓存（点击课名展开时按班级懒加载一次） */
const clazzStudentsCache = reactive({})
async function toggleSlot(r) {
  r._open = !r._open
  if (r._open && r.clazzId && !clazzStudentsCache[r.clazzId])
    clazzStudentsCache[r.clazzId] = (await api.scheduleClazzStudents(r.clazzId)) || []
}

function openSlotDrawer(rows, title) {
  slotRows.value = [...rows].sort((a, b) => String(a.startTime).localeCompare(String(b.startTime)))
  slotTitle.value = title
  slotPage.value = 1
  slotVisible.value = true
}

/** 清单内操作：先关抽屉再执行（弹窗/对话框不与抽屉叠层） */
function slotAction(r, fn, ...args) {
  slotVisible.value = false
  fn(r, ...args)
}

/** 清单内删除：成功后就地移除该行（不关抽屉，便于连续处理），并回收越界页码 */
async function slotDelete(r) {
  const ok = await doDeleteSchedule(r.id)
  if (ok) {
    slotRows.value = slotRows.value.filter((x) => x.id !== r.id)
    const maxPage = Math.max(1, Math.ceil(slotRows.value.length / SLOT_PAGE_SIZE))
    if (slotPage.value > maxPage) slotPage.value = maxPage
  }
}

/** 当日课次清单（月视图「+N 节」与双击日期入口），口径与日历当前展示一致 */
function openDayList(dateStr) {
  const rows = shownSchedules.value.filter((r) => String(r.startTime).slice(0, 10) === dateStr)
  openSlotDrawer(rows, `${dateStr} 当日课次（${rows.length} 节）`)
}

/** 月视图「+N 节」：改为打开当日清单抽屉，不用 FullCalendar 自带小弹层 */
function onMoreLinkClick(arg) {
  const d = arg.date
  if (d) {
    const p2 = (n) => String(n).padStart(2, '0')
    openDayList(`${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}`)
  }
  return 'cancel'
}

/** 双击日期（月视图日期格）/ 双击时间列（日/周视图）：当日课次清单 */
function onCalDblClick(e) {
  const date = e.target.closest('[data-date]')?.getAttribute('data-date')
  if (date) openDayList(date)
}

/* ===== 框选空白：确认创建排课，二选一 班级排课 / 一对一（老师）排课 ===== */
const pickVisible = ref(false)
const pickForm = ref({ date: '', range: [] })
function onSelect(info) {
  const start = fmtLocal(info.startStr)
  // 框选已过去的时间段：不弹新增排课，仅提示
  if (new Date(start.replace(' ', 'T')).getTime() <= Date.now()) {
    calRef.value?.getApi?.()?.unselect()
    return ElMessage.warning('所选时间段已过，不能在过去的时间排课')
  }
  const end = fmtLocal(info.endStr)
  pickForm.value = { date: start.slice(0, 10), range: [start.slice(11, 16), end.slice(11, 16)] }
  pickVisible.value = true
}
/** 选定排课类型后打开新增排课抽屉（带入框选日期时段） */
function pickCreate(mode) {
  pickVisible.value = false
  openCreate()
  form.value = { ...form.value, mode, date: pickForm.value.date, range: [...pickForm.value.range] }
  syncAttRangeInput()
  calRef.value?.getApi?.()?.unselect()
}

/* ===== 已过时段标识：过去整天由样式 .fc-day-past 置灰，今天 6:00~当前时间加遮罩 ===== */
function onDatesSet() { nextTick(() => setTimeout(shadePastArea, 50)) }
/** 日/周视图：给今天已过时段（时间轴 6:00~当前时间，按比例）加淡灰遮罩 */
function shadePastArea() {
  const root = document.querySelector('.cal-panel')
  if (!root) return
  root.querySelectorAll('.fc-timegrid-past-overlay').forEach((n) => n.remove())
  const todayCol = root.querySelector('.fc-timegrid-col.fc-day-today')
  const frame = todayCol?.querySelector('.fc-timegrid-col-frame') || todayCol
  if (!frame) return
  const now = new Date()
  const axisStart = new Date(now); axisStart.setHours(6, 0, 0, 0)
  const axisEnd = new Date(now); axisEnd.setHours(23, 0, 0, 0)
  if (now <= axisStart) return
  const pct = Math.min(1, (now - axisStart) / (axisEnd - axisStart))
  const ov = document.createElement('div')
  ov.className = 'fc-timegrid-past-overlay'
  ov.style.height = Math.round(frame.getBoundingClientRect().height * pct) + 'px'
  frame.appendChild(ov)
}
window.addEventListener('resize', shadePastArea)

/* ===== 视图切换：日历 resize / 列表与历史加载 ===== */
watch(viewTab, async (v) => {
  await nextTick()
  if (v === 'day' || v === 'week' || v === 'month') {
    const api = calRef.value?.getApi?.()
    if (api) {
      const viewMap = { day: 'timeGridDay', week: 'timeGridWeek', month: 'dayGridMonth' }
      api.changeView(viewMap[v])
      setTimeout(() => { if (typeof api.resize === 'function') api.resize(); shadePastArea() }, 50)
    }
  } else if (v === 'history') {
    loadHistory()
  } else if (v === 'list') {
    load(); loadPhaseCounts()
  }
})

/* ===== 列配置 / 导出 ===== */
const allColumns = [
  { prop: 'startTime', label: '上课时间' }, { prop: 'teacherId', label: '教师' },
  { prop: 'clazzId', label: '班级/学生' }, { prop: 'classroom', label: '教室' },
  { prop: 'status', label: '状态' }
]
const cols = reactive({ startTime: true, teacherId: true, clazzId: true, classroom: true, status: true })
const onCols = (c) => Object.assign(cols, c)

function doExport() {
  exportXlsx('排课列表', [
    { label: '上课时间', prop: 'startTime' }, { label: '结束时间', prop: 'endTime' },
    { label: '教室', prop: 'classroom' },
    { label: '状态', prop: 'phase', fmt: (v) => phaseMeta[v]?.label || v },
    { label: '剩余未排课时', prop: 'remainingHours' }
  ], rows.value)
}

watch(dateRange, doSearch)

/* ===== 新增排课 ===== */
const createVisible = ref(false)
const saving = ref(false)
const form = ref({})
const onClazzChange = (id) => {
  const c = clazzes.value.find((x) => x.id === id)
  if (c) form.value.teacherId = c.classTeacherId
}
/* ===== 新增排课：自由/重复（同班级管理排课TAB），时段预设/自定义，助教/主题 ===== */
const TIME_PRESETS = ['08:00~10:00', '10:00~12:00', '14:00~16:00', '16:00~18:00', '18:00~20:00', '19:00~21:00']
const WEEK_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const attCustomRange = ref(false)
const attPreset = ref('18:00~20:00')
function toggleAttCustom() {
  attCustomRange.value = !attCustomRange.value
  if (!attCustomRange.value) {
    const [a, b] = attPreset.value.split('~')
    form.value.range = [a, b]
  }
}
/** 时段反查：命中预设走下拉，否则切自定义 */
function syncAttRangeInput() {
  const r = form.value.range || []
  const hit = TIME_PRESETS.find((p) => { const [a, b] = p.split('~'); return a === r[0] && b === r[1] })
  attCustomRange.value = !hit
  attPreset.value = hit || '18:00~20:00'
}
function onTypeChange() { if (form.value.mode === 'one2one') form.value.schedMode = 'free' }
function openCreate() {
  const today = new Date().toISOString().slice(0, 10)
  form.value = { mode: 'clazz', schedMode: 'free', clazzId: null, studentId: null,
    date: today, range: ['18:00', '20:00'], teacherId: null, assistantId: null, classroomId: null, topic: '',
    beginDate: today, endMode: 'date', endDate: '', endCount: 10, repeatMode: 1,
    weekdays: [1, 2, 3, 4, 5], skipHoliday: true }
  attCustomRange.value = false
  attPreset.value = '18:00~20:00'
  attPreview.value = null
  createVisible.value = true
}
/* 重复规则预览（防抖调后端） */
const attPreview = ref(null)
let attPreviewTimer = null
watch([() => form.value?.schedMode, () => form.value?.beginDate, () => form.value?.endMode,
  () => form.value?.endDate, () => form.value?.endCount, () => form.value?.repeatMode,
  () => form.value?.skipHoliday, () => (form.value?.weekdays || []).join(',')],
  () => {
    attPreview.value = null
    const f = form.value
    if (!f || f.schedMode !== 'repeat' || !createVisible.value) return
    clearTimeout(attPreviewTimer)
    attPreviewTimer = setTimeout(async () => {
      if (!f.beginDate) return
      if (f.endMode !== 'count' && !f.endDate) return
      if (f.repeatMode !== 4 && !(f.weekdays || []).length) return
      try {
        attPreview.value = await api.scheduleRepeatPreview({
          clazzId: f.clazzId, teacherId: f.teacherId, classroomId: f.classroomId,
          repeatMode: f.repeatMode, weekdays: f.repeatMode === 4 ? [] : f.weekdays,
          beginDate: f.beginDate, endMode: f.endMode,
          endDate: f.endMode === 'count' ? null : f.endDate,
          endCount: f.endMode === 'count' ? f.endCount : null,
          skipHoliday: !!f.skipHoliday,
          beginTime: f.range?.[0], endTime: f.range?.[1] })
      } catch (e) { /* 预览失败不打扰 */ }
    }, 300)
  })
function onRoomChange(id) {
  const r = classrooms.value.find((x) => x.id === id)
  if (r) form.value.classroom = r.name
}
async function saveCreate() {
  const f = form.value
  if (f.mode === 'clazz') {
    if (!f.clazzId) return ElMessage.warning('请选择班级')
    const c = clazzes.value.find((x) => x.id === f.clazzId)
    f.courseId = c?.courseId
    f.studentId = null
  } else {
    if (!f.studentId) return ElMessage.warning('请选择学生')
    f.clazzId = null
    const enrolls = await api.enrollPage({ page: 1, size: 1, studentId: f.studentId, status: 1 })
    f.courseId = enrolls.records?.[0]?.courseId
  }
  if (!f.courseId) return ElMessage.warning('未找到对应课程，请先为学生报名')
  if (!f.teacherId) return ElMessage.warning('请选择教师')
  if (!f.classroomId) return ElMessage.warning('请选择教室')
  if (!f.range?.[0] || !f.range?.[1]) return ElMessage.warning('请选择上课时段')
  if (String(f.range[1]) <= String(f.range[0])) return ElMessage.warning('结束时间必须晚于开始时间')
  // 自由排课：单次
  if (f.schedMode !== 'repeat') {
    if (!f.date) return ElMessage.warning('请选择上课日期')
    f.startTime = `${f.date} ${f.range[0]}:00`
    f.endTime = `${f.date} ${f.range[1]}:00`
    const err = checkStartFuture(f.startTime)
    if (err) return ElMessage.warning(err)
    if (!(await confirmUrgentStart(f.startTime))) return
    saving.value = true
    try {
      f.classroom = classrooms.value.find((x) => x.id === Number(f.classroomId))?.name || f.classroom
      await api.scheduleCreate({ clazzId: f.clazzId, studentId: f.studentId, courseId: f.courseId,
        teacherId: f.teacherId, assistantId: f.assistantId || null, classroomId: f.classroomId,
        topic: f.topic || null, schedType: 0, startTime: f.startTime, endTime: f.endTime })
      ElMessage.success('排课成功')
      createVisible.value = false
      refreshAll()
    } finally { saving.value = false }
    return
  }
  // 重复排课（仅班课）：按规则批量生成，逐次校验冲突，失败汇总跳过
  if (!f.beginDate) return ElMessage.warning('请选择开始日期')
  if (f.endMode !== 'count' && !f.endDate) return ElMessage.warning('请选择结束日期')
  if (f.endMode !== 'count' && String(f.endDate) < String(f.beginDate)) return ElMessage.warning('结束日期不能早于开始日期')
  if (f.endMode === 'count' && !f.endCount) return ElMessage.warning('请填写排课次数')
  if (f.repeatMode !== 4 && !f.weekdays?.length) return ElMessage.warning('请选择周几上课')
  saving.value = true
  try {
    const r = await api.scheduleRepeat({ clazzId: f.clazzId, teacherId: f.teacherId,
      assistantId: f.assistantId || null, classroomId: f.classroomId, topic: f.topic || null,
      repeatMode: f.repeatMode, weekdays: f.repeatMode === 4 ? [] : f.weekdays,
      beginDate: f.beginDate, endMode: f.endMode,
      endDate: f.endMode === 'count' ? null : f.endDate,
      endCount: f.endMode === 'count' ? f.endCount : null,
      skipHoliday: !!f.skipHoliday, beginTime: f.range[0], endTime: f.range[1] })
    createVisible.value = false
    refreshAll()
    const skipped = r.skipped || []
    ElMessage.success(`重复排课完成：成功 ${r.created} 次${skipped.length ? `，跳过 ${skipped.length} 次` : ''}`)
    if (skipped.length) {
      ElMessageBox.alert(skipped.slice(0, 50).map((x) => `${x.date}：${x.reason}`).join('<br>'),
        `跳过明细（共 ${skipped.length} 条）`, { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' })
    }
  } finally { saving.value = false }
}

/* ===== 编辑上课（整页抽屉，参考 schoolpal editTeachingLog）：上课信息 + 按学员登记 到课(扣课)/请假/旷课 ===== */
const attDrawerVisible = ref(false)
const attSaving = ref(false)
const attLoading = ref(false)
const attRow = ref(null)
const attStudents = ref([])
const attForm = ref({})
const attEditCustom = ref(false)   // 时段：预设/自定义
const attEditPreset = ref('')
const attSearch = ref('')
const attChecked = ref([])
/** 已过上课时间的课次：日期/时段/教室锁定（教师/助教/主题/出勤仍可改） */
const attPast = computed(() => attRow.value ? new Date(String(attRow.value.startTime).replace(' ', 'T')) <= new Date() : false)
/** 头部信息章：校区（教室→校区）/课程名 */
const attCampus = computed(() => {
  const r = attRow.value
  if (!r) return ''
  const room = classrooms.value.find((c) => Number(c.id) === Number(r.classroomId))
  return room?.campus || clazzes.value.find((c) => c.id === r.clazzId)?.campus || ''
})
const attCourseName = computed(() => {
  const r = attRow.value
  if (!r) return ''
  if (r.courseId) return courses.value.find((c) => c.id === r.courseId)?.name || ''
  return r.clazzId ? courseNameOf(r.clazzId) : ''
})
const attCnt = (att) => attStudents.value.filter((s) => s.att === att && !s.__saved).length
    + (att !== 0 ? attStudents.value.filter((s) => s.att === att && s.__saved).length : 0)
const attLocked = computed(() => attStudents.value.filter((s) => s.__saved).length)
const attStudentsFiltered = computed(() => attStudents.value.filter((s) =>
  !attSearch.value || String(s.name || '').includes(attSearch.value)))
/** 行内「保存」：锁定本行（快照留底供「取消」还原）；「取消」：还原到锁定/初始状态 */
function lockAttRow(row) {
  if (row.att < 1) return ElMessage.warning('请先选择到课/请假/旷课再保存')
  row.__saved = true
  row.__snap = { att: row.att, hours: row.hours, late: !!row.late }
}
function resetAttRow(row) {
  const s = row.__snap || row.__init
  row.att = s.att; row.hours = s.hours; row.late = !!s.late
  row.__saved = false
  row.__snap = null
}
/** 移除学员行：正式学员移除后本次不登记（回到未记录），临时/补课学员直接剔除 */
function removeAttRow(row) {
  attStudents.value = attStudents.value.filter((s) => s.studentId !== row.studentId)
}
/** 批量到课：勾选行优先，未勾选则作用于当前筛选结果；临时学员固定到课 */
function attBatchAttend() {
  const list = attChecked.value.length ? attChecked.value : attStudentsFiltered.value
  if (!list.length) return ElMessage.warning('当前没有可标记的学员')
  for (const s of list) if (s.kind !== 'temp') s.att = 1
}
function onAttEditPreset(p) { const [a, b] = String(p).split('~'); attForm.value.range = [a, b] }
function toggleAttEditCustom() {
  attEditCustom.value = !attEditCustom.value
  if (!attEditCustom.value) onAttEditPreset(attEditPreset.value)
}
/** 学员头像底色：姓名哈希取色 */
function attAvatarColor(name) {
  const cs = ['#155EEF', '#12b76a', '#f79009', '#7c5cff', '#0e9f9f', '#f04438']
  let h = 0
  for (const c of String(name || '')) h = (h * 31 + c.charCodeAt(0)) % 997
  return cs[h % cs.length]
}
/** 打开编辑上课：头部信息章 + 上课信息回显 + 班课在读学员/一对一单行；剩余课时=本课程在读剩余，请假次数=本班考勤明细统计 */
async function openMarkAtt(r) {
  attRow.value = r
  const date = String(r.startTime || '').slice(0, 10)
  const range = [fmtHM(r.startTime), fmtHM(r.endTime)]
  attForm.value = { date, range, teacherId: r.teacherId || null, assistantId: r.assistantId ?? null,
    classroomId: r.classroomId ?? null, topic: r.topic || '', memo: '' }
  attEditPreset.value = TIME_PRESETS.find((p) => p === `${range[0]}~${range[1]}`) || ''
  attEditCustom.value = !attEditPreset.value
  attSearch.value = ''
  attDrawerVisible.value = true
  attLoading.value = true
  attStudents.value = []
  try {
    let rows = []
    if (r.clazzId) rows = (await api.scheduleClazzStudents(r.clazzId)) || []
    else if (r.studentId) rows = [{ studentId: r.studentId, name: studentName(r.studentId) }]
    const courseId = r.courseId || (r.clazzId ? clazzes.value.find((c) => c.id === r.clazzId)?.courseId : null)
    const leaveCnt = {}
    if (r.clazzId) {
      try {
        const rec = await api.scheduleClassRecords({ clazzId: r.clazzId })
        for (const d of rec.detail || [])
          if (d.type === '请假') leaveCnt[d.studentId] = (leaveCnt[d.studentId] || 0) + 1
      } catch (e) { /* 忽略 */ }
    }
    attStudents.value = await Promise.all(rows.map(async (s) => {
      let remain = null, unit = '课时'
      try {
        const en = await api.enrollPage({ page: 1, size: 20, studentId: s.studentId, status: 1 })
        const list = en.records || []
        const hit = list.find((e) => !courseId || Number(e.courseId) === Number(courseId)) || list[0]
        if (hit) {
          remain = Math.max(Number(hit.totalHours || 0) - Number(hit.usedHours || 0), 0)
          unit = hit.hoursUnit || '课时'
        }
      } catch (e) { /* 忽略 */ }
      const row = { studentId: s.studentId, name: s.name, status: 1, remain, unit,
        leaveCount: leaveCnt[s.studentId] || 0, att: 0, hours: 1, late: false, kind: null,
        __saved: false, __snap: null, __init: { att: 0, hours: 1, late: false } }
      return row
    }))
  } finally { attLoading.value = false }
}
/* 添加临时/补课学员（编辑上课）：临时=全库搜索到课不扣课；补课=同课程已分班请假学员，扣本人合同 */
const attPickVisible = ref(false)
const attPickKind = ref('temp')
const attPickKw = ref('')
const attPickSel = ref([])
let attAllStudents = []
const attMakeupRows = ref([])
const attMkCampus = ref('')
const attMkClazz = ref(null)
const attMkKw = ref('')
const attPickFiltered = computed(() => {
  const added = new Set(attStudents.value.map((s) => s.studentId))
  const k = attPickKw.value.trim().toLowerCase()
  return (attAllStudents || []).filter((s) => !added.has(s.studentId ?? s.id)
    && (!k || String(s.name || '').toLowerCase().includes(k) || String(s.phone || '').includes(k))).slice(0, 50)
})
const attMkCampusOptions = computed(() =>
  [...new Set(attMakeupRows.value.map((r) => r.campus).filter(Boolean))])
const attMkClazzOptions = computed(() => {
  const cid = attRow.value?.courseId || clazzes.value.find((c) => c.id === attRow.value?.clazzId)?.courseId
  return (clazzes.value || []).filter((c) => Number(c.courseId) === Number(cid))
})
const attMkFiltered = computed(() => {
  const added = new Set(attStudents.value.map((s) => s.studentId))
  const k = attMkKw.value.trim().toLowerCase()
  return attMakeupRows.value.filter((r) => !added.has(r.studentId)
    && (!attMkCampus.value || r.campus === attMkCampus.value)
    && (!attMkClazz.value || String(r.clazzId) === String(attMkClazz.value))
    && (!k || String(r.stuName || '').toLowerCase().includes(k)))
})
async function openAttPicker(kind) {
  attPickKind.value = kind
  attPickKw.value = ''
  attPickSel.value = []
  if (kind === 'makeup') {
    attMkCampus.value = ''
    attMkClazz.value = null
    attMkKw.value = ''
    attMakeupRows.value = []
    attPickVisible.value = true
    try {
      const cid = attRow.value?.courseId || clazzes.value.find((c) => c.id === attRow.value?.clazzId)?.courseId
      attMakeupRows.value = (await api.scheduleMakeupCandidates({ courseId: cid })) || []
    } catch { /* 空列表由 empty 兜底 */ }
  } else {
    attAllStudents = (await api.studentList()) || []
    attPickVisible.value = true
  }
}
function confirmAttPick() {
  if (!attPickSel.value.length) return ElMessage.warning('请勾选要加入的学员')
  for (const s of attPickSel.value) {
    attStudents.value.push({
      studentId: s.studentId ?? s.id, name: s.name || s.stuName,
      status: 1, remain: null, unit: '课时', leaveCount: 0,
      att: 1, hours: 1, late: false, kind: attPickKind.value,
      __saved: false, __snap: null, __init: { att: 1, hours: 1, late: false }
    })
  }
  ElMessage.success(`已加入 ${attPickSel.value.length} 名${attPickKind.value === 'temp' ? '临时' : '补课'}学员`)
  attPickVisible.value = false
}
async function saveMarkAtt() {
  const r = attRow.value
  const f = attForm.value
  if (!f.date || !f.range?.[0]) return ElMessage.warning('请选择上课日期和时段')
  if (!f.teacherId) return ElMessage.warning('请选择上课教师')
  const marked = attStudents.value.filter((s) => s.att > 0 || s.kind === 'temp')
  const submit = marked.map((s) => ({ studentId: s.studentId,
    att: s.kind === 'temp' ? 1 : s.att, hours: s.kind === 'temp' ? null : (s.att === 1 ? s.hours : null),
    kind: s.kind || undefined, ext: s.late ? '迟到' : null }))
  const normal = submit.filter((s) => s.kind !== 'temp')
  if (!normal.length) return ElMessage.warning('请先标记学员出勤（到课/请假/旷课）')
  if (normal.some((s) => s.att === 1 && (!s.hours || s.hours < 1)))
    return ElMessage.warning('到课学员的扣课节数至少 1 节')
  attSaving.value = true
  try {
    // 1) 上课信息有变化 → 先编辑排课（未过上课时间才允许改时间/教室；后端会再校验冲突）
    if (!attPast.value) {
      const st = `${f.date} ${f.range[0]}:00`
      const et = `${f.date} ${f.range[1]}:00`
      if (st !== r.startTime || et !== r.endTime || Number(f.teacherId) !== Number(r.teacherId)
        || (f.assistantId ?? null) !== (r.assistantId ?? null)
        || Number(f.classroomId || 0) !== Number(r.classroomId || 0)
        || (f.topic || '') !== (r.topic || '')) {
        await api.scheduleUpdate(r.id, { startTime: st, endTime: et,
          teacherId: f.teacherId, assistantId: f.assistantId ?? null,
          classroomId: f.classroomId ?? null, topic: f.topic || null })
        r.startTime = st; r.endTime = et
      }
    } else if (Number(f.teacherId) !== Number(r.teacherId)
      || (f.assistantId ?? null) !== (r.assistantId ?? null)
      || (f.topic || '') !== (r.topic || '')) {
      // 已开课课次：只同步教师/助教/主题（不带时间，后端跳过"不能调往过去"校验）
      await api.scheduleUpdate(r.id, { teacherId: f.teacherId,
        assistantId: f.assistantId ?? null, topic: f.topic || null })
    }
    // 2) 登记出勤（备注一并记入课次备注）
    const n = await api.scheduleMarkAttendance(r.id, {
      students: submit,
      memo: f.memo?.trim() || undefined
    })
    ElMessage.success(`登记完成：${normal.length} 名学员，消课合同 ${n} 份`)
    attDrawerVisible.value = false
    refreshAll()
  } finally { attSaving.value = false }
}

/* ===== 已下课登记：到课/教师请假/学生请假/旷课 ===== */
const attendVisible = ref(false)
const attendForm = ref({ id: null, hours: 1, maxHours: 1, minutes: 0, durationMinutes: 60, remark: '' })
async function openAttend(row) {
  attendForm.value = { id: row.id, hours: 1, maxHours: 1, minutes: 0, durationMinutes: 60, remark: '' }
  attendVisible.value = true
  const info = await api.scheduleAttendInfo(row.id)
  const max = info?.maxHours || 1
  attendForm.value = { id: row.id, hours: max, maxHours: max,
    minutes: info?.minutes, durationMinutes: info?.durationMinutes, remark: '' }
}
async function saveAttend() {
  const f = attendForm.value
  if (f.hours < f.maxHours && !f.remark?.trim())
    return ElMessage.warning('本次扣课少于本课次应扣节数，请填写扣课说明')
  saving.value = true
  try {
    const n = await api.scheduleAttend(f.id, f.hours, f.remark?.trim() || undefined)
    ElMessage.success(`到课登记成功：已扣 ${f.hours} 节课（${n ?? 0} 份合同）`)
    attendVisible.value = false
    refreshAll()
  } finally { saving.value = false }
}
async function doLeave(row, type) {
  const label = type === 3 ? '教师请假' : '学生请假'
  await ElMessageBox.confirm(`确认将该课次登记为「${label}」？该状态不扣课时。`, `${label}登记`, { type: 'warning' })
  await api.scheduleLeave(row.id, type)
  ElMessage.success(`已登记为${label}（不扣课）`)
  refreshAll()
}
async function doAbsent(row) {
  const { value } = await ElMessageBox.prompt(
    '登记旷课后将生成旷课申请：审批通过不扣课；72小时内未批准系统将自动扣课（扣课记录同步至课时管理）。',
    '旷课登记', { inputPlaceholder: '旷课说明（选填）', type: 'warning' })
  await api.scheduleAbsent(row.id, value || '')
  ElMessage.success('已生成旷课申请，等待审批（72小时内未批准将自动扣课）')
  refreshAll()
}

/* ===== 取消/调时/补课 ===== */
async function doCancel(row) {
  const { value } = await ElMessageBox.prompt('请输入取消原因', '取消课次')
  await api.scheduleCancel(row.id, { reason: value || '' })
  ElMessage.success('已取消')
  refreshAll()
}

const timeVisible = ref(false)
const timeMode = ref('adjust')
const timeForm = ref({})
const currentRow = ref(null)
function openAdjust(row) {
  timeMode.value = 'adjust'; currentRow.value = row
  timeForm.value = { startTime: row.startTime, endTime: row.endTime, reason: '', classroomId: row.classroomId ?? undefined }
  timeVisible.value = true
}
function openCompensate(row) {
  timeMode.value = 'compensate'; currentRow.value = row
  timeForm.value = { startTime: '', endTime: '', classroomId: row.classroomId ?? undefined }
  timeVisible.value = true
}
async function saveTime() {
  const err = checkStartFuture(timeForm.value.startTime)
  if (err) return ElMessage.warning(err)
  if (!(await confirmUrgentStart(timeForm.value.startTime))) return
  if (timeMode.value === 'adjust') {
    await api.scheduleAdjust(currentRow.value.id, timeForm.value)
    ElMessage.success('已调整')
  } else {
    await api.scheduleCompensate(currentRow.value.id, timeForm.value)
    ElMessage.success('补课已安排')
  }
  timeVisible.value = false
  refreshAll()
}

/* ===== 日历事件：颜色=状态，仅待上课可拖拽；点课次块/汇总块/「+N 节」/双击日期
   统一打开课次清单抽屉（信息 + 调时/取消/删除/登记等操作） ===== */
const calOptions = {
  plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin],
  initialView: 'timeGridWeek',
  locale: zhCnLocale,
  headerToolbar: { left: 'prev,next today', center: 'title', right: '' },
  allDaySlot: false,
  slotMinTime: '06:00:00', slotMaxTime: '23:00:00',
  // 月视图（dayGrid）定时事件默认渲染为圆点，改为色块与周视图一致
  eventDisplay: 'block',
  selectable: true, editable: true, eventStartEditable: true,
  height: '100%',
  events: loadCalEvents,
  // 月视图每天最多直显 3 条，其余收进「+N 节」，点击打开当日清单抽屉
  dayMaxEvents: 3,
  moreLinkText: (n) => `+${n} 节`,
  moreLinkClick: onMoreLinkClick,
  eventContent: renderEventContent,
  eventDrop: onEventDrop,
  eventClick: onEventClick,
  select: onSelect,
  datesSet: onDatesSet,
  eventTimeFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
  slotLabelFormat: { hour: '2-digit', minute: '2-digit', hour12: false }
}

/** 日历/清单删除课次：确认后软删除（排课历史留痕）；返回是否真正删除 */
async function doDeleteSchedule(id) {
  try {
    await ElMessageBox.confirm(
      '确认删除该课次？删除后将从排课中清除，并在排课历史中记录「删除排课」。',
      '删除课次', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
  } catch { return false }
  await api.scheduleDelete(id)
  ElMessage.success('课次已删除')
  refreshAll()
  return true
}

onMounted(async () => {
  load(); loadPhaseCounts()
  teachers.value = (await api.teacherList()) || []
  clazzes.value = (await api.clazzList()) || []
  students.value = (await api.studentList()) || []
  courses.value = (await api.courseList()) || []
  classrooms.value = (await api.classroomList()) || []
  const tree = (await api.orgTree()) || []
  const orgs = []
  const walk = (nodes, p) => nodes.forEach((n) => {
    orgs.push({ id: n.id, name: p ? p + '/' + n.name : n.name })
    walk(n.children || [], p || n.name)
  })
  walk(tree, '')
  orgList.value = orgs
})
</script>

<style scoped>
/* 阶段子TAB徽标位置 */
.phase-tabs :deep(.el-tabs__item) { height: 40px; }
.phase-badge { margin-left: 4px; }
.tobe-hint { color: #98a2b3; font-size: 12px; margin-left: 4px; }
/* 教师空闲查询抽屉：筛选条 */
.ft-bar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.remain-tag { margin-left: 6px; }
/* 日历文字与全站字号统一 */
.cal-panel :deep(.fc) { font-size: 12.5px; }
.cal-panel :deep(.fc .fc-toolbar-title) { font-size: 16px; color: #142a44; }
.cal-panel :deep(.fc .fc-button) { background: #155EEF; border-color: #155EEF; text-transform: capitalize; }
.cal-panel :deep(.fc .fc-button:hover) { background: #4a7df4; }
.cal-panel :deep(.fc .fc-button-active),
.cal-panel :deep(.fc .fc-button:disabled) {
  background: linear-gradient(135deg, #155EEF, #5B8DEF) !important;
  border-color: transparent;
}
.cal-panel :deep(.fc .fc-event) { border-radius: 5px; border: none; cursor: pointer; }
/* 两行式色块内容（eventContent 动态创建，须经 :deep 挂靠）：
   行1 时间+班级/学生，行2 📍教室 · 教师；窄块单行省略号截断，不再挤压换行 */
.cal-panel :deep(.ev2) {
  display: flex; flex-direction: column; gap: 1px;
  padding: 1px 4px 2px; line-height: 1.25; overflow: hidden;
}
.cal-panel :deep(.ev2-n) {
  font-size: 11.5px; font-weight: 600; color: #fff;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.cal-panel :deep(.ev2-t) { font-size: 10px; font-weight: 400; opacity: 0.85; }
/* 折叠汇总块的教室数高亮（颜色由 numHighlightOn 按底色亮度内联指定） */
.cal-panel :deep(.ev2-num) {
  font-size: 13.5px; font-weight: 700; padding: 0 1px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}
.cal-panel :deep(.ev2-m) {
  font-size: 10.5px; color: rgba(255, 255, 255, 0.92);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
/* 课次块无内嵌按钮，整块即点击目标（开课次抽屉），不再有删除按钮遮挡问题 */
.cal-panel :deep(.fc .fc-daygrid-day-number),
.cal-panel :deep(.fc .fc-col-header-cell-cushion) { color: #475467; }
/* 今天列底色（日/周/月视图的当日可用时段）：淡紫（渲染 ≈ #dac8fa），与白底/灰色对比明显 */
.cal-panel :deep(.fc .fc-day-today) { background: rgba(124, 58, 237, 0.28) !important; }
/* 系统当前时间之前的区域灰色（加深）：过去的整天（月视图日期格 + 日/周视图整天列 + 顶部星期头） */
.cal-panel :deep(.fc .fc-day-past) { background: #cbd5e1 !important; }
.cal-panel :deep(.fc .fc-day-past .fc-daygrid-day-number) { color: #8b95a7; }
/* 日/周视图：今天 6:00~当前时间 的已过时段遮罩（加深，不拦截点击） */
.cal-panel :deep(.fc-timegrid-past-overlay) {
  position: absolute; top: 0; left: 0; width: 100%;
  background: rgba(148, 163, 184, 0.45); pointer-events: none; z-index: 2;
}
/* ===== 课次清单抽屉条目 ===== */
.slot-item { padding: 10px 14px; margin-bottom: 10px; }
.slot-head { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.slot-name { font-weight: 600; font-size: 13.5px; color: #142a44; }
.slot-meta { font-size: 12.5px; color: #667085; margin: 4px 0 2px; }
.slot-acts { display: flex; flex-wrap: wrap; margin-top: 2px; }
.slot-acts .el-button + .el-button { margin-left: 8px; }
/* 点击课名展开/收缩：箭头随开合旋转 */
.slot-toggle { cursor: pointer; display: inline-flex; align-items: center; gap: 2px; }
.slot-toggle:hover { color: #155eef; }
.slot-arrow { font-size: 12px; color: #98a2b3; transition: transform 0.2s; }
.slot-arrow.open { transform: rotate(90deg); }
/* 展开区：教师/班级/上课学生 */
.slot-detail {
  margin-top: 8px; padding: 8px 10px; background: #f8fafc;
  border-radius: 6px; display: flex; flex-direction: column; gap: 5px;
  font-size: 12.5px; color: #475467;
}
.sd-row { display: flex; align-items: center; flex-wrap: wrap; }
.sd-label { color: #98a2b3; margin-right: 6px; white-space: nowrap; }
.sd-stu { margin: 0 4px 2px 0; }
.sd-muted { color: #98a2b3; }
.rec-hint { font-size: 12px; color: #8fa8c4; }
/* 编辑上课整页抽屉（参考 schoolpal editTeachingLog）：头部信息章 + 分区卡 + 汇总 + 学员行 */
.rah-header { display: flex; gap: 16px; margin-bottom: 12px; flex-wrap: wrap; }
.rah-chip { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: #344054; }
.rah-ic { width: 24px; height: 24px; border-radius: 6px; display: inline-flex; align-items: center; justify-content: center; color: #fff; }
.rah-ic-blue { background: linear-gradient(135deg, #155EEF, #4FA8FF); }
.rah-ic-purple { background: linear-gradient(135deg, #7c5cff, #a78bfa); }
.rah-ic-green { background: linear-gradient(135deg, #12b76a, #7ccf9c); }
.rah-sec { border: 1px solid #e4edf7; border-radius: 10px; padding: 12px 14px; margin-bottom: 12px; }
.rah-title { font-size: 13px; font-weight: 600; color: #344054;
  display: flex; align-items: center; margin-bottom: 10px; }
.rah-title::before { content: ''; width: 3px; height: 12px; background: #155EEF; border-radius: 2px; margin-right: 6px; }
.rah-row { display: flex; gap: 18px; flex-wrap: wrap; }
.rah-row .el-form-item { flex: 1; min-width: 220px; margin-bottom: 10px; }
.rah-stat { font-size: 12.5px; color: #667c99; background: #f5f9ff; border: 1px solid #dbe7fb;
  border-radius: 8px; padding: 5px 12px; margin-bottom: 8px; }
.rah-stat b { color: #155EEF; padding: 0 2px; }
.rah-toolbar { display: flex; align-items: center; margin-bottom: 8px; flex-wrap: wrap; gap: 4px; }
.rah-mk-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
.rah-mk-lb { font-size: 13px; color: #667c99; }
.att-stu { display: flex; align-items: center; gap: 6px; }
.att-avatar { width: 26px; height: 26px; border-radius: 50%; color: #fff; font-size: 13px;
  display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; }
.att-name { font-weight: 500; color: #344054; }
/* 框选空白·创建排课二选一 */
.pk-time { font-size: 13px; color: #475467; margin-bottom: 14px; }
.pk-time b { color: #155EEF; }
.pk-btns { display: flex; gap: 12px; }
.pk-btn { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 18px 10px; border: 1px solid #dfe7f3; border-radius: 10px; cursor: pointer;
  background: linear-gradient(180deg, #f9fcff, #fff); transition: all 0.15s ease; }
.pk-btn:hover { border-color: #b9d0f7; box-shadow: 0 4px 14px rgba(21, 94, 239, 0.12); }
.pk-btn b { font-size: 14px; color: #344054; }
.pk-btn span { font-size: 12px; color: #8fa8c4; }
/* 课程表弹窗：信息区 */
.ls-info { padding: 2px 6px; }
.ls-row { font-size: 13.5px; color: #475467; line-height: 2.1; }
.ls-row label { display: inline-block; width: 84px; color: #8fa8c4; }
.ls-link { color: #155EEF; cursor: pointer; font-weight: 600; }
.ls-footer { display: flex; justify-content: flex-end; gap: 2px; }
</style>
