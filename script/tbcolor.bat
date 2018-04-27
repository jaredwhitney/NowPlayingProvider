@echo off
reg add HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\Accent /f /v AccentPalette /t REG_BINARY /d %1
reg add HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\Accent /f /v StartColorMenu /t REG_DWORD /d 0xff%2
reg add HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\Accent /f /v AccentColorMenu /t REG_DWORD /d 0xff%3
