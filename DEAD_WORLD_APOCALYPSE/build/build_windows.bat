@echo off
REM Build DEAD WORLD: APOCALYPSE for Windows x64 (requires Python 3.11 in PATH)
setlocal
cd /d "%~dp0\.."

echo [1/5] Installing dependencies...
py -3.11 -m pip install --upgrade pip || python -m pip install --upgrade pip
py -3.11 -m pip install -r requirements.txt pyinstaller || python -m pip install -r requirements.txt pyinstaller

echo [2/5] Self-test of sources...
py -3.11 main.py --selftest || python main.py --selftest
if errorlevel 1 ( echo SELFTEST FAILED & exit /b 1 )

echo [3/5] Building EXE with PyInstaller...
py -3.11 -m PyInstaller --noconfirm --clean build\DeadWorldApocalypse.spec || python -m PyInstaller --noconfirm --clean build\DeadWorldApocalypse.spec
if errorlevel 1 ( echo BUILD FAILED & exit /b 1 )

echo [4/5] Packaging release folder...
if exist DEAD_WORLD_APOCALYPSE_RELEASE rmdir /s /q DEAD_WORLD_APOCALYPSE_RELEASE
mkdir DEAD_WORLD_APOCALYPSE_RELEASE
xcopy /e /i /q dist\DeadWorldApocalypse DEAD_WORLD_APOCALYPSE_RELEASE\DeadWorldApocalypse
copy /y README.md DEAD_WORLD_APOCALYPSE_RELEASE\ >nul

echo [5/5] Verifying built EXE (--selftest writes selftest_result.txt)...
cd DEAD_WORLD_APOCALYPSE_RELEASE\DeadWorldApocalypse
DeadWorldApocalypse.exe --selftest
cd ..\..
findstr /c:"SELFTEST_OK" DEAD_WORLD_APOCALYPSE_RELEASE\DeadWorldApocalypse\selftest_result.txt && echo BUILD VERIFIED || echo WARNING: selftest marker not found

echo.
echo DONE: DEAD_WORLD_APOCALYPSE_RELEASE\DeadWorldApocalypse\DeadWorldApocalypse.exe
pause
