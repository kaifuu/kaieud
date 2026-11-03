package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.exception.BizException;
import com.edu.entity.SysDeployRecord;
import com.edu.entity.SysDeployTarget;
import com.edu.mapper.SysDeployRecordMapper;
import com.edu.mapper.SysDeployTargetMapper;
import com.edu.security.AuthContext;
import com.edu.security.DeployCrypto;
import com.edu.service.ContainerDeployService;
import com.edu.service.SshService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 容器管理（仅平台运营）：打包发布 / SSH部署到远程服务器 / 远程服务拉起与日志。
 */
@RestController
@RequestMapping("/deploy")
@RequiredArgsConstructor
public class DeployController {

    private final SysDeployTargetMapper targetMapper;
    private final SysDeployRecordMapper recordMapper;
    private final SshService ssh;
    private final ContainerDeployService deployService;
    private final DeployCrypto crypto;

    private void platformOnly() {
        Long tid = AuthContext.get().getTenantId();
        if (tid != null && tid != 0) throw new BizException("仅平台运营可使用容器管理");
    }

    /* ==================== 目标服务器 ==================== */

    @GetMapping("/targets")
    public Result<List<SysDeployTarget>> targets() {
        platformOnly();
        List<SysDeployTarget> list = targetMapper.selectList(
                new LambdaQueryWrapper<SysDeployTarget>().orderByDesc(SysDeployTarget::getId));
        list.forEach(t -> { t.setSecret(null); t.setDbSecret(null); });   // 凭据不出后端
        return Result.ok(list);
    }

    @PostMapping("/targets")
    @Log("保存发布服务器")
    public Result<Long> saveTarget(@RequestBody TargetDTO dto) {
        platformOnly();
        if (!StringUtils.hasText(dto.getName()) || !StringUtils.hasText(dto.getHost()))
            return Result.error("名称与主机为必填项");
        SysDeployTarget t = dto.getId() == null ? new SysDeployTarget() : targetMapper.selectById(dto.getId());
        if (t == null) return Result.error("目标不存在");
        t.setName(dto.getName());
        t.setHost(dto.getHost());
        t.setSshPort(dto.getSshPort() == null ? 22 : dto.getSshPort());
        t.setUsername(StringUtils.hasText(dto.getUsername()) ? dto.getUsername() : "root");
        t.setAuthType(dto.getAuthType() == null ? 1 : dto.getAuthType());
        t.setKeyPath(dto.getKeyPath());
        t.setRemoteDir(StringUtils.hasText(dto.getRemoteDir()) ? dto.getRemoteDir() : "/opt/kaied");
        t.setAppPort(dto.getAppPort() == null ? 8080 : dto.getAppPort());
        t.setWebPort(dto.getWebPort() == null ? 80 : dto.getWebPort());
        t.setDbPassword(StringUtils.hasText(dto.getDbPassword()) ? dto.getDbPassword() : "kaied123");
        // 数据库配置：随包(1)/外部(2)；外部密码填了才更新（编辑留空=不改）
        t.setDbMode(dto.getDbMode() == null ? 1 : dto.getDbMode());
        t.setDbType(StringUtils.hasText(dto.getDbType()) ? dto.getDbType() : "MYSQL");
        t.setDbHost(dto.getDbHost());
        t.setDbPort(dto.getDbPort() == null ? 3306 : dto.getDbPort());
        t.setDbName(StringUtils.hasText(dto.getDbName()) ? dto.getDbName() : "edu_course");
        t.setDbUsername(dto.getDbUsername());
        if (StringUtils.hasText(dto.getDbPasswordExt())) t.setDbSecret(crypto.encrypt(dto.getDbPasswordExt()));
        t.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        // 密码：填了才更新（编辑留空 = 不改密码）
        if (StringUtils.hasText(dto.getPassword())) t.setSecret(crypto.encrypt(dto.getPassword()));
        else if (t.getSecret() == null) return Result.error("请填写 SSH 密码/口令");
        if (t.getCreateTime() == null) targetMapper.insert(t);
        else targetMapper.updateById(t);
        return Result.ok(t.getId());
    }

    @DeleteMapping("/targets/{id}")
    @Log("删除发布服务器")
    public Result<Void> deleteTarget(@PathVariable Long id) {
        platformOnly();
        targetMapper.deleteById(id);
        return Result.ok();
    }

