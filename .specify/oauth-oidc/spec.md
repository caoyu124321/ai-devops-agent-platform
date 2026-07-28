# IAM OAuth/OIDC 提供方规格

**修订版本**：1.4
**状态**：需求与技术设计已确认，待实施
**关联规格**：`../codex-mcp/spec.md`、`../iam/user-stories.md`

## 模块目标

在不改变 IAM 是唯一身份与授权来源这一边界的前提下，为外部 MCP Agent 提供基于 OAuth 2.0 与 OpenID Connect 的标准浏览器登录能力。该能力用于让客户端获取“某个用户在某个已批准客户端中的短期访问授权”，而不是把平台密码或长期会话交给 MCP Server。

## 范围

- 授权码流程、PKCE S256、OIDC 身份声明、OIDC/OAuth 元数据发现。
- 公共客户端的动态注册、启用状态与限流策略。
- Access Token、Refresh Token、授权码、用户授权同意及浏览器授权会话的生命周期。
- Token 撤销、自省和因改密导致的授权链撤销。
- 为 Streamable HTTP MCP 资源服务器提供受保护资源元数据及认证主体契约。

## 非范围

- 不提供资源所有者密码流程、隐式流程或在 API/MCP 参数中传递密码。
- 不提供客户端凭据、服务账号、设备授权、Token 交换、DPoP、mTLS。
- 不将 OAuth Scope 作为租户、项目或环境业务权限的替代品。
- 不新增审计数据表或审计查询功能。

## 术语

| 术语 | 定义 |
| --- | --- |
| 授权服务器 | 本平台 IAM 扩展，负责用户登录、同意、授权码和 Token 签发。 |
| 资源服务器 | 验证 Access Token 后提供 API 或 MCP 能力的组件；MCP 仍需调用 IAM 的统一授权入口。 |
| 公共客户端 | 不能可靠保管 `client_secret` 的 Agent，例如桌面 Agent；使用 PKCE 证明授权码持有者。 |
| 授权链（Grant） | 用户、客户端、受众和已同意 Scope 的长期关系；其下包含可轮换的 Refresh Token。 |
| Access Token | 有效期 15 分钟的随机不透明 Token，仅用于访问指定受众。 |
| Refresh Token | 仅用于续期 Access Token 的随机不透明 Token；不能直接调用 MCP。 |

## 功能需求

