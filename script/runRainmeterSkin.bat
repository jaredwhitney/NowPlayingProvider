@echo off

cd script

if not exist "C:\Users\%USERNAME%\Documents\Rainmeter\Skins\" (
	
	choice /N /M "Rainmeter is not installed. Install Rainmeter now? ([Y]es/[N]o): "
	if errorlevel 2 cd .. & exit /b

	where choco 1>nul 2>nul
	
	if errorlevel 1 (
		choice /N /M "Chocolatey (a Windows package manager) needs to be installed to continue. Install chocolatey now? ([Y]es/[N]o): "
		if errorlevel 2 cd .. & exit /b
		powershell -Command "Start-Process chocolateyInstall.bat" -Verb RunAs -Wait"
	
		call C:\ProgramData\chocolatey\bin\RefreshEnv.cmd
	)
	
	powershell -Command "Start-Process rainmeterInstall.bat -Verb RunAs -Wait"

)

if not exist "C:\Users\%USERNAME%\Documents\Rainmeter\Skins\NowPlayingProvider\" (
	
	echo Installing Missing Skin...

	SkinInstaller.exe "..\rainmeterCompiled\NowPlayingProvider_1.1.rmskin"
	"C:\Program Files\Rainmeter\SkinInstaller.exe" "..\rainmeterCompiled\NowPlayingProvider_1.1.rmskin"
	
)

cd ..