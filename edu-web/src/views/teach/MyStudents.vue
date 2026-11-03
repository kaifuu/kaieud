<template>
  <el-card>
    <template #header>我所带的学生（班级视角：点击班级查看该班全部学员）</template>

    <!-- 班级切换条：一对一 + 所带各班级；点击切换下方学员列表 -->
    <div class="clazz-bar">
      <div v-for="g in groups" :key="g.key" class="clazz-chip" :class="{ active: activeKey === g.key }"
           @click="activeKey = g.key">
        <span class="chip-name">{{ g.name }}</span>
        <span class="chip-count">{{ g.items.length }}人</span>
      </div>
      <el-empty v-if="!groups.length" description="暂无学生" :image-size="50" />
    </div>

    <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="activeItems" stripe v-loading="loading">
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column label="学生" width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ row.student ? row.student.name : '-' }}</template>
      </el-table-column>
      <el-table-column label="就读学校" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.student ? row.student.school : '-' }}</template>
      </el-table-column>
      <el-table-column label="班级" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.clazz ? row.clazz.name : '一对一' }}</template>
      </el-table-column>
      <el-table-column label="课程" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.course ? row.course.name : '-' }}</template>
      </el-table-column>
      <el-table-column label="课时(已用/总)" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.enroll.usedHours }} / {{ row.enroll.totalHours }}</template>
      </el-table-column>
      <el-table-column label="折扣" width="90" show-overflow-tooltip>
        <template #default="{ row }">{{ row.enroll.discount }}%</template>
      </el-table-column>
      <el-table-column label="合同金额" width="110" show-overflow-tooltip>
        <template #default="{ row }">¥{{ row.enroll.totalAmount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" show-overflow-tooltip>
        <template #default="{ row }">{{ ['', '在读', '已结课', '已退费'][row.enroll.status] }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openInfo(row)">基本信息</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 学生基本信息抽屉：手机号已在接口层脱敏（前3后4） -->
    <el-drawer v-model="infoVisible" :title="infoData.name ? `${infoData.name} · 基本信息` : '学生基本信息'" size="480px">
      <div v-loading="infoLoading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="姓名">{{ infoData.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ ['-', '男', '女'][infoData.gender] || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出生日期">{{ infoData.birthDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="年级">{{ infoData.grade || '-' }}</el-descriptions-item>
          <el-descriptions-item label="就读学校" :span="2">{{ infoData.school || '-' }}</el-descriptions-item>
          <el-descriptions-item label="家长姓名">{{ infoData.parentName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="家长电话">
            <span class="phone">{{ infoData.parentPhone || '-' }}</span>
            <el-tooltip content="为保护学员隐私，联系方式已脱敏" placement="top">
              <el-icon class="tip-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </el-descriptions-item>
          <el-descriptions-item label="备用电话">
            <span class="phone">{{ infoData.backupPhone || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="咨询顾问">{{ infoData.advisor || '-' }}</el-descriptions-item>
          <el-descriptions-item label="学员标签" :span="2">
            <template v-if="infoData.tags">
              <el-tag v-for="t in infoData.tags.split(',')" :key="t" size="small" style="margin-right: 6px">{{ t }}</el-tag>
            </template>
            <template v-else>-</template>
          </el-descriptions-item>
          <el-descriptions-item label="家庭住址" :span="2">{{ infoData.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="档案状态">
            <el-tag size="small" :type="infoData.status === 1 ? 'success' : 'info'">
              {{ { 1: '在读', 2: '停课', 3: '结课', 4: '退费' }[infoData.status] || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="建档时间">{{ infoData.createTime || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="enroll-title">本教师名下报名</div>
        <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="infoData.enrolls || []" size="small" border>
          <el-table-column label="课程" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.courseName }}</template>
          </el-table-column>
          <el-table-column label="班级" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.clazzName }}</template>
          </el-table-column>
          <el-table-column label="课时(已用/总)" width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.usedHours }} / {{ row.totalHours }}</template>
          </el-table-column>
          <el-table-column label="状态" width="70" show-overflow-tooltip>
            <template #default="{ row }">{{ ['', '在读', '已结课', '已退费'][row.status] }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import api from '../../api'

const list = ref([])
const loading = ref(false)
const activeKey = ref('')

// 学生基本信息抽屉
const infoVisible = ref(false)
const infoLoading = ref(false)
const infoData = ref({})

async function openInfo(row) {
  if (!row.student) return
  infoVisible.value = true
  infoLoading.value = true
  infoData.value = {}
  try {
    infoData.value = (await api.teachStudentInfo(row.student.id)) || {}
  } finally { infoLoading.value = false }
}

onMounted(async () => {
  loading.value = true
  try {
    list.value = (await api.teachStudents()) || []
    if (groups.value.length) activeKey.value = groups.value[0].key
  } finally { loading.value = false }
})

/**
 * 班级视角分组：班课按班级分组（clazzId），无班级的一对一报名归入「一对一」组；
 * 班级组按人数倒序，一对一置底。key 唯一标识供切换。
 */
const groups = computed(() => {
  const map = new Map()
  const one2one = []
  for (const item of list.value) {
    const cid = item.enroll?.clazzId
    if (cid) {
      if (!map.has(cid)) map.set(cid, { key: 'c' + cid, name: item.clazz?.name || `班级#${cid}`, items: [] })
      map.get(cid).items.push(item)
    } else {
      one2one.push(item)
    }
  }
  const classGroups = [...map.values()].sort((a, b) => b.items.length - a.items.length)
  if (one2one.length) classGroups.push({ key: 'o2o', name: '一对一', items: one2one })
  return classGroups
})

/** 当前选中班级的学员列表 */
const activeItems = computed(() => groups.value.find((g) => g.key === activeKey.value)?.items || [])
</script>

<style scoped>
/* 班级切换条：胶囊式分组标签，选中态主色点亮 */
.clazz-bar {
  display: flex; flex-wrap: wrap; gap: 8px;
  padding: 2px 0 14px;
}
.clazz-chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 5px 14px; border-radius: 14px;
  border: 1px solid #e4edf7; background: linear-gradient(180deg, #ffffff, #f7fafd);
  font-size: 13px; color: #475467; cursor: pointer; white-space: nowrap;
  transition: all 0.2s ease;
}
.clazz-chip:hover { color: #155EEF; border-color: rgba(21, 94, 239, 0.45); }
.clazz-chip.active {
  color: #fff; border-color: transparent;
  background: linear-gradient(135deg, #155EEF, #5B8DEF);
  box-shadow: 0 3px 10px rgba(21, 94, 239, 0.22);
}
.chip-name { font-weight: 600; }
.chip-count { font-size: 12px; opacity: 0.85; }

/* 脱敏手机号：等宽字体便于核对位数 */
.phone { font-family: 'JetBrains Mono', Consolas, monospace; letter-spacing: 0.5px; }
.tip-icon { margin-left: 4px; color: #98a2b3; font-size: 14px; vertical-align: -2px; }
.enroll-title {
  font-size: 13.5px; font-weight: 600; color: #344054;
  margin: 18px 0 10px;
}
</style>
