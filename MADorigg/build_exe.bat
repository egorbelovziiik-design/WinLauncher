@echo off
REM ============================================================
REM  MADorigg — сборка .exe одним кликом (Windows, PyInstaller)
REM ============================================================
setlocal

echo [1/3] Installing dependencies...
python -m pip install -r requirements.txt
if errorlevel 1 goto :error

echo [2/3] Installing PyInstaller...
python -m pip install pyinstaller
if errorlevel 1 goto :error

echo [3/3] Building MADorigg.exe (onefile, windowed)...
python -m PyInstaller --noconfirm --onefile --windowed ^
  --name MADorigg ^
  --collect-all pycaw ^
  --collect-all comtypes ^
  --collect-all numpy ^
  --collect-all pygame ^
  --hidden-import pycaw ^
  --hidden-import comtypes ^
  main.py
if errorlevel 1 goto :error

echo.
echo ============================================================
echo  DONE: dist\MADorigg.exe
echo ============================================================
goto :end

:error
echo.
echo BUILD FAILED. See errors above.
exit /b 1

:end
endlocal
