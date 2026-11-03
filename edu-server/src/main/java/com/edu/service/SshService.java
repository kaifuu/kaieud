package com.edu.service;

import com.edu.entity.SysDeployTarget;
import com.edu.security.DeployCrypto;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * SSH/SFTP 远程执行（jsch）：目标服务器命令执行、产物上传、连通性测试。
 * 每次操作独立会话，用完即断；StrictHostKeyChecking 关闭便于首连。
 */
@Service
@RequiredArgsConstructor
public class SshService {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private final DeployCrypto crypto;

    /** 连通性测试：回显目标机系统标识 */
    public String test(SysDeployTarget t) {
        String cmd = "echo __OK__ && (grep PRETTY_NAME /etc/os-release 2>/dev/null || head -1 /etc/issue 2>/dev/null || uname -a)";
        String out = exec(t, cmd, 20);
        String trimmed = out.replace("__OK__", "").trim();
        return trimmed.isEmpty() ? "连接成功" : trimmed;
    }

    /** 执行远程命令，合并 stdout/stderr；非零退出码抛出并携带输出尾部 */
    public String exec(SysDeployTarget t, String cmd, int timeoutSeconds) {
        Session session = null;
        try {
            session = open(t);
            ChannelExec ch = (ChannelExec) session.openChannel("exec");
            ch.setCommand(cmd);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ch.setErrStream(out);
            InputStream in = ch.getInputStream();
            ch.connect(CONNECT_TIMEOUT_MS);
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
            byte[] buf = new byte[8192];
            while (true) {
                while (in.available() > 0) {
                    int n = in.read(buf, 0, buf.length);
                    if (n < 0) break;
                    out.write(buf, 0, n);
                }
                if (ch.isClosed()) {
                    if (in.available() > 0) continue;
                    break;
                }
                if (System.currentTimeMillis() > deadline) {
                    ch.disconnect();
                    throw new IllegalArgumentException("远程命令超时(" + timeoutSeconds + "s)，已输出:\n"
                            + tail(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8), 1500));
                }
                Thread.sleep(30);
            }
            int exit = ch.getExitStatus();
            ch.disconnect();
            String text = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            if (exit != 0) {
                throw new IllegalArgumentException("远程命令退出码 " + exit + ": " + tail(text, 2000));
            }
            return text;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("SSH 执行失败(" + t.getHost() + "): " + rootMsg(e), e);
        } finally {
            if (session != null) session.disconnect();
        }
    }

    /** SFTP 上传文件到远端绝对路径 */
    public void upload(SysDeployTarget t, Path local, String remoteFile) {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = open(t);
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT_MS);
            sftp.put(local.toString(), remoteFile);
        } catch (Exception e) {
            throw new IllegalArgumentException("SFTP 上传失败(" + t.getHost() + "): " + rootMsg(e), e);
        } finally {
            if (sftp != null) sftp.disconnect();
            if (session != null) session.disconnect();
        }
    }

    private Session open(SysDeployTarget t) throws Exception {
        JSch jsch = new JSch();
        String pass = crypto.decrypt(t.getSecret());
        if (t.getAuthType() != null && t.getAuthType() == 2) {
            String keyPath = t.getKeyPath() == null ? "" : t.getKeyPath().replace("~", System.getProperty("user.home"));
            if (keyPath.isEmpty()) throw new IllegalArgumentException("私钥认证须配置私钥文件路径");
            jsch.addIdentity(keyPath, pass);
        }
        Session session = jsch.getSession(
                t.getUsername() == null ? "root" : t.getUsername(),
                t.getHost(), t.getSshPort() == null ? 22 : t.getSshPort());
        if (t.getAuthType() == null || t.getAuthType() != 2) {
            session.setPassword(pass);
        }
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(CONNECT_TIMEOUT_MS);
        return session;
    }

    private static String rootMsg(Exception e) {
        Throwable cur = e;
        for (int i = 0; i < 5 && cur.getCause() != null; i++) cur = cur.getCause();
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }

    private static String tail(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > max ? "... " + s.substring(s.length() - max) : s;
    }
}
