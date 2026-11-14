<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">{{ pageTitle }}</span>
      <div class="actions">
        <el-button v-if="mode !== 'list'" @click="tryBackToList">← 返回列表</el-button>
      </div>
    </div>

    <!-- ===== 列表页（默认） ===== -->
    <template v-if="mode === 'list'">
      <!-- 办理入口 -->
      <div class="panel tile-panel">
        <div class="tile" @click="enterForm">
          <div class="tile-icon ti-enroll"><el-icon :size="26"><Tickets /></el-icon></div>
          <p>报名/续报</p>
        </div>
        <div class="tile" @click="enterRecharge">
          <div class="tile-icon ti-recharge"><el-icon :size="26"><Wallet /></el-icon></div>
          <p>余额充值</p>
        </div>
        <div class="tile" @click="enterSupplement">
          <div class="tile-icon ti-pay"><el-icon :size="26"><Money /></el-icon></div>
          <p>补费</p>
        </div>
        <div class="tile" @click="enterTransfer">
          <div class="tile-icon ti-transfer"><el-icon :size="26"><Switch /></el-icon></div>
          <p>转课</p>
        </div>
        <div class="tile" @click="enterRefund">
          <div class="tile-icon ti-refund"><el-icon :size="26"><RefreshLeft /></el-icon></div>
          <p>退费</p>
        </div>
        <div class="tile" @click="enterMaterial">
          <div class="tile-icon ti-fees"><el-icon :size="26"><Reading /></el-icon></div>
          <p>教材杂费</p>
        </div>
        <div class="tile" @click="enterScore">
          <div class="tile-icon ti-score"><el-icon :size="26"><Medal /></el-icon></div>
          <p>积分</p>
        </div>
      </div>

      <!-- 订单列表 -->
      <div class="panel sec-panel">
        <div class="list-toolbar">
          <el-button size="small" :type="quick === 'mine' ? 'primary' : ''" @click="quick = quick === 'mine' ? null : 'mine'">我的今日</el-button>
          <el-button size="small" :type="quick === 'campus' ? 'primary' : ''" @click="quick = quick === 'campus' ? null : 'campus'">校区今日</el-button>
          <el-input v-model="listKw" placeholder="学员姓名 / 课程关键字" clearable style="width: 200px"
                    :prefix-icon="Search" size="small" />
          <el-select v-model="listType" placeholder="办理类型" clearable size="small" style="width: 104px">
            <el-option label="报名/续报" value="报名" /><el-option label="余额充值" value="充值" />
            <el-option label="转课" value="转课" /><el-option label="退费" value="退费" />
            <el-option label="补费" value="补费" /><el-option label="积分" value="积分" />
            <el-option label="教材杂费" value="教材杂费" />
          </el-select>
          <el-select v-model="listStatus" placeholder="报名状态" clearable size="small" style="width: 110px">
            <el-option label="在读" :value="1" /><el-option label="已结课" :value="2" />
            <el-option label="已退订" :value="3" /><el-option label="已转课" :value="4" />
          </el-select>
          <el-select v-model="listOwing" placeholder="到款状态" clearable size="small" style="width: 110px">
            <el-option label="欠费" value="owing" /><el-option label="已结清" value="clear" />
          </el-select>
          <el-date-picker v-model="listRange" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="经办起" end-placeholder="经办止" size="small" style="width: 230px" />
          <div style="flex: 1"></div>
          <ColumnSetting storage-key="reenroll-list" :columns="allColumns" @change="onCols" />
        </div>
        <div class="rec-summary" style="margin-bottom: 8px">
          当前结果：总计 <b>{{ filteredList.length }}</b> 条记录，学员总计 <b>{{ listStats.students }}</b> 人，
          报课总计 <b>{{ listStats.courses }}</b>，优惠总计 <b style="color: #f79009">¥{{ listStats.discount }}</b>，
          退费合计 <b style="color: #f04438">¥{{ listStats.refund }}</b>，
          应收款项总计 <b>¥{{ listSum.due }}</b> ｜ 实收总计 <b>¥{{ listSum.paid }}</b> ｜ 欠费总计 <b class="sum-money">¥{{ listSum.arrears }}</b>
        </div>
        <el-table :data="pagedList" size="small" stripe height="480" v-loading="listLoading">
          <el-table-column v-if="cols.id" prop="rid" label="单号" width="86" align="center" sortable fixed="left">
            <template #default="{ row }">{{ row.rid }}</template>
          </el-table-column>
          <el-table-column v-if="cols.studentId" label="学员姓名" width="90" show-overflow-tooltip fixed="left">
            <template #default="{ row }">{{ stuName(row.studentId) }}</template>
          </el-table-column>
          <el-table-column label="办理类型" width="82" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="plain"
                      :type="{ 报名: '', 充值: 'success', 转课: 'warning', 退费: 'danger', 补费: 'primary', 积分: 'info', 教材杂费: 'success' }[row.type]">
                {{ row.type }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="cols.content" label="交易内容" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <template v-if="row.kind === 'enroll'">
                {{ row.packageName || courseName(row.courseId) }}，{{ row.totalHours }}{{ row.hoursUnit || '课时' }}×¥{{ row.price }}
                {{ row.discountAmount > 0 ? `，优惠¥${row.discountAmount}` : '' }}
              </template>
              <span v-else>{{ row.content }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="cols.totalAmount" label="应收/应退" width="104" sortable>
            <template #default="{ row }">
              <div class="amt-cell" v-if="row.kind === 'enroll' || row.kind === 'material' || row.kind === 'supplement'"><span class="tag-in">收</span><b>{{ row.totalAmount }}</b></div>
              <div class="amt-cell" v-else-if="row.type === '退费'"><span class="tag-out">退</span><b>-{{ row.totalAmount || row.amount }}</b></div>
              <div class="amt-cell" v-else><span class="tag-in">收</span><b>{{ row.amount }}</b></div>
            </template>
          </el-table-column>
          <el-table-column v-if="cols.paidAmount" label="实收/实退" width="104" sortable>
            <template #default="{ row }">
              <div class="amt-cell" v-if="row.kind === 'enroll' || row.kind === 'material' || row.kind === 'supplement'"><span class="tag-in">收</span><b>{{ row.paidAmount ?? '0.00' }}</b></div>
              <div class="amt-cell" v-else-if="row.type === '退费'"><span class="tag-out">退</span><b>-{{ row.paidAmount ?? '0.00' }}</b></div>
              <div class="amt-cell" v-else><span class="tag-in">收</span><b>{{ row.amount ?? '0.00' }}</b></div>
            </template>
          </el-table-column>
          <el-table-column v-if="cols.due" label="欠费" width="86" sortable :sort-method="(a, b) => rowDue(a) - rowDue(b)">
            <template #default="{ row }">
              <div class="amt-cell"><span></span><b :class="rowDue(row) > 0 ? 'pay-due' : ''">{{ rowDue(row).toFixed(2) }}</b></div>
            </template>
          </el-table-column>
          <el-table-column v-if="cols.consultant" label="咨询顾问" width="88" show-overflow-tooltip>
            <template #default="{ row }">{{ row.consultant || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="cols.handleDate" label="经办日期" width="112" align="center" sortable
                           :sort-method="(a, b) => (a.handleDate || a.createTime || '').localeCompare(b.handleDate || b.createTime || '')">
            <template #default="{ row }"><span class="nowrap">{{ row.handleDate || (row.createTime || '').slice(0, 10) }}</span></template>
          </el-table-column>
          <el-table-column v-if="cols.handler" label="经办人" width="84" show-overflow-tooltip>
            <template #default="{ row }">{{ row.handler || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="cols.remark" label="备注" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="cols.status" label="报名状态" width="82" align="center" sortable
                           :sort-method="(a, b) => (a.status || 0) - (b.status || 0)">
            <template #default="{ row }">
              <el-tag v-if="row.kind === 'enroll'" size="small" :type="{ 1: 'success', 2: 'info', 3: 'danger', 4: 'warning', 5: 'info' }[row.status]" effect="plain">
                {{ { 1: '在读', 2: '已结课', 3: '已退订', 4: '已转课', 5: '已作废' }[row.status] || '-' }}
              </el-tag>
              <span v-else class="fee-sub">—</span>
            </template>
          </el-table-column>
          <el-table-column v-if="cols.payStatus" label="到款状态" width="82" align="center" sortable
                           :sort-method="(a, b) => (a.payStatus || 0) - (b.payStatus || 0) || rowDue(a) - rowDue(b)">
            <template #default="{ row }">
              <span v-if="row.payStatus === 1" class="order-status st-paid">已到款</span>
              <span v-else-if="row.kind === 'enroll' || row.kind === 'material'" class="order-status" :class="rowDue(row) > 0 ? 'st-owing' : 'st-done'">
                {{ rowDue(row) > 0 ? '欠费' : '已完成' }}
              </span>
              <span v-else class="order-status st-done">已完成</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="96" fixed="right" class-name="no-wrap-col">
            <template #default="{ row }">
              <el-dropdown trigger="click" popper-class="more-dropdown"
                           @visible-change="(v) => { row.__moreOpen = v }"
                           @command="(cmd) => onListCommand(cmd, row)">
                <span class="more-trigger" :class="{ open: row.__moreOpen }">
                  <span>更多</span>
                  <el-icon :size="10" class="more-arrow"><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="row.kind === 'enroll'" command="view" class="more-item">
                      <span class="mi mi-green"><el-icon :size="13"><View /></el-icon></span>
                      <span class="mi-txt"><b>查看</b><i>业务凭证详情</i></span>
                    </el-dropdown-item>
                    <el-dropdown-item v-if="row.status !== 5" command="edit" class="more-item">
                      <span class="mi mi-purple"><el-icon :size="13"><EditPen /></el-icon></span>
                      <span class="mi-txt"><b>编辑</b><i>{{ editHintOf(row) }}</i></span>
                    </el-dropdown-item>
                    <el-dropdown-item v-if="row.kind === 'enroll' && row.status !== 5" command="print" class="more-item">
                      <span class="mi mi-blue"><el-icon :size="13"><Printer /></el-icon></span>
                      <span class="mi-txt"><b>打印</b><i>业务凭证打印</i></span>
                    </el-dropdown-item>
                    <el-dropdown-item v-if="row.kind === 'enroll' && row.status !== 5 && row.payStatus !== 1" command="pay" class="more-item">
                      <span class="mi mi-green"><el-icon :size="13"><CircleCheck /></el-icon></span>
                      <span class="mi-txt"><b>确认到款</b><i>标记本单已收款</i></span>
                    </el-dropdown-item>
                    <el-dropdown-item v-else-if="row.kind === 'enroll' && row.status !== 5 && row.payStatus === 1" command="unpay" class="more-item">
                      <span class="mi mi-orange"><el-icon :size="13"><RefreshLeft /></el-icon></span>
                      <span class="mi-txt"><b>取消到款</b><i>撤销到款标记</i></span>
                    </el-dropdown-item>
                    <el-dropdown-item v-if="row.kind === 'enroll' && row.status === 1" command="void" divided class="more-item">
                      <span class="mi mi-red"><el-icon :size="13"><CircleClose /></el-icon></span>
                      <span class="mi-txt"><b>作废订单</b><i>填写作废原因</i></span>
                    </el-dropdown-item>
                    <el-dropdown-item v-if="row.kind === 'enroll'" command="log" class="more-item">
                      <span class="mi mi-orange"><el-icon :size="13"><Document /></el-icon></span>
                      <span class="mi-txt"><b>日志</b><i>操作变更记录</i></span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager-row">
          <el-pagination background layout="total, prev, pager, next" :total="filteredList.length"
                         :page-size="listPageSize" v-model:current-page="listPage" />
        </div>
      </div>
    </template>

    <!-- ===== 填报表单页 ===== -->
    <!-- 学员信息（各办理页共用） -->
    <div v-if="['form', 'recharge', 'transfer', 'refund', 'score', 'material', 'supplement'].includes(mode)" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">学员信息</span>
        <div class="stu-bar">
          <el-select v-model="studentId" filterable remote :remote-method="searchStudents" :loading="searching"
                     placeholder="输入姓名 / 手机号搜索，双击查看全部" clearable style="width: 280px"
                     @change="onPick" @dblclick="loadAllStudents">
            <el-option v-for="s in students" :key="s.studentId" :label="`${s.name}（${s.phone || '无手机号'}）`" :value="s.studentId" />
          </el-select>
          <el-button type="primary" @click="openCreate">创建学员</el-button>
        </div>
      </div>
      <div v-if="picked" class="picked-bar pb-block">
        <div class="pb-row">
          <el-icon color="#155EEF"><User /></el-icon>
          <b>{{ picked.name }}</b>
          <span v-if="picked.grade">{{ picked.grade }}</span>
          <span v-if="picked.school">{{ picked.school }}</span>
          <span v-if="picked.phone">{{ picked.phone }}</span>
          <el-tag v-if="mode === 'recharge' || mode === 'transfer' || mode === 'refund'" size="small" type="warning" effect="light">
            账户余额：¥{{ (Number(balanceAvail) || 0).toFixed(2) }}
          </el-tag>
        </div>
        <!-- 各办理页通用扩展：按校区分行呈现（每行 = 一个校区 + 该校区在读课程，先校区后课程） -->
        <div v-for="g in subInfo.groups" :key="g.campus" class="pb-row pb-campus-row">
          <i class="texttag tt-campus">校</i><b class="pb-campus">{{ g.campus }}</b>
          <span class="pb-dash"></span>
          <i class="texttag tt-course">课</i>
          <el-tooltip v-if="g.courses.length > 2" placement="bottom" effect="light" popper-class="pb-list-tip">
            <span class="pb-ext-hover">{{ pbCoursesText(g) }}</span>
            <template #content>
              <div class="pb-tip-title">{{ g.campus }} 在读课程（{{ g.courses.length }} 门）</div>
              <div v-for="c in g.courses" :key="c" class="pb-tip-row">{{ c }}</div>
            </template>
          </el-tooltip>
          <span v-else>{{ g.courses.join('、') || '—' }}</span>
        </div>
        <div v-if="!subInfo.groups.length" class="pb-row pb-campus-row">
          <i class="texttag tt-campus">校</i><span>暂无在读报读记录</span>
        </div>
      </div>
    </div>

    <!-- 支付信息（办理中心-余额充值：多账户支付记录） -->
    <div v-if="mode === 'recharge'" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">支付信息</span>
        <div class="pay-top">
          <span class="pay-item">充值金额：<b>{{ rechargeTotal }}</b> 元</span>
        </div>
      </div>
      <!-- 支付账户列表（多账户充值记录） -->
      <div class="pay-table">
        <div class="pay-row pay-head">
          <span class="w1">账户名称</span>
          <span class="w2">对方账户</span>
          <span class="w2">流水/支付单号</span>
          <span class="w3">实收（元）</span>
          <span class="w4">操作</span>
        </div>
        <div v-for="(p, i) in recharge.rows" :key="i" class="pay-row">
          <span class="w1">
            <el-select v-model="p.channel" size="small" style="width: 170px">
              <el-option v-for="d in dictStore.options('pay_channel')" :key="d.value" :label="d.label" :value="d.label" />
            </el-select>
          </span>
          <span class="w2"><el-input v-model="p.account" size="small" placeholder="对方账户（选填）" style="width: 180px" /></span>
          <span class="w2"><el-input v-model="p.serial" size="small" placeholder="流水/支付单号（选填）" style="width: 170px" /></span>
          <span class="w3">
            <el-input-number v-model="p.amount" :min="0" :max="999999.99" :precision="2"
                             size="small" controls-position="right" style="width: 110px" placeholder="实收" />
          </span>
          <span class="w4 pay-del" @click="recharge.rows.splice(i, 1)"><el-icon :size="17"><CircleClose /></el-icon></span>
        </div>
        <div class="pay-add" @click="recharge.rows.push({ channel: firstChannel, account: '', serial: '', amount: null })">
          <el-icon :size="14"><CirclePlus /></el-icon> 新增账户
        </div>
      </div>
      <div class="pay-total">
        <span>实收总计：<b>¥{{ rechargeTotal }}</b> 元</span>
        <span class="fee-sub">充值后账户余额</span>
        <b class="re-balance">¥{{ rechargeAfterBalance }}</b>
      </div>
    </div>

    <!-- 经办信息（办理中心-余额充值，独立分区） -->
    <div v-if="mode === 'recharge'" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">经办信息</span></div>
      <div class="handle-box">
        <div class="handle-item">
          <label class="req">销售来源</label>
          <el-select v-model="recharge.source" filterable allow-create clearable size="small"
                     :class="{ 'is-err': rechargeErrs.source }"
                     placeholder="请输入销售来源" style="width: 200px">
            <el-option v-for="s in sourcePresets" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>经办校区</label>
          <el-select v-model="recharge.campus" clearable size="small" placeholder="请选择经办校区" style="width: 200px">
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>经办日期</label>
          <el-date-picker v-model="recharge.date" type="date" value-format="YYYY-MM-DD"
                          size="small" placeholder="请输入经办日期" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label>经办人</label>
          <el-input :model-value="recharge.handler" disabled size="small" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label class="req">咨询顾问</label>
          <el-select v-model="recharge.consultant" filterable clearable size="small"
                     :class="{ 'is-err': rechargeErrs.consultant }"
                     placeholder="请选择咨询顾问" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
        <div class="handle-item re-remark">
          <label>备注</label>
          <el-input v-model="recharge.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="请输入备注（选填）" style="width: 538px; max-width: calc(100% - 64px)" />
        </div>
      </div>
      <el-button type="primary" size="large" class="ep-submit" :loading="recharging"
                 @click="submitRecharge">{{ editingLogId ? '保 存 修 改' : '确 认 充 值' }}</el-button>
    </div>

    <!-- ===== 转课页 ===== -->
    <!-- 转课信息：转出课程 → 转入课程（选择课程 / 直接选班） -->
    <div v-if="mode === 'transfer'" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">转课信息</span></div>
      <div v-if="!picked" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><Switch /></el-icon>
        <p>请先在上方选择学员</p>
        <span>转出在读课时，学费按单价折算；选择转入课程后自动报名并用转出学费抵扣</span>
      </div>
      <div v-else-if="!transferRows.length" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><Switch /></el-icon>
        <p>该学员暂无在读报名单，无法转课</p>
      </div>
      <div v-else class="transfer-flow">
        <div class="tf-side">
          <p>转出课程</p>
          <el-select v-model="transferFilter" clearable size="small" placeholder="选择要转出的课程" style="width: 230px">
            <el-option v-for="r in transferRows" :key="r.id" :value="r.id"
                       :label="`${r.packageName || courseName(r.courseId)}（${r.campus || '未分区'}）`" />
          </el-select>
        </div>
        <div class="tf-arrow"><el-icon :size="22"><Right /></el-icon></div>
        <div class="tf-side">
          <p>转入课程</p>
          <template v-if="transferIn">
            <span class="tf-target">{{ courseName(transferIn.courseId) }}</span>
            <span class="sel-sub">（{{ transferIn.campus || '未分区' }}）</span>
            <span v-if="transferIn.clazzId" class="sel-sub">{{ clazzName(transferIn.clazzId) }}</span>
            <el-icon class="sel-close" title="清除转入课程" @click="transferIn = null"><CircleClose /></el-icon>
          </template>
          <span v-else class="fee-sub">请选择转入课程（可不选，仅转出学费入账户余额）</span>
          <el-button type="primary" size="small" @click="openCourseDialog('transfer')">选择课程</el-button>
          <el-button type="primary" size="small" style="margin-left: 10px" @click="openClazzDialog('transfer')">直接选班</el-button>
        </div>
      </div>
    </div>

    <!-- 转出课程：先在转课信息选择转出课程，此处才展示对应报名单（未选时为空） -->
    <div v-if="mode === 'transfer' && picked && transferRows.length" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">转出课程</span></div>
      <div v-if="!transferFilter" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><Switch /></el-icon>
        <p>请先在上方「转课信息」中选择要转出的课程</p>
        <span>选择后此处展示该课程的在读报名单，逐单填写转出课时与金额</span>
      </div>
      <template v-else>
      <div class="handle-box">
        <div class="handle-item">
          <label>办理类型</label>
          <el-radio-group v-model="transferMode" size="small" @change="onTransferModeChange">
            <el-radio-button value="all">全部转出</el-radio-button>
            <el-radio-button value="part">部分转出</el-radio-button>
          </el-radio-group>
          <span class="fee-sub">转出学费将自动转入学员账户余额（当前余额 ¥{{ (Number(balanceAvail) || 0).toFixed(2) }}）</span>
        </div>
      </div>
      <el-table :data="transferViewRows" size="small" stripe style="margin-top: 10px">
          <el-table-column label="订单号" width="78" align="center">
            <template #default="{ row }">EN{{ row.id }}</template>
          </el-table-column>
          <el-table-column label="课程" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.packageName || courseName(row.courseId) }}<span class="sel-sub">（{{ row.campus || '未分区' }}）</span>
            </template>
          </el-table-column>
          <el-table-column label="经办日期" width="100" align="center">
            <template #default="{ row }"><span class="nowrap">{{ row.handleDate || (row.createTime || '').slice(0, 10) }}</span></template>
          </el-table-column>
          <el-table-column label="购买课时" width="74" align="right">
            <template #default="{ row }">{{ row.totalHours }}</template>
          </el-table-column>
          <el-table-column label="有效期" width="96" align="center">
            <template #default="{ row }">{{ row.validDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="原价" width="86" align="right">
            <template #default="{ row }">¥{{ (Number(row.totalAmount || 0) + Number(row.discountAmount || 0)).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="优惠" width="76" align="right">
            <template #default="{ row }">{{ Number(row.discountAmount) > 0 ? '¥' + row.discountAmount : '-' }}</template>
          </el-table-column>
          <el-table-column label="实收" width="86" align="right">
            <template #default="{ row }">¥{{ row.paidAmount ?? '0.00' }}</template>
          </el-table-column>
          <el-table-column label="剩余课时" width="74" align="right">
            <template #default="{ row }">{{ rowRemaining(row) }}</template>
          </el-table-column>
          <el-table-column label="已用学费" width="86" align="right">
            <template #default="{ row }">¥{{ (rowUnitPaid(row) * (row.usedHours || 0)).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="本次转出课时" width="128" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.out" :min="0" :max="rowRemaining(row)" :precision="0"
                               size="small" controls-position="right" style="width: 96px"
                               :disabled="transferMode === 'all' || rowRemaining(row) <= 0" />
            </template>
          </el-table-column>
          <el-table-column label="本次转出金额" width="102" align="right">
            <template #default="{ row }">
              <b class="sum-money">¥{{ (rowUnitPaid(row) * Number(row.out || 0)).toFixed(2) }}</b>
            </template>
          </el-table-column>
        </el-table>
        <div class="total-bar" style="margin-top: 10px">
          <span>转出学费：<b class="sum-money">¥{{ transferTotal }}</b> 元</span>
          <span class="fee-sub">共 {{ transferCount }} 单转出 {{ transferHours }} 课时，确认后自动转入学员账户余额</span>
        </div>
      </template>
    </div>

    <!-- 转入课程：选择课程/直接选班后出现，仅一门课程或班级；确认转课时自动报名并用转出学费抵扣 -->
    <div v-if="mode === 'transfer' && picked && transferIn" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">转入课程</span></div>
      <div class="sel-card">
        <div class="sel-line">
          <span class="sel-tag tag-course">{{ transferIn.via === 'clazz' ? '班' : '课' }}</span>
          <b class="sel-name">{{ courseName(transferIn.courseId) }}</b>
          <span class="sel-sub">{{ transferIn.campus || '未分区' }}</span>
          <div class="sel-sum">
            <span class="sum-item">共：<b>{{ transferInHours }}</b> 课时</span>
            <span class="sum-item">应收学费：<b class="sum-money">¥{{ transferInDue }}</b></span>
          </div>
          <el-icon class="sel-close" title="移除转入课程" @click="transferIn = null"><CircleClose /></el-icon>
        </div>
        <div class="fee-stand">
          <div class="fee-main">
            <div class="fee-row">
              <span class="fee-label">价格</span>
              <div class="fee-ctrl1"><span class="fee-value">¥{{ transferInSubtotal }}<i>元</i></span></div>
              <template v-if="transferIn.via === 'clazz'">
                <span class="fee-label fee-pair">班级</span>
                <el-select v-model="transferIn.clazzId" clearable size="small"
                           placeholder="选择班级" style="width: 200px">
                  <el-option v-for="c in clazzesOf(transferIn.courseId)" :key="c.id"
                             :label="`${c.name}（${c.capacity}名）`" :value="c.id" />
                </el-select>
              </template>
            </div>
            <div class="fee-row">
              <span class="fee-label">收费模式</span>
              <div class="fee-ctrl1">
                <el-select v-if="transferInPrices.length" v-model="transferIn.priceId" size="small"
                           style="width: 190px" placeholder="选择定价标准">
                  <el-option v-for="p in transferInPrices" :key="p.id" :value="p.id"
                             :label="`${fmt(p.hours)}课时=${fmt(p.totalAmount)}元`" />
                </el-select>
                <span v-else class="fee-sub fee-hint">未配置课时定价，按课程默认价</span>
              </div>
              <template v-if="transferInPrices.length">
                <span class="fee-label fee-pair">数量</span>
                <el-input-number v-model="transferIn.qty" :min="1" :max="99" :precision="0"
                                 size="small" controls-position="right" style="width: 92px" />
              </template>
            </div>
            <div class="fee-row">
              <span class="fee-label">有效期</span>
              <div class="fee-ctrl1">
                <el-checkbox v-model="transferIn.validEnabled" size="small" />
                <el-date-picker v-model="transferIn.validDate" type="date" value-format="YYYY-MM-DD"
                                size="small" :disabled="!transferIn.validEnabled"
                                placeholder="选择有效期" style="width: 170px" />
              </div>
            </div>
            <div class="fee-row">
              <span class="fee-label">备注</span>
              <el-input v-model="transferIn.remark" type="textarea" :rows="2" maxlength="200" resize="none"
                        placeholder="该备注只有内部操作人员可见"
                        style="width: 538px; max-width: calc(100% - 64px)" />
            </div>
          </div>
          <div class="disc-box">
            <div class="disc-head">
              <span class="disc-title">优惠</span>
              <span class="disc-total"><b>{{ transferInDiscount }}</b> 元</span>
              <el-button size="small" @click="transferIn.discounts.push({ type: '学费优惠', amount: null })">新增优惠</el-button>
            </div>
            <div v-for="(d, di) in transferIn.discounts" :key="di" class="disc-row">
              <el-select v-model="d.type" size="small" style="width: 104px">
                <el-option v-for="t in discountTypes" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input-number v-model="d.amount" :min="0" :max="999999.99" :precision="2"
                               size="small" controls-position="right" style="width: 100px" placeholder="金额" />
              <span class="fee-unit">元</span>
              <el-icon class="disc-del" title="删除本条优惠" @click="transferIn.discounts.splice(di, 1)"><CircleClose /></el-icon>
            </div>
            <div v-if="!transferIn.discounts.length" class="fee-sub">暂无优惠</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 支付信息（转课：应收总计=转入应收−转出学费 差价联动；余额+多渠道收银，允许欠费） -->
    <div v-if="mode === 'transfer' && picked && transferIn" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">支付信息</span>
        <div class="pay-top">
          <el-select v-model="pay.campus" placeholder="经办校区" clearable size="small" style="width: 150px">
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <span class="pay-item">应收总计：<b>{{ transferPayDue }}</b> 元</span>
          <span v-if="transferPayArrears > 0" class="pay-due">欠费：{{ transferPayArrears.toFixed(2) }} 元</span>
          <el-tag v-else size="small" type="success" effect="plain">已结清</el-tag>
        </div>
      </div>
      <div class="pay-balance">
        <span class="fee-label">使用余额</span>
        <el-input-number v-model="pay.balance" :min="0" :max="balanceAvail" :precision="2"
                         size="small" controls-position="right" style="width: 120px" />
        <span class="fee-sub">可用：{{ (Number(balanceAvail) || 0).toFixed(2) }} 元（转出学费 ¥{{ transferTotal }} 确认后自动入账）</span>
      </div>
      <div class="pay-table">
        <div class="pay-row pay-head">
          <span class="w1">账户名称</span>
          <span class="w2">对方账户</span>
          <span class="w2">流水/支付单号</span>
          <span class="w3">实收（元）</span>
          <span class="w4">操作</span>
        </div>
        <div v-for="(p, i) in pay.rows" :key="i" class="pay-row">
          <span class="w1">
            <el-select v-model="p.channel" size="small" style="width: 170px">
              <el-option v-for="d in dictStore.options('pay_channel')" :key="d.value" :label="d.label" :value="d.label" />
            </el-select>
          </span>
          <span class="w2"><el-input v-model="p.account" size="small" placeholder="对方账户（选填）" style="width: 180px" /></span>
          <span class="w2"><el-input v-model="p.serial" size="small" placeholder="流水/支付单号（选填）" style="width: 170px" /></span>
          <span class="w3">
            <el-input-number v-model="p.amount" :min="0" :max="999999.99" :precision="2"
                             size="small" controls-position="right" style="width: 110px" placeholder="实收" />
          </span>
          <span class="w4 pay-del" @click="pay.rows.splice(i, 1)"><el-icon :size="17"><CircleClose /></el-icon></span>
        </div>
        <div class="pay-add" @click="pay.rows.push({ channel: firstChannel, account: '', serial: '', amount: null })">
          <el-icon :size="14"><CirclePlus /></el-icon> 新增账户
        </div>
      </div>
      <div class="pay-total">
        <span>实收总计：<b>¥{{ transferReceived }}</b> 元</span>
        <span class="fee-sub">转出抵扣 ¥{{ transferTotal }} ＋ 余额支付 ¥{{ (Number(pay.balance) || 0).toFixed(2) }} ＋ 渠道支付 ¥{{ transferChannelTotal }}</span>
      </div>
    </div>

    <!-- 经办信息（转课，独立分区） -->
    <div v-if="mode === 'transfer' && picked" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">经办信息</span></div>
      <div class="handle-box">
        <div class="handle-item">
          <label class="req">销售来源</label>
          <el-select v-model="handle.source" filterable allow-create clearable size="small"
                     :class="{ 'is-err': handleErrs.source }"
                     placeholder="请输入销售来源" style="width: 200px">
            <el-option v-for="s in sourcePresets" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>经办日期</label>
          <el-date-picker v-model="handle.date" type="date" value-format="YYYY-MM-DD"
                          size="small" placeholder="请输入经办日期" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label>经办人</label>
          <el-input :model-value="handle.handler" disabled size="small" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label class="req">咨询顾问</label>
          <el-select v-model="handle.consultant" filterable clearable size="small"
                     :class="{ 'is-err': handleErrs.consultant }"
                     placeholder="请选择咨询顾问" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
        <div class="handle-item re-remark">
          <label>备注</label>
          <el-input v-model="transfer.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="请输入备注（选填）" style="width: 538px; max-width: calc(100% - 64px)" />
        </div>
      </div>
      <el-button type="primary" size="large" class="ep-submit" :loading="transferring"
                 @click="submitTransfer">确 认 转 课</el-button>
    </div>

    <!-- ===== 退费页 ===== -->
    <!-- 退费信息：选择课程 + 全部/部分退费 + 订单表格（实收折算/按原价双口径），金额转结账户余额 -->
    <div v-if="mode === 'refund'" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">退费信息</span></div>
      <div v-if="!picked" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><RefreshLeft /></el-icon>
        <p>请先在上方选择学员</p>
        <span>退出在读课时，退费金额自动转结到学员账户余额</span>
      </div>
      <div v-else-if="!refundRows.length" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><RefreshLeft /></el-icon>
        <p>该学员暂无在读报名单，无法退费</p>
      </div>
      <template v-else>
        <div class="handle-box">
          <div class="handle-item">
            <label>选择课程</label>
            <el-select v-model="refundFilter" clearable size="small" placeholder="请选择课程" style="width: 230px">
              <el-option v-for="r in refundRows" :key="r.id" :value="r.id"
                         :label="`${r.packageName || courseName(r.courseId)}（${r.campus || '未分区'}）`" />
            </el-select>
          </div>
          <div class="handle-item">
            <label>退费类型</label>
            <el-radio-group v-model="refundMode" size="small" @change="onRefundModeChange">
              <el-radio-button value="all">全部退费</el-radio-button>
              <el-radio-button value="part">部分退费</el-radio-button>
            </el-radio-group>
            <span class="fee-sub">退费金额将自动转结到学员账户余额（当前余额 ¥{{ (Number(balanceAvail) || 0).toFixed(2) }}）</span>
          </div>
        </div>
        <div v-if="!refundFilter" class="ep-empty">
          <el-icon :size="26" color="#c0d3e8"><RefreshLeft /></el-icon>
          <p>请先选择要退费的课程</p>
          <span>选择后此处展示该课程的在读报名单，逐单填写退出课时与金额</span>
        </div>
        <template v-else>
        <el-table :data="refundViewRows" size="small" stripe style="margin-top: 10px">
          <el-table-column label="订单号" width="78" align="center">
            <template #default="{ row }">EN{{ row.id }}</template>
          </el-table-column>
          <el-table-column label="课程" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.packageName || courseName(row.courseId) }}<span class="sel-sub">（{{ row.campus || '未分区' }}）</span>
            </template>
          </el-table-column>
          <el-table-column label="经办日期" width="100" align="center">
            <template #default="{ row }"><span class="nowrap">{{ row.handleDate || (row.createTime || '').slice(0, 10) }}</span></template>
          </el-table-column>
          <el-table-column label="购买课时" width="74" align="right">
            <template #default="{ row }">{{ row.totalHours }}</template>
          </el-table-column>
          <el-table-column label="有效期" width="96" align="center">
            <template #default="{ row }">{{ row.validDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="原价" width="86" align="right">
            <template #default="{ row }">¥{{ (Number(row.totalAmount || 0) + Number(row.discountAmount || 0)).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="优惠" width="76" align="right">
            <template #default="{ row }">{{ Number(row.discountAmount) > 0 ? '¥' + row.discountAmount : '-' }}</template>
          </el-table-column>
          <el-table-column label="实收" width="86" align="right">
            <template #default="{ row }">¥{{ row.paidAmount ?? '0.00' }}</template>
          </el-table-column>
          <el-table-column label="剩余课时" width="74" align="right">
            <template #default="{ row }">{{ rowRemaining(row) }}</template>
          </el-table-column>
          <el-table-column label="已用学费" width="86" align="right">
            <template #default="{ row }">¥{{ (rowUnitPaid(row) * (row.usedHours || 0)).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="按原价退费" width="92" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.byOriginal" size="small" inline-prompt active-text="优惠" inactive-text="实收" />
            </template>
          </el-table-column>
          <el-table-column label="本次退出课时" width="128" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.out" :min="0" :max="rowRemaining(row)" :precision="0"
                               size="small" controls-position="right" style="width: 96px"
                               :disabled="refundMode === 'all' || rowRemaining(row) <= 0" />
            </template>
          </el-table-column>
          <el-table-column label="本次退出金额" width="102" align="right">
            <template #default="{ row }">
              <b class="sum-money">¥{{ rowRefundAmt(row).toFixed(2) }}</b>
            </template>
          </el-table-column>
        </el-table>
        <div class="total-bar" style="margin-top: 10px">
          <span>转结学费：<b class="sum-money">¥{{ refundTotal }}</b> 元</span>
          <span class="fee-sub">共 {{ refundCount }} 单退出 {{ refundHours }} 课时，确认后自动转结到学员账户余额</span>
        </div>
        </template>
      </template>
    </div>

    <!-- 支付信息（退费：应退=转结学费；实退=渠道退款；差额自动转入账户余额） -->
    <div v-if="mode === 'refund' && picked && refundRows.length && refundFilter" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">支付信息</span>
        <div class="pay-top">
          <el-select v-model="pay.campus" placeholder="经办校区" clearable size="small" style="width: 150px">
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <span class="pay-item">应退总计：<b>{{ refundTotal }}</b> 元</span>
        </div>
      </div>
      <div class="pay-table">
        <div class="pay-row pay-head">
          <span class="w1">账户名称</span>
          <span class="w2">对方账户</span>
          <span class="w2">流水/支付单号</span>
          <span class="w3">实退（元）</span>
          <span class="w4">操作</span>
        </div>
        <div v-for="(p, i) in pay.rows" :key="i" class="pay-row">
          <span class="w1">
            <el-select v-model="p.channel" size="small" style="width: 170px">
              <el-option v-for="d in dictStore.options('pay_channel')" :key="d.value" :label="d.label" :value="d.label" />
            </el-select>
          </span>
          <span class="w2"><el-input v-model="p.account" size="small" placeholder="对方账户（选填）" style="width: 180px" /></span>
          <span class="w2"><el-input v-model="p.serial" size="small" placeholder="流水/支付单号（选填）" style="width: 170px" /></span>
          <span class="w3">
            <el-input-number v-model="p.amount" :min="0" :max="999999.99" :precision="2"
                             size="small" controls-position="right" style="width: 110px" placeholder="实退" />
          </span>
          <span class="w4 pay-del" @click="pay.rows.splice(i, 1)"><el-icon :size="17"><CircleClose /></el-icon></span>
        </div>
        <div class="pay-add" @click="pay.rows.push({ channel: firstChannel, account: '', serial: '', amount: null })">
          <el-icon :size="14"><CirclePlus /></el-icon> 新增账户
        </div>
      </div>
      <div class="pay-total">
        <span>实退总计：<b>¥{{ refundPaidTotal }}</b> 元</span>
        <span class="fee-sub">增加余额：¥{{ refundBalanceAmount }} 元（应退 − 实退，确认后自动转入学员账户余额）</span>
      </div>
    </div>

    <!-- 经办信息（退费，独立分区） -->
    <div v-if="mode === 'refund' && picked" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">经办信息</span></div>
      <div class="handle-box">
        <div class="handle-item">
          <label class="req">销售来源</label>
          <el-select v-model="handle.source" filterable allow-create clearable size="small"
                     :class="{ 'is-err': handleErrs.source }"
                     placeholder="请输入销售来源" style="width: 200px">
            <el-option v-for="s in sourcePresets" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>经办日期</label>
          <el-date-picker v-model="handle.date" type="date" value-format="YYYY-MM-DD"
                          size="small" placeholder="请输入经办日期" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label>经办人</label>
          <el-input :model-value="handle.handler" disabled size="small" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label class="req">咨询顾问</label>
          <el-select v-model="handle.consultant" filterable clearable size="small"
                     :class="{ 'is-err': handleErrs.consultant }"
                     placeholder="请选择咨询顾问" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
        <div class="handle-item re-remark">
          <label>备注</label>
          <el-input v-model="refund.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="请输入备注（选填）" style="width: 538px; max-width: calc(100% - 64px)" />
        </div>
      </div>
      <el-button type="primary" size="large" class="ep-submit" :loading="refunding"
                 @click="submitRefund">确 认 退 费</el-button>
    </div>

    <!-- ===== 积分页 ===== -->
    <!-- 积分信息：剩余积分 + 增加积分/积分兑换 -->
    <div v-if="mode === 'score'" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">积分信息</span></div>
      <div v-if="!picked" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><Medal /></el-icon>
        <p>请先在上方选择学员</p>
        <span>为学员增加积分或办理积分兑换</span>
      </div>
      <template v-else>
        <div class="picked-bar" style="margin-bottom: 12px">
          <el-icon color="#dd2590"><Medal /></el-icon>
          <span>账户剩余：</span>
          <b class="score-amount">{{ scoreAvail.toFixed(2) }}</b>
          <span class="fee-unit">分</span>
          <span class="fee-sub">累计增加 {{ scoreSum.earned }} 分 ｜ 累计兑换 {{ scoreSum.used }} 分</span>
        </div>
        <div class="handle-box">
          <div class="handle-item">
            <label>办理类型</label>
            <el-radio-group v-model="score.type" size="small">
              <el-radio-button :value="1">增加积分</el-radio-button>
              <el-radio-button :value="2">积分兑换</el-radio-button>
            </el-radio-group>
          </div>
          <div class="handle-item">
            <label>{{ score.type === 1 ? '增加积分' : '使用积分' }}</label>
            <el-input-number v-model="score.points" :min="1"
                             :max="score.type === 2 ? Math.floor(scoreAvail) : 99999999"
                             :precision="0" size="small" controls-position="right" style="width: 150px" />
            <span class="fee-unit">分</span>
            <span v-if="score.type === 2" class="fee-sub">
              兑换后剩余 {{ Math.max(scoreAvail - Number(score.points || 0), 0).toFixed(0) }} 分
            </span>
          </div>
          <div class="handle-item">
            <label>经办校区</label>
            <el-select v-model="score.campus" clearable size="small" placeholder="请选择经办校区" style="width: 200px">
              <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
            </el-select>
          </div>
        </div>
      </template>
    </div>

    <!-- 经办信息（积分，独立分区） -->
    <div v-if="mode === 'score' && picked" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">经办信息</span></div>
      <div class="handle-box">
        <div class="handle-item">
          <label class="req">销售来源</label>
          <el-select v-model="handle.source" filterable allow-create clearable size="small"
                     :class="{ 'is-err': handleErrs.source }"
                     placeholder="请输入销售来源" style="width: 200px">
            <el-option v-for="s in sourcePresets" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>经办日期</label>
          <el-date-picker v-model="handle.date" type="date" value-format="YYYY-MM-DD"
                          size="small" placeholder="请输入经办日期" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label>经办人</label>
          <el-input :model-value="handle.handler" disabled size="small" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label class="req">咨询顾问</label>
          <el-select v-model="handle.consultant" filterable clearable size="small"
                     :class="{ 'is-err': handleErrs.consultant }"
                     placeholder="请选择咨询顾问" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
        <div class="handle-item re-remark">
          <label>备注</label>
          <el-input v-model="score.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="请输入备注（选填）" style="width: 538px; max-width: calc(100% - 64px)" />
        </div>
      </div>
      <el-button type="primary" size="large" class="ep-submit" :loading="scoring"
                 @click="submitScore">{{ score.type === 1 ? '确认增加积分' : '确认积分兑换' }}</el-button>
    </div>

    <!-- ===== 教材杂费页 ===== -->
    <!-- 教材杂费信息：选择教材杂费 → 明细（领用/价格/数量/总计/删除）+ 小计 -->
    <div v-if="mode === 'material'" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">教材杂费信息</span>
        <el-button type="primary" size="small" @click="openMaterialDialog">选择教材杂费</el-button>
      </div>
      <div v-if="!picked" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><Reading /></el-icon>
        <p>请先在上方选择学员</p>
        <span>选择教材/杂费项目，逐项填写价格与数量后办理收款</span>
      </div>
      <div v-else-if="!materialRows.length" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><Reading /></el-icon>
        <p>请点击「选择教材杂费」添加项目</p>
        <span>支持多项目，价格与数量可逐项调整</span>
      </div>
      <template v-else>
        <el-table :data="materialRows" size="small" stripe>
          <el-table-column label="领用状态" width="80" align="center">
            <template #default="{ row }"><el-checkbox v-model="row.got" size="small" /></template>
          </el-table-column>
          <el-table-column label="教材杂费" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.name }}</template>
          </el-table-column>
          <el-table-column label="价格" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.price" :min="0" :max="999999.99" :precision="2"
                               size="small" controls-position="right" style="width: 110px" />
            </template>
          </el-table-column>
          <el-table-column label="购买数量" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.qty" :min="1" :max="9999" :precision="0"
                               size="small" controls-position="right" style="width: 110px" />
            </template>
          </el-table-column>
          <el-table-column label="总计" width="100" align="right">
            <template #default="{ row }">
              <b class="sum-money">¥{{ (Number(row.price || 0) * Number(row.qty || 1)).toFixed(2) }}</b>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-icon class="sel-close" title="移除" @click="materialRows.splice($index, 1)"><CircleClose /></el-icon>
            </template>
          </el-table-column>
        </el-table>
        <div class="total-bar" style="margin-top: 10px">
          <span>小计：<b class="sum-money">¥{{ materialTotal }}</b> 元</span>
          <span class="fee-sub">共 {{ materialRows.length }} 项{{ materialGotCount ? `，领用 ${materialGotCount} 项` : '' }}</span>
        </div>
      </template>
    </div>

    <!-- 支付信息（教材杂费：渠道收银，允许欠费） -->
    <div v-if="mode === 'material' && picked && materialRows.length" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">支付信息</span>
        <div class="pay-top">
          <el-select v-model="pay.campus" placeholder="经办校区" clearable size="small" style="width: 150px">
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <span class="pay-item">应收总计：<b>{{ materialTotal }}</b> 元</span>
          <span v-if="materialArrears > 0" class="pay-due">欠费：{{ materialArrears.toFixed(2) }} 元</span>
          <el-tag v-else size="small" type="success" effect="plain">已结清</el-tag>
        </div>
      </div>
      <div class="pay-table">
        <div class="pay-row pay-head">
          <span class="w1">账户名称</span>
          <span class="w2">对方账户</span>
          <span class="w2">流水/支付单号</span>
          <span class="w3">实收（元）</span>
          <span class="w4">操作</span>
        </div>
        <div v-for="(p, i) in pay.rows" :key="i" class="pay-row">
          <span class="w1">
            <el-select v-model="p.channel" size="small" style="width: 170px">
              <el-option v-for="d in dictStore.options('pay_channel')" :key="d.value" :label="d.label" :value="d.label" />
            </el-select>
          </span>
          <span class="w2"><el-input v-model="p.account" size="small" placeholder="对方账户（选填）" style="width: 180px" /></span>
          <span class="w2"><el-input v-model="p.serial" size="small" placeholder="流水/支付单号（选填）" style="width: 170px" /></span>
          <span class="w3">
            <el-input-number v-model="p.amount" :min="0" :max="999999.99" :precision="2"
                             size="small" controls-position="right" style="width: 110px" placeholder="实收" />
          </span>
          <span class="w4 pay-del" @click="pay.rows.splice(i, 1)"><el-icon :size="17"><CircleClose /></el-icon></span>
        </div>
        <div class="pay-add" @click="pay.rows.push({ channel: firstChannel, account: '', serial: '', amount: null })">
          <el-icon :size="14"><CirclePlus /></el-icon> 新增账户
        </div>
      </div>
      <div class="pay-total">
        <span>实收总计：<b>¥{{ materialPaidTotal }}</b> 元</span>
        <span class="fee-sub">渠道支付 ¥{{ materialPaidTotal }}（小计 ¥{{ materialTotal }}）</span>
      </div>
    </div>

    <!-- 经办信息（教材杂费，独立分区） -->
    <div v-if="mode === 'material' && picked" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">经办信息</span></div>
      <div class="handle-box">
        <div class="handle-item">
          <label class="req">销售来源</label>
          <el-select v-model="handle.source" filterable allow-create clearable size="small"
                     :class="{ 'is-err': handleErrs.source }"
                     placeholder="请输入销售来源" style="width: 200px">
            <el-option v-for="s in sourcePresets" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>经办日期</label>
          <el-date-picker v-model="handle.date" type="date" value-format="YYYY-MM-DD"
                          size="small" placeholder="请输入经办日期" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label>经办人</label>
          <el-input :model-value="handle.handler" disabled size="small" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label class="req">咨询顾问</label>
          <el-select v-model="handle.consultant" filterable clearable size="small"
                     :class="{ 'is-err': handleErrs.consultant }"
                     placeholder="请选择咨询顾问" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
        <div class="handle-item re-remark">
          <label>备注</label>
          <el-input v-model="material.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="请输入备注（选填）" style="width: 538px; max-width: calc(100% - 64px)" />
        </div>
      </div>
      <el-button type="primary" size="large" class="ep-submit" :loading="materialing"
                 @click="submitMaterial">确 认 办 理</el-button>
    </div>

    <!-- ===== 补费页 ===== -->
    <!-- 补费信息：学员欠费单列表，逐单填写本次补费金额 -->
    <div v-if="mode === 'supplement'" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">补费信息</span></div>
      <div v-if="!picked" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><Money /></el-icon>
        <p>请先在上方选择学员</p>
        <span>列出该学员的欠费报名单与教材杂费单，逐单补收</span>
      </div>
      <div v-else-if="!supplementRows.length" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><Money /></el-icon>
        <p>该学员暂无欠费记录，无需补费</p>
      </div>
      <template v-else>
        <el-table :data="supplementRows" size="small" stripe>
          <el-table-column label="订单号" width="80" align="center">
            <template #default="{ row }">{{ row.rid }}</template>
          </el-table-column>
          <el-table-column label="订单类型" width="86" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.kind === 'enroll' ? '' : 'success'">{{ row.typeName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="经办人" width="90" show-overflow-tooltip>
            <template #default="{ row }">{{ row.handler || '-' }}</template>
          </el-table-column>
          <el-table-column label="经办日期" width="100" align="center">
            <template #default="{ row }"><span class="nowrap">{{ row.handleDate || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="经办校区" width="96" show-overflow-tooltip>
            <template #default="{ row }">{{ row.campus || '-' }}</template>
          </el-table-column>
          <el-table-column label="欠费项目" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.kind === 'enroll' ? (row.packageName || courseName(row.courseId)) : row.name }}
            </template>
          </el-table-column>
          <el-table-column label="欠费金额" width="96" align="right">
            <template #default="{ row }">
              <b class="pay-due">¥{{ Number(row.arrears).toFixed(2) }}</b>
            </template>
          </el-table-column>
          <el-table-column label="本次补费金额" width="140" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.amount" :min="0" :max="Number(row.arrears)" :precision="2"
                               size="small" controls-position="right" style="width: 116px" placeholder="金额" />
            </template>
          </el-table-column>
        </el-table>
        <div class="total-bar" style="margin-top: 10px">
          <span>补费金额：<b class="sum-money">¥{{ supplementTotal }}</b> 元</span>
          <span class="fee-sub">共 {{ supplementCount }} 单，剩余欠费 ¥{{ supplementLeftArrears }}</span>
        </div>
      </template>
    </div>

    <!-- 支付信息（补费：渠道收银，允许部分收款继续挂欠费） -->
    <div v-if="mode === 'supplement' && picked && supplementRows.length" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">支付信息</span>
        <div class="pay-top">
          <el-select v-model="pay.campus" placeholder="经办校区" clearable size="small" style="width: 150px">
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <span class="pay-item">补费金额合计：<b>{{ supplementTotal }}</b> 元</span>
          <span v-if="supplementArrears > 0" class="pay-due">欠费：{{ supplementArrears.toFixed(2) }} 元</span>
          <el-tag v-else size="small" type="success" effect="plain">已结清</el-tag>
        </div>
      </div>
      <div class="pay-table">
        <div class="pay-row pay-head">
          <span class="w1">账户名称</span>
          <span class="w2">对方账户</span>
          <span class="w2">流水/支付单号</span>
          <span class="w3">实收（元）</span>
          <span class="w4">操作</span>
        </div>
        <div v-for="(p, i) in pay.rows" :key="i" class="pay-row">
          <span class="w1">
            <el-select v-model="p.channel" size="small" style="width: 170px">
              <el-option v-for="d in dictStore.options('pay_channel')" :key="d.value" :label="d.label" :value="d.label" />
            </el-select>
          </span>
          <span class="w2"><el-input v-model="p.account" size="small" placeholder="对方账户（选填）" style="width: 180px" /></span>
          <span class="w2"><el-input v-model="p.serial" size="small" placeholder="流水/支付单号（选填）" style="width: 170px" /></span>
          <span class="w3">
            <el-input-number v-model="p.amount" :min="0" :max="999999.99" :precision="2"
                             size="small" controls-position="right" style="width: 110px" placeholder="实收" />
          </span>
          <span class="w4 pay-del" @click="pay.rows.splice(i, 1)"><el-icon :size="17"><CircleClose /></el-icon></span>
        </div>
        <div class="pay-add" @click="pay.rows.push({ channel: firstChannel, account: '', serial: '', amount: null })">
          <el-icon :size="14"><CirclePlus /></el-icon> 新增账户
        </div>
      </div>
      <div class="pay-total">
        <span>实收总计：<b>¥{{ supplementPaidTotal }}</b> 元</span>
        <span class="fee-sub">补费 ¥{{ supplementTotal }} − 实收 ¥{{ supplementPaidTotal }} ＝ 仍欠 ¥{{ supplementArrears.toFixed(2) }}</span>
      </div>
    </div>

    <!-- 经办信息（补费，独立分区） -->
    <div v-if="mode === 'supplement' && picked" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">经办信息</span></div>
      <div class="handle-box">
        <div class="handle-item">
          <label class="req">销售来源</label>
          <el-select v-model="handle.source" filterable allow-create clearable size="small"
                     :class="{ 'is-err': handleErrs.source }"
                     placeholder="请输入销售来源" style="width: 200px">
            <el-option v-for="s in sourcePresets" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>经办日期</label>
          <el-date-picker v-model="handle.date" type="date" value-format="YYYY-MM-DD"
                          size="small" placeholder="请输入经办日期" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label>经办人</label>
          <el-input :model-value="handle.handler" disabled size="small" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label class="req">咨询顾问</label>
          <el-select v-model="handle.consultant" filterable clearable size="small"
                     :class="{ 'is-err': handleErrs.consultant }"
                     placeholder="请选择咨询顾问" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
        <div class="handle-item re-remark">
          <label>备注</label>
          <el-input v-model="supplement.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="请输入备注（选填）" style="width: 538px; max-width: calc(100% - 64px)" />
        </div>
      </div>
      <el-button type="primary" size="large" class="ep-submit" :loading="supplementing"
                 @click="submitSupplement">确 认 补 费</el-button>
    </div>

    <!-- 选择教材杂费弹窗：多选 + 快捷新增项目 -->
    <el-dialog v-model="materialDialog" title="选择教材杂费" width="640px" append-to-body>
      <div class="dlg-toolbar">
        <el-input v-model="materialNew.name" placeholder="新项目名称" size="small" style="width: 180px" />
        <el-input-number v-model="materialNew.price" :min="0" :precision="2" size="small"
                         controls-position="right" style="width: 110px" placeholder="价格" />
        <span class="fee-unit">元</span>
        <el-button type="primary" size="small" :loading="materialSaving" @click="saveMaterialItem">新增项目</el-button>
        <span class="fee-sub">新增后自动出现在列表，可勾选加入办理</span>
      </div>
      <el-table ref="materialTableRef" :data="materialFiltered" height="320" size="small" stripe
                row-key="id" @selection-change="(rows) => (materialSel = rows.map((r) => r.id))">
        <el-table-column type="selection" width="44" reserve-selection />
        <el-table-column prop="name" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="单价" width="110" align="right">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row }">
            <el-icon class="sel-close" title="停用" @click="disableMaterialItem(row)"><CircleClose /></el-icon>
          </template>
        </el-table-column>
      </el-table>
      <div class="picked-clazz-bar">
        <span class="fee-sub">已选择 <b style="color: #155EEF">{{ materialSel.length }}</b> 项</span>
      </div>
      <template #footer>
        <el-button @click="materialDialog = false">取 消</el-button>
        <el-button type="primary" @click="confirmMaterialDialog">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 报名信息：可多选课程/班级，多卡片 -->
    <div v-if="mode === 'form'" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">报名信息</span>
        <div class="stu-bar">
          <el-button type="primary" size="small" @click="openCourseDialog()">选择课程</el-button>
          <el-button type="primary" size="small" @click="openClazzDialog()">选择班级</el-button>
        </div>
      </div>

      <div v-if="!cards.length" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><Plus /></el-icon>
        <p>{{ picked ? '请点击「选择课程」或「选择班级」开始办理报名' : '请先在上方「学员信息」中选择学员' }}</p>
        <span>支持一次选择多门课程/多个班级，逐卡配置后批量订阅</span>
      </div>

      <!-- 课程卡片（按校区分组：一卡一校区一条报课信息；套餐订阅单独成组） -->
      <div v-for="grp in campusGroups" :key="grp.name" class="campus-group">
        <div class="campus-group-head">
          <span class="sel-tag" :class="grp.name === '套餐订阅' ? 'tag-course' : 'campus-tag'">{{
            grp.name === '套餐订阅' ? '套' : '校' }}</span>
          <b class="campus-group-name">{{ grp.name }}</b>
          <span class="fee-sub">（{{ grp.cards.length }} 门课程）</span>
          <span class="fee-sub" style="margin-left: auto">优惠总计：<b style="color: #f79009">¥{{ groupDiscount(grp) }}</b></span>
        </div>
        <div v-for="card in grp.cards" :key="card.__editingId || (card.courseId + '@' + (card.campus || ''))" class="sel-card">
        <div class="sel-line">
          <span class="sel-tag tag-course">{{ card.courseId ? '课' : '套' }}</span>
          <b class="sel-name">{{ cardName(card) }}</b>
          <span v-if="card.courseId" class="sel-sub">{{ card.campus || '-' }}</span>
          <div class="sel-sum">
            <span class="sum-item">共：<b>{{ cardHours(card) }}</b> {{ priceUnit(card) }}</span>
            <span class="sum-item">课程价格合计：<b>¥{{ cardSubtotal(card) }}</b></span>
            <span class="sum-item">优惠合计：<b>¥{{ cardDiscount(card) }}</b></span>
            <span class="sum-item">实收：<b class="sum-money">¥{{ cardFinal(card) }}</b></span>
          </div>
          <span class="origin-seal" :class="card.origin === 'clazz' ? 'seal-clazz' : 'seal-course'"
                :title="card.origin === 'clazz' ? '通过「选择班级」加入' : '通过「选择课程」加入'">
            <svg viewBox="0 0 32 32" width="26" height="26" aria-hidden="true">
              <rect x="1" y="1" width="30" height="30" rx="7" fill="currentColor" />
              <text x="16" y="23" text-anchor="middle" font-size="19" font-weight="700"
                    font-family="KaiTi, STKaiti, 'Kaiti SC', 'FZKai-Z03S', serif" fill="#fff">{{
                card.origin === 'clazz' ? '班' : '课' }}</text>
            </svg>
          </span>
          <el-icon class="sel-close" title="移除课程" @click="removeCard(card)"><CircleClose /></el-icon>
        </div>
        <div class="fee-stand">
          <div class="fee-main">
            <div class="fee-row">
              <span class="fee-label">价格</span>
              <div class="fee-ctrl1"><span class="fee-value">¥{{ cardSubtotal(card) }}<i>元</i></span></div>
              <template v-if="card.courseId">
                <span class="fee-label fee-pair">班级</span>
                <el-select v-model="card.clazzId" filterable clearable size="small"
                           placeholder="选择班级（可不选）" style="width: 200px">
                  <el-option v-for="c in clazzesOf(card.courseId)" :key="c.id"
                             :label="`${c.name}（${c.capacity}名）`" :value="c.id" />
                </el-select>
              </template>
            </div>
            <div class="fee-row" v-if="card.courseId">
              <span class="fee-label">收费模式</span>
              <div class="fee-ctrl1">
                <el-select v-if="card.priceRows.length" v-model="card.priceId" size="small"
                           style="width: 190px" placeholder="选择定价标准" @change="card.customized = true">
                  <el-option v-for="p in card.priceRows" :key="p.id" :value="p.id" :label="priceLabel(p)" />
                </el-select>
                <span v-else class="fee-sub fee-hint" title="该课程未配置定价标准，按课程默认价">未配置定价标准，按默认价</span>
              </div>
              <span class="fee-label fee-pair">数量</span>
              <el-input-number v-model="card.qty" :min="1" :max="99" :precision="0" size="small"
                               controls-position="right" style="width: 92px" @change="onCardQtyChange(card)" />
            </div>
            <div class="fee-row" v-else>
              <span class="fee-sub">套餐订阅：课时与金额以原报名记录为准</span>
            </div>
            <div class="fee-row">
              <span class="fee-label">有效期</span>
              <div class="fee-ctrl1">
                <el-checkbox v-model="card.validEnabled" size="small" />
                <el-date-picker v-model="card.validDate" type="date" value-format="YYYY-MM-DD"
                                size="small" :disabled="!card.validEnabled"
                                placeholder="选择有效期" style="width: 170px" />
              </div>
              <span class="fee-label fee-pair">授课老师</span>
              <el-select v-model="card.teacherId" filterable clearable size="small"
                         placeholder="可后指派" style="width: 200px">
                <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </div>
            <div class="fee-row">
              <span class="fee-label">备注</span>
              <el-input v-model="card.remark" type="textarea" :rows="2" maxlength="200" resize="none"
                        placeholder="该备注只有内部操作人员可见"
                        style="width: 538px; max-width: calc(100% - 64px)" />
            </div>
          </div>
          <div class="disc-box">
            <div class="disc-head">
              <span class="disc-title">优惠</span>
              <span class="disc-total"><b>{{ cardDiscount(card) }}</b> 元</span>
              <el-button size="small" @click="card.discounts.push({ type: '学费优惠', amount: null })">新增优惠</el-button>
            </div>
            <div v-for="(d, di) in card.discounts" :key="di" class="disc-row">
              <el-select v-model="d.type" size="small" style="width: 104px">
                <el-option v-for="t in discountTypes" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input-number v-model="d.amount" :min="0" :max="999999.99" :precision="2"
                               size="small" controls-position="right" style="width: 100px" placeholder="金额" />
              <span class="fee-unit">元</span>
              <el-icon class="disc-del" title="删除本条优惠" @click="card.discounts.splice(di, 1)"><CircleClose /></el-icon>
            </div>
            <div v-if="!card.discounts.length" class="fee-sub">暂无优惠</div>
          </div>
        </div>
      </div>
      </div>

      <!-- 合计与提交 -->
      <template v-if="cards.length">
        <div class="total-bar">
          <span>共 <b>{{ totalHours }}</b> 课时</span>
          <span>合计：<b class="sum-money">¥{{ totalAmount }}</b> 元</span>
          <span class="fee-sub">（共 {{ cards.length }} 门课程）</span>
        </div>
      </template>
    </div>

    <!-- 支付信息（应收/实收/欠费联动；选中学员后出现——先选学员，再逐区办理） -->
    <div v-if="mode === 'form' && picked" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">支付信息</span>
        <div class="pay-top">
          <el-select v-model="pay.campus" placeholder="经办校区" clearable size="small" style="width: 150px">
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <span class="pay-item">应收总计：<b>{{ totalAmount }}</b> 元</span>
          <span v-if="arrearsNum > 0" class="pay-due">欠费：{{ arrears }} 元</span>
          <el-tag v-else size="small" type="success" effect="plain">已结清</el-tag>
        </div>
      </div>
      <div class="pay-balance">
        <span class="fee-label">使用余额</span>
        <el-input-number v-model="pay.balance" :min="0" :max="balanceAvail" :precision="2"
                         size="small" controls-position="right" style="width: 120px" />
        <span class="fee-sub">可用：{{ (balanceAvail - Number(pay.balance || 0)).toFixed(2) }} 元</span>
        <el-tag v-if="overpay > 0" size="small" type="danger" effect="light" class="credit-tag">
          实收超出应收 ¥{{ overpay.toFixed(2) }}，请调整支付金额
        </el-tag>
      </div>
      <!-- 支付账户列表 -->
      <div class="pay-table" :class="{ 'is-err-box': payErr }">
        <div class="pay-row pay-head">
          <span class="w1">账户名称</span>
          <span class="w2">对方账户</span>
          <span class="w2">流水/支付单号</span>
          <span class="w3">实收（元）</span>
          <span class="w4">操作</span>
        </div>
        <div v-for="(p, i) in pay.rows" :key="i" class="pay-row">
          <span class="w1">
            <el-select v-model="p.channel" size="small" style="width: 170px">
              <el-option v-for="d in dictStore.options('pay_channel')" :key="d.value" :label="d.label" :value="d.label" />
            </el-select>
          </span>
          <span class="w2"><el-input v-model="p.account" size="small" placeholder="对方账户（选填）" style="width: 180px" /></span>
          <span class="w2"><el-input v-model="p.serial" size="small" placeholder="流水/支付单号（选填）" style="width: 170px" /></span>
          <span class="w3">
            <el-input-number v-model="p.amount" :min="0" :max="999999.99" :precision="2"
                             size="small" controls-position="right" style="width: 110px" placeholder="实收" />
          </span>
          <span class="w4 pay-del" @click="pay.rows.splice(i, 1)"><el-icon :size="17"><CircleClose /></el-icon></span>
        </div>
        <div class="pay-add" @click="pay.rows.push({ channel: firstChannel, account: '', serial: '', amount: null })">
          <el-icon :size="14"><CirclePlus /></el-icon> 新增账户
        </div>
      </div>
      <div class="pay-total">
        <span>实收总计：<b>¥{{ receivedTotal }}</b> 元</span>
        <span class="fee-sub">余额支付 ¥{{ (Number(pay.balance) || 0).toFixed(2) }} ＋ 渠道支付 ¥{{ channelTotal }}</span>
      </div>
      <!-- 经办信息 -->
      <div class="handle-box">
        <div class="handle-item">
          <label class="req">销售来源</label>
          <el-select v-model="handle.source" filterable allow-create clearable size="small"
                     :class="{ 'is-err': handleErrs.source }"
                     placeholder="请输入销售来源" style="width: 200px">
            <el-option v-for="s in sourcePresets" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>经办日期</label>
          <el-date-picker v-model="handle.date" type="date" value-format="YYYY-MM-DD"
                          size="small" placeholder="请输入经办日期" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label>经办人</label>
          <el-input :model-value="handle.handler" disabled size="small" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label class="req">咨询顾问</label>
          <el-select v-model="handle.consultant" filterable clearable size="small"
                     :class="{ 'is-err': handleErrs.consultant }"
                     placeholder="请选择咨询顾问" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
      </div>
      <el-button type="primary" size="large" class="ep-submit" :loading="saving"
                 @click="editingEnrollId ? saveEdit() : subscribeAll()">{{ editingEnrollId ? '保 存 修 改' : '确 认 订 阅' }}</el-button>
    </div>


    <!-- 业务凭证（查看/打印）：表格化版式 -->
    <el-dialog v-model="voucherVisible" title="业务凭证" width="760px" append-to-body>
      <div v-if="viewRow" class="voucher">
        <div class="v-top">智教云课 KaiEdu 业务凭证</div>
        <table class="vt">
          <tbody>
            <tr>
              <td class="lbl">学员姓名</td><td>{{ stuName(viewRow.studentId) }}</td>
              <td class="lbl">订单类型</td><td>报名</td>
              <td class="lbl">经办日期</td><td>{{ viewRow.handleDate || (viewRow.createTime || '').slice(0, 10) }}</td>
              <td class="lbl">订单号</td><td>EN{{ viewRow.id }}</td>
            </tr>
          </tbody>
        </table>
        <table class="vt vt-body">
          <thead>
            <tr>
              <th style="width:19%">交易内容</th><th style="width:17%">班级信息</th><th style="width:16%">价格</th>
              <th style="width:7%">数量</th><th style="width:11%">原价</th><th style="width:12%">优惠</th><th style="width:11%">应收小计</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td class="tl">{{ viewRow.packageName || courseName(viewRow.courseId) }}</td>
              <td class="tl">{{ clazzName(viewRow.clazzId) }}</td>
              <td>{{ viewRow.totalHours }}课时=¥{{ viewRow.price }}</td>
              <td>1</td>
              <td>¥{{ (Number(viewRow.totalAmount || 0) + Number(viewRow.discountAmount || 0)).toFixed(2) }}</td>
              <td>{{ viewRow.discountAmount > 0 ? `¥${viewRow.discountAmount}` : '—' }}</td>
              <td><b>¥{{ viewRow.totalAmount }}</b></td>
            </tr>
            <tr><td colspan="7" class="tl remark">备注：{{ viewRow.remark || '' }}</td></tr>
          </tbody>
        </table>
        <table class="vt vt-total">
          <tbody>
            <tr>
              <td class="lbl">应收总计</td><td><b>¥{{ viewRow.totalAmount }}</b></td>
              <td class="lbl">实收总计</td><td><b>¥{{ viewRow.paidAmount ?? '0.00' }}</b></td>
              <td class="lbl">使用余额</td><td>¥0.00</td>
              <td class="lbl">欠费</td><td><b>¥{{ rowDue(viewRow).toFixed(2) }}</b></td>
            </tr>
            <tr>
              <td class="lbl">收款方式</td><td colspan="7" class="tl">合计实收 ¥{{ viewRow.paidAmount ?? '0.00' }}</td>
            </tr>
            <tr>
              <td class="lbl">打印时间</td><td colspan="3">{{ new Date().toLocaleString('zh-CN') }}</td>
              <td class="lbl">经办人</td><td colspan="3">{{ viewRow.handler || '-' }}</td>
            </tr>
            <tr class="sign-row">
              <td class="lbl">经办签名</td><td colspan="3">&nbsp;</td>
              <td class="lbl">客户签名</td><td colspan="3">&nbsp;</td>
            </tr>
          </tbody>
        </table>
        <div class="v-note">开课三次后不予办理退费。请您保管好您的收据，丢失不予补办。</div>
      </div>
      <template #footer>
        <el-button @click="voucherVisible = false">关 闭</el-button>
        <el-button type="primary" @click="printOrder(viewRow)">打 印</el-button>
      </template>
    </el-dialog>

    <!-- 操作日志 -->
    <el-dialog v-model="logVisible" :title="`操作日志 — 订单 EN${logRow?.id || ''}`" width="860px" append-to-body>
      <div v-if="logRow" class="log-head">
        <span class="log-title">学员：<b>{{ stuName(logRow.studentId) }}</b></span>
        <el-tag size="small" type="primary" effect="plain" style="margin-left: 8px">报名</el-tag>
      </div>
      <div class="rec-summary" style="margin: 8px 0">当前结果：共计 <b>{{ logList.length }}</b> 条数据</div>
      <el-table :data="logList" size="small" stripe v-loading="logLoading" max-height="360">
        <el-table-column type="index" label="序号" width="56" align="center" />
        <el-table-column label="操作内容" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-icon :size="13" class="log-row-ico"
                     :style="{ color: row.action === 'CREATE' ? '#12b76a' : '#f79009' }"><Document /></el-icon>{{ row.content }}
          </template>
        </el-table-column>        <el-table-column label="应收" width="90" align="right">
          <template #default="{ row }"><span class="tag-in">收</span> {{ row.totalAmount ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column label="实收" width="90" align="right">
          <template #default="{ row }"><span class="tag-in">收</span> {{ row.paidAmount ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="payInfo" label="支付信息" min-width="140" show-overflow-tooltip />
        <el-table-column label="余额变动" width="80" align="right">
          <template #default="{ row }">+{{ row.balanceChange ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="修改人" width="80" show-overflow-tooltip />
        <el-table-column label="修改时间" width="140" align="center">
          <template #default="{ row }">{{ row.createTime }}</template>
        </el-table-column>
        <el-table-column label="操作类型" width="72" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.action === 'CREATE' ? 'success' : 'warning'" effect="plain">
              {{ row.action === 'CREATE' ? '新增' : row.action === 'TRANSFER' ? '转课'
                : row.action === 'REFUND' ? '退费' : row.action === 'SUPPLEMENT' ? '补费' : '修改' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 转课/退费流水：经办信息编辑（结算结果不可逆，仅经办信息可改） -->
    <el-dialog v-model="logEditVisible" :title="`编辑经办信息 — ${logEditRow?.rid || ''}`" width="600px" append-to-body>
      <div v-if="logEditRow" class="picked-bar" style="margin-bottom: 10px">
        <el-tag size="small" :type="{ 转课: 'warning', 退费: 'danger', 积分: 'info' }[logEditRow.type] || 'danger'" effect="plain">{{ logEditRow.type }}</el-tag>
        <b>{{ stuName(logEditRow.studentId) }}</b>
        <span>金额 ¥{{ logEditRow.amount }}</span>
        <span class="fee-sub" style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ logEditRow.content }}</span>
      </div>
      <div class="handle-box" style="margin-top: 0">
        <div class="handle-item">
          <label class="req">销售来源</label>
          <el-select v-model="logEditForm.source" filterable allow-create clearable size="small"
                     :class="{ 'is-err': handleErrs.source }"
                     placeholder="请输入销售来源" style="width: 200px">
            <el-option v-for="s in sourcePresets" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>经办校区</label>
          <el-select v-model="logEditForm.campus" clearable size="small" placeholder="请选择经办校区" style="width: 200px">
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>经办日期</label>
          <el-date-picker v-model="logEditForm.date" type="date" value-format="YYYY-MM-DD"
                          size="small" placeholder="请输入经办日期" style="width: 200px" />
        </div>
        <div class="handle-item">
          <label class="req">咨询顾问</label>
          <el-select v-model="logEditForm.consultant" filterable clearable size="small"
                     :class="{ 'is-err': handleErrs.consultant }"
                     placeholder="请选择咨询顾问" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
      </div>
      <template #footer>
        <el-button @click="logEditVisible = false">取 消</el-button>
        <el-button type="primary" :loading="logEditSaving" @click="saveLogEdit()">保 存</el-button>
      </template>
    </el-dialog>

    <!-- 选择课程弹窗：checkbox 多选 -->
    <el-dialog v-model="courseDialog" :title="coursePurpose === 'transfer' ? '选择课程（转入·单选）' : '选择课程'" width="880px" append-to-body>
      <div class="dlg-toolbar">
        <el-select v-model="dlgCampus" placeholder="选择校区" size="default" style="width: 170px">
          <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-input v-model="courseKw" placeholder="请输入课程名称关键字搜索" clearable
                  style="width: 240px" :prefix-icon="Search" />
        <el-select v-model="dlgTeachMode" placeholder="授课模式" clearable size="default" style="width: 110px">
          <el-option v-for="d in dictStore.options('class_type')" :key="d.value" :label="d.label" :value="Number(d.value)" />
        </el-select>
        <el-select v-model="dlgFeeMode" placeholder="收费模式" clearable size="default" style="width: 110px">
          <el-option label="按课时" value="hour" /><el-option label="按时间" value="time" />
        </el-select>
        <el-select v-model="dlgCategory" placeholder="课程类别" clearable size="default" style="width: 110px">
          <el-option v-for="d in dictStore.options('course_category')" :key="d.value" :label="d.label" :value="Number(d.value)" />
        </el-select>
      </div>
      <el-table ref="courseTableRef" :data="courseFiltered" height="360" size="small" stripe
                row-key="id" @selection-change="onCourseSelChange">
        <el-table-column type="selection" width="44" :selectable="canPickCourse" />
        <el-table-column label="校区" width="110" show-overflow-tooltip>
          <template #default>{{ dlgCampus }}</template>
        </el-table-column>
        <el-table-column prop="name" label="课程名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="课程类别" width="86">
          <template #default="{ row }">{{ row.category ? dictStore.label('course_category', row.category) : '-' }}</template>
        </el-table-column>
        <el-table-column label="收费模式" width="100">
          <template #default="{ row }">{{ feeModeText(row) }}</template>
        </el-table-column>
        <el-table-column label="学费标准" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ feeStdText(row) }}</template>
        </el-table-column>
        <el-table-column label="授课模式" width="86">
          <template #default="{ row }">{{ row.teachMode ? dictStore.label('class_type', row.teachMode) : '-' }}</template>
        </el-table-column>
        <el-table-column label="开班数" width="76" align="center">
          <template #default="{ row }">
            <span :title="dlgCampus ? `${dlgCampus}开班 ${clazzCount(row.id, dlgCampus)} 个` : `全部校区合计 ${clazzCount(row.id)} 个`">
              {{ dlgCampus ? clazzCount(row.id, dlgCampus) : clazzCount(row.id) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="74" align="center">
          <template #default="{ row }">
            <el-tag v-if="editingEnrollId != null" size="small" type="success" effect="plain">可选</el-tag>
            <el-tag v-else-if="!canPickCourse(row)" size="small" type="info" effect="plain">已订阅</el-tag>
            <el-tag v-else-if="inCards(row.id, dlgCampus)" size="small" type="warning" effect="plain">已选择</el-tag>
            <el-tag v-else size="small" type="success" effect="plain">可选</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="picked-clazz-bar">
        <span class="fee-sub">已选择课程 <b style="color: #155EEF">{{ dlgSelCount }}</b> 门</span>
        <span v-if="dlgSubGroups.total" class="fee-sub" style="margin-left: 14px">
          已订阅共 <b style="color: #12b76a">{{ dlgSubGroups.total }}</b> 门：
          <span v-for="(g, i) in dlgSubGroups.list" :key="g.campus">{{ i > 0 ? '；' : '' }}{{ g.campus }}（{{ g.names.join('、') }}）</span>
        </span>
      </div>
      <template #footer>
        <el-button @click="courseDialog = false">取 消</el-button>
        <el-button type="primary" @click="confirmCourseDialog">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 选择班级弹窗：checkbox 多选 -->
    <el-dialog v-model="clazzDialog" :title="clazzPurpose === 'transfer' ? '选择班级（转入·单选）' : '选择班级'" width="880px" append-to-body>
      <div class="dlg-toolbar">
        <el-input v-model="clazzKw" placeholder="请输入班级名称搜索" clearable
                  style="width: 240px" :prefix-icon="Search" />
        <span class="fee-sub">选中班级后自动归到对应课程卡片</span>
      </div>
      <el-table ref="clazzTableRef" :data="clazzFilteredAll" height="320" size="small" stripe
                row-key="id" @selection-change="(rows) => (selClazzIds = rows.map((r) => r.id))">
        <el-table-column type="selection" width="44" reserve-selection />
        <el-table-column label="班级名称" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="clazz-name">{{ row.name }}</span>
            <span v-if="isFull(row)" class="mini-tag tag-full">满</span>
            <span v-else-if="isNew(row)" class="mini-tag tag-new">新</span>
          </template>
        </el-table-column>
        <el-table-column label="课程名称" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column label="人数" width="70" align="center">
          <template #default="{ row }">{{ occupied(row.id) }}/{{ row.capacity }}</template>
        </el-table-column>
        <el-table-column label="收费模式" width="90">
          <template #default="{ row }">{{ feeModeText(courseOf(row.courseId) || {}) }}</template>
        </el-table-column>
        <el-table-column label="学费标准" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ feeStdText(courseOf(row.courseId) || {}) }}</template>
        </el-table-column>
        <el-table-column label="教师" width="80" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.classTeacherId ? (teachers.find((t) => t.id === row.classTeacherId)?.name || '-') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="开班日期" width="90" align="center">
          <template #default="{ row }">{{ row.startDate || '—' }}</template>
        </el-table-column>
      </el-table>
      <div class="picked-clazz-bar">
        <span class="fee-sub">已选择班级 <b style="color: #155EEF">{{ selClazzIds.length }}</b> 个</span>
      </div>
      <template #footer>
        <el-button @click="clazzDialog = false">取 消</el-button>
        <el-button type="primary" @click="confirmClazzDialog">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 创建学员：与用户管理一致，类型锁定为学生 -->
    <el-drawer v-model="createVisible" title="创建学员（用户类型固定为学生）" size="480px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="createForm.username" placeholder="登录账号，如 stu2026" />
        </el-form-item>
        <el-form-item label="姓名" required><el-input v-model="createForm.realName" /></el-form-item>
        <el-form-item label="归属校区">
          <el-select v-model="createForm.orgId" clearable style="width: 100%" placeholder="选择所属组织（可不选）">
            <el-option v-for="o in orgOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="照片">
          <PhotoUpload :url="createPhotoPreview" :user-id="null" :auto-upload="false"
                       :fallback="createForm.realName" @select="onCreatePhotoSelected" />
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model="createForm.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="createForm.email" /></el-form-item>
        <el-divider content-position="left">学生信息</el-divider>
        <el-form-item label="出生日期">
          <el-date-picker v-model="createForm.birthDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="年级">
          <el-select v-model="createForm.grade" clearable filterable style="width: 100%">
            <el-option v-for="d in dictStore.options('grade')" :key="d.value" :label="d.label" :value="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="身份证号"><el-input v-model="createForm.idCard" maxlength="18" /></el-form-item>
        <el-form-item label="就读学校"><el-input v-model="createForm.school" /></el-form-item>
        <el-form-item label="家长姓名"><el-input v-model="createForm.parentName" /></el-form-item>
        <el-form-item label="家长电话"><el-input v-model="createForm.parentPhone" /></el-form-item>
        <el-form-item label="备用电话"><el-input v-model="createForm.backupPhone" /></el-form-item>
        <el-form-item label="归属顾问"><el-input v-model="createForm.advisor" /></el-form-item>
        <el-form-item label="学员标签"><el-input v-model="createForm.tags" placeholder="多标签用逗号分隔" /></el-form-item>
        <el-form-item label="家庭住址"><el-input v-model="createForm.address" /></el-form-item>
        <el-alert type="info" :closable="false"
                   title="初始密码 123456；用户类型固定为学生，创建后自动绑定学生档案与默认角色" />
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createStudent">创建并选择</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, CircleClose, CirclePlus, User, Tickets, ArrowDown, View, EditPen, Printer, Document,
  Wallet, Money, Switch, RefreshLeft, Reading, Medal, Right } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import ColumnSetting from '../../components/ColumnSetting.vue'
