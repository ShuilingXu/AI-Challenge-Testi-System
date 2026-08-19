@echo off
setlocal
cd /d "%~dp0backend"
echo Starting AI School Examination backend on http://localhost:8081 ...
call mvn.cmd spring-boot:run -Dspring-boot.run.arguments="--server.address=0.0.0.0 --server.port=8081"
pause
