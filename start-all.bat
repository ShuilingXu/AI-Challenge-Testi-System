@echo off
setlocal
cd /d "%~dp0"
echo Starting AI School Examination backend and frontend ...
start "School Examination Backend" "%~dp0start-backend.bat"
start "School Examination Frontend" "%~dp0start-frontend.bat"
echo Backend:  http://localhost:8081
echo Frontend: http://localhost:3000
pause
