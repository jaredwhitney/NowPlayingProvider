@echo off

REM This is an adaptation of curlfire for Windows

REM Credit for the original curlfire and cookiefire bash scripts goes to Tal Wrii
REM (see https://github.com/talwrii/curlfire/)

for /F %%a in ('dir /b "C:\Users\%USERNAME%\AppData\Roaming\Mozilla\Firefox\Profiles\"') do (

	type "C:\Users\%USERNAME%\AppData\Roaming\Mozilla\Firefox\Profiles\%%a\cookies.sqlite" > cookies.sqlite

	sqlite3 -separator "	" cookies.sqlite "SELECT host, CASE substr(host,1,1)='.' WHEN 0 THEN 'FALSE' else 'TRUE' END, 	path, CASE isSecure WHEN 0 THEN 'FALSE' else 'TRUE' END, expiry, name, value FROM moz_cookies;" > curlcookies.txt

	curl -b curlcookies.txt %*

	goto :eof

)