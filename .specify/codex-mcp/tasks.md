# 远程 MCP 实施任务索引

**状态**：待实施
**前置条件**：OAuth/OIDC 技术设计评审通过。

远程 MCP 的完整任务清单在 [`../oauth-oidc/tasks.md`](../oauth-oidc/tasks.md)，其中 `MCP-T11` 至 `MCP-T14` 是本模块的唯一实施任务。

- [x] **MCP-T15** 恢复本机 stdio 一次性浏览器登录回退，并确保其与远程 OAuth 配置互斥。
  - 依赖：`../secure-login/spec.md`。
  - 完成标准：本机浏览器登录、跨 stdio 进程状态恢复、Windows 凭据存储和配置互斥均通过自动化测试。

修订 0.6 中的 `T-MCP-01` 至 `T-MCP-10` 描述的是已完成的本地 stdio 基线，不再代表正式交付状态，也不应继续追加实现。
