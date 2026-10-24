@echo off
chcp 65001 >nul
set "ROOT_DIR=%~dp0.."
set "BACKUP_DIR=%ROOT_DIR%\backups"
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set dt=%%I
set "STAMP=%dt:~0,8%_%dt:~8,6%"
set "OUT=%BACKUP_DIR%\reallyworld_backup_%STAMP%.zip"

powershell -NoProfile -Command "Compress-Archive -Path '%ROOT_DIR%\server\plugins\ReallyCore','%ROOT_DIR%\server\plugins\LuckPerms','%ROOT_DIR%\server\world' -DestinationPath '%OUT%' -Force"
echo [OK] Резервная копия сохранена: %OUT%
