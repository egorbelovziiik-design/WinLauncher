@echo off
setlocal enabledelayedexpansion
rem ==========================================================================
rem  NEXUS CLIENT OMEGA X - one click mod build
rem
rem  Builds the three Fabric jars and (optionally) installs them into
rem  %APPDATA%\.minecraft\mods
rem
rem    build.bat              build all three versions
rem    build.bat /install     build and copy the jars into .minecraft\mods
rem    build.bat 1.21.4       build a single version
rem ==========================================================================
cd /d "%~dp0"
set "ROOT=%cd%"
set "DIST=%ROOT%\dist"
if not exist "%DIST%" mkdir "%DIST%"

set "INSTALL=0"
set "ONLY="
for %%A in (%*) do (
    if /i "%%A"=="/install" set "INSTALL=1"
    if /i "%%A"=="1.16.5" set "ONLY=nexus1165"
    if /i "%%A"=="1.21.4" set "ONLY=nexus1214"
    if /i "%%A"=="1.21.11" set "ONLY=nexus12111"
)

echo.
echo  ================= NEXUS CLIENT OMEGA X - BUILD ================
echo.

rem ---------------------------------------------------------------- java ----
where java >nul 2>nul
if errorlevel 1 (
    echo  [FAIL] java not found. Install JDK 21: https://adoptium.net/temurin/releases/
    goto :die
)
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JV=%%~v"
echo  Java    : %JV%
echo  Java 21+ is required for Minecraft 1.21.x.

rem -------------------------------------------------------------- gradle ----
set "GRADLE=gradle"
where gradle >nul 2>nul
if errorlevel 1 goto :nogradle
goto :havedegradle

:nogradle
echo.
echo  Gradle is not installed. Trying to download it automatically
echo  (official distribution from services.gradle.org, ~130 MB)...
set "GDIR=%USERPROFILE%\.gradle-dist"
set "GVER=8.10.2"
if not exist "%GDIR%" mkdir "%GDIR%"
if not exist "%GDIR%\gradle-%GVER%\bin\gradle.bat" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; " ^
      "$zip=\"$env:TEMP\\gradle-%GVER%-bin.zip\"; " ^
      "Write-Host 'downloading gradle-%GVER%-bin.zip ...'; " ^
      "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GVER%-bin.zip' -OutFile $zip -UseBasicParsing; " ^
      "Write-Host 'extracting ...'; Expand-Archive -Path $zip -DestinationPath '%GDIR%' -Force; " ^
      "Remove-Item $zip -Force"
)
if exist "%GDIR%\gradle-%GVER%\bin\gradle.bat" (
    set "GRADLE=%GDIR%\gradle-%GVER%\bin\gradle.bat"
    echo  Gradle  : %GDIR%\gradle-%GVER% ^(downloaded^)
    goto :havedegradle
)
echo.
echo  [FAIL] Gradle is missing and could not be downloaded.
echo         Install it manually:  https://gradle.org/install/
echo         (chocolatey: choco install gradle   ^|   scoop: scoop install gradle)
goto :die

:havedegradle
echo  Gradle  : %GRADLE%
echo.

rem --------------------------------------------------------------- build ----
set "BUILT=0"
set "FAILED=0"
for %%A in (nexus1165 nexus1214 nexus12111) do (
    if "%ONLY%"=="" (call :buildone %%A) else (if /i "%ONLY%"=="%%A" call :buildone %%A)
)
echo.
echo  ------------------------------------------------------------------
echo   built: %BUILT%   failed: %FAILED%
echo   jars in: %DIST%
dir /b "%DIST%\*.jar" 2>nul
echo  ------------------------------------------------------------------
if "%FAILED%"=="0" if "%INSTALL%"=="1" call :installmods
if "%INSTALL%"=="0" (
    echo.
    echo   To install the jars into Minecraft now run:  install-mods.bat
)
echo.
endlocal
exit /b 0

rem -------------------------------------------------------------------------
:buildone
set "ADAPTER=%~1"
echo  === building %ADAPTER% ===
pushd "%ROOT%\adapters\%ADAPTER%"
call %GRADLE% build --console=plain --no-daemon
if errorlevel 1 (
    echo  [FAIL] %ADAPTER% - see the Gradle output above.
    echo         Fix hint: adapters\%ADAPTER%\gradle.properties lets you change
    echo         loomVersion / minecraftVersion / loaderVersion / yarnMappings.
    set /a FAILED+=1
) else (
    for /r "%ROOT%\adapters\%ADAPTER%\build\libs" %%J in (NexusOmegaX-*.jar) do (
        copy /y "%%J" "%DIST%\" >nul
        echo  [ OK ] %%~nxJ
    )
    set /a BUILT+=1
)
popd
exit /b 0

:installmods
set "MODS=%APPDATA%\.minecraft\mods"
if not exist "%APPDATA%\.minecraft" (
    echo  [WARN] %APPDATA%\.minecraft not found - start the official launcher once.
    exit /b 0
)
if not exist "%MODS%" mkdir "%MODS%"
for %%J in ("%DIST%\NexusOmegaX-*.jar") do (
    copy /y "%%J" "%MODS%\" >nul
    echo  [ OK ] installed %%~nxJ -^> %MODS%
)
echo.
echo   Next: install Fabric for your version (fabricmc.net/use/installer),
echo   choose the "Nexus Client Omega X" profile and press Play.
exit /b 0

:die
echo.
echo  Build stopped.
endlocal
exit /b 1
