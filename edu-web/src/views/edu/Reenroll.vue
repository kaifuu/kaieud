<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">{{ pageTitle }}</span>
      <div class="actions">
        <el-button v-if="mode !== 'list'" @click="backToList">← 返回列表</el-button>
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
        <div class="tile" @click="goTile('/payment?entry=补费')">
          <div class="tile-icon ti-pay"><el-icon :size="26"><Money /></el-icon></div>
          <p>补费</p>
        </div>
        <div class="tile" @click="enterTransfer">
          <div class="tile-icon ti-transfer"><el-icon :size="26"><Switch /></el-icon></div>
          <p>转课</p>
        </div>
        <div class="tile" @click="goTile(null, '退费')">
          <div class="tile-icon ti-refund"><el-icon :size="26"><RefreshLeft /></el-icon></div>
          <p>退费</p>
        </div>
        <div class="tile" @click="goTile(null, '教材杂费')">
          <div class="tile-icon ti-fees"><el-icon :size="26"><Reading /></el-icon></div>
          <p>教材杂费</p>
        </div>
        <div class="tile" @click="goTile(null, '积分')">
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
          当前结果：总计 <b>{{ filteredList.length }}</b> 条记录，
          应收款项总计 <b>¥{{ listSum.due }}</b> ｜ 实收总计 <b>¥{{ listSum.paid }}</b> ｜ 欠费总计 <b class="sum-money">¥{{ listSum.arrears }}</b>
        </div>
        <el-table :data="pagedList" size="small" stripe height="480" v-loading="listLoading">
          <el-table-column v-if="cols.id" prop="id" label="订单号" width="86" align="center" sortable fixed="left">
            <template #default="{ row }">EN{{ row.id }}</template>
          </el-table-column>
          <el-table-column v-if="cols.studentId" label="学员姓名" width="90" show-overflow-tooltip fixed="left">
            <template #default="{ row }">{{ stuName(row.studentId) }}</template>
          </el-table-column>
          <el-table-column v-if="cols.content" label="交易内容" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.packageName || courseName(row.courseId) }}，{{ row.totalHours }}课时×¥{{ row.price }}
              {{ row.discountAmount > 0 ? `，优惠¥${row.discountAmount}` : '' }}
            </template>
          </el-table-column>
          <el-table-column v-if="cols.totalAmount" label="应收" width="96" sortable>
            <template #default="{ row }">
              <div class="amt-cell"><span class="tag-in">收</span><b>{{ row.totalAmount }}</b></div>
            </template>
          </el-table-column>
          <el-table-column v-if="cols.paidAmount" label="实收" width="96" sortable>
            <template #default="{ row }">
              <div class="amt-cell"><span class="tag-in">收</span><b>{{ row.paidAmount ?? '0.00' }}</b></div>
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
              <el-tag size="small" :type="{ 1: 'success', 2: 'info', 3: 'danger', 4: 'warning' }[row.status]" effect="plain">
                {{ { 1: '在读', 2: '已结课', 3: '已退订', 4: '已转课' }[row.status] || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="cols.payStatus" label="到款状态" width="82" align="center" sortable
                           :sort-method="(a, b) => rowDue(a) - rowDue(b)">
            <template #default="{ row }">
              <span class="order-status" :class="rowDue(row) > 0 ? 'st-owing' : 'st-done'">
                {{ rowDue(row) > 0 ? '欠费' : '已完成' }}
              </span>
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
                    <el-dropdown-item command="view" class="more-item">
                      <span class="mi mi-green"><el-icon :size="13"><View /></el-icon></span>
                      <span class="mi-txt"><b>查看</b><i>业务凭证详情</i></span>
                    </el-dropdown-item>
                    <el-dropdown-item command="edit" class="more-item">
                      <span class="mi mi-purple"><el-icon :size="13"><EditPen /></el-icon></span>
                      <span class="mi-txt"><b>编辑</b><i>跳转填报页编辑</i></span>
                    </el-dropdown-item>
                    <el-dropdown-item command="print" class="more-item">
                      <span class="mi mi-blue"><el-icon :size="13"><Printer /></el-icon></span>
                      <span class="mi-txt"><b>打印</b><i>业务凭证打印</i></span>
                    </el-dropdown-item>
                    <el-dropdown-item command="log" class="more-item">
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
    <!-- 学员信息（报名/续报、余额充值 与 转课 共用） -->
    <div v-if="mode === 'form' || mode === 'recharge' || mode === 'transfer'" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">学员信息</span>
        <div class="stu-bar">
          <el-select v-model="studentId" filterable remote :remote-method="searchStudents" :loading="searching"
                     placeholder="输入学员姓名 / 手机号码进行搜索" clearable style="width: 280px" @change="onPick">
            <el-option v-for="s in students" :key="s.studentId" :label="`${s.name}（${s.phone || '无手机号'}）`" :value="s.studentId" />
          </el-select>
          <el-button type="primary" @click="openCreate">创建学员</el-button>
        </div>
      </div>
      <div v-if="picked" class="picked-bar">
        <el-icon color="#155EEF"><User /></el-icon>
        <b>{{ picked.name }}</b>
        <span v-if="picked.grade">{{ picked.grade }}</span>
        <span v-if="picked.school">{{ picked.school }}</span>
        <span v-if="picked.phone">{{ picked.phone }}</span>
        <el-tag v-if="mode === 'recharge' || mode === 'transfer'" size="small" type="warning" effect="light">
          账户余额：¥{{ (Number(balanceAvail) || 0).toFixed(2) }}
        </el-tag>
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
          <label>销售来源</label>
          <el-select v-model="recharge.source" filterable allow-create clearable size="small"
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
          <label>采单员</label>
          <el-select v-model="recharge.orderTaker" filterable clearable size="small"
                     placeholder="请选择采单员" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>咨询顾问</label>
          <el-select v-model="recharge.consultant" filterable clearable size="small"
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
                 :disabled="!picked" @click="submitRecharge">确 认 充 值</el-button>
    </div>

    <!-- ===== 转课页 ===== -->
    <!-- 转课信息：转出报名单 + 转入课程 -->
    <div v-if="mode === 'transfer'" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">转课信息</span></div>
      <div v-if="!picked" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><Switch /></el-icon>
        <p>请先在上方选择学员</p>
        <span>转课将该学员的一条在读报名单转为另一门课程，已付金额抵扣、多退少补</span>
      </div>
      <template v-else>
        <div class="handle-box">
          <div class="handle-item">
            <label>转出课程</label>
            <el-select v-model="transfer.fromId" placeholder="选择要转出的在读报名单" size="small"
                       style="width: 340px" :disabled="!transferFroms.length">
              <el-option v-for="f in transferFroms" :key="f.id" :value="f.id"
                         :label="`EN${f.id} ${f.packageName || courseName(f.courseId)}（${f.campus || '未分区'}）已付¥${f.paidAmount ?? '0.00'}`" />
            </el-select>
            <span v-if="!transferFroms.length" class="fee-sub">该学员暂无在读报名单，无法转课</span>
          </div>
        </div>
        <!-- 转出单实况 -->
        <div v-if="transferFrom" class="picked-bar" style="margin-top: 10px">
          <el-tag size="small" effect="plain" type="info">转出</el-tag>
          <b>{{ transferFrom.packageName || courseName(transferFrom.courseId) }}</b>
          <span>{{ transferFrom.campus || '未分区' }}</span>
          <span>{{ transferFrom.totalHours }}课时（已上{{ transferFrom.usedHours || 0 }}）</span>
          <span>应收 ¥{{ transferFrom.totalAmount }}</span>
          <span>已付 ¥{{ transferFrom.paidAmount ?? '0.00' }}</span>
          <span v-if="rowDue(transferFrom) > 0" class="pay-due">欠费 ¥{{ rowDue(transferFrom).toFixed(2) }}</span>
          <span class="fee-sub">本单已付金额将抵扣转入课程</span>
        </div>
        <!-- 转入课程配置 -->
        <div class="handle-box" style="margin-top: 10px">
          <div class="handle-item">
            <label>转入课程</label>
            <el-select v-model="transfer.courseId" filterable size="small" placeholder="选择课程"
                       style="width: 220px" @change="onTransferCourse">
              <el-option v-for="c in transferCourses" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </div>
          <div class="handle-item">
            <label>校区</label>
            <el-select v-model="transfer.campus" size="small" placeholder="选择校区" style="width: 160px">
              <el-option v-for="cs in transferCampuses" :key="cs" :label="cs" :value="cs" />
            </el-select>
          </div>
          <div class="handle-item">
            <label>定价标准</label>
            <el-select v-if="transferPrices.length" v-model="transfer.priceId" size="small"
                       style="width: 180px" placeholder="选择定价" @change="transfer.qty = 1">
              <el-option v-for="p in transferPrices" :key="p.id" :value="p.id"
                         :label="`${fmt(p.hours)}课时=${fmt(p.totalAmount)}元`" />
            </el-select>
            <span v-else class="fee-sub">未配置课时定价，按课程默认价</span>
          </div>
          <div class="handle-item" v-if="transferPrices.length">
            <label>数量</label>
            <el-input-number v-model="transfer.qty" :min="1" :max="99" :precision="0" size="small"
                             controls-position="right" style="width: 92px" />
          </div>
          <div class="handle-item">
            <label>优惠</label>
            <el-input-number v-model="transfer.discount" :min="0" :max="999999.99" :precision="2"
                             size="small" controls-position="right" style="width: 110px" placeholder="金额" />
            <span class="fee-unit">元</span>
          </div>
          <div class="handle-item">
            <label>班级</label>
            <el-select v-model="transfer.clazzId" filterable clearable size="small"
                       placeholder="可不选" style="width: 180px">
              <el-option v-for="c in clazzesOf(transfer.courseId)" :key="c.id"
                         :label="`${c.name}（${c.capacity}名）`" :value="c.id" />
            </el-select>
          </div>
          <div class="handle-item">
            <label>授课老师</label>
            <el-select v-model="transfer.teacherId" filterable clearable size="small"
                       placeholder="可后指派" style="width: 160px">
              <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
            </el-select>
          </div>
          <div class="handle-item">
            <label>有效期</label>
            <el-checkbox v-model="transfer.validEnabled" size="small" />
            <el-date-picker v-model="transfer.validDate" type="date" value-format="YYYY-MM-DD"
                            size="small" :disabled="!transfer.validEnabled"
                            placeholder="选择有效期" style="width: 160px" />
          </div>
        </div>
        <!-- 差价汇总：转入应收 − 原单抵扣 -->
        <div v-if="transfer.courseId" class="total-bar" style="margin-top: 10px">
          <span>转入应收：<b>¥{{ transferDue }}</b></span>
          <span>原单抵扣：¥{{ transferDeduct }}</span>
          <span v-if="transferDiff > 0" class="pay-due">应补差：¥{{ transferDiff.toFixed(2) }}</span>
          <el-tag v-else size="small" type="warning" effect="light">
            退差 ¥{{ (-transferDiff).toFixed(2) }}（确认后自动转入账户余额）
          </el-tag>
        </div>
      </template>
    </div>

    <!-- 支付信息（转课补差：应补差>0 时收银，允许欠费） -->
    <div v-if="mode === 'transfer' && picked && transferDiff > 0" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">支付信息</span>
        <div class="pay-top">
          <span class="pay-item">应补差：<b>{{ transferDiff.toFixed(2) }}</b> 元</span>
          <span v-if="transferArrears > 0" class="pay-due">欠费：{{ transferArrears.toFixed(2) }} 元</span>
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
        <span>补差实收总计：<b>¥{{ transferChannelTotal }}</b> 元</span>
        <span class="fee-sub">渠道支付 ¥{{ transferChannelTotal }}（原单已付 ¥{{ transferDeduct }} 自动抵扣）</span>
      </div>
    </div>

    <!-- 经办信息（转课，独立分区） -->
    <div v-if="mode === 'transfer' && picked" class="panel sec-panel">
      <div class="sec-head"><span class="sec-title">经办信息</span></div>
      <div class="handle-box">
        <div class="handle-item">
          <label>销售来源</label>
          <el-select v-model="handle.source" filterable allow-create clearable size="small"
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
          <label>采单员</label>
          <el-select v-model="handle.orderTaker" filterable clearable size="small"
                     placeholder="请选择采单员" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>咨询顾问</label>
          <el-select v-model="handle.consultant" filterable clearable size="small"
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
                 :disabled="!transferReady" @click="submitTransfer">确 认 转 课</el-button>
    </div>

    <!-- 报名信息：可多选课程/班级，多卡片 -->
    <div v-if="mode === 'form'" class="panel sec-panel">
      <div class="sec-head">
        <span class="sec-title">报名信息</span>
        <div class="stu-bar">
          <el-button type="primary" size="small" @click="openCourseDialog">选择课程</el-button>
          <el-button type="primary" size="small" @click="openClazzDialog">选择班级</el-button>
        </div>
      </div>

      <div v-if="!cards.length" class="ep-empty">
        <el-icon :size="26" color="#c0d3e8"><Plus /></el-icon>
        <p>请点击「选择课程」或「选择班级」开始办理报名</p>
        <span>支持一次选择多门课程/多个班级，逐卡配置后批量订阅</span>
      </div>

      <!-- 课程卡片（按校区分组：一卡一校区一条报课信息；套餐订阅单独成组） -->
      <div v-for="grp in campusGroups" :key="grp.name" class="campus-group">
        <div class="campus-group-head">
          <span class="sel-tag" :class="grp.name === '套餐订阅' ? 'tag-course' : 'campus-tag'">{{
            grp.name === '套餐订阅' ? '套' : '校' }}</span>
          <b class="campus-group-name">{{ grp.name }}</b>
          <span class="fee-sub">（{{ grp.cards.length }} 门课程）</span>
        </div>
        <div v-for="card in grp.cards" :key="card.__editingId || (card.courseId + '@' + (card.campus || ''))" class="sel-card">
        <div class="sel-line">
          <span class="sel-tag tag-course">{{ card.courseId ? '课' : '套' }}</span>
          <b class="sel-name">{{ cardName(card) }}</b>
          <span v-if="card.courseId" class="sel-sub">{{ card.campus || '-' }}</span>
          <div class="sel-sum">
            <span class="sum-item">共：<b>{{ cardHours(card) }}</b> 课时</span>
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
                  <el-option v-for="p in card.priceRows" :key="p.id" :value="p.id"
                             :label="`${fmt(p.hours)}课时=${fmt(p.totalAmount)}元`" />
                </el-select>
                <span v-else class="fee-sub fee-hint" title="该课程未配置按课时定价，按课程默认价">未配置课时定价，按默认价</span>
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

    <!-- 支付信息（应收/实收/欠费联动） -->
    <div v-if="mode === 'form' && cards.length" class="panel sec-panel">
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
        <span class="fee-sub">可用：{{ (balanceAvail - Number(pay.balance || 0) + overpay).toFixed(2) }} 元</span>
        <el-tag v-if="overpay > 0" size="small" type="success" effect="light" class="credit-tag">
          本次充值 +{{ overpay.toFixed(2) }} 元（将充入账户）
        </el-tag>
      </div>
      <!-- 支付账户列表 -->
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
        <span>实收总计：<b>¥{{ receivedTotal }}</b> 元</span>
        <span class="fee-sub">余额支付 ¥{{ (Number(pay.balance) || 0).toFixed(2) }} ＋ 渠道支付 ¥{{ channelTotal }}</span>
      </div>
      <!-- 经办信息 -->
      <div class="handle-box">
        <div class="handle-item">
          <label>销售来源</label>
          <el-select v-model="handle.source" filterable allow-create clearable size="small"
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
          <label>采单员</label>
          <el-select v-model="handle.orderTaker" filterable clearable size="small"
                     placeholder="请选择采单员" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
        <div class="handle-item">
          <label>咨询顾问</label>
          <el-select v-model="handle.consultant" filterable clearable size="small"
                     placeholder="请选择咨询顾问" style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </div>
      </div>
      <el-button type="primary" size="large" class="ep-submit" :loading="saving"
                 :disabled="!picked" @click="editingEnrollId ? saveEdit() : subscribeAll">{{ editingEnrollId ? '保 存 修 改' : '确 认 订 阅' }}</el-button>
    </div>


    <!-- 业务凭证（查看/打印）：表格化版式 -->
    <el-dialog v-model="voucherVisible" title="业务凭证" width="760px" append-to-body>
      <div v-if="viewRow" class="voucher">
        <div class="v-top">智教云课 KaiEdu 业务凭证</div>
        <table class="vt">
          <tr>
            <td class="lbl">学员姓名</td><td>{{ stuName(viewRow.studentId) }}</td>
            <td class="lbl">订单类型</td><td>报名</td>
            <td class="lbl">经办日期</td><td>{{ viewRow.handleDate || (viewRow.createTime || '').slice(0, 10) }}</td>
            <td class="lbl">订单号</td><td>EN{{ viewRow.id }}</td>
          </tr>
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
              {{ row.action === 'CREATE' ? '新增' : row.action === 'TRANSFER' ? '转课' : '修改' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 选择课程弹窗：checkbox 多选 -->
    <el-dialog v-model="courseDialog" title="选择课程" width="880px" append-to-body>
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
        <el-table-column label="开班数" width="64" align="center">
          <template #default="{ row }">{{ clazzCount(row.id) }}</template>
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
      </div>
      <template #footer>
        <el-button @click="courseDialog = false">取 消</el-button>
        <el-button type="primary" @click="confirmCourseDialog">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 选择班级弹窗：checkbox 多选 -->
    <el-dialog v-model="clazzDialog" title="选择班级" width="880px" append-to-body>
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
import { ref, reactive, computed, nextTick, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, CircleClose, CirclePlus, User, Tickets, ArrowDown, View, EditPen, Printer, Document,
  Wallet, Money, Switch, RefreshLeft, Reading, Medal } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import ColumnSetting from '../../components/ColumnSetting.vue'
import api from '../../api'
import { useDictStore } from '../../store/dict'
import { useUserStore } from '../../store/user'
import PhotoUpload from '../../components/PhotoUpload.vue'

const dictStore = useDictStore()
const userStore = useUserStore()

/* ===== 页面模式：列表（默认）/ 填报表单 ===== */
const mode = ref('list')
/** 页面标题跟随办理入口：报名/续报、余额充值、转课（列表页为 办理中心） */
const pageTitle = computed(() =>
  mode.value === 'form' ? '报名/续报' : mode.value === 'recharge' ? '余额充值'
    : mode.value === 'transfer' ? '转课' : '办理中心')

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
    allRecords.value = (await api.enrollPage({ page: 1, size: 200 })).records || []
    if (!Object.keys(stuMaps.value).length) {
      try {
        (await api.studentList()).forEach((s) => (stuMaps.value[s.id] = s.name))
      } catch (e) { /* 学员名缺失时显示 - */ }
    }
  } finally { listLoading.value = false }
}
const listStatus = ref(null)
const filteredList = computed(() => {
  const kw = (listKw.value || '').trim().toLowerCase()
  const [b, e] = listRange.value || []
  return allRecords.value.filter((r) => {
    if (kw) {
      const content = `${stuMaps.value[r.studentId] || ''} ${r.packageName || ''} ${courseMap.value[r.courseId] || ''}`.toLowerCase()
      if (!content.includes(kw)) return false
    }
    if (listStatus.value != null && r.status !== listStatus.value) return false
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
  { prop: 'content', label: '交易内容' }, { prop: 'totalAmount', label: '应收' },
  { prop: 'paidAmount', label: '实收' }, { prop: 'due', label: '欠费' },
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
  else if (cmd === 'edit') openEdit(row)
  else if (cmd === 'print') printOrder(row)
  else if (cmd === 'log') openLog(row)
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
  const due = filteredList.value.reduce((a, r) => a + Number(r.totalAmount || 0), 0)
  const paid = filteredList.value.reduce((a, r) => a + Number(r.paidAmount || 0), 0)
  return { due: due.toFixed(2), paid: paid.toFixed(2), arrears: Math.max(due - paid, 0).toFixed(2) }
})

/** 返回列表页：清空编辑态并刷新订单列表 */
function backToList() {
  editingEnrollId.value = null
  mode.value = 'list'
  loadList()
}

/* 办理入口瓷贴：补费→缴费管理、转课→转课办理；退费/教材杂费/积分功能规划中 */
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
  pay.value = { campus: null, balance: 0, rows: [] }
  balanceAvail.value = 0
  handle.value.source = null
  handle.value.orderTaker = null
  handle.value.consultant = null
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
  mode.value = 'recharge'
}

/** 提交充值：写账户流水（type=1），多账户支付明细与经办信息随单留档，成功后回列表 */
async function submitRecharge() {
  if (!picked.value) return ElMessage.warning('请先选择学员')
  const rows = recharge.value.rows.filter((r) => Number(r.amount) > 0)
  if (!rows.length) return ElMessage.warning('请至少填写一条实收金额大于 0 的支付账户')
  const amount = Math.round(rows.reduce((a, r) => a + Number(r.amount), 0) * 100) / 100
  recharging.value = true
  try {
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

/* ===== 转课页（办理中心-转课入口） ===== */
const transfer = ref({ fromId: null, courseId: null, campus: null, priceId: null, qty: 1,
  clazzId: null, teacherId: null, validEnabled: false, validDate: null, discount: null, remark: '' })
const transferring = ref(false)
/** 转出候选：该学员的在读报名单（选中学员后加载） */
const transferFroms = ref([])
/** 转入课程可选定价行（edu_course_price 按课时模式） */
const transferPriceRows = ref([])

/** 进入转课页：清空学员与表单，补差默认一行支付账户（首选渠道） */
function enterTransfer() {
  studentId.value = null
  picked.value = null
  balanceAvail.value = 0
  transferFroms.value = []
  transferPriceRows.value = []
  transfer.value = { fromId: null, courseId: null, campus: null, priceId: null, qty: 1,
    clazzId: null, teacherId: null, validEnabled: false, validDate: null, discount: null, remark: '' }
  pay.value = { campus: null, balance: 0,
    rows: [{ channel: firstChannel.value, account: '', serial: '', amount: null }] }
  handle.value.source = null
  handle.value.orderTaker = null
  handle.value.consultant = null
  handle.value.date = new Date().toISOString().slice(0, 10)
  mode.value = 'transfer'
}

/** 选中学员后拉取其在读报名单（转出候选） */
async function loadTransferFroms() {
  try {
    transferFroms.value = (await api.enrollPage(
      { page: 1, size: 100, studentId: studentId.value, status: 1 })).records || []
  } catch (e) { transferFroms.value = [] }
  if (!transferFroms.value.some((f) => f.id === transfer.value.fromId)) transfer.value.fromId = null
}

/** 选定转入课程：带出校区/定价行并重置数量与班级 */
async function onTransferCourse(cid) {
  transfer.value.campus = transferCampuses.value[0] || null
  transfer.value.priceId = null
  transfer.value.qty = 1
  transfer.value.clazzId = null
  transferPriceRows.value = []
  if (!cid) return
  try {
    const cached = dlgPriceMap.value[cid]
    const ps = cached || (await api.coursePrices(cid)) || []
    if (!cached) dlgPriceMap.value[cid] = ps
    transferPriceRows.value = ps.filter((p) => p.chargeMode === 1)
    if (transferPriceRows.value.length) transfer.value.priceId = transferPriceRows.value[0].id
  } catch (e) { transferPriceRows.value = [] }
}

/* 转课金额联动：转入应收（定价行×数量−优惠）与原单抵扣（已付），多退少补 */
const transferFrom = computed(() =>
  transferFroms.value.find((f) => f.id === transfer.value.fromId) || null)
const transferCourses = computed(() => courses.value.filter((c) => c.status === 1))
const transferCampuses = computed(() => courseOf(transfer.value.courseId).campuses || [])
const transferPrices = computed(() => transferPriceRows.value)
const transferDeduct = computed(() => (Number(transferFrom.value?.paidAmount || 0)).toFixed(2))
const transferDue = computed(() => {
  const t = transfer.value
  if (!t.courseId) return '0.00'
  const p = transferPriceRows.value.find((x) => x.id === t.priceId)
  let sub = p ? Number(p.totalAmount) * Number(t.qty || 1) : 0
  if (!p) {
    const c = courseOf(t.courseId)
    if (c.totalHours && c.price)
      sub = Number(c.totalHours) * Number(c.price) * Number(c.discount || 100) / 100
  }
  const v = sub - Number(t.discount || 0)
  return (v > 0 ? v : 0).toFixed(2)
})
/** 差额：>0 需补差（渠道收银，可欠费）；<0 退差（自动转入账户余额） */
const transferDiff = computed(() =>
  Math.round((Number(transferDue.value) - Number(transferDeduct.value)) * 100) / 100)
const transferChannelTotal = computed(() =>
  pay.value.rows.reduce((a, p) => a + Number(p.amount || 0), 0).toFixed(2))
const transferArrears = computed(() =>
  Math.max(transferDiff.value - Number(transferChannelTotal.value), 0))
const transferReady = computed(() => !!(picked.value && transfer.value.fromId && transfer.value.courseId))

/** 提交转课：原单置为已转课并创建转入报名单，退差自动入账户余额 */
async function submitTransfer() {
  const t = transfer.value
  if (!picked.value || !t.fromId || !t.courseId)
    return ElMessage.warning('请完整选择转出报名单与转入课程')
  transferring.value = true
  try {
    const rows = pay.value.rows.filter((r) => Number(r.amount) > 0)
    const res = await api.enrollTransfer({
      studentId: picked.value.studentId,
      fromEnrollId: t.fromId,
      courseId: t.courseId,
      campus: t.campus || undefined,
      clazzId: t.clazzId || undefined,
      teacherId: t.teacherId || undefined,
      priceId: t.priceId || undefined,
      qty: t.qty || 1,
      discountAmount: Number(t.discount) > 0 ? Number(t.discount) : undefined,
      paidAmount: Number(transferChannelTotal.value) || 0,
      validDate: t.validEnabled ? (t.validDate || undefined) : undefined,
      remark: t.remark.trim() || undefined,
      source: handle.value.source || undefined,
      handler: handle.value.handler || undefined,
      handleDate: handle.value.date || undefined,
      orderTaker: handle.value.orderTaker || undefined,
      consultant: handle.value.consultant || undefined,
      payInfo: rows.map((r) => `【${r.channel}】${r.amount}元`).join(' ') || '无',
      payDetail: JSON.stringify(rows.map((r) =>
        ({ channel: r.channel, account: r.account, serial: r.serial, amount: Number(r.amount) })))
    })
    const d = res || {}
    const extra = Number(d.refund) > 0
      ? `，退差 ¥${Number(d.refund).toFixed(2)} 已转入账户余额`
      : Number(d.arrears) > 0 ? `，欠费 ¥${Number(d.arrears).toFixed(2)}（可走补费）` : ''
    ElMessage.success(`转课成功：EN${d.fromId ?? ''} → EN${d.enrollId ?? ''}${extra}`)
    backToList()
  } catch (e) { /* 接口已提示 */ }
  finally { transferring.value = false }
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
  // 构建课程卡片（主记录 + 该学员其余在读记录，含套餐订阅）
  // 优惠在 buildEditCard 内同步恢复，合计从渲染首帧起即为核减后的应收
  const card = buildEditCard(row)
  cards.value = [card]
  // 加载该学员所有在读报名记录（含套餐订阅），作为额外卡片
  try {
    const all = (await api.enrollPage({ page: 1, size: 100, studentId: row.studentId })).records || []
    for (const e of all) {
      if (e.status !== 1 || e.id === row.id) continue
      cards.value.push(buildEditCard(e))
    }
  } catch (err) { /* 忽略 */ }
  // 支付信息：从 payDetail JSON 恢复，或按 paidAmount 构建单行（先于异步定价加载，实收/欠费不闪变）
  pay.value.balance = 0
  pay.value.rows = []
  if (row.payDetail) {
    try {
      const detail = JSON.parse(row.payDetail)
      if (Array.isArray(detail) && detail.length)
        pay.value.rows = detail.map((d) => ({ channel: d.channel || '现金', account: d.account || '', serial: d.serial || '', amount: Number(d.amount) || 0 }))
    } catch (e) { /* JSON 解析失败按默认 */ }
  }
  if (!pay.value.rows.length)
    pay.value.rows = [{ channel: '现金', account: '', serial: '', amount: Number(row.paidAmount || 0) }]
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
      c.priceRows = ps.filter((p) => p.chargeMode === 1)
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
  if (!editingEnrollId.value) return
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

/* ===== 学员信息 ===== */
const students = ref([])
const searching = ref(false)
const studentId = ref(null)
const picked = ref(null)

async function searchStudents(kw) {
  if (!kw || !kw.trim()) { students.value = []; return }
  searching.value = true
  try {
    const all = (await api.studentList()) || []
    const k = kw.trim().toLowerCase()
    // s.phone 为后端拼好的有效联系电话（家长电话→备用电话→账号手机号），
    // 用户管理补录手机号后搜索即同步显示，不再出现"无手机号"
    students.value = all.filter((s) =>
      (s.name || '').toLowerCase().includes(k) || (s.phone || '').includes(k)).map((s) => ({
      studentId: s.id, name: s.name,
      phone: s.phone,
      grade: s.grade, school: s.school, idCard: s.idCard
    }))
  } finally { searching.value = false }
}
function onPick(id) {
  picked.value = students.value.find((s) => s.studentId === id) || null
  studentId.value = id
  if (id) { loadSubscribed(); loadBalance() }
  // 转课页：选中学员后加载其在读报名单（转出候选）
  if (id && mode.value === 'transfer') loadTransferFroms()
}

/* 学员账户余额（支付信息-使用余额上限） */
const balanceAvail = ref(0)
async function loadBalance() {
  try {
    const s = await api.accountSummary({ studentId: studentId.value })
    balanceAvail.value = Number(s?.balanceTotal || 0)
  } catch (e) { balanceAvail.value = 0 }
}

/* 该学员在读课程集合（选课弹窗置灰用） */
const subscribedIds = ref(new Set())
async function loadSubscribed() {
  try {
    const en = (await api.enrollPage({ page: 1, size: 100, studentId: studentId.value })).records || []
    // 在读判重按 课程+校区（同一课程不同校区可各报一条）
    subscribedIds.value = new Set(en.filter((e) => e.status === 1 && e.courseId)
      .map((e) => e.courseId + '|' + (e.campus || '')))
  } catch (e) { subscribedIds.value = new Set() }
}
const rowDue = (row) => Math.max(Number(row.totalAmount || 0) - Number(row.paidAmount || 0), 0)

/* ===== 基础数据 ===== */
const courses = ref([])
const clazzes = ref([])
const teachers = ref([])
const courseMap = ref({})
const courseName = (id) => courseMap.value[id] || '-'
const courseOf = (id) => courses.value.find((c) => c.id === id) || {}
const clazzesOf = (cid) => clazzes.value.filter((c) => c.courseId === cid)
const clazzCount = (cid) => clazzes.value.filter((c) => c.courseId === cid).length
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
  // 定价行加载（弹窗缓存优先）
  const cached = dlgPriceMap.value[courseId]
  if (cached) {
    card.priceRows = cached.filter((p) => p.chargeMode === 1)
    if (card.priceRows.length) card.priceId = card.priceRows[0].id
  } else {
    try {
      const ps = (await api.coursePrices(courseId)) || []
      dlgPriceMap.value[courseId] = ps
      card.priceRows = ps.filter((p) => p.chargeMode === 1)
      if (card.priceRows.length) card.priceId = card.priceRows[0].id
    } catch (e) { /* 无定价按默认 */ }
  }
  return true
}

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

/* ===== 选课弹窗（checkbox 多选） ===== */
const courseDialog = ref(false)
const courseTableRef = ref(null)
const courseKw = ref('')
const dlgCampus = ref(null)
const dlgTeachMode = ref(null)
const dlgFeeMode = ref(null)
const dlgCategory = ref(null)
/** 选课弹窗勾选（按校区分桶）：切换校区互不影响，同一课程可在不同校区各选一次 */
const dlgSelMap = ref({})
const dlgSelCount = computed(() =>
  Object.values(dlgSelMap.value).reduce((a, s) => a + (s ? s.size : 0), 0))
/** 勾选变化：记入当前校区桶 */
function onCourseSelChange(rows) {
  const set = dlgSelMap.value[dlgCampus.value] || (dlgSelMap.value[dlgCampus.value] = new Set())
  set.clear()
  rows.forEach((r) => set.add(r.id))
}
/** 切换校区：快照旧校区勾选 → 恢复新校区勾选（表格数据换批前快照，避免丢失） */
watch(dlgCampus, (nv, ov) => {
  if (!courseDialog.value || nv === ov) return
  const rows = courseTableRef.value?.getSelectionRows?.() || []
  const prevSet = dlgSelMap.value[ov] || (dlgSelMap.value[ov] = new Set())
  prevSet.clear()
  rows.forEach((r) => prevSet.add(r.id))
  const target = [...(dlgSelMap.value[nv] || [])]
  nextTick(() => {
    courseFiltered.value.forEach((row) => {
      if (target.includes(row.id)) courseTableRef.value?.toggleRowSelection(row, true)
    })
  })
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
const feeStdText = (c) => {
  const p = (dlgPriceMap.value[c.id] || []).find((x) => x.chargeMode === 1)
  return p ? `${fmt(p.hours)}课时=${fmt(p.totalAmount)}元` : '—'
}
function openCourseDialog() {
  courseKw.value = ''
  // 校区必选：保持已选校区，未选时默认第一个校区（不允许为空/全部校区）
  dlgCampus.value = dlgCampus.value || campusOptions.value[0] || null
  dlgTeachMode.value = null
  dlgFeeMode.value = null; dlgCategory.value = null
  dlgSelMap.value = {}
  courseDialog.value = true
  // 预载在售课程定价（勾选不回显，由用户按需自行选择）
  courses.value.filter((c) => c.status === 1 && dlgPriceMap.value[c.id] === undefined)
    .forEach((c) => {
      api.coursePrices(c.id)
        .then((ps) => { dlgPriceMap.value[c.id] = ps || [] })
        .catch(() => { dlgPriceMap.value[c.id] = [] })
    })
}
async function confirmCourseDialog() {
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
function openClazzDialog() {
  clazzKw.value = ''
  clazzDialog.value = true
  clazzFilteredAll.value.forEach((c) => {
    if (occupyMap.value[c.id] === undefined) {
      api.clazzStudents(c.id)
        .then((d) => { occupyMap.value[c.id] = d?.studying ?? 0 })
        .catch(() => { occupyMap.value[c.id] = 0 })
    }
  })
  nextTick(() => {
    clazzFilteredAll.value.forEach((row) => {
      const card = cards.value.find((c) => c.clazzId === row.id)
      if (card) clazzTableRef.value?.toggleRowSelection(row, true)
    })
  })
}
async function confirmClazzDialog() {
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
  saving.value = true
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
  saving.value = false
  if (fail === 0) {
    // 超收部分自动充入学员账户
    if (overpay.value > 0) {
      try {
        await api.enrollCredit({ studentId: studentId.value, amount: overpay.value,
          remark: '报名实收超额自动充入' })
        ElMessage.success(`报名成功：${ok} 门课程，超额 ¥${overpay.value.toFixed(2)} 已充入账户`)
      } catch (e) {
        ElMessage.success(`报名成功：${ok} 门课程（超额 ¥${overpay.value.toFixed(2)} 充入账户失败，可在充值管理补录）`)
      }
    } else {
      ElMessage.success(`报名成功：${ok} 门课程`)
    }
    pay.value = { campus: pay.value.campus, balance: 0, rows: [] }
    // 全部成功：回到列表页并刷新
    mode.value = 'list'
    loadList()
  } else {
    ElMessage.warning(`成功 ${ok} 门，失败 ${fail} 门（失败卡片已保留，请调整后重试）`)
    loadSubscribed()
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
  display: flex; align-items: center; gap: 10px;
  background: #f5f9ff; border: 1px solid #dbe7fb; border-radius: 10px;
  padding: 8px 14px; font-size: 13px; color: #667c99;
}
.picked-bar b { color: #155EEF; font-size: 14px; }
.picked-bar span { color: #8fa8c4; }
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
/* 余额充值：充值后余额高亮 + 备注整行 */
.re-balance { font-size: 14px; font-weight: 600; color: #f59e0b; font-variant-numeric: tabular-nums; }
.re-remark { width: 100%; align-items: flex-start; }
.re-remark label { line-height: 28px; }
/* 报名记录汇总与状态标签 */
.rec-summary { font-size: 12.5px; color: #667c99; }
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
.order-status {
  display: inline-block; font-size: 12px; width: 64px; text-align: center;
  padding: 2px 0; border-radius: 4px; color: #fff;
}
.st-done { background: #ffb51f; }
.st-owing { background: #f04438; }
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
