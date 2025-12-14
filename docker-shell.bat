@echo off
REM Start or create the F-Droid build container

set IMAGE_NAME=pokerpayout-fdroid
set CONTAINER_NAME=pokerpayout-fdroid-build

REM Rebuild image if Dockerfile changed
docker image inspect %IMAGE_NAME% >nul 2>&1
if errorlevel 1 (
    echo Building Docker image %IMAGE_NAME%...
    docker build -t %IMAGE_NAME% .
) else (
    REM Check if Dockerfile is newer than image
    echo Checking for Dockerfile changes...
    powershell -command "try { $dockerfileTime = (Get-Item Dockerfile).LastWriteTime; $imageTime = [DateTime]::Parse((docker inspect --format '{{.Created}}' %IMAGE_NAME%)); if ($dockerfileTime -gt $imageTime) { exit 1 } else { exit 0 } } catch { exit 0 }" >nul 2>&1
    if errorlevel 1 (
        echo Dockerfile has changed, rebuilding image...
        docker build -t %IMAGE_NAME% .
    )
)

REM Check if container exists
docker ps -a --format "{{.Names}}" | findstr /x %CONTAINER_NAME% >nul 2>&1
if errorlevel 1 (
    REM Container doesn't exist - create it
    echo Creating new container...
    echo.
    echo First time: Run these commands inside:
    echo   dos2unix gradlew
    echo   ./gradlew clean assembleRelease
    echo.
    docker run -it --name %CONTAINER_NAME% -v "%cd%:/workspace" %IMAGE_NAME%
) else (
    REM Container exists - start and attach
    docker ps --format "{{.Names}}" | findstr /x %CONTAINER_NAME% >nul 2>&1
    if errorlevel 1 (
        echo Starting container...
        docker start %CONTAINER_NAME% >nul
    )
    echo Attaching to container...
    docker exec -it %CONTAINER_NAME% /bin/bash
)
