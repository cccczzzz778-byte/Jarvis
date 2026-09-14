@echo off
chcp 65001 >nul
cd /d "%~dp0"
py -m pip install --upgrade pip
py -m pip install -r requirements.txt
if errorlevel 1 (
  echo.
  echo O'rnatishda xatolik yuz berdi. Python 3.11 yoki 3.12 tavsiya etiladi.
  pause
  exit /b 1
)
echo.
echo Tayyor. Endi run.bat ni ishga tushiring.
pause
