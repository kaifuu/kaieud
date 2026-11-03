<template>
  <div class="page tabs-page">
    <div class="page-header">
      <span class="page-title">容器管理</span>
      <div class="actions" v-if="tab === 'build'">
        <el-button type="primary" @click="openBuild">打包发布</el-button>
      </div>
      <div class="actions" v-if="tab === 'server'">
        <el-button type="primary" @click="openTarget()">新增服务器</el-button>
      </div>
    </div>

    <div class="panel" style="padding: 0 16px; margin-bottom: 14px">
      <el-tabs v-model="tab">
        <el-tab-pane label="服务器" name="server" />
        <el-tab-pane label="容器" name="container" />
        <el-tab-pane label="构建日志" name="build" />
        <el-tab-pane label="远程日志" name="remote" />
      </el-tabs>
    </div>

    <!-- TAB1 服务器 -->
    <div v-show="tab === 'server'" class="panel table-panel tabs-fill">
      <div class="toolbar">
        <span style="color: #8fa8c4; font-size: 13px">发布目标服务器（腾讯云等）：打包产物经 SFTP 上传后由 deploy.sh 拉起 db+app+web 三容器</span>
      </div>
      <el-table :data="targets" v-loading="tLoading" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" width="130" show-overflow-tooltip />
        <el-table-column prop="host" label="主机" min-width="130" show-overflow-tooltip />
        <el-table-column prop="sshPort" label="SSH端口" width="80" show-overflow-tooltip />
        <el-table-column prop="username" label="用户" width="80" show-overflow-tooltip />
        <el-table-column label="远端目录" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remoteDir }}</template>
        </el-table-column>
        <el-table-column label="数据库" width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.dbMode === 2" size="small" type="warning" effect="light">外部库</el-tag>
            <el-tag v-else size="small" type="success" effect="light">随包MySQL</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="端口(应用/前端)" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.appPort }} / {{ row.webPort }}</template>
        </el-table-column>
        <el-table-column label="状态" width="70" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'" effect="plain">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="SSH连通" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.lastTestOk === 1" style="color: #12b76a">✓ {{ (row.lastTestMsg || '').slice(0, 12) }}</span>
            <span v-else-if="row.lastTestOk === 0" style="color: #f04438">{{ (row.lastTestMsg || '失败').slice(0, 12) }}</span>
            <span v-else style="color: #98a2b3">未测试</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="testTarget(row)">测试</el-button>
            <el-button link type="primary" @click="openTarget(row)">编辑</el-button>
            <el-popconfirm title="确认删除该服务器配置?" @confirm="delTarget(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- TAB2 容器：远程 docker compose 容器实况 -->
    <div v-show="tab === 'container'" class="panel table-panel tabs-fill">
      <div class="toolbar">
        <span style="color: #8fa8c4; font-size: 13px">各服务器容器实况（kaied-db / kaied-app / kaied-web）与启停控制</span>
        <div style="flex: 1"></div>
        <el-button @click="refreshAllContainers">刷新全部</el-button>
      </div>
      <div class="container-grid">
        <div v-for="s in targets" :key="s.id" class="server-card panel">
          <div class="sc-head">
            <b>{{ s.name }}</b>
            <span class="sc-host">{{ s.host }}</span>
          </div>
          <pre class="sc-ps">{{ containerPs[s.id] || '点「状态」加载容器实况…' }}</pre>
          <div class="sc-ops">
            <el-button size="small" @click="serviceOp(s, 'status', true)">状态</el-button>
            <el-button size="small" type="success" @click="serviceOp(s, 'start', true)">启动</el-button>
            <el-button size="small" type="warning" @click="serviceOp(s, 'restart', true)">重启</el-button>
            <el-button size="small" type="warning" @click="serviceOp(s, 'stop', true)">停止</el-button>
          </div>
        </div>
      </div>
      <el-empty v-if="!targets.length" description="请先在「服务器」TAB 配置目标服务器" :image-size="70" />
    </div>

    <!-- TAB3 构建日志：本地打包（mvn/npm/tar）记录 -->
    <div v-show="tab === 'build'" class="panel table-panel tabs-fill">
      <div class="toolbar">
        <span style="color: #8fa8c4; font-size: 13px">本地打包日志：mvn package + npm build + 封装 docker compose 发布包全过程</span>
        <div style="flex: 1"></div>
        <el-button @click="recPage.load()">刷新</el-button>
      </div>
      <el-table :data="recPage.rows.value" v-loading="recPage.loading.value" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" :index="recPage.seq" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : (row.status === 'FAILED' ? 'danger' : 'warning')"
                    effect="light">{{ { SUCCESS: '成功', FAILED: '失败', RUNNING: '打包中' }[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="产物" min-width="170" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="90" show-overflow-tooltip />
        <el-table-column label="时间" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewLog(row, '构建日志')">日志</el-button>
            <el-button v-if="row.status === 'SUCCESS'" link type="warning" @click="openDeploy(row)">部署</el-button>
            <el-popconfirm title="确认删除该记录?" @confirm="delRecord(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, prev, pager, next" :total="recPage.pager.total"
                       v-model:current-page="recPage.pager.page" :page-size="recPage.pager.size"
                       @current-change="recPage.load" />
      </div>
    </div>

    <!-- TAB4 远程日志：远程部署(docker compose拉起)与服务操作记录 -->
    <div v-show="tab === 'remote'" class="panel table-panel tabs-fill">
      <div class="toolbar">
        <span style="color: #8fa8c4; font-size: 13px">远程部署日志：上传发布包 → 解包 → docker compose 拉起容器（镜像拉取/启动）全过程输出</span>
        <div style="flex: 1"></div>
        <el-button @click="remotePage.load()">刷新</el-button>
      </div>
      <el-table :data="remotePage.rows.value" v-loading="remotePage.loading.value" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" :index="remotePage.seq" show-overflow-tooltip />
        <el-table-column label="类型" width="80" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'DEPLOY' ? 'warning' : 'info'" effect="light">
              {{ row.type === 'DEPLOY' ? '部署' : '服务' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" min-width="130" show-overflow-tooltip />
        <el-table-column prop="targetName" label="服务器" min-width="130" show-overflow-tooltip />
        <el-table-column prop="action" label="动作" width="70" show-overflow-tooltip>
          <template #default="{ row }">{{ { status: '状态', start: '启动', stop: '停止', restart: '重启', logs: '日志' }[row.action] || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : (row.status === 'FAILED' ? 'danger' : 'warning')"
                    effect="light">{{ { SUCCESS: '成功', FAILED: '失败', RUNNING: '进行中' }[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="90" show-overflow-tooltip />
        <el-table-column label="时间" min-width="165" show-overflow-tooltip>
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewLog(row, '远程日志')">日志</el-button>
            <el-popconfirm title="确认删除该记录?" @confirm="delRecord(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, prev, pager, next" :total="remotePage.pager.total"
                       v-model:current-page="remotePage.pager.page" :page-size="remotePage.pager.size"
                       @current-change="remotePage.load" />
      </div>
    </div>

    <!-- 服务器编辑（服务器信息 / 数据库配置 双TAB） -->
    <el-drawer v-model="tVisible" :title="tForm.id ? '编辑服务器' : '新增服务器'" size="480px">
      <el-tabs v-model="tTab" style="margin-top: -10px">
        <el-tab-pane label="服务器信息" name="srv" />
        <el-tab-pane label="数据库配置" name="db" />
      </el-tabs>
      <el-form v-show="tTab === 'srv'" :model="tForm" label-width="110px">
        <el-form-item label="名称" required><el-input v-model="tForm.name" placeholder="如：腾讯云生产" /></el-form-item>
        <el-form-item label="主机IP/域名" required><el-input v-model="tForm.host" /></el-form-item>
        <el-form-item label="SSH端口"><el-input-number v-model="tForm.sshPort" :min="1" :max="65535" style="width: 100%" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="tForm.username" placeholder="默认 root" /></el-form-item>
        <el-form-item label="认证方式">
          <el-radio-group v-model="tForm.authType">
            <el-radio :value="1">密码</el-radio><el-radio :value="2">私钥</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="tForm.authType === 2 ? '私钥口令' : 'SSH密码'" :required="!tForm.id">
          <el-input v-model="tForm.password" type="password" show-password
                    :placeholder="tForm.id ? '留空=不修改' : ''" />
        </el-form-item>
        <el-form-item v-if="tForm.authType === 2" label="私钥文件路径">
          <el-input v-model="tForm.keyPath" placeholder="本机私钥路径，如 ~/.ssh/id_rsa" />
        </el-form-item>
        <el-form-item label="远端部署目录"><el-input v-model="tForm.remoteDir" placeholder="/opt/kaied" /></el-form-item>
        <el-form-item label="应用端口"><el-input-number v-model="tForm.appPort" :min="1" :max="65535" style="width: 100%" /></el-form-item>
        <el-form-item label="前端端口"><el-input-number v-model="tForm.webPort" :min="1" :max="65535" style="width: 100%" /></el-form-item>
        <el-form-item label="MySQL密码">
          <el-input v-model="tForm.dbPassword" placeholder="随包数据库 root 密码，默认 kaied123" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="tForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>

      <el-form v-show="tTab === 'db'" :model="tForm" label-width="110px">
        <el-form-item label="数据库模式">
          <el-radio-group v-model="tForm.dbMode">
            <el-radio :value="1">随包 MySQL</el-radio>
            <el-radio :value="2">外部数据库</el-radio>
          </el-radio-group>
          <div class="form-tip">随包：部署时自动拉起 MySQL 容器并导入初始数据；外部：容器直连你指定的数据库（需提前建好库并导入 sql/edu_course.sql）</div>
        </el-form-item>
        <template v-if="tForm.dbMode === 2">
          <el-form-item label="数据库类型">
            <el-select v-model="tForm.dbType" style="width: 100%" disabled>
              <el-option label="MySQL" value="MYSQL" />
            </el-select>
          </el-form-item>
          <el-form-item label="地址" required><el-input v-model="tForm.dbHost" placeholder="数据库IP/域名" /></el-form-item>
          <el-form-item label="端口"><el-input-number v-model="tForm.dbPort" :min="1" :max="65535" style="width: 100%" /></el-form-item>
          <el-form-item label="数据库名"><el-input v-model="tForm.dbName" placeholder="edu_course" /></el-form-item>
          <el-form-item label="用户名" required><el-input v-model="tForm.dbUsername" placeholder="root" /></el-form-item>
          <el-form-item label="密码">
            <el-input v-model="tForm.dbPasswordExt" type="password" show-password
                      :placeholder="tForm.id ? '留空=不修改' : ''" />
          </el-form-item>
          <el-form-item label="连接测试">
            <el-button :loading="dbTesting" @click="testDb">测试连接</el-button>
            <el-button :loading="dbIniting" type="warning" @click="initDb">构建初始表</el-button>
            <div class="form-tip">构建初始表：在远程库自动建一套系统表与基础数据（仅平台管理员 admin，无演示企业/业务数据）；库中已有表时拒绝执行以防覆盖</div>
            <span v-if="tForm.lastDbTestOk === 1" style="color: #12b76a; margin-left: 8px">✓ {{ (tForm.lastDbTestMsg || '').slice(0, 30) }}</span>
            <span v-else-if="tForm.lastDbTestOk === 0" style="color: #f04438; margin-left: 8px">{{ (tForm.lastDbTestMsg || '失败').slice(0, 40) }}</span>
            <div class="form-tip">会自动保存当前配置再测试；部署后容器将默认连接此数据库</div>
          </el-form-item>
        </template>
        <el-alert v-else type="info" :closable="false"
                  title="随包模式无需配置：部署时自动创建 MySQL 容器（root密码在前一TAB「MySQL密码」中设置），首启自动建库导入初始数据" />
      </el-form>
      <template #footer>
        <el-button @click="tVisible = false">取消</el-button>
        <el-button type="primary" :loading="tSaving" @click="saveTarget">保存</el-button>
      </template>
    </el-drawer>

    <!-- 打包弹窗：可选目标服务器，打包成功后自动部署 -->
    <el-dialog v-model="bVisible" title="打包发布" width="460px">
      <el-form label-width="110px">
        <el-form-item label="版本号">
          <el-input v-model="bVersion" placeholder="留空按时间戳生成，如 v2.27" />
        </el-form-item>
        <el-form-item label="发布到服务器">
          <el-select v-model="bTargetId" clearable placeholder="可稍后在构建日志中手动部署" style="width: 100%">
            <el-option v-for="t in enabledTargets" :key="t.id"
                       :label="`${t.name}（${t.host}）`" :value="t.id" />
          </el-select>
          <div class="form-tip">选择后：打包完成自动部署到该服务器（上传→解包→docker compose 拉起）；不选则仅打包</div>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false"
                title="将在本机执行：mvn package + npm run build，连同 Dockerfile/compose/初始SQL 打成 tar.gz（约2-4分钟）" />
      <template #footer>
        <el-button @click="bVisible = false">取消</el-button>
        <el-button type="primary" @click="doBuild">开始打包</el-button>
      </template>
    </el-dialog>

    <!-- 部署弹窗 -->
    <el-dialog v-model="dVisible" title="远程部署" width="440px">
      <el-form label-width="90px">
        <el-form-item label="发布包">
          <el-input :model-value="dBuild.fileName" disabled />
        </el-form-item>
        <el-form-item label="目标服务器" required>
          <el-select v-model="dTargetId" style="width: 100%" placeholder="选择服务器">
            <el-option v-for="t in enabledTargets" :key="t.id"
                       :label="`${t.name}（${t.host}）`" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false"
                title="将上传发布包并拉起 kaied-db/kaied-app/kaied-web 容器；首次含镜像拉取较慢，请在远程日志中查看进度" />
      <template #footer>
        <el-button @click="dVisible = false">取消</el-button>
        <el-button type="primary" :loading="dSaving" @click="doDeploy">开始部署</el-button>
      </template>
    </el-dialog>

    <!-- 日志抽屉（RUNNING 自动轮询） -->
    <el-drawer v-model="lVisible" :title="logTitle" size="640px">
      <div class="log-toolbar">
        <el-tag v-if="logRec.status === 'RUNNING'" type="warning" effect="light" size="small">进行中，每3秒自动刷新…</el-tag>
        <el-tag v-else :type="logRec.status === 'SUCCESS' ? 'success' : 'danger'" effect="light" size="small">
          {{ logRec.status === 'SUCCESS' ? '成功' : '失败' }}
        </el-tag>
        <el-button link @click="loadLog(logRec.id)">手动刷新</el-button>
      </div>
      <pre class="log-body">{{ logRec.log || '暂无日志' }}</pre>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'

const tab = ref('server')

/* ===== 服务器 ===== */
const targets = ref([])
const tLoading = ref(false)
const enabledTargets = computed(() => targets.value.filter((t) => t.enabled === 1))
async function loadTargets() {
  tLoading.value = true
  try { targets.value = (await api.deployTargets()) || [] } finally { tLoading.value = false }
}

const tVisible = ref(false)
const tSaving = ref(false)
const tForm = ref({})
const tTab = ref('srv')
const dbTesting = ref(false)

function openTarget(row) {
  tTab.value = 'srv'
  tForm.value = row
    ? { ...row, password: '', dbPasswordExt: '' }
    : { sshPort: 22, username: 'root', authType: 1, remoteDir: '/opt/kaied',
        appPort: 8080, webPort: 80, dbPassword: 'kaied123', enabled: 1,
        dbMode: 1, dbType: 'MYSQL', dbPort: 3306, dbName: 'edu_course', dbPasswordExt: '' }
  tVisible.value = true
}
/** 静默保存（返回新ID） */
async function doSaveTarget() {
  const f = tForm.value
  if (!f.name || !f.host) { ElMessage.warning('名称与主机为必填项'); return null }
  if (!f.id && !f.password) { ElMessage.warning('请填写 SSH 密码'); return null }
  const id = await api.deploySaveTarget(f)
  if (id) f.id = id
  return id ?? f.id
}
async function saveTarget() {
  tSaving.value = true
  try {
    const id = await doSaveTarget()
    if (!id) return
    ElMessage.success('已保存')
    tVisible.value = false
    loadTargets()
  } finally { tSaving.value = false }
}
async function delTarget(id) {
  await api.deployDelTarget(id)
  ElMessage.success('已删除')
  loadTargets()
}
async function testTarget(row) {
  try {
    const msg = await api.deployTestTarget(row.id)
    ElMessage.success('连接成功：' + (msg || '').slice(0, 40))
    loadTargets()
  } catch (e) { /* 拦截器统一提示 */ }
}
const dbIniting = ref(false)
async function initDb() {
  await ElMessageBox.confirm(
    '将在远程数据库中创建系统表结构与基础数据（仅含平台管理员 admin/123456）。已有表时将拒绝执行。确认继续？',
    '构建初始表', { type: 'warning', confirmButtonText: '开始构建', cancelButtonText: '取消' })
  dbIniting.value = true
  try {
    const id = await doSaveTarget()
    if (!id) return
    const r = await api.deployInitDb(id)
    ElMessageBox.alert(
      `初始化完成（耗时 ${r?.seconds ?? '-'} 秒）。
平台管理员账号：admin，初始密码：123456，登录时企业编码留空。`,
      '构建成功', { type: 'success' })
    loadTargets()
  } catch (e) { /* 拦截器提示 */ } finally { dbIniting.value = false }
}

async function testDb() {
  const f = tForm.value
  if (f.dbMode === 2 && (!f.dbHost || !f.dbUsername))
    return ElMessage.warning('请先填写数据库地址与用户名')
  dbTesting.value = true
  try {
    const id = await doSaveTarget()
    if (!id) return
    const msg = await api.deployTestDb(id)
    ElMessage.success(msg || '连接成功')
    f.lastDbTestOk = 1
    f.lastDbTestMsg = msg
    loadTargets()
  } catch (e) {
    f.lastDbTestOk = 0
    f.lastDbTestMsg = (e && e.message) || '连接失败'
  } finally { dbTesting.value = false }
}

/* ===== 容器TAB：各服务器容器实况 ===== */
const containerPs = reactive({})
async function refreshAllContainers() {
  for (const s of targets.value) {
    try {
      const r = await api.deployService(s.id, 'status')
      containerPs[s.id] = r?.log || r?.msg || ''
    } catch (e) { /* 单台失败不影响其它 */ }
  }
}
async function serviceOp(row, action, silent) {
  const r = await api.deployService(row.id, action)
  if (silent) {
    containerPs[row.id] = r?.log || ''
    remotePage.load()
  } else {
    logRec.value = r || {}
    logTitle.value = `服务${{ status: '状态', start: '启动', stop: '停止', restart: '重启', logs: '日志' }[action]} · ${row.name}`
    lVisible.value = true
    remotePage.load()
  }
}

/* ===== 打包（可指定服务器自动部署） ===== */
const bVisible = ref(false)
const bVersion = ref('')
const bTargetId = ref(null)
function openBuild() {
  bVersion.value = ''
  bTargetId.value = enabledTargets.value[0]?.id ?? null
  bVisible.value = true
}
async function doBuild() {
  const version = bVersion.value
  const targetId = bTargetId.value
  bVisible.value = false
  const r = await api.deployBuild({ version })
  ElMessage.success('打包任务已提交')
  logRec.value = r || {}
  logTitle.value = `构建日志 · ${r?.version || ''}`
  lVisible.value = true
  tab.value = 'build'
  recPage.load()
  // 选了服务器：打包成功后自动部署
  if (targetId) {
    const poll = setInterval(async () => {
      try {
        const cur = await api.deployRecord(r.id)
        if (cur && cur.status !== 'RUNNING') {
          clearInterval(poll)
          if (cur.status === 'SUCCESS') {
            await api.deployDeploy(r.id, { targetId })
            ElMessage.success('打包完成，已自动发起部署，请在「远程日志」查看')
            tab.value = 'remote'
            remotePage.load()
          }
        }
      } catch (e) { clearInterval(poll) }
    }, 3000)
  }
}

/* ===== 部署 ===== */
const dVisible = ref(false)
const dSaving = ref(false)
const dBuild = ref({})
const dTargetId = ref(null)
function openDeploy(row) {
  dBuild.value = row
  dTargetId.value = enabledTargets.value[0]?.id ?? null
  dVisible.value = true
}
async function doDeploy() {
  if (!dTargetId.value) return ElMessage.warning('请选择目标服务器')
  dSaving.value = true
  try {
    const r = await api.deployDeploy(dBuild.value.id, { targetId: dTargetId.value })
    dVisible.value = false
    ElMessage.success('部署任务已提交，请在「远程日志」查看进度')
    tab.value = 'remote'
    logRec.value = r || {}
    logTitle.value = `远程日志 · 部署 ${r?.version || ''} → ${r?.targetName || ''}`
    lVisible.value = true
    remotePage.load()
  } finally { dSaving.value = false }
}

/* ===== 构建日志（本地打包） ===== */
const recPage = usePage((q) => api.deployRecords({ ...q, type: 'BUILD' }), {})

/* ===== 远程日志：全部远程记录（部署 + 服务操作）按时间倒序混排 ===== */
const remotePage = usePage((q) => api.deployRecords({ ...q, type: 'REMOTE' }), {})

/* ===== 日志查看与轮询 ===== */
const lVisible = ref(false)
const logRec = ref({})
const logTitle = ref('日志')
let timer = null
async function viewLog(row, kind) {
  logRec.value = row
  logTitle.value = `${kind} · ${row.version || row.action || ''} ${row.targetName ? '→ ' + row.targetName : ''}`
  lVisible.value = true
  await loadLog(row.id)
}
async function loadLog(id) {
  const r = await api.deployRecord(id)
  if (r) logRec.value = r
}
async function delRecord(id) {
  await api.deployDelRecord(id)
  ElMessage.success('已删除')
  recPage.load(); remotePage.load()
}
function ensurePoll() {
  clearInterval(timer)
  timer = setInterval(() => {
    if (lVisible.value && logRec.value.status === 'RUNNING') loadLog(logRec.value.id)
    if (recPage.rows.value.some((r) => r.status === 'RUNNING')) recPage.load()
    if (remotePage.rows.value.some((r) => r.status === 'RUNNING')) remotePage.load()
  }, 3000)
}

function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }
onMounted(() => { loadTargets(); recPage.load(); remotePage.load(); ensurePoll() })
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.form-tip { font-size: 12px; color: #8fa8c4; line-height: 1.5; margin-top: 4px; }
.log-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.log-body {
  background: #0d1526; color: #c9e3ff; border-radius: 10px;
  padding: 14px 16px; font-size: 12.5px; line-height: 1.75;
  white-space: pre-wrap; word-break: break-all;
  max-height: calc(100vh - 180px); overflow: auto;
}
/* 容器TAB：服务器卡片网格 */
.container-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 14px; overflow: auto; padding: 2px;
}
.server-card { padding: 14px 16px; }
.sc-head { display: flex; align-items: baseline; gap: 8px; margin-bottom: 8px; }
.sc-head b { font-size: 14.5px; color: #142a44; }
.sc-host { font-size: 12.5px; color: #8fa8c4; }
.sc-ps {
  background: #0d1526; color: #c9e3ff; border-radius: 8px;
  padding: 10px 12px; font-size: 12px; line-height: 1.7;
  white-space: pre-wrap; word-break: break-all;
  min-height: 110px; max-height: 220px; overflow: auto;
}
.sc-ops { display: flex; gap: 6px; margin-top: 10px; }
</style>
