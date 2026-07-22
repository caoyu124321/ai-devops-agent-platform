# OAuth/OIDC 与远程 MCP 测试计划

## 自动化单元与服务测试

- PKCE：缺失、`plain`、错误校验值、正确 S256 校验值。
- 授权码：5 分钟到期、仅能使用一次、客户端/回调地址不匹配。
- 回调地址：生产 HTTPS 严格匹配、原生 `localhost` 规则、开发环境仅 `127.0.0.1` HTTP。
- 客户端：未知 HTTPS 公共客户端默认创建为 `ACTIVE`；精确 `client_id` 覆盖可使其成为 `PENDING` 或 `SUSPENDED`；三种状态均须拒绝共享密钥和不支持 grant。
- Access Token：15 分钟到期、撤销、受众不匹配、Scope 不足。
- Refresh Token：连续刷新、30 天绝对截止、7 天闲置、旧 Token 重用、并发刷新只允许一条有效分支。
- 生命周期：登出、改密、客户端停用和成员移除的差异化行为。
- 输出与日志：密码、授权码、PKCE verifier、Access/Refresh Token 不能出现在响应日志或异常中。

## 协议与契约测试

- Discovery 与实际端点、支持 Scope、PKCE 方法、JWK `kid` 一致。
- OIDC ID Token 可通过 JWK 验签，且不作为 MCP Bearer Token 接受。
- `/oauth/introspect` 拒绝匿名外部调用。
- MCP `/mcp` 公开工具可匿名调用；受保护工具无 Token、错误 Token、过期 Token 的返回一致，且为 HTTP `401` 并携带指向受保护资源元数据的 `WWW-Authenticate` 挑战。
- MCP 工具认证成功后，授权由 IAM 资源/动作/范围门面决定，不能通过角色字符串分支绕过。

## 本地终版验收

1. 启动唯一 Spring Boot 应用，确认只绑定 `127.0.0.1:8080`。
2. 使用 URL 型 MCP 配置连接 `http://127.0.0.1:8080/mcp`，不启动本地 MCP JAR。
3. 通过浏览器注册、登录、同意，完成授权码 + PKCE 登录。
4. 调用当前用户和需要登录的 MCP 工具，确认不要求在聊天中输入密码。
5. 让 Access Token 到期或模拟刷新，验证 Agent 使用轮换后的 Refresh Token 继续工作。
6. 重放上一枚 Refresh Token，确认整个授权链失效并需要重新登录。
7. 修改密码后确认所有 OAuth Agent 登录失效；移除租户成员后确认仅该租户资源被拒绝。

## HTTPS 预发布验收

- 仅开放 HTTPS MCP 与 IAM 地址，HTTP 请求被拒绝或重定向到 HTTPS。
- 反向代理后的 Issuer、Discovery、回调地址和 Cookie 属性均指向外部可信地址。
- 使用至少 Codex 与另一种兼容 Agent 完成独立登录验证。
- 使用一个未预先配置 `client_id` 的兼容 Agent 注册并完成独立登录，确认默认 `ACTIVE` 不绕过登录、同意、PKCE 和回调地址校验。
