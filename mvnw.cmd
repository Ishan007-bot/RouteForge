@REM ----------------------------------------------------------------------
@REM Minimal Maven Wrapper for Windows.
@REM On first run, downloads Maven into .mvn\wrapper\ and reuses it after.
@REM ----------------------------------------------------------------------
@echo off
setlocal EnableDelayedExpansion

set "MAVEN_VERSION=3.9.9"
set "BASEDIR=%~dp0"
set "WRAPPER_DIR=%BASEDIR%.mvn\wrapper"
set "MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo [mvnw] Maven %MAVEN_VERSION% not found locally. Downloading...
    if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
    set "ZIP=%WRAPPER_DIR%\maven.zip"
    set "URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '!URL!' -OutFile '!ZIP!'" || (
        echo [mvnw] ERROR: download failed
        exit /b 1
    )
    powershell -NoProfile -Command "Expand-Archive -Force -Path '!ZIP!' -DestinationPath '%WRAPPER_DIR%'" || (
        echo [mvnw] ERROR: unzip failed
        exit /b 1
    )
    del "!ZIP!" >nul 2>&1
)

"%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
