@echo off
chcp 65001 >nul
title ReallyWorld КП Discord Bot — Владелец: robik0277 (1381255368650526861)
cd /d "%~dp0"

echo ====================================================================
echo   Запуск Discord-бота ReallyWorld КП
echo   Главный владелец: robik0277 (ID: 1381255368650526861)
echo ====================================================================

where python >nul 2>nul
if %errorlevel% neq 0 (
    echo [ОШИБКА] Python 3.10+ не установлен! Скачайте с https://www.python.org/downloads/ и отметьте галочку "Add Python to PATH".
    pause
    exit /b 1
)

if not exist ".venv" (
    echo [1/3] Создаю изолированное окружение Python...
    python -m venv .venv
)

echo [2/3] Проверяю зависимости discord.py и aiosqlite...
call .venv\Scripts\activate.bat
pip install -q -r requirements.txt

if not exist ".env" (
    copy /Y ".env.example" ".env" >nul
    echo.
    echo [!] Создан файл .env! Откройте его блокнотом (или откройте invite-generator.html в браузере),
    echo     вставьте ваш DISCORD_TOKEN с https://discord.com/developers/applications и снова запустите start.bat!
    notepad .env
    pause
    exit /b 0
)

echo [3/3] Запускаю бота...
python main.py
pause
