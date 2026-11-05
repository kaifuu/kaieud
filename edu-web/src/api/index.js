import request from './request'

const get = (url, params) => request.get(url, { params })
const post = (url, data) => request.post(url, data)
const put = (url, data) => request.put(url, data)
const del = (url) => request.delete(url)

export default {
  // 认证
  login: (d) => post('/auth/login', d),
  captcha: () => get('/auth/captcha'),
  logout: () => post('/auth/logout'),
  me: () => get('/auth/me'),
  meMenus: () => get('/auth/menus'),
  changePassword: (d) => post('/auth/password', d),
  // 套餐/合同/消课/成本/导入
  packagePage: (p) => get('/edu/package/page', p),
  packageList: () => get('/edu/package/list'),
  packageSave: (d) => post('/edu/package', d),
  packageDel: (id) => del(`/edu/package/${id}`),
  packageContracts: (id) => get(`/edu/package/${id}/contracts`),
  contractPage: (p) => get('/edu/contract/page', p),
  contractSave: (d) => post('/edu/contract', d),
  contractUpdate: (d) => put('/edu/contract', d),
  contractDetail: (id) => get(`/edu/contract/${id}`),
  contractDel: (id) => del(`/edu/contract/${id}`),
  contractStudentSummary: (studentId) => get('/edu/contract/student-summary', { studentId }),
  // 咨询顾问候选：用户管理中非学生/非系统用户的在职账号（租户内）
  contractAdvisorCandidates: () => get('/edu/contract/advisor-candidates'),
  contractRefund: (id, d) => post(`/edu/contract/${id}/refund`, d),
  contractRefundInfo: (id) => get(`/edu/contract/${id}/refund-info`),
  // 学生账户：充值管理（教务）+ 学生中心账户
  accountPage: (p) => get('/edu/account/page', p),
  accountSummary: (p) => get('/edu/account/summary', p),
  accountRecharge: (d) => post('/edu/account/recharge', d),
  portalMyAccount: () => get('/portal/my-account'),
  // 全部学生充值记录（企业管理员/财务，本租户范围，仅查询）
  portalAccountRecords: (p) => get('/portal/account-records', p),
  consumePage: (p) => get('/edu/consume/page', p),
  consumeManual: (d) => post('/edu/consume/manual', d),
  consumeSign: (scheduleId, hours) => post(`/edu/consume/sign/${scheduleId}?hours=${hours}`),
  consumeCancel: (id) => post(`/edu/consume/${id}/cancel`),
  consumeAudit: (id, d) => post(`/edu/consume/${id}/audit`, d),
  costDetail: (p) => get('/cost/detail', p),
  costSummary: (p) => get('/cost/summary', p),
  costDashboard: (p) => get('/cost/dashboard', p),
  importStudents: (rows) => post('/sys/import/students', rows),
  upload: (formData) => request.post('/common/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),

  teacherFreeTime: (p) => get('/edu/teacher/free-time', p),
  // 基础设置
  configAll: () => get('/sys/config/all'),
  configSave: (d) => post('/sys/config/save', d),
  configPublic: () => get('/sys/config/public'),
  // 系统字典
  dictAll: () => get('/sys/dict/all'),
  dictTypeNames: () => get('/sys/dict/type-names'),
  dictSave: (d) => post('/sys/dict', d),
  dictDel: (id) => del(`/sys/dict/${id}`),
  dictTypeSave: (d) => post('/sys/dict/type', d),
  dictTypeRename: (d) => put('/sys/dict/type', d),
  dictTypeDel: (type) => del(`/sys/dict/type/${encodeURIComponent(type)}`),
  // 通知
  noticePage: (p) => get('/sys/notice/page', p),
  // 消息历史：全部已发布通知全量记录（只读，任何登录用户可查）
  noticeHistory: (p) => get('/sys/notice/history', p),
  // 通知已读状态：已读ID集合 / 标记已读（点击查看内容时）/ 全部已读
  noticeReadIds: () => get('/sys/notice/read-ids'),
  noticeUnreadCount: () => get('/sys/notice/unread-count'),
  noticeRead: (id) => post('/sys/notice/read', { id }),
  noticeReadAll: () => post('/sys/notice/read-all'),
  // 平台公告管理（平台运营TAB）：tenant_id=0 的通知（系统升级公告等），仅平台运营
  noticePlatformPage: (p) => get('/sys/notice/platform-page', p),
  noticeRecent: (limit) => get('/sys/notice/recent', { limit }),
  noticeSave: (d) => post('/sys/notice', d),
  noticePublish: (id) => post(`/sys/notice/${id}/publish`),
  noticeDel: (id) => del(`/sys/notice/${id}`),
  configList: (p) => get('/sys/notify-config/list', p),
  // 注意：通知配置保存（曾与基础设置的 configSave 重名互相覆盖，导致基础设置保存误插 sys_notify_config）
  notifyConfigSave: (d) => post('/sys/notify-config', d),
  configToggle: (id, enabled) => post(`/sys/notify-config/${id}/toggle?enabled=${enabled}`),
  configDel: (id) => del(`/sys/notify-config/${id}`),
  updateProfile: (d) => put('/auth/profile', d),
  // 首页/统计
  overview: () => get('/home/overview'),
  statSummary: (p) => get('/stats/summary', p),
  // 系统管理
  tenantPage: (p) => get('/sys/tenant/page', p),
  tenantList: () => get('/sys/tenant/list'),
  tenantSave: (d) => post('/sys/tenant', d),
  tenantUpdate: (d) => put('/sys/tenant', d),
  tenantDel: (id) => del(`/sys/tenant/${id}`),
  // 启用/禁用租户（禁用后该租户所有用户立即无法访问）
  tenantToggle: (id, enable) => post(`/sys/tenant/${id}/toggle?enable=${enable}`),
  // 回收站与恢复：软删除的租户可一键恢复（用户重新启用，默认数据幂等补齐）
  tenantRecycle: () => get('/sys/tenant/recycle'),
  tenantRestore: (id) => post(`/sys/tenant/${id}/restore`),
  // 物理删除企业数据（需超级秘钥，审计留痕）；清除历史（审计记录）
  tenantPurge: (id, secret) => post(`/sys/tenant/${id}/purge`, { secret }),
  tenantPurgeLogs: () => get('/sys/tenant/purge-logs'),
  // 存量租户补开通（复制平台模板菜单/角色 + 生成租户管理员）
  tenantProvision: (id) => post(`/sys/tenant/${id}/provision`),
  orgTree: () => get('/sys/org/tree'),
  orgSave: (d) => post('/sys/org', d),
  orgUpdate: (d) => put('/sys/org', d),
  orgDel: (id) => del(`/sys/org/${id}`),
  userPage: (p) => get('/sys/user/page', p),
  userSave: (d) => post('/sys/user', d),
  userUpdate: (d) => put('/sys/user', d),
  userDel: (id) => del(`/sys/user/${id}`),
  userRoles: (id) => get(`/sys/user/${id}/roles`),
  userResetPwd: (id) => post(`/sys/user/${id}/reset-password`),
  userProfile: (id) => get(`/sys/user/${id}/profile`),
    userAvatarUpload: (id, fd) => request.post(`/sys/user/${id}/avatar-upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } }),
  menuTree: (params) => get('/sys/menu/tree', params),
  menuSave: (d) => post('/sys/menu', d),
  menuUpdate: (d) => put('/sys/menu', d),
  menuDel: (id) => del(`/sys/menu/${id}`),
  rolePage: (p) => get('/sys/role/page', p),
  roleList: () => get('/sys/role/list'),
  roleSave: (d) => post('/sys/role', d),
  roleUpdate: (d) => put('/sys/role', d),
  roleDel: (id) => del(`/sys/role/${id}`),
  roleMenus: (id) => get(`/sys/role/${id}/menus`),
  roleAssignMenus: (id, ids) => post(`/sys/role/${id}/menus`, ids),
  logPage: (p) => get('/sys/log/page', p),
  logDel: (id) => del(`/sys/log/${id}`),
  // 教务管理

  studentList: () => get('/edu/student/list'),




  teacherList: () => get('/edu/teacher/list'),



  coursePage: (p) => get('/edu/course/page', p),
  courseList: () => get('/edu/course/list'),
  coursePrices: (id) => get(`/edu/course/${id}/prices`),
  courseSave: (d) => post('/edu/course', d),
  courseUpdate: (d) => put('/edu/course', d),
  courseDel: (id) => del(`/edu/course/${id}`),
  clazzPage: (p) => get('/edu/clazz/page', p),
  // 班主任候选：用户管理中类型为员工的在职人员（含教师档案ID）
  clazzTeacherCandidates: () => get('/edu/clazz/teacher-candidates'),
  // 班级学生：候选（合同号/姓名/手机号）、加入（容量校验）、调班、班级学生列表
  clazzStudentCandidates: (p) => get('/edu/clazz/student-candidates', p),
  clazzAddStudent: (id, d) => post(`/edu/clazz/${id}/students`, d),
  clazzTransfer: (fromId, d) => post(`/edu/clazz/${fromId}/transfer`, d),
  clazzStudents: (id) => get(`/edu/clazz/${id}/students`),
  clazzList: () => get('/edu/clazz/list'),
  clazzSave: (d) => post('/edu/clazz', d),
  clazzUpdate: (d) => put('/edu/clazz', d),
  clazzDel: (id) => del(`/edu/clazz/${id}`),

  enrollPage: (p) => get('/edu/enroll/page', p),
  scheduleClazzStudents: (clazzId) => get('/edu/schedule/clazz-students', { clazzId }),
  enrollSubscribe: (d) => post('/edu/enroll/subscribe', d),
  enrollUpdate: (d) => put('/edu/enroll', d),
  enrollCredit: (d) => post('/edu/enroll/credit', d),
  enrollUnsubscribe: (id) => post(`/edu/enroll/${id}/unsubscribe`),
  enrollAssignTeacher: (id, teacherId) => post(`/edu/enroll/${id}/assign-teacher`, { teacherId }),
  enrollCompensateHours: (id, hours) => post(`/edu/enroll/${id}/compensate-hours`, { hours }),
  schedulePage: (p) => get('/edu/schedule/page', p),
  classroomPage: (p) => get('/edu/classroom/page', p),
  // 教室联系人候选：本企业在职员工（含手机号，选中联动回填）
  classroomContactCandidates: () => get('/edu/classroom/contact-candidates'),
  classroomList: () => get('/edu/classroom/list'),
  classroomSave: (d) => post('/edu/classroom', d),
  classroomUpdate: (d) => put('/edu/classroom', d),
  classroomDel: (id) => del(`/edu/classroom/${id}`),
  classroomOccupancy: (p) => get(`/edu/classroom/${p.id}/occupancy`, { begin: p.begin, end: p.end }),
  schedulePhaseCounts: (p) => get('/edu/schedule/phase-counts', p),
  scheduleHistory: (p) => get('/edu/schedule/history', p),
  scheduleDayDetail: (p) => get('/edu/schedule/day-detail', p),
    scheduleCreate: (d) => post('/edu/schedule', d),
  scheduleAdjust: (id, d) => post(`/edu/schedule/${id}/adjust`, d),
  scheduleCancel: (id, d) => post(`/edu/schedule/${id}/cancel`, d),
  scheduleComplete: (id) => post(`/edu/schedule/${id}/complete`),
  scheduleAttend: (id, hours, remark) => post(`/edu/schedule/${id}/attend`, { hours, remark }),
  scheduleAttendInfo: (id) => get(`/edu/schedule/${id}/attend-info`),
  scheduleDelete: (id) => post(`/edu/schedule/${id}/delete`),
  scheduleLeave: (id, type) => post(`/edu/schedule/${id}/leave?type=${type}`),
  scheduleAbsent: (id, reason) => post(`/edu/schedule/${id}/absent`, { reason }),
  scheduleCompensate: (id, d) => post(`/edu/schedule/${id}/compensate`, d),
  // 旷课审批 / 课时管理
  absencePage: (p) => get('/edu/absence/page', p),
  absenceApprove: (id, remark) => post(`/edu/absence/${id}/approve`, { remark }),
  absenceReject: (id, remark) => post(`/edu/absence/${id}/reject`, { remark }),
  hoursRecords: (p) => get('/edu/hours/records', p),
  paymentPage: (p) => get('/edu/payment/page', p),
  // 缴费操作记录：新增/修改/删除全量留痕（只读，支持类型/学生/单号/操作人/时间段筛选）
  paymentLogPage: (p) => get('/edu/payment/logs', p),
  // 合同缴费汇总：已缴=缴费记录求和（修改时排除本条），待缴=合同金额-已缴
  paymentContractSummary: (p) => get('/edu/payment/contract-summary', p),
  paymentSave: (d) => post('/edu/payment', d),
  paymentUpdate: (d) => put('/edu/payment', d),
  paymentDel: (id) => del(`/edu/payment/${id}`),
  leavePage: (p) => get('/edu/leave/page', p),
  leaveApply: (d) => post('/edu/leave/apply', d),
  leaveApprove: (id, pass) => post(`/edu/leave/${id}/approve?pass=${pass}`),
  consultPage: (p) => get('/edu/consult/page', p),
  consultDel: (id) => del(`/edu/consult/${id}`),
  evaluationPage: (p) => get('/edu/evaluation/page', p),
  // 学生端
  portalCourses: () => get('/portal/courses'),
  portalSubscribe: (d) => post('/portal/subscribe', d),
  // 我的课表：日/周/月日历数据（附课程/班级/老师名称）
  portalMySchedule: (p) => get('/portal/my-schedule', p),
  // 我的记录TAB：本人全部排课记录（分页/筛选/排序，含班课按班级归属）
  portalScheduleRecords: (p) => get('/portal/my-schedule-records', p),
  // 我的签约班级：咨询入口（按老师+班级/课程去重）
  portalMyClasses: () => get('/portal/my-classes'),
  portalMyTeachers: () => get('/portal/my-teachers'),
  portalMyEnrolls: () => get('/portal/my-enrolls'),
  portalConsult: (d) => post('/portal/consult', d),
  // 咨询追问：同一咨询下多次发问（对话流）
  portalConsultAsk: (id, content) => post(`/portal/consult/${id}/ask`, { content }),
  portalConsultMessages: (id) => get(`/portal/consult/${id}/messages`),
  portalEvaluation: (d) => post('/portal/evaluation', d),
  // 我的咨询记录（分页，可按老师筛选）
  portalMyConsults: (p) => get('/portal/my-consults', p),
  // 教师端
  teachSchedule: (p) => get('/teach/my-schedule', p),
  // 我的记录TAB：教师本人全部排课记录（分页/筛选/排序）
  teachScheduleRecords: (p) => get('/teach/my-schedule-records', p),
  // 教师端课次登记（仅限本人课次）：请假限未开始、到课/旷课限已下课
  teachScheduleAttendInfo: (id) => get(`/teach/schedule/${id}/attend-info`),
  teachScheduleAttend: (id, hours, remark) => post(`/teach/schedule/${id}/attend`, { hours, remark }),
  teachScheduleLeave: (id, type) => post(`/teach/schedule/${id}/leave?type=${type}`),
  teachScheduleAbsent: (id, reason) => post(`/teach/schedule/${id}/absent`, { reason }),
  teachStudents: () => get('/teach/my-students'),
  // 学生基本信息（教师端）：仅限本人所带学生，手机号接口层脱敏（前3后4）
  teachStudentInfo: (studentId) => get(`/teach/student-info/${studentId}`),
  teachEvaluations: () => get('/teach/my-evaluations'),
  teachConsults: () => get('/teach/my-consults'),
  teachReply: (id, reply) => post(`/teach/consult/${id}/reply`, { reply }),
  teachIncome: () => get('/teach/my-income'),
  // 容器管理（平台运营）：打包发布 / 远程部署 / 服务操作 / 发布日志
  deployTargets: () => get('/deploy/targets'),
  deploySaveTarget: (d) => post('/deploy/targets', d),
  deployDelTarget: (id) => del(`/deploy/targets/${id}`),
  deployTestTarget: (id) => post(`/deploy/targets/${id}/test`),
  deployTestDb: (id) => post(`/deploy/targets/${id}/test-db`),
  deployInitDb: (id) => post(`/deploy/targets/${id}/init-db`),
  deployBuild: (d) => post('/deploy/build', d),
  deployDeploy: (buildId, d) => post(`/deploy/${buildId}/deploy`, d),
  deployService: (id, action) => post(`/deploy/targets/${id}/service`, { action }),
  deployRecords: (p) => get('/deploy/records', p),
  deployRecord: (id) => get(`/deploy/records/${id}`),
  deployDelRecord: (id) => del(`/deploy/records/${id}`),
}
