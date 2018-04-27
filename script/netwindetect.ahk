#SingleInstance

IfWinExist, Netflix Playback
{
	WinGetTitle, title
	FileAppend true "%title%"`n, *
}
Else
{
	FileAppend false`n, *
}

ExitApp