import api from '../../api'
import { useDictStore } from '../../store/dict'
import { useUserStore } from '../../store/user'
import PhotoUpload from '../../components/PhotoUpload.vue'

const dictStore = useDictStore()
const userStore = useUserStore()

/* ===== 页面模式：列表（默认）/ 填报表单 ===== */
const mode = ref('list')
/** 页面标题跟随办理入口：报名/续报、余额充值、转课、退费、积分、教材杂费、补费（列表页为 办理中心） */
const pageTitle = computed(() =>
  mode.value === 'form' ? '报名/续报' : mode.value === 'recharge' ? '余额充值'
    : mode.value === 'transfer' ? '转课' : mode.value === 'refund' ? '退费'
    : mode.value === 'score' ? '积分' : mode.value === 'material' ? '教材杂费'
    : mode.value === 'supplement' ? '补费' : '办理中心')

/* ===== 列表页 ===== */
const listKw = ref('')
const listOwing = ref(null)
const listRange = ref(null)
const quick = ref(null)
const listPage = ref(1)
const listPageSize = 20
const listLoading = ref(false)
const allRecords = ref([])
const stuMaps = ref({})
const stuName = (id) => stuMaps.value[id] || '-'
const todayStr = () => new Date().toISOString().slice(0, 10)

async function loadList() {
  listLoading.value = true
  try {
    allRecords.value = (await api.handleRecords({ page: 1, size: 200 })).records || []
    if (!Object.keys(stuMaps.value).length) {
      try {
        (await api.studentList()).forEach((s) => (stuMaps.value[s.id] = s.name))
      } catch (e) { /* 学员名缺失时显示 - */ }
    }
  } finally { listLoading.value = false }
}
const listStatus = ref(null)
/** 办理类型筛选：报名/充值/转课/退费/补费（统一记录列表） */
const listType = ref(null)
const filteredList = computed(() => {
  const kw = (listKw.value || '').trim().toLowerCase()
  const [b, e] = listRange.value || []
  return allRecords.value.filter((r) => {
    if (kw) {
      const content = `${stuMaps.value[r.studentId] || ''} ${r.packageName || ''} ${courseMap.value[r.courseId] || ''} ${r.content || ''}`.toLowerCase()
      if (!content.includes(kw)) return false
    }
    if (listType.value && r.type !== listType.value) return false
    if (listStatus.value != null && (r.kind !== 'enroll' || r.status !== listStatus.value)) return false
    if (listOwing.value === 'owing' && rowDue(r) <= 0) return false
    if (listOwing.value === 'clear' && rowDue(r) > 0) return false
    const d = r.handleDate || (r.createTime || '').slice(0, 10)
    if (b && d < b) return false
    if (e && d > e) return false
    if (quick.value === 'mine' && !(d === todayStr() && r.handler === (userStore.user?.realName || ''))) return false
    if (quick.value === 'campus' && d !== todayStr()) return false
    return true
  })
})
const pagedList = computed(() =>
  filteredList.value.slice((listPage.value - 1) * listPageSize, listPage.value * listPageSize))

