@echo off

cd script

where choco 1>nul 2>nul

if errorlevel 1 (
	choice /N /M "Chocolatey (a Windows package manager) needs to be installed to continue. Install chocolatey now? ([Y]es/[N]o): "
	if errorlevel 2 cd .. & exit /b
	powershell -Command "Start-Process chocolateyInstall.bat -Verb RunAs -Wait"
	
	call C:\ProgramData\chocolatey\bin\RefreshEnv.cmd
)

choco search --local-only curl | findstr /i curl 1> nul
if errorlevel 1 (
	
	choice /N /M "curl needs to be installed to continue. Install curl now? ([Y]es/[N]o): "
	if errorlevel 2 cd .. & exit /b
	powershell -Command "Start-Process curlInstall.bat -Verb RunAs -Wait"
	REM cd ..
	REM exit /b
)

choco search --local-only sqlite | findstr /i sqlite 1>nul
if errorlevel 1 (

	choice /N /M "SQlite3 needs to be installed to continue. Install SQlite3 now? ([Y]es/[N]o): "
	if errorlevel 2 cd .. & exit /b
	powershell -Command "Start-Process sqliteInstall.bat -Verb RunAs -Wait"
	REM cd ..
	REM exit /b
)

cd ..

title Netflix Now Playing Provider
java NetflixNowPlayingProvider
exit /b