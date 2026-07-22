# 远程 MCP 测试计划索引

远程 MCP 与 OAuth/OIDC 的完整自动化、协议和人工验收场景见 [`../oauth-oidc/test-plan.md`](../oauth-oidc/test-plan.md)。

MCP 专项验收重点：

- `/mcp` 以 Streamable HTTP 工作，且唯一 Spring Boot 启动类仍为 `devops.AiDevopsAgentPlatformApplication`。
- 受保护工具缺少、过期或无效 Access Token 时返回 HTTP `401`，并以 `WWW-Authenticate` 的 `resource_metadata` 引导兼容 Agent 进入 OAuth 浏览器授权流程。
- 仅能力发现和注册引导可匿名访问；受保护工具必须验证 OAuth Access Token。
- 工具通过 IAM 授权门面进行资源、动作、范围检查；不直接判断角色。
- 正式接入不启动 stdio JAR，也不读写 Windows 凭据管理器。
