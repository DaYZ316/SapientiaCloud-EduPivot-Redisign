@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
set "ARTIFACT_DIR=%ROOT_DIR%\.deploy"

if exist "%ROOT_DIR%\deploy_artifact.local.bat" call "%ROOT_DIR%\deploy_artifact.local.bat"

if not defined DEPLOY_HOST set "DEPLOY_HOST=117.72.194.197"
if not defined DEPLOY_USER set "DEPLOY_USER=root"
if not defined DEPLOY_PORT set "DEPLOY_PORT=22"
if not defined DEPLOY_DIR set "DEPLOY_DIR=/opt/sc-edupivot"

if not defined VITE_API_BASE_URL set "VITE_API_BASE_URL="
if not defined VITE_GITHUB_CLIENT_ID set "VITE_GITHUB_CLIENT_ID=Ov23liUPmFq65QzwTLji"
if not defined VITE_GITHUB_REDIRECT_URI set "VITE_GITHUB_REDIRECT_URI=https://edupivot.xyz/login"
if not defined VITE_GITHUB_SCOPE set "VITE_GITHUB_SCOPE=read:user user:email"
if not defined VITE_GOOGLE_CLIENT_ID set "VITE_GOOGLE_CLIENT_ID="
if not defined VITE_GOOGLE_REDIRECT_URI set "VITE_GOOGLE_REDIRECT_URI=https://edupivot.xyz/auth/callback/google"

set "BACKEND_SERVICES=sc-auth sc-notification sc-course sc-storage sc-ai sc-gateway"
set "COMPOSE=docker compose --env-file .env -f docker-compose.artifact.yaml"
set "INTERACTIVE=1"

if not "%~1"=="" (
    set "TARGET=%~1"
    set "INTERACTIVE=0"
    goto RUN_TARGET
)

:MENU
set "TARGET="
cls
echo ================================================================
echo      SapientiaCloud EduPivot Artifact Deploy
echo ================================================================
echo   1. frontend
echo   2. sc-auth
echo   3. sc-notification
echo   4. sc-course
echo   5. sc-storage
echo   6. sc-ai
echo   7. sc-gateway
echo   8. all-backend
echo   9. infra
echo  10. all
echo   0. exit
echo ================================================================
echo Server: %DEPLOY_USER%@%DEPLOY_HOST%:%DEPLOY_DIR%
echo.
set /p choice="Select deploy target: "

if "%choice%"=="1" set "TARGET=frontend"
if "%choice%"=="2" set "TARGET=sc-auth"
if "%choice%"=="3" set "TARGET=sc-notification"
if "%choice%"=="4" set "TARGET=sc-course"
if "%choice%"=="5" set "TARGET=sc-storage"
if "%choice%"=="6" set "TARGET=sc-ai"
if "%choice%"=="7" set "TARGET=sc-gateway"
if "%choice%"=="8" set "TARGET=all-backend"
if "%choice%"=="9" set "TARGET=infra"
if "%choice%"=="10" set "TARGET=all"
if "%choice%"=="0" exit /b 0

if not defined TARGET (
    echo Invalid target.
    timeout /t 2 >nul
    goto MENU
)

:RUN_TARGET
call :CHECK_REMOTE_TOOLS
if errorlevel 1 goto FAIL
call :CHECK_CMD tar
if errorlevel 1 goto FAIL

if /i "%TARGET%"=="frontend" goto DO_FRONTEND
if /i "%TARGET%"=="all-backend" goto DO_ALL_BACKEND
if /i "%TARGET%"=="infra" goto DO_INFRA
if /i "%TARGET%"=="all" goto DO_ALL

call :IS_BACKEND_SERVICE "%TARGET%"
if errorlevel 1 (
    echo Unknown target: %TARGET%
    goto FAIL
)

call :CHECK_CMD mvn
if errorlevel 1 goto FAIL
call :DEPLOY_BACKEND_SERVICE "%TARGET%"
if errorlevel 1 goto FAIL
goto FINISH

:DO_FRONTEND
call :DEPLOY_FRONTEND
if errorlevel 1 goto FAIL
goto FINISH

