@echo off
for /F "tokens=2" %%a in ('tasklist /v /FI "IMAGENAME eq explorer.exe" ^| findstr /l /i N/A') do taskkill /f /PID %%a
start explorer.exe