<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">{{ pageTitle }}</span>
      <div class="actions">
        <template v-if="mode === 'list'">
          <el-button type="primary" @click="openEdit()">新增班级</el-button>
          <el-button v-if="sel.length" type="danger" @click="batchRemove">批量删除({{ sel.length }})</el-button>
        </template>
        <el-button v-else @click="backToList">← 返回列表</el-button>
      </div>
    </div>

    <!-- ===== 班级列表页 ===== -->
    <div v-if="mode === 'list'" class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="班级名称" clearable style="width: 190px"
                  @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="招生中" :value="1" /><el-option label="开课中" :value="2" /><el-option label="已结课" :value="3" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="clazz" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort" @selection-change="sel = $event" @row-click="onRowClick">
        <el-table-column type="selection" width="42" />
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.name" prop="name" label="班级名称" min-width="160" sortable="custom"  show-overflow-tooltip/>
        <el-table-column v-if="cols.courseId" label="课程" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.classTeacherId" label="班主任" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.classTeacherId" size="small" effect="light">{{ teacherName(row.classTeacherId) }}</el-tag>
            <span v-else>未指定</span>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.capacity" label="班容(占用/空余/满班)" width="155">
          <template #default="{ row }">
            <span style="color: #12b76a; font-weight: 600">{{ stuCount[row.id] ?? 0 }}</span>
            /
            <span style="color: #f79009; font-weight: 600">{{ Math.max((row.capacity || 0) - (stuCount[row.id] ?? 0), 0) }}</span>
            /
            <span style="color: #475467">{{ row.capacity }}</span> 名
          </template>
        </el-table-column>
        <el-table-column v-if="cols.minCapacity" label="最少开班" width="86" align="center">
          <template #default="{ row }">{{ row.minCapacity ?? '—' }}{{ row.minCapacity ? ' 名' : '' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.startDate" prop="startDate" label="开班日期" width="105" sortable="custom"  show-overflow-tooltip/>
        <el-table-column v-if="cols.endDate" prop="endDate" label="结班日期" width="105" sortable="custom"  show-overflow-tooltip>
          <template #default="{ row }">{{ row.endDate || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.status" label="状态" width="90" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="['', 'primary', 'success', 'info'][row.status]" effect="light">
              {{ ['', '招生中', '开课中', '已结课'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right" class-name="no-wrap-col">
          <template #default="{ row }">
            <el-dropdown trigger="click" popper-class="more-dropdown"
                         @visible-change="(v) => { row.__moreOpen = v }"
                         @command="(cmd) => onRowCommand(cmd, row)">
              <span class="more-trigger" :class="{ open: row.__moreOpen }">
                <el-icon :size="12"><MoreFilled /></el-icon>
                <span>更多</span>
                <el-icon :size="10" class="more-arrow"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="detail" class="more-item">
                    <span class="mi mi-green"><el-icon :size="13"><View /></el-icon></span>
                    <span class="mi-txt"><b>查看详情</b><i>班级完整信息</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item command="students" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><User /></el-icon></span>
                    <span class="mi-txt"><b>学生管理</b><i>添加 / 调班 / 课时明细</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item command="edit" class="more-item">
                    <span class="mi mi-purple"><el-icon :size="13"><EditPen /></el-icon></span>
                    <span class="mi-txt"><b>编辑</b><i>修改班级信息</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item command="del" divided class="more-item">
                    <span class="mi mi-red"><el-icon :size="13"><Delete /></el-icon></span>
                    <span class="mi-txt"><b>删除</b><i>有在读学生不可删</i></span>
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

    <!-- ===== 班级功能页：基本信息 + 统计 + 功能TAB ===== -->
    <template v-if="mode === 'detail'">
      <div class="panel cd-info-panel">
        <div class="class-info">
          <div class="class-info-item"><label class="class-item-label">所属课程</label>
            <span class="class-item-text">{{ detailCourse.name || '—' }}</span></div>
          <div class="class-info-item class-info-wide"><label class="class-item-label">收费标准</label>
            <span class="class-item-text" :title="feeStdAll">{{ feeStdAll }}</span></div>
          <div class="class-info-item"><label class="class-item-label">上课校区</label>
            <span class="class-item-text">{{ detailCampus }}</span></div>
          <div class="class-info-item"><label class="class-item-label">开班日期</label>
            <span class="class-item-text">{{ detailClazz?.startDate || '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">结班日期</label>
            <span class="class-item-text">{{ detailClazz?.endDate || '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">班级人数</label>
            <span class="class-item-text">
              <span class="full-power"><em class="rate-power" :style="{ width: fillRate + '%' }"></em></span>
              {{ detailStudying }}/{{ detailClazz?.capacity ?? '—' }}（{{ statusTextOf(detailClazz?.status) }}）
            </span></div>
          <div class="class-info-item"><label class="class-item-label">成班人数</label>
            <span class="class-item-text">{{ detailClazz?.minCapacity ?? '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">班主任</label>
            <span class="class-item-text">{{ detailClazz ? teacherName(detailClazz.classTeacherId) : '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">助教</label>
            <span class="class-item-text">—</span></div>
          <div class="class-info-item"><label class="class-item-label">教室</label>
            <span class="class-item-text">{{ detailRoomName }}</span></div>
          <div class="class-info-item"><label class="class-item-label">上课时间</label>
            <span class="class-item-text" :title="classTimeText">{{ classTimeText || '—' }}</span></div>
          <div class="class-info-item class-info-wide"><label class="class-item-label">备注</label>
            <span class="class-item-text">{{ detailClazz?.remark || '—' }}</span></div>
        </div>
        <div class="class-statistics">
          <label class="class-item-label">班级统计</label>
          <ul class="class-statistics-ul">
            <li> 在读学生：{{ detailStudying }}<span class="line">|</span></li>
            <li> 满班率：{{ fillRate.toFixed(2) }}%<span class="line">|</span></li>
            <li> 新生率：0.00%<span class="line">|</span></li>
            <li> 转出率：0.00%<span class="line">|</span></li>
            <li> 退费率：0.00%<span class="line">|</span></li>
            <li> 停课率：0.00%<span class="line">|</span></li>
            <li> 升期率：0.00%<span class="line">|</span></li>
            <li> 试听学员：{{ auditionCount }}<span class="line">|</span></li>
            <li> 试听转化率：{{ auditionRate }}%<span class="line">|</span></li>
            <li>出勤率：0.00%</li>
          </ul>
        </div>
      </div>

      <div class="panel cd-tab-panel">
        <el-tabs v-model="detailTab">
          <!-- 学员TAB：容量条 + 学员表（复用添加学生/调班弹窗） -->
          <el-tab-pane label="学员" name="students">
            <div class="stu-cap-bar">
              <el-tag effect="dark" type="success">已占用 {{ occupiedTotal }} 名</el-tag>
              <el-tag effect="dark" type="warning">空余 {{ Math.max((stuInfo.capacity || 0) - occupiedTotal, 0) }} 名</el-tag>
              <el-tag effect="dark">满班 {{ stuInfo.capacity }} 名</el-tag>
              <el-tag effect="light" type="info">最少开班 {{ detailClazz?.minCapacity ?? '—' }} 名</el-tag>
              <span v-if="auditionCount > 0" class="aud-seat" title="试听学员（计入已占用名额）">
                <svg viewBox="0 0 32 32" width="20" height="20" aria-hidden="true">
                  <rect x="1" y="1" width="30" height="30" rx="7" fill="currentColor" />
                  <text x="16" y="23" text-anchor="middle" font-size="19" font-weight="700"
                        font-family="KaiTi, STKaiti, 'Kaiti SC', serif" fill="#fff">试</text>
                </svg>
                <b>{{ auditionCount }}</b>
              </span>
              <div style="flex: 1"></div>
              <el-button type="primary" size="small"
                         :disabled="occupiedTotal >= (stuInfo.capacity || 0)"
                         @click="openAddStu">添加学生</el-button>
            </div>
            <el-table :data="stuInfo.rows || []" v-loading="stuLoading" stripe size="small">
              <el-table-column type="index" label="序号" width="55" />
              <el-table-column prop="name" label="学生" width="100" show-overflow-tooltip />
              <el-table-column prop="phone" label="手机号" width="125" show-overflow-tooltip>
                <template #default="{ row }">{{ row.phone || '-' }}</template>
              </el-table-column>
              <el-table-column label="报课总数" width="90" align="center">
                <template #default="{ row }">{{ row.totalHours }} 节</template>
              </el-table-column>
              <el-table-column label="已上课数" width="90" align="center">
                <template #default="{ row }">
                  <span style="color: #155EEF; font-weight: 600">{{ row.usedHours }}</span> 节
                </template>
              </el-table-column>
              <el-table-column label="未上课数" width="90" align="center">
                <template #default="{ row }">
                  <span style="color: #12b76a; font-weight: 600">{{ row.remainHours }}</span> 节
                </template>
              </el-table-column>
              <el-table-column label="加入时间" width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ fmtTime(row.enrollTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="90" fixed="right">
                <template #default="{ row }">
                  <el-button link type="warning" @click="openTransfer(row)">调班</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!(stuInfo.rows || []).length && !stuLoading"
                      description="暂无学生，点「添加学生」按合同/姓名/手机号加入" :image-size="70" />
          </el-tab-pane>

          <!-- 试听学员TAB：办理试听入口 + 试听名单 -->
          <el-tab-pane label="试听学员" name="audition" lazy>
            <div class="aud-toolbar">
              <el-button type="primary" size="small" @click="openHandleAudition">办理试听</el-button>
              <el-button type="primary" plain size="small" @click="openBatchAudStu">批量添加学员</el-button>
              <span class="aud-tip">批量添加 = 多学员一键登记本班日程试听</span>
              <div style="flex: 1"></div>
              <el-tag effect="light" type="info">共 {{ audStuRows.length }} 名试听学员</el-tag>
            </div>

            <el-table :data="audStuRows" v-loading="audLoading" stripe size="small">
              <el-table-column type="index" label="序号" width="55" />
              <el-table-column prop="studentName" label="学员" width="100" show-overflow-tooltip />
              <el-table-column label="来源" width="92" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.source === 1 ? 'primary' : 'warning'" effect="plain">
                    {{ row.source === 1 ? '试听活动' : '日程试听' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="auditionName" label="试听名称" min-width="140" show-overflow-tooltip />
              <el-table-column prop="courseName" label="课程" min-width="100" show-overflow-tooltip />
              <el-table-column prop="teacherName" label="教师" width="84" show-overflow-tooltip />
              <el-table-column prop="classroomName" label="试听教室" width="92" show-overflow-tooltip />
              <el-table-column prop="timePeriod" label="时间段" width="120" show-overflow-tooltip />
              <el-table-column prop="auditionDate" label="办试听时间" width="100" />
              <el-table-column prop="assistantName" label="助教" width="84" show-overflow-tooltip>
                <template #default="{ row }">{{ row.assistantName || '-' }}</template>
              </el-table-column>
              <el-table-column prop="operator" label="试听操作人" width="96" show-overflow-tooltip />
              <el-table-column label="操作" width="70" fixed="right">
                <template #default="{ row }">
                  <el-popconfirm title="移除该试听学员?" @confirm="removeAudition(row.id)">
                    <template #reference><el-button link type="danger">移除</el-button></template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!audStuRows.length && !audLoading"
                      description="暂无试听学员：上方搜索选学员 → 选择试听活动/日程试听 → 确认添加" :image-size="70" />
          </el-tab-pane>

          <!-- 排课TAB：工具条（新建/导出/视图切换）+ 图例 + 课表视图(日/周/月)/排课列表 -->
          <el-tab-pane label="排课" name="schedule" lazy>
            <div class="sch-toolbar">
              <el-button type="primary" size="small" @click="openCreateSched">新建排课</el-button>
              <el-button size="small" @click="exportSchedules">导出课表</el-button>
              <el-button size="small" :type="schedView === 'cal' ? 'primary' : ''" @click="schedView = schedView === 'cal' ? 'list' : 'cal'">
                <el-icon style="margin-right: 4px"><Switch /></el-icon>{{ schedView === 'cal' ? '排课列表' : '课程表' }}
              </el-button>
              <template v-if="schedView === 'cal'">
                <el-radio-group v-model="calMode" size="small" style="margin-left: 8px" @change="onCalMode">
                  <el-radio-button value="day">日</el-radio-button>
                  <el-radio-button value="week">周</el-radio-button>
                  <el-radio-button value="month">月</el-radio-button>
                </el-radio-group>
              </template>
              <div style="flex: 1"></div>
              <ul class="sch-legend">
                <li><span class="color-square" style="background: #347ab7; border-color: #1f6099"></span>已记上课</li>
                <li><span class="color-square" style="background: #ffb51f; border-color: #d99712"></span>冲突日程</li>
                <li><span class="color-square" style="background: #129ca9; border-color: #046e78"></span>我的日程</li>
                <li><span class="color-square" style="background: #5bc377; border-color: #3ba257"></span>其它日程</li>
                <li><span class="legend-badge">试</span>含试听学员</li>
                <li><span class="color-square" style="background: #9aa8bd; border-color: #8593a8"></span>已归档（取消/请假/旷课）</li>
                <li><span class="color-square" style="background: #94a3b8; border-color: #7c8798"></span>其它课程（只读）</li>
              </ul>
            </div>

            <!-- 课程表视图：FullCalendar。日/周：框选空白排课、拖拽课次调课；月：点日期排课、点课次调课/取消 -->
            <div v-if="schedView === 'cal'" class="cal-wrap">
              <FullCalendar ref="fcRef" :options="calOptions" />
            </div>
            <div v-if="schedView === 'cal'" class="cal-tip">
              <el-icon style="margin-right: 4px"><InfoFilled /></el-icon>
              日/周视图框选空白时段即可排课，拖拽课次块可调课；月视图点空白日期排课，点课次块调课/取消。仅本班课次可调整时间，灰色块为本班教师在其它课程的课次（只读，不可调整）。
            </div>

            <!-- 排课列表视图：服务端分页 + 表头排序（上课时间/状态/创建时间/编辑时间），列均单行不折行 -->
            <template v-if="schedView === 'list'">
              <el-table :data="schedListRows" stripe size="small" max-height="420"
                        :default-sort="{ prop: 'createTime', order: 'descending' }" @sort-change="onSchedSort">
                <el-table-column type="index" label="序号" width="55" :index="schedRowNo" />
                <el-table-column label="排课方式" width="92" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.schedType === 1 ? 'warning' : 'info'" effect="plain">
                      {{ row.schedType === 1 ? '重复排课' : '自由排课' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="startTime" label="上课时间" width="110" sortable="custom" show-overflow-tooltip>
                  <template #default="{ row }">{{ fmtDateWeek(row.startTime) }}</template>
                </el-table-column>
                <el-table-column label="上课时段" width="110" align="center" show-overflow-tooltip>
                  <template #default="{ row }">{{ String(row.startTime || '').slice(11, 16) }} ~ {{ String(row.endTime || '').slice(11, 16) }}</template>
                </el-table-column>
                <el-table-column label="上课教师" width="90" show-overflow-tooltip>
                  <template #default="{ row }">{{ teacherName(row.teacherId) }}</template>
                </el-table-column>
                <el-table-column label="助教" width="90" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.assistantId ? teacherName(row.assistantId) : '-' }}</template>
                </el-table-column>
                <el-table-column label="上课教室" width="110" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.classroom || roomName(row.classroomId) || '-' }}</template>
                </el-table-column>
                <el-table-column prop="topic" label="上课主题" min-width="120" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.topic || '-' }}</template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="82" align="center" sortable="custom">
                  <template #default="{ row }">
                    <el-tag size="small" :type="['warning', 'success', 'info'][row.status] || 'info'" effect="plain">
                      {{ ['待上课', '已完成', '已取消'][row.status] || '-' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="创建时间" width="150" sortable="custom" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.createTime || '-' }}</template>
                </el-table-column>
                <el-table-column prop="updateTime" label="编辑时间" width="150" sortable="custom" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.updateTime || '-' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="118" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" size="small" :disabled="row.status !== 0" @click="openEditSched(row)">编辑</el-button>
                    <el-button link type="warning" size="small" :disabled="row.status !== 0" @click="cancelSchedRow(row)">取消排课</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!schedPager.total" description="暂无排课记录，点「新建排课」安排课次" :image-size="70" />
              <div class="pager-row">
                <el-pagination background layout="total, sizes, prev, pager, next" :total="schedPager.total"
                               v-model:current-page="schedPager.page" v-model:page-size="schedPager.size"
                               :page-sizes="[10, 20, 50, 100]" @current-change="onSchedPage" @size-change="onSchedSize" />
              </div>
            </template>
          </el-tab-pane>

          <!-- 上课记录TAB：已完成课次 -->
          <el-tab-pane label="上课记录" name="history" lazy>
            <el-table :data="doneSchedules" stripe size="small" max-height="420">
              <el-table-column type="index" label="序号" width="55" />
              <el-table-column label="上课时间" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">{{ fmtTime(row.startTime) }} ~ {{ String(row.endTime || '').slice(11, 16) }}</template>
              </el-table-column>
              <el-table-column label="教师" width="100" show-overflow-tooltip>
                <template #default="{ row }">{{ teacherName(row.teacherId) }}</template>
              </el-table-column>
              <el-table-column prop="classroom" label="教室" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.classroom || '-' }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row.remark || '-' }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!doneSchedules.length" description="暂无上课记录（完成的课次自动归档到这里）" :image-size="70" />
          </el-tab-pane>

          <!-- 其余功能TAB：规划中 -->
          <el-tab-pane label="成绩" name="score"><el-empty description="成绩管理功能规划中" :image-size="80" /></el-tab-pane>
          <el-tab-pane label="课堂点评" name="comment"><el-empty description="课堂点评功能规划中" :image-size="80" /></el-tab-pane>
          <el-tab-pane label="作业" name="homework"><el-empty description="作业管理功能规划中" :image-size="80" /></el-tab-pane>
          <el-tab-pane label="点名表" name="rollcall"><el-empty description="点名表功能规划中" :image-size="80" /></el-tab-pane>
          <el-tab-pane label="信息表" name="info"><el-empty description="信息表功能规划中" :image-size="80" /></el-tab-pane>
        </el-tabs>
      </div>
    </template>


    <!-- 添加学员：本班课程的班课在读学员多选（校区/分班状态/报名时间筛选 + 关键字搜索） -->
    <el-dialog v-model="addStuVisible" title="添加学员" width="720px">
      <div class="add-toolbar">
        <el-select v-model="addCampus" placeholder="校区" clearable size="small" style="width: 120px">
          <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="addAssigned" placeholder="分班状态" clearable size="small" style="width: 104px">
          <el-option label="未分班" :value="false" /><el-option label="已分班" :value="true" />
        </el-select>
        <el-date-picker v-model="addRange" type="daterange" value-format="YYYY-MM-DD" size="small"
                        start-placeholder="报名起" end-placeholder="报名止" style="width: 225px" />
        <el-input v-model="addKw" placeholder="请输入姓名或关键字搜索" clearable size="small"
                  style="width: 185px" :prefix-icon="Search" />
      </div>
      <div class="add-count">当前结果：学员共 <b>{{ addRows.length }}</b> 个（不包括转、退、停、结）</div>
      <el-table :data="addRows" v-loading="addLoading" size="small" stripe max-height="300" row-key="studentId"
                @selection-change="addSel = $event">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="name" label="学员姓名" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.name }}<span v-if="row.phone" class="add-sub">{{ row.phone }}</span>
          </template>
        </el-table-column>
        <el-table-column label="剩余数量" width="96" align="center">
          <template #default="{ row }"><b style="color: #12b76a">{{ row.remainHours }}</b> 课时</template>
        </el-table-column>
        <el-table-column label="分班状态" width="86" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.assigned ? 'info' : 'warning'" effect="plain">
              {{ row.assigned ? '已分班' : '未分班' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enrollTime" label="报名时间" width="112" sortable>
          <template #default="{ row }">{{ row.enrollTime || '-' }}</template>
        </el-table-column>
      </el-table>
      <div class="add-sel-count">已选择学员 <b class="blue">{{ addSel.length }}</b> 名
        <span v-if="addSel.some((r) => r.assigned)">（已分班的学员将调整到本班）</span>
      </div>
      <template #footer>
        <el-button @click="addStuVisible = false">取 消</el-button>
        <el-button type="primary" :loading="addSaving" @click="confirmAddStudents">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 调班 -->
    <el-dialog v-model="transferVisible" title="学生调班" width="440px">
      <el-form label-width="90px">
        <el-form-item label="学生">
          <el-input :model-value="transferStu?.name" disabled />
        </el-form-item>
        <el-form-item label="当前班级">
          <el-input :model-value="curClazz?.name" disabled />
        </el-form-item>
        <el-form-item label="目标班级" required>
          <el-select v-model="transferTargetId" filterable style="width: 100%" placeholder="选择目标班级">
            <el-option v-for="c in transferTargets" :key="c.id"
                       :label="`${c.name}（占用${stuCount[c.id] ?? 0}/${c.capacity}）`" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="form-tip">调班后原班级名额自动释放，新班级不能超容量；课程不一致不允许调班</div>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferSaving" @click="doTransfer">确认调班</el-button>
      </template>
    </el-dialog>
    
    <el-drawer v-model="visible" :title="form.id ? '编辑班级' : '新增班级'" size="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="班级名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="课程" required>
          <div class="course-pick">
            <el-input :model-value="courseName(form.courseId)" readonly placeholder="点击「选择课程」挑选"
                      class="course-pick-input">
              <template #suffix><el-icon class="pick-arrow"><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" plain @click="openCourseDlg">选择课程</el-button>
          </div>
        </el-form-item>
        <el-form-item label="班主任">
          <el-select v-model="form.classTeacherUserId" clearable filterable style="width: 100%"
                     placeholder="输入姓名搜索员工">
            <el-option v-for="c in staffCandidates" :key="c.userId" :label="c.name" :value="c.userId">
              <span>{{ c.name }}</span>
              <el-tag size="small" effect="plain" style="margin-left: 8px">{{ STAFF_NAMES[c.userType] || '员工' }}</el-tag>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="每次上课">
          <div class="per-class-box">
            <div class="pc-item">
              <span class="pc-label">学生扣除</span>
              <el-input-number v-model="form.studentHours" :min="0" :max="999" :precision="1" :step="0.5"
                               size="small" controls-position="right" style="width: 92px" placeholder="课时" />
              <span class="pc-unit">课时</span>
            </div>
            <div class="pc-item">
              <span class="pc-label">教师获得</span>
              <el-input-number v-model="form.teacherHours" :min="0" :max="999" :precision="1" :step="0.5"
                               size="small" controls-position="right" style="width: 92px" placeholder="课时" />
              <span class="pc-unit">课时</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="教室">
          <el-select v-model="form.classroomId" clearable filterable style="width: 100%"
                     placeholder="选择固定教室（选填）">
            <el-option v-for="r in classroomList" :key="r.id"
                       :label="`${r.name}（${r.campus || '未设校区'}·${r.minCapacity ?? '—'}~${r.capacity ?? 0}人）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班容人数">
          <div class="cap-box">
            <span class="cap-label">最少</span>
            <el-input-number v-model="form.minCapacity" :min="1" :max="999" size="small"
                             controls-position="right" style="width: 92px" placeholder="开班" />
            <span class="cap-label">满班</span>
            <el-input-number v-model="form.capacity" :min="1" :max="999" size="small"
                             controls-position="right" style="width: 92px" />
            <span class="cap-unit">名</span>
          </div>
        </el-form-item>
        <el-form-item label="开班日期"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="结班日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD"
                          :disabled-date="(d) => form.startDate && d < new Date(form.startDate)" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="招生中" :value="1" /><el-option label="开课中" :value="2" /><el-option label="已结课" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 选择课程弹窗（单选，参考报名/续报选课弹窗） -->
    <el-dialog v-model="courseDlgVisible" title="选择课程" width="760px" append-to-body>
      <div class="dlg-toolbar">
        <el-input v-model="courseKw" placeholder="请输入课程名称关键字搜索" clearable
                  style="width: 220px" :prefix-icon="Search" />
        <el-select v-model="dlgCategory" placeholder="课程类别" clearable size="default" style="width: 120px">
          <el-option v-for="d in dictStore.options('course_category')" :key="d.value" :label="d.label" :value="Number(d.value)" />
        </el-select>
        <span class="fee-sub">仅展示「班课」课程，一对一不在此选班 · 点击行选择</span>
      </div>
      <el-table :data="courseFiltered" height="340" size="small" stripe
                highlight-current-row @current-change="(r) => (dlgPicked = r)">
        <el-table-column prop="name" label="课程名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="课程类别" width="86">
          <template #default="{ row }">{{ row.category ? dictStore.label('course_category', row.category) : '-' }}</template>
        </el-table-column>
        <el-table-column label="收费模式" width="92">
          <template #default="{ row }">{{ feeModeText(row) }}</template>
        </el-table-column>
        <el-table-column label="学费标准" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ feeStdText(row) }}</template>
        </el-table-column>
        <el-table-column label="授课模式" width="64" align="center">
          <template #default="{ row }">
            <span v-if="row.teachMode && dictStore.label('class_type', row.teachMode) === '班课'"
                  class="ban-seal" title="班课">
              <svg viewBox="0 0 32 32" width="22" height="22" aria-hidden="true">
                <rect x="1" y="1" width="30" height="30" rx="7" fill="currentColor" />
                <text x="16" y="23" text-anchor="middle" font-size="19" font-weight="700"
                      font-family="KaiTi, STKaiti, 'Kaiti SC', 'FZKai-Z03S', serif" fill="#fff">班</text>
              </svg>
            </span>
            <span v-else>{{ row.teachMode ? dictStore.label('class_type', row.teachMode) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开班数" width="68" align="center">
          <template #default="{ row }">{{ clazzCountOf(row.id) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="74" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="dlgPicked && dlgPicked.id === row.id ? 'primary' : 'success'" effect="plain">
              {{ dlgPicked && dlgPicked.id === row.id ? '已选择' : '可选' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="picked-clazz-bar">
        <span class="fee-sub">已选择：<b style="color: #155EEF">{{ dlgPicked ? dlgPicked.name : '未选择' }}</b></span>
      </div>
      <template #footer>
        <el-button @click="courseDlgVisible = false">取 消</el-button>
        <el-button type="primary" @click="confirmCourseDlg">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑排课（排课TAB）：排课方式（自由/重复）+ 日期规则 + 时段(预设/自定义) + 教师/助教/教室/主题 -->
    <el-dialog v-model="schedCreateVisible" :title="schedEditId ? '编辑排课' : '新建排课'" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="班级">
          <el-input :model-value="curClazz?.name" disabled />
        </el-form-item>
        <el-form-item v-if="!schedEditId" label="排课方式">
          <el-radio-group v-model="schedMode">
            <el-radio-button value="free">自由排课</el-radio-button>
            <el-radio-button value="repeat">重复排课</el-radio-button>
          </el-radio-group>
          <span v-if="schedMode === 'repeat'" class="sched-mode-tip">按规则批量生成多次课次</span>
        </el-form-item>

        <!-- 自由排课 / 编辑：单次上课日期 -->
        <el-form-item v-if="schedMode === 'free'" label="上课日期" required>
          <el-date-picker v-model="schedForm.date" type="date" value-format="YYYY-MM-DD"
                          :disabled-date="noPastDate" style="width: 100%" />
        </el-form-item>

        <!-- 重复排课：开始日期/结束方式/重复方式/周几上课/节假日过滤 -->
        <template v-else>
          <el-form-item label="开始日期" required>
            <el-date-picker v-model="schedForm.beginDate" type="date" value-format="YYYY-MM-DD"
                            :disabled-date="noPastDate" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束方式" required>
            <div class="sched-end-row">
              <el-select v-model="schedForm.endMode" style="width: 130px">
                <el-option label="按日期结束" value="date" />
                <el-option label="按次数结束" value="count" />
              </el-select>
              <el-date-picker v-if="schedForm.endMode !== 'count'" v-model="schedForm.endDate" type="date"
                              value-format="YYYY-MM-DD" :disabled-date="noPastDate" style="flex: 1" />
              <el-input-number v-else v-model="schedForm.endCount" :min="1" :max="200" :controls="false" style="width: 110px" />
              <span v-if="schedForm.endMode === 'count'" class="sched-end-unit">次</span>
            </div>
            <div v-if="repeatPreview && repeatPreview.count" class="repeat-preview-tip">
              预计排课 <b>{{ repeatPreview.count }}</b> 次<template v-if="repeatPreview.holidaySkipped">（节假日跳过 {{ repeatPreview.holidaySkipped }} 次）</template><template v-if="schedForm.endMode === 'count' && repeatPreview.lastDate">，最后一次 {{ repeatPreview.lastDate }}</template>
            </div>
          </el-form-item>
          <el-form-item label="重复方式" required>
            <el-radio-group v-model="schedForm.repeatMode">
              <el-radio :value="1">每周重复</el-radio>
              <el-radio :value="2">隔周重复</el-radio>
              <el-radio :value="4">隔天重复</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="schedForm.repeatMode !== 4" label="周几上课" required>
            <el-checkbox :model-value="allWeekPicked" style="margin-right: 4px" @change="toggleAllWeek">全选</el-checkbox>
            <el-checkbox-group v-model="schedForm.weekdays">
              <el-checkbox v-for="(w, i) in WEEK_NAMES" :key="w" :value="i + 1">{{ w }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="节假日过滤">
            <el-radio-group v-model="schedForm.skipHoliday">
              <el-radio :value="true">节假日不排课</el-radio>
              <el-radio :value="false">节假日排课</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>

        <!-- 共用：上课时段（预设下拉/自定义时间）+ 教师 + 助教 + 教室 + 主题 -->
        <el-form-item label="上课时段" required>
          <el-select v-if="!schedCustomRange" v-model="schedRangePreset" style="width: 170px" @change="onPresetRange">
            <el-option v-for="p in TIME_PRESETS" :key="p" :label="p" :value="p" />
          </el-select>
          <el-time-picker v-else v-model="schedForm.range" is-range range-separator="至"
                          start-placeholder="开始" end-placeholder="结束"
                          format="HH:mm" value-format="HH:mm" style="width: 210px" />
          <el-button link type="primary" style="margin-left: 10px" @click="toggleCustomRange">
            {{ schedCustomRange ? '选择预设时段' : '自定义时段' }}
          </el-button>
        </el-form-item>
        <el-form-item label="上课教师" required>
          <el-select v-model="schedForm.teacherId" filterable style="width: 100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="助教">
          <el-select v-model="schedForm.assistantId" clearable filterable placeholder="选填" style="width: 100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="上课教室" required>
          <el-select v-model="schedForm.classroomId" filterable style="width: 100%">
            <el-option v-for="r in classroomList" :key="r.id"
                       :label="`${r.name}（${r.minCapacity ?? '—'}~${r.capacity ?? 0}人）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="上课主题">
          <el-input v-model="schedForm.topic" maxlength="20" show-word-limit placeholder="选填，最多20字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="schedCreateVisible = false">取 消</el-button>
        <el-button type="primary" :loading="schedSaving" @click="saveSched">保 存</el-button>
      </template>
    </el-dialog>

    <!-- 课次操作（排课TAB 日/周/月视图点课次块）：信息 + 调课/取消；其它课程课次只读 -->
    <el-dialog v-model="schedOpVisible" title="课次详情" width="460px" append-to-body>
      <div v-if="schedOpRow" class="sch-op">
        <div class="sch-op-row"><span>上课时间</span><b>{{ fmtTime(schedOpRow.startTime) }} ~ {{ String(schedOpRow.endTime || '').slice(11, 16) }}</b></div>
        <div class="sch-op-row"><span>教师</span><b>{{ teacherName(schedOpRow.teacherId) }}</b></div>
        <div class="sch-op-row"><span>助教</span><b>{{ schedOpRow.assistantId ? teacherName(schedOpRow.assistantId) : '-' }}</b></div>
        <div class="sch-op-row"><span>教室</span><b>{{ schedOpRow.classroom || '-' }}</b></div>
        <div class="sch-op-row"><span>上课主题</span><b>{{ schedOpRow.topic || '-' }}</b></div>
        <div class="sch-op-row"><span>状态</span><b>{{ schedStatusText(schedOpRow) }}</b></div>
        <div class="sch-op-row">
          <span>标记</span>
          <span class="sch-op-tags">
            <el-tag v-for="t in schedTags(schedOpRow)" :key="t.text" size="small" effect="dark"
                    :style="{ background: t.color, borderColor: t.color, color: '#fff' }">{{ t.text }}</el-tag>
          </span>
        </div>
        <el-alert v-if="schedOpReadOnly" type="info" :closable="false" show-icon class="sch-op-alert"
                  title="该课次属于其它课程/其它班级，不可在班级管理中调整时间或取消" />
        <el-alert v-else-if="!schedOpAdjustable" type="warning" :closable="false" show-icon class="sch-op-alert"
                  title="仅待上课的课次可调课或取消" />
      </div>
      <template #footer>
        <el-button @click="schedOpVisible = false">关 闭</el-button>
        <el-button v-if="!schedOpReadOnly" type="warning" :disabled="!schedOpAdjustable" @click="openAdjustFromOp">调 课</el-button>
        <el-button v-if="!schedOpReadOnly" type="danger" :disabled="!schedOpCancellable" @click="cancelFromOp">取消课次</el-button>
      </template>
    </el-dialog>

    <!-- 调课（排课TAB）：改上课日期/时段 + 原因 -->
    <el-dialog v-model="schedAdjustVisible" title="调整上课时间" width="440px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="原时间">
          <el-input :model-value="`${fmtTime(schedAdjustRow?.startTime)} ~ ${String(schedAdjustRow?.endTime || '').slice(11, 16)}`" disabled />
        </el-form-item>
        <el-form-item label="日期" required>
          <el-date-picker v-model="schedAdjustForm.date" type="date" value-format="YYYY-MM-DD"
                          :disabled-date="noPastDate" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时段" required>
          <el-time-picker v-model="schedAdjustForm.range" is-range range-separator="至"
                          start-placeholder="开始" end-placeholder="结束"
                          format="HH:mm" value-format="HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item label="调课原因">
          <el-input v-model="schedAdjustForm.reason" type="textarea" :rows="2" placeholder="选填，如：教师临时有事" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="schedAdjustVisible = false">取 消</el-button>
        <el-button type="primary" :loading="schedAdjustSaving" @click="saveAdjust">保 存</el-button>
      </template>
    </el-dialog>

    <!-- 办理试听弹窗（试听学员TAB）：学员信息区 + 活动信息区 -->
    <el-dialog v-model="handleAudVisible" title="办理试听" width="560px">
      <div class="aud-sec">
        <div class="aud-sec-title">学员信息</div>
        <el-select v-model="audStuId" filterable remote :remote-method="searchAudStudents"
                   :loading="audSearching" clearable style="width: 100%"
                   placeholder="输入学员姓名 / 手机号搜索，双击查看全部" @change="onPickAudStu"
                   @dblclick="loadAllAudStudents">
          <el-option v-for="s in audStuCands" :key="s.studentId"
                     :label="`${s.name}（${s.phone || '无手机号'}）`" :value="s.studentId" />
        </el-select>
      </div>
      <div class="aud-sec">
        <div class="aud-sec-head">
          <span class="aud-sec-title">活动信息</span>
          <el-button v-if="audPickedAct" size="small" text type="danger" @click="audPickedAct = null">清空</el-button>
        </div>
        <div class="aud-acts">
          <el-button type="primary" plain size="small" @click="openActDlg">选择试听活动</el-button>
          <el-button type="primary" plain size="small" @click="openSchDlg">选择日程试听</el-button>
        </div>
        <div v-if="!audPickedAct" class="aud-empty-tip">请点击「选择试听活动」或「选择日程试听」，选中后回填项目信息</div>
        <ul v-else class="aud-detail">
          <li class="aud-detail-item aud-detail-name">
            <label>名称</label>
            <el-input v-model="audPickedAct.name" size="small" maxlength="100"
                      placeholder="必填" style="width: 180px" />
          </li>
          <li class="aud-detail-item"><label>校区</label>
            <span :title="audPickedAct.campus">{{ audPickedAct.campus || '—' }}</span></li>
          <li class="aud-detail-item"><label>日期</label>
            <span>{{ audPickedAct.date || '—' }}</span></li>
          <li class="aud-detail-item"><label>教师</label>
            <span :title="audPickedAct.teacherName">{{ audPickedAct.teacherName || '—' }}</span></li>
          <li class="aud-detail-item"><label>课程</label>
            <span :title="audPickedAct.courseName">{{ audPickedAct.courseName || '—' }}</span></li>
          <li class="aud-detail-item"><label>教室</label>
            <span :title="audPickedAct.classroomName">{{ audPickedAct.classroomName || '—' }}</span></li>
          <li class="aud-detail-item"><label>时段</label>
            <span>{{ audPickedAct.timePeriod || '—' }}</span></li>
          <li class="aud-detail-item"><label>助教</label>
            <span>{{ audPickedAct.assistantName || '—' }}</span></li>
          <li class="aud-detail-item"><label>预招人数</label>
            <span>{{ audPickedAct.plannedCount ?? '—' }}</span></li>
        </ul>
      </div>
      <template #footer>
        <el-button @click="handleAudVisible = false">取 消</el-button>
        <el-button type="primary" :loading="audSaving" @click="confirmAudition">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 批量添加学员弹窗（试听学员TAB）：多选学员一键登记本班日程试听 -->
    <el-dialog v-model="batchAudVisible" title="批量添加学员（本班日程试听）" width="560px" append-to-body>
      <div class="aud-acts" style="margin-bottom: 8px">
        <el-input v-model="batchAudKw" placeholder="输入学员姓名 / 手机号搜索，双击输入框查看全部" clearable
                  size="small" :prefix-icon="Search" @dblclick="loadAllBatchAudStu" />
      </div>
      <el-table :data="batchAudCands" v-loading="batchAudSearching" size="small" stripe
                max-height="300" row-key="studentId" @selection-change="batchAudSel = $event">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="name" label="学员姓名" min-width="110" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="年级" width="80">
          <template #default="{ row }">{{ row.grade || '-' }}</template>
        </el-table-column>
      </el-table>
      <div class="add-sel-count">已选择学员 <b class="blue">{{ batchAudSel.length }}</b> 名，将统一登记为「{{ curClazz?.name }}（日程试听）」</div>
      <template #footer>
        <el-button @click="batchAudVisible = false">取 消</el-button>
        <el-button type="primary" :loading="batchAudSaving" :disabled="!batchAudSel.length"
                   @click="confirmBatchAudStu">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 选择试听活动弹窗（试听学员TAB）：点击行选中回填 -->
    <el-dialog v-model="actDlgVisible" title="选择试听活动" width="820px" append-to-body>
      <div class="dlg-toolbar">
        <el-select v-model="actF.type" placeholder="试听类型" clearable size="small" style="width: 108px">
          <el-option label="插班试听" :value="1" /><el-option label="开班试听" :value="2" />
        </el-select>
        <el-date-picker v-model="actF.range" type="daterange" value-format="YYYY-MM-DD" size="small"
                        start-placeholder="日期起" end-placeholder="日期止" style="width: 215px" />
        <el-select v-model="actF.campus" placeholder="校区" clearable filterable size="small" style="width: 104px">
          <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="actF.courseName" placeholder="课程" clearable filterable size="small" style="width: 118px">
          <el-option v-for="c in actCourseOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="actF.teacherName" placeholder="老师" clearable filterable size="small" style="width: 104px">
          <el-option v-for="t in actTeacherOptions" :key="t" :label="t" :value="t" />
        </el-select>
      </div>
      <el-table ref="actTableRef" :data="actFiltered" height="330" size="small" stripe highlight-current-row
                @current-change="(r) => (actPickedRow = r)">
        <el-table-column prop="name" label="试听活动名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="人数" width="64" align="center">
          <template #default="{ row }">{{ row.plannedCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" min-width="100" show-overflow-tooltip />
        <el-table-column prop="teacherName" label="教师" width="84" show-overflow-tooltip />
        <el-table-column prop="classroomName" label="教室" width="88" show-overflow-tooltip />
        <el-table-column prop="timePeriod" label="时间段" width="110" show-overflow-tooltip />
        <el-table-column prop="auditionDate" label="日期" width="96" />
        <el-table-column label="选择" width="64" align="center">
          <template #default="{ row }">
            <el-tag v-if="actPickedRow && actPickedRow.id === row.id" size="small" type="primary" effect="plain">已选</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!actFiltered.length" description="暂无关联本班的试听活动（需在试听管理中办理关联本班的插班试听）" :image-size="70" />
      <template #footer>
        <el-button @click="actDlgVisible = false">取 消</el-button>
        <el-button type="primary" :disabled="!actPickedRow" @click="confirmActPick">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 选择日程试听弹窗（试听学员TAB）：已成班课列表，点击行选中回填 -->
    <el-dialog v-model="schDlgVisible" title="选择日程试听" width="820px" append-to-body>
      <div class="dlg-toolbar">
        <el-date-picker v-model="schF.range" type="daterange" value-format="YYYY-MM-DD" size="small"
                        start-placeholder="日期起" end-placeholder="日期止" style="width: 215px" />
        <el-select v-model="schF.campus" placeholder="校区" clearable filterable size="small" style="width: 104px">
          <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="schF.courseName" placeholder="课程" clearable filterable size="small" style="width: 118px">
          <el-option v-for="c in schCourseOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="schF.clazzName" placeholder="班级" clearable filterable size="small" style="width: 118px">
          <el-option v-for="c in schClazzOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="schF.teacherName" placeholder="老师" clearable filterable size="small" style="width: 104px">
          <el-option v-for="t in schTeacherOptions" :key="t" :label="t" :value="t" />
        </el-select>
      </div>
      <el-table ref="schTableRef" :data="schFiltered" v-loading="schLoading" height="330" size="small" stripe
                highlight-current-row @current-change="(r) => (schPickedRow = r)">
        <el-table-column prop="name" label="班级" min-width="140" show-overflow-tooltip />
        <el-table-column label="人数" width="76" align="center">
          <template #default="{ row }">{{ stuCount[row.id] ?? 0 }}/{{ row.capacity ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="课程" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column label="教师" width="84" show-overflow-tooltip>
          <template #default="{ row }">{{ teacherName(row.classTeacherId) || '-' }}</template>
        </el-table-column>
        <el-table-column label="教室" width="92" show-overflow-tooltip>
          <template #default="{ row }">{{ roomName(row.classroomId) || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间段" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ clazzTimeMap[row.id]?.text || '—' }}</template>
        </el-table-column>
        <el-table-column label="选择" width="64" align="center">
          <template #default="{ row }">
            <el-tag v-if="schPickedRow && schPickedRow.id === row.id" size="small" type="primary" effect="plain">已选</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!schFiltered.length && !schLoading" description="本班暂无日程（需为本班排课）" :image-size="70" />
      <template #footer>
        <el-button @click="schDlgVisible = false">取 消</el-button>
        <el-button type="primary" :disabled="!schPickedRow" @click="confirmSchPick">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {reactive, ref, computed, nextTick, onMounted, onBeforeUnmount, watch} from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {Search, MoreFilled, ArrowDown, View, User, EditPen, Delete, Switch, InfoFilled} from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'
import { useDictStore } from '../../store/dict'

const dictStore = useDictStore()

const query = reactive({ keyword: '', status: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.clazzPage(q), query)

const courses = ref([])
const teachers = ref([])
/* 教室列表（班级固定教室选择 + 班容校验） */
const classroomList = ref([])
/* 班主任候选：用户管理中类型为员工的在职人员（保存时后端按 userId 解析教师档案） */
const staffCandidates = ref([])
const STAFF_NAMES = { 2: '教务人员', 3: '班主任', 6: '校区校长', 7: '校区咨询师', 8: '校区学管师' }
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'
const teacherName = (id) => {
  const cand = staffCandidates.value.find((c) => c.teacherId === id)
  return cand?.name || teachers.value.find((t) => t.id === id)?.name || '-'
}

const allColumns = [
  { prop: 'name', label: '班级名称' }, { prop: 'courseId', label: '课程' },
  { prop: 'classTeacherId', label: '班主任' }, { prop: 'capacity', label: '班容(占用/空余/满班)' },
  { prop: 'minCapacity', label: '最少开班' },
  { prop: 'startDate', label: '开班日期' }, { prop: 'endDate', label: '结班日期' },
  { prop: 'status', label: '状态' }
]
const cols = reactive({ name: true, courseId: true, classTeacherId: true,
  capacity: true, minCapacity: true, startDate: true, endDate: true, status: true })
const onCols = (c) => Object.assign(cols, c)

const visible = ref(false)
const saving = ref(false)
const form = ref({})

/* ===== 班级功能页（列表点击进入）：基本信息 + 统计 + 功能TAB ===== */
const mode = ref('list')
const detailTab = ref('students')
const detailClazz = ref(null)
const detailSchedules = ref([])
const pageTitle = computed(() => (mode.value === 'detail' ? (detailClazz.value?.name || '班级') : '班级管理'))
const statusTextOf = (s) => ['', '招生中', '开课中', '已结课'][s] || '-'
const detailCourse = computed(() => courses.value.find((c) => c.id === detailClazz.value?.courseId) || {})
const detailRoom = computed(() => classroomList.value.find((r) => r.id === detailClazz.value?.classroomId) || null)
const detailRoomName = computed(() => detailRoom.value?.name || '—')
/** 上课校区：优先固定教室的校区，退回课程首个开课校区 */
const detailCampus = computed(() => detailRoom.value?.campus || (detailCourse.value.campuses || [])[0] || '—')
const detailStudying = computed(() => stuCount.value[detailClazz.value?.id] ?? 0)
/** 试听学员数（本班试听名单） */
const auditionCount = computed(() => audStuRows.value.length)
/** 已占用名额 = 正式在读 + 试听学员（试听同样占名额） */
const occupiedTotal = computed(() => (stuInfo.studying || 0) + auditionCount.value)
/** 试听转化率 = 试听名单中已转本班正式学员的占比 */
const auditionRate = computed(() => {
  if (!auditionCount.value) return '0.00'
  const formal = new Set((stuInfo.rows || []).map((r) => r.studentId))
  const converted = audStuRows.value.filter((a) => formal.has(a.studentId)).length
  return ((converted / auditionCount.value) * 100).toFixed(2)
})
/** 满班率 = 在读 ÷ 满班人数 */
const fillRate = computed(() => {
  const cap = Number(detailClazz.value?.capacity || 0)
  return cap > 0 ? Math.min((detailStudying.value / cap) * 100, 100) : 0
})
/** 收费标准：以上课校区口径展示（该校区定价行 + 全部校区通用行） */
const feeStdAll = computed(() => {
  const all = priceMap.value[detailClazz.value?.courseId] || []
  const ps = all.filter((p) => !detailCampus.value
    || p.campus === detailCampus.value || p.campus === '全部校区')
  const hour = ps.filter((p) => p.chargeMode === 1).map((p) => `${fmt(p.hours)}课时=${fmt(p.totalAmount)}元`)
  const time = ps.filter((p) => p.chargeMode === 2).map((p) => `1${p.timePeriod}=${fmt(p.totalAmount)}元`)
  const parts = []
  if (hour.length) parts.push(`按课时（${hour.join('、')}）`)
  if (time.length) parts.push(`按时间（${time.join('、')}）`)
  return parts.join(' ') || (all.length ? '本校区未设收费标准' : '未设置')
})
/** 上课时间：该班未取消课次去重聚合「周三 18:00-20:00」 */
const classTimeText = computed(() => {
  const set = new Set()
  detailSchedules.value.forEach((s) => {
    if (s.status === 2) return
    const d = new Date(s.startTime)
    const wd = '日一二三四五六'[d.getDay()]
    set.add(`周${wd} ${String(s.startTime).slice(11, 16)}-${String(s.endTime || '').slice(11, 16)}`)
  })
  return [...set].join('；')
})
/** 上课记录：已完成课次 */
const doneSchedules = computed(() => detailSchedules.value.filter((s) => s.status === 1))

/* ===== 排课TAB：课程表(FullCalendar，同排课管理) + 新建排课 + 导出 ===== */
const schedView = ref('list')        // list=排课列表 / cal=课程表
const calMode = ref('month')         // day / week / month
const todayStr = () => new Date().toISOString().slice(0, 10)
/** 本班课次时间重叠 = 冲突日程 */
const conflictIds = computed(() => {
  const ids = new Set()
  const list = detailSchedules.value.filter((s) => s.status !== 2)
  for (let i = 0; i < list.length; i++)
    for (let j = i + 1; j < list.length; j++) {
      if (String(list[i].startTime) < String(list[j].endTime)
        && String(list[j].startTime) < String(list[i].endTime)) {
        ids.add(list[i].id); ids.add(list[j].id)
      }
    }
  return ids
})
/** 试听日期集：本班试听学员的办试听时间 */
const auditionDates = computed(() =>
  new Set(audStuRows.value.map((a) => a.auditionDate).filter(Boolean)))
const chipHasAud = (s) => auditionDates.value.has(String(s.startTime).slice(0, 10))
const chipTitle = (s) => {
  const parts = [`${fmtTime(s.startTime)} ~ ${String(s.endTime || '').slice(11, 16)}`,
    teacherName(s.teacherId), s.classroom || '']
  if (conflictIds.value.has(s.id)) parts.push('⚠ 时间冲突')
  if (s.status === 1) parts.push('已记上课')
  if (isOwnClass(s) && chipHasAud(s)) parts.push('含试听学员')
  if (!isOwnClass(s)) parts.push('（其它课程/班级，只读）')
  return parts.filter(Boolean).join(' · ')
}
/** 课次标记：底色 + 角标，5 种状态（已记上课/冲突/我的日程/其它日程/含试听学员）可同时区分 */
const TAG_COLORS = {
  done: { text: '已记上课', color: '#347ab7' },
  conflict: { text: '冲突', color: '#ffb51f' },
  mine: { text: '我的', color: '#129ca9' },
  aud: { text: '试', color: '#f79009' },
  archived: { text: '已归档', color: '#8a99ad' },
  other: { text: '其它课程', color: '#7c8798' }
}
/** 课次状态：0待上课 1已记上课 2已取消 3教师请假 4学生请假 5旷课 */
const SCHED_STATUS = { 0: '待上课', 1: '已记上课', 2: '已取消', 3: '教师请假', 4: '学生请假', 5: '旷课' }
const schedStatusText = (s) => SCHED_STATUS[s?.status] || '待上课'
/** 已归档状态（已取消/请假/旷课）：不可再调整 */
const isArchived = (s) => s?.status != null && s.status !== 0
/** 是否本班课次：本页只允许改本班排课，其它课程课次一律只读 */
const isOwnClass = (s) => !!s && String(s.clazzId) === String(detailClazz.value?.id)
/** 本班课次标记角标：已记上课 / 冲突 / 我的日程 / 含试听学员（可并存） */
function schedTags(s) {
  const tags = []
  if (s.status === 1) tags.push(TAG_COLORS.done)
  else if (isArchived(s)) tags.push(TAG_COLORS.archived)
  if (conflictIds.value.has(s.id)) tags.push(TAG_COLORS.conflict)
  if (detailClazz.value && s.teacherId === detailClazz.value.classTeacherId) tags.push(TAG_COLORS.mine)
  if (chipHasAud(s)) tags.push(TAG_COLORS.aud)
  return tags
}
/** 本班课次底色：已归档 > 冲突 > 已记上课 > 我的日程 > 其它日程 */
function schedBg(s) {
  if (isArchived(s)) return '#9aa8bd'
  if (conflictIds.value.has(s.id)) return '#ffb51f'
  if (s.status === 1) return '#347ab7'
  if (detailClazz.value && s.teacherId === detailClazz.value.classTeacherId) return '#129ca9'
  return '#5bc377'
}
const clazzNameOf = (cid) => clazzList.value.find((c) => c.id === cid)?.name || '其它班级'
/** 本班课次 → 日历事件：仅待上课课次可拖拽调课（月视图统一走弹窗调课） */
function schedEvent(s) {
  const own = isOwnClass(s)
  return {
    id: String(s.id),
    title: `${teacherName(s.teacherId)}${s.classroom ? ' @' + s.classroom : ''}`,
    start: String(s.startTime || '').replace(' ', 'T'),
    end: String(s.endTime || '').replace(' ', 'T'),
    backgroundColor: own ? schedBg(s) : '#94a3b8',
    borderColor: 'transparent',
    editable: own && s.status === 0 && calMode.value !== 'month',
    extendedProps: { raw: s, own, tags: own ? schedTags(s) : [TAG_COLORS.other] }
  }
}
/** 其它课程课次：本班教师在其它班级的课次（只读参考，用于发现教师占用冲突） */
const otherSchedules = ref([])
function otherEvent(s) {
  return {
    id: 'other-' + s.id,
    title: `${clazzNameOf(s.clazzId)} · ${teacherName(s.teacherId)}`,
    start: String(s.startTime || '').replace(' ', 'T'),
    end: String(s.endTime || '').replace(' ', 'T'),
    backgroundColor: '#94a3b8',
    borderColor: 'transparent',
    editable: false,
    extendedProps: { raw: s, own: false, tags: [TAG_COLORS.other] }
  }
}
const fcRef = ref()
const VIEW_MAP = { day: 'timeGridDay', week: 'timeGridWeek', month: 'dayGridMonth' }
/** 课次块内容：时间 + 标题 + 标记角标（DOM 构建，文本安全） */
function renderCalEvent(arg) {
  const p = arg.event.extendedProps
  const s = p.raw
  const box = document.createElement('div')
  box.className = 'cz-ev'
  const l1 = document.createElement('div')
  l1.className = 'cz-ev-t'
  const txt = s ? `${teacherName(s.teacherId)}${s.classroom ? ' @' + s.classroom : ''}` : (arg.event.title || '')
  l1.textContent = (arg.timeText ? arg.timeText + ' ' : '') + txt
  box.appendChild(l1)
  if (s) box.title = chipTitle(s)   // 悬停显示完整课次信息（含冲突/已记上课/含试听学员标记）
  if (p.tags && p.tags.length) {
    const l2 = document.createElement('div')
    l2.className = 'cz-ev-tags'
    for (const t of p.tags) {
      const b = document.createElement('span')
      b.className = 'cz-ev-tag'
      b.style.background = t.color
      b.textContent = t.text
      l2.appendChild(b)
    }
    box.appendChild(l2)
  }
  return { domNodes: [box] }
}
const calOptions = computed(() => ({
  plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin],
  locale: zhCnLocale,
  initialView: VIEW_MAP[calMode.value],
  headerToolbar: { left: 'prev,today,next', center: 'title', right: '' },
  buttonText: { today: '今天' },
  height: 520,
  firstDay: 1,
  allDaySlot: false,
  slotMinTime: '05:00:00',
  slotMaxTime: '23:00:00',
  selectable: true,
  selectMirror: true,
  unselectAuto: true,
  eventDisplay: 'block',
  dayMaxEvents: true,                                  // 月视图一天课次过多时折叠，多出的收进"另外 N 个"
  moreLinkText: (args) => `另外 ${args.num} 个`,        // more 链接文案（默认 +more），点击弹层展示当日全部课次
  events: (info, successCallback, failureCallback) => {
    try {
      successCallback([
        // 已取消课次不进日历：时段已释放（后端冲突校验同样排除），显示出来会挡住该时段重新排课
        ...detailSchedules.value.filter((s) => s.status !== 2).map(schedEvent),
        ...otherSchedules.value.map(otherEvent)
      ])
    } catch (e) { failureCallback(e) }
  },
  eventContent: renderCalEvent,
  eventClick: onCalEventClick,
  eventDrop: onCalEventDrop,
  eventResize: onCalEventDrop,
  select: onCalSelect,
  datesSet: onCalDatesSet
}))
/** 日/周/月切换：通过 API 换视图 */
function onCalMode(v) {
  nextTick(() => {
    fcRef.value?.getApi()?.changeView(VIEW_MAP[v])
    refreshCalEvents()
  })
}
/** 刷新日历课次块（重新拉取事件，角标/底色随状态更新） */
function refreshCalEvents() {
  fcRef.value?.getApi?.()?.refetchEvents?.()
}
/** 视图区间变化：加载本班教师在其它班级的课次（只读） */
async function onCalDatesSet(info) {
  await loadOtherSchedules((info.startStr || '').slice(0, 10), (info.endStr || '').slice(0, 10))
  refreshCalEvents()
}
/** 加载本班教师在其它班级的课次（区间内、排除已取消与本班课次） */
async function loadOtherSchedules(begin, end) {
  const tid = detailClazz.value?.classTeacherId
  if (!tid || !begin || !end) { otherSchedules.value = []; return }
  try {
    const d = await api.schedulePage({ page: 1, size: 200, teacherId: tid, begin, end })
    const cid = String(detailClazz.value.id)
    otherSchedules.value = (d.records || [])
      .filter((r) => String(r.clazzId) !== cid && r.status !== 2)
  } catch (e) { otherSchedules.value = [] }
}

/* ===== 课次操作：点课次块 → 调课 / 取消（其它课程课次只读，不可调整） ===== */
const schedOpVisible = ref(false)
const schedOpRow = ref(null)
const schedOpReadOnly = ref(false)
const schedOpAdjustable = computed(() => !!schedOpRow.value && schedOpRow.value.status === 0)
const schedOpCancellable = computed(() => !!schedOpRow.value && schedOpRow.value.status === 0)
/** 点击课次：打开操作弹窗（其它课程课次只读） */
function onCalEventClick(info) {
  const s = info.event.extendedProps?.raw
  if (!s) return
  schedOpRow.value = s
  schedOpReadOnly.value = !isOwnClass(s)
  schedOpVisible.value = true
}
/** Date → 后端时间格式 yyyy-MM-dd HH:mm:ss */
function fmtLocalDT(dt) {
  if (!dt) return ''
  const p = (n) => String(n).padStart(2, '0')
  return `${dt.getFullYear()}-${p(dt.getMonth() + 1)}-${p(dt.getDate())} ${p(dt.getHours())}:${p(dt.getMinutes())}:${p(dt.getSeconds())}`
}
/** 调课日期禁选：不允许把课次调到过去的日期（今天可选，时段校验兜底拦"今天已过的时刻"） */
const noPastDate = (d) => {
  const t0 = new Date()
  t0.setHours(0, 0, 0, 0)
  return d.getTime() < t0.getTime()
}
/** 拖拽/拉伸调课：仅本班待上课课次可改，其它课程课次一律拒绝并回滚 */
async function onCalEventDrop(info) {
  const s = info.event.extendedProps?.raw
  if (!s || !isOwnClass(s)) {
    ElMessage.warning('该课次属于其它课程/其它班级，不可在班级管理中调整时间')
    info.revert()
    return
  }
  if (s.status !== 0) {
    ElMessage.warning('仅待上课的课次可调整时间')
    info.revert()
    return
  }
  const startTime = fmtLocalDT(info.event.start)
  const endTime = fmtLocalDT(info.event.end)
  if (!startTime || !endTime || new Date(startTime.replace(' ', 'T')).getTime() <= Date.now()) {
    ElMessage.warning('开始时间必须晚于当前时间')
    info.revert()
    return
  }
  try {
    await api.scheduleAdjust(s.id, { startTime, endTime, reason: '日历拖拽调整' })
    ElMessage.success('上课时间已调整')
    await loadDetailSchedules()   // 先拿到调课后的最新数据再刷新日历，否则日历仍显示旧时间
    refreshCalEvents()
  } catch (e) { info.revert() }
}
/** 调课弹窗：由课次操作弹窗进入 */
const schedAdjustVisible = ref(false)
const schedAdjustSaving = ref(false)
const schedAdjustRow = ref(null)
const schedAdjustForm = ref({})
function openAdjustFromOp() {
  const s = schedOpRow.value
  if (!s || !isOwnClass(s)) return ElMessage.warning('该课次属于其它课程/其它班级，不可在班级管理中调整时间')
  schedAdjustRow.value = s
  schedAdjustForm.value = {
    date: String(s.startTime).slice(0, 10),
    range: [String(s.startTime).slice(11, 16), String(s.endTime || '').slice(11, 16)],
    reason: ''
  }
  schedOpVisible.value = false
  schedAdjustVisible.value = true
}
async function saveAdjust() {
  const f = schedAdjustForm.value
  const s = schedAdjustRow.value
  if (!s) return
  if (!isOwnClass(s)) return ElMessage.warning('该课次属于其它课程/其它班级，不可在班级管理中调整时间')
  if (!f.date || !f.range?.[0] || !f.range?.[1]) return ElMessage.warning('请选择完整的上课时段')
  if (f.range[1] <= f.range[0]) return ElMessage.warning('结束时间必须晚于开始时间')
  const startTime = `${f.date} ${f.range[0]}:00`
  const endTime = `${f.date} ${f.range[1]}:00`
  if (new Date(startTime.replace(' ', 'T')).getTime() <= Date.now()) return ElMessage.warning('开始时间必须晚于当前时间')
  schedAdjustSaving.value = true
  try {
    await api.scheduleAdjust(s.id, { startTime, endTime, reason: f.reason || '班级管理调课' })
    ElMessage.success('上课时间已调整')
    schedAdjustVisible.value = false
    await loadDetailSchedules()   // 先拿到调课后的最新数据再刷新日历，否则日历仍显示旧时间
    refreshCalEvents()
  } finally { schedAdjustSaving.value = false }
}
/** 取消课次：二次确认 + 取消原因 */
async function cancelFromOp() {
  const s = schedOpRow.value
  if (!s) return
  if (!isOwnClass(s)) return ElMessage.warning('该课次属于其它课程/其它班级，不可在班级管理中取消')
  let reason = ''
  try {
    const r = await ElMessageBox.prompt('请输入取消原因', '取消课次', {
      inputPlaceholder: '如：教师临时请假', confirmButtonText: '确认取消', cancelButtonText: '返回'
    })
    reason = r.value || ''
  } catch { return }
  await api.scheduleCancel(s.id, { reason })
  ElMessage.success('课次已取消')
  schedOpVisible.value = false
  await loadDetailSchedules()   // 先拿到取消后的最新数据再刷新日历
  refreshCalEvents()
}
/** 时段默认 +N 小时 */
const plusHM = (hm, h) => {
  const [hh, mm] = String(hm).split(':').map(Number)
  const total = hh * 60 + mm + h * 60
  const p = (n) => String(n).padStart(2, '0')
  return `${p(Math.floor(total / 60) % 24)}:${p(total % 60)}`
}
/** 框选/点击空白时段：带入时间快速新建排课（月视图点日期用默认时段 18:00-20:00） */
function onCalSelect(info) {
  const d = info.startStr.slice(0, 10)
  const isMonth = info.view.type === 'dayGridMonth'
  const t0 = isMonth ? '18:00' : (info.startStr.slice(11, 16) || '18:00')
  let t1 = isMonth ? '20:00' : (info.endStr.slice(11, 16) || '')
  if (!t1 || t1 <= t0) t1 = plusHM(t0, 2)
  resetSchedForm()
  schedForm.value = { ...schedForm.value, date: d, range: [t0, t1] }
  syncRangeInput()
  schedCreateVisible.value = true
}

/* ===== 新建/编辑排课：自由排课 + 重复排课（每周/隔周/隔天 + 周几 + 节假日过滤） ===== */
const schedCreateVisible = ref(false)
const schedSaving = ref(false)
const schedForm = ref({})
const schedEditId = ref(null)        // 编辑课次ID：null=新建
const schedMode = ref('free')        // free=自由排课 repeat=重复排课
const schedCustomRange = ref(false)  // 上课时段：预设下拉 / 自定义时间范围
const schedRangePreset = ref('18:00~20:00')
const repeatPreview = ref(null)      // 重复排课预览：{count, lastDate, holidaySkipped}
/** 预设上课时段（高频时段下拉快捷选择，可切自定义 el-time-picker） */
const TIME_PRESETS = ['08:00~10:00', '10:00~12:00', '14:00~16:00', '16:00~18:00', '18:00~20:00', '19:00~21:00']
const WEEK_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const allWeekPicked = computed(() => schedForm.value.weekdays?.length === 7)
function toggleAllWeek(v) { schedForm.value.weekdays = v ? [1, 2, 3, 4, 5, 6, 7] : [] }
/** 预设时段 → range；打开弹窗时反查：命中预设走下拉，否则切自定义 */
function onPresetRange(p) { const [a, b] = String(p).split('~'); schedForm.value.range = [a, b] }
function syncRangeInput() {
  const r = schedForm.value.range || []
  const hit = TIME_PRESETS.find((p) => { const [a, b] = p.split('~'); return a === r[0] && b === r[1] })
  schedCustomRange.value = !hit
  schedRangePreset.value = hit || '18:00~20:00'
}
function toggleCustomRange() {
  schedCustomRange.value = !schedCustomRange.value
  if (!schedCustomRange.value) onPresetRange(schedRangePreset.value)
}
/** 新建排课表单默认值（自由/重复规则字段都初始化，切换模式不丢值） */
function resetSchedForm() {
  schedEditId.value = null
  schedMode.value = 'free'
  schedForm.value = {
    date: todayStr(),
    range: ['18:00', '20:00'],
    teacherId: detailClazz.value?.classTeacherId || null,
    assistantId: null,
    classroomId: detailClazz.value?.classroomId || null,
    topic: '',
    beginDate: todayStr(),
    endMode: 'date',
    endDate: '',
    endCount: 10,
    repeatMode: 1,
    weekdays: [1, 2, 3, 4, 5],
    skipHoliday: true
  }
  syncRangeInput()
  repeatPreview.value = null
}
function openCreateSched() {
  resetSchedForm()
  schedCreateVisible.value = true
}
/** 编辑排课（列表「编辑」）：按课次回填，仅改本次（重复生成的课次也只改这一课次） */
function openEditSched(row) {
  resetSchedForm()
  schedEditId.value = row.id
  schedForm.value = {
    ...schedForm.value,
    date: String(row.startTime || '').slice(0, 10),
    range: [String(row.startTime || '').slice(11, 16), String(row.endTime || '').slice(11, 16)],
    teacherId: row.teacherId,
    assistantId: row.assistantId || null,
    classroomId: row.classroomId || detailClazz.value?.classroomId || null,
    topic: row.topic || ''
  }
  syncRangeInput()
  schedCreateVisible.value = true
}
/** 列表「取消排课」：输入取消原因后取消（同日历取消课次） */
async function cancelSchedRow(row) {
  let reason = ''
  try {
    const r = await ElMessageBox.prompt('请输入取消原因', '取消排课', {
      inputPlaceholder: '如：教师临时请假', confirmButtonText: '确认取消', cancelButtonText: '返回'
    })
    reason = r.value || ''
  } catch { return }
  await api.scheduleCancel(row.id, { reason })
  ElMessage.success('课次已取消')
  await loadDetailSchedules()
  refreshCalEvents()
}
/** 重复排课提交参数（预览与提交共用） */
function repeatPayload() {
  const f = schedForm.value
  return {
    clazzId: curClazz.value?.id,
    teacherId: f.teacherId,
    assistantId: f.assistantId || null,
    classroomId: f.classroomId,
    topic: f.topic || null,
    repeatMode: f.repeatMode,
    weekdays: f.repeatMode === 4 ? [] : f.weekdays,
    beginDate: f.beginDate,
    endMode: f.endMode,
    endDate: f.endMode === 'count' ? null : f.endDate,
    endCount: f.endMode === 'count' ? f.endCount : null,
    skipHoliday: !!f.skipHoliday,
    beginTime: f.range?.[0],
    endTime: f.range?.[1]
  }
}
/** 重复规则变化 → 防抖调后端预览（弹窗"预计排课 N 次 / 最后一次日期"提示） */
let schedPreviewTimer = null
watch([
  () => schedMode.value,
  () => schedCreateVisible.value,
  () => schedForm.value.beginDate, () => schedForm.value.endMode, () => schedForm.value.endDate,
  () => schedForm.value.endCount, () => schedForm.value.repeatMode, () => schedForm.value.skipHoliday,
  () => (schedForm.value.weekdays || []).join(',')
], () => {
  repeatPreview.value = null
  if (schedMode.value !== 'repeat' || !schedCreateVisible.value) return
  clearTimeout(schedPreviewTimer)
  schedPreviewTimer = setTimeout(async () => {
    const f = schedForm.value
    if (!f.beginDate) return
    if (f.endMode !== 'count' && !f.endDate) return
    if (f.repeatMode !== 4 && !f.weekdays?.length) return
    try { repeatPreview.value = await api.scheduleRepeatPreview(repeatPayload()) } catch { /* 预览失败不打扰填写 */ }
  }, 300)
})
/** 列表「上课时间」列：MM-dd 周X */
const WEEK_CN = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
function fmtDateWeek(t) {
  const s = String(t || '')
  if (s.length < 10) return '-'
  const d = new Date(s.slice(0, 10) + 'T00:00:00')
  return `${s.slice(5, 10)} ${WEEK_CN[d.getDay()]}`
}
async function saveSched() {
  const f = schedForm.value
  if (!f.range?.[0] || !f.range?.[1]) return ElMessage.warning('请选择上课时段')
  if (String(f.range[1]) <= String(f.range[0])) return ElMessage.warning('结束时间必须晚于开始时间')
  if (!f.teacherId) return ElMessage.warning('请选择上课教师')
  if (!f.classroomId) return ElMessage.warning('请选择上课教室')
  // 编辑：更新本课次（时间/时段/教师/助教/教室/主题）
  if (schedEditId.value) {
    if (!f.date) return ElMessage.warning('请选择上课日期')
    const startTime = `${f.date} ${f.range[0]}:00`
    const endTime = `${f.date} ${f.range[1]}:00`
    if (new Date(startTime.replace(' ', 'T')).getTime() <= Date.now()) return ElMessage.warning('开始时间必须晚于当前时间')
    schedSaving.value = true
    try {
      await api.scheduleUpdate(schedEditId.value, {
        startTime, endTime, teacherId: f.teacherId,
        assistantId: f.assistantId || null, classroomId: f.classroomId, topic: f.topic || null
      })
      ElMessage.success('排课已更新')
      schedCreateVisible.value = false
      await loadDetailSchedules()
      refreshCalEvents()
    } finally { schedSaving.value = false }
    return
  }
  // 新建·自由排课：单次
  if (schedMode.value === 'free') {
    if (!f.date) return ElMessage.warning('请选择上课日期')
    const startTime = `${f.date} ${f.range[0]}:00`
    const endTime = `${f.date} ${f.range[1]}:00`
    if (new Date(startTime.replace(' ', 'T')).getTime() <= Date.now()) return ElMessage.warning('开始时间必须晚于当前时间')
    schedSaving.value = true
    try {
      await api.scheduleCreate({
        clazzId: curClazz.value.id,
        courseId: curClazz.value.courseId,
        teacherId: f.teacherId,
        assistantId: f.assistantId || null,
        classroomId: f.classroomId,
        topic: f.topic || null,
        schedType: 0,
        startTime, endTime
      })
      ElMessage.success('排课成功')
      schedCreateVisible.value = false
      await loadDetailSchedules()
      refreshCalEvents()
    } finally { schedSaving.value = false }
    return
  }
  // 新建·重复排课：按规则批量生成，逐次校验冲突，失败汇总跳过
  if (!f.beginDate) return ElMessage.warning('请选择开始日期')
  if (f.endMode !== 'count' && !f.endDate) return ElMessage.warning('请选择结束日期')
  if (f.endMode !== 'count' && String(f.endDate) < String(f.beginDate)) return ElMessage.warning('结束日期不能早于开始日期')
  if (f.endMode === 'count' && !f.endCount) return ElMessage.warning('请填写排课次数')
  if (f.repeatMode !== 4 && !f.weekdays?.length) return ElMessage.warning('请选择周几上课')
  schedSaving.value = true
  try {
    const r = await api.scheduleRepeat(repeatPayload())
    schedCreateVisible.value = false
    await loadDetailSchedules()
    refreshCalEvents()
    const skipped = r.skipped || []
    ElMessage.success(`重复排课完成：成功 ${r.created} 次${skipped.length ? `，跳过 ${skipped.length} 次` : ''}`)
    if (skipped.length) {
      ElMessageBox.alert(
        skipped.slice(0, 50).map((x) => `${x.date}：${x.reason}`).join('<br>'),
        `跳过明细（共 ${skipped.length} 条）`, { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' })
    }
  } finally { schedSaving.value = false }
}
/** 导出课表：当前班全部课次（时间/教师/教室/状态/备注） */
function exportSchedules() {
  if (!detailSchedules.value.length) return ElMessage.warning('暂无排课可导出')
  exportXlsx(`课表_${curClazz.value?.name || ''}`, [
    { label: '上课时间', prop: 'startTime' },
    { label: '结束时间', prop: 'endTime' },
    { label: '教师', prop: 'teacherId', fmt: (v) => teacherName(v) },
    { label: '教室', prop: 'classroom' },
    { label: '状态', prop: 'status', fmt: (v) => ['待上课', '已完成', '已取消'][v] || '-' },
    { label: '冲突', prop: 'id', fmt: (v) => (conflictIds.value.has(v) ? '⚠ 时间冲突' : '') },
    { label: '备注', prop: 'remark' }
  ], detailSchedules.value)
}

/** 进入班级功能页：加载学员、排课与课程定价 */
function openDetailPage(row, tab = 'students') {
  mode.value = 'detail'
  detailTab.value = tab
  detailClazz.value = row
  curClazz.value = row // 添加学生/调班/试听弹窗复用
  loadStudents()
  schedPager.page = 1   // 换班级后排课列表回到第1页
  loadDetailSchedules()
  loadAuditionStudents()
  resetAuditionForm()
  // 班级清单（排课TAB「其它课程」课次块显示班级名用）
  if (!clazzList.value.length) api.clazzList().then((r) => (clazzList.value = r || [])).catch(() => {})
  const cid = row.courseId
  if (cid && !priceMap.value[cid]) {
    api.coursePrices(cid).then((ps) => (priceMap.value[cid] = ps || [])).catch(() => (priceMap.value[cid] = []))
  }
}
async function loadDetailSchedules() {
  try {
    // 全量缓存（上限500，重复排课一次可达200条）：日历视图/冲突检测/导出课表/上课记录TAB 共用
    detailSchedules.value = (await api.schedulePage(
      { page: 1, size: 500, clazzId: detailClazz.value.id })).records || []
  } catch (e) { detailSchedules.value = [] }
  loadSchedPage()   // 排课列表视图（分页）同步刷新
}

/* ===== 排课列表视图：服务端分页 + 表头排序（detailSchedules 是全量缓存，列表单独取当前页） ===== */
const schedListRows = ref([])
const schedPager = reactive({ page: 1, size: 20, total: 0 })
const schedSort = reactive({ sortBy: 'createTime', direction: 'desc' })
async function loadSchedPage() {
  if (!detailClazz.value) { schedListRows.value = []; schedPager.total = 0; return }
  try {
    const r = await api.schedulePage({ page: schedPager.page, size: schedPager.size,
      clazzId: detailClazz.value.id, sortBy: schedSort.sortBy, direction: schedSort.direction })
    schedListRows.value = r.records || []
    schedPager.total = Number(r.total || 0)
  } catch (e) { schedListRows.value = []; schedPager.total = 0 }
}
function onSchedPage() { loadSchedPage() }
function onSchedSize() { schedPager.page = 1; loadSchedPage() }
/** 表头排序（sortable=custom）：prop 须在后端白名单内；取消排序恢复默认 创建时间倒序 */
function onSchedSort({ prop, order }) {
  if (!order) { schedSort.sortBy = 'createTime'; schedSort.direction = 'desc' }
  else { schedSort.sortBy = prop; schedSort.direction = order === 'ascending' ? 'asc' : 'desc' }
  schedPager.page = 1
  loadSchedPage()
}
/** 序号跨页连续：(当前页-1)×页大小 + 行号 */
const schedRowNo = (i) => (schedPager.page - 1) * schedPager.size + i + 1
function backToList() {
  mode.value = 'list'
  detailClazz.value = null
  load()
}

/** 功能页时点击/双击左侧「班级管理」菜单 → 视同返回列表 */
const route = useRoute()
function onMenuSelfClick(e) {
  if (e.detail !== route.path || mode.value === 'list') return
  backToList()
}
onMounted(() => window.addEventListener('menu-self-click', onMenuSelfClick))
onBeforeUnmount(() => window.removeEventListener('menu-self-click', onMenuSelfClick))

/* ===== 试听学员TAB：办理试听（学员混合搜索 + 试听活动/日程试听选择） ===== */
const audStuRows = ref([])
const audLoading = ref(false)
const audSaving = ref(false)
const handleAudVisible = ref(false)
const audStuId = ref(null)
const audSearching = ref(false)
const audStuCands = ref([])
const audPickedStu = ref(null)
const audPickedAct = ref(null)
const allStudents = ref([])

const roomName = (id) => classroomList.value.find((r) => r.id === id)?.name || ''
/** 班级校区：教室校区优先，退回课程首个校区 */
const clazzCampus = (c) => {
  const room = classroomList.value.find((r) => r.id === c.classroomId)
  return room?.campus || (courses.value.find((x) => x.id === c.courseId)?.campuses || [])[0] || ''
}

async function loadAuditionStudents() {
  audLoading.value = true
  try {
    audStuRows.value = (await api.clazzAuditionStudents(curClazz.value.id)) || []
  } finally { audLoading.value = false }
}

/** 学员候选统一映射（仅真实学员：studentList 已过滤兼任档案） */
const mapAudStudents = (list) => (list || []).map((s) =>
  ({ studentId: s.id, name: s.name, phone: s.phone, grade: s.grade, school: s.school }))
/** 混合搜索：姓名/手机号一个框（参考报名/续报学员搜索） */
async function searchAudStudents(kw) {
  if (!kw || !kw.trim()) { audStuCands.value = []; return }
  audSearching.value = true
  try {
    if (!allStudents.value.length) allStudents.value = (await api.studentList()) || []
    const k = kw.trim().toLowerCase()
    audStuCands.value = mapAudStudents(allStudents.value.filter((s) =>
      (s.name || '').toLowerCase().includes(k) || (s.phone || '').includes(k)))
  } finally { audSearching.value = false }
}
/** 双击搜索框：无关键字全量检索 */
async function loadAllAudStudents() {
  if (audSearching.value) return
  audSearching.value = true
  try {
    if (!allStudents.value.length) allStudents.value = (await api.studentList()) || []
    audStuCands.value = mapAudStudents(allStudents.value)
  } finally { audSearching.value = false }
}
function onPickAudStu(id) {
  audPickedStu.value = audStuCands.value.find((s) => s.studentId === id) || null
}
function openHandleAudition() {
  resetAuditionForm()
  handleAudVisible.value = true
}
function resetAuditionForm() {
  audStuId.value = null
  audStuCands.value = []
  audPickedStu.value = null
  audPickedAct.value = null
}
async function confirmAudition() {
  if (!audPickedStu.value) return ElMessage.warning('请先在「学员信息」区搜索并选择学员')
  if (!audPickedAct.value) return ElMessage.warning('请先点击「选择试听活动」或「选择日程试听」')
  if (!audPickedAct.value.name?.trim()) return ElMessage.warning('请填写活动名称')
  const a = audPickedAct.value
  audSaving.value = true
  try {
    await api.clazzAddAudition(curClazz.value.id, {
      studentId: audPickedStu.value.studentId,
      studentName: audPickedStu.value.name,
      source: a.source,
      auditionId: a.auditionId || undefined,
      auditionName: a.name,
      courseName: a.courseName || undefined,
      teacherName: a.teacherName || undefined,
      classroomName: a.classroomName || undefined,
      timePeriod: a.timePeriod || undefined,
      campus: a.campus || undefined,
      auditionDate: a.date || undefined,
      assistantName: a.assistantName || undefined
    })
    ElMessage.success(`已为 ${audPickedStu.value.name} 办理试听`)
    handleAudVisible.value = false
    resetAuditionForm()
    loadAuditionStudents()
  } finally { audSaving.value = false }
}
async function removeAudition(id) {
  await api.clazzDelAudition(id)
  ElMessage.success('已移除')
  loadAuditionStudents()
}

/* ===== 批量添加学员（本班日程试听）：多选学员一键登记 ===== */
const batchAudVisible = ref(false)
const batchAudKw = ref('')
const batchAudSearching = ref(false)
const batchAudSaving = ref(false)
const batchAudCands = ref([])
const batchAudSel = ref([])
let batchAudTimer = null
watch(batchAudKw, () => {
  clearTimeout(batchAudTimer)
  batchAudTimer = setTimeout(() => searchBatchAudStu(batchAudKw.value), 300)
})
async function searchBatchAudStu(kw) {
  const k = (kw || '').trim()
  if (!k) { batchAudCands.value = []; return }
  batchAudSearching.value = true
  try {
    if (!allStudents.value.length) allStudents.value = (await api.studentList()) || []
    batchAudCands.value = allStudents.value
      .filter((s) => (s.name || '').toLowerCase().includes(k.toLowerCase()) || (s.phone || '').includes(k))
      .map((s) => ({ studentId: s.id, name: s.name, phone: s.phone, grade: s.grade }))
  } finally { batchAudSearching.value = false }
}
async function loadAllBatchAudStu() {
  if (batchAudSearching.value) return
  batchAudSearching.value = true
  try {
    if (!allStudents.value.length) allStudents.value = (await api.studentList()) || []
    batchAudCands.value = allStudents.value
      .map((s) => ({ studentId: s.id, name: s.name, phone: s.phone, grade: s.grade }))
  } finally { batchAudSearching.value = false }
}
function openBatchAudStu() {
  batchAudKw.value = ''
  batchAudCands.value = []
  batchAudSel.value = []
  batchAudVisible.value = true
}
async function confirmBatchAudStu() {
  if (!batchAudSel.value.length) return ElMessage.warning('请先勾选要办理试听的学员')
  const c = curClazz.value
  const t = clazzTimeMap.value[c.id]
  const room = classroomList.value.find((r) => r.id === c.classroomId)
  batchAudSaving.value = true
  try {
    const msg = await api.clazzAddAuditionsBatch(c.id, {
      items: batchAudSel.value.map((s) => ({
        studentId: s.studentId, studentName: s.name,
        source: 2, auditionId: null, auditionName: `${c.name}（日程试听）`,
        courseName: courseName(c.courseId), teacherName: teacherName(c.classTeacherId) || '',
        classroomName: room?.name || '', timePeriod: t?.text || '', campus: clazzCampus(c),
        auditionDate: c.startDate, assistantName: ''
      }))
    })
    ElMessage.success(msg || '批量办理成功')
    batchAudVisible.value = false
    loadAuditionStudents()
  } finally { batchAudSaving.value = false }
}

/* 选择试听活动弹窗：试听管理记录（类型/日期/校区/课程/老师筛选）；行选中后点确定回填 */
const actDlgVisible = ref(false)
const actTableRef = ref(null)
const actPickedRow = ref(null)
const actF = reactive({ type: null, range: null, campus: null, courseName: null, teacherName: null })
const auditions = ref([])
const actCourseOptions = computed(() => [...new Set(auditions.value.map((a) => a.courseName).filter(Boolean))])
const actTeacherOptions = computed(() => [...new Set(auditions.value.map((a) => a.teacherName).filter(Boolean))])
const actFiltered = computed(() => auditions.value.filter((a) => {
  // 仅本班相关的试听活动（插班试听关联本班）；开班试听为独立活动，在试听管理详情查看
  if (a.relateClazzId !== curClazz.value?.id) return false
  if (actF.type != null && a.type !== actF.type) return false
  if (actF.campus && a.campus !== actF.campus) return false
  if (actF.courseName && a.courseName !== actF.courseName) return false
  if (actF.teacherName && a.teacherName !== actF.teacherName) return false
  const [b, e] = actF.range || []
  if (b && (a.auditionDate || '') < b) return false
  if (e && (a.auditionDate || '') > e) return false
  return true
}))
async function openActDlg() {
  if (!auditions.value.length) {
    try { auditions.value = (await api.auditionList()) || [] } catch (e) { auditions.value = [] }
  }
  Object.assign(actF, { type: null, range: null, campus: null, courseName: null, teacherName: null })
  // 重置行选中态（修复：清空后重开点同一行不触发 current-change）
  actPickedRow.value = null
  nextTick(() => actTableRef.value?.setCurrentRow(null))
  actDlgVisible.value = true
}
function confirmActPick() {
  if (!actPickedRow.value) return ElMessage.warning('请先点击选择一条试听活动')
  const row = actPickedRow.value
  audPickedAct.value = { source: 1, auditionId: row.id, name: row.name,
    courseName: row.courseName, teacherName: row.teacherName,
    classroomName: row.classroomName, timePeriod: row.timePeriod, campus: row.campus,
    date: row.auditionDate, assistantName: row.assistantName, plannedCount: row.plannedCount }
  actDlgVisible.value = false
  ElMessage.success(`已选择试听活动「${row.name}」，项目信息已回填`)
}

/* 选择日程试听弹窗：已成班课（日期/校区/课程/班级/老师筛选），时间段由排课推导；行选中后点确定回填 */
const schDlgVisible = ref(false)
const schTableRef = ref(null)
const schPickedRow = ref(null)
const schLoading = ref(false)
const schF = reactive({ range: null, campus: null, courseName: null, clazzName: null, teacherName: null })
/** clazzId -> { text: 时段文本, dates: 上课日期集 }（打开弹窗时一次性拉排课推导） */
const clazzTimeMap = ref({})
const schClasses = computed(() => clazzList.value.filter((c) => {
  const course = courses.value.find((x) => x.id === c.courseId)
  return course && bankeVal.value != null && course.teachMode === bankeVal.value && c.status !== 3
}))
const schCourseOptions = computed(() =>
  [...new Set(schClasses.value.map((c) => courseName(c.courseId)).filter((x) => x && x !== '-'))])
const schClazzOptions = computed(() => schClasses.value.map((c) => c.name))
const schTeacherOptions = computed(() =>
  [...new Set(schClasses.value.map((c) => teacherName(c.classTeacherId)).filter(Boolean))])
const schFiltered = computed(() => schClasses.value.filter((c) => {
  // 日程试听仅本班（本TAB即本班试听课）
  if (c.id !== curClazz.value?.id) return false
  if (schF.courseName && courseName(c.courseId) !== schF.courseName) return false
  if (schF.clazzName && c.name !== schF.clazzName) return false
  if (schF.teacherName && teacherName(c.classTeacherId) !== schF.teacherName) return false
  if (schF.campus && clazzCampus(c) !== schF.campus) return false
  const [b, e] = schF.range || []
  if (b || e) {
    const t = clazzTimeMap.value[c.id]
    if (!t || !t.dates.length) return false
    if (b && !t.dates.some((d) => d >= b)) return false
    if (e && !t.dates.some((d) => d <= e)) return false
  }
  return true
}))
async function openSchDlg() {
  Object.assign(schF, { range: null, campus: null, courseName: null, clazzName: null, teacherName: null })
  // 重置行选中态（修复：清空后重开点同一行不触发 current-change）
  schPickedRow.value = null
  nextTick(() => schTableRef.value?.setCurrentRow(null))
  schDlgVisible.value = true
  if (Object.keys(clazzTimeMap.value).length) return
  schLoading.value = true
  try {
    const rs = (await api.schedulePage({ page: 1, size: 500 })).records || []
    const map = {}
    rs.filter((s) => s.status !== 2 && s.clazzId).forEach((s) => {
      const m = map[s.clazzId] || (map[s.clazzId] = { texts: new Set(), dates: new Set() })
      const d = String(s.startTime)
      m.dates.add(d.slice(0, 10))
      m.texts.add(`周${'日一二三四五六'[new Date(d).getDay()]} ${d.slice(11, 16)}-${String(s.endTime || '').slice(11, 16)}`)
    })
    clazzTimeMap.value = Object.fromEntries(Object.entries(map).map(([k, v]) =>
      [k, { text: [...v.texts].slice(0, 2).join('；'), dates: [...v.dates].sort() }]))
  } catch (e) { /* 无排课 */ } finally { schLoading.value = false }
}
function confirmSchPick() {
  if (!schPickedRow.value) return ElMessage.warning('请先点击选择一条班级日程')
  const row = schPickedRow.value
  const t = clazzTimeMap.value[row.id]
  audPickedAct.value = { source: 2, auditionId: null, name: `${row.name}（日程试听）`,
    courseName: courseName(row.courseId), teacherName: teacherName(row.classTeacherId) || '',
    classroomName: roomName(row.classroomId), timePeriod: t?.text || '', campus: clazzCampus(row),
    date: row.startDate, assistantName: '',
    plannedCount: Math.max((row.capacity || 0) - (stuCount.value[row.id] ?? 0), 0) || row.capacity }
  schDlgVisible.value = false
  ElMessage.success(`已选择班级「${row.name}」的日程试听，项目信息已回填`)
}
/** 列表行点击进入功能页（勾选/操作列不触发） */
function onRowClick(row, column) {
  if (column && (column.type === 'selection' || column.label === '操作')) return
  openDetailPage(row)
}

function openEdit(row) {
  form.value = row ? { ...row } : { capacity: 20, minCapacity: null, studentHours: null,
    teacherHours: null, classroomId: null, startDate: null, endDate: null, status: 1 }
  // 已有班主任（教师档案ID）回显为对应员工 userId
  if (row?.classTeacherId) {
    const cand = staffCandidates.value.find((c) => c.teacherId === row.classTeacherId)
    form.value.classTeacherUserId = cand ? cand.userId : undefined
  }
  visible.value = true
}
async function save() {
  if (!form.value.name) return ElMessage.warning('班级名称不能为空')
  if (!form.value.courseId) return ElMessage.warning('请点击「选择课程」选择课程')
  if (form.value.minCapacity != null && form.value.capacity != null
      && form.value.minCapacity > form.value.capacity)
    return ElMessage.warning(`最少开班人数（${form.value.minCapacity} 名）不能大于满班人数（${form.value.capacity} 名）`)
  if (form.value.startDate && form.value.endDate && form.value.endDate < form.value.startDate)
    return ElMessage.warning('结班日期不能早于开班日期')
  // 教室容量校验：可容纳人数 < 满班人数时提示更换教室
  if (form.value.classroomId != null) {
    const room = classroomList.value.find((r) => r.id === form.value.classroomId)
    if (room && Number(room.capacity || 0) < Number(form.value.capacity || 0))
      return ElMessage.warning(`教室「${room.name}」可容纳 ${room.capacity} 人，小于满班人数 ${form.value.capacity} 人，请更换更大的教室`)
  }
  // 后端仅按 classTeacherUserId 解析班主任；清空选择时须把原教师档案ID一并置空
  if (!form.value.classTeacherUserId) form.value.classTeacherId = null
  saving.value = true
  try {
    if (form.value.id) await api.clazzUpdate(form.value)
    else await api.clazzSave(form.value)
    ElMessage.success('已保存')
    visible.value = false
    // 班主任可能在保存时才补建教师档案，刷新映射表避免列表显示 '-'
    await refreshTeacherMaps()
    load()
  } finally { saving.value = false }
}
function onRowCommand(cmd, row) {
  if (cmd === 'detail') openDetailPage(row)
  else if (cmd === 'students') openDetailPage(row, 'students')
  else if (cmd === 'edit') openEdit(row, null)
  else if (cmd === 'del') remove(row.id)
}

async function remove(id) {
  await api.clazzDel(id)
  ElMessage.success('已删除')
  load()
}

function doExport() {
  exportXlsx('班级列表', [
      { label: '班级名称', prop: 'name' }, { label: '最少开班', prop: 'minCapacity' },
      { label: '满班人数', prop: 'capacity' },
      { label: '学生每次扣除课时', prop: 'studentHours' }, { label: '教师每次获得课时', prop: 'teacherHours' },
      { label: '开班日期', prop: 'startDate' }, { label: '结班日期', prop: 'endDate' }
    ], rows.value)
}

/* ===== 选择课程弹窗（单选，仅班课；参考报名/续报选课弹窗） ===== */
const courseDlgVisible = ref(false)
const courseKw = ref('')
const dlgCategory = ref(null)
/** 当前点击选中的课程行（highlight-current-row） */
const dlgPicked = ref(null)
/** 「班课」的字典值（班级只开班课，一对一不在此选班） */
const bankeVal = computed(() => {
  const hit = dictStore.options('class_type').find((d) => d.label === '班课')
  return hit ? Number(hit.value) : null
})
const feeModeText = (c) => {
  const modes = []
  if (c.chargeHour === 1) modes.push('按课时')
  if (c.chargeTime === 1) modes.push('按时间')
  return modes.length ? modes.join(',') : '—'
}
/** 课程定价缓存：courseId -> 定价行（学费标准列展示用，打开弹窗时懒加载） */
const priceMap = ref({})
const fmt = (n) => String(Number(n ?? 0))
/** 学费标准：按课时定价首行「48课时=4800元」 */
const feeStdText = (c) => {
  const p = (priceMap.value[c.id] || []).find((x) => x.chargeMode === 1)
  return p ? `${fmt(p.hours)}课时=${fmt(p.totalAmount)}元` : '—'
}
const courseFiltered = computed(() => {
  const k = (courseKw.value || '').trim().toLowerCase()
  return courses.value.filter((c) => {
    if (c.status !== 1) return false
    // 仅展示班课课程
    if (bankeVal.value != null && c.teachMode !== bankeVal.value) return false
    if (k && !(c.name || '').toLowerCase().includes(k)
      && !(dictStore.label('course_category', c.category) || '').toLowerCase().includes(k)) return false
    if (dlgCategory.value != null && c.category !== dlgCategory.value) return false
    return true
  })
})
/** 该课程已开班级数（含已结课） */
const clazzCountOf = (cid) => clazzList.value.filter((c) => c.courseId === cid).length
function openCourseDlg() {
  courseKw.value = ''
  // 已选课程若为一对一（历史数据）仍可回显选中，不丢失
  dlgPicked.value = courses.value.find((c) => c.id === form.value.courseId) || null
  // 学费标准：懒加载在售课程的按课时定价（未缓存过才拉取）
  courses.value.filter((c) => c.status === 1 && !priceMap.value[c.id]).forEach((c) => {
    api.coursePrices(c.id).then((ps) => (priceMap.value[c.id] = ps || [])).catch(() => (priceMap.value[c.id] = []))
  })
  courseDlgVisible.value = true
}
function confirmCourseDlg() {
  if (!dlgPicked.value) return ElMessage.warning('请先点击选择一门课程')
  form.value.courseId = dlgPicked.value.id
  courseDlgVisible.value = false
}

const sel = ref([])
async function batchRemove() {
  await ElMessageBox.confirm(`确认删除选中的 ${sel.value.length} 条班级数据？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(sel.value.map((r) => api.clazzDel(r.id)))
  const ok = results.filter((x) => x.status === 'fulfilled').length
  const fail = results.length - ok
  ElMessage.success(`批量删除完成：成功 ${ok} 条` + (fail ? `，失败 ${fail} 条` : ''))
  sel.value = []
  load()
}

/** 刷新班主任名称映射（候选员工 + 教师档案，列表/详情按 classTeacherId 反查姓名） */
async function refreshTeacherMaps() {
  staffCandidates.value = (await api.clazzTeacherCandidates()) || []
  teachers.value = (await api.teacherList()) || []
}

/* ===== 班级学生管理（功能页学员TAB数据源） ===== */
const stuLoading = ref(false)
const curClazz = ref(null)
const stuInfo = reactive({ capacity: 0, studying: 0, rows: [] })
/** 列表容量占用统计：clazzId -> 在读人数 */
const stuCount = ref({})
const clazzList = ref([])

async function loadStuCounts() {
  clazzList.value = (await api.clazzList()) || []
  const map = {}
  const all = await api.enrollPage({ page: 1, size: 1000, status: 1 })
  for (const e of (all.records || [])) {
    if (e.clazzId) map[e.clazzId] = (map[e.clazzId] || 0) + 1
  }
  stuCount.value = map
}

async function loadStudents() {
  stuLoading.value = true
  try {
    const d = await api.clazzStudents(curClazz.value.id)
    Object.assign(stuInfo, d || {})
  } finally { stuLoading.value = false }
}
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-' }

/* ===== 添加学员（班级功能页-学员TAB）：本班课程的班课在读学员多选加入 ===== */
const addStuVisible = ref(false)
const addLoading = ref(false)
const addSaving = ref(false)
const addKw = ref('')
const addCampus = ref(null)
const addAssigned = ref(null)   // 分班状态：true已分班 / false未分班 / null全部
const addRange = ref(null)      // 报名时间范围
const addRows = ref([])
const addSel = ref([])
const campusOptions = computed(() =>
  dictStore.options('campus').map((d) => d.label).filter((l) => l !== '全部校区'))

/** 筛选联动：300ms 防抖重新拉取候选 */
let addTimer = null
watch([addKw, addCampus, addAssigned, addRange], () => {
  clearTimeout(addTimer)
  addTimer = setTimeout(loadAddCandidates, 300)
})
async function loadAddCandidates() {
  if (!curClazz.value) return
  addLoading.value = true
  try {
    addRows.value = (await api.clazzAddCandidates(curClazz.value.id, {
      keyword: addKw.value || undefined,
      campus: addCampus.value || undefined,
      assigned: addAssigned.value == null ? undefined : addAssigned.value,
      begin: addRange.value?.[0], end: addRange.value?.[1]
    })) || []
  } finally { addLoading.value = false }
}
function openAddStu() {
  addKw.value = ''
  addCampus.value = null
  addAssigned.value = null
  addRange.value = null
  addSel.value = []
  addStuVisible.value = true
  loadAddCandidates()
}
/** 确定加入：批量把所选学员的本课程在读报名单分入本班 */
async function confirmAddStudents() {
  if (!addSel.value.length) return ElMessage.warning('请先勾选要加入的学员')
  addSaving.value = true
  try {
    const msg = await api.clazzAddStudents(curClazz.value.id,
      { studentIds: addSel.value.map((r) => r.studentId) })
    ElMessage.success(msg || '已加入班级')
    addStuVisible.value = false
    loadStudents()
    loadStuCounts()
  } finally { addSaving.value = false }
}

/* 调班 */
const transferVisible = ref(false)
const transferStu = ref(null)
const transferTargetId = ref(null)
const transferSaving = ref(false)
const transferTargets = computed(() =>
  clazzList.value.filter((c) => c.id !== curClazz.value?.id && c.status !== 3))

function openTransfer(row) {
  // 注意：此处不能直接赋值为 api.clazzList() 的 Promise（会让 clazzList.value 变成 Promise，后续 .filter/.find 报错）
  if (!clazzList.value.length) api.clazzList().then((r) => (clazzList.value = r || [])).catch(() => {})
  transferStu.value = row
  transferTargetId.value = null
  transferVisible.value = true
}
async function doTransfer() {
  if (!transferTargetId.value) return ElMessage.warning('请选择目标班级')
  transferSaving.value = true
  try {
    await api.clazzTransfer(curClazz.value.id, {
      studentId: transferStu.value.studentId, toClazzId: transferTargetId.value
    })
    ElMessage.success('调班成功')
    transferVisible.value = false
    loadStudents()
    loadStuCounts()
  } finally { transferSaving.value = false }
}

onMounted(async () => {
  load()
  dictStore.load()
  courses.value = (await api.courseList()) || []
  classroomList.value = (await api.classroomList()) || []
  await refreshTeacherMaps()
  // 容量占用统计 + 选课弹窗「开班数」（列表首屏即显示占用，不再等添加/调班后刷新）
  loadStuCounts()
})
</script>

<style scoped>
.more-trigger {
  display: inline-flex; align-items: center; gap: 3px;
  font-size: 13px; color: #475467;
  padding: 3px 10px; border-radius: 12px;
  border: 1px solid #e4edf7;
  background: linear-gradient(180deg, #ffffff, #f7fafd);
  cursor: pointer; white-space: nowrap;
  transition: all 0.2s ease;
}
.more-trigger:hover, .more-trigger.open {
  color: #155EEF; border-color: rgba(21, 94, 239, 0.45);
  background: linear-gradient(180deg, #ffffff, #f0f7ff);
  box-shadow: 0 3px 10px rgba(21, 94, 239, 0.16);
}
.more-arrow { transition: transform 0.2s ease; }
.more-trigger.open .more-arrow { transform: rotate(180deg); }
</style>

<style scoped>
/* 课程选择：只读展示 + 选择按钮 */
.course-pick { display: flex; gap: 8px; width: 100%; }
.course-pick-input { flex: 1; }
.course-pick-input :deep(input) { cursor: pointer; }
.pick-arrow { color: #b3c5da; }
/* 每次上课区域：学生扣除/教师获得 各占一行，避免挤一行布局乱 */
.per-class-box {
  display: flex; flex-direction: column; gap: 8px; padding: 10px 12px;
  border: 1px dashed #dbe7fb; border-radius: 8px; background: #fafcff;
}
.pc-item { display: flex; align-items: center; gap: 6px; }
.pc-label { font-size: 12.5px; color: #667c99; white-space: nowrap; }
.pc-unit { font-size: 12.5px; color: #8fa8c4; }
/* 班容人数：最少 ~ 满班 */
.cap-box { display: flex; align-items: center; gap: 6px; }
.cap-label { font-size: 12.5px; color: #667c99; white-space: nowrap; }
.cap-unit { font-size: 12.5px; color: #8fa8c4; margin-left: 2px; }
/* ===== 班级功能页 ===== */
.cd-info-panel { padding: 14px 18px; }
/* 基本信息区：label+value 网格，一行四项 */
.class-info { display: flex; flex-wrap: wrap; row-gap: 4px; }
.class-info-item { display: flex; align-items: center; width: 25%; min-width: 200px; padding: 5px 0; }
.class-info-item.class-info-wide { width: 50%; min-width: 320px; }
.class-item-label { flex-shrink: 0; width: 62px; color: #8fa8c4; font-size: 12.5px; }
.class-item-text { color: #344054; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 班级人数进度条（绿） */
.full-power { display: inline-block; width: 110px; height: 8px; border-radius: 4px; background: #e8f0fb; overflow: hidden; vertical-align: middle; margin-right: 6px; }
.rate-power { display: block; height: 100%; border-radius: 4px; background: #59c06c; }
/* 班级统计区：竖线分隔的内联列表 */
.class-statistics { display: flex; align-items: center; border-top: 1px dashed #e4edf7; margin-top: 8px; padding-top: 10px; }
.class-statistics-ul { display: flex; flex-wrap: wrap; gap: 4px 0; list-style: none; margin: 0; padding: 0 0 0 12px; color: #475467; font-size: 13px; }
.class-statistics-ul li { white-space: nowrap; }
.class-statistics-ul .line { color: #d5e3f4; margin: 0 10px; }
/* 功能TAB面板 */
.cd-tab-panel { margin-top: 10px; padding: 4px 18px 14px; }
.cd-tab-panel :deep(.el-tabs__header) { margin-bottom: 8px; }
/* ===== 排课TAB：工具条 + 图例 + FullCalendar 课表 ===== */
.sch-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 4px; margin-bottom: 10px; }
/* 图例 */
.sch-legend { display: flex; align-items: center; gap: 12px; list-style: none; margin: 0; padding: 0; flex-wrap: wrap; }
.sch-legend li { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #667c99; white-space: nowrap; }
.color-square { width: 12px; height: 12px; border-radius: 3px; border: 1px solid; display: inline-block; }
/* 图例角标（试听学员「试」字章） */
.legend-badge { display: inline-flex; align-items: center; justify-content: center;
  width: 15px; height: 15px; border-radius: 3px; background: #f79009; color: #fff;
  font-size: 10px; font-weight: 600; line-height: 1; }
/* FullCalendar 容器 */
.cal-wrap { border: 1px solid #e4edf7; border-radius: 10px; background: #fff; padding: 6px 10px; }
.cal-wrap :deep(.fc) { font-size: 12.5px; }
.cal-wrap :deep(.fc .fc-toolbar-title) { font-size: 15px; font-weight: 600; color: #344054; }
.cal-wrap :deep(.fc .fc-button) { background: #fff; border: 1px solid #dfe7f3; color: #475467;
  font-size: 12.5px; padding: 3px 10px; }
.cal-wrap :deep(.fc .fc-button:hover) { background: #f5f9ff; color: #155EEF; border-color: #b9d0f7; }
.cal-wrap :deep(.fc .fc-button-primary:not(:disabled).fc-button-active),
.cal-wrap :deep(.fc .fc-button-primary:not(:disabled):active) { background: #155EEF; color: #fff; border-color: #155EEF; }
.cal-wrap :deep(.fc-theme-standard td), .cal-wrap :deep(.fc-theme-standard th) { border-color: #edf2f9; }
.cal-wrap :deep(.fc .fc-event) { border-radius: 6px; cursor: pointer; font-size: 12px; }
/* 月视图"另外 N 个"链接 + 点击展开的当日课次弹层 */
.cal-wrap :deep(.fc .fc-daygrid-more-link) { font-size: 12px; color: #155EEF; font-weight: 600; }
.cal-wrap :deep(.fc .fc-more-popover) { border-radius: 10px; border-color: #e4edf7;
  box-shadow: 0 10px 28px rgba(21, 94, 239, 0.1); font-size: 12.5px; }
.cal-wrap :deep(.fc .fc-more-popover .fc-popover-header) {
  background: #f5f9ff; border-bottom: 1px solid #e4edf7; padding: 6px 10px; }
.cal-wrap :deep(.fc .fc-more-popover .fc-popover-title) { font-size: 13px; font-weight: 600; color: #344054; }
.cal-wrap :deep(.fc .fc-more-popover .fc-popover-close) { color: #8fa8c4; }
/* 课次块：标题行 + 标记角标行（已记上课/冲突/我的/试/其它课程，可并存） */
.cal-wrap :deep(.fc .fc-event .cz-ev) { color: #fff; line-height: 1.35; overflow: hidden; }
.cal-wrap :deep(.fc .fc-event .cz-ev-t) { font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cal-wrap :deep(.fc .fc-event .cz-ev-tags) { display: flex; gap: 3px; margin-top: 2px; flex-wrap: wrap; }
.cal-wrap :deep(.fc .fc-event .cz-ev-tag) {
  color: #fff; border-radius: 3px; padding: 0 4px; font-size: 10px; line-height: 14px; white-space: nowrap;
}
/* 日历操作提示 */
.cal-tip { margin-top: 6px; font-size: 12px; color: #8fa8c4; display: flex; align-items: center; }
/* 新建/编辑排课弹窗：排课方式提示 + 结束方式行 + 预计次数提示 */
.sched-mode-tip { margin-left: 10px; font-size: 12px; color: #8fa8c4; }
.sched-end-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.sched-end-unit { font-size: 13px; color: #667c99; }
.repeat-preview-tip { width: 100%; margin-top: 4px; font-size: 12px; color: #667c99; }
.repeat-preview-tip b { color: #f79009; padding: 0 2px; }
/* 课次操作弹窗（时间/教师/教室/状态/标记 + 调课取消） */
.sch-op-row { display: flex; gap: 10px; font-size: 13px; color: #344054; padding: 4px 0; align-items: flex-start; }
.sch-op-row > span:first-child { width: 62px; color: #8fa8c4; flex-shrink: 0; }
.sch-op-tags { display: inline-flex; gap: 4px; flex-wrap: wrap; }
.sch-op-alert { margin-top: 10px; }
/* 学生管理容量条：标签与「添加学生」按钮同行，按钮靠右 */
.stu-cap-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
/* 试听学员占名印章（橙底白「试」字，微倾 6°）+ 数量 */
.aud-seat { display: inline-flex; align-items: center; gap: 4px; color: #f79009; }
.aud-seat svg { transform: rotate(-6deg); }
.aud-seat b { font-size: 13px; }
/* 班课印章（同课程卡片 origin-seal 风格）：蓝底白「班」字，微倾 6° 呈盖章手感 */
.ban-seal { display: inline-flex; color: #155EEF; transform: rotate(-6deg); vertical-align: middle; }
.ban-seal svg { display: block; }
/* 添加学员弹窗：筛选条 + 计数行 + 已选统计 */
.add-toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 8px; }
.add-count { font-size: 12.5px; color: #667c99; margin-bottom: 6px; }
.add-count b { color: #155EEF; }
.add-sub { color: #98a2b3; font-size: 12px; margin-left: 8px; }
.add-sel-count { margin-top: 8px; padding: 6px 12px; background: #f5f9ff; border: 1px solid #dbe7fb;
  border-radius: 8px; font-size: 12.5px; color: #667c99; }
.add-sel-count .blue { color: #155EEF; }
/* 试听学员TAB：分区 + 办理试听弹窗 + 活动选择 */
.aud-sec { margin-bottom: 14px; }
.aud-sec-title {
  font-size: 13px; font-weight: 600; color: #344054;
  padding-left: 8px; border-left: 3px solid #155EEF; margin-bottom: 8px;
}
.aud-toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.aud-tip { font-size: 12.5px; color: #8fa8c4; }
.aud-acts { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
/* 活动信息区标题行：标题 + 清空按钮 */
.aud-sec-head { display: flex; align-items: center; justify-content: space-between; }
.aud-sec-head .aud-sec-title { margin-bottom: 0; }
.aud-sec-head + .aud-acts { margin-top: 8px; }
.aud-empty-tip {
  margin-top: 8px; font-size: 12.5px; color: #98a2b3;
  border: 1px dashed #dbe7f5; border-radius: 8px; background: #fafcff; padding: 10px 12px;
}
/* 回填项目信息明细：label + value 两列网格，名称可编辑 */
.aud-detail {
  list-style: none; margin: 8px 0 0; padding: 10px 12px;
  background: #f5f9ff; border: 1px solid #dbe7fb; border-radius: 8px;
  display: flex; flex-wrap: wrap; row-gap: 8px;
}
.aud-detail-item { display: flex; align-items: center; width: 50%; min-width: 200px; }
.aud-detail-item.aud-detail-name { width: 100%; }
.aud-detail-item label { flex-shrink: 0; width: 60px; font-size: 12.5px; color: #8fa8c4; }
.aud-detail-item span {
  font-size: 13px; color: #344054; max-width: 200px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
/* 选择课程弹窗 */
.dlg-toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.dlg-toolbar .fee-sub { font-size: 12.5px; color: #8fa8c4; margin-left: auto; }
.picked-clazz-bar {
  margin-top: 10px; padding: 6px 12px;
  background: #f5f9ff; border: 1px solid #dbe7fb; border-radius: 8px;
}
.picked-clazz-bar .fee-sub { font-size: 12.5px; color: #667c99; }
</style>
