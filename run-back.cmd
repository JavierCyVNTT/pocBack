@echo off
setlocal

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-back.ps1" %*
exit /b %ERRORLEVEL%