:DO_ALL_BACKEND
call :CHECK_CMD mvn
if errorlevel 1 goto FAIL
call :DEPLOY_ALL_BACKEND
if errorlevel 1 goto FAIL
goto FINISH

:DO_INFRA
call :UPLOAD_INFRA
if errorlevel 1 goto FAIL
call :RESTART_INFRA
if errorlevel 1 goto FAIL
goto FINISH

:DO_ALL
call :CHECK_CMD mvn
if errorlevel 1 goto FAIL
call :UPLOAD_INFRA
if errorlevel 1 goto FAIL
call :RESTART_INFRA
if errorlevel 1 goto FAIL
call :DEPLOY_ALL_BACKEND
if errorlevel 1 goto FAIL
call :DEPLOY_FRONTEND
if errorlevel 1 goto FAIL
goto FINISH

:FINISH
echo.
echo Done: %TARGET% deployed to %DEPLOY_USER%@%DEPLOY_HOST%:%DEPLOY_DIR%
if "%INTERACTIVE%"=="1" pause
exit /b 0

:FAIL
echo.
echo Deployment failed.
if "%INTERACTIVE%"=="1" pause
exit /b 1

:CHECK_CMD
where "%~1" >nul 2>nul
if errorlevel 1 (
    echo Missing required command: %~1
    exit /b 1
)
exit /b 0

:CHECK_REMOTE_TOOLS
if defined DEPLOY_PASSWORD (
    call :CHECK_CMD plink
    if errorlevel 1 (
        echo DEPLOY_PASSWORD is set, so PuTTY plink is required.
        exit /b 1
    )
    call :CHECK_CMD pscp
    if errorlevel 1 (
        echo DEPLOY_PASSWORD is set, so PuTTY pscp is required.
        exit /b 1
    )
    exit /b 0
)
call :CHECK_CMD ssh
if errorlevel 1 exit /b 1
call :CHECK_CMD scp
exit /b %ERRORLEVEL%

:IS_BACKEND_SERVICE
set "SERVICE_TO_CHECK=%~1"
for %%S in (%BACKEND_SERVICES%) do (
    if /i "%%S"=="%SERVICE_TO_CHECK%" exit /b 0
)
exit /b 1

:REMOTE
if defined DEPLOY_PASSWORD (
    plink -ssh -batch -P "%DEPLOY_PORT%" -pw "%DEPLOY_PASSWORD%" "%DEPLOY_USER%@%DEPLOY_HOST%" "%~1"
) else (
    ssh -p "%DEPLOY_PORT%" "%DEPLOY_USER%@%DEPLOY_HOST%" "%~1"
)
exit /b %ERRORLEVEL%

:SCP_TO_REMOTE
if defined DEPLOY_PASSWORD (
    pscp -P "%DEPLOY_PORT%" -pw "%DEPLOY_PASSWORD%" "%~1" "%DEPLOY_USER%@%DEPLOY_HOST%:%~2"
) else (
    scp -P "%DEPLOY_PORT%" "%~1" "%DEPLOY_USER%@%DEPLOY_HOST%:%~2"
)
exit /b %ERRORLEVEL%

:REMOTE_COMPOSE
call :REMOTE "cd %DEPLOY_DIR% && %COMPOSE% %~1"
exit /b %ERRORLEVEL%

:PREPARE_REMOTE_DIRS
call :REMOTE "mkdir -p %DEPLOY_DIR%/jars %DEPLOY_DIR%/frontend/dist %DEPLOY_DIR%/.upload"
exit /b %ERRORLEVEL%

:BUILD_BACKEND
set "SERVICE=%~1"
if "%SKIP_BUILD%"=="1" (
    echo Skip Maven build for !SERVICE!
    exit /b 0
)
echo Build Maven package: !SERVICE! with upstream modules
pushd "%ROOT_DIR%"
call mvn -B -ntp -DskipTests -pl "!SERVICE!" -am package
set "ERR=!ERRORLEVEL!"
popd
exit /b !ERR!

