# 远程 MCP 适配层技术设计

本文件只描述 MCP 适配边界；OAuth/OIDC、Token 生命周期与持久化模型以 [`../oauth-oidc/technical-design.md`](../oauth-oidc/technical-design.md) 为准。

## 设计结论

`mcp` 保持为无启动类的 Maven 模块，由 `application` 在唯一的 `devops.AiDevopsAgentPlatformApplication` 中装配。它提供 Streamable HTTP `POST /mcp`，不再产出或运行 stdio JAR，也不使用 Windows 凭据管理器。

```text
Agent ── OAuth Bearer Token ──> /mcp
                                  │
                         MCP 认证适配器
                                  │
                    IAM 认证主体与授权门面
                                  │
                         MCP 业务工具处理器
```

## 包边界

| 包 | 职责 | 禁止事项 |
| --- | --- | --- |
| `devops.mcp.transport` | Streamable HTTP 与 MCP 生命周期 | 不处理密码或 Token 持久化 |
| `devops.mcp.authentication` | 从 Bearer Token 获得 `AuthenticatedSubject` | 不查询角色或直接访问 DAO |
| `devops.mcp.tool` | 工具输入/输出与用例转发 | 不保存用户凭据 |
| `devops.mcp.authorization` | 调用 IAM 资源+动作+范围门面 | 不以角色字符串决定权限 |
| `devops.mcp.publictool` | 公开能力发现和注册引导 | 不读取身份或 Token |

## 请求处理规则

1. 仅公开工具允许匿名访问；其名单由实际注册工具生成。
2. 受保护工具先检查 OAuth Access Token 的有效性、受众 `${issuer}/mcp` 和 `mcp.tools` Scope；未认证时返回 HTTP `401` 与 `resource_metadata` 认证挑战，由 Codex 发起浏览器 OAuth 登录。
3. 工具需要访问具体资源时，必须调用 IAM 授权门面；认证成功不代表具有租户、项目或环境权限。
4. 未认证、无效、过期和受众不匹配使用同一未认证结果，避免 Token 状态探测。
5. 工具输出、诊断日志和错误不得包含 Authorization 头、Token、授权码、密码或 PKCE 参数。

## 本地配置目标

最终本地接入为 URL 型 MCP Server：

```toml
[mcp_servers.ai_devops]
url = "http://127.0.0.1:8080/mcp"
auth = "oauth"
```

该配置仅适用于开发 Profile。生产只允许 HTTPS URL；OAuth 元数据由服务器自动发现。具体实现任务见 [`../oauth-oidc/tasks.md`](../oauth-oidc/tasks.md) 的 `MCP-T11` 至 `MCP-T14`。
