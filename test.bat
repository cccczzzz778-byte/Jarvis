@echo off
chcp 65001 >nul
cd /d "%~dp0"
py jarvis.py --text "soat nechi" --no-voice
pause