/* 列设置（参考班级管理） */
const allColumns = [
  { prop: 'id', label: '订单号' }, { prop: 'studentId', label: '学员姓名' },
  { prop: 'content', label: '交易内容' }, { prop: 'totalAmount', label: '应收/应退' },
  { prop: 'paidAmount', label: '实收/实退' }, { prop: 'due', label: '欠费' },
  { prop: 'consultant', label: '咨询顾问' }, { prop: 'handleDate', label: '经办日期' },
  { prop: 'handler', label: '经办人' }, { prop: 'remark', label: '备注' },
  { prop: 'status', label: '报名状态' }, { prop: 'payStatus', label: '到款状态' }
]
const cols = reactive({ id: true, studentId: true, content: true, totalAmount: true, paidAmount: true,
  due: true, consultant: true, handleDate: true, handler: true, remark: true, status: true, payStatus: true })
const onCols = (c) => Object.assign(cols, c)

/** 操作列“更多”命令分发 */
function onListCommand(cmd, row) {
  if (cmd === 'view') viewOrder(row)
  else if (cmd === 'edit') editRecord(row)
  else if (cmd === 'print') printOrder(row)
  else if (cmd === 'log') openLog(row)
  else if (cmd === 'pay') togglePayStatus(row, 1)
  else if (cmd === 'unpay') togglePayStatus(row, 0)
  else if (cmd === 'void') voidOrder(row)
}

