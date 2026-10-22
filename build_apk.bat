@echo off
setlocal EnableDelayedExpansion

echo =======================================================
echo           CTF APPLICATION - APK BUILDER
echo =======================================================

rem Override via environment variables if your setup differs.
if not defined TOOLS_DIR set "TOOLS_DIR=D:\Coding\Tools"
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
    echo          Copy native-secrets.properties.example to native-secrets.properties and fill in your values.
    exit /b 1
)

echo [2/4] Generating native key header...
if not defined PYTHON_EXE (
    where py >nul 2>nul && (set "PYTHON_EXE=py -3") || (set "PYTHON_EXE=python")
)
%PYTHON_EXE% "%CPP_DIR%\generate_keys.py"
if errorlevel 1 (
    echo [FAILED] Key generation failed. See the message above.
    echo          If Python was not found, set the PYTHON_EXE environment variable to your python.exe path.
    exit /b 1
)

echo.
echo [3/4] Compiling Java and C++ source code...
where gradle >nul 2>nul
if errorlevel 1 (
    echo [FAILED] 'gradle' command was not found in PATH or TOOLS_DIR.
    exit /b 1
)

pushd "%BASE_DIR%android_client"
call gradle assembleRelease
set "BUILD_STATUS=%ERRORLEVEL%"
popd

if not %BUILD_STATUS%==0 (
    echo.
    echo [FAILED] An error occurred during the Gradle build process.
    exit /b %BUILD_STATUS%
)

echo.
echo [4/4] Computing APK checksum...
if not exist "%APK_PATH%" (
    echo [FAILED] Built APK output was not found at:
    echo          %APK_PATH%
    exit /b 1
)
certutil -hashfile "%APK_PATH%" SHA256

echo.
echo =======================================================
echo [SUCCESS] APK built successfully!
echo Output path: %APK_PATH%
echo =======================================================
exit /b 0
