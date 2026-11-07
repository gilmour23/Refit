@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
py -3.12 scripts\laptop.py setup
if errorlevel 1 echo Installation failed. Check the message above.
pause