:BUILD_ALL_BACKEND
if "%SKIP_BUILD%"=="1" (
    echo Skip Maven build for all backend services
    exit /b 0
)
echo Build Maven package: all backend services
pushd "%ROOT_DIR%"
call mvn -B -ntp -DskipTests package
set "ERR=!ERRORLEVEL!"
popd
exit /b !ERR!

:FIND_JAR
set "SERVICE=%~1"
set "LOCAL_JAR="
for /f "delims=" %%F in ('dir /b /a-d /o-d "%ROOT_DIR%\!SERVICE!\target\!SERVICE!-*.jar" 2^>nul') do (
    if not defined LOCAL_JAR set "LOCAL_JAR=%ROOT_DIR%\!SERVICE!\target\%%F"
)
if not defined LOCAL_JAR (
    echo Jar not found for !SERVICE!.
    exit /b 1
)
exit /b 0

:UPLOAD_BACKEND
set "SERVICE=%~1"
call :FIND_JAR "!SERVICE!"
if errorlevel 1 exit /b 1
call :PREPARE_REMOTE_DIRS
if errorlevel 1 exit /b 1
echo Upload: !LOCAL_JAR! to %DEPLOY_DIR%/jars/!SERVICE!.jar
call :SCP_TO_REMOTE "!LOCAL_JAR!" "%DEPLOY_DIR%/jars/!SERVICE!.jar"
exit /b %ERRORLEVEL%

:RESTART_SERVICES
set "SERVICES=%~1"
if "%SKIP_RESTART%"=="1" (
    echo Skip remote restart: !SERVICES!
    exit /b 0
)
echo Restart services: !SERVICES!
call :REMOTE_COMPOSE "up -d --build --force-recreate !SERVICES!"
exit /b %ERRORLEVEL%

:BUILD_FRONTEND
if "%SKIP_BUILD%"=="1" (
    echo Skip frontend build
    exit /b 0
)
call :CHECK_CMD pnpm
if errorlevel 1 exit /b 1
echo Build frontend: VITE_API_BASE_URL=%VITE_API_BASE_URL%
pushd "%ROOT_DIR%\sc-frontend"
where corepack >nul 2>nul
if not errorlevel 1 call corepack enable >nul 2>nul
if not "%SKIP_PNPM_INSTALL%"=="1" (
    call pnpm install --frozen-lockfile
    if errorlevel 1 (
        set "ERR=!ERRORLEVEL!"
        popd
        exit /b !ERR!
    )
)
call pnpm build
set "ERR=!ERRORLEVEL!"
popd
exit /b !ERR!

:UPLOAD_FRONTEND
if not exist "%ROOT_DIR%\sc-frontend\dist\" (
    echo Frontend dist not found.
    exit /b 1
)
if not exist "%ARTIFACT_DIR%" mkdir "%ARTIFACT_DIR%"
set "ARCHIVE=%ARTIFACT_DIR%\frontend-dist.tgz"
echo Pack frontend dist
pushd "%ROOT_DIR%\sc-frontend\dist"
tar -czf "%ARCHIVE%" .
set "ERR=!ERRORLEVEL!"
popd
if not "!ERR!"=="0" exit /b !ERR!
call :PREPARE_REMOTE_DIRS
if errorlevel 1 exit /b 1
echo Upload frontend dist to %DEPLOY_DIR%/frontend/dist
call :SCP_TO_REMOTE "%ARCHIVE%" "%DEPLOY_DIR%/.upload/frontend-dist.tgz"
if errorlevel 1 exit /b 1
call :REMOTE "rm -rf %DEPLOY_DIR%/frontend/dist && mkdir -p %DEPLOY_DIR%/frontend/dist && tar -xzf %DEPLOY_DIR%/.upload/frontend-dist.tgz -C %DEPLOY_DIR%/frontend/dist"
exit /b %ERRORLEVEL%

