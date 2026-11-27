# Git 使用说明（写给非工程师）

## 一、仓库在哪

- 本地仓库：`D:\erp\prodsrc\智能ERP-项目`（已经存在，不是新建的）
- 远程仓库：GitHub `xioalismile/erp-system`（SSH 地址 `git@github.com:xioalismile/erp-system.git`）
- 当前分支：`main`

> 只有这个目录是仓库。`D:\erp` 下的其它产品（batch-record-system、温度互联、小程序等）不在此仓库内。

## 二、三条铁律

1. **密钥永不提交**：`.env`、`license.lic`、私钥、服务器密码、访问令牌。`.gitignore` 已覆盖，不要用 `git add -f` 强行添加。
2. **提交前先看清单**：`git status`，确认没有意外文件（安装包、日志、备份、测试截图）。
3. **一次改动一批提交**：一个功能或一个修复提交一次，写清楚改了什么，出问题才能精准回滚。

## 三、日常五条命令

在项目目录打开终端（PowerShell）：

```powershell
git status                      # 看当前改了哪些文件
git diff                        # 看具体改了什么内容
git add -A                      # 把改动加入本次提交
git commit -m "说明这次改了什么"   # 提交
git log --oneline -10           # 看最近 10 次提交
```

## 四、开分支与切回

```powershell
git switch -c fix/库存成本        # 从当前状态开一个新分支做改动
git switch main                  # 切回主干
git branch                       # 列出本地分支
```

约定：`main` 只放能跑的版本；试验性改动开分支做，验证通过再合并回 `main`。

## 五、回滚的三种场景

| 场景 | 命令 | 说明 |
|---|---|---|
| 撤回某个文件的未提交改动 | `git restore 文件路径` | 立即恢复成上次提交的样子 |
| 撤回全部未提交改动 | `git restore .` | 谨慎，改了没提交的都没了 |
| 回到某次提交的状态 | `git switch --detach 提交号` 或 `git reset --hard 提交号` | 后者会丢弃之后的改动，先确认 |
| 撤销某次已提交的改动 | `git revert 提交号` | 生成一次"反向提交"，历史保留，最安全 |

**关键边界**：只能回到"提交过"的状态。没提交的改动，git 帮不了你——所以改完就提交，别攒着。

## 六、密钥与凭据

- 远程地址：`git@github.com:xioalismile/erp-system.git`（SSH，不用令牌）。
- 本机 SSH 密钥：`C:\Users\z\.ssh\id_ed25519`，对应公钥已加到 GitHub 账号；验证命令 `ssh -T git@github.com`，返回 `Hi xioalismile!` 即正常。
- 数据库密码不再写在 `docker-compose.yml`：改为读取同目录 `.env` 里的 `POSTGRES_PASSWORD`，没设置会直接报错。
- 部署脚本里的服务器密码是运行时提示输入，不落在文件里。

## 七、上传前确认

```powershell
git remote -v                   # 确认远程地址（应为 git@github.com:...）
git push                        # 推送到 GitHub
```

上传前请确认：GitHub 那个仓库是**私有**的。

## 八、基线提交（一次性的起点）

当前工作区有大量从未提交的改动（仓库上次提交还停在 V3.0）。做一次基线提交并打标签后，从这一刻起每一步都可回滚：

```powershell
git add -A
git commit -m "baseline: 当前完整业务代码"
git tag baseline-2026-11-27
```
