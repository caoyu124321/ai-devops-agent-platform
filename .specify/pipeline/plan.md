# 流水线模块技术设计

**前置规格**：[spec.md](spec.md)  
**设计目标**：流水线核心只处理定义、版本、调度与状态；具体任务全部由可替换插件实现。

## 模块与依赖

新增 `pipeline` Maven 模块，依赖方向为：

```text
application -> pipeline -> project-management -> iam
                       -> iam
```

`pipeline` 不依赖 MCP，也不依赖 Docker、Kubernetes 或任何具体任务 SDK。MCP 以后只能通过 REST 调用流水线能力。

## 分层与解耦

```text
REST Controller
  -> PipelineService / PipelineRunService
      -> PipelineDao
      -> IAM AuthorizationService
      -> Project/Repository/Environment 查询边界
      -> PluginCatalog -> PipelinePlugin
```

- `PluginCatalog` 按 `插件名@版本` 返回描述符和执行实现。
- `PipelinePlugin` 的输入是通用 `PluginExecutionRequest`，输出是通用状态、日志、结构化输出和产物引用。
- 调度器不识别 Maven、Docker、Kubernetes 等名称，不包含任务类型分支；插件实现自行负责具体行为和安全校验。
- 首版调度只支持顺序执行。运行创建和调度解耦：创建后为 `QUEUED`，由独立的调度入口消费。

## YAML v1

```yaml
apiVersion: ai-devops/v1
name: example
repository: <repository-id>
source:
  branch: main # 可省略；省略时运行必须传 branch 或 commit
parameters:
  environmentId:
    type: environment
    required: true
stages:
  - name: build
    steps:
      - id: compile
        uses: plugin-name@1.0.0
        with:
          key: value
```

- 一条定义只能有一个仓库引用。
- `parameters` 只允许标量、布尔、数字、字符串和 `environment`；`${{ parameters.<name> }}` 是唯一变量引用格式。
- 步骤 `id` 在同版本内唯一；阶段和步骤按 YAML 顺序串行调度。
- `uses` 必须精确到插件版本。

## 运行与快照

运行创建时保存 YAML、插件描述符版本、仓库版本、环境版本、凭据引用版本的脱敏快照。调度前再次检查当前用户授权、流水线启用状态和环境健康状态；配置快照只用于历史追溯与可复现请求，不允许绕过已停用配置。

## 状态机

- 运行：`QUEUED -> RUNNING -> SUCCEEDED | FAILED | CANCELED | TIMED_OUT`。
- 步骤：`PENDING -> RUNNING -> SUCCEEDED | FAILED | CANCELED | TIMED_OUT | SKIPPED`。
- 任一步骤失败、取消或超时，当前运行的未开始步骤转为 `SKIPPED`，运行进入相应终态。
- 取消 `QUEUED` 运行直接进入 `CANCELED`；取消 `RUNNING` 运行请求当前插件取消，后续步骤不再调度。

## 授权

流水线模块通过 IAM 发送抽象请求，不读取角色：

| 行为 | Resource / Action | Scope |
| --- | --- | --- |
| 定义查看 | `PIPELINE / pipeline.view` | PROJECT |
| 创建/编辑/启停/回滚 | `PIPELINE / pipeline.edit` | PROJECT |
| 创建运行/重试 | `PIPELINE / pipeline.run` | PROJECT |
| 取消运行 | `PIPELINE / pipeline.cancel` | PROJECT |
| 查询日志 | `PIPELINE / pipeline.view` | PROJECT |
| 使用仓库 | `REPOSITORY / repository.use` | PROJECT |
| 部署环境 | `ENVIRONMENT / environment.deploy` | ENVIRONMENT |

缺失权限、跨租户与不存在资源统一表现为不可见或业务资源不存在，不泄露归属。

## 验证策略

- 服务层单元测试覆盖 YAML、版本、授权、资源隔离、幂等、状态机及插件交互。
- DAO/H2 集成测试覆盖迁移等价 Schema 与版本/唯一约束。
- Controller 测试覆盖 Token 认证、状态码和错误响应。
- 具体 Docker/Kubernetes 插件在后续独立规格中使用契约测试；本模块不调用外部执行系统。
