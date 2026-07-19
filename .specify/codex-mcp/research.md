# 技术调研

## MCP Java SDK 与传输方式

- 官方 MCP Java SDK 的核心模块提供 stdio、SSE 和 Streamable HTTP 服务端传输，无需引入 Web 框架。
- 本功能的 Codex 与服务位于同一 Windows 主机，适合使用 stdio。该方式由 Codex 启动子进程，通过标准输入输出交换 MCP 消息，不需要监听端口。
- 因此选择 Java MCP SDK 的核心 stdio 传输，而不在现有 Spring Boot HTTP 服务中新增 MCP HTTP 端点。

来源：[MCP Java SDK 服务端文档](https://java.sdk.modelcontextprotocol.io/latest/server/)、[MCP Java SDK 仓库](https://github.com/modelcontextprotocol/java-sdk)。

## Windows 凭据管理器

- Windows 凭据管理器可为当前 Windows 用户保存应用凭据；凭据位于用户的 Windows Vault 中。
- Java 进程通过 JNA 调用 Windows `CredReadW`、`CredWriteW`、`CredDeleteW` API，实现 Token 的读、写、删。
- 项目使用 JDK 26。JNA 在 JDK 24 及以上需要启用原生访问，因此启动 MCP 进程时必须加入 `--enable-native-access=ALL-UNNAMED`。

来源：[Microsoft Windows 凭据管理器说明](https://learn.microsoft.com/en-us/windows-server/security/windows-authentication/credentials-processes-in-windows-authentication)、[JNA JDK 24+ 说明](https://github.com/java-native-access/jna/issues/1665)。

## Codex 配置

- Codex 支持在 `config.toml` 中为本地 stdio MCP Server 声明 `command`、`args`、`env`、启动超时和工具超时。
- Codex 的 MCP 输入提示受 `mcp_elicitations` 权限项控制。若当前环境禁用该能力，登录工具必须显式返回不可用结果，不能要求用户将密码作为普通工具参数传递。

来源：[Codex MCP 文档](https://learn.chatgpt.com/docs/extend/mcp)。
