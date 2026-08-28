@echo off
echo =======================================================
echo           CTF APPLICATION - APK BUILDER
echo =======================================================
set "BASE_DIR=%~dp0"
set "TOOLS_DIR=D:\Coding\Tools"
set "JAVA_HOME=%TOOLS_DIR%\jdk17"
set "ANDROID_SDK_ROOT=%TOOLS_DIR%\android_sdk"
set "ANDROID_USER_HOME=%TOOLS_DIR%\.android"
set "GRADLE_USER_HOME=%TOOLS_DIR%\.gradle"

set "PATH=%JAVA_HOME%\bin;%TOOLS_DIR%\gradle\bin;%PATH%"

cd "%BASE_DIR%android_client"

echo.
echo [1/2] Initializing build environment...
echo [2/2] Compiling Java and C++ source code...
echo.

call gradle assembleRelease

if %ERRORLEVEL% equ 0 (
    echo.
    echo =======================================================
    echo [SUCCESS] APK built successfully!
    echo Output path: android_client\app\build\outputs\apk\release\app-release.apk
    echo =======================================================
) else (
    echo.
    echo [FAILED] An error occurred during the build process.
)
