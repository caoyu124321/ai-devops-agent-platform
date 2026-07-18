# IAM 对外接口规划

统一前缀为 `/api/v1`。错误响应统一包含 `code`、`message`、`traceId`、空的安全 `details`；未认证、无权限和不可见资源不暴露其他租户信息。

| 接口 | 调用方 | 目的 | 主要输入 | 主要错误 |
| --- | --- | --- | --- | --- |
| `POST /auth/register` | 匿名用户 | 注册用户 | 用户名、邮箱、密码 | 标识冲突、密码不合规 |
| `POST /auth/login` | 匿名用户 | 用户名或邮箱登录，创建 24 小时会话 | 登录标识、密码 | `LOGIN_FAILED` |
| `POST /auth/logout` | 已登录用户 | 撤销当前会话 | 无 | 未认证 |
| `POST /auth/password/change` | 已登录用户 | 校验旧密码后改密并撤销全量会话 | 旧密码、新密码 | 旧密码错误、密码不合规 |
| `GET /me` | 已登录用户 | 获取当前用户安全摘要 | 无 | 未认证 |
| `POST /tenants` | 已登录用户 | 创建同名允许重复的租户 | 名称 | 名称不合法 |
| `GET /tenants` | 已登录用户 | 查询本人所属租户 | 无 | 未认证 |
| `GET /tenants/{tenantId}/members` | 租户成员 | 查询本租户成员 | 无 | 租户不可见 |
| `POST /tenants/{tenantId}/invitations` | 租户管理员 | 邀请已注册用户成为 `MEMBER` 或 `TENANT_ADMIN` | 登录标识、租户成员身份 | 无权限、重复邀请、已是成员 |
| `GET /invitations/{invitationId}` | 受邀用户 | 查询本人待处理邀请 | 无 | 邀请不可见、已过期 |
| `POST /invitations/{invitationId}/accept` | 受邀用户 | 接受邀请 | 无 | 邀请不可见、已处理 |
| `POST /invitations/{invitationId}/reject` | 受邀用户 | 拒绝邀请 | 无 | 邀请不可见、已处理 |
| `DELETE /invitations/{invitationId}` | 租户管理员 | 撤销待处理邀请 | 无 | 无权限、已处理 |
| `PATCH /tenants/{tenantId}/members/{memberId}/role` | 租户管理员 | 调整租户成员身份 | `roleCode`：`TENANT_ADMIN` 或 `MEMBER` | 最后管理员保护、角色非法 |
| `DELETE /tenants/{tenantId}/members/{memberId}` | 租户管理员 | 移除成员及其项目角色 | 无 | 最后管理员保护、成员不可见 |
| `POST /tenants/{tenantId}/leave` | 租户成员 | 主动退出租户 | 无 | 最后管理员保护 |
| `POST /tenants/{tenantId}/members/{memberId}/project-roles` | 租户管理员 | 在抽象项目范围绑定固定项目角色 | `projectId`、`roleCode`：`PROJECT_ADMIN` 或 `DEVELOPER` | 无权限、成员不可见、项目或角色非法 |
| `GET /tenants/{tenantId}/members/{memberId}/project-roles` | 租户成员 | 查询成员在当前租户内的项目角色 | 无 | 租户或成员不可见 |
| `DELETE /tenants/{tenantId}/members/{memberId}/project-roles/{projectId}` | 租户管理员 | 撤销一个项目范围角色 | 无 | 无权限、成员或项目角色不可见 |
| `POST /authorization/check` | 后续模块或受控内部调用 | 按资源、动作、范围取得统一授权决策 | `resourceType`、`resourceId`、`actionCode`、`scope`、`context` | 未认证、请求不完整、无匹配权限 |

不提供 `/authorization-grants` 的查询、创建或撤销接口；它是 IAM 内部匹配数据。MVP 不提供二次确认接口，生产部署由 `environment.deploy` 的决策直接决定。
