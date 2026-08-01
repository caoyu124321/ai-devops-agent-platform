# 流水线插件模块

**状态**：规格编写中，尚未开始插件代码实现。

## 目标

为流水线提供独立、可替换的任务能力。插件模块可以实现 Maven/Java 构建、Docker 容器中的测试、Kubernetes 部署或人工确认；流水线核心只通过 `PipelinePlugin` 契约调用它们，不得识别或分支处理具体插件名称。

## 模块边界

```text
pipeline 核心 -> PipelinePlugin / PluginCatalog <- 独立插件模块
                                               <- 产物模块适配器
                                               <- 项目环境配置适配器
```

- `pipeline` 保存定义、创建运行、负责状态机与授权复查。
- 插件实现负责特定任务的输入校验、受控执行、取消、脱敏日志和结构化结果。
- 插件不能读取凭据明文；只可通过受控配置引用取得短期、最小权限的执行材料。
- 插件不能把 Docker、Kubernetes SDK 或宿主机执行逻辑反向引入 `pipeline` Maven 模块。

## 首批能力

1. `platform.maven-java-build@1.0.0`：在受控 Docker 容器中检出公开 GitHub HTTPS 仓库并执行 Maven/Java 构建与测试。
2. `platform.kubernetes-deploy@1.0.0`：将仓库内声明式 Kubernetes 清单部署到已预配置、授权且健康的环境。
3. `platform.manual-confirmation@1.0.0`：以进程内等待模型表达人工确认；该插件的二次确认规则由 IAM/运行编排的后续规格定义。

产物保存不是插件内置能力。构建插件只发布元数据和文件流，产物模块决定存储后端与下载授权。

