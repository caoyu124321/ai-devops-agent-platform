@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "AI_DEVOPS_PROJECT_ROOT=%~dp0.."
set "AI_DEVOPS_APPLICATION_JAR=%AI_DEVOPS_PROJECT_ROOT%\application\target\application-0.0.1-SNAPSHOT.jar"
set "AI_DEVOPS_MCP_JAR=%AI_DEVOPS_PROJECT_ROOT%\mcp\target\mcp-0.0.1-SNAPSHOT.jar"
set "AI_DEVOPS_PLATFORM_LOG=%TEMP%\ai-devops-platform.log"

set "AI_DEVOPS_JAVA="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "AI_DEVOPS_JAVA=%JAVA_HOME%\bin\java.exe"
if not defined AI_DEVOPS_JAVA for %%I in (java.exe) do set "AI_DEVOPS_JAVA=%%~$PATH:I"
if not defined AI_DEVOPS_JAVA for /d %%I in ("%USERPROFILE%\.jdks\openjdk-*") do if not defined AI_DEVOPS_JAVA if exist "%%~fI\bin\java.exe" set "AI_DEVOPS_JAVA=%%~fI\bin\java.exe"
if not defined AI_DEVOPS_JAVA goto java_not_found

if not exist "%AI_DEVOPS_APPLICATION_JAR%" goto application_jar_not_found

if not exist "%AI_DEVOPS_MCP_JAR%" goto mcp_jar_not_found

if /i "%~1"=="--verify" goto verification_complete

powershell -NoProfile -NonInteractive -Command "try { $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 1 http://127.0.0.1:8080/api/heartbeat; if ($response.StatusCode -eq 200) { exit 0 }; exit 1 } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 goto start_mcp

start "AI DevOps Platform" /b "%AI_DEVOPS_JAVA%" -jar "%AI_DEVOPS_APPLICATION_JAR%" > "%AI_DEVOPS_PLATFORM_LOG%" 2>&1
set /a AI_DEVOPS_ATTEMPT=0

:wait_for_platform
set /a AI_DEVOPS_ATTEMPT+=1
powershell -NoProfile -NonInteractive -Command "try { $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 1 http://127.0.0.1:8080/api/heartbeat; if ($response.StatusCode -eq 200) { exit 0 }; exit 1 } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 goto start_mcp
if !AI_DEVOPS_ATTEMPT! GEQ 30 goto platform_start_timeout
timeout /t 1 /nobreak >nul
goto wait_for_platform

:start_mcp
"%AI_DEVOPS_JAVA%" --enable-native-access=ALL-UNNAMED -jar "%AI_DEVOPS_MCP_JAR%"
exit /b %errorlevel%

:java_not_found
>&2 echo Java runtime was not found. Set JAVA_HOME or add java.exe to PATH.
exit /b 1

:application_jar_not_found
>&2 echo AI DevOps platform JAR was not found. Build the project first.
exit /b 1

:mcp_jar_not_found
>&2 echo AI DevOps MCP JAR was not found. Build the project first.
exit /b 1

:platform_start_timeout
>&2 echo AI DevOps platform did not start within 30 seconds. Check local Java and the platform log.
exit /b 1

:verification_complete
echo AI DevOps local MCP prerequisites are available.
exit /b 0
