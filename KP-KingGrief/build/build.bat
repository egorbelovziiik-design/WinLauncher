@echo off
chcp 65001 >nul
REM ============================================================
REM  КП-KingGrief — сборка Windows EXE
REM  Запуск:  build\build.bat
REM ============================================================

setlocal
cd /d "%~dp0\.."

echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo   КП-KingGrief — сборка Windows EXE
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

where python >nul 2>nul
if errorlevel 1 (
    echo [ОШИБКА] Python не найден. Установите Python 3.11+ с python.org
    echo          При установке обязательно отметьте "Add Python to PATH".
    pause
    exit /b 1
)

for /f "tokens=2" %%v in ('python --version 2^>^&1') do set PYVER=%%v
echo → Python %PYVER%

echo → Обновляю pip…
python -m pip install --upgrade pip >nul

echo → Устанавливаю зависимости…
python -m pip install -r requirements.txt
python -m pip install pyinstaller
if errorlevel 1 (
    echo [ОШИБКА] Не удалось установить зависимости.
    pause
    exit /b 1
)

echo → Запускаю тесты…
python -m pytest tests -q
if errorlevel 1 (
    echo [ВНИМАНИЕ] Часть тестов не прошла. Сборка продолжается.
)

echo → Собираю EXE…
python build\build_exe.py --clean
if errorlevel 1 (
    echo [ОШИБКА] Сборка не удалась.
    pause
    exit /b 1
)

echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo   ГОТОВО: dist\КП-KingGrief.exe
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.
echo   Дальше:
echo     1. Скопируйте dist\КП-KingGrief.exe в отдельную папку.
echo     2. Создайте рядом файл .env  (образец: .env.example)
echo     3. Впишите DISCORD_TOKEN=ваш_токен
echo     4. Запустите КП-KingGrief.exe двойным кликом.
echo.
pause
endlocal
