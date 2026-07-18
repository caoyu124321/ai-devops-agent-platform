# IAM 统一授权模型

## 决策契约

`AuthorizationRequest = Subject + Resource + Action + Scope + Context`

| 元素 | 说明 |
| --- | --- |
| Subject | 已认证用户 ID、会话 ID；会话有效性由认证层保证 |
| Resource | 调用方定义的资源类型和资源标识；IAM 不解释其业务含义 |
| Action | 调用方定义的动作编码，例如 `environment.deploy` |
| Scope | `PLATFORM`、`TENANT`、`PROJECT`、`ENVIRONMENT`，以及 tenant/project/environment 标识和环境等级 |
| Context | 调用方传入的非敏感上下文；当前 MVP 不将其持久化 |

决策输出为 `ALLOW` 或 `DENY`，并包含稳定的 `reasonCode`、命中的内部授权记录 ID 列表和 `decisionVersion`。平台范围没有平台角色，始终默认拒绝。

## 角色与范围

租户成员身份和项目角色分层保存，避免“同一成员只能有一个项目角色”的限制。

| 层级 | 角色 | 固定权限模板 |
| --- | --- | --- |
| 租户 | `TENANT_ADMIN` | 所属租户内任意抽象资源、动作和下级范围；包括生产环境部署 |
| 租户 | `MEMBER` | 仅具有成员身份，默认没有租户级业务权限 |
| 项目 | `PROJECT_ADMIN` | 已绑定项目及其环境下的任意资源和动作；包括 `PROD` 部署 |
| 项目 | `DEVELOPER` | 已绑定项目的 `repository.use`，以及 `TEST`、`STAGING` 环境下的全部动作；`PROD` 始终拒绝 |

- `iam_tenant_members.role_code` 仅允许 `TENANT_ADMIN`、`MEMBER`。
- `iam_project_role_bindings` 保存 `PROJECT_ADMIN`、`DEVELOPER` 和抽象 `projectId`；同一成员可在同一租户的不同项目拥有不同角色。
- 内置模板不可由用户修改。用户只感知角色；不提供授权项的 REST 管理接口。
- IAM 不依赖项目、环境、流水线或其他业务模块。调用方必须提交真实的租户与范围上下文。

## 匹配、继承与冲突

- 授权以前提成员关系开始：不是目标租户成员时，统一返回不可见的拒绝结果。
- 租户范围向项目和环境范围继承；项目范围向该项目的环境范围继承；环境范围仅覆盖自身。
- 内部范围记录支持 `resourceId=*`、`actionCode=*`，用于匹配任意同类资源或动作。
- 固定角色模板优先于内部附加记录；无匹配即拒绝。开发者的 `PROD` 拒绝优先于任何内部附加记录。
- IAM 不实现显式拒绝授权项。后续引入时必须定义优先级后再开放能力。

## 变更事件

成员移除、租户角色变化、项目角色变化和内部授权撤销都在事务提交后进程内广播。事件不含密码、Token、凭据或 Kubernetes 配置。IAM 不监听或停止任务；流水线调度模块消费事件后自行重做授权决策。
