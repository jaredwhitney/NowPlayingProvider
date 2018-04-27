@echo off
color 57

REM Server

	start /MIN cmd /c script\runServer.bat

REM Providers

	start /MIN cmd /c script\runYoutubeProvider.bat
	start /MIN cmd /c script\runNetflixProvider.bat

REM Consumers
	
	start /MIN cmd /c script\runWindowsThemeController.bat
	start /MIN cmd /c script\runPopupNotificationService.bat
	start /MIN cmd /c script\runBluetoothController.bat
	start /MIN cmd /c script\runRainmeterSkin.bat

exit