/** 确认到款/取消到款（互斥操作：确认后仅显示取消，取消后仅显示确认） */
async function togglePayStatus(row, status) {
  try {
    await api.enrollPayStatus(row.id, status)
    ElMessage.success(status === 1 ? '已确认到款' : '已取消到款')
    loadList()
  } catch (e) { /* 接口已弹错误提示 */ }
}

/** 作废订单：弹窗输入作废原因（必填），确认后记录作废并写回备注 */
async function voidOrder(row) {
  let reason = ''
  try {
    const r = await ElMessageBox.prompt(
      `作废后订单「${row.rid}」将标记为已作废（不可再编辑/收款），作废原因将展示在备注中`, '作废订单',
      { inputPlaceholder: '请输入作废原因', confirmButtonText: '确认作废', cancelButtonText: '取 消', type: 'warning' })
    reason = (r.value || '').trim()
  } catch { return }
  if (!reason) return ElMessage.warning('请填写作废原因')
  try {
    await api.enrollVoid(row.id, reason)
    ElMessage.success('订单已作废')
    loadList()
  } catch (e) { /* 接口已弹错误提示 */ }
}

/** 编辑提示语（按记录类型） */
const editHintOf = (row) => row.kind === 'enroll' ? '跳转填报页编辑'
  : row.kind === 'payment' ? '跳转缴费管理编辑'
  : row.type === '充值' ? '跳转充值页编辑' : '编辑经办信息'

/** 编辑入口按记录类型分流：报名单→报名/续报页；充值流水→充值页回显；
 *  转课/退费/积分流水→经办信息弹窗；补费→缴费管理 */
function editRecord(row) {
  if (row.kind === 'enroll') { openEdit(row); return }
  if (row.kind === 'payment') { router.push('/payment?entry=补费'); return }
  if (row.type === '充值') { editRechargeLog(row); return }
  openLogEdit(row)
}

/* 操作日志 */
const logVisible = ref(false)
const logRow = ref(null)
const logList = ref([])
const logLoading = ref(false)
async function openLog(row) {
  logRow.value = row
  logVisible.value = true
  logLoading.value = true
  try {
    logList.value = (await api.enrollLogs(row.id)) || []
  } catch (e) { logList.value = [] }
  finally { logLoading.value = false }
}
const listSum = computed(() => {
  const billable = filteredList.value.filter((r) => ['enroll', 'material', 'supplement'].includes(r.kind))
  const due = billable.reduce((a, r) => a + Number(r.totalAmount || 0), 0)
  const paid = billable.reduce((a, r) => a + Number(r.paidAmount || 0), 0)
  return { due: due.toFixed(2), paid: paid.toFixed(2), arrears: Math.max(due - paid, 0).toFixed(2) }
})
/** 汇总扩展：学员去重数 + 报课总计（按收费模式单位分组计量，课时与天/周/月不混加）+ 优惠总计 + 退费合计 */
const listStats = computed(() => {
  const students = new Set()
  const units = new Map()
  let discount = 0
  let refund = 0
  for (const r of filteredList.value) {
    if (r.studentId) students.add(r.studentId)
    if (r.kind === 'enroll') {
      const u = r.hoursUnit || '课时'
      units.set(u, (units.get(u) || 0) + Number(r.totalHours || 0))
      discount += Number(r.discountAmount || 0)
    }
    if (r.type === '退费') refund += Number(r.paidAmount || 0)
  }
  const courses = [...units].map(([u, n]) => `${n}${u}`).join(' + ')
  return { students: students.size, courses: courses || '0',
    discount: discount.toFixed(2), refund: refund.toFixed(2) }
})

/** 返回列表页：清空编辑态并刷新订单列表（顺带复位提交/校验状态，避免卡死态带入下次办理） */
function backToList() {
  editingEnrollId.value = null
  editingLogId.value = null
  saving.value = false
  recharging.value = false
  transferring.value = false
  mode.value = 'list'
  loadList()
}

/* ===== 未保存拦截：各办理页有已填内容时，返回列表前先确认 ===== */
/** 经办信息是否已填（销售来源/咨询顾问；采单员已下线） */
const handleFilled = (h) => !!(h && (h.source || h.consultant))
/** 支付账户是否已填实收金额 */
const payFilled = () => (pay.value?.rows || []).some((r) => Number(r.amount) > 0)
/** 各办理页「有变更」判定：仅完成初始态（未选学员或未填任何内容）视为无变更，返回时不提示 */
const formDirty = computed(() => {
  if (mode.value === 'list' || !picked.value) return false
  switch (mode.value) {
    case 'form': return cards.value.length > 0 || payFilled() || handleFilled(handle.value)
    case 'recharge': return recharge.value.rows.some((r) => Number(r.amount) > 0)
      || handleFilled(recharge.value) || !!(recharge.value.remark || '').trim()
    case 'transfer': return !!transferIn.value || handleFilled(handle.value)
      || !!(transfer.value.remark || '').trim()
    case 'refund': return !!refundFilter.value || payFilled() || handleFilled(handle.value)
      || !!(refund.value.remark || '').trim()
    case 'score': return Number(score.value.points) > 0 || handleFilled(handle.value)
      || !!(score.value.remark || '').trim()
    case 'material': return materialRows.value.length > 0 || payFilled() || handleFilled(handle.value)
      || !!(material.value.remark || '').trim()
    case 'supplement': return supplementRows.value.some((r) => Number(r.amount) > 0)
      || payFilled() || handleFilled(handle.value) || !!(supplement.value.remark || '').trim()
    default: return false
  }
})
/** 返回列表（带未保存拦截）：有已填内容先确认，避免误触丢失 */
async function tryBackToList() {
  if (formDirty.value) {
    try {
      await ElMessageBox.confirm(
        `当前「${pageTitle.value}」单据尚未保存，返回后已填写内容将丢失；<br/>如需保留请点「继续填写」，再点击页面底部按钮保存。`,
        '未保存提醒',
        { confirmButtonText: '放弃并返回', cancelButtonText: '继续填写', type: 'warning', dangerouslyUseHTMLString: true }
      )
    } catch (e) { return } // 继续填写：留在当前页
  }
  backToList()
}

/** 左侧菜单「办理中心」重复点击（已在办理中心页）→ 视同「返回列表」，同样走未保存拦截 */
const route = useRoute()
async function onMenuSelfClick(e) {
  if (e.detail !== route.path || mode.value === 'list') return
  tryBackToList()
}
onMounted(() => window.addEventListener('menu-self-click', onMenuSelfClick))
onBeforeUnmount(() => window.removeEventListener('menu-self-click', onMenuSelfClick))

/* 办理入口瓷贴：补费→缴费管理、转课→转课办理、退费→退费办理；教材杂费/积分功能规划中 */
const router = useRouter()
function goTile(path, name) {
  if (path) { router.push(path); return }
  ElMessage.info(`「${name}」功能规划中，敬请期待`)
}

/** 进入填报页：清空上次的表单状态 */
function enterForm() {
  editingEnrollId.value = null
  removedEnrollIds.value = []
  studentId.value = null
  picked.value = null
  cards.value = []
  // 支付信息为必填区域：默认给出一行可填的支付账户（首选渠道），避免空表格被必填校验拦下
  pay.value = { campus: null, balance: 0,
    rows: [{ channel: firstChannel.value, account: '', serial: '', amount: null }] }
  balanceAvail.value = 0
  handle.value.source = null
  handle.value.orderTaker = null
  handle.value.consultant = null
  Object.assign(handleErrs, { source: false, orderTaker: false, consultant: false })
  saving.value = false
  mode.value = 'form'
}

/* ===== 余额充值页（办理中心-余额充值入口） ===== */
const recharge = ref({ rows: [], campus: null, source: null, date: null, handler: '', orderTaker: null, consultant: null, remark: '' })
const recharging = ref(false)
/** 充值金额 = 支付账户实收合计 */
const rechargeTotal = computed(() =>
  recharge.value.rows.reduce((a, p) => a + Number(p.amount || 0), 0).toFixed(2))
/** 充值后账户余额 */
const rechargeAfterBalance = computed(() =>
  (Number(balanceAvail.value || 0) + Number(rechargeTotal.value || 0)).toFixed(2))

/** 进入余额充值页：清空学员与充值表单，默认一行支付账户（首选渠道） */
function enterRecharge() {
  editingLogId.value = null
  studentId.value = null
  picked.value = null
  balanceAvail.value = 0
  recharge.value = {
    rows: [{ channel: firstChannel.value, account: '', serial: '', amount: null }],
    campus: null, source: null,
    date: new Date().toISOString().slice(0, 10),
    handler: userStore.user?.realName || '',
    orderTaker: null, consultant: null, remark: ''
  }
  Object.assign(rechargeErrs, { source: false, orderTaker: false, consultant: false })
  recharging.value = false
  mode.value = 'recharge'
}

/** 提交充值：写账户流水（type=1），多账户支付明细与经办信息随单留档，成功后回列表；
 *  编辑模式（从办理记录进入）改为更新该笔流水 */
async function submitRecharge() {
  if (recharging.value) return // 重复提交防护：提交中忽略再次点击
  if (!picked.value) return ElMessage.warning('请先在「学员信息」中选择学员')
  if (!validateHandle(recharge.value, rechargeErrs)) return
  const rows = recharge.value.rows.filter((r) => Number(r.amount) > 0)
  if (!rows.length) return ElMessage.warning('请至少填写一条实收金额大于 0 的支付账户')
  const amount = Math.round(rows.reduce((a, r) => a + Number(r.amount), 0) * 100) / 100
  const payDetail = JSON.stringify(rows.map((r) =>
    ({ channel: r.channel, account: r.account, serial: r.serial, amount: Number(r.amount) })))
  recharging.value = true
  try {
    if (editingLogId.value) {
      // 编辑既有充值流水：金额/支付明细/经办信息就地更新
      await api.accountLogUpdate({
        id: editingLogId.value,
        amount,
        payDetail,
        campus: recharge.value.campus || undefined,
        source: recharge.value.source || undefined,
        handleDate: recharge.value.date || undefined,
        orderTaker: recharge.value.orderTaker || undefined,
        consultant: recharge.value.consultant || undefined,
        remark: recharge.value.remark.trim() || undefined
      })
      ElMessage.success('充值记录已保存修改')
      backToList()
      return
    }
    await api.accountRechargeBalance({
      studentId: picked.value.studentId,
      amount,
      // 支付明细JSON留档（多账户充值记录）
      payDetail: JSON.stringify(rows.map((r) => ({ channel: r.channel, account: r.account, serial: r.serial, amount: Number(r.amount) }))),
      campus: recharge.value.campus || undefined,
      source: recharge.value.source || undefined,
      handleDate: recharge.value.date || undefined,
      orderTaker: recharge.value.orderTaker || undefined,
      consultant: recharge.value.consultant || undefined,
      remark: recharge.value.remark.trim() || undefined
    })
    ElMessage.success(`已为 ${picked.value.name} 充值 ¥${amount.toFixed(2)}`)
    backToList()
  } catch (e) { /* 接口已提示 */ }
  finally { recharging.value = false }
}

