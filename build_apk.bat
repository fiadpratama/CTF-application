@echo off
echo =======================================================
echo          CTF APPLICATION - APK BUILDER
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
echo [1/2] Menyiapkan lingkungan kompilasi...
echo [2/2] Memulai kompilasi kode Java dan C++...
echo.

call gradle assembleRelease

if %ERRORLEVEL% equ 0 (
    echo.
    echo =======================================================
    echo [SUKSES] APK berhasil dibuat!
    echo Lokasi: android_client\app\build\outputs\apk\release\app-release.apk
    echo =======================================================
) else (
    echo.
    echo [GAGAL] Terjadi kesalahan saat kompilasi.
)