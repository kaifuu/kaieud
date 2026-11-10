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
            String sql = deployService.minimalInitSql();
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

    /**
     * 补丁升级：把 jar 内 sql/patch_*.sql 增量脚本逐条执行到目标外部库。
     * 可重复执行：重复列/重复表(1050/1060/1061/1062)自动跳过并计数，其余错误逐条记录不中断。
     */
    @PostMapping("/targets/{id}/patch-db")
    @Log("补丁升级远程数据库")
    public Result<Map<String, Object>> patchDb(@PathVariable Long id) {
        platformOnly();
        SysDeployTarget t = targetMapper.selectById(id);
        if (t == null) return Result.error("目标不存在");
        if (t.getDbMode() == null || t.getDbMode() != 2)
            return Result.error("仅外部数据库模式支持补丁升级");
        if (!StringUtils.hasText(t.getDbHost()) || !StringUtils.hasText(t.getDbUsername()))
            return Result.error("请先保存并测试数据库连接");

        // 扫描 jar 内全部 sql/patch_*.sql，按文件名版本号升序逐个执行（新增补丁放进资源目录即可被升级）
        org.springframework.core.io.Resource[] resources;
        try {
            resources = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                    .getResources("classpath:sql/patch_*.sql");
        } catch (Exception e) {
            return Result.error("补丁文件扫描失败：" + e.getMessage());
        }
        List<org.springframework.core.io.Resource> patches = new java.util.ArrayList<>();
        for (org.springframework.core.io.Resource r : resources) patches.add(r);
        patches.sort(java.util.Comparator.comparingLong(DeployController::patchVerOf));
        if (patches.isEmpty()) return Result.error("未找到补丁文件（jar 内 sql/patch_*.sql）");

        String pass = t.getDbSecret() == null ? "" : crypto.decrypt(t.getDbSecret());
        String base = StringUtils.hasText(t.getDbName()) ? t.getDbName().trim() : "edu_course";
        String url = "jdbc:mysql://" + t.getDbHost() + ":" + (t.getDbPort() == null ? 3306 : t.getDbPort()) + "/" + base
                + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false"
                + "&allowPublicKeyRetrieval=true&connectTimeout=6000&socketTimeout=300000&allowMultiQueries=false";
        long t0 = System.currentTimeMillis();
        StringBuilder log = new StringBuilder();
        int applied = 0, skipped = 0, failed = 0;
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(url, t.getDbUsername(), pass);
             java.sql.Statement st = c.createStatement()) {
            for (org.springframework.core.io.Resource res : patches) {
                String patch;
                try (java.io.InputStream in = res.getInputStream()) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) > 0) bos.write(b, 0, n);
                    patch = new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                }
                log.append("========== ").append(res.getFilename()).append(" ==========\n");
                for (String raw : patch.split(";\\r?\\n")) {
                    // 去掉注释行后合并为单条语句
                    StringBuilder one = new StringBuilder();
                    for (String line : raw.split("\\r?\\n")) {
                        if (line.trim().startsWith("--")) continue;
                        one.append(line).append('\n');
                    }
                    String sql = one.toString().trim();
                    if (sql.isEmpty()) continue;
                    String head = sql.length() > 60 ? sql.substring(0, 60) + "…" : sql;
                    try {
                        st.execute(sql);
                        applied++;
                        log.append("[OK]      ").append(head).append('\n');
                    } catch (java.sql.SQLException e) {
                        int code = e.getErrorCode();
                        if (code == 1050 || code == 1060 || code == 1061 || code == 1062) {
                            skipped++;
                            log.append("[SKIP]    ").append(head).append("  (").append(e.getMessage()).append(")\n");
                        } else {
                            failed++;
                            log.append("[FAIL]    ").append(head).append("  (").append(e.getMessage()).append(")\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            return Result.error("补丁执行失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    + "\n" + log);
        }
        Map<String, Object> r = new HashMap<>();
        r.put("applied", applied);
        r.put("skipped", skipped);
        r.put("failed", failed);
        r.put("seconds", (System.currentTimeMillis() - t0) / 1000.0);
        r.put("log", log.toString());
        return Result.ok(r);
    }

    /** 补丁文件版本号：patch_v2.55.sql → 2*100000+55，用于升序执行 */
    private static long patchVerOf(org.springframework.core.io.Resource r) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("v(\\d+)\\.(\\d+)").matcher(r.getFilename() == null ? "" : r.getFilename());
        return m.find() ? Long.parseLong(m.group(1)) * 100000L + Long.parseLong(m.group(2)) : 0L;
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
