package com.edu.service;

import com.edu.entity.SysDeployRecord;
import com.edu.entity.SysDeployTarget;
import com.edu.mapper.SysDeployRecordMapper;
import com.edu.mapper.SysDeployTargetMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 容器管理编排：本机打包（mvn + npm + tar.gz）→ SFTP 上传 → 远端 deploy.sh 拉起容器；
 * 打包/部署/服务操作全程写 sys_deploy_record 日志供前端轮询。
 * 参考 10_WRJ 的发布链路，适配本系统（SpringBoot2.7+MySQL8+Vue3）。
 */
@Service
@RequiredArgsConstructor
public class ContainerDeployService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SysDeployRecordMapper recordMapper;
    private final SysDeployTargetMapper targetMapper;
    private final SshService ssh;
    private final com.edu.security.DeployCrypto crypto;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Value("${edu.deploy.project-root:}")
    private String projectRootCfg;

    /** 打包/部署单线程串行，避免并发覆盖 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "container-deploy");
        t.setDaemon(true);
        return t;
    });

    /* ==================== 打包 ==================== */

    /** 发起打包：后端 mvn package + 前端 npm build + 模板 + 初始SQL → tar.gz；记录即时落库 */
    public SysDeployRecord startBuild(String version, String operator) {
        SysDeployRecord r = new SysDeployRecord();
        r.setType("BUILD");
        r.setVersion(version);
        r.setStatus("RUNNING");
        r.setOperator(operator);
        recordMapper.insert(r);
        final Long id = r.getId();
        executor.submit(() -> {
            StringBuilder log = new StringBuilder();
            try {
                Path root = projectRoot();
                log.append('[').append(now()).append("] 项目根目录: ").append(root).append('\n');

                // 1) 后端打包
                log.append('[').append(now()).append("] ① 后端 mvn clean package（约1-3分钟）...\n");
                flush(id, log);
                runCommand(log, root, 15, cmd("mvn"), "package", "-DskipTests", "-f", "edu-server/pom.xml");
                final File[] jarHolder = new File[1];
                try (java.util.stream.Stream<Path> st = Files.list(root.resolve("edu-server/target").normalize())) {
                    // 取最大 jar：spring-boot repackage 后主产物即完整可执行 jar
                    jarHolder[0] = st.filter(f -> f.getFileName().toString().endsWith(".jar"))
                            .max(Comparator.comparingLong(p2 -> p2.toFile().length()))
                            .map(Path::toFile).orElse(null);
                }
                if (jarHolder[0] == null) throw new IllegalStateException("未找到后端 jar 产物");
                log.append('[').append(now()).append("] 后端产物: ").append(jarHolder[0].getName()).append('\n');

                // 2) 前端打包
                log.append('[').append(now()).append("] ② 前端 npm run build（约1分钟）...\n");
                flush(id, log);
                runCommand(log, root.resolve("edu-web"), 10, cmd("npm"), "run", "build");

                // 3) 组装发布包
                String fileName = "kaied-" + version + ".tar.gz";
                Path out = root.resolve("deploy-stage").resolve(fileName);
                Files.createDirectories(out.getParent());
                log.append('[').append(now()).append("] ③ 组装发布包: ").append(fileName).append('\n');
                flush(id, log);
                tarGz(out, root, jarHolder[0]);
                log.append('[').append(now()).append("] 打包完成: ").append(out).append(" (")
                        .append(out.toFile().length() / 1024 / 1024).append(" MB)\n");

                r.setFileName(fileName);
                r.setStatus("SUCCESS");
                r.setFinishTime(LocalDateTime.now());
                r.setLog(log.toString());
                recordMapper.updateById(r);
            } catch (Throwable e) {
                fail(id, log, e instanceof Exception ? (Exception) e : new IllegalStateException(String.valueOf(e)));
            }
        });
        return r;
    }

    /** 发布包内容：app.jar + dist + sql初始化 + Dockerfile/compose/deploy.sh/nginx */
    private void tarGz(Path out, Path root, File jar) throws Exception {
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(
                new java.util.zip.GZIPOutputStream(new FileOutputStream(out.toFile())))) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            addFile(tar, jar.toPath(), "app.jar");
            addDir(tar, root.resolve("edu-web/dist"), "dist");
            // 初始SQL：精简版（仅平台admin+模板），全新部署干净起步；企业admin由平台创建企业时自动生成
            Path initSql = out.resolveSibling("init.sql");
            java.nio.file.Files.write(initSql, minimalInitSql().getBytes("UTF-8"));
            addFile(tar, initSql, "sql/init.sql");
            java.nio.file.Files.deleteIfExists(initSql);
            for (String tpl : Arrays.asList("Dockerfile", "compose-bundled.yml", "compose-external.yml", "deploy.sh", "nginx.conf.tpl")) {
                try (InputStream in = new ClassPathResource("deploy/" + tpl).getInputStream()) {
                    // 统一转 LF：源文件被 Windows 编辑器/git 转成 CRLF 时，远端 bash 会因 \r 报错
                    byte[] bytes = org.apache.commons.io.IOUtils.toByteArray(in);
                    Path tmp = Files.createTempFile("tpl-", tpl);
                    java.nio.file.Files.write(tmp, new String(bytes, "UTF-8").replace("\r\n", "\n").getBytes("UTF-8"));
                    addFile(tar, tmp, tpl);
                    Files.deleteIfExists(tmp);
                }
            }
        }
    }

    private void addFile(TarArchiveOutputStream tar, Path file, String name) throws Exception {
        TarArchiveEntry e = new TarArchiveEntry(file.toFile(), name);
        e.setMode(0644);
        tar.putArchiveEntry(e);
        try (InputStream in = new FileInputStream(file.toFile())) { IOUtils.copy(in, tar); }
        tar.closeArchiveEntry();
    }

    private void addDir(TarArchiveOutputStream tar, Path dir, String prefix) throws Exception {
        if (!Files.exists(dir)) throw new IllegalStateException("前端产物缺失: " + dir);
        Files.walk(dir).filter(Files::isRegularFile).forEach(f -> {
            try {
                String name = prefix + "/" + dir.relativize(f).toString().replace('\\', '/');
                TarArchiveEntry e = new TarArchiveEntry(f.toFile(), name);
                e.setMode(0644);
                tar.putArchiveEntry(e);
                try (InputStream in = new FileInputStream(f.toFile())) { IOUtils.copy(in, tar); }
                tar.closeArchiveEntry();
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }

    /* ==================== 部署 ==================== */

    /** 发起部署：上传 tar.gz → 远端解包生成 .env → deploy.sh 拉起容器 */
    public SysDeployRecord deploy(Long buildId, SysDeployTarget t, String operator) {
        SysDeployRecord build = recordMapper.selectById(buildId);
        if (build == null || !"BUILD".equals(build.getType())) throw new IllegalArgumentException("打包记录不存在");
        if (!"SUCCESS".equals(build.getStatus()) || build.getFileName() == null)
            throw new IllegalArgumentException("该打包记录未成功，无法部署");

        SysDeployRecord r = new SysDeployRecord();
        r.setType("DEPLOY");
        r.setVersion(build.getVersion());
        r.setTargetId(t.getId());
        r.setTargetName(t.getName());
        r.setStatus("RUNNING");
        r.setOperator(operator);
        recordMapper.insert(r);
        final Long id = r.getId();
        executor.submit(() -> {
            StringBuilder log = new StringBuilder();
            try {
                Path pkg = projectRoot().resolve("deploy-stage").resolve(build.getFileName());
                if (!Files.exists(pkg)) throw new IllegalStateException("产物已不存在: " + pkg);
                String remoteDir = t.getRemoteDir() == null ? "/opt/kaied" : t.getRemoteDir();
                String remotePkg = remoteDir + "/" + build.getFileName();

                log.append('[').append(now()).append("] ① 上传发布包到 ").append(t.getHost())
                        .append(':').append(remotePkg).append("（").append(pkg.toFile().length() / 1024 / 1024).append(" MB）...\n");
                flush(id, log);
                ssh.exec(t, "mkdir -p " + remoteDir + "/release", 20);
                ssh.upload(t, pkg, remotePkg);

                log.append('[').append(now()).append("] ② 远端解包并生成环境配置...\n");
                flush(id, log);
                // 数据库拓扑：随包MySQL(kaied-db容器) 或 外部数据库（容器不带 db，应用直连）
                boolean externalDb = t.getDbMode() != null && t.getDbMode() == 2;
                StringBuilder envSb = new StringBuilder()
                        .append("APP_PORT=").append(nz(t.getAppPort(), 8080)).append('\n')
                        .append("WEB_PORT=").append(nz(t.getWebPort(), 80)).append('\n')
                        .append("DB_MODE=").append(externalDb ? "EXTERNAL" : "BUNDLED").append('\n');
                if (externalDb) {
                    String dbPass = t.getDbSecret() == null ? "" : crypto.decrypt(t.getDbSecret());
                    envSb.append("DB_HOST=").append(nz2(t.getDbHost(), "127.0.0.1")).append('\n')
                            .append("DB_PORT=").append(nz(t.getDbPort(), 3306)).append('\n')
                            .append("DB_NAME=").append(nz2(t.getDbName(), "edu_course")).append('\n')
                            .append("DB_USER=").append(nz2(t.getDbUsername(), "root")).append('\n')
                            .append("DB_PASS=").append(dbPass).append('\n')
                            .append("SPRING_DATASOURCE_URL=jdbc:mysql://").append(t.getDbHost()).append(":")
                            .append(nz(t.getDbPort(), 3306)).append("/").append(nz2(t.getDbName(), "edu_course"))
                            .append("?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true\n")
                            .append("SPRING_DATASOURCE_USERNAME=").append(nz2(t.getDbUsername(), "root")).append('\n')
                            .append("SPRING_DATASOURCE_PASSWORD=").append(dbPass).append('\n');
                    log.append('[').append(now()).append("] 数据库模式: 外部 ").append(t.getDbHost())
                            .append(':').append(nz(t.getDbPort(), 3306)).append('/').append(nz2(t.getDbName(), "edu_course")).append('\n');
                } else {
                    envSb.append("DB_PASSWORD=").append(nz2(t.getDbPassword(), "kaied123")).append('\n')
                            .append("SPRING_DATASOURCE_URL=jdbc:mysql://kaied-db:3306/edu_course?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true\n")
                            .append("SPRING_DATASOURCE_USERNAME=root\n")
                            .append("SPRING_DATASOURCE_PASSWORD=").append(nz2(t.getDbPassword(), "kaied123")).append('\n');
                    log.append('[').append(now()).append("] DB mode: bundled MySQL").append('\n');
                }
                String env = envSb.toString();
                String setup = "cd " + remoteDir + "/release && tar -xzf ../" + build.getFileName()
                        + " && printf '" + env.replace("\n", "\\n").replace("'", "'\\''") + "' > .env"
                        + " && if [ \"$(grep '^DB_MODE=' .env | cut -d= -f2)\" = 'EXTERNAL' ]; then cp compose-external.yml docker-compose.yml; else cp compose-bundled.yml docker-compose.yml; fi"
                        + " && chmod +x deploy.sh && ls -l";
                log.append(ssh.exec(t, setup, 60)).append('\n');

                log.append('[').append(now()).append("] ③ 拉起容器（db+app+web，首次较慢）...\n");
                flush(id, log);
                String out = ssh.exec(t, "cd " + remoteDir + "/release && bash deploy.sh 2>&1", 900);
                log.append(out).append('\n');

                r.setStatus("SUCCESS");
                r.setFinishTime(LocalDateTime.now());
                r.setLog(log + "[" + now() + "] 部署完成：访问 http://" + t.getHost() + ":" + nz(t.getWebPort(), 80) + "\n");
                recordMapper.updateById(r);
            } catch (Throwable e) {
                fail(id, log, e instanceof Exception ? (Exception) e : new IllegalStateException(String.valueOf(e)));
            }
        });
        return r;
    }

    /* ==================== 远程服务操作 ==================== */

    /** 服务操作：status/start/stop/restart/logs（同步执行，日志落记录） */
    public SysDeployRecord serviceOp(SysDeployTarget t, String action, String operator) {
        if (!"status".equals(action)) {
            // 未部署过（release 目录不存在）时给出友好提示，而非裸 cd 报错
            String remoteDir0 = t.getRemoteDir() == null ? "/opt/kaied" : t.getRemoteDir();
            try {
                ssh.exec(t, "test -d " + remoteDir0 + "/release", 15);
            } catch (Exception e) {
                SysDeployRecord miss = new SysDeployRecord();
                miss.setType("SERVICE");
                miss.setTargetId(t.getId());
                miss.setTargetName(t.getName());
                miss.setAction(action);
                miss.setStatus("FAILED");
                miss.setOperator(operator);
                miss.setLog("尚未部署：" + remoteDir0 + "/release 不存在。\n请先在「发布记录」中对某个打包产物执行「部署」，"
                        + "部署成功后再进行服务操作。\n如部署过但目录被清理，重新部署一次即可。");
                miss.setFinishTime(LocalDateTime.now());
                recordMapper.insert(miss);
                throw new IllegalArgumentException(miss.getLog());
            }
        }
        String cmd;
        switch (action == null ? "" : action) {
            case "status":   cmd = "cd %s 2>/dev/null && $DC ps 2>/dev/null; docker ps --format '{{.Names}}  {{.Status}}' 2>/dev/null | head -10; echo '(提示: kaied-* 为本系统容器,不存在则尚未部署)'"; break;
            case "start":    cmd = "cd %s && $DC up -d --build && sleep 4 && $DC ps"; break;
            case "stop":     cmd = "cd %s && $DC down"; break;
            case "restart":  cmd = "cd %s && $DC restart && sleep 4 && $DC ps"; break;
            case "logs":     cmd = "cd %s && $DC logs --tail=120"; break;
            default: throw new IllegalArgumentException("不支持的操作: " + action);
        }
        String remoteDir = t.getRemoteDir() == null ? "/opt/kaied" : t.getRemoteDir();
        String full = "DC='docker compose'; command -v docker-compose >/dev/null 2>&1 && DC=docker-compose; "
                + String.format(cmd, remoteDir + "/release");

        SysDeployRecord r = new SysDeployRecord();
        r.setType("SERVICE");
        r.setTargetId(t.getId());
        r.setTargetName(t.getName());
        r.setAction(action);
        r.setOperator(operator);
        try {
            String out = ssh.exec(t, full, 900);
            r.setStatus("SUCCESS");
            r.setLog(out);
        } catch (Exception e) {
            r.setStatus("FAILED");
            r.setLog(e.getMessage());
        }
        r.setFinishTime(LocalDateTime.now());
        recordMapper.insert(r);
        return r;
    }

    /* ==================== 工具 ==================== */

    /** 项目根目录：默认取运行目录的上级（edu-server 的父目录），可用 yml 覆盖 */
    private Path projectRoot() {
        if (projectRootCfg != null && !projectRootCfg.trim().isEmpty())
            return new File(projectRootCfg.trim()).toPath().normalize();
        return new File(System.getProperty("user.dir")).toPath().normalize().getParent();
    }

    private boolean windows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    /** Windows 下用 cmd /c 包一层（mvn/npm 为 .cmd 脚本） */
    private String[] cmd(String bin) { return windows() ? new String[]{"cmd", "/c", bin} : new String[]{bin}; }

    private void runCommand(StringBuilder log, Path dir, int timeoutMin, String[] prefix, String... args) throws Exception {
        String[] cmd = new String[prefix.length + args.length];
        System.arraycopy(prefix, 0, cmd, 0, prefix.length);
        System.arraycopy(args, 0, cmd, prefix.length, args.length);
        String line = String.join(" ", cmd);
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        byte[] buf = new byte[8192];
        StringBuilder out = new StringBuilder();
        long deadline = System.currentTimeMillis() + timeoutMin * 60_000L;
        try (InputStream in = p.getInputStream()) {
            while (true) {
                int n = in.read(buf);
                if (n > 0) {
                    String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                    out.append(chunk);
                    // 只保留尾部输出到日志，避免超长
                    if (out.length() > 20000) out.delete(0, out.length() - 20000);
                }
                if (!p.isAlive() && in.available() == 0) break;
                if (System.currentTimeMillis() > deadline) {
                    p.destroyForcibly();
                    throw new IllegalStateException("命令超时: " + line);
                }
                Thread.sleep(50);
            }
        }
        int exit = p.waitFor();
        log.append(out).append('\n');
        if (exit != 0) throw new IllegalStateException("命令失败(exit " + exit + "): " + line + "\n" + tail(out.toString(), 1500));
    }

    private void flush(Long id, StringBuilder log) {
        SysDeployRecord r = recordMapper.selectById(id);
        if (r != null) { r.setLog(log.toString()); recordMapper.updateById(r); }
    }

    private void fail(Long id, StringBuilder log, Exception e) {
        SysDeployRecord r = recordMapper.selectById(id);
        if (r != null) {
            r.setStatus("FAILED");
            r.setFinishTime(LocalDateTime.now());
            r.setLog(log + "\n[FAILED] " + e.getMessage());
            recordMapper.updateById(r);
        }
    }

    private static int nz(Integer v, int dft) { return v == null ? dft : v; }
    private static String nz(String v, String dft) { return v == null || v.isEmpty() ? dft : v; }
    private static String nz2(String v, String dft) { return v == null || v.trim().isEmpty() ? dft : v.trim(); }
    private static String now() { return LocalDateTime.now().format(TS); }
    private static String tail(String s, int max) {
        return s == null ? "" : (s.length() > max ? "... " + s.substring(s.length() - max) : s);
    }

    /**
     * 精简初始SQL（运行时从本库实时导出）：
     * 结构 = SHOW CREATE TABLE（与运行系统完全一致，杜绝手写漂移）；
     * 种子 = 平台租户(0) + 平台管理员 + 全量菜单模板(scope=0/1) + 基础字典，
     * 不含演示企业与业务数据。企业admin由平台创建企业时自动生成。
     */
    public String minimalInitSql() {
        StringBuilder sb = new StringBuilder();
        // —— 表结构：本库全部表 ——
        List<String> tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME", String.class);
        for (String t : tables) {
            String ddl = jdbc.queryForMap("SHOW CREATE TABLE `" + t + "`")
                    .get("Create Table").toString();
            ddl = ddl.replaceAll(" AUTO_INCREMENT=\\d+", "");
            sb.append(ddl).append(";\n");
        }
        // —— 种子数据 ——
        appendInserts(sb, "sys_tenant", "SELECT * FROM sys_tenant WHERE id = 0");
        appendInserts(sb, "sys_user", "SELECT * FROM sys_user WHERE tenant_id = 0");
        appendInserts(sb, "sys_menu", "SELECT * FROM sys_menu WHERE tenant_id = 0 ORDER BY id");
        appendInserts(sb, "sys_dict_type", "SELECT * FROM sys_dict_type WHERE tenant_id = 0");
        appendInserts(sb, "sys_dict", "SELECT * FROM sys_dict WHERE tenant_id = 0 ORDER BY dict_type, sort, value");
        return sb.toString();
    }

    /** 按查询结果生成 INSERT（值转义：null→NULL，字符串加引号并转义单引号） */
    private void appendInserts(StringBuilder sb, String table, String query) {
        List<Map<String, Object>> rows = jdbc.queryForList(query);
        for (Map<String, Object> row : rows) {
            StringBuilder cols = new StringBuilder();
            StringBuilder vals = new StringBuilder();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (cols.length() > 0) { cols.append(", "); vals.append(", "); }
                cols.append("`").append(e.getKey()).append("`");
                Object v = e.getValue();
                if (v == null) vals.append("NULL");
                else if (v instanceof Number) vals.append(v);
                else vals.append("'").append(String.valueOf(v).replace("\\", "\\\\").replace("'", "\\'")).append("'");
            }
            sb.append("INSERT INTO `").append(table).append("` (").append(cols).append(") VALUES (").append(vals).append(");\n");
        }
    }
}

