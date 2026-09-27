@echo off
setlocal

REM Get the directory where this script is located
set "APP_HOME=%~dp0"

REM Remove trailing backslash
if "%APP_HOME:~-1%"=="\" set "APP_HOME=%APP_HOME:~0,-1%"

REM Find the JAR file
for %%i in ("%APP_HOME%\*.jar") do set "JAR_FILE=%%i"

if not defined JAR_FILE (
    echo Error: No JAR file found in %APP_HOME%
    exit /b 1
)

REM Set the config directory
set "CONFIG_DIR=%APP_HOME%\config"

REM Number of label positions to skip on the first page (default 0)
set "SKIP_LABELS=%~1"
if not defined SKIP_LABELS set "SKIP_LABELS=0"

REM Run the application with external config
echo Starting HAB Card Generator (skipping %SKIP_LABELS% labels)...
java -jar "%JAR_FILE%" --spring.config.location="file:%CONFIG_DIR%/application.yml" %SKIP_LABELS%

endlocal
