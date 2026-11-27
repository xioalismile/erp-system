package com.erp.deploy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.deploy.entity.DeployLog;
import com.erp.deploy.entity.DeployServer;
import com.erp.deploy.mapper.DeployLogMapper;
import com.erp.deploy.mapper.DeployServerMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 客户升级部署/回滚服务：通过 SSH/SFTP 直连客户服务器执行部署
 * agent（update-agent.ps1）负责服务器端：备份→替换→重启→健康检查→失败自动回滚
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeployService {

    private final DeployServerMapper serverMapper;
    private final DeployLogMapper logMapper;

    /** 正在执行的服务器ID（同服务器串行，防并发部署） */
    private final Set<Long> runningServers = ConcurrentHashMap.newKeySet();
    /** 运行中任务的实时日志 logId -> builder */
    private final Map<Long, StringBuilder> liveLogs = new ConcurrentHashMap<>();

    // ============ 服务器配置 CRUD ============
    public List<DeployServer> listServers() {
        return serverMapper.selectList(new LambdaQueryWrapper<DeployServer>().orderByAsc(DeployServer::getId));
    }

    public DeployServer saveServer(DeployServer s) {
        s.setUpdatedAt(LocalDateTime.now());
        if (s.getId() == null) {
            if (s.getEnabled() == null) s.setEnabled(1);
            serverMapper.insert(s);
        } else {
            // 密码留空 = 不修改
            if (s.getSshPassword() == null || s.getSshPassword().isEmpty()) {
                DeployServer old = serverMapper.selectById(s.getId());
                if (old != null) s.setSshPassword(old.getSshPassword());
            }
            // 数据库密码留空 = 不修改（与SSH密码同策略）
            if (s.getDbPassword() == null || s.getDbPassword().isEmpty()) {
                DeployServer old = serverMapper.selectById(s.getId());
                if (old != null) s.setDbPassword(old.getDbPassword());
            }
            serverMapper.updateById(s);
        }
        return s;
    }

    public void deleteServer(Long id) {
        serverMapper.deleteById(id);
    }

    // ============ 部署 ============
    public Long startDeploy(Long serverId, MultipartFile zipFile, String note, String operator) {
        if (zipFile == null || zipFile.isEmpty()) throw new IllegalArgumentException("请上传更新包zip");
        Path tmpZip = null;
        try {
            tmpZip = Files.createTempFile("deploy-", ".zip");
            zipFile.transferTo(tmpZip.toFile());
        } catch (Exception e) {
            if (tmpZip != null) try { Files.deleteIfExists(tmpZip); } catch (Exception ignored) {}
            throw new IllegalArgumentException("保存上传包失败: " + e.getMessage());
        }
        return startDeploy(serverId, tmpZip, zipFile.getOriginalFilename(),
                zipFile.getSize(), note, operator, true);
    }

    /** 从本地文件部署（版本库调用；keepFile=true 不删除源文件） */
    public Long startDeploy(Long serverId, Path zip, String note, String operator) {
        if (zip == null || !Files.exists(zip)) throw new IllegalArgumentException("部署包不存在: " + zip);
        try {
            return startDeploy(serverId, zip, zip.getFileName().toString(),
                    Files.size(zip), note, operator, true);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("读取部署包失败: " + e.getMessage());
        }
    }

    private Long startDeploy(Long serverId, Path zip, String displayFileName, long fileSize,
                             String note, String operator, boolean keepFile) {
        DeployServer s = serverMapper.selectById(serverId);
        if (s == null) throw new IllegalArgumentException("服务器不存在: " + serverId);
        if (!runningServers.add(serverId)) throw new IllegalStateException("该服务器有任务正在执行，请稍候");

        // 上传前计算 MD5（传输完整性校验：agent 收到 -ZipMd5 后先比对，不匹配零触碰退出）
        final String zipMd5;
        try {
            zipMd5 = md5(zip);
        } catch (Exception e) {
            runningServers.remove(serverId);
            throw new IllegalArgumentException("计算更新包MD5失败: " + e.getMessage());
        }

        DeployLog dl = new DeployLog();
        dl.setServerId(serverId);
        dl.setServerName(s.getName());
        dl.setAction("DEPLOY");
        dl.setStatus("RUNNING");
        dl.setVersionNote(note);
        dl.setOperator(operator);
        logMapper.insert(dl);

        StringBuilder buf = new StringBuilder();
        liveLogs.put(dl.getId(), buf);
        final Path tmpZip = zip;

        Thread t = new Thread(() -> {
            try {
                buf.append("== 部署开始: ").append(s.getName()).append(" (").append(s.getHost()).append(") ==\n");
                buf.append("更新包: ").append(displayFileName)
                   .append(" (").append(String.format("%.2f", fileSize / 1024.0 / 1024.0)).append(" MB)")
                   .append(" md5=").append(zipMd5).append("\n");

                // ---- 闸门1：包结构校验（新格式包必须带 SQL 套件，杜绝裸包上线） ----
                String structErr = validatePackageStructure(tmpZip);
                if (structErr != null) {
                    throw new IllegalStateException("部署包结构校验失败: " + structErr);
                }
                buf.append("-- 包结构校验通过（jar + frontend + agent + SQL套件齐全）\n");

                // ---- 闸门前置：新格式包含 SQL 套件，服务器必须配置数据库，否则 agent 侧无法执行 ----
                if (s.getDbName() == null || s.getDbName().isBlank()
                        || s.getDbUser() == null || s.getDbUser().isBlank()
                        || s.getDbPassword() == null || s.getDbPassword().isBlank()) {
                    throw new IllegalStateException("服务器未配置数据库连接信息（库名/用户/密码），新格式部署包必须执行SQL套件（菜单同步+升级脚本），已中止。请在部署控制台补全该服务器的数据库配置后再部署");
                }

                String agentCmd = buildAgentCommand(s, "Update", null, zipMd5);
                int rc = runFullDeploy(s, tmpZip, agentCmd, buf);
                buf.append("\n== agent 退出码: ").append(rc).append(" ==\n");
                if (rc == 0) {
                    dl.setStatus("SUCCESS");
                    buf.append("== 部署成功，服务健康检查通过 ==\n");
                } else if (rc == 2) {
                    dl.setStatus("ROLLED_BACK");
                    buf.append("!! 健康检查失败，已自动回滚到上一版本 !!\n");
                } else if (rc == 5) {
                    dl.setStatus("FAILED");
                    buf.append("!! SQL套件执行失败（退出码 5），已在改动任何文件前中止，服务器保持原状 !!\n");
                } else if (rc == 11 || rc == 12) {
                    dl.setStatus("FAILED");
                    buf.append("!! 部署前SchemaGuard闸门运行错误（退出码 ").append(rc)
                       .append("），服务器未被改动。退出码12=目标库连接失败/实体扫描失败/DDL执行失败。详见上方校验报告 !!\n");
                } else {
                    dl.setStatus("FAILED");
                    buf.append("!! 部署失败（退出码 ").append(rc).append("），请检查日志 !!\n");
                }
            } catch (Exception e) {
                dl.setStatus("FAILED");
                buf.append("!! 异常: ").append(e.getMessage()).append("\n");
                log.error("deploy failed server={}", s.getName(), e);
            } finally {
                dl.setLogText(buf.toString());
                dl.setFinishedAt(LocalDateTime.now());
                logMapper.updateById(dl);
                liveLogs.remove(dl.getId());
                runningServers.remove(serverId);
                if (!keepFile) try { Files.deleteIfExists(tmpZip); } catch (Exception ignored) {}
            }
        }, "deploy-" + serverId);
        t.setDaemon(true);
        t.start();
        return dl.getId();
    }

    /** 推送zip+agent → 执行agent → 返回agent退出码 */
    private int runFullDeploy(DeployServer s, Path zip, String agentCmd, StringBuilder buf) throws Exception {
        Session session = openSession(s);
        try {
            String work = normalizeRemote(s.getRemoteWorkDir());
            // 1. 确保工作目录存在（[void] 包裹避免输出；远程默认 shell 是 cmd.exe，不认 PowerShell 的 | Out-Null）
            exec(session, "powershell -NoProfile -Command \"[void](New-Item -ItemType Directory -Force -Path '" + work + "')\"", buf, 60);

            // 1.5 闸门2：SchemaGuard 前置校验（JSch 本地端口转发 → 目标库；
            //      缺列自动补列、缺表自动建表，覆盖业务库 erp_db + 平台库 erp_platform，服务器文件零改动）
            buf.append("-- SchemaGuard 前置闸门（实体类 vs 目标库，缺列补列、缺表建表，覆盖业务库+平台库）...\n");
            int fwdPort = session.setPortForwardingL(0, "localhost", 5432);
            try {
                // 业务库 erp_db：校验业务实体（缺列补列、缺表建表）
                int grc = runSchemaGuard(s, fwdPort, s.getDbName(), false, buf);
                if (grc != 0) {
                    return 10 + grc; // 2=运行错误，未改动服务器
                }
                // 平台库 erp_platform：校验租户实体（sys_tenant/sys_user_tenant）
                int grc2 = runSchemaGuard(s, fwdPort, "erp_platform", true, buf);
                if (grc2 != 0) {
                    return 10 + grc2;
                }
            } finally {
                try { session.delPortForwardingL(fwdPort); } catch (Exception ignored) {}
            }

            // 2. SFTP 推送 zip + agent
            buf.append("-- 上传更新包到服务器 ...\n");
            sftpPut(session, zip, work + "/incoming.zip");
            buf.append("-- 上传更新包完成\n-- 推送部署agent ...\n");
            Path agent = extractAgent();
            sftpPut(session, agent, work + "/update-agent.ps1");
            buf.append("-- agent 就绪，开始远程执行（SQL套件→备份→替换→重启→健康检查→失败自动回滚）...\n");
            // 3. 执行 agent（最长30分钟：含SQL套件/停服/拷前端/启动/健康检查）
            return exec(session, agentCmd, buf, 1800);
        } finally {
            session.disconnect();
        }
    }

    /**
     * 闸门1：部署包结构校验。
     * 新格式包必须包含：smart-erp.jar、frontend/、update-agent.ps1、sql/always/*.sql（菜单同步）。
     * 返回 null = 通过；否则返回失败原因（旧格式裸包直接拒绝，杜绝菜单不同步的部署）。
     */
    private String validatePackageStructure(Path zip) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
            boolean hasJar = false, hasAgent = false, hasFrontend = false, hasSqlAlways = false;
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.equals("smart-erp.jar")) hasJar = true;
                else if (n.equals("update-agent.ps1")) hasAgent = true;
                else if (n.startsWith("frontend/")) hasFrontend = true;
                else if (n.startsWith("sql/always/") && n.endsWith(".sql")) hasSqlAlways = true;
            }
            if (!hasJar) return "缺少 smart-erp.jar";
            if (!hasFrontend) return "缺少 frontend/ 目录";
            if (!hasAgent) return "缺少 update-agent.ps1";
            if (!hasSqlAlways) return "缺少 sql/always/ 菜单同步脚本（旧格式包）。请用新版一键打包重新构建后再部署";
            return null;
        } catch (Exception e) {
            return "读取部署包失败: " + e.getMessage();
        }
    }

    /**
     * 闸门2：通过已建立的 JSch 端口转发运行 SchemaGuard（deploy\Invoke-SchemaGuard-Local.ps1）。
     * 退出码 0=通过（缺列已补、缺表已建）；2=运行错误（拦截）；脚本不存在时放行（agent 侧 SQL 套件仍兜底）。
     * @param dbName 目标库名（业务库 erp_db 或 平台库 erp_platform）
     * @param platform true=只校验租户实体（sys_tenant/sys_user_tenant）
     */
    private int runSchemaGuard(DeployServer s, int localPort, String dbName, boolean platform, StringBuilder buf) throws Exception {
        String envDir = System.getenv("ERP_DEPLOY_DIR");
        Path script = (envDir != null && !envDir.isBlank())
                ? Paths.get(envDir, "Invoke-SchemaGuard-Local.ps1")
                : Paths.get("D:\\erp", "deploy", "Invoke-SchemaGuard-Local.ps1");
        if (!Files.exists(script)) {
            buf.append("-- SchemaGuard 脚本不存在(").append(script).append(")，跳过前置校验（agent 侧SQL套件仍会执行）\n");
            return 0;
        }
        String ps = findPowershellExe();
        // 用 cmd.exe /c 包裹 powershell，避免后端跑在 RDP/Session2 受限令牌下
        // 直接 CreateProcess("powershell") 因缺 SE_INCREASE_QUOTA_NAME 特权抛 error=5
        StringBuilder cmd = new StringBuilder();
        cmd.append('"').append(ps).append("\" -NoProfile -ExecutionPolicy Bypass -File \"").append(script).append("\"");
        cmd.append(" -LocalPort ").append(localPort);
        cmd.append(" -DbName \"").append(dbName).append("\"");
        cmd.append(" -DbUser \"").append(s.getDbUser()).append("\"");
        if (platform) {
            cmd.append(" -Platform");
        }
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", cmd.toString());
        // 密码走环境变量：避免 @ 等特殊字符经 cmd /c 引号转义后错乱（报"文件名、目录名或卷标语法不正确"）
        pb.environment().put("ERP_SG_PASSWORD", s.getDbPassword());
        pb.redirectErrorStream(true);
        Process proc = startProcessRobust(pb);
        StringBuilder out = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            } catch (Exception ignored) {}
        });
        reader.setDaemon(true);
        reader.start();
        // 首次运行可能触发 mvn test-compile，给足 15 分钟
        boolean finished = proc.waitFor(900, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            buf.append("!! SchemaGuard 超时(900s)，视为运行错误，部署中止 !!\n");
            return 2;
        }
        reader.join(5000);
        if (out.length() > 0) buf.append(out);
        return proc.exitValue();
    }

    // ============ 回滚 ============
    public Long startRollback(Long serverId, String backupName, String operator) {
        DeployServer s = serverMapper.selectById(serverId);
        if (s == null) throw new IllegalArgumentException("服务器不存在: " + serverId);
        if (backupName == null || backupName.trim().isEmpty()) throw new IllegalArgumentException("请指定要回滚的备份名");
        if (!runningServers.add(serverId)) throw new IllegalStateException("该服务器有任务正在执行，请稍候");

        DeployLog dl = new DeployLog();
        dl.setServerId(serverId);
        dl.setServerName(s.getName());
        dl.setAction("ROLLBACK");
        dl.setStatus("RUNNING");
        dl.setVersionNote("回滚到备份: " + backupName);
        dl.setOperator(operator);
        logMapper.insert(dl);

        StringBuilder buf = new StringBuilder();
        liveLogs.put(dl.getId(), buf);

        Thread t = new Thread(() -> {
            try {
                buf.append("== 回滚开始: ").append(s.getName()).append(" → ").append(backupName).append(" ==\n");
                Session session = openSession(s);
                int rc;
                try {
                    String work = normalizeRemote(s.getRemoteWorkDir());
                    exec(session, "powershell -NoProfile -Command \"[void](New-Item -ItemType Directory -Force -Path '" + work + "')\"", buf, 60);
                    Path agent = extractAgent();
                    sftpPut(session, agent, work + "/update-agent.ps1");
                    buf.append("-- agent 就绪，执行回滚 ...\n");
                    rc = exec(session, buildAgentCommand(s, "Rollback", backupName.trim(), null), buf, 900);
                } finally {
                    session.disconnect();
                }
                buf.append("\n== agent 退出码: ").append(rc).append(" ==\n");
                dl.setStatus(rc == 0 ? "SUCCESS" : "FAILED");
            } catch (Exception e) {
                dl.setStatus("FAILED");
                buf.append("!! 异常: ").append(e.getMessage()).append("\n");
                log.error("rollback failed server={}", s.getName(), e);
            } finally {
                dl.setLogText(buf.toString());
                dl.setFinishedAt(LocalDateTime.now());
                logMapper.updateById(dl);
                liveLogs.remove(dl.getId());
                runningServers.remove(serverId);
            }
        }, "rollback-" + serverId);
        t.setDaemon(true);
        t.start();
        return dl.getId();
    }

    // ============ 备份列表 / 日志查询 ============
    public List<String> listBackups(Long serverId) throws Exception {
        DeployServer s = serverMapper.selectById(serverId);
        if (s == null) throw new IllegalArgumentException("服务器不存在");
        Session session = openSession(s);
        try {
            String work = normalizeRemote(s.getRemoteWorkDir());
            String out = execCapture(session,
                    "powershell -NoProfile -ExecutionPolicy Bypass -File " + psQuote(work + "/update-agent.ps1") + " -Mode ListBackups", 60);
            List<String> list = new ArrayList<>();
            for (String line : out.split("\r?\n")) {
                String t = line.trim();
                if (t.matches("\\d{8}_\\d{6}")) list.add(t);
            }
            list.sort(Comparator.reverseOrder());
            return list;
        } finally {
            session.disconnect();
        }
    }

    /** 部署前探测服务器连通性（保存配置时用） */
    public String testConnection(Long serverId) throws Exception {
        DeployServer s = serverMapper.selectById(serverId);
        if (s == null) throw new IllegalArgumentException("服务器不存在");
        Session session = openSession(s);
        try {
            String out = execCapture(session, "echo ok", 15);
            return "连接成功: " + out.trim() + " @ " + s.getHost();
        } finally {
            session.disconnect();
        }
    }

    public DeployLog getLog(Long logId) {
        DeployLog dl = logMapper.selectById(logId);
        if (dl == null) return null;
        StringBuilder buf = liveLogs.get(logId);
        if (buf != null) dl.setLogText(buf.toString()); // 运行中：返回实时日志
        return dl;
    }

    public List<DeployLog> recentLogs(int limit) {
        return logMapper.selectList(new LambdaQueryWrapper<DeployLog>()
                .orderByDesc(DeployLog::getId).last("LIMIT " + Math.min(Math.max(limit, 1), 50)));
    }

    public boolean isServerRunning(Long serverId) {
        return runningServers.contains(serverId);
    }

    // ============ SSH 基础设施 ============
    private Session openSession(DeployServer s) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(s.getSshUser(), s.getHost(), s.getSshPort() == null ? 22 : s.getSshPort());
        if (s.getSshPassword() != null && !s.getSshPassword().isBlank()) {
            session.setPassword(s.getSshPassword());
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive,publickey");
        } else {
            // 密码为空 → 密钥认证（与 Publish.ps1 免密体系一致）
            // JSch 不会自动发现 ~/.ssh/id_ed25519，必须显式 addIdentity
            String home = System.getProperty("user.home");
            boolean keyLoaded = false;
            for (String k : new String[]{"id_ed25519", "id_rsa"}) {
                java.nio.file.Path p = java.nio.file.Paths.get(home, ".ssh", k);
                if (java.nio.file.Files.exists(p)) {
                    jsch.addIdentity(p.toString());
                    keyLoaded = true;
                    break;
                }
            }
            if (!keyLoaded) {
                throw new IllegalStateException("SSH密码为空且本地未找到 ~/.ssh/id_ed25519 或 id_rsa 密钥，请在服务器配置中填写SSH密码或安装密钥");
            }
            session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive");
        }
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(15000);
        return session;
    }

    /** 执行命令，输出流式追加到 buf，返回退出码 */
    private int exec(Session session, String cmd, StringBuilder buf, int timeoutSec) throws Exception {
        ChannelExec ch = (ChannelExec) session.openChannel("exec");
        ch.setCommand(cmd);
        ch.setInputStream(null);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ch.setErrStream(err);
        InputStream in = ch.getInputStream();
        ch.connect(10000);
        byte[] b = new byte[4096];
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        StringBuilder outBuf = new StringBuilder();
        // Windows OpenSSH 已知问题：远程命令启动的常驻子进程（如后端 java）若继承 stdio 句柄，
        // sshd 在进程退出后仍不关闭 channel，isClosed() 永不置位导致挂死到超时。
        // 防御：exit-status 已到达且输出静默满 5 秒即主动断开收工。
        long quietMs = 0;
        while (true) {
            boolean hadOutput = false;
            while (in.available() > 0) {
                int n = in.read(b, 0, b.length);
                if (n < 0) break;
                outBuf.append(new String(b, 0, n, StandardCharsets.UTF_8));
                hadOutput = true;
            }
            if (ch.isClosed()) {
                while (in.available() > 0) {
                    int n = in.read(b, 0, b.length);
                    if (n < 0) break;
                    outBuf.append(new String(b, 0, n, StandardCharsets.UTF_8));
                }
                break;
            }
            if (ch.getExitStatus() != -1) {
                quietMs = hadOutput ? 0 : quietMs + 200;
                if (quietMs >= 5000) {
                    int es = ch.getExitStatus();
                    buf.append("== 远程进程已退出(退出码 ").append(es)
                       .append(")，检测到残留句柄未关闭，主动断开连接 ==\n");
                    ch.disconnect();
                    String out2 = outBuf.toString();
                    if (!out2.isEmpty()) buf.append(out2.endsWith("\n") ? out2 : out2 + "\n");
                    String errs2 = err.toString(StandardCharsets.UTF_8);
                    if (!errs2.isBlank()) buf.append("[stderr] ").append(errs2.endsWith("\n") ? errs2 : errs2 + "\n");
                    return es;
                }
            } else {
                quietMs = 0;
            }
            if (System.currentTimeMillis() > deadline) {
                ch.disconnect();
                buf.append("!! 命令超时(").append(timeoutSec).append("s): ").append(cmd).append("\n");
                return -1;
            }
            Thread.sleep(200);
        }
        int rc = ch.getExitStatus();
        String out = outBuf.toString();
        if (!out.isEmpty()) buf.append(out.endsWith("\n") ? out : out + "\n");
        String errs = err.toString(StandardCharsets.UTF_8);
        if (!errs.isBlank()) buf.append("[stderr] ").append(errs.endsWith("\n") ? errs : errs + "\n");
        ch.disconnect();
        return rc;
    }

    private String execCapture(Session session, String cmd, int timeoutSec) throws Exception {
        StringBuilder buf = new StringBuilder();
        exec(session, cmd, buf, timeoutSec);
        return buf.toString();
    }

    private void sftpPut(Session session, Path local, String remotePath) throws Exception {
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect(10000);
        try {
            sftp.put(local.toString(), sftpPath(remotePath), ChannelSftp.OVERWRITE);
        } finally {
            sftp.disconnect();
        }
    }

    /**
     * Windows OpenSSH sftp-server 路径适配：
     * JSch 只把以 / 开头的路径当绝对路径，C:/xxx 会被拼接到远程 home 下导致 "No such file"；
     * /C:/xxx 是 Windows OpenSSH 认可的绝对路径写法（盘符前加 /）。
     */
    private static String sftpPath(String p) {
        if (p != null && p.matches("^[A-Za-z]:/.*")) return "/" + p;
        return p;
    }

    private Path extractAgent() throws Exception {
        Path agent = Files.createTempFile("update-agent-", ".ps1");
        try (InputStream in = new ClassPathResource("deploy/update-agent.ps1").getInputStream()) {
            Files.copy(in, agent, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return agent;
    }

    private String normalizeRemote(String dir) {
        if (dir == null || dir.isBlank()) return "C:/smart-erp-update";
        return dir.replace('\\', '/').replaceAll("/+$", "");
    }

    /**
     * PowerShell 双引号字符串内容转义（安全审计 V-003）：
     * 反引号、双引号、$ 前加反引号，防止配置值/回滚参数破坏命令结构注入任意代码。
     * 转义只作用于传输层，agent 脚本参数收到的仍是原始值。
     */
    private static String psQuote(String v) {
        return "\"" + v.replace("`", "``").replace("\"", "`\"").replace("$", "`$") + "\"";
    }

    /** PowerShell 单引号字符串内容转义：单引号翻倍（用于路径类参数） */
    private static String psSingleQuote(String v) {
        return "'" + v.replace("'", "''") + "'";
    }

    /** 拼 agent 命令（参数值经 PowerShell 转义，防命令注入） */
    private String buildAgentCommand(DeployServer s, String mode, String backupName, String zipMd5) {
        String work = normalizeRemote(s.getRemoteWorkDir());
        StringBuilder sb = new StringBuilder();
        sb.append("powershell -NoProfile -ExecutionPolicy Bypass -File ")
          .append(psQuote(work + "/update-agent.ps1"));
        sb.append(" -Mode ").append(mode);
        if (s.getJarTarget() != null && !s.getJarTarget().isBlank())
            sb.append(" -JarTarget ").append(psQuote(s.getJarTarget().replace('\\', '/')));
        if (s.getFrontendTargets() != null && !s.getFrontendTargets().isBlank())
            sb.append(" -FrontendTargets ").append(psQuote(s.getFrontendTargets().replace('\\', '/')));
        if (s.getStopCommand() != null && !s.getStopCommand().isBlank())
            sb.append(" -StopCommand ").append(psQuote(s.getStopCommand()));
        if (s.getStartCommand() != null && !s.getStartCommand().isBlank())
            sb.append(" -StartCommand ").append(psQuote(s.getStartCommand()));
        // 三项齐全才启用部署期幂等补列（schema sync）+ 租户模板库重建；缺省走 agent 跳过分支，行为与旧版一致
        if (s.getDbName() != null && !s.getDbName().isBlank()
                && s.getDbUser() != null && !s.getDbUser().isBlank()
                && s.getDbPassword() != null && !s.getDbPassword().isBlank()) {
            sb.append(" -DbName ").append(psQuote(s.getDbName()));
            sb.append(" -DbUser ").append(psQuote(s.getDbUser()));
            sb.append(" -DbPassword ").append(psQuote(s.getDbPassword()));
            // 多租户模板库：部署时从业务库克隆最新表结构+种子数据（被租户引用时 agent 自动跳过，安全保护）
            sb.append(" -TemplateDb ").append(psQuote("erp_tenant_template"));
        }
        if (backupName != null) sb.append(" -BackupName ").append(psQuote(backupName));
        // 传输完整性校验：agent 比对 incoming.zip 的 MD5，不匹配零触碰退出（exit 4）
        if (zipMd5 != null && !zipMd5.isBlank()) sb.append(" -ZipMd5 ").append(psQuote(zipMd5));
        return sb.toString();
    }

    /** 计算文件 MD5（小写十六进制） */
    private static String md5(Path file) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) md.update(b, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /** 定位 powershell.exe 绝对路径：优先 System32 标准路径，兜底 %SystemRoot%，最后退回裸名 */
    private static String findPowershellExe() {
        String[] candidates = new String[]{
                "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe",
                System.getenv("SystemRoot") + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"
        };
        for (String c : candidates) {
            if (c != null && java.nio.file.Files.exists(java.nio.file.Paths.get(c))) return c;
        }
        return "powershell.exe";
    }

    /**
     * 启动进程并在 CreateProcess error=5 时降级为直接 powershell 绝对路径调用。
     * 后端跑在 RDP/Session2 受限令牌时，直接 CreateProcess 控制台程序会因缺特权被拒；
     * 经 cmd.exe /c 包裹通常可绕过，此处再兜底一次直接调用，仍失败则给出可操作提示。
     */
    private Process startProcessRobust(ProcessBuilder pb) throws java.io.IOException {
        try {
            return pb.start();
        } catch (java.io.IOException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("CreateProcess") || msg.contains("error=5")
                    || msg.contains("拒绝访问") || msg.contains("Access is denied")) {
                log.warn("cmd 包裹启动失败({}, 降级为直接 powershell 调用)", msg);
                java.util.List<String> orig = pb.command();
                // 从 cmd /c "..." 中抽出 powershell 命令段，重新按参数构造直接调用
                ProcessBuilder fb = new ProcessBuilder(findPowershellExe(), "-NoProfile", "-ExecutionPolicy", "Bypass");
                if (orig.size() >= 3) {
                    String rest = orig.get(2);
                    int fileIdx = rest.indexOf("-File");
                    if (fileIdx >= 0) {
                        String tail = rest.substring(fileIdx);
                        java.util.List<String> parts = new java.util.ArrayList<>();
                        StringBuilder cur = new StringBuilder();
                        boolean inQ = false;
                        for (int i = 0; i < tail.length(); i++) {
                            char ch = tail.charAt(i);
                            if (ch == '"') { inQ = !inQ; continue; }
                            if (ch == ' ' && !inQ) {
                                if (cur.length() > 0) { parts.add(cur.toString()); cur.setLength(0); }
                            } else { cur.append(ch); }
                        }
                        if (cur.length() > 0) parts.add(cur.toString());
                        fb.command().addAll(parts);
                    }
                }
                fb.redirectErrorStream(true);
                Map<String, String> env = pb.environment();
                if (env != null) fb.environment().putAll(env);
                try {
                    return fb.start();
                } catch (java.io.IOException e2) {
                    log.error("降级直接 powershell 仍失败", e2);
                    throw new java.io.IOException(
                            "无法启动 powershell（CreateProcess 被拒绝）。" +
                            "后端进程可能运行在 RDP/二次登录的受限会话中，令牌缺少创建子进程所需特权。" +
                            "请在交互式桌面会话中重新启动后端后再试。原始错误：" + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() {
        // daemon线程随JVM退出，无需处理
    }
}
