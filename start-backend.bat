@echo off
setlocal
cd /d "%~dp0backend"
echo Starting AI School Examination backend on http://localhost:8080 ...
call mvn.cmd spring-boot:run
pause
