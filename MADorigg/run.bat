@echo off
rem Convenience launcher: creates a local venv, installs deps and runs the show.
setlocal
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
  echo Creating virtual environment...
  python -m venv .venv
)

call ".venv\Scripts\activate.bat"
python -m pip install --quiet --disable-pip-version-check -r requirements.txt

python main.py %*
set EXITCODE=%ERRORLEVEL%
pause
exit /b %EXITCODE%