- FR-OAUTH-001：授权服务器必须只接受授权码流程，且公共客户端必须提交 `code_challenge_method=S256`。
- FR-OAUTH-002：授权码只能使用一次，最长有效期 5 分钟，并与客户端、回调地址和 PKCE 校验值绑定。
- FR-OAUTH-003：Access Token 必须为随机不透明 Token，有效期固定 15 分钟，数据库仅保存哈希，并绑定用户、客户端、受众和 Scope。
- FR-OAUTH-004：Refresh Token 的授权链最长有效期为 30 天；连续 7 天没有成功刷新即失效；每次成功刷新必须轮换 Token。
- FR-OAUTH-005：发现任何已轮换 Refresh Token 被重用时，必须撤销同一授权链全部 Refresh Token 和未过期 Access Token，并要求重新浏览器登录。
- FR-OAUTH-006：平台用户登出当前 OAuth 客户端、主动撤销、修改密码或客户端被停用时，必须撤销受影响的 OAuth Token；成员移除不撤销平台身份 Token，但后续租户资源授权必须拒绝。
- FR-OAUTH-007：OAuth Scope 仅表达协议能力与 Token 受众，例如 `openid`、`profile`、`offline_access`、`mcp.tools`；项目、环境和部署权限必须在资源调用时由 IAM 统一授权决定。
- FR-OAUTH-008：动态注册仅允许公共客户端；注册记录具有 `PENDING`、`ACTIVE`、`SUSPENDED` 状态。`PENDING` 或 `SUSPENDED` 客户端不得开始授权流程。
- FR-OAUTH-009：生产环境回调地址必须为 HTTPS，或符合原生应用 `localhost` 回调规则；必须精确匹配已登记地址。开发环境只允许 `127.0.0.1` HTTP 回调。
- FR-OAUTH-010：OIDC Discovery、JWK Set、OAuth Authorization Server Metadata 和 MCP Protected Resource Metadata 必须可公开读取，但不得泄露客户机密、Token 或用户信息。
- FR-OAUTH-011：用户可在浏览器中拒绝授权；拒绝不得创建授权链或发放 Token。
- FR-OAUTH-012：所有 Token、授权码和浏览器会话 Cookie 均不得以明文写入数据库、日志、异常、MCP 输出或工作区文件。
- FR-OAUTH-013：动态客户端注册必须按直接来源 IP 限制为每小时最多 10 次；超限返回安全的限流错误，不泄露已有客户端信息。
- FR-OAUTH-014：客户端名称、发布方字段和回调地址以外的普通注册元数据均不得作为品牌信任依据；只有由预配置可信签发方签名且验证通过的 `software_statement` 才能识别 Agent 品牌。
- FR-OAUTH-015：未知公共客户端默认状态必须可通过后端配置定义，默认 `ACTIVE`，以支持兼容 OAuth + PKCE 的 Agent 自助接入；配置可按精确 `client_id` 覆盖为 `PENDING`、`ACTIVE` 或 `SUSPENDED`，配置变更在服务重启后生效。无论默认策略为何，动态注册均必须受回调地址校验、PKCE、用户登录同意和每 IP 每小时 10 次注册限流约束。
- FR-OAUTH-016：可信品牌的签名软件声明通过验证且品牌配置已启用时，客户端自动为 `ACTIVE`；品牌被禁用时，客户端不得开始或继续 OAuth 授权。
- FR-OAUTH-017：MCP 的 OAuth `resource`、Access Token 受众和受保护资源元数据中的 `resource` 必须统一为 `${issuer}/mcp`。受保护 MCP 工具在缺少、无效、过期或受众不匹配的 Token 时，必须返回 HTTP `401` 与指向 `${issuer}/.well-known/oauth-protected-resource/mcp` 的 `WWW-Authenticate` 挑战，供 Agent 发起浏览器 OAuth 登录。

## 用户故事

### US-OAUTH-01 浏览器授权

作为平台用户，我希望 Agent 在浏览器将我引导至可信 IAM 登录和同意页，以便授权该 Agent 访问我有权限使用的 AI DevOps 能力。

**验收标准**：登录成功并同意后仅向严格匹配的回调地址返回一次性授权码；取消、拒绝、过期和 PKCE 校验失败均不签发 Token。

### US-OAUTH-02 安全续期与重用防护

作为长期使用 Agent 的用户，我希望登录可在有限时间内自动续期，同时泄露的旧刷新令牌会被系统识别和切断。

**验收标准**：刷新后旧令牌失效；旧令牌重用后新令牌也不能继续使用；30 天或 7 天闲置任一条件达到后必须重新登录。

### US-OAUTH-03 外部 Agent 接入

作为 Agent 提供方，我希望无需获得共享密钥即可按标准注册公共客户端并接入 MCP，以便平台能支持多种 Agent。

**验收标准**：注册仅返回公共客户端标识；默认配置下客户端可直接发起用户授权，但仅允许登记的回调地址和授权码 + PKCE；被配置为 `PENDING` 或 `SUSPENDED` 的客户端不能发起授权。

### US-OAUTH-04 本地终版联调

作为开发者，我希望在 `127.0.0.1` 使用生产同一协议验证 OAuth 与 MCP，以便不再依赖 Windows 凭据管理器或 stdio JAR。

**验收标准**：本地 Agent 通过 HTTP MCP URL 完成浏览器 OAuth 登录；`resource`、Token 受众和受保护资源元数据均为同一 MCP URL；生产配置切换 HTTPS 域名后不改变协议和工具行为。

### US-OAUTH-05 按品牌与实例控制客户端接入

作为平台部署者，我希望兼容协议的未知 Agent 能自助接入，同时仍能通过受控配置暂停或停用特定 `client_id`，并在未来为可信品牌增加更强的身份识别，以便兼顾开放协议和平台安全。

**验收标准**：无可信软件声明的客户端默认 `ACTIVE`；配置中的精确 `client_id` 可被设为 `PENDING` 或 `SUSPENDED`；声明签名、签发方、受众、有效期、软件标识和回调地址任一校验失败时不得获得品牌可信资格，也不得绕过显式停用。