/* 充值记录编辑（办理记录-余额充值）：回显到充值页，确认按钮变为保存修改 */
const editingLogId = ref(null)
function editRechargeLog(row) {
  editingLogId.value = row.refId
  mode.value = 'recharge'
  const stu = { studentId: row.studentId, name: stuMaps.value[row.studentId] || '-' }
  students.value = [stu]
  onPick(row.studentId)
  let rows = []
  if (row.payDetail) {
    try {
      rows = JSON.parse(row.payDetail).map((d) =>
        ({ channel: d.channel || '现金', account: d.account || '', serial: d.serial || '', amount: Number(d.amount) || 0 }))
    } catch (e) { /* JSON损坏按默认 */ }
  }
  if (!rows.length) rows = [{ channel: firstChannel.value, account: '', serial: '', amount: Number(row.amount) || null }]
  recharge.value = { rows,
    campus: row.campus || null, source: row.source || null,
    date: row.handleDate || (row.createTime || '').slice(0, 10),
    handler: userStore.user?.realName || '',
    orderTaker: row.orderTaker || null, consultant: row.consultant || null,
    remark: (row.remark || '').replace(/^余额充值：?/, '') }
  Object.assign(rechargeErrs, { source: false, orderTaker: false, consultant: false })
  recharging.value = false
}

/* 转课/退费流水编辑：结算结果不可逆，仅经办信息可改（弹窗保存） */
const logEditVisible = ref(false)
const logEditRow = ref(null)
const logEditSaving = ref(false)
const logEditForm = ref({ campus: null, source: null, date: null, orderTaker: null, consultant: null })
function openLogEdit(row) {
  logEditRow.value = row
  logEditForm.value = { campus: row.campus || null, source: row.source || null,
    date: row.handleDate || (row.createTime || '').slice(0, 10),
    orderTaker: row.orderTaker || null, consultant: row.consultant || null }
  Object.assign(handleErrs, { source: false, orderTaker: false, consultant: false })
  logEditVisible.value = true
}
async function saveLogEdit() {
  if (!logEditRow.value || logEditSaving.value) return
  if (!validateHandle(logEditForm.value, handleErrs)) return
  logEditSaving.value = true
  try {
    const payload = {
      id: logEditRow.value.refId,
      campus: logEditForm.value.campus || undefined,
      source: logEditForm.value.source || undefined,
      handleDate: logEditForm.value.date || undefined,
      orderTaker: logEditForm.value.orderTaker || undefined,
      consultant: logEditForm.value.consultant || undefined
    }
    // 积分/教材杂费/补费流水走各自接口，其余为账户流水
    if (logEditRow.value.kind === 'score') await api.scoreLogUpdate(payload)
    else if (logEditRow.value.kind === 'material') await api.materialLogUpdate(payload)
    else if (logEditRow.value.kind === 'supplement') await api.supplementLogUpdate(payload)
    else await api.accountLogUpdate(payload)
    ElMessage.success('经办信息已保存')
    logEditVisible.value = false
    loadList()
  } catch (e) { /* 接口已提示 */ }
  finally { logEditSaving.value = false }
}

/* ===== 转课页（办理中心-转课入口）：课时转出，学费自动转入账户余额 ===== */
const transfer = ref({ remark: '' })
const transferring = ref(false)
/** 办理类型：all=全部转出（剩余课时整单转出）/ part=部分转出（逐单填课时） */
const transferMode = ref('all')
/** 转出明细行：该学员的在读报名单（选中学员后加载，out=本次转出课时） */
const transferRows = ref([])
const rowRemaining = (r) => Math.max(Number(r.totalHours || 0) - Number(r.usedHours || 0), 0)
/** 实收折算单价 = 订单实收 ÷ 购买课时（转出金额按实收口径，报名欠费时转出同步降低） */
const rowUnitPaid = (r) => Number(r.paidAmount || 0) / Math.max(Number(r.totalHours || 0), 1)
/** 转出课程筛选（转课信息左侧下拉）：选中时表格仅显示该单 */
const transferFilter = ref(null)
/** 转入课程（选择课程/直接选班带出）：courseId + campus + clazzId（直选班时） */
const transferIn = ref(null)
/** 转入课程可选定价行（展示学费标准用） */
const transferInPrices = ref([])

/** 进入转课页：清空学员与表单，默认全部转出 */
function enterTransfer() {
  studentId.value = null
  picked.value = null
  balanceAvail.value = 0
  transferRows.value = []
  transferMode.value = 'all'
  transferFilter.value = null
  transferIn.value = null
  transferInPrices.value = []
  transfer.value = { remark: '' }
  handle.value.source = null
  handle.value.orderTaker = null
  handle.value.consultant = null
  handle.value.date = new Date().toISOString().slice(0, 10)
  Object.assign(handleErrs, { source: false, orderTaker: false, consultant: false })
  transferring.value = false
  mode.value = 'transfer'
}

/** 选中学员后拉取其在读报名单并构建转出明细行（默认全部转出=剩余课时） */
async function loadTransferFroms() {
  try {
    const records = (await api.enrollPage(
      { page: 1, size: 100, studentId: studentId.value, status: 1 })).records || []
    transferRows.value = records.map((e) => ({ ...e, out: Math.max(e.totalHours - (e.usedHours || 0), 0) }))
  } catch (e) { transferRows.value = [] }
}

/** 表格可见行：按转出课程筛选（未选=全部单） */
const transferViewRows = computed(() => transferFilter.value
  ? transferRows.value.filter((r) => r.id === transferFilter.value)
  : transferRows.value)

/** 切换办理类型：全部转出=可见单自动填入剩余课时并锁定；部分转出=可逐单修改 */
function onTransferModeChange(v) {
  if (v === 'all') transferViewRows.value.forEach((r) => { r.out = rowRemaining(r) })
}

/** 选定转入课程/班级（转课信息右侧）：初始化转入配置（定价/数量/有效期/优惠/备注） */
async function setTransferIn(cid, campus, clazzId) {
  const target = { courseId: cid, campus: campus || null, clazzId: clazzId || null,
    via: clazzId ? 'clazz' : 'course',
    priceId: null, qty: 1, validEnabled: false, validDate: null, remark: '', discounts: [] }
  transferIn.value = target
  transferInPrices.value = []
  if (!cid) return
  try {
    // 缓存仅在非空时可用（预载失败留下的空数组视为未命中，重新请求）
    const cached = dlgPriceMap.value[cid]
    const ps = Array.isArray(cached) && cached.length ? cached : ((await api.coursePrices(cid)) || [])
    if (!Array.isArray(cached) || !cached.length) dlgPriceMap.value[cid] = ps
    if (transferIn.value !== target) return // 期间已重选，丢弃本次加载
    transferInPrices.value = ps.filter((p) => p.chargeMode === 1)
    if (transferInPrices.value.length) transferIn.value.priceId = transferInPrices.value[0].id
  } catch (e) { transferInPrices.value = [] }
}

/* 转入课程金额联动：定价行×数量−优惠（无定价行按课程默认价） */
const transferInPrice = computed(() =>
  transferInPrices.value.find((p) => p.id === transferIn.value?.priceId) || null)
const transferInSubtotal = computed(() => {
  const t = transferIn.value
  if (!t) return '0.00'
  const q = Number(t.qty || 1)
  const p = transferInPrice.value
  if (p) return (Number(p.totalAmount) * q).toFixed(2)
  const c = courseOf(t.courseId)
  if (c.totalHours && c.price)
    return (Number(c.totalHours) * Number(c.price) * Number(c.discount || 100) / 100).toFixed(2)
  return '0.00'
})
const transferInDiscount = computed(() =>
  (transferIn.value?.discounts || []).reduce((a, d) => a + Number(d.amount || 0), 0).toFixed(2))
/** 转入应收 = 定价小计 − 优惠（下限0） */
const transferInDue = computed(() => {
  const v = Number(transferInSubtotal.value) - Number(transferInDiscount.value)
  return (v > 0 ? v : 0).toFixed(2)
})
const transferInHours = computed(() => {
  const t = transferIn.value
  if (!t) return '-'
  const q = Number(t.qty || 1)
  const p = transferInPrice.value
  if (p) return fmt(Number(p.hours) * q)
  const c = courseOf(t.courseId)
  return c.totalHours ? String(c.totalHours) : '-'
})

/* 转课支付联动：应收总计=转入应收−转出学费（差价，下限0）；实收=渠道+余额；欠费=差价−实收 */
const transferChannelTotal = computed(() =>
  pay.value.rows.reduce((a, p) => a + Number(p.amount || 0), 0).toFixed(2))
const transferPayDue = computed(() => {
  if (!transferIn.value) return '0.00'
  const v = Number(transferInDue.value) - Number(transferTotal.value)
  return (v > 0 ? v : 0).toFixed(2)
})
const transferReceived = computed(() =>
  (Number(transferChannelTotal.value) + Number(pay.value.balance || 0)).toFixed(2))
const transferPayArrears = computed(() =>
  Math.max(Number(transferPayDue.value) - Number(transferReceived.value), 0))

/* 转出合计：仅统计可见单中转出课时>0 的单 */
const transferCount = computed(() => transferViewRows.value.filter((r) => Number(r.out) > 0).length)
const transferHours = computed(() =>
  transferViewRows.value.reduce((a, r) => a + Number(r.out > 0 ? r.out : 0), 0))
const transferTotal = computed(() =>
  transferViewRows.value.reduce((a, r) => a + rowUnitPaid(r) * Number(r.out || 0), 0).toFixed(2))
const transferReady = computed(() => !!(picked.value && transferCount.value > 0))

/** 提交转课：逐单扣减课时（剩余全部转出的单置为已转课），学费合计入账户余额；
 *  已选转入课程时，随后自动报名转入课程并用转出学费优先抵扣（差额欠费） */
async function submitTransfer() {
  if (transferring.value) return // 重复提交防护：提交中忽略再次点击
  // 前置校验：逐项提示缺什么，不再静默无响应
  if (!picked.value) return ElMessage.warning('请先在「学员信息」中选择学员')
  if (!transferRows.value.length) return ElMessage.warning('该学员暂无在读报名单，无法转课')
  if (!transferFilter.value) return ElMessage.warning('请先在「转课信息」的「转出课程」中选择要转出的课程')
  const items = transferViewRows.value
    .filter((r) => Number(r.out) > 0)
    .map((r) => ({ enrollId: r.id, hours: Number(r.out) }))
  if (!items.length) return ElMessage.warning('请填写本次转出课时（选择「部分转出」后可逐单修改）')
  const tIn = transferIn.value
  if (tIn && subscribedIds.value.has(tIn.courseId + '|' + (tIn.campus || '')))
    return ElMessage.warning(`转入课程《${courseName(tIn.courseId)}》该校区已在读，请另选转入课程`)
  if (!validateHandle(handle.value, handleErrs)) return
  transferring.value = true
  try {
    const res = await api.enrollTransfer({
      studentId: picked.value.studentId,
      items,
      remark: transfer.value.remark.trim() || undefined,
      source: handle.value.source || undefined,
      handler: handle.value.handler || undefined,
      handleDate: handle.value.date || undefined,
      orderTaker: handle.value.orderTaker || undefined,
      consultant: handle.value.consultant || undefined
    })
    const d = res || {}
    let msg = `转出 ${d.count ?? items.length} 单共 ${d.hours ?? transferHours.value} 课时，学费 ¥${Number(d.total ?? 0).toFixed(2)} 已转入账户余额`
    // 已选转入课程：按转入卡片配置自动报名；结算=转出学费抵扣＋使用余额＋渠道实收，
    // 不足留欠费（可走补费），超出部分自动充入学员账户
    if (tIn) {
      const pr = transferInPrices.value.find((p) => p.id === tIn.priceId)
      const due = Number(transferInDue.value)
      const credit = Number(d.total) || 0
      const bal = Number(pay.value.balance || 0)
      const chan = Number(transferChannelTotal.value)
      const paidTotal = credit + bal + chan
      const paid = Math.min(paidTotal, due)
      const overAmt = Math.max(paidTotal - due, 0)
      const discTotal = Number(transferInDiscount.value)
      const cardRemark = (tIn.remark || '').trim()
      const payRows = []
      if (credit > 0) payRows.push({ channel: '转课抵扣', account: '', serial: `转出${d.count ?? items.length}单`, amount: Math.round(credit * 100) / 100 })
      if (bal > 0) payRows.push({ channel: '账户余额', account: '', serial: '', amount: Math.round(bal * 100) / 100 })
      pay.value.rows.filter((r) => Number(r.amount) > 0).forEach((r) =>
        payRows.push({ channel: r.channel, account: r.account || '', serial: r.serial || '', amount: Number(r.amount) }))
      try {
        await api.enrollSubscribe({
          studentId: picked.value.studentId,
          courseId: tIn.courseId,
          campus: tIn.campus || undefined,
          clazzId: tIn.clazzId || undefined,
          priceId: pr ? pr.id : undefined,
          qty: Number(tIn.qty || 1),
          discountAmount: discTotal > 0 ? discTotal : undefined,
          discountDetail: JSON.stringify((tIn.discounts || [])
            .filter((x) => x.type && Number(x.amount) > 0)
            .map((x) => ({ type: x.type, amount: Number(x.amount) }))),
          paidAmount: paid > 0 ? paid : undefined,
          payDetail: JSON.stringify(payRows),
          validDate: tIn.validEnabled ? (tIn.validDate || undefined) : undefined,
          remark: `转课转入（抵扣 ¥${credit.toFixed(2)}${bal > 0 ? `＋余额 ¥${bal.toFixed(2)}` : ''}${chan > 0 ? `＋渠道 ¥${chan.toFixed(2)}` : ''}）` + (cardRemark ? `：${cardRemark}` : ''),
          source: handle.value.source || undefined,
          handler: handle.value.handler || undefined,
          handleDate: handle.value.date || undefined,
          orderTaker: handle.value.orderTaker || undefined,
          consultant: handle.value.consultant || undefined
        })
        // 超收部分（抵扣+余额+渠道 超过转入应收）自动充入学员账户
        if (overAmt > 0) {
          try {
            await api.enrollCredit({ studentId: picked.value.studentId, amount: overAmt,
              remark: '转课实收超额自动充入' })
          } catch (e) { /* 充入失败不阻塞，可在充值管理补录 */ }
        }
        msg += `；已转入《${courseName(tIn.courseId)}》（抵扣 ¥${credit.toFixed(2)}`
        if (bal > 0) msg += `＋余额 ¥${bal.toFixed(2)}`
        if (chan > 0) msg += `＋渠道 ¥${chan.toFixed(2)}`
        if (due - paid > 0) msg += `，欠费 ¥${(due - paid).toFixed(2)}（可走补费）`
        else if (overAmt > 0) msg += `，余 ¥${overAmt.toFixed(2)} 已充入账户`
        msg += `）`
      } catch (e) {
        msg += `；转入课程未完成（错误已提示），转出学费已留在账户余额`
      }
    }
    ElMessage.success(`转课成功：${msg}`)
    backToList()
  } catch (e) { /* 接口已提示 */ }
  finally { transferring.value = false }
}

/* ===== 退费页（办理中心-退费入口）：退出课时，金额转结账户余额 ===== */
const refund = ref({ remark: '' })
const refunding = ref(false)
/** 退费类型：all=全部退费 / part=部分退费（逐单填课时） */
const refundMode = ref('all')
/** 退费课程筛选（必选后才展示订单表格） */
const refundFilter = ref(null)
/** 退费明细行：在读报名单（out=本次退出课时，byOriginal=按原价退费） */
const refundRows = ref([])
/** 单行退费金额：按原价=原单价×课时（优惠一并退）；默认实收折算 */
const rowRefundAmt = (r) => (r.byOriginal ? Number(r.price || 0) : rowUnitPaid(r)) * Number(r.out || 0)

/** 进入退费页：清空学员与表单，默认全部退费（实退默认一行可填渠道） */
function enterRefund() {
  studentId.value = null
  picked.value = null
  balanceAvail.value = 0
  refundRows.value = []
  refundMode.value = 'all'
  refundFilter.value = null
  refund.value = { remark: '' }
  pay.value = { campus: null, balance: 0,
    rows: [{ channel: firstChannel.value, account: '', serial: '', amount: null }] }
  handle.value.source = null
  handle.value.orderTaker = null
  handle.value.consultant = null
  handle.value.date = new Date().toISOString().slice(0, 10)
  Object.assign(handleErrs, { source: false, orderTaker: false, consultant: false })
  refunding.value = false
  mode.value = 'refund'
}

/** 选中学员后拉取其在读报名单（退费候选，默认全部退费=剩余课时、实收口径） */
async function loadRefundRows() {
  try {
    const records = (await api.enrollPage(
      { page: 1, size: 100, studentId: studentId.value, status: 1 })).records || []
    refundRows.value = records.map((e) => ({
      ...e, out: Math.max(e.totalHours - (e.usedHours || 0), 0), byOriginal: false }))
  } catch (e) { refundRows.value = [] }
}

/** 表格可见行：按退费课程筛选 */
const refundViewRows = computed(() => refundFilter.value
  ? refundRows.value.filter((r) => r.id === refundFilter.value)
  : refundRows.value)

/** 切换退费类型：全部退费=可见单自动填入剩余课时并锁定；部分退费=可逐单修改 */
function onRefundModeChange(v) {
  if (v === 'all') refundViewRows.value.forEach((r) => { r.out = rowRemaining(r) })
}

/* 退费合计：仅统计可见单中退出课时>0 的单 */
const refundCount = computed(() => refundViewRows.value.filter((r) => Number(r.out) > 0).length)
const refundHours = computed(() =>
  refundViewRows.value.reduce((a, r) => a + Number(r.out > 0 ? r.out : 0), 0))
const refundTotal = computed(() =>
  refundViewRows.value.reduce((a, r) => a + rowRefundAmt(r), 0).toFixed(2))
/* 支付信息联动：实退=渠道退款合计；增加余额=应退−实退（差额自动转入账户余额） */
const refundPaidTotal = computed(() =>
  pay.value.rows.reduce((a, p) => a + Number(p.amount || 0), 0).toFixed(2))
const refundBalanceAmount = computed(() =>
  Math.max(Number(refundTotal.value) - Number(refundPaidTotal.value), 0).toFixed(2))

/** 提交退费：逐单扣减课时（剩余全部退出的单置为已退订），金额合计转结账户余额 */
async function submitRefund() {
  if (refunding.value) return // 重复提交防护：提交中忽略再次点击
  if (!picked.value) return ElMessage.warning('请先在「学员信息」中选择学员')
  if (!refundRows.value.length) return ElMessage.warning('该学员暂无在读报名单，无法退费')
  if (!refundFilter.value) return ElMessage.warning('请先在「退费信息」中选择要退费的课程')
  const items = refundViewRows.value
    .filter((r) => Number(r.out) > 0)
    .map((r) => ({ enrollId: r.id, hours: Number(r.out), byOriginal: !!r.byOriginal }))
  if (!items.length) return ElMessage.warning('请填写本次退出课时（选择「部分退费」后可逐单修改）')
  if (Number(refundPaidTotal.value) > Number(refundTotal.value))
    return ElMessage.warning(`实退金额 ¥${refundPaidTotal.value} 不能超过应退金额 ¥${refundTotal.value}`)
  if (!validateHandle(handle.value, handleErrs)) return
  refunding.value = true
  try {
    const rows = pay.value.rows.filter((r) => Number(r.amount) > 0)
    const res = await api.enrollRefund({
      studentId: picked.value.studentId,
      items,
      paidOut: Number(refundPaidTotal.value) || 0,
      payDetail: JSON.stringify(rows.map((r) =>
        ({ channel: r.channel, account: r.account, serial: r.serial, amount: Number(r.amount) }))),
      campus: pay.value.campus || undefined,
      remark: refund.value.remark.trim() || undefined,
      source: handle.value.source || undefined,
      handler: handle.value.handler || undefined,
      handleDate: handle.value.date || undefined,
      orderTaker: handle.value.orderTaker || undefined,
      consultant: handle.value.consultant || undefined
    })
    const d = res || {}
    let msg = `退费成功：退出 ${d.count ?? items.length} 单共 ${d.hours ?? refundHours.value} 课时，应退 ¥${Number(d.total ?? 0).toFixed(2)}`
    if (Number(d.paidOut) > 0) msg += `，实退 ¥${Number(d.paidOut).toFixed(2)}`
    if (Number(d.toBalance) > 0) msg += `，转结余额 ¥${Number(d.toBalance).toFixed(2)}`
    ElMessage.success(msg)
    backToList()
  } catch (e) { /* 接口已提示 */ }
  finally { refunding.value = false }
}

/* ===== 积分页（办理中心-积分入口）：增加积分/积分兑换，余额=Σ增加−Σ兑换 ===== */
const score = ref({ type: 1, points: null, campus: null, remark: '' })
const scoring = ref(false)
const scoreAvail = ref(0)
const scoreSum = ref({ earned: '0', used: '0' })

/** 进入积分页：清空学员与表单，默认增加积分 */
function enterScore() {
  studentId.value = null
  picked.value = null
  balanceAvail.value = 0
  scoreAvail.value = 0
  scoreSum.value = { earned: '0', used: '0' }
  score.value = { type: 1, points: null, campus: null, remark: '' }
  handle.value.source = null
  handle.value.orderTaker = null
  handle.value.consultant = null
  handle.value.date = new Date().toISOString().slice(0, 10)
  Object.assign(handleErrs, { source: false, orderTaker: false, consultant: false })
  scoring.value = false
  mode.value = 'score'
}

/** 选中学员后拉取积分余额（累计增加/兑换/剩余） */
async function loadScoreSummary() {
  try {
    const s = await api.scoreSummary({ studentId: studentId.value })
    scoreAvail.value = Number(s?.total || 0)
    scoreSum.value = { earned: Number(s?.earned || 0).toFixed(0), used: Number(s?.used || 0).toFixed(0) }
  } catch (e) { scoreAvail.value = 0; scoreSum.value = { earned: '0', used: '0' } }
}

/** 提交积分办理：type1增加 / type2兑换（后端校验余额），成功后回列表 */
async function submitScore() {
  if (scoring.value) return // 重复提交防护：提交中忽略再次点击
  if (!picked.value) return ElMessage.warning('请先在「学员信息」中选择学员')
  if (!Number(score.value.points) || Number(score.value.points) <= 0)
    return ElMessage.warning(`请填写${score.value.type === 1 ? '增加' : '使用'}的积分数量`)
  if (score.value.type === 2 && Number(score.value.points) > scoreAvail.value)
    return ElMessage.warning(`兑换积分超过剩余积分（${scoreAvail.value} 分）`)
  if (!validateHandle(handle.value, handleErrs)) return
  scoring.value = true
  try {
    const res = await api.scoreHandle({
      studentId: picked.value.studentId,
      type: score.value.type,
      points: Number(score.value.points),
      campus: score.value.campus || undefined,
      remark: score.value.remark.trim() || undefined,
      source: handle.value.source || undefined,
      handleDate: handle.value.date || undefined,
      orderTaker: handle.value.orderTaker || undefined,
      consultant: handle.value.consultant || undefined
    })
    ElMessage.success(`${score.value.type === 1 ? '增加积分' : '积分兑换'}成功：${Number(score.value.points)} 分，剩余 ${Number(res?.total ?? 0).toFixed(0)} 分`)
    backToList()
  } catch (e) { /* 接口已提示 */ }
  finally { scoring.value = false }
}

/* ===== 教材杂费页（办理中心-教材杂费入口）：多项目明细 + 渠道收银 ===== */
const material = ref({ remark: '' })
const materialing = ref(false)
/** 已选明细行：{materialId,name,price,qty,got} */
const materialRows = ref([])
const materials = ref([])
const materialDialog = ref(false)
const materialTableRef = ref(null)
const materialSel = ref([])
const materialSaving = ref(false)
const materialNew = ref({ name: '', price: null })

/** 进入教材杂费页：清空学员与表单 */
function enterMaterial() {
  studentId.value = null
  picked.value = null
  materialRows.value = []
  material.value = { remark: '' }
  pay.value = { campus: null, balance: 0,
    rows: [{ channel: firstChannel.value, account: '', serial: '', amount: null }] }
  handle.value.source = null
  handle.value.orderTaker = null
  handle.value.consultant = null
  handle.value.date = new Date().toISOString().slice(0, 10)
  Object.assign(handleErrs, { source: false, orderTaker: false, consultant: false })
  materialing.value = false
  mode.value = 'material'
}