    /** 连通性测试：成功回显系统标识并回写档案，失败抛错 */
    @PostMapping("/targets/{id}/test")
    public Result<String> testTarget(@PathVariable Long id) {
        platformOnly();
        SysDeployTarget t = targetMapper.selectById(id);
        if (t == null) return Result.error("目标不存在");
        String msg;
        boolean ok;
        try {
            msg = ssh.test(t);
            ok = true;
        } catch (Exception e) {
            msg = e.getMessage();
            ok = false;
        }
        t.setLastTestAt(LocalDateTime.now());
        t.setLastTestOk(ok ? 1 : 0);
        t.setLastTestMsg(msg.length() > 480 ? msg.substring(0, 480) : msg);
        targetMapper.updateById(t);
        if (!ok) return Result.error(msg);
        return Result.ok(msg);
    }

    /** 外部数据库连接测试（JDBC 直连，5秒超时），结果回写目标档案 */
    @PostMapping("/targets/{id}/test-db")
    public Result<String> testDb(@PathVariable Long id) {
        platformOnly();
        SysDeployTarget t = targetMapper.selectById(id);
        if (t == null) return Result.error("目标不存在");
        if (t.getDbMode() == null || t.getDbMode() != 2)
            return Result.error("当前为随包MySQL模式，无需连接测试");
        if (!StringUtils.hasText(t.getDbHost()) || !StringUtils.hasText(t.getDbUsername()))
            return Result.error("请先保存数据库地址与用户名");
        String msg;
        boolean ok;
        try {
            String pass = t.getDbSecret() == null ? "" : crypto.decrypt(t.getDbSecret());
            String url = "jdbc:mysql://" + t.getDbHost() + ":" + (t.getDbPort() == null ? 3306 : t.getDbPort())
                    + "/" + (t.getDbName() == null ? "edu_course" : t.getDbName())
                    + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false"
                    + "&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=8000";
            long t0 = System.currentTimeMillis();
            try (java.sql.Connection c = java.sql.DriverManager.getConnection(url, t.getDbUsername(), pass)) {
                msg = "连接成功（" + c.getMetaData().getDatabaseProductVersion() + "，耗时 "
                        + (System.currentTimeMillis() - t0) + "ms）";
            }
            ok = true;
        } catch (Exception e) {
            msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            ok = false;
        }
        t.setLastDbTestAt(LocalDateTime.now());
        t.setLastDbTestOk(ok ? 1 : 0);
        t.setLastDbTestMsg(msg.length() > 480 ? msg.substring(0, 480) : msg);
        targetMapper.updateById(t);
        if (!ok) return Result.error(msg);
        return Result.ok(msg);
    }

