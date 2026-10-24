@echo off
chcp 65001 >nul
title ReallyWorld Ultimate [1.21.4 — 1.21.11]
cd /d "%~dp0"

set MIN_RAM=4G
set MAX_RAM=8G
set JAR_FILE=server.jar

if not exist "%JAR_FILE%" (
    echo [!] Ядро %JAR_FILE% не найдено. Сначала запустите ..\install.bat!
    pause
    exit /b 1
)

java -Xms%MIN_RAM% -Xmx%MAX_RAM% -Dfile.encoding=UTF-8 ^
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 ^
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch ^
  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M ^
  -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 ^
  -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 ^
  -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 ^
  -jar "%JAR_FILE%" --nogui

pause
