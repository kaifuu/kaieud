# 智教云课 KaiEdu — K12 教务管理系统

一套前后端分离的 K12 教育培训机构教务管理系统，覆盖核心运营闭环：

**学员建档 → 课时套餐 / 合同 → 缴费录入 → 可视化排课 → 上课签到消课 → 自动计提教师成本 → 成本毛利报表**

对标校宝教务核心能力，聚焦机构内部运营（教务 / 财务 / 校长三类视角），剔除营销、招生等冗余模块。

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router + Tailwind CSS 4 |
| 排课 / 图表 | FullCalendar 6（日 / 周 / 月视图拖拽排课）、ECharts 5 |
| 后端 | Spring Boot 2.7 (Java 8) + MyBatis-Plus + JWT + AOP 操作日志 |
| 数据库 | MySQL 8 |

## 功能模块

**首页概览** — 在读学生、在职教师、开课班级、缴费总收入等运营指标，缴费趋势、课时占比、教师投入排名等图表

**教务管理**
- 学生管理（多校区归属、学管师负责关系、档案变更日志）
- 班级管理、课程（班型）管理、课时套餐
- 合同管理（总课时 / 单价 / 折扣 / 应收，缴费自动累计已缴金额）
- 缴费管理（缴费台账、凭证上传）
- 排课管理 — FullCalendar 可视化排课，教师时间冲突检测、教室占用检测、调时 / 取消 / 补课
- 教师空闲时段查询
- 签到消课（课消审核流，课时用尽自动结课）
- 请假管理（审批通过自动取消课次，支持补课时 / 补课）
- 教室管理、咨询与评价

**成本毛利报表** — 教师成本自动计提（按课次 / 课时单价），收入成本占比、毛利分析

**统计汇总** — 按学校 / 教师 / 学生维度的收入统计、课时占比、教师投入量与闲置量（按 160 标准课时 / 月折算）

**系统管理** — 租户管理、组织（校区）管理、用户管理、角色管理（菜单 + 按钮权限）、菜单管理、字典、基础设置、通知、操作日志（AOP 自动记录）、校宝存量数据导入

**学生端** — 选课中心（班课选班级、一对一直接预约）、我的课表、我的老师、发起咨询、评价老师

**教师端** — 我的课表、所带学生（课时 / 折扣 / 合同金额）、收到的评价、咨询回复、个人收入汇总

## 目录结构

```
├── sql/                  # 建库建表 + 种子数据 + 增量升级脚本
│   ├── edu_course.sql    # 基础库（V1 全量）
│   └── upgrade_v2*.sql   # V2 ~ V2.6 增量（套餐/合同/消课/成本/校区/教室/排课历史/旷课审批/课时管理）
├── edu-server/           # Spring Boot 后端（端口 8080）
└── edu-web/              # Vue3 前端（端口 5173，代理 /api → 8080）
```

## 快速开始

### 1. 数据库（MySQL 8）

按顺序执行：

```bash
mysql -uroot -p < sql/edu_course.sql     # 自动创建 edu_course 库
mysql -uroot -p edu_course < sql/upgrade_v2.sql
mysql -uroot -p edu_course < sql/upgrade_v2.1.sql
mysql -uroot -p edu_course < sql/upgrade_v2.2.sql
mysql -uroot -p edu_course < sql/upgrade_v2.3.sql
mysql -uroot -p edu_course < sql/upgrade_v2.4.sql
mysql -uroot -p edu_course < sql/upgrade_v2.5.sql
mysql -uroot -p edu_course < sql/upgrade_v2.6.sql
mysql -uroot -p edu_course < sql/upgrade_v2.7.sql
mysql -uroot -p edu_course < sql/upgrade_v2.8.sql
```

连接配置在 `edu-server/src/main/resources/application.yml`（默认 root / root，按需修改）。

### 2. 后端（Java 8）

> ⚠️ 早期 JDK 8（如 1.8.0_73）的 javac 存在「注解处理器生成的方法在方法引用中不可见」的 bug，会导致 Lombok 编译失败，请使用较新的 JDK 8（如 corretto 1.8.0_432）。

```bash
cd edu-server
mvn spring-boot:run
```

### 3. 前端

```bash
cd edu-web
npm install
npm run dev        # http://localhost:5173
```

## 默认账号（密码均为 123456）

| 账号 | 角色 | 视角 |
|------|------|------|
| admin | 超级管理员（校长） | 全部菜单、全部数据、成本毛利报表 |
| edu01 | 教务管理员 | 教务管理、排课消课、统计汇总；不可见成本毛利 |
| ct01 | 班主任 / 财务 | 教务管理、缴费录入、成本毛利报表 |
| teacher01 / teacher02 | 教师 | 我的课表 / 学生 / 评价 / 咨询 / 收入 |
| student01 | 学生 | 选课中心 / 我的课表 / 我的老师 |

## 关键设计

- **认证鉴权**：JWT（24h），拦截器解析 token 后加载用户 / 角色 / 权限，`@RequiresPerm("xxx")` 注解控制接口权限；超级管理员（userType=1）放行全部
- **多租户 + 多校区**：业务查询按登录用户 tenant_id / org_id 过滤；超级管理员查看全部数据
- **课时闭环**：合同（总课时 / 单价 / 折扣 / 应收）→ 缴费累计已缴 → 排课 → 签到消课扣课时 → 课时用尽自动结课；请假审批通过自动取消课次，补课时追加合同总课时、补课生成补课标记课次
- **成本核算**：课次完成自动按教师课时单价计提成本，成本毛利报表实时汇总
- **操作日志**：AOP 环绕 `@Log` 注解自动落库，管理端可查
