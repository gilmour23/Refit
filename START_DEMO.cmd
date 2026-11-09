@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
if not exist ".venv-demo\Scripts\python.exe" (
  echo Run INSTALL_FIRST.cmd first.
  pause
  exit /b 1
)
".venv-demo\Scripts\python.exe" scripts\laptop.py run
pause
