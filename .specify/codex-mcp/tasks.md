# 远程 MCP 实施任务索引

**状态**：待实施
**前置条件**：OAuth/OIDC 技术设计评审通过。

远程 MCP 的完整任务清单在 [`../oauth-oidc/tasks.md`](../oauth-oidc/tasks.md)，其中 `MCP-T11` 至 `MCP-T14` 是本模块的唯一实施任务。

- [x] **MCP-T15** 提供 Codex 项目级 URL 型 OAuth MCP 配置。
  - 依赖：`MCP-T11` 至 `MCP-T14`。
  - 完成标准：项目只声明 `http://127.0.0.1:8080/mcp` 与 OAuth，不启动 stdio MCP 或使用 Windows 凭据管理器。

- [x] **MCP-T16** 提供 Codex 直接登录状态查询工具。
  - 依赖：`MCP-T15`。
  - 完成标准：`login_ai_devops` 与 `get_ai_devops_login_status` 在认证后返回安全用户摘要与失效时间，未认证时触发 OAuth 挑战。

- [x] **MCP-T17** 初始化默认本地 H2 IAM/OAuth 表结构。
  - 依赖：`MCP-T15`。
  - 完成标准：服务启动后可完成浏览器注册、OAuth 授权码兑换与 MCP 登录状态调用；外部数据库不运行自动建表。

修订 0.6 中的 `T-MCP-01` 至 `T-MCP-10` 描述的是已完成的本地 stdio 基线，不再代表正式交付状态，也不应继续追加实现。
