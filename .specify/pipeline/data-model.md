# 流水线模块数据模型

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| `pl_pipelines` | 项目下的流水线当前状态。 | 项目内名称唯一；保存当前版本和启用状态。 |
| `pl_pipeline_versions` | 不可变 YAML 和解析后的摘要。 | `(pipeline_id, version_no)` 唯一；保存单仓库引用。 |
| `pl_pipeline_steps` | 流水线版本中按顺序解析后的步骤快照。 | `(pipeline_version_id, step_id)` 唯一；保存插件名、精确版本及脱敏输入。 |
| `pl_runs` | 一次运行及其脱敏配置快照。 | 调用方幂等键在项目范围唯一；保存流水线和仓库/环境版本引用。 |
| `pl_step_runs` | 某运行的步骤状态、结果和时间。 | `(run_id, sequence_no)` 唯一；步骤不保存敏感输入。 |
| `pl_step_logs` | 步骤的有序脱敏日志。 | `(step_run_id, sequence_no)` 唯一。 |

凭据本体、Docker 容器信息和 Kubernetes 客户端对象不保存于流水线表。环境及凭据引用继续由项目管理模块拥有。
