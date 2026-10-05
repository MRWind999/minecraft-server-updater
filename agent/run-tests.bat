@echo off
chcp 65001 >nul
REM ── Minecraft Client Update Java Agent self-check (Windows) ──────
REM Compiles src\ plus test\ into a temporary directory and runs AgentSelfCheck.
REM Usage: run-tests.bat
REM ──────────────────────────────────────────────────────────────────

setlocal
set "SCRIPT_DIR=%~dp0"
if "%JAVA_RELEASE%"=="" (set "RELEASE=15") else (set "RELEASE=%JAVA_RELEASE%")
set "BUILD_DIR=%TEMP%\mc-update-self-check-%RANDOM%%RANDOM%"

echo [test] Compiling sources and self-check (target Java %RELEASE%)...
mkdir "%BUILD_DIR%"
dir /s /b "%SCRIPT_DIR%src\*.java" > "%BUILD_DIR%\sources.txt"
dir /s /b "%SCRIPT_DIR%test\*.java" >> "%BUILD_DIR%\sources.txt"
javac --release %RELEASE% -d "%BUILD_DIR%" @"%BUILD_DIR%\sources.txt"
if %ERRORLEVEL% neq 0 (
    echo [test] Compilation failed!
    rmdir /s /q "%BUILD_DIR%" 2>nul
    exit /b 1
)

echo [test] Running AgentSelfCheck...
java -cp "%BUILD_DIR%" AgentSelfCheck
set "RESULT=%ERRORLEVEL%"

rmdir /s /q "%BUILD_DIR%" 2>nul
if %RESULT% neq 0 (
    echo [test] Self-check failed!
    exit /b %RESULT%
)
echo [test] Self-check passed.
endlocal