    /**
     * 初始化远程数据库：在目标库中自动构建一套初始表结构与数据。
     * 仅含平台管理员（admin/123456），不含演示企业/业务数据——适合全新生产库。
     * 幂等保护：库中已有 sys_user 表时拒绝执行，避免覆盖线上数据。
     */
    @PostMapping("/targets/{id}/init-db")
    @Log("初始化远程数据库")
    public Result<Map<String, Object>> initDb(@PathVariable Long id) {
        platformOnly();
        SysDeployTarget t = targetMapper.selectById(id);
        if (t == null) return Result.error("目标不存在");
        if (t.getDbMode() == null || t.getDbMode() != 2)
            return Result.error("仅外部数据库模式支持手动初始化");
        if (!StringUtils.hasText(t.getDbHost()) || !StringUtils.hasText(t.getDbUsername()))
            return Result.error("请先保存并测试数据库连接");
        String pass = t.getDbSecret() == null ? "" : crypto.decrypt(t.getDbSecret());
        String host = t.getDbHost();
        int port = t.getDbPort() == null ? 3306 : t.getDbPort();
        String base = StringUtils.hasText(t.getDbName()) ? t.getDbName().trim() : "edu_course";
        long t0 = System.currentTimeMillis();
        try {
            String rootUrl = "jdbc:mysql://" + host + ":" + port
                    + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false"
                    + "&allowPublicKeyRetrieval=true&connectTimeout=6000&socketTimeout=120000&allowMultiQueries=false";
            try (java.sql.Connection c = java.sql.DriverManager.getConnection(rootUrl, t.getDbUsername(), pass);
                 java.sql.Statement st = c.createStatement()) {
                // 1) 建库（存在则复用）
                st.execute("CREATE DATABASE IF NOT EXISTS `" + base + "` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci");
                // 2) 已初始化保护
                java.sql.ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='" + base + "' AND table_name='sys_user'");
                rs.next();
                if (rs.getInt(1) > 0)
                    return Result.error("库 " + base + " 已有表结构，为防覆盖拒绝执行；如需重建请先手工清库");
            }
            // 3) 执行精简初始SQL（仅结构 + 平台账号 + 平台模板菜单角色）
            String sql = minimalInitSql();
            String dbUrl = "jdbc:mysql://" + host + ":" + port + "/" + base
                    + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false"
                    + "&allowPublicKeyRetrieval=true&connectTimeout=6000&socketTimeout=300000&allowMultiQueries=true";
            try (java.sql.Connection c = java.sql.DriverManager.getConnection(dbUrl, t.getDbUsername(), pass);
                 java.sql.Statement st = c.createStatement()) {
                for (String s : sql.split(";\\r?\\n")) {
                    String one = s.trim();
                    if (one.isEmpty()) continue;
                    st.execute(one);
                }
            }
            Map<String, Object> r = new HashMap<>();
            r.put("seconds", (System.currentTimeMillis() - t0) / 1000.0);
            r.put("admin", "admin");
            r.put("password", "123456");
            return Result.ok(r);
        } catch (Exception e) {
            return Result.error("初始化失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** 精简初始SQL：全部表结构 + 平台租户(0) + 平台admin + 平台专属菜单（含模板），不含演示企业与业务数据 */
    private String minimalInitSql() {
        StringBuilder sb = new StringBuilder();
        // —— 结构：与全量初始化脚本同构（35张表）——
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS sys_tenant (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64) NOT NULL, code VARCHAR(32) NOT NULL, contact_person VARCHAR(32), contact_phone VARCHAR(20), admin_username VARCHAR(32), address VARCHAR(255), status TINYINT NOT NULL DEFAULT 1, expire_time DATETIME, package_code VARCHAR(32), remark VARCHAR(255), deleted TINYINT NOT NULL DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP, update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, UNIQUE KEY uk_tenant_name (name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_org (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 0, parent_id BIGINT NOT NULL DEFAULT 0, name VARCHAR(64) NOT NULL, leader VARCHAR(32), phone VARCHAR(20), sort INT NOT NULL DEFAULT 0, status TINYINT NOT NULL DEFAULT 1, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_user (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 0, org_id BIGINT, username VARCHAR(32) NOT NULL, password CHAR(32) NOT NULL, real_name VARCHAR(32) NOT NULL, gender TINYINT DEFAULT 1, phone VARCHAR(20), email VARCHAR(64), user_type TINYINT NOT NULL, status TINYINT NOT NULL DEFAULT 1, last_login_time DATETIME, create_time DATETIME DEFAULT CURRENT_TIMESTAMP, avatar MEDIUMBLOB, UNIQUE KEY uk_tenant_username (tenant_id, username)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_menu (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 0, parent_id BIGINT NOT NULL DEFAULT 0, name VARCHAR(32) NOT NULL, path VARCHAR(128), component VARCHAR(128), icon VARCHAR(64), perm VARCHAR(64), type CHAR(1) NOT NULL DEFAULT 'C', sort INT NOT NULL DEFAULT 0, visible TINYINT NOT NULL DEFAULT 1, scope TINYINT NOT NULL DEFAULT 0, is_default TINYINT NOT NULL DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_role (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, name VARCHAR(32) NOT NULL, code VARCHAR(32) NOT NULL, remark VARCHAR(255), create_time DATETIME DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_tenant_role_name (tenant_id, name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_role_menu (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, UNIQUE KEY uk_role_menu (role_id, menu_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_user_role (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, UNIQUE KEY uk_user_role (user_id, role_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_log (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 0, user_id BIGINT, username VARCHAR(32), operation VARCHAR(64), method VARCHAR(255), params TEXT, ip VARCHAR(64), duration INT, status TINYINT, error_msg VARCHAR(512), log_type TINYINT DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_config (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, config_key VARCHAR(64) NOT NULL, config_value VARCHAR(1024), remark VARCHAR(255), UNIQUE KEY uk_config_key (config_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_dict (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 0, dict_type VARCHAR(32) NOT NULL, label VARCHAR(32) NOT NULL, value INT NOT NULL, sort INT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_dict_type (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 0, dict_type VARCHAR(32) NOT NULL, name VARCHAR(64), UNIQUE KEY uk_tenant_dict_type (tenant_id, dict_type)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_notice (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 0, title VARCHAR(128) NOT NULL, content VARCHAR(1000), notice_type TINYINT DEFAULT 0, level TINYINT DEFAULT 0, target VARCHAR(20) DEFAULT 'ALL', target_users VARCHAR(512), create_user_id BIGINT, create_by VARCHAR(64), status TINYINT DEFAULT 0, publish_time DATETIME, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_notice_read (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, notice_id BIGINT NOT NULL, user_id BIGINT NOT NULL, read_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_notice_user (notice_id, user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_notify_config (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, name VARCHAR(64) NOT NULL, event_type VARCHAR(32) NOT NULL, channel TINYINT DEFAULT 0, receivers VARCHAR(255), enabled TINYINT DEFAULT 1, remark VARCHAR(255)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_deploy_target (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64) NOT NULL, host VARCHAR(128) NOT NULL, ssh_port INT NOT NULL DEFAULT 22, username VARCHAR(32) NOT NULL DEFAULT 'root', auth_type TINYINT NOT NULL DEFAULT 1, secret VARCHAR(512) NOT NULL, key_path VARCHAR(255), remote_dir VARCHAR(128) NOT NULL DEFAULT '/opt/kaied', app_port INT NOT NULL DEFAULT 8080, web_port INT NOT NULL DEFAULT 80, db_password VARCHAR(32) NOT NULL DEFAULT 'kaied123', db_mode TINYINT NOT NULL DEFAULT 1, db_type VARCHAR(16) DEFAULT 'MYSQL', db_host VARCHAR(128), db_port INT DEFAULT 3306, db_name VARCHAR(64) DEFAULT 'edu_course', db_username VARCHAR(64), db_secret VARCHAR(512), last_db_test_at DATETIME, last_db_test_ok TINYINT, last_db_test_msg VARCHAR(480), enabled TINYINT NOT NULL DEFAULT 1, last_test_at DATETIME, last_test_ok TINYINT, last_test_msg VARCHAR(480), create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_deploy_record (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, type VARCHAR(16) NOT NULL, version VARCHAR(32), target_id BIGINT, target_name VARCHAR(64), status VARCHAR(16) NOT NULL, file_name VARCHAR(255), action VARCHAR(32), log MEDIUMTEXT, operator VARCHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP, finish_time DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS sys_purge_log (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, tenant_name VARCHAR(64) NOT NULL, operator VARCHAR(64) NOT NULL, operation VARCHAR(64) NOT NULL, detail VARCHAR(512), create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_student (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, user_id BIGINT, name VARCHAR(32) NOT NULL, gender TINYINT DEFAULT 1, birth_date DATE, school VARCHAR(64), grade VARCHAR(32), parent_name VARCHAR(32), parent_phone VARCHAR(20), address VARCHAR(255), remark VARCHAR(255), backup_phone VARCHAR(20), advisor VARCHAR(32), tags VARCHAR(128), deleted TINYINT DEFAULT 0, status TINYINT DEFAULT 1, org_id BIGINT, manager_id BIGINT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_teacher (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, user_id BIGINT, name VARCHAR(32) NOT NULL, gender TINYINT DEFAULT 1, phone VARCHAR(20), email VARCHAR(64), subject VARCHAR(32), title VARCHAR(32), intro VARCHAR(512), hourly_rate DECIMAL(10,2), rate_one2one DECIMAL(10,2), rate_class DECIMAL(10,2), class_types VARCHAR(16), teach_course_ids VARCHAR(255), ban_times TEXT, deleted TINYINT DEFAULT 0, status TINYINT DEFAULT 1, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_course (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, name VARCHAR(64) NOT NULL, category INT, total_hours INT, unit_price DECIMAL(10,2), suit_grade VARCHAR(32), intro VARCHAR(512), status TINYINT DEFAULT 1, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_classroom (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, org_id BIGINT, name VARCHAR(32) NOT NULL, capacity INT, contact VARCHAR(32), contact_phone VARCHAR(20), remark VARCHAR(255), deleted TINYINT DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_clazz (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, org_id BIGINT, name VARCHAR(32) NOT NULL, course_id BIGINT, class_teacher_id BIGINT, capacity INT, start_date DATE, status TINYINT DEFAULT 1, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_package (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, name VARCHAR(64) NOT NULL, hours DECIMAL(10,1) NOT NULL, unit_price DECIMAL(10,2) NOT NULL, suit_course_ids VARCHAR(128), remark VARCHAR(255), status TINYINT DEFAULT 1, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_contract (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, contract_no VARCHAR(32) NOT NULL, student_id BIGINT NOT NULL, package_id BIGINT, course_id BIGINT NOT NULL, class_type TINYINT DEFAULT 1, purchased_hours DECIMAL(10,1), gift_hours DECIMAL(10,1) DEFAULT 0, used_purchased DECIMAL(10,1) DEFAULT 0, used_gift DECIMAL(10,1) DEFAULT 0, unit_price DECIMAL(10,2), total_amount DECIMAL(10,2), paid_amount DECIMAL(10,2) DEFAULT 0, advisor VARCHAR(32), start_date DATE, end_date DATE, status TINYINT DEFAULT 0, refund_hours DECIMAL(10,1), refund_amount DECIMAL(10,2), attachment_url VARCHAR(255), remark VARCHAR(512), create_by VARCHAR(64), org_id BIGINT, deleted TINYINT DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_enroll (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, student_id BIGINT NOT NULL, course_id BIGINT, clazz_id BIGINT, teacher_id BIGINT, total_hours INT DEFAULT 0, used_hours INT DEFAULT 0, price DECIMAL(10,2), discount DECIMAL(5,2) DEFAULT 100, total_amount DECIMAL(10,2), paid_amount DECIMAL(10,2) DEFAULT 0, status TINYINT DEFAULT 1, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_schedule (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, course_id BIGINT, clazz_id BIGINT, student_id BIGINT, teacher_id BIGINT, classroom_id BIGINT, classroom VARCHAR(32), start_time DATETIME, end_time DATETIME, status TINYINT DEFAULT 0, compensation TINYINT DEFAULT 0, remark VARCHAR(255), org_id BIGINT, deleted TINYINT DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP, update_time DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_consume (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, schedule_id BIGINT, student_id BIGINT, teacher_id BIGINT, course_id BIGINT, hours DECIMAL(10,1), remark VARCHAR(255), canceled TINYINT DEFAULT 0, deleted TINYINT DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_payment (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, payment_no VARCHAR(32) NOT NULL, contract_id BIGINT, student_id BIGINT, course_id BIGINT, amount DECIMAL(10,2) NOT NULL, pay_type TINYINT DEFAULT 0, pay_time DATETIME, org_id BIGINT, remark VARCHAR(255), create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_payment_log (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, payment_id BIGINT, action VARCHAR(16), detail VARCHAR(512), operator VARCHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_refund (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, refund_no VARCHAR(32) NOT NULL, contract_id BIGINT, student_id BIGINT, course_id BIGINT, refund_hours DECIMAL(10,1), refund_amount DECIMAL(10,2), refund_to TINYINT NOT NULL DEFAULT 1, remain_hours DECIMAL(10,1), reason VARCHAR(255), operator VARCHAR(64), org_id BIGINT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_leave (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, student_id BIGINT, schedule_id BIGINT, reason VARCHAR(255), status TINYINT DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_absence (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, org_id BIGINT, schedule_id BIGINT, student_id BIGINT, teacher_id BIGINT, course_id BIGINT, clazz_id BIGINT, class_time DATETIME, reason VARCHAR(255), status TINYINT DEFAULT 0, apply_time DATETIME, deadline DATETIME, approver VARCHAR(64), approve_time DATETIME, approve_remark VARCHAR(255), deduct_time DATETIME, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_evaluation (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, student_id BIGINT, teacher_id BIGINT, course_id BIGINT, schedule_id BIGINT, score INT, content VARCHAR(512), create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_consult (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, student_id BIGINT, teacher_id BIGINT, course_id BIGINT, content VARCHAR(512), reply VARCHAR(512), reply_time DATETIME, status TINYINT DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_consult_msg (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, consult_id BIGINT NOT NULL, sender TINYINT NOT NULL, content VARCHAR(1024), create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_schedule_log (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, schedule_id BIGINT, op_type VARCHAR(16), op_detail VARCHAR(64), record_status VARCHAR(16), operator VARCHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_student_log (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, student_id BIGINT, action VARCHAR(16), detail VARCHAR(512), operator VARCHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS edu_account_log (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, student_id BIGINT NOT NULL, contract_id BIGINT, type TINYINT NOT NULL, amount DECIMAL(10,2) NOT NULL, hours DECIMAL(10,1), remark VARCHAR(255), operator VARCHAR(64), create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        };
        for (String d : ddl) sb.append(d).append(";\n");
        // —— 种子数据：平台租户 + 平台admin（密码123456, md5(123456+edu)）+ 平台模板菜单 ——
        sb.append("INSERT INTO sys_tenant (id, name, code, status, remark) VALUES (0, '平台', 'PLATFORM', 1, '平台租户：tenant_id=0 保留值');\n");
        sb.append("INSERT INTO sys_user (tenant_id, username, password, real_name, user_type, status) VALUES (0, 'admin', '60783a8aa981454b3ade403d6577befd', '系统管理员', 1, 1);\n");
        // 平台模板菜单（scope=0 供新企业复制；核心教务/系统管理骨架）
        sb.append("INSERT INTO sys_menu (tenant_id, scope, parent_id, name, path, component, icon, perm, type, sort, visible) VALUES\n");
        sb.append("(0,0,0,'首页概览','home','home/Index','HomeFilled',NULL,'C',0,1),\n");
        sb.append("(0,0,0,'系统管理','/system',NULL,'Setting',NULL,'M',1,1),\n");
        sb.append("(0,0,0,'教务管理','/edu',NULL,'School',NULL,'M',2,1),\n");
        sb.append("(0,0,0,'学生中心','/portal',NULL,'Guide',NULL,'M',3,1),\n");
        sb.append("(0,0,0,'教师中心','/teach',NULL,'Suitcase',NULL,'M',4,1),\n");
        sb.append("(0,0,2,'用户管理','user','system/User','User','sys:user','C',1,1),\n");
        sb.append("(0,0,2,'角色管理','role','system/Role','UserFilled','sys:role','C',2,1),\n");
        sb.append("(0,0,2,'菜单管理','menu','system/Menu','Menu','sys:menu','C',3,1),\n");
        sb.append("(0,0,2,'组织管理','org','system/Org','OfficeBuilding','sys:org','C',4,1),\n");
        sb.append("(0,0,2,'日志管理','log','system/Log','Document','sys:log','C',5,1),\n");
        sb.append("(0,0,2,'通知管理','notify','system/Notify','Bell','sys:notify','C',6,1),\n");
        sb.append("(0,0,2,'数据导入','dataimport','system/DataImport','Upload','sys:import','C',7,1),\n");
        sb.append("(0,0,2,'系统字典','dict','system/Dict','Collection','sys:dict','C',8,1),\n");
        sb.append("(0,0,2,'基础设置','basic-setting','system/BasicSetting','Tools','sys:config','C',9,1),\n");
        sb.append("(0,0,3,'课程管理','course','edu/Course','Reading','edu:course','C',1,1),\n");
        sb.append("(0,0,3,'课时套餐','package','edu/Package','Box','edu:package','C',2,1),\n");
        sb.append("(0,0,3,'班级管理','clazz','edu/Clazz','Files','edu:clazz','C',3,1),\n");
        sb.append("(0,0,3,'教室管理','classroom','edu/Classroom','OfficeBuilding','edu:classroom','C',4,1),\n");
        sb.append("(0,0,3,'排课管理','schedule','edu/Schedule','Calendar','edu:schedule','C',5,1),\n");
        sb.append("(0,0,3,'报名管理','enroll','edu/Enroll','Tickets','edu:enroll','C',6,1),\n");
        sb.append("(0,0,3,'请假管理','leave','edu/Leave','Clock','edu:leave','C',7,1),\n");
        sb.append("(0,0,3,'咨询与评价','consult','edu/Consult','ChatDotRound','edu:consult','C',8,1),\n");
        sb.append("(0,0,3,'签到消课','consume','edu/Consume','Finished','edu:consume','C',9,1),\n");
        sb.append("(0,0,3,'旷课审批','absence','edu/Absence','WarningFilled','edu:absence','C',10,1),\n");
        sb.append("(0,0,3,'课时管理','hours','edu/Hours','Timer','edu:hours','C',11,1),\n");
        sb.append("(0,0,3,'缴费管理','payment','edu/Payment','Money','edu:payment','C',1,1),\n");
        sb.append("(0,0,4,'选课中心','course-select','portal/CourseSelect','Goods','portal','C',1,1),\n");
        sb.append("(0,0,4,'我的课表','my-schedule','portal/MySchedule','Calendar','portal','C',2,1),\n");
        sb.append("(0,0,4,'我的老师','my-teacher','portal/MyTeacher','User','portal','C',3,1),\n");
        sb.append("(0,0,4,'充值记录','recharge-records','portal/Account','Wallet','portal','C',4,1),\n");
        sb.append("(0,0,5,'我的课表','teach-schedule','teach/Schedule','Calendar','teach','C',1,1),\n");
        sb.append("(0,0,5,'我的学生','students','teach/MyStudents','User','teach','C',2,1),\n");
        sb.append("(0,0,5,'收到的评价','evaluations','teach/Evaluations','Star','teach','C',3,1),\n");
        sb.append("(0,0,5,'问题咨询','consults','teach/Consults','ChatDotRound','teach','C',4,1),\n");
        sb.append("(0,0,5,'我的收入','income','teach/Income','Money','teach','C',5,1);\n");
        // 平台专属（scope=1）：平台运营 + 容器管理 + 平台首页
        sb.append("INSERT INTO sys_menu (tenant_id, scope, parent_id, name, path, component, icon, perm, type, sort, visible) VALUES\n");
        sb.append("(0,1,0,'首页概览','home','home/Index','DataLine',NULL,'C',0,1),\n");
        sb.append("(0,1,2,'平台运营','tenant','system/Tenant','Shop','sys:tenant','C',1,1),\n");
        sb.append("(0,1,2,'容器管理','container','system/Container','Box','deploy:manage','C',2,1);\n");
        // 平台基础字典（新企业复制源）
        String[] dicts = {
            "INSERT INTO sys_dict_type (tenant_id, dict_type, name) VALUES (0,'class_type','班型')",
            "INSERT INTO sys_dict_type (tenant_id, dict_type, name) VALUES (0,'course_category','课程分类')",
            "INSERT INTO sys_dict_type (tenant_id, dict_type, name) VALUES (0,'pay_channel','缴费方式')",
            "INSERT INTO sys_dict_type (tenant_id, dict_type, name) VALUES (0,'student_status','学员状态')",
            "INSERT INTO sys_dict_type (tenant_id, dict_type, name) VALUES (0,'contract_status','合同状态')",
            "INSERT INTO sys_dict_type (tenant_id, dict_type, name) VALUES (0,'campus','所属校区')",
            "INSERT INTO sys_dict_type (tenant_id, dict_type, name) VALUES (0,'user_type','用户类型')",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'class_type','一对一',1,1)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'class_type','小组课',2,2)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'class_type','班课',3,3)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'course_category','学科辅导',1,1)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'course_category','艺术培养',2,2)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'course_category','素质拓展',3,3)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'pay_channel','现金',1,1)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'pay_channel','微信',2,2)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'pay_channel','支付宝',3,3)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'pay_channel','银行转账',4,4)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'student_status','在读',1,1)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'student_status','停课',2,2)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'student_status','结课',3,3)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'student_status','退费',4,4)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'contract_status','待缴费',0,1)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'contract_status','部分缴费',1,2)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'contract_status','已结清',2,3)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'contract_status','已退费',3,4)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'contract_status','已到期',4,5)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'user_type','运营总负责人',1,1)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'user_type','教务人员',2,2)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'user_type','班主任',3,3)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'user_type','教师',4,4)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'user_type','学生',5,5)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'user_type','校区校长',6,6)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'user_type','校区咨询师',7,7)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'user_type','校区学管师',8,8)",
            "INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort) VALUES (0,'user_type','系统用户',9,9)"
        };
        for (String d : dicts) sb.append(d).append(";\n");
        return sb.toString();
    }

    /* ==================== 打包 / 部署 / 服务 ==================== */

    /** 打包发布：后端+前端+模板+初始SQL → tar.gz（异步，日志见记录） */
    @PostMapping("/build")
    @Log("打包发布")
    public Result<SysDeployRecord> build(@RequestBody BuildDTO dto) {
        platformOnly();
        String version = dto.getVersion() == null || dto.getVersion().trim().isEmpty()
                ? LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
                : dto.getVersion().trim();
        return Result.ok(deployService.startBuild(version, AuthContext.get().getRealName()));
    }

    /** 部署：把指定打包产物上传到目标服务器并拉起容器（异步） */
    @PostMapping("/{buildId}/deploy")
    @Log("远程部署")
    public Result<SysDeployRecord> deploy(@PathVariable Long buildId, @RequestBody DeployDTO dto) {
        platformOnly();
        if (dto.getTargetId() == null) return Result.error("请选择目标服务器");
        SysDeployTarget t = targetMapper.selectById(dto.getTargetId());
        if (t == null) return Result.error("目标服务器不存在");
        if (t.getEnabled() == null || t.getEnabled() != 1) return Result.error("目标已停用");
        return Result.ok(deployService.deploy(buildId, t, AuthContext.get().getRealName()));
    }

    /** 远程服务操作：status/start/stop/restart/logs */
    @PostMapping("/targets/{id}/service")
    @Log("远程服务操作")
    public Result<SysDeployRecord> service(@PathVariable Long id, @RequestBody ServiceDTO dto) {
        platformOnly();
        SysDeployTarget t = targetMapper.selectById(id);
        if (t == null) return Result.error("目标服务器不存在");
        return Result.ok(deployService.serviceOp(t, dto.getAction(), AuthContext.get().getRealName()));
    }

    /* ==================== 发布记录与日志 ==================== */

    @GetMapping("/records")
    public Result<PageResult<SysDeployRecord>> records(@RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size,
                                                       @RequestParam(required = false) String type) {
        platformOnly();
        Page<SysDeployRecord> p = recordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysDeployRecord>()
                        // REMOTE = 远程日志视图：部署 + 服务操作（不含本地打包）
                        .eq("BUILD".equals(type), SysDeployRecord::getType, "BUILD")
                        .in("REMOTE".equals(type), SysDeployRecord::getType, java.util.Arrays.asList("DEPLOY", "SERVICE"))
                        .eq(StringUtils.hasText(type) && !"BUILD".equals(type) && !"REMOTE".equals(type),
                                SysDeployRecord::getType, type)
                        .orderByDesc(SysDeployRecord::getId));
        p.getRecords().forEach(r -> r.setLog(null));   // 列表不带日志（体积大），详情接口取
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/records/{id}")
    public Result<SysDeployRecord> record(@PathVariable Long id) {
        platformOnly();
        return Result.ok(recordMapper.selectById(id));
    }

    @DeleteMapping("/records/{id}")
    @Log("删除发布记录")
    public Result<Void> deleteRecord(@PathVariable Long id) {
        platformOnly();
        recordMapper.deleteById(id);
        return Result.ok();
    }

    @Data
    public static class TargetDTO {
        private Long id;
        private String name;
        private String host;
        private Integer sshPort;
        private String username;
        private Integer authType;
        /** 仅创建/改密时填写；编辑留空=不修改 */
        private String password;
        private String keyPath;
        private String remoteDir;
        private Integer appPort;
        private Integer webPort;
        private String dbPassword;
        private Integer dbMode;
        private String dbType;
        private String dbHost;
        private Integer dbPort;
        private String dbName;
        private String dbUsername;
        /** 外部数据库密码：仅创建/改密时填写；编辑留空=不修改 */
        private String dbPasswordExt;
        private Integer enabled;
    }

    @Data
    public static class BuildDTO { private String version; }

    @Data
    public static class DeployDTO { private Long targetId; }

    @Data
    public static class ServiceDTO { private String action; }
}
