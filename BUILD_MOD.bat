@echo off
title LT5Lyrics Mod Builder
cd /d "%~dp0"
echo ===================================================
echo   Building LT5Lyrics v1.11.7 for Minecraft 26.3
echo   This is optional: the supplied mod JAR is already built.
echo ===================================================
echo.
call gradlew.bat build
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Build failed! Make sure a Java 25 JDK is installed
    echo         and that your internet connection is working.
    echo.
    pause
    exit /b %ERRORLEVEL%
)
echo.
echo ===================================================
echo   SUCCESS! Your compiled mod JAR is ready at:
echo   %~dp0build\libs\lt5lyrics-1.11.7+mc26.3.jar
echo.
echo   Copy lt5lyrics-1.11.7+mc26.3.jar (NOT the -sources jar)
echo   into your Minecraft "mods" folder!
echo ===================================================
echo.
pause
