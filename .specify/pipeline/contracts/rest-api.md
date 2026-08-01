# 流水线 REST 接口契约

所有接口需要 IAM 会话 Token 或 OAuth Bearer Token；资源不可见时不返回归属信息。

| 方法 | 路径 | 目的 |
| --- | --- | --- |
| `POST` | `/api/v1/projects/{projectId}/pipelines/validate` | 校验 YAML，不持久化。 |
| `POST` | `/api/v1/projects/{projectId}/pipelines` | 创建流水线和版本 1。 |
| `GET` | `/api/v1/projects/{projectId}/pipelines` | 列表查询。 |
| `GET` | `/api/v1/pipelines/{pipelineId}` | 查询当前流水线。 |
| `PUT` | `/api/v1/pipelines/{pipelineId}` | 乐观锁更新并创建新版本。 |
| `POST` | `/api/v1/pipelines/{pipelineId}/enabled` | 启用或停用。 |
| `GET` | `/api/v1/pipelines/{pipelineId}/versions` | 查询版本历史。 |
| `POST` | `/api/v1/pipeline-versions/{versionId}/runs` | 创建一次运行；请求体支持 `branch`、`commit` 与 `parameters`，`Idempotency-Key` 可选。 |
| `GET` | `/api/v1/runs/{runId}` | 查询运行、阶段/步骤摘要。 |
| `GET` | `/api/v1/runs/{runId}/logs` | 游标查询脱敏日志。 |
| `POST` | `/api/v1/runs/{runId}/cancel` | 取消运行。 |
| `POST` | `/api/v1/runs/{runId}/retry` | 基于原始快照创建人工重试运行。 |

运行参数只允许使用 YAML `parameters` 已声明的键；必填项缺失、类型不匹配或步骤引用未提供的参数返回 `400`。`environment` 参数必须属于目标项目、已启用且健康，并在创建运行和调度前分别校验 `ENVIRONMENT / environment.deploy`。

校验失败返回 `400`，其中每项错误包含 `path`、`line`、`column`、`ruleCode`、`message` 与 `suggestion`。版本冲突返回 `409`；无认证返回 `401`；不可见资源返回统一 `404`。