:UPLOAD_INFRA
if not exist "%ROOT_DIR%\.env" (
    echo .env not found.
    exit /b 1
)
if not exist "%ARTIFACT_DIR%" mkdir "%ARTIFACT_DIR%"
set "ARCHIVE=%ARTIFACT_DIR%\infra.tgz"
echo Pack infra files
pushd "%ROOT_DIR%"
tar -czf "%ARCHIVE%" .env docker-compose.artifact.yaml deploy/artifact/frontend-nginx.conf deploy/artifact/java-runtime.Dockerfile deploy/nginx/edupivot.conf deploy/observability docs nacos-config nacos-plugins postgres-init scripts/nacos_config_init.py
set "ERR=!ERRORLEVEL!"
popd
if not "!ERR!"=="0" exit /b !ERR!
call :PREPARE_REMOTE_DIRS
if errorlevel 1 exit /b 1
echo Upload infra to %DEPLOY_DIR%
call :SCP_TO_REMOTE "%ARCHIVE%" "%DEPLOY_DIR%/.upload/infra.tgz"
if errorlevel 1 exit /b 1
call :REMOTE "tar -xzf %DEPLOY_DIR%/.upload/infra.tgz -C %DEPLOY_DIR%"
exit /b %ERRORLEVEL%

:UPLOAD_RUNTIME_FILES
if not exist "%ROOT_DIR%\docker-compose.artifact.yaml" (
    echo docker-compose.artifact.yaml not found.
    exit /b 1
)
if not exist "%ROOT_DIR%\deploy\artifact\java-runtime.Dockerfile" (
    echo deploy/artifact/java-runtime.Dockerfile not found.
    exit /b 1
)
if not exist "%ARTIFACT_DIR%" mkdir "%ARTIFACT_DIR%"
set "ARCHIVE=%ARTIFACT_DIR%\runtime-files.tgz"
echo Pack Java runtime deploy files
pushd "%ROOT_DIR%"
tar -czf "%ARCHIVE%" docker-compose.artifact.yaml deploy/artifact/java-runtime.Dockerfile
set "ERR=!ERRORLEVEL!"
popd
if not "!ERR!"=="0" exit /b !ERR!
call :PREPARE_REMOTE_DIRS
if errorlevel 1 exit /b 1
echo Upload Java runtime deploy files to %DEPLOY_DIR%
call :SCP_TO_REMOTE "%ARCHIVE%" "%DEPLOY_DIR%/.upload/runtime-files.tgz"
if errorlevel 1 exit /b 1
call :REMOTE "tar -xzf %DEPLOY_DIR%/.upload/runtime-files.tgz -C %DEPLOY_DIR%"
exit /b %ERRORLEVEL%

:RESTART_INFRA
if "%SKIP_RESTART%"=="1" (
    echo Skip remote infra restart
    exit /b 0
)
echo Restart infra
call :REMOTE_COMPOSE "up -d postgres redis kafka minio skywalking-banyandb skywalking-oap skywalking-ui prometheus grafana nacos"
if errorlevel 1 exit /b 1
call :REMOTE_COMPOSE "up -d --force-recreate nacos-config-init minio-init"
exit /b %ERRORLEVEL%

:DEPLOY_BACKEND_SERVICE
set "SERVICE=%~1"
call :BUILD_BACKEND "!SERVICE!"
if errorlevel 1 exit /b 1
call :UPLOAD_RUNTIME_FILES
if errorlevel 1 exit /b 1
call :UPLOAD_BACKEND "!SERVICE!"
if errorlevel 1 exit /b 1
call :RESTART_SERVICES "!SERVICE!"
exit /b %ERRORLEVEL%

:DEPLOY_ALL_BACKEND
call :BUILD_ALL_BACKEND
if errorlevel 1 exit /b 1
call :UPLOAD_RUNTIME_FILES
if errorlevel 1 exit /b 1
for %%S in (%BACKEND_SERVICES%) do (
    call :UPLOAD_BACKEND "%%S"
    if errorlevel 1 exit /b 1
)
call :RESTART_SERVICES "%BACKEND_SERVICES%"
exit /b %ERRORLEVEL%

:DEPLOY_FRONTEND
call :BUILD_FRONTEND
if errorlevel 1 exit /b 1
call :UPLOAD_FRONTEND
if errorlevel 1 exit /b 1
call :RESTART_SERVICES "sc-frontend"
exit /b %ERRORLEVEL%
