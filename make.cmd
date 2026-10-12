@echo off
rem Windows launcher; project commands and prerequisites: scripts/README.md.
setlocal
if exist "%LOCALAPPDATA%\Programs\GNU Make\4.4.1\bin\make.exe" goto make_local
where.exe make.exe >nul 2>&1
if not errorlevel 1 goto make_path
echo GNU Make was not found. See scripts/README.md for installation instructions. 1>&2
exit /b 1

:make_local
"%LOCALAPPDATA%\Programs\GNU Make\4.4.1\bin\make.exe" -C "%~dp0." %*
exit /b %errorlevel%

:make_path
make.exe -C "%~dp0." %*
exit /b %errorlevel%
