# MCP 测试计划

## 单元测试

| 场景 | 验证点 |
| --- | --- |
| 登录成功 | REST 客户端收到 Token 后写入凭据仓库；工具输出不含 Token |
| 登录失败 | 不覆盖已有活动凭据；只返回 `LOGIN_FAILED` |
| 用户取消输入 | 不发送登录 HTTP 请求，不修改凭据 |
| 当前用户未登录 | 不调用后端，返回 `NOT_LOGGED_IN` |
| 当前用户 401 | 清理凭据，返回 `SESSION_EXPIRED` |
| 当前用户 403 | 不清理凭据，返回 `BACKEND_ACCESS_DENIED` |
| 后端不可用 | 保留凭据，返回 `BACKEND_UNAVAILABLE` |
| 登出 | 始终清理本地凭据；重复执行成功 |
| 凭据仓库异常 | 返回 `CREDENTIAL_STORE_FAILED`，日志不含 Token |

## 契约测试

- 使用 HTTP Mock Server 验证请求方法、路径、请求体和 `Authorization` 头。
- 验证 `POST /auth/login` 的 Token 不会出现在 MCP 输出或日志捕获内容。
- 验证 `GET /me` 与 `POST /auth/logout` 仅使用凭据仓库读取的 Token。
- 验证 MCP 工具的输入 Schema 不包含用户名或密码字段。

## Windows 集成测试

- 在当前 Windows 用户下写入、读取、删除 Generic Credential。
- 确认目标名包含平台地址摘要与用户 ID，且工作区中没有 Token 文件。
- 使用 Java 26 和 `--enable-native-access=ALL-UNNAMED` 启动 MCP JAR，验证 JNA 原生调用可用。

## Codex 人工验收

1. 启动本机后端服务。
2. 配置并重启 Codex，使其加载 `ai_devops` MCP Server。
3. 请求“登录 ai-devops”，确认出现 Codex 输入提示而非普通工具参数。
4. 使用已注册账号登录，确认得到安全用户摘要。
5. 请求当前登录用户，确认无需再次输入密码。
6. 在后端撤销或等待 Token 失效后再次请求当前用户，确认提示重新登录。
7. 请求登出并再次查询当前用户，确认返回需要登录。
