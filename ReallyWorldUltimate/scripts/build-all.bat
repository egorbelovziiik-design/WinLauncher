@echo off
chcp 65001 >nul
setlocal
set "ROOT_DIR=%~dp0.."
cd /d "%ROOT_DIR%\source\ReallyCore"

where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    call gradle clean test jar
    copy /Y "build\libs\ReallyCore.jar" "%ROOT_DIR%\server\plugins\ReallyCore.jar"
    goto :done
)

where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    call mvn clean test package
    copy /Y "target\ReallyCore.jar" "%ROOT_DIR%\server\plugins\ReallyCore.jar"
    goto :done
)

echo [ОШИБКА] Не найден Gradle или Maven в PATH!
exit /b 1

:done
echo [OK] Плагин ReallyCore.jar успешно пересобран!
