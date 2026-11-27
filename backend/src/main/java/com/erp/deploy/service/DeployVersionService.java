package com.erp.deploy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.deploy.entity.DeployLog;
import com.erp.deploy.entity.DeployServer;
import com.erp.deploy.entity.DeployVersion;
import com.erp.deploy.mapper.DeployLogMapper;
import com.erp.deploy.mapper.DeployServerMapper;
import com.erp.deploy.mapper.DeployVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 部署版本服务（本地控制台专用，仅 SUPER_ADMIN）：
 *   1. 一键打包：调 Build-Package.ps1 构建 full/customer 包，实时日志，产物登记版本库
 *   2. 版本部署：从版本库选版本推到目标服务器（复用 DeployService 的 SSH/agent 链路）
 *   3. 版本回写：部署成功后更新 deploy_server.current_version
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeployVersionService {

    private final DeployVersionMapper versionMapper;
    private final DeployServerMapper serverMapper;
    private final DeployLogMapper logMapper;
    private final DeployService deployService;

    /** 打包任务实时日志 taskId -> builder（复用部署日志表存档） */
    private final Map<Long, StringBuilder> liveLogs = new ConcurrentHashMap<>();

    private static final Pattern VERSION_NO_PAT = Pattern.compile("^[\\w.\\-]{1,50}$");
    private static final Pattern BUILD_MODE_PAT = Pattern.compile("^(full|customer)$");

    // ============ 一键打包 ============

    /**
     * 启动打包（异步，返回 logId 供前端轮询实时日志）。
     * 打包成功后版本自动登记入 deploy_version 表。
     */
    public Long startBuild(String buildMode, String note, String operator) {
        if (buildMode == null || !BUILD_MODE_PAT.matcher(buildMode).matches()) {
            throw new IllegalArgumentException("buildMode 必须为 full 或 customer");
        }
        // 串行锁：同时只允许一个打包任务（构建目录共用）
        synchronized (liveLogs) {
            if (!liveLogs.isEmpty()) {
                throw new IllegalStateException("已有打包任务正在执行，请稍候");
            }
        }

        DeployLog dl = new DeployLog();
        dl.setAction("BUILD");
        dl.setStatus("RUNNING");
        dl.setVersionNote("打包 " + ("customer".equals(buildMode) ? "客户包" : "全量包") + (note == null || note.isBlank() ? "" : "：" + note));
        dl.setOperator(operator);
        logMapper.insert(dl);
        StringBuilder buf = new StringBuilder();
        liveLogs.put(dl.getId(), buf);

        Thread t = new Thread(() -> {
            try {
                buf.append("== 一键打包开始（").append("customer".equals(buildMode) ? "客户包" : "全量包").append("）==\n");
                // Build-Package.ps1 路径：本地 D:\erp\deploy\Build-Package.ps1（与项目同级部署脚本目录）
                Path script = resolveBuildScript();
                buf.append("脚本: ").append(script).append("\n");
                if (!Files.exists(script)) throw new IllegalStateException("打包脚本不存在: " + script);

                ProcessBuilder pb = buildPowershell(script.toString(), buildMode);
                pb.redirectErrorStream(true);
                pb.environment().put("MAVEN_OPTS", "-Dfile.encoding=UTF-8");
                Process p = startProcessRobust(pb, "powershell");

                String versionNo = null;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        buf.append(line).append('\n');
                        if (line.startsWith("VERSION_NO=")) versionNo = line.substring("VERSION_NO=".length()).trim();
                    }
                }
                int rc = p.waitFor();
                buf.append("\n== 打包退出码: ").append(rc).append(" ==\n");
                if (rc != 0) {
                    dl.setStatus("FAILED");
                } else if (versionNo == null || versionNo.isEmpty()) {
                    dl.setStatus("FAILED");
                    buf.append("!! 未解析到版本号（脚本输出异常）\n");
                } else {
                    // 读 meta 登记版本库
                    Path zip = Paths.get("D:\\erp\\deploy\\deploy-packages", versionNo + ".zip");
                    Path meta = Paths.get(zip + ".meta");
                    if (!Files.exists(zip)) {
                        dl.setStatus("FAILED");
                        buf.append("!! 产物 zip 不存在: ").append(zip).append("\n");
                    } else {
                        DeployVersion v = new DeployVersion();
                        v.setVersionNo(versionNo);
                        v.setBuildMode(buildMode);
                        v.setNote(note);
                        v.setZipPath(zip.toString());
                        v.setZipSize(Files.size(zip));
                        v.setZipMd5(md5(zip));
                        v.setOperator(operator);
                        versionMapper.insert(v);
                        dl.setVersionNo(versionNo);
                        dl.setStatus("SUCCESS");
                        buf.append("== 版本 ").append(versionNo).append(" 已登记版本库（")
                          .append(String.format("%.2f", v.getZipSize() / 1024.0 / 1024.0)).append("MB）==\n");
                    }
                }
            } catch (Exception e) {
                dl.setStatus("FAILED");
                buf.append("!! 异常: ").append(e.getMessage()).append("\n");
                log.error("build failed", e);
            } finally {
                dl.setLogText(buf.toString());
                dl.setFinishedAt(LocalDateTime.now());
                logMapper.updateById(dl);
                liveLogs.remove(dl.getId());
            }
        }, "build-package");
        t.setDaemon(true);
        t.start();
        return dl.getId();
    }

    /** 找 Build-Package.ps1：优先 D:\erp\deploy\，兜底后端工作目录的 deploy 子目录 */
    private Path resolveBuildScript() {
        Path p = Paths.get("D:\\erp\\deploy\\Build-Package.ps1");
        if (Files.exists(p)) return p;
        return Paths.get("deploy", "Build-Package.ps1").toAbsolutePath();
    }

    /**
     * 构造 powershell 调用的 ProcessBuilder。
     *
     * 为什么用 cmd.exe /c 包裹而不是直接 ProcessBuilder("powershell", ...)：
     *   当后端 Java 进程跑在 RDP/二次登录产生的 Session 2（或被 UAC 远程限制过滤过令牌）时，
     *   令牌里的 SE_INCREASE_QUOTA_NAME / SE_ASSIGNPRIMARYTOKEN_NAME 特权被剥离。
     *   直接 CreateProcess("powershell") 启动控制台子系统程序，Windows 需为子进程分配窗口站/桌面，
     *   缺少上述特权会直接抛 CreateProcess error=5 (ERROR_ACCESS_DENIED)。
     *   改走 cmd.exe /c：cmd.exe 是 Windows 命令外壳，内核对其窗口站分配走宽松路径，
     *   powershell 作为 cmd 子进程继承 cmd 已分配好的控制台，无需再申请窗口站，从而绕过特权依赖。
     */
    private ProcessBuilder buildPowershell(String scriptPath, String buildMode) {
        String ps = findPowershellExe();
        // cmd /c 整段命令；脚本路径用双引号包裹，避免空格/中文路径问题
        String cmd = "\"" + ps + "\" -NoProfile -ExecutionPolicy Bypass -File \"" + scriptPath + "\" -BuildMode " + buildMode;
        return new ProcessBuilder("cmd.exe", "/c", cmd);
    }

    /** 定位 powershell.exe 绝对路径：优先 System32 标准路径，兜底 %SystemRoot%，最后退回裸名 powershell */
    private String findPowershellExe() {
        String[] candidates = new String[]{
                "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe",
                System.getenv("SystemRoot") + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"
        };
        for (String c : candidates) {
            if (c != null && Files.exists(Paths.get(c))) return c;
        }
        return "powershell.exe";
    }

    /**
     * 启动进程并在 CreateProcess error=5 时自动降级重试。
     *   - 第一次按 pb（cmd.exe /c 包裹）启动
     *   - 若抛 IOException 且消息含 "CreateProcess" / "error=5" / "拒绝访问"，
     *     降级为直接用绝对路径 powershell.exe 启动（不经过 cmd）
     *   - 仍失败则抛原异常，日志里附带可操作提示（在交互式会话重启后端）
     */
    private Process startProcessRobust(ProcessBuilder pb, String label) throws java.io.IOException {
        try {
            return pb.start();
        } catch (java.io.IOException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            boolean accessDenied = msg.contains("CreateProcess") || msg.contains("error=5")
                    || msg.contains("拒绝访问") || msg.contains("Access is denied");
            if (accessDenied && "powershell".equals(label)) {
                log.warn("[{}] cmd 包裹启动失败({}, 降级为直接 powershell 调用)", label, msg);
                String ps = findPowershellExe();
                // 降级：直接绝对路径调用 powershell（命令行参数与原 buildPowershell 一致，去掉 cmd /c 外壳）
                java.util.List<String> orig = pb.command();
                // orig = [cmd.exe, /c, "..."]；取第3段即完整 powershell 命令字符串，重新拆成参数列表
                ProcessBuilder fb = new ProcessBuilder(ps, "-NoProfile", "-ExecutionPolicy", "Bypass");
                if (orig.size() >= 3) {
                    // 解析 "-File \"xxx\" -BuildMode yyy" 部分
                    String tail = orig.get(2);
                    int fileIdx = tail.indexOf("-File");
                    if (fileIdx >= 0) {
                        String rest = tail.substring(fileIdx);
                        // 简易按引号拆分：-File "path" -BuildMode mode
                        java.util.List<String> parts = new java.util.ArrayList<>();
                        StringBuilder cur = new StringBuilder();
                        boolean inQ = false;
                        for (int i = 0; i < rest.length(); i++) {
                            char ch = rest.charAt(i);
                            if (ch == '"') { inQ = !inQ; continue; }
                            if (ch == ' ' && !inQ) {
                                if (cur.length() > 0) { parts.add(cur.toString()); cur.setLength(0); }
                            } else {
                                cur.append(ch);
                            }
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
                    log.error("[{}] 降级直接 powershell 仍失败", label, e2);
                    throw new java.io.IOException(
                            "无法启动 powershell（CreateProcess 被拒绝）。" +
                            "可能原因：后端进程运行在 RDP/二次登录的受限会话中，令牌缺少创建子进程所需特权。" +
                            "建议：在交互式桌面会话中重新启动后端 java 进程后再试。原始错误：" + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    /** BUILD 运行中的实时日志文本（供 Controller 叠加） */
    public String getLiveLogText(Long logId) {
        StringBuilder buf = liveLogs.get(logId);
        return buf == null ? null : buf.toString();
    }

    /** 是否有打包任务在执行 */
    public boolean isBuilding() {
        return !liveLogs.isEmpty();
    }

    // ============ 版本库 CRUD ============

    public List<DeployVersion> listVersions() {
        return versionMapper.selectList(new LambdaQueryWrapper<DeployVersion>()
                .orderByDesc(DeployVersion::getId).last("LIMIT 200"));
    }

    public DeployVersion getVersion(Long id) {
        return versionMapper.selectById(id);
    }

    public void deleteVersion(Long id) {
        DeployVersion v = versionMapper.selectById(id);
        if (v == null) return;
        // 只删文件+记录；已部署服务器和历史日志不受影响（version_no 已冗余）
        try { Files.deleteIfExists(Paths.get(v.getZipPath())); } catch (Exception ignored) {}
        try { Files.deleteIfExists(Paths.get(v.getZipPath() + ".meta")); } catch (Exception ignored) {}
        versionMapper.deleteById(id);
    }

    // ============ 版本部署 ============

    /**
     * 从版本库选版本部署到服务器（异步）。
     * 校验包模式匹配（customer 包只能发 customer 服务器，full 发 full；不匹配抛错）。
     */
    public Long deployVersion(Long versionId, Long serverId, String operator) {
        DeployVersion v = versionMapper.selectById(versionId);
        if (v == null) throw new IllegalArgumentException("版本不存在: " + versionId);
        if (!VERSION_NO_PAT.matcher(v.getVersionNo()).matches()) throw new IllegalArgumentException("版本号非法");
        Path zip = Paths.get(v.getZipPath());
        if (!Files.exists(zip)) throw new IllegalArgumentException("版本包文件缺失: " + v.getZipPath());
        // 本地包完整性校验：实际 MD5 与登记入库的 zip_md5 比对，不一致说明本地包文件已损坏，拒绝部署
        if (v.getZipMd5() != null && !v.getZipMd5().isBlank()) {
            String actual;
            try {
                actual = md5(zip);
            } catch (Exception e) {
                throw new IllegalArgumentException("读取版本包失败: " + e.getMessage());
            }
            if (!v.getZipMd5().equalsIgnoreCase(actual)) {
                throw new IllegalArgumentException("版本包MD5校验失败（本地包文件已损坏或被改动）：登记="
                        + v.getZipMd5() + " 实际=" + actual + "，请重新打包");
            }
        }

        DeployServer s = serverMapper.selectById(serverId);
        if (s == null) throw new IllegalArgumentException("服务器不存在: " + serverId);
        // 包模式匹配校验：customer 服务器只吃 customer 包；full 服务器发 customer 包需前端二次确认（这里硬校验同模式）
        String serverMode = s.getBuildMode() == null ? "full" : s.getBuildMode();
        if (!serverMode.equals(v.getBuildMode())) {
            throw new IllegalArgumentException("包模式不匹配：服务器「" + s.getName() + "」为 " + serverMode
                    + " 包服务器，版本 " + v.getVersionNo() + " 是 " + v.getBuildMode() + " 包，禁止错发");
        }

        // 复用 DeployService 的 Path 重载：备份→替换→重启→健康检查→失败自动回滚
        String note = "版本 " + v.getVersionNo() + (v.getNote() == null || v.getNote().isBlank() ? "" : "：" + v.getNote());
        Long logId = deployService.startDeploy(serverId, zip, note, operator);
        // 异步回写：轮询部署日志状态，SUCCESS 时更新服务器当前版本
        Thread cb = new Thread(() -> {
            String result = waitForDeployResult(logId);
            if ("SUCCESS".equals(result)) {
                s.setCurrentVersion(v.getVersionNo());
                s.setVersionDeployedAt(LocalDateTime.now());
                serverMapper.updateById(s);
                DeployLog dl = logMapper.selectById(logId);
                if (dl != null) {
                    dl.setVersionNo(v.getVersionNo());
                    logMapper.updateById(dl);
                }
                log.info("server {} updated to version {}", s.getName(), v.getVersionNo());
            } else if ("ROLLED_BACK".equals(result)) {
                DeployLog dl = logMapper.selectById(logId);
                if (dl != null) {
                    dl.setVersionNo(v.getVersionNo());
                    logMapper.updateById(dl);
                }
            }
        }, "deploy-version-cb-" + logId);
        cb.setDaemon(true);
        cb.start();
        return logId;
    }

    /** 轮询部署结果（最长35分钟），返回最终状态 */
    private String waitForDeployResult(Long logId) {
        long deadline = System.currentTimeMillis() + 35 * 60 * 1000L;
        while (System.currentTimeMillis() < deadline) {
            DeployLog dl = logMapper.selectById(logId);
            if (dl != null && !"RUNNING".equals(dl.getStatus())) return dl.getStatus();
            try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        }
        return null;
    }

    /** 带版本追踪的回滚：成功后服务器当前版本显示为「备份:{时间戳}」 */
    public Long startRollbackTracked(Long serverId, String backupName, String operator) {
        Long logId = deployService.startRollback(serverId, backupName, operator);
        DeployServer s = serverMapper.selectById(serverId);
        if (s != null) {
            Thread cb = new Thread(() -> {
                String result = waitForDeployResult(logId);
                if ("SUCCESS".equals(result)) {
                    s.setCurrentVersion("备份:" + backupName);
                    s.setVersionDeployedAt(LocalDateTime.now());
                    serverMapper.updateById(s);
                }
            }, "rollback-cb-" + logId);
            cb.setDaemon(true);
            cb.start();
        }
        return logId;
    }

    private static String md5(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) md.update(b, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
