@echo off
setlocal
cd /d "%~dp0.."

echo ============================================
echo   MADorigg - Windows executable build
echo ============================================
echo.

where python >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Python was not found in PATH. Install Python 3.11+ first.
  pause
  exit /b 1
)

echo [1/4] Installing dependencies...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt pyinstaller==6.10.0
if errorlevel 1 goto fail

echo.
echo [2/4] Generating icon...
python tools\make_icon.py

echo.
echo [3/4] Building MADorigg.exe (this takes a minute)...
python tools\build_exe.py
if errorlevel 1 goto fail

echo.
echo [4/4] Done.
echo Executable: %CD%\dist\MADorigg.exe
echo.
pause
exit /b 0

:fail
echo.
echo [ERROR] Build failed - see the messages above.
pause
exit /b 1
