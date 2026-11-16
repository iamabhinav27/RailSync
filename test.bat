@echo off
REM Runs the automated tests (each test uses its own temporary SQLite database).
setlocal
if "%JAVAFX_HOME%"=="" set JAVAFX_HOME=lib\javafx-sdk
call build.bat || exit /b 1
java --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls -cp "bin;lib\*" com.railsync.Main --test
