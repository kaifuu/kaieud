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
            <li>出勤率：0.00%</li>
          </ul>
        </div>
      </div>

      <div class="panel cd-tab-panel">
        <el-tabs v-model="detailTab">
          <!-- 学员TAB：容量条 + 学员表（复用添加学生/调班弹窗） -->
          <el-tab-pane label="学员" name="students">
            <div class="stu-cap-bar">
              <el-tag effect="dark" type="success">已占用 {{ stuInfo.studying }} 名</el-tag>
              <el-tag effect="dark" type="warning">空余 {{ Math.max((stuInfo.capacity || 0) - (stuInfo.studying || 0), 0) }} 名</el-tag>
              <el-tag effect="dark">满班 {{ stuInfo.capacity }} 名</el-tag>
              <el-tag effect="light" type="info">最少开班 {{ detailClazz?.minCapacity ?? '—' }} 名</el-tag>
              <div style="flex: 1"></div>
              <el-button type="primary" size="small"
                         :disabled="(stuInfo.studying || 0) >= (stuInfo.capacity || 0)"
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

          <!-- 试听学员TAB -->
          <el-tab-pane label="试听学员" name="audition">
            <el-empty description="试听学员功能规划中" :image-size="80" />
          </el-tab-pane>

          <!-- 排课TAB：该班全部课次 -->
          <el-tab-pane label="排课" name="schedule" lazy>
            <el-table :data="detailSchedules" stripe size="small" max-height="420">
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
              <el-table-column label="状态" width="86" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="['warning', 'success', 'info'][row.status] || 'info'" effect="plain">
                    {{ ['待上课', '已完成', '已取消'][row.status] || '-' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ row.remark || (row.cancelReason ? '取消：' + row.cancelReason : '-') }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!detailSchedules.length" description="暂无排课记录" :image-size="70" />
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
                       :label="`${r.name}（${r.campus || '未设校区'}·容${r.capacity ?? 0}人）`" :value="r.id" />
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
  </div>
</template>

<script setup>
import {reactive, ref, computed, onMounted, watch} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {Search, MoreFilled, ArrowDown, View, User, EditPen, Delete} from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'
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

/** 进入班级功能页：加载学员、排课与课程定价 */
function openDetailPage(row, tab = 'students') {
  mode.value = 'detail'
  detailTab.value = tab
  detailClazz.value = row
  curClazz.value = row // 添加学生/调班弹窗复用
  loadStudents()
  loadDetailSchedules()
  const cid = row.courseId
  if (cid && !priceMap.value[cid]) {
    api.coursePrices(cid).then((ps) => (priceMap.value[cid] = ps || [])).catch(() => (priceMap.value[cid] = []))
  }
}
async function loadDetailSchedules() {
  try {
    detailSchedules.value = (await api.schedulePage(
      { page: 1, size: 100, clazzId: detailClazz.value.id })).records || []
  } catch (e) { detailSchedules.value = [] }
}
function backToList() {
  mode.value = 'list'
  detailClazz.value = null
  load()
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
  if (!clazzList.value.length) clazzList.value = (api.clazzList()) || []
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
/* 学生管理容量条：标签与「添加学生」按钮同行，按钮靠右 */
.stu-cap-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
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
/* 选择课程弹窗 */
.dlg-toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.dlg-toolbar .fee-sub { font-size: 12.5px; color: #8fa8c4; margin-left: auto; }
.picked-clazz-bar {
  margin-top: 10px; padding: 6px 12px;
  background: #f5f9ff; border: 1px solid #dbe7fb; border-radius: 8px;
}
.picked-clazz-bar .fee-sub { font-size: 12.5px; color: #667c99; }
</style>
