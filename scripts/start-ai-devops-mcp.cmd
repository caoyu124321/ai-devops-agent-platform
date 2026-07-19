@echo off
setlocal
set "PROJECT_ROOT=%~dp0.."
set "MCP_JAR=%PROJECT_ROOT%\mcp\target\mcp-0.0.1-SNAPSHOT.jar"

if not exist "%MCP_JAR%" (
  >&2 echo AI DevOps MCP JAR 不存在，请先执行 Maven package。
  exit /b 1
)

if not defined JAVA_HOME (
  >&2 echo JAVA_HOME 未设置，无法启动 AI DevOps MCP。
  exit /b 1
)

"%JAVA_HOME%\bin\java.exe" --enable-native-access=ALL-UNNAMED -jar "%MCP_JAR%"
