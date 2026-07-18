# IAM 数据模型与表结构设计

**数据库**：MySQL 8+  
**范围**：IAM 持久化数据；不创建项目、环境、流水线、运行、插件或产物表。  
**约定**：主键使用 UUID；时间统一 UTC；敏感值仅保存不可逆哈希或安全引用。

## 1. 表清单

| 表 | 目的 |
| --- | --- |
| `iam_users` | 平台用户身份与密码哈希 |
| `iam_sessions` | 多设备登录会话与 Token 撤销状态 |
| `iam_login_locks` | 连续登录失败计数与 15 分钟锁定状态 |
| `iam_tenants` | 租户；名称允许重复 |
| `iam_tenant_members` | 用户在租户中的成员关系和内置角色 |
| `iam_invitations` | 仅面向已注册用户的 7 天邀请 |
| `iam_authorization_grants` | 项目/环境等抽象范围的附加授权项 |
| `iam_project_role_bindings` | 成员在项目范围内的固定内置角色绑定 |

## 2. 用户与认证

### `iam_users`

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)`，主键 | 用户 ID |
| `username` | `varchar(64)`，唯一、非空 | 用户名；可用于登录 |
| `email` | `varchar(254)`，唯一、非空 | 邮箱；可用于登录 |
| `password_hash` | `varchar(255)`，非空 | 密码不可逆哈希 |
| `created_at` / `updated_at` | `datetime(3)`，非空 | 创建与更新时间 |

约束：不包含账号禁用字段；用户名和邮箱分别唯一；不保存密码明文或密码历史。

### `iam_sessions`

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)`，主键 | 会话 ID，同时作为 Token 的会话引用 |
| `user_id` | `char(36)`，非空，外键 → `iam_users.id` | 会话所属用户 |
| `token_hash` | `varchar(255)`，唯一、非空 | 随机不透明 Token 的不可逆摘要，不保存原文 |
| `issued_at` / `expires_at` | `datetime(3)`，非空 | 签发/到期时间；到期为签发后 24 小时 |
| `revoked_at` | `datetime(3)`，可空 | 撤销时间；非空即不可使用 |
| `revocation_reason` | `varchar(32)`，可空 | `LOGOUT`、`PASSWORD_CHANGED` 等 |
| `client_summary` | `varchar(255)`，可空 | 经脱敏后的设备/客户端摘要 |

索引：`(user_id, revoked_at, expires_at)` 用于改密时批量撤销和请求期校验；`token_hash` 唯一索引用于认证查询。

### `iam_login_locks`

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| `user_id` | `char(36)`，主键，外键 → `iam_users.id` | 已识别用户的失败状态 |
| `failed_count` | `int`，非空 | 连续失败次数 |
| `last_failed_at` | `datetime(3)`，可空 | 最近失败时间 |
| `locked_until` | `datetime(3)`，可空 | 锁定截至时间 |
| `updated_at` | `datetime(3)`，非空 | 更新时间 |

规则：只对已识别用户累积失败次数，避免通过错误登录标识制造无限状态；第 5 次连续失败设置 `locked_until = 当前时间 + 15 分钟`；成功登录删除或重置该记录。

## 3. 租户、成员与邀请

### `iam_tenants`

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)`，主键 | 租户 ID |
| `name` | `varchar(128)`，非空、非唯一 | 1–128 个字符；允许中文、英文、数字、空格和常用符号；可重复 |
| `created_by` | `char(36)`，非空，外键 → `iam_users.id` | 创建人 |
| `created_at` / `updated_at` | `datetime(3)`，非空 | 时间戳 |

### `iam_tenant_members`

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)`，主键 | 成员关系 ID |
| `tenant_id` | `char(36)`，非空，外键 → `iam_tenants.id` | 所属租户 |
| `user_id` | `char(36)`，非空，外键 → `iam_users.id` | 成员用户 |
| `role_code` | `varchar(32)`，非空 | 租户成员身份：`TENANT_ADMIN` 或 `MEMBER`；项目角色不存放在本表 |
| `joined_at` / `updated_at` | `datetime(3)`，非空 | 加入与更新时间 |

