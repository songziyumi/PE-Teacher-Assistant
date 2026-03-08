@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0generate_keystore.ps1" %*
endlocal