/** 打开选择弹窗：加载项目库（未加载过时） */
async function openMaterialDialog() {
  if (!materials.value.length) {
    try { materials.value = (await api.materialList()) || [] } catch (e) { materials.value = [] }
  }
  materialNew.value = { name: '', price: null }
  materialDialog.value = true
}
const materialFiltered = computed(() => materials.value)
/** 快捷新增项目（弹窗内）：保存后自动勾选待加入 */
async function saveMaterialItem() {
  if (!materialNew.value.name.trim()) return ElMessage.warning('请填写项目名称')
  if (Number(materialNew.value.price) < 0 || materialNew.value.price == null)
    return ElMessage.warning('请填写价格')
  materialSaving.value = true
  try {
    const m = await api.materialSave({ name: materialNew.value.name.trim(), price: Number(materialNew.value.price) })
    materials.value.push(m)
    materialNew.value = { name: '', price: null }
    ElMessage.success('项目已新增，勾选后点确定加入办理')
  } catch (e) { /* 接口已提示 */ }
  finally { materialSaving.value = false }
}
/** 停用项目（历史流水不受影响） */
async function disableMaterialItem(row) {
  try {
    await api.materialDisable(row.id)
    materials.value = materials.value.filter((m) => m.id !== row.id)
    ElMessage.success('项目已停用')
  } catch (e) { /* 接口已提示 */ }
}
/** 弹窗确定：勾选项目加入明细（价格带出、数量默认1、默认领用） */
function confirmMaterialDialog() {
  const chosen = materials.value.filter((m) => materialSel.value.includes(m.id))
  for (const m of chosen) {
    if (materialRows.value.some((r) => r.materialId === m.id)) continue
    materialRows.value.push({ materialId: m.id, name: m.name, price: Number(m.price) || 0, qty: 1, got: true })
  }
  materialDialog.value = false
  if (chosen.length) ElMessage.success(`已加入 ${chosen.length} 项`)
}
/* 明细合计 */
const materialTotal = computed(() =>
  materialRows.value.reduce((a, r) => a + Number(r.price || 0) * Number(r.qty || 1), 0).toFixed(2))
const materialGotCount = computed(() => materialRows.value.filter((r) => r.got).length)
const materialPaidTotal = computed(() =>
  pay.value.rows.reduce((a, p) => a + Number(p.amount || 0), 0).toFixed(2))
const materialArrears = computed(() =>
  Math.max(Number(materialTotal.value) - Number(materialPaidTotal.value), 0))

/** 提交教材杂费：明细+支付留档，允许欠费 */
async function submitMaterial() {
  if (materialing.value) return // 重复提交防护
  if (!picked.value) return ElMessage.warning('请先在「学员信息」中选择学员')
  if (!materialRows.value.length) return ElMessage.warning('请先点击「选择教材杂费」添加项目')
  const bad = materialRows.value.find((r) => Number(r.qty) < 1 || Number(r.price) < 0)
  if (bad) return ElMessage.warning(`项目「${bad.name}」的数量/价格不正确`)
  if (Number(materialPaidTotal.value) > Number(materialTotal.value))
    return ElMessage.warning(`实收 ¥${materialPaidTotal.value} 不能超过小计 ¥${materialTotal.value}`)
  if (!validateHandle(handle.value, handleErrs)) return
  materialing.value = true
  try {
    const items = materialRows.value.map((r) =>
      ({ materialId: r.materialId, name: r.name, price: Number(r.price), qty: Number(r.qty), got: !!r.got }))
    const rows = pay.value.rows.filter((r) => Number(r.amount) > 0)
    const paid = Math.round(rows.reduce((a, r) => a + Number(r.amount), 0) * 100) / 100
    const res = await api.materialHandle({
      studentId: picked.value.studentId,
      items,
      itemsJson: JSON.stringify(items),
      paidAmount: paid || 0,
      payDetail: JSON.stringify(rows.map((r) =>
        ({ channel: r.channel, account: r.account, serial: r.serial, amount: Number(r.amount) }))),
      campus: pay.value.campus || undefined,
      remark: material.value.remark.trim() || undefined,
      source: handle.value.source || undefined,
      handleDate: handle.value.date || undefined,
      orderTaker: handle.value.orderTaker || undefined,
      consultant: handle.value.consultant || undefined
    })
    const d = res || {}
    ElMessage.success(`教材杂费办理成功：应收 ¥${Number(d.amount ?? 0).toFixed(2)}，实收 ¥${Number(d.paid ?? 0).toFixed(2)}`
      + (Number(d.arrears) > 0 ? `，欠费 ¥${Number(d.arrears).toFixed(2)}` : ''))
    backToList()
  } catch (e) { /* 接口已提示 */ }
  finally { materialing.value = false }
}

/* ===== 补费页（办理中心-补费入口）：欠费单逐单补收 ===== */
const supplement = ref({ remark: '' })
const supplementing = ref(false)
/** 欠费记录行：amount=本次补费金额（默认补满欠费） */
const supplementRows = ref([])

/** 进入补费页：清空学员与表单 */
function enterSupplement() {
  studentId.value = null
  picked.value = null
  supplementRows.value = []
  supplement.value = { remark: '' }
  pay.value = { campus: null, balance: 0,
    rows: [{ channel: firstChannel.value, account: '', serial: '', amount: null }] }
  handle.value.source = null
  handle.value.orderTaker = null
  handle.value.consultant = null
  handle.value.date = new Date().toISOString().slice(0, 10)
  Object.assign(handleErrs, { source: false, orderTaker: false, consultant: false })
  supplementing.value = false
  mode.value = 'supplement'
}

/** 选中学员后拉取其欠费记录（在读报名单 + 教材杂费单，欠费>0） */
async function loadSupplementRows() {
  try {
    const rows = (await api.supplementRecords(studentId.value)) || []
    supplementRows.value = rows.map((r) => ({ ...r, amount: null }))
  } catch (e) { supplementRows.value = [] }
}

/* 补费联动：补费合计 / 实收 / 仍欠 / 剩余欠费（未补部分） */
const supplementCount = computed(() =>
  supplementRows.value.filter((r) => Number(r.amount) > 0).length)
const supplementTotal = computed(() =>
  supplementRows.value.reduce((a, r) => a + Number(r.amount > 0 ? r.amount : 0), 0).toFixed(2))
const supplementPaidTotal = computed(() =>
  pay.value.rows.reduce((a, p) => a + Number(p.amount || 0), 0).toFixed(2))
const supplementArrears = computed(() =>
  Math.max(Number(supplementTotal.value) - Number(supplementPaidTotal.value), 0))
const supplementLeftArrears = computed(() =>
  supplementRows.value.reduce((a, r) => a + Math.max(Number(r.arrears) - Number(r.amount || 0), 0), 0).toFixed(2))

/** 提交补费：逐单追加实收到原单，写补费流水 */
async function submitSupplement() {
  if (supplementing.value) return // 重复提交防护
  if (!picked.value) return ElMessage.warning('请先在「学员信息」中选择学员')
  if (!supplementRows.value.length) return ElMessage.warning('该学员暂无欠费记录，无需补费')
  const items = supplementRows.value
    .filter((r) => Number(r.amount) > 0)
    .map((r) => ({ kind: r.kind, refId: r.refId, amount: Math.round(Number(r.amount) * 100) / 100 }))
  if (!items.length) return ElMessage.warning('请填写本次补费金额（每单上限为其欠费金额）')
  if (Number(supplementPaidTotal.value) > Number(supplementTotal.value))
    return ElMessage.warning(`实收 ¥${supplementPaidTotal.value} 不能超过补费金额 ¥${supplementTotal.value}`)
  if (!validateHandle(handle.value, handleErrs)) return
  supplementing.value = true
  try {
    const rows = pay.value.rows.filter((r) => Number(r.amount) > 0)
    const paid = Math.round(rows.reduce((a, r) => a + Number(r.amount), 0) * 100) / 100
    const detail = supplementRows.value
      .filter((r) => Number(r.amount) > 0)
      .map((r) => ({ kind: r.kind, refId: r.refId, rid: r.rid, typeName: r.typeName,
        name: r.kind === 'enroll' ? (r.packageName || courseName(r.courseId)) : r.name,
        arrears: Number(r.arrears), amount: Number(r.amount) }))
    const res = await api.supplementHandle({
      studentId: picked.value.studentId,
      items,
      itemsJson: JSON.stringify(detail),
      paidAmount: paid || 0,
      payDetail: JSON.stringify(rows.map((r) =>
        ({ channel: r.channel, account: r.account, serial: r.serial, amount: Number(r.amount) }))),
      campus: pay.value.campus || undefined,
      remark: supplement.value.remark.trim() || undefined,
      source: handle.value.source || undefined,
      handleDate: handle.value.date || undefined,
      orderTaker: handle.value.orderTaker || undefined,
      consultant: handle.value.consultant || undefined
    })
    const d = res || {}
    ElMessage.success(`补费成功：${d.count ?? items.length} 单共 ¥${Number(d.total ?? 0).toFixed(2)}，实收 ¥${Number(d.paid ?? 0).toFixed(2)}`
      + (Number(d.arrears) > 0 ? `，仍欠 ¥${Number(d.arrears).toFixed(2)}` : '，已结清'))
    backToList()
  } catch (e) { /* 接口已提示 */ }
  finally { supplementing.value = false }
}

/* ===== 订单操作：查看 / 打印 / 编辑 ===== */
const clazzName = (cid) => (cid ? (clazzes.value.find((c) => c.id === cid)?.name || '-') : '-')
const voucherVisible = ref(false)
const viewRow = ref(null)
function viewOrder(row) {
  viewRow.value = row
  voucherVisible.value = true
}

