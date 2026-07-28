# 远程 MCP 工具契约

OAuth/OIDC HTTP 契约见 [`../oauth-oidc/api-contract.md`](../oauth-oidc/api-contract.md)。需要登录的工具均由 HTTP Bearer Access Token 认证，工具输入不接收 Token、用户名或密码。

| 工具 | 是否公开 | 目的 | 成功输出 | 安全约束 |
| --- | --- | --- | --- | --- |
| `get_ai_devops_capabilities` | 是 | 返回当前实际注册的能力目录 | 能力 ID、名称、说明、工具、是否需要登录、示例表达 | 不读取身份、Token、注册链接或用户信息。 |
| `register_ai_devops` | 是 | 创建/取得安全浏览器注册链接 | 短时注册链接和到期时间 | 不在工具参数中收集注册资料或密码；注册不创建 OAuth 登录。 |
| `login_ai_devops` | 否 | 触发或确认 OAuth 登录 | 当前登录用户摘要 | 未认证时返回 HTTP 401 OAuth 挑战，由 Agent 打开浏览器授权；不返回登录链接、密码或 Token。 |
| `get_ai_devops_login_status` | 否 | 查询当前 OAuth 登录状态 | `LOGGED_IN`、当前用户摘要、Access Token 失效时间 | 未认证时返回 HTTP 401 OAuth 挑战；不返回 Token。 |
| `get_current_ai_devops_user` | 否 | 获取当前 OAuth 主体的安全摘要 | 用户 ID、用户名、邮箱、Token 到期时间 | 不返回角色、授权项或 Token。 |
| `logout_ai_devops` | 否 | 撤销当前 Agent 的 OAuth 授权链 | 已登出 | 撤销当前 Grant 的有效 Token；重复调用按安全幂等处理。 |

后续项目、流水线和部署工具的认证方式固定为本契约，但每个工具的实际权限必须由 IAM 资源、动作、范围授权门面决定。