约束与索引：`unique(tenant_id, user_id)`；`(tenant_id, role_code)` 索引用于管理员保护校验；最后管理员规则在事务内校验，不依赖数据库检查约束。

### `iam_invitations`

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)`，主键 | 邀请 ID |
| `tenant_id` | `char(36)`，非空，外键 → `iam_tenants.id` | 目标租户 |
| `invited_user_id` | `char(36)`，非空，外键 → `iam_users.id` | 已注册的受邀用户 |
| `role_code` | `varchar(32)`，非空 | 接受后的初始角色 |
| `invited_by` | `char(36)`，非空，外键 → `iam_users.id` | 邀请发起者 |
| `created_at` / `expires_at` | `datetime(3)`，非空 | 到期时间为创建后 7 天 |
| `status` | `varchar(16)`，非空 | `PENDING`、`ACCEPTED`、`REJECTED`、`REVOKED` |
| `resolved_at` | `datetime(3)`，可空 | 被处理时间 |

索引：`(tenant_id, invited_user_id, status, expires_at)`。创建邀请时在事务内检查是否有未到期 `PENDING` 邀请；租户管理员可将 `PENDING` 更新为 `REVOKED`；过期清理任务删除过期 `PENDING` 记录。

## 4. 授权项

### `iam_authorization_grants`

该表不引用未来项目或环境表，只保存抽象资源范围标识，以保持 IAM 与业务模块解耦。

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)`，主键 | 授权项 ID |
| `tenant_id` | `char(36)`，非空，外键 → `iam_tenants.id` | 租户边界 |
| `member_id` | `char(36)`，非空，外键 → `iam_tenant_members.id` | 被授权成员 |
| `resource_type` | `varchar(32)`，非空 | `PROJECT`、`ENVIRONMENT` 等 |
| `resource_id` | `varchar(64)`，非空 | 业务模块资源 ID |
| `action_code` | `varchar(64)`，非空 | 如 `environment.deploy` |
| `environment_level` | `varchar(16)`，可空 | `TEST`、`STAGING`、`PROD`；无关动作为空 |
| `effect` | `varchar(8)`，非空 | MVP 仅写入 `ALLOW`；保留 `DENY` 扩展 |
| `created_by` | `char(36)`，非空，外键 → `iam_users.id` | 授权人 |
| `created_at` / `updated_at` | `datetime(3)`，非空 | 时间戳 |

约束：`unique(member_id, resource_type, resource_id, action_code, environment_level, effect)`；索引 `(tenant_id, member_id, resource_type, resource_id, action_code)` 用于授权决策。

## 5. 事务规则

1. 创建租户与创建首位租户管理员成员关系必须在同一事务内。
2. 接受邀请与创建成员关系必须在同一事务内，并依赖 `unique(tenant_id, user_id)` 防止重复成员。
3. 移除成员、角色变更与管理员转移必须在同一事务内校验“至少一名管理员”。
4. 改密与撤销全部会话必须在同一事务内；成功提交后再广播 `PasswordChanged` 事件。
5. 成员移除或角色变更提交成功后再广播对应授权变更事件。

## 6. 删除与保留

- 用户、租户、成员关系不做物理删除，除非未来增加合规删除策略；本期通过成员移除表达租户撤权。
- 邀请在过期后物理删除；已处理邀请不保留额外历史数据。
- 会话到期后可由定时清理任务删除；已撤销会话在保留期后清理。

## 7. MySQL 物理实现说明

- 全量建表脚本位于 [`database/iam-schema.sql`](../../database/iam-schema.sql)，目标版本为 MySQL 8.0.16 及以上。
- `iam_authorization_grants.environment_level` 保持可空，表示该授权动作不区分环境等级。由于 MySQL 复合唯一索引允许多条包含 `NULL` 的记录，脚本增加数据库生成列 `environment_level_key`，将 `NULL` 规范化为 `''`，并用它实现原有的授权项唯一性规则；该列不是领域模型字段，也不由应用写入。
- 授权项通过复合外键 `(member_id, tenant_id)` 指向成员关系，确保授权项所属租户与被授权成员的租户一致。
- 账户名、邮箱、租户名称的具体格式由应用层校验；数据库仅承担已定义的长度、枚举、唯一性、关联完整性及静态时间先后约束。
