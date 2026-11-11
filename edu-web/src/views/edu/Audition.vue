<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">{{ pageTitle }}</span>
      <div class="actions">
        <template v-if="mode === 'list'">
          <el-button type="primary" @click="openEdit()">新增试听</el-button>
        </template>
        <el-button v-else @click="backToList">← 返回列表</el-button>
      </div>
    </div>

    <!-- ===== 试听列表页（整表滚动：表头表体一起动，避免横向错位） ===== -->
    <div v-if="mode === 'list'" class="panel table-panel audition-panel">
      <div class="toolbar">
        <el-select v-model="query.type" placeholder="试听类型" clearable style="width: 120px">
          <el-option label="插班试听" :value="1" /><el-option label="开班试听" :value="2" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="名称 / 课程 / 教师" clearable style="width: 190px"
                  @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="search">查询</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="audition" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe @sort-change="onSort"
                @row-dblclick="openDetail">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column v-if="cols.name" prop="name" label="名称" min-width="150" sortable="custom" show-overflow-tooltip />
        <el-table-column v-if="cols.type" label="试听类型" width="92" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 1 ? 'primary' : 'warning'" effect="plain">
              {{ row.type === 1 ? '插班试听' : '开班试听' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.auditionDate" prop="auditionDate" label="日期" width="105" sortable="custom" />
        <el-table-column v-if="cols.campus" prop="campus" label="校区" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.campus || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.courseName" prop="courseName" label="课程分类" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.courseName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.teacherName" prop="teacherName" label="教师" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.teacherName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.classroomName" prop="classroomName" label="教室" width="95" show-overflow-tooltip>
          <template #default="{ row }">{{ row.classroomName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.timePeriod" prop="timePeriod" label="时段" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.timePeriod || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.assistantName" prop="assistantName" label="助教" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.assistantName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.plannedCount" prop="plannedCount" label="预招人数" width="86" align="center" sortable="custom">
          <template #default="{ row }">{{ row.plannedCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.registeredCount" label="已报名人数" width="92" align="center">
          <template #default="{ row }">
            <b :style="{ color: regColor(row) }" :title="regTitle(row)">{{ row.registeredCount ?? 0 }}</b>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.freeCount" label="空余人数" width="80" align="center">
          <template #default="{ row }">{{ row.freeCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.overCount" label="超员人数" width="80" align="center">
          <template #default="{ row }">
            <b v-if="row.overCount > 0 || (row.roomMax != null && row.registeredCount > row.roomMax)"
               style="color: #f04438; font-size: 13px">+{{ Math.max(row.overCount || 0,
              row.roomMax != null ? row.registeredCount - row.roomMax : 0) }}</b>
            <span v-else style="color: #c0c8d4">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.roomMin" label="教室最低人数" width="98" align="center">
          <template #default="{ row }">{{ row.roomMin ?? '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.roomMax" label="教室最多容纳" width="98" align="center">
          <template #default="{ row }">{{ row.roomMax ?? '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.operator" prop="operator" label="经办人" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operator || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该试听记录?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <!-- ===== 试听功能页：基本信息 + 试听学员TAB（双击列表行进入，样式参考班级功能页） ===== -->
    <template v-if="mode === 'detail'">
      <div class="panel ad-info-panel">
        <div class="class-info">
          <div class="class-info-item"><label class="class-item-label">名称</label>
            <span class="class-item-text">{{ detailRow?.name || '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">试听类型</label>
            <span class="class-item-text">{{ detailRow?.type === 1 ? '插班试听' : '开班试听' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">日期</label>
            <span class="class-item-text">{{ detailRow?.auditionDate || '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">校区</label>
            <span class="class-item-text">{{ detailRow?.campus || '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">课程分类</label>
            <span class="class-item-text">{{ detailRow?.courseName || '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">教师</label>
            <span class="class-item-text">{{ detailRow?.teacherName || '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">教室</label>
            <span class="class-item-text">{{ detailRow?.classroomName || '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">教室最低容纳</label>
            <span class="class-item-text">{{ detailRow?.roomMin ?? '—' }}{{ detailRow?.roomMin != null ? ' 人' : '' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">教室最多容纳</label>
            <span class="class-item-text">{{ detailRow?.roomMax ?? '—' }}{{ detailRow?.roomMax != null ? ' 人' : '' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">时段</label>
            <span class="class-item-text">{{ detailRow?.timePeriod || '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">助教</label>
            <span class="class-item-text">{{ detailRow?.assistantName || '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">预招人数</label>
            <span class="class-item-text">{{ detailRow?.plannedCount ?? '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">备注</label>
            <span class="class-item-text" :title="detailRow?.remark">{{ detailRow?.remark || '—' }}</span></div>
          <div class="class-info-item"><label class="class-item-label">经办人</label>
            <span class="class-item-text">{{ detailRow?.operator || '—' }}</span></div>
        </div>
        <div class="class-statistics">
          <label class="class-item-label">登记统计</label>
          <ul class="class-statistics-ul">
            <li> 已登记学员：<b :class="{ 'stat-over': overPlanned > 0 }">{{ dsPager.total }}</b><span class="line">|</span></li>
            <li> 剩余名额：{{ Math.max(Number(detailRow?.plannedCount || 0) - dsPager.total, 0) }}<span class="line">|</span></li>
            <li v-if="overPlanned > 0">
              <span class="stat-over">⚠ 超预招人数：<b>+{{ overPlanned }}</b> 人（预招 {{ detailRow?.plannedCount ?? '—' }}）</span><span class="line">|</span>
            </li>
            <li v-if="overRoom > 0">
              <span class="stat-over">⚠ 超教室容纳：<b>+{{ overRoom }}</b> 人（教室最多容纳 {{ detailRow?.roomMax }} 人）</span>
            </li>
            <li v-if="overPlanned === 0 && overRoom === 0"> 暂无超员</li>
          </ul>
          <div v-if="overPlanned > 0 || overRoom > 0" class="over-banner">
            当前已登记 <b>{{ dsPager.total }}</b> 人，已超出限制：<template v-if="overPlanned > 0">预招人数超 <b>{{ overPlanned }}</b> 人</template><template v-if="overPlanned > 0 && overRoom > 0">；</template><template v-if="overRoom > 0">教室容纳超 <b>{{ overRoom }}</b> 人</template>。请控制登记人数，或调整预招人数/更换更大教室。
          </div>
        </div>
      </div>

      <div class="panel ad-tab-panel">
        <el-tabs v-model="detailTab">
          <el-tab-pane label="试听学员" name="students" lazy>
            <div class="stu-cap-bar">
              <el-button type="primary" size="small" @click="openAddAudStu">添加学员</el-button>
              <el-input v-model="dsQuery.keyword" placeholder="学员姓名 / 手机号" clearable size="small"
                        style="width: 160px" @keyup.enter="loadDetailStudents(true)" />
              <el-date-picker v-model="dsQuery.range" type="daterange" value-format="YYYY-MM-DD" size="small"
                              start-placeholder="登记起" end-placeholder="登记止" style="width: 215px" />
              <el-button type="primary" size="small" @click="loadDetailStudents(true)">查询</el-button>
              <div style="flex: 1"></div>
              <el-tag effect="light" type="info">已登记 {{ dsPager.total }} 名 ｜ 预招 {{ detailRow?.plannedCount ?? '—' }} 名</el-tag>
            </div>
            <el-table :data="detailStudents" v-loading="detailStuLoading" stripe size="small"
                      @sort-change="onDsSort">
              <el-table-column type="index" label="序号" width="55"
                               :index="(i) => (dsPager.page - 1) * dsPager.size + i + 1" />
              <el-table-column prop="studentName" label="学员" width="100" sortable="custom" show-overflow-tooltip />
              <el-table-column prop="studentPhone" label="手机号" width="125" show-overflow-tooltip>
                <template #default="{ row }">{{ row.studentPhone || '-' }}</template>
              </el-table-column>
              <el-table-column prop="teacherName" label="教师" width="84" show-overflow-tooltip>
                <template #default="{ row }">{{ row.teacherName || '-' }}</template>
              </el-table-column>
              <el-table-column prop="classroomName" label="试听教室" width="92" show-overflow-tooltip>
                <template #default="{ row }">{{ row.classroomName || '-' }}</template>
              </el-table-column>
              <el-table-column prop="timePeriod" label="时间段" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.timePeriod || '-' }}</template>
              </el-table-column>
              <el-table-column prop="auditionDate" label="办试听时间" width="104" sortable="custom" />
              <el-table-column prop="operator" label="试听操作人" width="96" show-overflow-tooltip>
                <template #default="{ row }">{{ row.operator || '-' }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="登记时间" width="150" sortable="custom" show-overflow-tooltip>
                <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="70" fixed="right">
                <template #default="{ row }">
                  <el-popconfirm title="移除该试听学员?" @confirm="removeAudStu(row.id)">
                    <template #reference><el-button link type="danger">移除</el-button></template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
            <div class="pager-row">
              <el-pagination background layout="total, prev, pager, next" :total="dsPager.total"
                             v-model:current-page="dsPager.page" v-model:page-size="dsPager.size"
                             :page-size="20" @current-change="loadDetailStudents" />
            </div>
            <el-empty v-if="!detailStudents.length && !detailStuLoading"
                      description="暂无登记学员：点「添加学员」或在各班级功能页-试听学员TAB办理" :image-size="70" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>

    <!-- 添加学员（试听详情页）：搜索 + 多选表格，快速登记试听活动 -->
    <el-dialog v-model="addAudStuVisible" title="添加学员（试听活动登记）" width="560px" append-to-body>
      <div class="aud-search-bar">
        <el-input v-model="addAudKw" placeholder="输入学员姓名 / 手机号搜索，双击输入框查看全部" clearable
                  size="small" :prefix-icon="Search" @dblclick="loadAllAudStu" />
        <span class="aud-search-meta">登记班级：{{ relateClazzName }}</span>
      </div>
      <el-table :data="addAudStuCands" v-loading="addAudStuSearching" size="small" stripe max-height="300"
                row-key="studentId" @selection-change="addAudStuSel = $event">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="name" label="学员姓名" min-width="110" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="年级" width="80">
          <template #default="{ row }">{{ row.grade || '-' }}</template>
        </el-table-column>
      </el-table>
      <div class="add-sel-count">已选择学员 <b class="blue">{{ addAudStuSel.length }}</b> 名，将登记试听「{{ detailRow?.name }}」</div>
      <template #footer>
        <el-button @click="addAudStuVisible = false">取 消</el-button>
        <el-button type="primary" :loading="addAudStuSaving" :disabled="!addAudStuSel.length"
                   @click="confirmAddAudStu">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑试听 -->
    <el-drawer v-model="visible" :title="form.id ? '编辑试听' : '新增试听'" size="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="试听类型">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">插班试听</el-radio>
            <el-radio :value="2">开班试听</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.type === 1" label="关联日程">
          <div class="relate-box">
            <el-input :model-value="relateText" readonly placeholder="点击「选择班课」关联已成班级"
                      class="relate-input" />
            <el-button type="primary" plain @click="openClazzDlg">选择班课</el-button>
          </div>
          <div class="relate-tip">选择后自动回填名称/日期/校区/教师/教室/时段/课程，可再调整</div>
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" placeholder="试听名称" /></el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.auditionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="校区">
          <el-select v-model="form.campus" clearable filterable style="width: 100%">
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程分类">
          <el-select v-model="form.courseName" filterable clearable
                     :disabled="form.type === 1 && !!form.relateClazzId"
                     placeholder="选择课程分类" style="width: 100%">
            <el-option v-for="d in dictStore.options('course_category')" :key="d.value"
                       :label="d.label" :value="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="教师">
          <el-select v-model="form.teacherName" clearable filterable style="width: 100%" placeholder="选择教师">
            <el-option v-for="t in teacherOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="教室">
          <el-select v-model="form.classroomName" clearable filterable style="width: 100%">
            <el-option v-for="r in classroomList" :key="r.id"
                       :label="`${r.name}（${r.campus || '未设校区'}·容${r.capacity ?? 0}人）`" :value="r.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="时段">
          <el-time-picker v-model="timeRange" is-range range-separator="至"
                          start-placeholder="开始" end-placeholder="结束"
                          format="HH:mm" value-format="HH:mm" style="width: 230px" />
        </el-form-item>
        <el-form-item label="助教">
          <el-select v-model="form.assistantName" clearable filterable style="width: 100%">
            <el-option v-for="t in teacherOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="预招人数">
          <el-input-number v-model="form.plannedCount" :min="0" :max="999" style="width: 140px" />
          <span class="unit">人</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 选择班课弹窗：已成班级（开课中/招生中，课程为班课），点击行回填 -->
    <el-dialog v-model="clazzDlgVisible" title="选择班课（关联日程）" width="760px" append-to-body>
      <div class="dlg-toolbar">
        <el-input v-model="clazzKw" placeholder="班级名称 / 课程名称搜索" clearable
                  style="width: 220px" :prefix-icon="Search" />
        <span class="dlg-tip">点击行选择并回填（仅班课）</span>
      </div>
      <el-table ref="clazzTableRef" :data="clazzFiltered" v-loading="clazzLoading" height="340" size="small" stripe
                highlight-current-row @current-change="(r) => (clazzPickedRow = r)">
        <el-table-column prop="name" label="班级名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="课程分类" min-width="96" show-overflow-tooltip>
          <template #default="{ row }">{{ categoryOf(courses.find((c) => c.id === row.courseId)) || '-' }}</template>
        </el-table-column>
        <el-table-column label="班主任" width="86" show-overflow-tooltip>
          <template #default="{ row }">{{ teacherName(row.classTeacherId) || '-' }}</template>
        </el-table-column>
        <el-table-column label="教室" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ roomName(row.classroomId) }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="开班日期" width="100" />
        <el-table-column label="班容(占用/满班)" width="120" align="center">
          <template #default="{ row }">{{ stuCount[row.id] ?? 0 }}/{{ row.capacity }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'warning' : 'success'" effect="plain">
              {{ ['', '招生中', '开课中'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="选择" width="64" align="center">
          <template #default="{ row }">
            <el-tag v-if="clazzPickedRow && clazzPickedRow.id === row.id" size="small" type="primary" effect="plain">已选</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!clazzFiltered.length && !clazzLoading" description="暂无可选班课（需先在班级管理创建班课班级）" :image-size="70" />
      <template #footer>
        <el-button @click="clazzDlgVisible = false">取 消</el-button>
        <el-button type="primary" :disabled="!clazzPickedRow" @click="confirmClazzPick">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, nextTick, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { useDictStore } from '../../store/dict'

const dictStore = useDictStore()
const query = reactive({ type: null, keyword: '' })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.auditionPage(q), query)

/* ===== 试听功能页（列表双击进入）：基本信息 + 试听学员TAB（分页/筛选/排序） ===== */
const mode = ref('list')
const detailTab = ref('students')
const detailRow = ref(null)
const detailStudents = ref([])
const detailStuLoading = ref(false)
const dsQuery = reactive({ keyword: '', range: null, sortBy: null, direction: null })
const dsPager = reactive({ page: 1, size: 20, total: 0 })
const pageTitle = computed(() => (mode.value === 'detail' ? (detailRow.value?.name || '试听详情') : '试听管理'))
async function openDetail(row) {
  mode.value = 'detail'
  detailTab.value = 'students'
  detailRow.value = row
  if (!Object.keys(clazzNameMap.value).length) {
    try {
      (await api.clazzList()).forEach((c) => (clazzNameMap.value[c.id] = c.name))
    } catch (e) { /* 班级名缺失显示 - */ }
  }
  dsQuery.keyword = ''
  dsQuery.range = null
  dsQuery.sortBy = null
  dsQuery.direction = null
  loadDetailStudents(true)
}
async function loadDetailStudents(resetPage) {
  if (resetPage === true) dsPager.page = 1
  if (!detailRow.value) return
  detailStuLoading.value = true
  try {
    const d = await api.auditionStudents(detailRow.value.id, {
      page: dsPager.page, size: dsPager.size,
      keyword: dsQuery.keyword || undefined,
      begin: dsQuery.range?.[0], end: dsQuery.range?.[1],
      sortBy: dsQuery.sortBy || undefined, direction: dsQuery.direction || undefined
    })
    detailStudents.value = d.records || []
    dsPager.total = d.total || 0
  } catch (e) { detailStudents.value = []; dsPager.total = 0 }
  finally { detailStuLoading.value = false }
}
/** 试听学员列表排序（后端：学员/办试听时间/登记时间） */
function onDsSort({ prop, order }) {
  dsQuery.sortBy = order ? prop : null
  dsQuery.direction = order === 'ascending' ? 'asc' : 'desc'
  loadDetailStudents(true)
}
function backToList() {
  mode.value = 'list'
  detailRow.value = null
  load()
}

/** 功能页时点击左侧「试听管理」菜单 → 视同返回列表 */
const route = useRoute()
function onMenuSelfClick(e) {
  if (e.detail !== route.path || mode.value === 'list') return
  backToList()
}
onMounted(() => window.addEventListener('menu-self-click', onMenuSelfClick))
onBeforeUnmount(() => window.removeEventListener('menu-self-click', onMenuSelfClick))
const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')
/** 超员指标：超预招 / 超教室容纳（登记数与预招、教室最多容纳比较） */
const overPlanned = computed(() =>
  Math.max(dsPager.total - Number(detailRow.value?.plannedCount || 0), 0))
const overRoom = computed(() => detailRow.value?.roomMax != null
  ? Math.max(dsPager.total - Number(detailRow.value.roomMax || 0), 0) : 0)

/* ===== 试听详情：添加学员（搜索+多选表格，批量登记到活动关联班级）/ 移除 ===== */
const addAudStuVisible = ref(false)
const addAudKw = ref('')
const addAudStuSearching = ref(false)
const addAudStuSaving = ref(false)
const addAudStuCands = ref([])
const addAudStuSel = ref([])
const allAudStudents = ref([])
/** 班级名映射（登记班级回显用） */
const clazzNameMap = ref({})
const relateClazzName = computed(() =>
  clazzNameMap.value[detailRow.value?.relateClazzId] || '—')

let addAudTimer = null
watch(addAudKw, () => {
  clearTimeout(addAudTimer)
  addAudTimer = setTimeout(() => searchAudStu(addAudKw.value), 300)
})
/** 混合搜索：姓名/手机号（仅真实学员） */
async function searchAudStu(kw) {
  const k = (kw || '').trim()
  if (!k) { addAudStuCands.value = []; return }
  addAudStuSearching.value = true
  try {
    if (!allAudStudents.value.length) allAudStudents.value = (await api.studentList()) || []
    addAudStuCands.value = allAudStudents.value
      .filter((s) => (s.name || '').toLowerCase().includes(k.toLowerCase()) || (s.phone || '').includes(k))
      .map((s) => ({ studentId: s.id, name: s.name, phone: s.phone, grade: s.grade }))
  } finally { addAudStuSearching.value = false }
}
/** 双击搜索框：全量列出 */
async function loadAllAudStu() {
  if (addAudStuSearching.value) return
  addAudStuSearching.value = true
  try {
    if (!allAudStudents.value.length) allAudStudents.value = (await api.studentList()) || []
    addAudStuCands.value = allAudStudents.value
      .map((s) => ({ studentId: s.id, name: s.name, phone: s.phone, grade: s.grade }))
  } finally { addAudStuSearching.value = false }
}
function openAddAudStu() {
  addAudKw.value = ''
  addAudStuCands.value = []
  addAudStuSel.value = []
  addAudStuVisible.value = true
}
/** 确定登记：有关联班级走班级批量（含容量校验）；无关联（开班试听）走活动直接登记 */
async function confirmAddAudStu() {
  if (!addAudStuSel.value.length) return ElMessage.warning('请先勾选要登记的学员')
  const d = detailRow.value
  addAudStuSaving.value = true
  try {
    let msg
    if (d.relateClazzId) {
      msg = await api.clazzAddAuditionsBatch(d.relateClazzId, {
        items: addAudStuSel.value.map((s) => ({
          studentId: s.studentId, studentName: s.name,
          source: 1, auditionId: d.id, auditionName: d.name,
          courseName: d.courseName, teacherName: d.teacherName,
          classroomName: d.classroomName, timePeriod: d.timePeriod, campus: d.campus,
          auditionDate: d.auditionDate, assistantName: d.assistantName
        }))
      })
    } else {
      msg = await api.auditionAddStudents(d.id,
        { studentIds: addAudStuSel.value.map((s) => s.studentId) })
    }
    ElMessage.success(msg || '登记成功')
    addAudStuVisible.value = false
    openDetail(d)
  } finally { addAudStuSaving.value = false }
}
async function removeAudStu(id) {
  await api.clazzDelAudition(id)
  ElMessage.success('已移除')
  openDetail(detailRow.value)
}

const allColumns = [
  { prop: 'name', label: '名称' }, { prop: 'type', label: '试听类型' },
  { prop: 'auditionDate', label: '日期' }, { prop: 'campus', label: '校区' },
  { prop: 'courseName', label: '课程分类' }, { prop: 'teacherName', label: '教师' },
  { prop: 'classroomName', label: '教室' }, { prop: 'timePeriod', label: '时段' },
  { prop: 'assistantName', label: '助教' }, { prop: 'plannedCount', label: '预招人数' },
  { prop: 'registeredCount', label: '已报名人数' },
  { prop: 'freeCount', label: '空余人数' },
  { prop: 'overCount', label: '超员人数' }, { prop: 'roomMin', label: '教室最低人数' },
  { prop: 'roomMax', label: '教室最多容纳' }, { prop: 'operator', label: '经办人' }
]
const cols = reactive({ name: true, type: true, auditionDate: true, campus: true, courseName: true,
  teacherName: true, classroomName: true, timePeriod: true, assistantName: false,
  plannedCount: true, registeredCount: true,
  freeCount: true, overCount: true, roomMin: false, roomMax: false, operator: false })
const onCols = (c) => Object.assign(cols, c)

/** 已报名人数配色：超员红 / 满员橙 / 未满绿 / 未设预招灰（悬停提示状态说明） */
const regColor = (row) => {
  if (row.plannedCount == null) return '#98a2b3'
  if (row.overCount > 0) return '#f04438'
  if (row.freeCount === 0) return '#f79009'
  return '#12b76a'
}
const regTitle = (row) => {
  if (row.plannedCount == null) return '未设预招人数'
  if (row.overCount > 0) return `超员 ${row.overCount} 人（预招 ${row.plannedCount}）`
  if (row.freeCount === 0) return '满员'
  return `未满：剩余 ${row.freeCount} 人（预招 ${row.plannedCount}）`
}

/* ===== 基础数据 ===== */
const courses = ref([])
const teachers = ref([])
const classroomList = ref([])
const staffCandidates = ref([])
const clazzes = ref([])
const stuCount = ref({})
const campusOptions = computed(() =>
  dictStore.options('campus').map((d) => d.label).filter((l) => l !== '全部校区'))
/** 教师/助教候选（员工账号名） */
const teacherOptions = computed(() =>
  staffCandidates.value.map((c) => c.name).filter(Boolean))
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'
/** 课程分类（字典 course_category） */
const categoryOf = (course) => course?.category
  ? dictStore.label('course_category', course.category) : ''
const teacherName = (id) => {
  const cand = staffCandidates.value.find((c) => c.teacherId === id)
  return cand?.name || teachers.value.find((t) => t.id === id)?.name || ''
}
const roomName = (id) => classroomList.value.find((r) => r.id === id)?.name || '-'

async function loadBase() {
  courses.value = (await api.courseList()) || []
  classroomList.value = (await api.classroomList()) || []
  staffCandidates.value = (await api.clazzTeacherCandidates()) || []
  teachers.value = (await api.teacherList()) || []
  // 已成班级（含招生中/开课中）+ 班课过滤 + 占用统计
  clazzes.value = (await api.clazzList()) || []
  const counts = {}
  const all = await api.enrollPage({ page: 1, size: 1000, status: 1 })
  for (const e of (all.records || [])) if (e.clazzId) counts[e.clazzId] = (counts[e.clazzId] || 0) + 1
  stuCount.value = counts
}

/* ===== 新增/编辑 ===== */
const visible = ref(false)
const saving = ref(false)
const form = ref({})
/** 时段时间范围（time-picker is-range，HH:mm 二元组，存库拼为 HH:mm-HH:mm） */
const timeRange = ref(null)
const parsePeriod = (s) => {
  const m = /^(\d{2}:\d{2})-(\d{2}:\d{2})$/.exec(s || '')
  return m ? [m[1], m[2]] : null
}
const relateText = computed(() => {
  if (form.value.type !== 1 || !form.value.relateClazzId) return ''
  const c = clazzes.value.find((x) => x.id === form.value.relateClazzId)
  return c ? `${c.name}（${courseName(c.courseId)}）` : ''
})
function openEdit(row) {
  suppressTypeReset = true
  form.value = row
    ? { ...row }
    : { type: 2, name: '', auditionDate: null, campus: null, courseName: '', teacherName: null,
        classroomName: null, timePeriod: '', assistantName: null, plannedCount: null,
        relateClazzId: null, remark: '' }
  timeRange.value = parsePeriod(form.value.timePeriod)
  visible.value = true
  nextTick(() => { suppressTypeReset = false })
}
/** 切换试听类型（插班/开班）：表单回到初始状态（打开抽屉赋初值时不触发） */
let suppressTypeReset = false
watch(() => form.value.type, (nv, ov) => {
  if (suppressTypeReset || ov == null || nv === ov) return
  Object.assign(form.value, {
    name: '', auditionDate: null, campus: null, courseName: '',
    teacherName: null, classroomName: null, assistantName: null,
    plannedCount: null, relateClazzId: null, remark: '', timePeriod: ''
  })
  timeRange.value = null
})
async function save() {
  if (!form.value.name?.trim()) return ElMessage.warning('名称不能为空')
  form.value.timePeriod = timeRange.value ? timeRange.value.join('-') : ''
  saving.value = true
  try {
    if (form.value.id) await api.auditionUpdate(form.value)
    else await api.auditionSave(form.value)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally { saving.value = false }
}
async function remove(id) {
  await api.auditionDel(id)
  ElMessage.success('已删除')
  load()
}

/* ===== 插班试听：选择班课弹窗（行选中后点确定回填，重开可改选） ===== */
const clazzDlgVisible = ref(false)
const clazzTableRef = ref(null)
const clazzPickedRow = ref(null)
const clazzKw = ref('')
const clazzLoading = ref(false)
/** 班课 = 课程授课模式为「班课」（字典） */
const bankeVal = computed(() => {
  const hit = dictStore.options('class_type').find((d) => d.label === '班课')
  return hit ? Number(hit.value) : null
})
/** 可关联班级：非已结课 且 课程为班课 */
const clazzFiltered = computed(() => {
  const k = (clazzKw.value || '').trim().toLowerCase()
  return clazzes.value.filter((c) => {
    const course = courses.value.find((x) => x.id === c.courseId)
    if (!course || course.status !== 1) return false
    if (bankeVal.value != null && course.teachMode !== bankeVal.value) return false
    if (k && !(c.name || '').toLowerCase().includes(k)
      && !(course.name || '').toLowerCase().includes(k)) return false
    return true
  })
})
function openClazzDlg() {
  if (!clazzes.value.length) loadBase()
  clazzKw.value = ''
  // 已有关联班级时预选该行，否则清空选中态（修复：重开点同一行不触发 current-change）
  clazzPickedRow.value = clazzes.value.find((c) => c.id === form.value.relateClazzId) || null
  nextTick(() => clazzTableRef.value?.setCurrentRow(clazzPickedRow.value || null))
  clazzDlgVisible.value = true
}
/** 确认选择：回填全部字段（课程按关联班级锁定，其余可调） */
async function confirmClazzPick() {
  if (!clazzPickedRow.value) return ElMessage.warning('请先点击选择一个班级')
  const row = clazzPickedRow.value
  const course = courses.value.find((x) => x.id === row.courseId)
  form.value.relateClazzId = row.id
  form.value.name = `${row.name}（插班试听）`
  form.value.auditionDate = row.startDate || form.value.auditionDate
  const room = classroomList.value.find((r) => r.id === row.classroomId)
  form.value.campus = room?.campus || (course?.campuses || [])[0] || form.value.campus
  form.value.courseName = categoryOf(course) || course?.name || form.value.courseName
  form.value.teacherName = teacherName(row.classTeacherId) || form.value.teacherName
  form.value.classroomName = room?.name || form.value.classroomName
  // 时段：取该班最近一次未取消课次的开始-结束时间（时间范围选择器回显）
  try {
    const rs = (await api.schedulePage({ page: 1, size: 50, clazzId: row.id })).records || []
    const s = rs.find((x) => x.status !== 2)
    if (s) timeRange.value = [String(s.startTime).slice(11, 16), String(s.endTime || '').slice(11, 16)]
  } catch (e) { /* 无排课时保持原值 */ }
  form.value.plannedCount = form.value.plannedCount ?? Math.max((row.capacity || 0) - (stuCount.value[row.id] ?? 0), 0)
  clazzDlgVisible.value = false
  ElMessage.success(`已关联班级「${row.name}」，字段已回填`)
}

onMounted(async () => {
  load()
  dictStore.load(true)
  await loadBase()
})
</script>

<style scoped>
/* 列表整表滚动：面板不锁高，表格自然高度，横向滚动时表头表体一起动 */
.audition-panel { height: auto; overflow: visible; }
.audition-panel :deep(.el-table) { flex: none; width: 100%; }
/* ===== 试听功能页：基本信息 + 统计 + TAB（样式同班级功能页） ===== */
.ad-info-panel { padding: 14px 18px; }
.class-info { display: flex; flex-wrap: wrap; row-gap: 4px; }
.class-info-item { display: flex; align-items: center; width: 25%; min-width: 216px; padding: 5px 0; }
.class-item-label { flex-shrink: 0; width: 76px; color: #8fa8c4; font-size: 12.5px; }
.class-item-text { color: #344054; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.class-statistics { display: flex; flex-direction: column; align-items: flex-start; border-top: 1px dashed #e4edf7; margin-top: 8px; padding-top: 10px; }
.class-statistics > .class-item-label { margin-bottom: 6px; color: #344054; font-weight: 600; }
.class-statistics-ul { display: flex; flex-wrap: wrap; gap: 4px 0; list-style: none; margin: 0; padding: 0; color: #475467; font-size: 13px; }
.class-statistics-ul li { white-space: nowrap; }
.class-statistics-ul .line { color: #d5e3f4; margin: 0 10px; }
/* 超员醒目提示 */
.stat-over { color: #f04438; font-weight: 600; }
.over-banner {
  margin-top: 8px; padding: 8px 12px; width: 100%; box-sizing: border-box;
  background: #fef3f2; border: 1px solid #fecdca; border-radius: 8px;
  color: #b42318; font-size: 12.5px; line-height: 1.5;
}
.over-banner b { color: #d92d20; font-size: 14px; }
.ad-tab-panel { margin-top: 10px; padding: 4px 18px 14px; }
.ad-tab-panel :deep(.el-tabs__header) { margin-bottom: 8px; }
/* 试听学员TAB工具条 */
.stu-cap-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.aud-tip { font-size: 12.5px; color: #8fa8c4; }
/* 添加学员弹窗：搜索条 + 已选统计 */
.aud-search-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.aud-search-bar .el-input { flex: 1; }
.aud-search-meta { font-size: 12.5px; color: #8fa8c4; white-space: nowrap; }
.add-sel-count { margin-top: 8px; padding: 6px 12px; background: #f5f9ff; border: 1px solid #dbe7fb;
  border-radius: 8px; font-size: 12.5px; color: #667c99; }
.add-sel-count .blue { color: #155EEF; }
/* 关联日程：只读展示 + 选择按钮 */
.relate-box { display: flex; gap: 8px; width: 100%; }
.relate-input { flex: 1; }
.relate-tip { font-size: 12px; color: #98a2b3; line-height: 1.4; margin-top: 2px; }
.unit { font-size: 12.5px; color: #8fa8c4; margin-left: 6px; }
/* 选择班课弹窗 */
.dlg-toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.dlg-tip { font-size: 12.5px; color: #8fa8c4; }
</style>
