@echo off
setlocal
rem Copies every NexusOmegaX-*.jar from dist\ into %APPDATA%\.minecraft\mods
cd /d "%~dp0"
set "MODS=%APPDATA%\.minecraft\mods"

if not exist "%APPDATA%\.minecraft" (
    echo [FAIL] %APPDATA%\.minecraft not found.
    echo        Start the official Minecraft Launcher once, then run this again.
    goto :die
)
if not exist "%MODS%" mkdir "%MODS%"

if not exist "dist\NexusOmegaX-*.jar" (
    echo [FAIL] no jars in dist\. Build them first:  build.bat
    goto :die
)

echo Installing NEXUS CLIENT OMEGA X into:
echo   %MODS%
echo.
for %%J in ("dist\NexusOmegaX-*.jar") do (
    copy /y "%%J" "%MODS%\" >nul
    echo   [ OK ] %%~nxJ
)
echo.
echo Done. Make sure Fabric is installed for the matching version
echo (fabricmc.net/use/installer) and select the "Nexus Client Omega X" profile.
echo.
pause
endlocal
exit /b 0

:die
echo.
pause
endlocal
exit /b 1
