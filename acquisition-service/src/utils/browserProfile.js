/**
 * 浏览器持久化目录（user-data-dir）相关的清理工具。
 *
 * 背景（2026-09-17 8cloud 实测）：浏览器进程残留时会占住 user-data-dir，
 * 之后再启动同一目录的浏览器会立刻退出 —— Playwright 报
 * "Target page, context or browser has been closed"，Edge 进程 exitCode=21，
 * 表现为「采集任务跑不起来 / 跑了很久没有结果」。
 * 服务器上没人能进桌面手工处理，所以这里提供自动清理。
 */
const { execSync } = require('child_process');

/** 取错误信息的第一行：Playwright 的报错很长（含命令行/banner），日志里只留首行更可读 */
function firstLineOf(msg) {
  const s = String(msg || '');
  const i = s.indexOf('\n');
  return i === -1 ? s : s.slice(0, i);
}

/**
 * 关闭占用指定浏览器持久化目录的残留浏览器进程（仅 Windows），返回被清理的进程数。
 * 任何异常（非 Windows / 找不到进程 / 命令执行失败）都返回 0，绝不向上抛错。
 */
function killBrowsersUsingProfile(userDataDir) {
  if (process.platform !== 'win32') return 0;
  const dir = String(userDataDir || '');
  if (!dir) return 0;
  const script = "$n = 0; Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'msedge.exe' -or $_.Name -eq 'chrome.exe') "
    + "-and $_.CommandLine -and $_.CommandLine.Contains('" + dir + "') } | "
    + "ForEach-Object { try { Stop-Process -Id $_.ProcessId -Force -ErrorAction Stop; $n = $n + 1 } catch { } }; "
    + "Write-Output ([int]$n)";
  try {
    const out = execSync('powershell -NoProfile -Command "' + script + '"', {
      windowsHide: true,
      timeout: 30000,
      encoding: 'utf8'
    });
    const n = parseInt(String(out).trim(), 10);
    return Number.isFinite(n) ? n : 0;
  } catch (e) {
    return 0;
  }
}

module.exports = { killBrowsersUsingProfile, firstLineOf };
