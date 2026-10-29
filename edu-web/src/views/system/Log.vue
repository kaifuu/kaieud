<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">日志管理</span>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="操作内容 / 用户名" clearable style="width: 210px"
                  @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.logType" placeholder="日志类型" clearable style="width: 120px">
          <el-option label="操作日志" :value="0" /><el-option label="登录日志" :value="1" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="成功" :value="0" /><el-option label="失败" :value="1" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button v-if="sel.length" type="danger" @click="batchRemove">批量删除({{ sel.length }})</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="log" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort" @selection-change="sel = $event">
        <el-table-column type="selection" width="42" />
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.logType" label="类型" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="row.logType === 1 ? 'primary' : 'info'" effect="plain">
              {{ row.logType === 1 ? '登录日志' : '操作日志' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.username" prop="username" label="操作人" width="110" sortable="custom" />
        <el-table-column v-if="cols.operation" prop="operation" label="操作内容" width="150" sortable="custom" />
        <el-table-column v-if="cols.method" prop="method" label="方法" min-width="200" show-overflow-tooltip />
        <el-table-column v-if="cols.ip" prop="ip" label="IP" width="135" />
        <el-table-column v-if="cols.duration" prop="duration" label="耗时(ms)" width="100" sortable="custom" />
        <el-table-column v-if="cols.status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 0 ? 'success' : 'danger'" effect="plain">
              {{ row.status === 0 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.createTime" prop="createTime" label="时间" width="170" sortable="custom">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确认删除该日志?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
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
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'

const query = reactive({ keyword: '', status: null, logType: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.logPage(q), query)

const allColumns = [
  { prop: 'logType', label: '类型' },
  { prop: 'username', label: '操作人' }, { prop: 'operation', label: '操作内容' },
  { prop: 'method', label: '方法' }, { prop: 'ip', label: 'IP' },
  { prop: 'duration', label: '耗时' }, { prop: 'status', label: '状态' },
  { prop: 'createTime', label: '时间' }
]
const cols = reactive({ logType: true, username: true, operation: true, method: true, ip: true,
  duration: true, status: true, createTime: true })
const onCols = (c) => Object.assign(cols, c)

function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }
async function remove(id) {
  await api.logDel(id)
  ElMessage.success('已删除')
  load()
}

function doExport() {
  exportXlsx('操作日志', [
      { label: '操作人', prop: 'username' }, { label: '操作内容', prop: 'operation' },
      { label: '方法', prop: 'method' }, { label: 'IP', prop: 'ip' },
      { label: '耗时ms', prop: 'duration' }, { label: '时间', prop: 'createTime' }
    ], rows.value)
}

const sel = ref([])
async function batchRemove() {
  await ElMessageBox.confirm(`确认删除选中的 ${sel.value.length} 条日志数据？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(sel.value.map((r) => api.logDel(r.id)))
  const ok = results.filter((x) => x.status === 'fulfilled').length
  const fail = results.length - ok
  ElMessage.success(`批量删除完成：成功 ${ok} 条` + (fail ? `，失败 ${fail} 条` : ''))
  sel.value = []
  load()
}

onMounted(load)
</script>
