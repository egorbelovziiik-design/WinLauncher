@echo off
chcp 6501 >nul
set SRC=%APPDATA%\DeadWorldApocalypse\startup.log
if exist "%SRC%" (
  copy /Y "%SRC%" "%USERPROFILE%\Desktop\DeadWorld_startup.log" >nul
  echo Log copied to Desktop: DeadWorld_startup.log
  echo Send this file to the developer, it shows where loading stopped.
) else (
  echo Log not found: %SRC%
  echo Launch the game at least once, then run this file again.
)
pause