/** 打印：新窗口输出业务凭证（表格版式）并调起打印 */
function printOrder(row) {
  if (!row) return
  const due = rowDue(row).toFixed(2)
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>业务凭证 EN${row.id}</title>
<style>
  body { font-family: "Microsoft YaHei", sans-serif; color: #000; padding: 20px; max-width: 760px; margin: 0 auto; }
  .top { font-size: 20px; font-weight: 700; text-align: center; margin-bottom: 8px; }
  table { width: 100%; border-collapse: collapse; margin-bottom: 8px; table-layout: fixed; }
  th, td { border: 1px solid #000; padding: 6px 8px; font-size: 13px; text-align: center; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  th { background: #f2f2f2; }
  td.lbl { background: #f7f7f7; font-weight: 600; width: 11%; }
  td.tl { text-align: left; }
  .head td { font-size: 12.5px; }
  .note { margin-top: 6px; font-size: 12px; }
  @media print { body { padding: 0; max-width: none } }
</style></head><body>
<div class="top">智教云课 KaiEdu 业务凭证</div>
<table class="head">
  <tr>
    <td class="lbl">学员姓名</td><td>${stuName(row.studentId)}</td>
    <td class="lbl">订单类型</td><td>报名</td>
    <td class="lbl">经办日期</td><td>${row.handleDate || (row.createTime || '').slice(0, 10)}</td>
    <td class="lbl">订单号</td><td>EN${row.id}</td>
  </tr>
</table>
<table>
  <tr><th style="width:19%">交易内容</th><th style="width:17%">班级信息</th><th style="width:16%">价格</th><th style="width:7%">数量</th><th style="width:11%">原价</th><th style="width:12%">优惠</th><th style="width:11%">应收小计</th></tr>
  <tr>
    <td class="tl">${row.packageName || courseName(row.courseId)}</td>
    <td class="tl">${clazzName(row.clazzId)}</td>
    <td>${row.totalHours}课时=¥${row.price}</td>
    <td>1</td>
    <td>¥${(Number(row.totalAmount || 0) + Number(row.discountAmount || 0)).toFixed(2)}</td>
    <td>${row.discountAmount > 0 ? '¥' + row.discountAmount : '—'}</td>
    <td><b>¥${row.totalAmount}</b></td>
  </tr>
  <tr><td colspan="7" class="tl">备注：${row.remark || ''}</td></tr>
</table>
<table>
  <tr>
    <td class="lbl">应收总计</td><td><b>¥${row.totalAmount}</b></td>
    <td class="lbl">实收总计</td><td><b>¥${row.paidAmount ?? '0.00'}</b></td>
    <td class="lbl">使用余额</td><td>¥0.00</td>
    <td class="lbl">欠费</td><td><b>¥${due}</b></td>
  </tr>
  <tr><td class="lbl">收款方式</td><td colspan="7" class="tl">合计实收 ¥${row.paidAmount ?? '0.00'}</td></tr>
  <tr>
    <td class="lbl">打印时间</td><td colspan="3">${new Date().toLocaleString('zh-CN')}</td>
    <td class="lbl">经办人</td><td colspan="3">${row.handler || '-'}</td>
  </tr>
  <tr style="height: 36px">
    <td class="lbl">经办签名</td><td colspan="3">&nbsp;</td>
    <td class="lbl">客户签名</td><td colspan="3">&nbsp;</td>
  </tr>
</table>
<div class="note">开课三次后不予办理退费。请您保管好您的收据，丢失不予补办。</div>
<script>window.onload = function () { window.print() }<\/script>
</body></html>`
  const w = window.open('', '_blank')
  if (!w) return ElMessage.warning('浏览器拦截了打印窗口，请允许弹窗后重试')
  w.document.write(html)
  w.document.close()
}

/* 编辑报名单：跳转填报页预填数据 */
const editingEnrollId = ref(null)
/** 编辑模式下本次移除的报名记录ID（保存时删除对应报名单） */
const removedEnrollIds = ref([])

/** 编辑卡片：以记录实况初始化，未改定价/数量前按记录值显示课时与金额 */
function buildEditCard(e) {
  const card = {
    courseId: e.courseId, campus: e.campus || null, clazzId: e.clazzId, priceId: null, qty: 1,
    teacherId: e.teacherId,
    validEnabled: !!e.validDate, validDate: e.validDate || null,
    remark: e.remark || '', discounts: [], priceRows: [],
    __editingId: e.id,
    /** 套餐订阅无关联课程时的显示名 */
    pkgName: e.packageName || null,
    /** 记录实况：总课时 / 原价小计（未减优惠） */
    baseHours: e.totalHours,
    baseSubtotal: (Number(e.totalAmount || 0) + Number(e.discountAmount || 0)).toFixed(2),
    /** 用户是否改过定价标准/数量（改过后按所选定价行重算） */
    customized: false,
    /** 卡片来源：course=选择课程加入 / clazz=选择班级带出（按记录是否关联班级推导） */
    origin: e.clazzId ? 'clazz' : 'course'
  }
  // 优惠在构建时同步恢复（主卡/附加卡一致）：卡片小计与合计从第一帧起即为核减后的应收；
  // 优先按留档明细JSON原样恢复多行优惠（学员优惠/活动优惠不合并），无留档时按合计合成一行
  let restored = false
  if (e.discountDetail) {
    try {
      const dd = JSON.parse(e.discountDetail)
      if (Array.isArray(dd) && dd.length) {
        dd.forEach((d) => card.discounts.push({ type: d.type || '学费优惠', amount: Number(d.amount) || 0 }))
        restored = true
      }
    } catch (err) { /* JSON损坏按合计合成 */ }
  }
  if (!restored && Number(e.discountAmount || 0) > 0)
    card.discounts.push({ type: '学费优惠', amount: Number(e.discountAmount) })
  return card
}

/** 优惠明细JSON留档（有效行：类型+金额），保存时与合计 discountAmount 一同提交 */
const discountDetailJson = (card) => JSON.stringify(
  (card.discounts || []).filter((d) => d.type && Number(d.amount) > 0)
    .map((d) => ({ type: d.type, amount: Number(d.amount) }))
)

/** 按记录值反推 定价行×份数：命中则回选该行，卡片天然显示记录实况 */
function matchPriceRow(card) {
  const hours = Number(card.baseHours || 0)
  const original = Number(card.baseSubtotal || 0)
  if (!hours || !original) return null
  for (const p of card.priceRows) {
    const q = hours / Number(p.hours)
    if (q >= 1 && q <= 99 && Math.abs(q - Math.round(q)) < 1e-9
        && Math.abs(Number(p.totalAmount) * q - original) < 0.005)
      return { priceId: p.id, qty: Math.round(q) }
  }
  return null
}

/** 编辑卡片改数量：仅在已选定价行时按新数量重算（套餐/无定价卡保持记录值） */
function onCardQtyChange(card) {
  if (card.priceId) card.customized = true
}

async function openEdit(row) {
  editingEnrollId.value = row.id
  removedEnrollIds.value = []
  mode.value = 'form'
  // 预选学员
  const stu = { studentId: row.studentId, name: stuName(row.studentId) }
  students.value = [stu]
  onPick(row.studentId)
  // 构建课程卡片（仅本条订单记录；优惠在 buildEditCard 内同步恢复，合计从首帧起即为核减后的应收）
  const card = buildEditCard(row)
  cards.value = [card]
  // 编辑只呈现本条订单记录（不再并入该学员其它在读记录，避免多卡混看）；
  // 需要调整其它课程时从列表分别进入对应记录编辑
  // 支付信息：恢复本条记录实收（payDetail 留档逐行恢复，缺留档按 paidAmount 补行），与应收总计同口径
  pay.value.balance = 0
  pay.value.rows = []
  if (row.payDetail) {
    try {
      const detail = JSON.parse(row.payDetail)
      if (Array.isArray(detail) && detail.length)
        detail.forEach((d) => pay.value.rows.push(
          { channel: d.channel || '现金', account: d.account || '', serial: d.serial || '', amount: Number(d.amount) || 0 }))
    } catch (e) { /* JSON 解析失败按 paidAmount 补行 */ }
  }
  if (!pay.value.rows.length && Number(row.paidAmount || 0) > 0)
    pay.value.rows.push({ channel: '现金', account: '', serial: '', amount: Number(row.paidAmount) })
  // 经办信息
  handle.value.source = row.source || null
  handle.value.orderTaker = row.orderTaker || null
  handle.value.consultant = row.consultant || null
  handle.value.date = row.handleDate || (row.createTime || '').slice(0, 10)
  // 最后异步加载定价行并按记录值回选 定价行×份数（仅回选控件值，不影响卡片金额显示）
  for (const c of cards.value) {
    if (!c.courseId) continue
    try {
      const ps = (await api.coursePrices(c.courseId)) || []
      dlgPriceMap.value[c.courseId] = ps
      c.priceRows = pricesOfCard(ps, c.campus)
      const m = matchPriceRow(c)
      if (m) { c.priceId = m.priceId; c.qty = m.qty }
    } catch (e) { /* 无定价按默认 */ }
  }
}
/** 移除课程卡片：编辑模式下记录待删除的报名ID，保存时删除对应报名单 */
function removeCard(card) {
  const idx = cards.value.indexOf(card)
  if (idx < 0) return
  const doRemove = () => {
    if (card.__editingId && editingEnrollId.value != null && !removedEnrollIds.value.includes(card.__editingId))
      removedEnrollIds.value.push(card.__editingId)
    cards.value.splice(idx, 1)
  }
  if (card.__editingId && editingEnrollId.value != null) {
    ElMessageBox.confirm('移除后点击「保存修改」将删除该条报名记录（含其支付明细与操作日志），是否继续？', '移除课程', {
      confirmButtonText: '移 除',
      cancelButtonText: '取 消',
      type: 'warning'
    }).then(doRemove).catch(() => {})
  } else {
    doRemove()
  }
}

async function saveEdit() {
  if (saving.value) return // 重复提交防护：提交中忽略再次点击
  if (!editingEnrollId.value) return
  // 必填校验与新增一致（编辑时各区域内容从原单带出、可删改，保存前仍须保证有内容）：
  // 缺失项汇总成 alert 弹窗逐条列出
  if (!picked.value) return requiredAlert(['学员（在「学员信息」中选择学员）'])
  if (!cards.value.length) return requiredAlert(['报名课程（编辑时至少保留一门课程卡片）'])
  const missing = []
  handleErrs.source = !handle.value.source
  handleErrs.orderTaker = !handle.value.orderTaker
  handleErrs.consultant = !handle.value.consultant
  if (!handle.value.source) missing.push('销售来源（经办信息）')
  if (!handle.value.consultant) missing.push('咨询顾问（经办信息）')
  payErr.value = !hasPayRow.value
  if (!hasPayRow.value) missing.push('支付信息（至少一条实收金额大于 0 的支付记录）')
  if (missing.length) return requiredAlert(missing)
  // 超收拦截：实收不能超过应收（预存请走「余额充值」），不再自动充入账户产生充值流水
  if (overpay.value > 0)
    return ElMessage.warning(`实收金额（¥${receivedTotal.value}）超过应收（¥${totalAmount.value}）¥${overpay.value.toFixed(2)}，请调整支付金额；预存款项请走「余额充值」办理`)
  saving.value = true
  try {
    // 支付明细 JSON 留档
    const payDetailJson = JSON.stringify(
      pay.value.rows.filter((r) => Number(r.amount) > 0)
        .map((r) => ({ channel: r.channel, account: r.account, serial: r.serial, amount: Number(r.amount) }))
    )
    const payInfoStr = pay.value.rows.filter((r) => Number(r.amount) > 0)
      .map((r) => `【${r.channel}】${r.amount}元`).join(' ') || '无'
    const totalDue = cards.value.reduce((a, c) => a + Number(cardFinal(c)), 0)
    const totalPaid = Number(receivedTotal.value)
    const coverage = totalDue > 0 ? Math.min(totalPaid / totalDue, 1) : 1

    // 1. 先删除本次移除的报名记录（删除先行，同课程重新订阅才不会被判重拦截）
    const delResults = await Promise.allSettled(removedEnrollIds.value.map((id) => api.enrollDel(id)))
    const delOk = delResults.filter((r) => r.status === 'fulfilled').length
    const delFail = delResults.length - delOk
    // 删除成功的不再重试；失败的保留在列表中以便修正后重试
    removedEnrollIds.value = removedEnrollIds.value.filter(
      (_, i) => delResults[i].status !== 'fulfilled')

    // 2. 更新已有报名单（有 __editingId）+ 新增课程（无 __editingId 走 subscribe）
    let allocated = 0
    const jobs = cards.value.map((card, i) => () => {
      const discTotal = cardDiscount(card)
      let paid = Math.round(Number(cardFinal(card)) * coverage * 100) / 100
      if (i === cards.value.length - 1) paid = Math.round((totalPaid - allocated) * 100) / 100
      else allocated += paid

      // 无课程的套餐报名单（courseId null/undefined）仅更新经办信息，不传课程相关字段
      const hasCourse = !!card.courseId
      if (card.__editingId) {
        // 更新已有报名单：未改定价/数量的卡片不传 priceId/qty（避免后端按定价行重算覆盖记录金额）
        const reprice = hasCourse && card.customized
        return api.enrollUpdate({
          id: card.__editingId,
          courseId: hasCourse ? card.courseId : undefined,
          campus: hasCourse ? (card.campus || undefined) : undefined,
          clazzId: card.clazzId || undefined,
          teacherId: card.teacherId || undefined,
          priceId: reprice ? (card.priceId || undefined) : undefined,
          qty: reprice ? (card.qty || 1) : undefined,
          // 编辑卡显式传优惠（0=清除），后端以记录原价为基准重算应收；明细JSON留档
          discountAmount: Number(discTotal),
          discountDetail: discountDetailJson(card),
          paidAmount: paid > 0 ? paid : 0,
          payDetail: payDetailJson,
          remark: card.remark || undefined,
          source: handle.value.source || undefined,
          orderTaker: handle.value.orderTaker || undefined,
          consultant: handle.value.consultant || undefined,
          validDate: card.validEnabled ? (card.validDate || undefined) : undefined,
          handleDate: handle.value.date || undefined,
          status: 1,
          payInfo: payInfoStr
        })
      }
      if (hasCourse) {
        // 新增课程 → 走 subscribe（无课程的卡片跳过）
        return api.enrollSubscribe({
          studentId: studentId.value, courseId: card.courseId,
          campus: card.campus || undefined,
          clazzId: card.clazzId || undefined, teacherId: card.teacherId || undefined,
          priceId: card.priceId || undefined, qty: card.qty || 1,
          discountAmount: Number(discTotal) > 0 ? Number(discTotal) : undefined,
          discountDetail: discountDetailJson(card),
          paidAmount: paid > 0 ? paid : undefined,
          validDate: card.validEnabled ? (card.validDate || undefined) : undefined,
          remark: card.remark || undefined,
          source: handle.value.source || undefined,
          handler: handle.value.handler || undefined,
          handleDate: handle.value.date || undefined,
          orderTaker: handle.value.orderTaker || undefined,
          consultant: handle.value.consultant || undefined,
          payInfo: payInfoStr,
          payDetail: payDetailJson
        })
      }
      return Promise.resolve()
    })
    const results = await Promise.allSettled(jobs.map((j) => j()))
    const ok = results.filter((r) => r.status === 'fulfilled').length
    const fail = results.length - ok + delFail

    if (fail === 0) {
      const delTxt = delOk > 0 ? `，移除报名 ${delOk} 条` : ''
      ElMessage.success(`已保存 ${ok} 门课程${delTxt}`)
      editingEnrollId.value = null
      removedEnrollIds.value = []
      mode.value = 'list'
      loadList()
    } else {
      // 失败的卡片保留在页面（已保存的不再重试），修正后可再次点击保存
      const keep = []
      results.forEach((r, i) => { if (r.status !== 'fulfilled') keep.push(cards.value[i]) })
      cards.value = keep
      ElMessage.warning(`保存完成 ${ok} 项，失败 ${fail} 项（失败项已保留在页面，请调整后重试）`)
      loadSubscribed()
    }
  } finally { saving.value = false }
}

/* ===== 经办信息：来源/日期/经办人/采单员/咨询顾问 ===== */
const STAFF_TYPE_NAMES = { 2: '教务人员', 3: '班主任', 6: '校区校长', 7: '校区咨询师', 8: '校区学管师' }
const sourcePresets = ['转介绍', '线上咨询', '电话咨询', '门店到访', '活动推广', '老客户续费']
const staffOptions = ref([])
const handle = ref({
  source: null,
  date: new Date().toISOString().slice(0, 10),
  handler: userStore.user?.realName || '',
  orderTaker: null,
  consultant: null
})

/* 经办信息必填校验：未通过时对应控件红框标红（持续可见），补填后自动消除 */
const handleErrs = reactive({ source: false, orderTaker: false, consultant: false })
const rechargeErrs = reactive({ source: false, orderTaker: false, consultant: false })
watch(() => handle.value.source, (v) => { if (v) handleErrs.source = false })
watch(() => handle.value.consultant, (v) => { if (v) handleErrs.consultant = false })
watch(() => recharge.value.source, (v) => { if (v) rechargeErrs.source = false })
watch(() => recharge.value.consultant, (v) => { if (v) rechargeErrs.consultant = false })
/** 校验销售来源/咨询顾问：未填项标红并逐项弹提示，全部通过返回 true（采单员已下线） */
function validateHandle(h, errs) {
  errs.source = !h.source
  errs.consultant = !h.consultant
  if (!h.source) { ElMessage.warning('请在「经办信息」中填写销售来源'); return false }
  if (!h.consultant) { ElMessage.warning('请在「经办信息」中选择咨询顾问'); return false }
  return true
}

/** 必填项缺失统一提示：alert 弹窗逐条列出 + toast 兜底（双保险，确保用户一定能看到） */
function requiredAlert(items) {
  ElMessage.warning(`请录入必填项目：${items.join('；')}`)
  ElMessageBox.alert(
    `<div style="text-align: left; line-height: 2; font-size: 13.5px; color: #344054;">${
      items.map((i) => `<div>· 请录入${i}</div>`).join('')
    }</div>`,
    '请录入必填项目',
    { confirmButtonText: '去录入', type: 'warning', dangerouslyUseHTMLString: true }
  ).catch(() => {})
}

/* ===== 学员信息 ===== */
const students = ref([])
const searching = ref(false)
const studentId = ref(null)
const picked = ref(null)

/** 学员候选项统一映射（下拉选中即用，picked 直接取这些字段） */
const mapStudents = (all) => (all || []).map((s) => ({
  studentId: s.id, name: s.name, phone: s.phone,
  grade: s.grade, school: s.school, idCard: s.idCard
}))
async function searchStudents(kw) {
  if (!kw || !kw.trim()) { students.value = []; return }
  searching.value = true
  try {
    const all = (await api.studentList()) || []
    const k = kw.trim().toLowerCase()
    // s.phone 为后端拼好的有效联系电话（家长电话→备用电话→账号手机号），
    // 用户管理补录手机号后搜索即同步显示，不再出现"无手机号"
    students.value = mapStudents(all.filter((s) =>
      (s.name || '').toLowerCase().includes(k) || (s.phone || '').includes(k)))
  } finally { searching.value = false }
}
/** 双击搜索框：无关键字全量检索，下拉直接展示全部学员 */
async function loadAllStudents() {
  if (searching.value) return
  searching.value = true
  try { students.value = mapStudents(await api.studentList()) }
  finally { searching.value = false }
}
function onPick(id) {
  picked.value = students.value.find((s) => s.studentId === id) || null
  studentId.value = id
  if (id) { loadSubscribed(); loadBalance() }
  // 转课/退费页：选中学员后加载其在读报名单（转出/退出候选）
  if (id && mode.value === 'transfer') loadTransferFroms()
  if (id && mode.value === 'refund') loadRefundRows()
  // 积分页：选中学员后加载积分余额
  if (id && mode.value === 'score') loadScoreSummary()
  // 补费页：选中学员后加载欠费记录
  if (id && mode.value === 'supplement') loadSupplementRows()
}

/* 学员账户余额（支付信息-使用余额上限） */
const balanceAvail = ref(0)
async function loadBalance() {
  try {
    const s = await api.accountSummary({ studentId: studentId.value })
    balanceAvail.value = Number(s?.balanceTotal || 0)
  } catch (e) { balanceAvail.value = 0 }
}

/* 该学员在读课程集合（选课弹窗置灰用）+ 学员信息条：按校区分组的在读课程（每行=校区+课程） */
const subscribedIds = ref(new Set())
const subInfo = ref({ groups: [] })
/** 校区行课程文案：≤2 门直接展示，>2 门前两门 +「等N门」悬停看全部 */
const pbCoursesText = (g) => g.courses.length <= 2
  ? (g.courses.join('、') || '—')
  : `${g.courses.slice(0, 2).join('、')} 等${g.courses.length}门`
async function loadSubscribed() {
  try {
    const en = (await api.enrollPage({ page: 1, size: 100, studentId: studentId.value })).records || []
    const active = en.filter((e) => e.status === 1)
    // 在读判重按 课程+校区（同一课程不同校区可各报一条）
    subscribedIds.value = new Set(active.filter((e) => e.courseId)
      .map((e) => e.courseId + '|' + (e.campus || '')))
    // 按校区分组：每校区一行，行内为该校区在读课程名（courseMap 取中文，按课程去重）
    const gmap = new Map()
    for (const e of active) {
      const campus = String(e.campus || '').trim() || '未分校区'
      if (!gmap.has(campus)) gmap.set(campus, new Set())
      if (e.courseId) {
        const n = courseName(e.courseId)
        if (n && n !== '-') gmap.get(campus).add(n)
      }
    }
    subInfo.value = { groups: [...gmap].map(([campus, set]) => ({ campus, courses: [...set] })) }
  } catch (e) {
    subscribedIds.value = new Set()
    subInfo.value = { groups: [] }
  }
}
/** 欠费对报名单/教材杂费/补费单有意义；充值/转课/退费/积分流水无欠费概念 */
const rowDue = (row) => row.kind && !['enroll', 'material', 'supplement'].includes(row.kind)
  ? 0 : Math.max(Number(row.totalAmount || 0) - Number(row.paidAmount || 0), 0)

/* ===== 基础数据 ===== */
const courses = ref([])
const clazzes = ref([])
const classrooms = ref([])   // 班级→教室→校区 映射用（开班数按校区统计）
const teachers = ref([])
const courseMap = ref({})
const courseName = (id) => courseMap.value[id] || '-'
const courseOf = (id) => courses.value.find((c) => c.id === id) || {}
const clazzesOf = (cid) => clazzes.value.filter((c) => c.courseId === cid)
/** 班级所属校区：经教室（edu_classroom.campus）解析，教室缺失时归入空校区 */
const classroomCampus = (id) => classrooms.value.find((r) => r.id === id)?.campus || ''
const clazzCampus = (c) => classroomCampus(c.classroomId)
/** 开班数：选了校区时只数该校区该课程的班级；未选校区为数全部校区合计 */
const clazzCount = (cid, campus) => clazzes.value.filter((c) =>
  c.courseId === cid && (!campus || clazzCampus(c) === campus)).length
const fmt = (n) => String(Number(n ?? 0))
const feeModeText = (c) => {
  const modes = []
  if (c.chargeHour === 1) modes.push('按课时')
  if (c.chargeTime === 1) modes.push('按时间')
  return modes.length ? modes.join(',') : '—'
}

/* ===== 课程卡片（多选，每卡独立配置） ===== */
const cards = ref([])
const saving = ref(false)
const discountTypes = ['学费优惠', '教材优惠', '活动优惠', '老带新优惠']
/** 卡片判重按 课程+校区（同一课程可在不同校区各报一条） */
const inCards = (cid, campus) => cards.value.some((c) => c.courseId === cid && (c.campus || null) === (campus || null))

async function addCard(courseId, origin = 'course', campus = null) {
  if (inCards(courseId, campus) || !courseId) return false
  // 卡片校区（一卡一校区）：显式指定优先（选课弹窗带入当前校区）；
  // 否则课程关联校区含当前弹窗校区时取之，再退回课程首个关联校区
  const cs = (courseOf(courseId).campuses || [])
  const cardCampus = campus || (cs.includes(dlgCampus.value) ? dlgCampus.value : (cs[0] || null))
  const card = {
    courseId, campus: cardCampus, clazzId: null, priceId: null, qty: 1, teacherId: null,
    validEnabled: false, validDate: null, remark: '',
    discounts: [], priceRows: [],
    /** 卡片来源：course=选择课程加入 / clazz=选择班级带出 */
    origin
  }
  cards.value.push(card)
  // 定价行加载（弹窗缓存优先；空数组=预载失败留痕，视为未命中重取）
  const cached = dlgPriceMap.value[courseId]
  if (Array.isArray(cached) && cached.length) {
    card.priceRows = pricesOfCard(cached, cardCampus)
    if (card.priceRows.length) card.priceId = card.priceRows[0].id
  } else {
    try {
      const ps = (await api.coursePrices(courseId)) || []
      dlgPriceMap.value[courseId] = ps
      card.priceRows = pricesOfCard(ps, cardCampus)
      if (card.priceRows.length) card.priceId = card.priceRows[0].id
    } catch (e) { /* 无定价按默认 */ }
  }
  return true
}

/** 卡片可用定价行：按课时 + 按时间收费 × 卡片校区（本校区行 + 全部校区通用行） */
const pricesOfCard = (ps, campus) => (ps || []).filter((p) =>
  (!campus || p.campus === campus || p.campus === '全部校区'))

/* 每卡价格计算：编辑卡片未改定价/数量前按记录实况显示（含套餐订阅），改后按所选定价行重算 */
const cardName = (card) => card.courseId ? courseName(card.courseId) : (card.pkgName || '套餐订阅')
const cardIsBase = (card) => !!card.__editingId && !card.customized
const cardSelectedPrice = (card) => card.priceRows.find((p) => p.id === card.priceId) || null
const cardSubtotal = (card) => {
  if (cardIsBase(card)) return Number(card.baseSubtotal || 0).toFixed(2)
  const q = Number(card.qty || 1)
  const p = cardSelectedPrice(card)
  if (p) return (Number(p.totalAmount) * q).toFixed(2)
  const c = courseOf(card.courseId)
  if (c.totalHours && c.price)
    return (Number(c.totalHours) * Number(c.price) * Number(c.discount || 100) / 100).toFixed(2)
  return '0.00'
}
const cardDiscount = (card) =>
  card.discounts.reduce((a, d) => a + Number(d.amount || 0), 0).toFixed(2)
/** 校区分组优惠总计：组内各课程卡片优惠合计之和 */
const groupDiscount = (grp) =>
  grp.cards.reduce((s, c) => s + Number(cardDiscount(c) || 0), 0).toFixed(2)
const cardFinal = (card) => {
  const v = Number(cardSubtotal(card)) - Number(cardDiscount(card))
  return (v > 0 ? v : 0).toFixed(2)
}
const cardHours = (card) => {
  if (cardIsBase(card)) return card.baseHours != null ? String(card.baseHours) : '-'
  const q = Number(card.qty || 1)
  const p = cardSelectedPrice(card)
  if (p) return fmt(Number(p.hours) * q)
  const c = courseOf(card.courseId)
  return c.totalHours ? String(c.totalHours) : '-'
}
/** 数量单位：选中按时间定价时为周期（天/周/月/季/年），否则为课时 */
const priceUnit = (card) => {
  const p = cardSelectedPrice(card)
  return p && p.chargeMode === 2 ? (p.timePeriod || '天') : '课时'
}
const totalHours = computed(() =>
  cards.value.reduce((a, c) => {
    const h = Number(cardHours(c))
    return a + (Number.isNaN(h) ? 0 : h)
  }, 0))
const totalAmount = computed(() =>
  cards.value.reduce((a, c) => a + Number(cardFinal(c)), 0).toFixed(2))

/* ===== 支付信息：应收/实收/欠费联动 ===== */
const pay = ref({ campus: null, balance: 0, rows: [] })
const firstChannel = computed(() => dictStore.options('pay_channel')[0]?.label || '现金')
/* 支付信息必填（报名/续报提交校验）：至少一条实收金额>0 的支付记录，缺失时账户列表红框 */
const payErr = ref(false)
const hasPayRow = computed(() => pay.value.rows.some((r) => Number(r.amount) > 0))
watch(hasPayRow, (v) => { if (v) payErr.value = false })
const channelTotal = computed(() =>
  pay.value.rows.reduce((a, p) => a + Number(p.amount || 0), 0).toFixed(2))
const receivedTotal = computed(() =>
  (Number(channelTotal.value) + Number(pay.value.balance || 0)).toFixed(2))
const arrears = computed(() => {
  const v = Number(totalAmount.value) - Number(receivedTotal.value)
  return (v > 0 ? v : 0).toFixed(2)
})
const arrearsNum = computed(() => Number(arrears.value))
/** 超收金额：实收超过应收的部分，确认后自动充入学员账户 */
const overpay = computed(() => {
  const v = Number(receivedTotal.value) - Number(totalAmount.value)
  return v > 0 ? Math.round(v * 100) / 100 : 0
})

/* ===== 选课弹窗（checkbox 多选；转课转入时单选） ===== */
const courseDialog = ref(false)
const courseTableRef = ref(null)
const courseKw = ref('')
const dlgCampus = ref(null)
const dlgTeachMode = ref(null)
const dlgFeeMode = ref(null)
const dlgCategory = ref(null)
/** 弹窗用途：form=报名多选加卡 / transfer=转课转入单选 */
const coursePurpose = ref('form')
/** 选课弹窗勾选（按校区分桶）：切换校区互不影响，同一课程可在不同校区各选一次 */
const dlgSelMap = ref({})
const dlgSelCount = computed(() =>
  Object.values(dlgSelMap.value).reduce((a, s) => a + (s ? s.size : 0), 0))
/** 勾选变化：记入当前校区桶（转课转入时单选，后选的顶掉先选的） */
function onCourseSelChange(rows) {
  if (dlgRestoring.value) return   // 程序勾选已订阅行期间不处理，避免污染选择桶
  const pickable = rows.filter((r) => canPickCourse(r))   // 已订阅行仅展示勾选态，不计入选择
  if (coursePurpose.value === 'transfer' && pickable.length > 1) {
    const keep = pickable[pickable.length - 1]
    courseTableRef.value?.clearSelection()
    nextTick(() => {
      courseTableRef.value?.toggleRowSelection(keep, true)
      checkSubscribedRows()
    })
    return
  }
  const set = dlgSelMap.value[dlgCampus.value] || (dlgSelMap.value[dlgCampus.value] = new Set())
  set.clear()
  pickable.forEach((r) => set.add(r.id))
}
/** 已订阅课程行勾为「选中+禁用」态（信息展示：当前学员已在该校区订阅此课） */
const dlgRestoring = ref(false)
function checkSubscribedRows() {
  dlgRestoring.value = true
  nextTick(() => {
    courseFiltered.value.forEach((row) => {
      if (!canPickCourse(row)) courseTableRef.value?.toggleRowSelection(row, true)
    })
    nextTick(() => { dlgRestoring.value = false })
  })
}
/** 切换校区：快照旧校区勾选 → 恢复新校区勾选（表格数据换批前快照，避免丢失） */
watch(dlgCampus, (nv, ov) => {
  if (!courseDialog.value || nv === ov) return
  const rows = courseTableRef.value?.getSelectionRows?.() || []
  const prevSet = dlgSelMap.value[ov] || (dlgSelMap.value[ov] = new Set())
  prevSet.clear()
  rows.filter((r) => canPickCourse(r)).forEach((r) => prevSet.add(r.id))
  const target = [...(dlgSelMap.value[nv] || [])]
  nextTick(() => {
    courseFiltered.value.forEach((row) => {
      if (target.includes(row.id)) courseTableRef.value?.toggleRowSelection(row, true)
    })
    checkSubscribedRows()
  })
})
/** 该学员已订阅统计（弹窗底部汇总）：总数 + 各校区订阅课程名 */
const dlgSubGroups = computed(() => {
  const byCampus = new Map()
  for (const key of subscribedIds.value) {
    const [cid, campus] = String(key).split('|')
    const name = courseName(Number(cid))
    if (!name || name === '-') continue
    const c = campus || '未分校区'
    if (!byCampus.has(c)) byCampus.set(c, [])
    byCampus.get(c).push(name)
  }
  const list = [...byCampus].map(([campus, names]) => ({ campus, names }))
  return { total: list.reduce((a, g) => a + g.names.length, 0), list }
})
const campusOptions = computed(() =>
  dictStore.options('campus').map((d) => d.label).filter((l) => l !== '全部校区'))
/** 报名信息按校区分组呈现（一卡一校区一条报课信息记录）：
 *  卡片校区来自中间表关联校区（选择课程时按弹窗校区带入，报名单落库 edu_enroll.campus）；
 *  每条报课信息只归入一个校区组；套餐订阅单独成组；合计/分摊仍按 cards 计一次 */
const campusGroups = computed(() => {
  const all = campusOptions.value
  const groups = []
  const groupOf = (name) => {
    let g = groups.find((x) => x.name === name)
    if (!g) { g = { name, cards: [] }; groups.push(g) }
    return g
  }
  for (const card of cards.value) {
    groupOf(!card.courseId ? '套餐订阅' : (card.campus || '未设置校区')).cards.push(card)
  }
  // 组排序：按校区字典顺序在前，其余（套餐/未设置）在后
  groups.sort((a, b) => {
    const ia = all.indexOf(a.name), ib = all.indexOf(b.name)
    return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib)
  })
  return groups
})
const dlgPriceMap = ref({})
const courseFiltered = computed(() => {
  const k = (courseKw.value || '').trim().toLowerCase()
  return courses.value.filter((c) => {
    if (c.status !== 1) return false
    if (k && !(c.name || '').toLowerCase().includes(k)
      && !(dictStore.label('course_category', c.category) || '').toLowerCase().includes(k)) return false
    if (dlgTeachMode.value != null && c.teachMode !== dlgTeachMode.value) return false
    if (dlgCategory.value != null && c.category !== dlgCategory.value) return false
    if (dlgCampus.value && !(c.campuses || []).includes(dlgCampus.value)) return false
    if (dlgFeeMode.value === 'hour' && c.chargeHour !== 1) return false
    if (dlgFeeMode.value === 'time' && c.chargeTime !== 1) return false
    return true
  })
})
const canPickCourse = (c) => editingEnrollId.value != null
  || !subscribedIds.value.has(c.id + '|' + (dlgCampus.value || ''))
/** 定价行展示：按课时「X课时=Y元」；按时间「X天/周/月=Y元」（chargeMode 2） */
const priceLabel = (p) => p.chargeMode === 2
  ? `${fmt(p.hours)}${p.timePeriod || '天'}=${fmt(p.totalAmount)}元`
  : `${fmt(p.hours)}课时=${fmt(p.totalAmount)}元`
const feeStdText = (c) => {
  const ps = dlgPriceMap.value[c.id] || []
  const p = ps.find((x) => x.chargeMode === 1) || ps.find((x) => x.chargeMode === 2)
  return p ? priceLabel(p) : '—'
}
function openCourseDialog(purpose = 'form') {
  coursePurpose.value = purpose
  // 学员未选时先提示（选课判重/订阅都依赖学员）
  if (!picked.value) { ElMessage.warning('请先在「学员信息」中选择学员'); return }
  courseKw.value = ''
  // 校区必选：保持已选校区，未选时默认第一个校区（不允许为空/全部校区）
  dlgCampus.value = dlgCampus.value || campusOptions.value[0] || null
  dlgTeachMode.value = null
  dlgFeeMode.value = null; dlgCategory.value = null
  dlgSelMap.value = {}
  courseDialog.value = true
  nextTick(() => checkSubscribedRows())   // 已订阅课程勾为「选中+禁用」展示态
  // 预载在售课程定价（勾选不回显，由用户按需自行选择）；
  // 失败不写缓存（留 undefined），下次打开/选课会重试，避免空数组被当成有效缓存
  courses.value.filter((c) => c.status === 1 && dlgPriceMap.value[c.id] === undefined)
    .forEach((c) => {
      api.coursePrices(c.id)
        .then((ps) => { dlgPriceMap.value[c.id] = ps || [] })
        .catch(() => { /* 不缓存失败，后续按需重取 */ })
    })
}
async function confirmCourseDialog() {
  // 转课转入：取唯一勾选课程（单选），带出校区与定价
  if (coursePurpose.value === 'transfer') {
    const entry = Object.entries(dlgSelMap.value).find(([, ids]) => ids && ids.size)
    if (!entry) { ElMessage.warning('请勾选要转入的课程'); return }
    const campus = entry[0]
    const cid = [...entry[1]][0]
    await setTransferIn(cid, campus, null)
    courseDialog.value = false
    ElMessage.success(`已选择转入课程：${courseName(cid)}（${campus}）`)
    return
  }
  // 汇总各校区勾选：同一课程在不同校区各生成一条卡片
  let n = 0
  for (const [campus, ids] of Object.entries(dlgSelMap.value)) {
    for (const cid of ids) { if (await addCard(cid, 'course', campus)) n++ }
  }
  courseDialog.value = false
  if (n) ElMessage.success(`已添加 ${n} 门课程`)
}

/* ===== 选班弹窗（checkbox 多选，归到对应课程卡片） ===== */
const clazzDialog = ref(false)
const clazzTableRef = ref(null)
const clazzKw = ref('')
const selClazzIds = ref([])
const occupyMap = ref({})
const clazzFilteredAll = computed(() => {
  const k = (clazzKw.value || '').trim().toLowerCase()
  return clazzes.value.filter((c) => !k || (c.name || '').toLowerCase().includes(k))
})
const occupied = (cid) => occupyMap.value[cid] ?? '-'
const isFull = (c) => occupyMap.value[c.id] != null && Number(occupyMap.value[c.id]) >= Number(c.capacity || 0)
const isNew = (c) => {
  if (!c.startDate) return false
  return (Date.now() - new Date(c.startDate).getTime()) / 86400000 <= 30
}
/** 弹窗用途：form=报名多选归卡 / transfer=转课转入单选 */
const clazzPurpose = ref('form')
function openClazzDialog(purpose = 'form') {
  clazzPurpose.value = purpose
  // 学员未选时先提示（班级订阅依赖学员）
  if (!picked.value) { ElMessage.warning('请先在「学员信息」中选择学员'); return }
  clazzKw.value = ''
  selClazzIds.value = []
  clazzDialog.value = true
  clazzFilteredAll.value.forEach((c) => {
    if (occupyMap.value[c.id] === undefined) {
      api.clazzStudents(c.id)
        .then((d) => { occupyMap.value[c.id] = d?.studying ?? 0 })
        .catch(() => { occupyMap.value[c.id] = 0 })
    }
  })
  if (clazzPurpose.value === 'transfer') {
    // 班级表带 reserve-selection，跨开窗会保留勾选：转课单选进入前清空
    nextTick(() => clazzTableRef.value?.clearSelection())
    return
  }
  nextTick(() => {
    clazzFilteredAll.value.forEach((row) => {
      const card = cards.value.find((c) => c.clazzId === row.id)
      if (card) clazzTableRef.value?.toggleRowSelection(row, true)
    })
  })
}
async function confirmClazzDialog() {
  // 转课转入：取第一个勾选班级（单选），带出课程/校区与定价
  if (clazzPurpose.value === 'transfer') {
    const id = selClazzIds.value[0]
    if (!id) { ElMessage.warning('请勾选要转入的班级'); return }
    if (selClazzIds.value.length > 1) ElMessage.info('已按第一个勾选班级办理')
    const cl = clazzes.value.find((c) => c.id === id)
    if (!cl) return
    const cs = courseOf(cl.courseId).campuses || []
    await setTransferIn(cl.courseId, cs[0] || null, id)
    clazzDialog.value = false
    ElMessage.success(`已选择转入班级：${cl.name}（${courseName(cl.courseId)}）`)
    return
  }
  for (const cl of clazzes.value.filter((c) => selClazzIds.value.includes(c.id))) {
    let card = cards.value.find((c) => c.courseId === cl.courseId)
    if (!card) { await addCard(cl.courseId, 'clazz'); card = cards.value.find((c) => c.courseId === cl.courseId) }
    if (card) card.clazzId = cl.id
  }
  clazzDialog.value = false
  ElMessage.success('班级已关联到对应课程卡片')
}

/* ===== 批量订阅（实收按比例分摊到各课程） ===== */
async function subscribeAll() {
  if (saving.value) return // 重复提交防护：提交中忽略再次点击
  // 前置校验：学员/课程单独弹窗；经办信息与支付信息缺失项汇总成一个 alert 弹窗逐条列出
  if (!picked.value) return requiredAlert(['学员（在「学员信息」中选择学员）'])
  if (!cards.value.length) return requiredAlert(['报名课程（通过「选择课程」或「选择班级」添加）'])
  const missing = []
  handleErrs.source = !handle.value.source
  handleErrs.orderTaker = !handle.value.orderTaker
  handleErrs.consultant = !handle.value.consultant
  if (!handle.value.source) missing.push('销售来源（经办信息）')
  if (!handle.value.consultant) missing.push('咨询顾问（经办信息）')
  payErr.value = !hasPayRow.value
  if (!hasPayRow.value) missing.push('支付信息（至少一条实收金额大于 0 的支付记录）')
  if (missing.length) return requiredAlert(missing)
  // 超收拦截：实收不能超过应收（预存请走「余额充值」），不再自动充入账户产生充值流水
  if (overpay.value > 0)
    return ElMessage.warning(`实收金额（¥${receivedTotal.value}）超过应收（¥${totalAmount.value}）¥${overpay.value.toFixed(2)}，请调整支付金额；预存款项请走「余额充值」办理`)
  saving.value = true
  try {
    const due = Number(totalAmount.value)
    const received = Number(receivedTotal.value)
    const coverage = due > 0 ? Math.min(received / due, 1) : 1
    let allocated = 0
    const payDetailJson = JSON.stringify(
      pay.value.rows.filter((r) => Number(r.amount) > 0)
        .map((r) => ({ channel: r.channel, account: r.account, serial: r.serial, amount: Number(r.amount) }))
    )
    const payInfoStr = pay.value.rows.filter((r) => Number(r.amount) > 0)
      .map((r) => `【${r.channel}】${r.amount}元`).join(' ') || '无'
    const jobs = cards.value.map((card, i) => () => {
      // 实收分摊：按各卡小计占比，末卡补齐尾差
      let paid = Math.round(Number(cardFinal(card)) * coverage * 100) / 100
      if (i === cards.value.length - 1) paid = Math.round((received - allocated) * 100) / 100
      else allocated += paid
      return api.enrollSubscribe({
        studentId: studentId.value, courseId: card.courseId,
        campus: card.campus || undefined,
        clazzId: card.clazzId || undefined, teacherId: card.teacherId || undefined,
        priceId: card.priceId || undefined, qty: card.qty || 1,
        discountAmount: Number(cardDiscount(card)) > 0 ? Number(cardDiscount(card)) : undefined,
        discountDetail: discountDetailJson(card),
        paidAmount: paid > 0 ? paid : undefined,
        validDate: card.validEnabled ? (card.validDate || undefined) : undefined,
        remark: card.remark || undefined,
        source: handle.value.source || undefined,
        handler: handle.value.handler || undefined,
        handleDate: handle.value.date || undefined,
        orderTaker: handle.value.orderTaker || undefined,
        consultant: handle.value.consultant || undefined,
        payInfo: payInfoStr,
        payDetail: payDetailJson
      })
    })
    const results = await Promise.allSettled(jobs.map((j) => j()))
    const ok = results.filter((r) => r.status === 'fulfilled').length
    const fail = results.length - ok
    // 移除成功的卡片
    const keep = []
    results.forEach((r, i) => { if (r.status !== 'fulfilled') keep.push(cards.value[i]) })
    cards.value = keep
    if (fail === 0) {
      ElMessage.success(`报名成功：${ok} 门课程`)
      pay.value = { campus: pay.value.campus, balance: 0, rows: [] }
      // 全部成功：回到列表页并刷新
      mode.value = 'list'
      loadList()
    } else {
      // 拼上首个失败原因（如"该学生已订阅此课程"），不再只报数量
      const reason = results.find((r) => r.status === 'rejected')?.reason?.message
      ElMessage.warning(`成功 ${ok} 门，失败 ${fail} 门${reason ? `：${reason}` : ''}（失败卡片已保留，请调整后重试）`)
      loadSubscribed()
    }
  } catch (e) {
    // 兜底：任何未预料的异常都给出提示（并输出到控制台便于排查），避免按钮一直转圈无响应
    console.error('[subscribeAll] 确认订阅异常', e)
    ElMessage.error(`确认订阅出现异常：${e?.message || e}`)
  } finally {
    saving.value = false
  }
}

/* ===== 创建学员（类型锁定5，字段与用户管理一致） ===== */
const createVisible = ref(false)
const creating = ref(false)
const createForm = ref({})
const createPhoto = ref(null)
const createPhotoPreview = ref('')
const orgOptions = ref([])

function openCreate() {
  createForm.value = { username: '', realName: '', orgId: null, phone: '', email: '',
    birthDate: null, grade: null, idCard: '', school: '', parentName: '', parentPhone: '',
    backupPhone: '', advisor: '', tags: '', address: '' }
  createPhoto.value = null
  createPhotoPreview.value = ''
  createVisible.value = true
}
function onCreatePhotoSelected({ file, previewUrl }) {
  createPhoto.value = file
  createPhotoPreview.value = previewUrl
}

async function createStudent() {
  const f = createForm.value
  if (!f.username || !f.realName) return ElMessage.warning('用户名与姓名为必填项')
  creating.value = true
  try {
    const r = await api.userSave({ ...f, userType: 5, status: 1 })
    const created = r || {}
    if (createPhoto.value && created.id) {
      try {
        const fd = new FormData()
        fd.append('file', createPhoto.value)
        await api.userAvatarUpload(created.id, fd)
      } catch (e) { /* 照片失败不阻塞 */ }
    }
    students.value = [{ studentId: created.id, name: f.realName, phone: f.parentPhone || f.phone,
      grade: f.grade, school: f.school, idCard: f.idCard }]
    onPick(created.id)
    createVisible.value = false
    ElMessage.success('学员已创建并选中')
  } finally { creating.value = false }
}

onMounted(async () => {
  dictStore.load()
  loadList()
  courses.value = (await api.courseList()) || []
  courses.value.forEach((c) => (courseMap.value[c.id] = c.name))
  clazzes.value = (await api.clazzList()) || []
  try { classrooms.value = (await api.classroomList()) || [] } catch { classrooms.value = [] }
  teachers.value = (await api.teacherList()) || []
  // 经办信息人员候选：用户管理中员工类型在职账号
  try {
    const cands = (await api.contractAdvisorCandidates()) || []
    staffOptions.value = cands
      .filter((a) => STAFF_TYPE_NAMES[a.userType])
      .map((a) => ({ name: a.name, typeName: STAFF_TYPE_NAMES[a.userType] }))
  } catch (e) { staffOptions.value = [] }
  const tree = (await api.orgTree()) || []
  const flat = {}
  const walk = (nodes, parentName) => nodes.forEach((n) => {
    flat[n.id] = parentName ? `${parentName}/${n.name}` : n.name
    walk(n.children || [], parentName || n.name)
  })
  walk(tree, '')
  orgOptions.value = Object.keys(flat).map((id) => ({ value: Number(id), label: flat[id] }))
  // 报名管理「转课」跳转带参：预进转课页 + 预选学员 + 定位转出报名单
  if (route.query.mode === 'transfer' && route.query.studentId) {
    enterTransfer()
    const sid = Number(route.query.studentId)
    const stu = students.value.find((s) => s.studentId === sid)
      || { studentId: sid, name: stuMaps.value[sid] || `学员#${sid}` }
    students.value = [stu, ...students.value.filter((s) => s.studentId !== sid)]
    await onPick(sid)
    if (route.query.enrollId) transferFilter.value = Number(route.query.enrollId)
  }
})
</script>

<style scoped>
/* 分区面板：标题 + 右侧操作 */
.sec-panel { padding: 14px 16px; margin-bottom: 14px; }
.sec-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.sec-title {
  font-size: 14.5px; font-weight: 600; color: #344054;
  padding-left: 8px; border-left: 3px solid #155EEF;
}
.stu-bar { display: flex; gap: 10px; align-items: center; }
/* 选中学员信息条 */
.picked-bar {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap; row-gap: 6px;
  background: #f5f9ff; border: 1px solid #dbe7fb; border-radius: 10px;
  padding: 8px 14px; font-size: 13px; color: #667c99;
}
.picked-bar b { color: #155EEF; font-size: 14px; }
.picked-bar span { color: #8fa8c4; }
/* 积分页学员信息扩展：竖分隔线 + 课/校 徽标 */
.picked-bar .pb-dash { width: 1px; height: 13px; background: #d5e3f4; padding: 0; flex-shrink: 0; }
.picked-bar .pb-ext { display: inline-flex; align-items: center; gap: 5px; white-space: nowrap; }
/* 学员信息条分行版：首行身份，其后每校区一行（校区名+该校区课程） */
.picked-bar.pb-block { flex-direction: column; align-items: flex-start; gap: 5px; }
.picked-bar .pb-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; min-height: 20px; }
.picked-bar .pb-campus-row .pb-campus { color: #344054; font-size: 13px; }
.picked-bar .pb-campus-row > span:not(.pb-dash):not(.pb-ext-hover) { color: #475467; font-size: 12.5px; }
/* 多课程/多校区摘要：可悬停样式 + 弹层明细 */
.picked-bar .pb-ext-hover { cursor: pointer; border-bottom: 1px dashed #b9d0f7; }
.pb-list-tip { max-width: 260px; }
.pb-tip-title { font-size: 12px; font-weight: 600; color: #344054; margin-bottom: 4px; }
.pb-tip-row { font-size: 12.5px; color: #475467; line-height: 1.7; }
.texttag {
  font-style: normal; flex-shrink: 0;
  display: inline-flex; align-items: center; justify-content: center;
  width: 18px; height: 18px; border-radius: 4px;
  font-size: 12px; font-weight: 700; line-height: 1; color: #fff;
}
/* 实底白字渐变章（同 sel-tag 风格），浅色描边底对比不足改为深底 */
.tt-course { background: linear-gradient(135deg, #0f766e, #10a393); }
.tt-campus { background: linear-gradient(135deg, #6d28d9, #8b5cf6); }
/* 未选课程空态 */
.ep-empty {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 26px 0 20px; color: #8fa8c4; font-size: 13px;
  border: 1px dashed #dbe7f5; border-radius: 10px; background: #fafcff;
}
.ep-empty p { margin: 0; color: #475467; font-weight: 600; }
/* 课程卡片 */
.sel-card {
  border: 1px solid #dfeaf6; border-radius: 10px;
  background: linear-gradient(180deg, #f9fcff, #ffffff);
  padding: 10px 12px; margin-bottom: 10px;
}
.sel-line { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; row-gap: 6px; }
.sel-tag {
  flex-shrink: 0; width: 24px; height: 24px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 700; color: #fff;
}
.tag-course { background: linear-gradient(135deg, #12b76a, #7ccf9e); }
.tag-clazz { background: linear-gradient(135deg, #155EEF, #5B8DEF); }
/* 报名信息按校区分组（每校区一条报课信息记录） */
.campus-group { margin-bottom: 2px; }
.campus-group-head { display: flex; align-items: center; gap: 8px; margin: 12px 0 8px; }
.campus-group-name { font-size: 13.5px; color: #344054; }
.campus-tag { background: linear-gradient(135deg, #155EEF, #5B8DEF); }
.sel-name { flex: 0 1 auto; min-width: 80px; font-size: 14px; color: #142a44; max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 来源印章：关闭按钮左侧的毛笔风「课/班」印章图标（课=绿，班=蓝），微倾 6° 呈盖章手感 */
.origin-seal { flex-shrink: 0; display: inline-flex; transform: rotate(-6deg); cursor: help; }
.origin-seal svg { display: block; }
.seal-course { color: #12b76a; }
.seal-clazz { color: #155EEF; }
.sel-sub { color: #8fa8c4; font-size: 12.5px; flex-shrink: 0; }
.sel-close { color: #b3c5da; cursor: pointer; font-size: 17px; }
.sel-close:hover { color: #f04438; }
/* 卡片头统计：右侧统一间距，金额等宽右对齐（tabular-nums）保证纵向可比 */
.sel-sum { margin-left: auto; margin-right: 14px; display: flex; gap: 16px; align-items: baseline; flex-shrink: 0; }
.sum-item { font-size: 12.5px; color: #667c99; white-space: nowrap; }
.sum-item b { font-size: 15px; color: #142a44; display: inline-block; min-width: 68px; text-align: right; font-variant-numeric: tabular-nums; }
.sum-money { color: #f79009 !important; }
/* 收费标准 + 优惠（左右分区） */
.fee-stand {
  border-top: 1px dashed #e4edf7; margin-top: 10px; padding-top: 10px;
  display: flex; gap: 20px; align-items: flex-start;
}
.fee-main { flex: 1; display: flex; flex-direction: column; gap: 10px; min-width: 0; }
.fee-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
/* 标签定宽 + 两端对齐（2~4字标签等宽），同行的第二组控件统一缩进 */
.fee-label { width: 56px; flex-shrink: 0; font-size: 12.5px; color: #8fa8c4; text-align: justify; text-align-last: justify; }
.fee-pair { margin-left: 24px; }
/* 第一列控件定宽槽：价格/收费模式/有效期的控件左边缘对齐，第二列（班级/数量/授课老师）由此同轴起始 */
.fee-ctrl1 { width: 250px; flex-shrink: 0; display: inline-flex; align-items: center; gap: 8px; min-width: 0; }
.fee-hint { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fee-value { font-size: 15px; color: #155EEF; font-weight: 700; }
.fee-value i { font-style: normal; font-size: 12px; color: #8fa8c4; font-weight: 400; margin-left: 2px; }
.fee-sub { font-size: 12px; color: #98a2b3; }
.fee-unit { font-size: 12px; color: #8fa8c4; }
.disc-box {
  width: 280px; flex-shrink: 0;
  border: 1px solid #e0eaf8; border-radius: 10px; background: #f9fcff;
  padding: 10px 12px;
}
.disc-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.disc-title { font-size: 13px; font-weight: 700; color: #344054; }
.disc-total { margin-left: auto; font-size: 12.5px; color: #667c99; }
.disc-total b { font-size: 15px; color: #12b76a; }
.disc-row { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.disc-del { color: #b3c5da; cursor: pointer; font-size: 16px; }
.disc-del:hover { color: #f04438; }
/* 合计条 */
.total-bar {
  display: flex; align-items: center; gap: 16px;
  background: #f5f9ff; border: 1px solid #dbe7fb; border-radius: 10px;
  padding: 8px 14px; font-size: 13px; color: #667c99;
}
.total-bar b { font-size: 16px; color: #142a44; }
.ep-submit { width: 100%; margin-top: 12px; letter-spacing: 4px; }
/* 支付信息 */
.pay-top { display: flex; align-items: center; gap: 16px; }
.pay-item { font-size: 13px; color: #667c99; }
.pay-item b { font-size: 15px; color: #142a44; }
.pay-due { font-size: 13px; color: #f04438; font-weight: 600; }
.pay-balance { display: flex; align-items: center; gap: 8px; margin: 10px 0; }
.credit-tag { font-weight: 600; }
.pay-table { border: 1px solid #e4edf7; border-radius: 10px; overflow: hidden; }
/* 支付信息必填校验未通过：账户列表整体红框（录入实收后自动消除） */
.pay-table.is-err-box { border-color: #f04438; box-shadow: 0 0 0 2px rgba(240, 68, 56, 0.25); }
.pay-row {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 12px; border-bottom: 1px solid #eef2f7;
}
.pay-row:last-of-type { border-bottom: none; }
.pay-head {
  background: #f5f8fc; font-size: 12.5px; color: #667c99; font-weight: 600;
  padding: 8px 12px;
}
.pay-row .w1 { width: 186px; flex-shrink: 0; }
.pay-row .w2 { width: 196px; flex-shrink: 0; }
.pay-row .w3 { width: 130px; flex-shrink: 0; }
.pay-row .w4 { flex: 1; text-align: center; }
.pay-del { color: #b3c5da; cursor: pointer; }
.pay-del:hover { color: #f04438; }
.pay-add {
  display: flex; align-items: center; justify-content: center; gap: 5px;
  color: #155EEF; font-size: 13px; cursor: pointer; padding: 8px 0;
  border-top: 1px dashed #e4edf7;
}
.pay-add:hover { background: #f5f9ff; }
.pay-total {
  display: flex; align-items: center; gap: 14px;
  margin-top: 10px; font-size: 14px; color: #344054; font-weight: 600;
}
.pay-total b { font-size: 17px; color: #f79009; }
/* 经办信息 */
.handle-box {
  display: flex; flex-wrap: wrap; gap: 10px 22px;
  border: 1px solid #e4edf7; border-radius: 10px; background: #fafcff;
  padding: 10px 14px; margin-top: 10px;
}
.handle-item { display: flex; align-items: center; gap: 6px; }
.handle-item label { font-size: 12.5px; color: #8fa8c4; flex-shrink: 0; }
/* 必填项标识：红色星号前缀 */
.handle-item label.req::before { content: '* '; color: #f04438; font-weight: 700; }
/* 必填校验未通过：下拉控件红框提示（补填后自动消除） */
:deep(.is-err .el-select__wrapper),
:deep(.is-err .el-input__wrapper) { box-shadow: 0 0 0 2px rgba(240, 68, 56, 0.4) !important; }
/* 余额充值：充值后余额高亮 + 备注整行 */
.re-balance { font-size: 14px; font-weight: 600; color: #f59e0b; font-variant-numeric: tabular-nums; }
.re-remark { width: 100%; align-items: flex-start; }
.re-remark label { line-height: 28px; }
/* 报名记录汇总与状态标签 */
.rec-summary { font-size: 12.5px; color: #667c99; }
/* 转课信息：转出课程 →（箭头）→ 转入课程 左右布局 */
.transfer-flow { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; }
.tf-side { flex: 1; min-width: 300px; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tf-side p { margin: 0; font-size: 13px; color: #344054; font-weight: 600; flex-shrink: 0; }
.tf-target { font-size: 14px; color: #155EEF; font-weight: 600; }
.tf-arrow { color: #155EEF; flex-shrink: 0; }
.rec-summary b { color: #142a44; }
/* 金额单元格：图标左对齐、数字右对齐 */
.nowrap { white-space: nowrap; }
.amt-cell { display: flex; align-items: center; gap: 8px; }
.amt-cell b { margin-left: auto; font-weight: 600; color: #142a44; }
/* “更多”触发器（参考班级/课时套餐） */
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
.more-trigger.open { border-color: rgba(91, 141, 239, 0.5); }
.more-arrow { transition: transform 0.2s ease; }
.more-trigger.open .more-arrow { transform: rotate(180deg); }
/* 列表页：办理入口瓷贴 */
.tile-panel { display: flex; gap: 18px; padding: 18px 20px; margin-bottom: 14px; }
.tile {
  width: 120px; padding: 14px 0 10px; border-radius: 12px; text-align: center;
  border: 1px solid #dbe7f5; background: linear-gradient(180deg, #ffffff, #f5f9ff);
  cursor: pointer; transition: all 0.18s ease;
}
.tile:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(21, 94, 239, 0.14); border-color: rgba(21, 94, 239, 0.4); }
.tile p { margin: 8px 0 0; font-size: 13.5px; font-weight: 600; color: #344054; }
.tile-icon {
  width: 52px; height: 52px; margin: 0 auto; border-radius: 14px;
  display: flex; align-items: center; justify-content: center; color: #fff;
}
.ti-enroll { background: linear-gradient(135deg, #155EEF, #5B8DEF); }
.ti-recharge { background: linear-gradient(135deg, #12b76a, #7ccf9e); }
.ti-pay { background: linear-gradient(135deg, #f79009, #fdb022); }
.ti-transfer { background: linear-gradient(135deg, #7a5af8, #a78bfa); }
.ti-refund { background: linear-gradient(135deg, #f04438, #f97066); }
.ti-fees { background: linear-gradient(135deg, #0e9384, #15b79e); }
.ti-score { background: linear-gradient(135deg, #dd2590, #f272a1); }
/* 积分页：剩余积分高亮 */
.score-amount { font-size: 18px; font-weight: 700; color: #dd2590; font-variant-numeric: tabular-nums; }
/* 列表页工具栏 */
.list-toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 10px; }
.pager-row { display: flex; justify-content: flex-end; margin-top: 8px; }
/* 业务凭证：表格化版式（单元格不折行） */
.voucher { border: 1px solid #d5dfe9; border-radius: 8px; padding: 16px 18px; background: #fff; }
.v-top { font-size: 19px; font-weight: 700; text-align: center; margin-bottom: 10px; color: #142a44; }
.vt { width: 100%; border-collapse: collapse; margin-bottom: 10px; table-layout: fixed; }
.vt th, .vt td { border: 1px solid #d5dfe9; padding: 6px 8px; font-size: 12.8px; color: #344054;
  text-align: center; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.vt thead th { background: #f5f8fc; font-weight: 600; color: #475467; }
.vt td.lbl { background: #f7fafd; font-weight: 600; color: #667c99; width: 11%; }
.vt td.tl { text-align: left; }
.vt tbody tr:hover td { background: #fafcff; }
.vt td.remark { color: #8fa8c4; font-size: 12px; text-align: left; white-space: normal; }
.vt td b { color: #142a44; }
.vt .sign-row td { height: 34px; }
.v-note { margin-top: 8px; font-size: 12px; color: #98a2b3; }
/* 操作日志弹窗 */
.log-head { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
/* 日志行首图标：与「更多→日志」菜单的 Document 图标同语言（新增=绿，修改=橙） */
.log-row-ico { vertical-align: -2px; margin-right: 5px; }
.log-title { font-size: 14px; font-weight: 600; color: #344054; }
.log-title b { color: #155EEF; }
.tag-in {
  display: inline-block; font-size: 11px; line-height: 1;
  padding: 2px 4px; border-radius: 4px; margin-right: 2px;
  color: #60bab2; border: 1px solid #60bab2; background: #ddf8ef;
}
/* 「退」标：与「收」标同字体同结构，浅红底红字 */
.tag-out {
  display: inline-block; font-size: 11px; line-height: 1;
  padding: 2px 4px; border-radius: 4px; margin-right: 2px;
  color: #f04438; border: 1px solid #f9a8a0; background: #feeceb;
}
.order-status {
  display: inline-block; font-size: 12px; width: 64px; text-align: center;
  padding: 2px 0; border-radius: 4px; color: #fff;
}
.st-done { background: #ffb51f; }
.st-owing { background: #f04438; }
/* 已确认到款（手动确认标记） */
.st-paid { background: #12b76a; }
/* 弹窗工具栏 */
.dlg-toolbar {
  display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
  margin-bottom: 10px;
}
.clazz-name { margin-right: 4px; }
.mini-tag {
  display: inline-block; font-size: 11px; line-height: 1;
  padding: 2px 5px; border-radius: 4px; vertical-align: middle;
}
.tag-full { color: #fa935a; border: 1px solid #f9d088; background: #ffeac5; }
.tag-new { color: #4689df; border: 1px solid #a8ceff; background: rgba(168, 206, 255, 0.15); }
.picked-clazz-bar {
  display: flex; align-items: center; gap: 10px;
  border-top: 1px dashed #e4edf7; margin-top: 10px; padding-top: 10px;
}
</style>
