<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">报名/续报</span>
      <div class="actions">
        <el-button v-if="mode === 'form'" @click="mode = 'list'; loadList()">← 返回列表</el-button>
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
      </div>

      <!-- 订单列表 -->
      <div class="panel sec-panel">
        <div class="list-toolbar">
          <el-button size="small" :type="quick === 'mine' ? 'primary' : ''" @click="quick = quick === 'mine' ? null : 'mine'">我的今日</el-button>
          <el-button size="small" :type="quick === 'campus' ? 'primary' : ''" @click="quick = quick === 'campus' ? null : 'campus'">校区今日</el-button>
          <el-input v-model="listKw" placeholder="学员姓名 / 课程关键字" clearable style="width: 200px"
                    :prefix-icon="Search" size="small" />
          <el-select v-model="listOwing" placeholder="到款状态" clearable size="small" style="width: 110px">
            <el-option label="欠费" value="owing" /><el-option label="已结清" value="clear" />
          </el-select>
          <el-date-picker v-model="listRange" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="经办起" end-placeholder="经办止" size="small" style="width: 230px" />
        </div>
        <div class="rec-summary" style="margin-bottom: 8px">
          当前结果：总计 <b>{{ filteredList.length }}</b> 条记录，
          应收款项总计 <b>¥{{ listSum.due }}</b> ｜ 实收总计 <b>¥{{ listSum.paid }}</b> ｜ 欠费总计 <b class="sum-money">¥{{ listSum.arrears }}</b>
        </div>
        <el-table :data="pagedList" size="small" stripe max-height="480" v-loading="listLoading">
          <el-table-column prop="id" label="订单号" width="76" align="center">
            <template #default="{ row }">EN{{ row.id }}</template>
          </el-table-column>
          <el-table-column label="学员姓名" width="90" show-overflow-tooltip>
            <template #default="{ row }">{{ stuName(row.studentId) }}</template>
          </el-table-column>
          <el-table-column label="交易内容" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.packageName || courseName(row.courseId) }}，{{ row.totalHours }}课时×¥{{ row.price }}
              {{ row.discountAmount > 0 ? `，优惠¥${row.discountAmount}` : '' }}
            </template>
          </el-table-column>
          <el-table-column label="应收" width="88" align="right">
            <template #default="{ row }"><span class="tag-in">收</span> {{ row.totalAmount }}</template>
          </el-table-column>
          <el-table-column label="实收" width="88" align="right">
            <template #default="{ row }"><span class="tag-in">收</span> {{ row.paidAmount ?? '0.00' }}</template>
          </el-table-column>
          <el-table-column label="欠费" width="84" align="right">
            <template #default="{ row }">
              <span :class="rowDue(row) > 0 ? 'pay-due' : ''">{{ rowDue(row) > 0 ? rowDue(row).toFixed(2) : '0.00' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="consultant" label="咨询顾问" width="88" show-overflow-tooltip>
            <template #default="{ row }">{{ row.consultant || '-' }}</template>
          </el-table-column>
          <el-table-column prop="handleDate" label="经办日期" width="96" align="center">
            <template #default="{ row }">{{ row.handleDate || (row.createTime || '').slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column prop="handler" label="经办人" width="84" show-overflow-tooltip>
            <template #default="{ row }">{{ row.handler || '-' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
          <el-table-column label="报名状态" width="78" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="{ 1: 'success', 2: 'info', 3: 'danger' }[row.status]" effect="plain">
                {{ { 1: '在读', 2: '已结课', 3: '已退订' }[row.status] || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="到款状态" width="80" align="center">
            <template #default="{ row }">
              <span class="order-status" :class="rowDue(row) > 0 ? 'st-owing' : 'st-done'">
                {{ rowDue(row) > 0 ? '欠费' : '已完成' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="146" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="viewOrder(row)">查看</el-button>
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" size="small" @click="printOrder(row)">打印</el-button>
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
    <!-- 学员信息 -->
    <div v-if="mode === 'form'" class="panel sec-panel">
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
      </div>
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

      <!-- 课程卡片（每门课程一张，独立配置） -->
      <div v-for="(card, idx) in cards" :key="card.courseId" class="sel-card">
        <div class="sel-line">
          <span class="sel-tag tag-course">课</span>
          <b class="sel-name">{{ courseName(card.courseId) }}</b>
          <span class="sel-sub">{{ campusText(courseOf(card.courseId)) }}</span>
          <span class="sel-tag tag-clazz" style="margin-left: 14px">班</span>
          <el-select v-model="card.clazzId" filterable clearable size="small"
                     placeholder="选择班级（可不选）" style="width: 180px">
            <el-option v-for="c in clazzesOf(card.courseId)" :key="c.id"
                       :label="`${c.name}（${c.capacity}名）`" :value="c.id" />
          </el-select>
          <div class="sel-sum">
            <span class="sum-item">共：<b>{{ cardHours(card) }}</b> 课时</span>
            <span class="sum-item">小计：<b class="sum-money">¥{{ cardFinal(card) }}</b> 元</span>
          </div>
          <el-icon class="sel-close" title="移除课程" @click="cards.splice(idx, 1)"><CircleClose /></el-icon>
        </div>
        <div class="fee-stand">
          <div class="fee-main">
            <div class="fee-row">
              <span class="fee-label">价格</span>
              <span class="fee-value">¥{{ cardSubtotal(card) }}<i>元</i></span>
            </div>
            <div class="fee-row">
              <span class="fee-label">收费模式</span>
              <el-select v-if="card.priceRows.length" v-model="card.priceId" size="small"
                         style="width: 190px" placeholder="选择定价标准">
                <el-option v-for="p in card.priceRows" :key="p.id" :value="p.id"
                           :label="`${fmt(p.hours)}课时=${fmt(p.totalAmount)}元`" />
              </el-select>
              <span v-else class="fee-sub">该课程未配置按课时定价，按课程默认价</span>
              <span class="fee-label" style="margin-left: 14px">数量</span>
              <el-input-number v-model="card.qty" :min="1" :max="99" :precision="0" size="small"
                               controls-position="right" style="width: 92px" />
            </div>
            <div class="fee-row">
              <span class="fee-label">有效期</span>
              <el-checkbox v-model="card.validEnabled" size="small" />
              <el-date-picker v-model="card.validDate" type="date" value-format="YYYY-MM-DD"
                              size="small" :disabled="!card.validEnabled"
                              placeholder="选择有效期" style="width: 150px" />
              <span class="fee-label" style="margin-left: 14px">授课老师</span>
              <el-select v-model="card.teacherId" filterable clearable size="small"
                         placeholder="可后指派" style="width: 140px">
                <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </div>
            <div class="fee-row">
              <span class="fee-label">备注</span>
              <el-input v-model="card.remark" size="small" maxlength="200"
                        placeholder="该备注只有内部操作人员可见" style="width: 320px" />
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
                 :disabled="!picked" @click="subscribeAll">确 认 订 阅</el-button>
    </div>


    <!-- 业务凭证（查看/打印） -->
    <el-dialog v-model="voucherVisible" title="业务凭证" width="820px" append-to-body>
      <div v-if="viewRow" class="voucher">
        <div class="v-top">智教云课 KaiEdu 业务凭证</div>
        <div class="v-sub">
          <span>学员姓名：{{ stuName(viewRow.studentId) }}</span>
          <span>订单类型：报名</span>
          <span>经办日期：{{ viewRow.handleDate || (viewRow.createTime || '').slice(0, 10) }}</span>
          <span class="v-order">订单号：EN{{ viewRow.id }}</span>
        </div>
        <div class="v-table">
          <div class="v-row v-head">
            <span class="s160">交易内容</span><span class="s160">班级信息</span><span class="s140">价格</span>
            <span class="s50">数量</span><span class="s90">原价</span><span class="s110">优惠</span><span class="s90">应收小计</span>
          </div>
          <div class="v-row">
            <span class="s160">{{ viewRow.packageName || courseName(viewRow.courseId) }}</span>
            <span class="s160">{{ clazzName(viewRow.clazzId) }}</span>
            <span class="s140">{{ viewRow.totalHours }}课时=¥{{ viewRow.price }}</span>
            <span class="s50">1</span>
            <span class="s90">¥{{ (Number(viewRow.totalAmount || 0) + Number(viewRow.discountAmount || 0)).toFixed(2) }}</span>
            <span class="s110">{{ viewRow.discountAmount > 0 ? `¥${viewRow.discountAmount}` : '—' }}</span>
            <span class="s90">¥{{ viewRow.totalAmount }}</span>
          </div>
          <div class="v-mark">备注：{{ viewRow.remark || '' }}</div>
        </div>
        <div class="v-total">
          <span><b>应收总计：</b>¥{{ viewRow.totalAmount }}</span>
          <span><b>实收总计：</b>¥{{ viewRow.paidAmount ?? '0.00' }}</span>
          <span><b>使用余额：</b>¥0.00</span>
          <span><b>欠费：</b>¥{{ rowDue(viewRow).toFixed(2) }}</span>
        </div>
        <div class="v-foot">
          <div class="v-pay">收款方式：合计实收 ¥{{ viewRow.paidAmount ?? '0.00' }}</div>
          <div class="v-signs">
            <span><i>打印时间：</i>{{ new Date().toLocaleString('zh-CN') }}</span>
            <span><i>经办人：</i>{{ viewRow.handler || '-' }}</span>
            <span><i>经办签名：</i>&nbsp;</span>
            <span><i>客户签名：</i>&nbsp;</span>
          </div>
        </div>
        <div class="v-note">开课三次后不予办理退费。请您保管好您的收据，丢失不予补办。</div>
      </div>
      <template #footer>
        <el-button @click="voucherVisible = false">关 闭</el-button>
        <el-button type="primary" @click="printOrder(viewRow)">打 印</el-button>
      </template>
    </el-dialog>

    <!-- 编辑报名单 -->
    <el-dialog v-model="editVisible" title="编辑报名单" width="460px" append-to-body>
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="实收(元)">
          <el-input-number v-model="editForm.paidAmount" :min="0" :max="999999.99" :precision="2"
                           controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="报名状态">
          <el-select v-model="editForm.status" style="width: 200px">
            <el-option label="在读" :value="1" /><el-option label="已结课" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="销售来源">
          <el-select v-model="editForm.source" filterable allow-create clearable style="width: 200px">
            <el-option v-for="s in sourcePresets" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="采单员">
          <el-select v-model="editForm.orderTaker" filterable clearable style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="咨询顾问">
          <el-select v-model="editForm.consultant" filterable clearable style="width: 200px">
            <el-option v-for="a in staffOptions" :key="a.name" :label="`${a.name}（${a.typeName}）`" :value="a.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="经办日期">
          <el-date-picker v-model="editForm.handleDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker v-model="editForm.validDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取 消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEdit">保 存</el-button>
      </template>
    </el-dialog>

    <!-- 选择课程弹窗：checkbox 多选 -->
    <el-dialog v-model="courseDialog" title="选择课程" width="880px" append-to-body>
      <div class="dlg-toolbar">
        <el-select v-model="dlgCampus" placeholder="全部校区" clearable size="default" style="width: 170px">
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
                row-key="id" @selection-change="(rows) => (selCourseIds = rows.map((r) => r.id))">
        <el-table-column type="selection" width="44" :selectable="canPickCourse" reserve-selection />
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
            <el-tag v-if="!canPickCourse(row)" size="small" type="info" effect="plain">已订阅</el-tag>
            <el-tag v-else-if="inCards(row.id)" size="small" type="warning" effect="plain">已选择</el-tag>
            <el-tag v-else size="small" type="success" effect="plain">可选</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="picked-clazz-bar">
        <span class="fee-sub">已选择课程 <b style="color: #155EEF">{{ selCourseIds.length }}</b> 门</span>
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
import { ref, computed, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, CircleClose, CirclePlus, User, Tickets } from '@element-plus/icons-vue'
import api from '../../api'
import { useDictStore } from '../../store/dict'
import { useUserStore } from '../../store/user'
import PhotoUpload from '../../components/PhotoUpload.vue'

const dictStore = useDictStore()
const userStore = useUserStore()

/* ===== 页面模式：列表（默认）/ 填报表单 ===== */
const mode = ref('list')

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
const filteredList = computed(() => {
  const kw = (listKw.value || '').trim().toLowerCase()
  const [b, e] = listRange.value || []
  return allRecords.value.filter((r) => {
    if (kw) {
      const content = `${stuMaps.value[r.studentId] || ''} ${r.packageName || ''} ${courseMap.value[r.courseId] || ''}`.toLowerCase()
      if (!content.includes(kw)) return false
    }
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
const listSum = computed(() => {
  const due = filteredList.value.reduce((a, r) => a + Number(r.totalAmount || 0), 0)
  const paid = filteredList.value.reduce((a, r) => a + Number(r.paidAmount || 0), 0)
  return { due: due.toFixed(2), paid: paid.toFixed(2), arrears: Math.max(due - paid, 0).toFixed(2) }
})

/** 进入填报页：清空上次的表单状态 */
function enterForm() {
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

/* ===== 订单操作：查看 / 打印 / 编辑 ===== */
const clazzName = (cid) => (cid ? (clazzes.value.find((c) => c.id === cid)?.name || '-') : '-')
const voucherVisible = ref(false)
const viewRow = ref(null)
function viewOrder(row) {
  viewRow.value = row
  voucherVisible.value = true
}

/** 打印：新窗口输出业务凭证并调起打印 */
function printOrder(row) {
  if (!row) return
  const due = rowDue(row).toFixed(2)
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>业务凭证 EN${row.id}</title>
<style>
  body { font-family: "Microsoft YaHei", sans-serif; color: #000; padding: 24px; }
  .top { font-size: 20px; font-weight: 700; text-align: center; margin-bottom: 6px; }
  .sub { display: flex; flex-wrap: wrap; gap: 6px 20px; font-size: 13px; border-bottom: 2px solid #000; padding: 6px 0; }
  table { width: 100%; border-collapse: collapse; margin-top: 8px; }
  th, td { border: 1px solid #000; padding: 6px 8px; font-size: 13px; text-align: center; }
  .total { display: flex; gap: 26px; font-size: 14px; margin: 10px 0; }
  .pay { font-size: 13px; margin-bottom: 10px; }
  .signs { display: flex; gap: 26px; font-size: 13px; border-top: 1px dashed #888; padding-top: 10px; }
  .note { margin-top: 8px; font-size: 12px; }
  @media print { body { padding: 0 } }
</style></head><body>
<div class="top">智教云课 KaiEdu 业务凭证</div>
<div class="sub">
  <span>学员姓名：${stuName(row.studentId)}</span><span>订单类型：报名</span>
  <span>经办日期：${row.handleDate || (row.createTime || '').slice(0, 10)}</span><span>订单号：EN${row.id}</span>
</div>
<table>
  <tr><th>交易内容</th><th>班级信息</th><th>价格</th><th>数量</th><th>原价</th><th>优惠</th><th>应收小计</th></tr>
  <tr>
    <td>${row.packageName || courseName(row.courseId)}</td>
    <td>${clazzName(row.clazzId)}</td>
    <td>${row.totalHours}课时=¥${row.price}</td>
    <td>1</td>
    <td>¥${(Number(row.totalAmount || 0) + Number(row.discountAmount || 0)).toFixed(2)}</td>
    <td>${row.discountAmount > 0 ? '¥' + row.discountAmount : '—'}</td>
    <td>¥${row.totalAmount}</td>
  </tr>
</table>
<div class="total">
  <span><b>应收总计：</b>¥${row.totalAmount}</span><span><b>实收总计：</b>¥${row.paidAmount ?? '0.00'}</span>
  <span><b>使用余额：</b>¥0.00</span><span><b>欠费：</b>¥${due}</span>
</div>
<div class="pay">收款方式：合计实收 ¥${row.paidAmount ?? '0.00'}</div>
<div class="signs">
  <span>打印时间：${new Date().toLocaleString('zh-CN')}</span><span>经办人：${row.handler || '-'}</span>
  <span>经办签名：</span><span>客户签名：</span>
</div>
<div class="note">开课三次后不予办理退费。请您保管好您的收据，丢失不予补办。</div>
<script>window.onload = function () { window.print() }<\/script>
</body></html>`
  const w = window.open('', '_blank')
  if (!w) return ElMessage.warning('浏览器拦截了打印窗口，请允许弹窗后重试')
  w.document.write(html)
  w.document.close()
}

/* 编辑报名单 */
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = ref({})
function openEdit(row) {
  editForm.value = {
    id: row.id,
    paidAmount: Number(row.paidAmount || 0),
    status: row.status,
    source: row.source || null,
    orderTaker: row.orderTaker || null,
    consultant: row.consultant || null,
    handleDate: row.handleDate || (row.createTime || '').slice(0, 10),
    validDate: row.validDate || null,
    remark: row.remark || ''
  }
  editVisible.value = true
}
async function saveEdit() {
  editSaving.value = true
  try {
    await api.enrollUpdate(editForm.value)
    ElMessage.success('已保存')
    editVisible.value = false
    loadList()
  } finally { editSaving.value = false }
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
    students.value = all.filter((s) =>
      (s.name || '').toLowerCase().includes(k) || (s.parentPhone || '').includes(k)
        || (s.backupPhone || '').includes(k)).map((s) => ({
      studentId: s.id, name: s.name,
      phone: s.parentPhone || s.backupPhone,
      grade: s.grade, school: s.school, idCard: s.idCard
    }))
  } finally { searching.value = false }
}
function onPick(id) {
  picked.value = students.value.find((s) => s.studentId === id) || null
  studentId.value = id
  if (id) { loadSubscribed(); loadBalance() }
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
    subscribedIds.value = new Set(en.filter((e) => e.status === 1).map((e) => e.courseId))
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
const campusText = (c) => (c && c.campusMode === 2 ? (c.campus || '-') : '全部校区')
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
const inCards = (cid) => cards.value.some((c) => c.courseId === cid)

async function addCard(courseId) {
  if (inCards(courseId) || !courseId) return
  const card = {
    courseId, clazzId: null, priceId: null, qty: 1, teacherId: null,
    validEnabled: false, validDate: null, remark: '',
    discounts: [], priceRows: []
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
}

/* 每卡价格计算 */
const cardSelectedPrice = (card) => card.priceRows.find((p) => p.id === card.priceId) || null
const cardSubtotal = (card) => {
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
const selCourseIds = ref([])
const campusOptions = computed(() =>
  dictStore.options('campus').map((d) => d.label).filter((l) => l !== '全部校区'))
const dlgPriceMap = ref({})
const courseFiltered = computed(() => {
  const k = (courseKw.value || '').trim().toLowerCase()
  return courses.value.filter((c) => {
    if (c.status !== 1) return false
    if (k && !(c.name || '').toLowerCase().includes(k)
      && !(dictStore.label('course_category', c.category) || '').toLowerCase().includes(k)) return false
    if (dlgTeachMode.value != null && c.teachMode !== dlgTeachMode.value) return false
    if (dlgCategory.value != null && c.category !== dlgCategory.value) return false
    if (dlgCampus.value && !(c.campusMode === 1 || (c.campus || '').split(',').includes(dlgCampus.value))) return false
    if (dlgFeeMode.value === 'hour' && c.chargeHour !== 1) return false
    if (dlgFeeMode.value === 'time' && c.chargeTime !== 1) return false
    return true
  })
})
const canPickCourse = (c) => !subscribedIds.value.has(c.id)
const feeStdText = (c) => {
  const p = (dlgPriceMap.value[c.id] || []).find((x) => x.chargeMode === 1)
  return p ? `${fmt(p.hours)}课时=${fmt(p.totalAmount)}元` : '—'
}
function openCourseDialog() {
  courseKw.value = ''; dlgCampus.value = null; dlgTeachMode.value = null
  dlgFeeMode.value = null; dlgCategory.value = null
  courseDialog.value = true
  // 预载在售课程定价 + 回显已选卡片勾选
  courses.value.filter((c) => c.status === 1 && dlgPriceMap.value[c.id] === undefined)
    .forEach((c) => {
      api.coursePrices(c.id)
        .then((ps) => { dlgPriceMap.value[c.id] = ps || [] })
        .catch(() => { dlgPriceMap.value[c.id] = [] })
    })
  nextTick(() => {
    courseFiltered.value.forEach((row) => {
      if (inCards(row.id)) courseTableRef.value?.toggleRowSelection(row, true)
    })
  })
}
async function confirmCourseDialog() {
  for (const cid of selCourseIds.value) await addCard(cid)
  courseDialog.value = false
  if (selCourseIds.value.length) ElMessage.success(`已添加 ${selCourseIds.value.length} 门课程`)
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
    if (!card) { await addCard(cl.courseId); card = cards.value.find((c) => c.courseId === cl.courseId) }
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
  const jobs = cards.value.map((card, i) => () => {
    // 实收分摊：按各卡小计占比，末卡补齐尾差
    let paid = Math.round(Number(cardFinal(card)) * coverage * 100) / 100
    if (i === cards.value.length - 1) paid = Math.round((received - allocated) * 100) / 100
    else allocated += paid
    return api.enrollSubscribe({
      studentId: studentId.value, courseId: card.courseId,
      clazzId: card.clazzId || undefined, teacherId: card.teacherId || undefined,
      priceId: card.priceId || undefined, qty: card.qty || 1,
      discountAmount: Number(cardDiscount(card)) > 0 ? Number(cardDiscount(card)) : undefined,
      paidAmount: paid > 0 ? paid : undefined,
      validDate: card.validEnabled ? (card.validDate || undefined) : undefined,
      remark: card.remark || undefined,
      source: handle.value.source || undefined,
      handler: handle.value.handler || undefined,
      handleDate: handle.value.date || undefined,
      orderTaker: handle.value.orderTaker || undefined,
      consultant: handle.value.consultant || undefined
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
.sel-line { display: flex; align-items: center; gap: 8px; }
.sel-tag {
  flex-shrink: 0; width: 24px; height: 24px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 700; color: #fff;
}
.tag-course { background: linear-gradient(135deg, #12b76a, #7ccf9e); }
.tag-clazz { background: linear-gradient(135deg, #155EEF, #5B8DEF); }
.sel-name { font-size: 14px; color: #142a44; max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sel-sub { color: #8fa8c4; font-size: 12.5px; }
.sel-close { margin-left: auto; color: #b3c5da; cursor: pointer; font-size: 17px; }
.sel-close:hover { color: #f04438; }
.sel-sum { margin-left: auto; display: flex; gap: 18px; align-items: center; margin-right: 6px; }
.sum-item { font-size: 12.5px; color: #667c99; }
.sum-item b { font-size: 15px; color: #142a44; }
.sum-money { color: #f79009 !important; }
/* 收费标准 + 优惠（左右分区） */
.fee-stand {
  border-top: 1px dashed #e4edf7; margin-top: 10px; padding-top: 10px;
  display: flex; gap: 16px; align-items: flex-start;
}
.fee-main { flex: 1; display: flex; flex-direction: column; gap: 10px; min-width: 0; }
.fee-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.fee-label { font-size: 12.5px; color: #8fa8c4; flex-shrink: 0; }
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
/* 报名记录汇总与状态标签 */
.rec-summary { font-size: 12.5px; color: #667c99; }
.rec-summary b { color: #142a44; }
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
/* 列表页工具栏 */
.list-toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 10px; }
.pager-row { display: flex; justify-content: flex-end; margin-top: 8px; }
/* 业务凭证 */
.voucher { border: 1px solid #d5dfe9; border-radius: 8px; padding: 18px 22px; background: #fff; }
.v-top { font-size: 19px; font-weight: 700; text-align: center; margin-bottom: 6px; color: #142a44; }
.v-sub { display: flex; flex-wrap: wrap; gap: 4px 20px; font-size: 13px; color: #344054;
  border-bottom: 2px solid #142a44; padding: 6px 0; }
.v-order { margin-left: auto; color: #8fa8c4; }
.v-table { margin-top: 8px; }
.v-row { display: flex; font-size: 13px; color: #344054; }
.v-row > span { padding: 7px 8px; border-bottom: 1px solid #e4edf7; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.v-head { background: #f5f8fc; font-weight: 600; }
.v-head > span { border-bottom: 1px solid #d5dfe9; }
.s160 { width: 160px; flex-shrink: 0; } .s140 { width: 140px; flex-shrink: 0; }
.s130 { width: 130px; flex-shrink: 0; } .s110 { width: 110px; flex-shrink: 0; }
.s90 { width: 90px; flex-shrink: 0; text-align: right; } .s50 { width: 50px; flex-shrink: 0; text-align: center; }
.v-mark { font-size: 12.5px; color: #667c99; padding: 8px; }
.v-total { display: flex; flex-wrap: wrap; gap: 8px 24px; font-size: 13.5px; color: #344054; margin: 8px 0; }
.v-total b { color: #142a44; }
.v-foot { border-top: 1px dashed #c9d6e4; padding-top: 8px; }
.v-pay { font-size: 13px; color: #344054; margin-bottom: 8px; }
.v-signs { display: flex; flex-wrap: wrap; gap: 8px 26px; font-size: 13px; color: #344054; }
.v-signs i { font-style: normal; color: #8fa8c4; }
.v-note { margin-top: 8px; font-size: 12px; color: #98a2b3; }
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
