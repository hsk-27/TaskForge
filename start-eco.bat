@echo off
setlocal
echo ========================================================
echo  TaskForge Engine - Low-Resource Eco-Mode Launcher
echo  JVM Memory Limit: 256MB ^| Background Thread Priority: Low
echo ========================================================

set SCRIPT_DIR=%~dp0
set BACKEND_JAR=%SCRIPT_DIR%backend\target\taskforge-backend-1.0.0.jar

if "%1"=="clean" (
    echo [INFO] Purging local database files for pristine zero-job startup...
    if exist "%SCRIPT_DIR%data" rmdir /s /q "%SCRIPT_DIR%data"
)

if not exist "%BACKEND_JAR%" (
    echo [INFO] Packaging backend JAR for optimal low-memory startup...
    cd /d "%SCRIPT_DIR%backend"
    call mvn clean package -DskipTests
    cd /d "%SCRIPT_DIR%"
)

echo [1/2] Starting TaskForge Backend with bounded heap (-Xms64m -Xmx256m)...
start "TaskForge Backend (Eco-Mode)" java -Xms64m -Xmx256m -XX:+UseG1GC -Dspring.profiles.active=local -Dtaskforge.engine.eco-mode=true -Dtaskforge.engine.worker-count=2 -jar "%BACKEND_JAR%"

echo [2/2] Starting TaskForge Frontend dev server...
cd /d "%SCRIPT_DIR%frontend"
start "TaskForge Frontend" cmd /c "npm.cmd run dev"

echo.
echo ========================================================
echo  TaskForge is running in PC-Safe Eco-Mode!
echo  Backend API & Swagger: http://localhost:8080/swagger-ui.html
echo  Frontend Dashboard:    http://localhost:5173
echo ========================================================
