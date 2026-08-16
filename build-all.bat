@echo off
setlocal
cd /d "%~dp0backend"
echo Building backend ...
call mvn.cmd clean compile
if errorlevel 1 goto failed
cd /d "%~dp0frontend"
echo Building frontend ...
call npm.cmd run build
if errorlevel 1 goto failed
echo Build completed successfully.
exit /b 0

:failed
echo Build failed.
exit /b 1
