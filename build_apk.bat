@echo off
setlocal

echo =======================================================
echo           CTF APPLICATION - APK BUILDER
echo =======================================================

rem Override via environment variables if your setup differs.
rem PYTHON_EXE example with spaces: set PYTHON_EXE="C:\Path With Spaces\python.exe"
if not defined TOOLS_DIR set "TOOLS_DIR=D:\Coding\Tools"
if not defined PYTHON_EXE set "PYTHON_EXE=py -3"

set "BASE_DIR=%~dp0"
set "JAVA_HOME=%TOOLS_DIR%\jdk17"
set "ANDROID_SDK_ROOT=%TOOLS_DIR%\android_sdk"
set "ANDROID_USER_HOME=%TOOLS_DIR%\.android"
set "GRADLE_USER_HOME=%TOOLS_DIR%\.gradle"
set "PATH=%JAVA_HOME%\bin;%TOOLS_DIR%\gradle\bin;%PATH%"

set "CPP_DIR=%BASE_DIR%android_client\app\src\main\cpp"
set "SECRETS_FILE=%CPP_DIR%\native-secrets.properties"
set "APK_PATH=%BASE_DIR%android_client\app\build\outputs\apk\release\app-release.apk"

echo.
echo [1/4] Checking secret configuration...
if not exist "%SECRETS_FILE%" (
    echo [FAILED] native-secrets.properties was not found.
    echo          Copy native-secrets.properties.example to native-secrets.properties and fill in your own values.
    exit /b 1
)

echo [2/4] Generating native key header...
%PYTHON_EXE% "%CPP_DIR%\generate_keys.py"
if errorlevel 1 (
    echo [FAILED] Key generation failed. See the message above.
    echo          If Python was not found, set the PYTHON_EXE environment variable to your python.exe path.
    exit /b 1
)

echo.
echo [3/4] Compiling Java and C++ source code...
echo.
cd /d "%BASE_DIR%android_client"
call gradle assembleRelease
if errorlevel 1 (
    echo.
    echo [FAILED] An error occurred during the build process.
    exit /b 1
)

echo.
echo [4/4] Computing APK checksum...
certutil -hashfile "%APK_PATH%" SHA256

echo.
echo =======================================================
echo [SUCCESS] APK built successfully!
echo Output path: android_client\app\build\outputs\apk\release\app-release.apk
echo =======================================================
exit /b 0
