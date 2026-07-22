# 远程 MCP 实施计划索引

本模块的实施依赖 IAM OAuth/OIDC 能力先完成。完整、有序任务、依赖与完成标准统一维护在 [`../oauth-oidc/tasks.md`](../oauth-oidc/tasks.md)。

本模块仅承接以下任务：

1. `MCP-T11`：将现有 MCP 模块改为 application 装配的 Streamable HTTP 适配层。
2. `MCP-T12`：接入 IAM OAuth Access Token 认证与统一授权门面。
3. `MCP-T13`：迁移现有公开和用户工具。
4. `MCP-T14`：移除正式 stdio/Windows 凭据配置路径。
5. `MCP-T15`：在 Codex 缺少 OAuth 授权入口时，恢复仅本机使用的 stdio 一次性浏览器登录回退，并通过独立配置与远程模式互斥。

旧版 stdio 实现不再扩展；只在迁移期间保留为代码基线，不能作为正式联调入口。
